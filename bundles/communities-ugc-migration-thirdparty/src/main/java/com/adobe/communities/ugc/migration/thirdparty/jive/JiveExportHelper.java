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
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.mongodb.util.JSONParseException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class JiveExportHelper {

    private static final Logger LOG = LoggerFactory.getLogger(JiveExportHelper.class);

    public static void exportPlace(final String apiURL, final String user, final String pass, final String placeID,
                             final JSONWriter writer, final String contentType) throws JSONException, IOException, ServletException {
        final CloseableHttpClient client = buildAPIClient(user, pass);
        writer.object();
        writer.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
        writer.value(contentType);
        writer.key(ContentTypeDefinitions.LABEL_CONTENT);
        writer.object();
        CloseableHttpResponse response = null;
        try {
            String endpoint = apiURL + "/api/core/v3/places/" + placeID + "/contents";
            do {
                response = client.execute(new HttpGet(endpoint));
                endpoint = exportTopicsIteratively(client, writer, response, contentType);
                response.close();
                response = null;
            } while (null != endpoint);
        } finally {
            if (null != response) {
                response.close();
            }
            writer.endObject(); // content object
            writer.endObject(); // outer object
            client.close();
        }
    }

    private static String exportTopicsIteratively(final CloseableHttpClient client, final JSONWriter writer,
                                                  final CloseableHttpResponse response, final String contentType)
            throws ServletException, IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 300) {
            // maybe we want some retry handling here for errors liable to be temporary
            throw new ServletException("Could not get valid response from API");
        }
        final InputStream inputStream = response.getEntity().getContent();
        int c;
        do {
            c = inputStream.read();
        } while (c != 10); // skip the first line
        String nextApiURL = null;
        try {
            final JsonParser jsonParser = new JsonFactory().createParser(inputStream);
            jsonParser.nextToken(); // get the first token
            JsonToken token = jsonParser.getCurrentToken();
            if (!token.equals(JsonToken.START_OBJECT)) {
                throw new ServletException("Unexpected response from API");
            }
            while (!token.equals(JsonToken.END_OBJECT)) {
                token = jsonParser.nextToken();
                if (token.equals(JsonToken.FIELD_NAME)) {
                    if (jsonParser.getCurrentName().equals("links")) {
                        JsonToken innerToken = jsonParser.nextToken();  // skip START_OBJECT
                        while (!innerToken.equals(JsonToken.END_OBJECT)) {
                            innerToken = jsonParser.nextToken();
                            if (innerToken.equals(JsonToken.FIELD_NAME)) {
                                if (jsonParser.getCurrentName().equals("next")) {
                                    jsonParser.nextToken();
                                    nextApiURL = jsonParser.getValueAsString();
                                }
                            }
                        }
                    } else if (jsonParser.getCurrentName().equals("list")) {
                        exportTopics(client, writer, jsonParser, contentType);
                    }
                }
            }
        } catch (final JSONParseException e) {
            throw new ServletException("The API response could not be parsed as valid JSON", e);
        } catch (final Exception e) {
            throw new ServletException("Unknown error", e);
        } finally {
            inputStream.close();
        }
        return nextApiURL;

    }

    private static void exportTopics(final CloseableHttpClient client, final JSONWriter writer, final JsonParser parser,
                                     final String contentType)
            throws IOException, JSONException, ServletException {
        parser.nextToken(); // START_ARRAY
        JsonToken token = parser.nextToken();
        while (!token.equals(JsonToken.END_ARRAY)) {
            exportTopic(client, writer, parser, contentType);
            token = parser.nextToken();
        }
    }

    private static void exportTopic(final CloseableHttpClient client, final JSONWriter writer, final JsonParser parser,
                                    final String contentType)
            throws IOException, JSONException, ServletException {
        // parse entry into appropriate topic type,
        parser.nextToken(); // START_OBJECT
        JsonToken token = parser.getCurrentToken();
        // first token in a topic should be "id" field
        if (!token.equals(JsonToken.FIELD_NAME) || !parser.getCurrentName().equals("id")) {
            throw new PersistenceException("Expected topic object to begin with id field, got '"
                                            + parser.getCurrentName() + "' instead");
        }
        token = parser.nextToken();
        writer.key(parser.getValueAsString());
        JSONWriter topicObject = writer.object();
        String messagesURL = null;
        String parentID = null;
        int replyCount = 0;
        while (!token.equals(JsonToken.END_OBJECT)) {
            if (token.equals(JsonToken.FIELD_NAME)) {
                final String label = parser.getCurrentName();
                if (label.equals("resources")) {
                    // find the "messages" object, then the "ref" value
                    parser.nextToken(); // skip START_OBJECT
                    token = parser.nextToken();
                    while (!token.equals(JsonToken.END_OBJECT)) {
                        if (token.equals(JsonToken.FIELD_NAME)) {
                            if (parser.getCurrentName().equals("messages")) {
                                parser.nextToken(); // skip START_OBJECT
                                token = parser.nextToken();
                                while (!token.equals(JsonToken.END_OBJECT)) {
                                    if (token.equals(JsonToken.FIELD_NAME)) {
                                        if (parser.getCurrentName().equals("ref")) {
                                            parser.nextToken();
                                            messagesURL = parser.getValueAsString();
                                        }
                                    } else if (token.equals(JsonToken.START_OBJECT)) {
                                        parser.skipChildren();
                                    }
                                    token = parser.nextToken();
                                }
                            }
                        } else if (token.equals(JsonToken.START_OBJECT)) {
                            parser.skipChildren();
                        }
                        token = parser.nextToken();
                    }
                } else if (label.equals("author")) {
                    // find the "name" object, and then the "formatted" value
                    parser.nextToken(); // skip START_OBJECT
                    token = parser.nextToken();
                    while (!token.equals(JsonToken.END_OBJECT)) {
                        if (token.equals(JsonToken.FIELD_NAME)) {
                            if (parser.getCurrentName().equals("name")) {
                                parser.nextToken(); // skip START_OBJECT
                                token = parser.nextToken();
                                while (!token.equals(JsonToken.END_OBJECT)) {
                                    if (token.equals(JsonToken.FIELD_NAME)) {
                                        if (parser.getCurrentName().equals("formatted")) {
                                            parser.nextToken();
                                            writer.key("author_display_name");
                                            writer.value(URLEncoder.encode(parser.getValueAsString(), "UTF-8"));
                                        }
                                    } else if (token.equals(JsonToken.START_OBJECT)) {
                                        parser.skipChildren();
                                    }
                                    token = parser.nextToken();
                                }
                            } else if (parser.getCurrentName().equals("jive")) {
                                parser.nextToken(); // skip START_OBJECT
                                token = parser.nextToken();
                                while (!token.equals(JsonToken.END_OBJECT)) {
                                    if (token.equals(JsonToken.FIELD_NAME)) {
                                        if (parser.getCurrentName().equals("username")) {
                                            parser.nextToken();
                                            writer.key("userIdentifier");
                                            writer.value(URLEncoder.encode(parser.getValueAsString(), "UTF-8"));
                                        }
                                    } else if (token.equals(JsonToken.START_OBJECT)) {
                                        parser.skipChildren();
                                    }
                                    token = parser.nextToken();
                                }
                            }
                        } else if (token.equals(JsonToken.START_OBJECT)) {
                            parser.skipChildren();
                        }
                        token = parser.nextToken();
                    }
                } else if (label.equals("content")) {
                    token = parser.nextToken();
                    while (!token.equals(JsonToken.END_OBJECT)) {
                        if (token.equals(JsonToken.FIELD_NAME)) {
                            if (parser.getCurrentName().equals("text")) {
                                parser.nextToken();
                                topicObject.key("jcr:description");
                                topicObject.value(URLEncoder.encode(parser.getValueAsString(), "UTF-8"));
                            }
                        }
                        token = parser.nextToken();
                    }
                    // find the "text" value in the object
                } else if (label.equals("status")) {
                    parser.nextToken();
                    final String status = parser.getValueAsString();
                    if (status.equals("incomplete")) {
                        topicObject.key("isDraft");
                        topicObject.value("true");
                    } else if (status.equals("rejected")) {
                        topicObject.key("approved");
                        topicObject.value("false");
                    } else if (status.equals("published")) {
                        topicObject.key("approved");
                        topicObject.value("true");
                    } else if (status.equals("awaiting moderation")) {
                        // do nothing.
                    }
                } else if (label.equals("archives") || label.equals("restrictReplies")) {
                    parser.nextToken();
                    if (parser.getValueAsString().equals("true")) {
                        topicObject.key("isClosed");
                        topicObject.value("true");
                    }
                } else if (label.equals("published")) {
                    parser.nextToken();
                    // 2016-05-02T13:09:28.553+0000
                    try {
                        final Date added = DateUtils.parseDate(parser.getValueAsString(),
                                new String[]{"y-M-d'T'H:m:s.S"});
                        writer.key("added");
                        writer.value(added.getTime());
                    } catch (final ParseException e) {
                        e.printStackTrace();
                    }
                } else if (label.equals("subject")) {
                    parser.nextToken();
                    topicObject.key("jcr:title");
                    topicObject.value(URLEncoder.encode(parser.getValueAsString(), "UTF-8"));
                } else if (label.equals("replyCount")) {
                    parser.nextToken();
                    replyCount = parser.getIntValue();
                } else if (label.equals("contentID")) {
                    parser.nextToken();
                    parentID = parser.getValueAsString();
                } else if (label.equals("answer") && contentType.equals(ContentTypeDefinitions.LABEL_QNA_FORUM)) {
                    parser.nextToken();
                    final String answer = parser.getValueAsString();
                    if (!"".equals(answer)) {
                        writer.key("cq:tags");
                        writer.array();
                        writer.value("forum:topic/answered");
                        writer.endArray();
                    }
                } else {
                    parser.skipChildren();
                }
            } else if (token.equals(JsonToken.START_OBJECT) || token.equals(JsonToken.START_ARRAY)) {
                parser.skipChildren();
            }
            token = parser.nextToken();
        }
        topicObject.key("sling:resourceType");
        topicObject.value(JiveImportServlet.topicTypes.get(contentType));
        topicObject.key(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS);
        topicObject.array();
        topicObject.value("added");
        topicObject.endArray();
        if (replyCount > 0 && null != messagesURL) {
            topicObject.key(ContentTypeDefinitions.LABEL_REPLIES);
            final JSONWriter repliesObject = topicObject.object();
            exportMessages(client, messagesURL, repliesObject, parentID, contentType);
            repliesObject.endObject();
        }
        topicObject.endObject();
    }

    private static void exportMessages(final CloseableHttpClient client, final String apiURL, final JSONWriter writer,
                                       final String parentID, final String contentType)
            throws IOException, JSONException, ServletException {
        // get messages, export them in nested JSON replies
        final Map<String, Map<String, Object>> messages = new LinkedHashMap<String, Map<String, Object>>();
        String nextAPIURL = apiURL;
        do {
            nextAPIURL = getMessagesIteratively(client, nextAPIURL, contentType, messages);
        } while (null != nextAPIURL);
        exportMessages(writer, messages, parentID, contentType);

    }

    private static String getMessagesIteratively(final CloseableHttpClient client, final String apiURL,
                                            final String contentType, final Map<String, Map<String, Object>> messages)
            throws IOException, ServletException {
        final CloseableHttpResponse response = client.execute(new HttpGet(apiURL));
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 300) {
            // maybe we want some retry handling here for errors liable to be temporary
            throw new ServletException("Could not get valid response from API");
        }
        final InputStream inputStream = response.getEntity().getContent();
        int c;
        do {
            c = inputStream.read();
        } while (c != 10); // skip the first line
        String nextApiURL = null;
        try {
            final JsonParser jsonParser = new JsonFactory().createParser(inputStream);
            jsonParser.nextToken(); // get the first token
            JsonToken token = jsonParser.getCurrentToken();
            if (!token.equals(JsonToken.START_OBJECT)) {
                throw new ServletException("Unexpected response from API");
            }
            while (!token.equals(JsonToken.END_OBJECT)) {
                token = jsonParser.nextToken();
                if (token.equals(JsonToken.FIELD_NAME)) {
                    if (jsonParser.getCurrentName().equals("links")) {
                        JsonToken innerToken = jsonParser.nextToken();  // skip START_OBJECT
                        while (!innerToken.equals(JsonToken.END_OBJECT)) {
                            innerToken = jsonParser.nextToken();
                            if (innerToken.equals(JsonToken.FIELD_NAME)) {
                                if (jsonParser.getCurrentName().equals("next")) {
                                    jsonParser.nextToken();
                                    nextApiURL = jsonParser.getValueAsString();
                                }
                            }
                        }
                    } else if (jsonParser.getCurrentName().equals("list")) {
                        jsonParser.nextToken(); // skip "list"
                        mapMessages(jsonParser, messages, contentType);
                    }
                }
            }
        } catch (final JSONParseException e) {
            throw new ServletException("The API response could not be parsed as valid JSON", e);
        } catch (final Exception e) {
            throw new ServletException("Unknown error", e);
        } finally {
            inputStream.close();
            response.close();
        }
        return nextApiURL;
    }

    private static void mapMessages(final JsonParser parser, final Map<String, Map<String, Object>> messages,
                                    final String contentType) throws IOException {
        parser.nextToken(); // skip START_ARRAY
        JsonToken token = parser.getCurrentToken(); // presumably START_OBJECT, but could be END_ARRAY if no messages
        while (!token.equals(JsonToken.END_ARRAY)) {
            parser.nextToken(); // skip START_OBJECT
            token = parser.getCurrentToken(); // presumable a FIELD_NAME for the "id" field
            // first token in a reply should be "id" field
            if (!token.equals(JsonToken.FIELD_NAME) || !parser.getCurrentName().equals("id")) {
                throw new PersistenceException("Expected topic object to begin with id field, got '"
                        + parser.getCurrentName() + "' instead");
            }
            token = parser.nextToken();
            final String id = parser.getValueAsString();
            final Map<String, Object> message = new HashMap<String, Object>();
            message.put("contentID", id);
            while (!token.equals(JsonToken.END_OBJECT)) {
                if (token.equals(JsonToken.FIELD_NAME)) {
                    final String label = parser.getCurrentName();
                    if (label.equals("author")) {
                        // find the "name" object, and then the "formatted" value
                        parser.nextToken(); // skip START_OBJECT
                        token = parser.nextToken();
                        while (!token.equals(JsonToken.END_OBJECT)) {
                            if (token.equals(JsonToken.FIELD_NAME)) {
                                if (parser.getCurrentName().equals("displayName")) {
                                    parser.nextToken(); // skip START_OBJECT
                                    message.put("author_display_name",
                                            URLEncoder.encode(parser.getValueAsString(), "UTF-8"));
                                } else if (parser.getCurrentName().equals("jive")) {
                                    parser.nextToken(); // skip START_OBJECT
                                    token = parser.nextToken();
                                    while (!token.equals(JsonToken.END_OBJECT)) {
                                        if (token.equals(JsonToken.FIELD_NAME)) {
                                            if (parser.getCurrentName().equals("username")) {
                                                parser.nextToken();
                                                message.put("userIdentifier",
                                                        URLEncoder.encode(parser.getValueAsString(), "UTF-8"));
                                            }
                                        } else if (token.equals(JsonToken.START_OBJECT)) {
                                            parser.skipChildren();
                                        }
                                        token = parser.nextToken();
                                    }
                                } else {
                                    parser.skipChildren();
                                }
                            } else if (token.equals(JsonToken.START_OBJECT)) {
                                parser.skipChildren();
                            }
                            token = parser.nextToken();
                        }
                    } else if (label.equals("content")) {
                        token = parser.nextToken();
                        while (!token.equals(JsonToken.END_OBJECT)) {
                            if (token.equals(JsonToken.FIELD_NAME)) {
                                if (parser.getCurrentName().equals("text")) {
                                    parser.nextToken();
                                    message.put("jcr:description",
                                            URLEncoder.encode(parser.getValueAsString(), "UTF-8"));
                                }
                            }
                            token = parser.nextToken();
                        }
                        // find the "text" value in the object
                    } else if (label.equals("status")) {
                        parser.nextToken();
                        final String status = parser.getValueAsString();
                        if (status.equals("incomplete")) {
                            message.put("isDraft", "true");
                        } else if (status.equals("rejected")) {
                            message.put("approved", "false");
                        } else if (status.equals("published")) {
                            message.put("approved", "true");
                        } else if (status.equals("awaiting moderation")) {
                            // do nothing.
                        }
                    } else if (label.equals("archived") || label.equals("restrictReplies")) {
                        parser.nextToken();
                        if (parser.getValueAsString().equals("true")) {
                            message.put("isClosed", "true");
                        }
                    } else if (label.equals("published")) {
                        parser.nextToken();
                        try {
                            final Date added = DateUtils.parseDate(parser.getValueAsString(),
                                    new String[]{"y-M-d'T'H:m:s.S"});
                            message.put("added", added.getTime());
                        } catch (final ParseException e) {
                            e.printStackTrace();
                        }
                    } else if (label.equals("parent")) {
                        parser.nextToken(); // the parentID is the item at the end of the parent URL
                        final String[] tokens = StringUtils.split(parser.getValueAsString(), "/");
                        message.put("parentID", tokens[tokens.length-1]);
                    } else if (label.equals("replyCount")) {
                        parser.nextToken();
                        message.put("replyCount", parser.getIntValue());
                    } else if (label.equals("answer") && contentType.equals(ContentTypeDefinitions.LABEL_QNA_FORUM)) {
                        parser.nextToken();
                        message.put("is_answer", parser.getBooleanValue());
                    } else {
                        parser.skipChildren();
                    }
                } else if (token.equals(JsonToken.START_OBJECT) || token.equals(JsonToken.START_ARRAY)) {
                    parser.skipChildren();
                }
                token = parser.nextToken();
            }
            messages.put(id, message);
            token = parser.nextToken(); // next token will be either END_ARRAY or START_OBJECT
        }
    }

    private static void exportMessages(final JSONWriter writer, final Map<String, Map<String, Object>> messages,
                                       final String parentID, final String contentType) throws JSONException {
        // iterate over messages. export and then remove any message that has a parent value that ends with the parentID
        final Map<String, Map<String, Object>> children = new LinkedHashMap<String, Map<String, Object>>();
        for (final Map.Entry<String, Map<String, Object>> message : messages.entrySet()) {
            if (message.getValue().get("parentID").equals(parentID)) {
                children.put(message.getKey(), message.getValue());
            }
        }
        for (final String key : children.keySet()) {
            messages.remove(key);
        }
        for (final Map<String, Object> child : children.values()) {

            writer.key((String)child.get("contentID"));
            final JSONWriter messageObject = writer.object();

            messageObject.key("added");
            if (child.containsKey("added")) {
                messageObject.value(child.get("added"));
            } else {
                // if not provided by Jive, use the current export time as the published date
                messageObject.value(Calendar.getInstance().getTime().getTime());
            }
            if (child.containsKey("author_display_name")) {
                messageObject.key("author_display_name");
                messageObject.value(child.get("author_display_name"));
            }
            if (child.containsKey("userIdentifier")) {
                messageObject.key("userIdentifier");
                messageObject.value(child.get("userIdentifier"));
            }
            if (child.containsKey("jcr:description")) {
                messageObject.key("jcr:description");
                messageObject.value(child.get("jcr:description"));
            }
            messageObject.key("sling:resourceType");
            messageObject.value(JiveImportServlet.replyTypes.get(contentType));
            messageObject.key(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS);
            messageObject.array();
            messageObject.value("added");
            messageObject.endArray();

            if (contentType.equals(ContentTypeDefinitions.LABEL_QNA_FORUM)) {
                if (child.containsKey("is_answer") && (Boolean) child.get("is_answer")) {
                    messageObject.key("chosenAnswer_b");
                    messageObject.value("true");
                    messageObject.key("cq:tags");
                    messageObject.array();
                    messageObject.value("forum:topic/chosenanswer");
                    messageObject.endArray();
                }
            }

            if ((Integer)child.get("replyCount") > 0) {
                messageObject.key(ContentTypeDefinitions.LABEL_REPLIES);
                messageObject.object();
                exportMessages(writer, messages, (String) child.get("contentID"), contentType);
                messageObject.endObject();
            }
            messageObject.endObject();
        }
    }

    public static void scanAPIPlaces(final ResourceResolver resolver, final Resource folder, final JSONWriter writer,
                               final String apiURL, final String apiUser, final String apiPass)
            throws ServletException, IOException {

        final CloseableHttpClient client = buildAPIClient(apiUser, apiPass);
        try {
            String endpoint = apiURL + "/api/core/v3/places?fields=placeID,displayName,name";
            do {
                endpoint = scanAPIPlacesIteratively(resolver, folder, writer, endpoint, client);
            } while(null != endpoint);
        } finally {
            client.close();
        }
    }

    private static CloseableHttpClient buildAPIClient(final String apiUser, final String apiPass) {
        final CredentialsProvider provider = new BasicCredentialsProvider();
        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(apiUser, apiPass);
        provider.setCredentials(AuthScope.ANY, credentials);
        return HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
    }

    private static String scanAPIPlacesIteratively(final ResourceResolver resolver, final Resource folder,
                                            final JSONWriter writer, final String apiURL, final CloseableHttpClient client)
            throws ServletException, IOException {
        final CloseableHttpResponse response = client.execute(new HttpGet(apiURL));
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 300) {
            // maybe we want some retry handling here for errors liable to be temporary
            throw new ServletException("Could not get valid response from API");
        }
        final InputStream inputStream = response.getEntity().getContent();
        int c;
        do {
            c = inputStream.read();
        } while (c != 10); // skip the first line
        String nextApiURL = null;
        try {
            final JsonParser jsonParser = new JsonFactory().createParser(inputStream);
            jsonParser.nextToken(); // get the first token
            JsonToken token = jsonParser.getCurrentToken();
            if (!token.equals(JsonToken.START_OBJECT)) {
                throw new ServletException("Unexpected response from API");
            }
            while (!token.equals(JsonToken.END_OBJECT)) {
                token = jsonParser.nextToken();
                if (token.equals(JsonToken.FIELD_NAME)) {
                    if (jsonParser.getCurrentName().equals("links")) {
                        JsonToken innerToken = jsonParser.nextToken();  // skip START_OBJECT
                        while (!innerToken.equals(JsonToken.END_OBJECT)) {
                            innerToken = jsonParser.nextToken();
                            if (innerToken.equals(JsonToken.FIELD_NAME)) {
                                if (jsonParser.getCurrentName().equals("next")) {
                                    jsonParser.nextToken();
                                    nextApiURL = jsonParser.getValueAsString();
                                }
                            }
                        }
                    } else if (jsonParser.getCurrentName().equals("list")) {
                        exportPlaces(resolver, folder, writer, jsonParser);
                    }
                }
            }
        } catch (final JSONParseException e) {
            throw new ServletException("The API response could not be parsed as valid JSON", e);
        } catch (final Exception e) {
            throw new ServletException("Unknown error", e);
        } finally {
            inputStream.close();
            response.close();
        }
        return nextApiURL;
    }

    private static void exportPlaces(final ResourceResolver resolver, final Resource folder, final JSONWriter writer,
                              final JsonParser jsonParser) throws IOException, JSONException {
        Map<String, Object> properties = new HashMap<String, Object>();
        String placeID = null;
        JsonToken innerToken = jsonParser.nextToken();  // skip START_ARRAY
        while (!innerToken.equals(JsonToken.END_ARRAY)) {
            innerToken = jsonParser.nextToken();
            if (innerToken.equals(JsonToken.START_OBJECT)) {
                properties.clear();
                properties.put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED);
                placeID = null;
            } else if (innerToken.equals(JsonToken.END_OBJECT)) {
                if (null == placeID) {
                    LOG.error("Response from Jive included a place item without a placeID");
                    continue;
                }
                writer.object();
                writer.key("placeID");
                writer.value(placeID);
                Resource placeNode;
                placeNode = folder.getChild(placeID);
                if (null != placeNode) {
                    // update
                    final ModifiableValueMap mvm = placeNode.adaptTo(ModifiableValueMap.class);
                    writer.key("name");
                    if (properties.containsKey("name")) {
                        mvm.put("name", properties.get("name"));
                        writer.value(properties.get("name"));
                    } else {
                        LOG.info("node at " + placeID + " does not have a name property");
                        writer.value("");
                        mvm.put("name", "");
                    }
                    writer.key("displayName");
                    if (properties.containsKey("name")) {
                        mvm.put("displayName", properties.get("displayName"));
                        writer.value(properties.get("displayName"));
                    } else {
                        LOG.info("node at " + placeID + " does not have a displayName property");
                        writer.value("");
                        mvm.put("displayName", "");
                    }
                    if (mvm.containsKey("lastExportDate")) {
                        writer.key("lastExportDate");
                        Object lastExportDate = mvm.get("lastExportDate");
                        if (lastExportDate instanceof Calendar) {
                            writer.value(((Calendar)lastExportDate).getTime().getTime());
                        } else {
                            writer.value(lastExportDate.toString());
                        }
                    }
                    if (mvm.containsKey("numTopics")) {
                        writer.key("numTopics");
                        writer.value(mvm.get("numTopics"));
                    }
                } else {
                    resolver.create(folder, placeID, properties);
                    writer.key("name");
                    if (properties.containsKey("name")) {
                        writer.value(properties.get("name"));
                    } else {
                        LOG.info("node at " + placeID + " does not have a name property");
                        writer.value("");
                    }
                    writer.key("displayName");
                    if (properties.containsKey("name")) {
                        writer.value(properties.get("displayName"));
                    } else {
                        LOG.info("node at " + placeID + " does not have a displayName property");
                        writer.value("");
                    }
                }
                writer.endObject();
            } else if (innerToken.equals(JsonToken.FIELD_NAME)) {
                if (jsonParser.getCurrentName().equals("resources")) {
                    jsonParser.nextToken(); // next token is START_OBJECT
                    jsonParser.skipChildren();
                } else if (jsonParser.getCurrentName().equals("placeID")) {
                    jsonParser.nextToken();
                    placeID = jsonParser.getValueAsString();
                } else if (jsonParser.getCurrentName().equals("displayName")) {
                    jsonParser.nextToken();
                    properties.put("displayName", jsonParser.getValueAsString());
                } else if (jsonParser.getCurrentName().equals("name")) {
                    jsonParser.nextToken();
                    properties.put("name", jsonParser.getValueAsString());
                }
            }
        }
    }
}
