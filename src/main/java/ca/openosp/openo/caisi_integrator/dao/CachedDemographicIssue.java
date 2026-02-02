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
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import ca.openosp.openo.caisi_integrator.util.Role;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * Represents a cached demographic issue (health concern or diagnosis) within the CAISI integrator system.
 *
 * <p>This entity stores patient health issues that are cached from integrated facilities, enabling
 * efficient retrieval of patient health concerns across multiple healthcare facilities. The integrator
 * system synchronizes demographic issues from remote facilities to provide a unified view of patient
 * health problems.</p>
 *
 * <p>This class implements OpenJPA's {@link PersistenceCapable} interface, which provides enhanced
 * persistence capabilities including field-level change tracking, lazy loading, and detachment support.
 * The OpenJPA bytecode enhancement process generates the necessary persistence methods at build time.</p>
 *
 * <p><strong>Healthcare Context:</strong> Demographic issues represent clinical problems, diagnoses,
 * or health concerns documented in a patient's medical record. These issues can be acute or chronic,
 * certain or uncertain, major or minor, and may be resolved or ongoing. The role field indicates which
 * healthcare provider or team role documented the issue.</p>
 *
 * @see FacilityIdDemographicIssueCompositePk
 * @see AbstractModel
 * @see Role
 * @since 2026-01-24
 */
@Entity
public class CachedDemographicIssue extends AbstractModel<FacilityIdDemographicIssueCompositePk> implements PersistenceCapable
{
    @EmbeddedId
    private FacilityIdDemographicIssueCompositePk facilityDemographicIssuePk;
    @Column(nullable = false, length = 128)
    private String issueDescription;
    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private Role issueRole;
    @Column(columnDefinition = "tinyint(1)")
    private Boolean acute;
    @Column(columnDefinition = "tinyint(1)")
    private Boolean certain;
    @Column(columnDefinition = "tinyint(1)")
    private Boolean major;
    @Column(columnDefinition = "tinyint(1)")
    private Boolean resolved;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Boolean;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdDemographicIssueCompositePk;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$util$Role;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicIssue;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor that initializes all fields to null.
     *
     * <p>This constructor creates a new instance with all health issue attributes
     * uninitialized, ready to be populated with demographic issue data from an
     * integrated facility.</p>
     */
    public CachedDemographicIssue() {
        this.issueDescription = null;
        this.issueRole = null;
        this.acute = null;
        this.certain = null;
        this.major = null;
        this.resolved = null;
    }

    /**
     * Retrieves the composite primary key for this cached demographic issue.
     *
     * @return FacilityIdDemographicIssueCompositePk the composite primary key containing
     *         facility ID and demographic issue information
     */
    public FacilityIdDemographicIssueCompositePk getFacilityDemographicIssuePk() {
        return pcGetfacilityDemographicIssuePk(this);
    }

    /**
     * Sets the composite primary key for this cached demographic issue.
     *
     * @param facilityDemographicIssuePk FacilityIdDemographicIssueCompositePk the composite
     *                                   primary key to assign
     */
    public void setFacilityDemographicIssuePk(final FacilityIdDemographicIssueCompositePk facilityDemographicIssuePk) {
        pcSetfacilityDemographicIssuePk(this, facilityDemographicIssuePk);
    }

    /**
     * Retrieves the textual description of the health issue.
     *
     * @return String the issue description (e.g., "Type 2 Diabetes", "Hypertension")
     */
    public String getIssueDescription() {
        return pcGetissueDescription(this);
    }

    /**
     * Sets the textual description of the health issue.
     *
     * <p>The description is automatically trimmed and null values are preserved.
     * Whitespace-only strings are converted to null.</p>
     *
     * @param issueDescription String the issue description to set
     */
    public void setIssueDescription(final String issueDescription) {
        pcSetissueDescription(this, StringUtils.trimToNull(issueDescription));
    }

