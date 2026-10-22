<%-- Copyright (c) 2026 CARLOS Contributors. GPL v2 or later. --%>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <title><fmt:message key="consultation.attachments.reviewTitle"/></title>
</head>
<body>
<main class="container py-4" style="max-width:760px;">
    <h1 class="h4"><fmt:message key="consultation.attachments.reviewTitle"/></h1>
    <p><fmt:message key="consultation.attachments.reviewMessage"/></p>
    <c:if test="${signatureNotApplied}">
        <div class="alert alert-warning" role="alert"><fmt:message key="encounter.oscarConsultationRequest.ConfirmConsultationRequest.signatureNotApplied"/></div>
    </c:if>
    <div class="alert alert-warning" role="alert">
        <ul>
            <c:forEach items="${attachmentWarnings}" var="warning">
                <c:set var="typeLabel"><fmt:message key="${warning.typeLabelKey}"/></c:set>
                <c:set var="warningText"><fmt:message key="${warning.messageKey}"><fmt:param value="${typeLabel}"/><fmt:param value="${warning.id}"/></fmt:message></c:set>
                <li><carlos:encode value="${warningText}"/></li>
            </c:forEach>
        </ul>
    </div>
    <c:choose>
        <c:when test="${attachmentReviewFax}"><c:set var="reviewAction" value="/encounter/oscarConsultationRequest/ConsultationFormFax"/></c:when>
        <c:otherwise><c:set var="reviewAction" value="/encounter/RequestConsultation"/></c:otherwise>
    </c:choose>
    <form method="post" action="${pageContext.request.contextPath}${reviewAction}">
        <c:choose>
            <c:when test="${attachmentReviewFax}">
                <c:forEach items="${pageContext.request.parameterMap}" var="entry">
                    <c:if test="${entry.key ne 'consultAttachmentApproval' and entry.key ne 'confirmAttachmentOmissions' and entry.key ne 'OWASP_CSRFTOKEN'}">
                        <c:forEach items="${entry.value}" var="value">
                            <input type="hidden" name="${carlos:forHtmlAttribute(entry.key)}" value="${carlos:forHtmlAttribute(value)}"/>
                        </c:forEach>
                    </c:if>
                </c:forEach>
            </c:when>
            <c:otherwise>
                <input type="hidden" name="submission" value="Review And Print Preview"/>
                <input type="hidden" name="requestId" value="${carlos:forHtmlAttribute(reqId)}"/>
                <input type="hidden" name="demographicNo" value="${carlos:forHtmlAttribute(demographicId)}"/>
            </c:otherwise>
        </c:choose>
        <input type="hidden" name="consultAttachmentApproval" value="${carlos:forHtmlAttribute(consultAttachmentApproval)}"/>
        <div class="form-check mb-3">
            <input class="form-check-input" type="checkbox" id="confirmAttachmentOmissions" name="confirmAttachmentOmissions" value="true" required/>
            <label class="form-check-label" for="confirmAttachmentOmissions"><fmt:message key="consultation.attachments.confirm"/></label>
        </div>
        <button type="submit" class="btn btn-primary"><fmt:message key="consultation.attachments.proceed"/></button>
        <c:url var="returnUrl" value="/encounter/ViewRequest"><c:param name="requestId" value="${reqId}"/><c:param name="de" value="${demographicId}"/></c:url>
        <a class="btn btn-outline-secondary" href="${carlos:forHtmlAttribute(returnUrl)}"><fmt:message key="consultation.attachments.cancel"/></a>
    </form>
</main>
</body>
</html>
