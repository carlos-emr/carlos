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
    <%-- Chart-update assets load only with the feature on; nothing else on this page uses them. --%>
    <c:if test="${chartUpdatesEnabled}">
        <link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/css/ai-chart-updates-navigation.css">
    </c:if>
</head>
<body class="container py-3">
<header class="mb-3">
    <h1 class="h3"><fmt:message key="documentSummary.title"/></h1>
    <p class="text-muted mb-1"><carlos:encode value="${documentSummaryTitle}"/></p>
    <c:if test="${not documentSummaryReviewWorkflow}"><p class="alert alert-warning"><fmt:message key="documentSummary.warning"/></p></c:if>
</header>
<c:if test="${documentSummaryReviewAvailable and not documentSummaryReviewWorkflow}">
    <p><a class="btn btn-outline-primary" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/ReviewAiDocumentSummary?documentId=${carlos:forHtmlAttribute(documentSummaryId)}">Prepare a clinician-reviewed summary</a></p>
</c:if>
<c:if test="${documentSummaryReviewWorkflow}">
    <section class="alert alert-warning" aria-label="AI summary and review notice">
        <p class="document-summary-text"><carlos:encode value="${documentSummaryNotice}"/></p>
    </section>
    <p><a href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/ManageDocument?method=display&amp;doc_no=${carlos:forHtmlAttribute(documentSummaryId)}">Open current original document</a></p>
</c:if>
<p class="alert alert-info"><carlos:encode value="${documentSummaryExtraction}"/></p>
<c:if test="${not empty documentSummaryError}">
    <p class="alert alert-danger" role="alert"><carlos:encode value="${documentSummaryError}"/></p>
</c:if>
<c:choose>
    <c:when test="${documentSummaryGenerated}">
        <c:if test="${documentSummaryReviewWorkflow}">
            <p>Generated: <carlos:encode value="${documentSummaryGeneratedAt}"/></p>
            <p><a href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/ReviewedAiSummaryOriginal?documentId=${carlos:forHtmlAttribute(documentSummaryId)}&amp;draftId=${carlos:forHtmlAttribute(documentSummaryDraftId)}">Download the exact original used for this summary</a></p>
        </c:if>
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
        <c:if test="${documentSummaryReviewWorkflow}">
            <c:if test="${not documentSummaryReviewed}">
                <form method="post" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/ApproveReviewedAiDocumentSummary" class="my-3">
                    <input type="hidden" name="documentId" value="${carlos:forHtmlAttribute(documentSummaryId)}">
                    <input type="hidden" name="draftId" value="${carlos:forHtmlAttribute(documentSummaryDraftId)}">
                    <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
                    <label><input type="checkbox" name="affirmReview" value="true" required> I reviewed the original, this exact summary and the extraction limits and patient notice.</label>
                    <button type="submit" class="btn btn-primary d-block mt-2">Record my clinician review</button>
                </form>
            </c:if>
            <c:if test="${documentSummaryReviewed}">
                <p role="status">Clinician review is recorded for this original and summary version.</p>
            </c:if>
            <p>Summary delivery is unavailable. You can still send the original through the usual document workflow.</p>
            <form method="post" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/GenerateReviewedAiDocumentSummary" class="my-3">
                <input type="hidden" name="documentId" value="${carlos:forHtmlAttribute(documentSummaryId)}">
                <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
                <button type="submit" class="btn btn-outline-primary">Generate a new draft and clear this review</button>
            </form>
            <form method="post" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/DiscardReviewedAiDocumentSummary">
                <input type="hidden" name="documentId" value="${carlos:forHtmlAttribute(documentSummaryId)}">
                <input type="hidden" name="draftId" value="${carlos:forHtmlAttribute(documentSummaryDraftId)}">
                <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
                <button type="submit" class="btn btn-outline-secondary">Discard this draft and review</button>
            </form>
        </c:if>
    </c:when>
    <c:otherwise>
        <form id="document-summary-form" data-pending="<fmt:message key="documentSummary.pending"/>" method="post" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/${documentSummaryReviewWorkflow ? 'GenerateReviewedAiDocumentSummary' : 'GenerateAiDocumentSummary'}">
            <input type="hidden" name="documentId" value="${carlos:forHtmlAttribute(documentSummaryId)}">
            <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
            <button type="submit" class="btn btn-primary" <c:if test="${not documentSummaryAllowed}">disabled</c:if>><fmt:message key="documentSummary.generate"/></button>
        </form>
    </c:otherwise>
</c:choose>
<script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/ai-document-summary.js"></script>
<c:if test="${chartUpdatesEnabled}">
    <p class="mt-3"><a class="btn btn-outline-primary chart-update-document-link" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/AiChartUpdates?documentId=${carlos:forHtmlAttribute(documentSummaryId)}"><fmt:message key="chartUpdates.title"/></a></p>
    <%@ include file="/WEB-INF/jspf/chart-update-error-dialog.jspf" %>
    <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/ai-chart-updates-navigation.js"></script>
</c:if>
</body>
</html>
