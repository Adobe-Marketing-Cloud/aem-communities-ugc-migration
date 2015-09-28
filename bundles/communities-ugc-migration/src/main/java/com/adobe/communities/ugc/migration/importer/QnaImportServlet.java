/*************************************************************************
 *
 * ADOBE SYSTEMS INCORPORATED
 * Copyright 2015 Adobe Systems Incorporated
 * All Rights Reserved.
 *
 * NOTICE:  Adobe permits you to use, modify, and distribute this file in accordance with the
 * terms of the Adobe license agreement accompanying it.  If you have received this file from a
 * source other than Adobe, then your use, modification, or distribution of it requires the prior
 * written permission of Adobe.
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
