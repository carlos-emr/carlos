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
 * JPA entity representing cached demographic prevention data in the CAISI integrator system.
 *
 * <p>This class stores prevention and immunization records for patients across multiple healthcare
 * facilities in a federated environment. It enables the CAISI (Client Access to Integrated Services
 * and Information) integrator to cache and synchronize prevention data from different OpenO EMR
 * installations, providing a unified view of patient preventive care history.</p>
 *
 * <p>The entity tracks various aspects of preventive healthcare including:</p>
 * <ul>
 *   <li>Immunization records and vaccination schedules</li>
 *   <li>Preventive health screenings and tests</li>
 *   <li>Patient refusal of recommended preventions</li>
 *   <li>Provider-documented "never" indications for specific preventions</li>
 *   <li>Scheduled next prevention dates for follow-up care</li>
 * </ul>
 *
 * <p>This class is enhanced by Apache OpenJPA for persistence capabilities. The OpenJPA enhancement
 * process adds bytecode instrumentation to support transparent field access tracking, lazy loading,
 * and state management. All methods beginning with "pc" are part of the OpenJPA PersistenceCapable
 * interface and are used internally by the persistence framework.</p>
 *
 * <p><strong>Healthcare Context:</strong> Prevention tracking is a critical component of population
 * health management and quality improvement initiatives. This cached data supports:</p>
 * <ul>
 *   <li>Real-time access to patient prevention history across facilities</li>
 *   <li>Clinical decision support for preventive care recommendations</li>
 *   <li>Regulatory compliance reporting for immunization registries</li>
 *   <li>Quality measure calculations for preventive care metrics</li>
 * </ul>
 *
 * @see FacilityIdIntegerCompositePk
 * @see AbstractModel
 * @see PersistenceCapable
 *
 * @since 2026-01-24
 */
