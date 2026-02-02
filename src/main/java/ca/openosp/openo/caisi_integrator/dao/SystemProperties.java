package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.util.IntId;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.util.InternalException;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PersistenceCapable;
import org.apache.openjpa.enhance.PCRegistry;
import javax.persistence.PreRemove;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.Id;
import javax.persistence.Entity;

/**
 * System properties entity for the CAISI Integrator component of OpenO EMR.
 *
 * <p>This entity stores system-level configuration and metadata for the CAISI (Client Access to
 * Integrated Services and Information) Integrator, which enables healthcare data sharing across
 * multiple OpenO EMR installations. The SystemProperties entity maintains critical system state
 * including the database schema version to ensure data consistency during upgrades and migrations.</p>
 *
 * <p>This class is enhanced by OpenJPA for persistence capabilities and implements the JPA
 * {@link PersistenceCapable} interface. The OpenJPA bytecode enhancement adds field-level tracking,
 * state management, and lifecycle callbacks necessary for proper ORM functionality.</p>
 *
 * <p><strong>Important:</strong> This entity has a singleton pattern enforced by a fixed ID value
 * and prevents deletion through the {@link #jpaPreventDelete()} callback to maintain system
 * integrity.</p>
 *
 * @see AbstractModel
 * @see PersistenceCapable
 * @since 2026-01-24
 */
