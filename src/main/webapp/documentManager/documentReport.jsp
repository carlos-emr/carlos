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
    documentReport.jsp

    Purpose: Main Document Manager page; lists all documents associated with a patient
    or provider module in a searchable, filterable DataTable.

    Features:
    - Bootstrap 5 page-header-bar with FontAwesome icons and patient info bar
    - DataTables integration with merged Category/Type column and date sorting
    - Action buttons: Edit, Annotate, Delete/Undelete (CSRF-protected dynamic forms)
    - Inline "Add Document" and "Add Link" panels (from addDocument.jsp include)
    - OWASP-encoded JS string contexts and onclick attributes to prevent XSS
    - NumberFormatException guard on appointmentNo parameter

    Request Parameters:
    - function: Module context (demographic / provider)
    - functionid: Module entity ID
    - appointmentNo: Optional appointment reference (invalid values default to 0)
    - parentAjaxId: Optional encounter navbar AJAX refresh target

    Security:
    - Requires _edoc read privilege
    - CSRF token injected into all dynamic POST forms

    @since CARLOS 2026.03
--%>
<!DOCTYPE html>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO, io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    if (session.getValue("user") == null) response.sendRedirect("${ pageContext.request.contextPath }/logout.htm");
    if (session.getAttribute("userrole") == null)
        response.sendRedirect("${ pageContext.request.contextPath }/logout.jsp");
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    String user_no = (String) session.getAttribute("user");
    String demographicNo = (String) session.getAttribute("casemgmt_DemoNo");

    String annotation_display = CaseManagementNoteLink.DISP_DOCUMENT;
    String appointment = request.getParameter("appointmentNo");
    int appointmentNo = 0;
    if (appointment != null && !appointment.isEmpty()) {
        try {
            appointmentNo = Integer.parseInt(appointment);
        } catch (NumberFormatException e) {
            appointmentNo = 0;
        }
    }

%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>
<%@ taglib uri="/WEB-INF/oscarProperties-tag.tld" prefix="oscarProp" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CtlDocClassDao" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDocUtil" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDoc" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>


<%
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_edoc,_admin,_admin.edocdelete" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect("${ pageContext.request.contextPath }/securityError.jsp?type=_edoc&type=_admin&type=_admin.edocdelete");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }

    UserPropertyDAO pref = SpringUtils.getBean(UserPropertyDAO.class);

//if delete request is made
    if (request.getParameter("delDocumentNo") != null) {
        EDocUtil.deleteDocument(request.getParameter("delDocumentNo"));
    }

//if undelete request is made
    if (request.getParameter("undelDocumentNo") != null) {
        EDocUtil.undeleteDocument(request.getParameter("undelDocumentNo"));
    }

//view  - tabs
    String view = "all";
    if (request.getParameter("view") != null) {
        view = request.getParameter("view");
    } else if (request.getAttribute("view") != null) {
        view = (String) request.getAttribute("view");
    }
//preliminary JSP code

// "Module" and "function" is the same thing (old documentManager module)
    String module = "";
    String moduleid = "";
    if (request.getParameter("function") != null) {
        module = request.getParameter("function");
        moduleid = request.getParameter("functionid");
    } else if (request.getAttribute("function") != null) {
        module = (String) request.getAttribute("function");
        moduleid = (String) request.getAttribute("functionid");
    }

    if (!"".equalsIgnoreCase(moduleid) && (demographicNo == null || demographicNo.equalsIgnoreCase("null"))) {
        demographicNo = moduleid;
    }

// module can be "demographic", "provider", or "providers" (use EDocUtil.isProviderModule() for provider checks)

    String moduleName = "";
    if ("demographic".equals(module)) {
        moduleName = EDocUtil.getDemographicName(loggedInInfo, moduleid);
    } else if (EDocUtil.isProviderModule(module)) {
        moduleName = EDocUtil.getProviderName(moduleid);
    }


    String curUser = LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo();
    ArrayList<String> doctypes = EDocUtil.getActiveDocTypes(module);

