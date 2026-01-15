//CHECKSTYLE:OFF
/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */


package ca.openosp.openo.documentManager.actions;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Hashtable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.model.CaseManagementNoteLink;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.documentManager.EDoc;
import ca.openosp.openo.documentManager.EDocUtil;
import ca.openosp.openo.managers.ProgramManager2;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import ca.openosp.openo.util.UtilDateUtilities;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class AddEditHtml2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Creates a new instance of AddLinkAction
     */
    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        Hashtable errors = new Hashtable();
        String fileName = "";
        if (!EDocUtil.getDoctypes(this.getFunction()).contains(this.getDocType())) {
            EDocUtil.addDocTypeSQL(this.getDocType(), this.getFunction());
        }
        if ((this.getDocDesc().length() == 0) || (this.getDocDesc().equals("Enter Title"))) {
            errors.put("descmissing", "dms.error.descriptionInvalid");
            request.setAttribute("linkhtmlerrors", errors);
            request.setAttribute("completedForm", "");
            request.setAttribute("function", request.getParameter("function"));
            request.setAttribute("functionid", request.getParameter("functionid"));
            request.setAttribute("editDocumentNo", this.getMode());
            return "failed";
        }
        if (this.getDocType().length() == 0) {
            errors.put("typemissing", "dms.error.typeMissing");
            request.setAttribute("linkhtmlerrors", errors);
            request.setAttribute("completedForm", "");
            request.setAttribute("function", request.getParameter("function"));
            request.setAttribute("functionid", request.getParameter("functionid"));
            request.setAttribute("editDocumentNo", this.getMode());
            return "failed";
        }
        if (this.getHtml().length() == 0) {
            errors.put("urlmissing", "dms.error.htmlMissing");
            request.setAttribute("linkhtmlerrors", errors);
            request.setAttribute("completedForm", "");
            request.setAttribute("function", request.getParameter("function"));
            request.setAttribute("functionid", request.getParameter("functionid"));

            return "failed";
        }
        if (this.getMode().equals("addLink")) {
            //the 'html' variable is the url
            //checks for http://
            String html = this.getHtml();
            if (html.indexOf("http://") == -1) {
                html = "http://" + html;
            }
            html = "<script type=\"text/javascript\" language=\"Javascript\">\n" +
                    "window.location='" + html + "'\n" +
                    "</script>";
            this.setDocDesc(this.getDocDesc() + " (link)");
            this.setHtml(html);
            fileName = "link";
        } else if (this.getMode().equals("addHtml")) {
            fileName = "html";
        }

        String reviewerId = filled(this.getReviewerId()) ? this.getReviewerId() : "";
        String reviewDateTime = filled(this.getReviewDateTime()) ? this.getReviewDateTime() : "";

        if (!filled(reviewerId) && this.getReviewDoc()) {
            reviewerId = (String) request.getSession().getAttribute("user");
            reviewDateTime = UtilDateUtilities.DateToString(new Date(), EDocUtil.REVIEW_DATETIME_FORMAT);
        }
        EDoc currentDoc;
        MiscUtils.getLogger().debug("mode: " + this.getMode());
        if (this.getMode().indexOf("add") != -1) {
            currentDoc = new EDoc(this.getDocDesc(), this.getDocType(), fileName, this.getHtml(), this.getDocCreator(), this.getResponsibleId(), this.getSource(), 'H', this.getObservationDate(), reviewerId, reviewDateTime, this.getFunction(), this.getFunctionId());
            currentDoc.setContentType("text/html");
            currentDoc.setDocPublic(this.getDocPublic());
            currentDoc.setDocClass(this.getDocClass());
            currentDoc.setDocSubClass(this.getDocSubClass());

            // if the document was added in the context of a program
            ProgramManager2 programManager = SpringUtils.getBean(ProgramManager2.class);
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            ProgramProvider pp = programManager.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
            if (pp != null && pp.getProgramId() != null) {
                currentDoc.setProgramId(pp.getProgramId().intValue());
            }

            String docId = EDocUtil.addDocumentSQL(currentDoc);

            /* Save annotation */
            String attrib_name = request.getParameter("annotation_attrib");
            HttpSession se = request.getSession();
            WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(se.getServletContext());
            CaseManagementManager cmm = (CaseManagementManager) ctx.getBean(CaseManagementManager.class);
            if (attrib_name != null) {
                CaseManagementNote cmn = (CaseManagementNote) se.getAttribute(attrib_name);
                if (cmn != null) {
                    cmm.saveNoteSimple(cmn);
                    CaseManagementNoteLink cml = new CaseManagementNoteLink();
                    cml.setTableName(CaseManagementNoteLink.DOCUMENT);
                    cml.setTableId(Long.valueOf(docId));
                    cml.setNoteId(cmn.getId());
                    cmm.saveNoteLink(cml);

                    se.removeAttribute(attrib_name);
                }
            }
        } else {
            currentDoc = new EDoc(this.getDocDesc(), this.getDocType(), "", this.getHtml(), this.getDocCreator(), this.getResponsibleId(), this.getSource(), 'H', this.getObservationDate(), reviewerId, reviewDateTime, this.getFunction(), this.getFunctionId());
            currentDoc.setDocId(this.getMode());
            currentDoc.setContentType("text/html");
            currentDoc.setDocPublic(this.getDocPublic());
            currentDoc.setDocClass(this.getDocClass());
            currentDoc.setDocSubClass(this.getDocSubClass());
            EDocUtil.editDocumentSQL(currentDoc, this.getReviewDoc());
        }
        String contextPath = request.getContextPath();
        StringBuffer redirect = new StringBuffer(contextPath + "/documentManager/documentReport.jsp");
        redirect.append("?function=").append(request.getParameter("function"));
        redirect.append("&functionid=").append(request.getParameter("functionid"));
        try {
            response.sendRedirect(redirect.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return NONE;
    }

    private boolean filled(String s) {
        return (s != null && s.trim().length() > 0);
    }

    private String function = "";
    private String functionId = "";
    private String docType = "";
    private String docClass = "";
    private String docSubClass = "";
    private String docDesc = "";
    private String docCreator = "";
    private String responsibleId = "";
    private String source = "";
    private String sourceFacility = "";
    private File docFile;

    private File filedata;

    private String docPublic = "";
    private String mode = "";
    private String observationDate = "";
    private String reviewerId = "";
    private String reviewDateTime = "";
    private String contentDateTime = "";
    private boolean reviewDoc = false;
    private String html = "";

    private String appointmentNo = "0";

    private boolean restrictToProgram = false;
    private String receivedDate = "";
    private String abnormal = "";

    private String extraReviewerId = "";
    private boolean extraReviewDoc = false;

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public String getFunctionId() {
        return functionId;
    }

    public void setFunctionId(String functionId) {
        this.functionId = functionId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getDocClass() {
        return docClass;
    }

    public void setDocClass(String docClass) {
        this.docClass = docClass;
    }

    public String getDocSubClass() {
        return docSubClass;
    }

    public void setDocSubClass(String docSubClass) {
        this.docSubClass = docSubClass;
    }

    public String getDocDesc() {
        return docDesc;
    }

    public void setDocDesc(String docDesc) {
        this.docDesc = docDesc;
    }

    public String getDocCreator() {
        return docCreator;
    }

    public void setDocCreator(String docCreator) {
        this.docCreator = docCreator;
    }

    public String getResponsibleId() {
        return responsibleId;
    }

    public void setResponsibleId(String responsibleId) {
        this.responsibleId = responsibleId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceFacility() {
        return sourceFacility;
    }

    public void setSourceFacility(String sourceFacility) {
        this.sourceFacility = sourceFacility;
    }

    public File getDocFile() {
        return docFile;
    }

    public void setDocFile(File docFile) {
        this.docFile = docFile;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getDocPublic() {
        return docPublic;
    }

    public void setDocPublic(String docPublic) {
        this.docPublic = docPublic;
    }

    public String getObservationDate() {
        return observationDate;
    }

    public void setObservationDate(String observationDate) {
        this.observationDate = observationDate;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(String reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewDateTime() {
        return reviewDateTime;
    }

    public void setReviewDateTime(String reviewDateTime) {
        this.reviewDateTime = reviewDateTime;
    }

    public String getContentDateTime() {
        return contentDateTime;
    }

    public void setContentDateTime(String contentDateTime) {
        this.contentDateTime = contentDateTime;
    }

    public boolean getReviewDoc() {
        return reviewDoc;
    }

    public void setReviewDoc(boolean reviewDoc) {
        this.reviewDoc = reviewDoc;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public File getFiledata() {
        return filedata;
    }

    public void setFiledata(File Filedata) {
        this.filedata = Filedata;
    }

    public String getAppointmentNo() {
        return appointmentNo;
    }

    public void setAppointmentNo(String appointment) {
        this.appointmentNo = appointment;
    }

    public boolean isRestrictToProgram() {
        return restrictToProgram;
    }

    public void setRestrictToProgram(boolean restrictToProgram) {
        this.restrictToProgram = restrictToProgram;
    }

    public String getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(String receivedDate) {
        this.receivedDate = receivedDate;
    }

    public String getAbnormal() {
        return abnormal;
    }

    public void setAbnormal(String abnormal) {
        this.abnormal = abnormal;
    }

    public String getExtraReviewerId() {
        return extraReviewerId;
    }

    public void setExtraReviewerId(String extraReviewerId) {
        this.extraReviewerId = extraReviewerId;
    }

    public boolean isExtraReviewDoc() {
        return extraReviewDoc;
    }

    public void setExtraReviewDoc(boolean extraReviewDoc) {
        this.extraReviewDoc = extraReviewDoc;
    }
}
