<%-- Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<fmt:setBundle basename="oscarResources"/>
<%-- Page chrome is localized. The summary, ledger, findings and source evidence are English text built
     by the host or the model; those elements carry lang="en" (WCAG 3.1.2) so a screen reader keeps
     the right voice under a French, Spanish, Polish or Portuguese UI. Never translate them here. --%>
<fmt:message key="clinical.aiSummaryPrototype.btnOpenDemographic" var="openDemographicLabel"/>
<fmt:message key="clinical.aiSummaryPrototype.tooltipGenerateUnavailable" var="generateUnavailableLabel"/>
<fmt:message key="clinical.aiSummaryPrototype.msgGenerating" var="generatingLabel"/>
<fmt:message key="clinical.aiSummaryPrototype.msgPreparing" var="preparingLabel"/>
<fmt:message key="clinical.aiSummaryPrototype.labelSummaryViews" var="summaryViewsLabel"/>
<fmt:message key="clinical.aiSummaryPrototype.btnShowEvidence" var="showEvidenceLabel"/>
<fmt:message key="clinical.aiSummaryPrototype.tooltipViewEvidence" var="viewEvidenceLabel"/>
<fmt:message key="clinical.aiSummaryPrototype.labelResizeEvidence" var="resizeEvidenceLabel"/>
<fmt:message key="clinical.aiSummaryPrototype.btnPreviousStatement" var="previousStatementLabel"/>
<fmt:message key="clinical.aiSummaryPrototype.btnNextStatement" var="nextStatementLabel"/>
<fmt:message key="clinical.aiSummaryPrototype.btnCloseEvidence" var="closeEvidenceLabel"/>
<fmt:message key="clinical.aiSummaryPrototype.labelChooseSource" var="chooseSourceLabel"/>
<fmt:message key="clinical.aiSummaryPrototype.btnPreviousSource" var="previousSourceLabel"/>
<fmt:message key="clinical.aiSummaryPrototype.btnNextSource" var="nextSourceLabel"/>
<%-- Templates for ai-summary-prototype.js. Passing "{0}" through fmt:param applies MessageFormat
     quoting here, so the script only substitutes the literal {0}/{1} placeholders. --%>
<fmt:message key="clinical.aiSummaryPrototype.msgSourcePosition" var="sourcePositionTemplate"><fmt:param value="{0}"/><fmt:param value="{1}"/></fmt:message>
<fmt:message key="clinical.aiSummaryPrototype.msgLinkedSources" var="linkedSourcesTemplate"><fmt:param value="{0}"/></fmt:message>
<fmt:message key="clinical.aiSummaryPrototype.msgEvidenceWidth" var="evidenceWidthTemplate"><fmt:param value="{0}"/></fmt:message>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><fmt:message key="clinical.aiSummaryPrototype.title"/> | CARLOS EMR</title>
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/css/fontawesome-all.min.css">
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/css/ai-summary-prototype.css">
    <script defer src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/ai-summary-prototype.js"></script>
</head>
<body>
<header class="app-header">
    <strong>CARLOS EMR</strong><span class="current-workspace"><fmt:message key="clinical.aiSummaryPrototype.labelPatientOverview"/></span>
    <form class="demographic-picker" method="get" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/clinical/AiSummaryPrototype">
        <label for="demographic-no"><fmt:message key="clinical.aiSummaryPrototype.labelDemographicNo"/></label>
        <input id="demographic-no" name="demographicNo" type="number" min="1" max="2147483647" step="1" required value="${carlos:forHtmlAttribute(summaryDemographicNo)}">
        <button class="icon-button" type="submit" title="${carlos:forHtmlAttribute(openDemographicLabel)}" aria-label="${carlos:forHtmlAttribute(openDemographicLabel)}"><i class="fa-solid fa-arrow-right" aria-hidden="true"></i></button>
    </form>
