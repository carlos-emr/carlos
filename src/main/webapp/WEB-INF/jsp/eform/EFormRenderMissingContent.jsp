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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <title><fmt:message key="eform.renderMissingContent.title"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <style>
        .missing-content-card { max-width: 720px; margin: 32px auto; }
    </style>
</head>
<body>
<div class="card missing-content-card">
    <div class="card-header bg-warning-subtle">
        <h5 class="mb-0"><fmt:message key="eform.renderMissingContent.heading"/></h5>
    </div>
    <div class="card-body">
        <p><carlos:encode value="${missingContentMessage}"/></p>
        <p class="text-muted small">
            <fmt:message key="eform.renderMissingContent.msgDecision"/>
        </p>
        <%-- The eForm is ALREADY SAVED at this point: AddEForm2Action persists it before the render
             runs, so this page is about PDF delivery only. Saying so explicitly is what stops a
             clinician treating Cancel as an undo and re-submitting, which would create a second
             eform_data row for one clinical act. --%>
        <p class="small"><strong><fmt:message key="eform.renderMissingContent.msgAlreadySaved"/></strong></p>
        <ul class="small">
            <li><fmt:message key="eform.renderIssue.failedContentResources"/>: <carlos:encode value="${failedContentResources}"/></li>
            <li><fmt:message key="eform.renderIssue.excludedContentElements"/>: <carlos:encode value="${excludedContentElements}"/></li>
            <li><fmt:message key="eform.renderIssue.signatureMissing"/>: <carlos:encode value="${signatureMissing}"/></li>
            <li><fmt:message key="eform.renderIssue.providerStampMissing"/>: <carlos:encode value="${providerStampMissing}"/></li>
            <li><fmt:message key="eform.renderIssue.timerCompatibilityFailure"/>: <carlos:encode value="${timerCompatibilityFailure}"/></li>
            <li><fmt:message key="eform.renderIssue.severeConsoleErrors"/>: <carlos:encode value="${severeConsoleErrors}"/></li>
            <c:if test="${not empty severeConsoleErrorDetails}">
                <%-- PHI-safe per-error descriptions (script error type + source location only, no
                     page-authored message text) so the clinician can judge the errors before
                     approving the override. --%>
                <li><fmt:message key="eform.renderIssue.severeConsoleErrorDetails"/>:
                    <ul>
                        <c:forEach var="severeConsoleErrorDetail" items="${severeConsoleErrorDetails}">
                            <li><carlos:encode value="${severeConsoleErrorDetail}"/></li>
                        </c:forEach>
                        <%-- The detail list is capped; when more severe errors occurred than are
                             shown, say how many are omitted so the list is not silently truncated. --%>
                        <c:if test="${severeConsoleErrors > fn:length(severeConsoleErrorDetails)}">
                            <li><fmt:message key="eform.renderIssue.severeConsoleErrorsMore">
                                <fmt:param value="${severeConsoleErrors - fn:length(severeConsoleErrorDetails)}"/>
                            </fmt:message></li>
                        </c:if>
                    </ul>
                </li>
            </c:if>
            <li><fmt:message key="eform.renderIssue.containedInteractions"/>: <carlos:encode value="${containedInteractions}"/></li>
            <li><fmt:message key="eform.renderIssue.stabilizationCapped"/>: <carlos:encode value="${stabilizationCapped}"/></li>
            <li><fmt:message key="eform.renderIssue.labDecisionSupportStubbed"/>: <carlos:encode value="${labDecisionSupportStubbed}"/></li>
        </ul>
        <div class="d-flex gap-2 mt-3">
            <form method="post" action="${pageContext.request.contextPath}/${approvalAction}">
                <input type="hidden" name="fdid" value="<carlos:encode value="${fdid}" context="htmlAttribute"/>">
                <input type="hidden" name="demographicNo" value="<carlos:encode value="${demographicNo}" context="htmlAttribute"/>">
                <input type="hidden" name="parentAjaxId" value="eforms">
                <input type="hidden" name="renderApproval" value="<carlos:encode value="${renderApproval}" context="htmlAttribute"/>">
                <button type="submit" class="btn btn-warning">
                    <fmt:message key="${approvalButtonLabelKey}"/>
                </button>
            </form>
            <%-- NOT history.back(): that returned the clinician to the populated, re-submittable form,
                 and re-submitting calls saveEformData again — eFormDataDao.persist is an unconditional
                 INSERT, so it produces a SECOND eform_data row for one clinical act plus any repeated
                 chart-template side effects. Navigate forward to the patient's eForm list instead, which
                 cannot resubmit and shows the row that was already saved. --%>
            <a class="btn btn-secondary"
               href="${pageContext.request.contextPath}/eform/efmpatientformlist?demographic_no=${carlos:forUriComponent(demographicNo)}&amp;parentAjaxId=eforms">
                <fmt:message key="eform.renderMissingContent.btnReturnToList"/>
            </a>
        </div>
    </div>
</div>
</body>
</html>
