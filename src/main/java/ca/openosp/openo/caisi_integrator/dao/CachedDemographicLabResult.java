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
 * Cached laboratory result entity for the CAISI Integrator system.
 *
 * This entity represents cached laboratory results associated with patients (demographics) in the
 * OpenO EMR CAISI Integrator module. The CAISI Integrator enables data sharing across multiple
 * healthcare facilities, and this cache improves performance by storing frequently accessed lab
 * results locally instead of repeatedly querying remote facilities.
 *
 * The cache stores lab result data along with metadata including the patient's local demographic ID,
 * the facility identifier, the lab result identifier, and the result type. The actual lab result
 * data is stored as a medium blob to accommodate various lab result formats (HL7 messages, XML, etc.).
 *
 * This class is enhanced by Apache OpenJPA for persistence management, implementing the
 * PersistenceCapable interface which provides field-level access tracking, state management,
 * and detachment capabilities for distributed healthcare environments.
 *
 * @see FacilityIdLabResultCompositePk
 * @see AbstractModel
 * @since 2026-01-24
 */
@Entity
public class CachedDemographicLabResult extends AbstractModel<FacilityIdLabResultCompositePk> implements PersistenceCapable
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
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicLabResult;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    /**
     * Default constructor for CachedDemographicLabResult.
     *
     * Initializes a new instance with default values. The caisiDemographicId is set to 0.
     * This constructor is required by JPA for entity instantiation.
     */
    public CachedDemographicLabResult() {
        this.caisiDemographicId = 0;
    }
    
    /**
     * Gets the composite primary key for this cached lab result.
     *
     * @return FacilityIdLabResultCompositePk the composite primary key containing facility ID and lab result ID
     */
    @Override
    public FacilityIdLabResultCompositePk getId() {
        return pcGetfacilityIdLabResultCompositePk(this);
    }
    
    /**
     * Gets the composite primary key identifying this cached lab result across facilities.
     *
     * @return FacilityIdLabResultCompositePk the composite key containing the integrator facility ID and lab result ID
     */
    public FacilityIdLabResultCompositePk getFacilityIdLabResultCompositePk() {
        return pcGetfacilityIdLabResultCompositePk(this);
    }
    
    /**
     * Sets the composite primary key for this cached lab result.
     *
     * @param facilityIdLabResultCompositePk FacilityIdLabResultCompositePk the composite key to set
     */
    public void setFacilityIdLabResultCompositePk(final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk) {
        pcSetfacilityIdLabResultCompositePk(this, facilityIdLabResultCompositePk);
    }
    
    /**
     * Gets the CAISI demographic identifier for the patient associated with this lab result.
     *
     * @return int the local CAISI demographic ID
     */
    public int getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }
    
    /**
     * Sets the CAISI demographic identifier for the patient associated with this lab result.
     *
     * @param caisiDemographicId int the local CAISI demographic ID to set
     */
    public void setCaisiDemographicId(final int caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }
    
    /**
     * Gets the type classification of this lab result.
     *
     * @return String the lab result type (e.g., "HL7", "XML", specific test type), maximum 64 characters
     */
    public String getType() {
        return pcGettype(this);
    }
    
    /**
     * Sets the type classification of this lab result.
     *
     * @param type String the lab result type to set, maximum 64 characters
     */
    public void setType(final String type) {
        pcSettype(this, type);
    }
    
    /**
     * Gets the actual lab result data content.
     *
     * @return String the lab result data stored as a string (may contain HL7 messages, XML, JSON, or other formats)
     */
    public String getData() {
        return pcGetdata(this);
    }
    
    /**
     * Sets the actual lab result data content.
     *
     * @param data String the lab result data to store (may contain HL7 messages, XML, JSON, or other formats)
     */
    public void setData(final String data) {
        pcSetdata(this, data);
    }
    
    /**
     * Gets the OpenJPA enhancement contract version.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates which version
     * of the enhancement contract this class implements.
     *
     * @return int the enhancement contract version (currently 2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 3515657542883150113L;
        CachedDemographicLabResult.pcFieldNames = new String[] { "caisiDemographicId", "data", "facilityIdLabResultCompositePk", "type" };
        CachedDemographicLabResult.pcFieldTypes = new Class[] { Integer.TYPE, (CachedDemographicLabResult.class$Ljava$lang$String != null) ? CachedDemographicLabResult.class$Ljava$lang$String : (CachedDemographicLabResult.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicLabResult.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk != null) ? CachedDemographicLabResult.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk : (CachedDemographicLabResult.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdLabResultCompositePk")), (CachedDemographicLabResult.class$Ljava$lang$String != null) ? CachedDemographicLabResult.class$Ljava$lang$String : (CachedDemographicLabResult.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedDemographicLabResult.pcFieldFlags = new byte[] { 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographicLabResult.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicLabResult != null) ? CachedDemographicLabResult.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicLabResult : (CachedDemographicLabResult.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicLabResult = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicLabResult")), CachedDemographicLabResult.pcFieldNames, CachedDemographicLabResult.pcFieldTypes, CachedDemographicLabResult.pcFieldFlags, CachedDemographicLabResult.pcPCSuperclass, "CachedDemographicLabResult", (PersistenceCapable)new CachedDemographicLabResult());
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
     * Clears all managed fields to their default values.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used during
     * entity lifecycle management to reset field values.
     */
    protected void pcClearFields() {
        this.caisiDemographicId = 0;
        this.data = null;
        this.facilityIdLabResultCompositePk = null;
        this.type = null;
    }
    
    /**
     * Creates a new instance of this entity with the specified state manager and object ID.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used by the
     * persistence framework to create new managed instances.
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object ID to copy key fields from
     * @param b boolean if true, clear all fields to default values
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicLabResult cachedDemographicLabResult = new CachedDemographicLabResult();
        if (b) {
            cachedDemographicLabResult.pcClearFields();
        }
        cachedDemographicLabResult.pcStateManager = pcStateManager;
        cachedDemographicLabResult.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicLabResult;
    }
    
    /**
     * Creates a new instance of this entity with the specified state manager.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used by the
     * persistence framework to create new managed instances.
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean if true, clear all fields to default values
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicLabResult cachedDemographicLabResult = new CachedDemographicLabResult();
        if (b) {
            cachedDemographicLabResult.pcClearFields();
        }
        cachedDemographicLabResult.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicLabResult;
    }
    
    /**
     * Gets the count of managed fields in this entity.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and returns the total
     * number of fields managed by the persistence framework (4 fields: caisiDemographicId,
     * data, facilityIdLabResultCompositePk, type).
     *
     * @return int the number of managed fields (4)
     */
    protected static int pcGetManagedFieldCount() {
        return 4;
    }
    
    /**
     * Replaces a single field value using the state manager.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used during
     * entity state transitions to replace field values.
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicLabResult.pcInheritedFieldCount;
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
     * Replaces multiple field values using the state manager.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used during
     * entity state transitions to replace multiple field values efficiently.
     *
     * @param array int[] the array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }
    
    /**
     * Provides a single field value to the state manager.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used to provide
     * field values to the persistence framework during state management operations.
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedDemographicLabResult.pcInheritedFieldCount;
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
     * Provides multiple field values to the state manager.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used to provide
     * multiple field values to the persistence framework efficiently during state management operations.
     *
     * @param array int[] the array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    /**
     * Copies a single field value from another instance.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used to copy
     * field values between entity instances during persistence operations.
     *
     * @param cachedDemographicLabResult CachedDemographicLabResult the source instance to copy from
     * @param n int the field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final CachedDemographicLabResult cachedDemographicLabResult, final int n) {
        final int n2 = n - CachedDemographicLabResult.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = cachedDemographicLabResult.caisiDemographicId;
                return;
            }
            case 1: {
                this.data = cachedDemographicLabResult.data;
                return;
            }
            case 2: {
                this.facilityIdLabResultCompositePk = cachedDemographicLabResult.facilityIdLabResultCompositePk;
                return;
            }
            case 3: {
                this.type = cachedDemographicLabResult.type;
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
     * This method is part of the OpenJPA PersistenceCapable contract and is used to copy
     * multiple field values between entity instances efficiently during persistence operations.
     *
     * @param o Object the source instance to copy from
     * @param array int[] the array of field indices to copy
     * @throws IllegalArgumentException if the source object has a different state manager
     * @throws IllegalStateException if this instance has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicLabResult cachedDemographicLabResult = (CachedDemographicLabResult)o;
        if (cachedDemographicLabResult.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicLabResult, array[i]);
        }
    }
    
    /**
     * Gets the generic context from the state manager.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and provides access to
     * the generic context maintained by the state manager.
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
     * Fetches the object ID from the state manager.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and retrieves the object
     * ID for this persistent instance.
     *
     * @return Object the object ID, or null if no state manager is attached
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }
    
    /**
     * Checks if this entity is in a deleted state.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates whether
     * this instance has been marked for deletion in the current transaction.
     *
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }
    
    /**
     * Checks if this entity has been modified.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates whether
     * this instance has uncommitted changes.
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
     * Checks if this entity is newly created and not yet persisted.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates whether
     * this instance is new to the persistence context.
     *
     * @return boolean true if the entity is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }
    
    /**
     * Checks if this entity is managed by the persistence context.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates whether
     * this instance is currently being managed by a persistence context.
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }
    
    /**
     * Checks if this entity is enrolled in a transaction.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates whether
     * this instance is participating in a current transaction.
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }
    
    /**
     * Checks if this entity is currently being serialized.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates whether
     * this instance is in the process of serialization.
     *
     * @return boolean true if the entity is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }
    
    /**
     * Marks a field as dirty.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and notifies the state
     * manager that a specific field has been modified.
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
     * Gets the state manager associated with this entity.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and provides access to
     * the state manager responsible for tracking this instance.
     *
     * @return StateManager the state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }
    
    /**
     * Gets the version information for this entity.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and retrieves version
     * information used for optimistic locking.
     *
     * @return Object the version information, or null if no state manager is attached
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
     * This method is part of the OpenJPA PersistenceCapable contract and is used to replace
     * the state manager during entity lifecycle transitions.
     *
     * @param pcStateManager StateManager the new state manager to associate with this instance
     * @throws SecurityException if the replacement is not allowed
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
     * This method is part of the OpenJPA PersistenceCapable contract but is not supported
     * for this entity type due to the embedded ID structure.
     *
     * @param fieldSupplier FieldSupplier the field supplier to use
     * @param o Object the target object ID
     * @throws InternalException always, as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }
    
    /**
     * Copies key fields to an object ID.
     *
     * This method is part of the OpenJPA PersistenceCapable contract but is not supported
     * for this entity type due to the embedded ID structure.
     *
     * @param o Object the target object ID
     * @throws InternalException always, as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }
    
    /**
     * Copies key fields from an object ID using a field consumer.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used to populate
     * key fields from an object ID during entity restoration.
     *
     * @param fieldConsumer FieldConsumer the field consumer to use for storing the field value
     * @param o Object the source object ID containing the key fields
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(2 + CachedDemographicLabResult.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    /**
     * Copies key fields from an object ID.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used to populate
     * the composite primary key from an object ID during entity restoration.
     *
     * @param o Object the source object ID containing the composite primary key
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityIdLabResultCompositePk = (FacilityIdLabResultCompositePk)((ObjectId)o).getId();
    }
    
    /**
     * Creates a new object ID instance from a string representation.
     *
     * This method is part of the OpenJPA PersistenceCapable contract but is not supported
     * for this entity type due to its custom composite primary key structure.
     *
     * @param o Object the source object for creating the new object ID
     * @return Object the newly created object ID instance
     * @throws IllegalArgumentException always, as string-based object ID construction is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicLabResult\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    /**
     * Creates a new object ID instance for this entity.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and creates an object ID
     * based on the current composite primary key value.
     *
     * @return Object the newly created object ID wrapping the composite primary key
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicLabResult.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicLabResult != null) ? CachedDemographicLabResult.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicLabResult : (CachedDemographicLabResult.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicLabResult = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicLabResult")), (Object)this.facilityIdLabResultCompositePk);
    }
    
    private static final int pcGetcaisiDemographicId(final CachedDemographicLabResult cachedDemographicLabResult) {
        if (cachedDemographicLabResult.pcStateManager == null) {
            return cachedDemographicLabResult.caisiDemographicId;
        }
        cachedDemographicLabResult.pcStateManager.accessingField(CachedDemographicLabResult.pcInheritedFieldCount + 0);
        return cachedDemographicLabResult.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedDemographicLabResult cachedDemographicLabResult, final int caisiDemographicId) {
        if (cachedDemographicLabResult.pcStateManager == null) {
            cachedDemographicLabResult.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedDemographicLabResult.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicLabResult, CachedDemographicLabResult.pcInheritedFieldCount + 0, cachedDemographicLabResult.caisiDemographicId, caisiDemographicId, 0);
    }
    
    private static final String pcGetdata(final CachedDemographicLabResult cachedDemographicLabResult) {
        if (cachedDemographicLabResult.pcStateManager == null) {
            return cachedDemographicLabResult.data;
        }
        cachedDemographicLabResult.pcStateManager.accessingField(CachedDemographicLabResult.pcInheritedFieldCount + 1);
        return cachedDemographicLabResult.data;
    }
    
    private static final void pcSetdata(final CachedDemographicLabResult cachedDemographicLabResult, final String data) {
        if (cachedDemographicLabResult.pcStateManager == null) {
            cachedDemographicLabResult.data = data;
            return;
        }
        cachedDemographicLabResult.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicLabResult, CachedDemographicLabResult.pcInheritedFieldCount + 1, cachedDemographicLabResult.data, data, 0);
    }
    
    private static final FacilityIdLabResultCompositePk pcGetfacilityIdLabResultCompositePk(final CachedDemographicLabResult cachedDemographicLabResult) {
        if (cachedDemographicLabResult.pcStateManager == null) {
            return cachedDemographicLabResult.facilityIdLabResultCompositePk;
        }
        cachedDemographicLabResult.pcStateManager.accessingField(CachedDemographicLabResult.pcInheritedFieldCount + 2);
        return cachedDemographicLabResult.facilityIdLabResultCompositePk;
    }
    
    private static final void pcSetfacilityIdLabResultCompositePk(final CachedDemographicLabResult cachedDemographicLabResult, final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk) {
        if (cachedDemographicLabResult.pcStateManager == null) {
            cachedDemographicLabResult.facilityIdLabResultCompositePk = facilityIdLabResultCompositePk;
            return;
        }
        cachedDemographicLabResult.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicLabResult, CachedDemographicLabResult.pcInheritedFieldCount + 2, (Object)cachedDemographicLabResult.facilityIdLabResultCompositePk, (Object)facilityIdLabResultCompositePk, 0);
    }
    
    private static final String pcGettype(final CachedDemographicLabResult cachedDemographicLabResult) {
        if (cachedDemographicLabResult.pcStateManager == null) {
            return cachedDemographicLabResult.type;
        }
        cachedDemographicLabResult.pcStateManager.accessingField(CachedDemographicLabResult.pcInheritedFieldCount + 3);
        return cachedDemographicLabResult.type;
    }
    
    private static final void pcSettype(final CachedDemographicLabResult cachedDemographicLabResult, final String type) {
        if (cachedDemographicLabResult.pcStateManager == null) {
            cachedDemographicLabResult.type = type;
            return;
        }
        cachedDemographicLabResult.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicLabResult, CachedDemographicLabResult.pcInheritedFieldCount + 3, cachedDemographicLabResult.type, type, 0);
    }
    
    /**
     * Checks if this entity is in a detached state.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and determines whether
     * this instance has been detached from its persistence context. This is important for
     * distributed healthcare systems where entities may be transferred between facilities.
     *
     * @return Boolean true if detached, false if attached, null if the detached state is indeterminate
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
     * Gets the detached state information for this entity.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and retrieves the
     * detached state marker used to track whether the entity is detached from its persistence context.
     *
     * @return Object the detached state marker
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }
    
    /**
     * Sets the detached state information for this entity.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used to set the
     * detached state marker when the entity is detached from or reattached to a persistence context.
     *
     * @param pcDetachedState Object the detached state marker to set
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
