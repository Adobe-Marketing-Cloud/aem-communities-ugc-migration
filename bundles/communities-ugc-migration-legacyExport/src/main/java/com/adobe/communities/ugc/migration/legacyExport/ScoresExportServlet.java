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
package com.adobe.communities.ugc.migration.legacyExport;

import com.adobe.cq.social.scoring.api.ScoringConstants;
import com.adobe.granite.security.user.UserProperties;
import com.adobe.granite.security.user.UserPropertiesManager;
import com.adobe.granite.security.user.UserPropertiesService;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component(label = "Profile Scores Exporter",
        description = "Moves profile scores into a zip archive for storage or re-import", specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/scores/export")})
public class ScoresExportServlet extends SlingAllMethodsServlet {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY, policy = ReferencePolicy.STATIC)
    private UserPropertiesService userPropertiesService;


    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws ServletException, IOException {

        final JSONWriter writer = new JSONWriter(response.getWriter());
        writer.setTidy(true);
        final String path = StringUtils.stripEnd(request.getRequestParameter("path").getString(), "/");
        final Resource userRoot = request.getResourceResolver().getResource(path);
        if (null == userRoot) {
            throw new ServletException("Cannot locate a valid resource at " + path);
        }
        final ValueMap vm = userRoot.adaptTo(ValueMap.class);
        if (!vm.get("jcr:primaryType").equals("rep:AuthorizableFolder")) {
            throw new ServletException("Cannot locate a valid resource at " + path);
        }
        //iterate over child resources to get user nodes
        try {
            writer.object();
            exportScores(writer, userRoot, request.getResourceResolver());
            writer.endObject();
        } catch (final JSONException e) {
            throw new ServletException("Encountered a json exception while exporting scores", e);
        }
    }

    private void exportScores(final JSONWriter writer, final Resource resource, final ResourceResolver resolver)
            throws ServletException, JSONException {
        Iterable<Resource> children = resource.getChildren();
        for (final Resource child : children) {
            if (child.isResourceType("rep:User")) {
                Map<String, Long> scores = getScoreForUser(resolver, child.getName(), getScoreTypes(resolver));
                if (!scores.isEmpty()) {
                    writer.key(child.getName());
                    writer.object();
                    for (final String scoreType : scores.keySet()) {
                        writer.key(scoreType);
                        writer.value(scores.get(scoreType));
                    }
                    writer.endObject();
                }
            } else {
                exportScores(writer, child, resolver);
            }
        }
    }

    private Iterator<String> getScoreTypes(final ResourceResolver resolver) {
        List<String> scoreTypes = new ArrayList<String>();
        final Resource rootNode = resolver.getResource("/etc/segmentation/score");
        if (null == rootNode) {
            return null;
        }
        final Iterable<Resource> scoreNodes = rootNode.getChildren();
        for (final Resource scoreNode : scoreNodes) {
            ValueMap vm = scoreNode.adaptTo(ValueMap.class);
            if (vm.get("jcr:primaryType").equals("cq:Page")) {
                final Resource content = scoreNode.getChild("jcr:content");
                if (content.isResourceType("social/scoring/components/scoringpage")) {
                    final ValueMap valueMap = content.adaptTo(ValueMap.class);
                    if (valueMap.containsKey("sname")) {
                        final String[] scoreNames = (String[]) valueMap.get("sname");
                        scoreTypes.add(scoreNames[0]);
                    }
                }
            }
        }
        return scoreTypes.iterator();
    }

    private Map<String, Long> getScoreForUser(final ResourceResolver resolver, final String authId,
                                              final Iterator<String> scoreTypes) throws ServletException {
        Map<String, Long> result = new HashMap<String, Long>();
        try {
            final UserPropertiesManager userManager = userPropertiesService.createUserPropertiesManager(resolver);
            final UserProperties userProps = userManager.getUserProperties(authId, "profile");
            final Resource scoreResource = getScoreResource(resolver, userProps, ScoringConstants.SCORING_NODE);
            if (null == scoreResource) {
                return result;
            }
            final ValueMap props = ResourceUtil.getValueMap(scoreResource);
            while (scoreTypes.hasNext()) {
                final String scoreType = scoreTypes.next();
                if (props.containsKey(scoreType)) {
                    result.put(scoreType, props.get(scoreType, 0L));
                }
            }
        } catch (final RepositoryException e) {
            throw new ServletException("user [" + authId + "] doesn't exist: or unable to get score ", e);
        }
        return result;
    }

    private Resource getScoreResource(ResourceResolver resolver, final UserProperties user,
                                      final String resourcePath) throws RepositoryException {
        Resource resource = null;
        if (null != user) {
            final boolean hasScore = user.getNode().hasNode(resourcePath);
            if (hasScore) {
                resource = resolver.getResource(user.getNode().getNode(resourcePath).getPath());
            }
        }

        return resource;
    }
}
