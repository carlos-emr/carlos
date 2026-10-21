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
        String color = ApptUtil.getColorFromLocation(sites, _loc);
        // Validate color against safe CSS characters to prevent CSS injection via style attribute
        if (!color.matches("[a-zA-Z0-9#]+")) { color = "white"; }
        return "<span style='background-color:" + color + "'>" + SafeEncode.forHtml(ApptUtil.getShortNameFromLocation(sites, _loc)) + "</span>";
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

    String curProvider_no = request.getParameter("provider_no") != null ? request.getParameter("provider_no") : "174";
    if (!curProvider_no.matches("^[a-zA-Z0-9._-]+$")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid provider_no");
        return;
    }
    String curDemoNo = request.getParameter("demographic_no") != null ? request.getParameter("demographic_no") : "";
    String curDemoName = request.getParameter("demographic_name") != null ? request.getParameter("demographic_name") : "";
    String[] param = new String[3];

    String originalPage = request.getParameter("originalpage") != null ? request.getParameter("originalpage") : "schedule";
    String originalPagePath = request.getContextPath() + "/provider/providercontrol";

    if (originalPage.equals("waitingList")) {
        originalPagePath = request.getContextPath() + "/waitinglist/SetupDisplayWaitingList";
    }
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
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="schedule.scheduleflipview.title"/></title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css" type="text/css">
        <style>
            /* The page's former presentational attributes, as CSS, so it looks as it did. */
            body.flipview { background-color: #999FFF; color: #000000; margin: 0; }
            table.flipview-grid { width: 100%; border-spacing: 0; font-size: medium; }
            table.flipview-grid > tbody > tr > td,
            table.flipview-grid > tbody > tr > th { padding: 0; }
            tr.flipview-head { text-align: center; background-color: #CCCCFF; }
            tr.flipview-head > th { font-weight: normal; }
            th.flipview-provider { width: 15%; white-space: nowrap; }
            tr.flipview-day { text-align: center; }
            td.flipview-date { text-align: right; white-space: nowrap; }
            /* Was display:inline. With the doctype, an inline table sits on a text line and makes
               every row taller; a centred block table keeps the rows as they were. */
            table.flipview-slotbox { display: table; margin: 0 auto; font-size: x-small; }
            /* A slot is a button, not a link, and still looks like the link it was. */
            button.flipview-slot { background: none; border: 0; padding: 0; margin: 0; font: inherit; color: #0066CC; cursor: pointer; }
            button.flipview-slot:hover { color: red; text-decoration: underline; }
            .flipview-hidden { position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
        </style>

        <script language="JavaScript">
            <!--
            <%-- The patient being booked (from the waiting list) is kept across provider changes. --%>
            function changePro(providerno) {
                var destination = "${pageContext.request.contextPath}/schedule/FlipView?originalpage=<%=SafeEncode.forJavaScript(SafeEncode.forUriComponent(originalPage))%>&provider_no=" + encodeURIComponent(providerno) + "<%= request.getParameter("startDate") == null ? "" : "&startDate=" + SafeEncode.forJavaScript(SafeEncode.forUriComponent(request.getParameter("startDate"))) %><%= curDemoNo.isEmpty() ? "" : "&demographic_no=" + SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoNo)) + "&demographic_name=" + SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoName)) %>";<%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                self.location.href = destination;
            }

            function selectprovider(s) {
                changePro(s.options[s.selectedIndex].value);
            }//-->


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
        </SCRIPT>

    </head>
    <%


        //int nStartTime=9, nEndTime=17, nStep = 15;
        int colscode = (nEndTime - nStartTime) * 60 / nStep;
        String rColor1 = "#FFFFE0", rColor2 = "#FFFFE0", bgcolor = "gold";
        String startDate = request.getParameter("startDate") != null ? request.getParameter("startDate") : "today";
        SimpleDateFormat inform = new SimpleDateFormat("yyyy-MM-dd", request.getLocale());
        SimpleDateFormat outform = new SimpleDateFormat("EEE, yyyy/MM/dd", request.getLocale());
        GregorianCalendar now = new GregorianCalendar();

        if (!startDate.equals("today")) now.setTime(inform.parse(startDate));
        GregorianCalendar cal = (GregorianCalendar) now.clone();
        GregorianCalendar lastMonth = (GregorianCalendar) now.clone();
        GregorianCalendar nextMonth = (GregorianCalendar) now.clone();
        lastMonth.add(Calendar.MONTH, -1);
        nextMonth.add(Calendar.MONTH, 1);
        // note: brain-dead calendar numbers months from 0, thus all the +1s in the expressions below
//  String dateString1 = outform.format(inform.parse(cal.get(Calendar.YEAR)+"-"+(cal.get(Calendar.MONTH)+1)+"-"+cal.get(Calendar.DATE)) );
    %>
    <body class="flipview">


    <div style="colur: #FF0000; text-decoration: none"><a
            href="javascript:history.go(-1)"
            style="text-decoration: none; color: #000000"><fmt:message key="schedule.scheduleflipview.btnGoBack"/></a> <a
            href="<%= request.getContextPath() %>/provider/providercontrol"
            style="text-decoration: none; color: #000000"><fmt:message key="schedule.scheduleflipview.btnDayPage"/></a></div>
    <table border="1" class="flipview-grid">
        <tr class="flipview-head">
            <% if (bMultisites) { %><th scope="col"><fmt:message key="schedule.scheduleflipview.tableSite"/></th><% } %>
            <th scope="col" class="flipview-provider"><a
                    href="${pageContext.request.contextPath}/schedule/FlipView?originalpage=<carlos:encode value='<%= originalPage %>' context="uriComponent"/>&provider_no=<carlos:encode value='<%= curProvider_no %>' context="uriComponent"/>&startDate=<%=lastMonth.get(Calendar.YEAR)+"-"+(lastMonth.get(Calendar.MONTH)+1)+"-"+lastMonth.get(Calendar.DATE)%><% if (!curDemoNo.isEmpty()) { %>&amp;demographic_no=<carlos:encode value='<%= curDemoNo %>' context="uriComponent"/>&amp;demographic_name=<carlos:encode value='<%= curDemoName %>' context="uriComponent"/><% } %>"
                    title="<fmt:message key="schedule.scheduleflipview.msgLastMonth"/>"
                    border='0'><img src="<%= request.getContextPath() %>/images/previous.gif" alt="<fmt:message key="schedule.scheduleflipview.msgLastMonth"/>"></a> <label
                    for="flipViewProvider" class="flipview-hidden"><fmt:message key="provider.appointmentProviderAdminDay.provider"/></label><select
                    id="flipViewProvider" name="provider_no" onChange="selectprovider(this)">
                <%


                    if (bMultisites) {
                        Provider p = providerDao.getProvider(curProvider_no);
                        if (p != null) {
                %>
                <option value="<carlos:encode value='<%= p.getProviderNo() %>' context="htmlAttribute"/>" <%=p.getProviderNo().equals(curProvider_no) ? "selected" : ""%>><carlos:encode value='<%= Misc.getShortStr(p.getFormattedName(), "", 12) %>' context="html"/>
                </option>
                <%
                    }
                } else {
                    List<MyGroup> mgs = myGroupDao.getGroupByGroupNo(mygroupno);
                    for (MyGroup mg : mgs) {
                %>
                <option value="<carlos:encode value='<%= mg.getId().getProviderNo() %>' context="htmlAttribute"/>" <%=mg.getId().getProviderNo().equals(curProvider_no) ? "selected" : ""%>><carlos:encode value='<%= Misc.getShortStr(mg.getLastName() + "," + mg.getFirstName(), "", 12) %>' context="html"/>
                </option>
                <%
                        }
                    }
                %>
            </select><a
                    href="${pageContext.request.contextPath}/schedule/FlipView?originalpage=<carlos:encode value='<%= originalPage %>' context="uriComponent"/>&provider_no=<carlos:encode value='<%= curProvider_no %>' context="uriComponent"/>&startDate=<%=nextMonth.get(Calendar.YEAR)+"-"+(nextMonth.get(Calendar.MONTH)+1)+"-"+nextMonth.get(Calendar.DATE)%><% if (!curDemoNo.isEmpty()) { %>&amp;demographic_no=<carlos:encode value='<%= curDemoNo %>' context="uriComponent"/>&amp;demographic_name=<carlos:encode value='<%= curDemoName %>' context="uriComponent"/><% } %>"
                    title="<fmt:message key="schedule.scheduleflipview.msgNextmonth"/>"
                    border='0'><img src="<%= request.getContextPath() %>/images/next.gif" alt="<fmt:message key="schedule.scheduleflipview.msgNextmonth"/>"></a></th>
            <% for (int j = 0; j < colscode; j++) { %>
            <th scope="col">
                <% if (nStep < 60) { %> <%=j % (60 / nStep) == 0 ? "" + (j / (60 / nStep) + nStartTime) : ""%>
                <% } else { //show everyhour %> <%=j + nStartTime%> <% } %>
            </th>
            <% } %>
        </tr>
        <%
            cal.add(Calendar.DATE, 31);
            int starttime = 0, endtime = 0;
            StringBuffer hourmin = null;
            int hour = 0, min = 0;

            //find the appts above the schedule
            Integer numOfAppts;
            param[0] = curProvider_no;
            param[1] = startDate;
            param[2] = cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DATE);

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
                bgcolor = cal.get(Calendar.DAY_OF_WEEK) == 7 || cal.get(Calendar.DAY_OF_WEEK) == 1 ? "#EEEEFF" : (i % 2 == 0 ? rColor1 : rColor2);
                //bgcolor = cal.get(Calendar.DAY_OF_WEEK)==7?"#FFF68F":cal.get(Calendar.DAY_OF_WEEK)==1?"#FFF68F":(i%2==0?rColor1:rColor2);
                temp = temp.append(cal.get(Calendar.YEAR)).append("-").append(cal.get(Calendar.MONTH) + 1).append("-").append(cal.get(Calendar.DATE));
                strTempDate = inform.format(inform.parse(temp.toString()));

                /* This calendar will set the end_time of a appointment */
                Calendar appointmentTime = Calendar.getInstance();
                appointmentTime.set(Calendar.HOUR_OF_DAY, nStartTime);
                appointmentTime.set(Calendar.MINUTE, 0);
                /* this -1 is explained below */
                appointmentTime.add(Calendar.MINUTE, -1);
        %>
        <tr class="flipview-day" style="background-color: <%=bgcolor%>">
            <%
                if (bMultisites)
                    out.print("<td align='right'>" + getSiteHTML(strTempDate, curProvider_no, sites) + "</td>");
            %>
            <td class="flipview-date"><a
                    href="<%=originalPagePath%>?year=<%=cal.get(Calendar.YEAR)%>&month=<%=cal.get(Calendar.MONTH)+1%>&day=<%=cal.get(Calendar.DATE)%>&view=0&displaymode=day&dboperation=searchappointmentday"><%=outform.format(inform.parse(strTempDate))%>&nbsp;</a>
            </td>
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

            %>
            <td
                    <%=DateTimeCodeBean.get("color" + temp.toString()) != null ? ("bgcolor=\"" + SafeEncode.forHtmlAttribute(String.valueOf(DateTimeCodeBean.get("color" + temp.toString()))) + "\"") : ""%>
                    title="<%=hour+":"+(min<10?"0":"")+min%>">
                <table role="presentation" class="flipview-slotbox">
                    <tr>
                        <td rowspan="2" style="vertical-align:middle;"><button type="button" class="flipview-slot"
                                                                          onclick="t(<%=cal.get(Calendar.YEAR)%>,<%=cal.get(Calendar.MONTH)+1%>,<%=cal.get(Calendar.DATE)%>,'<%=(hour<10?"0":"")+hour+":"+(min<10?"0":"")+min %>','<%=appointmentTime.get(Calendar.HOUR_OF_DAY)%>:<%=appointmentTime.get(Calendar.MINUTE)%>','<carlos:encode value='<%= DateTimeCodeBean.get("duration"+temp.toString()) != null ? String.valueOf(DateTimeCodeBean.get("duration"+temp.toString())) : "" %>' context="javaScriptAttribute"/>','<carlos:encode value='<%= DateTimeCodeBean.get("confirm"+scheduleCode) != null ? String.valueOf(DateTimeCodeBean.get("confirm"+scheduleCode)) : "" %>' context="javaScriptAttribute"/>','<%=allowDay%>','<%=allowWeek%>');">
                            <%= "&nbsp;".equals(temp.toString()) ? "&nbsp;" : SafeEncode.forHtml(temp.toString()) %>
                        </button></td>
                        <td title="<fmt:message key="schedule.scheduleflipview.msgbookings"/>"
                            style="vertical-align:top; font-size: x-small;"><%=strNumOfAppts%>
                        </td>
                    </tr>
                    <tr>
                        <td style="vertical-align:bottom; font-size: x-small;"
                            title="<fmt:message key="schedule.scheduleflipview.msgbookinglimit"/>"><carlos:encode value='<%= bookinglimit %>' context="html"/>
                        </td>
                    </tr>
                </table>
            </td>
            <%
                }
            %>
        </tr>
        <%
                cal.add(Calendar.DATE, 1);
            }
        %>

    </table>
    <a
            href="${pageContext.request.contextPath}/schedule/FlipView?originalpage=<carlos:encode value='<%= originalPage %>' context="uriComponent"/>&provider_no=<carlos:encode value='<%= curProvider_no %>' context="uriComponent"/>&startDate=<%=lastMonth.get(Calendar.YEAR)+"-"+(lastMonth.get(Calendar.MONTH)+1)+"-"+lastMonth.get(Calendar.DATE)%><% if (!curDemoNo.isEmpty()) { %>&amp;demographic_no=<carlos:encode value='<%= curDemoNo %>' context="uriComponent"/>&amp;demographic_name=<carlos:encode value='<%= curDemoName %>' context="uriComponent"/><% } %>"><fmt:message key="schedule.scheduleflipview.btnLastMonth"/> </a>
    |
    <a
            href="${pageContext.request.contextPath}/schedule/FlipView?originalpage=<carlos:encode value='<%= originalPage %>' context="uriComponent"/>&provider_no=<carlos:encode value='<%= curProvider_no %>' context="uriComponent"/>&startDate=<%=nextMonth.get(Calendar.YEAR)+"-"+(nextMonth.get(Calendar.MONTH)+1)+"-"+nextMonth.get(Calendar.DATE)%><% if (!curDemoNo.isEmpty()) { %>&amp;demographic_no=<carlos:encode value='<%= curDemoNo %>' context="uriComponent"/>&amp;demographic_name=<carlos:encode value='<%= curDemoName %>' context="uriComponent"/><% } %>"><fmt:message key="schedule.scheduleflipview.btnNextMonth"/></a>
    </body>
</html>
