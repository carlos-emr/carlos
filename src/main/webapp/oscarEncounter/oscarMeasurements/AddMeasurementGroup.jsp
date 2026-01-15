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
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%
    String groupName = (String) session.getAttribute("groupName");
%>

<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/css/encounterStyles.css">
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Measurements.msgAddMeasurementGroup"/></title>
        <script type="text/javascript">
            function set(target) {
                document.forms[0].forward.value = target;
            };
        </script>
        <style>
            select {
                min-width: 400px;
            }
        </style>

    </head>

    <body class="BodyStyle" vlink="#0000FF"
          onload="window.resizeTo(1000,500)" ;>
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
    <form action="${pageContext.request.contextPath}/oscarEncounter/oscarMeasurements/AddMeasurementGroup.do" method="post">
        <table class="MainTable" id="scrollNumber1" name="encounterTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Measurements.msgMeasurements"/></td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar">
                        <tr>
                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Measurements.msgAddMeasurementGroup"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="MainTableLeftColumn"></td>
                <td class="MainTableRightColumn">
                    <table border=0 cellspacing=4 width=800>
                        <tr>
                            <td>
                                <table>
                                    <tr>
                                        <th align="left" class="td.tite"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.MeasurementGroup.allTypes"/>
                                        </th>

                                        <th align="left" class="td.tite"><%= session.getAttribute("groupName") %></th>
                                    </tr>
                                    <tr>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.MeasurementGroup.add2Group"/>
                                            <%= session.getAttribute("groupName") %></td>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.MeasurementGroup.deleteTypes"/>
                                            <%= session.getAttribute("groupName") %></td>
                                    <tr>
                                        <td><select multiple="true" name="selectedAddTypes" size="10">
                                            <c:forEach var="allTypeDisplayName" items="${allTypeDisplayNames}">
                                                <option value="${allTypeDisplayName.typeDisplayName}">
                                                        ${allTypeDisplayName.typeDisplayName}
                                                </option>
                                            </c:forEach>
                                        </select></td>
                                        <td><select multiple="true" name="selectedDeleteTypes" size="10">
                                            <c:forEach var="existingTypeDisplayName" items="${existingTypeDisplayNames}">
                                                <option value="${existingTypeDisplayName.typeDisplayName}">
                                                        ${existingTypeDisplayName.typeDisplayName}
                                                </option>
                                            </c:forEach>
                                        </select></td>
                                    </tr>
                                    <tr>
                                        <input type="hidden" name="forward" value="error"/>
                                        <td><input type="button" name="button"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.MeasurementsAction.addBtn"/>"
                                                   onclick="set('add');submit();"/></td>
                                        <td><input type="button" name="button"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.MeasurementsAction.deleteBtn"/>"
                                                   onclick="set('delete');submit();"/></td>
                                    </tr>
                                    <tr>
                                        <td><input type="button" name="Button"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>"
                                                   onClick="window.close()"></td>
                                        <td></td>
                                    </tr>
                                    <input type="hidden" name="groupName" value="<%= groupName %>"/>
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
