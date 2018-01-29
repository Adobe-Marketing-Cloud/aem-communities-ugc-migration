/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * __________________
 *
 *  Copyright 2018 Adobe Systems Incorporated
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated and its
 * suppliers and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 **************************************************************************/
package com.adobe.communities.upgrade.template.migration.api;


import javax.jcr.Node;
import javax.jcr.Session;
import javax.servlet.Servlet;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import com.adobe.communities.upgrade.template.migration.impl.FunctionTemplateMigrator;
import com.adobe.communities.upgrade.template.migration.impl.GroupTemplateMigrator;
import com.adobe.communities.upgrade.template.migration.impl.SiteTemplateMigrator;
import com.adobe.communities.upgrade.template.migration.impl.TemplateMigrationUtil;
import com.adobe.communities.upgrade.template.migration.impl.TemplateMigrator;

@Component(metatype = true, immediate = true, label = "EnablementResourceMigrationServlet", description = "This servlet handles the AF attachments associated with Forms Dashboard workflow participant step")
@Service(value = Servlet.class)
@Properties(value = {
        @Property(name = "sling.servlet.extensions", value = "json", propertyPrivate = true),
        @Property(name = "sling.servlet.paths", value = "social/upgrade/templateMigrationServlet", propertyPrivate = true),
        @Property(name = "sling.servlet.methods", value = {"POST"}, propertyPrivate = true),
        @Property(name = "sleep.time", value = {"2000"}, propertyPrivate = false)
})
public class CQ64CommunitiesTemplateMigrationTask extends SlingAllMethodsServlet{

    /**
	 * 
	 */
	private static final long serialVersionUID = -7368956900995542756L;
	private final Logger log = LoggerFactory.getLogger(CQ64CommunitiesTemplateMigrationTask.class);
	
	public static final String CONF_GLOBAL_SETTINGS_ROOT = "/conf/global/settings";
    public static final String COMMUNITY_CONFIGURATION_CONF_ROOT = "community/templates";
    public static final String EMAIL_PATH_ETC = "/etc/community/templates/email";
    public static final String SUBSCRIPTION_EMAIL_PATH_ETC = "/etc/community/templates/subscriptions-email";
    public static final String CONF_EMAIl_PATH = "email";
    public static final String CONF_SUBSCRIPTION_EMAIl_PATH = "subscriptions-email";
    public static final String CONF_FUNCTIONS_PATH = "functions";
    public static final String CONF_GROUPS_PATH = "groups";
    public static final String CONF_SITES_PATH = "sites";
    public static final String FUNCTIONS_PATH_ETC = "/etc/community/templates/functions";
    public static final String GROUPS_PATH_ETC = "/etc/community/templates/groups";
    public static final String SITES_PATH_ETC = "/etc/community/templates/sites";
    public static final String CUSTOM = "custom";
    public static final String REFERENCE = "reference";
    public static final String FUNCTIONS_CUSTOM_PATH_ETC = FUNCTIONS_PATH_ETC + "/" + CUSTOM;
    public static final String FUNCTIONS_REFERENCE_PATH_ETC = FUNCTIONS_PATH_ETC + "/" + REFERENCE;
    
    public static final String GROUPS_CUSTOM_PATH_ETC = GROUPS_PATH_ETC + "/" + CUSTOM;
    public static final String GROUPS_REFERENCE_PATH_ETC = GROUPS_PATH_ETC + "/" + REFERENCE;
    
    public static final String SITES_CUSTOM_PATH_ETC = SITES_PATH_ETC + "/" + CUSTOM;
    public static final String SITES_REFERENCE_PATH_ETC = SITES_PATH_ETC + "/" + REFERENCE;
    
    public static final String CONF_GLOBAL_FUNCTION_TEMPLATES = CONF_GLOBAL_SETTINGS_ROOT + "/" + 
    															COMMUNITY_CONFIGURATION_CONF_ROOT + "/" + CONF_FUNCTIONS_PATH;
    
    public static final String CONF_GLOBAL_GROUP_TEMPLATES = CONF_GLOBAL_SETTINGS_ROOT + "/" + 
															COMMUNITY_CONFIGURATION_CONF_ROOT + "/" + CONF_GROUPS_PATH;
    
    public static final String CONF_GLOBAL_SITES_TEMPLATES = CONF_GLOBAL_SETTINGS_ROOT + "/" + 
															COMMUNITY_CONFIGURATION_CONF_ROOT + "/" + CONF_SITES_PATH;
    
    public static final String LIBS_FUNCTION_TEMPLATES = "/libs/settings/community/templates/functions";
    public static final String LIBS_GROUPS_TEMPLATES = "/libs/settings/community/templates/groups";
    public static final String LIBS_SITES_TEMPLATES = "/libs/settings/community/templates/sites";
   
