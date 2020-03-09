package com.adobe.communities.ugc.migration.importer;

import com.adobe.communities.ugc.migration.util.Constants;
import com.adobe.cq.social.activitystreams.api.SocialActivityEventConstants;
import com.adobe.cq.social.activitystreams.api.SocialActivityManager;
import com.adobe.cq.social.activitystreams.listener.api.ActivityStreamProvider;
import com.adobe.cq.social.notifications.api.NotificationConstants;
import com.adobe.cq.social.notifications.api.Status;
import com.adobe.cq.social.notifications.client.api.SocialNotification;
import com.adobe.cq.social.notifications.impl.NotificationsActivityStreamProvider;
import com.adobe.granite.activitystreams.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;


public abstract class  UGCImport extends SlingAllMethodsServlet {

    private final Logger logger = LoggerFactory.getLogger(UGCImport.class);

    private EventAdmin eventAdmin;

    protected  void setEventAdmin(EventAdmin ead) {
        eventAdmin = ead;
    }


    private JSONObject getUpdateJsonObjectWithNewIDs(Map<String, Map<String,String>> idMap, JSONObject jsonObject)  {
        try {
            String objectId = jsonObject.optString(Constants.OBJECT_ID);

            //need to update target old(msrp) vs new(asrp)
            JSONObject targetMap  = jsonObject.optJSONObject(Constants.TARGET) ;
            JSONObject objectMap  = jsonObject.optJSONObject(Constants.OBJECT) ;

            if(objectMap != null && targetMap != null){

                //key.txt has ugc ids only, for voting need to append voting after fetching new id
                String objectSubType = objectMap.optString("subType");
                if(objectSubType.equals("social/tally/components/hbs/voting")){
                    objectId = StringUtils.removeEnd(objectId, "/voting");
                }

                //<id, url, referer>
                Map<String,String> objectNewMapping = getNewMapping(idMap, objectId);

                //fetch new id, url and referer
                String newId = objectNewMapping.get(Constants.NEW_ID);
                String newUrl = objectNewMapping.get(Constants.ENTITY_URL);
                String referer = objectNewMapping.get(Constants.REFERER);

                //if any ugc is not imported then key.txt wil not contain the new ids and so no need to import related activities and notification
                if(StringUtils.isNotBlank(newId)) {
                    if(objectSubType.equals("social/tally/components/hbs/voting")) {
                        newId = newId + "/voting";
                    }
                    jsonObject.put(Constants.OBJECT_ID, newId);
                    objectMap.put(Constants.ID, newId);

                    if (targetMap.has(Constants.LATESTACTIVITYPATH_S)) {
                        targetMap.put(Constants.LATESTACTIVITYPATH_S, newId);
                    }

                    if (StringUtils.isNotBlank(newUrl)) {
                        objectMap.put(Constants.URL, findString(newUrl, Constants.CONTENT));
                    }

                    if (StringUtils.isNotBlank(referer)) {
                        if(objectMap.has(Constants.REFERER))  objectMap.put(Constants.REFERER, referer);
                        if(objectMap.has(Constants.Referer)) objectMap.put(Constants.Referer, referer);
                    }

                    String oldTargetId = targetMap.optString(Constants.ID);
                    Map<String, String> targetNewMapping = getNewMapping(idMap, oldTargetId);

                    String targetId = targetNewMapping.get(Constants.NEW_ID);
                    String targetUrl = targetNewMapping.get(Constants.ENTITY_URL);
                    String targetReferer = targetNewMapping.get(Constants.REFERER);

                    if (StringUtils.isNotBlank(targetId)) {
                        targetMap.put(Constants.ID, targetId);
                    }

                    if (StringUtils.isNotBlank(targetUrl)) {
                        targetMap.put(Constants.URL, findString(targetUrl, Constants.CONTENT));
                    }

                    if (StringUtils.isNotBlank(targetReferer)) {
                        if(targetMap.has(Constants.REFERER))  targetMap.put(Constants.REFERER, targetReferer);
                        if(targetMap.has(Constants.Referer)) targetMap.put(Constants.Referer, targetReferer);
                    }

                    jsonObject.put(Constants.OBJECT, objectMap);
                    jsonObject.put(Constants.TARGET, targetMap);

                }else{
                    return null;
                }
            }else{
                return null;
            }
            return jsonObject;

        } catch (Exception e) {
            logger.error("Unable to map ids during import[{}] \n {}",jsonObject, e);
            throw new ActivityException("error during import", e);
        }
    }

    String loadData(final RequestParameter request){
        String jsonBody = "";
        InputStream inputStream = null;
        try {
            //  keyValueMAp = new HashMap<String, String>() ;
            if (request != null ) {
                inputStream = request.getInputStream();
                //if null exception will be thrown
                jsonBody =  IOUtils.toString(inputStream, Charset.defaultCharset());
            }
        }catch(Exception e){
            logger.error("Unable to load data during import",e);
        }finally{

            if(inputStream != null)
                IOUtils.closeQuietly(inputStream);
        }
        return jsonBody;
    }


