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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.openpdf.text.DocumentException;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMPDFCreator;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.OscarProperties;
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
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private ConsultationManager consultationManager = SpringUtils.getBean(ConsultationManager.class);

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
    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", "r", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        String reqId = (String) request.getAttribute("reqId");
        if (request.getParameter("reqId") != null) reqId = request.getParameter("reqId");

        String demoNo = request.getParameter("demographicNo");
        ArrayList<EDoc> docs = EDocUtil.listDocs(loggedInInfo, demoNo, reqId, EDocUtil.ATTACHED);
        String path = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
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

            for (EFormData eFormItem : eForms) {
                Path attachedForm = faxManager.renderFaxDocument(loggedInInfo, FaxManager.TransactionType.EFORM, eFormItem.getId(), eFormItem.getDemographicId());
                alist.add(Files.newInputStream(attachedForm));
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
            List<EctFormData.PatientForm> forms = consultationManager.getAttachedForms(loggedInInfo, Integer.parseInt(reqId), Integer.parseInt(demoNo));

            for (EctFormData.PatientForm formItem : forms) {
                FormTransportContainer formTransportContainer = new FormTransportContainer(
                        response, request, "/form/forwardshortcutname.jsp"
                        + "?method=fetch&formname="
                        + formItem.getFormName()
                        + "&demographic_no="
                        + formItem.getDemoNo()
                        + "&formId="
                        + formItem.getFormId());
                formTransportContainer.setDemographicNo(demoNo);
                formTransportContainer.setProviderNo(loggedInInfo.getLoggedInProviderNo());
                formTransportContainer.setSubject(formItem.getFormName() + " Form ID " + formItem.getFormId());
                formTransportContainer.setFormName(formItem.getFormName());
                formTransportContainer.setRealPath(ServletActionContext.getServletContext().getRealPath(File.separator));
                Path attachedForm = faxManager.renderFaxDocument(loggedInInfo, FaxManager.TransactionType.FORM, formTransportContainer);
                alist.add(Files.newInputStream(attachedForm));
            }

            if (alist.size() > 0) {

                bos = new ByteOutputStream();
                ConcatPDF.concat(alist, bos);
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
}
