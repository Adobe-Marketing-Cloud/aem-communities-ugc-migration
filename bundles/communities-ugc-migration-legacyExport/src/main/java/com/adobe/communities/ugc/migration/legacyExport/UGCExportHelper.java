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
package com.adobe.communities.ugc.migration.legacyExport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.adobe.cq.social.calendar.Event;
import com.adobe.cq.social.journal.JournalEntry;
import com.adobe.cq.social.storage.buckets.NestedBucketStorageSystem;
import com.adobe.cq.social.tally.PollResponse;
import com.adobe.cq.social.tally.ResponseValue;
import com.adobe.cq.social.tally.TallyConstants;
import org.apache.commons.codec.binary.Base64;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;

import com.adobe.cq.social.commons.Comment;
import com.adobe.cq.social.forum.api.Post;
import com.adobe.cq.social.tally.Response;
import com.adobe.cq.social.tally.Vote;
import com.adobe.cq.social.tally.Voting;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import static com.adobe.cq.social.tally.TallyConstants.RESPONSE_PROPERTY;
import static com.adobe.cq.social.tally.TallyConstants.TIMESTAMP_PROPERTY;

//import com.adobe.communities.ugc.migration.legacyExport.ContentTypeDefinitions;

public class UGCExportHelper {

    private final static int DATA_ENCODING_CHUNK_SIZE = 1440;

