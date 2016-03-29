package com.adobe.communities.ugc.migration.export;

import com.adobe.cq.social.commons.Comment;
import com.adobe.cq.social.srp.SocialResource;
import com.adobe.cq.social.srp.SocialResourceProvider;
import com.adobe.cq.social.ugcbase.SocialUtils;
import junit.framework.Assert;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONStringer;
import org.apache.sling.commons.json.io.JSONWriter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestCommentExport {

    private SocialResource resource;
    private SocialUtils socialUtils;
    private ResourceResolver resolver;
    private JSONWriter writer;
    private Comment post;
    private Writer responseWriter;
    private SocialResourceProvider srp;
    private Iterable<Resource> children;
    private Calendar now = new GregorianCalendar();

    @Before
    public void setUp() throws Exception {
        // create mocks
        resource = Mockito.mock(SocialResource.class);
        resolver = Mockito.mock(ResourceResolver.class);
        Mockito.when(resource.getResourceResolver()).thenReturn(resolver);
        socialUtils = Mockito.mock(SocialUtils.class);
        writer = new JSONStringer();
        responseWriter = Mockito.mock(Writer.class);
        post = Mockito.mock(Comment.class);
        srp = Mockito.mock(SocialResourceProvider.class);
        Mockito.when(resolver.getResource(Matchers.anyString())).thenReturn(resource);
        Mockito.when(resource.getResourceProvider()).thenReturn(srp);
        children = new ArrayList<Resource>();
        Mockito.when(resource.getChildren()).thenReturn(children);
        Mockito.when(post.getComments()).thenReturn(Collections.<Comment>emptyIterator());
        Map<String, Object> map = new LinkedHashMap<String, Object>(); // need LinkedHashMap for predictable ordering
        map.put("resource_type_s", "social/forum/components/hbs/post");
        map.put("jcr:description", "hello world");
        map.put("sentiment",5);
        map.put("referer","/content/community-components/en/forum.html");
        map.put("social:parentid","/content/community-components/en/forum/jcr:content/content/forum");
        map.put("authorizableId","admin");
        map.put("added", now);
        map.put("jcr:title","hello world");
        map.put("approved",true);
        map.put("social:key","/content/usergenerated/asi/mongo/content/community-components/en/forum/jcr:content/" +
                "content/forum/jceq-hello");
        map.put("social:baseType","social/commons/components/comments/comment");
        map.put("social:isReply",false);
        map.put("userIdentifier","admin");
        map.put("id","/content/usergenerated/asi/mongo/content/community-components/en/forum/jcr:content/content/" +
                "forum/jceq-hello");
        map.put("social:rootCommentSystem","/content/community-components/en/forum/jcr:content/content/forum");
        map.put("social:entity","/content/community-components/en/forum.topic.html/jceq-hello.html");
        map.put("isSpam",false);
        map.put("eventTopic","forum");
        ValueMap props = new ValueMapDecorator(map);
        Mockito.when(post.getProperties()).thenReturn(props);
    }

    @After
    public void tearDown() {

    }

    @Test
    public void exportComment() throws JSONException, IOException {
        UGCExportHelper.extractComment(writer.object(), post, resolver, responseWriter, socialUtils);
        writer.endObject();
        String mystring = writer.toString();
        Assert.assertEquals("exported comment didn't match expected value",
            "{\"resource_type_s\":\"social%2Fforum%2Fcomponents%2Fhbs%2Fpost\"," +
            "\"jcr:description\":\"hello+world\"," +
            "\"sentiment\":\"5\"," +
            "\"referer\":\"%2Fcontent%2Fcommunity-components%2Fen%2Fforum.html\"," +
            "\"social:parentid\":\"%2Fcontent%2Fcommunity-components%2Fen%2Fforum%2Fjcr%3Acontent%2Fcontent%2Fforum\"," +
            "\"authorizableId\":\"admin\"," +
            "\"added\":" + now.getTime().getTime() + "," +
            "\"jcr:title\":\"hello+world\"," +
            "\"approved\":\"true\"," +
            "\"social:key\":\"%2Fcontent%2Fusergenerated%2Fasi%2Fmongo%2Fcontent%2Fcommunity-components%2Fen%2Fforum%2Fjcr%3Acontent%2Fcontent%2Fforum%2Fjceq-hello\"," +
            "\"social:baseType\":\"social%2Fcommons%2Fcomponents%2Fcomments%2Fcomment\"," +
            "\"social:isReply\":\"false\"," +
            "\"userIdentifier\":\"admin\"," +
            "\"id\":\"%2Fcontent%2Fusergenerated%2Fasi%2Fmongo%2Fcontent%2Fcommunity-components%2Fen%2Fforum%2Fjcr%3Acontent%2Fcontent%2Fforum%2Fjceq-hello\"," +
            "\"social:rootCommentSystem\":\"%2Fcontent%2Fcommunity-components%2Fen%2Fforum%2Fjcr%3Acontent%2Fcontent%2Fforum\"," +
            "\"social:entity\":\"%2Fcontent%2Fcommunity-components%2Fen%2Fforum.topic.html%2Fjceq-hello.html\"," +
            "\"isSpam\":\"false\"," +
            "\"eventTopic\":\"forum\"," +
            "\"ugcExport:timestampFields\":[\"added\"]}",
            mystring);
    }
}
