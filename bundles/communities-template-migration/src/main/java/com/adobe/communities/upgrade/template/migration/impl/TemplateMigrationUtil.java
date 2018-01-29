package com.adobe.communities.upgrade.template.migration.impl;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.apache.sling.jcr.resource.JcrResourceConstants;

import com.adobe.communities.upgrade.template.migration.api.CQ64CommunitiesTemplateMigrationTask;
import com.day.cq.commons.jcr.JcrUtil;

public class TemplateMigrationUtil {

	public static void updateBluePrintPropValue(Node templateNode, Session session, String prefixPath, CQ64CommunitiesTemplateMigrationTask codeUpgradeTask) throws RepositoryException {
		QueryManager queryManager;
		String path = templateNode.getPath();
		String name = templateNode.getName();
		String updatedFunctionValue = prefixPath + "/" + name;
		queryManager = session.getWorkspace().getQueryManager();
        String queryString = String.format(CQ64CommunitiesTemplateMigrationTask.QUERY_BLUEPRINT_PROP, path);
        Query query = queryManager.createQuery(queryString, Query.JCR_SQL2);
        QueryResult queryResult = query.execute();
        for (NodeIterator nodeIterator = queryResult.getNodes(); nodeIterator.hasNext();) {
            Node node = nodeIterator.nextNode();
            node.setProperty(CQ64CommunitiesTemplateMigrationTask.PROP_BLUEPRINT, updatedFunctionValue);
            if (session.hasPendingChanges()) {
                session.save();
                codeUpgradeTask.setProgressInfo("Updated the blueprint value at " + node.getPath() + " with " + updatedFunctionValue);
            }
        }
        codeUpgradeTask.setProgressInfo("Updated the blueprint value for " + path);
		
	}
	
	public static Node getConfGlobalCommunityNode(Session session, CQ64CommunitiesTemplateMigrationTask codeUpgradeTask){
        try {
            if (session.nodeExists(CQ64CommunitiesTemplateMigrationTask.CONF_GLOBAL_SETTINGS_ROOT)) {
                Node globalSettingsNode = session.getNode(CQ64CommunitiesTemplateMigrationTask.CONF_GLOBAL_SETTINGS_ROOT);
                if(globalSettingsNode != null){
                    Node communityConfigNode =  JcrUtil.createPath(globalSettingsNode, CQ64CommunitiesTemplateMigrationTask.COMMUNITY_CONFIGURATION_CONF_ROOT,false,JcrResourceConstants.NT_SLING_FOLDER,JcrResourceConstants.NT_SLING_FOLDER,session,true);
                    return communityConfigNode;
                } else {
                	codeUpgradeTask.setProgressInfo("Could not find conf/global/settings node.");
                    return null;
                }
            }
        }
        catch (Exception e) {
        	codeUpgradeTask.setProgressInfo("Unable to create/find conf/global/settings/community node." + e.getMessage());
        }
        return null;
    }

    public static Node getOldConfigNode(Session session, String nodePath, CQ64CommunitiesTemplateMigrationTask codeUpgradeTask){
        try {
            if (session.nodeExists(nodePath)) {
                Node srpNode = session.getNode(nodePath);
                return srpNode ;
            } else {
            	codeUpgradeTask.setProgressInfo("Unable to find legacy "+ nodePath +" node. ");
                return null;
            }
        }
        catch (Exception e) {
        	codeUpgradeTask.setProgressInfo("Unable to find legacy "+ nodePath +" node : " + e.getMessage());
            return null;
        }
    }
	
}
