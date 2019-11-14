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
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.servlet.ServletException;
import java.io.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;

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
        final String fileName =request.getRequestParameter("fileName") !=null ? request.getRequestParameter("fileName").toString():null;

        if(fileName == null){
            logger.error("fileName parameter is not present. Exiting");
            throw new ServletException("fileName parameter is not present. Exiting");
        }
        if(relType == null){
        	logger.error("relType parameters us not present. Exiting");
        	throw new ServletException("relType parameters us not present. Exiting");
        }else if(typeS ==null){
            logger.error("typeS parameters us not present. Exiting");
            throw new ServletException("parameters typeS not present. Exiting");
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
                long startTime = Calendar.getInstance().getTimeInMillis() ;
                List<String> allUsersPath = getAllUsers(request) ;
                responseWriter = new OutputStreamWriter(bos);
                JSONWriter jsonWriter = new JSONWriter(responseWriter);
                jsonWriter.setTidy(true);
                jsonWriter.object() ;

                //exportSocialGraph(jsonWriter, userRoot, graph, relType, typeS);
                exportSocialGraphUsingUsersPath(jsonWriter, userRoot, graph, relType, typeS,allUsersPath);
                long endTime = Calendar.getInstance().getTimeInMillis() ;
                logger.info("end -time" + (endTime-startTime));
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
                Iterable<Relationship> relationships =
                        vertex.getRelationships(relType ,Direction.OUTGOING, typeS);
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



    private void exportSocialGraphUsingUsersPath(
            final JSONWriter writer,
            final Resource resource,
            final SocialGraph graph,
            String relType,
            String typeS,
            List<String> usersPathList)
            throws JSONException, RepositoryException {
        ResourceResolver resourceResolver = resource.getResourceResolver();
        LinkedBlockingQueue<Runnable> taskList = new LinkedBlockingQueue<Runnable>() ;
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                5,
                10,
                10L,
                TimeUnit.MILLISECONDS,
                taskList);
        for (String userPath : usersPathList) {
            RelationshipRunner relationshipRunner = new RelationshipRunner(resourceResolver, userPath, relType, typeS, writer, graph, logger);

            threadPoolExecutor.submit(relationshipRunner);
            //logger.info("size " +taskList.size()) ;
        }
        while(taskList.isEmpty() == false){
            try {
                Thread.currentThread().sleep(1000);
            }catch(Exception e){
                logger.error("interrupted",e);
            }
        }
        threadPoolExecutor.shutdown();
        try {
           threadPoolExecutor.awaitTermination(5, TimeUnit.MINUTES) ;
        }catch(Exception e){
            logger.error("interrupted",e);
        }
    }

    private List<String> getAllUsers(SlingHttpServletRequest request) throws Exception {
        HashSet<String> users = new HashSet<String>();
        users.add("pageexporterservice");
        users.add("projects-service");
        users.add("suggestionservice");
        users.add("media-service");
        users.add("authentication-service");
        users.add("snapshotservice");
        users.add("ocs-lifecycle");
        users.add("tagmanagerservice");
        users.add("searchpromote-service");
        users.add("device-identification-service");
        users.add("commerce-orders-service");
        users.add("commerce-frontend-service");
        users.add("commerce-backend-service");
        users.add("recs-deleted-products-listener-service");
        users.add("dam-sync-service");
        users.add("audiencemanager-syncsegments-service");
        users.add("audiencemanager-configlistener-service");
        users.add("campaign-reader");
        users.add("targetservice");
        users.add("webservice-support-servicelibfinder");
        users.add("webservice-support-replication");
        users.add("webservice-support-statistics");
        users.add("oauthservice");
        users.add("spellchecker-service");
        users.add("dam-teammgr-service");
        users.add("activitypurgesrv");
        users.add("idsjobprocessor");
        users.add("dynamic-media-replication");
        users.add("resourcecollectionservice");
        users.add("msm-service");
        users.add("dtmservice");
        users.add("communities-ugc-writer");
        users.add("communities-user-admin");
        users.add("communities-workflow-launcher");
        users.add("communities-utility-reader");
        users.add("version-purge-service");
        users.add("fd-service");
        users.add("analyticsservice");
        users.add("statistics-service");
        users.add("anonymous");
        users.add("james.devore@spambob.com");
        users.add("matt.monroe@mailinator.com");
        users.add("emily.andrews@mailinator.com");
        users.add("jason.werner@dodgit.com");
        users.add("ryan.palmer@spambob.com");
        users.add("felicia.carter@trashymail.com");
        users.add("andrew.schaeffer@trashymail.com");
        users.add("aaron.mcdonald@mailinator.com");
        users.add("sean.smith@geometrixxoutdoors.com");
        users.add("weston.mccall@dodgit.com");
        users.add("ashley.thompson@spambob.com");
        users.add("josh.bradley@pookmail.com");
        users.add("rebekah.larsen@trashymail.com");
        users.add("donna.billups@pookmail.com");
        users.add("boyd.larsen@dodgit.com");
        users.add("test");
        users.add("replication-receiver");
        users.add("campaign-remote");
        users.add("laura.j.richardson@pookmail.com");
        users.add("charles.s.johnson@trashymail.com");
        users.add("zachary.w.mitchell@spambob.com");
        users.add("iris.r.mccoy@mailinator.com");
        users.add("keith.m.mabry@spambob.com");
        users.add("carlene.j.avery@mailinator.com");
        users.add("leslie.d.dufault@trashymail.com");
        users.add("ralph.e.johnson@mailinator.com");
        users.add("yolanda.s.huggins@trashymail.com");
        users.add("olive.d.pixley@spambob.com");
        users.add("kelly.creative@geometrixx.info");
        users.add("jdoe@geometrixx.info");
        users.add("william.a.plunkett@mailinator.com");
        users.add("luz.a.smith@dodgit.com");
        users.add("willie.a.melton@dodgit.com");
        users.add("kerri.g.saner@dodgit.com");
        users.add("harold.w.gavin@spambob.com");
        users.add("leonard.a.duncan@mailinator.com");
        users.add("author");
        users.add("ivan.l.parrino@mailinator.com");
        users.add("shantel.j.jones@pookmail.com");
        users.add("scott.b.reynolds@dodgit.com");
        users.add("virginia.l.armstrong@spambob.com");
        users.add("omar.b.kamp@dodgit.com");
        users.add("larry.a.spiller@pookmail.com");
        users.add("aparker@geometrixx.info");
        users.add("wallace.escott@geometrixx-media.com");
        users.add("trina.dombrowski@geometrixx-media.com");
        users.add("marcy.aja@geometrixx-media.com");
        users.add("perry.eastman@geometrixx-media.com");
        users.add("charlotte.capp@geometrixx-media.com");
        users.add("mathew.echavez@geometrixx-media.com");
        users.add("joel.czuba@geometrixx-media.com");
        users.add("willard.ebbing@geometrixx-media.com");
        users.add("carl.eastham@geometrixx-media.com");
        //users.add("admin");
        Session session = request.getResourceResolver().adaptTo(Session.class);
        String q = "/jcr:root/home/users//element(*, rep:User)";
        Query query = session.getWorkspace().getQueryManager().createQuery(q, Query.XPATH);
        ArrayList<String> paths = new ArrayList<String>();
        for (NodeIterator i = query.execute().getNodes(); i.hasNext();) {
            Node node = i.nextNode();
            String usrPath = node.getPath();
            if(!users.contains(node.getProperty("rep:principalName").getString())
                    && !usrPath.startsWith("/home/users/system")
                    && !usrPath.startsWith("/home/users/geometrixx")
                    && !usrPath.startsWith("/home/users/mac/")
                    && !usrPath.startsWith("/home/users/media/")
                    && !usrPath.startsWith("/home/users/we-retail/")){
                paths.add(usrPath);
           }
        }
        return paths;
    }
}

