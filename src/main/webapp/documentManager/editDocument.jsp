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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@page import="org.apache.commons.lang3.StringUtils" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_edoc" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_edoc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    String user_no = (String) session.getAttribute("user");
    String userfirstname = (String) session.getAttribute("userfirstname");
    String userlastname = (String) session.getAttribute("userlastname");
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ page
        import="java.util.*, io.github.carlos_emr.carlos.util.*, io.github.carlos_emr.carlos.providers.data.ProviderData, io.github.carlos_emr.carlos.utility.SpringUtils, io.github.carlos_emr.carlos.commn.dao.CtlDocClassDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DocumentExtraReviewer" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DocumentExtraReviewerDao" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.data.AddEditDocument2Form" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDocUtil" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDoc" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%
    DocumentExtraReviewerDao documentExtraReviewerDao = SpringUtils.getBean(DocumentExtraReviewerDao.class);
    List<DocumentExtraReviewer> extraReviewers = new ArrayList<DocumentExtraReviewer>();

    String editDocumentNo = "";
    if (request.getAttribute("editDocumentNo") != null) {
        editDocumentNo = (String) request.getAttribute("editDocumentNo");
    } else {
        editDocumentNo = request.getParameter("editDocumentNo");
    }

    String module = "";
    String moduleid = "";
    if (request.getParameter("function") != null) {
        module = request.getParameter("function");
        moduleid = request.getParameter("functionid");
    } else if (request.getAttribute("function") != null) {
        module = (String) request.getAttribute("function");
        moduleid = (String) request.getAttribute("functionid");
    }

    Hashtable docerrors = new Hashtable();
    if (request.getAttribute("docerrors") != null) {
        docerrors = (Hashtable) request.getAttribute("docerrors");
    }

    String lastUpdate = "";
    String fileName = "";
    AddEditDocument2Form formdata = new AddEditDocument2Form();
    if (request.getAttribute("completedForm") != null) {
        formdata = (AddEditDocument2Form) request.getAttribute("completedForm");
        lastUpdate = EDocUtil.getDmsDateTime();
    } else if (editDocumentNo != null && !editDocumentNo.equals("")) {
        EDoc currentDoc = EDocUtil.getDoc(editDocumentNo);
        formdata.setFunction(currentDoc.getModule());
        formdata.setFunctionId(currentDoc.getModuleId());
        formdata.setDocType(currentDoc.getType());
        formdata.setDocClass(currentDoc.getDocClass());
        formdata.setDocSubClass(currentDoc.getDocSubClass());
        formdata.setDocDesc(currentDoc.getDescription());
        formdata.setDocPublic((currentDoc.getDocPublic().equals("1")) ? "checked" : "");
        formdata.setDocCreator(currentDoc.getCreatorId());
        formdata.setResponsibleId(currentDoc.getResponsibleId());
        formdata.setObservationDate(currentDoc.getObservationDate());
        formdata.setSource(currentDoc.getSource());
        formdata.setSourceFacility(currentDoc.getSourceFacility());
        formdata.setReviewerId(currentDoc.getReviewerId());
        formdata.setReviewDateTime(currentDoc.getReviewDateTime());
        formdata.setContentDateTime(UtilDateUtilities.DateToString(currentDoc.getContentDateTime(), EDocUtil.CONTENT_DATETIME_FORMAT));
        formdata.setRestrictToProgram(currentDoc.isRestrictToProgram());
        lastUpdate = currentDoc.getDateTimeStamp();
        fileName = currentDoc.getFileName();
        formdata.setAbnormal((currentDoc.getAbnormal().equals("true")) ? "checked" : "");
        formdata.setReceivedDate(currentDoc.getReceivedDate());

        extraReviewers = documentExtraReviewerDao.findByDocumentNo(Integer.parseInt(editDocumentNo));
    }

    List<Map<String, String>> pdList = new ProviderData().getProviderList();
    ArrayList doctypes = EDocUtil.getDoctypes(formdata.getFunction());
    String annotation_display = CaseManagementNoteLink.DISP_DOCUMENT;
    String annotation_tableid = editDocumentNo;

    CtlDocClassDao docClassDao = (CtlDocClassDao) SpringUtils.getBean(CtlDocClassDao.class);
    List<String> reportClasses = docClassDao.findUniqueReportClasses();
    ArrayList<String> subClasses = new ArrayList<String>();
    ArrayList<String> consultA = new ArrayList<String>();
    ArrayList<String> consultB = new ArrayList<String>();
    for (String reportClass : reportClasses) {
        List<String> subClassList = docClassDao.findSubClassesByReportClass(reportClass);
        if (reportClass.equals("Consultant ReportA")) consultA.addAll(subClassList);
        else if (reportClass.equals("Consultant ReportB")) consultB.addAll(subClassList);
        else subClasses.addAll(subClassList);

        if (!consultA.isEmpty() && !consultB.isEmpty()) {
            for (String partA : consultA) {
                for (String partB : consultB) {
                    subClasses.add(partA + " " + partB);
                }
            }
        }
    }
