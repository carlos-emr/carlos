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
import javax.xml.bind.DatatypeConverter;
import java.util.Arrays;
import ca.openosp.openo.caisi_integrator.util.EncryptionUtils;
import ca.openosp.openo.caisi_integrator.util.MiscUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Calendar;
import java.util.GregorianCalendar;
import javax.persistence.Column;
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import org.apache.log4j.Logger;
import javax.persistence.Entity;
import ca.openosp.openo.caisi_integrator.util.Named;

/**
 * Represents a healthcare facility entity in the CAISI Integrator system.
 *
 * <p>This entity manages facility authentication and tracking for the OpenO EMR CAISI integrator module,
 * which enables inter-facility data sharing and communication across multiple healthcare installations.
 * Each facility maintains its own credentials, login tracking, and enabled/disabled status for secure
 * system integration.</p>
 *
 * <p>The class is JPA-enhanced using OpenJPA for persistent field management and implements the
 * PersistenceCapable interface to support advanced ORM features including field-level state tracking,
 * detachment, and serialization.</p>
 *
 * <p><strong>Security Note:</strong> Passwords are stored as SHA-1 hashed byte arrays and never
 * returned in plain text. All password operations use secure hashing via {@link EncryptionUtils}.</p>
 *
 * @see AbstractModel
 * @see Named
 * @see PersistenceCapable
 * @see EncryptionUtils
 * @since 2026-01-24
 */
