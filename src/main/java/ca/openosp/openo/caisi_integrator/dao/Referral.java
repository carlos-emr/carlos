package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.util.IntId;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.util.InternalException;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PersistenceCapable;
import org.apache.openjpa.enhance.PCRegistry;
import org.apache.commons.lang3.StringUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Entity;

/**
 * JPA entity representing a healthcare referral between CAISI facilities within the OpenO EMR Integrator system.
 *
 * <p>This class manages the complete lifecycle of patient referrals from a source healthcare facility/provider
 * to a destination facility/program. Referrals are a critical component of coordinated care in community
 * healthcare settings, enabling seamless patient transitions between different care programs and facilities.</p>
 *
 * <p>The referral entity tracks:</p>
 * <ul>
 *   <li>Source information: facility, patient demographic, and referring provider</li>
 *   <li>Destination information: facility and specific care program</li>
 *   <li>Clinical details: referral date, reason for referral, and presenting problem</li>
 * </ul>
 *
 * <p>This entity is enhanced by Apache OpenJPA for persistence management, implementing the
 * {@link PersistenceCapable} interface to support advanced ORM features including lazy loading,
 * dirty tracking, and detached entity management.</p>
 *
 * <p><strong>Healthcare Context:</strong> In the CAISI (Client Access to Integrated Services and Information)
 * system, referrals facilitate care coordination across multiple healthcare facilities and programs, supporting
 * integrated service delivery for patients with complex care needs.</p>
 *
 * @see AbstractModel
 * @see PersistenceCapable
 * @since 2026-01-24
 */
