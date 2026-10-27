<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="cdsImportMessages" var="cds"/>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.response.locale.language)}">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title><fmt:message key="sourceFieldsTitle" bundle="${cds}"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css">
</head>
<body><main class="container-fluid p-3" id="cdsSourceFields">
    <h1 class="h4"><fmt:message key="sourceFieldsTitle" bundle="${cds}"/> — <carlos:encode value="${model.category}"/></h1>
    <p><fmt:message key="sourceFieldsHelp" bundle="${cds}"/></p>
    <p><fmt:message key="sourcePatient" bundle="${cds}"/>: <carlos:encode value="${model.demographicNo}"/></p>
    <c:if test="${empty model.fields}"><p role="status"><fmt:message key="sourceFieldsEmpty" bundle="${cds}"/></p></c:if>
    <c:forEach items="${model.fields}" var="field">
        <article class="card mb-3 cds-source-field" data-field-id="${carlos:forHtmlAttribute(field.id)}">
            <div class="card-body">
                <h2 class="h6 text-break"><carlos:encode value="${field.sourcePath}"/></h2>
                <dl class="row small">
                    <dt class="col-sm-2"><fmt:message key="sourceNamespace" bundle="${cds}"/></dt><dd class="col-sm-10 text-break"><carlos:encode value="${field.namespace}"/></dd>
                    <dt class="col-sm-2"><fmt:message key="sourceValueType" bundle="${cds}"/></dt><dd class="col-sm-10"><carlos:encode value="${field.valueType}"/></dd>
                    <dt class="col-sm-2"><fmt:message key="sourcePrecision" bundle="${cds}"/></dt><dd class="col-sm-10"><carlos:encode value="${field.precision}"/></dd>
                    <dt class="col-sm-2"><fmt:message key="sourceOccurrence" bundle="${cds}"/></dt><dd class="col-sm-10"><carlos:encode value="${field.occurrence}"/></dd>
                </dl>
                <c:choose><c:when test="${field.externalPayload}">
                    <p><fmt:message key="sourceExternalValue" bundle="${cds}"/> <carlos:encode value="${field.payloadBytes}"/> bytes</p>
                </c:when><c:otherwise>
                    <h3 class="h6"><fmt:message key="sourceOriginalValue" bundle="${cds}"/></h3><pre class="text-wrap text-break"><carlos:encode value="${field.originalPreview}"/></pre>
                </c:otherwise></c:choose>
                <c:if test="${field.normalizedAvailable}"><h3 class="h6"><fmt:message key="sourceNormalizedValue" bundle="${cds}"/></h3><pre class="text-wrap text-break"><carlos:encode value="${field.normalizedPreview}"/></pre></c:if>
                <c:if test="${field.truncated}"><p class="text-warning"><fmt:message key="sourcePreviewTruncated" bundle="${cds}"/></p></c:if>
                <c:url var="originalUrl" value="/demographic/cdsSourceFieldContent"><c:param name="demographicNo" value="${model.demographicNo}"/><c:param name="category" value="${model.category}"/><c:param name="fieldId" value="${field.id}"/></c:url>
                <a class="btn btn-sm btn-outline-secondary" href="${carlos:forHtmlAttribute(originalUrl)}"><fmt:message key="sourceDownload" bundle="${cds}"/></a>
            </div>
        </article>
    </c:forEach>
    <c:url var="firstUrl" value="/demographic/cdsSourceFields"><c:param name="demographicNo" value="${model.demographicNo}"/><c:param name="category" value="${model.category}"/></c:url>
    <a class="btn btn-outline-secondary" href="${carlos:forHtmlAttribute(firstUrl)}"><fmt:message key="sourceFirstPage" bundle="${cds}"/></a>
    <c:if test="${not empty model.nextCursor}">
        <c:url var="nextUrl" value="/demographic/cdsSourceFields"><c:param name="demographicNo" value="${model.demographicNo}"/><c:param name="category" value="${model.category}"/><c:param name="after" value="${model.nextCursor}"/><c:param name="reviewAfter" value="${model.reviewCursor}"/></c:url>
        <a class="btn btn-outline-primary" href="${carlos:forHtmlAttribute(nextUrl)}"><fmt:message key="nextPage" bundle="${cds}"/></a>
    </c:if>
<section class="mt-4" aria-labelledby="reviewProvenanceHeading">
        <h2 id="reviewProvenanceHeading" class="h5"><fmt:message key="sourceReviews" bundle="${cds}"/></h2>
        <p><fmt:message key="sourceReviewsHelp" bundle="${cds}"/></p>
        <c:if test="${empty model.reviews}"><p><fmt:message key="sourceReviewsEmpty" bundle="${cds}"/></p></c:if>
        <c:forEach items="${model.reviews}" var="review">
            <article class="card mb-3" id="review-${carlos:forHtmlAttribute(review.reviewId)}"><div class="card-body">
                <c:url var="reviewUrl" value="/demographic/cdsSourceFields"><c:param name="demographicNo" value="${model.demographicNo}"/><c:param name="category" value="${model.category}"/><c:param name="after" value="${model.fieldCursor}"/><c:param name="reviewAfter" value="${model.reviewCursor}"/></c:url>
                <h3 class="h6"><a href="${carlos:forHtmlAttribute(reviewUrl)}#review-${carlos:forHtmlAttribute(review.reviewId)}"><carlos:encode value="${review.reviewId}"/></a></h3>
                <dl class="row small">
                    <dt class="col-sm-3"><fmt:message key="sourceReviewOperator" bundle="${cds}"/></dt><dd class="col-sm-9"><carlos:encode value="${review.operator}"/></dd>
                    <dt class="col-sm-3"><fmt:message key="sourceReviewTime" bundle="${cds}"/></dt><dd class="col-sm-9"><carlos:encode value="${review.actedAt}"/></dd>
                    <dt class="col-sm-3"><fmt:message key="reviewKind" bundle="${cds}"/></dt><dd class="col-sm-9"><carlos:encode value="${review.kind}"/> — <carlos:encode value="${review.state}"/></dd>
                    <dt class="col-sm-3"><fmt:message key="sourceReviewVersion" bundle="${cds}"/></dt><dd class="col-sm-9"><carlos:encode value="${review.acceptedVersion}"/></dd>
                    <dt class="col-sm-3">XML XPath</dt><dd class="col-sm-9 text-break"><carlos:encode value="${review.sourceXPath}"/></dd>
                </dl>
                <p class="fw-bold"><fmt:message key="reason" bundle="${cds}"/></p><pre class="text-wrap text-break"><carlos:encode value="${review.reason}"/></pre>
            </div></article>
        </c:forEach>
        <c:if test="${not empty model.reviewNextCursor}">
            <c:url var="reviewNextUrl" value="/demographic/cdsSourceFields"><c:param name="demographicNo" value="${model.demographicNo}"/><c:param name="category" value="${model.category}"/><c:param name="after" value="${model.fieldCursor}"/><c:param name="reviewAfter" value="${model.reviewNextCursor}"/></c:url>
            <a class="btn btn-outline-primary" href="${carlos:forHtmlAttribute(reviewNextUrl)}"><fmt:message key="sourceReviewNextPage" bundle="${cds}"/></a>
        </c:if>
    </section>
</main></body></html>
