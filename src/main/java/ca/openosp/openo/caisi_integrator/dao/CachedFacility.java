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
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Entity;

/**
 * Represents a cached healthcare facility entity for the CAISI (Client Access to Integrated Services and Information) integrator system.
 *
 * This entity stores information about external healthcare facilities that participate in integrated referral and data sharing
 * networks within the OpenO EMR system. The cache allows for efficient lookup and access to facility information without
 * requiring repeated external system queries. It supports Health Information Custodian (HIC) designation, integrated referrals,
 * and SIMS (System for Integrated Management of Services) connectivity for inter-facility healthcare coordination.
 *
 * The class implements JPA PersistenceCapable interface to enable OpenJPA byte-code enhancement for transparent persistence,
 * automatic dirty tracking, and lazy loading capabilities. This implementation includes comprehensive state management methods
 * required by the OpenJPA framework for entity lifecycle management.
 *
 * @see AbstractModel
 * @see PersistenceCapable
 * @see StateManager
 * @since 2026-01-24
 */
@Entity
public class CachedFacility extends AbstractModel<Integer> implements PersistenceCapable
{
    @Id
    private Integer integratorFacilityId;
    @Column(length = 64)
    private String name;
    @Column(length = 255)
    private String description;
    @Column(length = 64)
    private String contactName;
    @Column(length = 64)
    private String contactEmail;
    @Column(length = 64)
    private String contactPhone;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean hic;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean enableIntegratedReferrals;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean allowSims;
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastDataUpdate;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Constructs a new CachedFacility instance with default values.
     *
     * Initializes all fields to their default values: null for reference types, false for boolean flags
     * except for enableIntegratedReferrals and allowSims which default to true to enable inter-facility
     * collaboration by default.
     */
    public CachedFacility() {
        this.integratorFacilityId = null;
        this.name = null;
        this.description = null;
        this.contactName = null;
        this.contactEmail = null;
        this.contactPhone = null;
        this.hic = false;
        this.enableIntegratedReferrals = true;
        this.allowSims = true;
        this.lastDataUpdate = null;
    }

    /**
     * Retrieves the unique integrator facility identifier.
     *
     * @return Integer the unique identifier for this facility in the integrator system
     */
    public Integer getIntegratorFacilityId() {
        return pcGetintegratorFacilityId(this);
    }

