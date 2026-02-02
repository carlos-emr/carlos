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
 * Cached measurement extension entity for the CAISI Integrator system.
 *
 * <p>This class represents extended measurement data cached from remote healthcare facilities
 * in the CAISI (Client Access to Integrated Services and Information) Integrator system.
 * It stores key-value pairs of additional measurement attributes that extend the core
 * measurement data, allowing flexible storage of facility-specific or measurement-specific
 * metadata.</p>
 *
 * <p>The entity uses OpenJPA persistence capabilities for optimized database operations and
 * supports multi-facility environments through a composite primary key that combines
 * facility identification with measurement extension identifiers.</p>
 *
 * <p><strong>Healthcare Context:</strong> In integrated healthcare environments, measurements
 * (such as vital signs, lab values, or clinical observations) may have facility-specific
 * attributes or extended metadata that need to be preserved when cached from remote systems.
 * This entity provides a flexible key-value storage mechanism for such extensions.</p>
 *
 * @see AbstractModel
 * @see FacilityIdIntegerCompositePk
 * @see PersistenceCapable
 * @since 2026-01-24
 */
@Entity
public class CachedMeasurementExt extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedMeasurementExt>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityMeasurementExtPk;
    @Column(nullable = false)
    @Index
    private Integer measurementId;
    @Column(nullable = false, length = 20)
    private String keyval;
    @Column(nullable = false, columnDefinition = "text")
    private String val;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementExt;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor initializing all fields to null.
     *
     * <p>Creates a new CachedMeasurementExt instance with all measurement extension
     * fields set to null values. The composite primary key and measurement reference
     * must be set separately before persisting the entity.</p>
     */
    public CachedMeasurementExt() {
        this.measurementId = null;
        this.keyval = null;
        this.val = null;
    }

    /**
     * Gets the composite primary key combining facility and measurement extension identifiers.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key, or null if not set
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityMeasurementExtPk(this);
    }

    /**
     * Sets the composite primary key combining facility and measurement extension identifiers.
     *
     * @param facilityMeasurementExtPk FacilityIdIntegerCompositePk the composite primary key to set
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityMeasurementExtPk) {
        pcSetfacilityMeasurementExtPk(this, facilityMeasurementExtPk);
    }

    /**
     * Gets the measurement identifier that this extension belongs to.
     *
     * @return Integer the measurement ID, or null if not set
     */
    public Integer getMeasurementId() {
        return pcGetmeasurementId(this);
    }

    /**
     * Sets the measurement identifier that this extension belongs to.
     *
     * @param measurementId Integer the measurement ID to set
     */
    public void setMeasurementId(final Integer measurementId) {
        pcSetmeasurementId(this, measurementId);
    }

    /**
     * Gets the key name of this measurement extension attribute.
     *
     * <p>The key represents the name or identifier of the extended attribute
     * (e.g., "unit", "notes", "lab_code"). Maximum length is 20 characters.</p>
     *
     * @return String the attribute key, or null if not set
     */
    public String getKeyval() {
        return pcGetkeyval(this);
    }

    /**
     * Sets the key name of this measurement extension attribute.
     *
     * <p>The input string is trimmed to remove leading and trailing whitespace.
     * If null, it is converted to an empty string.</p>
     *
     * @param keyval String the attribute key to set (maximum 20 characters)
     */
    public void setKeyval(final String keyval) {
        pcSetkeyval(this, StringUtils.trimToEmpty(keyval));
    }

    /**
     * Gets the value of this measurement extension attribute.
     *
     * <p>The value can be any text content associated with the key, such as
     * measurement units, notes, codes, or other facility-specific metadata.</p>
     *
     * @return String the attribute value, or null if not set
     */
    public String getVal() {
        return pcGetval(this);
    }

    /**
     * Sets the value of this measurement extension attribute.
     *
     * <p>The input string is trimmed to remove leading and trailing whitespace.
     * If null, it is converted to an empty string. Stored as TEXT type in database
     * to accommodate large values.</p>
     *
     * @param val String the attribute value to set
     */
    public void setVal(final String val) {
        pcSetval(this, StringUtils.trimToEmpty(val));
    }

    /**
     * Compares this cached measurement extension to another based on CAISI item ID.
     *
     * <p>The comparison is performed by subtracting the CAISI item IDs from the
     * composite primary keys of the two objects. This allows measurement extensions
     * to be sorted by their item identifiers.</p>
     *
     * @param o CachedMeasurementExt the measurement extension to compare to
     * @return int negative if this object's ID is less than the other, zero if equal,
     *         positive if greater
     */
    @Override
    public int compareTo(final CachedMeasurementExt o) {
        return pcGetfacilityMeasurementExtPk(this).getCaisiItemId() - pcGetfacilityMeasurementExtPk(o).getCaisiItemId();
    }

    /**
     * Gets the primary key identifier for this entity.
     *
     * <p>This method is required by the AbstractModel parent class and returns
     * the composite primary key containing both facility and measurement extension
     * identifiers.</p>
     *
     * @return FacilityIdIntegerCompositePk the composite primary key
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityMeasurementExtPk(this);
    }

    /**
     * Gets the OpenJPA enhancement contract version for this entity.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and returns
     * the version number of the bytecode enhancement applied to this class.</p>
     *
     * @return int the enhancement contract version (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 5357802782015682797L;
        CachedMeasurementExt.pcFieldNames = new String[] { "facilityMeasurementExtPk", "keyval", "measurementId", "val" };
        CachedMeasurementExt.pcFieldTypes = new Class[] { (CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedMeasurementExt.class$Ljava$lang$String != null) ? CachedMeasurementExt.class$Ljava$lang$String : (CachedMeasurementExt.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurementExt.class$Ljava$lang$Integer != null) ? CachedMeasurementExt.class$Ljava$lang$Integer : (CachedMeasurementExt.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedMeasurementExt.class$Ljava$lang$String != null) ? CachedMeasurementExt.class$Ljava$lang$String : (CachedMeasurementExt.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedMeasurementExt.pcFieldFlags = new byte[] { 26, 26, 26, 26 };
        PCRegistry.register((CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementExt != null) ? CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementExt : (CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementExt = class$("ca.openosp.openo.caisi_integrator.dao.CachedMeasurementExt")), CachedMeasurementExt.pcFieldNames, CachedMeasurementExt.pcFieldTypes, CachedMeasurementExt.pcFieldFlags, CachedMeasurementExt.pcPCSuperclass, "CachedMeasurementExt", (PersistenceCapable)new CachedMeasurementExt());
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
     * Clears all persistent fields to null values.
     *
     * <p>This protected method is used by OpenJPA to reset the entity's persistent
     * fields during various persistence lifecycle operations.</p>
     */
    protected void pcClearFields() {
        this.facilityMeasurementExtPk = null;
        this.keyval = null;
        this.measurementId = null;
        this.val = null;
    }

    /**
     * Creates a new instance with a state manager and object ID.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and is used
     * to create new instances during fetch operations from the database.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object ID to copy key fields from
     * @param b boolean if true, clears all fields before initializing
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedMeasurementExt cachedMeasurementExt = new CachedMeasurementExt();
        if (b) {
            cachedMeasurementExt.pcClearFields();
        }
        cachedMeasurementExt.pcStateManager = pcStateManager;
        cachedMeasurementExt.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedMeasurementExt;
    }

    /**
     * Creates a new instance with a state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract and is used
     * to create new instances during various persistence operations.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean if true, clears all fields before initializing
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedMeasurementExt cachedMeasurementExt = new CachedMeasurementExt();
        if (b) {
            cachedMeasurementExt.pcClearFields();
        }
        cachedMeasurementExt.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedMeasurementExt;
    }

    /**
     * Gets the number of managed fields in this entity.
     *
     * <p>This protected method returns the count of persistent fields managed by OpenJPA.
     * This entity has 4 managed fields: facilityMeasurementExtPk, keyval, measurementId, and val.</p>
     *
     * @return int the number of managed fields (4)
     */
    protected static int pcGetManagedFieldCount() {
        return 4;
    }

    /**
     * Replaces a single field value from the state manager.
     *
     * <p>This method is part of the OpenJPA field interception mechanism and is used
     * to restore field values during persistence operations.</p>
     *
     * @param n int the absolute field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedMeasurementExt.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityMeasurementExtPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.keyval = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.measurementId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.val = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
     * <p>This method is part of the OpenJPA field interception mechanism and processes
     * an array of field indices, replacing each field's value.</p>
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
     * <p>This method is part of the OpenJPA field interception mechanism and is used
     * to supply field values to the state manager during persistence operations.</p>
     *
     * @param n int the absolute field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedMeasurementExt.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityMeasurementExtPk);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.keyval);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.measurementId);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.val);
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
     * <p>This method is part of the OpenJPA field interception mechanism and processes
     * an array of field indices, providing each field's value to the state manager.</p>
     *
     * @param array int[] array of absolute field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }

    /**
     * Copies a single field value from another instance.
     *
     * <p>This protected method is used by OpenJPA to copy field values between
     * instances during various persistence operations.</p>
     *
     * @param cachedMeasurementExt CachedMeasurementExt the source instance to copy from
     * @param n int the absolute field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final CachedMeasurementExt cachedMeasurementExt, final int n) {
        final int n2 = n - CachedMeasurementExt.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityMeasurementExtPk = cachedMeasurementExt.facilityMeasurementExtPk;
                return;
            }
            case 1: {
                this.keyval = cachedMeasurementExt.keyval;
                return;
            }
            case 2: {
                this.measurementId = cachedMeasurementExt.measurementId;
                return;
            }
            case 3: {
                this.val = cachedMeasurementExt.val;
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
     * <p>This method is part of the OpenJPA field interception mechanism and processes
     * an array of field indices, copying each field's value from the source object.
     * Both instances must share the same state manager.</p>
     *
     * @param o Object the source object to copy from (must be a CachedMeasurementExt)
     * @param array int[] array of absolute field indices to copy
     * @throws IllegalArgumentException if the source object has a different state manager
     * @throws IllegalStateException if the state manager is null
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedMeasurementExt cachedMeasurementExt = (CachedMeasurementExt)o;
        if (cachedMeasurementExt.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedMeasurementExt, array[i]);
        }
    }

    /**
     * Gets the generic context from the state manager.
     *
     * @return Object the generic context, or null if no state manager is set
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
     * @return Object the object ID, or null if no state manager is set
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
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has been modified.
     *
     * @return boolean true if the entity is dirty (has unsaved changes), false otherwise
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
     * @return boolean true if the entity is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is persistent (managed by a persistence context).
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity is participating in a transaction.
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity is currently being serialized.
     *
     * @return boolean true if the entity is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a field as dirty (modified).
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
     * Gets the state manager for this entity.
     *
     * @return StateManager the state manager, or null if not set
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Gets the version identifier for optimistic locking.
     *
     * @return Object the version identifier, or null if no state manager is set
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
     * @param pcStateManager StateManager the new state manager to set
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
     * Copies key fields to an object ID using a field supplier.
     *
     * <p>This method is not supported for this entity type and will throw an exception.</p>
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
     * <p>This method is not supported for this entity type and will throw an exception.</p>
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
     * @param fieldConsumer FieldConsumer the field consumer to store the key field
     * @param o Object the source object ID to copy from
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(0 + CachedMeasurementExt.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key fields from an object ID directly to this instance.
     *
     * @param o Object the source object ID to copy from
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityMeasurementExtPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * <p>This method is not supported for this entity type and will throw an exception.</p>
     *
     * @param o Object the string representation to parse
     * @return Object never returns normally
     * @throws IllegalArgumentException always thrown as this constructor type is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedMeasurementExt\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance for this entity.
     *
     * @return Object the newly created object ID containing this entity's primary key
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementExt != null) ? CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementExt : (CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementExt = class$("ca.openosp.openo.caisi_integrator.dao.CachedMeasurementExt")), (Object)this.facilityMeasurementExtPk);
    }

    /**
     * Retrieves the composite primary key with state manager awareness.
     *
     * <p>This private static method handles field access interception for the primary key field,
     * notifying the state manager when the field is accessed.</p>
     *
     * @param cachedMeasurementExt CachedMeasurementExt the instance to get the key from
     * @return FacilityIdIntegerCompositePk the composite primary key
     */
    private static final FacilityIdIntegerCompositePk pcGetfacilityMeasurementExtPk(final CachedMeasurementExt cachedMeasurementExt) {
        if (cachedMeasurementExt.pcStateManager == null) {
            return cachedMeasurementExt.facilityMeasurementExtPk;
        }
        cachedMeasurementExt.pcStateManager.accessingField(CachedMeasurementExt.pcInheritedFieldCount + 0);
        return cachedMeasurementExt.facilityMeasurementExtPk;
    }

    /**
     * Sets the composite primary key with state manager awareness.
     *
     * <p>This private static method handles field modification interception for the primary key field,
     * notifying the state manager when the field is modified.</p>
     *
     * @param cachedMeasurementExt CachedMeasurementExt the instance to set the key on
     * @param facilityMeasurementExtPk FacilityIdIntegerCompositePk the composite primary key to set
     */
    private static final void pcSetfacilityMeasurementExtPk(final CachedMeasurementExt cachedMeasurementExt, final FacilityIdIntegerCompositePk facilityMeasurementExtPk) {
        if (cachedMeasurementExt.pcStateManager == null) {
            cachedMeasurementExt.facilityMeasurementExtPk = facilityMeasurementExtPk;
            return;
        }
        cachedMeasurementExt.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurementExt, CachedMeasurementExt.pcInheritedFieldCount + 0, (Object)cachedMeasurementExt.facilityMeasurementExtPk, (Object)facilityMeasurementExtPk, 0);
    }

    /**
     * Retrieves the key value with state manager awareness.
     *
     * <p>This private static method handles field access interception for the keyval field,
     * notifying the state manager when the field is accessed.</p>
     *
     * @param cachedMeasurementExt CachedMeasurementExt the instance to get the key from
     * @return String the key value
     */
    private static final String pcGetkeyval(final CachedMeasurementExt cachedMeasurementExt) {
        if (cachedMeasurementExt.pcStateManager == null) {
            return cachedMeasurementExt.keyval;
        }
        cachedMeasurementExt.pcStateManager.accessingField(CachedMeasurementExt.pcInheritedFieldCount + 1);
        return cachedMeasurementExt.keyval;
    }

    /**
     * Sets the key value with state manager awareness.
     *
     * <p>This private static method handles field modification interception for the keyval field,
     * notifying the state manager when the field is modified.</p>
     *
     * @param cachedMeasurementExt CachedMeasurementExt the instance to set the key on
     * @param keyval String the key value to set
     */
    private static final void pcSetkeyval(final CachedMeasurementExt cachedMeasurementExt, final String keyval) {
        if (cachedMeasurementExt.pcStateManager == null) {
            cachedMeasurementExt.keyval = keyval;
            return;
        }
        cachedMeasurementExt.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurementExt, CachedMeasurementExt.pcInheritedFieldCount + 1, cachedMeasurementExt.keyval, keyval, 0);
    }

    /**
     * Retrieves the measurement ID with state manager awareness.
     *
     * <p>This private static method handles field access interception for the measurementId field,
     * notifying the state manager when the field is accessed.</p>
     *
     * @param cachedMeasurementExt CachedMeasurementExt the instance to get the measurement ID from
     * @return Integer the measurement ID
     */
    private static final Integer pcGetmeasurementId(final CachedMeasurementExt cachedMeasurementExt) {
        if (cachedMeasurementExt.pcStateManager == null) {
            return cachedMeasurementExt.measurementId;
        }
        cachedMeasurementExt.pcStateManager.accessingField(CachedMeasurementExt.pcInheritedFieldCount + 2);
        return cachedMeasurementExt.measurementId;
    }

    /**
     * Sets the measurement ID with state manager awareness.
     *
     * <p>This private static method handles field modification interception for the measurementId field,
     * notifying the state manager when the field is modified.</p>
     *
     * @param cachedMeasurementExt CachedMeasurementExt the instance to set the measurement ID on
     * @param measurementId Integer the measurement ID to set
     */
    private static final void pcSetmeasurementId(final CachedMeasurementExt cachedMeasurementExt, final Integer measurementId) {
        if (cachedMeasurementExt.pcStateManager == null) {
            cachedMeasurementExt.measurementId = measurementId;
            return;
        }
        cachedMeasurementExt.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurementExt, CachedMeasurementExt.pcInheritedFieldCount + 2, (Object)cachedMeasurementExt.measurementId, (Object)measurementId, 0);
    }

    /**
     * Retrieves the value with state manager awareness.
     *
     * <p>This private static method handles field access interception for the val field,
     * notifying the state manager when the field is accessed.</p>
     *
     * @param cachedMeasurementExt CachedMeasurementExt the instance to get the value from
     * @return String the value
     */
    private static final String pcGetval(final CachedMeasurementExt cachedMeasurementExt) {
        if (cachedMeasurementExt.pcStateManager == null) {
            return cachedMeasurementExt.val;
        }
        cachedMeasurementExt.pcStateManager.accessingField(CachedMeasurementExt.pcInheritedFieldCount + 3);
        return cachedMeasurementExt.val;
    }

    /**
     * Sets the value with state manager awareness.
     *
     * <p>This private static method handles field modification interception for the val field,
     * notifying the state manager when the field is modified.</p>
     *
     * @param cachedMeasurementExt CachedMeasurementExt the instance to set the value on
     * @param val String the value to set
     */
    private static final void pcSetval(final CachedMeasurementExt cachedMeasurementExt, final String val) {
        if (cachedMeasurementExt.pcStateManager == null) {
            cachedMeasurementExt.val = val;
            return;
        }
        cachedMeasurementExt.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurementExt, CachedMeasurementExt.pcInheritedFieldCount + 3, cachedMeasurementExt.val, val, 0);
    }

    /**
     * Checks if this entity is in a detached state.
     *
     * <p>A detached entity is one that was previously managed by a persistence context
     * but is no longer associated with it. This method returns a Boolean (not boolean)
     * to allow for three states: true (definitely detached), false (definitely not detached),
     * or null (cannot determine).</p>
     *
     * @return Boolean true if detached, false if attached, null if state is indeterminate
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
     * <p>This private method returns false, indicating that the presence or absence
     * of detached state alone is not sufficient to definitively determine if the
     * entity is detached.</p>
     *
     * @return boolean always returns false
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Gets the detached state object.
     *
     * @return Object the detached state, or null if not set
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object.
     *
     * @param pcDetachedState Object the detached state to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method for writing this object to a stream.
     *
     * <p>This method handles the serialization of the entity, clearing the detached
     * state if the entity is currently being serialized by the persistence framework.</p>
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
     * Custom deserialization method for reading this object from a stream.
     *
     * <p>This method handles the deserialization of the entity, setting the detached
     * state to DESERIALIZED to indicate that the entity was loaded from serialization
     * rather than from the database.</p>
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
