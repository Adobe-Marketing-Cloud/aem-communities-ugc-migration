package com.adobe.communities.upgrade.template.migration.impl;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import com.adobe.communities.upgrade.template.migration.api.CQ64CommunitiesTemplateMigrationTask;


public class FunctionTemplateMigrator extends TemplateMigrator {

	public FunctionTemplateMigrator(CQ64CommunitiesTemplateMigrationTask codeUpgradeTask) {
		super(codeUpgradeTask);
	}

	@Override
	protected boolean copyTemplates(Session session) {
		boolean successful = false;
		try {
    		Node newTemplatesParentNode = TemplateMigrationUtil.getConfGlobalCommunityNode(session, codeUpgradeTask);
            Node oldCustomFunctionNode = TemplateMigrationUtil.getOldConfigNode(session, CQ64CommunitiesTemplateMigrationTask.FUNCTIONS_CUSTOM_PATH_ETC, codeUpgradeTask);
            if (oldCustomFunctionNode != null) {
                if(newTemplatesParentNode !=null ){
                    //copying old custom templates to new location
                    session.getWorkspace().copy(CQ64CommunitiesTemplateMigrationTask.FUNCTIONS_CUSTOM_PATH_ETC, newTemplatesParentNode.getPath() + "/" + CQ64CommunitiesTemplateMigrationTask.CONF_FUNCTIONS_PATH);
                    if (session.hasPendingChanges()) {
                        session.save();
                        Node functionTemplateNode = session.getNode(newTemplatesParentNode.getPath() + "/" + CQ64CommunitiesTemplateMigrationTask.CONF_FUNCTIONS_PATH);
                        setMergeList(session, functionTemplateNode);
                    }
                    codeUpgradeTask.setProgressInfo("SUCCESS : /templates/functions  moved from " + oldCustomFunctionNode.getPath() + " to " + newTemplatesParentNode.getPath() +"/" + CQ64CommunitiesTemplateMigrationTask.CONF_FUNCTIONS_PATH);
                    successful = true;
                    return true;
                }
            } else {
            	successful = true;
            	codeUpgradeTask.setProgressInfo("SUCCESS : custom function templates under etc not found. No migration required.");
            }
        }
        catch (Exception e) {
        	codeUpgradeTask.setProgressInfo("Could not complete the movement of function: " + e.getMessage());
        }
		return successful;
	}

