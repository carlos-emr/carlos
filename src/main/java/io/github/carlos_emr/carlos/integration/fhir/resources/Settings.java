package io.github.carlos_emr.carlos.integration.fhir.resources;
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

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import io.github.carlos_emr.carlos.integration.fhir.resources.constants.FhirDestination;
import io.github.carlos_emr.carlos.integration.fhir.resources.constants.Region;

/**
 * Configuration settings for FHIR message construction, including destination,
 * region, and sender endpoint preferences.
 *
 * <p>Currently configured via constructor parameters. Future versions may
 * load settings from a properties file.</p>
 *
 * @since 2026-03-17
 */
public class Settings {

    private boolean includeSenderEndpoint;
    private Region region;
    private FhirDestination fhirDestination;

    /**
     * Constructs Settings with a FHIR destination and region.
     *
     * @param fhirDestination the target FHIR endpoint
     * @param region the healthcare region (e.g., Ontario, British Columbia)
     */
    public Settings(FhirDestination fhirDestination, Region region) {
        this.region = region;
        this.fhirDestination = fhirDestination;
    }

    /**
     * Returns whether the sender endpoint URL should be included in outbound messages.
     *
     * @return boolean {@code true} if the sender endpoint should be included
     */
    public boolean isIncludeSenderEndpoint() {
        return includeSenderEndpoint;
    }

    public void setIncludeSenderEndpoint(boolean includeSenderEndpoint) {
        this.includeSenderEndpoint = includeSenderEndpoint;
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public FhirDestination getFhirDestination() {
        return fhirDestination;
    }

    public void setFhirDestination(FhirDestination fhirDestination) {
        this.fhirDestination = fhirDestination;
    }

    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

}
