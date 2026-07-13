package io.github.carlos_emr.carlos.integration.fhir.builder;

import io.github.carlos_emr.carlos.integration.fhir.resources.constants.FhirDestination;
import io.github.carlos_emr.carlos.integration.fhir.model.Destination;
import io.github.carlos_emr.carlos.integration.fhir.resources.Settings;

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

public final class DestinationFactory {

    public static final Destination getDestination(FhirDestination destination) {
        return new Destination(destination.title(), destination.endpoint());
    }

    public static final Destination getDestination(Settings settings) {
        FhirDestination destination = null;
        String title = "";
        String endpoint = "";

        if (settings != null) {
            destination = settings.getFhirDestination();
        }

        //TODO will be changed from an Enum to a Class once the properties files are in effect.
        if (destination != null) {
            title = destination.name();
            endpoint = destination.endpoint();
        }

        return new Destination(title, endpoint);
    }
}
