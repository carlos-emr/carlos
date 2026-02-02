package ca.openosp.openo.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlSchemaType;
import org.w3._2001.xmlschema.Adapter1;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.annotation.XmlElement;
import java.util.Calendar;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

/**
 * Represents a cached demographic document in the CAISI Integrator system.
 *
 * <p>This class is a JAXB-annotated data transfer object used for web service communication
 * in the CAISI (Client Access to Integrated Services and Information) Integrator module.
 * It encapsulates patient demographic documents with metadata including appointment associations,
 * document content, review status, and temporal tracking information.</p>
 *
 * <p>The cached document includes comprehensive metadata for healthcare document management:
 * <ul>
 *   <li>Document identification (filename, type, creator)</li>
 *   <li>Content and format information (contentType, docXml, numberOfPages)</li>
 *   <li>Healthcare context (appointmentNo, programId, caisiDemographicId)</li>
 *   <li>Workflow tracking (status, reviewer, reviewDateTime)</li>
 *   <li>Access control (responsible provider, public visibility flag)</li>
 *   <li>Temporal metadata (observationDate, updateDateTime)</li>
 *   <li>Multi-facility support via composite primary key</li>
 * </ul>
 * </p>
 *
 * <p>The class is configured for JAXB XML serialization with field-based access and
 * defined property ordering for consistent XML output in inter-EMR communication.</p>
 *
 * @see AbstractModel
 * @see FacilityIdIntegerCompositePk
 * @since 2026-01-23
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cachedDemographicDocument", propOrder = { "appointmentNo", "caisiDemographicId", "contentType", "description", "docCreator", "docFilename", "docType", "docXml", "facilityIntegerPk", "numberOfPages", "observationDate", "programId", "public1", "responsible", "reviewDateTime", "reviewer", "source", "status", "updateDateTime" })
public class CachedDemographicDocument extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected int appointmentNo;
    protected int caisiDemographicId;
    protected String contentType;
    protected String description;
    protected String docCreator;
    protected String docFilename;
    protected String docType;
    protected String docXml;
    protected FacilityIdIntegerCompositePk facilityIntegerPk;
    protected int numberOfPages;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar observationDate;
    protected Integer programId;
    protected int public1;
    protected String responsible;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar reviewDateTime;
    protected String reviewer;
    protected String source;
    protected String status;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar updateDateTime;

    /**
     * Gets the appointment number associated with this document.
     *
     * @return int the appointment identifier
     */
    public int getAppointmentNo() {
        return this.appointmentNo;
    }

    /**
     * Sets the appointment number for this document.
     *
     * @param appointmentNo int the appointment identifier to associate with this document
     */
    public void setAppointmentNo(final int appointmentNo) {
        this.appointmentNo = appointmentNo;
    }

    /**
     * Gets the CAISI demographic identifier for the patient.
     *
     * @return int the CAISI-specific demographic ID
     */
    public int getCaisiDemographicId() {
        return this.caisiDemographicId;
    }

    /**
     * Sets the CAISI demographic identifier for the patient.
     *
     * @param caisiDemographicId int the CAISI-specific demographic ID
     */
    public void setCaisiDemographicId(final int caisiDemographicId) {
        this.caisiDemographicId = caisiDemographicId;
    }

    /**
     * Gets the MIME content type of the document.
     *
     * @return String the document content type (e.g., "application/pdf", "text/html")
     */
    public String getContentType() {
        return this.contentType;
    }

    /**
     * Sets the MIME content type of the document.
     *
     * @param contentType String the document content type to set
     */
    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    /**
     * Gets the human-readable description of the document.
     *
     * @return String the document description
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Sets the human-readable description of the document.
     *
     * @param description String the document description to set
     */
    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     * Gets the identifier of the provider or user who created the document.
     *
     * @return String the document creator identifier
     */
    public String getDocCreator() {
        return this.docCreator;
    }

    /**
     * Sets the identifier of the provider or user who created the document.
     *
     * @param docCreator String the document creator identifier to set
     */
    public void setDocCreator(final String docCreator) {
        this.docCreator = docCreator;
    }

    /**
     * Gets the filename of the stored document.
     *
     * @return String the document filename
     */
    public String getDocFilename() {
        return this.docFilename;
    }

    /**
     * Sets the filename of the stored document.
     *
     * @param docFilename String the document filename to set
     */
    public void setDocFilename(final String docFilename) {
        this.docFilename = docFilename;
    }

    /**
     * Gets the document type classification.
     *
     * @return String the document type (e.g., "lab report", "consultation", "imaging")
     */
    public String getDocType() {
        return this.docType;
    }

    /**
     * Sets the document type classification.
     *
     * @param docType String the document type to set
     */
    public void setDocType(final String docType) {
        this.docType = docType;
    }

    /**
     * Gets the XML representation of the document content.
     *
     * @return String the document content in XML format
     */
    public String getDocXml() {
        return this.docXml;
    }

    /**
     * Sets the XML representation of the document content.
     *
     * @param docXml String the document content in XML format to set
     */
    public void setDocXml(final String docXml) {
        this.docXml = docXml;
    }

    /**
     * Gets the composite primary key containing facility and item identifiers.
     *
     * @return FacilityIdIntegerCompositePk the facility-item composite key
     */
    public FacilityIdIntegerCompositePk getFacilityIntegerPk() {
        return this.facilityIntegerPk;
    }

    /**
     * Sets the composite primary key containing facility and item identifiers.
     *
     * @param facilityIntegerPk FacilityIdIntegerCompositePk the facility-item composite key to set
     */
    public void setFacilityIntegerPk(final FacilityIdIntegerCompositePk facilityIntegerPk) {
        this.facilityIntegerPk = facilityIntegerPk;
    }

    /**
     * Gets the total number of pages in the document.
     *
     * @return int the page count
     */
    public int getNumberOfPages() {
        return this.numberOfPages;
    }

    /**
     * Sets the total number of pages in the document.
     *
     * @param numberOfPages int the page count to set
     */
    public void setNumberOfPages(final int numberOfPages) {
        this.numberOfPages = numberOfPages;
    }

    /**
     * Gets the date and time when the clinical observation occurred.
     *
     * @return Calendar the observation date and time
     */
    public Calendar getObservationDate() {
        return this.observationDate;
    }

    /**
     * Sets the date and time when the clinical observation occurred.
     *
     * @param observationDate Calendar the observation date and time to set
     */
    public void setObservationDate(final Calendar observationDate) {
        this.observationDate = observationDate;
    }

    /**
     * Gets the program identifier associated with this document.
     *
     * @return Integer the program ID, or null if not associated with a program
     */
    public Integer getProgramId() {
        return this.programId;
    }

    /**
     * Sets the program identifier associated with this document.
     *
     * @param programId Integer the program ID to set, or null to disassociate
     */
    public void setProgramId(final Integer programId) {
        this.programId = programId;
    }

    /**
     * Gets the public visibility flag for the document.
     *
     * @return int the public flag (1 for public, 0 for private)
     */
    public int getPublic1() {
        return this.public1;
    }

    /**
     * Sets the public visibility flag for the document.
     *
     * @param public1 int the public flag to set (1 for public, 0 for private)
     */
    public void setPublic1(final int public1) {
        this.public1 = public1;
    }

    /**
     * Gets the identifier of the provider responsible for this document.
     *
     * @return String the responsible provider identifier
     */
    public String getResponsible() {
        return this.responsible;
    }

    /**
     * Sets the identifier of the provider responsible for this document.
     *
     * @param responsible String the responsible provider identifier to set
     */
    public void setResponsible(final String responsible) {
        this.responsible = responsible;
    }

    /**
     * Gets the date and time when the document was reviewed.
     *
     * @return Calendar the review date and time, or null if not yet reviewed
     */
    public Calendar getReviewDateTime() {
        return this.reviewDateTime;
    }

    /**
     * Sets the date and time when the document was reviewed.
     *
     * @param reviewDateTime Calendar the review date and time to set
     */
    public void setReviewDateTime(final Calendar reviewDateTime) {
        this.reviewDateTime = reviewDateTime;
    }

    /**
     * Gets the identifier of the provider who reviewed the document.
     *
     * @return String the reviewer identifier, or null if not yet reviewed
     */
    public String getReviewer() {
        return this.reviewer;
    }

    /**
     * Sets the identifier of the provider who reviewed the document.
     *
     * @param reviewer String the reviewer identifier to set
     */
    public void setReviewer(final String reviewer) {
        this.reviewer = reviewer;
    }

    /**
     * Gets the source system or origin of the document.
     *
     * @return String the document source identifier
     */
    public String getSource() {
        return this.source;
    }

    /**
     * Sets the source system or origin of the document.
     *
     * @param source String the document source identifier to set
     */
    public void setSource(final String source) {
        this.source = source;
    }

    /**
     * Gets the current workflow status of the document.
     *
     * @return String the document status (e.g., "active", "archived", "pending review")
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * Sets the current workflow status of the document.
     *
     * @param status String the document status to set
     */
    public void setStatus(final String status) {
        this.status = status;
    }

    /**
     * Gets the date and time when the document was last updated.
     *
     * @return Calendar the last update date and time
     */
    public Calendar getUpdateDateTime() {
        return this.updateDateTime;
    }

    /**
     * Sets the date and time when the document was last updated.
     *
     * @param updateDateTime Calendar the last update date and time to set
     */
    public void setUpdateDateTime(final Calendar updateDateTime) {
        this.updateDateTime = updateDateTime;
    }
}
