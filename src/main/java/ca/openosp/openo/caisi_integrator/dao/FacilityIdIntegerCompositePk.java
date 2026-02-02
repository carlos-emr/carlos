package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PersistenceCapable;
import org.apache.openjpa.enhance.PCRegistry;
import org.apache.openjpa.enhance.StateManager;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Embeddable;
import java.io.Serializable;

/**
 * Composite primary key class for CAISI Integrator facility-based entities.
 *
 * This embeddable key combines a facility identifier with an item identifier to uniquely
 * identify entities across multiple healthcare facilities in the CAISI (Community Access
 * Information Systems Integration) network. The class is enhanced by Apache OpenJPA for
 * persistence management and implements state tracking for detached entities.
 *
 * The composite key structure enables:
 * <ul>
 *   <li>Cross-facility data sharing in multi-site healthcare deployments</li>
 *   <li>Unique identification of shared clinical data (demographics, appointments, etc.)</li>
 *   <li>Integration with the Integrator system for inter-EMR communication</li>
 * </ul>
 *
 * This class is bytecode-enhanced by OpenJPA at build time to implement transparent
 * persistence capabilities including field-level change tracking, lazy loading, and
 * detached state management.
 *
 * @since 2026-01-24
 */
@Embeddable
public class FacilityIdIntegerCompositePk implements Serializable, Comparable<FacilityIdIntegerCompositePk>, PersistenceCapable
{
    @Index
    private Integer integratorFacilityId;
    @Index
    private Integer caisiItemId;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor for JPA and OpenJPA framework use.
     *
     * Initializes both key fields to null. This constructor is required by JPA
     * specification for embeddable types and is used by the persistence framework
     * during entity loading and instantiation.
     */
    public FacilityIdIntegerCompositePk() {
        this.integratorFacilityId = null;
        this.caisiItemId = null;
    }

    /**
     * Constructs a composite primary key with specified facility and item identifiers.
     *
     * Creates a new composite key for identifying an entity within the CAISI Integrator
     * system. The combination of facility ID and item ID must be unique across the system.
     *
     * @param integratorFacilityId Integer the facility identifier in the CAISI Integrator network
     * @param caisiItemId Integer the unique item identifier within the facility's scope
     */
    public FacilityIdIntegerCompositePk(final Integer integratorFacilityId, final Integer caisiItemId) {
        this.integratorFacilityId = null;
        this.caisiItemId = null;
        this.integratorFacilityId = integratorFacilityId;
        this.caisiItemId = caisiItemId;
    }
    
    /**
     * Gets the integrator facility identifier.
     *
     * Returns the facility ID component of this composite key, which identifies the
     * specific healthcare facility within the CAISI Integrator network. This method
     * delegates to the OpenJPA-enhanced accessor for proper state management.
     *
     * @return Integer the facility identifier, or null if not set
     */
    public Integer getIntegratorFacilityId() {
        return pcGetintegratorFacilityId(this);
    }

