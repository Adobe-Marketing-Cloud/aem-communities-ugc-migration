package com.adobe.cq.social.badges.resource.migrator.api;

import javax.servlet.Servlet;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.settings.SlingSettingsService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.social.badges.resource.migrator.internal.BadgesMigrationUtils;
import com.adobe.cq.social.badges.resource.migrator.service.BadgeResourceValidationService;


@Component(metatype = true, immediate = true, label = "BadgeResourceCreateServlet", description = "This Servlets create the new badges")
@Service(value = Servlet.class)
@Properties(value = {
        @Property(name = "sling.servlet.extensions", value = "json", propertyPrivate = true),
        @Property(name = "sling.servlet.paths", value = "/libs/social/badges/badgeResourceValidationServlet", propertyPrivate = true),
        @Property(name = "sling.servlet.methods", value = {"POST"}, propertyPrivate = true),
        @Property(name = "sleep.time", value = {"2000"}, propertyPrivate = false)
})

public class BadgeResourceValidationServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;
    
    private static long delay = 2000;

    @Reference
    private SlingSettingsService settingsService;
    
    @Reference
    private BadgeResourceValidationService badgeResourceValidationService;
    
    private final Logger log = LoggerFactory.getLogger(BadgeResourceValidationServlet.class);

    protected void activate(ComponentContext componentContext) throws Exception {
        log.info("Activated: BadgeResourceValidationServlet started");
    }
    
    protected void deactivate(ComponentContext context) {
        log.info("Deactivated: BadgeResourceValidationServlet stopped");
    }

    public void doPost(SlingHttpServletRequest req, SlingHttpServletResponse resp) {
        getRequestHandler(req, resp);
    }

    private void getRequestHandler(SlingHttpServletRequest req, SlingHttpServletResponse resp) {
        log.info("Got Badge Validation Request");
        if (!BadgesMigrationUtils.isPublishMode(settingsService)) {
            log.info("Validation Request Not Received on Publish Aborting it");
            return;
        }
        try {
        	badgeResourceValidationService.validateNewBadges(req, delay, resp);
		} catch (Exception e) {
			log.error("Error in validating badges");
		}
    }
    
}