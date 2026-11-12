<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Page role: Renders `resultReport.jsp` for the reporting workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logoutPage");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>

<%@ page
        import="java.util.*, io.github.carlos_emr.carlos.report.reportByTemplate.*" %>
<%@ page import="io.github.carlos_emr.carlos.report.reportByTemplate.ReportObjectGeneric" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>

<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_report" rights="r" reverse="<%=true%>">
    <%
        response.sendRedirect(request.getContextPath() + "/logoutPage");
    %>
</security:oscarSec>
<!DOCTYPE html>

<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <title>Report by Template</title>

        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/css/dataTables.bootstrap5.min.css" rel="stylesheet">
        <script src="${pageContext.request.contextPath}/share/javascript/Oscar.js"></script>
        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
        <script src="${ pageContext.request.contextPath }/library/DataTables/DataTables-1.13.11/js/jquery.dataTables.min.js"></script>
        <script src="${ pageContext.request.contextPath }/library/DataTables/DataTables-1.13.11/js/dataTables.bootstrap5.min.js"></script>

        <script>
            function clearSession() {
                navigator.sendBeacon('<%= request.getContextPath() %>/oscarReport/reportByTemplate/ViewClearSession');
            }

            jQuery(document).ready(function () {
                jQuery('.reportTable').DataTable(
                    {
                        "aLengthMenu": [[-1, 10, 25, 50, 100], ["All", 10, 25, 50, 100]]
                    }
                );
            });
        </script>
        <style media="print">
            .noprint, .showhidequery, .sqlBorderDiv, .controls, .dataTables_length, .dataTables_filter, .dataTables_paginate {
                display: none;
            }

            div.sub-actions a + .result-btn {
                display: inline-block;
                padding-left: 5px;
                border-left: #0088cc 2px solid;
            }

        </style>
    </head>
    <%

        ReportObjectGeneric curreport = (ReportObjectGeneric) request.getAttribute("reportobject");
        Integer sequenceLength = (Integer) request.getAttribute("sequenceLength");
        List<String> sqlList = new ArrayList<String>();
        List<String> htmlList = new ArrayList<String>();
        List<String> csvList = new ArrayList<String>();
        // Which queries' CSV went over the CSV limit and is held back (#5849, D260).
        List<Boolean> csvWithheldList = new ArrayList<Boolean>();

        if (curreport.isSequence()) {
            for (int x = 0; x < sequenceLength; x++) {
                sqlList.add((String) request.getAttribute("sql-" + x));
                htmlList.add((String) request.getAttribute("resultsethtml-" + x));
                csvList.add((String) request.getAttribute("csv-" + x));
                csvWithheldList.add(Boolean.TRUE.equals(request.getAttribute("csvWithheld-" + x)));
            }
        } else {
            sqlList.add((String) request.getAttribute("sql"));
            htmlList.add((String) request.getAttribute("resultsethtml"));
            csvList.add((String) request.getAttribute("csv"));
            csvWithheldList.add(Boolean.TRUE.equals(request.getAttribute("csvWithheld")));
        }

        pageContext.setAttribute("htmlList", htmlList);
        pageContext.setAttribute("csvWithheldList", csvWithheldList);
        pageContext.setAttribute("sequenced", curreport.isSequence());

    %>

    <body onunload="clearSession();">

        <%@ include file="rbtTopNav.jspf" %>

    <h3>
        ${carlos:forHtml(reportobject.title)}<br>
        <small>${carlos:forHtml(reportobject.description)}</small>
    </h3>

    <c:if test="${not empty errormsg}">
        <div class="alert alert-warning" role="alert">
        <%-- A limit refusal names its limit in the user's language (#5849); other errors keep their English text. --%>
        <c:choose>
            <c:when test="${not empty errormsgKey}"><fmt:message key="${errormsgKey}"><fmt:param value="${errormsgArg}"/></fmt:message></c:when>
            <c:otherwise>${carlos:forHtml(errormsg)}</c:otherwise>
        </c:choose>
        </div>
    </c:if>

    <div class="reportBorderDiv row">
        <c:forEach items="${ htmlList }" var="htmlOut" varStatus="query">
            <%-- Shown above a result whose CSV went over the CSV limit, so its downloads are held back (#5849, D260).
                 In a sequence it names the query, as the export buttons below do. --%>
            <c:if test="${csvWithheldList[query.index]}">
                <div class="alert alert-info" role="status"><c:if test="${sequenced}"><strong><fmt:message key="report.byTemplate.queryNumber"><fmt:param value="${query.count}"/></fmt:message>:</strong> </c:if><fmt:message key="report.byTemplate.csvWithheld"><fmt:param value="${csvWithheldMb}"/></fmt:message></div>
            </c:if>
            <c:choose>
                <c:when test="${ not fn:startsWith(htmlOut, '<table') }">
                    <div class="alert alert-danger">
                        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                        ${carlos:forHtml(htmlOut)}
                    </div>
                </c:when>
                <c:otherwise>
                    ${ htmlOut }
                </c:otherwise>
            </c:choose>
        </c:forEach>
    </div>

    <div class="noprint form-actions">

        <div style="margin-bottom:15px;" class="d-flex gap-2">
            <input type="button" class="btn btn-primary" value="Back"
                   onclick="document.location='<%= request.getContextPath() %>/oscarReport/reportByTemplate/ViewReportConfiguration?templateid=${carlos:forUriComponent(reportobject.templateId)}'">
            <input type="button" class="btn btn-primary" value="Print" onclick="window.print();">

            <%
                for (int x = 0; x < csvList.size(); x++) {
                    if (csvList.get(x) == null || csvList.get(x).isEmpty()) continue;
                    pageContext.setAttribute("queryNumber", x + 1);
            %>

            <form style="display:inline;" action="${pageContext.request.contextPath}/oscarReport/reportByTemplate/generateOutFilesAction" method="post">
                <%-- Every export in a sequence names its query; a query whose CSV is held back has none (#5849). --%>
                <c:if test="${sequenced}">
                <span class="align-middle"><fmt:message key="report.byTemplate.queryNumber"><fmt:param value="${queryNumber}"/></fmt:message></span>
                </c:if>
                <input type="hidden" class="btn btn-secondary" name="csv" value="<carlos:encode value='<%= csvList.get(x) %>' context="htmlAttribute"/>">
                <input type="submit" class="btn btn-secondary" name="getCSV" value="Export to CSV">
                <input type="submit" class="btn btn-secondary" name="getXLS" value="Export to XLSX">
            </form>

            <% } %>
        </div>
        <div class="row sub-actions">
            <a href="#" class="showhidequery result-btn" onclick="showHideItem('sqlDiv')">
                Show/Hide Query
            </a>
            <%-- Template changes need _report write (ManageTemplates2Action); readers do not get controls that would only end in a security error. --%>
            <security:oscarSec roleName="<%=roleName$%>" objectName="_report" rights="w">
            <a href="javascript:void(0)" class="edit result-btn"
               style="padding-left: 5px;border-left:#0088cc 2px solid;"
               onclick="document.location='<%= request.getContextPath() %>/oscarReport/reportByTemplate/ViewAddEditTemplate?templateid=${carlos:forUriComponent(reportobject.templateId)}&opentext=1'">
                Edit Template
            </a>
            </security:oscarSec>
            <div class="sqlBorderDiv" id="sqlDiv" style="display:none;background-color:white;padding:5px;">
                <samp style="font-size: 11px;">
                    <%
                        for (int x = 0; x < sqlList.size(); x++) {
                            out.println((x + 1) + ")" + SafeEncode.forHtml(sqlList.get(x).trim()));
                        }
                    %>
                </samp>
            </div>
        </div>
    </div>

</html>
