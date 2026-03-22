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
package io.github.carlos_emr.carlos.labs;

/**
 * Value object that encapsulates a laboratory result identifier and its associated type.
 *
 * <p>This class pairs a numeric lab result ID with a string-based lab type classifier,
 * providing a lightweight container for identifying specific lab results across the
 * CARLOS EMR system. Lab types typically correspond to different laboratory information
 * system formats (e.g., HL7, MDS, CML) or processing pipelines.</p>
 *
 * @since 2001-01-01
 */
public class LabIdAndType {
    private int labId;
    private String labType;

    /**
     * Constructs an empty LabIdAndType with default values.
     */
    public LabIdAndType() {

    }

    /**
     * Constructs a LabIdAndType with the specified lab ID and type.
     *
     * @param labId int the unique identifier of the laboratory result
     * @param labType String the type classifier for the lab result (e.g., "HL7", "MDS", "CML")
     */
    public LabIdAndType(int labId, String labType) {
        setLabId(labId);
        setLabType(labType);
    }

    /**
     * Returns the unique identifier of the laboratory result.
     *
     * @return int the lab result ID
     */
    public int getLabId() {
        return labId;
    }

    /**
     * Sets the unique identifier of the laboratory result.
     *
     * @param labId int the lab result ID to set
     */
    public void setLabId(int labId) {
        this.labId = labId;
    }

    /**
     * Returns the type classifier for the laboratory result.
     *
     * @return String the lab type (e.g., "HL7", "MDS", "CML")
     */
    public String getLabType() {
        return labType;
    }

    /**
     * Sets the type classifier for the laboratory result.
     *
     * @param labType String the lab type to set
     */
    public void setLabType(String labType) {
        this.labType = labType;
    }


}