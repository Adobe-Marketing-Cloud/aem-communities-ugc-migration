package com.adobe.communities.ugc.migration.importer;

import com.adobe.communities.ugc.migration.util.Constants;
import com.adobe.cq.social.activitystreams.api.SocialActivityManager;
import com.adobe.cq.social.activitystreams.listener.api.ActivityStreamProvider;
import com.adobe.granite.activitystreams.Activity;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public abstract class  UGCImport extends SlingAllMethodsServlet {

    private final Logger logger = LoggerFactory.getLogger(UGCImport.class);

    private JSONObject getUpdateJsonObjectWithNewIDs(Map<String, List<String>> idMap, JSONObject jsonObject)  {
        try {
            String objectId = jsonObject.optString(Constants.OBJECT_ID);

            //<id, url, referer>
            List<String> objectNewMapping = getNewMapping(idMap, objectId);

            //target id
            JSONObject targetMap  = jsonObject.optJSONObject(Constants.TARGET) ;
            JSONObject objectMap  = jsonObject.optJSONObject(Constants.OBJECT) ;

            if(objectMap != null && targetMap != null){

                String newId = objectNewMapping.get(0);
                String newUrl = objectNewMapping.get(1);

                if(StringUtils.isNotBlank(newId)){
                    jsonObject.put(Constants.OBJECT_ID, newId) ;
                    objectMap.put(Constants.ID, newId);

                    if(targetMap.has(Constants.LATESTACTIVITYPATH_S)){
                        targetMap.put(Constants.LATESTACTIVITYPATH_S, newId);
                    }
                }

                if(StringUtils.isNotBlank(newUrl)) {
                    objectMap.put(Constants.URL, newUrl);
                }


                String targetId = targetMap.optString(Constants.ID);
                List<String> targetNewMapping = getNewMapping(idMap, targetId);

                targetId = targetNewMapping.get(0);
                String targetUrl = targetNewMapping.get(1);
                if (StringUtils.isNotBlank(targetId)) {
                    targetMap.put(Constants.ID, targetId);
                }

                if(StringUtils.isNotBlank(targetUrl)) {
                    targetMap.put(Constants.URL, targetUrl);
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


     Map<String, List<String>> loadMetaInfo(final RequestParameter requestParam){
        Map<String,List<String>> idMap = new HashMap() ;

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
                    //tODO : have apply loop
                    List<String> linkedList = new LinkedList<String>()  ;
                    linkedList.add(values[0]) ;
                    linkedList.add(values[1]) ;
                    idMap.put(keyValues[0],linkedList);
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

    int importUGC(JSONArray activities, ActivityStreamProvider streamProvider, SocialActivityManager activityManager, Map<String,List<String>> idMap, int start) {
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
       } catch (JSONException e) {
           logger.error("error occurred while importing ugc ",e);
       }
          return (toStart - start);

   }

   private List<String> getNewMapping(Map<String, List<String>> idMap, String key){
       //<id, url, referer>
      return  idMap.get(key);
   }
}