@Entity
public class CachedDemographicPrevention extends AbstractModel<FacilityIdIntegerCompositePk> implements PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityPreventionPk;
    @Column(nullable = false)
    @Index
    private Integer caisiDemographicId;
    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date preventionDate;
    @Column(nullable = false, length = 16)
    private String caisiProviderId;
    @Column(nullable = false, length = 32)
    private String preventionType;
    @Temporal(TemporalType.DATE)
    private Date nextDate;
    @Column(columnDefinition = "tinyint(1)")
    private boolean refused;
    @Column(columnDefinition = "tinyint(1)")
    private boolean never;
    @Column(columnDefinition = "mediumblob")
    private String attributes;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicPrevention;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Constructs a new CachedDemographicPrevention entity with default values.
     *
     * <p>Initializes all fields to their default states:</p>
     * <ul>
     *   <li>Object references are set to null</li>
     *   <li>Boolean flags (refused, never) are set to false</li>
     *   <li>Dates are set to null and must be provided before persistence</li>
     * </ul>
     *
     * <p>This no-argument constructor is required by JPA for entity instantiation
     * and should not be used directly in application code. Use the setter methods
     * to populate required fields before persisting the entity.</p>
     */
    public CachedDemographicPrevention() {
        this.facilityPreventionPk = null;
        this.caisiDemographicId = null;
        this.preventionDate = null;
        this.caisiProviderId = null;
        this.preventionType = null;
        this.nextDate = null;
        this.refused = false;
        this.never = false;
        this.attributes = null;
    }
    
    /**
     * Retrieves the composite primary key for this cached prevention record.
     *
     * <p>This method overrides the getId method from AbstractModel to provide
     * the facility-specific prevention identifier.</p>
     *
     * @return FacilityIdIntegerCompositePk the composite primary key containing
     *         the facility ID and prevention record ID
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityPreventionPk(this);
    }

    /**
     * Retrieves the composite primary key for this prevention record.
     *
     * <p>The composite key uniquely identifies a prevention record within a specific
     * facility in the federated CAISI integrator environment.</p>
     *
     * @return FacilityIdIntegerCompositePk the composite primary key, or null if not yet set
     */
    public FacilityIdIntegerCompositePk getFacilityPreventionPk() {
        return pcGetfacilityPreventionPk(this);
    }

    /**
     * Sets the composite primary key for this prevention record.
     *
     * <p>This should only be set once during entity creation and should not be modified
     * after the entity has been persisted.</p>
     *
     * @param facilityPreventionPk FacilityIdIntegerCompositePk the composite primary key
     *                             containing the facility ID and prevention record ID
     */
    public void setFacilityPreventionPk(final FacilityIdIntegerCompositePk facilityPreventionPk) {
        pcSetfacilityPreventionPk(this, facilityPreventionPk);
    }

    /**
     * Retrieves the CAISI demographic identifier for the patient.
     *
     * <p>This ID links the prevention record to a specific patient (demographic) within
     * the CAISI integrator system. It may differ from the patient's local demographic ID
     * at individual facilities.</p>
     *
     * @return Integer the CAISI demographic identifier, or null if not set
     */
    public Integer getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }

    /**
     * Sets the CAISI demographic identifier for the patient.
     *
     * <p>This field is required and must not be null for a valid prevention record.</p>
     *
     * @param caisiDemographicId Integer the CAISI demographic identifier for the patient
     */
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }

    /**
     * Retrieves the date when the prevention or immunization was administered or performed.
     *
     * <p>This is a required field representing when the preventive care activity occurred.
     * For immunizations, this is the vaccination date. For screenings, this is the test date.</p>
     *
     * @return Date the prevention administration date, or null if not set
     */
    public Date getPreventionDate() {
        return pcGetpreventionDate(this);
    }

    /**
     * Sets the date when the prevention or immunization was administered.
     *
     * <p>This field is required and must not be null for a valid prevention record.</p>
     *
     * @param preventionDate Date the date the prevention was performed
     */
    public void setPreventionDate(final Date preventionDate) {
        pcSetpreventionDate(this, preventionDate);
    }

    /**
     * Retrieves the CAISI provider identifier for the healthcare provider who administered
     * or documented the prevention.
     *
     * <p>This ID identifies the provider within the CAISI integrator system and may differ
     * from local provider identifiers at individual facilities.</p>
     *
     * @return String the CAISI provider identifier (maximum 16 characters), or null if not set
     */
    public String getCaisiProviderId() {
        return pcGetcaisiProviderId(this);
    }

    /**
     * Sets the CAISI provider identifier for the healthcare provider.
     *
     * <p>This field is required and must not be null. The value must not exceed 16 characters.</p>
     *
     * @param caisiProviderId String the CAISI provider identifier
     */
    public void setCaisiProviderId(final String caisiProviderId) {
        pcSetcaisiProviderId(this, caisiProviderId);
    }

    /**
     * Retrieves the prevention type code.
     *
     * <p>This code identifies the specific type of prevention or immunization, such as:</p>
     * <ul>
     *   <li>Immunization codes (e.g., "FLU", "MMR", "TDAP")</li>
     *   <li>Screening codes (e.g., "PAP", "MAMMO", "COLONOSCOPY")</li>
     *   <li>Other preventive care codes defined in the system</li>
     * </ul>
     *
     * @return String the prevention type code (maximum 32 characters), or null if not set
     */
    public String getPreventionType() {
        return pcGetpreventionType(this);
    }

    /**
     * Sets the prevention type code.
     *
     * <p>This field is required and must not be null. The value must not exceed 32 characters
     * and should match a valid prevention type code in the system.</p>
     *
     * @param preventionType String the prevention type code
     */
    public void setPreventionType(final String preventionType) {
        pcSetpreventionType(this, preventionType);
    }

    /**
     * Retrieves the scheduled date for the next prevention or follow-up.
     *
     * <p>This optional field indicates when the patient should receive the next dose
     * of a series (for immunizations) or when the next screening is due.</p>
     *
     * @return Date the next scheduled prevention date, or null if not applicable or not set
     */
    public Date getNextDate() {
        return pcGetnextDate(this);
    }

    /**
     * Sets the scheduled date for the next prevention or follow-up.
     *
     * <p>This field is optional and may be null if no follow-up is scheduled or required.</p>
     *
     * @param nextDate Date the next scheduled prevention date, or null if not applicable
     */
    public void setNextDate(final Date nextDate) {
        pcSetnextDate(this, nextDate);
    }

    /**
     * Retrieves the attributes blob containing additional prevention metadata.
     *
     * <p>This field stores serialized or encoded additional information about the prevention
     * that doesn't fit in the standard fields, such as:</p>
     * <ul>
     *   <li>Lot numbers for vaccines</li>
     *   <li>Manufacturer information</li>
     *   <li>Site and route of administration</li>
     *   <li>Additional clinical notes</li>
     * </ul>
     *
     * @return String the encoded attributes data, or null if no additional attributes
     */
    public String getAttributes() {
        return pcGetattributes(this);
    }

    /**
     * Sets the attributes blob for additional prevention metadata.
     *
     * <p>This field is stored as a MEDIUMBLOB in the database and can contain
     * substantial amounts of encoded data.</p>
     *
     * @param attributes String the encoded attributes data, or null if no additional attributes
     */
    public void setAttributes(final String attributes) {
        pcSetattributes(this, attributes);
    }

    /**
     * Checks if the patient refused the recommended prevention.
     *
     * <p>This flag indicates that the prevention was offered to the patient but they
     * declined to receive it. This is important for clinical decision support and
     * documentation of informed patient choice.</p>
     *
     * @return boolean true if the patient refused the prevention, false otherwise
     */
    public boolean isRefused() {
        return pcGetrefused(this);
    }

    /**
     * Sets whether the patient refused the recommended prevention.
     *
     * <p>When set to true, indicates that the prevention was offered but declined
     * by the patient. This affects clinical decision support and quality measure
     * calculations.</p>
     *
     * @param refused boolean true if the patient refused, false otherwise
     */
    public void setRefused(final boolean refused) {
        pcSetrefused(this, refused);
    }

    /**
     * Checks if the prevention should never be given to this patient.
     *
     * <p>This flag indicates a clinical contraindication or provider-documented
     * reason why this prevention should never be administered to the patient.
     * Common reasons include:</p>
     * <ul>
     *   <li>Severe allergic reactions to previous doses</li>
     *   <li>Medical contraindications</li>
     *   <li>Patient has completed lifetime series</li>
     * </ul>
     *
     * @return boolean true if the prevention should never be given, false otherwise
     */
    public boolean isNever() {
        return pcGetnever(this);
    }

    /**
     * Sets whether the prevention should never be given to this patient.
     *
     * <p>When set to true, clinical decision support systems should not recommend
     * this prevention for the patient. This flag should only be set by healthcare
     * providers with appropriate clinical justification.</p>
     *
     * @param never boolean true if the prevention should never be given, false otherwise
     */
    public void setNever(final boolean never) {
        pcSetnever(this, never);
    }
    
    /**
     * Returns the OpenJPA enhancement contract version for this entity.
     *
     * <p>This method is part of the PersistenceCapable interface and is used by
     * OpenJPA to verify bytecode enhancement compatibility. The version number
     * indicates which enhancement features are supported by this entity class.</p>
     *
     * @return int the enhancement contract version (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -5461784508345120397L;
        CachedDemographicPrevention.pcFieldNames = new String[] { "attributes", "caisiDemographicId", "caisiProviderId", "facilityPreventionPk", "never", "nextDate", "preventionDate", "preventionType", "refused" };
        CachedDemographicPrevention.pcFieldTypes = new Class[] { (CachedDemographicPrevention.class$Ljava$lang$String != null) ? CachedDemographicPrevention.class$Ljava$lang$String : (CachedDemographicPrevention.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicPrevention.class$Ljava$lang$Integer != null) ? CachedDemographicPrevention.class$Ljava$lang$Integer : (CachedDemographicPrevention.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedDemographicPrevention.class$Ljava$lang$String != null) ? CachedDemographicPrevention.class$Ljava$lang$String : (CachedDemographicPrevention.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicPrevention.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedDemographicPrevention.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedDemographicPrevention.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), Boolean.TYPE, (CachedDemographicPrevention.class$Ljava$util$Date != null) ? CachedDemographicPrevention.class$Ljava$util$Date : (CachedDemographicPrevention.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographicPrevention.class$Ljava$util$Date != null) ? CachedDemographicPrevention.class$Ljava$util$Date : (CachedDemographicPrevention.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographicPrevention.class$Ljava$lang$String != null) ? CachedDemographicPrevention.class$Ljava$lang$String : (CachedDemographicPrevention.class$Ljava$lang$String = class$("java.lang.String")), Boolean.TYPE };
        CachedDemographicPrevention.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographicPrevention.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicPrevention != null) ? CachedDemographicPrevention.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicPrevention : (CachedDemographicPrevention.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicPrevention = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicPrevention")), CachedDemographicPrevention.pcFieldNames, CachedDemographicPrevention.pcFieldTypes, CachedDemographicPrevention.pcFieldFlags, CachedDemographicPrevention.pcPCSuperclass, "CachedDemographicPrevention", (PersistenceCapable)new CachedDemographicPrevention());
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
        this.attributes = null;
        this.caisiDemographicId = null;
        this.caisiProviderId = null;
        this.facilityPreventionPk = null;
        this.never = false;
        this.nextDate = null;
        this.preventionDate = null;
        this.preventionType = null;
        this.refused = false;
    }
    
    /**
     * Creates a new instance of this entity with the specified StateManager and object ID.
     *
     * <p>This method is called by OpenJPA when creating instances from the database or
     * during entity lifecycle operations. It initializes the entity with the provided
     * StateManager for persistence tracking.</p>
     *
     * @param pcStateManager StateManager the OpenJPA state manager for this instance
     * @param o Object the object ID to copy key fields from
     * @param b boolean if true, clears all non-key fields to default values
     * @return PersistenceCapable a new entity instance with the specified configuration
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicPrevention cachedDemographicPrevention = new CachedDemographicPrevention();
        if (b) {
            cachedDemographicPrevention.pcClearFields();
        }
        cachedDemographicPrevention.pcStateManager = pcStateManager;
        cachedDemographicPrevention.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicPrevention;
    }

    /**
     * Creates a new instance of this entity with the specified StateManager.
     *
     * <p>This overloaded method is called by OpenJPA when creating new instances
     * without a predefined object ID.</p>
     *
     * @param pcStateManager StateManager the OpenJPA state manager for this instance
     * @param b boolean if true, clears all fields to default values
     * @return PersistenceCapable a new entity instance with the specified configuration
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicPrevention cachedDemographicPrevention = new CachedDemographicPrevention();
        if (b) {
            cachedDemographicPrevention.pcClearFields();
        }
        cachedDemographicPrevention.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicPrevention;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 9;
    }
    
    /**
     * Replaces a single field value from the OpenJPA StateManager.
     *
     * <p>This method is called by OpenJPA during entity refresh operations to update
     * field values from the database. The field to replace is identified by its index.</p>
     *
     * @param n int the field index to replace (relative to pcInheritedFieldCount)
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicPrevention.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.attributes = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.caisiDemographicId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.caisiProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.facilityPreventionPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.never = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.nextDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.preventionDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.preventionType = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.refused = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Replaces multiple field values from the OpenJPA StateManager.
     *
     * <p>This method is an optimized version of pcReplaceField that updates multiple
     * fields in a single call during entity refresh operations.</p>
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single field value to the OpenJPA StateManager.
     *
     * <p>This method is called by OpenJPA during entity flush operations to read
     * field values for persistence to the database.</p>
     *
     * @param n int the field index to provide (relative to pcInheritedFieldCount)
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedDemographicPrevention.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.attributes);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiDemographicId);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.caisiProviderId);
                return;
            }
            case 3: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityPreventionPk);
                return;
            }
            case 4: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.never);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.nextDate);
                return;
            }
            case 6: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.preventionDate);
                return;
            }
            case 7: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.preventionType);
                return;
            }
            case 8: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.refused);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    /**
     * Provides multiple field values to the OpenJPA StateManager.
     *
     * <p>This method is an optimized version of pcProvideField that reads multiple
     * fields in a single call during entity flush operations.</p>
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    protected void pcCopyField(final CachedDemographicPrevention cachedDemographicPrevention, final int n) {
        final int n2 = n - CachedDemographicPrevention.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.attributes = cachedDemographicPrevention.attributes;
                return;
            }
            case 1: {
                this.caisiDemographicId = cachedDemographicPrevention.caisiDemographicId;
                return;
            }
            case 2: {
                this.caisiProviderId = cachedDemographicPrevention.caisiProviderId;
                return;
            }
            case 3: {
                this.facilityPreventionPk = cachedDemographicPrevention.facilityPreventionPk;
                return;
            }
            case 4: {
                this.never = cachedDemographicPrevention.never;
                return;
            }
            case 5: {
                this.nextDate = cachedDemographicPrevention.nextDate;
                return;
            }
            case 6: {
                this.preventionDate = cachedDemographicPrevention.preventionDate;
                return;
            }
            case 7: {
                this.preventionType = cachedDemographicPrevention.preventionType;
                return;
            }
            case 8: {
                this.refused = cachedDemographicPrevention.refused;
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
     * <p>This method is used by OpenJPA during entity merge and refresh operations
     * to copy field values between instances managed by the same StateManager.</p>
     *
     * @param o Object the source entity to copy fields from (must be CachedDemographicPrevention)
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source entity has a different StateManager
     * @throws IllegalStateException if this entity has no StateManager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicPrevention cachedDemographicPrevention = (CachedDemographicPrevention)o;
        if (cachedDemographicPrevention.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicPrevention, array[i]);
        }
    }

    /**
     * Retrieves the generic context object from the OpenJPA StateManager.
     *
     * <p>The generic context provides access to the persistence context and
     * EntityManager for advanced operations.</p>
     *
     * @return Object the generic context, or null if no StateManager is attached
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }
    
    /**
     * Retrieves the object ID for this entity.
     *
     * <p>The object ID uniquely identifies this entity instance within the
     * persistence context and database.</p>
     *
     * @return Object the object ID, or null if no StateManager is attached
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks if this entity has been deleted.
     *
     * <p>An entity is considered deleted if it has been marked for removal
     * in the current transaction but not yet flushed to the database.</p>
     *
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has been modified since being loaded.
     *
     * <p>This method triggers a dirty check through the StateManager to determine
     * if any fields have been changed and need to be persisted.</p>
     *
     * @return boolean true if the entity has unsaved changes, false otherwise
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
     *
     * <p>An entity is considered new if it has been instantiated but not yet
     * flushed to the database.</p>
     *
     * @return boolean true if the entity is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is managed by a persistence context.
     *
     * <p>A persistent entity is one that has a database representation and is
     * being tracked by the EntityManager.</p>
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity is participating in a transaction.
     *
     * <p>A transactional entity is one that will be included in the current
     * transaction's flush and commit operations.</p>
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity is currently being serialized.
     *
     * <p>This method is used internally during serialization to ensure proper
     * handling of transient fields and detached state.</p>
     *
     * @return boolean true if the entity is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a specific field as dirty, requiring persistence on next flush.
     *
     * <p>This method is typically called by field setters to notify the StateManager
     * that a field has been modified and needs to be updated in the database.</p>
     *
     * @param s String the name of the field that was modified
     */
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }

    /**
     * Retrieves the OpenJPA StateManager managing this entity.
     *
     * <p>The StateManager tracks the entity's lifecycle, manages field access,
     * and coordinates persistence operations.</p>
     *
     * @return StateManager the state manager, or null if the entity is not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version identifier for optimistic locking.
     *
     * <p>The version is used to detect concurrent modifications and prevent
     * lost updates in multi-user environments.</p>
     *
     * @return Object the version identifier, or null if no StateManager is attached
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces or sets the StateManager for this entity.
     *
     * <p>This method is called by OpenJPA during entity attachment, detachment,
     * and state transitions. If a StateManager is already attached, it delegates
     * to that manager to handle the replacement.</p>
     *
     * @param pcStateManager StateManager the new state manager to attach
     * @throws SecurityException if the StateManager replacement is not allowed
     */
    public void pcReplaceStateManager(final StateManager pcStateManager) throws SecurityException {
        if (this.pcStateManager != null) {
            this.pcStateManager = this.pcStateManager.replaceStateManager(pcStateManager);
            return;
        }
        this.pcStateManager = pcStateManager;
    }
    
    /**
     * Copies key fields to an object ID using a FieldSupplier.
     *
     * <p>This method is not supported for this entity type and will always throw
     * an InternalException. Key field copying is handled through the alternate
     * method signatures.</p>
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
     *
     * <p>This method is not supported for this entity type and will always throw
     * an InternalException. This entity uses embedded composite keys which are
     * handled differently by OpenJPA.</p>
     *
     * @param o Object the object ID (not used)
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields from an object ID using a FieldConsumer.
     *
     * <p>This method extracts the composite primary key from the provided ObjectId
     * and stores it into the entity using the FieldConsumer interface.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer to receive the key field
     * @param o Object the ObjectId containing the composite key
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(3 + CachedDemographicPrevention.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key fields from an object ID directly to this entity.
     *
     * <p>This method extracts the composite primary key from the provided ObjectId
     * and sets it as this entity's facilityPreventionPk field.</p>
     *
     * @param o Object the ObjectId containing the composite key (FacilityIdIntegerCompositePk)
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityPreventionPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * <p>This method is not supported for this entity type because the composite key
     * type does not have a string constructor. Use the no-argument variant instead.</p>
     *
     * @param o Object the string representation of the ID (not used)
     * @return Object never returns
     * @throws IllegalArgumentException always thrown as this operation is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicPrevention\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance from this entity's current key fields.
     *
     * <p>This method constructs an OpenJPA ObjectId containing the current
     * facilityPreventionPk composite key value.</p>
     *
     * @return Object a new ObjectId for this entity
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicPrevention.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicPrevention != null) ? CachedDemographicPrevention.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicPrevention : (CachedDemographicPrevention.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicPrevention = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicPrevention")), (Object)this.facilityPreventionPk);
    }
    
    private static final String pcGetattributes(final CachedDemographicPrevention cachedDemographicPrevention) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            return cachedDemographicPrevention.attributes;
        }
        cachedDemographicPrevention.pcStateManager.accessingField(CachedDemographicPrevention.pcInheritedFieldCount + 0);
        return cachedDemographicPrevention.attributes;
    }
    
    private static final void pcSetattributes(final CachedDemographicPrevention cachedDemographicPrevention, final String attributes) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            cachedDemographicPrevention.attributes = attributes;
            return;
        }
        cachedDemographicPrevention.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicPrevention, CachedDemographicPrevention.pcInheritedFieldCount + 0, cachedDemographicPrevention.attributes, attributes, 0);
    }
    
    private static final Integer pcGetcaisiDemographicId(final CachedDemographicPrevention cachedDemographicPrevention) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            return cachedDemographicPrevention.caisiDemographicId;
        }
        cachedDemographicPrevention.pcStateManager.accessingField(CachedDemographicPrevention.pcInheritedFieldCount + 1);
        return cachedDemographicPrevention.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedDemographicPrevention cachedDemographicPrevention, final Integer caisiDemographicId) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            cachedDemographicPrevention.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedDemographicPrevention.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicPrevention, CachedDemographicPrevention.pcInheritedFieldCount + 1, (Object)cachedDemographicPrevention.caisiDemographicId, (Object)caisiDemographicId, 0);
    }
    
    private static final String pcGetcaisiProviderId(final CachedDemographicPrevention cachedDemographicPrevention) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            return cachedDemographicPrevention.caisiProviderId;
        }
        cachedDemographicPrevention.pcStateManager.accessingField(CachedDemographicPrevention.pcInheritedFieldCount + 2);
        return cachedDemographicPrevention.caisiProviderId;
    }
    
    private static final void pcSetcaisiProviderId(final CachedDemographicPrevention cachedDemographicPrevention, final String caisiProviderId) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            cachedDemographicPrevention.caisiProviderId = caisiProviderId;
            return;
        }
        cachedDemographicPrevention.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicPrevention, CachedDemographicPrevention.pcInheritedFieldCount + 2, cachedDemographicPrevention.caisiProviderId, caisiProviderId, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityPreventionPk(final CachedDemographicPrevention cachedDemographicPrevention) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            return cachedDemographicPrevention.facilityPreventionPk;
        }
        cachedDemographicPrevention.pcStateManager.accessingField(CachedDemographicPrevention.pcInheritedFieldCount + 3);
        return cachedDemographicPrevention.facilityPreventionPk;
    }
    
    private static final void pcSetfacilityPreventionPk(final CachedDemographicPrevention cachedDemographicPrevention, final FacilityIdIntegerCompositePk facilityPreventionPk) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            cachedDemographicPrevention.facilityPreventionPk = facilityPreventionPk;
            return;
        }
        cachedDemographicPrevention.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicPrevention, CachedDemographicPrevention.pcInheritedFieldCount + 3, (Object)cachedDemographicPrevention.facilityPreventionPk, (Object)facilityPreventionPk, 0);
    }
    
    private static final boolean pcGetnever(final CachedDemographicPrevention cachedDemographicPrevention) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            return cachedDemographicPrevention.never;
        }
        cachedDemographicPrevention.pcStateManager.accessingField(CachedDemographicPrevention.pcInheritedFieldCount + 4);
        return cachedDemographicPrevention.never;
    }
    
    private static final void pcSetnever(final CachedDemographicPrevention cachedDemographicPrevention, final boolean never) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            cachedDemographicPrevention.never = never;
            return;
        }
        cachedDemographicPrevention.pcStateManager.settingBooleanField((PersistenceCapable)cachedDemographicPrevention, CachedDemographicPrevention.pcInheritedFieldCount + 4, cachedDemographicPrevention.never, never, 0);
    }
    
    private static final Date pcGetnextDate(final CachedDemographicPrevention cachedDemographicPrevention) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            return cachedDemographicPrevention.nextDate;
        }
        cachedDemographicPrevention.pcStateManager.accessingField(CachedDemographicPrevention.pcInheritedFieldCount + 5);
        return cachedDemographicPrevention.nextDate;
    }
    
    private static final void pcSetnextDate(final CachedDemographicPrevention cachedDemographicPrevention, final Date nextDate) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            cachedDemographicPrevention.nextDate = nextDate;
            return;
        }
        cachedDemographicPrevention.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicPrevention, CachedDemographicPrevention.pcInheritedFieldCount + 5, (Object)cachedDemographicPrevention.nextDate, (Object)nextDate, 0);
    }
    
    private static final Date pcGetpreventionDate(final CachedDemographicPrevention cachedDemographicPrevention) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            return cachedDemographicPrevention.preventionDate;
        }
        cachedDemographicPrevention.pcStateManager.accessingField(CachedDemographicPrevention.pcInheritedFieldCount + 6);
        return cachedDemographicPrevention.preventionDate;
    }
    
    private static final void pcSetpreventionDate(final CachedDemographicPrevention cachedDemographicPrevention, final Date preventionDate) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            cachedDemographicPrevention.preventionDate = preventionDate;
            return;
        }
        cachedDemographicPrevention.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicPrevention, CachedDemographicPrevention.pcInheritedFieldCount + 6, (Object)cachedDemographicPrevention.preventionDate, (Object)preventionDate, 0);
    }
    
    private static final String pcGetpreventionType(final CachedDemographicPrevention cachedDemographicPrevention) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            return cachedDemographicPrevention.preventionType;
        }
        cachedDemographicPrevention.pcStateManager.accessingField(CachedDemographicPrevention.pcInheritedFieldCount + 7);
        return cachedDemographicPrevention.preventionType;
    }
    
    private static final void pcSetpreventionType(final CachedDemographicPrevention cachedDemographicPrevention, final String preventionType) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            cachedDemographicPrevention.preventionType = preventionType;
            return;
        }
        cachedDemographicPrevention.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicPrevention, CachedDemographicPrevention.pcInheritedFieldCount + 7, cachedDemographicPrevention.preventionType, preventionType, 0);
    }
    
    private static final boolean pcGetrefused(final CachedDemographicPrevention cachedDemographicPrevention) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            return cachedDemographicPrevention.refused;
        }
        cachedDemographicPrevention.pcStateManager.accessingField(CachedDemographicPrevention.pcInheritedFieldCount + 8);
        return cachedDemographicPrevention.refused;
    }
    
    private static final void pcSetrefused(final CachedDemographicPrevention cachedDemographicPrevention, final boolean refused) {
        if (cachedDemographicPrevention.pcStateManager == null) {
            cachedDemographicPrevention.refused = refused;
            return;
        }
        cachedDemographicPrevention.pcStateManager.settingBooleanField((PersistenceCapable)cachedDemographicPrevention, CachedDemographicPrevention.pcInheritedFieldCount + 8, cachedDemographicPrevention.refused, refused, 0);
    }
    
    /**
     * Checks if this entity is in a detached state.
     *
     * <p>A detached entity is one that was previously managed by a persistence context
     * but is no longer associated with an active EntityManager. Detached entities can
     * be modified and later reattached (merged) into a new persistence context.</p>
     *
     * <p>This method returns a Boolean object (not primitive) to allow for three states:</p>
     * <ul>
     *   <li>TRUE - entity is definitively detached</li>
     *   <li>FALSE - entity is definitively not detached (attached or transient)</li>
     *   <li>null - detachment state cannot be determined</li>
     * </ul>
     *
     * @return Boolean TRUE if detached, FALSE if attached/transient, null if indeterminate
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
    
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }
    
    /**
     * Retrieves the detached state object for this entity.
     *
     * <p>The detached state contains metadata about the entity when it was detached
     * from its persistence context. This is used by OpenJPA to manage entity merging
     * and version checking.</p>
     *
     * @return Object the detached state, or null if the entity has never been detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object for this entity.
     *
     * <p>This method is called by OpenJPA during detachment and serialization operations
     * to store metadata needed for later reattachment.</p>
     *
     * @param pcDetachedState Object the detached state to store
     */
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