</header>
<div class="synthetic-banner">
    <c:choose><c:when test="${summaryGenerated}">
        <strong><i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i> <fmt:message key="clinical.aiSummaryPrototype.msgBannerGeneratedTitle"/></strong>
        <span><fmt:message key="clinical.aiSummaryPrototype.msgBannerGeneratedDetail"/></span>
    </c:when><c:when test="${summaryArtifact.patient_context.synthetic}">
        <strong><i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i> <fmt:message key="clinical.aiSummaryPrototype.msgBannerSyntheticTitle"/></strong>
        <span><fmt:message key="clinical.aiSummaryPrototype.msgBannerSyntheticDetail"/></span>
    </c:when><c:otherwise>
        <strong><i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i> <fmt:message key="clinical.aiSummaryPrototype.msgBannerChartTitle"/></strong>
        <span><fmt:message key="clinical.aiSummaryPrototype.msgBannerChartDetail"/></span>
    </c:otherwise></c:choose>
</div>
<header class="patient-header">
    <div class="patient-main">
        <span class="patient-avatar" aria-hidden="true"><i class="fa-solid fa-user"></i></span>
        <div><h1><carlos:encode value="${summaryArtifact.patient_context.label}"/></h1>
            <div class="patient-meta"><span><fmt:message key="clinical.aiSummaryPrototype.labelPatientOverview"/></span><span><carlos:encode value="${summaryArtifact.artifact_id}"/></span></div>
        </div>
    </div>
    <div class="header-status">
        <div class="status-line"><span class="status-label"><fmt:message key="clinical.aiSummaryPrototype.labelSourcesInBundle"><fmt:param value="${fn:length(summaryArtifact.sources)}"/></fmt:message></span><span class="status-label research-label"><fmt:message key="clinical.aiSummaryPrototype.labelResearchPrototype"/></span></div>
        <span class="generated-at"><carlos:encode value="${summaryArtifact.generated_at}"/></span>
    </div>
</header>
<c:if test="${summaryGenerationEnabled}">
    <div class="generation-toolbar">
        <form id="generate-summary" method="post" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/clinical/GenerateAiSummary"
              data-msg-generating="${carlos:forHtmlAttribute(generatingLabel)}" data-msg-preparing="${carlos:forHtmlAttribute(preparingLabel)}">
            <input type="hidden" name="demographicNo" value="${carlos:forHtmlAttribute(summaryDemographicNo)}">
            <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
            <c:choose><c:when test="${summaryGenerationAllowed}">
                <button type="submit" class="generate-button"><i class="fa-solid fa-wand-magic-sparkles" aria-hidden="true"></i> <span><c:choose><c:when test="${summaryGenerated}"><fmt:message key="clinical.aiSummaryPrototype.btnRegenerateDraft"/></c:when><c:otherwise><fmt:message key="clinical.aiSummaryPrototype.btnGenerateDraft"/></c:otherwise></c:choose></span></button>
            </c:when><c:otherwise>
                <button type="button" class="generate-button" disabled title="${carlos:forHtmlAttribute(generateUnavailableLabel)}"><i class="fa-solid fa-wand-magic-sparkles" aria-hidden="true"></i> <fmt:message key="clinical.aiSummaryPrototype.btnGenerateDraft"/></button>
            </c:otherwise></c:choose>
        </form>
        <p id="generation-status" role="status" aria-live="polite"><c:choose><c:when test="${summaryGenerationAllowed}"><fmt:message key="clinical.aiSummaryPrototype.msgFixtureVerified"/></c:when><c:otherwise><fmt:message key="clinical.aiSummaryPrototype.msgGenerationUnavailable"/></c:otherwise></c:choose></p>
        <c:if test="${summaryGenerated}"><a class="chart-extract-link" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/clinical/AiSummaryPrototype?demographicNo=${carlos:forHtmlAttribute(summaryDemographicNo)}"><fmt:message key="clinical.aiSummaryPrototype.titleRecordedChartFacts"/></a></c:if>
    </div>
    <%-- Generation errors are English sentences composed by the host service. --%>
    <c:if test="${not empty summaryGenerationError}"><p class="generation-error notice error" role="alert" lang="en"><carlos:encode value="${summaryGenerationError}"/></p></c:if>
