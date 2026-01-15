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
import java.util.Comparator;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.apache.commons.lang3.StringUtils;

/**
 * Core measurement entity representing clinical measurements captured for patients in OpenO EMR.
 * This class stores vital signs, lab values, and other quantifiable healthcare data points
 * along with associated metadata such as provider, date observed, and measurement instructions.
 * 
 * <p>Measurements are immutable once created - updates are not allowed to maintain data integrity
 * and audit compliance. New measurements should be created for corrections or updated values.</p>
 * 
 * <p>Key relationships:</p>
 * <ul>
 *   <li>Links to {@link ca.openosp.openo.commn.model.Demographic} via demographicId</li>
 *   <li>Links to measurement type definitions via type field</li>
 *   <li>Links to provider who recorded the measurement via providerNo</li>
 *   <li>May be associated with a specific appointment via appointmentNo</li>
 * </ul>
 * 
 * <p>Common measurement types include vital signs (blood pressure, temperature, weight),
 * lab results (glucose, cholesterol), and clinical assessments (pain scales, BMI).</p>
 * 
 * @since 2006
 * @see MeasurementType
 * @see MeasurementsExt
 * @see ca.openosp.openo.commn.model.Demographic
 */
@Entity
@Table(name = "measurements")
public class Measurement extends AbstractModel<Integer> implements Serializable {

    /** Primary key for the measurement record. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 
     * Measurement type identifier linking to measurement type definitions.
     * Examples: "BP" for blood pressure, "WT" for weight, "HT" for height.
     * References measurement types configured in the system.
     */
    @Column(name = "type")
    private String type;

    /** 
     * Foreign key to the patient (demographic) record.
     * Links this measurement to a specific patient in the demographics table.
     */
    @Column(name = "demographicNo")
    private Integer demographicId;

    /** 
     * Provider identifier of the healthcare professional who recorded this measurement.
     * May be null for system-generated or imported measurements.
     */
    @Column(name = "providerNo")
    private String providerNo;

    /** 
     * The actual measurement value or data.
     * Format depends on measurement type - may be numeric ("120/80"), 
     * textual ("Normal"), or structured data.
     */
    @Column(name = "dataField", nullable = false, length = 255)
    private String dataField = "";

    /** 
     * Instructions or context for how the measurement was taken.
     * Examples: "Sitting position", "Fasting", "Post-exercise".
     */
    @Column(name = "measuringInstruction", length = 255)
    private String measuringInstruction = "";

    /** 
     * Additional comments or notes related to this measurement.
     * Free text field for clinical observations or contextual information.
     */
    @Column(name = "comments", length = 255)
    private String comments = "";

    /** 
     * Date and time when the measurement was actually taken or observed.
     * This is the clinical date, which may differ from when it was entered into the system.
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dateObserved")
    private Date dateObserved;

    /** 
     * Optional appointment number if this measurement was taken during a specific appointment.
     * Links to the appointment where this measurement was recorded.
     */
    @Column(name = "appointmentNo")
    private Integer appointmentNo;

    /** 
     * System timestamp when this measurement record was created in the database.
     * Automatically set to current date/time when the measurement is saved.
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dateEntered")
    private Date createDate = new Date();

    /**
     * JPA lifecycle callback that prevents any updates to measurement records.
     * Measurements are immutable once created to maintain data integrity and audit trails.
     * To correct a measurement, create a new measurement record instead.
     * 
     * @throws UnsupportedOperationException always, as updates are not permitted
     */
    @PreUpdate
    protected void jpaPreventChange() {
        throw (new UnsupportedOperationException("This action is not allowed for this type of item."));
    }

    /**
     * Gets the primary key identifier for this measurement.
     * 
     * @return Integer the unique measurement ID, null if not yet persisted
     */
    @Override
    public Integer getId() {
        return id;
    }

    /**
     * Gets the measurement type identifier.
     * 
     * @return String the measurement type code (e.g., "BP", "WT", "HT")
     */
    public String getType() {
        return (type);
    }

    /**
     * Sets the measurement type identifier.
     * Input is automatically trimmed to remove leading/trailing whitespace.
     * 
     * @param type String the measurement type code, will be trimmed
     */
    public void setType(String type) {
        this.type = StringUtils.trimToEmpty(type);
    }

