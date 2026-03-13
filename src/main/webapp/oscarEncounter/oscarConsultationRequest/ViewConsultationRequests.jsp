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
<%--
    ViewConsultationRequests.jsp

    Purpose: Displays a paginated, filterable list of consultation requests for a
    provider or team, supporting date range search, team filtering, and completion
    status toggling.

    Features:
    - Bootstrap 5 responsive table with sortable columns
    - Filter by team, date range (referral or appointment date), and completion status
    - Clickable rows (mouse + keyboard) to open consultation detail popup
    - Overdue consultation highlighting based on user preferences
    - Bulk tickler creation for "Nothing Done" consultations older than one week
    - Multisite support with site-specific background colours
    - Native HTML5 date inputs replacing legacy calendar widget
    - OWASP-encoded URLs for all popup interactions

    @since CARLOS EMR 1.0 (modernized 2026 from legacy OSCAR layout)
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


        List<String> tickerList = new ArrayList<String>();
    %>


    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="ectViewConsultationRequests.title"/>
        </title>

        <style>
            .consult-table th a {
                color: var(--carlos-text);
                text-decoration: none;
            }
            .consult-table th a:hover {
                text-decoration: underline;
            }
            .consult-row-overdue {
                color: red;
            }
            .consult-status-1 { background-color: #eeeeFF; }
            .consult-status-2 { background-color: #ccccFF; }
            .consult-status-3 { background-color: #B8B8FF; }
            .consult-status-4 { background-color: #eeeeff; }
            .consult-status-5 { background-color: rgb(212, 212, 254); }
            .urgency-urgent { color: red; font-weight: bold; }
            .filter-bar {
                background: var(--carlos-bg-light);
                border: 1px solid var(--carlos-border);
                border-radius: 4px;
                padding: 10px 15px;
                margin-bottom: 12px;
            }
            .filter-bar .form-check-input:checked {
                background-color: var(--carlos-primary);
                border-color: var(--carlos-primary);
            }
            .consult-table tbody tr {
                cursor: pointer;
            }
            .consult-table tbody tr:hover td {
                filter: brightness(0.95);
            }
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
    <div class="container-fluid p-0">

        <!-- Page Header -->
        <div class="page-header-bar d-flex justify-content-between align-items-center">
            <h4 class="page-header-title">
                <i class="fas fa-clipboard-list me-2"></i>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="ectViewConsultationRequests.title"/>
            </h4>
            <div>
                <a href="javascript:popupOscarConsultationConfig(700,960,'<%=request.getContextPath()%>/oscarEncounter/oscarConsultationRequest/config/ShowAllServices.jsp')"
                   class="btn btn-secondary btn-sm">
                    <i class="fas fa-cog me-1"></i>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgEditSpecialists"/>
                </a>
                <button type="button" class="btn btn-secondary btn-sm ms-1" onclick="window.close();">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnBack"/>
                </button>
            </div>
        </div>

        <div class="px-3">

            <!-- Filter Bar -->
            <form action="${pageContext.request.contextPath}/oscarEncounter/ViewConsultation.do" method="get">
                <div class="filter-bar">
                    <div class="row g-2 align-items-end">
                        <div class="col-auto">
                            <label class="form-label mb-0 small fw-bold">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.formSelectTeam"/>
                            </label>
                            <select name="sendTo" class="form-select form-select-sm">
                                <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.formViewAll"/></option>
                                <%
                                    if (team.equals("-1")) { %>
                                <option value="-1" selected><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.formTeamNotApplicable"/></option>
                                <% } else { %>
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
                        <div class="col-auto">
                            <label class="form-label mb-0 small fw-bold">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgStart"/>
                            </label>
                            <input type="date" name="startDate" id="startDate" class="form-control form-control-sm"
                                   value="<%= Encode.forHtmlAttribute(formattedStartDate) %>" />
                        </div>
                        <div class="col-auto">
                            <label class="form-label mb-0 small fw-bold">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgEnd"/>
                            </label>
                            <input type="date" name="endDate" id="endDate" class="form-control form-control-sm"
                                   value="<%= Encode.forHtmlAttribute(formattedEndDate) %>" />
                        </div>
                        <div class="col-auto">
                            <div class="form-check mt-2">
                                <input type="checkbox" name="includeCompleted" id="includeCompleted" class="form-check-input"
                                    <%= includeCompleted ? "checked" : "" %> />
                                <label class="form-check-label small" for="includeCompleted">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgIncludeCompleted"/>
                                </label>
                            </div>
                        </div>
                        <div class="col-auto">
                            <label class="form-label mb-0 small fw-bold">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgSearchon"/>
                            </label>
                            <div>
                                <div class="form-check form-check-inline">
                                    <input type="radio" name="searchDate" value="0" id="searchDateRef" class="form-check-input"
                                        <%= "0".equals(searchDate) ? "checked" : "" %> />
                                    <label class="form-check-label small" for="searchDateRef"><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgRefDate"/></label>
                                </div>
                                <div class="form-check form-check-inline">
                                    <input type="radio" name="searchDate" value="1" id="searchDateAppt" class="form-check-input"
                                        <%= "1".equals(searchDate) ? "checked" : "" %> />
                                    <label class="form-check-label small" for="searchDateAppt"><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgApptDate"/></label>
                                </div>
                            </div>
                        </div>
                        <div class="col-auto">
                            <fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgSearch" var="msgSearchBtn"/>
                            <input type="submit" class="btn btn-primary btn-sm"
                                   value="${msgSearchBtn}"/>
                        </div>
                    </div>
                    <input type="hidden" name="currentTeam" id="currentTeam" value="<%= Encode.forHtmlAttribute(team != null ? team : "") %>"/>
                    <input type="hidden" name="orderby" id="orderby" value="<%= Encode.forHtmlAttribute(orderby != null ? orderby : "") %>"/>
                    <input type="hidden" name="desc" id="desc" value="<%= Encode.forHtmlAttribute(desc != null ? desc : "") %>"/>
                    <input type="hidden" name="offset" id="offset" value="<%= Encode.forHtmlAttribute(String.valueOf(offset)) %>"/>
                    <input type="hidden" name="limit" id="limit" value="<%= Encode.forHtmlAttribute(String.valueOf(limit)) %>"/>
                </div>
            </form>

            <!-- Team Info Badge -->
            <div class="mb-2">
                <span class="badge bg-secondary">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msfConsReqForTeam"/>:
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
            </div>

            <!-- Consultation Results Table -->
            <div class="table-responsive">
                <table class="table table-hover table-sm table-bordered consult-table">
                    <thead class="table-light">
                        <tr>
                            <th>
                                <a href="#" onclick="setOrder('1'); return false;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgStatus"/>
                                </a>
                            </th>
                            <th>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgUrgency"/>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('2'); return false;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgTeam"/>
                                </a>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('3'); return false;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgPatient"/>
                                </a>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('4'); return false;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgProvider"/>
                                </a>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('5'); return false;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgService"/>
                                </a>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('6'); return false;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgConsultant"/>
                                </a>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('7'); return false;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgRefDate"/>
                                </a>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('8'); return false;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgAppointmentDate"/>
                                </a>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('9'); return false;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgFollowUpDate"/>
                                </a>
                            </th>
                            <% if (bMultisites) { %>
                            <th>
                                <a href="#" onclick="setOrder('10'); return false;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgSiteName"/>
                                </a>
                            </th>
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
                            int countback = 0;

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
                                    try {
                                        countback = Integer.parseInt(timeperiod);
                                    } catch (NumberFormatException e) {
                                        timeperiod = null; // fall through to default logic below
                                    }
                                }
                                if (timeperiod != null) {
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

                                String viewUrl = request.getContextPath() + "/oscarEncounter/ViewRequest.do?requestId=" + Encode.forUriComponent(id);
                        %>
                        <tr class="<%=overdue ? "consult-row-overdue" : ""%>"
                            tabindex="0"
                            role="button"
                            onclick="popupOscarRx(700,960,'<%=Encode.forJavaScriptAttribute(viewUrl)%>')"
                            onkeypress="if(event.key==='Enter'){popupOscarRx(700,960,'<%=Encode.forJavaScriptAttribute(viewUrl)%>');}">
                            <td class="consult-status-<%=Encode.forHtmlAttribute(status)%>">
                                <% if (status.equals("1")) { %>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgND"/>
                                <% } else if (status.equals("2")) { %>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgSR"/>
                                <% } else if (status.equals("3")) { %>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgPR"/>
                                <% } else if (status.equals("4")) { %>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgDONE"/>
                                <% } else if (status.equals("5")) { %>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgBC"/>
                                <%}%>
                            </td>
                            <td class="consult-status-<%=Encode.forHtmlAttribute(status)%>">
                                <% if (urgency.equals("1")) { %>
                                <span class="urgency-urgent"><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgUrgencyUrgent"/></span>
                                <% } else if (urgency.equals("2")) { %>
                                <fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgUrgencyNonUrgent"/>
                                <% } else if (urgency.equals("3")) { %>
                                <fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgUrgencyReturn"/>
                                <% } %>
                            </td>
                            <td class="consult-status-<%=Encode.forHtmlAttribute(status)%>">
                                <% if (sendTo.equals("-1")) { %>
                                <fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.formTeamNotApplicable"/>
                                <% } else { %>
                                <%=Encode.forHtml(sendTo)%>
                                <% } %>
                            </td>
                            <td class="consult-status-<%=Encode.forHtmlAttribute(status)%>">
                                <%=Encode.forHtml(patient)%>
                            </td>
                            <td class="consult-status-<%=Encode.forHtmlAttribute(status)%>">
                                <%=Encode.forHtml(provide)%>
                            </td>
                            <td class="consult-status-<%=Encode.forHtmlAttribute(status)%>">
                                <%=Encode.forHtml(service)%>
                            </td>
                            <td class="consult-status-<%=Encode.forHtmlAttribute(status)%>">
                                <%=Encode.forHtml(specialist)%>
                                <% if (eReferral) { %>
                                <span class="badge bg-info text-dark ms-1"><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgOceanBadge"/></span>
                                <%} %>
                            </td>
                            <td class="consult-status-<%=Encode.forHtmlAttribute(status)%>">
                                <%=Encode.forHtml(date)%>
                            </td>
                            <td class="consult-status-<%=Encode.forHtmlAttribute(status)%>">
                                <% if (patBook != null && patBook.trim().equals("1")) {%>
                                <span class="fst-italic"><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgPatientWillBook"/></span>
                                <%} else {%>
                                <%=Encode.forHtml(appt)%>
                                <%}%>
                            </td>
                            <td class="consult-status-<%=Encode.forHtmlAttribute(status)%>">
                                <%=Encode.forHtml(followUpDate)%>
                            </td>
                            <% if (bMultisites) { %>
                            <td style="background-color: <%=Encode.forHtmlAttribute(siteBgColor.get(siteName)==null || siteBgColor.get(siteName).length()== 0 ? "#FFFFFF" : siteBgColor.get(siteName))%>">
                                <%=Encode.forHtml(siteShortName.get(siteName) != null ? siteShortName.get(siteName) : "")%>
                            </td>
                            <%} %>
                        </tr>
                        <%}%>
                    </tbody>
                </table>
            </div>

            <!-- Pagination -->
            <div class="d-flex justify-content-between align-items-center mb-3">
                <div>
                    <%
                        if (offset > 0) {
                    %><button type="button" class="btn btn-secondary btn-sm" onclick="gotoPage(false);">
                        <i class="fas fa-chevron-left me-1"></i><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgPrev"/>
                    </button><%
                        }
                        if (theRequests.ids.size() == limit) {
                    %><button type="button" class="btn btn-secondary btn-sm ms-1" onclick="gotoPage(true);">
                        <fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgNext"/><i class="fas fa-chevron-right ms-1"></i>
                    </button><%
                        }
                    %>
                </div>
                <div>
                    <% if (tickerList.size() > 0) {
                        String queryStr = "";
                        for (int i = 0; i < tickerList.size(); i++) {
                            String demo = (String) tickerList.get(i);
                            if (i == 0) {
                                queryStr += "demo=" + Encode.forUriComponent(demo);
                            } else {
                                queryStr += "&demo=" + Encode.forUriComponent(demo);
                            }
                        }%>
                    <fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgAddTicklerConfirm" var="addTicklerConfirmVar"/>
                    <%  String addTicklerConfirmJs = Encode.forJavaScript((String)pageContext.getAttribute("addTicklerConfirmVar")); %>
                    <a class="btn btn-link btn-sm" target="_blank"
                       href="<%= Encode.forHtmlAttribute(request.getContextPath() + "/tickler/AddTickler.do?" + queryStr + "&message=" + java.net.URLEncoder.encode("Patient has Consultation Letter with a status of 'Nothing Done' for over one week","UTF-8")) %>"
                       onclick="return confirm('<%=addTicklerConfirmJs%>');">
                        <i class="fas fa-bell me-1"></i><fmt:message key="oscarEncounter.oscarConsultationRequest.ViewConsultationRequests.msgAddTicklerBtn"/>
                    </a>
                    <%}%>
                </div>
            </div>

        </div>
    </div>

    </body>

</html>
<%!

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
