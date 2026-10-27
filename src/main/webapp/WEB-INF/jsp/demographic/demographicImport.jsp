<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="cdsImportMessages" var="cds"/>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.response.locale.language)}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><fmt:message key="title" bundle="${cds}"/></title>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <script src="${pageContext.request.contextPath}/js/demographic/cds-import.js" defer></script>
</head>
<body>
<main id="cdsImport" class="container-fluid p-3"
      data-context="${carlos:forHtmlAttribute(pageContext.request.contextPath)}"
      data-owner="${carlos:forHtmlAttribute(model.operatorKey)}"
      data-max-file-bytes="${carlos:forHtmlAttribute(model.maxFileBytes)}"
      data-chunk-bytes="${carlos:forHtmlAttribute(model.chunkBytes)}">
    <h1 class="h3"><fmt:message key="title" bundle="${cds}"/></h1>
    <p><fmt:message key="intro" bundle="${cds}"/></p>
    <c:if test="${not model.enabled}">
        <div class="alert alert-warning" role="status"><fmt:message key="disabled" bundle="${cds}"/></div>
    </c:if>
    <c:if test="${empty model.sources}">
        <div class="alert alert-warning" role="status"><fmt:message key="sourcesMissing" bundle="${cds}"/></div>
    </c:if>
    <noscript><div class="alert alert-danger"><fmt:message key="javascript" bundle="${cds}"/></div></noscript>
    <div id="cdsNotice" class="alert alert-info" role="status" aria-live="polite" hidden></div>
    <div id="cdsAdministrativeAlert" class="alert alert-warning" role="alert" hidden></div>
    <div id="cdsWorkerAlert" class="alert alert-danger" role="alert" hidden></div>
    <form id="cdsImportForm" name="ImportDemographicDataForm" method="POST"
          action="${pageContext.request.contextPath}/form/cdsImportSubmit">
        <fieldset id="cdsOptions" <c:if test="${not model.enabled or empty model.sources}">disabled</c:if>>
            <legend class="h5"><fmt:message key="sourceOptions" bundle="${cds}"/></legend>
            <div class="mb-3">
                <label for="importFile" class="form-label"><fmt:message key="files" bundle="${cds}"/></label>
                <input id="importFile" name="importFile" type="file" accept=".xml,.zip" multiple class="form-control">
                <div class="form-text"><fmt:message key="filesHelp" bundle="${cds}"/></div>
            </div>
            <div class="mb-3">
                <label for="cdsSource" class="form-label"><fmt:message key="profile" bundle="${cds}"/></label>
                <select id="cdsSource" class="form-select" required>
                    <option value=""><fmt:message key="selectSource" bundle="${cds}"/></option>
                    <c:forEach items="${model.sources}" var="source">
                        <option value="${carlos:forHtmlAttribute(source.namespace)}|${carlos:forHtmlAttribute(source.profile)}"
                                data-namespace="${carlos:forHtmlAttribute(source.namespace)}"
                                data-profile="${carlos:forHtmlAttribute(source.profile)}"><carlos:encode value="${source.label}"/></option>
                    </c:forEach>
                </select>
            </div>
            <div class="row g-3 mb-3">
                <div class="col-sm-4"><label for="providerNo" class="form-label"><fmt:message key="provider" bundle="${cds}"/></label>
                    <input id="providerNo" name="providerNo" class="form-control" value="${carlos:forHtmlAttribute(model.providerNo)}" pattern="[A-Za-z0-9_-]{1,16}" maxlength="16" required></div>
                <div class="col-sm-4"><label for="programId" class="form-label"><fmt:message key="program" bundle="${cds}"/></label>
                    <input id="programId" name="programId" type="number" min="1" class="form-control" value="${carlos:forHtmlAttribute(model.programId)}"></div>
                <div class="col-sm-4"><label for="facilityId" class="form-label"><fmt:message key="facility" bundle="${cds}"/></label>
                    <input id="facilityId" name="facilityId" type="number" min="1" class="form-control" value="${carlos:forHtmlAttribute(model.facilityId)}"></div>
            </div>
            <div class="form-check"><input id="matchProviderNames" name="matchProviderNames" type="checkbox" checked class="form-check-input">
                <label for="matchProviderNames" class="form-check-label"><fmt:message key="providerMatching" bundle="${cds}"/></label></div>
            <div class="form-check"><input id="mergeExisting" name="mergeExisting" type="checkbox" class="form-check-input">
                <label for="mergeExisting" class="form-check-label"><fmt:message key="merge" bundle="${cds}"/></label></div>
            <div class="form-check mb-3"><input id="dryRun" name="dryRun" type="checkbox" class="form-check-input">
                <label for="dryRun" class="form-check-label"><fmt:message key="dryRun" bundle="${cds}"/></label></div>
            <c:if test="${model.learningEnabled}">
                <div class="border rounded p-3 mb-3">
                    <div class="form-check"><input id="synthetic" name="synthetic" type="checkbox" class="form-check-input">
                        <label for="synthetic" class="form-check-label"><fmt:message key="synthetic" bundle="${cds}"/></label></div>
                    <div class="row g-3">
                        <div class="col-sm-6"><label for="courseId" class="form-label"><fmt:message key="course" bundle="${cds}"/></label>
                            <input id="courseId" name="courseId" type="number" min="1" class="form-control"></div>
                        <div class="col-sm-6"><label for="timeshiftInDays" class="form-label"><fmt:message key="timeshift" bundle="${cds}"/></label>
                            <input id="timeshiftInDays" name="timeshiftInDays" type="number" value="0" class="form-control"></div>
                    </div>
                </div>
            </c:if>
            <p><fmt:message key="window" bundle="${cds}"/></p>
            <button id="cdsStart" name="Submit" type="submit" class="btn btn-primary"><fmt:message key="start" bundle="${cds}"/></button>
        </fieldset>
    </form>
    <section class="my-4" aria-labelledby="uploadHeading">
        <h2 id="uploadHeading" class="h5"><fmt:message key="uploads" bundle="${cds}"/></h2>
        <p id="currentUpload" aria-live="polite"></p>
        <progress id="uploadProgress" max="1" value="0" class="w-100" aria-label="Upload progress"></progress>
        <div class="d-flex flex-wrap gap-2 my-2">
            <button id="refreshUploads" type="button" class="btn btn-outline-secondary"><fmt:message key="refreshUploads" bundle="${cds}"/></button>
            <button id="nextUploads" type="button" class="btn btn-outline-secondary"><fmt:message key="nextPage" bundle="${cds}"/></button>
        </div>
        <div class="table-responsive"><table class="table table-sm"><thead><tr>
            <th><fmt:message key="file" bundle="${cds}"/></th><th><fmt:message key="state" bundle="${cds}"/></th>
            <th><fmt:message key="bytes" bundle="${cds}"/></th><th><fmt:message key="actions" bundle="${cds}"/></th>
        </tr></thead><tbody id="uploadRows"></tbody></table></div>
        <div class="mb-2"><label for="uploadResumeId" class="form-label"><fmt:message key="resumeUpload" bundle="${cds}"/></label>
            <input id="uploadResumeId" class="form-control" autocomplete="off" placeholder="00000000-0000-0000-0000-000000000000"></div>
        <button id="submitCompletedUpload" type="button" class="btn btn-outline-primary"><fmt:message key="submitCompleted" bundle="${cds}"/></button>
    </section>
    <section class="my-4" aria-labelledby="runHeading">
        <h2 id="runHeading" class="h5"><fmt:message key="runs" bundle="${cds}"/></h2>
        <div id="recentRuns" class="d-flex flex-wrap gap-2 mb-2"></div>
        <div class="input-group"><input id="runId" class="form-control" aria-label="Run ID" autocomplete="off">
            <button id="openRun" type="button" class="btn btn-outline-primary"><fmt:message key="openRun" bundle="${cds}"/></button></div>
        <p id="runState" class="mt-2" aria-live="polite"></p>
        <pre id="runSummary" class="border rounded p-2 text-wrap"></pre>
        <div class="d-flex flex-wrap gap-2 my-2">
            <button id="cancelRun" type="button" class="btn btn-outline-danger" disabled><fmt:message key="cancel" bundle="${cds}"/></button>
            <button id="resumeRun" type="button" class="btn btn-outline-primary" disabled><fmt:message key="resumeRun" bundle="${cds}"/></button>
            <a id="eventReport" class="btn btn-outline-secondary" hidden><fmt:message key="eventLog" bundle="${cds}"/></a>
            <a id="errorReport" class="btn btn-outline-secondary" hidden><fmt:message key="errorLog" bundle="${cds}"/></a>
            <a id="receiptReport" class="btn btn-outline-secondary" hidden><fmt:message key="receipt" bundle="${cds}"/></a>
        </div>
        <h3 class="h6"><fmt:message key="patients" bundle="${cds}"/></h3>
        <div class="table-responsive"><table class="table table-sm"><thead><tr>
            <th><fmt:message key="sourcePatient" bundle="${cds}"/></th><th><fmt:message key="state" bundle="${cds}"/></th>
            <th><fmt:message key="held" bundle="${cds}"/></th><th><fmt:message key="units" bundle="${cds}"/></th>
        </tr></thead><tbody id="patientRows"></tbody></table></div>
        <button id="nextPatients" type="button" class="btn btn-outline-secondary"><fmt:message key="nextPage" bundle="${cds}"/></button>
        <h3 class="h6 mt-3"><fmt:message key="diagnostics" bundle="${cds}"/></h3>
        <div class="table-responsive"><table class="table table-sm"><thead><tr>
            <th>ID</th><th><fmt:message key="code" bundle="${cds}"/></th><th><fmt:message key="detail" bundle="${cds}"/></th>
            <th><fmt:message key="review" bundle="${cds}"/></th>
        </tr></thead><tbody id="diagnosticRows"></tbody></table></div>
        <button id="nextDiagnostics" type="button" class="btn btn-outline-secondary"><fmt:message key="nextPage" bundle="${cds}"/></button>
        <p class="form-text"><fmt:message key="pagesHelp" bundle="${cds}"/></p>
    </section>
    <form id="cdsReviewForm" method="POST" action="${pageContext.request.contextPath}/form/cdsImportControl" class="border rounded p-3" hidden>
        <h2 class="h5"><fmt:message key="review" bundle="${cds}"/></h2>
        <p><fmt:message key="reviewHelp" bundle="${cds}"/></p>
        <input id="reviewDiagnosticId" type="hidden"><input id="reviewPatientKey" type="hidden">
        <input id="reviewUnitKey" type="hidden"><input id="reviewVersion" type="hidden">
        <label for="reviewKind" class="form-label"><fmt:message key="reviewKind" bundle="${cds}"/></label>
        <select id="reviewKind" class="form-select"><option value="CORRECT_VALUE"><fmt:message key="correctValue" bundle="${cds}"/></option>
            <option id="reviewDeclineOption" value="DECLINE_PATIENT"><fmt:message key="declinePatient" bundle="${cds}"/></option></select>
        <p id="reviewPatientHelp" class="form-text" role="status"></p>
        <label for="reviewXPath" class="form-label">XML XPath</label><input id="reviewXPath" class="form-control" maxlength="2048" aria-describedby="reviewXPathHelp">
        <p id="reviewXPathHelp" class="form-text"><fmt:message key="reviewXPathHelp" bundle="${cds}"/></p>
        <label for="reviewValue" class="form-label"><fmt:message key="replacement" bundle="${cds}"/></label>
        <textarea id="reviewValue" class="form-control" rows="3" maxlength="65536"></textarea>
        <label for="reviewReason" class="form-label"><fmt:message key="reason" bundle="${cds}"/></label>
        <textarea id="reviewReason" class="form-control" rows="2" maxlength="4000" required></textarea>
        <button type="submit" class="btn btn-primary mt-2"><fmt:message key="submitReview" bundle="${cds}"/></button>
    </form>
</main>
</body>
</html>
