package io.github.carlos_emr.carlos.integration.fhir.resources.types;

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
 * Represents a Public Health Unit (PHU) for Ontario immunization reporting.
 *
 * <p>Contains the PHU identifier, name, and the FHIR naming system URI
 * used in Organization resource identifiers for DHIR submissions.</p>
 *
 * @since 2026-03-17
 */
public final class PublicHealthUnitType {

    public static enum PhuKey {default_phu, phu}

    private String id;
    private String name;
    private String systemURI;

    /**
     * Constructs a PublicHealthUnitType with the given ID and name.
     *
     * @param id the PHU identifier (e.g., Panorama PHU ID)
     * @param name the PHU display name
     */
    public PublicHealthUnitType(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSystemURI() {
        return systemURI;
    }

    public void setSystemURI(String systemURI) {
        this.systemURI = systemURI;
    }

}
