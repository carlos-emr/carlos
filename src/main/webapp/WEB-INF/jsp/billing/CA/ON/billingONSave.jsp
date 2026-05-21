<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.

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
<%--
  Purpose: Supports billingONSave in the Ontario billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%--
    billingONSave.jsp (view) - Ontario billing save success page.
    Closes the billing window and triggers a schedule refresh via BroadcastChannel.
    Rendered by BillingOnSave2Action on successful save.
    @since 2026
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
</head>
<body>
<c:choose>
    <c:when test="${billingFailed}">
        <h1>Sorry, billing has failed. Please do it again!</h1>
        <c:if test="${not empty billingFailureReason}">
            <p><strong>Reason:</strong> <carlos:encode value="${billingFailureReason}"/></p>
        </c:if>
    </c:when>
    <c:when test="${addAnotherBill}">
        <script type="text/javascript">
            try { if (self.opener && self.opener.refresh) { self.opener.refresh(); } else { new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh'); } } catch(e) { new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh'); }
            <c:choose>
                <c:when test="${not empty safeUrlBack}">
                    self.location.href = "<carlos:encode value='${safeUrlBack}' context='javaScript'/>";
                </c:when>
                <c:otherwise>
                    self.close();
                </c:otherwise>
            </c:choose>
        </script>
    </c:when>
    <c:otherwise>
        <script type="text/javascript">
            self.close();
            try { if (self.opener && self.opener.refresh) { self.opener.refresh(); } else { new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh'); } } catch(e) { new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh'); }
        </script>
    </c:otherwise>
</c:choose>
</body>
</html>

