/*************************************************************************
 *
 * ADOBE SYSTEMS INCORPORATED
 * Copyright 2015 Adobe Systems Incorporated
 * All Rights Reserved.
 *
 * NOTICE:  Adobe permits you to use, modify, and distribute this file in accordance with the
 * terms of the Adobe license agreement accompanying it.  If you have received this file from a
 * source other than Adobe, then your use, modification, or distribution of it requires the prior
 * written permission of Adobe.
 **************************************************************************/
package com.adobe.communities.ugc.migration.importer;

import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import com.adobe.cq.social.calendar.client.endpoints.CalendarOperations;
import com.adobe.cq.social.commons.comments.endpoints.CommentOperations;
import com.adobe.cq.social.forum.client.endpoints.ForumOperations;
import com.adobe.cq.social.journal.client.endpoints.JournalOperations;
import com.adobe.cq.social.qna.client.endpoints.QnaForumOperations;
import com.adobe.cq.social.tally.client.endpoints.TallyOperationsService;
import com.adobe.cq.social.ugcbase.SocialUtils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.NonExistingResource;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component(label = "UGC Migration File Importer",
        description = "Accepts a zipped archive of migration data, unzips its contents and saves in jcr tree",
        specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/ugc/upload")})
public class ImportFileUploadServlet extends SlingAllMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ImportFileUploadServlet.class);

    public final static String UPLOAD_DIR = "/etc/migration/uploadFile";

    @Reference
    private ForumOperations forumOperations;

    @Reference
    private QnaForumOperations qnaForumOperations;

    @Reference
    private CommentOperations commentOperations;

    @Reference
    private TallyOperationsService tallyOperationsService;

    @Reference
    private CalendarOperations calendarOperations;

    @Reference
    private JournalOperations journalOperations;

    @Reference
    private SocialUtils socialUtils;

    @Reference
    private ResourceResolverFactory rrf;

    /**
     * The get operation returns a JSON string representing the current contents of the file repository holding our
     * current migration files. If necessary, this method will also create the files' repository resource.
     * @param request - the request
     * @param response - the response
     * @throws ServletException
     * @throws IOException
     */
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws ServletException, IOException {

        final ResourceResolver resolver = request.getResourceResolver();

        UGCImportHelper.checkUserPrivileges(resolver, rrf);

        final Resource parentResource = resolver.getResource(UPLOAD_DIR);
        final JSONWriter writer = new JSONWriter(response.getWriter());
        writer.setTidy(true);
        if (null == parentResource || parentResource instanceof NonExistingResource) {
            // create migration directory if needed
            final String[] dir_parts = UPLOAD_DIR.split("/");
            final StringBuilder parentPath = new StringBuilder("/");
            final Map<String, Object> folderProperties = new HashMap<String, Object>();
            folderProperties.put(JcrConstants.JCR_PRIMARYTYPE, "sling:Folder");
            for (final String dir : dir_parts) {
                if (dir.equals("")) {
                    continue;
                }
                Resource currentParent = resolver.getResource(parentPath + dir);
                if (null == currentParent || currentParent instanceof NonExistingResource) {
                    currentParent = resolver.getResource(parentPath.toString());
                    resolver.create(currentParent, dir, folderProperties);
                }
                parentPath.append(dir).append("/");
            }
            resolver.commit();
            try {
                writer.array();
                writer.endArray();
                return;
            } catch (JSONException e) {
                throw new ServletException("Unable to output JSON", e);
            }
        }

        final Iterable<Resource> folders = parentResource.getChildren();

        try {
            writer.array();
            for (final Resource folder : folders) {
                final ValueMap vm = folder.adaptTo(ValueMap.class);
                // check that the type is "sling:Folder" and that it contains the properties "uploadedFileName" and
                // "uploadDate"
                if (vm.containsKey(JcrConstants.JCR_PRIMARYTYPE)
                        && vm.get(JcrConstants.JCR_PRIMARYTYPE).equals("sling:Folder")
                        && vm.containsKey("uploadedFileName") && vm.containsKey("uploadDate")) {

                    writer.object();
                    writer.key("folderName");
                    writer.value(folder.getName());
                    writer.key("filename");
                    writer.value(vm.get("uploadedFileName"));
                    // dig into the child nodes recursively to build up the "files" array
                    writer.key("files");
                    writer.array();
                    final ArrayList<String> filenames = new ArrayList<String>();
                    extractFilenames(folder, "", filenames);
                    for (final String filename : filenames) {
                        writer.value(filename);
                    }
                    writer.endArray();
                    writer.key("uploadDate"); // record the date here to get the time our upload completed
                    writer.value(((GregorianCalendar) vm.get("uploadDate")).getTime().getTime());
                    writer.endObject();
                }
            }
            writer.endArray();
        } catch (final JSONException e) {
            throw new ServletException("Unable to output JSON", e);
        }

    }

    /**
     * Recursively populate the fileNames array
     * @param folder - the parent Resource
     * @param parentPath - the String that gives us the path we're currently within
     * @param fileNames - the ArrayList that will eventually contain all of the file names
     */
    private void extractFilenames(final Resource folder, final String parentPath, final ArrayList<String> fileNames) {
        Iterable<Resource> children = folder.getChildren();
        for (final Resource child : children) {
            final ValueMap childVM = child.adaptTo(ValueMap.class);
            // if there's a single child named "file" of type "nt:file", then parentPath is a file name and we're done
            if (child.getName().equals("file") && childVM.containsKey(JcrConstants.JCR_PRIMARYTYPE)
                    && childVM.get(JcrConstants.JCR_PRIMARYTYPE).equals(JcrConstants.NT_FILE)) {
                fileNames.add(parentPath);
                return;
            }
            // otherwise, keep diving into the child nodes
            extractFilenames(child, parentPath + "/" + child.getName(), fileNames);
        }
    }

    /**
     * The post operation accepts uploaded zip files, then explodes their contents and saves them into the jcr tree
     * @param request - the request
     * @param response - the response
     * @throws ServletException
     * @throws IOException
     */
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws ServletException, IOException {

        final ResourceResolver resolver = request.getResourceResolver();

        UGCImportHelper.checkUserPrivileges(resolver, rrf);

        final Resource parentResource = resolver.getResource(UPLOAD_DIR);
        if (null == parentResource || parentResource instanceof NonExistingResource) {
            throw new ServletException("upload directory hasn't been created");
        }

        // get the uploaded file
        final RequestParameter[] fileRequestParameters = request.getRequestParameters("file");
        if (fileRequestParameters == null || fileRequestParameters.length <= 0
                || fileRequestParameters[0].isFormField() || !fileRequestParameters[0].getFileName().endsWith(".zip")) {

            throw new ServletException("Unrecognized file input type");
        }
        final String filename = fileRequestParameters[0].getFileName();
        // create the folder name for the uploaded file contents

        final Random RNG = new Random();
        final String randomString =
            String.valueOf(RNG.nextInt(Integer.MAX_VALUE)) + String.valueOf(RNG.nextInt(Integer.MAX_VALUE));

        // record the folder name for sending back with the response
        final JSONWriter writer = new JSONWriter(response.getWriter());
        writer.setTidy(true);
        final Calendar uploadDate = new GregorianCalendar();
        try {
            writer.object();
            writer.key("folderName");
            writer.value(randomString);
            writer.key("filename");
            writer.value(filename);
            writer.key("uploadDate");
            writer.value(uploadDate.getTime().getTime()); // <-- not a typo
            writer.key("files");
            writer.array();
        } catch (JSONException e) {
            throw new ServletException("Unable to use JSONWriter", e);
        }
        // actually create the folder
        final Map<String, Object> folderProperties = new HashMap<String, Object>();
        folderProperties.put(JcrConstants.JCR_PRIMARYTYPE, "sling:Folder");
        folderProperties.put("uploadedFileName", filename);
        folderProperties.put("uploadDate", uploadDate);
        final Resource folder = resolver.create(parentResource, randomString, folderProperties);

        // prepare to read the uploaded zip file
        final InputStream uploadedFileInputStream;
        try {
            uploadedFileInputStream = fileRequestParameters[0].getInputStream();
        } catch (IOException e) {
            throw new ServletException("Could not read zip archive");
        }
        final ZipInputStream zipInputStream = new ZipInputStream(uploadedFileInputStream);

        try {
            saveExplodedFiles(resolver, folder, writer, zipInputStream, request.getParameter("basePath"));
            // close our JSONWriter
            writer.endArray();
            writer.endObject();
        } catch (final JSONException e) {
            throw new ServletException("Unable to close JSONWriter", e);
        } catch (final ServletException e) {
            resolver.delete(folder); // clean up after ourselves
            throw e;
        } finally {
            // close input streams
            zipInputStream.close();
            uploadedFileInputStream.close();
        }
    }

    private void saveExplodedFiles(final ResourceResolver resolver, final Resource folder, final JSONWriter writer,
        final ZipInputStream zipInputStream, final String basePath) throws ServletException {

        // we need the closeShieldInputStream to prevent the zipInputStream from being closed during resolver.create()
        final CloseShieldInputStream closeShieldInputStream = new CloseShieldInputStream(zipInputStream);

        // fileResourceProperties and folderProperties will be reused inside the while loop
        final Map<String, Object> fileResourceProperties = new HashMap<String, Object>();
        fileResourceProperties.put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE);

        final Map<String, Object> folderProperties = new HashMap<String, Object>();
        folderProperties.put(JcrConstants.JCR_PRIMARYTYPE, "sling:Folder");

        ZipEntry zipEntry;
        try {
            zipEntry = zipInputStream.getNextEntry();
        } catch (final IOException e) {
            throw new ServletException("Unable to read entries from uploaded zip archive", e);
        }
        final List<Resource> toDelete = new ArrayList<Resource>();
        while (zipEntry != null) {
            // store files under the provided folder
            try {
                final String name = ResourceUtil.normalize("/" + zipEntry.getName());
                if (null == name) {
                    // normalize filename and if they aren't inside upload path, don't store them
                    continue;
                }
                Resource parent = folder;
                Resource subFolder = null;
                for (final String subFolderName : name.split("/")) {
                    // check if the sub-folder already exists
                    subFolder = resolver.getResource(parent, subFolderName);
                    if (null == subFolder || subFolder instanceof NonExistingResource) {
                        // create the sub-folder
                        subFolder = resolver.create(parent, subFolderName, folderProperties);
                    }
                    parent = subFolder;
                }
                if (!zipEntry.isDirectory()) {
                    // first represent the file as a resource
                    final Resource file = resolver.create(subFolder, "file", fileResourceProperties);
                    // now store its data as a jcr:content node
                    final Map<String, Object> fileProperties = new HashMap<String, Object>();
                    byte[] bytes = IOUtils.toByteArray(closeShieldInputStream);
                    fileProperties.put(JcrConstants.JCR_DATA, new String(bytes, "UTF8"));
                    fileProperties.put(JcrConstants.JCR_PRIMARYTYPE, "nt:resource");
                    resolver.create(file, JcrConstants.JCR_CONTENT, fileProperties);

                    // if provided a basePath, import immediately
                    if (StringUtils.isNotBlank(basePath) && null != file && !(file instanceof NonExistingResource)) {
                        Resource fileContent = file.getChild(JcrConstants.JCR_CONTENT);
                        if (null != fileContent && !(fileContent instanceof NonExistingResource)) {
                            final ValueMap contentVM = fileContent.getValueMap();
                            InputStream inputStream = (InputStream) contentVM.get(JcrConstants.JCR_DATA);
                            if (inputStream != null) {
                                final JsonParser jsonParser = new JsonFactory().createParser(inputStream);
                                jsonParser.nextToken(); // get the first token
                                String resName = basePath + name.substring(0, name.lastIndexOf(".json"));
                                Resource resource = resolver.getResource(resName);
                                if (resource == null) {
                                    // voting does not have a node under articles
                                    resource = resolver.getResource(resName.substring(0, resName.lastIndexOf("/")));
                                }
                                try {
                                    importFile(jsonParser, resource, resolver);
                                    toDelete.add(file);
                                } catch (final Exception e) {
                                    // add the file name to our response ONLY if we failed to import it
                                    writer.value(name);
                                    // we want to log the reason we weren't able to import, but don't stop importing
                                    LOG.error(e.getMessage());
                                }
                            }
                        }
                    } else if (StringUtils.isBlank(basePath) && null != file
                            && !(file instanceof NonExistingResource)) {
                        // add the file name to our response
                        writer.value(name);
                    }
                }
                resolver.commit();
                zipEntry = zipInputStream.getNextEntry();
            } catch (final IOException e) {
                // convert any IOExceptions into ServletExceptions
                throw new ServletException(e.getMessage(), e);
            } catch (final JSONException e) {
                // convert any JSONExceptions into ServletExceptions
                throw new ServletException(e.getMessage(), e);
            }
        }
        closeShieldInputStream.close();
        // delete any files that were successfully imported
        if (!toDelete.isEmpty()) {
            for (final Resource deleteResource : toDelete) {
                deleteResource(deleteResource);
            }
        }
    }

    protected void doDelete(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws ServletException, IOException {

        final ResourceResolver resolver = request.getResourceResolver();
        UGCImportHelper.checkUserPrivileges(resolver, rrf);

        final RequestParameter filePathParam = request.getRequestParameter("filePath");
        if (null == filePathParam) {
            throw new ServletException("Required parameter 'filePath' missing");
        }
        final String filePath = filePathParam.getString();
        if (!filePath.startsWith(UPLOAD_DIR)) {
            throw new ServletException("Path to file resource lies outside migration import path");
        }
        final Resource fileResource = resolver.getResource(filePath);
        if (null != fileResource && !(fileResource instanceof NonExistingResource)) {
            deleteResource(fileResource);
        }
    }

    /**
     * Delete the file after a successful migration, including any newly-empty parent directories
     * @param fileResource the Resource being deleted
     */
    public static void deleteResource(final Resource fileResource) throws ServletException {
        // do a quick check that we're deleting a resource we're allowed to delete with this method
        final String path = fileResource.getPath();
        if (!path.startsWith(UPLOAD_DIR)) {
            throw new ServletException("Cannot delete resource outside of designated upload folder");
        }
        final Resource parent = fileResource.getParent();

        if (parent.getPath().equals(UPLOAD_DIR)) {
            // don't bother checking for siblings, we won't be going any higher to delete
            try {
                final ResourceResolver resolver = fileResource.getResourceResolver();
                resolver.delete(fileResource);
                resolver.commit();
            } catch (final PersistenceException e) {
                throw new ServletException("Failed to delete a file resource following migration", e);
            }
        } else {
            // Check to see if the resource we want to delete has siblings. If it doesn't, go one level higher and
// check
            // again. If it does have siblings, then just delete the current resource rather than the parent.
            final Iterable<Resource> siblings = parent.getChildren();
            int count = 0;
            boolean deleteParent = true;
            for (final Resource sibling : siblings) {
                count++;
                if (count > 1) {
                    deleteParent = false; // there's at least one thing besides the resource we mean to delete
                }
            }
            if (deleteParent) {
                deleteResource(parent);
            } else {
                try {
                    final ResourceResolver resolver = fileResource.getResourceResolver();
                    resolver.delete(fileResource);
                    resolver.commit();
                } catch (final PersistenceException e) {
                    throw new ServletException("Failed to delete a file resource following migration", e);
                }
            }
        }
    }

    protected void doPut(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws ServletException, IOException {

        final String path = request.getRequestParameter("path").getString();
        final ResourceResolver resolver = request.getResourceResolver();
        UGCImportHelper.checkUserPrivileges(resolver, rrf);

        final Resource resource = resolver.getResource(path);
        if (resource == null) {
            throw new ServletException("Could not find a valid resource for import");
        }
        final String filePath = request.getRequestParameter("filePath").getString();
        if (!filePath.startsWith(ImportFileUploadServlet.UPLOAD_DIR)) {
            throw new ServletException("Path to file resource lies outside migration import path");
        }
        final Resource fileResource = resolver.getResource(filePath);
        if (fileResource == null) {
            throw new ServletException("Could not find a valid file resource to read");
        }
        // get the input stream from the file resource
        Resource file = fileResource.getChild("file");
        if (null != file && !(file instanceof NonExistingResource)) {
            file = file.getChild(JcrConstants.JCR_CONTENT);
            if (null != file && !(file instanceof NonExistingResource)) {
                final ValueMap contentVM = file.getValueMap();
                InputStream inputStream = (InputStream) contentVM.get(JcrConstants.JCR_DATA);
                if (inputStream != null) {
                    final JsonParser jsonParser = new JsonFactory().createParser(inputStream);
                    jsonParser.nextToken(); // get the first token

                    importFile(jsonParser, resource, resolver);
                    deleteResource(fileResource);
                    return;
                }
            }
        }
        throw new ServletException("Unable to read file in provided file resource path");
    }

    /**
     * Handle each of the importable types of ugc content
     * @param jsonParser - the parsing stream
     * @param resource - the parent resource of whatever it is we're importing (must already exist)
     * @throws ServletException
     * @throws IOException
     */
    private void importFile(final JsonParser jsonParser, final Resource resource, final ResourceResolver resolver)
        throws ServletException, IOException {
        final UGCImportHelper importHelper = new UGCImportHelper();
        JsonToken token1 = jsonParser.getCurrentToken();
        importHelper.setSocialUtils(socialUtils);
        if (token1.equals(JsonToken.START_OBJECT)) {
            jsonParser.nextToken();
            if (jsonParser.getCurrentName().equals(ContentTypeDefinitions.LABEL_CONTENT_TYPE)) {
                jsonParser.nextToken();
                final String contentType = jsonParser.getValueAsString();
                if (contentType.equals(ContentTypeDefinitions.LABEL_QNA_FORUM)) {
                    importHelper.setQnaForumOperations(qnaForumOperations);
                } else if (contentType.equals(ContentTypeDefinitions.LABEL_FORUM)) {
                    importHelper.setForumOperations(forumOperations);
                } else if (contentType.equals(ContentTypeDefinitions.LABEL_COMMENTS)) {
                    importHelper.setCommentOperations(commentOperations);
                } else if (contentType.equals(ContentTypeDefinitions.LABEL_CALENDAR)) {
                    importHelper.setCalendarOperations(calendarOperations);
                } else if (contentType.equals(ContentTypeDefinitions.LABEL_JOURNAL)) {
                    importHelper.setJournalOperations(journalOperations);
//                } else if (contentType.equals(ContentTypeDefinitions.LABEL_TALLY)) {
//                    importHelper.setSocialUtils(socialUtils);
                }
                importHelper.setTallyService(tallyOperationsService); // (everything potentially needs tally)
                jsonParser.nextToken(); // content
                if (jsonParser.getCurrentName().equals(ContentTypeDefinitions.LABEL_CONTENT)) {
                    jsonParser.nextToken();
                    token1 = jsonParser.getCurrentToken();
                    if (token1.equals(JsonToken.START_OBJECT) || token1.equals(JsonToken.START_ARRAY)) {
                        if (!resolver.isLive()) {
                            throw new ServletException("Resolver is already closed");
                        }
                    } else {
                        throw new ServletException("Start object token not found for content");
                    }
                    if (token1.equals(JsonToken.START_OBJECT)) {
                        try {
                            if (contentType.equals(ContentTypeDefinitions.LABEL_QNA_FORUM)) {
                                importHelper.importQnaContent(jsonParser, resource, resolver);
                            } else if (contentType.equals(ContentTypeDefinitions.LABEL_FORUM)) {
                                importHelper.importForumContent(jsonParser, resource, resolver);
                            } else if (contentType.equals(ContentTypeDefinitions.LABEL_COMMENTS)) {
                                importHelper.importCommentsContent(jsonParser, resource, resolver);
                            } else if (contentType.equals(ContentTypeDefinitions.LABEL_JOURNAL)) {
                                importHelper.importJournalContent(jsonParser, resource, resolver);
                            } else {
                                LOG.info("Unsupported content type: {}", contentType);
                                jsonParser.skipChildren();
                            }
                            jsonParser.nextToken();
                        } catch (final IOException e) {
                            throw new ServletException(e);
                        }
                        jsonParser.nextToken(); // skip over END_OBJECT
                    } else {
                        try {
                            if (contentType.equals(ContentTypeDefinitions.LABEL_CALENDAR)) {
                                importHelper.importCalendarContent(jsonParser, resource);
                            } else if (contentType.equals(ContentTypeDefinitions.LABEL_TALLY)) {
                                importHelper.importTallyContent(jsonParser, resource);
                            } else {
                                LOG.info("Unsupported content type: {}", contentType);
                                jsonParser.skipChildren();
                            }
                            jsonParser.nextToken();
                        } catch (final IOException e) {
                            throw new ServletException(e);
                        }
                        jsonParser.nextToken(); // skip over END_ARRAY
                    }
                } else {
                    throw new ServletException("Content not found");
                }
            } else {
                throw new ServletException("No content type specified");
            }
        } else {
            throw new ServletException("Invalid Json format");
        }
    }
}
