<%--

    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%
    String curUser_no = (String) session.getAttribute("user");
%>
<%@ page import="java.sql.*, java.util.*,io.github.carlos_emr.*" errorPage="/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.billing.ca.on.pageUtil.*" %>
<%@ page import="io.github.carlos_emr.carlos.billing.ca.on.data.*" %>


<%@page import="io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Appointment" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.BillingDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Billing" %>
<%@ page import="io.github.carlos_emr.carlos.appt.ApptStatusData" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingCorrectionPrep" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>

<%
    AppointmentArchiveDao appointmentArchiveDao = (AppointmentArchiveDao) SpringUtils.getBean(AppointmentArchiveDao.class);
    OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
    BillingDao billingDao = SpringUtils.getBean(BillingDao.class);
%>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <script LANGUAGE="JavaScript">
        <!--
        function start() {
            this.focus();
        }

        function closeit() {
            //self.opener.refresh();
            //self.close();
        }

        //-->
    </script>
</head>
<body onload="start()">
<center>
    <table border="0" cellspacing="0" cellpadding="0" width="90%">
        <tr bgcolor="#486ebd">
            <th align="CENTER"><font face="Helvetica" color="#FFFFFF">
                DELETE A BILLING RECORD</font></th>
        </tr>
    </table>
    <%
        String billCode = " ";
        String apptNo = request.getParameter("appointment_no");
        String billNo = "";


        for (Billing b : billingDao.findByAppointmentNo(Integer.parseInt(apptNo))) {
            billCode = b.getStatus();
            billNo = b.getId().toString();
        }

        if (billCode.substring(0, 1).compareTo("B") == 0) {
    %>
    <p>
    <h1>Sorry, cannot delete billed items.</h1>

    <form><input type="button" value="Back to previous page"
                 onClick="window.close()"></form>
    <% } else {


        int rowsAffected = 0;
        OscarProperties props = OscarProperties.getInstance();
        if (props.getProperty("isNewONbilling", "").equals("true")) {
            //search bill status
            BillingCorrectionPrep dbObj = new BillingCorrectionPrep();
            List<String> billStatus = dbObj.getBillingNoStatusByAppt(apptNo);
            //delete the bill
            if (billStatus != null && ((billStatus.size() == 0) || (billStatus.size() > 1 && ((String) billStatus.get(billStatus.size() - 1)).startsWith("B")))) {
                out.println("Sorry, cannot delete billed items.");
            } else if (billStatus != null) {
                for (int idx = 0; idx < billStatus.size(); idx += 2) {
                    if (!((String) billStatus.get(idx)).equals("D")) {
                        rowsAffected = dbObj.deleteBilling((String) billStatus.get(idx), "D", curUser_no) ? 1 : 0;
                    }
                }
            }

        } else {
            Billing b = billingDao.find(Integer.parseInt(billNo));
            if (b != null) {
                b.setStatus("D");
                billingDao.merge(b);
            }
            rowsAffected = 1;
        }

        if (rowsAffected == 1) {
            ApptStatusData as = new ApptStatusData();
            String unbillStatus = as.unbillStatus(request.getParameter("status"));
            Appointment appt = appointmentDao.find(Integer.parseInt(request.getParameter("appointment_no")));
            appointmentArchiveDao.archiveAppointment(appt);
            if (appt != null) {
                appt.setStatus(unbillStatus);
                appt.setLastUpdateUser((String) session.getAttribute("user"));
                appointmentDao.merge(appt);
            }
    %>
    <p>
    <h1>Successful Addition of a billing Record.</h1>

    <script LANGUAGE="JavaScript">
        self.close();
        self.opener.refresh();
    </script>
    <%
        //  break; //get only one billing_no
        //  }//end of while
    } else {
    %>
    <p>
    <h1>Sorry, addition has failed.</h1>

    <%
            }
        }
    %>
    <p></p>
    <hr width="90%">
    <form><input type="button" value="Close this window"
                 onClick="window.close()"></form>
</center>
</body>
</html>
