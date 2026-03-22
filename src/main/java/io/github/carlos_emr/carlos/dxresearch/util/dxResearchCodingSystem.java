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


package io.github.carlos_emr.carlos.dxresearch.util;

import io.github.carlos_emr.CarlosProperties;

/**
 * Utility class that provides access to the configured diagnosis coding systems.
 *
 * <p>Reads the {@code dxResearch_coding_sys} property from the CARLOS configuration
 * to determine which coding systems (e.g. "icd9", "ichppccode") are available for
 * diagnosis research. Defaults to "icd9,ichppccode" if the property is not set.</p>
 *
 * @since 2026-03-17
 */
public class dxResearchCodingSystem {

    private String codingSystem;
    private String[] arrCodingSystems;

    /**
     * Constructs a coding system instance by reading the configured coding systems
     * from the {@code dxResearch_coding_sys} application property.
     */
    public dxResearchCodingSystem() {
        codingSystem = CarlosProperties.getInstance().getProperty("dxResearch_coding_sys", "icd9,ichppccode");
        arrCodingSystems = codingSystem.split(",");

    }

    /**
     * Returns the array of configured coding system identifiers.
     *
     * @return String[] the coding system names (e.g. {"icd9", "ichppccode"})
     */
    public String[] getCodingSystems() {
        return arrCodingSystems;
    }

}
