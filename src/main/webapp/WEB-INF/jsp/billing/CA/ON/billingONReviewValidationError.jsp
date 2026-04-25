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
  Purpose: Surface a BillingValidationException from ViewBillingONReview2Action
  (e.g. addToPatientDx with a non-numeric demographic_no) as an actionable
  message rather than the generic "CARLOS Error 500" page. The audit-trail
  integrity is preserved by the persister's ERROR log; this view is purely
  user-facing guidance.

  @since 2026-04-25
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Billing Review — Submission Rejected</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css"/>
</head>
<body>
<div class="container py-5">
    <div class="alert alert-danger" role="alert">
        <h4 class="alert-heading">Bill review could not be submitted</h4>
        <p>The "add diagnosis to the patient's disease registry" option was
           checked, but the submitted patient identifier is not a valid number.
           This usually happens when:</p>
        <ul>
            <li>The chart was reloaded mid-bill — return to the chart and try again.</li>
            <li>A browser auto-fill rewrote a hidden field — clear cached form data and retry.</li>
        </ul>
        <p class="mb-0">No data was saved. Use your browser's <strong>Back</strong>
           button to return to the bill review and either uncheck the
           "add to patient dx" option or reload the chart first.</p>
    </div>
    <div class="d-flex gap-2">
        <a class="btn btn-secondary" href="javascript:history.back()" role="button">Back</a>
        <a class="btn btn-secondary" href="${pageContext.request.contextPath}/provider/providercontrol" role="button">Schedule</a>
    </div>
</div>
</body>
</html>
