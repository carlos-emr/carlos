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
<!DOCTYPE html>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="ca.openosp.openo.commn.dao.MyGroupAccessRestrictionDao" %>
<%@ page import="ca.openosp.openo.commn.dao.UserPropertyDAO" %>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@ page import="ca.openosp.openo.commn.dao.SiteDao" %>
<%@ page import="ca.openosp.openo.commn.dao.MyGroupDao" %>
<%@ page import="ca.openosp.openo.commn.dao.ScheduleTemplateCodeDao" %>
<%@ page import="ca.openosp.openo.commn.dao.ScheduleDateDao" %>
<%@ page import="ca.openosp.openo.commn.dao.ProviderSiteDao" %>
<%@ page import="ca.openosp.openo.commn.dao.OscarAppointmentDao" %>
<%@ page import="ca.openosp.openo.PMmodule.model.ProgramProvider" %>

<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.utility.MiscUtils" %>
<%@ page import="ca.openosp.openo.utility.SessionConstants" %>
<%@page import="ca.openosp.openo.util.*" %>
<%@page import="ca.openosp.openo.commn.dao.SiteDao" %>
<%@page import="ca.openosp.openo.commn.model.Site" %>
<%@page import="ca.openosp.openo.web.admin.ProviderPreferencesUIBean" %>
<%@page import="ca.openosp.openo.commn.model.ProviderPreference" %>
<%@ page import="ca.openosp.openo.managers.*" %>
<%@ page import="java.util.*,java.text.*,java.net.*,ca.openosp.*,ca.openosp.openo.util.*" %>
<%@ page import="org.apache.commons.lang3.*" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.apache.commons.text.WordUtils" %>
<%@ page import="ca.openosp.openo.commn.model.*" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.managers.*" %>
<%@ page import="ca.openosp.openo.util.UtilMisc" %>
<%@ page import="ca.openosp.openo.util.UtilDateUtilities" %>
<%@ page import="ca.openosp.openo.util.ConversionUtils" %>
<%@ page import="ca.openosp.openo.commn.model.*" %>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/special_tag.tld" prefix="special" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<!-- Struts for i18n -->
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%
    LoggedInInfo loggedInInfo1 = LoggedInInfo.getLoggedInInfoFromSession(request);

    if (session.getAttribute("userrole") == null) {
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
    }

    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_appointment,_day" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_appointment");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_appointment" rights="r" reverse="<%=true%>">
    <%
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
    %>
</security:oscarSec>

<%
    boolean isSiteAccessPrivacy = false;
    boolean isTeamAccessPrivacy = false;
    MyGroupAccessRestrictionDao myGroupAccessRestrictionDao = SpringUtils.getBean(MyGroupAccessRestrictionDao.class);
%>
<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false">
    <%
        isSiteAccessPrivacy = true;
    %>
</security:oscarSec>
<security:oscarSec objectName="_team_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false">
    <%
        isTeamAccessPrivacy = true;
    %>
</security:oscarSec>

<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<jsp:useBean id="as" class="ca.openosp.openo.appt.ApptStatusData"/>
<jsp:useBean id="dateTimeCodeBean" class="java.util.HashMap"/>

<c:set var="rand"><%= java.lang.Math.round(java.lang.Math.random() * 2345) %>
</c:set>

<%!
    SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    UserPropertyDAO userPropertyDao = SpringUtils.getBean(UserPropertyDAO.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    SiteDao siteDao = SpringUtils.getBean(SiteDao.class);
    MyGroupDao myGroupDao = SpringUtils.getBean(MyGroupDao.class);
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    ScheduleTemplateCodeDao scheduleTemplateCodeDao = SpringUtils.getBean(ScheduleTemplateCodeDao.class);
    ScheduleDateDao scheduleDateDao = SpringUtils.getBean(ScheduleDateDao.class);
    ProviderSiteDao providerSiteDao = SpringUtils.getBean(ProviderSiteDao.class);
    OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
    LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);
    Map<Integer, LookupListItem> reasonCodesMap = new HashMap<>();
    OscarProperties oscarVariables = OscarProperties.getInstance();
    AppManager appManager = SpringUtils.getBean(AppManager.class);
    String resourcehelpHtml = "";
    UserProperty rbuHtml = userPropertyDao.getProp("resource_helpHtml");
    ca.openosp.openo.managers.PreventionManager providerPreventionManager = SpringUtils.getBean(ca.openosp.openo.managers.PreventionManager.class);
%>
<%
    if (rbuHtml != null) {
        resourcehelpHtml = rbuHtml.getValue();
    }
    LookupList reasonCodes = lookupListManager.findLookupListByName(loggedInInfo1, "reasonCode");
    for (LookupListItem lli : reasonCodes.getItems()) {
        reasonCodesMap.put(lli.getId(), lli);
    }

    // are prevention stop sign icons being loaded? This needs to be known when loading the schedule
    pageContext.setAttribute("isPreventionWarningDisabled", providerPreventionManager.isDisabled());
%>
<%!
    //multisite starts =====================
    private boolean bMultisites = ca.openosp.openo.commn.IsPropertiesOn.isMultisitesEnable();
    private List<Site> sites = new ArrayList<Site>();
    private List<Site> curUserSites = new ArrayList<Site>();
    private List<String> siteProviderNos = new ArrayList<String>();
    private List<String> siteGroups = new ArrayList<String>();
    private String selectedSite = null;
    private HashMap<String, String> siteBgColor = new HashMap<String, String>();
    private HashMap<String, String> CurrentSiteMap = new HashMap<String, String>();
%>
<%
    if (bMultisites) {
        sites = siteDao.getAllActiveSites();
        selectedSite = (String) session.getAttribute("site_selected");

        if (selectedSite != null) {
            //get site providers list
            siteProviderNos = siteDao.getProviderNoBySiteLocation(selectedSite);
            siteGroups = siteDao.getGroupBySiteLocation(selectedSite);
        }

        if (isSiteAccessPrivacy || isTeamAccessPrivacy) {
            String siteManagerProviderNo = (String) session.getAttribute("user");
            curUserSites = siteDao.getActiveSitesByProviderNo(siteManagerProviderNo);
            if (selectedSite == null) {
                siteProviderNos = siteDao.getProviderNoBySiteManagerProviderNo(siteManagerProviderNo);
                siteGroups = siteDao.getGroupBySiteManagerProviderNo(siteManagerProviderNo);
            }
        } else {
            curUserSites = sites;
        }

        for (Site s : curUserSites) {
            CurrentSiteMap.put(s.getName(), "Y");
        }

        //get all sites bgColors
        for (Site st : sites) {
            siteBgColor.put(st.getName(), st.getBgColor());
        }
    }
    //multisite ends =======================
%>

<!-- caisi infirmary view extension add -->
<caisi:isModuleLoad moduleName="caisi">
    <%
        if (request.getParameter("year") != null && request.getParameter("month") != null && request.getParameter("day") != null) {
            java.util.Date infirm_date = new java.util.GregorianCalendar(Integer.valueOf(request.getParameter("year")).intValue(), Integer.valueOf(request.getParameter("month")).intValue() - 1, Integer.valueOf(request.getParameter("day")).intValue()).getTime();
            session.setAttribute("infirmaryView_date", infirm_date);

        } else {
            session.setAttribute("infirmaryView_date", null);
        }
        String reqstr = request.getQueryString();
        if (reqstr == null) {
            //Hack:: an unknown bug of struts or JSP causing the queryString to be null
            String year_q = request.getParameter("year");
            String month_q = request.getParameter("month");
            String day_q = request.getParameter("day");
            String view_q = request.getParameter("view");
            String displayMode_q = request.getParameter("displaymode");
            reqstr = "year=" + year_q + "&month=" + month_q
                    + "&day=" + day_q + "&view=" + view_q + "&displaymode=" + displayMode_q;
        }
        session.setAttribute("infirmaryView_OscarQue", reqstr);
    %>
</caisi:isModuleLoad>

<%
    ProviderPreference providerPreference = ProviderPreferencesUIBean.getProviderPreference(loggedInInfo1.getLoggedInProviderNo());

    String mygroupno = providerPreference.getMyGroupNo();
    if (mygroupno == null) {
        mygroupno = ".default";
    }
    String caisiView = request.getParameter("GoToCaisiViewFromOscarView");
    boolean notOscarView = "false".equals(session.getAttribute("infirmaryView_isOscar"));
    if ((caisiView != null && "true".equals(caisiView)) || notOscarView) {
        mygroupno = ".default";
    }
    String userfirstname = (String) session.getAttribute("userfirstname");
    String userlastname = (String) session.getAttribute("userlastname");
    String prov = (oscarVariables.getProperty("billregion", "")).trim().toUpperCase();

    int startHour = providerPreference.getStartHour();
    int endHour = providerPreference.getEndHour();
    int everyMin = providerPreference.getEveryMin();
    String defaultServiceType = (String) session.getAttribute("default_servicetype");

    if (defaultServiceType == null && providerPreference != null) {
        defaultServiceType = providerPreference.getDefaultServiceType();
    }

    if (defaultServiceType == null) {
        defaultServiceType = "";
    }

    /*
     * Get all the forms, eforms, and quicklinks that the logged in providers
     * needs to see in all the appointment entries
     */

    Collection<ProviderPreference.QuickLink> quickLinkCollection = providerPreference.getAppointmentScreenQuickLinks();
    Collection<String> formNameCollection = providerPreference.getAppointmentScreenForms();
    List<String> formNamesList = new ArrayList<>(formNameCollection);
    Collections.sort(formNamesList);
    Collection<ProviderPreference.EformLink> eFormIdCollection = providerPreference.getAppointmentScreenEForms();

    pageContext.setAttribute("truncateLimit", providerPreference.getAppointmentScreenLinkNameDisplayLength());
    pageContext.setAttribute("quickLinksList", quickLinkCollection);
    pageContext.setAttribute("formNamesList", formNamesList);
    pageContext.setAttribute("eFormsList", eFormIdCollection);

    StringBuilder eformIds = new StringBuilder();
    for (ProviderPreference.EformLink eform : eFormIdCollection) {
        eformIds = eformIds.append("&eformId=" + eform.getAppointmentScreenEForm());
    }

    StringBuilder ectFormNames = new StringBuilder();
    for (String formName : formNamesList) {
        ectFormNames = ectFormNames.append("&encounterFormName=" + formName);
    }
    // end get eform form links

    boolean prescriptionQrCodes = providerPreference.isPrintQrCodeOnPrescriptions();
    boolean erx_enable = providerPreference.isERxEnabled();
    boolean erx_training_mode = providerPreference.isERxTrainingMode();

    boolean bShortcutIntakeForm = oscarVariables.getProperty("appt_intake_form", "").equalsIgnoreCase("on") ? true : false;

    String newticklerwarningwindow = null;
    String default_pmm = null;
    String programId_oscarView = null;
//    String ocanWarningWindow = null;
//    String cbiReminderWindow = null;
    String caisiBillingPreferenceNotDelete = null;
//    String tklerProviderNo = null;

//    UserProperty userprop = userPropertyDao.getProp(curUser_no, UserProperty.PROVIDER_FOR_TICKLER_WARNING);
//    if (userprop != null) {
//        tklerProviderNo = userprop.getValue();
//    } else {
//        tklerProviderNo = curUser_no;
//    }
//
//    if (ca.openosp.openo.commn.IsPropertiesOn.isCaisiEnable() && ca.openosp.openo.commn.IsPropertiesOn.propertiesOn("OCAN_warning_window")) {
//        ocanWarningWindow = (String) session.getAttribute("ocanWarningWindow");
//    }
//
//    if (ca.openosp.openo.commn.IsPropertiesOn.isCaisiEnable() && ca.openosp.openo.commn.IsPropertiesOn.propertiesOn("CBI_REMINDER_WINDOW")) {
//        cbiReminderWindow = (String) session.getAttribute("cbiReminderWindow");
//    }

    //Hide old echart link
    boolean showOldEchartLink = true;
    UserProperty oldEchartLink = userPropertyDao.getProp(loggedInInfo1.getLoggedInProviderNo(), UserProperty.HIDE_OLD_ECHART_LINK_IN_APPT);
    if (oldEchartLink != null && "Y".equals(oldEchartLink.getValue())) showOldEchartLink = false;

    programId_oscarView = "0";
    session.setAttribute("programId_oscarView", programId_oscarView);
    int lenLimitedL = 11; //L - long
    if (OscarProperties.getInstance().getProperty("APPT_SHOW_FULL_NAME", "").equalsIgnoreCase("true")) {
        lenLimitedL = 25;
    }
    int lenLimitedS = 3; //S - short
    int len = lenLimitedL;
    int view = request.getParameter("view") != null ? Integer.parseInt(request.getParameter("view")) : 0; //0-multiple views, 1-single view
    //// THIS IS THE VALUE I HAVE BEEN LOOKING FOR!!!!!
    boolean bDispTemplatePeriod = (oscarVariables.getProperty("receptionist_alt_view") != null && oscarVariables.getProperty("receptionist_alt_view").equals("yes")); // true - display as schedule template period, false - display as preference

    String tickler_no = "", textColor = "", tickler_note = "";
    String ver = "", roster = "";
    String yob = "";
    String mob = "";
    String dob = "";
    String demBday = "";

    // List of statuses that are excluded from the schedule appointment count for each providers
    List<String> noCountStatus = Arrays.asList("C", "CS", "CV", "N", "NS", "NV");

    String resourcebaseurl = oscarVariables.getProperty("resource_base_url");

    UserProperty rbu = userPropertyDao.getProp("resource_baseurl");
    if (rbu != null) {
        resourcebaseurl = rbu.getValue();
    }

    boolean isWeekView = false;
    String provNum = request.getParameter("provider_no");
    if (provNum != null) {
        isWeekView = true;
    }
    if (caisiView != null && "true".equals(caisiView)) {
        isWeekView = false;
    }
    int nProvider;

    boolean caseload = "1".equals(request.getParameter("caseload"));

    GregorianCalendar cal = new GregorianCalendar();
    int curYear = cal.get(Calendar.YEAR);
    int curMonth = (cal.get(Calendar.MONTH) + 1);
    int curDay = cal.get(Calendar.DAY_OF_MONTH);

    // Retrieve the 'year' parameter from the request and parse it into an integer
    // If the parameter is null or empty, default to the current year
    int year = Optional.ofNullable(request.getParameter("year"))
                   .filter(param -> !param.isEmpty())
                   .map(Integer::parseInt)
                   .orElse(curYear);

    // Retrieve the 'month' parameter from the request and parse it into an integer
    // If the parameter is null or empty, default to the current month
    int month = Optional.ofNullable(request.getParameter("month"))
                        .filter(param -> !param.isEmpty())
                        .map(Integer::parseInt)
                        .orElse(curMonth);

    // Retrieve the 'day' parameter from the request and parse it into an integer
    // If the parameter is null or empty, default to the current day
    int day = Optional.ofNullable(request.getParameter("day"))
                    .filter(param -> !param.isEmpty())
                    .map(Integer::parseInt)
                    .orElse(curDay);

    //verify the input date is really existed
    cal = new GregorianCalendar(year, (month - 1), day);
    boolean weekendsEnabled = true;
    int weekViewDays = 7;
    if (isWeekView) {
        UserProperty weekViewWeekendProp = userPropertyDao.getProp(loggedInInfo1.getLoggedInProviderNo(), UserProperty.SCHEDULE_WEEK_VIEW_WEEKENDS);
        if (weekViewWeekendProp != null && StringUtils.trimToNull(weekViewWeekendProp.getValue()) != null) {
            weekendsEnabled = Boolean.parseBoolean(weekViewWeekendProp.getValue());
        }
        weekViewDays = weekendsEnabled ? 7 : 5;
        cal.add(Calendar.DATE, -(cal.get(Calendar.DAY_OF_WEEK) - (weekendsEnabled ? 1 : 2)));
    }
    int week = cal.get(Calendar.WEEK_OF_YEAR);
    year = cal.get(Calendar.YEAR);
    month = (cal.get(Calendar.MONTH) + 1);
    day = cal.get(Calendar.DAY_OF_MONTH);

    String strDate = year + "-" + month + "-" + day;
    String monthDay = String.format("%02d", month) + "-" + String.format("%02d", day);
    SimpleDateFormat inform = new SimpleDateFormat("yyyy-MM-dd", request.getLocale());
    String formatDate;
    try {
        java.util.ResourceBundle prop = ResourceBundle.getBundle("oscarResources", request.getLocale());
        formatDate = UtilDateUtilities.DateToString(inform.parse(strDate), prop.getString("date.EEEyyyyMMdd"), request.getLocale());
    } catch (Exception e) {
        MiscUtils.getLogger().error("Error", e);
        formatDate = UtilDateUtilities.DateToString(inform.parse(strDate), "EEE, yyyy-MM-dd");
    }
    String strYear = "" + year;
    String strMonth = month > 9 ? ("" + month) : ("0" + month);
    String strDay = day > 9 ? ("" + day) : ("0" + day);


    Calendar apptDate = Calendar.getInstance();
    apptDate.set(year, month - 1, day);
    Calendar minDate = Calendar.getInstance();
    minDate.set(minDate.get(Calendar.YEAR), minDate.get(Calendar.MONTH), minDate.get(Calendar.DATE));
    String allowDay = "";
    if (apptDate.equals(minDate)) {
        allowDay = "Yes";
    } else {
        allowDay = "No";
    }
    minDate.add(Calendar.DATE, 7);
    String allowWeek = "";
    if (apptDate.before(minDate)) {
        allowWeek = "Yes";
    } else {
        allowWeek = "No";
    }
