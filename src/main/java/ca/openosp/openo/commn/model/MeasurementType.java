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


package ca.openosp.openo.commn.model;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.apache.commons.lang3.StringUtils;

/**
 * Measurement type definition entity that defines the structure and validation rules
 * for different types of clinical measurements in OpenO EMR.
 * 
 * <p>This class serves as the master configuration for measurement types such as vital signs,
 * lab values, and clinical assessments. Each measurement type defines:</p>
 * <ul>
 *   <li>A unique type code (e.g., "BP" for blood pressure)</li>
 *   <li>Display name for user interfaces (e.g., "Blood Pressure")</li>
 *   <li>Detailed description for clinical context</li>
 *   <li>Measuring instructions for consistent data collection</li>
 *   <li>Validation rules for data entry</li>
 * </ul>
 * 
 * <p>Measurement types are typically configured by system administrators and used
 * throughout the application to ensure consistent measurement data collection
 * and display.</p>
 * 
 * <p>Common measurement types include:</p>
 * <ul>
 *   <li>Vital signs: BP (Blood Pressure), HR (Heart Rate), TEMP (Temperature)</li>
 *   <li>Anthropometric: WT (Weight), HT (Height), BMI (Body Mass Index)</li>
 *   <li>Lab values: GLU (Glucose), CHOL (Cholesterol), HbA1c</li>
 *   <li>Clinical scales: Pain scales, depression screening scores</li>
 * </ul>
 * 
 * @since 2006
 * @see Measurement
 * @see MeasurementMap
 * @see ca.openosp.openo.encounter.oscarMeasurements.data.MeasurementTypes
 */
@Entity
@Table(name = "measurementType")
public class MeasurementType extends AbstractModel<Integer> implements Serializable {

    /** Primary key for the measurement type record. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 
     * Unique type code identifier for this measurement type.
     * Examples: "BP" (blood pressure), "WT" (weight), "HT" (height).
     * This code is used to link measurements to their type definitions.
     */
    private String type;
    /** 
     * Human-readable display name for this measurement type.
     * Used in user interfaces and reports. Examples: "Blood Pressure", "Weight", "Height".
     */
    private String typeDisplayName;
    /** 
     * Detailed description providing clinical context for this measurement type.
     * May include information about normal ranges, clinical significance, or usage notes.
     */
    private String typeDescription;
    /** 
     * Standard instructions for how this measurement should be taken.
     * Provides consistency guidance for healthcare providers.
     * Examples: "Patient sitting, arm at heart level" for blood pressure.
     */
    private String measuringInstruction;
    /** 
     * Validation rules for data entry of this measurement type.
     * May include regular expressions, numeric ranges, or format specifications.
     * Used to ensure data quality and consistency.
     */
    private String validation;

    /** 
     * Timestamp when this measurement type definition was created.
     * Automatically set when the measurement type is first saved.
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date createDate = new Date();


    /**
     * Gets the primary key identifier for this measurement type.
     * 
     * @return Integer the unique measurement type ID, null if not yet persisted
     */
    @Override
    public Integer getId() {
        return id;
    }

    /**
     * Gets the unique type code for this measurement type.
     * 
     * @return String the type code (e.g., "BP", "WT", "HT")
     */
    public String getType() {
        return (type);
    }

    /**
     * Sets the unique type code for this measurement type.
     * Empty or whitespace-only strings are converted to null.
     * 
     * @param type String the type code, will be trimmed
     */
    public void setType(String type) {
        this.type = StringUtils.trimToNull(type);
    }

    /**
     * Gets the human-readable display name for this measurement type.
     * 
     * @return String the display name, or null if not set
     */
    public String getTypeDisplayName() {
        return (typeDisplayName);
    }

    /**
     * Sets the human-readable display name for this measurement type.
     * Used in user interfaces and reports. Empty strings are converted to null.
     * 
     * @param typeDisplayName String the display name
     */
    public void setTypeDisplayName(String typeDisplayName) {
        this.typeDisplayName = StringUtils.trimToNull(typeDisplayName);
    }

    /**
     * Gets the detailed description for this measurement type.
     * 
     * @return String the description, or null if not set
     */
    public String getTypeDescription() {
        return (typeDescription);
    }

    /**
     * Sets the detailed description for this measurement type.
     * Provides clinical context and usage information. Empty strings are converted to null.
     * 
     * @param typeDescription String the detailed description
     */
    public void setTypeDescription(String typeDescription) {
        this.typeDescription = StringUtils.trimToNull(typeDescription);
    }

    /**
     * Gets the standard measuring instructions for this measurement type.
     * 
     * @return String the measuring instructions, or empty string if not set
     */
    public String getMeasuringInstruction() {
        return (measuringInstruction);
    }

    /**
     * Sets the standard measuring instructions for this measurement type.
     * Provides consistency guidance for data collection. Trimmed but empty strings preserved.
     * 
     * @param measuringInstruction String the measuring instructions
     */
    public void setMeasuringInstruction(String measuringInstruction) {
        this.measuringInstruction = StringUtils.trimToEmpty(measuringInstruction);
    }

    /**
     * Gets the validation rules for this measurement type.
     * 
     * @return String the validation rules, or null if no validation defined
     */
    public String getValidation() {
        return (validation);
    }

    /**
     * Sets the validation rules for this measurement type.
     * Rules may include regular expressions, numeric ranges, or format specifications.
     * Empty strings are converted to null.
     * 
     * @param validation String the validation rules
     */
    public void setValidation(String validation) {
        this.validation = StringUtils.trimToNull(validation);
    }

    /**
     * Gets the timestamp when this measurement type was created.
     * 
     * @return Date the creation timestamp
     */
    public Date getCreateDate() {
        return (createDate);
    }
}
