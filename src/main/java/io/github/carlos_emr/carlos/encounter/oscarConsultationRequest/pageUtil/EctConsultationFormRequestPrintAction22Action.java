/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

/*
 * EctConsultationFormRequestPrintAction.java
 *
 * Created on November 19, 2007, 4:05 PM
 */

package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.openpdf.text.DocumentException;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.consultation.ConsultationDemographicResolver;
import io.github.carlos_emr.carlos.consultation.ConsultationDemographicResolver.Resolution;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMPDFCreator;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.form.util.FormTransportContainer;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabPDFCreator;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

import com.sun.xml.messaging.saaj.util.ByteInputStream;
import com.sun.xml.messaging.saaj.util.ByteOutputStream;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action that generates a combined PDF of the consultation request with all
 * attached documents and streams it directly to the HTTP response for download.
 *
 * <p>Renders the consultation request form via {@link ConsultationPDFCreator}, then appends
 * all attached items as additional PDF pages:</p>
 * <ul>
 *   <li><b>eForms</b> - rendered via {@link FaxManager}</li>
 *   <li><b>Documents</b> - images converted via {@link ImagePDFCreator}, PDFs included directly</li>
 *   <li><b>Lab results</b> - rendered via {@link LabPDFCreator} with embedded documents</li>
 *   <li><b>HRM reports</b> - rendered via {@link HRMPDFCreator}</li>
 *   <li><b>Forms</b> - rendered via {@link FaxManager} with {@link FormTransportContainer}</li>
 * </ul>
 *
 * <p>All individual PDFs are concatenated using {@link ConcatPDF} and served as an inline
 * PDF download with a timestamped filename. Requires {@code _con} read privilege.</p>
 *
 * @see ConsultationPDFCreator
 * @see ImagePDFCreator
 * @see ConcatPDF
 * @since 2012-04-09
 */
public class EctConsultationFormRequestPrintAction22Action extends ActionSupport {
    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();
    private static final String ATTACHMENT_TYPE_EFORM = "EFORM";
    private static final String ATTACHMENT_TYPE_DOC = "DOC";
    private static final String ATTACHMENT_TYPE_LAB = "LAB";
    private static final String ATTACHMENT_TYPE_HRM = "HRM";
    private static final String ATTACHMENT_TYPE_FORM = "FORM";
    private static final String MISSING_ATTACHMENT_METADATA = "missing attachment metadata";
    private static final String MISSING_RENDERED_PDF = "missing rendered PDF";
    private static final String UNREADABLE_ATTACHMENT_FILE = "unreadable attachment file";
    private static final String UNREADABLE_TEMPORARY_PDF = "unreadable temporary PDF";
    private transient SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private transient ConsultationManager consultationManager = SpringUtils.getBean(ConsultationManager.class);
    private transient ConsultationRequestDao consultationRequestDao = SpringUtils.getBean(ConsultationRequestDao.class);

    private static FaxManager faxManager = SpringUtils.getBean(FaxManager.class);

    public EctConsultationFormRequestPrintAction22Action() {
    }

