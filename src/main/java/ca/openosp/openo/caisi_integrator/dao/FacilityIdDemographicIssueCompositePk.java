package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PCRegistry;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import ca.openosp.openo.caisi_integrator.util.CodeType;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Embeddable;
import org.apache.openjpa.enhance.PersistenceCapable;
import java.io.Serializable;

/**
 * Composite primary key for facility-demographic-issue associations in the CAISI Integrator system.
 *
 * <p>This embeddable class represents a unique composite key that identifies a specific medical issue
 * or health condition associated with a patient (demographic) at a particular healthcare facility
 * within the CAISI (Client Access to Integrated Services and Information) Integrator framework.
 * The composite key ensures that each combination of facility, patient, code type, and issue code
 * is unique across the integrated healthcare system.</p>
 *
 * <p>The class is enhanced by Apache OpenJPA for persistence capabilities, implementing the
 * PersistenceCapable interface to support JPA entity management. All OpenJPA-generated methods
 * (prefixed with 'pc') are used internally by the persistence framework for state management,
 * field access tracking, and object lifecycle operations.</p>
 *
 * <p><strong>Key Components:</strong></p>
 * <ul>
 *   <li><strong>integratorFacilityId</strong> - Identifies the healthcare facility in the integrator system</li>
 *   <li><strong>caisiDemographicId</strong> - Identifies the patient/demographic record</li>
 *   <li><strong>codeType</strong> - The medical coding system used (ICD9, ICD10, SNOMED, etc.)</li>
 *   <li><strong>issueCode</strong> - The specific medical code within the coding system</li>
 * </ul>
 *
 * <p><strong>Healthcare Context:</strong></p>
 * <p>In Canadian healthcare systems, the CAISI Integrator enables data sharing and integration
 * across multiple healthcare facilities. This composite key structure supports tracking of various
 * medical issues (diagnoses, preventions, medications) using different standardized coding systems,
 * ensuring data consistency and preventing duplicate entries across the integrated network.</p>
 *
 * @see ca.openosp.openo.caisi_integrator.util.CodeType
 * @see org.apache.openjpa.enhance.PersistenceCapable
 *
 * @since 2026-01-24
 */
@Embeddable
public class FacilityIdDemographicIssueCompositePk implements Serializable, PersistenceCapable
{
    @Index
    private Integer integratorFacilityId;
    @Index
    private Integer caisiDemographicId;
    @Enumerated(EnumType.STRING)
    @Column(length = 64, nullable = false)
    private CodeType codeType;
    @Column(length = 64, nullable = false)
    private String issueCode;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$util$CodeType;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdDemographicIssueCompositePk;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor that initializes all composite key fields to null.
     *
     * <p>This no-argument constructor is required by JPA for entity instantiation
     * and is used by the persistence framework during object creation and deserialization.</p>
     */
    public FacilityIdDemographicIssueCompositePk() {
        this.integratorFacilityId = null;
        this.caisiDemographicId = null;
        this.codeType = null;
        this.issueCode = null;
    }

    /**
     * Retrieves the integrator facility identifier.
     *
     * <p>The facility ID identifies which healthcare facility within the CAISI Integrator
     * network this issue record belongs to. This enables multi-facility data integration
     * while maintaining facility-specific patient issue tracking.</p>
     *
     * @return Integer the unique identifier of the healthcare facility in the integrator system,
     *         or null if not set
     */
    public Integer getIntegratorFacilityId() {
        return pcGetintegratorFacilityId(this);
    }

