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
package com.adobe.communities.ugc.migration.importer;

import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import com.adobe.cq.social.calendar.client.endpoints.CalendarOperations;
import com.adobe.cq.social.calendar.client.endpoints.CalendarRequestConstants;
import com.adobe.cq.social.calendar.CalendarConstants;
import com.adobe.cq.social.commons.Comment;
import com.adobe.cq.social.commons.FileDataSource;
import com.adobe.cq.social.commons.comments.endpoints.CommentOperations;
import com.adobe.cq.social.filelibrary.client.api.FileLibrary;
import com.adobe.cq.social.filelibrary.client.endpoints.FileLibraryOperations;
import com.adobe.cq.social.forum.client.api.Forum;
import com.adobe.cq.social.forum.client.api.Post;
import com.adobe.cq.social.forum.client.endpoints.ForumOperations;
import com.adobe.cq.social.journal.client.api.Journal;
import com.adobe.cq.social.journal.client.endpoints.JournalOperations;
import com.adobe.cq.social.qna.client.api.QnaPost;
import com.adobe.cq.social.qna.client.endpoints.QnaForumOperations;
import com.adobe.cq.social.scf.OperationException;
import com.adobe.cq.social.srp.SocialResourceProvider;
import com.adobe.cq.social.srp.config.SocialResourceConfiguration;
import com.adobe.cq.social.tally.Tally;
import com.adobe.cq.social.tally.TallyConstants;
import com.adobe.cq.social.tally.Voting;
import com.adobe.cq.social.tally.client.api.RatingSocialComponent;
import com.adobe.cq.social.tally.client.api.VotingSocialComponent;
import com.adobe.cq.social.tally.client.endpoints.TallyOperationsService;
import com.adobe.cq.social.ugcbase.SocialUtils;
import com.adobe.cq.social.ugcbase.core.SocialResourceUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.commons.codec.binary.Base64;
import org.apache.felix.scr.annotations.Reference;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.ModifyingResourceProvider;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.DataSource;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;
import javax.xml.crypto.Data;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

public class UGCImportHelper {
    private static final Logger LOG = LoggerFactory.getLogger(UGCImportHelper.class);

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
    private JournalOperations journalOperations;

    @Reference
    private FileLibraryOperations fileLibraryOperations;

    @Reference
    private SocialUtils socialUtils;

    private SocialResourceProvider resProvider;

    /**
     * These values ought to come from com.adobe.cq.social.calendar.CalendarConstants, but that class isn't in the
     * uberjar, so I'll define the constants here instead.
     */
    final static String PN_START = "calendar_event_start_dt";
    final static String PN_END = "calendar_event_end_dt";

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

    public void setCommentOperations(final CommentOperations commentOperations) {
        if (this.commentOperations == null)
            this.commentOperations = commentOperations;
    }

    public void setQnaForumOperations(final QnaForumOperations qnaForumOperations) {
        if (this.qnaForumOperations == null)
            this.qnaForumOperations = qnaForumOperations;
    }

    public void setCalendarOperations(final CalendarOperations calendarOperations) {
        if (this.calendarOperations == null)
            this.calendarOperations = calendarOperations;
    }

    public void setJournalOperations(final JournalOperations journalOperations) {
        if (this.journalOperations == null)
            this.journalOperations = journalOperations;
    }

    public void setFileLibraryOperations(final FileLibraryOperations fileLibraryOperations) {
        if (this.fileLibraryOperations == null)
            this.fileLibraryOperations = fileLibraryOperations;
    }

