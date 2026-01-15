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

@Entity
public class CachedAppointment extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedAppointment>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityAppointmentPk;
    @Column(nullable = false)
    @Index
    private Integer caisiDemographicId;
    @Column(nullable = false, length = 16)
    private String caisiProviderId;
    @Temporal(TemporalType.DATE)
    private Date appointmentDate;
    @Temporal(TemporalType.TIME)
    private Date startTime;
    @Temporal(TemporalType.TIME)
    private Date endTime;
    @Column(length = 80)
    private String notes;
    @Column(length = 80)
    private String reason;
    @Column(length = 30)
    private String location;
    @Column(length = 255)
    private String resources;
    @Column(length = 10)
    private String type;
    @Column(length = 10)
    private String style;
    @Column(length = 2)
    private String status;
    @Temporal(TemporalType.TIMESTAMP)
    private Date createDatetime;
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateDatetime;
    @Column(length = 50)
    private String remarks;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedAppointment;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedAppointment() {
        this.caisiDemographicId = null;
        this.caisiProviderId = null;
        this.appointmentDate = null;
        this.startTime = null;
        this.endTime = null;
        this.notes = null;
        this.reason = null;
        this.location = null;
        this.resources = null;
        this.type = null;
        this.style = null;
        this.status = null;
        this.createDatetime = null;
        this.updateDatetime = null;
        this.remarks = null;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityAppointmentPk(this);
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityAppointmentPk) {
        pcSetfacilityAppointmentPk(this, facilityAppointmentPk);
    }
    
    public Date getAppointmentDate() {
        return pcGetappointmentDate(this);
    }
    
    public void setAppointmentDate(final Date appointmentDate) {
        pcSetappointmentDate(this, appointmentDate);
    }
    
    public Date getStartTime() {
        return pcGetstartTime(this);
    }
    
    public void setStartTime(final Date startTime) {
        pcSetstartTime(this, startTime);
    }
    
    public Date getEndTime() {
        return pcGetendTime(this);
    }
    
    public void setEndTime(final Date endTime) {
        pcSetendTime(this, endTime);
    }
    
    public Integer getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }
    
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }
    
    public String getCaisiProviderId() {
        return pcGetcaisiProviderId(this);
    }
    
    public void setCaisiProviderId(final String caisiProviderId) {
        pcSetcaisiProviderId(this, StringUtils.trimToEmpty(caisiProviderId));
    }
    
    public String getLocation() {
        return pcGetlocation(this);
    }
    
    public void setLocation(final String location) {
        pcSetlocation(this, StringUtils.trimToNull(location));
    }
    
    public String getNotes() {
        return pcGetnotes(this);
    }
    
    public void setNotes(final String notes) {
        pcSetnotes(this, StringUtils.trimToNull(notes));
    }
    
    public String getReason() {
        return pcGetreason(this);
    }
    
    public void setReason(final String reason) {
        pcSetreason(this, StringUtils.trimToNull(reason));
    }
    
    public String getRemarks() {
        return pcGetremarks(this);
    }
    
    public void setRemarks(final String remarks) {
        pcSetremarks(this, StringUtils.trimToNull(remarks));
    }
    
    public String getResources() {
        return pcGetresources(this);
    }
    
    public void setResources(final String resources) {
        pcSetresources(this, StringUtils.trimToNull(resources));
    }
    
    public String getStatus() {
        return pcGetstatus(this);
    }
    
    public void setStatus(final String status) {
        pcSetstatus(this, StringUtils.trimToNull(status));
    }
    
    public String getStyle() {
        return pcGetstyle(this);
    }
    
    public void setStyle(final String style) {
        pcSetstyle(this, StringUtils.trimToNull(style));
    }
    
    public String getType() {
        return pcGettype(this);
    }
    
    public void setType(final String type) {
        pcSettype(this, StringUtils.trimToNull(type));
    }
    
    public Date getCreateDatetime() {
        return pcGetcreateDatetime(this);
    }
    
    public void setCreateDatetime(final Date createDatetime) {
        pcSetcreateDatetime(this, createDatetime);
    }
    
    public Date getUpdateDatetime() {
        return pcGetupdateDatetime(this);
    }
    
    public void setUpdateDatetime(final Date updateDatetime) {
        pcSetupdateDatetime(this, updateDatetime);
    }
    
    @Override
    public int compareTo(final CachedAppointment o) {
        return pcGetfacilityAppointmentPk(this).getCaisiItemId() - pcGetfacilityAppointmentPk(o).getCaisiItemId();
    }
    
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityAppointmentPk(this);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 4187303396737281637L;
        CachedAppointment.pcFieldNames = new String[] { "appointmentDate", "caisiDemographicId", "caisiProviderId", "createDatetime", "endTime", "facilityAppointmentPk", "location", "notes", "reason", "remarks", "resources", "startTime", "status", "style", "type", "updateDatetime" };
        CachedAppointment.pcFieldTypes = new Class[] { (CachedAppointment.class$Ljava$util$Date != null) ? CachedAppointment.class$Ljava$util$Date : (CachedAppointment.class$Ljava$util$Date = class$("java.util.Date")), (CachedAppointment.class$Ljava$lang$Integer != null) ? CachedAppointment.class$Ljava$lang$Integer : (CachedAppointment.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$util$Date != null) ? CachedAppointment.class$Ljava$util$Date : (CachedAppointment.class$Ljava$util$Date = class$("java.util.Date")), (CachedAppointment.class$Ljava$util$Date != null) ? CachedAppointment.class$Ljava$util$Date : (CachedAppointment.class$Ljava$util$Date = class$("java.util.Date")), (CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$util$Date != null) ? CachedAppointment.class$Ljava$util$Date : (CachedAppointment.class$Ljava$util$Date = class$("java.util.Date")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$lang$String != null) ? CachedAppointment.class$Ljava$lang$String : (CachedAppointment.class$Ljava$lang$String = class$("java.lang.String")), (CachedAppointment.class$Ljava$util$Date != null) ? CachedAppointment.class$Ljava$util$Date : (CachedAppointment.class$Ljava$util$Date = class$("java.util.Date")) };
        CachedAppointment.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$CachedAppointment != null) ? CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$CachedAppointment : (CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$CachedAppointment = class$("ca.openosp.openo.caisi_integrator.dao.CachedAppointment")), CachedAppointment.pcFieldNames, CachedAppointment.pcFieldTypes, CachedAppointment.pcFieldFlags, CachedAppointment.pcPCSuperclass, "CachedAppointment", (PersistenceCapable)new CachedAppointment());
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
        this.appointmentDate = null;
        this.caisiDemographicId = null;
        this.caisiProviderId = null;
        this.createDatetime = null;
        this.endTime = null;
        this.facilityAppointmentPk = null;
        this.location = null;
        this.notes = null;
        this.reason = null;
        this.remarks = null;
        this.resources = null;
        this.startTime = null;
        this.status = null;
        this.style = null;
        this.type = null;
        this.updateDatetime = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedAppointment cachedAppointment = new CachedAppointment();
        if (b) {
            cachedAppointment.pcClearFields();
        }
        cachedAppointment.pcStateManager = pcStateManager;
        cachedAppointment.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedAppointment;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedAppointment cachedAppointment = new CachedAppointment();
        if (b) {
            cachedAppointment.pcClearFields();
        }
        cachedAppointment.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedAppointment;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 16;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedAppointment.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.appointmentDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.caisiDemographicId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.caisiProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.createDatetime = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.endTime = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.facilityAppointmentPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.location = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.notes = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.reason = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.remarks = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 10: {
                this.resources = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 11: {
                this.startTime = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 12: {
                this.status = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 13: {
                this.style = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 14: {
                this.type = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 15: {
                this.updateDatetime = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedAppointment.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.appointmentDate);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiDemographicId);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.caisiProviderId);
                return;
            }
            case 3: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.createDatetime);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.endTime);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityAppointmentPk);
                return;
            }
            case 6: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.location);
                return;
            }
            case 7: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.notes);
                return;
            }
            case 8: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.reason);
                return;
            }
            case 9: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.remarks);
                return;
            }
            case 10: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.resources);
                return;
            }
            case 11: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.startTime);
                return;
            }
            case 12: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.status);
                return;
            }
            case 13: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.style);
                return;
            }
            case 14: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.type);
                return;
            }
            case 15: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.updateDatetime);
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
    
    protected void pcCopyField(final CachedAppointment cachedAppointment, final int n) {
        final int n2 = n - CachedAppointment.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.appointmentDate = cachedAppointment.appointmentDate;
                return;
            }
            case 1: {
                this.caisiDemographicId = cachedAppointment.caisiDemographicId;
                return;
            }
            case 2: {
                this.caisiProviderId = cachedAppointment.caisiProviderId;
                return;
            }
            case 3: {
                this.createDatetime = cachedAppointment.createDatetime;
                return;
            }
            case 4: {
                this.endTime = cachedAppointment.endTime;
                return;
            }
            case 5: {
                this.facilityAppointmentPk = cachedAppointment.facilityAppointmentPk;
                return;
            }
            case 6: {
                this.location = cachedAppointment.location;
                return;
            }
            case 7: {
                this.notes = cachedAppointment.notes;
                return;
            }
            case 8: {
                this.reason = cachedAppointment.reason;
                return;
            }
            case 9: {
                this.remarks = cachedAppointment.remarks;
                return;
            }
            case 10: {
                this.resources = cachedAppointment.resources;
                return;
            }
            case 11: {
                this.startTime = cachedAppointment.startTime;
                return;
            }
            case 12: {
                this.status = cachedAppointment.status;
                return;
            }
            case 13: {
                this.style = cachedAppointment.style;
                return;
            }
            case 14: {
                this.type = cachedAppointment.type;
                return;
            }
            case 15: {
                this.updateDatetime = cachedAppointment.updateDatetime;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedAppointment cachedAppointment = (CachedAppointment)o;
        if (cachedAppointment.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedAppointment, array[i]);
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
        fieldConsumer.storeObjectField(5 + CachedAppointment.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityAppointmentPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedAppointment\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$CachedAppointment != null) ? CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$CachedAppointment : (CachedAppointment.class$Lca$openosp$openo$caisi_integrator$dao$CachedAppointment = class$("ca.openosp.openo.caisi_integrator.dao.CachedAppointment")), (Object)this.facilityAppointmentPk);
    }
    
    private static final Date pcGetappointmentDate(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.appointmentDate;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 0);
        return cachedAppointment.appointmentDate;
    }
    
    private static final void pcSetappointmentDate(final CachedAppointment cachedAppointment, final Date appointmentDate) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.appointmentDate = appointmentDate;
            return;
        }
        cachedAppointment.pcStateManager.settingObjectField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 0, (Object)cachedAppointment.appointmentDate, (Object)appointmentDate, 0);
    }
    
    private static final Integer pcGetcaisiDemographicId(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.caisiDemographicId;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 1);
        return cachedAppointment.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedAppointment cachedAppointment, final Integer caisiDemographicId) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedAppointment.pcStateManager.settingObjectField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 1, (Object)cachedAppointment.caisiDemographicId, (Object)caisiDemographicId, 0);
    }
    
    private static final String pcGetcaisiProviderId(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.caisiProviderId;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 2);
        return cachedAppointment.caisiProviderId;
    }
    
    private static final void pcSetcaisiProviderId(final CachedAppointment cachedAppointment, final String caisiProviderId) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.caisiProviderId = caisiProviderId;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 2, cachedAppointment.caisiProviderId, caisiProviderId, 0);
    }
    
    private static final Date pcGetcreateDatetime(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.createDatetime;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 3);
        return cachedAppointment.createDatetime;
    }
    
    private static final void pcSetcreateDatetime(final CachedAppointment cachedAppointment, final Date createDatetime) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.createDatetime = createDatetime;
            return;
        }
        cachedAppointment.pcStateManager.settingObjectField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 3, (Object)cachedAppointment.createDatetime, (Object)createDatetime, 0);
    }
    
    private static final Date pcGetendTime(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.endTime;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 4);
        return cachedAppointment.endTime;
    }
    
    private static final void pcSetendTime(final CachedAppointment cachedAppointment, final Date endTime) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.endTime = endTime;
            return;
        }
        cachedAppointment.pcStateManager.settingObjectField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 4, (Object)cachedAppointment.endTime, (Object)endTime, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityAppointmentPk(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.facilityAppointmentPk;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 5);
        return cachedAppointment.facilityAppointmentPk;
    }
    
    private static final void pcSetfacilityAppointmentPk(final CachedAppointment cachedAppointment, final FacilityIdIntegerCompositePk facilityAppointmentPk) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.facilityAppointmentPk = facilityAppointmentPk;
            return;
        }
        cachedAppointment.pcStateManager.settingObjectField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 5, (Object)cachedAppointment.facilityAppointmentPk, (Object)facilityAppointmentPk, 0);
    }
    
    private static final String pcGetlocation(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.location;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 6);
        return cachedAppointment.location;
    }
    
    private static final void pcSetlocation(final CachedAppointment cachedAppointment, final String location) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.location = location;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 6, cachedAppointment.location, location, 0);
    }
    
    private static final String pcGetnotes(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.notes;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 7);
        return cachedAppointment.notes;
    }
    
    private static final void pcSetnotes(final CachedAppointment cachedAppointment, final String notes) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.notes = notes;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 7, cachedAppointment.notes, notes, 0);
    }
    
    private static final String pcGetreason(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.reason;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 8);
        return cachedAppointment.reason;
    }
    
    private static final void pcSetreason(final CachedAppointment cachedAppointment, final String reason) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.reason = reason;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 8, cachedAppointment.reason, reason, 0);
    }
    
    private static final String pcGetremarks(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.remarks;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 9);
        return cachedAppointment.remarks;
    }
    
    private static final void pcSetremarks(final CachedAppointment cachedAppointment, final String remarks) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.remarks = remarks;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 9, cachedAppointment.remarks, remarks, 0);
    }
    
    private static final String pcGetresources(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.resources;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 10);
        return cachedAppointment.resources;
    }
    
    private static final void pcSetresources(final CachedAppointment cachedAppointment, final String resources) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.resources = resources;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 10, cachedAppointment.resources, resources, 0);
    }
    
    private static final Date pcGetstartTime(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.startTime;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 11);
        return cachedAppointment.startTime;
    }
    
    private static final void pcSetstartTime(final CachedAppointment cachedAppointment, final Date startTime) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.startTime = startTime;
            return;
        }
        cachedAppointment.pcStateManager.settingObjectField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 11, (Object)cachedAppointment.startTime, (Object)startTime, 0);
    }
    
    private static final String pcGetstatus(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.status;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 12);
        return cachedAppointment.status;
    }
    
    private static final void pcSetstatus(final CachedAppointment cachedAppointment, final String status) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.status = status;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 12, cachedAppointment.status, status, 0);
    }
    
    private static final String pcGetstyle(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.style;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 13);
        return cachedAppointment.style;
    }
    
    private static final void pcSetstyle(final CachedAppointment cachedAppointment, final String style) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.style = style;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 13, cachedAppointment.style, style, 0);
    }
    
    private static final String pcGettype(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.type;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 14);
        return cachedAppointment.type;
    }
    
    private static final void pcSettype(final CachedAppointment cachedAppointment, final String type) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.type = type;
            return;
        }
        cachedAppointment.pcStateManager.settingStringField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 14, cachedAppointment.type, type, 0);
    }
    
    private static final Date pcGetupdateDatetime(final CachedAppointment cachedAppointment) {
        if (cachedAppointment.pcStateManager == null) {
            return cachedAppointment.updateDatetime;
        }
        cachedAppointment.pcStateManager.accessingField(CachedAppointment.pcInheritedFieldCount + 15);
        return cachedAppointment.updateDatetime;
    }
    
    private static final void pcSetupdateDatetime(final CachedAppointment cachedAppointment, final Date updateDatetime) {
        if (cachedAppointment.pcStateManager == null) {
            cachedAppointment.updateDatetime = updateDatetime;
            return;
        }
        cachedAppointment.pcStateManager.settingObjectField((PersistenceCapable)cachedAppointment, CachedAppointment.pcInheritedFieldCount + 15, (Object)cachedAppointment.updateDatetime, (Object)updateDatetime, 0);
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
