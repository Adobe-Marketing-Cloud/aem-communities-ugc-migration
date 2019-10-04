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

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;

import com.adobe.communities.ugc.migration.util.Constants;
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
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/graph/msrp/import")})
public class SocialGraphImportServlet extends SlingAllMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(SocialGraphImportServlet.class);

    @Reference
    private ResourceResolverFactory rrf;

    @Reference
    private SocialComponentFactoryManager componentFactoryManager;

    private Map<String,String> keyValueMAp = new HashMap<String, String>() ;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

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
        final String relType =request.getRequestParameter("relType") !=null ? request.getRequestParameter("relType").toString():null;
        final String typeS =request.getRequestParameter("typeS") !=null ? request.getRequestParameter("typeS").toString():null;

        if(relType == null || typeS ==null ){
            LOG.error("Required parameters are not present. Exiting");
            throw new ServletException("Required parameters are not present. Exiting");
        }

        loadMap(request);

        if (fileRequestParameters != null && fileRequestParameters.length > 0
                && !fileRequestParameters[0].isFormField()
                && fileRequestParameters[0].getFileName().endsWith(".json")) {
            final InputStream inputStream = fileRequestParameters[0].getInputStream();
            final JsonParser jsonParser = new JsonFactory().createParser(inputStream);
            JsonToken token = jsonParser.nextToken(); // get the first token
            if (token.equals(JsonToken.START_OBJECT)) {
                importFile(jsonParser, request, relType, typeS);
            } else {
                throw new ServletException("Expected a start object token, got " + token);
            }
        } else {
            throw new ServletException("Expected to get a json file in post request");
        }
    }

    private void importFile(final JsonParser jsonParser, final SlingHttpServletRequest request, String relType, String typeS)
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
                String followedId = jsonParser.getValueAsString() ;
                if(keyValueMAp.get(jsonParser.getValueAsString()) != null){
                    followedId = keyValueMAp.get(jsonParser.getValueAsString());
                    logger.info("using followerID = {} for oldFollowerId= {}" ,followedId,jsonParser.getValueAsString()) ;
                }
                final Map<String, Object> props = new HashMap<String, Object>();
                props.put("resourceType", Following.RESOURCE_TYPE);
                props.put("userId", userId);
                props.put("followedId", followedId);
                User user = UGCImportHelper.getUser(userId, request.getResourceResolver());
                if (user != null) {
                    if(relType.equals("USER")){
                        User followedUser = UGCImportHelper.getUser(followedId, request.getResourceResolver());
                        if(followedUser == null){
                            LOG.warn("[Skipped] User {} following a user :{}  who does not exist: " , userId, followedId);
                            token = jsonParser.nextToken();
                            continue;
                        }
                    }
                    Resource resource;
                    resource = request.getResourceResolver().create(tmpParent, typeS, props);
                    final SocialComponentFactory factory =
                            componentFactoryManager.getSocialComponentFactory(Following.RESOURCE_TYPE);
                    final Following following = (Following) factory.getSocialComponent(resource, request);
                    request.getResourceResolver().delete(resource); // need to delete it so we can create it again next time
                    final Vertex node = following.userNode();
                    final Vertex other = following.followedNode();
                    try {
                        node.createRelationshipTo(other, typeS, relType);
                        following.socialGraph().save();
                    } catch (final IllegalArgumentException e) {
                        // The relationship already exists. Do nothing.
                    }
                } else {
                    LOG.warn("[Skipped] Attempted to import social graph for user that does not exist: " + userId);
                }
                token = jsonParser.nextToken();
            }
            token = jsonParser.nextToken(); // skip over END_ARRAY
        }
    }

    private void loadMap(final SlingHttpServletRequest request){
        try {
            keyValueMAp = new HashMap<String, String>() ;
            final RequestParameter[] keyValueFileRequestParameters = request.getRequestParameters(Constants.ID_MAPPING_FILE);

            if (keyValueFileRequestParameters != null && keyValueFileRequestParameters.length > 0) {

                final InputStream inputStream = keyValueFileRequestParameters[0].getInputStream();
                DataInputStream in = new DataInputStream(inputStream);
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String line = null;
                while ((line = br.readLine()) != null) {
                    String values[] = line.split("=");
                    keyValueMAp.put(values[0], values[1]);
                    logger.info("oldkey = {} newKey= {}" ,values[0],values[1]) ;
                }
            }
        }catch(Exception e){
            logger.error("excpetion occured while loading map",e);
        }
    }
}