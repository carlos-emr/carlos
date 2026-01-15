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
public class CachedDemographicAllergy extends AbstractModel<FacilityIdIntegerCompositePk> implements PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    @Column(nullable = false)
    @Index
    private int caisiDemographicId;
    @Temporal(TemporalType.TIMESTAMP)
    private Date entryDate;
    private int pickId;
    private String description;
    private int hiclSeqNo;
    private int hicSeqNo;
    private int agcsp;
    private int agccs;
    private int typeCode;
    private String reaction;
    @Temporal(TemporalType.TIMESTAMP)
    private Date startDate;
    private String ageOfOnset;
    private String severityCode;
    private String onSetCode;
    private String regionalIdentifier;
    private String lifeStage;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicAllergy;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedDemographicAllergy() {
        this.caisiDemographicId = 0;
    }
    
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityIdIntegerCompositePk(this);
    }
    
    public void setCaisiDemographicId(final int caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }
    
    public int getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }
    
    public void setEntryDate(final Date entryDate) {
        pcSetentryDate(this, entryDate);
    }
    
    public Date getEntryDate() {
        return pcGetentryDate(this);
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityIdIntegerCompositePk(this);
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        pcSetfacilityIdIntegerCompositePk(this, facilityIdIntegerCompositePk);
    }
    
    public String getDescription() {
        return pcGetdescription(this);
    }
    
    public void setDescription(final String description) {
        pcSetdescription(this, StringUtils.trimToNull(description));
    }
    
    public String getReaction() {
        return pcGetreaction(this);
    }
    
    public void setReaction(final String reaction) {
        pcSetreaction(this, StringUtils.trimToNull(reaction));
    }
    
    public int getPickId() {
        return pcGetpickId(this);
    }
    
    public void setPickId(final int pickId) {
        pcSetpickId(this, pickId);
    }
    
    public int getHiclSeqNo() {
        return pcGethiclSeqNo(this);
    }
    
    public void setHiclSeqNo(final int hiclSeqNo) {
        pcSethiclSeqNo(this, hiclSeqNo);
    }
    
    public int getHicSeqNo() {
        return pcGethicSeqNo(this);
    }
    
    public void setHicSeqNo(final int hicSeqNo) {
        pcSethicSeqNo(this, hicSeqNo);
    }
    
    public int getAgcsp() {
        return pcGetagcsp(this);
    }
    
    public void setAgcsp(final int agcsp) {
        pcSetagcsp(this, agcsp);
    }
    
    public int getAgccs() {
        return pcGetagccs(this);
    }
    
    public void setAgccs(final int agccs) {
        pcSetagccs(this, agccs);
    }
    
    public int getTypeCode() {
        return pcGettypeCode(this);
    }
    
    public void setTypeCode(final int typeCode) {
        pcSettypeCode(this, typeCode);
    }
    
    public Date getStartDate() {
        return pcGetstartDate(this);
    }
    
    public void setStartDate(final Date startDate) {
        pcSetstartDate(this, startDate);
    }
    
    public String getAgeOfOnset() {
        return pcGetageOfOnset(this);
    }
    
    public void setAgeOfOnset(final String ageOfOnset) {
        pcSetageOfOnset(this, StringUtils.trimToNull(ageOfOnset));
    }
    
    public String getSeverityCode() {
        return pcGetseverityCode(this);
    }
    
    public void setSeverityCode(final String severityCode) {
        pcSetseverityCode(this, StringUtils.trimToNull(severityCode));
    }
    
    public String getOnSetCode() {
        return pcGetonSetCode(this);
    }
    
    public void setOnSetCode(final String onSetCode) {
        pcSetonSetCode(this, StringUtils.trimToNull(onSetCode));
    }
    
    public String getRegionalIdentifier() {
        return pcGetregionalIdentifier(this);
    }
    
    public void setRegionalIdentifier(final String regionalIdentifier) {
        pcSetregionalIdentifier(this, StringUtils.trimToNull(regionalIdentifier));
    }
    
    public String getLifeStage() {
        return pcGetlifeStage(this);
    }
    
    public void setLifeStage(final String lifeStage) {
        pcSetlifeStage(this, StringUtils.trimToNull(lifeStage));
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 6631114811357540778L;
        CachedDemographicAllergy.pcFieldNames = new String[] { "agccs", "agcsp", "ageOfOnset", "caisiDemographicId", "description", "entryDate", "facilityIdIntegerCompositePk", "hicSeqNo", "hiclSeqNo", "lifeStage", "onSetCode", "pickId", "reaction", "regionalIdentifier", "severityCode", "startDate", "typeCode" };
        CachedDemographicAllergy.pcFieldTypes = new Class[] { Integer.TYPE, Integer.TYPE, (CachedDemographicAllergy.class$Ljava$lang$String != null) ? CachedDemographicAllergy.class$Ljava$lang$String : (CachedDemographicAllergy.class$Ljava$lang$String = class$("java.lang.String")), Integer.TYPE, (CachedDemographicAllergy.class$Ljava$lang$String != null) ? CachedDemographicAllergy.class$Ljava$lang$String : (CachedDemographicAllergy.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicAllergy.class$Ljava$util$Date != null) ? CachedDemographicAllergy.class$Ljava$util$Date : (CachedDemographicAllergy.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), Integer.TYPE, Integer.TYPE, (CachedDemographicAllergy.class$Ljava$lang$String != null) ? CachedDemographicAllergy.class$Ljava$lang$String : (CachedDemographicAllergy.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicAllergy.class$Ljava$lang$String != null) ? CachedDemographicAllergy.class$Ljava$lang$String : (CachedDemographicAllergy.class$Ljava$lang$String = class$("java.lang.String")), Integer.TYPE, (CachedDemographicAllergy.class$Ljava$lang$String != null) ? CachedDemographicAllergy.class$Ljava$lang$String : (CachedDemographicAllergy.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicAllergy.class$Ljava$lang$String != null) ? CachedDemographicAllergy.class$Ljava$lang$String : (CachedDemographicAllergy.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicAllergy.class$Ljava$lang$String != null) ? CachedDemographicAllergy.class$Ljava$lang$String : (CachedDemographicAllergy.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicAllergy.class$Ljava$util$Date != null) ? CachedDemographicAllergy.class$Ljava$util$Date : (CachedDemographicAllergy.class$Ljava$util$Date = class$("java.util.Date")), Integer.TYPE };
        CachedDemographicAllergy.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicAllergy != null) ? CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicAllergy : (CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicAllergy = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicAllergy")), CachedDemographicAllergy.pcFieldNames, CachedDemographicAllergy.pcFieldTypes, CachedDemographicAllergy.pcFieldFlags, CachedDemographicAllergy.pcPCSuperclass, "CachedDemographicAllergy", (PersistenceCapable)new CachedDemographicAllergy());
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
        this.agccs = 0;
        this.agcsp = 0;
        this.ageOfOnset = null;
        this.caisiDemographicId = 0;
        this.description = null;
        this.entryDate = null;
        this.facilityIdIntegerCompositePk = null;
        this.hicSeqNo = 0;
        this.hiclSeqNo = 0;
        this.lifeStage = null;
        this.onSetCode = null;
        this.pickId = 0;
        this.reaction = null;
        this.regionalIdentifier = null;
        this.severityCode = null;
        this.startDate = null;
        this.typeCode = 0;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicAllergy cachedDemographicAllergy = new CachedDemographicAllergy();
        if (b) {
            cachedDemographicAllergy.pcClearFields();
        }
        cachedDemographicAllergy.pcStateManager = pcStateManager;
        cachedDemographicAllergy.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicAllergy;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicAllergy cachedDemographicAllergy = new CachedDemographicAllergy();
        if (b) {
            cachedDemographicAllergy.pcClearFields();
        }
        cachedDemographicAllergy.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicAllergy;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 17;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicAllergy.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.agccs = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.agcsp = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.ageOfOnset = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.caisiDemographicId = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.description = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.entryDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.hicSeqNo = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.hiclSeqNo = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.lifeStage = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 10: {
                this.onSetCode = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 11: {
                this.pickId = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 12: {
                this.reaction = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 13: {
                this.regionalIdentifier = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 14: {
                this.severityCode = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 15: {
                this.startDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 16: {
                this.typeCode = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedDemographicAllergy.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.agccs);
                return;
            }
            case 1: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.agcsp);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.ageOfOnset);
                return;
            }
            case 3: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.caisiDemographicId);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.description);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.entryDate);
                return;
            }
            case 6: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityIdIntegerCompositePk);
                return;
            }
            case 7: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.hicSeqNo);
                return;
            }
            case 8: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.hiclSeqNo);
                return;
            }
            case 9: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.lifeStage);
                return;
            }
            case 10: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.onSetCode);
                return;
            }
            case 11: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.pickId);
                return;
            }
            case 12: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.reaction);
                return;
            }
            case 13: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.regionalIdentifier);
                return;
            }
            case 14: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.severityCode);
                return;
            }
            case 15: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.startDate);
                return;
            }
            case 16: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.typeCode);
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
    
    protected void pcCopyField(final CachedDemographicAllergy cachedDemographicAllergy, final int n) {
        final int n2 = n - CachedDemographicAllergy.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.agccs = cachedDemographicAllergy.agccs;
                return;
            }
            case 1: {
                this.agcsp = cachedDemographicAllergy.agcsp;
                return;
            }
            case 2: {
                this.ageOfOnset = cachedDemographicAllergy.ageOfOnset;
                return;
            }
            case 3: {
                this.caisiDemographicId = cachedDemographicAllergy.caisiDemographicId;
                return;
            }
            case 4: {
                this.description = cachedDemographicAllergy.description;
                return;
            }
            case 5: {
                this.entryDate = cachedDemographicAllergy.entryDate;
                return;
            }
            case 6: {
                this.facilityIdIntegerCompositePk = cachedDemographicAllergy.facilityIdIntegerCompositePk;
                return;
            }
            case 7: {
                this.hicSeqNo = cachedDemographicAllergy.hicSeqNo;
                return;
            }
            case 8: {
                this.hiclSeqNo = cachedDemographicAllergy.hiclSeqNo;
                return;
            }
            case 9: {
                this.lifeStage = cachedDemographicAllergy.lifeStage;
                return;
            }
            case 10: {
                this.onSetCode = cachedDemographicAllergy.onSetCode;
                return;
            }
            case 11: {
                this.pickId = cachedDemographicAllergy.pickId;
                return;
            }
            case 12: {
                this.reaction = cachedDemographicAllergy.reaction;
                return;
            }
            case 13: {
                this.regionalIdentifier = cachedDemographicAllergy.regionalIdentifier;
                return;
            }
            case 14: {
                this.severityCode = cachedDemographicAllergy.severityCode;
                return;
            }
            case 15: {
                this.startDate = cachedDemographicAllergy.startDate;
                return;
            }
            case 16: {
                this.typeCode = cachedDemographicAllergy.typeCode;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicAllergy cachedDemographicAllergy = (CachedDemographicAllergy)o;
        if (cachedDemographicAllergy.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicAllergy, array[i]);
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
        fieldConsumer.storeObjectField(6 + CachedDemographicAllergy.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicAllergy\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicAllergy != null) ? CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicAllergy : (CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicAllergy = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicAllergy")), (Object)this.facilityIdIntegerCompositePk);
    }
    
    private static final int pcGetagccs(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.agccs;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 0);
        return cachedDemographicAllergy.agccs;
    }
    
    private static final void pcSetagccs(final CachedDemographicAllergy cachedDemographicAllergy, final int agccs) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.agccs = agccs;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 0, cachedDemographicAllergy.agccs, agccs, 0);
    }
    
    private static final int pcGetagcsp(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.agcsp;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 1);
        return cachedDemographicAllergy.agcsp;
    }
    
    private static final void pcSetagcsp(final CachedDemographicAllergy cachedDemographicAllergy, final int agcsp) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.agcsp = agcsp;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 1, cachedDemographicAllergy.agcsp, agcsp, 0);
    }
    
    private static final String pcGetageOfOnset(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.ageOfOnset;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 2);
        return cachedDemographicAllergy.ageOfOnset;
    }
    
    private static final void pcSetageOfOnset(final CachedDemographicAllergy cachedDemographicAllergy, final String ageOfOnset) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.ageOfOnset = ageOfOnset;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 2, cachedDemographicAllergy.ageOfOnset, ageOfOnset, 0);
    }
    
    private static final int pcGetcaisiDemographicId(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.caisiDemographicId;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 3);
        return cachedDemographicAllergy.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedDemographicAllergy cachedDemographicAllergy, final int caisiDemographicId) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 3, cachedDemographicAllergy.caisiDemographicId, caisiDemographicId, 0);
    }
    
    private static final String pcGetdescription(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.description;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 4);
        return cachedDemographicAllergy.description;
    }
    
    private static final void pcSetdescription(final CachedDemographicAllergy cachedDemographicAllergy, final String description) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.description = description;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 4, cachedDemographicAllergy.description, description, 0);
    }
    
    private static final Date pcGetentryDate(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.entryDate;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 5);
        return cachedDemographicAllergy.entryDate;
    }
    
    private static final void pcSetentryDate(final CachedDemographicAllergy cachedDemographicAllergy, final Date entryDate) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.entryDate = entryDate;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 5, (Object)cachedDemographicAllergy.entryDate, (Object)entryDate, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityIdIntegerCompositePk(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.facilityIdIntegerCompositePk;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 6);
        return cachedDemographicAllergy.facilityIdIntegerCompositePk;
    }
    
    private static final void pcSetfacilityIdIntegerCompositePk(final CachedDemographicAllergy cachedDemographicAllergy, final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 6, (Object)cachedDemographicAllergy.facilityIdIntegerCompositePk, (Object)facilityIdIntegerCompositePk, 0);
    }
    
    private static final int pcGethicSeqNo(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.hicSeqNo;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 7);
        return cachedDemographicAllergy.hicSeqNo;
    }
    
    private static final void pcSethicSeqNo(final CachedDemographicAllergy cachedDemographicAllergy, final int hicSeqNo) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.hicSeqNo = hicSeqNo;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 7, cachedDemographicAllergy.hicSeqNo, hicSeqNo, 0);
    }
    
    private static final int pcGethiclSeqNo(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.hiclSeqNo;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 8);
        return cachedDemographicAllergy.hiclSeqNo;
    }
    
    private static final void pcSethiclSeqNo(final CachedDemographicAllergy cachedDemographicAllergy, final int hiclSeqNo) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.hiclSeqNo = hiclSeqNo;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 8, cachedDemographicAllergy.hiclSeqNo, hiclSeqNo, 0);
    }
    
    private static final String pcGetlifeStage(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.lifeStage;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 9);
        return cachedDemographicAllergy.lifeStage;
    }
    
    private static final void pcSetlifeStage(final CachedDemographicAllergy cachedDemographicAllergy, final String lifeStage) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.lifeStage = lifeStage;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 9, cachedDemographicAllergy.lifeStage, lifeStage, 0);
    }
    
    private static final String pcGetonSetCode(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.onSetCode;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 10);
        return cachedDemographicAllergy.onSetCode;
    }
    
    private static final void pcSetonSetCode(final CachedDemographicAllergy cachedDemographicAllergy, final String onSetCode) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.onSetCode = onSetCode;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 10, cachedDemographicAllergy.onSetCode, onSetCode, 0);
    }
    
    private static final int pcGetpickId(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.pickId;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 11);
        return cachedDemographicAllergy.pickId;
    }
    
    private static final void pcSetpickId(final CachedDemographicAllergy cachedDemographicAllergy, final int pickId) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.pickId = pickId;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 11, cachedDemographicAllergy.pickId, pickId, 0);
    }
    
    private static final String pcGetreaction(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.reaction;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 12);
        return cachedDemographicAllergy.reaction;
    }
    
    private static final void pcSetreaction(final CachedDemographicAllergy cachedDemographicAllergy, final String reaction) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.reaction = reaction;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 12, cachedDemographicAllergy.reaction, reaction, 0);
    }
    
    private static final String pcGetregionalIdentifier(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.regionalIdentifier;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 13);
        return cachedDemographicAllergy.regionalIdentifier;
    }
    
    private static final void pcSetregionalIdentifier(final CachedDemographicAllergy cachedDemographicAllergy, final String regionalIdentifier) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.regionalIdentifier = regionalIdentifier;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 13, cachedDemographicAllergy.regionalIdentifier, regionalIdentifier, 0);
    }
    
    private static final String pcGetseverityCode(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.severityCode;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 14);
        return cachedDemographicAllergy.severityCode;
    }
    
    private static final void pcSetseverityCode(final CachedDemographicAllergy cachedDemographicAllergy, final String severityCode) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.severityCode = severityCode;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 14, cachedDemographicAllergy.severityCode, severityCode, 0);
    }
    
    private static final Date pcGetstartDate(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.startDate;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 15);
        return cachedDemographicAllergy.startDate;
    }
    
    private static final void pcSetstartDate(final CachedDemographicAllergy cachedDemographicAllergy, final Date startDate) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.startDate = startDate;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 15, (Object)cachedDemographicAllergy.startDate, (Object)startDate, 0);
    }
    
    private static final int pcGettypeCode(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.typeCode;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 16);
        return cachedDemographicAllergy.typeCode;
    }
    
    private static final void pcSettypeCode(final CachedDemographicAllergy cachedDemographicAllergy, final int typeCode) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.typeCode = typeCode;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 16, cachedDemographicAllergy.typeCode, typeCode, 0);
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
