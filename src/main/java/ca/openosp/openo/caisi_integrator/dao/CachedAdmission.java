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
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * Entity representing a cached admission record for the CAISI (Client Access to Integrated Services and Information) integrator system.
 *
 * <p>This class stores admission and discharge information for patients enrolled in CAISI programs. It maintains
 * a cache of admission data across multiple healthcare facilities participating in the integrator network.
 * The entity tracks demographic associations, program enrollments, admission/discharge dates, and associated
 * clinical notes.</p>
 *
 * <p>This is an OpenJPA enhanced entity that implements PersistenceCapable for advanced JPA features including
 * detached state management, field-level change tracking, and optimized persistence operations. The enhancement
 * is performed at build time by the OpenJPA enhancer.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Composite primary key combining facility ID and admission ID for cross-facility uniqueness</li>
 *   <li>Indexed fields for efficient querying by demographic, program, and admission date</li>
 *   <li>Support for medium text notes (up to 16MB) for admission and discharge documentation</li>
 *   <li>Temporal tracking with timestamp precision for admission and discharge events</li>
 *   <li>OpenJPA persistence capabilities for advanced state management and detachment</li>
 * </ul>
 *
 * <p><strong>CAISI Integration Context:</strong> CAISI is a community health information system that enables
 * coordinated care across multiple healthcare providers and facilities. This cached admission entity supports
 * the integrator's ability to aggregate and synchronize patient program enrollment data across the CAISI network.</p>
 *
 * @see AbstractModel
 * @see FacilityIdIntegerCompositePk
 * @see PersistenceCapable
 *
 * @since 2026-01-24
 */
