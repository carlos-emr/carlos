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
package io.github.carlos_emr.carlos.commn.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Extended attributes entity for storing additional key-value data associated with measurements.
 * This class provides a flexible mechanism for storing measurement metadata, annotations,
 * or extended properties that don't fit in the core {@link Measurement} entity structure.
 * 
 * <p>The extension system uses a simple key-value pair model where:</p>
 * <ul>
 *   <li>Each measurement can have multiple extension records</li>
 *   <li>Extensions are identified by a keyVal (the attribute name)</li>
 *   <li>The val field stores the corresponding attribute value</li>
 * </ul>
 * 
 * <p>Common use cases include:</p>
 * <ul>
 *   <li>Laboratory result metadata (reference ranges, flags, units)</li>
 *   <li>Device-specific information (meter model, calibration data)</li>
 *   <li>Clinical context (pre/post meal, exercise status)</li>
 *   <li>Quality indicators (accuracy flags, confidence levels)</li>
 *   <li>Integration data (external system IDs, import timestamps)</li>
 * </ul>
 * 
 * <p>Examples:</p>
 * <ul>
 *   <li>keyVal="unit", val="mmHg" for blood pressure measurements</li>
 *   <li>keyVal="reference_range", val="90-120" for lab results</li>
 *   <li>keyVal="device_id", val="OMRON-BP742" for device readings</li>
 * </ul>
 * 
 * @since 2006
 * @see Measurement
 * @see io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao
 */
@Entity
@Table(name = "measurementsExt")
public class MeasurementsExt extends AbstractModel<Integer> {

    /** Primary key for the measurement extension record. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 
     * Foreign key linking this extension to the parent measurement.
     * References the ID of the {@link Measurement} record this extension belongs to.
     */
    @Column(name = "measurement_id")
    private int measurementId;

    /** 
     * The attribute name or key for this extension property.
     * Examples: "unit", "reference_range", "device_id", "flag", "accuracy".
     */
    private String keyVal;

    /** 
     * The attribute value corresponding to the keyVal.
     * Stores the actual data for this extension property as a string.
     */
    private String val;

    /**
     * Default constructor for JPA and general use.
     */
    public MeasurementsExt() {

    }

    /**
     * Convenience constructor that creates an extension linked to a specific measurement.
     * 
     * @param measurementId int the ID of the parent measurement
     */
    public MeasurementsExt(int measurementId) {
        setMeasurementId(measurementId);
    }

    /**
     * Gets the primary key identifier for this measurement extension.
     * 
     * @return Integer the unique extension ID, null if not yet persisted
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the primary key identifier for this measurement extension.
     * Typically not called directly - managed by JPA.
     * 
     * @param id Integer the unique extension ID
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Gets the ID of the parent measurement this extension belongs to.
     * 
     * @return int the measurement ID this extension is associated with
     */
    public int getMeasurementId() {
        return measurementId;
    }

    /**
     * Sets the ID of the parent measurement this extension belongs to.
     * 
     * @param measurementId int the measurement ID to associate this extension with
     */
    public void setMeasurementId(int measurementId) {
        this.measurementId = measurementId;
    }

    /**
     * Gets the attribute name (key) for this extension property.
     * 
     * @return String the key name, or null if not set
     */
    public String getKeyVal() {
        return keyVal;
    }

    /**
     * Sets the attribute name (key) for this extension property.
     * The key identifies what type of additional data this extension contains.
     * 
     * @param keyVal String the key name (e.g., "unit", "reference_range")
     */
    public void setKeyVal(String keyVal) {
        this.keyVal = keyVal;
    }

    /**
     * Gets the attribute value for this extension property.
     * 
     * @return String the attribute value, or null if not set
     */
    public String getVal() {
        return val;
    }

    /**
     * Sets the attribute value for this extension property.
     * The value corresponds to the keyVal and stores the actual extension data.
     * 
     * @param val String the attribute value to store
     */
    public void setVal(String val) {
        this.val = val;
    }


}
