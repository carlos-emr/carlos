<%-- add.jsp - Add Patient Demographic Form (Master Page)
    Served by DemographicAdd2Action. Split into fragments to avoid JVM VerifyError.
    @since 2026-04-04
--%>
<%@ page import="java.util.*" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="io.github.carlos_emr.AppointmentMainBean" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.Misc" %>
<%@ page import="io.github.carlos_emr.carlos.commn.Gender" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.*" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.ProvinceNames" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.pageUtil.Util" %>
<%@ page import="io.github.carlos_emr.carlos.managers.LookupListManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.PatientConsentManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.ProgramManager2" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.Program" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.service.ProgramManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@ page import="io.github.carlos_emr.carlos.waitinglist.WaitingList" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<c:set var="ctx" value="${ pageContext.request.contextPath }"/>
<%-- Retrieve variables from request attributes (set by DemographicAdd2Action) --%>
<%
    String curUser_no = (String) request.getAttribute("curUser_no");
    CarlosProperties oscarProps = (CarlosProperties) request.getAttribute("oscarProps");
    String prov = (String) request.getAttribute("prov");
    String curYear = (String) request.getAttribute("curYear");
    String curMonth = (String) request.getAttribute("curMonth");
    String curDay = (String) request.getAttribute("curDay");
    String billingCentre = (String) request.getAttribute("billingCentre");
    String defaultCity = (String) request.getAttribute("defaultCity");
    List<CountryCode> countryList = (List<CountryCode>) request.getAttribute("countryList");
    CountryCodeDao ccDAO = (CountryCodeDao) request.getAttribute("ccDAO");
    UserPropertyDAO userPropertyDAO = (UserPropertyDAO) request.getAttribute("userPropertyDAO");
    String HCType = (String) request.getAttribute("HCType");
    String defaultProvince = (String) request.getAttribute("defaultProvince");
    ProvinceNames pNames = (ProvinceNames) request.getAttribute("pNames");
    boolean privateConsentEnabled = Boolean.TRUE.equals(request.getAttribute("privateConsentEnabled"));
    String today = (String) request.getAttribute("today");
    List<Provider> doctors = (List<Provider>) request.getAttribute("doctors");
    List<Provider> nurses = (List<Provider>) request.getAttribute("nurses");
    List<Provider> midwifes = (List<Provider>) request.getAttribute("midwifes");
    ProviderDao providerDao = (ProviderDao) request.getAttribute("providerDao");
    DemographicDao demographicDao = (DemographicDao) request.getAttribute("demographicDao");
    WaitingListNameDao waitingListNameDao = (WaitingListNameDao) request.getAttribute("waitingListNameDao");
    EFormDao eformDao = (EFormDao) request.getAttribute("eformDao");
    ProgramDao programDao = (ProgramDao) request.getAttribute("programDao");
    ProgramManager pm = (ProgramManager) request.getAttribute("programManager");
    ProgramManager2 programManager2 = (ProgramManager2) request.getAttribute("programManager2");
    ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) request.getAttribute("professionalSpecialistDao");

    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    int nStrShowLen = 20;
    CarlosProperties props = oscarProps;
    java.util.Properties oscarVariables = oscarProps;
    java.util.Locale vLocale = request.getLocale();
    // searchMode and keyWord are declared by add-form-personal.jsp (included below)

    ResourceBundle oscarResources = ResourceBundle.getBundle("oscarResources", request.getLocale());
%>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<jsp:useBean id="apptMainBean" class="io.github.carlos_emr.AppointmentMainBean" scope="session"/>

