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
package com.adobe.communities.ugc.migration.legacyProfileExport;

import com.adobe.granite.socialgraph.Direction;
import com.adobe.granite.socialgraph.GraphNode;
import com.adobe.granite.socialgraph.Relationship;
import com.adobe.granite.socialgraph.SocialGraph;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.sling.jcr.api.SlingRepository;

import javax.servlet.ServletException;
import java.io.IOException;

@Component(label = "Social Graph Exporter",
        description = "Moves social graph schema into a zip archive for storage or re-import", specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/graph/export")})

public class SocialGraphExportServlet extends SlingSafeMethodsServlet {

    @Reference
    protected SlingRepository repository;

    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws ServletException, IOException {

        final JSONWriter writer = new JSONWriter(response.getWriter());
        writer.setTidy(true);
        final SocialGraph graph = request.getResourceResolver().adaptTo(SocialGraph.class);
        final String path = StringUtils.stripEnd(request.getRequestParameter("path").getString(), "/");
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
            exportSocialGraph(writer, userRoot, graph);
            writer.endObject();
        } catch (final JSONException e) {
            throw new ServletException("Encountered a json exception while exporting social graph", e);
        }
    }

    private void exportSocialGraph(final JSONWriter writer, final Resource resource, final SocialGraph graph)
            throws JSONException {
        Iterable<Resource> children = resource.getChildren();
        for (final Resource child : children) {
            if (child.isResourceType("rep:User")) {
                final GraphNode graphNode = graph.getNode(child.getName());
                Iterable<Relationship> relationships = graphNode.getRelationships(Direction.OUTGOING, "following");
                Boolean first = true;
                for (final Relationship relationship : relationships) {
                    if (first) {
                        writer.key(child.getName());
                        writer.array();
                        first = false;
                    }
                    writer.value(relationship.getEndNode().getId());
                }
                if (!first) {
                    writer.endArray();
                }
            } else {
                exportSocialGraph(writer, child, graph);
            }
        }
    }
}
