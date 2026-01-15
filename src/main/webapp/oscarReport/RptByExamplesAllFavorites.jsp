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
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="java.util.*,ca.openosp.openo.report.data.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<link rel="stylesheet" type="text/css"
      href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">
<html lang="en">

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/> - <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgMyFavorites"/></title>

    </head>
    <script type="text/javascript">
        function set(text1, text2) {
            document.forms[0].newQuery.value = text1;
            document.forms[0].newName.value = text2;
        };

        function confirmDelete(id) {
            var answer = confirm("Are you sure you want to delete the selected query?");
            if (answer) {
                document.forms[0].toDelete.value = 'true';
                document.forms[0].id.value = id;
                document.forms[0].submit();
            }
        };

        function closeAndRefresh() {
            self.opener.document.location.reload();
            self.close();
        }
    </script>
    <body vlink="#0000FF" class="BodyStyle">

    <form action="${pageContext.request.contextPath}/oscarReport/RptByExamplesFavorite.do">
        <table class="MainTable" id="scrollNumber1" name="encounterTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgReport"/></td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar">
                        <tr>
                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/> - <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgMyFavorites"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="MainTableLeftColumn" valign="top"></td>
                <td class="MainTableRightColumn">
                    <table>
                        <tr class="Header">
                            <td align="left" width="150"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgName"/></td>
                            <td align="left" width="500"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgQuery"/></td>
                            <td width="100"></td>
                        </tr>
                        <input type="hidden" name="newName"/>
                        <input type="hidden" name="newQuery"/>
                        <input type="hidden" name="toDelete" value="false"/>
                        <input type="hidden" name="id" value="error"/>
                        <c:forEach var="favorite" items="${allFavorites.favoriteVector}">
                        <tr class="data">
                            <td><c:out value="${favorite.queryName}"/></td>
                            <td><c:out value="${favorite.query}"/></td>
                            <td><input type="button" name="editButton"
                                       value="<fmt:setBundle basename='oscarResources'/><fmt:message key='oscarReport.RptByExample.MsgEdit'/>"
                                       onClick="javascript:set('${favorite.queryWithEscapeChar}','${favorite.queryName}'); submit(); return false;"/><input
                                    type="button" name="deleteButton"
                                    value="<fmt:setBundle basename='oscarResources'/><fmt:message key='oscarReport.RptByExample.MsgDelete'/>"
                                    onClick="javascript:confirmDelete('${favorite.id}'); return false;"/>
                            </td>
                </td>
            </tr>
            </c:forEach>
            <tr>
                <td><input type="button"
                           value="<fmt:setBundle basename='oscarResources'/><fmt:message key='global.btnClose'/>"
                           onClick="javascript:closeAndRefresh();"/>
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
