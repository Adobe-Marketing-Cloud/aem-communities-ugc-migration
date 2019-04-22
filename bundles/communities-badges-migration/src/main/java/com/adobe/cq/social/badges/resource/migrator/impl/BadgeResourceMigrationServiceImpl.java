package com.adobe.cq.social.badges.resource.migrator.impl;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.social.badges.resource.migrator.internal.BadgesMigrationUtils;
import com.adobe.cq.social.badges.resource.migrator.internal.BadgingConstants;
import com.adobe.cq.social.badges.resource.migrator.service.BadgeResourceMigrationService;
import com.adobe.cq.social.badging.api.BadgingService;
import com.adobe.cq.social.community.api.CommunityContext;
import com.adobe.cq.social.scoring.api.ScoringConstants;
import com.adobe.cq.social.scoring.api.ScoringService;
import com.adobe.cq.social.srp.SocialResourceProvider;
import com.adobe.cq.social.ugcbase.SocialUtils;
import com.adobe.cq.social.user.internal.UserBadgeUtils;
import com.adobe.cq.social.user.internal.UserProfileBadge;
import com.adobe.granite.security.user.UserManagementService;

@Component(immediate = true)
@Service(value = BadgeResourceMigrationService.class)
public class BadgeResourceMigrationServiceImpl implements BadgeResourceMigrationService{

	private final Logger log = LoggerFactory.getLogger(BadgeResourceMigrationService.class);

	@Reference
	private UserManagementService userManagementService;

	@Reference
	private EventAdmin eventAdmin;

	@Reference
	private BadgingService badgingService;

	@Reference
	private ScoringService scoringService;


	@Override
	public void createNewBadges(SlingHttpServletRequest req , SlingHttpServletResponse resp) throws Exception {

		log.info("[BADGEMIGRATIONTASK] BadgeCreation Start Time : " + System.currentTimeMillis() / 1000);

		ResourceResolver resourceResolver = req.getResourceResolver();
		Resource componentResource = resourceResolver.getResource(BadgingConstants.COMPONENT_RESOURCE_PATH);
		Resource scoreRuleResource = resourceResolver.getResource(BadgingConstants.NEW_SCORING_NAME_PROP);
		Resource badgingRuleResource = resourceResolver.getResource(BadgingConstants.NEW_BADGING_RULE_PROP);
		if(componentResource == null) {
			log.error("[BadgesMigrationTask] componentResource null ");
			return;
		}else if (scoreRuleResource ==null){
			log.error("[BadgesMigrationTask] scoring null");
			return;
		}

		Resource resource = req.getResource();
		String userId = req.getParameter("authid");                         // createBadges for only single user.
		if(userId !=null){
			createNewEarnedBadges(resourceResolver,userId, resource,badgingRuleResource, componentResource);        	
			createNewAssignedBadges(resourceResolver,userId , badgingRuleResource, componentResource);
			log.info("[BadgesMigrationTask] BadgeCreation End TIme : " + System.currentTimeMillis() / 1000);
			return;
		}

		// getting total number of scoring nodes       
		final SocialResourceProvider srp = getSRP(resourceResolver, componentResource);
		long total_scores = 0;
		try {
			total_scores = scoringService.getTotalNumberOfScores(resourceResolver, componentResource, scoreRuleResource);         // not able to get total number of scoring node present
		} catch (RepositoryException e) {
			log.error("[BadgesMigrationTask] error in getting scoring number   {}", e.getCause());
			return;
		}

		// getting leaderboard Path		
		log.info("[BadgesMigrationTask] scoring number is  {}", total_scores);
		String leaderboardPath = null;
		try {
			leaderboardPath = scoringService.getScoreResourcePath(resourceResolver, null, componentResource, scoreRuleResource);
		} catch (RepositoryException e1) {
			log.error("[BadgesMigrationTask] error in getting scoring path");
		}
		if(leaderboardPath == null){
			log.error("[BadgesMigrationTask] error in getting scoring path");
			return;
		}
		log.info("[BadgesMigrationTask] leadeboardpath: {} ",leaderboardPath);


		final List<Map.Entry<String, Boolean>> order = new ArrayList<Map.Entry<String, Boolean>>();
		// sort based on score value
		order.add(new AbstractMap.SimpleEntry<String, Boolean>(ScoringConstants.SCORE_VALUE_PROP, true));
		// break ties with userid
		order.add(new AbstractMap.SimpleEntry<String, Boolean>(ScoringConstants.USERID_PROP, true));

		int offset =0;
		int size = 10;
		int totalLeaderboardItmes = 0;
		while (offset <=total_scores) {
			Iterator<Resource> scores =
					srp.listChildren(leaderboardPath, resourceResolver, offset, size, order);
			String authId = "";
			while (scores.hasNext()) {
				try {
					Resource nt = scores.next();
					totalLeaderboardItmes++;
					try {
						authId = nt.getValueMap().get("userIdentifier").toString();
					} catch (Exception e) {
						log.error("[BADGEMIGRATIONTASK]  Did not get the authid of userid from scoringNode");
						continue;
					}
					if (badgingService != null) {
						createNewEarnedBadges(resourceResolver,authId, resource, badgingRuleResource, componentResource);
						createNewAssignedBadges(resourceResolver,authId , badgingRuleResource, componentResource);
						
					}
				} catch (Exception e) {
					log.error("Error in creating badge for authid : {} error:{} ", authId, e.getCause());
				}
			}
			offset+=size;
		}

		log.info("[BadgesMigrationTask] total LeaderBoardItems iterated : " + totalLeaderboardItmes);
		log.info("[BadgesMigrationTask] BadgeCreation End TIme : " + System.currentTimeMillis() / 1000);

	}





