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

<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao" %>

<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.*,java.text.*,java.util.*" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page
        import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO, io.github.carlos_emr.carlos.commn.model.UserProperty, org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>

<%@ page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>

<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProviderDataDao" %>

<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequestUtil" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctViewConsultationRequestsUtil" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>
<%@ page import="org.owasp.encoder.Encode" %>

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
        <%@ include file="/includes/global-head.jspf" %>
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

        <style type="text/css">
            .stat1 { background-color: #eeeeFF; }
            .stat2 { background-color: #ccccFF; }
            .stat3 { background-color: #B8B8FF; }
            .stat4 { background-color: #eeeeff; }
            .stat5 { background-color: rgb(212, 212, 254); }
        </style>

        <script type="text/javascript">
            function BackToOscar() {
                window.close();
            }

            function popupOscarRx(vheight, vwidth, varpage) {
                var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                var popup = window.open(varpage, "<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgConsReq"/>", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                }
            }

            function popupOscarConsultationConfig(vheight, vwidth, varpage) {
                var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                var popup = window.open(varpage, "<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgConsConfig"/>", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                }
            }

            function setOrder(val) {
                if (document.forms[0].orderby.value == val) {
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
    </head>

    <body>
    <div class="container">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" class="page-header-icon">
                    <path d="M14 1a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H4.414A2 2 0 0 0 3 11.586l-2 2V2a1 1 0 0 1 1-1zM2 0a2 2 0 0 0-2 2v12.793a.5.5 0 0 0 .854.353l2.853-2.853A1 1 0 0 1 4.414 12H14a2 2 0 0 0 2-2V2a2 2 0 0 0-2-2z"/>
                    <path d="M3 3.5a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9a.5.5 0 0 1-.5-.5M3 6a.5.5 0 0 1 .5-.5h9a.5.5 0 0 1 0 1h-9A.5.5 0 0 1 3 6m0 2.5a.5.5 0 0 1 .5-.5h5a.5.5 0 0 1 0 1h-5a.5.5 0 0 1-.5-.5"/>
                </svg>
                &nbsp;Consultation Requests
                <span class="text-muted" style="font-size: 0.8em; font-weight: normal;">
                    &mdash; Team:
                    <%
                        if (team.equals("-1")) {
                    %>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.formTeamNotApplicable"/>
                    <% } else if (team.isEmpty()) { %>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.formViewAll"/>
                    <% } else { %>
                    <%= Encode.forHtml(team) %>
                    <% } %>
                </span>
            </h4>
        </div>

        <form action="${pageContext.request.contextPath}/oscarEncounter/ViewConsultation.do" method="get">
            <div class="d-flex flex-wrap align-items-end gap-3 mb-3">
                <div>
                    <label for="sendTo"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.formSelectTeam"/></label>
                    <select name="sendTo" class="form-select form-select-sm">
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
                        <option value="<%=Encode.forHtmlAttribute(te)%>" selected><%=Encode.forHtml(te)%></option>
                        <%} else {%>
                        <option value="<%=Encode.forHtmlAttribute(te)%>"><%=Encode.forHtml(te)%></option>
                        <%
                                }
                            }
                        %>
                    </select>
                </div>
                <div>
                    <label for="startDate"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgStart"/></label>
                    <div class="input-group input-group-sm">
                        <input type="text" name="startDate" class="form-control" size="8" id="startDate" value="<%= formattedStartDate %>" />
                        <a id="SCal" class="input-group-text" style="cursor:pointer;"><img title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
                    </div>
                </div>
                <div>
                    <label for="endDate"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgEnd"/></label>
                    <div class="input-group input-group-sm">
                        <input type="text" name="endDate" class="form-control" size="8" id="endDate" value="<%= formattedEndDate %>" />
                        <a id="ECal" class="input-group-text" style="cursor:pointer;"><img title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
                    </div>
                </div>
                <div>
                    <div class="form-check">
                        <input type="checkbox" name="includeCompleted" class="form-check-input" id="includeCompleted" <%= includeCompleted ? "checked" : "" %> />
                        <label class="form-check-label" for="includeCompleted"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgIncludeCompleted"/></label>
                    </div>
                </div>
                <div>
                    <label><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgSearchon"/></label>
                    <div>
                        <div class="form-check form-check-inline">
                            <input type="radio" name="searchDate" value="0" class="form-check-input" id="searchDateRef" <%= "0".equals(searchDate) ? "checked" : "" %> />
                            <label class="form-check-label" for="searchDateRef">Referral Date</label>
                        </div>
                        <div class="form-check form-check-inline">
                            <input type="radio" name="searchDate" value="1" class="form-check-input" id="searchDateAppt" <%= "1".equals(searchDate) ? "checked" : "" %> />
                            <label class="form-check-label" for="searchDateAppt"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgApptDate"/></label>
                        </div>
                    </div>
                </div>
                <div class="d-flex align-items-end gap-2">
                    <input type="submit" class="btn btn-primary btn-sm"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.btnConsReq"/>"/>
                    <a href="javascript:popupOscarConsultationConfig(700,960,'<%=request.getContextPath()%>/oscarEncounter/oscarConsultationRequest/config/ShowAllServices.jsp')"
                       class="btn btn-secondary btn-sm">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgEditSpecialists"/>
                    </a>
                </div>
                <input type="hidden" name="currentTeam" id="currentTeam" value="<%= Encode.forHtmlAttribute(team != null ? team : "") %>"/>
                <input type="hidden" name="orderby" id="orderby" value="<%= Encode.forHtmlAttribute(orderby != null ? orderby : "") %>"/>
                <input type="hidden" name="desc" id="desc" value="<%= Encode.forHtmlAttribute(desc != null ? desc : "") %>"/>
                <input type="hidden" name="offset" id="offset" value="<%= Encode.forHtmlAttribute(String.valueOf(offset)) %>"/>
                <input type="hidden" name="limit" id="limit" value="<%= Encode.forHtmlAttribute(String.valueOf(limit)) %>"/>
            </div>
        </form>

        <table class="table table-hover table-sm table-striped">
            <thead>
                <tr>
                    <th><a href="#" onclick="setOrder('1'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgStatus"/></a></th>
                    <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgUrgency"/></th>
                    <th><a href="#" onclick="setOrder('2'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgTeam"/></a></th>
                    <th><a href="#" onclick="setOrder('3'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgPatient"/></a></th>
                    <th><a href="#" onclick="setOrder('4'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgProvider"/></a></th>
                    <th><a href="#" onclick="setOrder('5'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgService"/></a></th>
                    <th><a href="#" onclick="setOrder('6'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgConsultant"/></a></th>
                    <th><a href="#" onclick="setOrder('7'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgRefDate"/></a></th>
                    <th><a href="#" onclick="setOrder('8'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgAppointmentDate"/></a></th>
                    <th><a href="#" onclick="setOrder('9'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgFollowUpDate"/></a></th>
                    <% if (bMultisites) { %>
                    <th><a href="#" onclick="setOrder('10'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgSiteName"/></a></th>
                    <%} %>
                </tr>
            </thead>
            <tbody>
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
                <tr <%=overdue ? "class='text-danger'" : ""%>>
                    <td>
                        <% if (status.equals("1")) { %>
                        <span class="badge bg-warning text-dark"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgND"/></span>
                        <% } else if (status.equals("2")) { %>
                        <span class="badge bg-info"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgSR"/></span>
                        <% } else if (status.equals("3")) { %>
                        <span class="badge bg-primary"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgPR"/></span>
                        <% } else if (status.equals("4")) { %>
                        <span class="badge bg-success"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgDONE"/></span>
                        <% } else if (status.equals("5")) { %>
                        <span class="badge bg-secondary"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgBC"/></span>
                        <%}%>
                    </td>
                    <td>
                        <% if (urgency.equals("1")) { %>
                        <span class="text-danger fw-bold">Urgent</span>
                        <% } else if (urgency.equals("2")) { %>
                        Non-Urgent
                        <% } else if (urgency.equals("3")) { %>
                        Return
                        <% } %>
                    </td>
                    <td>
                        <a href="javascript:popupOscarRx(700,960,'<%=request.getContextPath()%>/oscarEncounter/ViewRequest.do?requestId=<%=Encode.forUriComponent(id)%>')">
                            <%=sendTo.equals("-1") ? "N/A" : Encode.forHtml(sendTo)%>
                        </a>
                    </td>
                    <td>
                        <a href="javascript:popupOscarRx(700,960,'<%=request.getContextPath()%>/oscarEncounter/ViewRequest.do?requestId=<%=Encode.forUriComponent(id)%>')">
                            <%=Encode.forHtml(patient)%>
                        </a>
                    </td>
                    <td><%=Encode.forHtml(provide)%></td>
                    <td>
                        <a href="javascript:popupOscarRx(700,960,'<%=request.getContextPath()%>/oscarEncounter/ViewRequest.do?requestId=<%=Encode.forUriComponent(id)%>')">
                            <%=Encode.forHtml(service)%>
                        </a>
                    </td>
                    <td>
                        <a href="javascript:popupOscarRx(700,960,'<%=request.getContextPath()%>/oscarEncounter/ViewRequest.do?requestId=<%=Encode.forUriComponent(id)%>')">
                            <%=Encode.forHtml(specialist)%>
                        </a>
                        <% if (eReferral) { %>
                        <span class="text-muted">(via OCEAN)</span>
                        <%} %>
                    </td>
                    <td><%=Encode.forHtml(date)%></td>
                    <td>
                        <% if (patBook != null && patBook.trim().equals("1")) {%>
                        <em>Patient will book</em>
                        <%} else {%>
                        <%=Encode.forHtml(appt)%>
                        <%}%>
                    </td>
                    <td>
                        <a href="javascript:popupOscarRx(700,960,'<%=request.getContextPath()%>/oscarEncounter/ViewRequest.do?requestId=<%=Encode.forUriComponent(id)%>')">
                            <%=Encode.forHtml(followUpDate)%>
                        </a>
                    </td>
                    <% if (bMultisites) { %>
                    <td style="background-color: <%=Encode.forHtmlAttribute(siteBgColor.get(siteName)==null || siteBgColor.get(siteName).length()== 0 ? "#FFFFFF" : siteBgColor.get(siteName))%>">
                        <%=Encode.forHtml(siteShortName.get(siteName))%>
                    </td>
                    <%} %>
                </tr>
                <%}%>
            </tbody>
        </table>

        <div class="d-flex align-items-center gap-2 mb-3">
            <%
                if (offset > 0) {
            %><input type="button" class="btn btn-sm btn-secondary" value="Prev" onClick="gotoPage(false);"/><%
                }
                if (theRequests.ids.size() == limit) {
            %><input type="button" class="btn btn-sm btn-secondary" value="Next" onClick="gotoPage(true);"/><%
                }
            %>
        </div>

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
        <div class="mb-3">
            <a target="_blank"
               href="<%= request.getContextPath() %>/tickler/AddTickler.do?<%=queryStr%>&message=<%=java.net.URLEncoder.encode("Patient has Consultation Letter with a status of 'Nothing Done' for over one week","UTF-8")%>">
                Add Tickler for Consults with ND for more than one week
            </a>
        </div>
        <%}%>
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
    </div>
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
