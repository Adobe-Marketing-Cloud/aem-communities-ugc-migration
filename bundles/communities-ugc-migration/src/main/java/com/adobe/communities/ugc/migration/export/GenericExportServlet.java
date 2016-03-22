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
import com.adobe.cq.social.srp.SocialResource;
import com.adobe.cq.social.srp.SocialResourceProvider;
import com.adobe.cq.social.tally.client.api.RatingSocialComponent;
import com.adobe.cq.social.tally.client.api.VotingSocialComponent;
import com.adobe.cq.social.calendar.CalendarConstants;
//import com.adobe.cq.social.calendar.CqCalendar;
//import com.adobe.cq.social.calendar.Event;
import com.adobe.cq.social.commons.comments.api.Comment;
import com.adobe.cq.social.commons.CommentSystem;
import com.adobe.cq.social.commons.comments.api.CommentCollection;
import com.adobe.cq.social.commons.comments.listing.CommentSocialComponentList;
import com.adobe.cq.social.forum.client.api.Forum;
import com.adobe.cq.social.forum.api.Post;
import com.adobe.cq.social.journal.client.api.Journal;
import com.adobe.cq.social.journal.client.api.JournalEntryComment;
import com.adobe.cq.social.ugcbase.SocialUtils;
import com.adobe.cq.social.ugcbase.core.SocialResourceUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component(label = "UGC Exporter for all UGC Data Types",
        description = "Moves any ugc data into a zip archive for storage or re-import", specVersion = "1.0")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/ugc/export")})
public class GenericExportServlet extends SlingSafeMethodsServlet {


    @Reference
    private SocialUtils socialUtils;

