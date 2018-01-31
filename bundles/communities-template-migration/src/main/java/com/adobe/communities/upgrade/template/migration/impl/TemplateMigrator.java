package com.adobe.communities.upgrade.template.migration.impl;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import com.adobe.communities.upgrade.template.migration.api.CQ64CommunitiesTemplateMigrationTask;


public abstract class TemplateMigrator {
	
	protected CQ64CommunitiesTemplateMigrationTask codeUpgradeTask;
	
	public TemplateMigrator(CQ64CommunitiesTemplateMigrationTask codeUpgradeTask) {
		this.codeUpgradeTask = codeUpgradeTask;
	}

	public final boolean migrateTemplates(Session session) {
		//copy function templates
        if (copyTemplates(session)) {
            //update function template references
        	return updateReferences(session);
        }
        return false;
	}

	protected boolean deleteNode(Session session, String path){
		try {
			if(session.nodeExists(path)){
				session.getNode(path).remove();;
				session.save();
				return true;
			}
		} catch (RepositoryException e) {
			codeUpgradeTask.setProgressInfo("Something went wrong when deleting node: " + e.getMessage());
		}
		return false;
	}
	
	protected void setMergeList(Session session, Node functionTemplateNode) throws RepositoryException {
		functionTemplateNode.setProperty("mergeList", "true");
		NodeIterator iterator = functionTemplateNode.getNodes();
		if (iterator != null) {
			while (iterator.hasNext()) {
				Node childNode = iterator.nextNode();
				childNode.addNode("jcr:content","nt:unstructured");
			}
		}
		session.save();
	}
	
	protected abstract boolean copyTemplates(Session session);
	
	protected abstract boolean updateReferences(Session session);
	
	public abstract boolean deleteTemplates(Session session);
}
