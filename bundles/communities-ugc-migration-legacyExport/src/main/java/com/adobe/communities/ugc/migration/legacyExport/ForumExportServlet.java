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
import java.util.List;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;

import com.adobe.cq.social.forum.api.Forum;
import com.adobe.cq.social.forum.api.Post;

@Component(label = "UGC Exporter for Forum Data",
        description = "Moves forum and qna data into json files for storage or re-import", specVersion = "1.0")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/forum/export")})
public class ForumExportServlet extends SlingSafeMethodsServlet {

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
        final Forum forum = resource.adaptTo(Forum.class);
        if (forum == null) {
            throw new ServletException("Provided path to resource was not a forum");
        }
        exportContent(writer, forum, request.getResourceResolver());
    }

    protected void exportContent(final JSONWriter writer, final Forum forum, final ResourceResolver resolver)
        throws ServletException, IOException {
        try {
            writer.object();
            writer.key(ContentTypeDefinitions.LABEL_CONTENT_TYPE);
            writer.value(getContentType());
            writer.key(ContentTypeDefinitions.LABEL_CONTENT);
            writer.object();
            final List<Post> items = forum.getTopics(0, forum.getTopicCount(), true);
            for (Post post : items) {
                writer.key(post.getId());
                JSONWriter postObject = writer.object();
                UGCExportHelper.extractTopic(postObject, post, resolver, "social/forum/components/hbs/topic",
                    "social/forum/components/hbs/post", responseWriter);
                postObject.endObject();
            }
            writer.endObject();
            writer.endObject();
        } catch (final JSONException e) {
            throw new ServletException(e);
        } catch (final RepositoryException e) {
            throw new ServletException(e);
        }
    }

    protected String getContentType() {
        return ContentTypeDefinitions.LABEL_FORUM;
    }
}