%>
<!-- page settings -->
<security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="r">
    <c:set var="billingRights" value="true" scope="page"/>
</security:oscarSec>
<security:oscarSec roleName="<%=roleName$%>" objectName="_appointment.doctorLink" rights="r">
    <c:set var="doctorLinkRights" value="true" scope="page"/>
</security:oscarSec>
<security:oscarSec roleName="<%=roleName$%>" objectName="_masterLink" rights="r">
    <c:set var="masterLinkRights" value="true" scope="page"/>
</security:oscarSec>

<fmt:setBundle basename="oscarResources"/>

<html>
    <head>
        <title><%=WordUtils.capitalize(userlastname + ", " + org.apache.commons.lang3.StringUtils.substring(userfirstname, 0, 1)) + "-"%><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.title"/></title>
        <script type="text/javascript" src="${pageContext.servletContext.contextPath}/js/global.js"></script>

        <link rel="stylesheet"
              href="${pageContext.servletContext.contextPath}/library/bootstrap/3.0.0/css/bootstrap.min.css"
              type="text/css">

        <!-- Determine which stylesheet to use: mobile-optimized or regular -->
        <%
            boolean isMobileOptimized = session.getAttribute("mobileOptimized") != null;
            if (isMobileOptimized) {
        %>
        <meta name="viewport"
              content="initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=no, width=device-width"/>
        <link rel="stylesheet" href="${pageContext.servletContext.contextPath}/mobile/receptionistapptstyle.css"
              type="text/css">
        <%
        } else {
        %>
        <link rel="stylesheet" href="${pageContext.servletContext.contextPath}/css/receptionistapptstyle.css?v=${rand}"
              type="text/css">
        <%
            }
        %>

        <%
            if (!caseload) {
        %>
        <c:if test="${empty sessionScope.archiveView or sessionScope.archiveView != true}">
            <%!String refresh = ca.openosp.OscarProperties.getInstance().getProperty("refresh.appointmentprovideradminday.jsp", "-1");%>
            <%="-1".equals(refresh) ? "" : "<meta http-equiv=\"refresh\" content=\"" + refresh + "\">"%>
        </c:if>
        <%
            }
        %>


        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/share/javascript/Oscar.js"></script>
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/share/javascript/prototype.js"></script>
        <link rel="stylesheet" type="text/css" media="all"
              href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui.theme-1.12.1.min.css"/>
        <link rel="stylesheet" type="text/css" media="all"
              href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui-1.12.1.min.css"/>
        <link rel="stylesheet" type="text/css" media="all"
              href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui.structure-1.12.1.min.css"/>
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/library/jquery/jquery-1.12.0.min.js"></script>

        <script>
            jQuery.noConflict();
        </script>

        <script type="text/javascript" src="<%= request.getContextPath() %>/provider/schedulePage.js.jsp"></script>

        <script type="text/javascript">

            document.addEventListener("DOMContentLoaded", () => {
                const birthdayCakes = document.getElementsByClassName('birthday-cake');
                for (let item in birthdayCakes) {
                    if (!birthdayCakes[item].dataset) {
                        continue;
                    }
                    if (birthdayCakes[item].dataset.month === birthdayCakes[item].dataset.bday) {
                        birthdayCakes[item].style.display = 'inline';
                    }
                }
            })

            function changeGroup(s) {
                var newGroupNo = s.options[s.selectedIndex].value;
                if (newGroupNo.indexOf("_grp_") !== -1) {
                    newGroupNo = s.options[s.selectedIndex].value.substring(5);
                } else {
                    newGroupNo = s.options[s.selectedIndex].value;
                }
                var programId = 0;
                popupPage(10, 10, "${pageContext.request.contextPath}/provider/providercontrol.jsp?provider_no=<%=loggedInInfo1.getLoggedInProviderNo()%>&start_hour=<%=startHour%>&end_hour=<%=endHour%>&every_min=<%=everyMin%>&color_template=deepblue&dboperation=updatepreference&displaymode=updatepreference&default_servicetype=<%=defaultServiceType%>&prescriptionQrCodes=<%=prescriptionQrCodes%>&erx_enable=<%=erx_enable%>&erx_training_mode=<%=erx_training_mode%>&mygroup_no=" + newGroupNo + "&programId_oscarView=" + programId + "<%=eformIds.toString()%><%=ectFormNames.toString()%>");
            }

            function ts1(s) {
                popupPage(360, 780, ('<%= request.getContextPath() %>/appointment/addappointment.jsp?' + s));
            }

            function tsr(s) {
                popupPage(360, 780, ('<%= request.getContextPath() %>/appointment/appointmentcontrol.jsp?displaymode=edit&dboperation=search&' + s));
            }

            function goFilpView(s) {
                self.location.href = "<%= request.getContextPath() %>/schedule/scheduleflipview.jsp?originalpage=<%= request.getContextPath() %>/provider/providercontrol.jsp&startDate=<%=year+"-"+month+"-"+day%>" + "&provider_no=" + s;
            }

            function goWeekView(s) {
                self.location.href = "providercontrol.jsp?year=<%=year%>&month=<%=month%>&day=<%=day%>&view=0&displaymode=day&dboperation=searchappointmentday&viewall=1&provider_no=" + s;
            }

            function goZoomView(s, n) {
                self.location.href = "providercontrol.jsp?year=<%=strYear%>&month=<%=strMonth%>&day=<%=strDay%>&view=1&curProvider=" + s + "&curProviderName=" + encodeURIComponent(n) + "&displaymode=day&dboperation=searchappointmentday";
            }

            function findProvider(p, m, d) {
                popupPage(300, 400, "receptionistfindprovider.jsp?pyear=" + p + "&pmonth=" + m + "&pday=" + d + "&providername=" + document.findprovider.providername.value);
            }

            function goSearchView(s) {
                popupPage(600, 650, "<%= request.getContextPath() %>/appointment/appointmentsearch.jsp?provider_no=" + s);
            }

            function review(key) {
                if (self.location.href.lastIndexOf("?") > 0) {
                    if (self.location.href.lastIndexOf("&viewall=") > 0) a = self.location.href.substring(0, self.location.href.lastIndexOf("&viewall="));
                    else a = self.location.href;
                } else {
                    a = "providercontrol.jsp?year=" + document.jumptodate.year.value + "&month=" + document.jumptodate.month.value + "&day=" + document.jumptodate.day.value + "&view=0&displaymode=day&dboperation=searchappointmentday&site=" + "<%=(selectedSite==null? "none" : Encode.forJavaScriptBlock(selectedSite) )%>";
                }
                self.location.href = a + "&viewall=" + key;
            }


        </script>
        <style>
            .ds-btn {
                background-color: #f4ead7;
                border: 1px solid #0097cf;
            }
        </style>

        <%
            if (OscarProperties.getInstance().getBooleanProperty("indivica_hc_read_enabled", "true")) {
        %>
        <script src="${pageContext.servletContext.contextPath}/hcHandler/hcHandler.js"></script>
        <script src="${pageContext.servletContext.contextPath}/hcHandler/hcHandlerAppointment.js"></script>
        <link rel="stylesheet" href="${pageContext.servletContext.contextPath}/hcHandler/hcHandler.css"
              type="text/css"/>
        <%
            }
        %>

        <script src="${pageContext.request.contextPath}/csrfguard"></script>
    </head>
    <%
        if (ca.openosp.openo.commn.IsPropertiesOn.isCaisiEnable()) {
    %>
    <body onload="load();">
    <%
    } else {
    %>
    <body onLoad="refreshAllTabAlerts();scrollOnLoad();">
    <%
        }
    %>

    <%
        boolean isTeamScheduleOnly = false;
    %>
    <security:oscarSec roleName="<%=roleName$%>" objectName="_team_schedule_only" rights="r" reverse="false">
        <%
            isTeamScheduleOnly = true;
        %>
    </security:oscarSec>
    <%
        int numProvider = 0, numAvailProvider = 0;
        String[] curProvider_no;
        String[] curProviderName;
//initial providers bean for all the application
        if (providerBean.isEmpty()) {
            for (Provider p : providerDao.getActiveProviders()) {
                providerBean.setProperty(p.getProviderNo(), p.getFormattedName());
            }
        }

        String viewall = request.getParameter("viewall");
        if (viewall == null) {
            viewall = "0";
        }
        String _scheduleDate = strYear + "-" + strMonth + "-" + strDay;

        List<Map<String, Object>> resultList = null;

        //check if a maximum patient name length has ben configured for the user
        //if so, use that maximum length in all scenarios
        UserProperty uppatientNameLength = userPropertyDao.getProp(loggedInInfo1.getLoggedInProviderNo(), UserProperty.PATIENT_NAME_LENGTH);
        int NameLength = 0;
        if (uppatientNameLength != null && uppatientNameLength.getValue() != null) {
            try {
                NameLength = Integer.parseInt(uppatientNameLength.getValue());
            } catch (NumberFormatException e) {
                NameLength = 0;
            }
            if (NameLength > 0) {
                len = lenLimitedS = lenLimitedL = NameLength;
            }
        }

        if (mygroupno != null && providerBean.get(mygroupno) != null) { //single appointed providers view
            numProvider = 1;
            curProvider_no = new String[numProvider];
            curProviderName = new String[numProvider];
            curProvider_no[0] = mygroupno;

            curProviderName[0] = providerDao.getProvider(mygroupno).getFullName();

        } else {
            if (view == 0) { //multiple views
                if (selectedSite != null) {
                    numProvider = siteDao.site_searchmygroupcount(mygroupno, selectedSite).intValue();
                } else {
                    numProvider = myGroupDao.getGroupByGroupNo(mygroupno).size();
                }


                String[] param3 = new String[2];
                param3[0] = mygroupno;
                param3[1] = strDate; //strYear +"-"+ strMonth +"-"+ strDay ;
                numAvailProvider = 0;
                if (selectedSite != null) {
                    List<String> siteProviders = providerSiteDao.findByProviderNoBySiteName(selectedSite);
                    List<ScheduleDate> results = scheduleDateDao.search_numgrpscheduledate(mygroupno, ConversionUtils.fromDateString(strDate));

                    for (ScheduleDate result : results) {
                        if (siteProviders.contains(result.getProviderNo())) {
                            numAvailProvider++;
                        }
                    }
                } else {
                    numAvailProvider = scheduleDateDao.search_numgrpscheduledate(mygroupno, ConversionUtils.fromDateString(strDate)).size();

                }

                // _team_schedule_only does not support groups
                // As well, the mobile version only shows the schedule of the login providers.
                if (numProvider == 0 || isTeamScheduleOnly || isMobileOptimized) {
                    numProvider = 1; //the login user
                    curProvider_no = new String[]{loggedInInfo1.getLoggedInProviderNo()};  //[numProvider];
                    curProviderName = new String[]{(userlastname + ", " + userfirstname)}; //[numProvider];
                } else {
                    if (request.getParameter("viewall") != null && request.getParameter("viewall").equals("1")) {
                        if (numProvider >= 5) {
                            lenLimitedL = 2;
                            lenLimitedS = 3;
                        }
                    } else {
                        if (numAvailProvider >= 5) {
                            lenLimitedL = 2;
                            lenLimitedS = 3;
                        }
                        if (numAvailProvider == 2) {
                            lenLimitedL = 20;
                            lenLimitedS = 10;
                            len = 20;
                        }
                        if (numAvailProvider == 1) {
                            lenLimitedL = 30;
                            lenLimitedS = 30;
                            len = 30;
                        }
                    }
                    if (NameLength > 0) {
                        //if a custom maximum patient name has been set (NameLength variable)
                        //use that configured nameLength variable to override the length  
                        len = lenLimitedS = lenLimitedL = NameLength;
                    }
                    curProvider_no = new String[numProvider];
                    curProviderName = new String[numProvider];

                    int iTemp = 0;
                    if (selectedSite != null) {
                        List<String> siteProviders = providerSiteDao.findByProviderNoBySiteName(selectedSite);
                        List<MyGroup> results = myGroupDao.getGroupByGroupNo(mygroupno);
                        for (MyGroup result : results) {
                            if (siteProviders.contains(result.getId().getProviderNo())) {
                                curProvider_no[iTemp] = String.valueOf(result.getId().getProviderNo());

                                Provider p = providerDao.getProvider(curProvider_no[iTemp]);
                                if (p != null) {
                                    curProviderName[iTemp] = p.getFullName();
                                }
                                iTemp++;
                            }
                        }
                    } else {
                        List<MyGroup> results = myGroupDao.getGroupByGroupNo(mygroupno);
                        Collections.sort(results, MyGroup.MyGroupNoViewOrderComparator);

                        for (MyGroup result : results) {
                            curProvider_no[iTemp] = String.valueOf(result.getId().getProviderNo());

                            Provider p = providerDao.getProvider(curProvider_no[iTemp]);
                            if (p != null) {
                                curProviderName[iTemp] = p.getFullName();
                            }
                            iTemp++;
                        }
                    }


                }
            } else { //single view
                numProvider = 1;
                curProvider_no = new String[numProvider];
                curProviderName = new String[numProvider];
                curProvider_no[0] = request.getParameter("curProvider");
                curProviderName[0] = request.getParameter("curProviderName");
            }
        }

        //the view parameter controls how much information is displayed for each appointment such as the name and the E | M | R links. It is normally set in the URL parameter
        //an edge case has been identified where there's likely room to show more data, but because view is set to 0, it's not.
        //author did not feel confident that he could update all references correctly, without introducing regression.
        //instead, this next section of code explicitly sets view=1 (so more data) in the identified edge case

        //later in the code there is a complex inline ternary function that uses view as a parameter.
        //the following code "caches" the result of this function (as viewString) before setting view=1
        String curProviderString = request.getParameter("curProvider") != null ? "&curProvider=" + request.getParameter("curProvider") : "";
        String curProviderNameString = request.getParameter("curProviderName") != null ? "&curProviderName=" + URLEncoder.encode(request.getParameter("curProviderName"), StandardCharsets.UTF_8) : "";
        String viewString = view == 0 ? "&view=0" : "&view=1" + curProviderString + curProviderNameString;

        //Edge case: If the 'displaymode' is set to 'day' and 'viewall' is not equal to 1, then 'view' will be set to 1 to display all links.
        //This is believe to be a situation where there's only a single clinician displayed, so there should be enough space to display all of the data
        if ("day".equals(request.getParameter("displaymode")) && !"1".equals(request.getParameter("viewall"))) {
            view = 1;

            //when view=1, curProvider_no and curProviderName need to be set as well
            if (curProvider_no[0] == null || curProviderName[0] == null) {
                curProvider_no = new String[]{loggedInInfo1.getLoggedInProviderNo()};
                curProviderName = new String[]{(userlastname + ", " + userfirstname)};
            }
        }

