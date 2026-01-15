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

<%
    if (session.getValue("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
%>
<%@ page import="java.util.*,ca.openosp.openo.report.pageUtil.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/css/encounterStyles.css">
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Measurements.msgDisplayMeasurementStyleSheets"/></title>

    </head>

    <body class="BodyStyle" vlink="#0000FF">
    <!--  -->
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
    <form action="${pageContext.request.contextPath}/oscarEncounter/oscarMeasurements/DeleteMeasurementStyleSheet.do" method="post">
        <table class="MainTable" id="scrollNumber1" name="encounterTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Measurements.msgMeasurements"/></td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar">
                        <tr>
                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Measurements.msgDisplayMeasurementStyleSheets"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="MainTableLeftColumn"></td>
                <td class="MainTableRightColumn">
                    <table border=0 cellspacing=4 width=700>
                        <tr>
                            <td>
                                <table>
                                    <tr>
                                        <c:if test="${not empty messages}">
                                            <c:forEach var="msg" items="${messages}">
                                                ${msg}
                                                <br>
                                            </c:forEach>
                                        </c:if>
                                    </tr>
                                    <tr>
                                        <td>
                                    <tr>
                                        <td align="left" class="Header" width="300"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingStyleSheetName"/>
                                        </td>
                                        <td align="left" class="Header" width="10"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.MeasurementAction.headingDelete"/>
                                        </td>
                                    </tr>
                                    <c:forEach var="styleSheet" items="${styleSheets.styleSheetNameVector}" varStatus="ctr">
                                    <tr class="data">
                                        <td width="300"><c:out value="${styleSheet.styleSheetName}"/></td>
                                        <td width="10">
                                            <input type="checkbox" name="deleteCheckbox" value="${styleSheet.cssId}"/>
                                        </td>
                                    </tr>
                                    </c:forEach>
                            </td>
                        </tr>
                    </table>
                    <table>
                        <tr>
                            <td><input type="button" name="Button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>"
                                       onClick="window.close()"></td>
                            <td><input type="button" name="Button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.displayHistory.headingDelete"/>"
                                       onclick="submit();"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
        </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
        </table>
    </form>
    </body>
</html>
