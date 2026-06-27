<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<!DOCTYPE html>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.MyGroupAccessRestrictionDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.MyGroupDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ScheduleDateDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider" %>

<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@page import="io.github.carlos_emr.carlos.util.*" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@page import="io.github.carlos_emr.carlos.web.admin.ProviderPreferencesUIBean" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ProviderPreference" %>
<%@ page import="io.github.carlos_emr.carlos.managers.*" %>
<%@ page import="java.util.*,java.text.*,java.net.*,io.github.carlos_emr.*,io.github.carlos_emr.carlos.util.*" %>
<%@ page import="org.apache.commons.lang3.*" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.apache.commons.text.WordUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.*" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.managers.*" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilMisc" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.NavPath" %>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/special_tag.tld" prefix="special" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>

<!-- Struts for i18n -->
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%
    LoggedInInfo loggedInInfo1 = LoggedInInfo.getLoggedInInfoFromSession(request);

    if (session.getAttribute("userrole") == null) {
        response.sendRedirect(request.getContextPath() + "/logoutPage");
        return;
    }

    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_appointment,_day" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_appointment");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_appointment" rights="r" reverse="<%=true%>">
    <%
        response.sendRedirect(request.getContextPath() + "/logoutPage");
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
<jsp:useBean id="as" class="io.github.carlos_emr.carlos.appt.ApptStatusData"/>
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
    CarlosProperties oscarVariables = CarlosProperties.getInstance();
    AppManager appManager = SpringUtils.getBean(AppManager.class);
    io.github.carlos_emr.carlos.managers.PreventionManager providerPreventionManager = SpringUtils.getBean(io.github.carlos_emr.carlos.managers.PreventionManager.class);
%>
<%
    LookupList reasonCodes = lookupListManager.findLookupListByName(loggedInInfo1, "reasonCode");
    if (reasonCodes != null && reasonCodes.getItems() != null) {
        for (LookupListItem lli : reasonCodes.getItems()) {
            reasonCodesMap.put(lli.getId(), lli);
        }
    }

    // are prevention stop sign icons being loaded? This needs to be known when loading the schedule
    pageContext.setAttribute("isPreventionWarningDisabled", providerPreventionManager.isDisabled());
%>
<%!
    //multisite starts =====================
    private boolean bMultisites = io.github.carlos_emr.carlos.commn.IsPropertiesOn.isMultisitesEnable();
    private List<Site> sites = new ArrayList<Site>();
    private List<Site> curUserSites = new ArrayList<Site>();
    private List<String> siteProviderNos = new ArrayList<String>();
    private List<String> siteGroups = new ArrayList<String>();
    private String selectedSite = null;
    private HashMap<String, String> siteBgColor = new HashMap<String, String>();
    private HashMap<String, String> CurrentSiteMap = new HashMap<String, String>();

   private void appendTooltipLine(StringBuilder tooltip, String labelHtml, String value) {
    if (value == null) return;
    String trimmedValue = value.trim(); 
    if (trimmedValue.isEmpty() || "null".equalsIgnoreCase(trimmedValue)) return;
    tooltip
      .append(labelHtml) // <-- DO NOT escape; it's icon HTML we generate
      .append(" ")
      .append(SafeEncode.forHtmlAttribute(trimmedValue))
      .append("<br>");
  }
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
    request.setAttribute("providerPreference", providerPreference);

    StringBuilder eformIds = new StringBuilder();
    for (ProviderPreference.EformLink eform : eFormIdCollection) {
        eformIds = eformIds.append("&eformId=" + eform.getAppointmentScreenEForm());
    }

    StringBuilder ectFormNames = new StringBuilder();
    for (String formName : formNamesList) {
        ectFormNames = ectFormNames.append("&encounterFormName=" + SafeEncode.forUriComponent(formName));
    }
    // end get eform form links

    boolean prescriptionQrCodes = providerPreference.isPrintQrCodeOnPrescriptions();

    String newticklerwarningwindow = null;
    String default_pmm = null;
    String programId_oscarView = null;
//    String ocanWarningWindow = null;
    String caisiBillingPreferenceNotDelete = null;
//    String tklerProviderNo = null;

//    UserProperty userprop = userPropertyDao.getProp(curUser_no, UserProperty.PROVIDER_FOR_TICKLER_WARNING);
//    if (userprop != null) {
//        tklerProviderNo = userprop.getValue();
//    } else {
//        tklerProviderNo = curUser_no;
//    }
//
//    if (io.github.carlos_emr.carlos.commn.IsPropertiesOn.isCaisiEnable() && io.github.carlos_emr.carlos.commn.IsPropertiesOn.propertiesOn("OCAN_warning_window")) {
//        ocanWarningWindow = (String) session.getAttribute("ocanWarningWindow");
//    }

    //Hide old echart link
    boolean showOldEchartLink = true;
    UserProperty oldEchartLink = userPropertyDao.getProp(loggedInInfo1.getLoggedInProviderNo(), UserProperty.HIDE_OLD_ECHART_LINK_IN_APPT);
    if (oldEchartLink != null && "Y".equals(oldEchartLink.getValue())) showOldEchartLink = false;

    programId_oscarView = "0";
    session.setAttribute("programId_oscarView", programId_oscarView);
    int lenLimitedL = 11; //L - long
    if (CarlosProperties.getInstance().getProperty("APPT_SHOW_FULL_NAME", "").equalsIgnoreCase("true")) {
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
    pageContext.setAttribute("scheduleResourceBaseUrl", resourcebaseurl);

    boolean isWeekView = false;
    String provNum = request.getParameter("provider_no");
    if (provNum != null) {
        isWeekView = true;
    }
    if (caisiView != null && "true".equals(caisiView)) {
        isWeekView = false;
    }
    int nProvider;

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
        MiscUtils.getLogger().error("appointmentprovideradminday: missing 'date.EEEyyyyMMdd' bundle key for locale {}; falling back to default format",
                request.getLocale(), e);
        // The fallback parse can itself throw ParseException (same parser, same input).
        // Use the literal strDate as a final fallback so the page still renders
        // rather than propagating an uncaught exception out of the scriptlet.
        try {
            formatDate = UtilDateUtilities.DateToString(inform.parse(strDate), "EEE, yyyy-MM-dd");
        } catch (Exception fallbackEx) {
            MiscUtils.getLogger().error("appointmentprovideradminday: fallback date parse also failed; rendering raw strDate", fallbackEx);
            formatDate = strDate;
        }
    }
    String strYear = "" + year;
    String strMonth = month > 9 ? ("" + month) : ("0" + month);
    String strDay = day > 9 ? ("" + day) : ("0" + day);

    // Timeline indicator variables - shows current time marker on today's schedule
    final int TIMELINE_GRACE_OFFSET_MINUTES = 6; // Grace period to account for page load delay
    SimpleDateFormat formatHour = new SimpleDateFormat("HH");
    SimpleDateFormat formatMin = new SimpleDateFormat("mm");
    SimpleDateFormat formatAdate = new SimpleDateFormat("yyyyMMdd");
    Date curDate = new Date();
    String curHour = formatHour.format(curDate);
    String curMin = formatMin.format(curDate);
    String curDate2 = formatAdate.format(curDate);
    boolean isToday = curDate2.equals(strYear + strMonth + strDay);
    int curH = Integer.parseInt(curHour);
    int totalM = Integer.parseInt(curMin) + curH * 60 - TIMELINE_GRACE_OFFSET_MINUTES;
    boolean isTimeline = CarlosProperties.getInstance().getProperty("display_timeline", "true").equalsIgnoreCase("true");

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


