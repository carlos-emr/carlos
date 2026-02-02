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
import java.util.GregorianCalendar;
import org.apache.openjpa.enhance.StateManager;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Calendar;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * JPA entity representing the last push date for demographic data synchronization
 * in the CAISI integrator system.
 *
 * <p>This entity tracks when demographic information was last pushed to external
 * healthcare facilities or systems. It uses a composite primary key consisting of
 * a facility identifier and an integer ID to uniquely identify push date records
 * across multiple facilities.</p>
 *
 * <p>The class is enhanced by OpenJPA for persistence management and implements
 * the PersistenceCapable interface to support JPA bytecode enhancement features
 * including field tracking, state management, and optimized database operations.</p>
 *
 * <p><strong>Healthcare Context:</strong> In healthcare interoperability scenarios,
 * tracking when patient demographic data was last synchronized between systems is
 * critical for maintaining data consistency and avoiding duplicate updates. This
 * entity supports the CAISI (Client Access to Integrated Services and Information)
 * integrator functionality.</p>
 *
 * @see AbstractModel
 * @see FacilityIdIntegerCompositePk
 * @see org.apache.openjpa.enhance.PersistenceCapable
 * @since 2026-01-24
 */
@Entity
public class DemographicPushDate extends AbstractModel<FacilityIdIntegerCompositePk> implements PersistenceCapable
{
    /**
     * Composite primary key containing facility ID and integer identifier.
     * This embedded ID uniquely identifies demographic push date records across facilities.
     */
    @EmbeddedId
    private FacilityIdIntegerCompositePk id;

    /**
     * Timestamp of the last demographic data push operation.
     * This field is indexed for efficient querying and cannot be null.
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    @Index
    private Calendar lastPushDate;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Ljava$util$Calendar;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$DemographicPushDate;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor that initializes a new DemographicPushDate instance.
     * Sets the last push date to the current timestamp using GregorianCalendar.
     * The ID is initialized to null and should be set before persisting.
     */
    public DemographicPushDate() {
        this.id = null;
        this.lastPushDate = new GregorianCalendar();
    }
    
    /**
     * Retrieves the composite primary key for this demographic push date record.
     * This method is enhanced by OpenJPA to track field access for persistence management.
     *
     * @return FacilityIdIntegerCompositePk the composite key containing facility ID and integer identifier
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetid(this);
    }

    /**
     * Retrieves the timestamp of the last demographic data push operation.
     * This method is enhanced by OpenJPA to track field access for persistence management.
     *
     * @return Calendar the timestamp when demographic data was last pushed
     */
    public Calendar getLastPushDate() {
        return pcGetlastPushDate(this);
    }

    /**
     * Sets the timestamp of the last demographic data push operation.
     * This method is enhanced by OpenJPA to track field modifications for persistence management.
     *
     * @param lastPushDate Calendar the new timestamp for the last push operation
     */
    public void setLastPushDate(final Calendar lastPushDate) {
        pcSetlastPushDate(this, lastPushDate);
    }

    /**
     * Sets the composite primary key for this demographic push date record.
     * This method is enhanced by OpenJPA to track field modifications for persistence management.
     *
     * @param id FacilityIdIntegerCompositePk the composite key to set
     */
    public void setId(final FacilityIdIntegerCompositePk id) {
        pcSetid(this, id);
    }
    
    /**
     * Returns the OpenJPA enhancement contract version for this entity.
     * Used by OpenJPA to ensure compatibility between enhanced bytecode and runtime library.
     *
     * @return int the enhancement contract version (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 6913977537728838681L;
        DemographicPushDate.pcFieldNames = new String[] { "id", "lastPushDate" };
        DemographicPushDate.pcFieldTypes = new Class[] { (DemographicPushDate.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? DemographicPushDate.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (DemographicPushDate.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (DemographicPushDate.class$Ljava$util$Calendar != null) ? DemographicPushDate.class$Ljava$util$Calendar : (DemographicPushDate.class$Ljava$util$Calendar = class$("java.util.Calendar")) };
        DemographicPushDate.pcFieldFlags = new byte[] { 26, 26 };
        PCRegistry.register((DemographicPushDate.class$Lca$openosp$openo$caisi_integrator$dao$DemographicPushDate != null) ? DemographicPushDate.class$Lca$openosp$openo$caisi_integrator$dao$DemographicPushDate : (DemographicPushDate.class$Lca$openosp$openo$caisi_integrator$dao$DemographicPushDate = class$("ca.openosp.openo.caisi_integrator.dao.DemographicPushDate")), DemographicPushDate.pcFieldNames, DemographicPushDate.pcFieldTypes, DemographicPushDate.pcFieldFlags, DemographicPushDate.pcPCSuperclass, "DemographicPushDate", (PersistenceCapable)new DemographicPushDate());
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
     * Clears all persistent fields in this entity by setting them to null.
     * This method is used internally by OpenJPA during instance management.
     */
    protected void pcClearFields() {
        this.id = null;
        this.lastPushDate = null;
    }
    
