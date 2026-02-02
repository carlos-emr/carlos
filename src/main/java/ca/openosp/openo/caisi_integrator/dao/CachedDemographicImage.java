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
import ca.openosp.openo.caisi_integrator.util.ImageIoUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.Column;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * JPA entity representing cached demographic patient images in the CAISI Integrator system.
 *
 * <p>This entity stores scaled-down patient photographs for use in the EMR's integrator functionality,
 * which facilitates data sharing across multiple OpenO EMR installations. Images are automatically
 * scaled to 200x200 pixels on upload to optimize storage and network transfer while maintaining
 * adequate visual quality for patient identification.</p>
 *
 * <p>The entity uses a composite primary key combining facility ID and demographic ID, allowing
 * the same patient to have different images across different healthcare facilities. Images are
 * stored as JPEG format in a MEDIUMBLOB column (up to 16MB), though actual storage is typically
 * much smaller due to automatic scaling and compression.</p>
 *
 * <p><strong>Security Note:</strong> Patient photographs are Protected Health Information (PHI)
 * and must be handled according to PIPEDA/HIPAA requirements. Access should be restricted to
 * authorized healthcare providers with appropriate security privileges.</p>
 *
 * <p>This class is enhanced by OpenJPA for persistence management, implementing the
 * {@link PersistenceCapable} interface to support advanced JPA features including detached
 * entity state tracking, field-level dirty checking, and transparent lazy loading.</p>
 *
 * @see FacilityIdIntegerCompositePk
 * @see AbstractModel
 * @see ImageIoUtils
 * @since 2026-01-24
 */
