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
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * Cached demographic drug entity for storing patient medication records in the CAISI integrator system.
 *
 * This JPA entity represents a cached copy of a patient's prescribed medication (drug) record, designed for
 * use within the CAISI (Client Access to Integrated Services and Information) integrator framework. The class
 * stores comprehensive prescription details including medication names, dosages, administration instructions,
 * dispensing information, and prescription metadata.
 *
 * The entity is enhanced by Apache OpenJPA for transparent persistence and supports integration across multiple
 * healthcare facilities through the {@link FacilityIdIntegerCompositePk} composite primary key. This allows
 * the same medication data to be synchronized and accessed across different healthcare sites while maintaining
 * data integrity and traceability.
 *
 * Key features:
 * <ul>
 * <li>Comprehensive medication tracking including brand name, generic name, and ATC (Anatomical Therapeutic Chemical) codes</li>
 * <li>Detailed dosing information with min/max dosage ranges and administration frequencies</li>
 * <li>Prescription metadata including prescriber ID, prescription date, duration, and refill tracking</li>
 * <li>Support for custom instructions, special notes, and PRN (pro re nata - as needed) medications</li>
 * <li>Archival capabilities with reason tracking and timestamps</li>
 * <li>Regional identifier support for provincial/jurisdictional medication tracking</li>
 * <li>Patient compliance and medication history indicators</li>
 * </ul>
 *
 * This class is automatically enhanced by OpenJPA's bytecode enhancement process, which adds the extensive
 * persistence capability methods (pcGet*, pcSet*, pcProvide*, pcReplace*, etc.) visible in this file. These
 * methods should not be called directly by application code; instead, use the standard getter and setter methods.
 *
 * Healthcare Context:
 * This entity supports Canadian healthcare workflows including medication reconciliation, prescription history
 * tracking, and cross-facility drug profile synchronization. The ATC code field enables standardized medication
 * classification following WHO guidelines, while regional identifiers support province-specific drug identification
 * numbers (DINs) used in Canadian formularies.
 *
 * @see AbstractModel
 * @see FacilityIdIntegerCompositePk
 * @see PersistenceCapable
 * @since 2026-01-24
 */
