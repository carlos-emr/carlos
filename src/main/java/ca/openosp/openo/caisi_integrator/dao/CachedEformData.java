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
import javax.persistence.Lob;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

@Entity
public class CachedEformData extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedEformData>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityEformDataPk;
    @Column(nullable = false)
    private Integer formId;
    @Column(nullable = false)
    @Index
    private Integer caisiDemographicId;
    @Column(nullable = false)
    private Boolean status;
    @Column(length = 255)
    private String formName;
    @Column(length = 255)
    private String formProvider;
    @Column(length = 255)
    private String subject;
    @Temporal(TemporalType.DATE)
    private Date formDate;
    @Temporal(TemporalType.TIME)
    private Date formTime;
    @Lob
    private String formData;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Ljava$lang$Boolean;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedEformData;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedEformData() {
        this.formId = null;
        this.caisiDemographicId = null;
        this.status = null;
        this.formName = null;
        this.formProvider = null;
        this.subject = null;
        this.formDate = null;
        this.formTime = null;
        this.formData = null;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityEformDataPk(this);
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityEformDataPk) {
        pcSetfacilityEformDataPk(this, facilityEformDataPk);
    }
    
    public Integer getFormId() {
        return pcGetformId(this);
    }
    
    public void setFormId(final Integer formId) {
        pcSetformId(this, formId);
    }
    
    public Integer getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }
    
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }
    
    public Boolean getStatus() {
        return pcGetstatus(this);
    }
    
    public void setStatus(final Boolean status) {
        pcSetstatus(this, status);
    }
    
    public String getFormName() {
        return pcGetformName(this);
    }
    
    public void setFormName(final String formName) {
        pcSetformName(this, StringUtils.trimToNull(formName));
    }
    
    public String getFormProvider() {
        return pcGetformProvider(this);
    }
    
    public void setFormProvider(final String formProvider) {
        pcSetformProvider(this, StringUtils.trimToNull(formProvider));
    }
    
    public String getSubject() {
        return pcGetsubject(this);
    }
    
    public void setSubject(final String subject) {
        pcSetsubject(this, StringUtils.trimToNull(subject));
    }
    
    public Date getFormDate() {
        return pcGetformDate(this);
    }
    
    public void setFormDate(final Date formDate) {
        pcSetformDate(this, formDate);
    }
    
    public Date getFormTime() {
        return pcGetformTime(this);
    }
    
    public void setFormTime(final Date formTime) {
        pcSetformTime(this, formTime);
    }
    
    public String getFormData() {
        return pcGetformData(this);
    }
    
    public void setFormData(final String formData) {
        pcSetformData(this, StringUtils.trimToNull(formData));
    }
    
    @Override
    public int compareTo(final CachedEformData o) {
        return pcGetfacilityEformDataPk(this).getCaisiItemId() - pcGetfacilityEformDataPk(o).getCaisiItemId();
    }
    
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityEformDataPk(this);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 4959576261035143016L;
        CachedEformData.pcFieldNames = new String[] { "caisiDemographicId", "facilityEformDataPk", "formData", "formDate", "formId", "formName", "formProvider", "formTime", "status", "subject" };
        CachedEformData.pcFieldTypes = new Class[] { (CachedEformData.class$Ljava$lang$Integer != null) ? CachedEformData.class$Ljava$lang$Integer : (CachedEformData.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedEformData.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedEformData.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedEformData.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedEformData.class$Ljava$lang$String != null) ? CachedEformData.class$Ljava$lang$String : (CachedEformData.class$Ljava$lang$String = class$("java.lang.String")), (CachedEformData.class$Ljava$util$Date != null) ? CachedEformData.class$Ljava$util$Date : (CachedEformData.class$Ljava$util$Date = class$("java.util.Date")), (CachedEformData.class$Ljava$lang$Integer != null) ? CachedEformData.class$Ljava$lang$Integer : (CachedEformData.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedEformData.class$Ljava$lang$String != null) ? CachedEformData.class$Ljava$lang$String : (CachedEformData.class$Ljava$lang$String = class$("java.lang.String")), (CachedEformData.class$Ljava$lang$String != null) ? CachedEformData.class$Ljava$lang$String : (CachedEformData.class$Ljava$lang$String = class$("java.lang.String")), (CachedEformData.class$Ljava$util$Date != null) ? CachedEformData.class$Ljava$util$Date : (CachedEformData.class$Ljava$util$Date = class$("java.util.Date")), (CachedEformData.class$Ljava$lang$Boolean != null) ? CachedEformData.class$Ljava$lang$Boolean : (CachedEformData.class$Ljava$lang$Boolean = class$("java.lang.Boolean")), (CachedEformData.class$Ljava$lang$String != null) ? CachedEformData.class$Ljava$lang$String : (CachedEformData.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedEformData.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedEformData.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformData != null) ? CachedEformData.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformData : (CachedEformData.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformData = class$("ca.openosp.openo.caisi_integrator.dao.CachedEformData")), CachedEformData.pcFieldNames, CachedEformData.pcFieldTypes, CachedEformData.pcFieldFlags, CachedEformData.pcPCSuperclass, "CachedEformData", (PersistenceCapable)new CachedEformData());
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
        this.facilityEformDataPk = null;
        this.formData = null;
        this.formDate = null;
        this.formId = null;
        this.formName = null;
        this.formProvider = null;
        this.formTime = null;
        this.status = null;
        this.subject = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedEformData cachedEformData = new CachedEformData();
        if (b) {
            cachedEformData.pcClearFields();
        }
        cachedEformData.pcStateManager = pcStateManager;
        cachedEformData.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedEformData;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedEformData cachedEformData = new CachedEformData();
        if (b) {
            cachedEformData.pcClearFields();
        }
        cachedEformData.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedEformData;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 10;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedEformData.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.facilityEformDataPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.formData = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.formDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.formId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.formName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.formProvider = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.formTime = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.status = (Boolean)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.subject = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedEformData.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiDemographicId);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityEformDataPk);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.formData);
                return;
            }
            case 3: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.formDate);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.formId);
                return;
            }
            case 5: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.formName);
                return;
            }
            case 6: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.formProvider);
                return;
            }
            case 7: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.formTime);
                return;
            }
            case 8: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.status);
                return;
            }
            case 9: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.subject);
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
    
    protected void pcCopyField(final CachedEformData cachedEformData, final int n) {
        final int n2 = n - CachedEformData.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = cachedEformData.caisiDemographicId;
                return;
            }
            case 1: {
                this.facilityEformDataPk = cachedEformData.facilityEformDataPk;
                return;
            }
            case 2: {
                this.formData = cachedEformData.formData;
                return;
            }
            case 3: {
                this.formDate = cachedEformData.formDate;
                return;
            }
            case 4: {
                this.formId = cachedEformData.formId;
                return;
            }
            case 5: {
                this.formName = cachedEformData.formName;
                return;
            }
            case 6: {
                this.formProvider = cachedEformData.formProvider;
                return;
            }
            case 7: {
                this.formTime = cachedEformData.formTime;
                return;
            }
            case 8: {
                this.status = cachedEformData.status;
                return;
            }
            case 9: {
                this.subject = cachedEformData.subject;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedEformData cachedEformData = (CachedEformData)o;
        if (cachedEformData.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedEformData, array[i]);
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
        fieldConsumer.storeObjectField(1 + CachedEformData.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityEformDataPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedEformData\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedEformData.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformData != null) ? CachedEformData.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformData : (CachedEformData.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformData = class$("ca.openosp.openo.caisi_integrator.dao.CachedEformData")), (Object)this.facilityEformDataPk);
    }
    
    private static final Integer pcGetcaisiDemographicId(final CachedEformData cachedEformData) {
        if (cachedEformData.pcStateManager == null) {
            return cachedEformData.caisiDemographicId;
        }
        cachedEformData.pcStateManager.accessingField(CachedEformData.pcInheritedFieldCount + 0);
        return cachedEformData.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedEformData cachedEformData, final Integer caisiDemographicId) {
        if (cachedEformData.pcStateManager == null) {
            cachedEformData.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedEformData.pcStateManager.settingObjectField((PersistenceCapable)cachedEformData, CachedEformData.pcInheritedFieldCount + 0, (Object)cachedEformData.caisiDemographicId, (Object)caisiDemographicId, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityEformDataPk(final CachedEformData cachedEformData) {
        if (cachedEformData.pcStateManager == null) {
            return cachedEformData.facilityEformDataPk;
        }
        cachedEformData.pcStateManager.accessingField(CachedEformData.pcInheritedFieldCount + 1);
        return cachedEformData.facilityEformDataPk;
    }
    
    private static final void pcSetfacilityEformDataPk(final CachedEformData cachedEformData, final FacilityIdIntegerCompositePk facilityEformDataPk) {
        if (cachedEformData.pcStateManager == null) {
            cachedEformData.facilityEformDataPk = facilityEformDataPk;
            return;
        }
        cachedEformData.pcStateManager.settingObjectField((PersistenceCapable)cachedEformData, CachedEformData.pcInheritedFieldCount + 1, (Object)cachedEformData.facilityEformDataPk, (Object)facilityEformDataPk, 0);
    }
    
    private static final String pcGetformData(final CachedEformData cachedEformData) {
        if (cachedEformData.pcStateManager == null) {
            return cachedEformData.formData;
        }
        cachedEformData.pcStateManager.accessingField(CachedEformData.pcInheritedFieldCount + 2);
        return cachedEformData.formData;
    }
    
    private static final void pcSetformData(final CachedEformData cachedEformData, final String formData) {
        if (cachedEformData.pcStateManager == null) {
            cachedEformData.formData = formData;
            return;
        }
        cachedEformData.pcStateManager.settingStringField((PersistenceCapable)cachedEformData, CachedEformData.pcInheritedFieldCount + 2, cachedEformData.formData, formData, 0);
    }
    
    private static final Date pcGetformDate(final CachedEformData cachedEformData) {
        if (cachedEformData.pcStateManager == null) {
            return cachedEformData.formDate;
        }
        cachedEformData.pcStateManager.accessingField(CachedEformData.pcInheritedFieldCount + 3);
        return cachedEformData.formDate;
    }
    
    private static final void pcSetformDate(final CachedEformData cachedEformData, final Date formDate) {
        if (cachedEformData.pcStateManager == null) {
            cachedEformData.formDate = formDate;
            return;
        }
        cachedEformData.pcStateManager.settingObjectField((PersistenceCapable)cachedEformData, CachedEformData.pcInheritedFieldCount + 3, (Object)cachedEformData.formDate, (Object)formDate, 0);
    }
    
    private static final Integer pcGetformId(final CachedEformData cachedEformData) {
        if (cachedEformData.pcStateManager == null) {
            return cachedEformData.formId;
        }
        cachedEformData.pcStateManager.accessingField(CachedEformData.pcInheritedFieldCount + 4);
        return cachedEformData.formId;
    }
    
    private static final void pcSetformId(final CachedEformData cachedEformData, final Integer formId) {
        if (cachedEformData.pcStateManager == null) {
            cachedEformData.formId = formId;
            return;
        }
        cachedEformData.pcStateManager.settingObjectField((PersistenceCapable)cachedEformData, CachedEformData.pcInheritedFieldCount + 4, (Object)cachedEformData.formId, (Object)formId, 0);
    }
    
    private static final String pcGetformName(final CachedEformData cachedEformData) {
        if (cachedEformData.pcStateManager == null) {
            return cachedEformData.formName;
        }
        cachedEformData.pcStateManager.accessingField(CachedEformData.pcInheritedFieldCount + 5);
        return cachedEformData.formName;
    }
    
    private static final void pcSetformName(final CachedEformData cachedEformData, final String formName) {
        if (cachedEformData.pcStateManager == null) {
            cachedEformData.formName = formName;
            return;
        }
        cachedEformData.pcStateManager.settingStringField((PersistenceCapable)cachedEformData, CachedEformData.pcInheritedFieldCount + 5, cachedEformData.formName, formName, 0);
    }
    
    private static final String pcGetformProvider(final CachedEformData cachedEformData) {
        if (cachedEformData.pcStateManager == null) {
            return cachedEformData.formProvider;
        }
        cachedEformData.pcStateManager.accessingField(CachedEformData.pcInheritedFieldCount + 6);
        return cachedEformData.formProvider;
    }
    
    private static final void pcSetformProvider(final CachedEformData cachedEformData, final String formProvider) {
        if (cachedEformData.pcStateManager == null) {
            cachedEformData.formProvider = formProvider;
            return;
        }
        cachedEformData.pcStateManager.settingStringField((PersistenceCapable)cachedEformData, CachedEformData.pcInheritedFieldCount + 6, cachedEformData.formProvider, formProvider, 0);
    }
    
    private static final Date pcGetformTime(final CachedEformData cachedEformData) {
        if (cachedEformData.pcStateManager == null) {
            return cachedEformData.formTime;
        }
        cachedEformData.pcStateManager.accessingField(CachedEformData.pcInheritedFieldCount + 7);
        return cachedEformData.formTime;
    }
    
    private static final void pcSetformTime(final CachedEformData cachedEformData, final Date formTime) {
        if (cachedEformData.pcStateManager == null) {
            cachedEformData.formTime = formTime;
            return;
        }
        cachedEformData.pcStateManager.settingObjectField((PersistenceCapable)cachedEformData, CachedEformData.pcInheritedFieldCount + 7, (Object)cachedEformData.formTime, (Object)formTime, 0);
    }
    
    private static final Boolean pcGetstatus(final CachedEformData cachedEformData) {
        if (cachedEformData.pcStateManager == null) {
            return cachedEformData.status;
        }
        cachedEformData.pcStateManager.accessingField(CachedEformData.pcInheritedFieldCount + 8);
        return cachedEformData.status;
    }
    
    private static final void pcSetstatus(final CachedEformData cachedEformData, final Boolean status) {
        if (cachedEformData.pcStateManager == null) {
            cachedEformData.status = status;
            return;
        }
        cachedEformData.pcStateManager.settingObjectField((PersistenceCapable)cachedEformData, CachedEformData.pcInheritedFieldCount + 8, (Object)cachedEformData.status, (Object)status, 0);
    }
    
    private static final String pcGetsubject(final CachedEformData cachedEformData) {
        if (cachedEformData.pcStateManager == null) {
            return cachedEformData.subject;
        }
        cachedEformData.pcStateManager.accessingField(CachedEformData.pcInheritedFieldCount + 9);
        return cachedEformData.subject;
    }
    
    private static final void pcSetsubject(final CachedEformData cachedEformData, final String subject) {
        if (cachedEformData.pcStateManager == null) {
            cachedEformData.subject = subject;
            return;
        }
        cachedEformData.pcStateManager.settingStringField((PersistenceCapable)cachedEformData, CachedEformData.pcInheritedFieldCount + 9, cachedEformData.subject, subject, 0);
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
