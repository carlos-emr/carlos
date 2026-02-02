package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PCRegistry;
import org.apache.commons.lang3.StringUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.Column;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Embeddable;
import org.apache.openjpa.enhance.PersistenceCapable;
import java.io.Serializable;

/**
 * Composite primary key for CAISI Integrator lab result entities.
 *
 * This embeddable class represents the composite primary key used to uniquely identify
 * laboratory results within the CAISI (Client Access to Integrated Services and Information)
 * Integrator system. The key combines a facility identifier with a lab result identifier,
 * enabling cross-facility lab result tracking in multi-site healthcare environments.
 *
 * The class is enhanced by OpenJPA persistence framework to provide automatic field
 * tracking, state management, and detachment capabilities for JPA operations. This
 * enhancement enables transparent persistence and efficient caching of lab result
 * composite keys across distributed healthcare facilities.
 *
 * Key features:
 * <ul>
 *   <li>Composite key combining facility ID and lab result ID</li>
 *   <li>OpenJPA enhancement for transparent persistence</li>
 *   <li>Serialization support for distributed operations</li>
 *   <li>State management for detached entity handling</li>
 *   <li>Indexed fields for optimized database queries</li>
 * </ul>
 *
 * @since 2026-01-24
 */
@Embeddable
public class FacilityIdLabResultCompositePk implements Serializable, PersistenceCapable
{
    @Index
    private Integer integratorFacilityId;
    @Column(length = 64)
    @Index
    private String labResultId;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor for JPA persistence framework.
     *
     * Creates a new composite primary key instance with null field values.
     * This constructor is required by JPA specification for entity instantiation
     * during query result materialization and entity lifecycle operations.
     */
    public FacilityIdLabResultCompositePk() {
        this.integratorFacilityId = null;
        this.labResultId = null;
    }

    /**
     * Parameterized constructor for creating composite primary keys with specified values.
     *
     * Creates a new composite primary key instance initialized with the provided
     * facility identifier and lab result identifier. This constructor is typically
     * used when creating new lab result entities or performing queries based on
     * known composite key values.
     *
     * @param integratorFacilityId Integer the unique identifier for the healthcare facility
     *                             within the CAISI integrator system
     * @param labResultId String the unique identifier for the laboratory result, maximum
     *                    length 64 characters, will be trimmed to null if blank
     */
    public FacilityIdLabResultCompositePk(final Integer integratorFacilityId, final String labResultId) {
        this.integratorFacilityId = null;
        this.labResultId = null;
        this.integratorFacilityId = integratorFacilityId;
        this.labResultId = labResultId;
    }
    
    /**
     * Retrieves the integrator facility identifier component of this composite key.
     *
     * This method returns the facility identifier that represents a specific healthcare
     * facility within the CAISI integrator system. The facility ID is part of the composite
     * key used to uniquely identify lab results across multiple facilities.
     *
     * @return Integer the integrator facility identifier, or null if not set
     */
    public Integer getIntegratorFacilityId() {
        return pcGetintegratorFacilityId(this);
    }

