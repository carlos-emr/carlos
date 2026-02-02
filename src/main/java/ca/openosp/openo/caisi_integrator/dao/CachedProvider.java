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
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * Cached healthcare provider entity for the CAISI integrator system.
 *
 * This JPA entity represents a cached copy of healthcare provider information from remote CAISI
 * (Client Access to Integrated Services and Information) facilities. The cache stores essential
 * provider details including name, specialty, and contact information to support cross-facility
 * healthcare collaboration and data sharing within the OpenO EMR integrator framework.
 *
 * The class implements OpenJPA's PersistenceCapable interface to enable byte-code enhancement
 * for optimized JPA operations including field-level dirty tracking, lazy loading, and detached
 * state management. The enhancement is performed at build time by the OpenJPA bytecode enhancer.
 *
 * This entity uses a composite primary key {@link FacilityIdStringCompositePk} to uniquely
 * identify providers across multiple healthcare facilities, ensuring proper data isolation
 * and preventing duplicate provider records when aggregating data from distributed sources.
 *
 * All string fields are automatically trimmed to null using Apache Commons StringUtils to
 * ensure consistent data storage and prevent empty string vs null inconsistencies that can
 * cause issues in healthcare data integration scenarios.
 *
 * @see FacilityIdStringCompositePk
 * @see AbstractModel
 * @see org.apache.openjpa.enhance.PersistenceCapable
 * @since 2026-01-24
 */
