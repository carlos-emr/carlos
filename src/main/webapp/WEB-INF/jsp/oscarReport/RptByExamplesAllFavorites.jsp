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
    RptByExamplesAllFavorites.jsp
    =============================
    Purpose: Popup window for managing saved favourite queries. Allows providers to
             edit the name/content of a saved query or delete it.

    Features:
    - Bootstrap 5 / HTML5 compliant layout
    - OWASP encoding for all server-supplied values
    - i18n via oscarResources bundle
    - Edit and Delete actions submitted to RptByExamplesFavorite.do
    - Close button reloads the opener window and closes this popup

    Parameters (set by backing Action):
    - allFavorites — RptByExampleAllFavoriteBean containing favoriteVector
                     (items expose: id, queryName, query)

    Security:
    - Requires _report or _admin.reporting read privilege
    - CSRF token auto-injected by CsrfGuardScriptInjectionFilter

    @since 2001-2002
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
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <title>
        <fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/> -
        <fmt:message key="oscarReport.RptByExample.MsgMyFavorites"/>
    </title>

    <%@ include file="/includes/global-head.jspf" %>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.request.contextPath}/share/css/extractedFromPages.css">

    <script type="text/javascript">
        // Localized confirm-delete message rendered server-side for i18n support
        var msgConfirmDelete = '<fmt:message key="oscarReport.RptByExample.MsgConfirmDelete"/>';

        /**
         * Populates the hidden newQuery and newName fields with the selected
         * favourite's data and submits the edit form.
         *
         * @param {string} text1 - The raw SQL query text (JS-attribute-encoded by JSP)
         * @param {string} text2 - The display name of the favourite (JS-attribute-encoded by JSP)
         */
        function set(text1, text2) {
            document.getElementById('favoritesForm').newQuery.value = text1;
            document.getElementById('favoritesForm').newName.value = text2;
        }

        /**
         * Asks the user to confirm deletion, then sets the toDelete flag and
         * record id before submitting the edit form.
         *
         * @param {string|number} id - The database ID of the favourite to delete
         */
        function confirmDelete(id) {
            if (confirm(msgConfirmDelete)) {
                document.getElementById('favoritesForm').toDelete.value = 'true';
                document.getElementById('favoritesForm').id.value = id;
                document.getElementById('favoritesForm').submit();
            }
        }

        /**
         * Reloads the opener window (query-by-example page) so it reflects
         * any changes to favourites, then closes this popup.
         */
        function closeAndRefresh() {
            self.opener.document.location.reload();
            self.close();
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
                aria-label="<fmt:message key='button.close'/>"></button>
    </div>

    <!-- Page header bar -->
    <div class="page-header-bar d-flex align-items-center justify-content-between py-2 mb-3 border-bottom"
         id="header">
        <div class="d-flex align-items-center gap-2">
            <i class="fas fa-star text-secondary" aria-hidden="true"></i>
            <span class="fw-semibold"><fmt:message key="oscarReport.CDMReport.msgReport"/></span>
        </div>
        <span>
            <fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/> -
            <fmt:message key="oscarReport.RptByExample.MsgMyFavorites"/>
        </span>
    </div>

    <!-- Favourites management form -->
    <form id="favoritesForm"
          action="${pageContext.request.contextPath}/oscarReport/RptByExamplesFavorite.do"
          method="post">

        <input type="hidden" name="newName"/>
        <input type="hidden" name="newQuery"/>
        <input type="hidden" name="toDelete" value="false"/>
        <input type="hidden" name="id" value="error"/>

        <table class="table table-sm table-hover">
            <thead>
                <tr>
                    <th scope="col" style="width:200px"><fmt:message key="oscarReport.RptByExample.MsgName"/></th>
                    <th scope="col"><fmt:message key="oscarReport.RptByExample.MsgQuery"/></th>
                    <th scope="col" style="width:160px"></th>
                </tr>
            </thead>
            <tbody>
                <c:forEach var="favorite" items="${allFavorites.favoriteVector}">
                    <tr>
                        <td>${e:forHtml(favorite.queryName)}</td>
                        <td>${e:forHtml(favorite.query)}</td>
                        <td class="text-nowrap">
                            <input type="button"
                                   class="btn btn-outline-secondary btn-sm"
                                   value="<fmt:message key='oscarReport.RptByExample.MsgEdit'/>"
                                   onclick="set('${e:forJavaScriptAttribute(favorite.query)}', '${e:forJavaScriptAttribute(favorite.queryName)}'); document.getElementById('favoritesForm').submit(); return false;"/>
                            <input type="button"
                                   class="btn btn-danger btn-sm"
                                   value="<fmt:message key='oscarReport.RptByExample.MsgDelete'/>"
                                   onclick="confirmDelete('${e:forJavaScriptAttribute(favorite.id)}'); return false;"/>
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>

    </form>

    <!-- Close button outside the submit form — calls closeAndRefresh(), not a form submit -->
    <div class="mt-2">
        <button type="button"
                class="btn btn-outline-secondary btn-sm"
                onclick="closeAndRefresh()">
            <fmt:message key="global.btnClose"/>
        </button>
    </div>

</div><!-- end .container -->

</body>
</html>
