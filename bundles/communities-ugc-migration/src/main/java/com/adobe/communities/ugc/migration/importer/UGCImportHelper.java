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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;

import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import org.apache.commons.codec.binary.Base64;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import javax.jcr.Node;

public class UGCImportHelper {

    public static void extractResource(final JsonParser parser, final SocialResourceProvider provider)
            throws JsonParseException, IOException {
        provider
        final ValueMap vm = resource.getValueMap();

        while(parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.getCurrentName();
            parser.nextToken();
            JsonToken token = parser.getCurrentToken();

            if (name.equals(ContentTypeDefinitions.LABEL_SUBNODES)) {
                if (token.equals(JsonToken.START_OBJECT)) {
                    parser.nextToken();
                    String relPath = parser.getCurrentName();
                    Node resourceNode = resource.adaptTo(Node.class);
                    resourceNode.addNode(relPath);
                }
            }
        }
    }
}