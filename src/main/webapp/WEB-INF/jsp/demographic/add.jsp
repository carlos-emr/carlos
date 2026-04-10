<%-- add.jsp - Add Patient Demographic Form (Master Page)
    Served by DemographicAdd2Action. Split into fragments to avoid JVM VerifyError.
    @since 2026-04-04
--%>
<%@ page import="java.util.*" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Date" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
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
<%@ page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.waitinglist.WaitingList" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
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
    // searchMode and keyWord are declared by zdemographicfulltitlesearch.jsp (static include)
%>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<jsp:useBean id="apptMainBean" class="io.github.carlos_emr.AppointmentMainBean" scope="session"/>

<%-- === HTML head and scripts from original lines 159-766 === --%>
<!DOCTYPE html>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath()%>/library/jquery/jquery-compat.js"></script>
        <script>
            jQuery.noConflict();
        </script>

        <script type="text/javascript">
            function aSubmit() {

                if (document.getElementById("eform_iframe") != null) {
                    document.getElementById("eform_iframe").contentWindow.document.forms[0].submit();
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

                var rosterStatus = document.adddemographic.roster_status.value;
                if (rosterStatus == 'RO') {
                    var rosterEnrolledTo = document.adddemographic.roster_enrolled_to.value;
                    var rosterDateYear = document.adddemographic.roster_date_year.value;
                    var rosterDateMonth = document.adddemographic.roster_date_month.value;
                    var rosterDateDate = document.adddemographic.roster_date_date.value;

                    if (rosterEnrolledTo == '') {
                        alert('You must choose a valid Enrolled To physician');
                        return false;
                    }

                    if (rosterDateYear == '' || rosterDateMonth == '' || rosterDateDate == '') {
                        alert('You must choose a valid Date Rostered');
                        return false;
                    }

                }

                return true;
            }

        </script>

        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.title"/></title>

        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>

        <!-- main calendar program -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>

        <!-- language for the calendar -->
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>

        <!-- the following script defines the Calendar.setup helper function, which makes
       adding a calendar a matter of 1 or 2 lines of code. -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>

        <script type="text/javascript" src="<%=request.getContextPath() %>/js/check_hin.js"></script>

        <!-- Stylesheet for zdemographicfulltitlesearch.jsp -->
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/share/css/searchBox.css"/>
        <link rel="stylesheet" type="text/css" href="<%=request.getContextPath() %>/css/Demographic.css"/>

        <!--link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"  /-->
        <script language="JavaScript">
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

            function checkTypeInAdd() {
                var typeInOK = false;
                if (document.adddemographic.last_name.value != "" && document.adddemographic.first_name.value != "" && document.adddemographic.sex.value != "") {
                    if (checkTypeNum(document.adddemographic.year_of_birth.value) && checkTypeNum(document.adddemographic.month_of_birth.value) && checkTypeNum(document.adddemographic.date_of_birth.value)) {
                        typeInOK = true;
                    }
                }
                if (!typeInOK) alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgMissingFields"/>");
                return typeInOK;
            }

            function newStatus() {
                newOpt = prompt("Please enter the new status:", "");
                if (newOpt != "") {
                    document.adddemographic.patient_status.options[document.adddemographic.patient_status.length] = new Option(newOpt, newOpt);
                    document.adddemographic.patient_status.options[document.adddemographic.patient_status.length - 1].selected = true;
                } else {
                    alert("Invalid entry");
                }
            }

            function newStatus1() {
                newOpt = prompt("Please enter the new status:", "");
                if (newOpt != "") {
                    document.adddemographic.roster_status.options[document.adddemographic.roster_status.length] = new Option(newOpt, newOpt);
                    document.adddemographic.roster_status.options[document.adddemographic.roster_status.length - 1].selected = true;
                } else {
                    alert("Invalid entry");
                }
            }

            function formatPhoneNum() {
                if (document.adddemographic.phone.value.length == 10) {
                    document.adddemographic.phone.value = document.adddemographic.phone.value.substring(0, 3) + "-" + document.adddemographic.phone.value.substring(3, 6) + "-" + document.adddemographic.phone.value.substring(6);
                }
                if (document.adddemographic.phone.value.length == 11 && document.adddemographic.phone.value.charAt(3) == '-') {
                    document.adddemographic.phone.value = document.adddemographic.phone.value.substring(0, 3) + "-" + document.adddemographic.phone.value.substring(4, 7) + "-" + document.adddemographic.phone.value.substring(7);
                }

                if (document.adddemographic.phone2.value.length == 10) {
                    document.adddemographic.phone2.value = document.adddemographic.phone2.value.substring(0, 3) + "-" + document.adddemographic.phone2.value.substring(3, 6) + "-" + document.adddemographic.phone2.value.substring(6);
                }
                if (document.adddemographic.phone2.value.length == 11 && document.adddemographic.phone2.value.charAt(3) == '-') {
                    document.adddemographic.phone2.value = document.adddemographic.phone2.value.substring(0, 3) + "-" + document.adddemographic.phone2.value.substring(4, 7) + "-" + document.adddemographic.phone2.value.substring(7);
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
                rs('att', ('<%= request.getContextPath() %>/billing/CA/ON/searchRefDoc.jsp?param=' + t0 + '&param2=' + t1), 600, 600, 1);
            }

            function checkName() {
                var typeInOK = false;
                if (document.adddemographic.last_name.value != "" && document.adddemographic.first_name.value != "" && document.adddemographic.last_name.value != " " && document.adddemographic.first_name.value != " ") {
                    typeInOK = true;
                } else {
                    alert("You must type in the following fields: Last Name, First Name.");
                }
                return typeInOK;
            }

            function checkDob() {
                var typeInOK = false;
                var yyyy = document.adddemographic.year_of_birth.value;
                var selectBox = document.adddemographic.month_of_birth;
                var mm = selectBox.options[selectBox.selectedIndex].value
                selectBox = document.adddemographic.date_of_birth;
                var dd = selectBox.options[selectBox.selectedIndex].value

                if (checkTypeNum(yyyy) && checkTypeNum(mm) && checkTypeNum(dd)) {
                    //alert(yyyy); alert(mm); alert(dd);
                    var check_date = new Date(yyyy, (mm - 1), dd);
                    //alert(check_date);
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
                        //alert("failed in here 1");
                    }
                    if (yyyy == "0000") {
                        typeInOK = false;
                    }
                }

                if (!typeInOK) {
                    alert("You must type in the right DOB.");
                }

                if (!isValidDate(dd, mm, yyyy)) {
                    alert("DOB Date is an incorrect date");
                    typeInOK = false;
                }

                return typeInOK;
            }


            function isValidDate(day, month, year) {
                month = (month - 1);
                dteDate = new Date(year, month, day);
//alert(dteDate);
                return ((day == dteDate.getDate()) && (month == dteDate.getMonth()) && (year == dteDate.getFullYear()));
            }

            function checkHin() {
                var hin = document.adddemographic.hin.value;
                var province = document.adddemographic.hc_type.value;

                if (!isValidHin(hin, province)) {
                    alert("You must type in the right HIN.");
                    return (false);
                }

                return (true);
            }


            function checkSex() {
                var sex = document.adddemographic.sex.value;

                if (sex.length == 0) {
                    alert("You must select a Sex.");
                    return (false);
                }

                return (true);
            }


            function checkResidentStatus() {
                // If OSCAR program exists (ID 10034), make sure it or another program is selected
                var rs = document.adddemographic.rsid.value;
                var oscarOption = document.querySelector('#rsid option[value="10034"]');
                
                if (oscarOption && rs == "") {
                    // If OSCAR program exists but nothing selected, select OSCAR
                    document.adddemographic.rsid.value = "10034";
                }
                return true;
            }

            function checkAllDate() {
                var typeInOK = false;
                typeInOK = checkDateYMD(document.adddemographic.date_joined_year.value, document.adddemographic.date_joined_month.value, document.adddemographic.date_joined_date.value, "Date Joined");
                if (!typeInOK) {
                    return false;
                }

                typeInOK = checkDateYMD(document.adddemographic.end_date_year.value, document.adddemographic.end_date_month.value, document.adddemographic.end_date_date.value, "End Date");
                if (!typeInOK) {
                    return false;
                }

                typeInOK = checkDateYMD(document.adddemographic.hc_renew_date_year.value, document.adddemographic.hc_renew_date_month.value, document.adddemographic.hc_renew_date_date.value, "PCN Date");
                if (!typeInOK) {
                    return false;
                }

                typeInOK = checkDateYMD(document.adddemographic.eff_date_year.value, document.adddemographic.eff_date_month.value, document.adddemographic.eff_date_date.value, "EFF Date");
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
                if (document.getElementById("eform_iframe") != null) document.getElementById("eform_iframe").contentWindow.document.forms[0].submit();
                if (!checkName()) return false;
                if (!checkDob()) return false;
                if (!checkHin()) return false;
                if (!checkSex()) return false;
                if (!checkResidentStatus()) return false;
                if (!checkAllDate()) return false;
                return true;
            }

            function checkTitleSex(ttl) {
                // if (ttl=="MS" || ttl=="MISS" || ttl=="MRS" || ttl=="SR") document.adddemographic.sex.selectedIndex=1;
                //else if (ttl=="MR" || ttl=="MSSR") document.adddemographic.sex.selectedIndex=0;
            }

            function removeAccents(s) {
                var r = s.toLowerCase();
                r = r.replace(new RegExp("\\s", 'g'), "");
                r = r.replace(new RegExp("[������]", 'g'), "a");
                r = r.replace(new RegExp("�", 'g'), "c");
                r = r.replace(new RegExp("[����]", 'g'), "e");
                r = r.replace(new RegExp("[����]", 'g'), "i");
                r = r.replace(new RegExp("�", 'g'), "n");
                r = r.replace(new RegExp("[�����]", 'g'), "o");
                r = r.replace(new RegExp("[����]", 'g'), "u");
                r = r.replace(new RegExp("[��]", 'g'), "y");
                r = r.replace(new RegExp("\\W", 'g'), "");
                return r;
            }

            function autoFillHin() {
                var hcType = document.getElementById('hc_type').value;
                var hin = document.getElementById('hin').value;
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

                    document.getElementById('hin').value = last + first + yob + mob + dob;
                    hin.focus();
                    hin.value = hin.value;
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
                let findDuplicate = jQuery.post("<%=request.getContextPath()%>/demographicSupport.do", { method: "checkForDuplicates", lastName: lastName, firstName: firstName });
                findDuplicate.success(function (data) {
                    if (data.hasDuplicates) {
                        console.log(data);
                        ignore = confirm('There are other patients in this system with the same first and last name. Are you sure you want to create this new patient record?');
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
                        alert("The entered Postal Code is not valid");
                        return false;
                    }
                }//end cdn check

                return true;
            }

            function isCanadian() {
                e = document.adddemographic.province;
                var province = e.options[e.selectedIndex].value;

                if (province.indexOf("US") > -1 || province == "OT") { //if not canadian
                    return false;
                }
                return true;
            }

            function consentClearBtn(radioBtnName) {

                if (confirm("Proceed to clear all record of this consent?")) {

                    //clear out opt-in/opt-out radio buttons
                    var ele = document.getElementsByName(radioBtnName);
                    for (var i = 0; i < ele.length; i++) {
                        ele[i].checked = false;
                    }

                    //hide consent date field from displaying
                    var consentDate = document.getElementById("consentDate_" + radioBtnName);

                    if (consentDate) {
                        consentDate.style.display = "none";
                    }

                }
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
                    url: '<%=request.getContextPath()%>/demographicSupport.do',
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
                    url: '<%=request.getContextPath()%>/demographicSupport.do',
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

                console.log('country=' + country);

                jQuery.ajax({
                    type: "POST",
                    url: '<%=request.getContextPath()%>/demographicSupport.do',
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
                    url: '<%=request.getContextPath()%>/demographicSupport.do',
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

            <% }  %>


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
    </head>
    <!-- Databases have alias for today. It is not necessary give the current date -->

    <body>
    <table>
        <tr bgcolor="#CCCCFF">
            <th class="subject"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgMainLabel"/></th>
        </tr>
    </table>

    <%@ include file="/demographic/zdemographicfulltitlesearch.jsp" %>
    <table width="100%" bgcolor="#CCCCFF">
        <tr>
            <td class="RowTop" colspan="4">

            </td>
        </tr>
        <tr>
            <td>
                <form method="post" id="adddemographic" name="adddemographic" action="demographicaddarecord.jsp"

                    <jsp:include page="add-form-personal.jsp"/>
                    <jsp:include page="add-form-clinical.jsp"/>

<%-- === Closing from original lines 2491-2542 === --%>


            </td>
        </tr>
    </table>

    <script type="text/javascript">
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
    <%-- Registration intake iframe removed (dead commented-out code referencing fid from clinical fragment) --%>
    </body>
</html>
