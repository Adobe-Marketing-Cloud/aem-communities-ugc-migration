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
import com.adobe.cq.social.calendar.client.api.Calendar;
import com.adobe.cq.social.commons.CommentSystem;
import com.adobe.cq.social.commons.comments.api.Comment;
import com.adobe.cq.social.filelibrary.client.api.FileLibrary;
import com.adobe.cq.social.forum.client.api.Forum;
import com.adobe.cq.social.journal.client.api.Journal;
import com.adobe.cq.social.qna.client.api.QnaPost;
import com.adobe.cq.social.tally.client.api.RatingSocialComponent;
import com.adobe.cq.social.tally.client.api.VotingSocialComponent;
import com.adobe.cq.social.ugcbase.SocialUtils;
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
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
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
        try {
            if (rootNode.isResourceType(VotingSocialComponent.VOTING_RESOURCE_TYPE)) {
                if (rootNode.hasChildren()) {
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
                }
            } else
            if (rootNode.isResourceType(RatingSocialComponent.RATING_RESOURCE_TYPE)) {
                if (rootNode.hasChildren()) {
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
                }
            } else
            if (rootNode.isResourceType(Calendar.RESOURCE_TYPE)) {
                final CommentSystem commentSystem = rootNode.adaptTo(CommentSystem.class);
                int commentSize = commentSystem.countComments();
                if (commentSize == 0) {
                    return;
                }
                List<com.adobe.cq.social.commons.Comment> comments = commentSystem.getComments(0, commentSize);
                zip.putNextEntry(new ZipEntry(entryName));
                final JSONWriter calendarNode = writer.object();
                calendarNode.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
                calendarNode.value(ContentTypeDefinitions.LABEL_CALENDAR);
                calendarNode.key(ContentTypeDefinitions.LABEL_CONTENT);
                JSONWriter eventObjects = calendarNode.array();
                for (final com.adobe.cq.social.commons.Comment comment : comments) {
                    final Resource eventResource = comment.getResource();
                    UGCExportHelper.extractEvent(eventObjects.object(), eventResource, rootNode.getResourceResolver(),
                            responseWriter, socialUtils);
                    eventObjects.endObject();
                }
                calendarNode.endArray();
                writer.endObject();
                responseWriter.flush();
                zip.closeEntry();
            } else
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
                if (rootNode.isResourceType(Journal.RESOURCE_TYPE)) {
                    commentsNode.value(ContentTypeDefinitions.LABEL_JOURNAL);
                } else if (rootNode.isResourceType(QnaPost.RESOURCE_TYPE)) {
                    commentsNode.value(ContentTypeDefinitions.LABEL_QNA_FORUM);
                } else if (rootNode.isResourceType(Forum.RESOURCE_TYPE)) {
                    commentsNode.value(ContentTypeDefinitions.LABEL_FORUM);
                } else if (rootNode.isResourceType(FileLibrary.RESOURCE_TYPE_FILELIBRARY)) {
                    commentsNode.value(ContentTypeDefinitions.LABEL_FILELIBRARY);
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
}
