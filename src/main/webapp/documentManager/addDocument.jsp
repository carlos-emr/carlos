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
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_edoc" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect("${ pageContext.request.contextPath }/securityError.jsp?type=_edoc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ page
        import="java.util.*, io.github.carlos_emr.carlos.util.*, io.github.carlos_emr.CarlosProperties, io.github.carlos_emr.carlos.utility.SpringUtils, io.github.carlos_emr.carlos.commn.dao.CtlDocClassDao" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.data.AddEditDocument2Form" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDocUtil" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%--This is included in documentReport.jsp - wasn't meant to be displayed as a separate page --%>
<%
    String user_no = (String) session.getAttribute("user");
    String appointment = request.getParameter("appointmentNo");

    String module = "";
    String moduleid = "";
    if (request.getParameter("function") != null) {
        module = request.getParameter("function");
        moduleid = request.getParameter("functionid");
    } else if (request.getAttribute("function") != null) {
        module = (String) request.getAttribute("function");
        moduleid = (String) request.getAttribute("functionid");
    }

    String curUser = "";
    if (request.getParameter("curUser") != null) {
        curUser = request.getParameter("curUser");
    } else if (request.getAttribute("curUser") != null) {
        curUser = (String) request.getAttribute("curUser");
    }

    CarlosProperties props = CarlosProperties.getInstance();

    AddEditDocument2Form formdata = new AddEditDocument2Form();
    formdata.setAppointmentNo(appointment);
    String defaultType = (String) props.getProperty("eDocAddTypeDefault", "");
    String defaultDesc = "Enter Title"; //if defaultType isn't defined, this value is used for the title/description
    String defaultHtml = "Enter Link URL";

    if (request.getParameter("defaultDocType") != null) {
        defaultType = request.getParameter("defaultDocType");
    }

//for "add document" link from the patient master page - the "mode" variable allows the add div to open up
    String mode = "";
    if (request.getAttribute("mode") != null) {
        mode = (String) request.getAttribute("mode");
    } else if (request.getParameter("mode") != null) {
        mode = request.getParameter("mode");
    }

