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
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ page import="ca.openosp.openo.encounter.pageUtil.*" %>
<%@ page import="ca.openosp.openo.encounter.oscarMeasurements.pageUtil.*" %>
<%@ page import="java.util.Vector" %>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Measurements.msgCustomization"/></title>
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <script type="text/javascript">
            function popupOscarConS(vheight, vwidth, varpage, event) {
                event.preventDefault(); 
                event.stopPropagation(); 
                
                var popup = window.open(varpage, "...", 
                    "height="+vheight+",width="+vwidth+",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes");
                popup.focus();
                return false;
            }
        </script>
    </head>

    <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/messenger/styles.css">
    <body topmargin="0" leftmargin="0" vlink="#0000FF">
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
    <table>
        <tr>
            <td class=Title colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Measurements.msgGroup"/></td>
        </tr>
        <tr>
            <td>
                <table class=messButtonsA cellspacing=0 cellpadding=3>
                    <tr>
                        <td class="messengerButtonsA" width="200"><a href=#
                                                                     onClick="popupOscarConS(300,1000,'oscarEncounter/oscarMeasurements/SetupStyleSheetList.do', event)"
                                                                     class="messengerButtons"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.measurements.addMeasurementGroup"/></a></td>
                    </tr>
                </table>
            </td>
            <td>
                <table class=messButtonsA cellspacing=0 cellpadding=3>
                    <tr>
                        <td class="messengerButtonsA" width="200"><a href=#
                                                                     onClick="popupOscarConS(300,1000,'oscarEncounter/oscarMeasurements/SetupGroupList.do', event)"
                                                                     class="messengerButtons"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.measurements.editMeasurementGroup"/></a></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class=Title colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Measurements.msgType"/></td>
        </tr>
        <tr>
            <td>
                <table class=messButtonsA cellspacing=0 cellpadding=3>
                    <tr>
                        <td class="messengerButtonsA" width="200"><a href=#
                                                                     onClick="popupOscarConS(700,1000,'oscarEncounter/oscarMeasurements/SetupDisplayMeasurementTypes.do', event)"
                                                                     class="messengerButtons"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.measurements.viewMeasurementType"/></a></td>
                    </tr>
                </table>
            </td>
            <td>
                <table class=messButtonsA cellspacing=0 cellpadding=3>
                    <tr>
                        <td class="messengerButtonsA" width="200"><a href=#
                                                                     onClick="popupOscarConS(300,1000,'oscarEncounter/oscarMeasurements/SetupAddMeasurementType.do', event)"
                                                                     class="messengerButtons"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.measurements.addMeasurementType"/></a></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class=Title colspan="2">Mappings --
                <a href=# onClick="popupOscarConS(300,1000,'oscarEncounter/oscarMeasurements/viewMeasurementMap.jsp', event)" class="messengerButtons">View
                    Mapping</a></td>
        </tr>
        <tr>
            <td>
                <table class=messButtonsA cellspacing=0 cellpadding=3>
                    <tr>
                        <td class="messengerButtonsA" width="200"><a href=#
                                                                     onClick="popupOscarConS(700,1000,'oscarEncounter/oscarMeasurements/AddMeasurementMap.do', event)"
                                                                     class="messengerButtons">Add Measurement
                            Mapping</a></td>
                    </tr>
                </table>
            </td>
            <td>
                <table class=messButtonsA cellspacing=0 cellpadding=3>
                    <tr>
                        <td class="messengerButtonsA" width="200"><a href=#
                                                                     onClick="popupOscarConS(600,700,'oscarEncounter/oscarMeasurements/RemoveMeasurementMap.do', event)"
                                                                     class="messengerButtons">Remove/Remap Measurement
                            Mapping</a></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class=Title colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Measurements.msgMeasuringInstruction"/></td>
        </tr>
        <tr>
            <td>
                <table class=messButtonsA cellspacing=0 cellpadding=3>
                    <tr>
                        <td class="messengerButtonsA" width="200"><a href=#
                                                                     onClick="popupOscarConS(300,1000,'oscarEncounter/oscarMeasurements/SetupAddMeasuringInstruction.do', event)"
                                                                     class="messengerButtons"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.measurements.addMeasuringInstruction"/></a>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class=Title colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Measurements.msgStyleSheets"/></td>
        </tr>
        <tr>
            <td>
                <table class=messButtonsA cellspacing=0 cellpadding=3>
                    <tr>
                        <td class="messengerButtonsA" width="200"><a href=#
                                                                     onClick="popupOscarConS(300,1000,'oscarEncounter/oscarMeasurements/SetupDisplayMeasurementStyleSheet.do', event)"
                                                                     class="messengerButtons"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.measurements.viewMeasurementStyleSheet"/></a>
                        </td>
                    </tr>
                </table>
            </td>
            <td>
                <table class=messButtonsA cellspacing=0 cellpadding=3>
                    <tr>
                        <td class="messengerButtonsA" width="200"><a href=#
                                                                     onClick="popupOscarConS(300,1000,'oscarEncounter/oscarMeasurements/AddMeasurementStyleSheet.jsp', event)"
                                                                     class="messengerButtons"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.measurements.addMeasurementStyleSheet"/></a>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td></td>
        </tr>
    </table>
    </body>
</html>
