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
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
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

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<link rel="stylesheet" type="text/css"
      href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">
<html>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/> - <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgAllQueriesExecuted"/></title>

        <script type="text/javascript">
            function set(text) {
                document.forms[1].newQuery.value = text;
            };
        </script>

    </head>

    <body vlink="#0000FF" class="BodyStyle">

    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgReport"/></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <form action="${pageContext.request.contextPath}/oscarReport/RptViewAllQueryByExamples.do" method="post">
                        <tr>
                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgAllQueriesExecutedFrom"/>:
                                <input type="text" name="startDate" value="${startDate}" size="8"/>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgTo"/>
                                <input type="text" name="endDate" value="${endDate}" size="8"/> <input type="submit"
                                                                         value="Refresh"/></td>
                        </tr>
                    </form>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" valign="top"></td>
            <td class="MainTableRightColumn">
                <table>
                    <tr class="Header">
                        <td align="left" width="140"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgDate"/></td>
                        <td align="left" width="400"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgQuery"/></td>
                        <td align="left" width="100"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgProvider"/></td>
                        <td align="left" width="100"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgAddToFavorite"/></td>
                    </tr>

                    <form id="favouriteForm" action="${pageContext.request.contextPath}/oscarReport/RptByExamplesFavorite.do" method="post">
                        <input type="hidden" id="newQuery" name="newQuery" value="error"/>
                        <c:forEach var="queryInfo" items="${allQueries.queryVector}">
                            <c:set var="escapedQuery" value="${queryInfo.queryWithEscapeChar}"/>

                            <tr class="data">
                                <td><c:out value="${queryInfo.date}"/></td>
                                <td><c:out value="${queryInfo.query}"/></td>
                                <td><c:out value="${queryInfo.providerLastName}"/>, <c:out value="${queryInfo.providerFirstName}"/></td>
                                <td>
                                    <input type="button"
                                        value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgAddToFavorite"/>"
                                        onclick="set('<c:out value="${escapedQuery}" escapeXml="true"/>'); submit();" />
                            </tr>
                        </c:forEach>
                    </form>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>

    </body>
</html>
