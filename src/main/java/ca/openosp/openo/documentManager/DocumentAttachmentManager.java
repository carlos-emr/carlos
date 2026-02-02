//CHECKSTYLE:OFF
package ca.openosp.openo.documentManager;

import ca.openosp.openo.commn.model.EFormData;
import ca.openosp.openo.documentManager.data.AttachmentLabResultData;
import ca.openosp.openo.commn.model.enumerator.DocumentType;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.PDFGenerationException;

import ca.openosp.openo.encounter.data.EctFormData;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages document attachments within the OpenO EMR healthcare system.
 *
 * <p>This interface provides comprehensive functionality for managing medical document attachments
 * across various healthcare contexts including consultations, eForms, laboratory results, and
 * clinical encounters. It supports attachment operations, PDF generation, rendering, and
 * integration with external systems such as OceanMD.</p>
 *
 * <p>Key capabilities include:</p>
 * <ul>
 *   <li>Retrieving and managing attachments for consultations and eForms</li>
 *   <li>Handling laboratory results with version sorting for the attachment workflow</li>
 *   <li>PDF document concatenation and rendering for multiple document types</li>
 *   <li>Integration with e-referral systems (OceanMD) for automatic attachment synchronization</li>
 *   <li>Converting eForms to electronic documents (eDocs) for archival purposes</li>
 *   <li>Base64 encoding of PDFs for data transmission and storage</li>
 * </ul>
 *
 * <p>This interface is central to OpenO EMR's document management workflow and ensures
 * proper handling of patient health information (PHI) in compliance with HIPAA/PIPEDA
 * regulatory requirements.</p>
 *
 * @since 2026-01-24
 * @see DocumentType
 * @see LoggedInInfo
 * @see EFormData
 * @see AttachmentLabResultData
 * @see PDFGenerationException
 */
public interface DocumentAttachmentManager {

    /**
     * Retrieves all attachments associated with a specific consultation request.
     *
     * <p>This method returns a list of document identifiers that are currently attached to
     * a consultation request. The attachments can be of various types including eForms,
     * laboratory results, clinical documents, and other medical records.</p>
     *
     * @param loggedInInfo LoggedInInfo the current user's session information for security and audit purposes
     * @param requestId Integer the unique identifier of the consultation request
     * @param documentType DocumentType the type of documents to retrieve
     * @param demographicNo Integer the patient's unique demographic identifier
     * @return List&lt;String&gt; list of document identifiers attached to the consultation
     */
    public List<String> getConsultAttachments(LoggedInInfo loggedInInfo, Integer requestId, DocumentType documentType, Integer demographicNo);

    /**
     * Retrieves all attachments associated with a specific eForm.
     *
     * <p>This method returns a list of document identifiers that are currently attached to
     * an electronic form (eForm). The attachments can include various document types such as
     * clinical documents, laboratory results, imaging reports, and other medical records.</p>
     *
     * @param loggedInInfo LoggedInInfo the current user's session information for security and audit purposes
     * @param fdid Integer the unique identifier of the eForm (form data ID)
     * @param documentType DocumentType the type of documents to retrieve
     * @param demographicNo Integer the patient's unique demographic identifier
     * @return List&lt;String&gt; list of document identifiers attached to the eForm
     */
    public List<String> getEFormAttachments(LoggedInInfo loggedInInfo, Integer fdid, DocumentType documentType, Integer demographicNo);

    /**
     * Retrieves clinical encounter forms that are attached to a specific eForm.
     *
     * <p>This method returns encounter forms (such as Rourke charts, BCAR forms, and other
     * clinical assessment forms) that have been attached to an electronic form. These forms
     * represent structured clinical data collected during patient encounters.</p>
     *
     * @param loggedInInfo LoggedInInfo the current user's session information for security and audit purposes
     * @param fdid Integer the unique identifier of the eForm (form data ID)
     * @param documentType DocumentType the type of documents to retrieve
     * @param demographicNo Integer the patient's unique demographic identifier
     * @return List&lt;EctFormData.PatientForm&gt; list of patient encounter forms attached to the eForm
     */
    public List<EctFormData.PatientForm> getFormsAttachedToEForms(LoggedInInfo loggedInInfo, Integer fdid, DocumentType documentType, Integer demographicNo);

    /**
     * This method is responsible for lab version sorting and is intended for use in the attachment window (attachDocument.jsp).
     * In other parts of the application, developers should utilize CommonLabResultData.populateLabResultsData() to access all available lab data.
     */
    public List<AttachmentLabResultData> getAllLabsSortedByVersions(LoggedInInfo loggedInInfo, String demographicNo);

    /**
     * This method is intended for use in the attachment window (attachDocument.jsp) and is designed to retrieve a list of eForms except one.
     * In other parts of the application, developers are encouraged to use EFormUtil.listPatientEformsCurrent() to access all available eForms.
     * The reason for this function is to ensure a user cannot attach an eForm to itself.
     */
    public List<EFormData> getAllEFormsExpectFdid(LoggedInInfo loggedInInfo, Integer demographicNo, Integer fdid);

