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

import com.adobe.communities.ugc.migration.util.Constants;
import com.adobe.cq.social.graph.Vertex;
import com.adobe.cq.social.graph.client.api.Following;
import com.adobe.cq.social.scf.SocialComponentFactory;
import com.adobe.cq.social.scf.SocialComponentFactoryManager;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.*;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

@Component(label = "UGC Migration Social Graph Importer",
        description = "Accepts a json file containing social graph data and applies it to stored profiles",
        specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/graph/content/import")})
public class SocialGraphImportServlet extends  UGCImport {

    private static final Logger LOG = LoggerFactory.getLogger(SocialGraphImportServlet.class);

    @Reference
    private ResourceResolverFactory rrf;

    @Reference
    private SocialComponentFactoryManager componentFactoryManager;

    private Map<String, Map<String,String>> keyValueMap = new HashMap() ;

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
        try {
            RequestParameterMap paramMap = request.getRequestParameterMap();
            RequestParameter metaFileParam = paramMap.getValue(Constants.ID_MAPPING_FILE);
            keyValueMap = loadKeyMetaInfo(metaFileParam);
        }catch(Exception e){
            logger.error("error occured while computing keyvalue map",e);
        }

        if (fileRequestParameters != null && fileRequestParameters.length > 0
                && !fileRequestParameters[0].isFormField()
                && fileRequestParameters[0].getFileName().endsWith(".json")) {
            final InputStream inputStream = fileRequestParameters[0].getInputStream();
            final JsonParser jsonParser = new JsonFactory().createParser(inputStream);
            JsonToken token = jsonParser.nextToken(); // get the first token
            if (token.equals(JsonToken.START_OBJECT)) {
                Long startTime = Calendar.getInstance().getTimeInMillis() ;
                importFile(jsonParser, request, relType, typeS);
                Long endTime = Calendar.getInstance().getTimeInMillis() ;
                logger.info("time taken = " + (endTime - startTime));
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
            User user = UGCImportHelper.getUser(userId, request.getResourceResolver());
            while (!token.equals(JsonToken.END_ARRAY)) {
                String followedId = jsonParser.getValueAsString() ;
                Map<String,String> valueMap = keyValueMap.get(jsonParser.getValueAsString()) ;
                if(valueMap != null && StringUtils.isNotBlank(valueMap.get(Constants.NEW_ID))){
                    followedId = valueMap.get(Constants.NEW_ID);
                    logger.info("using followerID = {} for oldFollowerId= {}" ,followedId,jsonParser.getValueAsString()) ;
                }
                final Map<String, Object> props = new HashMap<String, Object>();
                props.put("resourceType", Following.RESOURCE_TYPE);
                props.put("userId", userId);
                props.put("followedId", followedId);
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
}