    Logger logger = LoggerFactory.getLogger(this.getClass());
    Writer responseWriter;
    ZipOutputStream zip;
    Map<String, Boolean> entries;
    Map<String, Boolean> entriesToSkip;

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws ServletException, IOException {

        if (!request.getRequestParameterMap().containsKey("path")) {
            throw new ServletException("No path specified for export. Exiting.");
        }
        final String path = StringUtils.stripEnd(request.getRequestParameter("path").getString(), "/");
        final Resource resource = request.getResourceResolver().getResource(path);
        if (resource == null) {
            throw new ServletException("Could not find a valid resource for export");
        }
        entries = new HashMap<String, Boolean>();
        entriesToSkip = new HashMap<String, Boolean>();
        File outFile = null;
        try {
            outFile = File.createTempFile(UUID.randomUUID().toString(), ".zip");
            if (!outFile.canWrite()) {
                throw new ServletException("Cannot write to specified output file");
            }
            response.setContentType("application/octet-stream");
            final String headerKey = "Content-Disposition";
            final String headerValue = "attachment; filename=\"export.zip\"";
            response.setHeader(headerKey, headerValue);

            FileOutputStream fos = new FileOutputStream(outFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            zip = new ZipOutputStream(bos);
            OutputStream outStream = null;
            InputStream inStream = null;
            try {
                exportContent(resource, path);
                if (entries.size() > 0) {
                    exportCommentSystems(entries, entriesToSkip, resource, path);
                }
                IOUtils.closeQuietly(zip);
                IOUtils.closeQuietly(bos);
                IOUtils.closeQuietly(fos);
                // obtains response's output stream
                outStream = response.getOutputStream();
                inStream = new FileInputStream(outFile);
                // copy from file to output
                IOUtils.copy(inStream, outStream);
            } catch (final IOException e) {
                throw new ServletException(e);
            } catch (final Exception e) {
                throw new ServletException(e);
            } finally {
                IOUtils.closeQuietly(zip);
                IOUtils.closeQuietly(bos);
                IOUtils.closeQuietly(fos);
                IOUtils.closeQuietly(inStream);
                IOUtils.closeQuietly(outStream);
            }
        } finally {
            if (outFile != null) {
                outFile.delete();
            }
        }
    }

    protected void exportContent(Resource rootNode, final String rootPath) throws IOException, ServletException {

        final String relPath = rootNode.getPath().substring(rootNode.getPath().indexOf(rootPath) + rootPath.length());
        String entryName = relPath.isEmpty() ? "root.json" : relPath + ".json";
        responseWriter = new OutputStreamWriter(zip);
        final JSONWriter writer = new JSONWriter(responseWriter);
        writer.setTidy(true);
//        if (rootNode.isResourceType(Comment.COMMENT_RESOURCETYPE)) {
            // if we're traversing a tree and come to a comment, we need to be looking at the comment system's
            // resource instead of the resource for the comment itself as we continue our dive.
//            Comment comment = rootNode.adaptTo(Comment.class);
//            if (null != comment) {
//                CommentSystem commentSystem = comment.getCommentSystem();
//                entries.put(commentSystem.getResource().getPath(), true);
//                return;
//            }
//        }
        try {
//            if (rootNode.isResourceType("social/qna/components/qnaforum")) {
//                final Forum forum = rootNode.adaptTo(Forum.class);
//                if (forum == null) { // avoid throwing a null pointer exception
//                    for (final Resource resource : rootNode.getChildren()) {
//                        exportContent(resource, rootPath);
//                    }
//                    return;
//                }
//                final List<Object> posts = forum.getItems();
//                if (!posts.isEmpty()) {
//                    zip.putNextEntry(new ZipEntry(entryName));
//                    final JSONWriter forumObject = writer.object();
//                    forumObject.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
//                    forumObject.value(ContentTypeDefinitions.LABEL_QNA_FORUM);
//                    forumObject.key(ContentTypeDefinitions.LABEL_CONTENT);
//                    forumObject.object();
//                    for (final Object post : posts) {
//                        forumObject.key(post.getId());
//                        final JSONWriter postObject = forumObject.object();
//                        UGCExportHelper.extractTopic(postObject, post, rootNode.getResourceResolver(),
//                            "social/qna/components/hbs/topic", "social/qna/components/hbs/post", responseWriter);
//                        forumObject.endObject();
//                    }
//                    forumObject.endObject();
//                    writer.endObject();
//                    responseWriter.flush();
//                    zip.closeEntry();
//                }
//            } else
//            if (rootNode.isResourceType(CommentSystem.RESOURCE_TYPE)) {
//                if (rootNode.getName().equals("entrycomments")) {
//                    return; // these are special cases of comments that will be handled by journal instead
//                }
//                final CommentSystem commentSystem = rootNode.adaptTo(CommentSystem.class);
//                if (commentSystem == null) { // avoid throwing a null pointer exception
//                    for (final Resource resource : rootNode.getChildren()) {
//                        exportContent(resource, rootPath);
//                    }
//                    return;
//                }
//                // we only export after all other nodes have been searched and exported as needed
//                entries.put(commentSystem.getResource().getPath(), true);
//            } else
//            if (rootNode.isResourceType(CalendarConstants.RT_CALENDAR_COMPONENT)
//                    || rootNode.isResourceType(CalendarConstants.MIX_CALENDAR)
//                    || rootNode.getResourceType().endsWith("calendar")) {
//                CqCalendar calendar = rootNode.adaptTo(CqCalendar.class);
//                if (calendar == null) { // avoid throwing a null pointer exception if this node isn't actually a
//                                        // calendar
//                    for (final Resource resource : rootNode.getChildren()) {
//                        exportContent(resource, rootPath);
//                    }
//                    return;
//                }
//                final Iterator<Event> events = calendar.getEvents();
//                if (!events.hasNext()) {
//                    return;
//                }
//                zip.putNextEntry(new ZipEntry(entryName));
//                final JSONWriter calendarNode = writer.object();
//                calendarNode.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
//                calendarNode.value(ContentTypeDefinitions.LABEL_CALENDAR);
//                calendarNode.key(ContentTypeDefinitions.LABEL_CONTENT);
//                JSONWriter eventObjects = calendarNode.array();
//                while (events.hasNext()) {
//                    final Event event = events.next();
//                    ValueMap eventProperties = event.getProperties();
//                    // we renamed "start" and "end" going from version 5.6.1 to 6.1
//                    Map<String, String> propertyNames = new HashMap<String, String>();
//                    propertyNames.put("start", "calendar_event_start_dt");
//                    propertyNames.put("end", "calendar_event_end_dt");
//                    propertyNames.put("jcr:title", "subject");
//                    propertyNames.put("jcr:created", "added");
//                    propertyNames.put("jcr:primaryType", null);
//                    UGCExportHelper.extractProperties(eventObjects.object(), eventProperties, propertyNames,
//                        "social/calendar/components/hbs/event");
//                    eventObjects.endObject();
//                }
//                calendarNode.endArray();
//                writer.endObject();
//                responseWriter.flush();
//                zip.closeEntry();
//            } else
            if (rootNode.isResourceType(VotingSocialComponent.VOTING_RESOURCE_TYPE)) { // No constant defined in 5.6.1
                zip.putNextEntry(new ZipEntry(entryName));
                final JSONWriter tallyNode = writer.object();
                tallyNode.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
                tallyNode.value(ContentTypeDefinitions.LABEL_TALLY);
                tallyNode.key(ContentTypeDefinitions.LABEL_CONTENT);
                final JSONWriter responseArray = tallyNode.array();
                UGCExportHelper.extractTally(responseArray, rootNode, "Voting");
                tallyNode.endArray();
                writer.endObject();
                responseWriter.flush();
                zip.closeEntry();
            } else
            if (rootNode.isResourceType(RatingSocialComponent.RATING_RESOURCE_TYPE)) { // No constant defined in 5.6.1
                zip.putNextEntry(new ZipEntry(entryName));
                final JSONWriter tallyNode = writer.object();
                tallyNode.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
                tallyNode.value(ContentTypeDefinitions.LABEL_TALLY);
                tallyNode.key(ContentTypeDefinitions.LABEL_CONTENT);
                final JSONWriter responseArray = tallyNode.array();
                UGCExportHelper.extractTally(responseArray, rootNode, "Rating");
                tallyNode.endArray();
                writer.endObject();
                responseWriter.flush();
                zip.closeEntry();
            } else
//            if (rootNode.isResourceType("social/journal/components/entrylist")) {
//                final Journal journal = rootNode.adaptTo(Journal.class);
//                if (journal == null) { // avoid throwing a null pointer exception
//                    for (final Resource resource : rootNode.getChildren()) {
//                        exportContent(resource, rootPath);
//                    }
//                    return;
//                }
//                zip.putNextEntry(new ZipEntry(entryName));
//                final JSONWriter journalNode = writer.object();
//                journalNode.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
//                journalNode.value(ContentTypeDefinitions.LABEL_JOURNAL);
//                journalNode.key(ContentTypeDefinitions.LABEL_CONTENT);
//                JSONWriter entryObjects = journalNode.object();
//                final List<Object> entries = journal.getItems();
//                for (final Object entry : entries) {
//                    entryObjects.key(((JournalEntryComment) entry).getId().toString());
//                    UGCExportHelper.extractJournalEntry(entryObjects.object(), entry, responseWriter);
//                    entryObjects.endObject();
//                    final List<Object> comments = ((JournalEntryComment)entry).getItems();
//                    if (comments.hasNext()) {
//                        final Comment comment = comments.next();
//                        final CommentSystem commentSystem = comment.getCommentSystem();
//                        entriesToSkip.put(commentSystem.getResource().getPath(), true);
//                    }
//                }
//                journalNode.endObject();
//                writer.endObject();
//                responseWriter.flush();
//                zip.closeEntry();
            if (rootNode.isResourceType(Comment.COMMENTCOLLECTION_RESOURCETYPE)) {
                final CommentSystem commentSystem = rootNode.adaptTo(CommentSystem.class);
                int commentSize = commentSystem.countComments();
                if (commentSize == 0) {
                    return;
                }

                List<com.adobe.cq.social.commons.Comment> comments = commentSystem.getComments(0, commentSize);
                zip.putNextEntry(new ZipEntry(entryName));
                final JSONWriter commentsNode = writer.object();
                commentsNode.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
                if (rootNode.isResourceType("social/journal/components/hbs/journal")) {
                    commentsNode.value(ContentTypeDefinitions.LABEL_JOURNAL);
                } else if (rootNode.isResourceType(Forum.RESOURCE_TYPE)) {
                    commentsNode.value(ContentTypeDefinitions.LABEL_FORUM);
                } else {
                    commentsNode.value(ContentTypeDefinitions.LABEL_COMMENTS);
                }
                commentsNode.key(ContentTypeDefinitions.LABEL_CONTENT);
                commentsNode.object();
                for (final com.adobe.cq.social.commons.Comment comment : comments) {
                    commentsNode.key(comment.getId());
                    final JSONWriter postObject = commentsNode.object();
                    UGCExportHelper.extractComment(postObject, comment, rootNode.getResourceResolver(), responseWriter, socialUtils);
                    commentsNode.endObject();
                }
                commentsNode.endObject();
                writer.endObject();
                responseWriter.flush();
                zip.closeEntry();
            } else {
                for (final Resource resource : rootNode.getChildren()) {
                    exportContent(resource, rootPath);
                }
            }
        } catch (final JSONException e) {
            throw new ServletException(e);
        }
    }

    protected void exportCommentSystems(final Map<String, Boolean> entries, final Map<String, Boolean> entriesToSkip,
                                        final Resource resource, final String path) throws IOException, JSONException {
//        for(final String commentSystemPath : entries.keySet()) {
//            if (!entriesToSkip.containsKey(commentSystemPath)) {
//                final String relPath = commentSystemPath.substring(commentSystemPath.indexOf(path) + path.length());
//                String entryName = relPath.isEmpty() ? ".root.json" : relPath + ".json";
//                responseWriter = new OutputStreamWriter(zip);
//                final JSONWriter writer = new JSONWriter(responseWriter);
//                writer.setTidy(true);
//                final Resource commentSystemResource = resource.getResourceResolver()
//                        .getResource(commentSystemPath);
//                if (commentSystemResource == null) {
//                    logger.error("Could not find comment parent resource: " + commentSystemPath + "; will not export its comments");
//                    continue;
//                }
//                final CommentSystem commentSystem = commentSystemResource.adaptTo(CommentSystem.class);
//                final Iterator<Comment> comments = commentSystem.getComments();
//                if (comments.hasNext()) {
//                    try {
//                        zip.putNextEntry(new ZipEntry(entryName));
//                        final JSONWriter commentsNode = writer.object();
//                        commentsNode.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
//                        commentsNode.value(ContentTypeDefinitions.LABEL_COMMENTS);
//                        commentsNode.key(ContentTypeDefinitions.LABEL_CONTENT);
//                        commentsNode.object();
//                        while (comments.hasNext()) {
//							final Comment comment = comments.next();
//                            if (null == comment) {
//                                continue;
//                            }
//							commentsNode.key(comment.getId());
//							final JSONWriter commentObject = commentsNode.object();
//							UGCExportHelper.extractComment(commentObject, comment,
//									resource.getResourceResolver(), responseWriter);
//							commentsNode.endObject();
//						}
//                        commentsNode.endObject();
//                        writer.endObject();
//                        responseWriter.flush();
//                        zip.closeEntry();
//                    } catch (Exception e) {
//                        logger.error("Cannot add zip entry: " + e);
//                    }
//                }
//            }
//        }
    }
}