    public void setSocialUtils(final SocialUtils socialUtils) {
        if (this.socialUtils == null)
            this.socialUtils = socialUtils;
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
        final ModifyingResourceProvider srp, final TallyOperationsService tallyOperationsService) throws IOException {
        jsonParser.nextToken(); // should be start object, but would be end array if no objects were present
        while (!jsonParser.getCurrentToken().equals(JsonToken.END_ARRAY)) {
            Long timestamp = null;
            String userIdentifier = null;
            String response = null;
            String tallyType = null;
            jsonParser.nextToken(); // should make current token by "FIELD_NAME" but could be END_OBJECT if this were
// an empty object
            while (!jsonParser.getCurrentToken().equals(JsonToken.END_OBJECT)) {
                final String label = jsonParser.getCurrentName();
                jsonParser.nextToken(); // should be FIELD_VALUE
                if (label.equals(TallyConstants.TIMESTAMP_PROPERTY)) {
                    timestamp = jsonParser.getValueAsLong();
                } else {
                    final String responseValue = jsonParser.getValueAsString();
                    if (label.equals("response")) {
                        response = URLDecoder.decode(responseValue, "UTF-8");
                    } else if (label.equals("userIdentifier")) {
                        userIdentifier = URLDecoder.decode(responseValue, "UTF-8");
                    } else if (label.equals("tallyType")) {
                        tallyType = responseValue;
                    }
                }
                jsonParser.nextToken(); // should make current token be "FIELD_NAME" unless we're at the end of our
// loop and it's now "END_OBJECT" instead
            }
            if (timestamp != null && userIdentifier != null && response != null && tallyType != null) {
                createTally(srp, post, tallyType, userIdentifier, timestamp, response, tallyOperationsService);
            }
            jsonParser.nextToken(); // may advance to "START_OBJECT" if we're not finished yet, but might be
// "END_ARRAY" now
        }
    }

    private static void createTally(final ModifyingResourceProvider srp, final Resource post, final String tallyType,
        final String userIdentifier, final Long timestamp, final String response,
        final TallyOperationsService tallyOperationsService) throws PersistenceException {

        final ResourceResolver resolver = post.getResourceResolver();
        final Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("jcr:primaryType", "social:asiResource");
        final Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(timestamp);
        properties.put("jcr:created", calendar.getTime());
        Resource tallyResource = null;
        Tally tally;
        if (tallyType.equals(TallyOperationsService.VOTING)) {
            if (post.getResourceType().equals(VotingSocialComponent.VOTING_RESOURCE_TYPE)) {
                tallyResource = post;
            } else {
                Iterable<Resource> postChildren = post.getChildren();
                for (final Resource postChild : postChildren) {
                    if (postChild.getResourceType().equals(VotingSocialComponent.VOTING_RESOURCE_TYPE)) {
                        tallyResource = postChild;
                        break;
                    }
                }
                if (tallyResource == null) {
                    properties.put("sling:resourceType", VotingSocialComponent.VOTING_RESOURCE_TYPE);
                    properties.put("social:parentid", post.getPath());
                    tallyResource = resolver.create(post, "voting", properties);
                    try {
                        resolver.commit();
                    } catch (Exception e) {
                        // ignoring exception to let the rest of the file get imported
                        LOG.error("Could not create vote {} on post {}", tallyResource, post);
                    }
                    properties.remove("sling:resourceType");
                }
            }
            tally = tallyResource.adaptTo(Voting.class);
            tally.setTallyResourceType(VotingSocialComponent.VOTING_RESOURCE_TYPE);
        } else if (tallyType.equals(TallyOperationsService.POLL)) {
            // don't throw an exception, since we know what it is, but log a warning since we no longer support polls
            LOG.warn("Unsupported tally type 'poll' could not be imported");
            return;
        } else if (tallyType.equals(TallyOperationsService.RATING)) {
            if (post.getResourceType().equals(RatingSocialComponent.RATING_RESOURCE_TYPE)) {
                tallyResource = post;
            } else {
                Iterable<Resource> postChildren = post.getChildren();
                for (final Resource postChild : postChildren) {
                    if (postChild.getResourceType().equals(RatingSocialComponent.RATING_RESOURCE_TYPE)) {
                        tallyResource = postChild;
                        break;
                    }
                }
                if (tallyResource == null) {
                    properties.put("sling:resourceType", RatingSocialComponent.RATING_RESOURCE_TYPE);
                    tallyResource = srp.create(resolver, post.getPath() + "/rating_" + randomHexString(), properties);
                    srp.commit(resolver);
                    properties.remove("sling:resourceType");
                }
            }
            tally = tallyResource.adaptTo(Voting.class);
            tally.setTallyResourceType(RatingSocialComponent.RATING_RESOURCE_TYPE);
        } else {
            throw new RuntimeException("unrecognized tally type");
        }
        // Needed params:
        try {
            properties.put(TallyConstants.TIMESTAMP_PROPERTY, calendar);
            tallyOperationsService.setTallyResponse(tally.getTallyTarget(), userIdentifier,
                resolver.adaptTo(Session.class), response, tallyType, properties);
        } catch (final OperationException e) {
            throw new RuntimeException("Unable to set the tally response value: " + e.getMessage(), e);
        } catch (final IllegalArgumentException e) {
            // We can ignore this. It means that the value set for the response in the migrated data is no longer
            // valid. This happens for "#neutral#" which used to be a valid response, but was taken out in later
            // versions.
        }
    }

