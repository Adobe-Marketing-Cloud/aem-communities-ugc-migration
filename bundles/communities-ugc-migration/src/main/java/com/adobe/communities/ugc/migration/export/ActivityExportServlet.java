package com.adobe.communities.ugc.migration.export;

import com.adobe.communities.ugc.migration.util.Constants;
import com.adobe.cq.social.activitystreams.api.SocialActivityManager;
import com.adobe.cq.social.activitystreams.api.SocialActivityStream;
import com.adobe.cq.social.scf.ClientUtilityFactory;
import com.adobe.cq.social.ugcbase.SocialUtils;
import com.adobe.granite.activitystreams.Activity;
import com.adobe.granite.xss.XSSAPI;
import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.*;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.io.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.*;
import java.util.HashMap;
import java.util.Map;


@Component(label = "UGC Importer for acttvity Data",
        description = "Moves activity data within json files", specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/activity/export")})
public class ActivityExportServlet extends SlingAllMethodsServlet {


    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY, policy = ReferencePolicy.STATIC)
    private XSSAPI xss;

    @Reference
    private SocialUtils socialUtils;

    @Reference
    ClientUtilityFactory clientUtilFactory;

    @Reference
    SocialActivityManager socialActivityManager ;

    private Writer responseWriter;

    private Integer fetchCount = 100 ;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws ServletException, IOException {
        File outFile = null ;
        try {

            outFile = File.createTempFile(Constants.ACTIVITIES, ".json");
            if (!outFile.canWrite()) {
                throw new ServletException("Cannot write to specified output file");
            }
            response.setContentType("application/octet-stream");
            final String headerKey = "Content-Disposition";
            final String headerValue = "attachment; filename=\"activities.json\"";
            response.setHeader(headerKey, headerValue);

            FileOutputStream fos = new FileOutputStream(outFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            //loadMap(request) ;
            exportToFile(request,bos) ;

            OutputStream outStream = null;
            InputStream inStream = null;
            try {
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

    private void exportToFile(
            final SlingHttpServletRequest request,
            BufferedOutputStream bos) {

        ResourceResolver resolver = request.getResourceResolver();
        resolver.getUserID();
        Resource streamResource = resolver.resolve(Constants.ACTIVITY_STREAM_PATH);
        SocialActivityStream stream = socialActivityManager.getSocialStream(streamResource, Constants.ACTIVITY_STREAM_NAME, true);

        responseWriter = new OutputStreamWriter(bos);
        try {
            JSONWriter jsonWriter = new JSONWriter(responseWriter);
            jsonWriter.setTidy(true);
            jsonWriter.object();
            jsonWriter.key(Constants.ACTIVITIES);
            jsonWriter.array();
            Integer readCount = 0;
            Integer index = 0;
            do {
                readCount = 0 ;
                int offset = fetchCount*index ;
                logger.info("reading from offset= {} fetchCount = {}", offset,fetchCount);
                for (Activity a : stream.getActivities(offset, fetchCount)) {
                    readCount++ ;
                    jsonWriter.value(a.toJSON());
                }
                logger.info("read successfully from offset= {} fetchCount = {}" ,offset, fetchCount);
                index++ ;
            }while(readCount.compareTo(fetchCount) == 0) ;

            jsonWriter.endArray();
            jsonWriter.endObject();
            responseWriter.flush();
        } catch (Exception e) {
            logger.error("exception occured while fetching activites from stream ",e);
        }
    }


}
