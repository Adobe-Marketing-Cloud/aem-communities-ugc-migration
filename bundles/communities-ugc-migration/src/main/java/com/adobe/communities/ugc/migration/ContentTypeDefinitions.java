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

package com.adobe.communities.ugc.migration;

public class ContentTypeDefinitions {
    public final static String NAMESPACE_PREFIX             = "ugcExport:";
    public final static String LABEL_CONTENT_TYPE           = NAMESPACE_PREFIX + "contentType";
    public final static String LABEL_CONTENT                = NAMESPACE_PREFIX + "content";
    public final static String LABEL_ATTACHMENTS            = NAMESPACE_PREFIX + "attachments";
    public final static String LABEL_TIMESTAMP_FIELDS       = NAMESPACE_PREFIX + "timestampFields";
    public final static String LABEL_ENCODED_DATA           = NAMESPACE_PREFIX + "encodedData";
    public final static String LABEL_ENCODED_DATA_FIELDNAME = NAMESPACE_PREFIX + "encodedDataFieldName";
    public final static String LABEL_ERROR                  = NAMESPACE_PREFIX + "error";
    public final static String LABEL_SUBNODES               = NAMESPACE_PREFIX + "subNodes";
    public final static String LABEL_REPLIES                = NAMESPACE_PREFIX + "replies";


    public final static String LABEL_FORUM                  = "forum";
    public final static String LABEL_QNA_FORUM              = "qnaForum";
}