    public void importQnaContent(final JsonParser jsonParser, final Resource resource, final ResourceResolver resolver)
        throws ServletException, IOException {
        if (!resource.getResourceType().equals(QnaPost.RESOURCE_TYPE)) {
            throw new IOException("Cannot import qna topic onto non-qna parent resource");
        }
        if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
            jsonParser.nextToken(); // advance to first key in the object - should be the id value of the old post
            while (jsonParser.getCurrentToken().equals(JsonToken.FIELD_NAME)) {
                extractTopic(jsonParser, resource, resolver, qnaForumOperations);
                jsonParser.nextToken(); // get the next token - presumably a field name for the next post
            }
            jsonParser.nextToken(); // skip end token
        } else {
            throw new IOException("Improperly formed JSON - expected an OBJECT_START token, but got "
                    + jsonParser.getCurrentToken().toString());
        }
    }

    public void importForumContent(final JsonParser jsonParser, final Resource resource,
        final ResourceResolver resolver) throws ServletException, IOException {
        if (!resource.getResourceType().equals(Forum.RESOURCE_TYPE)) {
            throw new IOException("Cannot import forum topic onto non-forum parent resource");
        }
        if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
            jsonParser.nextToken(); // advance to first key in the object - should be the id value of the old post
            while (jsonParser.getCurrentToken().equals(JsonToken.FIELD_NAME)) {
                extractTopic(jsonParser, resource, resolver, forumOperations);
                jsonParser.nextToken(); // get the next token - presumably a field name for the next post
            }
            jsonParser.nextToken(); // skip end token
        } else {
            throw new IOException("Improperly formed JSON - expected an OBJECT_START token, but got "
                    + jsonParser.getCurrentToken().toString());
        }
    }

    public void importCommentsContent(final JsonParser jsonParser, final Resource resource,
        final ResourceResolver resolver) throws ServletException, IOException {
        if (!resource.getResourceType()
                .equals(com.adobe.cq.social.commons.comments.api.Comment.COMMENTCOLLECTION_RESOURCETYPE)) {
            throw new IOException("Cannot import comment onto non-comment system parent resource");
        }
        if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
            jsonParser.nextToken(); // advance to first key in the object - should be the id value of the old post
            while (jsonParser.getCurrentToken().equals(JsonToken.FIELD_NAME)) {
                extractTopic(jsonParser, resource, resolver, commentOperations);
                jsonParser.nextToken(); // get the next token - presumably a field name for the next post
            }
            jsonParser.nextToken(); // skip end token
        } else {
            throw new IOException("Improperly formed JSON - expected an OBJECT_START token, but got "
                    + jsonParser.getCurrentToken().toString());
        }
    }

    public void importJournalContent(final JsonParser jsonParser, final Resource resource,
        final ResourceResolver resolver) throws IOException, ServletException {
        if (!resource.getResourceType().equals(Journal.RESOURCE_TYPE)) {
            throw new IOException("Cannot import journal entry onto non-journal parent resource");
        }
        if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
            jsonParser.nextToken(); // advance to first key in the object - should be the id value of the old post
            while (jsonParser.getCurrentToken().equals(JsonToken.FIELD_NAME)) {
                extractTopic(jsonParser, resource, resolver, journalOperations);
                jsonParser.nextToken(); // get the next token - presumably a field name for the next post
            }
            jsonParser.nextToken(); // skip end token
        } else {
            throw new IOException("Improperly formed JSON - expected an OBJECT_START token, but got "
                    + jsonParser.getCurrentToken().toString());
        }
    }

    public void importFileLibrary(final JsonParser jsonParser, final Resource resource,
                                     final ResourceResolver resolver) throws IOException, ServletException {
        if (!resource.getResourceType().equals(FileLibrary.RESOURCE_TYPE_FILELIBRARY)) {
            throw new IOException("Cannot import file library onto non-file library parent resource");
        }
        if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
            jsonParser.nextToken(); // advance to first key in the object - should be the id value of the old post
            while (jsonParser.getCurrentToken().equals(JsonToken.FIELD_NAME)) {
                extractTopic(jsonParser, resource, resolver, fileLibraryOperations);
                jsonParser.nextToken(); // get the next token - presumably a field name for the next post
            }
            jsonParser.nextToken(); // skip end token
        } else {
            throw new IOException("Improperly formed JSON - expected an OBJECT_START token, but got "
                    + jsonParser.getCurrentToken().toString());
        }
    }

    public void importCalendarContent(final JsonParser jsonParser, final Resource resource) throws IOException {
        if (!resource.getResourceType().equals(com.adobe.cq.social.calendar.client.api.Calendar.RESOURCE_TYPE)) {
            throw new IOException("Cannot import calendar event onto non-calendar parent resource");
        }
        if (jsonParser.getCurrentToken().equals(JsonToken.START_ARRAY)) {
            jsonParser.nextToken(); // skip START_ARRAY here
        } else {
            throw new IOException("Improperly formed JSON - expected an START_ARRAY token, but got "
                    + jsonParser.getCurrentToken().toString());
        }
        if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
            while (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
                extractEvent(jsonParser, resource);
                jsonParser.nextToken(); // get the next token - either a start object token or an end array token
            }
        } else {
            throw new IOException("Improperly formed JSON - expected an OBJECT_START token, but got "
                    + jsonParser.getCurrentToken().toString());
        }
    }

    public void importTallyContent(final JsonParser jsonParser, final Resource resource) throws IOException {

        SocialResourceConfiguration config = socialUtils.getStorageConfig(resource);
        final String rootPath = config.getAsiPath();
        if (null == resProvider) {
            resProvider =
                    SocialResourceUtils.getSocialResource(resource.getResourceResolver().getResource(rootPath))
                            .getResourceProvider();
            resProvider.setConfig(config);
        }
        if (jsonParser.getCurrentToken().equals(JsonToken.START_ARRAY)) {
            extractTally(resource, jsonParser, resProvider, tallyOperationsService);
            jsonParser.nextToken(); // get the next token - either a start object token or an end array token
        } else {
            throw new IOException("Improperly formed JSON - expected an START_ARRAY token, but got "
                    + jsonParser.getCurrentToken().toString());
        }
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
            boolean isClosed = false;
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
                            final String decodedValue = URLDecoder.decode(value, "UTF-8");
                            if (label.equals("language")) {
                                properties.put("mtlanguage", decodedValue);
                            } else {
                                properties.put(label, decodedValue);
                                if (label.equals("userIdentifier")) {
                                    author = decodedValue;
                                } else if (label.equals("jcr:description")) {
                                    properties.put("message", decodedValue);
                                }
                            }
                        }
                    }
                } else if (label.equals(ContentTypeDefinitions.LABEL_ATTACHMENTS)) {
                    attachments = getAttachments(jsonParser);
                } else if (label.equals(ContentTypeDefinitions.LABEL_REPLIES)
                        || label.equals(ContentTypeDefinitions.LABEL_TALLY)
                        || label.equals(ContentTypeDefinitions.LABEL_TRANSLATION)
                        || label.equals(ContentTypeDefinitions.LABEL_SUBNODES)) {
                    // replies and sub-nodes ALWAYS come after all other properties and attachments have been listed,
                    // so we can create the post now if we haven't already, and then dive in
                    if (post == null) {
                        if (properties.containsKey("isClosed")) {
                            if ((Boolean)properties.get("isClosed")) {
                                isClosed = true;
                                properties.put("isClosed", false);
                            }
                        }
                        try {
                            post =
                                createPost(resource, author, properties, attachments,
                                    resolver.adaptTo(Session.class), operations);
                            if (null == resProvider) {
                                resProvider = SocialResourceUtils.getSocialResource(post).getResourceProvider();
                                resProvider.setConfig(socialUtils.getStorageConfig(post));
                            }
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
                        UGCImportHelper.extractTally(post, jsonParser, resProvider, tallyOperationsService);
                    } else if (label.equals(ContentTypeDefinitions.LABEL_TRANSLATION)) {
                        importTranslation(jsonParser, post);
                        resProvider.commit(post.getResourceResolver());
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
                    if (properties.containsKey("isClosed")) {
                        if ((Boolean)properties.get("isClosed")) {
                            isClosed = true;
                            properties.put("isClosed", false);
                        }
                    }
                    post =
                        createPost(resource, author, properties, attachments, resolver.adaptTo(Session.class),
                            operations);
                    if (null == resProvider) {
                        resProvider = SocialResourceUtils.getSocialResource(post).getResourceProvider();
                        resProvider.setConfig(socialUtils.getStorageConfig(post));
                    }
// resProvider.commit(resolver);
                } catch (Exception e) {
                    throw new ServletException(e.getMessage(), e);
                }
            }
            if (isClosed) {
                // todo - this DOES NOT WORK in MSRP - figure out why and fix it
                final ModifiableValueMap map = resolver.getResource(post.getPath()).adaptTo(ModifiableValueMap.class);
                map.put("isClosed", true);
                resolver.commit();
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
                    // log the fact that we skipped it
                    LOG.warn("We failed to create a post because it had an empty message and title");
                    return false;
                } else {
                    properties.put(Comment.PROP_MESSAGE, message);
                    properties.put("message", message);
                }
            } else {
                // If title and message are both blank, we skip this entry and
                // log the fact that we skipped it
                LOG.warn("We failed to create a post because it had an empty message and title");
                return false;
            }
        }
        return true;
    }

    protected void extractEvent(final JsonParser jsonParser, final Resource resource) throws IOException {

        String author = null;
        Map<String, Object> eventParams = new HashMap<String, Object>();
        jsonParser.nextToken();
        JsonToken token = jsonParser.getCurrentToken();
        List<DataSource> attachments = null;
        boolean hasTranslation = false;
        while (token.equals(JsonToken.FIELD_NAME)) {
            String field = jsonParser.getCurrentName();
            jsonParser.nextToken();

            if(field.equals(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS)) {
                jsonParser.nextToken(); // advance to first field name
                while (!jsonParser.getCurrentToken().equals(JsonToken.END_ARRAY)) {
                    final String timestampLabel = jsonParser.getValueAsString();
                    if (eventParams.containsKey(timestampLabel)) {
                        final Calendar calendar = new GregorianCalendar();
                        calendar.setTimeInMillis(
                                Long.parseLong((String)eventParams.get(timestampLabel)));
                        eventParams.put(timestampLabel, calendar);
                    }
                    jsonParser.nextToken();
                }
            } else if (field.equals(CalendarRequestConstants.TAGS)) {
                List<String> tags = new ArrayList<String>();
                if (jsonParser.getCurrentToken().equals(JsonToken.START_ARRAY)) {
                    token = jsonParser.nextToken();
                    while (!token.equals(JsonToken.END_ARRAY)) {
                        tags.add(URLDecoder.decode(jsonParser.getValueAsString(), "UTF-8"));
                        token = jsonParser.nextToken();
                    }
                    eventParams.put(CalendarRequestConstants.TAGS, tags);
                } else {
                    LOG.warn("Tags field came in without an array of tags in it - not processed");
                    // do nothing, just log the error
                }
            } else if (field.equals("jcr:createdBy")) {
                author = URLDecoder.decode(jsonParser.getValueAsString(), "UTF-8");
            } else if (field.equals(ContentTypeDefinitions.LABEL_ATTACHMENTS)) {
                attachments = getAttachments(jsonParser);
            } else if (field.equals(CalendarConstants.PN_COVER_IMAGE)) {
                if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
                    DataSource coverimage = getAttachment(jsonParser);
                    if (null != coverimage) {
                        eventParams.put(CalendarConstants.PN_COVER_IMAGE, coverimage);
                    } else {
                        LOG.warn("Cover image property did not have a valid attachment object");
                    }
                } else {
                    LOG.error("Cover image property was not a json object");
                    jsonParser.skipChildren();
                }
            } else if (field.equals(ContentTypeDefinitions.LABEL_TRANSLATION)) {
                hasTranslation = true;
                break; // should always be the last item in the object, but break anyway, just in case
            } else {
                final String value = URLDecoder.decode(jsonParser.getValueAsString(), "UTF-8");
                eventParams.put(field, value);
            }
            token = jsonParser.nextToken();
        }
        try {
            eventParams.put("sling:resourceType", com.adobe.cq.social.calendar.client.api.Calendar.RESOURCE_TYPE_EVENT);
            final Map<String, Object> props = fillCustomCalendarProperties(eventParams);
            if (null == attachments) {
                attachments = Collections.emptyList();
            }
            final Resource post = calendarOperations.create(resource, author, props, attachments,
                    resource.getResourceResolver().adaptTo(Session.class));
            if (hasTranslation) {
                importTranslation(jsonParser, post);
                token = jsonParser.nextToken();
                if(!token.equals(JsonToken.END_OBJECT)) {
                    // translation was not the last item in the object, any other info was lost
                    LOG.error("translation was not the final item in the exported event: " + post.getPath());
                    while (!token.equals(JsonToken.END_OBJECT)) {
                        if (token.equals(JsonToken.START_OBJECT) || token.equals(JsonToken.START_ARRAY)) {
                            jsonParser.skipChildren();
                        }
                        token = jsonParser.nextToken();
                    }
                }
            }
        } catch (final OperationException e) {
//            probably caused by creating a folder that already exists. We ignore it, but still log the event.
            LOG.info("There was an operation exception while creating an event: " + e.getMessage());
        }
    }

    private Map<String, Object> fillCustomCalendarProperties(final Map<String, Object> props) {
        final Map<String, String> relabels = new HashMap<String, String>();
        relabels.put("location", "location_t");
        relabels.put("subject",  "jcr:title");
        relabels.put("jcr:description", "message");
        for (final String key : relabels.keySet()) {
            if (props.containsKey(key)) {
                props.put(relabels.get(key), props.get(key));
                props.remove(key);
            }
        }
        return props;
    }

    protected static List<DataSource> getAttachments(final JsonParser jsonParser) throws IOException {
        // an attachment has only 3 fields - jcr:data, filename, jcr:mimeType
        List<DataSource> attachments = new ArrayList<DataSource>();
        getAttachments(jsonParser, attachments);
        return attachments;
    }

    protected static void getAttachments(final JsonParser jsonParser, final List attachments) throws IOException {

        JsonToken token = jsonParser.nextToken(); // skip START_ARRAY token
        while (token.equals(JsonToken.START_OBJECT)) {
            DataSource attachment = getAttachment(jsonParser);
            if (null != attachment) {
                attachments.add(attachment);
            }
            token = jsonParser.nextToken();
        }
    }

    protected static DataSource getAttachment(final JsonParser jsonParser) throws IOException {

        String filename = null;
        String mimeType = null;
        InputStream inputStream = null;
        byte[] databytes = null;
        JsonToken token = jsonParser.nextToken();
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
            return new UGCImportHelper.AttachmentStruct(filename, inputStream, mimeType, databytes.length);
        } else {
            // log an error
            LOG.error("We expected to import an attachment, but information was missing and nothing was imported");
            return null;
        }
    }

    protected static void importTranslation(final JsonParser jsonParser, final Resource post) throws IOException {
        JsonToken token = jsonParser.getCurrentToken();
        final Map<String, Object> properties = new HashMap<String, Object>();
        if (token != JsonToken.START_OBJECT) {
            throw new IOException("expected a start object token, got " + token.asString());
        }
        properties.put("jcr:primaryType", "social:asiResource");
        Resource translationFolder = null;
        token = jsonParser.nextToken();
        while (token == JsonToken.FIELD_NAME) {
            token = jsonParser.nextToken(); //advance to the field value
            if (jsonParser.getCurrentName().equals((ContentTypeDefinitions.LABEL_TRANSLATIONS))) {
                if (null == translationFolder) {
                    // begin by creating the translation folder resource
                    translationFolder = post.getResourceResolver().create(post, "translation", properties);
                }
                //now check to see if any translations exist
                if (token == JsonToken.START_OBJECT) {
                    token = jsonParser.nextToken();
                    if (token == JsonToken.FIELD_NAME) {
                        while (token == JsonToken.FIELD_NAME) { // each new field represents another translation
                            final Map<String, Object> translationProperties = new HashMap<String, Object>();
                            translationProperties.put("jcr:primaryType", "social:asiResource");
                            String languageLabel = jsonParser.getCurrentName();
                            token = jsonParser.nextToken();
                            if (token != JsonToken.START_OBJECT) {
                                throw new IOException("expected a start object token for translation item, got "
                                        + token.asString());
                            }
                            token = jsonParser.nextToken();
                            while (token != JsonToken.END_OBJECT) {
                                jsonParser.nextToken(); //get next field value
                                if(jsonParser.getCurrentName().equals(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS)) {
                                    jsonParser.nextToken(); // advance to first field name
                                    while (!jsonParser.getCurrentToken().equals(JsonToken.END_ARRAY)) {
                                        final String timestampLabel = jsonParser.getValueAsString();
                                        if (translationProperties.containsKey(timestampLabel)) {
                                            final Calendar calendar = new GregorianCalendar();
                                            calendar.setTimeInMillis(
                                                    Long.parseLong((String)translationProperties.get(timestampLabel)));
                                            translationProperties.put(timestampLabel, calendar.getTime());
                                        }
                                        jsonParser.nextToken();
                                    }
                                } else if (jsonParser.getCurrentName().equals(ContentTypeDefinitions.LABEL_SUBNODES)) {
                                    jsonParser.skipChildren();
                                } else {
                                    translationProperties.put(jsonParser.getCurrentName(),
                                            URLDecoder.decode(jsonParser.getValueAsString(), "UTF-8"));
                                }
                                token = jsonParser.nextToken(); //get next field label
                            }
                            // add the language-specific translation under the translation folder resource
                            Resource translation = post.getResourceResolver().create(post.getChild("translation"),
                                    languageLabel, translationProperties);
                            if (null == translation) {
                                throw new IOException("translation not actually imported");
                            }
                        }
                        jsonParser.nextToken(); //skip END_OBJECT token for translation
                    } else if (token == JsonToken.END_OBJECT) {
                        // no actual translation to import, so we're done here
                        jsonParser.nextToken();
                    }
                } else {
                    throw new IOException("expected translations to be contained in an object, saw instead: "
                            +token.asString());
                }
            }
            else if (jsonParser.getCurrentName().equals("mtlanguage") ||
                     jsonParser.getCurrentName().equals("jcr:createdBy")) {
                properties.put(jsonParser.getCurrentName(), jsonParser.getValueAsString());
            } else if (jsonParser.getCurrentName().equals("jcr:created")) {
                final Calendar calendar = new GregorianCalendar();
                calendar.setTimeInMillis(jsonParser.getLongValue());
                properties.put("jcr:created", calendar.getTime());
            }
            token = jsonParser.nextToken();
        }
        if (null == translationFolder && properties.containsKey("mtlanguage")) {
            // it's possible that no translations existed, so we need to make sure the translation resource (which
            // includes the original post's detected language) is created anyway
            post.getResourceResolver().create(post, "translation", properties);
        }
    }

    public static void checkUserPrivileges(final ResourceResolver resolver, final ResourceResolverFactory rrf)
            throws ServletException {

        // determine whether the current session belongs to the group administrators
        final UserManager um = resolver.adaptTo(UserManager.class);
        try {
            final Authorizable adminGroup = um.getAuthorizable("administrators");
            final Authorizable user = resolver.adaptTo(Authorizable.class);
            final Group administrators = (Group) adminGroup;
            if (!administrators.isMember(user)) {
                throw new ServletException("Insufficient access");
            }
        } catch (final RepositoryException e) {
            throw new ServletException("Cannot access repository", e);
        }
    }

    private static String randomHexString() {
        Random rand = new Random();
        int hexInt = rand.nextInt(0X10000);
        return Integer.toHexString(hexInt).toLowerCase();
    }

    /**
     * This class must implement DataSource in order to be used to create an attachment, and also FileDataSource in
     * order to be used by the filterAttachments method in AbstractCommentOperationService
     */
    public static class AttachmentStruct implements FileDataSource {
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
         * Returns the file extension of the content.
         * @return file extension
         */
        public String getType() {
            return filename.substring(filename.lastIndexOf('.'));
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
