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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%--
    RptByExamplesFavorite.jsp
    =========================
    Purpose: Edit a saved Query-by-Example favorite before returning to the
             favorites list.

    Features:
    - Requires _report or _admin read privilege
    - Localized favorite-name and SQL editing form
    - POST-only submission to RptByExamplesFavorite

    Parameters (set by backing action):
    - favoriteName — Display name for the favorite
    - newQuery     — SQL text being edited

    @since 2001-2002
--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_report&type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html>
    <head>
        <link rel="icon" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/images/favicon.ico"/>
        <link rel="stylesheet" type="text/css"
              href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/encounter/encounterStyles.css"/>
        <script type="text/javascript" src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/global.js"></script>
        <title><fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/> - <fmt:message key="oscarReport.RptByExample.MsgEditMyFavorite"/></title>
    </head>

    <body vlink="#0000FF" class="BodyStyle">
    <form action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/oscarReport/RptByExamplesFavorite" method="post">
        <input type="hidden" name="id" value="${carlos:forHtmlAttribute(id)}"/>
    <table class="MainTable" id="scrollNumber1">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:message key="oscarReport.CDMReport.msgReport"/></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td><fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/> - <fmt:message key="oscarReport.RptByExample.MsgEditMyFavorite"/></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" valign="top"></td>
            <td class="MainTableRightColumn">
                <table>
                    <tr>
                       <td><input type="text" name="favoriteName" size="40" value="${carlos:forHtmlAttribute(favoriteName)}"/></td>
                    </tr>
                    <tr>
                        <td><textarea name="query" cols="80" rows="3">${carlos:forHtmlContent(query)}</textarea></td>
                    </tr>
                    <tr>
                        <td><input type="submit" value="<fmt:message key='global.btnAdd'/>"/> <input
                                type="button"
                                value="<fmt:message key='oscarReport.RptByExample.MsgCancel'/>"
                                onclick="history.back();"/></td>
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
