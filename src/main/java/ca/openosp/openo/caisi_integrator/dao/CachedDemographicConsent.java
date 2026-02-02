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
import java.util.HashMap;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.ElementCollection;
import javax.persistence.CollectionTable;
import javax.persistence.MapKeyColumn;
import javax.persistence.Column;
import java.util.Map;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * Represents cached patient demographic consent data for the CAISI Integrator system.
 *
 * <p>This entity manages patient consent information for sharing demographic and clinical data
 * across multiple healthcare facilities within the integrated care network. It supports
 * facility-specific consent preferences, mental health data exclusion, consent expiration,
 * and various consent status states (GIVEN, REVOKED).</p>
 *
 * <p>The class is enhanced by OpenJPA for persistence capability, providing automatic
 * state management and field-level access tracking. All persistence operations are
 * delegated to the OpenJPA StateManager for transaction consistency.</p>
 *
 * <p><strong>Healthcare Context:</strong> In multi-facility integrated care environments,
 * patient consent must be tracked per facility to comply with privacy regulations (PIPEDA/HIPAA).
 * This entity caches consent decisions to minimize database lookups during patient data
 * retrieval operations.</p>
 *
 * @see FacilityIdIntegerCompositePk
 * @see AbstractModel
 * @see ConsentStatus
 * @since 2026-01-24
 */
