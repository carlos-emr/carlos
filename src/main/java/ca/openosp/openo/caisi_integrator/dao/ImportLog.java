package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.util.LongId;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.util.InternalException;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PersistenceCapable;
import org.apache.openjpa.enhance.PCRegistry;
import javax.persistence.PreRemove;
import org.apache.openjpa.enhance.StateManager;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import org.apache.openjpa.persistence.DataCache;
import javax.persistence.Entity;

/**
 * Entity class representing import log records for the CAISI Integrator healthcare data import system.
 *
 * <p>This class tracks the import history of healthcare data files processed through the CAISI (Client Access to
 * Integrated Services and Information) Integrator. It maintains audit information about data imports including
 * file metadata, processing status, date ranges, and facility associations. This is critical for maintaining
 * data integrity and compliance in inter-EMR data sharing scenarios.</p>
 *
 * <p>The ImportLog entity provides comprehensive tracking of:</p>
 * <ul>
 *   <li>File identification through filename and checksum verification</li>
 *   <li>Temporal data boundaries via date interval start and end timestamps</li>
 *   <li>Processing status tracking for import lifecycle management</li>
 *   <li>Facility-level data segregation for multi-facility environments</li>
 *   <li>Dependency tracking for sequential import processing</li>
 *   <li>Audit trail through creation and update timestamps</li>
 * </ul>
 *
 * <p>This class is enhanced by Apache OpenJPA for persistence and implements the PersistenceCapable interface
 * to support advanced JPA features. Data caching is explicitly disabled to ensure real-time import status
 * visibility across the system.</p>
 *
 * <p><strong>Security Note:</strong> Delete operations are prevented via the {@link #jpaPreventDelete()}
 * callback to maintain permanent audit trail compliance for healthcare data governance requirements.</p>
 *
 * @see AbstractModel
 * @see PersistenceCapable
 * @see ca.openosp.openo.caisi_integrator.dao.AbstractModel
 *
 * @since 2026-01-24
 */
