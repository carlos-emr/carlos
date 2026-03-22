package io.github.carlos_emr.carlos.integration.fhir.model;
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

import java.util.UUID;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.integration.fhir.interfaces.ResourceAttributeFilterInterface;
import io.github.carlos_emr.carlos.integration.fhir.manager.OscarFhirConfigurationManager;
import io.github.carlos_emr.carlos.integration.fhir.resources.ResourceAttributeFilter;
import io.github.carlos_emr.carlos.integration.fhir.resources.constants.ActorType;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Resource;
import ca.uhn.fhir.context.FhirContext;


/**
 * Abstract base class for bidirectional mapping between CARLOS EMR domain models
 * and FHIR DSTU3 resources.
 *
 * <p>Provides the framework for converting between CARLOS entities (extending
 * {@link AbstractModel}) and FHIR resources (extending {@link org.hl7.fhir.dstu3.model.BaseResource}).
 * Subclasses implement {@link #mapAttributes(org.hl7.fhir.dstu3.model.BaseResource)} and
 * {@link #mapAttributes(AbstractModel)} to define the attribute mapping in each direction.</p>
 *
 * <p>Also implements {@link ResourceAttributeFilterInterface} to support destination-specific
 * attribute filtering, and tracks focus resource designation and actor type for message
 * header construction.</p>
 *
 * @param <FHIR> the FHIR DSTU3 resource type
 * @param <OSCAR> the CARLOS EMR domain model type
 * @since 2026-03-17
 */