    /**
     * Sets the integrator facility identifier.
     *
     * @param integratorFacilityId Integer the unique identifier of the healthcare facility
     *        in the integrator system
     */
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        pcSetintegratorFacilityId(this, integratorFacilityId);
    }

    /**
     * Retrieves the CAISI demographic identifier.
     *
     * <p>The demographic ID uniquely identifies the patient within the CAISI system.
     * This links the medical issue to a specific patient's healthcare record.</p>
     *
     * @return Integer the unique identifier of the patient/demographic record,
     *         or null if not set
     */
    public Integer getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }

    /**
     * Sets the CAISI demographic identifier.
     *
     * @param caisiDemographicId Integer the unique identifier of the patient/demographic record
     */
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }

    /**
     * Retrieves the medical issue code.
     *
     * <p>The issue code is the specific medical code within the coding system defined by
     * the codeType field. For example, if codeType is ICD10, this might be "E11.9" for
     * Type 2 diabetes mellitus without complications.</p>
     *
     * @return String the medical issue code (maximum 64 characters),
     *         or null if not set
     */
    public String getIssueCode() {
        return pcGetissueCode(this);
    }

    /**
     * Sets the medical issue code.
     *
     * @param issueCode String the medical issue code (maximum 64 characters)
     */
    public void setIssueCode(final String issueCode) {
        pcSetissueCode(this, issueCode);
    }

    /**
     * Retrieves the medical coding system type.
     *
     * <p>The code type specifies which standardized medical coding system is being used
     * for the issue code. Supported types include ICD9, ICD10, SNOMED, SNOMED_CORE,
     * DRUG, PREVENTION, CUSTOM_ISSUE, and SYSTEM.</p>
     *
     * @return CodeType the medical coding system enumeration value,
     *         or null if not set
     */
    public CodeType getCodeType() {
        return pcGetcodeType(this);
    }

    /**
     * Sets the medical coding system type.
     *
     * @param codeType CodeType the medical coding system enumeration value
     */
    public void setCodeType(final CodeType codeType) {
        pcSetcodeType(this, codeType);
    }

    /**
     * Returns a string representation of this composite key.
     *
     * <p>The format is: facilityId:demographicId:codeType:issueCode</p>
     * <p>Example: "123:456:ICD10:E11.9"</p>
     *
     * @return String representation in colon-delimited format
     */
    @Override
    public String toString() {
        return "" + pcGetintegratorFacilityId(this) + ':' + pcGetcaisiDemographicId(this) + ':' + pcGetcodeType(this) + ':' + pcGetissueCode(this);
    }

    /**
     * Returns a hash code value for this composite key.
     *
     * <p><strong>Note:</strong> This implementation only uses caisiDemographicId for the hash code,
     * which may lead to hash collisions when the same demographic has multiple issues.
     * This is acceptable for the current use case but may impact performance in large
     * hash-based collections.</p>
     *
     * @return int hash code based on the demographic ID
     */
    @Override
    public int hashCode() {
        return pcGetcaisiDemographicId(this);
    }

    /**
     * Compares this composite key with another object for equality.
     *
     * <p>Two FacilityIdDemographicIssueCompositePk instances are considered equal if all four
     * component fields match:</p>
     * <ul>
     *   <li>integratorFacilityId must be equal</li>
     *   <li>caisiDemographicId must be equal</li>
     *   <li>issueCode must be equal</li>
     *   <li>codeType must be the same enum value</li>
     * </ul>
     *
     * <p>If any RuntimeException occurs during comparison (e.g., NullPointerException,
     * ClassCastException), the method returns false rather than propagating the exception.</p>
     *
     * @param o Object the object to compare with
     * @return boolean true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(final Object o) {
        try {
            final FacilityIdDemographicIssueCompositePk o2 = (FacilityIdDemographicIssueCompositePk)o;
            return pcGetintegratorFacilityId(this).equals(pcGetintegratorFacilityId(o2)) && pcGetcaisiDemographicId(this).equals(pcGetcaisiDemographicId(o2)) && pcGetissueCode(this).equals(pcGetissueCode(o2)) && pcGetcodeType(this) == pcGetcodeType(o2);
        }
        catch (final RuntimeException e) {
            return false;
        }
    }

    /**
     * Returns the OpenJPA enhancement contract version.
     *
     * <p>This method is part of the OpenJPA persistence capability framework and is used
     * by the persistence provider to verify bytecode enhancement compatibility.</p>
     *
     * @return int the enhancement contract version (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -6292964234525710903L;
        FacilityIdDemographicIssueCompositePk.pcFieldNames = new String[] { "caisiDemographicId", "codeType", "integratorFacilityId", "issueCode" };
        FacilityIdDemographicIssueCompositePk.pcFieldTypes = new Class[] { (FacilityIdDemographicIssueCompositePk.class$Ljava$lang$Integer != null) ? FacilityIdDemographicIssueCompositePk.class$Ljava$lang$Integer : (FacilityIdDemographicIssueCompositePk.class$Ljava$lang$Integer = class$("java.lang.Integer")), (FacilityIdDemographicIssueCompositePk.class$Lca$openosp$openo$caisi_integrator$util$CodeType != null) ? FacilityIdDemographicIssueCompositePk.class$Lca$openosp$openo$caisi_integrator$util$CodeType : (FacilityIdDemographicIssueCompositePk.class$Lca$openosp$openo$caisi_integrator$util$CodeType = class$("ca.openosp.openo.caisi_integrator.util.CodeType")), (FacilityIdDemographicIssueCompositePk.class$Ljava$lang$Integer != null) ? FacilityIdDemographicIssueCompositePk.class$Ljava$lang$Integer : (FacilityIdDemographicIssueCompositePk.class$Ljava$lang$Integer = class$("java.lang.Integer")), (FacilityIdDemographicIssueCompositePk.class$Ljava$lang$String != null) ? FacilityIdDemographicIssueCompositePk.class$Ljava$lang$String : (FacilityIdDemographicIssueCompositePk.class$Ljava$lang$String = class$("java.lang.String")) };
        FacilityIdDemographicIssueCompositePk.pcFieldFlags = new byte[] { 26, 26, 26, 26 };
        PCRegistry.register((FacilityIdDemographicIssueCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdDemographicIssueCompositePk != null) ? FacilityIdDemographicIssueCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdDemographicIssueCompositePk : (FacilityIdDemographicIssueCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdDemographicIssueCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdDemographicIssueCompositePk")), FacilityIdDemographicIssueCompositePk.pcFieldNames, FacilityIdDemographicIssueCompositePk.pcFieldTypes, FacilityIdDemographicIssueCompositePk.pcFieldFlags, FacilityIdDemographicIssueCompositePk.pcPCSuperclass, (String)null, (PersistenceCapable)new FacilityIdDemographicIssueCompositePk());
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
        this.codeType = null;
        this.integratorFacilityId = null;
        this.issueCode = null;
    }

    /**
     * Creates a new instance managed by the specified StateManager with an object ID.
     *
     * <p>This OpenJPA callback method creates a new entity instance, optionally clears its fields,
     * assigns the provided StateManager, and initializes key fields from the given object identifier.</p>
     *
     * @param pcStateManager StateManager the state manager to assign to the new instance
     * @param o Object the object identifier to copy key fields from
     * @param b boolean if true, clears all fields after instantiation
     * @return PersistenceCapable the newly created and initialized instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final FacilityIdDemographicIssueCompositePk facilityIdDemographicIssueCompositePk = new FacilityIdDemographicIssueCompositePk();
        if (b) {
            facilityIdDemographicIssueCompositePk.pcClearFields();
        }
        facilityIdDemographicIssueCompositePk.pcStateManager = pcStateManager;
        facilityIdDemographicIssueCompositePk.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)facilityIdDemographicIssueCompositePk;
    }

    /**
     * Creates a new instance managed by the specified StateManager.
     *
     * <p>This OpenJPA callback method creates a new entity instance, optionally clears its fields,
     * and assigns the provided StateManager for persistence lifecycle management.</p>
     *
     * @param pcStateManager StateManager the state manager to assign to the new instance
     * @param b boolean if true, clears all fields after instantiation
     * @return PersistenceCapable the newly created and initialized instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final FacilityIdDemographicIssueCompositePk facilityIdDemographicIssueCompositePk = new FacilityIdDemographicIssueCompositePk();
        if (b) {
            facilityIdDemographicIssueCompositePk.pcClearFields();
        }
        facilityIdDemographicIssueCompositePk.pcStateManager = pcStateManager;
        return (PersistenceCapable)facilityIdDemographicIssueCompositePk;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 4;
    }

    /**
     * Replaces a single persistent field with a value from the StateManager.
     *
     * <p>This OpenJPA callback method is invoked during entity lifecycle operations
     * to update field values managed by the persistence framework. The field index
     * is relative to the inherited field count.</p>
     *
     * @param n int the absolute field index to replace
     * @throws IllegalArgumentException if the field index is invalid or out of range
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - FacilityIdDemographicIssueCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.codeType = (CodeType)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.integratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.issueCode = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple persistent fields with values from the StateManager.
     *
     * <p>This OpenJPA callback method invokes pcReplaceField for each field index
     * in the provided array, enabling batch field updates during persistence operations.</p>
     *
     * @param array int[] array of absolute field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single persistent field value to the StateManager.
     *
     * <p>This OpenJPA callback method is invoked during persistence operations to supply
     * field values to the persistence framework for state tracking and database synchronization.</p>
     *
     * @param n int the absolute field index to provide
     * @throws IllegalArgumentException if the field index is invalid or out of range
     */
    public void pcProvideField(final int n) {
        final int n2 = n - FacilityIdDemographicIssueCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiDemographicId);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.codeType);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.integratorFacilityId);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.issueCode);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Provides multiple persistent field values to the StateManager.
     *
     * <p>This OpenJPA callback method invokes pcProvideField for each field index
     * in the provided array, enabling batch field value transfer to the persistence framework.</p>
     *
     * @param array int[] array of absolute field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    protected void pcCopyField(final FacilityIdDemographicIssueCompositePk facilityIdDemographicIssueCompositePk, final int n) {
        final int n2 = n - FacilityIdDemographicIssueCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = facilityIdDemographicIssueCompositePk.caisiDemographicId;
                return;
            }
            case 1: {
                this.codeType = facilityIdDemographicIssueCompositePk.codeType;
                return;
            }
            case 2: {
                this.integratorFacilityId = facilityIdDemographicIssueCompositePk.integratorFacilityId;
                return;
            }
            case 3: {
                this.issueCode = facilityIdDemographicIssueCompositePk.issueCode;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies multiple persistent fields from another instance.
     *
     * <p>This OpenJPA callback method copies field values from the source object to this instance
     * for the specified field indices. Both objects must share the same StateManager.</p>
     *
     * @param o Object the source object to copy fields from (must be FacilityIdDemographicIssueCompositePk)
     * @param array int[] array of absolute field indices to copy
     * @throws IllegalArgumentException if the source object has a different StateManager
     * @throws IllegalStateException if the StateManager is null
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final FacilityIdDemographicIssueCompositePk facilityIdDemographicIssueCompositePk = (FacilityIdDemographicIssueCompositePk)o;
        if (facilityIdDemographicIssueCompositePk.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(facilityIdDemographicIssueCompositePk, array[i]);
        }
    }

    /**
     * Retrieves the generic context from the StateManager.
     *
     * <p>This method provides access to the OpenJPA generic context associated with
     * the persistence state manager.</p>
     *
     * @return Object the generic context, or null if no StateManager is present
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Retrieves the JPA object identifier for this entity.
     *
     * <p>This method returns the persistence identity of this object as managed by OpenJPA.</p>
     *
     * @return Object the object identifier, or null if no StateManager is present
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
     * @return boolean true if the entity is deleted in the current persistence context, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has been modified (dirty state).
     *
     * <p>This method performs a dirty check via the RedefinitionHelper and determines
     * if any persistent fields have been modified since the last synchronization with
     * the database.</p>
     *
     * @return boolean true if the entity has unsaved changes, false otherwise
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
     * @return boolean true if the entity is new (not yet saved to database), false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is managed by the persistence context.
     *
     * @return boolean true if the entity is persistent (managed), false otherwise
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
     * @return boolean true if serialization is in progress, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks the specified field as dirty (modified).
     *
     * <p>This method notifies the StateManager that a field has been changed,
     * ensuring proper change tracking and database synchronization.</p>
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
     * Retrieves the OpenJPA StateManager for this entity.
     *
     * <p>The StateManager manages the persistence lifecycle and state transitions
     * for this entity instance.</p>
     *
     * @return StateManager the state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version information for optimistic locking.
     *
     * @return Object the version value, or null if no StateManager is present
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces the current StateManager with a new one.
     *
     * <p>This method is used during entity lifecycle transitions to change the StateManager
     * responsible for managing this entity's persistence state. If a StateManager already exists,
     * it delegates the replacement to the existing manager; otherwise, it directly assigns
     * the new StateManager.</p>
     *
     * @param pcStateManager StateManager the new StateManager to use
     * @throws SecurityException if the StateManager replacement is not permitted
     */
    public void pcReplaceStateManager(final StateManager pcStateManager) throws SecurityException {
        if (this.pcStateManager != null) {
            this.pcStateManager = this.pcStateManager.replaceStateManager(pcStateManager);
            return;
        }
        this.pcStateManager = pcStateManager;
    }

    /**
     * Copies primary key fields to an object identifier using a FieldSupplier.
     *
     * <p>This method is intentionally empty as composite keys in this implementation
     * handle identity through direct field comparison rather than separate ID objects.</p>
     *
     * @param fieldSupplier FieldSupplier the field supplier (unused)
     * @param o Object the target object identifier (unused)
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
    }

    /**
     * Copies primary key fields to an object identifier.
     *
     * <p>This method is intentionally empty as composite keys in this implementation
     * handle identity through direct field comparison rather than separate ID objects.</p>
     *
     * @param o Object the target object identifier (unused)
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
    }

    /**
     * Copies primary key fields from an object identifier using a FieldConsumer.
     *
     * <p>This method is intentionally empty as composite keys in this implementation
     * handle identity through direct field comparison rather than separate ID objects.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer (unused)
     * @param o Object the source object identifier (unused)
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
    }

    /**
     * Copies primary key fields from an object identifier.
     *
     * <p>This method is intentionally empty as composite keys in this implementation
     * handle identity through direct field comparison rather than separate ID objects.</p>
     *
     * @param o Object the source object identifier (unused)
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
    }

    /**
     * Creates a new object identifier instance.
     *
     * <p>This implementation returns null as the composite key itself serves as
     * the identity, eliminating the need for a separate ID class.</p>
     *
     * @return Object always returns null
     */
    public Object pcNewObjectIdInstance() {
        return null;
    }

    /**
     * Creates a new object identifier instance based on a source object.
     *
     * <p>This implementation returns null as the composite key itself serves as
     * the identity, eliminating the need for a separate ID class.</p>
     *
     * @param o Object the source object (unused)
     * @return Object always returns null
     */
    public Object pcNewObjectIdInstance(final Object o) {
        return null;
    }
    
    private static final Integer pcGetcaisiDemographicId(final FacilityIdDemographicIssueCompositePk facilityIdDemographicIssueCompositePk) {
        if (facilityIdDemographicIssueCompositePk.pcStateManager == null) {
            return facilityIdDemographicIssueCompositePk.caisiDemographicId;
        }
        facilityIdDemographicIssueCompositePk.pcStateManager.accessingField(FacilityIdDemographicIssueCompositePk.pcInheritedFieldCount + 0);
        return facilityIdDemographicIssueCompositePk.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final FacilityIdDemographicIssueCompositePk facilityIdDemographicIssueCompositePk, final Integer caisiDemographicId) {
        if (facilityIdDemographicIssueCompositePk.pcStateManager == null) {
            facilityIdDemographicIssueCompositePk.caisiDemographicId = caisiDemographicId;
            return;
        }
        facilityIdDemographicIssueCompositePk.pcStateManager.settingObjectField((PersistenceCapable)facilityIdDemographicIssueCompositePk, FacilityIdDemographicIssueCompositePk.pcInheritedFieldCount + 0, (Object)facilityIdDemographicIssueCompositePk.caisiDemographicId, (Object)caisiDemographicId, 0);
    }
    
    private static final CodeType pcGetcodeType(final FacilityIdDemographicIssueCompositePk facilityIdDemographicIssueCompositePk) {
        if (facilityIdDemographicIssueCompositePk.pcStateManager == null) {
            return facilityIdDemographicIssueCompositePk.codeType;
        }
        facilityIdDemographicIssueCompositePk.pcStateManager.accessingField(FacilityIdDemographicIssueCompositePk.pcInheritedFieldCount + 1);
        return facilityIdDemographicIssueCompositePk.codeType;
    }
    
    private static final void pcSetcodeType(final FacilityIdDemographicIssueCompositePk facilityIdDemographicIssueCompositePk, final CodeType codeType) {
        if (facilityIdDemographicIssueCompositePk.pcStateManager == null) {
            facilityIdDemographicIssueCompositePk.codeType = codeType;
            return;
        }
        facilityIdDemographicIssueCompositePk.pcStateManager.settingObjectField((PersistenceCapable)facilityIdDemographicIssueCompositePk, FacilityIdDemographicIssueCompositePk.pcInheritedFieldCount + 1, (Object)facilityIdDemographicIssueCompositePk.codeType, (Object)codeType, 0);
    }
    
    private static final Integer pcGetintegratorFacilityId(final FacilityIdDemographicIssueCompositePk facilityIdDemographicIssueCompositePk) {
        if (facilityIdDemographicIssueCompositePk.pcStateManager == null) {
            return facilityIdDemographicIssueCompositePk.integratorFacilityId;
        }
        facilityIdDemographicIssueCompositePk.pcStateManager.accessingField(FacilityIdDemographicIssueCompositePk.pcInheritedFieldCount + 2);
        return facilityIdDemographicIssueCompositePk.integratorFacilityId;
    }
    
    private static final void pcSetintegratorFacilityId(final FacilityIdDemographicIssueCompositePk facilityIdDemographicIssueCompositePk, final Integer integratorFacilityId) {
        if (facilityIdDemographicIssueCompositePk.pcStateManager == null) {
            facilityIdDemographicIssueCompositePk.integratorFacilityId = integratorFacilityId;
            return;
        }
        facilityIdDemographicIssueCompositePk.pcStateManager.settingObjectField((PersistenceCapable)facilityIdDemographicIssueCompositePk, FacilityIdDemographicIssueCompositePk.pcInheritedFieldCount + 2, (Object)facilityIdDemographicIssueCompositePk.integratorFacilityId, (Object)integratorFacilityId, 0);
    }
    
    private static final String pcGetissueCode(final FacilityIdDemographicIssueCompositePk facilityIdDemographicIssueCompositePk) {
        if (facilityIdDemographicIssueCompositePk.pcStateManager == null) {
            return facilityIdDemographicIssueCompositePk.issueCode;
        }
        facilityIdDemographicIssueCompositePk.pcStateManager.accessingField(FacilityIdDemographicIssueCompositePk.pcInheritedFieldCount + 3);
        return facilityIdDemographicIssueCompositePk.issueCode;
    }
    
    private static final void pcSetissueCode(final FacilityIdDemographicIssueCompositePk facilityIdDemographicIssueCompositePk, final String issueCode) {
        if (facilityIdDemographicIssueCompositePk.pcStateManager == null) {
            facilityIdDemographicIssueCompositePk.issueCode = issueCode;
            return;
        }
        facilityIdDemographicIssueCompositePk.pcStateManager.settingStringField((PersistenceCapable)facilityIdDemographicIssueCompositePk, FacilityIdDemographicIssueCompositePk.pcInheritedFieldCount + 3, facilityIdDemographicIssueCompositePk.issueCode, issueCode, 0);
    }

    /**
     * Checks if this entity is in a detached state.
     *
     * <p>A detached entity is one that was previously managed by a persistence context
     * but is no longer associated with it. This method checks both the StateManager
     * and the detached state marker to determine the detachment status.</p>
     *
     * <p><strong>Return values:</strong></p>
     * <ul>
     *   <li>Boolean.TRUE - entity is definitely detached</li>
     *   <li>Boolean.FALSE - entity is definitely not detached</li>
     *   <li>null - detached state is indeterminate</li>
     * </ul>
     *
     * @return Boolean the detached state (TRUE, FALSE, or null if indeterminate)
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
     * Retrieves the detached state marker.
     *
     * <p>The detached state is an internal marker used by OpenJPA to track whether
     * an entity has been detached from its persistence context. This is used during
     * serialization and merge operations.</p>
     *
     * @return Object the detached state marker, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state marker.
     *
     * <p>This method is used by OpenJPA during entity lifecycle transitions,
     * particularly during serialization and deserialization operations.</p>
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
