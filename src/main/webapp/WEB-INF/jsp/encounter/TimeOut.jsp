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
    Closing page of the legacy encounter editor's Sign and Save / Verify and Sign
    (EctSaveEncounter2Action). It refreshes the schedule that opened the chart when that save
    changed a visit status, which the action reports as the refreshOpener request attribute. It
    used to decide from the login-wide EctSessionBean, which follows whichever chart was entered
    last in any window (issue #4673).
--%>
<!-- Jan 20, 2003,-->
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<%
    boolean refreshOpener = Boolean.TRUE.equals(request.getAttribute("refreshOpener"));
%>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <meta charset="UTF-8">
        <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
        <title><fmt:message key="encounter.timeOut.title"/></title>
        <script type="text/javascript">
            function loadUp() {
                window.resizeTo(900, 50);
                setTimeout(function() { window.close(); }, 1000);
                <%
                    if (refreshOpener) {
                        out.print("if (opener && typeof opener.refresh === 'function') { opener.refresh(); }");
                    }
                %>
            }
        </script>
    </head>
    <body onload="loadUp()">

    <div class="container">

        <div class="page-header-bar d-flex align-items-center justify-content-between
                    py-2 mb-3 border-bottom" id="header">
            <div class="d-flex align-items-center gap-2">
                <span class="fw-semibold"><fmt:message key="encounter.timeOut.title"/></span>
            </div>
            <div class="text-muted small"><fmt:message key="encounter.timeOut.msgSaveExit"/></div>
        </div>

    </div>
    </body>
</html>
