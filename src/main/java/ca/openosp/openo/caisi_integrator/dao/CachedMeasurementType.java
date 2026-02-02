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
 * Cached measurement type entity for the CAISI Integrator system.
 *
 * This JPA entity represents a cached copy of measurement type definitions from remote facilities
 * in the CAISI (Collaborative Application for Integrated Services Information) integrator context.
 * Measurement types define clinical measurements such as vital signs (blood pressure, temperature,
 * weight, height), laboratory values, and other quantifiable health data points.
 *
 * The caching mechanism improves performance by storing remote facility measurement type definitions
 * locally, reducing the need for repeated remote queries when displaying or processing clinical
 * measurements from integrated healthcare facilities.
 *
 * This class is enhanced by OpenJPA for persistence capabilities, implementing the PersistenceCapable
 * interface to support advanced ORM features including detached state management, field-level access
 * tracking, and transparent persistence operations.
 *
 * @see FacilityIdIntegerCompositePk
 * @see AbstractModel
 * @see PersistenceCapable
 * @since 2024-01-24
 */
@Entity
public class CachedMeasurementType extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedMeasurementType>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityMeasurementTypePk;
    @Column(nullable = false, length = 4)
    @Index
    private String type;
    @Column(nullable = false, length = 255)
    private String typeDescription;
    @Column(nullable = false, length = 255)
    private String measuringInstruction;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementType;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor initializing measurement type fields to null.
     *
     * Creates a new CachedMeasurementType instance with all fields set to their default null values.
     * This constructor is required by JPA for entity instantiation during persistence operations.
     */
    public CachedMeasurementType() {
        this.type = null;
        this.typeDescription = null;
        this.measuringInstruction = null;
    }

    /**
     * Retrieves the composite primary key for this cached measurement type.
     *
     * The composite key consists of both the integrator facility ID and the CAISI item ID,
     * uniquely identifying this measurement type within a specific remote facility context.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key containing facility and item identifiers
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityMeasurementTypePk(this);
    }

    /**
     * Sets the composite primary key for this cached measurement type.
     *
     * @param facilityMeasurementTypePk FacilityIdIntegerCompositePk the composite key to assign,
     *                                   containing both facility ID and measurement type item ID
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityMeasurementTypePk) {
        pcSetfacilityMeasurementTypePk(this, facilityMeasurementTypePk);
    }

    /**
     * Retrieves the measurement type code.
     *
     * The type code is a short identifier (maximum 4 characters) used to uniquely identify
     * the kind of clinical measurement (e.g., "BP" for blood pressure, "WT" for weight).
     *
     * @return String the measurement type code, or null if not set
     */
    public String getType() {
        return pcGettype(this);
    }

    /**
     * Sets the measurement type code.
     *
     * The input string is automatically trimmed of leading and trailing whitespace.
     * Empty strings are converted to empty strings (not null).
     *
     * @param type String the measurement type code to assign (maximum 4 characters)
     */
    public void setType(final String type) {
        pcSettype(this, StringUtils.trimToEmpty(type));
    }

    /**
     * Retrieves the human-readable description of the measurement type.
     *
     * This provides a full descriptive name for the measurement type, such as
     * "Blood Pressure" or "Body Weight", used for display in clinical interfaces.
     *
     * @return String the measurement type description (maximum 255 characters), or null if not set
     */
    public String getTypeDescription() {
        return pcGettypeDescription(this);
    }

    /**
     * Sets the human-readable description of the measurement type.
     *
     * The input string is automatically trimmed of leading and trailing whitespace.
     * Empty strings are converted to empty strings (not null).
     *
     * @param typeDescription String the measurement type description to assign (maximum 255 characters)
     */
    public void setTypeDescription(final String typeDescription) {
        pcSettypeDescription(this, StringUtils.trimToEmpty(typeDescription));
    }

    /**
     * Retrieves the instructions for taking this measurement.
     *
     * Provides clinical guidance on how to properly perform or record this measurement,
     * such as patient positioning, equipment usage, or special considerations.
     *
     * @return String the measuring instructions (maximum 255 characters), or null if not set
     */
    public String getMeasuringInstruction() {
        return pcGetmeasuringInstruction(this);
    }

    /**
     * Sets the instructions for taking this measurement.
     *
     * The input string is automatically trimmed of leading and trailing whitespace.
     * Empty strings are converted to empty strings (not null).
     *
     * @param measuringInstruction String the measuring instructions to assign (maximum 255 characters)
     */
    public void setMeasuringInstruction(final String measuringInstruction) {
        pcSetmeasuringInstruction(this, StringUtils.trimToEmpty(measuringInstruction));
    }

    /**
     * Compares this cached measurement type to another for natural ordering.
     *
     * The comparison is based on the CAISI item ID component of the composite primary key,
     * allowing measurement types to be sorted by their item identifiers.
     *
     * @param o CachedMeasurementType the measurement type to compare against
     * @return int negative if this item ID is less than o's item ID, zero if equal,
     *             positive if this item ID is greater than o's item ID
     */
    @Override
    public int compareTo(final CachedMeasurementType o) {
        return pcGetfacilityMeasurementTypePk(this).getCaisiItemId() - pcGetfacilityMeasurementTypePk(o).getCaisiItemId();
    }

    /**
     * Retrieves the entity identifier.
     *
     * This method satisfies the AbstractModel contract by returning the composite primary key
     * that uniquely identifies this cached measurement type entity.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityMeasurementTypePk(this);
    }

    /**
     * Returns the OpenJPA bytecode enhancement contract version.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates
     * which version of the enhancement specification this class implements.
     *
     * @return int the enhancement contract version number (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -3892680324771808367L;
        CachedMeasurementType.pcFieldNames = new String[] { "facilityMeasurementTypePk", "measuringInstruction", "type", "typeDescription" };
        CachedMeasurementType.pcFieldTypes = new Class[] { (CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedMeasurementType.class$Ljava$lang$String != null) ? CachedMeasurementType.class$Ljava$lang$String : (CachedMeasurementType.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurementType.class$Ljava$lang$String != null) ? CachedMeasurementType.class$Ljava$lang$String : (CachedMeasurementType.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurementType.class$Ljava$lang$String != null) ? CachedMeasurementType.class$Ljava$lang$String : (CachedMeasurementType.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedMeasurementType.pcFieldFlags = new byte[] { 26, 26, 26, 26 };
        PCRegistry.register((CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementType != null) ? CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementType : (CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementType = class$("ca.openosp.openo.caisi_integrator.dao.CachedMeasurementType")), CachedMeasurementType.pcFieldNames, CachedMeasurementType.pcFieldTypes, CachedMeasurementType.pcFieldFlags, CachedMeasurementType.pcPCSuperclass, "CachedMeasurementType", (PersistenceCapable)new CachedMeasurementType());
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
     * This method is part of the OpenJPA PersistenceCapable contract and is used internally
     * by the persistence framework to reset field values during entity lifecycle operations.
     */
    protected void pcClearFields() {
        this.facilityMeasurementTypePk = null;
        this.measuringInstruction = null;
        this.type = null;
        this.typeDescription = null;
    }

    /**
     * Creates a new instance with a StateManager and object ID.
     *
     * This factory method is part of the OpenJPA PersistenceCapable contract and creates
     * new instances during persistence operations, optionally clearing fields and copying
     * key field values from the provided object ID.
     *
     * @param pcStateManager StateManager the state manager to assign to the new instance
     * @param o Object the object ID from which to copy key field values
     * @param b boolean whether to clear fields after instantiation
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedMeasurementType cachedMeasurementType = new CachedMeasurementType();
        if (b) {
            cachedMeasurementType.pcClearFields();
        }
        cachedMeasurementType.pcStateManager = pcStateManager;
        cachedMeasurementType.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedMeasurementType;
    }

    /**
     * Creates a new instance with a StateManager.
     *
     * This factory method is part of the OpenJPA PersistenceCapable contract and creates
     * new instances during persistence operations, optionally clearing fields.
     *
     * @param pcStateManager StateManager the state manager to assign to the new instance
     * @param b boolean whether to clear fields after instantiation
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedMeasurementType cachedMeasurementType = new CachedMeasurementType();
        if (b) {
            cachedMeasurementType.pcClearFields();
        }
        cachedMeasurementType.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedMeasurementType;
    }

    /**
     * Returns the number of managed fields in this persistent class.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates
     * how many fields are tracked by the persistence framework.
     *
     * @return int the count of managed fields (4)
     */
    protected static int pcGetManagedFieldCount() {
        return 4;
    }

    /**
     * Replaces a single field value with a value from the state manager.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used during
     * detachment, attachment, and refresh operations to synchronize field values with
     * the persistence context.
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedMeasurementType.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityMeasurementTypePk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.measuringInstruction = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.type = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.typeDescription = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple field values with values from the state manager.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and efficiently
     * replaces multiple fields in a single operation during detachment, attachment,
     * or refresh operations.
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single field value to the state manager.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used during
     * persistence operations to supply current field values to the persistence context.
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedMeasurementType.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityMeasurementTypePk);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.measuringInstruction);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.type);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.typeDescription);
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
     * This method is part of the OpenJPA PersistenceCapable contract and efficiently
     * provides multiple field values in a single operation during persistence operations.
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }

    /**
     * Copies a single field value from another instance.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used during
     * merge and copy operations to transfer field values between entity instances.
     *
     * @param cachedMeasurementType CachedMeasurementType the source instance to copy from
     * @param n int the field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final CachedMeasurementType cachedMeasurementType, final int n) {
        final int n2 = n - CachedMeasurementType.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityMeasurementTypePk = cachedMeasurementType.facilityMeasurementTypePk;
                return;
            }
            case 1: {
                this.measuringInstruction = cachedMeasurementType.measuringInstruction;
                return;
            }
            case 2: {
                this.type = cachedMeasurementType.type;
                return;
            }
            case 3: {
                this.typeDescription = cachedMeasurementType.typeDescription;
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
     * This method is part of the OpenJPA PersistenceCapable contract and efficiently
     * copies multiple field values in a single operation during merge and copy operations.
     *
     * @param o Object the source instance to copy from (must be a CachedMeasurementType)
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source instance has a different state manager
     * @throws IllegalStateException if this instance has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedMeasurementType cachedMeasurementType = (CachedMeasurementType)o;
        if (cachedMeasurementType.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedMeasurementType, array[i]);
        }
    }

    /**
     * Retrieves the generic context from the state manager.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and provides
     * access to framework-specific context information.
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
     *
     * This method is part of the OpenJPA PersistenceCapable contract and retrieves
     * the unique identifier assigned by the persistence framework.
     *
     * @return Object the object ID, or null if no state manager is present or instance is not persistent
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks if this instance is marked for deletion.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates
     * whether the entity has been deleted in the current transaction.
     *
     * @return boolean true if the instance is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this instance has been modified.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates
     * whether any fields have been changed since the last persistence synchronization.
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
     * Checks if this instance is newly created.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates
     * whether the entity is newly created and not yet committed to the database.
     *
     * @return boolean true if the instance is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this instance is persistent.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates
     * whether the entity is managed by a persistence context (either new, modified, or synchronized).
     *
     * @return boolean true if the instance is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this instance is part of a transaction.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates
     * whether the entity is participating in an active transaction.
     *
     * @return boolean true if the instance is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this instance is being serialized.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates
     * whether the entity is currently undergoing serialization.
     *
     * @return boolean true if the instance is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a field as dirty.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and notifies
     * the state manager that a field has been modified, triggering change tracking.
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
     * Retrieves the state manager for this persistent instance.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and provides
     * access to the state manager responsible for tracking this entity's lifecycle.
     *
     * @return StateManager the state manager, or null if the instance is not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version information for this persistent instance.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and provides
     * access to optimistic locking version data if versioning is enabled.
     *
     * @return Object the version information, or null if no state manager is present or versioning is disabled
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
     * This method is part of the OpenJPA PersistenceCapable contract and handles
     * state manager transitions during entity lifecycle operations such as detachment
     * or attachment to a different persistence context.
     *
     * @param pcStateManager StateManager the new state manager to assign
     * @throws SecurityException if a security violation occurs during state manager replacement
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
     * This method is part of the OpenJPA PersistenceCapable contract but is not implemented
     * for this entity type, which uses a composite primary key structure.
     *
     * @param fieldSupplier FieldSupplier the field supplier (unused)
     * @param o Object the target object ID (unused)
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key field values to an object ID.
     *
     * This method is part of the OpenJPA PersistenceCapable contract but is not implemented
     * for this entity type, which uses a composite primary key structure.
     *
     * @param o Object the target object ID (unused)
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key field values from an object ID using a field consumer.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and populates
     * the composite primary key field from the provided object ID during entity instantiation.
     *
     * @param fieldConsumer FieldConsumer the field consumer to receive field values
     * @param o Object the source object ID containing the composite key
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(0 + CachedMeasurementType.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key field values from an object ID.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and extracts
     * the composite primary key from the provided object ID during entity instantiation.
     *
     * @param o Object the source object ID containing the composite key
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityMeasurementTypePk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * This method is part of the OpenJPA PersistenceCapable contract but is not supported
     * for this entity type because the ObjectId class does not have the required constructor.
     *
     * @param o Object the string representation (unused)
     * @return Object not returned as this method always throws an exception
     * @throws IllegalArgumentException always thrown indicating this operation is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedMeasurementType\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance using this entity's current key field values.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and constructs
     * an ObjectId wrapping the composite primary key.
     *
     * @return Object a new ObjectId instance containing this entity's composite key
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementType != null) ? CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementType : (CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementType = class$("ca.openosp.openo.caisi_integrator.dao.CachedMeasurementType")), (Object)this.facilityMeasurementTypePk);
    }

    /**
     * Internal accessor for the composite primary key field with state manager tracking.
     *
     * This method is part of the OpenJPA bytecode enhancement and handles field access
     * interception to notify the state manager when the field is read.
     *
     * @param cachedMeasurementType CachedMeasurementType the instance to read from
     * @return FacilityIdIntegerCompositePk the composite primary key value
     */
    private static final FacilityIdIntegerCompositePk pcGetfacilityMeasurementTypePk(final CachedMeasurementType cachedMeasurementType) {
        if (cachedMeasurementType.pcStateManager == null) {
            return cachedMeasurementType.facilityMeasurementTypePk;
        }
        cachedMeasurementType.pcStateManager.accessingField(CachedMeasurementType.pcInheritedFieldCount + 0);
        return cachedMeasurementType.facilityMeasurementTypePk;
    }

    /**
     * Internal mutator for the composite primary key field with state manager tracking.
     *
     * This method is part of the OpenJPA bytecode enhancement and handles field modification
     * interception to notify the state manager when the field is written.
     *
     * @param cachedMeasurementType CachedMeasurementType the instance to write to
     * @param facilityMeasurementTypePk FacilityIdIntegerCompositePk the new composite key value
     */
    private static final void pcSetfacilityMeasurementTypePk(final CachedMeasurementType cachedMeasurementType, final FacilityIdIntegerCompositePk facilityMeasurementTypePk) {
        if (cachedMeasurementType.pcStateManager == null) {
            cachedMeasurementType.facilityMeasurementTypePk = facilityMeasurementTypePk;
            return;
        }
        cachedMeasurementType.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurementType, CachedMeasurementType.pcInheritedFieldCount + 0, (Object)cachedMeasurementType.facilityMeasurementTypePk, (Object)facilityMeasurementTypePk, 0);
    }

    /**
     * Internal accessor for the measuring instruction field with state manager tracking.
     *
     * This method is part of the OpenJPA bytecode enhancement and handles field access
     * interception to notify the state manager when the field is read.
     *
     * @param cachedMeasurementType CachedMeasurementType the instance to read from
     * @return String the measuring instruction value
     */
    private static final String pcGetmeasuringInstruction(final CachedMeasurementType cachedMeasurementType) {
        if (cachedMeasurementType.pcStateManager == null) {
            return cachedMeasurementType.measuringInstruction;
        }
        cachedMeasurementType.pcStateManager.accessingField(CachedMeasurementType.pcInheritedFieldCount + 1);
        return cachedMeasurementType.measuringInstruction;
    }

    /**
     * Internal mutator for the measuring instruction field with state manager tracking.
     *
     * This method is part of the OpenJPA bytecode enhancement and handles field modification
     * interception to notify the state manager when the field is written.
     *
     * @param cachedMeasurementType CachedMeasurementType the instance to write to
     * @param measuringInstruction String the new measuring instruction value
     */
    private static final void pcSetmeasuringInstruction(final CachedMeasurementType cachedMeasurementType, final String measuringInstruction) {
        if (cachedMeasurementType.pcStateManager == null) {
            cachedMeasurementType.measuringInstruction = measuringInstruction;
            return;
        }
        cachedMeasurementType.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurementType, CachedMeasurementType.pcInheritedFieldCount + 1, cachedMeasurementType.measuringInstruction, measuringInstruction, 0);
    }

    /**
     * Internal accessor for the type field with state manager tracking.
     *
     * This method is part of the OpenJPA bytecode enhancement and handles field access
     * interception to notify the state manager when the field is read.
     *
     * @param cachedMeasurementType CachedMeasurementType the instance to read from
     * @return String the type code value
     */
    private static final String pcGettype(final CachedMeasurementType cachedMeasurementType) {
        if (cachedMeasurementType.pcStateManager == null) {
            return cachedMeasurementType.type;
        }
        cachedMeasurementType.pcStateManager.accessingField(CachedMeasurementType.pcInheritedFieldCount + 2);
        return cachedMeasurementType.type;
    }

    /**
     * Internal mutator for the type field with state manager tracking.
     *
     * This method is part of the OpenJPA bytecode enhancement and handles field modification
     * interception to notify the state manager when the field is written.
     *
     * @param cachedMeasurementType CachedMeasurementType the instance to write to
     * @param type String the new type code value
     */
    private static final void pcSettype(final CachedMeasurementType cachedMeasurementType, final String type) {
        if (cachedMeasurementType.pcStateManager == null) {
            cachedMeasurementType.type = type;
            return;
        }
        cachedMeasurementType.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurementType, CachedMeasurementType.pcInheritedFieldCount + 2, cachedMeasurementType.type, type, 0);
    }

    /**
     * Internal accessor for the type description field with state manager tracking.
     *
     * This method is part of the OpenJPA bytecode enhancement and handles field access
     * interception to notify the state manager when the field is read.
     *
     * @param cachedMeasurementType CachedMeasurementType the instance to read from
     * @return String the type description value
     */
    private static final String pcGettypeDescription(final CachedMeasurementType cachedMeasurementType) {
        if (cachedMeasurementType.pcStateManager == null) {
            return cachedMeasurementType.typeDescription;
        }
        cachedMeasurementType.pcStateManager.accessingField(CachedMeasurementType.pcInheritedFieldCount + 3);
        return cachedMeasurementType.typeDescription;
    }

    /**
     * Internal mutator for the type description field with state manager tracking.
     *
     * This method is part of the OpenJPA bytecode enhancement and handles field modification
     * interception to notify the state manager when the field is written.
     *
     * @param cachedMeasurementType CachedMeasurementType the instance to write to
     * @param typeDescription String the new type description value
     */
    private static final void pcSettypeDescription(final CachedMeasurementType cachedMeasurementType, final String typeDescription) {
        if (cachedMeasurementType.pcStateManager == null) {
            cachedMeasurementType.typeDescription = typeDescription;
            return;
        }
        cachedMeasurementType.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurementType, CachedMeasurementType.pcInheritedFieldCount + 3, cachedMeasurementType.typeDescription, typeDescription, 0);
    }

    /**
     * Checks if this instance is in a detached state.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and determines
     * whether the entity has been detached from its persistence context. The return
     * value is a Boolean object that can be null, true, or false to indicate uncertain,
     * definitively detached, or definitively attached states respectively.
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

    /**
     * Determines if the detached state is definitive.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and indicates
     * whether the detached state can be reliably determined from available information.
     *
     * @return boolean always returns false, indicating detached state is not definitive for this entity
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Retrieves the detached state information.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and provides
     * access to metadata about the entity's detached state, used during reattachment
     * operations to determine what has changed.
     *
     * @return Object the detached state metadata, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state information.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and stores
     * metadata about the entity's detached state for use during reattachment operations.
     *
     * @param pcDetachedState Object the detached state metadata to store
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method for writing this object to an output stream.
     *
     * This method is part of the Java serialization contract and handles special
     * serialization logic for OpenJPA-enhanced entities, including managing detached
     * state during the serialization process.
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
     * This method is part of the Java serialization contract and handles special
     * deserialization logic for OpenJPA-enhanced entities, marking the instance as
     * deserialized for proper detached state management.
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
