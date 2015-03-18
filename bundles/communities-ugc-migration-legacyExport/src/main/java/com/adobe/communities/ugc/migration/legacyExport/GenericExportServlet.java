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
import java.io.Writer;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;

import com.adobe.cq.social.calendar.CalendarConstants;
import com.adobe.cq.social.calendar.CqCalendar;
import com.adobe.cq.social.calendar.Event;
import com.adobe.cq.social.commons.Comment;
import com.adobe.cq.social.commons.CommentSystem;
import com.adobe.cq.social.forum.api.Forum;
import com.adobe.cq.social.forum.api.Post;
import com.adobe.cq.social.journal.Journal;
import com.adobe.cq.social.journal.JournalEntry;

//import com.adobe.cq.social.qna.api.QnaPost;

@Component(label = "UGC Exporter for all UGC Data Types",
        description = "Moves any ugc data into json files for storage or re-import", specVersion = "1.0")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/ugc/export")})

public class GenericExportServlet extends SlingSafeMethodsServlet {

    Writer responseWriter;
    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws ServletException, IOException {

        responseWriter = response.getWriter();
        final JSONWriter writer = new JSONWriter(responseWriter);

        final String path = request.getRequestParameter("path").getString();

        final Resource resource = request.getResourceResolver().getResource(path);
        if (resource == null) {
            throw new ServletException("Could not find a valid resource for export");
        }
        try {
            writer.object();
            exportContent(writer, resource, path);
            writer.endObject();
        } catch (final JSONException e) {
            throw new ServletException(e);
        }
    }

