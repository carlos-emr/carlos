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

<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.*" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>
        <fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/> -
        <fmt:message key="oscarReport.RptByExample.MsgAllQueriesExecuted"/>
    </title>

    <%@ include file="/includes/global-head.jspf" %>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.request.contextPath}/share/css/extractedFromPages.css">

    <script type="text/javascript">
        function set(text) {
            document.getElementById('newQuery').value = text;
        }
    </script>
</head>
<body>

<div class="container">

    <!-- Alert banner — hidden by default; surfaced via JS on error -->
    <div id="jsAlertBanner"
         class="alert alert-danger alert-dismissible"
         style="display:none"
         role="alert">
        <span id="jsAlertText"></span>
        <button type="button"
                class="btn-close"
                onclick="this.closest('.alert').style.display='none'"
                aria-label="Close"></button>
    </div>

    <!-- Page header bar: report label on the left, date-range refresh form on the right -->
    <div class="page-header-bar d-flex align-items-center justify-content-between py-2 mb-3 border-bottom"
         id="header">
        <div class="d-flex align-items-center gap-2">
            <i class="fas fa-search text-secondary" aria-hidden="true"></i>
            <span class="fw-semibold"><fmt:message key="oscarReport.CDMReport.msgReport"/></span>
        </div>
        <!-- Refresh form: form wraps controls directly — no table needed -->
        <form action="${pageContext.request.contextPath}/oscarReport/RptViewAllQueryByExamples.do"
              method="post"
              class="d-flex align-items-center gap-2">
            <label class="form-label form-label-sm mb-0">
                <fmt:message key="oscarReport.RptByExample.MsgAllQueriesExecutedFrom"/>:
            </label>
            <input type="text" name="startDate"
                   value="${e:forHtmlAttribute(startDate)}"
                   class="form-control form-control-sm"
                   style="width:8em"/>
            <label class="form-label form-label-sm mb-0">
                <fmt:message key="oscarReport.RptByExample.MsgTo"/>
            </label>
            <input type="text" name="endDate"
                   value="${e:forHtmlAttribute(endDate)}"
                   class="form-control form-control-sm"
                   style="width:8em"/>
            <button type="submit" class="btn btn-primary btn-sm"><fmt:message key="oscarReport.RptByExample.MsgRefresh"/></button>
        </form>
    </div>

    <!-- Main content area -->
    <div class="bg-light border rounded p-2">
        <div class="row g-2">

            <!-- Left sidebar column (empty, mirrors OSCAR MainTableLeftColumn) -->
            <div class="col-12 col-md-2"></div>

            <!-- Right content column: all-queries table -->
            <div class="col-12 col-md-10">

                <!-- favouriteForm wraps the table — correct HTML5 nesting -->
                <form id="favouriteForm"
                      action="${pageContext.request.contextPath}/oscarReport/RptByExamplesFavorite.do"
                      method="post">
                    <input type="hidden" id="newQuery" name="newQuery" value="error"/>

                    <table class="table table-sm table-hover">
                        <thead>
                            <tr>
                                <th scope="col" style="width:140px"><fmt:message key="oscarReport.RptByExample.MsgDate"/></th>
                                <th scope="col" style="width:400px"><fmt:message key="oscarReport.RptByExample.MsgQuery"/></th>
                                <th scope="col" style="width:100px"><fmt:message key="oscarReport.RptByExample.MsgProvider"/></th>
                                <th scope="col" style="width:100px"><fmt:message key="oscarReport.RptByExample.MsgAddToFavorite"/></th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="queryInfo" items="${allQueries.queryVector}">
                                <c:set var="escapedQuery" value="${queryInfo.queryWithEscapeChar}"/>
                                <tr>
                                    <td>${e:forHtml(queryInfo.date)}</td>
                                    <td>${e:forHtml(queryInfo.query)}</td>
                                    <td>${e:forHtml(queryInfo.providerLastName)}, ${e:forHtml(queryInfo.providerFirstName)}</td>
                                    <td>
                                        <input type="button"
                                               class="btn btn-outline-secondary btn-sm"
                                               value="<fmt:message key="oscarReport.RptByExample.MsgAddToFavorite"/>"
                                               onclick="set('${e:forJavaScriptAttribute(escapedQuery)}'); document.getElementById('favouriteForm').submit();"/>
                                    </td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>

                </form>
            </div><!-- end right column -->

        </div><!-- end .row -->
    </div><!-- end .bg-light -->

</div><!-- end .container -->

</body>
</html>
