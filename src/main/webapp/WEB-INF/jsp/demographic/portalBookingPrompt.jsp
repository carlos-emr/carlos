<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%@ page import="io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%
    int portalPatient;
    try { portalPatient = Integer.parseInt(request.getParameter("portalBookingPatient")); }
    catch (NumberFormatException invalid) { return; }
    LoggedInInfo portalSession = LoggedInInfo.getLoggedInInfoFromSession(request);
    SecurityInfoManager portalSecurity = SpringUtils.getBean(SecurityInfoManager.class);
    String portalPatientScope = String.valueOf(portalPatient);
    if (portalPatient <= 0 || portalSession == null
            || !portalSecurity.hasPrivilege(portalSession, "_demographic", SecurityInfoManager.READ, portalPatientScope)
            || !portalSecurity.isAllowedAccessToPatientRecord(portalSession, portalPatient)
            || !portalSecurity.hasPrivilege(portalSession, PortalStaffContextResolver.OBJECT_BOOKING_PROMPT,
                    SecurityInfoManager.READ, portalPatientScope)) { return; }
    boolean portalMayWrite = portalSecurity.hasPrivilege(portalSession,
            PortalStaffContextResolver.OBJECT_BOOKING_PROMPT, SecurityInfoManager.WRITE, portalPatientScope);
    boolean portalMayCreate = portalMayWrite;
%>
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>
<link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/css/portalBookingPrompt.css">
<section class="portal-booking" data-portal-booking
         data-endpoint="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/demographic/portalBookingPrompt"
         data-patient="<%= portalPatient %>"
         data-patient-input="<%= "#demographic_no".equals(request.getParameter("portalBookingPatientInput")) ? "#demographic_no" : "" %>"
         data-actor="<carlos:encode value='<%= portalSession.getLoggedInProviderNo() %>' context="htmlAttribute"/>">
    <h2><fmt:message key="portal.booking.title"/></h2>
    <p data-role="status" role="status" aria-live="polite"><fmt:message key="portal.booking.loading"/></p>
    <noscript><fmt:message key="portal.booking.noscript"/></noscript>
    <% if (portalMayCreate) { %>
    <div data-role="create" hidden>
        <label><fmt:message key="portal.booking.urgency"/>
            <select data-role="urgency">
                <option value="routine"><fmt:message key="portal.booking.routine"/></option>
                <option value="soon"><fmt:message key="portal.booking.soon"/></option>
                <option value="as_soon_as_possible"><fmt:message key="portal.booking.as_soon_as_possible"/></option>
            </select>
        </label>
        <label><fmt:message key="portal.booking.appointmentType"/>
            <select data-role="appointmentType">
                <option value="follow_up"><fmt:message key="portal.booking.follow_up"/></option>
                <option value="annual_exam"><fmt:message key="portal.booking.annual_exam"/></option>
                <option value="lab_review"><fmt:message key="portal.booking.lab_review"/></option>
            </select>
        </label>
        <button type="button" data-role="send" disabled><fmt:message key="portal.booking.send"/></button>
    </div>
    <% } %>
    <button type="button" data-role="refresh"><fmt:message key="portal.booking.refresh"/></button>
    <ul data-role="prompts"></ul>
    <div data-role="messages" hidden>
        <span data-message="ready"><fmt:message key="portal.booking.ready"/></span>
        <span data-message="patientChanged"><fmt:message key="portal.booking.patientChanged"/></span>
        <span data-message="title"><fmt:message key="portal.booking.title"/></span>
        <span data-message="loading"><fmt:message key="portal.booking.loading"/></span>
        <span data-message="inactive"><fmt:message key="portal.booking.inactive"/></span>
        <span data-message="unavailable"><fmt:message key="portal.booking.unavailable"/></span>
        <span data-message="urgency"><fmt:message key="portal.booking.urgency"/></span>
        <span data-message="appointmentType"><fmt:message key="portal.booking.appointmentType"/></span>
        <span data-message="routine"><fmt:message key="portal.booking.routine"/></span>
        <span data-message="soon"><fmt:message key="portal.booking.soon"/></span>
        <span data-message="as_soon_as_possible"><fmt:message key="portal.booking.as_soon_as_possible"/></span>
        <span data-message="follow_up"><fmt:message key="portal.booking.follow_up"/></span>
        <span data-message="annual_exam"><fmt:message key="portal.booking.annual_exam"/></span>
        <span data-message="lab_review"><fmt:message key="portal.booking.lab_review"/></span>
        <span data-message="send"><fmt:message key="portal.booking.send"/></span>
        <span data-message="retry"><fmt:message key="portal.booking.retry"/></span>
        <span data-message="refresh"><fmt:message key="portal.booking.refresh"/></span>
        <span data-message="sending"><fmt:message key="portal.booking.sending"/></span>
        <span data-message="sent"><fmt:message key="portal.booking.sent"/></span>
        <span data-message="uncertain"><fmt:message key="portal.booking.uncertain"/></span>
        <span data-message="notSent"><fmt:message key="portal.booking.notSent"/></span>
        <span data-message="storage"><fmt:message key="portal.booking.storage"/></span>
        <span data-message="empty"><fmt:message key="portal.booking.empty"/></span>
        <span data-message="created"><fmt:message key="portal.booking.created"/></span>
        <span data-message="read"><fmt:message key="portal.booking.read"/></span>
        <span data-message="unread"><fmt:message key="portal.booking.unread"/></span>
        <span data-message="withdraw"><fmt:message key="portal.booking.withdraw"/></span>
        <span data-message="withdrawing"><fmt:message key="portal.booking.withdrawing"/></span>
        <span data-message="withdrawn"><fmt:message key="portal.booking.withdrawn"/></span>
        <span data-message="withdrawFailed"><fmt:message key="portal.booking.withdrawFailed"/></span>
        <span data-message="state.sent"><fmt:message key="portal.booking.state.sent"/></span>
        <span data-message="state.read"><fmt:message key="portal.booking.state.read"/></span>
        <span data-message="state.choice_pending"><fmt:message key="portal.booking.state.choice_pending"/></span>
        <span data-message="state.booked"><fmt:message key="portal.booking.state.booked"/></span>
        <span data-message="state.declined_all"><fmt:message key="portal.booking.state.declined_all"/></span>
        <span data-message="state.withdrawn"><fmt:message key="portal.booking.state.withdrawn"/></span>
        <span data-message="state.expired"><fmt:message key="portal.booking.state.expired"/></span>
        <span data-message="noscript"><fmt:message key="portal.booking.noscript"/></span>
    </div>
    <template data-role="withdraw-template">
        <% if (portalMayWrite) { %><button type="button"><fmt:message key="portal.booking.withdraw"/></button><% } %>
    </template>
</section>
<script defer src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/portalBookingPrompt.js"></script>
