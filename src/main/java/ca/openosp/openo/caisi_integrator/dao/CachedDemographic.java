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
import ca.openosp.openo.caisi_integrator.util.MiscUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * Cached patient demographic data entity for the CAISI (Client Access to Integrated Services and Information) Integrator.
 *
 * This JPA entity represents a cached copy of patient demographic information retrieved from remote healthcare
 * facilities within the CAISI integrator network. The cache enables efficient access to patient data across
 * multiple EMR installations without requiring real-time queries to remote systems.
 *
 * <p>This entity contains Protected Health Information (PHI) including:
 * <ul>
 *   <li>Patient identification (name, date of birth, gender)</li>
 *   <li>Health Insurance Number (HIN) with validity periods</li>
 *   <li>Social Insurance Number (SIN)</li>
 *   <li>Contact information (address, phone numbers)</li>
 *   <li>Geographic location (province, city)</li>
 * </ul>
 *
 * <p>The entity is enhanced by Apache OpenJPA for persistence management, implementing the {@link PersistenceCapable}
 * interface which provides automatic field-level tracking, lazy loading, and state management. The enhancement
 * process generates additional methods (prefixed with "pc") that handle low-level persistence operations.
 *
 * <p><strong>Security Considerations:</strong>
 * <ul>
 *   <li>All access to this entity must be authorized through {@code SecurityInfoManager}</li>
 *   <li>PHI data must never be logged or exposed in error messages</li>
 *   <li>HIN and SIN values are stored in lowercase for consistent matching</li>
 *   <li>Audit trail maintained via {@code lastUpdateUser} and {@code lastUpdateDate}</li>
 * </ul>
 *
 * <p><strong>Data Normalization:</strong>
 * Several fields are automatically normalized on input:
 * <ul>
 *   <li>HIN, SIN, province, city: converted to lowercase and trimmed</li>
 *   <li>String fields: trimmed with null conversion for empty strings</li>
 *   <li>Dates: stored as temporal types (DATE or TIMESTAMP)</li>
 * </ul>
 *
 * @see AbstractModel
 * @see FacilityIdIntegerCompositePk
 * @see PersistenceCapable
 * @since 2026-01-24
 */
