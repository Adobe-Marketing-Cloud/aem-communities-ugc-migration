package com.adobe.communities.ugc.migration.export;

import com.adobe.communities.ugc.migration.util.Constants;
import com.adobe.cq.social.activitystreams.api.SocialActivityManager;
import com.adobe.cq.social.activitystreams.api.SocialActivityStream;
import com.adobe.cq.social.notifications.api.NotificationManager;
import com.adobe.cq.social.notifications.client.api.SocialNotification;
import com.adobe.cq.social.scf.ClientUtilityFactory;
import com.adobe.cq.social.ugc.api.ComparisonType;
import com.adobe.cq.social.ugc.api.Operator;
import com.adobe.cq.social.ugc.api.UgcFilter;
import com.adobe.cq.social.ugc.api.ValueConstraint;
import com.adobe.cq.social.ugcbase.SocialUtils;
import com.adobe.granite.activitystreams.Activity;
import com.adobe.granite.xss.XSSAPI;
import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.*;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.io.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.servlet.ServletException;
import java.io.*;
import java.util.*;


@Component(label = "UGC Importer for All UGC Data",
        description = "Moves ugc data within json files into the active SocialResourceProvider", specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/notification/export")})
public class NotificationExportServlet extends SlingAllMethodsServlet {

    @Reference
    private NotificationManager notioficationManagerImpl ;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY, policy = ReferencePolicy.STATIC)
    private XSSAPI xss;

    @Reference
    private SocialUtils socialUtils;

    @Reference
    private ClientUtilityFactory clientUtilFactory;

    private Writer responseWriter;

    private Integer fetchCount = 100 ;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Reference
    private SocialActivityManager activityStreamManager;

    public static final String DEFAULT_USER_ACTIVITIES_STREAM_NAME = "notifications";

    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws ServletException, IOException {
        Resource resource = request.getResource();

        File outFile = null ;
        try {
            outFile = File.createTempFile(Constants.NOTIFICATION, ".json");
            if (!outFile.canWrite()) {
                throw new ServletException("Cannot write to specified output file");
            }
            response.setContentType("application/octet-stream");
            final String headerKey = "Content-Disposition";
            final String headerValue = "attachment; filename=\"notifications.json\"";
            response.setHeader(headerKey, headerValue);

            FileOutputStream fos = new FileOutputStream(outFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            //loadMap(request) ;


            OutputStream outStream = null;
            InputStream inStream = null;
            try {

                exportNotificationToFile(request,bos) ;

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

    private void exportNotificationToFile(final SlingHttpServletRequest request, BufferedOutputStream bos){
        ResourceResolver resolver = request.getResourceResolver();
        resolver.getUserID() ;
        responseWriter = new OutputStreamWriter(bos);
        List<String> users = new ArrayList<String>();
        try {
            users = getAllUsers(request);
        } catch (Exception e) {
            logger.error("error occured while reading users");

        }
        try {

            JSONWriter jsonWriter = new JSONWriter(responseWriter);
            jsonWriter.setTidy(true);
            jsonWriter.object() ;
            jsonWriter.key(Constants.NOTIFICATION);
            jsonWriter.array() ;

            for(String user : users) {
                Integer readCount = 0;
                Integer index = 0;
                try {
                    do {
                        readCount = 0;
                        int offset = fetchCount * index;
                        SocialActivityStream stream =
                                (SocialActivityStream) activityStreamManager.getUserStream(resolver, user,
                                        DEFAULT_USER_ACTIVITIES_STREAM_NAME, false);
                        UgcFilter filter = new UgcFilter();
                        final ValueConstraint<String> resourceFilter =
                                new ValueConstraint<String>(SocialUtils.PN_SLING_RESOURCETYPE, SocialNotification.RESOURCE_TYPE,
                                        ComparisonType.Equals, Operator.And);

                        filter.addConstraint(resourceFilter);

                        if (stream != null) {
                            logger.info("reading notification for user = {} from offset= {}  fetchCount = {}" ,user,offset, fetchCount);
                            for (Activity a : stream.getActivities(offset, fetchCount, filter)) {
                                readCount++;
                                if (a != null) {
                                    jsonWriter.value(a.toJSON());
                                }
                            }
                            logger.info("read successfully from offset= " + offset + " fetchCount = " + fetchCount + " for user = " + user);
                        }
                        index++;
                    } while (readCount.compareTo(fetchCount) == 0);
                }catch(Exception e){
                    logger.error("error occured while read notification for user = " + user);
                }
            }
            jsonWriter.endArray() ;
            jsonWriter.endObject();
            responseWriter.flush();
        } catch (Exception e) {
            logger.error("exception occured while fetching notifications from stream ",e);
        }
    }


    private List<String>  getAllUsers(SlingHttpServletRequest request) throws Exception {
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
                paths.add(node.getProperty("rep:principalName").getString());
            }
        }
        return paths;
    }
}
