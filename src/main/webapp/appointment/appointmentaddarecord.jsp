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
<security:oscarSec roleName="<%=roleName$%>" objectName="_appointment" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_appointment");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page
        import="java.sql.*, java.util.*, ca.openosp.MyDateFormat, ca.openosp.openo.demographic.data.*, ca.openosp.openo.commn.OtherIdManager, java.text.SimpleDateFormat"
        errorPage="/errorpage.jsp" %>
<%@ page import="ca.openosp.openo.commn.model.Demographic,ca.openosp.openo.appt.AppointmentMailer, ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.dao.OscarAppointmentDao" %>
<%@page import="ca.openosp.openo.commn.model.Appointment" %>
<%@page import="ca.openosp.openo.commn.dao.WaitingListDao" %>
<%@page import="ca.openosp.openo.util.ConversionUtils" %>
<%@page import="ca.openosp.openo.util.UtilDateUtilities" %>
<%@ page import="ca.openosp.openo.event.EventService" %>
<%@page import="ca.openosp.openo.managers.DemographicManager" %>
<%@ page import="ca.openosp.openo.demographic.data.DemographicMerged" %>
<%@ page import="ca.openosp.openo.demographic.data.DemographicData" %>
<%@ page import="ca.openosp.openo.waitinglist.WaitingList" %>
<%@ page import="ca.openosp.openo.commn.model.WaitingListName" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    </head>
    <body>
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="90%">
            <tr bgcolor="#486ebd">
                <th align="CENTER"><font face="Helvetica" color="#FFFFFF">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgMainLabel"/></font></th>
            </tr>
        </table>
        <%

            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

            OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
            WaitingListDao waitingListDao = SpringUtils.getBean(WaitingListDao.class);

            String createDateTime = UtilDateUtilities.DateToString(new java.util.Date(), "yyyy-MM-dd HH:mm:ss");

            String[] param = new String[20];
            param[0] = request.getParameter("provider_no");
            param[1] = request.getParameter("appointment_date");
            param[2] = MyDateFormat.getTimeXX_XX_XX(request.getParameter("start_time"));
            param[3] = MyDateFormat.getTimeXX_XX_XX(request.getParameter("end_time"));

