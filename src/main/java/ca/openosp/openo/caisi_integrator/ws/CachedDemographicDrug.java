package ca.openosp.openo.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlSchemaType;
import org.w3._2001.xmlschema.Adapter1;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.annotation.XmlElement;
import java.util.Calendar;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Represents a cached demographic drug record for CAISI integrator web services.
 *
 * This class is a data transfer object (DTO) used in web service communication for the CAISI
 * (Collaborative Application for Integrated Services Information) integrator system. It encapsulates
 * comprehensive medication information associated with a patient demographic record, including
 * prescription details, drug identification (ATC codes, brand/generic names), dosage instructions,
 * administration routes, and archival tracking.
 *
 * The class supports JAXB XML serialization for inter-system communication within the OpenO EMR
 * healthcare integration framework, enabling medication data exchange between multiple EMR installations
 * and healthcare facilities.
 *
 * Key healthcare concepts represented:
 * <ul>
 *   <li>ATC (Anatomical Therapeutic Chemical) drug classification codes</li>
 *   <li>Prescription details including dosage, frequency, duration, and route of administration</li>
 *   <li>Drug substitution controls and PRN (as needed) medication flags</li>
 *   <li>Multi-facility support through composite primary key references</li>
 *   <li>Regional identifier support for provincial healthcare system integration</li>
 *   <li>Medication history tracking including archival status and refill dates</li>
 * </ul>
 *
 * @see AbstractModel
 * @see FacilityIdIntegerCompositePk
 * @since 2026-01-24
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cachedDemographicDrug", propOrder = { "archived", "archivedDate", "archivedReason", "atc", "brandName", "caisiDemographicId", "caisiProviderId", "createDate", "customInstructions", "customName", "dosage", "drugForm", "durUnit", "duration", "endDate", "facilityIdIntegerCompositePk", "freqCode", "genericName", "lastRefillDate", "longTerm", "method", "noSubs", "pastMed", "patientCompliance", "prn", "quantity", "regionalIdentifier", "repeats", "route", "rxDate", "scriptNo", "special", "takeMax", "takeMin", "unit", "unitName" })
public class CachedDemographicDrug extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected boolean archived;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar archivedDate;
    protected String archivedReason;
    protected String atc;
    protected String brandName;
    protected Integer caisiDemographicId;
    protected String caisiProviderId;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar createDate;
    protected boolean customInstructions;
    protected String customName;
    protected String dosage;
    protected String drugForm;
    protected String durUnit;
    protected String duration;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar endDate;
    protected FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    protected String freqCode;
    protected String genericName;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar lastRefillDate;
    protected Boolean longTerm;
    protected String method;
    protected boolean noSubs;
    protected Boolean pastMed;
    protected Boolean patientCompliance;
    protected boolean prn;
    protected String quantity;
    protected String regionalIdentifier;
    protected int repeats;
    protected String route;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar rxDate;
    protected int scriptNo;
    protected String special;
    protected float takeMax;
    protected float takeMin;
    protected String unit;
    protected String unitName;

    /**
     * Checks if this drug record has been archived.
     *
     * @return boolean true if the drug record is archived, false otherwise
     */
    public boolean isArchived() {
        return this.archived;
    }

    /**
     * Sets the archived status of this drug record.
     *
     * @param archived boolean indicating whether the drug record should be archived
     */
    public void setArchived(final boolean archived) {
        this.archived = archived;
    }

    /**
     * Gets the date when this drug record was archived.
     *
     * @return Calendar the date and time when the drug record was archived, or null if not archived
     */
    public Calendar getArchivedDate() {
        return this.archivedDate;
    }

    /**
     * Sets the date when this drug record was archived.
     *
     * @param archivedDate Calendar representing the date and time when the drug record was archived
     */
    public void setArchivedDate(final Calendar archivedDate) {
        this.archivedDate = archivedDate;
    }

    /**
     * Gets the reason why this drug record was archived.
     *
     * @return String the reason for archiving the drug record, or null if not archived or no reason provided
     */
    public String getArchivedReason() {
        return this.archivedReason;
    }

    /**
     * Sets the reason why this drug record was archived.
     *
     * @param archivedReason String describing the reason for archiving the drug record
     */
    public void setArchivedReason(final String archivedReason) {
        this.archivedReason = archivedReason;
    }

    /**
     * Gets the ATC (Anatomical Therapeutic Chemical) classification code for this drug.
     *
     * The ATC system is used internationally for the classification of active pharmaceutical ingredients
     * according to the organ or system on which they act and their therapeutic, pharmacological, and
     * chemical properties.
     *
     * @return String the ATC code, or null if not specified
     */
    public String getAtc() {
        return this.atc;
    }

    /**
     * Sets the ATC (Anatomical Therapeutic Chemical) classification code for this drug.
     *
     * @param atc String representing the ATC code for the medication
     */
    public void setAtc(final String atc) {
        this.atc = atc;
    }

    /**
     * Gets the brand (trade) name of the medication.
     *
     * @return String the brand name of the drug, or null if not specified
     */
    public String getBrandName() {
        return this.brandName;
    }

    /**
     * Sets the brand (trade) name of the medication.
     *
     * @param brandName String representing the brand name of the drug
     */
    public void setBrandName(final String brandName) {
        this.brandName = brandName;
    }

    /**
     * Gets the CAISI demographic identifier associated with this drug record.
     *
     * This identifier links the drug record to a specific patient demographic in the CAISI
     * (Collaborative Application for Integrated Services Information) system.
     *
     * @return Integer the CAISI demographic ID, or null if not specified
     */
    public Integer getCaisiDemographicId() {
        return this.caisiDemographicId;
    }

    /**
     * Sets the CAISI demographic identifier associated with this drug record.
     *
     * @param caisiDemographicId Integer representing the CAISI demographic ID
     */
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        this.caisiDemographicId = caisiDemographicId;
    }

    /**
     * Gets the CAISI provider identifier who prescribed or manages this drug record.
     *
     * @return String the CAISI provider ID, or null if not specified
     */
    public String getCaisiProviderId() {
        return this.caisiProviderId;
    }

    /**
     * Sets the CAISI provider identifier who prescribed or manages this drug record.
     *
     * @param caisiProviderId String representing the CAISI provider ID
     */
    public void setCaisiProviderId(final String caisiProviderId) {
        this.caisiProviderId = caisiProviderId;
    }

    /**
     * Gets the date when this drug record was created.
     *
     * @return Calendar the date and time when the drug record was created, or null if not specified
     */
    public Calendar getCreateDate() {
        return this.createDate;
    }

    /**
     * Sets the date when this drug record was created.
     *
     * @param createDate Calendar representing the date and time when the drug record was created
     */
    public void setCreateDate(final Calendar createDate) {
        this.createDate = createDate;
    }

    /**
     * Checks if custom instructions have been provided for this medication.
     *
     * @return boolean true if custom instructions are present, false otherwise
     */
    public boolean isCustomInstructions() {
        return this.customInstructions;
    }

    /**
     * Sets whether custom instructions are provided for this medication.
     *
     * @param customInstructions boolean indicating if custom instructions are present
     */
    public void setCustomInstructions(final boolean customInstructions) {
        this.customInstructions = customInstructions;
    }

    /**
     * Gets the custom name for this medication.
     *
     * Custom names allow healthcare providers to use alternative or descriptive names for medications
     * that may differ from the standard brand or generic names.
     *
     * @return String the custom name for the medication, or null if not specified
     */
    public String getCustomName() {
        return this.customName;
    }

    /**
     * Sets the custom name for this medication.
     *
     * @param customName String representing the custom name for the medication
     */
    public void setCustomName(final String customName) {
        this.customName = customName;
    }

    /**
     * Gets the dosage information for this medication.
     *
     * Dosage typically includes the strength and amount (e.g., "10mg", "500mg/5mL").
     *
     * @return String the dosage information, or null if not specified
     */
    public String getDosage() {
        return this.dosage;
    }

    /**
     * Sets the dosage information for this medication.
     *
     * @param dosage String representing the dosage information
     */
    public void setDosage(final String dosage) {
        this.dosage = dosage;
    }

    /**
     * Gets the pharmaceutical form of the medication.
     *
     * Drug form describes the physical form of the medication (e.g., "tablet", "capsule", "liquid",
     * "injection", "cream").
     *
     * @return String the pharmaceutical form, or null if not specified
     */
    public String getDrugForm() {
        return this.drugForm;
    }

    /**
     * Sets the pharmaceutical form of the medication.
     *
     * @param drugForm String representing the pharmaceutical form
     */
    public void setDrugForm(final String drugForm) {
        this.drugForm = drugForm;
    }

    /**
     * Gets the unit of time used for the duration of treatment.
     *
     * Duration unit specifies the time measurement (e.g., "days", "weeks", "months") used with
     * the duration value to indicate how long the medication should be taken.
     *
     * @return String the duration unit, or null if not specified
     */
    public String getDurUnit() {
        return this.durUnit;
    }

    /**
     * Sets the unit of time used for the duration of treatment.
     *
     * @param durUnit String representing the duration unit (e.g., "days", "weeks")
     */
    public void setDurUnit(final String durUnit) {
        this.durUnit = durUnit;
    }

    /**
     * Gets the duration value for how long the medication should be taken.
     *
     * This value is used in conjunction with the duration unit to specify the total treatment period.
     *
     * @return String the duration value, or null if not specified
     */
    public String getDuration() {
        return this.duration;
    }

    /**
     * Sets the duration value for how long the medication should be taken.
     *
     * @param duration String representing the numeric duration value
     */
    public void setDuration(final String duration) {
        this.duration = duration;
    }

    /**
     * Gets the end date for the medication prescription.
     *
     * The end date indicates when the medication should be discontinued or when the prescription expires.
     *
     * @return Calendar the end date of the prescription, or null if not specified or indefinite
     */
    public Calendar getEndDate() {
        return this.endDate;
    }

    /**
     * Sets the end date for the medication prescription.
     *
     * @param endDate Calendar representing the date when the prescription ends
     */
    public void setEndDate(final Calendar endDate) {
        this.endDate = endDate;
    }

    /**
     * Gets the composite primary key containing facility and integer identifiers.
     *
     * This composite key is used to uniquely identify the drug record across multiple healthcare
     * facilities in the integrator system.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key, or null if not specified
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return this.facilityIdIntegerCompositePk;
    }

    /**
     * Sets the composite primary key containing facility and integer identifiers.
     *
     * @param facilityIdIntegerCompositePk FacilityIdIntegerCompositePk representing the composite primary key
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        this.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
    }

    /**
     * Gets the frequency code for medication administration.
     *
     * Frequency codes use standard medical abbreviations (e.g., "BID" for twice daily, "TID" for
     * three times daily, "QD" for once daily, "PRN" for as needed).
     *
     * @return String the frequency code, or null if not specified
     */
    public String getFreqCode() {
        return this.freqCode;
    }

    /**
     * Sets the frequency code for medication administration.
     *
     * @param freqCode String representing the frequency code
     */
    public void setFreqCode(final String freqCode) {
        this.freqCode = freqCode;
    }

    /**
     * Gets the generic (non-proprietary) name of the medication.
     *
     * The generic name is the official pharmaceutical name of the active ingredient, as opposed to
     * the brand name which is trademarked by a manufacturer.
     *
     * @return String the generic name of the drug, or null if not specified
     */
    public String getGenericName() {
        return this.genericName;
    }

    /**
     * Sets the generic (non-proprietary) name of the medication.
     *
     * @param genericName String representing the generic name of the drug
     */
    public void setGenericName(final String genericName) {
        this.genericName = genericName;
    }

    /**
     * Gets the date when the prescription was last refilled.
     *
     * This tracking is important for medication adherence monitoring and refill authorization.
     *
     * @return Calendar the date of the last refill, or null if never refilled or not specified
     */
    public Calendar getLastRefillDate() {
        return this.lastRefillDate;
    }

    /**
     * Sets the date when the prescription was last refilled.
     *
     * @param lastRefillDate Calendar representing the date of the last refill
     */
    public void setLastRefillDate(final Calendar lastRefillDate) {
        this.lastRefillDate = lastRefillDate;
    }

    /**
     * Checks if this is a long-term medication.
     *
     * Long-term medications are those prescribed for chronic conditions requiring extended or
     * indefinite use, as opposed to short-term acute treatments.
     *
     * @return Boolean true if this is a long-term medication, false otherwise, or null if not specified
     */
    public Boolean isLongTerm() {
        return this.longTerm;
    }

    /**
     * Sets whether this is a long-term medication.
     *
     * @param longTerm Boolean indicating if this is a long-term medication
     */
    public void setLongTerm(final Boolean longTerm) {
        this.longTerm = longTerm;
    }

    /**
     * Gets the method of administration for the medication.
     *
     * The method describes how the medication should be taken or administered (e.g., "oral",
     * "topical", "intravenous", "subcutaneous").
     *
     * @return String the method of administration, or null if not specified
     */
    public String getMethod() {
        return this.method;
    }

    /**
     * Sets the method of administration for the medication.
     *
     * @param method String representing the method of administration
     */
    public void setMethod(final String method) {
        this.method = method;
    }

    /**
     * Checks if substitution with generic alternatives is not allowed for this medication.
     *
     * When true, the prescriber has indicated that the exact prescribed medication must be dispensed
     * and generic substitutions are not permitted.
     *
     * @return boolean true if substitutions are not allowed, false if substitutions are permitted
     */
    public boolean isNoSubs() {
        return this.noSubs;
    }

    /**
     * Sets whether substitution with generic alternatives is not allowed for this medication.
     *
     * @param noSubs boolean indicating if substitutions are prohibited
     */
    public void setNoSubs(final boolean noSubs) {
        this.noSubs = noSubs;
    }

    /**
     * Checks if this is a past medication no longer actively prescribed.
     *
     * Past medications are those that the patient previously took but are no longer part of their
     * current medication regimen. This is important for maintaining complete medication history.
     *
     * @return Boolean true if this is a past medication, false if current, or null if not specified
     */
    public Boolean isPastMed() {
        return this.pastMed;
    }

    /**
     * Sets whether this is a past medication no longer actively prescribed.
     *
     * @param pastMed Boolean indicating if this is a past medication
     */
    public void setPastMed(final Boolean pastMed) {
        this.pastMed = pastMed;
    }

    /**
     * Checks the patient's compliance status with this medication regimen.
     *
     * Patient compliance (also called adherence) indicates whether the patient is taking the
     * medication as prescribed. This is critical for treatment effectiveness monitoring.
     *
     * @return Boolean true if the patient is compliant, false if non-compliant, or null if not assessed
     */
    public Boolean isPatientCompliance() {
        return this.patientCompliance;
    }

    /**
     * Sets the patient's compliance status with this medication regimen.
     *
     * @param patientCompliance Boolean indicating patient compliance status
     */
    public void setPatientCompliance(final Boolean patientCompliance) {
        this.patientCompliance = patientCompliance;
    }

    /**
     * Checks if this medication is to be taken PRN (pro re nata - as needed).
     *
     * PRN medications are taken only when needed for specific symptoms or situations, rather than
     * on a fixed schedule. Examples include pain medications, anti-nausea drugs, or rescue inhalers.
     *
     * @return boolean true if this is a PRN medication, false if it follows a fixed schedule
     */
    public boolean isPrn() {
        return this.prn;
    }

    /**
     * Sets whether this medication is to be taken PRN (as needed).
     *
     * @param prn boolean indicating if this is a PRN medication
     */
    public void setPrn(final boolean prn) {
        this.prn = prn;
    }

    /**
     * Gets the quantity of medication prescribed.
     *
     * Quantity typically represents the number of units dispensed (e.g., number of tablets,
     * capsules, or volume of liquid medication).
     *
     * @return String the quantity prescribed, or null if not specified
     */
    public String getQuantity() {
        return this.quantity;
    }

    /**
     * Sets the quantity of medication prescribed.
     *
     * @param quantity String representing the quantity prescribed
     */
    public void setQuantity(final String quantity) {
        this.quantity = quantity;
    }

    /**
     * Gets the regional identifier for this drug record.
     *
     * Regional identifiers support provincial or territorial healthcare system requirements in
     * Canada, allowing medication tracking specific to jurisdictions (e.g., Ontario, British Columbia).
     *
     * @return String the regional identifier, or null if not specified
     */
    public String getRegionalIdentifier() {
        return this.regionalIdentifier;
    }

    /**
     * Sets the regional identifier for this drug record.
     *
     * @param regionalIdentifier String representing the regional identifier
     */
    public void setRegionalIdentifier(final String regionalIdentifier) {
        this.regionalIdentifier = regionalIdentifier;
    }

    /**
     * Gets the number of prescription refills authorized.
     *
     * This indicates how many times the prescription can be refilled without requiring a new
     * prescription from the healthcare provider.
     *
     * @return int the number of authorized refills
     */
    public int getRepeats() {
        return this.repeats;
    }

    /**
     * Sets the number of prescription refills authorized.
     *
     * @param repeats int representing the number of authorized refills
     */
    public void setRepeats(final int repeats) {
        this.repeats = repeats;
    }

    /**
     * Gets the route of administration for the medication.
     *
     * The route specifies how the medication enters the body (e.g., "PO" for oral/by mouth,
     * "IV" for intravenous, "IM" for intramuscular, "topical" for skin application).
     *
     * @return String the route of administration, or null if not specified
     */
    public String getRoute() {
        return this.route;
    }

    /**
     * Sets the route of administration for the medication.
     *
     * @param route String representing the route of administration
     */
    public void setRoute(final String route) {
        this.route = route;
    }

    /**
     * Gets the prescription date (Rx date) when the medication was prescribed.
     *
     * This is the date the healthcare provider wrote or authorized the prescription.
     *
     * @return Calendar the prescription date, or null if not specified
     */
    public Calendar getRxDate() {
        return this.rxDate;
    }

    /**
     * Sets the prescription date (Rx date) when the medication was prescribed.
     *
     * @param rxDate Calendar representing the prescription date
     */
    public void setRxDate(final Calendar rxDate) {
        this.rxDate = rxDate;
    }

    /**
     * Gets the prescription script number.
     *
     * The script number is a unique identifier assigned to the prescription for tracking and
     * reference purposes within the pharmacy and healthcare system.
     *
     * @return int the script number
     */
    public int getScriptNo() {
        return this.scriptNo;
    }

    /**
     * Sets the prescription script number.
     *
     * @param scriptNo int representing the script number
     */
    public void setScriptNo(final int scriptNo) {
        this.scriptNo = scriptNo;
    }

    /**
     * Gets special instructions or notes for this medication.
     *
     * Special instructions may include specific timing requirements, dietary considerations,
     * warnings, or other important information for the patient or pharmacist.
     *
     * @return String special instructions, or null if not specified
     */
    public String getSpecial() {
        return this.special;
    }

    /**
     * Sets special instructions or notes for this medication.
     *
     * @param special String representing special instructions
     */
    public void setSpecial(final String special) {
        this.special = special;
    }

    /**
     * Gets the maximum dosage amount to take per administration.
     *
     * This specifies the upper limit of how much medication should be taken at one time, particularly
     * important for PRN medications or those with flexible dosing ranges.
     *
     * @return float the maximum dosage amount per administration
     */
    public float getTakeMax() {
        return this.takeMax;
    }

    /**
     * Sets the maximum dosage amount to take per administration.
     *
     * @param takeMax float representing the maximum dosage amount
     */
    public void setTakeMax(final float takeMax) {
        this.takeMax = takeMax;
    }

    /**
     * Gets the minimum dosage amount to take per administration.
     *
     * This specifies the lower limit of how much medication should be taken at one time, useful
     * for medications with flexible dosing ranges or titration requirements.
     *
     * @return float the minimum dosage amount per administration
     */
    public float getTakeMin() {
        return this.takeMin;
    }

    /**
     * Sets the minimum dosage amount to take per administration.
     *
     * @param takeMin float representing the minimum dosage amount
     */
    public void setTakeMin(final float takeMin) {
        this.takeMin = takeMin;
    }

    /**
     * Gets the unit of measurement for the medication dosage.
     *
     * Common units include "mg" (milligrams), "mL" (milliliters), "IU" (international units),
     * "mcg" (micrograms), "g" (grams), etc.
     *
     * @return String the unit of measurement, or null if not specified
     */
    public String getUnit() {
        return this.unit;
    }

    /**
     * Sets the unit of measurement for the medication dosage.
     *
     * @param unit String representing the unit of measurement
     */
    public void setUnit(final String unit) {
        this.unit = unit;
    }

    /**
     * Gets the descriptive name of the dosage unit.
     *
     * While the unit field contains the abbreviation (e.g., "mg"), the unit name may contain
     * the full description or alternative representation of the measurement unit.
     *
     * @return String the descriptive unit name, or null if not specified
     */
    public String getUnitName() {
        return this.unitName;
    }

    /**
     * Sets the descriptive name of the dosage unit.
     *
     * @param unitName String representing the descriptive unit name
     */
    public void setUnitName(final String unitName) {
        this.unitName = unitName;
    }
}
