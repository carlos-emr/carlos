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
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ page
        import="java.util.*, ca.openosp.openo.encounter.oscarMeasurements.data.MeasurementMapConfig, ca.openosp.OscarProperties, ca.openosp.openo.util.StringUtils" %>

<%

%>

<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <title>Measurement Mapping Configuration</title>
        <!-- css -->
        <link href="<%=request.getContextPath()%>/css/bootstrap.css" rel="stylesheet"> <!-- Bootstrap 2.3.1 -->
        <script>

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
                }
                code = code.replace(/\D/g, "");

                // The Luhn Algorithm. It's so pretty.
                let nCheck = 0, bEven = false;

                for (var n = code.length - 1; n >= 0; n--) {
                    var cDigit = code.charAt(n),
                        nDigit = parseInt(cDigit, 10);

                    if (bEven && (nDigit *= 2) > 9) nDigit -= 9;

                    nCheck += nDigit;
                    bEven = !bEven;
                }

                if ((nCheck % 10) == 0) {
                    return true;
                } else {
                    alert("The loinc code specified is not a valid loinc code, please start the code with an 'X' if you would like to make your own.");
                    return false;
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

        </script>

    </head>
    <body>
    <form method="post" name="LOINC" action="oscarEncounter/oscarMeasurements/NewMeasurementMap.do"><input
            type="hidden" name="identifier" value="">
        <table style="width:100%">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRow" colspan="9" align="left">
                    <table style="width:100%">
                        <tr>
                            <td><input type="button" class="btn"
                                       value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/> "
                                       onClick="window.close()"></td>
                            <td style="text-align:right"><a
                                    href="javascript:popupStart(300,400, '<%= request.getContextPath() %>/oscarEncounter/About.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a> | <a
                                    href="javascript:popupStart(300,400, '<%= request.getContextPath() %>/oscarEncounter/License.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.license"/></a></td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
        <div class="well">
            <table>
                <tr>
                    <th colspan="2" class="Header">Add New Loinc
                        Code
                    </th>
                </tr>
                <tr>
                    <td>Loinc Code:</td>
                    <td><input type="text"
                               name="loinc_code"></td>
                </tr>
                <tr>
                    <td>Name:</td>
                    <td><input type="text" name="name"></td>
                </tr>
                <tr>
                    <td><input
                            type="submit" class="btn" value=" Add Loinc Code " onclick="return addLoinc()">
                    </td>
                </tr>
                <tr>
                    <td colspan="2">NOTE: <a
                            href="javascript:newWindow('https://loinc.org','RELMA')">It
                        is suggested that you use the RELMA application to help determine
                        correct loinc codes.</a></td>
                </tr>
            </table>
        </div>
    </form>
    </body>
</html>