//set timecode bean
        String bgcolordef = "#486ebd";
        String[] param3 = new String[2];
        param3[0] = strDate;
        for (nProvider = 0; nProvider < numProvider; nProvider++) {
            param3[1] = curProvider_no[nProvider];
            List<Object[]> results = scheduleDateDao.search_appttimecode(ConversionUtils.fromDateString(strDate), curProvider_no[nProvider]);
            for (Object[] result : results) {
                ScheduleTemplate st = (ScheduleTemplate) result[0];
                ScheduleDate sd = (ScheduleDate) result[1];
                dateTimeCodeBean.put(sd.getProviderNo(), st.getTimecode());
            }

        }

        for (ScheduleTemplateCode stc : scheduleTemplateCodeDao.findAll()) {

            dateTimeCodeBean.put("description" + stc.getCode(), stc.getDescription());
            dateTimeCodeBean.put("duration" + stc.getCode(), stc.getDuration());
            dateTimeCodeBean.put("color" + stc.getCode(), (stc.getColor() == null || "".equals(stc.getColor())) ? bgcolordef : stc.getColor());
            dateTimeCodeBean.put("confirm" + stc.getCode(), stc.getConfirm());
        }
    %>

        <%-- set if reasons will be shown by default or not. --%>
    <c:set value="false" var="hideReason" scope="page"/>
    <oscar:oscarPropertiesCheck property="SHOW_APPT_REASON" value="yes" defaultVal="no">
        <c:set value="true" var="hideReason" scope="page"/>
    </oscar:oscarPropertiesCheck>
    <input type="hidden" value="${ hideReason }" id="hideReason"/>
    <input type="hidden" value="${pageContext.servletContext.contextPath}" id="contextPath" />

    <table id="firstTable" class="noprint">
        <tr>

            <td id="firstMenu">
                <div class="icon-container">
                    <img alt="OSCAR EMR" src="<%=request.getContextPath()%>/images/oscar_logo_small.png" width="19px">
                </div>
                <ul id="navlist">
                    <c:if test="${infirmaryView_isOscar != 'false'}">
                        <% if (request.getParameter("viewall") != null && request.getParameter("viewall").equals("1")) { %>
                        <li>
                            <a href=# onClick="review('0')"
                               title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewProvAval"/>">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.schedView"/>
                            </a>
                        </li>
                        <% } else { %>
                        <li>
                            <a href='providercontrol.jsp?year=<%=curYear%>&month=<%=curMonth%>&day=<%=curDay%>&view=0&displaymode=day&dboperation=searchappointmentday&viewall=1'>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.schedView"/>
                            </a>
                        </li>
                        <% } %>
                    </c:if>

                    <li>
                        <a href='providercontrol.jsp?year=<%=curYear%>&month=<%=curMonth%>&day=<%=curDay%>&view=0&displaymode=day&dboperation=searchappointmentday&caseload=1&clProv=<%=loggedInInfo1.getLoggedInProviderNo()%>'><fmt:setBundle basename="oscarResources"/><fmt:message key="global.caseload"/></a>
                    </li>

                    <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                        <security:oscarSec roleName="<%=roleName$%>" objectName="_resource" rights="r">
                            <li>
                                <a href="#" ONCLICK="popupPage2('<%=resourcebaseurl%>');return false;"
                                   title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewResources"/>"
                                   onmouseover="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewResources"/>';return true"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.clinicalResources"/></a>
                            </li>
                        </security:oscarSec>
                    </caisi:isModuleLoad>

                    <%
                        if (isMobileOptimized) {
                    %>
                    <!-- Add a menu button for mobile version, which opens menu contents when clicked on -->
                    <li id="menu"><a class="leftButton top" onClick="showHideItem('navlistcontents');">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="global.menu"/></a>
                        <ul id="navlistcontents" style="display:none;">
                            <% } %>

                            <security:oscarSec roleName="<%=roleName$%>" objectName="_search" rights="r">
                                <li id="search">
                                    <caisi:isModuleLoad moduleName="caisi">
                                        <%
                                            String caisiSearch = oscarVariables.getProperty("caisi.search.workflow", "true");
                                            if ("true".equalsIgnoreCase(caisiSearch)) {
                                        %>
                                        <a href="<%= request.getContextPath() %>/PMmodule/ClientSearch2.do"
                                           TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.searchPatientRecords"/>'
                                           OnMouseOver="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.searchPatientRecords"/>' ; return true"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.search"/></a>

                                        <%
                                        } else {
                                        %>
                                        <a HREF="#" ONCLICK="popupPage2('<%= request.getContextPath() %>/demographic/search.jsp');return false;"
                                           TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.searchPatientRecords"/>'
                                           OnMouseOver="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.searchPatientRecords"/>' ; return true"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.search"/></a>
                                        <% } %>
                                    </caisi:isModuleLoad>
                                    <caisi:isModuleLoad moduleName="caisi" reverse="true">
                                        <a HREF="#" ONCLICK="popupPage2('<%= request.getContextPath() %>/demographic/search.jsp');return false;"
                                           TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.searchPatientRecords"/>'
                                           OnMouseOver="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.searchPatientRecords"/>' ; return true"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.search"/></a>
                                    </caisi:isModuleLoad>
                                </li>
                            </security:oscarSec>

                            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                                <security:oscarSec roleName="<%=roleName$%>" objectName="_report" rights="r">
                                    <li>
                                        <a HREF="#"
                                           ONCLICK="popupPage2('<%= request.getContextPath() %>/report/reportindex.jsp','reportPage');return false;"
                                           TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.genReport"/>'
                                           OnMouseOver="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.genReport"/>' ; return true"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.report"/></a>
                                    </li>
                                </security:oscarSec>
                                <oscar:oscarPropertiesCheck property="NOT_FOR_CAISI" value="no" defaultVal="true">

                                    <c:if test="${billingRights}">
                                        <li>
                                            <a HREF="#"
                                               ONCLICK="popupPage2('<%= request.getContextPath() %>/billing/CA/<%=prov%>/billingReportCenter.jsp?displaymode=billreport&providerview=<%=loggedInInfo1.getLoggedInProviderNo()%>');return false;"
                                               TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.genBillReport"/>'
                                               onMouseOver="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.genBillReport"/>';return true"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.billing"/></a>
                                        </li>
                                    </c:if>

                                    <c:if test="${doctorLinkRights}">
                                        <li>
                                       <a HREF="#" id="inboxLink">
                                                <span id="oscar_new_lab"><fmt:message key="global.lab"/></span>
                                            </a>
                                            <oscar:newUnclaimedLab>
                                                <a id="unclaimedLabLink" class="tabalert" HREF="javascript:void(0)"
                                                   title='<fmt:message key="provider.appointmentProviderAdminDay.viewLabReports"/>'>U</a>
                                            </oscar:newUnclaimedLab>
                                        </li>
                                    </c:if>
                                </oscar:oscarPropertiesCheck>

                            </caisi:isModuleLoad>

                                <%-- K2A link block removed --%>

                            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                                <security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="r">
                                    <li>
                                        <a HREF="#"
                                           ONCLICK="popupOscarRx(600,1024,'<%=request.getContextPath()%>/messenger/DisplayMessages.do?providerNo=<%=loggedInInfo1.getLoggedInProviderNo()%>&userName=<%=URLEncoder.encode(loggedInInfo1.getLoggedInProvider().getFirstName()+" "+loggedInInfo1.getLoggedInProvider().getLastName(), StandardCharsets.UTF_8)%>')"
                                           title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.messenger"/>">
                                            <span id="oscar_new_msg"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.msg"/></span></a>
                                    </li>
                                </security:oscarSec>
                            </caisi:isModuleLoad>
                            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                                <security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="r">
                                    <li id="con">
                                        <a HREF="#"
                                           ONCLICK="popupOscarRx(625,1024,'<%=request.getContextPath()%>/oscarEncounter/IncomingConsultation.do?providerNo=<%=loggedInInfo1.getLoggedInProviderNo()%>&userName=<%=URLEncoder.encode(loggedInInfo1.getLoggedInProvider().getFirstName()+" "+loggedInInfo1.getLoggedInProvider().getLastName(), StandardCharsets.UTF_8)%>')"
                                           title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewConReq"/>">
                                            <span id="oscar_aged_consults"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.con"/></span></a>
                                    </li>
                                </security:oscarSec>
                            </caisi:isModuleLoad>
                            <%
                                boolean hide_eConsult = OscarProperties.getInstance().isPropertyActive("hide_eConsult_link");
                                if ("on".equalsIgnoreCase(prov) && !hide_eConsult) {
                            %>
                            <li id="econ">
                                <a href="#" onclick="popupOscarRx(625, 1024, '<%=request.getContextPath()%>/oscarEncounter/econsult.do')"
                                   title="eConsult">
                                    <span>eConsult</span></a>
                            </li>
                            <% } %>

                            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                                <security:oscarSec roleName="<%=roleName$%>" objectName="_edoc" rights="r">
                                    <li>
                                        <a HREF="#"
                                           onclick="popup('700', '1024', '<%= request.getContextPath() %>/documentManager/documentReport.jsp?function=providers&functionid=<%=loggedInInfo1.getLoggedInProviderNo()%>&curUser=<%=loggedInInfo1.getLoggedInProviderNo()%>', 'edocView');"
                                           TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewEdoc"/>'><fmt:setBundle basename="oscarResources"/><fmt:message key="global.edoc"/></a>
                                    </li>
                                </security:oscarSec>
                            </caisi:isModuleLoad>
                            <security:oscarSec roleName="<%=roleName$%>" objectName="_tickler" rights="r">
                                <li>
                                    <a HREF="#"
                                       ONCLICK="popupPage2('<%= request.getContextPath() %>/tickler/ticklerMain.jsp','<fmt:setBundle basename="oscarResources"/><fmt:message key="global.tickler"/>');return false;"
                                       TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.tickler"/>'>
                                        <span id="oscar_new_tickler"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.btntickler"/></span></a>
                                </li>
                            </security:oscarSec>

                            <oscar:oscarPropertiesCheck property="referral_menu" value="yes">
                                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.misc" rights="r">
                                    <li id="ref">
                                        <a href="#"
                                           onclick="popupPage(550,800,'<%=request.getContextPath()%>/admin/ManageBillingReferral.do');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.manageReferrals"/></a>
                                    </li>
                                </security:oscarSec>
                            </oscar:oscarPropertiesCheck>

                            <oscar:oscarPropertiesCheck property="WORKFLOW" value="yes">
                                <li><a href="javascript:void(0)"
                                       onClick="popup(700,1024,'<%= request.getContextPath() %>/oscarWorkflow/WorkFlowList.jsp','<fmt:setBundle basename="oscarResources"/><fmt:message key="global.workflow"/>')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnworkflow"/>
                                </a></li>
                            </oscar:oscarPropertiesCheck>


                            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                                <security:oscarSec roleName="<%=roleName$%>"
                                                   objectName="_admin,_admin.userAdmin,_admin.schedule,_admin.billing,_admin.resource,_admin.reporting,_admin.backup,_admin.messenger,_admin.eform,_admin.encounter,_admin.misc,_admin.fax,_admin.flowsheet"
                                                   rights="r">

                                    <li id="admin2">
                                        <a href="javascript:void(0)" id="admin-panel" TITLE='Administration Panel'
                                           onclick="newWindow('<%=request.getContextPath()%>/administration/','admin')">Administration</a>
                                    </li>

                                </security:oscarSec>
                            </caisi:isModuleLoad>

                            <security:oscarSec roleName="<%=roleName$%>" objectName="_dashboardDisplay" rights="r">
                                <%
                                    DashboardManager dashboardManager = SpringUtils.getBean(DashboardManager.class);
                                    List<ca.openosp.openo.commn.model.Dashboard> dashboards = dashboardManager.getActiveDashboards(loggedInInfo1);
                                    pageContext.setAttribute("dashboards", dashboards);
                                %>

                                <li id="dashboardList">
                                    <div class="dropdown">
                                        <a href="#" class="dashboardBtn">Dashboard</a>
                                        <div class="dashboardDropdown">
                                            <ul>
                                                <c:forEach items="${ dashboards }" var="dashboard">
                                                    <li>
                                                        <a href="javascript:void(0)"
                                                           onclick="newWindow('<%=request.getContextPath()%>/web/dashboard/display/DashboardDisplay.do?method=getDashboard&dashboardId=${ dashboard.id }','dashboard')">
                                                            <c:out value="${ dashboard.name }"/>
                                                        </a>
                                                    </li>
                                                </c:forEach>
                                                <security:oscarSec roleName="<%=roleName$%>"
                                                                   objectName="_dashboardCommonLink" rights="r">
                                                    <li>
                                                        <a href="javascript:void(0)"
                                                           onclick="newWindow('<%=request.getContextPath()%>/web/dashboard/display/sharedOutcomesDashboard.jsp','shared_dashboard')">
                                                            Common Provider Dashboard
                                                        </a>
                                                    </li>
                                                </security:oscarSec>
                                            </ul>
                                        </div>

                                    </div>
                                </li>

                            </security:oscarSec>
                            <li id="helpLink">
                                <%if (resourcehelpHtml == "") { %>
                                <a href="javascript:void(0)"
                                   onClick="popupPage(600,750,'<%=resourcebaseurl%>')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.help"/></a>
                                <%} else {%>
                                <div id="help-link">
                                    <a href="javascript:void(0)"
                                       onclick="document.getElementById('helpHtml').style.display='block';document.getElementById('helpHtml').style.right='0px';"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.help"/></a>

                                    <div id="helpHtml">
                                        <div class="help-title">Help</div>

                                        <div class="help-body">

                                            <%=resourcehelpHtml%>
                                        </div>
                                        <a href="javascript:void(0)" class="help-close"
                                           onclick="document.getElementById('helpHtml').style.right='-280px';document.getElementById('helpHtml').style.display='none'">(X)</a>
                                    </div>

                                </div>
                                <%}%>
                            </li>
                            <% if (isMobileOptimized) { %>
                        </ul>
                    </li> <!-- end menu list for mobile-->
                    <% } %>

                </ul>  <!--- old TABLE -->

            </td>

            <td id="userSettings">
                <ul id="userSettingsMenu" style="display: flex; gap:5px;">
                    <li>
                        <a title="Scratch Pad" href="javascript: function myFunction() {return false; }"
                           onClick="popup(700,1024,'<%= request.getContextPath() %>/scratch/index.jsp','scratch')">
                            		<span class="glyphicon">
							<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor"
                                 class="bi bi-card-list" viewBox="0 0 16 16">
								<path d="M14.5 3a.5.5 0 0 1 .5.5v9a.5.5 0 0 1-.5.5h-13a.5.5 0 0 1-.5-.5v-9a.5.5 0 0 1 .5-.5zm-13-1A1.5 1.5 0 0 0 0 3.5v9A1.5 1.5 0 0 0 1.5 14h13a1.5 1.5 0 0 0 1.5-1.5v-9A1.5 1.5 0 0 0 14.5 2z"></path>
								<path d="M5 8a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7A.5.5 0 0 1 5 8m0-2.5a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5m0 5a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5m-1-5a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0M4 8a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0m0 2.5a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0"></path>
							</svg>
						</span>
                        </a>
                    </li>
                    <li>
                        <a href="javascript:void(0)" style="display: flex; align-items: flex-end;"
                           onClick="popupPage(715,680,'providerpreference.jsp?provider_no=<%=loggedInInfo1.getLoggedInProviderNo()%>')"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.msgSettings"/>'>
                            <span class="glyphicon">
                                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor"
                                     class="bi bi-person-fill" viewBox="0 0 16 16">
                                  <path d="M3 14s-1 0-1-1 1-4 6-4 6 3 6 4-1 1-1 1zm5-6a3 3 0 1 0 0-6 3 3 0 0 0 0 6"></path>
                                </svg>
						    </span>
                            <div>
                                <c:out value='<%= userfirstname + " " + userlastname %>'/>
                            </div>
                        </a>
                    </li>
                </ul>
                <div>
                    <a id="logoutButton" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnLogout"/>" href="<%= request.getContextPath() %>/logout.jsp">
                        <span class="glyphicon glyphicon-off"></span>
                    </a>
                </div>
            </td>

        </tr>
    </table>

    <script>
        jQuery(document).ready(function () {
            jQuery.get("<%=request.getContextPath()%>/SystemMessage.do?method=view", "html", function (data, textStatus) {
                jQuery("#system_message").html(data);
            });
            jQuery.get("<%=request.getContextPath()%>/FacilityMessage.do?method=view", "html", function (data, textStatus) {
                jQuery("#facility_message").html(data);
            });
        });
    </script>

    <div id="system_message"></div>
    <div id="facility_message"></div>
    <%
        if (caseload) {
    %>
    <jsp:include page="caseload.jspf"/>
    <%
    } else {
    %>

    <table id="scheduleNavigation">
        <tr id="ivoryBar">
            <td id="dateAndCalendar">
                <a class="redArrow"
                   href="providercontrol.jsp?year=<%=year%>&month=<%=month%>&day=<%=isWeekView?(day-7):(day-1)%><%=viewString%>&displaymode=day&dboperation=searchappointmentday<%=isWeekView?"&provider_no="+provNum:""%>&viewall=<%=viewall%>">
                    <span class="glyphicon glyphicon-step-backward"
                          title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewPrevDay"/>"></span>
                </a>
                <b><span class="dateAppointment"><%
                    if (isWeekView) {
                %><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.week"/> <%=week%><%
                } else {
                %><%=formatDate%><%
                    }
                %></span></b>
                <a class="redArrow"
                   href="providercontrol.jsp?year=<%=year%>&month=<%=month%>&day=<%=isWeekView?(day+7):(day+1)%><%=viewString%>&displaymode=day&dboperation=searchappointmentday<%=isWeekView?"&provider_no="+provNum:""%>&viewall=<%=viewall%>">
                    <span class="glyphicon glyphicon-step-forward"
                          title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewNextDay"/>"></span>
                </a>

                <%
                    String calendarUrl = request.getContextPath() + "/share/CalendarPopup.jsp?urlfrom=" + request.getContextPath() + "/provider/providercontrol.jsp" + "&year=" + strYear + "&month=" + strMonth + "&param=" + URLEncoder.encode("&view=0&displaymode=day&dboperation=searchappointmentday&viewall=" + viewall, "UTF-8");

                    if (isWeekView) {
                        calendarUrl += URLEncoder.encode("&provider_no=" + provNum, "UTF-8");
                    }
                %>

                <a id="calendarLink" href="#" onClick="popupPage(425,430,'<%=calendarUrl%>')">
                    <fmt:setBundle basename="oscarResources"/>
                    <fmt:message key="global.calendar"/>
                </a>

                <c:if test="${infirmaryView_isOscar != 'false'}">
                    <% if (request.getParameter("viewall") != null && request.getParameter("viewall").equals("1")) { %>
                    <u><a href=# onClick="review('0')"
                          title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewAllProv"/>"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.schedView"/></a></u>

                    <%} else {%>
                    <u><a href=# onClick="review('1')"
                          title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewAllProv"/>"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewAll"/></a></u>
                    <%}%>
                </c:if>

                <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_day" rights="r">
                        <a class="rightButton top"
                           href="providercontrol.jsp?year=<%=curYear%>&month=<%=curMonth%>&day=<%=curDay%><%=viewString%>&displaymode=day&dboperation=searchappointmentday"
                           TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewDaySched"/>'
                           OnMouseOver="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewDaySched"/>' ; return true"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.today"/></a>
                    </security:oscarSec>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_month" rights="r">

                        <a
                                href="providercontrol.jsp?year=<%=year%>&month=<%=month%>&day=1<%=viewString%>&displaymode=month&dboperation=searchappointmentmonth"
                                TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewMonthSched"/>'
                                OnMouseOver="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewMonthSched"/>' ; return true"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.month"/></a>

                    </security:oscarSec>

                </caisi:isModuleLoad>

                <%
                    boolean anonymousEnabled = false;
                    if (loggedInInfo1.getCurrentFacility() != null) {
                        anonymousEnabled = loggedInInfo1.getCurrentFacility().isEnableAnonymous();
                    }
                    if (anonymousEnabled) {
                %>
                &nbsp;&nbsp;(<a href="#" onclick="popupPage(710, 1024,
                '<%=(request.getContextPath() + "/PMmodule/createAnonymousClient.jsp")%>?programId=<%=(String)session.getAttribute(SessionConstants.CURRENT_PROGRAM_ID)%>');return false;">New
                Anon Client</a>)
                <%
                    }
                %>
                <%
                    boolean epe = false;
                    if (loggedInInfo1.getCurrentFacility() != null) {
                        epe = loggedInInfo1.getCurrentFacility().isEnablePhoneEncounter();
                    }
                    if (epe) {
                %>
                &nbsp;&nbsp;(<a href="#" onclick="popupPage(710, 1024,
                '<%=(request.getContextPath() + "/PMmodule/createPEClient.jsp")%>?programId=<%=(String)session.getAttribute(SessionConstants.CURRENT_PROGRAM_ID)%>');return false;">Phone
                Encounter</a>)
                <%
                    }
                %>
            </td>

            <td class="title noprint">

                <%
                    if (isWeekView) {
                        for (int provIndex = 0; provIndex < numProvider; provIndex++) {
                            if (curProvider_no[provIndex].equals(provNum)) {
                %>
                <%=Encode.forHtml(curProviderName[provIndex])%>
                <%
                            }
                        }
                    } %>

                    <%--                    else {--%>
                    <%--                    if (view == 1) {--%>
                    <%--                %>--%>
                    <%--                <a href='providercontrol.jsp?year=<%=strYear%>&month=<%=strMonth%>&day=<%=strDay%>&view=0&displaymode=day&dboperation=searchappointmentday'><fmt:message--%>
                    <%--                        key="providers.appointmentProviderAdminDay.grpView"/></a>--%>
                    <%--                <% } %>--%>
                    <%--&lt;%&ndash;                <% if (!isMobileOptimized) { %> <fmt:setBundle basename="oscarResources"/><fmt:message key="global.hello"/> <% } %>&ndash;%&gt;--%>
                    <%--&lt;%&ndash;                <% out.println(userfirstname + " " + userlastname); %>&ndash;%&gt;--%>
                    <%--                <%} %>--%>
            </td>


            <td id="group">

                <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                    <div>
                        <form method="post" name="findprovider"
                              onSubmit="findProvider(<%=year%>,<%=month%>,<%=day%>);return false;"
                              target="apptReception"
                              action="receptionistfindprovider.jsp">
                            <INPUT TYPE="text" NAME="providername" VALUE=""
                                   maxlength="10" class="noprint" title="Find a Provider" placeholder="Enter Lastname">
                            <INPUT TYPE="SUBMIT" NAME="Go"
                                   VALUE='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentprovideradminmonth.btnGo"/>'
                                   class="noprint" onClick="findProvider(<%=year%>,<%=month%>,<%=day%>);return false;">
                        </form>
                    </div>
                </caisi:isModuleLoad>
                <div>
                    <form name="appointmentForm">
                        <% if (isWeekView) { %>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.provider"/>:
                        <select name="provider_select" onChange="goWeekView(this.options[this.selectedIndex].value)">
                            <%
                                for (nProvider = 0; nProvider < numProvider; nProvider++) {
                            %>
                            <option value="<%=curProvider_no[nProvider]%>"<%=curProvider_no[nProvider].equals(provNum) ? " selected" : ""%>><%=curProviderName[nProvider]%>
                            </option>
                            <%
                                }
                            %>

                        </select>

                        <% } else { %>

                        <!-- caisi infirmary view extension add ffffffffffff-->
                        <caisi:isModuleLoad moduleName="caisi">
                        <table>
                            <tr>
                                <td align="right">
                                    <caisi:ProgramExclusiveView providerNo="<%=loggedInInfo1.getLoggedInProviderNo()%>"
                                                                value="appointment">
                                        <%
                                            session.setAttribute("infirmaryView_isOscar", "true");
                                        %>
                                    </caisi:ProgramExclusiveView>
                                    <caisi:ProgramExclusiveView providerNo="<%=loggedInInfo1.getLoggedInProviderNo()%>"
                                                                value="case-management">
                                        <%
                                            session.setAttribute("infirmaryView_isOscar", "false");
                                        %>
                                    </caisi:ProgramExclusiveView>
                                    </caisi:isModuleLoad>

                                    <caisi:isModuleLoad moduleName="TORONTO_RFQ">
                                        <%
                                            session.setAttribute("infirmaryView_isOscar", "false");
                                        %>
                                    </caisi:isModuleLoad>

                                    <!-- caisi infirmary view extension add end ffffffffffffff-->


                                    <c:if test="${infirmaryView_isOscar != 'false'}">
                                        <%
                                            //session.setAttribute("case_program_id", null);
                                        %>
                                        <!-- multi-site , add site dropdown list -->
                                        <%
                                            if (bMultisites) {
                                        %>
                                        <script>
                                            function changeSite(sel) {
                                                sel.style.backgroundColor = sel.options[sel.selectedIndex].style.backgroundColor;
                                                var siteName = sel.options[sel.selectedIndex].value;
                                                var newGroupNo = "<%=(mygroupno == null ? ".default" : mygroupno)%>";
                                                popupPage(10, 10, "providercontrol.jsp?provider_no=<%=loggedInInfo1.getLoggedInProviderNo()%>&start_hour=<%=startHour%>&end_hour=<%=endHour%>&every_min=<%=everyMin%>&color_template=deepblue&dboperation=updatepreference&displaymode=updatepreference&mygroup_no=" + newGroupNo + "&site=" + siteName);
                                            }
                                        </script>

                                        <select id="site" name="site" onchange="changeSite(this)"
                                                style="background-color: <%=( selectedSite == null || siteBgColor.get(selectedSite) == null ? "#FFFFFF" : siteBgColor.get(selectedSite))%>">
                                            <option value="none" style="background-color:white">all clinics</option>
                                            <%
                                                for (int i = 0; i < curUserSites.size(); i++) {
                                            %>
                                            <option value="<%=curUserSites.get(i).getName()%>"
                                                    style="background-color:<%=curUserSites.get(i).getBgColor()%>"
                                                    <%=(curUserSites.get(i).getName().equals(selectedSite)) ? " selected " : ""%> >
                                                <%=curUserSites.get(i).getName()%>
                                            </option>
                                            <%
                                                }
                                            %>
                                        </select>
                                        <%
                                            }
                                        %>
                                        <span><fmt:setBundle basename="oscarResources"/><fmt:message key="global.group"/>:</span>

                                        <%
                                            List<MyGroupAccessRestriction> restrictions = myGroupAccessRestrictionDao.findByProviderNo(loggedInInfo1.getLoggedInProviderNo());
                                        %>
                                        <select id="mygroup_no" name="mygroup_no" onChange="changeGroup(this)">
                                            <option value='.<fmt:setBundle basename="oscarResources"/><fmt:message key="global.default"/>'>.<fmt:setBundle basename="oscarResources"/><fmt:message key="global.default"/></option>


                                            <security:oscarSec roleName="<%=roleName$%>"
                                                               objectName="_team_schedule_only"
                                                               rights="r" reverse="false">
                                                <%
                                                    //                                                String provider_no = loggedInInfo1.getLoggedInProviderNo();
                                                    for (Provider p : providerDao.getActiveProviders()) {
                                                        boolean skip = checkRestriction(restrictions, p.getProviderNo());
                                                        if (!skip) {
                                                %>
                                                <option value='<%=p.getProviderNo()%>'<%=mygroupno.equals(p.getProviderNo()) ? " selected" : ""%> >
                                                    <c:out value="<%=p.getFormattedName()%>"/>
                                                </option>
                                                <%
                                                        }
                                                    }
                                                %>

                                            </security:oscarSec>
                                            <security:oscarSec roleName="<%=roleName$%>"
                                                               objectName="_team_schedule_only" rights="r"
                                                               reverse="true">
                                                <%
                                                    request.getSession().setAttribute("archiveView", "false");
                                                    for (MyGroup g : myGroupDao.searchmygroupno()) {

                                                        boolean skip = checkRestriction(restrictions, g.getId().getMyGroupNo());

                                                        if (!skip && (!bMultisites || siteGroups == null || siteGroups.size() == 0 || siteGroups.contains(g.getId().getMyGroupNo()))) {
                                                %>
                                                <option value='<%="_grp_"+g.getId().getMyGroupNo()%>' <%=mygroupno.equals(g.getId().getMyGroupNo()) ? "selected" : ""%>>
                                                    <c:out value="<%=g.getId().getMyGroupNo()%>"/>
                                                </option>
                                                <%
                                                        }
                                                    }

                                                    for (Provider p : providerDao.getActiveProviders()) {
                                                        boolean skip = checkRestriction(restrictions, p.getProviderNo());

                                                        if (!skip && (!bMultisites || siteProviderNos == null || siteProviderNos.size() == 0 || siteProviderNos.contains(p.getProviderNo()))) {
                                                %>
                                                <option value='<%=p.getProviderNo()%>' <%=mygroupno.equals(p.getProviderNo()) ? "selected" : ""%>>
                                                    <c:out value="<%=p.getFormattedName()%>"/>
                                                </option>
                                                <%
                                                        }
                                                    }
                                                %>
                                            </security:oscarSec>
                                        </select>
                                    </c:if>

                                    <%
                                        }
                                    %>


                                    <!-- caisi infirmary view extension add fffffffffffff-->
                                    <caisi:isModuleLoad moduleName="caisi">
                                    <jsp:include page="infirmaryviewprogramlist.jspf"/>
                                </td>
                            </tr>
                        </table>
                        </caisi:isModuleLoad>
                        <!-- caisi infirmary view extension add end fffffffffffff-->


                    </form>
                </div>
            </td>
        </tr>
    </table>
    <table id="scheduleTable" BGCOLOR="#C0C0C0">

        <tr>
            <td colspan="3">
                <table bgcolor="#486ebd">
                    <tr>
                        <%
                            int hourCursor = 0, minuteCursor = 0, depth = everyMin; //depth is the period, e.g. 10,15,30,60min.
                            String am_pm = null;
                            boolean bColor = true, bColorHour = true; //to change color

                            int iCols = 0, iRows = 0, iS = 0, iE = 0, iSm = 0, iEm = 0; //for each S/E starting/Ending hour, how many events
                            int ih = 0, im = 0, iSn = 0, iEn = 0; //hour, minute, nthStartTime, nthEndTime, rowspan
                            boolean bFirstTimeRs = true;
                            boolean bFirstFirstR = true;
                            Object[] paramTickler = new Object[2];

                            boolean userAvail = true;
                            int me = -1;
                            for (nProvider = 0; nProvider < numProvider; nProvider++) {
                                if (loggedInInfo1.getLoggedInProviderNo().equals(curProvider_no[nProvider])) {
                                    //userInGroup = true;
                                    me = nProvider;
                                    break;
                                }
                            }

                            // set up the iterator appropriately (today - for each doctor; this week - for each day)
                            int iterMax;
                            if (isWeekView) {
                                iterMax = weekViewDays;
                                // find the nProvider value that corresponds to provNum
                                if (numProvider == 1) {
                                    nProvider = 0;
                                } else {
                                    for (int provIndex = 0; provIndex < numProvider; provIndex++) {
                                        if (curProvider_no[provIndex].equals(provNum)) {
                                            nProvider = provIndex;
                                        }
                                    }
                                }
                            } else {
                                iterMax = numProvider;
                            }

                            StringBuffer hourmin = null;
                            String[] param1 = new String[2];

                            java.util.ResourceBundle wdProp = ResourceBundle.getBundle("oscarResources", request.getLocale());

                            // for each providers schedule in the display
                            for (int iterNum = 0; iterNum < iterMax; iterNum++) {

                                if (isWeekView) {
                                    // get the appropriate datetime objects for the current day in this week
                                    year = cal.get(Calendar.YEAR);
                                    month = (cal.get(Calendar.MONTH) + 1);
                                    day = cal.get(Calendar.DAY_OF_MONTH);

                                    strDate = year + "-" + month + "-" + day;
                                    monthDay = String.format("%02d", month) + "-" + String.format("%02d", day);

                                    inform = new SimpleDateFormat("yyyy-MM-dd", request.getLocale());
                                    try {
                                        formatDate = UtilDateUtilities.DateToString(inform.parse(strDate), wdProp.getString("date.EEEyyyyMMdd"), request.getLocale());
                                    } catch (Exception e) {
                                        MiscUtils.getLogger().error("Error", e);
                                        formatDate = UtilDateUtilities.DateToString(inform.parse(strDate), "EEE, yyyy-MM-dd");
                                    }
                                    strYear = "" + year;
                                    strMonth = month > 9 ? ("" + month) : ("0" + month);
                                    strDay = day > 9 ? ("" + day) : ("0" + day);

                                    // Reset timecode bean for this day
                                    param3[0] = strDate; //strYear+"-"+strMonth+"-"+strDay;
                                    param3[1] = curProvider_no[nProvider];
                                    dateTimeCodeBean.put(String.valueOf(provNum), "");

                                    List<Object[]> results = scheduleDateDao.search_appttimecode(ConversionUtils.fromDateString(strDate), curProvider_no[nProvider]);
                                    for (Object[] result : results) {
                                        ScheduleTemplate st = (ScheduleTemplate) result[0];
                                        ScheduleDate sd = (ScheduleDate) result[1];
                                        dateTimeCodeBean.put(sd.getProviderNo(), st.getTimecode());
                                    }


                                    for (ScheduleTemplateCode stc : scheduleTemplateCodeDao.findAll()) {

                                        dateTimeCodeBean.put("description" + stc.getCode(), stc.getDescription());
                                        dateTimeCodeBean.put("duration" + stc.getCode(), stc.getDuration());
                                        dateTimeCodeBean.put("color" + stc.getCode(), (stc.getColor() == null || "".equals(stc.getColor())) ? bgcolordef : stc.getColor());
                                        dateTimeCodeBean.put("confirm" + stc.getCode(), stc.getConfirm());
                                    }

                                    // move the calendar forward one day
                                    cal.add(Calendar.DATE, 1);
                                } else {
                                    nProvider = iterNum;
                                }

                                userAvail = true;
                                int timecodeLength = dateTimeCodeBean.get(curProvider_no[nProvider]) != null ? ((String) dateTimeCodeBean.get(curProvider_no[nProvider])).length() : 4 * 24;

                                if (timecodeLength == 0) {
                                    timecodeLength = 4 * 24;
                                }

                                depth = bDispTemplatePeriod ? (24 * 60 / timecodeLength) : everyMin; // add function to display different time slot
                                param1[0] = strDate; //strYear+"-"+strMonth+"-"+strDay;
                                param1[1] = curProvider_no[nProvider];

                                List<Appointment> appointmentsToCount = appointmentDao.searchappointmentday(curProvider_no[nProvider], ConversionUtils.fromDateString(year + "-" + month + "-" + day), ConversionUtils.fromIntString(programId_oscarView));
                                Integer appointmentCount = 0;

                                for (Appointment appointment : appointmentsToCount) {
                                    if (!noCountStatus.contains(appointment.getStatus()) && appointment.getDemographicNo() != 0
                                            && (!bMultisites || selectedSite == null || "none".equals(selectedSite) || (bMultisites && selectedSite.equals(appointment.getLocation())))
                                    ) {
                                        appointmentCount++;
                                    }
                                }

                                ScheduleDate sd = scheduleDateDao.findByProviderNoAndDate(curProvider_no[nProvider], ConversionUtils.fromDateString(strDate));

                                //viewall function
                                if (request.getParameter("viewall") == null || request.getParameter("viewall").equals("0")) {
                                    if (sd == null || "0".equals(String.valueOf(sd.getAvailable()))) {
                                        if (nProvider != me) continue;
                                        else userAvail = false;
                                    }
                                }
                                bColor = bColor ? false : true;
                        %>
                        <td valign="top" width="<%=isWeekView?100/7:100/numProvider%>%">
                            <!-- for the first providers's schedule -->

                            <table bgcolor="#486ebd">
                                <!-- for the first providers's name -->
                                <tr>
                                    <td class="infirmaryView" NOWRAP ALIGN="center"
                                        BGCOLOR="<%=bColor?"#bfefff":"silver"%>">
                                        <!-- caisi infirmary view extension modify ffffffffffff-->
                                        <c:if test="${infirmaryView_isOscar != 'false'}">
                                            <%
                                                if (isWeekView) {
                                            %>
                                            <b><a href="providercontrol.jsp?year=<%=year%>&month=<%=month%>&day=<%=day%>&view=0&displaymode=day&dboperation=searchappointmentday"><%=formatDate%>
                                            </a></b>
                                            <%
                                            } else {
                                            %>
                                            <input type='button'
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.weekLetter"/>"
                                                   name='weekview'
                                                   onClick="goWeekView('<%=curProvider_no[nProvider]%>')"
                                                   title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.weekView"/>"
                                                   style="color:black" class="noprint">
                                            <% if (OscarProperties.getInstance().isPropertyActive("view.appointmentdaysheetbutton")) { %>
                                            <input type='button' value="DS" name='daysheetview'
                                                   onClick=goDaySheet('<%=curProvider_no[nProvider]%>')
                                                   title="Day Sheet" style="color:black">
                                            <% } %>
                                            <input type='button'
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.searchLetter"/>"
                                                   name='searchview'
                                                   onClick="goSearchView('<%=curProvider_no[nProvider]%>')"
                                                   title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.searchView"/>"
                                                   style="color:black" class="noprint">
                                            <input type='radio' name='flipview' class="noprint"
                                                   onClick="goFilpView('<%=curProvider_no[nProvider]%>')"
                                                   title="Flip view">
                                            <a href=#
                                               onClick="goZoomView('<%=curProvider_no[nProvider]%>','<%= Encode.forJavaScript(curProviderName[nProvider])%>')"
                                               onDblClick="goFilpView('<%=curProvider_no[nProvider]%>')"
                                               title='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.zoomView"/>'>
                                                <c:out value='<%=curProviderName[nProvider]  + " (" + appointmentCount + ") " %>'/>
                                            </a>
                                                <oscar:oscarPropertiesCheck value="yes" property="TOGGLE_REASON_BY_PROVIDER" defaultVal="yes">
                                                    <a href="#"
                                                       class="expand-reason-btn"
                                                       data-provider="<%= curProvider_no[nProvider] %>"
                                                       onclick="toggleReason(event, '<%= curProvider_no[nProvider] %>');"
                                                       title="<fmt:setBundle basename='oscarResources'/><fmt:message key='provider.appointmentProviderAdminDay.expandreason'/>"
                                                    >*</a>
                                                </oscar:oscarPropertiesCheck>

                                            <% } %>

                                            <%
                                                if (!userAvail) {
                                            %>
                                            [<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.msgNotOnSched"/>]
                                            <%
                                                }
                                            %>
                                        </c:if>

                                        <c:if test="${infirmaryView_isOscar == 'false'}">
                                            <c:set var="prID" value="1" scope="page" />

                                            <c:if test="${not empty infirmaryView_programId}">
                                                <c:set var="prID" value="${sessionScope[SessionConstants.CURRENT_PROGRAM_ID]}" />
                                            </c:if>

                                            <c:forEach var="pb" items="${infirmaryView_programBeans}">
                                                <c:if test="${pb.value == prID}">
                                                    <b><label>${pb.label}</label></b>
                                                </c:if>
                                            </c:forEach>
                                        </c:if>
                                        <!-- caisi infirmary view extension modify end ffffffffffffffff-->
                                    </td>
                                </tr>
                                <!-- END for the first providers's name -->
                                <tr>

                                    <td valign="top">

                                        <!-- caisi infirmary view exteion add -->
                                        <!--  fffffffffffffffffffffffffffffffffffffffffff-->
                                        <caisi:isModuleLoad moduleName="caisi">
                                            <jsp:include page="infirmarydemographiclist.jspf"/>
                                        </caisi:isModuleLoad>

                                        <c:if test="${infirmaryView_isOscar != 'false'}">
                                        <!-- caisi infirmary view exteion add end ffffffffffffffffff-->
                                            <!-- =============== following block is the original oscar code. -->
                                            <!-- table for hours of day start -->
                                            <table id="providerSchedule" bgcolor="<%=userAvail?"#486ebd":"silver"%>">
                                                <%
                                                    bFirstTimeRs = true;
                                                    bFirstFirstR = true;

                                                    String useProgramLocation = OscarProperties.getInstance().getProperty("useProgramLocation");
                                                    String moduleNames = OscarProperties.getInstance().getProperty("ModuleNames");
                                                    boolean caisiEnabled = moduleNames != null && org.apache.commons.lang3.StringUtils.containsIgnoreCase(moduleNames, "Caisi");
                                                    boolean locationEnabled = caisiEnabled && (useProgramLocation != null && useProgramLocation.equals("true"));

                                                    int length = locationEnabled ? 4 : 3;

                                                    String[] param0 = new String[length];

                                                    param0[0] = curProvider_no[nProvider];
                                                    param0[1] = year + "-" + month + "-" + day;//e.g."2001-02-02";
                                                    param0[2] = programId_oscarView;
                                                    if (locationEnabled) {
                                                        ProgramManager2 programManager2 = SpringUtils.getBean(ProgramManager2.class);
                                                        ProgramProvider programProvider = programManager2.getCurrentProgramInDomain(loggedInInfo1, loggedInInfo1.getLoggedInProviderNo());
                                                        if (programProvider != null && programProvider.getProgram() != null) {
                                                            programProvider.getProgram().getName();
                                                        }
                                                        param0[3] = request.getParameter("programIdForLocation");
//                                                        strsearchappointmentday = "searchappointmentdaywithlocation";
                                                    }

                                                    List<Appointment> appointments = appointmentDao.searchappointmentday(curProvider_no[nProvider], ConversionUtils.fromDateString(year + "-" + month + "-" + day), ConversionUtils.fromIntString(programId_oscarView));
                                                    Iterator<Appointment> it = appointments.iterator();

                                                    Appointment appointment = null;
                                                    String router = "";
                                                    String record = "";
                                                    String module = "";
                                                    String newUxUrl = "";
                                                    String inContextStyle = "";

                                                    if (request.getParameter("record") != null) {
                                                        record = request.getParameter("record");
                                                    }

                                                    if (request.getParameter("module") != null) {
                                                        module = request.getParameter("module");
                                                    }
                                                    List<Object[]> confirmTimeCode = scheduleDateDao.search_appttimecode(ConversionUtils.fromDateString(strDate), curProvider_no[nProvider]);

                                                    for (ih = startHour * 60; ih <= (endHour * 60 + (60 / depth - 1) * depth); ih += depth) { // use minutes as base
                                                        hourCursor = ih / 60;
                                                        minuteCursor = ih % 60;
                                                        bColorHour = minuteCursor == 0 ? true : false; //every 00 minute, change color

                                                        //templatecode
                                                        if ((dateTimeCodeBean.get(curProvider_no[nProvider]) != null) && (dateTimeCodeBean.get(curProvider_no[nProvider]) != "") && confirmTimeCode.size() != 0) {
                                                            int nLen = 24 * 60 / ((String) dateTimeCodeBean.get(curProvider_no[nProvider])).length();
                                                            int ratio = (hourCursor * 60 + minuteCursor) / nLen;
                                                            hourmin = new StringBuffer(dateTimeCodeBean.get(curProvider_no[nProvider]) != null ? ((String) dateTimeCodeBean.get(curProvider_no[nProvider])).substring(ratio, ratio + 1) : " ");
                                                        } else {
                                                            hourmin = new StringBuffer();
                                                        }
                                                %>
                                                <tr>
                                                    <td class="<%=bColorHour?"scheduleTime00":"scheduleTimeNot00"%>">
                                                        <a href="javascript:void(0)"
                                                           onClick="confirmPopupPage(600,780, '<%= request.getContextPath() %>/appointment/addappointment.jsp?provider_no=<%=curProvider_no[nProvider]%>&bFirstDisp=<%=true%>&year=<%=strYear%>&month=<%=strMonth%>&day=<%=strDay%>&start_time=<%=(hourCursor>9?(""+hourCursor):("0"+hourCursor))+":"+ (minuteCursor<10?"0":"") +minuteCursor%>&end_time=<%=(hourCursor>9?(""+hourCursor):("0"+hourCursor))+":"+(minuteCursor+depth-1)%>&duration=<%=dateTimeCodeBean.get("duration"+hourmin.toString())%>','<%=dateTimeCodeBean.get("confirm"+hourmin.toString())%>','<%=allowDay%>','<%=allowWeek%>');return false;"
                                                           title='<%=MyDateFormat.getTimeXX_XXampm(hourCursor +":"+ (minuteCursor<10?"0":"")+minuteCursor)%> - <%=MyDateFormat.getTimeXX_XXampm(hourCursor +":"+((minuteCursor+depth-1)<10?"0":"")+(minuteCursor+depth-1))%>'
                                                           class="adhour">
                                                            <%=(hourCursor < 10 ? "0" : "") + hourCursor + ":"%><%=(minuteCursor < 10 ? "0" : "") + minuteCursor%>&nbsp;</a>
                                                    </td>
                                                    <td class="hourmin"
                                                        width='1%' <%=dateTimeCodeBean.get("color" + hourmin.toString()) != null ? ("bgcolor=" + dateTimeCodeBean.get("color" + hourmin.toString())) : ""%>
                                                        title='<%=dateTimeCodeBean.get("description"+hourmin.toString())%>'>
                                                                        <span color='<%=(dateTimeCodeBean.get("color"+hourmin.toString())!=null && !dateTimeCodeBean.get("color"+hourmin.toString()).equals(bgcolordef) )?"black":"white"%>'><%=hourmin.toString()%>
                                                                        </span>
                                                    </td>
                                                            <%
                                                    while (bFirstTimeRs?it.hasNext():true) { //if it's not the first time to parse the standard time, should pass it by
                                                          appointment = bFirstTimeRs?it.next():appointment;
                                                          len = bFirstTimeRs&&!bFirstFirstR?lenLimitedS:lenLimitedL;
                                                          String strStartTime = ConversionUtils.toTimeString(appointment.getStartTime());
                                                          String strEndTime = ConversionUtils.toTimeString(appointment.getEndTime());

                                                          iS=Integer.parseInt(String.valueOf(strStartTime).substring(0,2));
                                                          iSm=Integer.parseInt(String.valueOf(strStartTime).substring(3,5));
                                                          iE=Integer.parseInt(String.valueOf(strEndTime).substring(0,2));
                                                          iEm=Integer.parseInt(String.valueOf(strEndTime).substring(3,5));

                                                          if( (ih < iS*60+iSm) && (ih+depth-1)<iS*60+iSm ) { //iS not in this period (both start&end), get to the next period
                                                            //out.println("<td width='10'>&nbsp;</td>"); //should be comment
                                                            bFirstTimeRs=false;
                                                            break;
                                                          }
                                                          if( (ih > iE*60+iEm) ) { //appt before this time slot (both start&end), get to the next period
                                                            //out.println("<td width='10'>&nbsp;</td>"); //should be comment
                                                            bFirstTimeRs=true;
                                                            continue;
                                                          }
                                                            iRows=((iE*60+iEm)-ih)/depth+1; //to see if the period across an hour period
                                                            //iRows=(iE-iS)*60/depth+iEm/depth-iSm/depth+1; //to see if the period across an hour period


                                                              int demographic_no = appointment.getDemographicNo();
															  pageContext.setAttribute("appointment", appointment);
                                                              Demographic demographic = null;

                                                              //Pull the appointment name from the demographic information if the appointment is attached to a specific demographic.
                                                              //Otherwise get the name associated with the appointment from the appointment information
                                                              String name = ".";
                                                              if (demographic_no != 0) {
                                                                  demographic = demographicManager.getDemographic(loggedInInfo1, demographic_no);
                                                                  StringBuilder nameBuilder = new StringBuilder();
																  nameBuilder.append(UtilMisc.toUpperLowerCase(demographic.getLastName()))
																  .append(", ")
																  .append(UtilMisc.toUpperLowerCase(demographic.getFirstName()));
																  if(demographic.getAlias() != null && ! demographic.getAlias().isEmpty()) {
                                                                      nameBuilder.append(" (")
                                                                      .append(UtilMisc.toUpperLowerCase(demographic.getAlias()))
                                                                      .append(")");
																  }
																  if(demographic.getPronoun() != null && ! demographic.getPronoun().isEmpty()) {
																    nameBuilder.append("; ").append(demographic.getPronoun());
                                                                  }
                                                                  name = nameBuilder.toString();
                                                              }
                                                              else {
																  name = appointment.getName();
                                                              }

                                                              paramTickler[0]=String.valueOf(demographic_no);
                                                              paramTickler[1]=MyDateFormat.getSysDate(strDate);
                                                              tickler_no = "";
                                                              tickler_note="";

                                                             if(securityInfoManager.hasPrivilege(loggedInInfo1, "_tickler", "r", demographic_no)) {
                                                                  for(Tickler t: ticklerManager.search_tickler(loggedInInfo1, demographic_no, MyDateFormat.getSysDate(strDate))) {
                                                                      tickler_no = t.getId().toString();
                                                                      tickler_note = t.getMessage()==null?tickler_note:tickler_note + "\n" + t.getMessage();
                                                                  }
                                                             }

                                                              //alerts and notes
                                                              DemographicCust dCust = demographicManager.getDemographicCust(loggedInInfo1,demographic_no);

                                                              ver = "";
                                                              roster = "";

                                                              if(demographic != null) {

                                                                ver = demographic.getVer();
                                                                roster = demographic.getRosterStatus();

                                                                mob = demographic.getMonthOfBirth();
                                                                dob = demographic.getDateOfBirth();

                                                                if(mob != null && dob != null) {
                                                                    demBday = mob + "-" + dob;
                                                                }

                                                                if (roster == null ) {
                                                                    roster = "";
                                                                }
                                                              }

                                                                      String reason = String.valueOf(appointment.getReason()).trim();
                                                                      String notes = String.valueOf(appointment.getNotes()).trim();
                                                                      String status = String.valueOf(appointment.getStatus()).trim();
                                                                      String sitename = String.valueOf(appointment.getLocation()).trim();
                                                                      String type = appointment.getType();
                                                                      String urgency = appointment.getUrgency();
                                                                      String reasonCodeName = "";
                                                                      if(appointment.getReasonCode() != null)    {
                                                                            LookupListItem lli  = reasonCodesMap.get(appointment.getReasonCode());
                                                                            if(lli != null) {
                                                                                reason = lli.getLabel().trim() + " " + reason;
                                                                            }
                                                                      }

                                                                      if(reason != null && ! reason.isEmpty()) {
                                                                            reasonCodeName += reason;
                                                                      }

                                                                      if ( "yes".equalsIgnoreCase(OscarProperties.getInstance().getProperty("SHOW_APPT_TYPE_WITH_REASON")) ) {
                                                                        reasonCodeName = type
                                                                                + ((type != null && ! type.isEmpty()) ? " : " : "")
                                                                                + reasonCodeName;
                                                                      }

                                                                  bFirstTimeRs=true;
                                                            as.setApptStatus(status);

                                                         //multi-site. if a site have been selected, only display appointment in that site
                                                   if (!bMultisites || (selectedSite == null && CurrentSiteMap.get(sitename) != null) || sitename.equals(selectedSite)) {
                                                    %>
                                                    <td class="appt" bgcolor='<%=as.getBgColor()%>'
                                                        rowspan="<%=iRows%>"
                                                        nowrap>
                                                        <!-- multisites : add colour-coded to the "location" value of that appointment. -->
                                                        <%if (bMultisites) {%>
                                                        <span title="<%= sitename %>"
                                                              style="background-color:<%=siteBgColor.get(sitename)%>;">&nbsp;</span>|
                                                        <%} %>

                                                        <%
                                                            String nextStatus = as.getNextStatus();
                                                            if (nextStatus != null && !nextStatus.equals("")) {
                                                        %>
                                                        <!-- Short letters -->
                                                        <a class="apptStatus" href="javascript:void(0)"
                                                           onclick="refreshSameLoc('providercontrol.jsp?appointment_no=<%=appointment.getId()%>&amp;provider_no=<%=curProvider_no[nProvider]%>&amp;status=&amp;statusch=<%=nextStatus%>&amp;year=<%=year%>&amp;month=<%=month%>&amp;day=<%=day%>&amp;<%=viewString%>&amp;displaymode=addstatus&amp;dboperation=updateapptstatus&amp;viewall=${ not empty param.viewall ? param.viewall : "0" }<%= isWeekView ? "&amp;viewWeek=1" : "" %>');"
                                                           title='<c:out value="<%=as.getTitleString(request.getLocale())%>" />'>
                                                            <%
                                                                }
                                                                if (nextStatus != null) {
                                                                    if (OscarProperties.getInstance().getProperty("APPT_SHOW_SHORT_LETTERS", "false") != null
                                                                            && OscarProperties.getInstance().getProperty("APPT_SHOW_SHORT_LETTERS", "false").equals("true")) {

                                                                        String colour = as.getShortLetterColour();
                                                                        if (colour == null) {
                                                                            colour = "#FFFFFF";
                                                                        }

                                                            %>
                                                            <span class='short_letters'
                                                                  style='color:<%= colour%>;border:0;height:10px'>
                                                                    [<%=UtilMisc.htmlEscape(as.getShortLetters())%>]
                                                            </span>
                                                            <%
                                                            } else {
                                                            %>

                                                            <img src="<%= request.getContextPath() %>/images/<%=as.getImageName()%>"
                                                                 border="0" height="10"
                                                                 alt="<c:out value='<%=(as.getTitleString(request.getLocale()).length()>0)?as.getTitleString(request.getLocale()):as.getTitle()%>' /> ">

                                                            <%
                                                                    }
                                                                } else {
                                                                    out.print("&nbsp;");
                                                                }

                                                            %>
                                                        </a>
                                                        <%
                                                            if (urgency != null && urgency.equals("critical")) {
                                                        %>
                                                        <img src="<%= request.getContextPath() %>/images/warning-icon.png" border="0"
                                                             width="14" height="14"
                                                             alt="Critical Appointment"/>
                                                        <% } %>
                                                            <%--|--%>
                                                        <%
                                                            if (demographic_no == 0) {
                                                        %>
                                                        <!--  caisi  -->

                                                        <% if (tickler_no.compareTo("") != 0) {%>
                                                            <a href="#"
                                                               onClick="popupPage(700,1024, '<%= request.getContextPath() %>/tickler/ticklerMain.jsp?demoview=0');return false;"
                                                               title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.ticklerMsg"/>: <%=Encode.forHtmlContent(tickler_note)%>">
                                                                <span color="red">!</span></a>
                                                        <%} %>


                                                        <!--  alerts -->
                                                        <% if (OscarProperties.getInstance().getProperty("displayAlertsOnScheduleScreen", "").equals("true")) { %>
                                                        <% if (dCust != null && dCust.getAlert() != null && !dCust.getAlert().isEmpty()) { %>
                                                        <a href="#" onClick="return false;"

                                                           title="<%=Encode.forHtmlAttribute(dCust.getAlert())%>">A</a>

                                                        <%
                                                                }
                                                            }
                                                        %>

                                                        <!--  notes -->
                                                        <% if (OscarProperties.getInstance().getProperty("displayNotesOnScheduleScreen", "").equals("true")) { %>
                                                        <% if (dCust != null && dCust.getNotes() != null && !SxmlMisc.getXmlContent(dCust.getNotes(), "<unotes>", "</unotes>").isEmpty()) { %>
                                                        <a href="#" onClick="return false;"

                                                           title="<%=Encode.forHtmlAttribute(SxmlMisc.getXmlContent(dCust.getNotes(), "<unotes>", "</unotes>"))%>">N</a>

                                                        <%
                                                                }
                                                            }
                                                        %>


                                                        <%
                                                            // Build tooltip variants for privacy-compliant display (dot-name format)
                                                            // Always show reason/notes labels, but handle "null" string from String.valueOf(null)
                                                            String timeRange = iS + ":" + (iSm > 10 ? "" : "0") + iSm + "-" + iE + ":" + iEm;
                                                            String dotTooltipShort = timeRange + " " + Encode.forHtmlAttribute(name) + ((type != null && !type.isEmpty()) ? "&#013;&#010;type: " + Encode.forHtmlAttribute(type) : "");
                                                            String dotReasonDisplay = (reason != null && !"null".equals(reason)) ? reason : "";
                                                            String dotNotesDisplay = (notes != null && !"null".equals(notes)) ? notes : "";
                                                            String dotTooltipFull = dotTooltipShort + "&#013;&#010;reason: " + Encode.forHtmlAttribute(dotReasonDisplay) + "&#013;&#010;notes: " + Encode.forHtmlAttribute(dotNotesDisplay);

                                                            // Check if tooltips should be shown (defaults to true if property not set)
                                                            boolean showTooltipDot = OscarProperties.getInstance().getBooleanProperty("SHOW_APPT_REASON_TOOLTIP", "yes");
                                                        %>
                                                        <a href="javascript:void(0)"
                                                           class="<%= showTooltipDot ? "appt-reason-tooltip appt-tooltip-provider-" + curProvider_no[nProvider] : "" %>"
                                                           onClick="popupPage(600,780,'<%= request.getContextPath() %>/appointment/appointmentcontrol.jsp?appointment_no=<%=appointment.getId()%>&provider_no=<%=curProvider_no[nProvider]%>&year=<%=year%>&month=<%=month%>&day=<%=day%>&start_time=<%=iS+":"+iSm%>&demographic_no=0&displaymode=edit&dboperation=search');return false;"
                                                           <%= showTooltipDot ? "data-title-full=\"" + dotTooltipFull + "\" data-title-short=\"" + dotTooltipShort + "\" title=\"" + dotTooltipFull + "\"" : "" %>>
                                                            <span>
                                                            .<%=(view == 0 && numAvailProvider != 1) ? (name.length() > len ? name.substring(0, len).toUpperCase() : Encode.forHtmlContent(name.toUpperCase())) : Encode.forHtmlContent(name.toUpperCase())%>
                                                            </span>
                                                        </a><!--Inline display of reason -->

                                                        <span class="reason reason_<%=curProvider_no[nProvider]%> hideReason">
                                                            <%= Encode.forHtmlContent(reasonCodeName) %>
                                                        </span>


                                                        <%
                                                        } else {
                                                        %> <% if (tickler_no.compareTo("") != 0) {%>
                                                            <a href="#"
                                                               onClick="popupPage(700,1024, '<%= request.getContextPath() %>/tickler/ticklerMain.jsp?demoview=<%=demographic_no%>');return false;"
                                                               title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.ticklerMsg"/>: <%=UtilMisc.htmlEscape(tickler_note)%>"><span
                                                                    color="red">!</span></a>
                                                        <%} %>

                                                        <!--  alerts -->
                                                        <% if (OscarProperties.getInstance().getProperty("displayAlertsOnScheduleScreen", "").equals("true")) {%>
                                                        <% if (dCust != null && dCust.getAlert() != null && !dCust.getAlert().isEmpty()) { %>
                                                        <a href="#" onClick="return false;"

                                                           title="<%=Encode.forHtmlAttribute(dCust.getAlert())%>">A</a>
                                                        <%
                                                                }
                                                            }
                                                        %>

                                                        <!--  notes -->
                                                        <% if (OscarProperties.getInstance().getProperty("displayNotesOnScheduleScreen", "").equals("true")) {%>
                                                        <% if (dCust != null && dCust.getNotes() != null && !SxmlMisc.getXmlContent(dCust.getNotes(), "<unotes>", "</unotes>").isEmpty()) { %>
                                                        <a href="#" onClick="return false;"

                                                           title="<%=Encode.forHtmlAttribute(SxmlMisc.getXmlContent(dCust.getNotes(), "<unotes>", "</unotes>"))%>">N</a>

                                                        <%
                                                                }
                                                            }
                                                        %>

                                                        <!-- doctor code block 1 -->
                                                        <c:if test="${doctorLinkRights}">

                                                            <% if (ver != null && ver != "" && "##".compareTo(ver.toString()) == 0) {%><a
                                                                href="#"
                                                                title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.versionMsg"/> <%=UtilMisc.htmlEscape(ver)%>">
                                                            <span style="color:red;">*</span></a><%}%>

                                                            <% if (roster != "" && "FS".equalsIgnoreCase(roster)) {%>
                                                            <a href="#"
                                                               title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.rosterMsg"/> <%=UtilMisc.htmlEscape(roster)%>"><span
                                                                    style="color:red;">$</span></a><%}%>

                                                            <% if ("NR".equalsIgnoreCase(roster) || "PL".equalsIgnoreCase(roster)) {%>
                                                            <a href="#"
                                                               title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.rosterMsg"/> <%=UtilMisc.htmlEscape(roster)%>"><span
                                                                    style="color:red;">#</span></a><%}%>

                                                        </c:if>
                                                        <!-- doctor code block 2 -->
                                                        <c:if test="${not isPreventionWarningDisabled}">
                                                            <%
                                                                String warning = providerPreventionManager.getWarnings(loggedInInfo1, String.valueOf(demographic_no));
                                                                if (!warning.isEmpty()) {
                                                            %>
                                                            <img src="${pageContext.servletContext.contextPath}/images/stop_sign.png"
                                                                 width="14px" height="14px"
                                                                 style="margin-bottom: 3px;margin-left: 3px;"
                                                                 title="<%=Encode.forHtmlContent(warning)%>"/>&nbsp;
                                                            <% } %>
                                                        </c:if>
                                                        <%
                                                            String start_time = "";
                                                            if (iS < 10) {
                                                                start_time = "0";
                                                            }
                                                            start_time += iS + ":";
                                                            if (iSm < 10) {
                                                                start_time += "0";
                                                            }

                                                            start_time += iSm + ":00";
                                                        %>

                                                        <%
                                                            // Build tooltip variants for privacy-compliant display
                                                            // Always show reason/notes labels, but handle "null" string from String.valueOf(null)
                                                            String tooltipShort = Encode.forHtmlAttribute(name) + ((type != null && !type.isEmpty()) ? "&#013;&#010;type: " + Encode.forHtmlAttribute(type) : "");
                                                            String reasonDisplay = (reason != null && !"null".equals(reason)) ? reason : "";
                                                            String notesDisplay = (notes != null && !"null".equals(notes)) ? notes : "";
                                                            String tooltipFull = tooltipShort + "&#013;&#010;reason: " + Encode.forHtmlAttribute(reasonDisplay) + "&#013;&#010;notes: " + Encode.forHtmlAttribute(notesDisplay);

                                                            // Check if tooltips should be shown (defaults to true if property not set)
                                                            boolean showTooltip = OscarProperties.getInstance().getBooleanProperty("SHOW_APPT_REASON_TOOLTIP", "yes");
                                                        %>
                                                        <a class="apptLink<%= showTooltip ? " appt-reason-tooltip appt-tooltip-provider-" + curProvider_no[nProvider] : "" %>" href="javascript:void(0)"
                                                           onClick="popupPage(535,860,'<%= request.getContextPath() %>/appointment/appointmentcontrol.jsp?appointment_no=<%=appointment.getId()%>&provider_no=<%=curProvider_no[nProvider]%>&year=<%=year%>&month=<%=month%>&day=<%=day%>&start_time=<%=iS+":"+iSm%>&demographic_no=<%=demographic_no%>&displaymode=edit&dboperation=search');return false;"
                                                           <%= showTooltip ? "data-title-full=\"" + tooltipFull + "\" data-title-short=\"" + tooltipShort + "\" title=\"" + tooltipFull + "\"" : "" %> >
                                                            <%=(name.length() > len ? Encode.forHtmlContent(name.substring(0, len)) : Encode.forHtmlContent(name))%>
                                                        </a>
                                                        <% if (len == lenLimitedL || view != 0 || numAvailProvider == 1) {%>


                                                        <oscar:oscarPropertiesCheck
                                                                property="eform_in_appointment" value="yes">
                                                            &#124; <b><a href="#"
                                                                         onclick="popupPage(500,1024,'<%= request.getContextPath() %>/eform/efmformslistadd.jsp?parentAjaxId=eforms&demographic_no=<%=demographic_no%>&appointment=<%=appointment.getId()%>'); return false;"
                                                                         title="eForm Library">F</a></b>
                                                        </oscar:oscarPropertiesCheck>

                                                        <!-- doctor code block 3 -->
                                                        <% if (!isWeekView) { %>

                                                        <% String eURL = request.getContextPath() + "/oscarEncounter/IncomingEncounter.do?providerNo="
                                                                + loggedInInfo1.getLoggedInProviderNo() + "&appointmentNo="
                                                                + appointment.getId()
                                                                + "&demographicNo="
                                                                + demographic_no
                                                                + "&curProviderNo="
                                                                + curProvider_no[nProvider]
                                                                + "&reason="
                                                                + URLEncoder.encode(Encode.forHtmlContent(reason), StandardCharsets.UTF_8)
                                                                + "&reasonCode=" + (appointment.getReasonCode() != null ? appointment.getReasonCode() : "")
                                                                + "&encType="
                                                                + URLEncoder.encode("face to face encounter with client", "UTF-8")
                                                                + "&userName="
                                                                + URLEncoder.encode(userfirstname + " " + userlastname, StandardCharsets.UTF_8)
                                                                + "&curDate=" + curYear + "-" + curMonth + "-"
                                                                + curDay + "&appointmentDate=" + year + "-"
                                                                + month + "-" + day + "&startTime="
                                                                + start_time + "&status=" + status
                                                                + "&apptProvider_no="
                                                                + curProvider_no[nProvider]
                                                                + "&providerview="
                                                                + curProvider_no[nProvider];%>

                                                        <% if (showOldEchartLink) { %>
                                                        &#124; <a href="javascript:void(0)" class="encounterBtn"
                                                                  onClick="popupWithApptNo(710, 1024,'<%=eURL%>','encounter',<%=appointment.getId()%>);return false;"
                                                                  title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.encounter"/>">
                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.btnE"/></a>
                                                        <% }
                                                        } // end if not is week view %>

                                                        <%= (bShortcutIntakeForm) ? "| <a href='#' onClick='popupPage(700, 1024, \"formIntake.jsp?demographic_no=" + demographic_no + "\")' title='Intake Form'>In</a>" : "" %>

                                                        <!-- billing code block -->
                                                        <% if (!isWeekView) { %>
                                                        <c:if test="${billingRights}">
                                                            <%
                                                                if (status.indexOf('B') == -1) {
                                                            %>
                                                            &#124; <a href=#
                                                                      onClick='popupPage(755,1200, "<%=request.getContextPath()%>/billing.do?billRegion=<%=URLEncoder.encode(prov, StandardCharsets.UTF_8)%>&billForm=<%=URLEncoder.encode(oscarVariables.getProperty("default_view"), StandardCharsets.UTF_8)%>&hotclick=<%=URLEncoder.encode("", StandardCharsets.UTF_8)%>&appointment_no=<%=appointment.getId()%>&demographic_name=<%=URLEncoder.encode(name, StandardCharsets.UTF_8)%>&status=<%=status%>&demographic_no=<%=demographic_no%>&providerview=<%=curProvider_no[nProvider]%>&user_no=<%=loggedInInfo1.getLoggedInProviderNo()%>&apptProvider_no=<%=curProvider_no[nProvider]%>&xml_provider=<%=curProvider_no[nProvider]%>&appointment_date=<%=year+"-"+month+"-"+day%>&start_time=<%=start_time%>&bNewForm=1");return false;'
                                                                      title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.billingtag"/>"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.btnB"/></a>
                                                            <%
                                                            } else {
                                                                if (caisiBillingPreferenceNotDelete != null && caisiBillingPreferenceNotDelete.equals("1")) {
                                                            %>
                                                            &#124; <a href=#
                                                                      onClick='onUpdatebill("<%= request.getContextPath() %>/billing/CA/ON/billingEditWithApptNo.jsp?billRegion=<%=URLEncoder.encode(prov, StandardCharsets.UTF_8)%>&billForm=<%=URLEncoder.encode(oscarVariables.getProperty("default_view"), StandardCharsets.UTF_8)%>&hotclick=<%=URLEncoder.encode("", StandardCharsets.UTF_8)%>&appointment_no=<%=appointment.getId()%>&demographic_name=<%=URLEncoder.encode(name, StandardCharsets.UTF_8)%>&status=<%=status%>&demographic_no=<%=demographic_no%>&providerview=<%=curProvider_no[nProvider]%>&user_no=<%=loggedInInfo1.getLoggedInProviderNo()%>&apptProvider_no=<%=curProvider_no[nProvider]%>&appointment_date=<%=year+"-"+month+"-"+day%>&start_time=<%=iS+":"+iSm%>&bNewForm=1");return false;'
                                                                      title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.billingtag"/>">=<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.btnB"/></a>
                                                            <%
                                                            } else {
                                                            %>
                                                            &#124; <a href=#
                                                                      onClick='onUnbilled("<%= request.getContextPath() %>/billing/CA/<%=prov%>/billingDeleteWithoutNo.jsp?status=<%=status%>&appointment_no=<%=appointment.getId()%>");return false;'
                                                                      title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.billingtag"/>">-<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.btnB"/></a>
                                                            <%
                                                                    }
                                                                }
                                                            %>
                                                        </c:if> <!-- billing rights -->
                                                        <% } %>
                                                        <!-- billing code block -->
                                                        <c:if test="${masterLinkRights}">

                                                            &#124; <a class="masterBtn"
                                                                      href="javascript:void(0)"
                                                                      onClick="popupWithApptNo(700,1024, '<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=<%=demographic_no%>&apptProvider=<%=curProvider_no[nProvider]%>&appointment=<%=appointment.getId()%>&displaymode=edit&dboperation=search_detail','master',<%=appointment.getId()%>)"
                                                                      title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.msgMasterFile"/>"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.btnM"/></a>

                                                        </c:if>
                                                        <% if (!isWeekView) { %>

                                                        <!-- doctor code block 4 -->

                                                        <c:if test="${doctorLinkRights}">
                                                            &#124; <a href=#
                                                                      onClick="popupWithApptNo(700,1027,'<%=request.getContextPath()%>/oscarRx/choosePatient.do?providerNo=<%=loggedInInfo1.getLoggedInProviderNo()%>&demographicNo=<%=demographic_no%>','rx',<%=appointment.getId()%>)"
                                                                      title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.prescriptions"/>"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.rx"/>
                                                        </a>


                                                            <!-- doctor color -->
                                                            <oscar:oscarPropertiesCheck
                                                                    property="ENABLE_APPT_DOC_COLOR"
                                                                    value="yes">
                                                                <%
                                                                    String providerColor = null;
                                                                    if (view == 1 && demographic != null && userPropertyDao != null) {
                                                                        String providerNo = demographic.getProviderNo();
                                                                        UserProperty property = userPropertyDao.getProp(providerNo, UserPropertyDAO.COLOR_PROPERTY);
                                                                        if (property != null) {
                                                                            providerColor = property.getValue();
                                                                        }
                                                                    }
                                                                %>
                                                                <%= (providerColor != null ? "<span style=\"background-color:" + providerColor + ";width:5px\">&nbsp;</span>" : "") %>
                                                            </oscar:oscarPropertiesCheck>

                                                            <span class='reason reason_<%=curProvider_no[nProvider]%> hideReason'>
                                                        &#124; <strong><i>
                                                            <%= Encode.forHtmlContent(reasonCodeName)%>
                                                        </i></strong>
                                                    </span>
                                                        </c:if>

                                                        <!-- add one link to caisi Program Management Module -->
                                                        <caisi:isModuleLoad moduleName="caisi">
                                                            <a href=${pageContext.servletContext.contextPath}'/PMmodule/ClientManager.do?id=<%=demographic_no%>'
                                                               title="Program Management">|P</a>
                                                        </caisi:isModuleLoad>

                                                        <span class="birthday-cake" data-month="<%= monthDay %>"
                                                              data-bday="<%= demBday %>" style="display:none;">
                                                            &#124;<img
                                                                src="${pageContext.servletContext.contextPath}/images/cake.gif"
                                                                width="14" height="14"
                                                                style="margin-bottom: 3px;margin-left: 3px;"
                                                                alt="Happy Birthday"/>
                                                        </span>
                                                        <c:forEach items="${formNamesList}" var="form">
                                                            |<a href="javascript:void(0)" onClick='popupPage2("${pageContext.servletContext.contextPath}/form/forwardshortcutname.do?formname=<c:out
                                                                value="${form}"/>&amp;formId=0&provNo=${appointment.providerNo}&parentAjaxId=forms&amp;demographic_no=${appointment.demographicNo}&amp;appointmentNo=${appointment.id}")'
                                                            title='<c:out value="${form}"/>'>
                                                            <c:out value="${fn:substring(form, 0, truncateLimit)}"/>
                                                            </a>
                                                        </c:forEach>
                                                        <c:forEach items="${eFormsList}" var="eform">
                                                            |<a href="javascript:void(0)" onClick='popupPage2("${pageContext.servletContext.contextPath}/eform/efmformadd_data.jsp?fid=${eform.appointmentScreenEForm}&amp;demographic_no=${appointment.demographicNo}&amp;appointment=${appointment.id}")' title='<c:out
                                                                value="${eform.eFormName}"/>'>
                                                            <c:out value="${fn:substring(eform.eFormName, 0, truncateLimit)}"/>
                                                            </a>
                                                        </c:forEach>
                                                        <c:forEach items="${quickLinksList}" var="quickLink">
                                                            |<a href="javascript:void(0)" onClick='popupPage2("<c:out
                                                                value="${quickLink.url}"/>")' title='<c:out
                                                                value="${quickLink.name}"/>'>
                                                            <c:out value="${fn:substring(quickLink.name, 0, truncateLimit)}"/>
                                                            </a>
                                                        </c:forEach>

                                                        <oscar:oscarPropertiesCheck
                                                                property="appt_pregnancy" value="true"
                                                                defaultVal="false">

                                                            <c:set var="demographicNo"
                                                                   value="<%=demographic_no %>"/>
                                                            <jsp:include page="appointmentPregnancy.jspf">
                                                                <jsp:param value="${demographicNo}"
                                                                           name="demographicNo"/>
                                                            </jsp:include>

                                                        </oscar:oscarPropertiesCheck>

                                                        <% }
                                                        } %>
                                                    </td>
                                                            <%
                                                                    }
                                                                }
                                                                    bFirstFirstR = false;
                                                            }

                                                            out.println("<td class='noGrid' width='1'></td></tr>"); //no grid display
                                                          }
                                                                %>

                                            </table>
                                            <!-- end table for each providers schedule display -->
                                            <!-- caisi infirmary view extension add fffffffffff-->
                                        </c:if>
                                        <!-- caisi infirmary view extension add end fffffffffffffff-->

                                    </td>
                                </tr>

                            </table><!-- end table for each providers name -->

                        </td>
                        <%
                            } //end of display team a, etc.

                        %>


                    </tr>
                    <% } // end caseload view %>
                </table>        <!-- end table for the whole schedule row display -->


            </td>
        </tr>

    </table>

    <!-- key shortcut hotkey block added by phc -->
    <script language="JavaScript">

        // popup blocking for the site must be off!
        // developed on Windows FF 2, 3 IE 6 Linux FF 1.5
        // FF on Mac and Opera on Windows work but will require shift or control with alt and Alpha
        // to fire the altKey + Alpha combination - strange

        // Modification Notes:
        //     event propagation has not been blocked beyond returning false for onkeydown (onkeypress may or may not fire depending)
        //     keyevents have not been even remotely standardized so test mods across agents/systems or something will break!
        //     use popupOscarRx so that this codeblock can be cut and pasted to appointmentprovideradminmonth.jsp

        // Internationalization Notes:
        //     underlines should be added to the labels to prompt/remind the user and should correspond to
        //     the actual key whose keydown fires, which is also stored in the oscarResources.properties files
        //     if you are using the keydown/up event the value stored is the actual key code
        //     which, at least with a US keyboard, also is the uppercase utf-8 code, ie A keyCode=65

        document.onkeydown = function (e) {
            evt = e || window.event;  // window.event is the IE equivalent
            if (evt.altKey) {
                //use (evt.altKey || evt.metaKey) for Mac if you want Apple+A, you will probably want a seperate onkeypress handler in that case to return false to prevent propagation
                switch (evt.keyCode) {
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.adminShortcut"/> :
                        newWindow("<%= request.getContextPath() %>/administration/", "admin");
                        return false;  //run code for 'A'dmin
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.billingShortcut"/> :
                        popupOscarRx(600, 1024, '<%= request.getContextPath() %>/billing/CA/<%=prov%>/billingReportCenter.jsp?displaymode=billreport&providerview=<%=loggedInInfo1.getLoggedInProviderNo()%>');
                        return false;  //code for 'B'illing
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.calendarShortcut"/> :
                        popupOscarRx(425, 430, '<%= request.getContextPath() %>/share/CalendarPopup.jsp?urlfrom=<%= request.getContextPath() %>/provider/providercontrol.jsp&year=<%=strYear%>&month=<%=strMonth%>&param=<%=URLEncoder.encode("&view=0&displaymode=day&dboperation=searchappointmentday","UTF-8")%>');
                        return false;  //run code for 'C'alendar
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.edocShortcut"/> :
                        popupOscarRx('700', '1024', '<%= request.getContextPath() %>/documentManager/documentReport.jsp?function=providers&functionid=<%=loggedInInfo1.getLoggedInProviderNo()%>&curUser=<%=loggedInInfo1.getLoggedInProviderNo()%>', 'edocView');
                        return false;  //run code for e'D'oc
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.resourcesShortcut"/> :
                        popupOscarRx(550, 687, '<%=resourcebaseurl%>');
                        return false; // code for R'e'sources
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.helpShortcut"/> :
                        popupOscarRx(600, 750, '<%=resourcebaseurl%>');
                        return false;  //run code for 'H'elp
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.ticklerShortcut"/> : {
                        popupOscarRx(700, 1024, '<%= request.getContextPath() %>/tickler/ticklerMain.jsp', '<fmt:setBundle basename="oscarResources"/><fmt:message key="global.tickler"/>') //run code for t'I'ckler
                        return false;
                    }
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.labShortcut"/> :
                        popupOscarRx(600, 1024, '<%=request.getContextPath()%>/documentManager/inboxManage.do?method=prepareForIndexPage&providerNo=<%=loggedInInfo1.getLoggedInProviderNo()%>', '<fmt:setBundle basename="oscarResources"/><fmt:message key="global.lab"/>');
                        return false;  //run code for 'L'ab
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.msgShortcut"/> :
                        popupOscarRx(600, 1024, '<%=request.getContextPath()%>/messenger/DisplayMessages.do?providerNo=<%=loggedInInfo1.getLoggedInProviderNo()%>&userName=<%=URLEncoder.encode(userfirstname+" "+userlastname, StandardCharsets.UTF_8)%>');
                        return false;  //run code for 'M'essage
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.monthShortcut"/> :
                        window.open("providercontrol.jsp?year=<%=year%>&month=<%=month%>&day=1<%=viewString%>&displaymode=month&dboperation=searchappointmentmonth", "_self");
                        return false;  //run code for Mo'n'th
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.conShortcut"/> :
                        popupOscarRx(625, 1024, '<%=request.getContextPath()%>/oscarEncounter/IncomingConsultation.do?providerNo=<%=loggedInInfo1.getLoggedInProviderNo()%>&userName=<%=URLEncoder.encode(userfirstname+" "+userlastname, StandardCharsets.UTF_8)%>');
                        return false;  //run code for c'O'nsultation
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.reportShortcut"/> :
                        popupOscarRx(650, 1024, '<%= request.getContextPath() %>/report/reportindex.jsp', 'reportPage');
                        return false;  //run code for 'R'eports
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.prefShortcut"/> : {
                        popupOscarRx(715, 680, 'providerpreference.jsp?provider_no=<%=loggedInInfo1.getLoggedInProviderNo()%>&start_hour=<%=startHour%>&end_hour=<%=endHour%>&every_min=<%=everyMin%>&mygroup_no=<%=mygroupno%>'); //run code for 'P'references
                        return false;
                    }
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.searchShortcut"/> :
                        popupOscarRx(550, 687, '<%= request.getContextPath() %>/demographic/search.jsp');
                        return false;  //run code for 'S'earch
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.dayShortcut"/> :
                        window.open("providercontrol.jsp?year=<%=curYear%>&month=<%=curMonth%>&day=<%=curDay%><%=viewString%>&displaymode=day&dboperation=searchappointmentday", "_self");
                        return false;  //run code for 'T'oday
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.viewShortcut"/> : {
                        <% if(request.getParameter("viewall")!=null && request.getParameter("viewall").equals("1") ) { %>
                        review('0');
                        return false; //scheduled providers 'V'iew
                        <% } else {  %>
                        review('1');
                        return false; //all providers 'V'iew
                        <% } %>
                    }
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.workflowShortcut"/> :
                        popupOscarRx(700, 1024, '<%= request.getContextPath() %>/oscarWorkflow/WorkFlowList.jsp', '<fmt:setBundle basename="oscarResources"/><fmt:message key="global.workflow"/>');
                        return false; //code for 'W'orkflow
                    default :
                        return;
                }
            }
            if (evt.ctrlKey) {
                switch (evt.keyCode || evt.charCode) {
                    case <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnLogoutShortcut"/> :
                        window.open('<%= request.getContextPath() %>/logout.jsp', '_self');
                        return false;  // 'Q'uit/log out
                    default :
                        return;
                }

            }
        }

    </script>
    <script>
        jQuery(document).ready(function () {
            jQuery('.ds-btn').click(function () {
                //var provider_no = '<%=loggedInInfo1.getLoggedInProviderNo()%>';
                var provider_no = jQuery(this).attr('data-provider_no');
                var y = '<%=request.getParameter("year")%>';
                var m = '<%=request.getParameter("month")%>';
                var d = '<%=request.getParameter("day")%>';
                var sTime = 8;
                var eTime = 20;
                var dateStr = y + '-' + m + '-' + d;
                var url = '<%=request.getContextPath()%>/report/reportdaysheet.jsp?dsmode=all&provider_no=' + provider_no
                    + '&sdate=' + dateStr + '&edate=' + dateStr + '&sTime=' + sTime + '&eTime=' + eTime;
                popupPage(600, 750, url);
                return false;
            });
        });
    </script>
    <!-- end of keycode block -->
    <% if (OscarProperties.getInstance().getBooleanProperty("indivica_hc_read_enabled", "true")) { %>
    <jsp:include page="/hcHandler/hcHandler.html"/>
    <% } %>
    </body>
