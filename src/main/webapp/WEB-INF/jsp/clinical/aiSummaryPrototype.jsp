<%-- Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Patient overview prototype | CARLOS EMR</title>
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/css/fontawesome-all.min.css">
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/css/ai-summary-prototype.css">
    <script defer src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/ai-summary-prototype.js"></script>
</head>
<body>
<header class="app-header">
    <strong>CARLOS EMR</strong><span class="current-workspace">Patient overview</span>
    <form class="demographic-picker" method="get" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/clinical/AiSummaryPrototype">
        <label for="demographic-no">Demographic #</label>
        <input id="demographic-no" name="demographicNo" type="number" min="1" max="2147483647" step="1" required value="${carlos:forHtmlAttribute(summaryDemographicNo)}">
        <button class="icon-button" type="submit" title="Open demographic" aria-label="Open demographic"><i class="fa-solid fa-arrow-right" aria-hidden="true"></i></button>
    </form>
</header>
<div class="synthetic-banner">
    <c:choose><c:when test="${summaryGenerated}">
        <strong><i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i> Unverified AI draft / Synthetic testing only</strong>
        <span>Agent-generated. Not saved to the chart.</span>
    </c:when><c:when test="${summaryArtifact.patient_context.synthetic}">
        <strong><i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i> Synthetic patient / Not for clinical use</strong>
        <span>Unverified draft. No live chart data.</span>
    </c:when><c:otherwise>
        <strong><i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i> Partial chart extract / Verify against the chart</strong>
        <span>Read-only. No AI generation.</span>
    </c:otherwise></c:choose>
</div>
<header class="patient-header">
    <div class="patient-main">
        <span class="patient-avatar" aria-hidden="true"><i class="fa-solid fa-user"></i></span>
        <div><h1><carlos:encode value="${summaryArtifact.patient_context.label}"/></h1>
            <div class="patient-meta"><span>Patient overview</span><span><carlos:encode value="${summaryArtifact.artifact_id}"/></span></div>
        </div>
    </div>
    <div class="header-status">
        <div class="status-line"><span class="status-label">${fn:length(summaryArtifact.sources)} sources in bundle</span><span class="status-label research-label">Research prototype</span></div>
        <span class="generated-at"><carlos:encode value="${summaryArtifact.generated_at}"/></span>
    </div>
</header>
<c:if test="${summaryGenerationEnabled}">
    <div class="generation-toolbar">
        <form id="generate-summary" method="post" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/clinical/GenerateAiSummary">
            <input type="hidden" name="demographicNo" value="${carlos:forHtmlAttribute(summaryDemographicNo)}">
            <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
            <c:choose><c:when test="${summaryGenerationAllowed}">
                <button type="submit" class="generate-button"><i class="fa-solid fa-wand-magic-sparkles" aria-hidden="true"></i> <span><c:choose><c:when test="${summaryGenerated}">Regenerate draft</c:when><c:otherwise>Generate AI draft</c:otherwise></c:choose></span></button>
            </c:when><c:otherwise>
                <button type="button" class="generate-button" disabled title="Requires a complete, unmodified NHS synthetic fixture and authorized note access"><i class="fa-solid fa-wand-magic-sparkles" aria-hidden="true"></i> Generate AI draft</button>
            </c:otherwise></c:choose>
        </form>
        <p id="generation-status" role="status" aria-live="polite"><c:choose><c:when test="${summaryGenerationAllowed}">Verified NHS synthetic fixture.</c:when><c:otherwise>Generation unavailable: a complete, unmodified NHS fixture and eChart note access are required.</c:otherwise></c:choose></p>
        <c:if test="${summaryGenerated}"><a class="chart-extract-link" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/clinical/AiSummaryPrototype?demographicNo=${carlos:forHtmlAttribute(summaryDemographicNo)}">Recorded chart facts</a></c:if>
    </div>
    <c:if test="${not empty summaryGenerationError}"><p class="generation-error notice error" role="alert"><carlos:encode value="${summaryGenerationError}"/></p></c:if>
