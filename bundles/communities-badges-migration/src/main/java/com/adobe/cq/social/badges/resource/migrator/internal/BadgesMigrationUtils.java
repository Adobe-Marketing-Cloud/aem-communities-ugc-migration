package com.adobe.cq.social.badges.resource.migrator.internal;

import javax.annotation.Nonnull;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.settings.SlingSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BadgesMigrationUtils {
	
	private static final Logger log = LoggerFactory.getLogger(BadgesMigrationUtils.class);

	public static NodeIterator getScoringNodes(ResourceResolver resourceResolver, String leaderBoardPath) {

		 Session session = resourceResolver.adaptTo(Session.class);
		 Node scoringNodes = null;
		log.error(" in getting scoring node  ");
		// String  componentPath = DigestUtils.sha256Hex(BadgingConstants.COMPONENT_RESOURCE_PATH);
		 try {
			 scoringNodes = session.getNode(leaderBoardPath);
		 } catch (Exception e) {
			 log.error("error in getting scoring node {} ", e.getMessage() );
			 return null;
		 }		

		 NodeIterator sitesIterator = null;
		 try {
			 sitesIterator = scoringNodes.getNodes();
		 } catch (Exception e) {
			 log.error("error in getting scoring iterator {} ", e.getMessage() );
			 return null;
		 }

		 return sitesIterator;

	 }
	 
	 private static String getBadgeResourceName(@Nonnull final String rulePath, @Nonnull final String componentPath) {
		        // get badge root path for user

		        // a badge is associated with a rule + where the rule was located
		        // need to hash the leaf node since it is too long
		        // insert invalid jcr node /:/ in case (almost 0 probability) rulePath + componentPath is not unique
		        return DigestUtils.sha256Hex(rulePath + "/:/" + componentPath);
		    }
	 
	 
	 public static String getNewAssignedBadgesPath(String badgeName, ResourceResolver resourceResolver){
		 
         String  componentPath	 = BadgingConstants.COMPONENT_RESOURCE_PATH;
         String newBadgeContentPath = getNewBadgeContentPath(badgeName, resourceResolver);
		 return getBadgeResourceName(newBadgeContentPath, componentPath);
	 }
	 
	public static String getNewTopEarnedBadgesPath(String badgeName, ResourceResolver resourceResolver){
		 
		String  componentPath	 = BadgingConstants.COMPONENT_RESOURCE_PATH;
        String rulePath = BadgingConstants.NEW_BADGING_RULE_PROP;
	    return getBadgeResourceName(rulePath, componentPath);
	 }
	 

   	public static String getNewEarnedBadgesPath(String badgeName, ResourceResolver resourceResolver) {
   		String  componentPath	 = BadgingConstants.COMPONENT_RESOURCE_PATH;
        String rulePath = BadgingConstants.NEW_BADGING_RULE_PROP;
        String newBadgeContentPath = getNewBadgeContentPath(badgeName, resourceResolver);
        String badgeSRPResourceName =  getBadgeResourceName(rulePath, componentPath);
        return  getBadgeHistoryResourceName(badgeSRPResourceName, newBadgeContentPath);
	}
	
    public static String getNewBadgeContentPath(String currentBadgeContentPath, ResourceResolver resourceResolver) {
    	
        String libsImagePath = currentBadgeContentPath.replaceFirst("/etc", BadgingConstants.LIBS_IMAGES_PATH);
        Resource libsResource = resourceResolver.getResource(libsImagePath);
        if(libsResource != null)
        	 return libsResource.getPath();
        String contentImagePath = currentBadgeContentPath.replaceFirst("/etc", BadgingConstants.CONTENT_IMAGES_PATH);
        Resource contentResource = resourceResolver.getResource(contentImagePath);
        if(contentResource != null)
        	 return contentResource.getPath();
        return null;
	}
    
    private static String getBadgeHistoryResourceName(@Nonnull final String currentBadgePath, @Nonnull final String badgeContentPath) {
    	return currentBadgePath + "_" + DigestUtils.sha256Hex(badgeContentPath);
    }
    
    public static void change(Node node, String newName) throws RepositoryException {
        node.getSession().move(node.getPath(), node.getParent().getPath() + "/" + newName);
   }
    
    public static boolean isPublishMode(SlingSettingsService slingSettingsService){
        return slingSettingsService != null && slingSettingsService.getRunModes().contains("publish");
    }
    
	
}
