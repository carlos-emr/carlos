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

<%@page import="io.github.carlos_emr.carlos.appt.ApptData" %>
<%@page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ProviderPreference" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.MyGroup" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.MyGroupDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Appointment" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ScheduleTemplate" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ScheduleDate" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao" %>
<%@page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    MyGroupDao myGroupDao = SpringUtils.getBean(MyGroupDao.class);
    OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
    ScheduleTemplateDao scheduleTemplateDao = SpringUtils.getBean(ScheduleTemplateDao.class);
    ScheduleTemplateCodeDao scheduleTemplateCodeDao = SpringUtils.getBean(ScheduleTemplateCodeDao.class);
%>

<%!
    //multisite starts =====================
    private boolean bMultisites = io.github.carlos_emr.carlos.commn.IsPropertiesOn.isMultisitesEnable();
    private JdbcApptImpl jdbc = new JdbcApptImpl();
    private List<Site> sites;
    private String[] curScheduleMultisite;

    private String getSiteHTML(String scDate, String provider_no, List<Site> sites) {
        if (!bMultisites) return "";
        String _loc = jdbc.getLocationFromSchedule(scDate, provider_no);
        String color = getSafeCssColor(ApptUtil.getColorFromLocation(sites, _loc));
        if (color == null) { color = "white"; }
        return "<span style='background-color:" + color + "'>" + SafeEncode.forHtmlContent(ApptUtil.getShortNameFromLocation(sites, _loc)) + "</span>";
    }

    private static final java.util.regex.Pattern SAFE_CSS_COLOR_PATTERN =
            java.util.regex.Pattern.compile("(?:#[0-9a-fA-F]{3}|#[0-9a-fA-F]{4}|#[0-9a-fA-F]{6}|#[0-9a-fA-F]{8}|[a-zA-Z]+)");

    private String getSafeCssColor(Object configuredColor) {
        if (configuredColor == null) {
            return null;
        }
        String color = configuredColor.toString().trim();
        // Restrict values before interpolation into style attributes to prevent CSS injection.
        return SAFE_CSS_COLOR_PATTERN.matcher(color).matches() ? color : null;
    }
%>
<% if (bMultisites) {
    SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
    sites = siteDao.getAllSites();
}
//multisite ends =======================
%>

<%

    ProviderPreference providerPreference = (ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE);
    int nStartTime = providerPreference.getStartHour();
    int nEndTime = providerPreference.getEndHour();
    int nStep = providerPreference.getEveryMin();
    String mygroupno = providerPreference.getMyGroupNo();

    // Entry points such as the waiting-list booking popup open this page without provider_no.
    // Login2Action stores a default-constructed ProviderPreference for providers that have no
    // saved preference row, and its providerNo is null, so the session identity is the last resort.
    String curProvider_no = request.getParameter("provider_no");
    if (curProvider_no == null) {
        curProvider_no = providerPreference.getProviderNo();
    }
    if (curProvider_no == null) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        curProvider_no = loggedInInfo != null ? loggedInInfo.getLoggedInProviderNo() : null;
    }
    if (curProvider_no == null || !curProvider_no.matches("^[a-zA-Z0-9._-]+$")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid provider_no");
        return;
    }
    Provider currentProvider = providerDao.getProvider(curProvider_no);
    String curProviderName = currentProvider != null
            ? currentProvider.getFormattedName()
            : curProvider_no;
    String curDemoNo = request.getParameter("demographic_no") != null ? request.getParameter("demographic_no") : "";
    String curDemoName = request.getParameter("demographic_name") != null ? request.getParameter("demographic_name") : "";

    String originalPage = request.getParameter("originalpage") != null ? request.getParameter("originalpage") : "schedule";
    String originalPagePath = request.getContextPath() + "/provider/providercontrol";

    if (originalPage.equals("waitingList")) {
        originalPagePath = request.getContextPath() + "/waitinglist/SetupDisplayWaitingList";
    }

    int colscode = (nEndTime - nStartTime) * 60 / nStep;
    SimpleDateFormat inform = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    SimpleDateFormat outform = new SimpleDateFormat("EEE, yyyy/MM/dd", request.getLocale());
    inform.setLenient(false);
    GregorianCalendar now = new GregorianCalendar();
    String requestedStartDate = request.getParameter("startDate");

    // Validate before rendering any response content so the 400 status cannot be lost to a committed buffer.
    if (requestedStartDate != null && !requestedStartDate.isBlank() && !"today".equals(requestedStartDate)) {
        if (!requestedStartDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid startDate");
            return;
        }
        ParsePosition parsePosition = new ParsePosition(0);
        java.util.Date parsedStartDate = inform.parse(requestedStartDate, parsePosition);
        if (parsedStartDate == null || parsePosition.getIndex() != requestedStartDate.length()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid startDate");
            return;
        }
        now.setTime(parsedStartDate);
    }
    String startDate = inform.format(now.getTime());
    GregorianCalendar cal = (GregorianCalendar) now.clone();
    GregorianCalendar lastMonth = (GregorianCalendar) now.clone();
    GregorianCalendar nextMonth = (GregorianCalendar) now.clone();
    GregorianCalendar rangeEnd = (GregorianCalendar) now.clone();
    lastMonth.add(Calendar.MONTH, -1);
    nextMonth.add(Calendar.MONTH, 1);
    rangeEnd.add(Calendar.DATE, 30);