</c:if>
<main class="workspace" id="workspace" data-msg-source-position="${carlos:forHtmlAttribute(sourcePositionTemplate)}"
      data-msg-linked-sources="${carlos:forHtmlAttribute(linkedSourcesTemplate)}" data-msg-evidence-width="${carlos:forHtmlAttribute(evidenceWidthTemplate)}">
    <div class="draft">
        <div class="panel-top">
            <nav class="view-tabs" aria-label="${carlos:forHtmlAttribute(summaryViewsLabel)}">
                <a href="#summary"><fmt:message key="clinical.aiSummaryPrototype.tabOverview"/></a><a href="#ledger"><fmt:message key="clinical.aiSummaryPrototype.tabFactLedger"/></a>
                <a href="#coverage"><fmt:message key="clinical.aiSummaryPrototype.tabCoverage"/></a><a href="#validation"><fmt:message key="clinical.aiSummaryPrototype.tabValidation"/></a>
            </nav>
            <button type="button" id="show-evidence" class="icon-button js-control" title="${carlos:forHtmlAttribute(showEvidenceLabel)}" aria-label="${carlos:forHtmlAttribute(showEvidenceLabel)}" aria-controls="evidence" aria-expanded="true" hidden><i class="fa-solid fa-columns" aria-hidden="true"></i></button>
        </div>
        <div class="reading-scroll">
            <section id="summary" class="view-panel">
                <div class="overview-layout">
                    <aside class="context-rail" aria-labelledby="context-heading">
                        <h2 id="context-heading"><fmt:message key="clinical.aiSummaryPrototype.sectionRecordContext"/></h2>
                        <dl class="record-counts">
                            <div><dt><fmt:message key="clinical.aiSummaryPrototype.labelSources"/></dt><dd>${fn:length(summaryArtifact.sources)}</dd></div>
                            <div><dt><fmt:message key="clinical.aiSummaryPrototype.labelSummaryClaims"/></dt><dd>${fn:length(summaryArtifact.claims)}</dd></div>
                            <div><dt><fmt:message key="clinical.aiSummaryPrototype.labelLedgerEntries"/></dt><dd>${fn:length(summaryArtifact.fact_ledger)}</dd></div>
                        </dl>
                        <h3><fmt:message key="clinical.aiSummaryPrototype.sectionSourceManifest"/></h3>
                        <ul class="source-manifest" lang="en">
                            <c:forEach items="${summaryArtifact.sources}" var="source">
                                <li><a href="#source-${carlos:forHtmlAttribute(source.id)}" class="citation"><carlos:encode value="${source.title}"/></a><span><carlos:encode value="${source.date}"/></span></li>
                            </c:forEach>
                        </ul>
                        <p class="record-limit"><strong><fmt:message key="clinical.aiSummaryPrototype.labelRecordLimitation"/></strong><fmt:message key="clinical.aiSummaryPrototype.msgRecordLimitation"/></p>
                    </aside>
                    <div class="clinical-overview">
                        <h2><c:choose><c:when test="${summaryGenerated}"><fmt:message key="clinical.aiSummaryPrototype.titleAiClinicalDraft"/></c:when><c:when test="${summaryArtifact.patient_context.synthetic}"><fmt:message key="clinical.aiSummaryPrototype.titleClinicalSummary"/></c:when><c:otherwise><fmt:message key="clinical.aiSummaryPrototype.titleRecordedChartFacts"/></c:otherwise></c:choose></h2>
                        <c:if test="${not summaryArtifact.patient_context.synthetic}">
                            <details class="chart-scope" open><summary><fmt:message key="clinical.aiSummaryPrototype.labelIncludedRecords"/></summary>
                                <c:forEach items="${summaryArtifact.validation}" var="finding"><p lang="en"><carlos:encode value="${finding.message}"/></p></c:forEach>
                            </details>
                        </c:if>
                        <c:choose>
                            <c:when test="${not summaryRenderable}">
                                <p class="notice error"><fmt:message key="clinical.aiSummaryPrototype.msgSummaryWithheld"/></p>
                            </c:when>
                            <c:when test="${empty summaryArtifact.claims}">
                                <p class="empty-state"><fmt:message key="clinical.aiSummaryPrototype.msgNoClaims"/></p>
                            </c:when>
                            <c:otherwise>
                                <p class="claim-instruction"><fmt:message key="clinical.aiSummaryPrototype.msgClaimInstruction"/></p>
                                <c:forEach items="${summaryArtifact.sections}" var="section">
                                    <section class="summary-section">
                                        <h3 lang="en"><carlos:encode value="${section.title}"/></h3>
                                        <c:if test="${empty section.claim_ids}"><p><fmt:message key="clinical.aiSummaryPrototype.msgEmptySection"/></p></c:if>
                                        <c:forEach items="${section.claim_ids}" var="claimId">
                                            <c:set var="claim" value="${summaryClaims[claimId]}"/>
                                            <div class="claim-row">
                                                <a href="#source-${carlos:forHtmlAttribute(claim.source_ids[0])}" class="claim" data-claim-id="${carlos:forHtmlAttribute(claimId)}" title="${carlos:forHtmlAttribute(viewEvidenceLabel)}"><span class="claim-text" lang="en"><carlos:encode value="${claim.text}"/></span></a>
                                                <span class="claim-source-links" hidden>
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
                        <p class="reading-footnote"><fmt:message key="clinical.aiSummaryPrototype.msgReadingFootnote"/></p>
                    </div>
                </div>
            </section>
            <section id="ledger" class="view-panel">
                <h2><fmt:message key="clinical.aiSummaryPrototype.tabFactLedger"/></h2>
                <c:choose><c:when test="${empty summaryArtifact.fact_ledger}"><p><fmt:message key="clinical.aiSummaryPrototype.msgNoLedgerEntries"/></p></c:when><c:otherwise>
                    <div class="table-scroll"><table class="ledger-table">
                        <thead><tr><th scope="col"><fmt:message key="clinical.aiSummaryPrototype.thCategory"/></th><th scope="col"><fmt:message key="clinical.aiSummaryPrototype.thRecordedFact"/></th><th scope="col"><fmt:message key="clinical.aiSummaryPrototype.labelSources"/></th></tr></thead>
                        <tbody lang="en"><c:forEach items="${summaryArtifact.fact_ledger}" var="fact">
                            <tr><th scope="row"><carlos:encode value="${fact.category}"/></th><td><carlos:encode value="${fact.text}"/></td>
                                <td><c:forEach items="${fact.source_ids}" var="sourceId"><a href="#source-${carlos:forHtmlAttribute(sourceId)}" class="citation"><carlos:encode value="${sourceId}"/></a></c:forEach></td>
                            </tr>
                        </c:forEach></tbody>
                    </table></div>
                </c:otherwise></c:choose>
            </section>
            <section id="coverage" class="view-panel">
                <h2><fmt:message key="clinical.aiSummaryPrototype.sectionSourceCoverage"/></h2>
                <p class="record-limit"><fmt:message key="clinical.aiSummaryPrototype.msgCoverageScope"/></p>
                <c:forEach items="${summaryArtifact.coverage}" var="entry">
                    <article class="coverage-entry">
                        <div><a href="#source-${carlos:forHtmlAttribute(entry.source_id)}" class="citation"><carlos:encode value="${entry.source_id}"/></a>
                            <span class="coverage-status"><c:choose><c:when test="${entry.status eq 'cited'}"><fmt:message key="clinical.aiSummaryPrototype.labelStatusCited"/></c:when><c:when test="${entry.status eq 'reviewed_not_cited'}"><fmt:message key="clinical.aiSummaryPrototype.labelStatusReviewedNotCited"/></c:when><c:when test="${entry.status eq 'excluded'}"><fmt:message key="clinical.aiSummaryPrototype.labelStatusExcluded"/></c:when><c:otherwise><span lang="en"><carlos:encode value="${entry.status}"/></span></c:otherwise></c:choose></span></div>
                        <p lang="en"><carlos:encode value="${entry.reason}"/></p>
                    </article>
                </c:forEach>
            </section>
            <section id="validation" class="view-panel">
                <h2><fmt:message key="clinical.aiSummaryPrototype.tabValidation"/></h2>
                <p class="record-limit"><fmt:message key="clinical.aiSummaryPrototype.msgValidationScope"/></p>
                <h3><fmt:message key="clinical.aiSummaryPrototype.sectionArtifactFindings"/></h3>
                <c:if test="${empty summaryArtifact.validation}"><p><fmt:message key="clinical.aiSummaryPrototype.msgNoFindings"/></p></c:if>
                <c:forEach items="${summaryArtifact.validation}" var="finding">
                    <article class="finding ${carlos:forHtmlAttribute(finding.severity)}" lang="en">
                        <strong><carlos:encode value="${finding.severity}"/>: <carlos:encode value="${finding.code}"/></strong>
                        <p><carlos:encode value="${finding.message}"/></p>
                        <c:forEach items="${finding.source_ids}" var="sourceId"><a href="#source-${carlos:forHtmlAttribute(sourceId)}" class="citation"><carlos:encode value="${sourceId}"/></a></c:forEach>
                    </article>
                </c:forEach>
            </section>
            <footer class="artifact-footer" lang="en"><carlos:encode value="${summaryArtifact.model}"/></footer>
        </div>
    </div>
    <div id="pane-splitter" class="pane-splitter js-control" role="separator" tabindex="0" aria-label="${carlos:forHtmlAttribute(resizeEvidenceLabel)}" aria-orientation="vertical" aria-controls="evidence" aria-valuemin="24" aria-valuemax="55" aria-valuenow="28" hidden><i class="fa-solid fa-grip-lines-vertical" aria-hidden="true"></i></div>
    <aside id="evidence" class="evidence-panel" aria-labelledby="evidence-heading">
        <div class="panel-top">
            <div class="evidence-heading"><h2 id="evidence-heading"><fmt:message key="clinical.aiSummaryPrototype.sectionSourceEvidence"/></h2><p id="evidence-subtitle"><c:choose><c:when test="${summaryArtifact.patient_context.synthetic}"><fmt:message key="clinical.aiSummaryPrototype.msgSyntheticSources"/></c:when><c:otherwise><fmt:message key="clinical.aiSummaryPrototype.msgChartSources"/></c:otherwise></c:choose></p></div>
            <div class="evidence-controls js-control" hidden>
                <button type="button" id="previous-claim" class="icon-button" title="${carlos:forHtmlAttribute(previousStatementLabel)}" aria-label="${carlos:forHtmlAttribute(previousStatementLabel)}" disabled><i class="fa-solid fa-arrow-up" aria-hidden="true"></i></button>
                <button type="button" id="next-claim" class="icon-button" title="${carlos:forHtmlAttribute(nextStatementLabel)}" aria-label="${carlos:forHtmlAttribute(nextStatementLabel)}" disabled><i class="fa-solid fa-arrow-down" aria-hidden="true"></i></button>
                <button type="button" id="hide-evidence" class="icon-button" title="${carlos:forHtmlAttribute(closeEvidenceLabel)}" aria-label="${carlos:forHtmlAttribute(closeEvidenceLabel)}"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button>
            </div>
        </div>
        <div id="empty-evidence" class="empty-evidence" hidden><i class="fa-regular fa-file-lines" aria-hidden="true"></i><h3><fmt:message key="clinical.aiSummaryPrototype.msgNoStatementSelected"/></h3><p><fmt:message key="clinical.aiSummaryPrototype.sectionSourceEvidence"/></p></div>
        <div id="evidence-content" class="evidence-scroll">
            <div id="selected-statement" class="selected-statement" hidden><span><fmt:message key="clinical.aiSummaryPrototype.labelSelectedStatement"/></span><p lang="en"></p></div>
            <div id="source-switcher" class="source-switcher" hidden>
                <label for="source-picker" id="source-position"><fmt:message key="clinical.aiSummaryPrototype.labelSource"/></label>
                <div class="source-switcher-row"><select id="source-picker" aria-label="${carlos:forHtmlAttribute(chooseSourceLabel)}"></select>
                    <button type="button" id="previous-source" class="icon-button" title="${carlos:forHtmlAttribute(previousSourceLabel)}" aria-label="${carlos:forHtmlAttribute(previousSourceLabel)}"><i class="fa-solid fa-chevron-left" aria-hidden="true"></i></button>
                    <button type="button" id="next-source" class="icon-button" title="${carlos:forHtmlAttribute(nextSourceLabel)}" aria-label="${carlos:forHtmlAttribute(nextSourceLabel)}"><i class="fa-solid fa-chevron-right" aria-hidden="true"></i></button>
                </div>
            </div>
            <c:forEach items="${summaryArtifact.sources}" var="source">
                <details id="source-${carlos:forHtmlAttribute(source.id)}" class="source" open>
                    <summary lang="en"><strong><carlos:encode value="${source.title}"/></strong><span><carlos:encode value="${source.date}"/></span></summary>
                    <div class="source-id"><fmt:message key="clinical.aiSummaryPrototype.labelSourceId"/> <carlos:encode value="${source.id}"/></div>
                    <p tabindex="-1" lang="en"><carlos:encode value="${source.text}"/></p>
                </details>
            </c:forEach>
        </div>
    </aside>
</main>
</body>
</html>
