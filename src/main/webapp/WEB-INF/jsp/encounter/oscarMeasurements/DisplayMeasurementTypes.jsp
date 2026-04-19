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

<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logoutPage");
%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="java.util.List" %>
<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/css/encounterStyles.css">
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="encounter.Measurements.msgDisplayMeasurementTypes"/></title>

        <script type="text/javascript">
            function set(target) {
                document.forms[0].forward.value = target;
            };
        </script>
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
    <form action="${pageContext.request.contextPath}/encounter/oscarMeasurements/DeleteMeasurementTypes" method="post">
        <table class="MainTable" id="scrollNumber1" name="encounterTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn"><fmt:message key="encounter.Measurements.msgMeasurements"/></td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar">
                        <tr>
                            <td><fmt:message key="encounter.Measurements.msgDisplayMeasurementTypes"/></td>
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

                                <%
                                    // Check if messages list is not empty
                                    List<String> messages = (List<String>) request.getAttribute("messages");
                                    if (messages != null && !messages.isEmpty()) {
                                %>
                                    <tr>
                                <%      for (String msg : messages) { %><%= msg %><br><%}%>
                                    </tr>
                                <%

                                    }
                                %>
                                    <tr>
                                        <td>
                                    <tr>
                                        <th align="left" class="Header" width="5"><fmt:message key="encounter.oscarMeasurements.Measurements.headingType"/>
                                        </th>
                                        <th align="left" class="Header" width="20"><fmt:message key="encounter.oscarMeasurements.Measurements.headingDisplayName"/>
                                        </th>
                                        <th align="left" class="Header" width="10"><fmt:message key="encounter.oscarMeasurements.Measurements.headingTypeDesc"/>
                                        </th>
                                        <th align="left" class="Header" width="300"><fmt:message key="encounter.oscarMeasurements.Measurements.headingMeasuringInstrc"/>
                                        </th>
                                        <th align="left" class="Header" width="300"><fmt:message key="encounter.oscarMeasurements.Measurements.headingValidation"/>
                                        </th>
                                        <th align="left" class="Header" width="10"><fmt:message key="encounter.oscarMeasurements.MeasurementAction.headingDelete"/>
                                        </th>
                                    </tr>
                                    <c:forEach var="measurementType" items="${measurementTypes.measurementTypeVector}" varStatus="ctr">
                                    <tr class="data">
                                        <td width="5">
                                            <c:url var="exportMeasurementUrl" value="/encounter/oscarMeasurements/ViewExportMeasurement">
                                                <c:param name="mType" value="${measurementType.type}"/>
                                            </c:url>
                                            <a href="${carlos:forHtmlAttribute(exportMeasurementUrl)}" target="_blank">
                                                ${carlos:forHtml(measurementType.type)}
                                            </a>
                                        </td>
                                        <td width="20">${carlos:forHtml(measurementType.typeDisplayName)}</td>
                                        <td width="10">${carlos:forHtml(measurementType.typeDesc)}</td>
                                        <td width="300">${carlos:forHtml(measurementType.measuringInstrc)}</td>
                                        <td width="300">${carlos:forHtml(measurementType.validation)}</td>
                                        <td width="10">
                                            <input type="checkbox" name="deleteCheckbox" value="${measurementType.id}"/>
                                        </td>
                                    </tr>
                                    </c:forEach>
                                    <tr>
                                        <td><input type="button" name="Button"
                                                   value="<fmt:message key="global.btnClose"/>"
                                                   onClick="window.close()"></td>
                                        <td><input type="button" name="Button"
                                                   value="<fmt:message key="encounter.oscarMeasurements.displayHistory.headingDelete"/>"
                                                   onclick="submit();"/></td>
                                    </tr>

                            </td>
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
