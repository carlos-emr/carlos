/**
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.web;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhForm2Bean;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.business.BpmhForm2Handler;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.pdf.PDFController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhDrug;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.model.FormBPMH;

public class BpmhFormRetrieve2Action extends ActionSupport {
    private static final Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final String BPMH_PDF_TEMPLATE = "/WEB-INF/classes/oscar/form/prop/bpmh_template_marked.pdf";
    private BpmhForm2Handler bpmhFormHandler;

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        String method = request.getParameter("method");
        // Save and print both store the form (print saves a new one, formId 0, before streaming it), so they
        // come only as the form's POST, as the page sends them. Opening a form stays open to GET.
        if (("save".equals(method) || "print".equals(method)) && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            try {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            } catch (IOException e) {
                logger.error("BPMH method refusal failed", e);
            }
            return NONE;
        }
        if ("save".equals(method)) {
            return save(loggedInInfo);
        } else if ("print".equals(method)) {
            try {
                return print(loggedInInfo);
            } catch (Exception ex) {
                // No fallback to the page: print() may already have reset the response.
                logger.error("BPMH print failed", ex);
                try {
                    if (!response.isCommitted()) {
                        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    }
                } catch (IOException e) {
                    logger.error("BPMH print error response failed", e);
                }
                return NONE;
            }
        }
        return fetch(loggedInInfo);
    }

    public String fetch(LoggedInInfo loggedInInfo) {
        // Built here as save() and print() build theirs: the field was never set for fetch(), so
        // opening any BPMH form, new or saved, threw NullPointerException (#4731). Opening takes no
        // posted fields: everything shown comes from the patient's record or the saved form.
        bpmhFormHandler = newHandler(new BpmhForm2Bean());
        Integer demographicNo = Integer.parseInt(request.getParameter("demographic_no"));
        Integer formHistoryNumber = Integer.parseInt(request.getParameter("formId"));

        if (formHistoryNumber != null && formHistoryNumber > 0) {
            bpmhFormHandler.setFormHistory(formHistoryNumber);
        } else if (demographicNo != null) {
            bpmhFormHandler.setDemographicNo(demographicNo);
        }

        requirePatientRead(loggedInInfo, demographicNo);
        bpmhFormHandler.populateFormBean();
        if (formHistoryNumber == null || formHistoryNumber <= 0) {
            // A new form is id 0: the page offers Save only for formId 0, and print() parses the posted id.
            bpmhFormHandler.getBpmhFormBean().setFormId("0");
        }
        // The handler fills its own copy of the bean; formBPMH.jsp reads that copy under its Struts 1 bean name.
        request.setAttribute("bpmh", bpmhFormHandler.getBpmhFormBean());

        return SUCCESS;
    }

    /**
     * The fields the page edits, and nothing else the request may carry: which patient and form, the
     * note, the confirmation, and each drug row's id and instruction. Everything else on the form is
     * filled from the patient's record by the handler.
     */
    static BpmhForm2Bean editableFields(BpmhForm2Bean posted) {
        BpmhForm2Bean editable = new BpmhForm2Bean();
        if (posted == null) {
            return editable;
        }
        editable.setDemographicNo(posted.getDemographicNo());
        editable.setFormId(posted.getFormId());
        editable.setNote(posted.getNote());
        editable.setConfirm(posted.isConfirm());
        if (posted.getDrugs() != null) {
            List<BpmhDrug> rows = new ArrayList<>();
            for (BpmhDrug postedRow : posted.getDrugs()) {
                if (postedRow != null && postedRow.getId() != null) {
                    BpmhDrug row = new BpmhDrug();
                    row.setId(postedRow.getId());
                    row.setInstruction(postedRow.getInstruction());
                    rows.add(row);
                }
            }
            editable.setDrugs(rows);
        }
        return editable;
    }

    /** The form's handler; a seam so tests can stand in for its data-access lookups. */
    BpmhForm2Handler newHandler(BpmhForm2Bean bean) {
        return new BpmhForm2Handler(bean);
    }

    /**
     * Patient-scoped read check on the patient whose data the handler is about to load. The general
     * {@code _demographic} gate in {@link #execute()} passes no patient, so it cannot see a
     * patient-specific restriction ({@code _demographic$<demographicNo>}). A saved form carries its own
     * patient, which wins over the requested one when the handler loads it, so that is the patient
     * checked here.
     */
    private void requirePatientRead(LoggedInInfo loggedInInfo, Integer requestedDemographicNo) {
        FormBPMH saved = bpmhFormHandler.getFormHistory();
        int patient = saved != null ? saved.getDemographicNo() : requestedDemographicNo;
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", patient)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
    }

    // FindSecBugs UNVALIDATED_REDIRECT: the target is this application's own context path plus a literal
    // and two integer ids; no request string reaches it.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "same-application relative redirect; only integer ids are interpolated")
    public String save(LoggedInInfo loggedInInfo) {
        
        Integer demographicNo = Integer.parseInt(form.getDemographicNo());
        Integer formId = null;

        bpmhFormHandler = newHandler(editableFields(form));
        bpmhFormHandler.setDemographicNo(demographicNo);
        requirePatientRead(loggedInInfo, demographicNo);
        bpmhFormHandler.populateFormBean();
        // The handler saves its own copy of the bean, so the dates are set on that copy.
        Date now = new Date();
        bpmhFormHandler.getBpmhFormBean().setFormDate(now);
        bpmhFormHandler.getBpmhFormBean().setEditDate(now);

        formId = bpmhFormHandler.saveFormHistory();

        addActionMessage("Form Saved");


        StringBuilder actionRedirect = new StringBuilder("/formBPMH?method=fetch");
        actionRedirect.append("&demographic_no=").append(demographicNo);
        actionRedirect.append("&formId=").append(formId);
        actionRedirect.append("&provNo=");

        try {
            // A redirect path starting with "/" is relative to the server root, not this application.
            response.sendRedirect(request.getContextPath() + actionRedirect);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return NONE;
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public String print(LoggedInInfo loggedInInfo) throws IOException {

        OutputStream output = null;
        byte[] pdfContent = null;
        Integer demographicNo = Integer.parseInt(form.getDemographicNo());
        Integer formId = Integer.parseInt(form.getFormId());

        bpmhFormHandler = newHandler(editableFields(form));

        // form Id greater than zero means that this is a saved instance.
        if (formId > 0) {
            bpmhFormHandler.setFormHistory(formId);
        } else if (demographicNo != null) {
            bpmhFormHandler.setDemographicNo(demographicNo);
        }

        requirePatientRead(loggedInInfo, demographicNo);
        bpmhFormHandler.populateFormBean();

        PDFController pdfController = new PDFController(ServletActionContext.getServletContext().getRealPath(BPMH_PDF_TEMPLATE));
        pdfController.setOutputPath(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"));
        try {
            // The filled copy, not the posted form: the PDF needs the patient, provider and medication data.
            pdfController.writeDataToPDF(bpmhFormHandler.getBpmhFormBean(), new String[]{"1"}, demographicNo + "");

            // writeDataToPDF logs and swallows its own failures, so a name alone does not prove a PDF exists.
            // Read it before the form is saved: a failed print must not leave a saved form behind.
            File generatedPdf = PathValidationUtils.resolveTrustedPath(new File(pdfController.getOutputPath()));
            if (pdfController.getFileName() == null || !generatedPdf.isFile()) {
                throw new IOException("BPMH PDF was not generated");
            }
            try (FileInputStream pdfInput = new FileInputStream(generatedPdf)) {
                pdfContent = pdfInput.readAllBytes();
            }

            Date now = new Date();
            bpmhFormHandler.getBpmhFormBean().setEditDate(now);

            if (formId == 0) {
                bpmhFormHandler.getBpmhFormBean().setFormDate(now);
                bpmhFormHandler.saveFormHistory();
            }
        } finally {
            // The PDF is stamped to a file only to be streamed; a copy of the patient's medications left in
            // DOCUMENT_DIR serves nothing and is never cleaned up. The name is set once the file exists.
            if (pdfController.getFileName() != null) {
                Files.deleteIfExists(PathValidationUtils.resolveTrustedPath(new File(pdfController.getOutputPath())).toPath());
            }
        }

        response.reset();
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=" + pdfController.getFileName());
        output = response.getOutputStream();

        if (output != null) {
            output.write(pdfContent);
            output.close();
        }

        // The PDF is already written: no result may forward to a page after it.
        return NONE;
    }

    private BpmhForm2Bean form;

    // Depth 3 reaches form.drugs[i].instruction, the per-drug instructions the form posts.
    @StrutsParameter(depth = 3)
    public BpmhForm2Bean getForm() {
        return form;
    }

    @StrutsParameter
    public void setForm(BpmhForm2Bean form) {
        this.form = form;
    }
}
