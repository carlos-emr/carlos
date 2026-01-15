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

<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@page import="java.util.*,ca.openosp.openo.demographic.data.*,java.util.*,ca.openosp.openo.prevention.*,ca.openosp.openo.encounter.oscarMeasurements.*,ca.openosp.openo.encounter.oscarMeasurements.bean.*,java.net.*" %>
<%@page import="org.jdom2.Element,ca.openosp.openo.encounter.oscarMeasurements.data.*,org.jdom2.output.Format,org.jdom2.output.XMLOutputter,ca.openosp.openo.encounter.oscarMeasurements.util.*" %>
<%@page import="ca.openosp.openo.commn.dao.FlowSheetUserCreatedDao,ca.openosp.openo.commn.model.FlowSheetUserCreated,ca.openosp.openo.utility.SpringUtils" %>
<%
    FlowSheetUserCreatedDao flowSheetUserCreatedDao = (FlowSheetUserCreatedDao) SpringUtils.getBean(FlowSheetUserCreatedDao.class);
    List<FlowSheetUserCreated> flowsheets = flowSheetUserCreatedDao.getAllUserCreatedFlowSheets();
%>
<!DOCTYPE html>

<html>
<head>
        <title>Create New Flowsheet</title>
        <link href="<%=request.getContextPath()%>/css/bootstrap.css" rel="stylesheet" type="text/css">
    <script type="text/javascript">
        function checkForm() {
            var displayName = document.getElementById("displayName").value;
            var dxcodeTriggers = document.getElementById("dxcodeTriggers").value;
            var warningColour = document.getElementById("warningColour").value;
            var recommendationColour = document.getElementById("recommendationColour").value;

            if (displayName === "") {
                alert("Display Name can not be blank.");
                document.getElementById("displayName").focus();
                return false;
            }
            if (dxcodeTriggers === "") {
                alert("Dxcode Triggers can not be blank.");
                document.getElementById("dxcodeTriggers").focus();
                return false;
            }
            if (warningColour === "") {
                alert("Warning colour can not be blank.");
                document.getElementById("warningColour").focus();
                return false;
            }
            if (recommendationColour === "") {
                alert("Recommendation Colour can not be blank.");
                document.getElementById("recommendationColour").focus();
                return false;
            }

        }
    </script>
</head>
<body>
    <div class="navbar" id="demoHeader"><div class="navbar-inner">
        <a class="brand" href="javascript:void(0)">New Flowsheet</a>
    </div></div>
<form action="FlowSheetCustomAction.do" onsubmit="return checkForm()">
    <input type="hidden" name="method" value="createNewFlowSheet"/>
    <table border="0">
<tr>
    <td><label for="displayName">Name: </label></td>
    <td><input type="text" name="displayName" id="displayName"/></td>
</tr>
<tr>
    <td><label for="dxcodeTriggers">Trigger: </label></td>
    <td><input type="text" name="dxcodeTriggers" id="dxcodeTriggers"/> (eg icd9:250)</td>
</tr>
<tr>
    <td><label for="warningColour">Warning Colour: </label></td>
    <td><input type="text" name="warningColour" id="warningColour"/> (eg red or #E00000)</td>
</tr>
<tr>
    <td><label for="recommendationColour">Recommendation Colour: </label></td>
    <td><input type="text" name="recommendationColour" id="recommendationColour"/> (eg yellow)</td>
</tr>

    </table>
    <input type="submit" name="Submit" value="Create"/>
</form>
</body>
</html>
