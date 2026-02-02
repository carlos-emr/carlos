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
<!DOCTYPE html>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.utility.MiscUtils" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="ca.openosp.openo.commn.dao.UserPropertyDAO, ca.openosp.openo.commn.model.UserProperty" %>
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
        appointmentNo = Integer.parseInt(appointment);
    }

%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>
<%@ taglib uri="/WEB-INF/oscarProperties-tag.tld" prefix="oscarProp" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ page import="java.util.*" %>
<%@ page import="ca.openosp.openo.commn.dao.CtlDocClassDao" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="ca.openosp.openo.utility.SessionConstants" %>
<%@ page import="ca.openosp.openo.documentManager.EDocUtil" %>
<%@ page import="ca.openosp.openo.documentManager.EDoc" %>
<%@ page import="ca.openosp.openo.casemgmt.model.CaseManagementNoteLink" %>
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

        <link rel="stylesheet" type="text/css"
              href="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.css"/>
        <link href="${pageContext.request.contextPath}/library/bootstrap/3.0.0/css/bootstrap.css" rel="stylesheet"
              type="text/css"/>
        <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/css/jquery.dataTables.css"
              rel="stylesheet" type="text/css"/>


        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"
                type="text/javascript"></script>
        <script src="${pageContext.request.contextPath}/library/bootstrap/3.0.0/js/bootstrap.min.js"
                type="text/javascript"></script>
        <script src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"
                type="text/javascript"></script>
        <script type="text/javascript"
                src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.js"></script>

        <script src="${pageContext.request.contextPath}/js/global.js" type="text/javascript"></script>
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
                    "<%=subClasses.get(i)%>"<%=(i<subClasses.size()-1)?",":""%>
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


            function checkDelete(url, docDescription) {
// revision Apr 05 2004 - we now allow anyone to delete documents
                if (confirm("<fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgDelete"/> " + docDescription)) {
                    window.location = url;
                }
            }

            function selectAll(checkboxId, parentEle, className) {
                var f = document.getElementById(checkboxId);
                var val = f.checked;
                var chkList = document.getElementsByClassName(className, parentEle);
                for (i = 0; i < chkList.length; i++) {
                    chkList[i].checked = val;
                }
            }

            function submitForm(actionPath) {

                var form = document.forms[2];
                if (verifyChecks(form)) {
                    form.action = actionPath;
                    form.submit();
                    return true;
                } else
                    return false;
            }

            function verifyChecks(t) {

                if (t.docNo == null) {
                    alert("No documents selected");
                    return false;
                } else {
                    var oneChecked = 0;
                    if (t.docNo.length) {
                        for (i = 0; i < t.docNo.length; i++) {
                            if (t.docNo[i].checked) {
                                ++oneChecked;
                                break;
                            }
                        }
                    } else
                        oneChecked = t.docNo.checked ? 1 : 0;

                    if (oneChecked === 0) {
                        alert("No documents selected");
                        return false;
                    }
                }
                return true;
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
                var update = "<%=updateParent%>";
                var parentId = "<%=parentAjaxId%>";
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
                    columnDefs: [{orderable: false, targets: [0, 8]}],
                    lengthMenu: [
                        [-1, 10, 20, 50, 100, 200],
                        ['All', 10, 20, 50, 100, 200]
                    ],
                    order: [[6, 'dsc']],
                    "language": {
                        "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                    }
                });
            });
        </script>

        <style>
            :root *:not(h2) {
                font-family: Arial, "Helvetica Neue", Helvetica, sans-serif !important;
                font-size: 12px;
                overscroll-behavior: none;
                -webkit-font-smoothing: antialiased;
                -moz-osx-font-smoothing: grayscale;
            }

            .panel-body {
                overflow: auto;
            }

            a {
                color: blue;
            }
            .panel-body table tr td a {
	            text-wrap: auto;
	            word-wrap: anywhere;
	            word-break: break-word;
            }

        </style>

    </head>
    <body>

    <div class="container" style="margin-bottom: 25px">
        <h2>
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor"
                 class="bi bi-file-earmark" viewBox="0 0 16 16">
                <path d="M14 4.5V14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V2a2 2 0 0 1 2-2h5.5zm-3 0A1.5 1.5 0 0 1 9.5 3V1H4a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1V4.5z"></path>
            </svg>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgDocuments"/> Manager
        </h2>

        <% if ("demographic".equals(module)) { %>
        <oscar:nameage demographicNo="<%=Encode.forHtmlAttribute(moduleid)%>"/>
        <%} %>

        <jsp:include page="addDocument.jsp">
            <jsp:param name="appointmentNo" value="<%=appointmentNo%>"/>
            <jsp:param name="addDocument" value="${param.mode}"/>
        </jsp:include>


        <form action="${pageContext.request.contextPath}/documentManager/combinePDFs.do" method="post">
            <input type="hidden" name="curUser" value="<%=curUser%>">
            <input type="hidden" name="demoId" value="<%=Encode.forHtmlAttribute(moduleid)%>">
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

                //--- get remote documents ---
                if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
                    List<EDoc> remoteDocuments = EDocUtil.getRemoteDocuments(loggedInInfo, Integer.parseInt(moduleid));
                    categories.add(remoteDocuments);
                    categoryKeys.add("Remote Documents");
                }

                for (int i = 0; i < categories.size(); i++) {
                    String currentkey = (String) categoryKeys.get(i);
                    ArrayList category = (ArrayList) categories.get(i);
            %>
                <div class="doclist panel panel-default">
                    <div class="headerline panel-heading">
                        <div class="container">
                            <div class="form-inline">
                                <div class="form-group" style="margin-right: 10px;">
                                    <%= Encode.forHtmlContent(currentkey) %>
                                </div>

                                <% if (i == 0) {%>
                                <div class="form-group">
                                        <%--      <label for="viewstatus"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgViewStatus"/></label>--%>
                                    <select class="form-control" id="viewstatus" name="viewstatus"
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
                                <div class="form-group">
                                        <%--          <label for="view"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgView"/></label>--%>
                                    <select id="viewdoctype<%=i%>" name="view" id="view"
                                            class="input-medium form-control"
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
                                <div class="form-group">
                                    <a class="btn btn-link form-control"
                                        href="${ pageContext.request.contextPath }/documentManager/documentBrowser.jsp?function=<%=Encode.forUriComponent(module)%>&functionid=<%=Encode.forUriComponent(moduleid)%>&categorykey=<%=Encode.forUri(currentkey)%>">
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgBrowser"/>
                                    </a>
                                </div>
                                <%}%>
                            </div>
                        </div>
                    </div>

                    <div id="documentsInnerDiv<%=i%>" class="panel-body">
                        <table id="tblDocs<%=i%>" class="table table-condensed table-striped">
                            <thead>
                            <tr>
                                <th>
                                    <input class="tightCheckbox" type="checkbox" id="pdfCheck<%=i%>"
                                           onclick="selectAll('pdfCheck<%=i%>','privateDocsDiv', 'tightCheckbox<%=i%>');"/>
                                </th>
                                <th>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgContent"/>
                                </th>
                                <th>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgDocDesc"/>
                                </th>
                                <th>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgType"/>
                                </th>
                                <th>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgCreator"/>
                                </th>
                                <th>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgResponsible"/>
                                </th>
                                <th>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.observationDate"/>
                                </th>
                                <th>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgDate"/>
                                </th>

                                <th>&nbsp;</th>
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
                                    <% if (curdoc.isPDF()) {%>
                                    <input class="tightCheckbox<%=i%>" type="checkbox" name="docNo"
                                           id="docNo<%=curdoc.getDocId()%>" value="<%=curdoc.getDocId()%>"
                                           style="margin: 0; padding: 0;"/>
                                    <%}%>
                                </td>
                                <td><%=curdoc.getType() == null ? "N/A" : Encode.forHtmlContent(curdoc.getType())%>
                                </td>

                                <td>
                                    <%
                                        String url = "ManageDocument.do?method=display&doc_no=" + curdoc.getDocId() + "&providerNo=" + user_no + (curdoc.getRemoteFacilityId() != null ? "&remoteFacilityId=" + curdoc.getRemoteFacilityId() : "");
                                    %>
                                    <a <%=curdoc.getStatus() == 'D' ? "style='text-decoration:line-through'" : ""%>
                                            href="javascript:void(0);"
                                            title="<%=Encode.forHtmlAttribute(curdoc.getDescription())%>"
                                            style="word-break: break-word;overflow-wrap: anywhere;overflow: hidden;text-overflow: ellipsis;text-decoration: none;"
                                            onclick="popupFocusPage(500,700,'<%=url%>','demographic_document');">
                                        <%=Encode.forHtml(curdoc.getDescription())%>
                                    </a>
                                </td>
                                <td>
                                    <div style="overflow:hidden; text-overflow: ellipsis;"
                                         title="<%=Encode.forHtmlAttribute(contentType)%>">
                                        <%=Encode.forHtmlContent(contentType)%>
                                    </div>
                                </td>

                                <td><%=Encode.forHtml(curdoc.getCreatorName())%>
                                </td>
                                <td><%=Encode.forHtml(curdoc.getResponsibleName())%>
                                </td>
                                <td><%=Encode.forHtml(curdoc.getObservationDate())%>
                                </td>
                                <td><%=curdoc.getContentDateTime()%>
                                </td>

                                <td style="text-align: right;">
                                    <div style="white-space: nowrap;">
                                        <%
                                            if (curdoc.getRemoteFacilityId() == null) {
                                                if (curdoc.getCreatorId().equalsIgnoreCase(user_no)) {
                                                    if (curdoc.getStatus() == 'D') { %>
                                        <a href="documentReport.jsp?undelDocumentNo=<%=curdoc.getDocId()%>&function=<%=Encode.forUriComponent(module)%>&functionid=<%=Encode.forUriComponent(moduleid)%>&viewstatus=<%=Encode.forUriComponent(viewstatus)%>"
                                           class="btn btn-link" style="padding:0;"
                                           title="<fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.btnUnDelete"/>">
                                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"
                                                 fill="currentColor" class="bi bi-arrow-counterclockwise"
                                                 viewBox="0 0 16 16">
                                                <path fill-rule="evenodd"
                                                      d="M8 3a5 5 0 1 1-4.546 2.914.5.5 0 0 0-.908-.417A6 6 0 1 0 8 2z"></path>
                                                <path d="M8 4.466V.534a.25.25 0 0 0-.41-.192L5.23 2.308a.25.25 0 0 0 0 .384l2.36 1.966A.25.25 0 0 0 8 4.466"></path>
                                            </svg>
                                        </a>
                                        <%
                                        } else { // curdoc get status
                                        %>
                                        <a style="color:red; padding:0;"
                                           href="javascript: checkDelete('documentReport.jsp?delDocumentNo=<%=Encode.forJavaScript(Encode.forUriComponent(String.valueOf(curdoc.getDocId())))%>&function=<%=Encode.forJavaScript(Encode.forUriComponent(module))%>&functionid=<%=Encode.forJavaScript(Encode.forUriComponent(moduleid))%>&viewstatus=<%=Encode.forJavaScript(Encode.forUriComponent(viewstatus))%>','<%=Encode.forJavaScript(curdoc.getDescription())%>')"
                                           class="btn btn-link" title="Delete">
                                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"
                                                 fill="currentColor" class="bi bi-trash" viewBox="0 0 16 16">
                                                <path d="M5.5 5.5A.5.5 0 0 1 6 6v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5m2.5 0a.5.5 0 0 1 .5.5v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5m3 .5a.5.5 0 0 0-1 0v6a.5.5 0 0 0 1 0z"></path>
                                                <path d="M14.5 3a1 1 0 0 1-1 1H13v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V4h-.5a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1H6a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1h3.5a1 1 0 0 1 1 1zM4.118 4 4 4.059V13a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1V4.059L11.882 4zM2.5 3h11V2h-11z"></path>
                                            </svg>
                                        </a>
                                        <% } %>
                                        <%} else { // curdoc get creator id %>

                                        <security:oscarSec roleName="<%=roleName$%>"
                                                           objectName="_admin,_admin.edocdelete" rights="r">
                                            <% if (curdoc.getStatus() == 'D') {%>
                                            <a href="documentReport.jsp?undelDocumentNo=<%=Encode.forUriComponent(String.valueOf(curdoc.getDocId()))%>&function=<%=Encode.forUriComponent(module)%>&functionid=<%=Encode.forUriComponent(moduleid)%>&viewstatus=<%=Encode.forUriComponent(viewstatus)%>"
                                               title="<fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.btnUnDelete"/>"
                                               class="btn btn-link" style="padding:0;">
                                                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"
                                                     fill="currentColor" class="bi bi-arrow-counterclockwise"
                                                     viewBox="0 0 16 16">
                                                    <path fill-rule="evenodd"
                                                          d="M8 3a5 5 0 1 1-4.546 2.914.5.5 0 0 0-.908-.417A6 6 0 1 0 8 2z"></path>
                                                    <path d="M8 4.466V.534a.25.25 0 0 0-.41-.192L5.23 2.308a.25.25 0 0 0 0 .384l2.36 1.966A.25.25 0 0 0 8 4.466"></path>
                                                </svg>
                                            </a>
                                            <% } else { // curdoc get status %>
                                            <a style="color:red;padding:0;"
                                                href="javascript: checkDelete('documentReport.jsp?delDocumentNo=<%=Encode.forJavaScript(Encode.forUriComponent(String.valueOf(curdoc.getDocId())))%>&function=<%=Encode.forJavaScript(Encode.forUriComponent(module))%>&functionid=<%=Encode.forJavaScript(Encode.forUriComponent(moduleid))%>&viewstatus=<%=Encode.forJavaScript(Encode.forUriComponent(viewstatus))%>','<%=Encode.forJavaScript(curdoc.getDescription())%>')"
                                                class="btn btn-link" title="Delete">
                                                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"
                                                     fill="currentColor" class="bi bi-trash" viewBox="0 0 16 16">
                                                    <path d="M5.5 5.5A.5.5 0 0 1 6 6v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5m2.5 0a.5.5 0 0 1 .5.5v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5m3 .5a.5.5 0 0 0-1 0v6a.5.5 0 0 0 1 0z"></path>
                                                    <path d="M14.5 3a1 1 0 0 1-1 1H13v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V4h-.5a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1H6a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1h3.5a1 1 0 0 1 1 1zM4.118 4 4 4.059V13a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1V4.059L11.882 4zM2.5 3h11V2h-11z"></path>
                                                </svg>
                                            </a>
                                            <% } %>
                                        </security:oscarSec>

                                        <% } // curdoc get creator id %>

                                        <% if (curdoc.getStatus() != 'D') {
                                            if (curdoc.getStatus() == 'H') { %>
                                        <a href="javascript:void(0)"
                                           onclick="popup1(450, 600, 'addedithtmldocument.jsp?editDocumentNo=<%=Encode.forUriComponent(String.valueOf(curdoc.getDocId()))%>&function=<%=Encode.forUriComponent(module)%>&functionid=<%=Encode.forUriComponent(moduleid)%>', 'EditDoc')"
                                           title="<fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.btnEdit"/>"
                                           class="btn btn-link" style="padding:0;">
                                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"
                                                 fill="currentColor" class="bi bi-pencil-square"
                                                 viewBox="0 0 16 16">
                                                <path d="M15.502 1.94a.5.5 0 0 1 0 .706L14.459 3.69l-2-2L13.502.646a.5.5 0 0 1 .707 0l1.293 1.293zm-1.75 2.456-2-2L4.939 9.21a.5.5 0 0 0-.121.196l-.805 2.414a.25.25 0 0 0 .316.316l2.414-.805a.5.5 0 0 0 .196-.12l6.813-6.814z"></path>
                                                <path fill-rule="evenodd"
                                                      d="M1 13.5A1.5 1.5 0 0 0 2.5 15h11a1.5 1.5 0 0 0 1.5-1.5v-6a.5.5 0 0 0-1 0v6a.5.5 0 0 1-.5.5h-11a.5.5 0 0 1-.5-.5v-11a.5.5 0 0 1 .5-.5H9a.5.5 0 0 0 0-1H2.5A1.5 1.5 0 0 0 1 2.5z"></path>
                                            </svg>
                                        </a>
                                        <%} else {%>
                                        <a href="javascript:void(0)"
                                           onclick="popup1(350, 500, 'editDocument.jsp?editDocumentNo=<%=curdoc.getDocId()%>&function=<%=Encode.forUriComponent(module)%>&functionid=<%=Encode.forUriComponent(moduleid)%>', 'EditDoc')"
                                           title="<fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.btnEdit"/>"
                                           class="btn btn-link" style="padding:0;">
                                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"
                                                 fill="currentColor" class="bi bi-pencil-square"
                                                 viewBox="0 0 16 16">
                                                <path d="M15.502 1.94a.5.5 0 0 1 0 .706L14.459 3.69l-2-2L13.502.646a.5.5 0 0 1 .707 0l1.293 1.293zm-1.75 2.456-2-2L4.939 9.21a.5.5 0 0 0-.121.196l-.805 2.414a.25.25 0 0 0 .316.316l2.414-.805a.5.5 0 0 0 .196-.12l6.813-6.814z"></path>
                                                <path fill-rule="evenodd"
                                                      d="M1 13.5A1.5 1.5 0 0 0 2.5 15h11a1.5 1.5 0 0 0 1.5-1.5v-6a.5.5 0 0 0-1 0v6a.5.5 0 0 1-.5.5h-11a.5.5 0 0 1-.5-.5v-11a.5.5 0 0 1 .5-.5H9a.5.5 0 0 0 0-1H2.5A1.5 1.5 0 0 0 1 2.5z"></path>
                                            </svg>
                                        </a>
                                        <% } %>

                                        <% } %>

                                        <% if ("demographic".equals(module)) {%>
                                        <a href="javascript:void(0)" title="Annotation"
                                           onclick="window.open('${ pageContext.request.contextPath }/annotation/annotation.jsp?display=<%=Encode.forUriComponent(annotation_display)%>&table_id=<%=Encode.forUriComponent(String.valueOf(curdoc.getDocId()))%>&demo=<%=Encode.forUriComponent(moduleid)%>','anwin','width=400,height=500');"
                                           class="btn btn-link" style="padding:0">
                                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"
                                                 fill="currentColor" class="bi bi-clipboard" viewBox="0 0 16 16">
                                                <path d="M4 1.5H3a2 2 0 0 0-2 2V14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V3.5a2 2 0 0 0-2-2h-1v1h1a1 1 0 0 1 1 1V14a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3.5a1 1 0 0 1 1-1h1z"></path>
                                                <path d="M9.5 1a.5.5 0 0 1 .5.5v1a.5.5 0 0 1-.5.5h-3a.5.5 0 0 1-.5-.5v-1a.5.5 0 0 1 .5-.5zm-3-1A1.5 1.5 0 0 0 5 1.5v1A1.5 1.5 0 0 0 6.5 4h3A1.5 1.5 0 0 0 11 2.5v-1A1.5 1.5 0 0 0 9.5 0z"></path>
                                            </svg>
                                        </a>
                                        <% } %>
                                        <% if (!("demographic".equals(module) && moduleid != null && moduleid.equals(session.getAttribute("user")))) {

                                            String tickler_url = request.getContextPath() + "/tickler/ForwardDemographicTickler.do?docType=DOC&docId=" + curdoc.getDocId() + "&demographic_no=" + moduleid;
                                        %>
                                        <a href="javascript:void(0);" title="Tickler" class="btn btn-link"
                                           style="padding: 0;"
                                           onclick="popup1(450,600,'<%=tickler_url%>','tickler')">
                                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"
                                                 fill="currentColor" class="bi bi-feather" viewBox="0 0 16 16">
                                                <path d="M15.807.531c-.174-.177-.41-.289-.64-.363a3.765 3.765 0 0 0-.833-.15c-.62-.049-1.394 0-2.252.175C10.365.545 8.264 1.415 6.315 3.1c-1.95 1.686-3.168 3.724-3.758 5.423-.294.847-.44 1.634-.429 2.268.005.316.05.62.154.88.017.04.035.082.056.122A68.362 68.362 0 0 0 .08 15.198a.528.528 0 0 0 .157.72.504.504 0 0 0 .705-.16 67.606 67.606 0 0 1 2.158-3.26c.285.141.616.195.958.182.513-.02 1.098-.188 1.723-.49 1.25-.605 2.744-1.787 4.303-3.642l1.518-1.55a.528.528 0 0 0 0-.739l-.729-.744 1.311.209a.504.504 0 0 0 .443-.15c.222-.23.444-.46.663-.684.663-.68 1.292-1.325 1.763-1.892.314-.378.585-.752.754-1.107.163-.345.278-.773.112-1.188a.524.524 0 0 0-.112-.172ZM3.733 11.62C5.385 9.374 7.24 7.215 9.309 5.394l1.21 1.234-1.171 1.196a.526.526 0 0 0-.027.03c-1.5 1.789-2.891 2.867-3.977 3.393-.544.263-.99.378-1.324.39a1.282 1.282 0 0 1-.287-.018Zm6.769-7.22c1.31-1.028 2.7-1.914 4.172-2.6a6.85 6.85 0 0 1-.4.523c-.442.533-1.028 1.134-1.681 1.804l-.51.524-1.581-.25Zm3.346-3.357C9.594 3.147 6.045 6.8 3.149 10.678c.007-.464.121-1.086.37-1.806.533-1.535 1.65-3.415 3.455-4.976 1.807-1.561 3.746-2.36 5.31-2.68a7.97 7.97 0 0 1 1.564-.173Z"></path>
                                            </svg>
                                        </a>

                                        <% } %>
                                        <%} else { %>
                                        Remote Document
                                        <% } %>
                                    </div><!-- button grouping -->
                                </td>

                            </tr>
                            <%}%>
                            </tbody>
                        </table>
                    </div>
                </div>
                <%}%>
            </div>
            <div>
                <input type="button" name="Button" class="btn btn-primary"
                        value="<fmt:message key='dms.documentReport.btnDoneClose'/>"
                        onclick="window.closeWindow()"/>
                <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.btnCombinePDF"/>" class="btn"
                       onclick="return submitForm('<rewrite:reWrite jspPage="combinePDFs.do"/>');"/>
            </div>

        </form>


    </div>
    </body>
</html>