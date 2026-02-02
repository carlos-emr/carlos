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
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * Cached entity for HL7-formatted laboratory results associated with patient demographics in the integrator system.
 *
 * <p>This JPA entity represents laboratory results received in HL7 format (Health Level 7 messaging standard)
 * that have been cached from remote healthcare facilities through the OpenO integrator. The entity stores
 * HL7 lab result data along with the associated patient demographic identifier and facility information,
 * enabling efficient retrieval and synchronization of laboratory data across integrated healthcare systems.</p>
 *
 * <p>The entity uses a composite primary key consisting of the integrator facility ID and lab result ID,
 * allowing unique identification of lab results across multiple integrated healthcare facilities. The HL7
 * data is stored as a MEDIUMBLOB in the database to accommodate the potentially large size of HL7 messages
 * containing detailed laboratory results, observations, and associated metadata.</p>
 *
 * <p><strong>Healthcare Context:</strong> HL7 v2.x messages are the industry standard for transmitting
 * clinical laboratory results between systems. These messages typically contain patient demographics (PID segment),
 * order information (OBR segment), and individual result observations (OBX segments) with values, units,
 * reference ranges, and result flags. This cached entity enables the integrator to maintain a local copy
 * of remote lab results for improved performance and offline access.</p>
 *
 * <p><strong>OpenJPA Enhancement:</strong> This class is enhanced at runtime by Apache OpenJPA for
 * persistence capabilities, implementing transparent field access tracking and state management. The
 * enhancement process adds persistence-aware field accessors and state management methods automatically.</p>
 *
 * @see FacilityIdLabResultCompositePk
 * @see AbstractModel
 * @see CachedDemographicLabResult
 * @since 2026-01-24
 */
@Entity
public class CachedDemographicHL7LabResult extends AbstractModel<FacilityIdLabResultCompositePk> implements PersistenceCapable
{
    @EmbeddedId
    private FacilityIdLabResultCompositePk facilityIdLabResultCompositePk;
    @Column(nullable = false)
    @Index
    private int caisiDemographicId;
    @Column(length = 64)
    private String type;
    @Column(columnDefinition = "mediumblob")
    private String data;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicHL7LabResult;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Constructs a new CachedDemographicHL7LabResult with default values.
     *
     * <p>Initializes the CAISI demographic ID to 0. This no-argument constructor is required
     * by JPA for entity instantiation during database retrieval operations and by OpenJPA
     * for persistence capability enhancement.</p>
     */
    public CachedDemographicHL7LabResult() {
        this.caisiDemographicId = 0;
    }

    /**
     * Retrieves the composite primary key identifier for this cached HL7 lab result.
     *
     * <p>Returns the embedded composite key that uniquely identifies this lab result across
     * the integrator system, combining the facility ID and lab result ID.</p>
     *
     * @return FacilityIdLabResultCompositePk the composite primary key containing facility and lab result identifiers
     */
    @Override
    public FacilityIdLabResultCompositePk getId() {
        return pcGetfacilityIdLabResultCompositePk(this);
    }

    /**
     * Retrieves the composite primary key for this cached HL7 lab result.
     *
     * <p>Returns the embedded ID containing both the integrator facility identifier and the
     * lab result identifier, which together uniquely identify this lab result in the cache.</p>
     *
     * @return FacilityIdLabResultCompositePk the composite key containing facility and lab result IDs
     */
    public FacilityIdLabResultCompositePk getFacilityIdLabResultCompositePk() {
        return pcGetfacilityIdLabResultCompositePk(this);
    }

    /**
     * Sets the composite primary key for this cached HL7 lab result.
     *
     * <p>Assigns the embedded ID that uniquely identifies this lab result within the integrator
     * system. This should typically only be set during initial entity creation.</p>
     *
     * @param facilityIdLabResultCompositePk FacilityIdLabResultCompositePk the composite key to assign
     */
    public void setFacilityIdLabResultCompositePk(final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk) {
        pcSetfacilityIdLabResultCompositePk(this, facilityIdLabResultCompositePk);
    }

