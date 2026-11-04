<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

    This software is published under the GPL GNU General Public License.

    Email preparation failed after the eForm was saved (#4477). The action restores this
    window's draft, so reloading retries its own compose URL. Keep this page independent
    of the eForm viewer: forwarding there renders an empty template and breaks relative links.
    Reached only through EmailCompose2Action's _email write privilege check.
--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="email.compose.error.title"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <style>
        body { padding: 2rem; }
        .email-error-card { max-width: 640px; margin: 0 auto; }
    </style>
</head>
<body>
<div class="card email-error-card">
    <div class="card-header bg-warning-subtle">
        <h1 class="h5 mb-0"><fmt:message key="email.compose.error.title"/></h1>
    </div>
    <div class="card-body">
        <p id="emailComposeErrorMessage" role="alert"><c:out value="${requestScope.errorMessage}"/></p>
        <p><fmt:message key="email.compose.error.saved"/></p>
        <button id="retryEmailCompose" type="button" class="btn btn-primary" onclick="window.location.reload()">
            <fmt:message key="email.compose.error.retry"/>
        </button>
        <button type="button" class="btn btn-secondary" onclick="window.close()">
            <fmt:message key="global.btnClose"/>
        </button>
    </div>
</div>
</body>
</html>
