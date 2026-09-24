<!DOCTYPE html>
<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
    Patient portal: the staff page for one patient's portal access (issue #3854).

    Laid out like the demographic screen it is opened from, in the same window: a patient header, the
    patient's navigation down the left (Master Record, Appointment History, this page), and panels.

    Reached through demographic/portalManage, whose gate (PortalManage2Action) requires demographic
    read access plus portal invite or account read rights, and sets which controls to render. The page
    holds no patient data itself: portal-manage.js loads demographic/portalPanel and performs every
    action through demographic/portalInvite and demographic/portalAccount, each of which checks its own
    privileges. Everything the page says comes from the message list below, so it is translated with
    the rest of CARLOS: delivery outcomes and invitation refusals arrive as codes and are looked up
    there. Only a refusal the list does not know (a consent block, which carries the email layer's own
    explanation, or a portal failure) is shown as the server worded it.

    Request attributes: portalDemographicNo, portalCanInvite, portalCanRecover, portalCanRevoke, portalCanSetAccess,
    portalCanUnlock, and, when portalCanInvite, portalConsentName, portalConsentStatus and portalConsentLabelKey.

    @since 2026-09-22
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="demographicRecord" value="${ctx}/demographic/DemographicEdit?demographic_no=${portalDemographicNo}"/>
<%-- As text: a number parameter would be formatted with digit grouping ("#1,234"). --%>
<c:set var="demographicText">${portalDemographicNo}</c:set>
<fmt:message key="demographic.portal.patientNumber" var="patientNumber">
    <fmt:param value="${demographicText}"/>
