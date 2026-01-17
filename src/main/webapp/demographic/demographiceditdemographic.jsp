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

<%@page import="java.nio.charset.StandardCharsets" %>
<%@page import="ca.openosp.openo.commn.ISO36612" %>
<%@page import="ca.openosp.openo.managers.LookupListManager" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%@page import="ca.openosp.openo.util.ConversionUtils" %>
<%@page import="ca.openosp.openo.PMmodule.caisi_integrator.ConformanceTestHelper" %>
<%@page import="ca.openosp.OscarProperties" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="ca.openosp.openo.commn.Gender" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.managers.ProgramManager2" %>
<%@page import="ca.openosp.openo.PMmodule.model.Program" %>
<%@page import="ca.openosp.openo.PMmodule.model.ProgramProvider" %>
<%@page import="java.util.HashSet" %>
<%@page import="ca.openosp.openo.managers.PatientConsentManager" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="apptMainBean" class="ca.openosp.AppointmentMainBean" scope="session"/>
<%
    String demographic$ = request.getParameter("demographic_no");
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName='<%="_demographic$"+demographic$%>' rights="o"
                   reverse="<%=false%>">
    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.accessDenied"/>
    <% response.sendRedirect(request.getContextPath() + "/acctLocked.html");
        authed = false;
    %>
</security:oscarSec>

<%
    if (!authed) {
        return;
    }

%>
<%@ page
        import="java.util.*, java.net.*,java.text.DecimalFormat, ca.openosp.*, ca.openosp.openo.demographic.data.ProvinceNames, ca.openosp.openo.waitinglist.WaitingList, ca.openosp.openo.report.data.DemographicSets,ca.openosp.openo.log.*" %>
<%@ page import="ca.openosp.openo.demographic.data.*" %>
<%@ page import="ca.openosp.openo.demographic.pageUtil.Util" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%@ page import="ca.openosp.openo.commn.dao.*,ca.openosp.openo.commn.model.*" %>
<%@ page import="ca.openosp.openo.commn.OtherIdManager" %>
<%@ page import="ca.openosp.openo.commn.web.Contact2Action" %>
<%@ page import="ca.openosp.openo.casemgmt.model.CaseManagementNoteLink" %>
<%@ page import="ca.openosp.openo.casemgmt.service.CaseManagementManager" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@page import="ca.openosp.openo.managers.DemographicManager" %>
<%@page import="ca.openosp.openo.PMmodule.service.ProgramManager" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProgramDao" %>
<%@page import="ca.openosp.openo.PMmodule.service.AdmissionManager" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="org.apache.commons.lang3.StringUtils" %>

<%!

    CountryCodeDao ccDAO = SpringUtils.getBean(CountryCodeDao.class);
    UserPropertyDAO pref = SpringUtils.getBean(UserPropertyDAO.class);
    List<CountryCode> countryList = ccDAO.getAllCountryCodes();
    DemographicExtDao demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);
    DemographicArchiveDao demographicArchiveDao = SpringUtils.getBean(DemographicArchiveDao.class);
    DemographicExtArchiveDao demographicExtArchiveDao = SpringUtils.getBean(DemographicExtArchiveDao.class);
    ScheduleTemplateCodeDao scheduleTemplateCodeDao = SpringUtils.getBean(ScheduleTemplateCodeDao.class);
    WaitingListDao waitingListDao = SpringUtils.getBean(WaitingListDao.class);
    WaitingListNameDao waitingListNameDao = SpringUtils.getBean(WaitingListNameDao.class);
    OscarProperties oscarProps = OscarProperties.getInstance();
    String privateConsentEnabledProperty = oscarProps.getProperty("privateConsentEnabled");
    boolean privateConsentEnabled = (privateConsentEnabledProperty != null && privateConsentEnabledProperty.equals("true"));
    ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class);
    DemographicCustDao demographicCustDao = (DemographicCustDao) SpringUtils.getBean(DemographicCustDao.class);
    DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    List<Provider> providers = providerDao.getActiveProviders();
    List<Provider> doctors = providerDao.getActiveProvidersByRole("doctor");
    List<Provider> nurses = providerDao.getActiveProvidersByRole("nurse");
    List<Provider> midwifes = providerDao.getActiveProvidersByRole("midwife");

    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    ProgramManager2 programManager2 = SpringUtils.getBean(ProgramManager2.class);
    ProgramManager pm = SpringUtils.getBean(ProgramManager.class);
    ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);
    CaseManagementManager cmm = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);
    LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);
%>

<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>


<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ taglib uri="/WEB-INF/special_tag.tld" prefix="special" %>

<c:set var="ctx" value="${ pageContext.request.contextPath }"/>
<%
    if (session.getAttribute("user") == null) {
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
        return;
    }

    String curProvider_no = (String) session.getAttribute("user");
    String demographic_no = request.getParameter("demographic_no");
    String apptProvider = request.getParameter("apptProvider");
    String appointment = request.getParameter("appointment");
    String userfirstname = (String) session.getAttribute("userfirstname");
    String userlastname = (String) session.getAttribute("userlastname");
    String deepcolor = "#CCCCFF", weakcolor = "#EEEEFF";
    String str = null;
    int nStrShowLen = 20;
    String prov = (oscarProps.getProperty("billregion", "")).trim().toUpperCase();

    List<CaseManagementNoteLink> cml = cmm.getLinkByTableId(CaseManagementNoteLink.DEMOGRAPHIC, Long.valueOf(demographic_no));
    boolean hasImportExtra = (cml.size() > 0);
    String annotation_display = CaseManagementNoteLink.DISP_DEMO;

    LogAction.addLog((String) session.getAttribute("user"), LogConst.READ, LogConst.CON_DEMOGRAPHIC, demographic_no, request.getRemoteAddr(), demographic_no);

    Boolean isMobileOptimized = session.getAttribute("mobileOptimized") != null;
    ProvinceNames pNames = ProvinceNames.getInstance();
    Map<String, String> demoExt = demographicExtDao.getAllValuesForDemo(Integer.parseInt(demographic_no));

    String usSigned = StringUtils.defaultString(apptMainBean.getString(demoExt.get("usSigned")));
    String privacyConsent = StringUtils.defaultString(apptMainBean.getString(demoExt.get("privacyConsent")), "");
    String informedConsent = StringUtils.defaultString(apptMainBean.getString(demoExt.get("informedConsent")), "");

    pageContext.setAttribute("demoExtended", demoExt);

    boolean showConsentsThisTime = false;

    GregorianCalendar now = new GregorianCalendar();
    java.text.SimpleDateFormat simpleDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd");
    String dateString = simpleDateFormat.format(now.getTime());

    java.util.ResourceBundle oscarResources = ResourceBundle.getBundle("oscarResources", request.getLocale());
    String noteReason = oscarResources.getString("oscarEncounter.noteReason.TelProgress");

    if (oscarProps.getProperty("disableTelProgressNoteTitleInEncouterNotes") != null
            && oscarProps.getProperty("disableTelProgressNoteTitleInEncouterNotes").equals("yes")) {
        noteReason = "";
    }


    String currentProgram = "";
    String programId = (String) session.getAttribute(ca.openosp.openo.utility.SessionConstants.CURRENT_PROGRAM_ID);
    if (programId != null && programId.length() > 0) {
        Integer prId = null;
        try {
            prId = Integer.parseInt(programId);
        } catch (NumberFormatException e) {
            //do nothing
        }
        if (prId != null) {
            ProgramManager2 programManager = SpringUtils.getBean(ProgramManager2.class);
            Program p = programManager.getProgram(loggedInInfo, prId);
            if (p != null) {
                currentProgram = p.getName();
            }
        }
    }

    // get a list of programs the patient has consented to.
    if (oscarProps.getBooleanProperty("USE_NEW_PATIENT_CONSENT_MODULE", "true")) {
        PatientConsentManager patientConsentManager = SpringUtils.getBean(PatientConsentManager.class);
        pageContext.setAttribute("consentTypes", patientConsentManager.getActiveConsentTypes());
        pageContext.setAttribute("patientConsents", patientConsentManager.getAllConsentsByDemographic(loggedInInfo, Integer.parseInt(demographic_no)));
    }

    LookupList firstNationCommunities = lookupListManager.findLookupListByName(LoggedInInfo.getLoggedInInfoFromSession(request), "firstNationCommunity");

    if (firstNationCommunities != null) {
        LookupListItem fncommunity = lookupListManager.findLookupListItemByLookupListIdAndValue(LoggedInInfo.getLoggedInInfoFromSession(request), firstNationCommunities.getId(), demoExt.get("fNationCom"));
        if (fncommunity != null) {
            pageContext.setAttribute("fncommunity", fncommunity.getLabel());
        }
    }

    LookupList phuLookupList = lookupListManager.findLookupListByName(LoggedInInfo.getLoggedInInfoFromSession(request), "phu");

    if (phuLookupList != null) {
        LookupListItem phuItem = lookupListManager.findLookupListItemByLookupListIdAndValue(loggedInInfo, phuLookupList.getId(), demoExt.get("PHU"));
        if (phuItem != null && phuItem.isActive()) {
            pageContext.setAttribute("phuName", phuItem.getLabel());
        }
    }


%>

