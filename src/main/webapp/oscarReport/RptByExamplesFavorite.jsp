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
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<link rel="stylesheet" type="text/css"
      href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">
<html>
    <script language="JavaScript" type="text/JavaScript">

    </script>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/> - <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgEditMyFavorite"/></title>
    </head>

    <body vlink="#0000FF" class="BodyStyle">
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <form action="${pageContext.request.contextPath}/oscarReport/RptByExamplesFavorite.do" method="post">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgReport"/></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/> - <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgEditMyFavorite"/></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" valign="top"></td>
            <td class="MainTableRightColumn">
                <table>
                    <tr>
                       <td><input type="text" name="favoriteName" size="40" value="${favoriteName}"/></td>
                    </tr>
                    <tr>
                        <td><textarea name="query" cols="80" rows="3">${newQuery}</textarea></td>
                    </tr>
                    <tr>
                        <td><input type="button" value="Add" onclick="submit();"/> <input
                                type="button"
                                value="<fmt:setBundle basename='oscarResources'/><fmt:message key='oscarReport.RptByExample.MsgCancel'/>"
                                onclick="javascript:history.back(1);"/></td>
                    </tr>
                    <tr></tr>

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
