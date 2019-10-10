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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public abstract class  UGCImport extends SlingAllMethodsServlet {

    Logger logger = LoggerFactory.getLogger(UGCImport.class);

    JSONObject getUpdateJsonObjectWithNewIDs(Map<String, String> idMap, JSONObject jsonObject)  {
        try {
            String objectId = jsonObject.optString(Constants.OBJECT_ID);

            if(StringUtils.isNotBlank(idMap.get(objectId))){
                jsonObject.put(Constants.OBJECT_ID,idMap.get(objectId) ) ;
            }

            //target id
            JSONObject map  = jsonObject.optJSONObject(Constants.TARGET) ;
            String referer = "";
            String Referer = "";
            String randomID = "";

            if(map != null ) {
                String oldId = map.optString(Constants.ID);
                String newId = idMap.get(oldId) != null ? idMap.get(oldId) : "";

                if (!StringUtils.isBlank(newId)) {
                    map.put(Constants.ID, newId);

                    String[] spits = StringUtils.split(newId, "/");
                    randomID = spits[spits.length -1];
                }

                map.put(Constants.LATESTACTIVITYPATH_S, objectId);

                referer = map.optString(Constants.Referer, "") +"/"+ randomID;
                Referer = map.optString(Constants.REFERER, "") +"/"+ randomID;


                JSONObject objectMap  = jsonObject.optJSONObject(Constants.OBJECT) ;
                if(objectMap != null ) {
                    oldId = objectMap.optString(Constants.ID);
                    newId = idMap.get(oldId) != null ? idMap.get(oldId) : "";

                    if (!StringUtils.isBlank(newId)) {
                        map.put(Constants.ID, newId);
                    }

                    if (!StringUtils.isBlank(referer)) {
                        objectMap.put(Constants.REFERER, referer);
                    }

                    if (!StringUtils.isBlank(Referer)) {
                        objectMap.put(Constants.REFERER, Referer);
                    }

                    String oldUrl = map.optString(Constants.URL);
                    map.put(Constants.URL, referer);

                    logger.info("target->  old url vs new Url[{}, {}]", oldUrl, referer);

                    oldUrl = objectMap.optString(Constants.URL);
                    logger.info("object->  old url vs new Url[{}, {}]", oldUrl);


                }
                jsonObject.put(Constants.OBJECT, objectMap) ;
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


     Map<String, LinkedList<String>> loadMetaInfo(final RequestParameter requestParam){
        Map<String,LinkedList<String>> idMap = new HashMap() ;

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
                    String keyValues[] = line.split("=");
                    String values[] = keyValues[1].split(",") ;
                    LinkedList<String> linkedList = new LinkedList<String>()  ;
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
           logger.error("error occured while importing ugc ",e);
       }
          return (toStart - start);

   }
}
