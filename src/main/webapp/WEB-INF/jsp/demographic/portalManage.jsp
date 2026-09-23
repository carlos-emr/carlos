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

    Reached through demographic/portalManage, whose gate (PortalManage2Action) requires demographic
    read access plus portal invite or account read rights, and sets which controls to render. The page
    holds no patient data itself: portal-manage.js loads demographic/portalPanel and performs every
    action through demographic/portalInvite and demographic/portalAccount, each of which checks its own
    privileges. Everything the page says comes from the message list below, so it is translated with
    the rest of CARLOS: delivery outcomes and invitation refusals arrive as codes and are looked up
    there. Only a refusal the list does not know (a consent block, which carries the email layer's own
    explanation, or a portal failure) is shown as the server worded it.

    Request attributes: portalDemographicNo, portalCanInvite, portalCanRecover, portalCanRevoke, portalCanSetAccess,
    portalCanUnlock.

    @since 2026-09-22
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="demographic.portal.title"/></title>
    <link href="${carlos:forHtmlAttribute(ctx)}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="p-3">
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>
<main id="portal-manage"
      data-context="${carlos:forHtmlAttribute(ctx)}"
      data-demographic-no="${carlos:forHtmlAttribute(portalDemographicNo)}"
      data-can-invite="${portalCanInvite ? 'true' : 'false'}"
      data-can-recover="${portalCanRecover ? 'true' : 'false'}"
      data-can-revoke="${portalCanRevoke ? 'true' : 'false'}"
      data-can-set-access="${portalCanSetAccess ? 'true' : 'false'}"
      data-can-unlock="${portalCanUnlock ? 'true' : 'false'}">
    <h1 class="h4"><fmt:message key="demographic.portal.title"/></h1>
    <div id="portal-status" class="alert d-none" role="status" aria-live="polite"></div>

    <section class="mb-4" aria-labelledby="portal-account-heading">
        <h2 id="portal-account-heading" class="h5"><fmt:message key="demographic.portal.account.heading"/></h2>
        <div id="portal-account"><fmt:message key="demographic.portal.loading"/></div>
    </section>

    <section class="mb-4" aria-labelledby="portal-invites-heading">
        <h2 id="portal-invites-heading" class="h5"><fmt:message key="demographic.portal.invites.heading"/></h2>
        <form id="portal-invite-form" class="row g-2 align-items-end mb-3 d-none">
            <div class="col-auto">
                <label class="form-label" for="portal-channel"><fmt:message key="demographic.portal.invites.channel"/></label>
                <select id="portal-channel" name="channel" class="form-select form-select-sm">
                    <option value="email"><fmt:message key="demographic.portal.invites.channel.email"/></option>
                    <option value="sms" disabled><fmt:message key="demographic.portal.invites.channel.sms"/></option>
                </select>
            </div>
            <div class="col-auto">
                <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="portal-consent-override" name="consentOverride" value="true">
                    <label class="form-check-label" for="portal-consent-override"><fmt:message key="demographic.portal.invites.consentOverride"/></label>
                </div>
                <fmt:message key="demographic.portal.invites.consentReason" var="consentReasonLabel"/>
                <input class="form-control form-control-sm mt-1 d-none" type="text" id="portal-consent-reason"
                       name="consentOverrideReason" maxlength="255"
                       placeholder="${carlos:forHtmlAttribute(consentReasonLabel)}"
                       aria-label="${carlos:forHtmlAttribute(consentReasonLabel)}">
            </div>
            <div class="col-auto">
                <button type="submit" id="portal-invite" class="btn btn-primary btn-sm"><fmt:message key="demographic.portal.invites.invite"/></button>
            </div>
        </form>
        <div id="portal-invites"><fmt:message key="demographic.portal.loading"/></div>
    </section>

    <section class="mb-4" aria-labelledby="portal-deliveries-heading">
        <h2 id="portal-deliveries-heading" class="h5"><fmt:message key="demographic.portal.deliveries.heading"/></h2>
        <div id="portal-deliveries"><fmt:message key="demographic.portal.loading"/></div>
    </section>

    <ul id="portal-messages" hidden>
        <%-- page --%>
        <c:forTokens var="key" delims="," items="done,error.generic,loading">
            <li data-key="${carlos:forHtmlAttribute(key)}"><fmt:message key="demographic.portal.${key}"/></li>
        </c:forTokens>
        <%-- account --%>
        <c:forTokens var="key" delims="," items="account.active,account.disable,account.disableReason,account.disabled,account.enable,account.locked,account.none,account.resetRequired,account.unlock">
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
</main>
<script src="${carlos:forHtmlAttribute(ctx)}/share/javascript/demographic/portal-manage.js"></script>
</body>
</html>