@Entity
public class CachedDemographicImage extends AbstractModel<FacilityIdIntegerCompositePk> implements PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityDemographicPk;
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateDate;
    @Column(columnDefinition = "mediumblob")
    private byte[] image;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$L$B;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicImage;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor initializing a new cached demographic image with null values.
     *
     * <p>This constructor creates an empty image cache entry. The {@code updateDate} and
     * {@code image} fields are explicitly set to null. The composite primary key
     * ({@code facilityDemographicPk}) must be set separately using
     * {@link #setFacilityIdIntegerCompositePk(FacilityIdIntegerCompositePk)} before persisting.</p>
     *
     * <p>OpenJPA also uses this constructor during entity instantiation and enhancement.</p>
     */
    public CachedDemographicImage() {
        this.updateDate = null;
        this.image = null;
    }

    /**
     * Retrieves the composite primary key identifying this cached image.
     *
     * <p>The composite key combines the facility ID (identifying the healthcare facility) and
     * the demographic ID (identifying the patient). This allows the same patient to have
     * different cached images at different facilities.</p>
     *
     * @return FacilityIdIntegerCompositePk the composite primary key, or null if not yet set
     */
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityDemographicPk(this);
    }

    /**
     * Sets the composite primary key for this cached image.
     *
     * <p>This method must be called before persisting a new cached image entity. The composite
     * key uniquely identifies the cached image by combining facility and patient identifiers.</p>
     *
     * @param facilityDemographicPk FacilityIdIntegerCompositePk the composite key containing
     *                              facility ID and demographic ID
     */
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityDemographicPk) {
        pcSetfacilityDemographicPk(this, facilityDemographicPk);
    }

    /**
     * Retrieves the timestamp when this cached image was last updated.
     *
     * <p>This timestamp tracks when the patient's photograph was last modified in the cache,
     * which is useful for cache invalidation and synchronization across multiple facilities
     * in the CAISI Integrator network.</p>
     *
     * @return Date the last update timestamp, or null if never updated
     */
    public Date getUpdateDate() {
        return pcGetupdateDate(this);
    }

    /**
     * Sets the timestamp when this cached image was updated.
     *
     * <p>This should be set whenever the patient's photograph is modified or refreshed in the
     * cache. The timestamp is used for cache management and synchronization logic.</p>
     *
     * @param updateDate Date the timestamp to record for this update
     */
    public void setUpdateDate(final Date updateDate) {
        pcSetupdateDate(this, updateDate);
    }

    /**
     * Retrieves the patient's photograph as a JPEG byte array.
     *
     * <p>The returned image is the scaled version (200x200 pixels maximum) that was stored
     * when {@link #setImage(byte[])} was called. The image data is in JPEG format with
     * 90% quality compression.</p>
     *
     * @return byte[] the JPEG image data, or null if no image has been set
     */
    public byte[] getImage() {
        return pcGetimage(this);
    }

    /**
     * Sets the patient's photograph, automatically scaling it to optimize storage.
     *
     * <p>The original image is automatically scaled to fit within a 200x200 pixel bounding box
     * while maintaining aspect ratio, then compressed as JPEG at 90% quality. This reduces
     * storage requirements and network bandwidth for image synchronization across facilities
     * while maintaining adequate quality for patient identification.</p>
     *
     * <p>The scaling is performed by {@link ImageIoUtils#scaleJpgSmallerProportionally(byte[], int, int, float)}
     * which handles various input image formats and ensures consistent JPEG output.</p>
     *
     * @param original byte[] the original patient photograph in any image format supported by
     *                 Java ImageIO (typically JPEG, PNG, GIF, BMP)
     */
    public void setImage(final byte[] original) {
        pcSetimage(this, ImageIoUtils.scaleJpgSmallerProportionally(original, 200, 200, 0.9f));
    }

    /**
     * Retrieves the primary key identifier for this entity.
     *
     * <p>This method overrides {@link AbstractModel#getId()} to return the composite primary key
     * that uniquely identifies this cached image across facility and demographic dimensions.</p>
     *
     * @return FacilityIdIntegerCompositePk the composite primary key
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityDemographicPk(this);
    }

    /**
     * Returns the OpenJPA enhancement contract version for this persistent class.
     *
     * <p>This method is part of the OpenJPA bytecode enhancement contract and indicates
     * the version of enhancement applied to this class. Version 2 is the current contract
     * version for OpenJPA's persistence capabilities.</p>
     *
     * @return int the enhancement contract version (always 2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -9132848329454932558L;
        CachedDemographicImage.pcFieldNames = new String[] { "facilityDemographicPk", "image", "updateDate" };
        CachedDemographicImage.pcFieldTypes = new Class[] { (CachedDemographicImage.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedDemographicImage.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedDemographicImage.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedDemographicImage.class$L$B != null) ? CachedDemographicImage.class$L$B : (CachedDemographicImage.class$L$B = class$("[B")), (CachedDemographicImage.class$Ljava$util$Date != null) ? CachedDemographicImage.class$Ljava$util$Date : (CachedDemographicImage.class$Ljava$util$Date = class$("java.util.Date")) };
        CachedDemographicImage.pcFieldFlags = new byte[] { 26, 26, 26 };
        PCRegistry.register((CachedDemographicImage.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicImage != null) ? CachedDemographicImage.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicImage : (CachedDemographicImage.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicImage = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicImage")), CachedDemographicImage.pcFieldNames, CachedDemographicImage.pcFieldTypes, CachedDemographicImage.pcFieldFlags, CachedDemographicImage.pcPCSuperclass, "CachedDemographicImage", (PersistenceCapable)new CachedDemographicImage());
    }
    
    /**
     * Internal helper method to load a class by name during bytecode enhancement.
     *
     * <p>This synthetic method is generated by the Java compiler to support class literal
     * references in the static initializer block. It wraps {@link Class#forName(String)}
     * and converts checked {@link ClassNotFoundException} to unchecked {@link NoClassDefFoundError}.</p>
     *
     * @param className String the fully-qualified class name to load
     * @return Class the loaded class object
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
     * Clears all persistent fields to their default null values.
     *
     * <p>This method is used by OpenJPA during entity lifecycle management, particularly
     * when creating new instances or detaching entities. It resets the composite primary key,
     * image data, and update timestamp.</p>
     */
    protected void pcClearFields() {
        this.facilityDemographicPk = null;
        this.image = null;
        this.updateDate = null;
    }

    /**
     * Creates a new instance of this entity with the specified state manager and object ID.
     *
     * <p>This method is part of the OpenJPA persistence capability contract and is used to
     * instantiate entities during database queries and object retrieval. The new instance is
     * initialized with the provided state manager and primary key.</p>
     *
     * @param pcStateManager StateManager the OpenJPA state manager to attach to this instance
     * @param o Object the object ID containing the primary key values
     * @param b boolean whether to clear all fields before copying key fields
     * @return PersistenceCapable a new instance configured with the specified state manager and ID
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicImage cachedDemographicImage = new CachedDemographicImage();
        if (b) {
            cachedDemographicImage.pcClearFields();
        }
        cachedDemographicImage.pcStateManager = pcStateManager;
        cachedDemographicImage.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicImage;
    }

    /**
     * Creates a new instance of this entity with the specified state manager.
     *
     * <p>This overloaded variant creates a new entity instance without initializing primary key
     * fields from an object ID. It is used by OpenJPA when the key will be set separately.</p>
     *
     * @param pcStateManager StateManager the OpenJPA state manager to attach to this instance
     * @param b boolean whether to clear all fields after instantiation
     * @return PersistenceCapable a new instance configured with the specified state manager
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicImage cachedDemographicImage = new CachedDemographicImage();
        if (b) {
            cachedDemographicImage.pcClearFields();
        }
        cachedDemographicImage.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicImage;
    }

    /**
     * Returns the number of persistent fields managed by OpenJPA for this entity.
     *
     * <p>This entity has 3 managed fields: facilityDemographicPk, image, and updateDate.</p>
     *
     * @return int the count of managed fields (always 3)
     */
    protected static int pcGetManagedFieldCount() {
        return 3;
    }
    
    /**
     * Replaces a single persistent field value using the state manager.
     *
     * <p>This method is part of OpenJPA's field interception mechanism. When a field is accessed
     * or modified, OpenJPA uses this method to replace the field value with a managed version,
     * enabling lazy loading and dirty checking.</p>
     *
     * @param n int the absolute field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicImage.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityDemographicPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.image = (byte[])this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.updateDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple persistent field values using the state manager.
     *
     * <p>This is a batch version of {@link #pcReplaceField(int)} that processes multiple
     * fields in sequence, used by OpenJPA to efficiently manage field state.</p>
     *
     * @param array int[] array of absolute field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single field value to the state manager.
     *
     * <p>This method is part of OpenJPA's field interception mechanism. When OpenJPA needs
     * to read a field value (for dirty checking, persistence, or serialization), it calls
     * this method to retrieve the current value and pass it to the state manager.</p>
     *
     * @param n int the absolute field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedDemographicImage.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityDemographicPk);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.image);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.updateDate);
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
     * <p>This is a batch version of {@link #pcProvideField(int)} that processes multiple
     * fields in sequence, used by OpenJPA for efficient state management operations.</p>
     *
     * @param array int[] array of absolute field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    /**
     * Copies a single field value from another entity instance.
     *
     * <p>This method is used by OpenJPA when copying state between entity instances,
     * such as during merge operations or detachment/attachment cycles.</p>
     *
     * @param cachedDemographicImage CachedDemographicImage the source entity to copy from
     * @param n int the absolute field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final CachedDemographicImage cachedDemographicImage, final int n) {
        final int n2 = n - CachedDemographicImage.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityDemographicPk = cachedDemographicImage.facilityDemographicPk;
                return;
            }
            case 1: {
                this.image = cachedDemographicImage.image;
                return;
            }
            case 2: {
                this.updateDate = cachedDemographicImage.updateDate;
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
     * <p>This is a batch version of {@link #pcCopyField(CachedDemographicImage, int)} that
     * validates both entities are managed by the same state manager before copying fields.</p>
     *
     * @param o Object the source entity to copy from (must be a CachedDemographicImage)
     * @param array int[] array of absolute field indices to copy
     * @throws IllegalArgumentException if the source has a different state manager
     * @throws IllegalStateException if this entity has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicImage cachedDemographicImage = (CachedDemographicImage)o;
        if (cachedDemographicImage.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicImage, array[i]);
        }
    }
    
    /**
     * Retrieves the generic persistence context associated with this entity.
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
     * Fetches the object ID for this entity.
     *
     * @return Object the object ID representing the primary key, or null if not managed
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
     * @return boolean true if the entity is scheduled for deletion in the current transaction
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has been modified since it was loaded.
     *
     * <p>This method performs a dirty check to determine if any persistent fields have
     * changed. OpenJPA uses this for optimizing database writes during transaction commit.</p>
     *
     * @return boolean true if the entity has uncommitted changes
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
     * Checks if this entity is newly created and not yet persisted to the database.
     *
     * @return boolean true if the entity has been created but not yet flushed to the database
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is currently managed by a persistence context.
     *
     * @return boolean true if the entity is persistent (managed by OpenJPA)
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity is enrolled in an active transaction.
     *
     * @return boolean true if the entity is participating in a transaction
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity is currently being serialized.
     *
     * @return boolean true if serialization is in progress
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a specific field as dirty to trigger persistence on transaction commit.
     *
     * <p>This method notifies the state manager that a field has been modified, ensuring
     * OpenJPA will include it in the next database write operation.</p>
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
     * Retrieves the OpenJPA state manager managing this entity's persistence lifecycle.
     *
     * @return StateManager the state manager, or null if the entity is detached
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Retrieves the version number for optimistic locking.
     *
     * <p>OpenJPA uses this version to detect concurrent modifications and prevent
     * lost updates in multi-user scenarios.</p>
     *
     * @return Object the version object, or null if not versioned or detached
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
     * <p>This method is used during entity lifecycle transitions such as attachment,
     * detachment, or when transferring management between persistence contexts.</p>
     *
     * @param pcStateManager StateManager the new state manager to install
     * @throws SecurityException if the replacement is not allowed
     */
    public void pcReplaceStateManager(final StateManager pcStateManager) throws SecurityException {
        if (this.pcStateManager != null) {
            this.pcStateManager = this.pcStateManager.replaceStateManager(pcStateManager);
            return;
        }
        this.pcStateManager = pcStateManager;
    }
    
    /**
     * Copies primary key fields to an object ID using a field supplier.
     *
     * <p>This operation is not supported for this entity type and will throw an exception.</p>
     *
     * @param fieldSupplier FieldSupplier the field supplier to use for copying
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies primary key fields to an object ID.
     *
     * <p>This operation is not supported for this entity type and will throw an exception.</p>
     *
     * @param o Object the target object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies primary key fields from an object ID using a field consumer.
     *
     * <p>This method extracts the composite primary key from an OpenJPA ObjectId and
     * stores it using the provided field consumer.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer to receive the key fields
     * @param o Object the source object ID (must be an OpenJPA ObjectId)
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(0 + CachedDemographicImage.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies primary key fields from an object ID into this entity.
     *
     * <p>This method extracts the composite primary key from an OpenJPA ObjectId and
     * sets it as this entity's primary key.</p>
     *
     * @param o Object the source object ID (must be an OpenJPA ObjectId)
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityDemographicPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * <p>This operation is not supported as the ObjectId class does not have the required
     * string-based constructor.</p>
     *
     * @param o Object the string representation of the object ID
     * @return Object never returns; always throws exception
     * @throws IllegalArgumentException always thrown as this operation is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicImage\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance from this entity's primary key.
     *
     * <p>This method generates an OpenJPA ObjectId wrapper around this entity's
     * composite primary key for use in identity operations and caching.</p>
     *
     * @return Object a new ObjectId containing this entity's primary key
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicImage.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicImage != null) ? CachedDemographicImage.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicImage : (CachedDemographicImage.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicImage = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicImage")), (Object)this.facilityDemographicPk);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityDemographicPk(final CachedDemographicImage cachedDemographicImage) {
        if (cachedDemographicImage.pcStateManager == null) {
            return cachedDemographicImage.facilityDemographicPk;
        }
        cachedDemographicImage.pcStateManager.accessingField(CachedDemographicImage.pcInheritedFieldCount + 0);
        return cachedDemographicImage.facilityDemographicPk;
    }
    
    private static final void pcSetfacilityDemographicPk(final CachedDemographicImage cachedDemographicImage, final FacilityIdIntegerCompositePk facilityDemographicPk) {
        if (cachedDemographicImage.pcStateManager == null) {
            cachedDemographicImage.facilityDemographicPk = facilityDemographicPk;
            return;
        }
        cachedDemographicImage.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicImage, CachedDemographicImage.pcInheritedFieldCount + 0, (Object)cachedDemographicImage.facilityDemographicPk, (Object)facilityDemographicPk, 0);
    }
    
    private static final byte[] pcGetimage(final CachedDemographicImage cachedDemographicImage) {
        if (cachedDemographicImage.pcStateManager == null) {
            return cachedDemographicImage.image;
        }
        cachedDemographicImage.pcStateManager.accessingField(CachedDemographicImage.pcInheritedFieldCount + 1);
        return cachedDemographicImage.image;
    }
    
    private static final void pcSetimage(final CachedDemographicImage cachedDemographicImage, final byte[] image) {
        if (cachedDemographicImage.pcStateManager == null) {
            cachedDemographicImage.image = image;
            return;
        }
        cachedDemographicImage.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicImage, CachedDemographicImage.pcInheritedFieldCount + 1, (Object)cachedDemographicImage.image, (Object)image, 0);
    }
    
    private static final Date pcGetupdateDate(final CachedDemographicImage cachedDemographicImage) {
        if (cachedDemographicImage.pcStateManager == null) {
            return cachedDemographicImage.updateDate;
        }
        cachedDemographicImage.pcStateManager.accessingField(CachedDemographicImage.pcInheritedFieldCount + 2);
        return cachedDemographicImage.updateDate;
    }
    
    private static final void pcSetupdateDate(final CachedDemographicImage cachedDemographicImage, final Date updateDate) {
        if (cachedDemographicImage.pcStateManager == null) {
            cachedDemographicImage.updateDate = updateDate;
            return;
        }
        cachedDemographicImage.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicImage, CachedDemographicImage.pcInheritedFieldCount + 2, (Object)cachedDemographicImage.updateDate, (Object)updateDate, 0);
    }
    
    /**
     * Checks if this entity is currently detached from its persistence context.
     *
     * <p>An entity is detached when it is no longer managed by OpenJPA but may still
     * carry state information. This method returns a tri-state Boolean:</p>
     * <ul>
     * <li>TRUE - Definitely detached</li>
     * <li>FALSE - Definitely not detached (managed or never persisted)</li>
     * <li>null - Detachment state is indeterminate</li>
     * </ul>
     *
     * @return Boolean the detachment state, or null if indeterminate
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
     * Determines if the detached state is definitive.
     *
     * <p>This implementation always returns false, indicating that the detached state
     * cannot be definitively determined without a state manager.</p>
     *
     * @return boolean always false for this entity
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Retrieves the detached state marker for this entity.
     *
     * @return Object the detached state, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state marker for this entity.
     *
     * @param pcDetachedState Object the detached state to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method to handle OpenJPA state during object serialization.
     *
     * <p>This method ensures that the detached state is properly cleared when an entity
     * is being serialized by OpenJPA, preventing serialization of internal persistence
     * state that should not be transmitted.</p>
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
     * Custom deserialization method to handle OpenJPA state during object deserialization.
     *
     * <p>This method marks the entity as deserialized, indicating it was reconstituted
     * from a serialized form and may need to be reattached to a persistence context.</p>
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
