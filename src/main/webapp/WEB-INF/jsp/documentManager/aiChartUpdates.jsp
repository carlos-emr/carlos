<%-- Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<fmt:setBundle basename="oscarResources"/>
<!doctype html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title><fmt:message key="chartUpdates.title"/> | CARLOS</title>
    <%@ include file="/WEB-INF/jspf/bootstrap-css.jspf" %>
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/css/ai-chart-updates.css">
</head>
<body class="container-fluid py-3">
<header>
    <h1 class="h3"><fmt:message key="chartUpdates.title"/></h1>
    <p><fmt:message key="chartUpdates.patient"/>: <carlos:encode value="${chartUpdatePatientLabel}"/> (#<carlos:encode value="${chartUpdatePatient}"/>)</p>
    <p><carlos:encode value="${chartUpdateTitle}"/> · <carlos:encode value="${chartUpdateDate}"/></p>
    <p class="alert alert-warning"><fmt:message key="chartUpdates.warning"/></p>
</header>
<c:if test="${not empty chartUpdateError}"><p class="alert alert-danger" role="alert"><carlos:encode value="${chartUpdateError}"/></p></c:if>
<c:if test="${chartUpdateReady}">
<div class="chart-update-layout">
<main>
    <c:choose>
    <c:when test="${not empty chartUpdateReview}">
        <h2 class="h5"><fmt:message key="chartUpdates.proposals"/> (<carlos:encode value="${chartUpdateRows.size()}"/>)</h2>
        <p role="status"><fmt:message key="chartUpdates.remaining"><fmt:param value="${chartUpdateRemaining}"/></fmt:message></p>
        <c:if test="${chartUpdateRemaining == 0 and not empty chartUpdateRows}"><p class="alert alert-success"><fmt:message key="chartUpdates.complete"/></p></c:if>
        <c:if test="${empty chartUpdateRows}"><p><fmt:message key="chartUpdates.none"/></p></c:if>
        <c:forEach items="${chartUpdateRows}" var="proposal">
        <article class="card mb-3 proposal">
            <div class="card-body">
                <h3 class="h6"><c:choose><c:when test="${proposal.kind == 'tickler'}"><fmt:message key="chartUpdates.tickler"/></c:when><c:otherwise><fmt:message key="chartUpdates.history"/></c:otherwise></c:choose></h3>
                <details open><summary><fmt:message key="chartUpdates.evidence"/></summary><blockquote class="source-text"><carlos:encode value="${proposal.evidence}"/></blockquote></details>
                <c:choose>
                <c:when test="${not empty proposal.outcome}"><p class="alert alert-success mt-2" role="status"><carlos:encode value="${proposal.outcome}"/></p></c:when>
                <c:otherwise>
                <form class="proposal-form" method="post" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/ApplyAiChartUpdate">
                    <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
                    <input type="hidden" name="documentId" value="${carlos:forHtmlAttribute(chartUpdateDocumentId)}">
                    <input type="hidden" name="reviewToken" value="${carlos:forHtmlAttribute(chartUpdateReview.token)}">
                    <input type="hidden" name="proposalKey" value="${carlos:forHtmlAttribute(proposal.key)}">
                    <input type="hidden" name="chartFingerprint" value="${carlos:forHtmlAttribute(chartUpdateReview.fingerprint)}">
                    <label class="form-label mt-2" for="text-${carlos:forHtmlAttribute(proposal.key)}"><fmt:message key="chartUpdates.entry"/></label>
                    <textarea id="text-${carlos:forHtmlAttribute(proposal.key)}" class="form-control" name="entryText" rows="4" maxlength="2000" required><carlos:encode value="${proposal.text}"/></textarea>
                    <c:choose><c:when test="${proposal.kind == 'tickler'}">
                        <label class="form-label mt-2" for="due-${carlos:forHtmlAttribute(proposal.key)}"><fmt:message key="chartUpdates.due"/></label>
                        <input id="due-${carlos:forHtmlAttribute(proposal.key)}" class="form-control" type="date" name="dueDate" value="${carlos:forHtmlAttribute(proposal.dueDate)}" required>
                        <label class="form-label mt-2" for="assignee-${carlos:forHtmlAttribute(proposal.key)}"><fmt:message key="chartUpdates.assignee"/></label>
                        <select id="assignee-${carlos:forHtmlAttribute(proposal.key)}" class="form-select" name="assignee" required>
                            <option value=""><fmt:message key="chartUpdates.choose"/></option>
                            <c:forEach items="${chartUpdateProviders}" var="provider"><option value="${carlos:forHtmlAttribute(provider.providerNo)}" <c:if test="${proposal.assignee == provider.providerNo}">selected</c:if>><carlos:encode value="${provider.formattedName}"/></option></c:forEach>
                        </select>
                    </c:when><c:otherwise>
                        <label class="form-label mt-2" for="destination-${carlos:forHtmlAttribute(proposal.key)}"><fmt:message key="chartUpdates.destination"/></label>
                        <select id="destination-${carlos:forHtmlAttribute(proposal.key)}" class="form-select" name="destination" required>
                            <option value=""><fmt:message key="chartUpdates.choose"/></option>
                            <option value="MedHistory" <c:if test="${proposal.destination == 'MedHistory'}">selected</c:if>><fmt:message key="chartUpdates.medicalHistory"/></option>
                            <option value="Concerns" <c:if test="${proposal.destination == 'Concerns'}">selected</c:if>><fmt:message key="chartUpdates.concerns"/></option>
                        </select>
                        <p class="small mt-2"><fmt:message key="chartUpdates.signing"/></p>
                    </c:otherwise></c:choose>
                    <label class="d-block my-3"><input type="checkbox" name="confirmed" value="true" required> <fmt:message key="chartUpdates.confirm"/></label>
                    <button class="btn btn-primary" type="submit"><fmt:message key="chartUpdates.accept"/></button>
                    <button class="btn btn-outline-secondary" type="submit" formnovalidate formaction="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/DismissAiChartUpdate"><fmt:message key="chartUpdates.dismiss"/></button>
                </form>
                </c:otherwise></c:choose>
            </div>
        </article>
        </c:forEach>
    </c:when>
    <c:otherwise><p><fmt:message key="chartUpdates.intro"/></p></c:otherwise>
    </c:choose>
    <c:if test="${not empty chartUpdateReview}"><details class="regenerate mt-4"><summary><fmt:message key="chartUpdates.regenerate"/></summary><p class="mt-2"><fmt:message key="chartUpdates.regenerateHelp"/></p></c:if>
    <fmt:message key="chartUpdates.generating" var="generatingLabel"/>
    <form class="generation-form" method="post" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/documentManager/GenerateAiChartUpdates" data-busy-label="${carlos:forHtmlAttribute(generatingLabel)}">
        <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
        <input type="hidden" name="documentId" value="${carlos:forHtmlAttribute(chartUpdateDocumentId)}">
        <button class="btn btn-outline-primary" type="submit"><fmt:message key="chartUpdates.generate"/></button>
    </form>
    <c:if test="${not empty chartUpdateReview}"></details></c:if>
</main>
<aside>
    <h2 class="h5"><fmt:message key="chartUpdates.current"/></h2>
    <p class="small"><fmt:message key="chartUpdates.coverage"/></p>
    <c:if test="${empty chartUpdateEntries}"><p><fmt:message key="chartUpdates.noEntries"/></p></c:if>
    <c:forEach items="${chartUpdateEntries}" var="entry">
        <details class="mb-2"><summary><carlos:encode value="${entry.id}"/> · <carlos:encode value="${entry.kind}"/></summary><p class="source-text"><carlos:encode value="${entry.text}"/></p></details>
    </c:forEach>
    <details class="mt-4"><summary><fmt:message key="chartUpdates.fullSource"/></summary><pre class="source-text"><carlos:encode value="${chartUpdateSource}"/></pre></details>
</aside>
</div>
</c:if>
<script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/ai-chart-updates.js"></script>
</body>
</html>
