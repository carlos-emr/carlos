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

/**
 * JPA entity representing a homeless population report for the CAISI (Client Access to Integrated Services and Information) integrator module.
 * This entity tracks statistical data about homeless clients including demographics, healthcare utilization patterns, and prevalence
 * of various medical and mental health conditions. The data supports healthcare planning and resource allocation for vulnerable populations.
 *
 * <p>This class is enhanced by OpenJPA for persistence and includes automated field management through the PersistenceCapable interface.
 * All field access is mediated through the StateManager to enable transparent persistence, change tracking, and lazy loading.</p>
 *
 * <p><strong>Healthcare Context:</strong> This report aggregates data for homeless population health surveillance, tracking:
 * <ul>
 *   <li>Client counts and service utilization patterns over time</li>
 *   <li>Mortality statistics</li>
 *   <li>Prevalence of chronic conditions (HIV, cancer, diabetes, liver disease)</li>
 *   <li>Mental health conditions (schizophrenia, bipolar disorder, depression, cognitive disabilities)</li>
 *   <li>Acute conditions (pneumonia, influenza)</li>
 * </ul>
 * </p>
 *
 * @see AbstractModel
 * @see PersistenceCapable
 * @since 2026-01-24
 */
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

    /**
     * Default constructor creating a new HomelessPopulationReport instance with null id and reportTime.
     * All statistical fields are initialized to their default values (0 for primitive integers).
     */
    public HomelessPopulationReport() {
        this.id = null;
        this.reportTime = null;
    }

    /**
     * Retrieves the unique identifier for this homeless population report.
     *
     * @return Integer the unique database-generated ID, or null if not yet persisted
     */
    @Override
    public Integer getId() {
        return pcGetid(this);
    }

    /**
     * Retrieves the timestamp when this report was generated.
     *
     * @return Calendar the report generation time, indexed for efficient querying
     */
    public Calendar getReportTime() {
        return pcGetreportTime(this);
    }

    /**
     * Sets the timestamp when this report was generated.
     *
     * @param reportTime Calendar the report generation time (required, cannot be null)
     */
    public void setReportTime(final Calendar reportTime) {
        pcSetreportTime(this, reportTime);
    }

    /**
     * Retrieves the total number of homeless clients served in the past year.
     *
     * @return int the count of unique clients receiving services in the past 12 months
     */
    public int getClientCountInPastYear() {
        return pcGetclientCountInPastYear(this);
    }

    /**
     * Sets the total number of homeless clients served in the past year.
     *
     * @param clientCountInPastYear int the count of unique clients (required, non-negative)
     */
    public void setClientCountInPastYear(final int clientCountInPastYear) {
        pcSetclientCountInPastYear(this, clientCountInPastYear);
    }

    /**
     * Retrieves the current active client count at the time of report generation.
     *
     * @return int the number of currently active homeless clients
     */
    public int getCurrentClientCount() {
        return pcGetcurrentClientCount(this);
    }

    /**
     * Sets the current active client count at the time of report generation.
     *
     * @param currentClientCount int the number of currently active clients (required, non-negative)
     */
    public void setCurrentClientCount(final int currentClientCount) {
        pcSetcurrentClientCount(this, currentClientCount);
    }

    /**
     * Retrieves the number of clients with minimal service utilization (1-10 days) in the past 4 years.
     * This metric helps identify clients with sporadic or initial engagement with services.
     *
     * @return int the count of clients using services 1-10 days in the past 48 months
     */
    public int getUsage1To10DaysInPast4Years() {
        return pcGetusage1To10DaysInPast4Years(this);
    }

    /**
     * Sets the number of clients with minimal service utilization (1-10 days) in the past 4 years.
     *
     * @param usage1To10DaysInPast4Years int the count of minimally engaged clients (required, non-negative)
     */
    public void setUsage1To10DaysInPast4Years(final int usage1To10DaysInPast4Years) {
        pcSetusage1To10DaysInPast4Years(this, usage1To10DaysInPast4Years);
    }

    /**
     * Retrieves the number of clients with moderate service utilization (11-179 days) in the past 4 years.
     * This metric identifies clients with regular but not intensive service engagement.
     *
     * @return int the count of clients using services 11-179 days in the past 48 months
     */
    public int getUsage11To179DaysInPast4Years() {
        return pcGetusage11To179DaysInPast4Years(this);
    }

    /**
     * Sets the number of clients with moderate service utilization (11-179 days) in the past 4 years.
     *
     * @param usage11To179DaysInPast4Years int the count of moderately engaged clients (required, non-negative)
     */
    public void setUsage11To179DaysInPast4Years(final int usage11To179DaysInPast4Years) {
        pcSetusage11To179DaysInPast4Years(this, usage11To179DaysInPast4Years);
    }

    /**
     * Retrieves the number of clients with intensive service utilization (180-730 days) in the past 4 years.
     * This metric identifies chronically homeless clients with sustained service needs (6 months to 2 years).
     *
     * @return int the count of clients using services 180-730 days in the past 48 months
     */
    public int getUsage180To730DaysInPast4Years() {
        return pcGetusage180To730DaysInPast4Years(this);
    }

    /**
     * Sets the number of clients with intensive service utilization (180-730 days) in the past 4 years.
     *
     * @param usage180To730DaysInPast4Years int the count of intensively engaged clients (required, non-negative)
     */
    public void setUsage180To730DaysInPast4Years(final int usage180To730DaysInPast4Years) {
        pcSetusage180To730DaysInPast4Years(this, usage180To730DaysInPast4Years);
    }

    /**
     * Retrieves the number of homeless client deaths in the past year.
     * This critical metric tracks mortality among the homeless population for public health surveillance.
     *
     * @return int the count of client deaths in the past 12 months
     */
    public int getMortalityInPastYear() {
        return pcGetmortalityInPastYear(this);
    }

    /**
     * Sets the number of homeless client deaths in the past year.
     *
     * @param mortalityInPastYear int the count of client deaths (required, non-negative)
     */
    public void setMortalityInPastYear(final int mortalityInPastYear) {
        pcSetmortalityInPastYear(this, mortalityInPastYear);
    }

    /**
     * Retrieves the current number of homeless clients diagnosed with HIV.
     * Tracks HIV prevalence for targeted health interventions and resource planning.
     *
     * @return int the count of current clients with HIV diagnosis
     */
    public int getCurrentHiv() {
        return pcGetcurrentHiv(this);
    }

    /**
     * Sets the current number of homeless clients diagnosed with HIV.
     *
     * @param currentHiv int the count of clients with HIV (required, non-negative)
     */
    public void setCurrentHiv(final int currentHiv) {
        pcSetcurrentHiv(this, currentHiv);
    }

    /**
     * Retrieves the current number of homeless clients diagnosed with cancer.
     * Tracks cancer prevalence for oncology service planning and referral needs.
     *
     * @return int the count of current clients with cancer diagnosis
     */
    public int getCurrentCancer() {
        return pcGetcurrentCancer(this);
    }

    /**
     * Sets the current number of homeless clients diagnosed with cancer.
     *
     * @param currentCancer int the count of clients with cancer (required, non-negative)
     */
    public void setCurrentCancer(final int currentCancer) {
        pcSetcurrentCancer(this, currentCancer);
    }

    /**
     * Retrieves the current number of homeless clients diagnosed with diabetes.
     * Tracks diabetes prevalence for chronic disease management and medication access programs.
     *
     * @return int the count of current clients with diabetes diagnosis
     */
    public int getCurrentDiabetes() {
        return pcGetcurrentDiabetes(this);
    }

    /**
     * Sets the current number of homeless clients diagnosed with diabetes.
     *
     * @param currentDiabetes int the count of clients with diabetes (required, non-negative)
     */
    public void setCurrentDiabetes(final int currentDiabetes) {
        pcSetcurrentDiabetes(this, currentDiabetes);
    }

    /**
     * Retrieves the current number of homeless clients with seizure disorders.
     * Tracks seizure disorder prevalence for neurological care needs and medication management.
     *
     * @return int the count of current clients with seizure disorders
     */
    public int getCurrentSeizure() {
        return pcGetcurrentSeizure(this);
    }

    /**
     * Sets the current number of homeless clients with seizure disorders.
     *
     * @param currentSeizure int the count of clients with seizure disorders (required, non-negative)
     */
    public void setCurrentSeizure(final int currentSeizure) {
        pcSetcurrentSeizure(this, currentSeizure);
    }

    /**
     * Retrieves the current number of homeless clients diagnosed with liver disease.
     * Tracks liver disease prevalence including cirrhosis and hepatitis conditions.
     *
     * @return int the count of current clients with liver disease diagnosis
     */
    public int getCurrentLiverDisease() {
        return pcGetcurrentLiverDisease(this);
    }

    /**
     * Sets the current number of homeless clients diagnosed with liver disease.
     *
     * @param currentLiverDisease int the count of clients with liver disease (required, non-negative)
     */
    public void setCurrentLiverDisease(final int currentLiverDisease) {
        pcSetcurrentLiverDisease(this, currentLiverDisease);
    }

    /**
     * Retrieves the current number of homeless clients diagnosed with schizophrenia.
     * Tracks schizophrenia prevalence for mental health service planning and psychiatric care coordination.
     *
     * @return int the count of current clients with schizophrenia diagnosis
     */
    public int getCurrentSchizophrenia() {
        return pcGetcurrentSchizophrenia(this);
    }

    /**
     * Sets the current number of homeless clients diagnosed with schizophrenia.
     *
     * @param currentSchizophrenia int the count of clients with schizophrenia (required, non-negative)
     */
    public void setCurrentSchizophrenia(final int currentSchizophrenia) {
        pcSetcurrentSchizophrenia(this, currentSchizophrenia);
    }

    /**
     * Retrieves the current number of homeless clients diagnosed with bipolar disorder.
     * Tracks bipolar disorder prevalence for mood disorder treatment programs and medication management.
     *
     * @return int the count of current clients with bipolar disorder diagnosis
     */
    public int getCurrentBipolar() {
        return pcGetcurrentBipolar(this);
    }

    /**
     * Sets the current number of homeless clients diagnosed with bipolar disorder.
     *
     * @param currentBipolar int the count of clients with bipolar disorder (required, non-negative)
     */
    public void setCurrentBipolar(final int currentBipolar) {
        pcSetcurrentBipolar(this, currentBipolar);
    }

    /**
     * Retrieves the current number of homeless clients diagnosed with depression.
     * Tracks depression prevalence for mental health counseling and antidepressant medication access.
     *
     * @return int the count of current clients with depression diagnosis
     */
    public int getCurrentDepression() {
        return pcGetcurrentDepression(this);
    }

    /**
     * Sets the current number of homeless clients diagnosed with depression.
     *
     * @param currentDepression int the count of clients with depression (required, non-negative)
     */
    public void setCurrentDepression(final int currentDepression) {
        pcSetcurrentDepression(this, currentDepression);
    }

    /**
     * Retrieves the current number of homeless clients with cognitive disabilities.
     * Tracks cognitive impairment prevalence for specialized care planning and support services.
     *
     * @return int the count of current clients with cognitive disabilities
     */
    public int getCurrentCognitiveDisability() {
        return pcGetcurrentCognitiveDisability(this);
    }

    /**
     * Sets the current number of homeless clients with cognitive disabilities.
     *
     * @param currentCognitiveDisability int the count of clients with cognitive disabilities (required, non-negative)
     */
    public void setCurrentCognitiveDisability(final int currentCognitiveDisability) {
        pcSetcurrentCognitiveDisability(this, currentCognitiveDisability);
    }

    /**
     * Retrieves the current number of homeless clients diagnosed with pneumonia.
     * Tracks pneumonia cases for respiratory illness surveillance and vaccination planning.
     *
     * @return int the count of current clients with pneumonia diagnosis
     */
    public int getCurrentPneumonia() {
        return pcGetcurrentPneumonia(this);
    }

    /**
     * Sets the current number of homeless clients diagnosed with pneumonia.
     *
     * @param currentPneumonia int the count of clients with pneumonia (required, non-negative)
     */
    public void setCurrentPneumonia(final int currentPneumonia) {
        pcSetcurrentPneumonia(this, currentPneumonia);
    }

    /**
     * Retrieves the current number of homeless clients diagnosed with influenza.
     * Tracks influenza cases for infectious disease surveillance and vaccination programs.
     *
     * @return int the count of current clients with influenza diagnosis
     */
    public int getCurrentInfluenza() {
        return pcGetcurrentInfluenza(this);
    }

    /**
     * Sets the current number of homeless clients diagnosed with influenza.
     *
     * @param currentInfluenza int the count of clients with influenza (required, non-negative)
     */
    public void setCurrentInfluenza(final int currentInfluenza) {
        pcSetcurrentInfluenza(this, currentInfluenza);
    }

    /**
     * Returns the OpenJPA enhancement contract version for this entity.
     * This method is part of the OpenJPA bytecode enhancement API and should not be called directly.
     *
     * @return int the enhancement contract version (always 2 for this entity)
     */
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

    /**
     * Clears all persistent fields to their default values.
     * This method is part of the OpenJPA PersistenceCapable interface and is called during
     * entity initialization to reset field values. Should not be called directly by application code.
     */
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

    /**
     * Creates a new instance of HomelessPopulationReport for persistence operations with object ID initialization.
     * This method is part of the OpenJPA PersistenceCapable interface and should not be called directly.
     *
     * @param pcStateManager StateManager the OpenJPA state manager for this instance
     * @param o Object the object ID to copy key fields from
     * @param b boolean whether to clear all fields to default values
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final HomelessPopulationReport homelessPopulationReport = new HomelessPopulationReport();
        if (b) {
            homelessPopulationReport.pcClearFields();
        }
        homelessPopulationReport.pcStateManager = pcStateManager;
        homelessPopulationReport.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)homelessPopulationReport;
    }

    /**
     * Creates a new instance of HomelessPopulationReport for persistence operations.
     * This method is part of the OpenJPA PersistenceCapable interface and should not be called directly.
     *
     * @param pcStateManager StateManager the OpenJPA state manager for this instance
     * @param b boolean whether to clear all fields to default values
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final HomelessPopulationReport homelessPopulationReport = new HomelessPopulationReport();
        if (b) {
            homelessPopulationReport.pcClearFields();
        }
        homelessPopulationReport.pcStateManager = pcStateManager;
        return (PersistenceCapable)homelessPopulationReport;
    }

    /**
     * Returns the total count of managed fields in this entity.
     * This method is part of the OpenJPA PersistenceCapable interface and should not be called directly.
     *
     * @return int the number of managed fields (19 fields total)
     */
    protected static int pcGetManagedFieldCount() {
        return 19;
    }

    /**
     * Replaces a single managed field value from the state manager.
     * This method is part of the OpenJPA PersistenceCapable interface and handles field-level
     * state restoration during persistence operations. Should not be called directly.
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
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

    /**
     * Replaces multiple managed field values from the state manager.
     * This method is part of the OpenJPA PersistenceCapable interface and handles bulk field
     * restoration during persistence operations. Should not be called directly.
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single managed field value to the state manager.
     * This method is part of the OpenJPA PersistenceCapable interface and handles field-level
     * state capture during persistence operations. Should not be called directly.
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
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

    /**
     * Provides multiple managed field values to the state manager.
     * This method is part of the OpenJPA PersistenceCapable interface and handles bulk field
     * capture during persistence operations. Should not be called directly.
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }

    /**
     * Copies a single field value from another HomelessPopulationReport instance.
     * This method is part of the OpenJPA PersistenceCapable interface and handles field-level
     * data copying during merge operations. Should not be called directly.
     *
     * @param homelessPopulationReport HomelessPopulationReport the source instance to copy from
     * @param n int the field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
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

    /**
     * Copies multiple field values from another HomelessPopulationReport instance.
     * This method is part of the OpenJPA PersistenceCapable interface and handles bulk field
     * copying during merge operations. Should not be called directly.
     *
     * @param o Object the source instance to copy from (must be HomelessPopulationReport)
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source has a different state manager
     * @throws IllegalStateException if the state manager is null
     */
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

    /**
     * Retrieves the generic context associated with the state manager.
     * This method is part of the OpenJPA PersistenceCapable interface. Should not be called directly.
     *
     * @return Object the generic context, or null if no state manager is present
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the object ID for this entity instance.
     * This method is part of the OpenJPA PersistenceCapable interface. Should not be called directly.
     *
     * @return Object the object ID, or null if no state manager is present
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks whether this entity instance is marked for deletion.
     * This method is part of the OpenJPA PersistenceCapable interface.
     *
     * @return boolean true if the instance is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks whether this entity instance has uncommitted changes.
     * This method is part of the OpenJPA PersistenceCapable interface and performs
     * a dirty check through the RedefinitionHelper.
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
     * Checks whether this entity instance is newly created and not yet persisted.
     * This method is part of the OpenJPA PersistenceCapable interface.
     *
     * @return boolean true if the instance is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks whether this entity instance is managed by a persistence context.
     * This method is part of the OpenJPA PersistenceCapable interface.
     *
     * @return boolean true if the instance is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks whether this entity instance is part of an active transaction.
     * This method is part of the OpenJPA PersistenceCapable interface.
     *
     * @return boolean true if the instance is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks whether this entity instance is currently being serialized.
     * This method is part of the OpenJPA PersistenceCapable interface.
     *
     * @return boolean true if the instance is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a specific field as dirty to trigger change tracking.
     * This method is part of the OpenJPA PersistenceCapable interface and notifies
     * the state manager of field modifications. Should not be called directly.
     *
     * @param s String the field name that has been modified
     */
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }

    /**
     * Retrieves the OpenJPA state manager for this entity instance.
     * This method is part of the OpenJPA PersistenceCapable interface. Should not be called directly.
     *
     * @return StateManager the state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version information for optimistic locking.
     * This method is part of the OpenJPA PersistenceCapable interface and supports
     * optimistic concurrency control. Should not be called directly.
     *
     * @return Object the version information, or null if no state manager is present
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces the current state manager with a new one.
     * This method is part of the OpenJPA PersistenceCapable interface and handles
     * state manager transitions during entity lifecycle changes. Should not be called directly.
     *
     * @param pcStateManager StateManager the new state manager to use
     * @throws SecurityException if the state manager replacement is not allowed
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
     * This method is not supported for this entity type and always throws InternalException.
     *
     * @param fieldSupplier FieldSupplier the field supplier for copying
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields to an object ID.
     * This method is not supported for this entity type and always throws InternalException.
     *
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields from an object ID using a field consumer.
     * This method is part of the OpenJPA PersistenceCapable interface and extracts
     * the ID from an IntId object. Should not be called directly.
     *
     * @param fieldConsumer FieldConsumer the field consumer for storing the ID
     * @param o Object the source object ID (must be IntId)
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(13 + HomelessPopulationReport.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }

    /**
     * Copies key fields from an object ID to this instance.
     * This method is part of the OpenJPA PersistenceCapable interface and sets
     * the entity ID from an IntId object. Should not be called directly.
     *
     * @param o Object the source object ID (must be IntId)
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Integer.valueOf(((IntId)o).getId());
    }

    /**
     * Creates a new object ID instance from a string representation.
     * This method is part of the OpenJPA PersistenceCapable interface. Should not be called directly.
     *
     * @param o Object the string representation of the ID
     * @return Object a new IntId instance for this entity type
     */
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((HomelessPopulationReport.class$Lca$openosp$openo$caisi_integrator$dao$HomelessPopulationReport != null) ? HomelessPopulationReport.class$Lca$openosp$openo$caisi_integrator$dao$HomelessPopulationReport : (HomelessPopulationReport.class$Lca$openosp$openo$caisi_integrator$dao$HomelessPopulationReport = class$("ca.openosp.openo.caisi_integrator.dao.HomelessPopulationReport")), (String)o);
    }

    /**
     * Creates a new object ID instance using the current entity's ID.
     * This method is part of the OpenJPA PersistenceCapable interface. Should not be called directly.
     *
     * @return Object a new IntId instance containing this entity's ID
     */
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

    /**
     * Checks whether this entity instance is in a detached state.
     * A detached entity is one that was previously managed but is no longer associated
     * with a persistence context. This method is part of the OpenJPA PersistenceCapable interface.
     *
     * @return Boolean TRUE if detached, FALSE if not detached, null if state is indeterminate
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

    /**
     * Checks whether the detached state is definitive for this entity.
     * This method is part of the OpenJPA PersistenceCapable interface. Should not be called directly.
     *
     * @return boolean always returns false for this entity type
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Retrieves the detached state object for this entity.
     * This method is part of the OpenJPA PersistenceCapable interface and provides
     * state information when the entity is detached from the persistence context.
     *
     * @return Object the detached state, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object for this entity.
     * This method is part of the OpenJPA PersistenceCapable interface and is called
     * during serialization and detachment operations. Should not be called directly.
     *
     * @param pcDetachedState Object the detached state to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method invoked during object serialization.
     * This method handles proper serialization of the entity state, ensuring that
     * detached state information is cleared during the serialization process to prevent
     * state corruption when the object is deserialized in a different context.
     *
     * @param objectOutputStream ObjectOutputStream the stream to write the object to
     * @throws IOException if an I/O error occurs during serialization
     */
    private void writeObject(final ObjectOutputStream objectOutputStream) throws IOException {
        final boolean pcSerializing = this.pcSerializing();
        objectOutputStream.defaultWriteObject();
        if (pcSerializing) {
            this.pcSetDetachedState(null);
        }
    }

    /**
     * Custom deserialization method invoked during object deserialization.
     * This method handles proper restoration of the entity state, marking the entity
     * as DESERIALIZED to indicate it has been reconstituted from a serialized form
     * and may need reattachment to a persistence context.
     *
     * @param objectInputStream ObjectInputStream the stream to read the object from
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if the class of a serialized object cannot be found
     */
    private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        this.pcSetDetachedState(PersistenceCapable.DESERIALIZED);
        objectInputStream.defaultReadObject();
    }
}