@Entity(name = "IntegratorFacility")
public class Facility extends AbstractModel<Integer> implements Named, PersistenceCapable
{
    private static final long serialVersionUID = 1L;
    private static Logger logger;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, length = 32, unique = true)
    private String name;
    @Column(nullable = false, columnDefinition = "tinyblob")
    private byte[] password;
    @Temporal(TemporalType.TIMESTAMP)
    private Calendar lastLogin;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean disabled;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$util$GregorianCalendar;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$L$B;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$Facility;
    private transient Object pcDetachedState;

    /**
     * Constructs a new Facility instance with default values.
     *
     * <p>Initializes all fields to their default values: id, name, password, and lastLogin
     * are set to null, and disabled is set to false. This constructor is used by JPA for
     * entity instantiation.</p>
     */
    public Facility() {
        this.id = null;
        this.name = null;
        this.password = null;
        this.lastLogin = null;
        this.disabled = false;
    }

    /**
     * Retrieves the unique identifier for this facility.
     *
     * <p>This method is persistence-aware and will properly handle field access tracking
     * when the entity is managed by the JPA persistence context.</p>
     *
     * @return Integer the unique facility identifier, or null if not yet persisted
     */
    @Override
    public Integer getId() {
        return pcGetid(this);
    }

    /**
     * Retrieves the unique name of this facility.
     *
     * <p>The facility name is a unique identifier (max 32 characters) used for facility
     * identification and authentication in the CAISI integrator system.</p>
     *
     * @return String the facility name, or null if not set
     */
    public String getName() {
        return pcGetname(this);
    }

    /**
     * Sets the unique name for this facility.
     *
     * <p>The provided name will be validated and normalized using {@link MiscUtils#validateAndNormaliseUserName(String)}
     * to ensure it meets the required format and length constraints (max 32 characters).</p>
     *
     * @param name String the facility name to set (will be validated and normalized)
     * @throws IllegalArgumentException if the name fails validation
     */
    public void setName(final String name) {
        pcSetname(this, MiscUtils.validateAndNormaliseUserName(name));
    }

    /**
     * Sets the password for this facility using secure SHA-1 hashing.
     *
     * <p>The provided plain-text password is immediately hashed using SHA-1 via {@link EncryptionUtils#getSha1(String)}
     * and stored as a byte array. The plain-text password is never persisted. Debug logging is enabled
     * to track password operations when the logger is in DEBUG mode.</p>
     *
     * <p><strong>Security Note:</strong> This method stores the password as a SHA-1 hash. While SHA-1
     * is considered cryptographically weak for some applications, it is used here for facility authentication
     * in the integrator system.</p>
     *
     * @param password String the plain-text password to hash and store
     * @throws IllegalArgumentException if password is null
     */
    public void setPassword(final String password) {
        if (password == null) {
            throw new IllegalArgumentException("password can't be null");
        }
        pcSetpassword(this, EncryptionUtils.getSha1(password));
        if (Facility.logger.isDebugEnabled()) {
            Facility.logger.debug((Object)("setPassword provided pw : " + password));
            Facility.logger.debug((Object)("setPassword provided pw enc : " + Arrays.toString(EncryptionUtils.getSha1(password))));
        }
    }

    /**
     * Retrieves the facility password.
     *
     * <p><strong>Security Note:</strong> This method always returns null to prevent password exposure.
     * The actual hashed password is stored internally but cannot be retrieved. Use {@link #checkPassword(String)}
     * to verify passwords or {@link #getPasswordAsBase64()} to get the Base64-encoded hash for system integration.</p>
     *
     * @return String always returns null for security reasons
     */
    public String getPassword() {
        return null;
    }

    /**
     * Retrieves the hashed password as a Base64-encoded string.
     *
     * <p>This method is used for secure password transmission in inter-facility communication
     * within the CAISI integrator system. The returned value is the Base64 encoding of the
     * SHA-1 hashed password bytes.</p>
     *
     * @return String the Base64-encoded password hash
     * @throws NullPointerException if password has not been set
     */
    public String getPasswordAsBase64() {
        return DatatypeConverter.printBase64Binary(pcGetpassword(this));
    }

    /**
     * Verifies if the provided password matches the stored hashed password.
     *
     * <p>This method hashes the provided plain-text password using SHA-1 and compares it
     * byte-for-byte with the stored password hash. Debug logging is enabled to track
     * password verification attempts when the logger is in DEBUG mode.</p>
     *
     * @param password String the plain-text password to verify
     * @return boolean true if the provided password matches the stored hash, false otherwise
     */
    public boolean checkPassword(final String password) {
        if (Facility.logger.isDebugEnabled()) {
            Facility.logger.debug((Object)("provided pw : " + password));
            Facility.logger.debug((Object)("db pw : " + Arrays.toString(pcGetpassword(this))));
            Facility.logger.debug((Object)("provided pw enc : " + Arrays.toString(EncryptionUtils.getSha1(password))));
        }
        return password != null && Arrays.equals(pcGetpassword(this), EncryptionUtils.getSha1(password));
    }

    /**
     * Retrieves the timestamp of the last successful login for this facility.
     *
     * <p>This field tracks facility authentication activity in the CAISI integrator system
     * and can be used for security auditing and session management.</p>
     *
     * @return Calendar the last login timestamp, or null if the facility has never logged in
     */
    public Calendar getLastLogin() {
        return pcGetlastLogin(this);
    }

    /**
     * Sets the timestamp of the last successful login for this facility.
     *
     * <p>This method should be called after successful facility authentication to update
     * the login tracking timestamp.</p>
     *
     * @param lastLogin Calendar the timestamp of the last login, or null to clear
     */
    public void setLastLogin(final Calendar lastLogin) {
        pcSetlastLogin(this, lastLogin);
    }

    /**
     * Checks if this facility is currently disabled.
     *
     * <p>Disabled facilities are not allowed to authenticate or participate in the CAISI
     * integrator system. This flag provides an administrative control to temporarily or
     * permanently revoke facility access without deleting the facility record.</p>
     *
     * @return boolean true if the facility is disabled, false if enabled
     */
    public boolean isDisabled() {
        return pcGetdisabled(this);
    }

    /**
     * Sets the disabled status for this facility.
     *
     * <p>Setting this to true will prevent the facility from authenticating or participating
     * in the CAISI integrator system. Setting to false will re-enable the facility.</p>
     *
     * @param disabled boolean true to disable the facility, false to enable it
     */
    public void setDisabled(final boolean disabled) {
        pcSetdisabled(this, disabled);
    }
    
    static {
        Facility.logger = MiscUtils.getLogger();
        Facility.pcFieldNames = new String[] { "disabled", "id", "lastLogin", "name", "password" };
        Facility.pcFieldTypes = new Class[] { Boolean.TYPE, (Facility.class$Ljava$lang$Integer != null) ? Facility.class$Ljava$lang$Integer : (Facility.class$Ljava$lang$Integer = class$("java.lang.Integer")), (Facility.class$Ljava$util$GregorianCalendar != null) ? Facility.class$Ljava$util$GregorianCalendar : (Facility.class$Ljava$util$GregorianCalendar = class$("java.util.GregorianCalendar")), (Facility.class$Ljava$lang$String != null) ? Facility.class$Ljava$lang$String : (Facility.class$Ljava$lang$String = class$("java.lang.String")), (Facility.class$L$B != null) ? Facility.class$L$B : (Facility.class$L$B = class$("[B")) };
        Facility.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26 };
        PCRegistry.register((Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility != null) ? Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility : (Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility = class$("ca.openosp.openo.caisi_integrator.dao.Facility")), Facility.pcFieldNames, Facility.pcFieldTypes, Facility.pcFieldFlags, Facility.pcPCSuperclass, "Facility", (PersistenceCapable)new Facility());
    }

    /**
     * Returns the OpenJPA enhancement contract version for this entity.
     *
     * <p>This method is part of the PersistenceCapable interface and is used by OpenJPA
     * to verify that the entity bytecode enhancement is compatible with the current
     * OpenJPA version.</p>
     *
     * @return int the enhancement contract version (currently 2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
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
        this.disabled = false;
        this.id = null;
        this.lastLogin = null;
        this.name = null;
        this.password = null;
    }

    /**
     * Creates a new Facility instance with the specified state manager and object ID.
     *
     * <p>This method is part of the PersistenceCapable interface and is used by OpenJPA
     * to create new managed instances during entity loading and object graph navigation.
     * The instance is initialized with the provided StateManager and object ID.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with this instance
     * @param o Object the object ID containing the primary key value
     * @param b boolean true to clear all fields to default values, false to leave uninitialized
     * @return PersistenceCapable a new Facility instance with the specified configuration
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final Facility facility = new Facility();
        if (b) {
            facility.pcClearFields();
        }
        facility.pcStateManager = pcStateManager;
        facility.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)facility;
    }

    /**
     * Creates a new Facility instance with the specified state manager.
     *
     * <p>This method is part of the PersistenceCapable interface and is used by OpenJPA
     * to create new managed instances. Unlike the three-parameter version, this does not
     * copy key fields from an object ID.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with this instance
     * @param b boolean true to clear all fields to default values, false to leave uninitialized
     * @return PersistenceCapable a new Facility instance with the specified configuration
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final Facility facility = new Facility();
        if (b) {
            facility.pcClearFields();
        }
        facility.pcStateManager = pcStateManager;
        return (PersistenceCapable)facility;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 5;
    }

    /**
     * Replaces a single persistent field value from the state manager.
     *
     * <p>This method is part of the PersistenceCapable interface and is used by OpenJPA
     * to restore field values during entity loading, rollback, and refresh operations.
     * The field index is adjusted for inheritance and mapped to the appropriate field.</p>
     *
     * @param n int the absolute field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - Facility.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.disabled = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.id = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.lastLogin = (Calendar)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.name = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.password = (byte[])this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple persistent field values from the state manager.
     *
     * <p>This method is part of the PersistenceCapable interface and provides a batch
     * operation for replacing multiple fields at once. It delegates to {@link #pcReplaceField(int)}
     * for each field index in the array.</p>
     *
     * @param array int[] array of absolute field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single persistent field value to the state manager.
     *
     * <p>This method is part of the PersistenceCapable interface and is used by OpenJPA
     * to capture field values during operations like flush, serialization, and detachment.
     * The field index is adjusted for inheritance and the appropriate field value is
     * provided to the state manager.</p>
     *
     * @param n int the absolute field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - Facility.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.disabled);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.id);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.lastLogin);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.name);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.password);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Provides multiple persistent field values to the state manager.
     *
     * <p>This method is part of the PersistenceCapable interface and provides a batch
     * operation for providing multiple fields at once. It delegates to {@link #pcProvideField(int)}
     * for each field index in the array.</p>
     *
     * @param array int[] array of absolute field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    protected void pcCopyField(final Facility facility, final int n) {
        final int n2 = n - Facility.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.disabled = facility.disabled;
                return;
            }
            case 1: {
                this.id = facility.id;
                return;
            }
            case 2: {
                this.lastLogin = facility.lastLogin;
                return;
            }
            case 3: {
                this.name = facility.name;
                return;
            }
            case 4: {
                this.password = facility.password;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies multiple persistent field values from another Facility instance.
     *
     * <p>This method is part of the PersistenceCapable interface and is used by OpenJPA
     * for operations like merge and refresh. It copies the specified fields from the
     * source object to this instance. Both objects must be managed by the same state manager.</p>
     *
     * @param o Object the source Facility instance to copy fields from
     * @param array int[] array of absolute field indices to copy
     * @throws IllegalArgumentException if the objects have different state managers
     * @throws IllegalStateException if this instance has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final Facility facility = (Facility)o;
        if (facility.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(facility, array[i]);
        }
    }

    /**
     * Retrieves the generic context from the state manager.
     *
     * <p>This method is part of the PersistenceCapable interface and provides access to
     * OpenJPA's generic context object, which can be used for custom state management.</p>
     *
     * @return Object the generic context from the state manager, or null if not managed
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
     * <p>This method is part of the PersistenceCapable interface and returns the JPA
     * object ID that uniquely identifies this entity instance within the persistence context.</p>
     *
     * @return Object the object ID, or null if not managed
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
     * <p>This method is part of the PersistenceCapable interface and indicates whether
     * this entity has been deleted within the current transaction.</p>
     *
     * @return boolean true if deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has unsaved changes.
     *
     * <p>This method is part of the PersistenceCapable interface and indicates whether
     * any fields have been modified since the last flush or load operation.</p>
     *
     * @return boolean true if any fields have been modified, false otherwise
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
     * Checks if this is a new entity that has not yet been persisted to the database.
     *
     * <p>This method is part of the PersistenceCapable interface and indicates whether
     * this entity was created in the current transaction and has not yet been flushed.</p>
     *
     * @return boolean true if this is a new unpersisted entity, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is persistent (managed by a persistence context).
     *
     * <p>This method is part of the PersistenceCapable interface and indicates whether
     * this entity is currently managed by OpenJPA and will be persisted to the database.</p>
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity is transactional.
     *
     * <p>This method is part of the PersistenceCapable interface and indicates whether
     * this entity is participating in the current transaction and will be tracked for changes.</p>
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity is currently being serialized.
     *
     * <p>This method is part of the PersistenceCapable interface and is used during
     * serialization to determine whether to include detached state information.</p>
     *
     * @return boolean true if the entity is currently being serialized, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a field as dirty (modified) by name.
     *
     * <p>This method is part of the PersistenceCapable interface and notifies the state
     * manager that the specified field has been modified and should be tracked for updates.</p>
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
     * Retrieves the state manager associated with this entity.
     *
     * <p>This method is part of the PersistenceCapable interface and provides access to
     * the OpenJPA StateManager that tracks this entity's persistence state.</p>
     *
     * @return StateManager the state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version information for this entity.
     *
     * <p>This method is part of the PersistenceCapable interface and returns the version
     * object used for optimistic locking, if configured.</p>
     *
     * @return Object the version object, or null if not managed or versioning not enabled
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
     * <p>This method is part of the PersistenceCapable interface and is used by OpenJPA
     * to transfer entity management between persistence contexts or to detach entities.</p>
     *
     * @param pcStateManager StateManager the new state manager to associate with this entity
     * @throws SecurityException if state manager replacement is not allowed
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
     *
     * <p>This method is part of the PersistenceCapable interface. This implementation
     * throws InternalException as it is not supported for entities with single-field
     * primary keys (ID is auto-generated).</p>
     *
     * @param fieldSupplier FieldSupplier the field supplier to receive key field values
     * @param o Object the object ID to populate
     * @throws InternalException always, as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields to an object ID.
     *
     * <p>This method is part of the PersistenceCapable interface. This implementation
     * throws InternalException as it is not supported for entities with single-field
     * primary keys (ID is auto-generated).</p>
     *
     * @param o Object the object ID to populate
     * @throws InternalException always, as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields from an object ID using a field consumer.
     *
     * <p>This method is part of the PersistenceCapable interface and is used by OpenJPA
     * to extract the primary key value from an IntId object and provide it to the field
     * consumer for entity initialization.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer to store key field values
     * @param o Object the IntId object containing the primary key value
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(1 + Facility.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }

    /**
     * Copies key fields from an object ID directly into this entity.
     *
     * <p>This method is part of the PersistenceCapable interface and is used by OpenJPA
     * to extract the primary key value from an IntId object and set it directly on this entity.</p>
     *
     * @param o Object the IntId object containing the primary key value
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Integer.valueOf(((IntId)o).getId());
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * <p>This method is part of the PersistenceCapable interface and is used by OpenJPA
     * to create IntId instances from string representations of primary keys.</p>
     *
     * @param o Object the string representation of the primary key
     * @return Object a new IntId instance for this entity type
     */
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility != null) ? Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility : (Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility = class$("ca.openosp.openo.caisi_integrator.dao.Facility")), (String)o);
    }

    /**
     * Creates a new object ID instance using this entity's current primary key.
     *
     * <p>This method is part of the PersistenceCapable interface and is used by OpenJPA
     * to create IntId instances containing this entity's current primary key value.</p>
     *
     * @return Object a new IntId instance containing this entity's ID
     */
    public Object pcNewObjectIdInstance() {
        return new IntId((Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility != null) ? Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility : (Facility.class$Lca$openosp$openo$caisi_integrator$dao$Facility = class$("ca.openosp.openo.caisi_integrator.dao.Facility")), this.id);
    }
    
    private static final boolean pcGetdisabled(final Facility facility) {
        if (facility.pcStateManager == null) {
            return facility.disabled;
        }
        facility.pcStateManager.accessingField(Facility.pcInheritedFieldCount + 0);
        return facility.disabled;
    }
    
    private static final void pcSetdisabled(final Facility facility, final boolean disabled) {
        if (facility.pcStateManager == null) {
            facility.disabled = disabled;
            return;
        }
        facility.pcStateManager.settingBooleanField((PersistenceCapable)facility, Facility.pcInheritedFieldCount + 0, facility.disabled, disabled, 0);
    }
    
    private static final Integer pcGetid(final Facility facility) {
        if (facility.pcStateManager == null) {
            return facility.id;
        }
        facility.pcStateManager.accessingField(Facility.pcInheritedFieldCount + 1);
        return facility.id;
    }
    
    private static final void pcSetid(final Facility facility, final Integer id) {
        if (facility.pcStateManager == null) {
            facility.id = id;
            return;
        }
        facility.pcStateManager.settingObjectField((PersistenceCapable)facility, Facility.pcInheritedFieldCount + 1, (Object)facility.id, (Object)id, 0);
    }
    
    private static final Calendar pcGetlastLogin(final Facility facility) {
        if (facility.pcStateManager == null) {
            return facility.lastLogin;
        }
        facility.pcStateManager.accessingField(Facility.pcInheritedFieldCount + 2);
        return facility.lastLogin;
    }
    
    private static final void pcSetlastLogin(final Facility facility, final Calendar lastLogin) {
        if (facility.pcStateManager == null) {
            facility.lastLogin = lastLogin;
            return;
        }
        facility.pcStateManager.settingObjectField((PersistenceCapable)facility, Facility.pcInheritedFieldCount + 2, (Object)facility.lastLogin, (Object)lastLogin, 0);
    }
    
    private static final String pcGetname(final Facility facility) {
        if (facility.pcStateManager == null) {
            return facility.name;
        }
        facility.pcStateManager.accessingField(Facility.pcInheritedFieldCount + 3);
        return facility.name;
    }
    
    private static final void pcSetname(final Facility facility, final String name) {
        if (facility.pcStateManager == null) {
            facility.name = name;
            return;
        }
        facility.pcStateManager.settingStringField((PersistenceCapable)facility, Facility.pcInheritedFieldCount + 3, facility.name, name, 0);
    }
    
    private static final byte[] pcGetpassword(final Facility facility) {
        if (facility.pcStateManager == null) {
            return facility.password;
        }
        facility.pcStateManager.accessingField(Facility.pcInheritedFieldCount + 4);
        return facility.password;
    }
    
    private static final void pcSetpassword(final Facility facility, final byte[] password) {
        if (facility.pcStateManager == null) {
            facility.password = password;
            return;
        }
        facility.pcStateManager.settingObjectField((PersistenceCapable)facility, Facility.pcInheritedFieldCount + 4, (Object)facility.password, (Object)password, 0);
    }

    /**
     * Determines whether this entity is in a detached state.
     *
     * <p>This method is part of the PersistenceCapable interface and checks if the entity
     * has been detached from its persistence context. A detached entity has been loaded
     * but is no longer managed, often used for transferring entities between layers or
     * for lazy loading outside a transaction.</p>
     *
     * @return Boolean true if detached, false if attached, or null if state cannot be determined
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
     * <p>This method is part of the PersistenceCapable interface and returns the state
     * information stored when the entity was detached. This is used by OpenJPA to track
     * which fields have been loaded and modified in detached entities.</p>
     *
     * @return Object the detached state, or null if not detached or PersistenceCapable.DESERIALIZED if deserialized
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state object for this entity.
     *
     * <p>This method is part of the PersistenceCapable interface and is used by OpenJPA
     * to store state information when detaching entities or to mark deserialized entities.</p>
     *
     * @param pcDetachedState Object the detached state to store, or PersistenceCapable.DESERIALIZED for deserialized entities
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
