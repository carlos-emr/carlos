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

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib prefix="csrf" uri="http://www.owasp.org/index.php/Category:OWASP_CSRFGuard_Project/Owasp.CsrfGuard.tld" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page
        import="java.util.*, ca.openosp.openo.util.*, ca.openosp.OscarProperties, ca.openosp.openo.utility.SpringUtils, ca.openosp.openo.commn.dao.CtlDocClassDao" %>
<%@ page import="ca.openosp.openo.documentManager.data.AddEditDocument2Form" %>
<%@ page import="ca.openosp.openo.documentManager.EDocUtil" %>
<%@ page import="ca.openosp.openo.util.UtilDateUtilities" %>
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

    OscarProperties props = OscarProperties.getInstance();

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
%>
<%--<script src="${ pageContext.request.contextPath }/share/javascript/Oscar.js" type="text/javascript"></script>--%>
<script type="text/javascript">

    // request attribute "linkhtmlerrors" & "docerrors" is used to check if a document was just submitted
    window.onload = function () {
        if ('${ linkhtmlerrors != null and not empty linkhtmlerrors}' === 'true') {
            showhide('addLinkDiv', 'plusminusLinkA');
        }
        if ('${ docerrors != null and not empty docerrors }' === 'true') {
            showhide('addDocDiv', 'plusminusAddDocA');
        }
        if ('${ param.mode eq "add"}' === 'true') {
            showhide('addDocDiv', 'plusminusAddDocA');
        }
    }

    function checkSel(sel) {
        theForm = sel.form;
        if ((theForm.docDesc.value === "") || (theForm.docDesc.value === "<%= defaultDesc%>")) {
            theForm.docDesc.value = theForm.docType.value;
            theForm.docDesc.focus();
            theForm.docDesc.select();
        }
    }

    function checkDefaultValue(object) {
        if ((object.value === "<%= defaultDesc%>") || (object.value === "<%= defaultType%>") || (object.value === "<%= defaultHtml %>")) {
            object.value = "";
        }
    }

    function showhide(hideelement, button) {
        var plus = '<i class="icon-plus"></i>';
        var minus = '<i class="icon-minus"></i>';
        if (document.getElementById) { // DOM3 = IE5, NS6
            if (document.getElementById(hideelement).style.display === 'none') {
                document.getElementById(hideelement).style.display = 'block';
                document.getElementById(button).innerHTML = document.getElementById(button).innerHTML.replace(plus, minus);
                if ((hideelement === "addDocDiv") && (document.getElementById("addLinkDiv").style.display !== "none")) {
                    showhide("addLinkDiv", "plusminusLinkA");
                } else if ((hideelement === "addLinkDiv") && (document.getElementById("addDocDiv").style.display !== "none")) {
                    showhide("addDocDiv", "plusminusAddDocA");
                }
            } else {
                document.getElementById(hideelement).style.display = 'none';
                document.getElementById(button).innerHTML = document.getElementById(button).innerHTML.replace(minus, plus);

            }
        }
    }

    function submitUpload(object) {
        object.Submit.disabled = true;
        if (!validDate("observationDate")) {
            alert("Invalid Date: must be in format: yyyy-mm-dd");
            object.Submit.disabled = false;
            return false;
        }
        return true;
    }

    function submitUploadLink(object) {
        object.Submit.disabled = true;
        return true;
    }

    //clears default values
    function checkDefaultDate(object, defaultValue) {
        if (object.value === defaultValue) {
            object.value = "";
        }
    }

    function newDocType() {
        var newOpt = prompt("Please enter new document type:", "");

        if (newOpt == null)
            return;

        if (newOpt !== "") {
            document.getElementById("docType").options[document.getElementById("docType").length] = new Option(newOpt, newOpt);
            document.getElementById("docType").options[document.getElementById("docType").length - 1].selected = true;

        } else {
            alert("Invalid entry");
        }

    }

    function newDocTypeLink() {
        var newOpt = prompt("Please enter new document type:", "");

        if (newOpt == null)
            return;

        if (newOpt !== "") {
            document.getElementById("docType1").options[document.getElementById("docType1").length] = new Option(newOpt, newOpt);
            document.getElementById("docType1").options[document.getElementById("docType1").length - 1].selected = true;

        } else {
            alert("Invalid entry");
        }

    }


