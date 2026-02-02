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
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * JPA entity representing a cached demographic allergy record from the CAISI integrator.
 *
 * This class stores patient allergy information synchronized from remote healthcare facilities
 * through the CAISI (Client Access to Integrated Services and Information) integrator system.
 * It maintains a local cache of allergy data to enable efficient cross-facility patient care
 * and continuity of care across multiple healthcare organizations.
 *
 * The entity is enhanced by Apache OpenJPA for persistence capabilities and implements the
 * PersistenceCapable interface to support advanced JPA features including detachment,
 * change tracking, and state management.
 *
 * Key healthcare data captured includes:
 * <ul>
 *   <li>Allergy description and reaction details</li>
 *   <li>Severity codes and onset information</li>
 *   <li>Hierarchical classification codes (HIC, HICL)</li>
 *   <li>Regional identifiers for cross-jurisdictional care</li>
 *   <li>Temporal information (entry date, start date, life stage)</li>
 * </ul>
 *
 * @see AbstractModel
 * @see FacilityIdIntegerCompositePk
 * @see PersistenceCapable
 * @since 2026-01-24
 */
@Entity
public class CachedDemographicAllergy extends AbstractModel<FacilityIdIntegerCompositePk> implements PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    @Column(nullable = false)
    @Index
    private int caisiDemographicId;
    @Temporal(TemporalType.TIMESTAMP)
    private Date entryDate;
    private int pickId;
    private String description;
    private int hiclSeqNo;
    private int hicSeqNo;
    private int agcsp;
    private int agccs;
    private int typeCode;
    private String reaction;
    @Temporal(TemporalType.TIMESTAMP)
    private Date startDate;
    private String ageOfOnset;
    private String severityCode;
    private String onSetCode;
    private String regionalIdentifier;
    private String lifeStage;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicAllergy;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor for the cached demographic allergy entity.
     * Initializes the CAISI demographic ID to 0.
     */
    public CachedDemographicAllergy() {
        this.caisiDemographicId = 0;
    }
    
    /**
     * Retrieves the composite primary key for this cached allergy record.
     *
     * @return FacilityIdIntegerCompositePk the composite key containing facility ID and record ID
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityIdIntegerCompositePk(this);
    }

    /**
     * Sets the CAISI demographic identifier for the patient associated with this allergy.
     *
     * @param caisiDemographicId int the CAISI demographic identifier
     */
    public void setCaisiDemographicId(final int caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }

    /**
     * Retrieves the CAISI demographic identifier for the patient.
     *
     * @return int the CAISI demographic identifier
     */
    public int getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }
    
    /**
     * Sets the date when this allergy record was entered into the system.
     *
     * @param entryDate Date the entry timestamp
     */
    public void setEntryDate(final Date entryDate) {
        pcSetentryDate(this, entryDate);
    }

    /**
     * Retrieves the date when this allergy record was entered.
     *
     * @return Date the entry timestamp
     */
    public Date getEntryDate() {
        return pcGetentryDate(this);
    }
    
    /**
     * Retrieves the composite primary key containing facility and record identifiers.
     *
     * @return FacilityIdIntegerCompositePk the composite key
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityIdIntegerCompositePk(this);
    }

    /**
     * Sets the composite primary key for this record.
     *
     * @param facilityIdIntegerCompositePk FacilityIdIntegerCompositePk the composite key to set
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        pcSetfacilityIdIntegerCompositePk(this, facilityIdIntegerCompositePk);
    }
    
    /**
     * Retrieves the textual description of the allergy.
     *
     * @return String the allergy description, or null if not set
     */
    public String getDescription() {
        return pcGetdescription(this);
    }

    /**
     * Sets the textual description of the allergy.
     * The description is automatically trimmed and null values are handled.
     *
     * @param description String the allergy description to set
     */
    public void setDescription(final String description) {
        pcSetdescription(this, StringUtils.trimToNull(description));
    }

    /**
     * Retrieves the patient's reaction to the allergen.
     *
     * @return String the reaction description, or null if not set
     */
    public String getReaction() {
        return pcGetreaction(this);
    }

    /**
     * Sets the patient's reaction to the allergen.
     * The reaction is automatically trimmed and null values are handled.
     *
     * @param reaction String the reaction description to set
     */
    public void setReaction(final String reaction) {
        pcSetreaction(this, StringUtils.trimToNull(reaction));
    }
    
    /**
     * Retrieves the pick identifier for this allergy selection.
     *
     * @return int the pick identifier
     */
    public int getPickId() {
        return pcGetpickId(this);
    }

    /**
     * Sets the pick identifier for this allergy selection.
     *
     * @param pickId int the pick identifier to set
     */
    public void setPickId(final int pickId) {
        pcSetpickId(this, pickId);
    }

    /**
     * Retrieves the HICL (Health Insurance Classification List) sequence number.
     *
     * @return int the HICL sequence number
     */
    public int getHiclSeqNo() {
        return pcGethiclSeqNo(this);
    }

    /**
     * Sets the HICL (Health Insurance Classification List) sequence number.
     *
     * @param hiclSeqNo int the HICL sequence number to set
     */
    public void setHiclSeqNo(final int hiclSeqNo) {
        pcSethiclSeqNo(this, hiclSeqNo);
    }

    /**
     * Retrieves the HIC (Health Insurance Classification) sequence number.
     *
     * @return int the HIC sequence number
     */
    public int getHicSeqNo() {
        return pcGethicSeqNo(this);
    }

    /**
     * Sets the HIC (Health Insurance Classification) sequence number.
     *
     * @param hicSeqNo int the HIC sequence number to set
     */
    public void setHicSeqNo(final int hicSeqNo) {
        pcSethicSeqNo(this, hicSeqNo);
    }

    /**
     * Retrieves the AGCSP (Allergen Group Code - Specific) value.
     *
     * @return int the AGCSP code
     */
    public int getAgcsp() {
        return pcGetagcsp(this);
    }

    /**
     * Sets the AGCSP (Allergen Group Code - Specific) value.
     *
     * @param agcsp int the AGCSP code to set
     */
    public void setAgcsp(final int agcsp) {
        pcSetagcsp(this, agcsp);
    }

    /**
     * Retrieves the AGCCS (Allergen Group Code - Classification) value.
     *
     * @return int the AGCCS code
     */
    public int getAgccs() {
        return pcGetagccs(this);
    }

    /**
     * Sets the AGCCS (Allergen Group Code - Classification) value.
     *
     * @param agccs int the AGCCS code to set
     */
    public void setAgccs(final int agccs) {
        pcSetagccs(this, agccs);
    }

    /**
     * Retrieves the allergy type code.
     *
     * @return int the type code
     */
    public int getTypeCode() {
        return pcGettypeCode(this);
    }

    /**
     * Sets the allergy type code.
     *
     * @param typeCode int the type code to set
     */
    public void setTypeCode(final int typeCode) {
        pcSettypeCode(this, typeCode);
    }
    
    /**
     * Retrieves the date when the allergy first started or was identified.
     *
     * @return Date the start date of the allergy
     */
    public Date getStartDate() {
        return pcGetstartDate(this);
    }

    /**
     * Sets the date when the allergy first started or was identified.
     *
     * @param startDate Date the start date to set
     */
    public void setStartDate(final Date startDate) {
        pcSetstartDate(this, startDate);
    }

    /**
     * Retrieves the age of the patient when the allergy first manifested.
     *
     * @return String the age of onset, or null if not recorded
     */
    public String getAgeOfOnset() {
        return pcGetageOfOnset(this);
    }

    /**
     * Sets the age of the patient when the allergy first manifested.
     * The value is automatically trimmed and null values are handled.
     *
     * @param ageOfOnset String the age of onset to set
     */
    public void setAgeOfOnset(final String ageOfOnset) {
        pcSetageOfOnset(this, StringUtils.trimToNull(ageOfOnset));
    }

    /**
     * Retrieves the severity code indicating how severe the allergy reaction is.
     *
     * @return String the severity code, or null if not specified
     */
    public String getSeverityCode() {
        return pcGetseverityCode(this);
    }

    /**
     * Sets the severity code for the allergy.
     * The value is automatically trimmed and null values are handled.
     *
     * @param severityCode String the severity code to set
     */
    public void setSeverityCode(final String severityCode) {
        pcSetseverityCode(this, StringUtils.trimToNull(severityCode));
    }

    /**
     * Retrieves the onset code describing how the allergy began.
     *
     * @return String the onset code, or null if not specified
     */
    public String getOnSetCode() {
        return pcGetonSetCode(this);
    }

    /**
     * Sets the onset code describing how the allergy began.
     * The value is automatically trimmed and null values are handled.
     *
     * @param onSetCode String the onset code to set
     */
    public void setOnSetCode(final String onSetCode) {
        pcSetonSetCode(this, StringUtils.trimToNull(onSetCode));
    }

    /**
     * Retrieves the regional identifier for cross-jurisdictional healthcare coordination.
     *
     * @return String the regional identifier, or null if not applicable
     */
    public String getRegionalIdentifier() {
        return pcGetregionalIdentifier(this);
    }

    /**
     * Sets the regional identifier for cross-jurisdictional healthcare coordination.
     * The value is automatically trimmed and null values are handled.
     *
     * @param regionalIdentifier String the regional identifier to set
     */
    public void setRegionalIdentifier(final String regionalIdentifier) {
        pcSetregionalIdentifier(this, StringUtils.trimToNull(regionalIdentifier));
    }

    /**
     * Retrieves the life stage when this allergy was documented.
     *
     * @return String the life stage, or null if not specified
     */
    public String getLifeStage() {
        return pcGetlifeStage(this);
    }

    /**
     * Sets the life stage when this allergy was documented.
     * The value is automatically trimmed and null values are handled.
     *
     * @param lifeStage String the life stage to set
     */
    public void setLifeStage(final String lifeStage) {
        pcSetlifeStage(this, StringUtils.trimToNull(lifeStage));
    }
    
    /**
     * Returns the OpenJPA enhancement contract version for this entity.
     *
     * @return int the enhancement contract version (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 6631114811357540778L;
        CachedDemographicAllergy.pcFieldNames = new String[] { "agccs", "agcsp", "ageOfOnset", "caisiDemographicId", "description", "entryDate", "facilityIdIntegerCompositePk", "hicSeqNo", "hiclSeqNo", "lifeStage", "onSetCode", "pickId", "reaction", "regionalIdentifier", "severityCode", "startDate", "typeCode" };
        CachedDemographicAllergy.pcFieldTypes = new Class[] { Integer.TYPE, Integer.TYPE, (CachedDemographicAllergy.class$Ljava$lang$String != null) ? CachedDemographicAllergy.class$Ljava$lang$String : (CachedDemographicAllergy.class$Ljava$lang$String = class$("java.lang.String")), Integer.TYPE, (CachedDemographicAllergy.class$Ljava$lang$String != null) ? CachedDemographicAllergy.class$Ljava$lang$String : (CachedDemographicAllergy.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicAllergy.class$Ljava$util$Date != null) ? CachedDemographicAllergy.class$Ljava$util$Date : (CachedDemographicAllergy.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), Integer.TYPE, Integer.TYPE, (CachedDemographicAllergy.class$Ljava$lang$String != null) ? CachedDemographicAllergy.class$Ljava$lang$String : (CachedDemographicAllergy.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicAllergy.class$Ljava$lang$String != null) ? CachedDemographicAllergy.class$Ljava$lang$String : (CachedDemographicAllergy.class$Ljava$lang$String = class$("java.lang.String")), Integer.TYPE, (CachedDemographicAllergy.class$Ljava$lang$String != null) ? CachedDemographicAllergy.class$Ljava$lang$String : (CachedDemographicAllergy.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicAllergy.class$Ljava$lang$String != null) ? CachedDemographicAllergy.class$Ljava$lang$String : (CachedDemographicAllergy.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicAllergy.class$Ljava$lang$String != null) ? CachedDemographicAllergy.class$Ljava$lang$String : (CachedDemographicAllergy.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicAllergy.class$Ljava$util$Date != null) ? CachedDemographicAllergy.class$Ljava$util$Date : (CachedDemographicAllergy.class$Ljava$util$Date = class$("java.util.Date")), Integer.TYPE };
        CachedDemographicAllergy.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicAllergy != null) ? CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicAllergy : (CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicAllergy = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicAllergy")), CachedDemographicAllergy.pcFieldNames, CachedDemographicAllergy.pcFieldTypes, CachedDemographicAllergy.pcFieldFlags, CachedDemographicAllergy.pcPCSuperclass, "CachedDemographicAllergy", (PersistenceCapable)new CachedDemographicAllergy());
    }
    
    /**
     * Internal utility method to load a class by name.
     * Used by OpenJPA bytecode enhancement for class literal handling.
     *
     * @param className String the fully qualified class name
     * @return Class the loaded class
     * @throws NoClassDefFoundError if the class cannot be found
     */
    static /* synthetic */ Class class$(final String className) {
        try {
            return Class.forName(className);
        }
        catch (final ClassNotFoundException ex) {
            throw new NoClassDefFoundError(ex.getMessage());
        }
    }

    /**
     * Clears all fields of this entity, resetting them to their default values.
     * Used during entity lifecycle management by OpenJPA.
     */
    protected void pcClearFields() {
        this.agccs = 0;
        this.agcsp = 0;
        this.ageOfOnset = null;
        this.caisiDemographicId = 0;
        this.description = null;
        this.entryDate = null;
        this.facilityIdIntegerCompositePk = null;
        this.hicSeqNo = 0;
        this.hiclSeqNo = 0;
        this.lifeStage = null;
        this.onSetCode = null;
        this.pickId = 0;
        this.reaction = null;
        this.regionalIdentifier = null;
        this.severityCode = null;
        this.startDate = null;
        this.typeCode = 0;
    }
    
    /**
     * Creates a new instance of this entity with the given state manager and object ID.
     * Part of the OpenJPA PersistenceCapable contract.
     *
     * @param pcStateManager StateManager the persistence state manager
     * @param o Object the object ID to copy key fields from
     * @param b boolean whether to clear all fields after creation
     * @return PersistenceCapable the new instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicAllergy cachedDemographicAllergy = new CachedDemographicAllergy();
        if (b) {
            cachedDemographicAllergy.pcClearFields();
        }
        cachedDemographicAllergy.pcStateManager = pcStateManager;
        cachedDemographicAllergy.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicAllergy;
    }

    /**
     * Creates a new instance of this entity with the given state manager.
     * Part of the OpenJPA PersistenceCapable contract.
     *
     * @param pcStateManager StateManager the persistence state manager
     * @param b boolean whether to clear all fields after creation
     * @return PersistenceCapable the new instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicAllergy cachedDemographicAllergy = new CachedDemographicAllergy();
        if (b) {
            cachedDemographicAllergy.pcClearFields();
        }
        cachedDemographicAllergy.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicAllergy;
    }
    
    /**
     * Returns the number of managed fields in this entity.
     *
     * @return int the count of managed fields (17)
     */
    protected static int pcGetManagedFieldCount() {
        return 17;
    }
    
    /**
     * Replaces a managed field value using the state manager.
     * Part of the OpenJPA field management mechanism.
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicAllergy.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.agccs = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.agcsp = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.ageOfOnset = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.caisiDemographicId = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.description = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.entryDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.hicSeqNo = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.hiclSeqNo = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.lifeStage = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 10: {
                this.onSetCode = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 11: {
                this.pickId = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 12: {
                this.reaction = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 13: {
                this.regionalIdentifier = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 14: {
                this.severityCode = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 15: {
                this.startDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 16: {
                this.typeCode = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Replaces multiple managed field values using the state manager.
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a field value to the state manager.
     * Part of the OpenJPA field management mechanism.
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedDemographicAllergy.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.agccs);
                return;
            }
            case 1: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.agcsp);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.ageOfOnset);
                return;
            }
            case 3: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.caisiDemographicId);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.description);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.entryDate);
                return;
            }
            case 6: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityIdIntegerCompositePk);
                return;
            }
            case 7: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.hicSeqNo);
                return;
            }
            case 8: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.hiclSeqNo);
                return;
            }
            case 9: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.lifeStage);
                return;
            }
            case 10: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.onSetCode);
                return;
            }
            case 11: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.pickId);
                return;
            }
            case 12: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.reaction);
                return;
            }
            case 13: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.regionalIdentifier);
                return;
            }
            case 14: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.severityCode);
                return;
            }
            case 15: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.startDate);
                return;
            }
            case 16: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.typeCode);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Provides multiple field values to the state manager.
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }

    /**
     * Copies a field value from another cached demographic allergy instance.
     * Used by OpenJPA during entity management operations.
     *
     * @param cachedDemographicAllergy CachedDemographicAllergy the source instance
     * @param n int the field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final CachedDemographicAllergy cachedDemographicAllergy, final int n) {
        final int n2 = n - CachedDemographicAllergy.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.agccs = cachedDemographicAllergy.agccs;
                return;
            }
            case 1: {
                this.agcsp = cachedDemographicAllergy.agcsp;
                return;
            }
            case 2: {
                this.ageOfOnset = cachedDemographicAllergy.ageOfOnset;
                return;
            }
            case 3: {
                this.caisiDemographicId = cachedDemographicAllergy.caisiDemographicId;
                return;
            }
            case 4: {
                this.description = cachedDemographicAllergy.description;
                return;
            }
            case 5: {
                this.entryDate = cachedDemographicAllergy.entryDate;
                return;
            }
            case 6: {
                this.facilityIdIntegerCompositePk = cachedDemographicAllergy.facilityIdIntegerCompositePk;
                return;
            }
            case 7: {
                this.hicSeqNo = cachedDemographicAllergy.hicSeqNo;
                return;
            }
            case 8: {
                this.hiclSeqNo = cachedDemographicAllergy.hiclSeqNo;
                return;
            }
            case 9: {
                this.lifeStage = cachedDemographicAllergy.lifeStage;
                return;
            }
            case 10: {
                this.onSetCode = cachedDemographicAllergy.onSetCode;
                return;
            }
            case 11: {
                this.pickId = cachedDemographicAllergy.pickId;
                return;
            }
            case 12: {
                this.reaction = cachedDemographicAllergy.reaction;
                return;
            }
            case 13: {
                this.regionalIdentifier = cachedDemographicAllergy.regionalIdentifier;
                return;
            }
            case 14: {
                this.severityCode = cachedDemographicAllergy.severityCode;
                return;
            }
            case 15: {
                this.startDate = cachedDemographicAllergy.startDate;
                return;
            }
            case 16: {
                this.typeCode = cachedDemographicAllergy.typeCode;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Copies multiple field values from another entity instance.
     *
     * @param o Object the source entity to copy from
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if state managers don't match
     * @throws IllegalStateException if state manager is null
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicAllergy cachedDemographicAllergy = (CachedDemographicAllergy)o;
        if (cachedDemographicAllergy.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicAllergy, array[i]);
        }
    }

    /**
     * Retrieves the generic context from the state manager.
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
     * Fetches the object ID from the state manager.
     *
     * @return Object the object ID, or null if no state manager
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks if this entity has been marked for deletion.
     *
     * @return boolean true if deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has been modified.
     *
     * @return boolean true if dirty (modified), false otherwise
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
     * Checks if this is a newly created entity not yet persisted.
     *
     * @return boolean true if new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is persistent (managed by JPA).
     *
     * @return boolean true if persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity is part of a transaction.
     *
     * @return boolean true if transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity is currently being serialized.
     *
     * @return boolean true if serializing, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a field as dirty (modified).
     *
     * @param s String the field name to mark as dirty
     */
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }

    /**
     * Retrieves the OpenJPA state manager for this entity.
     *
     * @return StateManager the state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version of this entity for optimistic locking.
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
     *
     * @param pcStateManager StateManager the new state manager
     * @throws SecurityException if security constraints are violated
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
     * @param fieldSupplier FieldSupplier the field supplier
     * @param o Object the object ID
     * @throws InternalException always thrown as operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields to an object ID.
     * This operation is not supported for this entity type.
     *
     * @param o Object the object ID
     * @throws InternalException always thrown as operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields from an object ID using a field consumer.
     *
     * @param fieldConsumer FieldConsumer the field consumer
     * @param o Object the object ID to copy from
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(6 + CachedDemographicAllergy.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key fields from an object ID.
     *
     * @param o Object the object ID to copy from
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     * This operation is not supported for this entity type.
     *
     * @param o Object the string representation
     * @return Object never returns
     * @throws IllegalArgumentException always thrown as this constructor is not available
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicAllergy\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance for this entity.
     *
     * @return Object the new object ID
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicAllergy != null) ? CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicAllergy : (CachedDemographicAllergy.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicAllergy = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicAllergy")), (Object)this.facilityIdIntegerCompositePk);
    }
    
    private static final int pcGetagccs(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.agccs;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 0);
        return cachedDemographicAllergy.agccs;
    }
    
    private static final void pcSetagccs(final CachedDemographicAllergy cachedDemographicAllergy, final int agccs) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.agccs = agccs;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 0, cachedDemographicAllergy.agccs, agccs, 0);
    }
    
    private static final int pcGetagcsp(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.agcsp;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 1);
        return cachedDemographicAllergy.agcsp;
    }
    
    private static final void pcSetagcsp(final CachedDemographicAllergy cachedDemographicAllergy, final int agcsp) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.agcsp = agcsp;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 1, cachedDemographicAllergy.agcsp, agcsp, 0);
    }
    
    private static final String pcGetageOfOnset(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.ageOfOnset;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 2);
        return cachedDemographicAllergy.ageOfOnset;
    }
    
    private static final void pcSetageOfOnset(final CachedDemographicAllergy cachedDemographicAllergy, final String ageOfOnset) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.ageOfOnset = ageOfOnset;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 2, cachedDemographicAllergy.ageOfOnset, ageOfOnset, 0);
    }
    
    private static final int pcGetcaisiDemographicId(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.caisiDemographicId;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 3);
        return cachedDemographicAllergy.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedDemographicAllergy cachedDemographicAllergy, final int caisiDemographicId) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 3, cachedDemographicAllergy.caisiDemographicId, caisiDemographicId, 0);
    }
    
    private static final String pcGetdescription(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.description;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 4);
        return cachedDemographicAllergy.description;
    }
    
    private static final void pcSetdescription(final CachedDemographicAllergy cachedDemographicAllergy, final String description) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.description = description;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 4, cachedDemographicAllergy.description, description, 0);
    }
    
    private static final Date pcGetentryDate(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.entryDate;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 5);
        return cachedDemographicAllergy.entryDate;
    }
    
    private static final void pcSetentryDate(final CachedDemographicAllergy cachedDemographicAllergy, final Date entryDate) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.entryDate = entryDate;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 5, (Object)cachedDemographicAllergy.entryDate, (Object)entryDate, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityIdIntegerCompositePk(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.facilityIdIntegerCompositePk;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 6);
        return cachedDemographicAllergy.facilityIdIntegerCompositePk;
    }
    
    private static final void pcSetfacilityIdIntegerCompositePk(final CachedDemographicAllergy cachedDemographicAllergy, final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 6, (Object)cachedDemographicAllergy.facilityIdIntegerCompositePk, (Object)facilityIdIntegerCompositePk, 0);
    }
    
    private static final int pcGethicSeqNo(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.hicSeqNo;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 7);
        return cachedDemographicAllergy.hicSeqNo;
    }
    
    private static final void pcSethicSeqNo(final CachedDemographicAllergy cachedDemographicAllergy, final int hicSeqNo) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.hicSeqNo = hicSeqNo;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 7, cachedDemographicAllergy.hicSeqNo, hicSeqNo, 0);
    }
    
    private static final int pcGethiclSeqNo(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.hiclSeqNo;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 8);
        return cachedDemographicAllergy.hiclSeqNo;
    }
    
    private static final void pcSethiclSeqNo(final CachedDemographicAllergy cachedDemographicAllergy, final int hiclSeqNo) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.hiclSeqNo = hiclSeqNo;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 8, cachedDemographicAllergy.hiclSeqNo, hiclSeqNo, 0);
    }
    
    private static final String pcGetlifeStage(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.lifeStage;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 9);
        return cachedDemographicAllergy.lifeStage;
    }
    
    private static final void pcSetlifeStage(final CachedDemographicAllergy cachedDemographicAllergy, final String lifeStage) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.lifeStage = lifeStage;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 9, cachedDemographicAllergy.lifeStage, lifeStage, 0);
    }
    
    private static final String pcGetonSetCode(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.onSetCode;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 10);
        return cachedDemographicAllergy.onSetCode;
    }
    
    private static final void pcSetonSetCode(final CachedDemographicAllergy cachedDemographicAllergy, final String onSetCode) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.onSetCode = onSetCode;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 10, cachedDemographicAllergy.onSetCode, onSetCode, 0);
    }
    
    private static final int pcGetpickId(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.pickId;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 11);
        return cachedDemographicAllergy.pickId;
    }
    
    private static final void pcSetpickId(final CachedDemographicAllergy cachedDemographicAllergy, final int pickId) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.pickId = pickId;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 11, cachedDemographicAllergy.pickId, pickId, 0);
    }
    
    private static final String pcGetreaction(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.reaction;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 12);
        return cachedDemographicAllergy.reaction;
    }
    
    private static final void pcSetreaction(final CachedDemographicAllergy cachedDemographicAllergy, final String reaction) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.reaction = reaction;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 12, cachedDemographicAllergy.reaction, reaction, 0);
    }
    
    private static final String pcGetregionalIdentifier(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.regionalIdentifier;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 13);
        return cachedDemographicAllergy.regionalIdentifier;
    }
    
    private static final void pcSetregionalIdentifier(final CachedDemographicAllergy cachedDemographicAllergy, final String regionalIdentifier) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.regionalIdentifier = regionalIdentifier;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 13, cachedDemographicAllergy.regionalIdentifier, regionalIdentifier, 0);
    }
    
    private static final String pcGetseverityCode(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.severityCode;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 14);
        return cachedDemographicAllergy.severityCode;
    }
    
    private static final void pcSetseverityCode(final CachedDemographicAllergy cachedDemographicAllergy, final String severityCode) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.severityCode = severityCode;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 14, cachedDemographicAllergy.severityCode, severityCode, 0);
    }
    
    private static final Date pcGetstartDate(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.startDate;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 15);
        return cachedDemographicAllergy.startDate;
    }
    
    private static final void pcSetstartDate(final CachedDemographicAllergy cachedDemographicAllergy, final Date startDate) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.startDate = startDate;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 15, (Object)cachedDemographicAllergy.startDate, (Object)startDate, 0);
    }
    
    private static final int pcGettypeCode(final CachedDemographicAllergy cachedDemographicAllergy) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            return cachedDemographicAllergy.typeCode;
        }
        cachedDemographicAllergy.pcStateManager.accessingField(CachedDemographicAllergy.pcInheritedFieldCount + 16);
        return cachedDemographicAllergy.typeCode;
    }
    
    private static final void pcSettypeCode(final CachedDemographicAllergy cachedDemographicAllergy, final int typeCode) {
        if (cachedDemographicAllergy.pcStateManager == null) {
            cachedDemographicAllergy.typeCode = typeCode;
            return;
        }
        cachedDemographicAllergy.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicAllergy, CachedDemographicAllergy.pcInheritedFieldCount + 16, cachedDemographicAllergy.typeCode, typeCode, 0);
    }
    
    /**
     * Checks if this entity is in a detached state.
     * A detached entity has been removed from the persistence context but retains its identity.
     *
     * @return Boolean TRUE if detached, FALSE if attached, null if state is indeterminate
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
     * Indicates whether the detached state is definitive.
     *
     * @return boolean false, indicating the detached state may be uncertain
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Retrieves the detached state object for this entity.
     *
     * @return Object the detached state, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state for this entity.
     *
     * @param pcDetachedState Object the detached state to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method for writing this entity to an output stream.
     * Clears the detached state if the entity is being serialized by the persistence framework.
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
     * Sets the detached state to DESERIALIZED to indicate the entity was deserialized.
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
