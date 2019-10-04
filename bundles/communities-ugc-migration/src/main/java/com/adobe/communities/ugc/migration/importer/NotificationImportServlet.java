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
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

@Component(label = "UGC Migration Notification Importer",
        description = "Accepts a json file containing notification data and applies it to stored  profiles")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/notification/import")})
public class NotificationImportServlet extends  UGCImport {
    @Reference
    private SocialActivityManager activityManager;

    @Reference(target = "(component.name=com.adobe.cq.social.notifications.impl.NotificationsActivityStreamProvider)")
    private ActivityStreamProvider streamProvider;

    private static final Logger logger = LoggerFactory.getLogger(ActivityImportServlet.class);

    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException {

        RequestParameterMap paramMap = request.getRequestParameterMap();

        if(paramMap.size()<3){
            throw new ActivityException("Unable to import notification, missing input data");
        }

        //read meta data
        RequestParameter metaFileParam = paramMap.getValue(Constants.ID_MAPPING_FILE);
        Map<String,String> idMap = loadMetaInfo(metaFileParam);

        //read exported data
        RequestParameter dataFile = paramMap.getValue(Constants.DATA_FILE);
        String jsonBody = loadData(dataFile);

        RequestParameter startParam = paramMap.getValue(Constants.START);
        final int start  = startParam !=null ?Integer.parseInt(startParam.getString()):0;

        JSONObject json;
        int importCount = 0;
        try {
            json = new JSONObject(jsonBody);
            JSONArray notifications =json.optJSONArray(Constants.NOTIFICATION) != null
                                    ? json.optJSONArray(Constants.NOTIFICATION)
                                    : new JSONArray();

            importCount =  importUGC(notifications, streamProvider, activityManager, idMap, start);
        } catch (JSONException e) {
            e.printStackTrace();
        }finally{
            logger.info("Notification import metrics[Start: {}, end: {}, imported: {}]", start, (start+ importCount), importCount);
        }
    }

}