    /**
     * Generates and streams the combined consultation request PDF to the response.
     *
     * <p>Collects all attachment types (eForms, documents, labs, HRM reports, forms),
     * converts each to a PDF stream, concatenates them with the consultation form,
     * and writes the result directly to the HTTP response as an inline PDF attachment.</p>
     *
     * @return String null on success (response written directly), "error" on failure
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", "r", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        String reqId = (String) request.getAttribute("reqId");
        if (request.getParameter("reqId") != null) reqId = request.getParameter("reqId");

        String demoNo = resolveConsultationDemographicNo(reqId, request.getParameter("demographicNo"));
        if (demoNo == null) {
            request.setAttribute("printError", Boolean.valueOf(true));
            return "error";
        }
        ArrayList<EDoc> docs = EDocUtil.listDocs(loggedInInfo, demoNo, reqId, EDocUtil.ATTACHED);
        String path = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        if (!path.endsWith(File.separator)) {
            path = path + File.separator;
        }
        ArrayList<Object> alist = new ArrayList<Object>();
        byte[] buffer;
        ByteInputStream bis;
        ByteOutputStream bos;
        CommonLabResultData consultLabs = new CommonLabResultData();
        ArrayList<InputStream> streams = new ArrayList<InputStream>();

        ArrayList<LabResultData> labs = consultLabs.populateLabResultsData(loggedInInfo, demoNo, reqId, CommonLabResultData.ATTACHED);
        String error = "";
        Exception exception = null;
        try {

            bos = new ByteOutputStream();
            ConsultationPDFCreator cpdfc = new ConsultationPDFCreator(request, bos);
            cpdfc.printPdf(loggedInInfo);

            buffer = bos.getBytes();
            bis = new ByteInputStream(buffer, bos.getCount());
            bos.close();
            streams.add(bis);
            alist.add(bis);

            // attached eForms
            List<EFormData> eForms = consultationManager.getAttachedEForms(reqId);
            appendEFormAttachments(loggedInInfo, alist, streams, eForms, demoNo);

            //attached docs
            appendDocumentAttachments(alist, streams, docs, path);

            // Iterating over requested labs.
            appendLabAttachments(alist, streams, labs);

            // attached HRMs
            ArrayList<HashMap<String, ? extends Object>> attachedHRMDocuments = consultationManager.getAttachedHRMDocuments(loggedInInfo, demoNo, reqId);
            appendHRMAttachments(loggedInInfo, alist, streams, attachedHRMDocuments);

            // attached forms
            List<EctFormData.PatientForm> forms = consultationManager.getAttachedForms(loggedInInfo, Integer.parseInt(reqId), Integer.parseInt(demoNo));
            appendFormAttachments(loggedInInfo, alist, streams, forms, demoNo);

            if (alist.size() > 0) {

                bos = new ByteOutputStream();
                int skippedFiles = ConcatPDF.concat(alist, bos);
                if (skippedFiles > 0) {
                    logSkippedAttachment("MERGE", skippedFiles, "PDF merge skipped input files");
                }
                response.setContentType("application/pdf"); // octet-stream
                response.setHeader(
                        "Content-Disposition",
                        "inline; filename=\"combinedPDF-"
                                + UtilDateUtilities.getToday("yyyy-mm-dd.hh.mm.ss")
                                + ".pdf\"");
                response.getOutputStream().write(bos.getBytes(), 0, bos.getCount());
            }

        } catch (DocumentException de) {
            error = "DocumentException";
            exception = de;
        } catch (IOException ioe) {
            error = "IOException";
            exception = ioe;
        } catch (ServletException e) {
            throw new RuntimeException(e);
        } finally {
            // Cleaning up InputStreams created for concatenation.
            for (InputStream is : streams) {
                try {
                    is.close();
                } catch (IOException e) {
                    error = "IOException";
                }
            }
        }
        if (!error.equals("")) {
            logger.error(error + " occured insided ConsultationPrintAction", exception);
            request.setAttribute("printError", Boolean.valueOf(true));
            return "error";
        }
        return null;

    }

    private void addRenderedFaxAttachment(ArrayList<Object> alist, ArrayList<InputStream> streams, Path attachmentPath, String attachmentType, Object attachmentId) throws IOException {
        if (attachmentPath == null) {
            logSkippedAttachment(attachmentType, attachmentId, MISSING_RENDERED_PDF);
            return;
        }
        if (!Files.isReadable(attachmentPath)) {
            logSkippedAttachment(attachmentType, attachmentId, UNREADABLE_TEMPORARY_PDF);
            return;
        }

        InputStream inputStream = Files.newInputStream(attachmentPath);
        streams.add(inputStream);
        alist.add(inputStream);
    }

    private void appendDocumentAttachments(ArrayList<Object> alist, ArrayList<InputStream> streams, List<EDoc> docs, String documentDirectory) {
        for (EDoc doc : emptyIfNull(docs)) {
            if (doc == null) {
                logSkippedAttachment(ATTACHMENT_TYPE_DOC, null, MISSING_ATTACHMENT_METADATA);
                continue;
            }
            try {
                appendDocumentAttachment(alist, streams, doc, documentDirectory);
            } catch (SecurityException e) {
                throw e;
            } catch (DocumentException e) {
                logSkippedAttachment(ATTACHMENT_TYPE_DOC, documentId(doc), "document PDF conversion failed", e);
            } catch (IOException | RuntimeException e) {
                logSkippedAttachment(ATTACHMENT_TYPE_DOC, documentId(doc), e);
            }
        }
    }

    private void appendDocumentAttachment(ArrayList<Object> alist, ArrayList<InputStream> streams, EDoc doc, String documentDirectory) throws IOException, DocumentException {
        if (!doc.isPrintable()) {
            return;
        }

        File validatedFile = resolveReadableDocumentAttachmentFile(documentDirectory, doc);
        if (validatedFile == null) {
            return;
        }

        if (doc.isImage()) {
            try (ByteOutputStream outputStream = new ByteOutputStream()) {
                request.setAttribute("imagePath", validatedFile.getPath());
                request.setAttribute("imageTitle", doc.getDescription());
                ImagePDFCreator imagePDFCreator = new ImagePDFCreator(request, outputStream);
                imagePDFCreator.printPdf();
                addRenderedByteAttachment(alist, streams, outputStream);
            }
        } else if (doc.isPDF()) {
            alist.add(validatedFile.getPath());
        } else {
            if (logger.isErrorEnabled()) {
                logger.error("EctConsultationFormRequestPrintAction: {} is marked as printable but no means have been established to print it.",
                        LogSafe.sanitize(doc.getType()));
            }
        }
    }

    // FindSecBugs PATH_TRAVERSAL_IN: constructed path is immediately validated for
    // directory containment before readability check or use.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "path is validated for directory containment before readability check or use")
    private File resolveReadableDocumentAttachmentFile(String documentDirectory, EDoc doc) {
        File validatedFile = PathValidationUtils.validateExistingPath(
                new File(documentDirectory, doc.getFileName()), new File(documentDirectory));
        if (!Files.isReadable(validatedFile.toPath())) {
            logSkippedAttachment(ATTACHMENT_TYPE_DOC, documentId(doc), UNREADABLE_ATTACHMENT_FILE);
            return null;
        }
        return validatedFile;
    }

    private void appendLabAttachments(ArrayList<Object> alist, ArrayList<InputStream> streams, List<LabResultData> labs) {
        for (LabResultData lab : emptyIfNull(labs)) {
            if (lab == null) {
                logSkippedAttachment(ATTACHMENT_TYPE_LAB, null, MISSING_ATTACHMENT_METADATA);
                continue;
            }
            appendLabAttachment(alist, streams, lab);
        }
    }

    private void appendLabAttachment(ArrayList<Object> alist, ArrayList<InputStream> streams, LabResultData lab) {
        File tempLabPDF = null;
        try {
            tempLabPDF = PathValidationUtils.createSecureTempFile("lab-", ".pdf");

            // Defense-in-depth: verify temp file is in an allowed temp directory.
            if (!PathValidationUtils.isInAllowedTempDirectory(tempLabPDF)) {
                logger.error("Temp file not in allowed temp directory");
                throw new SecurityException("Temp file created outside allowed temp directory");
            }

            try (
                    FileOutputStream fileOutputStream = new FileOutputStream(tempLabPDF);
                    ByteOutputStream byteOutputStream = new ByteOutputStream()
            ) {
                request.setAttribute("segmentID", lab.segmentID);
                LabPDFCreator labPDFCreator = new LabPDFCreator(request, fileOutputStream);
                labPDFCreator.printPdf();
                labPDFCreator.addEmbeddedDocuments(tempLabPDF, byteOutputStream);
                addRenderedByteAttachment(alist, streams, byteOutputStream);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            logSkippedAttachment(ATTACHMENT_TYPE_LAB, lab.segmentID, e);
        } finally {
            deleteTemporaryLabPDF(tempLabPDF);
        }
    }

    private void deleteTemporaryLabPDF(File tempLabPDF) {
        if (tempLabPDF == null) {
            return;
        }
        try {
            Files.delete(tempLabPDF.toPath());
        } catch (NoSuchFileException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Temporary lab PDF was already removed");
            }
        } catch (IOException e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Could not remove temporary lab PDF", e);
            }
        }
    }

    private void appendHRMAttachments(LoggedInInfo loggedInInfo, ArrayList<Object> alist, ArrayList<InputStream> streams, List<HashMap<String, ? extends Object>> attachedHRMDocuments) {
        for (HashMap<String, ? extends Object> attachedHRMDocument : emptyIfNull(attachedHRMDocuments)) {
            Object hrmDocumentId = attachedHRMDocument == null ? null : attachedHRMDocument.get("id");
            if (!(hrmDocumentId instanceof Integer)) {
                logSkippedAttachment(ATTACHMENT_TYPE_HRM, hrmDocumentId, MISSING_ATTACHMENT_METADATA);
                continue;
            }
            try (ByteOutputStream outputStream = new ByteOutputStream()) {
                HRMPDFCreator hrmPdf = new HRMPDFCreator(outputStream, (Integer) hrmDocumentId, loggedInInfo);
                hrmPdf.printPdf();
                addRenderedByteAttachment(alist, streams, outputStream);
            } catch (SecurityException e) {
                throw e;
            } catch (RuntimeException e) {
                logSkippedAttachment(ATTACHMENT_TYPE_HRM, hrmDocumentId, e);
            }
        }
    }

    private void addRenderedByteAttachment(ArrayList<Object> alist, ArrayList<InputStream> streams, ByteOutputStream outputStream) {
        ByteInputStream inputStream = new ByteInputStream(outputStream.getBytes(), outputStream.getCount());
        streams.add(inputStream);
        alist.add(inputStream);
    }

    private void appendEFormAttachments(LoggedInInfo loggedInInfo, ArrayList<Object> alist, ArrayList<InputStream> streams, List<EFormData> eForms, String demoNo) {
        int renderDemographicNo;
        try {
            renderDemographicNo = Integer.parseInt(demoNo);
        } catch (NumberFormatException e) {
            logSkippedAttachment(ATTACHMENT_TYPE_EFORM, demoNo, "invalid consultation demographic number", e);
            return;
        }
        for (EFormData eFormItem : emptyIfNull(eForms)) {
            if (eFormItem == null) {
                logSkippedAttachment(ATTACHMENT_TYPE_EFORM, null, MISSING_ATTACHMENT_METADATA);
                continue;
            }
            try {
                Path attachedForm = faxManager.renderFaxDocument(loggedInInfo, FaxManager.TransactionType.EFORM, eFormItem.getId(), renderDemographicNo);
                addRenderedFaxAttachment(alist, streams, attachedForm, ATTACHMENT_TYPE_EFORM, eFormItem.getId());
            } catch (SecurityException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                logSkippedAttachment(ATTACHMENT_TYPE_EFORM, eFormItem.getId(), e);
            }
        }
    }

    private void appendFormAttachments(LoggedInInfo loggedInInfo, ArrayList<Object> alist, ArrayList<InputStream> streams, List<EctFormData.PatientForm> forms, String demoNo) throws ServletException {
        for (EctFormData.PatientForm formItem : emptyIfNull(forms)) {
            if (formItem == null) {
                logSkippedAttachment(ATTACHMENT_TYPE_FORM, null, MISSING_ATTACHMENT_METADATA);
                continue;
            }
            try {
                Path attachedForm = faxManager.renderFaxDocument(loggedInInfo, FaxManager.TransactionType.FORM, createFormTransportContainer(loggedInInfo, formItem, demoNo));
                addRenderedFaxAttachment(alist, streams, attachedForm, ATTACHMENT_TYPE_FORM, formItem.getFormId());
            } catch (SecurityException e) {
                throw e;
            } catch (IOException | ServletException | RuntimeException e) {
                logSkippedAttachment(ATTACHMENT_TYPE_FORM, formItem.getFormId(), e);
            }
        }
    }

    private FormTransportContainer createFormTransportContainer(LoggedInInfo loggedInInfo, EctFormData.PatientForm formItem, String demoNo) throws IOException, ServletException {
        FormTransportContainer formTransportContainer = new FormTransportContainer(
                response, request, "/form/forwardshortcutname"
                + "?method=fetch&formname="
                + encodeQueryValue(formItem.getFormName())
                + "&demographic_no="
                + encodeQueryValue(formItem.getDemoNo())
                + "&formId="
                + encodeQueryValue(formItem.getFormId()));
        formTransportContainer.setDemographicNo(demoNo);
        formTransportContainer.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        formTransportContainer.setSubject(formItem.getFormName() + " Form ID " + formItem.getFormId());
        formTransportContainer.setFormName(formItem.getFormName());
        formTransportContainer.setRealPath(ServletActionContext.getServletContext().getRealPath(File.separator));
        return formTransportContainer;
    }

    private String encodeQueryValue(String value) {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }

    private String resolveConsultationDemographicNo(String requestId, String submittedDemographicNo) {
        Resolution resolution = ConsultationDemographicResolver.resolve(consultationRequestDao, requestId,
                submittedDemographicNo, "print", logger);
        return resolution.isResolved() ? resolution.demographicId() : null;
    }

    private String documentId(EDoc doc) {
        return doc.getDocId() != null ? doc.getDocId() : doc.getFileName();
    }

    private void logSkippedAttachment(String attachmentType, Object attachmentId, String reason) {
        logSkippedAttachment(attachmentType, attachmentId, reason, null);
    }

    private void logSkippedAttachment(String attachmentType, Object attachmentId, Throwable cause) {
        String reason = cause == null ? "unknown error" : cause.getClass().getName();
        logSkippedAttachment(attachmentType, attachmentId, reason, cause);
    }

    private void logSkippedAttachment(String attachmentType, Object attachmentId, String reason, Throwable cause) {
        if (logger.isWarnEnabled()) {
            if (cause == null) {
                logger.warn("Skipped consultation print attachment type={} id={} while rendering PDF package: {}",
                        LogSafe.sanitize(attachmentType), LogSafe.sanitize(String.valueOf(attachmentId)), LogSafe.sanitize(reason));
            } else {
                logger.warn("Skipped consultation print attachment type={} id={} while rendering PDF package: {}",
                        LogSafe.sanitize(attachmentType), LogSafe.sanitize(String.valueOf(attachmentId)), LogSafe.sanitize(reason), cause);
            }
        }
    }

    private <T> List<T> emptyIfNull(List<T> source) {
        return source == null ? List.of() : source;
    }
}
