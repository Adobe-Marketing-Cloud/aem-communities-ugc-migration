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


import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.jcr.RepositoryException;
import javax.servlet.ServletException;

import com.adobe.cq.social.messaging.api.Message;
import com.adobe.cq.social.messaging.api.MessageFilter;
import com.adobe.cq.social.messaging.api.MessagingService;
import com.adobe.cq.social.scf.ClientUtilityFactory;
import com.adobe.cq.social.scf.OperationException;
import com.adobe.cq.social.ugcbase.core.attachments.FileDataSource;
import com.adobe.cq.social.ugcbase.SocialUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

import com.adobe.granite.xss.XSSAPI;
import com.adobe.communities.ugc.migration.ContentTypeDefinitions;
import com.adobe.cq.social.messaging.client.endpoints.MessagingOperationsService;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.osgi.service.component.ComponentContext;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

@Component(label = "UGC Migration Messages Importer",
        description = "Accepts a zipped archive of message data, unzips its contents and saves mail messages",
        specVersion = "1.1")
@Service
@Properties({@Property(name = "sling.servlet.paths", value = "/services/social/messages/import")})
public class MessagesImportServlet extends SlingAllMethodsServlet {

    private MessagingServiceTracker messagingServiceTracker;

    @Reference
    private MessagingOperationsService messagingService;

    @Reference
    private ResourceResolverFactory rrf;

    @Reference
    private SocialUtils socialUtils;
    @Reference
    private XSSAPI xss;
    @Reference
    private ClientUtilityFactory clientUtilsFactory;

    @Reference
    MessagingService messageSearch;

    static final String SERVICE_SELECTOR_PROPERTY = "serviceSelector";

    protected void activate(final ComponentContext context) {
        messagingServiceTracker = new MessagingServiceTracker(context.getBundleContext());
        messagingServiceTracker.open();
    }

    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws ServletException, IOException {


        final ResourceResolver resolver = request.getResourceResolver();

        UGCImportHelper.checkUserPrivileges(resolver, rrf);

        // first check to see if the caller provided an explicit selector for our MessagingOperationsService
        final RequestParameter serviceSelector = request.getRequestParameter("serviceSelector");
        // if no serviceSelector parameter was provided, we'll try going with whatever we get through the Reference
        if (null != serviceSelector && !serviceSelector.getString().equals("")) {
            // make sure the messagingServiceTracker was created by the activate method
            if (null == messagingServiceTracker) {
                throw new ServletException("Cannot use messagingServiceTracker");
            }
            // search for the MessagingOperationsService corresponding to the provided selector
            messagingService = messagingServiceTracker.getService(serviceSelector.getString());
            if (null == messagingService) {
                throw new ServletException("MessagingOperationsService for selector " + serviceSelector.getString()
                        + "was not found");
            }
        }
        // finally get the uploaded file
        final RequestParameter[] fileRequestParameters = request.getRequestParameters("file");
        if (fileRequestParameters != null && fileRequestParameters.length > 0
                && !fileRequestParameters[0].isFormField()) {

            if (fileRequestParameters[0].getFileName().endsWith(".json")) {
                // if upload is a single json file...
                final InputStream inputStream = fileRequestParameters[0].getInputStream();
                final JsonParser jsonParser = new JsonFactory().createParser(inputStream);
                jsonParser.nextToken(); // get the first token
                importMessages(request, jsonParser);
            } else if (fileRequestParameters[0].getFileName().endsWith(".zip")) {
                ZipInputStream zipInputStream;
                try {
                    zipInputStream = new ZipInputStream(fileRequestParameters[0].getInputStream());
                } catch (IOException e) {
                    throw new ServletException("Could not open zip archive");
                }
                ZipEntry zipEntry = zipInputStream.getNextEntry();
                while (zipEntry != null) {
                    final JsonParser jsonParser = new JsonFactory().createParser(zipInputStream);
                    jsonParser.nextToken(); // get the first token
                    importMessages(request, jsonParser);
                    zipInputStream.closeEntry();
                    zipEntry = zipInputStream.getNextEntry();
                }
                zipInputStream.close();
            } else {
                throw new ServletException("Unrecognized file input type");
            }
        } else {
            throw new ServletException("No file provided for UGC data");
        }
    }

