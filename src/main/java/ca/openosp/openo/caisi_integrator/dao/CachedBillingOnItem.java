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
public class CachedBillingOnItem extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedBillingOnItem>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityBillingOnItemPk;
    @Column(nullable = false)
    @Index
    private Integer caisiDemographicId;
    @Column(nullable = false, length = 16)
    @Index
    private String caisiProviderId;
    @Column(length = 16)
    private String apptProviderId;
    @Column(length = 16)
    private String asstProviderId;
    @Column
    private Integer appointmentId;
    @Temporal(TemporalType.DATE)
    private Date serviceDate;
    @Column(length = 20)
    private String serviceCode;
    @Column(length = 4)
    private String dx;
    @Column(length = 4)
    private String dx1;
    @Column(length = 4)
    private String dx2;
    @Column(length = 1)
    private String status;
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
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedBillingOnItem;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedBillingOnItem() {
        this.caisiDemographicId = null;
        this.caisiProviderId = null;
        this.apptProviderId = null;
        this.asstProviderId = null;
        this.appointmentId = null;
        this.serviceDate = null;
        this.serviceCode = null;
        this.dx = null;
        this.dx1 = null;
        this.dx2 = null;
        this.status = null;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityBillingOnItemPk(this);
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityBillingOnItemPk) {
        pcSetfacilityBillingOnItemPk(this, facilityBillingOnItemPk);
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
    
    public String getApptProviderId() {
        return pcGetapptProviderId(this);
    }
    
    public void setApptProviderId(final String apptProviderId) {
        pcSetapptProviderId(this, StringUtils.trimToNull(apptProviderId));
    }
    
    public String getAsstProviderId() {
        return pcGetasstProviderId(this);
    }
    
    public void setAsstProviderId(final String asstProviderId) {
        pcSetasstProviderId(this, StringUtils.trimToNull(asstProviderId));
    }
    
    public Integer getAppointmentId() {
        return pcGetappointmentId(this);
    }
    
    public void setAppointmentId(final Integer appointmentId) {
        pcSetappointmentId(this, appointmentId);
    }
    
    public Date getServiceDate() {
        return pcGetserviceDate(this);
    }
    
    public void setServiceDate(final Date serviceDate) {
        pcSetserviceDate(this, serviceDate);
    }
    
    public String getServiceCode() {
        return pcGetserviceCode(this);
    }
    
    public void setServiceCode(final String serviceCode) {
        pcSetserviceCode(this, StringUtils.trimToNull(serviceCode));
    }
    
    public String getDx() {
        return pcGetdx(this);
    }
    
    public void setDx(final String dx) {
        pcSetdx(this, StringUtils.trimToNull(dx));
    }
    
    public String getDx1() {
        return pcGetdx1(this);
    }
    
    public void setDx1(final String dx) {
        pcSetdx1(this, StringUtils.trimToNull(dx));
    }
    
    public String getDx2() {
        return pcGetdx2(this);
    }
    
    public void setDx2(final String dx) {
        pcSetdx2(this, StringUtils.trimToNull(dx));
    }
    
    public String getStatus() {
        return pcGetstatus(this);
    }
    
    public void setStatus(final String status) {
        pcSetstatus(this, status);
    }
    
    @Override
    public int compareTo(final CachedBillingOnItem o) {
        return pcGetfacilityBillingOnItemPk(this).getCaisiItemId() - pcGetfacilityBillingOnItemPk(o).getCaisiItemId();
    }
    
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityBillingOnItemPk(this);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -7391385481748879875L;
        CachedBillingOnItem.pcFieldNames = new String[] { "appointmentId", "apptProviderId", "asstProviderId", "caisiDemographicId", "caisiProviderId", "dx", "dx1", "dx2", "facilityBillingOnItemPk", "serviceCode", "serviceDate", "status" };
        CachedBillingOnItem.pcFieldTypes = new Class[] { (CachedBillingOnItem.class$Ljava$lang$Integer != null) ? CachedBillingOnItem.class$Ljava$lang$Integer : (CachedBillingOnItem.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedBillingOnItem.class$Ljava$lang$String != null) ? CachedBillingOnItem.class$Ljava$lang$String : (CachedBillingOnItem.class$Ljava$lang$String = class$("java.lang.String")), (CachedBillingOnItem.class$Ljava$lang$String != null) ? CachedBillingOnItem.class$Ljava$lang$String : (CachedBillingOnItem.class$Ljava$lang$String = class$("java.lang.String")), (CachedBillingOnItem.class$Ljava$lang$Integer != null) ? CachedBillingOnItem.class$Ljava$lang$Integer : (CachedBillingOnItem.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedBillingOnItem.class$Ljava$lang$String != null) ? CachedBillingOnItem.class$Ljava$lang$String : (CachedBillingOnItem.class$Ljava$lang$String = class$("java.lang.String")), (CachedBillingOnItem.class$Ljava$lang$String != null) ? CachedBillingOnItem.class$Ljava$lang$String : (CachedBillingOnItem.class$Ljava$lang$String = class$("java.lang.String")), (CachedBillingOnItem.class$Ljava$lang$String != null) ? CachedBillingOnItem.class$Ljava$lang$String : (CachedBillingOnItem.class$Ljava$lang$String = class$("java.lang.String")), (CachedBillingOnItem.class$Ljava$lang$String != null) ? CachedBillingOnItem.class$Ljava$lang$String : (CachedBillingOnItem.class$Ljava$lang$String = class$("java.lang.String")), (CachedBillingOnItem.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedBillingOnItem.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedBillingOnItem.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedBillingOnItem.class$Ljava$lang$String != null) ? CachedBillingOnItem.class$Ljava$lang$String : (CachedBillingOnItem.class$Ljava$lang$String = class$("java.lang.String")), (CachedBillingOnItem.class$Ljava$util$Date != null) ? CachedBillingOnItem.class$Ljava$util$Date : (CachedBillingOnItem.class$Ljava$util$Date = class$("java.util.Date")), (CachedBillingOnItem.class$Ljava$lang$String != null) ? CachedBillingOnItem.class$Ljava$lang$String : (CachedBillingOnItem.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedBillingOnItem.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedBillingOnItem.class$Lca$openosp$openo$caisi_integrator$dao$CachedBillingOnItem != null) ? CachedBillingOnItem.class$Lca$openosp$openo$caisi_integrator$dao$CachedBillingOnItem : (CachedBillingOnItem.class$Lca$openosp$openo$caisi_integrator$dao$CachedBillingOnItem = class$("ca.openosp.openo.caisi_integrator.dao.CachedBillingOnItem")), CachedBillingOnItem.pcFieldNames, CachedBillingOnItem.pcFieldTypes, CachedBillingOnItem.pcFieldFlags, CachedBillingOnItem.pcPCSuperclass, "CachedBillingOnItem", (PersistenceCapable)new CachedBillingOnItem());
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
        this.appointmentId = null;
        this.apptProviderId = null;
        this.asstProviderId = null;
        this.caisiDemographicId = null;
        this.caisiProviderId = null;
        this.dx = null;
        this.dx1 = null;
        this.dx2 = null;
        this.facilityBillingOnItemPk = null;
        this.serviceCode = null;
        this.serviceDate = null;
        this.status = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedBillingOnItem cachedBillingOnItem = new CachedBillingOnItem();
        if (b) {
            cachedBillingOnItem.pcClearFields();
        }
        cachedBillingOnItem.pcStateManager = pcStateManager;
        cachedBillingOnItem.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedBillingOnItem;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedBillingOnItem cachedBillingOnItem = new CachedBillingOnItem();
        if (b) {
            cachedBillingOnItem.pcClearFields();
        }
        cachedBillingOnItem.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedBillingOnItem;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 12;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedBillingOnItem.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.appointmentId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.apptProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.asstProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.caisiDemographicId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.caisiProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.dx = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.dx1 = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.dx2 = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.facilityBillingOnItemPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.serviceCode = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 10: {
                this.serviceDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 11: {
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
        final int n2 = n - CachedBillingOnItem.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.appointmentId);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.apptProviderId);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.asstProviderId);
                return;
            }
            case 3: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiDemographicId);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.caisiProviderId);
                return;
            }
            case 5: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.dx);
                return;
            }
            case 6: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.dx1);
                return;
            }
            case 7: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.dx2);
                return;
            }
            case 8: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityBillingOnItemPk);
                return;
            }
            case 9: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.serviceCode);
                return;
            }
            case 10: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.serviceDate);
                return;
            }
            case 11: {
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
    
    protected void pcCopyField(final CachedBillingOnItem cachedBillingOnItem, final int n) {
        final int n2 = n - CachedBillingOnItem.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.appointmentId = cachedBillingOnItem.appointmentId;
                return;
            }
            case 1: {
                this.apptProviderId = cachedBillingOnItem.apptProviderId;
                return;
            }
            case 2: {
                this.asstProviderId = cachedBillingOnItem.asstProviderId;
                return;
            }
            case 3: {
                this.caisiDemographicId = cachedBillingOnItem.caisiDemographicId;
                return;
            }
            case 4: {
                this.caisiProviderId = cachedBillingOnItem.caisiProviderId;
                return;
            }
            case 5: {
                this.dx = cachedBillingOnItem.dx;
                return;
            }
            case 6: {
                this.dx1 = cachedBillingOnItem.dx1;
                return;
            }
            case 7: {
                this.dx2 = cachedBillingOnItem.dx2;
                return;
            }
            case 8: {
                this.facilityBillingOnItemPk = cachedBillingOnItem.facilityBillingOnItemPk;
                return;
            }
            case 9: {
                this.serviceCode = cachedBillingOnItem.serviceCode;
                return;
            }
            case 10: {
                this.serviceDate = cachedBillingOnItem.serviceDate;
                return;
            }
            case 11: {
                this.status = cachedBillingOnItem.status;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedBillingOnItem cachedBillingOnItem = (CachedBillingOnItem)o;
        if (cachedBillingOnItem.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedBillingOnItem, array[i]);
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
        fieldConsumer.storeObjectField(8 + CachedBillingOnItem.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityBillingOnItemPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedBillingOnItem\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedBillingOnItem.class$Lca$openosp$openo$caisi_integrator$dao$CachedBillingOnItem != null) ? CachedBillingOnItem.class$Lca$openosp$openo$caisi_integrator$dao$CachedBillingOnItem : (CachedBillingOnItem.class$Lca$openosp$openo$caisi_integrator$dao$CachedBillingOnItem = class$("ca.openosp.openo.caisi_integrator.dao.CachedBillingOnItem")), (Object)this.facilityBillingOnItemPk);
    }
    
    private static final Integer pcGetappointmentId(final CachedBillingOnItem cachedBillingOnItem) {
        if (cachedBillingOnItem.pcStateManager == null) {
            return cachedBillingOnItem.appointmentId;
        }
        cachedBillingOnItem.pcStateManager.accessingField(CachedBillingOnItem.pcInheritedFieldCount + 0);
        return cachedBillingOnItem.appointmentId;
    }
    
    private static final void pcSetappointmentId(final CachedBillingOnItem cachedBillingOnItem, final Integer appointmentId) {
        if (cachedBillingOnItem.pcStateManager == null) {
            cachedBillingOnItem.appointmentId = appointmentId;
            return;
        }
        cachedBillingOnItem.pcStateManager.settingObjectField((PersistenceCapable)cachedBillingOnItem, CachedBillingOnItem.pcInheritedFieldCount + 0, (Object)cachedBillingOnItem.appointmentId, (Object)appointmentId, 0);
    }
    
    private static final String pcGetapptProviderId(final CachedBillingOnItem cachedBillingOnItem) {
        if (cachedBillingOnItem.pcStateManager == null) {
            return cachedBillingOnItem.apptProviderId;
        }
        cachedBillingOnItem.pcStateManager.accessingField(CachedBillingOnItem.pcInheritedFieldCount + 1);
        return cachedBillingOnItem.apptProviderId;
    }
    
    private static final void pcSetapptProviderId(final CachedBillingOnItem cachedBillingOnItem, final String apptProviderId) {
        if (cachedBillingOnItem.pcStateManager == null) {
            cachedBillingOnItem.apptProviderId = apptProviderId;
            return;
        }
        cachedBillingOnItem.pcStateManager.settingStringField((PersistenceCapable)cachedBillingOnItem, CachedBillingOnItem.pcInheritedFieldCount + 1, cachedBillingOnItem.apptProviderId, apptProviderId, 0);
    }
    
    private static final String pcGetasstProviderId(final CachedBillingOnItem cachedBillingOnItem) {
        if (cachedBillingOnItem.pcStateManager == null) {
            return cachedBillingOnItem.asstProviderId;
        }
        cachedBillingOnItem.pcStateManager.accessingField(CachedBillingOnItem.pcInheritedFieldCount + 2);
        return cachedBillingOnItem.asstProviderId;
    }
    
    private static final void pcSetasstProviderId(final CachedBillingOnItem cachedBillingOnItem, final String asstProviderId) {
        if (cachedBillingOnItem.pcStateManager == null) {
            cachedBillingOnItem.asstProviderId = asstProviderId;
            return;
        }
        cachedBillingOnItem.pcStateManager.settingStringField((PersistenceCapable)cachedBillingOnItem, CachedBillingOnItem.pcInheritedFieldCount + 2, cachedBillingOnItem.asstProviderId, asstProviderId, 0);
    }
    
    private static final Integer pcGetcaisiDemographicId(final CachedBillingOnItem cachedBillingOnItem) {
        if (cachedBillingOnItem.pcStateManager == null) {
            return cachedBillingOnItem.caisiDemographicId;
        }
        cachedBillingOnItem.pcStateManager.accessingField(CachedBillingOnItem.pcInheritedFieldCount + 3);
        return cachedBillingOnItem.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedBillingOnItem cachedBillingOnItem, final Integer caisiDemographicId) {
        if (cachedBillingOnItem.pcStateManager == null) {
            cachedBillingOnItem.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedBillingOnItem.pcStateManager.settingObjectField((PersistenceCapable)cachedBillingOnItem, CachedBillingOnItem.pcInheritedFieldCount + 3, (Object)cachedBillingOnItem.caisiDemographicId, (Object)caisiDemographicId, 0);
    }
    
    private static final String pcGetcaisiProviderId(final CachedBillingOnItem cachedBillingOnItem) {
        if (cachedBillingOnItem.pcStateManager == null) {
            return cachedBillingOnItem.caisiProviderId;
        }
        cachedBillingOnItem.pcStateManager.accessingField(CachedBillingOnItem.pcInheritedFieldCount + 4);
        return cachedBillingOnItem.caisiProviderId;
    }
    
    private static final void pcSetcaisiProviderId(final CachedBillingOnItem cachedBillingOnItem, final String caisiProviderId) {
        if (cachedBillingOnItem.pcStateManager == null) {
            cachedBillingOnItem.caisiProviderId = caisiProviderId;
            return;
        }
        cachedBillingOnItem.pcStateManager.settingStringField((PersistenceCapable)cachedBillingOnItem, CachedBillingOnItem.pcInheritedFieldCount + 4, cachedBillingOnItem.caisiProviderId, caisiProviderId, 0);
    }
    
    private static final String pcGetdx(final CachedBillingOnItem cachedBillingOnItem) {
        if (cachedBillingOnItem.pcStateManager == null) {
            return cachedBillingOnItem.dx;
        }
        cachedBillingOnItem.pcStateManager.accessingField(CachedBillingOnItem.pcInheritedFieldCount + 5);
        return cachedBillingOnItem.dx;
    }
    
    private static final void pcSetdx(final CachedBillingOnItem cachedBillingOnItem, final String dx) {
        if (cachedBillingOnItem.pcStateManager == null) {
            cachedBillingOnItem.dx = dx;
            return;
        }
        cachedBillingOnItem.pcStateManager.settingStringField((PersistenceCapable)cachedBillingOnItem, CachedBillingOnItem.pcInheritedFieldCount + 5, cachedBillingOnItem.dx, dx, 0);
    }
    
    private static final String pcGetdx1(final CachedBillingOnItem cachedBillingOnItem) {
        if (cachedBillingOnItem.pcStateManager == null) {
            return cachedBillingOnItem.dx1;
        }
        cachedBillingOnItem.pcStateManager.accessingField(CachedBillingOnItem.pcInheritedFieldCount + 6);
        return cachedBillingOnItem.dx1;
    }
    
    private static final void pcSetdx1(final CachedBillingOnItem cachedBillingOnItem, final String dx1) {
        if (cachedBillingOnItem.pcStateManager == null) {
            cachedBillingOnItem.dx1 = dx1;
            return;
        }
        cachedBillingOnItem.pcStateManager.settingStringField((PersistenceCapable)cachedBillingOnItem, CachedBillingOnItem.pcInheritedFieldCount + 6, cachedBillingOnItem.dx1, dx1, 0);
    }
    
    private static final String pcGetdx2(final CachedBillingOnItem cachedBillingOnItem) {
        if (cachedBillingOnItem.pcStateManager == null) {
            return cachedBillingOnItem.dx2;
        }
        cachedBillingOnItem.pcStateManager.accessingField(CachedBillingOnItem.pcInheritedFieldCount + 7);
        return cachedBillingOnItem.dx2;
    }
    
    private static final void pcSetdx2(final CachedBillingOnItem cachedBillingOnItem, final String dx2) {
        if (cachedBillingOnItem.pcStateManager == null) {
            cachedBillingOnItem.dx2 = dx2;
            return;
        }
        cachedBillingOnItem.pcStateManager.settingStringField((PersistenceCapable)cachedBillingOnItem, CachedBillingOnItem.pcInheritedFieldCount + 7, cachedBillingOnItem.dx2, dx2, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityBillingOnItemPk(final CachedBillingOnItem cachedBillingOnItem) {
        if (cachedBillingOnItem.pcStateManager == null) {
            return cachedBillingOnItem.facilityBillingOnItemPk;
        }
        cachedBillingOnItem.pcStateManager.accessingField(CachedBillingOnItem.pcInheritedFieldCount + 8);
        return cachedBillingOnItem.facilityBillingOnItemPk;
    }
    
    private static final void pcSetfacilityBillingOnItemPk(final CachedBillingOnItem cachedBillingOnItem, final FacilityIdIntegerCompositePk facilityBillingOnItemPk) {
        if (cachedBillingOnItem.pcStateManager == null) {
            cachedBillingOnItem.facilityBillingOnItemPk = facilityBillingOnItemPk;
            return;
        }
        cachedBillingOnItem.pcStateManager.settingObjectField((PersistenceCapable)cachedBillingOnItem, CachedBillingOnItem.pcInheritedFieldCount + 8, (Object)cachedBillingOnItem.facilityBillingOnItemPk, (Object)facilityBillingOnItemPk, 0);
    }
    
    private static final String pcGetserviceCode(final CachedBillingOnItem cachedBillingOnItem) {
        if (cachedBillingOnItem.pcStateManager == null) {
            return cachedBillingOnItem.serviceCode;
        }
        cachedBillingOnItem.pcStateManager.accessingField(CachedBillingOnItem.pcInheritedFieldCount + 9);
        return cachedBillingOnItem.serviceCode;
    }
    
    private static final void pcSetserviceCode(final CachedBillingOnItem cachedBillingOnItem, final String serviceCode) {
        if (cachedBillingOnItem.pcStateManager == null) {
            cachedBillingOnItem.serviceCode = serviceCode;
            return;
        }
        cachedBillingOnItem.pcStateManager.settingStringField((PersistenceCapable)cachedBillingOnItem, CachedBillingOnItem.pcInheritedFieldCount + 9, cachedBillingOnItem.serviceCode, serviceCode, 0);
    }
    
    private static final Date pcGetserviceDate(final CachedBillingOnItem cachedBillingOnItem) {
        if (cachedBillingOnItem.pcStateManager == null) {
            return cachedBillingOnItem.serviceDate;
        }
        cachedBillingOnItem.pcStateManager.accessingField(CachedBillingOnItem.pcInheritedFieldCount + 10);
        return cachedBillingOnItem.serviceDate;
    }
    
    private static final void pcSetserviceDate(final CachedBillingOnItem cachedBillingOnItem, final Date serviceDate) {
        if (cachedBillingOnItem.pcStateManager == null) {
            cachedBillingOnItem.serviceDate = serviceDate;
            return;
        }
        cachedBillingOnItem.pcStateManager.settingObjectField((PersistenceCapable)cachedBillingOnItem, CachedBillingOnItem.pcInheritedFieldCount + 10, (Object)cachedBillingOnItem.serviceDate, (Object)serviceDate, 0);
    }
    
    private static final String pcGetstatus(final CachedBillingOnItem cachedBillingOnItem) {
        if (cachedBillingOnItem.pcStateManager == null) {
            return cachedBillingOnItem.status;
        }
        cachedBillingOnItem.pcStateManager.accessingField(CachedBillingOnItem.pcInheritedFieldCount + 11);
        return cachedBillingOnItem.status;
    }
    
    private static final void pcSetstatus(final CachedBillingOnItem cachedBillingOnItem, final String status) {
        if (cachedBillingOnItem.pcStateManager == null) {
            cachedBillingOnItem.status = status;
            return;
        }
        cachedBillingOnItem.pcStateManager.settingStringField((PersistenceCapable)cachedBillingOnItem, CachedBillingOnItem.pcInheritedFieldCount + 11, cachedBillingOnItem.status, status, 0);
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
