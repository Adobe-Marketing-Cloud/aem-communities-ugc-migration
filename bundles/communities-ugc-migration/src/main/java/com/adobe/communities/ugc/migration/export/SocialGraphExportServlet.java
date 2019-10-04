/*************************************************************************
 *
 * ADOBE SYSTEMS INCORPORATED
 * Copyright 2015 Adobe Systems Incorporated
 * All Rights Reserved.
 *
 * NOTICE:  Adobe permits you to use, modify, and distribute this file in accordance with the
 * terms of the Adobe license agreement accompanying it.  If you have received this file from a
 * source other than Adobe, then your use, modification, or distribution of it requires the prior
 * written permission of Adobe.
 **************************************************************************/
package com.adobe.communities.ugc.migration.export;

import com.adobe.cq.social.graph.SocialGraph;
import com.adobe.cq.social.graph.Vertex;
import com.adobe.granite.socialgraph.Direction;
import com.adobe.granite.socialgraph.Relationship;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.*;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import java.io.IOException;

@Component(label = "Social Graph Exporter",
        description = "Moves social graph schema into a zip archive for storage or re-import", specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/graph/msrp/export")})

public class SocialGraphExportServlet extends SlingSafeMethodsServlet {

    @Reference
    protected SlingRepository repository;
    
    Logger logger = LoggerFactory.getLogger(this.getClass());

    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws ServletException, IOException {

        final JSONWriter writer = new JSONWriter(response.getWriter());
        writer.setTidy(true);
        final SocialGraph graph = request.getResourceResolver().adaptTo(SocialGraph.class);
        final String path = StringUtils.stripEnd(request.getRequestParameter("path").getString(), "/");
        final String relType =request.getRequestParameter("relType") !=null ? request.getRequestParameter("relType").toString():null;
        final String typeS =request.getRequestParameter("typeS") !=null ? request.getRequestParameter("typeS").toString():null;
       
        if(relType == null || typeS ==null ){
        	logger.error("Required parameters are not present. Exiting");
        	throw new ServletException("Required parameters are not present. Exiting");
        }
        
        
        final Resource userRoot = request.getResourceResolver().getResource(path);
        if (null == userRoot) {
            throw new ServletException("Cannot locate a valid resource at " + path);
        }
        final ValueMap vm = userRoot.adaptTo(ValueMap.class);
        if (!vm.get("jcr:primaryType").equals("rep:AuthorizableFolder")) {
            throw new ServletException("Cannot locate a valid resource at " + path);
        }
        //iterate over child resources to get user nodes
        try {
            writer.object();
            exportSocialGraph(writer, userRoot, graph, relType, typeS);
            writer.endObject();
        } catch (final Exception e) {
            throw new ServletException("Encountered a json exception while exporting social graph", e);
        }
    }

    private void exportSocialGraph(final JSONWriter writer, final Resource resource, final SocialGraph graph, String relType, 
    		String typeS)
            throws JSONException, RepositoryException {
        Iterable<Resource> children = resource.getChildren();
        for (final Resource child : children) {
            if (child.isResourceType("rep:User")) {
         
                Vertex vertex = graph.getVertex(child.adaptTo(User.class).getID());
                if(vertex == null) {
                  logger.info("not able to find vertex for user {}", child.adaptTo(User.class).getID());
                  continue;
                }
                Iterable<Relationship> relationships = vertex.getRelationships(relType ,Direction.OUTGOING, typeS);
                Boolean first = true;
                for (final Relationship relationship : relationships) {
                    if (first) {
                        writer.key(child.adaptTo(User.class).getID());
                        writer.array();
                        first = false;
                    }
                    writer.value(relationship.getEndNode().getId());
                }
                if (!first) {
                    writer.endArray();
                }
            } else {
                exportSocialGraph(writer, child, graph, relType, typeS);
            }
        }
    }
}