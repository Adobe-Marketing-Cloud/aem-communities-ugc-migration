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

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletException;

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
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import com.adobe.cq.social.calendar.client.endpoints.CalendarOperations;
import com.adobe.cq.social.commons.comments.endpoints.CommentOperations;
import com.adobe.cq.social.forum.client.endpoints.ForumOperations;
import com.adobe.cq.social.qna.client.endpoints.QnaForumOperations;
import com.adobe.cq.social.tally.client.endpoints.TallyOperationsService;
import com.adobe.cq.social.ugcbase.SocialUtils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.sling.commons.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(label = "UGC Importer for All UGC Data",
        description = "Moves ugc data within json files into the active SocialResourceProvider", specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/ugc/import")})
public class GenericUGCImporter extends SlingAllMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(GenericUGCImporter.class);

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
    private SocialUtils socialUtils;

    @Reference
    private ResourceResolverFactory rrf;

    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws ServletException, IOException {

        final ResourceResolver resolver = request.getResourceResolver();

        UGCImportHelper.checkUserPrivileges(resolver, rrf);

        // finally get the uploaded file
        final RequestParameter[] fileRequestParameters = request.getRequestParameters("file");
        if (fileRequestParameters != null && fileRequestParameters.length > 0
                && !fileRequestParameters[0].isFormField()) {

            if (fileRequestParameters[0].getFileName().endsWith(".json")) {
                // if upload is a single json file...

                // get the resource we'll be adding new content to
                final String path = request.getRequestParameter("path").getString();
                final Resource resource = resolver.getResource(path);
                if (resource == null) {
                    throw new ServletException("Could not find a valid resource for import");
                }
                final InputStream inputStream = fileRequestParameters[0].getInputStream();
                final JsonParser jsonParser = new JsonFactory().createParser(inputStream);
                jsonParser.nextToken(); // get the first token

                importFile(jsonParser, resource);
            } else if (fileRequestParameters[0].getFileName().endsWith(".zip")) {
                ZipInputStream zipInputStream;
                try {
                    zipInputStream = new ZipInputStream(fileRequestParameters[0].getInputStream());
                } catch (IOException e) {
                    throw new ServletException("Could not open zip archive");
                }

                try {
                    final RequestParameter[] paths = request.getRequestParameters("path");
                    int counter = 0;
                    ZipEntry zipEntry = zipInputStream.getNextEntry();
                    while (zipEntry != null && paths.length > counter) {
                        final String path = paths[counter].getString();
                        final Resource resource = resolver.getResource(path);
                        if (resource == null) {
                            throw new ServletException("Could not find a valid resource for import");
                        }

                        final JsonParser jsonParser = new JsonFactory().createParser(zipInputStream);
                        jsonParser.nextToken(); // get the first token
                        importFile(jsonParser, resource);
                        zipInputStream.closeEntry();
                        zipEntry = zipInputStream.getNextEntry();
                        counter++;
                    }
                } finally {
                    zipInputStream.close();
                }
            } else {
                throw new ServletException("Unrecognized file input type");
            }
        } else {
            throw new ServletException("No file provided for UGC data");
        }
    }

    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws ServletException, IOException {

        final ResourceResolver resolver = request.getResourceResolver();

        UGCImportHelper.checkUserPrivileges(resolver, rrf);

        final String path = request.getRequestParameter("path").getString();
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

                    importFile(jsonParser, resource);
                    ImportFileUploadServlet.deleteResource(fileResource);
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
    private void importFile(final JsonParser jsonParser, final Resource resource) throws ServletException, IOException {
        final UGCImportHelper importHelper = new UGCImportHelper();
        JsonToken token1 = jsonParser.getCurrentToken();
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
                } else if (contentType.equals(ContentTypeDefinitions.LABEL_TALLY)) {
                    importHelper.setSocialUtils(socialUtils);
                }
                importHelper.setTallyService(tallyOperationsService); // (everything potentially needs tally)
                jsonParser.nextToken(); // content
                if (jsonParser.getCurrentName().equals(ContentTypeDefinitions.LABEL_CONTENT)) {
                    jsonParser.nextToken();
                    token1 = jsonParser.getCurrentToken();
                    ResourceResolver resolver;
                    if (token1.equals(JsonToken.START_OBJECT) || token1.equals(JsonToken.START_ARRAY)) {
                        resolver = resource.getResourceResolver();
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
                            } else if (contentType.equals(ContentTypeDefinitions.LABEL_JOURNAL)) {
                                importHelper.importJournalContent(jsonParser, resource, resolver);
                            } else if (contentType.equals(ContentTypeDefinitions.LABEL_COMMENTS)) {
                                importHelper.importCommentsContent(jsonParser, resource, resolver);
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