@Entity
public class SystemProperties extends AbstractModel<Integer> implements PersistenceCapable
{
    public static final int SYSTEM_PROPERTY_ID = 1;
    public static final int CODE_SCHEMA_VERSION = 1;
    @Id
    private Integer id;
    private int schemaVersion;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Constructs a new SystemProperties instance with default values.
     *
     * <p>Initializes the system properties with a fixed ID of {@value #SYSTEM_PROPERTY_ID} and
     * schema version of {@value #CODE_SCHEMA_VERSION}. The singleton pattern is enforced through
     * this fixed ID value.</p>
     */
    public SystemProperties() {
        this.id = Integer.valueOf(1);
        this.schemaVersion = 1;
    }

    /**
     * Retrieves the unique identifier for this system properties entity.
     *
     * <p>This method is enhanced by OpenJPA to track field access through the persistence state
     * manager. The ID is always {@value #SYSTEM_PROPERTY_ID} as this entity follows a singleton
     * pattern.</p>
     *
     * @return Integer the unique identifier, always returns {@value #SYSTEM_PROPERTY_ID}
     */
    @Override
    public Integer getId() {
        return pcGetid(this);
    }

    /**
     * Retrieves the database schema version for the CAISI Integrator system.
     *
     * <p>This method is enhanced by OpenJPA to track field access through the persistence state
     * manager. The schema version is used to determine database compatibility and trigger
     * necessary migrations during system upgrades.</p>
     *
     * @return int the current schema version number
     */
    public int getSchemaVersion() {
        return pcGetschemaVersion(this);
    }

    /**
     * JPA lifecycle callback that prevents deletion of system properties.
     *
     * <p>This method is automatically invoked by the JPA persistence provider before an entity
     * removal operation. It enforces system integrity by preventing deletion of the singleton
     * SystemProperties entity, which contains critical system configuration that must always
     * be present in the database.</p>
     *
     * @throws UnsupportedOperationException always thrown to prevent deletion
     */
    @PreRemove
    protected void jpaPreventDelete() {
        throw new UnsupportedOperationException("Remove is not allowed for this type of item.");
    }

    /**
     * Returns the OpenJPA enhancement contract version for this entity.
     *
     * <p>This method is part of the OpenJPA bytecode enhancement contract and indicates the
     * version of the enhancement specification that was used to enhance this class. The value
     * is used by OpenJPA to ensure compatibility between the enhanced class and the runtime
     * persistence framework.</p>
     *
     * @return int the enhancement contract version, always returns 2
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 1250262097996517519L;
        SystemProperties.pcFieldNames = new String[] { "id", "schemaVersion" };
        SystemProperties.pcFieldTypes = new Class[] { (SystemProperties.class$Ljava$lang$Integer != null) ? SystemProperties.class$Ljava$lang$Integer : (SystemProperties.class$Ljava$lang$Integer = class$("java.lang.Integer")), Integer.TYPE };
        SystemProperties.pcFieldFlags = new byte[] { 26, 26 };
        PCRegistry.register((SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties != null) ? SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties : (SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties = class$("ca.openosp.openo.caisi_integrator.dao.SystemProperties")), SystemProperties.pcFieldNames, SystemProperties.pcFieldTypes, SystemProperties.pcFieldFlags, SystemProperties.pcPCSuperclass, "SystemProperties", (PersistenceCapable)new SystemProperties());
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
     * <p>This method is part of the OpenJPA PersistenceCapable contract and is used during
     * entity lifecycle management to reset field values. It sets the ID to null and schema
     * version to 0, effectively clearing the entity's state.</p>
     */
    protected void pcClearFields() {
        this.id = null;
        this.schemaVersion = 0;
    }

    /**
     * Creates a new instance of SystemProperties with the specified state manager and object ID.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and is used by the
     * persistence framework to create new entity instances during operations like queries and
     * detachment. The object ID is copied to the new instance's key fields.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object ID containing key field values
     * @param b boolean if true, clears all fields before copying key fields
     * @return PersistenceCapable the newly created SystemProperties instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final SystemProperties systemProperties = new SystemProperties();
        if (b) {
            systemProperties.pcClearFields();
        }
        systemProperties.pcStateManager = pcStateManager;
        systemProperties.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)systemProperties;
    }

    /**
     * Creates a new instance of SystemProperties with the specified state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and is used by the
     * persistence framework to create new entity instances during operations that don't require
     * copying an object ID.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean if true, clears all fields after construction
     * @return PersistenceCapable the newly created SystemProperties instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final SystemProperties systemProperties = new SystemProperties();
        if (b) {
            systemProperties.pcClearFields();
        }
        systemProperties.pcStateManager = pcStateManager;
        return (PersistenceCapable)systemProperties;
    }

    /**
     * Returns the number of managed fields in this entity.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and indicates how many
     * persistent fields are managed by the persistence framework. For SystemProperties, there
     * are 2 managed fields: id and schemaVersion.</p>
     *
     * @return int the number of managed fields, always returns 2
     */
    protected static int pcGetManagedFieldCount() {
        return 2;
    }

    /**
     * Replaces a single persistent field with a value from the state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and is invoked during
     * state synchronization operations. It replaces the specified field (by index) with a value
     * obtained from the state manager, ensuring the entity reflects the current persistence state.</p>
     *
     * @param n int the absolute field index to replace
     * @throws IllegalArgumentException if the field index is invalid or out of range
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - SystemProperties.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.id = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.schemaVersion = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple persistent fields with values from the state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and provides batch
     * field replacement by delegating to {@link #pcReplaceField(int)} for each field index
     * in the provided array.</p>
     *
     * @param array int[] array of absolute field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides the current value of a single persistent field to the state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and is invoked during
     * state synchronization to communicate field values to the persistence framework. The current
     * value of the specified field (by index) is passed to the state manager.</p>
     *
     * @param n int the absolute field index to provide
     * @throws IllegalArgumentException if the field index is invalid or out of range
     */
    public void pcProvideField(final int n) {
        final int n2 = n - SystemProperties.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.id);
                return;
            }
            case 1: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.schemaVersion);
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
     * <p>This method is part of the OpenJPA PersistenceCapable contract and provides batch
     * field value communication by delegating to {@link #pcProvideField(int)} for each field
     * index in the provided array.</p>
     *
     * @param array int[] array of absolute field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }

