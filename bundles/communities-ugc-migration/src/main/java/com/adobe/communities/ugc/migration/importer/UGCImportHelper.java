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
import java.io.OutputStream;
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

import org.apache.commons.codec.binary.Base64;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import com.adobe.cq.social.commons.Comment;
import com.adobe.cq.social.commons.comments.endpoints.CommentOperations;
import com.adobe.cq.social.forum.client.endpoints.ForumOperations;
import com.adobe.cq.social.qna.client.endpoints.QnaForumOperations;
import com.adobe.cq.social.scf.OperationException;
import com.adobe.cq.social.srp.SocialResourceProvider;
import com.adobe.cq.social.tally.Poll;
import com.adobe.cq.social.tally.Tally;
import com.adobe.cq.social.tally.Voting;
import com.adobe.cq.social.tally.client.api.RatingSocialComponent;
import com.adobe.cq.social.tally.client.api.VotingSocialComponent;
import com.adobe.cq.social.tally.client.endpoints.TallyOperationsService;
import com.adobe.cq.social.ugcbase.core.SocialResourceUtils;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class UGCImportHelper {

    @Reference
    private ForumOperations forumOperations;

    @Reference
    private QnaForumOperations qnaForumOperations;

    @Reference
    private TallyOperationsService tallyOperationsService;

    private SocialResourceProvider resProvider;

    public UGCImportHelper() {
    }

    public void setTallyService(final TallyOperationsService tallyOperationsService) {
        if (this.tallyOperationsService == null)
            this.tallyOperationsService = tallyOperationsService;
    }

    public void setForumOperations(final ForumOperations forumOperations) {
        if (this.forumOperations == null)
            this.forumOperations = forumOperations;
    }

    public void setQnaForumOperations(final QnaForumOperations qnaForumOperations) {
        if (this.qnaForumOperations == null)
            this.qnaForumOperations = qnaForumOperations;
    }

    public Resource extractResource(final JsonParser parser, final SocialResourceProvider provider,
        final ResourceResolver resolver, final String path) throws IOException {
        final Map<String, Object> properties = new HashMap<String, Object>();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.getCurrentName();
            parser.nextToken();
            JsonToken token = parser.getCurrentToken();

            if (name.equals(ContentTypeDefinitions.LABEL_SUBNODES)) {
                if (token.equals(JsonToken.START_OBJECT)) {
                    parser.nextToken();
                    final String childPath = path + "/" + parser.getCurrentName();
                    parser.nextToken(); // should equal JsonToken.START_OBJECT
                    final Resource childResource = extractResource(parser, provider, resolver, childPath); // should
// we do anything with this?
                }
            }
        }

        return provider.create(resolver, path, properties);
    }

    public static Map<String, Object> extractSubmap(final JsonParser jsonParser) throws IOException {
        jsonParser.nextToken(); // skip the START_OBJECT token
        final Map<String, Object> subMap = new HashMap<String, Object>();
        while (!jsonParser.getCurrentToken().equals(JsonToken.END_OBJECT)) {
            final String label = jsonParser.getCurrentName(); // get the current label
            final JsonToken token = jsonParser.nextToken(); // get the current value
            if (!token.isScalarValue()) {
                if (token.equals(JsonToken.START_OBJECT)) {
                    // if the next token starts a new object, recurse into it
                    subMap.put(label, extractSubmap(jsonParser));
                } else if (token.equals(JsonToken.START_ARRAY)) {
                    final List<String> subArray = new ArrayList<String>();
                    jsonParser.nextToken(); // skip the START_ARRAY token
                    while (!jsonParser.getCurrentToken().equals(JsonToken.END_ARRAY)) {
                        subArray.add(jsonParser.getValueAsString());
                        jsonParser.nextToken();
                    }
                    subMap.put(label, subArray);
                    jsonParser.nextToken(); // skip the END_ARRAY token
                }
            } else {
                // either a string, boolean, or long value
                if (token.isNumeric()) {
                    subMap.put(label, jsonParser.getValueAsLong());
                } else {
                    final String value = jsonParser.getValueAsString();
                    if (value.equals("true") || value.equals("false")) {
                        subMap.put(label, jsonParser.getValueAsBoolean());
                    } else {
                        subMap.put(label, value);
                    }
                }
            }
            jsonParser.nextToken(); // next token will either be an "END_OBJECT" or a new label
        }
        jsonParser.nextToken(); // skip the END_OBJECT token
        return subMap;
    }

    public static void extractTally(final Resource post, final JsonParser jsonParser,
        final SocialResourceProvider srp, final TallyOperationsService tallyOperationsService) throws IOException,
        OperationException {
        jsonParser.nextToken(); // should be start object, but would be end array if no objects were present
        while (!jsonParser.getCurrentToken().equals(JsonToken.END_ARRAY)) {
            Long timestamp = null;
            String userIdentifier = null;
            String response = null;
            String tallyType = null;
            jsonParser.nextToken();
            while (!jsonParser.getCurrentToken().equals(JsonToken.END_OBJECT)) {
                final String label = jsonParser.getCurrentName();
                jsonParser.nextToken();
                if (label.equals("timestamp")) {
                    timestamp = jsonParser.getValueAsLong();
                } else if (label.equals("response")) {
                    response = URLDecoder.decode(jsonParser.getValueAsString(), "UTF-8");
                } else if (label.equals("userIdentifier")) {
                    userIdentifier = URLDecoder.decode(jsonParser.getValueAsString(), "UTF-8");
                } else if (label.equals("tallyType")) {
                    tallyType = jsonParser.getValueAsString();
                }
                jsonParser.nextToken();
            }
            if (timestamp != null && userIdentifier != null && response != null && tallyType != null) {
                createTally(srp, post, tallyType, userIdentifier, timestamp, response, tallyOperationsService);
            }
            jsonParser.nextToken();
        }
    }

    private static void createTally(final SocialResourceProvider srp, final Resource post, final String tallyType,
        final String userIdentifier, final Long timestamp, final String response,
        final TallyOperationsService tallyOperationsService) throws PersistenceException, OperationException {

        final ResourceResolver resolver = post.getResourceResolver();
        final Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("jcr:primaryType", "social:asiResource");
        final Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(timestamp);
        properties.put("jcr:created", calendar.getTime());
        Resource tallyResource;
        Tally tally;
        if (tallyType.equals(TallyOperationsService.VOTING)) {
            tallyResource = post.getChild("voting");
            if (tallyResource == null) {
                properties.put("sling:resourceType", VotingSocialComponent.VOTING_RESOURCE_TYPE);
                tallyResource = srp.create(resolver, post.getPath() + "/voting", properties);
                srp.commit(resolver);
                properties.remove("sling:resourceType");
            }
            tally = tallyResource.adaptTo(Voting.class);
            tally.setTallyResourceType(VotingSocialComponent.VOTING_RESOURCE_TYPE);
        } else if (tallyType.equals(TallyOperationsService.POLL)) {
            tallyResource = post.getChild("poll");
            if (tallyResource == null) {
                tallyResource = srp.create(resolver, post.getPath() + "/poll", properties);
                srp.commit(resolver);
            }
            tally = tallyResource.adaptTo(Poll.class);
        } else if (tallyType.equals(TallyOperationsService.RATING)) {
            tallyResource = post.getChild("rating");
            if (tallyResource == null) {
                properties.put("sling:resourceType", RatingSocialComponent.RATING_RESOURCE_TYPE);
                tallyResource = srp.create(resolver, post.getPath() + "/rating", properties);
                srp.commit(resolver);
                properties.remove("sling:resourceType");
            }
            tally = tallyResource.adaptTo(Voting.class);
            tally.setTallyResourceType(RatingSocialComponent.RATING_RESOURCE_TYPE);
        } else {
            throw new RuntimeException("unrecognized tally type");
        }
        // Needed params:
        try {
            properties.put("timestamp", Long.toString(calendar.getTimeInMillis()));
            tallyOperationsService.setTallyResponse(tally, userIdentifier, resolver.adaptTo(Session.class), response,
                tallyType, properties);
        } catch (final OperationException e) {
            throw new RuntimeException("Unable to set the tally response value: " + e.getMessage(), e);
        } catch (final IllegalArgumentException e) {
            // We can ignore this. It means that the value set for the response in the migrated data is no longer
// valid.
            // This happens for "#neutral#" which used to be a valid response, but was taken out in later versions.
        }
    }

    public void importQnaContent(final JsonParser jsonParser, final Resource resource, final ResourceResolver resolver)
        throws ServletException, IOException {
        while (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
            extractTopic(jsonParser, resource, resolver, qnaForumOperations);
            jsonParser.nextToken(); // get the next token - presumably a start token
        }
        jsonParser.nextToken(); // skip end token
    }

    public void importForumContent(final JsonParser jsonParser, final Resource resource,
        final ResourceResolver resolver) throws ServletException, IOException {
        while (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
            jsonParser.nextToken(); // advance to first key in the object
            extractTopic(jsonParser, resource, resolver, forumOperations);
            jsonParser.nextToken(); // get the next token - presumably a start token
        }
        jsonParser.nextToken(); // skip end token
    }

    public void importCommentsContent(final JsonParser jsonParser, final Resource resource,
        final ResourceResolver resolver) throws JsonParseException, IOException {
        // not yet implemented
        jsonParser.skipChildren();
    }

    public void importJournalContent(final JsonParser jsonParser, final Resource resource,
        final ResourceResolver resolver) throws JsonParseException, IOException {
        // not yet implemented
        jsonParser.skipChildren();
    }

    public void importCalendarContent(final JsonParser jsonParser, final Resource resource,
        final ResourceResolver resolver) throws JsonParseException, IOException {
        // not yet implemented
        jsonParser.skipChildren();
    }

    public void importTallyContent(final JsonParser jsonParser, final Resource resource,
        final ResourceResolver resolver) throws JsonParseException, IOException {
        // not yet implemented
        jsonParser.skipChildren();
    }

    protected void extractTopic(final JsonParser jsonParser, final Resource resource,
        final ResourceResolver resolver, final CommentOperations operations) throws IOException, ServletException {
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

                    // either a string, boolean, or long value
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
                } else if (label.equals(ContentTypeDefinitions.LABEL_REPLIES)
                        || label.equals(ContentTypeDefinitions.LABEL_TALLY)
                        || label.equals(ContentTypeDefinitions.LABEL_SUBNODES)) {
                    // replies and sub-nodesALWAYS come after all other properties and attachments have been listed,
                    // so we can create the post now if we haven't already, and then dive in
                    if (post == null) {
                        try {
                            post =
                                createPost(resource, author, properties, attachments,
                                    resolver.adaptTo(Session.class), operations);
                            resProvider = SocialResourceUtils.getSocialResource(post).getResourceProvider();
// resProvider.commit(resolver);
                        } catch (Exception e) {
                            throw new ServletException(e.getMessage(), e);
                        }
                    }
                    if (label.equals(ContentTypeDefinitions.LABEL_REPLIES)) {
                        if (token.equals(JsonToken.START_OBJECT)) {
                            jsonParser.nextToken();
                            while (!token.equals(JsonToken.END_OBJECT)) {
                                extractTopic(jsonParser, post, resolver, operations);
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
                        try {
                            UGCImportHelper.extractTally(post, jsonParser, resProvider, tallyOperationsService);
                        } catch (OperationException e) {
                            throw new IOException("Could not create tally", e);
                        }
                    }

                } else if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
                    properties.put(label, UGCImportHelper.extractSubmap(jsonParser));
                } else if (jsonParser.getCurrentToken().equals(JsonToken.START_ARRAY)) {
                    jsonParser.nextToken(); // skip the START_ARRAY token
                    if (label.equals(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS)) {
                        while (!jsonParser.getCurrentToken().equals(JsonToken.END_ARRAY)) {
                            final String timestampLabel = jsonParser.getValueAsString();
                            if (properties.containsKey(timestampLabel)
                                    && properties.get(timestampLabel) instanceof Long) {
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
                        for (int i = 0; i < subArray.size(); i++) {
                            strings[i] = subArray.get(i);
                        }
                        properties.put(label, strings);
                    }
                }
                jsonParser.nextToken();
            }
            if (post == null) {
                try {
                    post =
                        createPost(resource, author, properties, attachments, resolver.adaptTo(Session.class),
                            operations);
                    resProvider = SocialResourceUtils.getSocialResource(post).getResourceProvider();
// resProvider.commit(resolver);
                } catch (Exception e) {
                    throw new ServletException(e.getMessage(), e);
                }
            }
        } else {
            throw new IOException("Improperly formed JSON - expected an OBJECT_START token, but got "
                    + jsonParser.getCurrentToken().toString());
        }
    }

    protected static Resource createPost(final Resource resource, final String author,
        final Map<String, Object> properties, final List<DataSource> attachments, final Session session,
        final CommentOperations operations) throws OperationException {
        if (populateMessage(properties)) {
            return operations.create(resource, author, properties, attachments, session);
        } else {
            return null;
        }
    }

    protected static boolean populateMessage(Map<String, Object> properties) {

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

    protected static List<DataSource> getAttachments(final JsonParser jsonParser) throws IOException {
        // an attachment has only 3 fields - jcr:data, filename, jcr:mimeType
        List<DataSource> attachments = new ArrayList<DataSource>();
        JsonToken token = jsonParser.nextToken(); // skip START_ARRAY token
        String filename;
        String mimeType;
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
                    filename = URLDecoder.decode(jsonParser.getValueAsString(), "UTF-8");
                } else if (label.equals("jcr:mimeType")) {
                    mimeType = jsonParser.getValueAsString();
                } else if (label.equals("jcr:data")) {
                    databytes = Base64.decodeBase64(jsonParser.getValueAsString());
                    inputStream = new ByteArrayInputStream(databytes);
                }
                token = jsonParser.nextToken();
            }
            if (filename != null && mimeType != null && inputStream != null) {
                attachments.add(new UGCImportHelper.AttachmentStruct(filename, inputStream, mimeType,
                    databytes.length));
            } else {
                // TODO - log an error
            }
            token = jsonParser.nextToken();
        }
        return attachments;
    }

    /**
     * This class must implement DataSource in order to be used to create an attachment, and also FileDataSource in
     * order to be used by the filterAttachments method in AbstractCommentOperationService
     */
    public static class AttachmentStruct implements DataSource {
        private String filename;
        private String mimeType;
        private InputStream data;
        private long size;

        public AttachmentStruct(final String filename, final InputStream data, final String mimeType, final long size) {
            this.filename = filename;
            this.data = data;
            this.mimeType = mimeType;
            this.size = size;
        }

        public String getName() {
            return filename;
        }

        public String getContentType() {
            return mimeType;
        }

        public OutputStream getOutputStream() throws IOException {
            throw new IOException("OutputStream is not supported");
        }

        public InputStream getInputStream() {
            return data;
        }

        /**
         * Returns the MIME type of the content.
         * @return content MIME type.
         */
        public String getType() {
            return mimeType;
        }

        /**
         * Returns the MIME type extension from file name.
         * @return content MIME type extension from file Name.
         */
        public String getTypeFromFileName() {
            return filename.substring(filename.lastIndexOf('.'));
        }

        /**
         * Returns the size of the file in bytes.
         * @return size of file in bytes.
         */
        public long getSize() {
            return size;
        }
    }
}
