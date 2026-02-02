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
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * Cached diagnosis research entity for the CAISI Integrator system.
 *
 * This JPA entity represents a cached diagnosis research record that is synchronized across
 * multiple facilities in the CAISI (Community and Institutional System Integration) network.
 * It stores diagnostic codes and research data for patient demographics, supporting cross-facility
 * data sharing and integration in the OpenO EMR system.
 *
 * The class is enhanced by Apache OpenJPA bytecode enhancement for persistence management,
 * implementing the PersistenceCapable interface to support JPA lifecycle operations, state
 * management, and field-level tracking. The enhancement adds runtime persistence capabilities
 * including dirty checking, lazy loading, and detached state management.
 *
 * Key features:
 * <ul>
 *   <li>Cross-facility diagnosis research synchronization</li>
 *   <li>Support for multiple coding systems (ICD-9, ICD-10, SNOMED, etc.)</li>
 *   <li>Temporal tracking with start and update dates</li>
 *   <li>Status-based filtering for active/inactive records</li>
 *   <li>Composite primary key with facility identification</li>
 * </ul>
 *
 * @see FacilityIdIntegerCompositePk
 * @see AbstractModel
 * @see PersistenceCapable
 * @since 2026-01-24
 */
@Entity
public class CachedDxresearch extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedDxresearch>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityDxresearchPk;
    @Column(nullable = false)
    @Index
    private Integer caisiDemographicId;
    @Temporal(TemporalType.DATE)
    private Date startDate;
    @Temporal(TemporalType.DATE)
    private Date updateDate;
    @Column(length = 1)
    private String status;
    @Column(length = 10)
    private String dxresearchCode;
    @Column(length = 20)
    private String codingSystem;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDxresearch;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor initializing all fields to null.
     *
     * Creates a new CachedDxresearch instance with all healthcare-related fields
     * (demographic ID, dates, status, codes) initialized to null values.
     */
    public CachedDxresearch() {
        this.caisiDemographicId = null;
        this.startDate = null;
        this.updateDate = null;
        this.status = null;
        this.dxresearchCode = null;
        this.codingSystem = null;
    }

    /**
     * Retrieves the composite primary key for this diagnosis research record.
     *
     * @return FacilityIdIntegerCompositePk the composite key containing facility ID and item ID
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityDxresearchPk(this);
    }

    /**
     * Sets the composite primary key for this diagnosis research record.
     *
     * @param facilityDxresearchPk FacilityIdIntegerCompositePk the composite key to set
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityDxresearchPk) {
        pcSetfacilityDxresearchPk(this, facilityDxresearchPk);
    }

    /**
     * Retrieves the CAISI demographic identifier.
     *
     * @return Integer the unique identifier for the patient demographic record in the CAISI system
     */
    public Integer getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }

    /**
     * Sets the CAISI demographic identifier.
     *
     * @param caisiDemographicId Integer the unique identifier for the patient demographic record
     */
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }

    /**
     * Retrieves the start date of the diagnosis research record.
     *
     * @return Date the date when this diagnosis research entry became active
     */
    public Date getStartDate() {
        return pcGetstartDate(this);
    }

    /**
     * Sets the start date of the diagnosis research record.
     *
     * @param startDate Date the date when this diagnosis research entry becomes active
     */
    public void setStartDate(final Date startDate) {
        pcSetstartDate(this, startDate);
    }

    /**
     * Retrieves the last update date of the diagnosis research record.
     *
     * @return Date the date when this diagnosis research entry was last modified
     */
    public Date getUpdateDate() {
        return pcGetupdateDate(this);
    }

    /**
     * Sets the last update date of the diagnosis research record.
     *
     * @param updateDate Date the date when this diagnosis research entry was last modified
     */
    public void setUpdateDate(final Date updateDate) {
        pcSetupdateDate(this, updateDate);
    }

    /**
     * Retrieves the status of the diagnosis research record.
     *
     * @return String the current status of the diagnosis (typically 'A' for active, 'D' for deleted)
     */
    public String getStatus() {
        return pcGetstatus(this);
    }

    /**
     * Sets the status of the diagnosis research record.
     *
     * The status value is trimmed to null using StringUtils to ensure consistent handling
     * of empty or whitespace-only strings.
     *
     * @param status String the status to set (typically 'A' for active, 'D' for deleted)
     */
    public void setStatus(final String status) {
        pcSetstatus(this, StringUtils.trimToNull(status));
    }

    /**
     * Retrieves the diagnosis research code.
     *
     * @return String the diagnostic code (e.g., ICD-9, ICD-10, or other coding system code)
     */
    public String getDxresearchCode() {
        return pcGetdxresearchCode(this);
    }

    /**
     * Sets the diagnosis research code.
     *
     * The code value is trimmed to null using StringUtils to ensure consistent handling
     * of empty or whitespace-only strings.
     *
     * @param dxresearchCode String the diagnostic code to set (e.g., ICD-9, ICD-10, or other coding system code)
     */
    public void setDxresearchCode(final String dxresearchCode) {
        pcSetdxresearchCode(this, StringUtils.trimToNull(dxresearchCode));
    }

    /**
     * Retrieves the coding system used for the diagnosis code.
     *
     * @return String the name of the coding system (e.g., "icd9", "icd10", "snomed")
     */
    public String getCodingSystem() {
        return pcGetcodingSystem(this);
    }

    /**
     * Sets the coding system used for the diagnosis code.
     *
     * The coding system value is trimmed to null using StringUtils to ensure consistent
     * handling of empty or whitespace-only strings.
     *
     * @param codingSystem String the name of the coding system (e.g., "icd9", "icd10", "snomed")
     */
    public void setCodingSystem(final String codingSystem) {
        pcSetcodingSystem(this, StringUtils.trimToNull(codingSystem));
    }

    /**
     * Compares this diagnosis research record to another based on CAISI item ID.
     *
     * @param o CachedDxresearch the other diagnosis research record to compare to
     * @return int negative if this ID is less than the other, zero if equal, positive if greater
     */
    @Override
    public int compareTo(final CachedDxresearch o) {
        return pcGetfacilityDxresearchPk(this).getCaisiItemId() - pcGetfacilityDxresearchPk(o).getCaisiItemId();
    }

    /**
     * Retrieves the identifier for this entity.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key identifying this record
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityDxresearchPk(this);
    }

    /**
     * Returns the enhancement contract version for OpenJPA bytecode enhancement.
     *
     * @return int the enhancement contract version number (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -109488893172250055L;
        CachedDxresearch.pcFieldNames = new String[] { "caisiDemographicId", "codingSystem", "dxresearchCode", "facilityDxresearchPk", "startDate", "status", "updateDate" };
        CachedDxresearch.pcFieldTypes = new Class[] { (CachedDxresearch.class$Ljava$lang$Integer != null) ? CachedDxresearch.class$Ljava$lang$Integer : (CachedDxresearch.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedDxresearch.class$Ljava$lang$String != null) ? CachedDxresearch.class$Ljava$lang$String : (CachedDxresearch.class$Ljava$lang$String = class$("java.lang.String")), (CachedDxresearch.class$Ljava$lang$String != null) ? CachedDxresearch.class$Ljava$lang$String : (CachedDxresearch.class$Ljava$lang$String = class$("java.lang.String")), (CachedDxresearch.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedDxresearch.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedDxresearch.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedDxresearch.class$Ljava$util$Date != null) ? CachedDxresearch.class$Ljava$util$Date : (CachedDxresearch.class$Ljava$util$Date = class$("java.util.Date")), (CachedDxresearch.class$Ljava$lang$String != null) ? CachedDxresearch.class$Ljava$lang$String : (CachedDxresearch.class$Ljava$lang$String = class$("java.lang.String")), (CachedDxresearch.class$Ljava$util$Date != null) ? CachedDxresearch.class$Ljava$util$Date : (CachedDxresearch.class$Ljava$util$Date = class$("java.util.Date")) };
        CachedDxresearch.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedDxresearch.class$Lca$openosp$openo$caisi_integrator$dao$CachedDxresearch != null) ? CachedDxresearch.class$Lca$openosp$openo$caisi_integrator$dao$CachedDxresearch : (CachedDxresearch.class$Lca$openosp$openo$caisi_integrator$dao$CachedDxresearch = class$("ca.openosp.openo.caisi_integrator.dao.CachedDxresearch")), CachedDxresearch.pcFieldNames, CachedDxresearch.pcFieldTypes, CachedDxresearch.pcFieldFlags, CachedDxresearch.pcPCSuperclass, "CachedDxresearch", (PersistenceCapable)new CachedDxresearch());
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
        this.codingSystem = null;
        this.dxresearchCode = null;
        this.facilityDxresearchPk = null;
        this.startDate = null;
        this.status = null;
        this.updateDate = null;
    }

    /**
     * Creates a new PersistenceCapable instance with the given state manager and object ID.
     *
     * This method is part of the OpenJPA PersistenceCapable interface and is used by the
     * persistence framework to create new instances during object loading and detachment.
     *
     * @param pcStateManager StateManager the state manager to assign to the new instance
     * @param o Object the object ID to copy key fields from
     * @param b boolean if true, clears all fields after creation
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDxresearch cachedDxresearch = new CachedDxresearch();
        if (b) {
            cachedDxresearch.pcClearFields();
        }
        cachedDxresearch.pcStateManager = pcStateManager;
        cachedDxresearch.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDxresearch;
    }

    /**
     * Creates a new PersistenceCapable instance with the given state manager.
     *
     * This method is part of the OpenJPA PersistenceCapable interface and is used by the
     * persistence framework to create new instances during object instantiation.
     *
     * @param pcStateManager StateManager the state manager to assign to the new instance
     * @param b boolean if true, clears all fields after creation
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDxresearch cachedDxresearch = new CachedDxresearch();
        if (b) {
            cachedDxresearch.pcClearFields();
        }
        cachedDxresearch.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDxresearch;
    }

    /**
     * Returns the number of managed fields in this entity.
     *
     * @return int the count of fields managed by the persistence framework (7)
     */
    protected static int pcGetManagedFieldCount() {
        return 7;
    }

    /**
     * Replaces a single field value with data from the state manager.
     *
     * This method is part of the OpenJPA field management system and is called during
     * object loading and refresh operations.
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDxresearch.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.codingSystem = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.dxresearchCode = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.facilityDxresearchPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.startDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.status = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.updateDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple field values with data from the state manager.
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
     * This method is part of the OpenJPA field management system and is called during
     * object persistence and serialization operations.
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedDxresearch.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiDemographicId);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.codingSystem);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.dxresearchCode);
                return;
            }
            case 3: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityDxresearchPk);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.startDate);
                return;
            }
            case 5: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.status);
                return;
            }
            case 6: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.updateDate);
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
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    protected void pcCopyField(final CachedDxresearch cachedDxresearch, final int n) {
        final int n2 = n - CachedDxresearch.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = cachedDxresearch.caisiDemographicId;
                return;
            }
            case 1: {
                this.codingSystem = cachedDxresearch.codingSystem;
                return;
            }
            case 2: {
                this.dxresearchCode = cachedDxresearch.dxresearchCode;
                return;
            }
            case 3: {
                this.facilityDxresearchPk = cachedDxresearch.facilityDxresearchPk;
                return;
            }
            case 4: {
                this.startDate = cachedDxresearch.startDate;
                return;
            }
            case 5: {
                this.status = cachedDxresearch.status;
                return;
            }
            case 6: {
                this.updateDate = cachedDxresearch.updateDate;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies field values from another instance.
     *
     * This method is used by the persistence framework to copy data between instances
     * that share the same state manager.
     *
     * @param o Object the source object to copy from (must be a CachedDxresearch instance)
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source object has a different state manager
     * @throws IllegalStateException if this instance has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDxresearch cachedDxresearch = (CachedDxresearch)o;
        if (cachedDxresearch.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDxresearch, array[i]);
        }
    }

    /**
     * Retrieves the generic context from the state manager.
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
     * Fetches the object ID from the state manager.
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
     * Checks if this instance has been deleted.
     *
     * @return boolean true if the instance is marked as deleted in the persistence context
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this instance has been modified.
     *
     * @return boolean true if the instance has uncommitted changes, false otherwise
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
     * Checks if this instance is a new persistent object.
     *
     * @return boolean true if the instance is newly created and not yet committed
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this instance is persistent.
     *
     * @return boolean true if the instance is managed by the persistence context
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this instance is transactional.
     *
     * @return boolean true if the instance is participating in a transaction
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this instance is being serialized.
     *
     * @return boolean true if the instance is currently being serialized
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks this instance as dirty with a specific field name.
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
     * Retrieves the state manager for this instance.
     *
     * @return StateManager the state manager managing this instance's persistence state
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version information for optimistic locking.
     *
     * @return Object the version value, or null if no state manager is assigned
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces the state manager for this instance.
     *
     * @param pcStateManager StateManager the new state manager to assign
     * @throws SecurityException if replacing the state manager is not allowed
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
     * This method is not supported for this entity type.
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
     * This method is not supported for this entity type.
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
     * @param fieldConsumer FieldConsumer the field consumer to receive the key field values
     * @param o Object the source object ID
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(3 + CachedDxresearch.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key fields from an object ID.
     *
     * @param o Object the source object ID to copy from
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityDxresearchPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * This method is not supported for ObjectId type.
     *
     * @param o Object the string representation of the object ID
     * @return Object the new object ID instance
     * @throws IllegalArgumentException always thrown as ObjectId does not support String constructor
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDxresearch\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance for this entity.
     *
     * @return Object the new object ID containing this entity's primary key
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDxresearch.class$Lca$openosp$openo$caisi_integrator$dao$CachedDxresearch != null) ? CachedDxresearch.class$Lca$openosp$openo$caisi_integrator$dao$CachedDxresearch : (CachedDxresearch.class$Lca$openosp$openo$caisi_integrator$dao$CachedDxresearch = class$("ca.openosp.openo.caisi_integrator.dao.CachedDxresearch")), (Object)this.facilityDxresearchPk);
    }

    /**
     * Static field accessor for CAISI demographic ID with state management.
     *
     * @param cachedDxresearch CachedDxresearch the instance to access
     * @return Integer the demographic ID value
     */
    private static final Integer pcGetcaisiDemographicId(final CachedDxresearch cachedDxresearch) {
        if (cachedDxresearch.pcStateManager == null) {
            return cachedDxresearch.caisiDemographicId;
        }
        cachedDxresearch.pcStateManager.accessingField(CachedDxresearch.pcInheritedFieldCount + 0);
        return cachedDxresearch.caisiDemographicId;
    }

    /**
     * Static field mutator for CAISI demographic ID with state management.
     *
     * @param cachedDxresearch CachedDxresearch the instance to modify
     * @param caisiDemographicId Integer the demographic ID value to set
     */
    private static final void pcSetcaisiDemographicId(final CachedDxresearch cachedDxresearch, final Integer caisiDemographicId) {
        if (cachedDxresearch.pcStateManager == null) {
            cachedDxresearch.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedDxresearch.pcStateManager.settingObjectField((PersistenceCapable)cachedDxresearch, CachedDxresearch.pcInheritedFieldCount + 0, (Object)cachedDxresearch.caisiDemographicId, (Object)caisiDemographicId, 0);
    }

    /**
     * Static field accessor for coding system with state management.
     *
     * @param cachedDxresearch CachedDxresearch the instance to access
     * @return String the coding system value
     */
    private static final String pcGetcodingSystem(final CachedDxresearch cachedDxresearch) {
        if (cachedDxresearch.pcStateManager == null) {
            return cachedDxresearch.codingSystem;
        }
        cachedDxresearch.pcStateManager.accessingField(CachedDxresearch.pcInheritedFieldCount + 1);
        return cachedDxresearch.codingSystem;
    }

    /**
     * Static field mutator for coding system with state management.
     *
     * @param cachedDxresearch CachedDxresearch the instance to modify
     * @param codingSystem String the coding system value to set
     */
    private static final void pcSetcodingSystem(final CachedDxresearch cachedDxresearch, final String codingSystem) {
        if (cachedDxresearch.pcStateManager == null) {
            cachedDxresearch.codingSystem = codingSystem;
            return;
        }
        cachedDxresearch.pcStateManager.settingStringField((PersistenceCapable)cachedDxresearch, CachedDxresearch.pcInheritedFieldCount + 1, cachedDxresearch.codingSystem, codingSystem, 0);
    }

    /**
     * Static field accessor for diagnosis research code with state management.
     *
     * @param cachedDxresearch CachedDxresearch the instance to access
     * @return String the diagnosis research code value
     */
    private static final String pcGetdxresearchCode(final CachedDxresearch cachedDxresearch) {
        if (cachedDxresearch.pcStateManager == null) {
            return cachedDxresearch.dxresearchCode;
        }
        cachedDxresearch.pcStateManager.accessingField(CachedDxresearch.pcInheritedFieldCount + 2);
        return cachedDxresearch.dxresearchCode;
    }

    /**
     * Static field mutator for diagnosis research code with state management.
     *
     * @param cachedDxresearch CachedDxresearch the instance to modify
     * @param dxresearchCode String the diagnosis research code value to set
     */
    private static final void pcSetdxresearchCode(final CachedDxresearch cachedDxresearch, final String dxresearchCode) {
        if (cachedDxresearch.pcStateManager == null) {
            cachedDxresearch.dxresearchCode = dxresearchCode;
            return;
        }
        cachedDxresearch.pcStateManager.settingStringField((PersistenceCapable)cachedDxresearch, CachedDxresearch.pcInheritedFieldCount + 2, cachedDxresearch.dxresearchCode, dxresearchCode, 0);
    }

    /**
     * Static field accessor for facility diagnosis research primary key with state management.
     *
     * @param cachedDxresearch CachedDxresearch the instance to access
     * @return FacilityIdIntegerCompositePk the composite primary key value
     */
    private static final FacilityIdIntegerCompositePk pcGetfacilityDxresearchPk(final CachedDxresearch cachedDxresearch) {
        if (cachedDxresearch.pcStateManager == null) {
            return cachedDxresearch.facilityDxresearchPk;
        }
        cachedDxresearch.pcStateManager.accessingField(CachedDxresearch.pcInheritedFieldCount + 3);
        return cachedDxresearch.facilityDxresearchPk;
    }

    /**
     * Static field mutator for facility diagnosis research primary key with state management.
     *
     * @param cachedDxresearch CachedDxresearch the instance to modify
     * @param facilityDxresearchPk FacilityIdIntegerCompositePk the composite primary key value to set
     */
    private static final void pcSetfacilityDxresearchPk(final CachedDxresearch cachedDxresearch, final FacilityIdIntegerCompositePk facilityDxresearchPk) {
        if (cachedDxresearch.pcStateManager == null) {
            cachedDxresearch.facilityDxresearchPk = facilityDxresearchPk;
            return;
        }
        cachedDxresearch.pcStateManager.settingObjectField((PersistenceCapable)cachedDxresearch, CachedDxresearch.pcInheritedFieldCount + 3, (Object)cachedDxresearch.facilityDxresearchPk, (Object)facilityDxresearchPk, 0);
    }

    /**
     * Static field accessor for start date with state management.
     *
     * @param cachedDxresearch CachedDxresearch the instance to access
     * @return Date the start date value
     */
    private static final Date pcGetstartDate(final CachedDxresearch cachedDxresearch) {
        if (cachedDxresearch.pcStateManager == null) {
            return cachedDxresearch.startDate;
        }
        cachedDxresearch.pcStateManager.accessingField(CachedDxresearch.pcInheritedFieldCount + 4);
        return cachedDxresearch.startDate;
    }

    /**
     * Static field mutator for start date with state management.
     *
     * @param cachedDxresearch CachedDxresearch the instance to modify
     * @param startDate Date the start date value to set
     */
    private static final void pcSetstartDate(final CachedDxresearch cachedDxresearch, final Date startDate) {
        if (cachedDxresearch.pcStateManager == null) {
            cachedDxresearch.startDate = startDate;
            return;
        }
        cachedDxresearch.pcStateManager.settingObjectField((PersistenceCapable)cachedDxresearch, CachedDxresearch.pcInheritedFieldCount + 4, (Object)cachedDxresearch.startDate, (Object)startDate, 0);
    }

    /**
     * Static field accessor for status with state management.
     *
     * @param cachedDxresearch CachedDxresearch the instance to access
     * @return String the status value
     */
    private static final String pcGetstatus(final CachedDxresearch cachedDxresearch) {
        if (cachedDxresearch.pcStateManager == null) {
            return cachedDxresearch.status;
        }
        cachedDxresearch.pcStateManager.accessingField(CachedDxresearch.pcInheritedFieldCount + 5);
        return cachedDxresearch.status;
    }

    /**
     * Static field mutator for status with state management.
     *
     * @param cachedDxresearch CachedDxresearch the instance to modify
     * @param status String the status value to set
     */
    private static final void pcSetstatus(final CachedDxresearch cachedDxresearch, final String status) {
        if (cachedDxresearch.pcStateManager == null) {
            cachedDxresearch.status = status;
            return;
        }
        cachedDxresearch.pcStateManager.settingStringField((PersistenceCapable)cachedDxresearch, CachedDxresearch.pcInheritedFieldCount + 5, cachedDxresearch.status, status, 0);
    }

    /**
     * Static field accessor for update date with state management.
     *
     * @param cachedDxresearch CachedDxresearch the instance to access
     * @return Date the update date value
     */
    private static final Date pcGetupdateDate(final CachedDxresearch cachedDxresearch) {
        if (cachedDxresearch.pcStateManager == null) {
            return cachedDxresearch.updateDate;
        }
        cachedDxresearch.pcStateManager.accessingField(CachedDxresearch.pcInheritedFieldCount + 6);
        return cachedDxresearch.updateDate;
    }

    /**
     * Static field mutator for update date with state management.
     *
     * @param cachedDxresearch CachedDxresearch the instance to modify
     * @param updateDate Date the update date value to set
     */
    private static final void pcSetupdateDate(final CachedDxresearch cachedDxresearch, final Date updateDate) {
        if (cachedDxresearch.pcStateManager == null) {
            cachedDxresearch.updateDate = updateDate;
            return;
        }
        cachedDxresearch.pcStateManager.settingObjectField((PersistenceCapable)cachedDxresearch, CachedDxresearch.pcInheritedFieldCount + 6, (Object)cachedDxresearch.updateDate, (Object)updateDate, 0);
    }

    /**
     * Checks if this instance is in a detached state.
     *
     * @return Boolean TRUE if detached, FALSE if attached, null if state cannot be determined
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
     * Retrieves the detached state object.
     *
     * @return Object the detached state, or null if not detached
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
