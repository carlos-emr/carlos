package ca.openosp.openo.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Represents a cached healthcare program in the CAISI Integrator web service.
 *
 * <p>This class is used to store and transfer program information across integrated
 * EMR systems through the CAISI (Client Access to Integrated Services and Information)
 * Integrator. Programs in the healthcare context represent community services, treatment
 * programs, or care facilities that may be referred to or associated with patient care.</p>
 *
 * <p>The cached program includes comprehensive program details such as contact information,
 * service characteristics (mental health, physical health, housing support, etc.), eligibility
 * criteria (age ranges, gender requirements), and facility affiliation information.</p>
 *
 * <p>This is a JAXB-annotated class for XML serialization/deserialization in web service
 * communications between different OpenO EMR installations through the Integrator system.</p>
 *
 * @since 2026-01-23
 * @see AbstractModel
 * @see FacilityIdIntegerCompositePk
 * @see Gender
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cachedProgram", propOrder = { "abstinenceSupport", "address", "alcohol", "bedProgramAffiliated", "description", "email", "emergencyNumber", "facilityIdIntegerCompositePk", "fax", "firstNation", "gender", "housing", "maxAge", "mentalHealth", "minAge", "name", "phone", "physicalHealth", "status", "type", "url" })
public class CachedProgram extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected String abstinenceSupport;
    protected String address;
    protected boolean alcohol;
    protected boolean bedProgramAffiliated;
    protected String description;
    protected String email;
    protected String emergencyNumber;
    protected FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    protected String fax;
    protected boolean firstNation;
    @XmlSchemaType(name = "string")
    protected Gender gender;
    protected boolean housing;
    protected int maxAge;
    protected boolean mentalHealth;
    protected int minAge;
    protected String name;
    protected String phone;
    protected boolean physicalHealth;
    protected String status;
    protected String type;
    protected String url;

    /**
     * Gets the abstinence support information for this program.
     *
     * @return String the abstinence support details, or null if not specified
     */
    public String getAbstinenceSupport() {
        return this.abstinenceSupport;
    }

    /**
     * Sets the abstinence support information for this program.
     *
     * @param abstinenceSupport String the abstinence support details to set
     */
    public void setAbstinenceSupport(final String abstinenceSupport) {
        this.abstinenceSupport = abstinenceSupport;
    }

    /**
     * Gets the physical address of the program location.
     *
     * @return String the program address, or null if not specified
     */
    public String getAddress() {
        return this.address;
    }

    /**
     * Sets the physical address of the program location.
     *
     * @param address String the program address to set
     */
    public void setAddress(final String address) {
        this.address = address;
    }

    /**
     * Checks if this program provides alcohol-related services or support.
     *
     * @return boolean true if the program provides alcohol services, false otherwise
     */
    public boolean isAlcohol() {
        return this.alcohol;
    }

    /**
     * Sets whether this program provides alcohol-related services or support.
     *
     * @param alcohol boolean true if the program provides alcohol services
     */
    public void setAlcohol(final boolean alcohol) {
        this.alcohol = alcohol;
    }

    /**
     * Checks if this program is affiliated with a bed program.
     *
     * @return boolean true if the program is affiliated with a bed program, false otherwise
     */
    public boolean isBedProgramAffiliated() {
        return this.bedProgramAffiliated;
    }

    /**
     * Sets whether this program is affiliated with a bed program.
     *
     * @param bedProgramAffiliated boolean true if the program is affiliated with a bed program
     */
    public void setBedProgramAffiliated(final boolean bedProgramAffiliated) {
        this.bedProgramAffiliated = bedProgramAffiliated;
    }
    
    /**
     * Gets the description of the program.
     *
     * @return String the program description, or null if not specified
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Sets the description of the program.
     *
     * @param description String the program description to set
     */
    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     * Gets the email contact address for the program.
     *
     * @return String the program email address, or null if not specified
     */
    public String getEmail() {
        return this.email;
    }

    /**
     * Sets the email contact address for the program.
     *
     * @param email String the program email address to set
     */
    public void setEmail(final String email) {
        this.email = email;
    }

    /**
     * Gets the emergency contact phone number for the program.
     *
     * @return String the emergency phone number, or null if not specified
     */
    public String getEmergencyNumber() {
        return this.emergencyNumber;
    }

    /**
     * Sets the emergency contact phone number for the program.
     *
     * @param emergencyNumber String the emergency phone number to set
     */
    public void setEmergencyNumber(final String emergencyNumber) {
        this.emergencyNumber = emergencyNumber;
    }

    /**
     * Gets the facility identifier composite primary key for this program.
     *
     * <p>This composite key uniquely identifies the program within the integrator
     * system by combining the CAISI item ID and the integrator facility ID.</p>
     *
     * @return FacilityIdIntegerCompositePk the facility identifier composite key, or null if not set
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return this.facilityIdIntegerCompositePk;
    }

    /**
     * Sets the facility identifier composite primary key for this program.
     *
     * @param facilityIdIntegerCompositePk FacilityIdIntegerCompositePk the facility identifier composite key to set
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        this.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
    }
    
    /**
     * Gets the fax number for the program.
     *
     * @return String the program fax number, or null if not specified
     */
    public String getFax() {
        return this.fax;
    }

    /**
     * Sets the fax number for the program.
     *
     * @param fax String the program fax number to set
     */
    public void setFax(final String fax) {
        this.fax = fax;
    }

    /**
     * Checks if this program is designated for First Nations populations.
     *
     * @return boolean true if the program serves First Nations populations, false otherwise
     */
    public boolean isFirstNation() {
        return this.firstNation;
    }

    /**
     * Sets whether this program is designated for First Nations populations.
     *
     * @param firstNation boolean true if the program serves First Nations populations
     */
    public void setFirstNation(final boolean firstNation) {
        this.firstNation = firstNation;
    }

    /**
     * Gets the gender eligibility requirement for the program.
     *
     * @return Gender the gender requirement (M, F, T, O, U), or null if not specified
     */
    public Gender getGender() {
        return this.gender;
    }

    /**
     * Sets the gender eligibility requirement for the program.
     *
     * @param gender Gender the gender requirement to set (M, F, T, O, U)
     */
    public void setGender(final Gender gender) {
        this.gender = gender;
    }

    /**
     * Checks if this program provides housing support or services.
     *
     * @return boolean true if the program provides housing services, false otherwise
     */
    public boolean isHousing() {
        return this.housing;
    }

    /**
     * Sets whether this program provides housing support or services.
     *
     * @param housing boolean true if the program provides housing services
     */
    public void setHousing(final boolean housing) {
        this.housing = housing;
    }
    
    /**
     * Gets the maximum age eligibility requirement for the program.
     *
     * @return int the maximum age in years, or 0 if not specified
     */
    public int getMaxAge() {
        return this.maxAge;
    }

    /**
     * Sets the maximum age eligibility requirement for the program.
     *
     * @param maxAge int the maximum age in years to set
     */
    public void setMaxAge(final int maxAge) {
        this.maxAge = maxAge;
    }

    /**
     * Checks if this program provides mental health services or support.
     *
     * @return boolean true if the program provides mental health services, false otherwise
     */
    public boolean isMentalHealth() {
        return this.mentalHealth;
    }

    /**
     * Sets whether this program provides mental health services or support.
     *
     * @param mentalHealth boolean true if the program provides mental health services
     */
    public void setMentalHealth(final boolean mentalHealth) {
        this.mentalHealth = mentalHealth;
    }

    /**
     * Gets the minimum age eligibility requirement for the program.
     *
     * @return int the minimum age in years, or 0 if not specified
     */
    public int getMinAge() {
        return this.minAge;
    }

    /**
     * Sets the minimum age eligibility requirement for the program.
     *
     * @param minAge int the minimum age in years to set
     */
    public void setMinAge(final int minAge) {
        this.minAge = minAge;
    }

    /**
     * Gets the name of the program.
     *
     * @return String the program name, or null if not specified
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the program.
     *
     * @param name String the program name to set
     */
    public void setName(final String name) {
        this.name = name;
    }
    
    /**
     * Gets the primary phone contact number for the program.
     *
     * @return String the program phone number, or null if not specified
     */
    public String getPhone() {
        return this.phone;
    }

    /**
     * Sets the primary phone contact number for the program.
     *
     * @param phone String the program phone number to set
     */
    public void setPhone(final String phone) {
        this.phone = phone;
    }

    /**
     * Checks if this program provides physical health services or support.
     *
     * @return boolean true if the program provides physical health services, false otherwise
     */
    public boolean isPhysicalHealth() {
        return this.physicalHealth;
    }

    /**
     * Sets whether this program provides physical health services or support.
     *
     * @param physicalHealth boolean true if the program provides physical health services
     */
    public void setPhysicalHealth(final boolean physicalHealth) {
        this.physicalHealth = physicalHealth;
    }

    /**
     * Gets the current operational status of the program.
     *
     * @return String the program status (e.g., "active", "inactive"), or null if not specified
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * Sets the current operational status of the program.
     *
     * @param status String the program status to set
     */
    public void setStatus(final String status) {
        this.status = status;
    }

    /**
     * Gets the type classification of the program.
     *
     * @return String the program type, or null if not specified
     */
    public String getType() {
        return this.type;
    }

    /**
     * Sets the type classification of the program.
     *
     * @param type String the program type to set
     */
    public void setType(final String type) {
        this.type = type;
    }

    /**
     * Gets the website URL for the program.
     *
     * @return String the program website URL, or null if not specified
     */
    public String getUrl() {
        return this.url;
    }

    /**
     * Sets the website URL for the program.
     *
     * @param url String the program website URL to set
     */
    public void setUrl(final String url) {
        this.url = url;
    }
}
