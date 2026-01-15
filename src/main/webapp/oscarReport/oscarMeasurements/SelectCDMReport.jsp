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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page
        import="java.util.*,ca.openosp.openo.report.oscarMeasurements.pageUtil.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>


<link rel="stylesheet" type="text/css"
      href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgSelectCDMGroup"/></title>

        <script type="text/javascript">
            function set(target) {
                document.forms[0].forward.value = target;
            };

            function checkGroup() {
                var cdmGroupSelect = document.forms[0].elements['value(CDMgroup)'];
                if (cdmGroupSelect.options.length == 0) {
                    alert('A Measurement Group is required for this type of report');
                    return;
                }
                document.forms[0].submit();
            }
        </script>

    </head>

    <body class="BodyStyle" vlink="#0000FF">
    <!--  -->
    <form action="${pageContext.request.contextPath}/oscarReport/oscarMeasurements/SelectCDMReport.do" method="post">
        <table class="MainTable" id="scrollNumber1" name="encounterTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgReport"/></td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar">
                        <tr>
                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgTitle"/></td>
                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgSelectCDMGroup"/>
                                <select name="value(CDMgroup)">
                                    <c:forEach var="CDMGroup" items="${CDMGroups}">
                                        <option value="${CDMGroup.groupName}">
                                                ${CDMGroup.groupName}
                                        </option>
                                    </c:forEach>
                            </select></td>
                            <td style="text-align: right"><a
                                    href="javascript:popupStart(300,400,'About.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a> | <a
                                    href="javascript:popupStart(300,400,'License.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.license"/></a></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="MainTableLeftColumn"></td>
                <td class="MainTableRightColumn">
                    <table border=0 cellspacing=4 width=900>
                        <tr>
                            <td>
                                <table>
                                    <input type="hidden" name="forward" value="error"/>
                                    <tr>
                                        <td width="450" class="fieldBox"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgPercentageOfPatientWhoMetGuideline"/>
                                        </td>

                                        <td width="120" class="fieldBox"><input type="button"
                                                                                name="button"
                                                                                value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.btnContinue"/>"
                                                                                onclick="set('patientWhoMetGuideline');submit();checkGroup();"/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td width="450" class="fieldBox"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgPercentageOfPatientInAbnormalRange"/>
                                        </td>
                                        <td width="120" class="fieldBox"><input type="button"
                                                                                name="button"
                                                                                value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.btnContinue"/>"
                                                                                onclick="set('patientInAbnormalRange');submit();checkGroup();"/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td width="450" class="fieldBox"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgFrequencyOfRelevantTestsBeingPerformed"/>
                                        </td>
                                        <td width="120" class="fieldBox"><input type="button"
                                                                                name="button"
                                                                                value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.btnContinue"/>"
                                                                                onclick="set('freqencyOfReleventTests');submit();checkGroup();"/>
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
