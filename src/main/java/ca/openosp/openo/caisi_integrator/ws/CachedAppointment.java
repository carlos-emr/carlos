package ca.openosp.openo.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlSchemaType;
import org.w3._2001.xmlschema.Adapter1;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.annotation.XmlElement;
import java.util.Calendar;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Represents a cached appointment data transfer object used in CAISI integrator web service communications.
 *
 * This class provides a serializable representation of appointment information for inter-facility
 * healthcare data exchange in the CAISI (Client Access to Integrated Services and Information) integration system.
 * It captures comprehensive appointment details including scheduling information, patient demographics,
 * provider assignments, and facility context for distributed EMR environments.
 *
 * The class uses JAXB annotations for XML serialization/deserialization to support SOAP-based web service
 * communication between OpenO EMR installations and integrated healthcare systems. All temporal fields
 * use Calendar objects with custom XML adapters to ensure proper datetime serialization.
 *
 * @see AbstractModel
 * @see FacilityIdIntegerCompositePk
 * @since 2026-01-23
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cachedAppointment", propOrder = { "appointmentDate", "caisiDemographicId", "caisiProviderId", "createDatetime", "endTime", "facilityIdIntegerCompositePk", "location", "notes", "reason", "remarks", "resources", "startTime", "status", "style", "type", "updateDatetime" })
public class CachedAppointment extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar appointmentDate;
    protected Integer caisiDemographicId;
    protected String caisiProviderId;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar createDatetime;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar endTime;
    protected FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    protected String location;
    protected String notes;
    protected String reason;
    protected String remarks;
    protected String resources;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar startTime;
    protected String status;
    protected String style;
    protected String type;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar updateDatetime;

    /**
     * Gets the scheduled appointment date.
     *
     * @return Calendar the date and time when the appointment is scheduled to occur
     */
    public Calendar getAppointmentDate() {
        return this.appointmentDate;
    }

    /**
     * Sets the scheduled appointment date.
     *
     * @param appointmentDate Calendar the date and time when the appointment is scheduled to occur
     */
    public void setAppointmentDate(final Calendar appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    /**
     * Gets the CAISI demographic identifier for the patient associated with this appointment.
     *
     * @return Integer the unique patient demographic identifier in the CAISI integration system
     */
    public Integer getCaisiDemographicId() {
        return this.caisiDemographicId;
    }

    /**
     * Sets the CAISI demographic identifier for the patient associated with this appointment.
     *
     * @param caisiDemographicId Integer the unique patient demographic identifier in the CAISI integration system
     */
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        this.caisiDemographicId = caisiDemographicId;
    }

    /**
     * Gets the CAISI provider identifier for the healthcare provider assigned to this appointment.
     *
     * @return String the unique provider identifier in the CAISI integration system
     */
    public String getCaisiProviderId() {
        return this.caisiProviderId;
    }

    /**
     * Sets the CAISI provider identifier for the healthcare provider assigned to this appointment.
     *
     * @param caisiProviderId String the unique provider identifier in the CAISI integration system
     */
    public void setCaisiProviderId(final String caisiProviderId) {
        this.caisiProviderId = caisiProviderId;
    }

    /**
     * Gets the timestamp when this appointment record was created.
     *
     * @return Calendar the date and time when the appointment record was first created in the system
     */
    public Calendar getCreateDatetime() {
        return this.createDatetime;
    }

    /**
     * Sets the timestamp when this appointment record was created.
     *
     * @param createDatetime Calendar the date and time when the appointment record was first created in the system
     */
    public void setCreateDatetime(final Calendar createDatetime) {
        this.createDatetime = createDatetime;
    }

    /**
     * Gets the scheduled end time for the appointment.
     *
     * @return Calendar the date and time when the appointment is scheduled to end
     */
    public Calendar getEndTime() {
        return this.endTime;
    }

    /**
     * Sets the scheduled end time for the appointment.
     *
     * @param endTime Calendar the date and time when the appointment is scheduled to end
     */
    public void setEndTime(final Calendar endTime) {
        this.endTime = endTime;
    }

    /**
     * Gets the composite primary key representing the facility and record identifier for this appointment.
     *
     * @return FacilityIdIntegerCompositePk the facility-specific composite primary key for cross-facility appointment tracking
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return this.facilityIdIntegerCompositePk;
    }

    /**
     * Sets the composite primary key representing the facility and record identifier for this appointment.
     *
     * @param facilityIdIntegerCompositePk FacilityIdIntegerCompositePk the facility-specific composite primary key for cross-facility appointment tracking
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        this.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
    }

    /**
     * Gets the physical location where the appointment is scheduled to take place.
     *
     * @return String the location designation (e.g., room number, clinic area, or facility name)
     */
    public String getLocation() {
        return this.location;
    }

    /**
     * Sets the physical location where the appointment is scheduled to take place.
     *
     * @param location String the location designation (e.g., room number, clinic area, or facility name)
     */
    public void setLocation(final String location) {
        this.location = location;
    }

    /**
     * Gets the clinical notes or additional information associated with the appointment.
     *
     * @return String the appointment notes containing clinical observations or administrative information
     */
    public String getNotes() {
        return this.notes;
    }

    /**
     * Sets the clinical notes or additional information associated with the appointment.
     *
     * @param notes String the appointment notes containing clinical observations or administrative information
     */
    public void setNotes(final String notes) {
        this.notes = notes;
    }

    /**
     * Gets the primary reason or purpose for the appointment.
     *
     * @return String the reason code or description indicating the purpose of the appointment
     */
    public String getReason() {
        return this.reason;
    }

    /**
     * Sets the primary reason or purpose for the appointment.
     *
     * @param reason String the reason code or description indicating the purpose of the appointment
     */
    public void setReason(final String reason) {
        this.reason = reason;
    }

    /**
     * Gets additional remarks or comments about the appointment.
     *
     * @return String the supplementary remarks providing additional context or instructions for the appointment
     */
    public String getRemarks() {
        return this.remarks;
    }

    /**
     * Sets additional remarks or comments about the appointment.
     *
     * @param remarks String the supplementary remarks providing additional context or instructions for the appointment
     */
    public void setRemarks(final String remarks) {
        this.remarks = remarks;
    }

    /**
     * Gets the resources allocated or required for the appointment.
     *
     * @return String the resource identifiers for equipment, rooms, or staff assigned to the appointment
     */
    public String getResources() {
        return this.resources;
    }

    /**
     * Sets the resources allocated or required for the appointment.
     *
     * @param resources String the resource identifiers for equipment, rooms, or staff assigned to the appointment
     */
    public void setResources(final String resources) {
        this.resources = resources;
    }

    /**
     * Gets the scheduled start time for the appointment.
     *
     * @return Calendar the date and time when the appointment is scheduled to begin
     */
    public Calendar getStartTime() {
        return this.startTime;
    }

    /**
     * Sets the scheduled start time for the appointment.
     *
     * @param startTime Calendar the date and time when the appointment is scheduled to begin
     */
    public void setStartTime(final Calendar startTime) {
        this.startTime = startTime;
    }

    /**
     * Gets the current status of the appointment.
     *
     * @return String the appointment status code (e.g., scheduled, confirmed, completed, cancelled, no-show)
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * Sets the current status of the appointment.
     *
     * @param status String the appointment status code (e.g., scheduled, confirmed, completed, cancelled, no-show)
     */
    public void setStatus(final String status) {
        this.status = status;
    }

    /**
     * Gets the visual style or display characteristics for the appointment.
     *
     * @return String the style identifier used for appointment display formatting in calendar views
     */
    public String getStyle() {
        return this.style;
    }

    /**
     * Sets the visual style or display characteristics for the appointment.
     *
     * @param style String the style identifier used for appointment display formatting in calendar views
     */
    public void setStyle(final String style) {
        this.style = style;
    }

    /**
     * Gets the appointment type classification.
     *
     * @return String the appointment type code indicating the category of visit (e.g., consultation, follow-up, procedure)
     */
    public String getType() {
        return this.type;
    }

    /**
     * Sets the appointment type classification.
     *
     * @param type String the appointment type code indicating the category of visit (e.g., consultation, follow-up, procedure)
     */
    public void setType(final String type) {
        this.type = type;
    }

    /**
     * Gets the timestamp when this appointment record was last updated.
     *
     * @return Calendar the date and time when the appointment record was most recently modified
     */
    public Calendar getUpdateDatetime() {
        return this.updateDatetime;
    }

    /**
     * Sets the timestamp when this appointment record was last updated.
     *
     * @param updateDatetime Calendar the date and time when the appointment record was most recently modified
     */
    public void setUpdateDatetime(final Calendar updateDatetime) {
        this.updateDatetime = updateDatetime;
    }
}
