<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos

    Staff-facing patient portal account screen. PortalPatient2Action is the public gate; this JSP
    remains under WEB-INF and obtains current account state only through the separately authorized
    portal JSON actions. No patient name or other demographic detail is rendered here.

    @since 2026-09-10
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="icon" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/images/favicon.ico">
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <title>Patient portal account</title>
    <style>
        .portal-account { max-width: 52rem; margin: 1rem auto; padding: 0 1rem; }
        .portal-account__card { border: 1px solid #bbb; border-radius: .25rem; padding: 1rem; }
        .portal-account__status { min-height: 1.5rem; margin-bottom: 1rem; }
        .portal-account__status--error { color: #9c1c1c; }
        .portal-account__details { display: grid; grid-template-columns: max-content 1fr; gap: .5rem 1rem; }
        .portal-account__details dt { font-weight: 700; }
        .portal-account__details dd { margin: 0; }
        .portal-account__actions { display: flex; flex-wrap: wrap; gap: .5rem; margin-top: 1rem; }
        .portal-account__actions button { min-height: 2.5rem; }
    </style>
</head>
<body class="BodyStyle">
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>
<main class="portal-account"
      id="portal-account"
      data-context-path="${carlos:forHtmlAttribute(pageContext.request.contextPath)}"
      data-demographic-no="${carlos:forHtmlAttribute(requestScope.demographicNo)}"
      data-can-manage="${carlos:forHtmlAttribute(requestScope.canManagePortalAccount)}"
      data-can-unlock="${carlos:forHtmlAttribute(requestScope.canUnlockPortalAccount)}">
    <h1>Patient portal account</h1>
    <p>
        This screen manages the selected patient's portal access. It does not change the patient's
        CARLOS EMR login or chart.
    </p>
    <p>
        CARLOS patient number:
        <strong>${carlos:forHtmlContent(requestScope.demographicNo)}</strong>
    </p>
    <section class="portal-account__card" aria-labelledby="portal-account-heading">
        <h2 id="portal-account-heading">Current status</h2>
        <div id="portal-account-status" class="portal-account__status" role="status" aria-live="polite">
            Loading portal account&hellip;
        </div>
        <dl id="portal-account-details" class="portal-account__details" hidden>
            <dt>Status</dt><dd id="portal-account-state"></dd>
            <dt>Locked</dt><dd id="portal-account-locked"></dd>
            <dt>Password reset required</dt><dd id="portal-account-reset"></dd>
            <dt id="portal-account-disabled-at-label" hidden>Disabled at</dt>
            <dd id="portal-account-disabled-at" hidden></dd>
            <dt id="portal-account-disabled-reason-label" hidden>Disabled reason</dt>
            <dd id="portal-account-disabled-reason" hidden></dd>
        </dl>
        <c:if test="${requestScope.canManagePortalAccount}">
            <div id="portal-account-disable-fields" hidden>
                <label for="portal-account-disable-reason">Reason for disabling</label>
                <input id="portal-account-disable-reason" type="text" maxlength="64" autocomplete="off">
            </div>
        </c:if>
        <div class="portal-account__actions">
            <button id="portal-account-refresh" type="button">Refresh</button>
            <c:if test="${requestScope.canUnlockPortalAccount}">
                <button id="portal-account-unlock" type="button" hidden>Clear lockout</button>
            </c:if>
            <c:if test="${requestScope.canManagePortalAccount}">
                <button id="portal-account-access" type="button" hidden></button>
            </c:if>
        </div>
    </section>
</main>
<script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/share/javascript/patientPortalAccount.js"></script>
</body>
</html>
