package ca.openosp.openo.caisi_integrator.ws.transfer;

import ca.openosp.openo.caisi_integrator.dao.CachedDemographic;
import java.util.Date;
import java.io.Serializable;

/**
 * Data transfer object for demographic information in the CAISI Integrator web service layer.
 *
 * This class is used to transfer patient demographic data between OpenO EMR instances through the
 * CAISI Integrator system, which enables inter-EMR data sharing across multiple OpenO installations.
 * It encapsulates all essential patient identification and contact information required for
 * healthcare service delivery in the Canadian context, including Health Insurance Number (HIN)
 * management with provincial validation periods.
 *
 * The class supports bidirectional synchronization of patient records while maintaining PHI
 * (Protected Health Information) compliance. It includes optional photo data transfer for
 * patient identification and audit trail fields (lastUpdateUser, lastUpdateDate) for tracking
 * data changes across systems.
 *
 * @see ca.openosp.openo.caisi_integrator.dao.CachedDemographic
 * @since 2026-01-24
 */
public class DemographicTransfer implements Serializable
{
    private static final long serialVersionUID = 570194986641348591L;
    private Integer integratorFacilityId;
    private int caisiDemographicId;
    private String caisiProviderId;
    private String firstName;
    private String lastName;
    private Date birthDate;
    private CachedDemographic.Gender gender;
    private String hin;
    private String hinType;
    private String hinVersion;
    private Date hinValidStart;
    private Date hinValidEnd;
    private String sin;
    private String province;
    private String city;
    private String streetAddress;
    private String phone1;
    private String phone2;
    private String lastUpdateUser;
    private Date lastUpdateDate;
    private Date photoUpdateDate;
    private byte[] photo;
    private boolean removeId;

    /**
     * Default constructor that initializes all fields to null or default values.
     *
     * Creates a new DemographicTransfer instance with all reference fields set to null,
     * numeric fields set to 0, and boolean fields set to false. This constructor is
     * typically used when creating a new transfer object before populating it with
     * demographic data from a source system.
     */
    public DemographicTransfer() {
        this.integratorFacilityId = null;
        this.caisiDemographicId = 0;
        this.caisiProviderId = null;
        this.firstName = null;
        this.lastName = null;
        this.birthDate = null;
        this.gender = null;
        this.hin = null;
        this.hinType = null;
        this.hinVersion = null;
        this.hinValidStart = null;
        this.hinValidEnd = null;
        this.sin = null;
        this.province = null;
        this.city = null;
        this.photoUpdateDate = null;
        this.photo = null;
        this.removeId = false;
    }

    /**
     * Gets the integrator facility identifier.
     *
     * @return Integer the unique identifier of the facility in the integrator system, or null if not set
     */
    public Integer getIntegratorFacilityId() {
        return this.integratorFacilityId;
    }

