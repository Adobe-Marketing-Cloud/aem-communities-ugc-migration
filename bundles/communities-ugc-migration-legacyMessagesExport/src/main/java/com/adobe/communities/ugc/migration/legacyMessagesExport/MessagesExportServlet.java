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
package com.adobe.communities.ugc.migration.legacyMessagesExport;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.jcr.RepositoryException;
import javax.servlet.ServletException;

import com.adobe.cq.social.messaging.client.endpoints.MessagingOperationsService;
import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.io.JSONWriter;

import com.adobe.cq.social.messaging.api.Message;
import com.adobe.cq.social.messaging.api.MessageFilter;
import com.adobe.cq.social.messaging.api.MessagingService;
import com.adobe.communities.ugc.migration.legacyExport.ContentTypeDefinitions;
import com.adobe.communities.ugc.migration.legacyExport.UGCExportHelper;

@Component(label = "Messages Exporter",
        description = "Moves messages into a zip archive for storage or re-import", specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/messages/export")})
public class MessagesExportServlet extends SlingSafeMethodsServlet {


    Writer responseWriter;
    ZipOutputStream zip;

    @Reference
    private MessagingService messagingService;

    private Map<String, Boolean> exportedIds;

    private Map<String, JSONObject> messagesForExport;

    private int counter = 0;

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/octet-stream");
        final String headerKey = "Content-Disposition";
        final String headerValue = "attachment; filename=\"export.zip\"";
        response.setHeader(headerKey, headerValue);
        File outFile = null;
        exportedIds = new HashMap<String, Boolean>();
        messagesForExport = new HashMap<String, JSONObject>();
        try {
            outFile = File.createTempFile(UUID.randomUUID().toString(), ".zip");
            if (!outFile.canWrite()) {
                throw new ServletException("Cannot write to specified output file");
            }
            FileOutputStream fos = new FileOutputStream(outFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            zip = new ZipOutputStream(bos);
            responseWriter = new OutputStreamWriter(zip);
            OutputStream outStream = null;
            InputStream inStream = null;
            try {

                int start = 0;
                int increment = 100;
                try {
                    do {
                        Iterable<Message> messages = messagingService.search(request.getResourceResolver(),
                                new MessageFilter(), start, start + increment);
                        if (messages.iterator().hasNext()) {
                            exportMessagesBatch(messages);
                        } else {
                            break;
                        }
                        start += increment;
                    } while (true);
                } catch (final RepositoryException e) {
                    // do nothing for now
                }
                IOUtils.closeQuietly(zip);
                IOUtils.closeQuietly(bos);
                IOUtils.closeQuietly(fos);
                // obtains response's output stream
                outStream = response.getOutputStream();
                inStream = new FileInputStream(outFile);
                // copy from file to output
                IOUtils.copy(inStream, outStream);
                IOUtils.closeQuietly(inStream);
                IOUtils.closeQuietly(outStream);
            } catch (final IOException e) {
                IOUtils.closeQuietly(zip);
                IOUtils.closeQuietly(bos);
                IOUtils.closeQuietly(fos);
                IOUtils.closeQuietly(inStream);
                IOUtils.closeQuietly(outStream);
                throw new ServletException(e);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        } finally {
            if (outFile != null) {
                outFile.delete();
            }
        }
    }


    private void exportMessagesBatch(final Iterable<Message> messages) throws JSONException,
            IOException {

        final JSONWriter writer = new JSONWriter(responseWriter);
        writer.setTidy(true);
        Boolean hasInitialized = false;
        for (final Message message : messages) {
            final Resource messageResource = message.adaptTo(Resource.class);
            if (exportedIds.containsKey(message.getId())) {
                if (!messagesForExport.containsKey(message.getId())) {
                    continue; // already exported (for example, a message with two recipients, both checked, now looking
                    // at the copy in the sender's outbox)
                }
                // we will always look at every message twice, since every message appears in an inbox and a sentitems
                // so, when we get here, we're looking at a message for the second time (or more, if the message has
                // multiple recipients). Our goal here is to fill in any missing recipient details, and, if this should
                // be the last time we look at a message, then write it to the output together with any attachments it
                // may have.
                final JSONObject messageObject = messagesForExport.get(message.getId());
                // check for an additional recipient
                boolean hasAllRecipients = true;
                final JSONObject recipientDetails = messageObject.getJSONObject("recipients");
                final Iterator<String> recipients = message.getRecipientIdList().listIterator();
                final String mailboxPath = messageResource.getPath();
                while (recipients.hasNext()) {
                    final String recipient = recipients.next();
                    if (!recipientDetails.has(recipient)) {
                        if (mailboxPath.contains(recipient)) {
                            final JSONObject recipientDetail = new JSONObject();
                            recipientDetail.put("read", message.isRead());
                            recipientDetail.put("deleted", message.isDeleted());
                            recipientDetails.put(recipient, recipientDetail);
                        } else {
                            hasAllRecipients = false;
                        }
                    }
                }
                if (hasAllRecipients) {
                    if (!hasInitialized) {
                        // we don't run this step unless at least one message is being written to a file
                        zip.putNextEntry(new ZipEntry("batch-" + counter + ".json"));
                        counter++;
                        writer.array();
                        hasInitialized = true;
                    }
                    // last time looking at a message, so write it to output together with any attachments
                    writer.object();
                    writeObject(writer, messageObject);
                    final Resource attachments = messageResource.getChild("attachments");
                    if (null != attachments) {
                        writer.key(ContentTypeDefinitions.LABEL_ATTACHMENTS);
                        final JSONWriter attachmentsWriter = writer.array();
                        for (final Resource attachment : attachments.getChildren()) {
                            UGCExportHelper.extractAttachment(responseWriter, attachmentsWriter.object(), attachment);
                            attachmentsWriter.endObject();
                        }
                        attachmentsWriter.endArray();
                    }
                    writer.endObject();
                    messagesForExport.remove(message.getId());
                }

                continue;
            }
            exportedIds.put(message.getId(), true);
            final JSONObject messageObject = new JSONObject();
            messageObject.put("content", URLEncoder.encode(message.getContent(), "UTF-8"));
            messageObject.put("subject", URLEncoder.encode(message.getSubject(), "UTF-8"));
            final Iterator<String> recipients = message.getRecipientIdList().listIterator();
            if (recipients.hasNext()) { //get each recipient and populate their "read" and "deleted" values
                final JSONObject recipientDetails = new JSONObject();
                final String mailboxPath = messageResource.getPath();
                while (recipients.hasNext()) {
                    final String recipient = recipients.next();
                    if (mailboxPath.contains(recipient)) {
                        final JSONObject recipientDetail = new JSONObject();
                        recipientDetail.put("read", message.isRead());
                        recipientDetail.put("deleted", message.isDeleted());
                        recipientDetails.put(recipient, recipientDetail);
                    }
                }
                messageObject.put("recipients", recipientDetails);
            }
            final Calendar timestamp = message.getTimestamp();
            messageObject.put("added", timestamp.getTime().getTime());
            messageObject.put("senderId", URLEncoder.encode(message.getSenderId(), "UTF-8"));
            messagesForExport.put(message.getId(), messageObject);
        }
        if (hasInitialized) {
            writer.endArray();
            responseWriter.flush();
            zip.closeEntry();
        }
    }

    private static void writeObject(final JSONWriter writer, final JSONObject object) throws JSONException {
        final Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            writer.key(key);
            final Object value = object.get(key);
            if (value instanceof JSONObject) {
                writer.object();
                writeObject(writer, (JSONObject) value);
                writer.endObject();
            } else {
                final String valString = value.toString();
                if (valString.matches("^[0-9]+$")) {
                    writer.value(Long.parseLong(valString));
                } else if (key.equals("read") || key.equals("deleted")) {
                    writer.value(value);
                } else {
                    writer.value(valString);
                }
            }
        }
    }
}