    public static void extractSubNode(JSONWriter object, final Resource node) throws JSONException {
        final ValueMap childVm = node.adaptTo(ValueMap.class);
        final JSONArray timestampFields = new JSONArray();
        for (Map.Entry<String, Object> prop : childVm.entrySet()) {
            final Object value = prop.getValue();
            if (value instanceof String[]) {
                final JSONArray list = new JSONArray();
                for (String v : (String[]) value) {
                    try {
                        list.put(URLEncoder.encode(v, "UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        throw new JSONException("String value cannot be encoded as UTF-8 for JSON transmission", e);
                    }
                }
                object.key(prop.getKey());
                object.value(list);
            } else if (value instanceof GregorianCalendar) {
                timestampFields.put(prop.getKey());
                object.key(prop.getKey());
                object.value(((Calendar) value).getTimeInMillis());
            } else if (value instanceof InputStream) {
                object.key(ContentTypeDefinitions.LABEL_ENCODED_DATA_FIELDNAME);
                object.value(prop.getKey());
                object.key(ContentTypeDefinitions.LABEL_ENCODED_DATA);
                object.value(""); //if we error out on the first read attempt, we need a placeholder value still
                try {
                    final InputStream data = (InputStream) value;
                    byte[] byteData = new byte[DATA_ENCODING_CHUNK_SIZE];
                    int read = 0;
                    while (read != -1) {
                        read = data.read(byteData);
                        if (read > 0 && read < DATA_ENCODING_CHUNK_SIZE) {
                            // make a right-size container for the byte data actually read
                            byte[] byteArray = new byte[read];
                            System.arraycopy(byteData, 0, byteArray, 0, read);
                            byte[] encodedBytes = Base64.encodeBase64(byteArray);
                            object.value(new String(encodedBytes));
                        } else if (read == DATA_ENCODING_CHUNK_SIZE) {
                            byte[] encodedBytes = Base64.encodeBase64(byteData);
                            object.value(new String(encodedBytes));
                        }
                    }
                } catch (IOException e) {
                    object.key(ContentTypeDefinitions.LABEL_ERROR);
                    object.value("IOException while getting attachment: " + e.getMessage());
                }
            } else {
                object.key(prop.getKey());
                try {
                    object.value(URLEncoder.encode(prop.getValue().toString(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new JSONException("String value cannot be encoded as UTF-8 for JSON transmission", e);
                }
            }
        }
        if (timestampFields.length() > 0) {
            object.key(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS);
            object.value(timestampFields);
        }
        final Iterable<Resource> childNodes = node.getChildren();
        if (childNodes != null) {
            object.key(ContentTypeDefinitions.LABEL_SUBNODES);
            object.object();
            for (final Resource subNode : childNodes) {
                object.key(subNode.getName());
                JSONWriter subObject = object.object();
                extractSubNode(subObject, subNode);
                object.endObject();
            }
            object.endObject();
        }
    }
    public static void extractAttachment(final Writer ioWriter, final JSONWriter writer, final Resource node)
            throws JSONException {
        Resource contentNode = node.getChild("jcr:content");
        if (contentNode == null) {
            writer.key(ContentTypeDefinitions.LABEL_ERROR);
            writer.value("provided resource was not an attachment - no content node");
            return;
        }
        ValueMap content = contentNode.adaptTo(ValueMap.class);
        if (!content.containsKey("jcr:mimeType") || !content.containsKey("jcr:data")) {
            writer.key(ContentTypeDefinitions.LABEL_ERROR);
            writer.value("provided resource was not an attachment - content node contained no attachment data");
            return;
        }
        writer.key("filename");
        writer.value(node.getName());
        writer.key("jcr:mimeType");
        writer.value(content.get("jcr:mimeType"));

        try {
            ioWriter.write(",\"jcr:data\":\"");
            final InputStream data = (InputStream) content.get("jcr:data");
            byte[] byteData = new byte[DATA_ENCODING_CHUNK_SIZE];
            int read = 0;
            while (read != -1) {
                read = data.read(byteData);
                if (read > 0 && read < DATA_ENCODING_CHUNK_SIZE) {
                    // make a right-size container for the byte data actually read
                    byte[] byteArray = new byte[read];
                    System.arraycopy(byteData, 0, byteArray, 0, read);
                    byte[] encodedBytes = Base64.encodeBase64(byteArray);
                    ioWriter.write(new String(encodedBytes));
                } else if (read == DATA_ENCODING_CHUNK_SIZE) {
                    byte[] encodedBytes = Base64.encodeBase64(byteData);
                    ioWriter.write(new String(encodedBytes));
                }
            }
            ioWriter.write("\"");
        } catch (IOException e) {
            writer.key(ContentTypeDefinitions.LABEL_ERROR);
            writer.value("IOException while getting attachment: " + e.getMessage());
        }
    }

    protected static Map<String, Map<Long, ResponseValue>> getTallyResponses(Resource tallyResource) {
        Map<String, Map<Long, ResponseValue>> returnValue = new HashMap<String, Map<Long, ResponseValue>>();
        final ResourceResolver resolver = tallyResource.getResourceResolver();
        final String tallyPath = tallyResource.getPath();
        if (!tallyPath.startsWith("/content/usergenerated")) {
            tallyResource = resolver.resolve("/content/usergenerated" + tallyPath);
        }
        final Resource responsesNode = tallyResource.getChild(TallyConstants.RESPONSES_PATH);
        if (responsesNode == null) {
            return null;
        }
        NestedBucketStorageSystem bucketSystem = getBucketSystem(responsesNode);

        final Iterator<Resource> buckets = bucketSystem.listBuckets();
        while (buckets.hasNext()) {
            final Resource bucketResource = buckets.next();
            final Node bucketNode = bucketResource.adaptTo(Node.class);
            try {
                final NodeIterator userNodesInBucket = bucketNode.getNodes();
                while (userNodesInBucket.hasNext()) {
                    final Node userNode = userNodesInBucket.nextNode();
                    final NestedBucketStorageSystem userBucketSystem =
                            getBucketSystem(resolver.getResource(userNode.getPath()));
                    final Iterator<Resource> userBuckets = userBucketSystem.listBuckets();
                    final Map<Long, ResponseValue> userReturnValue = new HashMap<Long, ResponseValue>();
                    while (userBuckets.hasNext()) {
                        final NodeIterator userResponses = userBuckets.next().adaptTo(Node.class).getNodes();
                        while (userResponses.hasNext()) {
                            final Node responseNode = userResponses.nextNode();
                            final Long responseTimestamp = responseNode.getProperty(TIMESTAMP_PROPERTY).getLong();
                            userReturnValue.put(responseTimestamp,
                                    new PollResponse(responseNode.getProperty(RESPONSE_PROPERTY).getString()));
                        }
                    }
                    returnValue.put(userNode.getName(), userReturnValue);
                }
            } catch (final RepositoryException e) {
//                log.error("Error trying to read user responses from bucket in ", bucketResource.getPath(), e);
            }
        }
        return returnValue;
    }
    /**
     * Need to do this because the responses that tally returns don't actually link users with their response values. In
     * order to do an export under the legacy code, we have to explicitly handle the bucketing system.
     * @param resource
     * @return
     */
    protected static NestedBucketStorageSystem getBucketSystem(final Resource resource) {
        NestedBucketStorageSystem bucketSystem;
        try {
            bucketSystem = resource.adaptTo(NestedBucketStorageSystem.class);
        } catch (final NullPointerException e) {
            return null;
        }
        if (bucketSystem != null) {
            bucketSystem.setBucketPostfix(NestedBucketStorageSystem.DEFAULT_BUCKET_POSTFIX);
        }
        return bucketSystem;
    }

    public static void extractTally(final JSONWriter responseArray, final Resource rootNode, final String tallyType)
            throws JSONException, UnsupportedEncodingException {

        final Map<String, Map<Long, ResponseValue>> responses = getTallyResponses(rootNode);
        for (final String userIdentifier : responses.keySet()) {
            JSONWriter voteObject = responseArray.object();
            for (final Map.Entry<Long, ResponseValue> entry : responses.get(userIdentifier).entrySet()) {
                final String response = entry.getValue().getResponseValue();
                voteObject.key("timestamp");
                voteObject.value(entry.getKey());
                voteObject.key("response");
                voteObject.value(URLEncoder.encode(response, "UTF-8"));
                voteObject.key("userIdentifier");
                voteObject.value(URLEncoder.encode(userIdentifier, "UTF-8"));
                // for the purposes of this export, tallyType is fixed
                voteObject.key("tallyType");
                voteObject.value(tallyType);
                voteObject.endObject();
            }
        }
    }


    public static void extractComment(final JSONWriter writer, final Comment post, final ResourceResolver resolver,
                                    final Writer responseWriter) throws JSONException, IOException {

        final ValueMap vm = post.getProperties();
        final JSONArray timestampFields = new JSONArray();
        for (final Map.Entry<String, Object> prop : vm.entrySet()) {
            final Object value = prop.getValue();
            if (value instanceof String[]) {
                final JSONArray list = new JSONArray();
                for (String v : (String[]) value) {
                    list.put(v);
                }
                writer.key(prop.getKey());
                writer.value(list);
            } else if (value instanceof GregorianCalendar) {
                timestampFields.put(prop.getKey());
                writer.key(prop.getKey());
                writer.value(((Calendar) value).getTimeInMillis());
            } else if (prop.getKey().equals("sling:resourceType")) {
                writer.key(prop.getKey());
                writer.value(Comment.RESOURCE_TYPE);
            } else {
                writer.key(prop.getKey());
                try {
                    writer.value(URLEncoder.encode(prop.getValue().toString(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new JSONException("Unsupported encoding - UTF-8", e);
                }
            }
        }
        if (timestampFields.length() > 0) {
            writer.key(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS);
            writer.value(timestampFields);
        }
        final Resource thisResource = resolver.getResource(post.getPath());
        final Resource attachments = thisResource.getChild("attachments");
        if (attachments != null) {
            writer.key(ContentTypeDefinitions.LABEL_ATTACHMENTS);
            final JSONWriter attachmentsWriter = writer.array();
            for (final Resource attachment : attachments.getChildren()) {
                UGCExportHelper.extractAttachment(responseWriter, attachmentsWriter.object(), attachment);
                attachmentsWriter.endObject();
            }
            writer.endArray();
        }
        final Resource voteResource = thisResource.getChild("votes");
        if (voteResource != null) {
            writer.key(ContentTypeDefinitions.LABEL_TALLY);
            final JSONWriter voteObjects = writer.array();
            UGCExportHelper.extractTally(voteObjects, voteResource, "Voting");
            writer.endArray();
        }
        final Iterable<Resource> childNodes = thisResource.getChildren();
        if (childNodes != null) {
            writer.key(ContentTypeDefinitions.LABEL_SUBNODES);
            final JSONWriter object = writer.object();
            for (final Resource subNode : childNodes) {
                final String nodeName = subNode.getName();
                if (nodeName.matches("^[0-9]+" + Post.POST_POSTFIX + "$")) {
                    continue; //this is a folder of replies, which will be picked up lower down
                }
                if (nodeName.equals("attachments") || nodeName.equals("votes")) {
                    continue; //already handled attachments and votes up above
                }
                object.key(nodeName);
                UGCExportHelper.extractSubNode(object.object(), subNode);
                object.endObject();
            }
            writer.endObject();
        }
        final Iterator<Comment> posts = post.getComments();
        if (posts.hasNext()) {
            writer.key(ContentTypeDefinitions.LABEL_REPLIES);
            final JSONWriter replyWriter = writer.object();
            while (posts.hasNext()) {
                Comment childPost = posts.next();
                replyWriter.key(childPost.getId());
                extractComment(replyWriter.object(), childPost, resolver, responseWriter);
                replyWriter.endObject();
            }
            writer.endObject();
        }
    }

    public static void extractTopic(final JSONWriter writer, final Post post, final ResourceResolver resolver,
        final String resourceType, final String childResourceType, final Writer responseWriter)
            throws JSONException, IOException {

        final ValueMap vm = post.getProperties();
        final JSONArray timestampFields = new JSONArray();
        for (final Map.Entry<String, Object> prop : vm.entrySet()) {
            final Object value = prop.getValue();
            if (value instanceof String[]) {
                final JSONArray list = new JSONArray();
                for (String v : (String[]) value) {
                    list.put(v);
                }
                writer.key(prop.getKey());
                writer.value(list);
            } else if (value instanceof GregorianCalendar) {
                timestampFields.put(prop.getKey());
                writer.key(prop.getKey());
                writer.value(((Calendar) value).getTimeInMillis());
            } else if (prop.getKey().equals("sling:resourceType")) {
                writer.key(prop.getKey());
                writer.value(resourceType);
            } else {
                writer.key(prop.getKey());
                try {
                    writer.value(URLEncoder.encode(prop.getValue().toString(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new JSONException("Unsupported encoding - UTF-8", e);
                }
            }
        }
        if (timestampFields.length() > 0) {
            writer.key(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS);
            writer.value(timestampFields);
        }
        final Resource thisResource = resolver.getResource(post.getPath());
        final Resource attachments = thisResource.getChild("attachments");
        if (attachments != null) {
            writer.key(ContentTypeDefinitions.LABEL_ATTACHMENTS);
            final JSONWriter attachmentsWriter = writer.array();
            for (final Resource attachment : attachments.getChildren()) {
                UGCExportHelper.extractAttachment(responseWriter, attachmentsWriter.object(), attachment);
                attachmentsWriter.endObject();
            }
            writer.endArray();
        }
        final Resource voteResource = thisResource.getChild("votes");
        if (voteResource != null) {
            writer.key(ContentTypeDefinitions.LABEL_TALLY);
            final JSONWriter voteObjects = writer.array();
            UGCExportHelper.extractTally(voteObjects, voteResource, "Voting");
            writer.endArray();
        }
        final Iterable<Resource> childNodes = thisResource.getChildren();
        if (childNodes != null) {
            writer.key(ContentTypeDefinitions.LABEL_SUBNODES);
            final JSONWriter object = writer.object();
            for (final Resource subNode : childNodes) {
                final String nodeName = subNode.getName();
                if (nodeName.matches("^[0-9]+" + Post.POST_POSTFIX + "$")) {
                    continue; //this is a folder of replies, which will be picked up lower down
                }
                if (nodeName.equals("attachments") || nodeName.equals("votes")) {
                    continue; //already handled attachments and votes up above
                }
                object.key(nodeName);
                UGCExportHelper.extractSubNode(object.object(), subNode);
                object.endObject();
            }
            writer.endObject();
        }
        final Iterator<Post> posts = post.getPosts();
        if (posts.hasNext()) {
            writer.key(ContentTypeDefinitions.LABEL_REPLIES);
            final JSONWriter replyWriter = writer.object();
            while (posts.hasNext()) {
                Post childPost = posts.next();
                replyWriter.key(childPost.getId());
                extractTopic(replyWriter.object(), childPost, resolver, childResourceType, childResourceType, responseWriter);
                replyWriter.endObject();
            }
            writer.endObject();
        }
    }

    public static void extractJournalEntry(final JSONWriter entryObject, final JournalEntry entry, final Writer rawWriter)
            throws JSONException, UnsupportedEncodingException {
        // re-importing a journal entry requires the following function
        // JournalEntry addEntry(String title, String text, Date date, String author, List<DataSource> attachmentDataSources);
        entryObject.key("title");
        entryObject.value(URLEncoder.encode(entry.getTitle(), "UTF-8"));
        entryObject.key("text");
        entryObject.value(URLEncoder.encode(entry.getText(), "UTF-8"));
        entryObject.key("date");
        entryObject.value(entry.getDate().getTime());
        entryObject.key("author");
        entryObject.value(URLEncoder.encode(entry.getAuthor(), "UTF-8"));
        if (entry.hasAttachments()) {
            entryObject.key(ContentTypeDefinitions.LABEL_ATTACHMENTS);
            JSONWriter attachmentsArray = entryObject.array();
            List<Resource> attachmentList = entry.getAttachments();
            for (final Resource attachment : attachmentList) {
                extractAttachment(rawWriter, attachmentsArray.object(), attachment);
                attachmentsArray.endObject();
            }
            entryObject.endArray();
        }
    }

    public static void extractCalendarEvent(final JSONWriter eventObject, final Event event) {
        //not yet implemented
    }
}
