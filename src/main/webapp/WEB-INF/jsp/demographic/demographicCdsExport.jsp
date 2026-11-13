<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%--
    Purpose: authenticated CDS export submission and saved-job monitoring.
    Features: asynchronous processing, revision-checked cancellation/resume, encrypted
    package retrieval and metadata diagnostics. Includes the shared CSRF bootstrap.
    Parameters: optional runId query parameter opens a saved job; all mutations use
    their POST actions. The model request attribute is CdsExportViewModel, assembled
    by ViewCdsExport2Action after authorization; this JSP does not read clinical data.
    @since 2026.08
--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<fmt:setBundle basename="cdsExportMessages" var="cdsx"/>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.response.locale.language)}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><fmt:message key="title" bundle="${cdsx}"/></title>
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/demographic/cds-export.js" defer></script>
</head>
<body>
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>
<main id="cdsExport" class="container p-3" data-context="${carlos:forHtmlAttribute(pageContext.request.contextPath)}"
      data-owner="${carlos:forHtmlAttribute(model.operatorKey)}" data-can-resume="${carlos:forHtmlAttribute(model.enabled)}">
    <h1 class="h3"><fmt:message key="title" bundle="${cdsx}"/></h1>
    <p><fmt:message key="intro" bundle="${cdsx}"/></p>
    <p><a href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/form/importUpload"><fmt:message key="importLink" bundle="${cdsx}"/></a></p>
    <c:if test="${not model.enabled}">
        <div class="alert alert-warning" role="status"><fmt:message key="disabled" bundle="${cdsx}"/></div>
    </c:if>
    <c:if test="${not model.dispatcherRunning}">
        <div class="alert alert-info" role="status"><fmt:message key="paused" bundle="${cdsx}"/></div>
    </c:if>
    <noscript><div class="alert alert-danger"><fmt:message key="javascript" bundle="${cdsx}"/></div></noscript>
    <%-- Script text in the page's language; cds-export.js keeps its English defaults for any entry missing here. --%>
    <div id="cdsExportText" hidden>
        <c:forTokens items="stateAccepted,stateDiscovering,stateCapturing,stateReconciling,stateSerializing,stateValidating,statePackaging,stateEncrypting,stateReady,stateReviewRequired,stateFailed,stateCancelled,stateExpired,heldBack,readyUnavailable,countUnknown,countProgress,countWorking,heldListed,heldNone,workerBusy,resumeInvalid,cancelInvalid,runtimeChangedControl,statusChanged,admissionDisabled,profileNotQualified,controlUnconfirmed,missingRight,submissionUnconfirmed,keepKey,waiting,confirmed,runtimeChanged,refreshBeforeDownload,downloadFailed,stale,sessionEnded,invalidJobId,checking,historyFailed,confirming,accepted,savedUnconfirmed,cancelRecorded,resumeRequested,confirmNew,newKey,storageUnavailable,noRandomUuid,missingOperatorRight,submissionConflict,requestInvalid,submissionRefused,clientRefresh,jobNotFound,jobAccessDenied,submissionNotFound,downloadStarted,confirmCancel" delims="," var="textKey"><fmt:message key="js.${textKey}" bundle="${cdsx}" var="textValue"/><span data-key="${carlos:forHtmlAttribute(textKey)}" data-value="${carlos:forHtmlAttribute(textValue)}"></span></c:forTokens>
    </div>
    <div id="exportNotice" class="alert alert-info" role="status" aria-live="polite" hidden></div>
    <form id="exportForm" method="POST" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/form/cdsExportSubmit">
        <fieldset id="exportOptions" <c:if test="${not model.enabled}">disabled</c:if>>
            <legend class="h5"><fmt:message key="scope" bundle="${cdsx}"/></legend>
            <label for="submissionKey" class="form-label"><fmt:message key="submissionKey" bundle="${cdsx}"/></label>
            <input id="submissionKey" name="submissionKey" class="form-control mb-2" maxlength="128" required readonly>
            <p class="form-text"><fmt:message key="submissionKeyHelp" bundle="${cdsx}"/></p>
            <div class="row g-3 mb-3">
                <div class="col-sm-4"><label for="providerNo" class="form-label"><fmt:message key="provider" bundle="${cdsx}"/></label>
                    <input id="providerNo" name="providerNo" class="form-control" value="${carlos:forHtmlAttribute(model.providerNo)}" maxlength="16" required></div>
                <div class="col-sm-4"><label for="facilityId" class="form-label"><fmt:message key="facility" bundle="${cdsx}"/></label>
                    <input id="facilityId" name="facilityId" type="number" min="1" class="form-control" value="${carlos:forHtmlAttribute(model.facilityId)}"></div>
                <div class="col-sm-4"><label for="profile" class="form-label"><fmt:message key="profile" bundle="${cdsx}"/></label>
                    <select id="profile" name="profile" class="form-select" required>
                        <c:forEach items="${model.profiles}" var="profile">
                            <option value="${carlos:forHtmlAttribute(profile.value)}"><carlos:encode value="${profile.label}"/></option>
                        </c:forEach>
                    </select></div>
            </div>
            <label for="selection" class="form-label"><fmt:message key="selection" bundle="${cdsx}"/></label>
            <select id="selection" name="selection" class="form-select mb-2">
                <option value="PHYSICIAN_ROSTER"><fmt:message key="selectionRoster" bundle="${cdsx}"/></option>
                <option value="SELECTED_PATIENTS"><fmt:message key="selectionPatients" bundle="${cdsx}"/></option>
            </select>
            <label for="physicianNos" class="form-label"><fmt:message key="physicians" bundle="${cdsx}"/></label>
            <input id="physicianNos" name="physicianNos" class="form-control mb-2" maxlength="1699">
            <label for="patientNos" class="form-label"><fmt:message key="patients" bundle="${cdsx}"/></label>
            <textarea id="patientNos" name="patientNos" class="form-control mb-3" rows="2" maxlength="119999" disabled></textarea>
            <fieldset class="mb-3"><legend class="h6"><fmt:message key="categories" bundle="${cdsx}"/></legend>
                <c:forEach items="${model.categories}" var="category">
                    <label for="exportCategory-${carlos:forHtmlAttribute(category.value)}" class="form-check"><input id="exportCategory-${carlos:forHtmlAttribute(category.value)}" type="checkbox" class="form-check-input export-category" checked
                        value="${carlos:forHtmlAttribute(category.value)}" <c:if test="${category.value eq 'Demographics'}">disabled</c:if>>
                        <span class="visually-hidden"><fmt:message key="categoryPrefix" bundle="${cdsx}"/></span><fmt:message key="category.${category.value}" bundle="${cdsx}" var="categoryLabel"/><span class="form-check-label"><carlos:encode value="${categoryLabel}"/></span></label>
                </c:forEach>
            </fieldset>
            <div class="row g-3 mb-3">
                <div class="col-sm-6"><label for="recipientId" class="form-label"><fmt:message key="recipient" bundle="${cdsx}"/></label>
                    <input id="recipientId" name="recipientId" class="form-control" maxlength="64" required></div>
                <div class="col-sm-6"><label for="auditPlanId" class="form-label"><fmt:message key="auditPlan" bundle="${cdsx}"/></label>
                    <input id="auditPlanId" name="auditPlanId" class="form-control" maxlength="64" required></div>
            </div>
            <button id="submitExport" class="btn btn-primary" type="submit"><fmt:message key="submit" bundle="${cdsx}"/></button>
        </fieldset>
    </form>
    <section class="my-4" aria-labelledby="savedHeading">
        <h2 id="savedHeading" class="h5"><fmt:message key="savedJobs" bundle="${cdsx}"/></h2>
        <div class="input-group mb-2"><fmt:message key="savedSubmission" bundle="${cdsx}" var="savedSubmissionLabel"/><input id="savedSubmission" class="form-control" aria-label="${carlos:forHtmlAttribute(savedSubmissionLabel)}" maxlength="128">
            <button id="findSubmission" type="button" class="btn btn-outline-primary"><fmt:message key="checkSubmission" bundle="${cdsx}"/></button></div>
        <div class="input-group mb-2"><fmt:message key="jobId" bundle="${cdsx}" var="jobIdLabel"/><input id="runId" class="form-control" aria-label="${carlos:forHtmlAttribute(jobIdLabel)}" maxlength="36">
            <button id="openExport" type="button" class="btn btn-outline-primary"><fmt:message key="openJob" bundle="${cdsx}"/></button></div>
        <div id="exportStatus" class="border rounded p-3" hidden>
            <h3 id="exportState" class="h5" aria-live="polite"><fmt:message key="status" bundle="${cdsx}"/></h3>
            <p id="exportConnection" role="status" aria-live="polite"></p>
            <p id="exportCounts"></p>
            <progress id="exportProgress" max="1" class="w-100" aria-labelledby="exportState"></progress>
            <p id="exportCode"></p>
            <form id="exportReportForm" method="POST" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/form/cdsExportReport" target="cdsExportDiagnostic" data-window="cdsExportDiagnostic">
                <input type="hidden" name="apiVersion" value="1">
                <input type="hidden" id="reportRunId" name="runId">
                <input type="hidden" id="reportCsrf" name="CSRF-TOKEN">
                <button id="reportExport" type="submit" class="btn btn-outline-secondary" disabled><fmt:message key="report" bundle="${cdsx}"/></button>
            </form>
            <form id="exportDownloadForm" method="POST" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/form/cdsExportDownload" target="cdsExportDelivery" data-window="cdsExportDelivery">
                <input type="hidden" name="apiVersion" value="1">
                <input type="hidden" name="artifact" value="package">
                <input type="hidden" id="downloadRunId" name="runId">
                <input type="hidden" id="downloadCsrf" name="CSRF-TOKEN">
                <button id="downloadExport" type="submit" class="btn btn-success" disabled><fmt:message key="download" bundle="${cdsx}"/></button>
            </form>
            <button id="cancelExport" type="button" class="btn btn-outline-danger" disabled><fmt:message key="cancel" bundle="${cdsx}"/></button>
            <button id="resumeExport" type="button" class="btn btn-outline-primary" disabled><fmt:message key="resume" bundle="${cdsx}"/></button>
        </div>
        <div class="d-flex gap-2 my-2"><button id="refreshExports" type="button" class="btn btn-outline-secondary"><fmt:message key="refresh" bundle="${cdsx}"/></button>
            <button id="nextExports" type="button" class="btn btn-outline-secondary" disabled><fmt:message key="next" bundle="${cdsx}"/></button>
            <button id="newSubmission" type="button" class="btn btn-outline-secondary"><fmt:message key="newSubmission" bundle="${cdsx}"/></button></div>
        <div id="exportHistory" class="d-flex flex-wrap gap-2"></div>
    </section>
</main>
</body>
</html>
