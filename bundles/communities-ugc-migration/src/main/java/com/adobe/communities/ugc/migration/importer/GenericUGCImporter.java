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
import com.adobe.cq.social.calendar.client.endpoints.CalendarOperations;
import com.adobe.cq.social.commons.comments.endpoints.CommentOperations;
import com.adobe.cq.social.forum.client.endpoints.ForumOperations;
import com.adobe.cq.social.qna.client.endpoints.QnaForumOperations;
import com.adobe.cq.social.tally.client.endpoints.TallyOperationsService;
import com.adobe.cq.social.ugcbase.SocialUtils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

@Component(label = "UGC Importer for All UGC Data",
        description = "Moves ugc data within json files into the active SocialResourceProvider", specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/ugc/import")})
public class GenericUGCImporter extends SlingAllMethodsServlet {

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
            jsonParser.nextToken(); // get the first token

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
                                } else if (contentType.equals(ContentTypeDefinitions.LABEL_COMMENTS)) {
                                    importHelper.importCommentsContent(jsonParser, resource, resolver);
                                } else {
                                    jsonParser.skipChildren();
                                }
                                jsonParser.nextToken();
                            } catch (Exception e) {
                                throw new ServletException(e);
                            }
                            jsonParser.nextToken(); // skip over END_OBJECT
                        } else {
                            try {
                                if (contentType.equals(ContentTypeDefinitions.LABEL_JOURNAL)) {
                                    importHelper.importJournalContent(jsonParser, resource, resolver);
                                } else if (contentType.equals(ContentTypeDefinitions.LABEL_CALENDAR)) {
                                    jsonParser.nextToken(); // we skip START_ARRAY here
                                    importHelper.importCalendarContent(jsonParser, resource, resolver);
                                } else if (contentType.equals(ContentTypeDefinitions.LABEL_TALLY)) {
                                    importHelper.importTallyContent(jsonParser, resource, resolver);
                                } else {
                                    jsonParser.skipChildren();
                                }
                                jsonParser.nextToken();
                            } catch (Exception e) {
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
        } else {
            throw new ServletException("No file provided for UGC data");
        }
    }
}