    Map<String, Map<String,String>> loadKeyMetaInfo(final RequestParameter requestParam){
        Map<String,Map<String,String>> idMap = new HashMap() ;

        InputStream inputStream = null;
        DataInputStream dataInputStream = null;
        BufferedReader br = null;
        try {
            if (requestParam != null ) {
                inputStream =requestParam.getInputStream();
                dataInputStream = new DataInputStream(inputStream);
                br = new BufferedReader(new InputStreamReader(dataInputStream));
                String line;
                while ((line = br.readLine()) != null) {
                    String[] keyValues = line.split(Constants.KEY_SPLITTER);
                    String values[] = keyValues[1].split(",") ;

                    Map<String,String> valuesMap = new HashMap<String, String>()  ;
                    for(String value : values){
                        if(StringUtils.isNotBlank(value)) {
                            String stringValues[] = value.split(Constants.VALUE_SPLIITTER);
                            if(stringValues.length == 2) {
                                valuesMap.put(stringValues[0], stringValues[1]);
                            }
                        }
                    }
                    idMap.put(keyValues[0], valuesMap);
                }
            }
        }catch(Exception e){
            logger.error("Unable to load keys mapping",e);
        }finally {
            if(br != null)
                IOUtils.closeQuietly(br);

            if(dataInputStream != null)
                IOUtils.closeQuietly(dataInputStream);

            if(inputStream != null)
                IOUtils.closeQuietly(inputStream);
        }
        return idMap;
    }

    List<Integer> loadSkippedMetaInfo(String filename){
        List<Integer> toImport = new ArrayList<Integer>() ;

        File f = new File(filename);

        BufferedReader br = null;
        FileReader fileReader = null;
        try {
            if(f.exists()){
                logger.info("file " + f.getAbsolutePath());
                fileReader = new FileReader(f.getAbsolutePath());
                br = new BufferedReader(fileReader);
                String line;
                while ((line = br.readLine()) != null) {
                    toImport.add(Integer.parseInt(line));
                }
            }
        }catch(Exception e){
            logger.error("Unable to load skipped items ",e);
        }finally {
            if(br != null)
                IOUtils.closeQuietly(br);

            if(fileReader != null)
                IOUtils.closeQuietly(fileReader);

        }
        return toImport;
    }

