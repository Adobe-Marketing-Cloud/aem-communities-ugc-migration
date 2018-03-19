/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * __________________
 *
 *  Copyright 2016 Adobe Systems Incorporated
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
 *
 *************************************************************************/
package com.adobe.communities.ugc.management.servlets;

import com.adobe.cq.social.scf.OperationException;
import org.apache.felix.scr.annotations.*;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import java.io.IOException;

@Component(label = "Social Ugc Management", immediate = true, enabled = true,
        metatype = true,
        description = "This configuration defines the endpoint for UGC Management",
        policy = ConfigurationPolicy.REQUIRE)
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/ugcmanagement", propertyPrivate = true)
}
)
public class UserUgcManagementServlet extends SlingAllMethodsServlet {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Reference
    private com.adobe.cq.social.user.ugc.management.api.UserUgcManagement userUgcManagement;

    @Property(name = "Enabled UGC Management", boolValue = false, description = "Enable UGC Management")
    private static volatile boolean ENABLED;

    @Activate
    private void config(final ComponentContext context){
        ENABLED = (Boolean) context.getProperties().get("Enabled UGC Management");
        logger.info("UGC Management ENABLED " + ENABLED);
    }

    @Override
    protected void doGet(final SlingHttpServletRequest req,
                         final SlingHttpServletResponse resp) throws ServletException, IOException {
        if (ENABLED){
            final ResourceResolver resourceResolver = req.getResourceResolver();
            String user = req.getParameter("user");
            String operation = req.getParameter("operation");
            if (user != null && operation.equalsIgnoreCase("getugc")) {
                resp.setContentType("application/octet-stream");
                final String headerKey = "Content-Disposition";
                final String headerValue = "attachment; filename=\"" + user + "-UgcData.zip" + "\"";
                resp.setHeader(headerKey, headerValue);
                try {
                    userUgcManagement.getUserUgc(resourceResolver, user, resp.getOutputStream());
                } catch (OperationException e) {
                    logger.error("Operation exception", e);
                    throw new ServletException("Unable to get UGC for " + user);
                } catch (JSONException e) {
                    logger.error("Malformed json", e);
                    throw new ServletException("Unable to get UGC for " + user);
                } catch (RepositoryException e) {
                    logger.error("Repository exception", e);
                    throw new ServletException("Unable to get UGC for " + user);
                }
            }
            else{
                throw new ServletException("Get Ugc: Undefined user/operation");
            }
        }
        else{
            logger.error("UGC Management not enabled");
            throw new ServletException("Unable to perform Operation: UGC Management not enabled");
        }
    }

    @Override
    protected void doPost(final SlingHttpServletRequest req,
                          final SlingHttpServletResponse resp) throws ServletException, IOException {
        if (ENABLED){
            final ResourceResolver resourceResolver = req.getResourceResolver();
            String user = req.getParameter("user");
            String operation = req.getParameter("operation");
            try {
                if(user != null && operation.equalsIgnoreCase("deleteUgc")) {
                    userUgcManagement.deleteUserUgc(resourceResolver, user);
                } else if (user != null && operation.equalsIgnoreCase("deleteUser")){
                    userUgcManagement.deleteUserAccount(resourceResolver, user);
                }
                else{
                    throw new ServletException("Unable to perform Operation: " + operation + " for user: " + user);
                }
            } catch (RepositoryException e) {
                throw new ServletException("Unable to perform Operation: " + operation + " for user: " + user);
            } catch (OperationException e) {
                logger.error("operation error", e);
                throw new ServletException("Unable to perform Operation: " + operation + " for user: " + user);
            }
        }
        else{
            logger.error("UGC Management not enabled");
            throw new ServletException("Unable to perform Operation: UGC Management not enabled");
        }
    }
}
