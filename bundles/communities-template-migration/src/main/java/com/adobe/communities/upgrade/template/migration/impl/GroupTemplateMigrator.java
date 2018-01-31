package com.adobe.communities.upgrade.template.migration.impl;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.communities.upgrade.template.migration.api.CQ64CommunitiesTemplateMigrationTask;


public class GroupTemplateMigrator extends TemplateMigrator {

	private final Logger log = LoggerFactory.getLogger(getClass());
	
	public GroupTemplateMigrator(CQ64CommunitiesTemplateMigrationTask codeUpgradeTask) {
		super(codeUpgradeTask);
	}

	@Override
	protected boolean copyTemplates(Session session) {
		boolean successful = false;
		try {
    		Node newTemplatesParentNode = TemplateMigrationUtil.getConfGlobalCommunityNode(session, codeUpgradeTask);
            Node oldCustomFunctionNode = TemplateMigrationUtil.getOldConfigNode(session, CQ64CommunitiesTemplateMigrationTask.GROUPS_CUSTOM_PATH_ETC, codeUpgradeTask);
            if (oldCustomFunctionNode != null) {
                if(newTemplatesParentNode !=null ){
                    //copying old custom templates to new location
                    session.getWorkspace().copy(CQ64CommunitiesTemplateMigrationTask.GROUPS_CUSTOM_PATH_ETC, newTemplatesParentNode.getPath() + "/" + CQ64CommunitiesTemplateMigrationTask.CONF_GROUPS_PATH);
                    if (session.hasPendingChanges()) {
                        session.save();
                    }
                    Node functionTemplateNode = session.getNode(newTemplatesParentNode.getPath() + "/" + CQ64CommunitiesTemplateMigrationTask.CONF_GROUPS_PATH);
                    setMergeList(session, functionTemplateNode);
                    codeUpgradeTask.setProgressInfo("SUCCESS : /templates/groups  moved from " + oldCustomFunctionNode.getPath() + " to " + newTemplatesParentNode.getPath() +"/" + CQ64CommunitiesTemplateMigrationTask.CONF_GROUPS_PATH);
                    successful = true;
                    return true;
                }
            } else {
                codeUpgradeTask.setProgressInfo("SUCCESS : custom group templates under etc not found. No migration required.");
            }
        }
        catch (Exception e) {
            codeUpgradeTask.setProgressInfo("Could not complete the movement of group templates: " + e.getMessage());
            log.info("exception ", e);
        }
		return successful;
		
	}

	@Override
	protected boolean updateReferences(Session session) {
		if (updateGroupReferencesForGroupTemplateProp(session)) {
			if(updateGroupReferencesForBluePrintProp(session)){
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean deleteTemplates(Session session) {
		return deleteNode(session, CQ64CommunitiesTemplateMigrationTask.GROUPS_PATH_ETC);
	}
	
	private boolean updateGroupReferencesForGroupTemplateProp(Session session) {
		boolean isSuccessful = false;
		try {
			
			NodeIterator customTemplates = getCustomGroupTemplates(session);
			while (customTemplates.hasNext()) {
				updateGroupTemplatePropValue(customTemplates.nextNode(), session, CQ64CommunitiesTemplateMigrationTask.CONF_GLOBAL_GROUP_TEMPLATES);
			}
			
			NodeIterator referenceTemplates = getReferenceGroupTemplates(session);
			while (referenceTemplates.hasNext()) {
				updateGroupTemplatePropValue(referenceTemplates.nextNode(), session, CQ64CommunitiesTemplateMigrationTask.LIBS_GROUPS_TEMPLATES);
			}
			isSuccessful = true;
        } catch (RepositoryException e) {
            codeUpgradeTask.setProgressInfo("Something went wrong when accessing the repository: " + e.getMessage());
        }
        if (isSuccessful) {
            codeUpgradeTask.setProgressInfo("Finished updateGroupReferencesForGroupTemplateProp");
        } 
		return isSuccessful;
	}

	private void updateGroupTemplatePropValue(Node template, Session session, String prefixPath) throws RepositoryException {
		QueryManager queryManager;
			String path = template.getPath();
			String name = template.getName();
			String updatedFunctionValue = prefixPath + "/" + name;
			queryManager = session.getWorkspace().getQueryManager();
            String queryString = String.format(CQ64CommunitiesTemplateMigrationTask.QUERY_GROUP_TEMPLATE_PROP, path);
            Query query = queryManager.createQuery(queryString, Query.JCR_SQL2);
            QueryResult queryResult = query.execute();
            for (NodeIterator nodeIterator = queryResult.getNodes(); nodeIterator.hasNext();) {
                Node node = nodeIterator.nextNode();
                Value[] values = node.getProperty(CQ64CommunitiesTemplateMigrationTask.PROP_GROUP_TEMPLATES).getValues();
                String[] finalPropValue = new String[values.length];
                int index = 0;
                for (Value value : values) {
                	finalPropValue[index] = value.getString().equals(path) ? updatedFunctionValue : value.getString();
                }
                node.setProperty(CQ64CommunitiesTemplateMigrationTask.PROP_GROUP_TEMPLATES, finalPropValue);
                if (session.hasPendingChanges()) {
                    session.save();
                    codeUpgradeTask.setProgressInfo("Updated the function value at " + node.getPath() + "with " + updatedFunctionValue);
                }
            }
            codeUpgradeTask.setProgressInfo("Updated the function value for " + path);
	}

	private NodeIterator getReferenceGroupTemplates(Session session) throws PathNotFoundException, RepositoryException {
		return session.nodeExists(CQ64CommunitiesTemplateMigrationTask.GROUPS_REFERENCE_PATH_ETC) ? session.getNode(CQ64CommunitiesTemplateMigrationTask.GROUPS_REFERENCE_PATH_ETC).getNodes() : null;
	}

	private NodeIterator getCustomGroupTemplates(Session session) throws PathNotFoundException, RepositoryException {
		return session.nodeExists(CQ64CommunitiesTemplateMigrationTask.GROUPS_CUSTOM_PATH_ETC) ? session.getNode(CQ64CommunitiesTemplateMigrationTask.GROUPS_CUSTOM_PATH_ETC).getNodes() : null;
	}
	
	private boolean updateGroupReferencesForBluePrintProp(Session session) {
		boolean isSuccessful = false;
		try {
			
			NodeIterator customTemplates = getCustomGroupTemplates(session);
			while (customTemplates.hasNext()) {
				TemplateMigrationUtil.updateBluePrintPropValue(customTemplates.nextNode(), session, CQ64CommunitiesTemplateMigrationTask.CONF_GLOBAL_GROUP_TEMPLATES, codeUpgradeTask);
			}
			
			NodeIterator referenceTemplates = getReferenceGroupTemplates(session);
			while (referenceTemplates.hasNext()){
				TemplateMigrationUtil.updateBluePrintPropValue(referenceTemplates.nextNode(), session, CQ64CommunitiesTemplateMigrationTask.LIBS_GROUPS_TEMPLATES, codeUpgradeTask);
			}
			isSuccessful = true;
        } catch (RepositoryException e) {
            codeUpgradeTask.setProgressInfo("Something went wrong when accessing the repository: " + e.getMessage());
        }
        if (isSuccessful) {
            codeUpgradeTask.setProgressInfo("Finished updateGroupReferencesForBluePrintProp");
        } 
		return isSuccessful;
	}
}
