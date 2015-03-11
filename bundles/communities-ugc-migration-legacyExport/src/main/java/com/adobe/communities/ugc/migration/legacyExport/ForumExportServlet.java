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
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;

//import com.adobe.communities.ugc.migration.legacyExport.ContentTypeDefinitions;
//import com.adobe.communities.ugc.migration.legacyExport.UGCExportHelper;
import com.adobe.cq.social.tally.Response;
import com.adobe.cq.social.tally.Vote;
import com.adobe.cq.social.tally.Voting;
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
                extractEntry(postObject, post, resolver, true);
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

    protected JSONObject extractEntry(final JSONWriter writer, final Post post, final ResourceResolver resolver, final Boolean isTopic) throws JSONException {

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
            } else if (value instanceof GregorianCalendar) {
                timestampFields.put(prop.getKey());
                writer.key(prop.getKey());
                writer.value(((Calendar) value).getTimeInMillis());
            } else if (prop.getKey().equals("sling:resourceType")) {
                writer.key(prop.getKey());
                if (isTopic) {
                    writer.value("social/qna/components/hbs/topic");
                } else {
                    writer.value("social/qna/components/hbs/post");
                }
            } else {
                writer.key(prop.getKey());
                try {
                    writer.value(URLEncoder.encode(prop.getValue().toString(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new JSONException("Unsupported encoding - UTF-8", e);
                }
            }
        }
        if (timestampFields.length() > 0) {
            writer.key(ContentTypeDefinitions.LABEL_TIMESTAMP_FIELDS);
            writer.value(timestampFields);
        }
        final Resource thisResource = resolver.getResource(post.getPath());
        final Resource attachments = thisResource.getChild("attachments");
        if (attachments != null) {
            writer.key(ContentTypeDefinitions.LABEL_ATTACHMENTS);
            final JSONWriter attachmentsWriter = writer.array();
            for (final Resource attachment : attachments.getChildren()) {
                UGCExportHelper.extractAttachment(responseWriter, attachmentsWriter.object(), attachment);
                attachmentsWriter.endObject();
            }
            writer.endArray();
        }
        final Resource voteResource = thisResource.getChild("votes");
        if (voteResource != null) {
            writer.key(ContentTypeDefinitions.LABEL_TALLY);
            final JSONWriter voteObjects = writer.array();
            UGCExportHelper.extractTally(voteObjects, voteResource, "Voting");
            writer.endArray();
        }
        final Iterable<Resource> childNodes = thisResource.getChildren();
        if (childNodes != null) {
            writer.key(ContentTypeDefinitions.LABEL_SUBNODES);
            final JSONWriter object = writer.object();
            for (final Resource subNode : childNodes) {
                final String nodeName = subNode.getName();
                if (nodeName.matches("^[0-9]+" + Post.POST_POSTFIX + "$")) {
                    continue; //this is a folder of replies, which will be picked up lower down
                }
                if (nodeName.equals("attachments") || nodeName.equals("votes")) {
                    continue; //already handled attachments and votes up above
                }
                object.key(nodeName);
                UGCExportHelper.extractSubNode(object.object(), subNode);
                object.endObject();
            }
            writer.endObject();
        }
        final Iterator<Post> posts = post.getPosts();
        if (posts.hasNext()) {
            writer.key(ContentTypeDefinitions.LABEL_REPLIES);
            final JSONWriter replyWriter = writer.object();
            while (posts.hasNext()) {
                Post childPost = posts.next();
                replyWriter.key(childPost.getId());
                extractEntry(replyWriter.object(), childPost, resolver, false);
                replyWriter.endObject();
            }
            writer.endObject();
        }
        return returnValue;
    }

    protected String getContentType() {
        return ContentTypeDefinitions.LABEL_FORUM;
    }
}
