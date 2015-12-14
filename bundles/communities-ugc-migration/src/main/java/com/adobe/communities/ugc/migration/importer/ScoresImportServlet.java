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

import com.adobe.cq.social.scoring.api.ScoreOperation;
import com.adobe.cq.social.srp.SocialResourceProvider;
import com.adobe.cq.social.ugcbase.SocialUtils;
import com.adobe.granite.security.user.UserProperties;
import com.adobe.granite.security.user.UserPropertiesManager;
import com.adobe.granite.security.user.UserPropertiesService;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(label = "UGC Migration Profile Scores Importer",
        description = "Accepts a json file containing profile scores and applies them to stored profiles",
        specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/scores/import")})
public class ScoresImportServlet extends SlingAllMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ScoresImportServlet.class);

    @Reference
    private ResourceResolverFactory rrf;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY, policy = ReferencePolicy.STATIC)
    private UserPropertiesService userPropertiesService;
    /**
     * The post operation accepts a json file, parses it and applies the profile scores to local profiles
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
                try {
                    final UserPropertiesManager userManager = userPropertiesService.createUserPropertiesManager(resolver);
                    importFile(jsonParser, userManager, resolver);
                } catch (RepositoryException e) {
                    throw new ServletException("Unable to communicate with Jcr repository", e);
                } catch (final Exception e) {
                    throw new ServletException("Problem!", e);
                }
            } else {
                throw new ServletException("Expected a start object token, got " + token);
            }
        } else {
            throw new ServletException("Expected to get a json file in post request");
        }
    }

    private Map<String, Boolean> getScoreTypes(final ResourceResolver resolver) {
        Map<String, Boolean> scoreTypes = new HashMap<String, Boolean>();
        final Resource rootNode = resolver.getResource("/etc/segmentation/score");
        if (null == rootNode) {
            return null;
        }
        final Iterable<Resource> scoreNodes = rootNode.getChildren();
        for (final Resource scoreNode : scoreNodes) {
            ValueMap vm = scoreNode.adaptTo(ValueMap.class);
            final Resource content = scoreNode.getChild("jcr:content");
            if (content.isResourceType("social/scoring/components/scoringpage")) {
                final ValueMap valueMap = content.adaptTo(ValueMap.class);
                if (valueMap.containsKey("sname")) {
                    final String[] scoreNames = (String[]) valueMap.get("sname");
                    scoreTypes.put(scoreNames[0], true);
                }
            }
        }
        return scoreTypes;
    }

    private void importFile(final JsonParser jsonParser, final UserPropertiesManager userManager,
                            final ResourceResolver resolver) throws ServletException, IOException, RepositoryException {
        Map<String, Boolean> scoreTypes = getScoreTypes(resolver);
        JsonToken token = jsonParser.nextToken();

        while (!token.equals(JsonToken.END_OBJECT)) {
            final String authId = jsonParser.getCurrentName();
            token = jsonParser.nextToken();
            if (!token.equals(JsonToken.START_OBJECT)) {
                throw new ServletException("Expected to see start object, got " + token);
            }
            final Map<String, Long> scores = new HashMap<String, Long>();
            token = jsonParser.nextToken();
            while (!token.equals(JsonToken.END_OBJECT)) {
                final String scoreName = jsonParser.getCurrentName();
                jsonParser.nextToken();
                final Long scoreValue = jsonParser.getLongValue();
                scores.put(scoreName, scoreValue);
                if (!scoreTypes.containsKey(scoreName)) {
                    LOG.warn("A score of type [{}] was imported for [{}], but that score type hasn't been configured " +
                                    "on this server", scoreName, authId);
                }
                token = jsonParser.nextToken();
            }
            updateProfileScore(authId, scores, userManager, resolver);
            token = jsonParser.nextToken();
        }
    }

    private void updateProfileScore(final String authId, final Map<String, Long> scores,
                                    final UserPropertiesManager userManager, final ResourceResolver resolver)
                                    throws RepositoryException {
        List<ScoreOperation> scoreOperations = new ArrayList<ScoreOperation>();
        for (final Map.Entry<String, Long> entry : scores.entrySet()) {
            ScoreOperation scoreOperation = new ScoreOperation();
            scoreOperation.setScoreName(entry.getKey());
            scoreOperation.setAbsolute(true);
            scoreOperation.setScoreValue(entry.getValue());
            scoreOperations.add(scoreOperation);
        }
        setPoints(authId, scoreOperations, resolver, userManager);
    }

    public void setPoints(final String userId, final List<ScoreOperation> scoreOperations,
                                 final ResourceResolver resourceResolver, final UserPropertiesManager userManager)
            throws RepositoryException {
        UserProperties userProps = userManager.getUserProperties(userId, "profile");

        final Resource scoreResource = getScoreResource(resourceResolver, userProps);
        if (null != scoreResource) {
            final SocialResourceProvider socialResourceProvider = getSocialResourceProvider(scoreResource);

            for (final ScoreOperation operation : scoreOperations) {
                try {
                    ModifiableValueMap modifiableValueMap = scoreResource.adaptTo(ModifiableValueMap.class);
                    modifiableValueMap
                            .put(
                                    operation.getScoreName(), operation.getScoreValue());
                    socialResourceProvider.commit(resourceResolver);
                } catch (final PersistenceException e) {
                    LOG.error("Failed to execute scoring operation {} on {}", new Object[]{operation.getScoreName(),
                            scoreResource.getPath()}, e);
                }

            }
            try {
                socialResourceProvider.commit(resourceResolver);
            } catch (final PersistenceException e) {
                LOG.error("Failed to commit score operations", e);
            }
        } else {
            LOG.debug("could not update score, profile/score unavailable.");
        }

    }

    private SocialResourceProvider getSocialResourceProvider(final Resource resource) {
        SocialResourceProvider socialResourceProvider = null;
        final SocialUtils socialUtils = resource.getResourceResolver().adaptTo(SocialUtils.class);
        if (socialUtils != null) {
            socialResourceProvider = socialUtils.getConfiguredProvider(resource);
        }
        return socialResourceProvider;
    }

    private Resource getScoreResource(final ResourceResolver resolver, UserProperties user) throws RepositoryException {

        Resource resource = null;
        if (null != user) {
            final SocialUtils socialUtils = resolver.adaptTo(SocialUtils.class);
            final Resource userResource = user.getResource("");
            if (userResource != null) {
                final SocialResourceProvider socialResourceProvider = getSocialResourceProvider(userResource);
                if (socialUtils != null) {
                    final String path = socialUtils.resourceToUGCStoragePath(userResource) + "/scoring";
                    resource = resolver.getResource(path);
                    LOG.trace("Getting scoring resource: {}", path);

                    if (resource == null) {
                        resource = createScoringResource(resolver, socialResourceProvider, path, "socialHolder");
                    }
                }
            }
        }

        return resource;
    }

    private static Resource createScoringResource(final ResourceResolver resolver, final SocialResourceProvider srp,
                                                  final String path, final String type) {
        Resource resource = null;
        try {
            LOG.trace("Creating scoring resource: {}", path);
            Map<String, Object> props = new HashMap<String, Object>();
            props.put(SlingConstants.NAMESPACE_PREFIX + ":" + SlingConstants.PROPERTY_RESOURCE_TYPE, type);
            srp.create(resolver, path, props);
            srp.commit(resolver);
            resource = resolver.getResource(path);
        } catch (final PersistenceException e) {
            LOG.error("Failed to create scoring nodes for {}", new Object[]{path}, e);
        }
        return resource;
    }

}