@Entity
public class CachedDemographicDrug extends AbstractModel<FacilityIdIntegerCompositePk> implements PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    @Column(nullable = false, length = 16)
    private String caisiProviderId;
    @Column(nullable = false)
    @Index
    private Integer caisiDemographicId;
    @Temporal(TemporalType.DATE)
    private Date rxDate;
    @Temporal(TemporalType.DATE)
    private Date endDate;
    @Column(length = 255)
    private String brandName;
    @Column(length = 255)
    private String customName;
    private float takeMin;
    private float takeMax;
    @Column(length = 64)
    private String freqCode;
    @Column(length = 64)
    private String duration;
    @Column(length = 64)
    private String durUnit;
    @Column(length = 64)
    private String quantity;
    private int repeats;
    @Temporal(TemporalType.DATE)
    private Date lastRefillDate;
    @Column(columnDefinition = "tinyint(1)")
    private boolean noSubs;
    @Column(columnDefinition = "tinyint(1)")
    private boolean prn;
    @Column(columnDefinition = "text")
    private String special;
    @Column(columnDefinition = "tinyint(1)")
    private boolean archived;
    @Column(length = 100)
    private String archivedReason;
    @Temporal(TemporalType.TIMESTAMP)
    private Date archivedDate;
    @Column(length = 255)
    private String genericName;
    @Column(length = 64)
    private String atc;
    private int scriptNo;
    @Column(length = 64)
    private String regionalIdentifier;
    @Column(length = 64)
    private String unit;
    @Column(length = 64)
    private String method;
    @Column(length = 64)
    private String route;
    @Column(length = 64)
    private String drugForm;
    @Temporal(TemporalType.TIMESTAMP)
    private Date createDate;
    @Column(columnDefinition = "text")
    private String dosage;
    @Column(columnDefinition = "tinyint(1)")
    private boolean customInstructions;
    @Column(length = 50)
    private String unitName;
    @Column(columnDefinition = "tinyint(1)")
    private Boolean longTerm;
    @Column(columnDefinition = "tinyint(1)")
    private Boolean pastMed;
    @Column(columnDefinition = "tinyint(1)")
    private Boolean patientCompliance;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Ljava$lang$Boolean;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDrug;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor that initializes a new CachedDemographicDrug instance with default values.
     *
     * All object-type fields are initialized to null, numeric fields to 0, and float fields to 0.0f.
     * This constructor is required by JPA and is called during entity instantiation by the persistence framework.
     * The OpenJPA StateManager will populate field values when loading entities from the database.
     */
    public CachedDemographicDrug() {
        this.caisiProviderId = null;
        this.caisiDemographicId = null;
        this.rxDate = null;
        this.endDate = null;
        this.brandName = null;
        this.customName = null;
        this.takeMin = 0.0f;
        this.takeMax = 0.0f;
        this.freqCode = null;
        this.duration = null;
        this.durUnit = null;
        this.quantity = null;
        this.repeats = 0;
        this.lastRefillDate = null;
        this.special = null;
        this.genericName = null;
        this.atc = null;
        this.scriptNo = 0;
        this.regionalIdentifier = null;
        this.unit = null;
        this.method = null;
        this.route = null;
        this.drugForm = null;
        this.createDate = null;
        this.dosage = null;
        this.unitName = null;
    }

    /**
     * Retrieves the composite primary key identifier for this cached demographic drug entity.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key containing facility ID and integer ID
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityIdIntegerCompositePk(this);
    }

    /**
     * Gets the embedded composite primary key for this entity.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key containing facility ID and drug record ID
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityIdIntegerCompositePk(this);
    }

    /**
     * Sets the composite primary key for this entity.
     *
     * @param facilityIdIntegerCompositePk FacilityIdIntegerCompositePk the composite primary key to assign
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        pcSetfacilityIdIntegerCompositePk(this, facilityIdIntegerCompositePk);
    }

    /**
     * Gets the CAISI provider identifier for the healthcare provider who prescribed this medication.
     *
     * @return String the provider ID who created the prescription, maximum 16 characters, or null if not set
     */
    public String getCaisiProviderId() {
        return pcGetcaisiProviderId(this);
    }

    /**
     * Sets the CAISI provider identifier for the prescribing healthcare provider.
     *
     * @param caisiProviderId String the provider ID, maximum 16 characters, cannot be null in database
     */
    public void setCaisiProviderId(final String caisiProviderId) {
        pcSetcaisiProviderId(this, caisiProviderId);
    }

    /**
     * Gets the CAISI demographic identifier for the patient this medication is prescribed to.
     *
     * @return Integer the patient's demographic ID, indexed for performance, or null if not set
     */
    public Integer getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }

    /**
     * Sets the CAISI demographic identifier for the patient.
     *
     * @param caisiDemographicId Integer the patient's demographic ID, cannot be null in database
     */
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }

    /**
     * Gets the prescription date when this medication was prescribed.
     *
     * @return Date the prescription date (date only, no time component), or null if not set
     */
    public Date getRxDate() {
        return pcGetrxDate(this);
    }

    /**
     * Sets the prescription date for this medication.
     *
     * @param rxDate Date the prescription date (stored as DATE type in database)
     */
    public void setRxDate(final Date rxDate) {
        pcSetrxDate(this, rxDate);
    }

    /**
     * Gets the end date when this medication prescription expires or should be discontinued.
     *
     * @return Date the end date (date only, no time component), or null if not set
     */
    public Date getEndDate() {
        return pcGetendDate(this);
    }

    /**
     * Sets the end date for this medication prescription.
     *
     * @param endDate Date the end date (stored as DATE type in database)
     */
    public void setEndDate(final Date endDate) {
        pcSetendDate(this, endDate);
    }

    /**
     * Gets the brand name (trade name) of the medication.
     *
     * @return String the medication's brand name, maximum 255 characters, or null if not set
     */
    public String getBrandName() {
        return pcGetbrandName(this);
    }

    /**
     * Sets the brand name (trade name) of the medication.
     *
     * @param brandName String the medication's brand name, maximum 255 characters
     */
    public void setBrandName(final String brandName) {
        pcSetbrandName(this, brandName);
    }

    /**
     * Gets the custom medication name entered by the prescriber.
     *
     * @return String the custom medication name, maximum 255 characters, or null if not set
     */
    public String getCustomName() {
        return pcGetcustomName(this);
    }

    /**
     * Sets the custom medication name.
     *
     * @param customName String the custom medication name, maximum 255 characters
     */
    public void setCustomName(final String customName) {
        pcSetcustomName(this, customName);
    }

    /**
     * Gets the minimum dosage amount to take per administration.
     *
     * @return float the minimum dosage quantity, or 0.0f if not set
     */
    public float getTakeMin() {
        return pcGettakeMin(this);
    }

    /**
     * Sets the minimum dosage amount to take per administration.
     *
     * @param takeMin float the minimum dosage quantity
     */
    public void setTakeMin(final float takeMin) {
        pcSettakeMin(this, takeMin);
    }

    /**
     * Gets the maximum dosage amount to take per administration.
     *
     * @return float the maximum dosage quantity, or 0.0f if not set
     */
    public float getTakeMax() {
        return pcGettakeMax(this);
    }

    /**
     * Sets the maximum dosage amount to take per administration.
     *
     * @param takeMax float the maximum dosage quantity
     */
    public void setTakeMax(final float takeMax) {
        pcSettakeMax(this, takeMax);
    }

    /**
     * Gets the frequency code indicating how often the medication should be taken.
     *
     * @return String the frequency code (e.g., "BID", "TID", "QID"), maximum 64 characters, or null if not set
     */
    public String getFreqCode() {
        return pcGetfreqCode(this);
    }

    /**
     * Sets the frequency code for medication administration.
     *
     * @param freqCode String the frequency code, maximum 64 characters
     */
    public void setFreqCode(final String freqCode) {
        pcSetfreqCode(this, freqCode);
    }

    /**
     * Gets the prescription duration value.
     *
     * @return String the duration value (numeric component), maximum 64 characters, or null if not set
     */
    public String getDuration() {
        return pcGetduration(this);
    }

    /**
     * Sets the prescription duration value.
     *
     * @param duration String the duration value, maximum 64 characters
     */
    public void setDuration(final String duration) {
        pcSetduration(this, duration);
    }

    /**
     * Gets the duration unit for the prescription (e.g., "days", "weeks", "months").
     *
     * @return String the duration unit, maximum 64 characters, or null if not set
     */
    public String getDurUnit() {
        return pcGetdurUnit(this);
    }

    /**
     * Sets the duration unit for the prescription.
     *
     * @param durUnit String the duration unit, maximum 64 characters
     */
    public void setDurUnit(final String durUnit) {
        pcSetdurUnit(this, durUnit);
    }

    /**
     * Gets the quantity of medication prescribed or dispensed.
     *
     * @return String the quantity (may include units), maximum 64 characters, or null if not set
     */
    public String getQuantity() {
        return pcGetquantity(this);
    }

    /**
     * Sets the quantity of medication prescribed or dispensed.
     *
     * @param quantity String the quantity, maximum 64 characters
     */
    public void setQuantity(final String quantity) {
        pcSetquantity(this, quantity);
    }

    /**
     * Gets the number of prescription repeats (refills) allowed.
     *
     * @return int the number of allowed repeats, or 0 if not set
     */
    public int getRepeats() {
        return pcGetrepeats(this);
    }

    /**
     * Sets the number of prescription repeats (refills) allowed.
     *
     * @param repeats int the number of allowed repeats
     */
    public void setRepeats(final int repeats) {
        pcSetrepeats(this, repeats);
    }

    /**
     * Gets the date of the last prescription refill.
     *
     * @return Date the last refill date (date only, no time component), or null if never refilled
     */
    public Date getLastRefillDate() {
        return pcGetlastRefillDate(this);
    }

    /**
     * Sets the date of the last prescription refill.
     *
     * @param lastRefillDate Date the last refill date (stored as DATE type in database)
     */
    public void setLastRefillDate(final Date lastRefillDate) {
        pcSetlastRefillDate(this, lastRefillDate);
    }

    /**
     * Checks if substitutions are not allowed for this medication.
     *
     * @return boolean true if medication substitutions are prohibited (no generics allowed), false otherwise
     */
    public boolean isNoSubs() {
        return pcGetnoSubs(this);
    }

    /**
     * Sets whether substitutions are allowed for this medication.
     *
     * @param noSubs boolean true to prohibit substitutions, false to allow them
     */
    public void setNoSubs(final boolean noSubs) {
        pcSetnoSubs(this, noSubs);
    }

    /**
     * Checks if this medication is to be taken PRN (pro re nata - as needed).
     *
     * @return boolean true if medication is taken as needed rather than on a regular schedule, false otherwise
     */
    public boolean isPrn() {
        return pcGetprn(this);
    }

    /**
     * Sets whether this medication is to be taken PRN (as needed).
     *
     * @param prn boolean true if medication should be taken as needed, false for scheduled dosing
     */
    public void setPrn(final boolean prn) {
        pcSetprn(this, prn);
    }

    /**
     * Gets special instructions or notes for this medication.
     *
     * @return String special instructions (stored as TEXT in database), or null if not set
     */
    public String getSpecial() {
        return pcGetspecial(this);
    }

    /**
     * Sets special instructions or notes for this medication.
     *
     * @param special String special instructions (unlimited length as TEXT type)
     */
    public void setSpecial(final String special) {
        pcSetspecial(this, special);
    }

    /**
     * Checks if this medication record has been archived.
     *
     * @return boolean true if the medication record is archived (discontinued or inactive), false if active
     */
    public boolean isArchived() {
        return pcGetarchived(this);
    }

    /**
     * Sets whether this medication record is archived.
     *
     * @param archived boolean true to archive the medication record, false to keep it active
     */
    public void setArchived(final boolean archived) {
        pcSetarchived(this, archived);
    }

    /**
     * Gets the reason why this medication was archived.
     *
     * @return String the archival reason (e.g., "Discontinued by patient", "Drug interaction"), maximum 100 characters, or null if not archived or reason not specified
     */
    public String getArchivedReason() {
        return pcGetarchivedReason(this);
    }

    /**
     * Sets the reason why this medication is being archived.
     *
     * @param archivedReason String the archival reason, maximum 100 characters
     */
    public void setArchivedReason(final String archivedReason) {
        pcSetarchivedReason(this, archivedReason);
    }

    /**
     * Gets the date and time when this medication was archived.
     *
     * @return Date the archival timestamp (includes date and time), or null if not archived
     */
    public Date getArchivedDate() {
        return pcGetarchivedDate(this);
    }

    /**
     * Sets the date and time when this medication is being archived.
     *
     * @param archivedDate Date the archival timestamp (stored as TIMESTAMP type in database)
     */
    public void setArchivedDate(final Date archivedDate) {
        pcSetarchivedDate(this, archivedDate);
    }

    /**
     * Gets the generic (non-proprietary) name of the medication.
     *
     * @return String the medication's generic name, maximum 255 characters, or null if not set
     */
    public String getGenericName() {
        return pcGetgenericName(this);
    }

    /**
     * Sets the generic (non-proprietary) name of the medication.
     *
     * @param genericName String the medication's generic name, maximum 255 characters
     */
    public void setGenericName(final String genericName) {
        pcSetgenericName(this, genericName);
    }

    /**
     * Gets the ATC (Anatomical Therapeutic Chemical) classification code for this medication.
     *
     * The ATC code is a WHO standard for classifying medications based on the organ or system
     * they act upon and their therapeutic, pharmacological, and chemical properties.
     *
     * @return String the ATC code, maximum 64 characters, or null if not set
     */
    public String getAtc() {
        return pcGetatc(this);
    }

    /**
     * Sets the ATC (Anatomical Therapeutic Chemical) classification code.
     *
     * @param atc String the ATC code following WHO classification standards, maximum 64 characters
     */
    public void setAtc(final String atc) {
        pcSetatc(this, atc);
    }

    /**
     * Gets the prescription script number for this medication.
     *
     * @return int the script number (prescription identifier), or 0 if not set
     */
    public int getScriptNo() {
        return pcGetscriptNo(this);
    }

    /**
     * Sets the prescription script number.
     *
     * @param scriptNo int the script number (prescription identifier)
     */
    public void setScriptNo(final int scriptNo) {
        pcSetscriptNo(this, scriptNo);
    }

    /**
     * Gets the regional identifier for this medication.
     *
     * In Canadian context, this typically stores the Drug Identification Number (DIN) or
     * other provincial/jurisdictional medication identifiers.
     *
     * @return String the regional medication identifier, maximum 64 characters, or null if not set
     */
    public String getRegionalIdentifier() {
        return pcGetregionalIdentifier(this);
    }

    /**
     * Sets the regional identifier for this medication.
     *
     * @param regionalIdentifier String the regional medication identifier (e.g., DIN), maximum 64 characters
     */
    public void setRegionalIdentifier(final String regionalIdentifier) {
        pcSetregionalIdentifier(this, regionalIdentifier);
    }

    /**
     * Gets the unit of measurement for the medication dosage.
     *
     * @return String the dosage unit (e.g., "mg", "mL", "tablets"), maximum 64 characters, or null if not set
     */
    public String getUnit() {
        return pcGetunit(this);
    }

    /**
     * Sets the unit of measurement for the medication dosage.
     *
     * @param unit String the dosage unit, maximum 64 characters
     */
    public void setUnit(final String unit) {
        pcSetunit(this, unit);
    }

    /**
     * Gets the administration method for the medication.
     *
     * @return String the administration method, maximum 64 characters, or null if not set
     */
    public String getMethod() {
        return pcGetmethod(this);
    }

    /**
     * Sets the administration method for the medication.
     *
     * @param method String the administration method, maximum 64 characters
     */
    public void setMethod(final String method) {
        pcSetmethod(this, method);
    }

    /**
     * Gets the route of administration for the medication.
     *
     * @return String the administration route (e.g., "PO" for oral, "IV" for intravenous, "IM" for intramuscular), maximum 64 characters, or null if not set
     */
    public String getRoute() {
        return pcGetroute(this);
    }

    /**
     * Sets the route of administration for the medication.
     *
     * @param route String the administration route, maximum 64 characters
     */
    public void setRoute(final String route) {
        pcSetroute(this, route);
    }

    /**
     * Gets the pharmaceutical form of the medication.
     *
     * @return String the drug form (e.g., "tablet", "capsule", "solution", "cream"), maximum 64 characters, or null if not set
     */
    public String getDrugForm() {
        return pcGetdrugForm(this);
    }

    /**
     * Sets the pharmaceutical form of the medication.
     *
     * @param drugForm String the drug form, maximum 64 characters
     */
    public void setDrugForm(final String drugForm) {
        pcSetdrugForm(this, drugForm);
    }

    /**
     * Gets the date and time when this medication record was created in the system.
     *
     * @return Date the creation timestamp (includes date and time), or null if not set
     */
    public Date getCreateDate() {
        return pcGetcreateDate(this);
    }

    /**
     * Sets the date and time when this medication record was created.
     *
     * @param createDate Date the creation timestamp (stored as TIMESTAMP type in database)
     */
    public void setCreateDate(final Date createDate) {
        pcSetcreateDate(this, createDate);
    }

    /**
     * Gets the dosage instructions as text.
     *
     * @return String the dosage instructions (stored as TEXT in database), or null if not set
     */
    public String getDosage() {
        return pcGetdosage(this);
    }

    /**
     * Sets the dosage instructions as text.
     *
     * @param dosage String the dosage instructions (unlimited length as TEXT type)
     */
    public void setDosage(final String dosage) {
        pcSetdosage(this, dosage);
    }

    /**
     * Checks if this prescription uses custom (free-text) instructions.
     *
     * @return boolean true if custom instructions are used instead of structured dosing, false otherwise
     */
    public boolean isCustomInstructions() {
        return pcGetcustomInstructions(this);
    }

    /**
     * Sets whether this prescription uses custom instructions.
     *
     * @param customInstructions boolean true to indicate custom instructions are used, false for structured dosing
     */
    public void setCustomInstructions(final boolean customInstructions) {
        pcSetcustomInstructions(this, customInstructions);
    }

    /**
     * Gets the descriptive name for the dosage unit.
     *
     * @return String the unit name (e.g., "milligrams", "milliliters"), maximum 50 characters, or null if not set
     */
    public String getUnitName() {
        return pcGetunitName(this);
    }

    /**
     * Sets the descriptive name for the dosage unit.
     *
     * @param unitName String the unit name, maximum 50 characters
     */
    public void setUnitName(final String unitName) {
        pcSetunitName(this, unitName);
    }

    /**
     * Checks if this medication is intended for long-term use.
     *
     * @return Boolean true if medication is for long-term use, false for short-term, null if not specified
     */
    public Boolean getLongTerm() {
        return pcGetlongTerm(this);
    }

    /**
     * Sets whether this medication is for long-term use.
     *
     * @param longTerm Boolean true for long-term medication, false for short-term, null if not applicable
     */
    public void setLongTerm(final Boolean longTerm) {
        pcSetlongTerm(this, longTerm);
    }

    /**
     * Checks if this is a past medication no longer being taken.
     *
     * @return Boolean true if this is a discontinued past medication, false if current, null if not specified
     */
    public Boolean getPastMed() {
        return pcGetpastMed(this);
    }

    /**
     * Sets whether this is a past medication.
     *
     * @param pastMed Boolean true if medication is discontinued, false if current, null if not applicable
     */
    public void setPastMed(final Boolean pastMed) {
        pcSetpastMed(this, pastMed);
    }

    /**
     * Gets the patient compliance indicator for this medication.
     *
     * Tracks whether the patient is adhering to the prescribed medication regimen.
     *
     * @return Boolean true if patient is compliant, false if non-compliant, null if not assessed
     */
    public Boolean getPatientCompliance() {
        return pcGetpatientCompliance(this);
    }

    /**
     * Sets the patient compliance indicator.
     *
     * @param patientCompliance Boolean true if patient is compliant with medication, false if non-compliant, null if not assessed
     */
    public void setPatientCompliance(final Boolean patientCompliance) {
        pcSetpatientCompliance(this, patientCompliance);
    }

    /**
     * Gets the OpenJPA enhancement contract version for this persistence-capable class.
     *
     * This method is part of the OpenJPA bytecode enhancement infrastructure and should not be called
     * directly by application code. It indicates the version of the enhancement contract implemented
     * by this enhanced class.
     *
     * @return int the enhancement contract version (2 for this implementation)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -1659277755766988859L;
        CachedDemographicDrug.pcFieldNames = new String[] { "archived", "archivedDate", "archivedReason", "atc", "brandName", "caisiDemographicId", "caisiProviderId", "createDate", "customInstructions", "customName", "dosage", "drugForm", "durUnit", "duration", "endDate", "facilityIdIntegerCompositePk", "freqCode", "genericName", "lastRefillDate", "longTerm", "method", "noSubs", "pastMed", "patientCompliance", "prn", "quantity", "regionalIdentifier", "repeats", "route", "rxDate", "scriptNo", "special", "takeMax", "takeMin", "unit", "unitName" };
        CachedDemographicDrug.pcFieldTypes = new Class[] { Boolean.TYPE, (CachedDemographicDrug.class$Ljava$util$Date != null) ? CachedDemographicDrug.class$Ljava$util$Date : (CachedDemographicDrug.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDrug.class$Ljava$lang$Integer != null) ? CachedDemographicDrug.class$Ljava$lang$Integer : (CachedDemographicDrug.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDrug.class$Ljava$util$Date != null) ? CachedDemographicDrug.class$Ljava$util$Date : (CachedDemographicDrug.class$Ljava$util$Date = class$("java.util.Date")), Boolean.TYPE, (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDrug.class$Ljava$util$Date != null) ? CachedDemographicDrug.class$Ljava$util$Date : (CachedDemographicDrug.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographicDrug.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedDemographicDrug.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedDemographicDrug.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDrug.class$Ljava$util$Date != null) ? CachedDemographicDrug.class$Ljava$util$Date : (CachedDemographicDrug.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographicDrug.class$Ljava$lang$Boolean != null) ? CachedDemographicDrug.class$Ljava$lang$Boolean : (CachedDemographicDrug.class$Ljava$lang$Boolean = class$("java.lang.Boolean")), (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), Boolean.TYPE, (CachedDemographicDrug.class$Ljava$lang$Boolean != null) ? CachedDemographicDrug.class$Ljava$lang$Boolean : (CachedDemographicDrug.class$Ljava$lang$Boolean = class$("java.lang.Boolean")), (CachedDemographicDrug.class$Ljava$lang$Boolean != null) ? CachedDemographicDrug.class$Ljava$lang$Boolean : (CachedDemographicDrug.class$Ljava$lang$Boolean = class$("java.lang.Boolean")), Boolean.TYPE, (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), Integer.TYPE, (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDrug.class$Ljava$util$Date != null) ? CachedDemographicDrug.class$Ljava$util$Date : (CachedDemographicDrug.class$Ljava$util$Date = class$("java.util.Date")), Integer.TYPE, (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), Float.TYPE, Float.TYPE, (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDrug.class$Ljava$lang$String != null) ? CachedDemographicDrug.class$Ljava$lang$String : (CachedDemographicDrug.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedDemographicDrug.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographicDrug.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDrug != null) ? CachedDemographicDrug.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDrug : (CachedDemographicDrug.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDrug = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicDrug")), CachedDemographicDrug.pcFieldNames, CachedDemographicDrug.pcFieldTypes, CachedDemographicDrug.pcFieldFlags, CachedDemographicDrug.pcPCSuperclass, "CachedDemographicDrug", (PersistenceCapable)new CachedDemographicDrug());
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
        this.archived = false;
        this.archivedDate = null;
        this.archivedReason = null;
        this.atc = null;
        this.brandName = null;
        this.caisiDemographicId = null;
        this.caisiProviderId = null;
        this.createDate = null;
        this.customInstructions = false;
        this.customName = null;
        this.dosage = null;
        this.drugForm = null;
        this.durUnit = null;
        this.duration = null;
        this.endDate = null;
        this.facilityIdIntegerCompositePk = null;
        this.freqCode = null;
        this.genericName = null;
        this.lastRefillDate = null;
        this.longTerm = null;
        this.method = null;
        this.noSubs = false;
        this.pastMed = null;
        this.patientCompliance = null;
        this.prn = false;
        this.quantity = null;
        this.regionalIdentifier = null;
        this.repeats = 0;
        this.route = null;
        this.rxDate = null;
        this.scriptNo = 0;
        this.special = null;
        this.takeMax = 0.0f;
        this.takeMin = 0.0f;
        this.unit = null;
        this.unitName = null;
    }

    /**
     * Creates a new instance of CachedDemographicDrug with the specified StateManager and object ID.
     *
     * This method is part of the OpenJPA PersistenceCapable interface and is used internally by the
     * persistence framework to create new entity instances during object loading and relationship resolution.
     * Application code should not call this method directly.
     *
     * @param pcStateManager StateManager the OpenJPA state manager for the new instance
     * @param o Object the object ID to copy key fields from
     * @param b boolean if true, clears all non-key fields to default values
     * @return PersistenceCapable a new CachedDemographicDrug instance with the specified state manager
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicDrug cachedDemographicDrug = new CachedDemographicDrug();
        if (b) {
            cachedDemographicDrug.pcClearFields();
        }
        cachedDemographicDrug.pcStateManager = pcStateManager;
        cachedDemographicDrug.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicDrug;
    }

    /**
     * Creates a new instance of CachedDemographicDrug with the specified StateManager.
     *
     * This method is part of the OpenJPA PersistenceCapable interface and is used internally by the
     * persistence framework to create new entity instances. Application code should not call this method directly.
     *
     * @param pcStateManager StateManager the OpenJPA state manager for the new instance
     * @param b boolean if true, clears all fields to default values
     * @return PersistenceCapable a new CachedDemographicDrug instance with the specified state manager
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicDrug cachedDemographicDrug = new CachedDemographicDrug();
        if (b) {
            cachedDemographicDrug.pcClearFields();
        }
        cachedDemographicDrug.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicDrug;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 36;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicDrug.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.archived = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.archivedDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.archivedReason = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.atc = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.brandName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.caisiDemographicId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.caisiProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.createDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.customInstructions = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.customName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 10: {
                this.dosage = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 11: {
                this.drugForm = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 12: {
                this.durUnit = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 13: {
                this.duration = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 14: {
                this.endDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 15: {
                this.facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 16: {
                this.freqCode = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 17: {
                this.genericName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 18: {
                this.lastRefillDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 19: {
                this.longTerm = (Boolean)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 20: {
                this.method = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 21: {
                this.noSubs = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 22: {
                this.pastMed = (Boolean)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 23: {
                this.patientCompliance = (Boolean)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 24: {
                this.prn = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 25: {
                this.quantity = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 26: {
                this.regionalIdentifier = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 27: {
                this.repeats = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 28: {
                this.route = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 29: {
                this.rxDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 30: {
                this.scriptNo = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 31: {
                this.special = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 32: {
                this.takeMax = this.pcStateManager.replaceFloatField((PersistenceCapable)this, n);
                return;
            }
            case 33: {
                this.takeMin = this.pcStateManager.replaceFloatField((PersistenceCapable)this, n);
                return;
            }
            case 34: {
                this.unit = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 35: {
                this.unitName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedDemographicDrug.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.archived);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.archivedDate);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.archivedReason);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.atc);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.brandName);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiDemographicId);
                return;
            }
            case 6: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.caisiProviderId);
                return;
            }
            case 7: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.createDate);
                return;
            }
            case 8: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.customInstructions);
                return;
            }
            case 9: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.customName);
                return;
            }
            case 10: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.dosage);
                return;
            }
            case 11: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.drugForm);
                return;
            }
            case 12: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.durUnit);
                return;
            }
            case 13: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.duration);
                return;
            }
            case 14: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.endDate);
                return;
            }
            case 15: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityIdIntegerCompositePk);
                return;
            }
            case 16: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.freqCode);
                return;
            }
            case 17: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.genericName);
                return;
            }
            case 18: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.lastRefillDate);
                return;
            }
            case 19: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.longTerm);
                return;
            }
            case 20: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.method);
                return;
            }
            case 21: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.noSubs);
                return;
            }
            case 22: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.pastMed);
                return;
            }
            case 23: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.patientCompliance);
                return;
            }
            case 24: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.prn);
                return;
            }
            case 25: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.quantity);
                return;
            }
            case 26: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.regionalIdentifier);
                return;
            }
            case 27: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.repeats);
                return;
            }
            case 28: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.route);
                return;
            }
            case 29: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.rxDate);
                return;
            }
            case 30: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.scriptNo);
                return;
            }
            case 31: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.special);
                return;
            }
            case 32: {
                this.pcStateManager.providedFloatField((PersistenceCapable)this, n, this.takeMax);
                return;
            }
            case 33: {
                this.pcStateManager.providedFloatField((PersistenceCapable)this, n, this.takeMin);
                return;
            }
            case 34: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.unit);
                return;
            }
            case 35: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.unitName);
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
    
    protected void pcCopyField(final CachedDemographicDrug cachedDemographicDrug, final int n) {
        final int n2 = n - CachedDemographicDrug.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.archived = cachedDemographicDrug.archived;
                return;
            }
            case 1: {
                this.archivedDate = cachedDemographicDrug.archivedDate;
                return;
            }
            case 2: {
                this.archivedReason = cachedDemographicDrug.archivedReason;
                return;
            }
            case 3: {
                this.atc = cachedDemographicDrug.atc;
                return;
            }
            case 4: {
                this.brandName = cachedDemographicDrug.brandName;
                return;
            }
            case 5: {
                this.caisiDemographicId = cachedDemographicDrug.caisiDemographicId;
                return;
            }
            case 6: {
                this.caisiProviderId = cachedDemographicDrug.caisiProviderId;
                return;
            }
            case 7: {
                this.createDate = cachedDemographicDrug.createDate;
                return;
            }
            case 8: {
                this.customInstructions = cachedDemographicDrug.customInstructions;
                return;
            }
            case 9: {
                this.customName = cachedDemographicDrug.customName;
                return;
            }
            case 10: {
                this.dosage = cachedDemographicDrug.dosage;
                return;
            }
            case 11: {
                this.drugForm = cachedDemographicDrug.drugForm;
                return;
            }
            case 12: {
                this.durUnit = cachedDemographicDrug.durUnit;
                return;
            }
            case 13: {
                this.duration = cachedDemographicDrug.duration;
                return;
            }
            case 14: {
                this.endDate = cachedDemographicDrug.endDate;
                return;
            }
            case 15: {
                this.facilityIdIntegerCompositePk = cachedDemographicDrug.facilityIdIntegerCompositePk;
                return;
            }
            case 16: {
                this.freqCode = cachedDemographicDrug.freqCode;
                return;
            }
            case 17: {
                this.genericName = cachedDemographicDrug.genericName;
                return;
            }
            case 18: {
                this.lastRefillDate = cachedDemographicDrug.lastRefillDate;
                return;
            }
            case 19: {
                this.longTerm = cachedDemographicDrug.longTerm;
                return;
            }
            case 20: {
                this.method = cachedDemographicDrug.method;
                return;
            }
            case 21: {
                this.noSubs = cachedDemographicDrug.noSubs;
                return;
            }
            case 22: {
                this.pastMed = cachedDemographicDrug.pastMed;
                return;
            }
            case 23: {
                this.patientCompliance = cachedDemographicDrug.patientCompliance;
                return;
            }
            case 24: {
                this.prn = cachedDemographicDrug.prn;
                return;
            }
            case 25: {
                this.quantity = cachedDemographicDrug.quantity;
                return;
            }
            case 26: {
                this.regionalIdentifier = cachedDemographicDrug.regionalIdentifier;
                return;
            }
            case 27: {
                this.repeats = cachedDemographicDrug.repeats;
                return;
            }
            case 28: {
                this.route = cachedDemographicDrug.route;
                return;
            }
            case 29: {
                this.rxDate = cachedDemographicDrug.rxDate;
                return;
            }
            case 30: {
                this.scriptNo = cachedDemographicDrug.scriptNo;
                return;
            }
            case 31: {
                this.special = cachedDemographicDrug.special;
                return;
            }
            case 32: {
                this.takeMax = cachedDemographicDrug.takeMax;
                return;
            }
            case 33: {
                this.takeMin = cachedDemographicDrug.takeMin;
                return;
            }
            case 34: {
                this.unit = cachedDemographicDrug.unit;
                return;
            }
            case 35: {
                this.unitName = cachedDemographicDrug.unitName;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicDrug cachedDemographicDrug = (CachedDemographicDrug)o;
        if (cachedDemographicDrug.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicDrug, array[i]);
        }
    }
    
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the object ID for this entity instance.
     *
     * This method is part of the OpenJPA PersistenceCapable interface and retrieves the unique
     * identifier for this persistent object from the state manager.
     *
     * @return Object the object ID if this instance is managed by a state manager, null otherwise
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks if this entity instance has been deleted.
     *
     * @return boolean true if the entity is in deleted state within the current transaction, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity instance has been modified (dirty).
     *
     * This method determines whether any fields have been changed since the entity was loaded
     * or last synchronized with the database.
     *
     * @return boolean true if the entity has unsaved modifications, false otherwise
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
     * @return boolean true if the entity is new and has not been saved to the database, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity instance is persistent (managed by the persistence context).
     *
     * @return boolean true if the entity is being managed by the persistence framework, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity instance is transactional.
     *
     * @return boolean true if the entity is part of an active transaction, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity instance is currently being serialized.
     *
     * @return boolean true if the entity is in the process of serialization, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }
    
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }

    /**
     * Gets the OpenJPA state manager for this entity instance.
     *
     * @return StateManager the state manager managing this entity, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Gets the version object for this entity instance.
     *
     * The version is used for optimistic locking to detect concurrent modifications.
     *
     * @return Object the version object if managed, null otherwise
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
     * This method is part of the OpenJPA PersistenceCapable interface and is used internally
     * by the persistence framework to manage entity state. Application code should not call this method.
     *
     * @param pcStateManager StateManager the new state manager to assign
     * @throws SecurityException if the state manager replacement is not permitted
     */
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
        fieldConsumer.storeObjectField(15 + CachedDemographicDrug.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicDrug\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicDrug.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDrug != null) ? CachedDemographicDrug.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDrug : (CachedDemographicDrug.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDrug = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicDrug")), (Object)this.facilityIdIntegerCompositePk);
    }
    
    private static final boolean pcGetarchived(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.archived;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 0);
        return cachedDemographicDrug.archived;
    }
    
    private static final void pcSetarchived(final CachedDemographicDrug cachedDemographicDrug, final boolean archived) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.archived = archived;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingBooleanField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 0, cachedDemographicDrug.archived, archived, 0);
    }
    
    private static final Date pcGetarchivedDate(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.archivedDate;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 1);
        return cachedDemographicDrug.archivedDate;
    }
    
    private static final void pcSetarchivedDate(final CachedDemographicDrug cachedDemographicDrug, final Date archivedDate) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.archivedDate = archivedDate;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 1, (Object)cachedDemographicDrug.archivedDate, (Object)archivedDate, 0);
    }
    
    private static final String pcGetarchivedReason(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.archivedReason;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 2);
        return cachedDemographicDrug.archivedReason;
    }
    
    private static final void pcSetarchivedReason(final CachedDemographicDrug cachedDemographicDrug, final String archivedReason) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.archivedReason = archivedReason;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 2, cachedDemographicDrug.archivedReason, archivedReason, 0);
    }
    
    private static final String pcGetatc(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.atc;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 3);
        return cachedDemographicDrug.atc;
    }
    
    private static final void pcSetatc(final CachedDemographicDrug cachedDemographicDrug, final String atc) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.atc = atc;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 3, cachedDemographicDrug.atc, atc, 0);
    }
    
    private static final String pcGetbrandName(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.brandName;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 4);
        return cachedDemographicDrug.brandName;
    }
    
    private static final void pcSetbrandName(final CachedDemographicDrug cachedDemographicDrug, final String brandName) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.brandName = brandName;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 4, cachedDemographicDrug.brandName, brandName, 0);
    }
    
    private static final Integer pcGetcaisiDemographicId(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.caisiDemographicId;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 5);
        return cachedDemographicDrug.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedDemographicDrug cachedDemographicDrug, final Integer caisiDemographicId) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 5, (Object)cachedDemographicDrug.caisiDemographicId, (Object)caisiDemographicId, 0);
    }
    
    private static final String pcGetcaisiProviderId(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.caisiProviderId;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 6);
        return cachedDemographicDrug.caisiProviderId;
    }
    
    private static final void pcSetcaisiProviderId(final CachedDemographicDrug cachedDemographicDrug, final String caisiProviderId) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.caisiProviderId = caisiProviderId;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 6, cachedDemographicDrug.caisiProviderId, caisiProviderId, 0);
    }
    
    private static final Date pcGetcreateDate(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.createDate;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 7);
        return cachedDemographicDrug.createDate;
    }
    
    private static final void pcSetcreateDate(final CachedDemographicDrug cachedDemographicDrug, final Date createDate) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.createDate = createDate;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 7, (Object)cachedDemographicDrug.createDate, (Object)createDate, 0);
    }
    
    private static final boolean pcGetcustomInstructions(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.customInstructions;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 8);
        return cachedDemographicDrug.customInstructions;
    }
    
    private static final void pcSetcustomInstructions(final CachedDemographicDrug cachedDemographicDrug, final boolean customInstructions) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.customInstructions = customInstructions;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingBooleanField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 8, cachedDemographicDrug.customInstructions, customInstructions, 0);
    }
    
    private static final String pcGetcustomName(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.customName;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 9);
        return cachedDemographicDrug.customName;
    }
    
    private static final void pcSetcustomName(final CachedDemographicDrug cachedDemographicDrug, final String customName) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.customName = customName;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 9, cachedDemographicDrug.customName, customName, 0);
    }
    
    private static final String pcGetdosage(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.dosage;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 10);
        return cachedDemographicDrug.dosage;
    }
    
    private static final void pcSetdosage(final CachedDemographicDrug cachedDemographicDrug, final String dosage) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.dosage = dosage;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 10, cachedDemographicDrug.dosage, dosage, 0);
    }
    
    private static final String pcGetdrugForm(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.drugForm;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 11);
        return cachedDemographicDrug.drugForm;
    }
    
    private static final void pcSetdrugForm(final CachedDemographicDrug cachedDemographicDrug, final String drugForm) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.drugForm = drugForm;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 11, cachedDemographicDrug.drugForm, drugForm, 0);
    }
    
    private static final String pcGetdurUnit(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.durUnit;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 12);
        return cachedDemographicDrug.durUnit;
    }
    
    private static final void pcSetdurUnit(final CachedDemographicDrug cachedDemographicDrug, final String durUnit) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.durUnit = durUnit;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 12, cachedDemographicDrug.durUnit, durUnit, 0);
    }
    
    private static final String pcGetduration(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.duration;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 13);
        return cachedDemographicDrug.duration;
    }
    
    private static final void pcSetduration(final CachedDemographicDrug cachedDemographicDrug, final String duration) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.duration = duration;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 13, cachedDemographicDrug.duration, duration, 0);
    }
    
    private static final Date pcGetendDate(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.endDate;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 14);
        return cachedDemographicDrug.endDate;
    }
    
    private static final void pcSetendDate(final CachedDemographicDrug cachedDemographicDrug, final Date endDate) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.endDate = endDate;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 14, (Object)cachedDemographicDrug.endDate, (Object)endDate, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityIdIntegerCompositePk(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.facilityIdIntegerCompositePk;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 15);
        return cachedDemographicDrug.facilityIdIntegerCompositePk;
    }
    
    private static final void pcSetfacilityIdIntegerCompositePk(final CachedDemographicDrug cachedDemographicDrug, final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 15, (Object)cachedDemographicDrug.facilityIdIntegerCompositePk, (Object)facilityIdIntegerCompositePk, 0);
    }
    
    private static final String pcGetfreqCode(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.freqCode;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 16);
        return cachedDemographicDrug.freqCode;
    }
    
    private static final void pcSetfreqCode(final CachedDemographicDrug cachedDemographicDrug, final String freqCode) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.freqCode = freqCode;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 16, cachedDemographicDrug.freqCode, freqCode, 0);
    }
    
    private static final String pcGetgenericName(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.genericName;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 17);
        return cachedDemographicDrug.genericName;
    }
    
    private static final void pcSetgenericName(final CachedDemographicDrug cachedDemographicDrug, final String genericName) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.genericName = genericName;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 17, cachedDemographicDrug.genericName, genericName, 0);
    }
    
    private static final Date pcGetlastRefillDate(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.lastRefillDate;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 18);
        return cachedDemographicDrug.lastRefillDate;
    }
    
    private static final void pcSetlastRefillDate(final CachedDemographicDrug cachedDemographicDrug, final Date lastRefillDate) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.lastRefillDate = lastRefillDate;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 18, (Object)cachedDemographicDrug.lastRefillDate, (Object)lastRefillDate, 0);
    }
    
    private static final Boolean pcGetlongTerm(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.longTerm;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 19);
        return cachedDemographicDrug.longTerm;
    }
    
    private static final void pcSetlongTerm(final CachedDemographicDrug cachedDemographicDrug, final Boolean longTerm) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.longTerm = longTerm;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 19, (Object)cachedDemographicDrug.longTerm, (Object)longTerm, 0);
    }
    
    private static final String pcGetmethod(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.method;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 20);
        return cachedDemographicDrug.method;
    }
    
    private static final void pcSetmethod(final CachedDemographicDrug cachedDemographicDrug, final String method) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.method = method;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 20, cachedDemographicDrug.method, method, 0);
    }
    
    private static final boolean pcGetnoSubs(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.noSubs;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 21);
        return cachedDemographicDrug.noSubs;
    }
    
    private static final void pcSetnoSubs(final CachedDemographicDrug cachedDemographicDrug, final boolean noSubs) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.noSubs = noSubs;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingBooleanField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 21, cachedDemographicDrug.noSubs, noSubs, 0);
    }
    
    private static final Boolean pcGetpastMed(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.pastMed;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 22);
        return cachedDemographicDrug.pastMed;
    }
    
    private static final void pcSetpastMed(final CachedDemographicDrug cachedDemographicDrug, final Boolean pastMed) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.pastMed = pastMed;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 22, (Object)cachedDemographicDrug.pastMed, (Object)pastMed, 0);
    }
    
    private static final Boolean pcGetpatientCompliance(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.patientCompliance;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 23);
        return cachedDemographicDrug.patientCompliance;
    }
    
    private static final void pcSetpatientCompliance(final CachedDemographicDrug cachedDemographicDrug, final Boolean patientCompliance) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.patientCompliance = patientCompliance;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 23, (Object)cachedDemographicDrug.patientCompliance, (Object)patientCompliance, 0);
    }
    
    private static final boolean pcGetprn(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.prn;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 24);
        return cachedDemographicDrug.prn;
    }
    
    private static final void pcSetprn(final CachedDemographicDrug cachedDemographicDrug, final boolean prn) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.prn = prn;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingBooleanField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 24, cachedDemographicDrug.prn, prn, 0);
    }
    
    private static final String pcGetquantity(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.quantity;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 25);
        return cachedDemographicDrug.quantity;
    }
    
    private static final void pcSetquantity(final CachedDemographicDrug cachedDemographicDrug, final String quantity) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.quantity = quantity;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 25, cachedDemographicDrug.quantity, quantity, 0);
    }
    
    private static final String pcGetregionalIdentifier(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.regionalIdentifier;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 26);
        return cachedDemographicDrug.regionalIdentifier;
    }
    
    private static final void pcSetregionalIdentifier(final CachedDemographicDrug cachedDemographicDrug, final String regionalIdentifier) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.regionalIdentifier = regionalIdentifier;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 26, cachedDemographicDrug.regionalIdentifier, regionalIdentifier, 0);
    }
    
    private static final int pcGetrepeats(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.repeats;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 27);
        return cachedDemographicDrug.repeats;
    }
    
    private static final void pcSetrepeats(final CachedDemographicDrug cachedDemographicDrug, final int repeats) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.repeats = repeats;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 27, cachedDemographicDrug.repeats, repeats, 0);
    }
    
    private static final String pcGetroute(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.route;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 28);
        return cachedDemographicDrug.route;
    }
    
    private static final void pcSetroute(final CachedDemographicDrug cachedDemographicDrug, final String route) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.route = route;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 28, cachedDemographicDrug.route, route, 0);
    }
    
    private static final Date pcGetrxDate(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.rxDate;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 29);
        return cachedDemographicDrug.rxDate;
    }
    
    private static final void pcSetrxDate(final CachedDemographicDrug cachedDemographicDrug, final Date rxDate) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.rxDate = rxDate;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 29, (Object)cachedDemographicDrug.rxDate, (Object)rxDate, 0);
    }
    
    private static final int pcGetscriptNo(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.scriptNo;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 30);
        return cachedDemographicDrug.scriptNo;
    }
    
    private static final void pcSetscriptNo(final CachedDemographicDrug cachedDemographicDrug, final int scriptNo) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.scriptNo = scriptNo;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 30, cachedDemographicDrug.scriptNo, scriptNo, 0);
    }
    
    private static final String pcGetspecial(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.special;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 31);
        return cachedDemographicDrug.special;
    }
    
    private static final void pcSetspecial(final CachedDemographicDrug cachedDemographicDrug, final String special) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.special = special;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 31, cachedDemographicDrug.special, special, 0);
    }
    
    private static final float pcGettakeMax(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.takeMax;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 32);
        return cachedDemographicDrug.takeMax;
    }
    
    private static final void pcSettakeMax(final CachedDemographicDrug cachedDemographicDrug, final float takeMax) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.takeMax = takeMax;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingFloatField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 32, cachedDemographicDrug.takeMax, takeMax, 0);
    }
    
    private static final float pcGettakeMin(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.takeMin;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 33);
        return cachedDemographicDrug.takeMin;
    }
    
    private static final void pcSettakeMin(final CachedDemographicDrug cachedDemographicDrug, final float takeMin) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.takeMin = takeMin;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingFloatField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 33, cachedDemographicDrug.takeMin, takeMin, 0);
    }
    
    private static final String pcGetunit(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.unit;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 34);
        return cachedDemographicDrug.unit;
    }
    
    private static final void pcSetunit(final CachedDemographicDrug cachedDemographicDrug, final String unit) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.unit = unit;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 34, cachedDemographicDrug.unit, unit, 0);
    }
    
    private static final String pcGetunitName(final CachedDemographicDrug cachedDemographicDrug) {
        if (cachedDemographicDrug.pcStateManager == null) {
            return cachedDemographicDrug.unitName;
        }
        cachedDemographicDrug.pcStateManager.accessingField(CachedDemographicDrug.pcInheritedFieldCount + 35);
        return cachedDemographicDrug.unitName;
    }
    
    private static final void pcSetunitName(final CachedDemographicDrug cachedDemographicDrug, final String unitName) {
        if (cachedDemographicDrug.pcStateManager == null) {
            cachedDemographicDrug.unitName = unitName;
            return;
        }
        cachedDemographicDrug.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDrug, CachedDemographicDrug.pcInheritedFieldCount + 35, cachedDemographicDrug.unitName, unitName, 0);
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
