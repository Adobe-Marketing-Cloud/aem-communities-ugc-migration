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
import java.util.Iterator;
import java.util.Map;

//import com.adobe.communities.ugc.migration.legacyExport.ContentTypeDefinitions;
import com.adobe.cq.social.tally.Response;
import com.adobe.cq.social.tally.Vote;
import com.adobe.cq.social.tally.Voting;
import org.apache.commons.codec.binary.Base64;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;

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

    public static void extractTally(final JSONWriter voteObjects, final Resource voteResource, final String tallyType)
    throws JSONException {
        final Voting voting = voteResource.adaptTo(Voting.class);
        final Iterator<Response<Vote>> responses = voting.getResponses(0L);
        // every tally object must define 4 fields: timestamp, response, userIdentifier, and tallyType
        while (responses.hasNext()) {
            voteObjects.object();
            final Response<Vote> response = responses.next();
            final Resource responseResource = response.getResource();
            final ValueMap resourceProperties = responseResource.adaptTo(ValueMap.class);
            voteObjects.key("timestamp");
            voteObjects.value(resourceProperties.get("timestamp"));
            voteObjects.key("response");
            voteObjects.value(resourceProperties.get("response"));
            voteObjects.key("userIdentifier");
            voteObjects.value(resourceProperties.get("userIdentifier"));
            // for the purposes of this export, tallyType is fixed
            voteObjects.key("tallyType");
            voteObjects.value(tallyType);
            voteObjects.endObject();
        }
    }
}



