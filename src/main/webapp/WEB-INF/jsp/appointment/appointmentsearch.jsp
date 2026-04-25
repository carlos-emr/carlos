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

<!--
    Search Next Appointment
    ================================
    Purpose: Search feature to find open appointment slots

    Features:
    - HTML5 compliant
    - Bootstrap 5 responsive grid (flex-based, auto-reformat by screen size)
    - Alert banner area for JS-driven error/warning messages
    - Form controls using Bootstrap form-control / form-control-sm classes

    @since 2020
-->


<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Calendar" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode" %>
<%@ page import="io.github.carlos_emr.carlos.appointment.web.NextAppointmentSearchHelper" %>
<%@ page import="io.github.carlos_emr.carlos.appointment.web.NextAppointmentSearchBean" %>
<%@ page import="io.github.carlos_emr.carlos.appointment.web.NextAppointmentSearchResult" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.util.LabelValueBean" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%
    SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm");
    java.util.ResourceBundle oscarRec = java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());
        //providers
    String providerNo = request.getParameter("provider_no") != null ? request.getParameter("provider_no") : "";
    ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
    List<Provider> providers = providerDao.getActiveProviders();

    //day of week
    String dayOfWeek = request.getParameter("dayOfWeek") != null ? request.getParameter("dayOfWeek") : "daily";
    String anywkd = oscarRec.getString("admin.jobs.everyWeekday");
    String mond = oscarRec.getString("admin.jobs.monday");  
    String tued = oscarRec.getString("admin.jobs.tuesday");      
    String wedd = oscarRec.getString("admin.jobs.wednesday");  
    String thud = oscarRec.getString("admin.jobs.thursday"); 
    String frid = oscarRec.getString("admin.jobs.friday");      
    String satd = oscarRec.getString("admin.jobs.saturday");  
    String sund = oscarRec.getString("admin.jobs.sunday"); 
    
        List<LabelValueBean> dayOfWeekOptions = new ArrayList<>();
    dayOfWeekOptions.add(new LabelValueBean(anywkd, "daily"));
    dayOfWeekOptions.add(new LabelValueBean(mond, String.valueOf(Calendar.MONDAY)));
    dayOfWeekOptions.add(new LabelValueBean(tued, String.valueOf(Calendar.TUESDAY)));
    dayOfWeekOptions.add(new LabelValueBean(wedd, String.valueOf(Calendar.WEDNESDAY)));
    dayOfWeekOptions.add(new LabelValueBean(thud, String.valueOf(Calendar.THURSDAY)));
    dayOfWeekOptions.add(new LabelValueBean(frid, String.valueOf(Calendar.FRIDAY)));
    dayOfWeekOptions.add(new LabelValueBean(satd, String.valueOf(Calendar.SATURDAY)));
    dayOfWeekOptions.add(new LabelValueBean(sund, String.valueOf(Calendar.SUNDAY)));

    //time of day
    String startTime = request.getParameter("startTime") != null ? request.getParameter("startTime") : "9";
    String endTime = request.getParameter("endTime") != null ? request.getParameter("endTime") : "17";
    List<LabelValueBean> startTimeOfDayOptions = new ArrayList<LabelValueBean>();
    startTimeOfDayOptions.add(new LabelValueBean("00:00", "0"));
    for (int x = 1; x <= 9; x++) {
        startTimeOfDayOptions.add(new LabelValueBean("0" + String.valueOf(x) + ":00", String.valueOf(x)));
    }
    for (int x = 10; x <= 23; x++) {
        startTimeOfDayOptions.add(new LabelValueBean(String.valueOf(x) + ":00", String.valueOf(x)));
    }
    List<LabelValueBean> endTimeOfDayOptions = new ArrayList<LabelValueBean>();
    for (int x = 1; x <= 9; x++) {
        endTimeOfDayOptions.add(new LabelValueBean("0" + String.valueOf(x) + ":00", String.valueOf(x)));
    }
    for (int x = 10; x <= 24; x++) {
        endTimeOfDayOptions.add(new LabelValueBean(String.valueOf(x) + ":00", String.valueOf(x)));
    }


    //code
    String code = request.getParameter("code") != null ? request.getParameter("code") : "";
    ScheduleTemplateCodeDao scheduleTemplateCodeDao = (ScheduleTemplateCodeDao) SpringUtils.getBean(ScheduleTemplateCodeDao.class);
    List<ScheduleTemplateCode> codes = scheduleTemplateCodeDao.findAll();

    //numberOfResults
    String numberOfResults = request.getParameter("numberOfResults") != null ? request.getParameter("numberOfResults") : "3";
    List<LabelValueBean> numberOfResultsOptions = new ArrayList<LabelValueBean>();
    for (int x = 1; x <= 10; x++) {
        numberOfResultsOptions.add(new LabelValueBean(String.valueOf(x), String.valueOf(x)));
    }

    List<NextAppointmentSearchResult> results = null;
    String method = request.getParameter("method");
    if (method != null && method.equals("search")) {
        NextAppointmentSearchBean searchBean = new NextAppointmentSearchBean();
        searchBean.setProviderNo(providerNo);
        searchBean.setDayOfWeek(dayOfWeek);
        searchBean.setStartTimeOfDay(startTime);
        searchBean.setEndTimeOfDay(endTime);
        searchBean.setCode(code);
        searchBean.setNumResults(Integer.parseInt(numberOfResults));
        results = NextAppointmentSearchHelper.search(searchBean);
    }

