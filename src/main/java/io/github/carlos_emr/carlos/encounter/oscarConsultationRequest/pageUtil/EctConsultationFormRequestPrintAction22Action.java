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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.openpdf.text.DocumentException;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.AttachmentOwnershipService;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMPDFCreator;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.form.gate.FormShortcutRouteResolver;
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
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private ConsultationManager consultationManager = SpringUtils.getBean(ConsultationManager.class);

    private static FaxManager faxManager = SpringUtils.getBean(FaxManager.class);

    private final AttachmentOwnershipService attachmentOwnershipService;

    private final ConsultationRequestDao consultationRequestDao;

    public EctConsultationFormRequestPrintAction22Action() {
        // Struts creates this legacy action with a no-arg constructor.
        this.attachmentOwnershipService = SpringUtils.getBean(AttachmentOwnershipService.class);
        this.consultationRequestDao = SpringUtils.getBean(ConsultationRequestDao.class);
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

        // Issue #3867: this route is reachable directly, so neither the demographicNo parameter nor
        // the consult_docs rows can be trusted. The patient comes from the stored consultation, and
        // every attachment looked up by consultation id is kept only when it is that patient's own:
        // a row written before attach-time ownership checks existed must not print another
        // patient's record. Labs are kept only as HL7 labs because LabPDFCreator resolves every
        // lab id as an HL7 segment. HRMs and forms below are already looked up per patient.
        Integer ownerDemographicNo = findConsultationDemographicNo(reqId);
        if (ownerDemographicNo == null) {
            logger.warn("Consultation print refused: the consultation request id is missing, malformed or unknown");
            request.setAttribute("printError", Boolean.TRUE);
            return "error";
        }
        String demoNo = String.valueOf(ownerDemographicNo);
        List<EDoc> docs = retainOwned(DocumentType.DOC, ownerDemographicNo,
                EDocUtil.listDocs(loggedInInfo, demoNo, reqId, EDocUtil.ATTACHED), EDoc::getDocId);
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

        List<LabResultData> labs = retainOwned(DocumentType.LAB, ownerDemographicNo,
                consultLabs.populateLabResultsData(loggedInInfo, demoNo, reqId, CommonLabResultData.ATTACHED),
                LabResultData::getSegmentID);
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
            List<EFormData> eForms = retainOwned(DocumentType.EFORM, ownerDemographicNo,
                    consultationManager.getAttachedEForms(reqId),
                    eForm -> eForm.getId() == null ? null : String.valueOf(eForm.getId()));

            for (EFormData eFormItem : eForms) {
                Path attachedForm;
                try {
                    attachedForm = faxManager.renderFaxDocument(loggedInInfo, FaxManager.TransactionType.EFORM, eFormItem.getId(), eFormItem.getDemographicId());
                } catch (PDFGenerationException e) {
                    // The renderer's message says why; only this loop knows which attachment.
                    throw new PDFGenerationException(
                            "Attached eForm \"" + eFormItem.getFormName() + "\" could not be rendered: " + e.getMessage(), e);
                }
                // Register in streams so the finally block below closes it; previously this
                // stream was created but never added to the cleanup list, leaking one file
                // descriptor per attached eForm on every print (see attached-forms site below
                // for the matching leak).
                InputStream attachedFormStream = Files.newInputStream(attachedForm);
                streams.add(attachedFormStream);
                alist.add(attachedFormStream);
            }

            //attached docs
            for (int i = 0; i < docs.size(); i++) {
                EDoc doc = docs.get(i);
                if (doc.isPrintable()) {
                    if (doc.isImage()) {
                        bos = new ByteOutputStream();
                        request.setAttribute("imagePath", path + doc.getFileName());
                        request.setAttribute("imageTitle", doc.getDescription());
                        ImagePDFCreator ipdfc = new ImagePDFCreator(request, bos);
                        ipdfc.printPdf();

                        buffer = bos.getBytes();
                        bis = new ByteInputStream(buffer, bos.getCount());
                        bos.close();
                        streams.add(bis);
                        alist.add(bis);

                    } else if (doc.isPDF()) {
                        alist.add(path + doc.getFileName());
                    } else {
                        logger.error("EctConsultationFormRequestPrintAction: " + doc.getType() + " is marked as printable but no means have been established to print it.");
                    }
                }
            }

            // Iterating over requested labs.
            for (int i = 0; labs != null && i < labs.size(); i++) {
                File tempLabPDF = File.createTempFile("lab" + labs.get(i).segmentID, "pdf");

                // Defense-in-depth: verify temp file is in an allowed temp directory
                if (!PathValidationUtils.isInAllowedTempDirectory(tempLabPDF)) {
                    logger.error("Temp file not in allowed temp directory: {}", tempLabPDF.getAbsolutePath());
                    tempLabPDF.delete();
                    throw new SecurityException("Temp file created outside allowed temp directory");
                }

                // Storing the lab in PDF format inside a byte stream.
                try (
                        FileOutputStream fileOutputStream = new FileOutputStream(tempLabPDF);
                        ByteOutputStream byteOutputStream = new ByteOutputStream();
                ) {
                    request.setAttribute("segmentID", labs.get(i).segmentID);
                    LabPDFCreator labPDFCreator = new LabPDFCreator(request, fileOutputStream);
                    labPDFCreator.printPdf();
                    labPDFCreator.addEmbeddedDocuments(tempLabPDF, byteOutputStream);

                    // Transferring PDF to an input stream to be concatenated with
                    // the rest of the documents.
                    buffer = byteOutputStream.getBytes();
                    bis = new ByteInputStream(buffer, byteOutputStream.getCount());
                    streams.add(bis);
                    alist.add(bis);
                }
                tempLabPDF.delete();
            }

            // attached HRMs
            ArrayList<HashMap<String, ? extends Object>> attachedHRMDocuments = consultationManager.getAttachedHRMDocuments(loggedInInfo, demoNo, reqId);
            for (HashMap<String, ? extends Object> attachedHRMDocument : attachedHRMDocuments) {
                bos = new ByteOutputStream();
                HRMPDFCreator hrmPdf = new HRMPDFCreator(bos, (Integer) attachedHRMDocument.get("id"), loggedInInfo);
                hrmPdf.printPdf();
                buffer = bos.getBytes();
                bis = new ByteInputStream(buffer, bos.getCount());
                bos.close();
                streams.add(bis);
                alist.add(bis);
            }

            // attached forms
            List<EctFormData.PatientForm> forms = consultationManager.getAttachedForms(loggedInInfo, Integer.parseInt(reqId.trim()), ownerDemographicNo);

            for (EctFormData.PatientForm formItem : forms) {
                InputStream attachedFormStream = renderFormAttachment(
                        loggedInInfo, request, response, faxManager, formItem);
                streams.add(attachedFormStream);
                alist.add(attachedFormStream);
            }

            if (alist.size() > 0) {

                bos = new ByteOutputStream();
                int skipped = ConcatPDF.concat(alist, bos);
                if (skipped > 0) {
                    // A document PDFBox could not parse was dropped — do not stream a consultation
                    // packet silently missing content; surface it via the error path below.
                    throw new IOException(skipped + " document(s) could not be included in the combined consultation PDF.");
                }
                if (response.isCommitted()) {
                    throw new IOException("Consultation print response committed before PDF output");
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
        } catch (PDFGenerationException pge) {
            // Attachment render failures land here with the failing attachment named in the
            // message (wrapped at the render call sites above) instead of the pre-fix behavior
            // of a context-free NullPointerException from Files.newInputStream(null).
            error = "PDFGenerationException";
            exception = pge;
        } finally {
            // Cleaning up InputStreams created for concatenation. A close failure here is
            // cleanup-only: the response bytes are already written (or the real failure was
            // captured above), so it must never flip a successful print to the "error" result —
            // Struts would forward an error JSP into the committed binary response.
            for (InputStream is : streams) {
                try {
                    is.close();
                } catch (IOException e) {
                    logger.warn("Failed to close attachment stream after consultation print", e);
                }
            }
        }
        if (!error.equals("")) {
            logger.error(error + " occured insided ConsultationPrintAction", exception);
            if (response.isCommitted()) {
                // The failure occurred after the PDF response was already committed (e.g. an
                // IOException mid-write from a client abort). Forwarding the "error" JSP now would
                // append HTML into the committed binary response; nothing more can be done safely.
                return NONE;
            }
            request.setAttribute("printError", Boolean.valueOf(true));
            return "error";
        }
        // Direct-response contract: the PDF bytes were streamed above; NONE (never a named
        // result or bare null) stops Struts from resolving a view into the binary response.
        return NONE;

    }

    /**
     * The patient the stored consultation request was written for, or {@code null} when the id is
     * missing, not a number or does not name a consultation.
     */
    private Integer findConsultationDemographicNo(String reqId) {
        if (reqId == null) {
            return null;
        }
        int requestId;
        try {
            requestId = Integer.parseInt(reqId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        ConsultationRequest consultationRequest = consultationRequestDao.find(requestId);
        return consultationRequest == null ? null : consultationRequest.getDemographicId();
    }

    /** Keeps the patient's own attachments; logs only how many were omitted, never ids. */
    private <T> List<T> retainOwned(DocumentType type, Integer demographicNo, List<T> attachments,
                                    Function<T, String> idOf) {
        List<T> retained = attachmentOwnershipService.retainOwned(type, demographicNo, attachments, idOf);
        int omitted = (attachments == null ? 0 : attachments.size()) - retained.size();
        if (omitted > 0) {
            logger.warn("Omitted {} consultation attachment(s) of type {} not owned by the consultation patient",
                    omitted, type.getType());
        }
        return retained;
    }

    private InputStream renderFormAttachment(
            LoggedInInfo loggedInInfo,
            HttpServletRequest request,
            HttpServletResponse response,
            FaxManager faxManager,
            EctFormData.PatientForm formItem) throws PDFGenerationException {
        if (formItem == null) {
            throw new PDFGenerationException(
                    "Attached form \"unknown\" could not be rendered: form metadata is missing");
        }
        String formName = formItem.getFormName();
        try {
            String formDemoNo = formItem.getDemoNo();
            String formId = formItem.getFormId();
            String formPath = FormShortcutRouteResolver.resolve(
                    formDemoNo, formName, formId, null, null);
            FormTransportContainer formTransportContainer = new FormTransportContainer(response, request, formPath);
            formTransportContainer.setDemographicNo(formDemoNo);
            formTransportContainer.setProviderNo(loggedInInfo.getLoggedInProviderNo());
            formTransportContainer.setSubject(formName + " Form ID " + formId);
            formTransportContainer.setFormName(formName);
            formTransportContainer.setRealPath(ServletActionContext.getServletContext().getRealPath(File.separator));
            Path attachedForm = faxManager.renderFaxDocument(
                    loggedInInfo, FaxManager.TransactionType.FORM, formTransportContainer);
            return Files.newInputStream(attachedForm);
        } catch (SQLException | IOException | ServletException | RuntimeException e) {
            throw new PDFGenerationException(
                    "Attached form \"" + formName + "\" could not be rendered: " + e.getMessage(), e);
        } catch (PDFGenerationException e) {
            throw new PDFGenerationException(
                    "Attached form \"" + formName + "\" could not be rendered: " + e.getMessage(), e);
        }
    }
}
