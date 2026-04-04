<%--
    edit.jsp - Patient Demographic Edit Form (Master Page)

    Served by DemographicEdit2Action which loads all data into request attributes.
    This page includes 3 fragments via <jsp:include> to stay under the JVM's
    64KB bytecode method limit:
      - edit-view.jsp: Read-only demographic display
      - edit-form-personal.jsp: Edit form personal info, address, HIN
      - edit-form-clinical.jsp: Roster, consent, programs, notes

    @since 2026-04-04
--%>
<%@ page import="java.util.*" %>
<%@ page import="java.net.*" %>
<%@ page import="java.text.DecimalFormat" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.AppointmentMainBean" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.MyDateFormat" %>
<%@ page import="io.github.carlos_emr.SxmlMisc" %>
<%@ page import="io.github.carlos_emr.carlos.commn.Gender" %>
<%@ page import="io.github.carlos_emr.carlos.commn.ISO36612" %>
<%@ page import="io.github.carlos_emr.carlos.commn.OtherIdManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ContactDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CountryCodeDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicContactDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicCustDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicExtArchiveDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicExtDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.WaitingListDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Admission" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Appointment" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DemographicArchive" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DemographicExtArchive" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.CountryCode" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DemographicContact" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DemographicCust" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.LookupList" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.LookupListItem" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderPreference" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.WaitingListName" %>
<%@ page import="io.github.carlos_emr.carlos.commn.web.Contact2Action" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicMerged" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicRelationship" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.ProvinceNames" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.pageUtil.DemographicEditHelper" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.pageUtil.Util" %>
<%@ page import="io.github.carlos_emr.carlos.log.LogAction" %>
<%@ page import="io.github.carlos_emr.carlos.log.LogConst" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.LookupListManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.PatientConsentManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.ProgramManager2" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.waitinglist.WaitingList" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.Program" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.service.ProgramManager" %>
<%@ page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="/WEB-INF/special_tag.tld" prefix="special" %>
<c:set var="ctx" value="${ pageContext.request.contextPath }"/>
<%-- Retrieve all variables from request attributes (set by DemographicEdit2Action) --%>
<%
    String demographic_no = (String) request.getAttribute("demographic_no");
    Demographic demographic = (Demographic) request.getAttribute("demographic");
    Map<String, String> demoExt = (Map<String, String>) request.getAttribute("demoExt");
    List<DemographicArchive> archives = (List<DemographicArchive>) request.getAttribute("archives");
    List<DemographicExtArchive> extArchives = (List<DemographicExtArchive>) request.getAttribute("extArchives");
    Admission communityAdmission = (Admission) request.getAttribute("communityAdmission");
    List<Admission> serviceAdmissions = (List<Admission>) request.getAttribute("serviceAdmissions");
    if (serviceAdmissions == null) serviceAdmissions = new ArrayList<Admission>();
    List<Provider> providers = (List<Provider>) request.getAttribute("providers");
    List<Provider> doctors = (List<Provider>) request.getAttribute("doctors");
    List<Provider> nurses = (List<Provider>) request.getAttribute("nurses");
    List<Provider> midwifes = (List<Provider>) request.getAttribute("midwifes");
    List<CountryCode> countryList = (List<CountryCode>) request.getAttribute("countryList");
    boolean hasImportExtra = Boolean.TRUE.equals(request.getAttribute("hasImportExtra"));
    String annotation_display = (String) request.getAttribute("annotation_display");
    String usSigned = (String) request.getAttribute("usSigned");
    String privacyConsent = (String) request.getAttribute("privacyConsent");
    String informedConsent = (String) request.getAttribute("informedConsent");
    boolean privateConsentEnabled = Boolean.TRUE.equals(request.getAttribute("privateConsentEnabled"));
    ProvinceNames pNames = (ProvinceNames) request.getAttribute("pNames");
    String dateString = (String) request.getAttribute("dateString");
    String noteReason = (String) request.getAttribute("noteReason");
    String currentProgram = (String) request.getAttribute("currentProgram");
    boolean isMobileOptimized = Boolean.TRUE.equals(request.getAttribute("isMobileOptimized"));
    String prov = (String) request.getAttribute("prov");
    String curProvider_no = (String) request.getAttribute("curProvider_no");
    String userfirstname = (String) request.getAttribute("userfirstname");
    String userlastname = (String) request.getAttribute("userlastname");
    String apptProvider = (String) request.getAttribute("apptProvider");
    String appointment = (String) request.getAttribute("appointment");
    CarlosProperties oscarProps = (CarlosProperties) request.getAttribute("oscarProps");

    // DAOs/Managers from request attributes
    ScheduleTemplateCodeDao scheduleTemplateCodeDao = (ScheduleTemplateCodeDao) request.getAttribute("scheduleTemplateCodeDao");
    WaitingListDao waitingListDao = (WaitingListDao) request.getAttribute("waitingListDao");
    WaitingListNameDao waitingListNameDao = (WaitingListNameDao) request.getAttribute("waitingListNameDao");
    UserPropertyDAO pref = (UserPropertyDAO) request.getAttribute("userPropertyDAO");
    DemographicDao demographicDao = (DemographicDao) request.getAttribute("demographicDao");
    DemographicExtDao demographicExtDao = (DemographicExtDao) request.getAttribute("demographicExtDao");
    DemographicArchiveDao demographicArchiveDao = (DemographicArchiveDao) request.getAttribute("demographicArchiveDao");
    DemographicExtArchiveDao demographicExtArchiveDao = (DemographicExtArchiveDao) request.getAttribute("demographicExtArchiveDao");
    DemographicManager demographicManager = (DemographicManager) request.getAttribute("demographicManager");
    ProgramManager pm = (ProgramManager) request.getAttribute("programManager");
    ProgramDao programDao = (ProgramDao) request.getAttribute("programDao");
    AdmissionManager admissionManager = (AdmissionManager) request.getAttribute("admissionManager");
    CountryCodeDao ccDAO = (CountryCodeDao) request.getAttribute("ccDAO");
    DemographicCustDao demographicCustDao = (DemographicCustDao) request.getAttribute("demographicCustDao");
    LookupListManager lookupListManager = (LookupListManager) request.getAttribute("lookupListManager");
    ProfessionalSpecialistDao professionalSpecialistDao = SpringUtils.getBean(ProfessionalSpecialistDao.class);
    ProviderDao providerDao = (ProviderDao) request.getAttribute("providerDao");
    ProgramManager2 programManager2 = (ProgramManager2) request.getAttribute("programManager2");
    boolean showConsentsThisTime = false;
    // Cross-fragment variables (computed locally since they depend on request context)
    Admission bedAdmission = null;
    
    String rootContextPath = request.getContextPath();
    String demoPath = rootContextPath + "/demographic/";
    String printEnvelope, printLbl, printAddressLbl, printChartLbl, printSexHealthLbl, printHtmlLbl, printLabLbl;
    if (oscarProps != null && "true".equals(oscarProps.getProperty("new_label_print"))) {
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

    String wLReadonly = "";
    if (oscarProps != null && "true".equals(oscarProps.getProperty("DEMOGRAPHIC_WAITING_LIST"))) {
        wLReadonly = "readonly";
    }
    
    String warningLevel = demoExt != null ? demoExt.get("rxInteractionWarningLevel") : null;
    if (warningLevel == null) warningLevel = "0";

    String demographicExt = oscarProps != null ? oscarProps.getProperty("demographicExt") : null;
    String[] propDemoExt = new String[0];
    DecimalFormat decF = new DecimalFormat();
    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
    LookupList phuLookupList = (LookupList) request.getAttribute("phuLookupList");
    LookupList firstNationCommunities = (LookupList) request.getAttribute("firstNationCommunities");
    
    // View section variables also used in edit sections
    String hasPrimaryCarePhysician = "N/A";
    String employmentStatus = "N/A";
    final String hasPrimary = "Has Primary Care Physician";
    final String empStatus = "Employment Status";
    boolean hasDemoExt = (demographicExt != null && !demographicExt.trim().isEmpty());
    boolean hasHasPrimary = (oscarProps != null && oscarProps.isPropertyActive("showPrimaryCarePhysicianCheck"));
    boolean hasEmpStatus = (oscarProps != null && oscarProps.isPropertyActive("showEmploymentStatusOnAddEdit"));
    if (hasDemoExt && propDemoExt != null) {
        for (String item : propDemoExt) {
            if (hasPrimary.equals(item)) {
                hasPrimaryCarePhysician = org.apache.commons.lang3.StringUtils.defaultString(demoExt.get(hasPrimary.replace(" ", "_")), "N/A");
            }
            if (empStatus.equals(item)) {
                employmentStatus = org.apache.commons.lang3.StringUtils.defaultString(demoExt.get(empStatus.replace(" ", "_")), "N/A");
            }
        }
    }
    if (demographicExt != null && !demographicExt.isEmpty()) {
        propDemoExt = demographicExt.split("\\|");
    }

    
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    java.util.ResourceBundle oscarResources = ResourceBundle.getBundle("oscarResources", request.getLocale());
    
    int nStrShowLen = 20;
    String deepcolor = "#CCCCFF", weakcolor = "#EEEEFF";
%>
<%!
    // Helper methods available in each fragment (delegating to DemographicEditHelper)
    public String getDisabled(String fieldName) {
        return DemographicEditHelper.getDisabled(fieldName);
    }
    public String isProgramSelected(Admission admission, Integer programId) {
        return DemographicEditHelper.isProgramSelected(admission, programId);
    }
    public String isProgramSelected(List<Admission> admissions, Integer programId) {
        return DemographicEditHelper.isProgramSelected(admissions, programId);
    }
    public int timeStrToMins(String timeStr) {
        return DemographicEditHelper.timeStrToMins(timeStr);
    }
%>
<%
    // Computed intermediate values (set by action)
    String rd = (String) request.getAttribute("rd");
    String rdohip = (String) request.getAttribute("rdohip");
    String family_doc = (String) request.getAttribute("family_doc");
    String resident = (String) request.getAttribute("resident");
    String nurse = (String) request.getAttribute("nurse");
    String alert = (String) request.getAttribute("alert");
    String notes = (String) request.getAttribute("notes");
    String midwife = (String) request.getAttribute("midwife");
    String birthYear = (String) request.getAttribute("birthYear");
    String birthMonth = (String) request.getAttribute("birthMonth");
    String birthDate = (String) request.getAttribute("birthDate");
    DemographicCust demographicCust = (DemographicCust) request.getAttribute("demographicCust");
    
    // providerBean is session-scoped, populated during login
%>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<jsp:useBean id="apptMainBean" class="io.github.carlos_emr.AppointmentMainBean" scope="session"/>

<!DOCTYPE html>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.title"/></title>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <oscar:oscarPropertiesCheck property="DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM" value="true">
            <link rel="stylesheet" type="text/css" href="${ pageContext.request.contextPath }/css/healthCareTeam.css"/>
        </oscar:oscarPropertiesCheck>

        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="<%=request.getContextPath()%>/share/calendar/calendar.css" title="win2k-cold-1"/>

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

        <script type="text/javascript" src="<%=request.getContextPath()%>/library/jquery/jquery-ui-1.12.1.min.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath() %>/js/check_hin.js"></script>

        <script type="text/javascript" src="<%=request.getContextPath() %>/js/popup.js"></script>

        <% if (isMobileOptimized) { %>
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/mobile/editdemographicstyle.css">
        <% } %>

        <!--popup menu for encounter type -->
        <script src="<c:out value="${ctx}"/>/share/javascript/popupmenu.js"
                type="text/javascript"></script>
        <script src="<c:out value="${ctx}"/>/share/javascript/menutility.js"
                type="text/javascript"></script>

        <script type="text/javascript"
                src="<%=request.getContextPath() %>/demographic/demographiceditdemographic.js.jsp"></script>

        <!-- Pre-computed i18n strings, safely encoded for JavaScript embedding -->
        <script>
            var i18n = {
                msgWrongDOB:                  '<%= Encode.forJavaScript(oscarResources.getString("demographic.search.msgWrongDOB")) %>',
                msgNameRequired:              '<%= Encode.forJavaScript(oscarResources.getString("demographic.demographiceditdemographic.msgNameRequired")) %>',
                msgWrongDate:                 '<%= Encode.forJavaScript(oscarResources.getString("demographic.demographiceditdemographic.msgWrongDate")) %>',
                msgWrongHIN:                  '<%= Encode.forJavaScript(oscarResources.getString("demographic.demographiceditdemographic.msgWrongHIN")) %>',
                msgBlankRoster:               '<%= Encode.forJavaScript(oscarResources.getString("demographic.demographiceditdemographic.msgBlankRoster")) %>',
                msgForbiddenRosterDate:       '<%= Encode.forJavaScript(oscarResources.getString("demographic.search.msgForbiddenRosterDate")) %>',
                msgLeaveBlank:                '<%= Encode.forJavaScript(oscarResources.getString("demographic.search.msgLeaveBlank")) %>',
                msgWrongRosterDate:           '<%= Encode.forJavaScript(oscarResources.getString("demographic.search.msgWrongRosterDate")) %>',
                msgWrongRosterEnrolledTo:     '<%= Encode.forJavaScript(oscarResources.getString("demographic.search.msgWrongRosterEnrolledTo")) %>',
                msgWrongRosterTerminationDate:'<%= Encode.forJavaScript(oscarResources.getString("demographic.search.msgWrongRosterTerminationDate")) %>',
                msgNoTerminationReason:       '<%= Encode.forJavaScript(oscarResources.getString("demographic.demographiceditdemographic.msgNoTerminationReason")) %>',
                msgWrongPatientStatusDate:    '<%= Encode.forJavaScript(oscarResources.getString("demographic.search.msgWrongPatientStatusDate")) %>',
                msgWrongReferral:             '<%= Encode.forJavaScript(oscarResources.getString("demographic.demographiceditdemographic.msgWrongReferral")) %>',
                msgPromptStatus:              '<%= Encode.forJavaScript(oscarResources.getString("demographic.demographiceditdemographic.msgPromptStatus")) %>',
                msgInvalidEntry:              '<%= Encode.forJavaScript(oscarResources.getString("demographic.demographiceditdemographic.msgInvalidEntry")) %>',
                updateCBIReminder:            '<%= Encode.forJavaScript(oscarResources.getString("demographic.demographiceditdemographic.updateCBIReminder")) %>',
                btnCancel:                    '<%= Encode.forJavaScript(oscarResources.getString("global.btnCancel")) %>',
                btnBack:                      '<%= Encode.forJavaScript(oscarResources.getString("global.btnBack")) %>',
                msgConfirmClearConsent:       '<%= Encode.forJavaScript(oscarResources.getString("demographic.demographiceditdemographic.msgConfirmClearConsent")) %>',
                msgConfirmEnrolledToMRP:      '<%= Encode.forJavaScript(oscarResources.getString("demographic.demographiceditdemographic.msgConfirmEnrolledToMRP")) %>',
                msgConfirmClearEnrolledTo:    '<%= Encode.forJavaScript(oscarResources.getString("demographic.demographiceditdemographic.msgConfirmClearEnrolledTo")) %>',
                msgAjaxError:                 '<%= Encode.forJavaScript(oscarResources.getString("demographic.demographiceditdemographic.msgAjaxError")) %>'
            };

            function showAlert(message) {
                var container = document.getElementById('carlos-alert-container');
                var alertDiv = document.createElement('div');
                alertDiv.className = 'alert alert-warning alert-dismissible fade show';
                alertDiv.setAttribute('role', 'alert');
                var text = document.createElement('span');
                text.style.whiteSpace = 'pre-line';
                text.textContent = String(message).replace(/<br\s*\/?>/gi, '\n');
                alertDiv.appendChild(text);
                var closeButton = document.createElement('button');
                closeButton.type = 'button';
                closeButton.className = 'btn-close';
                closeButton.setAttribute('data-bs-dismiss', 'alert');
                closeButton.setAttribute('aria-label', 'Close');
                alertDiv.appendChild(closeButton);
                container.appendChild(alertDiv);
            }
        </script>

        <script>

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
                        showAlert(i18n.msgWrongDOB);
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
                    showAlert(i18n.msgNameRequired);
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
                    showAlert(err_msg + '<br>' + i18n.msgWrongDate);
                    typeInOK = false;
                }

                return typeInOK;
            }

            function checkDob() {
                var yyyy = document.updatedelete.year_of_birth.value;
                var mm = document.updatedelete.month_of_birth.value;
                var dd = document.updatedelete.date_of_birth.value;

                return checkDate(yyyy, mm, dd, i18n.msgWrongDOB);
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
                    showAlert(i18n.msgWrongHIN);
                    return (false);
                }

                return (true);
            }


            function rosterStatusChangedNotBlank() {
                if (rosterStatusChanged()) {

                    if (document.updatedelete.roster_status.value == "") {
                        showAlert(i18n.msgBlankRoster);
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
                        showAlert(i18n.msgForbiddenRosterDate);
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
                var errMsg = i18n.msgWrongRosterDate;

                if (trueIfBlank) {
                    errMsg += '<br>' + i18n.msgLeaveBlank;
                    if (yyyy == "" && mm == "" && dd == "") return true;
                }
                return checkDate(yyyy, mm, dd, errMsg);
            }


            function rosterEnrolledToValid(trueIfBlank) {
                var val = document.updatedelete.roster_enrolled_to.value.trim();
                var errMsg = '';

                if (trueIfBlank) {
                    errMsg += i18n.msgLeaveBlank;
                    if (val == "") return true;
                }

                if (val == "") {
                    errMsg += i18n.msgWrongRosterEnrolledTo;
                }

                if (errMsg != '') {
                    showAlert(errMsg);
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
                var errMsg = i18n.msgWrongRosterTerminationDate;

                if (trueIfBlank) {
                    errMsg += '<br>' + i18n.msgLeaveBlank;
                    if (yyyy == "" && mm == "" && dd == "") return true;
                }
                return checkDate(yyyy, mm, dd, errMsg);
            }

            function rosterStatusTerminationReasonNotBlank() {
                if (document.updatedelete.roster_termination_reason.value == "") {
                    showAlert(i18n.msgNoTerminationReason);
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
                return checkDate(yyyy, mm, dd, i18n.msgWrongPatientStatusDate);
            }


            function checkONReferralNo() {
                <%
		String skip = CarlosProperties.getInstance().getProperty("SKIP_REFERRAL_NO_CHECK","false");
		if(!skip.equals("true")) {
	%>
                var referralNo = document.updatedelete.r_doctor_ohip.value;
                if (document.updatedelete.hc_type.value == 'ON' && referralNo.length > 0 && referralNo.length != 6) {
                    showAlert(i18n.msgWrongReferral);
                }

                <% } %>
            }


            function newStatus() {
                newOpt = prompt(i18n.msgPromptStatus + ':', "");
                if (newOpt == null) {
                    return;
                } else if (newOpt != "") {
                    document.updatedelete.patient_status.options[document.updatedelete.patient_status.length] = new Option(newOpt, newOpt);
                    document.updatedelete.patient_status.options[document.updatedelete.patient_status.length - 1].selected = true;
                } else {
                    showAlert(i18n.msgInvalidEntry);
                }
            }

            function newStatus1() {
                newOpt = prompt(i18n.msgPromptStatus + ':', "");
                if (newOpt == null) {
                    return;
                } else if (newOpt != "") {
                    document.updatedelete.roster_status.options[document.updatedelete.roster_status.length] = new Option(newOpt, newOpt);
                    document.updatedelete.roster_status.options[document.updatedelete.roster_status.length - 1].selected = true;
                } else {
                    showAlert(i18n.msgInvalidEntry);
                }
            }

        </script>
        <script>
            function showEdit() {
                document.getElementById('editDemographic').style.display = 'table';
                document.getElementById('viewDemographics2').style.display = 'none';
                document.getElementById('updateButton').style.display = 'block';
                var swipeBtn = document.getElementById('swipeButton');
                if (swipeBtn) swipeBtn.style.display = 'block';
                var editBtn = document.getElementById('editBtn');
                if (editBtn) editBtn.style.display = 'none';
                var closeBtn = document.getElementById('closeBtn');
                if (closeBtn) closeBtn.style.display = 'inline';
            }

            function showHideDetail() {
                showHideItem('editDemographic');
                showHideItem('viewDemographics2');
                showHideItem('updateButton');
                if (document.getElementById('swipeButton')) showHideItem('swipeButton');

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
                var cancelValue = i18n.btnCancel;
                var backValue = i18n.btnBack;
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
                var el = document.getElementById(id);
                if (!el) return;
                if (el.style.display === 'inline' || el.style.display === 'block' || el.style.display === 'table') {
                    el.style.display = 'none';
                } else {
                    el.style.display = (el.tagName === 'TABLE') ? 'table' : 'block';
                }
            }

            function showHideBtn(id) {
                var el = document.getElementById(id);
                if (!el) return;
                if (el.style.display === 'none') {
                    el.style.display = 'inline';
                } else {
                    el.style.display = 'none';
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
            var encURL = "<c:out value="${ctx}"/>/encounter/IncomingEncounter.do?providerNo=<%= Encode.forJavaScript(curProvider_no) %>&appointmentNo=&demographicNo=<%=demographic_no%>&curProviderNo=&reason=<%=URLEncoder.encode(noteReason, StandardCharsets.UTF_8)%>&encType=<%=URLEncoder.encode("telephone encounter with client", StandardCharsets.UTF_8)%>&userName=<%=URLEncoder.encode( userfirstname+" "+userlastname, StandardCharsets.UTF_8) %>&curDate=<%=dateString%>&appointmentDate=&startTime=&status=";

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
                var form = document.createElement('form');
                form.method = 'post';
                form.action = 'addDemoToPatientSet.jsp';
                form.target = 'addpsetwin';
                var fields = {demoNo: demoNo, patientSet: patientSet};
                for (var key in fields) {
                    var input = document.createElement('input');
                    input.type = 'hidden';
                    input.name = key;
                    input.value = fields[key];
                    form.appendChild(input);
                }
                document.body.appendChild(form);
                window.open('', 'addpsetwin', 'width=50,height=50');
                form.submit();
                document.body.removeChild(form);
            }

            </security:oscarSec>

            var demographicNo = '<%= Encode.forJavaScript(demographic_no) %>';


            function checkRosterStatus2() {
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
                return confirm(i18n.updateCBIReminder);
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

                if (confirm(i18n.msgConfirmClearConsent)) {

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
                    if (document.getElementById("enrolledTo").value != document.getElementById("mrp").value && confirm(i18n.msgConfirmEnrolledToMRP)) {
                        document.getElementById("enrolledTo").value = document.getElementById("mrp").value;
                    }
                } else {
                    if (document.getElementById("enrolledTo").value != "" && confirm(i18n.msgConfirmClearEnrolledTo.replace('{0}', rosterSelect.getValue()))) {
                        document.getElementById("enrolledTo").value = "";
                    }
                }
            }

            function validateHC() {
                const hin = jQuery("#hinBox").val();
                const ver = jQuery("#verBox").val();
                const hcType = jQuery("#hcTypeBox").val();

                jQuery.ajax({
                    type: "POST",
                    url: '<%=request.getContextPath() %>/ws/rs/patientDetailStatusService/validateHC',
                    data: JSON.stringify({hin: hin, ver: ver}),
                    dataType: 'json',
                    contentType: 'application/json',
                    success: function (data) {
                        showAlert(data.responseDescription);
                    },
                    error: function (data) {
                        showAlert(i18n.msgAjaxError);
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
            .searchBox .select-group, .searchBox div.input-group {
                display: flex;
                flex-direction: row;
                align-items: stretch;
            }

            .searchBox {
                margin: 0 !important;
            }

        </style>
        <link rel="stylesheet" href="<%=request.getContextPath() %>/demographic/demographiceditdemographic.css" type="text/css"/>

    <body onLoad="setfocus(); checkONReferralNo(); formatPhoneNum(); checkRosterStatus2();"
          topmargin="0" leftmargin="0" rightmargin="0" id="demographiceditdemographic">
    <!-- Bootstrap dismissible alert container -->
    <div id="carlos-alert-container" aria-live="polite"
         style="position:fixed;top:10px;left:50%;transform:translateX(-50%);z-index:9999;min-width:300px;max-width:600px;"></div>
    <%-- demographic, archives, extArchives, admissions already loaded by DemographicEdit2Action --%>
    <%
        pageContext.setAttribute("demographic", demographic, PageContext.PAGE_SCOPE);
    %>
    <div id="editDemographicWrapper" style="margin: 0 auto;">
        <table class="MainTable" id="scrollNumber1" name="encounterTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn" colspan="2">
                    <table class="TopStatusBar">
                        <tr>
                            <td>
                                <%
                                    // Birth date and referral doctor fields already computed by DemographicEdit2Action
                                    int dob_year = Integer.parseInt(birthYear);
                                    int dob_month = Integer.parseInt(birthMonth);
                                    int dob_date = Integer.parseInt(birthDate);

                                    if (demographic == null) {
                                        out.println("failed!!!");
                                    } else {
                                %>
                                <span class="patient-header-name"><%= Encode.forHtml(demographic.getLastName()) %>, <%= Encode.forHtml(demographic.getFirstName()) %></span>
                                <%
                                    String sexCode = demographic.getSex() != null ? demographic.getSex().toUpperCase() : "U";
                                    String genderI18nKey;
                                    switch (sexCode) {
                                        case "M":  genderI18nKey = "global.gender.male";        break;
                                        case "F":  genderI18nKey = "global.gender.female";      break;
                                        case "X":  genderI18nKey = "global.gender.intersex";    break;
                                        case "O":  genderI18nKey = "global.gender.other";       break;
                                        default:   genderI18nKey = "global.gender.undisclosed"; break;
                                    }
                                    String genderDisplayText = oscarResources.getString(genderI18nKey);
                                %>
                                <span class="patient-header-details"><%= Encode.forHtml(genderDisplayText) %> &middot; <%= Encode.forHtml(demographic.getAgeAsOf(new Date())) %> &middot; <fmt:message key="demographic.demographiceditdemographic.formDOB"/>: <%= Encode.forHtml(birthYear) %>-<%= Encode.forHtml(birthMonth) %>-<%= Encode.forHtml(birthDate) %></span>
                                <% if (demographic.getHin() != null && !demographic.getHin().isEmpty()) { %>
                                <span class="patient-header-hin"><fmt:message key="demographic.patient.context.hin"/>: <%= Encode.forHtml(demographic.getHin()) %><% if (demographic.getVer() != null && !demographic.getVer().isEmpty()) { %> <%= Encode.forHtml(demographic.getVer()) %><% } %></span>
                                <% } %>
                                <span class="patient-header-appt"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgNextAppt"/>: <oscar:nextAppt demographicNo='<%=demographic.getDemographicNo().toString()%>'/></span>

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
                                    href='<%= request.getContextPath() %>/demographic/DemographicApptHistory.do?demographic_no=<%=demographic.getDemographicNo()%>&orderby=appttime&dboperation=appt_history&limit1=0&limit2=25'><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnApptHist"/></a>
                            </td>
                        </tr>

                        <%
                            // wLReadonly already computed in preamble
                            WaitingList wL = WaitingList.getInstance();
                            if (!wL.getFound()) {
                                wLReadonly = "readonly";
                            }
                            if (wLReadonly.equals("")) {
                        %>
                        <tr>
                            <td><a
                                    href="<%= request.getContextPath() %>/waitinglist/SetupDisplayPatientWaitingList.do?demographic_no=<%=demographic.getDemographicNo()%>">
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
                                        if ("ON".equals(prov)) {
                                    %>
                                    <a href="javascript: function myFunction() {return false; }"
                                       onClick="popupPage(500,800,'<%= Encode.forJavaScriptAttribute(request.getContextPath() + "/billing/CA/ON/billingONHistory.jsp?demographic_no=" + Encode.forUriComponent(String.valueOf(demographic.getDemographicNo()))) %>')">
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
                            %>
                            <%
                                }
                            %>

                        </security:oscarSec>
                        <tr class="Header">
                            <td style="font-weight: bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.Index.clinicalModules"/></td>
                        </tr>
                        <tr>
                            <td><a
                                    href="javascript: function myFunction() {return false; }"
                                    onClick="popupPage(700,960,'<%= request.getContextPath() %>/encounter/oscarConsultationRequest/DisplayDemographicConsultationRequests.jsp?de=<%=demographic.getDemographicNo()%>&proNo=<%=demographic.getProviderNo()%>')"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnConsultation"/></a></td>
                        </tr>

                        <tr>
                            <td><a
                                    href="javascript: function myFunction() {return false; }"
                                    onClick="popupOscarRx(700,1027,'<%= request.getContextPath() %>/oscarRx/choosePatient.do?providerNo=<%= Encode.forJavaScriptAttribute(curProvider_no) %>&demographicNo=<%= Encode.forJavaScriptAttribute(demographic_no) %>')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.prescriptions"/></a>
                            </td>
                        </tr>

                        <security:oscarSec roleName="<%=roleName$%>" objectName="_eChart"
                                           rights="r" reverse="<%=false%>">
                                <tr>
                                    <td>
                                        <a href="javascript: function myFunction() {return false; }"
                                           onClick="popupEChart(710, 1024,encURL);return false;"
                                           title="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnEChart"/>">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnEChart"/></a>
                                    </td>
                                </tr>
                            <tr>
                                <td><a
                                        href="javascript: function myFunction() {return false; }"
                                        onClick="popupPage(700,960,'<c:out
                                                value="${ctx}"/>/oscarPrevention/index.jsp?demographic_no=<%= Encode.forJavaScriptAttribute(demographic_no) %>');return false;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.LeftNavBar.Prevent"/></a></td>
                            </tr>
                        </security:oscarSec>
                        <tr>
                            <td>
                                <a
                                        href="javascript: function myFunction() {return false; }"
                                        onClick="popupPage(700,1000,'<%= request.getContextPath() %>/tickler/ticklerMain.jsp?demoview=<%=Encode.forJavaScriptAttribute(Encode.forUriComponent(demographic_no))%>');return false;">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="global.tickler"/></a>
                            </td>
                        </tr>


                        <% if (oscarProps.getProperty("clinic_no", "").startsWith("1022")) { // quick hack to make Dr. Hunter happy
                        %>
                        <tr>
                            <td><a
                                    href="javascript: function myFunction() {return false; }"
                                    onClick="popupPage(700,1000,'<%=request.getContextPath()%>/form/forwardshortcutname.do?formname=AR1&demographic_no=<%= Encode.forUriComponent(io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("demographic_no"))) %>');">AR1</a>
                            </td>
                        </tr>
                        <tr>
                            <td><a
                                    href="javascript: function myFunction() {return false; }"
                                    onClick="popupPage(700,1000,'<%=request.getContextPath()%>/form/forwardshortcutname.do?formname=AR2&demographic_no=<%= Encode.forUriComponent(io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("demographic_no"))) %>');">AR2</a>
                            </td>
                        </tr>
                        <% } %>
                        <tr class="Header">
                            <td style="font-weight: bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.Index.clinicalResources"/></td>
                        </tr>
                        <special:SpecialPlugin moduleName="inboxmnger">
                            <tr>
                                <td>

                                    <a href="#"
                                       onClick="window.open('<%=request.getContextPath()%>/mod/docmgmtComp/DocList.do?method=list&&demographic_no=<%=Encode.forJavaScriptAttribute(Encode.forUriComponent(demographic_no))%>','_blank','resizable=yes,status=yes,scrollbars=yes');return false;">Inbox
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
                        </special:SpecialPlugin>
                        <tr>
                            <td><a
                                    href="<%= request.getContextPath() %>/eform/efmpatientformlist.jsp?demographic_no=<%= Encode.forUriComponent(demographic_no) %>&apptProvider=<%= Encode.forUriComponent(apptProvider != null ? apptProvider : "") %>&appointment=<%= Encode.forUriComponent(appointment != null ? appointment : "") %>"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnEForm"/></a></td>
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
                        <tr>
                            <td class="search-toggle-bar">
                                <a href="javascript:void(0)" onclick="var el=document.getElementById('searchTable'); el.className = el.className.indexOf('show-search')>=0 ? '' : 'show-search';">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" fill="currentColor" viewBox="0 0 16 16" style="vertical-align: text-bottom;">
                                        <path d="M11.742 10.344a6.5 6.5 0 1 0-1.397 1.398h-.001q.044.06.098.115l3.85 3.85a1 1 0 0 0 1.415-1.414l-3.85-3.85a1 1 0 0 0-.115-.1zM12 6.5a5.5 5.5 0 1 1-11 0 5.5 5.5 0 0 1 11 0"/>
                                    </svg>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.searchPatient"/>
                                </a>
                            </td>
                        </tr>
                        <tr id="searchTable">
                            <td>
                                <jsp:include page="/demographic/zdemographicfulltitlesearch.jsp"/>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <form method="post" name="updatedelete" id="updatedelete"
                                      action="demographic/DemographicUpdate.do"
                                      onSubmit="return checkTypeInEdit();" autocomplete="off">
                                    <input type="hidden" name="demographic_no"
                                           value="<%=demographic.getDemographicNo()%>">
                                    <table width="100%" class="demographicDetail">
                                        <%
                                            DemographicMerged dmDAO = new DemographicMerged();
                                            String dboperation = "search_detail";
                                            String head = dmDAO.getHead(demographic_no);
                                            ArrayList records = dmDAO.getTail(head);
                                        %>
                                        <tr>
                                            <td>
                                                <div class="demo-toolbar">
                                                    <span class="demo-toolbar-id">
                                                        <a href="<%= request.getContextPath() %>/demographic/DemographicEdit.do?demographic_no=<%= Encode.forUriComponent(head) %>">#<%= Encode.forHtml(head) %></a>
                                                        <%
                                                            for (int i = 0; i < records.size(); i++) {
                                                                if (((String) records.get(i)).equals(demographic_no)) {
                                                        %>, #<%= Encode.forHtml(demographic_no) %><%
                                                                } else {
                                                        %>, <a href="<%= request.getContextPath() %>/demographic/DemographicEdit.do?demographic_no=<%= Encode.forUriComponent(String.valueOf(records.get(i))) %>">#<%= Encode.forHtml(String.valueOf(records.get(i))) %></a><%
                                                                }
                                                            }
                                                        %>
                                                    </span>
                                                    <security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="w">
                                                        <% if (head.equals(demographic_no)) { %>
                                                        <button type="button" id="editBtn" class="demo-toolbar-btn" onclick="showHideDetail();">
                                                            <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" fill="currentColor" viewBox="0 0 16 16" style="vertical-align: text-bottom; margin-right: 3px;">
                                                                <path d="M15.502 1.94a.5.5 0 0 1 0 .706L14.459 3.69l-2-2L13.502.646a.5.5 0 0 1 .707 0l1.293 1.293zm-1.75 2.456-2-2L4.939 9.21a.5.5 0 0 0-.121.196l-.805 2.414a.25.25 0 0 0 .316.316l2.414-.805a.5.5 0 0 0 .196-.12l6.813-6.814z"/>
                                                                <path fill-rule="evenodd" d="M1 13.5A1.5 1.5 0 0 0 2.5 15h11a1.5 1.5 0 0 0 1.5-1.5v-6a.5.5 0 0 0-1 0v6a.5.5 0 0 1-.5.5h-11a.5.5 0 0 1-.5-.5v-11a.5.5 0 0 1 .5-.5H9a.5.5 0 0 0 0-1H2.5A1.5 1.5 0 0 0 1 2.5z"/>
                                                            </svg>
                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgEdit"/>
                                                        </button>
                                                        <button type="button" id="closeBtn" class="demo-toolbar-btn" onclick="showHideDetail();" style="display:none;"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/></button>
                                                        <% } %>
                                                    </security:oscarSec>
                                                </div>
                                            </td>
                                        </tr>
                                        <%-- Print label URLs already computed in preamble --%>
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
                                                            <span id="swipeButton" style="display: inline;">
                                    <input type="button" name="Button"
                                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnSwipeCard"/>"
                                           onclick="window.open('demographic/zdemographicswipe.jsp','', 'scrollbars=yes,resizable=yes,width=600,height=300, top=360, left=0')">
                                </span>
                                                            <!--input type="button" name="Button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnSwipeCard"/>" onclick="javascript:window.alert('Health Card Number Already Inuse');"-->
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
                                                <div class="toggle-empty-bar">
                                                    <a href="javascript:void(0)" id="toggleEmptyFields"
                                                       data-show-text="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.showAllFields"/>"
                                                       data-hide-text="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.hideEmptyFields"/>"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.showAllFields"/></a>
                                                </div>
                                                <script>
                                                    jQuery(document).ready(function() {
                                                        // Mark empty list items
                                                        jQuery('#viewDemographics2 .demographicSection li').each(function() {
                                                            var $li = jQuery(this);
                                                            var text = '';
                                                            // Check span.info, strong, b, and anchor text (in priority order)
                                                            var infoSpan = $li.find('span.info');
                                                            var strongEl = $li.find('strong');
                                                            var boldEl = $li.find('b');
                                                            var anchorEl = $li.find('a');
                                                            if (infoSpan.length) {
                                                                text = infoSpan.text();
                                                            } else if (strongEl.length) {
                                                                text = strongEl.text();
                                                            } else if (boldEl.length) {
                                                                text = boldEl.text();
                                                            } else if (anchorEl.length) {
                                                                text = anchorEl.text();
                                                            } else {
                                                                // Fall back to full li text minus label
                                                                text = $li.clone().children('span.label').remove().end().text();
                                                            }
                                                            // Normalize: trim whitespace and non-breaking spaces
                                                            text = text.replace(/\u00a0/g, ' ').trim();
                                                            if (text === '') {
                                                                $li.addClass('empty-field');
                                                            }
                                                        });
                                                        // Start with empties hidden
                                                        jQuery('#viewDemographics2').addClass('hide-empties');
                                                        // Toggle
                                                        jQuery('#toggleEmptyFields').on('click', function() {
                                                            var wrapper = jQuery('#viewDemographics2');
                                                            var showText = jQuery(this).data('showText');
                                                            var hideText = jQuery(this).data('hideText');
                                                            if (wrapper.hasClass('hide-empties')) {
                                                                wrapper.removeClass('hide-empties');
                                                                jQuery(this).text(hideText);
                                                            } else {
                                                                wrapper.addClass('hide-empties');
                                                                jQuery(this).text(showText);
                                                            }
                                                        });
                                                    });
                                                </script>

                                                <%-- === Included fragments (each compiles as separate class) === --%>
                                                <jsp:include page="edit-view.jsp"/>
                                                <jsp:include page="edit-form-personal.jsp"/>
                                                <jsp:include page="edit-form-clinical.jsp"/>
                <%
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
            var params = "demographic=<%= Encode.forJavaScript(Encode.forUriComponent(demographic_no)) %>&method=checkElig&rand=" + ran_number;  //hack to get around ie caching the page
            fetch(url + '?' + params, {
                method: 'GET',
                credentials: 'same-origin',
                headers: {
                    'X-Requested-With': 'XMLHttpRequest'
                }
            }).then(function(r) { return r.text(); })
              .then(function(text) {
                // Server-rendered eligibility HTML inserted into DOM (same-origin trusted content)
                document.getElementById(id).innerHTML = text;
                document.getElementById('search_spinner').innerHTML = "";
            });
        }
        
        function checkInsuranceEligibility() {
            let params = {};
            params.demographic = '<%= Encode.forJavaScript(demographic_no) %>';
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