    /**
     * Retrieves the CAISI demographic identifier associated with this lab result.
     *
     * <p>Returns the local demographic ID that links this cached lab result to a patient
     * record in the CAISI system. This identifier enables correlation between remotely
     * obtained lab results and local patient demographics.</p>
     *
     * @return int the CAISI demographic identifier
     */
    public int getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }

    /**
     * Sets the CAISI demographic identifier for this lab result.
     *
     * <p>Assigns the local patient demographic ID that this lab result should be associated with,
     * establishing the link between the remotely cached HL7 lab data and the local patient record.</p>
     *
     * @param caisiDemographicId int the CAISI demographic identifier to associate with this lab result
     */
    public void setCaisiDemographicId(final int caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }

    /**
     * Retrieves the type classification of this HL7 lab result.
     *
     * <p>Returns a string value indicating the category or classification of this laboratory
     * result. The type field may be used to distinguish between different kinds of lab results,
     * such as chemistry panels, hematology, microbiology, or other laboratory disciplines.</p>
     *
     * @return String the type classification, maximum 64 characters, or null if not set
     */
    public String getType() {
        return pcGettype(this);
    }

    /**
     * Sets the type classification for this HL7 lab result.
     *
     * <p>Assigns a categorization or classification to this laboratory result. The type value
     * is limited to 64 characters by the database column definition.</p>
     *
     * @param type String the type classification to assign, maximum 64 characters
     */
    public void setType(final String type) {
        pcSettype(this, type);
    }

    /**
     * Retrieves the HL7-formatted laboratory result data.
     *
     * <p>Returns the complete HL7 message containing the laboratory result information. This
     * typically includes the HL7 message segments such as MSH (Message Header), PID (Patient
     * Identification), OBR (Observation Request), and OBX (Observation Result) segments along
     * with all associated laboratory values, units, reference ranges, and result flags.</p>
     *
     * <p>The data is stored as a MEDIUMBLOB in the database to accommodate potentially large
     * HL7 messages that may contain multiple test results, extensive notes, and embedded
     * documents or images.</p>
     *
     * @return String the HL7-formatted lab result message, or null if not set
     */
    public String getData() {
        return pcGetdata(this);
    }

    /**
     * Sets the HL7-formatted laboratory result data.
     *
     * <p>Stores the complete HL7 message containing the laboratory result. The message should
     * be a properly formatted HL7 v2.x message with appropriate segments for transmitting
     * clinical laboratory data. The data is stored as a MEDIUMBLOB, supporting messages up
     * to approximately 16MB in size.</p>
     *
     * @param data String the HL7-formatted lab result message to store
     */
    public void setData(final String data) {
        pcSetdata(this, data);
    }

    /**
     * Returns the OpenJPA enhancement contract version for this persistent class.
     *
     * <p>This method is part of the OpenJPA persistence capability infrastructure and indicates
     * the version of the enhancement contract that this class implements. Version 2 represents
     * the current OpenJPA enhancement specification.</p>
     *
     * @return int the enhancement contract version number (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 8411054820363937199L;
        CachedDemographicHL7LabResult.pcFieldNames = new String[] { "caisiDemographicId", "data", "facilityIdLabResultCompositePk", "type" };
        CachedDemographicHL7LabResult.pcFieldTypes = new Class[] { Integer.TYPE, (CachedDemographicHL7LabResult.class$Ljava$lang$String != null) ? CachedDemographicHL7LabResult.class$Ljava$lang$String : (CachedDemographicHL7LabResult.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicHL7LabResult.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk != null) ? CachedDemographicHL7LabResult.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk : (CachedDemographicHL7LabResult.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdLabResultCompositePk")), (CachedDemographicHL7LabResult.class$Ljava$lang$String != null) ? CachedDemographicHL7LabResult.class$Ljava$lang$String : (CachedDemographicHL7LabResult.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedDemographicHL7LabResult.pcFieldFlags = new byte[] { 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographicHL7LabResult.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicHL7LabResult != null) ? CachedDemographicHL7LabResult.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicHL7LabResult : (CachedDemographicHL7LabResult.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicHL7LabResult = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicHL7LabResult")), CachedDemographicHL7LabResult.pcFieldNames, CachedDemographicHL7LabResult.pcFieldTypes, CachedDemographicHL7LabResult.pcFieldFlags, CachedDemographicHL7LabResult.pcPCSuperclass, "CachedDemographicHL7LabResult", (PersistenceCapable)new CachedDemographicHL7LabResult());
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
     * Clears all persistent fields to their default values.
     *
     * <p>This OpenJPA infrastructure method resets all persistent fields to their initial state:
     * numeric fields to 0 or null, and reference fields to null. Used during entity lifecycle
     * management and persistence operations.</p>
     */
    protected void pcClearFields() {
        this.caisiDemographicId = 0;
        this.data = null;
        this.facilityIdLabResultCompositePk = null;
        this.type = null;
    }

    /**
     * Creates a new instance managed by the specified state manager with key fields from object ID.
     *
     * <p>This OpenJPA infrastructure method creates a new entity instance, optionally clears its fields,
     * assigns the provided state manager, and copies key field values from the given object ID. Used
     * during entity retrieval and detachment operations.</p>
     *
     * @param pcStateManager StateManager the state manager to manage this instance
     * @param o Object the object ID containing key field values to copy
     * @param b boolean true to clear fields after creation, false to retain default values
     * @return PersistenceCapable the newly created managed instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicHL7LabResult cachedDemographicHL7LabResult = new CachedDemographicHL7LabResult();
        if (b) {
            cachedDemographicHL7LabResult.pcClearFields();
        }
        cachedDemographicHL7LabResult.pcStateManager = pcStateManager;
        cachedDemographicHL7LabResult.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicHL7LabResult;
    }

    /**
     * Creates a new instance managed by the specified state manager.
     *
     * <p>This OpenJPA infrastructure method creates a new entity instance, optionally clears its fields,
     * and assigns the provided state manager. Used during entity instantiation and persistence operations.</p>
     *
     * @param pcStateManager StateManager the state manager to manage this instance
     * @param b boolean true to clear fields after creation, false to retain default values
     * @return PersistenceCapable the newly created managed instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicHL7LabResult cachedDemographicHL7LabResult = new CachedDemographicHL7LabResult();
        if (b) {
            cachedDemographicHL7LabResult.pcClearFields();
        }
        cachedDemographicHL7LabResult.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicHL7LabResult;
    }

    /**
     * Returns the number of persistent fields managed by OpenJPA for this entity.
     *
     * <p>This OpenJPA infrastructure method reports the count of persistent fields that are
     * tracked and managed for change detection and persistence operations. This entity has
     * 4 managed fields: caisiDemographicId, data, facilityIdLabResultCompositePk, and type.</p>
     *
     * @return int the number of managed persistent fields (4)
     */
    protected static int pcGetManagedFieldCount() {
        return 4;
    }

    /**
     * Replaces the value of a single persistent field from the state manager.
     *
     * <p>This OpenJPA infrastructure method updates a specific field's value by retrieving
     * the replacement value from the state manager. Used during entity refresh and merge operations.</p>
     *
     * @param n int the field index to replace (0=caisiDemographicId, 1=data, 2=facilityIdLabResultCompositePk, 3=type)
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicHL7LabResult.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.data = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.facilityIdLabResultCompositePk = (FacilityIdLabResultCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.type = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces the values of multiple persistent fields from the state manager.
     *
     * <p>This OpenJPA infrastructure method updates multiple fields by retrieving replacement
     * values from the state manager for each field index in the provided array. Used during
     * entity refresh and merge operations.</p>
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides the current value of a single persistent field to the state manager.
     *
     * <p>This OpenJPA infrastructure method supplies the current field value to the state manager
     * for change detection, persistence, and serialization operations.</p>
     *
     * @param n int the field index to provide (0=caisiDemographicId, 1=data, 2=facilityIdLabResultCompositePk, 3=type)
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedDemographicHL7LabResult.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.caisiDemographicId);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.data);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityIdLabResultCompositePk);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.type);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Provides the current values of multiple persistent fields to the state manager.
     *
     * <p>This OpenJPA infrastructure method supplies current field values to the state manager
     * for each field index in the provided array. Used during change detection, persistence,
     * and serialization operations.</p>
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    protected void pcCopyField(final CachedDemographicHL7LabResult cachedDemographicHL7LabResult, final int n) {
        final int n2 = n - CachedDemographicHL7LabResult.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = cachedDemographicHL7LabResult.caisiDemographicId;
                return;
            }
            case 1: {
                this.data = cachedDemographicHL7LabResult.data;
                return;
            }
            case 2: {
                this.facilityIdLabResultCompositePk = cachedDemographicHL7LabResult.facilityIdLabResultCompositePk;
                return;
            }
            case 3: {
                this.type = cachedDemographicHL7LabResult.type;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies specified field values from another instance of this entity.
     *
     * <p>This OpenJPA infrastructure method copies field values from the source object to this instance
     * for the field indices specified in the array. Both instances must be managed by the same state
     * manager. Used during entity merging and detachment operations.</p>
     *
     * @param o Object the source CachedDemographicHL7LabResult instance to copy from
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source object has a different state manager
     * @throws IllegalStateException if this instance has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicHL7LabResult cachedDemographicHL7LabResult = (CachedDemographicHL7LabResult)o;
        if (cachedDemographicHL7LabResult.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicHL7LabResult, array[i]);
        }
    }

    /**
     * Retrieves the generic context from the state manager.
     *
     * <p>This OpenJPA infrastructure method returns the generic context object from the state manager,
     * if one exists. The generic context may contain additional metadata or configuration used during
     * persistence operations.</p>
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
     * <p>This OpenJPA infrastructure method retrieves the object ID that uniquely identifies this
     * entity within the persistence context.</p>
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
     * <p>This OpenJPA infrastructure method determines if this entity is in a deleted state within
     * the current persistence context.</p>
     *
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks whether this entity has been modified since being loaded or last persisted.
     *
     * <p>This OpenJPA infrastructure method performs change detection to determine if any persistent
     * fields have been modified. Returns false if no state manager is present.</p>
     *
     * @return boolean true if the entity has pending changes, false otherwise
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
     * Checks whether this entity is newly created and not yet persisted.
     *
     * <p>This OpenJPA infrastructure method determines if this entity is in a transient state,
     * newly created but not yet saved to the database.</p>
     *
     * @return boolean true if the entity is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks whether this entity is managed by a persistence context.
     *
     * <p>This OpenJPA infrastructure method determines if this entity is in a persistent state,
     * managed by the persistence provider.</p>
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks whether this entity is enrolled in a transaction.
     *
     * <p>This OpenJPA infrastructure method determines if this entity is participating in an
     * active transaction context.</p>
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks whether this entity is currently being serialized.
     *
     * <p>This OpenJPA infrastructure method determines if serialization is in progress for this
     * entity. Used to control behavior during serialization operations.</p>
     *
     * @return boolean true if serialization is in progress, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a specific field as dirty for change tracking.
     *
     * <p>This OpenJPA infrastructure method notifies the state manager that the specified field
     * has been modified, triggering change detection and potentially requiring persistence on
     * transaction commit.</p>
     *
     * @param s String the name of the field that has been modified
     */
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }

    /**
     * Retrieves the state manager managing this persistent instance.
     *
     * <p>This OpenJPA infrastructure method returns the state manager responsible for tracking
     * this entity's lifecycle and field changes.</p>
     *
     * @return StateManager the state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version information for this entity.
     *
     * <p>This OpenJPA infrastructure method returns version metadata used for optimistic locking
     * and concurrency control.</p>
     *
     * @return Object the version information, or null if no state manager is present
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
     * <p>This OpenJPA infrastructure method changes the state manager responsible for managing this
     * entity. If a state manager already exists, it delegates the replacement operation to that
     * manager; otherwise, the new manager is assigned directly.</p>
     *
     * @param pcStateManager StateManager the new state manager to assign
     * @throws SecurityException if the replacement is not permitted
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
     * <p>This OpenJPA infrastructure method is not supported for this entity type and will
     * throw an InternalException if called.</p>
     *
     * @param fieldSupplier FieldSupplier the field supplier to use
     * @param o Object the target object ID
     * @throws InternalException always, as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key field values to an object ID.
     *
     * <p>This OpenJPA infrastructure method is not supported for this entity type and will
     * throw an InternalException if called.</p>
     *
     * @param o Object the target object ID
     * @throws InternalException always, as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key field values from an object ID using a field consumer.
     *
     * <p>This OpenJPA infrastructure method extracts the composite key from the provided object ID
     * and stores it in the field consumer at the appropriate field index.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer to store the key value
     * @param o Object the source ObjectId containing the composite key
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(2 + CachedDemographicHL7LabResult.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key field values from an object ID to this entity.
     *
     * <p>This OpenJPA infrastructure method extracts the composite key from the provided object ID
     * and assigns it to this entity's facilityIdLabResultCompositePk field.</p>
     *
     * @param o Object the source ObjectId containing the composite key
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityIdLabResultCompositePk = (FacilityIdLabResultCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * <p>This OpenJPA infrastructure method is not supported for this entity type because the
     * composite key class does not support string-based construction.</p>
     *
     * @param o Object the string representation (not used)
     * @return Object never returns
     * @throws IllegalArgumentException always, as string-based object ID construction is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicHL7LabResult\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance for this entity.
     *
     * <p>This OpenJPA infrastructure method constructs an ObjectId wrapping this entity's
     * composite key, which can be used for identity comparisons and caching.</p>
     *
     * @return Object a new ObjectId containing this entity's composite key
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicHL7LabResult.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicHL7LabResult != null) ? CachedDemographicHL7LabResult.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicHL7LabResult : (CachedDemographicHL7LabResult.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicHL7LabResult = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicHL7LabResult")), (Object)this.facilityIdLabResultCompositePk);
    }
    
    private static final int pcGetcaisiDemographicId(final CachedDemographicHL7LabResult cachedDemographicHL7LabResult) {
        if (cachedDemographicHL7LabResult.pcStateManager == null) {
            return cachedDemographicHL7LabResult.caisiDemographicId;
        }
        cachedDemographicHL7LabResult.pcStateManager.accessingField(CachedDemographicHL7LabResult.pcInheritedFieldCount + 0);
        return cachedDemographicHL7LabResult.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedDemographicHL7LabResult cachedDemographicHL7LabResult, final int caisiDemographicId) {
        if (cachedDemographicHL7LabResult.pcStateManager == null) {
            cachedDemographicHL7LabResult.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedDemographicHL7LabResult.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicHL7LabResult, CachedDemographicHL7LabResult.pcInheritedFieldCount + 0, cachedDemographicHL7LabResult.caisiDemographicId, caisiDemographicId, 0);
    }
    
    private static final String pcGetdata(final CachedDemographicHL7LabResult cachedDemographicHL7LabResult) {
        if (cachedDemographicHL7LabResult.pcStateManager == null) {
            return cachedDemographicHL7LabResult.data;
        }
        cachedDemographicHL7LabResult.pcStateManager.accessingField(CachedDemographicHL7LabResult.pcInheritedFieldCount + 1);
        return cachedDemographicHL7LabResult.data;
    }
    
    private static final void pcSetdata(final CachedDemographicHL7LabResult cachedDemographicHL7LabResult, final String data) {
        if (cachedDemographicHL7LabResult.pcStateManager == null) {
            cachedDemographicHL7LabResult.data = data;
            return;
        }
        cachedDemographicHL7LabResult.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicHL7LabResult, CachedDemographicHL7LabResult.pcInheritedFieldCount + 1, cachedDemographicHL7LabResult.data, data, 0);
    }
    
    private static final FacilityIdLabResultCompositePk pcGetfacilityIdLabResultCompositePk(final CachedDemographicHL7LabResult cachedDemographicHL7LabResult) {
        if (cachedDemographicHL7LabResult.pcStateManager == null) {
            return cachedDemographicHL7LabResult.facilityIdLabResultCompositePk;
        }
        cachedDemographicHL7LabResult.pcStateManager.accessingField(CachedDemographicHL7LabResult.pcInheritedFieldCount + 2);
        return cachedDemographicHL7LabResult.facilityIdLabResultCompositePk;
    }
    
    private static final void pcSetfacilityIdLabResultCompositePk(final CachedDemographicHL7LabResult cachedDemographicHL7LabResult, final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk) {
        if (cachedDemographicHL7LabResult.pcStateManager == null) {
            cachedDemographicHL7LabResult.facilityIdLabResultCompositePk = facilityIdLabResultCompositePk;
            return;
        }
        cachedDemographicHL7LabResult.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicHL7LabResult, CachedDemographicHL7LabResult.pcInheritedFieldCount + 2, (Object)cachedDemographicHL7LabResult.facilityIdLabResultCompositePk, (Object)facilityIdLabResultCompositePk, 0);
    }
    
    private static final String pcGettype(final CachedDemographicHL7LabResult cachedDemographicHL7LabResult) {
        if (cachedDemographicHL7LabResult.pcStateManager == null) {
            return cachedDemographicHL7LabResult.type;
        }
        cachedDemographicHL7LabResult.pcStateManager.accessingField(CachedDemographicHL7LabResult.pcInheritedFieldCount + 3);
        return cachedDemographicHL7LabResult.type;
    }
    
    private static final void pcSettype(final CachedDemographicHL7LabResult cachedDemographicHL7LabResult, final String type) {
        if (cachedDemographicHL7LabResult.pcStateManager == null) {
            cachedDemographicHL7LabResult.type = type;
            return;
        }
        cachedDemographicHL7LabResult.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicHL7LabResult, CachedDemographicHL7LabResult.pcInheritedFieldCount + 3, cachedDemographicHL7LabResult.type, type, 0);
    }

    /**
     * Checks whether this entity is in a detached state.
     *
     * <p>This OpenJPA infrastructure method determines if the entity has been detached from its
     * persistence context. Returns TRUE if detached, FALSE if attached, or null if the state
     * cannot be definitively determined.</p>
     *
     * @return Boolean TRUE if detached, FALSE if attached, null if indeterminate
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
     * Checks whether the detached state is definitive for this entity.
     *
     * <p>This OpenJPA infrastructure method indicates whether the detached state can be
     * determined conclusively. Always returns false for this entity type.</p>
     *
     * @return boolean false, indicating detached state is not definitive
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Retrieves the detached state object for this entity.
     *
     * <p>This OpenJPA infrastructure method returns the object representing the detached state,
     * which may be null, DESERIALIZED, or contain version/timestamp information.</p>
     *
     * @return Object the detached state object, or null if not set
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object for this entity.
     *
     * <p>This OpenJPA infrastructure method assigns the detached state, which tracks whether
     * and how this entity has been detached from its persistence context. Used during
     * serialization and detachment operations.</p>
     *
     * @param pcDetachedState Object the detached state to assign
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method for writing this entity to an object stream.
     *
     * <p>This method handles the serialization of the entity, performing default serialization
     * and then clearing the detached state if serialization is in progress. This ensures proper
     * state management when the entity is transmitted across process boundaries or persisted
     * to storage.</p>
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
     * Custom deserialization method for reading this entity from an object stream.
     *
     * <p>This method handles the deserialization of the entity, setting the detached state to
     * DESERIALIZED before performing default deserialization. This marks the entity as having
     * been deserialized and potentially detached from its original persistence context.</p>
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
