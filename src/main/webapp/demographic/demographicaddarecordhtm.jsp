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
<%@page import="java.text.SimpleDateFormat" %>
<%@page import="java.util.Date" %>
<%@page import="ca.openosp.openo.managers.LookupListManager" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="ca.openosp.openo.utility.SessionConstants" %>
<%
    String curUser_no = (String) session.getAttribute("user");
    String str = null;
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ page
        import="java.util.*, ca.openosp.*, ca.openosp.openo.demographic.data.ProvinceNames, ca.openosp.openo.demographic.pageUtil.Util, ca.openosp.openo.waitinglist.WaitingList" %>
<%@ page
        import="ca.openosp.openo.commn.dao.*,ca.openosp.openo.commn.model.*" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@page import="ca.openosp.openo.PMmodule.model.Program" %>
<%@page import="ca.openosp.openo.PMmodule.service.ProgramManager" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProgramDao" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="ca.openosp.OscarProperties" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="ca.openosp.openo.managers.ProgramManager2" %>
<%@page import="ca.openosp.openo.PMmodule.model.ProgramProvider" %>

<%@page import="ca.openosp.openo.managers.PatientConsentManager" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.commn.Gender" %>
<%@ page import="ca.openosp.openo.commn.dao.*" %>
<%@ page import="ca.openosp.openo.commn.model.*" %>
<%@ page import="ca.openosp.Misc" %>

<jsp:useBean id="apptMainBean" class="ca.openosp.AppointmentMainBean" scope="session"/>
<%!
    ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    ProgramManager pm = SpringUtils.getBean(ProgramManager.class);
    DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    WaitingListNameDao waitingListNameDao = SpringUtils.getBean(WaitingListNameDao.class);
    EFormDao eformDao = (EFormDao) SpringUtils.getBean(EFormDao.class);
    ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);
    ProgramManager2 programManager2 = SpringUtils.getBean(ProgramManager2.class);
    String privateConsentEnabledProperty = OscarProperties.getInstance().getProperty("privateConsentEnabled");
    boolean privateConsentEnabled = privateConsentEnabledProperty != null && privateConsentEnabledProperty.equals("true");
%>
<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
%>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>

<%@ include file="/admin/dbconnection.jsp" %>
<%
    java.util.Locale vLocale = request.getLocale();

    OscarProperties props = OscarProperties.getInstance();

    GregorianCalendar now = new GregorianCalendar();
    String curYear = Integer.toString(now.get(Calendar.YEAR));
    String curMonth = Integer.toString(now.get(Calendar.MONTH) + 1);
    if (curMonth.length() < 2) curMonth = "0" + curMonth;
    String curDay = Integer.toString(now.get(Calendar.DAY_OF_MONTH));
    if (curDay.length() < 2) curDay = "0" + curDay;

    int nStrShowLen = 20;
    OscarProperties oscarProps = OscarProperties.getInstance();

    ProvinceNames pNames = ProvinceNames.getInstance();
    String prov = (props.getProperty("billregion", "")).trim().toUpperCase();

    String billingCentre = (props.getProperty("billcenter", "")).trim().toUpperCase();
    String defaultCity = prov.equals("ON") && billingCentre.equals("N") ? "Toronto" : "";

    CountryCodeDao ccDAO = SpringUtils.getBean(CountryCodeDao.class);
    List<CountryCode> countryList = ccDAO.getAllCountryCodes();

    // Used to retrieve properties from user (i.e. HC_Type & default_sex)
    UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);

    String HCType = "";
    // Determine if curUser has selected a default HC Type
    UserProperty HCTypeProp = userPropertyDAO.getProp(curUser_no, UserProperty.HC_TYPE);
    if (HCTypeProp != null) {
        HCType = HCTypeProp.getValue();
    } else {
        // If there is no user defined property, then determine if the hctype system property is activated
        HCType = props.getProperty("hctype", "");
        if (HCType == null || HCType.equals("")) {
            // The system property is not activated, so use the billregion
            String billregion = props.getProperty("billregion", "");
            HCType = billregion;
        }
    }
    // Use this value as the default value for province, as well
    String defaultProvince = HCType;


    //get a list of programs the patient has consented to.
    if (OscarProperties.getInstance().getBooleanProperty("USE_NEW_PATIENT_CONSENT_MODULE", "true")) {
        PatientConsentManager patientConsentManager = SpringUtils.getBean(PatientConsentManager.class);
        pageContext.setAttribute("consentTypes", patientConsentManager.getConsentTypes());
    }


    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
    String today = fmt.format(new Date());