    private void importMessages(final SlingHttpServletRequest request, final JsonParser jsonParser) throws ServletException {

        if (!jsonParser.getCurrentToken().equals(JsonToken.START_ARRAY)) {
            throw new ServletException("unexpected starting token "+ jsonParser.getCurrentToken().asString());
        }

        try {
            jsonParser.nextToken(); //presumably, we will advance to a "start object" token
            while (!jsonParser.getCurrentToken().equals(JsonToken.END_ARRAY)) {
                final Map<String, Map<String, Boolean>> recipientModifiers =new HashMap<String, Map<String, Boolean>>();
                final Map<String, Object> props = new HashMap<String, Object>();
                List<FileDataSource> attachments = new ArrayList<FileDataSource>();
                String sender = "";
                jsonParser.nextToken(); //field name
                while (!jsonParser.getCurrentToken().equals(JsonToken.END_OBJECT)) {
                    final String fieldName = jsonParser.getCurrentName();
                    jsonParser.nextToken(); //value
                    if (fieldName.equals("senderId")) {
                        sender = URLDecoder.decode(jsonParser.getValueAsString(), "UTF-8");
                    } else if (fieldName.equals("added")) {
                        final Calendar calendar = new GregorianCalendar();
                        calendar.setTimeInMillis(jsonParser.getLongValue());
                        props.put("added", calendar);
                    } else if (fieldName.equals("recipients")) {
                        // build the string for the "to" property and also create the modifiers we'll need later
                        final StringBuilder recipientString = new StringBuilder();
                        //iterate over each key (each being a recipient id)
                        if (jsonParser.getCurrentToken().equals(JsonToken.START_OBJECT)) {
                            jsonParser.nextToken(); // should get first recipientId
                            while (!jsonParser.getCurrentToken().equals(JsonToken.END_OBJECT)) {
                                final String recipientId = jsonParser.getCurrentName();
                                recipientString.append(recipientId);
                                jsonParser.nextToken(); //start object
                                jsonParser.nextToken(); //first label
                                final Map<String, Boolean> interactionModifiers = new HashMap<String, Boolean>();
                                while (!jsonParser.getCurrentToken().equals(JsonToken.END_OBJECT)) {
                                    final String label = jsonParser.getCurrentName();
                                    jsonParser.nextToken();
                                    final Boolean labelValue = jsonParser.getBooleanValue();
                                    interactionModifiers.put(label, labelValue);
                                    jsonParser.nextToken(); //next label or end object
                                }
                                recipientModifiers.put(recipientId, interactionModifiers);
                                jsonParser.nextToken(); // next recipientId or end object
                                if (jsonParser.getCurrentToken().equals(JsonToken.FIELD_NAME)) {
                                    recipientString.append(';');
                                }
                            }
                            props.put("to", recipientString);
                        }
                    } else if (fieldName.equals(ContentTypeDefinitions.LABEL_ATTACHMENTS)) {
                        UGCImportHelper.getAttachments(jsonParser, attachments);
                    } else {
                        props.put(fieldName, URLDecoder.decode(jsonParser.getValueAsString(), "UTF-8"));
                    }
                    jsonParser.nextToken(); //either next field name or end object
                }
                final Random range = new Random();
                final String key = String.valueOf(range.nextInt(Integer.MAX_VALUE))
                                 + String.valueOf(range.nextInt(Integer.MAX_VALUE));
                props.put("searchKey", key);
                try {
                    short result = messagingService.create(request.getResourceResolver(), request.getResource(), sender,
                            props, attachments, clientUtilsFactory.getClientUtilities(xss, request, socialUtils));

                    if (result != 200) {
                        throw new ServletException("Message sending failed. Return code was " + result);
                    }
                } catch (final OperationException e) {
                    throw new ServletException("Unable to create a message through the operation service", e);
                }
                // now search for the messages we just sent and update them appropriately by filling in their "read",
                // "deleted", and "added" properties, and stripping their "searchKey" property
                final MessageFilter filter = new MessageFilter();
                filter.setContentType("social:asiResource");
                filter.setVariableValue("searchKey", key);
                Iterable<Message> messages = null;
                try {
                    messages = messageSearch.search(request.getResourceResolver(), filter, 0, recipientModifiers.size() + 1);

                } catch (final RepositoryException e) {
                    throw new ServletException("Sent messages could not be found", e);
                }
                for (final Message message : messages) {
                    final Resource messageResource = message.adaptTo(Resource.class);
                    ModifiableValueMap mvm = messageResource.adaptTo(ModifiableValueMap.class);
                    mvm.put("added", props.get("added"));
                    mvm.remove("searchKey");
                    for (final String recipientId : recipientModifiers.keySet()) {
                        if (messageResource.getPath().contains(recipientId)) {
                            final Map<String, Boolean> modifiers = recipientModifiers.get(recipientId);
                            mvm.put("read_b", modifiers.get("read"));
                            mvm.put("deleted_b", modifiers.get("deleted"));
                            break;
                        }
                    }
                }
                request.getResourceResolver().commit(); //save changes
                jsonParser.nextToken(); //either END_ARRAY or START_OBJECT
            }

        } catch (final IOException e) {
            throw new ServletException("Encountered exception while parsing json content", e);
        }
    }
    static final class MessagingServiceTracker extends ServiceTracker {

        private final ConcurrentMap<String, MessagingOperationsService> serviceCache =
                new ConcurrentHashMap<String, MessagingOperationsService>();

        /**
         * Default constructor.
         * @param context The {@link org.osgi.framework.BundleContext}.
         */
        MessagingServiceTracker(final BundleContext context) {
            super(context, MessagingOperationsService.class.getName(), null);
        }

        /**
         * Get the {@link com.adobe.cq.social.messaging.client.endpoints.MessagingOperationsService} instance for the
         * specified path.
         * @param path The path used to determine the
         *            {@link com.adobe.cq.social.messaging.client.endpoints.MessagingOperationsService} to return.
         * @return The {@link com.adobe.cq.social.messaging.client.endpoints.MessagingOperationsService}
         */
        @CheckForNull
        MessagingOperationsService getService(@Nonnull final String path) {
            return this.serviceCache.get(path);
        }

        @Override
        public Object addingService(final ServiceReference reference) {
            final Object service = super.addingService(reference);
            if (null != service) {
                bindService(reference, service);
            }
            return service;
        }

        @Override
        public void modifiedService(final ServiceReference reference, final Object service) {
            unbindService(reference);
            if (null != service) {
                bindService(reference, service);
            }
        }

        @Override
        public void removedService(final ServiceReference reference, final Object service) {
            unbindService(reference);
            super.removedService(reference, service);
        }

        private void bindService(@Nonnull final ServiceReference reference, final Object service) {
            serviceCache.putIfAbsent(
                    String.valueOf(reference.getProperty(SERVICE_SELECTOR_PROPERTY)),
                        (MessagingOperationsService) service);
        }

        private void unbindService(@Nonnull final ServiceReference reference) {
            serviceCache.remove(String.valueOf(reference
                    .getProperty(SERVICE_SELECTOR_PROPERTY)));
        }
    }
}
