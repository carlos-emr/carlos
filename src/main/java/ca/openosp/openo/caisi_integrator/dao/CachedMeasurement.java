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
 * Cached clinical measurement entity for the CAISI Integrator system.
 *
 * This entity represents cached clinical measurement data retrieved from remote healthcare facilities
 * through the CAISI (Client Access to Integrated Services and Information) Integrator. The integrator
 * allows OpenO EMR instances to share patient data across multiple facilities while maintaining local
 * control over patient information.
 *
 * Measurements stored in this cache include vital signs, clinical observations, and other quantitative
 * health data points. The cache mechanism improves performance by reducing redundant remote data requests
 * while ensuring that healthcare providers have access to comprehensive patient measurement history across
 * the integrated healthcare network.
 *
 * This class is enhanced by OpenJPA for persistence management and includes specialized field accessors
 * that integrate with the JPA StateManager for transaction management and dirty checking. The entity uses
 * a composite primary key combining facility identifier and measurement item identifier to ensure unique
 * identification across the integrated system.
 *
 * @see FacilityIdIntegerCompositePk
 * @see AbstractModel
 * @see PersistenceCapable
 * @since 2026-01-24
 */
@Entity
public class CachedMeasurement extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedMeasurement>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityMeasurementPk;
    @Column(nullable = false, length = 50)
    private String type;
    @Column(nullable = false)
    @Index
    private Integer caisiDemographicId;
    @Column(nullable = false, length = 16)
    private String caisiProviderId;
    private String dataField;
    private String measuringInstruction;
    private String comments;
    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateObserved;
    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateEntered;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurement;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default no-argument constructor.
     *
     * Initializes a new CachedMeasurement instance with all fields set to null.
     * This constructor is required by JPA for entity instantiation and is also used
     * by OpenJPA's persistence capability enhancement mechanisms.
     */
    public CachedMeasurement() {
        this.type = null;
        this.caisiDemographicId = null;
        this.caisiProviderId = null;
        this.dataField = null;
        this.measuringInstruction = null;
        this.comments = null;
        this.dateObserved = null;
        this.dateEntered = null;
    }

    /**
     * Retrieves the composite primary key for this cached measurement.
     *
     * The composite key uniquely identifies this measurement across the CAISI Integrator network
     * by combining the facility identifier with the measurement item identifier.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key containing facility and item identifiers
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityMeasurementPk(this);
    }

    /**
     * Sets the composite primary key for this cached measurement.
     *
     * @param facilityMeasurementPk FacilityIdIntegerCompositePk the composite key to set, containing
     *                             facility identifier and measurement item identifier
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityMeasurementPk) {
        pcSetfacilityMeasurementPk(this, facilityMeasurementPk);
    }

    /**
     * Retrieves the measurement type identifier.
     *
     * The type field identifies the specific clinical measurement being recorded (e.g., "BP" for blood
     * pressure, "WT" for weight, "HT" for height). This corresponds to standard measurement type codes
     * used throughout the OpenO EMR system.
     *
     * @return String the measurement type code, maximum 50 characters
     */
    public String getType() {
        return pcGettype(this);
    }

    /**
     * Sets the measurement type identifier.
     *
     * The type value is automatically trimmed of leading and trailing whitespace. If null is provided,
     * it will be converted to an empty string.
     *
     * @param type String the measurement type code to set, maximum 50 characters (will be trimmed)
     */
    public void setType(final String type) {
        pcSettype(this, StringUtils.trimToEmpty(type));
    }

    /**
     * Retrieves the CAISI demographic identifier for the patient.
     *
     * This identifier references the patient record in the CAISI Integrator system, allowing correlation
     * of measurements with patient demographics across multiple healthcare facilities.
     *
     * @return Integer the CAISI demographic identifier for the patient associated with this measurement
     */
    public Integer getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }

    /**
     * Sets the CAISI demographic identifier for the patient.
     *
     * @param caisiDemographicId Integer the CAISI demographic identifier to associate with this measurement
     */
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }

    /**
     * Retrieves the CAISI provider identifier for the healthcare provider who recorded the measurement.
     *
     * This identifier references the healthcare provider in the CAISI Integrator system, enabling tracking
     * of which provider recorded each measurement across the integrated healthcare network.
     *
     * @return String the CAISI provider identifier, maximum 16 characters
     */
    public String getCaisiProviderId() {
        return pcGetcaisiProviderId(this);
    }

    /**
     * Sets the CAISI provider identifier for the healthcare provider who recorded the measurement.
     *
     * The provider ID is automatically trimmed of leading and trailing whitespace. If null is provided,
     * it will be converted to an empty string.
     *
     * @param caisiProviderId String the CAISI provider identifier to set, maximum 16 characters (will be trimmed)
     */
    public void setCaisiProviderId(final String caisiProviderId) {
        pcSetcaisiProviderId(this, StringUtils.trimToEmpty(caisiProviderId));
    }

    /**
     * Retrieves the actual measurement data value.
     *
     * This field contains the quantitative or qualitative value of the clinical measurement.
     * The format and content depend on the measurement type (e.g., "120/80" for blood pressure,
     * "72.5" for weight in kilograms).
     *
     * @return String the measurement value
     */
    public String getDataField() {
        return pcGetdataField(this);
    }

    /**
     * Sets the actual measurement data value.
     *
     * The data value is automatically trimmed of leading and trailing whitespace. If null is provided,
     * it will be converted to an empty string.
     *
     * @param dataField String the measurement value to set (will be trimmed)
     */
    public void setDataField(final String dataField) {
        pcSetdataField(this, StringUtils.trimToEmpty(dataField));
    }

    /**
     * Retrieves the measuring instructions associated with this measurement.
     *
     * This field contains any special instructions or context about how the measurement was taken
     * (e.g., "sitting position", "after 5 minutes rest", "left arm").
     *
     * @return String the measuring instructions or context
     */
    public String getMeasuringInstruction() {
        return pcGetmeasuringInstruction(this);
    }

    /**
     * Sets the measuring instructions for this measurement.
     *
     * The instruction text is automatically trimmed of leading and trailing whitespace. If null is provided,
     * it will be converted to an empty string.
     *
     * @param measuringInstruction String the measuring instructions to set (will be trimmed)
     */
    public void setMeasuringInstruction(final String measuringInstruction) {
        pcSetmeasuringInstruction(this, StringUtils.trimToEmpty(measuringInstruction));
    }

    /**
     * Retrieves the comments associated with this measurement.
     *
     * This field contains any additional notes or observations related to the measurement that the
     * healthcare provider wishes to record for clinical context.
     *
     * @return String the measurement comments
     */
    public String getComments() {
        return pcGetcomments(this);
    }

    /**
     * Sets the comments for this measurement.
     *
     * @param comments String the measurement comments to set
     */
    public void setComments(final String comments) {
        pcSetcomments(this, comments);
    }

    /**
     * Retrieves the date and time when the measurement was observed or taken.
     *
     * This is the clinical observation timestamp indicating when the measurement was actually performed,
     * which may differ from when it was entered into the system.
     *
     * @return Date the observation timestamp for this measurement
     */
    public Date getDateObserved() {
        return pcGetdateObserved(this);
    }

    /**
     * Sets the date and time when the measurement was observed or taken.
     *
     * @param dateObserved Date the observation timestamp to set
     */
    public void setDateObserved(final Date dateObserved) {
        pcSetdateObserved(this, dateObserved);
    }

    /**
     * Retrieves the date and time when the measurement was entered into the system.
     *
     * This timestamp records when the measurement data was captured in the EMR system, which may be
     * later than the actual observation time if the measurement was recorded retrospectively.
     *
     * @return Date the data entry timestamp for this measurement
     */
    public Date getDateEntered() {
        return pcGetdateEntered(this);
    }

    /**
     * Sets the date and time when the measurement was entered into the system.
     *
     * @param dateEntered Date the data entry timestamp to set
     */
    public void setDateEntered(final Date dateEntered) {
        pcSetdateEntered(this, dateEntered);
    }

    /**
     * Compares this measurement to another measurement for ordering purposes.
     *
     * The comparison is based on the CAISI item identifier within the composite primary key,
     * enabling natural ordering of measurements by their unique item identifiers.
     *
     * @param o CachedMeasurement the other measurement to compare against
     * @return int a negative integer, zero, or a positive integer as this measurement's item ID
     *         is less than, equal to, or greater than the specified measurement's item ID
     */
    @Override
    public int compareTo(final CachedMeasurement o) {
        return pcGetfacilityMeasurementPk(this).getCaisiItemId() - pcGetfacilityMeasurementPk(o).getCaisiItemId();
    }

    /**
     * Retrieves the identifier for this entity.
     *
     * This method is part of the AbstractModel contract and returns the composite primary key
     * that uniquely identifies this measurement in the database.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key for this measurement
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityMeasurementPk(this);
    }

    /**
     * Returns the OpenJPA enhancement contract version.
     *
     * This method is part of the OpenJPA persistence capability enhancement mechanism and indicates
     * the version of the bytecode enhancement contract implemented by this class.
     *
     * @return int the enhancement contract version (currently 2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 8171701675811807108L;
        CachedMeasurement.pcFieldNames = new String[] { "caisiDemographicId", "caisiProviderId", "comments", "dataField", "dateEntered", "dateObserved", "facilityMeasurementPk", "measuringInstruction", "type" };
        CachedMeasurement.pcFieldTypes = new Class[] { (CachedMeasurement.class$Ljava$lang$Integer != null) ? CachedMeasurement.class$Ljava$lang$Integer : (CachedMeasurement.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedMeasurement.class$Ljava$lang$String != null) ? CachedMeasurement.class$Ljava$lang$String : (CachedMeasurement.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurement.class$Ljava$lang$String != null) ? CachedMeasurement.class$Ljava$lang$String : (CachedMeasurement.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurement.class$Ljava$lang$String != null) ? CachedMeasurement.class$Ljava$lang$String : (CachedMeasurement.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurement.class$Ljava$util$Date != null) ? CachedMeasurement.class$Ljava$util$Date : (CachedMeasurement.class$Ljava$util$Date = class$("java.util.Date")), (CachedMeasurement.class$Ljava$util$Date != null) ? CachedMeasurement.class$Ljava$util$Date : (CachedMeasurement.class$Ljava$util$Date = class$("java.util.Date")), (CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedMeasurement.class$Ljava$lang$String != null) ? CachedMeasurement.class$Ljava$lang$String : (CachedMeasurement.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurement.class$Ljava$lang$String != null) ? CachedMeasurement.class$Ljava$lang$String : (CachedMeasurement.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedMeasurement.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurement != null) ? CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurement : (CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurement = class$("ca.openosp.openo.caisi_integrator.dao.CachedMeasurement")), CachedMeasurement.pcFieldNames, CachedMeasurement.pcFieldTypes, CachedMeasurement.pcFieldFlags, CachedMeasurement.pcPCSuperclass, "CachedMeasurement", (PersistenceCapable)new CachedMeasurement());
    }

    /**
     * Loads a class by name using reflection.
     *
     * This synthetic method is generated by the compiler to support class literal access in
     * environments where direct class literals may not be available. It wraps ClassNotFoundException
     * as NoClassDefFoundError.
     *
     * @param className String the fully qualified name of the class to load
     * @return Class the loaded class object
     * @throws NoClassDefFoundError if the class cannot be found
     */
    static /* synthetic */ Class class$(final String className) {
        try {
            return Class.forName(className);
        }
        catch (final ClassNotFoundException ex) {
            throw new NoClassDefFoundError(ex.getMessage());
        }
    }

    /**
     * Clears all persistent fields to null.
     *
     * This method is part of the OpenJPA persistence capability enhancement and is used during
     * entity lifecycle management to reset the entity's state.
     */
    protected void pcClearFields() {
        this.caisiDemographicId = null;
        this.caisiProviderId = null;
        this.comments = null;
        this.dataField = null;
        this.dateEntered = null;
        this.dateObserved = null;
        this.facilityMeasurementPk = null;
        this.measuringInstruction = null;
        this.type = null;
    }

    /**
     * Creates a new instance with a specified state manager and object identifier.
     *
     * This factory method is part of the OpenJPA persistence capability interface and is used
     * to create new entity instances during persistence operations. If the clear flag is true,
     * all fields are initialized to null.
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object identifier to copy key fields from
     * @param b boolean flag indicating whether to clear all fields after instantiation
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedMeasurement cachedMeasurement = new CachedMeasurement();
        if (b) {
            cachedMeasurement.pcClearFields();
        }
        cachedMeasurement.pcStateManager = pcStateManager;
        cachedMeasurement.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedMeasurement;
    }

    /**
     * Creates a new instance with a specified state manager.
     *
     * This factory method is part of the OpenJPA persistence capability interface and is used
     * to create new entity instances during persistence operations. If the clear flag is true,
     * all fields are initialized to null.
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean flag indicating whether to clear all fields after instantiation
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedMeasurement cachedMeasurement = new CachedMeasurement();
        if (b) {
            cachedMeasurement.pcClearFields();
        }
        cachedMeasurement.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedMeasurement;
    }

    /**
     * Returns the number of managed persistent fields in this entity.
     *
     * This method is part of the OpenJPA persistence capability enhancement and indicates
     * how many fields are managed by the persistence framework.
     *
     * @return int the number of managed fields (9 for this entity)
     */
    protected static int pcGetManagedFieldCount() {
        return 9;
    }

    /**
     * Replaces a single managed field with a value from the state manager.
     *
     * This method is part of the OpenJPA persistence capability interface and is used during
     * state management operations to update field values from the persistence context.
     *
     * @param n int the absolute field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedMeasurement.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.caisiProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.comments = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.dataField = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.dateEntered = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.dateObserved = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.facilityMeasurementPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.measuringInstruction = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.type = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple managed fields with values from the state manager.
     *
     * This method is part of the OpenJPA persistence capability interface and replaces
     * each field specified in the array by delegating to pcReplaceField.
     *
     * @param array int[] array of absolute field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single managed field value to the state manager.
     *
     * This method is part of the OpenJPA persistence capability interface and is used during
     * state management operations to supply field values to the persistence context.
     *
     * @param n int the absolute field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedMeasurement.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiDemographicId);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.caisiProviderId);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.comments);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.dataField);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.dateEntered);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.dateObserved);
                return;
            }
            case 6: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityMeasurementPk);
                return;
            }
            case 7: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.measuringInstruction);
                return;
            }
            case 8: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.type);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Provides multiple managed field values to the state manager.
     *
     * This method is part of the OpenJPA persistence capability interface and provides
     * each field specified in the array by delegating to pcProvideField.
     *
     * @param array int[] array of absolute field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }

    /**
     * Copies a single field value from another CachedMeasurement instance.
     *
     * This method is part of the OpenJPA persistence capability enhancement and is used during
     * merge operations to copy field values between entity instances.
     *
     * @param cachedMeasurement CachedMeasurement the source instance to copy from
     * @param n int the absolute field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final CachedMeasurement cachedMeasurement, final int n) {
        final int n2 = n - CachedMeasurement.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = cachedMeasurement.caisiDemographicId;
                return;
            }
            case 1: {
                this.caisiProviderId = cachedMeasurement.caisiProviderId;
                return;
            }
            case 2: {
                this.comments = cachedMeasurement.comments;
                return;
            }
            case 3: {
                this.dataField = cachedMeasurement.dataField;
                return;
            }
            case 4: {
                this.dateEntered = cachedMeasurement.dateEntered;
                return;
            }
            case 5: {
                this.dateObserved = cachedMeasurement.dateObserved;
                return;
            }
            case 6: {
                this.facilityMeasurementPk = cachedMeasurement.facilityMeasurementPk;
                return;
            }
            case 7: {
                this.measuringInstruction = cachedMeasurement.measuringInstruction;
                return;
            }
            case 8: {
                this.type = cachedMeasurement.type;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies multiple field values from another entity instance.
     *
     * This method is part of the OpenJPA persistence capability interface and is used during
     * merge operations. It validates that both instances share the same state manager before
     * copying the specified fields.
     *
     * @param o Object the source entity to copy from (must be a CachedMeasurement)
     * @param array int[] array of absolute field indices to copy
     * @throws IllegalArgumentException if the source has a different state manager
     * @throws IllegalStateException if this instance has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedMeasurement cachedMeasurement = (CachedMeasurement)o;
        if (cachedMeasurement.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedMeasurement, array[i]);
        }
    }

    /**
     * Retrieves the generic context from the state manager.
     *
     * This method is part of the OpenJPA persistence capability interface and returns
     * implementation-specific context information from the associated state manager.
     *
     * @return Object the generic context, or null if no state manager is present
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the object identifier for this persistent instance.
     *
     * This method is part of the OpenJPA persistence capability interface and retrieves
     * the unique identifier assigned by the persistence framework.
     *
     * @return Object the object identifier, or null if no state manager is present
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks whether this entity has been marked for deletion.
     *
     * This method is part of the OpenJPA persistence capability interface and queries
     * the state manager to determine if the entity is in deleted state.
     *
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks whether this entity has been modified since it was last synchronized with the database.
     *
     * This method is part of the OpenJPA persistence capability interface and performs a dirty
     * check to determine if any fields have been changed.
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
     *
     * This method is part of the OpenJPA persistence capability interface and queries
     * the state manager to determine if the entity is in new state.
     *
     * @return boolean true if the entity is new and unpersisted, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks whether this entity is managed by the persistence framework.
     *
     * This method is part of the OpenJPA persistence capability interface and queries
     * the state manager to determine if the entity is in persistent state.
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks whether this entity is participating in a transaction.
     *
     * This method is part of the OpenJPA persistence capability interface and queries
     * the state manager to determine if the entity is in transactional state.
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks whether this entity is currently being serialized.
     *
     * This method is part of the OpenJPA persistence capability interface and queries
     * the state manager to determine if serialization is in progress.
     *
     * @return boolean true if the entity is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a field as dirty (modified) for change tracking.
     *
     * This method is part of the OpenJPA persistence capability interface and notifies
     * the state manager that the specified field has been changed.
     *
     * @param s String the name of the field that was modified
     */
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }

    /**
     * Retrieves the state manager associated with this persistent instance.
     *
     * This method is part of the OpenJPA persistence capability interface and returns
     * the state manager responsible for tracking the entity's lifecycle and state changes.
     *
     * @return StateManager the associated state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version identifier for optimistic locking.
     *
     * This method is part of the OpenJPA persistence capability interface and returns
     * the version value used for detecting concurrent modifications.
     *
     * @return Object the version identifier, or null if no state manager is present
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces the current state manager with a new one.
     *
     * This method is part of the OpenJPA persistence capability interface and is used
     * during entity lifecycle transitions to change the managing state manager. If a state
     * manager is already present, it is consulted for the replacement.
     *
     * @param pcStateManager StateManager the new state manager to set
     * @throws SecurityException if the replacement is not permitted by the current state manager
     */
    public void pcReplaceStateManager(final StateManager pcStateManager) throws SecurityException {
        if (this.pcStateManager != null) {
            this.pcStateManager = this.pcStateManager.replaceStateManager(pcStateManager);
            return;
        }
        this.pcStateManager = pcStateManager;
    }

    /**
     * Copies key fields to an object identifier using a field supplier.
     *
     * This method is part of the OpenJPA persistence capability interface. This particular
     * implementation throws InternalException as it is not supported for this entity type.
     *
     * @param fieldSupplier FieldSupplier the field supplier to use
     * @param o Object the target object identifier
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields to an object identifier.
     *
     * This method is part of the OpenJPA persistence capability interface. This particular
     * implementation throws InternalException as it is not supported for this entity type.
     *
     * @param o Object the target object identifier
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields from an object identifier using a field consumer.
     *
     * This method is part of the OpenJPA persistence capability interface and extracts
     * the primary key from the object identifier, storing it in the specified field consumer.
     *
     * @param fieldConsumer FieldConsumer the field consumer to receive the key field
     * @param o Object the source object identifier (must be an ObjectId)
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(6 + CachedMeasurement.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key fields from an object identifier directly to this entity.
     *
     * This method is part of the OpenJPA persistence capability interface and extracts
     * the primary key from the object identifier, storing it in this entity's facilityMeasurementPk field.
     *
     * @param o Object the source object identifier (must be an ObjectId)
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityMeasurementPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object identifier instance from a string representation.
     *
     * This method is part of the OpenJPA persistence capability interface. This particular
     * implementation always throws IllegalArgumentException as ObjectId does not support
     * string-based construction for this entity type.
     *
     * @param o Object the string representation of the object identifier
     * @return Object the new object identifier (never returns, always throws)
     * @throws IllegalArgumentException always thrown as string-based construction is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedMeasurement\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object identifier instance from this entity's current primary key.
     *
     * This method is part of the OpenJPA persistence capability interface and constructs
     * an ObjectId containing this entity's class and current facilityMeasurementPk value.
     *
     * @return Object the newly created object identifier
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurement != null) ? CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurement : (CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurement = class$("ca.openosp.openo.caisi_integrator.dao.CachedMeasurement")), (Object)this.facilityMeasurementPk);
    }

    /**
     * Internal accessor for the caisiDemographicId field with state manager integration.
     *
     * This private method is part of the OpenJPA persistence capability enhancement and provides
     * field access tracking through the state manager when present.
     *
     * @param cachedMeasurement CachedMeasurement the instance to access
     * @return Integer the CAISI demographic identifier
     */
    private static final Integer pcGetcaisiDemographicId(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.caisiDemographicId;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 0);
        return cachedMeasurement.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedMeasurement cachedMeasurement, final Integer caisiDemographicId) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedMeasurement.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 0, (Object)cachedMeasurement.caisiDemographicId, (Object)caisiDemographicId, 0);
    }
    
    private static final String pcGetcaisiProviderId(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.caisiProviderId;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 1);
        return cachedMeasurement.caisiProviderId;
    }
    
    private static final void pcSetcaisiProviderId(final CachedMeasurement cachedMeasurement, final String caisiProviderId) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.caisiProviderId = caisiProviderId;
            return;
        }
        cachedMeasurement.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 1, cachedMeasurement.caisiProviderId, caisiProviderId, 0);
    }
    
    private static final String pcGetcomments(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.comments;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 2);
        return cachedMeasurement.comments;
    }
    
    private static final void pcSetcomments(final CachedMeasurement cachedMeasurement, final String comments) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.comments = comments;
            return;
        }
        cachedMeasurement.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 2, cachedMeasurement.comments, comments, 0);
    }
    
    private static final String pcGetdataField(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.dataField;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 3);
        return cachedMeasurement.dataField;
    }
    
    private static final void pcSetdataField(final CachedMeasurement cachedMeasurement, final String dataField) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.dataField = dataField;
            return;
        }
        cachedMeasurement.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 3, cachedMeasurement.dataField, dataField, 0);
    }
    
    private static final Date pcGetdateEntered(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.dateEntered;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 4);
        return cachedMeasurement.dateEntered;
    }
    
    private static final void pcSetdateEntered(final CachedMeasurement cachedMeasurement, final Date dateEntered) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.dateEntered = dateEntered;
            return;
        }
        cachedMeasurement.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 4, (Object)cachedMeasurement.dateEntered, (Object)dateEntered, 0);
    }
    
    private static final Date pcGetdateObserved(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.dateObserved;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 5);
        return cachedMeasurement.dateObserved;
    }
    
    private static final void pcSetdateObserved(final CachedMeasurement cachedMeasurement, final Date dateObserved) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.dateObserved = dateObserved;
            return;
        }
        cachedMeasurement.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 5, (Object)cachedMeasurement.dateObserved, (Object)dateObserved, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityMeasurementPk(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.facilityMeasurementPk;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 6);
        return cachedMeasurement.facilityMeasurementPk;
    }
    
    private static final void pcSetfacilityMeasurementPk(final CachedMeasurement cachedMeasurement, final FacilityIdIntegerCompositePk facilityMeasurementPk) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.facilityMeasurementPk = facilityMeasurementPk;
            return;
        }
        cachedMeasurement.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 6, (Object)cachedMeasurement.facilityMeasurementPk, (Object)facilityMeasurementPk, 0);
    }
    
    private static final String pcGetmeasuringInstruction(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.measuringInstruction;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 7);
        return cachedMeasurement.measuringInstruction;
    }
    
    private static final void pcSetmeasuringInstruction(final CachedMeasurement cachedMeasurement, final String measuringInstruction) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.measuringInstruction = measuringInstruction;
            return;
        }
        cachedMeasurement.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 7, cachedMeasurement.measuringInstruction, measuringInstruction, 0);
    }
    
    private static final String pcGettype(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.type;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 8);
        return cachedMeasurement.type;
    }
    
    private static final void pcSettype(final CachedMeasurement cachedMeasurement, final String type) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.type = type;
            return;
        }
        cachedMeasurement.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 8, cachedMeasurement.type, type, 0);
    }

    /**
     * Checks whether this entity is in detached state.
     *
     * This method is part of the OpenJPA persistence capability interface and determines
     * if the entity has been detached from the persistence context. Returns null if the
     * detached state cannot be definitively determined.
     *
     * @return Boolean true if detached, false if not detached, null if state is indeterminate
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
     * Checks whether the detached state can be definitively determined.
     *
     * This private method is part of the OpenJPA persistence capability enhancement and
     * indicates whether the detached state information is reliable. Currently always returns false.
     *
     * @return boolean false, indicating detached state may be indeterminate
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Retrieves the detached state object.
     *
     * This method is part of the OpenJPA persistence capability interface and returns
     * the state information stored when the entity was detached from the persistence context.
     *
     * @return Object the detached state, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object.
     *
     * This method is part of the OpenJPA persistence capability interface and stores
     * state information when the entity is detached from or reattached to the persistence context.
     *
     * @param pcDetachedState Object the detached state to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization handler for writing this entity to an object stream.
     *
     * This private method is part of Java's serialization mechanism and ensures proper
     * handling of the detached state during serialization. If the entity is being serialized
     * by the persistence framework, the detached state is cleared after writing.
     *
     * @param objectOutputStream ObjectOutputStream the stream to write to
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
     * Custom deserialization handler for reading this entity from an object stream.
     *
     * This private method is part of Java's serialization mechanism and ensures proper
     * initialization of the detached state when the entity is deserialized. The detached
     * state is set to DESERIALIZED to indicate the entity was reconstituted from a stream.
     *
     * @param objectInputStream ObjectInputStream the stream to read from
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if a required class cannot be found during deserialization
     */
    private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        this.pcSetDetachedState(PersistenceCapable.DESERIALIZED);
        objectInputStream.defaultReadObject();
    }
}
