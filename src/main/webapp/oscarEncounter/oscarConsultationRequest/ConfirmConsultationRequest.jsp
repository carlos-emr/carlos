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
<%@ page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_con");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
    // Store transType as a local variable for safe comparison
    String transType = (String) request.getAttribute("transType");
    String isPreview = (String) request.getAttribute("isPreviewReady");
%>
<!DOCTYPE html>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConfirmConsultationRequest.title"/></title>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    </head>

    <body onload="finishPage(5);" class="d-flex align-items-center justify-content-center" style="min-height:100vh; background-color:var(--carlos-bg-light);">

        <div class="text-center p-4" style="max-width:420px;">
            <div class="mb-3">
                <i class="fa-solid fa-circle-check" style="font-size:3rem; color:var(--carlos-primary);"></i>
            </div>

            <h5 class="fw-semibold mb-2">
                <% if ("1".equals(transType)) { %>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConfirmConsultationRequest.msgConsReq"/>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConfirmConsultationRequest.msgUpdated"/>
                <% } else if ("2".equals(transType)) { %>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConfirmConsultationRequest.msgConsReq"/>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConfirmConsultationRequest.msgCreated"/>
                <% } %>
            </h5>

            <%=WebUtils.popInfoMessagesAsHtml(session)%>

            <% if ("true".equals(isPreview)) { %>
                <p class="text-muted mb-2" style="font-size:0.9rem;">Printing Consultation form...</p>
            <% } %>

            <p class="text-muted mb-3" style="font-size:0.85rem;">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConfirmConsultationRequest.msgClose5Sec"/>
                <br>
                <span id="countdown" class="fw-semibold">5</span>s
            </p>

            <a href="javascript:BackToOscar();" class="btn btn-sm btn-outline-secondary">
                <i class="fa-solid fa-xmark me-1"></i><fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>
            </a>
        </div>

    <script>
        function BackToOscar() {
            window.close();
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
            const consultPDFName = '<%=Encode.forJavaScript(String.valueOf(request.getAttribute("consultPDFName")))%>';
            const consultPDF = '<%=Encode.forJavaScript(String.valueOf(request.getAttribute("consultPDF")))%>';
            const isPreviewReady = '<%=Encode.forJavaScript(String.valueOf(request.getAttribute("isPreviewReady")))%>';
            if (consultPDF !== 'null' && consultPDFName !== 'null' && isPreviewReady === 'true') {
                downloadConsultForm(consultPDFName, consultPDF, function () {
                    setTimeout("window.close()", secs * 1000);
                });
                return;
            }

            setTimeout("window.close()", secs * 500);
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
    </script>
    </body>
</html>
