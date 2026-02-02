package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.util.IntId;
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
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Entity;

/**
 * Represents a communication message between healthcare providers across integrator facilities.
 *
 * This entity tracks inter-provider communications within the CAISI (Client Access to Integrated Services and Information)
 * integrator system, enabling secure message exchange between providers at different healthcare facilities. Each communication
 * record maintains the source and destination provider/facility identifiers, transmission timestamp, message type, and
 * the actual message data as a serialized byte array.
 *
 * <p>The entity is OpenJPA-enhanced for persistence and includes database indexes on destination facility and provider
 * identifiers to optimize query performance for inbox/outbox operations. Messages can be marked as active or inactive
 * to support soft deletion and archival workflows.</p>
 *
 * <p>This class is part of the CAISI integrator DAO layer which facilitates cross-facility healthcare data exchange
 * while maintaining appropriate security boundaries and audit trails.</p>
 *
 * @since 2026-01-24
 * @see AbstractModel
 * @see ca.openosp.openo.caisi_integrator.dao
 */
@Entity
public class ProviderCommunication extends AbstractModel<Integer> implements PersistenceCapable
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false)
    private Integer sourceIntegratorFacilityId;
    @Column(nullable = false)
    private String sourceProviderId;
    @Column(nullable = false)
    @Index
    private Integer destinationIntegratorFacilityId;
    @Column(nullable = false)
    @Index
    private String destinationProviderId;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date sentDate;
    @Column(nullable = false, columnDefinition = "tinyint")
    private boolean active;
    @Index
    private String type;
    @Column(columnDefinition = "mediumblob")
    private byte[] data;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$L$B;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor for ProviderCommunication entity.
     *
     * <p>Initializes a new provider communication message with default values:
     * <ul>
     *   <li>sentDate set to current timestamp</li>
     *   <li>active flag set to true</li>
     *   <li>all other fields set to null</li>
     * </ul>
     *
     * <p>This constructor is used by JPA/OpenJPA framework for entity instantiation
     * and by application code when creating new communication messages.</p>
     */
    public ProviderCommunication() {
        this.id = null;
        this.sourceIntegratorFacilityId = null;
        this.sourceProviderId = null;
        this.destinationIntegratorFacilityId = null;
        this.destinationProviderId = null;
        this.sentDate = new Date();
        this.active = true;
        this.type = null;
        this.data = null;
    }

    /**
     * Retrieves the unique identifier for this provider communication record.
     *
     * @return Integer the primary key identifier, or null if the entity has not been persisted yet
     */
    @Override
    public Integer getId() {
        return pcGetid(this);
    }

    /**
     * Retrieves the integrator facility identifier of the message sender.
     *
     * <p>This identifier corresponds to the healthcare facility in the integrator system
     * from which the communication message originated.</p>
     *
     * @return Integer the source facility's integrator identifier
     */
    public Integer getSourceIntegratorFacilityId() {
        return pcGetsourceIntegratorFacilityId(this);
    }

    /**
     * Sets the integrator facility identifier of the message sender.
     *
     * @param sourceIntegratorFacilityId Integer the source facility's integrator identifier (required, must not be null)
     */
    public void setSourceIntegratorFacilityId(final Integer sourceIntegratorFacilityId) {
        pcSetsourceIntegratorFacilityId(this, sourceIntegratorFacilityId);
    }

    /**
     * Retrieves the provider identifier of the message sender.
     *
     * <p>This identifier uniquely identifies the healthcare provider who sent the communication
     * within the context of their facility.</p>
     *
     * @return String the source provider's identifier
     */
    public String getSourceProviderId() {
        return pcGetsourceProviderId(this);
    }

    /**
     * Sets the provider identifier of the message sender.
     *
     * @param sourceProviderId String the source provider's identifier (required, must not be null)
     */
    public void setSourceProviderId(final String sourceProviderId) {
        pcSetsourceProviderId(this, sourceProviderId);
    }

    /**
     * Retrieves the integrator facility identifier of the message recipient.
     *
     * <p>This identifier corresponds to the healthcare facility in the integrator system
     * to which the communication message is being sent. This field is indexed in the database
     * to optimize queries for retrieving messages by destination facility.</p>
     *
     * @return Integer the destination facility's integrator identifier
     */
    public Integer getDestinationIntegratorFacilityId() {
        return pcGetdestinationIntegratorFacilityId(this);
    }

    /**
     * Sets the integrator facility identifier of the message recipient.
     *
     * @param destinationIntegratorFacilityId Integer the destination facility's integrator identifier (required, must not be null)
     */
    public void setDestinationIntegratorFacilityId(final Integer destinationIntegratorFacilityId) {
        pcSetdestinationIntegratorFacilityId(this, destinationIntegratorFacilityId);
    }

    /**
     * Retrieves the provider identifier of the message recipient.
     *
     * <p>This identifier uniquely identifies the healthcare provider who is receiving the communication
     * within the context of their facility. This field is indexed in the database to optimize queries
     * for retrieving a provider's inbox messages.</p>
     *
     * @return String the destination provider's identifier
     */
    public String getDestinationProviderId() {
        return pcGetdestinationProviderId(this);
    }

    /**
     * Sets the provider identifier of the message recipient.
     *
     * @param destinationProviderId String the destination provider's identifier (required, must not be null)
     */
    public void setDestinationProviderId(final String destinationProviderId) {
        pcSetdestinationProviderId(this, destinationProviderId);
    }

    /**
     * Retrieves the timestamp when the communication message was sent.
     *
     * <p>This timestamp is automatically set to the current date/time when a new ProviderCommunication
     * instance is created. It provides an audit trail and chronological ordering of messages.</p>
     *
     * @return Date the message transmission timestamp
     */
    public Date getSentDate() {
        return pcGetsentDate(this);
    }

    /**
     * Sets the timestamp when the communication message was sent.
     *
     * @param sentDate Date the message transmission timestamp (required, must not be null)
     */
    public void setSentDate(final Date sentDate) {
        pcSetsentDate(this, sentDate);
    }

    /**
     * Checks whether this communication message is active.
     *
     * <p>The active flag supports soft deletion and archival workflows. Active messages (true) are
     * typically displayed in provider inboxes and outboxes, while inactive messages (false) may be
     * archived or logically deleted without being physically removed from the database.</p>
     *
     * @return boolean true if the message is active, false if inactive/archived
     */
    public boolean isActive() {
        return pcGetactive(this);
    }

    /**
     * Sets the active status of this communication message.
     *
     * @param active boolean true to mark the message as active, false to mark as inactive/archived
     */
    public void setActive(final boolean active) {
        pcSetactive(this, active);
    }

    /**
     * Retrieves the serialized message data.
     *
     * <p>The message content is stored as a byte array (MEDIUMBLOB in database) to support
     * flexible message formats. The data typically contains serialized objects representing
     * the actual communication content, which may include clinical notes, alerts, or other
     * healthcare-related information.</p>
     *
     * @return byte[] the serialized message content, or null if no data has been set
     */
    public byte[] getData() {
        return pcGetdata(this);
    }

    /**
     * Sets the serialized message data.
     *
     * @param data byte[] the serialized message content to store (may be null)
     */
    public void setData(final byte[] data) {
        pcSetdata(this, data);
    }

    /**
     * Retrieves the type classification of this communication message.
     *
     * <p>The type field allows categorization of different kinds of provider communications
     * (e.g., referrals, consultations, alerts, notifications). This field is indexed in the
     * database to support efficient filtering and retrieval by message type.</p>
     *
     * @return String the message type classification, or null if not set
     */
    public String getType() {
        return pcGettype(this);
    }

    /**
     * Sets the type classification of this communication message.
     *
     * <p>The provided type value is automatically trimmed and converted to null if it contains
     * only whitespace, ensuring data consistency.</p>
     *
     * @param type String the message type classification (will be trimmed to null if blank)
     */
    public void setType(final String type) {
        pcSettype(this, StringUtils.trimToNull(type));
    }

    /**
     * Returns the OpenJPA enhancement contract version for this entity.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and is used internally
     * by the framework to verify bytecode enhancement compatibility.</p>
     *
     * @return int the enhancement contract version (always 2 for this entity)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -8060429262360155339L;
        ProviderCommunication.pcFieldNames = new String[] { "active", "data", "destinationIntegratorFacilityId", "destinationProviderId", "id", "sentDate", "sourceIntegratorFacilityId", "sourceProviderId", "type" };
        ProviderCommunication.pcFieldTypes = new Class[] { Boolean.TYPE, (ProviderCommunication.class$L$B != null) ? ProviderCommunication.class$L$B : (ProviderCommunication.class$L$B = class$("[B")), (ProviderCommunication.class$Ljava$lang$Integer != null) ? ProviderCommunication.class$Ljava$lang$Integer : (ProviderCommunication.class$Ljava$lang$Integer = class$("java.lang.Integer")), (ProviderCommunication.class$Ljava$lang$String != null) ? ProviderCommunication.class$Ljava$lang$String : (ProviderCommunication.class$Ljava$lang$String = class$("java.lang.String")), (ProviderCommunication.class$Ljava$lang$Integer != null) ? ProviderCommunication.class$Ljava$lang$Integer : (ProviderCommunication.class$Ljava$lang$Integer = class$("java.lang.Integer")), (ProviderCommunication.class$Ljava$util$Date != null) ? ProviderCommunication.class$Ljava$util$Date : (ProviderCommunication.class$Ljava$util$Date = class$("java.util.Date")), (ProviderCommunication.class$Ljava$lang$Integer != null) ? ProviderCommunication.class$Ljava$lang$Integer : (ProviderCommunication.class$Ljava$lang$Integer = class$("java.lang.Integer")), (ProviderCommunication.class$Ljava$lang$String != null) ? ProviderCommunication.class$Ljava$lang$String : (ProviderCommunication.class$Ljava$lang$String = class$("java.lang.String")), (ProviderCommunication.class$Ljava$lang$String != null) ? ProviderCommunication.class$Ljava$lang$String : (ProviderCommunication.class$Ljava$lang$String = class$("java.lang.String")) };
        ProviderCommunication.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication != null) ? ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication : (ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication = class$("ca.openosp.openo.caisi_integrator.dao.ProviderCommunication")), ProviderCommunication.pcFieldNames, ProviderCommunication.pcFieldTypes, ProviderCommunication.pcFieldFlags, ProviderCommunication.pcPCSuperclass, "ProviderCommunication", (PersistenceCapable)new ProviderCommunication());
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
        this.active = false;
        this.data = null;
        this.destinationIntegratorFacilityId = null;
        this.destinationProviderId = null;
        this.id = null;
        this.sentDate = null;
        this.sourceIntegratorFacilityId = null;
        this.sourceProviderId = null;
        this.type = null;
    }

    /**
     * Creates a new instance of this entity with the specified state manager and object ID.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and is used internally
     * by the framework to instantiate entity objects during data retrieval operations.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object ID to copy key fields from
     * @param b boolean whether to clear all fields after instantiation
     * @return PersistenceCapable a new instance with the specified state manager and key fields
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final ProviderCommunication providerCommunication = new ProviderCommunication();
        if (b) {
            providerCommunication.pcClearFields();
        }
        providerCommunication.pcStateManager = pcStateManager;
        providerCommunication.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)providerCommunication;
    }

    /**
     * Creates a new instance of this entity with the specified state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and is used internally
     * by the framework to instantiate entity objects during persistence operations.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean whether to clear all fields after instantiation
     * @return PersistenceCapable a new instance with the specified state manager
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final ProviderCommunication providerCommunication = new ProviderCommunication();
        if (b) {
            providerCommunication.pcClearFields();
        }
        providerCommunication.pcStateManager = pcStateManager;
        return (PersistenceCapable)providerCommunication;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 9;
    }

    /**
     * Replaces the value of a single field in this entity during OpenJPA state management.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and is used internally
     * by the framework to update individual field values from the state manager during operations
     * like refresh or merge.</p>
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - ProviderCommunication.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.active = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.data = (byte[])this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.destinationIntegratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.destinationProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.id = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.sentDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.sourceIntegratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.sourceProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.type = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces the values of multiple fields in this entity during OpenJPA state management.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and delegates to
     * {@link #pcReplaceField(int)} for each field index in the provided array.</p>
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides the value of a single field to the OpenJPA state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and is used internally
     * by the framework to read field values from the entity into the state manager during operations
     * like flush or detachment.</p>
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - ProviderCommunication.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.active);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.data);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.destinationIntegratorFacilityId);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.destinationProviderId);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.id);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.sentDate);
                return;
            }
            case 6: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.sourceIntegratorFacilityId);
                return;
            }
            case 7: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.sourceProviderId);
                return;
            }
            case 8: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.type);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Provides the values of multiple fields to the OpenJPA state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and delegates to
     * {@link #pcProvideField(int)} for each field index in the provided array.</p>
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    protected void pcCopyField(final ProviderCommunication providerCommunication, final int n) {
        final int n2 = n - ProviderCommunication.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.active = providerCommunication.active;
                return;
            }
            case 1: {
                this.data = providerCommunication.data;
                return;
            }
            case 2: {
                this.destinationIntegratorFacilityId = providerCommunication.destinationIntegratorFacilityId;
                return;
            }
            case 3: {
                this.destinationProviderId = providerCommunication.destinationProviderId;
                return;
            }
            case 4: {
                this.id = providerCommunication.id;
                return;
            }
            case 5: {
                this.sentDate = providerCommunication.sentDate;
                return;
            }
            case 6: {
                this.sourceIntegratorFacilityId = providerCommunication.sourceIntegratorFacilityId;
                return;
            }
            case 7: {
                this.sourceProviderId = providerCommunication.sourceProviderId;
                return;
            }
            case 8: {
                this.type = providerCommunication.type;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies field values from another entity instance to this instance.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and is used internally
     * by the framework to copy field values between managed entity instances during merge operations.</p>
     *
     * @param o Object the source ProviderCommunication instance to copy fields from
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source object has a different state manager
     * @throws IllegalStateException if this entity's state manager is null
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final ProviderCommunication providerCommunication = (ProviderCommunication)o;
        if (providerCommunication.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(providerCommunication, array[i]);
        }
    }

    /**
     * Retrieves the generic context object from the OpenJPA state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and provides access
     * to framework-specific context information associated with this entity.</p>
     *
     * @return Object the generic context, or null if no state manager is associated
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the object ID for this entity from the OpenJPA state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and returns the
     * unique identifier object used by the persistence framework to track this entity.</p>
     *
     * @return Object the object ID, or null if no state manager is associated
     */
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }

    /**
     * Checks whether this entity has been deleted in the current persistence context.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and indicates
     * whether the entity has been marked for deletion in the current transaction.</p>
     *
     * @return boolean true if the entity is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks whether this entity has been modified in the current persistence context.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and indicates
     * whether any field values have been changed since the entity was loaded or last flushed.</p>
     *
     * @return boolean true if the entity has pending changes, false otherwise
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
     * Checks whether this entity is newly created and not yet persisted to the database.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and indicates
     * whether the entity has been created in the current transaction but not yet committed.</p>
     *
     * @return boolean true if the entity is new and unsaved, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks whether this entity is managed by the persistence context.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and indicates
     * whether the entity is currently being tracked by the OpenJPA state manager.</p>
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks whether this entity is participating in the current transaction.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and indicates
     * whether the entity is being tracked within an active transaction context.</p>
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks whether this entity is currently being serialized.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and is used during
     * serialization to determine whether special handling is needed for detached state.</p>
     *
     * @return boolean true if the entity is being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a specific field as dirty in the OpenJPA state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and notifies
     * the state manager that a field has been modified and needs to be updated in the database.</p>
     *
     * @param s String the name of the field that has been modified
     */
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }

    /**
     * Retrieves the OpenJPA state manager associated with this entity.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and provides
     * access to the state manager that tracks this entity's persistence state.</p>
     *
     * @return StateManager the associated state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version object for this entity from the OpenJPA state manager.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and returns
     * the version information used for optimistic locking and change tracking.</p>
     *
     * @return Object the version object, or null if no state manager is associated
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces the current state manager with a new one.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and is used internally
     * by the framework when transferring entity management between persistence contexts.</p>
     *
     * @param pcStateManager StateManager the new state manager to associate with this entity
     * @throws SecurityException if the replacement violates security constraints
     */
    public void pcReplaceStateManager(final StateManager pcStateManager) throws SecurityException {
        if (this.pcStateManager != null) {
            this.pcStateManager = this.pcStateManager.replaceStateManager(pcStateManager);
            return;
        }
        this.pcStateManager = pcStateManager;
    }

    /**
     * Copies key field values to an object ID using a field supplier.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface but is not implemented
     * for this entity type as it uses application-identity with a single auto-generated key.</p>
     *
     * @param fieldSupplier FieldSupplier the field supplier to copy values to
     * @param o Object the target object ID
     * @throws InternalException always, as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key field values directly to an object ID.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface but is not implemented
     * for this entity type as it uses application-identity with a single auto-generated key.</p>
     *
     * @param o Object the target object ID
     * @throws InternalException always, as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key field values from an object ID using a field consumer.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and is used internally
     * by the framework to populate the entity's ID field from an IntId object ID.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer to receive the key field value
     * @param o Object the source IntId object containing the ID value
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(4 + ProviderCommunication.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }

    /**
     * Copies key field values directly from an object ID.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and is used internally
     * by the framework to set the entity's ID field from an IntId object ID.</p>
     *
     * @param o Object the source IntId object containing the ID value
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Integer.valueOf(((IntId)o).getId());
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and is used internally
     * by the framework to construct IntId objects from string keys.</p>
     *
     * @param o Object a String containing the ID value
     * @return Object a new IntId instance for this entity class
     */
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication != null) ? ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication : (ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication = class$("ca.openosp.openo.caisi_integrator.dao.ProviderCommunication")), (String)o);
    }

    /**
     * Creates a new object ID instance based on this entity's current ID field.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and is used internally
     * by the framework to create IntId objects representing this entity's identity.</p>
     *
     * @return Object a new IntId instance containing this entity's ID value
     */
    public Object pcNewObjectIdInstance() {
        return new IntId((ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication != null) ? ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication : (ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication = class$("ca.openosp.openo.caisi_integrator.dao.ProviderCommunication")), this.id);
    }
    
    private static final boolean pcGetactive(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.active;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 0);
        return providerCommunication.active;
    }
    
    private static final void pcSetactive(final ProviderCommunication providerCommunication, final boolean active) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.active = active;
            return;
        }
        providerCommunication.pcStateManager.settingBooleanField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 0, providerCommunication.active, active, 0);
    }
    
    private static final byte[] pcGetdata(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.data;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 1);
        return providerCommunication.data;
    }
    
    private static final void pcSetdata(final ProviderCommunication providerCommunication, final byte[] data) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.data = data;
            return;
        }
        providerCommunication.pcStateManager.settingObjectField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 1, (Object)providerCommunication.data, (Object)data, 0);
    }
    
    private static final Integer pcGetdestinationIntegratorFacilityId(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.destinationIntegratorFacilityId;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 2);
        return providerCommunication.destinationIntegratorFacilityId;
    }
    
    private static final void pcSetdestinationIntegratorFacilityId(final ProviderCommunication providerCommunication, final Integer destinationIntegratorFacilityId) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.destinationIntegratorFacilityId = destinationIntegratorFacilityId;
            return;
        }
        providerCommunication.pcStateManager.settingObjectField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 2, (Object)providerCommunication.destinationIntegratorFacilityId, (Object)destinationIntegratorFacilityId, 0);
    }
    
    private static final String pcGetdestinationProviderId(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.destinationProviderId;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 3);
        return providerCommunication.destinationProviderId;
    }
    
    private static final void pcSetdestinationProviderId(final ProviderCommunication providerCommunication, final String destinationProviderId) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.destinationProviderId = destinationProviderId;
            return;
        }
        providerCommunication.pcStateManager.settingStringField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 3, providerCommunication.destinationProviderId, destinationProviderId, 0);
    }
    
    private static final Integer pcGetid(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.id;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 4);
        return providerCommunication.id;
    }
    
    private static final void pcSetid(final ProviderCommunication providerCommunication, final Integer id) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.id = id;
            return;
        }
        providerCommunication.pcStateManager.settingObjectField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 4, (Object)providerCommunication.id, (Object)id, 0);
    }
    
    private static final Date pcGetsentDate(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.sentDate;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 5);
        return providerCommunication.sentDate;
    }
    
    private static final void pcSetsentDate(final ProviderCommunication providerCommunication, final Date sentDate) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.sentDate = sentDate;
            return;
        }
        providerCommunication.pcStateManager.settingObjectField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 5, (Object)providerCommunication.sentDate, (Object)sentDate, 0);
    }
    
    private static final Integer pcGetsourceIntegratorFacilityId(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.sourceIntegratorFacilityId;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 6);
        return providerCommunication.sourceIntegratorFacilityId;
    }
    
    private static final void pcSetsourceIntegratorFacilityId(final ProviderCommunication providerCommunication, final Integer sourceIntegratorFacilityId) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.sourceIntegratorFacilityId = sourceIntegratorFacilityId;
            return;
        }
        providerCommunication.pcStateManager.settingObjectField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 6, (Object)providerCommunication.sourceIntegratorFacilityId, (Object)sourceIntegratorFacilityId, 0);
    }
    
    private static final String pcGetsourceProviderId(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.sourceProviderId;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 7);
        return providerCommunication.sourceProviderId;
    }
    
    private static final void pcSetsourceProviderId(final ProviderCommunication providerCommunication, final String sourceProviderId) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.sourceProviderId = sourceProviderId;
            return;
        }
        providerCommunication.pcStateManager.settingStringField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 7, providerCommunication.sourceProviderId, sourceProviderId, 0);
    }
    
    private static final String pcGettype(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.type;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 8);
        return providerCommunication.type;
    }
    
    private static final void pcSettype(final ProviderCommunication providerCommunication, final String type) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.type = type;
            return;
        }
        providerCommunication.pcStateManager.settingStringField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 8, providerCommunication.type, type, 0);
    }

    /**
     * Determines whether this entity is in a detached state.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and checks various
     * indicators to determine if the entity has been detached from its persistence context.
     * Returns null if the detached state cannot be definitively determined.</p>
     *
     * @return Boolean true if detached, false if attached, null if indeterminate
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
            if (this.id != null) {
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
     * <p>This method is part of the OpenJPA PersistenceCapable interface and returns
     * the state information stored when the entity was detached from its persistence context.</p>
     *
     * @return Object the detached state, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object for this entity.
     *
     * <p>This method is part of the OpenJPA PersistenceCapable interface and is used internally
     * by the framework to store state information when the entity is detached or deserialized.</p>
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