%>
<%@ page
        import="java.util.*, java.sql.*, io.github.carlos_emr.*, java.text.*, java.lang.*,java.net.*"
        errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>

<jsp:useBean id="DateTimeCodeBean" class="java.util.Hashtable"
             scope="page"/>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>


<%@page import="io.github.carlos_emr.carlos.appt.JdbcApptImpl" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="io.github.carlos_emr.carlos.appt.ApptUtil" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<!DOCTYPE html>
<html lang="<%= SafeEncode.forHtmlAttribute(request.getLocale().toLanguageTag()) %>">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="schedule.scheduleflipview.title"/></title>
        <link rel="stylesheet"
              href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css"
              type="text/css">
        <link rel="stylesheet"
              href="${pageContext.request.contextPath}/css/fontawesome-all.min.css"
              type="text/css">
        <link rel="stylesheet"
              href="${pageContext.request.contextPath}/css/scheduleavailability.css"
              type="text/css">

        <script type="text/javascript">
            function changePro(providerno) {
                var destination = "${pageContext.request.contextPath}/schedule/FlipView?originalpage=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(originalPage))%>&provider_no=" + encodeURIComponent(providerno) +<%=request.getParameter("startDate")!=null?("\"&startDate="+SafeEncode.forJavaScript(SafeEncode.forUriComponent(request.getParameter("startDate")))+"\""):"\""%>;<%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                self.location.href = destination;
            }

            function selectprovider(s) {
                changePro(s.options[s.selectedIndex].value);
            }


            function t(s1, s2, s3, s4, s5, s6, doConfirm, allowDay, allowWeek) {
                if (doConfirm == "Yes") {
                    if (confirm("<fmt:message key="provider.appointmentProviderAdminDay.confirmBooking"/>")) {
                        popupPage(360, 680, ('<%= request.getContextPath() %>/appointment/addappointment?demographic_no=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoNo))%>&name=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoName))%>&provider_no=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curProvider_no))%>&bFirstDisp=<%=true%>&year=' + s1 + '&month=' + s2 + '&day=' + s3 + '&start_time=' + s4 + '&end_time=' + s5 + '&duration=' + s6));
                    }
                } else if (doConfirm == "Day") {
                    if (allowDay == "No") {
                        alert("<fmt:message key="provider.appointmentProviderAdminDay.sameDay"/>");
                    } else {
                        popupPage(360, 680, ('<%= request.getContextPath() %>/appointment/addappointment?demographic_no=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoNo))%>&name=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoName))%>&provider_no=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curProvider_no))%>&bFirstDisp=<%=true%>&year=' + s1 + '&month=' + s2 + '&day=' + s3 + '&start_time=' + s4 + '&end_time=' + s5 + '&duration=' + s6));
                    }
                } else if (doConfirm == "Wk") {
                    if (allowWeek == "No") {
                        alert("<fmt:message key="provider.appointmentProviderAdminDay.sameWeek"/>");
                    } else {
                        popupPage(360, 680, ('<%= request.getContextPath() %>/appointment/addappointment?demographic_no=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoNo))%>&name=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoName))%>&provider_no=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curProvider_no))%>&bFirstDisp=<%=true%>&year=' + s1 + '&month=' + s2 + '&day=' + s3 + '&start_time=' + s4 + '&end_time=' + s5 + '&duration=' + s6));
                    }
                } else if (doConfirm == "Onc") {
                    if (allowDay == "No") {
                        if (confirm("<fmt:message key='schedule.scheduleflipview.msgOnCallUrgentConfirm'/>")) {
                            popupPage(360, 680, ('<%= request.getContextPath() %>/appointment/addappointment?demographic_no=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoNo))%>&name=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoName))%>&provider_no=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curProvider_no))%>&bFirstDisp=<%=true%>&year=' + s1 + '&month=' + s2 + '&day=' + s3 + '&start_time=' + s4 + '&end_time=' + s5 + '&duration=' + s6));
                        }
                    } else {
                        popupPage(360, 680, ('<%= request.getContextPath() %>/appointment/addappointment?demographic_no=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoNo))%>&name=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoName))%>&provider_no=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curProvider_no))%>&bFirstDisp=<%=true%>&year=' + s1 + '&month=' + s2 + '&day=' + s3 + '&start_time=' + s4 + '&end_time=' + s5 + '&duration=' + s6));
                    }
                } else {
                    popupPage(360, 680, ('<%= request.getContextPath() %>/appointment/addappointment?demographic_no=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoNo))%>&name=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoName))%>&provider_no=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(curProvider_no))%>&bFirstDisp=<%=true%>&year=' + s1 + '&month=' + s2 + '&day=' + s3 + '&start_time=' + s4 + '&end_time=' + s5 + '&duration=' + s6));
                }
            }
        </script>
    </head>
    <body class="availability-page">
    <main class="container-fluid availability-shell py-3">
        <header class="availability-page-header mb-3">
            <div>
                <h1><fmt:message key="schedule.scheduleflipview.title"/></h1>
                <p class="text-body-secondary mb-0"><fmt:message key="schedule.scheduleflipview.instructions"/></p>
            </div>
            <nav class="btn-group" aria-label="<fmt:message key="schedule.scheduleflipview.navigation"/>">
                <button type="button" class="btn btn-outline-secondary" onclick="history.back()">
                    <span class="fa-solid fa-arrow-left" aria-hidden="true"></span>
                    <fmt:message key="schedule.scheduleflipview.btnGoBack"/>
                </button>
                <a class="btn btn-primary"
                   href="<%=originalPagePath%>?year=<%=now.get(Calendar.YEAR)%>&amp;month=<%=now.get(Calendar.MONTH) + 1%>&amp;day=<%=now.get(Calendar.DATE)%>&amp;view=1&amp;curProvider=<carlos:encode value='<%= curProvider_no %>' context="uriComponent"/>&amp;curProviderName=<carlos:encode value='<%= curProviderName %>' context="uriComponent"/>&amp;displaymode=day&amp;dboperation=searchappointmentday">
                    <span class="fa-solid fa-calendar-day" aria-hidden="true"></span>
                    <fmt:message key="schedule.scheduleflipview.btnDayPage"/>
                </a>
            </nav>
        </header>

        <section class="card shadow-sm mb-3" aria-label="<fmt:message key="schedule.scheduleflipview.filters"/>">
            <div class="card-body availability-controls">
                <div class="availability-provider-control">
                    <label class="form-label fw-semibold mb-1" for="availabilityProvider">
                        <fmt:message key="schedule.scheduleflipview.provider"/>
                    </label>
                    <select id="availabilityProvider" class="form-select" name="provider_no"
                            onchange="selectprovider(this)">
                <%

                    if (currentProvider != null) {
                %>
                <option value="<carlos:encode value='<%= currentProvider.getProviderNo() %>' context="htmlAttribute"/>" selected><carlos:encode value='<%= currentProvider.getFormattedName() %>' context="html"/>
                </option>
                <%
                    } else {
                        // The grid below is still built from curProvider_no, so the selector has to
                        // name it even when no provider row matches. Without an explicitly selected
                        // option the browser falls back to the first entry, which labels this
                        // provider's schedule with a different, real provider. curProviderName
                        // already degrades to the raw number for exactly this case.
                %>
                <option value="<carlos:encode value='<%= curProvider_no %>' context="htmlAttribute"/>" selected><carlos:encode value='<%= curProviderName %>' context="html"/>
                </option>
                <%
                    }

                    if (!bMultisites) {
                        List<MyGroup> mgs = myGroupDao.getGroupByGroupNo(mygroupno);
                        for (MyGroup mg : mgs) {
                            if (mg.getId().getProviderNo().equals(curProvider_no)) {
                                continue;
                            }
                %>
                <option value="<carlos:encode value='<%= mg.getId().getProviderNo() %>' context="htmlAttribute"/>"><carlos:encode value='<%= mg.getLastName() + ", " + mg.getFirstName() %>' context="html"/>
                </option>
                <%
                        }
                    }
                %>
                    </select>
                </div>
                <div>
                    <div class="form-label fw-semibold mb-1"><fmt:message key="schedule.scheduleflipview.dateRange"/></div>
                    <div class="form-control bg-body-secondary">
                        <%=SafeEncode.forHtmlContent(outform.format(now.getTime()))%>
                        &ndash;
                        <%=SafeEncode.forHtmlContent(outform.format(rangeEnd.getTime()))%>
                    </div>
                </div>
                <nav class="btn-group" aria-label="<fmt:message key="schedule.scheduleflipview.monthNavigation"/>">
                    <a class="btn btn-outline-primary"
                       href="${pageContext.request.contextPath}/schedule/FlipView?originalpage=<carlos:encode value='<%= originalPage %>' context="uriComponent"/>&amp;provider_no=<carlos:encode value='<%= curProvider_no %>' context="uriComponent"/>&amp;startDate=<carlos:encode value='<%= inform.format(lastMonth.getTime()) %>' context="uriComponent"/>"
                       title="<fmt:message key="schedule.scheduleflipview.msgLastMonth"/>">
                        <span class="fa-solid fa-chevron-left" aria-hidden="true"></span>
                        <fmt:message key="schedule.scheduleflipview.btnLastMonth"/>
                    </a>
                    <a class="btn btn-outline-primary"
                       href="${pageContext.request.contextPath}/schedule/FlipView?originalpage=<carlos:encode value='<%= originalPage %>' context="uriComponent"/>&amp;provider_no=<carlos:encode value='<%= curProvider_no %>' context="uriComponent"/>&amp;startDate=<carlos:encode value='<%= inform.format(nextMonth.getTime()) %>' context="uriComponent"/>"
                       title="<fmt:message key="schedule.scheduleflipview.msgNextmonth"/>">
                        <fmt:message key="schedule.scheduleflipview.btnNextMonth"/>
                        <span class="fa-solid fa-chevron-right" aria-hidden="true"></span>
                    </a>
                </nav>
            </div>
        </section>

        <div class="availability-help alert alert-light border d-flex gap-2 align-items-start" role="note">
            <span class="fa-solid fa-circle-info mt-1" aria-hidden="true"></span>
            <span><fmt:message key="schedule.scheduleflipview.legend"/></span>
        </div>

        <div class="availability-grid-wrapper">
        <table id="availabilityGrid" class="table table-sm" aria-label="<fmt:message key="schedule.scheduleflipview.gridLabel"/>">
        <thead>
        <tr>
            <th class="availability-date" scope="col"><fmt:message key="schedule.scheduleflipview.date"/></th>
            <% if (bMultisites) { %>
            <th class="availability-site" scope="col"><fmt:message key="schedule.scheduleflipview.tableSite"/></th>
            <% } %>
            <% for (int j = 0; j < colscode; j++) { %>
            <%
                int headingMinutes = nStartTime * 60 + j * nStep;
                int headingHour = headingMinutes / 60;
                int headingMinute = headingMinutes % 60;
            %>
            <th scope="col"><%=String.format(Locale.ROOT, "%02d:%02d", headingHour, headingMinute)%></th>
            <% } %>
        </tr>
        </thead>
        <tbody>
        <%
            cal.add(Calendar.DATE, 31);
            int starttime = 0, endtime = 0;
            StringBuffer hourmin = null;
            int hour = 0, min = 0;

            //find the appts above the schedule
            Integer numOfAppts;

            for (Appointment a : appointmentDao.search_appt(ConversionUtils.fromDateString(startDate), ConversionUtils.fromDateString(cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DATE)), curProvider_no)) {

                starttime = Integer.parseInt(ConversionUtils.toTimeString(a.getStartTime()).substring(0, 2)) * 60 + Integer.parseInt(ConversionUtils.toTimeString(a.getStartTime()).substring(3, 5));
                endtime = Integer.parseInt(ConversionUtils.toTimeString(a.getEndTime()).substring(0, 2)) * 60 + Integer.parseInt(ConversionUtils.toTimeString(a.getEndTime()).substring(3, 5));

                for (int k = nStartTime * 60; k < (nEndTime + 1) * 60; k += nStep) {
                    if (starttime > k) continue;
                    else {
                        if (endtime > k && !a.getStatus().equals("C")) {
                            hour = k / 60;
                            min = k % 60;
                            hourmin = new StringBuffer(ConversionUtils.toDateString(a.getAppointmentDate()) + (hour < 10 ? "0" : "") + hour + (min < 10 ? ":0" : ":") + min + ":00");

                            if (DateTimeCodeBean.get(hourmin.toString()) == null) {
                                //DateTimeCodeBean.put(hourmin.toString(), "-");
                                DateTimeCodeBean.put(hourmin.toString(), 1);
                            } else {
                                numOfAppts = (Integer) DateTimeCodeBean.get(hourmin.toString());
                                ++numOfAppts;
                                DateTimeCodeBean.put(hourmin.toString(), numOfAppts);
                            }

                            continue;
                        } else break; //e<=k
                    }
                }
            }

            //store timecode for every available day
            String bgcolordef = "#FFFFE0";
            for (Object[] result : scheduleTemplateDao.findSchedules(ConversionUtils.fromDateString(startDate), ConversionUtils.fromDateString(cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DATE)), curProvider_no)) {
                ScheduleTemplate st = (ScheduleTemplate) result[0];
                ScheduleDate sd = (ScheduleDate) result[1];
                DateTimeCodeBean.put(ConversionUtils.toDateString(sd.getDate()), st.getTimecode());
            }

            //color for template code
            List<ScheduleTemplateCode> stcs = scheduleTemplateCodeDao.findAll();
            Collections.sort(stcs, ScheduleTemplateCode.CodeComparator);

            for (ScheduleTemplateCode stc : stcs) {
                //DateTimeCodeBean.put("description"+rsdemo.getString("code"), rsdemo.getString("description"));
                DateTimeCodeBean.put("duration" + stc.getCode(), stc.getDuration());
                DateTimeCodeBean.put("color" + stc.getCode(), (stc.getColor() == null || stc.getColor().equals("")) ? bgcolordef : stc.getColor());
                DateTimeCodeBean.put("bookinglimit" + stc.getCode(), String.valueOf(stc.getBookinglimit()));
                DateTimeCodeBean.put("confirm" + stc.getCode(), stc.getConfirm());
            }

            DateTimeCodeBean.put("color-", "silver");
            DateTimeCodeBean.put("color|", "gold");
            DateTimeCodeBean.put("color||", "red");

            cal.add(Calendar.DATE, -31);
            StringBuffer temp = null;
            String strTempDate = null;

            for (int i = 0; i < 31; i++) {
                temp = new StringBuffer();
                boolean weekend = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                        || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
                temp = temp.append(cal.get(Calendar.YEAR)).append("-").append(cal.get(Calendar.MONTH) + 1).append("-").append(cal.get(Calendar.DATE));
                strTempDate = inform.format(inform.parse(temp.toString()));

                /* This calendar will set the end_time of a appointment */
                Calendar appointmentTime = Calendar.getInstance();
                appointmentTime.set(Calendar.HOUR_OF_DAY, nStartTime);
                appointmentTime.set(Calendar.MINUTE, 0);
                /* this -1 is explained below */
                appointmentTime.add(Calendar.MINUTE, -1);
        %>
        <tr class="<%=weekend ? "weekend" : ""%>">
            <th class="availability-date" scope="row">
                <a href="<%=originalPagePath%>?year=<%=cal.get(Calendar.YEAR)%>&amp;month=<%=cal.get(Calendar.MONTH)+1%>&amp;day=<%=cal.get(Calendar.DATE)%>&amp;view=1&amp;curProvider=<carlos:encode value='<%= curProvider_no %>' context="uriComponent"/>&amp;curProviderName=<carlos:encode value='<%= curProviderName %>' context="uriComponent"/>&amp;displaymode=day&amp;dboperation=searchappointmentday">
                    <%=SafeEncode.forHtmlContent(outform.format(inform.parse(strTempDate)))%>
                </a>
            </th>
            <% if (bMultisites) { %>
            <td class="availability-site"><%=getSiteHTML(strTempDate, curProvider_no, sites)%></td>
            <% } %>
            <%
                String bookinglimit;
                String scheduleCode;
                //calculate the ratio by the length of timecode
                for (int j = 0; j < colscode; j++) {
                    scheduleCode = "";
                    hour = (nStartTime * 60 + j * nStep) / 60;
                    min = j * nStep % 60;
	/* This appoint will finish one minute before the next appointment
	   To do this minute before, set -1 outside this loop
	 */
                    appointmentTime.add(Calendar.MINUTE, nStep);
                    if (DateTimeCodeBean.get(MyDateFormat.getMysqlStandardDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DATE))) != null) {
                        int nLen = 24 * 60 / ((String) DateTimeCodeBean.get(MyDateFormat.getMysqlStandardDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DATE)))).length();
                        int ratio = (hour * 60 + min) / nLen;
                        temp = new StringBuffer(((String) DateTimeCodeBean.get(MyDateFormat.getMysqlStandardDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DATE)))).substring(ratio, ratio + 1)); //(nStartTime*60*ratio/nStep+j*ratio),(nStartTime*60*ratio/nStep+j*ratio)+1)
                        scheduleCode = temp.toString();
                        bookinglimit = String.valueOf(DateTimeCodeBean.get("bookinglimit" + scheduleCode));
                        if (bookinglimit == null || bookinglimit.equals("null")) {
                            bookinglimit = "";
                        }
                    } else {
                        temp = new StringBuffer("&nbsp;");
                        bookinglimit = "";
                    }

                    String strNumOfAppts = "";
                    int limitDelta = 0;
                    int limit = bookinglimit.length() > 0 ? Integer.parseInt(bookinglimit) : 1;
                    hourmin = new StringBuffer(strTempDate + (hour < 10 ? "0" : "") + hour + (min < 10 ? ":0" : ":") + min + ":00");
                    if (DateTimeCodeBean.get(hourmin.toString()) != null) {
                        numOfAppts = (Integer) DateTimeCodeBean.get(hourmin.toString());
                        strNumOfAppts = String.valueOf(numOfAppts);
                        limitDelta = limit - numOfAppts;
                        if (limitDelta == 0) {
                            temp = new StringBuffer("-");
                        } else if (limitDelta == -1) {
                            temp = new StringBuffer("|");
                        } else if (limitDelta <= -2) {
                            temp = new StringBuffer("||");
                        }

                    }

                    Calendar minDate = Calendar.getInstance();
                    minDate.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY));
                    minDate.set(Calendar.MINUTE, cal.get(Calendar.MINUTE));
                    minDate.set(Calendar.SECOND, cal.get(Calendar.SECOND));
                    minDate.set(Calendar.MILLISECOND, cal.get(Calendar.MILLISECOND));

                    String allowDay = "";
                    if (cal.equals(minDate)) {
                        allowDay = "Yes";
                    } else {
                        allowDay = "No";
                    }
                    minDate.add(Calendar.DATE, 7);
                    String allowWeek = "";
                    if (cal.before(minDate)) {
                        allowWeek = "Yes";
                    } else {
                        allowWeek = "No";
                    }

                    String slotColor = getSafeCssColor(DateTimeCodeBean.get("color" + temp.toString()));
            %>
            <td <%=slotColor != null
                    ? ("style=\"background-color:" + SafeEncode.forHtmlAttribute(slotColor) + "\"")
                    : ""%>
                title="<%=String.format(Locale.ROOT, "%02d:%02d", hour, min)%>">
                <button type="button" class="availability-slot"
                        aria-label="<%=SafeEncode.forHtmlAttribute(outform.format(cal.getTime()))%> <%=String.format(Locale.ROOT, "%02d:%02d", hour, min)%>; <fmt:message key="schedule.scheduleflipview.msgbookings"/>: <%=SafeEncode.forHtmlAttribute(strNumOfAppts)%>; <fmt:message key="schedule.scheduleflipview.msgbookinglimit"/>: <carlos:encode value='<%= bookinglimit %>' context="htmlAttribute"/>"
                        onclick="t(<%=cal.get(Calendar.YEAR)%>,<%=cal.get(Calendar.MONTH)+1%>,<%=cal.get(Calendar.DATE)%>,'<%=(hour<10?"0":"")+hour+":"+(min<10?"0":"")+min %>','<%=appointmentTime.get(Calendar.HOUR_OF_DAY)%>:<%=appointmentTime.get(Calendar.MINUTE)%>','<carlos:encode value='<%= DateTimeCodeBean.get("duration"+temp.toString()) != null ? String.valueOf(DateTimeCodeBean.get("duration"+temp.toString())) : "" %>' context="javaScriptAttribute"/>','<carlos:encode value='<%= DateTimeCodeBean.get("confirm"+scheduleCode) != null ? String.valueOf(DateTimeCodeBean.get("confirm"+scheduleCode)) : "" %>' context="javaScriptAttribute"/>','<%=allowDay%>','<%=allowWeek%>');">
                    <span class="availability-slot-code">
                        <%= "&nbsp;".equals(temp.toString()) ? "&nbsp;" : SafeEncode.forHtmlContent(temp.toString()) %>
                    </span>
                    <span class="availability-slot-counts">
                        <span title="<fmt:message key="schedule.scheduleflipview.msgbookings"/>"><%=strNumOfAppts%></span>
                        <span title="<fmt:message key="schedule.scheduleflipview.msgbookinglimit"/>"><carlos:encode value='<%= bookinglimit %>' context="html"/></span>
                    </span>
                </button>
            </td>
            <%
                }
            %>
        </tr>
        <%
                cal.add(Calendar.DATE, 1);
            }
        %>
        </tbody>
    </table>
    </div>
    <%-- Visual repeat of the month controls in the filter card, for users who have scrolled
         past the grid. Deliberately not a <nav> landmark: a second landmark carrying the same
         accessible name as the header one is duplicate noise for screen-reader navigation. --%>
    <div class="availability-footer-nav mt-3">
        <a class="btn btn-outline-primary"
           href="${pageContext.request.contextPath}/schedule/FlipView?originalpage=<carlos:encode value='<%= originalPage %>' context="uriComponent"/>&amp;provider_no=<carlos:encode value='<%= curProvider_no %>' context="uriComponent"/>&amp;startDate=<carlos:encode value='<%= inform.format(lastMonth.getTime()) %>' context="uriComponent"/>">
            <span class="fa-solid fa-chevron-left" aria-hidden="true"></span>
            <fmt:message key="schedule.scheduleflipview.btnLastMonth"/>
        </a>
        <a class="btn btn-outline-primary"
           href="${pageContext.request.contextPath}/schedule/FlipView?originalpage=<carlos:encode value='<%= originalPage %>' context="uriComponent"/>&amp;provider_no=<carlos:encode value='<%= curProvider_no %>' context="uriComponent"/>&amp;startDate=<carlos:encode value='<%= inform.format(nextMonth.getTime()) %>' context="uriComponent"/>">
            <fmt:message key="schedule.scheduleflipview.btnNextMonth"/>
            <span class="fa-solid fa-chevron-right" aria-hidden="true"></span>
        </a>
    </div>
    </main>
    </body>
</html>
