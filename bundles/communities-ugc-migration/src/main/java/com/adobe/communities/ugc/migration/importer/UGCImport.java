package com.adobe.communities.ugc.migration.importer;

import com.adobe.communities.ugc.migration.util.Constants;
import com.adobe.cq.social.activitystreams.api.SocialActivityManager;
import com.adobe.cq.social.activitystreams.listener.api.ActivityStreamProvider;
import com.adobe.granite.activitystreams.Activity;
import com.adobe.granite.activitystreams.ActivityException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import com.adobe.granite.activitystreams.ActivityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;


public abstract class  UGCImport extends SlingAllMethodsServlet {

    private final Logger logger = LoggerFactory.getLogger(UGCImport.class);

    private JSONObject getUpdateJsonObjectWithNewIDs(Map<String, Map<String,String>> idMap, JSONObject jsonObject)  {
        try {
            String objectId = jsonObject.optString(Constants.OBJECT_ID);

            //<id, url, referer>
            Map<String,String> objectNewMapping = getNewMapping(idMap, objectId);

            //target id
            JSONObject targetMap  = jsonObject.optJSONObject(Constants.TARGET) ;
            JSONObject objectMap  = jsonObject.optJSONObject(Constants.OBJECT) ;

            if(objectMap != null && targetMap != null){

                String newId = objectNewMapping.get(Constants.NEW_ID);
                String newUrl = objectNewMapping.get(Constants.ENTITY_URL);
                String referer = objectNewMapping.get(Constants.REFERER);

                if(StringUtils.isNotBlank(newId)){
                    jsonObject.put(Constants.OBJECT_ID, newId) ;
                    objectMap.put(Constants.ID, newId);

                    if(targetMap.has(Constants.LATESTACTIVITYPATH_S)){
                        targetMap.put(Constants.LATESTACTIVITYPATH_S, newId);
                    }
                }


                if(StringUtils.isNotBlank(newUrl)) {
                    objectMap.put(Constants.URL, findString(newUrl, Constants.CONTENT));
                }

                if(StringUtils.isNotBlank(referer)){
                    objectMap.put(Constants.REFERER, referer);
                    objectMap.put(Constants.Referer, referer);
                }

                String oldTargetId = targetMap.optString(Constants.ID);
                Map<String,String>  targetNewMapping = getNewMapping(idMap, oldTargetId);

                String targetId = targetNewMapping.get(Constants.NEW_ID);
                String targetUrl = targetNewMapping.get(Constants.ENTITY_URL);

                if (StringUtils.isNotBlank(targetId)) {
                    targetMap.put(Constants.ID, targetId);
                }

                if(StringUtils.isNotBlank(targetUrl)) {
                    targetMap.put(Constants.URL, findString(targetUrl, Constants.CONTENT));
                }

                jsonObject.put(Constants.OBJECT,objectMap) ;
                jsonObject.put(Constants.TARGET,targetMap) ;
            }else{
                logger.info("Below activity data is incomplete\n[{}], Hence ignoring it ", jsonObject);
            }

        } catch (JSONException e) {
            logger.error("Unable to map ids during import",e);
        }
        return jsonObject ;
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


     Map<String, Map<String,String>> loadMetaInfo(final RequestParameter requestParam){
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
                    String[] keyValues = line.split("=");
                    String values[] = keyValues[1].split(",") ;

                    Map<String,String> valuesMap = new HashMap<String, String>()  ;
                    valuesMap.put(Constants.NEW_ID,values[0]);
                    valuesMap.put(Constants.ENTITY_URL,values[1]);
                    valuesMap.put(Constants.REFERER,values[2]) ;
                    idMap.put(keyValues[0],valuesMap);
                }
            }
        }catch(Exception e){
            logger.error("Unable to load meta file",e);
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

    void importUGC(JSONArray activities, ActivityStreamProvider streamProvider, SocialActivityManager activityManager, Map<String,Map<String,String>> idMap, int start) {
       int  toStart = start;
       try {
           for (; toStart < activities.length(); toStart++) {
               JSONObject activityJson = activities.getJSONObject(toStart);
               JSONObject updatedJson = getUpdateJsonObjectWithNewIDs(idMap, activityJson);
               final Activity activity = activityManager.newActivity(updatedJson);
               if (activity != null) {
                   if (streamProvider.accept(activity)) {
                       logger.debug("Appending activity id {}, Object id : {} to streamProvider {} ", activity.getId(), activity.getObject().getId(),
                               streamProvider.getClass().getName());
                       streamProvider.append(activity);
                   }
               }
           }
       } catch (Exception e) {
           throw new ActivityException("error during import", e);
       } finally {
           logger.info("Activity import metrics[Start: {}, end: {}, imported: {}]", start, toStart, (toStart - start));
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
}
