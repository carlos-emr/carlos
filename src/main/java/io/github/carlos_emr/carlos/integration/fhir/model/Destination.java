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
/*
"destination": [
{
  "name": "DHIR",
  "endpoint": "https://wsgateway.prod.ehealthontario.ca/API/FHIR/Immunizations/v1/"
}
*/


import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.hl7.fhir.dstu3.model.BaseResource;
import org.hl7.fhir.dstu3.model.MessageHeader.MessageDestinationComponent;
import io.github.carlos_emr.carlos.integration.fhir.resources.constants.FhirDestination;


/**
 * A class that references a message destination header information.
 * This Object builds a FHIR MessageDestinationComponent.
 * <p>
 * The ultimate intention of this class is to auto populate and translate specific data
 * according to its environment.
 *
 * @since 2026-03-17
 */
public final class Destination {

    private List<MessageDestinationComponent> messageDestinationComponents;
    private List<FhirDestination> destinations;
    private List<AbstractOscarFhirResource<?, ?>> oscarFhirResource;
    private List<BaseResource> fhirResources;

    /**
     * Constructs a Destination from a single FhirDestination enum.
     *
     * @param destination the FHIR destination enum
     */
    public Destination(FhirDestination destination) {
        addDestination(destination);
    }

    /**
     * Constructs a Destination with an explicit name and endpoint URL.
     *
     * @param name the destination name
     * @param endpoint the destination endpoint URL
     */
    public Destination(String name, String endpoint) {
        addDestination(name, endpoint);
    }

    /**
     * Constructs a Destination from multiple FhirDestination enums.
     *
     * @param destinations the list of FHIR destination enums
     */
    public Destination(List<FhirDestination> destinations) {
        setDestinations(destinations);
    }

    /**
     * Returns the list of FHIR MessageDestinationComponents for this destination.
     *
     * @return List of MessageDestinationComponent objects
     */
    public List<MessageDestinationComponent> getMessageDestinationComponents() {
        if (messageDestinationComponents == null) {
            messageDestinationComponents = new ArrayList<MessageDestinationComponent>();
        }
        return messageDestinationComponents;
    }

    /**
     * Add a new destination Name and its associated endpoint.
     * Multiple destinations can be added.
     */
    private void addMessageDestinationComponent(MessageDestinationComponent messageDestinationComponent) {
        getMessageDestinationComponents().add(messageDestinationComponent);
    }

    public List<FhirDestination> getDestinations() {
        if (destinations == null) {
            destinations = new ArrayList<FhirDestination>();
        }
        return destinations;
    }

    public void setDestinations(List<FhirDestination> destinations) {
        for (FhirDestination destination : destinations) {
            addDestination(destination);
        }
    }

    public void addDestination(FhirDestination destination) {
        addDestination(destination.title(), destination.endpoint());
    }

    private void addDestination(String name, String endpoint) {
        MessageDestinationComponent messageDestinationComponent = new MessageDestinationComponent();
        messageDestinationComponent.setName(name);
        messageDestinationComponent.setEndpoint(endpoint);
        addMessageDestinationComponent(messageDestinationComponent);
    }

    /**
     * Adds a CARLOS FHIR Organization as a destination recipient.
     *
     * @param organization the Organization resource to add
     */
    public void addOrganization(Organization<?> organization) {
        addFhirResource(organization.getFhirResource());
        getOscarFhirResources().add(organization);
    }

    /**
     * Adds a raw FHIR Organization as a destination recipient.
     *
     * @param organization the FHIR Organization resource to add
     */
    public void addOrganization(org.hl7.fhir.dstu3.model.Organization organization) {
        addFhirResource(organization);
    }

    public List<AbstractOscarFhirResource<?, ?>> getOscarFhirResources() {
        if (oscarFhirResource == null) {
            oscarFhirResource = new ArrayList<AbstractOscarFhirResource<?, ?>>();
        }
        return oscarFhirResource;
    }

    public List<BaseResource> getFhirResources() {
        if (fhirResources == null) {
            fhirResources = new ArrayList<BaseResource>();
        }
        return fhirResources;
    }

    public void addFhirResource(BaseResource fhirResource) {
        getFhirResources().add(fhirResource);
    }

    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