//Retrieve encounter id for updating encounter navbar if info this page changes anything
    String parentAjaxId;
    if (request.getParameter("parentAjaxId") != null)
        parentAjaxId = request.getParameter("parentAjaxId");
    else if (request.getAttribute("parentAjaxId") != null)
        parentAjaxId = (String) request.getAttribute("parentAjaxId");
    else
        parentAjaxId = "";


    String updateParent;
    if (request.getParameter("updateParent") != null)
        updateParent = request.getParameter("updateParent");
    else
        updateParent = "false";

    String viewstatus = request.getParameter("viewstatus");
    if (viewstatus == null) {
        viewstatus = "active";
    }


    UserProperty up = pref.getProp(user_no, UserProperty.EDOC_BROWSER_IN_DOCUMENT_REPORT);
    boolean DocumentBrowserLink = false;

    if (up != null && up.getValue() != null && up.getValue().equals("yes")) {
        DocumentBrowserLink = true;
    }
%>
<html>
    <head>

        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgDocuments"/> Manager</title>

        <%@ include file="/includes/global-head.jspf" %>
        <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/css/jquery.dataTables.css"
              rel="stylesheet" type="text/css"/>

        <script src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"
                type="text/javascript"></script>
        <script type="text/javascript"
                src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.js"></script>
        <%
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

        <script>
            $(function () {
                var docSubClassList = [
                    <% for (int i=0; i<subClasses.size(); i++) { %>
                    "<%=Encode.forJavaScript(subClasses.get(i))%>"<%=(i<subClasses.size()-1)?",":""%>
                    <% } %>
                ];
                $("#docSubClass").autocomplete({
                    source: docSubClassList
                });
            });

            var awnd = null;

            function popPage(url) {
                awnd = rs('', url, 400, 200, 1);
                awnd.focus();
            }


            function checkDelete(docId, func, funcId, viewStatus, docDescription) {
// revision Apr 05 2004 - we now allow anyone to delete documents
                if (confirm("<fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgDelete"/> " + docDescription)) {
                    submitDocAction('delDocumentNo', docId, func, funcId, viewStatus);
                }
            }

            /** Creates a dynamic POST form to submit a document action (delete/unfile) to documentReport.jsp. */
            function submitDocAction(paramName, docId, func, funcId, viewStatus) {
                var form = document.createElement('form');
                form.method = 'post';
                form.action = 'documentReport.jsp';
                var fields = {};
                fields[paramName] = docId;
                fields['function'] = func;
                fields['functionid'] = funcId;
                fields['viewstatus'] = viewStatus;
                var existingToken = document.querySelector('input[name="CSRF-TOKEN"]');
                if (existingToken) {
                    fields['CSRF-TOKEN'] = existingToken.value;
                }
                for (var key in fields) {
                    var input = document.createElement('input');
                    input.type = 'hidden';
                    input.name = key;
                    input.value = fields[key];
                    form.appendChild(input);
                }
                document.body.appendChild(form);
                form.submit();
            }

            function popup1(height, width, url, windowName) {
                windowprops = "height=" + height + ",width=" + width + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                var popup = window.open(url, windowName, windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                }
                popup.focus()

            }

            function setup() {
                var update = "<%=Encode.forJavaScript(updateParent)%>";
                var parentId = "<%=Encode.forJavaScript(parentAjaxId)%>";
                var Url = window.opener.URLs;

                if (update === "true" && !window.opener.closed) {
                    window.opener.popLeftColumn(Url[parentId], parentId, parentId);
                }
            }

            window.closeWindow = function() {
                if (window.opener) {
                    window.close();
                } else {
                    try {
                        open(location, '_self').close();
                    } catch(e) {
                        history.back();
                    }
                }
            }

            jQuery(document).ready(function () {
                jQuery("table[id^='tblDocs']").DataTable({
                    ordering: true,
                    columnDefs: [{orderable: false, targets: [4]}],
                    lengthMenu: [
                        [-1, 10, 20, 50, 100, 200],
                        ['All', 10, 20, 50, 100, 200]
                    ],
                    order: [[3, 'dsc']],
                    "language": {
                        "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                    }
                });
            });
        </script>

        <style>
            .filter-bar {
                background: var(--carlos-bg-light);
                border: 1px solid var(--carlos-border);
                border-radius: 4px;
                padding: 10px 15px;
                margin-bottom: 12px;
            }
            .patient-info-bar {
                background: #fff;
                border-bottom: 1px solid var(--carlos-border);
                padding: 8px 15px;
                margin-bottom: 12px;
                display: flex;
                align-items: center;
                justify-content: space-between;
                flex-wrap: wrap;
                gap: 8px;
            }
            .doc-table td a {
                word-break: break-word;
                overflow-wrap: anywhere;
            }
            .doc-table th, .doc-table td {
                vertical-align: middle;
            }
            .doc-actions {
                white-space: nowrap;
            }
            .doc-actions .btn-link {
                font-size: 14px;
            }
            .col-actions {
                width: 1%;
                white-space: nowrap;
            }
            .col-date {
                white-space: nowrap;
            }
        </style>

    </head>
    <body>

    <div class="container-fluid p-0">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <i class="fas fa-file-lines me-2"></i>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgDocuments"/> Manager
            </h4>
        </div>

        <div class="px-3">

        <div class="patient-info-bar">
            <div>
                <% if ("demographic".equals(module)) { %>
                <oscar:nameage demographicNo="<%=Encode.forHtmlAttribute(moduleid)%>"/>
                <%} %>
            </div>
        </div>

        <jsp:include page="addDocument.jsp">
            <jsp:param name="appointmentNo" value="<%=appointmentNo%>"/>
            <jsp:param name="addDocument" value="<%=Encode.forHtmlAttribute(request.getParameter(\"mode\") != null ? request.getParameter(\"mode\") : \"\")%>"/>
        </jsp:include>


            <div class="documentLists"><%-- STUFF TO DISPLAY --%> <%
                ArrayList categories = new ArrayList();
                ArrayList categoryKeys = new ArrayList();

                MiscUtils.getLogger().debug("module=" + module + ", moduleid=" + moduleid + ", view=" + view + ", EDocUtil.PRIVATE=" + EDocUtil.PRIVATE + ", viewstatus=" + viewstatus);
                ArrayList<EDoc> privatedocs = EDocUtil.listDocs(loggedInInfo, module, moduleid, view, EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE, viewstatus);
                MiscUtils.getLogger().debug("privatedocs:" + privatedocs.size());

                categories.add(privatedocs);
                categoryKeys.add(moduleName + "'s Private Documents");
                if (EDocUtil.isProviderModule(module)) {
                    ArrayList publicdocs = EDocUtil.listDocs(loggedInInfo, module, moduleid, view, EDocUtil.PUBLIC, EDocUtil.EDocSort.OBSERVATIONDATE, viewstatus);
                    categories.add(publicdocs);
                    categoryKeys.add("Public Documents");
                }

                for (int i = 0; i < categories.size(); i++) {
                    String currentkey = (String) categoryKeys.get(i);
                    ArrayList category = (ArrayList) categories.get(i);
            %>
                <div class="doclist mb-3">
                    <div class="filter-bar">
                        <div class="row g-2 align-items-center">
                            <div class="col-auto fw-bold">
                                <%= Encode.forHtmlContent(currentkey) %>
                            </div>

                            <% if (i == 0) {%>
                            <div class="col-auto">
                                <select class="form-select form-select-sm" id="viewstatus" name="viewstatus"
                                        onchange="var val = encodeURIComponent(this.options[this.selectedIndex].value); window.location.href='?function=<%=Encode.forUriComponent(module)%>&functionid=<%=Encode.forUriComponent(moduleid)%>&view=<%=Encode.forUriComponent(view)%>&viewstatus=' + val;">
                                    <option value="all"
                                            <%=viewstatus.equalsIgnoreCase("all") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgAll"/></option>
                                    <option value="deleted"
                                            <%=viewstatus.equalsIgnoreCase("deleted") ? "selected" : ""%>>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgDeleted"/></option>
                                    <option value="active"
                                            <%=viewstatus.equalsIgnoreCase("active") ? "selected" : ""%>>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgPublished"/></option>
                                </select>
                            </div>
                            <%}%>
                            <div class="col-auto">
                                <select id="viewdoctype<%=i%>" name="view"
                                        class="form-select form-select-sm"
                                        onchange="var val = encodeURIComponent(this.options[this.selectedIndex].value); window.location.href='?function=<%=Encode.forUriComponent(module)%>&functionid=<%=Encode.forUriComponent(moduleid)%>&view=' + val;">
                                    <option value="">All</option>
                                    <%
                                        for (int i3 = 0; i3 < doctypes.size(); i3++) {
                                            String doctype = (String) doctypes.get(i3); %>
                                    <option value="<%= Encode.forHtmlAttribute(doctype)%>"
                                            <%=(view.equalsIgnoreCase(doctype)) ? "selected" : ""%>><%= Encode.forHtmlContent(doctype)%>
                                    </option>
                                    <%}%>

                                </select>
                            </div>
                            <%if (DocumentBrowserLink) {%>
                            <div class="col-auto">
                                <a class="btn btn-outline-secondary btn-sm"
                                    href="${ pageContext.request.contextPath }/documentManager/documentBrowser.jsp?function=<%=Encode.forUriComponent(module)%>&functionid=<%=Encode.forUriComponent(moduleid)%>&categorykey=<%=Encode.forUri(currentkey)%>">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgBrowser"/>
                                </a>
                            </div>
                            <%}%>
                        </div>
                    </div>

                    <div id="documentsInnerDiv<%=i%>" class="table-responsive">
                        <table id="tblDocs<%=i%>" class="table table-hover table-sm table-bordered doc-table">
                            <thead class="table-light">
                            <tr>
                                <th>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgDocDesc"/>
                                </th>
                                <th>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgContent"/>
                                </th>
                                <th>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgCreator"/>
                                </th>
                                <th class="col-date">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgDate"/>
                                </th>
                                <th class="col-actions text-end">Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            <%
                                for (int i2 = 0; i2 < category.size(); i2++) {
                                    EDoc curdoc = (EDoc) category.get(i2);
                                    //content type (take everything following '/')
                                    int slash = 0;
                                    String contentType = "";
                                    if (curdoc.getContentType() == null || curdoc.getContentType().isEmpty()) {
                                        contentType = "ukn";
                                    } else if ((slash = curdoc.getContentType().indexOf('/')) != -1) {
                                        contentType = curdoc.getContentType().substring(slash + 1);
                                    } else {
                                        contentType = curdoc.getContentType();
                                    }
                                    // remove punctuation
                                    contentType = contentType.replaceAll("\\p{Punct}", "");

                                    // truncate to save space
                                    if (contentType.length() > 3) {
                                        contentType = contentType.substring(0, 3);
                                    }

                                    String dStatus = "";
                                    if ((curdoc.getStatus() + "").compareTo("H") == 0) {
                                        dStatus = "html";
                                    } else {
                                        dStatus = "active";
                                    }
//									String reviewerName = curdoc.getReviewerName();
//									if (reviewerName.equals("")) {
//                                        reviewerName = "- - -";
//                                    }
                            %>
                            <tr>
                                <td>
                                    <%
                                        String url = "ManageDocument.do?method=display&doc_no=" + curdoc.getDocId() + "&providerNo=" + user_no;
                                    %>
                                    <a class="<%=curdoc.getStatus() == 'D' ? "text-decoration-line-through" : "text-decoration-none"%>"
                                            href="javascript:void(0);"
                                            title="<%=Encode.forHtmlAttribute(curdoc.getDescription())%>"
                                            onclick="popupFocusPage(500,700,'<%=url%>','demographic_document');">
                                        <%=Encode.forHtml(curdoc.getDescription())%>
                                    </a>
                                </td>
                                <td>
                                    <%=curdoc.getType() == null ? "N/A" : Encode.forHtmlContent(curdoc.getType())%>
                                    <% if (contentType != null && !contentType.isEmpty()) { %>
                                    <span class="text-muted small">(<%=Encode.forHtmlContent(contentType)%>)</span>
                                    <% } %>
                                </td>
                                <td><%=Encode.forHtml(curdoc.getCreatorName())%>
                                </td>
                                <td class="col-date"><fmt:formatDate value="<%=curdoc.getContentDateTime()%>" pattern="yyyy-MM-dd"/></td>

                                <td class="text-end">
                                    <div class="doc-actions">
                                        <%-- Edit button (first - most common action) --%>
                                        <% if (curdoc.getStatus() != 'D') {
                                            if (curdoc.getStatus() == 'H') { %>
                                        <a href="javascript:void(0)"
                                           onclick="popup1(450, 600, 'addedithtmldocument.jsp?editDocumentNo=<%=Encode.forUriComponent(String.valueOf(curdoc.getDocId()))%>&function=<%=Encode.forUriComponent(module)%>&functionid=<%=Encode.forUriComponent(moduleid)%>', 'EditDoc')"
                                           title="<fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.btnEdit"/>"
                                           class="btn btn-link p-0">
                                            <i class="fas fa-pencil-alt"></i>
                                        </a>
                                        <%} else {%>
                                        <a href="javascript:void(0)"
                                           onclick="popup1(350, 500, 'editDocument.jsp?editDocumentNo=<%=curdoc.getDocId()%>&function=<%=Encode.forUriComponent(module)%>&functionid=<%=Encode.forUriComponent(moduleid)%>', 'EditDoc')"
                                           title="<fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.btnEdit"/>"
                                           class="btn btn-link p-0">
                                            <i class="fas fa-pencil-alt"></i>
                                        </a>
                                        <% } %>
                                        <% } %>

                                        <%-- Annotate button --%>
                                        <% if ("demographic".equals(module)) {%>
                                        <a href="javascript:void(0)" title="Annotation"
                                           onclick="window.open('${ pageContext.request.contextPath }/annotation/annotation.jsp?display=<%=Encode.forUriComponent(annotation_display)%>&table_id=<%=Encode.forUriComponent(String.valueOf(curdoc.getDocId()))%>&demo=<%=Encode.forUriComponent(moduleid)%>','anwin','width=400,height=500');"
                                           class="btn btn-link p-0">
                                            <i class="fas fa-clipboard"></i>
                                        </a>
                                        <% } %>

                                        <%-- Delete/Undelete button (last - destructive action) --%>
                                        <%
                                                if (curdoc.getCreatorId().equalsIgnoreCase(user_no)) {
                                                    if (curdoc.getStatus() == 'D') { %>
                                        <a href="javascript:void(0);" onclick="submitDocAction('undelDocumentNo','<%=Encode.forJavaScript(String.valueOf(curdoc.getDocId()))%>','<%=Encode.forJavaScript(module)%>','<%=Encode.forJavaScript(moduleid)%>','<%=Encode.forJavaScript(viewstatus)%>');"
                                           class="btn btn-link p-0"
                                           title="<fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.btnUnDelete"/>">
                                            <i class="fas fa-undo"></i>
                                        </a>
                                        <%
                                        } else { // curdoc get status
                                        %>
                                        <a href="javascript:void(0);" onclick="checkDelete('<%=Encode.forJavaScript(String.valueOf(curdoc.getDocId()))%>','<%=Encode.forJavaScript(module)%>','<%=Encode.forJavaScript(moduleid)%>','<%=Encode.forJavaScript(viewstatus)%>','<%=Encode.forJavaScript(curdoc.getDescription())%>');"
                                           class="btn btn-link p-0 text-danger" title="Delete">
                                            <i class="fas fa-trash"></i>
                                        </a>
                                        <% } %>
                                        <%} else { // curdoc get creator id %>

                                        <security:oscarSec roleName="<%=roleName$%>"
                                                           objectName="_admin,_admin.edocdelete" rights="r">
                                            <% if (curdoc.getStatus() == 'D') {%>
                                            <a href="javascript:void(0);" onclick="submitDocAction('undelDocumentNo','<%=Encode.forJavaScript(String.valueOf(curdoc.getDocId()))%>','<%=Encode.forJavaScript(module)%>','<%=Encode.forJavaScript(moduleid)%>','<%=Encode.forJavaScript(viewstatus)%>');"
                                               title="<fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.btnUnDelete"/>"
                                               class="btn btn-link p-0">
                                                <i class="fas fa-undo"></i>
                                            </a>
                                            <% } else { // curdoc get status %>
                                            <a href="javascript:void(0);" onclick="checkDelete('<%=Encode.forJavaScript(String.valueOf(curdoc.getDocId()))%>','<%=Encode.forJavaScript(module)%>','<%=Encode.forJavaScript(moduleid)%>','<%=Encode.forJavaScript(viewstatus)%>','<%=Encode.forJavaScript(curdoc.getDescription())%>');"
                                                class="btn btn-link p-0 text-danger" title="Delete">
                                                <i class="fas fa-trash"></i>
                                            </a>
                                            <% } %>
                                        </security:oscarSec>

                                        <% } // curdoc get creator id %>
                                    </div>
                                </td>

                            </tr>
                            <%}%>
                            </tbody>
                        </table>
                    </div>
                </div>
                <%}%>
            </div>

        <div class="mt-3 mb-3 text-end">
            <button type="button" class="btn btn-secondary btn-sm" onclick="window.closeWindow()">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnBack"/>
            </button>
        </div>

        </div><!-- /px-3 -->
    </div><!-- /container-fluid -->
    </body>
</html>