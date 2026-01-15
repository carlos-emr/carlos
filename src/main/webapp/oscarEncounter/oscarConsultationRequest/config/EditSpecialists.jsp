<%@ page import="org.owasp.encoder.Encode" %><%--

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
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ page import="java.util.List" %>
<%@ page import="ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConTitlebar" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.consult" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.consult");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<!DOCTYPE html>
<html>
    <jsp:useBean id="displayServiceUtil" scope="request"
                 class="ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConDisplayServiceUtil"/>
    <%
        displayServiceUtil.estSpecialistVector();
    %>
    <head>

        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.title"/>
        </title>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <script language="javascript">
            function BackToOscar() {
                window.close();
            }
        </script>
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/css/encounterStyles.css">

    </head>

    <body class="BodyStyle" vlink="#0000FF">
    <jsp:include page="/images/spinner.jsp" flush="true"/>
    <script>
        ShowSpin(true);
        document.onreadystatechange = function () {
            if (document.readyState === "interactive") {
                HideSpin();
            }
        }
    </script>
    <% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %>
    <div id="service-providers-wrapper" style="margin:auto 10px;">
        <table class="MainTable" id="scrollNumber1">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn">Consultation</td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar">
                        <tr>
                            <td class="Header"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.title"/>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr style="vertical-align: top">
                <td class="MainTableLeftColumn">
                    <%
                        EctConTitlebar titlebar = new EctConTitlebar(request);
                        out.print(titlebar.estBar(request));
                    %>
                </td>
                <td class="MainTableRightColumn">
                    <table cellpadding="0" cellspacing="2"
                           style="border-collapse: collapse" bordercolor="#111111" width="100%">

                        <!----Start new rows here-->
                        <tr>
                            <td>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.msgClickOn"/><br>

                            </td>
                        </tr>
                        <tr>
                            <td><form action="${pageContext.request.contextPath}/oscarEncounter/EditSpecialists.do" method="post">

                                <table>
                                    <tr>
                                        <th>&nbsp;</th>
                                        <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.specialist"/>
                                        </th>
                                        <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.address"/>
                                        </th>
                                        <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.phone"/>
                                        </th>
                                        <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.fax"/>
                                        </th>

                                    </tr>
                                    <%

                                        for (int i = 0; i < displayServiceUtil.specIdVec.size(); i++) {
                                            String specId = displayServiceUtil.specIdVec.elementAt(i);
                                            String fName = displayServiceUtil.fNameVec.elementAt(i);
                                            String lName = displayServiceUtil.lNameVec.elementAt(i);
                                            String proLetters = displayServiceUtil.proLettersVec.elementAt(i);
                                            String address = displayServiceUtil.addressVec.elementAt(i);
                                            String phone = displayServiceUtil.phoneVec.elementAt(i);
                                            String fax = displayServiceUtil.faxVec.elementAt(i);
                                    %>

                                    <tr>
                                        <td><input type="checkbox" name="specialists"
                                                   value="<%=specId%>"></td>
                                        <td>
                                            <%
                                                String contextPath = request.getContextPath();
                                                String url = contextPath + "/oscarEncounter/EditSpecialists.do?specId=" + specId;
                                                out.print("<a href=\"" + url + "\">");
                                                out.print(Encode.forHtmlContent(lName + " " + fName + " " + (proLetters == null ? "" : proLetters)));
                                                out.print("</a>");
                                            %>
                                        </td>
                                        <td><%=Encode.forHtmlContent(address) %>
                                        </td>
                                        <td><%=Encode.forHtmlContent(phone)%>
                                        </td>
                                        <td><%=Encode.forHtmlContent(fax)%>
                                        </td>
                                    </tr>
                                    <% }%>

                                </table>

                            </form></td>
                        </tr>

                    </table>
                </td>
            </tr>
            <tr>
                <td class="MainTableBottomRowLeftColumn"></td>
                <td class="MainTableBottomRowRightColumn"></td>
            </tr>
        </table>
    </div>
    </body>
</html>
