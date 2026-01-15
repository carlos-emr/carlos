<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada

--%>
<%@page import="ca.openosp.openo.log.LogAction" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@page import="ca.openosp.openo.commn.dao.AppointmentArchiveDao" %>
<%@page import="ca.openosp.openo.commn.dao.OscarAppointmentDao" %>
<%@page import="ca.openosp.openo.commn.model.Appointment" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%
    AppointmentArchiveDao appointmentArchiveDao = (AppointmentArchiveDao) SpringUtils.getBean(AppointmentArchiveDao.class);
    OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
%>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    </head>
    <body onload="start()">
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="90%">
            <tr bgcolor="#486ebd">
                <th align="CENTER"><font face="Helvetica" color="#FFFFFF">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentdeletearecord.msgLabel"/></font></th>
            </tr>
        </table>
        <%
            Appointment appt = appointmentDao.find(Integer.parseInt(request.getParameter("appointment_no")));
            if (appt.getLastUpdateUser() == null || "".equals(appt.getLastUpdateUser())) {
                appt.setLastUpdateUser(loggedInInfo.getLoggedInProviderNo());
            }
            appointmentArchiveDao.archiveAppointment(appt);
            int rowsAffected = 0;
            if (appt != null) {
                LogAction.addLogSynchronous(loggedInInfo, "Appointment.delete", "id=" + appt.getId());
                appointmentDao.remove(appt.getId());
                rowsAffected = 1;
            }
            if (rowsAffected == 1) {
        %>
        <p>
        <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentdeletearecord.msgDeleteSuccess"/></h1>

        <script LANGUAGE="JavaScript">
            self.opener.refresh();
            self.close();
        </script>
        <%
        } else {
        %>
        <p>
        <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentdeletearecord.msgDeleteFailure"/></h1>

        <%
            }
        %>
        <p></p>
        <hr width="90%"/>
        <form>
            <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>" onClick="closeit()">
        </form>
    </center>
    </body>
</html>
