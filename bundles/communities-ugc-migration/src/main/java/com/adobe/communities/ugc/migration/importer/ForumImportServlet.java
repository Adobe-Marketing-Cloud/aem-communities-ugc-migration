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
import java.io.Writer;

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
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import com.adobe.cq.social.commons.comments.endpoints.CommentOperations;
import com.adobe.cq.social.forum.client.endpoints.ForumOperations;
import com.adobe.cq.social.srp.SocialResourceProvider;
import com.adobe.cq.social.tally.client.endpoints.TallyOperationsService;
import com.adobe.cq.social.ugcbase.SocialUtils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

@Component(label = "UGC Importer for Forum Data",
        description = "Moves forum data within json files into the active SocialResourceProvider",
        specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/forum/import")})
public class ForumImportServlet extends SlingAllMethodsServlet {

    /**
     * Social Utils Service.
     */
    @Reference
    private SocialUtils socialUtils;

    private SocialResourceProvider resProvider;

    @Reference
    private ForumOperations forumOperations;

    @Reference
    private TallyOperationsService tallyOperationsService;

    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException {
        Writer responseWriter = response.getWriter();
        responseWriter.append("Hello world");
    }

    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws ServletException, IOException {

        final UGCImportHelper importHelper = new UGCImportHelper();
        importHelper.setForumOperations(forumOperations);
        importHelper.setTallyService(tallyOperationsService);
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
            jsonParser.nextToken(); // get the first token

            if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
                jsonParser.nextToken();
                if (jsonParser.getCurrentName().equals(ContentTypeDefinitions.LABEL_CONTENT_TYPE)) {
                    jsonParser.nextToken();
                    final String contentType = jsonParser.getValueAsString();
                    if (contentType.equals(getContentType())) {
                        jsonParser.nextToken(); // content
                        if (jsonParser.getCurrentName().equals(ContentTypeDefinitions.LABEL_CONTENT)) {
                            jsonParser.nextToken(); // startObject
                            if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
                                JsonToken token = jsonParser.nextToken(); // social:key
                                try {
                                    while (!token.equals(JsonToken.END_OBJECT)) {
                                        importHelper.extractTopic(jsonParser, resource,
                                            resource.getResourceResolver(), getOperationsService());
                                        token = jsonParser.nextToken();
                                    }
// } catch (final OperationException e) {
// throw new ServletException("Encountered an OperationException", e);
                                } catch (final IOException e) {
                                    throw new ServletException("Encountered an IOException", e);
                                }
                            } else {
                                throw new ServletException("Start object token not found for content");
                            }
                        } else {
                            throw new ServletException("Content not found");
                        }
                    } else {
                        throw new ServletException("Expected forum data");
                    }
                } else {
                    throw new ServletException("Content Type not specified");
                }
            } else {
                throw new ServletException("Invalid Json format");
            }
        }
        return;
    }

    protected CommentOperations getOperationsService() {
        return forumOperations;
    }

    protected String getContentType() {
        return ContentTypeDefinitions.LABEL_FORUM;
    }

}
