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
 * Cached representation of a patient's allergy information from the CAISI Integrator system.
 *
 * <p>This class serves as a data transfer object (DTO) for allergy data retrieved from the
 * CAISI (Client Access to Integrated Services and Information) Integrator web service.
 * It provides a cached snapshot of patient allergy information to reduce the need for
 * repeated web service calls when accessing allergy data across multiple EMR installations.</p>
 *
 * <p>The class captures comprehensive allergy information including:</p>
 * <ul>
 *   <li>Allergy description and classification codes</li>
 *   <li>Severity and reaction details</li>
 *   <li>Age of onset and life stage information</li>
 *   <li>Regional identifiers for inter-jurisdictional data sharing</li>
 *   <li>Facility and demographic associations</li>
 * </ul>
 *
 * <p>This cached allergy data is essential for clinical decision support, as it allows
 * healthcare providers to quickly access critical patient safety information when prescribing
 * medications or planning treatments, even when the source EMR system is temporarily unavailable.</p>
 *
 * <p>The class uses JAXB annotations for XML serialization, enabling seamless integration
 * with SOAP-based web services used in the CAISI Integrator architecture.</p>
 *
 * @see AbstractModel
 * @see FacilityIdIntegerCompositePk
 * @since 2026-01-24
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cachedDemographicAllergy", propOrder = { "agccs", "agcsp", "ageOfOnset", "caisiDemographicId", "description", "entryDate", "facilityIdIntegerCompositePk", "hicSeqNo", "hiclSeqNo", "lifeStage", "onSetCode", "pickId", "reaction", "regionalIdentifier", "severityCode", "startDate", "typeCode" })
public class CachedDemographicAllergy extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected int agccs;
    protected int agcsp;
    protected String ageOfOnset;
    protected int caisiDemographicId;
    protected String description;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar entryDate;
    protected FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    protected int hicSeqNo;
    protected int hiclSeqNo;
    protected String lifeStage;
    protected String onSetCode;
    protected int pickId;
    protected String reaction;
    protected String regionalIdentifier;
    protected String severityCode;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar startDate;
    protected int typeCode;

    /**
     * Gets the allergy group code - causative substance.
     *
     * <p>This code represents the causative substance group classification for the allergy.</p>
     *
     * @return int the allergy group code for causative substance
     */
    public int getAgccs() {
        return this.agccs;
    }

    /**
     * Sets the allergy group code - causative substance.
     *
     * @param agccs int the allergy group code for causative substance to set
     */
    public void setAgccs(final int agccs) {
        this.agccs = agccs;
    }

    /**
     * Gets the allergy group code - substance preparation.
     *
     * <p>This code represents the substance preparation group classification for the allergy.</p>
     *
     * @return int the allergy group code for substance preparation
     */
    public int getAgcsp() {
        return this.agcsp;
    }

    /**
     * Sets the allergy group code - substance preparation.
     *
     * @param agcsp int the allergy group code for substance preparation to set
     */
    public void setAgcsp(final int agcsp) {
        this.agcsp = agcsp;
    }

    /**
     * Gets the age of onset for the allergy.
     *
     * <p>Indicates the patient's age when the allergy was first observed or reported.</p>
     *
     * @return String the age of onset, may be null if not recorded
     */
    public String getAgeOfOnset() {
        return this.ageOfOnset;
    }

    /**
     * Sets the age of onset for the allergy.
     *
     * @param ageOfOnset String the age of onset to set
     */
    public void setAgeOfOnset(final String ageOfOnset) {
        this.ageOfOnset = ageOfOnset;
    }

    /**
     * Gets the CAISI demographic ID.
     *
     * <p>This is the unique identifier for the patient (demographic) within the CAISI
     * Integrator system, used to link allergy records to the correct patient across
     * multiple integrated EMR installations.</p>
     *
     * @return int the CAISI demographic identifier
     */
    public int getCaisiDemographicId() {
        return this.caisiDemographicId;
    }

    /**
     * Sets the CAISI demographic ID.
     *
     * @param caisiDemographicId int the CAISI demographic identifier to set
     */
    public void setCaisiDemographicId(final int caisiDemographicId) {
        this.caisiDemographicId = caisiDemographicId;
    }

    /**
     * Gets the allergy description.
     *
     * <p>This is a human-readable description of the allergy, typically including the
     * allergen name and may include additional clinical notes.</p>
     *
     * @return String the allergy description, may be null
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Sets the allergy description.
     *
     * @param description String the allergy description to set
     */
    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     * Gets the date when this allergy record was entered into the system.
     *
     * <p>This represents the timestamp when the allergy information was first recorded
     * in the EMR system, which may differ from the onset date of the allergy itself.</p>
     *
     * @return Calendar the entry date, may be null
     */
    public Calendar getEntryDate() {
        return this.entryDate;
    }

    /**
     * Sets the date when this allergy record was entered into the system.
     *
     * @param entryDate Calendar the entry date to set
     */
    public void setEntryDate(final Calendar entryDate) {
        this.entryDate = entryDate;
    }

    /**
     * Gets the composite primary key identifying the facility.
     *
     * <p>This composite key uniquely identifies the healthcare facility where the allergy
     * record originates, enabling proper data attribution in multi-facility environments.</p>
     *
     * @return FacilityIdIntegerCompositePk the facility identifier composite key, may be null
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return this.facilityIdIntegerCompositePk;
    }

    /**
     * Sets the composite primary key identifying the facility.
     *
     * @param facilityIdIntegerCompositePk FacilityIdIntegerCompositePk the facility identifier to set
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        this.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
    }

    /**
     * Gets the health insurance coverage sequence number.
     *
     * <p>This sequence number is used to track the health insurance coverage associated
     * with the allergy record for billing and administrative purposes.</p>
     *
     * @return int the health insurance coverage sequence number
     */
    public int getHicSeqNo() {
        return this.hicSeqNo;
    }

    /**
     * Sets the health insurance coverage sequence number.
     *
     * @param hicSeqNo int the health insurance coverage sequence number to set
     */
    public void setHicSeqNo(final int hicSeqNo) {
        this.hicSeqNo = hicSeqNo;
    }

    /**
     * Gets the health insurance coverage line sequence number.
     *
     * <p>This sequence number provides a more granular level of tracking within the
     * health insurance coverage for administrative and billing purposes.</p>
     *
     * @return int the health insurance coverage line sequence number
     */
    public int getHiclSeqNo() {
        return this.hiclSeqNo;
    }

    /**
     * Sets the health insurance coverage line sequence number.
     *
     * @param hiclSeqNo int the health insurance coverage line sequence number to set
     */
    public void setHiclSeqNo(final int hiclSeqNo) {
        this.hiclSeqNo = hiclSeqNo;
    }

    /**
     * Gets the life stage when the allergy was identified.
     *
     * <p>Indicates the patient's life stage (e.g., infant, child, adult, senior) when
     * the allergy was first identified, which can be clinically relevant for understanding
     * allergy development and potential resolution over time.</p>
     *
     * @return String the life stage description, may be null
     */
    public String getLifeStage() {
        return this.lifeStage;
    }

    /**
     * Sets the life stage when the allergy was identified.
     *
     * @param lifeStage String the life stage description to set
     */
    public void setLifeStage(final String lifeStage) {
        this.lifeStage = lifeStage;
    }

    /**
     * Gets the onset code for the allergy.
     *
     * <p>This coded value represents the type or timing of allergy onset, which may be
     * used for clinical classification and reporting purposes.</p>
     *
     * @return String the onset code, may be null
     */
    public String getOnSetCode() {
        return this.onSetCode;
    }

    /**
     * Sets the onset code for the allergy.
     *
     * @param onSetCode String the onset code to set
     */
    public void setOnSetCode(final String onSetCode) {
        this.onSetCode = onSetCode;
    }

    /**
     * Gets the pick list identifier.
     *
     * <p>This identifier references a standardized allergy code from a pick list or
     * clinical terminology system, enabling consistent allergy coding across the EMR.</p>
     *
     * @return int the pick list identifier
     */
    public int getPickId() {
        return this.pickId;
    }

    /**
     * Sets the pick list identifier.
     *
     * @param pickId int the pick list identifier to set
     */
    public void setPickId(final int pickId) {
        this.pickId = pickId;
    }

    /**
     * Gets the allergic reaction description.
     *
     * <p>This field captures the clinical manifestation of the allergy, such as rash,
     * anaphylaxis, respiratory distress, etc. This information is critical for clinical
     * decision support and patient safety.</p>
     *
     * @return String the reaction description, may be null
     */
    public String getReaction() {
        return this.reaction;
    }

    /**
     * Sets the allergic reaction description.
     *
     * @param reaction String the reaction description to set
     */
    public void setReaction(final String reaction) {
        this.reaction = reaction;
    }

    /**
     * Gets the regional identifier for the allergy record.
     *
     * <p>This identifier enables allergy data to be tracked and shared across different
     * healthcare jurisdictions or regions, supporting inter-regional patient care coordination.</p>
     *
     * @return String the regional identifier, may be null
     */
    public String getRegionalIdentifier() {
        return this.regionalIdentifier;
    }

    /**
     * Sets the regional identifier for the allergy record.
     *
     * @param regionalIdentifier String the regional identifier to set
     */
    public void setRegionalIdentifier(final String regionalIdentifier) {
        this.regionalIdentifier = regionalIdentifier;
    }

    /**
     * Gets the severity code for the allergy.
     *
     * <p>This coded value indicates the severity level of the allergic reaction
     * (e.g., mild, moderate, severe, life-threatening). This is essential information
     * for clinical decision support and medication safety alerts.</p>
     *
     * @return String the severity code, may be null
     */
    public String getSeverityCode() {
        return this.severityCode;
    }

    /**
     * Sets the severity code for the allergy.
     *
     * @param severityCode String the severity code to set
     */
    public void setSeverityCode(final String severityCode) {
        this.severityCode = severityCode;
    }

    /**
     * Gets the date when the allergy symptoms first started.
     *
     * <p>This represents the clinical onset date of the allergy, which may differ from
     * the entry date when the record was created in the system.</p>
     *
     * @return Calendar the start date of the allergy, may be null
     */
    public Calendar getStartDate() {
        return this.startDate;
    }

    /**
     * Sets the date when the allergy symptoms first started.
     *
     * @param startDate Calendar the start date of the allergy to set
     */
    public void setStartDate(final Calendar startDate) {
        this.startDate = startDate;
    }

    /**
     * Gets the allergy type code.
     *
     * <p>This code classifies the type of allergy (e.g., drug allergy, food allergy,
     * environmental allergy) for proper clinical categorization and reporting.</p>
     *
     * @return int the type code
     */
    public int getTypeCode() {
        return this.typeCode;
    }

    /**
     * Sets the allergy type code.
     *
     * @param typeCode int the type code to set
     */
    public void setTypeCode(final int typeCode) {
        this.typeCode = typeCode;
    }
}
