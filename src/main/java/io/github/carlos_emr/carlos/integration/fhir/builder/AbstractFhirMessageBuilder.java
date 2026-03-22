package io.github.carlos_emr.carlos.integration.fhir.builder;
/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.logging.log4j.Logger;
import org.hl7.fhir.dstu3.model.Attachment;

import org.hl7.fhir.dstu3.model.BaseResource;
import org.hl7.fhir.dstu3.model.MessageHeader;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Resource;
import io.github.carlos_emr.carlos.integration.fhir.manager.OscarFhirConfigurationManager;
import io.github.carlos_emr.carlos.integration.fhir.model.AbstractOscarFhirResource;
import io.github.carlos_emr.carlos.integration.fhir.model.Destination;
import io.github.carlos_emr.carlos.integration.fhir.model.Sender;
import io.github.carlos_emr.carlos.integration.fhir.resources.constants.ActorType;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import ca.uhn.fhir.context.FhirContext;


/**
 * Abstract base builder for constructing FHIR messages with CARLOS EMR FHIR resources.
 *
 * <p>Provides the common infrastructure for assembling FHIR messages including
 * MessageHeader construction from Sender/Destination objects, resource reference tracking,
 * attachment handling, and JSON serialization via HAPI FHIR (DSTU3).</p>
 *
 * @param <T> the FHIR wrapper resource type (e.g., Bundle, Communication)
 * @since 2026-03-17
 */
public abstract class AbstractFhirMessageBuilder<T extends BaseResource> {

    private static Logger logger = MiscUtils.getLogger();
    private static FhirContext fhirContext = FhirContext.forDstu3();

    private MessageHeader messageHeader;
    private Sender sender;
    private Destination destination;
    private List<AbstractOscarFhirResource<?, ?>> resources;
    private T wrapper;
    private HashMap<ReferenceKey, Reference> references;
    private OscarFhirConfigurationManager oscarFhirConfigurationManager;

    /**
     * Adds a raw FHIR resource to the message wrapper.
     *
     * @param resource the FHIR BaseResource to add
     */
    public abstract void addResource(BaseResource resource);

    /**
     * Adds an attachment to the message. Implementation varies by message type.
     *
     * @param attachment the FHIR Attachment to add
     */
    protected abstract void addAttachment(Attachment attachment);

    /**
     * Constructor to add a MessageHeader to the message using the Sender and Destination
     * information
     *
     * @throws Exception
     */
    protected AbstractFhirMessageBuilder(Sender sender, Destination destination) {
        setEndpointParameters(sender, destination);
    }

    /**
     * Constructs a message builder using the configuration manager's Sender and Destination.
     *
     * @param oscarFhirConfigurationManager the FHIR configuration manager providing sender and destination
     */
    protected AbstractFhirMessageBuilder(OscarFhirConfigurationManager oscarFhirConfigurationManager) {
        setEndpointParameters(oscarFhirConfigurationManager.getSender(), oscarFhirConfigurationManager.getDestination());
        this.oscarFhirConfigurationManager = oscarFhirConfigurationManager;
    }

    /**
     * The MessageHeader is a requirement because there are often some reference
     * links that need to be added after they are created.
     */
    protected AbstractFhirMessageBuilder(MessageHeader messageHeader) {
        setMessageHeader(messageHeader);
    }

    private void setEndpointParameters(Sender sender, Destination destination) {
        if (sender != null && destination != null) {
            setSender(sender);
            setDestination(destination);
            createMessageHeader(new MessageHeader());
        } else {
            try {
                throw new Exception("Init parameters cannot be null");
            } catch (Exception e) {
                logger.error("Error instantiating " + this.getClass().getName(), e);
            }
        }
    }

    protected T getWrapper() {
        return wrapper;
    }

    protected void setWrapper(T wrapper) {
        this.wrapper = wrapper;
    }

    /**
     * Returns the shared FHIR context instance (DSTU3).
     *
     * @return FhirContext the DSTU3 FHIR context used for serialization
     */
    public static final FhirContext getFhirContext() {
        return fhirContext;
    }

    /**
     * Replaces the shared FHIR context instance.
     *
     * @param fhirContext the new FhirContext to use for serialization
     */
    public static void setFhirContext(FhirContext fhirContext) {
        AbstractFhirMessageBuilder.fhirContext = fhirContext;
    }