    /**
     * Creates a new instance of DemographicPushDate with the specified state manager and object ID.
     * This method is used by OpenJPA during entity instantiation from database results.
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object ID containing key field values
     * @param b boolean if true, clears all fields before copying key fields
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final DemographicPushDate demographicPushDate = new DemographicPushDate();
        if (b) {
            demographicPushDate.pcClearFields();
        }
        demographicPushDate.pcStateManager = pcStateManager;
        demographicPushDate.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)demographicPushDate;
    }
    
    /**
     * Creates a new instance of DemographicPushDate with the specified state manager.
     * This method is used by OpenJPA during entity instantiation.
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean if true, clears all fields after instantiation
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final DemographicPushDate demographicPushDate = new DemographicPushDate();
        if (b) {
            demographicPushDate.pcClearFields();
        }
        demographicPushDate.pcStateManager = pcStateManager;
        return (PersistenceCapable)demographicPushDate;
    }
    
    /**
     * Returns the count of managed persistent fields in this entity.
     * DemographicPushDate has 2 managed fields: id and lastPushDate.
     *
     * @return int the number of managed fields (2)
     */
    protected static int pcGetManagedFieldCount() {
        return 2;
    }
    
    /**
     * Replaces a single persistent field value using the state manager.
     * This method is called by OpenJPA during instance state management.
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - DemographicPushDate.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.id = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.lastPushDate = (Calendar)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Replaces multiple persistent field values using the state manager.
     * This method iterates through the provided field indices and calls pcReplaceField for each.
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }
    
    /**
     * Provides a single persistent field value to the state manager.
     * This method is called by OpenJPA to retrieve field values during persistence operations.
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - DemographicPushDate.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.id);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.lastPushDate);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Provides multiple persistent field values to the state manager.
     * This method iterates through the provided field indices and calls pcProvideField for each.
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    /**
     * Copies a single persistent field value from another DemographicPushDate instance.
     * This method is used internally by OpenJPA during instance cloning and merging operations.
     *
     * @param demographicPushDate DemographicPushDate the source instance to copy from
     * @param n int the field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final DemographicPushDate demographicPushDate, final int n) {
        final int n2 = n - DemographicPushDate.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.id = demographicPushDate.id;
                return;
            }
            case 1: {
                this.lastPushDate = demographicPushDate.lastPushDate;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Copies multiple persistent field values from another instance.
     * This method validates that both instances share the same state manager before copying.
     *
     * @param o Object the source instance to copy from (must be DemographicPushDate)
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source instance has a different state manager
     * @throws IllegalStateException if the state manager is null
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final DemographicPushDate demographicPushDate = (DemographicPushDate)o;
        if (demographicPushDate.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(demographicPushDate, array[i]);
        }
    }
    
    /**
     * Retrieves the generic context object from the state manager.
     * This context can be used to pass information between OpenJPA components.
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
     * Fetches the object ID for this persistent instance.
     * The object ID uniquely identifies this entity in the persistence context.
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
     * Checks whether this entity has been marked for deletion in the persistence context.
     *
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }
    
    /**
     * Checks whether this entity has been modified since it was loaded from the database.
     * Performs a dirty check to ensure the state is up-to-date.
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
     * Checks whether this entity is new and has not yet been persisted to the database.
     *
     * @return boolean true if the entity is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }
    
    /**
     * Checks whether this entity is persistent (managed by the persistence context).
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }
    
    /**
     * Checks whether this entity is participating in a transaction.
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }
    
    /**
     * Checks whether this entity is currently being serialized.
     *
     * @return boolean true if the entity is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }
    
    /**
     * Marks a specific field as dirty (modified) in the persistence context.
     * This notifies the state manager that the field has been changed and needs to be updated.
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
     * Retrieves the OpenJPA state manager associated with this entity.
     * The state manager tracks the entity's lifecycle and manages persistence operations.
     *
     * @return StateManager the state manager for this entity
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }
    
    /**
     * Retrieves the version object for this entity.
     * The version is used for optimistic locking to prevent concurrent modification conflicts.
     *
     * @return Object the version object, or null if no state manager is present
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }
    
    /**
     * Replaces the current state manager with a new one.
     * If a state manager already exists, it delegates the replacement to the existing manager.
     *
     * @param pcStateManager StateManager the new state manager to set
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
     * This method is not supported for this entity type.
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
     * This method is not supported for this entity type.
     *
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }
    
    /**
     * Copies key field values from an object ID to a field consumer.
     * This method extracts the ID from the object ID and stores it in the field consumer.
     *
     * @param fieldConsumer FieldConsumer the field consumer to receive the key field values
     * @param o Object the source object ID
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(0 + DemographicPushDate.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    /**
     * Copies key field values from an object ID to this entity's ID field.
     * Extracts the composite key from the object ID and assigns it to this entity.
     *
     * @param o Object the source object ID containing the composite key
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    /**
     * Creates a new object ID instance from a string representation.
     * This method is not supported for this entity type.
     *
     * @param o Object the string representation of the object ID
     * @return Object the new object ID instance
     * @throws IllegalArgumentException always thrown as this operation is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.DemographicPushDate\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    /**
     * Creates a new object ID instance for this entity using its current ID value.
     * The object ID combines the entity class type and the composite key.
     *
     * @return Object the new object ID instance
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((DemographicPushDate.class$Lca$openosp$openo$caisi_integrator$dao$DemographicPushDate != null) ? DemographicPushDate.class$Lca$openosp$openo$caisi_integrator$dao$DemographicPushDate : (DemographicPushDate.class$Lca$openosp$openo$caisi_integrator$dao$DemographicPushDate = class$("ca.openosp.openo.caisi_integrator.dao.DemographicPushDate")), (Object)this.id);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetid(final DemographicPushDate demographicPushDate) {
        if (demographicPushDate.pcStateManager == null) {
            return demographicPushDate.id;
        }
        demographicPushDate.pcStateManager.accessingField(DemographicPushDate.pcInheritedFieldCount + 0);
        return demographicPushDate.id;
    }
    
    private static final void pcSetid(final DemographicPushDate demographicPushDate, final FacilityIdIntegerCompositePk id) {
        if (demographicPushDate.pcStateManager == null) {
            demographicPushDate.id = id;
            return;
        }
        demographicPushDate.pcStateManager.settingObjectField((PersistenceCapable)demographicPushDate, DemographicPushDate.pcInheritedFieldCount + 0, (Object)demographicPushDate.id, (Object)id, 0);
    }
    
    private static final Calendar pcGetlastPushDate(final DemographicPushDate demographicPushDate) {
        if (demographicPushDate.pcStateManager == null) {
            return demographicPushDate.lastPushDate;
        }
        demographicPushDate.pcStateManager.accessingField(DemographicPushDate.pcInheritedFieldCount + 1);
        return demographicPushDate.lastPushDate;
    }
    
    private static final void pcSetlastPushDate(final DemographicPushDate demographicPushDate, final Calendar lastPushDate) {
        if (demographicPushDate.pcStateManager == null) {
            demographicPushDate.lastPushDate = lastPushDate;
            return;
        }
        demographicPushDate.pcStateManager.settingObjectField((PersistenceCapable)demographicPushDate, DemographicPushDate.pcInheritedFieldCount + 1, (Object)demographicPushDate.lastPushDate, (Object)lastPushDate, 0);
    }
    
    /**
     * Determines whether this entity is in a detached state.
     * A detached entity is no longer managed by the persistence context but retains its data.
     * Returns null if the detached state cannot be definitively determined.
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
    
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }
    
    /**
     * Retrieves the detached state object for this entity.
     * The detached state is used to track entity state when not managed by a persistence context.
     *
     * @return Object the detached state object
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }
    
    /**
     * Sets the detached state object for this entity.
     * This is used internally by OpenJPA to manage entity state during detachment and serialization.
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
