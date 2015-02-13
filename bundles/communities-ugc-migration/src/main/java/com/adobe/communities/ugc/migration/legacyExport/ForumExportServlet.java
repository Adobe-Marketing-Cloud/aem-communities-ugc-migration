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
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;

import com.adobe.communities.ugc.migration.UGCExportHelper;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import com.adobe.cq.social.forum.api.Forum;
import com.adobe.cq.social.forum.api.Post;

@Component(label = "UGC Exporter for Forum Data",
        description = "Moves forum and qna data into json files for storage or re-import", specVersion = "1.0")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/forum/export")})
public class ForumExportServlet extends SlingSafeMethodsServlet {

    final static String LABEL_REPLIES = UGCExportHelper.NAMESPACE_PREFIX + "replies";
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
            writer.key(UGCExportHelper.LABEL_CONTENT_TYPE);
            writer.value(getContentType());
            writer.key(UGCExportHelper.LABEL_CONTENT);
            writer.array();
            final List<Post> items = forum.getTopics(0, forum.getTopicCount(), true);
            for (Post post : items) {
                JSONWriter object = writer.object();
                object.key(post.getId());
                JSONWriter postObject = object.object();
                extractEntry(postObject, post, resolver);
                postObject.endObject();
                object.endObject();
            }
            writer.endArray();
            writer.endObject();
        } catch (final JSONException e) {
            throw new ServletException(e);
        } catch (final RepositoryException e) {
            throw new ServletException(e);
        }
    }

    protected JSONObject extractEntry(final JSONWriter writer, final Post post, final ResourceResolver resolver) throws JSONException {

        final JSONObject returnValue = new JSONObject();
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
//                returnValue.put(prop.getKey(), list);
            } else if (value instanceof GregorianCalendar) {
                timestampFields.put(prop.getKey());
                writer.key(prop.getKey());
                writer.value(((Calendar) value).getTimeInMillis());
//                returnValue.put(prop.getKey(), ((Calendar) value).getTimeInMillis());
            } else {
                writer.key(prop.getKey());
                writer.value(prop.getValue());
//                returnValue.put(prop.getKey(), prop.getValue());
            }
        }
        if (timestampFields.length() > 0) {
            writer.key(UGCExportHelper.LABEL_TIMESTAMP_FIELDS);
            writer.value(timestampFields);
//            returnValue.put(UGCExportHelper.LABEL_TIMESTAMP_FIELDS, timestampFields);
        }
        final Resource thisResource = resolver.getResource(post.getPath());
//        final JSONObject subNodes = new JSONObject();
        if (thisResource.hasChildren()) {
            writer.key(UGCExportHelper.LABEL_SUBNODES);
            final JSONWriter object = writer.object();
            for (final Resource subNode : thisResource.getChildren()) {
                final String nodeName = subNode.getName();
                if (nodeName.matches("^[0-9]+" + Post.POST_POSTFIX + "$")) {
                    continue; //this is a folder of replies, which will be picked up lower down
                }
                if (nodeName.equals("attachments")) {
                    continue; //handle attachments separately
                }
                object.key(nodeName);
                UGCExportHelper.extractSubNode(object.object(), subNode);
                object.endObject();
            }
            writer.endObject();
        }
        final Resource attachments = thisResource.getChild("attachments");
        if (attachments != null) {
            writer.key(UGCExportHelper.LABEL_ATTACHMENTS);
            final JSONWriter attachmentsWriter = writer.array();
            for (final Resource attachment : attachments.getChildren()) {
                UGCExportHelper.extractAttachment(responseWriter, attachmentsWriter.object(), attachment);
                attachmentsWriter.endObject();
            }
            writer.endArray();
        }
        final Iterator<Post> posts = post.getPosts();
        if (posts.hasNext()) {
            writer.key(LABEL_REPLIES);
            final JSONWriter replyWriter = writer.array();
            while (posts.hasNext()) {
                extractEntry(replyWriter.object(), posts.next(), resolver);
                replyWriter.endObject();
            }
            writer.endArray();
        }
        return returnValue;
    }

    protected String getContentType() {
        return UGCExportHelper.LABEL_FORUM;
    }
}
