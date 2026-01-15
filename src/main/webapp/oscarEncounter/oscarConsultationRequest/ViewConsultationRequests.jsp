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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_con");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="ca.openosp.openo.commn.dao.ConsultationRequestDao" %>

<%@ page import="ca.openosp.openo.encounter.pageUtil.*,java.text.*,java.util.*" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page
        import="ca.openosp.openo.commn.dao.UserPropertyDAO, ca.openosp.openo.commn.model.UserProperty, org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>

<%@ page import="ca.openosp.openo.commn.model.Site" %>
<%@ page import="ca.openosp.openo.commn.dao.SiteDao" %>

<%@ page import="ca.openosp.openo.commn.model.ProviderData" %>
<%@ page import="ca.openosp.openo.commn.dao.ProviderDataDao" %>

<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequestUtil" %>
<%@ page import="ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctViewConsultationRequestsUtil" %>
<%@ page import="ca.openosp.openo.commn.IsPropertiesOn" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    String curProvider_no = (String) session.getAttribute("user");

    boolean isSiteAccessPrivacy = false;
    boolean isTeamAccessPrivacy = false;
    boolean bMultisites = IsPropertiesOn.isMultisitesEnable();
    List<String> mgrSite = new ArrayList<String>();

    ProviderDataDao providerDataDao = SpringUtils.getBean(ProviderDataDao.class);

    String strLimit = request.getParameter("limit");
    String strOffset = request.getParameter("offset");

    Integer limit = ConsultationRequestDao.DEFAULT_CONSULT_REQUEST_RESULTS_LIMIT;
    Integer offset = 0;

    try {
        offset = Integer.parseInt(strOffset);
    } catch (NumberFormatException e) {
        offset = 0;
    }

    try {
        limit = Integer.parseInt(strLimit);
    } catch (NumberFormatException e) {
        limit = 100;
    }
%>
<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r"
                   reverse="false"><%isSiteAccessPrivacy = true; %></security:oscarSec>
<security:oscarSec objectName="_team_access_privacy" roleName="<%=roleName$%>" rights="r"
                   reverse="false"><%isTeamAccessPrivacy = true; %></security:oscarSec>

<%
    List<ProviderData> pdList = null;
    HashMap<String, String> providerMap = new HashMap<String, String>();

//multisites function
    if (isSiteAccessPrivacy || isTeamAccessPrivacy) {

        if (isSiteAccessPrivacy)
            pdList = providerDataDao.findByProviderSite(curProvider_no);

        if (isTeamAccessPrivacy)
            pdList = providerDataDao.findByProviderTeam(curProvider_no);

        for (ProviderData providerData : pdList) {
            providerMap.put(providerData.getId(), "true");
        }
    }
%>

<%
    //multi-site office , save all bgcolor to Hashmap
    HashMap<String, String> siteBgColor = new HashMap<String, String>();
    HashMap<String, String> siteShortName = new HashMap<String, String>();
    if (bMultisites) {
        SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);

        List<Site> sites = siteDao.getAllSites();
        for (Site st : sites) {
            siteBgColor.put(st.getName(), st.getBgColor());
            siteShortName.put(st.getName(), st.getShortName());
        }
        List<Site> providerSites = siteDao.getActiveSitesByProviderNo(curProvider_no);
        for (Site st : providerSites) {
            mgrSite.add(st.getName());
        }
    }
%>

