<!DOCTYPE html>

<%--
  Purpose: Tells the provider that an email compose window can no longer be used.
  Key features: Shown instead of the compose screen when its prepared state has been sent,
  has expired, belongs to another session, or was never staged (#3632). It depends on no eForm
  or patient context, unlike the eForm error page, which cannot render without one.
  Request attributes: errorMessage (a fixed, PHI-free message set by EmailCompose2Action).
  @since 2026-09-28
--%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <fmt:message key="email.compose.title" var="emailComposeTitle"/>
    <fmt:message key="email.compose.btn.close" var="emailComposeClose"/>
    <title>${emailComposeTitle}</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" type="text/css"/>
</head>
<body class="p-3">
<div class="alert alert-warning" role="alert" id="emailComposeExpired">
    <carlos:encode value="${errorMessage}"/>
</div>
<input type="button" class="btn btn-secondary btn-md" value="${carlos:forHtmlAttribute(emailComposeClose)}" onclick="window.close();"/>
</body>
</html>