    /**
     * Retrieves whether this health issue is classified as acute.
     *
     * <p>An acute issue has a sudden onset and typically short duration, as opposed
     * to chronic conditions that persist over time.</p>
     *
     * @return Boolean true if the issue is acute, false if chronic, null if unspecified
     */
    public Boolean getAcute() {
        return pcGetacute(this);
    }

    /**
     * Sets whether this health issue is classified as acute.
     *
     * @param acute Boolean true for acute issues, false for chronic, null if unspecified
     */
    public void setAcute(final Boolean acute) {
        pcSetacute(this, acute);
    }

    /**
     * Retrieves the healthcare role that documented this issue.
     *
     * @return Role the role (e.g., physician, nurse) that documented the issue
     */
    public Role getIssueRole() {
        return pcGetissueRole(this);
    }

    /**
     * Sets the healthcare role that documented this issue.
     *
     * @param issueRole Role the documenting role to assign
     */
    public void setIssueRole(final Role issueRole) {
        pcSetissueRole(this, issueRole);
    }

    /**
     * Retrieves whether the diagnosis or issue is certain or probable.
     *
     * <p>A certain diagnosis has been confirmed through clinical evidence, while an
     * uncertain diagnosis is suspected or provisional pending further investigation.</p>
     *
     * @return Boolean true if diagnosis is certain, false if probable/suspected, null if unspecified
     */
    public Boolean getCertain() {
        return pcGetcertain(this);
    }

    /**
     * Sets whether the diagnosis or issue is certain or probable.
     *
     * @param certain Boolean true for confirmed diagnosis, false for suspected, null if unspecified
     */
    public void setCertain(final Boolean certain) {
        pcSetcertain(this, certain);
    }

    /**
     * Retrieves whether this is considered a major health issue.
     *
     * <p>Major issues typically have significant impact on patient health, require ongoing
     * management, or affect treatment decisions.</p>
     *
     * @return Boolean true if major issue, false if minor, null if unspecified
     */
    public Boolean getMajor() {
        return pcGetmajor(this);
    }

    /**
     * Sets whether this is considered a major health issue.
     *
     * @param major Boolean true for major issues, false for minor, null if unspecified
     */
    public void setMajor(final Boolean major) {
        pcSetmajor(this, major);
    }

    /**
     * Retrieves whether this health issue has been resolved.
     *
     * <p>Resolved issues are no longer active concerns but remain in the patient's
     * medical history for clinical reference.</p>
     *
     * @return Boolean true if issue is resolved, false if ongoing, null if unspecified
     */
    public Boolean getResolved() {
        return pcGetresolved(this);
    }

    /**
     * Sets whether this health issue has been resolved.
     *
     * @param resolved Boolean true for resolved issues, false for ongoing, null if unspecified
     */
    public void setResolved(final Boolean resolved) {
        pcSetresolved(this, resolved);
    }

    /**
     * Retrieves the entity identifier.
     *
     * <p>This method overrides the abstract method from {@link AbstractModel} and returns
     * the composite primary key.</p>
     *
     * @return FacilityIdDemographicIssueCompositePk the entity's composite primary key
     */
    @Override
    public FacilityIdDemographicIssueCompositePk getId() {
        return pcGetfacilityDemographicIssuePk(this);
    }

