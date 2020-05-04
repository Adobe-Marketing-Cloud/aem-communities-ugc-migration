package com.adobe.communities.ugc.migration.importer;


import com.adobe.communities.ugc.migration.util.Constants;
import com.adobe.cq.social.activitystreams.api.SocialActivityManager;
import com.adobe.cq.social.activitystreams.listener.api.ActivityStreamProvider;
import com.adobe.granite.activitystreams.ActivityException;
import org.apache.felix.scr.annotations.*;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component(label = "UGC Migration Notification Importer",
        description = "Accepts a json file containing notification data and applies it to stored  profiles")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/notification/import")})
public class NotificationImportServlet extends  UGCImport {

    @Reference
    private ResourceResolverFactory rrf;

    @Reference
    private SocialActivityManager activityManager;

    @Reference
    private EventAdmin eventAdmin;

    @Reference(target = "(component.name=com.adobe.cq.social.notifications.impl.NotificationsActivityStreamProvider)")
    private ActivityStreamProvider streamProvider;

    private static final Logger logger = LoggerFactory.getLogger(ActivityImportServlet.class);

    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException, ServletException {

        //allowed only if user is admin
        final ResourceResolver resolver = request.getResourceResolver();
        UGCImportHelper.checkUserPrivileges(resolver, rrf);

        RequestParameterMap paramMap = request.getRequestParameterMap();

        if(paramMap.size()<3){
            throw new ActivityException("Unable to import notification, missing input data");
        }

        //read meta data
        RequestParameter metaFileParam = paramMap.getValue(Constants.ID_MAPPING_FILE);
        Map<String, Map<String,String>> idMap = loadKeyMetaInfo(metaFileParam);

        List<Integer> toImportNoti = loadSkippedMetaInfo(Constants.SKIPPED_NOTIFICATION);

        //read exported data
        RequestParameter dataFile = paramMap.getValue(Constants.DATA_FILE);
        String jsonBody = loadData(dataFile);

        RequestParameter startParam = paramMap.getValue(Constants.OFFSET);
        final int start  = startParam !=null ?Integer.parseInt(startParam.getString()):0;

        JSONObject json;
        try {
            json = new JSONObject(jsonBody);
            JSONArray notifications =json.optJSONArray(Constants.NOTIFICATIONS) != null
                    ? json.optJSONArray(Constants.NOTIFICATIONS)
                    : new JSONArray();

            setEventAdmin(eventAdmin);
            importUGC(notifications, streamProvider, activityManager, idMap, toImportNoti, start, Constants.SKIPPED_NOTIFICATION, resolver);
        } catch (Exception e) {
            logger.error("Error during notification import", e.getCause());
        }
    }

}