%>


<!DOCTYPE html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><fmt:message key="appointment.searchnext.title"/></title>

    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>

 <!--   The global-head.jspf fragment provides:
        - Viewport meta tag for responsive design
        - global.js (legacy focus/refresh helpers)
        - jQuery 3.7.1
        - Bootstrap 5.3.3 (JS bundle + CSS)
        - jQuery UI 1.14.2 CSS (JS must be included page-specifically where dialogs/widgets are needed)
        - Font Awesome 6.7.2 (icon library)
        - searchBox.css (shared search/form styles)
        - global.css (CARLOS design tokens and common classes)
    -->

    <script>
        function popupPage2(varpage, windowname, vheight, vwidth) {
            // Provide default values for windowname, vheight, and vwidth incase popupPage2
            // is called with only 1 or 2 arguments (must always specify varpage)
            windowname = typeof (windowname) != 'undefined' ? windowname : 'apptProviderSearch';
            vheight = typeof (vheight) != 'undefined' ? vheight : '700px';
            vwidth = typeof (vwidth) != 'undefined' ? vwidth : '1024px';
            var page = "" + varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=0,left=0";
            var popup = window.open(page, windowname, windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
                popup.focus();
            }
        }

        function selectSlot(providerNo, year, month, day, startTime, endTime, duration) {
            var queryString = '<%=request.getContextPath()%>/appointment/addappointment?provider_no=' + providerNo + '&year=' + year + '&month=' + month + '&day=' + day + '&start_time=' + startTime + '&end_time=' + endTime + '&duration=' + duration;
            popupPage2(queryString, 'appointment', 800, 780);

        }

        function validate() {
            var startTime = parseInt(document.forms["searchForm"].elements["startTime"].value);
            var endTime = parseInt(document.forms["searchForm"].elements["endTime"].value);
            if (startTime >= endTime) {
                alert('Start time must be less than end time.');
                return false;
            }
            return true;
        }
    </script>
</head>
<body>

<!-- ================================================================
     CONTAINER — outermost wrapper; constrains max-width and centers
     content on large screens while staying full-width on mobile.
     ================================================================ -->
