//CHECKSTYLE:OFF
package ca.openosp.openo.documentManager;

import ca.openosp.openo.managers.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import ca.openosp.openo.commn.dao.ConsultDocsDao;
import ca.openosp.openo.commn.dao.EFormDocsDao;
import ca.openosp.openo.commn.model.ConsultDocs;
import ca.openosp.openo.commn.model.EFormData;
import ca.openosp.openo.commn.model.EFormDocs;
import ca.openosp.openo.hospitalReportManager.HRMUtil;
import ca.openosp.openo.commn.model.enumerator.DocumentType;
import ca.openosp.openo.documentManager.data.AttachmentLabResultData;
import ca.openosp.openo.utility.DateUtils;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.PDFGenerationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.openosp.openo.eform.EFormUtil;
import ca.openosp.openo.encounter.data.EctFormData;
import ca.openosp.openo.lab.ca.all.Hl7textResultsData;
import ca.openosp.openo.lab.ca.on.CommonLabResultData;
import ca.openosp.openo.lab.ca.on.LabResultData;
import ca.openosp.openo.util.ConcatPDF;
import ca.openosp.openo.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Implementation of the DocumentAttachmentManager interface providing comprehensive document attachment
 * and rendering services for OpenO EMR healthcare documents.
 *
 * This service manages the lifecycle of document attachments across multiple healthcare document types
 * including consultation requests, electronic forms (eForms), electronic documents (eDocs), laboratory
 * results, hospital reports (HRMs), and clinical encounter forms. It provides functionality for:
 *
 * <ul>
 *   <li>Attaching and retrieving documents from consultation requests and eForms</li>
 *   <li>Rendering individual documents and composite documents with all attachments as PDF</li>
 *   <li>Converting documents to Base64 encoding for electronic transmission</li>
 *   <li>Concatenating multiple PDF documents with proper form field flattening</li>
 *   <li>Managing version-controlled laboratory results</li>
 *   <li>Supporting OceanMD eReferral integration for external consultation workflows</li>
 * </ul>
 *
 * All operations enforce security through privilege checks using SecurityInfoManager to ensure
 * healthcare providers have appropriate read/write access to patient health information (PHI).
 * The service integrates with multiple healthcare standards including HL7 for lab results and
 * supports Canadian provincial healthcare workflows (BC, ON).
 *
 * @see DocumentAttachmentManager
 * @see DocumentType
 * @see ConsultationManager
 * @see EformDataManager
 * @see SecurityInfoManager
 * @since 2026-01-24
 */
@Service
public class DocumentAttachmentManagerImpl implements DocumentAttachmentManager {
    @Autowired
    private ConsultDocsDao consultDocsDao;
    @Autowired
    private EFormDocsDao eFormDocsDao;

    @Autowired
    private ConsultationManager consultationManager;
    @Autowired
    private DocumentManager documentManager;
    @Autowired
    private EformDataManager eformDataManager;
    @Autowired
    private FormsManager formsManager;
    @Autowired
    private LabManager labManager;
    @Autowired
    private NioFileManager nioFileManager;
    @Autowired
    private SecurityInfoManager securityInfoManager;

    // @Autowired
    // public void setEformDataManager(EformDataManager eformDataManager) {
    //     this.eformDataManager = eformDataManager;
    // }

