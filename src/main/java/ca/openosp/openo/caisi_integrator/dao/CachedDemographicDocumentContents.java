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
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * JPA entity representing cached demographic document binary contents for the CAISI Integrator system.
 *
 * This class stores the binary file contents of demographic documents cached from integrated facilities
 * in the OpenO EMR CAISI Integrator. The integrator allows multiple OpenO EMR installations to share
 * patient demographic data across healthcare facilities. This entity caches document files (such as
 * scanned identification, consent forms, or demographic attachments) to improve performance and reduce
 * network load when accessing documents from remote facilities.
 *
 * The class uses OpenJPA persistence enhancement to provide transparent persistence capabilities,
 * including state management, field access tracking, and detached entity support. The document contents
 * are stored as a LONGBLOB in the database to accommodate large file sizes.
 *
 * This is an OpenJPA-enhanced entity with bytecode instrumentation that adds persistence capability
 * methods prefixed with "pc" for state management, field tracking, and serialization support.
 *
 * @see FacilityIdIntegerCompositePk
 * @see AbstractModel
 * @see PersistenceCapable
 * @since 2024-01-24
 */
@Entity
public class CachedDemographicDocumentContents extends AbstractModel<FacilityIdIntegerCompositePk> implements PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityIntegerCompositePk;
    @Column(columnDefinition = "longblob")
    private byte[] fileContents;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$L$B;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocumentContents;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    /**
     * Retrieves the composite primary key identifier for this cached document.
     *
     * The identifier consists of the integrator facility ID and the CAISI item ID,
     * uniquely identifying a cached demographic document across the integrator system.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key containing facility and item identifiers
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityIntegerCompositePk(this);
    }
    
    /**
     * Retrieves the facility and item composite primary key for this cached document.
     *
     * This method provides access to the composite key that combines the integrator facility ID
     * (identifying the healthcare facility) and the CAISI item ID (identifying the specific
     * document within that facility's system).
     *
     * @return FacilityIdIntegerCompositePk the composite primary key, or null if not yet persisted
     */
    public FacilityIdIntegerCompositePk getFacilityIntegerCompositePk() {
        return pcGetfacilityIntegerCompositePk(this);
    }
    
    /**
     * Sets the facility and item composite primary key for this cached document.
     *
     * This method assigns the composite key that uniquely identifies this document cache entry
     * by facility and item identifiers. Should be called when creating a new cache entry.
     *
     * @param facilityIntegerCompositePk FacilityIdIntegerCompositePk the composite primary key to assign
     */
    public void setFacilityIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIntegerCompositePk) {
        pcSetfacilityIntegerCompositePk(this, facilityIntegerCompositePk);
    }
    
    /**
     * Retrieves the binary file contents of the cached demographic document.
     *
     * This method returns the raw byte array containing the complete file contents of the cached
     * demographic document. The contents may be any file type (PDF, image, etc.) and can be large,
     * as they are stored in a LONGBLOB database column.
     *
     * @return byte[] the binary file contents, or null if no contents have been cached
     */
    public byte[] getFileContents() {
        return pcGetfileContents(this);
    }
    
    /**
     * Sets the binary file contents of the demographic document to be cached.
     *
     * This method stores the raw byte array containing the complete file contents of a demographic
     * document retrieved from an integrated facility. The file contents are persisted as a LONGBLOB
     * in the database to support large file sizes.
     *
     * @param fileContents byte[] the binary file contents to cache
     */
    public void setFileContents(final byte[] fileContents) {
        pcSetfileContents(this, fileContents);
    }
    
    /**
     * Returns the OpenJPA enhancement contract version for this persistence-capable class.
     *
     * This method is part of the OpenJPA bytecode enhancement framework and indicates which
     * version of the enhancement contract this class implements. Version 2 is the current
     * enhancement contract version.
     *
     * @return int the enhancement contract version number (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 2801972328978058955L;
        CachedDemographicDocumentContents.pcFieldNames = new String[] { "facilityIntegerCompositePk", "fileContents" };
        CachedDemographicDocumentContents.pcFieldTypes = new Class[] { (CachedDemographicDocumentContents.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedDemographicDocumentContents.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedDemographicDocumentContents.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedDemographicDocumentContents.class$L$B != null) ? CachedDemographicDocumentContents.class$L$B : (CachedDemographicDocumentContents.class$L$B = class$("[B")) };
        CachedDemographicDocumentContents.pcFieldFlags = new byte[] { 26, 26 };
        PCRegistry.register((CachedDemographicDocumentContents.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocumentContents != null) ? CachedDemographicDocumentContents.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocumentContents : (CachedDemographicDocumentContents.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocumentContents = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicDocumentContents")), CachedDemographicDocumentContents.pcFieldNames, CachedDemographicDocumentContents.pcFieldTypes, CachedDemographicDocumentContents.pcFieldFlags, CachedDemographicDocumentContents.pcPCSuperclass, "CachedDemographicDocumentContents", (PersistenceCapable)new CachedDemographicDocumentContents());
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
        this.facilityIntegerCompositePk = null;
        this.fileContents = null;
    }
    
    /**
     * Creates a new persistence-capable instance with the specified state manager and object ID.
     *
     * This method is called by OpenJPA to create a new instance for loading from the database.
     * The new instance is initialized with the provided state manager, and its primary key fields
     * are populated from the object ID. Fields are optionally cleared if requested.
     *
     * @param pcStateManager StateManager the state manager to assign to the new instance
     * @param o Object the object ID containing the primary key values
     * @param b boolean whether to clear all fields after creation
     * @return PersistenceCapable the newly created instance with state manager and key fields assigned
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicDocumentContents cachedDemographicDocumentContents = new CachedDemographicDocumentContents();
        if (b) {
            cachedDemographicDocumentContents.pcClearFields();
        }
        cachedDemographicDocumentContents.pcStateManager = pcStateManager;
        cachedDemographicDocumentContents.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicDocumentContents;
    }
    
    /**
     * Creates a new persistence-capable instance with the specified state manager.
     *
     * This method is called by OpenJPA to create a new instance without initializing primary
     * key fields. The new instance is assigned the provided state manager, and its fields are
     * optionally cleared if requested.
     *
     * @param pcStateManager StateManager the state manager to assign to the new instance
     * @param b boolean whether to clear all fields after creation
     * @return PersistenceCapable the newly created instance with state manager assigned
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicDocumentContents cachedDemographicDocumentContents = new CachedDemographicDocumentContents();
        if (b) {
            cachedDemographicDocumentContents.pcClearFields();
        }
        cachedDemographicDocumentContents.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicDocumentContents;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 2;
    }
    
    /**
     * Replaces a managed field value using the OpenJPA state manager.
     *
     * This method is called by OpenJPA to replace the value of a specific managed field during
     * persistence operations. The field index is adjusted for inheritance and validated before
     * delegating to the state manager.
     *
     * @param n int the absolute field index to replace
     * @throws IllegalArgumentException if the field index is invalid or out of range
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicDocumentContents.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityIntegerCompositePk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.fileContents = (byte[])this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Replaces multiple managed field values using the OpenJPA state manager.
     *
     * This method iterates through an array of field indices and delegates to pcReplaceField
     * for each field to be replaced during persistence operations.
     *
     * @param array int[] array of absolute field indices to replace
     * @throws IllegalArgumentException if any field index is invalid or out of range
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }
    
    /**
     * Provides a managed field value to the OpenJPA state manager.
     *
     * This method is called by OpenJPA to retrieve the current value of a specific managed field
     * for persistence operations such as flushing to the database. The field index is adjusted
     * for inheritance and validated before providing the value to the state manager.
     *
     * @param n int the absolute field index to provide
     * @throws IllegalArgumentException if the field index is invalid or out of range
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedDemographicDocumentContents.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityIntegerCompositePk);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.fileContents);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Provides multiple managed field values to the OpenJPA state manager.
     *
     * This method iterates through an array of field indices and delegates to pcProvideField
     * for each field to provide its current value during persistence operations.
     *
     * @param array int[] array of absolute field indices to provide
     * @throws IllegalArgumentException if any field index is invalid or out of range
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    protected void pcCopyField(final CachedDemographicDocumentContents cachedDemographicDocumentContents, final int n) {
        final int n2 = n - CachedDemographicDocumentContents.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityIntegerCompositePk = cachedDemographicDocumentContents.facilityIntegerCompositePk;
                return;
            }
            case 1: {
                this.fileContents = cachedDemographicDocumentContents.fileContents;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Copies specified field values from another instance to this instance.
     *
     * This method copies the values of specified fields from a source instance to this instance.
     * Both instances must be managed by the same state manager and the state manager must not be null.
     *
     * @param o Object the source instance to copy field values from (must be CachedDemographicDocumentContents)
     * @param array int[] array of absolute field indices to copy
     * @throws IllegalArgumentException if the source object has a different state manager
     * @throws IllegalStateException if the state manager is null
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicDocumentContents cachedDemographicDocumentContents = (CachedDemographicDocumentContents)o;
        if (cachedDemographicDocumentContents.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicDocumentContents, array[i]);
        }
    }
    
    /**
     * Retrieves the generic context object from the OpenJPA state manager.
     *
     * The generic context provides access to additional context information maintained by OpenJPA
     * for this persistence-capable instance during persistence operations.
     *
     * @return Object the generic context from the state manager, or null if no state manager is assigned
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }
    
    /**
     * Fetches the JPA object identifier for this persistence-capable instance.
     *
     * This method retrieves the unique object ID assigned by OpenJPA to identify this entity
     * within the persistence context and database.
     *
     * @return Object the JPA object identifier, or null if no state manager is assigned
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
     * This method queries the OpenJPA state manager to determine if this entity has been
     * deleted within the current transaction but not yet flushed to the database.
     *
     * @return boolean true if the entity is marked for deletion, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }
    
    /**
     * Checks whether this entity has been modified and requires database synchronization.
     *
     * This method queries the OpenJPA state manager to determine if any fields have been
     * modified since the entity was loaded or last synchronized with the database. A dirty
     * check is performed before querying the state.
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
     * Checks whether this entity is newly created and not yet persisted to the database.
     *
     * This method queries the OpenJPA state manager to determine if this entity has been
     * created in the current transaction but not yet flushed to the database.
     *
     * @return boolean true if the entity is new and unpersisted, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }
    
    /**
     * Checks whether this entity is in a persistent state.
     *
     * This method queries the OpenJPA state manager to determine if this entity is managed
     * by a persistence context and associated with the database. A persistent entity may be
     * new, modified, or clean.
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }
    
    /**
     * Checks whether this entity is participating in a transaction.
     *
     * This method queries the OpenJPA state manager to determine if this entity is currently
     * part of an active transaction and will be synchronized with the database upon commit.
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }
    
    /**
     * Checks whether this entity is currently being serialized.
     *
     * This method queries the OpenJPA state manager to determine if the entity is in the
     * process of serialization, which requires special handling of the detached state.
     *
     * @return boolean true if the entity is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }
    
    /**
     * Marks a specific field as modified (dirty) for database synchronization.
     *
     * This method notifies the OpenJPA state manager that a particular field has been modified
     * and requires synchronization with the database. If no state manager is assigned, this
     * operation is silently ignored.
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
     * Retrieves the OpenJPA state manager for this persistence-capable instance.
     *
     * The state manager is responsible for tracking field modifications, managing the entity's
     * lifecycle, and coordinating persistence operations with the database.
     *
     * @return StateManager the OpenJPA state manager, or null if not assigned
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }
    
    /**
     * Retrieves the version identifier for optimistic locking.
     *
     * This method returns the version object used by OpenJPA for optimistic concurrency control.
     * The version is incremented on each update to detect concurrent modifications.
     *
     * @return Object the version identifier, or null if no state manager is assigned
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }
    
    /**
     * Replaces the current OpenJPA state manager with a new one.
     *
     * This method updates the state manager for this persistence-capable instance. If a state
     * manager is already assigned, it delegates the replacement to the existing state manager
     * to ensure proper state transition. Otherwise, the new state manager is directly assigned.
     *
     * @param pcStateManager StateManager the new state manager to assign
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
     * Copies primary key fields to an object ID using a field supplier.
     *
     * This method is not supported for this entity type as it uses an embedded composite key.
     *
     * @param fieldSupplier FieldSupplier the field supplier to provide field values
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }
    
    /**
     * Copies primary key fields to an object ID.
     *
     * This method is not supported for this entity type as it uses an embedded composite key.
     *
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }
    
    /**
     * Copies primary key fields from an object ID using a field consumer.
     *
     * This method extracts the composite key from an OpenJPA ObjectId and stores it in the
     * entity using the provided field consumer.
     *
     * @param fieldConsumer FieldConsumer the field consumer to store field values
     * @param o Object the source object ID containing the composite key
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(0 + CachedDemographicDocumentContents.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    /**
     * Copies primary key fields from an object ID directly to this entity.
     *
     * This method extracts the embedded composite key from an OpenJPA ObjectId and assigns
     * it directly to the facilityIntegerCompositePk field.
     *
     * @param o Object the source object ID containing the composite key
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityIntegerCompositePk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    /**
     * Creates a new object ID instance from a string representation.
     *
     * This method is not supported for this entity type because ObjectId does not provide
     * the required constructors for string-based initialization.
     *
     * @param o Object the string representation of the object ID
     * @return Object never returns
     * @throws IllegalArgumentException always thrown as this operation is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicDocumentContents\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    /**
     * Creates a new OpenJPA object ID instance for this entity.
     *
     * This method constructs an ObjectId containing this entity's class type and the current
     * composite primary key value. The object ID uniquely identifies this entity within the
     * persistence context.
     *
     * @return Object a new OpenJPA ObjectId containing the entity class and composite key
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicDocumentContents.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocumentContents != null) ? CachedDemographicDocumentContents.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocumentContents : (CachedDemographicDocumentContents.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocumentContents = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicDocumentContents")), (Object)this.facilityIntegerCompositePk);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityIntegerCompositePk(final CachedDemographicDocumentContents cachedDemographicDocumentContents) {
        if (cachedDemographicDocumentContents.pcStateManager == null) {
            return cachedDemographicDocumentContents.facilityIntegerCompositePk;
        }
        cachedDemographicDocumentContents.pcStateManager.accessingField(CachedDemographicDocumentContents.pcInheritedFieldCount + 0);
        return cachedDemographicDocumentContents.facilityIntegerCompositePk;
    }
    
    private static final void pcSetfacilityIntegerCompositePk(final CachedDemographicDocumentContents cachedDemographicDocumentContents, final FacilityIdIntegerCompositePk facilityIntegerCompositePk) {
        if (cachedDemographicDocumentContents.pcStateManager == null) {
            cachedDemographicDocumentContents.facilityIntegerCompositePk = facilityIntegerCompositePk;
            return;
        }
        cachedDemographicDocumentContents.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDocumentContents, CachedDemographicDocumentContents.pcInheritedFieldCount + 0, (Object)cachedDemographicDocumentContents.facilityIntegerCompositePk, (Object)facilityIntegerCompositePk, 0);
    }
    
    private static final byte[] pcGetfileContents(final CachedDemographicDocumentContents cachedDemographicDocumentContents) {
        if (cachedDemographicDocumentContents.pcStateManager == null) {
            return cachedDemographicDocumentContents.fileContents;
        }
        cachedDemographicDocumentContents.pcStateManager.accessingField(CachedDemographicDocumentContents.pcInheritedFieldCount + 1);
        return cachedDemographicDocumentContents.fileContents;
    }
    
    private static final void pcSetfileContents(final CachedDemographicDocumentContents cachedDemographicDocumentContents, final byte[] fileContents) {
        if (cachedDemographicDocumentContents.pcStateManager == null) {
            cachedDemographicDocumentContents.fileContents = fileContents;
            return;
        }
        cachedDemographicDocumentContents.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDocumentContents, CachedDemographicDocumentContents.pcInheritedFieldCount + 1, (Object)cachedDemographicDocumentContents.fileContents, (Object)fileContents, 0);
    }
    
    /**
     * Determines the detached state of this entity.
     *
     * This method checks whether the entity is detached from the persistence context. If a state
     * manager is assigned, it queries the state manager. Otherwise, it examines the detached state
     * field to make the determination. Returns null if the detached state cannot be definitively
     * determined.
     *
     * @return Boolean TRUE if detached, FALSE if not detached, null if state is indeterminate
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
     * Retrieves the detached state object maintained by OpenJPA.
     *
     * The detached state object contains information about the entity's state when it was
     * detached from the persistence context, used for merging and reattachment operations.
     *
     * @return Object the detached state object, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }
    
    /**
     * Sets the detached state object for this entity.
     *
     * This method assigns the detached state information used by OpenJPA to track the entity's
     * state when detached from the persistence context. Called during detachment and
     * deserialization operations.
     *
     * @param pcDetachedState Object the detached state object to assign
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
