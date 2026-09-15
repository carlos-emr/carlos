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
    <link rel="stylesheet"
          href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/demographic/patientPortalAccount.css">
    <title>Patient portal account</title>
</head>
<body class="BodyStyle portal-account-page">
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>
<div id="portal-account"
     data-context-path="${carlos:forHtmlAttribute(pageContext.request.contextPath)}"
     data-demographic-no="${carlos:forHtmlAttribute(requestScope.demographicNo)}"
     data-can-manage="${carlos:forHtmlAttribute(requestScope.canManagePortalAccount)}"
     data-can-unlock="${carlos:forHtmlAttribute(requestScope.canUnlockPortalAccount)}">
    <header class="portal-patient-header">
        <span class="portal-patient-header__title">Patient portal account</span>
        <span class="portal-patient-header__details">
            CARLOS patient #${carlos:forHtmlContent(requestScope.demographicNo)}
        </span>
    </header>

    <div class="portal-layout">
        <nav class="portal-sidebar" aria-label="Patient navigation">
            <a href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/demographic/DemographicEdit?demographic_no=${carlos:forHtmlAttribute(requestScope.demographicNo)}">
                Demographic record
            </a>
            <a href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/demographic/DemographicApptHistory?demographic_no=${carlos:forHtmlAttribute(requestScope.demographicNo)}&amp;orderby=appttime&amp;dboperation=appt_history&amp;limit1=0&amp;limit2=25">
                Appointment history
            </a>
            <span class="portal-sidebar__current" aria-current="page">Portal account</span>
        </nav>

        <main class="portal-content">
            <div class="portal-toolbar">
                <strong>Patient #${carlos:forHtmlContent(requestScope.demographicNo)}</strong>
                <a class="portal-button portal-button--primary"
                   href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/demographic/DemographicEdit?demographic_no=${carlos:forHtmlAttribute(requestScope.demographicNo)}">
                    Back to demographic
                </a>
            </div>

            <div class="portal-account__intro">
                <h1>Portal access</h1>
                <p>
                    Manage the selected patient's portal access. These controls do not change the
                    patient's CARLOS EMR login or chart.
                </p>
            </div>

            <section class="portal-account__card" aria-labelledby="portal-account-heading">
                <h2 id="portal-account-heading">Current status</h2>
                <div class="portal-account__card-body">
                    <div id="portal-account-status"
                         class="portal-account__status"
                         role="status"
                         aria-live="polite">
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
                        <div id="portal-account-disable-fields" class="portal-account__disable-fields" hidden>
                            <label for="portal-account-disable-reason">Reason for disabling</label>
                            <input id="portal-account-disable-reason"
                                   type="text"
                                   maxlength="64"
                                   autocomplete="off"
                                   aria-describedby="portal-account-disable-help">
                            <small id="portal-account-disable-help">Required, up to 64 characters.</small>
                        </div>
                    </c:if>
                    <div class="portal-account__actions">
                        <button id="portal-account-refresh" class="portal-button portal-button--secondary" type="button">
                            Refresh
                        </button>
                        <c:if test="${requestScope.canUnlockPortalAccount}">
                            <button id="portal-account-unlock" class="portal-button portal-button--primary" type="button" hidden>
                                Clear lockout
                            </button>
                        </c:if>
                        <c:if test="${requestScope.canManagePortalAccount}">
                            <button id="portal-account-access" class="portal-button portal-button--primary" type="button" hidden></button>
                        </c:if>
                    </div>
                </div>
            </section>
        </main>
    </div>
</div>
<script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/share/javascript/patientPortalAccount.js"></script>
</body>
</html>