    /**
     * Retrieves a list of document IDs attached to a specific consultation request.
     *
     * This method queries the consultation documents database to find all documents of a specified type
     * that are attached to a given consultation request. Security checks ensure the user has read access
     * to consultation data for the specified patient.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information
     * @param requestId Integer the unique identifier of the consultation request
     * @param documentType DocumentType the type of documents to retrieve (e.g., DOC, LAB, EFORM, HRM, FORM)
     * @param demographicNo Integer the patient's demographic number for security validation
     * @return List&lt;String&gt; a list of document IDs as strings attached to the consultation request
     * @throws RuntimeException if the user lacks the required "_con" read privilege
     */
    public List<String> getConsultAttachments(LoggedInInfo loggedInInfo, Integer requestId, DocumentType documentType, Integer demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, demographicNo)) {
            throw new RuntimeException("missing required sec object (_con)");
        }

        List<String> consultAttachments = new ArrayList<>();
        List<ConsultDocs> consultDocs = consultDocsDao.findByRequestIdDocType(requestId, documentType.getType());
        for (ConsultDocs consultDocs1 : consultDocs) {
            consultAttachments.add(String.valueOf(consultDocs1.getDocumentNo()));
        }
        return consultAttachments;
    }

    /**
     * Retrieves a list of document IDs attached to a specific electronic form (eForm).
     *
     * This method queries the eForm documents database to find all documents of a specified type
     * that are attached to a given eForm instance. Security checks ensure the user has read access
     * to eForm data for the specified patient.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information
     * @param fdid Integer the unique form data identifier of the eForm
     * @param documentType DocumentType the type of documents to retrieve (e.g., DOC, LAB, EFORM, HRM, FORM)
     * @param demographicNo Integer the patient's demographic number for security validation
     * @return List&lt;String&gt; a list of document IDs as strings attached to the eForm
     * @throws RuntimeException if the user lacks the required "_eform" read privilege
     */
    public List<String> getEFormAttachments(LoggedInInfo loggedInInfo, Integer fdid, DocumentType documentType, Integer demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, demographicNo)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }

        List<String> eFormAttachments = new ArrayList<>();
        List<EFormDocs> eFormDocs = eFormDocsDao.findByFdidIdDocType(fdid, documentType.getType());
        for (EFormDocs eFormDocs1 : eFormDocs) {
            eFormAttachments.add(String.valueOf(eFormDocs1.getDocumentNo()));
        }
        return eFormAttachments;
    }

    /**
     * Retrieves a list of clinical encounter forms attached to a specific eForm.
     *
     * This method retrieves the full form objects (not just IDs) for all clinical encounter forms
     * that are attached to a given eForm. It first retrieves the list of attached form IDs, then
     * filters the patient's complete form list to return only the attached forms.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information
     * @param fdid Integer the unique form data identifier of the eForm
     * @param documentType DocumentType the type of documents to retrieve (typically FORM)
     * @param demographicNo Integer the patient's demographic number for security validation
     * @return List&lt;EctFormData.PatientForm&gt; a list of PatientForm objects attached to the eForm
     * @throws RuntimeException if the user lacks the required "_eform" read privilege
     */
    public List<EctFormData.PatientForm> getFormsAttachedToEForms(LoggedInInfo loggedInInfo, Integer fdid, DocumentType documentType, Integer demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, demographicNo)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }

        List<EctFormData.PatientForm> attachedForms = new ArrayList<>();
        List<String> attachedFormIds = getEFormAttachments(loggedInInfo, fdid, documentType, demographicNo);
        List<EctFormData.PatientForm> allForms = formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, demographicNo, true, true);
        for (String formId : attachedFormIds) {
            for (EctFormData.PatientForm form : allForms) {
                if (!form.getFormId().equals(formId)) {
                    continue;
                }
                attachedForms.add(form);
            }
        }

        return attachedForms;
    }

    /**
     * Retrieves all laboratory results for a patient, sorted and grouped by version relationships.
     *
     * This method is specifically designed for the attachment window (attachDocument.jsp) to present
     * laboratory results in a user-friendly format where the latest version of each lab is shown with
     * its historical versions grouped together. The method performs complex version tracking to ensure
     * that amended or corrected lab results are properly linked to their original versions.
     *
     * The algorithm identifies version relationships between labs (e.g., original, correction, amendment)
     * and groups them together, with the latest version as the primary entry and older versions as
     * sub-entries. This prevents duplicate selection and provides clear version history.
     *
     * <strong>Note:</strong> For general lab data access in other parts of the application, developers
     * should use CommonLabResultData.populateLabResultsData() instead.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information
     * @param demographicNo String the patient's demographic number
     * @return List&lt;AttachmentLabResultData&gt; a list of lab results with version information,
     *         where each entry contains the latest version and a map of its historical versions
     */
    public List<AttachmentLabResultData> getAllLabsSortedByVersions(LoggedInInfo loggedInInfo, String demographicNo) {
        CommonLabResultData commonLabResultData = new CommonLabResultData();
        List<LabResultData> allLabs = commonLabResultData.populateLabResultsData(loggedInInfo, "", demographicNo, "", "", "", "U");
        Collections.sort(allLabs);

        List<String> allLabVersionIds = new ArrayList<>();
        List<AttachmentLabResultData> allLabsSortedByVersions = new ArrayList<>();

        Map<String, LabResultData> labMap = new HashMap<>();
        for (LabResultData lab : allLabs) {
            labMap.put(lab.getSegmentID(), lab);
        }

        /*
         * Explaining this code with an example:
         * Let's assume the 'allLabs' variable contains these lab IDs [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].
         * Among these IDs, ID 2 is the latest version, and 3, 4, and 5 are older versions of that.
         * Similarly, 6 is the latest version, and 1, 7, 8, and 9 are older versions of that.
         * Lab ID 10 doesn't have any version.
         *
         * First, I iterate through the 'allLabs' using a for loop.
         */
        for (LabResultData lab : allLabs) {
            if (allLabVersionIds.contains(lab.getSegmentID())) {
                continue;
            }

            AttachmentLabResultData attachmentLabResultData = new AttachmentLabResultData(lab.getSegmentID(), getDisplayLabName(lab), lab.getDateObj());

            /*
             * Then, if, for example, I pass lab ID 1, it will give all its related labs in the correct version order.
             * By 'correct order,' I mean it will return this array [7, 9, 8, 1, 6].
             * This array will be in version order, where the first is the oldest and the last is the latest.
             */
            String[] matchingLabIds = Hl7textResultsData.getMatchingLabs(lab.getSegmentID()).split(",");


            /*
             * Here, I add the latest lab (6) to 'allLabsSortedByVersions' after attaching its versions (7, 9, 8, and 1) to the latest lab.
             */
            for (int i = matchingLabIds.length - 2; i >= 0; i--) {
                LabResultData versionLab = labMap.get(matchingLabIds[i]);
                if (versionLab != null) {
                    attachmentLabResultData.getLabVersionIds().put(versionLab.getSegmentID(), DateUtils.formatDate(versionLab.getDateObj(), null));
                }

                /*
                 * Then, I add those version labs (7, 9, and 8) into the 'allLabVersionIds' array so that they can be skipped.
                 * At the start of the for loop, I use `if (allLabVersionIds.contains(lab.getSegmentID())) { continue; }` to ensure that labs already included in 'allLabVersionIds' are skipped during the iteration.
                 */
                allLabVersionIds.add(matchingLabIds[i]);
            }
            allLabsSortedByVersions.add(attachmentLabResultData);
        }
        return allLabsSortedByVersions;
    }

    /**
     * Retrieves all current eForms for a patient, excluding a specified eForm.
     *
     * This method is specifically designed for the attachment window (attachDocument.jsp) to provide
     * a list of available eForms that can be attached to another eForm. The primary purpose is to
     * prevent circular references by excluding the target eForm from the list of attachable documents.
     * This ensures a user cannot attach an eForm to itself, which would create an invalid relationship.
     *
     * <strong>Note:</strong> For general eForm access in other parts of the application, developers
     * should use EFormUtil.listPatientEformsCurrent() instead.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information
     * @param demographicNo Integer the patient's demographic number
     * @param fdid Integer the form data identifier of the eForm to exclude from results
     * @return List&lt;EFormData&gt; a list of all current eForms for the patient except the specified one
     * @throws RuntimeException if the user lacks the required "_eform" read privilege
     */
    public List<EFormData> getAllEFormsExpectFdid(LoggedInInfo loggedInInfo, Integer demographicNo, Integer fdid) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, demographicNo)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }

        List<EFormData> allEForms = EFormUtil.listPatientEformsCurrent(demographicNo, true);
        Iterator<EFormData> iterator = allEForms.iterator();
        while (iterator.hasNext()) {
            EFormData eForm = iterator.next();
            if (fdid.equals(eForm.getId())) {
                iterator.remove();
            }
        }

        return allEForms;
    }

    /**
     * Attaches documents to a consultation request.
     *
     * This method creates attachment relationships between specified documents and a consultation request.
     * It supports attaching multiple document types (eDocs, labs, eForms, HRMs, forms) to a single
     * consultation request, enabling comprehensive patient information sharing for specialist referrals.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information
     * @param documentType DocumentType the type of documents being attached
     * @param attachments String[] an array of document IDs to attach to the consultation
     * @param providerNo String the provider number performing the attachment operation
     * @param requestId Integer the unique identifier of the consultation request
     * @param demographicNo Integer the patient's demographic number for security validation
     * @throws RuntimeException if the user lacks the required "_con" write privilege
     */
    public void attachToConsult(LoggedInInfo loggedInInfo, DocumentType documentType, String[] attachments, String providerNo, Integer requestId, Integer demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo)) {
            throw new RuntimeException("missing required sec object (_con)");
        }

        DocumentAttach documentAttach = new DocumentAttach();
        documentAttach.attachToConsult(attachments, documentType, providerNo, requestId);
    }

    /**
     * Attaches documents to a consultation request with optional OceanMD eReferral integration.
     *
     * This method extends the basic consultation attachment functionality to support OceanMD eReferral
     * workflows. When editOnOcean is enabled, the method performs dual operations: it attaches documents
     * to the consultation request locally and registers them in the EreferAttachment table for automatic
     * transmission to OceanMD. This eliminates the need for manual attachment upload to external eReferral
     * systems, streamlining the specialist referral process.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information
     * @param documentType DocumentType the type of documents being attached
     * @param attachments String[] an array of document IDs to attach to the consultation
     * @param providerNo String the provider number performing the attachment operation
     * @param requestId Integer the unique identifier of the consultation request
     * @param demographicNo Integer the patient's demographic number for security validation
     * @param editOnOcean Boolean when true, registers attachments for OceanMD transmission;
     *                            when false, performs standard local attachment only
     * @throws RuntimeException if the user lacks the required "_con" write privilege
     */
    public void attachToConsult(LoggedInInfo loggedInInfo, DocumentType documentType, String[] attachments, String providerNo, Integer requestId, Integer demographicNo, Boolean editOnOcean) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, demographicNo)) {
            throw new RuntimeException("missing required sec object (_con)");
        }

        DocumentAttach documentAttach = new DocumentAttach(demographicNo, editOnOcean);
        documentAttach.attachToConsult(attachments, documentType, providerNo, requestId);
    }

    /**
     * Attaches documents to an electronic form (eForm).
     *
     * This method creates attachment relationships between specified documents and an eForm instance.
     * It enables clinicians to build comprehensive electronic forms by attaching supporting documents
     * such as lab results, imaging reports, previous forms, and other clinical documentation.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information
     * @param documentType DocumentType the type of documents being attached
     * @param attachments String[] an array of document IDs to attach to the eForm
     * @param providerNo String the provider number performing the attachment operation
     * @param fdid Integer the unique form data identifier of the eForm
     * @param demographicNo Integer the patient's demographic number for security validation
     * @throws RuntimeException if the user lacks the required "_eform" write privilege
     */
    public void attachToEForm(LoggedInInfo loggedInInfo, DocumentType documentType, String[] attachments, String providerNo, Integer fdid, Integer demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.WRITE, demographicNo)) {
            throw new RuntimeException("missing required sec object (_eform)");
        }

        DocumentAttach documentAttach = new DocumentAttach();
        documentAttach.attachToEForm(attachments, documentType, providerNo, fdid);
    }

    /**
     * Concatenates multiple PDF documents into a single PDF file with flattened form fields.
     *
     * This method accepts a list of PDF documents (as Path strings or other compatible objects) and
     * merges them into a single consolidated PDF. After concatenation, all PDF form fields are
     * flattened to prevent further editing and ensure consistent rendering across different PDF
     * viewers. The resulting PDF is saved as a temporary file.
     *
     * @param pdfDocumentList ArrayList&lt;Object&gt; a list of PDF documents to concatenate,
     *                        where each entry can be a file path string or compatible object
     * @return Path the file system path to the generated concatenated PDF file
     * @throws PDFGenerationException if an error occurs during PDF concatenation or form field flattening
     */
    public Path concatPDF(ArrayList<Object> pdfDocumentList) throws PDFGenerationException {
        Path path = null;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ConcatPDF.concat(pdfDocumentList, outputStream);
            path = nioFileManager.saveTempFile("combinedPDF_" + new Date().getTime(), outputStream);
            flattenPDFFormFields(path);
        } catch (IOException e) {
            throw new PDFGenerationException("An error occurred while concatenating PDF.", e);
        }
        return path;
    }

    /**
     * Concatenates multiple PDF documents from Path objects into a single PDF file.
     *
     * This is a convenience method that converts a list of Path objects to the format required by
     * the primary concatPDF method. It then delegates to that method for the actual concatenation
     * and form field flattening operations.
     *
     * @param pdfDocuments List&lt;Path&gt; a list of Path objects pointing to PDF files to concatenate
     * @return Path the file system path to the generated concatenated PDF file
     * @throws PDFGenerationException if an error occurs during PDF concatenation or form field flattening
     */
    public Path concatPDF(List<Path> pdfDocuments) throws PDFGenerationException {
        ArrayList<Object> pdfDocumentList = new ArrayList<>();
        for (Path pdfDocument : pdfDocuments) {
            pdfDocumentList.add(pdfDocument.toString());
        }
        return concatPDF(pdfDocumentList);
    }

    /**
     * Renders a document to PDF format using HTTP request/response context.
     *
     * This is a convenience method that delegates to the primary renderDocument method with
     * minimal parameters. It is typically used when the document ID is not known in advance
     * and will be extracted from the request context.
     *
     * @param request HttpServletRequest the HTTP request containing document context
     * @param response HttpServletResponse the HTTP response for output streaming
     * @param documentType DocumentType the type of document to render
     * @return Path the file system path to the generated PDF file
     * @throws PDFGenerationException if an error occurs during document rendering
     */
    public Path renderDocument(HttpServletRequest request, HttpServletResponse response, DocumentType documentType) throws PDFGenerationException {
        return renderDocument(null, request, response, documentType, 0);
    }

    /**
     * Renders a healthcare document to PDF format based on document type and ID.
     *
     * This method provides a unified interface for rendering various types of healthcare documents
     * including electronic forms (eForms), electronic documents (eDocs), hospital report manager
     * documents (HRMs), and laboratory results (Labs). It delegates to the appropriate specialized
     * rendering service based on the document type.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information
     * @param documentType DocumentType the type of document to render (EFORM, DOC, HRM, or LAB)
     * @param documentId Integer the unique identifier of the document to render
     * @return Path the file system path to the generated PDF file
     * @throws PDFGenerationException if an error occurs during document rendering
     */
    public Path renderDocument(LoggedInInfo loggedInInfo, DocumentType documentType, Integer documentId) throws PDFGenerationException {
        return renderDocument(loggedInInfo, null, null, documentType, documentId);
    }

    /**
     * Renders a complete consultation request form with all attached documents as a single PDF.
     *
     * This method generates a comprehensive consultation package by rendering the consultation request
     * form and all its attachments (eForms, eDocs, lab results, HRMs, and clinical forms) into a single
     * concatenated PDF document. This is typically used for printing or electronic transmission of
     * complete consultation referrals to specialists.
     *
     * The method retrieves the consultation request ID and demographic ID from request attributes,
     * renders the consultation form, fetches all attached documents, renders each attachment to PDF,
     * and concatenates everything into a single document with flattened form fields.
     *
     * @param request HttpServletRequest the HTTP request containing reqId and demographicId attributes
     * @param response HttpServletResponse the HTTP response for output streaming
     * @return Path the file system path to the generated consolidated PDF file
     * @throws PDFGenerationException if an error occurs during rendering or concatenation
     */
    public Path renderConsultationFormWithAttachments(HttpServletRequest request, HttpServletResponse response) throws PDFGenerationException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String requestId = (String) request.getAttribute("reqId");
        String demographicId = (String) request.getAttribute("demographicId");
        Path consultationFormPDFPath = consultationManager.renderConsultationForm(request);

        List<EFormData> attachedEForms = consultationManager.getAttachedEForms(requestId);
        List<EDoc> attachedEDocs = EDocUtil.listDocs(loggedInInfo, demographicId, requestId, EDocUtil.ATTACHED);
        CommonLabResultData labResultData = new CommonLabResultData();
        List<LabResultData> attachedLabs = labResultData.populateLabResultsData(loggedInInfo, demographicId, requestId, CommonLabResultData.ATTACHED);
        ArrayList<HashMap<String, ? extends Object>> attachedHRMs = consultationManager.getAttachedHRMDocuments(loggedInInfo, demographicId, requestId);
        List<EctFormData.PatientForm> attachedForms = consultationManager.getAttachedForms(loggedInInfo, Integer.parseInt(requestId), Integer.parseInt(demographicId));

        ArrayList<Object> pdfDocumentList = new ArrayList<>();
        pdfDocumentList.add(consultationFormPDFPath.toString());
        attachEFormPDFs(loggedInInfo, attachedEForms, pdfDocumentList);
        attachEDocPDFs(loggedInInfo, attachedEDocs, pdfDocumentList);
        attachLabPDFs(loggedInInfo, attachedLabs, pdfDocumentList);
        attachHRMPDFs(loggedInInfo, attachedHRMs, pdfDocumentList);
        attachFormPDFs(request, response, attachedForms, pdfDocumentList);

        return concatPDF(pdfDocumentList);
    }

    /**
     * Renders a complete eForm with all attached documents as a single PDF.
     *
     * This method generates a comprehensive eForm package by rendering the eForm itself and all its
     * attachments (other eForms, eDocs, lab results, HRMs, and clinical forms) into a single
     * concatenated PDF document. This is typically used for printing, archiving, or electronic
     * transmission of complete electronic forms with supporting documentation.
     *
     * The method retrieves the form data ID (fdid) and demographic ID from request attributes,
     * renders the eForm, fetches all attached documents, renders each attachment to PDF,
     * and concatenates everything into a single document with flattened form fields.
     *
     * @param request HttpServletRequest the HTTP request containing fdid and demographicId attributes
     * @param response HttpServletResponse the HTTP response for output streaming
     * @return Path the file system path to the generated consolidated PDF file
     * @throws PDFGenerationException if an error occurs during rendering or concatenation
     */
    public Path renderEFormWithAttachments(HttpServletRequest request, HttpServletResponse response) throws PDFGenerationException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String fdid = (String) request.getAttribute("fdid");
        String demographicId = (String) request.getAttribute("demographicId");
        Path eFormPath = eformDataManager.createEformPDF(loggedInInfo, Integer.parseInt(fdid));

        List<EFormData> attachedEForms = EFormUtil.listPatientEformsCurrentAttachedToEForm(fdid);
        List<EDoc> attachedEDocs = EDocUtil.listDocsAttachedToEForm(loggedInInfo, demographicId, fdid, EDocUtil.ATTACHED);
        CommonLabResultData labResultData = new CommonLabResultData();
        List<LabResultData> attachedLabs = labResultData.populateLabResultsDataEForm(loggedInInfo, demographicId, fdid, CommonLabResultData.ATTACHED);
        ArrayList<HashMap<String, ? extends Object>> attachedHRMs = eformDataManager.getHRMDocumentsAttachedToEForm(loggedInInfo, fdid, demographicId);
        List<EctFormData.PatientForm> attachedForms = eformDataManager.getFormsAttachedToEForm(loggedInInfo, fdid, demographicId);

        ArrayList<Object> pdfDocumentList = new ArrayList<>();
        pdfDocumentList.add(eFormPath.toString());
        attachEFormPDFs(loggedInInfo, attachedEForms, pdfDocumentList);
        attachEDocPDFs(loggedInInfo, attachedEDocs, pdfDocumentList);
        attachLabPDFs(loggedInInfo, attachedLabs, pdfDocumentList);
        attachHRMPDFs(loggedInInfo, attachedHRMs, pdfDocumentList);
        attachFormPDFs(request, response, attachedForms, pdfDocumentList);

        return concatPDF(pdfDocumentList);
    }

    /**
     * Saves an eForm with all attachments as a permanent electronic document (eDoc).
     *
     * This method provides eForm archival functionality by rendering the eForm with all its
     * attachments as a PDF and saving it as a permanent eDoc in the document management system.
     * This is useful for creating immutable snapshots of eForms at specific points in time,
     * such as when submitting forms to external systems or archiving completed clinical assessments.
     *
     * @param request HttpServletRequest the HTTP request containing fdid and demographicId attributes
     * @param response HttpServletResponse the HTTP response for output streaming
     * @return Integer the unique document ID of the newly created eDoc
     * @throws PDFGenerationException if an error occurs during rendering or document creation
     */
    public Integer saveEFormAsEDoc(HttpServletRequest request, HttpServletResponse response) throws PDFGenerationException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String fdid = (String) request.getAttribute("fdid");
        String demographicId = (String) request.getAttribute("demographicId");
        Path eFormPath = renderEFormWithAttachments(request, response);
        return eformDataManager.saveEFormWithAttachmentsAsEDoc(loggedInInfo, fdid, demographicId, eFormPath);
    }

    /**
     * Converts a PDF document to Base64-encoded string for electronic transmission.
     *
     * This method reads a PDF file from the file system and encodes it as a Base64 string,
     * which is suitable for embedding in JSON/XML payloads, transmitting via web services,
     * or storing in text-based databases. This is commonly used for integration with external
     * healthcare systems such as provincial eReferral platforms or health information exchanges.
     *
     * @param renderedDocument Path the file system path to the PDF document to encode
     * @return String the Base64-encoded representation of the PDF file, or empty string if path is null
     * @throws PDFGenerationException if an error occurs while reading the PDF file
     */
    public String convertPDFToBase64(Path renderedDocument) throws PDFGenerationException {
        String base64 = "";
        if (renderedDocument == null) {
            return base64;
        }
        try {
            byte[] bytes = Files.readAllBytes(renderedDocument);
            base64 = Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new PDFGenerationException("An error occurred while processing the PDF file", e);
        }
        return base64 != null ? base64 : "";
    }

    private Path renderDocument(LoggedInInfo loggedInInfo, HttpServletRequest request, HttpServletResponse response, DocumentType documentType, Integer documentId) throws PDFGenerationException {
        Path path = null;
        switch (documentType) {
            case DOC:
                path = documentManager.renderDocument(loggedInInfo, String.valueOf(documentId));
                break;
            case LAB:
                path = labManager.renderLab(loggedInInfo, documentId);
                break;
            case EFORM:
                path = eformDataManager.createEformPDF(loggedInInfo, documentId);
                break;
            case HRM:
                path = HRMUtil.renderHRM(loggedInInfo, documentId);
                break;
            case FORM:
                EctFormData.PatientForm patientForm = null;
                path = formsManager.renderForm(request, response, patientForm);
                break;
        }
        return path;
    }

    private void attachEFormPDFs(LoggedInInfo loggedInInfo, List<EFormData> attachedEForms, ArrayList<Object> pdfDocumentList) throws PDFGenerationException {
        for (EFormData eForm : attachedEForms) {
            Path path = renderDocument(loggedInInfo, DocumentType.EFORM, eForm.getId());
            pdfDocumentList.add(path.toString());
        }
    }

    private void attachEDocPDFs(LoggedInInfo loggedInInfo, List<EDoc> attachedEDocs, ArrayList<Object> pdfDocumentList) throws PDFGenerationException {
        for (EDoc eDoc : attachedEDocs) {
            Path path = documentManager.renderDocument(loggedInInfo, eDoc);
            pdfDocumentList.add(path.toString());
        }
    }

    private void attachLabPDFs(LoggedInInfo loggedInInfo, List<LabResultData> attachedLabs, ArrayList<Object> pdfDocumentList) throws PDFGenerationException {
        for (LabResultData lab : attachedLabs) {
            Path path = renderDocument(loggedInInfo, DocumentType.LAB, Integer.parseInt(lab.getSegmentID()));
            pdfDocumentList.add(path.toString());
        }
    }

    private void attachHRMPDFs(LoggedInInfo loggedInInfo, ArrayList<HashMap<String, ? extends Object>> attachedHRMs, ArrayList<Object> pdfDocumentList) throws PDFGenerationException {
        for (HashMap<String, ?> hrm : attachedHRMs) {
            Path path = renderDocument(loggedInInfo, DocumentType.HRM, (Integer) hrm.get("id"));
            pdfDocumentList.add(path.toString());
        }
    }

    private void attachFormPDFs(HttpServletRequest request, HttpServletResponse response, List<EctFormData.PatientForm> attachedForms, ArrayList<Object> pdfDocumentList) throws PDFGenerationException {
        for (EctFormData.PatientForm form : attachedForms) {
            Path path = formsManager.renderForm(request, response, form);
            pdfDocumentList.add(path.toString());
        }
    }

    private String getDisplayLabName(LabResultData labResultData) {
        String label = labResultData.getLabel() != null ? labResultData.getLabel() : "";
        String discipline = labResultData.getDiscipline() != null ? labResultData.getDiscipline() : "";
        String labTitle = !"".equals(label) ? label.substring(0, Math.min(label.length(), 40)) : discipline.substring(0, Math.min(discipline.length(), 40));
        return StringUtils.isNullOrEmpty(labTitle) ? "UNLABELLED" : labTitle;
    }

    /**
     * Flattens all form fields in a PDF document to prevent further editing.
     *
     * This method processes a PDF file containing interactive form fields (AcroForm) and flattens
     * them by converting the fields into static content. Flattening ensures that form data cannot
     * be modified after generation and provides consistent rendering across different PDF viewers
     * and platforms. This is particularly important for healthcare documents that must maintain
     * data integrity and comply with record-keeping regulations.
     *
     * The operation modifies the PDF file in place, replacing the original file with the flattened version.
     *
     * @param pdfPath Path the file system path to the PDF document to flatten
     * @throws PDFGenerationException if an error occurs while loading, processing, or saving the PDF file
     */
    public void flattenPDFFormFields(Path pdfPath) throws PDFGenerationException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
            if (acroForm != null) {
                acroForm.flatten();
            }
            document.save(pdfPath.toString());
        } catch (IOException e) {
            throw new PDFGenerationException("Error while flattening the " + pdfPath.getFileName() + " file. " + e.getMessage(), e);
        }
    }
}
