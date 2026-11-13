<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    "groupSaved" result of appointment/appointmentgrouprecords (the Group
    Appointment page), answered by ViewAppointmentSelfPost2Action to a repeat of
    a rendering whose group action (Add Group Appointment, Group Update, Group
    Cancel or Group Delete) already saved: a double click, a re-click while a
    slow save was out, or a reload of the result. The page's one-time submission
    token (FormSubmissionGuard, see AppointmentGroupSubmission) was spent by the
    first press, so this request wrote nothing.

    The browser keeps whichever response arrives last, often this one, so this is
    the same page the first save ended on in appointmentgrouprecords.jsp: the
    success message, a refresh of the day sheet behind the popup, and the popup
    closing. It echoes no request data.

    @since 2026-11-13
--%><%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<h1><fmt:message key="appointment.appointmentgrouprecords.msgAddSuccess"/></h1>
<script LANGUAGE="JavaScript">
    self.opener.refresh();
    self.close();
</script>