<div class="container">
<form name="searchForm" action="<%=request.getContextPath()%>/appointment/appointmentsearch" method="get"
      onsubmit="return validate()">
    <!-- ============================================================
         ALERT BANNER — hidden by default; shown via JavaScript when
         a server-side or client-side error needs to be surfaced.
         Usage: document.getElementById('jsAlertText').textContent = msg;
                document.getElementById('jsAlertBanner').style.display = '';
         ============================================================ -->
    <div id="jsAlertBanner"
         class="alert alert-danger alert-dismissible"
         style="display:none"
         role="alert">
        <span id="jsAlertText"></span>
        <button type="button"
                class="btn-close"
                onclick="this.closest('.alert').style.display='none'"
                aria-label="Close"></button>
    </div>

    <!-- ============================================================
         PAGE HEADER BAR — short title + long title (icon optional).
         Mirrors the OSCAR MainTableTopRow / TopStatusBar pattern.
         Structure:
           [icon?] [Short Title]   [Long Title .....................]
         ============================================================ -->
    <div class="page-header-bar d-flex align-items-center justify-content-between
                py-2 mb-3 border-bottom" id="header">
        <div class="d-flex align-items-center gap-2">
            <!--
                Optional Fontawesome Icon — replace "bi-file-earmark-text"
                with any icon from https://icons.getbootstrap.com/
                or remove the <i> tag entirely if no icon is needed.
            -->
            <i class="fa-solid fa-magnifying-glass"></i>
            <span class="fw-semibold"><fmt:message key="appointment.searchnext.2ndtitle"/></span>
        </div>
        <div class="text-muted small"></div>
    </div>

    <!-- ============================================================
         MAIN CONTENT WRAPPER — light background card to separate
         page content from the body background.
         ============================================================ -->
    <div class="bg-light border rounded p-2">

         <input type="hidden" name="method" value="search"/>

            <!-- ==================================================
                 CONTENT ROW — two-column layout:
                   col-12 col-md-2 : left sidebar  (OSCAR left col)
                   col-12 col-md-10: right content (OSCAR right col)
                 On small screens both columns stack vertically.
                 ================================================== -->
            <div class="row g-2">

                <!-- LEFT SIDEBAR COLUMN
                     Mirrors: MainTableLeftColumn
                     Contains navigation links / contextual actions. -->
                <div class="col-12 col-md-2">
                    <nav class="d-flex flex-column gap-1" aria-label="Sidebar navigation">
                    </nav>
                </div>

                <!-- RIGHT CONTENT COLUMN
                     Mirrors: MainTableRightColumn
                     Contains the primary form and data entry area. -->
                <div class="col-12 col-md-10">

                    <div class="row mb-3">
                        <label for="provider_no" class="col-sm-4 col-form-label">
                            <fmt:message key="appointment.searchnext.provider"/>:
                        </label>
                        <select name="provider_no" id="provider_no" class="form-control form-select w-50">
                            <option value=""><fmt:message key="provider.appointmentprovideradminmonth.formAllProviders"/></option>
                            <%
                                for (Provider provider : providers) {
                                    String selected = "";
                                    if (providerNo.equals(provider.getProviderNo())) {
                                        selected = " selected=\"selected\" ";
                                    }
                            %>
                            <option value="<%= SafeEncode.forHtmlAttribute(provider.getProviderNo()) %>" <%= selected %>><%= SafeEncode.forHtml(provider.getFormattedName()) %>
                            </option>
                            <%
                                }
                            %>
                        </select>
                    </div>

                    <div class="row mb-3">
                         <label for="dayOfWeek" class="col-sm-4 col-form-label">
                            <fmt:message key="appointment.searchnext.day_of_week"/>:
                        </label>
                        <select name="dayOfWeek" id="dayOfWeek" class="form-select w-50">
                            <%
                                for (LabelValueBean lvb : dayOfWeekOptions) {
                                    String selected = new String();
                                    if (lvb.getValue().equals(dayOfWeek)) {
                                        selected = " selected=\"selected\" ";
                                    }
                            %>
                            <option value="<%=lvb.getValue()%>" <%=selected%>><%=lvb.getLabel()%>
                            </option>
                            <%
                                }
                            %>
                        </select>
                    </div>

                    <div class="row mb-3">
                         <label for="startTime" class="col-sm-4 col-form-label">
                            <fmt:message key="appointment.searchnext.time_of_day"/>:
                        </label>

                        <select name="startTime" id="startTime" class="form-select w-25">
                            <%
                                for (LabelValueBean lvb : startTimeOfDayOptions) {
                                    String selected = new String();
                                    if (lvb.getValue().equals(startTime)) {
                                        selected = " selected=\"selected\" ";
                                    }
                            %>
                            <option value="<%=lvb.getValue()%>" <%=selected%>><%=lvb.getLabel()%>
                            </option>
                            <%
                                }
                            %>
                        </select>
                        &nbsp;<fmt:message key="appointment.searchnext.to"/>&nbsp;
                        <select name="endTime" id="endTime"  class="form-select w-25">
                            <%
                                for (LabelValueBean lvb : endTimeOfDayOptions) {
                                    String selected = new String();
                                    if (lvb.getValue().equals(endTime)) {
                                        selected = " selected=\"selected\" ";
                                    }
                            %>
                            <option value="<%=lvb.getValue()%>" <%=selected%>><%=lvb.getLabel()%>
                            </option>
                            <%
                                }
                            %>
                        </select>
                    </div>

                    <div class="row mb-3">
                         <label for="code" class="col-sm-4 col-form-label">
                            <fmt:message key="appointment.searchnext.appt_type"/>:
                        </label>
                        <select name="code" id="code" class="form-select w-50">
                            <option value=""><fmt:message key="SearchDrug.searchParam.any"/></option>
                            <%
                                for (ScheduleTemplateCode c : codes) {
                                    String selected = "";
                                    if (String.valueOf(c.getCode()).equals(code)) {
                                        selected = " selected=\"selected\" ";
                                    }
                            %>
                            <option value="<%= SafeEncode.forHtmlAttribute(String.valueOf(c.getCode())) %>" <%=selected%>><%= SafeEncode.forHtml(String.valueOf(c.getCode())) %> - <%= SafeEncode.forHtml(c.getDescription()) %>
                            </option>
                            <%
                                }
                            %>
                        </select>
                    </div>
                    
                    <div class="row mb-3">
                         <label for="numberOfResults" class="col-sm-4 col-form-label">
                            <fmt:message key="appointment.searchnext.num_results"/>:
                        </label>
                        <select name="numberOfResults" id="numberOfResults" class="form-select w-50">
                            <%
                                for (LabelValueBean lvb : numberOfResultsOptions) {
                                    String selected = new String();
                                    if (lvb.getValue().equals(numberOfResults)) {
                                        selected = " selected=\"selected\" ";
                                    }
                            %>
                            <option value="<%=lvb.getValue()%>" <%=selected%>><%=lvb.getLabel()%>
                            </option>
                            <%
                                }
                            %>
                        </select>
                    </div>

                    <!-- Primary action -->
                    <div class="mb-2">
                        <button type="submit" class="btn btn-primary btn-sm">
                            <fmt:message key="global.btnSubmit"/>
                        </button>
                        &nbsp;&nbsp;
                        <button type="button" class="btn btn-secondary btn-sm" onclick="window.close();window.opener.location.reload();">
                            <fmt:message key="global.btnBack"/>
                        </button>
                    </div>
                    
                    <%if (results != null) { %>
                    <br>
                    <table style="width:100%" class="table table-hover">
                        <tr>
                            <th style="width:20%"><fmt:message key="appointment.searchnext.date"/></th>
                            <th style="width:20%"><fmt:message key="appointment.searchnext.time"/></th>
                            <th style="width:60%"><fmt:message key="appointment.searchnext.provider"/></th>
                        </tr>
                        <%
                            for (int x = 0; x < Math.min(results.size(), Integer.parseInt(numberOfResults)); x++) {
                                NextAppointmentSearchResult result = results.get(x);
                        %>
                        <tr
                         onclick="selectSlot('<%= SafeEncode.forJavaScript(result.getProviderNo()) %>','<%= result.getYear() %>','<%= result.getMonth() %>','<%= result.getDay() %>','<%= SafeEncode.forJavaScript(result.getStartTime()) %>','<%= SafeEncode.forJavaScript(result.getEndTime()) %>','<%= result.getDuration() %>');">
                            <td><%= dayFormatter.format(result.getDate()) %>
                            </td>
                            <td><%= timeFormatter.format(result.getDate()) %>
                            </td>
                            <td><%= SafeEncode.forHtml(result.getProvider().getFormattedName()) %>
                            </td>
                        </tr>
                        <% } %>
                    </table>
                    <% } %>
                    <br>
                </div><!-- end right column -->
            </div><!-- end .row -->

        </form>

    </div><!-- end .bg-light -->

</div><!-- end .container -->

</body>
</html>
