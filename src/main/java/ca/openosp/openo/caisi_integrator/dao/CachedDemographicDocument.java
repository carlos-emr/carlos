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
import java.util.Date;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

/**
 * Represents a cached demographic document entity in the CAISI Integrator system.
 *
 * <p>This entity stores document metadata and content associated with patient demographics
 * in a multi-facility healthcare environment. It is used by the CAISI (Collaborative
 * Application for Integrated Systems Information) Integrator to cache and manage patient
 * documents across different healthcare facilities.</p>
 *
 * <p>This class is enhanced by Apache OpenJPA for persistence management, implementing the
 * {@link PersistenceCapable} interface to support advanced ORM features including lazy loading,
 * dirty state tracking, and detachment/reattachment of entities. The OpenJPA enhancement adds
 * specialized getter/setter methods (prefixed with 'pc') that integrate with the JPA state
 * management system.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Multi-facility support through composite primary key ({@link FacilityIdIntegerCompositePk})</li>
 *   <li>Document metadata tracking (type, creator, filename, content type)</li>
 *   <li>Document content storage (XML and binary formats)</li>
 *   <li>Review workflow support (reviewer, review date/time)</li>
 *   <li>Program association for care management integration</li>
 *   <li>Appointment linkage for contextual document access</li>
 * </ul>
 *
 * <p><strong>Healthcare Context:</strong></p>
 * <p>In a healthcare EMR system, documents can include lab results, imaging reports, consultation
 * notes, consent forms, and other clinical documents. This cached representation allows the
 * integrator to provide fast access to documents from multiple facilities without repeated
 * remote data fetches.</p>
 *
 * @see AbstractModel
 * @see FacilityIdIntegerCompositePk
 * @see PersistenceCapable
 * @since 2026-01-24
 */
