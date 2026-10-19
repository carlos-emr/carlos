<%--

    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos

--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%--
    Purpose:
        Refusal page for a signed-in user whose account has no schedule access, rendered by
        ViewProviderControl2Action in place of the schedule landing (issue #2689).

    Features:
        Says whether the account has no role at all or a role without schedule access, and what
        to ask an administrator for, instead of the generic "insufficient privileges" page.
        Answers 403: it is a refusal, and nothing of the schedule is rendered or requested.
        Offers Log Out through the standard logout broadcast page.

    Parameters:
        Request attribute noRoleAssigned (Boolean): true when the account holds no active role.

    @since 2026-10-15
--%>
<% response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN); %>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <meta charset="UTF-8">
    <link rel="icon" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/images/favicon.ico"/>
    <title><fmt:message key="provider.noScheduleAccess.title"/></title>
    <style>
        body { font-family: Helvetica, Arial, sans-serif; margin: 0; color: #222; background: #fff; }
        header { background: #486ebd; color: #fff; padding: 12px 16px; }
        header h1 { font-size: 1.25em; margin: 0; }
        main { max-width: 40em; padding: 16px; line-height: 1.5; }
        .logout { display: inline-block; margin-top: 8px; }
    </style>
</head>
<body>
<header>
    <h1 id="noScheduleAccessTitle"><fmt:message key="provider.noScheduleAccess.title"/></h1>
</header>
<main>
    <p id="noScheduleAccessReason" role="alert">
        <c:choose>
            <c:when test="${noRoleAssigned}">
                <fmt:message key="provider.noScheduleAccess.noRole"/>
            </c:when>
            <c:otherwise>
                <fmt:message key="provider.noScheduleAccess.noSchedulePrivilege"/>
            </c:otherwise>
        </c:choose>
    </p>
    <a id="noScheduleAccessLogout" class="logout"
       href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/logoutPage"><fmt:message key="global.btnLogout"/></a>
</main>
</body>
</html>