    /**
     * Sets the integrator facility identifier.
     *
     * Updates the facility ID component of this composite key. This method delegates
     * to the OpenJPA-enhanced mutator to ensure proper change tracking and dirty
     * field management.
     *
     * @param integratorFacilityId Integer the facility identifier to set
     */
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        pcSetintegratorFacilityId(this, integratorFacilityId);
    }

    /**
     * Gets the CAISI item identifier.
     *
     * Returns the item ID component of this composite key, which uniquely identifies
     * an entity within the scope of a specific facility. This method delegates to the
     * OpenJPA-enhanced accessor for proper state management.
     *
     * @return Integer the item identifier, or null if not set
     */
    public Integer getCaisiItemId() {
        return pcGetcaisiItemId(this);
    }

    /**
     * Sets the CAISI item identifier.
     *
     * Updates the item ID component of this composite key. This method delegates to
     * the OpenJPA-enhanced mutator to ensure proper change tracking and dirty field
     * management.
     *
     * @param caisiItemId Integer the item identifier to set
     */
    public void setCaisiItemId(final Integer caisiItemId) {
        pcSetcaisiItemId(this, caisiItemId);
    }
    
    /**
     * Returns a string representation of this composite key.
     *
     * Formats the key as "facilityId:itemId" for logging and debugging purposes.
     * Uses the OpenJPA-enhanced accessors to ensure proper state management.
     *
     * @return String the composite key in format "facilityId:itemId"
     */
    @Override
    public String toString() {
        return String.valueOf(pcGetintegratorFacilityId(this)) + ':' + pcGetcaisiItemId(this);
    }

    /**
     * Returns the hash code for this composite key.
     *
     * Uses only the CAISI item ID for hash code calculation. This provides adequate
     * distribution for hash-based collections while maintaining consistency with equals.
     *
     * @return int hash code based on the item ID component
     */
    @Override
    public int hashCode() {
        return pcGetcaisiItemId(this);
    }

    /**
     * Compares this composite key to another object for equality.
     *
     * Two composite keys are considered equal if both their facility IDs and item IDs
     * are equal. Returns false if the comparison fails due to null values or type mismatch.
     * Uses the OpenJPA-enhanced accessors to ensure proper state management during comparison.
     *
     * @param o Object the object to compare with
     * @return boolean true if both key components are equal, false otherwise
     */
    @Override
    public boolean equals(final Object o) {
        try {
            final FacilityIdIntegerCompositePk o2 = (FacilityIdIntegerCompositePk)o;
            return pcGetintegratorFacilityId(this).equals(pcGetintegratorFacilityId(o2)) && pcGetcaisiItemId(this).equals(pcGetcaisiItemId(o2));
        }
        catch (final RuntimeException e) {
            return false;
        }
    }

    /**
     * Compares this composite key to another for ordering.
     *
     * Comparison is based on the string representation of the keys, providing a
     * consistent ordering for sorted collections. The ordering is lexicographic
     * based on the "facilityId:itemId" format.
     *
     * @param o FacilityIdIntegerCompositePk the key to compare with
     * @return int negative if this key is less, zero if equal, positive if greater
     */
    @Override
    public int compareTo(final FacilityIdIntegerCompositePk o) {
        return this.toString().compareTo(o.toString());
    }
    
    /**
     * Gets the OpenJPA enhancement contract version.
     *
     * Returns the version number of the OpenJPA enhancement contract implemented by
     * this bytecode-enhanced class. Version 2 indicates compatibility with OpenJPA's
     * current enhancement specifications.
     *
     * @return int the enhancement contract version (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 6684273625189092823L;
        FacilityIdIntegerCompositePk.pcFieldNames = new String[] { "caisiItemId", "integratorFacilityId" };
        FacilityIdIntegerCompositePk.pcFieldTypes = new Class[] { (FacilityIdIntegerCompositePk.class$Ljava$lang$Integer != null) ? FacilityIdIntegerCompositePk.class$Ljava$lang$Integer : (FacilityIdIntegerCompositePk.class$Ljava$lang$Integer = class$("java.lang.Integer")), (FacilityIdIntegerCompositePk.class$Ljava$lang$Integer != null) ? FacilityIdIntegerCompositePk.class$Ljava$lang$Integer : (FacilityIdIntegerCompositePk.class$Ljava$lang$Integer = class$("java.lang.Integer")) };
        FacilityIdIntegerCompositePk.pcFieldFlags = new byte[] { 26, 26 };
        PCRegistry.register((FacilityIdIntegerCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? FacilityIdIntegerCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (FacilityIdIntegerCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), FacilityIdIntegerCompositePk.pcFieldNames, FacilityIdIntegerCompositePk.pcFieldTypes, FacilityIdIntegerCompositePk.pcFieldFlags, FacilityIdIntegerCompositePk.pcPCSuperclass, (String)null, (PersistenceCapable)new FacilityIdIntegerCompositePk());
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
     * Clears all managed fields to their default null values.
     *
     * Used by OpenJPA framework during entity initialization and state management
     * to reset field values. This method is called by the persistence framework
     * and should not be invoked directly by application code.
     */
    protected void pcClearFields() {
        this.caisiItemId = null;
        this.integratorFacilityId = null;
    }

    /**
     * Creates a new instance with state manager and object ID.
     *
     * Factory method used by OpenJPA to create new instances during entity loading
     * and state management operations. Optionally clears fields and copies key values
     * from the provided object ID.
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object ID to copy key fields from
     * @param b boolean true to clear fields after instantiation
     * @return PersistenceCapable a new instance with configured state manager
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk = new FacilityIdIntegerCompositePk();
        if (b) {
            facilityIdIntegerCompositePk.pcClearFields();
        }
        facilityIdIntegerCompositePk.pcStateManager = pcStateManager;
        facilityIdIntegerCompositePk.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)facilityIdIntegerCompositePk;
    }

    /**
     * Creates a new instance with state manager.
     *
     * Factory method used by OpenJPA to create new instances during entity operations.
     * Optionally clears fields after instantiation based on the provided flag.
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean true to clear fields after instantiation
     * @return PersistenceCapable a new instance with configured state manager
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk = new FacilityIdIntegerCompositePk();
        if (b) {
            facilityIdIntegerCompositePk.pcClearFields();
        }
        facilityIdIntegerCompositePk.pcStateManager = pcStateManager;
        return (PersistenceCapable)facilityIdIntegerCompositePk;
    }

    /**
     * Gets the count of fields managed by OpenJPA.
     *
     * Returns the total number of persistent fields managed by the OpenJPA framework
     * for this entity class (2: caisiItemId and integratorFacilityId).
     *
     * @return int the number of managed fields (2)
     */
    protected static int pcGetManagedFieldCount() {
        return 2;
    }
    
    /**
     * Replaces a single field value using the state manager.
     *
     * Called by OpenJPA during entity loading and refresh operations to replace field
     * values with values managed by the state manager. The field index is adjusted
     * for inheritance and validated before replacement.
     *
     * @param n int the absolute field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - FacilityIdIntegerCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiItemId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.integratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple field values using the state manager.
     *
     * Batch operation that delegates to pcReplaceField for each field index in the
     * provided array. Used by OpenJPA for efficient bulk field replacement operations.
     *
     * @param array int[] array of field indices to replace
     * @throws IllegalArgumentException if any field index is invalid
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a field value to the state manager.
     *
     * Called by OpenJPA to retrieve field values for persistence operations such as
     * flush and serialization. The field index is validated and the corresponding
     * field value is provided to the state manager.
     *
     * @param n int the absolute field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - FacilityIdIntegerCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiItemId);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.integratorFacilityId);
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
     * Batch operation that delegates to pcProvideField for each field index in the
     * provided array. Used by OpenJPA for efficient bulk field retrieval operations.
     *
     * @param array int[] array of field indices to provide
     * @throws IllegalArgumentException if any field index is invalid
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    /**
     * Copies a single field value from another instance.
     *
     * Used by OpenJPA for field-level copying during merge and refresh operations.
     * The field index is validated and the corresponding field value is copied directly
     * from the source instance.
     *
     * @param facilityIdIntegerCompositePk FacilityIdIntegerCompositePk the source instance to copy from
     * @param n int the absolute field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk, final int n) {
        final int n2 = n - FacilityIdIntegerCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiItemId = facilityIdIntegerCompositePk.caisiItemId;
                return;
            }
            case 1: {
                this.integratorFacilityId = facilityIdIntegerCompositePk.integratorFacilityId;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies multiple field values from another instance.
     *
     * Batch operation used by OpenJPA during merge operations. Validates that both
     * instances share the same state manager before copying the specified fields.
     *
     * @param o Object the source instance to copy from (must be FacilityIdIntegerCompositePk)
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if instances have different state managers
     * @throws IllegalStateException if the state manager is null
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)o;
        if (facilityIdIntegerCompositePk.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(facilityIdIntegerCompositePk, array[i]);
        }
    }
    
    /**
     * Gets the generic context from the state manager.
     *
     * Returns the generic context object associated with this entity's state manager,
     * which may contain framework-specific metadata or configuration.
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
     * Fetches the object ID for this entity.
     *
     * Returns the object identifier assigned by OpenJPA to uniquely identify this
     * entity instance within the persistence context.
     *
     * @return Object the object ID, or null if no state manager is present
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks if this entity is marked for deletion.
     *
     * Returns true if the entity has been deleted within the current persistence context
     * but the deletion has not yet been flushed to the database.
     *
     * @return boolean true if marked for deletion, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has been modified.
     *
     * Performs a dirty check to determine if any fields have been changed since the
     * entity was loaded or last persisted. Used by OpenJPA to optimize flush operations.
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
     * Checks if this is a newly created entity.
     *
     * Returns true if the entity has been persisted in the current transaction but
     * has not yet been committed to the database.
     *
     * @return boolean true if newly created, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is persistent.
     *
     * Returns true if the entity is managed by the persistence context and has a
     * representation in the database (either existing or pending insert).
     *
     * @return boolean true if persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity is transactional.
     *
     * Returns true if the entity is participating in an active transaction and
     * changes will be tracked and synchronized with the database.
     *
     * @return boolean true if transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity is currently being serialized.
     *
     * Returns true during serialization operations, which allows special handling
     * of transient fields and state management during serialization.
     *
     * @return boolean true if serializing, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a field as dirty in the state manager.
     *
     * Notifies the state manager that the specified field has been modified, ensuring
     * the change will be included in the next flush operation.
     *
     * @param s String the field name that was modified
     */
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }

    /**
     * Gets the state manager for this entity.
     *
     * Returns the OpenJPA state manager responsible for tracking the lifecycle and
     * state changes of this entity instance.
     *
     * @return StateManager the state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Gets the version identifier for optimistic locking.
     *
     * Returns the version value used by OpenJPA for optimistic concurrency control,
     * if version fields are configured for this entity.
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
     * Replaces the state manager for this entity.
     *
     * Used by OpenJPA framework to reassign state management responsibilities,
     * typically during entity lifecycle transitions or persistence context changes.
     *
     * @param pcStateManager StateManager the new state manager to assign
     * @throws SecurityException if state manager replacement is not permitted
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
     *
     * This implementation is intentionally empty as composite primary keys handle
     * their own identity. For embeddable types, the instance itself serves as the
     * object ID representation.
     *
     * @param fieldSupplier FieldSupplier the field supplier to use (unused)
     * @param o Object the target object ID (unused)
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
    }

    /**
     * Copies key fields to an object ID.
     *
     * This implementation is intentionally empty as composite primary keys handle
     * their own identity. For embeddable types, the instance itself serves as the
     * object ID representation.
     *
     * @param o Object the target object ID (unused)
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
    }

    /**
     * Copies key fields from an object ID using a field consumer.
     *
     * This implementation is intentionally empty as composite primary keys handle
     * their own identity. For embeddable types, the instance itself serves as the
     * object ID representation.
     *
     * @param fieldConsumer FieldConsumer the field consumer to use (unused)
     * @param o Object the source object ID (unused)
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
    }

    /**
     * Copies key fields from an object ID.
     *
     * This implementation is intentionally empty as composite primary keys handle
     * their own identity. For embeddable types, the instance itself serves as the
     * object ID representation.
     *
     * @param o Object the source object ID (unused)
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
    }

    /**
     * Creates a new object ID instance.
     *
     * Returns null because composite primary key classes serve as their own object IDs.
     * The embeddable instance itself is used for identity purposes in OpenJPA.
     *
     * @return Object always returns null for embeddable composite keys
     */
    public Object pcNewObjectIdInstance() {
        return null;
    }

    /**
     * Creates a new object ID instance from a source object.
     *
     * Returns null because composite primary key classes serve as their own object IDs.
     * The embeddable instance itself is used for identity purposes in OpenJPA.
     *
     * @param o Object the source object (unused)
     * @return Object always returns null for embeddable composite keys
     */
    public Object pcNewObjectIdInstance(final Object o) {
        return null;
    }
    
    private static final Integer pcGetcaisiItemId(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        if (facilityIdIntegerCompositePk.pcStateManager == null) {
            return facilityIdIntegerCompositePk.caisiItemId;
        }
        facilityIdIntegerCompositePk.pcStateManager.accessingField(FacilityIdIntegerCompositePk.pcInheritedFieldCount + 0);
        return facilityIdIntegerCompositePk.caisiItemId;
    }
    
    private static final void pcSetcaisiItemId(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk, final Integer caisiItemId) {
        if (facilityIdIntegerCompositePk.pcStateManager == null) {
            facilityIdIntegerCompositePk.caisiItemId = caisiItemId;
            return;
        }
        facilityIdIntegerCompositePk.pcStateManager.settingObjectField((PersistenceCapable)facilityIdIntegerCompositePk, FacilityIdIntegerCompositePk.pcInheritedFieldCount + 0, (Object)facilityIdIntegerCompositePk.caisiItemId, (Object)caisiItemId, 0);
    }
    
    private static final Integer pcGetintegratorFacilityId(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        if (facilityIdIntegerCompositePk.pcStateManager == null) {
            return facilityIdIntegerCompositePk.integratorFacilityId;
        }
        facilityIdIntegerCompositePk.pcStateManager.accessingField(FacilityIdIntegerCompositePk.pcInheritedFieldCount + 1);
        return facilityIdIntegerCompositePk.integratorFacilityId;
    }
    
    private static final void pcSetintegratorFacilityId(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk, final Integer integratorFacilityId) {
        if (facilityIdIntegerCompositePk.pcStateManager == null) {
            facilityIdIntegerCompositePk.integratorFacilityId = integratorFacilityId;
            return;
        }
        facilityIdIntegerCompositePk.pcStateManager.settingObjectField((PersistenceCapable)facilityIdIntegerCompositePk, FacilityIdIntegerCompositePk.pcInheritedFieldCount + 1, (Object)facilityIdIntegerCompositePk.integratorFacilityId, (Object)integratorFacilityId, 0);
    }
    
    /**
     * Checks if this entity is in a detached state.
     *
     * Returns the detachment status of this entity instance:
     * - TRUE: definitively detached from the persistence context
     * - FALSE: definitively attached to a persistence context
     * - null: detachment status cannot be determined
     *
     * This tri-state return allows OpenJPA to handle entities whose state cannot
     * be definitively determined without a state manager.
     *
     * @return Boolean the detachment status (TRUE, FALSE, or null)
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
     * Checks if the detached state indicator is definitive.
     *
     * Returns false to indicate that detachment status cannot be definitively
     * determined for this embeddable type without examining the state manager.
     *
     * @return boolean always returns false for this embeddable type
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Gets the detached state marker object.
     *
     * Returns the internal marker object used by OpenJPA to track whether this
     * entity instance has been detached from a persistence context. The marker
     * may be null, DESERIALIZED, or contain detachment metadata.
     *
     * @return Object the detached state marker
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state marker object.
     *
     * Updates the internal marker used by OpenJPA to track detachment state.
     * This is called by the framework during detachment and serialization operations.
     *
     * @param pcDetachedState Object the detached state marker to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method for Java serialization protocol.
     *
     * Handles serialization of this entity instance, performing default serialization
     * and clearing the detached state marker if the entity is being serialized by
     * the persistence framework. This prevents unnecessary metadata from being included
     * in the serialized form.
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
     * Custom deserialization method for Java serialization protocol.
     *
     * Handles deserialization of this entity instance, marking the detached state
     * as DESERIALIZED before performing default deserialization. This signals to
     * OpenJPA that the instance was reconstituted via serialization rather than
     * loaded from the database.
     *
     * @param objectInputStream ObjectInputStream the stream to read from
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if a class cannot be found during deserialization
     */
    private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        this.pcSetDetachedState(PersistenceCapable.DESERIALIZED);
        objectInputStream.defaultReadObject();
    }
}
