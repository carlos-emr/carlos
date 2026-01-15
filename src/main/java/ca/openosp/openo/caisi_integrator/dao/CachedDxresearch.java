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
    
    public CachedDxresearch() {
        this.caisiDemographicId = null;
        this.startDate = null;
        this.updateDate = null;
        this.status = null;
        this.dxresearchCode = null;
        this.codingSystem = null;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityDxresearchPk(this);
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityDxresearchPk) {
        pcSetfacilityDxresearchPk(this, facilityDxresearchPk);
    }
    
    public Integer getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }
    
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }
    
    public Date getStartDate() {
        return pcGetstartDate(this);
    }
    
    public void setStartDate(final Date startDate) {
        pcSetstartDate(this, startDate);
    }
    
    public Date getUpdateDate() {
        return pcGetupdateDate(this);
    }
    
    public void setUpdateDate(final Date updateDate) {
        pcSetupdateDate(this, updateDate);
    }
    
    public String getStatus() {
        return pcGetstatus(this);
    }
    
    public void setStatus(final String status) {
        pcSetstatus(this, StringUtils.trimToNull(status));
    }
    
    public String getDxresearchCode() {
        return pcGetdxresearchCode(this);
    }
    
    public void setDxresearchCode(final String dxresearchCode) {
        pcSetdxresearchCode(this, StringUtils.trimToNull(dxresearchCode));
    }
    
    public String getCodingSystem() {
        return pcGetcodingSystem(this);
    }
    
    public void setCodingSystem(final String codingSystem) {
        pcSetcodingSystem(this, StringUtils.trimToNull(codingSystem));
    }
    
    @Override
    public int compareTo(final CachedDxresearch o) {
        return pcGetfacilityDxresearchPk(this).getCaisiItemId() - pcGetfacilityDxresearchPk(o).getCaisiItemId();
    }
    
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityDxresearchPk(this);
    }
    
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
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDxresearch cachedDxresearch = new CachedDxresearch();
        if (b) {
            cachedDxresearch.pcClearFields();
        }
        cachedDxresearch.pcStateManager = pcStateManager;
        cachedDxresearch.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDxresearch;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDxresearch cachedDxresearch = new CachedDxresearch();
        if (b) {
            cachedDxresearch.pcClearFields();
        }
        cachedDxresearch.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDxresearch;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 7;
    }
    
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
    
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }
    
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
        fieldConsumer.storeObjectField(3 + CachedDxresearch.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityDxresearchPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDxresearch\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDxresearch.class$Lca$openosp$openo$caisi_integrator$dao$CachedDxresearch != null) ? CachedDxresearch.class$Lca$openosp$openo$caisi_integrator$dao$CachedDxresearch : (CachedDxresearch.class$Lca$openosp$openo$caisi_integrator$dao$CachedDxresearch = class$("ca.openosp.openo.caisi_integrator.dao.CachedDxresearch")), (Object)this.facilityDxresearchPk);
    }
    
    private static final Integer pcGetcaisiDemographicId(final CachedDxresearch cachedDxresearch) {
        if (cachedDxresearch.pcStateManager == null) {
            return cachedDxresearch.caisiDemographicId;
        }
        cachedDxresearch.pcStateManager.accessingField(CachedDxresearch.pcInheritedFieldCount + 0);
        return cachedDxresearch.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedDxresearch cachedDxresearch, final Integer caisiDemographicId) {
        if (cachedDxresearch.pcStateManager == null) {
            cachedDxresearch.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedDxresearch.pcStateManager.settingObjectField((PersistenceCapable)cachedDxresearch, CachedDxresearch.pcInheritedFieldCount + 0, (Object)cachedDxresearch.caisiDemographicId, (Object)caisiDemographicId, 0);
    }
    
    private static final String pcGetcodingSystem(final CachedDxresearch cachedDxresearch) {
        if (cachedDxresearch.pcStateManager == null) {
            return cachedDxresearch.codingSystem;
        }
        cachedDxresearch.pcStateManager.accessingField(CachedDxresearch.pcInheritedFieldCount + 1);
        return cachedDxresearch.codingSystem;
    }
    
    private static final void pcSetcodingSystem(final CachedDxresearch cachedDxresearch, final String codingSystem) {
        if (cachedDxresearch.pcStateManager == null) {
            cachedDxresearch.codingSystem = codingSystem;
            return;
        }
        cachedDxresearch.pcStateManager.settingStringField((PersistenceCapable)cachedDxresearch, CachedDxresearch.pcInheritedFieldCount + 1, cachedDxresearch.codingSystem, codingSystem, 0);
    }
    
    private static final String pcGetdxresearchCode(final CachedDxresearch cachedDxresearch) {
        if (cachedDxresearch.pcStateManager == null) {
            return cachedDxresearch.dxresearchCode;
        }
        cachedDxresearch.pcStateManager.accessingField(CachedDxresearch.pcInheritedFieldCount + 2);
        return cachedDxresearch.dxresearchCode;
    }
    
    private static final void pcSetdxresearchCode(final CachedDxresearch cachedDxresearch, final String dxresearchCode) {
        if (cachedDxresearch.pcStateManager == null) {
            cachedDxresearch.dxresearchCode = dxresearchCode;
            return;
        }
        cachedDxresearch.pcStateManager.settingStringField((PersistenceCapable)cachedDxresearch, CachedDxresearch.pcInheritedFieldCount + 2, cachedDxresearch.dxresearchCode, dxresearchCode, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityDxresearchPk(final CachedDxresearch cachedDxresearch) {
        if (cachedDxresearch.pcStateManager == null) {
            return cachedDxresearch.facilityDxresearchPk;
        }
        cachedDxresearch.pcStateManager.accessingField(CachedDxresearch.pcInheritedFieldCount + 3);
        return cachedDxresearch.facilityDxresearchPk;
    }
    
    private static final void pcSetfacilityDxresearchPk(final CachedDxresearch cachedDxresearch, final FacilityIdIntegerCompositePk facilityDxresearchPk) {
        if (cachedDxresearch.pcStateManager == null) {
            cachedDxresearch.facilityDxresearchPk = facilityDxresearchPk;
            return;
        }
        cachedDxresearch.pcStateManager.settingObjectField((PersistenceCapable)cachedDxresearch, CachedDxresearch.pcInheritedFieldCount + 3, (Object)cachedDxresearch.facilityDxresearchPk, (Object)facilityDxresearchPk, 0);
    }
    
    private static final Date pcGetstartDate(final CachedDxresearch cachedDxresearch) {
        if (cachedDxresearch.pcStateManager == null) {
            return cachedDxresearch.startDate;
        }
        cachedDxresearch.pcStateManager.accessingField(CachedDxresearch.pcInheritedFieldCount + 4);
        return cachedDxresearch.startDate;
    }
    
    private static final void pcSetstartDate(final CachedDxresearch cachedDxresearch, final Date startDate) {
        if (cachedDxresearch.pcStateManager == null) {
            cachedDxresearch.startDate = startDate;
            return;
        }
        cachedDxresearch.pcStateManager.settingObjectField((PersistenceCapable)cachedDxresearch, CachedDxresearch.pcInheritedFieldCount + 4, (Object)cachedDxresearch.startDate, (Object)startDate, 0);
    }
    
    private static final String pcGetstatus(final CachedDxresearch cachedDxresearch) {
        if (cachedDxresearch.pcStateManager == null) {
            return cachedDxresearch.status;
        }
        cachedDxresearch.pcStateManager.accessingField(CachedDxresearch.pcInheritedFieldCount + 5);
        return cachedDxresearch.status;
    }
    
    private static final void pcSetstatus(final CachedDxresearch cachedDxresearch, final String status) {
        if (cachedDxresearch.pcStateManager == null) {
            cachedDxresearch.status = status;
            return;
        }
        cachedDxresearch.pcStateManager.settingStringField((PersistenceCapable)cachedDxresearch, CachedDxresearch.pcInheritedFieldCount + 5, cachedDxresearch.status, status, 0);
    }
    
    private static final Date pcGetupdateDate(final CachedDxresearch cachedDxresearch) {
        if (cachedDxresearch.pcStateManager == null) {
            return cachedDxresearch.updateDate;
        }
        cachedDxresearch.pcStateManager.accessingField(CachedDxresearch.pcInheritedFieldCount + 6);
        return cachedDxresearch.updateDate;
    }
    
    private static final void pcSetupdateDate(final CachedDxresearch cachedDxresearch, final Date updateDate) {
        if (cachedDxresearch.pcStateManager == null) {
            cachedDxresearch.updateDate = updateDate;
            return;
        }
        cachedDxresearch.pcStateManager.settingObjectField((PersistenceCapable)cachedDxresearch, CachedDxresearch.pcInheritedFieldCount + 6, (Object)cachedDxresearch.updateDate, (Object)updateDate, 0);
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