public abstract class AbstractOscarFhirResource<FHIR extends org.hl7.fhir.dstu3.model.BaseResource, OSCAR extends AbstractModel<?>>
        implements ResourceAttributeFilterInterface {

    private static FhirContext fhirContext = FhirContext.forDstu3();
    private FHIR fhirResource;
    private OSCAR oscarResource;
    private OscarFhirConfigurationManager configurationManager;
    private boolean focusResource;
    private ActorType actor = ActorType.none;

    /**
     * Map attributes from an Oscar resource into a FHIR Resource.
     */
    protected abstract void mapAttributes(FHIR fhirResource);

    /**
     * Map attributes from a FHIR resource into an Oscar Resource.
     */
    protected abstract void mapAttributes(OSCAR oscarResource);

    protected AbstractOscarFhirResource() {
        //default constructor
    }

    protected AbstractOscarFhirResource(OSCAR to, FHIR from) {
        setResource(to, from);
    }

    protected AbstractOscarFhirResource(FHIR to, OSCAR from) {
        setResource(to, from);
    }

    protected AbstractOscarFhirResource(OscarFhirConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    protected AbstractOscarFhirResource(FHIR to, OSCAR from, OscarFhirConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
        setResource(to, from);
    }

    /**
     * Set the reference id of the FHIR Resource.
     * This can be set with the unique id of the Oscar Resource (ie: demographic_no) or can be
     * generated uniquely.
     * In any case, the id is a requirement.
     */
    protected void setId(FHIR fhirResource) {
        fhirResource.setId(UUID.randomUUID().toString());
    }

    protected void setResource(FHIR to, OSCAR from) {
        this.oscarResource = from;
        setFhirResource(to);
    }

    protected void setResource(OSCAR to, FHIR from) {
        this.fhirResource = from;
        setOscarResource(to);
    }

    /**
     * Returns the mapped FHIR resource.
     *
     * @return FHIR the FHIR DSTU3 resource
     */
    public FHIR getFhirResource() {
        return fhirResource;
    }

    protected void setFhirResource(FHIR fhirResource) {
        setId(fhirResource);
        this.fhirResource = fhirResource;

        if (this.oscarResource != null) {
            mapAttributes(fhirResource);
        }
    }

    /**
     * Returns the CARLOS EMR domain model.
     *
     * @return OSCAR the source domain entity
     */
    public OSCAR getOscarResource() {
        return this.oscarResource;
    }

    protected void setOscarResource(OSCAR oscarResource) {
        if (this.fhirResource != null) {
            mapAttributes(oscarResource);
        }

        this.oscarResource = oscarResource;
    }

    /**
     * Returns the shared FHIR context (DSTU3).
     *
     * @return FhirContext the DSTU3 FHIR context
     */
    public static FhirContext getFhirContext() {
        return fhirContext;
    }

    /**
     * Replaces the shared FHIR context instance.
     *
     * @param fhirContext the new FhirContext to use
     */
    public static void setFhirContext(FhirContext fhirContext) {
        AbstractOscarFhirResource.fhirContext = fhirContext;
    }

    /**
     * Serializes the FHIR resource to a pretty-printed JSON string.
     *
     * @return String the JSON representation of the FHIR resource
     */
    public String getFhirJSON() {
        return getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(getFhirResource());
    }

    /**
     * Serializes the FHIR resource to an XML string.
     *
     * @return String the XML representation of the FHIR resource
     */
    public String getFhirXML() {
        return getFhirContext().newXmlParser().encodeResourceToString(getFhirResource());
    }

    /**
     * Creates a FHIR Reference pointing to this resource.
     *
     * @return Reference containing both the reference link and the resource itself
     */
    public Reference getReference() {
        Reference reference = new Reference();
        reference.setReference(getReferenceLink());
        reference.setResource(getFhirResource());
        return reference;
    }

    /**
     * Returns a relative reference link in the format {@code ResourceType/id}.
     *
     * @return String the relative reference link
     */
    public String getReferenceLink() {
        return String.format("%s/%s", ((Resource) getFhirResource()).getResourceType(), getFhirResource().getId());
    }

    /**
     * Returns a contained reference link prefixed with {@code #} for inline resources.
     *
     * <p>Also updates the resource's own ID to match the contained reference format.</p>
     *
     * @return String the contained reference link (e.g., "#Patient123")
     */
    public String getContainedReferenceLink() {
        String resourceType = ((Resource) getFhirResource()).getResourceType().name();
        String referenceLink = String.format("%s%s", resourceType, getFhirResource().getId().replaceAll(resourceType, ""));
        getFhirResource().setId(referenceLink);
        return ("#" + referenceLink);
    }

    /**
     * override to return true whenever the filter is not available.
     */
    @Override
    public boolean include(OptionalFHIRAttribute attribute) {
        boolean include = Boolean.TRUE;
        ResourceAttributeFilter resourceAttributeFilter = getFilter(this.getClass());
        if (resourceAttributeFilter != null) {
            include = resourceAttributeFilter.include(attribute);
        }
        return include;
    }

    @Override
    public boolean isMandatory(MandatoryFHIRAttribute attribute) {
        ResourceAttributeFilter resourceAttributeFilter = getFilter(this.getClass());
        boolean mandatory = Boolean.FALSE;
        if (resourceAttributeFilter != null) {
            mandatory = resourceAttributeFilter.isMandatory(attribute);
        }
        return mandatory;
    }

    @Override
    public ResourceAttributeFilter getFilter(Class<?> targetResource) {
        ResourceAttributeFilter resourceAttributeFilter = null;
        if (this.configurationManager != null) {
            resourceAttributeFilter = configurationManager.getResourceAttributeFilter(targetResource);
        }
        return resourceAttributeFilter;
    }

    /**
     * Returns the configuration manager for this resource.
     *
     * @return OscarFhirConfigurationManager the configuration manager, or {@code null} if not set
     */
    public OscarFhirConfigurationManager getConfigurationManager() {
        return configurationManager;
    }

    /**
     * Returns whether this resource is designated as the focus resource in the message.
     *
     * @return boolean {@code true} if this is the focus resource
     */
    public boolean isFocusResource() {
        return focusResource;
    }

    /**
     * Only one resource can be the focus point in any message structure.
     * Ie: the Patient is the focus resource in an immunization transmission
     */
    public void setFocusResource(boolean focusResource) {
        this.focusResource = focusResource;
    }

    /**
     * Get the type of actor for this resource. Returns null if not set.
     */
    public final ActorType getActor() {
        return actor;
    }

    /**
     * Set the type of actor for this resource
     * This is auto set when instantiating the Practitioner subclasses.
     */
    public final void setActor(ActorType actor) {
        this.actor = actor;
    }

}