//the keyword(name) must match the demographic_no if it has been changed
            Demographic demo = null;
            if (request.getParameter("demographic_no") != null && !(request.getParameter("demographic_no").equals(""))) {
                DemographicMerged dmDAO = new DemographicMerged();
                param[16] = dmDAO.getHead(request.getParameter("demographic_no"));

                DemographicData demData = new DemographicData();
                demo = demData.getDemographic(loggedInInfo, param[16]);
                param[4] = demo.getLastName() + "," + demo.getFirstName();
            } else {
                param[16] = "0";
                param[4] = request.getParameter("keyword");
            }

            param[5] = request.getParameter("notes");
            param[6] = request.getParameter("reason");
            param[7] = request.getParameter("location");
            param[8] = request.getParameter("resources");
            param[9] = request.getParameter("type");
            param[10] = request.getParameter("style");
            param[11] = request.getParameter("billing");
            param[12] = request.getParameter("status");
            param[13] = createDateTime;
            param[14] = request.getParameter("creator");
            param[15] = request.getParameter("remarks");
            param[17] = (String) request.getSession().getAttribute("programId_oscarView");
            param[18] = (request.getParameter("urgency") != null) ? request.getParameter("urgency") : "";
            param[19] = request.getParameter("reasonCode");

            Appointment a = new Appointment();
            a.setProviderNo(request.getParameter("provider_no"));
            a.setAppointmentDate(ConversionUtils.fromDateString(request.getParameter("appointment_date")));
            a.setStartTime(ConversionUtils.fromTimeStringNoSeconds(request.getParameter("start_time")));
            a.setEndTime(ConversionUtils.fromTimeStringNoSeconds(request.getParameter("end_time")));
            a.setName(request.getParameter("keyword"));
            a.setNotes(request.getParameter("notes"));
            a.setReason(request.getParameter("reason"));
            a.setLocation(request.getParameter("location"));
            a.setResources(request.getParameter("resources"));
            a.setType(request.getParameter("type"));
            a.setStyle(request.getParameter("style"));
            a.setBilling(request.getParameter("billing"));
            a.setStatus(request.getParameter("status"));
            a.setCreateDateTime(ConversionUtils.fromTimestampString(createDateTime));
            a.setCreator(request.getParameter("creator"));
            a.setRemarks(request.getParameter("remarks"));
            if (!StringUtils.isEmpty(request.getParameter("reasonCode"))) {
                a.setReasonCode(Integer.parseInt(request.getParameter("reasonCode")));
            }
            //the keyword(name) must match the demographic_no if it has been changed
            demo = null;
            if (request.getParameter("demographic_no") != null && !(request.getParameter("demographic_no").equals(""))) {
                DemographicMerged dmDAO = new DemographicMerged();
                a.setDemographicNo(Integer.parseInt(dmDAO.getHead(request.getParameter("demographic_no"))));

                DemographicData demData = new DemographicData();
                demo = demData.getDemographic(loggedInInfo, String.valueOf(a.getDemographicNo()));
                a.setName(demo.getLastName() + "," + demo.getFirstName());
            } else {
                a.setDemographicNo(0);
                a.setName(request.getParameter("keyword"));
            }

            a.setProgramId(Integer.parseInt((String) request.getSession().getAttribute("programId_oscarView")));
            a.setUrgency((request.getParameter("urgency") != null) ? request.getParameter("urgency") : "");

            appointmentDao.persist(a);
            int rowsAffected = 1;

            if (rowsAffected == 1) {

                //email patient appointment record
                if (request.getParameter("emailPt") != null) {

                    out.print("Appointment mailing is broken.");
                    /*
                     * The send method in AppointmentMailer is not supported.
                     * For more details, please refer to the AppointmentMailer class.
                     */
				/*
					try{
					
					Appointment aa =  appointmentDao.search_appt_no(request.getParameter("provider_no"), ConversionUtils.fromDateString(request.getParameter("appointment_date")), ConversionUtils.fromTimeStringNoSeconds(request.getParameter("start_time")),
									ConversionUtils.fromTimeStringNoSeconds(request.getParameter("end_time")), ConversionUtils.fromTimestampString(createDateTime), request.getParameter("creator"), Integer.parseInt(param[16]));
			
						if (aa != null) {
							Integer apptNo = aa.getId();
							DemographicManager demographicManager =  SpringUtils.getBean(DemographicManager.class);
							Demographic demographic = demographicManager.getDemographic(loggedInInfo,param[16]);

							if ((demographic != null) && (apptNo > 0)) {
								AppointmentMailer emailer = new AppointmentMailer(apptNo,demographic);
								emailer.prepareMessage();
								emailer.send();
							}
						}

					}catch(Exception e) {
						out.print(e.getMessage());
					}
				*/
                }


                // turn off reminder of "remove patient from the waiting list"
                OscarProperties pros = OscarProperties.getInstance();
                String strMWL = pros.getProperty("MANUALLY_CLEANUP_WL");
                if (strMWL != null && strMWL.equalsIgnoreCase("yes")) {
                    ;
                } else {
                    WaitingList wL = WaitingList.getInstance();
                    if (wL.getFound()) {
                        String demographicNo = request.getParameter("demographic_no");
                        if (demographicNo != null && !"".equals(demographicNo)) {

                            List<Object[]> wl = waitingListDao.findByDemographic(Integer.parseInt(demographicNo));
                            if (wl.size() > 0) {
                                WaitingListName wln = (WaitingListName) wl.get(0)[0];
                                ca.openosp.openo.commn.model.WaitingList wl1 = (ca.openosp.openo.commn.model.WaitingList) wl.get(0)[1];

        %>
        <form name="updateWLFrm"
              action="<%= request.getContextPath() %>/oscarWaitingList/RemoveFromWaitingList.jsp"><input
                type="hidden" name="listId"
                value="<%=wl1.getListId()%>"/><input
                type="hidden" name="demographicNo"
                value="<%=request.getParameter("demographic_no")%>"/>
            <script
                    LANGUAGE="JavaScript">
                var removeList = confirm("Click OK to remove patient from the waiting list: <%=wln.getName()%>");
                if (removeList) {
                    document.forms[0].action = "<%= request.getContextPath() %>/oscarWaitingList/RemoveFromWaitingList.jsp?demographicNo=<%=request.getParameter("demographic_no")%>&listID=<%=wl1.getListId()%>";
                    document.forms[0].submit();
                }
            </script>
        </form>
        <%
                        }
                    }
                }
            }
        %>
        <p>
        <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgAddSuccess"/></h1>

        <script LANGUAGE="JavaScript">
            <%
                int apptId=0;
                if(!(request.getParameter("printReceipt")==null) && request.getParameter("printReceipt").equals("1")) {
                    Appointment aa =  appointmentDao.search_appt_no(request.getParameter("provider_no"), ConversionUtils.fromDateString(request.getParameter("appointment_date")), ConversionUtils.fromTimeStringNoSeconds(request.getParameter("start_time")),
                         ConversionUtils.fromTimeStringNoSeconds(request.getParameter("end_time")),ConversionUtils.fromTimestampString(createDateTime),  request.getParameter("creator"), Integer.parseInt(param[16]));
                    if (aa != null) {
                        apptId = aa.getId();
                    }%>
            popupPage(350, 750, 'printappointment.jsp?appointment_no=<%=apptId%>');
            <%}%>
            self.opener.refresh();
            self.close();
        </script>

        <%
            Appointment aa = appointmentDao.search_appt_no(request.getParameter("provider_no"), ConversionUtils.fromDateString(request.getParameter("appointment_date")), ConversionUtils.fromTimeStringNoSeconds(request.getParameter("start_time")),
                    ConversionUtils.fromTimeStringNoSeconds(request.getParameter("end_time")), ConversionUtils.fromTimestampString(createDateTime), request.getParameter("creator"), Integer.parseInt(param[16]));


            if (aa != null) {
                Integer apptNo = aa.getId();
                String mcNumber = request.getParameter("appt_mc_number");
                OtherIdManager.saveIdAppointment(apptNo, "appt_mc_number", mcNumber);

                EventService eventService = SpringUtils.getBean(EventService.class);
                eventService.appointmentCreated(this, apptNo.toString(), param[0]); // called when adding an appointment

            }

        } else {
        %>
        <p>
        <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="appointment.addappointment.msgAddFailure"/></h1>

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
