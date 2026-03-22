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

import java.io.File;
import java.net.URL;

import io.github.carlos_emr.carlos.integration.fhir.resources.ResourceAttributeFilter;
import io.github.carlos_emr.carlos.integration.fhir.resources.constants.FhirDestination;

/**
 * Factory for loading {@link ResourceAttributeFilter} instances from properties files
 * based on the FHIR destination.
 *
 * <p>Filter files are loaded from the classpath under
 * {@code /org/oscarehr/integration/fhir/filters/{destination}/resource_attribute.filter}
 * and control which optional and mandatory FHIR attributes are included in outbound messages.</p>
 *
 * @since 2026-03-17
 */
public final class ResourceAttributeFilterFactory {

    private static final String ROOT_RESOURCE_URL = "/org/oscarehr/integration/fhir/filters/";
    private static final String FILE_NAME = "resource_attribute";
    private static final String FILE_APPEND = "filter";

    /**
     * Returns a Resource Attribute Filter class customized specifically for the
     * FHIR destination and FHIR Resource.
     * For Example. A Patient FHIR resource being sent to the Ontario DHIR API would have a method signature:
     * <p>
     * getFilter("dhir", "org.hl7.fhir.dstu3.model.Patient")
     * <p>
     * Or if the filter URL is known:
     * <p>
     * getFilter("/dhir/Patient.filter")
     */
    /**
     * Loads a ResourceAttributeFilter for the given FHIR destination.
     *
     * @param destination the FHIR destination whose filter properties file to load
     * @return ResourceAttributeFilter the loaded filter, or {@code null} if no filter file exists
     */
    public static ResourceAttributeFilter getFilter(FhirDestination destination) {
        return getFilter(destination.name().toLowerCase());
    }

    private static ResourceAttributeFilter getFilter(String destination) {
        String filterURL = String.format("%s%s%s%s.%s", ROOT_RESOURCE_URL, destination, File.separator, FILE_NAME, FILE_APPEND);

        // dont even try to instantiate a class if the resource is not available.
        URL url = ResourceAttributeFilterFactory.class.getResource(filterURL);
        if (url == null) {
            return null;
        }

        return new ResourceAttributeFilter(url.getPath());
    }


}
