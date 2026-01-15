//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.form.pharmaForms.formBPMH.web;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.OscarProperties;
import ca.openosp.openo.form.pharmaForms.formBPMH.bean.BpmhForm2Bean;
import ca.openosp.openo.form.pharmaForms.formBPMH.business.BpmhForm2Handler;
import ca.openosp.openo.form.pharmaForms.formBPMH.pdf.PDFController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

public class BpmhFormRetrieve2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final String BPMH_PDF_TEMPLATE = "/WEB-INF/classes/oscar/form/prop/bpmh_template_marked.pdf";
    private BpmhForm2Handler bpmhFormHandler;

    public String execute() {
        String method = request.getParameter("method");
        if ("save".equals(method)) {
            return save();
        } else if ("print".equals(method)) {
            try {
                return print();
            } catch(Exception ex) {
                //
            }
        }
        return fetch();
    }

    public String fetch() {
        Integer demographicNo = Integer.parseInt(request.getParameter("demographic_no"));
        Integer formHistoryNumber = Integer.parseInt(request.getParameter("formId"));

        if (formHistoryNumber != null && formHistoryNumber > 0) {
            bpmhFormHandler.setFormHistory(formHistoryNumber);
        } else if (demographicNo != null) {
            bpmhFormHandler.setDemographicNo(demographicNo);
        }

        bpmhFormHandler.populateFormBean();

        return SUCCESS;
    }

    public String save() {
        
        Integer demographicNo = Integer.parseInt(form.getDemographicNo());
        Integer formId = null;

        bpmhFormHandler = new BpmhForm2Handler(form);
        bpmhFormHandler.setDemographicNo(demographicNo);
        bpmhFormHandler.populateFormBean();
        form.setEditDate(new Date());

        formId = bpmhFormHandler.saveFormHistory();

        addActionMessage("Form Saved");


        StringBuilder actionRedirect = new StringBuilder("/formBPMH.do?method=fetch");
        actionRedirect.append("&demographic_no=").append(demographicNo);
        actionRedirect.append("&formId=").append(formId);
        actionRedirect.append("&provNo=");

        try {
            response.sendRedirect(actionRedirect.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return NONE;
    }

    public String print() throws IOException {

        FileInputStream input = null;
        OutputStream output = null;
        byte[] pdfContent = null;
        Integer demographicNo = Integer.parseInt(form.getDemographicNo());
        Integer formId = Integer.parseInt(form.getFormId());

        bpmhFormHandler = new BpmhForm2Handler(form);

        // form Id greater than zero means that this is a saved instance.
        if (formId > 0) {
            bpmhFormHandler.setFormHistory(formId);
        } else if (demographicNo != null) {
            bpmhFormHandler.setDemographicNo(demographicNo);
        }

        bpmhFormHandler.populateFormBean();

        PDFController pdfController = new PDFController(ServletActionContext.getServletContext().getRealPath(BPMH_PDF_TEMPLATE));
        pdfController.setOutputPath(OscarProperties.getInstance().getProperty("DOCUMENT_DIR"));
        pdfController.writeDataToPDF(form, new String[]{"1"}, demographicNo + "");

        form.setEditDate(new Date());

        if (formId == 0) {
            bpmhFormHandler.saveFormHistory();
        }

        input = new FileInputStream(pdfController.getOutputPath());
        pdfContent = new byte[input.available()];
        input.read(pdfContent, 0, input.available());

        response.reset();
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=" + pdfController.getFileName());
        output = response.getOutputStream();

        if (output != null) {
            output.write(pdfContent);
            output.close();
        }

        if (input != null) {
            input.close();
        }

        return null;
    }

    private BpmhForm2Bean form;

    public BpmhForm2Bean getForm() {
        return form;
    }

    public void setForm(BpmhForm2Bean form) {
        this.form = form;
    }
}
