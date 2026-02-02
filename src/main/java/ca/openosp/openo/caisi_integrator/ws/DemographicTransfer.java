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
 * Data transfer object for patient demographic information used in CAISI Integrator web services.
 * This class represents a patient's demographic data that is exchanged between different OpenO EMR
 * installations through the CAISI (Client Access to Integrated Services and Information) Integrator system.
 *
 * <p>The demographic transfer includes essential patient identifiers, personal information, contact details,
 * and health insurance information required for inter-facility patient data sharing in Canadian healthcare systems.
 * This class is JAXB-annotated for XML serialization in SOAP web service communications.</p>
 *
 * <p>Key healthcare data elements include:</p>
 * <ul>
 *   <li>HIN (Health Insurance Number) with validation dates and provincial type</li>
 *   <li>Personal identifiers (name, birth date, gender, SIN)</li>
 *   <li>Contact information (address, phone numbers)</li>
 *   <li>Provider and facility associations</li>
 *   <li>Patient photograph with update tracking</li>
 * </ul>
 *
 * <p><strong>Security Note:</strong> This class contains Protected Health Information (PHI).
 * All transmissions must be over secure channels and comply with PIPEDA/HIPAA regulations.</p>
 *
 * @see Gender
 * @since 2026-01-23
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "demographicTransfer", propOrder = { "birthDate", "caisiDemographicId", "caisiProviderId", "city", "firstName", "gender", "hin", "hinType", "hinValidEnd", "hinValidStart", "hinVersion", "integratorFacilityId", "lastName", "lastUpdateDate", "lastUpdateUser", "phone1", "phone2", "photo", "photoUpdateDate", "province", "removeId", "sin", "streetAddress" })
public class DemographicTransfer implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar birthDate;
    protected int caisiDemographicId;
    protected String caisiProviderId;
    protected String city;
    protected String firstName;
    @XmlSchemaType(name = "string")
    protected Gender gender;
    protected String hin;
    protected String hinType;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar hinValidEnd;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar hinValidStart;
    protected String hinVersion;
    protected Integer integratorFacilityId;
    protected String lastName;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar lastUpdateDate;
    protected String lastUpdateUser;
    protected String phone1;
    protected String phone2;
    protected byte[] photo;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar photoUpdateDate;
    protected String province;
    protected boolean removeId;
    protected String sin;
    protected String streetAddress;

    /**
     * Gets the patient's birth date.
     *
     * @return Calendar the patient's date of birth, or null if not set
     */
    public Calendar getBirthDate() {
        return this.birthDate;
    }

    /**
     * Sets the patient's birth date.
     *
     * @param birthDate Calendar the patient's date of birth
     */
    public void setBirthDate(final Calendar birthDate) {
        this.birthDate = birthDate;
    }

    /**
     * Gets the CAISI demographic identifier for this patient.
     * This is the local demographic ID from the source CAISI system.
     *
     * @return int the CAISI demographic identifier
     */
    public int getCaisiDemographicId() {
        return this.caisiDemographicId;
    }

    /**
     * Sets the CAISI demographic identifier for this patient.
     *
     * @param caisiDemographicId int the CAISI demographic identifier
     */
    public void setCaisiDemographicId(final int caisiDemographicId) {
        this.caisiDemographicId = caisiDemographicId;
    }

    /**
     * Gets the CAISI provider identifier associated with this patient.
     * This identifies the healthcare provider responsible for this patient in the source system.
     *
     * @return String the CAISI provider identifier, or null if not assigned
     */
    public String getCaisiProviderId() {
        return this.caisiProviderId;
    }

    /**
     * Sets the CAISI provider identifier associated with this patient.
     *
     * @param caisiProviderId String the CAISI provider identifier
     */
    public void setCaisiProviderId(final String caisiProviderId) {
        this.caisiProviderId = caisiProviderId;
    }

    /**
     * Gets the patient's city of residence.
     *
     * @return String the city name, or null if not set
     */
    public String getCity() {
        return this.city;
    }

    /**
     * Sets the patient's city of residence.
     *
     * @param city String the city name
     */
    public void setCity(final String city) {
        this.city = city;
    }

    /**
     * Gets the patient's first name.
     *
     * @return String the patient's first name, or null if not set
     */
    public String getFirstName() {
        return this.firstName;
    }

    /**
     * Sets the patient's first name.
     *
     * @param firstName String the patient's first name
     */
    public void setFirstName(final String firstName) {
        this.firstName = firstName;
    }

    /**
     * Gets the patient's gender.
     *
     * @return Gender the patient's gender enumeration value, or null if not set
     */
    public Gender getGender() {
        return this.gender;
    }

    /**
     * Sets the patient's gender.
     *
     * @param gender Gender the patient's gender enumeration value
     */
    public void setGender(final Gender gender) {
        this.gender = gender;
    }

    /**
     * Gets the patient's Health Insurance Number (HIN).
     * The HIN is a provincial health card number used to identify patients in Canadian healthcare systems.
     *
     * @return String the health insurance number, or null if not set
     */
    public String getHin() {
        return this.hin;
    }

    /**
     * Sets the patient's Health Insurance Number (HIN).
     *
     * @param hin String the health insurance number
     */
    public void setHin(final String hin) {
        this.hin = hin;
    }

    /**
     * Gets the HIN type, indicating the provincial jurisdiction of the health card.
     * Common values include "ON" (Ontario), "BC" (British Columbia), etc.
     *
     * @return String the HIN type/province code, or null if not set
     */
    public String getHinType() {
        return this.hinType;
    }

    /**
     * Sets the HIN type, indicating the provincial jurisdiction.
     *
     * @param hinType String the HIN type/province code
     */
    public void setHinType(final String hinType) {
        this.hinType = hinType;
    }

    /**
     * Gets the expiry date for the health insurance number.
     *
     * @return Calendar the date when the HIN expires, or null if not applicable
     */
    public Calendar getHinValidEnd() {
        return this.hinValidEnd;
    }

    /**
     * Sets the expiry date for the health insurance number.
     *
     * @param hinValidEnd Calendar the date when the HIN expires
     */
    public void setHinValidEnd(final Calendar hinValidEnd) {
        this.hinValidEnd = hinValidEnd;
    }

    /**
     * Gets the start date for the health insurance number validity.
     *
     * @return Calendar the date when the HIN becomes valid, or null if not applicable
     */
    public Calendar getHinValidStart() {
        return this.hinValidStart;
    }

    /**
     * Sets the start date for the health insurance number validity.
     *
     * @param hinValidStart Calendar the date when the HIN becomes valid
     */
    public void setHinValidStart(final Calendar hinValidStart) {
        this.hinValidStart = hinValidStart;
    }

    /**
     * Gets the version code for the health insurance number.
     * Some provinces use version codes to track health card renewals or updates.
     *
     * @return String the HIN version code, or null if not applicable
     */
    public String getHinVersion() {
        return this.hinVersion;
    }

    /**
     * Sets the version code for the health insurance number.
     *
     * @param hinVersion String the HIN version code
     */
    public void setHinVersion(final String hinVersion) {
        this.hinVersion = hinVersion;
    }

    /**
     * Gets the integrator facility identifier where this demographic record originated.
     * This identifies which facility in the CAISI Integrator network created or owns this record.
     *
     * @return Integer the integrator facility ID, or null if not assigned
     */
    public Integer getIntegratorFacilityId() {
        return this.integratorFacilityId;
    }

    /**
     * Sets the integrator facility identifier.
     *
     * @param integratorFacilityId Integer the integrator facility ID
     */
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        this.integratorFacilityId = integratorFacilityId;
    }

    /**
     * Gets the patient's last name (surname).
     *
     * @return String the patient's last name, or null if not set
     */
    public String getLastName() {
        return this.lastName;
    }

    /**
     * Sets the patient's last name (surname).
     *
     * @param lastName String the patient's last name
     */
    public void setLastName(final String lastName) {
        this.lastName = lastName;
    }

    /**
     * Gets the timestamp when this demographic record was last updated.
     * Used for audit trails and synchronization between systems.
     *
     * @return Calendar the last update date/time, or null if never updated
     */
    public Calendar getLastUpdateDate() {
        return this.lastUpdateDate;
    }

    /**
     * Sets the timestamp when this demographic record was last updated.
     *
     * @param lastUpdateDate Calendar the last update date/time
     */
    public void setLastUpdateDate(final Calendar lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    /**
     * Gets the username or identifier of the user who last updated this record.
     * Used for audit trails and compliance with healthcare data regulations.
     *
     * @return String the username of the last user to update this record, or null if unknown
     */
    public String getLastUpdateUser() {
        return this.lastUpdateUser;
    }

    /**
     * Sets the username or identifier of the user who last updated this record.
     *
     * @param lastUpdateUser String the username of the updating user
     */
    public void setLastUpdateUser(final String lastUpdateUser) {
        this.lastUpdateUser = lastUpdateUser;
    }

    /**
     * Gets the patient's primary phone number.
     *
     * @return String the primary phone number, or null if not set
     */
    public String getPhone1() {
        return this.phone1;
    }

    /**
     * Sets the patient's primary phone number.
     *
     * @param phone1 String the primary phone number
     */
    public void setPhone1(final String phone1) {
        this.phone1 = phone1;
    }

    /**
     * Gets the patient's secondary phone number.
     *
     * @return String the secondary phone number, or null if not set
     */
    public String getPhone2() {
        return this.phone2;
    }

    /**
     * Sets the patient's secondary phone number.
     *
     * @param phone2 String the secondary phone number
     */
    public void setPhone2(final String phone2) {
        this.phone2 = phone2;
    }

    /**
     * Gets the patient's photograph as a byte array.
     * The photo is typically used for patient identification and verification.
     *
     * @return byte[] the patient photo data, or null if no photo available
     */
    public byte[] getPhoto() {
        return this.photo;
    }

    /**
     * Sets the patient's photograph as a byte array.
     *
     * @param photo byte[] the patient photo data
     */
    public void setPhoto(final byte[] photo) {
        this.photo = photo;
    }

    /**
     * Gets the timestamp when the patient's photograph was last updated.
     *
     * @return Calendar the photo update date/time, or null if photo has never been set
     */
    public Calendar getPhotoUpdateDate() {
        return this.photoUpdateDate;
    }

    /**
     * Sets the timestamp when the patient's photograph was last updated.
     *
     * @param photoUpdateDate Calendar the photo update date/time
     */
    public void setPhotoUpdateDate(final Calendar photoUpdateDate) {
        this.photoUpdateDate = photoUpdateDate;
    }

    /**
     * Gets the patient's province of residence.
     * Uses two-letter Canadian province codes (ON, BC, AB, etc.).
     *
     * @return String the province code, or null if not set
     */
    public String getProvince() {
        return this.province;
    }

    /**
     * Sets the patient's province of residence.
     *
     * @param province String the province code
     */
    public void setProvince(final String province) {
        this.province = province;
    }

    /**
     * Checks if the demographic record should have its ID removed during transfer.
     * This flag may be used during data synchronization to indicate that local IDs
     * should not be included in the transfer to avoid conflicts.
     *
     * @return boolean true if the ID should be removed, false otherwise
     */
    public boolean isRemoveId() {
        return this.removeId;
    }

    /**
     * Sets whether the demographic record should have its ID removed during transfer.
     *
     * @param removeId boolean true to remove ID during transfer, false to retain it
     */
    public void setRemoveId(final boolean removeId) {
        this.removeId = removeId;
    }

    /**
     * Gets the patient's Social Insurance Number (SIN).
     *
     * <p><strong>Privacy Warning:</strong> SIN is highly sensitive personal information.
     * Use of SIN in healthcare should be limited and comply with privacy regulations.</p>
     *
     * @return String the social insurance number, or null if not collected
     */
    public String getSin() {
        return this.sin;
    }

    /**
     * Sets the patient's Social Insurance Number (SIN).
     *
     * @param sin String the social insurance number
     */
    public void setSin(final String sin) {
        this.sin = sin;
    }

    /**
     * Gets the patient's street address.
     *
     * @return String the street address, or null if not set
     */
    public String getStreetAddress() {
        return this.streetAddress;
    }

    /**
     * Sets the patient's street address.
     *
     * @param streetAddress String the street address
     */
    public void setStreetAddress(final String streetAddress) {
        this.streetAddress = streetAddress;
    }
}
