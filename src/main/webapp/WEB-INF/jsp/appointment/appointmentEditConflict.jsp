<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    "editConflict" result of the classic Edit Appointment form's write routes
    (appointment/UpdateRecord, DeleteRecord, CutRecord) and the forward target of
    the group page (appointmentgrouprecords.jsp) for a stale form.

    The edit form carries the version of the appointment it was rendered from
    (AppointmentEditConflict.PARAMETER). When another user changed, cancelled or
    removed the appointment after the popup was opened, the write is refused
    before anything is archived or stored: the action has already set HTTP 409
    and Cache-Control: no-store. This page says so, refreshes the day sheet
    behind the popup so it shows the current appointment, and offers to reopen
    the appointment as it is now.

    Request attributes (set by AppointmentEditConflict.refuse):
        appointmentConflictNo         (String, digits) - appointment to reopen; absent hides the button
        appointmentConflictProviderNo (String)         - provider of the popup, carried to the reopened form
    The page echoes nothing else the refused form posted.

    @since 2026-11-12
--%><%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <title><fmt:message key="appointment.editConflict.title"/></title>
</head>
<body class="p-4">
<div class="alert alert-danger" role="alert" id="appointmentEditConflict">
    <h5 class="alert-heading"><fmt:message key="appointment.editConflict.title"/></h5>
    <p><fmt:message key="appointment.editConflict.message"/></p>
    <p class="mb-0"><fmt:message key="appointment.editConflict.instructions"/></p>
</div>
<c:if test="${not empty appointmentConflictNo}">
    <%-- A link that carries only appointment_no is a supported way to open the edit form (#3729). --%>
    <c:set var="reopenUrl" value="${pageContext.request.contextPath}/appointment/editappointment?appointment_no=${carlos:forUriComponent(appointmentConflictNo)}"/>
    <c:if test="${not empty appointmentConflictProviderNo}">
        <c:set var="reopenUrl" value="${reopenUrl}&provider_no=${carlos:forUriComponent(appointmentConflictProviderNo)}"/>
    </c:if>
    <a class="btn btn-primary" id="appointmentEditConflictReopen" href="${carlos:forHtmlAttribute(reopenUrl)}">
        <fmt:message key="appointment.editConflict.btnReopen"/>
    </a>
</c:if>
<button type="button" class="btn btn-secondary" onclick="if (window.opener) { window.close(); } else { history.back(); }">
    <fmt:message key="global.btnClose"/>
</button>
<script>
    // Show the current appointment on the day sheet behind this popup; the popup stays open so the refusal is read.
    try { if (window.opener && typeof window.opener.refresh === 'function') { window.opener.refresh(); } } catch (e) { /* opener closed or cross-origin */ }
</script>
</body>
</html>