    void importUGC(JSONArray activities, ActivityStreamProvider streamProvider, SocialActivityManager activityManager, Map<String, Map<String, String>> idMap, List<Integer> importInfo, int offset, String filename, ResourceResolver resolver) {

        int skipped = 0;
        int processedCount  =0;

        //maintain skipped item in current processing
        ArrayList<Integer> skippedItem = new ArrayList<Integer>();

        String type = getType(filename);

        File file = null;
        try {
            file= new File(filename);

            if(file.exists()) file.delete();

            //everytime, new file will be creaed
            boolean created = file.createNewFile();

            for (int current = 0; current < activities.length(); current++) {

                //process when, either skippedlist is empty(fresh import) or data skipped in previous import
                if ((importInfo.isEmpty() && (current >= offset)) || importInfo.contains(current)) {
                    processedCount++;
                    JSONObject activityJson = activities.getJSONObject(current);
                    JSONObject updatedJson = getUpdateJsonObjectWithNewIDs(idMap, activityJson);
                    if (updatedJson != null) {
                        final Activity activity = activityManager.newActivity(updatedJson);
                        if (activity != null) {
                            if (streamProvider.accept(activity)) {
                                logger.debug("Appending activity id {}, Object id {} to streamProvider {}. Activity Before import[{}]\n, During processing[{}]\n", activity.getId(), activity.getObject().getId(),
                                        streamProvider.getClass().getSimpleName(), activityJson, updatedJson);
                                if (Constants.ACTIVITY.equals(type)) {
                                    streamProvider.append(activity);
                                } else if (Constants.NOTIFICATION.equals(type)) {
                                    String toNotify = getUserId(activity);
                                    if (StringUtils.isNotBlank(toNotify)) {
                                        UserManager userManager = resolver.adaptTo(UserManager.class);
                                        Authorizable authorizable = userManager.getAuthorizable(toNotify);
                                        if (authorizable != null && !authorizable.isGroup()) {
                                            appendNotification(activity, activityManager, authorizable, resolver, toNotify);
                                        }else{
                                            logger.error("Below user id[{}] is not authorized, hence ignoring below Json[{}]\n", toNotify, activity);
                                        }
                                    } else {
                                        logger.debug("Missing toNotify userId. Hence ignoring below Json [{}]\n", activity);
                                    }
                                }
                            }
                        } else {
                            logger.warn("[Skipped] Either below activity[{}] data is incomplete or corresponding UGC is not imported, Hence ignoring it \n", activityJson);
                            skippedItem.add(current);
                            skipped++;
                        }
                        if (!importInfo.isEmpty())
                            importInfo.remove((Integer) current);
                    }
                }
            }
        } catch (RepositoryException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (JSONException ex) {
            ex.printStackTrace();
        } catch (Exception e) {
        throw new ActivityException("error during import", e);
    } finally {
        try{
            if(!skippedItem.isEmpty())
                FileUtils.writeLines(file,  skippedItem);
        } catch (IOException e) {
            e.printStackTrace();
        }

        logger.info("{} import metrics[Total imports: {}, skipped: {}, next iteration offset: {}]", getType(filename), (processedCount - skipped), skipped, ((offset + processedCount) > activities.length()) ? activities.length() : (offset+ processedCount));
    }

}

    private Map<String,String> getNewMapping(Map<String, Map<String,String>> idMap, String key){
        //<id, url, referer>
        Map<String, String> map = idMap.get(key);
        return map != null ? map:new HashMap<String, String>();
    }

    private String findString(String str, String pattern){
        int index = str.indexOf(pattern);
        if(index != -1){
            str = str.substring(index);
        }
        return str;
    }
    private String getType(String filename){
        String type = "Activity";
        if(filename.contains("Notification"))
            type =  "Notification";
        return type;
    }

    private String getUserId(Activity activity) {
        if (activity.getObject() != null) {
            ValueMap prop = activity.getObject().getProperties();
            if (prop != null) {
                return prop.get("toNotify", String.class);
            }
        }

        return "";
    }

    private void appendNotification(Activity activity, ActivityManager activityManager, Authorizable authorizable, ResourceResolver resolver, String toNotify) throws RepositoryException {

            final String[] channels = new String[1];
            channels[0] = "web";//defaultPreferencesManager.getChannels(SubscriptionType.NOTIFICATIONS).toArray(new String[0]);
                                           /* ActivityStreamUtils.prepareStreamAcl(resolver, socialUtils, toNotify);
                                            ActivityStreamUtils.getStream(activityManager, resolver, toNotify, true);*/
            final ActivityStream stream =
                    activityManager.getStream(resolver, authorizable, NotificationsActivityStreamProvider.DEFAULT_USER_ACTIVITIES_STREAM_NAME, true);
            if (stream != null) {
                // Add notification extra property into the activity
                MutableActivity mutableActivity;
                if (activity instanceof MutableActivity) {
                    mutableActivity = (MutableActivity) activity;
                } else {
                    mutableActivity = activity.getMutableActivity();
                }
                String status = (String) mutableActivity.getProperties().get(NotificationConstants.NOTIFICATION_STATUS_PROP,
                        Status.UNREAD.name());
                mutableActivity.setProperty(NotificationConstants.NOTIFICATION_STATUS_PROP,
                        status);
                mutableActivity
                        .setProperty(Constants.SLING_RESOURCETYPE_PROPERTY, SocialNotification.RESOURCE_TYPE);
                mutableActivity.setProperty(NotificationConstants.NOTIFICATION_CHANNELS_PROP, channels);
                final Activity socialActivity = stream.append(mutableActivity);
                onAppend(socialActivity, channels, toNotify);
            }
        }


    protected void onAppend(final Activity activity, final String channels[], final String userId) {
        if(eventAdmin != null){
            try {

                // Notifies channel router that there is a new activity for them to process
                final Dictionary<String, Object> props = new Hashtable<String, Object>();
                if (StringUtils.isNotEmpty(activity.getId())) {
                    props.put(ActivityEventConstants.PROPERTY_ID, activity.getId());
                }
                if (StringUtils.isNotEmpty(activity.getPath())) {
                    props.put(NotificationConstants.NOTIFICATION_PATH_PROP, activity.getPath());
                }
                if (channels != null) {
                    props.put(NotificationConstants.EVENT_CHANNELS_PROP, channels);
                }
                if (StringUtils.isNotEmpty(activity.getVerb())) {
                    props.put(NotificationConstants.VERB_PROP, activity.getVerb());
                }
                if (activity.getTarget() != null) {
                    props.put(NotificationConstants.TARGET_ID_PROP, activity.getTarget().getId());
                }
                props.put(SocialActivityEventConstants.ACTIIVITY_COMPONENT_PROP, this.getClass().getName());
                props.put(NotificationConstants.USER_ID_PROP, userId);
                final Event event = new Event(NotificationConstants.TOPIC_SOCIAL_NOTIFICATIONS_ACTIVITY_ADDED, props);

                eventAdmin.postEvent(event);
            }
            catch (final Exception e) {
                logger.error("Failed to send message to router", e);
            }
        }
    }
}
