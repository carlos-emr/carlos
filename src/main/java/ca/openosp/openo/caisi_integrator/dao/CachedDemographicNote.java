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
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.PostLoad;
import java.util.Iterator;
import javax.xml.bind.annotation.XmlTransient;
import java.util.HashSet;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.Transient;
import javax.persistence.JoinColumn;
import javax.persistence.CollectionTable;
import javax.persistence.FetchType;
import javax.persistence.ElementCollection;
import java.util.Set;
import javax.persistence.Column;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.EmbeddedId;
import java.util.Comparator;
import javax.persistence.Entity;

/**
 * JPA entity representing cached demographic clinical notes from integrated healthcare facilities.
 *
 * This class is part of the CAISI Integrator module, which enables inter-EMR data sharing across
 * multiple OpenO EMR installations. It stores clinical notes (encounter documentation) associated
 * with patient demographics, cached locally from remote integrated facilities to improve performance
 * and enable offline access to critical patient care information.
 *
 * The entity uses OpenJPA enhancement for transparent persistence and implements the PersistenceCapable
 * interface to support advanced JPA features including lazy loading, dirty tracking, and detachment.
 * Clinical notes are stored with full audit trail information including observation dates, update dates,
 * provider identifiers, program associations, and clinical issue coding.
 *
 * Key features:
 * - Composite primary key based on facility ID and UUID for distributed system uniqueness
 * - Support for clinical issue coding through NoteIssue enumeration
 * - Audit trail with observation and update timestamps
 * - Provider tracking for both observing and signing providers
 * - Program-based organization for community health programs (CAISI)
 * - Encounter type classification for clinical workflow
 * - Medium text storage for comprehensive clinical documentation
 *
 * This class is enhanced by OpenJPA bytecode weaving to add persistence capabilities including
 * state management, field-level change tracking, and lazy loading support. The enhancement process
 * adds additional methods prefixed with 'pc' that should not be called directly by application code.
 *
 * @see CachedDemographicNoteCompositePk
 * @see NoteIssue
 * @see AbstractModel
 * @since 2026-01-24
 */