<html>
    <head>
        <title><%=WordUtils.capitalize(userlastname + ", " + org.apache.commons.lang3.StringUtils.substring(userfirstname, 0, 1)) + "-"%><fmt:message key="provider.appointmentProviderAdminDay.title"/></title>
        <script type="text/javascript" src="${pageContext.servletContext.contextPath}/js/global.js"></script>
        <script type="text/javascript" src="${pageContext.servletContext.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script> 
        <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <link rel="stylesheet"
              href="${pageContext.servletContext.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css"
              type="text/css">
        <link rel="stylesheet"
              href="${pageContext.servletContext.contextPath}/css/fontawesome-all.min.css"
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
        <link rel="stylesheet" href="${pageContext.servletContext.contextPath}/css/topnav.css?v=${rand}"
              type="text/css">

        <c:if test="${empty sessionScope.archiveView or sessionScope.archiveView != true}">
            <%!String refresh = io.github.carlos_emr.CarlosProperties.getInstance().getProperty("refresh.appointmentprovideradminday.jsp", "-1");%>
            <%="-1".equals(refresh) ? "" : "<meta http-equiv=\"refresh\" content=\"" + refresh + "\">"%>
        </c:if>


        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/share/javascript/Oscar.js"></script>
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/share/javascript/carlos-ajax.js"></script>
        <link rel="stylesheet" type="text/css" media="all"
              href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui.theme-1.14.2.min.css"/>
        <link rel="stylesheet" type="text/css" media="all"
              href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui-1.14.2.min.css"/>
        <link rel="stylesheet" type="text/css" media="all"
              href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui.structure-1.14.2.min.css"/>
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
                <script src="${pageContext.servletContext.contextPath}/library/jquery/jquery-compat.js"></script>


        <script type="text/javascript" src="<%= request.getContextPath() %>/provider/ViewSchedulePageJs"></script>

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
                postViaForm("${pageContext.request.contextPath}/provider/providercontrol?provider_no=<%=loggedInInfo1.getLoggedInProviderNo()%>&start_hour=<%=startHour%>&end_hour=<%=endHour%>&every_min=<%=everyMin%>&color_template=deepblue&dboperation=updatepreference&displaymode=updatepreference&default_servicetype=<%=defaultServiceType%>&prescriptionQrCodes=<%=prescriptionQrCodes%>&mygroup_no=" + encodeURIComponent(newGroupNo) + "&programId_oscarView=" + encodeURIComponent(programId) + "<%=eformIds.toString()%><%=ectFormNames.toString()%>", "attachment");
            }

            function ts1(s) {
                popupPage(600, 860, ('<%= request.getContextPath() %>/appointment/addappointment?' + s));
            }

            function tsr(s) {
                popupPage(600, 860, ('<%= request.getContextPath() %>/appointment/editappointment?dboperation=search&' + s));
            }

            function goFilpView(s) {
                self.location.href = "<%= request.getContextPath() %>/schedule/FlipView?originalpage=<%= request.getContextPath() %>/provider/providercontrol&startDate=<%=year+"-"+month+"-"+day%>" + "&provider_no=" + s;
            }

            function goWeekView(s) {
                self.location.href = "<%= request.getContextPath() %>/provider/providercontrol?year=<%=year%>&month=<%=month%>&day=<%=day%>&view=0&displaymode=day&dboperation=searchappointmentday&viewall=1&provider_no=" + s;
            }

            function goZoomView(s, n) {
                self.location.href = "<%= request.getContextPath() %>/provider/providercontrol?year=<%=strYear%>&month=<%=strMonth%>&day=<%=strDay%>&view=1&curProvider=" + s + "&curProviderName=" + encodeURIComponent(n) + "&displaymode=day&dboperation=searchappointmentday";
            }

            function findProvider(p, m, d) {
                popupPage(300, 400, "<%= request.getContextPath() %>/provider/ViewReceptionistFindProvider?pyear=" + p + "&pmonth=" + m + "&pday=" + d + "&providername=" + encodeURIComponent(document.findprovider.providername.value));
            }

            function goSearchView(s) {
                popupPage(600, 650, "<%= request.getContextPath() %>/appointment/appointmentsearch?provider_no=" + s);
            }

            function review(key) {
                if (self.location.href.lastIndexOf("?") > 0) {
                    if (self.location.href.lastIndexOf("&viewall=") > 0) a = self.location.href.substring(0, self.location.href.lastIndexOf("&viewall="));
                    else a = self.location.href;
                } else {
                    a = "<%= request.getContextPath() %>/provider/providercontrol?year=" + document.jumptodate.year.value + "&month=" + document.jumptodate.month.value + "&day=" + document.jumptodate.day.value + "&view=0&displaymode=day&dboperation=searchappointmentday&site=" + "<%=(selectedSite==null? "none" : SafeEncode.forJavaScriptBlock(selectedSite) )%>";
                }
                self.location.href = a + "&viewall=" + key;
            }


        </script>
        <style>
            .ds-btn {
                background-color: #f4ead7;
                border: 1px solid #0097cf;
            }

            /* Quick Date Navigation Buttons */
            .quick-nav {
                display: inline-block;
                vertical-align: middle;
            }
            .quick-btn {
                padding: 2px 6px;
                margin: 0 1px;
                font-size: 11px;
                background-color: #f0f0f0;
                border: 1px solid #ccc;
                border-radius: 3px;
                cursor: pointer;
            }
            .quick-btn:hover {
                background-color: #e0e0e0;
                border-color: #999;
            }
            .multiplier-input {
                width: 32px;
                padding: 2px 4px;
                font-size: 11px;
                text-align: center;
                border: 1px solid #ccc;
                border-radius: 3px;
                background-color: #f8f8f8;
                -moz-appearance: textfield;
            }
            .multiplier-input::-webkit-outer-spin-button,
            .multiplier-input::-webkit-inner-spin-button {
                -webkit-appearance: none;
                margin: 0;
            }
            .multiplier-input:focus {
                border-color: #0066cc;
                outline: none;
            }

            /* Toggle Cancelled Appointments */
            #toggleCancelledBtn {
                display: inline-block;
                vertical-align: middle;
                cursor: pointer;
                color: #333;
            }
            #toggleCancelledBtn:hover {
                color: #0066cc;
            }
            #toggleCancelledIcon.cancelled-hidden {
                opacity: 0.5;
            }
            .hideCancelled {
                display: none !important;
            }

            /* Timeline Indicator - Current Time Marker */
            .timeline-indicator {
                height: 2px;
            }
            .timeline-indicator td {
                padding: 0 !important;
                border: none !important;
            }
            .timeline-marker {
                border: none;
                border-top: 2px dotted tomato;
                margin: 0;
                padding: 0;
                opacity: 1;
            }

            /* Clickable Date for Calendar Popup */
            .clickable-date {
                color: inherit;
                text-decoration: none;
                cursor: pointer;
            }
            .clickable-date:hover {
                color: #0066cc;
                text-decoration: underline;
            }
            .clickable-date .dateAppointment {
                border-bottom: 1px dashed #999;
            }
            .clickable-date:hover .dateAppointment {
                border-bottom-color: #0066cc;
            }
        </style>


    </head>
    <%
        if (io.github.carlos_emr.carlos.commn.IsPropertiesOn.isCaisiEnable()) {
    %>
    <body onload="load();tooltipStart()">
    <%
    } else {
    %>
    <body onLoad="showPasswordExpiryWarning();refreshAllTabAlerts();scrollOnLoad();tooltipStart()">
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
        String curProviderString = request.getParameter("curProvider") != null ? "&curProvider=" + SafeEncode.forUriComponent(request.getParameter("curProvider")) : "";
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
    <input type="hidden" id="scheduleLoggedInProviderNo" value="<carlos:encode value='<%= loggedInInfo1.getLoggedInProviderNo() %>' context="htmlAttribute"/>"/>
    <input type="hidden" id="scheduleCurrentDate" value="<carlos:encode value='<%= strYear + "-" + strMonth + "-" + strDay %>' context="htmlAttribute"/>"/>
    <%-- Provider numbers visible on this schedule view, comma-separated, for the quick-search slot finder --%>
    <input type="hidden" id="scheduleVisibleProviderNos" value="<carlos:encode value='<%= java.util.Arrays.stream(curProvider_no).filter(p -> p != null && !p.isEmpty()).collect(java.util.stream.Collectors.joining(",")) %>' context="htmlAttribute"/>"/>
    <%-- Current schedule view parameters needed to rebuild the navigation URL after finding a slot --%>
    <input type="hidden" id="scheduleViewAll" value="<carlos:encode value='<%= request.getParameter("viewall") != null ? request.getParameter("viewall") : "1" %>' context="htmlAttribute"/>"/><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
    <input type="hidden" id="scheduleView" value="<carlos:encode value='<%= request.getParameter("view") != null ? request.getParameter("view") : "0" %>' context="htmlAttribute"/>"/><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>

    <div id="fixedHeaderWrapper">
    <table id="firstTable" class="noprint">
        <tr>

            <td class="icon-container">
                <img alt="CARLOS EMR" src="<%=request.getContextPath()%>/images/oscar_logo_small.png" width="19">
            </td>
            <td id="firstMenu">
                <ul id="navlist">
                    <c:if test="${infirmaryView_isOscar != 'false'}">
                        <% String scheduleNavActiveClass = NavPath.requestPathMatches(request,
                                "/provider/providercontrol", "/provider/appointmentprovideradmin",
                                "/provider/appointmentprovideradminday") ? "nav-active" : ""; %>
                        <% if (request.getParameter("viewall") != null && request.getParameter("viewall").equals("1")) { %>
                        <li class="<%= scheduleNavActiveClass %>">
                            <a href=# onClick="review('0')"
                               title="<fmt:message key="provider.appointmentProviderAdminDay.viewProvAval"/>">
                                <fmt:message key="provider.appointmentProviderAdminDay.schedView"/>
                            </a>
                        </li>
                        <% } else { %>
                        <li class="<%= scheduleNavActiveClass %>">
                            <a href='<%= request.getContextPath() %>/provider/providercontrol?year=<%=curYear%>&month=<%=curMonth%>&day=<%=curDay%>&view=0&displaymode=day&dboperation=searchappointmentday&viewall=1'>
                                <fmt:message key="provider.appointmentProviderAdminDay.schedView"/>
                            </a>
                        </li>
                        <% } %>
                    </c:if>

                    <%
                        if (isMobileOptimized) {
                    %>
                    <!-- Add a menu button for mobile version, which opens menu contents when clicked on -->
                    <li id="menu"><a class="leftButton top" onClick="showHideItem('navlistcontents');">
                        <fmt:message key="global.menu"/></a>
                        <ul id="navlistcontents" style="display:none;">
                            <% } %>

                            <security:oscarSec roleName="<%=roleName$%>" objectName="_search" rights="r">
                                <li id="search">
                                    <caisi:isModuleLoad moduleName="caisi">
                                        <%
                                            String caisiSearch = oscarVariables.getProperty("caisi.search.workflow", "true");
                                            if ("true".equalsIgnoreCase(caisiSearch)) {
                                        %>
                                        <a href="<%= request.getContextPath() %>/PMmodule/ClientSearch2"
                                           TITLE='<fmt:message key="global.searchPatientRecords"/>'><fmt:message key="provider.appointmentProviderAdminDay.search"/></a>

                                        <%
                                        } else {
                                        %>
                                        <a HREF="#" ONCLICK="popupPage2('<%= request.getContextPath() %>/demographic/ViewSearch');return false;"
                                           TITLE='<fmt:message key="global.searchPatientRecords"/>'><fmt:message key="provider.appointmentProviderAdminDay.search"/></a>
                                        <% } %>
                                    </caisi:isModuleLoad>
                                    <caisi:isModuleLoad moduleName="caisi" reverse="true">
                                        <a HREF="#" ONCLICK="popupPage2('<%= request.getContextPath() %>/demographic/ViewSearch');return false;"
                                           TITLE='<fmt:message key="global.searchPatientRecords"/>'><fmt:message key="provider.appointmentProviderAdminDay.search"/></a>
                                    </caisi:isModuleLoad>
                                </li>
                            </security:oscarSec>

                            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                                <oscar:oscarPropertiesCheck property="NOT_FOR_CAISI" value="no" defaultVal="true">
                                    <c:if test="${doctorLinkRights}">
                                        <li>
                                       <a HREF="<%= "1".equals(request.getParameter("scheduleNav")) ? request.getContextPath() + "/web/inboxhub/Inboxhub?method=displayInboxForm&scheduleNav=1" : "#" %>" id="inboxLink">
                                                <span id="oscar_new_lab" title="<fmt:message key="provider.appointmentProviderAdminDay.viewLabReports"/>">
                                                    <oscar:newLab providerNo="<%=loggedInInfo1.getLoggedInProviderNo()%>"><fmt:message key="global.lab"/></oscar:newLab>
                                                </span>
                                            </a>
                                            <oscar:newUnclaimedLab>
                                                <a id="unclaimedLabLink" class="tabalert" HREF="<%= "1".equals(request.getParameter("scheduleNav")) ? request.getContextPath() + "/web/inboxhub/Inboxhub?method=displayInboxForm&unclaimed=1&scheduleNav=1" : "javascript:void(0)" %>"
                                                   title='<fmt:message key="inbox.inboxmanager.msgUnmatched"/>'>U</a>
                                            </oscar:newUnclaimedLab>
                                        </li>
                                    </c:if>
                                </oscar:oscarPropertiesCheck>
                            </caisi:isModuleLoad>

                            <%
                                String loggedInProviderNo = loggedInInfo1.getLoggedInProviderNo();
                                Provider loggedInProvider = loggedInInfo1.getLoggedInProvider();
                                String loggedInProviderFirstName = loggedInProvider != null ? loggedInProvider.getFirstName() : null;
                                String loggedInProviderLastName = loggedInProvider != null ? loggedInProvider.getLastName() : null;
                                String loggedInProviderName = StringUtils.trim(
                                        StringUtils.defaultString(loggedInProviderFirstName)
                                                + " "
                                                + StringUtils.defaultString(loggedInProviderLastName));
                                String encodedLoggedInProviderName = URLEncoder.encode(loggedInProviderName, StandardCharsets.UTF_8);
                                String scheduleMessengerUrl = request.getContextPath() + "/messenger/DisplayMessages?providerNo=" + loggedInProviderNo + "&userName=" + encodedLoggedInProviderName;
                                String scheduleConsultationUrl = request.getContextPath() + "/encounter/IncomingConsultation?providerNo=" + loggedInProviderNo + "&userName=" + encodedLoggedInProviderName;
                                String scheduleDocumentReportUrl = request.getContextPath() + "/documentManager/ViewDocumentReport?function=providers&functionid=" + SafeEncode.forUriComponent(loggedInProviderNo);
                                String scheduleReportIndexUrl = request.getContextPath() + "/report/ViewReportindex";
                                String scheduleAdministrationUrl = request.getContextPath() + "/administration";
                                String scheduleTicklerUrl = request.getContextPath() + "/tickler/ViewTicklerMain";
                                String scheduleMessengerUrlForJsAttribute = SafeEncode.forJavaScriptAttribute(scheduleMessengerUrl);
                                String scheduleConsultationUrlForJsAttribute = SafeEncode.forJavaScriptAttribute(scheduleConsultationUrl);
                                String scheduleDocumentReportUrlForJsAttribute = SafeEncode.forJavaScriptAttribute(scheduleDocumentReportUrl);
                                String scheduleReportIndexUrlForJsAttribute = SafeEncode.forJavaScriptAttribute(scheduleReportIndexUrl);
                                String scheduleAdministrationUrlForJsAttribute = SafeEncode.forJavaScriptAttribute(scheduleAdministrationUrl);
                                String scheduleTicklerUrlForJsAttribute = SafeEncode.forJavaScriptAttribute(scheduleTicklerUrl);
                            %>
                            <fmt:message var="ticklerTitle" key="global.tickler"/>
                            <security:oscarSec roleName="<%=roleName$%>" objectName="_tickler" rights="r">
                                <li>
                                    <a HREF="#"
                                       ONCLICK="return openScheduleSection('<%=scheduleTicklerUrlForJsAttribute%>', function(u){ popupPage2(u,'${carlos:forJavaScript(ticklerTitle)}'); }, event);"
                                       TITLE='${carlos:forHtmlAttribute(ticklerTitle)}'>
                                        <span id="oscar_new_tickler">
                                            <oscar:newTickler providerNo="<%=loggedInInfo1.getLoggedInProviderNo()%>"><fmt:message key="global.btntickler"/></oscar:newTickler>
                                        </span></a>
                                </li>
                            </security:oscarSec>

                            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                                <security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="r">
                                    <li>
                                        <a HREF="#"
                                           ONCLICK="return openScheduleSection('<%=scheduleMessengerUrlForJsAttribute%>', function(u){ popupOscarRx(600,1024,u); }, event);"
                                           title="<fmt:message key="global.messenger"/>">
                                              <span id="oscar_new_msg">
                                                  <oscar:newMessage providerNo="<%=loggedInInfo1.getLoggedInProviderNo()%>"><fmt:message key="global.msg"/></oscar:newMessage>
                                              </span></a>
                                    </li>
                                </security:oscarSec>
                            </caisi:isModuleLoad>
                            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                                <security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="r">
                                    <li id="con">
                                        <a HREF="#"
                                           ONCLICK="return openScheduleSection('<%=scheduleConsultationUrlForJsAttribute%>', function(u){ popupOscarRx(625,1024,u); }, event);"
                                           title="<fmt:message key="provider.appointmentProviderAdminDay.viewConReq"/>">
                                              <span id="oscar_aged_consults"><fmt:message key="global.con"/></span></a>
                                    </li>
                                </security:oscarSec>
                            </caisi:isModuleLoad>
                            <%
                                boolean hide_eConsult = CarlosProperties.getInstance().isPropertyActive("hide_eConsult_link");
                                if ("on".equalsIgnoreCase(prov) && !hide_eConsult) {
                            %>
                            <li id="econ">
                                <a href="#" onclick="popupOscarRx(625, 1024, '<%=request.getContextPath()%>/encounter/econsult')"
                                   title="<fmt:message key="provider.appointmentProviderAdminDay.eConsult"/>">
                                    <span><fmt:message key="provider.appointmentProviderAdminDay.eConsult"/></span></a>
                            </li>
                            <% } %>

                            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                                <security:oscarSec roleName="<%=roleName$%>" objectName="_edoc" rights="r">
                                    <li>
                                        <a HREF="#"
                                           onclick="return openScheduleSection('<%=scheduleDocumentReportUrlForJsAttribute%>', function(u){ popup('800', '1200', u, 'edocView'); }, event);"
                                           TITLE='<fmt:message key="provider.appointmentProviderAdminDay.viewEdoc"/>'><fmt:message key="global.edoc"/></a>
                                    </li>
                                </security:oscarSec>
                            </caisi:isModuleLoad>

                            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                                <security:oscarSec roleName="<%=roleName$%>" objectName="_report" rights="r">
                                    <li>
                                        <a HREF="#"
                                           ONCLICK="return openScheduleSection('<%=scheduleReportIndexUrlForJsAttribute%>', function(u){ popupPage2(u,'reportPage'); }, event);"
                                           TITLE='<fmt:message key="global.genReport"/>'><fmt:message key="global.report"/></a>
                                    </li>
                                </security:oscarSec>
                            </caisi:isModuleLoad>

                            <oscar:oscarPropertiesCheck property="referral_menu" value="yes">
                                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.misc" rights="r">
                                    <li id="ref">
                                        <a href="#"
                                           onclick="popupPage(550,800,'<%=request.getContextPath()%>/admin/ManageBillingReferral');return false;"><fmt:message key="global.manageReferrals"/></a>
                                    </li>
                                </security:oscarSec>
                            </oscar:oscarPropertiesCheck>

                            <fmt:message var="workflowTitle" key="global.workflow"/>
                            <oscar:oscarPropertiesCheck property="WORKFLOW" value="yes">
                                <li><a href="javascript:void(0)"
                                       onClick="popup(800,1200,'<%= request.getContextPath() %>/oscarWorkflow/WorkFlowList','${carlos:forJavaScript(workflowTitle)}')"><fmt:message key="global.btnworkflow"/>
                                </a></li>
                            </oscar:oscarPropertiesCheck>


                            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                                <security:oscarSec roleName="<%=roleName$%>"
                                                   objectName="_admin,_admin.userAdmin,_admin.schedule,_admin.billing,_admin.resource,_admin.reporting,_admin.backup,_admin.messenger,_admin.eform,_admin.encounter,_admin.misc,_admin.fax,_admin.flowsheet"
                                                   rights="r">

                                    <li id="admin2">
                                        <a href="javascript:void(0)" id="admin-panel"
                                           title="<fmt:message key="admin.admin.page.title"/>"
                                       onclick="return openScheduleSection('<%=scheduleAdministrationUrlForJsAttribute%>', function(u){ newWindow(u,'admin'); }, event);"><fmt:message key="provider.mainMenu.administration"/></a>
                                    </li>

                                </security:oscarSec>
                            </caisi:isModuleLoad>

                            <security:oscarSec roleName="<%=roleName$%>" objectName="_dashboardDisplay" rights="r">
                                <%
                                    DashboardManager dashboardManager = SpringUtils.getBean(DashboardManager.class);
                                    List<io.github.carlos_emr.carlos.commn.model.Dashboard> dashboards = dashboardManager.getActiveDashboards(loggedInInfo1);
                                    pageContext.setAttribute("dashboards", dashboards);
                                %>

                                <li id="dashboardList">
                                    <div class="dropdown">
                                        <a href="#" class="dashboardBtn"><fmt:message key="provider.mainMenu.dashboard"/></a>
                                        <div class="dashboardDropdown">
                                            <ul>
                                                <c:forEach items="${ dashboards }" var="dashboard">
                                                    <li>
                                                        <a href="javascript:void(0)"
                                                           onclick="newWindow('<%=request.getContextPath()%>/web/dashboard/display/DashboardDisplay?method=getDashboard&dashboardId=${ dashboard.id }','dashboard')">
                                                            ${carlos:forHtml(dashboard.name)}
                                                        </a>
                                                    </li>
                                                </c:forEach>
                                                <security:oscarSec roleName="<%=roleName$%>"
                                                                   objectName="_dashboardCommonLink" rights="r">
                                                    <li>
                                                        <a href="javascript:void(0)"
                                                           onclick="newWindow('<%=request.getContextPath()%>/web/dashboard/display/sharedOutcomesDashboard','shared_dashboard')">
                                                        <fmt:message key="provider.mainMenu.commonDashboard"/>
                                                        </a>
                                                    </li>
                                                </security:oscarSec>
                                            </ul>
                                        </div>

                                    </div>
                                </li>

                            </security:oscarSec>

                            <li id="helpLink">
                                <a href="javascript:void(0)"
                                   onClick="popupPage(600,750,'${carlos:forJavaScriptAttribute(scheduleResourceBaseUrl)}');return false;"><fmt:message key="global.help"/></a>
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
                        <a title="<fmt:message key="ScratchPad.title"/>" href="javascript: function myFunction() {return false; }"
                           onClick="popup(800,1200,'<%= request.getContextPath() %>/Scratch','scratch')">
                            		<span>
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
                           onClick="popupPage(800,1000,'<%= request.getContextPath() %>/provider/ViewProviderPreference?provider_no=<carlos:encode value='<%= loggedInInfo1.getLoggedInProviderNo() %>' context="uriComponent"/>')"
                           title='<fmt:message key="provider.appointmentProviderAdminDay.msgSettings"/>'>
                            <span>
                                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor"
                                     class="bi bi-person-fill" viewBox="0 0 16 16">
                                  <path d="M3 14s-1 0-1-1 1-4 6-4 6 3 6 4-1 1-1 1zm5-6a3 3 0 1 0 0-6 3 3 0 0 0 0 6"></path>
                                </svg>
						    </span>
                            <div>
                                <carlos:encode value='<%= userfirstname + " " + userlastname %>' context="html"/>
                            </div>
                        </a>
                    </li>
                </ul>
                <div>
                    <a id="logoutButton" title="<fmt:message key="global.btnLogout"/>" href="<%= request.getContextPath() %>/logoutPage">
                        <span class="fa-solid fa-power-off"></span>
                    </a>
                </div>
            </td>

        </tr>
    </table>

    <table id="scheduleNavigation">
        <tr id="ivoryBar">
            <td id="dateAndCalendar">
                <span id="quickSearchWrapper" class="quick-search-wrapper noprint">
                    <span class="quick-search-input-wrapper">
                        <input type="text" id="quickSearch" name="quickSearch"
                               placeholder="<fmt:message key='provider.appointmentProviderAdminDay.quickSearch.placeholder'/>" autocomplete="off"
                               aria-label="<fmt:message key='provider.appointmentProviderAdminDay.quickSearch.ariaLabel'/>"
                               aria-autocomplete="list" aria-expanded="false" aria-controls="quickSearchDropdown"
                               role="combobox">
                        <button type="button" id="quickSearchClear" class="quick-search-clear"
                                aria-label="<fmt:message key='provider.appointmentProviderAdminDay.quickSearch.clearTitle'/>"
                                title="<fmt:message key='provider.appointmentProviderAdminDay.quickSearch.clearTitle'/>"
                                style="display:none;">
                            <i class="fa-solid fa-xmark"></i>
                        </button>
                    </span>
                    <div id="quickSearchDropdown" class="quick-search-dropdown" role="listbox" aria-live="polite" aria-hidden="true" style="display:none;"></div>
                    <form id="quickSearchCsrfForm" method="post"
                          action="<%= request.getContextPath() %>/demographic/SearchDemographic"
                          style="display:none;"></form>
                </span>
                <a class="redArrow"
                   href="<%= request.getContextPath() %>/provider/providercontrol?year=<%=year%>&month=<%=month%>&day=<%=isWeekView?(day-7):(day-1)%><%=viewString%>&displaymode=day&dboperation=searchappointmentday<%= isWeekView ? "&provider_no=" + SafeEncode.forUriComponent(io.github.carlos_emr.carlos.util.StringUtils.noNull(provNum)) : "" %>&viewall=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(viewall) %>' context="uriComponent"/>">
                    <span class="fa-solid fa-backward-step"
                          title="<fmt:message key="provider.appointmentProviderAdminDay.viewPrevDay"/>"></span>
                </a>
                <%
                    String calendarUrl = request.getContextPath() + "/share/CalendarPopup?urlfrom=" + request.getContextPath() + "/provider/providercontrol" + "&year=" + strYear + "&month=" + strMonth + "&param=" + URLEncoder.encode("&view=0&displaymode=day&dboperation=searchappointmentday&viewall=" + viewall, "UTF-8");

                    if (isWeekView) {
                        calendarUrl += URLEncoder.encode("&provider_no=" + provNum, "UTF-8");
                    }
                %>
                <b><a href="#" class="clickable-date" onclick="popupPage(425,430,'<%=calendarUrl%>'); return false;"
                      title="<fmt:message key="provider.appointmentProviderAdminDay.clickToOpenCalendar"/>">
                    <span class="dateAppointment"><%
                    if (isWeekView) {
                %><fmt:message key="provider.appointmentProviderAdminDay.week"/> <%=week%><%
                } else {
                %><%=formatDate%><%
                    }
                %></span></a></b>
                <a class="redArrow"
                   href="<%= request.getContextPath() %>/provider/providercontrol?year=<%=year%>&month=<%=month%>&day=<%=isWeekView?(day+7):(day+1)%><%=viewString%>&displaymode=day&dboperation=searchappointmentday<%= isWeekView ? "&provider_no=" + SafeEncode.forUriComponent(io.github.carlos_emr.carlos.util.StringUtils.noNull(provNum)) : "" %>&viewall=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(viewall) %>' context="uriComponent"/>">
                    <span class="fa-solid fa-forward-step"
                          title="<fmt:message key="provider.appointmentProviderAdminDay.viewNextDay"/>"></span>
                </a>

                <!-- Quick Date Navigation Shortcuts with Multiplier -->
                <fmt:message key="provider.appointmentProviderAdminDay.weekLetter" var="weekLetter"/>
                <fmt:message key="appointment.addappointment.msgM" var="msgM"/>
                <fmt:message key="provider.appointmentProviderAdminDay.monthBack" var="monthBackTitle"/>
                <fmt:message key="provider.appointmentProviderAdminDay.weekBack" var="weekBackTitle"/>
                <fmt:message key="provider.appointmentProviderAdminDay.multiplier" var="multiplierTitle"/>
                <fmt:message key="provider.appointmentProviderAdminDay.weekForward" var="weekForwardTitle"/>
                <fmt:message key="provider.appointmentProviderAdminDay.monthForward" var="monthForwardTitle"/>
                <fmt:message key="provider.appointmentProviderAdminDay.weeks" var="weeksLabel"/>
                <fmt:message key="provider.appointmentProviderAdminDay.months" var="monthsLabel"/>
                <fmt:message key="provider.appointmentProviderAdminDay.findProvider" var="findProviderTitle"/>
                <fmt:message key="provider.appointmentProviderAdminDay.enterLastname" var="enterLastnameLabel"/>
                <span class="quick-nav noprint" style="margin-left: 10px;">
                    <input type="button" value="${carlos:forHtmlAttribute(msgM)}-" class="quick-btn" onclick="getLocation('monthBackward', document.getElementById('dateMultiplier').value)" title="${carlos:forHtmlAttribute(monthBackTitle)}"/>
                    <input type="button" value="${carlos:forHtmlAttribute(weekLetter)}-" class="quick-btn" onclick="getLocation('weekBackward', document.getElementById('dateMultiplier').value)" title="${carlos:forHtmlAttribute(weekBackTitle)}"/>
                    <input type="number" id="dateMultiplier" value="1" min="1" max="99" class="multiplier-input" title="${carlos:forHtmlAttribute(multiplierTitle)}"/>
                    <script>
                      (function() {
                        'use strict';
                        var multiplierInput = document.getElementById('dateMultiplier');
                        if (!multiplierInput) {
                          return;
                        }

                        // Load saved value from localStorage
                        try {
                          var savedValue = localStorage.getItem('dateMultiplier');
                          if (savedValue) {
                            var numericValue = parseInt(savedValue, 10);
                            if (!isNaN(numericValue) && numericValue >= 1 && numericValue <= 99) {
                              multiplierInput.value = numericValue;
                            }
                          }
                        } catch (e) {
                          console.warn('Could not load dateMultiplier from localStorage.', e);
                        }

                        // Save value to localStorage on change
                        multiplierInput.addEventListener('input', function() {
                          try {
                            localStorage.setItem('dateMultiplier', this.value);
                          } catch (e) {
                            console.warn('Could not save dateMultiplier to localStorage.', e);
                          }
                        });
                      })();
                    </script>
                    <input type="button" value="${carlos:forHtmlAttribute(weekLetter)}+" class="quick-btn" onclick="getLocation('weekForward', document.getElementById('dateMultiplier').value)" title="${carlos:forHtmlAttribute(weekForwardTitle)}"/>
                    <input type="button" value="${carlos:forHtmlAttribute(msgM)}+" class="quick-btn" onclick="getLocation('monthForward', document.getElementById('dateMultiplier').value)" title="${carlos:forHtmlAttribute(monthForwardTitle)}"/>
                    |
                    <input type="button" value="2${carlos:forHtmlAttribute(weekLetter)}" class="quick-btn" onclick="getLocation('weekForward', 2)" title="2 ${carlos:forHtmlAttribute(weeksLabel)}"/>
                    <input type="button" value="4${carlos:forHtmlAttribute(weekLetter)}" class="quick-btn" onclick="getLocation('weekForward', 4)" title="4 ${carlos:forHtmlAttribute(weeksLabel)}"/>
                    <input type="button" value="3${carlos:forHtmlAttribute(msgM)}" class="quick-btn" onclick="getLocation('monthForward', 3)" title="3 ${carlos:forHtmlAttribute(monthsLabel)}"/>
                    <input type="button" value="6${carlos:forHtmlAttribute(msgM)}" class="quick-btn" onclick="getLocation('monthForward', 6)" title="6 ${carlos:forHtmlAttribute(monthsLabel)}"/>
                </span>

                <!-- Toggle Cancelled Appointments -->
                <fmt:message var="hideCancelledTitle" key="provider.appointmentProviderAdminDay.hideCancelled"/>
                <fmt:message var="showCancelledTitle" key="provider.appointmentProviderAdminDay.showCancelled"/>
                <a id="toggleCancelledBtn" href="javascript:void(0)" onclick="toggleCancelled();" class="noprint" style="margin-left: 10px;"
                   title="${carlos:forHtmlAttribute(hideCancelledTitle)}"
                   data-title-hide="${carlos:forHtmlAttribute(hideCancelledTitle)}"
                   data-title-show="${carlos:forHtmlAttribute(showCancelledTitle)}">
                    <span id="toggleCancelledIcon">
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-eye" viewBox="0 0 16 16">
                            <path d="M16 8s-3-5.5-8-5.5S0 8 0 8s3 5.5 8 5.5S16 8 16 8M1.173 8a13 13 0 0 1 1.66-2.043C4.12 4.668 5.88 3.5 8 3.5s3.879 1.168 5.168 2.457A13 13 0 0 1 14.828 8q-.086.13-.195.288c-.335.48-.83 1.12-1.465 1.755C11.879 11.332 10.119 12.5 8 12.5s-3.879-1.168-5.168-2.457A13 13 0 0 1 1.172 8z"/>
                            <path d="M8 5.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5M4.5 8a3.5 3.5 0 1 1 7 0 3.5 3.5 0 0 1-7 0"/>
                        </svg>
                    </span>
                </a>

                <c:if test="${infirmaryView_isOscar != 'false'}">
                    <% if (request.getParameter("viewall") != null && request.getParameter("viewall").equals("1")) { %>
                    <u><a href=# onClick="review('0')"
                          title="<fmt:message key="provider.appointmentProviderAdminDay.viewAllProv"/>"><fmt:message key="provider.appointmentProviderAdminDay.schedView"/></a></u>

                    <%} else {%>
                    <u><a href=# onClick="review('1')"
                          title="<fmt:message key="provider.appointmentProviderAdminDay.viewAllProv"/>"><fmt:message key="provider.appointmentProviderAdminDay.viewAll"/></a></u>
                    <%}%>
                </c:if>

                <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_day" rights="r">
                        <a class="rightButton top"
                           href="<%= request.getContextPath() %>/provider/providercontrol?year=<%=curYear%>&month=<%=curMonth%>&day=<%=curDay%><%=viewString%>&displaymode=day&dboperation=searchappointmentday"
                           TITLE='<fmt:message key="provider.appointmentProviderAdminDay.viewDaySched"/>'><fmt:message key="global.today"/></a>
                    </security:oscarSec>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_month" rights="r">

                        <a
                                href="<%= request.getContextPath() %>/provider/providercontrol?year=<%=year%>&month=<%=month%>&day=1<%=viewString%>&displaymode=month&dboperation=searchappointmentmonth"
                                TITLE='<fmt:message key="provider.appointmentProviderAdminDay.viewMonthSched"/>'><fmt:message key="global.month"/></a>

                    </security:oscarSec>

                </caisi:isModuleLoad>

            </td>

            <td class="title noprint">

                <%
                    if (isWeekView) {
                        for (int provIndex = 0; provIndex < numProvider; provIndex++) {
                            if (curProvider_no[provIndex].equals(provNum)) {
                %>
                <carlos:encode value='<%= curProviderName[provIndex] %>' context="html"/>
                <%
                            }
                        }
                    } %>

                    <%--                    else {--%>
                    <%--                    if (view == 1) {--%>
                    <%--                %>--%>
                    <%--                <a href='<%= request.getContextPath() %>/provider/providercontrol?year=<%=strYear%>&month=<%=strMonth%>&day=<%=strDay%>&view=0&displaymode=day&dboperation=searchappointmentday'><fmt:message--%>
                    <%--                        key="providers.appointmentProviderAdminDay.grpView"/></a>--%>
                    <%--                <% } %>--%>
                    <%--&lt;%&ndash;                <% if (!isMobileOptimized) { %> <fmt:message key="global.hello"/> <% } %>&ndash;%&gt;--%>
                    <%--&lt;%&ndash;                <% out.println(userfirstname + " " + userlastname); %>&ndash;%&gt;--%>
                    <%--                <%} %>--%>
            </td>


            <td id="group">

                <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                    <div>
                        <form method="post" name="findprovider"
                              onSubmit="findProvider(<%=year%>,<%=month%>,<%=day%>);return false;"
                              target="apptReception"
                              action="<%= request.getContextPath() %>/provider/ViewReceptionistFindProvider">
                            <INPUT TYPE="text" NAME="providername" VALUE=""
                                   maxlength="10" class="noprint" title="${carlos:forHtmlAttribute(findProviderTitle)}" placeholder="${carlos:forHtmlAttribute(enterLastnameLabel)}">
                            <INPUT TYPE="SUBMIT" NAME="Go"
                                   VALUE='<fmt:message key="provider.appointmentprovideradminmonth.btnGo"/>'
                                   class="noprint" onClick="findProvider(<%=year%>,<%=month%>,<%=day%>);return false;">
                        </form>
                    </div>
                </caisi:isModuleLoad>
                <div>
                    <form name="appointmentForm">
                        <% if (isWeekView) { %>
                        <fmt:message key="provider.appointmentProviderAdminDay.provider"/>:
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
                                                var newGroupNo = "<carlos:encode value='<%= mygroupno == null ? ".default" : mygroupno %>' context="javaScriptBlock"/>";
                                                postViaForm("<%= request.getContextPath() %>/provider/providercontrol?provider_no=<%=loggedInInfo1.getLoggedInProviderNo()%>&start_hour=<%=startHour%>&end_hour=<%=endHour%>&every_min=<%=everyMin%>&color_template=deepblue&dboperation=updatepreference&displaymode=updatepreference&mygroup_no=" + encodeURIComponent(newGroupNo) + "&site=" + encodeURIComponent(siteName), "attachment");
                                            }
                                        </script>

                                        <select id="site" name="site" onchange="changeSite(this)"
                                                style="background-color: <%=( selectedSite == null || siteBgColor.get(selectedSite) == null ? "#FFFFFF" : siteBgColor.get(selectedSite))%>">
                                            <option value="none" style="background-color:white"><fmt:message key="provider.appointmentprovideradminmonth.formAllClinics"/></option>
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
                                        <span><fmt:message key="global.group"/>:</span>

                                        <%
                                            List<MyGroupAccessRestriction> restrictions = myGroupAccessRestrictionDao.findByProviderNo(loggedInInfo1.getLoggedInProviderNo());
                                        %>
                                        <select id="mygroup_no" name="mygroup_no" onChange="changeGroup(this)">
                                            <option value='.<fmt:message key="global.default"/>'>.<fmt:message key="global.default"/></option>


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
                                                    <carlos:encode value='<%= p.getFormattedName() %>' context="html"/>
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
                                                    <carlos:encode value='<%= g.getId().getMyGroupNo() %>' context="html"/>
                                                </option>
                                                <%
                                                        }
                                                    }

                                                    for (Provider p : providerDao.getActiveProviders()) {
                                                        boolean skip = checkRestriction(restrictions, p.getProviderNo());

                                                        if (!skip && (!bMultisites || siteProviderNos == null || siteProviderNos.size() == 0 || siteProviderNos.contains(p.getProviderNo()))) {
                                                %>
                                                <option value='<%=p.getProviderNo()%>' <%=mygroupno.equals(p.getProviderNo()) ? "selected" : ""%>>
                                                    <carlos:encode value='<%= p.getFormattedName() %>' context="html"/>
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
    </div><!-- end fixedHeaderWrapper -->

    <script>
        jQuery(document).ready(function () {
            jQuery.get("<%=request.getContextPath()%>/SystemMessage?method=view", "html", function (data, textStatus) {
                jQuery("#system_message").html(data);
            });
            jQuery.get("<%=request.getContextPath()%>/FacilityMessage?method=view", "html", function (data, textStatus) {
                jQuery("#facility_message").html(data);
            });
        });
    </script>

    <div id="system_message"></div>
    <div id="facility_message"></div>

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
                                        MiscUtils.getLogger().error("appointmentprovideradminday weekday loop: missing 'date.EEEyyyyMMdd' bundle key for locale {}; falling back to default format",
                                                request.getLocale(), e);
                                        try {
                                            formatDate = UtilDateUtilities.DateToString(inform.parse(strDate), "EEE, yyyy-MM-dd");
                                        } catch (Exception fallbackEx) {
                                            MiscUtils.getLogger().error("appointmentprovideradminday weekday loop: fallback date parse also failed; rendering raw strDate", fallbackEx);
                                            formatDate = strDate;
                                        }
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

                                //viewall function - when viewall=0, hide other providers without a schedule
                                //but always show the logged-in provider's own schedule (not grayed out)
                                if (request.getParameter("viewall") == null || request.getParameter("viewall").equals("0")) {
                                    if (sd == null || "0".equals(String.valueOf(sd.getAvailable()))) {
                                        if (nProvider != me) continue;
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
                                            <b><a href="<%= request.getContextPath() %>/provider/providercontrol?year=<%=year%>&month=<%=month%>&day=<%=day%>&view=0&displaymode=day&dboperation=searchappointmentday"><%=formatDate%>
                                            </a></b>
                                            <%
                                            } else {
                                            %>
                                            <input type='button'
                                                   value="<fmt:message key="provider.appointmentProviderAdminDay.weekLetter"/>"
                                                   name='weekview'
                                                   onClick="goWeekView('<%=curProvider_no[nProvider]%>')"
                                                   title="<fmt:message key="provider.appointmentProviderAdminDay.weekView"/>"
                                                   style="color:black" class="noprint">
                                            <% if (CarlosProperties.getInstance().isPropertyActive("view.appointmentdaysheetbutton")) { %>
                                            <input type='button' value="DS" name='daysheetview'
                                                   onClick=goDaySheet('<%=curProvider_no[nProvider]%>')
                                                   title="<fmt:message key="report.reportindex.formDaySheet"/>" style="color:black">
                                            <% } %>
                                            <input type='button'
                                                   value="<fmt:message key="provider.appointmentProviderAdminDay.searchLetter"/>"
                                                   name='searchview'
                                                   onClick="goSearchView('<%=curProvider_no[nProvider]%>')"
                                                   title="<fmt:message key="provider.appointmentProviderAdminDay.searchView"/>"
                                                   style="color:black" class="noprint">
                                            <input type='radio' name='flipview' class="noprint"
                                                   onClick="goFilpView('<%=curProvider_no[nProvider]%>')"
                                                   title="<fmt:message key="schedule.scheduleflipview.title"/>">
                                            <a href=#
                                               onClick="goZoomView('<%=curProvider_no[nProvider]%>','<carlos:encode value='<%= curProviderName[nProvider] %>' context="javaScriptAttribute"/>')"
                                               onDblClick="goFilpView('<%=curProvider_no[nProvider]%>')"
                                               title='<fmt:message key="provider.appointmentProviderAdminDay.zoomView"/>'>
                                                <carlos:encode value='<%= curProviderName[nProvider] + " (" + appointmentCount + ") " %>' context="html"/>
                                            </a>
                                                <oscar:oscarPropertiesCheck value="yes" property="TOGGLE_REASON_BY_PROVIDER" defaultVal="yes">
                                                    <a href="#"
                                                       class="expand-reason-btn"
                                                       data-provider="<%= curProvider_no[nProvider] %>"
                                                       onclick="toggleReason(event, '<%= curProvider_no[nProvider] %>');"
                                                       title="<fmt:message key='provider.appointmentProviderAdminDay.expandreason'/>"
                                                    >*</a>
                                                </oscar:oscarPropertiesCheck>

                                            <% } %>

                                            <%
                                                if (!userAvail) {
                                            %>
                                            [<fmt:message key="provider.appointmentProviderAdminDay.msgNotOnSched"/>]
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

                                                    String useProgramLocation = CarlosProperties.getInstance().getProperty("useProgramLocation");
                                                    String moduleNames = CarlosProperties.getInstance().getProperty("ModuleNames");
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

                                                        // Timeline indicator - show current time marker on today's schedule
                                                        if ((ih >= totalM) && (ih < (totalM + depth)) && !isWeekView && isToday && isTimeline) {
                                                %>
                                                <tr class="timeline-indicator noprint">
                                                    <td colspan="8"><hr class="timeline-marker"/></td>
                                                </tr>
                                                <%
                                                        }
                                                %>
                                                <tr>
                                                                                                           <c:set var="__enc_1"><carlos:encode value='<%= curProvider_no[nProvider] %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_2"><carlos:encode value='<%= strYear %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_3"><carlos:encode value='<%= strMonth %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_4"><carlos:encode value='<%= strDay %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_5"><carlos:encode value='<%= (hourCursor>9?(""+hourCursor):("0"+hourCursor))+":"+ (minuteCursor<10?"0":"") +minuteCursor %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_6"><carlos:encode value='<%= (hourCursor>9?(""+hourCursor):("0"+hourCursor))+":"+(minuteCursor+depth-1) %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_7"><carlos:encode value='<%= dateTimeCodeBean.get("duration"+hourmin.toString()) != null ? (String) dateTimeCodeBean.get("duration"+hourmin.toString()) : "" %>' context="uriComponent"/></c:set>
    <td class="<%=bColorHour?"scheduleTime00":"scheduleTimeNot00"%>">
                                                        <a href="javascript:void(0)"
                                                           onClick="confirmPopupPage(600,860, '<%= request.getContextPath() %>/appointment/addappointment?provider_no=<carlos:encode value='${__enc_1}' context="javaScriptAttribute"/>&bFirstDisp=<%=true%>&year=<carlos:encode value='${__enc_2}' context="javaScriptAttribute"/>&month=<carlos:encode value='${__enc_3}' context="javaScriptAttribute"/>&day=<carlos:encode value='${__enc_4}' context="javaScriptAttribute"/>&start_time=<carlos:encode value='${__enc_5}' context="javaScriptAttribute"/>&end_time=<carlos:encode value='${__enc_6}' context="javaScriptAttribute"/>&duration=<carlos:encode value='${__enc_7}' context="javaScriptAttribute"/>','<carlos:encode value='<%= dateTimeCodeBean.get("confirm"+hourmin.toString()) != null ? (String) dateTimeCodeBean.get("confirm"+hourmin.toString()) : "" %>' context="javaScriptAttribute"/>','<carlos:encode value='<%= allowDay %>' context="javaScriptAttribute"/>','<carlos:encode value='<%= allowWeek %>' context="javaScriptAttribute"/>');return false;"
                                                           title='<%=MyDateFormat.getTimeXX_XXampm(hourCursor +":"+ (minuteCursor<10?"0":"")+minuteCursor)%> - <%=MyDateFormat.getTimeXX_XXampm(hourCursor +":"+((minuteCursor+depth-1)<10?"0":"")+(minuteCursor+depth-1))%>'
                                                           class="adhour">
                                                            <%=(hourCursor < 10 ? "0" : "") + hourCursor + ":"%><%=(minuteCursor < 10 ? "0" : "") + minuteCursor%>&nbsp;</a>
                                                    </td>
                                                    <td class="hourmin"
                                                        width='1%' <%=dateTimeCodeBean.get("color" + hourmin.toString()) != null ? ("bgcolor='" + SafeEncode.forHtmlAttribute((String) dateTimeCodeBean.get("color" + hourmin.toString())) + "'") : ""%>
                                                        title='<carlos:encode value='<%= dateTimeCodeBean.get("description"+hourmin.toString()) != null ? (String) dateTimeCodeBean.get("description"+hourmin.toString()) : "" %>' context="htmlAttribute"/>'>
                                                                        <span color='<%=(dateTimeCodeBean.get("color"+hourmin.toString())!=null && !dateTimeCodeBean.get("color"+hourmin.toString()).equals(bgcolordef) )?"black":"white"%>'><carlos:encode value='<%= hourmin.toString() %>' context="html"/>
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

                                                                      String reason = appointment.getReason() != null ? appointment.getReason().trim() : "";
                                                                      String notes = appointment.getNotes() != null ? appointment.getNotes().trim() : "";
                                                                      String status = appointment.getStatus() != null ? appointment.getStatus().trim() : "";
                                                                      String sitename = appointment.getLocation() != null ? appointment.getLocation().trim() : "";
                                                                      String type = appointment.getType() != null ? appointment.getType() : "";
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

                                                                      if ( "yes".equalsIgnoreCase(CarlosProperties.getInstance().getProperty("SHOW_APPT_TYPE_WITH_REASON")) ) {
                                                                        reasonCodeName = type
                                                                                + ((type != null && ! type.isEmpty()) ? " : " : "")
                                                                                + reasonCodeName;
                                                                      }

                                                                  String demographicAlert = dCust != null && dCust.getAlert() != null ? dCust.getAlert() : "";
                                                                  String demographicNotes = dCust != null && dCust.getNotes() != null
                                                                          ? SxmlMisc.getXmlContent(dCust.getNotes(), "<unotes>", "</unotes>")
                                                                          : "";

                                                                  String timeRange = iS + ":" + (iSm >= 10 ? "" : "0") + iSm + "-" + iE + ":" + (iEm >= 10 ? "" : "0") + iEm;
                                                                  StringBuilder appointmentTooltipSummaryBuilder = new StringBuilder();
                                                                  appointmentTooltipSummaryBuilder.append(SafeEncode.forHtmlAttribute(timeRange))
                                                                          .append(" ")
                                                                          .append(SafeEncode.forHtmlAttribute(name))
                                                                          .append(" ")
                                                                          .append(SafeEncode.forHtmlAttribute(reasonCodeName));

                                                                  String appointmentTooltipSummary = appointmentTooltipSummaryBuilder.toString();

                                                                  StringBuilder appointmentTooltipFullBuilder = new StringBuilder();
                                                                  appendTooltipLine(appointmentTooltipFullBuilder, "<i class='fa-solid fa-person' aria-hidden='true'></i>", name);
                                                                  appendTooltipLine(appointmentTooltipFullBuilder, "<i class='fa-regular fa-clock' aria-hidden='true'></i>", timeRange);
                                                                  appendTooltipLine(appointmentTooltipFullBuilder, "<i class='fa-regular fa-user me-2' aria-hidden='true'></i>", type);
                                                                  appendTooltipLine(appointmentTooltipFullBuilder, "<i class='fa-regular fa-circle-question' aria-hidden='true'></i>", reasonCodeName);
                                                                  appendTooltipLine(appointmentTooltipFullBuilder, "<i class='fa-regular fa-note-sticky' aria-hidden='true'></i>", notes);
                                                                  appendTooltipLine(appointmentTooltipFullBuilder, "<i class='fa-solid fa-triangle-exclamation' aria-hidden='true'></i>", tickler_note);
                                                                  appendTooltipLine(appointmentTooltipFullBuilder, "<i class='fa-solid fa-circle-exclamation me-2' aria-hidden='true'></i>", demographicAlert);
                                                                  appendTooltipLine(appointmentTooltipFullBuilder, "<i class='fa-regular fa-comment' aria-hidden='true'></i>", demographicNotes);

                                                                  String preventionWarning = "";
                                                                  if (!providerPreventionManager.isDisabled() && demographic_no != 0) {
                                                                      // prevention warnings is concacted "[" + me.getKey() + "=" + me.getValue() + "]"
                                                                      preventionWarning = providerPreventionManager.getWarnings(loggedInInfo1, String.valueOf(demographic_no));
                                                                      preventionWarning = preventionWarning.replaceAll(
                                                                          "\\[[^=\\]]*=([^\\]]*)\\](?:\\r?\\n)?",
                                                                          "$1<br>"
                                                                      );
                                                                  }
                                                                  if (preventionWarning.length()>1) {
                                                                      appointmentTooltipFullBuilder.append("<i class='fa-regular fa-bell' aria-hidden='true'></i> ")
                                                                      .append(preventionWarning);
                                                                  }
                                                                  String appointmentTooltipFull = appointmentTooltipFullBuilder.toString();
                                                                  appointmentTooltipFull = appointmentTooltipFull.replaceAll("(?:<br>)+$", ""); // remove trailing <br>
                                                                  boolean showTooltip = true; //CarlosProperties.getInstance().getBooleanProperty("SHOW_APPT_REASON_TOOLTIP", "yes");

                                                                  bFirstTimeRs=true;
                                                            as.setApptStatus(status);

                                                         //multi-site. if a site have been selected, only display appointment in that site
                                                   if (!bMultisites || (selectedSite == null && CurrentSiteMap.get(sitename) != null) || sitename.equals(selectedSite)) {
                                                        // Check if this is a cancelled appointment (C, CS, CV, N, NS, NV)
                                                        boolean isCancelled = noCountStatus.contains(status);
                                                    %>
                                                    <td class="appt<%= isCancelled ? " Cancelled" : "" %><%= showTooltip ? " appt-reason-tooltip appt-tooltip-provider-" + curProvider_no[nProvider] : "" %>" bgcolor='<%=as.getBgColor()%>'
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
                                                           onclick="postViaForm('<%= request.getContextPath() %>/provider/providercontrol?appointment_no=<%=appointment.getId()%>&amp;provider_no=<%=curProvider_no[nProvider]%>&amp;status=&amp;statusch=<%=nextStatus%>&amp;year=<%=year%>&amp;month=<%=month%>&amp;day=<%=day%>&amp;<%=viewString%>&amp;displaymode=addstatus&amp;dboperation=updateapptstatus&amp;viewall=${carlos:forUriComponent(not empty param.viewall ? param.viewall : '0')}<%= isWeekView ? "&amp;viewWeek=1" : "" %>');"
                                                           title='<carlos:encode value='<%= as.getTitleString(request.getLocale()) %>' context="htmlAttribute"/>'>
                                                            <%
                                                                }
                                                                if (nextStatus != null) {
                                                                    if (CarlosProperties.getInstance().getProperty("APPT_SHOW_SHORT_LETTERS", "false") != null
                                                                            && CarlosProperties.getInstance().getProperty("APPT_SHOW_SHORT_LETTERS", "false").equals("true")) {

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
                                                                 alt="<carlos:encode value='<%= (as.getTitleString(request.getLocale()).length()>0)?as.getTitleString(request.getLocale()):as.getTitle() %>' context="htmlAttribute"/>">

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
                                                               onClick="popupPage(800,1200, '<%= request.getContextPath() %>/tickler/ViewTicklerMain?demoview=0');return false;"
                                                               title="<fmt:message key="provider.appointmentProviderAdminDay.ticklerMsg"/>: <carlos:encode value='<%= tickler_note %>' context="html"/>">
                                                                <span color="red">!</span></a>
                                                        <%} %>


                                                        <!--  alerts -->
                                                        <% if (CarlosProperties.getInstance().getProperty("displayAlertsOnScheduleScreen", "").equals("true")) { %>
                                                        <% if (dCust != null && dCust.getAlert() != null && !dCust.getAlert().isEmpty()) { %>
                                                        <a href="#" onClick="return false;"

                                                           title="<carlos:encode value='<%= dCust.getAlert() %>' context="htmlAttribute"/>">A</a>

                                                        <%
                                                                }
                                                            }
                                                        %>

                                                        <!--  notes -->
                                                        <% if (CarlosProperties.getInstance().getProperty("displayNotesOnScheduleScreen", "").equals("true")) { %>
                                                        <% if (dCust != null && dCust.getNotes() != null && !SxmlMisc.getXmlContent(dCust.getNotes(), "<unotes>", "</unotes>").isEmpty()) { %>
                                                        <a href="#" onClick="return false;"

                                                           title="<carlos:encode value='<%= SxmlMisc.getXmlContent(dCust.getNotes(), "<unotes>", "</unotes>") %>' context="htmlAttribute"/>">N</a>

                                                        <%
                                                                }
                                                            }
                                                        %>


                                                        <a href="javascript:void(0)"
                                                           class="<%= showTooltip ? "appt-reason-tooltip appt-tooltip-provider-" + curProvider_no[nProvider] : "" %>"
                                                           <c:set var="__enc_8"><carlos:encode value='<%= String.valueOf(appointment.getId()) %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_9"><carlos:encode value='<%= curProvider_no[nProvider] %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_10"><carlos:encode value='<%= String.valueOf(year) %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_11"><carlos:encode value='<%= String.valueOf(month) %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_12"><carlos:encode value='<%= String.valueOf(day) %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_13"><carlos:encode value='<%= iS+":"+iSm %>' context="uriComponent"/></c:set>
                                                           onClick="popupPage(600,860,'<%= request.getContextPath() %>/appointment/editappointment?appointment_no=<carlos:encode value='${__enc_8}' context="javaScriptAttribute"/>&provider_no=<carlos:encode value='${__enc_9}' context="javaScriptAttribute"/>&year=<carlos:encode value='${__enc_10}' context="javaScriptAttribute"/>&month=<carlos:encode value='${__enc_11}' context="javaScriptAttribute"/>&day=<carlos:encode value='${__enc_12}' context="javaScriptAttribute"/>&start_time=<carlos:encode value='${__enc_13}' context="javaScriptAttribute"/>&demographic_no=0&dboperation=search');return false;"
                                                            <%= showTooltip
                                                            ? "data-bs-toggle=\"tooltip\""
                                                              + " data-title-full=\"" + appointmentTooltipFull + "\""
                                                              + " data-title-short=\"" + appointmentTooltipSummary + "\""
                                                              + " title=\"" + appointmentTooltipFull + "\""
                                                            : "" %>>
                                                            <span>
                                                            .<%=(view == 0 && numAvailProvider != 1) ? (name.length() > len ? SafeEncode.forHtmlContent(name.substring(0, len).toUpperCase()) : SafeEncode.forHtmlContent(name.toUpperCase())) : SafeEncode.forHtmlContent(name.toUpperCase())%>
                                                            </span>
                                                        </a><!--Inline display of reason -->

                                                        <span class="reason reason_<%=curProvider_no[nProvider]%> hideReason">
                                                            <carlos:encode value='<%= reasonCodeName %>' context="html"/>
                                                        </span>


                                                        <%
                                                        } else {
                                                        %> <% if (tickler_no.compareTo("") != 0) {%>
                                                            <a href="#"
                                                               onClick="popupPage(800,1200, '<%= request.getContextPath() %>/tickler/ViewTicklerMain?demoview=<%=demographic_no%>');return false;"
                                                               title="<fmt:message key="provider.appointmentProviderAdminDay.ticklerMsg"/>: <%=UtilMisc.htmlEscape(tickler_note)%>"><span
                                                                    color="red">!</span></a>
                                                        <%} %>

                                                        <!--  alerts -->
                                                        <% if (CarlosProperties.getInstance().getProperty("displayAlertsOnScheduleScreen", "").equals("true")) {%>
                                                        <% if (dCust != null && dCust.getAlert() != null && !dCust.getAlert().isEmpty()) { %>
                                                        <a href="#" onClick="return false;"

                                                           title="<carlos:encode value='<%= dCust.getAlert() %>' context="htmlAttribute"/>">A</a>
                                                        <%
                                                                }
                                                            }
                                                        %>

                                                        <!--  notes -->
                                                        <% if (CarlosProperties.getInstance().getProperty("displayNotesOnScheduleScreen", "").equals("true")) {%>
                                                        <% if (dCust != null && dCust.getNotes() != null && !SxmlMisc.getXmlContent(dCust.getNotes(), "<unotes>", "</unotes>").isEmpty()) { %>
                                                        <a href="#" onClick="return false;"

                                                           title="<carlos:encode value='<%= SxmlMisc.getXmlContent(dCust.getNotes(), "<unotes>", "</unotes>") %>' context="htmlAttribute"/>">N</a>

                                                        <%
                                                                }
                                                            }
                                                        %>

                                                        <!-- doctor code block 1 -->
                                                        <c:if test="${doctorLinkRights}">

                                                            <% if (ver != null && ver != "" && "##".compareTo(ver.toString()) == 0) {%><a
                                                                href="#"
                                                                title="<fmt:message key="provider.appointmentProviderAdminDay.versionMsg"/> <%=UtilMisc.htmlEscape(ver)%>">
                                                            <span style="color:red;">*</span></a><%}%>

                                                            <% if (roster != "" && "FS".equalsIgnoreCase(roster)) {%>
                                                            <a href="#"
                                                               title="<fmt:message key="provider.appointmentProviderAdminDay.rosterMsg"/> <%=UtilMisc.htmlEscape(roster)%>"><span
                                                                    style="color:red;">$</span></a><%}%>

                                                            <% if ("NR".equalsIgnoreCase(roster) || "PL".equalsIgnoreCase(roster)) {%>
                                                            <a href="#"
                                                               title="<fmt:message key="provider.appointmentProviderAdminDay.rosterMsg"/> <%=UtilMisc.htmlEscape(roster)%>"><span
                                                                    style="color:red;">#</span></a><%}%>

                                                        </c:if>
                                                        <!-- doctor code block 2 -->
                                                        <c:if test="${not isPreventionWarningDisabled}">
                                                            <%
                                                                if (!preventionWarning.isEmpty()) {
                                                            %>
                                                            <img src="${pageContext.servletContext.contextPath}/images/stop_sign.png"
                                                                 width="14px" height="14px"
                                                                 style="margin-bottom: 3px;margin-left: 3px;"
                                                                 title="<carlos:encode value='<%= preventionWarning %>' context="htmlAttribute"/>"/>&nbsp;
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

                                                        <a class="apptLink<%= showTooltip ? " appt-reason-tooltip appt-tooltip-provider-" + curProvider_no[nProvider] : "" %>" href="javascript:void(0)"
                                                           <c:set var="__enc_14"><carlos:encode value='<%= String.valueOf(appointment.getId()) %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_15"><carlos:encode value='<%= curProvider_no[nProvider] %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_16"><carlos:encode value='<%= String.valueOf(year) %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_17"><carlos:encode value='<%= String.valueOf(month) %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_18"><carlos:encode value='<%= String.valueOf(day) %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_19"><carlos:encode value='<%= iS+":"+iSm %>' context="uriComponent"/></c:set>
                                                           <c:set var="__enc_20"><carlos:encode value='<%= String.valueOf(demographic_no) %>' context="uriComponent"/></c:set>
                                                           onClick="popupPage(600,860,'<%= request.getContextPath() %>/appointment/editappointment?appointment_no=<carlos:encode value='${__enc_14}' context="javaScriptAttribute"/>&provider_no=<carlos:encode value='${__enc_15}' context="javaScriptAttribute"/>&year=<carlos:encode value='${__enc_16}' context="javaScriptAttribute"/>&month=<carlos:encode value="${__enc_17}" context="javaScriptAttribute"/>&day=<carlos:encode value='${__enc_18}' context="javaScriptAttribute"/>&start_time=<carlos:encode value='${__enc_19}' context="javaScriptAttribute"/>&demographic_no=<carlos:encode value='${__enc_20}' context="javaScriptAttribute"/>&dboperation=search');return false;"
                                                            <%= showTooltip
															? "data-bs-toggle=\"tooltip\""
															  + " data-title-full=\"" + SafeEncode.forHtmlAttribute(appointmentTooltipFull) + "\""
															  + " data-title-short=\"" + SafeEncode.forHtmlAttribute(appointmentTooltipSummary) + "\""
															  + " title=\"" + SafeEncode.forHtmlAttribute(appointmentTooltipFull) + "\""
															: "" %> >
                                                            <%=(name.length() > len ? SafeEncode.forHtmlContent(name.substring(0, len)) : SafeEncode.forHtmlContent(name))%>
                                                        </a>
                                                        <% if (len == lenLimitedL || view != 0 || numAvailProvider == 1) {%>


                                                        <oscar:oscarPropertiesCheck
                                                                property="eform_in_appointment" value="yes">
                                                            &#124; <b><a href="#"
                                                                         onclick="popupPage(500,1024,'<%= request.getContextPath() %>/eform/efmformslistadd?parentAjaxId=eforms&demographic_no=<%=demographic_no%>&appointment=<%=appointment.getId()%>'); return false;"
                                                                         title="<fmt:message key="eform.myform.title"/>">F</a></b>
                                                        </oscar:oscarPropertiesCheck>

                                                        <!-- doctor code block 3 -->
                                                        <% if (!isWeekView) { %>

                                                        <% String eURL = request.getContextPath() + "/encounter/IncomingEncounter?providerNo="
                                                                + loggedInInfo1.getLoggedInProviderNo() + "&appointmentNo="
                                                                + appointment.getId()
                                                                + "&demographicNo="
                                                                + demographic_no
                                                                + "&curProviderNo="
                                                                + curProvider_no[nProvider]
                                                                + "&reason="
                                                                + URLEncoder.encode(reason != null ? reason : "", StandardCharsets.UTF_8)
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
                                                                  onClick="popupWithApptNo(800,1200,'<%=eURL%>','encounter',<%=appointment.getId()%>);return false;"
                                                                  title="<fmt:message key="global.encounter"/>">
                                                        <fmt:message key="provider.appointmentProviderAdminDay.btnE"/></a>
                                                        <% }
                                                        } // end if not is week view %>

                                                        <!-- billing code block -->
                                                        <% if (!isWeekView) { %>
                                                        <c:if test="${billingRights}">
                                                            <%
                                                                if (status.indexOf('B') == -1) {
                                                            %>
                                                            &#124; <a href=#
                                                                      onClick='popupPage(800,1200, "<%=request.getContextPath()%>/billing?billRegion=<%=URLEncoder.encode(prov, StandardCharsets.UTF_8)%>&billForm=<%=URLEncoder.encode(oscarVariables.getProperty("default_view"), StandardCharsets.UTF_8)%>&hotclick=<%=URLEncoder.encode("", StandardCharsets.UTF_8)%>&appointment_no=<%=appointment.getId()%>&demographic_name=<%=URLEncoder.encode(name, StandardCharsets.UTF_8)%>&status=<%=status%>&demographic_no=<%=demographic_no%>&providerview=<%=curProvider_no[nProvider]%>&user_no=<%=loggedInInfo1.getLoggedInProviderNo()%>&apptProvider_no=<%=curProvider_no[nProvider]%>&xml_provider=<%=curProvider_no[nProvider]%>&appointment_date=<%=year+"-"+month+"-"+day%>&start_time=<%=start_time%>&bNewForm=1");return false;'
                                                                      title="<fmt:message key="global.billingtag"/>"><fmt:message key="provider.appointmentProviderAdminDay.btnB"/></a>
                                                            <%
                                                            } else {
                                                                if (caisiBillingPreferenceNotDelete != null && caisiBillingPreferenceNotDelete.equals("1")) {
                                                            %>
                                                            &#124; <a href=#
                                                                      onClick='onUpdatebill("<%= request.getContextPath() %>/billing/CA/ON/BillingEditWithApptNo?billRegion=<%=URLEncoder.encode(prov, StandardCharsets.UTF_8)%>&billForm=<%=URLEncoder.encode(oscarVariables.getProperty("default_view"), StandardCharsets.UTF_8)%>&hotclick=<%=URLEncoder.encode("", StandardCharsets.UTF_8)%>&appointment_no=<%=appointment.getId()%>&demographic_name=<%=URLEncoder.encode(name, StandardCharsets.UTF_8)%>&status=<%=status%>&demographic_no=<%=demographic_no%>&providerview=<%=curProvider_no[nProvider]%>&user_no=<%=loggedInInfo1.getLoggedInProviderNo()%>&apptProvider_no=<%=curProvider_no[nProvider]%>&appointment_date=<%=year+"-"+month+"-"+day%>&start_time=<%=iS+":"+iSm%>&bNewForm=1");return false;'
                                                                      title="<fmt:message key="global.billingtag"/>">=<fmt:message key="provider.appointmentProviderAdminDay.btnB"/></a>
                                                            <%
                                                            } else {
                                                            %>
                                                            &#124; <a href=#
                                                                      onClick='onUnbilled("<%= request.getContextPath() %>/billing/CA/<%=prov%>/BillingDeleteWithoutNo?status=<%=status%>&appointment_no=<%=appointment.getId()%>");return false;'
                                                                      title="<fmt:message key="global.billingtag"/>">-<fmt:message key="provider.appointmentProviderAdminDay.btnB"/></a>
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
                                                                      onClick="popupWithApptNo(800,1200, '<%= request.getContextPath() %>/demographic/DemographicEdit?demographic_no=<%=demographic_no%>&apptProvider=<%=curProvider_no[nProvider]%>&appointment=<%=appointment.getId()%>','master',<%=appointment.getId()%>)"
                                                                      title="<fmt:message key="provider.appointmentProviderAdminDay.msgMasterFile"/>"><fmt:message key="provider.appointmentProviderAdminDay.btnM"/></a>

                                                        </c:if>
                                                        <% if (!isWeekView) { %>

                                                        <!-- doctor code block 4 -->

                                                        <c:if test="${doctorLinkRights}">
                                                            &#124; <a href=#
                                                                      onClick="popupWithApptNo(800,1200,'<%=request.getContextPath()%>/rx/choosePatient?providerNo=<%=loggedInInfo1.getLoggedInProviderNo()%>&demographicNo=<%=demographic_no%>','rx',<%=appointment.getId()%>)"
                                                                      title="<fmt:message key="global.prescriptions"/>"><fmt:message key="global.rx"/>
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
                                                            <carlos:encode value='<%= reasonCodeName %>' context="html"/>
                                                        </i></strong>
                                                    </span>
                                                        </c:if>

                                                        <!-- add one link to caisi Program Management Module -->
                                                        <caisi:isModuleLoad moduleName="caisi">
                                                            <a href=${pageContext.servletContext.contextPath}'/PMmodule/ClientManager?id=<%=demographic_no%>'
                                                               title="<fmt:message key="provider.appointmentProviderAdminDay.programManagement"/>">|P</a>
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
                                                            |<a href="javascript:void(0)" onClick='popupPage2("${pageContext.servletContext.contextPath}/form/forwardshortcutname?formname=${carlos:forUriComponent(form)}&amp;formId=0&provNo=${appointment.providerNo}&parentAjaxId=forms&amp;demographic_no=${appointment.demographicNo}&amp;appointmentNo=${appointment.id}")'
                                                            title='${carlos:forHtmlAttribute(form)}'>
                                                            ${carlos:forHtml(fn:substring(form, 0, truncateLimit))}
                                                            </a>
                                                        </c:forEach>
                                                        <c:forEach items="${eFormsList}" var="eform">
                                                            |<a href="javascript:void(0)" onClick='popupPage2("${pageContext.servletContext.contextPath}/eform/efmformadd_data?fid=${eform.appointmentScreenEForm}&amp;demographic_no=${appointment.demographicNo}&amp;appointment=${appointment.id}")' title='${carlos:forHtmlAttribute(eform.eFormName)}'>
                                                            ${carlos:forHtml(fn:substring(eform.eFormName, 0, truncateLimit))}
                                                            </a>
                                                        </c:forEach>
                                                        <c:if test="${not empty quickLinksList}">
                                                            <jsp:include page="appointmentFormsLinks.jspf">
                                                                <jsp:param name="demographic_no" value="${appointment.demographicNo}"/>
                                                                <jsp:param name="appointment_no" value="${appointment.id}"/>
                                                                <jsp:param name="skipFormsAndEforms" value="true"/>
                                                            </jsp:include>
                                                        </c:if>

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
                </table>        <!-- end table for the whole schedule row display -->


            </td>
        </tr>

    </table>

    <!-- key shortcut hotkey block added by phc -->
    <fmt:message var="labTitle" key="global.lab"/>
    <c:set var="__enc_21"><carlos:encode value='<%= mygroupno %>' context="uriComponent"/></c:set>
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
                    case <fmt:message key="global.adminShortcut"/> :
                        newWindow("<%= request.getContextPath() %>/administration", "admin");
                        return false;  //run code for 'A'dmin
                    case <fmt:message key="global.calendarShortcut"/> :
                        popupOscarRx(425, 430, '<%= request.getContextPath() %>/share/CalendarPopup?urlfrom=<%= request.getContextPath() %>/provider/providercontrol&year=<%=strYear%>&month=<%=strMonth%>&param=<%=URLEncoder.encode("&view=0&displaymode=day&dboperation=searchappointmentday","UTF-8")%>');
                        return false;  //run code for 'C'alendar
                    case <fmt:message key="global.edocShortcut"/> :
                        popupOscarRx('800', '1200', '<%= request.getContextPath() %>/documentManager/ViewDocumentReport?function=providers&functionid=<%=SafeEncode.forUriComponent(loggedInInfo1.getLoggedInProviderNo())%>', 'edocView');
                        return false;  //run code for e'D'oc
                    case <fmt:message key="global.resourcesShortcut"/> :
                        popupOscarRx(550, 687, '<%=resourcebaseurl%>');
                        return false; // code for R'e'sources
                    case <fmt:message key="global.helpShortcut"/> :
                        popupOscarRx(600, 750, '<%=resourcebaseurl%>');
                        return false;  //run code for 'H'elp
                    case <fmt:message key="global.ticklerShortcut"/> : {
                        popupOscarRx(800, 1200, '<%= request.getContextPath() %>/tickler/ViewTicklerMain', '${carlos:forJavaScript(ticklerTitle)}'); //run code for t'I'ckler
                        return false;
                    }
                    case <fmt:message key="global.labShortcut"/> :
                        popupOscarRx(800, 1200, '<%=request.getContextPath()%>/web/inboxhub/Inboxhub?method=displayInboxForm', '${carlos:forJavaScript(labTitle)}');
                        return false;  //run code for 'L'ab
                    case <fmt:message key="global.msgShortcut"/> :
                        popupOscarRx(800, 1200, '<%=request.getContextPath()%>/messenger/DisplayMessages?providerNo=<%=loggedInInfo1.getLoggedInProviderNo()%>&userName=<%=URLEncoder.encode(userfirstname+" "+userlastname, StandardCharsets.UTF_8)%>');
                        return false;  //run code for 'M'essage
                    case <fmt:message key="global.monthShortcut"/> :
                        window.open("<%= request.getContextPath() %>/provider/providercontrol?year=<%=year%>&month=<%=month%>&day=1<%=viewString%>&displaymode=month&dboperation=searchappointmentmonth", "_self");
                        return false;  //run code for Mo'n'th
                    case <fmt:message key="global.conShortcut"/> :
                        popupOscarRx(800, 1200, '<%=request.getContextPath()%>/encounter/IncomingConsultation?providerNo=<%=loggedInInfo1.getLoggedInProviderNo()%>&userName=<%=URLEncoder.encode(userfirstname+" "+userlastname, StandardCharsets.UTF_8)%>');
                        return false;  //run code for c'O'nsultation
                    case <fmt:message key="global.reportShortcut"/> :
                        popupOscarRx(800, 1200, '<%= request.getContextPath() %>/report/ViewReportindex', 'reportPage');
                        return false;  //run code for 'R'eports
                    case <fmt:message key="global.prefShortcut"/> : {
                        popupOscarRx(715, 680, '<%= request.getContextPath() %>/provider/ViewProviderPreference?provider_no=<%=loggedInInfo1.getLoggedInProviderNo()%>&start_hour=<%=startHour%>&end_hour=<%=endHour%>&every_min=<%=everyMin%>&mygroup_no=<carlos:encode value='${__enc_21}' context="javaScript"/>'); //run code for 'P'references
                        return false;
                    }
                    case <fmt:message key="global.searchShortcut"/> :
                        popupOscarRx(550, 687, '<%= request.getContextPath() %>/demographic/ViewSearch');
                        return false;  //run code for 'S'earch
                    case <fmt:message key="global.dayShortcut"/> :
                        window.open("<%= request.getContextPath() %>/provider/providercontrol?year=<%=curYear%>&month=<%=curMonth%>&day=<%=curDay%><%=viewString%>&displaymode=day&dboperation=searchappointmentday", "_self");
                        return false;  //run code for 'T'oday
                    case <fmt:message key="global.viewShortcut"/> : {
                        <% if(request.getParameter("viewall")!=null && request.getParameter("viewall").equals("1") ) { %>
                        review('0');
                        return false; //scheduled providers 'V'iew
                        <% } else {  %>
                        review('1');
                        return false; //all providers 'V'iew
                        <% } %>
                    }
                    case <fmt:message key="global.workflowShortcut"/> :
                        popupOscarRx(800, 1200, '<%= request.getContextPath() %>/oscarWorkflow/WorkFlowList', '${carlos:forJavaScript(workflowTitle)}');
                        return false; //code for 'W'orkflow
                    default :
                        return;
                }
            }
            if (evt.ctrlKey) {
                switch (evt.keyCode || evt.charCode) {
                    case <fmt:message key="global.btnLogoutShortcut"/> :
                        window.open('<%= request.getContextPath() %>/logoutPage', '_self');
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
                var y = '<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("year")) %>' context="javaScriptBlock"/>';
                var m = '<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("month")) %>' context="javaScriptBlock"/>';
                var d = '<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("day")) %>' context="javaScriptBlock"/>';
                var sTime = 8;
                var eTime = 20;
                var dateStr = y + '-' + m + '-' + d;
                var url = '<%=request.getContextPath()%>/report/ViewReportdaysheet?dsmode=all&provider_no=' + provider_no
                    + '&sdate=' + dateStr + '&edate=' + dateStr + '&sTime=' + sTime + '&eTime=' + eTime;
                popupPage(600, 750, url);
                return false;
            });
        });
    </script>
    <!-- end of keycode block -->

    <!-- Quick Search Typeahead -->
    <script>

    /**
     * Builds the addappointment URL pre-filled with slot coordinates.
     * Uses bFirstDisp=true and year/month/day parameters to match the standard
     * schedule slot link format used throughout the schedule view. The target
     * is the ViewAppointmentWrite2Action gate — do not reintroduce a direct
     * addappointment.jsp reference (the JSP lives under /WEB-INF/jsp/).
     * Patient name is resolved server-side via demographic_no (no name PHI
     * transmitted through the URL).
     */
    function buildApptUrl(ctx, demographicNo, providerNo, startTime, endTime, duration, year, month, day) {
        var mm = String(month).padStart(2, '0');
        var dd = String(day).padStart(2, '0');
        return ctx + '/appointment/addappointment'
            + '?demographic_no='   + encodeURIComponent(demographicNo)
            + '&provider_no='      + encodeURIComponent(providerNo)
            + '&bFirstDisp=true'
            + '&year='             + encodeURIComponent(String(year))
            + '&month='            + encodeURIComponent(mm)
            + '&day='              + encodeURIComponent(dd)
            + '&start_time='       + encodeURIComponent(startTime)
            + '&end_time='         + encodeURIComponent(endTime)
            + '&duration='         + encodeURIComponent(String(duration));
    }

    (function() {
        'use strict';

        var SEARCH_MIN_LENGTH = 2;
        var DEBOUNCE_DELAY = 300;

        var input = document.getElementById('quickSearch');
        var clearBtn = document.getElementById('quickSearchClear');
        var dropdown = document.getElementById('quickSearchDropdown');
        var wrapper = document.getElementById('quickSearchWrapper');

        if (!input || !dropdown || !wrapper) return;

        var abortController = null;
        var debounceTimer = null;
        var activeIndex = -1;

        var ctx = document.getElementById('contextPath').value;
        var scheduleProviderNo = document.getElementById('scheduleLoggedInProviderNo').value;
        var scheduleCurrentDate = document.getElementById('scheduleCurrentDate').value;
        // var scheduleVisibleProviderNos = document.getElementById('scheduleVisibleProviderNos').value; // Reserved for future multi-provider slot search
        var scheduleViewAll = document.getElementById('scheduleViewAll').value;
        var scheduleView = document.getElementById('scheduleView').value;

        <%-- OWASP-encode each i18n message for JavaScript string context --%>
        <fmt:message var="qsNoPatientsFound"    key="provider.appointmentProviderAdminDay.quickSearch.noPatientsFound"/>
        <fmt:message var="qsAddNewPatient"      key="provider.appointmentProviderAdminDay.quickSearch.addNewPatient"/>
        <fmt:message var="qsBadgeMasterFile"    key="provider.appointmentProviderAdminDay.quickSearch.badgeMasterFile"/>
        <fmt:message var="qsBadgeEChart"        key="provider.appointmentProviderAdminDay.quickSearch.badgeEChart"/>
        <fmt:message var="qsBadgePrescriptions" key="provider.appointmentProviderAdminDay.quickSearch.badgePrescriptions"/>
        <fmt:message var="qsBadgeApptTitle"     key="provider.appointmentProviderAdminDay.quickSearch.badgeApptTitle"/>
        <fmt:message var="qsLabelDob"           key="provider.appointmentProviderAdminDay.quickSearch.labelDob"/>
        <fmt:message var="qsLabelHin"           key="provider.appointmentProviderAdminDay.quickSearch.labelHin"/>
        <fmt:message var="qsNoMrpAlert"         key="provider.appointmentProviderAdminDay.quickSearch.noMrpAlert"/>
        <fmt:message var="qsNoSlotsAlert"       key="provider.appointmentProviderAdminDay.quickSearch.noSlotsAlert"/>
        <fmt:message var="qsSlotErrorAlert"     key="provider.appointmentProviderAdminDay.quickSearch.slotErrorAlert"/>
        /* Translated UI strings — OWASP-encoded for JS string context to prevent injection */
        var msgs = {
            noPatientsFound:    '${carlos:forJavaScript(qsNoPatientsFound)}',
            addNewPatient:      '${carlos:forJavaScript(qsAddNewPatient)}',
            badgeMasterFile:    '${carlos:forJavaScript(qsBadgeMasterFile)}',
            badgeEChart:        '${carlos:forJavaScript(qsBadgeEChart)}',
            badgePrescriptions: '${carlos:forJavaScript(qsBadgePrescriptions)}',
            badgeApptTitle:     '${carlos:forJavaScript(qsBadgeApptTitle)}',
            labelDob:           '${carlos:forJavaScript(qsLabelDob)}',
            labelHin:           '${carlos:forJavaScript(qsLabelHin)}',
            noMrpAlert:         '${carlos:forJavaScript(qsNoMrpAlert)}',
            noSlotsAlert:       '${carlos:forJavaScript(qsNoSlotsAlert)}',
            slotErrorAlert:     '${carlos:forJavaScript(qsSlotErrorAlert)}'
        };

        /**
         * Detect search type from the query string.
         * - comma present → name (Last, First)
         * - 8 pure digits → dob (YYYYMMDD format)
         * - pure digits or digits + separators → phone
         * - letters followed by digits → HIN
         * - digit + space + letters → address
         * - default → name
         */
        function detectSearchType(query) {
            if (query.indexOf(',') !== -1) return 'name';
            if (/^\d{8}$/.test(query)) return 'dob';
            if (/^[\d\s\-().+]+$/.test(query)) return 'phone';
            if (/^[A-Za-z]{2,4}\d{4,}/i.test(query)) return 'hin';
            if (/^\d+\s+[A-Za-z]/i.test(query)) return 'address';
            return 'name';
        }

        function doSearch(query) {
            if (query.length < SEARCH_MIN_LENGTH) {
                if (abortController) {
                    try { abortController.abort(); } catch (e) { /* ignore */ }
                    abortController = null;
                }
                hideDropdown();
                return;
            }
            var searchType = detectSearchType(query);
            var url = ctx + '/demographic/SearchDemographic';
            var csrfToken = (document.querySelector('input[name="CSRF-TOKEN"]') || {value: ''}).value;
            var body = 'jqueryJSON=true&term=' + encodeURIComponent(query) + '&searchType=' + encodeURIComponent(searchType);

            if (abortController) {
                try { abortController.abort(); } catch (e) { /* ignore */ }
            }
            abortController = typeof AbortController !== 'undefined' ? new AbortController() : null;

            var fetchOpts = {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'XMLHttpRequest',
                    'CSRF-TOKEN': csrfToken
                },
                body: body,
                credentials: 'same-origin'
            };
            if (abortController) fetchOpts.signal = abortController.signal;

            fetch(url, fetchOpts)
                .then(function(r) {
                    if (!r.ok) throw new Error('HTTP ' + r.status);
                    return r.json();
                })
                .then(function(data) { renderResults(data); })
                .catch(function(err) {
                    if (!err || err.name !== 'AbortError') {
                        console.error('Quick search error:', err);
                    }
                });
        }

        function renderResults(data) {
            while (dropdown.firstChild) { dropdown.removeChild(dropdown.firstChild); }
            activeIndex = -1;

            if (!Array.isArray(data) || data.length === 0) {
                var noResult = document.createElement('div');
                noResult.className = 'qs-no-results';
                noResult.textContent = msgs.noPatientsFound;
                dropdown.appendChild(noResult);
            } else {
                data.forEach(function(item, idx) {
                    dropdown.appendChild(buildResultRow(item, idx));
                });
            }

            // "Add New Patient" link at bottom
            var addDiv = document.createElement('div');
            addDiv.className = 'qs-add-link';
            var addA = document.createElement('a');
            addA.href = 'javascript:void(0)';
            addA.textContent = msgs.addNewPatient;
            addA.addEventListener('click', function(e) {
                e.stopPropagation();
                popupPage2(ctx + '/demographic/ViewDemographicAddARecordHtm');
                hideDropdown();
            });
            addDiv.appendChild(addA);
            dropdown.appendChild(addDiv);

            showDropdown();
        }

        function buildResultRow(item, idx) {
            var row = document.createElement('div');
            row.className = 'qs-result-row' + (idx % 2 === 1 ? ' qs-alt' : '');
            row.setAttribute('role', 'option');
            row.setAttribute('aria-selected', 'false');

            // Clicking anywhere on row (except badges) opens the E-Chart
            row.addEventListener('click', function(e) {
                if (e.target.closest && e.target.closest('.qs-badge')) return;
                if (item.demographicNo) {
                    popupPage(800, 1200, ctx + '/encounter/IncomingEncounter'
                        + '?demographicNo='  + encodeURIComponent(item.demographicNo)
                        + '&providerNo='     + encodeURIComponent(scheduleProviderNo)
                        + '&curProviderNo='  + encodeURIComponent(scheduleProviderNo)
                        + '&curDate='        + encodeURIComponent(scheduleCurrentDate)
                        + '&encType=&status=');
                    hideDropdown();
                }
            });

            // --- Line 1: bold name + M/E/Rx badges ---
            var line1 = document.createElement('div');
            line1.className = 'qs-line1';

            var nameSpan = document.createElement('strong');
            nameSpan.className = 'qs-patient-name';
            nameSpan.textContent = item.formattedName || '';
            if (item.status) {
                var statusBadge = document.createElement('span');
                statusBadge.className = 'qs-status-label';
                statusBadge.textContent = ' (' + item.status + ')';
                nameSpan.appendChild(statusBadge);
            }
            line1.appendChild(nameSpan);

            var badgesDiv = document.createElement('div');
            badgesDiv.className = 'qs-badges';
            badgesDiv.appendChild(makeBadge('M', 'qs-badge-m', msgs.badgeMasterFile, function(e) {
                e.stopPropagation();
                popupPage(800, 1200, ctx + '/demographic/DemographicEdit?demographic_no='
                    + encodeURIComponent(item.demographicNo));
                hideDropdown();
            }));
            badgesDiv.appendChild(makeBadge('E', 'qs-badge-e', msgs.badgeEChart, function(e) {
                e.stopPropagation();
                popupPage(800, 1200, ctx + '/encounter/IncomingEncounter'
                    + '?demographicNo=' + encodeURIComponent(item.demographicNo)
                    + '&providerNo=' + encodeURIComponent(scheduleProviderNo)
                    + '&curProviderNo=' + encodeURIComponent(scheduleProviderNo)
                    + '&curDate=' + encodeURIComponent(scheduleCurrentDate)
                    + '&encType=&status=');
                hideDropdown();
            }));
            badgesDiv.appendChild(makeBadge('Rx', 'qs-badge-rx', msgs.badgePrescriptions, function(e) {
                e.stopPropagation();
                popupPage(800, 1200, ctx + '/rx/choosePatient'
                    + '?providerNo=' + encodeURIComponent(scheduleProviderNo)
                    + '&demographicNo=' + encodeURIComponent(item.demographicNo));
                hideDropdown();
            }));
            line1.appendChild(badgesDiv);
            row.appendChild(line1);

            // --- Line 2: DOB + HIN + Appt badge ---
            var line2 = document.createElement('div');
            line2.className = 'qs-line2 qs-muted';

            var dobHin = document.createElement('span');
            var dobStrong = document.createElement('strong');
            dobStrong.textContent = msgs.labelDob + ' ';
            dobHin.appendChild(dobStrong);
            dobHin.appendChild(document.createTextNode((item.formattedDob || '\u2014') + '\u00a0\u00a0'));
            var hinStrong = document.createElement('strong');
            hinStrong.textContent = msgs.labelHin + ' ';
            dobHin.appendChild(hinStrong);
            dobHin.appendChild(document.createTextNode(item.hin || '\u2014'));
            line2.appendChild(dobHin);

            // Only show Appt badge for active patients with a Most Responsible Provider (MRP)
            var isPatientActive = !item.status || item.status === '' || item.status === 'AC';
            var hasMrp = item.providerNo && item.providerNo.trim();
            if (isPatientActive && hasMrp) {
                var apptBadge = makeBadge('Appt', 'qs-badge-appt', msgs.badgeApptTitle, function(e) {
                    e.stopPropagation();
                    openNextAvailableAppt(item, apptBadge);
                });
                line2.appendChild(apptBadge);
            }
            row.appendChild(line2);

            // --- Line 3: cell/phone + email ---
            var line3 = document.createElement('div');
            line3.className = 'qs-line3 qs-muted';

            var hasCell = item.cellPhone && item.cellPhone.trim();
            var hasPhone = item.phone && item.phone.trim();
            var hasEmail = item.email && item.email.trim();

            if (hasCell) {
                appendIconAndText(line3, 'fa-solid fa-mobile-screen-button', item.cellPhone.trim());
            } else if (hasPhone) {
                appendIconAndText(line3, 'fa-solid fa-phone', item.phone.trim());
            }
            if (hasEmail) {
                if (hasCell || hasPhone) { line3.appendChild(document.createTextNode('\u00a0\u00a0')); }
                appendIconAndText(line3, 'fa-solid fa-envelope', item.email.trim());
            }
            if (!hasCell && !hasPhone && !hasEmail) {
                line3.appendChild(document.createTextNode('\u2014'));
            }
            row.appendChild(line3);

            // --- Line 4: doctor name ---
            var doctorName = item.providerName || item.provider || '';
            if (doctorName) {
                var line4 = document.createElement('div');
                line4.className = 'qs-line4 qs-muted';
                appendIconAndText(line4, 'fa-solid fa-user-doctor', doctorName);
                row.appendChild(line4);
            }

            return row;
        }

        /**
         * Fetches the next available appointment slot for the patient's MRP (Most Responsible
         * Provider), stores the slot info in sessionStorage, then navigates the schedule to that
         * day. On page load the pending slot handler opens the add-appointment popup pre-filled
         * with the patient, provider, date, time, and duration.
         *
         * Only called when the patient is active and has an MRP (badge is hidden otherwise).
         *
         * @param {Object}      item    - result row data (demographicNo, formattedName, providerNo, …)
         * @param {HTMLElement} badgeEl - the Appt badge element (used to show loading state)
         */
        function openNextAvailableAppt(item, badgeEl) {
            var mrpProviderNo = item.providerNo;
            if (!mrpProviderNo || !mrpProviderNo.trim()) {
                alert(msgs.noMrpAlert);
                return;
            }

            // Show loading state on badge
            var origText = badgeEl.textContent;
            badgeEl.textContent = '...';
            badgeEl.style.opacity = '0.6';
            badgeEl.style.pointerEvents = 'none';

            // No startDate: backend defaults to tomorrow, so today's (possibly past) open slots
            // are not counted toward the 3-slot target.
            var url = ctx + '/demographic/FindNextAvailableSlot'
                + '?providerNos=' + encodeURIComponent(mrpProviderNo);

            fetch(url, { credentials: 'same-origin' })
                .then(function(r) {
                    if (!r.ok) throw new Error('HTTP ' + r.status);
                    return r.json();
                })
                .then(function(slot) {
                    if (!slot.found) {
                        badgeEl.textContent = origText;
                        badgeEl.style.opacity = '';
                        badgeEl.style.pointerEvents = '';
                        alert(msgs.noSlotsAlert.replace('{0}', slot.lookaheadDays || 90));
                        return;
                    }

                    // Compute end_time: start + duration - 1 minute (matches schedule slot display)
                    var startParts = slot.startTime.split(':');
                    var startMins  = parseInt(startParts[0], 10) * 60 + parseInt(startParts[1], 10);
                    var endMins    = startMins + slot.duration - 1;
                    var endHH      = Math.floor(endMins / 60) % 24;
                    var endMM      = endMins % 60;
                    var endTime    = (endHH < 10 ? '0' : '') + endHH + ':' + (endMM < 10 ? '0' : '') + endMM;

                    // Store pending appointment in sessionStorage so the page-load handler can open the popup.
                    // Only scheduling coordinates are stored (no PHI); patient name is resolved server-side
                    // by addappointment.jsp via demographic_no.
                    try {
                        sessionStorage.setItem('carlosPendingAppt', JSON.stringify({
                            demographicNo: item.demographicNo,
                            providerNo:    slot.providerNo,
                            startTime:     slot.startTime,
                            endTime:       endTime,
                            duration:      slot.duration,
                            year:          slot.year,
                            month:         slot.month,
                            day:           slot.day
                        }));
                    } catch (storageErr) {
                        // sessionStorage unavailable — open popup directly (no schedule navigation)
                        popupPage(600, 860, buildApptUrl(ctx, item.demographicNo,
                            slot.providerNo, slot.startTime, endTime, slot.duration, slot.year, slot.month, slot.day));
                        hideDropdown();
                        return;
                    }

                    // Navigate the schedule to the next available day (include provider_no to preserve schedule context)
                    var navUrl = '<%= request.getContextPath() %>/provider/providercontrol'
                        + '?year='          + encodeURIComponent(String(slot.year))
                        + '&month='         + encodeURIComponent(String(slot.month))
                        + '&day='           + encodeURIComponent(String(slot.day))
                        + '&view='          + encodeURIComponent(scheduleView)
                        + '&displaymode=day'
                        + '&dboperation=searchappointmentday'
                        + '&viewall='       + encodeURIComponent(scheduleViewAll)
                        + '&provider_no='   + encodeURIComponent(slot.providerNo);
                    window.location.href = navUrl;
                })
                .catch(function(err) {
                    badgeEl.textContent = origText;
                    badgeEl.style.opacity = '';
                    badgeEl.style.pointerEvents = '';
                    console.error('FindNextAvailableSlot error:', err);
                    alert(msgs.slotErrorAlert);
                });

            hideDropdown();
        }

        function makeBadge(label, cssClass, title, clickHandler) {
            var a = document.createElement('a');
            a.href = 'javascript:void(0)';
            a.className = 'qs-badge ' + cssClass;
            a.title = title;
            a.textContent = label;
            a.addEventListener('click', clickHandler);
            return a;
        }

        function appendIconAndText(parent, iconClass, text) {
            var icon = document.createElement('i');
            icon.className = iconClass;
            parent.appendChild(icon);
            parent.appendChild(document.createTextNode('\u00a0' + text));
        }

        function showDropdown() {
            dropdown.style.display = 'block';
            dropdown.setAttribute('aria-hidden', 'false');
            input.setAttribute('aria-expanded', 'true');
        }

        function hideDropdown() {
            dropdown.style.display = 'none';
            dropdown.setAttribute('aria-hidden', 'true');
            input.setAttribute('aria-expanded', 'false');
            activeIndex = -1;
        }

        // Debounced input handler
        input.addEventListener('input', function() {
            var query = this.value.trim();
            clearBtn.style.display = query ? 'flex' : 'none';
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(function() { doSearch(query); }, DEBOUNCE_DELAY);
        });

        // Clear button
        clearBtn.addEventListener('click', function() {
            input.value = '';
            clearBtn.style.display = 'none';
            hideDropdown();
            input.focus();
        });

        // Keyboard navigation
        input.addEventListener('keydown', function(e) {
            var rows = dropdown.querySelectorAll('.qs-result-row');
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                activeIndex = Math.min(activeIndex + 1, rows.length - 1);
                updateActive(rows);
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                activeIndex = Math.max(activeIndex - 1, -1);
                updateActive(rows);
            } else if (e.key === 'Escape' || e.key === 'Tab') {
                hideDropdown();
            } else if (e.key === 'Enter') {
                if (activeIndex >= 0 && rows[activeIndex]) {
                    rows[activeIndex].click();
                }
            }
        });

        function updateActive(rows) {
            rows.forEach(function(r, i) {
                var isActive = i === activeIndex;
                r.classList.toggle('qs-active', isActive);
                r.setAttribute('aria-selected', isActive ? 'true' : 'false');
                if (isActive) { r.scrollIntoView({ block: 'nearest' }); }
            });
        }

        // Close dropdown when clicking outside
        document.addEventListener('click', function(e) {
            if (!wrapper.contains(e.target)) { hideDropdown(); }
        });

    })();

    // ── Pending-Appt page-load handler ─────────────────────────────────────
    // After navigating the schedule to the next available day, this fires and
    // opens the add-appointment popup pre-filled with the slot time / duration.
    (function openPendingApptOnLoad() {
        var pending;
        try {
            var raw = sessionStorage.getItem('carlosPendingAppt');
            if (!raw) return;
            pending = JSON.parse(raw);
            sessionStorage.removeItem('carlosPendingAppt');
        } catch (e) {
            return; // sessionStorage unavailable or JSON parse error
        }
        if (!pending || !pending.startTime) return;

        var ctx2 = document.getElementById('contextPath').value;
        var popupUrl = buildApptUrl(ctx2,
            pending.demographicNo,
            pending.providerNo,
            pending.startTime,
            pending.endTime || '',
            pending.duration,
            pending.year,
            pending.month,
            pending.day);

        // Wait for the page to finish rendering before opening the popup
        window.addEventListener('load', function() {
            popupPage(600, 860, popupUrl);
        });
    })();

	function tooltipStart() {
		document.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(el => {
			const full = el.getAttribute('data-title-full') || '';
			el.setAttribute('title', full);
			
			new bootstrap.Tooltip(el, {
				html: true

			});
		});
	};


    </script>
<style>
.tooltip .tooltip-inner { text-align: left !important; }
.tooltip-inner { max-width: 350px !important; }
.tooltip .tooltip-inner br { line-height: 1.2; }
</style>

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
    const inboxUrl = contextPath + "/web/inboxhub/Inboxhub?method=displayInboxForm";
    const unclaimedLabUrl = contextPath + "/web/inboxhub/Inboxhub?method=displayInboxForm&unclaimed=1";
    const inboxLinkClickEvent = "return openScheduleSection('" + inboxUrl + "', function(u){ popupInboxManager(u, 800); }, event);";
    const unclaimedLabLinkClickEvent = "return openScheduleSection('" + unclaimedLabUrl + "', function(u){ popupInboxManager(u, 800); }, event);";

    const inboxLink = document.getElementById("inboxLink");
    if (inboxLink) {
        inboxLink.setAttribute("onclick", inboxLinkClickEvent);
    }
    const unclaimedLabLink = document.getElementById("unclaimedLabLink");
    if (unclaimedLabLink) {
        unclaimedLabLink.setAttribute("onclick", unclaimedLabLinkClickEvent);
    }

</script>
