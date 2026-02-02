package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.util.ObjectId;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.util.InternalException;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PersistenceCapable;
import org.apache.openjpa.enhance.PCRegistry;
import org.apache.commons.lang3.StringUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * JPA entity representing cached appointment data synchronized from external healthcare facilities
 * through the CAISI Integrator system. This entity stores appointment information for patients
 * across multiple integrated healthcare facilities, enabling centralized appointment management
 * and scheduling coordination.
 *
 * <p>The CachedAppointment entity is part of the CAISI (Collaborative Applications for Integrated
 * Service Information) Integrator data access layer, which facilitates data sharing between
 * different healthcare EMR installations. Each cached appointment is uniquely identified by a
 * composite key combining the facility identifier and the appointment ID from the source system.</p>
 *
 * <p>This class uses OpenJPA bytecode enhancement to implement the PersistenceCapable interface,
 * which provides field-level access tracking and change detection for JPA persistence operations.
 * The enhancement generates numerous internal methods (prefixed with "pc") that manage the
 * entity's persistence state.</p>
 *
 * <p><strong>Healthcare Context:</strong> Appointment scheduling is a critical function in healthcare
 * delivery. This entity supports multi-facility healthcare networks where patients may have appointments
 * scheduled at different locations within the same healthcare organization or integrated network.
 * By caching appointment data from external facilities, the system enables providers to view a
 * comprehensive schedule across all integrated facilities.</p>
 *
 * <p><strong>Field Trimming:</strong> String fields use Apache Commons StringUtils for automatic
 * trimming. Provider ID uses trimToEmpty() to ensure non-null values, while other string fields
 * use trimToNull() to convert blank strings to null for database storage efficiency.</p>
 *
 * @see FacilityIdIntegerCompositePk
 * @see AbstractModel
 * @see PersistenceCapable
 * @since 2026-01-24
 */
