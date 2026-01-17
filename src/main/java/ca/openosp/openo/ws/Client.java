package ca.openosp.openo.ws;

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
 * Client data transfer object for the Health Number Registry (HNR) web service.
 * 
 * <p>This class represents patient demographic and health insurance information used in the
 * CAISI Integrator system for matching and linking clients across multiple healthcare facilities.
 * It contains Protected Health Information (PHI) including Health Insurance Numbers (HIN),
 * personal demographics, and optional patient images.</p>
 * 
 * <p>The HNR web service enables healthcare facilities to share client information while
 * maintaining privacy and security compliance. This class is used as a data container for
 * SOAP-based web service operations including client retrieval, creation, and matching.</p>
 * 
 * <p><strong>Security Considerations:</strong></p>
 * <ul>
 *   <li>Contains PHI (Protected Health Information) - handle according to HIPAA/PIPEDA requirements</li>
 *   <li>Health Insurance Number (HIN) validation is performed server-side</li>
 *   <li>Social Insurance Number (SIN) should be encrypted at rest</li>
 *   <li>Client images may contain sensitive biometric data</li>
 *   <li>All access to client data must be logged for audit trail compliance</li>
 * </ul>
 * 
 * <p><strong>Provincial Health Insurance:</strong></p>
 * <ul>
 *   <li>HIN format and validation rules vary by province (BC, ON, etc.)</li>
 *   <li>HIN validity periods are tracked with start and end dates</li>
 *   <li>HIN version codes indicate card issuance versions</li>
 *   <li>HIN type differentiates between regular, temporary, and other card types</li>
 * </ul>
 * 
 * <p><strong>Client Matching:</strong></p>
 * <p>The linkingId field is used to associate clients across different facilities in the
 * integrator system. Clients with the same linkingId are considered to be the same person
 * registered at different healthcare providers.</p>
 * 
 * <p><strong>Privacy Features:</strong></p>
 * <ul>
 *   <li>hidden flag allows marking clients as hidden from general searches</li>
 *   <li>hiddenChangeDate tracks when the hidden status was last modified</li>
 *   <li>lockbox flag indicates restricted access requiring special privileges</li>
 * </ul>
 * 
 * @see AbstractModel
 * @see Gender
 * @see ca.openosp.openo.caisi_integrator.ws.HnrWs
 * @see ca.openosp.openo.ws.MatchingClientParameters
 * @see ca.openosp.openo.ws.MatchingClientScore
 * @since 2026-01-14
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "client", propOrder = { "birthDate", "city", "firstName", "gender", "hidden", "hiddenChangeDate", "hin", "hinType", "hinValidEnd", "hinValidStart", "hinVersion", "image", "lastName", "linkingId", "lockbox", "province", "sin", "streetAddress", "updated", "updatedBy" })
public class Client extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar birthDate;
    protected String city;
    protected String firstName;
    @XmlSchemaType(name = "string")
    protected Gender gender;
    protected boolean hidden;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar hiddenChangeDate;
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
    protected byte[] image;
    protected String lastName;
    protected Integer linkingId;
    protected boolean lockbox;
    protected String province;
    protected String sin;
    protected String streetAddress;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar updated;
    protected String updatedBy;
    
    /**
     * Gets the client's date of birth.
     * 
     * @return Calendar the client's birth date, or null if not set
     */
    public Calendar getBirthDate() {
        return this.birthDate;
    }
    
    /**
     * Sets the client's date of birth.
     * 
     * @param birthDate Calendar the client's birth date to set
     */
    public void setBirthDate(final Calendar birthDate) {
        this.birthDate = birthDate;
    }
    
    /**
     * Gets the city portion of the client's address.
     * 
     * @return String the city name, or null if not set
     */
    public String getCity() {
        return this.city;
    }
    
    /**
     * Sets the city portion of the client's address.
     * 
     * @param city String the city name to set
     */
    public void setCity(final String city) {
        this.city = city;
    }
    
    /**
     * Gets the client's first name (given name).
     * 
     * @return String the first name, or null if not set
     */
    public String getFirstName() {
        return this.firstName;
    }
    
    /**
     * Sets the client's first name (given name).
     * 
     * @param firstName String the first name to set
     */
    public void setFirstName(final String firstName) {
        this.firstName = firstName;
    }
    
    /**
     * Gets the client's gender identity.
     * 
     * @return Gender the gender (M=Male, F=Female, T=Transgender, O=Other, U=Unknown), or null if not set
     * @see Gender
     */
    public Gender getGender() {
        return this.gender;
    }
    
    /**
     * Sets the client's gender identity.
     * 
     * @param gender Gender the gender to set (M=Male, F=Female, T=Transgender, O=Other, U=Unknown)
     * @see Gender
     */
    public void setGender(final Gender gender) {
        this.gender = gender;
    }
    
    /**
     * Checks if the client is marked as hidden.
     * 
     * <p>Hidden clients are excluded from general searches and listings for privacy reasons.
     * Access to hidden client records typically requires elevated privileges.</p>
     * 
     * @return boolean true if the client is hidden, false otherwise
     * @see #getHiddenChangeDate()
     */
    public boolean isHidden() {
        return this.hidden;
    }
    
    /**
     * Sets whether the client should be marked as hidden.
     * 
     * <p>When hiding or unhiding a client, also update the hiddenChangeDate to track when
     * the status changed for audit trail purposes.</p>
     * 
     * @param hidden boolean true to hide the client, false to make visible
     * @see #setHiddenChangeDate(Calendar)
     */
    public void setHidden(final boolean hidden) {
        this.hidden = hidden;
    }
    
    /**
     * Gets the date and time when the client's hidden status was last changed.
     * 
     * <p>This timestamp provides an audit trail for privacy-related status changes.</p>
     * 
     * @return Calendar the date and time of the last hidden status change, or null if never changed
     * @see #isHidden()
     */
    public Calendar getHiddenChangeDate() {
        return this.hiddenChangeDate;
    }
    
    /**
     * Sets the date and time when the client's hidden status was last changed.
     * 
     * <p>This should be updated whenever the hidden flag is modified to maintain an accurate audit trail.</p>
     * 
     * @param hiddenChangeDate Calendar the date and time to set
     * @see #setHidden(boolean)
     */
    public void setHiddenChangeDate(final Calendar hiddenChangeDate) {
        this.hiddenChangeDate = hiddenChangeDate;
    }
    
    /**
     * Gets the client's Health Insurance Number (HIN).
     * 
     * <p><strong>Security Notice:</strong> This field contains Protected Health Information (PHI).
     * Access must be logged and authorized. The HIN should be encrypted at rest in the database.</p>
     * 
     * <p>The HIN format varies by province (e.g., 10 digits for Ontario, 10 digits for BC).
     * Validation rules are province-specific and should be applied server-side.</p>
     * 
     * @return String the Health Insurance Number, or null if not set
     * @see #getProvince()
     * @see #getHinType()
     * @see #getHinVersion()
     */
    public String getHin() {
        return this.hin;
    }
    
    /**
     * Sets the client's Health Insurance Number (HIN).
     * 
     * <p><strong>Security Notice:</strong> This field contains Protected Health Information (PHI).
     * Ensure the HIN is validated according to provincial rules before setting, and that it will
     * be encrypted at rest in the database.</p>
     * 
     * @param hin String the Health Insurance Number to set
     * @throws ca.openosp.openo.ws.InvalidHinException if the HIN fails provincial validation (server-side)
     * @throws ca.openosp.openo.ws.DuplicateHinException if the HIN is already registered (server-side)
     * @see #setProvince(String)
     * @see #setHinType(String)
     * @see #setHinVersion(String)
     */
    public void setHin(final String hin) {
        this.hin = hin;
    }
    
    /**
     * Gets the type of health insurance card.
     * 
     * <p>The HIN type differentiates between regular provincial health cards, temporary cards,
     * out-of-province coverage, and other special card types. Values vary by province.</p>
     * 
     * @return String the HIN type code, or null if not set
     * @see #getHin()
     */
    public String getHinType() {
        return this.hinType;
    }
    
    /**
     * Sets the type of health insurance card.
     * 
     * @param hinType String the HIN type code to set
     * @see #setHin(String)
     */
    public void setHinType(final String hinType) {
        this.hinType = hinType;
    }
    
    /**
     * Gets the expiration date of the health insurance card.
     * 
     * <p>After this date, the HIN may no longer be valid for billing purposes. Some provinces
     * issue cards with expiration dates while others do not.</p>
     * 
     * @return Calendar the HIN expiration date, or null if not set or no expiration
     * @see #getHinValidStart()
     */
    public Calendar getHinValidEnd() {
        return this.hinValidEnd;
    }
    
    /**
     * Sets the expiration date of the health insurance card.
     * 
     * @param hinValidEnd Calendar the HIN expiration date to set
     * @see #setHinValidStart(Calendar)
     */
    public void setHinValidEnd(final Calendar hinValidEnd) {
        this.hinValidEnd = hinValidEnd;
    }
    
    /**
     * Gets the start date of health insurance card validity.
     * 
     * <p>This is typically the issue date or registration date when coverage begins.</p>
     * 
     * @return Calendar the HIN validity start date, or null if not set
     * @see #getHinValidEnd()
     */
    public Calendar getHinValidStart() {
        return this.hinValidStart;
    }
    
    /**
     * Sets the start date of health insurance card validity.
     * 
     * @param hinValidStart Calendar the HIN validity start date to set
     * @see #setHinValidEnd(Calendar)
     */
    public void setHinValidStart(final Calendar hinValidStart) {
        this.hinValidStart = hinValidStart;
    }
    
    /**
     * Gets the version code of the health insurance card.
     * 
     * <p>The version code typically indicates which physical card issuance this represents
     * (e.g., "1" for first card, "2" for replacement, etc.). This helps differentiate between
     * multiple cards issued for the same HIN.</p>
     * 
     * @return String the HIN version code, or null if not set
     * @see #getHin()
     */
    public String getHinVersion() {
        return this.hinVersion;
    }
    
    /**
     * Sets the version code of the health insurance card.
     * 
     * @param hinVersion String the HIN version code to set
     * @see #setHin(String)
     */
    public void setHinVersion(final String hinVersion) {
        this.hinVersion = hinVersion;
    }
    
    /**
     * Gets the client's photograph or image data.
     * 
     * <p><strong>Security Notice:</strong> This field may contain biometric data (facial photograph)
     * which is considered Protected Health Information (PHI). Access must be logged and authorized.</p>
     * 
     * <p>The image is typically stored as JPEG or PNG binary data. Large images should be resized
     * before storage to minimize bandwidth and storage requirements.</p>
     * 
     * @return byte[] the image data as a byte array, or null if no image is set
     */
    public byte[] getImage() {
        return this.image;
    }
    
    /**
     * Sets the client's photograph or image data.
     * 
     * <p><strong>Security Notice:</strong> This field may contain biometric data (facial photograph).
     * Ensure proper access controls and audit logging are in place.</p>
     * 
     * <p>Images should be validated for file type and size before setting to prevent security
     * vulnerabilities and resource exhaustion.</p>
     * 
     * @param image byte[] the image data to set, typically JPEG or PNG format
     */
    public void setImage(final byte[] image) {
        this.image = image;
    }
    
    /**
     * Gets the client's last name (surname/family name).
     * 
     * @return String the last name, or null if not set
     */
    public String getLastName() {
        return this.lastName;
    }
    
    /**
     * Sets the client's last name (surname/family name).
     * 
     * @param lastName String the last name to set
     */
    public void setLastName(final String lastName) {
        this.lastName = lastName;
    }
    
    /**
     * Gets the linking identifier used to associate this client across multiple facilities.
     * 
     * <p>In the CAISI Integrator system, clients registered at different healthcare facilities
     * can be linked together using this identifier. Clients with the same linkingId represent
     * the same person across different provider organizations.</p>
     * 
     * <p>This enables coordinated care and information sharing while maintaining separate
     * local client records at each facility.</p>
     * 
     * @return Integer the linking ID, or null if this client is not linked to other facilities
     */
    public Integer getLinkingId() {
        return this.linkingId;
    }
    
    /**
     * Sets the linking identifier to associate this client across multiple facilities.
     * 
     * <p>When linking clients, ensure proper consent and privacy compliance. The linkingId
     * should be obtained from the HNR service when creating client matches.</p>
     * 
     * @param linkingId Integer the linking ID to set
     */
    public void setLinkingId(final Integer linkingId) {
        this.linkingId = linkingId;
    }
    
    /**
     * Checks if the client record has lockbox (restricted access) protection.
     * 
     * <p>Lockbox records have enhanced privacy protection and can only be accessed by providers
     * with explicit authorization. This is typically used for sensitive cases including:</p>
     * <ul>
     *   <li>Healthcare workers who are also patients</li>
     *   <li>Clients requiring enhanced privacy due to safety concerns</li>
     *   <li>High-profile or sensitive cases</li>
     * </ul>
     * 
     * <p>Access to lockbox records generates additional audit log entries and may require
     * elevated privileges beyond normal client access.</p>
     * 
     * @return boolean true if lockbox protection is enabled, false otherwise
     */
    public boolean isLockbox() {
        return this.lockbox;
    }
    
    /**
     * Sets whether the client record should have lockbox (restricted access) protection.
     * 
     * <p>Enabling lockbox protection should be done carefully and only when enhanced privacy
     * is justified. Access to lockbox records requires special privileges.</p>
     * 
     * @param lockbox boolean true to enable lockbox protection, false to disable
     */
    public void setLockbox(final boolean lockbox) {
        this.lockbox = lockbox;
    }
    
    /**
     * Gets the province or territory code for the client's health insurance.
     * 
     * <p>This determines which provincial health insurance rules apply, including HIN format
     * validation, billing codes, and coverage rules. Common values include:</p>
     * <ul>
     *   <li>BC - British Columbia</li>
     *   <li>ON - Ontario</li>
     *   <li>AB - Alberta</li>
     *   <li>SK - Saskatchewan</li>
     *   <li>MB - Manitoba</li>
     *   <li>QC - Quebec</li>
     *   <li>And other Canadian provinces/territories</li>
     * </ul>
     * 
     * @return String the two-letter province/territory code, or null if not set
     * @see #getHin()
     */
    public String getProvince() {
        return this.province;
    }
    
    /**
     * Sets the province or territory code for the client's health insurance.
     * 
     * <p>The province code must match the province that issued the Health Insurance Number.
     * This affects HIN validation rules and billing procedures.</p>
     * 
     * @param province String the two-letter province/territory code to set (e.g., "BC", "ON")
     * @see #setHin(String)
     */
    public void setProvince(final String province) {
        this.province = province;
    }
    
    /**
     * Gets the client's Social Insurance Number (SIN).
     * 
     * <p><strong>Security Notice:</strong> This field contains highly sensitive Protected Health
     * Information (PHI). The SIN must be encrypted at rest and access must be strictly controlled
     * and logged. SIN should only be collected when absolutely necessary for legal/administrative
     * purposes.</p>
     * 
     * <p>In most healthcare contexts, the Health Insurance Number (HIN) should be used instead
     * of the SIN for identification purposes.</p>
     * 
     * @return String the Social Insurance Number, or null if not set
     * @see #getHin()
     */
    public String getSin() {
        return this.sin;
    }
    
    /**
     * Sets the client's Social Insurance Number (SIN).
     * 
     * <p><strong>Security Notice:</strong> This field contains highly sensitive Protected Health
     * Information (PHI). Ensure the SIN will be encrypted at rest and that collection is legally
     * justified. Validate the SIN format (9 digits) before setting.</p>
     * 
     * <p>Collection of SIN should be minimized and only done when legally required.</p>
     * 
     * @param sin String the Social Insurance Number to set (9 digits)
     * @see #setHin(String)
     */
    public void setSin(final String sin) {
        this.sin = sin;
    }
    
    /**
     * Gets the street address portion of the client's address.
     * 
     * <p>This typically includes the street number, street name, and unit/apartment number
     * if applicable. The full address is formed by combining this with city and province.</p>
     * 
     * @return String the street address, or null if not set
     * @see #getCity()
     * @see #getProvince()
     */
    public String getStreetAddress() {
        return this.streetAddress;
    }
    
    /**
     * Sets the street address portion of the client's address.
     * 
     * @param streetAddress String the street address to set
     * @see #setCity(String)
     * @see #setProvince(String)
     */
    public void setStreetAddress(final String streetAddress) {
        this.streetAddress = streetAddress;
    }
    
    /**
     * Gets the date and time when this client record was last updated.
     * 
     * <p>This timestamp is part of the audit trail for tracking changes to client information.
     * It should be automatically updated whenever any client data is modified.</p>
     * 
     * @return Calendar the last update timestamp, or null if never updated
     * @see #getUpdatedBy()
     */
    public Calendar getUpdated() {
        return this.updated;
    }
    
    /**
     * Sets the date and time when this client record was last updated.
     * 
     * <p>This should be set to the current timestamp whenever client data is modified
     * to maintain an accurate audit trail.</p>
     * 
     * @param updated Calendar the update timestamp to set
     * @see #setUpdatedBy(String)
     */
    public void setUpdated(final Calendar updated) {
        this.updated = updated;
    }
    
    /**
     * Gets the identifier of the user who last updated this client record.
     * 
     * <p>This is typically the provider number or username of the healthcare provider who
     * made the most recent changes. This information is part of the audit trail required
     * for PHI access compliance.</p>
     * 
     * @return String the user identifier of who last updated this record, or null if unknown
     * @see #getUpdated()
     */
    public String getUpdatedBy() {
        return this.updatedBy;
    }
    
    /**
     * Sets the identifier of the user who last updated this client record.
     * 
     * <p>This should be set to the current user's provider number or username whenever
     * client data is modified to maintain an accurate audit trail.</p>
     * 
     * @param updatedBy String the user identifier to set
     * @see #setUpdated(Calendar)
     */
    public void setUpdatedBy(final String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
