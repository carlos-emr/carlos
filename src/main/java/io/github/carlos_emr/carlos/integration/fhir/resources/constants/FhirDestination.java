package io.github.carlos_emr.carlos.integration.fhir.resources.constants;

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


/**
 * Enumerates the available FHIR message destinations with their titles and endpoint URLs.
 *
 * <p>Currently includes the Ontario Digital Health Immunization Repository (DHIR).
 * Additional destinations can be added as new enum constants.</p>
 *
 * @since 2026-03-17
 */
public enum FhirDestination {

    // add more locations here in this format: ID (["Title or Description"], ["endpoint"] )
    DHIR("DHIR", "https://wsgateway.prod.ehealthontario.ca/API/FHIR/Immunizations/v3/clinician/");

    private final String title;
    private final String endpoint;

    private FhirDestination(String title, String endpoint) {
        this.title = title;
        this.endpoint = endpoint;
    }

    /**
     * Returns the display title for this destination.
     *
     * @return String the destination title
     */
    public final String title() {
        return title;
    }

    /**
     * Returns the endpoint URL for this destination.
     *
     * @return String the destination endpoint URL
     */
    public final String endpoint() {
        return endpoint;
    }

}
