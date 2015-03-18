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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;

import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import com.adobe.cq.social.commons.FileDataSource;
import com.adobe.cq.social.commons.client.endpoints.OperationException;
import com.adobe.cq.social.forum.api.Post;
import com.adobe.cq.social.tally.Poll;
import com.adobe.cq.social.tally.Tally;
import com.adobe.cq.social.tally.Voting;
import com.adobe.cq.social.tally.client.api.RatingSocialComponent;
import com.adobe.cq.social.tally.client.api.VotingSocialComponent;
import com.adobe.cq.social.tally.client.endpoints.TallyOperationsService;
import com.adobe.cq.social.ugcbase.SocialResourceProvider;
import com.fasterxml.jackson.core.JsonLocation;
import org.apache.commons.codec.binary.Base64;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import javax.activation.DataSource;
import javax.jcr.Session;
import javax.naming.OperationNotSupportedException;

public class UGCImportHelper {

    public static Resource extractResource(final JsonParser parser, final SocialResourceProvider provider,
                                       final ResourceResolver resolver, final String path)
            throws IOException {
        final Map<String, Object> properties = new HashMap<String, Object>();

        while(parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.getCurrentName();
            parser.nextToken();
            JsonToken token = parser.getCurrentToken();

            if (name.equals(ContentTypeDefinitions.LABEL_SUBNODES)) {
                if (token.equals(JsonToken.START_OBJECT)) {
                    parser.nextToken();
                    final String childPath = path + "/" + parser.getCurrentName();
                    parser.nextToken(); //should equal JsonToken.START_OBJECT
                    final Resource childResource = extractResource(parser, provider, resolver, childPath); // should we do anything with this?
                }
            }
        }

        return provider.create(resolver, path, properties);
    }

    public static Map<String, Object> extractSubmap(final JsonParser jsonParser) throws IOException {
        jsonParser.nextToken(); //skip the START_OBJECT token
        final Map<String, Object> subMap = new HashMap<String, Object>();
        while (!jsonParser.getCurrentToken().equals(JsonToken.END_OBJECT)) {
            final String label = jsonParser.getCurrentName(); //get the current label
            final JsonToken token = jsonParser.nextToken(); //get the current value
            if (!token.isScalarValue()) {
                if (token.equals(JsonToken.START_OBJECT)) {
                    //if the next token starts a new object, recurse into it
                    subMap.put(label, extractSubmap(jsonParser));
                } else if (token.equals(JsonToken.START_ARRAY)) {
                    final List<String> subArray = new ArrayList<String>();
                    jsonParser.nextToken(); //skip the START_ARRAY token
                    while(!jsonParser.getCurrentToken().equals(JsonToken.END_ARRAY)) {
                        subArray.add(jsonParser.getValueAsString());
                        jsonParser.nextToken();
                    }
                    subMap.put(label, subArray);
                    jsonParser.nextToken(); //skip the END_ARRAY token
                }
            } else {
                //either a string, boolean, or long value
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
        jsonParser.nextToken(); //skip the END_OBJECT token
        return subMap;
    }

    public static void extractTally(final Resource post, final JsonParser jsonParser, final SocialResourceProvider srp,
                             final TallyOperationsService tallyOperationsService)
        throws IOException {
        jsonParser.nextToken(); //should be start object, but would be end array if no objects were present
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
                    response = jsonParser.getValueAsString();
                } else if (label.equals("userIdentifier")) {
                    userIdentifier = jsonParser.getValueAsString();
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
                                    final TallyOperationsService tallyOperationsService)
        throws PersistenceException {

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
            properties.put("timestamp", calendar.getTime());
            tallyOperationsService.setTallyResponse(tally, userIdentifier, resolver.adaptTo(Session.class), response,
                    tallyType, properties);
        } catch (OperationException e) {
            throw new RuntimeException("Unable to set the tally response value: " + e.getMessage(), e);
        }
    }

    /**
     * This class must implement DataSource in order to be used to create an attachment, and also FileDataSource in
     * order to be used by the filterAttachments method in AbstractCommentOperationService
     */
    public static class AttachmentStruct implements DataSource, FileDataSource {
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