	private int createNewEarnedBadges(ResourceResolver resourceResolver,String authId, Resource resource, Resource badgingRuleResource, Resource componentResource) throws Exception{

		UserBadgeUtils badgeUtils = new UserBadgeUtils(resourceResolver, badgingService, authId);
		CommunityContext communityContext = resource.adaptTo(CommunityContext.class);
		if (communityContext != null && StringUtils.isBlank(communityContext.getSiteId())) {
			communityContext = null;
		}
		List<UserProfileBadge> earnedBadges = badgeUtils.getBadges(communityContext, BadgingService.EARNED_BADGES);
		Set<String> userBadgesSet = new HashSet<String>();
		for (UserProfileBadge badge:earnedBadges){
			userBadgesSet.add(badge.getUserBadgeResource().getValueMap().get("badgeContentPath", String.class));
		}
		Collections.sort(earnedBadges, new CustomComparator());
		int count = 0 ;
		for (UserProfileBadge badge:earnedBadges) {
			Resource badgeResource = badge.getUserBadgeResource();
			ValueMap  badgeMap = badgeResource.getValueMap();
			String currentBadgeContentPath = badgeMap.get("badgeContentPath", String.class);
			String newBadgeContentPath = BadgesMigrationUtils.getNewBadgeContentPath(currentBadgeContentPath, resourceResolver);
			if(currentBadgeContentPath.startsWith("/etc") && !userBadgesSet.contains(newBadgeContentPath)){
				final Map<String, Object> badgingProps = new HashMap<String, Object>();
				Calendar earnedDate  = badge.getUserBadgeResource().getValueMap().get(BadgingService.BADGE_EARNED_DATE_PROP, Calendar.class);
				int score = badge.getEarnedScore();
				badgingProps.put(ScoringConstants.COMPONENT_PATH_PROP, BadgingConstants.COMPONENT_RESOURCE_PATH);
				badgingProps.put(BadgingService.BADGE_EARNED_SCORE_PROP, score);
				badgingProps.put(BadgingService.BADGE_EARNED_DATE_PROP, earnedDate);
				badgingService.saveBadge(resourceResolver, authId, badgingRuleResource, componentResource, newBadgeContentPath, false,badgingProps);
				log.info("[BADGEMIGRATIONTASK] new earned badges created for authId:{} badge: {}" , authId, newBadgeContentPath);
				count++;
			}
		}
		return count;
	}

	
	
	public class CustomComparator implements Comparator<UserProfileBadge> {
	    @Override
	    public int compare(UserProfileBadge u1, UserProfileBadge u2) {
	    	Integer u1Score = u1.getEarnedScore();
	    	Integer u2Score =  u2.getEarnedScore();
	        return u1Score.compareTo(u2Score);
	    }
	}
	