</script>
<div class="add-document-wrapper">

    <div class="docHeading btn-group">
        <a class="btn" id="plusminusAddDocA" href="javascript: showhide('addDocDiv', 'plusminusAddDocA');">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.msgAddDocument"/>
        </a>
        <a class="btn" id="plusminusLinkA" href="javascript: showhide('addLinkDiv', 'plusminusLinkA')">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.AddLink"/>
        </a>
        <a class="btn" href="javascript:void(0);"
           onclick="popup1(450, 600, 'addedithtmldocument.jsp?function=<%=module%>&functionid=<%=moduleid%>&mode=addHtml', 'addhtml')">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.AddHTML"/>
        </a>
    </div>

    <div id="addDocDiv" class="addDocDiv well form-inline" style="display: none; padding:5px;">
        <form action="${pageContext.request.contextPath}/documentManager/addEditDocument.do" method="POST" enctype="multipart/form-data"
                   class="forms" onsubmit="return submitUpload(this)">

            <c:forEach var="error" items="${ docerrors }">
                <div class="alert alert-danger">
                    <strong>Error:</strong> <fmt:setBundle basename="oscarResources"/><fmt:message key="${error.value}"/>
                </div>
            </c:forEach>

            <input type="hidden" name="function" value="<%=Encode.forHtmlAttribute(formdata.getFunction())%>">
            <input type="hidden" name="functionId" value="<%=Encode.forHtmlAttribute(formdata.getFunctionId())%>">
            <input type="hidden" name="functionid" value="<%=Encode.forHtmlAttribute(moduleid)%>">
            <input type="hidden" name="parentAjaxId" value="<%=Encode.forHtmlAttribute(parentAjaxId)%>">
            <input type="hidden" name="curUser" value="<%=Encode.forHtmlAttribute(curUser)%>">
            <input type="hidden" name="appointmentNo" value="<%=Encode.forHtmlAttribute(formdata.getAppointmentNo())%>"/>

            <div class="form-group">
                <label for="docType">Type</label>
                <div class="input-group">
                    <select id="docType" class="form-control" name="docType">
                        <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.formSelect"/></option>
                        <%
                            for (int i = 0; i < doctypes.size(); i++) {
                                String doctype = (String) doctypes.get(i); %>
                        <option value="<%=Encode.forHtmlAttribute(doctype)%>"
                                <%=(formdata.getDocType().equals(doctype)) ? " selected" : ""%>><%=Encode.forHtmlContent(doctype)%>
                        </option>
                        <%}%>
                    </select>
                    <div class="input-group-btn btn-group">
                        <input id="docTypeinput" type="button" class="btn btn-default form-control"
                               onClick="newDocType();"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentEdit.formAddNewDocType"/>"/>
                    </div>
                </div>
            </div>

            <div class="form-group">
                <label for="docDesc">Description</label>
                <input type="text"
                       class="form-control <c:if test='${ docerrors["descmissing"] != null}' >alert-danger</c:if>"
                       id="docDesc" name="docDesc" value="<%=Encode.forHtmlAttribute(formdata.getDocDesc())%>"
                       onfocus="checkDefaultValue(this)"/>
                <input type="hidden" name="docCreator" value="<%=formdata.getDocCreator()%>"/>
            </div>
            <div class="form-group">
                <label for="observationDate" title="Observation Date">Observation Date</label>
                <input class="span2 form-control" type="date" name="observationDate" id="observationDate"
                       value="<%=formdata.getObservationDate()%>"
                       onclick="checkDefaultDate(this, '<%=UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd")%>')">
            </div>

            <div class="form-group">
                <label for="docClass"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.msgDocClass"/>:</label>
                <select name="docClass" id="docClass" class="form-control">
                    <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.formSelectClass"/></option>
                    <% boolean consult1Shown = false;
                        for (String reportClass : reportClasses) {
                            if (reportClass.startsWith("Consultant Report")) {
                                if (consult1Shown) continue;
                                reportClass = "Consultant Report";
                                consult1Shown = true;
                            }
                    %>
                    <option value="<%=reportClass%>"><%=reportClass%>
                    </option>
                    <% } %>
                </select>
            </div>
            <div class="form-group">
                <label for="docSubClass"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.msgDocSubClass"/>:</label>
                <input type="text" name="docSubClass" id="docSubClass" class="form-control">
                <div class="autocomplete_style" id="docSubClass_list"></div>
            </div>
            <div class="checkbox">
                <label>
                    <input type="checkbox" id="restrictToProgram" name="restrictToProgram">
                    Restrict to current program</label>
            </div>
            <% if (EDocUtil.isProviderModule(module)) {%>
            <div class="checkbox">
                <label>
                    <input type="checkbox" id="docPublic" name="docPublic" <%=formdata.getDocPublic() + " "%>
                           value="checked">
                    Public</label>
            </div>
            <% } %>
            <div class="form-group">
                <label for="docFile">Select Document</label>
                <div class="input-group">
                    <input type="file" name="docFile" id="docFile" class="form-control"
                           <c:if test="${ docerrors['uploaderror'] != null }">alert-danger</c:if>">

                    <input type="hidden" name="mode" value="add">
                    <div class="input-group-btn">
                        <input type="submit" name="Submit" value="Add" class="btn btn-primary">
                        <input type="button" name="Button" class="btn btn-error"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnCancel"/>"
                               onclick="window.location='documentReport.jsp?function=<%=module%>&functionid=<%=moduleid%>'">

                    </div>
                </div>
            </div>


        </form>
    </div>

    <div id="addLinkDiv" class="addDocDiv form-inline well" style="display: none;">

        <form action="${pageContext.request.contextPath}/documentManager/addLink.do" method="POST" class="forms"
                   onsubmit="return submitUploadLink(this)">
            <%-- Lists Errors --%>
        <c:forEach var="error" items="${ linkhtmlerrors }">
            <div class="alert alert-danger">
                <strong>Error:</strong> <fmt:setBundle basename="oscarResources"/><fmt:message key="${error.value}"/>
            </div>
        </c:forEach>

        <input type="hidden" name="function" value="<%=formdata.getFunction()%>">
        <input type="hidden" name="functionId" value="<%=formdata.getFunctionId()%>">
        <input type="hidden" name="functionid" value="<%=moduleid%>">
        <input type="hidden" name="observationDate" value="<%=formdata.getObservationDate()%>">
        <input type="hidden" name="appointmentNo" value="<%=formdata.getAppointmentNo()%>"/>

        <div class="form-group">
            <label for="docType1">Link Type</label>
            <div class="input-group">
                <select id="docType1" name="docType" class="form-control">
                    <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.formSelect"/></option>
                    <%
                        for (int i1 = 0; i1 < doctypes.size(); i1++) {
                            String doctype = (String) doctypes.get(i1); %>
                    <option value="<%= doctype%>"
                            <%=(formdata.getDocType().equals(doctype)) ? " selected" : ""%>><%= doctype%>
                    </option>
                    <%}%>
                </select>
                <div class="input-group-btn btn-group">
                    <input id="docTypeinput1" type="button" class="btn btn-default form-control"
                           onClick="newDocTypeLink();"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentEdit.formAddNewDocType"/>"/>
                </div>
            </div>
        </div>


        <div class="form-group">
            <label for="docDesc2">Description</label>
            <input type="text" name="docDesc" id="docDesc2"
                   class="form-control <c:if test="${ linkhtmlerrors['descmissing'] != null }">alert-danger</c:if>"
                   value="<%=formdata.getDocDesc()%>" onfocus="checkDefaultValue(this)">
        </div>

        <div class="form-group">
            <label for="docClassB"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.msgDocClass"/></label>
            <select name="docClass" id="docClassB" class="form-control">
                <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.formSelectClass"/></option>
                <% boolean consult2Shown = false;
                    for (String reportClass : reportClasses) {
                        if (reportClass.startsWith("Consultant Report")) {
                            if (consult2Shown) continue;
                            reportClass = "Consultant Report";
                            consult2Shown = true;
                        }
                %>
                <option value="<%=reportClass%>"><%=reportClass%>
                </option>
                <% } %>
            </select>
        </div>
        <div class="form-group">
            <label for="docSubClass2"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.msgDocSubClass"/></label>
            <input type="text" name="docSubClass" id="docSubClass2" class="form-control">
            <div class="autocomplete_style" id="docSubClass_list2"></div>
        </div>
        <% if (EDocUtil.isProviderModule(module)) {%>
        <div class="checkbox">
            <label>
                <input type="checkbox" name="docPublic" <%=formdata.getDocPublic() + " "%> value="checked">
                Public</label>
        </div>
        <% } %>

        <div class="form-group">
            <label for="html">Link</label>
            <div class="input-group">
                <input type="text" id="html" name="html" class="form-control"
                       value="<%=formdata.getHtml()%>" onfocus="checkDefaultValue(this)">
                <input type="hidden" name="docCreator"
                       value="<%=formdata.getDocCreator()%>">
                <input type="hidden" name="appointmentNo" value="<%=formdata.getAppointmentNo()%>"/>
                <div class="input-group-btn">
                    <input type="hidden" name="mode" value="addLink">
                    <input class="btn btn-primary" type="SUBMIT" name="Submit" value="Add">
                    <input class="btn" type="button" name="Button"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnCancel"/>"
                           onclick="window.location='documentReport.jsp?function=<%=module%>&functionid=<%=moduleid%>'">
                </div>
            </div>
            </form>

        </div>
    </div>

</div>