    /**
     * Retrieves the OpenJPA enhancement contract version.
     *
     * <p>This method is part of the OpenJPA persistence enhancement framework and
     * indicates the version of the bytecode enhancement contract implemented.</p>
     *
     * @return int the enhancement contract version (currently 2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 2003466602538355709L;
        CachedDemographicIssue.pcFieldNames = new String[] { "acute", "certain", "facilityDemographicIssuePk", "issueDescription", "issueRole", "major", "resolved" };
        CachedDemographicIssue.pcFieldTypes = new Class[] { (CachedDemographicIssue.class$Ljava$lang$Boolean != null) ? CachedDemographicIssue.class$Ljava$lang$Boolean : (CachedDemographicIssue.class$Ljava$lang$Boolean = class$("java.lang.Boolean")), (CachedDemographicIssue.class$Ljava$lang$Boolean != null) ? CachedDemographicIssue.class$Ljava$lang$Boolean : (CachedDemographicIssue.class$Ljava$lang$Boolean = class$("java.lang.Boolean")), (CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdDemographicIssueCompositePk != null) ? CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdDemographicIssueCompositePk : (CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdDemographicIssueCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdDemographicIssueCompositePk")), (CachedDemographicIssue.class$Ljava$lang$String != null) ? CachedDemographicIssue.class$Ljava$lang$String : (CachedDemographicIssue.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$util$Role != null) ? CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$util$Role : (CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$util$Role = class$("ca.openosp.openo.caisi_integrator.util.Role")), (CachedDemographicIssue.class$Ljava$lang$Boolean != null) ? CachedDemographicIssue.class$Ljava$lang$Boolean : (CachedDemographicIssue.class$Ljava$lang$Boolean = class$("java.lang.Boolean")), (CachedDemographicIssue.class$Ljava$lang$Boolean != null) ? CachedDemographicIssue.class$Ljava$lang$Boolean : (CachedDemographicIssue.class$Ljava$lang$Boolean = class$("java.lang.Boolean")) };
        CachedDemographicIssue.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicIssue != null) ? CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicIssue : (CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicIssue = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicIssue")), CachedDemographicIssue.pcFieldNames, CachedDemographicIssue.pcFieldTypes, CachedDemographicIssue.pcFieldFlags, CachedDemographicIssue.pcPCSuperclass, "CachedDemographicIssue", (PersistenceCapable)new CachedDemographicIssue());
    }
    
    /**
     * Internal utility method to load a class by name.
     *
     * <p>This synthetic method is generated by the compiler to support class literal
     * operations in bytecode for compatibility with older Java versions.</p>
     *
     * @param className String the fully qualified class name to load
     * @return Class the loaded class object
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
     * Clears all persistent fields to their default null values.
     *
     * <p>This method is part of the OpenJPA persistence enhancement framework and is
     * used during object lifecycle management to reset field values.</p>
     */
    protected void pcClearFields() {
        this.acute = null;
        this.certain = null;
        this.facilityDemographicIssuePk = null;
        this.issueDescription = null;
        this.issueRole = null;
        this.major = null;
        this.resolved = null;
    }

    /**
     * Creates a new persistence-capable instance with an object ID.
     *
     * <p>This method is part of the OpenJPA persistence enhancement framework and is
     * used to instantiate managed entities with identity.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object ID to copy key fields from
     * @param b boolean true to clear all fields after creation
     * @return PersistenceCapable the newly created managed instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicIssue cachedDemographicIssue = new CachedDemographicIssue();
        if (b) {
            cachedDemographicIssue.pcClearFields();
        }
        cachedDemographicIssue.pcStateManager = pcStateManager;
        cachedDemographicIssue.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicIssue;
    }

    /**
     * Creates a new persistence-capable instance without an object ID.
     *
     * <p>This method is part of the OpenJPA persistence enhancement framework and is
     * used to instantiate managed entities without initial identity.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean true to clear all fields after creation
     * @return PersistenceCapable the newly created managed instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicIssue cachedDemographicIssue = new CachedDemographicIssue();
        if (b) {
            cachedDemographicIssue.pcClearFields();
        }
        cachedDemographicIssue.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicIssue;
    }

    /**
     * Returns the total number of managed persistent fields in this entity.
     *
     * <p>This method is part of the OpenJPA persistence enhancement framework.</p>
     *
     * @return int the count of managed fields (currently 7)
     */
    protected static int pcGetManagedFieldCount() {
        return 7;
    }