    /**
     * Creates the MessageHeader auto-magically from the provided Sender and
     * Destination Objects.
     */
    private void createMessageHeader(MessageHeader messageHeader) {
        messageHeader.setId(UUID.randomUUID().toString());
        messageHeader.getEvent()
                .setSystem("http://hl7.org/fhir/message-events")
                .setCode("MedicationAdministration-Recording");

        messageHeader.setTimestamp(new Date(System.currentTimeMillis()));
        messageHeader.setSource(getSender().getMessageSourceComponent());
        messageHeader.setDestination(getDestination().getMessageDestinationComponents());
        messageHeader.getSender().setDisplay(getSender().getSenderName());

        setMessageHeader(messageHeader);
    }

    /**
     * Set a Resource reference link that will be identified as the Sender
     * Resource in the MessageHeader.
     */
    public void setMessageHeaderSender(Reference reference) {
        if (getMessageHeader() != null) {
            messageHeader.setSender(reference);
        }
    }

    /**
     * Set a contained Resource inside the MessageHeader that will be
     * identified as the message sender details.
     * Usually an Organization resource.
     */
    public void setMessageHeaderSender(Resource resource) {
        if (getMessageHeader() != null) {
            messageHeader.getSender().setResource(resource);
        }
    }

    /**
     * Set the sender attribute as a text (display) value only.
     */
    public void setMessageHeaderSender(String value) {
        if (getMessageHeader() != null) {
            messageHeader.getSender().setDisplay(value);
        }
    }

    /**
     * Set a Resource reference link that will be identified as the Receiver
     * Resource in the MessageHeader.
     */
    public void setMessageHeaderReciever(Reference reference) {
        if (getMessageHeader() != null) {
            messageHeader.setReceiver(reference);
        }
    }

    /**
     * Add a Resource reference link that will be identified as the Focus
     * Resource in the MessageHeader. Multiple Resources can be identified as the
     * Focus
     */
    public void addMessageHeaderFocus(Reference reference) {
        if (getMessageHeader() != null) {
            messageHeader.addFocus(reference);
        }
    }

    /**
     * Add a Resource reference link that will be identified as the Responsible
     * Resource in the MessageHeader. Usually the sender of the message in an Organization
     * Resource.
     */
    public void setMessageHeaderResponsible(Reference reference) {
        if (getMessageHeader() != null) {
            messageHeader.setResponsible(reference);
        }
    }

    /**
     * Add a Resource reference link that will be identified as the Author
     * Resource in the MessageHeader. Usually the practitioner resource.
     * Can also be considered the submitter of this message.
     */
    public void setMessageHeaderAuthor(Reference reference) {
        if (getMessageHeader() != null) {
            messageHeader.setAuthor(reference);
        }
    }

    /**
     * Get the MessageHeader Resource.
     */
    public MessageHeader getMessageHeader() {
        return this.messageHeader;
    }

    /**
     * Get the MessageHeader Resource printed into a JSON string.
     */
    public String getMessageHeaderJson() {
        return resourceToJson(messageHeader);
    }

    /**
     * If the sender and destination objects are set, this will be set automatically
     * Otherwise setting this will override any previously set MessageHeaders.
     */
    private void setMessageHeader(MessageHeader messageHeader) {
        this.messageHeader = messageHeader;
    }

    /**
     * Get this entire message in JSON format in a String.
     */
    public String getMessageJson() {
        return resourceToJson(getWrapper());
    }

    /**
     * Returns the Sender configured for this message.
     *
     * @return Sender the message sender
     */
    public Sender getSender() {
        return sender;
    }

    private void setSender(Sender sender) {
        this.sender = sender;
    }

    /**
     * Returns the Destination configured for this message.
     *
     * @return Destination the message destination
     */
    public Destination getDestination() {
        return destination;
    }

    private void setDestination(Destination destination) {
        this.destination = destination;
    }

    /**
     * Get a List of Resources that were contained in this message bundle.
     */
    public List<AbstractOscarFhirResource<?, ?>> getResources() {
        if (this.resources == null) {
            resources = new ArrayList<AbstractOscarFhirResource<?, ?>>();
        }
        return resources;
    }

    //TODO this will need to be set in a properties. ie: DHIR = MessageHeader.focus = Patient Resource
    private void resourceFilter(AbstractOscarFhirResource<?, ?> oscarFhirResource) {

        if (oscarFhirResource.isFocusResource()) {

            addMessageHeaderFocus(oscarFhirResource.getReference());
        }

        if (oscarFhirResource.getActor().equals(ActorType.submitting)) {

            setMessageHeaderAuthor(oscarFhirResource.getReference());

        }
    }