@Entity
public class CachedDemographic extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedDemographic>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityDemographicPk;
    private String firstName;
    private String lastName;
    @Temporal(TemporalType.DATE)
    private Date birthDate;
    @Enumerated(EnumType.STRING)
    @Column(length = 1)
    private Gender gender;
    @Column(length = 32)
    private String hin;
    @Column(length = 32)
    private String hinType;
    @Column(length = 8)
    private String hinVersion;
    @Temporal(TemporalType.DATE)
    private Date hinValidStart;
    @Temporal(TemporalType.DATE)
    private Date hinValidEnd;
    @Column(length = 32)
    private String sin;
    @Column(length = 4)
    private String province;
    @Column(length = 128)
    private String city;
    @Column(length = 16)
    private String caisiProviderId;
    @Column(length = 32)
    private String idHash;
    @Column(length = 128)
    private String streetAddress;
    @Column(length = 64)
    private String phone1;
    @Column(length = 64)
    private String phone2;
    @Column(length = 16)
    private String lastUpdateUser;
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastUpdateDate;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Creates a new CachedDemographic instance with default null values.
     *
     * This default constructor initializes all demographic fields to null. The composite primary key
     * and state manager are left uninitialized and will be set by the persistence framework.
     */
    public CachedDemographic() {
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
        this.caisiProviderId = null;
        this.idHash = null;
    }

    /**
     * Retrieves the composite primary key for this cached demographic record.
     *
     * The composite key contains both the facility ID (identifying the source healthcare facility)
     * and the CAISI item ID (the demographic ID at that facility). Together these uniquely identify
     * a patient record within the integrator cache.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key, or null if not yet set
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityDemographicPk(this);
    }

    /**
     * Sets the composite primary key for this cached demographic record.
     *
     * @param facilityDemographicPk FacilityIdIntegerCompositePk the composite key containing facility ID and CAISI item ID
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityDemographicPk) {
        pcSetfacilityDemographicPk(this, facilityDemographicPk);
    }

    /**
     * Retrieves the patient's first name.
     *
     * @return String the patient's first name, or null if not set
     */
    public String getFirstName() {
        return pcGetfirstName(this);
    }

    /**
     * Sets the patient's first name.
     *
     * The value is automatically trimmed and converted to null if empty or whitespace-only.
     *
     * @param firstName String the patient's first name
     */
    public void setFirstName(final String firstName) {
        pcSetfirstName(this, StringUtils.trimToNull(firstName));
    }

    /**
     * Retrieves the patient's last name (surname).
     *
     * @return String the patient's last name, or null if not set
     */
    public String getLastName() {
        return pcGetlastName(this);
    }

    /**
     * Sets the patient's last name (surname).
     *
     * The value is automatically trimmed and converted to null if empty or whitespace-only.
     *
     * @param lastName String the patient's last name
     */
    public void setLastName(final String lastName) {
        pcSetlastName(this, StringUtils.trimToNull(lastName));
    }

    /**
     * Retrieves the patient's date of birth.
     *
     * The date is stored with DATE temporal type (time component not stored).
     *
     * @return Date the patient's birth date, or null if not set
     */
    public Date getBirthDate() {
        return pcGetbirthDate(this);
    }

    /**
     * Sets the patient's date of birth.
     *
     * Only the date component is stored; time information is discarded.
     *
     * @param birthDate Date the patient's birth date
     */
    public void setBirthDate(final Date birthDate) {
        pcSetbirthDate(this, birthDate);
    }

    /**
     * Retrieves the patient's gender.
     *
     * @return Gender the patient's gender (M, F, T, O, or U), or null if not set
     * @see Gender
     */
    public Gender getGender() {
        return pcGetgender(this);
    }

    /**
     * Sets the patient's gender.
     *
     * @param gender Gender the patient's gender (M, F, T, O, or U)
     * @see Gender
     */
    public void setGender(final Gender gender) {
        pcSetgender(this, gender);
    }

    /**
     * Retrieves the patient's Health Insurance Number (HIN).
     *
     * The HIN is stored in lowercase format for consistent matching across systems.
     * Maximum length is 32 characters.
     *
     * @return String the patient's HIN in lowercase, or null if not set
     */
    public String getHin() {
        return pcGethin(this);
    }

    /**
     * Sets the patient's Health Insurance Number (HIN).
     *
     * The value is automatically converted to lowercase, trimmed, and set to null if empty.
     * This normalization ensures consistent matching across different EMR systems.
     *
     * @param hin String the patient's HIN (will be normalized to lowercase)
     */
    public void setHin(final String hin) {
        pcSethin(this, MiscUtils.trimToNullLowerCase(hin));
    }

    /**
     * Retrieves the HIN version code.
     *
     * Some provinces use version codes to track HIN card versions or renewals.
     * Maximum length is 8 characters.
     *
     * @return String the HIN version code, or null if not applicable
     */
    public String getHinVersion() {
        return pcGethinVersion(this);
    }

    /**
     * Sets the HIN version code.
     *
     * The value is automatically trimmed and converted to null if empty or whitespace-only.
     *
     * @param hinVersion String the HIN version code
     */
    public void setHinVersion(final String hinVersion) {
        pcSethinVersion(this, StringUtils.trimToNull(hinVersion));
    }

    /**
     * Retrieves the patient's Social Insurance Number (SIN).
     *
     * The SIN is stored in lowercase format for consistent matching. Maximum length is 32 characters.
     * <strong>Note:</strong> SIN collection should be limited to cases where legally required.
     *
     * @return String the patient's SIN in lowercase, or null if not set
     */
    public String getSin() {
        return pcGetsin(this);
    }

    /**
     * Sets the patient's Social Insurance Number (SIN).
     *
     * The value is automatically converted to lowercase, trimmed, and set to null if empty.
     *
     * @param sin String the patient's SIN (will be normalized to lowercase)
     */
    public void setSin(final String sin) {
        pcSetsin(this, MiscUtils.trimToNullLowerCase(sin));
    }

    /**
     * Retrieves the patient's province or territory code.
     *
     * Typically a 2-character provincial code (e.g., "on", "bc", "ab"). Maximum length is 4 characters.
     * Stored in lowercase for consistent matching.
     *
     * @return String the province/territory code in lowercase, or null if not set
     */
    public String getProvince() {
        return pcGetprovince(this);
    }

    /**
     * Sets the patient's province or territory code.
     *
     * The value is automatically converted to lowercase, trimmed, and set to null if empty.
     *
     * @param province String the province/territory code (will be normalized to lowercase)
     */
    public void setProvince(final String province) {
        pcSetprovince(this, MiscUtils.trimToNullLowerCase(province));
    }

    /**
     * Retrieves the patient's city of residence.
     *
     * Maximum length is 128 characters. Stored in lowercase for consistent matching.
     *
     * @return String the city name in lowercase, or null if not set
     */
    public String getCity() {
        return pcGetcity(this);
    }

    /**
     * Sets the patient's city of residence.
     *
     * The value is automatically converted to lowercase, trimmed, and set to null if empty.
     *
     * @param city String the city name (will be normalized to lowercase)
     */
    public void setCity(final String city) {
        pcSetcity(this, MiscUtils.trimToNullLowerCase(city));
    }

    /**
     * Retrieves the HIN type indicator.
     *
     * Different provinces may use different HIN types or categories. Maximum length is 32 characters.
     *
     * @return String the HIN type, or null if not applicable
     */
    public String getHinType() {
        return pcGethinType(this);
    }

    /**
     * Sets the HIN type indicator.
     *
     * The value is automatically trimmed and converted to null if empty or whitespace-only.
     *
     * @param hinType String the HIN type
     */
    public void setHinType(final String hinType) {
        pcSethinType(this, StringUtils.trimToNull(hinType));
    }

    /**
     * Retrieves the CAISI provider identifier.
     *
     * This is the provider ID from the CAISI system associated with this demographic record.
     * Maximum length is 16 characters.
     *
     * @return String the CAISI provider ID, or null if not set
     */
    public String getCaisiProviderId() {
        return pcGetcaisiProviderId(this);
    }

    /**
     * Sets the CAISI provider identifier.
     *
     * The value is automatically trimmed and converted to null if empty or whitespace-only.
     *
     * @param caisiProviderId String the CAISI provider ID
     */
    public void setCaisiProviderId(final String caisiProviderId) {
        pcSetcaisiProviderId(this, StringUtils.trimToNull(caisiProviderId));
    }

    /**
     * Retrieves the identifier hash for privacy-preserving record matching.
     *
     * This hash may be used for matching records across facilities without exposing actual identifiers.
     * Maximum length is 32 characters.
     *
     * @return String the ID hash, or null if not set
     */
    public String getIdHash() {
        return pcGetidHash(this);
    }

    /**
     * Sets the identifier hash for privacy-preserving record matching.
     *
     * The value is automatically trimmed and converted to null if empty or whitespace-only.
     *
     * @param idHash String the ID hash value
     */
    public void setIdHash(final String idHash) {
        pcSetidHash(this, StringUtils.trimToNull(idHash));
    }

    /**
     * Retrieves the patient's street address.
     *
     * Maximum length is 128 characters. This field is not normalized to lowercase.
     *
     * @return String the street address, or null if not set
     */
    public String getStreetAddress() {
        return pcGetstreetAddress(this);
    }

    /**
     * Sets the patient's street address.
     *
     * @param streetAddress String the street address
     */
    public void setStreetAddress(final String streetAddress) {
        pcSetstreetAddress(this, streetAddress);
    }

    /**
     * Retrieves the patient's primary phone number.
     *
     * Maximum length is 64 characters. No specific format is enforced.
     *
     * @return String the primary phone number, or null if not set
     */
    public String getPhone1() {
        return pcGetphone1(this);
    }

    /**
     * Sets the patient's primary phone number.
     *
     * @param phone1 String the primary phone number
     */
    public void setPhone1(final String phone1) {
        pcSetphone1(this, phone1);
    }

    /**
     * Retrieves the patient's secondary phone number.
     *
     * Maximum length is 64 characters. No specific format is enforced.
     *
     * @return String the secondary phone number, or null if not set
     */
    public String getPhone2() {
        return pcGetphone2(this);
    }

    /**
     * Sets the patient's secondary phone number.
     *
     * @param phone2 String the secondary phone number
     */
    public void setPhone2(final String phone2) {
        pcSetphone2(this, phone2);
    }

    /**
     * Retrieves the HIN validity start date.
     *
     * Indicates when the patient's health insurance coverage begins or when the current HIN became valid.
     *
     * @return Date the HIN validity start date, or null if not set
     */
    public Date getHinValidStart() {
        return pcGethinValidStart(this);
    }

    /**
     * Sets the HIN validity start date.
     *
     * @param hinValidStart Date the HIN validity start date
     */
    public void setHinValidStart(final Date hinValidStart) {
        pcSethinValidStart(this, hinValidStart);
    }

    /**
     * Retrieves the HIN validity end date.
     *
     * Indicates when the patient's health insurance coverage expires or when the current HIN expires.
     *
     * @return Date the HIN validity end date, or null if not set
     */
    public Date getHinValidEnd() {
        return pcGethinValidEnd(this);
    }

    /**
     * Sets the HIN validity end date.
     *
     * @param hinValidEnd Date the HIN validity end date
     */
    public void setHinValidEnd(final Date hinValidEnd) {
        pcSethinValidEnd(this, hinValidEnd);
    }

    /**
     * Compares this cached demographic to another based on CAISI item ID.
     *
     * The comparison is performed using the numeric CAISI item ID from the composite primary key.
     * This provides a natural ordering for cached demographic records.
     *
     * @param o CachedDemographic the other cached demographic to compare to
     * @return int negative if this record's ID is less, zero if equal, positive if greater
     */
    @Override
    public int compareTo(final CachedDemographic o) {
        return pcGetfacilityDemographicPk(this).getCaisiItemId() - pcGetfacilityDemographicPk(o).getCaisiItemId();
    }

    /**
     * Retrieves the entity's primary key identifier.
     *
     * This is an alias for {@link #getFacilityIdIntegerCompositePk()} required by the
     * AbstractModel base class.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityDemographicPk(this);
    }

    /**
     * Retrieves the username of the user who last updated this record.
     *
     * This field is part of the audit trail for PHI access and modifications.
     * Maximum length is 16 characters.
     *
     * @return String the username of the last updating user, or null if not set
     */
    public String getLastUpdateUser() {
        return pcGetlastUpdateUser(this);
    }

    /**
     * Sets the username of the user who last updated this record.
     *
     * @param lastUpdateUser String the username of the updating user
     */
    public void setLastUpdateUser(final String lastUpdateUser) {
        pcSetlastUpdateUser(this, lastUpdateUser);
    }

    /**
     * Retrieves the timestamp of the last update to this record.
     *
     * This field is part of the audit trail for PHI access and modifications.
     * Stored with TIMESTAMP temporal type (includes date and time).
     *
     * @return Date the last update timestamp, or null if not set
     */
    public Date getLastUpdateDate() {
        return pcGetlastUpdateDate(this);
    }

    /**
     * Sets the timestamp of the last update to this record.
     *
     * @param lastUpdateDate Date the last update timestamp
     */
    public void setLastUpdateDate(final Date lastUpdateDate) {
        pcSetlastUpdateDate(this, lastUpdateDate);
    }

    /**
     * Returns the OpenJPA enhancement contract version.
     *
     * This method is part of the PersistenceCapable contract and indicates which version
     * of the OpenJPA enhancement specification this class implements.
     *
     * @return int the enhancement contract version (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -3212280391303991557L;
        CachedDemographic.pcFieldNames = new String[] { "birthDate", "caisiProviderId", "city", "facilityDemographicPk", "firstName", "gender", "hin", "hinType", "hinValidEnd", "hinValidStart", "hinVersion", "idHash", "lastName", "lastUpdateDate", "lastUpdateUser", "phone1", "phone2", "province", "sin", "streetAddress" };
        CachedDemographic.pcFieldTypes = new Class[] { (CachedDemographic.class$Ljava$util$Date != null) ? CachedDemographic.class$Ljava$util$Date : (CachedDemographic.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender != null) ? CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender : (CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographic$Gender")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$util$Date != null) ? CachedDemographic.class$Ljava$util$Date : (CachedDemographic.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographic.class$Ljava$util$Date != null) ? CachedDemographic.class$Ljava$util$Date : (CachedDemographic.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$util$Date != null) ? CachedDemographic.class$Ljava$util$Date : (CachedDemographic.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographic.class$Ljava$lang$String != null) ? CachedDemographic.class$Ljava$lang$String : (CachedDemographic.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedDemographic.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic != null) ? CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic : (CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographic")), CachedDemographic.pcFieldNames, CachedDemographic.pcFieldTypes, CachedDemographic.pcFieldFlags, CachedDemographic.pcPCSuperclass, "CachedDemographic", (PersistenceCapable)new CachedDemographic());
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
        this.birthDate = null;
        this.caisiProviderId = null;
        this.city = null;
        this.facilityDemographicPk = null;
        this.firstName = null;
        this.gender = null;
        this.hin = null;
        this.hinType = null;
        this.hinValidEnd = null;
        this.hinValidStart = null;
        this.hinVersion = null;
        this.idHash = null;
        this.lastName = null;
        this.lastUpdateDate = null;
        this.lastUpdateUser = null;
        this.phone1 = null;
        this.phone2 = null;
        this.province = null;
        this.sin = null;
        this.streetAddress = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographic cachedDemographic = new CachedDemographic();
        if (b) {
            cachedDemographic.pcClearFields();
        }
        cachedDemographic.pcStateManager = pcStateManager;
        cachedDemographic.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographic;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographic cachedDemographic = new CachedDemographic();
        if (b) {
            cachedDemographic.pcClearFields();
        }
        cachedDemographic.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographic;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 20;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographic.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.birthDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.caisiProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.city = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.facilityDemographicPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.firstName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.gender = (Gender)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.hin = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.hinType = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.hinValidEnd = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.hinValidStart = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 10: {
                this.hinVersion = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 11: {
                this.idHash = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 12: {
                this.lastName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 13: {
                this.lastUpdateDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 14: {
                this.lastUpdateUser = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 15: {
                this.phone1 = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 16: {
                this.phone2 = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 17: {
                this.province = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 18: {
                this.sin = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 19: {
                this.streetAddress = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedDemographic.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.birthDate);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.caisiProviderId);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.city);
                return;
            }
            case 3: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityDemographicPk);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.firstName);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.gender);
                return;
            }
            case 6: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.hin);
                return;
            }
            case 7: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.hinType);
                return;
            }
            case 8: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.hinValidEnd);
                return;
            }
            case 9: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.hinValidStart);
                return;
            }
            case 10: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.hinVersion);
                return;
            }
            case 11: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.idHash);
                return;
            }
            case 12: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.lastName);
                return;
            }
            case 13: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.lastUpdateDate);
                return;
            }
            case 14: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.lastUpdateUser);
                return;
            }
            case 15: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.phone1);
                return;
            }
            case 16: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.phone2);
                return;
            }
            case 17: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.province);
                return;
            }
            case 18: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.sin);
                return;
            }
            case 19: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.streetAddress);
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
    
    protected void pcCopyField(final CachedDemographic cachedDemographic, final int n) {
        final int n2 = n - CachedDemographic.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.birthDate = cachedDemographic.birthDate;
                return;
            }
            case 1: {
                this.caisiProviderId = cachedDemographic.caisiProviderId;
                return;
            }
            case 2: {
                this.city = cachedDemographic.city;
                return;
            }
            case 3: {
                this.facilityDemographicPk = cachedDemographic.facilityDemographicPk;
                return;
            }
            case 4: {
                this.firstName = cachedDemographic.firstName;
                return;
            }
            case 5: {
                this.gender = cachedDemographic.gender;
                return;
            }
            case 6: {
                this.hin = cachedDemographic.hin;
                return;
            }
            case 7: {
                this.hinType = cachedDemographic.hinType;
                return;
            }
            case 8: {
                this.hinValidEnd = cachedDemographic.hinValidEnd;
                return;
            }
            case 9: {
                this.hinValidStart = cachedDemographic.hinValidStart;
                return;
            }
            case 10: {
                this.hinVersion = cachedDemographic.hinVersion;
                return;
            }
            case 11: {
                this.idHash = cachedDemographic.idHash;
                return;
            }
            case 12: {
                this.lastName = cachedDemographic.lastName;
                return;
            }
            case 13: {
                this.lastUpdateDate = cachedDemographic.lastUpdateDate;
                return;
            }
            case 14: {
                this.lastUpdateUser = cachedDemographic.lastUpdateUser;
                return;
            }
            case 15: {
                this.phone1 = cachedDemographic.phone1;
                return;
            }
            case 16: {
                this.phone2 = cachedDemographic.phone2;
                return;
            }
            case 17: {
                this.province = cachedDemographic.province;
                return;
            }
            case 18: {
                this.sin = cachedDemographic.sin;
                return;
            }
            case 19: {
                this.streetAddress = cachedDemographic.streetAddress;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographic cachedDemographic = (CachedDemographic)o;
        if (cachedDemographic.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographic, array[i]);
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
        fieldConsumer.storeObjectField(3 + CachedDemographic.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityDemographicPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographic\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic != null) ? CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic : (CachedDemographic.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographic")), (Object)this.facilityDemographicPk);
    }
    
    private static final Date pcGetbirthDate(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.birthDate;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 0);
        return cachedDemographic.birthDate;
    }
    
    private static final void pcSetbirthDate(final CachedDemographic cachedDemographic, final Date birthDate) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.birthDate = birthDate;
            return;
        }
        cachedDemographic.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 0, (Object)cachedDemographic.birthDate, (Object)birthDate, 0);
    }
    
    private static final String pcGetcaisiProviderId(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.caisiProviderId;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 1);
        return cachedDemographic.caisiProviderId;
    }
    
    private static final void pcSetcaisiProviderId(final CachedDemographic cachedDemographic, final String caisiProviderId) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.caisiProviderId = caisiProviderId;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 1, cachedDemographic.caisiProviderId, caisiProviderId, 0);
    }
    
    private static final String pcGetcity(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.city;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 2);
        return cachedDemographic.city;
    }
    
    private static final void pcSetcity(final CachedDemographic cachedDemographic, final String city) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.city = city;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 2, cachedDemographic.city, city, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityDemographicPk(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.facilityDemographicPk;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 3);
        return cachedDemographic.facilityDemographicPk;
    }
    
    private static final void pcSetfacilityDemographicPk(final CachedDemographic cachedDemographic, final FacilityIdIntegerCompositePk facilityDemographicPk) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.facilityDemographicPk = facilityDemographicPk;
            return;
        }
        cachedDemographic.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 3, (Object)cachedDemographic.facilityDemographicPk, (Object)facilityDemographicPk, 0);
    }
    
    private static final String pcGetfirstName(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.firstName;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 4);
        return cachedDemographic.firstName;
    }
    
    private static final void pcSetfirstName(final CachedDemographic cachedDemographic, final String firstName) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.firstName = firstName;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 4, cachedDemographic.firstName, firstName, 0);
    }
    
    private static final Gender pcGetgender(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.gender;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 5);
        return cachedDemographic.gender;
    }
    
    private static final void pcSetgender(final CachedDemographic cachedDemographic, final Gender gender) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.gender = gender;
            return;
        }
        cachedDemographic.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 5, (Object)cachedDemographic.gender, (Object)gender, 0);
    }
    
    private static final String pcGethin(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.hin;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 6);
        return cachedDemographic.hin;
    }
    
    private static final void pcSethin(final CachedDemographic cachedDemographic, final String hin) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.hin = hin;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 6, cachedDemographic.hin, hin, 0);
    }
    
    private static final String pcGethinType(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.hinType;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 7);
        return cachedDemographic.hinType;
    }
    
    private static final void pcSethinType(final CachedDemographic cachedDemographic, final String hinType) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.hinType = hinType;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 7, cachedDemographic.hinType, hinType, 0);
    }
    
    private static final Date pcGethinValidEnd(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.hinValidEnd;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 8);
        return cachedDemographic.hinValidEnd;
    }
    
    private static final void pcSethinValidEnd(final CachedDemographic cachedDemographic, final Date hinValidEnd) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.hinValidEnd = hinValidEnd;
            return;
        }
        cachedDemographic.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 8, (Object)cachedDemographic.hinValidEnd, (Object)hinValidEnd, 0);
    }
    
    private static final Date pcGethinValidStart(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.hinValidStart;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 9);
        return cachedDemographic.hinValidStart;
    }
    
    private static final void pcSethinValidStart(final CachedDemographic cachedDemographic, final Date hinValidStart) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.hinValidStart = hinValidStart;
            return;
        }
        cachedDemographic.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 9, (Object)cachedDemographic.hinValidStart, (Object)hinValidStart, 0);
    }
    
    private static final String pcGethinVersion(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.hinVersion;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 10);
        return cachedDemographic.hinVersion;
    }
    
    private static final void pcSethinVersion(final CachedDemographic cachedDemographic, final String hinVersion) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.hinVersion = hinVersion;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 10, cachedDemographic.hinVersion, hinVersion, 0);
    }
    
    private static final String pcGetidHash(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.idHash;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 11);
        return cachedDemographic.idHash;
    }
    
    private static final void pcSetidHash(final CachedDemographic cachedDemographic, final String idHash) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.idHash = idHash;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 11, cachedDemographic.idHash, idHash, 0);
    }
    
    private static final String pcGetlastName(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.lastName;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 12);
        return cachedDemographic.lastName;
    }
    
    private static final void pcSetlastName(final CachedDemographic cachedDemographic, final String lastName) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.lastName = lastName;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 12, cachedDemographic.lastName, lastName, 0);
    }
    
    private static final Date pcGetlastUpdateDate(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.lastUpdateDate;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 13);
        return cachedDemographic.lastUpdateDate;
    }
    
    private static final void pcSetlastUpdateDate(final CachedDemographic cachedDemographic, final Date lastUpdateDate) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.lastUpdateDate = lastUpdateDate;
            return;
        }
        cachedDemographic.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 13, (Object)cachedDemographic.lastUpdateDate, (Object)lastUpdateDate, 0);
    }
    
    private static final String pcGetlastUpdateUser(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.lastUpdateUser;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 14);
        return cachedDemographic.lastUpdateUser;
    }
    
    private static final void pcSetlastUpdateUser(final CachedDemographic cachedDemographic, final String lastUpdateUser) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.lastUpdateUser = lastUpdateUser;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 14, cachedDemographic.lastUpdateUser, lastUpdateUser, 0);
    }
    
    private static final String pcGetphone1(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.phone1;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 15);
        return cachedDemographic.phone1;
    }
    
    private static final void pcSetphone1(final CachedDemographic cachedDemographic, final String phone1) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.phone1 = phone1;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 15, cachedDemographic.phone1, phone1, 0);
    }
    
    private static final String pcGetphone2(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.phone2;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 16);
        return cachedDemographic.phone2;
    }
    
    private static final void pcSetphone2(final CachedDemographic cachedDemographic, final String phone2) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.phone2 = phone2;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 16, cachedDemographic.phone2, phone2, 0);
    }
    
    private static final String pcGetprovince(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.province;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 17);
        return cachedDemographic.province;
    }
    
    private static final void pcSetprovince(final CachedDemographic cachedDemographic, final String province) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.province = province;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 17, cachedDemographic.province, province, 0);
    }
    
    private static final String pcGetsin(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.sin;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 18);
        return cachedDemographic.sin;
    }
    
    private static final void pcSetsin(final CachedDemographic cachedDemographic, final String sin) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.sin = sin;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 18, cachedDemographic.sin, sin, 0);
    }
    
    private static final String pcGetstreetAddress(final CachedDemographic cachedDemographic) {
        if (cachedDemographic.pcStateManager == null) {
            return cachedDemographic.streetAddress;
        }
        cachedDemographic.pcStateManager.accessingField(CachedDemographic.pcInheritedFieldCount + 19);
        return cachedDemographic.streetAddress;
    }
    
    private static final void pcSetstreetAddress(final CachedDemographic cachedDemographic, final String streetAddress) {
        if (cachedDemographic.pcStateManager == null) {
            cachedDemographic.streetAddress = streetAddress;
            return;
        }
        cachedDemographic.pcStateManager.settingStringField((PersistenceCapable)cachedDemographic, CachedDemographic.pcInheritedFieldCount + 19, cachedDemographic.streetAddress, streetAddress, 0);
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

    /**
     * Enumeration representing patient gender identity options.
     *
     * This enum provides standardized gender values for demographic records in compliance
     * with Canadian healthcare privacy and human rights requirements. The values support
     * both traditional binary gender categories and inclusive options for diverse gender identities.
     *
     * <p><strong>Values:</strong>
     * <ul>
     *   <li><strong>M</strong> - Male</li>
     *   <li><strong>F</strong> - Female</li>
     *   <li><strong>T</strong> - Transgender/Trans</li>
     *   <li><strong>O</strong> - Other/Non-binary</li>
     *   <li><strong>U</strong> - Undisclosed/Unknown/Prefer not to answer</li>
     * </ul>
     *
     * <p>The gender field is stored as a single character in the database and should be handled
     * with appropriate privacy and respect for patient identity.
     *
     * @since 2026-01-24
     */
    public enum Gender
    {
        /** Male */
        M,
        /** Female */
        F,
        /** Transgender/Trans */
        T,
        /** Other/Non-binary */
        O,
        /** Undisclosed/Unknown/Prefer not to answer */
        U;
    }
}