    /**
     * Sets the integrator facility identifier.
     *
     * @param integratorFacilityId Integer the unique identifier of the facility in the integrator system
     */
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        this.integratorFacilityId = integratorFacilityId;
    }

    /**
     * Gets the CAISI demographic identifier.
     *
     * @return int the unique demographic identifier in the CAISI system
     */
    public int getCaisiDemographicId() {
        return this.caisiDemographicId;
    }

    /**
     * Sets the CAISI demographic identifier.
     *
     * @param caisiDemographicId int the unique demographic identifier in the CAISI system
     */
    public void setCaisiDemographicId(final int caisiDemographicId) {
        this.caisiDemographicId = caisiDemographicId;
    }

    /**
     * Gets the CAISI provider identifier.
     *
     * @return String the unique identifier of the provider in the CAISI system, or null if not set
     */
    public String getCaisiProviderId() {
        return this.caisiProviderId;
    }

    /**
     * Sets the CAISI provider identifier.
     *
     * @param caisiProviderId String the unique identifier of the provider in the CAISI system
     */
    public void setCaisiProviderId(final String caisiProviderId) {
        this.caisiProviderId = caisiProviderId;
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
     * Gets the patient's last name.
     *
     * @return String the patient's last name, or null if not set
     */
    public String getLastName() {
        return this.lastName;
    }

    /**
     * Sets the patient's last name.
     *
     * @param lastName String the patient's last name
     */
    public void setLastName(final String lastName) {
        this.lastName = lastName;
    }

    /**
     * Gets the patient's birth date.
     *
     * @return Date the patient's birth date, or null if not set
     */
    public Date getBirthDate() {
        return this.birthDate;
    }

    /**
     * Sets the patient's birth date.
     *
     * @param birthDate Date the patient's birth date
     */
    public void setBirthDate(final Date birthDate) {
        this.birthDate = birthDate;
    }

    /**
     * Gets the patient's gender.
     *
     * @return CachedDemographic.Gender the patient's gender, or null if not set
     */
    public CachedDemographic.Gender getGender() {
        return this.gender;
    }

    /**
     * Sets the patient's gender.
     *
     * @param gender CachedDemographic.Gender the patient's gender
     */
    public void setGender(final CachedDemographic.Gender gender) {
        this.gender = gender;
    }

    /**
     * Gets the patient's Health Insurance Number (HIN).
     *
     * The HIN is the provincial health card number used for billing and patient identification
     * in the Canadian healthcare system. Each province has its own format and validation rules.
     *
     * @return String the patient's Health Insurance Number, or null if not set
     */
    public String getHin() {
        return this.hin;
    }

    /**
     * Sets the patient's Health Insurance Number (HIN).
     *
     * @param hin String the patient's Health Insurance Number
     */
    public void setHin(final String hin) {
        this.hin = hin;
    }

    /**
     * Gets the HIN version code.
     *
     * The version code is typically a single character that appears on provincial health cards
     * and is used for validation purposes.
     *
     * @return String the HIN version code, or null if not set
     */
    public String getHinVersion() {
        return this.hinVersion;
    }

    /**
     * Sets the HIN version code.
     *
     * @param hinVersion String the HIN version code
     */
    public void setHinVersion(final String hinVersion) {
        this.hinVersion = hinVersion;
    }

    /**
     * Gets the patient's Social Insurance Number (SIN).
     *
     * Note: SIN is sensitive personal information and should be handled with appropriate
     * security measures and only collected when legally required.
     *
     * @return String the patient's Social Insurance Number, or null if not set
     */
    public String getSin() {
        return this.sin;
    }

    /**
     * Sets the patient's Social Insurance Number (SIN).
     *
     * @param sin String the patient's Social Insurance Number
     */
    public void setSin(final String sin) {
        this.sin = sin;
    }

    /**
     * Gets the patient's province of residence.
     *
     * This field typically contains a two-letter province code (e.g., ON for Ontario, BC for
     * British Columbia) and is used for determining applicable provincial healthcare regulations
     * and billing rules.
     *
     * @return String the patient's province code, or null if not set
     */
    public String getProvince() {
        return this.province;
    }

    /**
     * Sets the patient's province of residence.
     *
     * @param province String the patient's province code
     */
    public void setProvince(final String province) {
        this.province = province;
    }

    /**
     * Gets the patient's city of residence.
     *
     * @return String the patient's city, or null if not set
     */
    public String getCity() {
        return this.city;
    }

    /**
     * Sets the patient's city of residence.
     *
     * @param city String the patient's city
     */
    public void setCity(final String city) {
        this.city = city;
    }

    /**
     * Gets the date when the patient's photo was last updated.
     *
     * This timestamp is used to determine if a photo needs to be synchronized between
     * systems in the integrator network.
     *
     * @return Date the photo's last update date, or null if no photo or not set
     */
    public Date getPhotoUpdateDate() {
        return this.photoUpdateDate;
    }

    /**
     * Sets the date when the patient's photo was last updated.
     *
     * @param photoUpdateDate Date the photo's last update date
     */
    public void setPhotoUpdateDate(final Date photoUpdateDate) {
        this.photoUpdateDate = photoUpdateDate;
    }

    /**
     * Gets the patient's photo as a byte array.
     *
     * The photo is stored in binary format and is used for patient identification purposes.
     * This field may be null if no photo is available or if photo transfer is not required.
     *
     * @return byte[] the patient's photo in binary format, or null if not set
     */
    public byte[] getPhoto() {
        return this.photo;
    }

    /**
     * Sets the patient's photo as a byte array.
     *
     * @param photo byte[] the patient's photo in binary format
     */
    public void setPhoto(final byte[] photo) {
        this.photo = photo;
    }

    /**
     * Gets the HIN type code.
     *
     * The HIN type indicates the category of health insurance coverage (e.g., regular coverage,
     * out-of-province, refugee coverage) and varies by province.
     *
     * @return String the HIN type code, or null if not set
     */
    public String getHinType() {
        return this.hinType;
    }

    /**
     * Sets the HIN type code.
     *
     * @param hinType String the HIN type code
     */
    public void setHinType(final String hinType) {
        this.hinType = hinType;
    }

    /**
     * Gets the flag indicating whether to remove the identifier during transfer.
     *
     * This flag is used in the integrator system to control whether demographic identifiers
     * should be stripped from the transfer object, typically for privacy or security reasons
     * when sharing data across different facilities.
     *
     * @return boolean true if the identifier should be removed, false otherwise
     */
    public boolean getRemoveId() {
        return this.removeId;
    }

    /**
     * Sets the flag indicating whether to remove the identifier during transfer.
     *
     * @param removeId boolean true to remove the identifier, false to keep it
     */
    public void setRemoveId(final boolean removeId) {
        this.removeId = removeId;
    }

    /**
     * Gets the patient's street address.
     *
     * @return String the patient's street address, or null if not set
     */
    public String getStreetAddress() {
        return this.streetAddress;
    }

    /**
     * Sets the patient's street address.
     *
     * @param streetAddress String the patient's street address
     */
    public void setStreetAddress(final String streetAddress) {
        this.streetAddress = streetAddress;
    }

    /**
     * Gets the patient's primary phone number.
     *
     * @return String the patient's primary phone number, or null if not set
     */
    public String getPhone1() {
        return this.phone1;
    }

    /**
     * Sets the patient's primary phone number.
     *
     * @param phone1 String the patient's primary phone number
     */
    public void setPhone1(final String phone1) {
        this.phone1 = phone1;
    }

    /**
     * Gets the patient's secondary phone number.
     *
     * @return String the patient's secondary phone number, or null if not set
     */
    public String getPhone2() {
        return this.phone2;
    }

    /**
     * Sets the patient's secondary phone number.
     *
     * @param phone2 String the patient's secondary phone number
     */
    public void setPhone2(final String phone2) {
        this.phone2 = phone2;
    }

    /**
     * Gets the start date of the HIN validity period.
     *
     * Provincial health insurance cards have validity periods defined by start and end dates.
     * This date indicates when the health card becomes valid.
     *
     * @return Date the HIN validity start date, or null if not set
     */
    public Date getHinValidStart() {
        return this.hinValidStart;
    }

    /**
     * Sets the start date of the HIN validity period.
     *
     * @param hinValidStart Date the HIN validity start date
     */
    public void setHinValidStart(final Date hinValidStart) {
        this.hinValidStart = hinValidStart;
    }

    /**
     * Gets the end date of the HIN validity period.
     *
     * Provincial health insurance cards have validity periods defined by start and end dates.
     * This date indicates when the health card expires and requires renewal.
     *
     * @return Date the HIN validity end date, or null if not set
     */
    public Date getHinValidEnd() {
        return this.hinValidEnd;
    }

    /**
     * Sets the end date of the HIN validity period.
     *
     * @param hinValidEnd Date the HIN validity end date
     */
    public void setHinValidEnd(final Date hinValidEnd) {
        this.hinValidEnd = hinValidEnd;
    }

    /**
     * Gets the username of the user who last updated this demographic record.
     *
     * This field is part of the audit trail and tracks who made the most recent changes
     * to the demographic information.
     *
     * @return String the username of the last updating user, or null if not set
     */
    public String getLastUpdateUser() {
        return this.lastUpdateUser;
    }

    /**
     * Sets the username of the user who last updated this demographic record.
     *
     * @param lastUpdateUser String the username of the last updating user
     */
    public void setLastUpdateUser(final String lastUpdateUser) {
        this.lastUpdateUser = lastUpdateUser;
    }

    /**
     * Gets the date and time when this demographic record was last updated.
     *
     * This field is part of the audit trail and provides a timestamp for the most recent
     * changes to the demographic information.
     *
     * @return Date the last update timestamp, or null if not set
     */
    public Date getLastUpdateDate() {
        return this.lastUpdateDate;
    }

    /**
     * Sets the date and time when this demographic record was last updated.
     *
     * @param lastUpdateDate Date the last update timestamp
     */
    public void setLastUpdateDate(final Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }
}