%>
<!DOCTYPE html>
<html>
    <script src="${pageContext.request.contextPath}/csrfguard"></script>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/library/jquery/jquery-1.12.0.min.js"></script>
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

                <% if("false".equals(OscarProperties.getInstance().getProperty("skip_postal_code_validation","false"))) { %>
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

        <% if (OscarProperties.getInstance().getBooleanProperty("indivica_hc_read_enabled", "true")) { %>
        <script language="javascript" src="<%=request.getContextPath() %>/hcHandler/hcHandler.js"></script>
        <script language="javascript"
                src="<%=request.getContextPath() %>/hcHandler/hcHandlerNewDemographic.js"></script>
        <link rel="stylesheet" href="<%=request.getContextPath() %>/hcHandler/hcHandler.css" type="text/css"/>
        <% } %>

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
                //do the check
                let ignore = true;
                const lastName = jQuery("#last_name").val();
                const firstName = jQuery("#first_name").val();
                const yearOfBirth = jQuery("#year_of_birth").val();
                const monthOfBirth = jQuery("#month_of_birth").val();
                const dayOfBirth = jQuery("#date_of_birth").val();
                jQuery.ajaxSetup({async: false});
                let findDuplicate = jQuery.post("<%=request.getContextPath()%>/demographicSupport.do?method=checkForDuplicates&lastName=" + lastName + "&firstName=" + firstName + "&yearOfBirth=" + yearOfBirth + "&monthOfBirth=" + monthOfBirth + "&dayOfBirth=" + dayOfBirth);
                findDuplicate.success(function (data) {
                    if (data.hasDuplicates) {
                        console.log(data);
                        ignore = confirm('There are other patients in this system with the same name and date of birth. Are you sure you want to create this new patient record?');
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
if("true".equals(OscarProperties.getInstance().getProperty("iso3166.2.enabled","false"))) {
%>
            jQuery(document).ready(function () {

                jQuery("#country").bind('change', function () {
                    updateProvinces('');
                });

                jQuery("#residentialCountry").bind('change', function () {
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

                        var defaultProvince = '<%=OscarProperties.getInstance().getProperty("demographic.default_province","")%>';
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

                        var defaultProvince = '<%=OscarProperties.getInstance().getProperty("demographic.default_province","")%>';
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
    <!-- Databases have alias for today. It is not necessary give the current date -->

    <body>
    <table>
        <tr bgcolor="#CCCCFF">
            <th class="subject"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgMainLabel"/></th>
        </tr>
    </table>

    <%@ include file="zdemographicfulltitlesearch.jsp" %>
    <table width="100%" bgcolor="#CCCCFF">
        <tr>
            <td class="RowTop" colspan="4">

                <% if (OscarProperties.getInstance().getBooleanProperty("indivica_hc_read_enabled", "true")) { %>
                <span style="position: relative; float: right; font-style: italic; background: black; color: white; padding: 4px; font-size: 12px; border-radius: 3px;">
			<span class="_hc_status_icon _hc_status_success"></span>Ready for Card Swipe
		</span>
                <% } %>
            </td>
        </tr>
        <tr>
            <td>
                <form method="post" id="adddemographic" name="adddemographic" action="demographicaddarecord.jsp"
                      onsubmit="return aSubmit()" autocomplete="off">
                    <input type="hidden" name="fromAppt" value="<%=request.getParameter("fromAppt")%>">
                    <input type="hidden" name="originalPage" value="<%=request.getParameter("originalPage")%>">
                    <input type="hidden" name="bFirstDisp" value="<%=request.getParameter("bFirstDisp")%>">
                    <input type="hidden" name="provider_no" value="<%=request.getParameter("provider_no")%>">
                    <input type="hidden" name="start_time" value="<%=request.getParameter("start_time")%>">
                    <input type="hidden" name="end_time" value="<%=request.getParameter("end_time")%>">
                    <input type="hidden" name="duration" value="<%=request.getParameter("duration")%>">
                    <input type="hidden" name="year" value="<%=request.getParameter("year")%>">
                    <input type="hidden" name="month" value="<%=request.getParameter("month")%>">
                    <input type="hidden" name="day" value="<%=request.getParameter("day")%>">
                    <input type="hidden" name="appointment_date" value="<%=request.getParameter("appointment_date")%>">
                    <input type="hidden" name="notes" value="<%=request.getParameter("notes")%>">
                    <input type="hidden" name="reason" value="<%=request.getParameter("reason")%>">
                    <input type="hidden" name="location" value="<%=request.getParameter("location")%>">
                    <input type="hidden" name="resources" value="<%=request.getParameter("resources")%>">
                    <input type="hidden" name="type" value="<%=request.getParameter("type")%>">
                    <input type="hidden" name="style" value="<%=request.getParameter("style")%>">
                    <input type="hidden" name="billing" value="<%=request.getParameter("billing")%>">
                    <input type="hidden" name="status" value="<%=request.getParameter("status")%>">
                    <input type="hidden" name="createdatetime" value="<%=request.getParameter("createdatetime")%>">
                    <input type="hidden" name="creator" value="<%=request.getParameter("creator")%>">
                    <input type="hidden" name="remarks" value="<%=request.getParameter("remarks")%>">


                    <table id="addDemographicTbl" bgcolor="#EEEEFF">


                        <%if (OscarProperties.getInstance().getProperty("workflow_enhance") != null && OscarProperties.getInstance().getProperty("workflow_enhance").equals("true")) { %>
                        <tr bgcolor="#CCCCFF">
                            <td colspan="4">
                                <input type="hidden" name="dboperation"
                                       value="add_record">
                                <input type="hidden" name="displaymode" value="Add Record">
                                <input type="submit" name="submit"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.btnAddRecord"/>">
                                <input type="button" name="Button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.btnSwipeCard"/>"
                                       onclick="window.open('zadddemographicswipe.htm','', 'scrollbars=yes,resizable=yes,width=600,height=300')"
                                       ;>
                                <input type="button" name="Button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.btnCancel"/>"
                                       onclick=self.close();>
                            </td>
                        </tr>
                        <%
                            }

                            String lastNameVal = "";
                            String firstNameVal = "";
                            String chartNoVal = "";

                            if (searchMode != null) {
                                if ("search_name".equals(searchMode)) {
                                    int commaIdx = keyWord.indexOf(",");
                                    if (commaIdx == -1)
                                        lastNameVal = keyWord.trim();
                                    else if (commaIdx == (keyWord.length() - 1))
                                        lastNameVal = keyWord.substring(0, keyWord.length() - 1).trim();
                                    else {
                                        lastNameVal = keyWord.substring(0, commaIdx).trim();
                                        firstNameVal = keyWord.substring(commaIdx + 1).trim();
                                    }
                                } else if ("search_chart_no".equals(searchMode)) {
                                    chartNoVal = keyWord;
                                }
                            }
                        %>

                        <tr id="rowWithLastName">
                            <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formLastName"/><span
                                    style="color:red;">:</span> </b></td>
                            <td id="lastName" align="left">
                                <input type="text" name="last_name" id="last_name" onBlur="upCaseCtrl(this)"
                                       value="<%=lastNameVal%>">

                            </td>
                            <td align="right" id="firstNameLbl"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formFirstName"/><span
                                    style="color:red;">:</span> </b></td>
                            <td id="firstName" align="left">
                                <input type="text" name="first_name" id="first_name" onBlur="upCaseCtrl(this)"
                                       value="<%=firstNameVal%>">
                            </td>
                        </tr>
                        <tr>
                            <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formMiddleNames"/>: </b></td>
                            <td id="middleName" align="left">
                                <input type="text" name="middleNames" id="middleNames" onBlur="upCaseCtrl(this)"
                                       value="">

                            </td>
                            <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNameUsed"/>:
                            </b></td>
                            <td align="left">
                                <input type="text" name="nameUsed" size="30" value="" onBlur="upCaseCtrl(this)"/>
                            </td>
                        </tr>
                        <tr>
                            <td id="languageLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgDemoLanguage"/><font
                                    color="red">:</font></b></td>
                            <td id="languageCell" align="left">
                                <select id="official_lang" name="official_lang">
                                    <option value="English" <%= vLocale.getLanguage().equals("en") ? " selected" : "" %>>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceaddrecordhtm.msgEnglish"/></option>
                                    <option value="French"  <%= vLocale.getLanguage().equals("fr") ? " selected" : "" %>>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceaddrecordhtm.msgFrench"/></option>
                                </select>
                            </td>
                            <td id="titleLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgDemoTitle"/><font
                                    color="red">:</font></b></td>
                            <td id="titleCell" align="left">
                                <select id="title" name="title" onchange="checkTitleSex(value);">
                                    <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgNotSet"/></option>
                                    <option value="DR"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgDr"/></option>
                                    <option value="MS"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgMs"/></option>
                                    <option value="MISS"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgMiss"/></option>
                                    <option value="MRS"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgMrs"/></option>
                                    <option value="MR"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgMr"/></option>
                                    <option value="MSSR"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgMssr"/></option>
                                    <option value="PROF"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgProf"/></option>
                                    <option value="REEVE"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgReeve"/></option>
                                    <option value="REV"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgRev"/></option>
                                    <option value="RT_HON"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgRtHon"/></option>
                                    <option value="SEN"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgSen"/></option>
                                    <option value="SGT"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgSgt"/></option>
                                    <option value="SR"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgSr"/></option>

                                    <option value="MADAM"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgMadam"/></option>
                                    <option value="MME"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgMme"/></option>
                                    <option value="MLLE"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgMlle"/></option>
                                    <option value="MAJOR"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgMajor"/></option>
                                    <option value="MAYOR"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgMayor"/></option>

                                    <option value="BRO"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgBro"/></option>
                                    <option value="CAPT"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgCapt"/></option>
                                    <option value="Chief"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgChief"/></option>
                                    <option value="Cst"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgCst"/></option>
                                    <option value="Corp"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgCorp"/></option>
                                    <option value="FR"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgFr"/></option>
                                    <option value="HON"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgHon"/></option>
                                    <option value="LT"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgLt"/></option>
                                </select>
                            </td>
                        </tr>
                        <tr>
                            <td id="spokenLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgSpoken"/>:</b></td>
                            <td id="spokenCell"><select name="spoken_lang">
                                <%for (String sp_lang : Util.spokenLangProperties.getLangSorted()) { %>
                                <option value="<%=sp_lang %>"><%=sp_lang %>
                                </option>
                                <%} %>
                            </select>
                            </td>
                            <td><!-- placeholder --></td>
                            <td><!-- placeholder --></td>
                        </tr>

                        <tr valign="top">
                            <td id="addrLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formAddress"/>: </b></td>
                            <td id="addressCell" align="left"><input id="address" type="text" name="address" size=40/>

                            </td>
                            <td id="cityLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formCity"/>: </b></td>
                            <td id="cityCell" align="left"><input type="text" id="city" name="city"
                                                                  value="<%=defaultCity %>"/></td>
                        </tr>

                        <tr valign="top">
                            <td id="provLbl" align="right"><b>
                                <% if (oscarProps.getProperty("demographicLabelProvince") == null) { %>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formprovince"/>
                                <% } else {
                                    out.print(oscarProps.getProperty("demographicLabelProvince"));
                                } %> : </b></td>
                            <td id="provCell" align="left">
                                <%
                                    if ("true".equals(OscarProperties.getInstance().getProperty("iso3166.2.enabled", "false"))) {
                                %>
                                <select name="province" id="province"></select>
                                <br/>
                                Filter by Country: <select name="country" id="country"></select>

                                <% } else { %>
                                <select id="province" name="province">
                                    <option value="OT"
                                            <%=defaultProvince.equals("") || defaultProvince.equals("OT") ? " selected" : ""%>>
                                        Other
                                    </option>
                                        <%-- <option value="">None Selected</option> --%>
                                    <% if (pNames.isDefined()) {
                                        for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                            String province = (String) li.next(); %>
                                    <option value="<%=province%>"
                                            <%=province.equals(defaultProvince) ? " selected" : ""%>><%=li.next()%>
                                    </option>
                                    <% } %>
                                    <% } else { %>
                                    <option value="AB" <%=defaultProvince.equals("AB") ? " selected" : ""%>>AB-Alberta
                                    </option>
                                    <option value="BC" <%=defaultProvince.equals("BC") ? " selected" : ""%>>BC-British
                                        Columbia
                                    </option>
                                    <option value="MB" <%=defaultProvince.equals("MB") ? " selected" : ""%>>
                                        MB-Manitoba
                                    </option>
                                    <option value="NB" <%=defaultProvince.equals("NB") ? " selected" : ""%>>NB-New
                                        Brunswick
                                    </option>
                                    <option value="NL" <%=defaultProvince.equals("NL") ? " selected" : ""%>>
                                        NL-Newfoundland & Labrador
                                    </option>
                                    <option value="NT" <%=defaultProvince.equals("NT") ? " selected" : ""%>>NT-Northwest
                                        Territory
                                    </option>
                                    <option value="NS" <%=defaultProvince.equals("NS") ? " selected" : ""%>>NS-Nova
                                        Scotia
                                    </option>
                                    <option value="NU" <%=defaultProvince.equals("NU") ? " selected" : ""%>>NU-Nunavut
                                    </option>
                                    <option value="ON" <%=defaultProvince.equals("ON") ? " selected" : ""%>>ON-Ontario
                                    </option>
                                    <option value="PE" <%=defaultProvince.equals("PE") ? " selected" : ""%>>PE-Prince
                                        Edward Island
                                    </option>
                                    <option value="QC" <%=defaultProvince.equals("QC") ? " selected" : ""%>>QC-Quebec
                                    </option>
                                    <option value="SK" <%=defaultProvince.equals("SK") ? " selected" : ""%>>
                                        SK-Saskatchewan
                                    </option>
                                    <option value="YT" <%=defaultProvince.equals("YT") ? " selected" : ""%>>YT-Yukon
                                    </option>
                                    <option value="US" <%=defaultProvince.equals("US") ? " selected" : ""%>>US
                                        resident
                                    </option>
                                    <option value="US-AK" <%=defaultProvince.equals("US-AK") ? " selected" : ""%>>
                                        US-AK-Alaska
                                    </option>
                                    <option value="US-AL" <%=defaultProvince.equals("US-AL") ? " selected" : ""%>>
                                        US-AL-Alabama
                                    </option>
                                    <option value="US-AR" <%=defaultProvince.equals("US-AR") ? " selected" : ""%>>
                                        US-AR-Arkansas
                                    </option>
                                    <option value="US-AZ" <%=defaultProvince.equals("US-AZ") ? " selected" : ""%>>
                                        US-AZ-Arizona
                                    </option>
                                    <option value="US-CA" <%=defaultProvince.equals("US-CA") ? " selected" : ""%>>
                                        US-CA-California
                                    </option>
                                    <option value="US-CO" <%=defaultProvince.equals("US-CO") ? " selected" : ""%>>
                                        US-CO-Colorado
                                    </option>
                                    <option value="US-CT" <%=defaultProvince.equals("US-CT") ? " selected" : ""%>>
                                        US-CT-Connecticut
                                    </option>
                                    <option value="US-CZ" <%=defaultProvince.equals("US-CZ") ? " selected" : ""%>>
                                        US-CZ-Canal Zone
                                    </option>
                                    <option value="US-DC" <%=defaultProvince.equals("US-DC") ? " selected" : ""%>>
                                        US-DC-District Of Columbia
                                    </option>
                                    <option value="US-DE" <%=defaultProvince.equals("US-DE") ? " selected" : ""%>>
                                        US-DE-Delaware
                                    </option>
                                    <option value="US-FL" <%=defaultProvince.equals("US-FL") ? " selected" : ""%>>
                                        US-FL-Florida
                                    </option>
                                    <option value="US-GA" <%=defaultProvince.equals("US-GA") ? " selected" : ""%>>
                                        US-GA-Georgia
                                    </option>
                                    <option value="US-GU" <%=defaultProvince.equals("US-GU") ? " selected" : ""%>>
                                        US-GU-Guam
                                    </option>
                                    <option value="US-HI" <%=defaultProvince.equals("US-HI") ? " selected" : ""%>>
                                        US-HI-Hawaii
                                    </option>
                                    <option value="US-IA" <%=defaultProvince.equals("US-IA") ? " selected" : ""%>>
                                        US-IA-Iowa
                                    </option>
                                    <option value="US-ID" <%=defaultProvince.equals("US-ID") ? " selected" : ""%>>
                                        US-ID-Idaho
                                    </option>
                                    <option value="US-IL" <%=defaultProvince.equals("US-IL") ? " selected" : ""%>>
                                        US-IL-Illinois
                                    </option>
                                    <option value="US-IN" <%=defaultProvince.equals("US-IN") ? " selected" : ""%>>
                                        US-IN-Indiana
                                    </option>
                                    <option value="US-KS" <%=defaultProvince.equals("US-KS") ? " selected" : ""%>>
                                        US-KS-Kansas
                                    </option>
                                    <option value="US-KY" <%=defaultProvince.equals("US-KY") ? " selected" : ""%>>
                                        US-KY-Kentucky
                                    </option>
                                    <option value="US-LA" <%=defaultProvince.equals("US-LA") ? " selected" : ""%>>
                                        US-LA-Louisiana
                                    </option>
                                    <option value="US-MA" <%=defaultProvince.equals("US-MA") ? " selected" : ""%>>
                                        US-MA-Massachusetts
                                    </option>
                                    <option value="US-MD" <%=defaultProvince.equals("US-MD") ? " selected" : ""%>>
                                        US-MD-Maryland
                                    </option>
                                    <option value="US-ME" <%=defaultProvince.equals("US-ME") ? " selected" : ""%>>
                                        US-ME-Maine
                                    </option>
                                    <option value="US-MI" <%=defaultProvince.equals("US-MI") ? " selected" : ""%>>
                                        US-MI-Michigan
                                    </option>
                                    <option value="US-MN" <%=defaultProvince.equals("US-MN") ? " selected" : ""%>>
                                        US-MN-Minnesota
                                    </option>
                                    <option value="US-MO" <%=defaultProvince.equals("US-MO") ? " selected" : ""%>>
                                        US-MO-Missouri
                                    </option>
                                    <option value="US-MS" <%=defaultProvince.equals("US-MS") ? " selected" : ""%>>
                                        US-MS-Mississippi
                                    </option>
                                    <option value="US-MT" <%=defaultProvince.equals("US-MT") ? " selected" : ""%>>
                                        US-MT-Montana
                                    </option>
                                    <option value="US-NC" <%=defaultProvince.equals("US-NC") ? " selected" : ""%>>
                                        US-NC-North Carolina
                                    </option>
                                    <option value="US-ND" <%=defaultProvince.equals("US-ND") ? " selected" : ""%>>
                                        US-ND-North Dakota
                                    </option>
                                    <option value="US-NE" <%=defaultProvince.equals("US-NE") ? " selected" : ""%>>
                                        US-NE-Nebraska
                                    </option>
                                    <option value="US-NH" <%=defaultProvince.equals("US-NH") ? " selected" : ""%>>
                                        US-NH-New Hampshire
                                    </option>
                                    <option value="US-NJ" <%=defaultProvince.equals("US-NJ") ? " selected" : ""%>>
                                        US-NJ-New Jersey
                                    </option>
                                    <option value="US-NM" <%=defaultProvince.equals("US-NM") ? " selected" : ""%>>
                                        US-NM-New Mexico
                                    </option>
                                    <option value="US-NU" <%=defaultProvince.equals("US-NU") ? " selected" : ""%>>
                                        US-NU-Nunavut
                                    </option>
                                    <option value="US-NV" <%=defaultProvince.equals("US-NV") ? " selected" : ""%>>
                                        US-NV-Nevada
                                    </option>
                                    <option value="US-NY" <%=defaultProvince.equals("US-NY") ? " selected" : ""%>>
                                        US-NY-New York
                                    </option>
                                    <option value="US-OH" <%=defaultProvince.equals("US-OH") ? " selected" : ""%>>
                                        US-OH-Ohio
                                    </option>
                                    <option value="US-OK" <%=defaultProvince.equals("US-OK") ? " selected" : ""%>>
                                        US-OK-Oklahoma
                                    </option>
                                    <option value="US-OR" <%=defaultProvince.equals("US-OR") ? " selected" : ""%>>
                                        US-OR-Oregon
                                    </option>
                                    <option value="US-PA" <%=defaultProvince.equals("US-PA") ? " selected" : ""%>>
                                        US-PA-Pennsylvania
                                    </option>
                                    <option value="US-PR" <%=defaultProvince.equals("US-PR") ? " selected" : ""%>>
                                        US-PR-Puerto Rico
                                    </option>
                                    <option value="US-RI" <%=defaultProvince.equals("US-RI") ? " selected" : ""%>>
                                        US-RI-Rhode Island
                                    </option>
                                    <option value="US-SC" <%=defaultProvince.equals("US-SC") ? " selected" : ""%>>
                                        US-SC-South Carolina
                                    </option>
                                    <option value="US-SD" <%=defaultProvince.equals("US-SD") ? " selected" : ""%>>
                                        US-SD-South Dakota
                                    </option>
                                    <option value="US-TN" <%=defaultProvince.equals("US-TN") ? " selected" : ""%>>
                                        US-TN-Tennessee
                                    </option>
                                    <option value="US-TX" <%=defaultProvince.equals("US-TX") ? " selected" : ""%>>
                                        US-TX-Texas
                                    </option>
                                    <option value="US-UT" <%=defaultProvince.equals("US-UT") ? " selected" : ""%>>
                                        US-UT-Utah
                                    </option>
                                    <option value="US-VA" <%=defaultProvince.equals("US-VA") ? " selected" : ""%>>
                                        US-VA-Virginia
                                    </option>
                                    <option value="US-VI" <%=defaultProvince.equals("US-VI") ? " selected" : ""%>>
                                        US-VI-Virgin Islands
                                    </option>
                                    <option value="US-VT" <%=defaultProvince.equals("US-VT") ? " selected" : ""%>>
                                        US-VT-Vermont
                                    </option>
                                    <option value="US-WA" <%=defaultProvince.equals("US-WA") ? " selected" : ""%>>
                                        US-WA-Washington
                                    </option>
                                    <option value="US-WI" <%=defaultProvince.equals("US-WI") ? " selected" : ""%>>
                                        US-WI-Wisconsin
                                    </option>
                                    <option value="US-WV" <%=defaultProvince.equals("US-WV") ? " selected" : ""%>>
                                        US-WV-West Virginia
                                    </option>
                                    <option value="US-WY" <%=defaultProvince.equals("US-WY") ? " selected" : ""%>>
                                        US-WY-Wyoming
                                    </option>
                                    <% } %>
                                </select>
                                <% } %>
                            </td>
                            <td class="postalLbl" align="right">
                                <b><% if (oscarProps.getProperty("demographicLabelPostal") == null) { %>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPostal"/>
                                    <% if ("false".equals(OscarProperties.getInstance().getProperty("skip_postal_code_validation", "false"))) { %>
                                    <span style="color:red">*</span>
                                    <% } %>

                                    <% } else {
                                        out.print(oscarProps.getProperty("demographicLabelPostal"));
                                    } %> : </b></td>
                            <td class="postalCell" align="left"><input type="text" id="postal" name="postal"
                                                                       onBlur="upCaseCtrl(this)"></td>
                        </tr>


                        <tr valign="top">
                            <td class="addrLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formResidentialAddress"/>: </b></td>
                            <td class="addressCell" align="left"><input id="residentialAddress" type="text"
                                                                        name="residentialAddress" size=40/>

                            </td>
                            <td class="cityLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formResidentialCity"/>: </b></td>
                            <td class="cityCell" align="left"><input type="text" id="residentialCity"
                                                                     name="residentialCity"
                                                                     value=""/></td>
                        </tr>

                        <tr valign="top">
                            <td class="provLbl" align="right"><b>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formResidentialProvince"/> : </b>
                            </td>
                            <td class="provCell" align="left">
                                <%
                                    if ("true".equals(OscarProperties.getInstance().getProperty("iso3166.2.enabled", "false"))) {
                                %>
                                <select name="residentialProvince" id="residentialProvince"></select>
                                <br/>
                                Filter by Country: <select name="residentialCountry" id="residentialCountry"></select>

                                <% } else { %>
                                <select id="residentialProvince" name="residentialProvince">
                                    <option value="OT"
                                            <%=defaultProvince.equals("") || defaultProvince.equals("OT") ? " selected" : ""%>>
                                        Other
                                    </option>

                                    <% if (pNames.isDefined()) {
                                        for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                            String province = (String) li.next(); %>
                                    <option value="<%=province%>"
                                            <%=province.equals(defaultProvince) ? " selected" : ""%>><%=li.next()%>
                                    </option>
                                    <% } %>
                                    <% } else { %>
                                    <option value="AB" <%=defaultProvince.equals("AB") ? " selected" : ""%>>AB-Alberta
                                    </option>
                                    <option value="BC" <%=defaultProvince.equals("BC") ? " selected" : ""%>>BC-British
                                        Columbia
                                    </option>
                                    <option value="MB" <%=defaultProvince.equals("MB") ? " selected" : ""%>>
                                        MB-Manitoba
                                    </option>
                                    <option value="NB" <%=defaultProvince.equals("NB") ? " selected" : ""%>>NB-New
                                        Brunswick
                                    </option>
                                    <option value="NL" <%=defaultProvince.equals("NL") ? " selected" : ""%>>
                                        NL-Newfoundland & Labrador
                                    </option>
                                    <option value="NT" <%=defaultProvince.equals("NT") ? " selected" : ""%>>NT-Northwest
                                        Territory
                                    </option>
                                    <option value="NS" <%=defaultProvince.equals("NS") ? " selected" : ""%>>NS-Nova
                                        Scotia
                                    </option>
                                    <option value="NU" <%=defaultProvince.equals("NU") ? " selected" : ""%>>NU-Nunavut
                                    </option>
                                    <option value="ON" <%=defaultProvince.equals("ON") ? " selected" : ""%>>ON-Ontario
                                    </option>
                                    <option value="PE" <%=defaultProvince.equals("PE") ? " selected" : ""%>>PE-Prince
                                        Edward Island
                                    </option>
                                    <option value="QC" <%=defaultProvince.equals("QC") ? " selected" : ""%>>QC-Quebec
                                    </option>
                                    <option value="SK" <%=defaultProvince.equals("SK") ? " selected" : ""%>>
                                        SK-Saskatchewan
                                    </option>
                                    <option value="YT" <%=defaultProvince.equals("YT") ? " selected" : ""%>>YT-Yukon
                                    </option>
                                    <option value="US" <%=defaultProvince.equals("US") ? " selected" : ""%>>US
                                        resident
                                    </option>
                                    <option value="US-AK" <%=defaultProvince.equals("US-AK") ? " selected" : ""%>>
                                        US-AK-Alaska
                                    </option>
                                    <option value="US-AL" <%=defaultProvince.equals("US-AL") ? " selected" : ""%>>
                                        US-AL-Alabama
                                    </option>
                                    <option value="US-AR" <%=defaultProvince.equals("US-AR") ? " selected" : ""%>>
                                        US-AR-Arkansas
                                    </option>
                                    <option value="US-AZ" <%=defaultProvince.equals("US-AZ") ? " selected" : ""%>>
                                        US-AZ-Arizona
                                    </option>
                                    <option value="US-CA" <%=defaultProvince.equals("US-CA") ? " selected" : ""%>>
                                        US-CA-California
                                    </option>
                                    <option value="US-CO" <%=defaultProvince.equals("US-CO") ? " selected" : ""%>>
                                        US-CO-Colorado
                                    </option>
                                    <option value="US-CT" <%=defaultProvince.equals("US-CT") ? " selected" : ""%>>
                                        US-CT-Connecticut
                                    </option>
                                    <option value="US-CZ" <%=defaultProvince.equals("US-CZ") ? " selected" : ""%>>
                                        US-CZ-Canal Zone
                                    </option>
                                    <option value="US-DC" <%=defaultProvince.equals("US-DC") ? " selected" : ""%>>
                                        US-DC-District Of Columbia
                                    </option>
                                    <option value="US-DE" <%=defaultProvince.equals("US-DE") ? " selected" : ""%>>
                                        US-DE-Delaware
                                    </option>
                                    <option value="US-FL" <%=defaultProvince.equals("US-FL") ? " selected" : ""%>>
                                        US-FL-Florida
                                    </option>
                                    <option value="US-GA" <%=defaultProvince.equals("US-GA") ? " selected" : ""%>>
                                        US-GA-Georgia
                                    </option>
                                    <option value="US-GU" <%=defaultProvince.equals("US-GU") ? " selected" : ""%>>
                                        US-GU-Guam
                                    </option>
                                    <option value="US-HI" <%=defaultProvince.equals("US-HI") ? " selected" : ""%>>
                                        US-HI-Hawaii
                                    </option>
                                    <option value="US-IA" <%=defaultProvince.equals("US-IA") ? " selected" : ""%>>
                                        US-IA-Iowa
                                    </option>
                                    <option value="US-ID" <%=defaultProvince.equals("US-ID") ? " selected" : ""%>>
                                        US-ID-Idaho
                                    </option>
                                    <option value="US-IL" <%=defaultProvince.equals("US-IL") ? " selected" : ""%>>
                                        US-IL-Illinois
                                    </option>
                                    <option value="US-IN" <%=defaultProvince.equals("US-IN") ? " selected" : ""%>>
                                        US-IN-Indiana
                                    </option>
                                    <option value="US-KS" <%=defaultProvince.equals("US-KS") ? " selected" : ""%>>
                                        US-KS-Kansas
                                    </option>
                                    <option value="US-KY" <%=defaultProvince.equals("US-KY") ? " selected" : ""%>>
                                        US-KY-Kentucky
                                    </option>
                                    <option value="US-LA" <%=defaultProvince.equals("US-LA") ? " selected" : ""%>>
                                        US-LA-Louisiana
                                    </option>
                                    <option value="US-MA" <%=defaultProvince.equals("US-MA") ? " selected" : ""%>>
                                        US-MA-Massachusetts
                                    </option>
                                    <option value="US-MD" <%=defaultProvince.equals("US-MD") ? " selected" : ""%>>
                                        US-MD-Maryland
                                    </option>
                                    <option value="US-ME" <%=defaultProvince.equals("US-ME") ? " selected" : ""%>>
                                        US-ME-Maine
                                    </option>
                                    <option value="US-MI" <%=defaultProvince.equals("US-MI") ? " selected" : ""%>>
                                        US-MI-Michigan
                                    </option>
                                    <option value="US-MN" <%=defaultProvince.equals("US-MN") ? " selected" : ""%>>
                                        US-MN-Minnesota
                                    </option>
                                    <option value="US-MO" <%=defaultProvince.equals("US-MO") ? " selected" : ""%>>
                                        US-MO-Missouri
                                    </option>
                                    <option value="US-MS" <%=defaultProvince.equals("US-MS") ? " selected" : ""%>>
                                        US-MS-Mississippi
                                    </option>
                                    <option value="US-MT" <%=defaultProvince.equals("US-MT") ? " selected" : ""%>>
                                        US-MT-Montana
                                    </option>
                                    <option value="US-NC" <%=defaultProvince.equals("US-NC") ? " selected" : ""%>>
                                        US-NC-North Carolina
                                    </option>
                                    <option value="US-ND" <%=defaultProvince.equals("US-ND") ? " selected" : ""%>>
                                        US-ND-North Dakota
                                    </option>
                                    <option value="US-NE" <%=defaultProvince.equals("US-NE") ? " selected" : ""%>>
                                        US-NE-Nebraska
                                    </option>
                                    <option value="US-NH" <%=defaultProvince.equals("US-NH") ? " selected" : ""%>>
                                        US-NH-New Hampshire
                                    </option>
                                    <option value="US-NJ" <%=defaultProvince.equals("US-NJ") ? " selected" : ""%>>
                                        US-NJ-New Jersey
                                    </option>
                                    <option value="US-NM" <%=defaultProvince.equals("US-NM") ? " selected" : ""%>>
                                        US-NM-New Mexico
                                    </option>
                                    <option value="US-NU" <%=defaultProvince.equals("US-NU") ? " selected" : ""%>>
                                        US-NU-Nunavut
                                    </option>
                                    <option value="US-NV" <%=defaultProvince.equals("US-NV") ? " selected" : ""%>>
                                        US-NV-Nevada
                                    </option>
                                    <option value="US-NY" <%=defaultProvince.equals("US-NY") ? " selected" : ""%>>
                                        US-NY-New York
                                    </option>
                                    <option value="US-OH" <%=defaultProvince.equals("US-OH") ? " selected" : ""%>>
                                        US-OH-Ohio
                                    </option>
                                    <option value="US-OK" <%=defaultProvince.equals("US-OK") ? " selected" : ""%>>
                                        US-OK-Oklahoma
                                    </option>
                                    <option value="US-OR" <%=defaultProvince.equals("US-OR") ? " selected" : ""%>>
                                        US-OR-Oregon
                                    </option>
                                    <option value="US-PA" <%=defaultProvince.equals("US-PA") ? " selected" : ""%>>
                                        US-PA-Pennsylvania
                                    </option>
                                    <option value="US-PR" <%=defaultProvince.equals("US-PR") ? " selected" : ""%>>
                                        US-PR-Puerto Rico
                                    </option>
                                    <option value="US-RI" <%=defaultProvince.equals("US-RI") ? " selected" : ""%>>
                                        US-RI-Rhode Island
                                    </option>
                                    <option value="US-SC" <%=defaultProvince.equals("US-SC") ? " selected" : ""%>>
                                        US-SC-South Carolina
                                    </option>
                                    <option value="US-SD" <%=defaultProvince.equals("US-SD") ? " selected" : ""%>>
                                        US-SD-South Dakota
                                    </option>
                                    <option value="US-TN" <%=defaultProvince.equals("US-TN") ? " selected" : ""%>>
                                        US-TN-Tennessee
                                    </option>
                                    <option value="US-TX" <%=defaultProvince.equals("US-TX") ? " selected" : ""%>>
                                        US-TX-Texas
                                    </option>
                                    <option value="US-UT" <%=defaultProvince.equals("US-UT") ? " selected" : ""%>>
                                        US-UT-Utah
                                    </option>
                                    <option value="US-VA" <%=defaultProvince.equals("US-VA") ? " selected" : ""%>>
                                        US-VA-Virginia
                                    </option>
                                    <option value="US-VI" <%=defaultProvince.equals("US-VI") ? " selected" : ""%>>
                                        US-VI-Virgin Islands
                                    </option>
                                    <option value="US-VT" <%=defaultProvince.equals("US-VT") ? " selected" : ""%>>
                                        US-VT-Vermont
                                    </option>
                                    <option value="US-WA" <%=defaultProvince.equals("US-WA") ? " selected" : ""%>>
                                        US-WA-Washington
                                    </option>
                                    <option value="US-WI" <%=defaultProvince.equals("US-WI") ? " selected" : ""%>>
                                        US-WI-Wisconsin
                                    </option>
                                    <option value="US-WV" <%=defaultProvince.equals("US-WV") ? " selected" : ""%>>
                                        US-WV-West Virginia
                                    </option>
                                    <option value="US-WY" <%=defaultProvince.equals("US-WY") ? " selected" : ""%>>
                                        US-WY-Wyoming
                                    </option>
                                    <% } %>
                                </select>
                                <% } %>
                            </td>
                            <td id="postalLbl" align="right"><b>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formResidentialPostal"/>
                                : </b></td>
                            <td id="postalCell" align="left"><input type="text" id="residentialPostal"
                                                                    name="residentialPostal"
                                                                    onBlur="upCaseCtrl(this)"></td>
                        </tr>

                        <tr valign="top">
                            <td id="phoneLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPhoneHome"/>: </b></td>
                            <td id="phoneCell" align="left"><input type="text" id="phone" name="phone"
                                                                   onBlur="formatPhoneNum()"
                                                                   value="<%=props.getProperty("phoneprefix", "905-")%>">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.Ext"/>:<input
                                        type="text" id="hPhoneExt" name="hPhoneExt" value="" size="4"/></td>
                            <td id="phoneWorkLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPhoneWork"/>:</b></td>
                            <td id="phoneWorkCell" align="left"><input type="text" name="phone2"
                                                                       onBlur="formatPhoneNum()" value=""> <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.Ext"/>:<input type="text"
                                                                                           name="wPhoneExt" value=""
                                                                                           style="display: inline"
                                                                                           size="4"/></td>
                        </tr>
                        <tr valign="top">
                            <td id="phoneCellLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPhoneCell"/>: </b></td>
                            <td id="phoneCellCell" align="left"><input type="text" name="demo_cell"
                                                                       onBlur="formatPhoneNum()"></td>
                            <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPhoneComment"/>: </b></td>
                            <td align="left" colspan="3">
                                <textarea rows="2" cols="30" name="phoneComment"></textarea>
                            </td>
                        </tr>
                        <tr valign="top">
                            <td id="newsletterLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter"/>: </b></td>
                            <td id="newsletterCell" align="left"><select name="newsletter">
                                <option value="Unknown" selected><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optUnknown"/></option>
                                <option value="No"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optNo"/></option>
                                <option value="Paper"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optPaper"/></option>
                                <option value="Electronic"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optElectronic"/></option>
                            </select></td>
                            <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.aboriginal"/>: </b>
                            </td>
                            <td align="left">
                                <select name="aboriginal">
                                    <option value="">Unknown</option>
                                    <option value="No">No</option>
                                    <option value="Yes">Yes</option>
                                </select>
                        </tr>
                        <tr valign="top">
                            <td id="emailLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formEMail"/>: </b></td>
                            <td id="emailCell" align="left"><input type="text" id="email" name="email" value="">
                            </td>
                        </tr>
                        <tr valign="top">
                            <td id="dobLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formDOB"/><span
                                    style="color:red;">:</span></b></td>
                            <td id="dobTbl" align="left">
                                <table>
                                    <tr>
                                        <td><input type="text" name="year_of_birth" placeholder="yyyy"
                                                   id="year_of_birth"
                                                   maxlength="4"></td>

                                        <td>
                                            <select name="month_of_birth" id="month_of_birth">
                                                <option value="01">01
                                                <option value="02">02
                                                <option value="03">03
                                                <option value="04">04
                                                <option value="05">05
                                                <option selected value="06">06
                                                <option value="07">07
                                                <option value="08">08
                                                <option value="09">09
                                                <option value="10">10
                                                <option value="11">11
                                                <option value="12">12
                                            </select></td>

                                        <td>
                                            <select name="date_of_birth" id="date_of_birth">
                                                <option value="01">01
                                                <option value="02">02
                                                <option value="03">03
                                                <option value="04">04
                                                <option value="05">05
                                                <option value="06">06
                                                <option value="07">07
                                                <option value="08">08
                                                <option value="09">09
                                                <option value="10">10
                                                <option value="11">11
                                                <option value="12">12
                                                <option value="13">13
                                                <option value="14">14
                                                <option selected value="15">15
                                                <option value="16">16
                                                <option value="17">17
                                                <option value="18">18
                                                <option value="19">19
                                                <option value="20">20
                                                <option value="21">21
                                                <option value="22">22
                                                <option value="23">23
                                                <option value="24">24
                                                <option value="25">25
                                                <option value="26">26
                                                <option value="27">27
                                                <option value="28">28
                                                <option value="29">29
                                                <option value="30">30
                                                <option value="31">31
                                            </select></td>
                                    </tr>
                                </table>
                            </td>

                            <td style="text-align: right;">
                                <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPronouns"/></strong>
                            </td>
                            <td style="text-align: left;">
                                <input type="text" id="patientPronouns" name="pronouns"/>
                            </td>
                        </tr>
                        <tr>
                            <td align="right" id="genderLbl"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formSex"/><font
                                    color="red">:</font></b></td>

                            <% // Determine if curUser has selected a default sex in preferences
                                UserProperty sexProp = userPropertyDAO.getProp(curUser_no, UserProperty.DEFAULT_SEX);
                                String sex = "";
                                if (sexProp != null) {
                                    sex = sexProp.getValue();
                                } else {
                                    // Access defaultsex system property
                                    sex = props.getProperty("defaultsex", "");
                                }
                            %>
                            <td id="gender" align="left">

                                <select name="sex" id="sex">
                                    <option value=""></option>
                                    <% for (Gender gn : Gender.values()) { %>
                                    <option value="<%=gn.name()%>" <%=((sex.toUpperCase().equals(gn.name())) ? "selected=\"selected\"" : "") %>><%=gn.getText()%>
                                    </option>
                                    <% } %>
                                </select>

                            </td>

                            <td style="text-align: right;">
                                <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formGender"/></strong>
                            </td>
                            <td style="text-align: left;">
                                <input type="text" id="patientGender" name="gender"/>
                            </td>
                        </tr>


                        <tr valign="top">
                            <td align="right" id="hinLbl"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formHIN"/>: </b></td>
                            <td align="left" id="hinVer">
                                <input type="text" name="hin" id="hin" onfocus="autoFillHin()">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formVer"/>:
                                <input type="text" id="ver" name="ver" value="" onBlur="upCaseCtrl(this)">
                            </td>
                            <td id="effDateLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formEFFDate"/>: </b></td>
                            <td id="effDate" align="left">
                                <input type="text" placeholder="yyyy" id="eff_date_year" name="eff_date_year"
                                       maxlength="4">
                                <input type="text" placeholder="mm" id="eff_date_month" name="eff_date_month"
                                       maxlength="2">
                                <input type="text" placeholder="dd" id="eff_date_date" name="eff_date_date"
                                       maxlength="2">
                            </td>
                        </tr>
                        <tr>
                            <td id="hcTypeLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formHCType"/>: </b></td>
                            <td id="hcType">

                                <select name="hc_type" id="hc_type">
                                    <option value="OT"
                                            <%=HCType.equals("") || HCType.equals("OT") ? " selected" : ""%>>Other
                                    </option>
                                    <% if (pNames.isDefined()) {
                                        for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                            String province = (String) li.next(); %>
                                    <option value="<%=province%>"<%=province.equals(HCType) ? " selected" : ""%>><%=li.next()%>
                                    </option>
                                    <% } %>
                                    <% } else { %>
                                    <option value="AB"<%=HCType.equals("AB") ? " selected" : ""%>>AB-Alberta</option>
                                    <option value="BC"<%=HCType.equals("BC") ? " selected" : ""%>>BC-British Columbia
                                    </option>
                                    <option value="MB"<%=HCType.equals("MB") ? " selected" : ""%>>MB-Manitoba</option>
                                    <option value="NB"<%=HCType.equals("NB") ? " selected" : ""%>>NB-New Brunswick
                                    </option>
                                    <option value="NL"<%=HCType.equals("NL") ? " selected" : ""%>>NL-Newfoundland &
                                        Labrador
                                    </option>
                                    <option value="NT"<%=HCType.equals("NT") ? " selected" : ""%>>NT-Northwest
                                        Territory
                                    </option>
                                    <option value="NS"<%=HCType.equals("NS") ? " selected" : ""%>>NS-Nova Scotia
                                    </option>
                                    <option value="NU"<%=HCType.equals("NU") ? " selected" : ""%>>NU-Nunavut</option>
                                    <option value="ON"<%=HCType.equals("ON") ? " selected" : ""%>>ON-Ontario</option>
                                    <option value="PE"<%=HCType.equals("PE") ? " selected" : ""%>>PE-Prince Edward
                                        Island
                                    </option>
                                    <option value="QC"<%=HCType.equals("QC") ? " selected" : ""%>>QC-Quebec</option>
                                    <option value="SK"<%=HCType.equals("SK") ? " selected" : ""%>>SK-Saskatchewan
                                    </option>
                                    <option value="YT"<%=HCType.equals("YT") ? " selected" : ""%>>YT-Yukon</option>
                                    <option value="US"<%=HCType.equals("US") ? " selected" : ""%>>US resident</option>
                                    <option value="US-AK" <%=HCType.equals("US-AK") ? " selected" : ""%>>US-AK-Alaska
                                    </option>
                                    <option value="US-AL" <%=HCType.equals("US-AL") ? " selected" : ""%>>US-AL-Alabama
                                    </option>
                                    <option value="US-AR" <%=HCType.equals("US-AR") ? " selected" : ""%>>
                                        US-AR-Arkansas
                                    </option>
                                    <option value="US-AZ" <%=HCType.equals("US-AZ") ? " selected" : ""%>>US-AZ-Arizona
                                    </option>
                                    <option value="US-CA" <%=HCType.equals("US-CA") ? " selected" : ""%>>
                                        US-CA-California
                                    </option>
                                    <option value="US-CO" <%=HCType.equals("US-CO") ? " selected" : ""%>>
                                        US-CO-Colorado
                                    </option>
                                    <option value="US-CT" <%=HCType.equals("US-CT") ? " selected" : ""%>>
                                        US-CT-Connecticut
                                    </option>
                                    <option value="US-CZ" <%=HCType.equals("US-CZ") ? " selected" : ""%>>US-CZ-Canal
                                        Zone
                                    </option>
                                    <option value="US-DC" <%=HCType.equals("US-DC") ? " selected" : ""%>>US-DC-District
                                        Of Columbia
                                    </option>
                                    <option value="US-DE" <%=HCType.equals("US-DE") ? " selected" : ""%>>
                                        US-DE-Delaware
                                    </option>
                                    <option value="US-FL" <%=HCType.equals("US-FL") ? " selected" : ""%>>US-FL-Florida
                                    </option>
                                    <option value="US-GA" <%=HCType.equals("US-GA") ? " selected" : ""%>>US-GA-Georgia
                                    </option>
                                    <option value="US-GU" <%=HCType.equals("US-GU") ? " selected" : ""%>>US-GU-Guam
                                    </option>
                                    <option value="US-HI" <%=HCType.equals("US-HI") ? " selected" : ""%>>US-HI-Hawaii
                                    </option>
                                    <option value="US-IA" <%=HCType.equals("US-IA") ? " selected" : ""%>>US-IA-Iowa
                                    </option>
                                    <option value="US-ID" <%=HCType.equals("US-ID") ? " selected" : ""%>>US-ID-Idaho
                                    </option>
                                    <option value="US-IL" <%=HCType.equals("US-IL") ? " selected" : ""%>>
                                        US-IL-Illinois
                                    </option>
                                    <option value="US-IN" <%=HCType.equals("US-IN") ? " selected" : ""%>>US-IN-Indiana
                                    </option>
                                    <option value="US-KS" <%=HCType.equals("US-KS") ? " selected" : ""%>>US-KS-Kansas
                                    </option>
                                    <option value="US-KY" <%=HCType.equals("US-KY") ? " selected" : ""%>>
                                        US-KY-Kentucky
                                    </option>
                                    <option value="US-LA" <%=HCType.equals("US-LA") ? " selected" : ""%>>
                                        US-LA-Louisiana
                                    </option>
                                    <option value="US-MA" <%=HCType.equals("US-MA") ? " selected" : ""%>>
                                        US-MA-Massachusetts
                                    </option>
                                    <option value="US-MD" <%=HCType.equals("US-MD") ? " selected" : ""%>>
                                        US-MD-Maryland
                                    </option>
                                    <option value="US-ME" <%=HCType.equals("US-ME") ? " selected" : ""%>>US-ME-Maine
                                    </option>
                                    <option value="US-MI" <%=HCType.equals("US-MI") ? " selected" : ""%>>
                                        US-MI-Michigan
                                    </option>
                                    <option value="US-MN" <%=HCType.equals("US-MN") ? " selected" : ""%>>
                                        US-MN-Minnesota
                                    </option>
                                    <option value="US-MO" <%=HCType.equals("US-MO") ? " selected" : ""%>>
                                        US-MO-Missouri
                                    </option>
                                    <option value="US-MS" <%=HCType.equals("US-MS") ? " selected" : ""%>>
                                        US-MS-Mississippi
                                    </option>
                                    <option value="US-MT" <%=HCType.equals("US-MT") ? " selected" : ""%>>US-MT-Montana
                                    </option>
                                    <option value="US-NC" <%=HCType.equals("US-NC") ? " selected" : ""%>>US-NC-North
                                        Carolina
                                    </option>
                                    <option value="US-ND" <%=HCType.equals("US-ND") ? " selected" : ""%>>US-ND-North
                                        Dakota
                                    </option>
                                    <option value="US-NE" <%=HCType.equals("US-NE") ? " selected" : ""%>>
                                        US-NE-Nebraska
                                    </option>
                                    <option value="US-NH" <%=HCType.equals("US-NH") ? " selected" : ""%>>US-NH-New
                                        Hampshire
                                    </option>
                                    <option value="US-NJ" <%=HCType.equals("US-NJ") ? " selected" : ""%>>US-NJ-New
                                        Jersey
                                    </option>
                                    <option value="US-NM" <%=HCType.equals("US-NM") ? " selected" : ""%>>US-NM-New
                                        Mexico
                                    </option>
                                    <option value="US-NU" <%=HCType.equals("US-NU") ? " selected" : ""%>>US-NU-Nunavut
                                    </option>
                                    <option value="US-NV" <%=HCType.equals("US-NV") ? " selected" : ""%>>US-NV-Nevada
                                    </option>
                                    <option value="US-NY" <%=HCType.equals("US-NY") ? " selected" : ""%>>US-NY-New
                                        York
                                    </option>
                                    <option value="US-OH" <%=HCType.equals("US-OH") ? " selected" : ""%>>US-OH-Ohio
                                    </option>
                                    <option value="US-OK" <%=HCType.equals("US-OK") ? " selected" : ""%>>
                                        US-OK-Oklahoma
                                    </option>
                                    <option value="US-OR" <%=HCType.equals("US-OR") ? " selected" : ""%>>US-OR-Oregon
                                    </option>
                                    <option value="US-PA" <%=HCType.equals("US-PA") ? " selected" : ""%>>
                                        US-PA-Pennsylvania
                                    </option>
                                    <option value="US-PR" <%=HCType.equals("US-PR") ? " selected" : ""%>>US-PR-Puerto
                                        Rico
                                    </option>
                                    <option value="US-RI" <%=HCType.equals("US-RI") ? " selected" : ""%>>US-RI-Rhode
                                        Island
                                    </option>
                                    <option value="US-SC" <%=HCType.equals("US-SC") ? " selected" : ""%>>US-SC-South
                                        Carolina
                                    </option>
                                    <option value="US-SD" <%=HCType.equals("US-SD") ? " selected" : ""%>>US-SD-South
                                        Dakota
                                    </option>
                                    <option value="US-TN" <%=HCType.equals("US-TN") ? " selected" : ""%>>
                                        US-TN-Tennessee
                                    </option>
                                    <option value="US-TX" <%=HCType.equals("US-TX") ? " selected" : ""%>>US-TX-Texas
                                    </option>
                                    <option value="US-UT" <%=HCType.equals("US-UT") ? " selected" : ""%>>US-UT-Utah
                                    </option>
                                    <option value="US-VA" <%=HCType.equals("US-VA") ? " selected" : ""%>>
                                        US-VA-Virginia
                                    </option>
                                    <option value="US-VI" <%=HCType.equals("US-VI") ? " selected" : ""%>>US-VI-Virgin
                                        Islands
                                    </option>
                                    <option value="US-VT" <%=HCType.equals("US-VT") ? " selected" : ""%>>US-VT-Vermont
                                    </option>
                                    <option value="US-WA" <%=HCType.equals("US-WA") ? " selected" : ""%>>
                                        US-WA-Washington
                                    </option>
                                    <option value="US-WI" <%=HCType.equals("US-WI") ? " selected" : ""%>>
                                        US-WI-Wisconsin
                                    </option>
                                    <option value="US-WV" <%=HCType.equals("US-WV") ? " selected" : ""%>>US-WV-West
                                        Virginia
                                    </option>
                                    <option value="US-WY" <%=HCType.equals("US-WY") ? " selected" : ""%>>US-WY-Wyoming
                                    </option>
                                    <% } %>
                                </select>

                            </td>
                            <td id="renewDateLbl" align="right"><b>*<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formHCRenewDate"/>:</b></td>
                            <td id="renewDate" align="left"><input type="text" placeholder="yyyy"
                                                                   id="hc_renew_date_year" name="hc_renew_date_year"
                                                                   size="4" maxlength="4" value="">
                                <input type="text" placeholder="mm" id="hc_renew_date_month" name="hc_renew_date_month"
                                       size="2" maxlength="2" value="">
                                <input type="text" placeholder="dd" id="hc_renew_date_date" name="hc_renew_date_date"
                                       size="2" maxlength="2" value="">
                            </td>
                        </tr>
                        <tr>
                            <td id="countryLbl" align="right">
                                <b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgCountryOfOrigin"/>:</b>
                            </td>
                            <td id="countryCell">
                                <select id="countryOfOrigin" name="countryOfOrigin">
                                    <option value="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgNotSet"/></option>
                                    <%for (CountryCode cc : countryList) { %>
                                    <option value="<%=cc.getCountryId()%>"><%=cc.getCountryName() %>
                                    </option>
                                    <%}%>
                                </select>
                            </td>
                            <oscar:oscarPropertiesCheck property="privateConsentEnabled" value="true">
                                <%
                                    String[] privateConsentPrograms = OscarProperties.getInstance().getProperty("privateConsentPrograms", "").split(",");
                                    ProgramProvider pp2 = programManager2.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
                                    boolean showConsentsThisTime = false;
                                    if (pp2 != null) {
                                        for (int x = 0; x < privateConsentPrograms.length; x++) {
                                            if (privateConsentPrograms[x].equals(pp2.getProgramId().toString())) {
                                                showConsentsThisTime = true;
                                            }
                                        }
                                    }

                                    if (showConsentsThisTime) {
                                %>
                                <td colspan="2">

                                    <input type="radio" name="usSigned" value="signed">U.S. Resident Consent Form Signed

                                    <input type="radio" name="usSigned" value="unsigned">U.S. Resident Consent Form NOT
                                    Signed

                                </td>
                                <% } %>
                            </oscar:oscarPropertiesCheck>
                            <oscar:oscarPropertiesCheck property="privateConsentEnabled" value="false">
                                <td><!-- placeholder --></td>
                                <td><!-- placeholder --></td>
                            </oscar:oscarPropertiesCheck>
                        </tr>


                        <tr valign="top">
                                <%-- TOGGLE FIRST NATIONS MODULE --%>
                            <oscar:oscarPropertiesCheck value="true" defaultVal="false" property="FIRST_NATIONS_MODULE">
                                <jsp:include page="manageFirstNationsModule.jsp" flush="true">
                                    <jsp:param name="demo" value="0"/>
                                </jsp:include>
                            </oscar:oscarPropertiesCheck>
                                <%-- END TOGGLE FIRST NATIONS MODULE --%>
                            <td id="sinNoLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgSIN"/>:</b></td>
                            <td id="sinNoCell" align="left">
                                <input type="text" name="sin">
                            </td>


                            <td id="cytologyLbl" align="right"><b> <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.cytolNum"/>:</b></td>
                            <td id="cytologyCell" align="left">
                                <input type="text" name="cytolNum">

                            </td>
                        </tr>
                        <tr valign="top">
                            <td id="demoDoctorLbl" align="right">
                                <b><% if (oscarProps.getProperty("demographicLabelDoctor") != null) {
                                    out.print(oscarProps.getProperty("demographicLabelDoctor", ""));
                                } else { %>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formDoctor"/> <% } %>
                                    : </b></td>
                            <td id="demoDoctorCell" align="left">
                                <select name="staff">
                                    <option value=""></option>
                                    <%
                                        for (Provider p : providerDao.getActiveProvidersByRole("doctor")) {
                                            String docProviderNo = p.getProviderNo();
                                    %>
                                    <option id="doc<%=docProviderNo%>"
                                            value="<%=docProviderNo%>"><%=Encode.forHtmlContent(p.getFormattedName())%>
                                    </option>
                                    <%
                                        }
                                    %>
                                    <option value=""></option>
                                </select></td>
                            <td id="nurseLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNurse"/>: </b></td>
                            <td id="nurseCell"><select name="cust1">
                                <option value=""></option>
                                <%
                                    for (Provider p : providerDao.getActiveProvidersByRole("nurse")) {
                                %>
                                <option value="<%=p.getProviderNo()%>"><%=Encode.forHtmlContent(p.getFormattedName())%>
                                </option>
                                <%
                                    }

                                %>
                            </select></td>
                        </tr>
                        <tr valign="top">
                            <td id="midwifeLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formMidwife"/>: </b></td>
                            <td id="midwifeCell"><select name="cust4">
                                <option value=""></option>
                                <%
                                    for (Provider p : providerDao.getActiveProvidersByRole("midwife")) {
                                %>
                                <option value="<%=p.getProviderNo()%>">
                                    <%=Encode.forHtmlContent(p.getFormattedName())%>
                                </option>
                                <%
                                    }

                                %>
                            </select></td>
                            <td id="residentLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formResident"/>: </b></td>
                            <td id="residentCell" align="left"><select name="cust2">
                                <option value=""></option>
                                <%
                                    for (Provider p : providerDao.getActiveProvidersByRole("doctor")) {
                                %>
                                <option value="<%=p.getProviderNo()%>">
                                    <%=Encode.forHtmlContent(p.getFormattedName())%>
                                </option>
                                <%
                                    }

                                %>
                            </select></td>
                        </tr>
                        <tr id="rowWithReferralDoc" valign="top">
                            <td id="referralDocLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formReferalDoctor"/>:</b></td>
                            <td id="referralDocCell" align="left">
                                <% if ("true".equals(oscarProps.getProperty("isMRefDocSelectList", ""))) {
                                    // drop down list
                                    Properties prop = null;
                                    Vector vecRef = new Vector();

                                    List<ProfessionalSpecialist> specialists = professionalSpecialistDao.findAll();
                                    for (ProfessionalSpecialist specialist : specialists) {

                                        if (specialist != null && specialist.getReferralNo() != null && !specialist.getReferralNo().equals("")) {
                                            prop = new Properties();
                                            prop.setProperty("referral_no", specialist.getReferralNo());
                                            prop.setProperty("last_name", specialist.getLastName());
                                            prop.setProperty("first_name", specialist.getFirstName());
                                            vecRef.add(prop);
                                        }
                                    }
                                %> <select name="r_doctor"
                                           onChange="changeRefDoc()">
                                <option value=""></option>
                                <% for (int k = 0; k < vecRef.size(); k++) {
                                    prop = (Properties) vecRef.get(k);
                                %>
                                <option
                                        value="<%=prop.getProperty("last_name")+","+prop.getProperty("first_name")%>">
                                    <%=Misc.getShortStr((prop.getProperty("last_name") + "," + prop.getProperty("first_name")), "", nStrShowLen)%>
                                </option>
                                <% } %>
                            </select>
                                <script language="Javascript">

                                    function changeRefDoc() {
//alert(document.forms[1].r_doctor.value);
                                        var refName = document.forms[1].r_doctor.options[document.forms[1].r_doctor.selectedIndex].value;
                                        var refNo = "";
                                        <% for(int k=0; k<vecRef.size(); k++) {
  		prop= (Properties) vecRef.get(k);
  	%>
                                        if (refName.indexOf("<%=prop.getProperty("last_name")+","+prop.getProperty("first_name")%>") >= 0) {
                                            refNo = '<%=prop.getProperty("referral_no", "")%>';
                                        }
                                        <% } %>
                                        document.forms[1].r_doctor_ohip.value = refNo;
                                    }

                                </script>
                                <% } else {%> <input type="text" name="r_doctor" maxlength="40"
                                                     value=""> <% } %>
                            </td>
                            <td id="referralDocNoLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formReferalDoctorN"/>:</b></td>
                            <td id="referralDocNoCell" align="left"><input type="text"
                                                                           name="r_doctor_ohip"
                                                                           maxlength="6"> <% if ("ON".equals(prov)) { %>
                                <a
                                        href="javascript:referralScriptAttach2('r_doctor_ohip','r_doctor')"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnSearch"/>
                                    #</a> <% } %>
                            </td>
                        </tr>
                        <tr valign="top">
                            <td align="right" id="rosterStatusLbl" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPCNRosterStatus"/>: </b></td>
                            <td id="rosterStatus" align="left">
                                <!--input type="text" name="roster_status" onBlur="upCaseCtrl(this)"-->
                                <select id="roster_status" name="roster_status" style="width: 160px">
                                    <option value=""></option>
                                    <option value="RO"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.RO-rostered"/></option>
                                    <option value="FS"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.FS-feeforservice"/></option>

                                    <%
                                        for (String status : demographicDao.getRosterStatuses()) {%>
                                    <option value="<%=status%>"><%=status%>
                                    </option>
                                    <% } // end while %>
                                </select> <input type="button" onClick="newStatus1();" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.AddNewRosterStatus"/> "/></td>
                            <td id="rosterDateLbl" align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPCNDateJoined"/>: </b></td>
                            <td class="rosterDateCell" align="left"><input type="text" name="roster_date_year"
                                                                           size="4" maxlength="4"> <input type="text"
                                                                                                          name="roster_date_month"
                                                                                                          size="2"
                                                                                                          maxlength="2">
                                <input
                                        type="text" name="roster_date_date" size="2" maxlength="2">
                            </td>
                        </tr>
                        <tr valign="top">
                            <td align="right" id="rosterEnrolledToLbl"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formRosterEnrolledTo"/>: </b></td>
                            <td id="rosterEnrolledTo" align="left">
                                <select id="roster_enrolled_to" name="roster_enrolled_to">
                                    <option value=""></option>
                                            <%
						for (Provider p : providerDao.getActiveProvidersByRole("doctor")) {
								String docProviderNo = p.getProviderNo();
					%>
                                    <option id="<%=docProviderNo%>" value="<%=docProviderNo%>"><%=p.getFormattedName()%>
                                    </option>
                                            <%
						}
					%>
                                    <option value=""></option>
                            </td>
                            <td id="chartNoLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formChartNo"/>:</b></td>
                            <td id="chartNo" align="left"><input type="text" id="chart_no" name="chart_no"
                                                                 value="<%=StringEscapeUtils.escapeHtml4(chartNoVal)%>">
                            </td>

                        </tr>
                        <tr valign="top">
                            <td id="ptStatusLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPatientStatus"/>:</b></td>
                            <td id="ptStatusCell" align="left">
                                <select id="patient_status" name="patient_status" style="width: 160px">
                                    <option value="AC"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.AC-Active"/></option>
                                    <option value="IN"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.IN-InActive"/></option>
                                    <option value="DE"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.DE-Deceased"/></option>
                                    <option value="MO"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.MO-Moved"/></option>
                                    <option value="FI"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.FI-Fired"/></option>
                                    <%
                                        for (String status : demographicDao.search_ptstatus()) { %>
                                    <option value="<%=status%>"><%=status%>
                                    </option>
                                    <% } // end while %>
                                </select> <input type="button" onClick="newStatus();" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.AddNewPatient"/> ">

                            </td>
                            <td align="right" nowrap>
                                <b>Patient Status Date:</b>
                            </td>
                            <td align="left">
                                <input type="text" placeholder="yyyy-mm-dd"
                                       name="patient_status_date" id="patient_status_date"
                                       value="<%=today %>" size="12"> <img
                                    src="<%= request.getContextPath() %>/images/cal.gif" id="patient_status_date_cal">
                            </td>
                        </tr>


                        <tr valign="top">
                            <td id="joinDateLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formDateJoined"/></b><b>:
                            </b></td>
                            <td id="joinDateCell" align="left"><input type="text" name="date_joined_year"
                                                                      placeholder="yyyy"
                                                                      size="4" maxlength="4" value="<%=curYear%>">
                                <input
                                        type="text" placeholder="mm" name="date_joined_month" size="2" maxlength="2"
                                        value="<%=curMonth%>"> <input type="text" placeholder="dd"
                                                                      name="date_joined_date" size="2" maxlength="2"
                                                                      value="<%=curDay%>">
                            </td>
                            <td id="endDateLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formEndDate"/></b><b>: </b></td>
                            <td id="endDateCell" align="left"><input type="text" placeholder="yyyy" name="end_date_year"
                                                                     size="4" maxlength="4"> <input type="text"
                                                                                                    placeholder="mm"
                                                                                                    name="end_date_month"
                                                                                                    size="2"
                                                                                                    maxlength="2">
                                <input
                                        type="text" placeholder="dd" name="end_date_date" size="2" maxlength="2"></td>
                        </tr>

                        <tr valign="top">
                            <td id="phuLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPHU"/>:</b></td>
                            <td id="phuLblCell" align="left">
                                <select id="PHU" name="PHU">
                                    <option value="">Select Below</option>
                                    <%
                                        String defaultPhu = OscarProperties.getInstance().getProperty("default_phu");

                                        LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);
                                        LookupList ll = lookupListManager.findLookupListByName(LoggedInInfo.getLoggedInInfoFromSession(request), "phu");
                                        if (ll != null) {
                                            for (LookupListItem llItem : ll.getItems()) {
                                                if (llItem.isActive()) {
                                                    String selected = "";
                                                    if (llItem.getValue().equals(defaultPhu)) {
                                                        selected = " selected=\"selected\" ";
                                                    }
                                    %>
                                    <option value="<%=llItem.getValue()%>" <%=selected%>><%=llItem.getLabel()%>
                                    </option>
                                    <%
                                            }
                                        }
                                    } else {
                                    %>
                                    <option value="">None Available</option>
                                    <%
                                        }

                                    %>
                                </select>
                            </td>
                            <td><!-- placeholder --></td>
                            <td><!-- placeholder --></td>
                        </tr>


                        <% //"Has Primary Care Physician" & "Employment Status" fields
                            final String hasPrimary = "Has Primary Care Physician";
                            final String empStatus = "Employment Status";
                            boolean hasHasPrimary = oscarProps.isPropertyActive("showPrimaryCarePhysicianCheck");
                            boolean hasEmpStatus = oscarProps.isPropertyActive("showEmploymentStatus");
                            String hasPrimaryCarePhysician = "N/A";
                            String employmentStatus = "N/A";

                            if (hasHasPrimary || hasEmpStatus) {
                        %>
                        <tr valign="top">
                            <% if (hasHasPrimary) {
                            %>
                            <td style="text-align: right;"><b><%=hasPrimary.replace(" ", "&nbsp;")%>:</b></td>
                            <td>
                                <select name="<%=hasPrimary.replace(" ", "")%>">
                                    <option value="N/A" <%="N/A".equals(hasPrimaryCarePhysician) ? "selected" : ""%>>
                                        N/A
                                    </option>
                                    <option value="Yes" <%="Yes".equals(hasPrimaryCarePhysician) ? "selected" : ""%>>
                                        Yes
                                    </option>
                                    <option value="No" <%="No".equals(hasPrimaryCarePhysician) ? "selected" : ""%>>No
                                    </option>
                                </select>
                            </td>
                            <% }
                                if (hasEmpStatus) {
                            %>
                            <td style="text-align: right;"><b><%=empStatus.replace(" ", "&nbsp;")%>:</b></td>
                            <td>
                                <select name="<%=empStatus.replace(" ", "")%>">
                                    <option value="N/A" <%="N/A".equals(employmentStatus) ? "selected" : ""%>>N/A
                                    </option>
                                    <option value="FULL TIME" <%="FULL TIME".equals(employmentStatus) ? "selected" : ""%>>
                                        FULL TIME
                                    </option>
                                    <option value="ODSP" <%="ODSP".equals(employmentStatus) ? "selected" : ""%>>ODSP
                                    </option>
                                    <option value="OW" <%="OW".equals(employmentStatus) ? "selected" : ""%>>OW</option>
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

//customized key
                            if (oscarVariables.getProperty("demographicExt") != null) {
                                boolean bExtForm = oscarVariables.getProperty("demographicExtForm") != null ? true : false;
                                String[] propDemoExtForm = bExtForm ? (oscarVariables.getProperty("demographicExtForm", "").split("\\|")) : null;
                                String[] propDemoExt = oscarVariables.getProperty("demographicExt", "").split("\\|");
                                for (int k = 0; k < propDemoExt.length; k = k + 2) {
                        %>
                        <tr valign="top">
                            <td style="text-align: right;"><b><%=propDemoExt[k] %>
                            </b><b>: </b></td>
                            <td style="text-align: left;">
                                <% if (bExtForm) {
                                    out.println(propDemoExtForm[k]);
                                } else { %>
                                <input type="text" name="<%=propDemoExt[k].replace(' ', '_') %>" value="">
                                <% } %>
                            </td>
                            <td style="text-align: right;"><%=(k + 1) < propDemoExt.length ? ("<b>" + propDemoExt[k + 1] + ": </b>") : "&nbsp;" %>
                            </td>
                            <td style="text-align: left;">
                                <% if (bExtForm && (k + 1) < propDemoExt.length) {
                                    out.println(propDemoExtForm[k + 1]);
                                } else { %> <%=(k + 1) < propDemoExt.length ? "<input type=\"text\" name=\"" + propDemoExt[k + 1].replace(' ', '_') + "\"  value=''>" : "&nbsp;" %>
                                <% } %>
                            </td>
                        </tr>
                        <% }
                        }
                            if (oscarVariables.getProperty("demographicExtJScript") != null) {
                                out.println(oscarVariables.getProperty("demographicExtJScript"));
                            }
                        %>


                        <%
                            if (oscarProps.getProperty("EXTRA_DEMO_FIELDS") != null) {
                                String fieldJSP = oscarProps.getProperty("EXTRA_DEMO_FIELDS");
                                fieldJSP += ".jsp";
                        %>
                        <tr>
                            <td colspan="4">
                                <jsp:include page="<%=fieldJSP%>"/>

                                <%}%>
                            </td>
                        </tr>


                        <%
                            String wLReadonly = "";
                            WaitingList wL = WaitingList.getInstance();
                            if (!wL.getFound()) {
                                wLReadonly = "readonly";
                            }
                        %>
                        <tr>
                            <td id="waitListTbl" colspan="4">
                                <table border="1" width="100%">
                                    <tr valign="top">
                                        <td align="right"><b> <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddarecordhtm.msgWaitList"/>: </b></td>
                                        <td align="left"><select id="name_list_id" name="list_id">
                                            <% if (wLReadonly.equals("")) { %>
                                            <option value="0">--Select Waiting List--</option>
                                            <%} else { %>
                                            <option value="0"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddarecordhtm.optCreateWaitList"/>
                                            </option>
                                            <%} %>
                                            <%
                                                for (WaitingListName wln : waitingListNameDao.findCurrentByGroup(((ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE)).getMyGroupNo())) {

                                            %>
                                            <option value="<%=wln.getId()%>"><%=wln.getName()%>
                                            </option>
                                            <%
                                                }
                                            %>
                                        </select></td>
                                        <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddarecordhtm.msgWaitListNote"/>: </b></td>
                                        <td align="left"><input type="text" id="waiting_list_note"
                                                                name="waiting_list_note"
                                                <%=wLReadonly%>></td>
                                    </tr>

                                    <tr>

                                        <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddarecordhtm.msgDateOfReq"/>:</b></td>
                                        <td align="left"><input type="text" placeholder="yyyy-mm-dd"
                                                                name="waiting_list_referral_date"
                                                                id="waiting_list_referral_date"
                                                                value="" size="12" <%=wLReadonly%>> <img
                                                src="<%= request.getContextPath() %>/images/cal.gif" id="referral_date_cal">
                                        </td>
                                        <td><!-- placeholder --></td>
                                        <td><!-- placeholder --></td>
                                    </tr>
                                </table>
                            </td>
                        </tr>


                            <%-- TOGGLE PRIVACY CONSENT MODULE --%>
                        <oscar:oscarPropertiesCheck property="privateConsentEnabled" value="true">

                            <%
                                String[] privateConsentPrograms2 = OscarProperties.getInstance().getProperty("privateConsentPrograms", "").split(",");
                                ProgramProvider pp3 = programManager2.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
                                boolean showConsents = false;
                                if (pp3 != null) {
                                    for (int x = 0; x < privateConsentPrograms2.length; x++) {
                                        if (privateConsentPrograms2[x].equals(pp3.getProgramId().toString())) {
                                            showConsents = true;
                                        }
                                    }
                                }

                                if (showConsents) { %>
                            <!-- consents -->
                            <tr valign="top">

                                <td colspan="4">
                                    <input type="checkbox" name="privacyConsent" value="yes"><b>Privacy Consent (verbal)
                                    Obtained</b>
                                    <br/>
                                    <input type="checkbox" name="informedConsent" value="yes"><b>Informed Consent
                                    (verbal) Obtained</b>
                                    <br/>
                                </td>

                            </tr>

                            <% } %>

                            <oscar:oscarPropertiesCheck property="USE_NEW_PATIENT_CONSENT_MODULE" value="true">

                                <c:forEach items="${ consentTypes }" var="consentType" varStatus="count">
                                    <c:set var="patientConsent" value=""/>
                                    <c:forEach items="${ patientConsents }" var="consent">
                                        <c:if test="${ consent.consentType.id eq consentType.id }">
                                            <c:set var="patientConsent" value="${ consent }"/>
                                        </c:if>
                                    </c:forEach>
                                    <tr class="privacyConsentRow" id="${ count.index }">
                                        <td class="alignRight" style="width:16%;vertical-align:top;">
                                            <div style="font-weight:bold;white-space:nowrap;">
                                                <c:out value="${ consentType.name }"/>
                                            </div>
                                        </td>

                                        <td colspan="2" style="padding-left:10px;vertical-align:top;">
                                            <c:out value="${ consentType.description }"/>
                                        </td>

                                        <td id="consentStatusDate" style="width:31%;vertical-align:top;">
                                            <input type="radio"
                                                   name="${ consentType.type }"
                                                   id="optin_${ consentType.type }"
                                                   value="0"
                                            />
                                            <label for="optin_${ consentType.type }">Opt-In</label>
                                            <input type="radio"
                                                   name="${ consentType.type }"
                                                   id="optout_${ consentType.type }"
                                                   value="1"
                                            />
                                            <label for="optout_${ consentType.type }">Opt-Out</label>
                                            <input type="button"
                                                   name="clearRadio_${consentType.type}_btn"
                                                   onclick="consentClearBtn('${consentType.type}')" value="Clear"/>

                                        </td>

                                    </tr>
                                </c:forEach>

                            </oscar:oscarPropertiesCheck>

                        </oscar:oscarPropertiesCheck>

                        <tr valign="top">
                            <td colspan="4">
                                <table>
                                    <tr bgcolor="#CCCCFF" class="category_table_heading">
                                        <th colspan="2" class="alignLeft">Program Admissions</th>
                                    </tr>
                                    <tr>
                                        <td>Residential Status:</td>
                                        <td>Service Programs</td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <select id="rsid" name="rps">
                                                <%
                                                    String _pvid = loggedInInfo.getLoggedInProviderNo();
                                                    Program[] bedP = new Program[0];
                                                    Program oscarProgram = programDao.getProgramByName("OSCAR");
                                                    
                                                    // Always use OSCAR program as default if it exists
                                                    if (oscarProgram != null) {
                                                %>
                                                <option value="<%=oscarProgram.getId()%>" selected="selected"><%=oscarProgram.getName()%></option>
                                                <%
                                                    }
                                                    
                                                    for (Program _p : bedP) {
                                                        // Skip OSCAR program since we already added it
                                                        if (oscarProgram != null && _p.getId().equals(oscarProgram.getId())) {
                                                            continue;
                                                        }
                                                %>
                                                <option value="<%=_p.getId()%>"><%=_p.getName()%></option>
                                                <%
                                                    }
                                                    
                                                    // If no OSCAR program and no bed programs, still need a value
                                                    if (oscarProgram == null && bedP.length == 0) {
                                                %>
                                                <option value="">No programs available</option>
                                                <%
                                                    }
                                                %>
                                            </select>
                                        </td>
                                        <td>
                                            <ul>
                                                <%
                                                    List<Program> servP = pm.getServicePrograms();
                                                    for (Program _p : servP) {
                                                %>
                                                <li>
                                                    <input type="checkbox" name="sp"
                                                           value="<%=_p.getId()%>"/><%=_p.getName()%>
                                                </li>
                                                <%}%>
                                            </ul>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>

                        <tr>
                            <td colspan="4">
                                <table width="100%" bgcolor="#EEEEFF">
                                    <tr>
                                        <td id="alertLbl" width="10%" align="right"><font
                                                color="#FF0000"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formAlert"/>: </b></font></td>
                                        <td id="alertCell"><textarea id="cust3" name="cust3" style="width: 100%"
                                                                     rows="2"></textarea>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td id="notesLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNotes"/> : </b></td>
                                        <td id="notesCell"><textarea id="content" name="content" style="width: 100%"
                                                                     rows="2"></textarea>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="4">
                                <div>
                                    <%
                                        //    Integer fid = ((Facility)session.getAttribute("currentFacility")).getRegistrationIntake();
                                        Facility facility = loggedInInfo.getCurrentFacility();
                                        Integer fid = null;
                                        if (facility != null) fid = facility.getRegistrationIntake();
                                        if (fid == null || fid < 0) {
                                            List<EForm> eforms = eformDao.getEfromInGroupByGroupName("Registration Intake");
                                            if (eforms != null && eforms.size() == 1) fid = eforms.get(0).getId();
                                        }
                                        if (fid != null && fid >= 0) {
                                    %>
                                    <iframe scrolling="no" id="eform_iframe" name="eform_iframe" frameborder="0"
                                            src="<%= request.getContextPath() %>/eform/efmshowform_data.jsp?fid=<%=fid%>"
                                            onload="this.height=0;var fdh=(this.Document?this.Document.body.scrollHeight:this.contentDocument.body.offsetHeight);this.height=(fdh>800?fdh:800)"
                                            width="100%"></iframe>
                                    <%}%>
                                </div>
                            </td>
                        </tr>
                        <tr bgcolor="#CCCCFF">
                            <td colspan="4">
                                <input type="hidden" name="dboperation"
                                       value="add_record"> <input type="hidden" name="displaymode" value="Add Record">
                                <input type="submit" id="btnAddRecord" name="btnAddRecord"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.btnAddRecord"/>"/>
                                <input type="button" id="btnSwipeCard" name="Button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.btnSwipeCard"/>"
                                       onclick="window.open('zadddemographicswipe.htm','', 'scrollbars=yes,resizable=yes,width=600,height=300')"
                                       ;>

                                <input type="button" name="closeButton"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.btnCancel"/>"
                                       onclick="self.close();">

                            </td>
                        </tr>
                    </table>
                </form>


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
    <!--<iframe src="<%= request.getContextPath() %>/eform/efmshowform_data.jsp?fid=<%=fid%>" width="100%" height="100%"></iframe>-->
    <%//}%>
    </body>
</html>