<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.log.LogAction" %>
<%@ page import="ca.openosp.openo.log.LogConst" %>
<%@ page import="ca.openosp.openo.demographic.data.DemographicMerged" %>
<%@ page import="ca.openosp.openo.demographic.data.DemographicRelationship" %>
<%@ page import="ca.openosp.openo.utility.*" %>
<%@ page import="ca.openosp.openo.commn.model.*" %>
<%@ page import="ca.openosp.openo.commn.dao.*" %>
<%@ page import="ca.openosp.MyDateFormat" %>
<%@ page import="ca.openosp.SxmlMisc" %>
<!DOCTYPE html>
<html>

    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.title"/></title>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <oscar:oscarPropertiesCheck property="DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM" value="true">
            <link rel="stylesheet" type="text/css" href="${ pageContext.request.contextPath }/css/healthCareTeam.css"/>
        </oscar:oscarPropertiesCheck>

        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="<%=request.getContextPath()%>/share/calendar/calendar.css" title="win2k-cold-1"/>

        <script type="text/javascript" src="<%=request.getContextPath()%>/library/jquery/jquery-1.12.0.min.js"></script>
        <script type="text/javascript"
                src="<%=request.getContextPath()%>/library/jquery/jquery-ui-1.12.1.min.js"></script>
        <% if (oscarProps.getBooleanProperty("workflow_enhance", "true")) { %>
        <script language="javascript" src="<%=request.getContextPath() %>/hcHandler/hcHandler.js"></script>
        <script language="javascript"
                src="<%=request.getContextPath() %>/hcHandler/hcHandlerUpdateDemographic.js"></script>
        <link rel="stylesheet" href="<%=request.getContextPath() %>/hcHandler/hcHandler.css" type="text/css"/>
        <% } %>
        <link rel="stylesheet" href="<%=request.getContextPath() %>/demographic/demographiceditdemographic.css"
              type="text/css"/>

        <!-- main calendar program -->
        <script type="text/javascript" src="<%=request.getContextPath()%>/share/calendar/calendar.js"></script>

        <!-- language for the calendar -->
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>

        <!-- the following script defines the Calendar.setup helper function, which makes
       adding a calendar a matter of 1 or 2 lines of code. -->
        <script type="text/javascript" src="<%=request.getContextPath()%>/share/calendar/calendar-setup.js"></script>

        <script type="text/javascript" src="<%=request.getContextPath() %>/js/check_hin.js"></script>

        <script type="text/javascript" src="<%=request.getContextPath() %>/js/nhpup_1.1.js"></script>

        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="<%=request.getContextPath()%>/share/calendar/calendar.css" title="win2k-cold-1"/>
        <% if (isMobileOptimized) { %>
        <meta name="viewport"
              content="initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=no, width=device-width"/>
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/mobile/editdemographicstyle.css">
        <% } else { %>
        <link rel="stylesheet" type="text/css" href="<%=request.getContextPath()%>/oscarEncounter/encounterStyles.css">
        <link rel="stylesheet" type="text/css" href="<%=request.getContextPath()%>/share/css/searchBox.css">
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <% } %>
        <script language="javascript" type="text/javascript"
                src="<%=request.getContextPath()%>/share/javascript/Oscar.js"></script>

        <!--popup menu for encounter type -->
        <script src="<c:out value="${ctx}"/>/share/javascript/popupmenu.js"
                type="text/javascript"></script>
        <script src="<c:out value="${ctx}"/>/share/javascript/menutility.js"
                type="text/javascript"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/share/javascript/prototype.js"></script>
        <script>
            jQuery.noConflict();
        </script>

        <script type="text/javascript"
                src="<%=request.getContextPath() %>/demographic/demographiceditdemographic.js.jsp"></script>

        <script language="JavaScript" type="text/javascript">

            function checkTypeIn() {
                var dob = document.titlesearch.keyword;
                typeInOK = false;

                if (dob.value.indexOf('%b610054') == 0 && dob.value.length > 18) {
                    document.titlesearch.keyword.value = dob.value.substring(8, 18);
                    document.titlesearch.search_mode[4].checked = true;
                }

                if (document.titlesearch.search_mode[2].checked) {
                    if (dob.value.length == 8) {
                        dob.value = dob.value.substring(0, 4) + "-" + dob.value.substring(4, 6) + "-" + dob.value.substring(6, 8);
                        //alert(dob.value.length);
                        typeInOK = true;
                    }
                    if (dob.value.length != 10) {
                        alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.msgWrongDOB"/>");
                        typeInOK = false;
                    }

                    return typeInOK;
                } else {
                    return true;
                }
            }

            function checkName() {
                var typeInOK = false;
                if (document.updatedelete.last_name.value != "" && document.updatedelete.first_name.value != "" && document.updatedelete.last_name.value != " " && document.updatedelete.first_name.value != " ") {
                    typeInOK = true;
                } else {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgNameRequired"/>");
                }
                return typeInOK;
            }

            function checkDate(yyyy, mm, dd, err_msg) {

                var typeInOK = false;

                if (checkTypeNum(yyyy) && checkTypeNum(mm) && checkTypeNum(dd)) {
                    var check_date = new Date(yyyy, (mm - 1), dd);
                    var now = new Date();
                    var year = now.getFullYear();
                    var month = now.getMonth() + 1;
                    var date = now.getDate();
                    //alert(yyyy + " | " + mm + " | " + dd + " " + year + " " + month + " " +date);

                    var young = new Date(year, month, date);
                    var old = new Date(1800, 1, 1);
                    //alert(check_date.getTime() + " | " + young.getTime() + " | " + old.getTime());
                    if (check_date.getTime() <= young.getTime() && check_date.getTime() >= old.getTime() && yyyy.length == 4) {
                        typeInOK = true;
                    }
                    if (yyyy == "0000") {
                        typeInOK = false;
                    }
                }

                if (!isValidDate(dd, mm, yyyy) || !typeInOK) {
                    alert(err_msg + "\n<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgWrongDate"/>");
                    typeInOK = false;
                }

                return typeInOK;
            }

            function checkDob() {
                var yyyy = document.updatedelete.year_of_birth.value;
                var mm = document.updatedelete.month_of_birth.value;
                var dd = document.updatedelete.date_of_birth.value;

                return checkDate(yyyy, mm, dd, "<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.msgWrongDOB"/>");
            }

            function isValidDate(day, month, year) {
                month = (month - 1);
                dteDate = new Date(year, month, day);
                //alert(dteDate);
                return ((day == dteDate.getDate()) && (month == dteDate.getMonth()) && (year == dteDate.getFullYear()));
            }

            function checkHin() {
                var hin = document.updatedelete.hin.value;
                var province = document.updatedelete.hc_type.value;

                if (!isValidHin(hin, province)) {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgWrongHIN"/>");
                    return (false);
                }

                return (true);
            }


            function rosterStatusChangedNotBlank() {
                if (rosterStatusChanged()) {

                    if (document.updatedelete.roster_status.value == "") {
                        alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgBlankRoster"/>");
                        document.updatedelete.roster_status.focus();
                        return false;
                    }

                    return true;
                }
                return false;
            }

            function rosterStatusDateAllowed() {
                if (document.updatedelete.roster_status.value == "") {
                    yyyy = document.updatedelete.roster_date_year.value.trim();
                    mm = document.updatedelete.roster_date_month.value.trim();
                    dd = document.updatedelete.roster_date_day.value.trim();

                    if (yyyy != "" || mm != "" || dd != "") {
                        alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.msgForbiddenRosterDate"/>");
                        return false;
                    }
                    return true;
                }
                return true;
            }

            function rosterStatusDateValid(trueIfBlank) {
                yyyy = document.updatedelete.roster_date_year.value.trim();
                mm = document.updatedelete.roster_date_month.value.trim();
                dd = document.updatedelete.roster_date_day.value.trim();
                var errMsg = "<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.msgWrongRosterDate"/>";

                if (trueIfBlank) {
                    errMsg += "\n<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.msgLeaveBlank"/>";
                    if (yyyy == "" && mm == "" && dd == "") return true;
                }
                return checkDate(yyyy, mm, dd, errMsg);
            }


            function rosterEnrolledToValid(trueIfBlank) {
                var val = document.updatedelete.roster_enrolled_to.value.trim();

                if (trueIfBlank) {
                    errMsg += "\n<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.msgLeaveBlank"/>";
                    if (val == "") return true;
                }

                var errMsg = '';

                if (val == "") {
                    errMsg += "<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.msgWrongRosterEnrolledTo"/>";
                }

                if (errMsg != '') {
                    alert(errMsg);
                    return false;
                }
                return true;
            }

            function rosterStatusTerminationDateFilled() {
                yyyy = document.updatedelete.roster_termination_date_year.value.trim();
                mm = document.updatedelete.roster_termination_date_month.value.trim();
                dd = document.updatedelete.roster_termination_date_day.value.trim();

                if (yyyy != '' || mm != '' || dd != '') {
                    return true;
                }
                return false;
            }

            function rosterStatusTerminationReasonFilled() {
                reason = document.updatedelete.roster_termination_reason.value;

                if (reason != '') {
                    return true;
                }
                return false;
            }

            function rosterStatusTerminationDateValid(trueIfBlank) {
                yyyy = document.updatedelete.roster_termination_date_year.value.trim();
                mm = document.updatedelete.roster_termination_date_month.value.trim();
                dd = document.updatedelete.roster_termination_date_day.value.trim();
                var errMsg = "<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.msgWrongRosterTerminationDate"/>";

                if (trueIfBlank) {
                    errMsg += "\n<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.msgLeaveBlank"/>";
                    if (yyyy == "" && mm == "" && dd == "") return true;
                }
                return checkDate(yyyy, mm, dd, errMsg);
            }

            function rosterStatusTerminationReasonNotBlank() {
                if (document.updatedelete.roster_termination_reason.value == "") {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgNoTerminationReason"/>");
                    return false;
                }
                return true;
            }


            function patientStatusDateValid(trueIfBlank) {
                var yyyy = document.updatedelete.patientstatus_date_year.value.trim();
                var mm = document.updatedelete.patientstatus_date_month.value.trim();
                var dd = document.updatedelete.patientstatus_date_day.value.trim();

                if (trueIfBlank) {
                    if (yyyy == "" && mm == "" && dd == "") return true;
                }
                return checkDate(yyyy, mm, dd, "<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.msgWrongPatientStatusDate"/>");
            }


            function checkONReferralNo() {
                <%
		String skip = OscarProperties.getInstance().getProperty("SKIP_REFERRAL_NO_CHECK","false");
		if(!skip.equals("true")) {
	%>
                var referralNo = document.updatedelete.r_doctor_ohip.value;
                if (document.updatedelete.hc_type.value == 'ON' && referralNo.length > 0 && referralNo.length != 6) {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgWrongReferral"/>");
                }

                <% } %>
            }


            function newStatus() {
                newOpt = prompt("<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgPromptStatus"/>:", "");
                if (newOpt == null) {
                    return;
                } else if (newOpt != "") {
                    document.updatedelete.patient_status.options[document.updatedelete.patient_status.length] = new Option(newOpt, newOpt);
                    document.updatedelete.patient_status.options[document.updatedelete.patient_status.length - 1].selected = true;
                } else {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgInvalidEntry"/>");
                }
            }

            function newStatus1() {
                newOpt = prompt("<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgPromptStatus"/>:", "");
                if (newOpt == null) {
                    return;
                } else if (newOpt != "") {
                    document.updatedelete.roster_status.options[document.updatedelete.roster_status.length] = new Option(newOpt, newOpt);
                    document.updatedelete.roster_status.options[document.updatedelete.roster_status.length - 1].selected = true;
                } else {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgInvalidEntry"/>");
                }
            }

        </script>
        <script language="JavaScript">
            function showEdit() {
                document.getElementById('editDemographic').style.display = 'table';
                document.getElementById('viewDemographics2').style.display = 'none';
                document.getElementById('updateButton').style.display = 'block';
                document.getElementById('swipeButton').style.display = 'block';
                document.getElementById('editBtn').style.display = 'none';
                document.getElementById('closeBtn').style.display = 'inline';
            }

            function showHideDetail() {
                showHideItem('editDemographic');
                showHideItem('viewDemographics2');
                showHideItem('updateButton');
                showHideItem('swipeButton');

                showHideBtn('editBtn');
                showHideBtn('closeBtn');

            }

            // Used to display demographic sections, where sections is an array of id's for
            // div elements with class "demographicSection"
            function showHideMobileSections(sections) {
                showHideItem('mobileDetailSections');
                for (var i = 0; i < sections.length; i++) {
                    showHideItem(sections[i]);
                }
                // Change behaviour of cancel button
                var cancelValue = "<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnCancel"/>";
                var backValue = "<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnBack"/>";
                var cancelBtn = document.getElementById('cancelButton');
                if (cancelBtn.value == cancelValue) {
                    cancelBtn.value = backValue;
                    cancelBtn.onclick = function () {
                        showHideMobileSections(sections);
                    };
                } else {
                    cancelBtn.value = cancelValue;
                    cancelBtn.onclick = function () {
                        self.close();
                    };
                }
            }

            function showHideItem(id) {
                if (document.getElementById(id).style.display === 'inline' || document.getElementById(id).style.display === 'block' || document.getElementById(id).style.display === 'table') {
                    document.getElementById(id).style.display = 'none';
                } else {
                    document.getElementById(id).style.display = 'table';
                }
            }

            function showHideBtn(id) {
                if (document.getElementById(id).style.display === 'none') {
                    document.getElementById(id).style.display = 'inline';
                } else {
                    document.getElementById(id).style.display = 'none';
                }
            }


            function showItem(id) {
                document.getElementById(id).style.display = 'inline';
            }

            function hideItem(id) {
                document.getElementById(id).style.display = 'none';
            }

            <security:oscarSec roleName="<%= roleName$ %>" objectName="_eChart" rights="r" reverse="<%= false %>" >
            var numMenus = 1;
            var encURL = "<c:out value="${ctx}"/>/oscarEncounter/IncomingEncounter.do?providerNo=<%=curProvider_no%>&appointmentNo=&demographicNo=<%=demographic_no%>&curProviderNo=&reason=<%=URLEncoder.encode(noteReason, StandardCharsets.UTF_8)%>&encType=<%=URLEncoder.encode("telephone encounter with client", StandardCharsets.UTF_8)%>&userName=<%=URLEncoder.encode( userfirstname+" "+userlastname, StandardCharsets.UTF_8) %>&curDate=<%=dateString%>&appointmentDate=&startTime=&status=";

            function showMenu(menuNumber, eventObj) {
                var menuId = 'menu' + menuNumber;
                return showPopup(menuId, eventObj);
            }

            <%if (oscarProps.getProperty("workflow_enhance")!=null && oscarProps.getProperty("workflow_enhance").equals("true")) {%>

            function showAppt(targetAppt, eventObj) {
                if (eventObj) {
                    targetObjectId = 'menu' + targetAppt;
                    hideCurrentPopup();
                    eventObj.cancelBubble = true;
                    moveObject(targetObjectId, 300, 200);
                    if (changeObjectVisibility(targetObjectId, 'visible')) {
                        window.currentlyVisiblePopup = targetObjectId;
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } // showPopup

            function closeApptBox(e) {
                if (!e) var e = window.event;
                var tg = (window.event) ? e.srcElement : e.target;
                if (tg.nodeName != 'DIV') return;
                var reltg = (e.relatedTarget) ? e.relatedTarget : e.toElement;
                while (reltg != tg && reltg.nodeName != 'BODY')
                    reltg = reltg.parentNode;
                if (reltg == tg) return;

                // Mouseout took place when mouse actually left layer
                // Handle event
                hideCurrentPopup();
            }

            <%}%>

            function add2url(txt) {
                var reasonLabel = "reason=";
                var encTypeLabel = "encType=";
                var beg = encURL.indexOf(reasonLabel);
                beg += reasonLabel.length;
                var end = encURL.indexOf("&", beg);
                var part1 = encURL.substring(0, beg);
                var part2 = encURL.substr(end);
                encURL = part1 + encodeURI(txt) + part2;
                beg = encURL.indexOf(encTypeLabel);
                beg += encTypeLabel.length;
                end = encURL.indexOf("&", beg);
                part1 = encURL.substring(0, beg);
                part2 = encURL.substr(end);
                encURL = part1 + encodeURI(txt) + part2;
                popupEChart(710, 1024, encURL);
                return false;
            }

            function customReason() {
                var txtInput;
                var list = document.getElementById("listCustom");
                if (list.style.display == "block")
                    list.style.display = "none";
                else {
                    list.style.display = "block";
                    txtInput = document.getElementById("txtCustom");
                    txtInput.focus();
                }

                return false;
            }

            function grabEnterCustomReason(event) {

                var txtInput = document.getElementById("txtCustom");
                if (window.event && window.event.keyCode == 13) {
                    add2url(txtInput.value);
                } else if (event && event.which == 13) {
                    add2url(txtInput.value);
                }

                return true;
            }

            function addToPatientSet(demoNo, patientSet) {
                if (patientSet == "-") return;
                window.open("addDemoToPatientSet.jsp?demoNo=" + demoNo + "&patientSet=" + patientSet, "addpsetwin", "width=50,height=50");
            }

            </security:oscarSec>

            var demographicNo = '<%=demographic_no%>';


            function checkRosterStatus2() {
                <oscar:oscarPropertiesCheck property="FORCED_ROSTER_INTEGRATOR_LOCAL_STORE" value="yes">
                var rosterSelect = document.getElementById("roster_status");
                if (rosterSelect.getValue() == "RO") {
                    var primaryEmr = document.getElementById("primaryEMR");
                    primaryEmr.value = "1";
                    primaryEmr.disable(true);
                }
                </oscar:oscarPropertiesCheck>
                return true;
            }

            jQuery(document).ready(function ($) {
                jQuery("a.popup").click(function () {
                    var $me = jQuery(this);
                    var name = $me.attr("title");
                    var rel = $me.attr("rel");
                    var content = jQuery("#" + rel).html();
                    var win = window.open(null, name, "height=250,width=600,location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes");
                    jQuery(win.document.body).html(content);
                    return false;
                });

            });


            function showCbiReminder() {
                alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.updateCBIReminder"/>');
            }


            var addressHistory = "";
            var homePhoneHistory = "";
            var workPhoneHistory = "";
            var cellPhoneHistory = "";

            function generateMarkup(addresses, type, header) {
                var markup = '<table border="0" cellpadding="2" cellspacing="2" width="200px">';
                markup += '<tr><th><b>Date Entered</b></th><th><b>' + header + '</b></th></tr>';
                for (var x = 0; x < addresses.length; x++) {
                    if (addresses[x].type == type) {
                        markup += '<tr><td>' + addresses[x].dateSeen + '</td><td>' + addresses[x].name + '</td></tr>';
                    }
                }
                markup += "</table>";
                return markup;
            }

            function updatePaperArchive(paperArchiveSel) {
                var val = jQuery("#paper_chart_archived").val();
                if (val == '' || val == 'NO') {
                    jQuery("#paper_chart_archived_date").val('');
                    jQuery("#paper_chart_archived_program").val('');
                }
                if (val == 'YES') {
                    jQuery("#paper_chart_archived_program").val('<%=currentProgram%>');
                }
            }

            function updatePatientStatusDate() {
                var d = new Date();
                document.updatedelete.patientstatus_date_year.value = d.getFullYear();
                var mth = "" + (d.getMonth() + 1);
                if (mth.length == 1) {
                    mth = "0" + mth;
                }
                document.updatedelete.patientstatus_date_month.value = mth;
                var day = "" + d.getDate();
                if (day.length == 1) {
                    day = "0" + day;
                }
                document.updatedelete.patientstatus_date_day.value = day;
            }


            jQuery(document).ready(function () {
                var addresses;

                jQuery.getJSON("${ctx}/demographicSupport.do",
                    {
                        method: "getAddressAndPhoneHistoryAsJson",
                        demographicNo: demographicNo
                    },
                    function (response) {
                        if (response instanceof Array) {
                            addresses = response;
                        } else {
                            var arr = new Array();
                            arr[0] = response;
                            addresses = arr;
                        }

                        addressHistory = generateMarkup(addresses, 'address', 'Address');
                        homePhoneHistory = generateMarkup(addresses, 'phone', 'Phone #');
                        workPhoneHistory = generateMarkup(addresses, 'phone2', 'Phone #');
                        cellPhoneHistory = generateMarkup(addresses, 'cell', 'Phone #');
                    });
            });

            function consentClearBtn(radioBtnName) {

                if (confirm("Proceed to clear all record of this consent?")) {

                    //clear out opt-in/opt-out radio buttons
                    var ele = document.getElementsByName(radioBtnName);
                    var preset = document.getElementById("consentPreset_" + radioBtnName).value;

                    for (var i = 0; i < ele.length; i++) {
                        ele[i].checked = false;
                    }

                    //hide consent date field from displaying
                    var consentDate = document.getElementById("consentDate_" + radioBtnName);

                    if (consentDate) {
                        consentDate.style.display = "none";
                    }

                    // is the user trying to clear an old consent or are they just curious what the clear button does.
                    if (preset === "true") {
                        // set the delete parameter to update the deleted status in the database entry.
                        document.getElementById("deleteConsent_" + radioBtnName).value = 1;
                    }
                }
            }

            function updateEnrolledTo() {
                var rosterSelect = document.getElementById("roster_status");
                if (rosterSelect.getValue() == "RO") {
                    if (document.getElementById("enrolledTo").value != document.getElementById("mrp").value && confirm("Enrolment status changed to 'rostered'. Would you like to set the 'Enrolled To' to the MRP?")) {
                        document.getElementById("enrolledTo").value = document.getElementById("mrp").value;
                    }
                } else {
                    if (document.getElementById("enrolledTo").value != "" && confirm("Enrolment status changed to '" + rosterSelect.getValue() + "''. Would you like to clear the 'Enrolled To' field?")) {
                        document.getElementById("enrolledTo").value = "";
                    }
                }
            }

            function validateHC() {
                const hin = jQuery("#hinBox").val();
                const ver = jQuery("#verBox").val();
                const hcType = jQuery("#hcTypeBox").val();

                jQuery.ajax({
                    type: "GET",
                    url: '<%=request.getContextPath() %>/ws/rs/patientDetailStatusService/validateHC?hin=' + hin + '&ver=' + ver,
                    dataType: 'json',
                    contentType: 'application/json',
                    success: function (data) {
                        alert(data.responseDescription);
                    },
                    error: function (data) {
                        alert('An error occured.');
                    }
                });
            }

        </script>
        <script type="application/javascript">
            <%
		if(oscarProps.getProperty("demographicExtJScript") != null) {
			out.println(oscarProps.getProperty("demographicExtJScript"));
		}
	%>
        </script>

        <style>
            /* for the search buttons at the top of the page
			this should be removed if the page is updated to bootstrap
		*/
            .searchBox .select-group, .searchBox div.input-group-btn {
                display: flex;
                flex-direction: row;
                align-items: stretch;
            }

            .searchBox {
                margin: 0 !important;
            }

        </style>
    </head>
    <body onLoad="setfocus(); checkONReferralNo(); formatPhoneNum(); checkRosterStatus2();"
          topmargin="0" leftmargin="0" rightmargin="0" id="demographiceditdemographic">
    <%
        Demographic demographic = demographicDao.getDemographic(demographic_no);
        List<DemographicArchive> archives = demographicArchiveDao.findByDemographicNo(Integer.parseInt(demographic_no));
        List<DemographicExtArchive> extArchives = demographicExtArchiveDao.getDemographicExtArchiveByDemoAndKey(Integer.parseInt(demographic_no), "demo_cell");

        AdmissionManager admissionManager = SpringUtils.getBean(AdmissionManager.class);
        Admission bedAdmission = null;
        Admission communityAdmission = admissionManager.getCurrentCommunityProgramAdmission(demographic.getDemographicNo());
        List<Admission> serviceAdmissions = admissionManager.getCurrentServiceProgramAdmission(demographic.getDemographicNo());
        if (serviceAdmissions == null) {
            serviceAdmissions = new ArrayList<Admission>();
        }
        pageContext.setAttribute("demographic", demographic, PageContext.PAGE_SCOPE);
    %>
    <div id="editDemographicWrapper" style="margin: auto 10px;">
        <table class="MainTable" id="scrollNumber1" name="encounterTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgPatientDetailRecord"/>
                </td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar">
                        <tr>
                            <td>
                                <%
                                    //----------------------------REFERRAL DOCTOR------------------------------
                                    String rdohip = "", rd = "", fd = "", family_doc = "";

                                    String resident = "", nurse = "", alert = "", notes = "", midwife = "";

                                    DemographicCust demographicCust = demographicCustDao.find(Integer.parseInt(demographic_no));
                                    if (demographicCust != null) {
                                        resident = demographicCust.getResident() == null ? "" : demographicCust.getResident();
                                        nurse = demographicCust.getNurse() == null ? "" : demographicCust.getNurse();
                                        alert = demographicCust.getAlert() == null ? "" : demographicCust.getAlert();
                                        ;
                                        midwife = demographicCust.getMidwife() == null ? "" : demographicCust.getMidwife();
                                        ;
                                        notes = SxmlMisc.getXmlContent(demographicCust.getNotes(), "unotes");

                                        resident = resident == null ? "" : resident;
                                        nurse = nurse == null ? "" : nurse;
                                        alert = alert == null ? "" : alert;
                                        midwife = midwife == null ? "" : midwife;
                                        notes = notes == null ? "" : notes;
                                    }

                                    int dob_year = 0, dob_month = 0, dob_date = 0;
                                    String birthYear = "0000", birthMonth = "00", birthDate = "00";


                                    if (demographic == null) {
                                        out.println("failed!!!");
                                    } else {
                                        if (true) {
                                            //----------------------------REFERRAL DOCTOR------------------------------
                                            fd = demographic.getFamilyDoctor();
                                            if (fd == null) {
                                                rd = "";
                                                rdohip = "";
                                                family_doc = "";
                                            } else {
                                                rd = SxmlMisc.getXmlContent(StringUtils.trimToEmpty(demographic.getFamilyDoctor()), "rd");
                                                rd = rd != null && !rd.equals("null") ? rd : "";
                                                rdohip = SxmlMisc.getXmlContent(StringUtils.trimToEmpty(demographic.getFamilyDoctor()), "rdohip");
                                                rdohip = rdohip != null && !rdohip.equals("null") ? rdohip : "";
                                                family_doc = SxmlMisc.getXmlContent(StringUtils.trimToEmpty(demographic.getFamilyDoctor()), "family_doc");
                                                family_doc = family_doc != null ? family_doc : "";
                                            }
                                            //----------------------------REFERRAL DOCTOR --------------end-----------

                                            if (ca.openosp.openo.util.StringUtils.filled(demographic.getYearOfBirth()))
                                                birthYear = StringUtils.trimToEmpty(demographic.getYearOfBirth());
                                            if (ca.openosp.openo.util.StringUtils.filled(demographic.getMonthOfBirth()))
                                                birthMonth = StringUtils.trimToEmpty(demographic.getMonthOfBirth());
                                            if (ca.openosp.openo.util.StringUtils.filled(demographic.getDateOfBirth()))
                                                birthDate = StringUtils.trimToEmpty(demographic.getDateOfBirth());

                                            dob_year = Integer.parseInt(birthYear);
                                            dob_month = Integer.parseInt(birthMonth);
                                            dob_date = Integer.parseInt(birthDate);

                                %> <%=demographic.getLastName()%>,
                                <%=demographic.getFirstName()%> <%=demographic.getSex()%>
                                <%=demographic.getAgeAsOf(new Date())%>

                                <span style="margin-left: 20px;font-style:italic">
				<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgNextAppt"/>: <oscar:nextAppt
                                        demographicNo='<%=demographic.getDemographicNo().toString()%>'/>
				</span>

                                <%
                                    if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {%>
                                <jsp:include page="/admin/IntegratorStatus.jspf"/>
                                <%}%>

                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="MainTableLeftColumn" valign="top">
                    <table border=0 cellspacing=0 width="100%" id="appt_table">
                        <tr class="Header">
                            <td style="font-weight: bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgAppt"/></td>
                        </tr>
                        <tr id="appt_hx">
                            <td><a
                                    href='<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=<%=demographic.getDemographicNo()%>&last_name=<%=URLEncoder.encode(demographic.getLastName(), StandardCharsets.UTF_8)%>&first_name=<%=URLEncoder.encode(demographic.getFirstName(), StandardCharsets.UTF_8)%>&orderby=appttime&displaymode=appt_history&dboperation=appt_history&limit1=0&limit2=25'><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnApptHist"/></a>
                            </td>
                        </tr>

                        <%
                            String wLReadonly = "";
                            WaitingList wL = WaitingList.getInstance();
                            if (!wL.getFound()) {
                                wLReadonly = "readonly";
                            }
                            if (wLReadonly.equals("")) {
                        %>
                        <tr>
                            <td><a
                                    href="<%= request.getContextPath() %>/oscarWaitingList/SetupDisplayPatientWaitingList.do?demographic_no=<%=demographic.getDemographicNo()%>">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgWaitList"/></a>
                            </td>
                        </tr>
                    </table>
                    <table border=0 cellspacing=0 width="100%">
                        <%}%>
                        <security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="r">
                            <tr class="Header">
                                <td style="font-weight: bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.billing"/></td>
                            </tr>
                            <tr>
                                <td>
                                    <%
                                        if ("CLINICAID".equals(prov)) {
                                    %>
                                    <a href="<%= request.getContextPath() %>/billing.do?billRegion=CLINICAID&action=invoice_reports" target="_blank">
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgInvoiceList"/>
                                    </a>
                                    <%
                                    } else if ("ON".equals(prov)) {
                                    %>
                                    <a href="javascript: function myFunction() {return false; }"
                                       onClick="popupPage(500,800,'<%= request.getContextPath() %>/billing/CA/ON/billinghistory.jsp?demographic_no=<%=demographic.getDemographicNo()%>&last_name=<%=URLEncoder.encode(demographic.getLastName(), StandardCharsets.UTF_8)%>&first_name=<%=URLEncoder.encode(demographic.getFirstName(), StandardCharsets.UTF_8)%>&orderby=appointment_date&displaymode=appt_history&dboperation=appt_history&limit1=0&limit2=10')">
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgBillHistory"/></a>
                                    <%
                                    } else {
                                    %>
                                    <a href="#"
                                       onclick="popupPage(800,1000,'<%= request.getContextPath() %>/billing/CA/BC/billStatus.jsp?lastName=<%=URLEncoder.encode(demographic.getLastName(), StandardCharsets.UTF_8)%>&firstName=<%=URLEncoder.encode(demographic.getFirstName(), StandardCharsets.UTF_8)%>&filterPatient=true&demographicNo=<%=demographic.getDemographicNo()%>');return false;">
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgInvoiceList"/></a>


                                    <br/>
                                    <a href="javascript:void(0);" onclick="return !showMenu('2', event);"
                                       onmouseover="callEligibilityWebService('<%=request.getContextPath()%>/billing/CA/BC/ManageTeleplan.do','returnTeleplanMsg');"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCheckElig"/></a>
                                    <div id='menu2' class='menu' onclick='event.cancelBubble = true;'
                                         style="width:350px;">
                                        <span id="search_spinner"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgLoading"/></span>
                                        <span id="returnTeleplanMsg"></span>
                                    </div>
                                    <%}%>
                                </td>
                            </tr>
                            <tr>
                                <td><a
                                        href="javascript: function myFunction() {return false; }"
                                        onClick="popupPage(700, 1000, '<%=request.getContextPath()%>/billing.do?billRegion=<%=URLEncoder.encode(prov, StandardCharsets.UTF_8)%>&billForm=<%=URLEncoder.encode(oscarProps.getProperty("default_view"), StandardCharsets.UTF_8)%>&hotclick=&appointment_no=0&demographic_name=<%=URLEncoder.encode(demographic.getLastName(), StandardCharsets.UTF_8)%>%2C<%=URLEncoder.encode(demographic.getFirstName(), StandardCharsets.UTF_8)%>&demographic_no=<%=demographic.getDemographicNo()%>&providerview=<%=demographic.getProviderNo()%>&user_no=<%=curProvider_no%>&apptProvider_no=none&appointment_date=<%=dateString%>&start_time=00:00:00&bNewForm=1&status=t');return false;"
                                        title="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgBillPatient"/>"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgCreateInvoice"/></a></td>
                            </tr>
                            <%
                                if ("ON".equals(prov)) {
                                    String default_view = oscarProps.getProperty("default_view", "");

                                    if (!oscarProps.getProperty("clinic_no", "").startsWith("1022")) { // part 2 of quick hack to make Dr. Hunter happy
                            %>
                            <tr>
                                <td><a
                                        href="javascript: function myFunction() {return false; }"
                                        onClick="window.open('<%= request.getContextPath() %>/billing/CA/ON/specialtyBilling/fluBilling/addFluBilling.jsp?function=demographic&functionid=<%=demographic.getDemographicNo()%>&creator=<%=curProvider_no%>&demographic_name=<%=URLEncoder.encode(demographic.getLastName(), StandardCharsets.UTF_8)%>%2C<%=URLEncoder.encode(demographic.getFirstName(), StandardCharsets.UTF_8)%>&hin=<%=URLEncoder.encode(demographic.getHin()!=null?demographic.getHin():"", StandardCharsets.UTF_8)%><%=URLEncoder.encode(demographic.getVer()!=null?demographic.getVer():"", StandardCharsets.UTF_8)%>&demo_sex=<%=URLEncoder.encode(demographic.getSex(), StandardCharsets.UTF_8)%>&demo_hctype=<%=URLEncoder.encode(demographic.getHcType()==null?"null":demographic.getHcType(), StandardCharsets.UTF_8)%>&rd=<%=URLEncoder.encode(rd==null?"null":rd, StandardCharsets.UTF_8)%>&rdohip=<%=URLEncoder.encode(rdohip==null?"null":rdohip, StandardCharsets.UTF_8)%>&dob=<%=MyDateFormat.getStandardDate(Integer.parseInt(birthYear),Integer.parseInt(birthMonth),Integer.parseInt(birthDate))%>&mrp=<%=demographic.getProviderNo() != null ? demographic.getProviderNo() : ""%>','', 'scrollbars=yes,resizable=yes,width=720,height=500');return false;"
                                        title='<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgAddFluBill"/>'><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgFluBilling"/></a></td>
                            </tr>
                            <% } %>
                            <tr>
                                <td><a
                                        href="javascript: function myFunction() {return false; }"
                                        onClick="popupS('<%= request.getContextPath() %>/billing/CA/ON/billingShortcutPg1.jsp?billRegion=<%=URLEncoder.encode(prov, StandardCharsets.UTF_8)%>&billForm=<%=URLEncoder.encode(oscarProps.getProperty("hospital_view", default_view), StandardCharsets.UTF_8)%>&hotclick=&appointment_no=0&demographic_name=<%=URLEncoder.encode(demographic.getLastName(), StandardCharsets.UTF_8)%>%2C<%=URLEncoder.encode(demographic.getFirstName(), StandardCharsets.UTF_8)%>&demographic_no=<%=demographic.getDemographicNo()%>&providerview=<%=demographic.getProviderNo()%>&user_no=<%=curProvider_no%>&apptProvider_no=none&appointment_date=<%=dateString%>&start_time=00:00:00&bNewForm=1&status=t');return false;"
                                        title="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgBillPatient"/>"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgHospitalBilling"/></a></td>
                            </tr>
                            <tr>
                                <td><a
                                        href="javascript: function myFunction() {return false; }"
                                        onClick="window.open('<%= request.getContextPath() %>/billing/CA/ON/addBatchBilling.jsp?demographic_no=<%=demographic.getDemographicNo().toString()%>&creator=<%=curProvider_no%>&demographic_name=<%=URLEncoder.encode(demographic.getLastName(), StandardCharsets.UTF_8)%>%2C<%=URLEncoder.encode(demographic.getFirstName(), StandardCharsets.UTF_8)%>&hin=<%=demographic.getHin()!=null?URLEncoder.encode(demographic.getHin(), StandardCharsets.UTF_8):""%> <%=demographic.getVer()!=null?URLEncoder.encode(demographic.getVer(), StandardCharsets.UTF_8):""%>&dob=<%=MyDateFormat.getStandardDate(Integer.parseInt(birthYear),Integer.parseInt(birthMonth),Integer.parseInt(birthDate))%>','', 'scrollbars=yes,resizable=yes,width=600,height=400');return false;"
                                        title='<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgAddBatchBilling"/>'><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgAddBatchBilling"/></a>
                                </td>
                            </tr>
                            <tr>
                                <td><a
                                        href="javascript: function myFunction() {return false; }"
                                        onClick="window.open('<%= request.getContextPath() %>/billing/CA/ON/inr/addINRbilling.jsp?function=demographic&functionid=<%=demographic.getDemographicNo()%>&creator=<%=curProvider_no%>&demographic_name=<%=URLEncoder.encode(demographic.getLastName(), StandardCharsets.UTF_8)%>%2C<%=URLEncoder.encode(demographic.getFirstName(), StandardCharsets.UTF_8)%>&hin=<%=demographic.getHin()!=null?URLEncoder.encode(demographic.getHin(), StandardCharsets.UTF_8):""%> <%=demographic.getVer()!=null?URLEncoder.encode(demographic.getVer(), StandardCharsets.UTF_8):""%>&dob=<%=MyDateFormat.getStandardDate(Integer.parseInt(birthYear),Integer.parseInt(birthMonth),Integer.parseInt(birthDate))%>','', 'scrollbars=yes,resizable=yes,width=600,height=400');return false;"
                                        title='<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgAddINRBilling"/>'><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgAddINR"/></a>
                                </td>
                            </tr>
                            <tr>
                                <td><a
                                        href="javascript: function myFunction() {return false; }"
                                        onClick="window.open('<%= request.getContextPath() %>/billing/CA/ON/inr/reportINR.jsp?provider_no=<%=curProvider_no%>','', 'scrollbars=yes,resizable=yes,width=600,height=600');return false;"
                                        title='<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgINRBilling"/>'><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgINRBill"/></a>
                                </td>
                            </tr>
                            <%
                                }
                            %>

                        </security:oscarSec>
                        <tr class="Header">
                            <td style="font-weight: bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.clinicalModules"/></td>
                        </tr>
                        <tr>
                            <td><a
                                    href="javascript: function myFunction() {return false; }"
                                    onClick="popupPage(700,960,'<%= request.getContextPath() %>/oscarEncounter/oscarConsultationRequest/DisplayDemographicConsultationRequests.jsp?de=<%=demographic.getDemographicNo()%>&proNo=<%=demographic.getProviderNo()%>')"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnConsultation"/></a></td>
                        </tr>

                        <tr>
                            <td><a
                                    href="javascript: function myFunction() {return false; }"
                                    onClick="popupOscarRx(700,1027,'<%= request.getContextPath() %>/oscarRx/choosePatient.do?providerNo=<%=curProvider_no%>&demographicNo=<%=demographic_no%>')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.prescriptions"/></a>
                            </td>
                        </tr>

                        <security:oscarSec roleName="<%=roleName$%>" objectName="_eChart"
                                           rights="r" reverse="<%=false%>">
                                <tr>
                                    <td>
                                        <a href="javascript: function myFunction() {return false; }"
                                           onClick="popupEChart(710, 1024,encURL);return false;"
                                           title="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnEChart"/>">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnEChart"/></a>&nbsp;<a
                                            style="text-decoration: none;"
                                            href="javascript: function myFunction() {return false; }"
                                            onmouseover="return !showMenu('1', event);">+</a>
                                        <div id='menu1' class='menu' onclick='event.cancelBubble = true;'>
                                            <h3 style='text-align: center'><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgEncType"/></h3>
                                            <br>
                                            <ul>
                                                <li><a href="#" onmouseover='this.style.color="black"'
                                                       onmouseout='this.style.color="white"'
                                                       onclick="return add2url('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.faceToFaceEnc.title"/>');"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.faceToFaceEnc.title"/>
                                                </a><br/>
                                                </li>
                                                <li><a href="#" onmouseover='this.style.color="black"'
                                                       onmouseout='this.style.color="white"'
                                                       onclick="return add2url('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.telephoneEnc.title"/>');"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.telephoneEnc.title"/>
                                                </a><br/>
                                                </li>
                                                <li><a href="#" onmouseover='this.style.color="black"'
                                                       onmouseout='this.style.color="white"'
                                                       onclick="return add2url('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.noClientEnc.title"/>');"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.noClientEnc.title"/>
                                                </a><br/>
                                                </li>
                                                <li><a href="#" onmouseover='this.style.color="black"'
                                                       onmouseout='this.style.color="white"'
                                                       onclick="return customReason();"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgCustom"/></a>
                                                </li>
                                                <li id="listCustom" style="display: none;"><input id="txtCustom"
                                                                                                  type="text" size="16"
                                                                                                  maxlength="32"
                                                                                                  onkeypress="return grabEnterCustomReason(event);">
                                                </li>
                                            </ul>
                                        </div>
                                    </td>
                                </tr>
                            <tr>
                                <td><a
                                        href="javascript: function myFunction() {return false; }"
                                        onClick="popupPage(700,960,'<c:out
                                                value="${ctx}"/>/oscarPrevention/index.jsp?demographic_no=<%=demographic_no%>');return false;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.LeftNavBar.Prevent"/></a></td>
                            </tr>
                        </security:oscarSec>
                        <tr>
                            <td>
                                <a
                                        href="javascript: function myFunction() {return false; }"
                                        onClick="popupPage(700,1000,'<%= request.getContextPath() %>/tickler/ticklerMain.jsp?demoview=<%=demographic_no%>');return false;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="global.tickler"/></a>
                            </td>
                        </tr>
                        <tr>
                            <td><a
                                    href="javascript: function myFunction() {return false; }"
                                    onClick="popup(700,960,'<%= request.getContextPath() %>/messenger/SendDemoMessage.do?demographic_no=<%=demographic.getDemographicNo()%>','msg')">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgSendMsg"/></a></td>
                        </tr>
                        <tr>
                            <td><a href="#"
                                   onclick="popup(300,300,'<%=request.getContextPath()%>/demographic/demographicCohort.jsp?demographic_no=<%=demographic.getDemographicNo()%>', 'cohort'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgAddPatientSet"/></a>
                            </td>
                        </tr>

                        <%
                            if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
                        %>
                        <tr>
                            <td><a href="#"
                                   onclick="popup(500,500,'<%= request.getContextPath() %>/integrator/manage_linked_clients.jsp?demographicId=<%=demographic.getDemographicNo()%>', 'manage_linked_clients'); return false;">Integrator
                                Linking</a>
                            </td>
                        </tr>
                        <% } %>

                        <% if (oscarProps.getProperty("clinic_no", "").startsWith("1022")) { // quick hack to make Dr. Hunter happy
                        %>
                        <tr>
                            <td><a
                                    href="javascript: function myFunction() {return false; }"
                                    onClick="popupPage(700,1000,'<%=request.getContextPath()%>/form/forwardshortcutname.do?formname=AR1&demographic_no=<%=request.getParameter("demographic_no")%>');">AR1</a>
                            </td>
                        </tr>
                        <tr>
                            <td><a
                                    href="javascript: function myFunction() {return false; }"
                                    onClick="popupPage(700,1000,'<%=request.getContextPath()%>/form/forwardshortcutname.do?formname=AR2&demographic_no=<%=request.getParameter("demographic_no")%>');">AR2</a>
                            </td>
                        </tr>
                        <% } %>
                        <tr class="Header">
                            <td style="font-weight: bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.clinicalResources"/></td>
                        </tr>
                        <special:SpecialPlugin moduleName="inboxmnger">
                            <tr>
                                <td>

                                    <a href="#"
                                       onClick="window.open('<%=request.getContextPath()%>/mod/docmgmtComp/DocList.do?method=list&&demographic_no=<%=demographic_no %>','_blank','resizable=yes,status=yes,scrollbars=yes');return false;">Inbox
                                        Manager</a><br>
                                </td>
                            </tr>
                        </special:SpecialPlugin>
                        <special:SpecialPlugin moduleName="inboxmnger" reverse="true">
                            <tr>
                                <td>
                                    <a href="javascript: function myFunction() {return false; }"
                                       onClick="popupPage(710,970,'<%= request.getContextPath() %>/documentManager/documentReport.jsp?function=demographic&doctype=lab&functionid=<%=demographic.getDemographicNo()%>&curUser=<%=curProvider_no%>')"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDocuments"/></a></td>
                            </tr>
                            <%
                                UserProperty upDocumentBrowserLink = pref.getProp(curProvider_no, UserProperty.EDOC_BROWSER_IN_MASTER_FILE);
                                if (upDocumentBrowserLink != null && upDocumentBrowserLink.getValue() != null && upDocumentBrowserLink.getValue().equals("yes")) {%>
                            <tr>
                                <td>
                                    <a href="javascript: function myFunction() {return false; }"
                                       onClick="popupPage(710,970,'<%= request.getContextPath() %>/documentManager/documentBrowser.jsp?function=demographic&doctype=lab&functionid=<%=demographic.getDemographicNo()%>&categorykey=Private Documents')"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDocumentBrowser"/></a></td>
                            </tr>
                            <%}%>
                            <tr>
                                <td><a
                                        href="javascript: function myFunction() {return false; }"
                                        onClick="popupPage(710,970,'<%= request.getContextPath() %>/documentManager/documentReport.jsp?function=demographic&doctype=lab&functionid=<%=demographic.getDemographicNo()%>&curUser=<%=curProvider_no%>&mode=add')"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnAddDocument"/></a></td>
                            </tr>
                        </special:SpecialPlugin>
                        <tr>
                            <td><a
                                    href="<%= request.getContextPath() %>/eform/efmpatientformlist.jsp?demographic_no=<%=demographic_no%>&apptProvider=<%=apptProvider%>&appointment=<%=appointment%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnEForm"/></a></td>
                        </tr>
                        <tr>
                            <td><a
                                    href="<%= request.getContextPath() %>/eform/efmformslistadd.jsp?demographic_no=<%=demographic_no%>&appointment=<%=appointment%>">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnAddEForm"/> </a></td>
                        </tr>

                    </table>
                </td>
                <td class="MainTableRightColumn" valign="top">
                    <!-- A list used in the mobile version for users to pick which information they'd like to see -->
                    <div id="mobileDetailSections" style="display:<%=(isMobileOptimized)?"block":"none"%>;">
                        <ul class="wideList">
                            <% if (!"".equals(alert)) { %>
                            <li><a style="color:brown"
                                   onClick="showHideMobileSections(new Array('alert'))"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formAlert"/></a></li>
                            <% } %>
                            <li><a onClick="showHideMobileSections(new Array('demographic'))"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDemographic"/></a></li>
                            <li><a onClick="showHideMobileSections(new Array('contactInformation'))"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgContactInfo"/></a></li>
                            <li><a onClick="showHideMobileSections(new Array('otherContacts'))"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgOtherContacts"/></a></li>
                            <li><a onClick="showHideMobileSections(new Array('healthInsurance'))"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgHealthIns"/></a></li>
                            <li>
                                <a onClick="showHideMobileSections(new Array('patientClinicStatus','clinicStatus'))"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgClinicStatus"/></a></li>
                            <li><a onClick="showHideMobileSections(new Array('notes'))"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formNotes"/></a></li>
                        </ul>
                    </div>
                    <table border=0 width="100%">
                        <tr id="searchTable">
                            <td>
                                <jsp:include page="zdemographicfulltitlesearch.jsp"/>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <form method="post" name="updatedelete" id="updatedelete"
                                      action="demographic/demographiccontrol.jsp"
                                      onSubmit="return checkTypeInEdit();" autocomplete="off">
                                    <input type="hidden" name="demographic_no"
                                           value="<%=demographic.getDemographicNo()%>">
                                    <table width="100%" class="demographicDetail">
                                        <tr>
                                            <td>
                                                <%
                                                    DemographicMerged dmDAO = new DemographicMerged();
                                                    String dboperation = "search_detail";
                                                    String head = dmDAO.getHead(demographic_no);
                                                    ArrayList records = dmDAO.getTail(head);

                                                %><a
                                                    href="<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=<%= head %>&displaymode=edit&dboperation=<%= dboperation %>"><%=head%>
                                            </a>
                                                <%

                                                    for (int i = 0; i < records.size(); i++) {
                                                        if (((String) records.get(i)).equals(demographic_no)) {
                                                %><%=", " + demographic_no %>
                                                <%
                                                } else {
                                                %>, <a
                                                    href="<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=<%= records.get(i) %>&displaymode=edit&dboperation=<%= dboperation %>"><%=records.get(i)%>
                                            </a>
                                                <%
                                                        }
                                                    }
                                                %> )

                                                <security:oscarSec roleName="<%=roleName$%>" objectName="_demographic"
                                                                   rights="w">
                                                    <%
                                                        if (head.equals(demographic_no)) {
                                                    %>
                                                    <a id="editBtn" href="javascript: showHideDetail();"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgEdit"/></a>
                                                    <a id="closeBtn" href="javascript: showHideDetail();"
                                                       style="display:none;">Close</a>
                                                    <% } %>
                                                </security:oscarSec>
                                            </td>
                                        </tr>
                                        <%
                                            String printEnvelope, printLbl, printAddressLbl, printChartLbl, printSexHealthLbl, printHtmlLbl, printLabLbl;
                                            printEnvelope = printLbl = printAddressLbl = printChartLbl = printSexHealthLbl = printHtmlLbl = printLabLbl = null;

                                            String rootContextPath = request.getContextPath();
                                            String demoPath = rootContextPath + "/demographic/";

                                            if (oscarProps.getProperty("new_label_print") != null && oscarProps.getProperty("new_label_print").equals("true")) {
                                                printEnvelope = demoPath + "printEnvelope.jsp?demos=";
                                                printLbl = demoPath + "printDemoLabel.jsp?demographic_no=";
                                                printAddressLbl = demoPath + "printAddressLabel.jsp?demographic_no=";
                                                printChartLbl = demoPath + "printDemoChartLabel.jsp?demographic_no=";
                                                printSexHealthLbl = demoPath + "printDemoChartLabel.jsp?labelName=SexualHealthClinicLabel&demographic_no=";
                                                printHtmlLbl = demoPath + "demographiclabelprintsetting.jsp?demographic_no=";
                                                printLabLbl = demoPath + "printClientLabLabel.jsp?demographic_no=";
                                            } else {
                                                printEnvelope = rootContextPath + "/report/GenerateEnvelopes.do?demos=";
                                                printLbl = demoPath + "printDemoLabelAction.do?demographic_no=";
                                                printAddressLbl = demoPath + "printDemoAddressLabelAction.do?demographic_no=";
                                                printChartLbl = demoPath + "printDemoChartLabelAction.do?demographic_no=";
                                                printSexHealthLbl = demoPath + "printDemoChartLabelAction.do?labelName=SexualHealthClinicLabel&demographic_no=";
                                                printHtmlLbl = demoPath + "demographiclabelprintsetting.jsp?demographic_no=";
                                                printLabLbl = demoPath + "printClientLabLabelAction.do?demographic_no=";
                                            }

                                        %>
                                        <%if (oscarProps.getProperty("workflow_enhance") != null && oscarProps.getProperty("workflow_enhance").equals("true")) {%>

                                        <tr bgcolor="#CCCCFF">
                                            <td>
                                                <table border="0" width="100%" cellpadding="0" cellspacing="0">
                                                    <tr>
                                                        <td width="30%" valign="top">

                                                            <input type="hidden" name="displaymode"
                                                                   value="Update Record">

                                                            <input type="hidden" name="dboperation"
                                                                   value="update_record">

                                                            <security:oscarSec roleName="<%=roleName$%>"
                                                                               objectName="_demographicExport"
                                                                               rights="r" reverse="<%=false%>">
                                                                <input type="button"
                                                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgExport"/>"
                                                                       onclick="window.open('<%= request.getContextPath() %>/demographic/demographicExport.jsp?demographicNo=<%=demographic.getDemographicNo()%>');">
                                                            </security:oscarSec>
                                                        </td>
                                                        <td width="30%" align='center' valign="top">
                                                            <% if (oscarProps.getBooleanProperty("workflow_enhance", "true")) { %>
                                                            <span style="position: relative; float: right; font-style: italic; background: black; color: white; padding: 4px; font-size: 12px; border-radius: 3px;">
										<span class="_hc_status_icon _hc_status_success"></span>Ready for Card Swipe
									</span>
                                                            <% } %>
                                                            <% if (!oscarProps.getBooleanProperty("workflow_enhance", "true")) { %>
                                                            <span id="swipeButton" style="display: inline;">
                                    <input type="button" name="Button"
                                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnSwipeCard"/>"
                                           onclick="window.open('demographic/zdemographicswipe.jsp','', 'scrollbars=yes,resizable=yes,width=600,height=300, top=360, left=0')">
                                </span>
                                                            <!--input type="button" name="Button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnSwipeCard"/>" onclick="javascript:window.alert('Health Card Number Already Inuse');"-->
                                                            <% } %>
                                                        </td>
                                                        <td width="40%" align='right' valign="top">
                                                            <input type="button" size="110" name="Button"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePDFEnvelope"/>"
                                                                   onclick="popupPage(400,700,'<%=printEnvelope%><%=demographic.getDemographicNo()%>');return false;">
                                                            <input type="button" size="110" name="Button"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePDFLabel"/>"
                                                                   onclick="popupPage(400,700,'<%=printLbl%><%=demographic.getDemographicNo()%>');return false;">
                                                            <input type="button" size="110" name="Button"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePDFAddressLabel"/>"
                                                                   onclick="popupPage(400,700,'<%=printAddressLbl%><%=demographic.getDemographicNo()%>');return false;">
                                                            <input type="button" size="110" name="Button"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePDFChartLabel"/>"
                                                                   onclick="popupPage(400,700,'<%=printChartLbl%><%=demographic.getDemographicNo()%>');return false;">
                                                            <%
                                                                if (oscarProps.getProperty("showSexualHealthLabel", "false").equals("true")) {
                                                            %>
                                                            <input type="button" size="110" name="Button"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePublicHealthLabel"/>"
                                                                   onclick="popupPage(400,700,'<%=printSexHealthLbl%><%=demographic.getDemographicNo()%>');return false;">
                                                            <% } %>
                                                            <input type="button" name="Button" size="110"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnPrintLabel"/>"
                                                                   onclick="popupPage(600,800,'<%=printHtmlLbl%><%=demographic.getDemographicNo()%>');return false;">
                                                            <input type="button" size="110" name="Button"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnClientLabLabel"/>"
                                                                   onclick="popupPage(400,700,'<%=printLabLbl%><%=demographic.getDemographicNo()%>');return false;">
                                                        </td>
                                                    </tr>
                                                </table>
                                            </td>
                                        </tr>


                                        <%} %>

                                        <tr>
                                            <td class="lightPurple"><!---new-->
                                                <div style="display: block;" id="viewDemographics2">
                                                    <div class="demographicWrapper">
                                                        <div class="leftSection">
                                                            <div class="demographicSection" id="demographic">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDemographic"/></h3>
                                                                <%
                                                                    for (String key : demoExt.keySet()) {
                                                                        if (key.endsWith("_id")) {
                                                                %>
                                                                <input type="hidden" name="<%= key %>"
                                                                       value="<%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demoExt.get(key)))%>"/>
                                                                <%
                                                                        }
                                                                    }
                                                                %>
                                                                <ul>

                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDemoTitle"/>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getTitle())%></span>
                                                                    </li>

                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formLastName"/>:</span>
                                                                        <span class="info"><%=Encode.forHtmlContent(demographic.getLastName())%></span>
                                                                    </li>
                                                                    <li><span class="label">
							<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formFirstName"/>:</span>
                                                                        <span class="info"><%=Encode.forHtmlContent(demographic.getFirstName())%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formMiddleNames"/>:</span>
                                                                        <span class="info"> <c:out
                                                                                value="<%=Encode.forHtmlContent(demographic.getMiddleNames())%>"/></span>
                                                                    </li>
                                                                    <li>
														<span class="label" style="color:red;"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNameUsed"/>:
														</span>
                                                                        <span class="info" style="color:red;">
															<c:out value="<%=Encode.forHtml(demographic.getAlias())%>"/>
														</span>
                                                                    </li>

                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPronouns"/>:</span>
                                                                        <span class="info"><%=Encode.forHtmlContent(StringUtils.trimToEmpty(demographic.getPronoun()))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formSex"/>:</span>
                                                                        <span class="info"><%=Gender.valueOf(demographic.getSex()).getText()%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formGender"/>:</span>
                                                                        <span class="info"><%=Encode.forHtmlContent(StringUtils.trimToEmpty(demographic.getGender()))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDemoAge"/>:</span>
                                                                        <span class="info"><%=demographic.getAgeAsOf(new Date())%>&nbsp;(
	                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formDOB"/>: <%=birthYear%>-<%=birthMonth%>-<%=birthDate%>)
                                                        </span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDemoLanguage"/>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getOfficialLanguage())%></span>
                                                                    </li>
                                                                    <% if (demographic.getCountryOfOrigin() != null && !demographic.getCountryOfOrigin().equals("") && !demographic.getCountryOfOrigin().equals("-1")) {
                                                                        CountryCode countryCode = ccDAO.getCountryCode(demographic.getCountryOfOrigin());
                                                                        if (countryCode != null) {
                                                                    %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgCountryOfOrigin"/>:</span>
                                                                        <span class="info"><%=countryCode.getCountryName() %></span>
                                                                    </li>
                                                                    <% }
                                                                    }
                                                                    %>
                                                                    <% String sp_lang = demographic.getSpokenLanguage();
                                                                        if (sp_lang != null && sp_lang.length() > 0) { %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgSpokenLang"/>:</span>
                                                                        <span class="info"><%=sp_lang%></span>
                                                                    </li>
                                                                    <% } %>

                                                                    <% String sin = demographic.getSin();
                                                                        if (sin != null && sin.length() > 0) { %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgSIN"/>:</span>
                                                                        <span class="info"><%=sin%></span>
                                                                    </li>
                                                                    <% } %>

                                                                    <oscar:oscarPropertiesCheck value="true"
                                                                                                defaultVal="false"
                                                                                                property="FIRST_NATIONS_MODULE">
                                                                        <li><span class="label">
	                           	First Nation:</span>
                                                                            <span class="info">
	                            	<c:out value='${ pageScope.demoExtended["aboriginal"] }'/>
	                            </span>
                                                                        </li>
                                                                        <li>
                                                                            <span class="label">Status Number:</span>
                                                                            <span class="info"><c:out
                                                                                    value='${ pageScope.demoExtended["statusNum"] }'/></span>
                                                                        </li>
                                                                        <li>
                                                                            <span class="label">First Nation Community:</span>
                                                                            <span class="info"><c:out
                                                                                    value='${ fncommunity }'/></span>
                                                                        </li>
                                                                    </oscar:oscarPropertiesCheck>

                                                                    <% if (oscarProps.getProperty("EXTRA_DEMO_FIELDS") != null) {
                                                                        String fieldJSP = oscarProps.getProperty("EXTRA_DEMO_FIELDS");
                                                                        fieldJSP += "View.jsp";
                                                                    %>
                                                                    <jsp:include page="<%=fieldJSP%>">
                                                                        <jsp:param name="demo"
                                                                                   value="<%=demographic_no%>"/>
                                                                    </jsp:include>
                                                                    <%}%>

                                                                </ul>
                                                            </div>

                                                                <%-- TOGGLE NEW CONTACTS UI --%>
                                                            <%if (!oscarProps.isPropertyActive("NEW_CONTACTS_UI")) { %>

                                                            <div class="demographicSection" id="otherContacts">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgOtherContacts"/>:
                                                                    <b><a
                                                                            href="javascript: function myFunction() {return false; }"
                                                                            onClick="popup(700,960,'<%= request.getContextPath() %>/demographic/AddAlternateContact.jsp?demo=<%=demographic.getDemographicNo()%>','AddRelation')">
                                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgAddRelation"/><!--i18n--></a></b>
                                                                </h3>
                                                                <ul>
                                                                    <%
                                                                        DemographicRelationship demoRelation = new DemographicRelationship();
                                                                        List relList = demoRelation.getDemographicRelationshipsWithNamePhone(loggedInInfo, demographic.getDemographicNo().toString(), loggedInInfo.getCurrentFacility().getId());
                                                                        for (int reCounter = 0; reCounter < relList.size(); reCounter++) {
                                                                            HashMap relHash = (HashMap) relList.get(reCounter);
                                                                            String dNo = (String) relHash.get("demographicNo");
                                                                            String workPhone = demographicManager.getDemographicWorkPhoneAndExtension(loggedInInfo, Integer.valueOf(dNo));
                                                                            String cellPhone = demographicExtDao.getValueForDemoKey(Integer.valueOf(dNo), "demo_cell");

                                                                            String formattedWorkPhone = (workPhone != null && workPhone.length() > 0 && !workPhone.equals("null")) ? "  W:" + workPhone : "";
                                                                            String formattedCellPhone = (cellPhone != null && cellPhone.length() > 0 && !cellPhone.equals("null")) ? "  C:" + cellPhone : "";
                                                                            String sdb = relHash.get("subDecisionMaker") == null ? "" : ((Boolean) relHash.get("subDecisionMaker")).booleanValue() ? "<span title=\"SDM\" >/SDM</span>" : "";
                                                                            String ec = relHash.get("emergencyContact") == null ? "" : ((Boolean) relHash.get("emergencyContact")).booleanValue() ? "<span title=\"Emergency Contact\">/EC</span>" : "";
                                                                            String masterLink = "<a target=\"demographic" + dNo + "\" href=\"" + request.getContextPath() + "/demographic/demographiccontrol.jsp?demographic_no=" + dNo + "&displaymode=edit&dboperation=search_detail\">M</a>";
                                                                            String encounterLink = "<a target=\"encounter" + dNo + "\" href=\"javascript: function myFunction() {return false; }\" onClick=\"popupEChart(710,1024,'" + request.getContextPath() + "/oscarEncounter/IncomingEncounter.do?demographicNo=" + dNo + "&providerNo=" + loggedInInfo.getLoggedInProviderNo() + "&appointmentNo=&curProviderNo=&reason=&appointmentDate=&startTime=&status=&userName=" + URLEncoder.encode(userfirstname + " " + userlastname, StandardCharsets.UTF_8) + "&curDate=" + dateString + "');return false;\">E</a>";
                                                                    %>
                                                                    <li><span
                                                                            class="label"><%=relHash.get("relation")%><%=sdb%><%=ec%>:</span>
                                                                        <span class="info"><%=relHash.get("lastName")%>, <%=relHash.get("firstName")%>, H:<%=relHash.get("phone") == null ? "" : relHash.get("phone")%><%=formattedWorkPhone%><%=formattedCellPhone%> <%=masterLink%> <%=encounterLink %></span>
                                                                    </li>
                                                                    <%}%>

                                                                </ul>
                                                            </div>

                                                            <% } else { %>

                                                            <div class="demographicSection" id="otherContacts2">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgOtherContacts"/>:
                                                                    <b><a
                                                                            href="javascript: function myFunction() {return false; }"
                                                                            onClick="popup(700,960,'Contact.do?method=manage&demographic_no=<%=demographic.getDemographicNo()%>','ManageContacts')">
                                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgManageContacts"/><!--i18n--></a></b>
                                                                </h3>
                                                                <ul>
                                                                    <%
                                                                        ContactDao contactDao = (ContactDao) SpringUtils.getBean(ContactDao.class);
                                                                        DemographicContactDao dContactDao = (DemographicContactDao) SpringUtils.getBean(DemographicContactDao.class);
                                                                        List<DemographicContact> dContacts = dContactDao.findByDemographicNo(demographic.getDemographicNo());
                                                                        dContacts = Contact2Action.fillContactNames(dContacts);
                                                                        for (DemographicContact dContact : dContacts) {
                                                                            String sdm = (dContact.getSdm() != null && dContact.getSdm().equals("true")) ? "<span title=\"SDM\" >/SDM</span>" : "";
                                                                            String ec = (dContact.getEc() != null && dContact.getEc().equals("true")) ? "<span title=\"Emergency Contact\" >/EC</span>" : "";
                                                                            String masterLink = null;
                                                                            if (DemographicContact.CATEGORY_PERSONAL.equals(dContact.getCategory()) && DemographicContact.TYPE_DEMOGRAPHIC == dContact.getType()) {
                                                                                masterLink = "<a target=\"demographic" + dContact.getContactId() + "\" href=\"" + request.getContextPath() + "/demographic/demographiccontrol.jsp?demographic_no=" + dContact.getContactId() + "&displaymode=edit&dboperation=search_detail\">M</a>";
                                                                            }
                                                                            if (DemographicContact.CATEGORY_PERSONAL.equals(dContact.getCategory()) && DemographicContact.TYPE_CONTACT == dContact.getType()) {
                                                                                masterLink = "<a target=\"_blank\" href=\"" + request.getContextPath() + "/demographic/Contact.do?method=viewContact&contact.id=" + dContact.getContactId() + "\">details</a>";
                                                                            }
                                                                    %>

                                                                    <li><span
                                                                            class="label"><%=dContact.getRole()%>:</span>
                                                                        <span class="info"><%=dContact.getContactName() %><%=sdm%><%=ec%> <%=masterLink != null ? masterLink : "" %></span>
                                                                    </li>

                                                                    <%} %>

                                                                </ul>
                                                            </div>

                                                            <% } %>
                                                            <div class="demographicSection" id="clinicStatus">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgClinicStatus"/>
                                                                    (<a href="#"
                                                                        onclick="popup(1000, 650, '<%= request.getContextPath() %>/demographic/EnrollmentHistory.jsp?demographicNo=<%=demographic_no%>', 'enrollmentHistory'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgEnrollmentHistory"/></a>)
                                                                </h3>
                                                                <ul>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formRosterStatus"/>:</span>
                                                                        <span class="info"><%=demographic.getRosterStatusDisplay()%></span>
                                                                    </li>
                                                                    <%if ("RO".equals(demographic.getRosterStatus()) || "TE".equals(demographic.getRosterStatus())) { %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.DateJoined"/>:</span>
                                                                        <span class="info"><%=MyDateFormat.getMyStandardDate(demographic.getRosterDate())%></span>
                                                                    </li>
                                                                    <% } %>
                                                                    <%if ("RO".equals(demographic.getRosterStatus())) { %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.RosterEnrolledTo"/>:</span>
                                                                        <span class="info">
                                                        <%
                                                            String enrolledTo = "";
                                                            if (demographic.getRosterEnrolledTo() != null) {
                                                                Provider enrolledToProvider = providerDao.getProvider(demographic.getRosterEnrolledTo());
                                                                if (enrolledToProvider != null) {
                                                                    enrolledTo = enrolledToProvider.getFormattedName();
                                                                }
                                                            }
                                                        %>
                                                        <%=enrolledTo %>
                                                        </span>
                                                                    </li>
                                                                    <% } %>
                                                                    <%if ("TE".equals(demographic.getRosterStatus())) { %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.RosterTerminationDate"/>:</span>
                                                                        <span class="info"><%=MyDateFormat.getMyStandardDate(demographic.getRosterTerminationDate())%></span>
                                                                    </li>
                                                                    <%if (null != demographic.getRosterTerminationDate()) { %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.RosterTerminationReason"/>:</span>
                                                                        <span class="info"><%=Util.rosterTermReasonProperties.getReasonByCode(demographic.getRosterTerminationReason()) %></span>
                                                                    </li>
                                                                    <%
                                                                            }
                                                                        }
                                                                    %>

                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPatientStatus"/>:</span>
                                                                        <span class="info">
							<%
                                String PatStat = demographic.getPatientStatus();
                                String Dead = "DE";
                                String Inactive = "IN";

                                if (Dead.equals(PatStat)) {%>
							<b style="color: #FF0000;"><%=demographic.getPatientStatus()%></b>
							<%} else if (Inactive.equals(PatStat)) {%>
							<b style="color: #0000FF;"><%=demographic.getPatientStatus()%></b>
							<%} else {%>
                                                            <%=demographic.getPatientStatus()%>
							<%}%>
                                                        </span>
                                                                    </li>
                                                                    <li><span class="label">
							 	<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.PatientStatusDate"/>:</span>
                                                                        <span class="info">
                                <%
                                    String tmpDate = "";
                                    if (demographic.getPatientStatusDate() != null) {
                                        tmpDate = org.apache.commons.lang3.time.DateFormatUtils.ISO_DATE_FORMAT.format(demographic.getPatientStatusDate());
                                    }
                                %>
                                <%=tmpDate%></span>
                                                                    </li>

                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formChartNo"/>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getChartNo())%></span>
                                                                    </li>
                                                                    <% if (oscarProps.isPropertyActive("meditech_id")) { %>
                                                                    <li><span class="label">Meditech ID:</span>
                                                                        <span class="info"><%=OtherIdManager.getDemoOtherId(demographic_no, "meditech_id")%></span>
                                                                    </li>
                                                                    <% } %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.cytolNum"/>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demoExt.get("cytolNum"))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formDateJoined1"/>:</span>
                                                                        <span class="info"><%=MyDateFormat.getMyStandardDate(demographic.getDateJoined())%></span>
                                                                    </li>
                                                                    <li>
                                                        <span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formEndDate"/>:</span>
                                                                        <span class="info"><%=MyDateFormat.getMyStandardDate(demographic.getEndDate())%></span>
                                                                    </li>

                                                                    <%if (!"true".equals(OscarProperties.getInstance().getProperty("phu.hide", "false"))) { %>
                                                                    <li><span class="label">
								<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPHU"/>:</span>
                                                                        <span class="info"><c:out
                                                                                value="${phuName}"/></span>
                                                                    </li>
                                                                    <%} %>

                                                                </ul>
                                                            </div>

                                                            <div class="demographicSection" id="alert">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formAlert"/></h3>
                                                                <b style="color: brown;"><%=alert%>
                                                                </b>
                                                                &nbsp;
                                                            </div>

                                                            <div class="demographicSection"
                                                                 id="rxInteractionWarningLevelDisplay">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.rxInteractionWarningLevel"/></h3>
                                                                <%
                                                                    String warningLevel = demoExt.get("rxInteractionWarningLevel");
                                                                    if (warningLevel == null) warningLevel = "0";
                                                                    String warningLevelStr = "Not Specified";
                                                                    if (warningLevel.equals("1")) {
                                                                        warningLevelStr = "Low";
                                                                    }
                                                                    if (warningLevel.equals("2")) {
                                                                        warningLevelStr = "Medium";
                                                                    }
                                                                    if (warningLevel.equals("3")) {
                                                                        warningLevelStr = "High";
                                                                    }
                                                                    if (warningLevel.equals("4")) {
                                                                        warningLevelStr = "None";
                                                                    }
                                                                %>
                                                                <span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.rxInteractionWarningLevel"/>:</span>
                                                                <span class="info"><%=warningLevelStr%></span>


                                                            </div>

                                                            <div class="demographicSection" id="paperChartIndicator">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.paperChartIndicator"/></h3>
                                                                <%
                                                                    String archived = demoExt.get("paper_chart_archived");
                                                                    String archivedStr = "", archivedDate = "", archivedProgram = "";
                                                                    if ("YES".equals(archived)) {
                                                                        archivedStr = "Yes";
                                                                    }
                                                                    if ("NO".equals(archived)) {
                                                                        archivedStr = "No";
                                                                    }
                                                                    if (demoExt.get("paper_chart_archived_date") != null) {
                                                                        archivedDate = demoExt.get("paper_chart_archived_date");
                                                                    }
                                                                    if (demoExt.get("paper_chart_archived_program") != null) {
                                                                        archivedProgram = demoExt.get("paper_chart_archived_program");
                                                                    }
                                                                %>
                                                                <ul>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.paperChartIndicator.archived"/>:</span>
                                                                        <span class="info"><%=archivedStr %></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.paperChartIndicator.dateArchived"/>:</span>
                                                                        <span class="info"><%=archivedDate %></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.paperChartIndicator.programArchived"/>:</span>
                                                                        <span class="info"><%=archivedProgram %></span>
                                                                    </li>
                                                                </ul>
                                                            </div>

                                                                <%-- TOGGLE PRIVACY CONSENTS --%>
                                                            <oscar:oscarPropertiesCheck property="privateConsentEnabled"
                                                                                        value="true">

                                                                <div class="demographicSection" id="consent">
                                                                    <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.consent"/></h3>

                                                                    <ul>

                                                                        <%
                                                                            String[] privateConsentPrograms = oscarProps.getProperty("privateConsentPrograms", "").split(",");
                                                                            ProgramProvider pp = programManager2.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());

                                                                            if (pp != null) {
                                                                                for (int x = 0; x < privateConsentPrograms.length; x++) {
                                                                                    if (privateConsentPrograms[x].equals(pp.getProgramId().toString())) {
                                                                                        showConsentsThisTime = true;
                                                                                    }
                                                                                }
                                                                            }

                                                                            if (showConsentsThisTime) { %>

                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.privacyConsent"/>:</span>
                                                                            <span class="info"><%=privacyConsent %></span>
                                                                        </li>
                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.informedConsent"/>:</span>
                                                                            <span class="info"><%=informedConsent %></span>
                                                                        </li>
                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.usConsent"/>:</span>
                                                                            <span class="info"><%=usSigned %></span>
                                                                        </li>


                                                                        <% } %>

                                                                            <%-- ENABLE THE NEW PATIENT CONSENT MODULE --%>
                                                                        <oscar:oscarPropertiesCheck
                                                                                property="USE_NEW_PATIENT_CONSENT_MODULE"
                                                                                value="true">

                                                                            <c:forEach items="${ patientConsents }"
                                                                                       var="patientConsent">
                                                                                <c:if test="${ not empty patientConsent.optout}">
                                                                                    <li>
                                                                                        <c:if test="${ patientConsent.consentType.active }">
                          			<span class="popup label"
                                          onmouseover="nhpup.popup(${ patientConsent.consentType.description },{'width':350} );">
										<c:out value="${ patientConsent.consentType.name }"/>
									</span>

                                                                                            <c:choose>
                                                                                                <c:when test="${ patientConsent.optout }">
                                                                                                    <span class="info"
                                                                                                          style="color:red;"> Opted Out:<c:out
                                                                                                            value="${ patientConsent.optoutDate }"/></span>
                                                                                                </c:when>

                                                                                                <c:otherwise>
                                                                                                    <span class="info"
                                                                                                          style="color:green;">Consented:<c:out
                                                                                                            value="${ patientConsent.consentDate }"/></span>
                                                                                                </c:otherwise>
                                                                                            </c:choose>

                                                                                        </c:if>
                                                                                    </li>
                                                                                </c:if>
                                                                            </c:forEach>
                                                                        </oscar:oscarPropertiesCheck>
                                                                            <%-- END ENABLE NEW PATIENT CONSENT MODULE --%>

                                                                    </ul>
                                                                </div>

                                                            </oscar:oscarPropertiesCheck>
                                                                <%-- END TOGGLE ALL PRIVACY CONSENTS --%>

                                                            <% // customized key + "Has Primary Care Physician" & "Employment Status"
                                                                String[] propDemoExt = new String[0];
                                                                String hasPrimaryCarePhysician = "N/A";
                                                                String employmentStatus = "N/A";

                                                                final String hasPrimary = "Has Primary Care Physician";
                                                                final String empStatus = "Employment Status";
                                                                boolean hasDemoExt = false, hasHasPrimary = false, hasEmpStatus = false;

                                                                String demographicExt = oscarProps.getProperty("demographicExt");
                                                                if (demographicExt != null && !demographicExt.trim().isEmpty()) {
                                                                    hasDemoExt = true;
                                                                    propDemoExt = demographicExt.split("\\|");
                                                                }
                                                                if (oscarProps.isPropertyActive("showPrimaryCarePhysicianCheck")) {
                                                                    hasHasPrimary = true;
                                                                    String key = hasPrimary.replace(" ", "");
                                                                    if (demoExt.get(key) != null && !demoExt.get(key).trim().isEmpty()) {
                                                                        hasPrimaryCarePhysician = demoExt.get(key);
                                                                    }
                                                                }
                                                                if (oscarProps.isPropertyActive("showEmploymentStatus")) {
                                                                    hasEmpStatus = true;
                                                                    String key = empStatus.replace(" ", "");
                                                                    if (demoExt.get(key) != null && !demoExt.get(key).trim().isEmpty()) {
                                                                        employmentStatus = demoExt.get(key);
                                                                    }
                                                                }

                                                                if (hasDemoExt || hasHasPrimary || hasEmpStatus) {
                                                            %>
                                                            <div id="special" class="demographicSection">
                                                                <h3>&nbsp;Custom</h3>
                                                                <ul>

                                                                    <%
                                                                        if (propDemoExt != null) {
                                                                            for (String propItem : propDemoExt) {
                                                                                String propValue = StringUtils.trimToEmpty(demoExt.get(propItem.replace(' ', '_')));
                                                                    %>
                                                                    <li>
                                                                        <%= Encode.forHtml(propItem + ": ") %>
                                                                        <strong><%=    Encode.forHtml(propValue) %>
                                                                        </strong>
                                                                    </li>
                                                                    <% }
                                                                    }

                                                                        if (hasHasPrimary) {
                                                                    %>
                                                                    <li>
                                                                        <%=hasPrimary%>:
                                                                        <strong><%=hasPrimaryCarePhysician%>
                                                                        </strong>
                                                                    </li>
                                                                    <% }
                                                                        if (hasEmpStatus) {
                                                                    %>
                                                                    <li>
                                                                        <%=empStatus%>: <strong><%=employmentStatus%>
                                                                    </strong>
                                                                    </li>
                                                                    <% }
                                                                    %>
                                                                </ul>
                                                            </div>
                                                            <%} %>

                                                        </div> <!-- end of left section -->

                                                        <div class="rightSection">
                                                            <div class="demographicSection" id="contactInformation">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgContactInfo"/></h3>
                                                                <ul>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPhoneH"/>(<span
                                                                            class="popup"
                                                                            onmouseover="nhpup.popup(homePhoneHistory);"
                                                                            title="Home phone History">History</span>):</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getPhone())%> <%=StringUtils.trimToEmpty(demoExt.get("hPhoneExt"))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPhoneW"/>(<span
                                                                            class="popup"
                                                                            onmouseover="nhpup.popup(workPhoneHistory);"
                                                                            title="Work phone History">History</span>):</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getPhone2())%> <%=StringUtils.trimToEmpty(demoExt.get("wPhoneExt"))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPhoneC"/>(<span
                                                                            class="popup"
                                                                            onmouseover="nhpup.popup(cellPhoneHistory);"
                                                                            title="cell phone History">History</span>):</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demoExt.get("demo_cell"))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPhoneComment"/>:</span>
                                                                        <span class="info"><%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demoExt.get("phoneComment")))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formAddr"/>(<span
                                                                            class="popup"
                                                                            onmouseover="nhpup.popup(addressHistory);"
                                                                            title="Address History">History</span>):</span>
                                                                        <span class="info"><%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demographic.getAddress()))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formCity"/>:</span>
                                                                        <span class="info"><%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demographic.getCity()))%></span>
                                                                    </li>
                                                                    <li><span class="label">
							<% if (oscarProps.getProperty("demographicLabelProvince") == null) { %>
							<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formProcvince"/> <% } else {
                                                                        out.print(oscarProps.getProperty("demographicLabelProvince"));
                                                                    } %>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(ISO36612.getInstance().translateCodeToHumanReadableString(demographic.getProvince()))%></span>
                                                                    </li>
                                                                    <li><span class="label">
							<% if (oscarProps.getProperty("demographicLabelPostal") == null) { %>
							<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPostal"/> <% } else {
                                                                        out.print(oscarProps.getProperty("demographicLabelPostal"));
                                                                    } %>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getPostal())%></span>
                                                                    </li>


                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialAddr"/>:</span>
                                                                        <span class="info"><%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demographic.getResidentialAddress()))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialCity"/>:</span>
                                                                        <span class="info"><%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demographic.getResidentialCity()))%></span>
                                                                    </li>
                                                                    <li><span class="label">
														<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialProvince"/>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(ISO36612.getInstance().translateCodeToHumanReadableString(demographic.getResidentialProvince()))%></span>
                                                                    </li>
                                                                    <li><span class="label">

							<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialPostal"/>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getResidentialPostal())%></span>
                                                                    </li>


                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formEmail"/>:</span>
                                                                        <span class="info"><%=demographic.getEmail() != null ? demographic.getEmail() : ""%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formNewsLetter"/>:</span>
                                                                        <span class="info"><%=demographic.getNewsletter() != null ? demographic.getNewsletter() : "Unknown"%></span>
                                                                    </li>
                                                                </ul>
                                                            </div>

                                                            <div class="demographicSection" id="healthInsurance">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgHealthIns"/></h3>
                                                                <ul>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formHin"/>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getHin())%>
							&nbsp; <%=StringUtils.trimToEmpty(demographic.getVer())%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formHCType"/>:</span>
                                                                        <span class="info"><%=demographic.getHcType() == null ? "" : demographic.getHcType() %></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formEFFDate"/>:</span>
                                                                        <span class="info"><%=MyDateFormat.getMyStandardDate(demographic.getEffDate())%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formHCRenewDate"/>:</span>
                                                                        <span class="info"><%=MyDateFormat.getMyStandardDate(demographic.getHcRenewDate())%></span>
                                                                    </li>
                                                                </ul>
                                                                    <%-- TOGGLE FIRST NATIONS MODULE --%>
                                                                <oscar:oscarPropertiesCheck value="true"
                                                                                            defaultVal="false"
                                                                                            property="FIRST_NATIONS_MODULE">

                                                                    <jsp:include page="./displayFirstNationsModule.jsp"
                                                                                 flush="false">
                                                                        <jsp:param name="demo"
                                                                                   value="<%= demographic_no %>"/>
                                                                        <jsp:param name="fncommunity"
                                                                                   value="${fncommunity}"/>
                                                                    </jsp:include>

                                                                </oscar:oscarPropertiesCheck>
                                                                    <%-- END TOGGLE FIRST NATIONS MODULE --%>
                                                            </div>

                                                                <%-- TOGGLE WORKFLOW_ENHANCE - SHOWS PATIENTS INTERNAL PROVIDERS AND RELATED SCHEDULE AVAIL --%>

                                                            <oscar:oscarPropertiesCheck value="true"
                                                                                        property="workflow_enhance">
                                                                <div class="demographicSection">
                                                                    <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgInternalProviders"/></h3>
                                                                    <div style="background-color: #EEEEFF;">
                                                                        <ul>
                                                                            <%! // ===== functions for quick appointment booking =====

                                                                                // convert hh:nn:ss format to elapsed minutes (from 00:00:00)
                                                                                int timeStrToMins(String timeStr) {
                                                                                    String[] temp = timeStr.split(":");
                                                                                    return Integer.parseInt(temp[0]) * 60 + Integer.parseInt(temp[1]);
                                                                                }
                                                                            %>
                                                                            <% // ===== quick appointment booking =====
                                                                                // database access object, data objects for looking things up


                                                                                String[] twoLetterDate = {"", "Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};

                                                                                // build templateMap, which maps template codes to their associated duration
                                                                                Map<String, String> templateMap = new HashMap<String, String>();
                                                                                for (ScheduleTemplateCode stc : scheduleTemplateCodeDao.findTemplateCodes()) {
                                                                                    templateMap.put(String.valueOf(stc.getCode()), stc.getDuration());
                                                                                }


                                                                                // build list of providers associated with this patient
                                                                                Map<String, Map<String, Map<String, String>>> provMap = new HashMap<String, Map<String, Map<String, String>>>();
                                                                                if (demographic != null) {
                                                                                    provMap.put("doctor", new HashMap<String, Map<String, String>>());
                                                                                    provMap.get("doctor").put("prov_no", new HashMap<String, String>());
                                                                                    provMap.get("doctor").get("prov_no").put("no", demographic.getProviderNo());
                                                                                }
                                                                                if (StringUtils.isNotEmpty(providerBean.getProperty(resident, ""))) {
                                                                                    provMap.put("prov1", new HashMap<String, Map<String, String>>());
                                                                                    provMap.get("prov1").put("prov_no", new HashMap<String, String>());
                                                                                    provMap.get("prov1").get("prov_no").put("no", resident);
                                                                                }
                                                                                if (StringUtils.isNotEmpty(providerBean.getProperty(midwife, ""))) {
                                                                                    provMap.put("prov2", new HashMap<String, Map<String, String>>());
                                                                                    provMap.get("prov2").put("prov_no", new HashMap<String, String>());
                                                                                    provMap.get("prov2").get("prov_no").put("no", midwife);
                                                                                }
                                                                                if (StringUtils.isNotEmpty(providerBean.getProperty(nurse, ""))) {
                                                                                    provMap.put("prov3", new HashMap<String, Map<String, String>>());
                                                                                    provMap.get("prov3").put("prov_no", new HashMap<String, String>());
                                                                                    provMap.get("prov3").get("prov_no").put("no", nurse);
                                                                                }

                                                                                // precompute all data for the providers associated with this patient
                                                                                for (String thisProv : provMap.keySet()) {

                                                                                    String thisProvNo = provMap.get(thisProv).get("prov_no").get("no");

                                                                                    // starting tomorrow, look for available appointment slots
                                                                                    Calendar qApptCal = new GregorianCalendar();
                                                                                    qApptCal.add(Calendar.DATE, 1);
                                                                                    int numDays = 0;
                                                                                    int maxLookahead = 90;

                                                                                    while ((numDays < 5) && (maxLookahead > 0)) {
                                                                                        int qApptYear = qApptCal.get(Calendar.YEAR);
                                                                                        int qApptMonth = (qApptCal.get(Calendar.MONTH) + 1);
                                                                                        int qApptDay = qApptCal.get(Calendar.DAY_OF_MONTH);
                                                                                        String qApptWkDay = twoLetterDate[qApptCal.get(Calendar.DAY_OF_WEEK)];
                                                                                        String qCurDate = qApptYear + "-" + qApptMonth + "-" + qApptDay;

                                                                                        // get timecode string template associated with this day, number of minutes each slot represents
                                                                                        ScheduleTemplateDao dao = SpringUtils.getBean(ScheduleTemplateDao.class);
                                                                                        List<Object> timecodeResult = dao.findTimeCodeByProviderNo2(thisProvNo, ConversionUtils.fromDateString(qCurDate));

                                                                                        // if theres a template on this day, continue
                                                                                        if (!timecodeResult.isEmpty()) {

                                                                                            String timecode = StringUtils.trimToEmpty(String.valueOf(timecodeResult.get(0)));

                                                                                            int timecodeInterval = 1440 / timecode.length();

                                                                                            // build schedArr, which has 1s where template slots are
                                                                                            int[] schedArr = new int[timecode.length()];
                                                                                            String schedChar;
                                                                                            for (int i = 0; i < timecode.length(); i++) {
                                                                                                schedChar = "" + timecode.charAt(i);
                                                                                                if (!schedChar.equals("_")) {
                                                                                                    if (templateMap.get("" + timecode.charAt(i)) != null) {
                                                                                                        schedArr[i] = 1;
                                                                                                    }
                                                                                                }
                                                                                            }

                                                                                            // get list of appointments on this day
                                                                                            int start_index, end_index;
                                                                                            OscarAppointmentDao apptDao = SpringUtils.getBean(OscarAppointmentDao.class);
                                                                                            // put 0s in schedArr where appointments are
                                                                                            for (Appointment appt : apptDao.findByProviderAndDayandNotStatuses(thisProvNo, ConversionUtils.fromDateString(qCurDate), new String[]{"N", "C"})) {
                                                                                                start_index = timeStrToMins(StringUtils.trimToEmpty(ConversionUtils.toTimeString(appt.getStartTime()))) / timecodeInterval;
                                                                                                end_index = timeStrToMins(StringUtils.trimToEmpty(ConversionUtils.toTimeString(appt.getEndTime()))) / timecodeInterval;

                                                                                                // very late appts may push us past the time range we care about
                                                                                                // trying to invalidate these times will lead to a ArrayIndexOutOfBoundsException
                                                                                                // fix this so we stay within the bounds of schedArr
                                                                                                if (end_index > (timecode.length() - 1)) {
                                                                                                    end_index = timecode.length() - 1;
                                                                                                }

                                                                                                // protect against the dual case as well
                                                                                                if (start_index < 0) {
                                                                                                    start_index = 0;
                                                                                                }

                                                                                                // handle appts of duration longer than template interval
                                                                                                for (int i = start_index; i <= end_index; i++) {
                                                                                                    schedArr[i] = 0;
                                                                                                }
                                                                                            }

                                                                                            // list slots that can act as start times for appointments of template specified length
                                                                                            boolean enoughRoom;
                                                                                            boolean validDay = false;
                                                                                            int templateDuration, startHour, startMin;
                                                                                            String startTimeStr, endTimeStr, sortDateStr;
                                                                                            String timecodeChar;
                                                                                            for (int i = 0; i < timecode.length(); i++) {
                                                                                                if (schedArr[i] == 1) {
                                                                                                    enoughRoom = true;
                                                                                                    timecodeChar = "" + timecode.charAt(i);
                                                                                                    templateDuration = Integer.parseInt(templateMap.get(timecodeChar));
                                                                                                    for (int n = 0; n < templateDuration / timecodeInterval; n++) {
                                                                                                        if (((i + n) < (schedArr.length - 1)) && (schedArr[i + n] != 1)) {
                                                                                                            enoughRoom = false;
                                                                                                        }
                                                                                                    }
                                                                                                    if (enoughRoom) {
                                                                                                        validDay = true;
                                                                                                        sortDateStr = qApptYear + "-" + String.format("%02d", qApptMonth) + "-" + String.format("%02d", qApptDay);
                                                                                                        if (!provMap.get(thisProv).containsKey(sortDateStr + "," + qApptWkDay + " " + qApptMonth + "-" + qApptDay)) {
                                                                                                            provMap.get(thisProv).put(sortDateStr + "," + qApptWkDay + " " + qApptMonth + "-" + qApptDay, new HashMap<String, String>());
                                                                                                        }
                                                                                                        startHour = i * timecodeInterval / 60;
                                                                                                        startMin = i * timecodeInterval % 60;
                                                                                                        startTimeStr = String.format("%02d", startHour) + ":" + String.format("%02d", startMin);
                                                                                                        endTimeStr = String.format("%02d", startHour) + ":" + String.format("%02d", startMin + timecodeInterval - 1);

                                                                                                        provMap.get(thisProv).get(sortDateStr + "," + qApptWkDay + " " + qApptMonth + "-" + qApptDay).put(startTimeStr + "," + timecodeChar, request.getContextPath() + "/appointment/addappointment.jsp?demographic_no=" + demographic.getDemographicNo() + "&name=" + URLEncoder.encode(demographic.getLastName() + "," + demographic.getFirstName(), "UTF-8") + "&provider_no=" + thisProvNo + "&bFirstDisp=true&year=" + qApptYear + "&month=" + qApptMonth + "&day=" + qApptDay + "&start_time=" + startTimeStr + "&end_time=" + endTimeStr + "&duration=" + templateDuration + "&search=true");
                                                                                                    }
                                                                                                }
                                                                                            }

                                                                                            if (validDay) {
                                                                                                numDays++;
                                                                                            }
                                                                                        }

                                                                                        // look at the next day
                                                                                        qApptCal.add(Calendar.DATE, 1);
                                                                                        maxLookahead--;
                                                                                    }
                                                                                }
                                                                            %>
                                                                            <% if (demographic.getProviderNo() != null) { %>
                                                                            <li>
                                                                                <% if (oscarProps.getProperty("demographicLabelDoctor") != null) {
                                                                                    out.print(oscarProps.getProperty("demographicLabelDoctor", ""));
                                                                                } else { %>
                                                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formDoctor"/>
                                                                                <% } %>:
                                                                                <b><%=providerBean.getProperty(demographic.getProviderNo(), "")%>
                                                                                </b>
                                                                                <% // ===== quick appointment booking for doctor =====
                                                                                    if (provMap.get("doctor") != null) {
                                                                                %><br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<%
                                                                                boolean firstBar = true;
                                                                                ArrayList<String> sortedDays = new ArrayList(provMap.get("doctor").keySet());
                                                                                Collections.sort(sortedDays);
                                                                                for (String thisDate : sortedDays) {
                                                                                    if (!thisDate.equals("prov_no")) {
                                                                                        if (!firstBar) {%>|<%
                                                                                }
                                                                                ;
                                                                                firstBar = false;
                                                                                String[] thisDateArr = thisDate.split(",");
                                                                                String thisDispDate = thisDateArr[1];
                                                                            %>
                                                                                <a style="text-decoration: none;"
                                                                                   href="#"
                                                                                   onclick="return !showAppt('_doctor_<%=thisDateArr[0]%>', event);"><b><%=thisDispDate%>
                                                                                </b></a>
                                                                                <div id='menu_doctor_<%=thisDateArr[0]%>'
                                                                                     class='menu'
                                                                                     onclick='event.cancelBubble = true;'>
                                                                                    <h3 style='text-align: center; color: black;'>
                                                                                        Available Appts.
                                                                                        (<%=thisDispDate%>)</h3>
                                                                                    <ul>
                                                                                        <%
                                                                                            ArrayList<String> sortedTimes = new ArrayList(provMap.get("doctor").get(thisDate).keySet());
                                                                                            Collections.sort(sortedTimes);
                                                                                            for (String thisTime : sortedTimes) {
                                                                                                String[] thisTimeArr = thisTime.split(",");
                                                                                        %>
                                                                                        <li>[<%=thisTimeArr[1]%>] <a
                                                                                                href="#"
                                                                                                onClick="popupPage(400,780,'<%=provMap.get("doctor").get(thisDate).get(thisTime) %>');return false;"><%= thisTimeArr[0] %>
                                                                                        </a></li>
                                                                                        <%
                                                                                            }
                                                                                        %></ul>
                                                                                </div>
                                                                                <% }
                                                                                }
                                                                                }
                                                                                %>
                                                                            </li>
                                                                            <% }
                                                                                if (StringUtils.isNotEmpty(providerBean.getProperty(resident, ""))) { %>
                                                                            <li>Alt. Provider 1:
                                                                                <b><%=providerBean.getProperty(resident, "")%>
                                                                                </b>
                                                                                <% // ===== quick appointment booking for prov1 =====
                                                                                    if (provMap.get("prov1") != null) {
                                                                                %><br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<%
                                                                                    boolean firstBar = true;
                                                                                    ArrayList<String> sortedDays = new ArrayList(provMap.get("prov1").keySet());
                                                                                    Collections.sort(sortedDays);
                                                                                    for (String thisDate : sortedDays) {
                                                                                        if (!thisDate.equals("prov_no")) {
                                                                                            if (!firstBar) {%>|<%
                                                                                    }
                                                                                    ;
                                                                                    firstBar = false;
                                                                                    String[] thisDateArr = thisDate.split(",");
                                                                                    String thisDispDate = thisDateArr[1];
                                                                                %>
                                                                                <a style="text-decoration: none;"
                                                                                   href="#"
                                                                                   onclick="return !showAppt('_prov1_<%=thisDateArr[0]%>', event);"><b><%=thisDispDate%>
                                                                                </b></a>
                                                                                <div id='menu_prov1_<%=thisDateArr[0]%>'
                                                                                     class='menu'
                                                                                     onclick='event.cancelBubble = true;'>
                                                                                    <h3 style='text-align: center; color: black;'>
                                                                                        Available Appts.
                                                                                        (<%=thisDispDate%>)</h3>
                                                                                    <ul>
                                                                                        <%
                                                                                            ArrayList<String> sortedTimes = new ArrayList(provMap.get("prov1").get(thisDate).keySet());
                                                                                            Collections.sort(sortedTimes);
                                                                                            for (String thisTime : sortedTimes) {
                                                                                                String[] thisTimeArr = thisTime.split(",");
                                                                                        %>
                                                                                        <li>[<%=thisTimeArr[1]%>] <a
                                                                                                href="#"
                                                                                                onClick="popupPage(400,780,'<%=provMap.get("prov1").get(thisDate).get(thisTime) %>');return false;"><%= thisTimeArr[0] %>
                                                                                        </a></li>
                                                                                        <%
                                                                                            }
                                                                                        %></ul>
                                                                                </div>
                                                                                <%
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                %>
                                                                            </li>
                                                                            <% }
                                                                                if (StringUtils.isNotEmpty(providerBean.getProperty(midwife, ""))) { %>
                                                                            <li>Alt. Provider 2:
                                                                                <b><%=providerBean.getProperty(midwife, "")%>
                                                                                </b>
                                                                                <% // ===== quick appointment booking for prov2 =====
                                                                                    if (provMap.get("prov2") != null) {
                                                                                %><br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<%
                                                                                    boolean firstBar = true;
                                                                                    ArrayList<String> sortedDays = new ArrayList(provMap.get("prov2").keySet());
                                                                                    Collections.sort(sortedDays);
                                                                                    for (String thisDate : sortedDays) {
                                                                                        if (!thisDate.equals("prov_no")) {
                                                                                            if (!firstBar) {%>|<%
                                                                                    }
                                                                                    ;
                                                                                    firstBar = false;
                                                                                    String[] thisDateArr = thisDate.split(",");
                                                                                    String thisDispDate = thisDateArr[1];
                                                                                %>
                                                                                <a style="text-decoration: none;"
                                                                                   href="#"
                                                                                   onclick="return !showAppt('_prov2_<%=thisDateArr[0]%>', event);"><b><%=thisDispDate%>
                                                                                </b></a>
                                                                                <div id='menu_prov2_<%=thisDateArr[0]%>'
                                                                                     class='menu'
                                                                                     onclick='event.cancelBubble = true;'>
                                                                                    <h3 style='text-align: center; color: black;'>
                                                                                        Available Appts.
                                                                                        (<%=thisDispDate%>)</h3>
                                                                                    <ul>
                                                                                        <%
                                                                                            ArrayList<String> sortedTimes = new ArrayList(provMap.get("prov2").get(thisDate).keySet());
                                                                                            Collections.sort(sortedTimes);
                                                                                            for (String thisTime : sortedTimes) {
                                                                                                String[] thisTimeArr = thisTime.split(",");
                                                                                        %>
                                                                                        <li>[<%=thisTimeArr[1]%>] <a
                                                                                                href="#"
                                                                                                onClick="popupPage(400,780,'<%=provMap.get("prov2").get(thisDate).get(thisTime) %>');return false;"><%= thisTimeArr[0] %>
                                                                                        </a></li>
                                                                                        <%
                                                                                            }
                                                                                        %></ul>
                                                                                </div>
                                                                                <%
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                %>
                                                                            </li>
                                                                            <% }
                                                                                if (StringUtils.isNotEmpty(providerBean.getProperty(nurse, ""))) { %>
                                                                            <li>Alt. Provider 3:
                                                                                <b><%=providerBean.getProperty(nurse, "")%>
                                                                                </b>
                                                                                <% // ===== quick appointment booking for prov3 =====
                                                                                    if (provMap.get("prov3") != null) {
                                                                                %><br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<%
                                                                                    boolean firstBar = true;
                                                                                    ArrayList<String> sortedDays = new ArrayList(provMap.get("prov3").keySet());
                                                                                    Collections.sort(sortedDays);
                                                                                    for (String thisDate : sortedDays) {
                                                                                        if (!thisDate.equals("prov_no")) {
                                                                                            if (!firstBar) {%>|<%
                                                                                    }
                                                                                    ;
                                                                                    firstBar = false;
                                                                                    String[] thisDateArr = thisDate.split(",");
                                                                                    String thisDispDate = thisDateArr[1];
                                                                                %>
                                                                                <a style="text-decoration: none;"
                                                                                   href="#"
                                                                                   onclick="return !showAppt('_prov3_<%=thisDateArr[0]%>', event);"><b><%=thisDispDate%>
                                                                                </b></a>
                                                                                <div id='menu_prov3_<%=thisDateArr[0]%>'
                                                                                     class='menu'
                                                                                     onclick='event.cancelBubble = true;'>
                                                                                    <h3 style='text-align: center; color: black;'>
                                                                                        Available Appts.
                                                                                        (<%=thisDispDate%>)</h3>
                                                                                    <ul>
                                                                                        <%
                                                                                            ArrayList<String> sortedTimes = new ArrayList(provMap.get("prov3").get(thisDate).keySet());
                                                                                            Collections.sort(sortedTimes);
                                                                                            for (String thisTime : sortedTimes) {
                                                                                                String[] thisTimeArr = thisTime.split(",");
                                                                                        %>
                                                                                        <li>[<%=thisTimeArr[1]%>] <a
                                                                                                href="#"
                                                                                                onClick="popupPage(400,780,'<%=provMap.get("prov3").get(thisDate).get(thisTime) %>');return false;"><%= thisTimeArr[0] %>
                                                                                        </a></li>
                                                                                        <%
                                                                                            }
                                                                                        %></ul>
                                                                                </div>
                                                                                <%
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                %>
                                                                            </li>
                                                                            <% } %>
                                                                        </ul>
                                                                    </div>
                                                                </div>

                                                                <%--} --%>
                                                            </oscar:oscarPropertiesCheck>
                                                                <%-- END TOGGLE WORKFLOW_ENHANCE --%>

                                                                <%-- AUTHOR DENNIS WARREN O/A COLCAMEX RESOURCES --%>
                                                            <oscar:oscarPropertiesCheck
                                                                    property="DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM"
                                                                    value="true">
                                                                <jsp:include page="displayHealthCareTeam.jsp">
                                                                    <jsp:param name="demographicNo"
                                                                               value="<%= demographic_no %>"/>
                                                                </jsp:include>
                                                            </oscar:oscarPropertiesCheck>
                                                                <%-- TOGGLE OFF PATIENT CLINIC STATUS --%>
                                                            <oscar:oscarPropertiesCheck
                                                                    property="DEMOGRAPHIC_PATIENT_CLINIC_STATUS"
                                                                    value="true">

                                                                <div class="demographicSection"
                                                                     id="patientClinicStatus">
                                                                    <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgPatientClinicStatus"/></h3>
                                                                    <ul>
                                                                        <li><span class="label">
							<% if (oscarProps.getProperty("demographicLabelDoctor") != null) {
                                out.print(oscarProps.getProperty("demographicLabelDoctor", ""));
                            } else { %>
							<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formMRP"/>
                                                    <% } %>:</span><span class="info">
                                                    <%if (demographic != null && demographic.getProviderNo() != null) {%>
                                                           <%=providerBean.getProperty(demographic.getProviderNo(), "")%>
                                                    <%}%>
                                                    </span>
                                                                        </li>
                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formNurse"/>:</span><span
                                                                                class="info"><%=providerBean.getProperty(nurse == null ? "" : nurse, "")%></span>
                                                                        </li>
                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formMidwife"/>:</span><span
                                                                                class="info"><%=providerBean.getProperty(midwife == null ? "" : midwife, "")%></span>
                                                                        </li>
                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResident"/>:</span>
                                                                            <span class="info"><%=providerBean.getProperty(resident == null ? "" : resident, "")%></span>
                                                                        </li>
                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formRefDoc"/>:</span><span
                                                                                class="info"><%=rd%></span>
                                                                        </li>
                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formRefDocNo"/>:</span><span
                                                                                class="info"><%=rdohip%></span>
                                                                        </li>
                                                                    </ul>
                                                                </div>

                                                            </oscar:oscarPropertiesCheck>

                                                                <%-- END TOGGLE OFF PATIENT CLINIC STATUS --%>

                                                                <%-- END AUTHOR DENNIS WARREN O/A COLCAMEX RESOURCES --%>


                                                            <div class="demographicSection" id="notes">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formNotes"/></h3>

                                                                <%=StringEscapeUtils.escapeHtml4(notes)%>&nbsp;
                                                                <%if (hasImportExtra) { %>
                                                                <a href="javascript:void(0);"
                                                                   title="Extra data from Import"
                                                                   onclick="window.open('<%= request.getContextPath() %>/annotation/importExtra.jsp?display=<%=annotation_display %>&amp;table_id=<%=demographic_no %>&amp;demo=<%=demographic_no %>','anwin','width=400,height=250');">
                                                                    <img src="<%= request.getContextPath() %>/images/notes.gif" align="right"
                                                                         alt="Extra data from Import" height="16"
                                                                         width="13" border="0"> </a>
                                                                <%} %>


                                                            </div>

                                                                <%-- TOGGLED OFF PROGRAM ADMISSIONS --%>
                                                            <oscar:oscarPropertiesCheck
                                                                    property="DEMOGRAPHIC_PROGRAM_ADMISSIONS"
                                                                    value="true">
                                                                <div class="demographicSection" id="programs">
                                                                    <h3>Programs</h3>
                                                                    <ul>
                                                                        <%
                                                                            for (Admission adm : serviceAdmissions) {
                                                                        %>
                                                                        <li><span class="label">Service:</span><span
                                                                                class="info"><%=adm.getProgramName()%></span>
                                                                        </li>

                                                                        <%
                                                                            }
                                                                        %>
                                                                    </ul>

                                                                </div>
                                                            </oscar:oscarPropertiesCheck>
                                                                <%-- TOGGLED OFF PROGRAM ADMISSIONS --%>

                                                        </div>

                                                    </div>
                                                </div>
                                                <!--newEnd-->

                                                <table width="100%" bgcolor="#EEEEFF" border=0 id="editDemographic"
                                                       style="display: none;">
                                                    <tr>
                                                        <td align="right"
                                                            title='<%=demographic.getDemographicNo()%>'>
                                                            <b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formLastName"/>: </b>
                                                        </td>
                                                        <td align="left"><input type="text"
                                                                                name="last_name" <%=getDisabled("last_name")%>
                                                                                size="30"
                                                                                value="<%=StringEscapeUtils.escapeHtml4(demographic.getLastName())%>"
                                                                                onBlur="upCaseCtrl(this)"></td>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formFirstName"/>:
                                                        </b></td>
                                                        <td align="left"><input type="text"
                                                                                name="first_name" <%=getDisabled("first_name")%>
                                                                                size="30"
                                                                                value="<%=StringEscapeUtils.escapeHtml4(demographic.getFirstName())%>"
                                                                                onBlur="upCaseCtrl(this)"></td>
                                                    </tr>
                                                    <tr>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formMiddleNames"/>:
                                                        </b></td>
                                                        <td align="left"><input type="text"
                                                                                name="middleNames" <%=getDisabled("middleNames")%>
                                                                                size="30"
                                                                                value="<%=StringEscapeUtils.escapeHtml4(demographic.getMiddleNames())%>"
                                                                                onBlur="upCaseCtrl(this)"></td>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDemoTitle"/>: </b>
                                                        </td>
                                                        <td align="left">
                                                            <%
                                                                String title = demographic.getTitle();
                                                                if (title == null) {
                                                                    title = "";
                                                                }
                                                            %>
                                                            <select name="title" <%=getDisabled("title")%>>
                                                                <option value="" <%=title.equals("") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgNotSet"/></option>
                                                                <option value="DR" <%=title.equalsIgnoreCase("DR") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgDr"/></option>
                                                                <option value="MS" <%=title.equalsIgnoreCase("MS") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMs"/></option>
                                                                <option value="MISS" <%=title.equalsIgnoreCase("MISS") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMiss"/></option>
                                                                <option value="MRS" <%=title.equalsIgnoreCase("MRS") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMrs"/></option>
                                                                <option value="MR" <%=title.equalsIgnoreCase("MR") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMr"/></option>
                                                                <option value="MSSR" <%=title.equalsIgnoreCase("MSSR") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMssr"/></option>
                                                                <option value="PROF" <%=title.equalsIgnoreCase("PROF") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgProf"/></option>
                                                                <option value="REEVE" <%=title.equalsIgnoreCase("REEVE") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgReeve"/></option>
                                                                <option value="REV" <%=title.equalsIgnoreCase("REV") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgRev"/></option>
                                                                <option value="RT_HON" <%=title.equalsIgnoreCase("RT_HON") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgRtHon"/></option>
                                                                <option value="SEN" <%=title.equalsIgnoreCase("SEN") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgSen"/></option>
                                                                <option value="SGT" <%=title.equalsIgnoreCase("SGT") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgSgt"/></option>
                                                                <option value="SR" <%=title.equalsIgnoreCase("SR") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgSr"/></option>

                                                                <option value="MADAM" <%=title.equalsIgnoreCase("MADAM") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMadam"/></option>
                                                                <option value="MME" <%=title.equalsIgnoreCase("MME") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMme"/></option>
                                                                <option value="MLLE" <%=title.equalsIgnoreCase("MLLE") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMlle"/></option>
                                                                <option value="MAJOR" <%=title.equalsIgnoreCase("MAJOR") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMajor"/></option>
                                                                <option value="MAYOR" <%=title.equalsIgnoreCase("MAYOR") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMayor"/></option>

                                                                <option value="BRO" <%=title.equalsIgnoreCase("BRO") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgBro"/></option>
                                                                <option value="CAPT" <%=title.equalsIgnoreCase("CAPT") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgCapt"/></option>
                                                                <option value="CHIEF" <%=title.equalsIgnoreCase("CHIEF") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgChief"/></option>
                                                                <option value="CST" <%=title.equalsIgnoreCase("CST") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgCst"/></option>
                                                                <option value="CORP" <%=title.equalsIgnoreCase("CORP") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgCorp"/></option>
                                                                <option value="FR" <%=title.equalsIgnoreCase("FR") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgFr"/></option>
                                                                <option value="HON" <%=title.equalsIgnoreCase("HON") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgHon"/></option>
                                                                <option value="LT" <%=title.equalsIgnoreCase("LT") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgLt"/></option>

                                                            </select>
                                                        </td>
                                                    </tr>
                                                    <tr>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNameUsed"/>:
                                                        </b></td>
                                                        <td align="left"><input type="text"
                                                                                name="nameUsed" <%=getDisabled("nameUsed")%>
                                                                                size="30"
                                                                                value="<c:out value="<%=Encode.forHtmlAttribute(demographic.getAlias())%>" />"
                                                                                onBlur="upCaseCtrl(this)"></td>
                                                        <td style="text-align: right;">
                                                            <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPronouns"/></strong>
                                                        </td>
                                                        <td style="text-align: left;">
                                                            <input type="text" id="patientPronouns" name="pronouns"
                                                                   value="<%=Encode.forHtmlAttribute(StringUtils.trimToEmpty(demographic.getPronoun()))%>"/>
                                                        </td>
                                                    </tr>
                                                    <tr>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDemoLanguage"/>: </b>
                                                        </td>
                                                        <td align="left">
                                                            <%
                                                                String lang = ca.openosp.openo.util.StringUtils.noNull(demographic.getOfficialLanguage()); %>
                                                            <select name="official_lang" <%=getDisabled("official_lang")%>>
                                                                <option value="English" <%=lang.equals("English") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgEnglish"/></option>
                                                                <option value="French" <%=lang.equals("French") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgFrench"/></option>
                                                                <option value="Other" <%=lang.equals("Other") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optOther"/></option>
                                                            </select>
                                                        </td>
                                                        <td align="right">
                                                            <b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgSpoken"/>: </b>
                                                        </td>
                                                        <td>
                                                            <%
                                                                String spokenLang = ca.openosp.openo.util.StringUtils.noNull(demographic.getSpokenLanguage()); %>
                                                            <select name="spoken_lang"
                                                                    style="width: 200px;" <%=getDisabled("spoken_lang")%>>
                                                                <%for (String splang : Util.spokenLangProperties.getLangSorted()) { %>
                                                                <option value="<%=splang %>" <%=spokenLang.equals(splang) ? "selected" : "" %>><%=splang %>
                                                                </option>
                                                                <%} %>
                                                            </select>
                                                        </td>

                                                    </tr>

                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formAddr"/>: </b>
                                                        </td>
                                                        <td align="left"><input type="text"
                                                                                name="address" <%=getDisabled("address")%>
                                                                                size="30"
                                                                                value="<%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demographic.getAddress()))%>">
                                                        </td>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formCity"/>: </b>
                                                        </td>
                                                        <td align="left"><input type="text" name="city"
                                                                                size="30" <%=getDisabled("city")%>
                                                                                value="<%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demographic.getCity()))%>">
                                                        </td>
                                                    </tr>

                                                    <tr valign="top">
                                                        <td align="right">
                                                            <b><% if (oscarProps.getProperty("demographicLabelProvince") == null) { %>
                                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formProcvince"/> <% } else {
                                                                    out.print(oscarProps.getProperty("demographicLabelProvince"));
                                                                } %> : </b></td>
                                                        <td align="left">
                                                            <% String province = demographic.getProvince(); %>
                                                            <%
                                                                if ("true".equals(OscarProperties.getInstance().getProperty("iso3166.2.enabled", "false"))) {
                                                            %>
                                                            <select name="province" id="province"></select>
                                                            <br/>
                                                            Filter by Country: <select name="country"
                                                                                       id="country"></select>

                                                            <% } else { %>
                                                            <select name="province"
                                                                    style="width: 200px" <%=getDisabled("province")%>>
                                                                <option value="OT"
                                                                        <%=(province == null || province.equals("OT") || province.equals("") || province.length() > 2) ? " selected" : ""%>>
                                                                    Other
                                                                </option>
                                                                <% if (pNames.isDefined()) {
                                                                    for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                                                        String pr2 = (String) li.next(); %>
                                                                <option value="<%=pr2%>"
                                                                        <%=pr2.equals(province) ? " selected" : ""%>><%=li.next()%>
                                                                </option>
                                                                <% }//for %>
                                                                <% } else { %>
                                                                <option value="AB" <%="AB".equals(province) ? " selected" : ""%>>
                                                                    AB-Alberta
                                                                </option>
                                                                <option value="BC" <%="BC".equals(province) ? " selected" : ""%>>
                                                                    BC-British Columbia
                                                                </option>
                                                                <option value="MB" <%="MB".equals(province) ? " selected" : ""%>>
                                                                    MB-Manitoba
                                                                </option>
                                                                <option value="NB" <%="NB".equals(province) ? " selected" : ""%>>
                                                                    NB-New Brunswick
                                                                </option>
                                                                <option value="NL" <%="NL".equals(province) ? " selected" : ""%>>
                                                                    NL-Newfoundland Labrador
                                                                </option>
                                                                <option value="NT" <%="NT".equals(province) ? " selected" : ""%>>
                                                                    NT-Northwest Territory
                                                                </option>
                                                                <option value="NS" <%="NS".equals(province) ? " selected" : ""%>>
                                                                    NS-Nova Scotia
                                                                </option>
                                                                <option value="NU" <%="NU".equals(province) ? " selected" : ""%>>
                                                                    NU-Nunavut
                                                                </option>
                                                                <option value="ON" <%="ON".equals(province) ? " selected" : ""%>>
                                                                    ON-Ontario
                                                                </option>
                                                                <option value="PE" <%="PE".equals(province) ? " selected" : ""%>>
                                                                    PE-Prince Edward Island
                                                                </option>
                                                                <option value="QC" <%="QC".equals(province) ? " selected" : ""%>>
                                                                    QC-Quebec
                                                                </option>
                                                                <option value="SK" <%="SK".equals(province) ? " selected" : ""%>>
                                                                    SK-Saskatchewan
                                                                </option>
                                                                <option value="YT" <%="YT".equals(province) ? " selected" : ""%>>
                                                                    YT-Yukon
                                                                </option>
                                                                <option value="US" <%="US".equals(province) ? " selected" : ""%>>
                                                                    US resident
                                                                </option>
                                                                <option value="US-AK" <%="US-AK".equals(province) ? " selected" : ""%>>
                                                                    US-AK-Alaska
                                                                </option>
                                                                <option value="US-AL" <%="US-AL".equals(province) ? " selected" : ""%>>
                                                                    US-AL-Alabama
                                                                </option>
                                                                <option value="US-AR" <%="US-AR".equals(province) ? " selected" : ""%>>
                                                                    US-AR-Arkansas
                                                                </option>
                                                                <option value="US-AZ" <%="US-AZ".equals(province) ? " selected" : ""%>>
                                                                    US-AZ-Arizona
                                                                </option>
                                                                <option value="US-CA" <%="US-CA".equals(province) ? " selected" : ""%>>
                                                                    US-CA-California
                                                                </option>
                                                                <option value="US-CO" <%="US-CO".equals(province) ? " selected" : ""%>>
                                                                    US-CO-Colorado
                                                                </option>
                                                                <option value="US-CT" <%="US-CT".equals(province) ? " selected" : ""%>>
                                                                    US-CT-Connecticut
                                                                </option>
                                                                <option value="US-CZ" <%="US-CZ".equals(province) ? " selected" : ""%>>
                                                                    US-CZ-Canal Zone
                                                                </option>
                                                                <option value="US-DC" <%="US-DC".equals(province) ? " selected" : ""%>>
                                                                    US-DC-District Of Columbia
                                                                </option>
                                                                <option value="US-DE" <%="US-DE".equals(province) ? " selected" : ""%>>
                                                                    US-DE-Delaware
                                                                </option>
                                                                <option value="US-FL" <%="US-FL".equals(province) ? " selected" : ""%>>
                                                                    US-FL-Florida
                                                                </option>
                                                                <option value="US-GA" <%="US-GA".equals(province) ? " selected" : ""%>>
                                                                    US-GA-Georgia
                                                                </option>
                                                                <option value="US-GU" <%="US-GU".equals(province) ? " selected" : ""%>>
                                                                    US-GU-Guam
                                                                </option>
                                                                <option value="US-HI" <%="US-HI".equals(province) ? " selected" : ""%>>
                                                                    US-HI-Hawaii
                                                                </option>
                                                                <option value="US-IA" <%="US-IA".equals(province) ? " selected" : ""%>>
                                                                    US-IA-Iowa
                                                                </option>
                                                                <option value="US-ID" <%="US-ID".equals(province) ? " selected" : ""%>>
                                                                    US-ID-Idaho
                                                                </option>
                                                                <option value="US-IL" <%="US-IL".equals(province) ? " selected" : ""%>>
                                                                    US-IL-Illinois
                                                                </option>
                                                                <option value="US-IN" <%="US-IN".equals(province) ? " selected" : ""%>>
                                                                    US-IN-Indiana
                                                                </option>
                                                                <option value="US-KS" <%="US-KS".equals(province) ? " selected" : ""%>>
                                                                    US-KS-Kansas
                                                                </option>
                                                                <option value="US-KY" <%="US-KY".equals(province) ? " selected" : ""%>>
                                                                    US-KY-Kentucky
                                                                </option>
                                                                <option value="US-LA" <%="US-LA".equals(province) ? " selected" : ""%>>
                                                                    US-LA-Louisiana
                                                                </option>
                                                                <option value="US-MA" <%="US-MA".equals(province) ? " selected" : ""%>>
                                                                    US-MA-Massachusetts
                                                                </option>
                                                                <option value="US-MD" <%="US-MD".equals(province) ? " selected" : ""%>>
                                                                    US-MD-Maryland
                                                                </option>
                                                                <option value="US-ME" <%="US-ME".equals(province) ? " selected" : ""%>>
                                                                    US-ME-Maine
                                                                </option>
                                                                <option value="US-MI" <%="US-MI".equals(province) ? " selected" : ""%>>
                                                                    US-MI-Michigan
                                                                </option>
                                                                <option value="US-MN" <%="US-MN".equals(province) ? " selected" : ""%>>
                                                                    US-MN-Minnesota
                                                                </option>
                                                                <option value="US-MO" <%="US-MO".equals(province) ? " selected" : ""%>>
                                                                    US-MO-Missouri
                                                                </option>
                                                                <option value="US-MS" <%="US-MS".equals(province) ? " selected" : ""%>>
                                                                    US-MS-Mississippi
                                                                </option>
                                                                <option value="US-MT" <%="US-MT".equals(province) ? " selected" : ""%>>
                                                                    US-MT-Montana
                                                                </option>
                                                                <option value="US-NC" <%="US-NC".equals(province) ? " selected" : ""%>>
                                                                    US-NC-North Carolina
                                                                </option>
                                                                <option value="US-ND" <%="US-ND".equals(province) ? " selected" : ""%>>
                                                                    US-ND-North Dakota
                                                                </option>
                                                                <option value="US-NE" <%="US-NE".equals(province) ? " selected" : ""%>>
                                                                    US-NE-Nebraska
                                                                </option>
                                                                <option value="US-NH" <%="US-NH".equals(province) ? " selected" : ""%>>
                                                                    US-NH-New Hampshire
                                                                </option>
                                                                <option value="US-NJ" <%="US-NJ".equals(province) ? " selected" : ""%>>
                                                                    US-NJ-New Jersey
                                                                </option>
                                                                <option value="US-NM" <%="US-NM".equals(province) ? " selected" : ""%>>
                                                                    US-NM-New Mexico
                                                                </option>
                                                                <option value="US-NU" <%="US-NU".equals(province) ? " selected" : ""%>>
                                                                    US-NU-Nunavut
                                                                </option>
                                                                <option value="US-NV" <%="US-NV".equals(province) ? " selected" : ""%>>
                                                                    US-NV-Nevada
                                                                </option>
                                                                <option value="US-NY" <%="US-NY".equals(province) ? " selected" : ""%>>
                                                                    US-NY-New York
                                                                </option>
                                                                <option value="US-OH" <%="US-OH".equals(province) ? " selected" : ""%>>
                                                                    US-OH-Ohio
                                                                </option>
                                                                <option value="US-OK" <%="US-OK".equals(province) ? " selected" : ""%>>
                                                                    US-OK-Oklahoma
                                                                </option>
                                                                <option value="US-OR" <%="US-OR".equals(province) ? " selected" : ""%>>
                                                                    US-OR-Oregon
                                                                </option>
                                                                <option value="US-PA" <%="US-PA".equals(province) ? " selected" : ""%>>
                                                                    US-PA-Pennsylvania
                                                                </option>
                                                                <option value="US-PR" <%="US-PR".equals(province) ? " selected" : ""%>>
                                                                    US-PR-Puerto Rico
                                                                </option>
                                                                <option value="US-RI" <%="US-RI".equals(province) ? " selected" : ""%>>
                                                                    US-RI-Rhode Island
                                                                </option>
                                                                <option value="US-SC" <%="US-SC".equals(province) ? " selected" : ""%>>
                                                                    US-SC-South Carolina
                                                                </option>
                                                                <option value="US-SD" <%="US-SD".equals(province) ? " selected" : ""%>>
                                                                    US-SD-South Dakota
                                                                </option>
                                                                <option value="US-TN" <%="US-TN".equals(province) ? " selected" : ""%>>
                                                                    US-TN-Tennessee
                                                                </option>
                                                                <option value="US-TX" <%="US-TX".equals(province) ? " selected" : ""%>>
                                                                    US-TX-Texas
                                                                </option>
                                                                <option value="US-UT" <%="US-UT".equals(province) ? " selected" : ""%>>
                                                                    US-UT-Utah
                                                                </option>
                                                                <option value="US-VA" <%="US-VA".equals(province) ? " selected" : ""%>>
                                                                    US-VA-Virginia
                                                                </option>
                                                                <option value="US-VI" <%="US-VI".equals(province) ? " selected" : ""%>>
                                                                    US-VI-Virgin Islands
                                                                </option>
                                                                <option value="US-VT" <%="US-VT".equals(province) ? " selected" : ""%>>
                                                                    US-VT-Vermont
                                                                </option>
                                                                <option value="US-WA" <%="US-WA".equals(province) ? " selected" : ""%>>
                                                                    US-WA-Washington
                                                                </option>
                                                                <option value="US-WI" <%="US-WI".equals(province) ? " selected" : ""%>>
                                                                    US-WI-Wisconsin
                                                                </option>
                                                                <option value="US-WV" <%="US-WV".equals(province) ? " selected" : ""%>>
                                                                    US-WV-West Virginia
                                                                </option>
                                                                <option value="US-WY" <%="US-WY".equals(province) ? " selected" : ""%>>
                                                                    US-WY-Wyoming
                                                                </option>
                                                                <% } %>
                                                            </select>

                                                            <% } %>
                                                        </td>
                                                        <td align="right">
                                                            <b><% if (oscarProps.getProperty("demographicLabelPostal") == null) { %>
                                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPostal"/> <% } else {
                                                                    out.print(oscarProps.getProperty("demographicLabelPostal"));
                                                                } %> : </b></td>
                                                        <td align="left"><input type="text" name="postal"
                                                                                size="30" <%=getDisabled("postal")%>
                                                                                value="<%=StringUtils.trimToEmpty(demographic.getPostal())%>"
                                                                                onBlur="upCaseCtrl(this)"
                                                                                onChange="isPostalCode()"></td>
                                                    </tr>


                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialAddr"/>: </b>
                                                        </td>
                                                        <td align="left"><input type="text"
                                                                                name="residentialAddress" <%=getDisabled("residentialAddress")%>
                                                                                size="30"
                                                                                value="<%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demographic.getResidentialAddress()))%>">
                                                        </td>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialCity"/>: </b>
                                                        </td>
                                                        <td align="left"><input type="text" name="residentialCity"
                                                                                size="30" <%=getDisabled("residentialCity")%>
                                                                                value="<%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demographic.getResidentialCity()))%>">
                                                        </td>
                                                    </tr>

                                                    <tr valign="top">
                                                        <td align="right"><b>
                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialProvince"/>
                                                            : </b></td>
                                                        <td align="left">

                                                            <%
                                                                String residentialProvince = demographic.getResidentialProvince(); %>
                                                            <%
                                                                if ("true".equals(OscarProperties.getInstance().getProperty("iso3166.2.enabled", "false"))) {
                                                            %>
                                                            <select name="residentialProvince"
                                                                    id="residentialProvince"></select>
                                                            <br/>
                                                            Filter by Country: <select name="residentialCountry"
                                                                                       id="residentialCountry"></select>

                                                            <% } else { %>

                                                            <select name="residentialProvince"
                                                                    style="width: 200px" <%=getDisabled("residentialProvince")%>>
                                                                <option value="OT"
                                                                        <%=(residentialProvince == null || residentialProvince.equals("OT") || residentialProvince.equals("") || residentialProvince.length() > 2) ? " selected" : ""%>>
                                                                    Other
                                                                </option>
                                                                <% if (pNames.isDefined()) {
                                                                    for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                                                        String pr2 = (String) li.next(); %>
                                                                <option value="<%=pr2%>"
                                                                        <%=pr2.equals(residentialProvince) ? " selected" : ""%>><%=li.next()%>
                                                                </option>
                                                                <% }//for %>
                                                                <% } else { %>
                                                                <option value="AB" <%="AB".equals(residentialProvince) ? " selected" : ""%>>
                                                                    AB-Alberta
                                                                </option>
                                                                <option value="BC" <%="BC".equals(residentialProvince) ? " selected" : ""%>>
                                                                    BC-British Columbia
                                                                </option>
                                                                <option value="MB" <%="MB".equals(residentialProvince) ? " selected" : ""%>>
                                                                    MB-Manitoba
                                                                </option>
                                                                <option value="NB" <%="NB".equals(residentialProvince) ? " selected" : ""%>>
                                                                    NB-New Brunswick
                                                                </option>
                                                                <option value="NL" <%="NL".equals(residentialProvince) ? " selected" : ""%>>
                                                                    NL-Newfoundland Labrador
                                                                </option>
                                                                <option value="NT" <%="NT".equals(residentialProvince) ? " selected" : ""%>>
                                                                    NT-Northwest Territory
                                                                </option>
                                                                <option value="NS" <%="NS".equals(residentialProvince) ? " selected" : ""%>>
                                                                    NS-Nova Scotia
                                                                </option>
                                                                <option value="NU" <%="NU".equals(residentialProvince) ? " selected" : ""%>>
                                                                    NU-Nunavut
                                                                </option>
                                                                <option value="ON" <%="ON".equals(residentialProvince) ? " selected" : ""%>>
                                                                    ON-Ontario
                                                                </option>
                                                                <option value="PE" <%="PE".equals(residentialProvince) ? " selected" : ""%>>
                                                                    PE-Prince Edward Island
                                                                </option>
                                                                <option value="QC" <%="QC".equals(residentialProvince) ? " selected" : ""%>>
                                                                    QC-Quebec
                                                                </option>
                                                                <option value="SK" <%="SK".equals(residentialProvince) ? " selected" : ""%>>
                                                                    SK-Saskatchewan
                                                                </option>
                                                                <option value="YT" <%="YT".equals(residentialProvince) ? " selected" : ""%>>
                                                                    YT-Yukon
                                                                </option>
                                                                <option value="US" <%="US".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US resident
                                                                </option>
                                                                <option value="US-AK" <%="US-AK".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-AK-Alaska
                                                                </option>
                                                                <option value="US-AL" <%="US-AL".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-AL-Alabama
                                                                </option>
                                                                <option value="US-AR" <%="US-AR".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-AR-Arkansas
                                                                </option>
                                                                <option value="US-AZ" <%="US-AZ".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-AZ-Arizona
                                                                </option>
                                                                <option value="US-CA" <%="US-CA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-CA-California
                                                                </option>
                                                                <option value="US-CO" <%="US-CO".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-CO-Colorado
                                                                </option>
                                                                <option value="US-CT" <%="US-CT".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-CT-Connecticut
                                                                </option>
                                                                <option value="US-CZ" <%="US-CZ".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-CZ-Canal Zone
                                                                </option>
                                                                <option value="US-DC" <%="US-DC".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-DC-District Of Columbia
                                                                </option>
                                                                <option value="US-DE" <%="US-DE".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-DE-Delaware
                                                                </option>
                                                                <option value="US-FL" <%="US-FL".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-FL-Florida
                                                                </option>
                                                                <option value="US-GA" <%="US-GA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-GA-Georgia
                                                                </option>
                                                                <option value="US-GU" <%="US-GU".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-GU-Guam
                                                                </option>
                                                                <option value="US-HI" <%="US-HI".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-HI-Hawaii
                                                                </option>
                                                                <option value="US-IA" <%="US-IA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-IA-Iowa
                                                                </option>
                                                                <option value="US-ID" <%="US-ID".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-ID-Idaho
                                                                </option>
                                                                <option value="US-IL" <%="US-IL".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-IL-Illinois
                                                                </option>
                                                                <option value="US-IN" <%="US-IN".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-IN-Indiana
                                                                </option>
                                                                <option value="US-KS" <%="US-KS".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-KS-Kansas
                                                                </option>
                                                                <option value="US-KY" <%="US-KY".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-KY-Kentucky
                                                                </option>
                                                                <option value="US-LA" <%="US-LA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-LA-Louisiana
                                                                </option>
                                                                <option value="US-MA" <%="US-MA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-MA-Massachusetts
                                                                </option>
                                                                <option value="US-MD" <%="US-MD".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-MD-Maryland
                                                                </option>
                                                                <option value="US-ME" <%="US-ME".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-ME-Maine
                                                                </option>
                                                                <option value="US-MI" <%="US-MI".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-MI-Michigan
                                                                </option>
                                                                <option value="US-MN" <%="US-MN".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-MN-Minnesota
                                                                </option>
                                                                <option value="US-MO" <%="US-MO".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-MO-Missouri
                                                                </option>
                                                                <option value="US-MS" <%="US-MS".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-MS-Mississippi
                                                                </option>
                                                                <option value="US-MT" <%="US-MT".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-MT-Montana
                                                                </option>
                                                                <option value="US-NC" <%="US-NC".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NC-North Carolina
                                                                </option>
                                                                <option value="US-ND" <%="US-ND".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-ND-North Dakota
                                                                </option>
                                                                <option value="US-NE" <%="US-NE".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NE-Nebraska
                                                                </option>
                                                                <option value="US-NH" <%="US-NH".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NH-New Hampshire
                                                                </option>
                                                                <option value="US-NJ" <%="US-NJ".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NJ-New Jersey
                                                                </option>
                                                                <option value="US-NM" <%="US-NM".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NM-New Mexico
                                                                </option>
                                                                <option value="US-NU" <%="US-NU".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NU-Nunavut
                                                                </option>
                                                                <option value="US-NV" <%="US-NV".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NV-Nevada
                                                                </option>
                                                                <option value="US-NY" <%="US-NY".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NY-New York
                                                                </option>
                                                                <option value="US-OH" <%="US-OH".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-OH-Ohio
                                                                </option>
                                                                <option value="US-OK" <%="US-OK".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-OK-Oklahoma
                                                                </option>
                                                                <option value="US-OR" <%="US-OR".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-OR-Oregon
                                                                </option>
                                                                <option value="US-PA" <%="US-PA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-PA-Pennsylvania
                                                                </option>
                                                                <option value="US-PR" <%="US-PR".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-PR-Puerto Rico
                                                                </option>
                                                                <option value="US-RI" <%="US-RI".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-RI-Rhode Island
                                                                </option>
                                                                <option value="US-SC" <%="US-SC".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-SC-South Carolina
                                                                </option>
                                                                <option value="US-SD" <%="US-SD".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-SD-South Dakota
                                                                </option>
                                                                <option value="US-TN" <%="US-TN".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-TN-Tennessee
                                                                </option>
                                                                <option value="US-TX" <%="US-TX".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-TX-Texas
                                                                </option>
                                                                <option value="US-UT" <%="US-UT".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-UT-Utah
                                                                </option>
                                                                <option value="US-VA" <%="US-VA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-VA-Virginia
                                                                </option>
                                                                <option value="US-VI" <%="US-VI".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-VI-Virgin Islands
                                                                </option>
                                                                <option value="US-VT" <%="US-VT".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-VT-Vermont
                                                                </option>
                                                                <option value="US-WA" <%="US-WA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-WA-Washington
                                                                </option>
                                                                <option value="US-WI" <%="US-WI".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-WI-Wisconsin
                                                                </option>
                                                                <option value="US-WV" <%="US-WV".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-WV-West Virginia
                                                                </option>
                                                                <option value="US-WY" <%="US-WY".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-WY-Wyoming
                                                                </option>
                                                                <% } %>
                                                            </select>
                                                            <% } %>
                                                        </td>
                                                        <td align="right"><b>
                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialPostal"/>
                                                            : </b></td>
                                                        <td align="left"><input type="text" name="residentialPostal"
                                                                                size="30" <%=getDisabled("residentialPostal")%>
                                                                                value="<%=StringUtils.trimToEmpty(demographic.getResidentialPostal())%>"
                                                                                onBlur="upCaseCtrl(this)"
                                                                                onChange="isPostalCode2()"></td>
                                                    </tr>


                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPhoneH"/>: </b>
                                                        </td>
                                                        <td align="left">
                                                            <input type="text" name="phone"
                                                                   onblur="formatPhoneNum();" <%=getDisabled("phone")%>
                                                                   style="display: inline; width: auto;"
                                                                   value="<%=StringUtils.trimToEmpty(StringUtils.trimToEmpty(demographic.getPhone()))%>">
                                                            <label for="hPhoneExt"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgExt"/>:</label>
                                                            <input type="text" style="width:50% !important;"
                                                                   name="hPhoneExt"
                                                                   id="hPhoneExt" <%=getDisabled("hPhoneExt")%>
                                                                   value="<%=StringUtils.trimToEmpty(StringUtils.trimToEmpty(demoExt.get("hPhoneExt")))%>"
                                                                   size="4"/>
                                                            <input type="hidden" name="hPhoneExtOrig"
                                                                   value="<%=StringUtils.trimToEmpty(StringUtils.trimToEmpty(demoExt.get("hPhoneExt")))%>"/>
                                                        </td>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPhoneW"/>:</b>
                                                        </td>
                                                        <td align="left"><input type="text"
                                                                                name="phone2" <%=getDisabled("phone2")%>
                                                                                onblur="formatPhoneNum();"
                                                                                style="display: inline; width: auto;"
                                                                                value="<%=StringUtils.trimToEmpty(demographic.getPhone2())%>">
                                                            <label for="wPhoneExt"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgExt"/>:</label>
                                                            <input type="text" style="width:50% !important;"
                                                                   name="wPhoneExt"
                                                                   id="wPhoneExt" <%=getDisabled("wPhoneExt")%>
                                                                   value="<%=StringUtils.trimToEmpty(StringUtils.trimToEmpty(demoExt.get("wPhoneExt")))%>"
                                                                   size="4"/> <input type="hidden"
                                                                                     name="wPhoneExtOrig"
                                                                                     value="<%=StringUtils.trimToEmpty(StringUtils.trimToEmpty(demoExt.get("wPhoneExt")))%>"/>
                                                        </td>
                                                    </tr>
                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPhoneC"/>: </b>
                                                        </td>
                                                        <td align="left">
                                                            <input type="text" name="demo_cell"
                                                                   onblur="formatPhoneNum();"
                                                                   style="display: inline; width: auto;" <%=getDisabled("demo_cell")%>
                                                                   value="<%=StringUtils.trimToEmpty(demoExt.get("demo_cell"))%>">
                                                            <input type="hidden" name="demo_cellOrig"
                                                                   value="<%=StringUtils.trimToEmpty(demoExt.get("demo_cell"))%>"/>
                                                        </td>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPhoneComment"/>: </b>
                                                        </td>
                                                        <td align="left" colspan="3">
                                                            <input type="hidden" name="phoneCommentOrig"
                                                                   value="<%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demoExt.get("phoneComment")))%>"/>
                                                            <textarea rows="2" cols="30"
                                                                      name="phoneComment"><%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demoExt.get("phoneComment")))%></textarea>
                                                        </td>
                                                    </tr>
                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formNewsLetter"/>:
                                                        </b></td>
                                                        <td align="left">
                                                            <%
                                                                String newsletter = ca.openosp.openo.util.StringUtils.noNull(demographic.getNewsletter()).trim();
                                                                if (newsletter == null || newsletter.equals("")) {
                                                                    newsletter = "Unknown";
                                                                }
                                                            %> <select name="newsletter" <%=getDisabled("newsletter")%>>
                                                            <option value="Unknown" <%if (newsletter.equals("Unknown")) {%>
                                                                    selected <%}%>><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optUnknown"/></option>
                                                            <option value="No" <%if (newsletter.equals("No")) {%>
                                                                    selected
                                                                    <%}%>><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optNo"/></option>
                                                            <option value="Paper" <%if (newsletter.equals("Paper")) {%>
                                                                    selected <%}%>><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optPaper"/></option>
                                                            <option value="Electronic"
                                                                    <%if (newsletter.equals("Electronic")) {%>
                                                                    selected <%}%>><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optElectronic"/></option>
                                                        </select></td>

                                                        <oscar:oscarPropertiesCheck value="true" defaultVal="false"
                                                                                    property="FIRST_NATIONS_MODULE">
                                                            <td align="right"><b>First Nation: </b></td>
                                                            <td align="left">

                                                                <select name="aboriginal" <%=getDisabled("aboriginal")%>>
                                                                    <option value="" ${ pageScope.demoExtended["aboriginal"] eq '' ? 'selected' : '' } >
                                                                        Unknown
                                                                    </option>
                                                                    <option value="No" ${ pageScope.demoExtended["aboriginal"] eq 'No' ? 'selected' : '' } >
                                                                        No
                                                                    </option>
                                                                    <option value="Yes" ${ pageScope.demoExtended["aboriginal"] eq 'Yes' ? 'selected' : '' } >
                                                                        Yes
                                                                    </option>

                                                                </select>
                                                                <input type="hidden" name="aboriginalOrig"
                                                                       value="${ pageScope.demoExtended["aboriginal"] }"/>
                                                            </td>
                                                        </oscar:oscarPropertiesCheck>
                                                    </tr>
                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formEmail"/>: </b>
                                                        </td>
                                                        <td align="left"><input type="text" name="email"
                                                                                size="30" <%=getDisabled("email")%>
                                                                                value="<%=demographic.getEmail() !=null ? Encode.forHtmlContent(demographic.getEmail()) : ""%>">
                                                        </td>
                                                        <td style="text-align: right;">
                                                            <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formGender"/></strong>
                                                        </td>
                                                        <td style="text-align: left;">
                                                            <input type="text" id="patientGender" name="gender"
                                                                   value="<%=Encode.forHtmlAttribute(StringUtils.trimToEmpty(demographic.getGender()))%>"/>
                                                        </td>
                                                    </tr>
                                                        <%--							<tr valign="top">--%>
                                                        <%--								<td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.consentToUseEmailForCare"/></b></td>--%>
                                                        <%--								<td align="left" nowrap>--%>
                                                        <%--									 <label for="consentToUseEmailForCareY"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgYes"/></label> --%>
                                                        <%--            								<input type="radio" value="yes" id="consentToUseEmailForCareY" name="consentToUseEmailForCare" <% if (demographic.getConsentToUseEmailForCare() != null && demographic.getConsentToUseEmailForCare()){ out.write("checked"); }%> />--%>
                                                        <%--          							 <label for="consentToUseEmailForCareN"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgNo"/></label>--%>
                                                        <%--            								<input type="radio" value="no" id="consentToUseEmailForCareN" name="consentToUseEmailForCare"  <% if (demographic.getConsentToUseEmailForCare() != null && !demographic.getConsentToUseEmailForCare()){ out.write("checked");}%> />--%>
                                                        <%--									 <label for="consentToUseEmailForCareE"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgUnset"/></label>--%>
                                                        <%--            								<input type="radio" value="unset" id="consentToUseEmailForCareE" name="consentToUseEmailForCare"  <% if (demographic.getConsentToUseEmailForCare() == null){ out.write("checked"); } %> />--%>
                                                        <%--								</td>--%>
                                                        <%--							</tr>--%>
                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formDOB"/>
                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formDOBDetais"/>:</b>
                                                        </td>
                                                        <td align="left" nowrap><input type="text" placeholder="yyyy"
                                                                                       name="year_of_birth" <%=getDisabled("year_of_birth")%>
                                                                                       value="<%=birthYear%>"
                                                                                       size="3" maxlength="4">

                                                            <%
                                                                String sbMonth;
                                                                String sbDay;
                                                                DecimalFormat dFormat = new DecimalFormat("00");
                                                            %>
                                                            <select name="month_of_birth" id="month_of_birth">
                                                                <% for (int i = 1; i <= 12; i++) {
                                                                    sbMonth = dFormat.format(i); %>
                                                                <option value="<%=sbMonth%>"<%=birthMonth.equals(sbMonth) ? " selected" : ""%>><%=sbMonth%>
                                                                </option>
                                                                <%} %>
                                                            </select>

                                                            <select name="date_of_birth" id="date_of_birth">
                                                                <% for (int i = 1; i <= 31; i++) {
                                                                    sbDay = dFormat.format(i); %>
                                                                <option value="<%=sbDay%>"<%=birthDate.equals(sbDay) ? " selected" : ""%>><%=sbDay%>
                                                                </option>
                                                                <%} %>
                                                            </select>

                                                            <label for="age">Age:</label>
                                                            <input type="text" name="age" id="age"
                                                                   value="<%=demographic.getAgeAsOf(new Date())%>"
                                                                   readonly>

                                                        </td>
                                                        <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formSex"/>:</b>
                                                        </td>
                                                        <td><select name="sex" id="sex">
                                                            <option value=""></option>
                                                            <% for (Gender gn : Gender.values()) { %>
                                                            <option value=<%=gn.name()%> <%=((demographic.getSex().toUpperCase().equals(gn.name())) ? " selected=\"selected\" " : "") %>><%=gn.getText()%>
                                                            </option>
                                                            <% } %>
                                                        </select>
                                                        </td>
                                                    </tr>

                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formHin"/>: </b>
                                                        </td>
                                                        <td align="left" nowrap><input type="text" name="hin"
                                                                                       id="hinBox" <%=getDisabled("hin")%>
                                                                                       value="<%=StringUtils.trimToEmpty(demographic.getHin())%>"
                                                                                       size="17">
                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formVer"/><input
                                                                    type="text" name="ver"
                                                                    style="width:20% !important;" <%=getDisabled("ver")%>
                                                                    value="<%=StringUtils.trimToEmpty(demographic.getVer())%>"
                                                                    onBlur="upCaseCtrl(this)" id="verBox">
                                                            <%if ("online".equals(oscarProps.getProperty("hcv.type", "simple"))) { %>
                                                            <input type="button" value="Validate"
                                                                   onClick="validateHC()"/>
                                                            <% } %>
                                                        </td>
                                                        <td align="right">
                                                            <b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formEFFDate"/>:</b>
                                                        </td>
                                                        <td align="left">
                                                            <%
                                                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                                                                String effDate = null;
                                                                if (demographic.getEffDate() != null) {
                                                                    effDate = StringUtils.trimToNull(sdf.format(demographic.getEffDate()));
                                                                }
                                                                // Put 0 on the left on dates
                                                                DecimalFormat decF = new DecimalFormat();
                                                                String effDateYear = "";
                                                                String effDateMonth = "";
                                                                String effDateDay = "";
                                                                if (effDate != null) {
                                                                    // Year
                                                                    decF.applyPattern("0000");
                                                                    effDateYear = decF.format(MyDateFormat.getYearFromStandardDate(effDate));
                                                                    // Month and Day
                                                                    decF.applyPattern("00");
                                                                    effDateMonth = decF.format(MyDateFormat.getMonthFromStandardDate(effDate));
                                                                    effDateDay = decF.format(MyDateFormat.getDayFromStandardDate(effDate));
                                                                }
                                                            %> <input type="text" placeholder="yyyy"
                                                                      name="eff_date_year" <%=getDisabled("eff_date_year")%>
                                                                      size="4" maxlength="4" value="<%= effDateYear%>">
                                                            <input
                                                                    type="text" placeholder="mm" name="eff_date_month"
                                                                    size="2"
                                                                    maxlength="2" <%=getDisabled("eff_date_month")%>
                                                                    value="<%= effDateMonth%>"> <input type="text"
                                                                                                       placeholder="dd"
                                                                                                       name="eff_date_date"
                                                                                                       size="2"
                                                                                                       maxlength="2" <%=getDisabled("eff_date_date")%>
                                                                                                       value="<%= effDateDay%>">
                                                        </td>
                                                    </tr>
                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formHCType"/>:</b>
                                                        </td>
                                                        <td align="left">

                                                            <%
                                                                String hctype = demographic.getHcType() == null ? "" : demographic.getHcType(); %>
                                                            <select name="hc_type"
                                                                    style="width: 200px" <%=getDisabled("hc_type")%>>
                                                                <option value="OT"
                                                                        <%=(hctype.equals("OT") || hctype.equals("") || hctype.length() > 2) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optOther"/></option>
                                                                <% if (pNames.isDefined()) {
                                                                    for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                                                        province = (String) li.next(); %>
                                                                <option value="<%=province%>"
                                                                        <%=province.equals(hctype) ? " selected" : ""%>><%=li.next()%>
                                                                </option>
                                                                <% } %>
                                                                <% } else { %>
                                                                <option value="AB" <%=hctype.equals("AB") ? " selected" : ""%>>
                                                                    AB-Alberta
                                                                </option>
                                                                <option value="BC" <%=hctype.equals("BC") ? " selected" : ""%>>
                                                                    BC-British Columbia
                                                                </option>
                                                                <option value="MB" <%=hctype.equals("MB") ? " selected" : ""%>>
                                                                    MB-Manitoba
                                                                </option>
                                                                <option value="NB" <%=hctype.equals("NB") ? " selected" : ""%>>
                                                                    NB-New Brunswick
                                                                </option>
                                                                <option value="NL" <%=hctype.equals("NL") ? " selected" : ""%>>
                                                                    NL-Newfoundland & Labrador
                                                                </option>
                                                                <option value="NT" <%=hctype.equals("NT") ? " selected" : ""%>>
                                                                    NT-Northwest Territory
                                                                </option>
                                                                <option value="NS" <%=hctype.equals("NS") ? " selected" : ""%>>
                                                                    NS-Nova Scotia
                                                                </option>
                                                                <option value="NU" <%=hctype.equals("NU") ? " selected" : ""%>>
                                                                    NU-Nunavut
                                                                </option>
                                                                <option value="ON" <%=hctype.equals("ON") ? " selected" : ""%>>
                                                                    ON-Ontario
                                                                </option>
                                                                <option value="PE" <%=hctype.equals("PE") ? " selected" : ""%>>
                                                                    PE-Prince Edward Island
                                                                </option>
                                                                <option value="QC" <%=hctype.equals("QC") ? " selected" : ""%>>
                                                                    QC-Quebec
                                                                </option>
                                                                <option value="SK" <%=hctype.equals("SK") ? " selected" : ""%>>
                                                                    SK-Saskatchewan
                                                                </option>
                                                                <option value="YT" <%=hctype.equals("YT") ? " selected" : ""%>>
                                                                    YT-Yukon
                                                                </option>
                                                                <option value="US" <%=hctype.equals("US") ? " selected" : ""%>>
                                                                    US resident
                                                                </option>
                                                                <option value="US-AK" <%=hctype.equals("US-AK") ? " selected" : ""%>>
                                                                    US-AK-Alaska
                                                                </option>
                                                                <option value="US-AL" <%=hctype.equals("US-AL") ? " selected" : ""%>>
                                                                    US-AL-Alabama
                                                                </option>
                                                                <option value="US-AR" <%=hctype.equals("US-AR") ? " selected" : ""%>>
                                                                    US-AR-Arkansas
                                                                </option>
                                                                <option value="US-AZ" <%=hctype.equals("US-AZ") ? " selected" : ""%>>
                                                                    US-AZ-Arizona
                                                                </option>
                                                                <option value="US-CA" <%=hctype.equals("US-CA") ? " selected" : ""%>>
                                                                    US-CA-California
                                                                </option>
                                                                <option value="US-CO" <%=hctype.equals("US-CO") ? " selected" : ""%>>
                                                                    US-CO-Colorado
                                                                </option>
                                                                <option value="US-CT" <%=hctype.equals("US-CT") ? " selected" : ""%>>
                                                                    US-CT-Connecticut
                                                                </option>
                                                                <option value="US-CZ" <%=hctype.equals("US-CZ") ? " selected" : ""%>>
                                                                    US-CZ-Canal Zone
                                                                </option>
                                                                <option value="US-DC" <%=hctype.equals("US-DC") ? " selected" : ""%>>
                                                                    US-DC-District Of Columbia
                                                                </option>
                                                                <option value="US-DE" <%=hctype.equals("US-DE") ? " selected" : ""%>>
                                                                    US-DE-Delaware
                                                                </option>
                                                                <option value="US-FL" <%=hctype.equals("US-FL") ? " selected" : ""%>>
                                                                    US-FL-Florida
                                                                </option>
                                                                <option value="US-GA" <%=hctype.equals("US-GA") ? " selected" : ""%>>
                                                                    US-GA-Georgia
                                                                </option>
                                                                <option value="US-GU" <%=hctype.equals("US-GU") ? " selected" : ""%>>
                                                                    US-GU-Guam
                                                                </option>
                                                                <option value="US-HI" <%=hctype.equals("US-HI") ? " selected" : ""%>>
                                                                    US-HI-Hawaii
                                                                </option>
                                                                <option value="US-IA" <%=hctype.equals("US-IA") ? " selected" : ""%>>
                                                                    US-IA-Iowa
                                                                </option>
                                                                <option value="US-ID" <%=hctype.equals("US-ID") ? " selected" : ""%>>
                                                                    US-ID-Idaho
                                                                </option>
                                                                <option value="US-IL" <%=hctype.equals("US-IL") ? " selected" : ""%>>
                                                                    US-IL-Illinois
                                                                </option>
                                                                <option value="US-IN" <%=hctype.equals("US-IN") ? " selected" : ""%>>
                                                                    US-IN-Indiana
                                                                </option>
                                                                <option value="US-KS" <%=hctype.equals("US-KS") ? " selected" : ""%>>
                                                                    US-KS-Kansas
                                                                </option>
                                                                <option value="US-KY" <%=hctype.equals("US-KY") ? " selected" : ""%>>
                                                                    US-KY-Kentucky
                                                                </option>
                                                                <option value="US-LA" <%=hctype.equals("US-LA") ? " selected" : ""%>>
                                                                    US-LA-Louisiana
                                                                </option>
                                                                <option value="US-MA" <%=hctype.equals("US-MA") ? " selected" : ""%>>
                                                                    US-MA-Massachusetts
                                                                </option>
                                                                <option value="US-MD" <%=hctype.equals("US-MD") ? " selected" : ""%>>
                                                                    US-MD-Maryland
                                                                </option>
                                                                <option value="US-ME" <%=hctype.equals("US-ME") ? " selected" : ""%>>
                                                                    US-ME-Maine
                                                                </option>
                                                                <option value="US-MI" <%=hctype.equals("US-MI") ? " selected" : ""%>>
                                                                    US-MI-Michigan
                                                                </option>
                                                                <option value="US-MN" <%=hctype.equals("US-MN") ? " selected" : ""%>>
                                                                    US-MN-Minnesota
                                                                </option>
                                                                <option value="US-MO" <%=hctype.equals("US-MO") ? " selected" : ""%>>
                                                                    US-MO-Missouri
                                                                </option>
                                                                <option value="US-MS" <%=hctype.equals("US-MS") ? " selected" : ""%>>
                                                                    US-MS-Mississippi
                                                                </option>
                                                                <option value="US-MT" <%=hctype.equals("US-MT") ? " selected" : ""%>>
                                                                    US-MT-Montana
                                                                </option>
                                                                <option value="US-NC" <%=hctype.equals("US-NC") ? " selected" : ""%>>
                                                                    US-NC-North Carolina
                                                                </option>
                                                                <option value="US-ND" <%=hctype.equals("US-ND") ? " selected" : ""%>>
                                                                    US-ND-North Dakota
                                                                </option>
                                                                <option value="US-NE" <%=hctype.equals("US-NE") ? " selected" : ""%>>
                                                                    US-NE-Nebraska
                                                                </option>
                                                                <option value="US-NH" <%=hctype.equals("US-NH") ? " selected" : ""%>>
                                                                    US-NH-New Hampshire
                                                                </option>
                                                                <option value="US-NJ" <%=hctype.equals("US-NJ") ? " selected" : ""%>>
                                                                    US-NJ-New Jersey
                                                                </option>
                                                                <option value="US-NM" <%=hctype.equals("US-NM") ? " selected" : ""%>>
                                                                    US-NM-New Mexico
                                                                </option>
                                                                <option value="US-NU" <%=hctype.equals("US-NU") ? " selected" : ""%>>
                                                                    US-NU-Nunavut
                                                                </option>
                                                                <option value="US-NV" <%=hctype.equals("US-NV") ? " selected" : ""%>>
                                                                    US-NV-Nevada
                                                                </option>
                                                                <option value="US-NY" <%=hctype.equals("US-NY") ? " selected" : ""%>>
                                                                    US-NY-New York
                                                                </option>
                                                                <option value="US-OH" <%=hctype.equals("US-OH") ? " selected" : ""%>>
                                                                    US-OH-Ohio
                                                                </option>
                                                                <option value="US-OK" <%=hctype.equals("US-OK") ? " selected" : ""%>>
                                                                    US-OK-Oklahoma
                                                                </option>
                                                                <option value="US-OR" <%=hctype.equals("US-OR") ? " selected" : ""%>>
                                                                    US-OR-Oregon
                                                                </option>
                                                                <option value="US-PA" <%=hctype.equals("US-PA") ? " selected" : ""%>>
                                                                    US-PA-Pennsylvania
                                                                </option>
                                                                <option value="US-PR" <%=hctype.equals("US-PR") ? " selected" : ""%>>
                                                                    US-PR-Puerto Rico
                                                                </option>
                                                                <option value="US-RI" <%=hctype.equals("US-RI") ? " selected" : ""%>>
                                                                    US-RI-Rhode Island
                                                                </option>
                                                                <option value="US-SC" <%=hctype.equals("US-SC") ? " selected" : ""%>>
                                                                    US-SC-South Carolina
                                                                </option>
                                                                <option value="US-SD" <%=hctype.equals("US-SD") ? " selected" : ""%>>
                                                                    US-SD-South Dakota
                                                                </option>
                                                                <option value="US-TN" <%=hctype.equals("US-TN") ? " selected" : ""%>>
                                                                    US-TN-Tennessee
                                                                </option>
                                                                <option value="US-TX" <%=hctype.equals("US-TX") ? " selected" : ""%>>
                                                                    US-TX-Texas
                                                                </option>
                                                                <option value="US-UT" <%=hctype.equals("US-UT") ? " selected" : ""%>>
                                                                    US-UT-Utah
                                                                </option>
                                                                <option value="US-VA" <%=hctype.equals("US-VA") ? " selected" : ""%>>
                                                                    US-VA-Virginia
                                                                </option>
                                                                <option value="US-VI" <%=hctype.equals("US-VI") ? " selected" : ""%>>
                                                                    US-VI-Virgin Islands
                                                                </option>
                                                                <option value="US-VT" <%=hctype.equals("US-VT") ? " selected" : ""%>>
                                                                    US-VT-Vermont
                                                                </option>
                                                                <option value="US-WA" <%=hctype.equals("US-WA") ? " selected" : ""%>>
                                                                    US-WA-Washington
                                                                </option>
                                                                <option value="US-WI" <%=hctype.equals("US-WI") ? " selected" : ""%>>
                                                                    US-WI-Wisconsin
                                                                </option>
                                                                <option value="US-WV" <%=hctype.equals("US-WV") ? " selected" : ""%>>
                                                                    US-WV-West Virginia
                                                                </option>
                                                                <option value="US-WY" <%=hctype.equals("US-WY") ? " selected" : ""%>>
                                                                    US-WY-Wyoming
                                                                </option>
                                                                <% } %>
                                                            </select>
                                                        </td>

                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formHCRenewDate"/>:</b>
                                                        </td>
                                                        <td align="left">
                                                            <%
                                                                // Put 0 on the left on dates
                                                                // Year
                                                                decF.applyPattern("0000");

                                                                GregorianCalendar hcRenewalCal = new GregorianCalendar();
                                                                String renewDateYear = "";
                                                                String renewDateMonth = "";
                                                                String renewDateDay = "";
                                                                if (demographic.getHcRenewDate() != null) {
                                                                    hcRenewalCal.setTime(demographic.getHcRenewDate());
                                                                    renewDateYear = decF.format(hcRenewalCal.get(GregorianCalendar.YEAR));
                                                                    // Month and Day
                                                                    decF.applyPattern("00");
                                                                    renewDateMonth = decF.format(hcRenewalCal.get(GregorianCalendar.MONTH) + 1);
                                                                    renewDateDay = decF.format(hcRenewalCal.get(GregorianCalendar.DAY_OF_MONTH));
                                                                }

                                                            %>
                                                            <input type="text" placeholder="yyyy"
                                                                   name="hc_renew_date_year" size="4" maxlength="4"
                                                                   value="<%=renewDateYear%>" <%=getDisabled("hc_renew_date_year")%>>
                                                            <input type="text" placeholder="mm"
                                                                   name="hc_renew_date_month" size="2" maxlength="2"
                                                                   value="<%=renewDateMonth%>" <%=getDisabled("hc_renew_date_month")%>>
                                                            <input type="text" placeholder="dd"
                                                                   name="hc_renew_date_date" size="2" maxlength="2"
                                                                   value="<%=renewDateDay%>" <%=getDisabled("hc_renew_date_date")%>>
                                                        </td>
                                                    </tr>
                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgCountryOfOrigin"/>: </b>
                                                        </td>
                                                        <td align="left"><select id="countryOfOrigin"
                                                                                 name="countryOfOrigin"
                                                                                 style="width: 200px;" <%=getDisabled("countryOfOrigin")%>>
                                                            <option value="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgNotSet"/></option>
                                                            <%for (CountryCode cc : countryList) { %>
                                                            <option value="<%=cc.getCountryId()%>"
                                                                    <% if (ca.openosp.openo.util.StringUtils.noNull(demographic.getCountryOfOrigin()).equals(cc.getCountryId())) {
                                                                        out.print("SELECTED");
                                                                    }%>><%=cc.getCountryName() %>
                                                            </option>
                                                            <%}%>
                                                        </select></td>
                                                        <td><!-- padding --></td>
                                                        <td><!-- padding --></td>
                                                    </tr>
                                                    <tr valign="top">
                                                        <td align="right"><b>SIN:</b></td>
                                                        <td align="left"><input type="text" name="sin"
                                                                                size="30" <%=getDisabled("sin")%>
                                                                                value="<%=(demographic.getSin()==null||demographic.getSin().equals("null"))?"":demographic.getSin()%>">
                                                        </td>
                                                        <td align="right" nowrap><b> <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.cytolNum"/>:</b>
                                                        </td>
                                                        <td><input type="text"
                                                                   name="cytolNum" <%=getDisabled("cytolNum")%>
                                                                   style="display: inline; width: auto;"
                                                                   value="<%=StringUtils.trimToEmpty(demoExt.get("cytolNum"))%>">
                                                            <input type="hidden" name="cytolNumOrig"
                                                                   value="<%=StringUtils.trimToEmpty(demoExt.get("cytolNum"))%>"/>
                                                        </td>
                                                    </tr>
                                                        <%-- TOGGLE FIRST NATIONS MODULE --%>
                                                    <oscar:oscarPropertiesCheck value="true" defaultVal="false"
                                                                                property="FIRST_NATIONS_MODULE">
                                                        <tr>
                                                            <td colspan="8">
                                                                <jsp:include page="manageFirstNationsModule.jsp">
                                                                    <jsp:param name="demo"
                                                                               value="<%= demographic_no %>"/>
                                                                </jsp:include>
                                                            </td>
                                                        </tr>
                                                    </oscar:oscarPropertiesCheck>
                                                        <%-- END TOGGLE FIRST NATIONS MODULE --%>
                                                        <%-- TOGGLE OFF PATIENT CLINIC STATUS --%>
                                                    <oscar:oscarPropertiesCheck
                                                            property="DEMOGRAPHIC_PATIENT_CLINIC_STATUS" value="true">

                                                        <tr valign="top">
                                                            <td align="right" nowrap><b>
                                                                <% if (oscarProps.getProperty("demographicLabelDoctor") != null) {
                                                                    out.print(oscarProps.getProperty("demographicLabelDoctor", ""));
                                                                } else { %>
                                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formMRP"/>
                                                                <% } %>: </b></td>
                                                            <td align="left"><select id="mrp"
                                                                                     name="provider_no" <%=getDisabled("provider_no")%>
                                                                                     style="width: 200px">
                                                                <option value=""></option>
                                                                <%
                                                                    for (Provider p : doctors) {

                                                                %>
                                                                <option value="<%=p.getProviderNo()%>"
                                                                        <%=p.getProviderNo().equals(demographic.getProviderNo()) ? "selected" : ""%>>
                                                                    <%=p.getLastName() + "," + p.getFirstName()%>
                                                                </option>
                                                                <% } %>
                                                            </select></td>
                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formNurse"/>: </b>
                                                            </td>
                                                            <td align="left"><select
                                                                    name="nurse" <%=getDisabled("nurse")%>
                                                                    style="width: 200px">
                                                                <option value=""></option>
                                                                <%


                                                                    for (Provider p : nurses) {
                                                                %>
                                                                <option value="<%=p.getProviderNo()%>"
                                                                        <%=p.getProviderNo().equals(nurse) ? "selected" : ""%>>
                                                                    <%=p.getLastName() + "," + p.getFirstName()%>
                                                                </option>
                                                                <% } %>
                                                            </select></td>
                                                        </tr>
                                                        <tr valign="top">
                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formMidwife"/>: </b>
                                                            </td>
                                                            <td align="left"><select
                                                                    name="midwife" <%=getDisabled("midwife")%>
                                                                    style="width: 200px">
                                                                <option value=""></option>
                                                                <%
                                                                    for (Provider p : midwifes) {
                                                                %>
                                                                <option value="<%=p.getProviderNo()%>"
                                                                        <%=p.getProviderNo().equals(midwife) ? "selected" : ""%>>
                                                                    <%=p.getLastName() + "," + p.getFirstName()%>
                                                                </option>
                                                                <% } %>
                                                            </select></td>
                                                            <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResident"/>:</b>
                                                            </td>
                                                            <td align="left"><select name="resident"
                                                                                     style="width: 200px" <%=getDisabled("resident")%>>
                                                                <option value=""></option>
                                                                <%
                                                                    for (Provider p : doctors) {
                                                                %>
                                                                <option value="<%=p.getProviderNo()%>"
                                                                        <%=p.getProviderNo().equals(resident) ? "selected" : ""%>>
                                                                    <%=p.getLastName() + "," + p.getFirstName()%>
                                                                </option>
                                                                <% } %>
                                                            </select></td>
                                                        </tr>

                                                        <tr valign="top">
                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formRefDoc"/>: </b>
                                                            </td>
                                                            <td align="left">
                                                                <% if (oscarProps.getProperty("isMRefDocSelectList", "").equals("true")) {
                                                                    // drop down list
                                                                    Properties prop = null;
                                                                    Vector vecRef = new Vector();
                                                                    List<ProfessionalSpecialist> specialists = professionalSpecialistDao.findAll();
                                                                    for (ProfessionalSpecialist specialist : specialists) {
                                                                        prop = new Properties();
                                                                        if (specialist != null && specialist.getReferralNo() != null && !specialist.getReferralNo().equals("")) {
                                                                            prop.setProperty("referral_no", specialist.getReferralNo());
                                                                            prop.setProperty("last_name", specialist.getLastName());
                                                                            prop.setProperty("first_name", specialist.getFirstName());
                                                                            vecRef.add(prop);
                                                                        }
                                                                    }

                                                                %> <select name="r_doctor" <%=getDisabled("r_doctor")%>
                                                                           onChange="changeRefDoc()"
                                                                           style="width: 200px">
                                                                <option value=""></option>
                                                                <% for (int k = 0; k < vecRef.size(); k++) {
                                                                    prop = (Properties) vecRef.get(k);
                                                                %>
                                                                <option
                                                                        value="<%=prop.getProperty("last_name")+","+prop.getProperty("first_name")%>"
                                                                        <%=prop.getProperty("referral_no").equals(rdohip) ? "selected" : ""%>>
                                                                    <%=prop.getProperty("last_name") + "," + prop.getProperty("first_name")%>
                                                                </option>
                                                                <% }

                                                                %>
                                                            </select>
                                                                <script type="text/javascript" language="Javascript">
                                                                    //<!--
                                                                    function changeRefDoc() {
//alert(document.updatedelete.r_doctor.value);
                                                                        var refName = document.updatedelete.r_doctor.options[document.updatedelete.r_doctor.selectedIndex].value;
                                                                        var refNo = "";
                                                                        <% for(int k=0; k<vecRef.size(); k++) {
  		prop= (Properties) vecRef.get(k);
  	%>
                                                                        if (refName == "<%=prop.getProperty("last_name")+","+prop.getProperty("first_name")%>") {
                                                                            refNo = '<%=prop.getProperty("referral_no", "")%>';
                                                                        }
                                                                        <% } %>
                                                                        document.updatedelete.r_doctor_ohip.value = refNo;
                                                                    }

                                                                    //-->
                                                                </script>
                                                                <% } else {%> <input type="text" name="r_doctor"
                                                                                     size="30"
                                                                                     maxlength="40" <%=getDisabled("r_doctor")%>
                                                                                     value="<%=rd%>"> <% } %>
                                                            </td>
                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formRefDocNo"/>: </b>
                                                            </td>
                                                            <td align="left"><input type="text"
                                                                                    name="r_doctor_ohip" <%=getDisabled("r_doctor_ohip")%>
                                                                                    size="20" maxlength="6"
                                                                                    value="<%=rdohip%>"> <% if ("ON".equals(prov)) { %>
                                                                <a
                                                                        href="javascript:referralScriptAttach2('r_doctor_ohip','r_doctor')"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnSearch"/>
                                                                    #</a> <% } %>
                                                            </td>
                                                        </tr>

                                                    </oscar:oscarPropertiesCheck>
                                                        <%-- END TOGGLE OFF PATIENT CLINIC STATUS --%>

                                                        <%-- TOGGLE OFF PATIENT ROSTERING - NOT USED IN ALL PROVINCES. --%>
                                                    <oscar:oscarPropertiesCheck property="DEMOGRAPHIC_PATIENT_ROSTERING"
                                                                                value="true">

                                                        <tr valign="top">
                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formRosterStatus"/>:
                                                            </b></td>
                                                            <td align="left">
                                                                <%
                                                                    String rosterStatus = demographic.getRosterStatus();
                                                                    if (rosterStatus == null) {
                                                                        rosterStatus = "";
                                                                    }
                                                                %>
                                                                <input type="hidden" name="initial_rosterstatus"
                                                                       value="<%=rosterStatus%>"/>
                                                                <select id="roster_status" name="roster_status"
                                                                        style="width: 120px;" <%=getDisabled("roster_status")%>
                                                                        onchange="checkRosterStatus2(); updateEnrolledTo();">
                                                                    <option value=""></option>
                                                                    <option value="RO"
                                                                            <%="RO".equals(rosterStatus) ? " selected" : ""%>>
                                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optRostered"/></option>
                                                                    <!--
									<option value="NR"
										<%=rosterStatus.equals("NR")?" selected":""%>>
									<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optNotRostered"/></option>
									-->
                                                                    <option value="TE"
                                                                            <%=rosterStatus.equals("TE") ? " selected" : ""%>>
                                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optTerminated"/></option>

                                                                    <option value="FS"
                                                                            <%=rosterStatus.equals("FS") ? " selected" : ""%>>
                                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optFeeService"/></option>
                                                                    <%
                                                                        for (String status : demographicDao.getRosterStatuses()) {
                                                                    %>
                                                                    <option
                                                                            <%=rosterStatus.equals(status) ? " selected" : ""%>><%=status%>
                                                                    </option>
                                                                    <% }

                                                                        // end while %>
                                                                </select>
                                                                <security:oscarSec roleName="<%=roleName$%>"
                                                                                   objectName="_admin.demographic"
                                                                                   rights="r" reverse="<%=false%>">
                                                                    <input type="button" onClick="newStatus1();"
                                                                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnAddNew"/>">
                                                                </security:oscarSec>
                                                            </td>
                                                            <%
                                                                // Put 0 on the left on dates
                                                                // Year
                                                                decF.applyPattern("0000");

                                                                GregorianCalendar dateCal = new GregorianCalendar();
                                                                String rosterDateYear = "";
                                                                String rosterDateMonth = "";
                                                                String rosterDateDay = "";
                                                                if (demographic.getRosterDate() != null) {
                                                                    dateCal.setTime(demographic.getRosterDate());
                                                                    rosterDateYear = decF.format(dateCal.get(GregorianCalendar.YEAR));
                                                                    // Month and Day
                                                                    decF.applyPattern("00");
                                                                    rosterDateMonth = decF.format(dateCal.get(GregorianCalendar.MONTH) + 1);
                                                                    rosterDateDay = decF.format(dateCal.get(GregorianCalendar.DAY_OF_MONTH));
                                                                }
                                                                String rosterTerminationDateYear = "";
                                                                String rosterTerminationDateMonth = "";
                                                                String rosterTerminationDateDay = "";
                                                                String rosterTerminationReason = "";
                                                                if (demographic.getRosterTerminationDate() != null) {
                                                                    dateCal.setTime(demographic.getRosterTerminationDate());
                                                                    rosterTerminationDateYear = decF.format(dateCal.get(GregorianCalendar.YEAR));
                                                                    // Month and Day
                                                                    decF.applyPattern("00");
                                                                    rosterTerminationDateMonth = decF.format(dateCal.get(GregorianCalendar.MONTH) + 1);
                                                                    rosterTerminationDateDay = decF.format(dateCal.get(GregorianCalendar.DAY_OF_MONTH));
                                                                }
                                                                rosterTerminationReason = demographic.getRosterTerminationReason();


                                                            %>

                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.DateJoined"/>: </b>
                                                            </td>
                                                            <td align="left">
                                                                <input type="text" name="roster_date_year" size="4"
                                                                       maxlength="4" value="<%=rosterDateYear%>">
                                                                <input type="text" name="roster_date_month" size="2"
                                                                       maxlength="2" value="<%=rosterDateMonth%>">
                                                                <input type="text" name="roster_date_day" size="2"
                                                                       maxlength="2" value="<%=rosterDateDay%>">
                                                            </td>
                                                        </tr>

                                                        <tr valign="top">
                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.RosterEnrolledTo"/>:
                                                            </b></td>
                                                            <td align="left">
                                                                <!-- select box here -->
                                                                <select id="enrolledTo"
                                                                        name="roster_enrolled_to" <%=getDisabled("roster_enrolled_to")%>
                                                                        style="width: 200px">
                                                                    <option value=""></option>
                                                                    <%
                                                                        for (Provider p : doctors) {

                                                                    %>
                                                                    <option value="<%=p.getProviderNo()%>"
                                                                            <%=p.getProviderNo().equals(demographic.getRosterEnrolledTo()) ? "selected" : ""%>>
                                                                        <%=p.getLastName() + "," + p.getFirstName()%>
                                                                    </option>
                                                                    <% } %>
                                                                </select>


                                                            </td>

                                                            <td align="right" nowrap><b></b></td>
                                                            <td align="left" colspan="3">

                                                            </td>
                                                        </tr>


                                                        <tr valign="top">

                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.RosterTerminationDate"/>: </b>
                                                            </td>
                                                            <td align="left">
                                                                <input type="text" placeholder="yyyy"
                                                                       name="roster_termination_date_year" size="4"
                                                                       maxlength="4"
                                                                       value="<%=rosterTerminationDateYear%>">
                                                                <input type="text" placeholder="mm"
                                                                       name="roster_termination_date_month" size="2"
                                                                       maxlength="2"
                                                                       value="<%=rosterTerminationDateMonth%>">
                                                                <input type="text" placeholder="dd"
                                                                       name="roster_termination_date_day" size="2"
                                                                       maxlength="2"
                                                                       value="<%=rosterTerminationDateDay%>">
                                                            </td>


                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.RosterTerminationReason"/>: </b>
                                                            </td>
                                                            <td align="left" colspan="3">
                                                                <select name="roster_termination_reason"
                                                                        style="width: 200px;">
                                                                    <option value="">N/A</option>
                                                                    <%for (String code : Util.rosterTermReasonProperties.getTermReasonCodes()) { %>
                                                                    <option value="<%=code %>" <%=code.equals(rosterTerminationReason) ? "selected" : "" %> ><%=Util.rosterTermReasonProperties.getReasonByCode(code) %>
                                                                    </option>
                                                                    <%} %>
                                                                </select>
                                                            </td>
                                                        </tr>

                                                    </oscar:oscarPropertiesCheck>
                                                        <%-- END TOGGLE OFF PATIENT ROSTERING --%>


                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPatientStatus"/>:</b>
                                                            <b> </b></td>
                                                        <td align="left">
                                                            <%
                                                                String patientStatus = demographic.getPatientStatus();
                                                                if (patientStatus == null) patientStatus = "";%>
                                                            <input type="hidden" name="initial_patientstatus"
                                                                   value="<%=patientStatus%>">
                                                            <select name="patient_status"
                                                                    style="width: 120px" <%=getDisabled("patient_status")%>
                                                                    onChange="updatePatientStatusDate()">
                                                                <option value="AC"
                                                                        <%="AC".equals(patientStatus) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optActive"/></option>
                                                                <option value="IN"
                                                                        <%="IN".equals(patientStatus) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optInActive"/></option>
                                                                <option value="DE"
                                                                        <%="DE".equals(patientStatus) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optDeceased"/></option>
                                                                <option value="MO"
                                                                        <%="MO".equals(patientStatus) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optMoved"/></option>
                                                                <option value="FI"
                                                                        <%="FI".equals(patientStatus) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optFired"/></option>
                                                                <%
                                                                    for (String status : demographicDao.search_ptstatus()) {
                                                                %>
                                                                <option
                                                                        <%=status.equals(patientStatus) ? " selected" : ""%>><%=status%>
                                                                </option>
                                                                <% }

                                                                    // end while %>
                                                            </select>
                                                            <security:oscarSec roleName="<%=roleName$%>"
                                                                               objectName="_admin.demographic"
                                                                               rights="r" reverse="<%=false%>">
                                                                <input type="button" onClick="newStatus();"
                                                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnAddNew"/>">
                                                            </security:oscarSec>
                                                        </td>

                                                        <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.PatientStatusDate"/>: </b>
                                                        </td>
                                                        <td align="left">
                                                            <%
                                                                decF.applyPattern("0000");

                                                                GregorianCalendar dateCal = new GregorianCalendar();
                                                                String patientStatusDateYear = "";
                                                                String patientStatusDateMonth = "";
                                                                String patientStatusDateDay = "";
                                                                if (demographic.getPatientStatusDate() != null) {
                                                                    dateCal.setTime(demographic.getPatientStatusDate());
                                                                    patientStatusDateYear = decF.format(dateCal.get(GregorianCalendar.YEAR));
                                                                    // Month and Day
                                                                    decF.applyPattern("00");
                                                                    patientStatusDateMonth = decF.format(dateCal.get(GregorianCalendar.MONTH) + 1);
                                                                    patientStatusDateDay = decF.format(dateCal.get(GregorianCalendar.DAY_OF_MONTH));
                                                                }
                                                            %>
                                                            <input type="text" placeholder="yyyy"
                                                                   name="patientstatus_date_year" size="4" maxlength="4"
                                                                   value="<%=patientStatusDateYear%>">
                                                            <input type="text" placeholder="mm"
                                                                   name="patientstatus_date_month" size="2"
                                                                   maxlength="2" value="<%=patientStatusDateMonth%>">
                                                            <input type="text" placeholder="dd"
                                                                   name="patientstatus_date_day" size="2" maxlength="2"
                                                                   value="<%=patientStatusDateDay%>">
                                                        </td>
                                                    </tr>
                                                    <tr>
                                                        <%if (!"true".equals(OscarProperties.getInstance().getProperty("phu.hide", "false"))) { %>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPHU"/>:</b>
                                                        </td>
                                                        <td align="left">
                                                            <select id="PHU" name="PHU">
                                                                <option value="">Select Below</option>
                                                                <%
                                                                    if (phuLookupList != null) {
                                                                        for (LookupListItem llItem : phuLookupList.getItems()) {
                                                                            if (llItem.isActive()) {
                                                                                String selected = "";
                                                                                if (llItem.getValue().equals(StringUtils.trimToEmpty(demoExt.get("PHU")))) {
                                                                                    selected = " selected=\"selected\" ";
                                                                                }
                                                                %>
                                                                <option value="<%=llItem.getValue()%>" <%=selected%>><%=llItem.getLabel()%>
                                                                </option>
                                                                <%
                                                                            }
                                                                        }
                                                                    }

                                                                %>
                                                            </select>
                                                        </td>
                                                        <% } else { %>
                                                        <td align="right"></td>
                                                        <td align="left"><input type="hidden" name="PHU" value=""/></td>
                                                        <% } %>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formChartNo"/>:</b>
                                                        </td>
                                                        <td align="left"><input type="text" name="chart_no"
                                                                                size="30"
                                                                                value="<%=StringUtils.trimToEmpty(demographic.getChartNo())%>" <%=getDisabled("chart_no")%>>
                                                        </td>
                                                    </tr>

                                                    <tr>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="web.record.details.archivedPaperChart"/>: </b></td>
                                                        <td align="left">
                                                            <%
                                                                String paperChartIndicator = StringUtils.trimToEmpty(demoExt.get("paper_chart_archived"));
                                                                String paperChartIndicatorDate = StringUtils.trimToEmpty(demoExt.get("paper_chart_archived_date"));
                                                                String paperChartIndicatorProgram = StringUtils.trimToEmpty(demoExt.get("paper_chart_archived_program"));
                                                            %>
                                                            <select name="paper_chart_archived"
                                                                    id="paper_chart_archived" <%=getDisabled("paper_chart_archived")%>
                                                                    onChange="updatePaperArchive()">
                                                                <option value="" <%="".equals(paperChartIndicator) ? " selected" : ""%>>
                                                                </option>
                                                                <option value="NO" <%="NO".equals(paperChartIndicator) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.paperChartIndicator.no"/>
                                                                </option>
                                                                <option value="YES"    <%="YES".equals(paperChartIndicator) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.paperChartIndicator.yes"/>
                                                                </option>
                                                            </select>

                                                            <input type="text" placeholder="yyyy-mm-dd"
                                                                   name="paper_chart_archived_date"
                                                                   id="paper_chart_archived_date"
                                                                   value="<%=paperChartIndicatorDate%>">
                                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="archive_date_cal">

                                                            <input type="hidden" name="paper_chart_archived_program"
                                                                   id="paper_chart_archived_program"
                                                                   value="<%=paperChartIndicatorProgram%>"/>
                                                        </td>
                                                        <td><!-- padding --></td>
                                                        <td><!-- padding --></td>
                                                    </tr>
                                                        <%--
						THE "PATIENT JOINED DATE" ROW HAS NOT BEEN ADDED TWICE IN ERROR
						IT IS PLACED HERE FOR REPOSITIONING WHEN THE WAITING LIST
						MODULE IS ACTIVE.
						THIS WAY WILL MAKE EVERYONE HAPPY.
					--%>
                                                    <tr valign="top">
                                                        <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formDateJoined1"/>:
                                                        </b></td>
                                                        <td align="left">
                                                            <%

                                                                String date_joined = demographic.getDateJoined() != null ? sdf.format(demographic.getDateJoined()) : null;
                                                                String dateJoinedYear = "";
                                                                String dateJoinedMonth = "";
                                                                String dateJoinedDay = "";
                                                                if (date_joined != null && date_joined.length() == 10) {
                                                                    // Format year
                                                                    decF.applyPattern("0000");
                                                                    dateJoinedYear = decF.format(MyDateFormat.getYearFromStandardDate(date_joined));
                                                                    decF.applyPattern("00");
                                                                    dateJoinedMonth = decF.format(MyDateFormat.getMonthFromStandardDate(date_joined));
                                                                    dateJoinedDay = decF.format(MyDateFormat.getDayFromStandardDate(date_joined));
                                                                }
                                                            %> <input type="text"
                                                                      name="date_joined_year" size="4" maxlength="4"
                                                                      placeholder="yyyy"
                                                                      value="<%= dateJoinedYear %>"> <input type="text"
                                                                                                            placeholder="mm"
                                                                                                            name="date_joined_month"
                                                                                                            size="2"
                                                                                                            maxlength="2"
                                                                                                            value="<%= dateJoinedMonth %>">
                                                            <input type="text" placeholder="dd"
                                                                   name="date_joined_date" size="2" maxlength="2"
                                                                   value="<%= dateJoinedDay %>"></td>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formEndDate"/>: </b>
                                                        </td>
                                                        <td align="left">
                                                            <%
                                                                String endDate = null;
                                                                if (demographic.getEndDate() != null) {
                                                                    endDate = sdf.format(demographic.getEndDate());
                                                                }
                                                                String endYear = "";
                                                                String endMonth = "";
                                                                String endDay = "";

                                                                if (endDate != null) {
                                                                    // Format year
                                                                    decF.applyPattern("0000");
                                                                    endYear = decF.format(MyDateFormat.getYearFromStandardDate(endDate));
                                                                    decF.applyPattern("00");
                                                                    endMonth = decF.format(MyDateFormat.getMonthFromStandardDate(endDate));
                                                                    endDay = decF.format(MyDateFormat.getDayFromStandardDate(endDate));
                                                                }
                                                            %> <input type="text" name="end_date_year"
                                                                      placeholder="yyyy"
                                                                      size="4" maxlength="4" value="<%= endYear %>">
                                                            <input placeholder="mm"
                                                                   type="text" name="end_date_month" size="2"
                                                                   maxlength="2"
                                                                   value="<%= endMonth %>"> <input type="text"
                                                                                                   placeholder="dd"
                                                                                                   name="end_date_date"
                                                                                                   size="2"
                                                                                                   maxlength="2"
                                                                                                   value="<%= endDay %>">
                                                        </td>
                                                    </tr>
                                                        <%-- END MOVE PATIENT JOINED DATE --%>

                                                    <% // customized key + "Has Primary Care Physician" & "Employment Status"
                                                        if (hasHasPrimary || hasEmpStatus) {
                                                    %>
                                                    <tr valign="top">
                                                        <% if (hasHasPrimary) {
                                                        %>
                                                        <td style="text-align: right;">
                                                            <b><%=hasPrimary.replace(" ", "&nbsp;")%>:</b></td>
                                                        <td style="text-align: left;">
                                                            <select name="<%=hasPrimary.replace(" ", "")%>">
                                                                <option value="N/A" <%="N/A".equals(hasPrimaryCarePhysician) ? "selected" : ""%>>
                                                                    N/A
                                                                </option>
                                                                <option value="Yes" <%="Yes".equals(hasPrimaryCarePhysician) ? "selected" : ""%>>
                                                                    Yes
                                                                </option>
                                                                <option value="No" <%="No".equals(hasPrimaryCarePhysician) ? "selected" : ""%>>
                                                                    No
                                                                </option>
                                                            </select>
                                                        </td>
                                                        <% }
                                                            if (hasEmpStatus) {
                                                        %>
                                                        <td style="text-align: right;">
                                                            <b><%=empStatus.replace(" ", "&nbsp;")%>:</b></td>
                                                        <td style="text-align: left;">
                                                            <select name="<%=empStatus.replace(" ", "")%>">
                                                                <option value="N/A" <%="N/A".equals(employmentStatus) ? "selected" : ""%>>
                                                                    N/A
                                                                </option>
                                                                <option value="FULL TIME" <%="FULL TIME".equals(employmentStatus) ? "selected" : ""%>>
                                                                    FULL TIME
                                                                </option>
                                                                <option value="ODSP" <%="ODSP".equals(employmentStatus) ? "selected" : ""%>>
                                                                    ODSP
                                                                </option>
                                                                <option value="OW" <%="OW".equals(employmentStatus) ? "selected" : ""%>>
                                                                    OW
                                                                </option>
                                                                <option value="PART TIME" <%="PART TIME".equals(employmentStatus) ? "selected" : ""%>>
                                                                    PART TIME
                                                                </option>
                                                                <option value="UNEMPLOYED" <%="UNEMPLOYED".equals(employmentStatus) ? "selected" : ""%>>
                                                                    UNEMPLOYED
                                                                </option>
                                                            </select>
                                                        </td>
                                                    </tr>
                                                    <% }
                                                    }
                                                        if (hasDemoExt) {
                                                            boolean bExtForm = oscarProps.getProperty("demographicExtForm") != null ? true : false;
                                                            String[] propDemoExtForm = bExtForm ? (oscarProps.getProperty("demographicExtForm", "").split("\\|")) : null;
                                                            for (int k = 0; k < propDemoExt.length; k = k + 2) {
                                                    %>
                                                    <tr valign="top">
                                                        <td align="right"><b><%=Encode.forHtmlContent(propDemoExt[k])%>
                                                            : </b></td>
                                                        <td align="left">
                                                            <% if (bExtForm) {
                                                                if (propDemoExtForm[k].indexOf("<select") >= 0) {
                                                                    out.println(propDemoExtForm[k].replaceAll("value=\"" + StringUtils.trimToEmpty(demoExt.get(propDemoExt[k].replace(' ', '_'))) + "\"", "value=\"" + StringUtils.trimToEmpty(demoExt.get(propDemoExt[k].replace(' ', '_'))) + "\"" + " selected"));
                                                                } else {
                                                                    out.println(propDemoExtForm[k].replaceAll("value=\"\"", "value=\"" + StringUtils.trimToEmpty(demoExt.get(propDemoExt[k].replace(' ', '_'))) + "\""));
                                                                }
                                                            } else { %>
                                                            <input type="text"
                                                                   name="<%= Encode.forHtmlAttribute(propDemoExt[k].replace(' ', '_')) %>"
                                                                   value="<%=Encode.forHtmlAttribute(StringUtils.trimToEmpty(demoExt.get(propDemoExt[k].replace(' ', '_'))))%>"/>
                                                            <% } %>
                                                            <input type="hidden"
                                                                   name="<%=propDemoExt[k].replace(' ', '_')%>Orig"
                                                                   value="<%=StringUtils.trimToEmpty(demoExt.get(propDemoExt[k].replace(' ', '_')))%>"/>
                                                        </td>
                                                        <% if ((k + 1) < propDemoExt.length) { %>
                                                        <td align="right"><b>
                                                            <%out.println(Encode.forHtmlContent(propDemoExt[k + 1]) + ":");%></b>
                                                        </td>
                                                        <td align="left">
                                                            <% if (bExtForm) {
                                                                if (propDemoExtForm[k + 1].indexOf("<select") >= 0) {
                                                                    out.println(propDemoExtForm[k + 1].replaceAll("value=\"" + StringUtils.trimToEmpty(demoExt.get(propDemoExt[k + 1].replace(' ', '_'))) + "\"", "value=\"" + StringUtils.trimToEmpty(demoExt.get(propDemoExt[k + 1].replace(' ', '_'))) + "\"" + " selected"));
                                                                } else {
                                                                    out.println(propDemoExtForm[k + 1].replaceAll("value=\"\"", "value=\"" + StringUtils.trimToEmpty(demoExt.get(propDemoExt[k + 1].replace(' ', '_'))) + "\""));
                                                                }
                                                            } else { %> <input type="text"
                                                                               name="<%=Encode.forHtmlAttribute(propDemoExt[k+1].replace(' ', '_'))%>"
                                                                               value="<%=Encode.forHtmlAttribute(StringUtils.trimToEmpty(demoExt.get(propDemoExt[k+1].replace(' ', '_'))))%>"/>
                                                            <% } %> <input type="hidden"
                                                                           name="<%=Encode.forHtmlAttribute(propDemoExt[k+1].replace(' ', '_'))%>Orig"
                                                                           value="<%=Encode.forHtmlAttribute(StringUtils.trimToEmpty(demoExt.get(propDemoExt[k+1].replace(' ', '_'))))%>"/>
                                                        </td>
                                                        <% } else {%>
                                                        <td>&nbsp;</td>
                                                        <td>&nbsp;</td>
                                                        <% } %>
                                                    </tr>
                                                    <% }
                                                    } %>

                                                        <%-- TOGGLE PATIENT PRIVACY CONSENT --%>
                                                    <oscar:oscarPropertiesCheck property="privateConsentEnabled"
                                                                                value="true">

                                                        <tr valign="top">
                                                            <td colspan="4">
                                                                <table id="privacyConsentTable">
                                                                    <tr id="privacyConsentHeading"
                                                                        class="category_table_heading"
                                                                        style="display:none;">
                                                                        <th class="alignLeft" colspan="4">Privacy
                                                                            Consent
                                                                        </th>
                                                                    </tr>

                                                                    <% if (showConsentsThisTime) { %>
                                                                    <tr>
                                                                        <td width="30%">
                                                                            <input type="hidden" name="usSignedOrig"
                                                                                   value="<%=StringUtils.defaultString(apptMainBean.getString(demoExt.get("usSigned")))%>"/>
                                                                            <input type="hidden"
                                                                                   name="privacyConsentOrig"
                                                                                   value="<%=privacyConsent%>"/>
                                                                            <input type="hidden"
                                                                                   name="informedConsentOrig"
                                                                                   value="<%=informedConsent%>"/>

                                                                            <input type="checkbox" name="privacyConsent"
                                                                                   id="privacyConsent"
                                                                                   value="yes" <%=privacyConsent.equals("yes") ? "checked" : ""%>>
                                                                            <label style="font-weight:bold;"
                                                                                   for="privacyConsent">Privacy Consent
                                                                                (verbal) Obtained</label>
                                                                        </td>
                                                                        <td>
                                                                            &nbsp;
                                                                        </td>
                                                                    </tr>
                                                                    <tr>
                                                                        <td>
                                                                            <input type="checkbox"
                                                                                   name="informedConsent"
                                                                                   id="informedConsent"
                                                                                   value="yes" <%=informedConsent.equals("yes") ? "checked" : ""%>>
                                                                            <label style="font-weight:bold;"
                                                                                   for="informedConsent">Informed
                                                                                Consent (verbal) Obtained</label>
                                                                        </td>
                                                                    </tr>
                                                                    <tr>
                                                                        <td>
                                                                            <div id="usSigned">
                                                                                <input type="radio" name="usSigned"
                                                                                       id="usSignedYes"
                                                                                       value="signed" <%=usSigned.equals("signed") ? "checked" : ""%>>
                                                                                <label style="font-weight:bold;"
                                                                                       for="usSigned">U.S. Resident
                                                                                    Consent Form Signed </label>

                                                                                <input type="radio" name="usSigned"
                                                                                       id="usSignedNo"
                                                                                       value="unsigned" <%=usSigned.equals("unsigned") ? "checked" : ""%>>
                                                                                <label style="font-weight:bold;"
                                                                                       for="usSigned">U.S. Resident
                                                                                    Consent Form NOT Signed</label>
                                                                            </div>
                                                                        </td>
                                                                    </tr>
                                                                    <% } %>

                                                                        <%-- This block of code was designed to eventually manage all of the patient consents. --%>
                                                                    <oscar:oscarPropertiesCheck
                                                                            property="USE_NEW_PATIENT_CONSENT_MODULE"
                                                                            value="true">

                                                                        <c:forEach items="${ consentTypes }"
                                                                                   var="consentType" varStatus="count">
                                                                            <c:set var="patientConsent" value=""/>
                                                                            <c:forEach items="${ patientConsents }"
                                                                                       var="consent">
                                                                                <c:if test="${ consent.consentType.id eq consentType.id }">
                                                                                    <c:set var="patientConsent"
                                                                                           value="${ consent }"/>
                                                                                </c:if>
                                                                            </c:forEach>
                                                                            <tr class="privacyConsentRow"
                                                                                id="${ count.index }">
                                                                                <td class="alignRight"
                                                                                    style="width:16%;vertical-align:top;">
                                                                                    <div style="font-weight:bold;white-space:nowrap;">
                                                                                        <c:out value="${ consentType.name }"/>
                                                                                    </div>

                                                                                    <c:if test="${ not empty patientConsent and not empty patientConsent.optout }">
                                                                                        <c:choose>
                                                                                            <c:when test="${ patientConsent.optout }">
                                                                                                <div id="consentDate_${consentType.type}"
                                                                                                     style="color:red;white-space:nowrap;">
                                                                                                    Opted Out:<c:out
                                                                                                        value="${ patientConsent.optoutDate }"/>
                                                                                                </div>
                                                                                            </c:when>
                                                                                            <c:otherwise>
                                                                                                <div id="consentDate_${consentType.type}"
                                                                                                     style="color:green;white-space:nowrap;">
                                                                                                    Consented:<c:out
                                                                                                        value="${ patientConsent.consentDate }"/>
                                                                                                </div>
                                                                                            </c:otherwise>
                                                                                        </c:choose>
                                                                                    </c:if>
                                                                                </td>

                                                                                <td colspan="2"
                                                                                    style="padding-left:10px;vertical-align:top;">
                                                                                    <c:out value="${ consentType.description }"/>
                                                                                </td>

                                                                                <td id="consentStatusDate"
                                                                                    style="width:31%;vertical-align:top;">
                                                                                    <input type="radio"
                                                                                           name="${ consentType.type }"
                                                                                           id="optin_${ consentType.type }"
                                                                                           value="0"
                                                                                            <c:if test="${ not empty patientConsent and not empty patientConsent.optout and not patientConsent.optout }">
                                                                                                <c:out value="checked"/>
                                                                                            </c:if>
                                                                                    />
                                                                                    <label for="optin_${ consentType.type }">Opt-In</label>
                                                                                    <input type="radio"
                                                                                           name="${ consentType.type }"
                                                                                           id="optout_${ consentType.type }"
                                                                                           value="1"
                                                                                            <c:if test="${ not empty patientConsent and not empty patientConsent.optout and patientConsent.optout }">
                                                                                                <c:out value="checked"/>
                                                                                            </c:if>
                                                                                    />
                                                                                    <label for="optout_${ consentType.type }">Opt-Out</label>
                                                                                    <input type="button"
                                                                                           name="clearRadio_${consentType.type}_btn"
                                                                                           onclick="consentClearBtn('${consentType.type}')"
                                                                                           value="Clear"/>

                                                                                        <%-- Was this consent set by the user? Or by the database?  --%>
                                                                                    <input type="hidden"
                                                                                           name="consentPreset_${consentType.type}"
                                                                                           id="consentPreset_${consentType.type}"
                                                                                           value="${ not empty patientConsent }"/>

                                                                                        <%-- This consent will be labeled for delete when the clear button is clicked. --%>
                                                                                    <input type="hidden"
                                                                                           name="deleteConsent_${consentType.type}"
                                                                                           id="deleteConsent_${consentType.type}"
                                                                                           value="0"/>

                                                                                </td>

                                                                            </tr>
                                                                        </c:forEach>

                                                                    </oscar:oscarPropertiesCheck>

                                                                </table>
                                                            </td>
                                                        </tr>
                                                    </oscar:oscarPropertiesCheck>

                                                        <%-- END TOGGLE OFF PATIENT PRIVACY CONSENT --%>

                                                        <%-- TOGGLE OFF MEDITECH MODULE --%>
                                                    <% if (oscarProps.isPropertyActive("meditech_id")) { %>
                                                    <tr>
                                                        <td align="right"><b>Meditech ID: </b></td>
                                                        <td align="left"><input type="text" name="meditech_id"
                                                                                size="30"
                                                                                value="<%=OtherIdManager.getDemoOtherId(
									demographic_no, "meditech_id")%>">
                                                            <input type="hidden" name="meditech_idOrig"
                                                                   value="<%=OtherIdManager.getDemoOtherId(
									demographic_no, "meditech_id")%>">
                                                        </td>
                                                        <td><!-- padding --></td>
                                                        <td><!-- padding --></td>
                                                    </tr>
                                                    <%
                                                        }
                                                    %>
                                                        <%-- END TOGGLE OFF MEDITECH MODULE --%>

                                                        <%-- TOGGLE OFF EXTRA DEMO FIELDS (NATIVE HEALTH) --%>
                                                    <%
                                                        if (oscarProps.getProperty("EXTRA_DEMO_FIELDS") != null) {
                                                            String fieldJSP = oscarProps
                                                                    .getProperty("EXTRA_DEMO_FIELDS");
                                                            fieldJSP += ".jsp";
                                                    %>
                                                    <tr>
                                                        <td colspan="4">
                                                            <jsp:include page="<%=fieldJSP%>">
                                                                <jsp:param name="demo" value="<%=demographic_no%>"/>
                                                            </jsp:include>
                                                        </td>
                                                    </tr>
                                                    <%}%>

                                                        <%-- END TOGGLE OFF EXTRA DEMO FIELDS (NATIVE HEALTH) --%>

                                                        <%-- WAITING LIST MODULE --%>
                                                    <oscar:oscarPropertiesCheck property="DEMOGRAPHIC_WAITING_LIST"
                                                                                value="true">


                                                        <tr valign="top">
                                                            <td colspan="4">
                                                                <table border="0" cellspacing="0" cellpadding="0"
                                                                       width="100%" id="waitingListTable">

                                                                    <tr id="waitingListHeading"
                                                                        class="category_table_heading">
                                                                        <th colspan="4" class="alignLeft">Waiting List
                                                                        </th>
                                                                    </tr>
                                                                    <tr>
                                                                        <td align="right" nowrap><b>
                                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgWaitList"/>:</b>
                                                                        </td>
                                                                        <td align="left">
                                                                            <%

                                                                                List<ca.openosp.openo.commn.model.WaitingList> wls = waitingListDao.search_wlstatus(Integer.parseInt(demographic_no));

                                                                                String wlId = "", listID = "", wlnote = "";
                                                                                String wlReferralDate = "";
                                                                                if (wls.size() > 0) {
                                                                                    ca.openosp.openo.commn.model.WaitingList wl = wls.get(0);
                                                                                    wlId = wl.getId().toString();
                                                                                    listID = String.valueOf(wl.getListId());
                                                                                    wlnote = wl.getNote();
                                                                                    wlReferralDate = ConversionUtils.toDateString(wl.getOnListSince());
                                                                                    if (wlReferralDate != null && wlReferralDate.length() > 10) {
                                                                                        wlReferralDate = wlReferralDate.substring(0, 11);
                                                                                    }
                                                                                }

                                                                            %> <input type="hidden" name="wlId"
                                                                                      value="<%=wlId%>"> <select
                                                                                name="list_id">
                                                                            <%if ("".equals(wLReadonly)) {%>
                                                                            <option value="0"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optSelectWaitList"/></option>
                                                                            <%} else {%>
                                                                            <option value="0">
                                                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optCreateWaitList"/></option>
                                                                            <%} %>
                                                                            <%

                                                                                List<WaitingListName> wlns = waitingListNameDao.findCurrentByGroup(((ProviderPreference) session.getAttribute(ca.openosp.openo.utility.SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE)).getMyGroupNo());
                                                                                for (WaitingListName wln : wlns) {
                                                                            %>
                                                                            <option value="<%=wln.getId()%>"
                                                                                    <%=wln.getId().toString().equals(listID) ? " selected" : ""%>>
                                                                                <%=wln.getName()%>
                                                                            </option>
                                                                            <%
                                                                                }

                                                                            %>
                                                                        </select></td>
                                                                        <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgWaitListNote"/>: </b>
                                                                        </td>
                                                                        <td align="left"><input type="text"
                                                                                                name="waiting_list_note"
                                                                                                value="<%=wlnote%>"
                                                                                <%=wLReadonly%>></td>
                                                                    </tr>
                                                                    <tr>

                                                                        <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDateOfReq"/>: </b>
                                                                        </td>
                                                                        <td align="left"><input type="text"
                                                                                                placeholder="yyyy-mm-dd"
                                                                                                name="waiting_list_referral_date"
                                                                                                id="waiting_list_referral_date"
                                                                                                size="11"
                                                                                                value="<%=wlReferralDate%>" <%=wLReadonly%>><img
                                                                                src="<%= request.getContextPath() %>/images/cal.gif"
                                                                                id="referral_date_cal">
                                                                        </td>
                                                                        <td><!-- padding --></td>
                                                                        <td><!-- padding --></td>
                                                                    </tr>
                                                                </table>
                                                            </td>
                                                        </tr>
                                                    </oscar:oscarPropertiesCheck>
                                                        <%-- END WAITING LIST MODULE --%>


                                                        <%-- AUTHOR DENNIS WARREN O/A COLCAMEX RESOURCES --%>
                                                    <oscar:oscarPropertiesCheck
                                                            property="DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM"
                                                            value="true">
                                                        <tr>
                                                            <td colspan="4">
                                                                <jsp:include page="manageHealthCareTeam.jsp">
                                                                    <jsp:param name="demographicNo"
                                                                               value="<%= demographic_no %>"/>
                                                                </jsp:include>
                                                            </td>
                                                        </tr>
                                                    </oscar:oscarPropertiesCheck>
                                                        <%-- END AUTHOR DENNIS WARREN O/A COLCAMEX RESOURCES --%>

                                                        <%-- TOGGLED OFF PROGRAM ADMISSIONS --%>
                                                    <oscar:oscarPropertiesCheck
                                                            property="DEMOGRAPHIC_PROGRAM_ADMISSIONS" value="true">

                                                        <tr>
                                                            <td colspan="4">
                                                                <table style="width:100%;">
                                                                    <tr class="category_table_heading">
                                                                        <th colspan="4" class="alignLeft">Program
                                                                            Admissions
                                                                        </th>
                                                                    </tr>
                                                                    <tr>
                                                                        <td><b>Residential Status:</b></td>
                                                                        <td>
                                                                            <select id="rsid" name="rps">
                                                                                <option value=""></option>
                                                                                <%
                                                                                    String _pvid = loggedInInfo.getLoggedInProviderNo();
                                                                                    ProgramManager tempPm = SpringUtils.getBean(ProgramManager.class);
                                                                                    
                                                                                    // Get providers's programs for permission checking - recreating getActiveProviderPrograms logic
                                                                                    Set<Program> pset = new HashSet<Program>();
                                                                                    for (Program providerProgram : tempPm.getProgramDomain(_pvid)) {
                                                                                        if (providerProgram != null && providerProgram.isActive()) {
                                                                                            pset.add(providerProgram);
                                                                                        }
                                                                                    }
                                                                                    
                                                                                    Program[] bedP = new Program[0];
                                                                                    Program oscarp = programDao.getProgramByName("OSCAR");


                                                                                    for (Program _p : bedP) {
                                                                                %>
                                                                                <option value="<%=_p.getId()%>" <%=isProgramSelected(bedAdmission, _p.getId()) %>><%=_p.getName()%>
                                                                                </option>
                                                                                <%
                                                                                    }

                                                                                %>
                                                                            </select>

                                                                        </td>

                                                                        <td><b>Service Programs</b></td>

                                                                        <td>
                                                                            <ul>
                                                                                <%
                                                                                    ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);
                                                                                    List<Program> servP = programManager.getServicePrograms();

                                                                                    for (Program _p : servP) {
                                                                                        boolean readOnly = false;
                                                                                        if (!pset.contains(_p)) {
                                                                                            readOnly = true;
                                                                                        }
                                                                                        String selected = isProgramSelected(serviceAdmissions, _p.getId());

                                                                                        if (readOnly && selected.length() == 0) {
                                                                                            continue;
                                                                                        }

                                                                                %>
                                                                                <li>
                                                                                    <input type="checkbox" name="sp"
                                                                                           id="sp"
                                                                                           value="<%=_p.getId()%>" <%=selected %> <%=(readOnly) ? " disabled=\"disabled\" " : "" %> />
                                                                                    <label for="sp"><%=_p.getName()%>
                                                                                    </label>
                                                                                </li>
                                                                                <%}%>
                                                                            </ul>
                                                                        </td>

                                                                    </tr>
                                                                </table>
                                                            </td>
                                                        </tr>

                                                    </oscar:oscarPropertiesCheck>
                                                        <%-- END TOGGLE OFF PROGRAM ADMISSIONS --%>

                                                    <tr valign="top">
                                                        <td colspan="4">
                                                            <table id="rxinteractionTable" style="width:100%;">
                                                                <tr class="category_table_heading">
                                                                    <th class="alignLeft" colspan="4">
                                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.rxInteractionWarningLevel"/></th>
                                                                </tr>
                                                                <tr>
                                                                    <td class="alignRight">
                                                                        <strong>Level</strong>
                                                                    </td>
                                                                    <td>
                                                                        <input type="hidden"
                                                                               name="rxInteractionWarningLevelOrig"
                                                                               value="<%=StringUtils.trimToEmpty(demoExt.get("rxInteractionWarningLevel"))%>"/>
                                                                        <select id="rxInteractionWarningLevel"
                                                                                name="rxInteractionWarningLevel">
                                                                            <option value="0" <%=(warningLevel.equals("0") ? "selected=\"selected\"" : "") %>>
                                                                                Not Specified
                                                                            </option>
                                                                            <option value="1" <%=(warningLevel.equals("1") ? "selected=\"selected\"" : "") %>>
                                                                                Low
                                                                            </option>
                                                                            <option value="2" <%=(warningLevel.equals("2") ? "selected=\"selected\"" : "") %>>
                                                                                Medium
                                                                            </option>
                                                                            <option value="3" <%=(warningLevel.equals("3") ? "selected=\"selected\"" : "") %>>
                                                                                High
                                                                            </option>
                                                                            <option value="4" <%=(warningLevel.equals("4") ? "selected=\"selected\"" : "") %>>
                                                                                None
                                                                            </option>
                                                                        </select>
                                                                    </td>

                                                                    <oscar:oscarPropertiesCheck
                                                                            property="INTEGRATOR_LOCAL_STORE"
                                                                            value="yes">
                                                                        <td class="alignRight">
                                                                            <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.primaryEMR"/>:</strong>
                                                                        </td>
                                                                        <td>
                                                                            <%
                                                                                String primaryEMR = demoExt.get("primaryEMR");
                                                                                if (primaryEMR == null)
                                                                                    primaryEMR = "0";
                                                                            %>
                                                                            <input type="hidden" name="primaryEMROrig"
                                                                                   value="<%=StringUtils.trimToEmpty(demoExt.get("primaryEMR"))%>"/>
                                                                            <select id="primaryEMR" name="primaryEMR">
                                                                                <option value="0" <%=(primaryEMR.equals("0") ? "selected=\"selected\"" : "") %>>
                                                                                    No
                                                                                </option>
                                                                                <option value="1" <%=(primaryEMR.equals("1") ? "selected=\"selected\"" : "") %>>
                                                                                    Yes
                                                                                </option>
                                                                            </select>
                                                                        </td>
                                                                    </oscar:oscarPropertiesCheck>
                                                                    <oscar:oscarPropertiesCheck
                                                                            property="INTEGRATOR_LOCAL_STORE"
                                                                            value="no">
                                                                        <td><!-- padding --></td>
                                                                        <td><!-- padding --></td>
                                                                    </oscar:oscarPropertiesCheck>
                                                                </tr>
                                                            </table>
                                                        </td>
                                                    </tr>
                                                        <%-- PATIENT NOTES MODULE --%>
                                                    <tr>
                                                        <td nowrap colspan="4">
                                                            <table width="100%" bgcolor="#EEEEFF"
                                                                   id="demographicPatientNotes">
                                                                <tr id="paitientNotesHeading"
                                                                    class="category_table_heading">
                                                                    <th colspan="4" class="alignLeft">Patient Notes</th>
                                                                </tr>

                                                                <tr>
                                                                    <td width="7%" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formAlert"/>: </b>
                                                                    </td>
                                                                    <td><textarea name="alert" style="width: 100%"
                                                                                  rows="8"><%=alert%></textarea></td>

                                                                    <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formNotes"/>: </b>
                                                                    </td>
                                                                    <td><textarea name="notes" style="width: 100%"
                                                                                  rows="8"><%=notes%></textarea>
                                                                    </td>
                                                                </tr>
                                                            </table>
                                                        </td>
                                                    </tr>

                                                </table>

                                            </td>
                                        </tr>
                                            <%-- END PATIENT NOTES MODULE --%>
                                            <%-- BOTTOM TOOLBAR  --%>
                                        <tr class="darkPurple">
                                            <td>
                                                <table border="0" width="100%" cellpadding="0" cellspacing="0">
                                                    <tr>
                                                        <td width="30%" valign="top">

                                                            <oscar:oscarPropertiesCheck value="BC"
                                                                                        property="billregion">
                                                                <security:oscarSec roleName="<%=roleName$%>"
                                                                                   objectName="_careconnect" rights="r">
                                                                    <c:set value="${ OscarProperties.getInstance()['BC_CARECONNECT_URL'] }"
                                                                           var="url" scope="page"/>
                                                                    <c:if test="${ not empty url }">
                                                                        <script type="text/javascript"
                                                                                src="${ctx}/careconnect/careconnect.js"></script>
                                                                        <input type="button" value="CareConnect"
                                                                               onclick="callCareConnect('${url}', '${ demographic.hin }', '${ demographic.firstName }',
                                                                                       '${ demographic.lastName }', '${ demographic.formattedDob }', '${ demographic.sex }',
                                                                                       '${ OscarProperties.getInstance()['BC_CARECONNECT_REGION'] }' )"/>
                                                                        <br/>
                                                                    </c:if>
                                                                </security:oscarSec>
                                                            </oscar:oscarPropertiesCheck>
                                                            <input type="button" class="oscar-dialog-link"
                                                                   onClick="window.open('<%= request.getContextPath() %>/demographic/demographicAudit.jsp?demographic_no=<%=demographic_no %>')"
                                                                   value="Audit Information"/>

                                                            <input type="hidden" name="dboperation"
                                                                   value="update_record">

                                                            <security:oscarSec roleName="<%=roleName$%>"
                                                                               objectName="_demographicExport"
                                                                               rights="r" reverse="<%=false%>">
                                                                <input type="button"
                                                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgExport"/>"
                                                                       onclick="window.open('<%= request.getContextPath() %>/demographic/demographicExport.jsp?demographicNo=<%=demographic.getDemographicNo()%>');"/>
                                                            </security:oscarSec>
                                                            <br>


                                                            <input
                                                                    type="button" name="Button" id="cancelButton"
                                                                    class="leftButton top"
                                                                    value="Exit Master Record" onclick="self.close();">
                                                        </td>
                                                        <td width="30%" align='center' valign="top"><input
                                                                type="hidden" name="displaymode" value="Update Record">
                                                            <!-- sec code block --> <span id="updateButton"
                                                                                               style="display: none;"> <security:oscarSec
                                                                    roleName="<%=roleName$%>" objectName="_demographic"
                                                                    rights="w">
                                                                <%
                                                                    boolean showCbiReminder = oscarProps.getBooleanProperty("CBI_REMIND_ON_UPDATE_DEMOGRAPHIC", "true");
                                                                %>
                                                                <input type="submit" <%=(showCbiReminder?"onclick='showCbiReminder()'":"")%>
                                                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnUpdate"/>">
                                                            </security:oscarSec> </span> <!-- sec code block -->
                                                        </td>
                                                        <td width="40%" align='right' valign="top"><span
                                                                id="swipeButton" style="display: none;"> <input
                                                                type="button" name="Button"
                                                                value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnSwipeCard"/>"
                                                                onclick="window.open('demographic/zdemographicswipe.jsp','', 'scrollbars=yes,resizable=yes,width=600,height=300, top=360, left=0')">
								</span>
                                                            <!--input type="button" name="Button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnSwipeCard"/>" onclick="javascript:window.alert('Health Card Number Already Inuse');"-->
                                                            <input type="button" size="110" name="Button"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePDFEnvelope"/>"
                                                                   onclick="popupPage(400,700,'<%=printEnvelope%><%=demographic.getDemographicNo()%>');return false;">
                                                            <input type="button" size="110" name="Button"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePDFLabel"/>"
                                                                   onclick="popupPage(400,700,'<%=printLbl%><%=demographic.getDemographicNo()%>&appointment_no=<%=appointment%>');return false;">
                                                            <input type="button" size="110" name="Button"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePDFAddressLabel"/>"
                                                                   onclick="popupPage(400,700,'<%=printAddressLbl%><%=demographic.getDemographicNo()%>');return false;">
                                                            <input type="button" size="110" name="Button"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePDFChartLabel"/>"
                                                                   onclick="popupPage(400,700,'<%=printChartLbl%><%=demographic.getDemographicNo()%>');return false;">
                                                            <%
                                                                if (oscarProps.getProperty("showSexualHealthLabel", "false").equals("true")) {
                                                            %>
                                                            <input type="button" size="110" name="Button"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePublicHealthLabel"/>"
                                                                   onclick="popupPage(400,700,'<%=printSexHealthLbl%><%=demographic.getDemographicNo()%>');return false;">
                                                            <% } %>
                                                            <input type="button" name="Button" size="110"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnPrintLabel"/>"
                                                                   onclick="popupPage(600,800,'<%=printHtmlLbl%><%=demographic.getDemographicNo()%>');return false;">
                                                            <input type="button" size="110" name="Button"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnClientLabLabel"/>"
                                                                   onclick="popupPage(400,700,'<%=printLabLbl%><%=demographic.getDemographicNo()%>');return false;">
                                                        </td>
                                                    </tr>
                                                </table>
                                                    <%-- END BOTTOM TOOLBAR  --%>

                                                <%
                                                    if (ConformanceTestHelper.enableConformanceOnlyTestFeatures) {
                                                        String styleBut = "";
                                                        if (ConformanceTestHelper.hasDifferentRemoteDemographics(loggedInInfo, Integer.parseInt(demographic$))) {
                                                            styleBut = "style=\"background-color:yellow\"";
                                                        }%>
                                                <input type="button" value="Compare with Integrator" <%=styleBut%>
                                                       onclick="popup(425, 600, 'DiffRemoteDemographics.jsp?demographicId=<%=demographic$%>', 'RemoteDemoWindow')"/>
                                                <input type="button"
                                                       value="Update latest integrated demographics information"
                                                       onclick="document.location='<%=request.getContextPath()%>/demographic/copyLinkedDemographicInfoAction.jsp?demographicId=<%=demographic$%>&<%=request.getQueryString()%>'"/>
                                                <input type="button" value="Send note to integrated provider"
                                                       onclick="document.location='<%=request.getContextPath()%>/demographic/followUpSelection.jsp?demographicId=<%=demographic$%>'"/>
                                                <%
                                                    }
                                                %>
                                            </td>
                                        </tr>
                                    </table>

                                </form>
                                        <%
                    }
                  }
                %>

                    </table>
                </td>
            </tr>
            <tr>
                <td class="MainTableBottomRowLeftColumn"></td>
                <td class="MainTableBottomRowRightColumn"></td>
            </tr>
        </table>
    </div>
    <oscar:oscarPropertiesCheck property="DEMOGRAPHIC_WAITING_LIST" value="true">
        <script type="text/javascript">
            Calendar.setup({
                inputField: "waiting_list_referral_date",
                ifFormat: "%Y-%m-%d",
                showsTime: false,
                button: "referral_date_cal",
                singleClick: true,
                step: 1
            });
        </script>
    </oscar:oscarPropertiesCheck>

    <script type="text/javascript">


        Calendar.setup({
            inputField: "paper_chart_archived_date",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "archive_date_cal",
            singleClick: true,
            step: 1
        });

        function callEligibilityWebService(url, id) {
            var ran_number = Math.round(Math.random() * 1000000);
            var params = "demographic=<%=demographic_no%>&method=checkElig&rand=" + ran_number;  //hack to get around ie caching the page
            var response;
            new Ajax.Request(url + '?' + params, {
                onSuccess: function (response) {
                    document.getElementById(id).innerHTML = response.responseText;
                    document.getElementById('search_spinner').innerHTML = "";
                }
            });
        }
        
        function checkInsuranceEligibility() {
            let params = {};
            params.demographic =<%=demographic_no%>;
            params.method = 'checkElig';
            params.rand = Math.round(Math.random()*1000000);  //hack to get around ie caching the page
            let url = '${ctx}/billing/CA/BC/ManageTeleplan.do';
            jQuery.post(url, params, function() {
                jQuery('#menu2').dialog({
                    title: "MSP Eligibility"
                });
            }).done(function(data){
                jQuery('#menu2 #search_spinner').text("");
                jQuery('#menu2 #returnTeleplanMsg').html(data);
            })
         }

        <%
