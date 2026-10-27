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
                    <h3 class="h6"><fmt:message key="sourceNormalizedValue" bundle="${cds}"/></h3><pre class="text-wrap text-break"><carlos:encode value="${field.normalizedPreview}"/></pre>
                    <c:if test="${field.truncated}"><p class="text-warning"><fmt:message key="sourcePreviewTruncated" bundle="${cds}"/></p></c:if>
                </c:otherwise></c:choose>
                <c:url var="originalUrl" value="/demographic/cdsSourceFieldContent"><c:param name="demographicNo" value="${model.demographicNo}"/><c:param name="category" value="${model.category}"/><c:param name="fieldId" value="${field.id}"/></c:url>
                <a class="btn btn-sm btn-outline-secondary" href="${carlos:forHtmlAttribute(originalUrl)}"><fmt:message key="sourceDownload" bundle="${cds}"/></a>
            </div>
        </article>
    </c:forEach>
    <c:url var="firstUrl" value="/demographic/cdsSourceFields"><c:param name="demographicNo" value="${model.demographicNo}"/><c:param name="category" value="${model.category}"/></c:url>
    <a class="btn btn-outline-secondary" href="${carlos:forHtmlAttribute(firstUrl)}"><fmt:message key="sourceFirstPage" bundle="${cds}"/></a>
    <c:if test="${not empty model.nextCursor}">
        <c:url var="nextUrl" value="/demographic/cdsSourceFields"><c:param name="demographicNo" value="${model.demographicNo}"/><c:param name="category" value="${model.category}"/><c:param name="after" value="${model.nextCursor}"/></c:url>
        <a class="btn btn-outline-primary" href="${carlos:forHtmlAttribute(nextUrl)}"><fmt:message key="nextPage" bundle="${cds}"/></a>
    </c:if>
</main></body></html>
