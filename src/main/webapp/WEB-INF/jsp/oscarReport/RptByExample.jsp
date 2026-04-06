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
    RptByExample.jsp
    ================
    Purpose: Query-by-example report entry page. Allows authorized providers
             to enter or select a saved SQL query and execute it against the
             database, viewing results inline.

    Features:
    - Bootstrap 5 / HTML5 compliant layout
    - OWASP encoding for all user-supplied values
    - i18n via oscarResources bundle
    - Links to query history (RptViewAllQueryByExamples.do) and favorites editor
      (RptByExamplesAllFavorites.do)
    - Favourite queries loaded from the request scope via ${favorites} attribute

    Parameters (set by backing Action):
    - favorites   — Collection of favourite query objects (query property)
    - results     — HTML string of query results (rendered unescaped; backend-generated)

    Security:
    - Requires _report or _admin.reporting read privilege
    - CSRF token auto-injected by CsrfGuardScriptInjectionFilter

    @since 2001-2002
--%>

<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.*" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
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

<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/></title>

    <%@ include file="/includes/global-head.jspf" %>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.request.contextPath}/share/css/extractedFromPages.css">

    <style type="text/css" media="print">
        .page-header-bar { display: none; }
    </style>

    <script type="text/javascript">
        // Copies the selected favourite query into the SQL textarea.
        function write2TextArea() {
            const form = document.getElementById('queryForm');
            const select = form.selectedRecentSearch;
            const selectedValue = select.options[select.selectedIndex].value;
            form.sql.value = selectedValue;
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

    <!-- Page header bar -->
    <div class="page-header-bar d-flex align-items-center justify-content-between py-2 mb-3 border-bottom"
         id="header">
        <div class="d-flex align-items-center gap-2">
            <i class="fas fa-search text-secondary" aria-hidden="true"></i>
            <span class="fw-semibold"><fmt:message key="oscarReport.CDMReport.msgReport"/></span>
        </div>
        <span><fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/></span>
    </div>

    <!-- Main content area -->
    <div class="bg-light border rounded p-2">
        <div class="row g-2">

            <!-- Left sidebar: navigation links -->
            <div class="col-12 col-md-2">
                <nav class="d-flex flex-column gap-2 pt-1">
                    <a href="#"
                       onclick="popupPage(600, 1000, 'RptViewAllQueryByExamples.do'); return false;">
                        <fmt:message key="oscarReport.RptByExample.MsgViewQueryHistory"/>
                    </a>
                    <a href="#"
                       onclick="popupPage(600, 1000, 'RptByExamplesAllFavorites.do'); return false;">
                        <fmt:message key="oscarReport.RptByExample.MsgEditMyFavorite"/>
                    </a>
                </nav>
            </div>

            <!-- Right content column: query form and results -->
            <div class="col-12 col-md-10">

                <form id="queryForm"
                      action="${pageContext.request.contextPath}/oscarReport/RptByExample.do"
                      method="post">

                    <!-- SQL textarea -->
                    <div class="mb-3">
                        <label for="sql" class="form-label form-label-sm">
                            <fmt:message key="oscarReport.RptByExample.MsgEnterAQuery"/>
                        </label>
                        <textarea id="sql" name="sql" rows="4"
                                  class="form-control form-control-sm"></textarea>
                    </div>

                    <!-- OR divider -->
                    <div class="mb-2">
                        <span class="form-label form-label-sm text-muted">
                            <fmt:message key="oscarReport.RptByExample.MsgOr"/>
                        </span>
                    </div>

                    <!-- Favourites selector -->
                    <div class="mb-3">
                        <label for="selectedRecentSearch" class="form-label form-label-sm">
                            <fmt:message key="oscarReport.RptByExample.MsgSelectFromMyFavorites"/>
                        </label>
                        <div class="d-flex gap-2 align-items-center">
                            <select id="selectedRecentSearch"
                                    name="selectedRecentSearch"
                                    class="form-select form-select-sm">
                                <option value="" disabled="disabled" selected="selected">
                                    <fmt:message key="oscarReport.RptByExample.MsgMyFavorites"/>
                                </option>
                                <c:forEach var="favorite" items="${favorites}">
                                    <option value="${e:forHtmlAttribute(favorite.query)}">
                                        ${e:forHtml(favorite.query)}
                                    </option>
                                </c:forEach>
                            </select>
                            <input type="button"
                                   class="btn btn-outline-secondary btn-sm text-nowrap"
                                   value="<fmt:message key="oscarReport.RptByExample.MsgLoadQuery"/>"
                                   onclick="write2TextArea(); return false;"/>
                        </div>
                    </div>

                    <!-- Submit -->
                    <div class="mb-3">
                        <button type="submit" class="btn btn-primary btn-sm">
                            <fmt:message key="oscarReport.RptByExample.MsgRunQuery"/>
                        </button>
                    </div>

                    <!-- Query results — rendered as backend-generated HTML -->
                    <c:if test="${not empty results}">
                        <div class="mt-3">
                            ${results}
                        </div>
                    </c:if>

                </form>
            </div><!-- end right column -->

        </div><!-- end .row -->
    </div><!-- end .bg-light -->

</div><!-- end .container -->

</body>
</html>