    /**
     * Sets the unique integrator facility identifier.
     *
     * @param integratorFacilityId Integer the unique identifier to assign to this facility
     */
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        pcSetintegratorFacilityId(this, integratorFacilityId);
    }

    /**
     * Retrieves the facility name.
     *
     * @return String the name of the healthcare facility
     */
    public String getName() {
        return pcGetname(this);
    }

    /**
     * Sets the facility name.
     *
     * Input is automatically trimmed to null if blank to ensure data consistency.
     *
     * @param name String the name to assign to the healthcare facility
     */
    public void setName(final String name) {
        pcSetname(this, StringUtils.trimToNull(name));
    }

    /**
     * Retrieves the facility description.
     *
     * @return String a detailed description of the healthcare facility
     */
    public String getDescription() {
        return pcGetdescription(this);
    }

    /**
     * Sets the facility description.
     *
     * Input is automatically trimmed to null if blank to ensure data consistency.
     *
     * @param description String a detailed description to assign to the healthcare facility
     */
    public void setDescription(final String description) {
        pcSetdescription(this, StringUtils.trimToNull(description));
    }

    /**
     * Retrieves the contact person's name for this facility.
     *
     * @return String the name of the primary contact person at the healthcare facility
     */
    public String getContactName() {
        return pcGetcontactName(this);
    }

    /**
     * Sets the contact person's name for this facility.
     *
     * Input is automatically trimmed to null if blank to ensure data consistency.
     *
     * @param contactName String the name of the primary contact person to assign
     */
    public void setContactName(final String contactName) {
        pcSetcontactName(this, StringUtils.trimToNull(contactName));
    }

    /**
     * Retrieves the contact email address for this facility.
     *
     * @return String the email address of the primary contact at the healthcare facility
     */
    public String getContactEmail() {
        return pcGetcontactEmail(this);
    }

    /**
     * Sets the contact email address for this facility.
     *
     * Input is automatically trimmed to null if blank to ensure data consistency.
     *
     * @param contactEmail String the email address of the primary contact to assign
     */
    public void setContactEmail(final String contactEmail) {
        pcSetcontactEmail(this, StringUtils.trimToNull(contactEmail));
    }

    /**
     * Retrieves the contact phone number for this facility.
     *
     * @return String the phone number of the primary contact at the healthcare facility
     */
    public String getContactPhone() {
        return pcGetcontactPhone(this);
    }

    /**
     * Sets the contact phone number for this facility.
     *
     * Input is automatically trimmed to null if blank to ensure data consistency.
     *
     * @param contactPhone String the phone number of the primary contact to assign
     */
    public void setContactPhone(final String contactPhone) {
        pcSetcontactPhone(this, StringUtils.trimToNull(contactPhone));
    }

    /**
     * Retrieves the timestamp of the last data update for this facility.
     *
     * @return Date the timestamp indicating when this facility's cached data was last synchronized
     */
    public Date getLastDataUpdate() {
        return pcGetlastDataUpdate(this);
    }

    /**
     * Sets the timestamp of the last data update for this facility.
     *
     * @param lastDataUpdate Date the timestamp to record when this facility's cached data was synchronized
     */
    public void setLastDataUpdate(final Date lastDataUpdate) {
        pcSetlastDataUpdate(this, lastDataUpdate);
    }

    /**
     * Checks if this facility is designated as a Health Information Custodian (HIC).
     *
     * A Health Information Custodian has legal responsibility for maintaining and protecting patient health information
     * under Canadian healthcare privacy regulations (PIPEDA/provincial equivalents).
     *
     * @return boolean true if the facility is a Health Information Custodian, false otherwise
     */
    public boolean isHic() {
        return pcGethic(this);
    }

    /**
     * Sets whether this facility is designated as a Health Information Custodian (HIC).
     *
     * @param hic boolean true to designate the facility as a Health Information Custodian, false otherwise
     */
    public void setHic(final boolean hic) {
        pcSethic(this, hic);
    }

    /**
     * Checks if integrated referrals are enabled for this facility.
     *
     * When enabled, this facility can participate in the integrated referral system, allowing electronic
     * transmission of patient referrals between healthcare providers and facilities.
     *
     * @return boolean true if integrated referrals are enabled, false otherwise
     */
    public boolean isEnableIntegratedReferrals() {
        return pcGetenableIntegratedReferrals(this);
    }

    /**
     * Checks if SIMS (System for Integrated Management of Services) connectivity is allowed for this facility.
     *
     * When enabled, this facility can connect to and share data through the SIMS platform for coordinated
     * healthcare service delivery and case management across multiple providers.
     *
     * @return boolean true if SIMS connectivity is allowed, false otherwise
     */
    public boolean isAllowSims() {
        return pcGetallowSims(this);
    }

    /**
     * Sets whether SIMS (System for Integrated Management of Services) connectivity is allowed for this facility.
     *
     * @param allowSims boolean true to allow SIMS connectivity, false to disable it
     */
    public void setAllowSims(final boolean allowSims) {
        pcSetallowSims(this, allowSims);
    }

    /**
     * Sets whether integrated referrals are enabled for this facility.
     *
     * @param enableIntegratedReferrals boolean true to enable integrated referrals, false to disable them
     */
    public void setEnableIntegratedReferrals(final boolean enableIntegratedReferrals) {
        pcSetenableIntegratedReferrals(this, enableIntegratedReferrals);
    }

    /**
     * Retrieves the entity identifier.
     *
     * This method overrides the AbstractModel getId() method to return the integrator facility ID
     * as the primary identifier for this entity.
     *
     * @return Integer the unique identifier for this facility in the integrator system
     */
    @Override
    public Integer getId() {
        return pcGetintegratorFacilityId(this);
    }

    /**
     * Retrieves the OpenJPA byte-code enhancement contract version.
     *
     * This method is part of the PersistenceCapable interface and indicates which version of the
     * OpenJPA enhancement contract this class implements.
     *
     * @return int the enhancement contract version (2 for current OpenJPA implementation)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -2517194288896303617L;
        CachedFacility.pcFieldNames = new String[] { "allowSims", "contactEmail", "contactName", "contactPhone", "description", "enableIntegratedReferrals", "hic", "integratorFacilityId", "lastDataUpdate", "name" };
        CachedFacility.pcFieldTypes = new Class[] { Boolean.TYPE, (CachedFacility.class$Ljava$lang$String != null) ? CachedFacility.class$Ljava$lang$String : (CachedFacility.class$Ljava$lang$String = class$("java.lang.String")), (CachedFacility.class$Ljava$lang$String != null) ? CachedFacility.class$Ljava$lang$String : (CachedFacility.class$Ljava$lang$String = class$("java.lang.String")), (CachedFacility.class$Ljava$lang$String != null) ? CachedFacility.class$Ljava$lang$String : (CachedFacility.class$Ljava$lang$String = class$("java.lang.String")), (CachedFacility.class$Ljava$lang$String != null) ? CachedFacility.class$Ljava$lang$String : (CachedFacility.class$Ljava$lang$String = class$("java.lang.String")), Boolean.TYPE, Boolean.TYPE, (CachedFacility.class$Ljava$lang$Integer != null) ? CachedFacility.class$Ljava$lang$Integer : (CachedFacility.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedFacility.class$Ljava$util$Date != null) ? CachedFacility.class$Ljava$util$Date : (CachedFacility.class$Ljava$util$Date = class$("java.util.Date")), (CachedFacility.class$Ljava$lang$String != null) ? CachedFacility.class$Ljava$lang$String : (CachedFacility.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedFacility.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility != null) ? CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility : (CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility = class$("ca.openosp.openo.caisi_integrator.dao.CachedFacility")), CachedFacility.pcFieldNames, CachedFacility.pcFieldTypes, CachedFacility.pcFieldFlags, CachedFacility.pcPCSuperclass, "CachedFacility", (PersistenceCapable)new CachedFacility());
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
        this.allowSims = false;
        this.contactEmail = null;
        this.contactName = null;
        this.contactPhone = null;
        this.description = null;
        this.enableIntegratedReferrals = false;
        this.hic = false;
        this.integratorFacilityId = null;
        this.lastDataUpdate = null;
        this.name = null;
    }

    /**
     * Creates a new PersistenceCapable instance with the specified StateManager and object ID.
     *
     * This method is part of the PersistenceCapable interface and is used by OpenJPA to create new instances
     * during entity loading and reconstruction from the database.
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object ID from which to copy key fields
     * @param b boolean true to clear all fields to default values, false to retain initialized values
     * @return PersistenceCapable a new CachedFacility instance configured with the specified state manager
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedFacility cachedFacility = new CachedFacility();
        if (b) {
            cachedFacility.pcClearFields();
        }
        cachedFacility.pcStateManager = pcStateManager;
        cachedFacility.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedFacility;
    }

    /**
     * Creates a new PersistenceCapable instance with the specified StateManager.
     *
     * This method is part of the PersistenceCapable interface and is used by OpenJPA to create new instances
     * without an object ID, typically for new transient entities.
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean true to clear all fields to default values, false to retain initialized values
     * @return PersistenceCapable a new CachedFacility instance configured with the specified state manager
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedFacility cachedFacility = new CachedFacility();
        if (b) {
            cachedFacility.pcClearFields();
        }
        cachedFacility.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedFacility;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 10;
    }

    /**
     * Replaces a single field value using the StateManager.
     *
     * This method is part of the PersistenceCapable interface and is called by OpenJPA during entity
     * state transitions (e.g., loading from database, refresh operations).
     *
     * @param n int the field index to replace (relative to pcInheritedFieldCount)
     * @throws IllegalArgumentException if the field index is invalid or out of range
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedFacility.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.allowSims = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.contactEmail = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.contactName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.contactPhone = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.description = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.enableIntegratedReferrals = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.hic = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.integratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.lastDataUpdate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.name = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple field values using the StateManager.
     *
     * This method is part of the PersistenceCapable interface and efficiently replaces multiple fields
     * at once during entity state transitions.
     *
     * @param array int[] an array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single field value to the StateManager.
     *
     * This method is part of the PersistenceCapable interface and is called by OpenJPA to read field values
     * during flush operations (e.g., when persisting changes to the database).
     *
     * @param n int the field index to provide (relative to pcInheritedFieldCount)
     * @throws IllegalArgumentException if the field index is invalid or out of range
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedFacility.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.allowSims);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.contactEmail);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.contactName);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.contactPhone);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.description);
                return;
            }
            case 5: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.enableIntegratedReferrals);
                return;
            }
            case 6: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.hic);
                return;
            }
            case 7: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.integratorFacilityId);
                return;
            }
            case 8: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.lastDataUpdate);
                return;
            }
            case 9: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.name);
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
     * This method is part of the PersistenceCapable interface and efficiently provides multiple fields
     * at once during flush operations.
     *
     * @param array int[] an array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    protected void pcCopyField(final CachedFacility cachedFacility, final int n) {
        final int n2 = n - CachedFacility.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.allowSims = cachedFacility.allowSims;
                return;
            }
            case 1: {
                this.contactEmail = cachedFacility.contactEmail;
                return;
            }
            case 2: {
                this.contactName = cachedFacility.contactName;
                return;
            }
            case 3: {
                this.contactPhone = cachedFacility.contactPhone;
                return;
            }
            case 4: {
                this.description = cachedFacility.description;
                return;
            }
            case 5: {
                this.enableIntegratedReferrals = cachedFacility.enableIntegratedReferrals;
                return;
            }
            case 6: {
                this.hic = cachedFacility.hic;
                return;
            }
            case 7: {
                this.integratorFacilityId = cachedFacility.integratorFacilityId;
                return;
            }
            case 8: {
                this.lastDataUpdate = cachedFacility.lastDataUpdate;
                return;
            }
            case 9: {
                this.name = cachedFacility.name;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies field values from another CachedFacility instance.
     *
     * This method is part of the PersistenceCapable interface and is used by OpenJPA during merge
     * and copy operations to transfer field values between entities.
     *
     * @param o Object the source CachedFacility object from which to copy field values
     * @param array int[] an array of field indices to copy
     * @throws IllegalArgumentException if the source object has a different StateManager
     * @throws IllegalStateException if this entity has no StateManager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedFacility cachedFacility = (CachedFacility)o;
        if (cachedFacility.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedFacility, array[i]);
        }
    }

    /**
     * Retrieves the generic context from the StateManager.
     *
     * This method is part of the PersistenceCapable interface and provides access to OpenJPA-specific
     * context information associated with this entity.
     *
     * @return Object the generic context object, or null if no StateManager is assigned
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
     * This method is part of the PersistenceCapable interface and retrieves the unique identifier
     * used by OpenJPA to track this entity instance.
     *
     * @return Object the object ID for this entity, or null if no StateManager is assigned
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks if this entity has been marked as deleted.
     *
     * This method is part of the PersistenceCapable interface and indicates whether OpenJPA
     * has marked this entity for deletion from the database.
     *
     * @return boolean true if the entity is marked as deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has unsaved changes.
     *
     * This method is part of the PersistenceCapable interface and indicates whether any fields
     * have been modified since the entity was loaded or last persisted.
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
     * This method is part of the PersistenceCapable interface and indicates whether this entity
     * represents a new object that has not yet been saved to the database.
     *
     * @return boolean true if the entity is new and unsaved, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is persistent (managed by OpenJPA).
     *
     * This method is part of the PersistenceCapable interface and indicates whether this entity
     * is currently managed by the persistence layer and can be saved to the database.
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity is part of an active transaction.
     *
     * This method is part of the PersistenceCapable interface and indicates whether this entity
     * is currently enrolled in a transaction context.
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity is currently being serialized.
     *
     * This method is part of the PersistenceCapable interface and indicates whether the entity
     * is in the process of being serialized for storage or transmission.
     *
     * @return boolean true if the entity is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a field as dirty (modified) to trigger persistence.
     *
     * This method is part of the PersistenceCapable interface and notifies OpenJPA that a specific
     * field has been modified and needs to be persisted to the database.
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
     * Retrieves the StateManager associated with this entity.
     *
     * This method is part of the PersistenceCapable interface and provides access to the OpenJPA
     * StateManager responsible for tracking the lifecycle and state of this entity.
     *
     * @return StateManager the state manager for this entity, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version information for this entity.
     *
     * This method is part of the PersistenceCapable interface and returns version data used
     * for optimistic locking and concurrency control.
     *
     * @return Object the version information, or null if no StateManager is assigned
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces the StateManager for this entity.
     *
     * This method is part of the PersistenceCapable interface and is used by OpenJPA to change
     * the state manager during entity lifecycle transitions.
     *
     * @param pcStateManager StateManager the new state manager to assign to this entity
     * @throws SecurityException if the state manager replacement is not permitted
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
     * This method is part of the PersistenceCapable interface but is not implemented for this entity
     * as it uses a single-field IntId instead of a composite key.
     *
     * @param fieldSupplier FieldSupplier the field supplier to use for copying field values
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields to an object ID.
     *
     * This method is part of the PersistenceCapable interface but is not implemented for this entity
     * as it uses a single-field IntId instead of a composite key.
     *
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields from an object ID using a FieldConsumer.
     *
     * This method is part of the PersistenceCapable interface and extracts the primary key value
     * from the object ID to populate the entity's key field.
     *
     * @param fieldConsumer FieldConsumer the field consumer to use for storing field values
     * @param o Object the source object ID containing the primary key value
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(7 + CachedFacility.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }

    /**
     * Copies key fields from an object ID directly to this entity.
     *
     * This method is part of the PersistenceCapable interface and extracts the primary key value
     * from the IntId object ID to populate the integratorFacilityId field.
     *
     * @param o Object the source object ID (must be an IntId instance)
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.integratorFacilityId = Integer.valueOf(((IntId)o).getId());
    }

    /**
     * Creates a new object ID instance from a String key value.
     *
     * This method is part of the PersistenceCapable interface and constructs an IntId object ID
     * from a string representation of the primary key.
     *
     * @param o Object a String containing the primary key value
     * @return Object a new IntId instance for this entity class
     */
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility != null) ? CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility : (CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility = class$("ca.openosp.openo.caisi_integrator.dao.CachedFacility")), (String)o);
    }

    /**
     * Creates a new object ID instance from this entity's current primary key value.
     *
     * This method is part of the PersistenceCapable interface and constructs an IntId object ID
     * using the current integratorFacilityId value.
     *
     * @return Object a new IntId instance representing this entity's identity
     */
    public Object pcNewObjectIdInstance() {
        return new IntId((CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility != null) ? CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility : (CachedFacility.class$Lca$openosp$openo$caisi_integrator$dao$CachedFacility = class$("ca.openosp.openo.caisi_integrator.dao.CachedFacility")), this.integratorFacilityId);
    }
    
    private static final boolean pcGetallowSims(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.allowSims;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 0);
        return cachedFacility.allowSims;
    }
    
    private static final void pcSetallowSims(final CachedFacility cachedFacility, final boolean allowSims) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.allowSims = allowSims;
            return;
        }
        cachedFacility.pcStateManager.settingBooleanField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 0, cachedFacility.allowSims, allowSims, 0);
    }
    
    private static final String pcGetcontactEmail(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.contactEmail;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 1);
        return cachedFacility.contactEmail;
    }
    
    private static final void pcSetcontactEmail(final CachedFacility cachedFacility, final String contactEmail) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.contactEmail = contactEmail;
            return;
        }
        cachedFacility.pcStateManager.settingStringField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 1, cachedFacility.contactEmail, contactEmail, 0);
    }
    
    private static final String pcGetcontactName(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.contactName;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 2);
        return cachedFacility.contactName;
    }
    
    private static final void pcSetcontactName(final CachedFacility cachedFacility, final String contactName) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.contactName = contactName;
            return;
        }
        cachedFacility.pcStateManager.settingStringField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 2, cachedFacility.contactName, contactName, 0);
    }
    
    private static final String pcGetcontactPhone(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.contactPhone;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 3);
        return cachedFacility.contactPhone;
    }
    
    private static final void pcSetcontactPhone(final CachedFacility cachedFacility, final String contactPhone) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.contactPhone = contactPhone;
            return;
        }
        cachedFacility.pcStateManager.settingStringField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 3, cachedFacility.contactPhone, contactPhone, 0);
    }
    
    private static final String pcGetdescription(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.description;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 4);
        return cachedFacility.description;
    }
    
    private static final void pcSetdescription(final CachedFacility cachedFacility, final String description) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.description = description;
            return;
        }
        cachedFacility.pcStateManager.settingStringField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 4, cachedFacility.description, description, 0);
    }
    
    private static final boolean pcGetenableIntegratedReferrals(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.enableIntegratedReferrals;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 5);
        return cachedFacility.enableIntegratedReferrals;
    }
    
    private static final void pcSetenableIntegratedReferrals(final CachedFacility cachedFacility, final boolean enableIntegratedReferrals) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.enableIntegratedReferrals = enableIntegratedReferrals;
            return;
        }
        cachedFacility.pcStateManager.settingBooleanField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 5, cachedFacility.enableIntegratedReferrals, enableIntegratedReferrals, 0);
    }
    
    private static final boolean pcGethic(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.hic;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 6);
        return cachedFacility.hic;
    }
    
    private static final void pcSethic(final CachedFacility cachedFacility, final boolean hic) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.hic = hic;
            return;
        }
        cachedFacility.pcStateManager.settingBooleanField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 6, cachedFacility.hic, hic, 0);
    }
    
    private static final Integer pcGetintegratorFacilityId(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.integratorFacilityId;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 7);
        return cachedFacility.integratorFacilityId;
    }
    
    private static final void pcSetintegratorFacilityId(final CachedFacility cachedFacility, final Integer integratorFacilityId) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.integratorFacilityId = integratorFacilityId;
            return;
        }
        cachedFacility.pcStateManager.settingObjectField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 7, (Object)cachedFacility.integratorFacilityId, (Object)integratorFacilityId, 0);
    }
    
    private static final Date pcGetlastDataUpdate(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.lastDataUpdate;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 8);
        return cachedFacility.lastDataUpdate;
    }
    
    private static final void pcSetlastDataUpdate(final CachedFacility cachedFacility, final Date lastDataUpdate) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.lastDataUpdate = lastDataUpdate;
            return;
        }
        cachedFacility.pcStateManager.settingObjectField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 8, (Object)cachedFacility.lastDataUpdate, (Object)lastDataUpdate, 0);
    }
    
    private static final String pcGetname(final CachedFacility cachedFacility) {
        if (cachedFacility.pcStateManager == null) {
            return cachedFacility.name;
        }
        cachedFacility.pcStateManager.accessingField(CachedFacility.pcInheritedFieldCount + 9);
        return cachedFacility.name;
    }
    
    private static final void pcSetname(final CachedFacility cachedFacility, final String name) {
        if (cachedFacility.pcStateManager == null) {
            cachedFacility.name = name;
            return;
        }
        cachedFacility.pcStateManager.settingStringField((PersistenceCapable)cachedFacility, CachedFacility.pcInheritedFieldCount + 9, cachedFacility.name, name, 0);
    }

    /**
     * Checks if this entity is in a detached state.
     *
     * This method is part of the PersistenceCapable interface and determines whether the entity
     * is detached from the persistence context (i.e., not currently managed by OpenJPA).
     *
     * @return Boolean true if detached, false if attached, null if the state cannot be determined
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
     * Retrieves the detached state object for this entity.
     *
     * This method is part of the PersistenceCapable interface and returns the state information
     * that OpenJPA uses to track detached entity instances.
     *
     * @return Object the detached state object, or null if not set
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object for this entity.
     *
     * This method is part of the PersistenceCapable interface and stores the state information
     * that OpenJPA uses to manage detached entity instances.
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
