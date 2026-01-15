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

<%@page import="org.apache.commons.lang3.StringUtils" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_appointment" rights="u" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_appointment");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="java.sql.*, java.util.*, ca.openosp.*, ca.openosp.openo.util.*, ca.openosp.openo.commn.OtherIdManager" %>
<%@ page import="ca.openosp.openo.event.EventService" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@page import="ca.openosp.openo.commn.dao.AppointmentArchiveDao" %>
<%@page import="ca.openosp.openo.commn.dao.OscarAppointmentDao" %>
<%@page import="ca.openosp.openo.commn.model.Appointment" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.util.ConversionUtils" %>
<%@ page import="ca.openosp.MyDateFormat" %>
<%
    AppointmentArchiveDao appointmentArchiveDao = (AppointmentArchiveDao) SpringUtils.getBean(AppointmentArchiveDao.class);
    OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
    String changedStatus = null;
%>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    </head>

    <body>
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="90%">
            <tr bgcolor="#486ebd">
                <th align="CENTER"><font face="Helvetica" color="#FFFFFF">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentupdatearecord.msgMainLabel"/></font></th>
            </tr>
        </table>
        <%
            String updateuser = (String) session.getAttribute("user");

            int rowsAffected = 0;
            Appointment appt = appointmentDao.find(Integer.parseInt(request.getParameter("appointment_no")));
            appointmentArchiveDao.archiveAppointment(appt);

            //Did the appt status change ?
            if (!appt.getStatus().equals(request.getParameter("status"))) {
                changedStatus = request.getParameter("status");
            }

            if (request.getParameter("buttoncancel") != null && (request.getParameter("buttoncancel").equals("Cancel Appt") || request.getParameter("buttoncancel").equals("No Show"))) {
                changedStatus = request.getParameter("buttoncancel").equals("Cancel Appt") ? "C" : "N";
                if (appt != null) {
                    appt.setStatus(request.getParameter("buttoncancel").equals("Cancel Appt") ? "C" : "N");
                    appt.setLastUpdateUser(updateuser);
                    appointmentDao.merge(appt);
                    rowsAffected = 1;
                }

            } else {

                if (appt != null) {
                    if (request.getParameter("demographic_no") != null && !(request.getParameter("demographic_no").equals(""))) {
                        appt.setDemographicNo(Integer.parseInt(request.getParameter("demographic_no")));
                    } else {
                        appt.setDemographicNo(0);
                    }
                    appt.setAppointmentDate(ConversionUtils.fromDateString(request.getParameter("appointment_date")));
                    appt.setStartTime(ConversionUtils.fromTimeString(MyDateFormat.getTimeXX_XX_XX(request.getParameter("start_time"))));
                    appt.setEndTime(ConversionUtils.fromTimeString(MyDateFormat.getTimeXX_XX_XX(request.getParameter("end_time"))));
                    appt.setName(request.getParameter("keyword"));
                    appt.setNotes(request.getParameter("notes"));
                    appt.setReason(request.getParameter("reason"));
                    appt.setLocation(request.getParameter("location"));
                    appt.setResources(request.getParameter("resources"));
                    appt.setType(request.getParameter("type"));
                    appt.setStyle(request.getParameter("style"));
                    appt.setBilling(request.getParameter("billing"));
                    appt.setStatus(request.getParameter("status"));
                    appt.setLastUpdateUser(updateuser);
                    appt.setRemarks(request.getParameter("remarks"));
                    appt.setUpdateDateTime(new java.util.Date());
                    appt.setUrgency((request.getParameter("urgency") != null) ? request.getParameter("urgency") : "");
                    String rc = request.getParameter("reasonCode");
                    if (!StringUtils.isEmpty(rc)) {
                        appt.setReasonCode(Integer.parseInt(rc));
                    }

                    appointmentDao.merge(appt);
                    rowsAffected = 1;
                }

            }
            if (rowsAffected == 1) {
        %>
        <p>
        <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentupdatearecord.msgUpdateSuccess"/></h1>

        <script LANGUAGE="JavaScript">
            <%
                if(!(request.getParameter("printReceipt")==null) && request.getParameter("printReceipt").equals("1")) {
            %>
            popupPage(350, 750, 'printappointment.jsp?appointment_no=<%=request.getParameter("appointment_no")%>');
            <%}%>
            self.opener.refresh();
            self.close();
        </script>
        <%
            String apptNo = request.getParameter("appointment_no");
            String mcNumber = request.getParameter("appt_mc_number");
            OtherIdManager.saveIdAppointment(apptNo, "appt_mc_number", mcNumber);

            if (changedStatus != null) {
                EventService eventService = SpringUtils.getBean(EventService.class); //updating an appt from the appt update screen delete doesn't work
                eventService.appointmentStatusChanged(this, apptNo.toString(), appt.getProviderNo(), changedStatus);
            }
            // End External Prescriber
        } else {
        %>
        <p>
        <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.appointmentupdatearecord.msgUpdateFailure"/></h1>

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
