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
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Measurements.msgAddMeasurementType"/></title>
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

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
    <form action="${pageContext.request.contextPath}/oscarEncounter/oscarMeasurements/AddMeasurementType.do" method="post" onsubmit="return validateForm()">
        <table class="MainTable" id="scrollNumber1" name="encounterTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Measurements.msgMeasurements"/></td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar">
                        <tr>
                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Measurements.msgAddMeasurementType"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="MainTableLeftColumn"></td>
                <td class="MainTableRightColumn">
                    <table border=0 cellspacing=4 width=400>
                        <tr>
                            <td>
                                <table>
                                    <tr>
                                        <td>
                                    <tr>
                                        <td colspan="2">
                                            <c:if test="${not empty messages}">
                                                <c:forEach var="msg" items="${messages}">
                                                    <c:out value="${msg}"/>
                                                    <br>
                                                </c:forEach>
                                            </c:if>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th align="left" class="td.tite" width="5"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingType"/>

                                        </th>
                                        <td><input type="text" name="type" id="type" /></td>
                                    </tr>
                                    <tr>
                                        <th align="left" class="td.tite"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingTypeDesc"/>
                                        </th>
                                        <td><input type="text" name="typeDesc" id="typeDesc" /></td>
                                    </tr>
                                    <tr>
                                        <th align="left" class="td.tite" width="50"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingDisplayName"/>
                                        </th>
                                        <td><input type="text" name="typeDisplayName" id="typeDisplayName" /></td>
                                    </tr>
                                    <tr>
                                        <th align="left" class="td.tite"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingMeasuringInstrc"/>
                                        </th>
                                        <td><input type="text" name="measuringInstrc" id="measuringInstrc" /></td>
                                    </tr>
                                    <tr>
                                        <th align="left" class="td.tite"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingValidation"/>
                                        </th>
                                        <td><select name="validation" id="validation">
                                            <c:forEach var="validation" items="${validations}">
                                                <option value="${validation.id}">
                                                        ${validation.name}
                                                </option>
                                            </c:forEach>\
                                        </select></td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <table>
                                                <tr>
                                                    <td><input type="button" name="Button"
                                                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>"
                                                               onClick="window.close()"></td>
                                                    <td><input type="submit" name="submit"
                                                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.MeasurementsAction.addBtn"/>"/>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <input type="hidden" name="msgBetween"
                                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.AddMeasurementType.duplicateType"/>"/>
                                    <input type="hidden" name="msgBetween"
                                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.AddMeasurementType.successful"/>"/>
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

    <script type="text/javascript">
        function validateForm() {
            var a = document.forms[0]["type"].value;
            var b = document.forms[0]["typeDesc"].value;
            var c = document.forms[0]["typeDisplayName"].value;

            if (a == null || a == "") {
                alert("Please enter a type");
                return false;
            } else if (b == null || b == "") {
                alert("Please enter a type description");
                return false;
            } else if (c == null || c == "") {
                alert("Please enter a display name");
                return false;
            }
        }
    </script>
    </body>
</html>