@Entity
public class CachedProvider extends AbstractModel<FacilityIdStringCompositePk> implements Comparable<CachedProvider>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdStringCompositePk facilityIdStringCompositePk;
    @Column(length = 64)
    private String firstName;
    @Column(length = 64)
    private String lastName;
    @Column(length = 64)
    private String specialty;
    @Column(length = 64)
    private String workPhone;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedProvider;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Constructs a new CachedProvider instance with null field values.
     *
     * This default constructor initializes all provider fields (firstName, lastName, specialty,
     * workPhone) to null, providing a clean slate for new provider records. Required by JPA
     * specification for entity instantiation and by OpenJPA for bytecode enhancement operations.
     *
     * @see #setFirstName(String)
     * @see #setLastName(String)
     * @see #setSpecialty(String)
     * @see #setWorkPhone(String)
     */
    public CachedProvider() {
        this.firstName = null;
        this.lastName = null;
        this.specialty = null;
        this.workPhone = null;
    }

    /**
     * Retrieves the composite primary key for this cached provider.
     *
     * Returns the facility and ID composite key that uniquely identifies this provider
     * across multiple healthcare facilities in the CAISI integrator network. This method
     * delegates to the OpenJPA-enhanced static accessor to ensure proper field access
     * tracking and lazy loading support.
     *
     * @return FacilityIdStringCompositePk the composite primary key containing facility ID and provider ID
     * @see #setFacilityIdStringCompositePk(FacilityIdStringCompositePk)
     * @see FacilityIdStringCompositePk
     */
    public FacilityIdStringCompositePk getFacilityIdStringCompositePk() {
        return pcGetfacilityIdStringCompositePk(this);
    }

    /**
     * Sets the composite primary key for this cached provider.
     *
     * Assigns the facility and ID composite key that uniquely identifies this provider
     * across multiple healthcare facilities. This method delegates to the OpenJPA-enhanced
     * static mutator to ensure proper state management and dirty field tracking.
     *
     * @param facilityIdStringCompositePk FacilityIdStringCompositePk the composite primary key to assign
     * @see #getFacilityIdStringCompositePk()
     * @see FacilityIdStringCompositePk
     */
    public void setFacilityIdStringCompositePk(final FacilityIdStringCompositePk facilityIdStringCompositePk) {
        pcSetfacilityIdStringCompositePk(this, facilityIdStringCompositePk);
    }

    /**
     * Compares this provider to another provider based on CAISI item ID.
     *
     * Implements natural ordering for CachedProvider objects by comparing the CAISI item IDs
     * from their composite primary keys. This enables consistent sorting of provider collections
     * across the integrator system. The comparison is delegated to the String compareTo method
     * of the CAISI item IDs, providing lexicographic ordering.
     *
     * @param o CachedProvider the provider to compare against
     * @return int negative if this provider's ID is less than the other, zero if equal,
     *         positive if greater than the other provider's ID
     * @see Comparable#compareTo(Object)
     * @see FacilityIdStringCompositePk#getCaisiItemId()
     */
    @Override
    public int compareTo(final CachedProvider o) {
        return pcGetfacilityIdStringCompositePk(this).getCaisiItemId().compareTo(pcGetfacilityIdStringCompositePk(o).getCaisiItemId());
    }

    /**
     * Retrieves the entity identifier for this cached provider.
     *
     * Returns the composite primary key as required by the AbstractModel parent class.
     * This method provides the unique identifier used by the persistence framework for
     * entity lifecycle management, caching, and relationship mapping.
     *
     * @return FacilityIdStringCompositePk the composite primary key identifying this provider
     * @see AbstractModel#getId()
     * @see #getFacilityIdStringCompositePk()
     */
    @Override
    public FacilityIdStringCompositePk getId() {
        return pcGetfacilityIdStringCompositePk(this);
    }

    /**
     * Retrieves the first name of the healthcare provider.
     *
     * Returns the provider's given name as stored in the cache. This method uses the
     * OpenJPA-enhanced accessor to ensure proper persistence state tracking. The value
     * may be null if not set or if trimmed to null during assignment.
     *
     * @return String the provider's first name, or null if not set
     * @see #setFirstName(String)
     */
    public String getFirstName() {
        return pcGetfirstName(this);
    }

    /**
     * Sets the first name of the healthcare provider.
     *
     * Assigns the provider's given name to the cache after trimming whitespace. Empty strings
     * are automatically converted to null to ensure consistent data storage. This method uses
     * the OpenJPA-enhanced mutator for proper dirty field tracking.
     *
     * @param firstName String the provider's first name to store (will be trimmed to null if blank)
     * @see #getFirstName()
     * @see StringUtils#trimToNull(String)
     */
    public void setFirstName(final String firstName) {
        pcSetfirstName(this, StringUtils.trimToNull(firstName));
    }

    /**
     * Retrieves the last name of the healthcare provider.
     *
     * Returns the provider's family name as stored in the cache. This method uses the
     * OpenJPA-enhanced accessor to ensure proper persistence state tracking. The value
     * may be null if not set or if trimmed to null during assignment.
     *
     * @return String the provider's last name, or null if not set
     * @see #setLastName(String)
     */
    public String getLastName() {
        return pcGetlastName(this);
    }

    /**
     * Sets the last name of the healthcare provider.
     *
     * Assigns the provider's family name to the cache after trimming whitespace. Empty strings
     * are automatically converted to null to ensure consistent data storage. This method uses
     * the OpenJPA-enhanced mutator for proper dirty field tracking.
     *
     * @param lastName String the provider's last name to store (will be trimmed to null if blank)
     * @see #getLastName()
     * @see StringUtils#trimToNull(String)
     */
    public void setLastName(final String lastName) {
        pcSetlastName(this, StringUtils.trimToNull(lastName));
    }

    /**
     * Retrieves the medical specialty of the healthcare provider.
     *
     * Returns the provider's area of medical practice or specialization as stored in the cache.
     * This may include values such as "Family Medicine", "Cardiology", "Pediatrics", etc.
     * This method uses the OpenJPA-enhanced accessor to ensure proper persistence state tracking.
     *
     * @return String the provider's medical specialty, or null if not set
     * @see #setSpecialty(String)
     */
    public String getSpecialty() {
        return pcGetspecialty(this);
    }

    /**
     * Sets the medical specialty of the healthcare provider.
     *
     * Assigns the provider's area of medical practice or specialization to the cache after
     * trimming whitespace. Empty strings are automatically converted to null to ensure
     * consistent data storage. This method uses the OpenJPA-enhanced mutator for proper
     * dirty field tracking.
     *
     * @param specialty String the provider's medical specialty to store (will be trimmed to null if blank)
     * @see #getSpecialty()
     * @see StringUtils#trimToNull(String)
     */
    public void setSpecialty(final String specialty) {
        pcSetspecialty(this, StringUtils.trimToNull(specialty));
    }

    /**
     * Retrieves the work phone number of the healthcare provider.
     *
     * Returns the provider's business contact phone number as stored in the cache. This is
     * the primary phone number for professional healthcare communications. This method uses
     * the OpenJPA-enhanced accessor to ensure proper persistence state tracking.
     *
     * @return String the provider's work phone number, or null if not set
     * @see #setWorkPhone(String)
     */
    public String getWorkPhone() {
        return pcGetworkPhone(this);
    }

    /**
     * Sets the work phone number of the healthcare provider.
     *
     * Assigns the provider's business contact phone number to the cache after trimming
     * whitespace. Empty strings are automatically converted to null to ensure consistent
     * data storage. This method uses the OpenJPA-enhanced mutator for proper dirty field
     * tracking.
     *
     * @param workPhone String the provider's work phone number to store (will be trimmed to null if blank)
     * @see #getWorkPhone()
     * @see StringUtils#trimToNull(String)
     */
    public void setWorkPhone(final String workPhone) {
        pcSetworkPhone(this, StringUtils.trimToNull(workPhone));
    }

    /**
     * Returns the OpenJPA bytecode enhancement contract version.
     *
     * This method is part of the PersistenceCapable contract and indicates the version of the
     * OpenJPA enhancement specification implemented by this class. The value 2 corresponds to
     * the OpenJPA enhancement contract version used during bytecode processing.
     *
     * @return int the enhancement contract version number (always returns 2)
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcGetEnhancementContractVersion()
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -3178592570531907948L;
        CachedProvider.pcFieldNames = new String[] { "facilityIdStringCompositePk", "firstName", "lastName", "specialty", "workPhone" };
        CachedProvider.pcFieldTypes = new Class[] { (CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk != null) ? CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk : (CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdStringCompositePk")), (CachedProvider.class$Ljava$lang$String != null) ? CachedProvider.class$Ljava$lang$String : (CachedProvider.class$Ljava$lang$String = class$("java.lang.String")), (CachedProvider.class$Ljava$lang$String != null) ? CachedProvider.class$Ljava$lang$String : (CachedProvider.class$Ljava$lang$String = class$("java.lang.String")), (CachedProvider.class$Ljava$lang$String != null) ? CachedProvider.class$Ljava$lang$String : (CachedProvider.class$Ljava$lang$String = class$("java.lang.String")), (CachedProvider.class$Ljava$lang$String != null) ? CachedProvider.class$Ljava$lang$String : (CachedProvider.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedProvider.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$CachedProvider != null) ? CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$CachedProvider : (CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$CachedProvider = class$("ca.openosp.openo.caisi_integrator.dao.CachedProvider")), CachedProvider.pcFieldNames, CachedProvider.pcFieldTypes, CachedProvider.pcFieldFlags, CachedProvider.pcPCSuperclass, "CachedProvider", (PersistenceCapable)new CachedProvider());
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
     * Clears all managed field values to null.
     *
     * This OpenJPA enhancement method resets all persistent fields (facilityIdStringCompositePk,
     * firstName, lastName, specialty, workPhone) to null. Used internally by the persistence
     * framework during entity initialization and state management operations.
     *
     * @see #pcNewInstance(StateManager, Object, boolean)
     * @see #pcNewInstance(StateManager, boolean)
     */
    protected void pcClearFields() {
        this.facilityIdStringCompositePk = null;
        this.firstName = null;
        this.lastName = null;
        this.specialty = null;
        this.workPhone = null;
    }

    /**
     * Creates a new provider instance with state manager and object ID.
     *
     * This OpenJPA enhancement method constructs a new CachedProvider instance, optionally
     * clearing its fields, assigning the provided state manager, and copying key fields from
     * the given object ID. Used by the persistence framework when loading entities from the
     * database or creating new managed instances.
     *
     * @param pcStateManager StateManager the OpenJPA state manager to assign to the new instance
     * @param o Object the object ID containing key field values to copy
     * @param b boolean true to clear all fields before initialization, false to preserve default values
     * @return PersistenceCapable the newly created provider instance
     * @see #pcClearFields()
     * @see #pcCopyKeyFieldsFromObjectId(Object)
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcNewInstance(StateManager, Object, boolean)
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedProvider cachedProvider = new CachedProvider();
        if (b) {
            cachedProvider.pcClearFields();
        }
        cachedProvider.pcStateManager = pcStateManager;
        cachedProvider.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedProvider;
    }

    /**
     * Creates a new provider instance with state manager.
     *
     * This OpenJPA enhancement method constructs a new CachedProvider instance, optionally
     * clearing its fields and assigning the provided state manager. Used by the persistence
     * framework when creating new managed instances without a specific object ID.
     *
     * @param pcStateManager StateManager the OpenJPA state manager to assign to the new instance
     * @param b boolean true to clear all fields, false to preserve default values
     * @return PersistenceCapable the newly created provider instance
     * @see #pcClearFields()
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcNewInstance(StateManager, boolean)
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedProvider cachedProvider = new CachedProvider();
        if (b) {
            cachedProvider.pcClearFields();
        }
        cachedProvider.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedProvider;
    }

    /**
     * Returns the count of managed persistent fields.
     *
     * This OpenJPA enhancement method returns the total number of fields managed by the
     * persistence framework for this entity class. The count includes all persistent fields:
     * facilityIdStringCompositePk, firstName, lastName, specialty, and workPhone.
     *
     * @return int the number of managed fields (always returns 5)
     */
    protected static int pcGetManagedFieldCount() {
        return 5;
    }

    /**
     * Replaces a single managed field value from the state manager.
     *
     * This OpenJPA enhancement method replaces the value of a specific persistent field
     * by retrieving the new value from the state manager. The field index is adjusted
     * for inheritance and validated before replacement. Used during entity refresh,
     * merge, and detachment operations.
     *
     * @param n int the absolute field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     * @see #pcReplaceFields(int[])
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcReplaceField(int)
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedProvider.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityIdStringCompositePk = (FacilityIdStringCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.firstName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.lastName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.specialty = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.workPhone = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple managed field values from the state manager.
     *
     * This OpenJPA enhancement method replaces the values of specified persistent fields
     * by iterating through the provided field indices and delegating to pcReplaceField
     * for each. Used during bulk entity refresh and merge operations.
     *
     * @param array int[] array of absolute field indices to replace
     * @throws IllegalArgumentException if any field index is invalid
     * @see #pcReplaceField(int)
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcReplaceFields(int[])
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single managed field value to the state manager.
     *
     * This OpenJPA enhancement method sends the current value of a specific persistent field
     * to the state manager for tracking or serialization. The field index is adjusted for
     * inheritance and validated before the value is provided. Used during entity persistence,
     * flush, and detachment operations.
     *
     * @param n int the absolute field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     * @see #pcProvideFields(int[])
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcProvideField(int)
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedProvider.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityIdStringCompositePk);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.firstName);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.lastName);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.specialty);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.workPhone);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Provides multiple managed field values to the state manager.
     *
     * This OpenJPA enhancement method sends the current values of specified persistent fields
     * to the state manager by iterating through the provided field indices and delegating to
     * pcProvideField for each. Used during bulk entity persistence and flush operations.
     *
     * @param array int[] array of absolute field indices to provide
     * @throws IllegalArgumentException if any field index is invalid
     * @see #pcProvideField(int)
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcProvideFields(int[])
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }

    /**
     * Copies a single field value from another provider instance.
     *
     * This OpenJPA enhancement method copies the value of a specific persistent field from
     * the source provider to this instance. The field index is adjusted for inheritance and
     * validated before the copy operation. Used during entity merge and refresh operations.
     *
     * @param cachedProvider CachedProvider the source provider instance to copy from
     * @param n int the absolute field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     * @see #pcCopyFields(Object, int[])
     */
    protected void pcCopyField(final CachedProvider cachedProvider, final int n) {
        final int n2 = n - CachedProvider.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityIdStringCompositePk = cachedProvider.facilityIdStringCompositePk;
                return;
            }
            case 1: {
                this.firstName = cachedProvider.firstName;
                return;
            }
            case 2: {
                this.lastName = cachedProvider.lastName;
                return;
            }
            case 3: {
                this.specialty = cachedProvider.specialty;
                return;
            }
            case 4: {
                this.workPhone = cachedProvider.workPhone;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies multiple field values from another provider instance.
     *
     * This OpenJPA enhancement method copies the values of specified persistent fields from
     * the source object to this instance. Validates that both instances share the same state
     * manager before performing the copy operation. Used during entity merge operations.
     *
     * @param o Object the source provider instance to copy from (must be a CachedProvider)
     * @param array int[] array of absolute field indices to copy
     * @throws IllegalArgumentException if the source object has a different state manager
     * @throws IllegalStateException if the state manager is null
     * @see #pcCopyField(CachedProvider, int)
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcCopyFields(Object, int[])
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedProvider cachedProvider = (CachedProvider)o;
        if (cachedProvider.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedProvider, array[i]);
        }
    }

    /**
     * Retrieves the generic context from the state manager.
     *
     * Returns the generic context object associated with this entity's state manager.
     * The context contains persistence framework-specific metadata and configuration.
     *
     * @return Object the generic context, or null if no state manager is present
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcGetGenericContext()
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the object identifier for this entity.
     *
     * Returns the JPA object ID that uniquely identifies this entity instance within the
     * persistence context. Returns null for transient (non-persistent) instances.
     *
     * @return Object the object identifier, or null if not persistent or no state manager
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcFetchObjectId()
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks if this entity has been marked for deletion.
     *
     * Returns true if this provider entity has been marked for removal in the current
     * transaction but the deletion has not yet been flushed to the database.
     *
     * @return boolean true if marked for deletion, false otherwise
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcIsDeleted()
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has been modified.
     *
     * Returns true if any persistent fields have been changed since the entity was loaded
     * or last synchronized with the database. Triggers a dirty check via RedefinitionHelper
     * to ensure accurate state detection.
     *
     * @return boolean true if any fields have been modified, false otherwise
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcIsDirty()
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
     * Checks if this entity is newly created.
     *
     * Returns true if this provider entity has been persisted (added to the persistence
     * context) but not yet committed to the database.
     *
     * @return boolean true if newly created in the current transaction, false otherwise
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcIsNew()
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is managed by the persistence context.
     *
     * Returns true if this provider entity is persistent (has been loaded from or saved to
     * the database) and is currently managed by the JPA entity manager.
     *
     * @return boolean true if persistent, false if transient
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcIsPersistent()
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity is participating in a transaction.
     *
     * Returns true if this provider entity is currently enlisted in an active JPA transaction.
     *
     * @return boolean true if transactional, false otherwise
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcIsTransactional()
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity is currently being serialized.
     *
     * Returns true if the persistence framework is currently serializing this provider
     * entity, typically during detachment or remote transfer operations.
     *
     * @return boolean true if serialization is in progress, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a field as dirty for persistence tracking.
     *
     * Notifies the state manager that the specified field has been modified and needs to
     * be synchronized with the database. Used internally by enhanced setter methods to
     * maintain dirty field tracking.
     *
     * @param s String the name of the field that was modified
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcDirty(String)
     */
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }

    /**
     * Retrieves the OpenJPA state manager for this entity.
     *
     * Returns the state manager instance responsible for tracking the persistence state
     * and lifecycle of this provider entity. Returns null for transient instances.
     *
     * @return StateManager the state manager, or null if not managed
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcGetStateManager()
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version identifier for optimistic locking.
     *
     * Returns the version value used by the persistence framework for optimistic concurrency
     * control. The version is automatically incremented on each update to detect concurrent
     * modifications.
     *
     * @return Object the version identifier, or null if no state manager or not versioned
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcGetVersion()
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
     * Assigns a new state manager to this provider entity, with proper coordination if
     * a state manager is already present. Used during entity attachment, detachment,
     * and context transitions.
     *
     * @param pcStateManager StateManager the new state manager to assign
     * @throws SecurityException if state manager replacement is not permitted
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcReplaceStateManager(StateManager)
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
     * This method is not supported for this entity type and will throw an InternalException
     * if called. The entity uses embedded ID mapping which does not support this operation.
     *
     * @param fieldSupplier FieldSupplier the field supplier to use for copying
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcCopyKeyFieldsToObjectId(FieldSupplier, Object)
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields to an object ID.
     *
     * This method is not supported for this entity type and will throw an InternalException
     * if called. The entity uses embedded ID mapping which does not support this operation.
     *
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcCopyKeyFieldsToObjectId(Object)
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields from an object ID using a field consumer.
     *
     * Extracts the embedded composite primary key from the provided OpenJPA ObjectId and
     * stores it in the field consumer at the appropriate field index. Used during entity
     * loading and state restoration.
     *
     * @param fieldConsumer FieldConsumer the field consumer to receive the key field
     * @param o Object the source object ID containing the embedded composite key
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcCopyKeyFieldsFromObjectId(FieldConsumer, Object)
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(0 + CachedProvider.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key fields from an object ID to this entity.
     *
     * Extracts the embedded composite primary key from the provided OpenJPA ObjectId and
     * assigns it to this provider entity's facilityIdStringCompositePk field. Used during
     * entity instantiation and state restoration.
     *
     * @param o Object the source object ID containing the embedded composite key
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityIdStringCompositePk = (FacilityIdStringCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * This method is not supported for this entity type because embedded composite IDs
     * cannot be constructed from a simple string representation. Throws an exception
     * explaining the constraint.
     *
     * @param o Object the string representation of the object ID
     * @return Object never returns (always throws exception)
     * @throws IllegalArgumentException always thrown because this ID type does not support
     *         string-based construction
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcNewObjectIdInstance(Object)
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedProvider\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance from this entity's current key fields.
     *
     * Constructs an OpenJPA ObjectId containing this provider's composite primary key
     * (facilityIdStringCompositePk). Used by the persistence framework for caching,
     * identity management, and relationship mapping.
     *
     * @return Object a new ObjectId instance wrapping the composite primary key
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcNewObjectIdInstance()
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$CachedProvider != null) ? CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$CachedProvider : (CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$CachedProvider = class$("ca.openosp.openo.caisi_integrator.dao.CachedProvider")), (Object)this.facilityIdStringCompositePk);
    }

    /**
     * Enhanced static accessor for the composite primary key field.
     *
     * OpenJPA-generated method that provides optimized field access with automatic state
     * manager notification. Used internally by the public getter to ensure proper field
     * tracking during lazy loading and dirty checking.
     *
     * @param cachedProvider CachedProvider the provider instance to access
     * @return FacilityIdStringCompositePk the composite primary key value
     */
    private static final FacilityIdStringCompositePk pcGetfacilityIdStringCompositePk(final CachedProvider cachedProvider) {
        if (cachedProvider.pcStateManager == null) {
            return cachedProvider.facilityIdStringCompositePk;
        }
        cachedProvider.pcStateManager.accessingField(CachedProvider.pcInheritedFieldCount + 0);
        return cachedProvider.facilityIdStringCompositePk;
    }

    /**
     * Enhanced static mutator for the composite primary key field.
     *
     * OpenJPA-generated method that provides optimized field modification with automatic
     * state manager notification for dirty tracking. Used internally by the public setter
     * to ensure proper persistence state management.
     *
     * @param cachedProvider CachedProvider the provider instance to modify
     * @param facilityIdStringCompositePk FacilityIdStringCompositePk the new composite key value
     */
    private static final void pcSetfacilityIdStringCompositePk(final CachedProvider cachedProvider, final FacilityIdStringCompositePk facilityIdStringCompositePk) {
        if (cachedProvider.pcStateManager == null) {
            cachedProvider.facilityIdStringCompositePk = facilityIdStringCompositePk;
            return;
        }
        cachedProvider.pcStateManager.settingObjectField((PersistenceCapable)cachedProvider, CachedProvider.pcInheritedFieldCount + 0, (Object)cachedProvider.facilityIdStringCompositePk, (Object)facilityIdStringCompositePk, 0);
    }

    /**
     * Enhanced static accessor for the firstName field.
     *
     * OpenJPA-generated method that provides optimized field access with automatic state
     * manager notification. Used internally by the public getter to ensure proper field
     * tracking during lazy loading and dirty checking.
     *
     * @param cachedProvider CachedProvider the provider instance to access
     * @return String the firstName value
     */
    private static final String pcGetfirstName(final CachedProvider cachedProvider) {
        if (cachedProvider.pcStateManager == null) {
            return cachedProvider.firstName;
        }
        cachedProvider.pcStateManager.accessingField(CachedProvider.pcInheritedFieldCount + 1);
        return cachedProvider.firstName;
    }

    /**
     * Enhanced static mutator for the firstName field.
     *
     * OpenJPA-generated method that provides optimized field modification with automatic
     * state manager notification for dirty tracking. Used internally by the public setter
     * to ensure proper persistence state management.
     *
     * @param cachedProvider CachedProvider the provider instance to modify
     * @param firstName String the new firstName value
     */
    private static final void pcSetfirstName(final CachedProvider cachedProvider, final String firstName) {
        if (cachedProvider.pcStateManager == null) {
            cachedProvider.firstName = firstName;
            return;
        }
        cachedProvider.pcStateManager.settingStringField((PersistenceCapable)cachedProvider, CachedProvider.pcInheritedFieldCount + 1, cachedProvider.firstName, firstName, 0);
    }

    /**
     * Enhanced static accessor for the lastName field.
     *
     * OpenJPA-generated method that provides optimized field access with automatic state
     * manager notification. Used internally by the public getter to ensure proper field
     * tracking during lazy loading and dirty checking.
     *
     * @param cachedProvider CachedProvider the provider instance to access
     * @return String the lastName value
     */
    private static final String pcGetlastName(final CachedProvider cachedProvider) {
        if (cachedProvider.pcStateManager == null) {
            return cachedProvider.lastName;
        }
        cachedProvider.pcStateManager.accessingField(CachedProvider.pcInheritedFieldCount + 2);
        return cachedProvider.lastName;
    }

    /**
     * Enhanced static mutator for the lastName field.
     *
     * OpenJPA-generated method that provides optimized field modification with automatic
     * state manager notification for dirty tracking. Used internally by the public setter
     * to ensure proper persistence state management.
     *
     * @param cachedProvider CachedProvider the provider instance to modify
     * @param lastName String the new lastName value
     */
    private static final void pcSetlastName(final CachedProvider cachedProvider, final String lastName) {
        if (cachedProvider.pcStateManager == null) {
            cachedProvider.lastName = lastName;
            return;
        }
        cachedProvider.pcStateManager.settingStringField((PersistenceCapable)cachedProvider, CachedProvider.pcInheritedFieldCount + 2, cachedProvider.lastName, lastName, 0);
    }

    /**
     * Enhanced static accessor for the specialty field.
     *
     * OpenJPA-generated method that provides optimized field access with automatic state
     * manager notification. Used internally by the public getter to ensure proper field
     * tracking during lazy loading and dirty checking.
     *
     * @param cachedProvider CachedProvider the provider instance to access
     * @return String the specialty value
     */
    private static final String pcGetspecialty(final CachedProvider cachedProvider) {
        if (cachedProvider.pcStateManager == null) {
            return cachedProvider.specialty;
        }
        cachedProvider.pcStateManager.accessingField(CachedProvider.pcInheritedFieldCount + 3);
        return cachedProvider.specialty;
    }

    /**
     * Enhanced static mutator for the specialty field.
     *
     * OpenJPA-generated method that provides optimized field modification with automatic
     * state manager notification for dirty tracking. Used internally by the public setter
     * to ensure proper persistence state management.
     *
     * @param cachedProvider CachedProvider the provider instance to modify
     * @param specialty String the new specialty value
     */
    private static final void pcSetspecialty(final CachedProvider cachedProvider, final String specialty) {
        if (cachedProvider.pcStateManager == null) {
            cachedProvider.specialty = specialty;
            return;
        }
        cachedProvider.pcStateManager.settingStringField((PersistenceCapable)cachedProvider, CachedProvider.pcInheritedFieldCount + 3, cachedProvider.specialty, specialty, 0);
    }

    /**
     * Enhanced static accessor for the workPhone field.
     *
     * OpenJPA-generated method that provides optimized field access with automatic state
     * manager notification. Used internally by the public getter to ensure proper field
     * tracking during lazy loading and dirty checking.
     *
     * @param cachedProvider CachedProvider the provider instance to access
     * @return String the workPhone value
     */
    private static final String pcGetworkPhone(final CachedProvider cachedProvider) {
        if (cachedProvider.pcStateManager == null) {
            return cachedProvider.workPhone;
        }
        cachedProvider.pcStateManager.accessingField(CachedProvider.pcInheritedFieldCount + 4);
        return cachedProvider.workPhone;
    }

    /**
     * Enhanced static mutator for the workPhone field.
     *
     * OpenJPA-generated method that provides optimized field modification with automatic
     * state manager notification for dirty tracking. Used internally by the public setter
     * to ensure proper persistence state management.
     *
     * @param cachedProvider CachedProvider the provider instance to modify
     * @param workPhone String the new workPhone value
     */
    private static final void pcSetworkPhone(final CachedProvider cachedProvider, final String workPhone) {
        if (cachedProvider.pcStateManager == null) {
            cachedProvider.workPhone = workPhone;
            return;
        }
        cachedProvider.pcStateManager.settingStringField((PersistenceCapable)cachedProvider, CachedProvider.pcInheritedFieldCount + 4, cachedProvider.workPhone, workPhone, 0);
    }

    /**
     * Checks if this entity is in a detached state.
     *
     * Determines whether this provider entity has been detached from its persistence context.
     * Returns Boolean.TRUE if definitely detached, Boolean.FALSE if definitely attached or
     * transient, or null if the detached state cannot be definitively determined.
     *
     * @return Boolean TRUE if detached, FALSE if attached/transient, null if indeterminate
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcIsDetached()
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
     * Returns false to indicate that the detached state stored in this entity is not
     * definitive and should not be solely relied upon for determining detachment status.
     * OpenJPA uses this method in conjunction with other checks.
     *
     * @return boolean always returns false
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Retrieves the detached state object.
     *
     * Returns the detached state marker used by OpenJPA to track whether this entity
     * has been detached from its persistence context. The value may be null (never detached),
     * DESERIALIZED (loaded via serialization), or a version indicator.
     *
     * @return Object the detached state marker
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcGetDetachedState()
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object.
     *
     * Assigns the detached state marker used by OpenJPA to track detachment status.
     * This is typically set to null, DESERIALIZED, or a version indicator depending
     * on the entity lifecycle operations.
     *
     * @param pcDetachedState Object the detached state marker to set
     * @see org.apache.openjpa.enhance.PersistenceCapable#pcSetDetachedState(Object)
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization handler for writing this entity to an output stream.
     *
     * Handles Java serialization by delegating to the default serialization mechanism
     * and clearing the detached state if the entity is being serialized by the persistence
     * framework. This ensures proper state management during entity detachment and transfer.
     *
     * @param objectOutputStream ObjectOutputStream the stream to write this entity to
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
     * Custom deserialization handler for reading this entity from an input stream.
     *
     * Handles Java deserialization by marking the entity as DESERIALIZED before delegating
     * to the default deserialization mechanism. This ensures OpenJPA properly recognizes
     * the entity as detached after being reconstituted from serialized form.
     *
     * @param objectInputStream ObjectInputStream the stream to read this entity from
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if a required class cannot be found during deserialization
     */
    private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        this.pcSetDetachedState(PersistenceCapable.DESERIALIZED);
        objectInputStream.defaultReadObject();
    }
}