//Retrieve encounter id for updating encounter navbar if info this page changes anything
    String parentAjaxId;
    if (request.getParameter("parentAjaxId") != null)
        parentAjaxId = request.getParameter("parentAjaxId");
    else if (request.getAttribute("parentAjaxId") != null)
        parentAjaxId = (String) request.getAttribute("parentAjaxId");
    else
        parentAjaxId = "";

    if (request.getAttribute("completedForm") != null) {
        formdata = (AddEditDocument2Form) request.getAttribute("completedForm");
    } else {
        formdata.setFunction(module);  //"module" and "function" are the same
        formdata.setFunctionId(moduleid);
        formdata.setDocType(defaultType);
        formdata.setDocDesc(defaultType.equals("") ? defaultDesc : defaultType);
        formdata.setDocCreator(user_no);
        formdata.setObservationDate(UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd"));
        formdata.setHtml(defaultHtml);
        formdata.setAppointmentNo(appointment);
    }
    ArrayList doctypes = EDocUtil.getActiveDocTypes(formdata.getFunction());

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

    // Determine which panel to open on load based on errors or mode
    boolean openDocPanel = (request.getAttribute("docerrors") != null) || "add".equals(mode);
    boolean openLinkPanel = (request.getAttribute("linkhtmlerrors") != null);
%>
<fmt:setBundle basename="oscarResources"/>
<script type="text/javascript">
    /* i18n strings for JavaScript use */
    var msgInvalidDate = '<fmt:message key="dms.addDocument.errorInvalidDate"/>';
    var msgInvalidEntry = '<fmt:message key="dms.addDocument.errorInvalidEntry"/>';
    var msgEnterNewDocType = '<fmt:message key="dms.addDocument.msgEnterNewDocType"/>';

    /**
     * Displays a dismissable Bootstrap alert-danger in the specified container element.
     * The message text is encoded via DOM textContent to prevent XSS.
     *
     * @param {string} containerId - The ID of the container element
     * @param {string} message     - The plain-text message to display
     */
    function showDocAlert(containerId, message) {
        var container = document.getElementById(containerId);
        if (!container) return;
        // Use textContent assignment to encode the message safely
        var msgNode = document.createTextNode(message);
        var alertDiv = document.createElement('div');
        alertDiv.className = 'alert alert-danger alert-dismissible fade show';
        alertDiv.setAttribute('role', 'alert');
        alertDiv.appendChild(msgNode);
        var closeBtn = document.createElement('button');
        closeBtn.type = 'button';
        closeBtn.className = 'btn-close';
        closeBtn.setAttribute('data-bs-dismiss', 'alert');
        closeBtn.setAttribute('aria-label', 'Close');
        alertDiv.appendChild(closeBtn);
        container.innerHTML = '';
        container.appendChild(alertDiv);
    }

    /**
     * Opens the correct accordion panel on page load based on server-side error flags or the 'mode' parameter.
     */
    document.addEventListener('DOMContentLoaded', function () {
        <% if (openLinkPanel) { %>
        bootstrap.Collapse.getOrCreateInstance(document.getElementById('addLinkDiv')).show();
        <% } %>
        <% if (openDocPanel) { %>
        bootstrap.Collapse.getOrCreateInstance(document.getElementById('addDocDiv')).show();
        <% } %>
    });

    function checkSel(sel) {
        theForm = sel.form;
        if ((theForm.docDesc.value === "") || (theForm.docDesc.value === "<%=Encode.forJavaScript(defaultDesc)%>")) {
            theForm.docDesc.value = theForm.docType.value;
            theForm.docDesc.focus();
            theForm.docDesc.select();
        }
    }

    function checkDefaultValue(object) {
        if ((object.value === "<%=Encode.forJavaScript(defaultDesc)%>")
                || (object.value === "<%=Encode.forJavaScript(defaultType)%>")
                || (object.value === "<%=Encode.forJavaScript(defaultHtml)%>")) {
            object.value = "";
        }
    }

    /**
     * Validates the observation date field before document form submission.
     * Shows a dismissable Bootstrap alert on invalid date format.
     *
     * @returns {boolean} true if the date is valid, false otherwise
     */
    function submitUpload(object) {
        object.Submit.disabled = true;
        if (!validDate("observationDate")) {
            showDocAlert('docAlertContainer', msgInvalidDate);
            object.Submit.disabled = false;
            return false;
        }
        return true;
    }

    function submitUploadLink(object) {
        object.Submit.disabled = true;
        return true;
    }

    // Clears default placeholder value from date field on focus
    function checkDefaultDate(object, defaultValue) {
        if (object.value === defaultValue) {
            object.value = "";
        }
    }

    /**
     * Prompts the user to enter a new document type and appends it to the docType select.
     * Shows a dismissable Bootstrap alert if the entry is blank.
     */
    function newDocType() {
        var newOpt = prompt(msgEnterNewDocType, "");
        if (newOpt == null)
            return;
        if (newOpt !== "") {
            document.getElementById("docType").options[document.getElementById("docType").length] = new Option(newOpt, newOpt);
            document.getElementById("docType").options[document.getElementById("docType").length - 1].selected = true;
        } else {
            showDocAlert('docAlertContainer', msgInvalidEntry);
        }
    }

    /**
     * Prompts the user to enter a new document type for the link form and appends it to docType1.
     * Shows a dismissable Bootstrap alert if the entry is blank.
     */
    function newDocTypeLink() {
        var newOpt = prompt(msgEnterNewDocType, "");
        if (newOpt == null)
            return;
        if (newOpt !== "") {
            document.getElementById("docType1").options[document.getElementById("docType1").length] = new Option(newOpt, newOpt);
            document.getElementById("docType1").options[document.getElementById("docType1").length - 1].selected = true;
        } else {
            showDocAlert('linkAlertContainer', msgInvalidEntry);
        }
    }
</script>

<div class="add-document-wrapper mb-2" id="addDocAccordion">

    <div class="docHeading btn-group mb-2">
        <button type="button" class="btn btn-secondary"
                data-bs-toggle="collapse" data-bs-target="#addDocDiv"
                aria-expanded="false" aria-controls="addDocDiv">
            <fmt:message key="dms.addDocument.msgAddDocument"/>
        </button>
        <button type="button" class="btn btn-secondary"
                data-bs-toggle="collapse" data-bs-target="#addLinkDiv"
                aria-expanded="false" aria-controls="addLinkDiv">
            <fmt:message key="dms.addDocument.AddLink"/>
        </button>
        <button type="button" class="btn btn-secondary"
                onclick="popup1(450, 600, 'addedithtmldocument.jsp?function=<%=Encode.forUriComponent(module)%>&functionid=<%=Encode.forUriComponent(moduleid)%>&mode=addHtml', 'addhtml')">
            <fmt:message key="dms.addDocument.AddHTML"/>
        </button>
    </div>

    <div id="addDocDiv" class="collapse card card-body bg-body-tertiary mb-1" data-bs-parent="#addDocAccordion">
        <form action="${pageContext.request.contextPath}/documentManager/addEditDocument.do" method="POST"
              enctype="multipart/form-data" class="forms" onsubmit="return submitUpload(this)">

            <div id="docAlertContainer"></div>

            <c:forEach var="error" items="${ docerrors }">
                <div class="alert alert-danger alert-dismissible fade show" role="alert">
                    <strong><fmt:message key="global.error"/>:</strong> <fmt:message key="${error.value}"/>
                    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                </div>
            </c:forEach>

            <input type="hidden" name="function" value="<%=Encode.forHtmlAttribute(formdata.getFunction())%>">
            <input type="hidden" name="functionId" value="<%=Encode.forHtmlAttribute(formdata.getFunctionId())%>">
            <input type="hidden" name="functionid" value="<%=Encode.forHtmlAttribute(moduleid)%>">
            <input type="hidden" name="parentAjaxId" value="<%=Encode.forHtmlAttribute(parentAjaxId)%>">
            <input type="hidden" name="curUser" value="<%=Encode.forHtmlAttribute(curUser)%>">
            <input type="hidden" name="appointmentNo" value="<%=Encode.forHtmlAttribute(formdata.getAppointmentNo())%>"/>

            <div class="mb-3">
                <label for="docType"><fmt:message key="dms.addDocument.labelType"/></label>
                <div class="input-group">
                    <select id="docType" class="form-select" name="docType">
                        <option value=""><fmt:message key="dms.addDocument.formSelect"/></option>
                        <%
                            for (int i = 0; i < doctypes.size(); i++) {
                                String doctype = (String) doctypes.get(i); %>
                        <option value="<%=Encode.forHtmlAttribute(doctype)%>"
                                <%=(formdata.getDocType().equals(doctype)) ? " selected" : ""%>><%=Encode.forHtmlContent(doctype)%>
                        </option>
                        <%}%>
                    </select>
                    <button type="button" id="docTypeinput" class="btn btn-secondary"
                            onclick="newDocType();">
                        <fmt:message key="dms.documentEdit.formAddNewDocType"/>
                    </button>
                </div>
            </div>

            <div class="mb-3">
                <label for="docDesc"><fmt:message key="dms.addDocument.labelDescription"/></label>
                <input type="text"
                       class="form-control<c:if test='${ docerrors["descmissing"] != null}'> is-invalid</c:if>"
                       id="docDesc" name="docDesc" value="<%=Encode.forHtmlAttribute(formdata.getDocDesc())%>"
                       onfocus="checkDefaultValue(this)"/>
                <input type="hidden" name="docCreator" value="<%=Encode.forHtmlAttribute(formdata.getDocCreator())%>"/>
            </div>

            <div class="mb-3">
                <label for="observationDate"><fmt:message key="dms.addDocument.labelObservationDate"/></label>
                <input class="form-control" type="date" name="observationDate" id="observationDate"
                       value="<%=Encode.forHtmlAttribute(formdata.getObservationDate())%>"
                       onclick="checkDefaultDate(this, '<%=Encode.forJavaScriptAttribute(UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd"))%>')">
            </div>

            <div class="mb-3">
                <label for="docClass"><fmt:message key="dms.addDocument.msgDocClass"/>:</label>
                <select name="docClass" id="docClass" class="form-select">
                    <option value=""><fmt:message key="dms.addDocument.formSelectClass"/></option>
                    <% boolean consult1Shown = false;
                        for (String reportClass : reportClasses) {
                            if (reportClass.startsWith("Consultant Report")) {
                                if (consult1Shown) continue;
                                reportClass = "Consultant Report";
                                consult1Shown = true;
                            }
                    %>
                    <option value="<%=Encode.forHtmlAttribute(reportClass)%>"><%=Encode.forHtmlContent(reportClass)%>
                    </option>
                    <% } %>
                </select>
            </div>

            <div class="mb-3">
                <label for="docSubClass"><fmt:message key="dms.addDocument.msgDocSubClass"/>:</label>
                <input type="text" name="docSubClass" id="docSubClass" class="form-control">
                <div class="autocomplete_style" id="docSubClass_list"></div>
            </div>

            <div class="form-check mb-2">
                <input type="checkbox" class="form-check-input" id="restrictToProgram" name="restrictToProgram">
                <label class="form-check-label" for="restrictToProgram">
                    <fmt:message key="dms.addDocument.labelRestrictToProgram"/>
                </label>
            </div>

            <% if (EDocUtil.isProviderModule(module)) {%>
            <div class="form-check mb-2">
                <input type="checkbox" class="form-check-input" id="docPublic" name="docPublic"
                       <%=formdata.getDocPublic() + " "%> value="checked">
                <label class="form-check-label" for="docPublic">
                    <fmt:message key="dms.addDocument.labelPublic"/>
                </label>
            </div>
            <% } %>

            <div class="mb-3">
                <label for="docFile"><fmt:message key="dms.addDocument.labelSelectDocument"/></label>
                <input type="file" name="docFile" id="docFile"
                       class="form-control<c:if test="${ docerrors['uploaderror'] != null }"> is-invalid</c:if>">
            </div>

            <div class="d-flex gap-2">
                <input type="hidden" name="mode" value="add">
                <input type="submit" name="Submit" class="btn btn-primary"
                       value="<fmt:message key='dms.addDocument.btnAdd'/>">
                <input type="button" name="Button" class="btn btn-warning"
                       value="<fmt:message key='global.btnCancel'/>"
                       onclick="window.location='documentReport.jsp?function=<%=Encode.forUriComponent(module)%>&functionid=<%=Encode.forUriComponent(moduleid)%>'">
            </div>
        </form>
    </div>

    <div id="addLinkDiv" class="collapse card card-body bg-body-tertiary" data-bs-parent="#addDocAccordion">
        <form action="${pageContext.request.contextPath}/documentManager/addLink.do" method="POST" class="forms"
              onsubmit="return submitUploadLink(this)">

            <div id="linkAlertContainer"></div>

            <%-- Lists Errors --%>
            <c:forEach var="error" items="${ linkhtmlerrors }">
                <div class="alert alert-danger alert-dismissible fade show" role="alert">
                    <strong><fmt:message key="global.error"/>:</strong> <fmt:message key="${error.value}"/>
                    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                </div>
            </c:forEach>

            <input type="hidden" name="function" value="<%=Encode.forHtmlAttribute(formdata.getFunction())%>">
            <input type="hidden" name="functionId" value="<%=Encode.forHtmlAttribute(formdata.getFunctionId())%>">
            <input type="hidden" name="functionid" value="<%=Encode.forHtmlAttribute(moduleid)%>">
            <input type="hidden" name="observationDate" value="<%=Encode.forHtmlAttribute(formdata.getObservationDate())%>">
            <input type="hidden" name="appointmentNo" value="<%=Encode.forHtmlAttribute(formdata.getAppointmentNo())%>"/>
            <input type="hidden" name="docCreator" value="<%=Encode.forHtmlAttribute(formdata.getDocCreator())%>">

            <div class="mb-3">
                <label for="docType1"><fmt:message key="dms.addDocument.labelLinkType"/></label>
                <div class="input-group">
                    <select id="docType1" name="docType" class="form-select">
                        <option value=""><fmt:message key="dms.addDocument.formSelect"/></option>
                        <%
                            for (int i1 = 0; i1 < doctypes.size(); i1++) {
                                String doctype = (String) doctypes.get(i1); %>
                        <option value="<%=Encode.forHtmlAttribute(doctype)%>"
                                <%=(formdata.getDocType().equals(doctype)) ? " selected" : ""%>><%=Encode.forHtmlContent(doctype)%>
                        </option>
                        <%}%>
                    </select>
                    <button type="button" id="docTypeinput1" class="btn btn-secondary"
                            onclick="newDocTypeLink();">
                        <fmt:message key="dms.documentEdit.formAddNewDocType"/>
                    </button>
                </div>
            </div>

            <div class="mb-3">
                <label for="docDesc2"><fmt:message key="dms.addDocument.labelDescription"/></label>
                <input type="text" name="docDesc" id="docDesc2"
                       class="form-control<c:if test="${ linkhtmlerrors['descmissing'] != null }"> is-invalid</c:if>"
                       value="<%=Encode.forHtmlAttribute(formdata.getDocDesc())%>" onfocus="checkDefaultValue(this)">
            </div>

            <div class="mb-3">
                <label for="docClassB"><fmt:message key="dms.addDocument.msgDocClass"/></label>
                <select name="docClass" id="docClassB" class="form-select">
                    <option value=""><fmt:message key="dms.addDocument.formSelectClass"/></option>
                    <% boolean consult2Shown = false;
                        for (String reportClass : reportClasses) {
                            if (reportClass.startsWith("Consultant Report")) {
                                if (consult2Shown) continue;
                                reportClass = "Consultant Report";
                                consult2Shown = true;
                            }
                    %>
                    <option value="<%=Encode.forHtmlAttribute(reportClass)%>"><%=Encode.forHtmlContent(reportClass)%>
                    </option>
                    <% } %>
                </select>
            </div>

            <div class="mb-3">
                <label for="docSubClass2"><fmt:message key="dms.addDocument.msgDocSubClass"/></label>
                <input type="text" name="docSubClass" id="docSubClass2" class="form-control">
                <div class="autocomplete_style" id="docSubClass_list2"></div>
            </div>

            <% if (EDocUtil.isProviderModule(module)) {%>
            <div class="form-check mb-2">
                <input type="checkbox" class="form-check-input" id="docPublicLink" name="docPublic"
                       <%=formdata.getDocPublic() + " "%> value="checked">
                <label class="form-check-label" for="docPublicLink">
                    <fmt:message key="dms.addDocument.labelPublic"/>
                </label>
            </div>
            <% } %>

            <div class="mb-3">
                <label for="html"><fmt:message key="dms.addDocument.labelLink"/></label>
                <div class="input-group">
                    <input type="text" id="html" name="html" class="form-control"
                           value="<%=Encode.forHtmlAttribute(formdata.getHtml())%>" onfocus="checkDefaultValue(this)">
                    <input type="hidden" name="mode" value="addLink">
                    <input class="btn btn-primary" type="submit" name="Submit"
                           value="<fmt:message key='dms.addDocument.btnAdd'/>">
                </div>
            </div>

            <div class="d-flex gap-2 mb-2">
                <input class="btn btn-warning" type="button" name="Button"
                       value="<fmt:message key='global.btnCancel'/>"
                       onclick="window.location='documentReport.jsp?function=<%=Encode.forUriComponent(module)%>&functionid=<%=Encode.forUriComponent(moduleid)%>'">
            </div>

        </form>
    </div>

</div>
