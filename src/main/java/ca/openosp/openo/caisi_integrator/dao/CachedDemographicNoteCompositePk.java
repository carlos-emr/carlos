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
 * Composite primary key for the CachedDemographicNote entity in the CAISI Integrator system.
 *
 * <p>This embeddable class represents a composite primary key that uniquely identifies cached
 * demographic notes across multiple integrated healthcare facilities. The key combines the
 * integrator facility identifier with a universally unique identifier (UUID) to ensure uniqueness
 * across distributed healthcare systems.</p>
 *
 * <p>The class is enhanced by OpenJPA for persistence management and implements both
 * {@link Serializable} for Java serialization and {@link PersistenceCapable} for JPA persistence
 * operations. OpenJPA byte-code enhancement provides automatic field-level change tracking and
 * lazy loading capabilities.</p>
 *
 * <p><strong>Healthcare Context:</strong> The CAISI Integrator facilitates data sharing between
 * multiple OpenO EMR installations. This composite key ensures that demographic notes from
 * different facilities can be cached and retrieved without key conflicts, supporting multi-site
 * healthcare delivery and patient record integration.</p>
 *
 * @see CachedDemographicNote
 * @since 2026-01-24
 */
@Embeddable
public class CachedDemographicNoteCompositePk implements Serializable, PersistenceCapable
{
    private static final long serialVersionUID = 1L;
    @Index
    private Integer integratorFacilityId;
    @Column(length = 50)
    @Index
    private String uuid;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNoteCompositePk;
    private transient Object pcDetachedState;

    /**
     * Default constructor that initializes a new composite primary key with null values.
     *
     * <p>This no-argument constructor is required by JPA for entity instantiation and
     * deserialization. It initializes both the integrator facility ID and UUID to null.</p>
     */
    public CachedDemographicNoteCompositePk() {
        this.integratorFacilityId = null;
        this.uuid = null;
    }

    /**
     * Parameterized constructor that creates a composite primary key with specified values.
     *
     * <p>This constructor is used to create a new composite key instance with both components
     * of the primary key populated. The facility ID identifies the specific healthcare facility
     * in the integrator network, while the UUID provides a unique identifier for the cached note.</p>
     *
     * @param integratorFacilityId Integer the unique identifier for the integrator facility
     * @param uuid String the universally unique identifier for the cached demographic note
     */
    public CachedDemographicNoteCompositePk(final Integer integratorFacilityId, final String uuid) {
        this.integratorFacilityId = null;
        this.uuid = null;
        this.integratorFacilityId = integratorFacilityId;
        this.uuid = uuid;
    }

    /**
     * Retrieves the integrator facility identifier component of this composite key.
     *
     * <p>The integrator facility ID identifies the specific healthcare facility within the
     * CAISI Integrator network from which this cached demographic note originated.</p>
     *
     * @return Integer the integrator facility identifier, or null if not set
     */
    public Integer getIntegratorFacilityId() {
        return pcGetintegratorFacilityId(this);
    }