    /**
     * Copies the value of a single persistent field from another SystemProperties instance.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and is used to copy
     * field values from one entity instance to another during operations like merge and refresh.</p>
     *
     * @param systemProperties SystemProperties the source instance to copy from
     * @param n int the absolute field index to copy
     * @throws IllegalArgumentException if the field index is invalid or out of range
     */
    protected void pcCopyField(final SystemProperties systemProperties, final int n) {
        final int n2 = n - SystemProperties.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.id = systemProperties.id;
                return;
            }
            case 1: {
                this.schemaVersion = systemProperties.schemaVersion;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies the values of multiple persistent fields from another entity instance.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and provides batch
     * field copying by delegating to {@link #pcCopyField(SystemProperties, int)} for each
     * field index in the provided array. Both instances must share the same state manager.</p>
     *
     * @param o Object the source instance to copy from (must be a SystemProperties instance)
     * @param array int[] array of absolute field indices to copy
     * @throws IllegalArgumentException if the source instance has a different state manager
     * @throws IllegalStateException if this instance has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final SystemProperties systemProperties = (SystemProperties)o;
        if (systemProperties.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(systemProperties, array[i]);
        }
    }

    /**
     * Retrieves the generic context from the state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and returns the generic
     * context object associated with the persistence state manager, or null if no state manager
     * is assigned.</p>
     *
     * @return Object the generic context from the state manager, or null if no state manager exists
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the object ID for this entity instance.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and retrieves the
     * object identity from the state manager, or null if no state manager is assigned.</p>
     *
     * @return Object the object ID for this instance, or null if no state manager exists
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Determines whether this entity instance has been deleted.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and queries the state
     * manager to check if this entity is marked for deletion within the current persistence context.</p>
     *
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Determines whether this entity instance has been modified.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and checks if any
     * persistent fields have been modified since the entity was loaded or last synchronized
     * with the database. The dirty check is performed through the state manager.</p>
     *
     * @return boolean true if the entity has unsaved modifications, false otherwise
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
     * Determines whether this entity instance is newly created.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and queries the state
     * manager to check if this entity is newly created and not yet persisted to the database.</p>
     *
     * @return boolean true if the entity is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Determines whether this entity instance is persistent.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and queries the state
     * manager to check if this entity is managed by the persistence context (either loaded from
     * the database or newly created within a transaction).</p>
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Determines whether this entity instance is transactional.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and queries the state
     * manager to check if this entity is participating in an active transaction.</p>
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Determines whether this entity instance is currently being serialized.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and queries the state
     * manager to check if the entity is in the process of being serialized. This affects how
     * certain persistence operations are handled.</p>
     *
     * @return boolean true if the entity is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks the specified field as dirty (modified).
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and notifies the state
     * manager that the specified field has been modified, triggering change tracking and
     * ensuring the modification will be persisted during the next flush or commit.</p>
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
     * Retrieves the state manager associated with this entity instance.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and returns the
     * state manager responsible for managing this entity's persistence lifecycle, change
     * tracking, and database synchronization.</p>
     *
     * @return StateManager the state manager for this entity, or null if not assigned
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version identifier for this entity instance.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and returns the
     * version object used for optimistic locking, or null if no state manager is assigned.
     * The version is used to detect concurrent modifications.</p>
     *
     * @return Object the version identifier, or null if no state manager exists
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
     * <p>This method is part of the OpenJPA PersistenceCapable contract and handles state
     * manager replacement during operations like entity detachment and reattachment. If a
     * state manager is already assigned, it delegates the replacement to the current manager.</p>
     *
     * @param pcStateManager StateManager the new state manager to assign
     * @throws SecurityException if the replacement violates security constraints
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
     * <p>This method is part of the OpenJPA PersistenceCapable contract. However, this
     * implementation always throws an InternalException as SystemProperties uses application
     * identity with a single ID field that is managed differently.</p>
     *
     * @param fieldSupplier FieldSupplier the field supplier to use for copying
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields to an object ID.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract. However, this
     * implementation always throws an InternalException as SystemProperties uses application
     * identity with a single ID field that is managed differently.</p>
     *
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields from an object ID using a field consumer.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and extracts the ID
     * value from an IntId object, storing it through the provided field consumer. This is used
     * during entity instantiation and identity management.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer to store the ID value
     * @param o Object the source object ID (must be an IntId instance)
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(0 + SystemProperties.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }

    /**
     * Copies key fields from an object ID directly to this entity instance.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and extracts the ID
     * value from an IntId object, assigning it directly to this entity's id field. This is used
     * during entity instantiation and identity management.</p>
     *
     * @param o Object the source object ID (must be an IntId instance)
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Integer.valueOf(((IntId)o).getId());
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and constructs a new
     * IntId object from a string representation of the ID value. This is used for identity
     * management and object ID parsing.</p>
     *
     * @param o Object the string representation of the ID value
     * @return Object a new IntId instance constructed from the string
     */
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties != null) ? SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties : (SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties = class$("ca.openosp.openo.caisi_integrator.dao.SystemProperties")), (String)o);
    }

    /**
     * Creates a new object ID instance from this entity's current ID field.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and constructs a new
     * IntId object using this entity's current id field value. This is used for identity
     * management and persistence operations.</p>
     *
     * @return Object a new IntId instance containing this entity's ID value
     */
    public Object pcNewObjectIdInstance() {
        return new IntId((SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties != null) ? SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties : (SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties = class$("ca.openosp.openo.caisi_integrator.dao.SystemProperties")), this.id);
    }

    /**
     * Static accessor for the id field with state manager tracking.
     *
     * <p>This is an OpenJPA-enhanced accessor that notifies the state manager when the id
     * field is accessed, enabling lazy loading and change tracking capabilities.</p>
     *
     * @param systemProperties SystemProperties the instance to get the id from
     * @return Integer the id value
     */
    private static final Integer pcGetid(final SystemProperties systemProperties) {
        if (systemProperties.pcStateManager == null) {
            return systemProperties.id;
        }
        systemProperties.pcStateManager.accessingField(SystemProperties.pcInheritedFieldCount + 0);
        return systemProperties.id;
    }

    /**
     * Static mutator for the id field with state manager tracking.
     *
     * <p>This is an OpenJPA-enhanced mutator that notifies the state manager when the id
     * field is modified, enabling change tracking and dirty field management.</p>
     *
     * @param systemProperties SystemProperties the instance to set the id on
     * @param id Integer the new id value
     */
    private static final void pcSetid(final SystemProperties systemProperties, final Integer id) {
        if (systemProperties.pcStateManager == null) {
            systemProperties.id = id;
            return;
        }
        systemProperties.pcStateManager.settingObjectField((PersistenceCapable)systemProperties, SystemProperties.pcInheritedFieldCount + 0, (Object)systemProperties.id, (Object)id, 0);
    }

    /**
     * Static accessor for the schemaVersion field with state manager tracking.
     *
     * <p>This is an OpenJPA-enhanced accessor that notifies the state manager when the
     * schemaVersion field is accessed, enabling lazy loading and change tracking capabilities.</p>
     *
     * @param systemProperties SystemProperties the instance to get the schema version from
     * @return int the schema version value
     */
    private static final int pcGetschemaVersion(final SystemProperties systemProperties) {
        if (systemProperties.pcStateManager == null) {
            return systemProperties.schemaVersion;
        }
        systemProperties.pcStateManager.accessingField(SystemProperties.pcInheritedFieldCount + 1);
        return systemProperties.schemaVersion;
    }

    /**
     * Static mutator for the schemaVersion field with state manager tracking.
     *
     * <p>This is an OpenJPA-enhanced mutator that notifies the state manager when the
     * schemaVersion field is modified, enabling change tracking and dirty field management.</p>
     *
     * @param systemProperties SystemProperties the instance to set the schema version on
     * @param schemaVersion int the new schema version value
     */
    private static final void pcSetschemaVersion(final SystemProperties systemProperties, final int schemaVersion) {
        if (systemProperties.pcStateManager == null) {
            systemProperties.schemaVersion = schemaVersion;
            return;
        }
        systemProperties.pcStateManager.settingIntField((PersistenceCapable)systemProperties, SystemProperties.pcInheritedFieldCount + 1, systemProperties.schemaVersion, schemaVersion, 0);
    }

    /**
     * Determines whether this entity instance is detached from the persistence context.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and checks the
     * detachment state of this entity. Returns true if detached, false if attached, or null
     * if the detachment state cannot be determined definitively.</p>
     *
     * @return Boolean true if detached, false if attached, null if indeterminate
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
     * Indicates whether the detached state can be determined definitively.
     *
     * <p>This method is used internally by {@link #pcIsDetached()} to determine if the
     * detachment status can be conclusively established based on available state information.</p>
     *
     * @return boolean always returns false, indicating detached state is not definitive
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Retrieves the detached state object for this entity instance.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and returns the
     * detached state marker which indicates whether the entity was deserialized or explicitly
     * detached from a persistence context.</p>
     *
     * @return Object the detached state marker, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object for this entity instance.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and updates the
     * detached state marker to indicate the detachment status of the entity.</p>
     *
     * @param pcDetachedState Object the detached state marker to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method for Java serialization.
     *
     * <p>This method handles serialization of the SystemProperties entity, preserving the
     * detached state during the serialization process. If the entity is being serialized by
     * the persistence framework, the detached state is cleared after writing.</p>
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
     * Custom deserialization method for Java serialization.
     *
     * <p>This method handles deserialization of the SystemProperties entity, marking it as
     * DESERIALIZED to indicate it was loaded through Java serialization rather than through
     * the persistence framework.</p>
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
