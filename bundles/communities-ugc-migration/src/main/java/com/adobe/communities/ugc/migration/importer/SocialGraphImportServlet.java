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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

import com.adobe.cq.social.graph.Edge;
import com.adobe.cq.social.graph.Vertex;
import com.adobe.cq.social.graph.client.api.Following;
import com.adobe.cq.social.scf.SocialComponentFactory;
import com.adobe.cq.social.scf.SocialComponentFactoryManager;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(label = "UGC Migration Social Graph Importer",
        description = "Accepts a json file containing social graph data and applies it to stored profiles",
        specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/graph/import")})
public class SocialGraphImportServlet extends SlingAllMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(SocialGraphImportServlet.class);

    @Reference
    private ResourceResolverFactory rrf;

    @Reference
    private SocialComponentFactoryManager componentFactoryManager;

    /**
     * The post operation accepts a json file, parses it and applies the social graph relationships to local profiles
     * @param request - the request
     * @param response - the response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws ServletException, IOException {

        final ResourceResolver resolver = request.getResourceResolver();

        UGCImportHelper.checkUserPrivileges(resolver, rrf);

        final RequestParameter[] fileRequestParameters = request.getRequestParameters("file");
        if (fileRequestParameters != null && fileRequestParameters.length > 0
                && !fileRequestParameters[0].isFormField()
                && fileRequestParameters[0].getFileName().endsWith(".json")) {
            final InputStream inputStream = fileRequestParameters[0].getInputStream();
            final JsonParser jsonParser = new JsonFactory().createParser(inputStream);
            JsonToken token = jsonParser.nextToken(); // get the first token
            if (token.equals(JsonToken.START_OBJECT)) {
                importFile(jsonParser, request);
            } else {
                throw new ServletException("Expected a start object token, got " + token);
            }
        } else {
            throw new ServletException("Expected to get a json file in post request");
        }
    }

    private void importFile(final JsonParser jsonParser, final SlingHttpServletRequest request)
        throws ServletException, IOException {

        JsonToken token = jsonParser.nextToken();
        while (!token.equals(JsonToken.END_OBJECT)) {
            if (!token.equals(JsonToken.FIELD_NAME)) {
                throw new ServletException("Expected a field name, got " + token);
            }
            final String userId = jsonParser.getCurrentName();
            token = jsonParser.nextToken();
            if (!token.equals(JsonToken.START_ARRAY)) {
                throw new ServletException("Expected an array start token, got " + token);
            }
            token = jsonParser.nextToken();
            final Resource tmpParent = request.getResourceResolver().getResource("/tmp");
            while (!token.equals(JsonToken.END_ARRAY)) {
                final Map<String, Object> props = new HashMap<String, Object>();
                props.put("resourceType", Following.RESOURCE_TYPE);
                props.put("userId", userId);
                props.put("followedId", jsonParser.getValueAsString());

                User user = UGCImportHelper.getUser(userId, request.getResourceResolver());
                if (user != null) {
                    Resource resource;
                    resource = request.getResourceResolver().create(tmpParent, "following", props);
                    final SocialComponentFactory factory =
                        componentFactoryManager.getSocialComponentFactory(Following.RESOURCE_TYPE);
                    final Following following = (Following) factory.getSocialComponent(resource, request);
                    request.getResourceResolver().delete(resource); // need to delete it so we can create it again next time
                    final Vertex node = following.userNode();
                    final Vertex other = following.followedNode();
                    final String relType = "USER";
                    try {
                        node.createRelationshipTo(other, Edge.FOLLOWING_RELATIONSHIP_TYPE, relType);
                        following.socialGraph().save();
                    } catch (final IllegalArgumentException e) {
                        // The relationship already exists. Do nothing.
                    }
                } else {
                    LOG.warn("Attempted to import social graph for user that does not exist: " + userId);
                }
                token = jsonParser.nextToken();
            }
            token = jsonParser.nextToken(); // skip over END_ARRAY
        }
    }
}
