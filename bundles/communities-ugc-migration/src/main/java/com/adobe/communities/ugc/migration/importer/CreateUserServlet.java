package com.adobe.communities.ugc.migration.importer;

import com.adobe.cq.social.scf.OperationException;
import com.adobe.cq.social.serviceusers.internal.ServiceUserWrapper;
import com.adobe.cq.social.user.endpoints.CommunityUserOperations;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.*;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component(label = "Create User Servlet",
        description = "Creates community users from json", specVersion = "1.1",
        immediate = true)
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/create-users")})
public class CreateUserServlet extends SlingAllMethodsServlet {

    private static final Logger log = LoggerFactory.getLogger(CreateUserServlet.class);

    final static String UGC_WRITER = "ugc-writer";

    @Reference
    private CommunityUserOperations userOperations;

    @Reference
    protected ResourceResolverFactory rrf;

    @Reference
    protected ServiceUserWrapper serviceUserWrapper;

    @Override
    protected void doPost(@Nonnull SlingHttpServletRequest request, @Nonnull SlingHttpServletResponse response) throws ServletException, IOException {
        ResourceResolver resolver = request.getResourceResolver();

        boolean createProfileNode = false;

        try {
            userOperations.create(request);
        } catch (OperationException e) {
            log.error("Error when creating user.", e);

            int errorCode = e.getErrorCode();
            if (errorCode == 409) {
                createProfileNode = true;
            }
        }

        // Check if we need to create a profile node. We may need to do this if a user already exists
        if (createProfileNode) {
            Map<String, Object> requestParams = getRequestParams(request.getRequestParameterMap());

            String email = (String) requestParams.get("email");
            String userId = (String) requestParams.get("user_id");

            // If user_id not provided, use email
            if (StringUtils.isEmpty(userId) && !StringUtils.isEmpty(email)) {
                userId = email;
            }

            if (!StringUtils.isEmpty(userId)) {
                try {
                    addProfileNode(resolver, userId);
                } catch (RepositoryException e) {
                    throw new ServletException("Couldn't create user", e);
                }
            }
        }
    }

    private void addProfileNode(ResourceResolver resolver, String userId) throws RepositoryException, PersistenceException {
        UserManager userManager = resolver.adaptTo(UserManager.class);
        Authorizable user = userManager.getAuthorizable(userId);
        if (user != null) {
            String userPath = user.getPath();
            Resource resource = resolver.getResource(userPath);
            Resource profile = resource.getChild("profile");
            if (profile == null) {
                Node node = resource.adaptTo(Node.class);
                node.addNode("profile", "nt:unstructured");
                resolver.commit();
            }
        }
    }

    private Map<String, Object> getRequestParams(RequestParameterMap params) {
        Map<String, Object> requestParams = new HashMap();
        for (String key : params.keySet()) {
            requestParams.put(key, params.getValue(key).getString());
        }

        return requestParams;
    }
}