	@Override
	protected boolean updateReferences(Session session) {
		// 1) update custom function's references in custom site and custom group templates
		if (updateFunctionReferencesForFunctionProp(session)) {
			// 2) update all function's references in actual sites and groups
			if(updateFunctionReferencesForETCPathProp(session)){
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean deleteTemplates(Session session) {
		return deleteNode(session, CQ64CommunitiesTemplateMigrationTask.FUNCTIONS_PATH_ETC);
	}

	private boolean updateFunctionReferencesForETCPathProp(Session session){
		boolean isSuccessful = false;
		try {
			
			NodeIterator customTemplates = getCustomFunctionTemplates(session);
			if (customTemplates != null) {
				updateETCPathPropValue(customTemplates, session, CQ64CommunitiesTemplateMigrationTask.CONF_GLOBAL_FUNCTION_TEMPLATES);
			}
			
			NodeIterator referenceTemplates = getReferenceFunctionTemplates(session);
			if (referenceTemplates != null) {
				updateETCPathPropValue(referenceTemplates, session, CQ64CommunitiesTemplateMigrationTask.LIBS_FUNCTION_TEMPLATES);
			}
			isSuccessful = true;
        } catch (RepositoryException e) {
        	codeUpgradeTask.setProgressInfo("Something went wrong when accessing the repository: " + e.getMessage());
        }
        if (isSuccessful) {
        	codeUpgradeTask.setProgressInfo("Finished updateFunctionReferencesForETCPathProp");
        } 
		return isSuccessful;
	}
	
	private boolean updateFunctionReferencesForFunctionProp(Session session){
		boolean isSuccessful = false;
		try {
			
			NodeIterator customTemplates = getCustomFunctionTemplates(session);
			if (customTemplates != null) {
				updateFunctionPropValue(customTemplates, session, CQ64CommunitiesTemplateMigrationTask.CONF_GLOBAL_FUNCTION_TEMPLATES);
			}
			
			NodeIterator referenceTemplates = getReferenceFunctionTemplates(session);
			if (referenceTemplates != null) {
				updateFunctionPropValue(referenceTemplates, session, CQ64CommunitiesTemplateMigrationTask.LIBS_FUNCTION_TEMPLATES);
			}
			isSuccessful = true;
        } catch (RepositoryException e) {
        	codeUpgradeTask.setProgressInfo("Something went wrong when accessing the repository: " + e.getMessage());
        }
        if (isSuccessful) {
        	codeUpgradeTask.setProgressInfo("Finished updateFunctionReferencesForFunctionProp");
        } 
		return isSuccessful;
	}
	
	private void updateFunctionPropValue(NodeIterator customTemplates,  Session session, String prefixPath) throws RepositoryException {
		QueryManager queryManager;
		while(customTemplates.hasNext()) {
			Node functionTemplate = customTemplates.nextNode();
			String path = functionTemplate.getPath();
			String name = functionTemplate.getName();
			String updatedFunctionValue = prefixPath + "/" + name;
			queryManager = session.getWorkspace().getQueryManager();
            String queryString = String.format(CQ64CommunitiesTemplateMigrationTask.QUERY_FUNCTION_PROP, path);
            Query query = queryManager.createQuery(queryString, Query.JCR_SQL2);
            QueryResult queryResult = query.execute();
            for (NodeIterator nodeIterator = queryResult.getNodes(); nodeIterator.hasNext();) {
                Node node = nodeIterator.nextNode();
                node.setProperty(CQ64CommunitiesTemplateMigrationTask.PROP_FUNCTION, updatedFunctionValue);
                if (session.hasPendingChanges()) {
                    session.save();
                    codeUpgradeTask.setProgressInfo("Updated the function value at " + node.getPath() + "with " + updatedFunctionValue);
                }
            }
            codeUpgradeTask.setProgressInfo("Updated the function value for " + path);
		}
	}
	
	private void updateETCPathPropValue(NodeIterator customTemplates,  Session session, String prefixPath) throws RepositoryException {
		QueryManager queryManager;
		while(customTemplates.hasNext()) {
			Node functionTemplate = customTemplates.nextNode();
			String path = functionTemplate.getPath();
			String name = functionTemplate.getName();
			String updatedFunctionValue = prefixPath + "/" + name;
			queryManager = session.getWorkspace().getQueryManager();
            String queryString = String.format(CQ64CommunitiesTemplateMigrationTask.QUERY_ETC_PATH, path);
            Query query = queryManager.createQuery(queryString, Query.JCR_SQL2);
            QueryResult queryResult = query.execute();
            for (NodeIterator nodeIterator = queryResult.getNodes(); nodeIterator.hasNext();) {
                Node node = nodeIterator.nextNode();
                node.setProperty(CQ64CommunitiesTemplateMigrationTask.PROP_ETC_PATH, updatedFunctionValue);
                if (session.hasPendingChanges()) {
                    session.save();
                    codeUpgradeTask.setProgressInfo("Updated the function value at " + node.getPath() + "with " + updatedFunctionValue);
                }
            }
            codeUpgradeTask.setProgressInfo("Updated the function value for " + path);
		}
	}

	private NodeIterator getCustomFunctionTemplates(Session session) throws PathNotFoundException, RepositoryException {
		return session.nodeExists(CQ64CommunitiesTemplateMigrationTask.FUNCTIONS_CUSTOM_PATH_ETC) ? session.getNode(CQ64CommunitiesTemplateMigrationTask.FUNCTIONS_CUSTOM_PATH_ETC).getNodes() : null;
	}
	
	private NodeIterator getReferenceFunctionTemplates(Session session) throws PathNotFoundException, RepositoryException {
		return session.nodeExists(CQ64CommunitiesTemplateMigrationTask.FUNCTIONS_REFERENCE_PATH_ETC) ? session.getNode(CQ64CommunitiesTemplateMigrationTask.FUNCTIONS_REFERENCE_PATH_ETC).getNodes() : null;
	}
}