	private int createNewAssignedBadges(ResourceResolver resourceResolver,String authId, Resource resource, Resource componentResource) throws Exception {

		UserBadgeUtils badgeUtils = new UserBadgeUtils(resourceResolver, badgingService, authId);
		CommunityContext communityContext = resource.adaptTo(CommunityContext.class);
		if (communityContext != null && StringUtils.isBlank(communityContext.getSiteId())) {
			communityContext = null;
		}
		List<UserProfileBadge>  assignBadges = badgeUtils.getBadges(communityContext, BadgingService.ASSIGNED_BADGES);
		Set<String> userBadgesSet = new HashSet<String>();
		for (UserProfileBadge badge:assignBadges){
			userBadgesSet.add(badge.getUserBadgeResource().getValueMap().get("badgeContentPath", String.class));
		}
		int count = 0; 
		for (UserProfileBadge badge: assignBadges) {
			String currentBadgeContentPath = badge.getUserBadgeResource().getValueMap().get("badgeContentPath", String.class);
			String newBadgeContentPath = BadgesMigrationUtils.getNewBadgeContentPath(currentBadgeContentPath, resourceResolver);
			if(currentBadgeContentPath.startsWith("/etc") && !userBadgesSet.contains(newBadgeContentPath)) {
				Calendar earnedDate  = badge.getUserBadgeResource().getValueMap().get(BadgingService.BADGE_EARNED_DATE_PROP, Calendar.class);
				final Map<String, Object> badgeProps = new HashMap<String, Object>();
				badgeProps.put(BadgingService.BADGE_EARNED_DATE_PROP, earnedDate);
				badgingService.saveBadge(resourceResolver, authId, null, componentResource, newBadgeContentPath, true, badgeProps);
				log.info("[BADGEMIGRATIONTASK] new assigned badges created for authId:{} badge: {}" , authId, newBadgeContentPath);
			}
		}
		return count;
	}


	@Override
	public void deleteOldBadges(SlingHttpServletRequest req,SlingHttpServletResponse resp ) throws Exception {
		log.info("[BADGEMIGRATIONTASK] Deleting badges Start Time : " + System.currentTimeMillis() / 1000);
		ResourceResolver resourceResolver = req.getResourceResolver();
		Resource componentResource = resourceResolver.getResource(BadgingConstants.COMPONENT_RESOURCE_PATH);
		Resource scoreRuleResource = resourceResolver.getResource(BadgingConstants.NEW_SCORING_NAME_PROP);
		Resource oldBadgingRuleResource = resourceResolver.getResource(BadgingConstants.OLD_BADGING_RULE_PROP);
		if(componentResource == null) {
			log.error("[BadgesMigrationTask] componentResource null ");
			return;
		}else if (scoreRuleResource ==null){
			log.error("[BadgesMigrationTask] scoring null");
			return;
		}

		Resource resource = req.getResource();
		String authId = req.getParameter("authid");
		if(authId !=null){
			if (badgingService != null) {
				CommunityContext communityContext = resource.adaptTo(CommunityContext.class);
				if (communityContext != null && StringUtils.isBlank(communityContext.getSiteId())) {
					communityContext = null;
				}
				final UserBadgeUtils badgeUtils = new UserBadgeUtils(resourceResolver, badgingService, authId);
				List<UserProfileBadge> earnedBadges = badgeUtils.getBadges(communityContext, BadgingService.EARNED_BADGES);
				List<UserProfileBadge>  assignedBadges = badgeUtils.getBadges(communityContext, BadgingService.ASSIGNED_BADGES);

				deleteBadges(earnedBadges,authId, oldBadgingRuleResource , componentResource, false, resourceResolver );
				deleteBadges(assignedBadges, authId, null, componentResource, true, resourceResolver);
			}  
			return;
		}

		final SocialResourceProvider srp = getSRP(resourceResolver, componentResource);
		long total_scores = 0;
		total_scores = scoringService.getTotalNumberOfScores(resourceResolver, componentResource, scoreRuleResource);

		log.info("[BadgesMigrationTask] scoring number is  {}", total_scores);
		String leaderboardPath = scoringService.getScoreResourcePath(resourceResolver, null, componentResource, scoreRuleResource);

		if(leaderboardPath == null){
			log.error("[BadgesMigrationTask] error in getting scoring path");
			return;
		}

		log.info("[BadgesMigrationTask] leadeboardpath: {} ",leaderboardPath);
		final List<Map.Entry<String, Boolean>> order = new ArrayList<Map.Entry<String, Boolean>>();
		// sort based on score value
		order.add(new AbstractMap.SimpleEntry<String, Boolean>(ScoringConstants.SCORE_VALUE_PROP, true));

		int offset =0;
		int size = 10;
		int totalLeaderboardItmes = 0;
		while (offset <=total_scores) {
			Iterator<Resource> scores =
					srp.listChildren(leaderboardPath, resourceResolver, offset, size, order);
			while (scores.hasNext()) {
				Resource nt = scores.next();
				totalLeaderboardItmes++;
				try {
					authId = nt.getValueMap().get("userIdentifier").toString();
				} catch (Exception e) {
					log.error("[BADGEMIGRATIONTASK]  Did not get the authid of userid from scoringNode");
					continue;
				}
				if (badgingService != null) {
					CommunityContext communityContext = resource.adaptTo(CommunityContext.class);
					if (communityContext != null && StringUtils.isBlank(communityContext.getSiteId())) {
						communityContext = null;
					}
					final UserBadgeUtils badgeUtils = new UserBadgeUtils(resourceResolver, badgingService, authId);
					List<UserProfileBadge> earnedBadges = badgeUtils.getBadges(communityContext, BadgingService.EARNED_BADGES);
					List<UserProfileBadge>  assignedBadges = badgeUtils.getBadges(communityContext, BadgingService.ASSIGNED_BADGES);
					deleteBadges(earnedBadges,authId, oldBadgingRuleResource , componentResource, false, resourceResolver );  // delete earned badges
					deleteBadges(assignedBadges, authId, null, componentResource, true, resourceResolver);        // delete assigned badges
				}  
			}
			offset+=size;
		}

		log.info("[BadgesMigrationTask] total LeaderBoardItems iterated : " + totalLeaderboardItmes);
		log.info("[BadgesMigrationTask] Deleting old badges End TIme : " + System.currentTimeMillis() / 1000);

	}




