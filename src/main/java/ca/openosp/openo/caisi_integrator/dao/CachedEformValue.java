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
import org.apache.commons.lang3.StringUtils;
import org.apache.openjpa.enhance.StateManager;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * Represents a cached electronic form (eForm) field value in the CAISI integrator system.
 *
 * <p>This entity caches individual form field values from electronic forms to support efficient
 * cross-facility data sharing in the CAISI (Collaborative Application for Integrated Health Services Information)
 * integrator. Each instance stores a single variable name-value pair from a specific form, along with references
 * to the form, demographic, and facility context.</p>
 *
 * <p>The entity uses JPA/OpenJPA persistence with byte-code enhancement for field-level tracking and lazy loading.
 * Form field values are stored as text and automatically trimmed during persistence. The entity supports comparison
 * based on the CAISI item identifier for ordered collections.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Composite primary key combining facility ID and item ID for multi-facility support</li>
 *   <li>Indexes on demographic ID for efficient patient-specific queries</li>
 *   <li>Automatic string trimming for variable names (to empty) and values (to null)</li>
 *   <li>OpenJPA bytecode enhancement for persistence state management</li>
 *   <li>Serialization support for distributed caching</li>
 * </ul>
 *
 * @see FacilityIdIntegerCompositePk
 * @see AbstractModel
 * @see PersistenceCapable
 * @since 2026-01-24
 */
