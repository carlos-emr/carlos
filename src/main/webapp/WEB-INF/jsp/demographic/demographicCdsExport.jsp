<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>CDS Export</title>
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/demographic/cds-export.js" defer></script>
</head>
<body>
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>
<main id="cdsExport" class="container p-3" data-context="${carlos:forHtmlAttribute(pageContext.request.contextPath)}"
      data-owner="${carlos:forHtmlAttribute(model.operatorKey)}" data-can-resume="${carlos:forHtmlAttribute(model.enabled)}">
    <h1 class="h3">CDS Export</h1>
    <p>Create an encrypted patient transfer package. Processing continues online after acceptance. Keep the job ID to check its outcome.</p>
    <p><a href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/form/importUpload">CDS Import</a></p>
    <c:if test="${not model.enabled}">
        <div class="alert alert-warning" role="status">New exports are unavailable. Saved job status and cancellation remain available.</div>
    </c:if>
    <c:if test="${not model.dispatcherRunning}">
        <div class="alert alert-info" role="status">Online processing is paused on this server. An accepted job remains waiting until a compatible worker runs it.</div>
    </c:if>
    <noscript><div class="alert alert-danger">JavaScript is required to submit and monitor an export.</div></noscript>
    <div id="exportNotice" class="alert alert-info" role="status" aria-live="polite" hidden></div>
    <form id="exportForm" method="POST" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/form/cdsExportSubmit">
        <fieldset id="exportOptions" <c:if test="${not model.enabled}">disabled</c:if>>
            <legend class="h5">Transfer scope</legend>
            <label for="submissionKey" class="form-label">Submission key</label>
            <input id="submissionKey" name="submissionKey" class="form-control mb-2" maxlength="128" required readonly>
            <p class="form-text">Retries use this key to recover the same job. Check a saved submission before starting another export.</p>
            <div class="row g-3 mb-3">
                <div class="col-sm-4"><label for="providerNo" class="form-label">Authorizing provider</label>
                    <input id="providerNo" name="providerNo" class="form-control" value="${carlos:forHtmlAttribute(model.providerNo)}" maxlength="16" required></div>
                <div class="col-sm-4"><label for="facilityId" class="form-label">Facility ID</label>
                    <input id="facilityId" name="facilityId" type="number" min="1" class="form-control" value="${carlos:forHtmlAttribute(model.facilityId)}"></div>
                <div class="col-sm-4"><label for="profile" class="form-label">Transfer profile</label>
                    <select id="profile" name="profile" class="form-select" required>
                        <c:forEach items="${model.profiles}" var="profile">
                            <option value="${carlos:forHtmlAttribute(profile.value)}"><carlos:encode value="${profile.label}"/></option>
                        </c:forEach>
                    </select></div>
            </div>
            <label for="selection" class="form-label">Patient selection</label>
            <select id="selection" name="selection" class="form-select mb-2">
                <option value="PHYSICIAN_ROSTER">Primary provider (MRP) roster — all patient statuses</option>
                <option value="SELECTED_PATIENTS">Specified patients</option>
            </select>
            <label for="physicianNos" class="form-label">Physician provider numbers, separated by commas</label>
            <input id="physicianNos" name="physicianNos" class="form-control mb-2" maxlength="1699">
            <label for="patientNos" class="form-label">Patient numbers, separated by commas</label>
            <textarea id="patientNos" name="patientNos" class="form-control mb-3" rows="2" maxlength="119999" disabled></textarea>
            <fieldset class="mb-3"><legend class="h6">Clinical categories</legend>
                <c:forEach items="${model.categories}" var="category">
                    <label class="form-check"><input type="checkbox" class="form-check-input export-category" checked
                        value="${carlos:forHtmlAttribute(category.value)}" <c:if test="${category.value eq 'Demographics'}">disabled</c:if>>
                        <span class="form-check-label"><carlos:encode value="${category.label}"/></span></label>
                </c:forEach>
            </fieldset>
            <div class="row g-3 mb-3">
                <div class="col-sm-6"><label for="recipientId" class="form-label">Configured recipient ID</label>
                    <input id="recipientId" name="recipientId" class="form-control" maxlength="64" required></div>
                <div class="col-sm-6"><label for="auditPlanId" class="form-label">Configured audit transfer arrangement ID</label>
                    <input id="auditPlanId" name="auditPlanId" class="form-control" maxlength="64" required></div>
            </div>
            <button id="submitExport" class="btn btn-primary" type="submit">Submit export</button>
        </fieldset>
    </form>
    <section class="my-4" aria-labelledby="savedHeading">
        <h2 id="savedHeading" class="h5">Saved jobs</h2>
        <div class="input-group mb-2"><input id="savedSubmission" class="form-control" aria-label="Saved submission key" maxlength="128">
            <button id="findSubmission" type="button" class="btn btn-outline-primary">Check submission</button></div>
        <div class="input-group mb-2"><input id="runId" class="form-control" aria-label="Job ID" maxlength="36">
            <button id="openExport" type="button" class="btn btn-outline-primary">Open job</button></div>
        <div id="exportStatus" class="border rounded p-3" hidden>
            <h3 id="exportState" class="h5" aria-live="polite"></h3>
            <p id="exportConnection" role="status" aria-live="polite"></p>
            <p id="exportCounts"></p>
            <progress id="exportProgress" max="1" class="w-100"></progress>
            <p id="exportCode"></p>
            <form id="exportReportForm" method="POST" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/form/cdsExportReport" target="cdsExportDiagnostic">
                <input type="hidden" name="apiVersion" value="1">
                <input type="hidden" id="reportRunId" name="runId">
                <input type="hidden" id="reportCsrf" name="CSRF-TOKEN">
                <button id="reportExport" type="submit" class="btn btn-outline-secondary" disabled>Download diagnostic report</button>
            </form>
            <form id="exportDownloadForm" method="POST" action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/form/cdsExportDownload" target="cdsExportDelivery">
                <input type="hidden" name="apiVersion" value="1">
                <input type="hidden" name="artifact" value="package">
                <input type="hidden" id="downloadRunId" name="runId">
                <input type="hidden" id="downloadCsrf" name="CSRF-TOKEN">
                <button id="downloadExport" type="submit" class="btn btn-success" disabled>Download encrypted package</button>
            </form>
            <button id="cancelExport" type="button" class="btn btn-outline-danger" disabled>Cancel export</button>
            <button id="resumeExport" type="button" class="btn btn-outline-primary" disabled>Resume export</button>
        </div>
        <div class="d-flex gap-2 my-2"><button id="refreshExports" type="button" class="btn btn-outline-secondary">Refresh jobs</button>
            <button id="nextExports" type="button" class="btn btn-outline-secondary" disabled>Next page</button>
            <button id="newSubmission" type="button" class="btn btn-outline-secondary">Start another submission</button></div>
        <div id="exportHistory" class="d-flex flex-wrap gap-2" aria-live="polite"></div>
    </section>
</main>
</body>
</html>