	private void deleteBadges(List<UserProfileBadge> userBadges, String userId, Resource ruleResource, Resource componentResource, boolean isAssigned, ResourceResolver resourceResolver) {

		Set<String> userBadgesSet = new HashSet<String>();
		for (UserProfileBadge badge:userBadges){
			userBadgesSet.add(badge.getUserBadgeResource().getValueMap().get("badgeContentPath", String.class));
		}
		for (UserProfileBadge badge:userBadges) {
			String currentBadgeContentPath = badge.getUserBadgeResource().getValueMap().get("badgeContentPath", String.class);
			String newBadgeContentPath = BadgesMigrationUtils.getNewBadgeContentPath(currentBadgeContentPath, resourceResolver);
			
			if(currentBadgeContentPath.startsWith("/etc") && userBadgesSet.contains(newBadgeContentPath)) {
				try {
					badgingService.deleteBadge(resourceResolver, userId, ruleResource, componentResource, currentBadgeContentPath, isAssigned);
					log.info("[BADGEMIGRATIONTASK] Deleted  badge userid : {} badgeContentPath : {} ",userId, currentBadgeContentPath);
				} catch (Exception e) {
					log.error("[BADGEMIGRATIONTASK] Error in deleting badge userid : {} badgeContentPath : {} ",userId, currentBadgeContentPath);
				}
			}
		}
	}


	private  SocialResourceProvider getSRP(final ResourceResolver resolver, final Resource componentResource) {
		if (componentResource == null) {
			log.error("can't obtain configured SocialResourceProvider because componentResource is null");
			return null;
		}

		final SocialUtils socialUtils = resolver.adaptTo(SocialUtils.class);
		if (socialUtils == null) {
			log.error("can't obtain a reference to SocialUtils");
			return null;
		}

		SocialResourceProvider srp = socialUtils.getConfiguredProvider(componentResource);
		if (srp == null) {
			log.error("can't obtain configured SocialResourceProvider using the componentResource");
			return null;
		}

		// the current SRP's resolver is from the componentResource. We want a SRP with the passed-in resolver.
		final Resource root = resolver.getResource(srp.getASIPath());
		if (root == null) {
			log.error("can't read SRP root");
			return null;
		}

		srp = socialUtils.getConfiguredProvider(root);
		if (srp == null) {
			log.error("can't obtain configured SocialResourceProvider");
			return null;
		}

		// initialize the configured SRP
		srp.setConfig(socialUtils.getStorageConfig(root));

		return srp;
	}



}
