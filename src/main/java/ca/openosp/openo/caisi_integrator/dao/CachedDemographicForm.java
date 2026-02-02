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
 * Cached entity for storing demographic form data in the CAISI Integrator system.
 *
 * This entity represents a cached copy of patient demographic form information that is synchronized
 * across multiple healthcare facilities through the CAISI (Client Access to Integrated Services and Information)
 * integration framework. The cache improves performance by storing frequently accessed demographic forms
 * locally while maintaining consistency with remote facility data.
 *
 * <p>The entity uses OpenJPA enhancement for persistence capability, which provides transparent
 * state management, lazy loading, and change tracking through bytecode instrumentation. The enhancement
 * process generates additional methods (prefixed with 'pc') that handle communication with the
 * OpenJPA StateManager for all field access operations.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Composite primary key using facility ID and integer ID for multi-facility support</li>
 *   <li>Form data stored as medium blob to accommodate large healthcare documents</li>
 *   <li>Edit date tracking for synchronization and audit purposes</li>
 *   <li>Provider and demographic associations for access control</li>
 *   <li>OpenJPA detachment support for serialization across tiers</li>
 * </ul>
 *
 * @see FacilityIdIntegerCompositePk
 * @see AbstractModel
 * @see org.apache.openjpa.enhance.PersistenceCapable
 * @since 2026-01-24
 */