@Entity
public class CachedDemographicConsent extends AbstractModel<FacilityIdIntegerCompositePk> implements PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityDemographicPk;
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "CachedDemographicConsentConsents")
    @MapKeyColumn(name = "integratorFacilityId")
    @Column(name = "shareData", columnDefinition = "tinyint(1)")
    private Map<Integer, Boolean> consentToShareData;
    private boolean excludeMentalHealthData;
    @Enumerated(EnumType.STRING)
    private ConsentStatus clientConsentStatus;
    @Temporal(TemporalType.DATE)
    private Date expiry;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicConsent$ConsentStatus;
    static /* synthetic */ Class class$Ljava$util$Map;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicConsent;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Creates a new CachedDemographicConsent instance with default values.
     *
     * <p>Initializes the consent data map, sets default exclusion flags, and prepares
     * the entity for persistence. All date fields and consent status are initialized
     * to null, requiring explicit setting before persistence.</p>
     */
    public CachedDemographicConsent() {
        this.createdDate = null;
        this.consentToShareData = new HashMap<Integer, Boolean>();
        this.excludeMentalHealthData = false;
        this.clientConsentStatus = null;
        this.expiry = null;
    }

    /**
     * Retrieves the composite primary key for this consent record.
     *
     * <p>The primary key combines the facility ID and demographic ID,
     * uniquely identifying a patient's consent record within a specific facility.</p>
     *
     * @return FacilityIdIntegerCompositePk the composite primary key containing facility and demographic identifiers
     */
    public FacilityIdIntegerCompositePk getFacilityDemographicPk() {
        return pcGetfacilityDemographicPk(this);
    }

    /**
     * Sets the composite primary key for this consent record.
     *
     * <p>This method should be called when creating a new consent record to establish
     * the association between a patient (demographic) and a facility.</p>
     *
     * @param facilityDemographicPk FacilityIdIntegerCompositePk the composite key to assign
     */
    public void setFacilityDemographicPk(final FacilityIdIntegerCompositePk facilityDemographicPk) {
        pcSetfacilityDemographicPk(this, facilityDemographicPk);
    }

    /**
     * Retrieves the timestamp when this consent record was created.
     *
     * @return Date the creation timestamp, or null if not yet set
     */
    public Date getCreatedDate() {
        return pcGetcreatedDate(this);
    }

    /**
     * Sets the creation timestamp for this consent record.
     *
     * @param createdDate Date the timestamp to set
     */
    public void setCreatedDate(final Date createdDate) {
        pcSetcreatedDate(this, createdDate);
    }

    /**
     * Retrieves the entity identifier.
     *
     * <p>This method overrides the parent AbstractModel getId() method to return
     * the composite primary key as the entity identifier.</p>
     *
     * @return FacilityIdIntegerCompositePk the entity's composite primary key
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityDemographicPk(this);
    }

    /**
     * Retrieves the facility-specific consent preferences map.
     *
     * <p>This map contains consent decisions for each integrator facility,
     * where the key is the integrator facility ID and the value indicates
     * whether the patient consents to share data with that facility.</p>
     *
     * @return Map&lt;Integer, Boolean&gt; map of facility IDs to consent decisions
     */
    public Map<Integer, Boolean> getConsentToShareData() {
        return pcGetconsentToShareData(this);
    }

    /**
     * Sets the facility-specific consent preferences map.
     *
     * @param consentToShareData Map&lt;Integer, Boolean&gt; map of facility IDs to consent decisions
     */
    public void setConsentToShareData(final Map<Integer, Boolean> consentToShareData) {
        pcSetconsentToShareData(this, consentToShareData);
    }

    /**
     * Determines whether patient data can be shared with a specific facility.
     *
     * <p>This method evaluates multiple criteria to determine data sharing permission:</p>
     * <ul>
     *   <li>Consent status must be GIVEN (not REVOKED)</li>
     *   <li>Consent must not be expired (expiry date must be in the future or null)</li>
     *   <li>Facility-specific consent must be true or unspecified (defaults to true)</li>
     * </ul>
     *
     * <p><strong>Privacy Compliance:</strong> This method implements PIPEDA/HIPAA-compliant
     * consent verification, ensuring patient data is only shared when explicitly permitted
     * and within valid time bounds.</p>
     *
     * @param integratorFacilityId Integer the integrator facility ID to check consent for
     * @return boolean true if data sharing is allowed, false otherwise
     */
    public boolean allowedToShareData(final Integer integratorFacilityId) {
        if (pcGetclientConsentStatus(this) != ConsentStatus.GIVEN) {
            return false;
        }
        if (pcGetexpiry(this) != null && pcGetexpiry(this).before(new Date())) {
            return false;
        }
        final Boolean result = (Boolean)((Map)pcGetconsentToShareData(this)).get(integratorFacilityId);
        return result == null || result;
    }

    /**
     * Checks whether mental health data should be excluded from sharing.
     *
     * <p>Patients may consent to general data sharing while excluding sensitive
     * mental health information due to stigma or privacy concerns.</p>
     *
     * @return boolean true if mental health data should be excluded, false otherwise
     */
    public boolean isExcludeMentalHealthData() {
        return pcGetexcludeMentalHealthData(this);
    }

    /**
     * Sets whether mental health data should be excluded from sharing.
     *
     * @param excludeMentalHealthData boolean true to exclude mental health data, false to include
     */
    public void setExcludeMentalHealthData(final boolean excludeMentalHealthData) {
        pcSetexcludeMentalHealthData(this, excludeMentalHealthData);
    }

    /**
     * Retrieves the current consent status.
     *
     * @return ConsentStatus the consent status (GIVEN or REVOKED), or null if not set
     */
    public ConsentStatus getClientConsentStatus() {
        return pcGetclientConsentStatus(this);
    }

    /**
     * Sets the consent status.
     *
     * @param clientConsentStatus ConsentStatus the status to set (GIVEN or REVOKED)
     */
    public void setClientConsentStatus(final ConsentStatus clientConsentStatus) {
        pcSetclientConsentStatus(this, clientConsentStatus);
    }

    /**
     * Retrieves the consent expiry date.
     *
     * <p>If null, the consent does not expire. If set, consent is only valid
     * until the specified date.</p>
     *
     * @return Date the expiry date, or null if no expiration is set
     */
    public Date getExpiry() {
        return pcGetexpiry(this);
    }

    /**
     * Sets the consent expiry date.
     *
     * @param expiry Date the expiry date to set, or null for no expiration
     */
    public void setExpiry(final Date expiry) {
        pcSetexpiry(this, expiry);
    }

    /**
     * Returns the OpenJPA enhancement contract version.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and
     * indicates the version of the bytecode enhancement applied to this class.</p>
     *
     * @return int the enhancement contract version (2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -7209077813006175494L;
        CachedDemographicConsent.pcFieldNames = new String[] { "clientConsentStatus", "consentToShareData", "createdDate", "excludeMentalHealthData", "expiry", "facilityDemographicPk" };
        CachedDemographicConsent.pcFieldTypes = new Class[] { (CachedDemographicConsent.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicConsent$ConsentStatus != null) ? CachedDemographicConsent.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicConsent$ConsentStatus : (CachedDemographicConsent.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicConsent$ConsentStatus = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicConsent$ConsentStatus")), (CachedDemographicConsent.class$Ljava$util$Map != null) ? CachedDemographicConsent.class$Ljava$util$Map : (CachedDemographicConsent.class$Ljava$util$Map = class$("java.util.Map")), (CachedDemographicConsent.class$Ljava$util$Date != null) ? CachedDemographicConsent.class$Ljava$util$Date : (CachedDemographicConsent.class$Ljava$util$Date = class$("java.util.Date")), Boolean.TYPE, (CachedDemographicConsent.class$Ljava$util$Date != null) ? CachedDemographicConsent.class$Ljava$util$Date : (CachedDemographicConsent.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographicConsent.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedDemographicConsent.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedDemographicConsent.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")) };
        CachedDemographicConsent.pcFieldFlags = new byte[] { 26, 10, 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographicConsent.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicConsent != null) ? CachedDemographicConsent.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicConsent : (CachedDemographicConsent.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicConsent = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicConsent")), CachedDemographicConsent.pcFieldNames, CachedDemographicConsent.pcFieldTypes, CachedDemographicConsent.pcFieldFlags, CachedDemographicConsent.pcPCSuperclass, "CachedDemographicConsent", (PersistenceCapable)new CachedDemographicConsent());
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
        this.clientConsentStatus = null;
        this.consentToShareData = null;
        this.createdDate = null;
        this.excludeMentalHealthData = false;
        this.expiry = null;
        this.facilityDemographicPk = null;
    }

    /**
     * Creates a new instance of this persistent class with a StateManager and object ID.
     *
     * <p>This method is called by OpenJPA during entity instantiation when an object ID
     * is available. It creates a new instance, optionally clears fields, assigns the
     * StateManager, and copies key fields from the object ID.</p>
     *
     * @param pcStateManager StateManager the state manager to assign to the new instance
     * @param o Object the object ID containing key field values
     * @param b boolean true to clear all fields after instantiation
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicConsent cachedDemographicConsent = new CachedDemographicConsent();
        if (b) {
            cachedDemographicConsent.pcClearFields();
        }
        cachedDemographicConsent.pcStateManager = pcStateManager;
        cachedDemographicConsent.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicConsent;
    }

    /**
     * Creates a new instance of this persistent class with a StateManager.
     *
     * <p>This method is called by OpenJPA during entity instantiation when no object ID
     * is available. It creates a new instance, optionally clears fields, and assigns
     * the StateManager.</p>
     *
     * @param pcStateManager StateManager the state manager to assign to the new instance
     * @param b boolean true to clear all fields after instantiation
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicConsent cachedDemographicConsent = new CachedDemographicConsent();
        if (b) {
            cachedDemographicConsent.pcClearFields();
        }
        cachedDemographicConsent.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicConsent;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 6;
    }

    /**
     * Replaces a single field value using the StateManager.
     *
     * <p>This method is called by OpenJPA to replace field values during merge,
     * refresh, or other state management operations. The field index is adjusted
     * for inheritance and mapped to the appropriate field.</p>
     *
     * @param n int the absolute field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicConsent.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.clientConsentStatus = (ConsentStatus)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.consentToShareData = (Map)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.createdDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.excludeMentalHealthData = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.expiry = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.facilityDemographicPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple field values using the StateManager.
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single field value to the StateManager.
     *
     * <p>This method is called by OpenJPA to read field values during persistence
     * operations. The field index is adjusted for inheritance and the appropriate
     * field value is provided to the StateManager.</p>
     *
     * @param n int the absolute field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedDemographicConsent.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.clientConsentStatus);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.consentToShareData);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.createdDate);
                return;
            }
            case 3: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.excludeMentalHealthData);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.expiry);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityDemographicPk);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Provides multiple field values to the StateManager.
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    protected void pcCopyField(final CachedDemographicConsent cachedDemographicConsent, final int n) {
        final int n2 = n - CachedDemographicConsent.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.clientConsentStatus = cachedDemographicConsent.clientConsentStatus;
                return;
            }
            case 1: {
                this.consentToShareData = cachedDemographicConsent.consentToShareData;
                return;
            }
            case 2: {
                this.createdDate = cachedDemographicConsent.createdDate;
                return;
            }
            case 3: {
                this.excludeMentalHealthData = cachedDemographicConsent.excludeMentalHealthData;
                return;
            }
            case 4: {
                this.expiry = cachedDemographicConsent.expiry;
                return;
            }
            case 5: {
                this.facilityDemographicPk = cachedDemographicConsent.facilityDemographicPk;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies field values from another instance.
     *
     * <p>This method is called by OpenJPA to copy field values between instances
     * during merge or clone operations. Both instances must be managed by the
     * same StateManager.</p>
     *
     * @param o Object the source instance to copy from
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the instances have different StateManagers
     * @throws IllegalStateException if this instance has no StateManager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicConsent cachedDemographicConsent = (CachedDemographicConsent)o;
        if (cachedDemographicConsent.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicConsent, array[i]);
        }
    }

    /**
     * Retrieves the generic context from the StateManager.
     *
     * @return Object the generic context, or null if no StateManager is present
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the object ID from the StateManager.
     *
     * @return Object the object ID, or null if no StateManager is present
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks if this instance has been deleted.
     *
     * @return boolean true if deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this instance has been modified.
     *
     * @return boolean true if modified since last persistence operation, false otherwise
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
     * Checks if this instance is newly created and not yet persisted.
     *
     * @return boolean true if new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this instance is persistent (managed by the persistence context).
     *
     * @return boolean true if persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this instance is transactional.
     *
     * @return boolean true if participating in a transaction, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this instance is currently being serialized.
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
     * Retrieves the StateManager for this instance.
     *
     * @return StateManager the state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version object for optimistic locking.
     *
     * @return Object the version, or null if no StateManager is present
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces the StateManager for this instance.
     *
     * @param pcStateManager StateManager the new state manager to assign
     * @throws SecurityException if the operation violates security constraints
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
     * <p>This operation is not supported for this entity type.</p>
     *
     * @param fieldSupplier FieldSupplier the field supplier
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields to an object ID.
     *
     * <p>This operation is not supported for this entity type.</p>
     *
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields from an object ID using a FieldConsumer.
     *
     * @param fieldConsumer FieldConsumer the field consumer to store field values
     * @param o Object the source object ID
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(5 + CachedDemographicConsent.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key fields from an object ID.
     *
     * @param o Object the source object ID containing the facility demographic primary key
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityDemographicPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string.
     *
     * <p>This operation is not supported for this entity type as ObjectId
     * does not have a String constructor.</p>
     *
     * @param o Object the string representation
     * @return Object never returns (always throws exception)
     * @throws IllegalArgumentException always thrown as this operation is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicConsent\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance from the current key field values.
     *
     * @return Object the newly created ObjectId containing this instance's primary key
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicConsent.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicConsent != null) ? CachedDemographicConsent.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicConsent : (CachedDemographicConsent.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicConsent = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicConsent")), (Object)this.facilityDemographicPk);
    }
    
    private static final ConsentStatus pcGetclientConsentStatus(final CachedDemographicConsent cachedDemographicConsent) {
        if (cachedDemographicConsent.pcStateManager == null) {
            return cachedDemographicConsent.clientConsentStatus;
        }
        cachedDemographicConsent.pcStateManager.accessingField(CachedDemographicConsent.pcInheritedFieldCount + 0);
        return cachedDemographicConsent.clientConsentStatus;
    }
    
    private static final void pcSetclientConsentStatus(final CachedDemographicConsent cachedDemographicConsent, final ConsentStatus clientConsentStatus) {
        if (cachedDemographicConsent.pcStateManager == null) {
            cachedDemographicConsent.clientConsentStatus = clientConsentStatus;
            return;
        }
        cachedDemographicConsent.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicConsent, CachedDemographicConsent.pcInheritedFieldCount + 0, (Object)cachedDemographicConsent.clientConsentStatus, (Object)clientConsentStatus, 0);
    }
    
    private static final Map pcGetconsentToShareData(final CachedDemographicConsent cachedDemographicConsent) {
        if (cachedDemographicConsent.pcStateManager == null) {
            return cachedDemographicConsent.consentToShareData;
        }
        cachedDemographicConsent.pcStateManager.accessingField(CachedDemographicConsent.pcInheritedFieldCount + 1);
        return cachedDemographicConsent.consentToShareData;
    }
    
    private static final void pcSetconsentToShareData(final CachedDemographicConsent cachedDemographicConsent, final Map consentToShareData) {
        if (cachedDemographicConsent.pcStateManager == null) {
            cachedDemographicConsent.consentToShareData = consentToShareData;
            return;
        }
        cachedDemographicConsent.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicConsent, CachedDemographicConsent.pcInheritedFieldCount + 1, (Object)cachedDemographicConsent.consentToShareData, (Object)consentToShareData, 0);
    }
    
    private static final Date pcGetcreatedDate(final CachedDemographicConsent cachedDemographicConsent) {
        if (cachedDemographicConsent.pcStateManager == null) {
            return cachedDemographicConsent.createdDate;
        }
        cachedDemographicConsent.pcStateManager.accessingField(CachedDemographicConsent.pcInheritedFieldCount + 2);
        return cachedDemographicConsent.createdDate;
    }
    
    private static final void pcSetcreatedDate(final CachedDemographicConsent cachedDemographicConsent, final Date createdDate) {
        if (cachedDemographicConsent.pcStateManager == null) {
            cachedDemographicConsent.createdDate = createdDate;
            return;
        }
        cachedDemographicConsent.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicConsent, CachedDemographicConsent.pcInheritedFieldCount + 2, (Object)cachedDemographicConsent.createdDate, (Object)createdDate, 0);
    }
    
    private static final boolean pcGetexcludeMentalHealthData(final CachedDemographicConsent cachedDemographicConsent) {
        if (cachedDemographicConsent.pcStateManager == null) {
            return cachedDemographicConsent.excludeMentalHealthData;
        }
        cachedDemographicConsent.pcStateManager.accessingField(CachedDemographicConsent.pcInheritedFieldCount + 3);
        return cachedDemographicConsent.excludeMentalHealthData;
    }
    
    private static final void pcSetexcludeMentalHealthData(final CachedDemographicConsent cachedDemographicConsent, final boolean excludeMentalHealthData) {
        if (cachedDemographicConsent.pcStateManager == null) {
            cachedDemographicConsent.excludeMentalHealthData = excludeMentalHealthData;
            return;
        }
        cachedDemographicConsent.pcStateManager.settingBooleanField((PersistenceCapable)cachedDemographicConsent, CachedDemographicConsent.pcInheritedFieldCount + 3, cachedDemographicConsent.excludeMentalHealthData, excludeMentalHealthData, 0);
    }
    
    private static final Date pcGetexpiry(final CachedDemographicConsent cachedDemographicConsent) {
        if (cachedDemographicConsent.pcStateManager == null) {
            return cachedDemographicConsent.expiry;
        }
        cachedDemographicConsent.pcStateManager.accessingField(CachedDemographicConsent.pcInheritedFieldCount + 4);
        return cachedDemographicConsent.expiry;
    }
    
    private static final void pcSetexpiry(final CachedDemographicConsent cachedDemographicConsent, final Date expiry) {
        if (cachedDemographicConsent.pcStateManager == null) {
            cachedDemographicConsent.expiry = expiry;
            return;
        }
        cachedDemographicConsent.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicConsent, CachedDemographicConsent.pcInheritedFieldCount + 4, (Object)cachedDemographicConsent.expiry, (Object)expiry, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityDemographicPk(final CachedDemographicConsent cachedDemographicConsent) {
        if (cachedDemographicConsent.pcStateManager == null) {
            return cachedDemographicConsent.facilityDemographicPk;
        }
        cachedDemographicConsent.pcStateManager.accessingField(CachedDemographicConsent.pcInheritedFieldCount + 5);
        return cachedDemographicConsent.facilityDemographicPk;
    }
    
    private static final void pcSetfacilityDemographicPk(final CachedDemographicConsent cachedDemographicConsent, final FacilityIdIntegerCompositePk facilityDemographicPk) {
        if (cachedDemographicConsent.pcStateManager == null) {
            cachedDemographicConsent.facilityDemographicPk = facilityDemographicPk;
            return;
        }
        cachedDemographicConsent.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicConsent, CachedDemographicConsent.pcInheritedFieldCount + 5, (Object)cachedDemographicConsent.facilityDemographicPk, (Object)facilityDemographicPk, 0);
    }

    /**
     * Checks if this instance is detached from the persistence context.
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
    
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Retrieves the detached state object.
     *
     * @return Object the detached state, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object.
     *
     * @param pcDetachedState Object the detached state to set
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

    /**
     * Represents the consent status for patient data sharing.
     *
     * <p>This enum defines the possible states of patient consent for sharing
     * demographic and clinical data across integrated care facilities.</p>
     */
    public enum ConsentStatus
    {
        /**
         * Patient has given consent to share data.
         */
        GIVEN,

        /**
         * Patient has revoked previously given consent.
         */
        REVOKED;
    }
}
