/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * __________________
 *
 *  Copyright 2015 Adobe Systems Incorporated
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated and its
 * suppliers and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 **************************************************************************/
package com.adobe.communities.ugc.migration.importer;

import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import com.adobe.cq.social.commons.client.endpoints.OperationException;
import com.adobe.cq.social.qna.api.QnaPost;
import com.adobe.cq.social.qna.client.api.QnaPostSocialComponentFactory;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

import javax.activation.DataSource;
import javax.jcr.Session;
import java.util.List;
import java.util.Map;

@Component(label = "UGC Importer for Q&A Data",
        description = "Moves Q&A data within json files into the active SocialResourceProvider", specVersion = "1.0")
@Service(value = SlingAllMethodsServlet.class)
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/qna/import")})
public class QnaImportServlet extends ForumImportServlet {

    @Reference
    QnaPostSocialComponentFactory qnaPostSocialComponentFactory;

    @Override
    protected String getContentType() {
        return ContentTypeDefinitions.LABEL_QNA_FORUM;
    }

    @Override
    protected Resource createPost(final Resource resource, final String author, final Map<String, Object> properties,
                                  final List<DataSource> attachments, final Session session) throws OperationException {
        Resource post = super.createPost(resource, author, properties, attachments, session);
        final QnaPost qnaForum = resource.adaptTo(QnaPost.class);
        QnaPost qnaPost = (QnaPost) qnaPostSocialComponentFactory.getSocialComponent(post);
        return qnaForum.addPost(resource.getResourceResolver(), qnaPost).getResource();
    }
}
