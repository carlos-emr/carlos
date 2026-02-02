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
import org.apache.commons.lang3.StringUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * JPA entity representing a cached program from the CAISI Integrator system.
 *
 * This class represents a healthcare program that can be shared across multiple
 * facilities in the OpenO EMR integrator network. Programs are community-based
 * services that provide support for various health and social needs including
 * mental health, physical health, housing assistance, and substance abuse support.
 *
 * The entity is enhanced by Apache OpenJPA for persistence capabilities and includes
 * eligibility criteria such as age ranges, gender restrictions, First Nation status,
 * and specific service categories (alcohol support, mental health, physical health, housing).
 *
 * This cached representation enables efficient cross-facility program lookups without
 * requiring real-time queries to remote CAISI installations.
 *
 * @see AbstractModel
 * @see FacilityIdIntegerCompositePk
 * @see CachedDemographic
 * @since 2026-01-24
 */
@Entity
public class CachedProgram extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedProgram>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    private String name;
    private String description;
    @Column(length = 32)
    private String type;
    @Column(length = 32)
    private String status;
    @Enumerated(EnumType.STRING)
    @Column(length = 1)
    private CachedDemographic.Gender gender;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean firstNation;
    @Column(nullable = false)
    private int minAge;
    @Column(nullable = false)
    private int maxAge;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean bedProgramAffiliated;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean alcohol;
    @Column(length = 32)
    private String abstinenceSupport;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean physicalHealth;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean mentalHealth;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean housing;
    private String address;
    private String phone;
    private String fax;
    private String url;
    private String email;
    private String emergencyNumber;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedProgram;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor that initializes a new CachedProgram instance.
     *
     * Initializes all fields to their default values:
     * - String fields are set to null
     * - Boolean fields are set to false
     * - minAge is set to 0
     * - maxAge is set to 200 (representing no upper age limit)
     */
    public CachedProgram() {
        this.name = null;
        this.description = null;
        this.type = null;
        this.status = null;
        this.gender = null;
        this.firstNation = false;
        this.minAge = 0;
        this.maxAge = 200;
        this.bedProgramAffiliated = false;
        this.alcohol = false;
        this.abstinenceSupport = null;
        this.physicalHealth = false;
        this.mentalHealth = false;
        this.housing = false;
        this.address = null;
        this.phone = null;
        this.fax = null;
        this.url = null;
        this.email = null;
        this.emergencyNumber = null;
    }

    /**
     * Gets the composite primary key for this cached program.
     *
     * @return FacilityIdIntegerCompositePk the composite primary key containing facility ID and CAISI item ID
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityIdIntegerCompositePk(this);
    }

    /**
     * Sets the composite primary key for this cached program.
     *
     * @param facilityIdIntegerCompositePk FacilityIdIntegerCompositePk the composite primary key to set
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        pcSetfacilityIdIntegerCompositePk(this, facilityIdIntegerCompositePk);
    }

    /**
     * Gets the name of the program.
     *
     * @return String the program name, may be null
     */
    public String getName() {
        return pcGetname(this);
    }

    /**
     * Sets the name of the program.
     * The input string is trimmed and converted to null if empty.
     *
     * @param name String the program name to set
     */
    public void setName(final String name) {
        pcSetname(this, StringUtils.trimToNull(name));
    }

    /**
     * Gets the description of the program.
     *
     * @return String the program description, may be null
     */
    public String getDescription() {
        return pcGetdescription(this);
    }

    /**
     * Sets the description of the program.
     * The input string is trimmed and converted to null if empty.
     *
     * @param description String the program description to set
     */
    public void setDescription(final String description) {
        pcSetdescription(this, StringUtils.trimToNull(description));
    }

    /**
     * Gets the type of the program.
     *
     * @return String the program type, maximum length 32 characters, may be null
     */
    public String getType() {
        return pcGettype(this);
    }

    /**
     * Sets the type of the program.
     * The input string is trimmed and converted to null if empty.
     *
     * @param type String the program type to set, maximum length 32 characters
     */
    public void setType(final String type) {
        pcSettype(this, StringUtils.trimToNull(type));
    }

    /**
     * Gets the status of the program.
     *
     * @return String the program status, maximum length 32 characters, may be null
     */
    public String getStatus() {
        return pcGetstatus(this);
    }

    /**
     * Sets the status of the program.
     * The input string is trimmed and converted to null if empty.
     *
     * @param status String the program status to set, maximum length 32 characters
     */
    public void setStatus(final String status) {
        pcSetstatus(this, StringUtils.trimToNull(status));
    }

    /**
     * Checks if the program is affiliated with a bed program.
     *
     * @return boolean true if the program is affiliated with a bed program, false otherwise
     */
    public boolean isBedProgramAffiliated() {
        return pcGetbedProgramAffiliated(this);
    }

    /**
     * Sets whether the program is affiliated with a bed program.
     *
     * @param bedProgramAffiliated boolean true to indicate bed program affiliation, false otherwise
     */
    public void setBedProgramAffiliated(final boolean bedProgramAffiliated) {
        pcSetbedProgramAffiliated(this, bedProgramAffiliated);
    }

    /**
     * Checks if the program provides alcohol-related services or support.
     *
     * @return boolean true if the program provides alcohol services, false otherwise
     */
    public boolean isAlcohol() {
        return pcGetalcohol(this);
    }

    /**
     * Sets whether the program provides alcohol-related services or support.
     *
     * @param alcohol boolean true to indicate alcohol services are provided, false otherwise
     */
    public void setAlcohol(final boolean alcohol) {
        pcSetalcohol(this, alcohol);
    }

    /**
     * Gets the abstinence support level or type provided by the program.
     *
     * @return String the abstinence support level, maximum length 32 characters, may be null
     */
    public String getAbstinenceSupport() {
        return pcGetabstinenceSupport(this);
    }

    /**
     * Sets the abstinence support level or type provided by the program.
     * The input string is trimmed and converted to null if empty.
     *
     * @param abstinenceSupport String the abstinence support level to set, maximum length 32 characters
     */
    public void setAbstinenceSupport(final String abstinenceSupport) {
        pcSetabstinenceSupport(this, StringUtils.trimToNull(abstinenceSupport));
    }

    /**
     * Checks if the program provides physical health services.
     *
     * @return boolean true if the program provides physical health services, false otherwise
     */
    public boolean isPhysicalHealth() {
        return pcGetphysicalHealth(this);
    }

    /**
     * Sets whether the program provides physical health services.
     *
     * @param physicalHealth boolean true to indicate physical health services are provided, false otherwise
     */
    public void setPhysicalHealth(final boolean physicalHealth) {
        pcSetphysicalHealth(this, physicalHealth);
    }

    /**
     * Checks if the program provides mental health services.
     *
     * @return boolean true if the program provides mental health services, false otherwise
     */
    public boolean isMentalHealth() {
        return pcGetmentalHealth(this);
    }

    /**
     * Sets whether the program provides mental health services.
     *
     * @param mentalHealth boolean true to indicate mental health services are provided, false otherwise
     */
    public void setMentalHealth(final boolean mentalHealth) {
        pcSetmentalHealth(this, mentalHealth);
    }

    /**
     * Checks if the program provides housing assistance or services.
     *
     * @return boolean true if the program provides housing services, false otherwise
     */
    public boolean isHousing() {
        return pcGethousing(this);
    }

    /**
     * Sets whether the program provides housing assistance or services.
     *
     * @param housing boolean true to indicate housing services are provided, false otherwise
     */
    public void setHousing(final boolean housing) {
        pcSethousing(this, housing);
    }

    /**
     * Gets the gender restriction for program eligibility.
     *
     * @return Gender the gender restriction (M, F, T, O, or U), may be null if no gender restriction
     * @see CachedDemographic.Gender
     */
    public CachedDemographic.Gender getGender() {
        return pcGetgender(this);
    }

    /**
     * Sets the gender restriction for program eligibility.
     *
     * @param gender Gender the gender restriction to set, may be null for no restriction
     * @see CachedDemographic.Gender
     */
    public void setGender(final CachedDemographic.Gender gender) {
        pcSetgender(this, gender);
    }

    /**
     * Checks if the program is restricted to First Nation individuals.
     *
     * @return boolean true if the program is restricted to First Nation individuals, false otherwise
     */
    public boolean isFirstNation() {
        return pcGetfirstNation(this);
    }

    /**
     * Sets whether the program is restricted to First Nation individuals.
     *
     * @param firstNation boolean true to restrict to First Nation individuals, false otherwise
     */
    public void setFirstNation(final boolean firstNation) {
        pcSetfirstNation(this, firstNation);
    }

    /**
     * Gets the minimum age for program eligibility.
     *
     * @return int the minimum age in years (default is 0)
     */
    public int getMinAge() {
        return pcGetminAge(this);
    }

    /**
     * Sets the minimum age for program eligibility.
     *
     * @param minAge int the minimum age in years
     */
    public void setMinAge(final int minAge) {
        pcSetminAge(this, minAge);
    }

    /**
     * Gets the maximum age for program eligibility.
     *
     * @return int the maximum age in years (default is 200, representing no upper limit)
     */
    public int getMaxAge() {
        return pcGetmaxAge(this);
    }

    /**
     * Sets the maximum age for program eligibility.
     *
     * @param maxAge int the maximum age in years
     */
    public void setMaxAge(final int maxAge) {
        pcSetmaxAge(this, maxAge);
    }

    /**
     * Gets the physical address of the program location.
     *
     * @return String the program address, may be null
     */
    public String getAddress() {
        return pcGetaddress(this);
    }

    /**
     * Sets the physical address of the program location.
     *
     * @param address String the program address to set
     */
    public void setAddress(final String address) {
        pcSetaddress(this, address);
    }

    /**
     * Gets the phone number for the program.
     *
     * @return String the phone number, may be null
     */
    public String getPhone() {
        return pcGetphone(this);
    }

    /**
     * Sets the phone number for the program.
     *
     * @param phone String the phone number to set
     */
    public void setPhone(final String phone) {
        pcSetphone(this, phone);
    }

    /**
     * Gets the fax number for the program.
     *
     * @return String the fax number, may be null
     */
    public String getFax() {
        return pcGetfax(this);
    }

    /**
     * Sets the fax number for the program.
     *
     * @param fax String the fax number to set
     */
    public void setFax(final String fax) {
        pcSetfax(this, fax);
    }

    /**
     * Gets the website URL for the program.
     *
     * @return String the website URL, may be null
     */
    public String getUrl() {
        return pcGeturl(this);
    }

    /**
     * Sets the website URL for the program.
     *
     * @param url String the website URL to set
     */
    public void setUrl(final String url) {
        pcSeturl(this, url);
    }

    /**
     * Gets the email address for the program.
     *
     * @return String the email address, may be null
     */
    public String getEmail() {
        return pcGetemail(this);
    }

    /**
     * Sets the email address for the program.
     *
     * @param email String the email address to set
     */
    public void setEmail(final String email) {
        pcSetemail(this, email);
    }

    /**
     * Gets the emergency contact number for the program.
     *
     * @return String the emergency contact number, may be null
     */
    public String getEmergencyNumber() {
        return pcGetemergencyNumber(this);
    }

    /**
     * Sets the emergency contact number for the program.
     *
     * @param emergencyNumber String the emergency contact number to set
     */
    public void setEmergencyNumber(final String emergencyNumber) {
        pcSetemergencyNumber(this, emergencyNumber);
    }

    /**
     * Compares this program with another program based on their CAISI item IDs.
     * Used for sorting programs in collections.
     *
     * @param o CachedProgram the program to compare to
     * @return int negative if this program's ID is less, zero if equal, positive if greater
     */
    @Override
    public int compareTo(final CachedProgram o) {
        return pcGetfacilityIdIntegerCompositePk(this).getCaisiItemId() - pcGetfacilityIdIntegerCompositePk(o).getCaisiItemId();
    }

    /**
     * Gets the identifier for this program (composite primary key).
     *
     * @return FacilityIdIntegerCompositePk the composite primary key
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityIdIntegerCompositePk(this);
    }

    /**
     * Returns the OpenJPA enhancement contract version for this entity.
     * Used by OpenJPA for bytecode enhancement versioning.
     *
     * @return int the enhancement contract version (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 892878660194000526L;
        CachedProgram.pcFieldNames = new String[] { "abstinenceSupport", "address", "alcohol", "bedProgramAffiliated", "description", "email", "emergencyNumber", "facilityIdIntegerCompositePk", "fax", "firstNation", "gender", "housing", "maxAge", "mentalHealth", "minAge", "name", "phone", "physicalHealth", "status", "type", "url" };
        CachedProgram.pcFieldTypes = new Class[] { (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), Boolean.TYPE, Boolean.TYPE, (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), (CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), Boolean.TYPE, (CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender != null) ? CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender : (CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographic$Gender")), Boolean.TYPE, Integer.TYPE, Boolean.TYPE, Integer.TYPE, (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), Boolean.TYPE, (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedProgram.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedProgram != null) ? CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedProgram : (CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedProgram = class$("ca.openosp.openo.caisi_integrator.dao.CachedProgram")), CachedProgram.pcFieldNames, CachedProgram.pcFieldTypes, CachedProgram.pcFieldFlags, CachedProgram.pcPCSuperclass, "CachedProgram", (PersistenceCapable)new CachedProgram());
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
     * Clears all fields in this entity to their default values.
     * Used by OpenJPA for entity lifecycle management.
     */
    protected void pcClearFields() {
        this.abstinenceSupport = null;
        this.address = null;
        this.alcohol = false;
        this.bedProgramAffiliated = false;
        this.description = null;
        this.email = null;
        this.emergencyNumber = null;
        this.facilityIdIntegerCompositePk = null;
        this.fax = null;
        this.firstNation = false;
        this.gender = null;
        this.housing = false;
        this.maxAge = 0;
        this.mentalHealth = false;
        this.minAge = 0;
        this.name = null;
        this.phone = null;
        this.physicalHealth = false;
        this.status = null;
        this.type = null;
        this.url = null;
    }

    /**
     * Creates a new instance of this entity with the specified state manager and object ID.
     * Used by OpenJPA for creating managed instances during persistence operations.
     *
     * @param pcStateManager StateManager the state manager to manage this instance
     * @param o Object the object ID to copy key fields from
     * @param b boolean true to clear all fields, false to preserve them
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedProgram cachedProgram = new CachedProgram();
        if (b) {
            cachedProgram.pcClearFields();
        }
        cachedProgram.pcStateManager = pcStateManager;
        cachedProgram.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedProgram;
    }

    /**
     * Creates a new instance of this entity with the specified state manager.
     * Used by OpenJPA for creating managed instances during persistence operations.
     *
     * @param pcStateManager StateManager the state manager to manage this instance
     * @param b boolean true to clear all fields, false to preserve them
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedProgram cachedProgram = new CachedProgram();
        if (b) {
            cachedProgram.pcClearFields();
        }
        cachedProgram.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedProgram;
    }

    /**
     * Returns the number of managed fields in this entity.
     * Used by OpenJPA for field tracking and state management.
     *
     * @return int the number of managed fields (21)
     */
    protected static int pcGetManagedFieldCount() {
        return 21;
    }

    /**
     * Replaces the value of a single field from the state manager.
     * Used by OpenJPA during entity hydration and state restoration.
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedProgram.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.abstinenceSupport = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.address = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.alcohol = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.bedProgramAffiliated = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.description = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.email = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.emergencyNumber = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.fax = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.firstNation = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 10: {
                this.gender = (CachedDemographic.Gender)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 11: {
                this.housing = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 12: {
                this.maxAge = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 13: {
                this.mentalHealth = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 14: {
                this.minAge = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 15: {
                this.name = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 16: {
                this.phone = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 17: {
                this.physicalHealth = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 18: {
                this.status = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 19: {
                this.type = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 20: {
                this.url = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces the values of multiple fields from the state manager.
     * Used by OpenJPA during entity hydration and state restoration.
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides the value of a single field to the state manager.
     * Used by OpenJPA during entity persistence and state capture.
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedProgram.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.abstinenceSupport);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.address);
                return;
            }
            case 2: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.alcohol);
                return;
            }
            case 3: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.bedProgramAffiliated);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.description);
                return;
            }
            case 5: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.email);
                return;
            }
            case 6: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.emergencyNumber);
                return;
            }
            case 7: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityIdIntegerCompositePk);
                return;
            }
            case 8: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.fax);
                return;
            }
            case 9: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.firstNation);
                return;
            }
            case 10: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.gender);
                return;
            }
            case 11: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.housing);
                return;
            }
            case 12: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.maxAge);
                return;
            }
            case 13: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.mentalHealth);
                return;
            }
            case 14: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.minAge);
                return;
            }
            case 15: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.name);
                return;
            }
            case 16: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.phone);
                return;
            }
            case 17: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.physicalHealth);
                return;
            }
            case 18: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.status);
                return;
            }
            case 19: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.type);
                return;
            }
            case 20: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.url);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Provides the values of multiple fields to the state manager.
     * Used by OpenJPA during entity persistence and state capture.
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }

    /**
     * Copies the value of a single field from another CachedProgram instance.
     * Used by OpenJPA for entity cloning and merging operations.
     *
     * @param cachedProgram CachedProgram the source program to copy from
     * @param n int the field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final CachedProgram cachedProgram, final int n) {
        final int n2 = n - CachedProgram.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.abstinenceSupport = cachedProgram.abstinenceSupport;
                return;
            }
            case 1: {
                this.address = cachedProgram.address;
                return;
            }
            case 2: {
                this.alcohol = cachedProgram.alcohol;
                return;
            }
            case 3: {
                this.bedProgramAffiliated = cachedProgram.bedProgramAffiliated;
                return;
            }
            case 4: {
                this.description = cachedProgram.description;
                return;
            }
            case 5: {
                this.email = cachedProgram.email;
                return;
            }
            case 6: {
                this.emergencyNumber = cachedProgram.emergencyNumber;
                return;
            }
            case 7: {
                this.facilityIdIntegerCompositePk = cachedProgram.facilityIdIntegerCompositePk;
                return;
            }
            case 8: {
                this.fax = cachedProgram.fax;
                return;
            }
            case 9: {
                this.firstNation = cachedProgram.firstNation;
                return;
            }
            case 10: {
                this.gender = cachedProgram.gender;
                return;
            }
            case 11: {
                this.housing = cachedProgram.housing;
                return;
            }
            case 12: {
                this.maxAge = cachedProgram.maxAge;
                return;
            }
            case 13: {
                this.mentalHealth = cachedProgram.mentalHealth;
                return;
            }
            case 14: {
                this.minAge = cachedProgram.minAge;
                return;
            }
            case 15: {
                this.name = cachedProgram.name;
                return;
            }
            case 16: {
                this.phone = cachedProgram.phone;
                return;
            }
            case 17: {
                this.physicalHealth = cachedProgram.physicalHealth;
                return;
            }
            case 18: {
                this.status = cachedProgram.status;
                return;
            }
            case 19: {
                this.type = cachedProgram.type;
                return;
            }
            case 20: {
                this.url = cachedProgram.url;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies the values of multiple fields from another CachedProgram instance.
     * Used by OpenJPA for entity cloning and merging operations.
     *
     * @param o Object the source object to copy from (must be a CachedProgram)
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if state managers don't match
     * @throws IllegalStateException if state manager is null
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedProgram cachedProgram = (CachedProgram)o;
        if (cachedProgram.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedProgram, array[i]);
        }
    }

    /**
     * Gets the generic context from the state manager.
     * Used by OpenJPA for accessing persistence context information.
     *
     * @return Object the generic context, or null if no state manager
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the object identifier for this entity.
     * Used by OpenJPA for identity management.
     *
     * @return Object the object identifier, or null if no state manager
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks if this entity is marked for deletion.
     * Used by OpenJPA for lifecycle state tracking.
     *
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has been modified since being loaded or last persisted.
     * Used by OpenJPA for change detection and optimistic locking.
     *
     * @return boolean true if the entity is dirty, false otherwise
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
     * Checks if this entity is newly created and not yet persisted.
     * Used by OpenJPA for lifecycle state tracking.
     *
     * @return boolean true if the entity is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is persistent (managed by a persistence context).
     * Used by OpenJPA for lifecycle state tracking.
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity is part of an active transaction.
     * Used by OpenJPA for transaction management.
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity is currently being serialized.
     * Used by OpenJPA for serialization handling.
     *
     * @return boolean true if the entity is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a field as dirty (modified).
     * Used by OpenJPA for change tracking.
     *
     * @param s String the field name that was modified
     */
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }

    /**
     * Gets the state manager for this entity.
     * Used by OpenJPA for accessing persistence state management.
     *
     * @return StateManager the state manager managing this entity
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Gets the version of this entity for optimistic locking.
     * Used by OpenJPA for detecting concurrent modifications.
     *
     * @return Object the version object, or null if no state manager
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces the state manager for this entity.
     * Used by OpenJPA during entity attachment and detachment.
     *
     * @param pcStateManager StateManager the new state manager
     * @throws SecurityException if replacement is not allowed
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
     * This operation is not supported for this entity type.
     *
     * @param fieldSupplier FieldSupplier the field supplier (not used)
     * @param o Object the object ID (not used)
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields to an object ID.
     * This operation is not supported for this entity type.
     *
     * @param o Object the object ID (not used)
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields from an object ID using a field consumer.
     * Used by OpenJPA for reconstructing entity identity.
     *
     * @param fieldConsumer FieldConsumer the field consumer to receive key fields
     * @param o Object the object ID to copy from
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(7 + CachedProgram.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key fields from an object ID.
     * Used by OpenJPA for reconstructing entity identity.
     *
     * @param o Object the object ID to copy from
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     * This operation is not supported for this entity type due to composite key structure.
     *
     * @param o Object the string representation (not used)
     * @return Object never returns normally
     * @throws IllegalArgumentException always thrown with explanatory message
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedProgram\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance for this entity.
     * Used by OpenJPA for generating entity identifiers.
     *
     * @return Object the new object ID instance containing this entity's composite key
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedProgram != null) ? CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedProgram : (CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedProgram = class$("ca.openosp.openo.caisi_integrator.dao.CachedProgram")), (Object)this.facilityIdIntegerCompositePk);
    }
    
    private static final String pcGetabstinenceSupport(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.abstinenceSupport;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 0);
        return cachedProgram.abstinenceSupport;
    }
    
    private static final void pcSetabstinenceSupport(final CachedProgram cachedProgram, final String abstinenceSupport) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.abstinenceSupport = abstinenceSupport;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 0, cachedProgram.abstinenceSupport, abstinenceSupport, 0);
    }
    
    private static final String pcGetaddress(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.address;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 1);
        return cachedProgram.address;
    }
    
    private static final void pcSetaddress(final CachedProgram cachedProgram, final String address) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.address = address;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 1, cachedProgram.address, address, 0);
    }
    
    private static final boolean pcGetalcohol(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.alcohol;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 2);
        return cachedProgram.alcohol;
    }
    
    private static final void pcSetalcohol(final CachedProgram cachedProgram, final boolean alcohol) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.alcohol = alcohol;
            return;
        }
        cachedProgram.pcStateManager.settingBooleanField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 2, cachedProgram.alcohol, alcohol, 0);
    }
    
    private static final boolean pcGetbedProgramAffiliated(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.bedProgramAffiliated;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 3);
        return cachedProgram.bedProgramAffiliated;
    }
    
    private static final void pcSetbedProgramAffiliated(final CachedProgram cachedProgram, final boolean bedProgramAffiliated) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.bedProgramAffiliated = bedProgramAffiliated;
            return;
        }
        cachedProgram.pcStateManager.settingBooleanField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 3, cachedProgram.bedProgramAffiliated, bedProgramAffiliated, 0);
    }
    
    private static final String pcGetdescription(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.description;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 4);
        return cachedProgram.description;
    }
    
    private static final void pcSetdescription(final CachedProgram cachedProgram, final String description) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.description = description;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 4, cachedProgram.description, description, 0);
    }
    
    private static final String pcGetemail(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.email;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 5);
        return cachedProgram.email;
    }
    
    private static final void pcSetemail(final CachedProgram cachedProgram, final String email) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.email = email;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 5, cachedProgram.email, email, 0);
    }
    
    private static final String pcGetemergencyNumber(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.emergencyNumber;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 6);
        return cachedProgram.emergencyNumber;
    }
    
    private static final void pcSetemergencyNumber(final CachedProgram cachedProgram, final String emergencyNumber) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.emergencyNumber = emergencyNumber;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 6, cachedProgram.emergencyNumber, emergencyNumber, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityIdIntegerCompositePk(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.facilityIdIntegerCompositePk;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 7);
        return cachedProgram.facilityIdIntegerCompositePk;
    }
    
    private static final void pcSetfacilityIdIntegerCompositePk(final CachedProgram cachedProgram, final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
            return;
        }
        cachedProgram.pcStateManager.settingObjectField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 7, (Object)cachedProgram.facilityIdIntegerCompositePk, (Object)facilityIdIntegerCompositePk, 0);
    }
    
    private static final String pcGetfax(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.fax;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 8);
        return cachedProgram.fax;
    }
    
    private static final void pcSetfax(final CachedProgram cachedProgram, final String fax) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.fax = fax;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 8, cachedProgram.fax, fax, 0);
    }
    
    private static final boolean pcGetfirstNation(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.firstNation;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 9);
        return cachedProgram.firstNation;
    }
    
    private static final void pcSetfirstNation(final CachedProgram cachedProgram, final boolean firstNation) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.firstNation = firstNation;
            return;
        }
        cachedProgram.pcStateManager.settingBooleanField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 9, cachedProgram.firstNation, firstNation, 0);
    }
    
    private static final CachedDemographic.Gender pcGetgender(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.gender;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 10);
        return cachedProgram.gender;
    }
    
    private static final void pcSetgender(final CachedProgram cachedProgram, final CachedDemographic.Gender gender) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.gender = gender;
            return;
        }
        cachedProgram.pcStateManager.settingObjectField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 10, (Object)cachedProgram.gender, (Object)gender, 0);
    }
    
    private static final boolean pcGethousing(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.housing;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 11);
        return cachedProgram.housing;
    }
    
    private static final void pcSethousing(final CachedProgram cachedProgram, final boolean housing) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.housing = housing;
            return;
        }
        cachedProgram.pcStateManager.settingBooleanField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 11, cachedProgram.housing, housing, 0);
    }
    
    private static final int pcGetmaxAge(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.maxAge;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 12);
        return cachedProgram.maxAge;
    }
    
    private static final void pcSetmaxAge(final CachedProgram cachedProgram, final int maxAge) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.maxAge = maxAge;
            return;
        }
        cachedProgram.pcStateManager.settingIntField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 12, cachedProgram.maxAge, maxAge, 0);
    }
    
    private static final boolean pcGetmentalHealth(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.mentalHealth;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 13);
        return cachedProgram.mentalHealth;
    }
    
    private static final void pcSetmentalHealth(final CachedProgram cachedProgram, final boolean mentalHealth) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.mentalHealth = mentalHealth;
            return;
        }
        cachedProgram.pcStateManager.settingBooleanField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 13, cachedProgram.mentalHealth, mentalHealth, 0);
    }
    
    private static final int pcGetminAge(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.minAge;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 14);
        return cachedProgram.minAge;
    }
    
    private static final void pcSetminAge(final CachedProgram cachedProgram, final int minAge) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.minAge = minAge;
            return;
        }
        cachedProgram.pcStateManager.settingIntField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 14, cachedProgram.minAge, minAge, 0);
    }
    
    private static final String pcGetname(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.name;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 15);
        return cachedProgram.name;
    }
    
    private static final void pcSetname(final CachedProgram cachedProgram, final String name) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.name = name;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 15, cachedProgram.name, name, 0);
    }
    
    private static final String pcGetphone(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.phone;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 16);
        return cachedProgram.phone;
    }
    
    private static final void pcSetphone(final CachedProgram cachedProgram, final String phone) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.phone = phone;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 16, cachedProgram.phone, phone, 0);
    }
    
    private static final boolean pcGetphysicalHealth(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.physicalHealth;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 17);
        return cachedProgram.physicalHealth;
    }
    
    private static final void pcSetphysicalHealth(final CachedProgram cachedProgram, final boolean physicalHealth) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.physicalHealth = physicalHealth;
            return;
        }
        cachedProgram.pcStateManager.settingBooleanField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 17, cachedProgram.physicalHealth, physicalHealth, 0);
    }
    
    private static final String pcGetstatus(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.status;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 18);
        return cachedProgram.status;
    }
    
    private static final void pcSetstatus(final CachedProgram cachedProgram, final String status) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.status = status;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 18, cachedProgram.status, status, 0);
    }
    
    private static final String pcGettype(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.type;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 19);
        return cachedProgram.type;
    }
    
    private static final void pcSettype(final CachedProgram cachedProgram, final String type) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.type = type;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 19, cachedProgram.type, type, 0);
    }
    
    private static final String pcGeturl(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.url;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 20);
        return cachedProgram.url;
    }
    
    private static final void pcSeturl(final CachedProgram cachedProgram, final String url) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.url = url;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 20, cachedProgram.url, url, 0);
    }

    /**
     * Checks if this entity is in a detached state.
     * Detached entities have been loaded from the database but are no longer managed by a persistence context.
     * Used by OpenJPA for lifecycle state tracking and entity merging operations.
     *
     * @return Boolean true if detached, false if attached, null if state is indeterminate
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
     * Checks if the detached state is definitive for this entity.
     * Used internally by OpenJPA to determine if the detached state can be trusted.
     *
     * @return boolean always returns false, indicating detached state is not definitive
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Gets the detached state object for this entity.
     * The detached state holds information needed to reattach the entity to a persistence context.
     *
     * @return Object the detached state object, may be null or DESERIALIZED constant
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object for this entity.
     * Used by OpenJPA during detachment and serialization operations.
     *
     * @param pcDetachedState Object the detached state to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method for writing this entity to an output stream.
     * Handles proper serialization of OpenJPA-enhanced entities.
     *
     * @param objectOutputStream ObjectOutputStream the stream to write to
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
     * Custom deserialization method for reading this entity from an input stream.
     * Handles proper deserialization of OpenJPA-enhanced entities and marks the entity as deserialized.
     *
     * @param objectInputStream ObjectInputStream the stream to read from
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if the class of a serialized object cannot be found
     */
    private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        this.pcSetDetachedState(PersistenceCapable.DESERIALIZED);
        objectInputStream.defaultReadObject();
    }
}
