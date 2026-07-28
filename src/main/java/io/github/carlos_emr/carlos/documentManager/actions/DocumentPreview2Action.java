package io.github.carlos_emr.carlos.documentManager.actions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApproval;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.documentManager.data.AttachmentLabResultData;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMUtil;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.StringUtils;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action for previewing and rendering medical documents as PDFs in the OpenO EMR system.
 *
 * This action handles the preview and rendering of various healthcare document types including
 * electronic documents (EDocs), electronic forms (EForms), hospital report manager documents (HRM),
 * laboratory results, encounter forms, and consultation documents. It provides secure PDF generation
 * and delivery for clinical documentation while enforcing path traversal protection to maintain
 * PHI (Patient Health Information) security.
 *
 * The action supports method-based routing via the "method" request parameter to handle different
 * document rendering operations and document retrieval workflows.
 *
 * @since 2026-01-24
 * @see DocumentAttachmentManager
 * @see DocumentType
 */
public class DocumentPreview2Action extends ActionSupport {
    private static final String FETCH_CONSULT_DOCUMENTS = "fetchConsultDocuments";
    private static final String DEMOGRAPHIC_NO_PARAMETER = "demographicNo";
    private static final String EFORM_SECURITY_OBJECT = "_eform";

    private enum PreviewError {
        INVALID_REQUEST("invalid_request", "Invalid preview request."),
        EDOC_RENDER_FAILED("edoc_render_failed", "Failed to render document PDF."),
        EFORM_RENDER_FAILED("eform_render_failed", "Failed to render eForm PDF."),
        EFORM_APPROVAL_INVALID("eform_approval_invalid",
                "The incomplete-render approval is invalid or expired. Render the preview again."),
        EFORM_MISSING_CONTENT("eform_missing_content",
                "This eForm could not be fully rendered because required content or behavior is missing. "
                        + "You can render it only after approving the listed issues, but the document may be incomplete."),
        HRM_RENDER_FAILED("hrm_render_failed", "Failed to render HRM PDF."),
        LAB_RENDER_FAILED("lab_render_failed", "Failed to render lab PDF."),
        FORM_RENDER_FAILED("form_render_failed", "Failed to render form PDF.");

        private final String code;
        private final String message;

