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
public class CachedMeasurement extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedMeasurement>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityMeasurementPk;
    @Column(nullable = false, length = 50)
    private String type;
    @Column(nullable = false)
    @Index
    private Integer caisiDemographicId;
    @Column(nullable = false, length = 16)
    private String caisiProviderId;
    private String dataField;
    private String measuringInstruction;
    private String comments;
    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateObserved;
    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateEntered;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurement;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedMeasurement() {
        this.type = null;
        this.caisiDemographicId = null;
        this.caisiProviderId = null;
        this.dataField = null;
        this.measuringInstruction = null;
        this.comments = null;
        this.dateObserved = null;
        this.dateEntered = null;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityMeasurementPk(this);
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityMeasurementPk) {
        pcSetfacilityMeasurementPk(this, facilityMeasurementPk);
    }
    
    public String getType() {
        return pcGettype(this);
    }
    
    public void setType(final String type) {
        pcSettype(this, StringUtils.trimToEmpty(type));
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
    
    public String getDataField() {
        return pcGetdataField(this);
    }
    
    public void setDataField(final String dataField) {
        pcSetdataField(this, StringUtils.trimToEmpty(dataField));
    }
    
    public String getMeasuringInstruction() {
        return pcGetmeasuringInstruction(this);
    }
    
    public void setMeasuringInstruction(final String measuringInstruction) {
        pcSetmeasuringInstruction(this, StringUtils.trimToEmpty(measuringInstruction));
    }
    
    public String getComments() {
        return pcGetcomments(this);
    }
    
    public void setComments(final String comments) {
        pcSetcomments(this, comments);
    }
    
    public Date getDateObserved() {
        return pcGetdateObserved(this);
    }
    
    public void setDateObserved(final Date dateObserved) {
        pcSetdateObserved(this, dateObserved);
    }
    
    public Date getDateEntered() {
        return pcGetdateEntered(this);
    }
    
    public void setDateEntered(final Date dateEntered) {
        pcSetdateEntered(this, dateEntered);
    }
    
    @Override
    public int compareTo(final CachedMeasurement o) {
        return pcGetfacilityMeasurementPk(this).getCaisiItemId() - pcGetfacilityMeasurementPk(o).getCaisiItemId();
    }
    
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityMeasurementPk(this);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 8171701675811807108L;
        CachedMeasurement.pcFieldNames = new String[] { "caisiDemographicId", "caisiProviderId", "comments", "dataField", "dateEntered", "dateObserved", "facilityMeasurementPk", "measuringInstruction", "type" };
        CachedMeasurement.pcFieldTypes = new Class[] { (CachedMeasurement.class$Ljava$lang$Integer != null) ? CachedMeasurement.class$Ljava$lang$Integer : (CachedMeasurement.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedMeasurement.class$Ljava$lang$String != null) ? CachedMeasurement.class$Ljava$lang$String : (CachedMeasurement.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurement.class$Ljava$lang$String != null) ? CachedMeasurement.class$Ljava$lang$String : (CachedMeasurement.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurement.class$Ljava$lang$String != null) ? CachedMeasurement.class$Ljava$lang$String : (CachedMeasurement.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurement.class$Ljava$util$Date != null) ? CachedMeasurement.class$Ljava$util$Date : (CachedMeasurement.class$Ljava$util$Date = class$("java.util.Date")), (CachedMeasurement.class$Ljava$util$Date != null) ? CachedMeasurement.class$Ljava$util$Date : (CachedMeasurement.class$Ljava$util$Date = class$("java.util.Date")), (CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedMeasurement.class$Ljava$lang$String != null) ? CachedMeasurement.class$Ljava$lang$String : (CachedMeasurement.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurement.class$Ljava$lang$String != null) ? CachedMeasurement.class$Ljava$lang$String : (CachedMeasurement.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedMeasurement.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurement != null) ? CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurement : (CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurement = class$("ca.openosp.openo.caisi_integrator.dao.CachedMeasurement")), CachedMeasurement.pcFieldNames, CachedMeasurement.pcFieldTypes, CachedMeasurement.pcFieldFlags, CachedMeasurement.pcPCSuperclass, "CachedMeasurement", (PersistenceCapable)new CachedMeasurement());
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
        this.caisiProviderId = null;
        this.comments = null;
        this.dataField = null;
        this.dateEntered = null;
        this.dateObserved = null;
        this.facilityMeasurementPk = null;
        this.measuringInstruction = null;
        this.type = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedMeasurement cachedMeasurement = new CachedMeasurement();
        if (b) {
            cachedMeasurement.pcClearFields();
        }
        cachedMeasurement.pcStateManager = pcStateManager;
        cachedMeasurement.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedMeasurement;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedMeasurement cachedMeasurement = new CachedMeasurement();
        if (b) {
            cachedMeasurement.pcClearFields();
        }
        cachedMeasurement.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedMeasurement;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 9;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedMeasurement.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.caisiProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.comments = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.dataField = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.dateEntered = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.dateObserved = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.facilityMeasurementPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.measuringInstruction = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.type = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedMeasurement.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiDemographicId);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.caisiProviderId);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.comments);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.dataField);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.dateEntered);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.dateObserved);
                return;
            }
            case 6: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityMeasurementPk);
                return;
            }
            case 7: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.measuringInstruction);
                return;
            }
            case 8: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.type);
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
    
    protected void pcCopyField(final CachedMeasurement cachedMeasurement, final int n) {
        final int n2 = n - CachedMeasurement.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = cachedMeasurement.caisiDemographicId;
                return;
            }
            case 1: {
                this.caisiProviderId = cachedMeasurement.caisiProviderId;
                return;
            }
            case 2: {
                this.comments = cachedMeasurement.comments;
                return;
            }
            case 3: {
                this.dataField = cachedMeasurement.dataField;
                return;
            }
            case 4: {
                this.dateEntered = cachedMeasurement.dateEntered;
                return;
            }
            case 5: {
                this.dateObserved = cachedMeasurement.dateObserved;
                return;
            }
            case 6: {
                this.facilityMeasurementPk = cachedMeasurement.facilityMeasurementPk;
                return;
            }
            case 7: {
                this.measuringInstruction = cachedMeasurement.measuringInstruction;
                return;
            }
            case 8: {
                this.type = cachedMeasurement.type;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedMeasurement cachedMeasurement = (CachedMeasurement)o;
        if (cachedMeasurement.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedMeasurement, array[i]);
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
        fieldConsumer.storeObjectField(6 + CachedMeasurement.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityMeasurementPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedMeasurement\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurement != null) ? CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurement : (CachedMeasurement.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurement = class$("ca.openosp.openo.caisi_integrator.dao.CachedMeasurement")), (Object)this.facilityMeasurementPk);
    }
    
    private static final Integer pcGetcaisiDemographicId(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.caisiDemographicId;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 0);
        return cachedMeasurement.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedMeasurement cachedMeasurement, final Integer caisiDemographicId) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedMeasurement.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 0, (Object)cachedMeasurement.caisiDemographicId, (Object)caisiDemographicId, 0);
    }
    
    private static final String pcGetcaisiProviderId(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.caisiProviderId;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 1);
        return cachedMeasurement.caisiProviderId;
    }
    
    private static final void pcSetcaisiProviderId(final CachedMeasurement cachedMeasurement, final String caisiProviderId) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.caisiProviderId = caisiProviderId;
            return;
        }
        cachedMeasurement.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 1, cachedMeasurement.caisiProviderId, caisiProviderId, 0);
    }
    
    private static final String pcGetcomments(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.comments;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 2);
        return cachedMeasurement.comments;
    }
    
    private static final void pcSetcomments(final CachedMeasurement cachedMeasurement, final String comments) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.comments = comments;
            return;
        }
        cachedMeasurement.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 2, cachedMeasurement.comments, comments, 0);
    }
    
    private static final String pcGetdataField(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.dataField;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 3);
        return cachedMeasurement.dataField;
    }
    
    private static final void pcSetdataField(final CachedMeasurement cachedMeasurement, final String dataField) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.dataField = dataField;
            return;
        }
        cachedMeasurement.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 3, cachedMeasurement.dataField, dataField, 0);
    }
    
    private static final Date pcGetdateEntered(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.dateEntered;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 4);
        return cachedMeasurement.dateEntered;
    }
    
    private static final void pcSetdateEntered(final CachedMeasurement cachedMeasurement, final Date dateEntered) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.dateEntered = dateEntered;
            return;
        }
        cachedMeasurement.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 4, (Object)cachedMeasurement.dateEntered, (Object)dateEntered, 0);
    }
    
    private static final Date pcGetdateObserved(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.dateObserved;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 5);
        return cachedMeasurement.dateObserved;
    }
    
    private static final void pcSetdateObserved(final CachedMeasurement cachedMeasurement, final Date dateObserved) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.dateObserved = dateObserved;
            return;
        }
        cachedMeasurement.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 5, (Object)cachedMeasurement.dateObserved, (Object)dateObserved, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityMeasurementPk(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.facilityMeasurementPk;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 6);
        return cachedMeasurement.facilityMeasurementPk;
    }
    
    private static final void pcSetfacilityMeasurementPk(final CachedMeasurement cachedMeasurement, final FacilityIdIntegerCompositePk facilityMeasurementPk) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.facilityMeasurementPk = facilityMeasurementPk;
            return;
        }
        cachedMeasurement.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 6, (Object)cachedMeasurement.facilityMeasurementPk, (Object)facilityMeasurementPk, 0);
    }
    
    private static final String pcGetmeasuringInstruction(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.measuringInstruction;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 7);
        return cachedMeasurement.measuringInstruction;
    }
    
    private static final void pcSetmeasuringInstruction(final CachedMeasurement cachedMeasurement, final String measuringInstruction) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.measuringInstruction = measuringInstruction;
            return;
        }
        cachedMeasurement.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 7, cachedMeasurement.measuringInstruction, measuringInstruction, 0);
    }
    
    private static final String pcGettype(final CachedMeasurement cachedMeasurement) {
        if (cachedMeasurement.pcStateManager == null) {
            return cachedMeasurement.type;
        }
        cachedMeasurement.pcStateManager.accessingField(CachedMeasurement.pcInheritedFieldCount + 8);
        return cachedMeasurement.type;
    }
    
    private static final void pcSettype(final CachedMeasurement cachedMeasurement, final String type) {
        if (cachedMeasurement.pcStateManager == null) {
            cachedMeasurement.type = type;
            return;
        }
        cachedMeasurement.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurement, CachedMeasurement.pcInheritedFieldCount + 8, cachedMeasurement.type, type, 0);
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