    public static final String PROP_FUNCTION = "function";
    public static final String PROP_ETC_PATH = "etcpath";
    public static final String PROP_BLUEPRINT = "blueprint";
	public static final String PROP_GROUP_TEMPLATES = "groupTemplates";
	
	public static final String QUERY_FUNCTION_PROP = "SELECT * FROM [oak:Unstructured] AS s WHERE "
            + "( ISDESCENDANTNODE([/etc/community/templates/groups/custom]) "
            + "or  ISDESCENDANTNODE([/etc/community/templates/sites/custom]) "
            + "or  ISDESCENDANTNODE([/content]) ) "
            + "and (s.[function] = '%s')";
	
	public static final String QUERY_ETC_PATH = "SELECT * FROM [nt:unstructured] AS s WHERE "
            + "ISDESCENDANTNODE([/content]) "
            + "and (s.[etcpath] = '%s')" ;

	public static final String QUERY_GROUP_TEMPLATE_PROP = "SELECT * FROM [oak:Unstructured] AS s WHERE "
            + "( ISDESCENDANTNODE([/etc/community/templates/sites/custom]) "
            + "or  ISDESCENDANTNODE([/content]) ) "
            + "and CONTAINS(s.[groupTemplates], '%s')" ;

	public static final String QUERY_BLUEPRINT_PROP = "SELECT * FROM [sling:Folder] AS s WHERE "
            + "ISDESCENDANTNODE([/content]) "
            + "and (s.[blueprint] = '%s')" ;
	
	public void doPost(SlingHttpServletRequest req, SlingHttpServletResponse resp) {
		doUpgrade(req.getResourceResolver().adaptTo(Session.class));
    }
	
    protected void doUpgrade(Session session) {
        setProgressInfo("moving communities templates.");
        //move email templates
        moveEmailTemplates(session, EMAIL_PATH_ETC, CONF_EMAIl_PATH);
        
        //move subscription email templates
        moveSubsCriptionEmailTemplates(session);
        
        boolean successStatus = false;
        //migrate function templates
        
        TemplateMigrator functionMigrator = new FunctionTemplateMigrator(this); 
        successStatus = functionMigrator.migrateTemplates(session);
        if(successStatus){
	       
        	//migrate group templates
        	TemplateMigrator groupMigrator = new GroupTemplateMigrator(this);
        	successStatus = groupMigrator.migrateTemplates(session);
	        
	        //migrate site templates
	        if(successStatus){
	        	TemplateMigrator siteMigrator = new SiteTemplateMigrator(this);
	        	if(siteMigrator.migrateTemplates(session)) {
	        		//delete custom site templates
					if (siteMigrator.deleteTemplates(session)) {
						setProgressInfo("Deleted old site templates");
						if (groupMigrator.deleteTemplates(session)) {
							setProgressInfo("Deleted old group templates");
							if (functionMigrator.deleteTemplates(session)) {
								setProgressInfo("Deleted old function templates");
							}
						}
					}
	        	}
	        }else {
	        	setProgressInfo("group templates migration failed, aborting");
	        }
        }else{
        	setProgressInfo("functions templates migration failed, aborting");
        }
    }

	private void moveEmailTemplates(Session session, String oldPath, String newPathSuffix) {
    	try {
    		Node newTemplatesParentNode = TemplateMigrationUtil.getConfGlobalCommunityNode(session, this);
            Node oldEmailTemplatesNode = TemplateMigrationUtil.getOldConfigNode(session,oldPath, this);
            if (oldEmailTemplatesNode != null) {
                if(newTemplatesParentNode !=null ){
                    //move old email Folder to new location
                    session.move(oldEmailTemplatesNode.getPath(), newTemplatesParentNode.getPath() +"/" +newPathSuffix);
                    if (session.hasPendingChanges()) {
                        session.save();
                    }
                    setProgressInfo("SUCCESS : /templates/email configurations moved from " + oldEmailTemplatesNode.getPath() + " to " + newTemplatesParentNode.getPath() + "/" +CONF_EMAIl_PATH);
                }
            } else {
                setProgressInfo("SUCCESS : email templates under etc not found. No migration required.");
            }
        }
        catch (Exception e) {
            setProgressInfo("Could not complete the movement of email templates: " + e.getMessage());
        }
	}
    
    private void moveSubsCriptionEmailTemplates(Session session) {
    	moveEmailTemplates(session, SUBSCRIPTION_EMAIL_PATH_ETC, CONF_SUBSCRIPTION_EMAIl_PATH);
    }

    public void setProgressInfo(String info){
    	log.info(info);
    }
}