    /**
     * Adds multiple CARLOS FHIR resources to the message.
     *
     * @param oscarFhirResources the list of resources to add
     */
    public void addResources(List<AbstractOscarFhirResource<?, ?>> oscarFhirResources) {
        for (AbstractOscarFhirResource<?, ?> oscarFhirResource : oscarFhirResources) {
            addResource(oscarFhirResource);
        }
    }

    /**
     * Adds a single CARLOS FHIR resource to the message, applying resource filters
     * and tracking the reference.
     *
     * @param oscarFhirResource the CARLOS FHIR resource to add
     */
    public void addResource(AbstractOscarFhirResource<?, ?> oscarFhirResource) {
        resourceFilter(oscarFhirResource);
        getResources().add(oscarFhirResource);
        BaseResource resource = oscarFhirResource.getFhirResource();
        addResource(resource);
        addReference(oscarFhirResource);
    }

    protected static final String resourceToJson(org.hl7.fhir.dstu3.model.BaseResource resource) {
        if (resource == null) {
            return "";
        }
        return getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(resource);
    }

    /**
     * Attaches a PDF document to the message.
     *
     * @param pdf the PDF content as a string
     * @param title the attachment title
     */
    public void attachPDF(String pdf, String title) {
        Attachment attachment = new Attachment();
        attachment.setContentType("application/pdf");
        setAttachment(pdf, title, attachment);
    }

    /**
     * Attaches a rich text (RTF) document to the message.
     *
     * @param rtf the RTF content as a string
     * @param title the attachment title
     */
    public void attachRichText(String rtf, String title) {
        Attachment attachment = new Attachment();
        attachment.setContentType("text/rtf");
        setAttachment(rtf, title, attachment);
    }

    /**
     * Attaches a plain text document to the message.
     *
     * @param text the text content
     * @param title the attachment title
     */
    public void attachText(String text, String title) {
        Attachment attachment = new Attachment();
        attachment.setContentType("text/plain");
        setAttachment(text, title, attachment);
    }

    /**
     * Attaches an XML document to the message.
     *
     * @param xml the XML content as a string
     * @param title the attachment title
     */
    public void attachXML(String xml, String title) {
        Attachment attachment = new Attachment();
        attachment.setContentType("text/xml");
        setAttachment(xml, title, attachment);
    }

    private void setAttachment(String data, String title, Attachment attachment) {
        attachment.setTitle(title);
        attachment.setData(data.getBytes());
        addAttachment(attachment);
    }

    /**
     * Returns the map of resource references tracked by this builder.
     *
     * @return HashMap mapping ReferenceKey to FHIR Reference objects
     */
    public HashMap<ReferenceKey, Reference> getReferences() {
        if (references == null) {
            setReferences(new HashMap<ReferenceKey, Reference>());
        }
        return references;
    }

    private void setReferences(HashMap<ReferenceKey, Reference> references) {
        this.references = references;
    }

    private void addReference(Reference reference) {
        addReference(new ReferenceKey(reference.getResource().getClass().getSimpleName(), ((BaseResource) reference.getResource()).getId()), reference);
    }

    private void addReference(ReferenceKey referenceKey, Reference reference) {
        getReferences().put(referenceKey, reference);
    }

    private void addReference(AbstractOscarFhirResource<?, ?> oscarFhirResource) {
        addReference(oscarFhirResource.getReference());
    }

    protected OscarFhirConfigurationManager getOscarFhirConfigurationManager() {
        return oscarFhirConfigurationManager;
    }

    protected void setOscarFhirConfigurationManager(OscarFhirConfigurationManager oscarFhirConfigurationManager) {
        this.oscarFhirConfigurationManager = oscarFhirConfigurationManager;
    }

}

/**
 * Composite key for tracking FHIR resource references by class name and resource ID.
 *
 * <p>Used internally by {@link AbstractFhirMessageBuilder} to maintain a lookup map
 * of references added to the message.</p>
 *
 * @since 2026-03-17
 */
final class ReferenceKey {

    private String className;
    private String id;

    public ReferenceKey(String className, String id) {
        setClassName(className);
        setId(id);
    }

    @Override
    public boolean equals(Object object) {
        if (object != null && object instanceof ReferenceKey) {
            ReferenceKey referenceKey = (ReferenceKey) object;
            return className.equals(referenceKey.className)
                    && id.equals(referenceKey.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (className + id).hashCode();
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

}