    /**
     * Attaches documents to a consultation request.
     *
     * <p>This method associates one or more documents with a specific consultation request,
     * allowing healthcare providers to include relevant medical records, laboratory results,
     * imaging reports, and other clinical information as part of the referral process.</p>
     *
     * @param loggedInInfo LoggedInInfo the current user's session information for security and audit purposes
     * @param documentType DocumentType the type of documents being attached
     * @param attachments String[] array of document identifiers to attach to the consultation
     * @param providerNo String the provider number performing the attachment operation
     * @param requestId Integer the unique identifier of the consultation request
     * @param demographicNo Integer the patient's unique demographic identifier
     */
    public void attachToConsult(LoggedInInfo loggedInInfo, DocumentType documentType, String[] attachments, String providerNo, Integer requestId, Integer demographicNo);

    /**
     * Attaches documents to a consultation request with optional OceanMD integration.
     *
     * <p>This method provides enhanced functionality for attaching documents to consultation requests,
     * including automatic synchronization with OceanMD e-referral system when applicable. This allows
     * for seamless integration with external referral management platforms.</p>
     *
     * <p>When editOnOcean is set to false, this performs standard attach/detach operations on the
     * consultation request form. When editOnOcean is set to true, indicating the consultation was
     * created by OceanMD, the method performs two operations:</p>
     * <ol>
     *   <li>Attaches or detaches documents from the consultation request in OpenO EMR</li>
     *   <li>Adds new attachments to the 'EreferAttachment' table for automatic synchronization with OceanMD</li>
     * </ol>
     *
     * <p>This dual operation eliminates the need for manual attachment uploads to the e-referral system,
     * as attachments are automatically synchronized and made available to the receiving specialist.</p>
     *
     * @param loggedInInfo LoggedInInfo the current user's session information for security and audit purposes
     * @param documentType DocumentType the type of documents being attached
     * @param attachments String[] array of document identifiers to attach to the consultation
     * @param providerNo String the provider number performing the attachment operation
     * @param requestId Integer the unique identifier of the consultation request
     * @param demographicNo Integer the patient's unique demographic identifier
     * @param editOnOcean Boolean true if the consultation was created by OceanMD and requires automatic synchronization, false for standard attach/detach operations
     */
    public void attachToConsult(LoggedInInfo loggedInInfo, DocumentType documentType, String[] attachments, String providerNo, Integer requestId, Integer demographicNo, Boolean editOnOcean);

    /**
     * Attaches documents to an electronic form (eForm).
     *
     * <p>This method associates one or more documents with a specific eForm, allowing healthcare
     * providers to include supporting documentation, laboratory results, imaging reports, and
     * other relevant medical records alongside structured form data.</p>
     *
     * @param loggedInInfo LoggedInInfo the current user's session information for security and audit purposes
     * @param documentType DocumentType the type of documents being attached
     * @param attachments String[] array of document identifiers to attach to the eForm
     * @param providerNo String the provider number performing the attachment operation
     * @param fdid Integer the unique identifier of the eForm (form data ID)
     * @param demographicNo Integer the patient's unique demographic identifier
     */
    public void attachToEForm(LoggedInInfo loggedInInfo, DocumentType documentType, String[] attachments, String providerNo, Integer fdid, Integer demographicNo);

    /**
     * Concatenates multiple PDF documents into a single PDF file.
     *
     * <p>This method combines a list of PDF document objects into a single consolidated PDF file.
     * This is commonly used when generating comprehensive patient records that include multiple
     * clinical documents, laboratory results, imaging reports, and other medical records.</p>
     *
     * @param pdfDocumentList ArrayList&lt;Object&gt; list of PDF document objects to concatenate
     * @return Path the file system path to the concatenated PDF document
     * @throws PDFGenerationException if an error occurs during the PDF concatenation process
     */
    public Path concatPDF(ArrayList<Object> pdfDocumentList) throws PDFGenerationException;

    /**
     * Concatenates multiple PDF documents specified by file paths into a single PDF file.
     *
     * <p>This method combines multiple PDF files, identified by their file system paths, into
     * a single consolidated PDF document. This is particularly useful for creating comprehensive
     * patient records that merge various clinical documents stored as separate PDF files.</p>
     *
     * @param pdfDocuments List&lt;Path&gt; list of file system paths to PDF documents to concatenate
     * @return Path the file system path to the concatenated PDF document
     * @throws PDFGenerationException if an error occurs during the PDF concatenation process
     */
    public Path concatPDF(List<Path> pdfDocuments) throws PDFGenerationException;

    /**
     * Renders a medical document to PDF format based on HTTP request parameters.
     *
     * <p>This method generates a PDF representation of a medical document by processing
     * HTTP request parameters to identify the document and its rendering requirements.
     * The rendered PDF is suitable for printing, archival, or transmission to external systems.</p>
     *
     * @param request HttpServletRequest the HTTP request containing document parameters
     * @param response HttpServletResponse the HTTP response for potential streaming operations
     * @param documentType DocumentType the type of document to render (eForm, lab result, clinical document, etc.)
     * @return Path the file system path to the rendered PDF document
     * @throws PDFGenerationException if an error occurs during the PDF rendering process
     */
    public Path renderDocument(HttpServletRequest request, HttpServletResponse response, DocumentType documentType) throws PDFGenerationException;

