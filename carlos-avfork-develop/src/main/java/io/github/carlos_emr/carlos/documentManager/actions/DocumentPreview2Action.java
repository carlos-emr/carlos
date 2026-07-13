package io.github.carlos_emr.carlos.documentManager.actions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.data.AttachmentLabResultData;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMUtil;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
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
 * @see PathValidationUtils
 */
public class DocumentPreview2Action extends ActionSupport {
    private static final String FETCH_CONSULT_DOCUMENTS = "fetchConsultDocuments";
    private static final String EDOC_PDF_RENDER_FAILURE_MESSAGE = "Failed to render document PDF.";
    private static final String EFORM_PDF_RENDER_FAILURE_MESSAGE = "Failed to render eForm PDF.";
    private static final String HRM_PDF_RENDER_FAILURE_MESSAGE = "Failed to render HRM PDF.";
    private static final String LAB_PDF_RENDER_FAILURE_MESSAGE = "Failed to render lab PDF.";
    private static final String FORM_PDF_RENDER_FAILURE_MESSAGE = "Failed to render form PDF.";

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();
    private final DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
    private final FormsManager formsManager = SpringUtils.getBean(FormsManager.class);
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
                requirePrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ);
                return fetchEFormDocuments();
            case "renderedocpdf":
                requirePrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ);
                renderEDocPDF();
                return null;
            case "rendereformpdf":
                requirePrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ);
                renderEFormPDF();
                return null;
            case "renderhrmpdf":
                requirePrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ);
                renderHrmPDF();
                return null;
            case "renderlabpdf":
                requirePrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ);
                renderLabPDF();
                return null;
            case "renderformpdf":
                requirePrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ);
                renderFormPDF();
                return null;
            case "renderpdf":
                requirePrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ);
                renderPDF();
                return null;
            case "fetchconsultdocuments":
                requirePrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE);
                return fetchConsultDocuments();
            default:
                logger.warn("Unsupported previewDocs method requested.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
        }
    }

    private void requirePrivilege(LoggedInInfo loggedInInfo, String securityObjectName, String privilege) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, securityObjectName, privilege, null)) {
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
        try {
            Path docPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.DOC, eDocId);
            generateResponse(response, docPDFPath);
        } catch (PDFGenerationException e) {
            logger.error("Error occurred while rendering eDoc. " + e.getMessage(), e);
            generateResponse(response, EDOC_PDF_RENDER_FAILURE_MESSAGE);
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
        try {
            Path eFormPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.EFORM, eFormId);
            generateResponse(response, eFormPDFPath);
        } catch (PDFGenerationException e) {
            logger.error("Error occurred while rendering eForm. " + e.getMessage(), e);
            generateResponse(response, EFORM_PDF_RENDER_FAILURE_MESSAGE);
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
        try {
            Path hrmPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.HRM, hrmId);
            generateResponse(response, hrmPDFPath);
        } catch (PDFGenerationException e) {
            logger.error("Error occurred while rendering HRM. " + e.getMessage(), e);
            generateResponse(response, HRM_PDF_RENDER_FAILURE_MESSAGE);
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
        try {
            Path labPDFPath = documentAttachmentManager.renderDocument(loggedInInfo, DocumentType.LAB, segmentId);
            generateResponse(response, labPDFPath);
        } catch (PDFGenerationException e) {
            logger.error("Error occurred while rendering Lab. " + e.getMessage(), e);
            generateResponse(response, LAB_PDF_RENDER_FAILURE_MESSAGE);
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
        try {
            Path formPDFPath = documentAttachmentManager.renderDocument(request, response, DocumentType.FORM);
            generateResponse(response, formPDFPath);
        } catch (PDFGenerationException e) {
            logger.error("Error occurred while rendering Form. " + e.getMessage(), e);
            generateResponse(response, FORM_PDF_RENDER_FAILURE_MESSAGE);
        }
    }

    /**
     * Renders a PDF file from a validated file path and streams it directly to the HTTP response.
     *
     * This method performs comprehensive security validation to prevent path traversal attacks
     * before serving PDF files. It validates that the requested file path exists within allowed
     * directories (DOCUMENT_DIR, TMP_DIR, eform_image, or system temp directory) using
     * PathValidationUtils. Only files that pass canonical path validation and exist as regular
     * files are served. This method is critical for maintaining PHI security and preventing
     * unauthorized file access.
     *
     * Expected request parameters:
     * - pdfPath: String the file system path to the PDF file to render
     *
     * Security measures:
     * - Validates path is not empty
     * - Resolves canonical path to detect traversal attempts
     * - Validates path is within allowed directories using PathValidationUtils
     * - Verifies file exists and is a regular file
     * - Sets appropriate HTTP status codes (400 for bad requests, 403 for forbidden paths,
     *   404 for missing files, 500 for server errors)
     *
     * Response: Streams PDF content directly with "application/pdf" content type, or sets
     * appropriate HTTP error status code if validation fails.
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public void renderPDF() {
        String pdfPathString = StringUtils.isNullOrEmpty(request.getParameter("pdfPath")) ? "" : request.getParameter("pdfPath");
        
        if (pdfPathString.isEmpty()) {
            logger.error("Empty PDF path provided");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        
        // Validate the PDF path to prevent path traversal attacks
        Path pdfPath;
        try {
            pdfPath = new java.io.File(pdfPathString).toPath();
        } catch (RuntimeException e) {
            logger.error("Invalid PDF path provided: {}", LogSafe.sanitize(pdfPathString));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        
        try {
            // Canonicalize first so no filesystem check runs on the raw, user-supplied path.
            // toRealPath resolves symlinks and ".." and throws if the file does not exist;
            // containment against the allowed roots is validated below before the file is read.
            Path canonicalPdfPath;
            try {
                canonicalPdfPath = pdfPath.toRealPath();
            } catch (IOException e) {
                logger.error("PDF file not found: {}", LogSafe.sanitize(pdfPathString)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            
            // Define allowed directories based on OSCAR configuration
            String[] allowedBasePaths = {
                CarlosProperties.getInstance().getProperty("DOCUMENT_DIR", "/var/lib/OscarDocument/"),
                CarlosProperties.getInstance().getProperty("TMP_DIR", "/tmp/"),
                CarlosProperties.getInstance().getProperty("eform_image", "/var/lib/OscarDocument/eform/images/"),
                System.getProperty("java.io.tmpdir")
            };

            boolean isValidPath = false;
            for (String basePath : allowedBasePaths) {
                if (basePath != null && !basePath.isEmpty()) {
                    java.io.File baseDir = new java.io.File(basePath);
                    if (baseDir.exists()) {
                        try {
                            canonicalPdfPath = PathValidationUtils.validateExistingPath(canonicalPdfPath.toFile(), baseDir).toPath();
                            isValidPath = true;
                            break;
                        } catch (SecurityException e) {
                            // File not in this directory, try next
                        }
                    }
                }
            }
            
            if (!isValidPath) {
                logger.error("Access denied: Path traversal attempt detected for path: {}", LogSafe.sanitize(pdfPathString)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            // Reject non-regular files (directories, devices) now that the path is validated.
            if (!Files.isRegularFile(canonicalPdfPath)) {
                logger.error("PDF path is not a regular file: {}", LogSafe.sanitize(pdfPathString)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Serve the validated PDF file
            response.setContentType("application/pdf");
            try (InputStream inputStream = Files.newInputStream(canonicalPdfPath);
                 BufferedInputStream bfis = new BufferedInputStream(inputStream);
                 ServletOutputStream outs = response.getOutputStream()) {

                int data;
                while ((data = bfis.read()) != -1) {
                    outs.write(data); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- application/pdf binary document preview
                }

                outs.flush();
            }
        } catch (IOException e) {
            logger.error("Error processing PDF file: {}", LogSafe.sanitize(pdfPathString), e);
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

        String demographicNo = StringUtils.isNullOrEmpty(request.getParameter("demographicNo")) ? "0" : request.getParameter("demographicNo");
        Integer demographicId;
        try {
            demographicId = Integer.valueOf(demographicNo);
        } catch (NumberFormatException e) {
            logger.warn("Invalid demographicNo received: {}. Falling back to 0.", LogSafe.sanitize(demographicNo), e);
            demographicNo = "0";
            demographicId = 0;
        }

        populateCommonDocs(loggedInInfo, demographicNo, demographicId);
		List<EFormData> allEForms = hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, null)
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

        String demographicNo = StringUtils.isNullOrEmpty(request.getParameter("demographicNo")) ? "0" : request.getParameter("demographicNo");
        String fdid = StringUtils.isNullOrEmpty(request.getParameter("fdid")) ? "0" : request.getParameter("fdid");
        Integer demographicId = parseIntegerParameterOrDefault(demographicNo, "demographicNo", 0);
        String sanitizedDemographicNo = String.valueOf(demographicId);
        Integer fdidInt = parseIntegerParameterOrDefault(fdid, "fdid", 0);

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
        ObjectNode json = objectMapper.createObjectNode();
        String base64Data = documentAttachmentManager.convertPDFToBase64(pdfPath);
        json.put("base64Data", base64Data);
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
     * @param errorMessage String the error message describing the PDF generation failure
     */
    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    private void generateResponse(HttpServletResponse response, String errorMessage) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("errorMessage", errorMessage);
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
        List<EDoc> allDocuments = hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, null)
                ? EDocUtil.listDocs(loggedInInfo, "demographic", demographicNo, null, EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE)
                : new ArrayList<>();
        ArrayList<HashMap<String, ? extends Object>> allHRMDocuments = hasPrivilege(loggedInInfo, "_hrm", SecurityInfoManager.READ, null)
                ? HRMUtil.listHRMDocuments(loggedInInfo, "report_date", false, demographicNo, false)
                : new ArrayList<>();
        List<AttachmentLabResultData> allLabsSortedByVersions = hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, null)
                ? documentAttachmentManager.getAllLabsSortedByVersions(loggedInInfo, demographicNo)
                : new ArrayList<>();
        List<EctFormData.PatientForm> allForms = hasPrivilege(loggedInInfo, "_form", SecurityInfoManager.READ, null)
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
            generateResponse(response, "Invalid " + parameterName);
            return null;
        }
    }
}
