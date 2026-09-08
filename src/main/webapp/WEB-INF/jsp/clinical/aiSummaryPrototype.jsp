<%-- Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Clinical summary prototype | CARLOS EMR</title>
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/css/ai-summary-prototype.css">
    <script defer src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/ai-summary-prototype.js"></script>
</head>
<body>
<header class="app-header"><strong>CARLOS EMR</strong><span>Clinical research</span></header>
<main>
    <div class="page-heading">
        <div><h1>Clinical summary</h1><p><carlos:encode value="${summaryArtifact.patient_context.label}"/></p></div>
        <strong class="research-label">Research prototype / Synthetic data</strong>
    </div>
    <p class="notice">Unverified draft. Not for clinical use. No live chart data.</p>
    <div class="workspace">
        <div class="draft">
            <nav class="view-tabs" aria-label="Summary views">
                <a href="#summary">Summary</a><a href="#ledger">Fact ledger</a>
                <a href="#coverage">Coverage</a><a href="#validation">Validation</a>
                <a href="#evidence" class="evidence-shortcut">Evidence</a>
            </nav>
            <section id="summary" class="view-panel">
                <h2>Summary</h2>
                <c:choose>
                    <c:when test="${not summaryRenderable}">
                        <p class="notice error">Summary withheld: this artifact contains validation errors.</p>
                    </c:when>
                    <c:when test="${empty summaryArtifact.claims}">
                        <p>No summary claims available.</p>
                    </c:when>
                    <c:otherwise>
                        <c:forEach items="${summaryArtifact.sections}" var="section">
                            <section class="summary-section">
                                <h3><carlos:encode value="${section.title}"/></h3>
                                <c:if test="${empty section.claim_ids}"><p>No claims in this section.</p></c:if>
                                <c:forEach items="${section.claim_ids}" var="claimId">
                                    <c:set var="claim" value="${summaryClaims[claimId]}"/>
                                    <p><carlos:encode value="${claim.text}"/>
                                        <span class="citations">
                                            <c:forEach items="${claim.source_ids}" var="sourceId">
                                                <a href="#source-${carlos:forHtmlAttribute(sourceId)}" class="citation"><carlos:encode value="${sourceId}"/></a>
                                            </c:forEach>
                                        </span>
                                    </p>
                                </c:forEach>
                            </section>
                        </c:forEach>
                    </c:otherwise>
                </c:choose>
            </section>
            <section id="ledger" class="view-panel">
                <h2>Fact ledger</h2>
                <c:if test="${empty summaryArtifact.fact_ledger}"><p>No ledger entries available.</p></c:if>
                <c:forEach items="${summaryArtifact.fact_ledger}" var="fact">
                    <article class="ledger-entry"><h3><carlos:encode value="${fact.category}"/></h3>
                        <p><carlos:encode value="${fact.text}"/></p>
                        <c:forEach items="${fact.source_ids}" var="sourceId">
                            <a href="#source-${carlos:forHtmlAttribute(sourceId)}" class="citation"><carlos:encode value="${sourceId}"/></a>
                        </c:forEach>
                    </article>
                </c:forEach>
            </section>
            <section id="coverage" class="view-panel">
                <h2>Source coverage</h2>
                <p class="muted">Coverage describes this synthetic bundle only.</p>
                <c:forEach items="${summaryArtifact.coverage}" var="entry">
                    <article class="coverage-entry">
                        <a href="#source-${carlos:forHtmlAttribute(entry.source_id)}" class="citation"><carlos:encode value="${entry.source_id}"/></a>
                        <strong><carlos:encode value="${entry.status}"/></strong>
                        <p><carlos:encode value="${entry.reason}"/></p>
                    </article>
                </c:forEach>
            </section>
            <section id="validation" class="view-panel">
                <h2>Validation</h2>
                <p class="notice">Reference structure and source coverage passed runtime checks. Clinical accuracy and completeness remain unverified.</p>
                <h3>Artifact findings</h3>
                <c:if test="${empty summaryArtifact.validation}"><p>No artifact findings supplied.</p></c:if>
                <c:forEach items="${summaryArtifact.validation}" var="finding">
                    <article class="finding ${carlos:forHtmlAttribute(finding.severity)}">
                        <strong><carlos:encode value="${finding.severity}"/>: <carlos:encode value="${finding.code}"/></strong>
                        <p><carlos:encode value="${finding.message}"/></p>
                        <c:forEach items="${finding.source_ids}" var="sourceId">
                            <a href="#source-${carlos:forHtmlAttribute(sourceId)}" class="citation"><carlos:encode value="${sourceId}"/></a>
                        </c:forEach>
                    </article>
                </c:forEach>
            </section>
        </div>
        <aside id="evidence" aria-labelledby="evidence-heading">
            <h2 id="evidence-heading">Evidence</h2>
            <c:forEach items="${summaryArtifact.sources}" var="source">
                <details id="source-${carlos:forHtmlAttribute(source.id)}" class="source" open>
                    <summary><strong><carlos:encode value="${source.title}"/></strong><span><carlos:encode value="${source.id}"/> / <carlos:encode value="${source.date}"/></span></summary>
                    <p tabindex="-1"><carlos:encode value="${source.text}"/></p>
                </details>
            </c:forEach>
        </aside>
    </div>
    <footer><span>Artifact: <carlos:encode value="${summaryArtifact.artifact_id}"/></span>
        <span><carlos:encode value="${summaryArtifact.model}"/></span>
        <span><carlos:encode value="${summaryArtifact.generated_at}"/></span></footer>
</main>
</body>
</html>