@Entity
public class CachedDemographicForm extends AbstractModel<FacilityIdIntegerCompositePk> implements PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    @Column(nullable = false, length = 16)
    private String caisiProviderId;
    @Column(nullable = false)
    @Index
    private Integer caisiDemographicId;
    @Temporal(TemporalType.DATE)
    private Date editDate;
    @Column(nullable = false, length = 128)
    private String formName;
    @Column(columnDefinition = "mediumblob")
    private String formData;
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
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicForm;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor initializing a new CachedDemographicForm instance.
     *
     * Creates a new cached demographic form with all fields set to null.
     * This constructor is required by JPA specification and is used during
     * entity instantiation and deserialization.
     */
    public CachedDemographicForm() {
        this.caisiProviderId = null;
        this.caisiDemographicId = null;
        this.editDate = null;
    }

    /**
     * Retrieves the composite primary key for this cached demographic form.
     *
     * This method overrides the abstract getId method from the parent AbstractModel class
     * and delegates to the OpenJPA-enhanced getter to ensure proper state management and
     * change tracking.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key containing facility ID and form ID
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityIdIntegerCompositePk(this);
    }

    /**
     * Gets the composite primary key containing facility ID and form ID.
     *
     * This method provides direct access to the embedded composite key that uniquely
     * identifies this cached demographic form across multiple healthcare facilities.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key, or null if not set
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityIdIntegerCompositePk(this);
    }

    /**
     * Sets the composite primary key for this cached demographic form.
     *
     * Assigns the facility ID and form ID composite key that uniquely identifies
     * this form in the multi-facility integration environment.
     *
     * @param facilityIdIntegerCompositePk FacilityIdIntegerCompositePk the composite key to set
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        pcSetfacilityIdIntegerCompositePk(this, facilityIdIntegerCompositePk);
    }

    /**
     * Gets the CAISI provider identifier associated with this cached form.
     *
     * The provider ID identifies the healthcare provider who created or last modified
     * this demographic form in the CAISI integration system.
     *
     * @return String the CAISI provider identifier (max 16 characters), or null if not set
     */
    public String getCaisiProviderId() {
        return pcGetcaisiProviderId(this);
    }

    /**
     * Sets the CAISI provider identifier for this cached form.
     *
     * Associates a healthcare provider with this demographic form for access control
     * and audit tracking purposes.
     *
     * @param caisiProviderId String the CAISI provider identifier to set (max 16 characters)
     */
    public void setCaisiProviderId(final String caisiProviderId) {
        pcSetcaisiProviderId(this, caisiProviderId);
    }

    /**
     * Gets the CAISI demographic identifier for the patient associated with this form.
     *
     * This indexed field enables efficient queries by patient demographic ID across
     * the cached form data.
     *
     * @return Integer the CAISI demographic (patient) identifier, or null if not set
     */
    public Integer getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }

    /**
     * Sets the CAISI demographic identifier for this cached form.
     *
     * Associates this form with a specific patient in the CAISI integration system.
     * This field is indexed for query performance.
     *
     * @param caisiDemographicId Integer the patient demographic identifier to set
     */
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }

    /**
     * Gets the last edit date of this demographic form.
     *
     * This temporal field tracks when the form was last modified, supporting
     * synchronization logic and audit requirements across facilities.
     *
     * @return Date the last edit date (date only, no time component), or null if not set
     */
    public Date getEditDate() {
        return pcGeteditDate(this);
    }

    /**
     * Sets the last edit date for this demographic form.
     *
     * Records the date this form was last modified, used for cache invalidation
     * and synchronization across the CAISI integration network.
     *
     * @param editDate Date the edit date to set (date only, no time component)
     */
    public void setEditDate(final Date editDate) {
        pcSeteditDate(this, editDate);
    }

    /**
     * Gets the form data content stored in this cached demographic form.
     *
     * The form data is stored as a medium blob to accommodate large healthcare
     * documents and structured form content.
     *
     * @return String the serialized form data content, or null if not set
     */
    public String getFormData() {
        return pcGetformData(this);
    }

    /**
     * Sets the form data content for this cached demographic form.
     *
     * Stores the complete form data, typically in serialized format, which may include
     * patient demographics, healthcare information, and other clinical data.
     *
     * @param formData String the serialized form data to store (stored as medium blob)
     */
    public void setFormData(final String formData) {
        pcSetformData(this, formData);
    }

    /**
     * Gets the name identifier of this cached demographic form.
     *
     * The form name identifies the type or template of the demographic form being cached.
     *
     * @return String the form name identifier (max 128 characters), or null if not set
     */
    public String getFormName() {
        return pcGetformName(this);
    }

    /**
     * Sets the name identifier for this cached demographic form.
     *
     * Assigns a name that identifies the form type or template, enabling retrieval
     * and categorization of cached forms by their purpose.
     *
     * @param formName String the form name to set (max 128 characters)
     */
    public void setFormName(final String formName) {
        pcSetformName(this, formName);
    }

    /**
     * Gets the OpenJPA enhancement contract version for this entity.
     *
     * This method is part of the OpenJPA PersistenceCapable interface and indicates
     * the version of the bytecode enhancement contract being used. Version 2 is the
     * standard contract for OpenJPA-enhanced entities.
     *
     * @return int the enhancement contract version (always 2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 5367902933529831098L;
        CachedDemographicForm.pcFieldNames = new String[] { "caisiDemographicId", "caisiProviderId", "editDate", "facilityIdIntegerCompositePk", "formData", "formName" };
        CachedDemographicForm.pcFieldTypes = new Class[] { (CachedDemographicForm.class$Ljava$lang$Integer != null) ? CachedDemographicForm.class$Ljava$lang$Integer : (CachedDemographicForm.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedDemographicForm.class$Ljava$lang$String != null) ? CachedDemographicForm.class$Ljava$lang$String : (CachedDemographicForm.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicForm.class$Ljava$util$Date != null) ? CachedDemographicForm.class$Ljava$util$Date : (CachedDemographicForm.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographicForm.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedDemographicForm.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedDemographicForm.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedDemographicForm.class$Ljava$lang$String != null) ? CachedDemographicForm.class$Ljava$lang$String : (CachedDemographicForm.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicForm.class$Ljava$lang$String != null) ? CachedDemographicForm.class$Ljava$lang$String : (CachedDemographicForm.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedDemographicForm.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographicForm.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicForm != null) ? CachedDemographicForm.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicForm : (CachedDemographicForm.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicForm = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicForm")), CachedDemographicForm.pcFieldNames, CachedDemographicForm.pcFieldTypes, CachedDemographicForm.pcFieldFlags, CachedDemographicForm.pcPCSuperclass, "CachedDemographicForm", (PersistenceCapable)new CachedDemographicForm());
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
     * Clears all persistent fields in this entity to their default null values.
     *
     * This method is used by OpenJPA during entity lifecycle operations to reset
     * field values when creating new instances or during state transitions.
     */
    protected void pcClearFields() {
        this.caisiDemographicId = null;
        this.caisiProviderId = null;
        this.editDate = null;
        this.facilityIdIntegerCompositePk = null;
        this.formData = null;
        this.formName = null;
    }

    /**
     * Creates a new instance of this entity with the specified state manager and object ID.
     *
     * This factory method is used by OpenJPA to create new entity instances during
     * persistence operations. It assigns the provided state manager and copies key fields
     * from the given object ID.
     *
     * @param pcStateManager StateManager the OpenJPA state manager to assign
     * @param o Object the object ID containing key field values
     * @param b boolean whether to clear all fields after instantiation
     * @return PersistenceCapable a new CachedDemographicForm instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicForm cachedDemographicForm = new CachedDemographicForm();
        if (b) {
            cachedDemographicForm.pcClearFields();
        }
        cachedDemographicForm.pcStateManager = pcStateManager;
        cachedDemographicForm.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicForm;
    }

    /**
     * Creates a new instance of this entity with the specified state manager.
     *
     * This factory method is used by OpenJPA to create new entity instances without
     * an object ID, typically for transient instances that will be persisted later.
     *
     * @param pcStateManager StateManager the OpenJPA state manager to assign
     * @param b boolean whether to clear all fields after instantiation
     * @return PersistenceCapable a new CachedDemographicForm instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicForm cachedDemographicForm = new CachedDemographicForm();
        if (b) {
            cachedDemographicForm.pcClearFields();
        }
        cachedDemographicForm.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicForm;
    }
    
    /**
     * Gets the number of managed persistent fields in this entity.
     *
     * This count is used by OpenJPA to iterate over all persistent fields during
     * various state management operations.
     *
     * @return int the number of managed fields (always 6)
     */
    protected static int pcGetManagedFieldCount() {
        return 6;
    }

    /**
     * Replaces a single field value from the state manager.
     *
     * This method is called by OpenJPA during state synchronization to update field
     * values from the managed state. The field index is used to identify which field
     * to replace.
     *
     * @param n int the absolute field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicForm.pcInheritedFieldCount;
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
                this.editDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.formData = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.formName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
     * This batch operation calls pcReplaceField for each field index in the provided array,
     * allowing OpenJPA to efficiently synchronize multiple fields at once.
     *
     * @param array int[] array of absolute field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single field value to the state manager.
     *
     * This method is called by OpenJPA to retrieve current field values and provide them
     * to the state manager for change detection and persistence operations.
     *
     * @param n int the absolute field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedDemographicForm.pcInheritedFieldCount;
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
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.editDate);
                return;
            }
            case 3: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityIdIntegerCompositePk);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.formData);
                return;
            }
            case 5: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.formName);
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
     * This batch operation calls pcProvideField for each field index in the provided array,
     * allowing OpenJPA to efficiently retrieve multiple field values at once.
     *
     * @param array int[] array of absolute field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }

    /**
     * Copies a single field value from another instance of this entity.
     *
     * This method is used during entity cloning and merge operations to transfer
     * field values between entity instances.
     *
     * @param cachedDemographicForm CachedDemographicForm the source instance to copy from
     * @param n int the absolute field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final CachedDemographicForm cachedDemographicForm, final int n) {
        final int n2 = n - CachedDemographicForm.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = cachedDemographicForm.caisiDemographicId;
                return;
            }
            case 1: {
                this.caisiProviderId = cachedDemographicForm.caisiProviderId;
                return;
            }
            case 2: {
                this.editDate = cachedDemographicForm.editDate;
                return;
            }
            case 3: {
                this.facilityIdIntegerCompositePk = cachedDemographicForm.facilityIdIntegerCompositePk;
                return;
            }
            case 4: {
                this.formData = cachedDemographicForm.formData;
                return;
            }
            case 5: {
                this.formName = cachedDemographicForm.formName;
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
     * This method performs a batch copy operation for the specified fields from the source
     * object to this instance. Both instances must share the same state manager.
     *
     * @param o Object the source CachedDemographicForm instance to copy from
     * @param array int[] array of absolute field indices to copy
     * @throws IllegalArgumentException if the instances have different state managers
     * @throws IllegalStateException if this instance has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicForm cachedDemographicForm = (CachedDemographicForm)o;
        if (cachedDemographicForm.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicForm, array[i]);
        }
    }

    /**
     * Gets the generic context associated with this entity's state manager.
     *
     * The generic context is used by OpenJPA to store additional metadata about the
     * persistence context in which this entity is managed.
     *
     * @return Object the generic context, or null if no state manager is assigned
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
     * Retrieves the OpenJPA object identifier that uniquely identifies this entity
     * instance within the persistence context.
     *
     * @return Object the object ID, or null if no state manager is assigned
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks if this entity is in the deleted state.
     *
     * Indicates whether this entity has been marked for deletion in the current
     * persistence context.
     *
     * @return boolean true if deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has been modified since loading.
     *
     * Performs a dirty check to determine if any persistent fields have been changed
     * from their original loaded values.
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
     * Indicates whether this entity was created in the current transaction and
     * has not yet been flushed to the database.
     *
     * @return boolean true if new and not yet persisted, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is currently managed by a persistence context.
     *
     * Indicates whether this entity is in a persistent state and being tracked
     * by OpenJPA for automatic change detection and synchronization.
     *
     * @return boolean true if persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity is participating in a transaction.
     *
     * Indicates whether this entity is enlisted in the current transaction context
     * and subject to transactional operations.
     *
     * @return boolean true if transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity is currently being serialized.
     *
     * Used during serialization to determine whether special handling is needed
     * for detached state and transient fields.
     *
     * @return boolean true if currently serializing, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a specific field as dirty in the state manager.
     *
     * Notifies OpenJPA that the named field has been modified and should be included
     * in the next flush or commit operation.
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
     * Gets the state manager assigned to this entity.
     *
     * Returns the OpenJPA StateManager instance responsible for tracking changes
     * and managing the persistence lifecycle of this entity.
     *
     * @return StateManager the assigned state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Gets the version indicator for this entity.
     *
     * Returns the version field value used for optimistic locking, if versioning
     * is enabled for this entity type.
     *
     * @return Object the version value, or null if no state manager or no versioning
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
     * Assigns a new state manager to this entity, typically during attachment to a
     * new persistence context. If a state manager already exists, it delegates the
     * replacement to the existing manager.
     *
     * @param pcStateManager StateManager the new state manager to assign
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
     * This method is not supported for this entity type because it uses an embedded
     * composite key rather than individual key fields.
     *
     * @param fieldSupplier FieldSupplier the field supplier to use
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields to an object ID.
     *
     * This method is not supported for this entity type because it uses an embedded
     * composite key rather than individual key fields.
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
     * Extracts the embedded composite key from the OpenJPA ObjectId and stores it
     * in the provided field consumer.
     *
     * @param fieldConsumer FieldConsumer the field consumer to receive the key value
     * @param o Object the source ObjectId containing the composite key
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(3 + CachedDemographicForm.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key fields from an object ID to this entity.
     *
     * Extracts the embedded composite key from the OpenJPA ObjectId and assigns it
     * to this entity's facilityIdIntegerCompositePk field.
     *
     * @param o Object the source ObjectId containing the composite key
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * This method is not supported for this entity type because the ObjectId class
     * does not provide the required string constructor.
     *
     * @param o Object the string representation (not used)
     * @return Object never returns (always throws exception)
     * @throws IllegalArgumentException always thrown as this operation is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicForm\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance for this entity.
     *
     * Constructs a new OpenJPA ObjectId using this entity's class and current
     * composite key value.
     *
     * @return Object a new ObjectId instance containing this entity's composite key
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicForm.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicForm != null) ? CachedDemographicForm.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicForm : (CachedDemographicForm.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicForm = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicForm")), (Object)this.facilityIdIntegerCompositePk);
    }

    /**
     * OpenJPA-enhanced getter for the caisiDemographicId field.
     *
     * This static method implements the enhanced field access pattern, notifying the
     * state manager when the field is accessed and returning the current value.
     *
     * @param cachedDemographicForm CachedDemographicForm the entity instance
     * @return Integer the demographic ID value
     */
    private static final Integer pcGetcaisiDemographicId(final CachedDemographicForm cachedDemographicForm) {
        if (cachedDemographicForm.pcStateManager == null) {
            return cachedDemographicForm.caisiDemographicId;
        }
        cachedDemographicForm.pcStateManager.accessingField(CachedDemographicForm.pcInheritedFieldCount + 0);
        return cachedDemographicForm.caisiDemographicId;
    }

    /**
     * OpenJPA-enhanced setter for the caisiDemographicId field.
     *
     * This static method implements the enhanced field mutation pattern, notifying the
     * state manager when the field is modified to enable change tracking.
     *
     * @param cachedDemographicForm CachedDemographicForm the entity instance
     * @param caisiDemographicId Integer the new demographic ID value
     */
    private static final void pcSetcaisiDemographicId(final CachedDemographicForm cachedDemographicForm, final Integer caisiDemographicId) {
        if (cachedDemographicForm.pcStateManager == null) {
            cachedDemographicForm.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedDemographicForm.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicForm, CachedDemographicForm.pcInheritedFieldCount + 0, (Object)cachedDemographicForm.caisiDemographicId, (Object)caisiDemographicId, 0);
    }

    /**
     * OpenJPA-enhanced getter for the caisiProviderId field.
     *
     * @param cachedDemographicForm CachedDemographicForm the entity instance
     * @return String the provider ID value
     */
    private static final String pcGetcaisiProviderId(final CachedDemographicForm cachedDemographicForm) {
        if (cachedDemographicForm.pcStateManager == null) {
            return cachedDemographicForm.caisiProviderId;
        }
        cachedDemographicForm.pcStateManager.accessingField(CachedDemographicForm.pcInheritedFieldCount + 1);
        return cachedDemographicForm.caisiProviderId;
    }

    /**
     * OpenJPA-enhanced setter for the caisiProviderId field.
     *
     * @param cachedDemographicForm CachedDemographicForm the entity instance
     * @param caisiProviderId String the new provider ID value
     */
    private static final void pcSetcaisiProviderId(final CachedDemographicForm cachedDemographicForm, final String caisiProviderId) {
        if (cachedDemographicForm.pcStateManager == null) {
            cachedDemographicForm.caisiProviderId = caisiProviderId;
            return;
        }
        cachedDemographicForm.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicForm, CachedDemographicForm.pcInheritedFieldCount + 1, cachedDemographicForm.caisiProviderId, caisiProviderId, 0);
    }

    /**
     * OpenJPA-enhanced getter for the editDate field.
     *
     * @param cachedDemographicForm CachedDemographicForm the entity instance
     * @return Date the edit date value
     */
    private static final Date pcGeteditDate(final CachedDemographicForm cachedDemographicForm) {
        if (cachedDemographicForm.pcStateManager == null) {
            return cachedDemographicForm.editDate;
        }
        cachedDemographicForm.pcStateManager.accessingField(CachedDemographicForm.pcInheritedFieldCount + 2);
        return cachedDemographicForm.editDate;
    }

    /**
     * OpenJPA-enhanced setter for the editDate field.
     *
     * @param cachedDemographicForm CachedDemographicForm the entity instance
     * @param editDate Date the new edit date value
     */
    private static final void pcSeteditDate(final CachedDemographicForm cachedDemographicForm, final Date editDate) {
        if (cachedDemographicForm.pcStateManager == null) {
            cachedDemographicForm.editDate = editDate;
            return;
        }
        cachedDemographicForm.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicForm, CachedDemographicForm.pcInheritedFieldCount + 2, (Object)cachedDemographicForm.editDate, (Object)editDate, 0);
    }

    /**
     * OpenJPA-enhanced getter for the facilityIdIntegerCompositePk field.
     *
     * @param cachedDemographicForm CachedDemographicForm the entity instance
     * @return FacilityIdIntegerCompositePk the composite key value
     */
    private static final FacilityIdIntegerCompositePk pcGetfacilityIdIntegerCompositePk(final CachedDemographicForm cachedDemographicForm) {
        if (cachedDemographicForm.pcStateManager == null) {
            return cachedDemographicForm.facilityIdIntegerCompositePk;
        }
        cachedDemographicForm.pcStateManager.accessingField(CachedDemographicForm.pcInheritedFieldCount + 3);
        return cachedDemographicForm.facilityIdIntegerCompositePk;
    }

    /**
     * OpenJPA-enhanced setter for the facilityIdIntegerCompositePk field.
     *
     * @param cachedDemographicForm CachedDemographicForm the entity instance
     * @param facilityIdIntegerCompositePk FacilityIdIntegerCompositePk the new composite key value
     */
    private static final void pcSetfacilityIdIntegerCompositePk(final CachedDemographicForm cachedDemographicForm, final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        if (cachedDemographicForm.pcStateManager == null) {
            cachedDemographicForm.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
            return;
        }
        cachedDemographicForm.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicForm, CachedDemographicForm.pcInheritedFieldCount + 3, (Object)cachedDemographicForm.facilityIdIntegerCompositePk, (Object)facilityIdIntegerCompositePk, 0);
    }

    /**
     * OpenJPA-enhanced getter for the formData field.
     *
     * @param cachedDemographicForm CachedDemographicForm the entity instance
     * @return String the form data value
     */
    private static final String pcGetformData(final CachedDemographicForm cachedDemographicForm) {
        if (cachedDemographicForm.pcStateManager == null) {
            return cachedDemographicForm.formData;
        }
        cachedDemographicForm.pcStateManager.accessingField(CachedDemographicForm.pcInheritedFieldCount + 4);
        return cachedDemographicForm.formData;
    }

    /**
     * OpenJPA-enhanced setter for the formData field.
     *
     * @param cachedDemographicForm CachedDemographicForm the entity instance
     * @param formData String the new form data value
     */
    private static final void pcSetformData(final CachedDemographicForm cachedDemographicForm, final String formData) {
        if (cachedDemographicForm.pcStateManager == null) {
            cachedDemographicForm.formData = formData;
            return;
        }
        cachedDemographicForm.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicForm, CachedDemographicForm.pcInheritedFieldCount + 4, cachedDemographicForm.formData, formData, 0);
    }

    /**
     * OpenJPA-enhanced getter for the formName field.
     *
     * @param cachedDemographicForm CachedDemographicForm the entity instance
     * @return String the form name value
     */
    private static final String pcGetformName(final CachedDemographicForm cachedDemographicForm) {
        if (cachedDemographicForm.pcStateManager == null) {
            return cachedDemographicForm.formName;
        }
        cachedDemographicForm.pcStateManager.accessingField(CachedDemographicForm.pcInheritedFieldCount + 5);
        return cachedDemographicForm.formName;
    }

    /**
     * OpenJPA-enhanced setter for the formName field.
     *
     * @param cachedDemographicForm CachedDemographicForm the entity instance
     * @param formName String the new form name value
     */
    private static final void pcSetformName(final CachedDemographicForm cachedDemographicForm, final String formName) {
        if (cachedDemographicForm.pcStateManager == null) {
            cachedDemographicForm.formName = formName;
            return;
        }
        cachedDemographicForm.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicForm, CachedDemographicForm.pcInheritedFieldCount + 5, cachedDemographicForm.formName, formName, 0);
    }

    /**
     * Checks if this entity is in a detached state.
     *
     * Determines whether this entity has been detached from its persistence context,
     * which is important for merge operations and serialization across tiers.
     *
     * @return Boolean TRUE if detached, FALSE if attached, or null if state is indeterminate
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
     * Checks if the detached state is definitive.
     *
     * This method indicates whether the detached state can be reliably determined
     * for this entity type. Returns false as this entity does not define a fixed
     * detached state pattern.
     *
     * @return boolean always returns false
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Gets the detached state object for this entity.
     *
     * Returns the detached state marker used by OpenJPA to track whether this entity
     * was detached from a persistence context.
     *
     * @return Object the detached state marker, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object for this entity.
     *
     * Assigns the detached state marker used by OpenJPA during serialization and
     * deserialization operations.
     *
     * @param pcDetachedState Object the detached state marker to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method for this entity.
     *
     * Handles proper serialization of OpenJPA-enhanced entities by clearing the
     * detached state if currently being serialized by the state manager.
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
     * Custom deserialization method for this entity.
     *
     * Handles proper deserialization of OpenJPA-enhanced entities by marking the
     * entity as deserialized and requiring reattachment to a persistence context.
     *
     * @param objectInputStream ObjectInputStream the stream to read from
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if a required class cannot be found
     */
    private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        this.pcSetDetachedState(PersistenceCapable.DESERIALIZED);
        objectInputStream.defaultReadObject();
    }
}
