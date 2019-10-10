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

import com.adobe.communities.ugc.migration.util.Constants;
import com.adobe.cq.social.graph.SocialGraph;
import com.adobe.cq.social.graph.Vertex;
import com.adobe.granite.socialgraph.Direction;
import com.adobe.granite.socialgraph.Relationship;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.*;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
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
import java.io.*;

@Component(label = "Social Graph Exporter",
        description = "Moves social graph schema into a  json for storage or re-import", specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/graph/content/export")})

public class SocialGraphExportServlet extends SlingSafeMethodsServlet {

    @Reference
    protected SlingRepository repository;
    
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String JCR_PRIMARY_TYPE = JcrConstants.JCR_PRIMARYTYPE;
    private static final String AUTHORIZABLE_USER = UserConstants.NT_REP_AUTHORIZABLE_FOLDER ;
    private static final String USER = UserConstants.NT_REP_USER ;

    private Writer responseWriter;

    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws ServletException, IOException {

        final SocialGraph graph = request.getResourceResolver().adaptTo(SocialGraph.class);
        final String path = StringUtils.stripEnd(request.getRequestParameter("path").getString(), "/");
        final String relType =request.getRequestParameter("relType") !=null ? request.getRequestParameter("relType").toString():null;
        final String typeS =request.getRequestParameter("typeS") !=null ? request.getRequestParameter("typeS").toString():null;
        final String fileName =request.getRequestParameter("fileName") !=null ? request.getRequestParameter("fileName").toString():"null";

        if(fileName == null){
            logger.error("fileName parameter is not present. Exiting");
            throw new ServletException("fileName parameter is not present. Exiting");
        }
        if(relType == null){
        	logger.error("relType parameters us not present. Exiting");
        	throw new ServletException("relType parameters us not present. Exiting");
        }else if(typeS ==null){
            logger.error("typeS parameters us not present. Exiting");
            throw new ServletException("typeSO37\n" +
                    "518Adobe-Marketing-Cloud/aem-communities-ugc-migration\n" +
                    " Code Issues 3 Pull requests 2 Projects 0 Wiki Security Insights\n" +
                    "CQ-4279355 export code for migration from MSRP to ASRP. #25\n" +
                    " Open\tabhishekgarg18 wants to merge 2 commits into Adobe-Marketing-Cloud:master from abhishekgarg18:dev_msrp_asrp_migration\n" +
                    "+976 −15 \n" +
                    " Conversation 20  Commits 2  Checks 0  Files changed 10\n" +
                    " Open\n" +
                    "CQ-4279355 export code for migration from MSRP to ASRP. #25\n" +
                    "File filter... \n" +
                    "0 / 8 files viewed\n" +
                    " 137  ...ation/src/main/java/com/adobe/communities/ugc/migration/export/ActivityExportServlet.java \n" +
                    "Viewed\n" +
                    "@@ -0,0 +1,137 @@\n" +
                    "Multi-line comments are here!\n" +
                    "\n" +
                    "You can now comment on multiple lines. Just click and drag on the  button.\n" +
                    "\n" +
                    "Demonstrating selecting multiple lines for commenting\n" +
                    "\n" +
                    "package com.adobe.communities.ugc.migration.export;\n" +
                    "\n" +
                    "import com.adobe.communities.ugc.migration.util.Constants;\n" +
                    "import com.adobe.cq.social.activitystreams.api.SocialActivityManager;\n" +
                    "import com.adobe.cq.social.activitystreams.api.SocialActivityStream;\n" +
                    "import com.adobe.cq.social.scf.ClientUtilityFactory; parameters us not present. Exiting");
        }


        final Resource userRoot = request.getResourceResolver().getResource(path);
        if (null == userRoot) {
            throw new ServletException("Cannot locate a valid resource at " + path);
        }
        final ValueMap vm = userRoot.adaptTo(ValueMap.class);
        if (!vm.get(JCR_PRIMARY_TYPE).equals(AUTHORIZABLE_USER)) {
            throw new ServletException("Cannot locate a valid resource at " + path);
        }

        File outFile = null ;
        try {
            outFile = File.createTempFile(fileName, ".json");
            if (!outFile.canWrite()) {
                throw new ServletException("Cannot write to specified output file");
            }
            response.setContentType("application/octet-stream");
            final String headerKey = "Content-Disposition";
            final String headerValue = "attachment; filename=\""+fileName+".json\"";
            response.setHeader(headerKey, headerValue);

            FileOutputStream fos = new FileOutputStream(outFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            //loadMap(request) ;


            OutputStream outStream = null;
            InputStream inStream = null;
            try {
                responseWriter = new OutputStreamWriter(bos);
                JSONWriter jsonWriter = new JSONWriter(responseWriter);
                jsonWriter.setTidy(true);
                jsonWriter.object() ;
                exportSocialGraph(jsonWriter, userRoot, graph, relType, typeS);
                jsonWriter.endObject();
                responseWriter.flush();

                IOUtils.closeQuietly(bos);
                IOUtils.closeQuietly(fos);
                // obtains response's output stream
                outStream = response.getOutputStream();
                inStream = new FileInputStream(outFile);
                // copy from file to output
                IOUtils.copy(inStream, outStream);
            } catch (final IOException e) {
                throw new ServletException(e);
            } catch (final Exception e) {
                throw new ServletException(e);
            } finally {
                IOUtils.closeQuietly(bos);
                IOUtils.closeQuietly(fos);
                IOUtils.closeQuietly(inStream);
                IOUtils.closeQuietly(outStream);
            }
        } finally {
            if (outFile != null) {
                outFile.delete();
            }
        }
    }

    private void exportSocialGraph(
            final JSONWriter writer, final Resource resource, final SocialGraph graph, String relType,
    		String typeS)
            throws JSONException, RepositoryException {
        Iterable<Resource> children = resource.getChildren();
        for (final Resource child : children) {
            if (child.isResourceType(USER)) {
         
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