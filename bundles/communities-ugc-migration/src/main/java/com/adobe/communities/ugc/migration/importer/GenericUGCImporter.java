/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * __________________
 *
 *  Copyright 2015 Adobe Systems Incorporated
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated and its
 * suppliers and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 **************************************************************************/
package com.adobe.communities.ugc.migration.importer;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.StringTokenizer;

import javax.servlet.ServletException;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import com.adobe.cq.social.forum.client.endpoints.ForumOperations;
import com.adobe.cq.social.qna.client.endpoints.QnaForumOperations;
import com.adobe.cq.social.tally.client.endpoints.TallyOperationsService;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

@Component(label = "UGC Importer for All UGC Data",
        description = "Moves ugc data within json files into the active SocialResourceProvider", specVersion = "1.0")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/ugc/import")})
public class GenericUGCImporter extends SlingAllMethodsServlet {

    @Reference
    private ForumOperations forumOperations;

    @Reference
    private QnaForumOperations qnaForumOperations;

    @Reference
    private TallyOperationsService tallyOperationsService;

    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws ServletException, IOException {

        final UGCImportHelper importHelper = new UGCImportHelper();

        // get the forum we'll be adding new topics to
        final String path = request.getRequestParameter("path").getString();
        final Resource resource = request.getResourceResolver().getResource(path);
        if (resource == null) {
            throw new ServletException("Could not find a valid resource for import");
        }

        // finally get the uploaded file
        final RequestParameter[] fileRequestParameters = request.getRequestParameters("file");
        if (fileRequestParameters != null && fileRequestParameters.length > 0
                && !fileRequestParameters[0].isFormField()) {

            InputStream inputStream = fileRequestParameters[0].getInputStream();
            JsonParser jsonParser = new JsonFactory().createParser(inputStream);
            jsonParser.nextToken(); //get the first token

            if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
                jsonParser.nextToken();
                while (!jsonParser.getCurrentToken().equals(JsonToken.END_OBJECT)) {
                    final String relPath = jsonParser.getCurrentName();
                    jsonParser.nextToken();
                    if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
                        jsonParser.nextToken();
                        if(jsonParser.getCurrentName().equals(ContentTypeDefinitions.LABEL_CONTENT_TYPE)) {
                            jsonParser.nextToken();
                            final String contentType = jsonParser.getValueAsString();
                            jsonParser.nextToken(); // content
                            if (jsonParser.getCurrentName().equals(ContentTypeDefinitions.LABEL_CONTENT)) {
                                jsonParser.nextToken();
                                if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
                                    final ResourceResolver resolver = resource.getResourceResolver();
                                    final String rootPath = resource.getPath() + relPath;
                                    Resource parentResource = resolver.resolve(rootPath);

                                    if (parentResource.isResourceType("sling:nonexisting")) {
                                        StringTokenizer st = new StringTokenizer(relPath, "/", false);
                                        parentResource = resource;
                                        String parentPath = resource.getPath();
                                        while(st.hasMoreTokens()) {
                                            final String token = st.nextToken();
                                            parentPath += "/" + token;
                                            parentResource = resolver.resolve(parentPath);
                                            if (parentResource.isResourceType("sling:nonexisting")) {
                                                parentResource = resolver.create(parentResource.getParent(), token,
                                                        new HashMap<String, Object>());
                                            }
                                        }

                                    }
                                    try {
                                        importHelper.setTallyService(tallyOperationsService);
                                        if (contentType.equals(ContentTypeDefinitions.LABEL_QNA_FORUM)) {
                                            importHelper.setQnaForumOperations(qnaForumOperations);
                                            importHelper.importQnaContent(jsonParser, parentResource, resolver);
                                        } else if (contentType.equals(ContentTypeDefinitions.LABEL_FORUM)) {
                                            importHelper.setForumOperations(forumOperations);
                                            importHelper.importForumContent(jsonParser, parentResource, resolver);
                                        } else if (contentType.equals(ContentTypeDefinitions.LABEL_COMMENTS)) {
                                            importHelper.importCommentsContent(jsonParser, parentResource, resolver);
                                        } else if (contentType.equals(ContentTypeDefinitions.LABEL_JOURNAL)) {
                                            importHelper.importJournalContent(jsonParser, parentResource, resolver);
                                        } else if (contentType.equals(ContentTypeDefinitions.LABEL_CALENDAR)) {
                                            importHelper.importCalendarContent(jsonParser, parentResource, resolver);
                                        } else if (contentType.equals(ContentTypeDefinitions.LABEL_TALLY)) {
                                            importHelper.importTallyContent(jsonParser, parentResource, resolver);
                                        } else {
                                            // TODO - not needed by 5.6.1 customers, but still need to implement reviews
                                            jsonParser.skipChildren();
                                        }
                                        jsonParser.nextToken();
                                    } catch (Exception e) {
                                        throw new ServletException(e);
                                    }
                                } else {
                                    throw new ServletException("Start object token not found for content");
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
            } else {
                throw new ServletException("Invalid Json format");
            }
        } else {
            throw new ServletException("No file provided for UGC data");
        }
    }
}
