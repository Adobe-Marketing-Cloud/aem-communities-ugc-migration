package com.adobe.cq.social.badges.resource.migrator.service;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;

public interface BadgeResourceValidationService {

    public void validateNewBadges(SlingHttpServletRequest req, long delay, SlingHttpServletResponse resp) throws Exception;
}