@Entity
public class CachedAdmission extends AbstractModel<FacilityIdIntegerCompositePk> implements PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    @Column(nullable = false)
    @Index
    private int caisiDemographicId;
    @Column(nullable = false)
    @Index
    private int caisiProgramId;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    @Index
    private Date admissionDate;
    @Temporal(TemporalType.TIMESTAMP)
    private Date dischargeDate;
    @Column(columnDefinition = "mediumtext")
    private String admissionNotes;
    @Column(columnDefinition = "mediumtext")
    private String dischargeNotes;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedAdmission;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Constructs a new CachedAdmission instance with default values.
     *
     * <p>Initializes all fields to their default states: object references to null and
     * integer IDs to 0. This constructor is primarily used by JPA/OpenJPA for entity
     * instantiation during database operations.</p>
     */
    public CachedAdmission() {
        this.facilityIdIntegerCompositePk = null;
        this.caisiDemographicId = 0;
        this.caisiProgramId = 0;
        this.admissionDate = null;
        this.dischargeDate = null;
        this.admissionNotes = null;
        this.dischargeNotes = null;
    }

    /**
     * Retrieves the composite primary key for this admission record.
     *
     * <p>The composite key combines the facility ID and admission ID to ensure uniqueness
     * across multiple facilities in the CAISI integrator network.</p>
     *
     * @return FacilityIdIntegerCompositePk the composite primary key containing facility and admission identifiers
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityIdIntegerCompositePk(this);
    }

    /**
     * Sets the composite primary key for this admission record.
     *
     * @param facilityIdIntegerCompositePk FacilityIdIntegerCompositePk the composite primary key to set
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        pcSetfacilityIdIntegerCompositePk(this, facilityIdIntegerCompositePk);
    }

    /**
     * Retrieves the entity's primary key identifier.
     *
     * <p>This method overrides the AbstractModel getId() method to return the composite primary key.</p>
     *
     * @return FacilityIdIntegerCompositePk the composite primary key
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityIdIntegerCompositePk(this);
    }

    /**
     * Retrieves the CAISI demographic identifier for the patient.
     *
     * <p>This ID references the patient's demographic record in the CAISI system and is indexed
     * for efficient query performance.</p>
     *
     * @return int the CAISI demographic identifier
     */
    public int getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }

    /**
     * Sets the CAISI demographic identifier for the patient.
     *
     * @param caisiDemographicId int the CAISI demographic identifier to set
     */
    public void setCaisiDemographicId(final int caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }

    /**
     * Retrieves the CAISI program identifier.
     *
     * <p>This ID identifies the specific CAISI program the patient is admitted to and is indexed
     * for efficient querying of program enrollments.</p>
     *
     * @return int the CAISI program identifier
     */
    public int getCaisiProgramId() {
        return pcGetcaisiProgramId(this);
    }

    /**
     * Sets the CAISI program identifier.
     *
     * @param caisiProgramId int the CAISI program identifier to set
     */
    public void setCaisiProgramId(final int caisiProgramId) {
        pcSetcaisiProgramId(this, caisiProgramId);
    }

    /**
     * Retrieves the date and time when the patient was admitted to the program.
     *
     * <p>This field is indexed and stored with timestamp precision to support temporal queries
     * and admission timeline tracking.</p>
     *
     * @return Date the admission date and time, or null if not set
     */
    public Date getAdmissionDate() {
        return pcGetadmissionDate(this);
    }

    /**
     * Sets the date and time when the patient was admitted to the program.
     *
     * @param admissionDate Date the admission date and time to set
     */
    public void setAdmissionDate(final Date admissionDate) {
        pcSetadmissionDate(this, admissionDate);
    }

    /**
     * Retrieves the date and time when the patient was discharged from the program.
     *
     * <p>This field is nullable to support ongoing admissions (not yet discharged) and is stored
     * with timestamp precision.</p>
     *
     * @return Date the discharge date and time, or null if patient is still admitted
     */
    public Date getDischargeDate() {
        return pcGetdischargeDate(this);
    }

    /**
     * Sets the date and time when the patient was discharged from the program.
     *
     * @param dischargeDate Date the discharge date and time to set, or null for ongoing admissions
     */
    public void setDischargeDate(final Date dischargeDate) {
        pcSetdischargeDate(this, dischargeDate);
    }

    /**
     * Retrieves the clinical notes recorded at admission.
     *
     * <p>This field supports medium text storage (up to 16MB) to accommodate comprehensive
     * admission documentation including clinical assessments, treatment plans, and initial observations.</p>
     *
     * @return String the admission notes, or null if not recorded
     */
    public String getAdmissionNotes() {
        return pcGetadmissionNotes(this);
    }

    /**
     * Sets the clinical notes recorded at admission.
     *
     * @param admissionNotes String the admission notes to set
     */
    public void setAdmissionNotes(final String admissionNotes) {
        pcSetadmissionNotes(this, admissionNotes);
    }

    /**
     * Retrieves the clinical notes recorded at discharge.
     *
     * <p>This field supports medium text storage (up to 16MB) for comprehensive discharge documentation
     * including discharge summaries, follow-up instructions, and outcome assessments.</p>
     *
     * @return String the discharge notes, or null if not recorded
     */
    public String getDischargeNotes() {
        return pcGetdischargeNotes(this);
    }

    /**
     * Sets the clinical notes recorded at discharge.
     *
     * @param dischargeNotes String the discharge notes to set
     */
    public void setDischargeNotes(final String dischargeNotes) {
        pcSetdischargeNotes(this, dischargeNotes);
    }

    /**
     * Returns the OpenJPA enhancement contract version.
     *
     * <p>This method is part of the OpenJPA enhancement infrastructure and indicates the version
     * of the persistence capability contract implemented by this enhanced entity.</p>
     *
     * @return int the enhancement contract version (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 475490153351795557L;
        CachedAdmission.pcFieldNames = new String[] { "admissionDate", "admissionNotes", "caisiDemographicId", "caisiProgramId", "dischargeDate", "dischargeNotes", "facilityIdIntegerCompositePk" };
        CachedAdmission.pcFieldTypes = new Class[] { (CachedAdmission.class$Ljava$util$Date != null) ? CachedAdmission.class$Ljava$util$Date : (CachedAdmission.class$Ljava$util$Date = class$("java.util.Date")), (CachedAdmission.class$Ljava$lang$String != null) ? CachedAdmission.class$Ljava$lang$String : (CachedAdmission.class$Ljava$lang$String = class$("java.lang.String")), Integer.TYPE, Integer.TYPE, (CachedAdmission.class$Ljava$util$Date != null) ? CachedAdmission.class$Ljava$util$Date : (CachedAdmission.class$Ljava$util$Date = class$("java.util.Date")), (CachedAdmission.class$Ljava$lang$String != null) ? CachedAdmission.class$Ljava$lang$String : (CachedAdmission.class$Ljava$lang$String = class$("java.lang.String")), (CachedAdmission.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedAdmission.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedAdmission.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")) };
        CachedAdmission.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedAdmission.class$Lca$openosp$openo$caisi_integrator$dao$CachedAdmission != null) ? CachedAdmission.class$Lca$openosp$openo$caisi_integrator$dao$CachedAdmission : (CachedAdmission.class$Lca$openosp$openo$caisi_integrator$dao$CachedAdmission = class$("ca.openosp.openo.caisi_integrator.dao.CachedAdmission")), CachedAdmission.pcFieldNames, CachedAdmission.pcFieldTypes, CachedAdmission.pcFieldFlags, CachedAdmission.pcPCSuperclass, "CachedAdmission", (PersistenceCapable)new CachedAdmission());
    }
    
    static /* synthetic */ Class class$(final String className) {
        try {
            return Class.forName(className);
        }
        catch (final ClassNotFoundException ex) {
            throw new NoClassDefFoundError(ex.getMessage());
        }
    }
    
    protected void pcClearFields() {
        this.admissionDate = null;
        this.admissionNotes = null;
        this.caisiDemographicId = 0;
        this.caisiProgramId = 0;
        this.dischargeDate = null;
        this.dischargeNotes = null;
        this.facilityIdIntegerCompositePk = null;
    }

    /**
     * Creates a new instance of this entity with the specified state manager and object ID.
     *
     * <p>This OpenJPA lifecycle method is used to create new entity instances with a specific
     * state manager and key values copied from the provided object ID.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object ID containing key field values to copy
     * @param b boolean if true, clears all fields to default values before copying key fields
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedAdmission cachedAdmission = new CachedAdmission();
        if (b) {
            cachedAdmission.pcClearFields();
        }
        cachedAdmission.pcStateManager = pcStateManager;
        cachedAdmission.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedAdmission;
    }

    /**
     * Creates a new instance of this entity with the specified state manager.
     *
     * <p>This OpenJPA lifecycle method is used to create new entity instances with a specific
     * state manager but without copying key fields from an object ID.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean if true, clears all fields to default values
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedAdmission cachedAdmission = new CachedAdmission();
        if (b) {
            cachedAdmission.pcClearFields();
        }
        cachedAdmission.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedAdmission;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 7;
    }

    /**
     * Replaces a single field value from the state manager.
     *
     * <p>This OpenJPA field management method retrieves the current value for the specified field
     * from the state manager and updates the entity's field accordingly. Used during entity refresh
     * and state synchronization operations.</p>
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedAdmission.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.admissionDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.admissionNotes = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.caisiDemographicId = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.caisiProgramId = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.dischargeDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.dischargeNotes = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple field values from the state manager.
     *
     * <p>This OpenJPA field management method replaces multiple fields in a single call by
     * delegating to pcReplaceField for each specified field index.</p>
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single field value to the state manager.
     *
     * <p>This OpenJPA field management method supplies the current value of the specified field
     * to the state manager. Used during persistence operations such as flush and commit.</p>
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedAdmission.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.admissionDate);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.admissionNotes);
                return;
            }
            case 2: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.caisiDemographicId);
                return;
            }
            case 3: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.caisiProgramId);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.dischargeDate);
                return;
            }
            case 5: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.dischargeNotes);
                return;
            }
            case 6: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityIdIntegerCompositePk);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Provides multiple field values to the state manager.
     *
     * <p>This OpenJPA field management method supplies multiple field values in a single call
     * by delegating to pcProvideField for each specified field index.</p>
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    protected void pcCopyField(final CachedAdmission cachedAdmission, final int n) {
        final int n2 = n - CachedAdmission.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.admissionDate = cachedAdmission.admissionDate;
                return;
            }
            case 1: {
                this.admissionNotes = cachedAdmission.admissionNotes;
                return;
            }
            case 2: {
                this.caisiDemographicId = cachedAdmission.caisiDemographicId;
                return;
            }
            case 3: {
                this.caisiProgramId = cachedAdmission.caisiProgramId;
                return;
            }
            case 4: {
                this.dischargeDate = cachedAdmission.dischargeDate;
                return;
            }
            case 5: {
                this.dischargeNotes = cachedAdmission.dischargeNotes;
                return;
            }
            case 6: {
                this.facilityIdIntegerCompositePk = cachedAdmission.facilityIdIntegerCompositePk;
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
     * <p>This OpenJPA field management method copies field values from the source entity to this
     * entity for the specified field indices. Both entities must share the same state manager.</p>
     *
     * @param o Object the source entity to copy from (must be a CachedAdmission instance)
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source entity has a different state manager
     * @throws IllegalStateException if this entity has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedAdmission cachedAdmission = (CachedAdmission)o;
        if (cachedAdmission.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedAdmission, array[i]);
        }
    }

    /**
     * Retrieves the generic context from the state manager.
     *
     * <p>The generic context provides access to persistence context information managed by OpenJPA.</p>
     *
     * @return Object the generic context, or null if no state manager is associated
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the object ID for this entity from the state manager.
     *
     * @return Object the object ID, or null if no state manager is associated
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks if this entity is marked as deleted.
     *
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has been modified.
     *
     * <p>Performs a dirty check to determine if any field values have changed since the entity
     * was loaded or last synchronized with the database.</p>
     *
     * @return boolean true if the entity has been modified, false otherwise
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
     * Checks if this entity is newly created (not yet persisted).
     *
     * @return boolean true if the entity is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is persistent (managed by a persistence context).
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity is transactional (participating in a transaction).
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity is currently being serialized.
     *
     * @return boolean true if the entity is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks the specified field as dirty.
     *
     * <p>This method notifies the state manager that the named field has been modified and should
     * be included in the next flush/commit operation.</p>
     *
     * @param s String the name of the field to mark as dirty
     */
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }

    /**
     * Retrieves the state manager associated with this entity.
     *
     * @return StateManager the state manager, or null if not associated
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version information for this entity.
     *
     * <p>The version is used for optimistic locking to detect concurrent modifications.</p>
     *
     * @return Object the version information, or null if no state manager is associated
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
     * <p>If a state manager is already associated, it delegates to the existing manager's
     * replaceStateManager method. Otherwise, directly assigns the new manager.</p>
     *
     * @param pcStateManager StateManager the new state manager to associate
     * @throws SecurityException if the state manager replacement is not permitted
     */
    public void pcReplaceStateManager(final StateManager pcStateManager) throws SecurityException {
        if (this.pcStateManager != null) {
            this.pcStateManager = this.pcStateManager.replaceStateManager(pcStateManager);
            return;
        }
        this.pcStateManager = pcStateManager;
    }

    /**
     * Copies key field values to an object ID using a field supplier.
     *
     * <p>This operation is not supported for this entity type.</p>
     *
     * @param fieldSupplier FieldSupplier the field supplier to use
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key field values to an object ID.
     *
     * <p>This operation is not supported for this entity type.</p>
     *
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key field values from an object ID using a field consumer.
     *
     * <p>Extracts the composite primary key from the object ID and stores it via the field consumer.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer to receive the key field value
     * @param o Object the source object ID containing the key value
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(6 + CachedAdmission.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key field values from an object ID directly to this entity.
     *
     * <p>Extracts the composite primary key from the object ID and assigns it to this entity's
     * facilityIdIntegerCompositePk field.</p>
     *
     * @param o Object the source object ID containing the key value
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * <p>This operation is not supported for this entity type as the ObjectId class does not
     * provide the required constructors.</p>
     *
     * @param o Object the string representation of the object ID
     * @return Object never returns; always throws exception
     * @throws IllegalArgumentException always thrown as this operation is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedAdmission\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance using this entity's current key field values.
     *
     * @return Object a new ObjectId instance containing this entity's composite primary key
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedAdmission.class$Lca$openosp$openo$caisi_integrator$dao$CachedAdmission != null) ? CachedAdmission.class$Lca$openosp$openo$caisi_integrator$dao$CachedAdmission : (CachedAdmission.class$Lca$openosp$openo$caisi_integrator$dao$CachedAdmission = class$("ca.openosp.openo.caisi_integrator.dao.CachedAdmission")), (Object)this.facilityIdIntegerCompositePk);
    }
    
    private static final Date pcGetadmissionDate(final CachedAdmission cachedAdmission) {
        if (cachedAdmission.pcStateManager == null) {
            return cachedAdmission.admissionDate;
        }
        cachedAdmission.pcStateManager.accessingField(CachedAdmission.pcInheritedFieldCount + 0);
        return cachedAdmission.admissionDate;
    }
    
    private static final void pcSetadmissionDate(final CachedAdmission cachedAdmission, final Date admissionDate) {
        if (cachedAdmission.pcStateManager == null) {
            cachedAdmission.admissionDate = admissionDate;
            return;
        }
        cachedAdmission.pcStateManager.settingObjectField((PersistenceCapable)cachedAdmission, CachedAdmission.pcInheritedFieldCount + 0, (Object)cachedAdmission.admissionDate, (Object)admissionDate, 0);
    }
    
    private static final String pcGetadmissionNotes(final CachedAdmission cachedAdmission) {
        if (cachedAdmission.pcStateManager == null) {
            return cachedAdmission.admissionNotes;
        }
        cachedAdmission.pcStateManager.accessingField(CachedAdmission.pcInheritedFieldCount + 1);
        return cachedAdmission.admissionNotes;
    }
    
    private static final void pcSetadmissionNotes(final CachedAdmission cachedAdmission, final String admissionNotes) {
        if (cachedAdmission.pcStateManager == null) {
            cachedAdmission.admissionNotes = admissionNotes;
            return;
        }
        cachedAdmission.pcStateManager.settingStringField((PersistenceCapable)cachedAdmission, CachedAdmission.pcInheritedFieldCount + 1, cachedAdmission.admissionNotes, admissionNotes, 0);
    }
    
    private static final int pcGetcaisiDemographicId(final CachedAdmission cachedAdmission) {
        if (cachedAdmission.pcStateManager == null) {
            return cachedAdmission.caisiDemographicId;
        }
        cachedAdmission.pcStateManager.accessingField(CachedAdmission.pcInheritedFieldCount + 2);
        return cachedAdmission.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedAdmission cachedAdmission, final int caisiDemographicId) {
        if (cachedAdmission.pcStateManager == null) {
            cachedAdmission.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedAdmission.pcStateManager.settingIntField((PersistenceCapable)cachedAdmission, CachedAdmission.pcInheritedFieldCount + 2, cachedAdmission.caisiDemographicId, caisiDemographicId, 0);
    }
    
    private static final int pcGetcaisiProgramId(final CachedAdmission cachedAdmission) {
        if (cachedAdmission.pcStateManager == null) {
            return cachedAdmission.caisiProgramId;
        }
        cachedAdmission.pcStateManager.accessingField(CachedAdmission.pcInheritedFieldCount + 3);
        return cachedAdmission.caisiProgramId;
    }
    
    private static final void pcSetcaisiProgramId(final CachedAdmission cachedAdmission, final int caisiProgramId) {
        if (cachedAdmission.pcStateManager == null) {
            cachedAdmission.caisiProgramId = caisiProgramId;
            return;
        }
        cachedAdmission.pcStateManager.settingIntField((PersistenceCapable)cachedAdmission, CachedAdmission.pcInheritedFieldCount + 3, cachedAdmission.caisiProgramId, caisiProgramId, 0);
    }
    
    private static final Date pcGetdischargeDate(final CachedAdmission cachedAdmission) {
        if (cachedAdmission.pcStateManager == null) {
            return cachedAdmission.dischargeDate;
        }
        cachedAdmission.pcStateManager.accessingField(CachedAdmission.pcInheritedFieldCount + 4);
        return cachedAdmission.dischargeDate;
    }
    
    private static final void pcSetdischargeDate(final CachedAdmission cachedAdmission, final Date dischargeDate) {
        if (cachedAdmission.pcStateManager == null) {
            cachedAdmission.dischargeDate = dischargeDate;
            return;
        }
        cachedAdmission.pcStateManager.settingObjectField((PersistenceCapable)cachedAdmission, CachedAdmission.pcInheritedFieldCount + 4, (Object)cachedAdmission.dischargeDate, (Object)dischargeDate, 0);
    }
    
    private static final String pcGetdischargeNotes(final CachedAdmission cachedAdmission) {
        if (cachedAdmission.pcStateManager == null) {
            return cachedAdmission.dischargeNotes;
        }
        cachedAdmission.pcStateManager.accessingField(CachedAdmission.pcInheritedFieldCount + 5);
        return cachedAdmission.dischargeNotes;
    }
    
    private static final void pcSetdischargeNotes(final CachedAdmission cachedAdmission, final String dischargeNotes) {
        if (cachedAdmission.pcStateManager == null) {
            cachedAdmission.dischargeNotes = dischargeNotes;
            return;
        }
        cachedAdmission.pcStateManager.settingStringField((PersistenceCapable)cachedAdmission, CachedAdmission.pcInheritedFieldCount + 5, cachedAdmission.dischargeNotes, dischargeNotes, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityIdIntegerCompositePk(final CachedAdmission cachedAdmission) {
        if (cachedAdmission.pcStateManager == null) {
            return cachedAdmission.facilityIdIntegerCompositePk;
        }
        cachedAdmission.pcStateManager.accessingField(CachedAdmission.pcInheritedFieldCount + 6);
        return cachedAdmission.facilityIdIntegerCompositePk;
    }
    
    private static final void pcSetfacilityIdIntegerCompositePk(final CachedAdmission cachedAdmission, final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        if (cachedAdmission.pcStateManager == null) {
            cachedAdmission.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
            return;
        }
        cachedAdmission.pcStateManager.settingObjectField((PersistenceCapable)cachedAdmission, CachedAdmission.pcInheritedFieldCount + 6, (Object)cachedAdmission.facilityIdIntegerCompositePk, (Object)facilityIdIntegerCompositePk, 0);
    }

    /**
     * Checks if this entity is in a detached state.
     *
     * <p>A detached entity is one that was previously managed by a persistence context but is no
     * longer associated with an active context. This method provides a three-valued logic:</p>
     * <ul>
     *   <li>Boolean.TRUE - definitively detached</li>
     *   <li>Boolean.FALSE - definitively not detached</li>
     *   <li>null - detachment state cannot be determined</li>
     * </ul>
     *
     * @return Boolean the detachment state, or null if indeterminate
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
    
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Retrieves the detached state information for this entity.
     *
     * <p>The detached state contains metadata used by OpenJPA to manage detached entities,
     * including version information for optimistic locking and loaded field tracking.</p>
     *
     * @return Object the detached state object, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state information for this entity.
     *
     * <p>This method is used internally by OpenJPA during detachment and attachment operations
     * to maintain the entity's state tracking information.</p>
     *
     * @param pcDetachedState Object the detached state to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }
    
    private void writeObject(final ObjectOutputStream objectOutputStream) throws IOException {
        final boolean pcSerializing = this.pcSerializing();
        objectOutputStream.defaultWriteObject();
        if (pcSerializing) {
            this.pcSetDetachedState(null);
        }
    }
    
    private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        this.pcSetDetachedState(PersistenceCapable.DESERIALIZED);
        objectInputStream.defaultReadObject();
    }
}