    /**
     * Sets the integrator facility identifier component of this composite key.
     *
     * Updates the facility identifier for this composite key. This method triggers
     * OpenJPA state management to track field modifications for persistence operations.
     *
     * @param integratorFacilityId Integer the integrator facility identifier to set
     */
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        pcSetintegratorFacilityId(this, integratorFacilityId);
    }

    /**
     * Retrieves the lab result identifier component of this composite key.
     *
     * This method returns the lab result identifier that uniquely identifies a specific
     * laboratory result within a facility. Combined with the facility ID, this forms
     * the complete composite primary key for lab result entities.
     *
     * @return String the lab result identifier (maximum 64 characters), or null if not set
     */
    public String getLabResultId() {
        return pcGetlabResultId(this);
    }

    /**
     * Sets the lab result identifier component of this composite key.
     *
     * Updates the lab result identifier for this composite key. The provided value
     * is automatically trimmed to null if it contains only whitespace, ensuring
     * consistent handling of blank values. This method triggers OpenJPA state
     * management to track field modifications.
     *
     * @param labResultId String the lab result identifier to set (maximum 64 characters),
     *                    will be trimmed to null if blank
     */
    public void setLabResultId(final String labResultId) {
        pcSetlabResultId(this, StringUtils.trimToNull(labResultId));
    }
    
    /**
     * Returns a string representation of this composite primary key.
     *
     * The string format is "facilityId:labResultId" which provides a human-readable
     * representation of the composite key components. This format is useful for
     * logging and debugging purposes.
     *
     * @return String the composite key in format "facilityId:labResultId"
     */
    @Override
    public String toString() {
        return "" + pcGetintegratorFacilityId(this) + ':' + pcGetlabResultId(this);
    }

    /**
     * Computes the hash code for this composite primary key.
     *
     * The hash code is based solely on the lab result identifier component.
     * This implementation provides consistent hashing for use in hash-based
     * collections such as HashMap and HashSet.
     *
     * Note: This implementation only uses labResultId for hashing. This may
     * lead to hash collisions when multiple facilities have lab results with
     * the same identifier.
     *
     * @return int the hash code value derived from the lab result identifier
     */
    @Override
    public int hashCode() {
        return pcGetlabResultId(this).hashCode();
    }

    /**
     * Determines whether this composite key is equal to another object.
     *
     * Two composite keys are considered equal if they are both instances of
     * FacilityIdLabResultCompositePk and have equal facility IDs and lab result IDs.
     * This method is essential for entity identity comparison in JPA operations
     * and collection membership testing.
     *
     * The method safely handles type casting and null values by catching runtime
     * exceptions and returning false for any comparison failures.
     *
     * @param o Object the object to compare with this composite key
     * @return boolean true if the objects are equal composite keys with matching
     *         facility IDs and lab result IDs, false otherwise
     */
    @Override
    public boolean equals(final Object o) {
        try {
            final FacilityIdLabResultCompositePk o2 = (FacilityIdLabResultCompositePk)o;
            return pcGetintegratorFacilityId(this).equals(pcGetintegratorFacilityId(o2)) && pcGetlabResultId(this).equals(pcGetlabResultId(o2));
        }
        catch (final RuntimeException e) {
            return false;
        }
    }
    
    /**
     * Returns the OpenJPA enhancement contract version for this persistence capable class.
     *
     * This method indicates the version of the OpenJPA enhancement contract that
     * this class implements. The version number ensures compatibility between the
     * enhanced bytecode and the OpenJPA runtime environment.
     *
     * @return int the enhancement contract version number (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 4579769637734049905L;
        FacilityIdLabResultCompositePk.pcFieldNames = new String[] { "integratorFacilityId", "labResultId" };
        FacilityIdLabResultCompositePk.pcFieldTypes = new Class[] { (FacilityIdLabResultCompositePk.class$Ljava$lang$Integer != null) ? FacilityIdLabResultCompositePk.class$Ljava$lang$Integer : (FacilityIdLabResultCompositePk.class$Ljava$lang$Integer = class$("java.lang.Integer")), (FacilityIdLabResultCompositePk.class$Ljava$lang$String != null) ? FacilityIdLabResultCompositePk.class$Ljava$lang$String : (FacilityIdLabResultCompositePk.class$Ljava$lang$String = class$("java.lang.String")) };
        FacilityIdLabResultCompositePk.pcFieldFlags = new byte[] { 26, 26 };
        PCRegistry.register((FacilityIdLabResultCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk != null) ? FacilityIdLabResultCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk : (FacilityIdLabResultCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdLabResultCompositePk")), FacilityIdLabResultCompositePk.pcFieldNames, FacilityIdLabResultCompositePk.pcFieldTypes, FacilityIdLabResultCompositePk.pcFieldFlags, FacilityIdLabResultCompositePk.pcPCSuperclass, (String)null, (PersistenceCapable)new FacilityIdLabResultCompositePk());
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
     * Clears all persistent field values to their default null state.
     *
     * This protected method resets both the integrator facility ID and lab result ID
     * fields to null. It is used internally by OpenJPA during entity lifecycle operations
     * such as creating new instances and clearing entity state.
     */
    protected void pcClearFields() {
        this.integratorFacilityId = null;
        this.labResultId = null;
    }

    /**
     * Creates a new persistence capable instance with object ID initialization.
     *
     * This factory method creates a new instance of the composite key, optionally
     * clears its fields, assigns the provided state manager, and copies key field
     * values from the specified object ID. This method is used by OpenJPA during
     * entity materialization from database queries.
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object ID from which to copy key field values
     * @param b boolean if true, clears all fields to null before initialization
     * @return PersistenceCapable the newly created composite key instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk = new FacilityIdLabResultCompositePk();
        if (b) {
            facilityIdLabResultCompositePk.pcClearFields();
        }
        facilityIdLabResultCompositePk.pcStateManager = pcStateManager;
        facilityIdLabResultCompositePk.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)facilityIdLabResultCompositePk;
    }

    /**
     * Creates a new persistence capable instance with state manager initialization.
     *
     * This factory method creates a new instance of the composite key, optionally
     * clears its fields, and assigns the provided state manager. This method is
     * used by OpenJPA during entity instantiation and detachment operations.
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean if true, clears all fields to null after creation
     * @return PersistenceCapable the newly created composite key instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk = new FacilityIdLabResultCompositePk();
        if (b) {
            facilityIdLabResultCompositePk.pcClearFields();
        }
        facilityIdLabResultCompositePk.pcStateManager = pcStateManager;
        return (PersistenceCapable)facilityIdLabResultCompositePk;
    }
    
    /**
     * Returns the total number of managed persistent fields in this class.
     *
     * This method returns the count of fields that are managed by the OpenJPA
     * persistence framework for this composite key class. The composite key
     * has two managed fields: integratorFacilityId and labResultId.
     *
     * @return int the number of managed persistent fields (2)
     */
    protected static int pcGetManagedFieldCount() {
        return 2;
    }

    /**
     * Replaces a single managed field value from the state manager.
     *
     * This method is used by OpenJPA to restore field values during entity
     * detachment and reattachment operations. It delegates to the state manager
     * to retrieve the appropriate value based on field index.
     *
     * @param n int the absolute field index to replace
     * @throws IllegalArgumentException if the field index is invalid or out of range
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - FacilityIdLabResultCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.integratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.labResultId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple managed field values from the state manager.
     *
     * This method iterates through the provided array of field indices and
     * replaces each corresponding field value by delegating to pcReplaceField.
     * Used by OpenJPA during bulk field restoration operations.
     *
     * @param array int[] array of absolute field indices to replace
     * @throws IllegalArgumentException if any field index is invalid
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }
    
    /**
     * Provides a single managed field value to the state manager.
     *
     * This method is used by OpenJPA to retrieve field values during persistence
     * operations such as flushing changes to the database. It provides the current
     * value of the specified field to the state manager based on field index.
     *
     * @param n int the absolute field index to provide
     * @throws IllegalArgumentException if the field index is invalid or out of range
     */
    public void pcProvideField(final int n) {
        final int n2 = n - FacilityIdLabResultCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.integratorFacilityId);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.labResultId);
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
     * This method iterates through the provided array of field indices and
     * provides each corresponding field value to the state manager by delegating
     * to pcProvideField. Used by OpenJPA during bulk field retrieval operations.
     *
     * @param array int[] array of absolute field indices to provide
     * @throws IllegalArgumentException if any field index is invalid
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    /**
     * Copies a single field value from another composite key instance.
     *
     * This protected method copies the value of a specified field from the source
     * composite key to this instance. Used internally by OpenJPA during entity
     * merging and state synchronization operations.
     *
     * @param facilityIdLabResultCompositePk FacilityIdLabResultCompositePk the source
     *                                       composite key from which to copy the field
     * @param n int the absolute field index to copy
     * @throws IllegalArgumentException if the field index is invalid or out of range
     */
    protected void pcCopyField(final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk, final int n) {
        final int n2 = n - FacilityIdLabResultCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.integratorFacilityId = facilityIdLabResultCompositePk.integratorFacilityId;
                return;
            }
            case 1: {
                this.labResultId = facilityIdLabResultCompositePk.labResultId;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies multiple field values from another persistence capable object.
     *
     * This method copies values for the specified fields from the source object
     * to this instance. Both objects must be managed by the same state manager,
     * and the state manager must not be null.
     *
     * @param o Object the source persistence capable object from which to copy fields
     * @param array int[] array of absolute field indices to copy
     * @throws IllegalArgumentException if the source object has a different state manager
     * @throws IllegalStateException if the state manager is null
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk = (FacilityIdLabResultCompositePk)o;
        if (facilityIdLabResultCompositePk.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(facilityIdLabResultCompositePk, array[i]);
        }
    }
    
    /**
     * Retrieves the generic context from the associated state manager.
     *
     * The generic context provides access to the persistence context and related
     * infrastructure managed by OpenJPA. Returns null if this instance is not
     * currently associated with a state manager.
     *
     * @return Object the generic context from the state manager, or null if no
     *         state manager is associated
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Retrieves the object identifier for this persistence capable instance.
     *
     * The object ID uniquely identifies this composite key within the persistence
     * context. Returns null if this instance is not currently managed by a state
     * manager or does not have an assigned object ID.
     *
     * @return Object the object identifier for this instance, or null if not available
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks whether this persistence capable instance has been deleted.
     *
     * An instance is considered deleted if it is managed by a state manager
     * and the state manager indicates the instance is marked for deletion.
     *
     * @return boolean true if this instance is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks whether this persistence capable instance has been modified.
     *
     * An instance is considered dirty if it has been modified since it was
     * loaded or last synchronized with the database. This method performs
     * a dirty check through the OpenJPA redefinition helper before querying
     * the state manager.
     *
     * @return boolean true if this instance has unsaved modifications, false otherwise
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
     * Checks whether this persistence capable instance is newly created.
     *
     * An instance is considered new if it has been created within the current
     * transaction but has not yet been persisted to the database.
     *
     * @return boolean true if this instance is new and not yet persisted, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks whether this persistence capable instance is persistent.
     *
     * An instance is considered persistent if it is associated with a database
     * record and managed by the persistence context.
     *
     * @return boolean true if this instance is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks whether this persistence capable instance is transactional.
     *
     * An instance is considered transactional if it is participating in the
     * current transaction and any changes will be synchronized with the database
     * upon transaction commit.
     *
     * @return boolean true if this instance is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks whether this persistence capable instance is currently being serialized.
     *
     * This method indicates whether the instance is in the process of serialization,
     * which affects how certain persistence operations are handled during the
     * serialization lifecycle.
     *
     * @return boolean true if this instance is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a specific field as dirty in the state manager.
     *
     * This method notifies the state manager that a field has been modified,
     * triggering change tracking for persistence operations. If no state manager
     * is associated, the call is silently ignored.
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
     * Retrieves the state manager associated with this persistence capable instance.
     *
     * The state manager is responsible for tracking entity state, managing persistence
     * operations, and coordinating with the OpenJPA persistence context.
     *
     * @return StateManager the state manager for this instance, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version information for this persistence capable instance.
     *
     * The version is used for optimistic locking to detect concurrent modifications.
     * Returns null if this instance is not managed by a state manager or does not
     * have version tracking enabled.
     *
     * @return Object the version information, or null if not available
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
     * This method is used during entity detachment and reattachment operations to
     * transfer management responsibility between different persistence contexts.
     * If a state manager is already present, it delegates the replacement to the
     * existing state manager for proper coordination.
     *
     * @param pcStateManager StateManager the new state manager to associate with this instance
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
     * This method is used to populate an object ID with key field values from
     * this composite key instance using the provided field supplier for value
     * extraction. Since this class is itself an embeddable composite key, this
     * method has no implementation as the composite key serves as its own identifier.
     *
     * @param fieldSupplier FieldSupplier the supplier for extracting field values
     * @param o Object the target object ID to populate
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
    }

    /**
     * Copies key field values to an object ID.
     *
     * This method is used to populate an object ID with key field values from
     * this composite key instance. Since this class is itself an embeddable
     * composite key, this method has no implementation as the composite key
     * serves as its own identifier.
     *
     * @param o Object the target object ID to populate
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
    }

    /**
     * Copies key field values from an object ID using a field consumer.
     *
     * This method is used to populate this composite key instance with values
     * from an object ID using the provided field consumer for value consumption.
     * Since this class is itself an embeddable composite key, this method has
     * no implementation as the composite key serves as its own identifier.
     *
     * @param fieldConsumer FieldConsumer the consumer for processing field values
     * @param o Object the source object ID from which to copy values
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
    }

    /**
     * Copies key field values from an object ID.
     *
     * This method is used to populate this composite key instance with values
     * from an object ID. Since this class is itself an embeddable composite key,
     * this method has no implementation as the composite key serves as its own
     * identifier.
     *
     * @param o Object the source object ID from which to copy values
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
    }

    /**
     * Creates a new object ID instance for this persistence capable class.
     *
     * This method would normally return a new instance of the object ID class
     * used to identify entities of this type. Since this class is itself an
     * embeddable composite key that serves as its own identifier, this method
     * returns null.
     *
     * @return Object always returns null for embeddable composite keys
     */
    public Object pcNewObjectIdInstance() {
        return null;
    }

    /**
     * Creates a new object ID instance initialized with a value.
     *
     * This method would normally return a new instance of the object ID class
     * initialized with the provided value. Since this class is itself an embeddable
     * composite key that serves as its own identifier, this method returns null.
     *
     * @param o Object the value to initialize the object ID with
     * @return Object always returns null for embeddable composite keys
     */
    public Object pcNewObjectIdInstance(final Object o) {
        return null;
    }
    
    private static final Integer pcGetintegratorFacilityId(final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk) {
        if (facilityIdLabResultCompositePk.pcStateManager == null) {
            return facilityIdLabResultCompositePk.integratorFacilityId;
        }
        facilityIdLabResultCompositePk.pcStateManager.accessingField(FacilityIdLabResultCompositePk.pcInheritedFieldCount + 0);
        return facilityIdLabResultCompositePk.integratorFacilityId;
    }
    
    private static final void pcSetintegratorFacilityId(final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk, final Integer integratorFacilityId) {
        if (facilityIdLabResultCompositePk.pcStateManager == null) {
            facilityIdLabResultCompositePk.integratorFacilityId = integratorFacilityId;
            return;
        }
        facilityIdLabResultCompositePk.pcStateManager.settingObjectField((PersistenceCapable)facilityIdLabResultCompositePk, FacilityIdLabResultCompositePk.pcInheritedFieldCount + 0, (Object)facilityIdLabResultCompositePk.integratorFacilityId, (Object)integratorFacilityId, 0);
    }
    
    private static final String pcGetlabResultId(final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk) {
        if (facilityIdLabResultCompositePk.pcStateManager == null) {
            return facilityIdLabResultCompositePk.labResultId;
        }
        facilityIdLabResultCompositePk.pcStateManager.accessingField(FacilityIdLabResultCompositePk.pcInheritedFieldCount + 1);
        return facilityIdLabResultCompositePk.labResultId;
    }
    
    private static final void pcSetlabResultId(final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk, final String labResultId) {
        if (facilityIdLabResultCompositePk.pcStateManager == null) {
            facilityIdLabResultCompositePk.labResultId = labResultId;
            return;
        }
        facilityIdLabResultCompositePk.pcStateManager.settingStringField((PersistenceCapable)facilityIdLabResultCompositePk, FacilityIdLabResultCompositePk.pcInheritedFieldCount + 1, facilityIdLabResultCompositePk.labResultId, labResultId, 0);
    }

    /**
     * Checks whether this persistence capable instance is in a detached state.
     *
     * A detached instance is one that was previously managed by a persistence
     * context but is no longer associated with an active state manager. This method
     * returns a Boolean (not boolean) to support tri-state logic: TRUE if definitely
     * detached, FALSE if definitely not detached, or null if the state is indeterminate.
     *
     * The determination is made by checking the state manager status if present, or
     * by examining the detached state field if no state manager is associated.
     *
     * @return Boolean TRUE if detached, FALSE if not detached, null if indeterminate
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
     * Determines whether the detached state can be definitively determined.
     *
     * This private method indicates whether the detached state field provides
     * definitive information about the detachment status. For this composite key
     * class, this always returns false, indicating that detachment state may be
     * indeterminate when no state manager is present.
     *
     * @return boolean always returns false for this implementation
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Retrieves the detached state marker for this persistence capable instance.
     *
     * The detached state is used to track whether an instance has been detached
     * from its persistence context. Common values include null (not detached),
     * DESERIALIZED (created through deserialization), or other state manager
     * specific markers.
     *
     * @return Object the detached state marker, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state marker for this persistence capable instance.
     *
     * This method is used by OpenJPA to mark instances as detached and track
     * their detachment status. The detached state is set during serialization
     * and detachment operations to maintain proper entity lifecycle management.
     *
     * @param pcDetachedState Object the detached state marker to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method for writing this object to an output stream.
     *
     * This method handles the special serialization requirements for persistence
     * capable objects. If the instance is being serialized as part of a persistence
     * operation, the detached state is cleared to null after writing the object.
     * This ensures proper detachment semantics when instances are transferred across
     * JVM boundaries.
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
     * Custom deserialization method for reading this object from an input stream.
     *
     * This method handles the special deserialization requirements for persistence
     * capable objects. Upon deserialization, the detached state is set to DESERIALIZED
     * to indicate that this instance was created through deserialization and may require
     * reattachment to a persistence context before use.
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
