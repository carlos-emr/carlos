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

<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed2 = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_appointment" rights="r" reverse="<%=true%>">
    <%authed2 = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_appointment");%>
</security:oscarSec>
<%
    if (!authed2) {
        return;
    }
%>

<%@ page import="java.sql.*, java.util.*, ca.openosp.MyDateFormat, ca.openosp.openo.commn.OtherIdManager" %>
<%@ page import="ca.openosp.openo.event.EventService, ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.dao.OscarAppointmentDao" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="ca.openosp.openo.commn.model.Appointment" %>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="ca.openosp.openo.commn.dao.ClinicDAO" %>
<%@ page import="ca.openosp.openo.commn.model.Clinic" %>
<%@ page import="ca.openosp.openo.commn.dao.UserPropertyDAO" %>
<%@ page import="ca.openosp.openo.commn.model.UserProperty" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ include file="/common/webAppContextAndSuperMgr.jsp" %>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <style type="text/css" media="print">
            .DoNotPrint {
                display: none;
            }
        </style>
    </head>
    <body background="<%= request.getContextPath() %>/images/gray_bg.jpg"
          bgproperties="fixed" onload="window.print()">
    <center>
        <div class="DoNotPrint">
            <table border="0" cellspacing="0" cellpadding="0" width="90%">
                <tr bgcolor="#486ebd">
                    <th align="CENTER"><font face="Helvetica" color="#FFFFFF">
                        Appointment Card</font></th>
                </tr>
            </table>
            <%
                UserPropertyDAO userPropertyDao = SpringUtils.getBean(UserPropertyDAO.class);

                String strAppointmentNo = request.getParameter("appointment_no");
                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
                SimpleDateFormat timeFormatter = new SimpleDateFormat("hh:mm aaa");


                SimpleDateFormat dateFormatter2 = new SimpleDateFormat("EEE, d MMM yyyy");
                SimpleDateFormat timeFormatter2 = new SimpleDateFormat("h:mm aaa");

                OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
                Appointment appt = appointmentDao.find(Integer.parseInt(strAppointmentNo));

                ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

                //get clinic info for address
                ClinicDAO clinicDao = SpringUtils.getBean(ClinicDAO.class);
                Clinic clinic = clinicDao.getClinic();

                String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");

            %>
            <p>

        </div>

        <!-- hiding this old version -->
            <%--
            <div class="DoNotPrint">
        <form>
            <table border="1" bgcolor="white" >
                <tr><td>

                <table style="font-size: 14pt;"  align="left" valign="top">

                    <tr style="font-family: arial, sans-serif; font-size: 12pt;" >
                        <th colspan="3"><%=appt.getName()%></th>
                    </tr>
                     <tr style="font-family: arial, sans-serif; font-size: 12pt;" >
                <th style="padding-right: 10px"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formDate"/></th>
                 <th width="60" style="padding-right: 10px"><fmt:setBundle basename="oscarResources"/><fmt:message key="Appointment.formStartTime"/></th>
                <th width="120" style="padding-right: 10px"><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgProvider"/></th>

                    </tr>
                <%

                int iRow=0;
                int iPageSize=5;
                String pname="";
                // if the booking is not matched to a demographic demoNo=="0" as a default
                if( appt.getDemographicNo() == 0 ) {

                %>
                    <tr bgcolor="#eeeeff">
                <td style="padding-right: 10px"><%=dateFormatter.format(appt.getAppointmentDate())%></td>
                <td style="padding-right: 10px"><%=timeFormatter.format(appt.getStartTime())%></td>
                <td style="padding-right: 10px">&nbsp;</td>
                    </tr>
            <%
                } else {

                    for(Appointment a: appointmentDao.findDemoAppointmentsOnDate(appt.getDemographicNo(),appt.getAppointmentDate())) {
                        Provider providers = providerDao.getProvider(a.getProviderNo());

            %>
                    <tr bgcolor="#eeeeff">
                <td style="padding-right: 10px"><%=dateFormatter.format(a.getAppointmentDate())%></td>
                <td style="padding-right: 10px"><%=timeFormatter.format(a.getStartTime())%></td>
                <td style="padding-right: 10px"><%=providers.getLastName() + "," + providers.getFirstName().substring(0,1)%></td>
                    </tr>
            <%
                    }
                }
            %>

                    <tr class="DoNotPrint">
                <td style="padding-left: 10px"><input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnPrint"/>" onClick="window.print();"></td>
                        <td>&nbsp;</td>
                <td>&nbsp;</td>
                    </tr>
               </table>
               </td></tr>
        </table>
        <p>

        <p></p>
        <hr width="90%"/>


        </div>

        --%>

        <table border="1" bgcolor="white">
            <tr>
                <td style="padding: 10px 10px 10px 10px">
                    <table>
                        <tr> <!-- first row is logo | prov info -->
                            <td style="padding-right: 10px">
                                <img src="<%= request.getContextPath() %>/imageRenderingServlet?source=clinic_logo" width="200px"/>
                            </td>
                            <td>
                                <%
                                    Provider provider = providerDao.getProvider(appt.getProviderNo());

                                    String salutation = "";

                                    if (roleName$.indexOf("doctor") != -1) {
                                        salutation = "Dr.";
                                    }

                                    String firstLine = salutation + " " + provider.getFirstName() + " " + provider.getLastName();
                                    String phone = clinic.getClinicPhone();
                                    String fax = clinic.getClinicFax();


                                    UserProperty up = userPropertyDao.getProp(appt.getProviderNo(), "APPT_CARD_NAME");
                                    if (up != null && !up.getValue().isEmpty()) {
                                        firstLine = up.getValue();
                                    }

                                    up = userPropertyDao.getProp(appt.getProviderNo(), "APPT_CARD_PHONE");
                                    if (up != null && !up.getValue().isEmpty()) {
                                        phone = up.getValue();
                                    }

                                    up = userPropertyDao.getProp(appt.getProviderNo(), "APPT_CARD_FAX");
                                    if (up != null && !up.getValue().isEmpty()) {
                                        fax = up.getValue();
                                    }

                                %>
                                <b style="font-size:14pt"><%=firstLine %>
                                </b><br/>
                                <%=StringEscapeUtils.escapeHtml4(provider.getSpecialty()) %><br/>
                                <br/>
                                <%=StringEscapeUtils.escapeHtml4(clinic.getClinicAddress()) %><br/>
                                <%=StringEscapeUtils.escapeHtml4(clinic.getClinicCity()) %>
                                , <%=StringEscapeUtils.escapeHtml4(clinic.getClinicProvince()) %>  <%=StringEscapeUtils.escapeHtml4(clinic.getClinicPostal()) %>
                                <br/>
                                <%=StringEscapeUtils.escapeHtml4(phone) %><br/>
                                Fax <%=StringEscapeUtils.escapeHtml4(fax) %> <br/>
                            </td>
                        </tr>

                        <tr> <!-- patient name -->
                            <td colspan="2">
                                <b>Name</b>: <span
                                    style="text-decoration: underline;"><%=StringEscapeUtils.escapeHtml4(appt.getName()) %></span>
                            </td>
                        </tr>

                        <tr> <!-- appt date and time-->
                            <td colspan="2">
                                <b>Appointment Date</b>: <span
                                    style="text-decoration: underline;"><%=dateFormatter2.format(appt.getAppointmentDate()) %> at <%=timeFormatter2.format(appt.getStartTime()) %><span>
                            </td>
                        </tr>

                    </table>
                </td>
            </tr>
        </table>

        <div class="DoNotPrint">
            <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>" onClick="window.close();">
            &nbsp;
            <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnPrint"/>" onClick="window.print();">
        </div>
        </form>
    </center>
    </body>
</html>
