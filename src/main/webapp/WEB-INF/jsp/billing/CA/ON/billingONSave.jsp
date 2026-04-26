<%--
    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.
--%>
<%--
    billingONSave.jsp (view) - Ontario billing save success page.
    Closes the billing window and triggers a schedule refresh via BroadcastChannel.
    Rendered by BillingONSave2Action on successful save.
    @since 2026
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<!DOCTYPE html>
<html>
<head>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
</head>
<body>
<c:choose>
    <c:when test="${billingFailed}">
        <h1>Sorry, billing has failed. Please do it again!</h1>
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

