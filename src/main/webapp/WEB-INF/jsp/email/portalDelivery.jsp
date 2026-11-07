<%@ page contentType="text/html;charset=UTF-8" %>
<%--
  Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
  This software is published under the GPL GNU General Public License.
--%>
<%--
  Purpose: Shows the portal password state of one encrypted email and offers the single recovery
  operation its state allows. Recovery publishes or revokes the password; it never resends email.
  Request attributes: emailLog (the stored email), recoveryView (a
  PortalEmailDeliveryService.RecoveryView name), portalRecoveryErrorKey (optional message key).
  Rendered by PortalEmailDelivery2Action (/email/portalDelivery).
  @since 2026-09-15
--%>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="email.portalDelivery.title"/></title>
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(ctx)}/library/bootstrap/5.3.8/css/bootstrap.min.css" type="text/css"/>
</head>
<body class="container py-4">
<h1 class="h3 mb-3"><fmt:message key="email.portalDelivery.title"/></h1>
<c:if test="${not empty portalRecoveryErrorKey}">
    <div class="alert alert-danger" role="alert"><fmt:message key="${portalRecoveryErrorKey}"/></div>
</c:if>
<c:if test="${not empty emailLog}">
    <p><fmt:message key="email.portalDelivery.reference"><fmt:param><carlos:encode value="${emailLog.id}"/></fmt:param></fmt:message></p>
    <c:set var="recoveryAction" value="${carlos:forHtmlAttribute(ctx)}/email/portalDelivery"/>
    <c:choose>
        <c:when test="${recoveryView eq 'WAIT'}">
            <p><fmt:message key="email.portalDelivery.state.wait"/></p>
        </c:when>
        <c:when test="${recoveryView eq 'CONFIRM_OUTCOME'}">
            <div class="alert alert-warning" role="alert"><fmt:message key="email.portalDelivery.state.confirmOutcome"/></div>
            <form method="post" action="${recoveryAction}">
                <input type="hidden" name="emailLogId" value="${carlos:forHtmlAttribute(emailLog.id)}"/>
                <div class="form-check mb-3">
                    <input class="form-check-input" type="checkbox" id="confirmed" name="confirmed" value="true" required/>
                    <label class="form-check-label" for="confirmed"><fmt:message key="email.portalDelivery.confirm.checked"/></label>
                </div>
                <button class="btn btn-primary" name="operation" value="confirmSent"><fmt:message key="email.portalDelivery.confirm.sent"/></button>
                <button class="btn btn-outline-danger" name="operation" value="confirmNotSent"><fmt:message key="email.portalDelivery.confirm.notSent"/></button>
            </form>
        </c:when>
        <c:when test="${recoveryView eq 'RETRY_PUBLISH' or recoveryView eq 'RETRY_REVOKE' or recoveryView eq 'UPDATE_RECORD'}">
            <p><fmt:message key="email.portalDelivery.state.${recoveryView eq 'RETRY_PUBLISH' ? 'retryPublish' : (recoveryView eq 'RETRY_REVOKE' ? 'retryRevoke' : 'updateRecord')}"/></p>
            <p class="text-muted"><fmt:message key="email.portalDelivery.neverResends"/></p>
            <form method="post" action="${recoveryAction}">
                <input type="hidden" name="emailLogId" value="${carlos:forHtmlAttribute(emailLog.id)}"/>
                <button class="btn btn-primary" name="operation" value="retry"><fmt:message key="email.portalDelivery.retry"/></button>
            </form>
        </c:when>
        <c:when test="${recoveryView eq 'PUBLISHED'}">
            <div class="alert alert-success" role="alert"><fmt:message key="email.portalDelivery.state.published"/></div>
        </c:when>
        <c:when test="${recoveryView eq 'REVOKED'}">
            <div class="alert alert-secondary" role="alert"><fmt:message key="email.portalDelivery.state.revoked"/></div>
        </c:when>
        <c:otherwise>
            <div class="alert alert-danger" role="alert"><fmt:message key="email.portalDelivery.state.inconsistent"/></div>
        </c:otherwise>
    </c:choose>
</c:if>
</body>
</html>