@Entity
public class CachedDemographicDocument extends AbstractModel<FacilityIdIntegerCompositePk> implements PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityIntegerPk;
    @Column(nullable = false)
    @Index
    private int caisiDemographicId;
    private String docType;
    private String docXml;
    private String docFilename;
    private String docCreator;
    private String responsible;
    private String source;
    private Integer programId;
    private Date updateDateTime;
    private String status;
    private String contentType;
    private int public1;
    private Date observationDate;
    private String reviewer;
    private Date reviewDateTime;
    private int numberOfPages;
    private int appointmentNo;
    private String description;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocument;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;

    /**
     * Constructs a new CachedDemographicDocument with default values.
     *
     * <p>Initializes the caisiDemographicId to 0. All other fields are set to their
     * default values (null for objects, 0 for primitives).</p>
     */
    public CachedDemographicDocument() {
        this.caisiDemographicId = 0;
    }

    /**
     * Gets the composite primary key identifier for this document.
     *
     * <p>This method is part of the {@link AbstractModel} contract and returns the
     * composite key consisting of facility ID and an integer document identifier.</p>
     *
     * @return FacilityIdIntegerCompositePk the composite primary key for this entity
     */
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityIntegerPk(this);
    }

    /**
     * Gets the facility-scoped composite primary key.
     *
     * <p>The composite key uniquely identifies this document within a specific healthcare
     * facility in a multi-facility environment.</p>
     *
     * @return FacilityIdIntegerCompositePk the composite primary key combining facility ID and document ID
     */
    public FacilityIdIntegerCompositePk getFacilityIntegerPk() {
        return pcGetfacilityIntegerPk(this);
    }

    /**
     * Sets the facility-scoped composite primary key.
     *
     * @param facilityIntegerPk FacilityIdIntegerCompositePk the composite primary key to set
     */
    public void setFacilityIntegerPk(final FacilityIdIntegerCompositePk facilityIntegerPk) {
        pcSetfacilityIntegerPk(this, facilityIntegerPk);
    }

    /**
     * Gets the CAISI demographic identifier.
     *
     * <p>This ID links the document to a specific patient demographic record in the
     * CAISI system. It is indexed for efficient queries.</p>
     *
     * @return int the CAISI demographic identifier
     */
    public int getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }

    /**
     * Sets the CAISI demographic identifier.
     *
     * @param caisiDemographicId int the CAISI demographic identifier to set
     */
    public void setCaisiDemographicId(final int caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }

    /**
     * Gets the document type classification.
     *
     * <p>Common document types in healthcare EMR systems include lab results, imaging reports,
     * consultation notes, referral letters, consent forms, and administrative documents.</p>
     *
     * @return String the document type, or null if not set
     */
    public String getDocType() {
        return pcGetdocType(this);
    }

    /**
     * Sets the document type classification.
     *
     * <p>The value is trimmed and null-safe using {@link StringUtils#trimToNull(String)}.</p>
     *
     * @param docType String the document type to set
     */
    public void setDocType(final String docType) {
        pcSetdocType(this, StringUtils.trimToNull(docType));
    }

    /**
     * Gets the document content in XML format.
     *
     * <p>This field stores the document data as XML, which may include structured clinical
     * data, HL7 messages, or other XML-based healthcare document formats.</p>
     *
     * @return String the XML document content, or null if not set
     */
    public String getDocXml() {
        return pcGetdocXml(this);
    }

    /**
     * Sets the document content in XML format.
     *
     * <p>The value is trimmed and null-safe using {@link StringUtils#trimToNull(String)}.</p>
     *
     * @param docXml String the XML document content to set
     */
    public void setDocXml(final String docXml) {
        pcSetdocXml(this, StringUtils.trimToNull(docXml));
    }

    /**
     * Gets the document description.
     *
     * <p>Provides a human-readable description or summary of the document content.</p>
     *
     * @return String the document description, or null if not set
     */
    public String getDescription() {
        return pcGetdescription(this);
    }

    /**
     * Sets the document description.
     *
     * <p>The value is trimmed and null-safe using {@link StringUtils#trimToNull(String)}.</p>
     *
     * @param description String the document description to set
     */
    public void setDescription(final String description) {
        pcSetdescription(this, StringUtils.trimToNull(description));
    }

    /**
     * Gets the original filename of the document.
     *
     * <p>Preserves the original filename when documents are uploaded or imported into the system.</p>
     *
     * @return String the document filename, or null if not set
     */
    public String getDocFilename() {
        return pcGetdocFilename(this);
    }

    /**
     * Sets the original filename of the document.
     *
     * <p>The value is trimmed and null-safe using {@link StringUtils#trimToNull(String)}.</p>
     *
     * @param docFilename String the document filename to set
     */
    public void setDocFilename(final String docFilename) {
        pcSetdocFilename(this, StringUtils.trimToNull(docFilename));
    }

    /**
     * Gets the document creator identifier.
     *
     * <p>Identifies the healthcare provider or user who created or uploaded the document.</p>
     *
     * @return String the document creator identifier, or null if not set
     */
    public String getDocCreator() {
        return pcGetdocCreator(this);
    }

    /**
     * Sets the document creator identifier.
     *
     * <p>The value is trimmed and null-safe using {@link StringUtils#trimToNull(String)}.</p>
     *
     * @param docCreator String the document creator identifier to set
     */
    public void setDocCreator(final String docCreator) {
        pcSetdocCreator(this, StringUtils.trimToNull(docCreator));
    }

    /**
     * Gets the identifier of the healthcare provider responsible for this document.
     *
     * <p>This may differ from the creator if the document has been reassigned or if
     * responsibility has been transferred to another provider.</p>
     *
     * @return String the responsible provider identifier, or null if not set
     */
    public String getResponsible() {
        return pcGetresponsible(this);
    }

    /**
     * Sets the identifier of the healthcare provider responsible for this document.
     *
     * <p>The value is trimmed and null-safe using {@link StringUtils#trimToNull(String)}.</p>
     *
     * @param responsible String the responsible provider identifier to set
     */
    public void setResponsible(final String responsible) {
        pcSetresponsible(this, StringUtils.trimToNull(responsible));
    }

    /**
     * Gets the source system or origin of the document.
     *
     * <p>Identifies where the document came from, such as a specific facility, external
     * system, or integration source in a multi-facility healthcare network.</p>
     *
     * @return String the document source identifier, or null if not set
     */
    public String getSource() {
        return pcGetsource(this);
    }

    /**
     * Sets the source system or origin of the document.
     *
     * <p>The value is trimmed and null-safe using {@link StringUtils#trimToNull(String)}.</p>
     *
     * @param source String the document source identifier to set
     */
    public void setSource(final String source) {
        pcSetsource(this, StringUtils.trimToNull(source));
    }

    /**
     * Gets the program identifier for care management association.
     *
     * <p>Links this document to a specific care program (e.g., diabetes management,
     * mental health services, chronic disease management) in the CAISI system.</p>
     *
     * @return Integer the program ID, or null if not associated with a program
     */
    public Integer getProgramId() {
        return pcGetprogramId(this);
    }

    /**
     * Sets the program identifier for care management association.
     *
     * @param programId Integer the program ID to set, or null to remove association
     */
    public void setProgramId(final Integer programId) {
        pcSetprogramId(this, programId);
    }

    /**
     * Gets the last update timestamp for this document.
     *
     * <p>Tracks when the document metadata or content was last modified.</p>
     *
     * @return Date the last update date and time, or null if never updated
     */
    public Date getUpdateDateTime() {
        return pcGetupdateDateTime(this);
    }

    /**
     * Sets the last update timestamp for this document.
     *
     * @param updateDateTime Date the last update date and time to set
     */
    public void setUpdateDateTime(final Date updateDateTime) {
        pcSetupdateDateTime(this, updateDateTime);
    }

    /**
     * Gets the document status.
     *
     * <p>Common statuses include active, archived, deleted, pending review, or approved.
     * Status values control document visibility and workflow processing.</p>
     *
     * @return String the document status, or null if not set
     */
    public String getStatus() {
        return pcGetstatus(this);
    }

    /**
     * Sets the document status.
     *
     * <p>The value is trimmed and null-safe using {@link StringUtils#trimToNull(String)}.</p>
     *
     * @param status String the document status to set
     */
    public void setStatus(final String status) {
        pcSetstatus(this, StringUtils.trimToNull(status));
    }

    /**
     * Gets the MIME content type of the document.
     *
     * <p>Examples include application/pdf, image/jpeg, text/html, or application/xml.
     * This helps determine how to display or process the document content.</p>
     *
     * @return String the MIME content type, or null if not set
     */
    public String getContentType() {
        return pcGetcontentType(this);
    }

    /**
     * Sets the MIME content type of the document.
     *
     * <p>The value is trimmed and null-safe using {@link StringUtils#trimToNull(String)}.</p>
     *
     * @param contentType String the MIME content type to set
     */
    public void setContentType(final String contentType) {
        pcSetcontentType(this, StringUtils.trimToNull(contentType));
    }

    /**
     * Gets the public visibility flag.
     *
     * <p>Controls whether the document is visible to the patient or restricted to
     * healthcare providers only. Typically 1 for public/patient-visible, 0 for private.</p>
     *
     * @return int the public visibility flag
     */
    public int getPublic1() {
        return pcGetpublic1(this);
    }

    /**
     * Sets the public visibility flag.
     *
     * @param public1 int the public visibility flag to set (typically 0 or 1)
     */
    public void setPublic1(final int public1) {
        pcSetpublic1(this, public1);
    }

    /**
     * Gets the clinical observation date for the document.
     *
     * <p>For lab results or diagnostic reports, this is the date the test was performed
     * or the observation was made, which may differ from the document creation date.</p>
     *
     * @return Date the observation date, or null if not applicable
     */
    public Date getObservationDate() {
        return pcGetobservationDate(this);
    }

    /**
     * Sets the clinical observation date for the document.
     *
     * @param observationDate Date the observation date to set
     */
    public void setObservationDate(final Date observationDate) {
        pcSetobservationDate(this, observationDate);
    }

    /**
     * Gets the identifier of the healthcare provider who reviewed this document.
     *
     * <p>Part of the document review workflow to track which provider has reviewed
     * and acknowledged the document, particularly important for lab results and
     * diagnostic reports requiring clinical review.</p>
     *
     * @return String the reviewer identifier, or null if not yet reviewed
     */
    public String getReviewer() {
        return pcGetreviewer(this);
    }

    /**
     * Sets the identifier of the healthcare provider who reviewed this document.
     *
     * <p>The value is trimmed and null-safe using {@link StringUtils#trimToNull(String)}.</p>
     *
     * @param reviewer String the reviewer identifier to set
     */
    public void setReviewer(final String reviewer) {
        pcSetreviewer(this, StringUtils.trimToNull(reviewer));
    }

    /**
     * Gets the timestamp when this document was reviewed.
     *
     * <p>Records when a healthcare provider reviewed and acknowledged the document.</p>
     *
     * @return Date the review date and time, or null if not yet reviewed
     */
    public Date getReviewDateTime() {
        return pcGetreviewDateTime(this);
    }

    /**
     * Sets the timestamp when this document was reviewed.
     *
     * @param reviewDateTime Date the review date and time to set
     */
    public void setReviewDateTime(final Date reviewDateTime) {
        pcSetreviewDateTime(this, reviewDateTime);
    }

    /**
     * Gets the number of pages in the document.
     *
     * <p>For multi-page documents such as PDFs or scanned images, tracks the total
     * page count for display and navigation purposes.</p>
     *
     * @return int the number of pages (0 if not applicable or not set)
     */
    public int getNumberOfPages() {
        return pcGetnumberOfPages(this);
    }

    /**
     * Sets the number of pages in the document.
     *
     * @param numberOfPages int the number of pages to set
     */
    public void setNumberOfPages(final int numberOfPages) {
        pcSetnumberOfPages(this, numberOfPages);
    }

    /**
     * Gets the appointment number associated with this document.
     *
     * <p>Links the document to a specific patient appointment, providing clinical context
     * for documents generated or received during an appointment visit.</p>
     *
     * @return int the appointment number (0 if not associated with an appointment)
     */
    public int getAppointmentNo() {
        return pcGetappointmentNo(this);
    }

    /**
     * Sets the appointment number associated with this document.
     *
     * @param appointmentNo int the appointment number to set
     */
    public void setAppointmentNo(final int appointmentNo) {
        pcSetappointmentNo(this, appointmentNo);
    }

    /**
     * Gets the OpenJPA enhancement contract version.
     *
     * <p>This method is part of the {@link PersistenceCapable} interface and returns
     * the version of the bytecode enhancement contract implemented by this class.</p>
     *
     * @return int the enhancement contract version (currently 2)
     */
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 743919396538312571L;
        CachedDemographicDocument.pcFieldNames = new String[] { "appointmentNo", "caisiDemographicId", "contentType", "description", "docCreator", "docFilename", "docType", "docXml", "facilityIntegerPk", "numberOfPages", "observationDate", "programId", "public1", "responsible", "reviewDateTime", "reviewer", "source", "status", "updateDateTime" };
        CachedDemographicDocument.pcFieldTypes = new Class[] { Integer.TYPE, Integer.TYPE, (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), Integer.TYPE, (CachedDemographicDocument.class$Ljava$util$Date != null) ? CachedDemographicDocument.class$Ljava$util$Date : (CachedDemographicDocument.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographicDocument.class$Ljava$lang$Integer != null) ? CachedDemographicDocument.class$Ljava$lang$Integer : (CachedDemographicDocument.class$Ljava$lang$Integer = class$("java.lang.Integer")), Integer.TYPE, (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$util$Date != null) ? CachedDemographicDocument.class$Ljava$util$Date : (CachedDemographicDocument.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$util$Date != null) ? CachedDemographicDocument.class$Ljava$util$Date : (CachedDemographicDocument.class$Ljava$util$Date = class$("java.util.Date")) };
        CachedDemographicDocument.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocument != null) ? CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocument : (CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocument = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicDocument")), CachedDemographicDocument.pcFieldNames, CachedDemographicDocument.pcFieldTypes, CachedDemographicDocument.pcFieldFlags, CachedDemographicDocument.pcPCSuperclass, "CachedDemographicDocument", (PersistenceCapable)new CachedDemographicDocument());
    }
    
    /**
     * Helper method to load a class by name.
     *
     * <p>This synthetic method is generated by the compiler to support class literal
     * loading in the static initializer block.</p>
     *
     * @param className String the fully qualified class name to load
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
     * Clears all persistent fields to their default values.
     *
     * <p>This method is part of the OpenJPA persistence lifecycle and is called during
     * entity initialization and state management operations.</p>
     */
    protected void pcClearFields() {
        this.appointmentNo = 0;
        this.caisiDemographicId = 0;
        this.contentType = null;
        this.description = null;
        this.docCreator = null;
        this.docFilename = null;
        this.docType = null;
        this.docXml = null;
        this.facilityIntegerPk = null;
        this.numberOfPages = 0;
        this.observationDate = null;
        this.programId = null;
        this.public1 = 0;
        this.responsible = null;
        this.reviewDateTime = null;
        this.reviewer = null;
        this.source = null;
        this.status = null;
        this.updateDateTime = null;
    }

    /**
     * Creates a new instance with state manager and object ID.
     *
     * <p>This method is part of the {@link PersistenceCapable} interface and is used by
     * OpenJPA to create new instances during entity lifecycle management operations.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param o Object the object ID containing key field values
     * @param b boolean whether to clear all fields after initialization
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicDocument cachedDemographicDocument = new CachedDemographicDocument();
        if (b) {
            cachedDemographicDocument.pcClearFields();
        }
        cachedDemographicDocument.pcStateManager = pcStateManager;
        cachedDemographicDocument.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicDocument;
    }

    /**
     * Creates a new instance with state manager.
     *
     * <p>This method is part of the {@link PersistenceCapable} interface and is used by
     * OpenJPA to create new instances during entity lifecycle management operations.</p>
     *
     * @param pcStateManager StateManager the state manager to associate with the new instance
     * @param b boolean whether to clear all fields after initialization
     * @return PersistenceCapable the newly created instance
     */
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicDocument cachedDemographicDocument = new CachedDemographicDocument();
        if (b) {
            cachedDemographicDocument.pcClearFields();
        }
        cachedDemographicDocument.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicDocument;
    }

    /**
     * Gets the count of managed persistent fields.
     *
     * <p>This entity has 19 managed fields tracked by the OpenJPA state manager.</p>
     *
     * @return int the number of managed fields (19)
     */
    protected static int pcGetManagedFieldCount() {
        return 19;
    }

    /**
     * Replaces a single field value from the state manager.
     *
     * <p>This method is part of the {@link PersistenceCapable} interface and is called by
     * OpenJPA to restore field values during state management operations such as rollback,
     * refresh, or detachment/reattachment.</p>
     *
     * @param n int the field index to replace
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicDocument.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.appointmentNo = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.caisiDemographicId = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.contentType = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.description = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.docCreator = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.docFilename = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.docType = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.docXml = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.facilityIntegerPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.numberOfPages = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 10: {
                this.observationDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 11: {
                this.programId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 12: {
                this.public1 = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 13: {
                this.responsible = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 14: {
                this.reviewDateTime = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 15: {
                this.reviewer = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 16: {
                this.source = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 17: {
                this.status = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 18: {
                this.updateDateTime = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Replaces multiple field values from the state manager.
     *
     * <p>This method is part of the {@link PersistenceCapable} interface and is called by
     * OpenJPA to restore multiple field values efficiently during state management operations.</p>
     *
     * @param array int[] array of field indices to replace
     */
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }

    /**
     * Provides a single field value to the state manager.
     *
     * <p>This method is part of the {@link PersistenceCapable} interface and is called by
     * OpenJPA to read field values during state management operations such as flush,
     * detachment, or serialization.</p>
     *
     * @param n int the field index to provide
     * @throws IllegalArgumentException if the field index is invalid
     */
    public void pcProvideField(final int n) {
        final int n2 = n - CachedDemographicDocument.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.appointmentNo);
                return;
            }
            case 1: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.caisiDemographicId);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.contentType);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.description);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.docCreator);
                return;
            }
            case 5: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.docFilename);
                return;
            }
            case 6: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.docType);
                return;
            }
            case 7: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.docXml);
                return;
            }
            case 8: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityIntegerPk);
                return;
            }
            case 9: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.numberOfPages);
                return;
            }
            case 10: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.observationDate);
                return;
            }
            case 11: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.programId);
                return;
            }
            case 12: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.public1);
                return;
            }
            case 13: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.responsible);
                return;
            }
            case 14: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.reviewDateTime);
                return;
            }
            case 15: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.reviewer);
                return;
            }
            case 16: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.source);
                return;
            }
            case 17: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.status);
                return;
            }
            case 18: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.updateDateTime);
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
     * <p>This method is part of the {@link PersistenceCapable} interface and is called by
     * OpenJPA to read multiple field values efficiently during state management operations.</p>
     *
     * @param array int[] array of field indices to provide
     */
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }

    /**
     * Copies a single field value from another instance.
     *
     * <p>This method is used during entity merge and copy operations to transfer field
     * values between instances of the same entity type.</p>
     *
     * @param cachedDemographicDocument CachedDemographicDocument the source instance to copy from
     * @param n int the field index to copy
     * @throws IllegalArgumentException if the field index is invalid
     */
    protected void pcCopyField(final CachedDemographicDocument cachedDemographicDocument, final int n) {
        final int n2 = n - CachedDemographicDocument.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.appointmentNo = cachedDemographicDocument.appointmentNo;
                return;
            }
            case 1: {
                this.caisiDemographicId = cachedDemographicDocument.caisiDemographicId;
                return;
            }
            case 2: {
                this.contentType = cachedDemographicDocument.contentType;
                return;
            }
            case 3: {
                this.description = cachedDemographicDocument.description;
                return;
            }
            case 4: {
                this.docCreator = cachedDemographicDocument.docCreator;
                return;
            }
            case 5: {
                this.docFilename = cachedDemographicDocument.docFilename;
                return;
            }
            case 6: {
                this.docType = cachedDemographicDocument.docType;
                return;
            }
            case 7: {
                this.docXml = cachedDemographicDocument.docXml;
                return;
            }
            case 8: {
                this.facilityIntegerPk = cachedDemographicDocument.facilityIntegerPk;
                return;
            }
            case 9: {
                this.numberOfPages = cachedDemographicDocument.numberOfPages;
                return;
            }
            case 10: {
                this.observationDate = cachedDemographicDocument.observationDate;
                return;
            }
            case 11: {
                this.programId = cachedDemographicDocument.programId;
                return;
            }
            case 12: {
                this.public1 = cachedDemographicDocument.public1;
                return;
            }
            case 13: {
                this.responsible = cachedDemographicDocument.responsible;
                return;
            }
            case 14: {
                this.reviewDateTime = cachedDemographicDocument.reviewDateTime;
                return;
            }
            case 15: {
                this.reviewer = cachedDemographicDocument.reviewer;
                return;
            }
            case 16: {
                this.source = cachedDemographicDocument.source;
                return;
            }
            case 17: {
                this.status = cachedDemographicDocument.status;
                return;
            }
            case 18: {
                this.updateDateTime = cachedDemographicDocument.updateDateTime;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Copies multiple field values from another instance.
     *
     * <p>This method is part of the {@link PersistenceCapable} interface and is used during
     * entity merge and copy operations to efficiently transfer multiple field values.</p>
     *
     * @param o Object the source instance to copy from (must be CachedDemographicDocument)
     * @param array int[] array of field indices to copy
     * @throws IllegalArgumentException if the source instance has a different state manager
     * @throws IllegalStateException if this instance has no state manager
     */
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicDocument cachedDemographicDocument = (CachedDemographicDocument)o;
        if (cachedDemographicDocument.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicDocument, array[i]);
        }
    }

    /**
     * Gets the generic context from the state manager.
     *
     * @return Object the generic context, or null if no state manager is assigned
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
     * @return Object the object ID, or null if no state manager is assigned
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
     * @return boolean true if the instance is deleted, false otherwise
     */
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }

    /**
     * Checks if this instance has been modified.
     *
     * <p>Performs a dirty check to determine if any field values have changed since
     * the instance was loaded or last persisted.</p>
     *
     * @return boolean true if the instance has uncommitted changes, false otherwise
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
     * @return boolean true if the instance is new, false otherwise
     */
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }

    /**
     * Checks if this instance is managed by the persistence context.
     *
     * @return boolean true if the instance is persistent, false otherwise
     */
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }

    /**
     * Checks if this instance is transactional.
     *
     * @return boolean true if the instance participates in a transaction, false otherwise
     */
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }

    /**
     * Checks if this instance is currently being serialized.
     *
     * @return boolean true if serialization is in progress, false otherwise
     */
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }

    /**
     * Marks a specific field as dirty.
     *
     * <p>Notifies the state manager that the specified field has been modified and
     * should be included in the next database update.</p>
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
     * Gets the OpenJPA state manager for this instance.
     *
     * @return StateManager the state manager, or null if not managed
     */
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }

    /**
     * Gets the version information for optimistic locking.
     *
     * @return Object the version object, or null if no state manager or versioning is not used
     */
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }

    /**
     * Replaces the state manager with a new one.
     *
     * <p>This method is used during entity state transitions and detachment/reattachment
     * operations.</p>
     *
     * @param pcStateManager StateManager the new state manager to assign
     * @throws SecurityException if the state manager replacement is not allowed
     */
    public void pcReplaceStateManager(final StateManager pcStateManager) throws SecurityException {
        if (this.pcStateManager != null) {
            this.pcStateManager = this.pcStateManager.replaceStateManager(pcStateManager);
            return;
        }
        this.pcStateManager = pcStateManager;
    }

    /**
     * Copies key fields to object ID using field supplier.
     *
     * <p>This operation is not supported for this entity type.</p>
     *
     * @param fieldSupplier FieldSupplier the field supplier
     * @param o Object the object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields to object ID.
     *
     * <p>This operation is not supported for this entity type.</p>
     *
     * @param o Object the object ID
     * @throws InternalException always thrown as this operation is not supported
     */
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }

    /**
     * Copies key fields from object ID using field consumer.
     *
     * <p>Extracts the composite primary key from the object ID and stores it in the
     * field consumer for persistence operations.</p>
     *
     * @param fieldConsumer FieldConsumer the field consumer to receive the key field
     * @param o Object the object ID containing the key
     */
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(8 + CachedDemographicDocument.pcInheritedFieldCount, ((ObjectId)o).getId());
    }

    /**
     * Copies key fields from object ID.
     *
     * <p>Extracts the composite primary key from the object ID and assigns it to the
     * facilityIntegerPk field.</p>
     *
     * @param o Object the object ID containing the key
     */
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityIntegerPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }

    /**
     * Creates a new object ID instance from a string representation.
     *
     * <p>This operation is not supported for this entity type as it uses a composite key.</p>
     *
     * @param o Object the string representation
     * @return Object the new object ID
     * @throws IllegalArgumentException always thrown as this operation is not supported for composite keys
     */
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicDocument\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }

    /**
     * Creates a new object ID instance for this entity.
     *
     * <p>Constructs an OpenJPA {@link ObjectId} using the current composite primary key.</p>
     *
     * @return Object the new object ID wrapping the facilityIntegerPk
     */
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocument != null) ? CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocument : (CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocument = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicDocument")), (Object)this.facilityIntegerPk);
    }
    
    private static final int pcGetappointmentNo(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.appointmentNo;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 0);
        return cachedDemographicDocument.appointmentNo;
    }
    
    private static final void pcSetappointmentNo(final CachedDemographicDocument cachedDemographicDocument, final int appointmentNo) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.appointmentNo = appointmentNo;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 0, cachedDemographicDocument.appointmentNo, appointmentNo, 0);
    }
    
    private static final int pcGetcaisiDemographicId(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.caisiDemographicId;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 1);
        return cachedDemographicDocument.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedDemographicDocument cachedDemographicDocument, final int caisiDemographicId) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 1, cachedDemographicDocument.caisiDemographicId, caisiDemographicId, 0);
    }
    
    private static final String pcGetcontentType(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.contentType;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 2);
        return cachedDemographicDocument.contentType;
    }
    
    private static final void pcSetcontentType(final CachedDemographicDocument cachedDemographicDocument, final String contentType) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.contentType = contentType;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 2, cachedDemographicDocument.contentType, contentType, 0);
    }
    
    private static final String pcGetdescription(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.description;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 3);
        return cachedDemographicDocument.description;
    }
    
    private static final void pcSetdescription(final CachedDemographicDocument cachedDemographicDocument, final String description) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.description = description;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 3, cachedDemographicDocument.description, description, 0);
    }
    
    private static final String pcGetdocCreator(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.docCreator;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 4);
        return cachedDemographicDocument.docCreator;
    }
    
    private static final void pcSetdocCreator(final CachedDemographicDocument cachedDemographicDocument, final String docCreator) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.docCreator = docCreator;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 4, cachedDemographicDocument.docCreator, docCreator, 0);
    }
    
    private static final String pcGetdocFilename(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.docFilename;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 5);
        return cachedDemographicDocument.docFilename;
    }
    
    private static final void pcSetdocFilename(final CachedDemographicDocument cachedDemographicDocument, final String docFilename) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.docFilename = docFilename;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 5, cachedDemographicDocument.docFilename, docFilename, 0);
    }
    
    private static final String pcGetdocType(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.docType;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 6);
        return cachedDemographicDocument.docType;
    }
    
    private static final void pcSetdocType(final CachedDemographicDocument cachedDemographicDocument, final String docType) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.docType = docType;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 6, cachedDemographicDocument.docType, docType, 0);
    }
    
    private static final String pcGetdocXml(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.docXml;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 7);
        return cachedDemographicDocument.docXml;
    }
    
    private static final void pcSetdocXml(final CachedDemographicDocument cachedDemographicDocument, final String docXml) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.docXml = docXml;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 7, cachedDemographicDocument.docXml, docXml, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityIntegerPk(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.facilityIntegerPk;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 8);
        return cachedDemographicDocument.facilityIntegerPk;
    }
    
    private static final void pcSetfacilityIntegerPk(final CachedDemographicDocument cachedDemographicDocument, final FacilityIdIntegerCompositePk facilityIntegerPk) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.facilityIntegerPk = facilityIntegerPk;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 8, (Object)cachedDemographicDocument.facilityIntegerPk, (Object)facilityIntegerPk, 0);
    }
    
    private static final int pcGetnumberOfPages(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.numberOfPages;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 9);
        return cachedDemographicDocument.numberOfPages;
    }
    
    private static final void pcSetnumberOfPages(final CachedDemographicDocument cachedDemographicDocument, final int numberOfPages) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.numberOfPages = numberOfPages;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 9, cachedDemographicDocument.numberOfPages, numberOfPages, 0);
    }
    
    private static final Date pcGetobservationDate(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.observationDate;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 10);
        return cachedDemographicDocument.observationDate;
    }
    
    private static final void pcSetobservationDate(final CachedDemographicDocument cachedDemographicDocument, final Date observationDate) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.observationDate = observationDate;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 10, (Object)cachedDemographicDocument.observationDate, (Object)observationDate, 0);
    }
    
    private static final Integer pcGetprogramId(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.programId;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 11);
        return cachedDemographicDocument.programId;
    }
    
    private static final void pcSetprogramId(final CachedDemographicDocument cachedDemographicDocument, final Integer programId) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.programId = programId;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 11, (Object)cachedDemographicDocument.programId, (Object)programId, 0);
    }
    
    private static final int pcGetpublic1(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.public1;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 12);
        return cachedDemographicDocument.public1;
    }
    
    private static final void pcSetpublic1(final CachedDemographicDocument cachedDemographicDocument, final int public1) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.public1 = public1;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 12, cachedDemographicDocument.public1, public1, 0);
    }
    
    private static final String pcGetresponsible(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.responsible;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 13);
        return cachedDemographicDocument.responsible;
    }
    
    private static final void pcSetresponsible(final CachedDemographicDocument cachedDemographicDocument, final String responsible) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.responsible = responsible;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 13, cachedDemographicDocument.responsible, responsible, 0);
    }
    
    private static final Date pcGetreviewDateTime(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.reviewDateTime;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 14);
        return cachedDemographicDocument.reviewDateTime;
    }
    
    private static final void pcSetreviewDateTime(final CachedDemographicDocument cachedDemographicDocument, final Date reviewDateTime) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.reviewDateTime = reviewDateTime;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 14, (Object)cachedDemographicDocument.reviewDateTime, (Object)reviewDateTime, 0);
    }
    
    private static final String pcGetreviewer(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.reviewer;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 15);
        return cachedDemographicDocument.reviewer;
    }
    
    private static final void pcSetreviewer(final CachedDemographicDocument cachedDemographicDocument, final String reviewer) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.reviewer = reviewer;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 15, cachedDemographicDocument.reviewer, reviewer, 0);
    }
    
    private static final String pcGetsource(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.source;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 16);
        return cachedDemographicDocument.source;
    }
    
    private static final void pcSetsource(final CachedDemographicDocument cachedDemographicDocument, final String source) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.source = source;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 16, cachedDemographicDocument.source, source, 0);
    }
    
    private static final String pcGetstatus(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.status;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 17);
        return cachedDemographicDocument.status;
    }
    
    private static final void pcSetstatus(final CachedDemographicDocument cachedDemographicDocument, final String status) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.status = status;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 17, cachedDemographicDocument.status, status, 0);
    }
    
    private static final Date pcGetupdateDateTime(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.updateDateTime;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 18);
        return cachedDemographicDocument.updateDateTime;
    }
    
    private static final void pcSetupdateDateTime(final CachedDemographicDocument cachedDemographicDocument, final Date updateDateTime) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.updateDateTime = updateDateTime;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 18, (Object)cachedDemographicDocument.updateDateTime, (Object)updateDateTime, 0);
    }

    /**
     * Checks if this instance is detached from the persistence context.
     *
     * <p>A detached entity is one that was previously managed but is no longer associated
     * with an active persistence context. This can occur after serialization, transaction
     * completion with detach-on-close, or explicit detachment.</p>
     *
     * @return Boolean true if definitely detached, false if definitely attached, or null if uncertain
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
     * Checks if the detached state is definitive.
     *
     * <p>Returns false to indicate that detached state detection may not be completely
     * reliable without a state manager present.</p>
     *
     * @return boolean false indicating detached state is not definitive
     */
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }

    /**
     * Gets the detached state information.
     *
     * <p>The detached state is used by OpenJPA to track entity version and change information
     * when the entity is not actively managed by a persistence context.</p>
     *
     * @return Object the detached state, or null if not detached
     */
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }

    /**
     * Sets the detached state information.
     *
     * @param pcDetachedState Object the detached state to set
     */
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }

    /**
     * Custom serialization method.
     *
     * <p>Handles proper serialization of the entity, clearing the detached state if
     * serialization is happening within a managed context.</p>
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
     * Custom deserialization method.
     *
     * <p>Handles proper deserialization of the entity, marking it as deserialized to
     * indicate it needs to be reattached to a persistence context before use.</p>
     *
     * @param objectInputStream ObjectInputStream the stream to read from
     * @throws IOException if an I/O error occurs during deserialization
     * @throws ClassNotFoundException if a required class cannot be found during deserialization
     */
    private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        this.pcSetDetachedState(PersistenceCapable.DESERIALIZED);
        objectInputStream.defaultReadObject();
    }
}