<html>

    <%

        String team = (String) request.getAttribute("teamVar");
        if (team == null) {
            team = new String();
        }

        Boolean includeBool = (Boolean) request.getAttribute("includeCompleted");
        boolean includeCompleted = false;
        if (includeBool != null) {
            includeCompleted = "on".equals(request.getParameter("includeCompleted"));
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        // Getting startDate attribute of the consultation request and ensuring that it is of type "Date" before casting
        Object startDateObj = request.getAttribute("startDate");
        Date startDate = null;
        String formattedStartDate = "";
        if (startDateObj instanceof Date) {
            startDate = (Date) startDateObj;
            formattedStartDate = sdf.format(startDateObj);
        }

        // Getting endDate attribute of the consultation request and ensuring that it is of type "Date" before casting
        Object endDateObj = request.getAttribute("endDate");
        Date endDate = null;
        String formattedEndDate = "";
        if (endDateObj instanceof Date) {
            endDate = (Date) endDateObj;
            formattedEndDate = sdf.format(endDateObj);
        }

        // Getting orderby, description, and searchDate attributes of the consultation request
        String orderby = (String) request.getAttribute("orderby");
        String desc = (String) request.getAttribute("desc");
        String searchDate = (String) request.getAttribute("searchDate");

        // Setting defaults to match consultation request in struts 1
        if (searchDate == null) {
            searchDate = "0";
        }

        EctConsultationFormRequestUtil consultUtil;
        consultUtil = new EctConsultationFormRequestUtil();

        if (isTeamAccessPrivacy) {
            consultUtil.estTeamsByTeam(curProvider_no);
        } else if (isSiteAccessPrivacy) {
            consultUtil.estTeamsBySite(curProvider_no);
        } else {
            consultUtil.estTeams();
        }


        ArrayList tickerList = new ArrayList();
    %>


    <head>
        <title>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="ectViewConsultationRequests.title"/>
        </title>


        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/calendar/calendar.css"
              title="win2k-cold-1"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
        <!--META HTTP-EQUIV="Refresh" CONTENT="20;"-->

        <style type="text/css">
            td.stat1 {
                background-color: #eeeeFF;
            }

            th, td.stat2 {
                background-color: #ccccFF;
            }

            td.stat3 {
                background-color: #B8B8FF;
            }

            td.stat4 {
                background-color: #eeeeff;
            }
td.stat5 {
background-color:rgb(212, 212, 254);
}

            th.VCRheads {
                background-color: #ddddff;
                color: black;
            }

        </style>


    </head>
    <script language="javascript">
        function BackToOscar() {
            window.close();
        }

        ///
        function popupOscarRx(vheight, vwidth, varpage) { //open a new popup window
            var page = varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
            var popup = window.open(varpage, "<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgConsReq"/>", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
            }
//setTimeout("window.location.reload();",5000);
        }

        function popupOscarConsultationConfig(vheight, vwidth, varpage) { //open a new popup window
            var page = varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
            var popup = window.open(varpage, "<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgConsConfig"/>", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
            }
        }

        function setOrder(val) {
            if (document.forms[0].orderby.value == val) {
                //alert( document.forms[0].desc.value);
                if (document.forms[0].desc.value == '1') {
                    document.forms[0].desc.value = '0';
                } else {
                    document.forms[0].desc.value = '1';
                }
            } else {
                document.forms[0].orderby.value = val;
                document.forms[0].desc.value = '0';
            }
            document.forms[0].submit();
        }

        function gotoPage(next) {
            var frm = document.forms[0];

            frm.limit.value = <%=limit%>;
            if (next) frm.offset.value = <%=offset+limit%>;
            else frm.offset.value = <%=offset-limit%>;

            frm.submit();
        }
    </script>


    <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">
    <body class="BodyStyle" vlink="#0000FF">
    <!--  -->
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn">
                Consultation
            </td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td class="Header" NOWRAP>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msfConsReqForTeam"/>
                            =
                            <%
                                if (team.equals("-1")) {
                            %>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.formTeamNotApplicable"/>
                            <% } else if (team.isEmpty()) { %>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.formViewAll"/>
                            <% } else { %>
                            <%= team %>
                            <% } %>
                        </td>
                        <td>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr style="vertical-align:top">
            <td class="MainTableLeftColumn">
                <table>
                    <tr>
                        <td NOWRAP>
                            <a href="javascript:popupOscarConsultationConfig(700,960,'<%=request.getContextPath()%>/oscarEncounter/oscarConsultationRequest/config/ShowAllServices.jsp')"
                               class="consultButtonsActive">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgEditSpecialists"/>
                            </a>
                        </td>
                    </tr>
                </table>
            </td>
            <td class="MainTableRightColumn">
                <table width="100%">
                    <tr>
                        <td style="margin: 0; padding: 0;">
                            <form action="${pageContext.request.contextPath}/oscarEncounter/ViewConsultation.do" method="get">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.formSelectTeam"/>:
                                <select name="sendTo">
                                    <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.formViewAll"/></option>
                                    <%
                                        if (team.equals("-1")) { %>
                                    <option value="-1" selected><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.formTeamNotApplicable"/></option>
                                    <% } else {
                                    %>
                                    <option value="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.formTeamNotApplicable"/></option>
                                    <% }
                                        for (int i = 0; i < consultUtil.teamVec.size(); i++) {
                                            String te = (String) consultUtil.teamVec.get(i);
                                            if (te.equals(team)) {
                                    %>
                                    <option value="<%=te%>" selected><%=te%>
                                    </option>
                                    <%} else {%>
                                    <option value="<%=te%>"><%=te%>
                                    </option>
                                    <%
                                            }
                                        }
                                    %>
                                </select>
                                <input type="submit"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.btnConsReq"/>"/>
                                <div style="margin: 0; padding: 0; ">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgStart"/>:
                                    <input type="text" name="startDate" size="8" id="startDate" value="<%= formattedStartDate %>" /><a id="SCal"><img
                                        title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgEnd"/>:
                                    <input type="text" name="endDate" size="8" id="endDate" value="<%= formattedEndDate %>" /><a id="ECal"><img
                                        title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgIncludeCompleted"/>:
                                    <input type="checkbox" name="includeCompleted" <%= includeCompleted ? "checked" : "" %> />
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgSearchon"/>
                                    <input type="radio" name="searchDate" value="0" titleKey="Search on Referal Date"
                                        <%= "0".equals(searchDate) ? "checked" : "" %> />
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgApptDate"/>
                                    <input type="radio" name="searchDate" value="1" titleKey="Search on Appt. Date"
                                        <%= "1".equals(searchDate) ? "checked" : "" %> />
                                    <input type="hidden" name="currentTeam" id="currentTeam"/>
                                    <input type="hidden" name="orderby" id="orderby"/>
                                    <input type="hidden" name="desc" id="desc"/>
                                    <input type="hidden" name="offset" id="offset" value="<%= offset %>"/>
                                    <input type="hidden" name="limit" id="limit" value="<%= limit %>"/>
                                </div>
                            </form>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <table border="0" width="90%" cellspacing="1" style="border: thin solid #C0C0C0;">
                                <tr>
                                    <th align="left" class="VCRheads" width="10%">
                                        <a href=# onclick="setOrder('1'); return false;">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgStatus"/>
                                        </a>
                                    </th>
                                    <th align="left" class="VCRheads" width="10%">
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgUrgency"/>
                                    </th>
                                    <th align="left" class="VCRheads">
                                        <a href=# onclick="setOrder('2'); return false;">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgTeam"/>
                                        </a>
                                    </th>
                                    <th align="left" class="VCRheads" width="75">
                                        <a href=# onclick="setOrder('3'); return false;">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgPatient"/>
                                        </a>
                                    </th>
                                    <th align="left" class="VCRheads">
                                        <a href=# onclick="setOrder('4'); return false;">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgProvider"/>
                                        </a>
                                    </th>
                                    <th align="left" class="VCRheads">
                                        <a href=# onclick="setOrder('5'); return false;">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgService"/>
                                        </a>
                                    </th>
                                    <th align="left" class="VCRheads">
                                        <a href=# onclick="setOrder('6'); return false;">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgConsultant"/>
                                        </a>
                                    </th>
                                    <th align="left" class="VCRheads">
                                        <a href=# onclick="setOrder('7'); return false;">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgRefDate"/>
                                        </a>
                                    </th>
                                    <th align="left" class="VCRheads">
                                        <a href=# onclick="setOrder('8'); return false;">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgAppointmentDate"/>
                                        </a>
                                    </th>
                                    <th align="left" class="VCRheads">
                                        <a href=# onclick="setOrder('9'); return false;">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgFollowUpDate"/>
                                        </a>
                                    </th>
                                    <% if (bMultisites) { %>
                                    <th align="left" class="VCRheads">
                                        <a href=# onclick="setOrder('10'); return false;">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgSiteName"/>
                                        </a>
                                    </th>
                                    <%} %>
                                </tr>
                                <%
                                    EctViewConsultationRequestsUtil theRequests;
                                    theRequests = new EctViewConsultationRequestsUtil();
                                    theRequests.estConsultationVecByTeam(LoggedInInfo.getLoggedInInfoFromSession(request), team, includeCompleted, startDate, endDate, orderby, desc, searchDate, offset, limit);
                                    boolean overdue;
                                    UserPropertyDAO pref = (UserPropertyDAO) WebApplicationContextUtils.getWebApplicationContext(pageContext.getServletContext()).getBean(UserPropertyDAO.class);
                                    String user = (String) session.getAttribute("user");
                                    UserProperty up = pref.getProp(user, UserProperty.CONSULTATION_TIME_PERIOD_WARNING);
                                    String timeperiod = null;
                                    int countback;

                                    if (up != null && up.getValue() != null && !up.getValue().trim().equals("")) {
                                        timeperiod = up.getValue();
                                    }

                                    for (int i = 0; i < theRequests.ids.size(); i++) {
                                        //multisites. skip record if not belong to same site/team
                                        if (isSiteAccessPrivacy || isTeamAccessPrivacy) {
                                            if (providerMap.get(theRequests.providerNo.get(i)) == null) continue;
                                        }

                                        String id = theRequests.ids.get(i);
                                        String status = theRequests.status.get(i);
                                        String patient = theRequests.patient.get(i);
                                        String provide = theRequests.provider.get(i);
                                        String service = theRequests.service.get(i);
                                        boolean eReferral = theRequests.eReferral.get(i);
                                        String date = theRequests.date.get(i);
                                        String demo = theRequests.demographicNo.get(i);
                                        String appt = theRequests.apptDate.get(i);
                                        String patBook = theRequests.patientWillBook.get(i);
                                        String urgency = theRequests.urgency.get(i);
                                        String sendTo = theRequests.teams.get(i);
                                        if (sendTo == null) sendTo = "-1";
                                        String specialist = theRequests.vSpecialist.get(i);
                                        String followUpDate = theRequests.followUpDate.get(i);
                                        String siteName = "";
                                        if (bMultisites) {
                                            siteName = theRequests.siteName.get(i);
                                        }
                                        if (status.equals("1") && dateGreaterThan(date, Calendar.WEEK_OF_YEAR, -1)) {
                                            tickerList.add(demo);
                                        }

                                        //multisites. skip record if not belong to same site
                                        if (isSiteAccessPrivacy || isTeamAccessPrivacy) {
                                            if (!mgrSite.contains(siteName)) continue;
                                        }
                                        overdue = false;

                                        if (timeperiod != null) {
                                            countback = Integer.parseInt(timeperiod);
                                            countback = countback * -1;


                                            if ((status.equals("1") || status.equals("2") || status.equals("3")) && dateGreaterThan(date, Calendar.MONTH, countback)) {
                                                overdue = true;
                                            }
                                        } else {
                                            countback = -7;  //7 days
                                            if ((status.equals("1") || status.equals("3")) && dateGreaterThan(date, Calendar.DAY_OF_YEAR, countback)) {
                                                overdue = true;
                                            }

                                            countback = -30;  //30 days
                                            if (status.equals("2") && dateGreaterThan(date, Calendar.DAY_OF_YEAR, countback)) {
                                                overdue = true;
                                            }
                                        }


                                %>
                                <tr <%=overdue ? "style='color:red;'" : ""%>>
                                    <td class="stat<%=status%>">
                                        <% if (status.equals("1")) { %>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgND"/>
                                        <% } else if (status.equals("2")) { %>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgSR"/>
                                        <% } else if (status.equals("3")) { %>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgPR"/>
                                        <% } else if (status.equals("4")) { %>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgDONE"/>
                                        <% }else if(status.equals("5")) { %>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgBC"/>
                                        <%}%>
                                    </td>
                                    <td class="stat<%=status%>">
                                        <% if (urgency.equals("1")) { %>
                                        <div style="color:red;"> Urgent</div>
                                        <% } else if (urgency.equals("2")) { %>
                                        Non-Urgent
                                        <% } else if (urgency.equals("3")) { %>
                                        Return
                                        <% } %>


                                    </td>
                                    <td class="stat<%=status%>">
                                        <a href="javascript:popupOscarRx(700,960,'<%=request.getContextPath()%>/oscarEncounter/ViewRequest.do?requestId=<%=id%>')">
                                            <%=sendTo.equals("-1") ? "N/A" : sendTo%>
                                        </a>
                                    </td>
                                    <td class="stat<%=status%>">
                                        <a href="javascript:popupOscarRx(700,960,'<%=request.getContextPath()%>/oscarEncounter/ViewRequest.do?requestId=<%=id%>')">
                                            <%=patient%>
                                        </a>
                                    </td>
                                    <td class="stat<%=status%>">
                                        <%=provide%>
                                    </td>
                                    <td class="stat<%=status%>">
                                        <a href="javascript:popupOscarRx(700,960,'<%=request.getContextPath()%>/oscarEncounter/ViewRequest.do?requestId=<%=id%>')">
                                            <%=service%>
                                        </a>

                                    </td>
                                    <td class="stat<%=status%>">
                                        <a href="javascript:popupOscarRx(700,960,'<%=request.getContextPath()%>/oscarEncounter/ViewRequest.do?requestId=<%=id%>')">
                                            <%=specialist%>
                                        </a>
                                    <% if (eReferral) { %>
                                    <span>(via OCEAN)</span>
                                    <%} %>
                                    </td>
                                    <td class="stat<%=status%>">
                                        <%=date%>
                                    </td>
                                    <td class="stat<%=status%>">
                                        <% if (patBook != null && patBook.trim().equals("1")) {%>
                                        Patient will book
                                        <%} else {%>
                                        <%=appt%>
                                        <%}%>
                                    </td>
                                    <td class="stat<%=status%>">
                                        <a href="javascript:popupOscarRx(700,960,'<%=request.getContextPath()%>/oscarEncounter/ViewRequest.do?requestId=<%=id%>')">
                                            <%=followUpDate%>
                                        </a>

                                    </td>
                                    <% if (bMultisites) { %>
                                    <td bgcolor="<%=(siteBgColor.get(siteName)==null || siteBgColor.get(siteName).length()== 0 ? "#FFFFFF" : siteBgColor.get(siteName))%>">
                                        <%=siteShortName.get(siteName)%>
                                    </td>
                                    <%} %>
                                </tr>
                                <%}%>
                            </table>

                        </td>
                    </tr>
                </table>


                <%
                    if (offset > 0) {
//                		String queryString = getNewQueryString(request.getQueryString(),offset-limit,limit);
                %><input type="button" value="Prev" onClick="gotoPage(false);"/><%
                }
                if (theRequests.ids.size() == limit) {
//                		String queryString = getNewQueryString(request.getQueryString(),offset+limit,limit);
            %><input type="button" value="Next" onClick="gotoPage(true);"/><%
                }
            %>

            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn">

            </td>
            <td class="MainTableBottomRowRightColumn">
                <% if (tickerList.size() > 0) {
                    String queryStr = "";
                    for (int i = 0; i < tickerList.size(); i++) {
                        String demo = (String) tickerList.get(i);
                        if (i == 0) {
                            queryStr += "demo=" + demo;
                        } else {
                            queryStr += "&demo=" + demo;
                        }
                    }%>
                <a target="_blank"
                   href="<%= request.getContextPath() %>/tickler/AddTickler.do?<%=queryStr%>&message=<%=java.net.URLEncoder.encode("Patient has Consultation Letter with a status of 'Nothing Done' for over one week","UTF-8")%>">Add
                    Tickler for Consults with ND for more than one week</a>
                <%}%>
            </td>
        </tr>
    </table>
    <script language='javascript'>
        Calendar.setup({
            inputField: "startDate",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "SCal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "endDate",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "ECal",
            singleClick: true,
            step: 1
        });
    </script>
    </body>

</html>
<%!
    /*
    String getNewQueryString(String queryString,Integer offset, Integer limit) {

        String result = "";
        List<String> resultParts = new ArrayList<String>();

        String[] parts = queryString.split("&");
        for(String part:parts) {

            if(!part.startsWith("offset=") && !part.startsWith("limit=")) {
                resultParts.add(part);
            }
        }

        resultParts.add("offset=" + (offset!=null?offset:0));
        resultParts.add("limit=" + (limit != null?limit:ConsultationRequestDao.DEFAULT_CONSULT_REQUEST_RESULTS_LIMIT));
        for(int x=0;x<resultParts.size();x++) {
            if(x>0)
                result += "&";
            result += resultParts.get(x);
        }

        return result;
    }
    */

    boolean dateGreaterThan(String dateStr, int unit, int period) {
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date prevDate = null;
        try {
            prevDate = formatter.parse(dateStr);
        } catch (Exception e) {
            return false;
        }

        Calendar bonusEl = Calendar.getInstance();
        bonusEl.add(unit, period);
        Date bonusStartDate = bonusEl.getTime();

        return bonusStartDate.after(prevDate);
    }

%>
