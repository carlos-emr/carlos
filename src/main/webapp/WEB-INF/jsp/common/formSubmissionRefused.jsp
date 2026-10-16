<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    Shared "submissionRefused" result for create forms guarded by
    io.github.carlos_emr.carlos.utility.FormSubmissionGuard (issue #4225):
    Add Appointment, the Ontario bill review Save, Add Demographic and
    Consultation configuration > Add Service.

    A repeated submission of one rendered form (double click, double Enter, a
    re-click while a slow save is out, or a reload of the result page) is
    normally answered with the first save's own result page. This page is only
    for the repeats that cannot be: the first save is still running, its outcome
    could not be confirmed, or the token is not one this session issued. The
    action has already set HTTP 409 and Cache-Control: no-store; nothing was
    written by the request that produced this page.

    Request attribute: formSubmissionMessageKey, one of the
    global.formSubmission.* bundle keys chosen by FormSubmissionGuard.refuse().
    The page echoes no request data.

    @since 2026-10-15
--%><%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <title><fmt:message key="global.formSubmission.title"/></title>
</head>
<body class="p-4">
<div class="alert alert-warning" role="alert" id="formSubmissionRefused">
    <h5 class="alert-heading"><fmt:message key="global.formSubmission.title"/></h5>
    <p class="mb-0"><fmt:message key="${formSubmissionMessageKey}"/></p>
</div>
<button type="button" class="btn btn-secondary" onclick="history.back();">
    <fmt:message key="global.btnBack"/>
</button>
<button type="button" class="btn btn-secondary" onclick="if (window.opener) { window.close(); } else { history.back(); }">
    <fmt:message key="global.btnClose"/>
</button>
</body>
</html>
