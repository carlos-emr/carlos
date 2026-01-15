package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.util.IntId;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.util.InternalException;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PersistenceCapable;
import org.apache.openjpa.enhance.PCRegistry;
import org.apache.openjpa.enhance.StateManager;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Calendar;
import java.util.GregorianCalendar;
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Entity;

@Entity
public class HomelessPopulationReport extends AbstractModel<Integer> implements PersistenceCapable
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    @Index
    private Calendar reportTime;
    @Column(nullable = false)
    private int clientCountInPastYear;
    @Column(nullable = false)
    private int currentClientCount;
    @Column(nullable = false)
    private int usage1To10DaysInPast4Years;
    @Column(nullable = false)
    private int usage11To179DaysInPast4Years;
    @Column(nullable = false)
    private int usage180To730DaysInPast4Years;
    @Column(nullable = false)
    private int mortalityInPastYear;
    @Column(nullable = false)
    private int currentHiv;
    @Column(nullable = false)
    private int currentCancer;
    @Column(nullable = false)
    private int currentDiabetes;
    @Column(nullable = false)
    private int currentSeizure;
    @Column(nullable = false)
    private int currentLiverDisease;
    @Column(nullable = false)
    private int currentSchizophrenia;
    @Column(nullable = false)
    private int currentBipolar;
    @Column(nullable = false)
    private int currentDepression;
    @Column(nullable = false)
    private int currentCognitiveDisability;
    @Column(nullable = false)
    private int currentPneumonia;
    @Column(nullable = false)
    private int currentInfluenza;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$util$GregorianCalendar;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$HomelessPopulationReport;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public HomelessPopulationReport() {
        this.id = null;
        this.reportTime = null;
    }
    
    @Override
    public Integer getId() {
        return pcGetid(this);
    }
    
    public Calendar getReportTime() {
        return pcGetreportTime(this);
    }
    
    public void setReportTime(final Calendar reportTime) {
        pcSetreportTime(this, reportTime);
    }
    
    public int getClientCountInPastYear() {
        return pcGetclientCountInPastYear(this);
    }
    
    public void setClientCountInPastYear(final int clientCountInPastYear) {
        pcSetclientCountInPastYear(this, clientCountInPastYear);
    }
    
    public int getCurrentClientCount() {
        return pcGetcurrentClientCount(this);
    }
    
    public void setCurrentClientCount(final int currentClientCount) {
        pcSetcurrentClientCount(this, currentClientCount);
    }
    
    public int getUsage1To10DaysInPast4Years() {
        return pcGetusage1To10DaysInPast4Years(this);
    }
    
    public void setUsage1To10DaysInPast4Years(final int usage1To10DaysInPast4Years) {
        pcSetusage1To10DaysInPast4Years(this, usage1To10DaysInPast4Years);
    }
    
    public int getUsage11To179DaysInPast4Years() {
        return pcGetusage11To179DaysInPast4Years(this);
    }
    
    public void setUsage11To179DaysInPast4Years(final int usage11To179DaysInPast4Years) {
        pcSetusage11To179DaysInPast4Years(this, usage11To179DaysInPast4Years);
    }
    
    public int getUsage180To730DaysInPast4Years() {
        return pcGetusage180To730DaysInPast4Years(this);
    }
    
    public void setUsage180To730DaysInPast4Years(final int usage180To730DaysInPast4Years) {
        pcSetusage180To730DaysInPast4Years(this, usage180To730DaysInPast4Years);
    }
    
    public int getMortalityInPastYear() {
        return pcGetmortalityInPastYear(this);
    }
    
    public void setMortalityInPastYear(final int mortalityInPastYear) {
        pcSetmortalityInPastYear(this, mortalityInPastYear);
    }
    
    public int getCurrentHiv() {
        return pcGetcurrentHiv(this);
    }
    
    public void setCurrentHiv(final int currentHiv) {
        pcSetcurrentHiv(this, currentHiv);
    }
    
    public int getCurrentCancer() {
        return pcGetcurrentCancer(this);
    }
    
    public void setCurrentCancer(final int currentCancer) {
        pcSetcurrentCancer(this, currentCancer);
    }
    
    public int getCurrentDiabetes() {
        return pcGetcurrentDiabetes(this);
    }
    
    public void setCurrentDiabetes(final int currentDiabetes) {
        pcSetcurrentDiabetes(this, currentDiabetes);
    }
    
    public int getCurrentSeizure() {
        return pcGetcurrentSeizure(this);
    }
    
    public void setCurrentSeizure(final int currentSeizure) {
        pcSetcurrentSeizure(this, currentSeizure);
    }
    
    public int getCurrentLiverDisease() {
        return pcGetcurrentLiverDisease(this);
    }
    
    public void setCurrentLiverDisease(final int currentLiverDisease) {
        pcSetcurrentLiverDisease(this, currentLiverDisease);
    }
    
    public int getCurrentSchizophrenia() {
        return pcGetcurrentSchizophrenia(this);
    }
    
    public void setCurrentSchizophrenia(final int currentSchizophrenia) {
        pcSetcurrentSchizophrenia(this, currentSchizophrenia);
    }
    
    public int getCurrentBipolar() {
        return pcGetcurrentBipolar(this);
    }
    
    public void setCurrentBipolar(final int currentBipolar) {
        pcSetcurrentBipolar(this, currentBipolar);
    }
    
    public int getCurrentDepression() {
        return pcGetcurrentDepression(this);
    }
    
    public void setCurrentDepression(final int currentDepression) {
        pcSetcurrentDepression(this, currentDepression);
    }
    
    public int getCurrentCognitiveDisability() {
        return pcGetcurrentCognitiveDisability(this);
    }
    
    public void setCurrentCognitiveDisability(final int currentCognitiveDisability) {
        pcSetcurrentCognitiveDisability(this, currentCognitiveDisability);
    }
    
    public int getCurrentPneumonia() {
        return pcGetcurrentPneumonia(this);
    }
    
    public void setCurrentPneumonia(final int currentPneumonia) {
        pcSetcurrentPneumonia(this, currentPneumonia);
    }
    
    public int getCurrentInfluenza() {
        return pcGetcurrentInfluenza(this);
    }
    
    public void setCurrentInfluenza(final int currentInfluenza) {
        pcSetcurrentInfluenza(this, currentInfluenza);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -5271733349975208716L;
        HomelessPopulationReport.pcFieldNames = new String[] { "clientCountInPastYear", "currentBipolar", "currentCancer", "currentClientCount", "currentCognitiveDisability", "currentDepression", "currentDiabetes", "currentHiv", "currentInfluenza", "currentLiverDisease", "currentPneumonia", "currentSchizophrenia", "currentSeizure", "id", "mortalityInPastYear", "reportTime", "usage11To179DaysInPast4Years", "usage180To730DaysInPast4Years", "usage1To10DaysInPast4Years" };
        HomelessPopulationReport.pcFieldTypes = new Class[] { Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, (HomelessPopulationReport.class$Ljava$lang$Integer != null) ? HomelessPopulationReport.class$Ljava$lang$Integer : (HomelessPopulationReport.class$Ljava$lang$Integer = class$("java.lang.Integer")), Integer.TYPE, (HomelessPopulationReport.class$Ljava$util$GregorianCalendar != null) ? HomelessPopulationReport.class$Ljava$util$GregorianCalendar : (HomelessPopulationReport.class$Ljava$util$GregorianCalendar = class$("java.util.GregorianCalendar")), Integer.TYPE, Integer.TYPE, Integer.TYPE };
        HomelessPopulationReport.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((HomelessPopulationReport.class$Lca$openosp$openo$caisi_integrator$dao$HomelessPopulationReport != null) ? HomelessPopulationReport.class$Lca$openosp$openo$caisi_integrator$dao$HomelessPopulationReport : (HomelessPopulationReport.class$Lca$openosp$openo$caisi_integrator$dao$HomelessPopulationReport = class$("ca.openosp.openo.caisi_integrator.dao.HomelessPopulationReport")), HomelessPopulationReport.pcFieldNames, HomelessPopulationReport.pcFieldTypes, HomelessPopulationReport.pcFieldFlags, HomelessPopulationReport.pcPCSuperclass, "HomelessPopulationReport", (PersistenceCapable)new HomelessPopulationReport());
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
        this.clientCountInPastYear = 0;
        this.currentBipolar = 0;
        this.currentCancer = 0;
        this.currentClientCount = 0;
        this.currentCognitiveDisability = 0;
        this.currentDepression = 0;
        this.currentDiabetes = 0;
        this.currentHiv = 0;
        this.currentInfluenza = 0;
        this.currentLiverDisease = 0;
        this.currentPneumonia = 0;
        this.currentSchizophrenia = 0;
        this.currentSeizure = 0;
        this.id = null;
        this.mortalityInPastYear = 0;
        this.reportTime = null;
        this.usage11To179DaysInPast4Years = 0;
        this.usage180To730DaysInPast4Years = 0;
        this.usage1To10DaysInPast4Years = 0;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final HomelessPopulationReport homelessPopulationReport = new HomelessPopulationReport();
        if (b) {
            homelessPopulationReport.pcClearFields();
        }
        homelessPopulationReport.pcStateManager = pcStateManager;
        homelessPopulationReport.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)homelessPopulationReport;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final HomelessPopulationReport homelessPopulationReport = new HomelessPopulationReport();
        if (b) {
            homelessPopulationReport.pcClearFields();
        }
        homelessPopulationReport.pcStateManager = pcStateManager;
        return (PersistenceCapable)homelessPopulationReport;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 19;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - HomelessPopulationReport.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.clientCountInPastYear = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.currentBipolar = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.currentCancer = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.currentClientCount = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.currentCognitiveDisability = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.currentDepression = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.currentDiabetes = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.currentHiv = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.currentInfluenza = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.currentLiverDisease = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 10: {
                this.currentPneumonia = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 11: {
                this.currentSchizophrenia = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 12: {
                this.currentSeizure = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 13: {
                this.id = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 14: {
                this.mortalityInPastYear = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 15: {
                this.reportTime = (Calendar)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 16: {
                this.usage11To179DaysInPast4Years = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 17: {
                this.usage180To730DaysInPast4Years = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 18: {
                this.usage1To10DaysInPast4Years = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
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
        final int n2 = n - HomelessPopulationReport.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.clientCountInPastYear);
                return;
            }
            case 1: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.currentBipolar);
                return;
            }
            case 2: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.currentCancer);
                return;
            }
            case 3: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.currentClientCount);
                return;
            }
            case 4: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.currentCognitiveDisability);
                return;
            }
            case 5: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.currentDepression);
                return;
            }
            case 6: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.currentDiabetes);
                return;
            }
            case 7: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.currentHiv);
                return;
            }
            case 8: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.currentInfluenza);
                return;
            }
            case 9: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.currentLiverDisease);
                return;
            }
            case 10: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.currentPneumonia);
                return;
            }
            case 11: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.currentSchizophrenia);
                return;
            }
            case 12: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.currentSeizure);
                return;
            }
            case 13: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.id);
                return;
            }
            case 14: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.mortalityInPastYear);
                return;
            }
            case 15: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.reportTime);
                return;
            }
            case 16: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.usage11To179DaysInPast4Years);
                return;
            }
            case 17: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.usage180To730DaysInPast4Years);
                return;
            }
            case 18: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.usage1To10DaysInPast4Years);
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
    
    protected void pcCopyField(final HomelessPopulationReport homelessPopulationReport, final int n) {
        final int n2 = n - HomelessPopulationReport.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.clientCountInPastYear = homelessPopulationReport.clientCountInPastYear;
                return;
            }
            case 1: {
                this.currentBipolar = homelessPopulationReport.currentBipolar;
                return;
            }
            case 2: {
                this.currentCancer = homelessPopulationReport.currentCancer;
                return;
            }
            case 3: {
                this.currentClientCount = homelessPopulationReport.currentClientCount;
                return;
            }
            case 4: {
                this.currentCognitiveDisability = homelessPopulationReport.currentCognitiveDisability;
                return;
            }
            case 5: {
                this.currentDepression = homelessPopulationReport.currentDepression;
                return;
            }
            case 6: {
                this.currentDiabetes = homelessPopulationReport.currentDiabetes;
                return;
            }
            case 7: {
                this.currentHiv = homelessPopulationReport.currentHiv;
                return;
            }
            case 8: {
                this.currentInfluenza = homelessPopulationReport.currentInfluenza;
                return;
            }
            case 9: {
                this.currentLiverDisease = homelessPopulationReport.currentLiverDisease;
                return;
            }
            case 10: {
                this.currentPneumonia = homelessPopulationReport.currentPneumonia;
                return;
            }
            case 11: {
                this.currentSchizophrenia = homelessPopulationReport.currentSchizophrenia;
                return;
            }
            case 12: {
                this.currentSeizure = homelessPopulationReport.currentSeizure;
                return;
            }
            case 13: {
                this.id = homelessPopulationReport.id;
                return;
            }
            case 14: {
                this.mortalityInPastYear = homelessPopulationReport.mortalityInPastYear;
                return;
            }
            case 15: {
                this.reportTime = homelessPopulationReport.reportTime;
                return;
            }
            case 16: {
                this.usage11To179DaysInPast4Years = homelessPopulationReport.usage11To179DaysInPast4Years;
                return;
            }
            case 17: {
                this.usage180To730DaysInPast4Years = homelessPopulationReport.usage180To730DaysInPast4Years;
                return;
            }
            case 18: {
                this.usage1To10DaysInPast4Years = homelessPopulationReport.usage1To10DaysInPast4Years;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final HomelessPopulationReport homelessPopulationReport = (HomelessPopulationReport)o;
        if (homelessPopulationReport.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(homelessPopulationReport, array[i]);
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
        fieldConsumer.storeObjectField(13 + HomelessPopulationReport.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Integer.valueOf(((IntId)o).getId());
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((HomelessPopulationReport.class$Lca$openosp$openo$caisi_integrator$dao$HomelessPopulationReport != null) ? HomelessPopulationReport.class$Lca$openosp$openo$caisi_integrator$dao$HomelessPopulationReport : (HomelessPopulationReport.class$Lca$openosp$openo$caisi_integrator$dao$HomelessPopulationReport = class$("ca.openosp.openo.caisi_integrator.dao.HomelessPopulationReport")), (String)o);
    }
    
    public Object pcNewObjectIdInstance() {
        return new IntId((HomelessPopulationReport.class$Lca$openosp$openo$caisi_integrator$dao$HomelessPopulationReport != null) ? HomelessPopulationReport.class$Lca$openosp$openo$caisi_integrator$dao$HomelessPopulationReport : (HomelessPopulationReport.class$Lca$openosp$openo$caisi_integrator$dao$HomelessPopulationReport = class$("ca.openosp.openo.caisi_integrator.dao.HomelessPopulationReport")), this.id);
    }
    
    private static final int pcGetclientCountInPastYear(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.clientCountInPastYear;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 0);
        return homelessPopulationReport.clientCountInPastYear;
    }
    
    private static final void pcSetclientCountInPastYear(final HomelessPopulationReport homelessPopulationReport, final int clientCountInPastYear) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.clientCountInPastYear = clientCountInPastYear;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 0, homelessPopulationReport.clientCountInPastYear, clientCountInPastYear, 0);
    }
    
    private static final int pcGetcurrentBipolar(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.currentBipolar;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 1);
        return homelessPopulationReport.currentBipolar;
    }
    
    private static final void pcSetcurrentBipolar(final HomelessPopulationReport homelessPopulationReport, final int currentBipolar) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.currentBipolar = currentBipolar;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 1, homelessPopulationReport.currentBipolar, currentBipolar, 0);
    }
    
    private static final int pcGetcurrentCancer(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.currentCancer;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 2);
        return homelessPopulationReport.currentCancer;
    }
    
    private static final void pcSetcurrentCancer(final HomelessPopulationReport homelessPopulationReport, final int currentCancer) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.currentCancer = currentCancer;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 2, homelessPopulationReport.currentCancer, currentCancer, 0);
    }
    
    private static final int pcGetcurrentClientCount(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.currentClientCount;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 3);
        return homelessPopulationReport.currentClientCount;
    }
    
    private static final void pcSetcurrentClientCount(final HomelessPopulationReport homelessPopulationReport, final int currentClientCount) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.currentClientCount = currentClientCount;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 3, homelessPopulationReport.currentClientCount, currentClientCount, 0);
    }
    
    private static final int pcGetcurrentCognitiveDisability(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.currentCognitiveDisability;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 4);
        return homelessPopulationReport.currentCognitiveDisability;
    }
    
    private static final void pcSetcurrentCognitiveDisability(final HomelessPopulationReport homelessPopulationReport, final int currentCognitiveDisability) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.currentCognitiveDisability = currentCognitiveDisability;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 4, homelessPopulationReport.currentCognitiveDisability, currentCognitiveDisability, 0);
    }
    
    private static final int pcGetcurrentDepression(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.currentDepression;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 5);
        return homelessPopulationReport.currentDepression;
    }
    
    private static final void pcSetcurrentDepression(final HomelessPopulationReport homelessPopulationReport, final int currentDepression) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.currentDepression = currentDepression;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 5, homelessPopulationReport.currentDepression, currentDepression, 0);
    }
    
    private static final int pcGetcurrentDiabetes(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.currentDiabetes;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 6);
        return homelessPopulationReport.currentDiabetes;
    }
    
    private static final void pcSetcurrentDiabetes(final HomelessPopulationReport homelessPopulationReport, final int currentDiabetes) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.currentDiabetes = currentDiabetes;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 6, homelessPopulationReport.currentDiabetes, currentDiabetes, 0);
    }
    
    private static final int pcGetcurrentHiv(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.currentHiv;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 7);
        return homelessPopulationReport.currentHiv;
    }
    
    private static final void pcSetcurrentHiv(final HomelessPopulationReport homelessPopulationReport, final int currentHiv) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.currentHiv = currentHiv;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 7, homelessPopulationReport.currentHiv, currentHiv, 0);
    }
    
    private static final int pcGetcurrentInfluenza(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.currentInfluenza;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 8);
        return homelessPopulationReport.currentInfluenza;
    }
    
    private static final void pcSetcurrentInfluenza(final HomelessPopulationReport homelessPopulationReport, final int currentInfluenza) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.currentInfluenza = currentInfluenza;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 8, homelessPopulationReport.currentInfluenza, currentInfluenza, 0);
    }
    
    private static final int pcGetcurrentLiverDisease(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.currentLiverDisease;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 9);
        return homelessPopulationReport.currentLiverDisease;
    }
    
    private static final void pcSetcurrentLiverDisease(final HomelessPopulationReport homelessPopulationReport, final int currentLiverDisease) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.currentLiverDisease = currentLiverDisease;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 9, homelessPopulationReport.currentLiverDisease, currentLiverDisease, 0);
    }
    
    private static final int pcGetcurrentPneumonia(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.currentPneumonia;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 10);
        return homelessPopulationReport.currentPneumonia;
    }
    
    private static final void pcSetcurrentPneumonia(final HomelessPopulationReport homelessPopulationReport, final int currentPneumonia) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.currentPneumonia = currentPneumonia;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 10, homelessPopulationReport.currentPneumonia, currentPneumonia, 0);
    }
    
    private static final int pcGetcurrentSchizophrenia(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.currentSchizophrenia;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 11);
        return homelessPopulationReport.currentSchizophrenia;
    }
    
    private static final void pcSetcurrentSchizophrenia(final HomelessPopulationReport homelessPopulationReport, final int currentSchizophrenia) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.currentSchizophrenia = currentSchizophrenia;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 11, homelessPopulationReport.currentSchizophrenia, currentSchizophrenia, 0);
    }
    
    private static final int pcGetcurrentSeizure(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.currentSeizure;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 12);
        return homelessPopulationReport.currentSeizure;
    }
    
    private static final void pcSetcurrentSeizure(final HomelessPopulationReport homelessPopulationReport, final int currentSeizure) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.currentSeizure = currentSeizure;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 12, homelessPopulationReport.currentSeizure, currentSeizure, 0);
    }
    
    private static final Integer pcGetid(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.id;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 13);
        return homelessPopulationReport.id;
    }
    
    private static final void pcSetid(final HomelessPopulationReport homelessPopulationReport, final Integer id) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.id = id;
            return;
        }
        homelessPopulationReport.pcStateManager.settingObjectField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 13, (Object)homelessPopulationReport.id, (Object)id, 0);
    }
    
    private static final int pcGetmortalityInPastYear(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.mortalityInPastYear;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 14);
        return homelessPopulationReport.mortalityInPastYear;
    }
    
    private static final void pcSetmortalityInPastYear(final HomelessPopulationReport homelessPopulationReport, final int mortalityInPastYear) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.mortalityInPastYear = mortalityInPastYear;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 14, homelessPopulationReport.mortalityInPastYear, mortalityInPastYear, 0);
    }
    
    private static final Calendar pcGetreportTime(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.reportTime;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 15);
        return homelessPopulationReport.reportTime;
    }
    
    private static final void pcSetreportTime(final HomelessPopulationReport homelessPopulationReport, final Calendar reportTime) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.reportTime = reportTime;
            return;
        }
        homelessPopulationReport.pcStateManager.settingObjectField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 15, (Object)homelessPopulationReport.reportTime, (Object)reportTime, 0);
    }
    
    private static final int pcGetusage11To179DaysInPast4Years(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.usage11To179DaysInPast4Years;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 16);
        return homelessPopulationReport.usage11To179DaysInPast4Years;
    }
    
    private static final void pcSetusage11To179DaysInPast4Years(final HomelessPopulationReport homelessPopulationReport, final int usage11To179DaysInPast4Years) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.usage11To179DaysInPast4Years = usage11To179DaysInPast4Years;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 16, homelessPopulationReport.usage11To179DaysInPast4Years, usage11To179DaysInPast4Years, 0);
    }
    
    private static final int pcGetusage180To730DaysInPast4Years(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.usage180To730DaysInPast4Years;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 17);
        return homelessPopulationReport.usage180To730DaysInPast4Years;
    }
    
    private static final void pcSetusage180To730DaysInPast4Years(final HomelessPopulationReport homelessPopulationReport, final int usage180To730DaysInPast4Years) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.usage180To730DaysInPast4Years = usage180To730DaysInPast4Years;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 17, homelessPopulationReport.usage180To730DaysInPast4Years, usage180To730DaysInPast4Years, 0);
    }
    
    private static final int pcGetusage1To10DaysInPast4Years(final HomelessPopulationReport homelessPopulationReport) {
        if (homelessPopulationReport.pcStateManager == null) {
            return homelessPopulationReport.usage1To10DaysInPast4Years;
        }
        homelessPopulationReport.pcStateManager.accessingField(HomelessPopulationReport.pcInheritedFieldCount + 18);
        return homelessPopulationReport.usage1To10DaysInPast4Years;
    }
    
    private static final void pcSetusage1To10DaysInPast4Years(final HomelessPopulationReport homelessPopulationReport, final int usage1To10DaysInPast4Years) {
        if (homelessPopulationReport.pcStateManager == null) {
            homelessPopulationReport.usage1To10DaysInPast4Years = usage1To10DaysInPast4Years;
            return;
        }
        homelessPopulationReport.pcStateManager.settingIntField((PersistenceCapable)homelessPopulationReport, HomelessPopulationReport.pcInheritedFieldCount + 18, homelessPopulationReport.usage1To10DaysInPast4Years, usage1To10DaysInPast4Years, 0);
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