    protected void exportContent(final JSONWriter writer, final Resource rootNode, final String rootPath)
            throws IOException, ServletException {

        final String relPath = rootNode.getPath().substring(rootNode.getPath().indexOf(rootPath)+rootPath.length());
        try {
            if (rootNode.isResourceType("social/qna/components/qnaforum")) {
                final Forum forum = rootNode.adaptTo(Forum.class);
                final Iterator<Post> posts = forum.getTopics();
                if (posts.hasNext()) {
                    writer.key(relPath);
                    final JSONWriter forumObject = writer.object();
                    forumObject.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
                    forumObject.value(ContentTypeDefinitions.LABEL_QNA_FORUM);
                    forumObject.key(ContentTypeDefinitions.LABEL_CONTENT);
                    forumObject.object();
                    while (posts.hasNext()) {
                        final Post post = posts.next();
                        forumObject.key(post.getId());
                        final JSONWriter postObject = forumObject.object();
                        UGCExportHelper.extractTopic(postObject, post, rootNode.getResourceResolver(),
                                "social/qna/components/hbs/topic", "social/qna/components/hbs/post", responseWriter);
                        forumObject.endObject();
                    }
                    forumObject.endObject();
                    writer.endObject();
                }
            } else if (rootNode.isResourceType(Forum.RESOURCE_TYPE)) {

                final Forum forum = rootNode.adaptTo(Forum.class);
                final Iterator<Post> posts = forum.getTopics();
                if (posts.hasNext()) {
                    writer.key(relPath);
                    final JSONWriter forumObject = writer.object();
                    forumObject.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
                    forumObject.value(ContentTypeDefinitions.LABEL_FORUM);
                    forumObject.key(ContentTypeDefinitions.LABEL_CONTENT);
                    forumObject.object();
                    while (posts.hasNext()) {
                        final Post post = posts.next();
                        forumObject.key(post.getId());
                        final JSONWriter postObject = forumObject.object();
                        UGCExportHelper.extractTopic(postObject, post, rootNode.getResourceResolver(),
                                "social/qna/components/hbs/topic", "social/qna/components/hbs/post", responseWriter);
                        forumObject.endObject();
                    }
                    forumObject.endObject();
                    writer.endObject();
                }
            } else if (rootNode.isResourceType(CommentSystem.RESOURCE_TYPE)) {
                if (rootNode.getName().equals("entrycomments")) {
                    return; //these are special cases of comments that will be handled by journal instead
                }
                final CommentSystem commentSystem = rootNode.adaptTo(CommentSystem.class);
                final Iterator<Comment> comments = commentSystem.getComments();
                if (comments.hasNext()) {
                    writer.key(relPath);
                    final JSONWriter commentsNode = writer.object();
                    commentsNode.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
                    commentsNode.value(ContentTypeDefinitions.LABEL_COMMENTS);
                    commentsNode.key(ContentTypeDefinitions.LABEL_CONTENT);
                    commentsNode.object();
                    while (comments.hasNext()) {
                        final Comment comment = comments.next();
                        commentsNode.key(comment.getId());
                        final JSONWriter commentObject = commentsNode.object();
                        UGCExportHelper.extractComment(commentObject, comment, rootNode.getResourceResolver(), responseWriter);
                        commentsNode.endObject();
                    }
                    commentsNode.endObject();
                    writer.endObject();
                }
            } else if (rootNode.isResourceType(CalendarConstants.RT_CALENDAR_COMPONENT)) { //"social/calendar/components/calendar"
                final CqCalendar calendar = rootNode.adaptTo(CqCalendar.class);
                writer.key(relPath);
                final JSONWriter calendarNode = writer.object();
                calendarNode.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
                calendarNode.value(ContentTypeDefinitions.LABEL_CALENDAR);
                calendarNode.key(ContentTypeDefinitions.LABEL_CONTENT);
                JSONWriter eventObjects = calendarNode.object();
                final Iterator<Event> events = calendar.getEvents();
                while (events.hasNext()) {
                    final Event event = events.next();
                    eventObjects.key(event.getUid());
                    UGCExportHelper.extractCalendarEvent(eventObjects.object(), event);
                    eventObjects.endObject();
                }
                calendarNode.endObject();
                writer.endObject();
            } else if (rootNode.isResourceType("social/tally/components/poll")) { // No constant defined in 5.6.1
                writer.key(relPath);
                final JSONWriter tallyNode = writer.object();
                tallyNode.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
                tallyNode.value(ContentTypeDefinitions.LABEL_TALLY);
                tallyNode.key(ContentTypeDefinitions.LABEL_CONTENT);
                final JSONWriter responseArray = tallyNode.array();
                UGCExportHelper.extractTally(responseArray, rootNode, "Poll");
                tallyNode.endArray();
                writer.endObject();
            } else if (rootNode.isResourceType("social/tally/components/voting")) { // No constant defined in 5.6.1
                writer.key(relPath);
                final JSONWriter tallyNode = writer.object();
                tallyNode.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
                tallyNode.value(ContentTypeDefinitions.LABEL_TALLY);
                tallyNode.key(ContentTypeDefinitions.LABEL_CONTENT);
                final JSONWriter responseArray = tallyNode.array();
                UGCExportHelper.extractTally(responseArray, rootNode, "Voting");
                tallyNode.endArray();
                writer.endObject();
            } else if (rootNode.isResourceType("social/tally/components/rating")) { // No constant defined in 5.6.1
                writer.key(relPath);
                final JSONWriter tallyNode = writer.object();
                tallyNode.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
                tallyNode.value(ContentTypeDefinitions.LABEL_TALLY);
                tallyNode.key(ContentTypeDefinitions.LABEL_CONTENT);
                final JSONWriter responseArray = tallyNode.array();
                UGCExportHelper.extractTally(responseArray, rootNode, "Rating");
                tallyNode.endArray();
                writer.endObject();
            } else if (rootNode.isResourceType("social/journal/components/entrylist")) {
                final Journal journal = rootNode.adaptTo(Journal.class);
                writer.key(relPath);
                final JSONWriter journalNode = writer.object();
                journalNode.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
                journalNode.value(ContentTypeDefinitions.LABEL_JOURNAL);
                journalNode.key(ContentTypeDefinitions.LABEL_CONTENT);
                JSONWriter entryObjects = journalNode.array();
                final List<JournalEntry> entries = journal.getEntries();
                for (final JournalEntry entry : entries) {
                    UGCExportHelper.extractJournalEntry(entryObjects.object(), entry, responseWriter);
                    entryObjects.endObject();
                }
                journalNode.endArray();
                writer.endObject();
            } else {
                for (final Resource resource : rootNode.getChildren()) {
                    exportContent(writer, resource, rootPath);
                }
            }
        } catch (final JSONException e) {
            throw new ServletException(e);
        }
    }

}
