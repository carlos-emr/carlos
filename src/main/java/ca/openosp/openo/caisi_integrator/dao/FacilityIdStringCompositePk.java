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
 * Composite primary key for CAISI integrator facility mapping entities.
 *
 * <p>This embeddable class represents a composite primary key consisting of an integrator facility ID
 * and a CAISI item ID. It is used in the CAISI integrator system to uniquely identify healthcare
 * facility mappings across different EMR installations.</p>
 *
 * <p>The class is OpenJPA-enhanced for persistence capabilities, implementing both {@link Serializable}
 * and {@link PersistenceCapable} interfaces. The OpenJPA enhancement provides automatic field tracking,
 * state management, and dirty checking for JPA persistence operations.</p>
 *
 * <p>Both key fields are indexed for optimal query performance in facility lookup operations:</p>
 * <ul>
 *   <li>integratorFacilityId - Integer identifier for the integrator facility</li>
 *   <li>caisiItemId - String identifier (max 16 characters) for the CAISI item</li>
 * </ul>
 *
 * @see javax.persistence.Embeddable
 * @see org.apache.openjpa.enhance.PersistenceCapable
 * @since 2026-01-24
 */
@Embeddable
public class FacilityIdStringCompositePk implements Serializable, PersistenceCapable
{
    @Index
    private Integer integratorFacilityId;
    @Column(length = 16)
    @Index
    private String caisiItemId;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor creating an empty composite key.
     *
     * <p>Initializes both key fields to null. This constructor is required by JPA
     * for entity instantiation and OpenJPA enhancement.</p>
     */
    public FacilityIdStringCompositePk() {
        this.integratorFacilityId = null;
        this.caisiItemId = null;
    }

    /**
     * Constructs a composite key with specified facility and item identifiers.
     *
     * <p>Creates a new composite key initialized with the provided integrator facility ID
     * and CAISI item ID. This constructor is used when creating new facility mappings
     * in the integrator system.</p>
     *
     * @param integratorFacilityId Integer the integrator facility identifier
     * @param caisiItemId String the CAISI item identifier (max 16 characters)
     */
    public FacilityIdStringCompositePk(final Integer integratorFacilityId, final String caisiItemId) {
        this.integratorFacilityId = null;
        this.caisiItemId = null;
        this.integratorFacilityId = integratorFacilityId;
        this.caisiItemId = caisiItemId;
    }

    /**
     * Gets the integrator facility identifier.
     *
     * <p>This method is OpenJPA-enhanced to provide automatic field access tracking
     * through the StateManager for persistence operations.</p>
     *
     * @return Integer the integrator facility ID, or null if not set
     */
    public Integer getIntegratorFacilityId() {
        return pcGetintegratorFacilityId(this);
    }