@Entity
public class Referral extends AbstractModel<Integer> implements PersistenceCapable
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false)
    @Index
    private Integer sourceIntegratorFacilityId;
    @Column(nullable = false)
    @Index
    private Integer sourceCaisiDemographicId;
    @Column(nullable = false, length = 32)
    private String sourceCaisiProviderId;
    @Column(nullable = false)
    @Index
    private Integer destinationIntegratorFacilityId;
    @Column(nullable = false)
    @Index
    private Integer destinationCaisiProgramId;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date referralDate;
    @Column(columnDefinition = "text")
    private String reasonForReferral;
    @Column(columnDefinition = "text")
    private String presentingProblem;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$Referral;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    /**
     * Default constructor initializing all fields to null.
     *
     * <p>Creates a new Referral instance with all properties unset. This constructor is primarily
     * used by the JPA persistence framework when instantiating entities from database records.</p>
     */
    public Referral() {
        this.id = null;
        this.sourceIntegratorFacilityId = null;
        this.sourceCaisiDemographicId = null;
        this.sourceCaisiProviderId = null;
        this.destinationIntegratorFacilityId = null;
        this.destinationCaisiProgramId = null;
        this.referralDate = null;
        this.reasonForReferral = null;
        this.presentingProblem = null;
    }
    
    /**
     * Gets the unique identifier for this referral.
     *
     * @return Integer the referral ID, or null if not yet persisted
     */
    @Override
    public Integer getId() {
        return pcGetid(this);
    }
    
    /**
     * Gets the unique identifier for this referral (alias for getId).
     *
     * @return Integer the referral ID, or null if not yet persisted
     */
    public Integer getReferralId() {
        return pcGetid(this);
    }
    
    /**
     * Sets the referral ID (not supported - IDs are auto-generated).
     *
     * @param id Integer the proposed ID value
     * @throws UnsupportedOperationException always, as IDs are managed by the persistence framework
     */
    public void setReferralId(final Integer id) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Gets the integrator facility ID of the referring healthcare facility.
     *
     * @return Integer the source facility ID in the integrator system
     */
    public Integer getSourceIntegratorFacilityId() {
        return pcGetsourceIntegratorFacilityId(this);
    }
    
    /**
     * Sets the integrator facility ID of the referring healthcare facility.
     *
     * @param sourceIntegratorFacilityId Integer the source facility ID in the integrator system
     */
    public void setSourceIntegratorFacilityId(final Integer sourceIntegratorFacilityId) {
        pcSetsourceIntegratorFacilityId(this, sourceIntegratorFacilityId);
    }
    
    /**
     * Gets the CAISI demographic ID of the patient being referred.
     *
     * @return Integer the patient's demographic ID in the source CAISI system
     */
    public Integer getSourceCaisiDemographicId() {
        return pcGetsourceCaisiDemographicId(this);
    }
    
    /**
     * Sets the CAISI demographic ID of the patient being referred.
     *
     * @param sourceCaisiDemographicId Integer the patient's demographic ID in the source CAISI system
     */
    public void setSourceCaisiDemographicId(final Integer sourceCaisiDemographicId) {
        pcSetsourceCaisiDemographicId(this, sourceCaisiDemographicId);
    }
    
    /**
     * Gets the CAISI provider ID of the healthcare provider making the referral.
     *
     * @return String the referring provider's ID in the source CAISI system
     */
    public String getSourceCaisiProviderId() {
        return pcGetsourceCaisiProviderId(this);
    }
    
    /**
     * Sets the CAISI provider ID of the healthcare provider making the referral.
     *
     * <p>The value is automatically trimmed and converted to null if blank.</p>
     *
     * @param sourceCaisiProviderId String the referring provider's ID in the source CAISI system
     */
    public void setSourceCaisiProviderId(final String sourceCaisiProviderId) {
        pcSetsourceCaisiProviderId(this, StringUtils.trimToNull(sourceCaisiProviderId));
    }
    
    /**
     * Gets the integrator facility ID of the destination healthcare facility.
     *
     * @return Integer the destination facility ID in the integrator system
     */
    public Integer getDestinationIntegratorFacilityId() {
        return pcGetdestinationIntegratorFacilityId(this);
    }
    
    /**
     * Sets the integrator facility ID of the destination healthcare facility.
     *
     * @param destinationIntegratorFacilityId Integer the destination facility ID in the integrator system
     */
    public void setDestinationIntegratorFacilityId(final Integer destinationIntegratorFacilityId) {
        pcSetdestinationIntegratorFacilityId(this, destinationIntegratorFacilityId);
    }
    
    /**
     * Gets the CAISI program ID at the destination facility where the patient is being referred.
     *
     * @return Integer the destination program ID in the CAISI system
     */
    public Integer getDestinationCaisiProgramId() {
        return pcGetdestinationCaisiProgramId(this);
    }
    
    /**
     * Sets the CAISI program ID at the destination facility where the patient is being referred.
     *
     * @param destinationCaisiProgramId Integer the destination program ID in the CAISI system
     */
    public void setDestinationCaisiProgramId(final Integer destinationCaisiProgramId) {
        pcSetdestinationCaisiProgramId(this, destinationCaisiProgramId);
    }
    
    /**
     * Gets the date when this referral was made.
     *
     * @return Date the referral date and time
     */
    public Date getReferralDate() {
        return pcGetreferralDate(this);
    }
    
    /**
     * Sets the date when this referral was made.
     *
     * @param referralDate Date the referral date and time
     */
    public void setReferralDate(final Date referralDate) {
        pcSetreferralDate(this, referralDate);
    }
    
    /**
     * Gets the clinical reason for this referral.
     *
     * @return String the reason for referral, or null if not specified
     */
    public String getReasonForReferral() {
        return pcGetreasonForReferral(this);
    }
    
    /**
     * Sets the clinical reason for this referral.
     *
     * <p>The value is automatically trimmed and converted to null if blank.</p>
     *
     * @param reasonForReferral String the reason for referral
     */
    public void setReasonForReferral(final String reasonForReferral) {
        pcSetreasonForReferral(this, StringUtils.trimToNull(reasonForReferral));
    }
    
    /**
     * Gets the patient's presenting problem that prompted this referral.
     *
     * @return String the presenting problem description, or null if not specified
     */
    public String getPresentingProblem() {
        return pcGetpresentingProblem(this);
    }
    
    /**
     * Sets the patient's presenting problem that prompted this referral.
     *
     * <p>The value is automatically trimmed and converted to null if blank.</p>
     *
     * @param presentingProblem String the presenting problem description
     */
    public void setPresentingProblem(final String presentingProblem) {
        pcSetpresentingProblem(this, StringUtils.trimToNull(presentingProblem));
    }
    
    /**
     * Gets the OpenJPA enhancement contract version for this entity.
     *
     * @return int the enhancement contract version (always 2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -3840584601644845846L;
        Referral.pcFieldNames = new String[] { "destinationCaisiProgramId", "destinationIntegratorFacilityId", "id", "presentingProblem", "reasonForReferral", "referralDate", "sourceCaisiDemographicId", "sourceCaisiProviderId", "sourceIntegratorFacilityId" };
        Referral.pcFieldTypes = new Class[] { (Referral.class$Ljava$lang$Integer != null) ? Referral.class$Ljava$lang$Integer : (Referral.class$Ljava$lang$Integer = class$("java.lang.Integer")), (Referral.class$Ljava$lang$Integer != null) ? Referral.class$Ljava$lang$Integer : (Referral.class$Ljava$lang$Integer = class$("java.lang.Integer")), (Referral.class$Ljava$lang$Integer != null) ? Referral.class$Ljava$lang$Integer : (Referral.class$Ljava$lang$Integer = class$("java.lang.Integer")), (Referral.class$Ljava$lang$String != null) ? Referral.class$Ljava$lang$String : (Referral.class$Ljava$lang$String = class$("java.lang.String")), (Referral.class$Ljava$lang$String != null) ? Referral.class$Ljava$lang$String : (Referral.class$Ljava$lang$String = class$("java.lang.String")), (Referral.class$Ljava$util$Date != null) ? Referral.class$Ljava$util$Date : (Referral.class$Ljava$util$Date = class$("java.util.Date")), (Referral.class$Ljava$lang$Integer != null) ? Referral.class$Ljava$lang$Integer : (Referral.class$Ljava$lang$Integer = class$("java.lang.Integer")), (Referral.class$Ljava$lang$String != null) ? Referral.class$Ljava$lang$String : (Referral.class$Ljava$lang$String = class$("java.lang.String")), (Referral.class$Ljava$lang$Integer != null) ? Referral.class$Ljava$lang$Integer : (Referral.class$Ljava$lang$Integer = class$("java.lang.Integer")) };
        Referral.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral != null) ? Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral : (Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral = class$("ca.openosp.openo.caisi_integrator.dao.Referral")), Referral.pcFieldNames, Referral.pcFieldTypes, Referral.pcFieldFlags, Referral.pcPCSuperclass, "Referral", (PersistenceCapable)new Referral());
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
     * Clears all fields to null, used by OpenJPA for entity lifecycle management.
     */
    protected void pcClearFields() {
        this.destinationCaisiProgramId = null;
        this.destinationIntegratorFacilityId = null;
        this.id = null;
        this.presentingProblem = null;
        this.reasonForReferral = null;
        this.referralDate = null;
        this.sourceCaisiDemographicId = null;
        this.sourceCaisiProviderId = null;
        this.sourceIntegratorFacilityId = null;
    }
    
    /**
     * Creates a new instance of this entity with the specified state manager and object ID.
     *
     * <p>This method is used by OpenJPA to create instances during database loading operations.</p>
     *
     * @param pcStateManager StateManager the persistence state manager for this instance
     * @param o Object the object ID to copy key fields from
     * @param b boolean whether to clear all fields after initialization
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final Referral referral = new Referral();
        if (b) {
            referral.pcClearFields();
        }
        referral.pcStateManager = pcStateManager;
        referral.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)referral;
    }
    
    /**
     * Creates a new instance of this entity with the specified state manager.
     *
     * <p>This method is used by OpenJPA to create instances during database loading operations.</p>
     *
     * @param pcStateManager StateManager the persistence state manager for this instance
     * @param b boolean whether to clear all fields after initialization
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final Referral referral = new Referral();
        if (b) {
            referral.pcClearFields();
        }
        referral.pcStateManager = pcStateManager;
        return (PersistenceCapable)referral;
    }
    
    /**
     * Gets the number of managed fields in this entity.
     *
     * @return int the count of managed fields (9)
     */
    protected static int pcGetManagedFieldCount() {
        return 9;
    }
    
    /**
     * Replaces a single field value from the persistence state manager.
     *
     * <p>This method is called by OpenJPA during entity loading and refresh operations.</p>
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - Referral.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.destinationCaisiProgramId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.destinationIntegratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.id = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.presentingProblem = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.reasonForReferral = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.referralDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.sourceCaisiDemographicId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.sourceCaisiProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.sourceIntegratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Replaces multiple field values from the persistence state manager.
     *
     * <p>This method is called by OpenJPA during entity loading and refresh operations.</p>
     *
     * @param array int[] the array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }
    
    /**
     * Provides a single field value to the persistence state manager.
     *
     * <p>This method is called by OpenJPA during entity flushing and detachment operations.</p>
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - Referral.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.destinationCaisiProgramId);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.destinationIntegratorFacilityId);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.id);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.presentingProblem);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.reasonForReferral);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.referralDate);
                return;
            }
            case 6: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.sourceCaisiDemographicId);
                return;
            }
            case 7: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.sourceCaisiProviderId);
                return;
            }
            case 8: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.sourceIntegratorFacilityId);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Provides multiple field values to the persistence state manager.
     *
     * <p>This method is called by OpenJPA during entity flushing and detachment operations.</p>
     *
     * @param array int[] the array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    /**
     * Copies a single field value from another Referral instance.
     *
     * <p>This method is used by OpenJPA for entity cloning and merge operations.</p>
     *
     * @param referral Referral the source instance to copy from
     * @param n int the field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final Referral referral, final int n) {
        final int n2 = n - Referral.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.destinationCaisiProgramId = referral.destinationCaisiProgramId;
                return;
            }
            case 1: {
                this.destinationIntegratorFacilityId = referral.destinationIntegratorFacilityId;
                return;
            }
            case 2: {
                this.id = referral.id;
                return;
            }
            case 3: {
                this.presentingProblem = referral.presentingProblem;
                return;
            }
            case 4: {
                this.reasonForReferral = referral.reasonForReferral;
                return;
            }
            case 5: {
                this.referralDate = referral.referralDate;
                return;
            }
            case 6: {
                this.sourceCaisiDemographicId = referral.sourceCaisiDemographicId;
                return;
            }
            case 7: {
                this.sourceCaisiProviderId = referral.sourceCaisiProviderId;
                return;
            }
            case 8: {
                this.sourceIntegratorFacilityId = referral.sourceIntegratorFacilityId;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Copies multiple field values from another Referral instance.
     *
     * <p>This method is used by OpenJPA for entity cloning and merge operations.</p>
     *
     * @param o Object the source instance to copy from (must be a Referral)
     * @param array int[] the array of field indices to copy
     * @throws IllegalArgumentException if the source object has a different state manager
     * @throws IllegalStateException if the state manager is null
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final Referral referral = (Referral)o;
        if (referral.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(referral, array[i]);
        }
    }
    
    /**
     * Gets the generic context from the persistence state manager.
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
     * Fetches the object ID for this entity from the persistence state manager.
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
     * Checks if this entity is marked as deleted.
     *
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }
    
    /**
     * Checks if this entity has been modified since it was loaded.
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
     * @return boolean true if the entity is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }
    
    /**
     * Checks if this entity is managed by the persistence context.
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
     * Marks the specified field as dirty (modified).
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
     * Gets the persistence state manager for this entity.
     *
     * @return StateManager the state manager, or null if none is attached
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }
    
    /**
     * Gets the version identifier for optimistic locking.
     *
     * @return Object the version object, or null if no state manager is attached
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
     * @param pcStateManager StateManager the new state manager to use
     * @throws SecurityException if state manager replacement is not allowed
     */
    public void pcReplaceStateManager(final StateManager pcStateManager) throws SecurityException {
        if (this.pcStateManager != null) {
            this.pcStateManager = this.pcStateManager.replaceStateManager(pcStateManager);
            return;
        }
        this.pcStateManager = pcStateManager;
    }
    
    /**
     * Copies key fields to an object ID using a field supplier (not supported for this entity).
     *
     * @param fieldSupplier FieldSupplier the field supplier
     * @param o Object the target object ID
     * @throws InternalException always, as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }
    
    /**
     * Copies key fields to an object ID (not supported for this entity).
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
     * @param fieldConsumer FieldConsumer the field consumer to receive the key fields
     * @param o Object the source object ID (must be an IntId)
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(2 + Referral.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }
    
    /**
     * Copies key fields from an object ID.
     *
     * @param o Object the source object ID (must be an IntId)
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Integer.valueOf(((IntId)o).getId());
    }
    
    /**
     * Creates a new object ID instance from a string representation.
     *
     * @param o Object the string representation of the ID
     * @return Object a new IntId instance
     */
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral != null) ? Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral : (Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral = class$("ca.openosp.openo.caisi_integrator.dao.Referral")), (String)o);
    }
    
    /**
     * Creates a new object ID instance from this entity's current ID value.
     *
     * @return Object a new IntId instance
     */
    public Object pcNewObjectIdInstance() {
        return new IntId((Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral != null) ? Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral : (Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral = class$("ca.openosp.openo.caisi_integrator.dao.Referral")), this.id);
    }
    
    private static final Integer pcGetdestinationCaisiProgramId(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.destinationCaisiProgramId;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 0);
        return referral.destinationCaisiProgramId;
    }
    
    private static final void pcSetdestinationCaisiProgramId(final Referral referral, final Integer destinationCaisiProgramId) {
        if (referral.pcStateManager == null) {
            referral.destinationCaisiProgramId = destinationCaisiProgramId;
            return;
        }
        referral.pcStateManager.settingObjectField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 0, (Object)referral.destinationCaisiProgramId, (Object)destinationCaisiProgramId, 0);
    }
    
    private static final Integer pcGetdestinationIntegratorFacilityId(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.destinationIntegratorFacilityId;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 1);
        return referral.destinationIntegratorFacilityId;
    }
    
    private static final void pcSetdestinationIntegratorFacilityId(final Referral referral, final Integer destinationIntegratorFacilityId) {
        if (referral.pcStateManager == null) {
            referral.destinationIntegratorFacilityId = destinationIntegratorFacilityId;
            return;
        }
        referral.pcStateManager.settingObjectField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 1, (Object)referral.destinationIntegratorFacilityId, (Object)destinationIntegratorFacilityId, 0);
    }
    
    private static final Integer pcGetid(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.id;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 2);
        return referral.id;
    }
    
    private static final void pcSetid(final Referral referral, final Integer id) {
        if (referral.pcStateManager == null) {
            referral.id = id;
            return;
        }
        referral.pcStateManager.settingObjectField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 2, (Object)referral.id, (Object)id, 0);
    }
    
    private static final String pcGetpresentingProblem(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.presentingProblem;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 3);
        return referral.presentingProblem;
    }
    
    private static final void pcSetpresentingProblem(final Referral referral, final String presentingProblem) {
        if (referral.pcStateManager == null) {
            referral.presentingProblem = presentingProblem;
            return;
        }
        referral.pcStateManager.settingStringField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 3, referral.presentingProblem, presentingProblem, 0);
    }
    
    private static final String pcGetreasonForReferral(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.reasonForReferral;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 4);
        return referral.reasonForReferral;
    }
    
    private static final void pcSetreasonForReferral(final Referral referral, final String reasonForReferral) {
        if (referral.pcStateManager == null) {
            referral.reasonForReferral = reasonForReferral;
            return;
        }
        referral.pcStateManager.settingStringField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 4, referral.reasonForReferral, reasonForReferral, 0);
    }
    
    private static final Date pcGetreferralDate(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.referralDate;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 5);
        return referral.referralDate;
    }
    
    private static final void pcSetreferralDate(final Referral referral, final Date referralDate) {
        if (referral.pcStateManager == null) {
            referral.referralDate = referralDate;
            return;
        }
        referral.pcStateManager.settingObjectField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 5, (Object)referral.referralDate, (Object)referralDate, 0);
    }
    
    private static final Integer pcGetsourceCaisiDemographicId(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.sourceCaisiDemographicId;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 6);
        return referral.sourceCaisiDemographicId;
    }
    
    private static final void pcSetsourceCaisiDemographicId(final Referral referral, final Integer sourceCaisiDemographicId) {
        if (referral.pcStateManager == null) {
            referral.sourceCaisiDemographicId = sourceCaisiDemographicId;
            return;
        }
        referral.pcStateManager.settingObjectField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 6, (Object)referral.sourceCaisiDemographicId, (Object)sourceCaisiDemographicId, 0);
    }
    
    private static final String pcGetsourceCaisiProviderId(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.sourceCaisiProviderId;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 7);
        return referral.sourceCaisiProviderId;
    }
    
    private static final void pcSetsourceCaisiProviderId(final Referral referral, final String sourceCaisiProviderId) {
        if (referral.pcStateManager == null) {
            referral.sourceCaisiProviderId = sourceCaisiProviderId;
            return;
        }
        referral.pcStateManager.settingStringField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 7, referral.sourceCaisiProviderId, sourceCaisiProviderId, 0);
    }
    
    private static final Integer pcGetsourceIntegratorFacilityId(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.sourceIntegratorFacilityId;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 8);
        return referral.sourceIntegratorFacilityId;
    }
    
    private static final void pcSetsourceIntegratorFacilityId(final Referral referral, final Integer sourceIntegratorFacilityId) {
        if (referral.pcStateManager == null) {
            referral.sourceIntegratorFacilityId = sourceIntegratorFacilityId;
            return;
        }
        referral.pcStateManager.settingObjectField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 8, (Object)referral.sourceIntegratorFacilityId, (Object)sourceIntegratorFacilityId, 0);
    }
    
    /**
     * Checks if this entity is in a detached state.
     *
     * <p>A detached entity is one that was previously managed by a persistence context
     * but is no longer associated with it.</p>
     *
     * @return Boolean true if detached, false if attached, null if unknown
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
            if (this.id != null) {
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
     * Gets the detached state object for this entity.
     *
     * @return Object the detached state, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }
    
    /**
     * Sets the detached state object for this entity.
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
