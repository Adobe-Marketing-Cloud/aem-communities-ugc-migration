package com.adobe.communities.ugc.migration.export;

import com.adobe.communities.ugc.migration.util.Constants;
import com.adobe.cq.social.activitystreams.api.SocialActivityManager;
import com.adobe.cq.social.activitystreams.api.SocialActivityStream;
import com.adobe.granite.activitystreams.Activity;
import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.*;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


@Component(label = "UGC Exporter for activity Data",
        description = "Moves activity data within json files", specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/activity/export")})
public class ActivityExportServlet extends SlingAllMethodsServlet {

    @Reference
    private SocialActivityManager socialActivityManager ;

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
                logger.error("ioexception occured while exporting activities",e);
                throw new ServletException(e);
            } catch (final Exception e) {
                logger.error("exception occured while exporting activities",e);
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
        Resource streamResource = resolver.resolve(Constants.ACTIVITY_STREAM_PATH);

        if(streamResource != null) {

            SocialActivityStream stream = socialActivityManager.getSocialStream(streamResource, Constants.ACTIVITY_STREAM_NAME, true);

            if(stream != null) {

                responseWriter = new OutputStreamWriter(bos);

                try {
                    JSONWriter jsonWriter = new JSONWriter(responseWriter);
                    jsonWriter.setTidy(true);
                    jsonWriter.object();
                    jsonWriter.key(Constants.ACTIVITIES);
                    jsonWriter.array();
                    Integer readCount;
                    Integer index = 0;
                    do {
                        readCount = 0;
                        int offset = fetchCount * index;
                        logger.info("reading from offset= {} with fetchCount = {}", offset, fetchCount);

                        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                        format.setTimeZone(TimeZone.getDefault());

                        for (Activity a : stream.getActivities(offset, fetchCount)) {
                            if(a != null) {
                                if(a.getProperties() != null) {
                                    //mainly required for tally related activities
                                    Long publishTime = (Long)a.getProperties().get(Constants.PUBLISHED);
                                    if(publishTime == null){
                                        //added property will be present always
                                        publishTime = (Long)a.getProperties().get(Constants.ADDED);
                                    }
                                    Date date = new Date(publishTime);
                                    String dateTime = format.format(date);

                                    if(a.getObject() != null) {
                                        ValueMap prop = a.getObject().getProperties();
                                        if(prop != null) prop.put(Constants.PUBLISHDATE, dateTime);
                                    }

                                    readCount++ ;
                                    jsonWriter.value(a.toJSON());
                                }
                            }
                            logger.info("read successfully from offset= {} with fetchCount = {}", offset, fetchCount);
                            index++;
                        } }while (readCount.compareTo(fetchCount) == 0);

                    jsonWriter.endArray();
                    jsonWriter.endObject();
                    responseWriter.flush();
                } catch (JSONException e) {
                    logger.error("json exception occurred while fetching activities from stream ", e);
                }catch(Exception e){
                    logger.error("exception occurred while fetching activities from stream ", e);
                }
            }else{
                logger.error("stream object is null while exporting activity");
            }
        }else{
            logger.error("stream resource object is null while exporting activity");
        }
    }
}
