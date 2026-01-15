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

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ page
        import="java.util.*, ca.openosp.openo.encounter.oscarMeasurements.data.MeasurementMapConfig, ca.openosp.OscarProperties, ca.openosp.openo.util.StringUtils" %>

<%

%>

<link rel="stylesheet" type="text/css"
      href="<%= request.getContextPath() %>/oscarMDS/encounterStyles.css">

<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>Measurement Mapping Configuration</title>

    <script type="text/javascript" language=javascript>

        function newWindow(varpage, windowname) {
            var page = varpage;
            windowprops = "fullscreen=yes,toolbar=yes,directories=no,resizable=yes,dependent=yes,scrollbars=yes,location=yes,status=yes,menubar=yes";
            var popup = window.open(varpage, windowname, windowprops);
        }

        function addLoinc() {
            var loinc_code = document.LOINC.loinc_code.value;
            var name = document.LOINC.name.value;

            if (loinc_code.length > 0 && name.length > 0) {
                if (modCheck(loinc_code)) {
                    document.LOINC.identifier.value = loinc_code + ',PATHL7,' + name;
                    return true;
                }
            } else {
                alert("Please specify both a loinc code and a name before adding.");
            }

            return false;
        }

        function modCheck(code) {
            if (code.charAt(0) == 'x' || code.charAt(0) == 'X') {
                return true;
            } else {

                var codeArray = new Array();
                codeArray = code.split('-');
                var length = codeArray[0].length;

                var even = false;
                if ((length % 2) == 0) even = true;


                var oddNums = '';
                var evenNums = '';

                length--;
                for (length; length >= 0; length--) {
                    if (even) {
                        even = false;
                        evenNums = evenNums + codeArray[0].charAt(length);
                    } else {
                        even = true;
                        oddNums = oddNums + codeArray[0].charAt(length);
                    }
                }

                oddNums = oddNums * 2;
                var newNum = evenNums + oddNums;
                var sum = 0;


                for (var i = 0; i < newNum.length; i++) {
                    sum = sum + parseInt(newNum.charAt(i));
                }

                var newSum = sum;

                while ((newSum % 10) != 0) {
                    newSum++;
                }

                var checkDigit = newSum - sum;
                if (checkDigit == codeArray[1]) {
                    return true;
                } else {
                    alert("The loinc code specified is not a valid loinc code, please start the code with an 'X' if you would like to make your own.");
                    return false;
                }

            }

        }

        <%String outcome = request.getParameter("outcome");
        if (outcome != null){
            if (outcome.equals("success")){
                %>
        alert("Successfully added loinc code");
        window.opener.location.reload()
        window.close();
        <%
    }else if (outcome.equals("failedcheck")){
        %>
        alert("Unable to add code: The specified code already exists in the database");
        <%
    }else{
        %>
        alert("Failed to add the new code");
        <%
    }
}%>


        window.onload = stripe;

    </script>
    <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    <style type="text/css">


        .even {
            background-color: #ccccff;
        }
    </style>
</head>

<body>
<form method="post" name="LOINC" action="oscarEncounter/oscarMeasurements/NewMeasurementMap.do"><input
        type="hidden" name="identifier" value="">
    <table width="100%" height="100%" border="0">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRow" colspan="9" align="left">
                <table width="100%">
                    <tr>
                        <td align="left"><input type="button"
                                                value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/> "
                                                onClick="window.close()"></td>
                        <td align="right">

                            <a href="javascript:popupStart(300,400, '<%= request.getContextPath() %>/oscarEncounter/About.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a> |
                            <a href="javascript:popupStart(300,400, '<%= request.getContextPath() %>/oscarEncounter/License.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.license"/></a>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td valign="top">

                <table>
                    <tr>
                        <th valign="bottom" class="Header">MEAS</th>
                        <th valign="bottom" class="Header">Loinc Code</th>
                        <th valign="bottom" class="Header">Desc</th>
                        <th valign="bottom" class="Header">--</th>
                        <%
                            MeasurementMapConfig map = new MeasurementMapConfig();
                            List<String> types = map.getLabTypes();
                            types.remove("FLOWSHEET");
                            for (String type : types) {%>
                        <th valign="bottom" class="Header"><%=type%>
                        </th>
                        <%}%>
                    </tr>
                    <tbody>
                    <%


                        List<String> list = map.getDistinctLoincCodes();
                        boolean odd = true;
                        for (String s : list) {

                            List<HashMap<String, String>> codesHash = map.getMappedCodesFromLoincCodes(s);
                            String desc = "";
                            if (codesHash.size() > 0) {
                                desc = getDesc(codesHash.get(0));
                            }

                            HashMap<String, HashMap<String, String>> h = map.getMappedCodesFromLoincCodesHash(s);

                            String measurement = getDisplay(h, "FLOWSHEET");

                    %>

                    <tr style="background-color:<%=rowColour(odd)%>">
                        <td class="Cell">
                            <% if (measurement != null && !measurement.equals("&nbsp;")) {%>
                            <%=measurement%>
                            <%} else {%>
                            <a href="addMeasurementMap2.jsp?loinc=<%=s%>">map</a>
                            <%}%>
                        </td>
                        <td class="Cell"><%=s%>
                        </td>
                        <td class="Cell"><%=desc%>
                        </td>
                        <td class="Cell">&nbsp;</td>

                        <%for (String type : types) {%>
                        <td class="Cell"><%=getDisplay(h, type)%>
                        </td>
                        <%}%>


                    </tr>

                    <%
                            odd = !odd;
                        }%>
                    </tbody>
                    <tfoot>
                    <tr>
                        <td colspan="<%=4+types.size()%>" style="background-color:black;color:white" align="center">
                            Unmapped Codes
                        </td>
                    </tr>
                    <tr>
                        <td colspan="4">&nbsp;</td>
                        <%for (String type : types) {%>

                        <td valign="top" style="border: 1px solid black;">

                            <h4 class="Header" style="text-align:center"><%=type%>
                            </h4>
                            <ul>
                                <li>test</li>
                                <%
                                    ArrayList<HashMap<String, String>> unList = map.getUnmappedMeasurements(type);
                                    for (HashMap<String, String> h : unList) {
                                %>
                                <li><%=h.get(("name"))%>
                                </li>
                                <%}%>
                            </ul>
                        </td>
                        <%}%>
                    </tr>
                    </tfoot>
                </table>

            </td>
        </tr>
    </table>
</form>
</body>
</html>
<%!
    String rowColour(Boolean b) {
        if (b.booleanValue()) {
            b = Boolean.valueOf(!b);
            return "#DDDDDD";
        } else {

            return "#FFFFFF";
        }

    }


    String getDesc(HashMap<String, String> h) {
        return h.get("name");
    }

    String getDisplay(HashMap<String, HashMap<String, String>> h, String type) {
        HashMap<String, String> data = h.get(type);
        if (data == null) {
            return "&nbsp;";
        }
        return data.get("name") + ": " + data.get("ident_code");
    }

    String getCodeMap(List<HashMap<String, String>> list) {
        StringBuffer sb = new StringBuffer();

        for (HashMap<String, String> h : list) {
            sb.append(h.get("name") + " : " + h.get("lab_type") + "(" + h.get("ident_code") + ")   |  ");
        }
        return sb.toString();
    }
%>