    /**
     * Renders a medical document to PDF format by document type and identifier.
     *
     * <p>This method generates a PDF representation of various healthcare document types including
     * electronic forms (eForms), clinical documents (Docs), hospital report manager records (HRMs),
     * and laboratory results (Labs). The rendered PDF is suitable for printing, archival, electronic
     * transmission, or attachment to other clinical documents.</p>
     *
     * <p>This method provides a streamlined interface for document rendering by directly accepting
     * the document identifier and type, without requiring HTTP request/response objects.</p>
     *
     * @param loggedInInfo LoggedInInfo the current user's session information for security and audit purposes
     * @param documentType DocumentType the type of document to render (eForm, Doc, HRM, or Lab)
     * @param documentId Integer the unique identifier of the document to render
     * @return Path the file system path to the rendered PDF document
     * @throws PDFGenerationException if an error occurs during the PDF rendering process
     */
    public Path renderDocument(LoggedInInfo loggedInInfo, DocumentType documentType, Integer documentId) throws PDFGenerationException;

    /**
     * Renders a consultation form along with all its associated attachments as a single PDF.
     *
     * <p>This method generates a comprehensive PDF document that includes the consultation request
     * form and all attached medical records, laboratory results, imaging reports, and other
     * supporting documentation. This consolidated PDF is suitable for specialist referrals,
     * e-referral system transmission, or archival purposes.</p>
     *
     * @param request HttpServletRequest the HTTP request containing consultation parameters
     * @param response HttpServletResponse the HTTP response for potential streaming operations
     * @return Path the file system path to the rendered PDF document containing the consultation form and attachments
     * @throws PDFGenerationException if an error occurs during the PDF rendering or concatenation process
     */
    public Path renderConsultationFormWithAttachments(HttpServletRequest request, HttpServletResponse response) throws PDFGenerationException;

    /**
     * Renders an electronic form (eForm) along with all its associated attachments as a single PDF.
     *
     * <p>This method generates a comprehensive PDF document that includes the eForm data and all
     * attached medical records, laboratory results, clinical documents, and other supporting
     * documentation. This consolidated PDF is suitable for archival, printing, or conversion
     * to an electronic document (eDoc) for permanent storage.</p>
     *
     * @param request HttpServletRequest the HTTP request containing eForm parameters
     * @param response HttpServletResponse the HTTP response for potential streaming operations
     * @return Path the file system path to the rendered PDF document containing the eForm and attachments
     * @throws PDFGenerationException if an error occurs during the PDF rendering or concatenation process
     */
    public Path renderEFormWithAttachments(HttpServletRequest request, HttpServletResponse response) throws PDFGenerationException;

    /**
     * Converts an electronic form (eForm) to an electronic document (eDoc) for permanent archival.
     *
     * <p>This method renders an eForm as a PDF and saves it as an electronic document in the
     * OpenO EMR document management system. This conversion creates a permanent, immutable record
     * of the eForm data and any attachments, which is essential for regulatory compliance and
     * long-term patient record retention.</p>
     *
     * <p>The resulting eDoc maintains all the clinical information from the original eForm while
     * providing a stable format for archival and retrieval purposes.</p>
     *
     * @param request HttpServletRequest the HTTP request containing eForm parameters
     * @param response HttpServletResponse the HTTP response for potential streaming operations
     * @return Integer the unique identifier of the newly created electronic document (eDoc)
     * @throws PDFGenerationException if an error occurs during the PDF rendering or document saving process
     */
    public Integer saveEFormAsEDoc(HttpServletRequest request, HttpServletResponse response) throws PDFGenerationException;

    /**
     * Converts a PDF document to Base64-encoded string representation.
     *
     * <p>This method reads a PDF document from the file system and encodes it as a Base64 string,
     * which is suitable for embedding in JSON/XML payloads, transmitting via web services, or
     * storing in text-based fields. This is commonly used for integration with external healthcare
     * systems such as e-referral platforms, laboratory information systems, and document exchange networks.</p>
     *
     * @param renderedDocument Path the file system path to the PDF document to encode
     * @return String the Base64-encoded representation of the PDF document
     * @throws PDFGenerationException if an error occurs during the file reading or encoding process
     */
    public String convertPDFToBase64(Path renderedDocument) throws PDFGenerationException;

    /**
     * Flattens interactive form fields in a PDF document to make them non-editable.
     *
     * <p>This method converts all interactive form fields (text fields, checkboxes, radio buttons, etc.)
     * in a PDF document into static content, effectively making the document non-editable. This process
     * is important for creating final, immutable versions of clinical documents for archival purposes,
     * preventing accidental modifications to patient health information.</p>
     *
     * <p>Flattening is commonly applied before transmitting documents to external systems or when
     * creating permanent records that must remain unchanged for regulatory compliance.</p>
     *
     * @param pdfPath Path the file system path to the PDF document to flatten
     * @throws PDFGenerationException if an error occurs during the PDF flattening process
     */
    public void flattenPDFFormFields(Path pdfPath) throws PDFGenerationException;
}

	
