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

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <meta charset="UTF-8">
        <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
        <title><fmt:message key="encounter.oscarConsultationRequest.ConfirmConsultationRequest.title"/></title>
        <script type="text/javascript">
            function BackToOscar() {
                window.history.back();
            }

            function finishPage(secs) {
                setTimeout(function() { BackToOscar(); }, secs * 500);
            }
        </script>
    </head>

    <body onload="finishPage(15);">

    <div class="container">

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

        <div class="page-header-bar d-flex align-items-center justify-content-between
                    py-2 mb-3 border-bottom" id="header">
            <div class="d-flex align-items-center gap-2">
                <span class="fw-semibold"><fmt:message key="encounter.oscarConsultationRequest.nothingtoPrint.title"/></span>
            </div>
        </div>

        <div class="bg-light border rounded p-2">
            <p><fmt:message key="encounter.oscarConsultationRequest.msgNothingPrinted"/></p>
            <p><fmt:message key="encounter.oscarConsultationRequest.ConfirmConsultationRequest.msgClose5Sec"/></p>
            <p><a href="javascript: BackToOscar();"><fmt:message key="global.btnClose"/></a></p>
        </div>

    </div>
    </body>
</html>
