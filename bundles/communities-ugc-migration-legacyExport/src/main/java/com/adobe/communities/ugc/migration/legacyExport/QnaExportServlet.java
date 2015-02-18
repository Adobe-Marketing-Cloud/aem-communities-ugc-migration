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
package com.adobe.communities.ugc.migration.legacyExport;

//import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;

@Component(label = "UGC Exporter for Qna Forum Data",
        description = "Moves qna data into json files for storage or re-import", specVersion = "1.0")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/qna/export")})
public class QnaExportServlet extends ForumExportServlet {
    
    @Override
    protected String getContentType() {
        return ContentTypeDefinitions.LABEL_QNA_FORUM;
    }
}
