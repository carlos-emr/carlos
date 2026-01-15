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


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_eChart" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_eChart");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.immunization.config.createImmunizationSetinit.title"/>
        </title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

    </head>
    <script language="javascript">
        function BackToOscar() {
            window.close();
        }


    </script>


    <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/css/encounterStyles.css">
    <body topmargin="0" leftmargin="0" vlink="#0000FF"
          onload="window.focus();">
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
    <table border="0" cellpadding="0" cellspacing="0"
           style="border-collapse: collapse" bordercolor="#111111" width="100%"
           id="AutoNumber1" height="100%">
        <tr>
            <td width="100%"
                style="padding-left: 3; padding-right: 3; padding-top: 2; padding-bottom: 2"
                height="0%" colspan="2">

            </td>
        </tr>
        <tr>
            <td width="10%" height="37" bgcolor="#000000">&nbsp;</td>
            <td width="100%" bgcolor="#000000"
                style="border-left: 2px solid #A9A9A9; padding-left: 5" height="0%">
                <p class="ScreenTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.immunization.config.createImmunizationSetinit.msgCreateNewSet"/></p>
            </td>
        </tr>
        <tr>
            <td></td>
            <td width="100%" style="border-left: 2px solid #A9A9A9;" height="100%"
                valign="top">
                <table cellpadding="0" cellspacing="2"
                       style="border-collapse: collapse" bordercolor="#111111" width="100%"
                       height="100%">

                    <!----Start new rows here-->
                    <tr>
                        <td>
                            <div><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.immunization.config.createImmunizationSetinit.msgFollowSteps"/>
                            </div>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <div class="DivContentTitle"><br>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.immunization.config.createImmunizationSetinit.msgStep1"/><br>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.immunization.config.createImmunizationSetinit.msgStep2"/><br>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.immunization.config.createImmunizationSetinit.msgStep3"/><br>

                                <br>
                                <br>


                            </div>
                        </td>
                    </tr>
                    <tr>
                        <td><form action="${pageContext.request.contextPath}/oscarEncounter/immunization/config/CreateInitImmunization.do" method="post">
                            <table cellspacing="1">
                                <tr>
                                    <td class="cells"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.immunization.config.createImmunizationSetinit.formSetName"/>:
                                    </td>
                                    <td class="cells"><input type="text" name="setName" id="setName" /></td>
                                </tr>
                                <tr>
                                    <td class="cells"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.immunization.config.createImmunizationSetinit.formNRows"/>:
                                    </td>
                                    <td class="cells"><input type="text" name="numRows" id="numRows" /></td>
                                </tr>
                                <tr>
                                    <td class="cells"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.immunization.config.createImmunizationSetinit.formNCol"/>:
                                    </td>
                                    <td class="cells"><input type="text" name="numCols" id="numCols" /></td>
                                </tr>

                                <tr>
                                    <td><input type="submit"
                                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.immunization.config.createImmunizationSetinit.btnNext"/>"/>
                                    </td>
                                </tr>
                            </table>
                        </form></td>

                    </tr>


                    <!----End new rows here-->

                    <tr height="100%">
                        <td></td>
                    </tr>
                </table>
            </td>
        </tr>

        <tr>
            <td height="0%"
                style="border-bottom: 2px solid #A9A9A9; border-top: 2px solid #A9A9A9;"></td>
            <td height="0%"
                style="border-bottom: 2px solid #A9A9A9; border-top: 2px solid #A9A9A9;"></td>
        </tr>
        <tr>
            <td width="100%" height="0%" colspan="2">&nbsp;</td>
        </tr>
        <tr>
            <td width="100%" height="0%" style="padding: 5" bgcolor="#DCDCDC"
                colspan="2"></td>
        </tr>
    </table>
    </body>
</html>