</c:if>
<main class="workspace" id="workspace">
    <div class="draft">
        <div class="panel-top">
            <nav class="view-tabs" aria-label="Summary views">
                <a href="#summary">Overview</a><a href="#ledger">Fact ledger</a>
                <a href="#coverage">Coverage</a><a href="#validation">Validation</a>
            </nav>
            <button type="button" id="show-evidence" class="icon-button js-control" title="Show source evidence" aria-label="Show source evidence" aria-controls="evidence" aria-expanded="true" hidden><i class="fa-solid fa-columns" aria-hidden="true"></i></button>
        </div>
        <div class="reading-scroll">
            <section id="summary" class="view-panel">
                <div class="overview-layout">
                    <aside class="context-rail" aria-labelledby="context-heading">
                        <h2 id="context-heading">Record context</h2>
                        <dl class="record-counts">
                            <div><dt>Sources</dt><dd>${fn:length(summaryArtifact.sources)}</dd></div>
                            <div><dt>Summary claims</dt><dd>${fn:length(summaryArtifact.claims)}</dd></div>
                            <div><dt>Ledger entries</dt><dd>${fn:length(summaryArtifact.fact_ledger)}</dd></div>
                        </dl>
                        <h3>Source manifest</h3>
                        <ul class="source-manifest">
                            <c:forEach items="${summaryArtifact.sources}" var="source">
                                <li><a href="#source-${carlos:forHtmlAttribute(source.id)}" class="citation"><carlos:encode value="${source.title}"/></a><span><carlos:encode value="${source.date}"/></span></li>
                            </c:forEach>
                        </ul>
                        <p class="record-limit"><strong>Record limitation</strong>Only the sources listed here are represented. Missing information is not a negative clinical finding.</p>
                    </aside>
                    <div class="clinical-overview">
                        <h2><c:choose><c:when test="${summaryGenerated}">AI clinical draft</c:when><c:when test="${summaryArtifact.patient_context.synthetic}">Clinical summary</c:when><c:otherwise>Recorded chart facts</c:otherwise></c:choose></h2>
                        <c:if test="${not summaryArtifact.patient_context.synthetic}">
                            <details class="chart-scope" open><summary>Included records and limitations</summary>
                                <c:forEach items="${summaryArtifact.validation}" var="finding"><p><carlos:encode value="${finding.message}"/></p></c:forEach>
                            </details>
                        </c:if>
                        <c:choose>
                            <c:when test="${not summaryRenderable}">
                                <p class="notice error">Summary withheld: this artifact contains validation errors.</p>
                            </c:when>
                            <c:when test="${empty summaryArtifact.claims}">
                                <p class="empty-state">No summary claims available.</p>
                            </c:when>
                            <c:otherwise>
                                <c:forEach items="${summaryArtifact.sections}" var="section">
                                    <section class="summary-section">
                                        <h3><carlos:encode value="${section.title}"/></h3>
                                        <c:if test="${empty section.claim_ids}"><p>No records included in this section. This does not establish absence of a condition or treatment.</p></c:if>
                                        <c:forEach items="${section.claim_ids}" var="claimId">
                                            <c:set var="claim" value="${summaryClaims[claimId]}"/>
                                            <div class="claim-row">
                                                <a href="#source-${carlos:forHtmlAttribute(claim.source_ids[0])}" class="claim" data-claim-id="${carlos:forHtmlAttribute(claimId)}"><span class="claim-text"><carlos:encode value="${claim.text}"/></span> <span class="source-count">${fn:length(claim.source_ids)} <c:choose><c:when test="${fn:length(claim.source_ids) eq 1}">source</c:when><c:otherwise>sources</c:otherwise></c:choose></span></a>
                                                <span class="citations">
                                                    <c:forEach items="${claim.source_ids}" var="sourceId">
                                                        <a href="#source-${carlos:forHtmlAttribute(sourceId)}" class="citation"><carlos:encode value="${sourceId}"/></a>
                                                    </c:forEach>
                                                </span>
                                            </div>
                                        </c:forEach>
                                    </section>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                        <p class="reading-footnote">Reference and coverage checks do not establish clinical correctness. This draft remains unverified.</p>
                    </div>
                </div>
            </section>
            <section id="ledger" class="view-panel">
                <h2>Fact ledger</h2>
                <c:choose><c:when test="${empty summaryArtifact.fact_ledger}"><p>No ledger entries available.</p></c:when><c:otherwise>
                    <div class="table-scroll"><table class="ledger-table">
                        <thead><tr><th scope="col">Category</th><th scope="col">Recorded fact</th><th scope="col">Sources</th></tr></thead>
                        <tbody><c:forEach items="${summaryArtifact.fact_ledger}" var="fact">
                            <tr><th scope="row"><carlos:encode value="${fact.category}"/></th><td><carlos:encode value="${fact.text}"/></td>
                                <td><c:forEach items="${fact.source_ids}" var="sourceId"><a href="#source-${carlos:forHtmlAttribute(sourceId)}" class="citation"><carlos:encode value="${sourceId}"/></a></c:forEach></td>
                            </tr>
                        </c:forEach></tbody>
                    </table></div>
                </c:otherwise></c:choose>
            </section>
            <section id="coverage" class="view-panel">
                <h2>Source coverage</h2>
                <p class="record-limit">Coverage describes the included sources only, not the complete patient chart.</p>
                <c:forEach items="${summaryArtifact.coverage}" var="entry">
                    <article class="coverage-entry">
                        <div><a href="#source-${carlos:forHtmlAttribute(entry.source_id)}" class="citation"><carlos:encode value="${entry.source_id}"/></a>
                            <span class="coverage-status"><c:choose><c:when test="${entry.status eq 'reviewed_not_cited'}">Reviewed, not cited</c:when><c:otherwise><carlos:encode value="${entry.status}"/></c:otherwise></c:choose></span></div>
                        <p><carlos:encode value="${entry.reason}"/></p>
                    </article>
                </c:forEach>
            </section>
            <section id="validation" class="view-panel">
                <h2>Validation</h2>
                <p class="record-limit">Reference structure and source coverage passed runtime checks. Clinical accuracy and completeness remain unverified.</p>
                <h3>Artifact findings</h3>
                <c:if test="${empty summaryArtifact.validation}"><p>No artifact findings supplied.</p></c:if>
                <c:forEach items="${summaryArtifact.validation}" var="finding">
                    <article class="finding ${carlos:forHtmlAttribute(finding.severity)}">
                        <strong><carlos:encode value="${finding.severity}"/>: <carlos:encode value="${finding.code}"/></strong>
                        <p><carlos:encode value="${finding.message}"/></p>
                        <c:forEach items="${finding.source_ids}" var="sourceId"><a href="#source-${carlos:forHtmlAttribute(sourceId)}" class="citation"><carlos:encode value="${sourceId}"/></a></c:forEach>
                    </article>
                </c:forEach>
            </section>
            <footer class="artifact-footer"><carlos:encode value="${summaryArtifact.model}"/></footer>
        </div>
    </div>
    <div id="pane-splitter" class="pane-splitter js-control" role="separator" tabindex="0" aria-label="Resize source evidence" aria-orientation="vertical" aria-controls="evidence" aria-valuemin="24" aria-valuemax="55" aria-valuenow="28" hidden><i class="fa-solid fa-grip-lines-vertical" aria-hidden="true"></i></div>
    <aside id="evidence" class="evidence-panel" aria-labelledby="evidence-heading">
        <div class="panel-top">
            <div class="evidence-heading"><h2 id="evidence-heading">Source evidence</h2><p id="evidence-subtitle"><c:choose><c:when test="${summaryArtifact.patient_context.synthetic}">Complete synthetic source documents</c:when><c:otherwise>Recorded fields and full included note text</c:otherwise></c:choose></p></div>
            <div class="evidence-controls js-control" hidden>
                <button type="button" id="previous-claim" class="icon-button" title="Previous statement" aria-label="Previous statement" disabled><i class="fa-solid fa-arrow-up" aria-hidden="true"></i></button>
                <button type="button" id="next-claim" class="icon-button" title="Next statement" aria-label="Next statement" disabled><i class="fa-solid fa-arrow-down" aria-hidden="true"></i></button>
                <button type="button" id="hide-evidence" class="icon-button" title="Close source evidence" aria-label="Close source evidence"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button>
            </div>
        </div>
        <div id="empty-evidence" class="empty-evidence" hidden><i class="fa-regular fa-file-lines" aria-hidden="true"></i><h3>No statement selected</h3><p>Source evidence</p></div>
        <div id="evidence-content" class="evidence-scroll">
            <div id="selected-statement" class="selected-statement" hidden><span>Selected statement</span><p></p></div>
            <div id="source-switcher" class="source-switcher" hidden>
                <label for="source-picker" id="source-position">Source</label>
                <div class="source-switcher-row"><select id="source-picker" aria-label="Choose a source document"></select>
                    <button type="button" id="previous-source" class="icon-button" title="Previous source" aria-label="Previous source"><i class="fa-solid fa-chevron-left" aria-hidden="true"></i></button>
                    <button type="button" id="next-source" class="icon-button" title="Next source" aria-label="Next source"><i class="fa-solid fa-chevron-right" aria-hidden="true"></i></button>
                </div>
            </div>
            <c:forEach items="${summaryArtifact.sources}" var="source">
                <details id="source-${carlos:forHtmlAttribute(source.id)}" class="source" open>
                    <summary><strong><carlos:encode value="${source.title}"/></strong><span><carlos:encode value="${source.date}"/></span></summary>
                    <div class="source-id">Source ID: <carlos:encode value="${source.id}"/></div>
                    <p tabindex="-1"><carlos:encode value="${source.text}"/></p>
                </details>
            </c:forEach>
        </div>
    </aside>
</main>
</body>
</html>