%>

<html>
<head>
    <title>Edit Document</title>
    <%@ include file="/includes/global-head.jspf" %>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <script type="text/javascript">
        function submitEdit(object) {
            object.Submit.disabled = true;
            var errors = [];
            var typeEl = document.getElementById('docType');
            var descEl = document.getElementById('docDesc');
            var dateEl = document.getElementById('observationDate');

            if (!typeEl || typeEl.value === "") {
                errors.push("- Document type is required");
                if (typeEl) typeEl.classList.add('is-invalid');
            } else if (typeEl) {
                typeEl.classList.remove('is-invalid');
            }

            if (!descEl || descEl.value.trim() === "") {
                errors.push("- Description is required");
                if (descEl) descEl.classList.add('is-invalid');
            } else if (descEl) {
                descEl.classList.remove('is-invalid');
            }

            if (!dateEl || dateEl.value.trim() === "") {
                errors.push("- Observation date is required");
                if (dateEl) dateEl.classList.add('is-invalid');
            } else if (dateEl) {
                dateEl.classList.remove('is-invalid');
            }

            if (errors.length > 0) {
                alert("Please fix the following:\n\n" + errors.join("\n"));
                object.Submit.disabled = false;
                return false;
            }
            return true;
        }
    </script>
</head>
<body style="padding: 10px;">

    <% for (Enumeration errorkeys = docerrors.keys(); errorkeys.hasMoreElements(); ) {%>
    <div class="alert alert-danger">
        <strong>Error:</strong> <fmt:setBundle basename="oscarResources"/><fmt:message key="<%=(String) docerrors.get(errorkeys.nextElement())%>"/>
    </div>
    <% } %>

    <form action="${pageContext.request.contextPath}/documentManager/addEditDocument.do" method="POST"
          enctype="multipart/form-data" onsubmit="return submitEdit(this);">
    <input type="hidden" name="function" value="<%=Encode.forHtmlAttribute(formdata.getFunction())%>"/>
    <input type="hidden" name="functionId" value="<%=Encode.forHtmlAttribute(formdata.getFunctionId())%>"/>
    <input type="hidden" name="functionid" value="<%=Encode.forHtmlAttribute(moduleid)%>"/>
    <input type="hidden" name="mode" value="<%=Encode.forHtmlAttribute(editDocumentNo)%>"/>
    <input type="hidden" name="reviewerId" value="<%=Encode.forHtmlAttribute(formdata.getReviewerId())%>"/>
    <input type="hidden" name="reviewDateTime" value="<%=Encode.forHtmlAttribute(formdata.getReviewDateTime())%>"/>
    <input type="hidden" name="reviewDoc" value="false"/>
    <input type="hidden" name="extraReviewerId" value=""/>
    <input type="hidden" name="extraReviewDoc" value="false"/>

    <div class="row g-2 mb-2">
        <div class="col-auto">
            <label for="docType" class="form-label mb-0 small fw-bold">Type</label>
            <select name="docType" id="docType" class="form-select form-select-sm<% if (docerrors.containsKey("typemissing")) {%> is-invalid<%}%>">
                <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.formSelect"/></option>
                <% for (int i = 0; i < doctypes.size(); i++) {
                    String doctype = (String) doctypes.get(i); %>
                <option value="<%=Encode.forHtmlAttribute(doctype)%>" <%=(formdata.getDocType().equals(doctype)) ? " selected" : ""%>><%=Encode.forHtmlContent(doctype)%></option>
                <%}%>
            </select>
        </div>
        <div class="col-auto">
            <label for="docClass" class="form-label mb-0 small fw-bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.msgDocClass"/></label>
            <select name="docClass" id="docClass" class="form-select form-select-sm">
                <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.formSelectClass"/></option>
                <% boolean consultShown = false;
                    for (String reportClass : reportClasses) {
                        if (reportClass.startsWith("Consultant Report")) {
                            if (consultShown) continue;
                            reportClass = "Consultant Report";
                            consultShown = true;
                        }
                %>
                <option value="<%=Encode.forHtmlAttribute(reportClass)%>" <%=reportClass.equals(formdata.getDocClass()) ? "selected" : ""%>><%=Encode.forHtmlContent(reportClass)%></option>
                <% } %>
            </select>
        </div>
        <div class="col-auto">
            <label for="docSubClass" class="form-label mb-0 small fw-bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.msgDocSubClass"/></label>
            <input type="text" name="docSubClass" id="docSubClass" class="form-control form-control-sm"
                   value="<%=Encode.forHtmlAttribute(formdata.getDocSubClass())%>">
        </div>
    </div>

    <div class="row g-2 mb-2">
        <div class="col-auto">
            <label for="docDesc" class="form-label mb-0 small fw-bold">Description</label>
            <input type="text" name="docDesc" id="docDesc" class="form-control form-control-sm<% if (docerrors.containsKey("descmissing")) {%> is-invalid<%}%>"
                   value="<%=Encode.forHtmlAttribute(formdata.getDocDesc())%>">
        </div>
        <div class="col-auto">
            <label for="observationDate" class="form-label mb-0 small fw-bold">Observation Date</label>
            <input type="date" name="observationDate" id="observationDate" class="form-control form-control-sm"
                   value="<%=Encode.forHtmlAttribute(formdata.getObservationDate().replace("/", "-"))%>">
        </div>
    </div>

    <div class="row g-2 mb-2">
        <div class="col-auto">
            <label class="form-label mb-0 small fw-bold">File</label>
            <div class="form-text small text-truncate" style="max-width: 300px;"><%=Encode.forHtml(fileName)%></div>
        </div>
        <div class="col-auto">
            <label class="form-label mb-0 small fw-bold">Added By</label>
            <div class="form-text small"><%=Encode.forHtml(EDocUtil.getProviderName(formdata.getDocCreator()))%></div>
        </div>
    </div>

    <% if (EDocUtil.isProviderModule(module)) {%>
    <div class="form-check mb-2">
        <input type="checkbox" class="form-check-input" name="docPublic" id="docPublic"
            <%=Encode.forHtmlAttribute(formdata.getDocPublic() + " ")%> value="checked">
        <label class="form-check-label small" for="docPublic">Public</label>
    </div>
    <%}%>

    <% boolean updatableContent = false; %>
    <oscar:oscarPropertiesCheck property="ALLOW_UPDATE_DOCUMENT_CONTENT" value="true" defaultVal="false">
        <% updatableContent = true; %>
    </oscar:oscarPropertiesCheck>
    <% if (updatableContent) { %>
    <div class="mb-2">
        <label class="form-label mb-0 small fw-bold">Replace File <span class="fw-normal text-muted">(blank to keep current)</span></label>
        <input type="file" name="docFile" class="form-control form-control-sm<% if (docerrors.containsKey("uploaderror")) {%> is-invalid<%}%>">
    </div>
    <% } %>

    <div class="d-flex gap-2">
        <input class="btn btn-primary btn-sm" type="submit" name="Submit" value="Update" <%=("".equals(editDocumentNo) ? "disabled" : "") %>>
        <input class="btn btn-outline-secondary btn-sm" type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnCancel"/>"
               onclick="window.close();">
    </div>

    </form>
</body>
</html>
