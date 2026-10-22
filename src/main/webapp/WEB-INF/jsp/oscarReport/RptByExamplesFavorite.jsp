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
        <style>
            .report-favorite-layout {
                display: grid;
                grid-template-columns: minmax(0, 130px) minmax(400px, 1fr);
            }
            .report-favorite-layout > div {
                box-sizing: border-box;
            }
            .report-favorite-title {
                align-items: center;
                display: flex;
                padding: 0 6px;
            }
            .report-favorite-content {
                height: auto;
                padding: 8px 6px;
            }
            .report-favorite-fields {
                display: grid;
                gap: 8px;
            }
            .report-favorite-field {
                align-items: center;
                display: flex;
                gap: 8px;
            }
            .report-favorite-field label {
                flex: 0 0 100px;
                font-weight: bold;
            }
            .report-favorite-query-field {
                align-items: flex-start;
            }
            .report-favorite-actions {
                padding-left: 108px;
            }
        </style>
        <title><fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/> - <fmt:message key="oscarReport.RptByExample.MsgEditMyFavorite"/></title>
    </head>

    <body vlink="#0000FF" class="BodyStyle">
    <form action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/oscarReport/RptByExamplesFavorite" method="post">
        <input type="hidden" name="id" value="${carlos:forHtmlAttribute(id)}"/>
        <div class="MainTable report-favorite-layout" id="scrollNumber1">
            <div class="MainTableTopRowLeftColumn report-favorite-title">
                <fmt:message key="oscarReport.CDMReport.msgReport"/>
            </div>
            <div class="MainTableTopRowRightColumn report-favorite-title">
                <div class="TopStatusBar report-favorite-title">
                    <fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/> -
                    <fmt:message key="oscarReport.RptByExample.MsgEditMyFavorite"/>
                </div>
            </div>
            <div class="MainTableLeftColumn"></div>
            <div class="MainTableRightColumn report-favorite-content">
                <div class="report-favorite-fields">
                    <div class="report-favorite-field">
                        <label for="favoriteName"><fmt:message key="oscarReport.RptByExample.MsgMyFavorites"/></label>
                        <input id="favoriteName" type="text" name="favoriteName" size="40"
                               value="${carlos:forHtmlAttribute(favoriteName)}"/>
                    </div>
                    <div class="report-favorite-field report-favorite-query-field">
                        <label for="query"><fmt:message key="oscarReport.RptByExample.MsgQuery"/></label>
                        <textarea id="query" name="query" cols="80" rows="3">${carlos:forHtmlContent(query)}</textarea>
                    </div>
                    <div class="report-favorite-field report-favorite-actions">
                        <input type="submit" value="<fmt:message key='global.btnAdd'/>"/>
                        <input type="button"
                               value="<fmt:message key='oscarReport.RptByExample.MsgCancel'/>"
                               onclick="history.back();"/>
                    </div>
                </div>
            </div>
            <div class="MainTableBottomRowLeftColumn"></div>
            <div class="MainTableBottomRowRightColumn"></div>
        </div>
    </form>
    </body>
</html>