    /**
     * Sets the integrator facility identifier.
     *
     * <p>This method is OpenJPA-enhanced to provide automatic dirty checking and
     * state management through the StateManager for persistence operations.</p>
     *
     * @param integratorFacilityId Integer the integrator facility ID to set
     */
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        pcSetintegratorFacilityId(this, integratorFacilityId);
    }

    /**
     * Gets the CAISI item identifier.
     *
     * <p>This method is OpenJPA-enhanced to provide automatic field access tracking
     * through the StateManager for persistence operations.</p>
     *
     * @return String the CAISI item ID (max 16 characters), or null if not set
     */
    public String getCaisiItemId() {
        return pcGetcaisiItemId(this);
    }

    /**
     * Sets the CAISI item identifier.
     *
     * <p>This method is OpenJPA-enhanced to provide automatic dirty checking and
     * state management. The input string is trimmed to null using Apache Commons
     * StringUtils to ensure consistent handling of empty strings.</p>
     *
     * @param caisiItemId String the CAISI item ID to set (max 16 characters), will be trimmed to null if empty
     */
    public void setCaisiItemId(final String caisiItemId) {
        pcSetcaisiItemId(this, StringUtils.trimToNull(caisiItemId));
    }

    /**
     * Returns a string representation of the composite key.
     *
     * <p>Format: "integratorFacilityId:caisiItemId"</p>
     *
     * @return String the composite key formatted as "facilityId:itemId"
     */
    @Override
    public String toString() {
        return "" + pcGetintegratorFacilityId(this) + ':' + pcGetcaisiItemId(this);
    }

    /**
     * Generates a hash code based on the CAISI item ID.
     *
     * <p>Uses only the caisiItemId field for hash code generation. This method
     * may throw NullPointerException if caisiItemId is null.</p>
     *
     * @return int the hash code of the CAISI item ID
     */
    @Override
    public int hashCode() {
        return pcGetcaisiItemId(this).hashCode();
    }

    /**
     * Compares this composite key with another object for equality.
     *
     * <p>Two composite keys are considered equal if both their integrator facility IDs
     * and CAISI item IDs are equal. Returns false if the comparison fails due to
     * type mismatch, null values, or any runtime exception.</p>
     *
     * @param o Object the object to compare with
     * @return boolean true if both keys are equal, false otherwise
     */
    @Override
    public boolean equals(final Object o) {
        try {
            final FacilityIdStringCompositePk o2 = (FacilityIdStringCompositePk)o;
            return pcGetintegratorFacilityId(this).equals(pcGetintegratorFacilityId(o2)) && pcGetcaisiItemId(this).equals(pcGetcaisiItemId(o2));
        }
        catch (final RuntimeException e) {
            return false;
        }
    }

    /**
     * Gets the OpenJPA enhancement contract version.
     *
     * <p>Returns the version of the OpenJPA enhancement contract this class implements.
     * This is used by OpenJPA to ensure compatibility between enhanced classes and the
     * persistence runtime.</p>
     *
     * @return int the enhancement contract version (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }

    /**
     * Static initializer for OpenJPA persistence capabilities.
     *
     * <p>Registers this class with the OpenJPA PCRegistry, defining field names, types,
     * and flags for persistence operations. This is automatically generated by the
     * OpenJPA enhancement process.</p>
     */
    static {
        serialVersionUID = -3604093556893062689L;
        FacilityIdStringCompositePk.pcFieldNames = new String[] { "caisiItemId", "integratorFacilityId" };
        FacilityIdStringCompositePk.pcFieldTypes = new Class[] { (FacilityIdStringCompositePk.class$Ljava$lang$String != null) ? FacilityIdStringCompositePk.class$Ljava$lang$String : (FacilityIdStringCompositePk.class$Ljava$lang$String = class$("java.lang.String")), (FacilityIdStringCompositePk.class$Ljava$lang$Integer != null) ? FacilityIdStringCompositePk.class$Ljava$lang$Integer : (FacilityIdStringCompositePk.class$Ljava$lang$Integer = class$("java.lang.Integer")) };
        FacilityIdStringCompositePk.pcFieldFlags = new byte[] { 26, 26 };
        PCRegistry.register((FacilityIdStringCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk != null) ? FacilityIdStringCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk : (FacilityIdStringCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdStringCompositePk")), FacilityIdStringCompositePk.pcFieldNames, FacilityIdStringCompositePk.pcFieldTypes, FacilityIdStringCompositePk.pcFieldFlags, FacilityIdStringCompositePk.pcPCSuperclass, (String)null, (PersistenceCapable)new FacilityIdStringCompositePk());
    }

    /**
     * Helper method for loading classes by name.
     *
     * <p>Used by OpenJPA enhancement to load field type classes dynamically.
     * Converts ClassNotFoundException to NoClassDefFoundError for enhanced
     * static initialization.</p>
     *
     * @param className String the fully qualified class name to load
     * @return Class the loaded class
     * @throws NoClassDefFoundError if the class cannot be found
     */
    static /* synthetic */ Class class$(final String className) {
        try {
            return Class.forName(className);
        }
        catch (final ClassNotFoundException ex) {
            throw new NoClassDefFoundError(ex.getMessage());
        }
    }

    /**
     * Clears all persistent fields to null.
     *
     * <p>This method is used by OpenJPA during entity initialization and detachment
     * operations to reset field values.</p>
     */
    protected void pcClearFields() {
        this.caisiItemId = null;
        this.integratorFacilityId = null;
    }

    /**
     * Creates a new instance with a StateManager and object ID.
     *
     * <p>This method is called by OpenJPA to create new managed instances during
     * persistence operations. The instance is initialized with the provided StateManager
     * and optionally has its fields cleared and key fields copied from the object ID.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object ID to copy key fields from
     * @param b boolean true to clear fields before initialization
     * @return PersistenceCapable the newly created managed instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final FacilityIdStringCompositePk facilityIdStringCompositePk = new FacilityIdStringCompositePk();
        if (b) {
            facilityIdStringCompositePk.pcClearFields();
        }
        facilityIdStringCompositePk.pcStateManager = pcStateManager;
        facilityIdStringCompositePk.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)facilityIdStringCompositePk;
    }

    /**
     * Creates a new instance with a StateManager.
     *
     * <p>This method is called by OpenJPA to create new managed instances during
     * persistence operations. The instance is initialized with the provided StateManager
     * and optionally has its fields cleared.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean true to clear fields before initialization
     * @return PersistenceCapable the newly created managed instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final FacilityIdStringCompositePk facilityIdStringCompositePk = new FacilityIdStringCompositePk();
        if (b) {
            facilityIdStringCompositePk.pcClearFields();
        }
        facilityIdStringCompositePk.pcStateManager = pcStateManager;
        return (PersistenceCapable)facilityIdStringCompositePk;
    }

    /**
     * Gets the count of managed persistent fields.
     *
     * <p>Returns the number of fields managed by OpenJPA for this entity.
     * This composite key has 2 managed fields: caisiItemId and integratorFacilityId.</p>
     *
     * @return int the number of managed fields (2)
     */
    protected static int pcGetManagedFieldCount() {
        return 2;
    }

    /**
     * Replaces a single field value from the StateManager.
     *
     * <p>This method is called by OpenJPA to replace a field's value during
     * entity loading and refresh operations. The field index is adjusted by
     * the inherited field count to determine which field to replace.</p>
     *
     * @param n int the absolute field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - FacilityIdStringCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiItemId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.integratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple field values from the StateManager.
     *
     * <p>This method is called by OpenJPA to replace multiple field values during
     * entity loading and refresh operations. It iterates through the provided field
     * indices and replaces each field.</p>
     *
     * @param array int[] array of absolute field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single field value to the StateManager.
     *
     * <p>This method is called by OpenJPA to provide a field's current value to
     * the StateManager during persistence operations such as flush and commit.
     * The field index is adjusted by the inherited field count to determine
     * which field to provide.</p>
     *
     * @param n int the absolute field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - FacilityIdStringCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.caisiItemId);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.integratorFacilityId);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Provides multiple field values to the StateManager.
     *
     * <p>This method is called by OpenJPA to provide multiple field values to
     * the StateManager during persistence operations. It iterates through the
     * provided field indices and provides each field value.</p>
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
     * <p>This method is called by OpenJPA to copy field values between instances
     * during merge and refresh operations. The field index is adjusted by the
     * inherited field count to determine which field to copy.</p>
     *
     * @param facilityIdStringCompositePk FacilityIdStringCompositePk the source instance to copy from
     * @param n int the absolute field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final FacilityIdStringCompositePk facilityIdStringCompositePk, final int n) {
        final int n2 = n - FacilityIdStringCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiItemId = facilityIdStringCompositePk.caisiItemId;
                return;
            }
            case 1: {
                this.integratorFacilityId = facilityIdStringCompositePk.integratorFacilityId;
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
     * <p>This method is called by OpenJPA to copy multiple field values between
     * instances during merge and refresh operations. Both instances must share
     * the same StateManager and have a non-null StateManager.</p>
     *
     * @param o Object the source instance to copy from
     * @param array int[] array of absolute field indices to copy
     * @throws IllegalArgumentException if the source has a different StateManager
     * @throws IllegalStateException if the StateManager is null
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final FacilityIdStringCompositePk facilityIdStringCompositePk = (FacilityIdStringCompositePk)o;
        if (facilityIdStringCompositePk.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(facilityIdStringCompositePk, array[i]);
        }
    }

    /**
     * Gets the generic context from the StateManager.
     *
     * <p>Returns the generic context object from the associated StateManager,
     * or null if there is no StateManager.</p>
     *
     * @return Object the generic context, or null if no StateManager
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the object ID from the StateManager.
     *
     * <p>Returns the object ID for this persistent instance, or null if there
     * is no StateManager.</p>
     *
     * @return Object the object ID, or null if no StateManager
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
     * @return boolean true if the instance is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this instance has been modified.
     *
     * <p>Performs a dirty check through the OpenJPA RedefinitionHelper to ensure
     * accurate state tracking before checking the StateManager's dirty flag.</p>
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
     * @return boolean true if the instance is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this instance is persistent.
     *
     * @return boolean true if the instance is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this instance is transactional.
     *
     * @return boolean true if the instance is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this instance is being serialized.
     *
     * @return boolean true if the instance is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a field as dirty.
     *
     * <p>Notifies the StateManager that a field has been modified. If there is
     * no StateManager, this method has no effect.</p>
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
     * Gets the associated StateManager.
     *
     * @return StateManager the state manager for this instance, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Gets the version object from the StateManager.
     *
     * <p>Returns the version object used for optimistic locking, or null if
     * there is no StateManager.</p>
     *
     * @return Object the version object, or null if no StateManager
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces the StateManager for this instance.
     *
     * <p>If a StateManager is already present, it delegates the replacement to
     * the existing StateManager. Otherwise, the new StateManager is assigned directly.</p>
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
     * Copies key fields to an object ID using a FieldSupplier.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract but is not
     * implemented for this embeddable composite key class.</p>
     *
     * @param fieldSupplier FieldSupplier the field supplier to use
     * @param o Object the target object ID
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
    }

    /**
     * Copies key fields to an object ID.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract but is not
     * implemented for this embeddable composite key class.</p>
     *
     * @param o Object the target object ID
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
    }

    /**
     * Copies key fields from an object ID using a FieldConsumer.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract but is not
     * implemented for this embeddable composite key class.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer to use
     * @param o Object the source object ID
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
    }

    /**
     * Copies key fields from an object ID.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract but is not
     * implemented for this embeddable composite key class.</p>
     *
     * @param o Object the source object ID
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
    }

    /**
     * Creates a new object ID instance.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract but returns
     * null for this embeddable composite key class.</p>
     *
     * @return Object always returns null
     */
    public Object pcNewObjectIdInstance() {
        return null;
    }

    /**
     * Creates a new object ID instance based on a source object.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable contract but returns
     * null for this embeddable composite key class.</p>
     *
     * @param o Object the source object
     * @return Object always returns null
     */
    public Object pcNewObjectIdInstance(final Object o) {
        return null;
    }

    /**
     * Static helper to get the CAISI item ID with StateManager tracking.
     *
     * <p>This OpenJPA-enhanced accessor notifies the StateManager when the field
     * is accessed, enabling proper lazy loading and change tracking.</p>
     *
     * @param facilityIdStringCompositePk FacilityIdStringCompositePk the instance to get the field from
     * @return String the CAISI item ID
     */
    private static final String pcGetcaisiItemId(final FacilityIdStringCompositePk facilityIdStringCompositePk) {
        if (facilityIdStringCompositePk.pcStateManager == null) {
            return facilityIdStringCompositePk.caisiItemId;
        }
        facilityIdStringCompositePk.pcStateManager.accessingField(FacilityIdStringCompositePk.pcInheritedFieldCount + 0);
        return facilityIdStringCompositePk.caisiItemId;
    }

    /**
     * Static helper to set the CAISI item ID with StateManager tracking.
     *
     * <p>This OpenJPA-enhanced mutator notifies the StateManager when the field
     * is modified, enabling proper dirty tracking and cascade operations.</p>
     *
     * @param facilityIdStringCompositePk FacilityIdStringCompositePk the instance to set the field on
     * @param caisiItemId String the new CAISI item ID value
     */
    private static final void pcSetcaisiItemId(final FacilityIdStringCompositePk facilityIdStringCompositePk, final String caisiItemId) {
        if (facilityIdStringCompositePk.pcStateManager == null) {
            facilityIdStringCompositePk.caisiItemId = caisiItemId;
            return;
        }
        facilityIdStringCompositePk.pcStateManager.settingStringField((PersistenceCapable)facilityIdStringCompositePk, FacilityIdStringCompositePk.pcInheritedFieldCount + 0, facilityIdStringCompositePk.caisiItemId, caisiItemId, 0);
    }

    /**
     * Static helper to get the integrator facility ID with StateManager tracking.
     *
     * <p>This OpenJPA-enhanced accessor notifies the StateManager when the field
     * is accessed, enabling proper lazy loading and change tracking.</p>
     *
     * @param facilityIdStringCompositePk FacilityIdStringCompositePk the instance to get the field from
     * @return Integer the integrator facility ID
     */
    private static final Integer pcGetintegratorFacilityId(final FacilityIdStringCompositePk facilityIdStringCompositePk) {
        if (facilityIdStringCompositePk.pcStateManager == null) {
            return facilityIdStringCompositePk.integratorFacilityId;
        }
        facilityIdStringCompositePk.pcStateManager.accessingField(FacilityIdStringCompositePk.pcInheritedFieldCount + 1);
        return facilityIdStringCompositePk.integratorFacilityId;
    }

    /**
     * Static helper to set the integrator facility ID with StateManager tracking.
     *
     * <p>This OpenJPA-enhanced mutator notifies the StateManager when the field
     * is modified, enabling proper dirty tracking and cascade operations.</p>
     *
     * @param facilityIdStringCompositePk FacilityIdStringCompositePk the instance to set the field on
     * @param integratorFacilityId Integer the new integrator facility ID value
     */
    private static final void pcSetintegratorFacilityId(final FacilityIdStringCompositePk facilityIdStringCompositePk, final Integer integratorFacilityId) {
        if (facilityIdStringCompositePk.pcStateManager == null) {
            facilityIdStringCompositePk.integratorFacilityId = integratorFacilityId;
            return;
        }
        facilityIdStringCompositePk.pcStateManager.settingObjectField((PersistenceCapable)facilityIdStringCompositePk, FacilityIdStringCompositePk.pcInheritedFieldCount + 1, (Object)facilityIdStringCompositePk.integratorFacilityId, (Object)integratorFacilityId, 0);
    }

    /**
     * Checks if this instance is in a detached state.
     *
     * <p>Returns a three-state Boolean indicating detachment status:</p>
     * <ul>
     *   <li>TRUE - Instance is definitely detached</li>
     *   <li>FALSE - Instance is definitely not detached</li>
     *   <li>null - Detachment status cannot be determined definitively</li>
     * </ul>
     *
     * @return Boolean the detachment state, or null if indeterminate
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
     * <p>Always returns false for this class, indicating that detachment status
     * cannot be determined definitively without a StateManager.</p>
     *
     * @return boolean always returns false
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Gets the detached state object.
     *
     * <p>The detached state tracks whether this instance was detached from a
     * persistence context and holds information needed for reattachment.</p>
     *
     * @return Object the detached state object
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object.
     *
     * <p>Used by OpenJPA to mark this instance as detached and store information
     * needed for reattachment to a persistence context.</p>
     *
     * @param pcDetachedState Object the detached state object to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method.
     *
     * <p>Handles serialization for this persistent object. If the instance is
     * being serialized as part of a persistence operation, the detached state
     * is cleared after writing the object.</p>
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
     * Custom deserialization method.
     *
     * <p>Handles deserialization for this persistent object. Sets the detached
     * state to DESERIALIZED to indicate that this instance was loaded from
     * serialization rather than from the database.</p>
     *
     * @param objectInputStream ObjectInputStream the stream to read from
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if a class cannot be found during deserialization
     */
    private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        this.pcSetDetachedState(PersistenceCapable.DESERIALIZED);
        objectInputStream.defaultReadObject();
    }
}
