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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestCommentExport {

    private SocialResource resource;
    private SocialUtils socialUtils;
    private ResourceResolver resolver;
    private Comment post;
    private Writer responseWriter;
    final private Calendar now = new GregorianCalendar();
    private SocialResourceProvider srp;

    @Before
    public void setUp() throws Exception {
        // create mocks
        resource = Mockito.mock(SocialResource.class);
        resolver = Mockito.mock(ResourceResolver.class);
        Mockito.when(resource.getResourceResolver()).thenReturn(resolver);
        socialUtils = Mockito.mock(SocialUtils.class);
        responseWriter = Mockito.mock(Writer.class);
        post = Mockito.mock(Comment.class);
        srp = Mockito.mock(SocialResourceProvider.class);
        Mockito.when(resolver.getResource(Matchers.anyString())).thenReturn(resource);
        Mockito.when(resource.getResourceProvider()).thenReturn(srp);

    }

    @After
    public void tearDown() {

    }

    private Map<String, Object> getSimpleDoc() {
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
        return map;
    }

    @Test
    public void exportSimpleComment() throws JSONException, IOException {
        Iterable<Resource> children = new ArrayList<Resource>();
        Mockito.when(resource.getChildren()).thenReturn(children);
        Mockito.when(post.getComments()).thenReturn(Collections.<Comment>emptyIterator());
        Map<String, Object> map = getSimpleDoc();
        ValueMap props = new ValueMapDecorator(map);
        Mockito.when(post.getProperties()).thenReturn(props);

        JSONWriter writer = new JSONStringer();
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

    @Test
    public void exportCommentWithFlags() throws JSONException, IOException {

        final ArrayList<Resource> children = new ArrayList<Resource>();
        Mockito.when(post.getComments()).thenReturn(Collections.<Comment>emptyIterator());
        final Map<String, Object> map = getSimpleDoc();
        // add flag
        map.put("isFlagged", true);
        map.put(Comment.PROP_FLAG_ALLOW_COUNT, 0);
        final Resource flag1 = Mockito.mock(Resource.class);
        Map<String, Object> flag1Map = new LinkedHashMap<String, Object>();
        flag1Map.put("social:baseType", "social/tally/components/voting");
        flag1Map.put("social:flagReason", "testing");
        flag1Map.put("added", now);
        flag1Map.put("userIdentifier", "testUser1");
        flag1Map.put("response", -1);
        flag1Map.put("author_display_name", "First Test User");
        Mockito.when(flag1.adaptTo(ValueMap.class)).thenReturn(new ValueMapDecorator(flag1Map));
        Mockito.when(flag1.getName()).thenReturn("testUser1");
        final ArrayList<Resource> flagChildren = new ArrayList<Resource>();
        flagChildren.add(flag1);
        final Resource flagResource = Mockito.mock(Resource.class);
        Mockito.when(flagResource.isResourceType("social/tally/components/voting")).thenReturn(true);
        Mockito.when(flagResource.getName()).thenReturn("flags_0");
        Mockito.when(flagResource.getPath()).thenReturn(map.get("id") + "/flags_0");
        Mockito.when(flagResource.getResourceResolver()).thenReturn(resolver);
        Mockito.when(flagResource.hasChildren()).thenReturn(true);
        Mockito.when(flagResource.getChildren()).thenReturn(flagChildren);
        children.add(flagResource);
        Mockito.when(resource.hasChildren()).thenReturn(true);
        Mockito.when(resource.getChildren()).thenReturn(children);

        final ValueMap props = new ValueMapDecorator(map);
        Mockito.when(post.getProperties()).thenReturn(props);

        final JSONWriter writer = new JSONStringer();
        UGCExportHelper.extractComment(writer.object(), post, resolver, responseWriter, socialUtils);
        writer.endObject();
        final String mystring = writer.toString();
        Assert.assertEquals("exported comment with flags didn't match expected value",
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
            "\"isFlagged\":\"true\"," +
            "\"ugcExport:timestampFields\":[\"added\"]," +
            "\"ugcExport:flags\":[" +
                    "{\"timestamp\":" + now.getTime().getTime() + "," +
                    "\"response\":\"-1\"," +
                    "\"author_username\":\"testUser1\"," +
                    "\"author_display_name\":\"First+Test+User\"," +
                    "\"social:flagReason\":\"testing\"}" +
                "]" +
            "}",
            mystring);
    }

    @Test
    public void exportCommentWithVotes() throws JSONException, IOException {

        final ArrayList<Resource> children = new ArrayList<Resource>();
        Mockito.when(post.getComments()).thenReturn(Collections.<Comment>emptyIterator());
        final Map<String, Object> map = getSimpleDoc();

        // add vote
        final Resource voteResource = Mockito.mock(Resource.class);
        Mockito.when(voteResource.getResourceResolver()).thenReturn(resolver);
        Mockito.when(voteResource.isResourceType("social/tally/components/hbs/voting")).thenReturn(true);
        Mockito.when(voteResource.hasChildren()).thenReturn(true);

        final ArrayList<Resource> voteChildren = new ArrayList<Resource>();
        final Resource vote1Child = Mockito.mock(Resource.class);
        final Map<String, Object> vote1Map = new HashMap<String, Object>();
        vote1Map.put("social:baseType", "social/tally/components/hbs/voting");
        vote1Map.put("added", now);
        vote1Map.put("userIdentifier", "testUser1");
        vote1Map.put("response", -1);
        vote1Map.put("author_display_name", "First Test User");
        final ValueMap vm1 = new ValueMapDecorator(vote1Map);
        Mockito.when(vote1Child.adaptTo(ValueMap.class)).thenReturn(vm1);
        voteChildren.add(vote1Child);

        Mockito.when(voteResource.getChildren()).thenReturn(voteChildren);
        children.add(voteResource);
        Mockito.when(resource.hasChildren()).thenReturn(true);
        Mockito.when(resource.getChildren()).thenReturn(children);

        final ValueMap props = new ValueMapDecorator(map);
        Mockito.when(post.getProperties()).thenReturn(props);

        final JSONWriter writer = new JSONStringer();
        UGCExportHelper.extractComment(writer.object(), post, resolver, responseWriter, socialUtils);
        writer.endObject();
        final String mystring = writer.toString();
        Assert.assertEquals("exported comment with flags didn't match expected value",
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
            "\"ugcExport:timestampFields\":[\"added\"]," +
            "\"ugcExport:tally\":[" +
                    "{\"timestamp\":" + now.getTime().getTime() + "," +
                    "\"response\":\"-1\"," +
                    "\"userIdentifier\":\"testUser1\"," +
                    "\"tallyType\":\"Voting\"}" +
                "]" +
            "}", mystring);
    }
}