class RelationshipRunner extends Thread {
    ResourceResolver resourceResolver = null ;
    String userPath = null ;
    String relType= null ;
    String typeS = null ;
    JSONWriter writer = null ;
    SocialGraph graph = null ;
    private Logger logger = null ;

    public RelationshipRunner(ResourceResolver resourceResolver,
                              String userPath,
                              String relType,
                              String typeS,
                              JSONWriter jsonWriter,
                              final SocialGraph graph,
                              Logger logger ) {
        this.resourceResolver = resourceResolver;
        this.userPath = userPath;
        this.relType = relType;
        this.typeS = typeS;
        this.writer = jsonWriter ;
        this.graph = graph ;
         this.logger = logger ;
    }



    public void run() {
        try {
            Resource user = resourceResolver.getResource(userPath);
            Vertex vertex = graph.getVertex(user.adaptTo(User.class).getID());
            if (vertex == null) {
                logger.info("not able to find vertex for user {}", user.adaptTo(User.class).getID());
                return;
            }
            Iterable<Relationship> relationships = vertex.getRelationships(relType, Direction.OUTGOING, typeS);
            Boolean first = true;
            for (final Relationship relationship : relationships) {
                if (first) {
                    writer.key(user.adaptTo(User.class).getID());
                    writer.array();
                    first = false;
                }
                writer.value(relationship.getEndNode().getId());
            }
            if (!first) {
                writer.endArray();
            }
        }catch(Exception e){
            logger.error("exception occured in thread  for userPath "+ userPath,e);
        }
    }
}