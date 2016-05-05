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
package com.adobe.communities.ugc.migration.export;

import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import com.adobe.cq.social.commons.Comment;
import com.adobe.cq.social.srp.SocialResourceProvider;
import com.adobe.cq.social.ugcbase.SocialUtils;
import com.adobe.cq.social.ugcbase.core.SocialResourceUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class UGCExportHelper {

    private final static int DATA_ENCODING_CHUNK_SIZE = 1440;

    public static SocialResourceProvider srp;

    public static void extractSubNode(JSONWriter object, final Resource node) throws JSONException {
        final ValueMap childVm = node.getValueMap();
        final JSONArray timestampFields = new JSONArray();
        for (Map.Entry<String, Object> prop : childVm.entrySet()) {
            final Object value = prop.getValue();
            if (value instanceof String[]) {
                final JSONArray list = new JSONArray();
                for (String v : (String[]) value) {
                    list.put(v);
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
                object.value(prop.getValue());
            }
        }
        if (timestampFields.length() > 0) {
            object.key(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS);
            object.value(timestampFields);
        }
        if (node.hasChildren()) {
            object.key(ContentTypeDefinitions.LABEL_SUBNODES);
            object.object();
            for (final Resource subNode : node.getChildren()) {
                object.key(subNode.getName());
                JSONWriter subObject = object.object();
                extractSubNode(subObject, subNode);
                object.endObject();
            }
            object.endObject();
        }
    }

    public static void extractEvent(JSONWriter writer, final Resource event, final ResourceResolver resolver,
                                    final Writer responseWriter, final SocialUtils socialUtils) throws JSONException, IOException {

        final ValueMap vm = event.getValueMap();
        final JSONArray timestampFields = new JSONArray();
        List<String> attachments = null;
        Integer flagAllowCount = -1;
        String coverimage = null;
        for (final Map.Entry<String, Object> prop : vm.entrySet()) {
            final Object value = prop.getValue();
            if (value instanceof String[]) {
                if (prop.getKey().equals("social:attachments")) {
                    attachments = Arrays.asList((String[]) value);
                } else {
                    final JSONArray list = new JSONArray();
                    for (String v : (String[]) value) {
                        list.put(v);
                    }
                    writer.key(prop.getKey());
                    writer.value(list);
                }
            } else if (prop.getKey().equals("coverimage")) {
                coverimage = value.toString();
            } else if (value instanceof GregorianCalendar) {
                timestampFields.put(prop.getKey());
                writer.key(prop.getKey());
                writer.value(((Calendar) value).getTimeInMillis());
            } else if (prop.getKey().equals("sling:resourceType")) {
                writer.key(prop.getKey());
                writer.value(Comment.RESOURCE_TYPE);
            } else if (prop.getKey().startsWith("voting_")) {
                continue; //we'll reconstruct this value automatically when we import votes
            } else if (prop.getKey().equals(Comment.PROP_FLAG_ALLOW_COUNT)) {
                if (value instanceof Long) {
                    flagAllowCount = ((Long)value).intValue();
                } else if (value instanceof Integer){
                    flagAllowCount = (Integer) value;
                } else {
                    // may throw a NumberFormatException
                    flagAllowCount = Integer.getInteger(value.toString());
                }
            } else {
                writer.key(prop.getKey());
                try {
                    writer.value(URLEncoder.encode(prop.getValue().toString(), "UTF-8"));
                } catch (final UnsupportedEncodingException e) {
                    throw new JSONException("Unsupported encoding (UTF-8) for resource at " + event.getPath(), e);
                }
            }
        }
        if (timestampFields.length() > 0) {
            writer.key(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS);
            writer.value(timestampFields);
        }
        if (srp == null) {
            srp = SocialResourceUtils.getSocialResource(event).getResourceProvider();
            srp.setConfig(socialUtils.getStorageConfig(event));
        }
        if (attachments != null) {
            writer.key(ContentTypeDefinitions.LABEL_ATTACHMENTS);
            final JSONWriter attachmentsWriter = writer.array();
            for (final String attachment : attachments) {
                UGCExportHelper.extractAttachment(responseWriter, attachmentsWriter.object(), resolver.getResource(attachment));
                attachmentsWriter.endObject();
            }
            writer.endArray();
        }
        if (coverimage != null && !"".equals(coverimage)) {
            writer.key("coverimage");
            UGCExportHelper.extractAttachment(responseWriter, writer.object(), resolver.getResource(coverimage));
            writer.endObject();
        }
        Iterable<Resource> children = event.getChildren();
        boolean hasReplies = false;
        final List<Resource> comments = new ArrayList<Resource>();
        for (final Resource child : children) {
            // check for votes, flags, or translations
            if (child.isResourceType("social/tally/components/hbs/voting")) {
                if (!child.hasChildren()) continue;
                writer.key(ContentTypeDefinitions.LABEL_TALLY);
                final JSONWriter voteObjects = writer.array();
                UGCExportHelper.extractTally(voteObjects, child, "Voting");
                writer.endArray();
            } else if (child.getName().equals("translation")) {
                if (!child.hasChildren()) continue;
                extractTranslation(writer, child);
            } else if (child.isResourceType("social/tally/components/voting")) {
                if (!child.hasChildren() || !child.getPath().endsWith(flagAllowCount.toString())) continue;
                // this resource type is used for flagging
                writer.key(ContentTypeDefinitions.LABEL_FLAGS);
                final JSONWriter flagObjects = writer.array();
                UGCExportHelper.extractFlags(flagObjects, child);
                writer.endArray();
            } else if (child.isResourceType("social/commons/components/comments/comment")) {
                hasReplies = true;
                comments.add(child);
            }
        }

        if (hasReplies) {
            JSONWriter replyWriter = null;
            for (final Resource comment : comments) {
                if (null == replyWriter) {
                    writer.key(ContentTypeDefinitions.LABEL_REPLIES);
                    replyWriter = writer.object();
                }
                replyWriter.key(comment.getPath());
                extractComment(replyWriter.object(), comment.adaptTo(Comment.class), resolver, responseWriter, socialUtils);
                replyWriter.endObject();
            }
            if (null != replyWriter) {
                writer.endObject();
            }
        }
    }
    public static void extractAttachment(final Writer ioWriter, final JSONWriter writer, final Resource node)
            throws JSONException {
        InputStream data = node.adaptTo(InputStream.class);
        if (data == null) {
            try {
                data = srp.getAttachmentInputStream(node.getResourceResolver(), node.getPath());
            } catch (final IOException e) {
                writer.key(ContentTypeDefinitions.LABEL_ERROR);
                writer.value("provided resource was not an attachment - no content node beneath " + node.getPath());
                return;
            } catch (final Exception e) {
                writer.key(ContentTypeDefinitions.LABEL_ERROR);
                writer.value(e.getMessage());
                return;
            }
        }
        ValueMap content = node.getValueMap();
        if (!content.containsKey("mimetype")) {
            writer.key(ContentTypeDefinitions.LABEL_ERROR);
            writer.value("provided resource was not an attachment - content node contained no mime type data");
            return;
        }
        writer.key("filename");
        writer.value(node.getName());
        writer.key("jcr:mimeType");
        writer.value(content.get("mimetype"));

        try {
            ioWriter.write(",\"jcr:data\":\"");
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
    public static void extractComment(final JSONWriter writer, final Comment post, final ResourceResolver resolver,
                      final Writer responseWriter, final SocialUtils socialUtils) throws JSONException, IOException {

        final ValueMap vm = post.getProperties();
        final JSONArray timestampFields = new JSONArray();
        List<String> attachments = null;
        Integer flagAllowCount = -1;
        for (final Map.Entry<String, Object> prop : vm.entrySet()) {
            final Object value = prop.getValue();
            if (value instanceof String[]) {
                if (prop.getKey().equals("social:attachments")) {
                    attachments = Arrays.asList((String[])value);
                } else {
                    final JSONArray list = new JSONArray();
                    for (String v : (String[]) value) {
                        list.put(v);
                    }
                    writer.key(prop.getKey());
                    writer.value(list);
                }
            } else if (value instanceof GregorianCalendar) {
                timestampFields.put(prop.getKey());
                writer.key(prop.getKey());
                writer.value(((Calendar) value).getTimeInMillis());
            } else if (prop.getKey().startsWith("voting_")) {
                continue; //we'll reconstruct this value automatically when we import votes
            } else if (prop.getKey().equals(Comment.PROP_FLAG_ALLOW_COUNT)) {
                if (value instanceof Long) {
                    flagAllowCount = ((Long)value).intValue();
                } else if (value instanceof Integer){
                    flagAllowCount = (Integer) value;
                } else {
                    // may throw a NumberFormatException
                    flagAllowCount = Integer.getInteger(value.toString());
                }
            } else {
                writer.key(prop.getKey());
                try {
                    writer.value(URLEncoder.encode(prop.getValue().toString(), "UTF-8"));
                } catch (final UnsupportedEncodingException e) {
                    throw new JSONException("Unsupported encoding (UTF-8) for resource at " + post.getPath(), e);
                }
            }
        }
        if (timestampFields.length() > 0) {
            writer.key(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS);
            writer.value(timestampFields);
        }
        final Resource thisResource = resolver.getResource(post.getPath());
        if (srp == null) {
            srp = SocialResourceUtils.getSocialResource(thisResource).getResourceProvider();
            srp.setConfig(socialUtils.getStorageConfig(thisResource));
        }
        if (attachments != null) {
            writer.key(ContentTypeDefinitions.LABEL_ATTACHMENTS);
            final JSONWriter attachmentsWriter = writer.array();
            for (final String attachment : attachments) {
                UGCExportHelper.extractAttachment(responseWriter, attachmentsWriter.object(), resolver.getResource(attachment));
                attachmentsWriter.endObject();
            }
            writer.endArray();
        }
        final Iterable<Resource> children = thisResource.getChildren();
        for (final Resource child : children) {
             // check for votes, flags, or translations
            if (child.isResourceType("social/tally/components/hbs/voting")) {
                if (!child.hasChildren()) continue;
                writer.key(ContentTypeDefinitions.LABEL_TALLY);
                final JSONWriter voteObjects = writer.array();
                UGCExportHelper.extractTally(voteObjects, child, "Voting");
                writer.endArray();
            } else if (child.getName().equals("translation")) {
                if (!child.hasChildren()) continue;
                extractTranslation(writer, child);
            } else if (child.isResourceType("social/tally/components/voting")) {
                if (!child.hasChildren() || !child.getPath().endsWith(flagAllowCount.toString())) continue;
                // this resource type is used for flagging
                writer.key(ContentTypeDefinitions.LABEL_FLAGS);
                final JSONWriter flagObjects = writer.array();
                UGCExportHelper.extractFlags(flagObjects, child);
                writer.endArray();
            }
        }
        final Iterator<Comment> posts = post.getComments();
        if (posts.hasNext()) {
            JSONWriter replyWriter = null;
            while (posts.hasNext()) {
                Comment childPost = posts.next();
                if(!childPost.getResource().isResourceType("social/commons/components/comments/comment")) {
                    continue;
                } else if (null == replyWriter) {
                    writer.key(ContentTypeDefinitions.LABEL_REPLIES);
                    replyWriter = writer.object();
                }
                replyWriter.key(childPost.getId());
                extractComment(replyWriter.object(), childPost, resolver, responseWriter, socialUtils);
                replyWriter.endObject();
            }
            if (null != replyWriter) {
                writer.endObject();
            }
        }
    }


    public static void extractTally(final JSONWriter responseArray, final Resource rootNode, final String tallyType)
            throws JSONException, UnsupportedEncodingException {

        final Iterable<Resource> responses = rootNode.getChildren();
        String tallyResourceType;
        if (tallyType.equals("Voting")) {
            tallyResourceType = "social/tally/components/hbs/voting";
        } else {
            tallyResourceType = "social/tally/components/hbs/tally";
        }
        for (final Resource response : responses) {
            final ValueMap properties = response.adaptTo(ValueMap.class);
            if (!(properties.get("social:baseType")).equals(tallyResourceType)) {
                continue;
            }
            final String userIdentifier = properties.get("userIdentifier", "");
            final String responseValue = properties.get("response", "");
            final JSONWriter voteObject = responseArray.object();
            voteObject.key("timestamp");
            if (properties.get("added") instanceof Calendar) {
                voteObject.value(((Calendar) properties.get("added")).getTimeInMillis());
            } else {
                voteObject.value(properties.get("added", GregorianCalendar.getInstance().getTimeInMillis()));
            }
            voteObject.key("response");
            voteObject.value(URLEncoder.encode(responseValue, "UTF-8"));
            voteObject.key("userIdentifier");
            voteObject.value(URLEncoder.encode(userIdentifier, "UTF-8"));
            if (tallyType != null) {
                // for the purposes of this export, tallyType is fixed
                voteObject.key("tallyType");
                voteObject.value(tallyType);
            }
            voteObject.endObject();
        }
    }
    public static void extractTranslation(final JSONWriter writer, final Resource translationResource)
            throws JSONException, IOException {

        final Iterable<Resource> translations = translationResource.getChildren();
        final ValueMap props = translationResource.adaptTo(ValueMap.class);
        String languageLabel = (String) props.get("mtlanguage");
        if (null == languageLabel) {
            languageLabel = (String) props.get("mtlanguage");
            if (null == languageLabel) {
                return;
            }
        }
        writer.key(ContentTypeDefinitions.LABEL_TRANSLATION);
        writer.object();
        writer.key("mtlanguage");
        writer.value(languageLabel);
        writer.key("jcr:created");
        Object createdDate = props.get("added");
        if (null != createdDate && createdDate instanceof Calendar) {
            writer.value(((Calendar)createdDate).getTimeInMillis());
        } else {
            writer.value((new GregorianCalendar()).getTimeInMillis());
        }
        if (translations.iterator().hasNext()) {
            writer.key(ContentTypeDefinitions.LABEL_TRANSLATIONS);
            final JSONWriter translationObjects = writer.object();
            UGCExportHelper.extractTranslations(translationObjects, translations);
            writer.endObject();
        }
        writer.endObject();
    }
    public static void extractTranslations(final JSONWriter writer, final Iterable<Resource> translations)
            throws JSONException, IOException {
        for (final Resource translation : translations) {
            final JSONArray timestampFields = new JSONArray();
            final ValueMap vm = translation.adaptTo(ValueMap.class);
            if (!vm.containsKey("jcr:description")) {
                continue; //if there's no translation, we're done here
            }
            String languageLabel = translation.getName();
            writer.key(languageLabel);

            JSONWriter translationObject = writer.object();
            translationObject.key("jcr:description");
            translationObject.value(URLEncoder.encode((String) vm.get("jcr:description"), "UTF-8"));
            if (vm.containsKey("jcr:createdBy")) {
                translationObject.key("jcr:createdBy");
                translationObject.value(URLEncoder.encode((String) vm.get("jcr:createdBy"), "UTF-8"));
            }
            if (vm.containsKey("jcr:title")) {
                translationObject.key("jcr:title");
                translationObject.value(URLEncoder.encode((String) vm.get("jcr:title"), "UTF-8"));
            }
            if (vm.containsKey("postEdited")) {
                translationObject.key("postEdited");
                translationObject.value(vm.get("postEdited"));
            }
            if (vm.containsKey("translationDate")) {
                translationObject.key("translationDate");
                translationObject.value(((Calendar) vm.get("translationDate")).getTimeInMillis());
                timestampFields.put("translationDate");
            }
            if (vm.containsKey("jcr:created")) {
                translationObject.key("jcr:created");
                translationObject.value(((Calendar) vm.get("jcr:created")).getTimeInMillis());
                timestampFields.put("jcr:created");
            }
            if (timestampFields.length() > 0) {
                translationObject.key(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS);
                translationObject.value(timestampFields);
            }
            translationObject.endObject();
        }
    }
    public static void extractFlags(final JSONWriter responseArray, final Resource rootNode) throws JSONException, IOException {
        final Iterable<Resource> responses = rootNode.getChildren();

        for (final Resource response : responses) {
            final ValueMap properties = response.adaptTo(ValueMap.class);
            final String userIdentifier = properties.get("userIdentifier", "");
            final String responseValue = properties.get("response", "");
            final String author_name = properties.get("author_display_name", "");
            final String flag_reason = properties.get("social:flagReason", "");
            final JSONWriter flagObject = responseArray.object();
            flagObject.key("timestamp");
            if (properties.get("added") instanceof Calendar) {
                flagObject.value(((Calendar) properties.get("added")).getTimeInMillis());
            } else {
                flagObject.value(properties.get("added", GregorianCalendar.getInstance().getTimeInMillis()));
            }
            flagObject.key("response");
            flagObject.value(URLEncoder.encode(responseValue, "UTF-8"));
            flagObject.key("author_username");
            flagObject.value(URLEncoder.encode(userIdentifier, "UTF-8"));
            if (!"".equals(author_name)) {
                flagObject.key("author_display_name");
                flagObject.value(URLEncoder.encode(author_name, "UTF-8"));
            }
            flagObject.key("social:flagReason");
            flagObject.value(URLEncoder.encode(flag_reason, "UTF-8"));
            flagObject.endObject();
        }
    }
}



