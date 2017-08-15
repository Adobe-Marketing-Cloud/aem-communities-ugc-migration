package com.adobe.communities.ugc.migration.importer;

import com.adobe.granite.security.user.UserPropertiesManager;
import com.adobe.granite.security.user.UserPropertiesService;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.felix.scr.annotations.*;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.*;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import com.adobe.cq.social.scoring.api.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;


@SuppressWarnings("serial")
@SlingServlet(paths = "/services/social/scores/simple-import",
            methods = "POST",
            metatype = true)
@Properties({
    @Property(name = "service.vendor", value = "UGC Migration Profile Scores Migrate"),
    @Property(name = "service.description", value = "Accepts a json file containing profile scores and applies them to stored profiles")
})
public class ScoresSimpleImportServlet extends SlingAllMethodsServlet {
        
    private static final Logger log = LoggerFactory.getLogger(ScoresSimpleImportServlet.class);
    
    @Reference
    private ResourceResolverFactory resourceResolver;


    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY, policy = ReferencePolicy.STATIC)
    private UserPropertiesService userPropertiesService;

    @Reference
    private ScoringService scoringService;


    /**
    *The post operation accepts a json file, parses it and applies the profile scores to local profiles
    * @param request - the request
    * @param response - the response
    * @throws javax.servlet.ServletException
    * @throws java.io.IOException
    */
    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        ResourceResolver resolver = request.getResourceResolver();

        UGCImportHelper.checkUserPrivileges(resolver, resourceResolver);

        final RequestParameter[] fileRequestParameters = request.getRequestParameters("file");
        String resourcePath = request.getParameter("path"); //get the path passed via query parameter 'path'

        if (fileRequestParameters != null && fileRequestParameters.length > 0 
            && !fileRequestParameters[0].isFormField() 
            && fileRequestParameters[0].getFileName().endsWith(".json")) {
                
            final InputStream inputStream = fileRequestParameters[0].getInputStream();
            final JsonParser jsonParser = new JsonFactory().createParser(inputStream);
            JsonToken token = jsonParser.nextToken(); // get the first token
            
            if (token.equals(JsonToken.START_OBJECT)) {
                try {
                    final UserPropertiesManager userManager = userPropertiesService.createUserPropertiesManager(resolver);
                    readJsonUserScore(jsonParser, resolver, resourcePath);
                } catch (RepositoryException e) {
                    throw new ServletException("Unable to communicate with Jcr repository", e);
                } catch (NullPointerException ex) {
                    throw new ServletException("Please enter the path to the communities page in the url using query parameter 'path'");
                }
            } else {
                throw new ServletException("Expected a start object token, got " + token);
            }
        } else {
            log.info("Invalid file");
        }

    }


    private void readJsonUserScore(final JsonParser jsonParser, ResourceResolver resolver, String resourcePath)
            throws ServletException, IOException {

        JsonToken jsonToken = jsonParser.nextToken();

        Resource componentResource = resolver.getResource(resourcePath); // CRX node path to the communities site passed via the URL
        Resource scoreRuleResource = resolver.getResource(resourcePath + "/jcr:content"); // Child node to the communities page where scoringRules are applied
//        ValueMap valueMap = scoreRuleResource.adaptTo(ValueMap.class);
//        String scoringRule = valueMap.get("scoringRules", ""); //scoringRules property value being fetched

        while (!jsonToken.equals(JsonToken.END_OBJECT)) {
            String authId = jsonParser.getCurrentName();
            jsonToken = jsonParser.nextToken();
            Long score = jsonParser.getValueAsLong();

            try {
                scoringService.saveScore(resolver, authId, componentResource, scoreRuleResource, score);
                log.info("auth_id:" + authId + "& Score:" + score);
                log.info("Component Resource:" + componentResource);
                log.info("Score Rule Resource:" + scoreRuleResource);
//                log.info("Scoring Rule:" + scoringRule);

                jsonToken = jsonParser.nextToken();
            } catch (RepositoryException e) {
                e.printStackTrace();
            } catch (NullPointerException ex) {
                throw new ServletException("Please enter the path to the communities page in the url using query parameter 'path'");
            }
        }
    }

}
