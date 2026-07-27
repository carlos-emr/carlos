<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
    Offers a clinician an exact, one-time approval for an eForm render the completeness gate refused.

    Shared by every eForm path that can be refused (download, save-as-eDoc). The target route and
    button label come from request attributes so the category list below exists exactly once: a
    per-path copy would drift, and a category missing from one copy is one those clinicians approve
    without ever seeing.

    Every category the completeness report carries is listed. That is deliberate and must stay that
    way: the approval token's digest binds to the COMPLETE issue set, so a category omitted here is
    one the clinician is being asked to approve without having seen it.

    The retry posts to eform/downloadEFormPdf, NOT back to eform/addEForm. addEForm calls
    saveEformData, which persists a new eForm on every submit, so re-posting to approve a render
    would duplicate the saved clinical record. It would also require carrying every form field —
    patient data — through this page as hidden inputs. The eForm is already saved by this point;
    only its rendering failed, so fdid, the demographic and the token are all the retry needs.

    @since 2026-07-26
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <title>eForm content could not be rendered</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <style>
        .missing-content-card { max-width: 720px; margin: 32px auto; }
    </style>
</head>
<body>
<div class="card missing-content-card">
    <div class="card-header bg-warning-subtle">
        <h5 class="mb-0">Some eForm content could not be loaded</h5>
    </div>
    <div class="card-body">
        <p><carlos:encode value="${missingContentMessage}"/></p>
        <p class="text-muted small">
            Proceeding with an incomplete clinical document is your decision. Consider cancelling and
            correcting the eForm when any required content or behavior is missing.
        </p>
        <ul class="small">
            <li>Failed content resources: <carlos:encode value="${failedContentResources}"/></li>
            <li>Excluded visible elements: <carlos:encode value="${excludedContentElements}"/></li>
            <li>Signature missing: <carlos:encode value="${signatureMissing}"/></li>
            <li>Provider signature stamp not on file: <carlos:encode value="${providerStampMissing}"/></li>
            <li>Timer compatibility failed: <carlos:encode value="${timerCompatibilityFailure}"/></li>
            <li>Page script errors: <carlos:encode value="${severeConsoleErrors}"/></li>
            <li>Blocked dialogs or pop-ups: <carlos:encode value="${containedInteractions}"/></li>
            <li>Page captured before it finished building: <carlos:encode value="${stabilizationCapped}"/></li>
            <li>Lab decision support unavailable: <carlos:encode value="${labDecisionSupportStubbed}"/></li>
        </ul>
        <div class="d-flex gap-2 mt-3">
            <form method="post" action="${pageContext.request.contextPath}/${approvalAction}">
                <input type="hidden" name="fdid" value="<carlos:encode value="${fdid}" context="htmlAttribute"/>">
                <input type="hidden" name="demographicNo" value="<carlos:encode value="${demographicNo}" context="htmlAttribute"/>">
                <input type="hidden" name="parentAjaxId" value="eforms">
                <input type="hidden" name="renderApproval" value="<carlos:encode value="${renderApproval}" context="htmlAttribute"/>">
                <button type="submit" class="btn btn-warning">
                    <carlos:encode value="${approvalButtonLabel}"/>
                </button>
            </form>
            <button type="button" class="btn btn-secondary" onclick="history.back();">Cancel</button>
        </div>
    </div>
</div>
</body>
</html>
