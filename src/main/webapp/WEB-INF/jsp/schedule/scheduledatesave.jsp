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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%

    String user_name = (String) session.getAttribute("userlastname") + "," + (String) session.getAttribute("userfirstname");
    String provider_no = request.getParameter("provider_no");
%>
<%@ page
        import="java.util.*, java.sql.*, io.github.carlos_emr.*, java.text.*, java.lang.*"
        errorPage="/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<jsp:useBean id="scheduleDateBean" class="java.util.Hashtable" scope="session"/>
<jsp:useBean id="scheduleRscheduleBean" class="io.github.carlos_emr.RscheduleBean" scope="session"/>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ScheduleDate" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ScheduleDateDao" %>
<%
    ScheduleDateDao scheduleDateDao = SpringUtils.getBean(ScheduleDateDao.class);
%>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="schedule.scheduledatesave.title"/></title>
    </head>
    <%
        String available = request.getParameter("available");
        String priority = "c";
        String reason = request.getParameter("reason");
        String hour = request.getParameter("hour");
        String dateParam = request.getParameter("date");

        if (provider_no == null || provider_no.isEmpty()) {
            throw new IllegalArgumentException("missing required parameter: provider_no");
        }
        if (dateParam == null || dateParam.isEmpty()) {
            throw new IllegalArgumentException("missing required parameter: date");
        }

        //save the record first, change holidaybean next
        int rowsAffected = 0;

        ScheduleDate sd = scheduleDateDao.findByProviderNoAndDate(provider_no, MyDateFormat.getSysDate(dateParam));
        if (sd != null) {
            sd.setStatus('D');
            scheduleDateDao.merge(sd);
        }
        //add R schedule date if it is available
        if (request.getParameter("Submit") != null && request.getParameter("Submit").equals(" Delete ")) {
            if (scheduleRscheduleBean.getDateAvail(dateParam)) {
                sd = new ScheduleDate();
                sd.setDate(MyDateFormat.getSysDate(dateParam));
                sd.setProviderNo(provider_no);
                sd.setAvailable('1');
                sd.setPriority('b');
                sd.setReason("");
                sd.setHour(scheduleRscheduleBean.getDateAvailHour(dateParam));
                sd.setCreator(user_name);
                sd.setStatus(scheduleRscheduleBean.active.charAt(0));
                scheduleDateDao.persist(sd);
            }
        }
        scheduleDateBean.remove(dateParam);

        if (request.getParameter("Submit") != null && request.getParameter("Submit").equals(" Save ")) {

            if (available == null || available.isEmpty()) {
                throw new IllegalArgumentException("missing required parameter: available");
            }

            sd = new ScheduleDate();
            sd.setDate(MyDateFormat.getSysDate(dateParam));
            sd.setProviderNo(provider_no);
            sd.setAvailable(available.charAt(0));
            sd.setPriority(priority.charAt(0));
            sd.setReason(reason);
            sd.setHour(hour);
            sd.setCreator(user_name);
            sd.setStatus(scheduleRscheduleBean.active.charAt(0));
            scheduleDateDao.persist(sd);

            scheduleDateBean.put(dateParam, new HScheduleDate(available, priority, reason, hour, user_name));
        }
    %>

    <script language="JavaScript">
        <!--
        opener.location.reload();
        self.close();
        //-->
    </script>
    <body>
    </body>
</html>