@Entity
public class CachedEformValue extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedEformValue>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityEformValuesPk;
    @Column(nullable = false)
    private Integer formDataId;
    private Integer formId;
    @Index
    private Integer caisiDemographicId;
    @Column(nullable = false, length = 30)
    private String varName;
    @Column(columnDefinition = "text")
    private String varValue;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedEformValue;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Constructs a new CachedEformValue with all fields initialized to null.
     *
     * <p>This default constructor is required for JPA entity instantiation and creates
     * an unpopulated instance ready for field assignment or ORM hydration.</p>
     */
    public CachedEformValue() {
        this.formDataId = null;
        this.formId = null;
        this.caisiDemographicId = null;
        this.varName = null;
        this.varValue = null;
    }

    /**
     * Retrieves the composite primary key containing facility and item identifiers.
     *
     * @return FacilityIdIntegerCompositePk the composite key identifying this cached form value
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityEformValuesPk(this);
    }

    /**
     * Sets the composite primary key containing facility and item identifiers.
     *
     * @param facilityEformValuesPk FacilityIdIntegerCompositePk the composite key to assign
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityEformValuesPk) {
        pcSetfacilityEformValuesPk(this, facilityEformValuesPk);
    }

    /**
     * Retrieves the form data identifier for the parent eForm submission.
     *
     * @return Integer the unique identifier of the form data record
     */
    public Integer getFormDataId() {
        return pcGetformDataId(this);
    }

    /**
     * Sets the form data identifier for the parent eForm submission.
     *
     * @param formDataId Integer the unique identifier of the form data record
     */
    public void setFormDataId(final Integer formDataId) {
        pcSetformDataId(this, formDataId);
    }

    /**
     * Retrieves the form template identifier.
     *
     * @return Integer the unique identifier of the eForm template
     */
    public Integer getFormId() {
        return pcGetformId(this);
    }

    /**
     * Sets the form template identifier.
     *
     * @param formId Integer the unique identifier of the eForm template
     */
    public void setFormId(final Integer formId) {
        pcSetformId(this, formId);
    }

    /**
     * Retrieves the CAISI demographic identifier for the patient associated with this form value.
     *
     * <p>This indexed field enables efficient retrieval of all cached form values for a specific patient
     * across multiple facilities in the CAISI integrator network.</p>
     *
     * @return Integer the unique CAISI demographic identifier for the patient
     */
    public Integer getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }

    /**
     * Sets the CAISI demographic identifier for the patient associated with this form value.
     *
     * @param caisiDemographicId Integer the unique CAISI demographic identifier for the patient
     */
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }

    /**
     * Retrieves the form field variable name.
     *
     * <p>Variable names identify specific fields within an eForm template, such as "patient_weight",
     * "blood_pressure_systolic", or "appointment_date". These names correspond to the field identifiers
     * defined in the eForm template.</p>
     *
     * @return String the field variable name (maximum 30 characters)
     */
    public String getVarName() {
        return pcGetvarName(this);
    }

    /**
     * Sets the form field variable name.
     *
     * <p>The provided variable name is automatically trimmed to an empty string if null or whitespace-only.
     * This ensures consistent handling of field names across the system.</p>
     *
     * @param varName String the field variable name (maximum 30 characters, will be trimmed to empty if null)
     */
    public void setVarName(final String varName) {
        pcSetvarName(this, StringUtils.trimToEmpty(varName));
    }

    /**
     * Retrieves the form field value.
     *
     * <p>Form field values are stored as text and may contain patient-entered data, provider selections,
     * calculated results, or other form content. The value is stored in a TEXT column to accommodate
     * large content such as clinical notes or detailed responses.</p>
     *
     * @return String the field value, or null if the field has no value
     */
    public String getVarValue() {
        return pcGetvarValue(this);
    }

    /**
     * Sets the form field value.
     *
     * <p>The provided value is automatically trimmed to null if it contains only whitespace.
     * This ensures that empty or whitespace-only values are stored consistently as null
     * rather than as empty strings.</p>
     *
     * @param varValue String the field value to store, or null (will be trimmed to null if whitespace-only)
     */
    public void setVarValue(final String varValue) {
        pcSetvarValue(this, StringUtils.trimToNull(varValue));
    }

    /**
     * Compares this cached form value to another based on CAISI item identifiers.
     *
     * <p>The comparison is performed by subtracting the other object's CAISI item ID from this object's
     * CAISI item ID, providing a natural ordering for collections of cached form values.</p>
     *
     * @param o CachedEformValue the cached form value to compare against
     * @return int negative if this item ID is less, zero if equal, positive if greater
     */
    @Override
    public int compareTo(final CachedEformValue o) {
        return pcGetfacilityEformValuesPk(this).getCaisiItemId() - pcGetfacilityEformValuesPk(o).getCaisiItemId();
    }

    /**
     * Retrieves the entity identifier (composite primary key).
     *
     * <p>This method satisfies the AbstractModel contract by returning the composite key
     * that uniquely identifies this cached form value across all facilities.</p>
     *
     * @return FacilityIdIntegerCompositePk the composite primary key
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityEformValuesPk(this);
    }

    /**
     * Retrieves the OpenJPA enhancement contract version for this entity.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and indicates
     * the version of bytecode enhancement applied to this class. The contract version
     * ensures compatibility between the enhanced class and the OpenJPA runtime.</p>
     *
     * @return int the enhancement contract version (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -5338133436139653969L;
        CachedEformValue.pcFieldNames = new String[] { "caisiDemographicId", "facilityEformValuesPk", "formDataId", "formId", "varName", "varValue" };
        CachedEformValue.pcFieldTypes = new Class[] { (CachedEformValue.class$Ljava$lang$Integer != null) ? CachedEformValue.class$Ljava$lang$Integer : (CachedEformValue.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedEformValue.class$Ljava$lang$Integer != null) ? CachedEformValue.class$Ljava$lang$Integer : (CachedEformValue.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedEformValue.class$Ljava$lang$Integer != null) ? CachedEformValue.class$Ljava$lang$Integer : (CachedEformValue.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedEformValue.class$Ljava$lang$String != null) ? CachedEformValue.class$Ljava$lang$String : (CachedEformValue.class$Ljava$lang$String = class$("java.lang.String")), (CachedEformValue.class$Ljava$lang$String != null) ? CachedEformValue.class$Ljava$lang$String : (CachedEformValue.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedEformValue.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformValue != null) ? CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformValue : (CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformValue = class$("ca.openosp.openo.caisi_integrator.dao.CachedEformValue")), CachedEformValue.pcFieldNames, CachedEformValue.pcFieldTypes, CachedEformValue.pcFieldFlags, CachedEformValue.pcPCSuperclass, "CachedEformValue", (PersistenceCapable)new CachedEformValue());
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
        this.caisiDemographicId = null;
        this.facilityEformValuesPk = null;
        this.formDataId = null;
        this.formId = null;
        this.varName = null;
        this.varValue = null;
    }

    /**
     * Creates a new persistence-capable instance with the provided state manager and object ID.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and is used by the ORM
     * framework to create new instances during query result hydration and relationship loading.
     * The boolean parameter controls whether fields should be cleared after instantiation.</p>
     *
     * @param pcStateManager StateManager the OpenJPA state manager to assign to the new instance
     * @param o Object the object ID from which to copy key fields
     * @param b boolean true to clear fields after copying key fields, false to leave them unchanged
     * @return PersistenceCapable the newly created instance with assigned state manager
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedEformValue cachedEformValue = new CachedEformValue();
        if (b) {
            cachedEformValue.pcClearFields();
        }
        cachedEformValue.pcStateManager = pcStateManager;
        cachedEformValue.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedEformValue;
    }

    /**
     * Creates a new persistence-capable instance with the provided state manager.
     *
     * <p>This overload of pcNewInstance creates a new instance without copying key fields from
     * an object ID. It is used when creating new transient instances that will later be populated
     * with data from other sources.</p>
     *
     * @param pcStateManager StateManager the OpenJPA state manager to assign to the new instance
     * @param b boolean true to clear fields after instantiation, false to leave them unchanged
     * @return PersistenceCapable the newly created instance with assigned state manager
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedEformValue cachedEformValue = new CachedEformValue();
        if (b) {
            cachedEformValue.pcClearFields();
        }
        cachedEformValue.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedEformValue;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 6;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedEformValue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.facilityEformValuesPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.formDataId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.formId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.varName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.varValue = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }
    
    public void pcProvideField(final int n) {
        final int n2 = n - CachedEformValue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiDemographicId);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityEformValuesPk);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.formDataId);
                return;
            }
            case 3: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.formId);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.varName);
                return;
            }
            case 5: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.varValue);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    protected void pcCopyField(final CachedEformValue cachedEformValue, final int n) {
        final int n2 = n - CachedEformValue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = cachedEformValue.caisiDemographicId;
                return;
            }
            case 1: {
                this.facilityEformValuesPk = cachedEformValue.facilityEformValuesPk;
                return;
            }
            case 2: {
                this.formDataId = cachedEformValue.formDataId;
                return;
            }
            case 3: {
                this.formId = cachedEformValue.formId;
                return;
            }
            case 4: {
                this.varName = cachedEformValue.varName;
                return;
            }
            case 5: {
                this.varValue = cachedEformValue.varValue;
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
     * <p>This method is part of the OpenJPA PersistenceCapable contract and is used during
     * entity state synchronization. It validates that both instances share the same state manager
     * before performing the copy operation.</p>
     *
     * @param o Object the source CachedEformValue instance to copy field values from
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source object has a different state manager
     * @throws IllegalStateException if this instance has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedEformValue cachedEformValue = (CachedEformValue)o;
        if (cachedEformValue.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedEformValue, array[i]);
        }
    }

    /**
     * Retrieves the generic context object from the state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and provides access
     * to framework-specific context information associated with this entity instance.</p>
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
     * Fetches the object identifier for this entity instance.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and retrieves
     * the object ID that uniquely identifies this entity within the persistence context.</p>
     *
     * @return Object the object ID from the state manager, or null if no state manager is assigned
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks if this entity instance is marked as deleted in the current transaction.
     *
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity instance has been modified in the current transaction.
     *
     * <p>This method triggers a dirty check via the RedefinitionHelper before querying
     * the state manager to ensure accurate dirty state tracking.</p>
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
     * Checks if this entity instance is newly created and not yet persisted.
     *
     * @return boolean true if the entity is new (transient), false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity instance is persistent (managed by a persistence context).
     *
     * @return boolean true if the entity is persistent, false if transient
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity instance is enrolled in an active transaction.
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity instance is currently being serialized.
     *
     * @return boolean true if the entity is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks the specified field as dirty to trigger update tracking.
     *
     * @param s String the field name to mark as dirty
     */
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }

    /**
     * Retrieves the OpenJPA state manager associated with this entity instance.
     *
     * @return StateManager the state manager, or null if the entity is not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version object for this entity instance, used for optimistic locking.
     *
     * @return Object the version object from the state manager, or null if no state manager is assigned
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
     * <p>This method delegates to the existing state manager to handle the replacement,
     * or directly assigns the new state manager if none is currently assigned.</p>
     *
     * @param pcStateManager StateManager the new state manager to assign
     * @throws SecurityException if a security violation occurs during replacement
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
     * @param fieldSupplier FieldSupplier the field supplier to use for copying
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
     * <p>This method extracts the composite key from the provided object ID and stores it
     * in the field consumer at the appropriate field index.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer to receive the key field values
     * @param o Object the source object ID containing the key
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(1 + CachedEformValue.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key field values from an object ID into this entity instance.
     *
     * <p>This method extracts the composite primary key from the provided object ID
     * and assigns it to this entity's facilityEformValuesPk field.</p>
     *
     * @param o Object the source object ID containing the composite key
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityEformValuesPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * <p>This operation is not supported for ObjectId-based identity.</p>
     *
     * @param o Object the string representation of the object ID
     * @return Object not applicable as method throws exception
     * @throws IllegalArgumentException always thrown as string-based object ID construction is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedEformValue\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance for this entity using its current key field values.
     *
     * <p>This method constructs an OpenJPA ObjectId containing the composite primary key
     * from this entity's facilityEformValuesPk field.</p>
     *
     * @return Object the newly created object ID containing this entity's composite key
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformValue != null) ? CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformValue : (CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformValue = class$("ca.openosp.openo.caisi_integrator.dao.CachedEformValue")), (Object)this.facilityEformValuesPk);
    }
    
    private static final Integer pcGetcaisiDemographicId(final CachedEformValue cachedEformValue) {
        if (cachedEformValue.pcStateManager == null) {
            return cachedEformValue.caisiDemographicId;
        }
        cachedEformValue.pcStateManager.accessingField(CachedEformValue.pcInheritedFieldCount + 0);
        return cachedEformValue.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedEformValue cachedEformValue, final Integer caisiDemographicId) {
        if (cachedEformValue.pcStateManager == null) {
            cachedEformValue.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedEformValue.pcStateManager.settingObjectField((PersistenceCapable)cachedEformValue, CachedEformValue.pcInheritedFieldCount + 0, (Object)cachedEformValue.caisiDemographicId, (Object)caisiDemographicId, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityEformValuesPk(final CachedEformValue cachedEformValue) {
        if (cachedEformValue.pcStateManager == null) {
            return cachedEformValue.facilityEformValuesPk;
        }
        cachedEformValue.pcStateManager.accessingField(CachedEformValue.pcInheritedFieldCount + 1);
        return cachedEformValue.facilityEformValuesPk;
    }
    
    private static final void pcSetfacilityEformValuesPk(final CachedEformValue cachedEformValue, final FacilityIdIntegerCompositePk facilityEformValuesPk) {
        if (cachedEformValue.pcStateManager == null) {
            cachedEformValue.facilityEformValuesPk = facilityEformValuesPk;
            return;
        }
        cachedEformValue.pcStateManager.settingObjectField((PersistenceCapable)cachedEformValue, CachedEformValue.pcInheritedFieldCount + 1, (Object)cachedEformValue.facilityEformValuesPk, (Object)facilityEformValuesPk, 0);
    }
    
    private static final Integer pcGetformDataId(final CachedEformValue cachedEformValue) {
        if (cachedEformValue.pcStateManager == null) {
            return cachedEformValue.formDataId;
        }
        cachedEformValue.pcStateManager.accessingField(CachedEformValue.pcInheritedFieldCount + 2);
        return cachedEformValue.formDataId;
    }
    
    private static final void pcSetformDataId(final CachedEformValue cachedEformValue, final Integer formDataId) {
        if (cachedEformValue.pcStateManager == null) {
            cachedEformValue.formDataId = formDataId;
            return;
        }
        cachedEformValue.pcStateManager.settingObjectField((PersistenceCapable)cachedEformValue, CachedEformValue.pcInheritedFieldCount + 2, (Object)cachedEformValue.formDataId, (Object)formDataId, 0);
    }
    
    private static final Integer pcGetformId(final CachedEformValue cachedEformValue) {
        if (cachedEformValue.pcStateManager == null) {
            return cachedEformValue.formId;
        }
        cachedEformValue.pcStateManager.accessingField(CachedEformValue.pcInheritedFieldCount + 3);
        return cachedEformValue.formId;
    }
    
    private static final void pcSetformId(final CachedEformValue cachedEformValue, final Integer formId) {
        if (cachedEformValue.pcStateManager == null) {
            cachedEformValue.formId = formId;
            return;
        }
        cachedEformValue.pcStateManager.settingObjectField((PersistenceCapable)cachedEformValue, CachedEformValue.pcInheritedFieldCount + 3, (Object)cachedEformValue.formId, (Object)formId, 0);
    }
    
    private static final String pcGetvarName(final CachedEformValue cachedEformValue) {
        if (cachedEformValue.pcStateManager == null) {
            return cachedEformValue.varName;
        }
        cachedEformValue.pcStateManager.accessingField(CachedEformValue.pcInheritedFieldCount + 4);
        return cachedEformValue.varName;
    }
    
    private static final void pcSetvarName(final CachedEformValue cachedEformValue, final String varName) {
        if (cachedEformValue.pcStateManager == null) {
            cachedEformValue.varName = varName;
            return;
        }
        cachedEformValue.pcStateManager.settingStringField((PersistenceCapable)cachedEformValue, CachedEformValue.pcInheritedFieldCount + 4, cachedEformValue.varName, varName, 0);
    }
    
    private static final String pcGetvarValue(final CachedEformValue cachedEformValue) {
        if (cachedEformValue.pcStateManager == null) {
            return cachedEformValue.varValue;
        }
        cachedEformValue.pcStateManager.accessingField(CachedEformValue.pcInheritedFieldCount + 5);
        return cachedEformValue.varValue;
    }
    
    private static final void pcSetvarValue(final CachedEformValue cachedEformValue, final String varValue) {
        if (cachedEformValue.pcStateManager == null) {
            cachedEformValue.varValue = varValue;
            return;
        }
        cachedEformValue.pcStateManager.settingStringField((PersistenceCapable)cachedEformValue, CachedEformValue.pcInheritedFieldCount + 5, cachedEformValue.varValue, varValue, 0);
    }

    /**
     * Checks if this entity instance is in a detached state.
     *
     * <p>A detached entity has been removed from its persistence context but retains
     * its identity and state. This method returns a Boolean object rather than a primitive
     * to support tri-state logic: true (definitely detached), false (definitely not detached),
     * or null (detachment state is unknown).</p>
     *
     * @return Boolean true if detached, false if attached, null if state cannot be determined
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
     * Retrieves the detached state object for this entity instance.
     *
     * <p>The detached state contains information about the entity's persistence state
     * when it was detached from its persistence context, enabling reattachment and
     * change detection.</p>
     *
     * @return Object the detached state object, or null if the entity is not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object for this entity instance.
     *
     * <p>This method is called by the OpenJPA framework during detachment to store
     * the entity's persistence state information for later reattachment.</p>
     *
     * @param pcDetachedState Object the detached state to assign
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