    /**
     * Sets the integrator facility identifier component of this composite key.
     *
     * <p>This method updates the facility identifier portion of the composite key. The value
     * is managed through OpenJPA's persistence-capable field tracking mechanism.</p>
     *
     * @param integratorFacilityId Integer the integrator facility identifier to set
     */
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        pcSetintegratorFacilityId(this, integratorFacilityId);
    }

    /**
     * Retrieves the UUID component of this composite key.
     *
     * <p>The UUID provides a universally unique identifier for the cached demographic note,
     * ensuring uniqueness across all facilities in the integrator network.</p>
     *
     * @return String the UUID of the cached demographic note, or null if not set
     */
    public String getUuid() {
        return pcGetuuid(this);
    }

    /**
     * Sets the UUID component of this composite key.
     *
     * <p>This method updates the UUID portion of the composite key. The input value is
     * automatically trimmed and null values are preserved using Apache Commons StringUtils.
     * The value is managed through OpenJPA's persistence-capable field tracking mechanism.</p>
     *
     * @param uuid String the UUID to set, will be trimmed to null if empty or whitespace
     */
    public void setUuid(final String uuid) {
        pcSetuuid(this, StringUtils.trimToNull(uuid));
    }

    /**
     * Returns a string representation of this composite primary key.
     *
     * <p>The string format is "integratorFacilityId:uuid", providing a human-readable
     * representation of both components of the composite key separated by a colon.</p>
     *
     * @return String a colon-separated string representation of the facility ID and UUID
     */
    @Override
    public String toString() {
        return "" + pcGetintegratorFacilityId(this) + ':' + pcGetuuid(this);
    }

    /**
     * Generates a hash code for this composite primary key based on the UUID component.
     *
     * <p>The hash code is derived solely from the UUID field. This implementation assumes
     * that the UUID alone provides sufficient uniqueness for hash-based collections.</p>
     *
     * @return int the hash code value for this composite key
     */
    @Override
    public int hashCode() {
        return pcGetuuid(this).hashCode();
    }

    /**
     * Compares this composite primary key with another object for equality.
     *
     * <p>Two composite keys are considered equal if both the integrator facility ID and
     * UUID components are equal. The comparison uses the equals method of both Integer
     * and String classes. Any runtime exceptions during comparison (such as null pointer
     * or class cast exceptions) result in a false return value.</p>
     *
     * @param o Object the object to compare with this composite key
     * @return boolean true if both components are equal, false otherwise or on exception
     */
    @Override
    public boolean equals(final Object o) {
        try {
            final CachedDemographicNoteCompositePk o2 = (CachedDemographicNoteCompositePk)o;
            return pcGetintegratorFacilityId(this).equals(pcGetintegratorFacilityId(o2)) && pcGetuuid(this).equals(pcGetuuid(o2));
        }
        catch (final RuntimeException e) {
            return false;
        }
    }

    /**
     * Returns the OpenJPA byte-code enhancement contract version for this entity.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and indicates
     * the version of the byte-code enhancement specification this class implements.
     * Version 2 represents the current enhancement contract version.</p>
     *
     * @return int the enhancement contract version, currently 2
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        CachedDemographicNoteCompositePk.pcFieldNames = new String[] { "integratorFacilityId", "uuid" };
        CachedDemographicNoteCompositePk.pcFieldTypes = new Class[] { (CachedDemographicNoteCompositePk.class$Ljava$lang$Integer != null) ? CachedDemographicNoteCompositePk.class$Ljava$lang$Integer : (CachedDemographicNoteCompositePk.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedDemographicNoteCompositePk.class$Ljava$lang$String != null) ? CachedDemographicNoteCompositePk.class$Ljava$lang$String : (CachedDemographicNoteCompositePk.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedDemographicNoteCompositePk.pcFieldFlags = new byte[] { 26, 26 };
        PCRegistry.register((CachedDemographicNoteCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNoteCompositePk != null) ? CachedDemographicNoteCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNoteCompositePk : (CachedDemographicNoteCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNoteCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicNoteCompositePk")), CachedDemographicNoteCompositePk.pcFieldNames, CachedDemographicNoteCompositePk.pcFieldTypes, CachedDemographicNoteCompositePk.pcFieldFlags, CachedDemographicNoteCompositePk.pcPCSuperclass, (String)null, (PersistenceCapable)new CachedDemographicNoteCompositePk());
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
     * <p>This protected method is part of the OpenJPA persistence infrastructure and is
     * used to reset the entity state during persistence operations such as instance creation
     * or state transitions.</p>
     */
    protected void pcClearFields() {
        this.integratorFacilityId = null;
        this.uuid = null;
    }

    /**
     * Creates a new PersistenceCapable instance with the specified state manager and object ID.
     *
     * <p>This factory method is part of the OpenJPA PersistenceCapable contract and is used
     * by the persistence framework to create new instances during database operations. The
     * method creates a new instance, optionally clears its fields, assigns the state manager,
     * and copies key fields from the provided object ID.</p>
     *
     * @param pcStateManager StateManager the state manager to assign to the new instance
     * @param o Object the object ID from which to copy key field values
     * @param b boolean if true, clears all fields before copying key fields
     * @return PersistenceCapable a new instance configured with the provided parameters
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk = new CachedDemographicNoteCompositePk();
        if (b) {
            cachedDemographicNoteCompositePk.pcClearFields();
        }
        cachedDemographicNoteCompositePk.pcStateManager = pcStateManager;
        cachedDemographicNoteCompositePk.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicNoteCompositePk;
    }

    /**
     * Creates a new PersistenceCapable instance with the specified state manager.
     *
     * <p>This factory method is part of the OpenJPA PersistenceCapable contract and is used
     * by the persistence framework to create new instances. Unlike the three-parameter version,
     * this method does not initialize key fields from an object ID.</p>
     *
     * @param pcStateManager StateManager the state manager to assign to the new instance
     * @param b boolean if true, clears all fields after instance creation
     * @return PersistenceCapable a new instance configured with the provided state manager
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk = new CachedDemographicNoteCompositePk();
        if (b) {
            cachedDemographicNoteCompositePk.pcClearFields();
        }
        cachedDemographicNoteCompositePk.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicNoteCompositePk;
    }

    /**
     * Returns the number of managed fields in this entity class.
     *
     * <p>This static method is part of the OpenJPA persistence metadata and indicates
     * the total number of fields that are managed by the persistence framework. This
     * class has two managed fields: integratorFacilityId and uuid.</p>
     *
     * @return int the number of managed fields, currently 2
     */
    protected static int pcGetManagedFieldCount() {
        return 2;
    }

    /**
     * Replaces the value of a single managed field at the specified index.
     *
     * <p>This method is part of the OpenJPA field replacement infrastructure and is called
     * by the persistence framework to update field values during state management operations
     * such as refresh, merge, or detach. The field index is relative to inherited fields.</p>
     *
     * @param n int the field index to replace (0 for integratorFacilityId, 1 for uuid)
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicNoteCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.integratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.uuid = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces the values of multiple managed fields at the specified indices.
     *
     * <p>This method is part of the OpenJPA field replacement infrastructure and provides
     * batch field replacement by iterating through the provided array of field indices
     * and calling pcReplaceField for each one.</p>
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides the value of a single managed field to the state manager.
     *
     * <p>This method is part of the OpenJPA field access infrastructure and is called
     * by the persistence framework to read field values during operations such as flush,
     * detach, or serialization. The field value is passed to the state manager's
     * providedObjectField or providedStringField method.</p>
     *
     * @param n int the field index to provide (0 for integratorFacilityId, 1 for uuid)
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedDemographicNoteCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.integratorFacilityId);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.uuid);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Provides the values of multiple managed fields to the state manager.
     *
     * <p>This method is part of the OpenJPA field access infrastructure and provides
     * batch field provision by iterating through the provided array of field indices
     * and calling pcProvideField for each one.</p>
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }

    /**
     * Copies the value of a single field from another instance of this class.
     *
     * <p>This protected method is part of the OpenJPA field copy infrastructure and is
     * used during merge or refresh operations to transfer field values from one instance
     * to another. The field index is relative to inherited fields.</p>
     *
     * @param cachedDemographicNoteCompositePk CachedDemographicNoteCompositePk the source instance
     * @param n int the field index to copy (0 for integratorFacilityId, 1 for uuid)
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk, final int n) {
        final int n2 = n - CachedDemographicNoteCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.integratorFacilityId = cachedDemographicNoteCompositePk.integratorFacilityId;
                return;
            }
            case 1: {
                this.uuid = cachedDemographicNoteCompositePk.uuid;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies multiple field values from another instance of this class.
     *
     * <p>This method is part of the OpenJPA field copy infrastructure and provides batch
     * field copying by iterating through the provided array of field indices. Both instances
     * must share the same state manager, and a state manager must be present for the
     * operation to succeed.</p>
     *
     * @param o Object the source instance to copy from (must be CachedDemographicNoteCompositePk)
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source has a different state manager
     * @throws IllegalStateException if no state manager is present
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk = (CachedDemographicNoteCompositePk)o;
        if (cachedDemographicNoteCompositePk.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicNoteCompositePk, array[i]);
        }
    }

    /**
     * Returns the generic context object from the persistence state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and provides
     * access to the generic context associated with the state manager. Returns null
     * if no state manager is assigned.</p>
     *
     * @return Object the generic context from the state manager, or null if no state manager
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the object ID for this persistence-capable instance.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and retrieves
     * the unique object identifier assigned by the persistence framework. Returns null
     * if no state manager is assigned.</p>
     *
     * @return Object the object ID for this instance, or null if no state manager
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks whether this instance has been marked for deletion.
     *
     * <p>This method is part of the OpenJPA state checking infrastructure and returns
     * true if the instance is managed and has been marked for deletion in the current
     * transaction.</p>
     *
     * @return boolean true if marked for deletion, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks whether this instance has been modified since being loaded.
     *
     * <p>This method is part of the OpenJPA dirty checking infrastructure and returns
     * true if any managed fields have been modified. The method performs a dirty check
     * operation before querying the dirty state.</p>
     *
     * @return boolean true if the instance has been modified, false otherwise
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
     * Checks whether this instance is newly created and not yet persisted.
     *
     * <p>This method is part of the OpenJPA state checking infrastructure and returns
     * true if the instance is managed and represents a new object that has not yet
     * been saved to the database.</p>
     *
     * @return boolean true if newly created, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks whether this instance is managed by the persistence context.
     *
     * <p>This method is part of the OpenJPA state checking infrastructure and returns
     * true if the instance is currently managed by a persistence context and represents
     * a persistent entity in the database.</p>
     *
     * @return boolean true if persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks whether this instance is participating in a transaction.
     *
     * <p>This method is part of the OpenJPA state checking infrastructure and returns
     * true if the instance is managed and is participating in an active transaction.</p>
     *
     * @return boolean true if transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks whether this instance is currently being serialized.
     *
     * <p>This method is part of the OpenJPA serialization infrastructure and returns
     * true if the instance is currently undergoing serialization. This is used to
     * control special behavior during the serialization process.</p>
     *
     * @return boolean true if currently serializing, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks this instance as dirty, indicating that the specified field has been modified.
     *
     * <p>This method is part of the OpenJPA change tracking infrastructure and notifies
     * the state manager that a field has been modified, triggering dirty checking and
     * eventual persistence of the change.</p>
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
     * Returns the state manager currently assigned to this instance.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and provides
     * access to the StateManager that manages the persistence state of this instance.</p>
     *
     * @return StateManager the current state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Returns the version object for this managed instance.
     *
     * <p>This method is part of the OpenJPA versioning infrastructure and retrieves
     * the version identifier used for optimistic locking. Returns null if no state
     * manager is assigned.</p>
     *
     * @return Object the version object, or null if no state manager
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
     * <p>This method is part of the OpenJPA state manager lifecycle and allows the
     * persistence framework to reassign or update the state manager. If a state manager
     * is already present, it delegates to that manager's replaceStateManager method.</p>
     *
     * @param pcStateManager StateManager the new state manager to assign
     * @throws SecurityException if the replacement is not permitted by security policy
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
     * <p>This method is part of the OpenJPA object ID management infrastructure. For this
     * embeddable composite key class, the implementation is empty as the key fields are
     * managed directly within the object itself.</p>
     *
     * @param fieldSupplier FieldSupplier the field supplier providing field values
     * @param o Object the target object ID
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
    }

    /**
     * Copies key field values to an object ID.
     *
     * <p>This method is part of the OpenJPA object ID management infrastructure. For this
     * embeddable composite key class, the implementation is empty as the key fields are
     * managed directly within the object itself.</p>
     *
     * @param o Object the target object ID
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
    }

    /**
     * Copies key field values from an object ID using a field consumer.
     *
     * <p>This method is part of the OpenJPA object ID management infrastructure. For this
     * embeddable composite key class, the implementation is empty as the key fields are
     * managed directly within the object itself.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer receiving field values
     * @param o Object the source object ID
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
    }

    /**
     * Copies key field values from an object ID.
     *
     * <p>This method is part of the OpenJPA object ID management infrastructure. For this
     * embeddable composite key class, the implementation is empty as the key fields are
     * managed directly within the object itself.</p>
     *
     * @param o Object the source object ID
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
    }

    /**
     * Creates a new object ID instance for this entity type.
     *
     * <p>This method is part of the OpenJPA object ID infrastructure. For this embeddable
     * composite key class, it returns null as the composite key itself serves as the
     * object identifier.</p>
     *
     * @return Object always returns null for this embeddable key class
     */
    public Object pcNewObjectIdInstance() {
        return null;
    }

    /**
     * Creates a new object ID instance initialized from the provided object.
     *
     * <p>This method is part of the OpenJPA object ID infrastructure. For this embeddable
     * composite key class, it returns null as the composite key itself serves as the
     * object identifier.</p>
     *
     * @param o Object the source object for initializing the ID
     * @return Object always returns null for this embeddable key class
     */
    public Object pcNewObjectIdInstance(final Object o) {
        return null;
    }
    
    private static final Integer pcGetintegratorFacilityId(final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk) {
        if (cachedDemographicNoteCompositePk.pcStateManager == null) {
            return cachedDemographicNoteCompositePk.integratorFacilityId;
        }
        cachedDemographicNoteCompositePk.pcStateManager.accessingField(CachedDemographicNoteCompositePk.pcInheritedFieldCount + 0);
        return cachedDemographicNoteCompositePk.integratorFacilityId;
    }
    
    private static final void pcSetintegratorFacilityId(final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk, final Integer integratorFacilityId) {
        if (cachedDemographicNoteCompositePk.pcStateManager == null) {
            cachedDemographicNoteCompositePk.integratorFacilityId = integratorFacilityId;
            return;
        }
        cachedDemographicNoteCompositePk.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicNoteCompositePk, CachedDemographicNoteCompositePk.pcInheritedFieldCount + 0, (Object)cachedDemographicNoteCompositePk.integratorFacilityId, (Object)integratorFacilityId, 0);
    }
    
    private static final String pcGetuuid(final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk) {
        if (cachedDemographicNoteCompositePk.pcStateManager == null) {
            return cachedDemographicNoteCompositePk.uuid;
        }
        cachedDemographicNoteCompositePk.pcStateManager.accessingField(CachedDemographicNoteCompositePk.pcInheritedFieldCount + 1);
        return cachedDemographicNoteCompositePk.uuid;
    }
    
    private static final void pcSetuuid(final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk, final String uuid) {
        if (cachedDemographicNoteCompositePk.pcStateManager == null) {
            cachedDemographicNoteCompositePk.uuid = uuid;
            return;
        }
        cachedDemographicNoteCompositePk.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicNoteCompositePk, CachedDemographicNoteCompositePk.pcInheritedFieldCount + 1, cachedDemographicNoteCompositePk.uuid, uuid, 0);
    }

    /**
     * Checks whether this instance is in a detached state.
     *
     * <p>This method is part of the OpenJPA detachment infrastructure and determines if
     * the instance has been detached from its persistence context. The method returns:</p>
     * <ul>
     * <li>Boolean.TRUE if definitely detached</li>
     * <li>Boolean.FALSE if definitely not detached</li>
     * <li>null if the detachment state cannot be determined definitively</li>
     * </ul>
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
     * Determines if the detached state can be definitively determined.
     *
     * <p>This private method is part of the OpenJPA detachment infrastructure and is
     * used internally by pcIsDetached() to determine if the detachment status can be
     * conclusively determined from available information.</p>
     *
     * @return boolean always returns false, indicating the state is not definitive
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Returns the current detached state object for this instance.
     *
     * <p>This method is part of the OpenJPA detachment infrastructure and retrieves
     * the detached state marker. The value may be null (not detached), DESERIALIZED
     * (recently deserialized), or another object indicating detached status.</p>
     *
     * @return Object the detached state object, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state for this instance.
     *
     * <p>This method is part of the OpenJPA detachment infrastructure and is used
     * by the persistence framework to mark the instance's detachment status during
     * detach operations or deserialization.</p>
     *
     * @param pcDetachedState Object the detached state to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method that handles persistence state during serialization.
     *
     * <p>This private method is part of Java's serialization infrastructure and is called
     * automatically during object serialization. If the instance is being serialized by
     * the persistence framework, it clears the detached state after writing the object.</p>
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
     * Custom deserialization method that handles persistence state during deserialization.
     *
     * <p>This private method is part of Java's serialization infrastructure and is called
     * automatically during object deserialization. It marks the instance as DESERIALIZED
     * before reading the object state, indicating that the instance was created through
     * deserialization rather than normal persistence operations.</p>
     *
     * @param objectInputStream ObjectInputStream the stream to read the object from
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if a required class cannot be found during deserialization
     */
    private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        this.pcSetDetachedState(PersistenceCapable.DESERIALIZED);
        objectInputStream.defaultReadObject();
    }
}
