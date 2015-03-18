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

import javax.jcr.RepositoryException;
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

import com.adobe.cq.social.commons.Comment;
import com.adobe.cq.social.commons.CommentSystem;

@Component(label = "UGC Exporter for Comments Data",
        description = "Moves comments into json files for storage or re-import", specVersion = "1.0")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/comments/export")})
public class CommentsExportServlet extends SlingSafeMethodsServlet {


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
            exportContent(writer, resource);
        } catch (IOException e) {

        }
    }

    protected void exportContent(final JSONWriter writer, final Resource rootNode)
            throws IOException, ServletException {
        try {
            writer.object();
            writer.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
            writer.value(getContentType());
            writer.key(ContentTypeDefinitions.LABEL_CONTENT);
            writer.object();
            exportComments(writer, rootNode);
            writer.endObject();
            writer.endObject();
        } catch (final JSONException e) {
            throw new ServletException(e);
        } catch (final RepositoryException e) {
            throw new ServletException(e);
        }
    }
    
    protected void exportComments(final JSONWriter writer, final Resource rootNode) throws JSONException, RepositoryException, IOException {

        if (rootNode.isResourceType(CommentSystem.RESOURCE_TYPE)) {
            CommentSystem commentSystem = rootNode.adaptTo(CommentSystem.class);
            Iterator<Comment> comments = commentSystem.getComments();
            if (comments.hasNext()) {
                writer.key(rootNode.getPath());
                JSONWriter commentsList = writer.array();
                while (comments.hasNext()) {
                    UGCExportHelper.extractComment(commentsList.object(), comments.next(), rootNode.getResourceResolver(), responseWriter);
                    commentsList.endObject();
                }
                writer.endArray();
            }
        } else if (rootNode.isResourceType(Comment.RESOURCE_TYPE)) {
            writer.value(rootNode.getPath());
            UGCExportHelper.extractComment(writer.object(), rootNode.adaptTo(Comment.class), rootNode.getResourceResolver(), responseWriter);
            writer.endObject();
        } else {
            for (final Resource resource : rootNode.getChildren()) {
                exportComments(writer, resource);
            }
        }
    }

    protected String getContentType() {
        return ContentTypeDefinitions.LABEL_COMMENTS;
    }
}