        PreviewError(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();
    private final DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
    private final EFormRenderApprovalService renderApprovalService = SpringUtils.getBean(EFormRenderApprovalService.class);
    private final PdfPreviewCapabilityService pdfPreviewCapabilityService =
            SpringUtils.getBean(PdfPreviewCapabilityService.class);
    private final FormsManager formsManager = SpringUtils.getBean(FormsManager.class);
    private final transient EFormDataDao eFormDataDao = SpringUtils.getBean(EFormDataDao.class);
    private final transient PatientLabRoutingDao patientLabRoutingDao = SpringUtils.getBean(PatientLabRoutingDao.class);
    private final transient HRMDocumentToDemographicDao hrmDocumentToDemographicDao = SpringUtils.getBean(HRMDocumentToDemographicDao.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Main execution entry point for the DocumentPreview2Action.
     *
     * Routes requests to appropriate document handling methods based on the "method" request parameter.
     * Supports the following methods:
     * - fetchEFormDocuments: Retrieves electronic forms for document selection
     * - renderEDocPDF: Renders electronic documents as PDF
     * - renderEFormPDF: Renders electronic forms as PDF
     * - renderHrmPDF: Renders hospital report manager documents as PDF
     * - renderLabPDF: Renders laboratory results as PDF
     * - renderFormPDF: Renders encounter forms as PDF
     * - renderPDF: Renders arbitrary PDF files with security validation
     * - fetchConsultDocuments: Retrieves consultation-related documents (default)
     *
     * @return String result name for Struts2 result mapping, or null for direct response rendering
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String requestMethod = request.getParameter("method");
        String method = StringUtils.isNullOrEmpty(requestMethod)
                ? FETCH_CONSULT_DOCUMENTS
                : requestMethod;

        switch (method.toLowerCase(Locale.ROOT)) {
            case "fetcheformdocuments":
                return fetchEFormDocuments();
            case "renderedocpdf":
                renderEDocPDF();
                return NONE;
            case "rendereformpdf":
                renderEFormPDF();
                return NONE;
            case "renderhrmpdf":
                renderHrmPDF();
                return NONE;
            case "renderlabpdf":
                renderLabPDF();
                return NONE;
            case "renderformpdf":
                renderFormPDF();
                return NONE;
            case "renderpdf":
                renderPDF();
                return NONE;
            case "fetchconsultdocuments":
                return fetchConsultDocuments();
            default:
                logger.warn("Unsupported previewDocs method requested.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
        }
    }

    private void requirePrivilege(LoggedInInfo loggedInInfo, String securityObjectName, String privilege) {
        requirePrivilege(loggedInInfo, securityObjectName, privilege, null);
    }

    private void requirePrivilege(LoggedInInfo loggedInInfo, String securityObjectName, String privilege, String target) {
        if (!hasPrivilege(loggedInInfo, securityObjectName, privilege, target)) {
            throw new SecurityException("missing required sec object (" + securityObjectName + ")");
        }
    }

    /**
     * Renders an electronic document (EDoc) as a PDF and returns it as base64-encoded JSON.
     *
     * Retrieves the specified EDoc by ID and generates a PDF representation using the
     * DocumentAttachmentManager. The resulting PDF is converted to base64 and returned
     * in a JSON response. This method writes directly to the HTTP response and returns
     * null to prevent additional view rendering.
     *
     * Expected request parameters:
     * - eDocId: String the unique identifier of the electronic document to render
     *
     * Response format: JSON object with "base64Data" field containing the PDF data,
     * or "errorMessage" field if PDF generation fails.
     */
    public void renderEDocPDF() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Integer eDocId = parseIntegerParameterOrRespondBadRequest(request.getParameter("eDocId"), "eDocId");
        if (eDocId == null) {
            return;
        }
        String demographicNo = parseDemographicNoOrRespondBadRequest();
        if (demographicNo == null) {
            return;
        }
        requirePrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, demographicNo);
        resolveEDocDemographicNoOrDeny(eDocId, demographicNo);
        try {
            Path docPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.DOC, eDocId);
            generateResponse(response, docPDFPath);
        } catch (PDFGenerationException e) {
            logger.error("Error occurred while rendering eDoc. " + e.getMessage(), e);
            generateResponse(response, PreviewError.EDOC_RENDER_FAILED);
        }
    }

