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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;

import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import com.adobe.cq.social.commons.comments.endpoints.CommentOperations;
import com.adobe.cq.social.qna.client.endpoints.QnaForumOperations;

@Component(label = "UGC Importer for QnA Data",
        description = "Moves QnA data within json files into the active SocialResourceProvider", specVersion = "1.1",
        immediate = true)
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/qna/import")})
public class QnaImportServlet extends ForumImportServlet {

    @Reference
    private QnaForumOperations qnaForumOperations;

    protected String getContentType() {
        return ContentTypeDefinitions.LABEL_QNA_FORUM;
    }

    @Override
    protected CommentOperations getOperationsService() {
        return qnaForumOperations;
    }
}