@Entity
public class CachedAppointment extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedAppointment>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityAppointmentPk;
    @Column(nullable = false)
    @Index
    private Integer caisiDemographicId;
    @Column(nullable = false, length = 16)
    private String caisiProviderId;
    @Temporal(TemporalType.DATE)
    private Date appointmentDate;
    @Temporal(TemporalType.TIME)
    private Date startTime;
    @Temporal(TemporalType.TIME)
    private Date endTime;
    @Column(length = 80)
    private String notes;
    @Column(length = 80)
    private String reason;
    @Column(length = 30)
    private String location;
    @Column(length = 255)
    private String resources;
    @Column(length = 10)
    private String type;
    @Column(length = 10)
    private String style;
    @Column(length = 2)
    private String status;
    @Temporal(TemporalType.TIMESTAMP)
    private Date createDatetime;
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateDatetime;
    @Column(length = 50)
    private String remarks;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedAppointment;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Constructs a new CachedAppointment instance with all fields initialized to null.
     * This default constructor is required by JPA for entity instantiation and is used
     * by the OpenJPA persistence framework during entity creation and deserialization.
     *
     * <p>After construction, the setter methods should be used to populate the appointment
     * data with values from the external facility's appointment system.</p>
     */
    public CachedAppointment() {
        this.caisiDemographicId = null;
        this.caisiProviderId = null;
        this.appointmentDate = null;
        this.startTime = null;
        this.endTime = null;
        this.notes = null;
        this.reason = null;
        this.location = null;
        this.resources = null;
        this.type = null;
        this.style = null;
        this.status = null;
        this.createDatetime = null;
        this.updateDatetime = null;
        this.remarks = null;
    }

    /**
     * Retrieves the composite primary key identifying this cached appointment.
     * The composite key consists of the integrator facility ID and the appointment ID
     * from the source CAISI system.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key, or null if not yet set
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityAppointmentPk(this);
    }

    /**
     * Sets the composite primary key identifying this cached appointment.
     * This method should be called during entity initialization to establish the
     * appointment's unique identity within the cached data set.
     *
     * @param facilityAppointmentPk FacilityIdIntegerCompositePk the composite primary key
     *        containing the facility ID and appointment ID from the source system
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityAppointmentPk) {
        pcSetfacilityAppointmentPk(this, facilityAppointmentPk);
    }

    /**
     * Retrieves the scheduled date for this appointment.
     * The appointment date indicates when the patient is scheduled to visit the healthcare
     * facility, stored as a DATE temporal type without time component.
     *
     * @return Date the scheduled appointment date, or null if not set
     */
    public Date getAppointmentDate() {
        return pcGetappointmentDate(this);
    }

    /**
     * Sets the scheduled date for this appointment.
     * This field is mandatory for scheduling and should represent the day on which
     * the patient's appointment is booked.
     *
     * @param appointmentDate Date the scheduled appointment date
     */
    public void setAppointmentDate(final Date appointmentDate) {
        pcSetappointmentDate(this, appointmentDate);
    }

    /**
     * Retrieves the scheduled start time for this appointment.
     * The start time indicates when the appointment begins, stored as a TIME temporal type.
     * This should be used in conjunction with the appointment date to determine the exact
     * date-time of the scheduled visit.
     *
     * @return Date the appointment start time, or null if not set
     */
    public Date getStartTime() {
        return pcGetstartTime(this);
    }

    /**
     * Sets the scheduled start time for this appointment.
     * The start time should represent the time of day when the patient's appointment is
     * scheduled to begin.
     *
     * @param startTime Date the appointment start time
     */
    public void setStartTime(final Date startTime) {
        pcSetstartTime(this, startTime);
    }

    /**
     * Retrieves the scheduled end time for this appointment.
     * The end time indicates when the appointment is scheduled to conclude, stored as a
     * TIME temporal type. The duration between start time and end time determines the
     * length of the appointment slot.
     *
     * @return Date the appointment end time, or null if not set
     */
    public Date getEndTime() {
        return pcGetendTime(this);
    }

    /**
     * Sets the scheduled end time for this appointment.
     * The end time should be after the start time and determines when the appointment
     * slot is scheduled to conclude.
     *
     * @param endTime Date the appointment end time
     */
    public void setEndTime(final Date endTime) {
        pcSetendTime(this, endTime);
    }

    /**
     * Retrieves the CAISI demographic identifier for the patient associated with this appointment.
     * This ID references the patient's record in the CAISI Integrator system and is used to
     * link the appointment to the patient's demographic information. This field is indexed
     * for efficient query performance.
     *
     * @return Integer the CAISI demographic ID, or null if not set
     */
    public Integer getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }

    /**
     * Sets the CAISI demographic identifier for the patient associated with this appointment.
     * This is a required field that establishes the link between the appointment and the
     * patient's record in the CAISI Integrator system.
     *
     * @param caisiDemographicId Integer the CAISI demographic ID
     */
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }

    /**
     * Retrieves the CAISI provider identifier for the healthcare provider scheduled for this appointment.
     * This ID references the provider's record in the CAISI Integrator system and identifies
     * which healthcare provider will be seeing the patient. Maximum length is 16 characters.
     *
     * @return String the CAISI provider ID (never null, returns empty string if not set due to trimToEmpty)
     */
    public String getCaisiProviderId() {
        return pcGetcaisiProviderId(this);
    }

    /**
     * Sets the CAISI provider identifier for the healthcare provider scheduled for this appointment.
     * This is a required field. The value is automatically trimmed to empty string (not null)
     * using StringUtils.trimToEmpty to ensure a non-null value for database storage.
     *
     * @param caisiProviderId String the CAISI provider ID (maximum 16 characters, will be trimmed)
     */
    public void setCaisiProviderId(final String caisiProviderId) {
        pcSetcaisiProviderId(this, StringUtils.trimToEmpty(caisiProviderId));
    }

    /**
     * Retrieves the physical location or room designation for this appointment.
     * This field indicates where within the healthcare facility the appointment will take place,
     * such as a specific exam room, clinic area, or department. Maximum length is 30 characters.
     *
     * @return String the appointment location, or null if not specified
     */
    public String getLocation() {
        return pcGetlocation(this);
    }

    /**
     * Sets the physical location or room designation for this appointment.
     * The value is automatically trimmed to null using StringUtils.trimToNull,
     * converting blank strings to null for efficient database storage.
     *
     * @param location String the appointment location (maximum 30 characters, will be trimmed to null if blank)
     */
    public void setLocation(final String location) {
        pcSetlocation(this, StringUtils.trimToNull(location));
    }

    /**
     * Retrieves the clinical or administrative notes associated with this appointment.
     * Notes may contain special instructions, preparation requirements, or other relevant
     * information for the appointment. Maximum length is 80 characters.
     *
     * @return String the appointment notes, or null if not specified
     */
    public String getNotes() {
        return pcGetnotes(this);
    }

    /**
     * Sets the clinical or administrative notes for this appointment.
     * The value is automatically trimmed to null using StringUtils.trimToNull,
     * converting blank strings to null for efficient database storage.
     *
     * @param notes String the appointment notes (maximum 80 characters, will be trimmed to null if blank)
     */
    public void setNotes(final String notes) {
        pcSetnotes(this, StringUtils.trimToNull(notes));
    }

    /**
     * Retrieves the reason or purpose for this appointment.
     * This field describes why the patient is scheduling the visit, such as "Annual Physical",
     * "Follow-up", or specific symptoms/concerns. Maximum length is 80 characters.
     *
     * @return String the appointment reason, or null if not specified
     */
    public String getReason() {
        return pcGetreason(this);
    }

    /**
     * Sets the reason or purpose for this appointment.
     * The value is automatically trimmed to null using StringUtils.trimToNull,
     * converting blank strings to null for efficient database storage.
     *
     * @param reason String the appointment reason (maximum 80 characters, will be trimmed to null if blank)
     */
    public void setReason(final String reason) {
        pcSetreason(this, StringUtils.trimToNull(reason));
    }

    /**
     * Retrieves additional remarks or comments about this appointment.
     * Remarks provide a free-text field for any supplementary information that doesn't
     * fit in other structured fields. Maximum length is 50 characters.
     *
     * @return String the appointment remarks, or null if not specified
     */
    public String getRemarks() {
        return pcGetremarks(this);
    }

    /**
     * Sets additional remarks or comments for this appointment.
     * The value is automatically trimmed to null using StringUtils.trimToNull,
     * converting blank strings to null for efficient database storage.
     *
     * @param remarks String the appointment remarks (maximum 50 characters, will be trimmed to null if blank)
     */
    public void setRemarks(final String remarks) {
        pcSetremarks(this, StringUtils.trimToNull(remarks));
    }

    /**
     * Retrieves the resources or equipment required for this appointment.
     * This field may list medical equipment, interpreters, or other special resources
     * needed for the appointment. Maximum length is 255 characters.
     *
     * @return String the required resources, or null if not specified
     */
    public String getResources() {
        return pcGetresources(this);
    }

    /**
     * Sets the resources or equipment required for this appointment.
     * The value is automatically trimmed to null using StringUtils.trimToNull,
     * converting blank strings to null for efficient database storage.
     *
     * @param resources String the required resources (maximum 255 characters, will be trimmed to null if blank)
     */
    public void setResources(final String resources) {
        pcSetresources(this, StringUtils.trimToNull(resources));
    }

    /**
     * Retrieves the current status of this appointment.
     * Status indicates the appointment's state such as scheduled, confirmed, cancelled,
     * completed, or no-show. Maximum length is 2 characters, typically using status codes.
     *
     * @return String the appointment status code, or null if not specified
     */
    public String getStatus() {
        return pcGetstatus(this);
    }

    /**
     * Sets the current status of this appointment.
     * The value is automatically trimmed to null using StringUtils.trimToNull,
     * converting blank strings to null for efficient database storage.
     *
     * @param status String the appointment status code (maximum 2 characters, will be trimmed to null if blank)
     */
    public void setStatus(final String status) {
        pcSetstatus(this, StringUtils.trimToNull(status));
    }

    /**
     * Retrieves the display style or visual classification for this appointment.
     * Style may be used to control how the appointment appears in calendar views or
     * scheduling interfaces, such as color coding or icon selection. Maximum length is 10 characters.
     *
     * @return String the appointment style, or null if not specified
     */
    public String getStyle() {
        return pcGetstyle(this);
    }

    /**
     * Sets the display style or visual classification for this appointment.
     * The value is automatically trimmed to null using StringUtils.trimToNull,
     * converting blank strings to null for efficient database storage.
     *
     * @param style String the appointment style (maximum 10 characters, will be trimmed to null if blank)
     */
    public void setStyle(final String style) {
        pcSetstyle(this, StringUtils.trimToNull(style));
    }

    /**
     * Retrieves the appointment type classification.
     * Type categorizes appointments by their nature, such as "Office Visit", "Telephone Consult",
     * "Virtual Visit", or other appointment categories used by the healthcare facility.
     * Maximum length is 10 characters.
     *
     * @return String the appointment type, or null if not specified
     */
    public String getType() {
        return pcGettype(this);
    }

    /**
     * Sets the appointment type classification.
     * The value is automatically trimmed to null using StringUtils.trimToNull,
     * converting blank strings to null for efficient database storage.
     *
     * @param type String the appointment type (maximum 10 characters, will be trimmed to null if blank)
     */
    public void setType(final String type) {
        pcSettype(this, StringUtils.trimToNull(type));
    }

    /**
     * Retrieves the timestamp when this appointment record was created.
     * This field provides an audit trail of when the appointment was initially scheduled
     * or when the cached record was first synchronized from the external facility system.
     *
     * @return Date the creation timestamp, or null if not set
     */
    public Date getCreateDatetime() {
        return pcGetcreateDatetime(this);
    }

    /**
     * Sets the timestamp when this appointment record was created.
     * This field should be set when the appointment is first created or when the cached
     * record is initially synchronized from the external facility.
     *
     * @param createDatetime Date the creation timestamp
     */
    public void setCreateDatetime(final Date createDatetime) {
        pcSetcreateDatetime(this, createDatetime);
    }

    /**
     * Retrieves the timestamp when this appointment record was last updated.
     * This field provides an audit trail of the most recent modification to the appointment
     * data, whether from rescheduling, status changes, or cache synchronization updates.
     *
     * @return Date the last update timestamp, or null if never updated
     */
    public Date getUpdateDatetime() {
        return pcGetupdateDatetime(this);
    }

    /**
     * Sets the timestamp when this appointment record was last updated.
     * This field should be updated whenever any appointment data is modified or when
     * the cached record is re-synchronized from the external facility.
     *
     * @param updateDatetime Date the last update timestamp
     */
    public void setUpdateDatetime(final Date updateDatetime) {
        pcSetupdateDatetime(this, updateDatetime);
    }

    /**
     * Compares this appointment to another CachedAppointment for ordering purposes.
     * Appointments are ordered by their CAISI item ID from the composite primary key.
     * This enables consistent sorting of appointments when displayed in lists or collections.
     *
     * @param o CachedAppointment the appointment to compare against
     * @return int negative if this appointment's ID is less, zero if equal, positive if greater
     */
    @Override
    public int compareTo(final CachedAppointment o) {
        return pcGetfacilityAppointmentPk(this).getCaisiItemId() - pcGetfacilityAppointmentPk(o).getCaisiItemId();
    }

    /**
     * Retrieves the composite primary key identifier for this appointment.
     * This method is required by the AbstractModel base class and provides access to
     * the entity's unique identifier for persistence operations.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityAppointmentPk(this);
    }

    /**
     * Returns the OpenJPA bytecode enhancement contract version.
     * This method is part of the PersistenceCapable interface and is used by OpenJPA
     * to verify compatibility between the enhanced entity class and the runtime environment.
     *
     * @return int the enhancement contract version (always 2 for this entity)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 4187303396737281637L;
        CachedAppointment.pcFieldNames = new String[] { "appointmentDate", "caisiDemographicId", "caisiProviderId", "createDatetime", "endTime", "facilityAppointmentPk", "location", "notes", "reason", "remarks", "resources", "startTime", "status", "style", "type", "updateDatetime" };
        CachedAppointment.pcFieldTypes = new Class[] { (CachedAppointment.class$Ljava$util$Date != null) ? CachedAppointment.class$Ljava$util$Date : (CachedAppointment.class$Ljava$util$Date = class$("java.util.Date")), (CachedAppointment.class$Ljava$lang$Integer != null) ? CachedAppointment.class$Ljava$lang$Integer : (CachedAppointment.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$util$Date != null) ? CachedAppointment.class$Ljava$util$Date : (CachedAppointment.class$Ljava$util$Date = class$("java.util.Date")), (CachedAppointment.class$Ljava$util$Date != null) ? CachedAppointment.class$Ljava$util$Date : (CachedAppointment.class$Ljava$util$Date = class$("java.util.Date")), (CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$util$Date != null) ? CachedAppointment.class$Ljava$util$Date : (CachedAppointment.class$Ljava$util$Date = class$("java.util.Date")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$util$Date != null) ? CachedAppointment.class$Ljava$util$Date : (CachedAppointment.class$Ljava$util$Date = class$("java.util.Date")) };
        CachedAppointment.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$CachedAppointment != null) ? CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$CachedAppointment : (CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$CachedAppointment = class$("ca.openosp.openo.caisi_integrator.dao.CachedAppointment")), CachedAppointment.pcFieldNames, CachedAppointment.pcFieldTypes, CachedAppointment.pcFieldFlags, CachedAppointment.pcPCSuperclass, "CachedAppointment", (PersistenceCapable)new CachedAppointment());
    }
    
    static /* synthetic */ Class class$(final String className) {
        try {
            return Class.forName(className);
        }
        catch (final ClassNotFoundException ex) {
            throw new NoClassDefFoundError(ex.getMessage());
        }
    }

    /**
     * Clears all persistent fields to their default null values.
     * This method is part of the OpenJPA persistence capability and is used during
     * entity state management operations such as detachment or clearing.
     */
    protected void pcClearFields() {
        this.appointmentDate = null;
        this.caisiDemographicId = null;
        this.caisiProviderId = null;
        this.createDatetime = null;
        this.endTime = null;
        this.facilityAppointmentPk = null;
        this.location = null;
        this.notes = null;
        this.reason = null;
        this.remarks = null;
        this.resources = null;
        this.startTime = null;
        this.status = null;
        this.style = null;
        this.type = null;
        this.updateDatetime = null;
    }

    /**
     * Creates a new instance of CachedAppointment with the specified state manager and object ID.
     * This method is part of the PersistenceCapable interface and is used by OpenJPA
     * during entity instantiation and persistence operations.
     *
     * @param pcStateManager StateManager the state manager to attach to the new instance
     * @param o Object the object ID to use for key field initialization
     * @param b boolean if true, clears all fields after initialization
     * @return PersistenceCapable the newly created CachedAppointment instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedAppointment cachedAppointment = new CachedAppointment();
        if (b) {
            cachedAppointment.pcClearFields();
        }
        cachedAppointment.pcStateManager = pcStateManager;
        cachedAppointment.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedAppointment;
    }

    /**
     * Creates a new instance of CachedAppointment with the specified state manager.
     * This method is part of the PersistenceCapable interface and is used by OpenJPA
     * during entity instantiation when no object ID is available.
     *
     * @param pcStateManager StateManager the state manager to attach to the new instance
     * @param b boolean if true, clears all fields after initialization
     * @return PersistenceCapable the newly created CachedAppointment instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedAppointment cachedAppointment = new CachedAppointment();
        if (b) {
            cachedAppointment.pcClearFields();
        }
        cachedAppointment.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedAppointment;
    }

    /**
     * Returns the count of managed persistent fields in this entity.
     * This method is used by OpenJPA to determine how many fields are under
     * persistence management for state tracking and change detection.
     *
     * @return int the number of managed fields (16 for this entity)
     */
    protected static int pcGetManagedFieldCount() {
        return 16;
    }

    /**
     * Replaces a single field value from the state manager during persistence operations.
     * This method is part of the PersistenceCapable interface and is invoked by OpenJPA
     * when restoring entity state from the database or during refresh operations.
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedAppointment.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.appointmentDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.caisiDemographicId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.caisiProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.createDatetime = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.endTime = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.facilityAppointmentPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.location = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.notes = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.reason = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.remarks = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 10: {
                this.resources = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 11: {
                this.startTime = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 12: {
                this.status = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 13: {
                this.style = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 14: {
                this.type = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 15: {
                this.updateDatetime = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple field values from the state manager during persistence operations.
     * This method delegates to pcReplaceField for each field index in the array,
     * providing an efficient batch operation for state restoration.
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single field value to the state manager during persistence operations.
     * This method is part of the PersistenceCapable interface and is invoked by OpenJPA
     * when saving entity state to the database or during flush operations.
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedAppointment.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.appointmentDate);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiDemographicId);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.caisiProviderId);
                return;
            }
            case 3: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.createDatetime);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.endTime);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityAppointmentPk);
                return;
            }
            case 6: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.location);
                return;
            }
            case 7: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.notes);
                return;
            }
            case 8: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.reason);
                return;
            }
            case 9: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.remarks);
                return;
            }
            case 10: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.resources);
                return;
            }
            case 11: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.startTime);
                return;
            }
            case 12: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.status);
                return;
            }
            case 13: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.style);
                return;
            }
            case 14: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.type);
                return;
            }
            case 15: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.updateDatetime);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Provides multiple field values to the state manager during persistence operations.
     * This method delegates to pcProvideField for each field index in the array,
     * providing an efficient batch operation for state persistence.
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }

    /**
     * Copies a single field value from another CachedAppointment instance.
     * This method is used by OpenJPA during entity merge and copy operations
     * to transfer field values between entity instances.
     *
     * @param cachedAppointment CachedAppointment the source appointment to copy from
     * @param n int the field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final CachedAppointment cachedAppointment, final int n) {
        final int n2 = n - CachedAppointment.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.appointmentDate = cachedAppointment.appointmentDate;
                return;
            }
            case 1: {
                this.caisiDemographicId = cachedAppointment.caisiDemographicId;
                return;
            }
            case 2: {
                this.caisiProviderId = cachedAppointment.caisiProviderId;
                return;
            }
            case 3: {
                this.createDatetime = cachedAppointment.createDatetime;
                return;
            }
            case 4: {
                this.endTime = cachedAppointment.endTime;
                return;
            }
            case 5: {
                this.facilityAppointmentPk = cachedAppointment.facilityAppointmentPk;
                return;
            }
            case 6: {
                this.location = cachedAppointment.location;
                return;
            }
            case 7: {
                this.notes = cachedAppointment.notes;
                return;
            }
            case 8: {
                this.reason = cachedAppointment.reason;
                return;
            }
            case 9: {
                this.remarks = cachedAppointment.remarks;
                return;
            }
            case 10: {
                this.resources = cachedAppointment.resources;
                return;
            }
            case 11: {
                this.startTime = cachedAppointment.startTime;
                return;
            }
            case 12: {
                this.status = cachedAppointment.status;
                return;
            }
            case 13: {
                this.style = cachedAppointment.style;
                return;
            }
            case 14: {
                this.type = cachedAppointment.type;
                return;
            }
            case 15: {
                this.updateDatetime = cachedAppointment.updateDatetime;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies multiple field values from another entity instance.
     * This method delegates to pcCopyField for each field index, enabling efficient
     * batch copying during merge and detachment operations.
     *
     * @param o Object the source appointment to copy from (must be a CachedAppointment)
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source has a different state manager
     * @throws IllegalStateException if this entity has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedAppointment cachedAppointment = (CachedAppointment)o;
        if (cachedAppointment.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedAppointment, array[i]);
        }
    }

    /**
     * Retrieves the generic context object from the state manager.
     * This method provides access to the OpenJPA context associated with this entity,
     * which may contain framework-specific metadata and configuration.
     *
     * @return Object the generic context, or null if no state manager is attached
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the OpenJPA object ID for this entity.
     * The object ID uniquely identifies this entity within the persistence context
     * and is used for entity lookup and caching operations.
     *
     * @return Object the OpenJPA object ID, or null if no state manager is attached
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks whether this entity has been marked for deletion.
     * This method is used by OpenJPA to track entities that have been removed
     * but may not yet be deleted from the database.
     *
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks whether this entity has been modified since it was loaded or last saved.
     * This method is used by OpenJPA to determine if the entity needs to be persisted
     * during transaction commit. Triggers a dirty check through RedefinitionHelper.
     *
     * @return boolean true if the entity has unsaved changes, false otherwise
     */
    public boolean pcIsDirty() {
        if (this.pcStateManager == null) {
            return false;
        }
        final StateManager pcStateManager = this.pcStateManager;
        RedefinitionHelper.dirtyCheck(pcStateManager);
        return pcStateManager.isDirty();
    }

    /**
     * Checks whether this entity is newly created and not yet persisted to the database.
     * New entities have not been assigned a database-generated ID and have not been
     * saved in a transaction.
     *
     * @return boolean true if the entity is new and unpersisted, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks whether this entity is managed by the persistence context.
     * Persistent entities are tracked by OpenJPA and will have their changes
     * automatically synchronized with the database during transaction commit.
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks whether this entity is associated with a transaction.
     * Transactional entities are being tracked within a database transaction
     * and their changes will be committed or rolled back with the transaction.
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks whether this entity is currently being serialized.
     * This method is used during object serialization to determine if special
     * handling is needed for the entity's state.
     *
     * @return boolean true if the entity is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a specific field as dirty, indicating it has been modified.
     * This method notifies the state manager that a field has changed and needs
     * to be persisted during the next flush or commit operation.
     *
     * @param s String the field name that has been modified
     */
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }

    /**
     * Retrieves the OpenJPA state manager associated with this entity.
     * The state manager handles all persistence operations, field access tracking,
     * and dirty checking for this entity instance.
     *
     * @return StateManager the state manager, or null if not attached
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version object for this entity used in optimistic locking.
     * The version is incremented each time the entity is updated, preventing
     * lost update problems in concurrent access scenarios.
     *
     * @return Object the version object, or null if no state manager is attached
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces the current state manager with a new one.
     * This method is used by OpenJPA during entity attachment, detachment, and
     * context transfer operations. If a state manager already exists, it delegates
     * the replacement to the current state manager.
     *
     * @param pcStateManager StateManager the new state manager to attach
     * @throws SecurityException if the state manager replacement is not allowed
     */
    public void pcReplaceStateManager(final StateManager pcStateManager) throws SecurityException {
        if (this.pcStateManager != null) {
            this.pcStateManager = this.pcStateManager.replaceStateManager(pcStateManager);
            return;
        }
        this.pcStateManager = pcStateManager;
    }

    /**
     * Copies key fields to an object ID using a field supplier.
     * This method is not supported for this entity type and will throw an InternalException.
     * The entity uses embedded ID which requires different object ID handling.
     *
     * @param fieldSupplier FieldSupplier the field supplier for copying values
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields to an object ID.
     * This method is not supported for this entity type and will throw an InternalException.
     * The entity uses embedded ID which requires different object ID handling.
     *
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields from an OpenJPA object ID using a field consumer.
     * This method extracts the embedded composite key from the object ID and stores it
     * using the provided field consumer at the appropriate field index.
     *
     * @param fieldConsumer FieldConsumer the field consumer for storing values
     * @param o Object the source OpenJPA ObjectId containing the key
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(5 + CachedAppointment.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key fields from an OpenJPA object ID to this entity's composite key field.
     * This method extracts the embedded composite key from the object ID and assigns it
     * to the facilityAppointmentPk field.
     *
     * @param o Object the source OpenJPA ObjectId containing the embedded key
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityAppointmentPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     * This method is not supported for this entity type because OpenJPA ObjectId
     * does not provide the required string constructor for this entity's ID type.
     *
     * @param o Object the string representation of the object ID
     * @return Object (never returns - always throws exception)
     * @throws IllegalArgumentException always thrown as this operation is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedAppointment\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new OpenJPA object ID instance for this entity.
     * The object ID encapsulates the embedded composite key (facilityAppointmentPk)
     * and is used by OpenJPA for entity identification and caching.
     *
     * @return Object the newly created OpenJPA ObjectId containing this entity's key
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$CachedAppointment != null) ? CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$CachedAppointment : (CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$CachedAppointment = class$("ca.openosp.openo.caisi_integrator.dao.CachedAppointment")), (Object)this.facilityAppointmentPk);
    }
    
    private static final Date pcGetappointmentDate(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.appointmentDate;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 0);
        return cachedAppointment.appointmentDate;
    }
    
    private static final void pcSetappointmentDate(final CachedAppointment cachedAppointment, final Date appointmentDate) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.appointmentDate = appointmentDate;
            return;
        }
        cachedAppointment.pcStateManager.settingObjectField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 0, (Object)cachedAppointment.appointmentDate, (Object)appointmentDate, 0);
    }
    
    private static final Integer pcGetcaisiDemographicId(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.caisiDemographicId;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 1);
        return cachedAppointment.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedAppointment cachedAppointment, final Integer caisiDemographicId) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedAppointment.pcStateManager.settingObjectField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 1, (Object)cachedAppointment.caisiDemographicId, (Object)caisiDemographicId, 0);
    }
    
    private static final String pcGetcaisiProviderId(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.caisiProviderId;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 2);
        return cachedAppointment.caisiProviderId;
    }
    
    private static final void pcSetcaisiProviderId(final CachedAppointment cachedAppointment, final String caisiProviderId) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.caisiProviderId = caisiProviderId;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 2, cachedAppointment.caisiProviderId, caisiProviderId, 0);
    }
    
    private static final Date pcGetcreateDatetime(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.createDatetime;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 3);
        return cachedAppointment.createDatetime;
    }
    
    private static final void pcSetcreateDatetime(final CachedAppointment cachedAppointment, final Date createDatetime) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.createDatetime = createDatetime;
            return;
        }
        cachedAppointment.pcStateManager.settingObjectField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 3, (Object)cachedAppointment.createDatetime, (Object)createDatetime, 0);
    }
    
    private static final Date pcGetendTime(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.endTime;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 4);
        return cachedAppointment.endTime;
    }
    
    private static final void pcSetendTime(final CachedAppointment cachedAppointment, final Date endTime) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.endTime = endTime;
            return;
        }
        cachedAppointment.pcStateManager.settingObjectField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 4, (Object)cachedAppointment.endTime, (Object)endTime, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityAppointmentPk(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.facilityAppointmentPk;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 5);
        return cachedAppointment.facilityAppointmentPk;
    }
    
    private static final void pcSetfacilityAppointmentPk(final CachedAppointment cachedAppointment, final FacilityIdIntegerCompositePk facilityAppointmentPk) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.facilityAppointmentPk = facilityAppointmentPk;
            return;
        }
        cachedAppointment.pcStateManager.settingObjectField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 5, (Object)cachedAppointment.facilityAppointmentPk, (Object)facilityAppointmentPk, 0);
    }
    
    private static final String pcGetlocation(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.location;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 6);
        return cachedAppointment.location;
    }
    
    private static final void pcSetlocation(final CachedAppointment cachedAppointment, final String location) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.location = location;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 6, cachedAppointment.location, location, 0);
    }
    
    private static final String pcGetnotes(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.notes;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 7);
        return cachedAppointment.notes;
    }
    
    private static final void pcSetnotes(final CachedAppointment cachedAppointment, final String notes) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.notes = notes;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 7, cachedAppointment.notes, notes, 0);
    }
    
    private static final String pcGetreason(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.reason;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 8);
        return cachedAppointment.reason;
    }
    
    private static final void pcSetreason(final CachedAppointment cachedAppointment, final String reason) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.reason = reason;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 8, cachedAppointment.reason, reason, 0);
    }
    
    private static final String pcGetremarks(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.remarks;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 9);
        return cachedAppointment.remarks;
    }
    
    private static final void pcSetremarks(final CachedAppointment cachedAppointment, final String remarks) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.remarks = remarks;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 9, cachedAppointment.remarks, remarks, 0);
    }
    
    private static final String pcGetresources(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.resources;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 10);
        return cachedAppointment.resources;
    }
    
    private static final void pcSetresources(final CachedAppointment cachedAppointment, final String resources) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.resources = resources;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 10, cachedAppointment.resources, resources, 0);
    }
    
    private static final Date pcGetstartTime(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.startTime;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 11);
        return cachedAppointment.startTime;
    }
    
    private static final void pcSetstartTime(final CachedAppointment cachedAppointment, final Date startTime) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.startTime = startTime;
            return;
        }
        cachedAppointment.pcStateManager.settingObjectField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 11, (Object)cachedAppointment.startTime, (Object)startTime, 0);
    }
    
    private static final String pcGetstatus(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.status;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 12);
        return cachedAppointment.status;
    }
    
    private static final void pcSetstatus(final CachedAppointment cachedAppointment, final String status) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.status = status;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 12, cachedAppointment.status, status, 0);
    }
    
    private static final String pcGetstyle(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.style;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 13);
        return cachedAppointment.style;
    }
    
    private static final void pcSetstyle(final CachedAppointment cachedAppointment, final String style) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.style = style;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 13, cachedAppointment.style, style, 0);
    }
    
    private static final String pcGettype(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.type;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 14);
        return cachedAppointment.type;
    }
    
    private static final void pcSettype(final CachedAppointment cachedAppointment, final String type) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.type = type;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 14, cachedAppointment.type, type, 0);
    }
    
    private static final Date pcGetupdateDatetime(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.updateDatetime;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 15);
        return cachedAppointment.updateDatetime;
    }
    
    private static final void pcSetupdateDatetime(final CachedAppointment cachedAppointment, final Date updateDatetime) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.updateDatetime = updateDatetime;
            return;
        }
        cachedAppointment.pcStateManager.settingObjectField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 15, (Object)cachedAppointment.updateDatetime, (Object)updateDatetime, 0);
    }

    /**
     * Checks whether this entity is in a detached state.
     * A detached entity has been previously managed but is no longer associated with
     * a persistence context. Returns null if the detached state cannot be definitively determined.
     *
     * @return Boolean TRUE if detached, FALSE if attached, null if state is indeterminate
     */
    public Boolean pcIsDetached() {
        if (this.pcStateManager != null) {
            if (this.pcStateManager.isDetached()) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
        else {
            if (this.pcGetDetachedState() != null && this.pcGetDetachedState() != PersistenceCapable.DESERIALIZED) {
                return Boolean.TRUE;
            }
            if (!this.pcisDetachedStateDefinitive()) {
                return null;
            }
            if (this.pcGetDetachedState() == null) {
                return Boolean.FALSE;
            }
            return null;
        }
    }

    /**
     * Determines if the detached state can be definitively determined.
     * This entity always returns false, indicating that the detached state
     * may be indeterminate in some cases.
     *
     * @return boolean always returns false for this entity
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Retrieves the detached state object for this entity.
     * The detached state tracks information about the entity's previous attachment
     * to a persistence context, used during merge and reattachment operations.
     *
     * @return Object the detached state, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object for this entity.
     * This method is called by OpenJPA when the entity is detached from a persistence
     * context or during deserialization to track the entity's detachment state.
     *
     * @param pcDetachedState Object the detached state to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method called during entity serialization.
     * Ensures proper handling of the entity's state during serialization by clearing
     * the detached state if the entity is being serialized by the persistence framework.
     *
     * @param objectOutputStream ObjectOutputStream the stream to write the object to
     * @throws IOException if an I/O error occurs during serialization
     */
    private void writeObject(final ObjectOutputStream objectOutputStream) throws IOException {
        final boolean pcSerializing = this.pcSerializing();
        objectOutputStream.defaultWriteObject();
        if (pcSerializing) {
            this.pcSetDetachedState(null);
        }
    }

    /**
     * Custom deserialization method called during entity deserialization.
     * Marks the entity as deserialized by setting the detached state to the DESERIALIZED
     * constant, then performs default deserialization of the object's fields.
     *
     * @param objectInputStream ObjectInputStream the stream to read the object from
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if the class of a serialized object cannot be found
     */
    private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        this.pcSetDetachedState(PersistenceCapable.DESERIALIZED);
        objectInputStream.defaultReadObject();
    }
}