@Entity
public class CachedDemographicNote extends AbstractModel<CachedDemographicNoteCompositePk> implements PersistenceCapable
{
    public static final Comparator<CachedDemographicNote> OBSERVATION_DATE_COMPARATOR;
    @EmbeddedId
    @Index
    private CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date updateDate;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date observationDate;
    @Column(nullable = false)
    private int caisiDemographicId;
    @Column(nullable = false, length = 16)
    private String observationCaisiProviderId;
    @Column(length = 16)
    private String signingCaisiProviderId;
    @Column(nullable = false, length = 100)
    private String encounterType;
    @Column(nullable = false)
    private int caisiProgramId;
    @Column(columnDefinition = "mediumtext")
    private String note;
    @Column(nullable = false, length = 64)
    private String role;
    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "CachedDemographicNoteIssues", joinColumns = { @JoinColumn(name = "integratorFacilityId", referencedColumnName = "integratorFacilityId"), @JoinColumn(name = "uuid", referencedColumnName = "uuid") })
    @Column(name = "noteIssue")
    private Set<String> noteIssues;
    @Transient
    private Set<NoteIssue> issues;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNoteCompositePk;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$util$Set;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNote;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Default constructor required by JPA specification.
     *
     * Initializes all fields to their default values. Collections are initialized as empty HashSets.
     * This constructor is primarily used by the JPA persistence provider during entity instantiation
     * and should not typically be called directly by application code.
     */
    public CachedDemographicNote() {
        this.updateDate = null;
        this.observationDate = null;
        this.caisiDemographicId = 0;
        this.observationCaisiProviderId = null;
        this.signingCaisiProviderId = null;
        this.encounterType = null;
        this.caisiProgramId = 0;
        this.note = null;
        this.role = null;
        this.noteIssues = new HashSet<String>();
        this.issues = new HashSet<NoteIssue>();
    }

    /**
     * Constructs a new cached demographic note with required identifying and clinical information.
     *
     * This constructor initializes the composite primary key and essential clinical observation data.
     * It is used when creating new cached note entries from integrated facility data. Additional
     * properties such as encounter type, signing provider, and note content should be set using
     * the appropriate setter methods after construction.
     *
     * @param integratedFacilityId Integer the unique identifier of the integrated facility providing this data
     * @param uuid String the universally unique identifier for this note across the distributed system
     * @param caisiDemographicId int the demographic (patient) identifier in the CAISI system
     * @param caisiProgramId int the program identifier this note is associated with
     * @param observationCaisiProviderId String the provider identifier who made the clinical observation (max 16 chars)
     * @param observationDate Date the timestamp when the clinical observation was made
     * @param role String the role classification for this note (max 64 chars)
     */
    public CachedDemographicNote(final Integer integratedFacilityId, final String uuid, final int caisiDemographicId, final int caisiProgramId, final String observationCaisiProviderId, final Date observationDate, final String role) {
        this.updateDate = null;
        this.observationDate = null;
        this.caisiDemographicId = 0;
        this.observationCaisiProviderId = null;
        this.signingCaisiProviderId = null;
        this.encounterType = null;
        this.caisiProgramId = 0;
        this.note = null;
        this.role = null;
        this.noteIssues = new HashSet<String>();
        this.issues = new HashSet<NoteIssue>();
        this.cachedDemographicNoteCompositePk = new CachedDemographicNoteCompositePk(integratedFacilityId, uuid);
        this.caisiDemographicId = caisiDemographicId;
        this.caisiProgramId = caisiProgramId;
        this.observationCaisiProviderId = observationCaisiProviderId;
        this.observationDate = observationDate;
        this.role = role;
    }

    /**
     * Sets the composite primary key for this cached demographic note.
     *
     * @param cachedDemographicNoteCompositePk CachedDemographicNoteCompositePk the composite key containing facility ID and UUID
     */
    public void setCachedDemographicNoteCompositePk(final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk) {
        pcSetcachedDemographicNoteCompositePk(this, cachedDemographicNoteCompositePk);
    }

    /**
     * Gets the composite primary key for this cached demographic note.
     *
     * @return CachedDemographicNoteCompositePk the composite key containing facility ID and UUID
     */
    public CachedDemographicNoteCompositePk getCachedDemographicNoteCompositePk() {
        return pcGetcachedDemographicNoteCompositePk(this);
    }

    /**
     * Gets the entity identifier (composite primary key).
     *
     * This method overrides the AbstractModel getId() method to provide type-specific access
     * to the composite key.
     *
     * @return CachedDemographicNoteCompositePk the composite primary key
     */
    @Override
    public CachedDemographicNoteCompositePk getId() {
        return pcGetcachedDemographicNoteCompositePk(this);
    }

    /**
     * Gets the timestamp when this cached note record was last updated.
     *
     * @return Date the last update timestamp, used for cache synchronization and audit trails
     */
    public Date getUpdateDate() {
        return pcGetupdateDate(this);
    }

    /**
     * Sets the timestamp when this cached note record was last updated.
     *
     * @param updateDate Date the last update timestamp
     */
    public void setUpdateDate(final Date updateDate) {
        pcSetupdateDate(this, updateDate);
    }

    /**
     * Gets the clinical observation date when this note was originally created.
     *
     * @return Date the timestamp when the clinical observation was made
     */
    public Date getObservationDate() {
        return pcGetobservationDate(this);
    }

    /**
     * Sets the clinical observation date when this note was originally created.
     *
     * @param observationDate Date the timestamp when the clinical observation was made
     */
    public void setObservationDate(final Date observationDate) {
        pcSetobservationDate(this, observationDate);
    }

    /**
     * Gets the CAISI demographic (patient) identifier this note is associated with.
     *
     * @return int the demographic identifier in the CAISI system
     */
    public int getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }

    /**
     * Sets the CAISI demographic (patient) identifier this note is associated with.
     *
     * @param caisiDemographicId int the demographic identifier in the CAISI system
     */
    public void setCaisiDemographicId(final int caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }

    /**
     * Gets the provider identifier who made the clinical observation.
     *
     * @return String the CAISI provider identifier (max 16 characters)
     */
    public String getObservationCaisiProviderId() {
        return pcGetobservationCaisiProviderId(this);
    }

    /**
     * Sets the provider identifier who made the clinical observation.
     *
     * @param observationCaisiProviderId String the CAISI provider identifier (max 16 characters)
     */
    public void setObservationCaisiProviderId(final String observationCaisiProviderId) {
        pcSetobservationCaisiProviderId(this, observationCaisiProviderId);
    }

    /**
     * Gets the CAISI program identifier this note is associated with.
     *
     * @return int the program identifier for community health program organization
     */
    public int getCaisiProgramId() {
        return pcGetcaisiProgramId(this);
    }

    /**
     * Sets the CAISI program identifier this note is associated with.
     *
     * @param caisiProgramId int the program identifier for community health program organization
     */
    public void setCaisiProgramId(final int caisiProgramId) {
        pcSetcaisiProgramId(this, caisiProgramId);
    }

    /**
     * Gets the role classification for this clinical note.
     *
     * @return String the role identifier (max 64 characters)
     */
    public String getRole() {
        return pcGetrole(this);
    }

    /**
     * Sets the role classification for this clinical note.
     *
     * @param role String the role identifier (max 64 characters)
     */
    public void setRole(final String role) {
        pcSetrole(this, role);
    }

    /**
     * Gets the clinical note text content.
     *
     * @return String the note content, stored as medium text (up to ~16MB)
     */
    public String getNote() {
        return pcGetnote(this);
    }

    /**
     * Sets the clinical note text content.
     *
     * @param note String the note content, stored as medium text (up to ~16MB)
     */
    public void setNote(final String note) {
        pcSetnote(this, note);
    }

    /**
     * Gets the provider identifier who signed off on this clinical note.
     *
     * @return String the CAISI provider identifier of the signing provider (max 16 characters), may be null if unsigned
     */
    public String getSigningCaisiProviderId() {
        return pcGetsigningCaisiProviderId(this);
    }

    /**
     * Sets the provider identifier who signed off on this clinical note.
     *
     * @param signingCaisiProviderId String the CAISI provider identifier of the signing provider (max 16 characters)
     */
    public void setSigningCaisiProviderId(final String signingCaisiProviderId) {
        pcSetsigningCaisiProviderId(this, signingCaisiProviderId);
    }

    /**
     * Gets the encounter type classification for this clinical note.
     *
     * @return String the encounter type identifier (max 100 characters)
     */
    public String getEncounterType() {
        return pcGetencounterType(this);
    }

    /**
     * Sets the encounter type classification for this clinical note.
     *
     * @param encounterType String the encounter type identifier (max 100 characters)
     */
    public void setEncounterType(final String encounterType) {
        pcSetencounterType(this, encounterType);
    }

    /**
     * Gets the transient set of clinical issues associated with this note.
     *
     * This is a transient field populated from the persisted noteIssues string collection
     * during the PostLoad lifecycle event. It provides type-safe access to NoteIssue enums.
     *
     * @return Set&lt;NoteIssue&gt; the set of clinical issues, converted from string representations
     */
    public Set<NoteIssue> getIssues() {
        return this.issues;
    }

    /**
     * Sets the transient set of clinical issues associated with this note.
     *
     * Changes to this field are persisted to the database through the noteIssues collection
     * during the PrePersist and PreUpdate lifecycle events.
     *
     * @param issues Set&lt;NoteIssue&gt; the set of clinical issues to associate with this note
     */
    public void setIssues(final Set<NoteIssue> issues) {
        this.issues = issues;
    }

    /**
     * Gets the persisted string collection of note issues.
     *
     * This field stores the string representations of NoteIssue enums for database persistence.
     * The @XmlTransient annotation excludes this field from XML serialization to prevent duplication
     * with the issues field in XML representations.
     *
     * @return Set&lt;String&gt; the set of note issue string identifiers
     */
    @XmlTransient
    public Set<String> getNoteIssues() {
        return pcGetnoteIssues(this);
    }

    /**
     * Sets the persisted string collection of note issues.
     *
     * @param noteIssues Set&lt;String&gt; the set of note issue string identifiers
     */
    public void setNoteIssues(final Set<String> noteIssues) {
        pcSetnoteIssues(this, noteIssues);
    }

    /**
     * JPA PostLoad lifecycle callback that converts persisted string issue codes to NoteIssue enums.
     *
     * This method is automatically invoked by the JPA provider after an entity is loaded from the database.
     * It populates the transient issues collection by converting each string in noteIssues to its
     * corresponding NoteIssue enum value, providing type-safe access to clinical issue coding.
     */
    @PostLoad
    protected void logRead() {
        for (final Object obj : pcGetnoteIssues(this)) {
            final String noteIssue = (String)obj;
            this.getIssues().add(NoteIssue.valueOf(noteIssue));
        }
    }

    /**
     * JPA PrePersist and PreUpdate lifecycle callback that converts NoteIssue enums to strings for persistence.
     *
     * This method is automatically invoked by the JPA provider before an entity is inserted or updated
     * in the database. It synchronizes the transient issues collection to the persisted noteIssues
     * collection by converting each NoteIssue enum to its string representation.
     */
    @PreUpdate
    @PrePersist
    protected void logWrite() {
        for (final NoteIssue issue : this.issues) {
            this.getNoteIssues().add(issue.asString());
        }
    }
    
    static {
        serialVersionUID = 3202392287993714226L;
        OBSERVATION_DATE_COMPARATOR = new Comparator<CachedDemographicNote>() {
            @Override
            public int compare(final CachedDemographicNote o1, final CachedDemographicNote o2) {
                return o1.getObservationDate().compareTo(o2.getObservationDate());
            }
        };
        CachedDemographicNote.pcFieldNames = new String[] { "cachedDemographicNoteCompositePk", "caisiDemographicId", "caisiProgramId", "encounterType", "note", "noteIssues", "observationCaisiProviderId", "observationDate", "role", "signingCaisiProviderId", "updateDate" };
        CachedDemographicNote.pcFieldTypes = new Class[] { (CachedDemographicNote.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNoteCompositePk != null) ? CachedDemographicNote.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNoteCompositePk : (CachedDemographicNote.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNoteCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicNoteCompositePk")), Integer.TYPE, Integer.TYPE, (CachedDemographicNote.class$Ljava$lang$String != null) ? CachedDemographicNote.class$Ljava$lang$String : (CachedDemographicNote.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicNote.class$Ljava$lang$String != null) ? CachedDemographicNote.class$Ljava$lang$String : (CachedDemographicNote.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicNote.class$Ljava$util$Set != null) ? CachedDemographicNote.class$Ljava$util$Set : (CachedDemographicNote.class$Ljava$util$Set = class$("java.util.Set")), (CachedDemographicNote.class$Ljava$lang$String != null) ? CachedDemographicNote.class$Ljava$lang$String : (CachedDemographicNote.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicNote.class$Ljava$util$Date != null) ? CachedDemographicNote.class$Ljava$util$Date : (CachedDemographicNote.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographicNote.class$Ljava$lang$String != null) ? CachedDemographicNote.class$Ljava$lang$String : (CachedDemographicNote.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicNote.class$Ljava$lang$String != null) ? CachedDemographicNote.class$Ljava$lang$String : (CachedDemographicNote.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicNote.class$Ljava$util$Date != null) ? CachedDemographicNote.class$Ljava$util$Date : (CachedDemographicNote.class$Ljava$util$Date = class$("java.util.Date")) };
        CachedDemographicNote.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 10, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographicNote.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNote != null) ? CachedDemographicNote.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNote : (CachedDemographicNote.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNote = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicNote")), CachedDemographicNote.pcFieldNames, CachedDemographicNote.pcFieldTypes, CachedDemographicNote.pcFieldFlags, CachedDemographicNote.pcPCSuperclass, "CachedDemographicNote", (PersistenceCapable)new CachedDemographicNote());
    }

    /**
     * Gets the OpenJPA enhancement contract version number.
     *
     * This method is part of the PersistenceCapable contract and returns the version number
     * of the enhancement specification that this class complies with. This is used internally
     * by OpenJPA to ensure compatibility between enhanced classes and the persistence framework.
     *
     * @return int the enhancement contract version (2 for current OpenJPA version)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    /**
     * Utility method to load a class by name (synthetic method generated during compilation).
     *
     * This is a compiler-generated synthetic method used to optimize class literal references
     * in the static initializer block. It should not be called directly by application code.
     *
     * @param className String the fully qualified name of the class to load
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
     * Clears all persistent field values to their default states.
     *
     * This method is part of the OpenJPA PersistenceCapable contract and is used internally
     * by the persistence framework during entity lifecycle management. It should not be called
     * directly by application code.
     */
    protected void pcClearFields() {
        this.cachedDemographicNoteCompositePk = null;
        this.caisiDemographicId = 0;
        this.caisiProgramId = 0;
        this.encounterType = null;
        this.note = null;
        this.noteIssues = null;
        this.observationCaisiProviderId = null;
        this.observationDate = null;
        this.role = null;
        this.signingCaisiProviderId = null;
        this.updateDate = null;
    }

    /**
     * Creates a new instance of this entity with a state manager and object ID.
     *
     * This method is part of the PersistenceCapable contract and is used by OpenJPA to create
     * new entity instances during query results processing and entity lifecycle operations.
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object ID containing the entity's primary key values
     * @param b boolean if true, clears all field values to defaults after creation
     * @return PersistenceCapable the newly created entity instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicNote cachedDemographicNote = new CachedDemographicNote();
        if (b) {
            cachedDemographicNote.pcClearFields();
        }
        cachedDemographicNote.pcStateManager = pcStateManager;
        cachedDemographicNote.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicNote;
    }

    /**
     * Creates a new instance of this entity with a state manager.
     *
     * This method is part of the PersistenceCapable contract and is used by OpenJPA to create
     * new entity instances without pre-populating the primary key values.
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean if true, clears all field values to defaults after creation
     * @return PersistenceCapable the newly created entity instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicNote cachedDemographicNote = new CachedDemographicNote();
        if (b) {
            cachedDemographicNote.pcClearFields();
        }
        cachedDemographicNote.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicNote;
    }

    /**
     * Gets the count of managed persistent fields in this entity.
     *
     * This method is part of the PersistenceCapable contract and returns the number of fields
     * that are managed by the OpenJPA persistence framework. This count is used internally
     * for field indexing and state management operations.
     *
     * @return int the number of managed fields (11 for this entity)
     */
    protected static int pcGetManagedFieldCount() {
        return 11;
    }

    /**
     * Replaces a single persistent field value with the value from the state manager.
     *
     * This method is part of the PersistenceCapable contract and is called by OpenJPA during
     * entity state restoration operations (e.g., rollback, refresh). It replaces the field
     * at the specified index with the value stored in the state manager.
     *
     * @param n int the field index (relative to pcInheritedFieldCount)
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicNote.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.cachedDemographicNoteCompositePk = (CachedDemographicNoteCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.caisiDemographicId = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.caisiProgramId = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.encounterType = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.note = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.noteIssues = (Set)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.observationCaisiProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.observationDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.role = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.signingCaisiProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 10: {
                this.updateDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple persistent field values with values from the state manager.
     *
     * This is a convenience method that calls pcReplaceField for each field index in the array.
     * Part of the PersistenceCapable contract for batch field restoration operations.
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides the current value of a single persistent field to the state manager.
     *
     * This method is part of the PersistenceCapable contract and is called by OpenJPA to
     * read field values from the entity and provide them to the state manager for tracking,
     * persistence, or detachment operations.
     *
     * @param n int the field index (relative to pcInheritedFieldCount)
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedDemographicNote.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.cachedDemographicNoteCompositePk);
                return;
            }
            case 1: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.caisiDemographicId);
                return;
            }
            case 2: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.caisiProgramId);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.encounterType);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.note);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.noteIssues);
                return;
            }
            case 6: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.observationCaisiProviderId);
                return;
            }
            case 7: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.observationDate);
                return;
            }
            case 8: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.role);
                return;
            }
            case 9: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.signingCaisiProviderId);
                return;
            }
            case 10: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.updateDate);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Provides the current values of multiple persistent fields to the state manager.
     *
     * This is a convenience method that calls pcProvideField for each field index in the array.
     * Part of the PersistenceCapable contract for batch field access operations.
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }

    /**
     * Copies a single field value from another instance of this entity.
     *
     * This method is part of the PersistenceCapable contract and is used by OpenJPA for
     * entity cloning and merging operations. It copies the field value at the specified
     * index from the source entity to this entity.
     *
     * @param cachedDemographicNote CachedDemographicNote the source entity to copy from
     * @param n int the field index (relative to pcInheritedFieldCount)
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final CachedDemographicNote cachedDemographicNote, final int n) {
        final int n2 = n - CachedDemographicNote.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.cachedDemographicNoteCompositePk = cachedDemographicNote.cachedDemographicNoteCompositePk;
                return;
            }
            case 1: {
                this.caisiDemographicId = cachedDemographicNote.caisiDemographicId;
                return;
            }
            case 2: {
                this.caisiProgramId = cachedDemographicNote.caisiProgramId;
                return;
            }
            case 3: {
                this.encounterType = cachedDemographicNote.encounterType;
                return;
            }
            case 4: {
                this.note = cachedDemographicNote.note;
                return;
            }
            case 5: {
                this.noteIssues = cachedDemographicNote.noteIssues;
                return;
            }
            case 6: {
                this.observationCaisiProviderId = cachedDemographicNote.observationCaisiProviderId;
                return;
            }
            case 7: {
                this.observationDate = cachedDemographicNote.observationDate;
                return;
            }
            case 8: {
                this.role = cachedDemographicNote.role;
                return;
            }
            case 9: {
                this.signingCaisiProviderId = cachedDemographicNote.signingCaisiProviderId;
                return;
            }
            case 10: {
                this.updateDate = cachedDemographicNote.updateDate;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies multiple field values from another instance of this entity.
     *
     * This method is part of the PersistenceCapable contract and validates that both entities
     * share the same state manager before performing the copy operation.
     *
     * @param o Object the source entity to copy from (must be CachedDemographicNote)
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source entity has a different state manager
     * @throws IllegalStateException if this entity has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicNote cachedDemographicNote = (CachedDemographicNote)o;
        if (cachedDemographicNote.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicNote, array[i]);
        }
    }

    /**
     * Gets the generic context object from the state manager.
     *
     * Part of the PersistenceCapable contract. Returns the generic context associated with
     * this entity's state manager, or null if the entity is not managed.
     *
     * @return Object the generic context, or null if no state manager is attached
     */
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }

    /**
     * Fetches the JPA object identifier for this entity.
     *
     * Part of the PersistenceCapable contract. Returns the object ID used by the persistence
     * framework to uniquely identify this entity instance.
     *
     * @return Object the object identifier, or null if the entity is not managed
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
     * Part of the PersistenceCapable contract. Returns true if the entity has been deleted
     * in the current transaction but not yet flushed to the database.
     *
     * @return boolean true if the entity is marked for deletion, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this entity has been modified since it was loaded.
     *
     * Part of the PersistenceCapable contract. Returns true if any persistent fields have
     * been changed and the changes have not yet been flushed to the database.
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
     * Checks if this entity is newly created and not yet persisted.
     *
     * Part of the PersistenceCapable contract. Returns true if the entity has been created
     * in the current transaction but not yet flushed to the database.
     *
     * @return boolean true if the entity is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this entity is managed by the persistence context.
     *
     * Part of the PersistenceCapable contract. Returns true if the entity is associated
     * with a persistence context and its lifecycle is being tracked by the JPA provider.
     *
     * @return boolean true if the entity is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this entity is part of an active transaction.
     *
     * Part of the PersistenceCapable contract. Returns true if the entity is enlisted
     * in the current transaction context.
     *
     * @return boolean true if the entity is transactional, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this entity is currently being serialized.
     *
     * Part of the PersistenceCapable contract. Returns true during serialization operations
     * to allow special handling of persistent state.
     *
     * @return boolean true if serialization is in progress, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a field as dirty (modified) by name.
     *
     * Part of the PersistenceCapable contract. Notifies the state manager that a field
     * has been modified, triggering change tracking and update detection.
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
     * Gets the OpenJPA state manager attached to this entity.
     *
     * Part of the PersistenceCapable contract. Returns the state manager responsible
     * for tracking this entity's lifecycle and persistent state.
     *
     * @return StateManager the state manager, or null if the entity is not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Gets the version object for optimistic locking.
     *
     * Part of the PersistenceCapable contract. Returns the version value used for
     * optimistic locking to detect concurrent modifications.
     *
     * @return Object the version value, or null if the entity is not managed
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces the state manager attached to this entity.
     *
     * Part of the PersistenceCapable contract. This method is used by OpenJPA to transfer
     * entity management from one state manager to another, such as when transferring entities
     * between persistence contexts.
     *
     * @param pcStateManager StateManager the new state manager to attach
     * @throws SecurityException if the state manager replacement is not permitted
     */
    public void pcReplaceStateManager(final StateManager pcStateManager) throws SecurityException {
        if (this.pcStateManager != null) {
            this.pcStateManager = this.pcStateManager.replaceStateManager(pcStateManager);
            return;
        }
        this.pcStateManager = pcStateManager;
    }

    /**
     * Copies primary key field values to an object ID using a field supplier.
     *
     * Part of the PersistenceCapable contract. This method is not supported for this entity
     * type and throws an InternalException if called.
     *
     * @param fieldSupplier FieldSupplier the field supplier to use for copying
     * @param o Object the target object ID
     * @throws InternalException always, as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies primary key field values to an object ID.
     *
     * Part of the PersistenceCapable contract. This method is not supported for this entity
     * type and throws an InternalException if called.
     *
     * @param o Object the target object ID
     * @throws InternalException always, as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies primary key field values from an object ID using a field consumer.
     *
     * Part of the PersistenceCapable contract. This method extracts the composite primary
     * key from the provided ObjectId and stores it using the field consumer.
     *
     * @param fieldConsumer FieldConsumer the field consumer to receive the key values
     * @param o Object the source object ID containing the primary key
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(0 + CachedDemographicNote.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies primary key field values from an object ID to this entity.
     *
     * Part of the PersistenceCapable contract. This method extracts the composite primary
     * key from the provided ObjectId and assigns it to this entity's primary key field.
     *
     * @param o Object the source object ID containing the primary key
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.cachedDemographicNoteCompositePk = (CachedDemographicNoteCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * Part of the PersistenceCapable contract. This method is not supported for this entity
     * type because ObjectId does not have a suitable string constructor.
     *
     * @param o Object the string representation of the object ID
     * @return Object never returns (always throws exception)
     * @throws IllegalArgumentException always, as string-based object ID construction is not supported
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicNote\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance based on this entity's current primary key values.
     *
     * Part of the PersistenceCapable contract. This method constructs an ObjectId wrapper
     * around the entity's composite primary key for use in JPA operations.
     *
     * @return Object a new ObjectId containing this entity's primary key
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicNote.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNote != null) ? CachedDemographicNote.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNote : (CachedDemographicNote.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNote = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicNote")), (Object)this.cachedDemographicNoteCompositePk);
    }
    
    private static final CachedDemographicNoteCompositePk pcGetcachedDemographicNoteCompositePk(final CachedDemographicNote cachedDemographicNote) {
        if (cachedDemographicNote.pcStateManager == null) {
            return cachedDemographicNote.cachedDemographicNoteCompositePk;
        }
        cachedDemographicNote.pcStateManager.accessingField(CachedDemographicNote.pcInheritedFieldCount + 0);
        return cachedDemographicNote.cachedDemographicNoteCompositePk;
    }
    
    private static final void pcSetcachedDemographicNoteCompositePk(final CachedDemographicNote cachedDemographicNote, final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk) {
        if (cachedDemographicNote.pcStateManager == null) {
            cachedDemographicNote.cachedDemographicNoteCompositePk = cachedDemographicNoteCompositePk;
            return;
        }
        cachedDemographicNote.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicNote, CachedDemographicNote.pcInheritedFieldCount + 0, (Object)cachedDemographicNote.cachedDemographicNoteCompositePk, (Object)cachedDemographicNoteCompositePk, 0);
    }
    
    private static final int pcGetcaisiDemographicId(final CachedDemographicNote cachedDemographicNote) {
        if (cachedDemographicNote.pcStateManager == null) {
            return cachedDemographicNote.caisiDemographicId;
        }
        cachedDemographicNote.pcStateManager.accessingField(CachedDemographicNote.pcInheritedFieldCount + 1);
        return cachedDemographicNote.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedDemographicNote cachedDemographicNote, final int caisiDemographicId) {
        if (cachedDemographicNote.pcStateManager == null) {
            cachedDemographicNote.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedDemographicNote.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicNote, CachedDemographicNote.pcInheritedFieldCount + 1, cachedDemographicNote.caisiDemographicId, caisiDemographicId, 0);
    }
    
    private static final int pcGetcaisiProgramId(final CachedDemographicNote cachedDemographicNote) {
        if (cachedDemographicNote.pcStateManager == null) {
            return cachedDemographicNote.caisiProgramId;
        }
        cachedDemographicNote.pcStateManager.accessingField(CachedDemographicNote.pcInheritedFieldCount + 2);
        return cachedDemographicNote.caisiProgramId;
    }
    
    private static final void pcSetcaisiProgramId(final CachedDemographicNote cachedDemographicNote, final int caisiProgramId) {
        if (cachedDemographicNote.pcStateManager == null) {
            cachedDemographicNote.caisiProgramId = caisiProgramId;
            return;
        }
        cachedDemographicNote.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicNote, CachedDemographicNote.pcInheritedFieldCount + 2, cachedDemographicNote.caisiProgramId, caisiProgramId, 0);
    }
    
    private static final String pcGetencounterType(final CachedDemographicNote cachedDemographicNote) {
        if (cachedDemographicNote.pcStateManager == null) {
            return cachedDemographicNote.encounterType;
        }
        cachedDemographicNote.pcStateManager.accessingField(CachedDemographicNote.pcInheritedFieldCount + 3);
        return cachedDemographicNote.encounterType;
    }
    
    private static final void pcSetencounterType(final CachedDemographicNote cachedDemographicNote, final String encounterType) {
        if (cachedDemographicNote.pcStateManager == null) {
            cachedDemographicNote.encounterType = encounterType;
            return;
        }
        cachedDemographicNote.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicNote, CachedDemographicNote.pcInheritedFieldCount + 3, cachedDemographicNote.encounterType, encounterType, 0);
    }
    
    private static final String pcGetnote(final CachedDemographicNote cachedDemographicNote) {
        if (cachedDemographicNote.pcStateManager == null) {
            return cachedDemographicNote.note;
        }
        cachedDemographicNote.pcStateManager.accessingField(CachedDemographicNote.pcInheritedFieldCount + 4);
        return cachedDemographicNote.note;
    }
    
    private static final void pcSetnote(final CachedDemographicNote cachedDemographicNote, final String note) {
        if (cachedDemographicNote.pcStateManager == null) {
            cachedDemographicNote.note = note;
            return;
        }
        cachedDemographicNote.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicNote, CachedDemographicNote.pcInheritedFieldCount + 4, cachedDemographicNote.note, note, 0);
    }
    
    private static final Set pcGetnoteIssues(final CachedDemographicNote cachedDemographicNote) {
        if (cachedDemographicNote.pcStateManager == null) {
            return cachedDemographicNote.noteIssues;
        }
        cachedDemographicNote.pcStateManager.accessingField(CachedDemographicNote.pcInheritedFieldCount + 5);
        return cachedDemographicNote.noteIssues;
    }
    
    private static final void pcSetnoteIssues(final CachedDemographicNote cachedDemographicNote, final Set noteIssues) {
        if (cachedDemographicNote.pcStateManager == null) {
            cachedDemographicNote.noteIssues = noteIssues;
            return;
        }
        cachedDemographicNote.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicNote, CachedDemographicNote.pcInheritedFieldCount + 5, (Object)cachedDemographicNote.noteIssues, (Object)noteIssues, 0);
    }
    
    private static final String pcGetobservationCaisiProviderId(final CachedDemographicNote cachedDemographicNote) {
        if (cachedDemographicNote.pcStateManager == null) {
            return cachedDemographicNote.observationCaisiProviderId;
        }
        cachedDemographicNote.pcStateManager.accessingField(CachedDemographicNote.pcInheritedFieldCount + 6);
        return cachedDemographicNote.observationCaisiProviderId;
    }
    
    private static final void pcSetobservationCaisiProviderId(final CachedDemographicNote cachedDemographicNote, final String observationCaisiProviderId) {
        if (cachedDemographicNote.pcStateManager == null) {
            cachedDemographicNote.observationCaisiProviderId = observationCaisiProviderId;
            return;
        }
        cachedDemographicNote.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicNote, CachedDemographicNote.pcInheritedFieldCount + 6, cachedDemographicNote.observationCaisiProviderId, observationCaisiProviderId, 0);
    }
    
    private static final Date pcGetobservationDate(final CachedDemographicNote cachedDemographicNote) {
        if (cachedDemographicNote.pcStateManager == null) {
            return cachedDemographicNote.observationDate;
        }
        cachedDemographicNote.pcStateManager.accessingField(CachedDemographicNote.pcInheritedFieldCount + 7);
        return cachedDemographicNote.observationDate;
    }
    
    private static final void pcSetobservationDate(final CachedDemographicNote cachedDemographicNote, final Date observationDate) {
        if (cachedDemographicNote.pcStateManager == null) {
            cachedDemographicNote.observationDate = observationDate;
            return;
        }
        cachedDemographicNote.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicNote, CachedDemographicNote.pcInheritedFieldCount + 7, (Object)cachedDemographicNote.observationDate, (Object)observationDate, 0);
    }
    
    private static final String pcGetrole(final CachedDemographicNote cachedDemographicNote) {
        if (cachedDemographicNote.pcStateManager == null) {
            return cachedDemographicNote.role;
        }
        cachedDemographicNote.pcStateManager.accessingField(CachedDemographicNote.pcInheritedFieldCount + 8);
        return cachedDemographicNote.role;
    }
    
    private static final void pcSetrole(final CachedDemographicNote cachedDemographicNote, final String role) {
        if (cachedDemographicNote.pcStateManager == null) {
            cachedDemographicNote.role = role;
            return;
        }
        cachedDemographicNote.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicNote, CachedDemographicNote.pcInheritedFieldCount + 8, cachedDemographicNote.role, role, 0);
    }
    
    private static final String pcGetsigningCaisiProviderId(final CachedDemographicNote cachedDemographicNote) {
        if (cachedDemographicNote.pcStateManager == null) {
            return cachedDemographicNote.signingCaisiProviderId;
        }
        cachedDemographicNote.pcStateManager.accessingField(CachedDemographicNote.pcInheritedFieldCount + 9);
        return cachedDemographicNote.signingCaisiProviderId;
    }
    
    private static final void pcSetsigningCaisiProviderId(final CachedDemographicNote cachedDemographicNote, final String signingCaisiProviderId) {
        if (cachedDemographicNote.pcStateManager == null) {
            cachedDemographicNote.signingCaisiProviderId = signingCaisiProviderId;
            return;
        }
        cachedDemographicNote.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicNote, CachedDemographicNote.pcInheritedFieldCount + 9, cachedDemographicNote.signingCaisiProviderId, signingCaisiProviderId, 0);
    }
    
    private static final Date pcGetupdateDate(final CachedDemographicNote cachedDemographicNote) {
        if (cachedDemographicNote.pcStateManager == null) {
            return cachedDemographicNote.updateDate;
        }
        cachedDemographicNote.pcStateManager.accessingField(CachedDemographicNote.pcInheritedFieldCount + 10);
        return cachedDemographicNote.updateDate;
    }
    
    private static final void pcSetupdateDate(final CachedDemographicNote cachedDemographicNote, final Date updateDate) {
        if (cachedDemographicNote.pcStateManager == null) {
            cachedDemographicNote.updateDate = updateDate;
            return;
        }
        cachedDemographicNote.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicNote, CachedDemographicNote.pcInheritedFieldCount + 10, (Object)cachedDemographicNote.updateDate, (Object)updateDate, 0);
    }

    /**
     * Checks if this entity is in a detached state.
     *
     * Part of the PersistenceCapable contract. A detached entity is one that was previously
     * managed by a persistence context but is no longer associated with it. This method returns:
     * - Boolean.TRUE if the entity is definitely detached
     * - Boolean.FALSE if the entity is definitely not detached
     * - null if the detachment state cannot be determined definitively
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
     * Checks if the detached state determination is definitive.
     *
     * Internal helper method for pcIsDetached(). Returns false to indicate that detachment
     * state determination requires additional context beyond the detached state field.
     *
     * @return boolean always returns false for this entity type
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Gets the detached state marker object.
     *
     * Part of the PersistenceCapable contract. The detached state object is used to track
     * whether an entity instance has been detached from its persistence context.
     *
     * @return Object the detached state marker, or null if never detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state marker object.
     *
     * Part of the PersistenceCapable contract. Used by the JPA provider to mark an entity
     * as detached or to clear the detachment state.
     *
     * @param pcDetachedState Object the detached state marker to set
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