</fmt:message>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="demographic.portal.title"/></title>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <link href="${carlos:forHtmlAttribute(ctx)}/share/css/portal-manage.css" rel="stylesheet">
</head>
<body class="portal-page">
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>
<div id="portal-manage"
     data-context="${carlos:forHtmlAttribute(ctx)}"
     data-demographic-no="${carlos:forHtmlAttribute(portalDemographicNo)}"
     data-can-invite="${portalCanInvite ? 'true' : 'false'}"
     data-can-recover="${portalCanRecover ? 'true' : 'false'}"
     data-can-revoke="${portalCanRevoke ? 'true' : 'false'}"
     data-can-set-access="${portalCanSetAccess ? 'true' : 'false'}"
     data-can-unlock="${portalCanUnlock ? 'true' : 'false'}">
    <header class="portal-header">
        <span class="portal-header__title"><fmt:message key="demographic.portal.title"/></span>
        <span class="portal-header__details"><carlos:encode value="${patientNumber}"/></span>
    </header>

    <div class="portal-layout">
        <nav class="portal-sidebar" aria-label="<fmt:message key="demographic.portal.navigation"/>">
            <a href="${carlos:forHtmlAttribute(demographicRecord)}"><fmt:message key="encounter.Index.masterFile"/></a>
            <a href="${carlos:forHtmlAttribute(ctx)}/demographic/DemographicApptHistory?demographic_no=${carlos:forUriComponent(portalDemographicNo)}&amp;orderby=appttime&amp;dboperation=appt_history&amp;limit1=0&amp;limit2=25"><fmt:message key="demographic.demographiceditdemographic.btnApptHist"/></a>
            <span class="portal-sidebar__current" aria-current="page"><fmt:message key="demographic.portal.link"/></span>
        </nav>

        <main class="portal-content">
            <div class="portal-toolbar">
                <strong><carlos:encode value="${patientNumber}"/></strong>
                <div class="portal-toolbar__actions">
                    <button type="button" id="portal-refresh" class="portal-button portal-button--secondary"><fmt:message key="demographic.portal.refresh"/></button>
                    <a class="portal-button portal-button--primary" href="${carlos:forHtmlAttribute(demographicRecord)}"><fmt:message key="demographic.portal.back"/></a>
                </div>
            </div>

            <div class="portal-intro">
                <h1><fmt:message key="demographic.portal.intro.heading"/></h1>
                <p><fmt:message key="demographic.portal.intro.text"/></p>
            </div>

            <div id="portal-status" class="portal-status" role="status" aria-live="polite" hidden></div>

            <section class="portal-card" aria-labelledby="portal-account-heading">
                <h2 id="portal-account-heading"><fmt:message key="demographic.portal.account.heading"/></h2>
                <div class="portal-card__body" id="portal-account"><fmt:message key="demographic.portal.loading"/></div>
            </section>

            <section class="portal-card" aria-labelledby="portal-invites-heading">
                <h2 id="portal-invites-heading"><fmt:message key="demographic.portal.invites.heading"/></h2>
                <div class="portal-card__body">
                    <form id="portal-invite-form" class="portal-invite-form" hidden>
                        <div>
                            <label for="portal-channel"><fmt:message key="demographic.portal.invites.channel"/></label>
                            <select id="portal-channel" name="channel">
                                <option value="email"><fmt:message key="demographic.portal.invites.channel.email"/></option>
                                <option value="sms" disabled><fmt:message key="demographic.portal.invites.channel.sms"/></option>
                            </select>
                        </div>
                        <div class="portal-consent">
                            <span class="portal-consent__label"><fmt:message key="demographic.portal.invites.consentOnChart"/></span>
                            <span id="portal-consent-status" data-status="${carlos:forHtmlAttribute(portalConsentStatus)}">
                                <c:if test="${not empty portalConsentLabelKey}"><fmt:message key="${portalConsentLabelKey}"/></c:if>
                                <c:if test="${not empty portalConsentName}"><span class="portal-muted">(<carlos:encode value="${portalConsentName}"/>)</span></c:if>
                            </span>
                            <%-- The email layer sends on an opt-in, and on an unknown only with a documented override;
                                 it refuses an opt-out, or any email when consent tracking is not configured. --%>
                            <c:choose>
                                <c:when test="${portalConsentStatus == 'UNKNOWN'}">
                                    <div class="portal-check">
                                        <input type="checkbox" id="portal-consent-override" name="consentOverride" value="true">
                                        <label for="portal-consent-override"><fmt:message key="demographic.portal.invites.consentOverride"/></label>
                                        <fmt:message key="demographic.portal.invites.consentReason" var="consentReasonLabel"/>
                                        <input type="text" id="portal-consent-reason" name="consentOverrideReason" maxlength="255" hidden
                                               placeholder="${carlos:forHtmlAttribute(consentReasonLabel)}"
                                               aria-label="${carlos:forHtmlAttribute(consentReasonLabel)}">
                                    </div>
                                </c:when>
                                <c:when test="${portalConsentStatus == 'OPT_OUT'}">
                                    <div class="portal-error"><fmt:message key="demographic.portal.invites.consent.optOut"/></div>
                                </c:when>
                                <c:when test="${portalConsentStatus == 'NOT_CONFIGURED'}">
                                    <div class="portal-error"><fmt:message key="demographic.portal.invites.consent.notConfigured"/></div>
                                </c:when>
                            </c:choose>
                        </div>
                        <div>
                            <button type="submit" id="portal-invite" class="portal-button portal-button--primary"><fmt:message key="demographic.portal.invites.invite"/></button>
                        </div>
                    </form>
                    <div id="portal-invites"><fmt:message key="demographic.portal.loading"/></div>
                </div>
            </section>

            <section class="portal-card" aria-labelledby="portal-deliveries-heading">
                <h2 id="portal-deliveries-heading"><fmt:message key="demographic.portal.deliveries.heading"/></h2>
                <div class="portal-card__body" id="portal-deliveries"><fmt:message key="demographic.portal.loading"/></div>
            </section>
        </main>
    </div>

    <ul id="portal-messages" hidden>
        <%-- page --%>
        <li data-key="yes"><fmt:message key="global.yes"/></li>
        <li data-key="no"><fmt:message key="global.no"/></li>
        <c:forTokens var="key" delims="," items="done,error.generic,loading">
            <li data-key="${carlos:forHtmlAttribute(key)}"><fmt:message key="demographic.portal.${key}"/></li>
        </c:forTokens>
        <%-- account --%>
        <c:forTokens var="key" delims="," items="account.active,account.confirmDisable,account.confirmEnable,account.confirmUnlock,account.disable,account.disableReason,account.disabled,account.enable,account.field.disabledAt,account.field.disabledReason,account.field.locked,account.field.resetRequired,account.field.status,account.none,account.reasonRequired,account.unlock">
            <li data-key="${carlos:forHtmlAttribute(key)}"><fmt:message key="demographic.portal.${key}"/></li>
        </c:forTokens>
        <%-- invitations --%>
        <c:forTokens var="key" delims="," items="invites.by,invites.confirmReplace,invites.confirmRevoke,invites.confirmWithdrawStale,invites.expires,invites.issued,invites.none,invites.resend,invites.revoke,invites.status,invites.status.accepted,invites.status.pending,invites.status.prepared,invites.status.revoked,invites.status.superseded">
            <li data-key="${carlos:forHtmlAttribute(key)}"><fmt:message key="demographic.portal.${key}"/></li>
        </c:forTokens>
        <%-- delivery states --%>
        <c:forTokens var="key" delims="," items="deliveries.state.abandoned,deliveries.state.committed,deliveries.state.prepared,deliveries.state.preparing,deliveries.state.queued,deliveries.state.revoked,deliveries.state.send_failed,deliveries.state.send_uncertain,deliveries.state.sent">
            <li data-key="${carlos:forHtmlAttribute(key)}"><fmt:message key="demographic.portal.${key}"/></li>
        </c:forTokens>
        <%-- delivery outcomes --%>
        <c:forTokens var="key" delims="," items="deliveries.outcome.abandoned_by_staff,deliveries.outcome.chart_note_failed,deliveries.outcome.commit_refused,deliveries.outcome.commit_unconfirmed,deliveries.outcome.confirmed_not_sent,deliveries.outcome.confirmed_sent,deliveries.outcome.prepare_refused,deliveries.outcome.prepare_unconfirmed,deliveries.outcome.send_blocked,deliveries.outcome.send_refused,deliveries.outcome.send_unconfirmed,deliveries.replacementMayBeLost,deliveries.revokeFailed">
            <li data-key="${carlos:forHtmlAttribute(key)}"><fmt:message key="demographic.portal.${key}"/></li>
        </c:forTokens>
        <%-- delivery decisions --%>
        <c:forTokens var="key" delims="," items="deliveries.confirm.abandon,deliveries.confirm.confirmNotSent,deliveries.confirm.confirmSent,deliveries.decision.abandon,deliveries.decision.confirmNotSent,deliveries.decision.confirmSent">
            <li data-key="${carlos:forHtmlAttribute(key)}"><fmt:message key="demographic.portal.${key}"/></li>
        </c:forTokens>
        <%-- deliveries, other --%>
        <c:forTokens var="key" delims="," items="deliveries.none,deliveries.waiting,deliveries.when">
            <li data-key="${carlos:forHtmlAttribute(key)}"><fmt:message key="demographic.portal.${key}"/></li>
        </c:forTokens>
        <%-- refusals, by reason code --%>
        <c:forTokens var="key" delims="," items="refusal.channel_unavailable,refusal.delivery_not_found,refusal.incomplete_date_of_birth,refusal.invalid_email,refusal.invite_already_used,refusal.invite_not_configured,refusal.invite_not_pending,refusal.missing_email,refusal.missing_health_card,refusal.patient_not_found,refusal.pending_invite_exists,refusal.portal_connection_changed,refusal.recovery_not_allowed,refusal.recovery_too_early,refusal.stale_attempt_exists,refusal.state_changed">
            <li data-key="${carlos:forHtmlAttribute(key)}"><fmt:message key="demographic.portal.${key}"/></li>
        </c:forTokens>
    </ul>
</div>
<script src="${carlos:forHtmlAttribute(ctx)}/share/javascript/demographic/portal-manage.js"></script>
</body>
</html>
