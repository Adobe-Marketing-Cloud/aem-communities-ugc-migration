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

import com.adobe.cq.social.scoring.api.ScoringConstants;
import com.adobe.granite.security.user.UserProperties;
import com.adobe.granite.security.user.UserPropertiesManager;
import com.adobe.granite.security.user.UserPropertiesService;
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
        final Resource userRoot = request.getResourceResolver().getResource("/home/users");
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
                long score = getScoreForUser(resolver, child.getName());
                if (score > 0) {
                    writer.key(child.getName());
                    writer.value(score);
                }
            } else {
                exportScores(writer, child, resolver);
            }
        }
    }

    private long getScoreForUser(final ResourceResolver resolver, final String authId) throws ServletException {
        long result = 0L;
        try {
            final UserPropertiesManager userManager = userPropertiesService.createUserPropertiesManager(resolver);
            final UserProperties userProps = userManager.getUserProperties(authId, "profile");
            final Resource scoreResource = getScoreResource(resolver, userProps, ScoringConstants.SCORING_NODE);
            if (null == scoreResource) {
                return 0L;
            }
            final ValueMap props = ResourceUtil.getValueMap(scoreResource);
            result = props.get("communityScore", 0L);
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
