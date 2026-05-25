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
<%--
    addedithtmldocument.jsp

    Purpose: Provides a form for creating and editing HTML-based documents in the Document Manager.

    Features:
    - Bootstrap 5 styled form with validation
    - Dynamic population of document types, classes, and subclasses from database
    - HTML content editing via textarea
    - Reviewed button for existing documents (oldDoc=true)
    - OWASP-encoded outputs to prevent XSS

    Parameters:
    - editDocumentNo: Document ID for edit mode (optional; omit for new document)
    - function / functionid: Module context (demographic/provider) and entity ID
    - mode: Operation mode

    Security:
    - Requires _edoc write privilege
    - All user-visible outputs OWASP-encoded

    @since CARLOS 2026.03
--%>
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

<%@ page
        import="java.util.*, io.github.carlos_emr.*, io.github.carlos_emr.carlos.util.*, io.github.carlos_emr.carlos.providers.data.ProviderData, io.github.carlos_emr.carlos.utility.SpringUtils, io.github.carlos_emr.carlos.commn.dao.CtlDocClassDao" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.data.AddEditDocument2Form" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDocUtil" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDoc" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilMisc" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    String mode = "";
    if (request.getAttribute("mode") != null) {
        mode = (String) request.getAttribute("mode");
    } else if (request.getParameter("mode") != null) {
        mode = request.getParameter("mode");
    }

    String editDocumentNo = "";
    if (request.getAttribute("editDocumentNo") != null) {
        editDocumentNo = (String) request.getAttribute("editDocumentNo");
        mode = editDocumentNo;
    } else if (request.getParameter("editDocumentNo") != null) {
        editDocumentNo = request.getParameter("editDocumentNo");
        mode = editDocumentNo;
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

    OscarProperties props = OscarProperties.getInstance();
    String defaultType = props.getProperty("eDocAddTypeDefault", "");
    String defaultDesc = "Enter Title"; //if defaultType isn't defined, this value is used for the title/description

    Hashtable linkhtmlerrors = new Hashtable();
    if (request.getAttribute("linkhtmlerrors") != null) {
        linkhtmlerrors = (Hashtable) request.getAttribute("linkhtmlerrors");
    }

    String lastUpdate = "", fileName = "";
    boolean oldDoc = true;
    AddEditDocument2Form formdata = new AddEditDocument2Form();
    if (request.getAttribute("completedForm") != null) {
        formdata = (AddEditDocument2Form) request.getAttribute("completedForm");
        lastUpdate = EDocUtil.getDmsDateTime();
    } else if ((editDocumentNo != null) && (!editDocumentNo.isEmpty())) {
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
        formdata.setSource(currentDoc.getSource());
        formdata.setSourceFacility(currentDoc.getSourceFacility());
        formdata.setObservationDate(currentDoc.getObservationDate());
        formdata.setReviewerId(currentDoc.getReviewerId());
        formdata.setReviewDateTime(currentDoc.getReviewDateTime());
        formdata.setContentDateTime(UtilDateUtilities.DateToString(currentDoc.getContentDateTime(), EDocUtil.CONTENT_DATETIME_FORMAT));
        formdata.setHtml(UtilMisc.htmlEscape(currentDoc.getHtml()));
        lastUpdate = currentDoc.getDateTimeStamp();
        fileName = currentDoc.getFileName();
    } else {
        formdata.setFunction(module);  //"module" and "function" are the same
        formdata.setFunctionId(moduleid);
        formdata.setDocType(defaultType);
        formdata.setDocDesc(defaultType.equals("") ? defaultDesc : defaultType);
        formdata.setDocCreator(user_no);
        formdata.setObservationDate(UtilDateUtilities.DateToString(new Date(), "yyyy/MM/dd"));
        lastUpdate = "--";
        oldDoc = false;
    }

    List<Map<String, String>> pdList = new ProviderData().getProviderList();
    ArrayList<String> doctypes = EDocUtil.getDoctypes(module);
    String annotation_display = CaseManagementNoteLink.DISP_DOCUMENT;
    String annotation_tableid = editDocumentNo;
    Long now = new Date().getTime();
    String annotation_attrib = "anno" + now;

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

<!DOCTYPE HTML>

<html>
<head>
    <title>Edit Document</title>
    <%@ include file="/includes/global-head.jspf" %>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <script type="text/javascript">
        function submitUpload(object) {
            object.Submit.disabled = true;
            var ans = true;
            if (object.reviewerId.value !== "") {
                ans = confirm("Re-submitting this HTML will remove reviewer information. Confirm?");
            }
            if (ans) {
                var dateEl = document.getElementById("observationDate");
                if (!dateEl || dateEl.value.trim() === "") {
                    alert("Observation date is required");
                    ans = false;
                }
            }
            object.Submit.disabled = false;
            return ans;
        }

        function checkDefaultValue(object) {
            if ((object.value === "<%= defaultDesc%>") || (object.value === "<%= defaultType%>")) {
                object.value = "";
            }
        }

        function newDocType() {
            var newOpt = prompt("Please enter new document type:", "");
            if (newOpt === null) {
                return;
            }
            if (newOpt !== "") {
                document.getElementById("docType").options[document.getElementById("docType").length] = new Option(newOpt, newOpt);
                document.getElementById("docType").options[document.getElementById("docType").length - 1].selected = true;
            } else {
                alert("Invalid entry");
            }
        }
    </script>
</head>
<body style="padding: 10px;">

    <% for (Enumeration errorkeys = linkhtmlerrors.keys(); errorkeys.hasMoreElements(); ) {%>
    <div class="alert alert-danger">
        <strong>Error:</strong> <fmt:setBundle basename="oscarResources"/><fmt:message key="<%=(String) linkhtmlerrors.get(errorkeys.nextElement())%>"/>
    </div>
    <% } %>

    <form action="${pageContext.request.contextPath}/documentManager/addEditHtml.do" method="POST"
          enctype="multipart/form-data" onsubmit="return submitUpload(this);">
    <input type="hidden" name="function" value="<%=Encode.forHtmlAttribute(formdata.getFunction())%>"/>
    <input type="hidden" name="functionId" value="<%=Encode.forHtmlAttribute(formdata.getFunctionId())%>"/>
    <input type="hidden" name="functionid" value="<%=Encode.forHtmlAttribute(moduleid)%>"/>
    <input type="hidden" name="mode" value="<%=Encode.forHtmlAttribute(mode)%>"/>
    <input type="hidden" name="docCreator" value="<%=Encode.forHtmlAttribute(formdata.getDocCreator())%>"/>
    <input type="hidden" name="reviewerId" value="<%=Encode.forHtmlAttribute(formdata.getReviewerId())%>"/>
    <input type="hidden" name="reviewDateTime" value="<%=Encode.forHtmlAttribute(formdata.getReviewDateTime())%>"/>
    <input type="hidden" name="reviewDoc" value="false"/>

    <div class="row g-2 mb-2">
        <div class="col-auto">
            <label for="docType" class="form-label mb-0 small fw-bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.msgType"/></label>
            <div class="input-group input-group-sm">
                <select id="docType" name="docType" class="form-select form-select-sm">
                    <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.formSelect"/></option>
                    <% for (int i = 0; i < doctypes.size(); i++) {
                        String doctype = doctypes.get(i); %>
                    <option value="<%=Encode.forHtmlAttribute(doctype)%>" <%=(formdata.getDocType().equals(doctype)) ? " selected" : ""%>><%=Encode.forHtmlContent(doctype)%></option>
                    <%}%>
                </select>
                <button type="button" class="btn btn-outline-secondary btn-sm" onclick="newDocType();">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentEdit.formAddNewDocType"/>
                </button>
            </div>
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
            <label for="docDesc" class="form-label mb-0 small fw-bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.msgDescription"/></label>
            <input type="text" name="docDesc" id="docDesc" class="form-control form-control-sm<% if (linkhtmlerrors.containsKey("descmissing")) {%> is-invalid<%}%>"
                   onfocus="checkDefaultValue(this)" value="<%=Encode.forHtmlAttribute(formdata.getDocDesc())%>">
        </div>
        <div class="col-auto">
            <label for="observationDate" class="form-label mb-0 small fw-bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.msgObservationDate"/></label>
            <input type="date" name="observationDate" id="observationDate" class="form-control form-control-sm"
                   value="<%=Encode.forHtmlAttribute(formdata.getObservationDate().replace("/", "-"))%>">
        </div>
    </div>

    <% if (EDocUtil.isProviderModule(module)) {%>
    <div class="form-check mb-2">
        <input type="checkbox" class="form-check-input" name="docPublic" id="docPublic"
            <%=Encode.forHtmlAttribute(formdata.getDocPublic() + " ")%> value="checked">
        <label class="form-check-label small" for="docPublic"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.msgPublic"/></label>
    </div>
    <% } %>


    <div class="mb-2">
        <label for="htmlContent" class="form-label mb-0 small fw-bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.msgHtmlContent"/></label>
        <textarea name="html" id="htmlContent" class="form-control form-control-sm<% if (linkhtmlerrors.containsKey("uploaderror")) {%> is-invalid<%}%>"
                  rows="8" wrap="off"><%=Encode.forHtml(formdata.getHtml())%></textarea>
    </div>

    <div class="d-flex gap-2">
        <input class="btn btn-primary btn-sm" type="submit" name="Submit" value="Submit">
        <input class="btn btn-outline-secondary btn-sm" type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnCancel"/>"
               onclick="if (window.parent !== window) { window.parent.showhide('addHtmlDiv', 'plusminusHtmlA'); } else { window.close(); }">
    </div>

    </form>
</body>
</html>
