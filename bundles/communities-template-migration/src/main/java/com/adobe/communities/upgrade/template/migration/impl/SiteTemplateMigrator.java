package com.adobe.communities.upgrade.template.migration.impl;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.communities.upgrade.template.migration.api.CQ64CommunitiesTemplateMigrationTask;


public class SiteTemplateMigrator extends TemplateMigrator{

	public SiteTemplateMigrator(CQ64CommunitiesTemplateMigrationTask codeUpgradeTask) {
		super(codeUpgradeTask);
	}

	private final Logger log = LoggerFactory.getLogger(getClass());
	
	@Override
	protected boolean copyTemplates(Session session) {
    	boolean successful = false;
		try {
    		Node newTemplatesParentNode = TemplateMigrationUtil.getConfGlobalCommunityNode(session, codeUpgradeTask);
            Node oldCustomFunctionNode = TemplateMigrationUtil.getOldConfigNode(session,CQ64CommunitiesTemplateMigrationTask.SITES_CUSTOM_PATH_ETC, codeUpgradeTask);
            if (oldCustomFunctionNode != null) {
                if(newTemplatesParentNode !=null ){
                    //copying old custom templates to new location
                    session.getWorkspace().copy(CQ64CommunitiesTemplateMigrationTask.SITES_CUSTOM_PATH_ETC, newTemplatesParentNode.getPath() + "/" + CQ64CommunitiesTemplateMigrationTask.CONF_SITES_PATH);
                    if (session.hasPendingChanges()) {
                        session.save();
                    }
                    Node functionTemplateNode = session.getNode(newTemplatesParentNode.getPath() + "/" + CQ64CommunitiesTemplateMigrationTask.CONF_SITES_PATH);
                    setMergeList(session, functionTemplateNode);
                    codeUpgradeTask.setProgressInfo("SUCCESS : /templates/sites  moved from " + oldCustomFunctionNode.getPath() + " to " + newTemplatesParentNode.getPath() +"/" + CQ64CommunitiesTemplateMigrationTask.CONF_SITES_PATH);
                    successful = true;
                    return true;
                }
            } else {
            	codeUpgradeTask.setProgressInfo("SUCCESS : custom sites templates under etc not found. No migration required.");
            }
        }
        catch (Exception e) {
        	codeUpgradeTask.setProgressInfo("Could not complete the movement of site templates: " + e.getMessage());
            log.info("exception ", e);
        }
		return successful;
		
	}

	@Override
	protected boolean updateReferences(Session session) {
		return updateSiteReferencesForBluePrintProp(session);
	}

	@Override
	public boolean deleteTemplates(Session session) {
		return deleteNode(session, CQ64CommunitiesTemplateMigrationTask.SITES_PATH_ETC);
	}

	private boolean updateSiteReferencesForBluePrintProp(Session session) {
		boolean isSuccessful = false;
		try {
			
			NodeIterator customTemplates = getCustomSiteTemplates(session);
			while (customTemplates.hasNext()) {
				TemplateMigrationUtil.updateBluePrintPropValue(customTemplates.nextNode(), session, CQ64CommunitiesTemplateMigrationTask.CONF_GLOBAL_SITES_TEMPLATES, codeUpgradeTask);
			}
			
			NodeIterator referenceTemplates = getReferenceSiteTemplates(session);
			while (referenceTemplates.hasNext()){
				TemplateMigrationUtil.updateBluePrintPropValue(referenceTemplates.nextNode(), session, CQ64CommunitiesTemplateMigrationTask.LIBS_SITES_TEMPLATES, codeUpgradeTask);
			}
			isSuccessful = true;
        } catch (RepositoryException e) {
        	codeUpgradeTask.setProgressInfo("Something went wrong when accessing the repository: " + e.getMessage());
        }
        if (isSuccessful) {
        	codeUpgradeTask.setProgressInfo("Finished updateSiteReferencesForBluePrintProp");
        } 
		return isSuccessful;
	}
	
	private NodeIterator getReferenceSiteTemplates(Session session) throws PathNotFoundException, RepositoryException {
		return session.nodeExists(CQ64CommunitiesTemplateMigrationTask.SITES_REFERENCE_PATH_ETC) ?  session.getNode(CQ64CommunitiesTemplateMigrationTask.SITES_REFERENCE_PATH_ETC).getNodes() : null;
	}

	private NodeIterator getCustomSiteTemplates(Session session) throws PathNotFoundException, RepositoryException {
		return session.nodeExists(CQ64CommunitiesTemplateMigrationTask.SITES_CUSTOM_PATH_ETC)? session.getNode(CQ64CommunitiesTemplateMigrationTask.SITES_CUSTOM_PATH_ETC).getNodes() : null;
	}
}
