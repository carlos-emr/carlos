<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada

--%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<%@ page import="ca.openosp.openo.eform.data.*" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.encounter.data.EctFormData" %>
<%@ page import="ca.openosp.openo.commn.model.enumerator.DocumentType" %>
<%@ page import="ca.openosp.openo.documentManager.DocumentAttachmentManager" %>
<%@ page import="ca.openosp.openo.managers.EmailComposeManager" %>
<%@ page import="ca.openosp.openo.managers.SecurityInfoManager"%>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.util.StringUtils" %>
<%@ page import="java.util.List" %>
<%@ page import="ca.openosp.openo.eform.data.EForm" %>

<%!
    public void addHiddenEFormAttachments(LoggedInInfo loggedInInfo, EForm eForm, String eFormId) {
        Integer demographicNo = StringUtils.isInteger(eForm.getDemographicNo()) ? Integer.parseInt(eForm.getDemographicNo()) : null;
        Integer fdid = StringUtils.isInteger(eFormId) ? Integer.parseInt(eFormId) : null;
        if (demographicNo == null || fdid == null) {
            return;
        }

        DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);
        List<String> attachedDocumentIds = documentAttachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.DOC, demographicNo);
        List<String> attachedEFormIds = documentAttachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.EFORM, demographicNo);
        List<String> attachedHRMDocumentIds = documentAttachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.HRM, demographicNo);
        List<String> attachedLabIds = documentAttachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.LAB, demographicNo);
        List<EctFormData.PatientForm> attachedForms = documentAttachmentManager.getFormsAttachedToEForms(loggedInInfo, fdid, DocumentType.FORM, demographicNo);
        eForm.addHiddenAttachments(attachedDocumentIds, attachedEFormIds, attachedHRMDocumentIds, attachedLabIds, attachedForms);
    }

    public void addHiddenEmailProperties(LoggedInInfo loggedInInfo, EForm eForm, String demographicNo) {
        EmailComposeManager emailComposeManager = SpringUtils.getBean(EmailComposeManager.class);
        if (!emailComposeManager.hasEmailPrivilege(loggedInInfo, SecurityInfoManager.WRITE)) {
            eForm.addHiddenInputElement("hasEmailPrivilege", Boolean.FALSE.toString());
            return;
        }

        Boolean hasValidRecipient = emailComposeManager.hasValidRecipient(loggedInInfo, Integer.parseInt(demographicNo));
        String[] emailConsent = emailComposeManager.getEmailConsentStatus(loggedInInfo, Integer.parseInt(demographicNo));

        eForm.addHiddenInputElement("hasValidRecipient", Boolean.toString(hasValidRecipient));
        eForm.addHiddenInputElement("emailConsentName", emailConsent[0]);
        eForm.addHiddenInputElement("emailConsentStatus", emailConsent[1]);
    }
%>


<%
    /**
     * TODO: Move all JSP scriptlet code from efmshowform_data.jsp and efmformadd_data.jsp to the ShowEFormAction.java (create if necessary) action file.
     */
    String fid = request.getParameter("fid");
    String fdid = StringUtils.isNullOrEmpty(request.getParameter("fdid")) ? ((String) request.getAttribute("fdid")) : request.getParameter("fdid");
    String messageOnFailure = "No eform or appointment is available";
    EForm eForm = null;
    if (fid == null) {  // form exists in patient
        String appointmentNo = request.getParameter("appointment");
        String eformLink = request.getParameter("eform_link");

        eForm = new EForm(fdid);
        eForm.setContextPath(request.getContextPath());
        eForm.setOscarOPEN(request.getRequestURI());

        if (fdid != null) {
            eForm.setFdid(fdid);
        }

        if (appointmentNo != null) eForm.setAppointmentNo(appointmentNo);
        if (eformLink != null) eForm.setEformLink(eformLink);

        String parentAjaxId = request.getParameter("parentAjaxId");
        if (parentAjaxId != null) {
            eForm.setAction(parentAjaxId);
        } else {
            eForm.setAction();
        }
    } else {  //if form is viewed from admin screen
        eForm = new EForm(fid, "-1"); //form cannot be submitted, demographic_no "-1" indicate this specialty
        eForm.setContextPath(request.getContextPath());
        eForm.setupInputFields();
        eForm.setOscarOPEN(request.getRequestURI());
        eForm.setImagePath();
        eForm.setFdid("");
    }

    /*
     * Modifying EForm by directly incorporating libraries and adding hidden fields.
     * Ordering is very important.
     * For Javascript: First is last.
     */

    eForm.addHeadJavascript(request.getContextPath()+"/library/bootstrap/5.0.2/js/bootstrap.bundle.js");
    eForm.addHeadJavascript(request.getContextPath()+"/js/jquery.are-you-sure.js");
    eForm.addHeadJavascript(request.getContextPath()+"/library/jquery/jquery-ui-1.12.1.min.js");
    eForm.addHeadJavascript(request.getContextPath()+"/library/jquery/jquery-3.6.4.min.js");

    eForm.addCSS(request.getContextPath()+"/library/bootstrap/5.0.2/css/bootstrap.css", "all");
    eForm.addHeadJavascript(request.getContextPath()+"/library/bootstrap/5.0.2/js/bootstrap.bundle.js");

    eForm.addCSS(request.getContextPath()+"/css/oscar_alert.css", "all");
    eForm.addBodyJavascript(request.getContextPath()+"/js/oscar-alert.js");

    eForm.addCSS(request.getContextPath() + "/library/jquery/jquery-ui-1.12.1.min.css", "all");
    eForm.addBodyJavascript(request.getContextPath() + "/eform/eformFloatingToolbar/eform_floating_toolbar.js");
    eForm.addFontLibrary(request.getContextPath() + "/share/javascript/eforms/dejavufonts/ttf/DejaVuSans.ttf");
    eForm.addHiddenInputElement("context", request.getContextPath());
    eForm.addHiddenInputElement("demographicNo", eForm.getDemographicNo());
    eForm.addHiddenInputElement("fdid", fdid);
    eForm.addHiddenInputElement("fid", eForm.getFid());

    // Add EForm error message
    eForm.addHiddenInputElement("error", request.getParameter("error"));
    eForm.addHiddenInputElement("errorMessage", (String) request.getAttribute("errorMessage"));

    // Add EForm properties for handling download operation
    eForm.addHiddenInputElement("eFormPDFName", (String) request.getAttribute("eFormPDFName"));
    eForm.addHiddenInputElement("eFormPDF", (String) request.getAttribute("eFormPDF"));
    eForm.addHiddenInputElement("isDownloadEForm", (String) request.getAttribute("isDownload"));
    eForm.addHiddenInputElement("isSuccess_Autoclose", (String) request.getAttribute("isSuccess_Autoclose"));
    // Add EForm attachments
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    addHiddenEFormAttachments(loggedInInfo, eForm, fdid);

    // Add email consent properties
    addHiddenEmailProperties(loggedInInfo, eForm, eForm.getDemographicNo());

    out.print(eForm.getFormHtml());
%>
<script type="text/javascript" src="<%= request.getContextPath() %>/library/moment.js"></script>
