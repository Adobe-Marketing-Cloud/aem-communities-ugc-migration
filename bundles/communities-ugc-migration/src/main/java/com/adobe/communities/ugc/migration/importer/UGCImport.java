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
import java.util.Map;

public abstract class  UGCImport extends SlingAllMethodsServlet {

    Logger logger = LoggerFactory.getLogger(UGCImport.class);

     JSONObject getUpdateJsonObjectWithNewIDs(Map<String, String> idMap, JSONObject jsonObject){
        try {
            String objectId = jsonObject.optString(Constants.OBJECT_ID);

            if(StringUtils.isNotBlank(idMap.get(objectId))){
                jsonObject.put(Constants.OBJECT_ID,idMap.get(objectId) ) ;
            }

            JSONObject map  = jsonObject.optJSONObject(Constants.TARGET) ;
            if(map != null ){
                String oldId = map.optString(Constants.ID);
                String newId = idMap.get(oldId) != null ?idMap.get(oldId) :"" ;

                if(!StringUtils.isBlank(newId)) {
                    map.put(Constants.ID, newId);
                }
            }
            jsonObject.put(Constants.TARGET,map) ;
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
                //TODO : how to take care of locale
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


     Map<String, String> loadMetaInfo(final RequestParameter requestParam){
        Map<String,String> idMap = new HashMap() ;

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
                    String values[] = line.split("=");
                    idMap.put(values[0], values[1]);
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

    int importUGC(JSONArray activities, ActivityStreamProvider streamProvider, SocialActivityManager activityManager, Map<String,String> idMap, int start) {
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
           e.printStackTrace();
       }
          return (toStart - start);

   }
}
