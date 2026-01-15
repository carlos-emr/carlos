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
<!DOCTYPE html>
<%-- This JSP is the first page you see when you enter 'report by template' --%>
<%@page import="ca.openosp.openo.commn.dao.DemographicDao" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@page import="ca.openosp.openo.commn.dao.FlowSheetUserCreatedDao" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    Provider provider = loggedInInfo.getLoggedInProvider();
%>
<html>
    <head>
        <title>Flowsheet Editor</title>
        <link href="<%=request.getContextPath() %>/css/bootstrap.css" rel="stylesheet" type="text/css">
        <script src="<%=request.getContextPath() %>/js/global.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="<%=request.getContextPath() %>/share/javascript/Oscar.js"></script>


        <%
            String flowsheetId = request.getParameter("flowsheetId");
            String measurementType = request.getParameter("measurementType");
        %>
        <script>
            $(document).ready(function () {

            });

            function saveItem() {
                jQuery.post('<%=request.getContextPath()%>/admin/Flowsheet.do?method=saveFlowsheetItemWarning',
                    jQuery('#theForm').serialize(),
                    function (data) {
                        location.href = '<%=request.getContextPath()%>/oscarEncounter/oscarMeasurements/adminFlowsheet/FlowsheetItemEditor.jsp?flowsheetId=<%=flowsheetId %>&measurementType=<%=measurementType%>';
                    });
            }
        </script>
    </head>

    <!--
    <recommendation strength="recommendation" >
    <condition type="monthrange" param="BMI" value="3-6" />
    </recommendation>

    -->
    <body>
    <h2>Flowsheet Item Editor</h2>
    <br/>
    <form name="theForm" id="theForm">
        <input type="hidden" name="flowsheetId" value="<%=flowsheetId %>"/>
        <input type="hidden" name="measurementType" value="<%=measurementType %>"/>

        <table style="width:20%">
            <tr>
                <td><b>Strength:</b></td>
                <td>
                    <select name="strength" id="strength">
                        <option value="recommendation">recommendation</option>
                        <option value="warning">warning</option>
                    </select>
                </td>
            </tr>
            <tr>
                <td><b>Message:</b></td>
                <td><input type="text" name="message" id="message" value=""/></td>
            </tr>
            <tr>
                <td><b>Condition:</b></td>
                <td>
                    <select name="condition" id="condition">
                        <option value="monthrange">Month Range</option>
                        <option value="lastValueAsInt">Last Int Value</option>
                    </select>
                </td>
            </tr>
            <tr>
                <td><b>Parameter:</b></td>
                <td><input type="text" name="param" id="param" value=""/>(Measurement Type)</td>
            </tr>
            <tr>
                <td><b>Value:</b></td>
                <td>
                    <input type="text" name="value" id="value" value=""/>
                </td>
            </tr>
            <tr>
                <td colspan="2">
                    <input type="button" class="btn btn-primary" value="Save" onClick="saveItem()"/>
                </td>
            </tr>
        </table>
    </form>

    </body>
</html>