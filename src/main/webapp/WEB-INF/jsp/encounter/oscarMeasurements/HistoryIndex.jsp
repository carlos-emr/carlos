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

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<security:oscarSec roleName="<%=roleName$%>" objectName="_measurements" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_eChart");%>
</security:oscarSec>

<%
    if (!authed) {
        return;
    }
%>

<%@page import="io.github.carlos_emr.carlos.utility.WebUtils"%>

<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logoutPage");
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>



<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="encounter.Index.oldMeasurements"/></title>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css">
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap5.min.js"></script>
    <style type="text/css" media="print">
        .noprint { display: none; }
    </style>
    <script>
        jQuery(document).ready(function () {
            jQuery('#measurementsHistoryTbl').DataTable({
                searching: true,
                pageLength: 25,
                language: {
                    url: '${pageContext.request.contextPath}/library/DataTables/i18n/<fmt:message key="global.i18n.datatablescode"/>.json'
                }
            });
        });
    </script>
</head>
<body>
<div class="container mt-2">

    <%-- Session-level error and info messages --%>
    <%=WebUtils.popErrorAndInfoMessagesAsHtml(session)%>

 <% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="alert alert-danger">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><carlos:encode value="<%= error %>" context="html"/></li>
            <% } %>
        </ul>
    </div>
<% } %>

    <div class="page-header-bar d-flex align-items-center justify-content-between py-2 mb-3 border-bottom" id="header">
        <div class="d-flex align-items-center gap-2">
            <i class="fa-solid fa-chart-line"></i>
            <span class="fw-semibold"><fmt:message key="encounter.Index.oldMeasurements"/></span>
        </div>
    </div>

    <div class="bg-light border rounded p-3">

        <p class="text-muted mb-3"><fmt:message key="encounter.oscarMeasurements.oldmesurementindex"/></p>

        <c:if test="${not empty measurementsData}">
            <table id="measurementsHistoryTbl" class="table table-striped table-hover table-sm">
                <thead>
                    <tr>
                        <th><fmt:message key="encounter.oscarMeasurements.displayHistory.headingType"/></th>
                        <th><fmt:message key="encounter.oscarMeasurements.typedescription"/></th>
                        <th></th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach var="data" items="${measurementsData.measurementsDataVector}">
                        <tr>
                            <td><carlos:encode value="${data.type}" context="html"/></td>
                            <td><carlos:encode value="${data.typeDescription}" context="html"/></td>
                            <td>
                                <a href="#"
                                   onclick="popupPage(300,800,'${pageContext.request.contextPath}/encounter/oscarMeasurements/SetupDisplayHistory?type=${carlos:forUriComponent(data.type)}'); return false;">
                                    <fmt:message key="encounter.oscarMeasurements.historyIndex.btnMore"/>
                                </a>
                            </td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
        </c:if>

        <div class="mt-3 noprint d-flex gap-2">
            <button type="button" class="btn btn-secondary" onclick="window.print()">
                <fmt:message key="global.btnPrint"/>
            </button>
            <button type="button" class="btn btn-secondary" onclick="window.close()">
                <fmt:message key="global.btnClose"/>
            </button>
        </div>

        <c:if test="${not empty type}">
            <input type="hidden" name="type" value="<carlos:encode value='${type}' context='htmlAttribute'/>"/>
        </c:if>

    </div><%-- end bg-light --%>

</div><%-- end container --%>
</body>
</html>