if (privateConsentEnabled) {
%>
        jQuery(document).ready(function () {
            var countryOfOrigin = jQuery("#countryOfOrigin").val();
            if ("US" != countryOfOrigin) {
                jQuery("#usSigned").hide();
            } else {
                jQuery("#usSigned").show();
            }

            jQuery("#countryOfOrigin").change(function () {
                var countryOfOrigin = jQuery("#countryOfOrigin").val();
                if ("US" == countryOfOrigin) {
                    jQuery("#usSigned").show();
                } else {
                    jQuery("#usSigned").hide();
                }
            });
        });
        <%
}
%>

    </script>
    </body>
</html>


<%!

    public String getDisabled(String fieldName) {
        String val = OscarProperties.getInstance().getProperty("demographic.edit." + fieldName, "");
        if (val != null && val.equals("disabled")) {
            return " disabled=\"disabled\" ";
        }

        return "";
    }

%>

<%!
    public String isProgramSelected(Admission admission, Integer programId) {
        if (admission != null && admission.getProgramId() != null && admission.getProgramId().equals(programId)) {
            return " selected=\"selected\" ";
        }

        return "";
    }

    public String isProgramSelected(List<Admission> admissions, Integer programId) {
        for (Admission admission : admissions) {
            if (admission.getProgramId() != null && admission.getProgramId().equals(programId)) {
                return " checked=\"checked\" ";
            }
        }
        return "";
    }

%>
