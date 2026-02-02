//CHECKSTYLE:OFF
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
 */
package io.github.carlos_emr.carlos.commn.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Measurement mapping entity that links clinical measurements to standardized laboratory codes.
 * This class provides a bridge between OpenO EMR's internal measurement system and 
 * external laboratory information systems using standardized coding systems like LOINC.
 * 
 * <p>The mapping system enables:</p>
 * <ul>
 *   <li>Integration with laboratory information systems (LIS)</li>
 *   <li>Standardized reporting using LOINC codes</li>
 *   <li>Data exchange with external healthcare systems</li>
 *   <li>Flowsheet organization and display grouping</li>
 * </ul>
 * 
 * <p>Key mappings supported:</p>
 * <ul>
 *   <li><strong>LOINC codes</strong>: International standard for laboratory data identification</li>
 *   <li><strong>Local codes</strong>: Lab-specific identifier codes for internal systems</li>
 *   <li><strong>Name mapping</strong>: Human-readable test names and descriptions</li>
 *   <li><strong>Lab type categorization</strong>: Classification by laboratory system type</li>
 * </ul>
 * 
 * <p>Supported laboratory system types include PATHL7 (pathology HL7), 
 * FLOWSHEET (clinical flowsheets), CML, GDML, and ICL systems.</p>
 * 
 * @since 2006
 * @see Measurement
 * @see MeasurementType
 * @see io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.MeasurementMapConfig
 */
@Entity
@Table(name = "measurementMap")
public class MeasurementMap extends AbstractModel<Integer> {

    /**
     * Enumeration of supported laboratory system types.
     * Each type represents a different category of laboratory or clinical system
     * that can be integrated with the measurement mapping system.
     */
    public enum LAB_TYPE {
        /** Pathology HL7 laboratory systems */
        PATHL7, 
        /** Clinical flowsheet systems */
        FLOWSHEET, 
        /** CML laboratory systems */
        CML, 
        /** GDML laboratory systems */
        GDML, 
        /** ICL laboratory systems */
        ICL
    }

    /** Primary key for the measurement map record. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 
     * LOINC (Logical Observation Identifiers Names and Codes) identifier.
     * International standard code for laboratory and clinical observations.
     * Example: "33747-0" for hemoglobin A1c.
     */
    @Column(name = "loinc_code")
    private String loincCode;

    /** 
     * Laboratory-specific identifier code for this measurement.
     * Local code used by the laboratory system for internal identification.
     * May correspond to test catalog numbers or system-specific identifiers.
     */
    @Column(name = "ident_code")
    private String identCode;

    /** 
     * Human-readable name or description for this measurement mapping.
     * Used for display purposes and reporting.
     * Example: "Hemoglobin A1c", "Blood Glucose", "Total Cholesterol".
     */
    private String name;

    /** 
     * Laboratory system type classification.
     * Identifies which type of laboratory or clinical system this mapping applies to.
     * Should correspond to one of the {@link LAB_TYPE} enumeration values.
     */
    @Column(name = "lab_type")
    private String labType;

    /**
     * Gets the primary key identifier for this measurement map.
     * 
     * @return Integer the unique map ID, null if not yet persisted
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the primary key identifier for this measurement map.
     * Typically not called directly - managed by JPA.
     * 
     * @param id Integer the unique map ID
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Gets the LOINC code for this measurement mapping.
     * 
     * @return String the LOINC code, or null if not mapped to LOINC
     */
    public String getLoincCode() {
        return loincCode;
    }

    /**
     * Sets the LOINC code for this measurement mapping.
     * Should be a valid LOINC identifier from the LOINC database.
     * 
     * @param loincCode String the LOINC code (e.g., "33747-0")
     */
    public void setLoincCode(String loincCode) {
        this.loincCode = loincCode;
    }

    /**
     * Gets the laboratory-specific identifier code.
     * 
     * @return String the lab identifier code, or null if not specified
     */
    public String getIdentCode() {
        return identCode;
    }

    /**
     * Sets the laboratory-specific identifier code.
     * This is typically a local code used by the laboratory system.
     * 
     * @param identCode String the lab identifier code
     */
    public void setIdentCode(String identCode) {
        this.identCode = identCode;
    }

    /**
     * Gets the human-readable name for this measurement mapping.
     * 
     * @return String the measurement name, or null if not specified
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the human-readable name for this measurement mapping.
     * Used for display in user interfaces and reports.
     * 
     * @param name String the measurement name or description
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the laboratory system type for this mapping.
     * 
     * @return String the lab type, should correspond to {@link LAB_TYPE} values
     */
    public String getLabType() {
        return labType;
    }

    /**
     * Sets the laboratory system type for this mapping.
     * Should be one of the values from the {@link LAB_TYPE} enumeration.
     * 
     * @param labType String the lab type classification
     */
    public void setLabType(String labType) {
        this.labType = labType;
    }


}
