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

<%@page import="ca.openosp.openo.utility.WebUtils" %>
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
%>


<%@page import="ca.openosp.openo.utility.WebUtils" %>
<html>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConfirmConsultationRequest.title"/>
        </title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

    </head>
    <script language="javascript">
        function BackToOscar() {
            window.close();
        }

        function finishPage(secs) {

            // Print consultatin request form
            const consultPDFName = '<%=request.getAttribute("consultPDFName")%>';
            const consultPDF = '<%=request.getAttribute("consultPDF")%>';
            const isPreviewReady = '<%=request.getAttribute("isPreviewReady")%>';
            if (consultPDF !== 'null' && consultPDFName !== 'null' && isPreviewReady === 'true') {
                downloadConsultForm(consultPDFName, consultPDF, function () {
                    setTimeout("window.close()", secs * 1000);
                });
                return;
            }

            setTimeout("window.close()", secs * 500);
            //window.opener.location.reload();
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


    <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">
    <body topmargin="0" leftmargin="0" vlink="#0000FF"
          onload="finishPage(5);">
    <!--  -->
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn">Consultation</td>
            <td class="MainTableTopRowRightColumn"></td>
        </tr>
        <tr style="vertical-align: top">
            <td class="MainTableLeftColumn" width="10%">&nbsp;</td>
            <td class="MainTableRightColumn">
                <table width="100%" height="100%">
                    <tr>
                        <td>
                            <c:choose>
                                <c:when test="${not empty requestScope.transType and requestScope.transType eq '1'}">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConfirmConsultationRequest.msgConsReq"/>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConfirmConsultationRequest.msgUpdated"/>
                                </c:when>
                                <c:when test="${not empty requestScope.transType and requestScope.transType eq '2'}">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConfirmConsultationRequest.msgConsReq"/>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConfirmConsultationRequest.msgCreated"/>
                                </c:when>
                                <c:otherwise>
                                </c:otherwise>
                            </c:choose>

                            <%=WebUtils.popInfoMessagesAsHtml(session)%>
                        </td>
                    </tr>
                    <tr>
                        <c:if test="${not empty isPreviewReady and isPreviewReady eq 'true'}">
                            <td>Printing Consultation form.....</td>
                        </c:if>
                    </tr>
                    <tr>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConfirmConsultationRequest.msgClose5Sec"/>
                        </td>
                    </tr>
                    <tr>
                        <td><a href="javascript: BackToOscar();"> <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/> </a></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>
    </body>
</html>