<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><fmt:message key="demographic.demographicaddrecordhtm.title"/></title>

        <link href="<%= request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">

        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>

        <!-- Stylesheet for zdemographicfulltitlesearch.jsp -->
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/share/css/searchBox.css"/>
        <link rel="stylesheet" type="text/css" href="<%=request.getContextPath() %>/css/Demographic.css"/>

        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath()%>/library/jquery/jquery-compat.js"></script>
        <script>jQuery.noConflict();</script>

        <!-- main calendar program -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>

        <!-- language for the calendar -->
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:message key="global.javascript.calendar"/>"></script>

        <!-- calendar setup helper function -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>

        <script type="text/javascript" src="<%=request.getContextPath() %>/js/check_hin.js"></script>

        <%@ include file="/WEB-INF/jspf/demographic-field-length-limits.jspf" %>

        <script type="text/javascript">
            // Pre-computed i18n strings, safely encoded for JavaScript
            var i18n = {
                msgEnrolledToRequired:   '<%= SafeEncode.forJavaScript(oscarResources.getString("demographic.add.msgEnrolledToRequired")) %>',
                msgRosterDateRequired:   '<%= SafeEncode.forJavaScript(oscarResources.getString("demographic.add.msgRosterDateRequired")) %>',
                promptNewStatus:         '<%= SafeEncode.forJavaScript(oscarResources.getString("demographic.add.promptNewStatus")) %>',
                msgInvalidEntry:         '<%= SafeEncode.forJavaScript(oscarResources.getString("demographic.add.msgInvalidEntry")) %>',
                msgInvalidDOB:           '<%= SafeEncode.forJavaScript(oscarResources.getString("demographic.add.msgInvalidDOB")) %>',
                msgInvalidDOBDate:       '<%= SafeEncode.forJavaScript(oscarResources.getString("demographic.add.msgInvalidDOBDate")) %>',
                msgInvalidHIN:           '<%= SafeEncode.forJavaScript(oscarResources.getString("demographic.add.msgInvalidHIN")) %>',
                msgWrongDate:            '<%= SafeEncode.forJavaScript(oscarResources.getString("demographic.demographiceditdemographic.msgWrongDate")) %>',
                msgSexRequired:          '<%= SafeEncode.forJavaScript(oscarResources.getString("demographic.add.msgSexRequired")) %>',
                msgInvalidPostalCode:    '<%= SafeEncode.forJavaScript(oscarResources.getString("demographic.add.msgInvalidPostalCode")) %>',
                confirmDuplicatePatient: '<%= SafeEncode.forJavaScript(oscarResources.getString("demographic.add.confirmDuplicatePatient")) %>',
                confirmClearConsent:     '<%= SafeEncode.forJavaScript(oscarResources.getString("demographic.add.confirmClearConsent")) %>'
            };

            function showAlert(message) {
                alert(String(message).replace(/<br\s*\/?>/gi, '\n'));
            }

            function aSubmit() {
                parseHINforVC();
                formatPhoneNum(document.adddemographic.phone);
                formatPhoneNum(document.adddemographic.phone2);
                formatPhoneNum(document.adddemographic.demo_cell);
                syncInputDobParts();
                if (document.getElementById("eform_iframe") != null) {
                    var eformDocument = document.getElementById("eform_iframe").contentWindow.document;
                    if (eformDocument.forms && eformDocument.forms.length > 0) {
                        eformDocument.forms[0].submit();
                    }
                }

                if (!checkFormTypeIn()) {
                    return false;
                }

                if (!ignoreDuplicates()) {
                    return false;
                }

                <% if("false".equals(CarlosProperties.getInstance().getProperty("skip_postal_code_validation","false"))) { %>
                if (!isPostalCode()) {
                    return false;
                }
                <% } %>

                var rosterStatus = document.adddemographic.roster_status ? document.adddemographic.roster_status.value : '';
                if (rosterStatus == 'RO') {
                    var rosterEnrolledTo = document.adddemographic.roster_enrolled_to ? document.adddemographic.roster_enrolled_to.value : '';
                    var rosterDateYear = document.adddemographic.roster_date_year ? document.adddemographic.roster_date_year.value : '';
                    var rosterDateMonth = document.adddemographic.roster_date_month ? document.adddemographic.roster_date_month.value : '';
                    var rosterDateDate = document.adddemographic.roster_date_date ? document.adddemographic.roster_date_date.value : '';

                    if (rosterEnrolledTo == '') {
                        alert(i18n.msgEnrolledToRequired);
                        return false;
                    }

                    if (rosterDateYear == '' || rosterDateMonth == '' || rosterDateDate == '') {
                        alert(i18n.msgRosterDateRequired);
                        return false;
                    }
                }
              if (window.opener && !window.opener.closed) {
                  window.opener.location.reload(true); // update the search now that it has a new demo that you might want to access
              }
                return true;
            }


            function upCaseCtrl(ctrl) {
                ctrl.value = ctrl.value.toUpperCase();
            }

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
                        typeInOK = true;
                    }
                    if (dob.value.length != 10) {
                        alert("<fmt:message key="demographic.search.msgWrongDOB"/>");
                        typeInOK = false;
                    }
                    return typeInOK;
                } else {
                    return true;
                }
            }

            function checkTypeInAdd() {
                var typeInOK = false;
                if (document.adddemographic.last_name.value != "" && document.adddemographic.first_name.value != "" && document.adddemographic.sex.value != "") {
                    if (checkTypeNum(document.adddemographic.year_of_birth.value) && checkTypeNum(document.adddemographic.month_of_birth.value) && checkTypeNum(document.adddemographic.date_of_birth.value)) {
                        typeInOK = true;
                    }
                }
                if (!typeInOK) alert("<fmt:message key="demographic.demographicaddrecordhtm.msgMissingFields"/>");
                return typeInOK;
            }

            function newStatus() {
                let newOpt = prompt(i18n.promptNewStatus, "");
                if (newOpt !== null && newOpt.trim() !== "") {
                    document.adddemographic.patient_status.options[document.adddemographic.patient_status.length] = new Option(newOpt, newOpt);
                    document.adddemographic.patient_status.options[document.adddemographic.patient_status.length - 1].selected = true;
                } else {
                    alert(i18n.msgInvalidEntry);
                }
            }

            function newStatus1() {
                let newOpt = prompt(i18n.promptNewStatus, "");
                if (newOpt !== null && newOpt.trim() !== "") {
                    document.adddemographic.roster_status.options[document.adddemographic.roster_status.length] = new Option(newOpt, newOpt);
                    document.adddemographic.roster_status.options[document.adddemographic.roster_status.length - 1].selected = true;
                } else {
                    alert(i18n.msgInvalidEntry);
                }
            }

            function checkHINforVC(hin){
              // Check total length is exactly 12
              // First 10 characters must be digits
              // Last 2 characters must be letters
              return /^\d{10}[A-Za-z]{2}$/.test(hin);
            }

            function parseHINforVC(){
              if (!document.adddemographic || !document.adddemographic.hin) return;
              const hin = document.adddemographic.hin.value;
					if (checkHINforVC(hin)) {
						const firstTen = hin.substring(0, 10);
						const lastTwo = hin.substring(10);
						document.adddemographic.hin.value = firstTen;
                    if (document.adddemographic.ver) {
                        document.adddemographic.ver.value = lastTwo;
                    }
				// Validate Alberta HIN (9 digits) if province is AB
            }

            function formatPhoneNum(el) {
                if (!el || !el.value) return;
                if (el.value.substring(0, 1) == "+") return; // do not reformat E.164 and similar + formatted strings
                const digits = el.value.replace(/\D/g, ''); // strip formatting if any
                if (digits.length === 10) { // test for Canadian pattern XXX-XXX-XXXX
                    el.value = digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
                } else if (digits.length === 11 && digits.substring(0, 1) == "1") { // test for Canadian pattern 1-XXX-XXX-XXXX
                    el.value = digits.substring(0, 1) + "-" + digits.substring(1, 4) + "-" + digits.substring(4, 7) + "-" + digits.substring(7);
                }
            }

            function rs(n, u, w, h, x) {
                args = "width=" + w + ",height=" + h + ",resizable=yes,scrollbars=yes,status=0,top=60,left=30";
                remote = window.open(u, n, args);
            }

            function referralScriptAttach2(elementName, name2) {
                var d = elementName;
                t0 = escape("document.forms[1].elements[\'" + d + "\'].value");
                t1 = escape("document.forms[1].elements[\'" + name2 + "\'].value");
                rs('att', ('<%= request.getContextPath() %>/billing/CA/ON/ViewSearchRefDoc?param=' + t0 + '&param2=' + t1), 600, 600, 1);
            }

            function checkName() {
                var typeInOK = false;
                if (document.adddemographic.last_name.value != "" && document.adddemographic.first_name.value != "" && document.adddemographic.last_name.value != " " && document.adddemographic.first_name.value != " ") {
                    typeInOK = true;
                }
                return typeInOK;
            }

            function checkDob() {
                syncInputDobParts(); // ensure hidden part-fields reflect current visible input
                var typeInOK = false;
                var yyyy = document.adddemographic.year_of_birth.value;
                var mm = document.adddemographic.month_of_birth.value;
                var dd = document.adddemographic.date_of_birth.value;

                return checkDate(yyyy, mm, dd, i18n.msgInvalidDOB);
            }

            function checkDate(yyyy, mm, dd, err_msg) {

                var typeInOK = false;

                if (checkTypeNum(yyyy) && checkTypeNum(mm) && checkTypeNum(dd)) {
                    var check_date = new Date(yyyy, (mm - 1), dd);

                    var young = new Date();
                    young.setDate(young.getDate() + 1); // allow to register newborns born today
                    var old = new Date(1900, 0, 1);

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

            function isValidDate(day, month, year) {
                month = (month - 1);
                dteDate = new Date(year, month, day);
                return ((day == dteDate.getDate()) && (month == dteDate.getMonth()) && (year == dteDate.getFullYear()));
            }

            function checkHin() {
                var hin = document.adddemographic.hin.value;
                var province = document.adddemographic.hc_type.value;

                if (!isValidHin(hin, province)) {
                    alert(i18n.msgInvalidHIN);
                    return (false);
                }

                return (true);
            }

            function checkSex() {
                var sex = document.adddemographic.sex.value;

                if (sex.length == 0) {
                    //alert(i18n.msgSexRequired);  //handelled by Boostrap validation
                    return (false);
                }

                return (true);
            }

            function checkResidentStatus() {
                var rsid = document.adddemographic.rsid;
                if (!rsid) {
                    return true;
                }

                var oscarOption = document.querySelector('#rsid option[value="10034"]');
                if (oscarOption && rsid.value == "") {
                    rsid.value = "10034";
                }
                return true;
            }

            function checkAllDate() {
                var typeInOK = false;
                function formValue(name) {
                    var field = document.adddemographic[name];
                    return field ? field.value : "";
                }

                typeInOK = checkDateYMD(formValue("date_joined_year"), formValue("date_joined_month"), formValue("date_joined_date"), "Date Joined");
                if (!typeInOK) {
                    return false;
                }

                typeInOK = checkDateYMD(formValue("end_date_year"), formValue("end_date_month"), formValue("end_date_date"), "End Date");
                if (!typeInOK) {
                    return false;
                }

                typeInOK = checkDateYMD(formValue("hc_renew_date_year"), formValue("hc_renew_date_month"), formValue("hc_renew_date_date"), "PCN Date");
                if (!typeInOK) {
                    return false;
                }

                typeInOK = checkDateYMD(formValue("eff_date_year"), formValue("eff_date_month"), formValue("eff_date_date"), "EFF Date");
                if (!typeInOK) {
                    return false;
                }

                return typeInOK;
            }

            function checkDateYMD(yy, mm, dd, fieldName) {
                var typeInOK = false;
                if ((yy.length == 0) && (mm.length == 0) && (dd.length == 0)) {
                    typeInOK = true;
                } else if (checkTypeNum(yy) && checkTypeNum(mm) && checkTypeNum(dd)) {
                    if (checkDateYear(yy) && checkDateMonth(mm) && checkDateDate(dd)) {
                        typeInOK = true;
                    }
                }
                if (!typeInOK) {
                    alert("You must type in the right '" + fieldName + "'.");
                    return false;
                }
                return typeInOK;
            }

            function checkDateYear(y) {
                if (y > 1900 && y < 2045) return true;
                return false;
            }

            function checkDateMonth(y) {
                if (y >= 1 && y <= 12) return true;
                return false;
            }

            function checkDateDate(y) {
                if (y >= 1 && y <= 31) return true;
                return false;
            }

            function checkFormTypeIn() {
                if (document.getElementById("eform_iframe") != null) {
                    var eformDocument = document.getElementById("eform_iframe").contentWindow.document;
                    if (eformDocument.forms && eformDocument.forms.length > 0) {
                        eformDocument.forms[0].submit();
                    }
                }
                if (!checkName()) return false;
                if (!checkDob()) return false;
                if (!checkHin()) return false;
                if (!checkSex()) return false;
                if (!checkResidentStatus()) return false;
                if (!checkAllDate()) return false;
                return true;
            }

            function checkTitleSex(ttl) {
                // reserved for future use
            }

            function removeAccents(s) {
                var r = s.toLowerCase();
                r = r.replace(new RegExp("\\s", 'g'), "");
                r = r.replace(new RegExp("[àáâãäå]", 'g'), "a");
                r = r.replace(new RegExp("ç", 'g'), "c");
                r = r.replace(new RegExp("[èéêë]", 'g'), "e");
                r = r.replace(new RegExp("[ìíîï]", 'g'), "i");
                r = r.replace(new RegExp("ñ", 'g'), "n");
                r = r.replace(new RegExp("[òóôõö]", 'g'), "o");
                r = r.replace(new RegExp("[ùúûü]", 'g'), "u");
                r = r.replace(new RegExp("[ýÿ]", 'g'), "y");
                r = r.replace(new RegExp("\\W", 'g'), "");
                return r;
            }

            function autoFillHin() {
                var hcType = document.getElementById('hc_type').value;
                var hinField = document.getElementById('hin');
                var hin = hinField ? hinField.value : '';
                if (hcType == 'QC' && hin == '') {
                    var last = document.getElementById('last_name').value;
                    var first = document.getElementById('first_name').value;
                    var yob = document.getElementById('year_of_birth').value;
                    var mob = document.getElementById('month_of_birth').value;
                    var dob = document.getElementById('date_of_birth').value;

                    last = removeAccents(last.substring(0, 3)).toUpperCase();
                    first = removeAccents(first.substring(0, 1)).toUpperCase();
                    yob = yob.substring(2, 4);

                    var sex = document.getElementById('sex').value;
                    if (sex == 'F') {
                        mob = parseInt(mob) + 50;
                    }

                    if (hinField) {
                        hinField.value = last + first + yob + mob + dob;
                        hinField.focus();
                    }
                }
            }

            function ignoreDuplicates() {
                let ignore = true;
                const lastName = jQuery("#last_name").val().trim();
                const firstName = jQuery("#first_name").val().trim();

                if (!lastName || !firstName) {
                    return true;
                }
                jQuery.ajaxSetup({async: false});
                let findDuplicate = jQuery.post("<%=request.getContextPath()%>/demographicSupport", { method: "checkForDuplicates", lastName: lastName, firstName: firstName });
                findDuplicate.done(function (data) {
                    if (data.hasDuplicates) {
                        console.log(data);
                        ignore = confirm(i18n.confirmDuplicatePatient);
                    }
                })
                jQuery.ajaxSetup({async: true});
                return ignore;
            }

            function isPostalCode() {
                if (isCanadian()) {
                    e = document.adddemographic.postal;
                    postalcode = e.value;

                    rePC = new RegExp(/(^s*([a-z](\s)?\d(\s)?){3}$)s*/i);

                    if (!rePC.test(postalcode)) {
                        e.focus();
                        alert(i18n.msgInvalidPostalCode);
                        return false;
                    }
                }

                return true;
            }

            function isCanadian() {
                e = document.adddemographic.province;
                var province = e.options[e.selectedIndex].value;

                if (province.indexOf("US") > -1 || province == "OT") {
                    return false;
                }
                return true;
            }

            function consentClearBtn(radioBtnName) {
                if (confirm(i18n.confirmClearConsent)) {
                    var ele = document.getElementsByName(radioBtnName);
                    for (var i = 0; i < ele.length; i++) {
                        ele[i].checked = false;
                    }

                    var consentDate = document.getElementById("consentDate_" + radioBtnName);
                    if (consentDate) {
                        consentDate.style.display = "none";
                    }
                }
            }

            function parseDateField(fieldId) {
                const input = document.getElementById(fieldId).value;

                let year = "";
                let month = "";
                let day = "";

                if (input) {
                    const [y, m, d] = input.split("-");
                    year = y || "";
                    month = m || "";
                    day = d || "";
                }
                const yearField = document.querySelector(`input[name="${fieldId}_year"]`);
                const monthField = document.querySelector(`input[name="${fieldId}_month"]`);
                const dateField = document.querySelector(`input[name="${fieldId}_date"]`);
                if (yearField) yearField.value = year;
                if (monthField) monthField.value = month;
                if (dateField) dateField.value = day;
            }

            <%
            if("true".equals(CarlosProperties.getInstance().getProperty("iso3166.2.enabled","false"))) {
            %>
            jQuery(document).ready(function () {

                jQuery("#country").on('change', function () {
                    updateProvinces('');
                });

                jQuery("#residentialCountry").on('change', function () {
                    updateResidentialProvinces('');
                });

                jQuery.ajax({
                    type: "POST",
                    url: '<%=request.getContextPath()%>/demographicSupport',
                    data: 'method=getCountryAndProvinceCodes',
                    dataType: 'json',
                    success: function (data) {
                        jQuery('#country').append(jQuery('<option>').text('').attr('value', ''));
                        jQuery.each(data, function (i, value) {
                            jQuery('#country').append(jQuery('<option>').text(value.label).attr('value', value.value));
                        });

                        var defaultProvince = '<%=CarlosProperties.getInstance().getProperty("demographic.default_province","")%>';
                        var defaultCountry = '';

                        if (defaultProvince == '' && defaultCountry == '') {
                            defaultProvince = 'CA-ON';
                        }
                        defaultCountry = defaultProvince.substring(0, defaultProvince.indexOf('-'));

                        jQuery("#country").val(defaultCountry);
                        updateProvinces(defaultProvince);
                    }
                });

                jQuery.ajax({
                    type: "POST",
                    url: '<%=request.getContextPath()%>/demographicSupport',
                    data: 'method=getCountryAndProvinceCodes',
                    dataType: 'json',
                    success: function (data) {
                        jQuery('#residentialCountry').append(jQuery('<option>').text('').attr('value', ''));
                        jQuery.each(data, function (i, value) {
                            jQuery('#residentialCountry').append(jQuery('<option>').text(value.label).attr('value', value.value));
                        });

                        var defaultProvince = '<%=CarlosProperties.getInstance().getProperty("demographic.default_province","")%>';
                        var defaultCountry = '';

                        if (defaultProvince == '' && defaultCountry == '') {
                            defaultProvince = 'CA-ON';
                        }
                        defaultCountry = defaultProvince.substring(0, defaultProvince.indexOf('-'));

                        jQuery("#residentialCountry").val(defaultCountry);
                        updateResidentialProvinces(defaultProvince);
                    }
                });
            });

            function updateProvinces(province) {
                var country = jQuery("#country").val();
                jQuery.ajax({
                    type: "POST",
                    url: '<%=request.getContextPath()%>/demographicSupport',
                    data: 'method=getCountryAndProvinceCodes&country=' + country,
                    dataType: 'json',
                    success: function (data) {
                        jQuery('#province').empty();
                        jQuery.each(data, function (i, value) {
                            jQuery('#province').append(jQuery('<option>').text(value.label).attr('value', value.value));
                        });

                        if (province != null) {
                            jQuery("#province").val(province);
                        }
                    }
                });
            }

            function updateResidentialProvinces(province) {
                var country = jQuery("#residentialCountry").val();
                jQuery.ajax({
                    type: "POST",
                    url: '<%=request.getContextPath()%>/demographicSupport',
                    data: 'method=getCountryAndProvinceCodes&country=' + country,
                    dataType: 'json',
                    success: function (data) {
                        jQuery('#residentialProvince').empty();
                        jQuery.each(data, function (i, value) {
                            jQuery('#residentialProvince').append(jQuery('<option>').text(value.label).attr('value', value.value));
                        });

                        if (province != null) {
                            jQuery("#residentialProvince").val(province);
                        }
                    }
                });
            }
            <% } %>
        </script>
    </head>

    <body>
    <div class="container-fluid py-2">
        <div class="card">
            <div class="card-header bg-primary text-white py-2">
                <h5 class="mb-0"><fmt:message key="demographic.demographicaddrecordhtm.msgMainLabel"/></h5>
            </div>
            <div class="card-body p-2">

                <jsp:include page="/demographic/ViewZdemographicFullTitleSearch" />

                <form method="post" id="adddemographic" name="adddemographic" action="${ctx}/demographic/DemographicAddRecord" novalidate class="needs-validation" onsubmit="return aSubmit()" autocomplete="off">
                    <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>"/>

                    <jsp:include page="add-form-personal.jsp"/>
                    <jsp:include page="add-form-clinical.jsp"/>

            </div><%-- /.card-body — form closes inside add-form-clinical.jsp --%>
        </div><%-- /.card --%>
    </div><%-- /.container-fluid --%>

    <script type="text/javascript">
      document.addEventListener('DOMContentLoaded', () => {
        'use strict'
        // Fetch all the forms we want to apply custom Bootstrap validation styles to
        const forms = document.querySelectorAll('.needs-validation')
        // Loop over them and prevent submission
        Array.from(forms).forEach(form => {
          form.addEventListener('submit', event => {
            syncInputDobParts();
            if (!form.checkValidity()) {
              event.preventDefault()
              event.stopPropagation()
            }
            form.classList.add('was-validated')
          }, false)
        })
      })

        /* -------------------------------------------------------
         * DOB single-input: calendar picker + hidden-field sync
         * The server expects separate year_of_birth / month_of_birth /
         * date_of_birth parameters; we derive them from the one visible
         * yyyy-mm-dd field every time it changes or the calendar selects.
         * ------------------------------------------------------- */
        Calendar.setup({
            inputField: "inputDOB",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "inputDOB_cal",
            singleClick: true,
            step: 1,
            onUpdate: function() { syncInputDobParts(); }
        });
        function syncInputDobParts() {
            var dobEl = document.getElementById('inputDOB');
            var yearEl = document.getElementById('year_of_birth');
            var monthEl = document.getElementById('month_of_birth');
            var dayEl = document.getElementById('date_of_birth');
            var val = dobEl ? dobEl.value.trim() : '';

            if (!yearEl || !monthEl || !dayEl) {
                return;
            }

            yearEl.value = '';
            monthEl.value = '';
            dayEl.value = '';

            var parts = val.match(/^(\d{4})-(\d{2})-(\d{2})$/);
            if (parts) {
                yearEl.value = parts[1];
                monthEl.value = parts[2];
                dayEl.value = parts[3];

            }
        }
        Calendar.setup({
            inputField: "waiting_list_referral_date",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "referral_date_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "patient_status_date",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "patient_status_date_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "roster_date",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "roster_date_cal",
            singleClick: true,
            step: 1,
            onUpdate: function() { parseDateField('roster_date'); }
        });
        Calendar.setup({
            inputField: "end_date",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "end_date_cal",
            singleClick: true,
            step: 1,
            onUpdate: function() { parseDateField('end_date'); }
        });
        Calendar.setup({
            inputField: "date_joined",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "date_joined_cal",
            singleClick: true,
            step: 1,
            onUpdate: function() { parseDateField('date_joined'); }
        });
        Calendar.setup({
            inputField: "hc_renew_date",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "hc_renew_date_cal",
            singleClick: true,
            step: 1,
            onUpdate: function() { parseDateField('hc_renew_date'); }
        });
        Calendar.setup({
            inputField: "eff_date",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "eff_date_cal",
            singleClick: true,
            step: 1,
            onUpdate: function() { parseDateField('eff_date'); }
        });
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
    <script src="<%= request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
    </body>
</html>
