/*************************************************************************
 *
 * ADOBE SYSTEMS INCORPORATED
 * Copyright 2016 Adobe Systems Incorporated
 * All Rights Reserved.
 *
 * NOTICE:  Adobe permits you to use, modify, and distribute this file in accordance with the
 * terms of the Adobe license agreement accompanying it.  If you have received this file from a
 * source other than Adobe, then your use, modification, or distribution of it requires the prior
 * written permission of Adobe.
 **************************************************************************/
package com.adobe.communities.ugc.migration.thirdparty.jive;

import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import com.adobe.communities.ugc.migration.importer.ImportFileUploadServlet;
import com.adobe.communities.ugc.migration.importer.UGCImportHelper;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.mongodb.util.JSONParseException;
import org.apache.commons.codec.binary.Base64;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.NonExistingResource;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

@Component(label = "UGC Migration API Importer (Jive)",
        description = "Accepts an API URL and credential for Jive, exports its contents and saves in jcr tree",
        specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/ugc/api_import/jive")})

public class JiveImportServlet extends SlingAllMethodsServlet {

    @Reference
    protected ResourceResolverFactory rrf;

    private static final Logger LOG = LoggerFactory.getLogger(JiveImportServlet.class);

    public final static String UPLOAD_DIR = "/etc/migration/jive";

    public final static Map<String, String> topicTypes = new HashMap<String, String>();
    public final static Map<String, String> replyTypes = new HashMap<String, String>();
    {
        topicTypes.put(ContentTypeDefinitions.LABEL_FORUM, "social%2Fforum%2Fcomponents%2Fhbs%2Ftopic");
        topicTypes.put(ContentTypeDefinitions.LABEL_QNA_FORUM, "social%2Fqna%2Fcomponents%2Fhbs%2Ftopic");
        replyTypes.put(ContentTypeDefinitions.LABEL_FORUM, "social%2Fforum%2Fcomponents%2Fhbs%2Fpost");
        replyTypes.put(ContentTypeDefinitions.LABEL_QNA_FORUM, "social%2Fqna%2Fcomponents%2Fhbs%2Fpost");
    }

    protected void doGet(@Nonnull final SlingHttpServletRequest request, @Nonnull final SlingHttpServletResponse response)
        throws ServletException, IOException {
        // fetch nodes of saved jive migrations, return their contents as JSON
        final ResourceResolver resolver = request.getResourceResolver();

        UGCImportHelper.checkUserPrivileges(resolver, rrf);
        final RequestParameter url = request.getRequestParameter("url");
        if (null != url) {
            final String apiURL = url.getString();
            final String apiUser = request.getRequestParameter("user").getString();
            final String apiPass = request.getRequestParameter("pass").getString();
            final String placeID = request.getRequestParameter("placeID").getString();
            String contentType;
            final RequestParameter content = request.getRequestParameter("contentType");
            if (null == content) {
                contentType = ContentTypeDefinitions.LABEL_FORUM;
            } else {
                contentType = content.getString();
                if (!topicTypes.containsKey(contentType)) {
                    LOG.warn("unsupported content type " + contentType);
                    contentType = ContentTypeDefinitions.LABEL_FORUM;
                }
            }
            final JSONWriter writer = new JSONWriter(response.getWriter());
            writer.setTidy(true);
            try {
                JiveExportHelper.exportPlace(apiURL, apiUser, apiPass, placeID, writer, contentType);
            } catch (final JSONException e) {
                throw new ServletException("Could not export Jive place", e);
            }
            return;
        }

        final Resource parentResource = resolver.getResource(UPLOAD_DIR);
        final JSONWriter writer = new JSONWriter(response.getWriter());
        writer.setTidy(true);
        if (null == parentResource || parentResource instanceof NonExistingResource) {
            createJiveMigrationDirectory(resolver);
            try { // no upload folder existed before, so there's nothing to search
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
                // check that the type is "sling:Folder" and that it contains the jive export properties
                if (vm.containsKey(JcrConstants.JCR_PRIMARYTYPE)
                        && vm.get(JcrConstants.JCR_PRIMARYTYPE).equals("sling:Folder")
                        && vm.containsKey("migration:jive:siteURL") && vm.containsKey("migration:jive:user")
                        && vm.containsKey("migration:jive:pass") && vm.containsKey("migration:lastSyncDate")) {

                    writer.object();
                    writer.key("folderName");
                    writer.value(folder.getName());
                    writer.key("siteURL");
                    writer.value(vm.get("migration:jive:siteURL"));
                    // dig into the child nodes recursively to build up the "files" array
                    writer.key("places");
                    writer.array();
                    Iterable<Resource> children = folder.getChildren();
                    for (final Resource child : children) {
                        final ValueMap place = child.adaptTo(ValueMap.class);
                        writer.object();
                        writer.key("placeId");
                        writer.value(place.get("placeId"));
                        writer.key("name");
                        writer.value(place.get("name"));
                        if (place.containsKey("lastExportDate")) {
                            writer.key("lastExportDate");
                            Object lastExportDate = place.get("lastExportDate");
                            if (lastExportDate instanceof Calendar) {
                                writer.value(((Calendar)lastExportDate).getTime().getTime());
                            } else {
                                writer.value(lastExportDate.toString());
                            }
                        }
                        if (place.containsKey("numTopics")) {
                            writer.key("numTopics");
                            writer.value(place.get("numTopics"));
                        }
                        writer.endObject();
                    }
                    writer.endArray();
                    writer.key("lastSyncDate"); // record the date here to get the time we last refreshed place data
                    writer.value(((GregorianCalendar) vm.get("migration:lastSyncDate")).getTime().getTime());
                    writer.endObject();
                }
            }
            writer.endArray();
        } catch (final JSONException e) {
            throw new ServletException("Unable to output JSON", e);
        }

    }

    // use provided URL and credentials to get list of places, store them in a jive migration node
    protected void doPost(@Nonnull final SlingHttpServletRequest request, @Nonnull final SlingHttpServletResponse response)
            throws ServletException, IOException {

        final ResourceResolver resolver = request.getResourceResolver();

        UGCImportHelper.checkUserPrivileges(resolver, rrf);

        Resource parentResource = resolver.getResource(UPLOAD_DIR);
        if (null == parentResource || parentResource instanceof NonExistingResource) {
            parentResource = createJiveMigrationDirectory(resolver);
        }

        // get the credentials and URL
        final String apiURL = request.getRequestParameter("url").getString();
        final String apiUser = request.getRequestParameter("user").getString();
        final String apiPass = request.getRequestParameter("pass").getString();

        // create the folder name for the place descriptor nodes
        final String deterministicFolderName = getFolderName(apiURL);
        // record the folder name for sending back with the response
        final JSONWriter writer = new JSONWriter(response.getWriter());
        writer.setTidy(true);
        final Calendar lastSyncDate = new GregorianCalendar();
        try {
            writer.object();
            writer.key("folderName");
            writer.value(deterministicFolderName);
            writer.key("siteURL");
            writer.value(apiURL);
            writer.key("lastSyncDate");
            writer.value(lastSyncDate.getTime().getTime()); // <-- not a typo
            writer.key("places");
            writer.array();
        } catch (JSONException e) {
            throw new ServletException("Unable to use JSONWriter", e);
        }
        Resource folder;

        final Map<String, Object> folderProperties = new HashMap<String, Object>();
        folderProperties.put(JcrConstants.JCR_PRIMARYTYPE, "sling:Folder");
        folderProperties.put("siteURL", apiURL);
        folderProperties.put("lastSyncDate", lastSyncDate);
        // check if the folder already exists
        folder = parentResource.getChild(deterministicFolderName);
        if (null != folder) {
            // folder already exists, so just update the last sync date
            final ModifiableValueMap mvm = folder.adaptTo(ModifiableValueMap.class);
            mvm.putAll(folderProperties);
        } else {
            // actually create the folder
            folder = resolver.create(parentResource, deterministicFolderName, folderProperties);
        }

        // prepare to scan the jive places
        try {
            JiveExportHelper.scanAPIPlaces(resolver, folder, writer, apiURL, apiUser, apiPass);
            resolver.commit();
        } catch (final Exception e) {
            resolver.revert(); // clean up after ourselves
            throw new ServletException(e);
        } finally {
            resolver.close();
            try {
                // close our JSONWriter
                writer.endArray();
                writer.endObject();
            } catch (final JSONException e) {
                throw new ServletException("Unable to close JSONWriter", e);
            }
        }
    }

    // use selected jive place id's to perform exports, saving them as JSON files in jcr migration directory
    protected void doPut(@Nonnull final SlingHttpServletRequest request, @Nonnull final SlingHttpServletResponse response)
            throws ServletException, IOException {
        final ResourceResolver resolver = request.getResourceResolver();

        UGCImportHelper.checkUserPrivileges(resolver, rrf);

        Resource parentResource = resolver.getResource(UPLOAD_DIR);
        if (null == parentResource || parentResource instanceof NonExistingResource) {
            throw new ServletException("upload directory doesn't exist");
        }

        final String apiURL = request.getRequestParameter("url").getString();
        final String apiUser = request.getRequestParameter("user").getString();
        final String apiPass = request.getRequestParameter("pass").getString();
        final String[] placeIDs = request.getParameterValues("placeIDs");

        final String folderName = getFolderName(apiURL);
        Resource folder = parentResource.getChild(folderName);
        if (null == folder || folder instanceof NonExistingResource) {
            // create folder
            final Map<String, Object> folderProperties = new HashMap<String, Object>();
            folderProperties.put("siteURL", apiURL);
            folderProperties.put(JcrConstants.JCR_PRIMARYTYPE, "sling:Folder");
            folder = resolver.create(parentResource, folderName, folderProperties);
        }
        Resource importUploadDir = resolver.getResource(ImportFileUploadServlet.UPLOAD_DIR);
        Resource uploadDir;
        if (null == importUploadDir || importUploadDir instanceof NonExistingResource) {
            // create migration directory if needed
            final String[] dir_parts = ImportFileUploadServlet.UPLOAD_DIR.split("/");
            final StringBuilder parentPath = new StringBuilder("/");
            final Map<String, Object> folderProperties = new HashMap<String, Object>();
            folderProperties.put(JcrConstants.JCR_PRIMARYTYPE, "sling:Folder");
            for (final String dir : dir_parts) {
                if (dir.equals("")) {
                    continue;
                }
                importUploadDir = resolver.getResource(parentPath + dir);
                if (null == importUploadDir || importUploadDir instanceof NonExistingResource) {
                    importUploadDir = resolver.getResource(parentPath.toString());
                    resolver.create(importUploadDir, dir, folderProperties);
                }
                parentPath.append(dir).append("/");
            }
            folderProperties.put("uploadedFileName", apiURL);
            folderProperties.put("uploadDate", new GregorianCalendar());
            uploadDir = resolver.create(importUploadDir, folderName, folderProperties);
        } else {
            uploadDir = importUploadDir.getChild(folderName);
        }

        /***
         * The idea here was to power exports through a UI - an effort that may later be revisited, but for now
         * only the cURL-powered version of exporting Jive places is supported. This method is incomplete.
         * -- masonw, 2016-08-31
         */
    }



    private Resource createJiveMigrationDirectory(final ResourceResolver resolver) throws PersistenceException {
        // create migration directory if needed
        final String[] dir_parts = UPLOAD_DIR.split("/");
        final StringBuilder parentPath = new StringBuilder("/");
        final Map<String, Object> folderProperties = new HashMap<String, Object>();
        folderProperties.put(JcrConstants.JCR_PRIMARYTYPE, "sling:Folder");
        Resource currentParent = null;
        for (final String dir : dir_parts) {
            if (dir.equals("")) {
                continue;
            }
            currentParent = resolver.getResource(parentPath + dir);
            if (null == currentParent || currentParent instanceof NonExistingResource) {
                currentParent = resolver.getResource(parentPath.toString());
                resolver.create(currentParent, dir, folderProperties);
            }
            parentPath.append(dir).append("/");
        }
        resolver.commit();
        return currentParent;
    }

    private String getFolderName(final String apiURL) throws ServletException {
        final MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e) {
            throw new ServletException("MD5 algorithm not known");
        }
        byte[] hashedURL = md5.digest(apiURL.getBytes());
        return new String(Base64.encodeBase64(hashedURL));
    }
}
