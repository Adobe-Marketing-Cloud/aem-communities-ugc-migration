package com.adobe.cq.social.badges.resource.migrator.service;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;

public interface BadgeResourceMigrationService {

    public void createNewBadges(SlingHttpServletRequest req, SlingHttpServletResponse resp) throws Exception;
    
    public void deleteOldBadges(SlingHttpServletRequest req, SlingHttpServletResponse resp) throws Exception;
}
