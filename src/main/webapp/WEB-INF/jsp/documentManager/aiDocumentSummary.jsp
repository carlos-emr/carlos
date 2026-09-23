<%-- Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. --%>
<%-- Displays an on-demand, unsaved draft of one authorized Document Manager file.
     Input: request-scoped documentSummary* attributes from AiDocumentSummary2Action.
     Supporting excerpts are escaped plain text; extraction limits remain visible after generation. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><fmt:message key="documentSummary.title"/> | CARLOS EMR</title>
    <%@ include file="/WEB-INF/jspf/bootstrap-css.jspf" %>
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/css/ai-document-summary.css">
</head>
<body class="container py-3">
<header class="mb-3">
    <h1 class="h3"><fmt:message key="documentSummary.title"/></h1>
    <p class="text-muted mb-1"><carlos:encode value="${documentSummaryTitle}"/></p>
    <p class="alert alert-warning"><fmt:message key="documentSummary.warning"/></p>
</header>
<p class="alert alert-info"><carlos:encode value="${documentSummaryExtraction}"/></p>
<c:if test="${not empty documentSummaryError}">
    <p class="alert alert-danger" role="alert"><carlos:encode value="${documentSummaryError}"/></p>
</c:if>
<c:choose>
    <c:when test="${documentSummaryGenerated}">
        <details id="document-summary-overview" class="mb-3">
            <summary id="overview-heading"><fmt:message key="documentSummary.overview"/></summary>
            <p class="document-summary-text"><carlos:encode value="${documentSummaryOverview}"/></p>
        </details>
        <section class="document-summary-points" aria-labelledby="points-heading">
            <h2 id="points-heading" class="h4"><fmt:message key="documentSummary.points"/></h2>
            <c:forEach items="${documentSummaryPoints}" var="point">
                <article class="card mb-3"><div class="card-body">
                    <p class="card-text document-summary-text"><carlos:encode value="${point.text}"/></p>
                    <details><summary><fmt:message key="documentSummary.evidence"/></summary>
                        <c:forEach items="${point.evidence}" var="evidence"><blockquote class="border-start ps-3 mt-2 document-summary-text"><carlos:encode value="${evidence}"/></blockquote></c:forEach>
                    </details>
                </div></article>
            </c:forEach>
        </section>
    </c:when>
    <c:otherwise>
        <form id="document-summary-form" data-pending="<fmt:message key="documentSummary.pending"/>" method="post" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/GenerateAiDocumentSummary">
            <input type="hidden" name="documentId" value="${carlos:forHtmlAttribute(documentSummaryId)}">
            <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
            <button type="submit" class="btn btn-primary" <c:if test="${not documentSummaryAllowed}">disabled</c:if>><fmt:message key="documentSummary.generate"/></button>
        </form>
    </c:otherwise>
</c:choose>
<script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/ai-document-summary.js"></script>
</body>
</html>