</html>

<%!
    public boolean checkRestriction(List<MyGroupAccessRestriction> restrictions, String name) {
        for (MyGroupAccessRestriction restriction : restrictions) {
            if (restriction.getMyGroupNo().equals(name))
                return true;
        }
        return false;
    }
%>

<script>
    const contextPath = document.getElementById("contextPath").value;
    const originalInboxLinkClickEvent = "popupInboxManager('" + contextPath + "/documentManager/inboxManage.do?method=prepareForIndexPage&providerNo=<%=loggedInInfo1.getLoggedInProviderNo()%>');return false;";
    const newInboxLinkClickEvent = "popupInboxManager('" + contextPath + "/web/inboxhub/Inboxhub.do?method=displayInboxForm', 800);return false;";

    const originalUnclaimedLabLinkClickEvent = "popupInboxManager('" + contextPath + "/documentManager/inboxManage.do?method=prepareForIndexPage&providerNo=0&searchProviderNo=0&status=N&lname=&fname=&hnum=&pageNum=1&startIndex=0');return false;"
    const newUnclaimedLabLinkClickEvent = "popupInboxManager('" + contextPath + "/web/inboxhub/Inboxhub.do?method=displayInboxForm&unclaimed=1', 800);return false;"

    document.getElementById("inboxLink").addEventListener("mouseup", function(event) {
        if(event.altKey) {
            document.getElementById("inboxLink").setAttribute("onclick", newInboxLinkClickEvent);
        } else {
            document.getElementById("inboxLink").setAttribute("onclick", originalInboxLinkClickEvent);
        }
    });

    document.getElementById("unclaimedLabLink").addEventListener("mouseup", function(event) {
        if(event.altKey) {
            document.getElementById("unclaimedLabLink").setAttribute("onclick", newUnclaimedLabLinkClickEvent);
        } else {
            document.getElementById("unclaimedLabLink").setAttribute("onclick", originalUnclaimedLabLinkClickEvent);
        }
    });
</script>