    /**
     * Gets the patient demographic ID this measurement belongs to.
     * 
     * @return Integer the demographic ID, or null if not set
     */
    public Integer getDemographicId() {
        return (demographicId);
    }

    /**
     * Sets the patient demographic ID for this measurement.
     * 
     * @param demographicId Integer the demographic ID of the patient
     */
    public void setDemographicId(Integer demographicId) {
        this.demographicId = demographicId;
    }

    /**
     * Gets the provider number who recorded this measurement.
     * 
     * @return String the provider identifier, or null if not specified
     */
    public String getProviderNo() {
        return (providerNo);
    }

    /**
     * Sets the provider number who recorded this measurement.
     * Empty or whitespace-only strings are converted to null.
     * 
     * @param providerNo String the provider identifier
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = StringUtils.trimToNull(providerNo);
    }

    /**
     * Gets the measurement value or data.
     * 
     * @return String the measurement data, never null (empty string by default)
     */
    public String getDataField() {
        return (dataField);
    }

    /**
     * Sets the measurement value or data.
     * This is the core measurement information - format depends on measurement type.
     * 
     * @param dataField String the measurement data to store
     */
    public void setDataField(String dataField) {
        this.dataField = dataField;
    }

    /**
     * Gets the measuring instructions for this measurement.
     * 
     * @return String the measuring instructions, or empty string if not specified
     */
    public String getMeasuringInstruction() {
        return (measuringInstruction);
    }

    /**
     * Sets the measuring instructions for this measurement.
     * Used to record context about how the measurement was taken.
     * 
     * @param measuringInstruction String the measuring instructions
     */
    public void setMeasuringInstruction(String measuringInstruction) {
        this.measuringInstruction = measuringInstruction;
    }

    /**
     * Gets additional comments associated with this measurement.
     * 
     * @return String the comments, or empty string if no comments
     */
    public String getComments() {
        return (comments);
    }

    /**
     * Sets additional comments for this measurement.
     * Free text field for any additional clinical observations.
     * 
     * @param comments String the comments to associate with this measurement
     */
    public void setComments(String comments) {
        this.comments = comments;
    }

    /**
     * Gets the date and time when this measurement was actually observed.
     * This is the clinical date, which may differ from when entered into system.
     * 
     * @return Date the observation date/time, or null if not specified
     */
    public Date getDateObserved() {
        return (dateObserved);
    }

    /**
     * Sets the date and time when this measurement was observed.
     * Should reflect the actual clinical observation time, not system entry time.
     * 
     * @param dateObserved Date the date/time of observation
     */
    public void setDateObserved(Date dateObserved) {
        this.dateObserved = dateObserved;
    }

    /**
     * Gets the appointment number if this measurement was taken during an appointment.
     * 
     * @return Integer the appointment number, or null if not associated with an appointment
     */
    public Integer getAppointmentNo() {
        return (appointmentNo);
    }

    /**
     * Sets the appointment number for this measurement.
     * Links this measurement to a specific appointment record.
     * 
     * @param appointmentNo Integer the appointment number
     */
    public void setAppointmentNo(Integer appointmentNo) {
        this.appointmentNo = appointmentNo;
    }

    /**
     * Gets the system creation date when this measurement record was entered.
     * This is different from dateObserved - it's when the record was created in the database.
     * 
     * @return Date the database creation timestamp
     */
    public Date getCreateDate() {
        return (createDate);
    }


    /**
     * Sets the system creation date for this measurement record.
     * Typically set automatically when the measurement is first persisted.
     * 
     * @param createDate Date the creation timestamp
     */
    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    /**
     * Comparator for sorting measurements by their observation date.
     * Used to display measurements in chronological order in clinical interfaces.
     * Only compares measurements that have been persisted (non-null IDs).
     * 
     * <p>Usage example:</p>
     * <pre>{@code
     * Collections.sort(measurements, Measurement.DateObservedComparator);
     * }</pre>
     * 
     * @see #getDateObserved()
     */
    public static final Comparator<Measurement> DateObservedComparator = new Comparator<Measurement>() {
        public int compare(Measurement o1, Measurement o2) {
            if (o1.getId() != null && o2.getId() != null) {
                return o1.getDateObserved().compareTo(o2.getDateObserved());
            }
            return 0;
        }
    };
}