@Entity
@DataCache(enabled = false)
public class ImportLog extends AbstractModel<Long> implements PersistenceCapable
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 255)
    private String filename;
    @Column(length = 255)
    private String checksum;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date dateIntervalStart;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date dateIntervalEnd;
    @Column(length = 50)
    private String status;
    @Index
    private Integer facilityId;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date dateCreated;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date lastUpdatedDate;
    @Column(length = 255)
    private String dependsOn;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$Long;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$ImportLog;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor initializing a new ImportLog entity.
     *
     * <p>Creates a new import log record with the ID field initialized to null. The ID will be
     * automatically assigned by the persistence provider when the entity is persisted to the database.</p>
     */
    public ImportLog() {
        this.id = null;
    }
    
    /**
     * Retrieves the unique identifier for this import log record.
     *
     * @return Long the primary key identifier, or null if not yet persisted
     */
    @Override
    public Long getId() {
        return pcGetid(this);
    }

    /**
     * Sets the unique identifier for this import log record.
     *
     * @param id Long the primary key identifier to set
     */
    public void setId(final Long id) {
        pcSetid(this, id);
    }
    
    /**
     * Retrieves the filename of the imported healthcare data file.
     *
     * @return String the filename of the imported file, maximum length 255 characters
     */
    public String getFilename() {
        return pcGetfilename(this);
    }

    /**
     * Sets the filename of the imported healthcare data file.
     *
     * @param filename String the filename to set, maximum length 255 characters
     */
    public void setFilename(final String filename) {
        pcSetfilename(this, filename);
    }
    
    /**
     * Retrieves the checksum hash of the imported file for integrity verification.
     *
     * <p>The checksum is used to detect duplicate imports and verify file integrity. This is critical
     * for preventing duplicate data processing and ensuring data consistency across the system.</p>
     *
     * @return String the checksum hash value, maximum length 255 characters
     */
    public String getChecksum() {
        return pcGetchecksum(this);
    }

    /**
     * Sets the checksum hash of the imported file.
     *
     * @param checksum String the checksum hash to set, maximum length 255 characters
     */
    public void setChecksum(final String checksum) {
        pcSetchecksum(this, checksum);
    }
    
    /**
     * Retrieves the start date of the data interval covered by this import.
     *
     * <p>This represents the earliest date of healthcare data contained in the imported file.
     * Used for querying and filtering import logs by data coverage period.</p>
     *
     * @return Date the start date of the data interval
     */
    public Date getDateIntervalStart() {
        return pcGetdateIntervalStart(this);
    }

    /**
     * Sets the start date of the data interval covered by this import.
     *
     * @param dateIntervalStart Date the start date to set
     */
    public void setDateIntervalStart(final Date dateIntervalStart) {
        pcSetdateIntervalStart(this, dateIntervalStart);
    }
    
    /**
     * Retrieves the end date of the data interval covered by this import.
     *
     * <p>This represents the latest date of healthcare data contained in the imported file.
     * Together with the start date, defines the temporal coverage of the import.</p>
     *
     * @return Date the end date of the data interval
     */
    public Date getDateIntervalEnd() {
        return pcGetdateIntervalEnd(this);
    }

    /**
     * Sets the end date of the data interval covered by this import.
     *
     * @param dateIntervalEnd Date the end date to set
     */
    public void setDateIntervalEnd(final Date dateIntervalEnd) {
        pcSetdateIntervalEnd(this, dateIntervalEnd);
    }
    
    /**
     * Retrieves the current processing status of this import.
     *
     * <p>Status values typically include states such as "pending", "processing", "completed", "failed",
     * or other application-defined states that track the import lifecycle.</p>
     *
     * @return String the current status, maximum length 50 characters
     */
    public String getStatus() {
        return pcGetstatus(this);
    }

    /**
     * Sets the processing status of this import.
     *
     * @param status String the status to set, maximum length 50 characters
     */
    public void setStatus(final String status) {
        pcSetstatus(this, status);
    }
    
    /**
     * Retrieves the facility identifier associated with this import.
     *
     * <p>The facility ID links this import to a specific healthcare facility or clinic within the system.
     * This supports multi-facility environments where data must be segregated by facility for privacy
     * and organizational requirements. This field is indexed for efficient facility-based queries.</p>
     *
     * @return Integer the facility identifier
     */
    public Integer getFacilityId() {
        return pcGetfacilityId(this);
    }

    /**
     * Sets the facility identifier for this import.
     *
     * @param facilityId Integer the facility identifier to set
     */
    public void setFacilityId(final Integer facilityId) {
        pcSetfacilityId(this, facilityId);
    }
    
    /**
     * Retrieves the timestamp when this import log record was created.
     *
     * <p>This provides an audit trail of when the import was first registered in the system.
     * This timestamp is part of the mandatory audit trail for healthcare data compliance.</p>
     *
     * @return Date the creation timestamp
     */
    public Date getDateCreated() {
        return pcGetdateCreated(this);
    }

    /**
     * Sets the creation timestamp for this import log record.
     *
     * @param dateCreated Date the creation timestamp to set
     */
    public void setDateCreated(final Date dateCreated) {
        pcSetdateCreated(this, dateCreated);
    }
    
    /**
     * Retrieves the timestamp of the last update to this import log record.
     *
     * <p>This tracks when the import record was last modified, supporting audit trail requirements
     * and enabling monitoring of import processing progress over time.</p>
     *
     * @return Date the last update timestamp
     */
    public Date getLastUpdatedDate() {
        return pcGetlastUpdatedDate(this);
    }

    /**
     * Retrieves the dependency identifier indicating prerequisite imports.
     *
     * <p>This field supports sequential import processing where certain imports must be completed
     * before others can proceed. It references the identifier of an import that must complete
     * before this import can be processed.</p>
     *
     * @return String the dependency identifier, maximum length 255 characters
     */
    public String getDependsOn() {
        return pcGetdependsOn(this);
    }

    /**
     * Sets the dependency identifier for sequential import ordering.
     *
     * @param dependsOn String the dependency identifier to set, maximum length 255 characters
     */
    public void setDependsOn(final String dependsOn) {
        pcSetdependsOn(this, dependsOn);
    }

    /**
     * Sets the last update timestamp for this import log record.
     *
     * @param lastUpdatedDate Date the last update timestamp to set
     */
    public void setLastUpdatedDate(final Date lastUpdatedDate) {
        pcSetlastUpdatedDate(this, lastUpdatedDate);
    }
    
    /**
     * JPA lifecycle callback that prevents deletion of import log records.
     *
     * <p>This callback is invoked before entity removal and always throws an exception to prevent
     * deletion. Import logs serve as a permanent audit trail for healthcare data governance and
     * regulatory compliance, and therefore must never be deleted from the system.</p>
     *
     * @throws UnsupportedOperationException always thrown to prevent deletion
     */
    @PreRemove
    protected void jpaPreventDelete() {
        throw new UnsupportedOperationException("Remove is not allowed for this type of item.");
    }
    
    /**
     * Returns the OpenJPA enhancement contract version for this entity.
     *
     * <p>This method is part of the OpenJPA bytecode enhancement infrastructure and indicates
     * the version of the persistence enhancement contract this class implements.</p>
     *
     * @return int the enhancement contract version, always returns 2
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -7008945686433931728L;
        ImportLog.pcFieldNames = new String[] { "checksum", "dateCreated", "dateIntervalEnd", "dateIntervalStart", "dependsOn", "facilityId", "filename", "id", "lastUpdatedDate", "status" };
        ImportLog.pcFieldTypes = new Class[] { (ImportLog.class$Ljava$lang$String != null) ? ImportLog.class$Ljava$lang$String : (ImportLog.class$Ljava$lang$String = class$("java.lang.String")), (ImportLog.class$Ljava$util$Date != null) ? ImportLog.class$Ljava$util$Date : (ImportLog.class$Ljava$util$Date = class$("java.util.Date")), (ImportLog.class$Ljava$util$Date != null) ? ImportLog.class$Ljava$util$Date : (ImportLog.class$Ljava$util$Date = class$("java.util.Date")), (ImportLog.class$Ljava$util$Date != null) ? ImportLog.class$Ljava$util$Date : (ImportLog.class$Ljava$util$Date = class$("java.util.Date")), (ImportLog.class$Ljava$lang$String != null) ? ImportLog.class$Ljava$lang$String : (ImportLog.class$Ljava$lang$String = class$("java.lang.String")), (ImportLog.class$Ljava$lang$Integer != null) ? ImportLog.class$Ljava$lang$Integer : (ImportLog.class$Ljava$lang$Integer = class$("java.lang.Integer")), (ImportLog.class$Ljava$lang$String != null) ? ImportLog.class$Ljava$lang$String : (ImportLog.class$Ljava$lang$String = class$("java.lang.String")), (ImportLog.class$Ljava$lang$Long != null) ? ImportLog.class$Ljava$lang$Long : (ImportLog.class$Ljava$lang$Long = class$("java.lang.Long")), (ImportLog.class$Ljava$util$Date != null) ? ImportLog.class$Ljava$util$Date : (ImportLog.class$Ljava$util$Date = class$("java.util.Date")), (ImportLog.class$Ljava$lang$String != null) ? ImportLog.class$Ljava$lang$String : (ImportLog.class$Ljava$lang$String = class$("java.lang.String")) };
        ImportLog.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((ImportLog.class$Lca$openosp$openo$caisi_integrator$dao$ImportLog != null) ? ImportLog.class$Lca$openosp$openo$caisi_integrator$dao$ImportLog : (ImportLog.class$Lca$openosp$openo$caisi_integrator$dao$ImportLog = class$("ca.openosp.openo.caisi_integrator.dao.ImportLog")), ImportLog.pcFieldNames, ImportLog.pcFieldTypes, ImportLog.pcFieldFlags, ImportLog.pcPCSuperclass, "ImportLog", (PersistenceCapable)new ImportLog());
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
        this.checksum = null;
        this.dateCreated = null;
        this.dateIntervalEnd = null;
        this.dateIntervalStart = null;
        this.dependsOn = null;
        this.facilityId = null;
        this.filename = null;
        this.id = null;
        this.lastUpdatedDate = null;
        this.status = null;
    }
    
    /**
     * Creates a new instance of ImportLog for JPA persistence with an object ID.
     *
     * <p>This method is part of the PersistenceCapable contract and is used by OpenJPA to create
     * new entity instances during persistence operations. It initializes the instance with the
     * provided state manager and copies key fields from the given object ID.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object ID containing key field values
     * @param b boolean if true, fields are cleared after initialization
     * @return PersistenceCapable the newly created ImportLog instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final ImportLog importLog = new ImportLog();
        if (b) {
            importLog.pcClearFields();
        }
        importLog.pcStateManager = pcStateManager;
        importLog.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)importLog;
    }

    /**
     * Creates a new instance of ImportLog for JPA persistence without an object ID.
     *
     * <p>This method is part of the PersistenceCapable contract and creates a new entity instance
     * with the provided state manager but without initializing key fields from an object ID.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean if true, fields are cleared after initialization
     * @return PersistenceCapable the newly created ImportLog instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final ImportLog importLog = new ImportLog();
        if (b) {
            importLog.pcClearFields();
        }
        importLog.pcStateManager = pcStateManager;
        return (PersistenceCapable)importLog;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 10;
    }
    
    /**
     * Replaces a single field value during JPA persistence operations.
     *
     * <p>This method is part of the PersistenceCapable contract and is called by the state manager
     * to update individual field values. The field to update is identified by the field index.</p>
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - ImportLog.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.checksum = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.dateCreated = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.dateIntervalEnd = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.dateIntervalStart = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.dependsOn = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.facilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.filename = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.id = (Long)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.lastUpdatedDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.status = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Replaces multiple field values during JPA persistence operations.
     *
     * <p>This method is part of the PersistenceCapable contract and efficiently updates multiple
     * fields by delegating to {@link #pcReplaceField(int)} for each field index.</p>
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }
    
    /**
     * Provides a single field value to the state manager during JPA operations.
     *
     * <p>This method is part of the PersistenceCapable contract and is called by the persistence
     * provider to retrieve individual field values for persistence operations.</p>
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - ImportLog.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.checksum);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.dateCreated);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.dateIntervalEnd);
                return;
            }
            case 3: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.dateIntervalStart);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.dependsOn);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityId);
                return;
            }
            case 6: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.filename);
                return;
            }
            case 7: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.id);
                return;
            }
            case 8: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.lastUpdatedDate);
                return;
            }
            case 9: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.status);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Provides multiple field values to the state manager during JPA operations.
     *
     * <p>This method is part of the PersistenceCapable contract and efficiently provides multiple
     * field values by delegating to {@link #pcProvideField(int)} for each field index.</p>
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    protected void pcCopyField(final ImportLog importLog, final int n) {
        final int n2 = n - ImportLog.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.checksum = importLog.checksum;
                return;
            }
            case 1: {
                this.dateCreated = importLog.dateCreated;
                return;
            }
            case 2: {
                this.dateIntervalEnd = importLog.dateIntervalEnd;
                return;
            }
            case 3: {
                this.dateIntervalStart = importLog.dateIntervalStart;
                return;
            }
            case 4: {
                this.dependsOn = importLog.dependsOn;
                return;
            }
            case 5: {
                this.facilityId = importLog.facilityId;
                return;
            }
            case 6: {
                this.filename = importLog.filename;
                return;
            }
            case 7: {
                this.id = importLog.id;
                return;
            }
            case 8: {
                this.lastUpdatedDate = importLog.lastUpdatedDate;
                return;
            }
            case 9: {
                this.status = importLog.status;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Copies specified field values from another ImportLog instance.
     *
     * <p>This method is part of the PersistenceCapable contract and copies field values from
     * the source object to this instance. Both objects must share the same state manager.</p>
     *
     * @param o Object the source ImportLog to copy from
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the state managers differ
     * @throws IllegalStateException if the state manager is null
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final ImportLog importLog = (ImportLog)o;
        if (importLog.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(importLog, array[i]);
        }
    }
    
    /**
     * Retrieves the generic context from the associated state manager.
     *
     * @return Object the generic context, or null if no state manager is associated
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the object ID for this entity instance.
     *
     * @return Object the object ID, or null if no state manager is associated
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks if this entity instance has been marked for deletion.
     *
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity instance has been modified since being loaded.
     *
     * <p>Triggers a dirty check through the state manager to ensure the dirty state is current.</p>
     *
     * @return boolean true if the entity has been modified, false otherwise
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
     * @return boolean true if the entity is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity instance is managed by a persistence context.
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity instance is participating in a transaction.
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
     * Marks a specific field as dirty for persistence tracking.
     *
     * <p>Notifies the state manager that the named field has been modified and needs to be
     * persisted when the transaction commits.</p>
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
     * Retrieves the state manager associated with this entity instance.
     *
     * @return StateManager the associated state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version identifier for this entity instance.
     *
     * <p>The version is used for optimistic locking to detect concurrent modifications.</p>
     *
     * @return Object the version identifier, or null if no state manager is associated
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces the state manager for this entity instance.
     *
     * <p>If a state manager is already associated, delegates the replacement to the existing
     * state manager. Otherwise, directly assigns the new state manager.</p>
     *
     * @param pcStateManager StateManager the new state manager to associate
     * @throws SecurityException if the replacement is not permitted
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
     * @param fieldSupplier FieldSupplier the field supplier to receive key field values
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
     * <p>Extracts the ID value from the provided LongId object and stores it using the field consumer.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer to store the key field value
     * @param o Object the source LongId object containing the ID value
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(7 + ImportLog.pcInheritedFieldCount, (Object)Long.valueOf(((LongId)o).getId()));
    }

    /**
     * Copies key field values from an object ID directly to this entity instance.
     *
     * <p>Extracts the ID value from the provided LongId object and assigns it to this entity's ID field.</p>
     *
     * @param o Object the source LongId object containing the ID value
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Long.valueOf(((LongId)o).getId());
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * @param o Object the string representation of the object ID
     * @return Object a new LongId instance
     */
    public Object pcNewObjectIdInstance(final Object o) {
        return new LongId((ImportLog.class$Lca$openosp$openo$caisi_integrator$dao$ImportLog != null) ? ImportLog.class$Lca$openosp$openo$caisi_integrator$dao$ImportLog : (ImportLog.class$Lca$openosp$openo$caisi_integrator$dao$ImportLog = class$("ca.openosp.openo.caisi_integrator.dao.ImportLog")), (String)o);
    }

    /**
     * Creates a new object ID instance from this entity's current ID value.
     *
     * @return Object a new LongId instance initialized with this entity's ID
     */
    public Object pcNewObjectIdInstance() {
        return new LongId((ImportLog.class$Lca$openosp$openo$caisi_integrator$dao$ImportLog != null) ? ImportLog.class$Lca$openosp$openo$caisi_integrator$dao$ImportLog : (ImportLog.class$Lca$openosp$openo$caisi_integrator$dao$ImportLog = class$("ca.openosp.openo.caisi_integrator.dao.ImportLog")), this.id);
    }
    
    private static final String pcGetchecksum(final ImportLog importLog) {
        if (importLog.pcStateManager == null) {
            return importLog.checksum;
        }
        importLog.pcStateManager.accessingField(ImportLog.pcInheritedFieldCount + 0);
        return importLog.checksum;
    }
    
    private static final void pcSetchecksum(final ImportLog importLog, final String checksum) {
        if (importLog.pcStateManager == null) {
            importLog.checksum = checksum;
            return;
        }
        importLog.pcStateManager.settingStringField((PersistenceCapable)importLog, ImportLog.pcInheritedFieldCount + 0, importLog.checksum, checksum, 0);
    }
    
    private static final Date pcGetdateCreated(final ImportLog importLog) {
        if (importLog.pcStateManager == null) {
            return importLog.dateCreated;
        }
        importLog.pcStateManager.accessingField(ImportLog.pcInheritedFieldCount + 1);
        return importLog.dateCreated;
    }
    
    private static final void pcSetdateCreated(final ImportLog importLog, final Date dateCreated) {
        if (importLog.pcStateManager == null) {
            importLog.dateCreated = dateCreated;
            return;
        }
        importLog.pcStateManager.settingObjectField((PersistenceCapable)importLog, ImportLog.pcInheritedFieldCount + 1, (Object)importLog.dateCreated, (Object)dateCreated, 0);
    }
    
    private static final Date pcGetdateIntervalEnd(final ImportLog importLog) {
        if (importLog.pcStateManager == null) {
            return importLog.dateIntervalEnd;
        }
        importLog.pcStateManager.accessingField(ImportLog.pcInheritedFieldCount + 2);
        return importLog.dateIntervalEnd;
    }
    
    private static final void pcSetdateIntervalEnd(final ImportLog importLog, final Date dateIntervalEnd) {
        if (importLog.pcStateManager == null) {
            importLog.dateIntervalEnd = dateIntervalEnd;
            return;
        }
        importLog.pcStateManager.settingObjectField((PersistenceCapable)importLog, ImportLog.pcInheritedFieldCount + 2, (Object)importLog.dateIntervalEnd, (Object)dateIntervalEnd, 0);
    }
    
    private static final Date pcGetdateIntervalStart(final ImportLog importLog) {
        if (importLog.pcStateManager == null) {
            return importLog.dateIntervalStart;
        }
        importLog.pcStateManager.accessingField(ImportLog.pcInheritedFieldCount + 3);
        return importLog.dateIntervalStart;
    }
    
    private static final void pcSetdateIntervalStart(final ImportLog importLog, final Date dateIntervalStart) {
        if (importLog.pcStateManager == null) {
            importLog.dateIntervalStart = dateIntervalStart;
            return;
        }
        importLog.pcStateManager.settingObjectField((PersistenceCapable)importLog, ImportLog.pcInheritedFieldCount + 3, (Object)importLog.dateIntervalStart, (Object)dateIntervalStart, 0);
    }
    
    private static final String pcGetdependsOn(final ImportLog importLog) {
        if (importLog.pcStateManager == null) {
            return importLog.dependsOn;
        }
        importLog.pcStateManager.accessingField(ImportLog.pcInheritedFieldCount + 4);
        return importLog.dependsOn;
    }
    
    private static final void pcSetdependsOn(final ImportLog importLog, final String dependsOn) {
        if (importLog.pcStateManager == null) {
            importLog.dependsOn = dependsOn;
            return;
        }
        importLog.pcStateManager.settingStringField((PersistenceCapable)importLog, ImportLog.pcInheritedFieldCount + 4, importLog.dependsOn, dependsOn, 0);
    }
    
    private static final Integer pcGetfacilityId(final ImportLog importLog) {
        if (importLog.pcStateManager == null) {
            return importLog.facilityId;
        }
        importLog.pcStateManager.accessingField(ImportLog.pcInheritedFieldCount + 5);
        return importLog.facilityId;
    }
    
    private static final void pcSetfacilityId(final ImportLog importLog, final Integer facilityId) {
        if (importLog.pcStateManager == null) {
            importLog.facilityId = facilityId;
            return;
        }
        importLog.pcStateManager.settingObjectField((PersistenceCapable)importLog, ImportLog.pcInheritedFieldCount + 5, (Object)importLog.facilityId, (Object)facilityId, 0);
    }
    
    private static final String pcGetfilename(final ImportLog importLog) {
        if (importLog.pcStateManager == null) {
            return importLog.filename;
        }
        importLog.pcStateManager.accessingField(ImportLog.pcInheritedFieldCount + 6);
        return importLog.filename;
    }
    
    private static final void pcSetfilename(final ImportLog importLog, final String filename) {
        if (importLog.pcStateManager == null) {
            importLog.filename = filename;
            return;
        }
        importLog.pcStateManager.settingStringField((PersistenceCapable)importLog, ImportLog.pcInheritedFieldCount + 6, importLog.filename, filename, 0);
    }
    
    private static final Long pcGetid(final ImportLog importLog) {
        if (importLog.pcStateManager == null) {
            return importLog.id;
        }
        importLog.pcStateManager.accessingField(ImportLog.pcInheritedFieldCount + 7);
        return importLog.id;
    }
    
    private static final void pcSetid(final ImportLog importLog, final Long id) {
        if (importLog.pcStateManager == null) {
            importLog.id = id;
            return;
        }
        importLog.pcStateManager.settingObjectField((PersistenceCapable)importLog, ImportLog.pcInheritedFieldCount + 7, (Object)importLog.id, (Object)id, 0);
    }
    
    private static final Date pcGetlastUpdatedDate(final ImportLog importLog) {
        if (importLog.pcStateManager == null) {
            return importLog.lastUpdatedDate;
        }
        importLog.pcStateManager.accessingField(ImportLog.pcInheritedFieldCount + 8);
        return importLog.lastUpdatedDate;
    }
    
    private static final void pcSetlastUpdatedDate(final ImportLog importLog, final Date lastUpdatedDate) {
        if (importLog.pcStateManager == null) {
            importLog.lastUpdatedDate = lastUpdatedDate;
            return;
        }
        importLog.pcStateManager.settingObjectField((PersistenceCapable)importLog, ImportLog.pcInheritedFieldCount + 8, (Object)importLog.lastUpdatedDate, (Object)lastUpdatedDate, 0);
    }
    
    private static final String pcGetstatus(final ImportLog importLog) {
        if (importLog.pcStateManager == null) {
            return importLog.status;
        }
        importLog.pcStateManager.accessingField(ImportLog.pcInheritedFieldCount + 9);
        return importLog.status;
    }
    
    private static final void pcSetstatus(final ImportLog importLog, final String status) {
        if (importLog.pcStateManager == null) {
            importLog.status = status;
            return;
        }
        importLog.pcStateManager.settingStringField((PersistenceCapable)importLog, ImportLog.pcInheritedFieldCount + 9, importLog.status, status, 0);
    }
    
    /**
     * Determines if this entity instance is in a detached state.
     *
     * <p>An entity is detached if it was previously managed by a persistence context but is
     * no longer associated with an active session. This method checks various state indicators
     * to make this determination.</p>
     *
     * @return Boolean true if detached, false if attached, or null if state is indeterminate
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
     * Retrieves the detached state object for this entity instance.
     *
     * <p>The detached state contains metadata used by the persistence provider to track
     * the entity's state when it is not actively managed by a persistence context.</p>
     *
     * @return Object the detached state object, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object for this entity instance.
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