    /**
     * Renders an electronic form (EForm) as a PDF and returns it as base64-encoded JSON.
     *
     * Retrieves the specified EForm by ID and generates a PDF representation using the
     * DocumentAttachmentManager. Electronic forms are structured clinical data entry forms
     * used throughout the OpenO EMR system. The resulting PDF is converted to base64 and
     * returned in a JSON response.
     *
     * Expected request parameters:
     * - eFormId: String the unique identifier of the electronic form to render
     *
     * Response format: JSON object with "base64Data" field containing the PDF data,
     * or "errorMessage" field if PDF generation fails.
     */
    public void renderEFormPDF() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Integer eFormId = parseIntegerParameterOrRespondBadRequest(request.getParameter("eFormId"), "eFormId");
        if (eFormId == null) {
            return;
        }
        String demographicNo = parseDemographicNoOrRespondBadRequest();
        if (demographicNo == null) {
            return;
        }
        requirePrivilege(loggedInInfo, EFORM_SECURITY_OBJECT, SecurityInfoManager.READ, demographicNo);
        resolveEFormDemographicNoOrDeny(eFormId, demographicNo);
        String approvalToken = request.getParameter("renderApproval");
        EFormRenderApproval approval = renderApprovalService.consume(
                request, loggedInInfo, eFormId, demographicNo,
                EFormRenderApprovalService.Operation.PREVIEW, approvalToken);
        if (approvalToken != null && approval == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            generateResponse(response, PreviewError.EFORM_APPROVAL_INVALID);
            return;
        }
        try {
            EformDataManager.EformPdfRender rendered =
                    documentAttachmentManager.renderEform(loggedInInfo, eFormId, approval);
            generateResponse(response, rendered.path(), rendered.completeness());
        } catch (EformContentUnavailableException e) {
            // Return sanitized issue categories so the clinician can make an informed decision.
            logger.warn("eForm preview incomplete: offering exact-issue approval (issues={})",
                    e.getIssueCount());
            String token = renderApprovalService.issue(
                    request, loggedInInfo, eFormId, demographicNo,
                    EFormRenderApprovalService.Operation.PREVIEW, e.getReport(),
                    approval, e.getFdid());
            generateMissingContentResponse(
                    response, PreviewError.EFORM_MISSING_CONTENT, token, e.getReport());
        } catch (PDFGenerationException e) {
            logger.error("Error occurred while rendering eForm. " + e.getMessage(), e);
            generateResponse(response, PreviewError.EFORM_RENDER_FAILED);
        }
    }

    /**
     * Renders a Hospital Report Manager (HRM) document as a PDF and returns it as base64-encoded JSON.
     *
     * Retrieves the specified HRM document by ID and generates a PDF representation. HRM documents
     * contain reports from hospitals and external healthcare facilities, typically including lab
     * results, diagnostic imaging reports, and consultation notes from specialists. The resulting
     * PDF is converted to base64 and returned in a JSON response.
     *
     * Expected request parameters:
     * - hrmId: String the unique identifier of the HRM document to render
     *
     * Response format: JSON object with "base64Data" field containing the PDF data,
     * or "errorMessage" field if PDF generation fails.
     */
    public void renderHrmPDF() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Integer hrmId = parseIntegerParameterOrRespondBadRequest(request.getParameter("hrmId"), "hrmId");
        if (hrmId == null) {
            return;
        }
        String demographicNo = parseDemographicNoOrRespondBadRequest();
        if (demographicNo == null) {
            return;
        }
        requirePrivilege(loggedInInfo, "_hrm", SecurityInfoManager.READ, demographicNo);
        resolveHrmDemographicNoOrDeny(hrmId, demographicNo);
        try {
            Path hrmPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.HRM, hrmId);
            generateResponse(response, hrmPDFPath);
        } catch (PDFGenerationException e) {
            logger.error("Error occurred while rendering HRM. " + e.getMessage(), e);
            generateResponse(response, PreviewError.HRM_RENDER_FAILED);
        }
    }

    /**
     * Renders a laboratory result document as a PDF and returns it as base64-encoded JSON.
     *
     * Retrieves the specified lab result by segment ID and generates a PDF representation.
     * Laboratory results include HL7-formatted lab reports from integrated laboratory
     * information systems. The resulting PDF is converted to base64 and returned in a JSON response.
     *
     * Expected request parameters:
     * - segmentId: String the unique segment identifier of the laboratory result to render
     *
     * Response format: JSON object with "base64Data" field containing the PDF data,
     * or "errorMessage" field if PDF generation fails.
     */
    public void renderLabPDF() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Integer segmentId = parseIntegerParameterOrRespondBadRequest(request.getParameter("segmentId"), "segmentId");
        if (segmentId == null) {
            return;
        }
        String demographicNo = parseDemographicNoOrRespondBadRequest();
        if (demographicNo == null) {
            return;
        }
        requirePrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, demographicNo);
        resolveLabDemographicNoOrDeny(segmentId, demographicNo);
        try {
            Path labPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.LAB, segmentId);
            generateResponse(response, labPDFPath);
        } catch (PDFGenerationException e) {
            logger.error("Error occurred while rendering Lab. " + e.getMessage(), e);
            generateResponse(response, PreviewError.LAB_RENDER_FAILED);
        }
    }

    /**
     * Renders an encounter form as a PDF and returns it as base64-encoded JSON.
     *
     * Retrieves and generates a PDF representation of an encounter form (classic form data).
     * Encounter forms include various clinical assessment forms such as Rourke growth charts,
     * BCAR (British Columbia Antenatal Record), mental health assessments, and other
     * province-specific medical forms. The resulting PDF is converted to base64 and returned
     * in a JSON response.
     *
     * Response format: JSON object with "base64Data" field containing the PDF data,
     * or "errorMessage" field if PDF generation fails.
     */
    public void renderFormPDF() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Integer formId = parseIntegerParameterOrRespondBadRequest(request.getParameter("formId"), "formId");
        if (formId == null) {
            return;
        }
        String formName = parseRequiredParameterOrRespondBadRequest(request.getParameter("formName"), "formName");
        if (formName == null) {
            return;
        }
        Integer demographicId = parseIntegerParameterOrRespondBadRequest(
                request.getParameter(DEMOGRAPHIC_NO_PARAMETER), DEMOGRAPHIC_NO_PARAMETER);
        if (demographicId == null) {
            return;
        }
        String demographicNo = String.valueOf(demographicId);
        requirePrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, demographicNo);
        requireFormBelongsToDemographic(loggedInInfo, formId, formName, demographicId);
        try {
            Path formPDFPath = documentAttachmentManager.renderDocument(request, response, DocumentType.FORM);
            generateResponse(response, formPDFPath);
        } catch (PDFGenerationException e) {
            logger.error("Error occurred while rendering Form. " + e.getMessage(), e);
            generateResponse(response, PreviewError.FORM_RENDER_FAILED);
        }
    }

    /**
     * Streams the generated PDF identified by a short-lived capability. Raw filesystem paths are
     * never accepted from the browser; the capability is bound to the exact canonical temp file,
     * authenticated provider, and HTTP session that prepared the email attachment.
     */
    public void renderPDF() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Path pdfPath = pdfPreviewCapabilityService.resolve(
                request, loggedInInfo, request.getParameter("previewToken"));
        if (pdfPath == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        try {
            response.setContentType("application/pdf");
            try (InputStream inputStream = Files.newInputStream(pdfPath);
                 BufferedInputStream bfis = new BufferedInputStream(inputStream);
                 ServletOutputStream outs = response.getOutputStream()) {

                int data;
                while ((data = bfis.read()) != -1) {
                    outs.write(data); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- application/pdf binary document preview
                }

                outs.flush();
            }
        } catch (IOException e) {
            logger.error("Error processing authorized PDF preview", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Fetches all consultation-related documents for a specified patient.
     *
     * Retrieves comprehensive medical documentation for consultation workflows including
     * electronic documents, hospital reports, laboratory results sorted by versions,
     * encounter forms, and current electronic forms. The documents are populated as
     * request attributes for rendering in the consultation document selection interface.
     *
     * Expected request parameters:
     * - demographicNo: String the patient's demographic number (defaults to "0" if not provided)
     *
     * Request attributes set:
     * - allDocuments: List&lt;EDoc&gt; all electronic documents for the patient
     * - allHRMDocuments: ArrayList&lt;HashMap&lt;String, ? extends Object&gt;&gt; all HRM documents
     * - allLabsSortedByVersions: List&lt;AttachmentLabResultData&gt; lab results sorted by versions
     * - allForms: List&lt;EctFormData.PatientForm&gt; all encounter forms
     * - allEForms: List&lt;EFormData&gt; all current electronic forms
     *
     * @return String "fetchDocuments" result name for Struts2 result mapping
     */
    public String fetchConsultDocuments() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String demographicNo = requestDemographicNoOrZero();
        Integer demographicId = parseIntegerParameterOrDefault(demographicNo, DEMOGRAPHIC_NO_PARAMETER, 0);
        String sanitizedDemographicNo = String.valueOf(demographicId);

        requirePrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, sanitizedDemographicNo);
        request.setAttribute(DEMOGRAPHIC_NO_PARAMETER, sanitizedDemographicNo);
        request.setAttribute("attachmentSecurityObject", "_con");
        request.setAttribute("canManageAttachments", hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, sanitizedDemographicNo));
        populateCommonDocs(loggedInInfo, sanitizedDemographicNo, demographicId);
		List<EFormData> allEForms = hasPrivilege(loggedInInfo, EFORM_SECURITY_OBJECT, SecurityInfoManager.READ, sanitizedDemographicNo)
                ? EFormUtil.listPatientEformsCurrent(demographicId, true)
                : new ArrayList<>();
        request.setAttribute("allEForms", allEForms);

        return "fetchDocuments";
    }

    /**
     * Fetches electronic form documents for a specified patient, excluding a specific form.
     *
     * Retrieves comprehensive medical documentation similar to fetchConsultDocuments, but
     * filters out a specific electronic form by form data ID (fdid). This is typically used
     * when attaching documents to an existing eForm to prevent self-reference. The documents
     * are populated as request attributes for rendering in the document selection interface.
     *
     * Expected request parameters:
     * - demographicNo: String the patient's demographic number (defaults to "0" if not provided)
     * - fdid: String the form data ID to exclude from the eForm list (defaults to "0" if not provided)
     *
     * Request attributes set:
     * - allDocuments: List&lt;EDoc&gt; all electronic documents for the patient
     * - allHRMDocuments: ArrayList&lt;HashMap&lt;String, ? extends Object&gt;&gt; all HRM documents
     * - allLabsSortedByVersions: List&lt;AttachmentLabResultData&gt; lab results sorted by versions
     * - allForms: List&lt;EctFormData.PatientForm&gt; all encounter forms
     * - allEForms: List&lt;EFormData&gt; all electronic forms excluding the specified fdid
     *
     * @return String "fetchDocuments" result name for Struts2 result mapping
     */
    public String fetchEFormDocuments() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String demographicNo = requestDemographicNoOrZero();
        String fdid = StringUtils.isNullOrEmpty(request.getParameter("fdid")) ? "0" : request.getParameter("fdid");
        Integer demographicId = parseIntegerParameterOrDefault(demographicNo, DEMOGRAPHIC_NO_PARAMETER, 0);
        String sanitizedDemographicNo = String.valueOf(demographicId);
        Integer fdidInt = parseIntegerParameterOrDefault(fdid, "fdid", 0);

        requirePrivilege(loggedInInfo, EFORM_SECURITY_OBJECT, SecurityInfoManager.READ, sanitizedDemographicNo);
        request.setAttribute(DEMOGRAPHIC_NO_PARAMETER, sanitizedDemographicNo);
        request.setAttribute("attachmentSecurityObject", EFORM_SECURITY_OBJECT);
        request.setAttribute("canManageAttachments", hasPrivilege(loggedInInfo, EFORM_SECURITY_OBJECT, SecurityInfoManager.WRITE, sanitizedDemographicNo));
        populateCommonDocs(loggedInInfo, sanitizedDemographicNo, demographicId);
		List<EFormData> allEForms = documentAttachmentManager.getAllEFormsExpectFdid(loggedInInfo, demographicId, fdidInt);
		request.setAttribute("allEForms", allEForms);

        return "fetchDocuments";
    }

    /**
     * Generates a JSON response containing a base64-encoded PDF document.
     *
     * Converts the PDF file at the specified path to base64 encoding and wraps it in a JSON
     * object for transmission to the client. This method is used by the various renderXXXPDF
     * methods to return PDF data in a format suitable for JavaScript-based document viewers.
     *
     * @param response HttpServletResponse the HTTP response object to write to
     * @param pdfPath Path the file system path to the PDF file to encode
     * @throws PDFGenerationException if an error occurs during base64 conversion or writing the response
     */
    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    private void generateResponse(HttpServletResponse response, Path pdfPath) throws PDFGenerationException {
        generateResponse(response, pdfPath, null);
    }

    /**
     * Writes the PDF response, attaching any non-blocking advisory conditions the render reported.
     */
    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    private void generateResponse(HttpServletResponse response, Path pdfPath,
            EFormRenderCompletenessReport completeness) throws PDFGenerationException {
        ObjectNode json = objectMapper.createObjectNode();
        String base64Data = documentAttachmentManager.convertPDFToBase64(pdfPath);
        json.put("base64Data", base64Data);
        if (completeness != null && completeness.advisoryIssueCount() > 0) {
            json.put("advisoryIssues", completeness.advisoryIssueCount());
        }
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            response.getWriter().write(json.toString());
        } catch (IOException e) {
            throw new PDFGenerationException("An error occurred while writing JSON response to the output stream", e);
        }
    }

    /**
     * Generates a JSON error response for PDF generation failures.
     *
     * Creates a JSON object containing the error message and writes it to the HTTP response.
     * This method provides consistent error reporting for PDF generation failures across
     * all document rendering methods.
     *
     * @param response HttpServletResponse the HTTP response object to write to
     * @param error fixed public error code and message
     */
    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    private void generateResponse(HttpServletResponse response, PreviewError error) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("errorCode", error.code);
        json.put("errorMessage", error.message);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            response.getWriter().write(json.toString());
        } catch (IOException e) {
            logger.error("An error occurred while writing JSON response to the output stream", e);
        }
    }

    /**
     * Generates the sanitized issue report and exact approval token for an incomplete render.
     *
     * @param response HttpServletResponse the HTTP response object to write to
     * @param error fixed clinician-facing error (no PHI; no asset names)
     */
    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    private void generateMissingContentResponse(HttpServletResponse response, PreviewError error,
            String approvalToken, EFormRenderCompletenessReport report) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("errorCode", error.code);
        json.put("errorMessage", error.message);
        json.put("missingContent", true);
        json.put("renderApproval", approvalToken);
        json.put("failedContentResources", report.failedContentResources());
        json.put("excludedContentElements", report.excludedContentElements());
        json.put("signatureMissing", report.signatureMissing());
        json.put("providerStampMissing", report.providerStampMissing());
        json.put("timerCompatibilityFailure", report.timerCompatibilityFailure());
        // Keep in step with EFormRenderCompletenessReport: an omitted category would let the caller
        // present an incomplete issue set for an approval whose digest covers all of them.
        json.put("severeConsoleErrors", report.severeConsoleErrors());
        json.put("containedInteractions", report.containedInteractions());
        json.put("stabilizationCapped", report.stabilizationCapped());
        json.put("labDecisionSupportStubbed", report.labDecisionSupportStubbed());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            response.getWriter().write(json.toString());
        } catch (IOException e) {
            logger.error("An error occurred while writing JSON response to the output stream", e);
        }
    }

    /**
     * Populate common documents like EDocs, Labs, Forms, HRM documents
     * @param loggedInInfo Information about the logged-in user
     * @param demographicNo Demographic number of the patient
     */
    private void populateCommonDocs(LoggedInInfo loggedInInfo, String demographicNo, Integer demographicId) {
        List<EDoc> allDocuments = hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, demographicNo)
                ? EDocUtil.listDocs(loggedInInfo, "demographic", demographicNo, null, EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE)
                : new ArrayList<>();
        ArrayList<HashMap<String, ? extends Object>> allHRMDocuments = hasPrivilege(loggedInInfo, "_hrm", SecurityInfoManager.READ, demographicNo)
                ? HRMUtil.listHRMDocuments(loggedInInfo, "report_date", false, demographicNo, false)
                : new ArrayList<>();
        List<AttachmentLabResultData> allLabsSortedByVersions = hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, demographicNo)
                ? documentAttachmentManager.getAllLabsSortedByVersions(loggedInInfo, demographicNo)
                : new ArrayList<>();
        List<EctFormData.PatientForm> allForms = hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, demographicNo)
                ? formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, demographicId, false, true)
                : new ArrayList<>();

        request.setAttribute("allDocuments", allDocuments);
        request.setAttribute("allHRMDocuments", allHRMDocuments);
		request.setAttribute("allLabsSortedByVersions", allLabsSortedByVersions);
		request.setAttribute("allForms", allForms);
    }

    private boolean hasPrivilege(LoggedInInfo loggedInInfo, String securityObjectName, String privilege, String target) {
        return securityInfoManager.hasPrivilege(loggedInInfo, securityObjectName, privilege, target);
    }

    private String resolveEFormDemographicNoOrDeny(Integer eFormId, String requestedDemographicNo) {
        EFormData eFormData = eFormDataDao.find(eFormId.intValue());
        Integer demographicNo = eFormData == null ? null : eFormData.getDemographicId();
        return requireMatchingDemographicNo(demographicNo, requestedDemographicNo, "eForm");
    }

    private String resolveEDocDemographicNoOrDeny(Integer eDocId, String requestedDemographicNo) {
        EDoc eDoc = EDocUtil.getEDocFromDocId(String.valueOf(eDocId));
        if (eDoc == null || !"demographic".equals(eDoc.getModule())) {
            throw new SecurityException("document does not match demographic");
        }

        return requireMatchingDemographicNo(parseIntegerValue(eDoc.getModuleId()), requestedDemographicNo, "document");
    }

    private String resolveHrmDemographicNoOrDeny(Integer hrmId, String requestedDemographicNo) {
        List<HRMDocumentToDemographic> linkedDemographics = hrmDocumentToDemographicDao.findByHrmDocumentId(hrmId);
        if (linkedDemographics != null) {
            for (HRMDocumentToDemographic linkedDemographic : linkedDemographics) {
                if (linkedDemographic != null && requestedDemographicNo.equals(String.valueOf(linkedDemographic.getDemographicNo()))) {
                    return requestedDemographicNo;
                }
            }
        }

        throw new SecurityException("HRM document does not match demographic");
    }

    private String resolveLabDemographicNoOrDeny(Integer segmentId, String requestedDemographicNo) {
        PatientLabRouting patientLabRouting = patientLabRoutingDao.findDemographicByLabId(segmentId);
        Integer demographicNo = patientLabRouting == null ? null : patientLabRouting.getDemographicNo();
        return requireMatchingDemographicNo(demographicNo, requestedDemographicNo, "lab");
    }

    private void requireFormBelongsToDemographic(LoggedInInfo loggedInInfo, Integer formId, String formName, Integer demographicNo) {
        List<EctFormData.PatientForm> forms = formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, demographicNo, true, true);
        if (forms != null) {
            String requestedFormId = String.valueOf(formId);
            for (EctFormData.PatientForm form : forms) {
                if (form != null
                        && requestedFormId.equals(form.getFormId())
                        && demographicNo.equals(form.demographicId)
                        && formName.equals(form.getFormName())) {
                    return;
                }
            }
        }

        throw new SecurityException("form does not match demographic");
    }

    private String requireMatchingDemographicNo(Integer resolvedDemographicNo, String requestedDemographicNo, String resourceName) {
        if (resolvedDemographicNo == null) {
            throw new SecurityException(resourceName + " does not match demographic");
        }

        String resolvedDemographicNoString = String.valueOf(resolvedDemographicNo);
        if (!resolvedDemographicNoString.equals(requestedDemographicNo)) {
            throw new SecurityException(resourceName + " does not match demographic");
        }

        return resolvedDemographicNoString;
    }

    private String parseDemographicNoOrRespondBadRequest() {
        Integer demographicId = parseIntegerParameterOrRespondBadRequest(
                request.getParameter(DEMOGRAPHIC_NO_PARAMETER), DEMOGRAPHIC_NO_PARAMETER);
        return demographicId == null ? null : String.valueOf(demographicId);
    }

    private String requestDemographicNoOrZero() {
        String demographicNo = request.getParameter(DEMOGRAPHIC_NO_PARAMETER);
        return StringUtils.isNullOrEmpty(demographicNo) ? "0" : demographicNo;
    }

    private String parseRequiredParameterOrRespondBadRequest(String value, String parameterName) {
        if (StringUtils.isNullOrEmpty(value)) {
            logger.warn("Invalid {} received: empty value", parameterName);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            generateResponse(response, PreviewError.INVALID_REQUEST);
            return null;
        }

        return value;
    }

    private Integer parseIntegerParameterOrDefault(String value, String parameterName, Integer defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid {} received: {}. Falling back to {}.", parameterName, LogSafe.sanitize(value), defaultValue, e);
            return defaultValue;
        }
    }

    private Integer parseIntegerParameterOrRespondBadRequest(String value, String parameterName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid {} received: {}", parameterName, LogSafe.sanitize(value), e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            generateResponse(response, PreviewError.INVALID_REQUEST);
            return null;
        }
    }

    private Integer parseIntegerValue(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
