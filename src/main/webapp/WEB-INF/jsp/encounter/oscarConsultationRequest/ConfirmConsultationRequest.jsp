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

<%@page import="io.github.carlos_emr.carlos.utility.WebUtils" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_con");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
    // Store transType as a local variable for safe comparison
    String transType = (String) request.getAttribute("transType");
    String isPreview = (String) request.getAttribute("isPreviewReady");
    String fallbackDemographicNo = request.getParameter("demographicNo");
    if (fallbackDemographicNo == null || fallbackDemographicNo.trim().isEmpty()) {
        fallbackDemographicNo = request.getParameter("de");
    }
    if (fallbackDemographicNo == null) {
        fallbackDemographicNo = "";
    }
    String fallbackUrl = request.getContextPath() + "/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests";
    if (!fallbackDemographicNo.trim().isEmpty()) {
        fallbackUrl += "?de=" + java.net.URLEncoder.encode(fallbackDemographicNo, java.nio.charset.StandardCharsets.UTF_8);
    }
%>
<!DOCTYPE html>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
        <title><fmt:message key="encounter.oscarConsultationRequest.ConfirmConsultationRequest.title"/></title>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    </head>

    <body class="d-flex align-items-center justify-content-center" style="min-height:100vh; background-color:var(--carlos-bg-light);">

        <div class="text-center p-4" style="max-width:420px;">
            <div class="mb-3">
                <i class="fa-solid fa-circle-check" style="font-size:3rem; color:var(--carlos-primary);"></i>
            </div>

            <h5 class="fw-semibold mb-2">
                <% if ("1".equals(transType)) { %>
                    <fmt:message key="encounter.oscarConsultationRequest.ConfirmConsultationRequest.msgConsReq"/>
                    <fmt:message key="encounter.oscarConsultationRequest.ConfirmConsultationRequest.msgUpdated"/>
                <% } else if ("2".equals(transType)) { %>
                    <fmt:message key="encounter.oscarConsultationRequest.ConfirmConsultationRequest.msgConsReq"/>
                    <fmt:message key="encounter.oscarConsultationRequest.ConfirmConsultationRequest.msgCreated"/>
                <% } %>
            </h5>

            <%-- The signal is set as a request attribute on the same-request paths and re-encoded as a
                 query parameter when the save path redirects here (request attributes do not survive a 302). --%>
            <% if (Boolean.TRUE.equals(request.getAttribute("signatureNotApplied"))
                   || "1".equals(request.getParameter("signatureNotApplied"))) { %>
                <div class="alert alert-warning py-2 px-3 mb-3" role="alert" style="font-size:0.85rem;">
                    <i class="fa-solid fa-triangle-exclamation me-1"></i>
                    <fmt:message key="encounter.oscarConsultationRequest.ConfirmConsultationRequest.signatureNotApplied"/>
                </div>
            <% } %>

            <%=WebUtils.popInfoMessagesAsHtml(session)%>

            <% if ("true".equals(isPreview)) { %>
                <p class="text-muted mb-2" style="font-size:0.9rem;">Printing Consultation form...</p>
            <% } %>

            <p class="text-muted mb-3" style="font-size:0.85rem;">
                <fmt:message key="encounter.oscarConsultationRequest.ConfirmConsultationRequest.msgClose5Sec"/>
                <br>
                <span id="countdown" class="fw-semibold">5</span>s
            </p>

            <button type="button" id="closeButton" class="btn btn-sm btn-outline-secondary">
                <i class="fa-solid fa-xmark me-1"></i><fmt:message key="global.btnClose"/>
            </button>
        </div>

    <script>
        function BackToOscar() {
            closeOrReturn();
        }

        function closeOrReturn() {
            if (window.opener && !window.opener.closed) {
                window.close();
                window.setTimeout(returnToConsultations, 100);
                return;
            }
            returnToConsultations();
        }

        function returnToConsultations() {
            if (window.history.length > 1 && document.referrer) {
                window.history.back();
                return;
            }
            window.location.href = '<carlos:encode value='<%= fallbackUrl %>' context="javaScriptBlock"/>';
        }

        function finishPage(secs) {
            // Countdown display
            var remaining = secs;
            var countdownEl = document.getElementById('countdown');
            var timer = setInterval(function() {
                remaining--;
                if (countdownEl) countdownEl.textContent = remaining;
                if (remaining <= 0) clearInterval(timer);
            }, 1000);

            // Print consultation request form
            const consultPDFName = '<carlos:encode value='<%= String.valueOf(request.getAttribute("consultPDFName")) %>' context="javaScriptBlock"/>';
            const consultPDF = '<carlos:encode value='<%= String.valueOf(request.getAttribute("consultPDF")) %>' context="javaScriptBlock"/>';
            const isPreviewReady = '<carlos:encode value='<%= String.valueOf(request.getAttribute("isPreviewReady")) %>' context="javaScriptBlock"/>';
            if (consultPDF !== 'null' && consultPDFName !== 'null' && isPreviewReady === 'true') {
                downloadConsultForm(consultPDFName, consultPDF, function () {
                    window.setTimeout(closeOrReturn, secs * 1000);
                });
                return;
            }

            window.setTimeout(closeOrReturn, secs * 1000);
        }

        function downloadConsultForm(consultPDFName, consultPDF, callback) {
            const pdfData = new Uint8Array(atob(consultPDF).split('').map(char => char.charCodeAt(0)));
            const pdfBlob = new Blob([pdfData], {type: 'application/pdf'});
            const downloadLink = document.createElement('a');
            downloadLink.href = URL.createObjectURL(pdfBlob);
            downloadLink.download = consultPDFName;
            downloadLink.click();
            URL.revokeObjectURL(downloadLink.href);
            callback();
        }

        document.getElementById('closeButton').addEventListener('click', BackToOscar);
        finishPage(5);
    </script>
    </body>
</html>
