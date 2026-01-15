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
    
    public ImportLog() {
        this.id = null;
    }
    
    @Override
    public Long getId() {
        return pcGetid(this);
    }
    
    public void setId(final Long id) {
        pcSetid(this, id);
    }
    
    public String getFilename() {
        return pcGetfilename(this);
    }
    
    public void setFilename(final String filename) {
        pcSetfilename(this, filename);
    }
    
    public String getChecksum() {
        return pcGetchecksum(this);
    }
    
    public void setChecksum(final String checksum) {
        pcSetchecksum(this, checksum);
    }
    
    public Date getDateIntervalStart() {
        return pcGetdateIntervalStart(this);
    }
    
    public void setDateIntervalStart(final Date dateIntervalStart) {
        pcSetdateIntervalStart(this, dateIntervalStart);
    }
    
    public Date getDateIntervalEnd() {
        return pcGetdateIntervalEnd(this);
    }
    
    public void setDateIntervalEnd(final Date dateIntervalEnd) {
        pcSetdateIntervalEnd(this, dateIntervalEnd);
    }
    
    public String getStatus() {
        return pcGetstatus(this);
    }
    
    public void setStatus(final String status) {
        pcSetstatus(this, status);
    }
    
    public Integer getFacilityId() {
        return pcGetfacilityId(this);
    }
    
    public void setFacilityId(final Integer facilityId) {
        pcSetfacilityId(this, facilityId);
    }
    
    public Date getDateCreated() {
        return pcGetdateCreated(this);
    }
    
    public void setDateCreated(final Date dateCreated) {
        pcSetdateCreated(this, dateCreated);
    }
    
    public Date getLastUpdatedDate() {
        return pcGetlastUpdatedDate(this);
    }
    
    public String getDependsOn() {
        return pcGetdependsOn(this);
    }
    
    public void setDependsOn(final String dependsOn) {
        pcSetdependsOn(this, dependsOn);
    }
    
    public void setLastUpdatedDate(final Date lastUpdatedDate) {
        pcSetlastUpdatedDate(this, lastUpdatedDate);
    }
    
    @PreRemove
    protected void jpaPreventDelete() {
        throw new UnsupportedOperationException("Remove is not allowed for this type of item.");
    }
    
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
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final ImportLog importLog = new ImportLog();
        if (b) {
            importLog.pcClearFields();
        }
        importLog.pcStateManager = pcStateManager;
        importLog.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)importLog;
    }
    
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
    
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }
    
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
    
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }
    
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }
    
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }
    
    public boolean pcIsDirty() {
        if (this.pcStateManager == null) {
            return false;
        }
        final StateManager pcStateManager = this.pcStateManager;
        RedefinitionHelper.dirtyCheck(pcStateManager);
        return pcStateManager.isDirty();
    }
    
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }
    
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }
    
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }
    
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }
    
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }
    
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }
    
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }
    
    public void pcReplaceStateManager(final StateManager pcStateManager) throws SecurityException {
        if (this.pcStateManager != null) {
            this.pcStateManager = this.pcStateManager.replaceStateManager(pcStateManager);
            return;
        }
        this.pcStateManager = pcStateManager;
    }
    
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }
    
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }
    
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(7 + ImportLog.pcInheritedFieldCount, (Object)Long.valueOf(((LongId)o).getId()));
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Long.valueOf(((LongId)o).getId());
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        return new LongId((ImportLog.class$Lca$openosp$openo$caisi_integrator$dao$ImportLog != null) ? ImportLog.class$Lca$openosp$openo$caisi_integrator$dao$ImportLog : (ImportLog.class$Lca$openosp$openo$caisi_integrator$dao$ImportLog = class$("ca.openosp.openo.caisi_integrator.dao.ImportLog")), (String)o);
    }
    
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
    
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }
    
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
