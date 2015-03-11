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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.activation.DataSource;
import javax.jcr.Session;
import javax.servlet.ServletException;

import com.adobe.cq.social.tally.client.endpoints.TallyOperationsService;
import com.adobe.cq.social.ugcbase.SocialResource;
import com.adobe.cq.social.ugcbase.SocialResourceConfiguration;
import com.adobe.cq.social.ugcbase.SocialResourceProvider;
import com.adobe.cq.social.ugcbase.SocialResourceUtils;
import org.apache.commons.codec.binary.Base64;
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
import com.adobe.cq.social.commons.Comment;
import com.adobe.cq.social.commons.client.endpoints.OperationException;
import com.adobe.cq.social.forum.client.endpoints.ForumOperations;
import com.adobe.cq.social.ugcbase.SocialUtils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

@Component(label = "UGC Importer for Forum Data",
        description = "Moves forum data within json files into the active SocialResourceProvider", specVersion = "1.0")
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

    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException {
        Writer responseWriter = response.getWriter();
        responseWriter.append("Hello world");
    }
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws ServletException, IOException {

        // get the forum we'll be adding new topics to
        final String path = request.getRequestParameter("path").getString();
        final Resource resource = request.getResourceResolver().getResource(path);
        if (resource == null) {
            throw new ServletException("Could not find a valid resource for export");
        }

        // finally get the uploaded file
        final RequestParameter[] fileRequestParameters = request.getRequestParameters("file");
        if (fileRequestParameters != null && fileRequestParameters.length > 0
                && !fileRequestParameters[0].isFormField()) {

            InputStream inputStream = fileRequestParameters[0].getInputStream();
            JsonParser jsonParser = new JsonFactory().createParser(inputStream);
            jsonParser.nextToken(); //get the first token

            if(jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
                jsonParser.nextToken();
                if(jsonParser.getCurrentName().equals(ContentTypeDefinitions.LABEL_CONTENT_TYPE)) {
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
                                        extractTopic(jsonParser, resource, resource.getResourceResolver());
                                        token = jsonParser.nextToken();
                                    }
                                } catch (final OperationException e) {
                                    throw new ServletException("Encountered an OperationException", e);
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

    protected String getContentType() {
        return ContentTypeDefinitions.LABEL_FORUM;
    }

    private void extractTopic(final JsonParser jsonParser, final Resource resource, final ResourceResolver resolver)
            throws ServletException, IOException, OperationException {

        if (jsonParser.getCurrentToken().equals(JsonToken.END_OBJECT)) {
            return; // replies could just be an empty object (i.e. "ugc:replies":{} ) in which case, do nothing
        }
        final Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("social:key", jsonParser.getCurrentName());
        Resource post = null;
        jsonParser.nextToken();
        if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
            jsonParser.nextToken();
            String author = null;
            List<DataSource> attachments = new ArrayList<DataSource>();
            while (!jsonParser.getCurrentToken().equals(JsonToken.END_OBJECT)) {
                final String label = jsonParser.getCurrentName();
                JsonToken token = jsonParser.nextToken();
                if (jsonParser.getCurrentToken().isScalarValue()) {

                    //either a string, boolean, or long value
                    if (token.isNumeric()) {
                        properties.put(label, jsonParser.getValueAsLong());
                    } else {
                        final String value = jsonParser.getValueAsString();
                        if (value.equals("true") || value.equals("false")) {
                            properties.put(label, jsonParser.getValueAsBoolean());
                        } else {
                            properties.put(label, URLDecoder.decode(value, "UTF-8"));
                            if (label.equals("userIdentifier")) {
                                author = URLDecoder.decode(value, "UTF-8");
                            } else if (label.equals("jcr:description")) {
                                properties.put("message", URLDecoder.decode(value, "UTF-8"));
                            }
                        }
                    }
                } else if (label.equals(ContentTypeDefinitions.LABEL_ATTACHMENTS)) {
                    attachments = getAttachments(jsonParser);
                } else if (label.equals(ContentTypeDefinitions.LABEL_REPLIES) ||
                           label.equals(ContentTypeDefinitions.LABEL_TALLY) ||
                           label.equals(ContentTypeDefinitions.LABEL_SUBNODES)) {
                    // replies and sub-nodesALWAYS come after all other properties and attachments have been listed,
                    // so we can create the post now if we haven't already, and then dive in
                    if (post == null) {
                        try {
                            post = createPost(resource, author, properties, attachments, resolver.adaptTo(Session.class));
                            resProvider = SocialResourceUtils.getSocialResource(post).getResourceProvider();
//                            resProvider.commit(resolver);
                        } catch (Exception e) {
                            throw new ServletException(e.getMessage(), e);
                        }
                    }
                    if (label.equals(ContentTypeDefinitions.LABEL_REPLIES)) {
                        if (token.equals(JsonToken.START_OBJECT)) {
                            jsonParser.nextToken();
                            while (!token.equals(JsonToken.END_OBJECT)) {
                                extractTopic(jsonParser, post, resolver);
                                token = jsonParser.nextToken();
                            }
                        } else {
                            throw new IOException("Expected an object for the subnodes");
                        }
                    } else if (label.equals(ContentTypeDefinitions.LABEL_SUBNODES)) {
                        // TODO - handle the separate types of sub-nodes (eg. voting, tally, translation)
                        if (token.equals(JsonToken.START_OBJECT)) {
                            token = jsonParser.nextToken();
                            try {
                                while (!token.equals(JsonToken.END_OBJECT)) {
                                    final String subnodeType = jsonParser.getCurrentName();
                                    token = jsonParser.nextToken();
                                    if (token.equals(JsonToken.START_OBJECT)) {
                                        jsonParser.skipChildren();
                                        token = jsonParser.nextToken();
                                    }
                                }
                            } catch (final IOException e) {
                                throw new IOException("unable to skip child of sub-nodes", e);
                            }
                        } else {
                            final String field = jsonParser.getValueAsString();
                            throw new IOException("Expected an object for the subnodes. Instead: " + field);
                        }
                    } else if (label.equals(ContentTypeDefinitions.LABEL_TALLY)) {
                        UGCImportHelper.extractTally(post, jsonParser, resolver, tallyOperationsService);
                    }

                } else if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
                    properties.put(label, UGCImportHelper.extractSubmap(jsonParser));
                } else if (jsonParser.getCurrentToken().equals(JsonToken.START_ARRAY)) {
                    jsonParser.nextToken(); //skip the START_ARRAY token
                    if (label.equals(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS)) {
                        while (!jsonParser.getCurrentToken().equals(JsonToken.END_ARRAY)) {
                            final String timestampLabel = jsonParser.getValueAsString();
                            if (properties.containsKey(timestampLabel) && properties.get(timestampLabel) instanceof Long) {
                                final Calendar calendar = new GregorianCalendar();
                                calendar.setTimeInMillis((Long) properties.get(timestampLabel));
                                properties.put(timestampLabel, calendar.getTime());
                            }
                            jsonParser.nextToken();
                        }
                    } else {
                        final List<String> subArray = new ArrayList<String>();
                        while (!jsonParser.getCurrentToken().equals(JsonToken.END_ARRAY)) {
                            subArray.add(jsonParser.getValueAsString());
                            jsonParser.nextToken();
                        }
                        String[] strings = new String[subArray.size()];
                        for (int i=0; i<subArray.size(); i++) { strings[i] = subArray.get(i); }
                        properties.put(label, strings);
                    }
                }
                jsonParser.nextToken();
            }
            if (post == null) {
                try {
                    post = createPost(resource, author, properties, attachments, resolver.adaptTo(Session.class));
                    resProvider = SocialResourceUtils.getSocialResource(post).getResourceProvider();
//                    resProvider.commit(resolver);
                } catch (Exception e) {
                    throw new ServletException(e.getMessage(), e);
                }
            }
        } else {
            throw new IOException("Improperly formed JSON - expected an OBJECT_START token, but got " + jsonParser.getCurrentToken().toString());
        }
        return;
    }

    protected Resource createPost(final Resource resource, final String author, final Map<String, Object> properties,
                                  final List<DataSource> attachments, final Session session) throws OperationException {
        if(populateMessage(properties)) {
            return forumOperations.create(resource, author, properties, attachments, session);
        } else {
            return null;
        }
    }

    protected boolean populateMessage(Map<String, Object> properties) {

        String message = null;
        if (properties.containsKey(Comment.PROP_MESSAGE)) {
            message = properties.get(Comment.PROP_MESSAGE).toString();
        }
        if (message == null || message.equals("")) {
            if (properties.containsKey("jcr:title")) {
                message = properties.get("jcr:title").toString();
                if (message == null || message.equals("")) {
                    // If title and message are both blank, we skip this entry and
                    // TODO - log the fact that we skipped it
                    return false;
                } else {
                    properties.put(Comment.PROP_MESSAGE, message);
                    properties.put("message", message);
                }
            } else {
                // If title and message are both blank, we skip this entry and
                // TODO - log the fact that we skipped it
                return false;
            }
        }
        return true;
    }

    private List<DataSource> getAttachments(final JsonParser jsonParser) throws IOException {
        // an attachment has only 3 fields - jcr:data, filename, jcr:mimeType
        List<DataSource> attachments = new ArrayList<DataSource>();
        JsonToken token = jsonParser.nextToken(); // skip START_ARRAY token
        String filename;
        String mimeType;
        String data;
        InputStream inputStream;
        while (token.equals(JsonToken.START_OBJECT)) {
            filename = null;
            mimeType = null;
            inputStream = null;
            byte[] databytes = null;
            token = jsonParser.nextToken();
            while (!token.equals(JsonToken.END_OBJECT)) {
                final String label = jsonParser.getCurrentName();
                jsonParser.nextToken();
                if (label.equals("filename")) {
                    filename = jsonParser.getValueAsString();
                } else if (label.equals("jcr:mimeType")) {
                    mimeType = jsonParser.getValueAsString();
                } else if (label.equals("jcr:data")) {
                    databytes = Base64.decodeBase64(jsonParser.getValueAsString());
                    inputStream = new ByteArrayInputStream(databytes);
                }
                token = jsonParser.nextToken();
            }
            if (filename != null && mimeType != null && inputStream != null) {
                attachments.add(new UGCImportHelper.AttachmentStruct(filename, inputStream, mimeType, databytes.length));
            } else {
                // TODO - log an error
            }
            token = jsonParser.nextToken();
        }
        return attachments;
    }
}
