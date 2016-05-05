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

package com.adobe.communities.ugc.migration;

public class ContentTypeDefinitions {
    public final static String NAMESPACE_PREFIX             = "ugcExport:";
    public final static String LABEL_CONTENT_TYPE           = NAMESPACE_PREFIX + "contentType";
    public final static String LABEL_CONTENT                = NAMESPACE_PREFIX + "content";
    public final static String LABEL_ATTACHMENTS            = NAMESPACE_PREFIX + "attachments";
    public final static String LABEL_TALLY                  = NAMESPACE_PREFIX + "tally";
    public final static String LABEL_FLAGS                  = NAMESPACE_PREFIX + "flags";
    public final static String LABEL_TRANSLATION            = NAMESPACE_PREFIX + "translation";
    public final static String LABEL_TRANSLATIONS           = NAMESPACE_PREFIX + "translations";
    public final static String LABEL_TIMESTAMP_FIELDS       = NAMESPACE_PREFIX + "timestampFields";
    public final static String LABEL_ENCODED_DATA           = NAMESPACE_PREFIX + "encodedData";
    public final static String LABEL_ENCODED_DATA_FIELDNAME = NAMESPACE_PREFIX + "encodedDataFieldName";
    public final static String LABEL_ERROR                  = NAMESPACE_PREFIX + "error";
    public final static String LABEL_SUBNODES               = NAMESPACE_PREFIX + "subNodes";
    public final static String LABEL_REPLIES                = NAMESPACE_PREFIX + "replies";

    public final static String LABEL_FORUM                  = "forum";
    public final static String LABEL_QNA_FORUM              = "qnaForum";
    public final static String LABEL_COMMENTS               = "comments";
    public final static String LABEL_CALENDAR               = "calendar";
    public final static String LABEL_JOURNAL                = "journal";
    public final static String LABEL_FILELIBRARY            = "fileLibrary";
}