    /**
     * Replaces a single field value using the state manager.
     *
     * <p>This method is part of the OpenJPA persistence enhancement framework and
     * enables field-level change tracking and lazy loading.</p>
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicIssue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.acute = (Boolean)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.certain = (Boolean)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.facilityDemographicIssuePk = (FacilityIdDemographicIssueCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.issueDescription = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.issueRole = (Role)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.major = (Boolean)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.resolved = (Boolean)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
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
     * <p>This method is part of the OpenJPA persistence enhancement framework and
     * enables batch field replacement for performance optimization.</p>
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
     * <p>This method is part of the OpenJPA persistence enhancement framework and
     * enables field-level state management for persistence operations.</p>
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedDemographicIssue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.acute);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.certain);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityDemographicIssuePk);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.issueDescription);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.issueRole);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.major);
                return;
            }
            case 6: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.resolved);
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
     * <p>This method is part of the OpenJPA persistence enhancement framework and
     * enables batch field state management for performance optimization.</p>
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
     * <p>This method is part of the OpenJPA persistence enhancement framework and
     * supports entity cloning and state transfer operations.</p>
     *
     * @param cachedDemographicIssue CachedDemographicIssue the source instance to copy from
     * @param n int the field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final CachedDemographicIssue cachedDemographicIssue, final int n) {
        final int n2 = n - CachedDemographicIssue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.acute = cachedDemographicIssue.acute;
                return;
            }
            case 1: {
                this.certain = cachedDemographicIssue.certain;
                return;
            }
            case 2: {
                this.facilityDemographicIssuePk = cachedDemographicIssue.facilityDemographicIssuePk;
                return;
            }
            case 3: {
                this.issueDescription = cachedDemographicIssue.issueDescription;
                return;
            }
            case 4: {
                this.issueRole = cachedDemographicIssue.issueRole;
                return;
            }
            case 5: {
                this.major = cachedDemographicIssue.major;
                return;
            }
            case 6: {
                this.resolved = cachedDemographicIssue.resolved;
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
     * <p>This method is part of the OpenJPA persistence enhancement framework and
     * enables batch field copying for entity cloning operations.</p>
     *
     * @param o Object the source instance to copy from (must be a CachedDemographicIssue)
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source object has a different state manager
     * @throws IllegalStateException if the state manager is null
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicIssue cachedDemographicIssue = (CachedDemographicIssue)o;
        if (cachedDemographicIssue.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicIssue, array[i]);
        }
    }

    /**
     * Retrieves the generic context from the state manager.
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
     * Fetches the object ID from the state manager.
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
     * Checks if this entity has been deleted.
     *
     * @return boolean true if the entity is marked for deletion, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has been modified since loading.
     *
     * <p>The dirty state indicates that field values have changed and require
     * persistence to the database.</p>
     *
     * @return boolean true if any fields have been modified, false otherwise
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
     * @return boolean true if the entity is new (transient), false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is in a persistent state.
     *
     * <p>Persistent entities are managed by the persistence context and have
     * database representation.</p>
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
     * @return boolean true if serialization is in progress, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a field as dirty by field name.
     *
     * <p>This method notifies the persistence framework that the specified field
     * has been modified and requires persistence.</p>
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
     * Retrieves the associated state manager.
     *
     * @return StateManager the state manager managing this entity's persistence
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version identifier for optimistic locking.
     *
     * @return Object the version value, or null if no state manager is present
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
     * <p>This method is used during entity state transitions within the persistence
     * lifecycle.</p>
     *
     * @param pcStateManager StateManager the new state manager to assign
     * @throws SecurityException if replacement is not permitted
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
     * @param fieldSupplier FieldSupplier the field supplier (not used)
     * @param o Object the target object ID (not used)
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
     * @param o Object the target object ID (not used)
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key field values from an object ID using a field consumer.
     *
     * <p>This method is part of the OpenJPA persistence enhancement framework and
     * extracts the composite primary key from an object ID.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer to store the key field
     * @param o Object the source object ID containing the key
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(2 + CachedDemographicIssue.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key field values from an object ID.
     *
     * <p>This method is part of the OpenJPA persistence enhancement framework and
     * extracts the composite primary key from an object ID.</p>
     *
     * @param o Object the source object ID containing the key
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityDemographicIssuePk = (FacilityIdDemographicIssueCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * <p>This operation is not supported for composite primary keys.</p>
     *
     * @param o Object the string representation (not used)
     * @return Object not applicable
     * @throws IllegalArgumentException always thrown as this operation is not supported for composite keys
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicIssue\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance for this entity.
     *
     * <p>This method is part of the OpenJPA persistence enhancement framework and
     * creates an object ID wrapper around the composite primary key.</p>
     *
     * @return Object the newly created object ID
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicIssue != null) ? CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicIssue : (CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicIssue = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicIssue")), (Object)this.facilityDemographicIssuePk);
    }
    
    private static final Boolean pcGetacute(final CachedDemographicIssue cachedDemographicIssue) {
        if (cachedDemographicIssue.pcStateManager == null) {
            return cachedDemographicIssue.acute;
        }
        cachedDemographicIssue.pcStateManager.accessingField(CachedDemographicIssue.pcInheritedFieldCount + 0);
        return cachedDemographicIssue.acute;
    }
    
    private static final void pcSetacute(final CachedDemographicIssue cachedDemographicIssue, final Boolean acute) {
        if (cachedDemographicIssue.pcStateManager == null) {
            cachedDemographicIssue.acute = acute;
            return;
        }
        cachedDemographicIssue.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicIssue, CachedDemographicIssue.pcInheritedFieldCount + 0, (Object)cachedDemographicIssue.acute, (Object)acute, 0);
    }
    
    private static final Boolean pcGetcertain(final CachedDemographicIssue cachedDemographicIssue) {
        if (cachedDemographicIssue.pcStateManager == null) {
            return cachedDemographicIssue.certain;
        }
        cachedDemographicIssue.pcStateManager.accessingField(CachedDemographicIssue.pcInheritedFieldCount + 1);
        return cachedDemographicIssue.certain;
    }
    
    private static final void pcSetcertain(final CachedDemographicIssue cachedDemographicIssue, final Boolean certain) {
        if (cachedDemographicIssue.pcStateManager == null) {
            cachedDemographicIssue.certain = certain;
            return;
        }
        cachedDemographicIssue.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicIssue, CachedDemographicIssue.pcInheritedFieldCount + 1, (Object)cachedDemographicIssue.certain, (Object)certain, 0);
    }
    
    private static final FacilityIdDemographicIssueCompositePk pcGetfacilityDemographicIssuePk(final CachedDemographicIssue cachedDemographicIssue) {
        if (cachedDemographicIssue.pcStateManager == null) {
            return cachedDemographicIssue.facilityDemographicIssuePk;
        }
        cachedDemographicIssue.pcStateManager.accessingField(CachedDemographicIssue.pcInheritedFieldCount + 2);
        return cachedDemographicIssue.facilityDemographicIssuePk;
    }
    
    private static final void pcSetfacilityDemographicIssuePk(final CachedDemographicIssue cachedDemographicIssue, final FacilityIdDemographicIssueCompositePk facilityDemographicIssuePk) {
        if (cachedDemographicIssue.pcStateManager == null) {
            cachedDemographicIssue.facilityDemographicIssuePk = facilityDemographicIssuePk;
            return;
        }
        cachedDemographicIssue.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicIssue, CachedDemographicIssue.pcInheritedFieldCount + 2, (Object)cachedDemographicIssue.facilityDemographicIssuePk, (Object)facilityDemographicIssuePk, 0);
    }
    
    private static final String pcGetissueDescription(final CachedDemographicIssue cachedDemographicIssue) {
        if (cachedDemographicIssue.pcStateManager == null) {
            return cachedDemographicIssue.issueDescription;
        }
        cachedDemographicIssue.pcStateManager.accessingField(CachedDemographicIssue.pcInheritedFieldCount + 3);
        return cachedDemographicIssue.issueDescription;
    }
    
    private static final void pcSetissueDescription(final CachedDemographicIssue cachedDemographicIssue, final String issueDescription) {
        if (cachedDemographicIssue.pcStateManager == null) {
            cachedDemographicIssue.issueDescription = issueDescription;
            return;
        }
        cachedDemographicIssue.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicIssue, CachedDemographicIssue.pcInheritedFieldCount + 3, cachedDemographicIssue.issueDescription, issueDescription, 0);
    }
    
    private static final Role pcGetissueRole(final CachedDemographicIssue cachedDemographicIssue) {
        if (cachedDemographicIssue.pcStateManager == null) {
            return cachedDemographicIssue.issueRole;
        }
        cachedDemographicIssue.pcStateManager.accessingField(CachedDemographicIssue.pcInheritedFieldCount + 4);
        return cachedDemographicIssue.issueRole;
    }
    
    private static final void pcSetissueRole(final CachedDemographicIssue cachedDemographicIssue, final Role issueRole) {
        if (cachedDemographicIssue.pcStateManager == null) {
            cachedDemographicIssue.issueRole = issueRole;
            return;
        }
        cachedDemographicIssue.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicIssue, CachedDemographicIssue.pcInheritedFieldCount + 4, (Object)cachedDemographicIssue.issueRole, (Object)issueRole, 0);
    }
    
    private static final Boolean pcGetmajor(final CachedDemographicIssue cachedDemographicIssue) {
        if (cachedDemographicIssue.pcStateManager == null) {
            return cachedDemographicIssue.major;
        }
        cachedDemographicIssue.pcStateManager.accessingField(CachedDemographicIssue.pcInheritedFieldCount + 5);
        return cachedDemographicIssue.major;
    }
    
    private static final void pcSetmajor(final CachedDemographicIssue cachedDemographicIssue, final Boolean major) {
        if (cachedDemographicIssue.pcStateManager == null) {
            cachedDemographicIssue.major = major;
            return;
        }
        cachedDemographicIssue.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicIssue, CachedDemographicIssue.pcInheritedFieldCount + 5, (Object)cachedDemographicIssue.major, (Object)major, 0);
    }
    
    private static final Boolean pcGetresolved(final CachedDemographicIssue cachedDemographicIssue) {
        if (cachedDemographicIssue.pcStateManager == null) {
            return cachedDemographicIssue.resolved;
        }
        cachedDemographicIssue.pcStateManager.accessingField(CachedDemographicIssue.pcInheritedFieldCount + 6);
        return cachedDemographicIssue.resolved;
    }
    
    private static final void pcSetresolved(final CachedDemographicIssue cachedDemographicIssue, final Boolean resolved) {
        if (cachedDemographicIssue.pcStateManager == null) {
            cachedDemographicIssue.resolved = resolved;
            return;
        }
        cachedDemographicIssue.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicIssue, CachedDemographicIssue.pcInheritedFieldCount + 6, (Object)cachedDemographicIssue.resolved, (Object)resolved, 0);
    }
    
    /**
     * Checks if this entity is in a detached state.
     *
     * <p>A detached entity was previously managed by a persistence context but is
     * no longer associated with one. Detached entities can be modified and later
     * reattached (merged) to a persistence context.</p>
     *
     * @return Boolean true if detached, false if not detached, null if state cannot be determined
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
     * <p>This method is part of the OpenJPA persistence enhancement framework.</p>
     *
     * @return boolean always returns false for this entity type
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Retrieves the detached state marker.
     *
     * <p>The detached state tracks whether this entity has been serialized or
     * explicitly detached from a persistence context.</p>
     *
     * @return Object the detached state marker, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state marker.
     *
     * <p>This method is part of the OpenJPA persistence enhancement framework and
     * is used during serialization and detachment operations.</p>
     *
     * @param pcDetachedState Object the detached state marker to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method to handle persistent state during object serialization.
     *
     * <p>This method is invoked during Java serialization and ensures proper handling
     * of persistence-related state. If the entity is being serialized by the persistence
     * framework, the detached state is cleared.</p>
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
     * Custom deserialization method to handle persistent state during object deserialization.
     *
     * <p>This method is invoked during Java deserialization and marks the entity as
     * deserialized to enable proper persistence framework handling.</p>
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
