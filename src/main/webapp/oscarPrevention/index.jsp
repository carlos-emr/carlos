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

<%--
    Page    : oscarPrevention/index.jsp
    Purpose : Displays the immunization and screening prevention record for a patient.
              Provides a navigable list of prevention types (immunizations and screenings),
              shows existing prevention records, and allows entry of new immunizations via
              a brand autocomplete selector and lot-number lookup.

    Features:
      - Vaccine brand autocomplete (loaded from eform images or bundled catalogue)
      - Keyboard-navigable brand selector populating brand, dose, route, DIN, and manufacturer
      - Lot-number autocomplete via CVC query endpoint
      - Bootstrap 5 alert-based dismissible SSO / ISPA / non-ISPA warnings
      - Decision-support colour-coded recommendations via Drools (DSPreventionDrools)
      - DHIR (Digital Health Immunization Repository) submission status

    Parameters:
      @param demographic_no  String  patient demographic number (required)

    @since 2005-10-26
--%>

<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ConsentDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CVCMappingDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Consent" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.CVCMapping" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DHIRSubmissionLog" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DHIRSubmissionManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.PreventionManager" %>
<%@ page import="io.github.carlos_emr.carlos.prevention.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LocaleUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.WebUtils" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="org.owasp.encoder.Encode" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.CVCMapping" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.CVCMappingDao" %>
<%@page import="org.apache.commons.lang3.StringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.DHIRSubmissionLog" %>
<%@page import="io.github.carlos_emr.carlos.managers.DHIRSubmissionManager" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Consent" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ConsentDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.utility.WebUtils" %>
<%@page import="io.github.carlos_emr.CarlosProperties" %>
<%@page import="io.github.carlos_emr.carlos.demographic.data.*,java.util.*,io.github.carlos_emr.carlos.prevention.*" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao, io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.LocaleUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.WebUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@page import="io.github.carlos_emr.carlos.managers.PreventionManager" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_prevention" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_prevention");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    DHIRSubmissionManager submissionManager = SpringUtils.getBean(DHIRSubmissionManager.class);
    UserPropertyDAO userPropertyDao = SpringUtils.getBean(UserPropertyDAO.class);

    String demographic_no = request.getParameter("demographic_no");
    try {
        Integer.parseInt(demographic_no);
    } catch (NumberFormatException e) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid demographic number");
        return;
    }
    DemographicData demoData = new DemographicData();
    String nameAge = demoData.getNameAgeString(loggedInInfo, demographic_no);
    Demographic demo = demoData.getDemographic(loggedInInfo, demographic_no);
    String hin = demo.getHin() + demo.getVer();
    String mrp = demo.getProviderNo();
    PreventionManager preventionManager = SpringUtils.getBean(PreventionManager.class);

    PreventionDisplayConfig pdc = PreventionDisplayConfig.getInstance();
    ArrayList<HashMap<String, String>> prevList = pdc.getPreventions();

    ArrayList<Map<String, Object>> configSets = pdc.getConfigurationSets();


    Prevention p = PreventionData.getPrevention(loggedInInfo, Integer.valueOf(demographic_no));

    Integer demographicId = Integer.parseInt(demographic_no);
    Date demographicDateOfBirth = PreventionData.getDemographicDateOfBirth(loggedInInfo, Integer.valueOf(demographic_no));
    String demographicDob = UtilDateUtilities.DateToString(demographicDateOfBirth);

    PreventionDS pf = SpringUtils.getBean(PreventionDS.class);

    CVCMappingDao cvcMappingDao = SpringUtils.getBean(CVCMappingDao.class);

    boolean dsProblems = false;
    try {
        pf.getMessages(p);
    } catch (Exception dsException) {
        MiscUtils.getLogger().error("Error running prevention rules", dsException);
        dsProblems = true;
    }

    ArrayList warnings = p.getWarnings();
    ArrayList recomendations = p.getReminder();

    boolean printError = request.getAttribute("printError") != null;

    boolean dhirEnabled = false;

    if ("true".equals(CarlosProperties.getInstance().getProperty("dhir.enabled", "false"))) {
        dhirEnabled = true;
    }

    ConsentDao consentDao = SpringUtils.getBean(ConsentDao.class);
    Consent ispaConsent = consentDao.findByDemographicAndConsentType(demographicId, "dhir_ispa_consent");
    Consent nonIspaConsent = consentDao.findByDemographicAndConsentType(demographicId, "dhir_non_ispa_consent");

    boolean isSSOLoggedIn = session.getAttribute("oneIdEmail") != null;
    boolean hasIspaConsent = ispaConsent != null && !ispaConsent.isOptout();
    boolean hasNonIspaConsent = nonIspaConsent != null && !nonIspaConsent.isOptout();

    UserProperty ssoWarningUp = userPropertyDao.getProp(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), UserProperty.PREVENTION_SSO_WARNING);
    boolean hideSSOWarning = ssoWarningUp != null && "true".equals(ssoWarningUp.getValue());

    UserProperty ispaWarningUp = userPropertyDao.getProp(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), UserProperty.PREVENTION_ISPA_WARNING);
    boolean hideISPAWarning = ispaWarningUp != null && "true".equals(ispaWarningUp.getValue());

    UserProperty nonIspaWarningUp = userPropertyDao.getProp(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), UserProperty.PREVENTION_NON_ISPA_WARNING);
    boolean hideNonISPAWarning = nonIspaWarningUp != null && "true".equals(nonIspaWarningUp.getValue());

%>


<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">


<%@page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.prevention.PreventionData" %>
<%@ page import="io.github.carlos_emr.carlos.prevention.PreventionDS" %>
<%@ page import="io.github.carlos_emr.carlos.prevention.Prevention" %>
<%@ page import="io.github.carlos_emr.carlos.prevention.PreventionDisplayConfig" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="org.owasp.encoder.Encode" %>
<html>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarprevention.index.oscarpreventiontitre"/></title>
        <!--I18n-->
        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/share/css/OscarStandardLayout.css"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath()%>/library/jquery/jquery-compat.js"></script>
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/css/autocomplete.css">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/library/bootstrap/5.3.3/css/bootstrap.min.css">
        <link href="<%= request.getContextPath() %>/css/fontawesome-all.min.css" rel="stylesheet"><!-- fontawesome 6.x -->
        <script src="<%= request.getContextPath() %>/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>

        <script src="<%= request.getContextPath() %>/share/javascript/popupmenu.js" type="text/javascript"></script>
        <script src="<%= request.getContextPath() %>/share/javascript/menutility.js" type="text/javascript"></script>


        <script>
            function showMenu(menuNumber, eventObj) {
                var menuId = 'menu' + menuNumber;
                return showPopup(menuId, eventObj);
            }
        </script>
        <style type="text/css">
            div.ImmSet {
                background-color: #ffffff;
                clear: left;
                margin-top: 10px;
            }

            div.ImmSet h2 {

            }

            div.ImmSet h2 span {
                font-size: smaller;
            }

            div.ImmSet ul {

            }

            div.ImmSet li {

            }

            div.ImmSet li a {
                text-decoration: none;
                color: blue;
            }

            div.ImmSet li a:hover {
                text-decoration: none;
                color: red;
            }

            div.ImmSet li a:visited {
                text-decoration: none;
                color: blue;
            }

            /*h3{font-size: 100%;margin:0 0 10px;padding: 2px 0;color: #497B7B;text-align: center}*/
            div.onPrint {
                display: none;
            }

            span.footnote {
                background-color: #ccccee;
                border: 1px solid #000;
                width: 4px;
            }

            .autocomplete .ac-item.active {
                background-color: #e8f0fe;
            }
        </style>

        <script type="text/javascript">

            function display(elements) {

                for (var idx = 0; idx < elements.length; ++idx)
                    elements[idx].style.display = 'block';
            }

            function EnablePrint(button) {
                if (button.value == "Enable Print") {
                    button.value = "Print";
                    var checkboxes = document.getElementsByName("printHP");
                    display(checkboxes);
                    var spaces = document.getElementsByName("printSp");
                    display(spaces);
                    showImmunizationOnlyPrintButton();
                } else {
                    if (onPrint())
                        document.printFrm.submit();
                }
            }

            function printImmOnly() {
                document.printFrm.immunizationOnly.value = "true";
                document.printFrm.submit();
                document.printFrm.immunizationOnly.value = "false";
            }

            function showImmunizationOnlyPrintButton() {
                document.getElementById('print_buttons').insertAdjacentHTML('beforeend',
                    '<input type="button" class="noPrint btn btn-secondary" name="printImmButton" onclick="printImmOnly()" value="Print Immunizations Only">');
            }

            function onPrint() {
                var checked = document.getElementsByName("printHP");
                var thereIsData = false;

                for (var idx = 0; idx < checked.length; ++idx) {
                    if (checked[idx].checked) {
                        thereIsData = true;
                        break;
                    }
                }

                if (!thereIsData) {
                    alert("You should check at least one prevention by selecting a checkbox next to a prevention");
                    return false;
                }

                return true;
            }


            function addByLot() {
                var input = document.getElementById('lotNumberToAdd2');
                var lotNbr = input ? input.value : '';
                popup(600, 900, 'AddPreventionData.jsp?demographic_no=<%= Encode.forJavaScript(demographic_no) %>&lotNumber=' + encodeURIComponent(lotNbr), 'addPreventionData' + <%=new java.util.Random().nextInt(10000) + 1%>);
            }
        </script>


        <script type="text/javascript">
            <!--
            //if (document.all || document.layers)  window.resizeTo(790,580);
            function newWindow(file, window) {
                msgWindow = open(file, window, 'scrollbars=yes,width=760,height=520,screenX=0,screenY=0,top=0,left=10');
                if (msgWindow.opener == null) msgWindow.opener = self;
            }

            //-->
        </script>


        <style type="text/css">
            body {
                font-size: 100%
            }

            div.news {
                width: 100px;
                background: #FFF;
                margin-bottom: 20px;
                margin-left: 20px;
            }

            div.leftBox {
                width: 90%;
                margin-top: 2px;
                margin-left: 3px;
                margin-right: 3px;
                float: left;
            }

            div.leftBox h3 {
                background-color: #ccccff;
                /*font-size: 1.25em;*/
                font-size: 8pt;
                font-variant: small-caps;
                font: bold;
                margin-top: 0px;
                padding-top: 0px;
                margin-bottom: 0px;
                padding-bottom: 0px;
            }

            div.leftBox ul { /*border-top: 1px solid #F11;*/
                /*border-bottom: 1px solid #F11;*/
                font-size: 1.0em;
                list-style: none;
                list-style-type: none;
                list-style-position: outside;
                padding-left: 1px;
                margin-left: 1px;
                margin-top: 0px;
                padding-top: 1px;
                margin-bottom: 0px;
                padding-bottom: 0px;
            }

            div.leftBox li {
                padding-right: 15px;
                white-space: nowrap;
            }

            div.headPrevention {
                position: relative;
                float: left;
                width: 8.4em;
                height: 2.5em;
            }

            div.headPrevention p {
                background: #EEF;
                font-family: verdana, tahoma, sans-serif;
                margin: 0;
                padding: 4px 5px;
                line-height: 1.3;
                text-align: justify
                height: 2em;
                font-family: sans-serif;
                border-left: 0px;
            }

            div.headPrevention a {
                text-decoration: none;
            }

            div.headPrevention a:active {
                color: blue;
            }

            div.headPrevention a:hover {
                color: blue;
            }

            div.headPrevention a:link {
                color: blue;
            }

            div.headPrevention a:visited {
                color: blue;
            }

            div.preventionProcedure {
                width: 10em;
                float: left;
                margin-left: 3px;
                margin-bottom: 3px;
            }

            div.preventionProcedure p {
                font-size: 0.7em;
                font-family: verdana, tahoma, sans-serif;
                background: #F0F0E7;
                margin: 0;
                padding: 1px 2px;
                /*line-height: 1.3;*/ /*text-align: justify*/
            }

            div.preventionSection {
                width: 100%;
                postion: relative;
                margin-top: 5px;
                float: left;
                clear: left;
            }

            div.preventionSet {
                border: thin solid grey;
                clear: left;
            }

            div.recommendations {
                font-family: verdana, tahoma, sans-serif;
                font-size: 1.2em;
            }

            div.recommendations ul {
                padding-left: 15px;
                margin-left: 1px;
                margin-top: 0px;
                padding-top: 1px;
                margin-bottom: 0px;
                padding-bottom: 0px;
            }

            div.recommendations li {

            }

            table.legend {
                border: 0;
                padding-top: 10px;
                width: 420px;
            }

            table.legend td {
                font-size: 8;
                text-align: left;

            }


            table.colour_codes {
                width: 8px;
                height: 10px;

            }

        </style>



        <script>
            function disableSSOWarning() {
                if (confirm("Are you sure you would like to permanently disable this warning?\nYou may re-enable it from your preferences")) {
                    var csrfEl = document.querySelector('input[name="CSRF-TOKEN"]');
                    var csrfToken = csrfEl ? csrfEl.value : '';
                    fetch('<%=request.getContextPath()%>/ws/rs/persona/updatePreference', {
                        method: 'POST',
                        credentials: 'same-origin',
                        headers: {'Content-Type': 'application/json', 'CSRF-TOKEN': csrfToken, 'X-Requested-With': 'XMLHttpRequest'},
                        body: JSON.stringify({key: 'prevention_sso_warning', value: 'true'})
                    }).then(function(response) {
                        if (response.ok) {
                            document.getElementById('ssoWarning').style.display = 'none';
                        }
                    }).catch(function() {
                        console.warn('Could not save SSO warning preference');
                    });
                }
            }

            function disableISPAWarning() {
                if (confirm("Are you sure you would like to permanently disable this warning?\nYou may re-enable it from your preferences")) {
                    var csrfEl = document.querySelector('input[name="CSRF-TOKEN"]');
                    var csrfToken = csrfEl ? csrfEl.value : '';
                    fetch('<%=request.getContextPath()%>/ws/rs/persona/updatePreference', {
                        method: 'POST',
                        credentials: 'same-origin',
                        headers: {'Content-Type': 'application/json', 'CSRF-TOKEN': csrfToken, 'X-Requested-With': 'XMLHttpRequest'},
                        body: JSON.stringify({key: 'prevention_ispa_warning', value: 'true'})
                    }).then(function(response) {
                        if (response.ok) {
                            document.getElementById('ispaWarning').style.display = 'none';
                        }
                    }).catch(function() {
                        console.warn('Could not save ISPA warning preference');
                    });
                }
            }

            function disableNonISPAWarning() {
                if (confirm("Are you sure you would like to permanently disable this warning?\nYou may re-enable it from your preferences")) {
                    var csrfEl = document.querySelector('input[name="CSRF-TOKEN"]');
                    var csrfToken = csrfEl ? csrfEl.value : '';
                    fetch('<%=request.getContextPath()%>/ws/rs/persona/updatePreference', {
                        method: 'POST',
                        credentials: 'same-origin',
                        headers: {'Content-Type': 'application/json', 'CSRF-TOKEN': csrfToken, 'X-Requested-With': 'XMLHttpRequest'},
                        body: JSON.stringify({key: 'prevention_non_ispa_warning', value: 'true'})
                    }).then(function(response) {
                        if (response.ok) {
                            document.getElementById('nonIspaWarning').style.display = 'none';
                        }
                    }).catch(function() {
                        console.warn('Could not save non-ISPA warning preference');
                    });
                }
            }
        /* ---- Vaccine brand catalogue loading ----
         * Loads vaccine-brands.json from the eform images directory.
         * Admins can upload a customised vaccine-brands.json via the eform images
         * upload screen; the file is served via:
         *   eform/displayImage.do?imagefile=vaccine-brands.json
         * Falls back to the bundled oscarPrevention/vaccine-brands.json if the
         * custom file cannot be loaded or is absent.
         */
        var tags = [];
        function _parseVaccineBrands(data) {
            if (!Array.isArray(data) || !data.length) return null;
            function _toStr(v) { return (v !== null && v !== undefined) ? String(v) : ''; }
            var parsed = data
                .map(function(item) {
                    if (!item) return null;
                    var name  = _toStr(item.name).trim();
                    var value = _toStr(item.value).trim();
                    if (!name || !value) return null;
                    return {
                        name:        name,
                        value:       value,
                        manufacture: _toStr(item.manufacture),
                        dose:        _toStr(item.dose),
                        units:       _toStr(item.units),
                        route:       _toStr(item.route),
                        din:         _toStr(item.din)
                    };
                })
                .filter(Boolean);
            return parsed.length ? parsed : null;
        }
        var _vaccineLoadPromise = fetch('<%=request.getContextPath()%>/eform/displayImage.do?imagefile=vaccine-brands.json')
            .then(function(r) { return r.ok ? r.json() : Promise.reject(r.status); })
            .then(function(data) {
                var parsed = _parseVaccineBrands(data);
                if (parsed) {
                    tags = parsed;
                } else {
                    return Promise.reject('empty or invalid');
                }
            })
            .catch(function() {
                console.warn('Could not load custom vaccine-brands.json, falling back to bundled catalogue');
                return fetch('<%=request.getContextPath()%>/oscarPrevention/vaccine-brands.json')
                    .then(function(r) { return r.ok ? r.json() : Promise.reject(r.status); })
                    .then(function(data) {
                        tags = _parseVaccineBrands(data) || [];
                    })
                    .catch(function() {
                        console.error('Could not load bundled vaccine-brands.json');
                        tags = [];
                    });
            });

        </script>
    </head>

    <body class="BodyStyle">
    <!--  -->
    <%=WebUtils.popErrorAndInfoMessagesAsHtml(session)%>
    <%
        List<String> OTHERS = Arrays.asList(new String[]{"DTaP-Hib", "TdP-IPV-Hib", "HBTmf"});
    %>
    <table class="MainTable" id="scrollNumber1">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumnx"><h3><i class="fa-solid fa-syringe"></i><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarprevention.index.oscarpreventiontitre"/></h3></td>
            <td class="MainTableTopRowRightColumnx">
                <table class="TopStatusBarx">
                    <tr>
                        <td><h4><%=Encode.forHtml(nameAge)%></h4>
                        </td>

                        <td></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumnX" style="width: 190px;">

                <div class="leftBox">
                    <h3>&nbsp;Preventions</h3>
                    <div style="background-color: lightgray;">
                        <span>Screenings</span>
                        <ul>
                            <%
                                for (int i = 0; i < prevList.size(); i++) {
                                    HashMap<String, String> h = prevList.get(i);
                                    String prevName = h.get("name");
                                    String displayName = StringUtils.isNotBlank(h.get("displayName")) ? h.get("displayName") : prevName;
                                    String snomedId = h.get("snomedConceptCode") != null ? h.get("snomedConceptCode") : null;
                                    String hcType = h.get("healthCanadaType");
                                    if (hcType == null) {
                                        if (!preventionManager.hideItem(prevName) && !OTHERS.contains(prevName)) {
                                            List<CVCMapping> mappings = cvcMappingDao.findMultipleByOscarName(prevName);
                                            if (mappings != null && mappings.size() > 1) {
                            %>
                            <li class="py-0"><a
                                    href="javascript: function myFunction() {return false; }"
                                    onclick="javascript:popup(600,900,'AddPreventionDataDisambiguate.jsp?<%=snomedId != null ? "snomedId=" + snomedId + "&" : ""%>prevention=<%= java.net.URLEncoder.encode(prevName, StandardCharsets.UTF_8) %>&amp;demographic_no=<%= Encode.forJavaScript(demographic_no) %>&amp;prevResultDesc=<%= java.net.URLEncoder.encode(h.get("resultDesc"), StandardCharsets.UTF_8) %>','addPreventionData<%=Math.abs(prevName.hashCode()) %>')"
                                    title="<%=Encode.forHtmlAttribute(h.get("desc"))%>">
                                <%=Encode.forHtml(displayName)%>
                            </a></li>
                            <% } else {
                            %>
                            <li class="py-0"><a
                                    href="javascript: function myFunction() {return false; }"
                                    onclick="javascript:popup(600,900,'AddPreventionData.jsp?4=4&<%=snomedId != null ? "snomedId=" + snomedId + "&" : ""%>prevention=<%= java.net.URLEncoder.encode(prevName, StandardCharsets.UTF_8) %>&amp;demographic_no=<%= Encode.forJavaScript(demographic_no) %>&amp;prevResultDesc=<%= java.net.URLEncoder.encode(h.get("resultDesc"), StandardCharsets.UTF_8) %>','addPreventionData<%=Math.abs(prevName.hashCode()) %>')"
                                    title="<%=Encode.forHtmlAttribute(h.get("desc"))%>">
                                <%=Encode.forHtml(displayName)%>
                            </a></li>
                            <%
                                            }
                                        }
                                    }
                                }
                            %>

                        </ul>
                        <span>Immunizations</span>
                        <ul>
                            <%
                                for (int i = 0; i < prevList.size(); i++) {
                                    HashMap<String, String> h = prevList.get(i);
                                    String prevName = h.get("name");
                                    String displayName = StringUtils.isNotBlank(h.get("displayName")) ? h.get("displayName") : prevName;
                                    String snomedId = h.get("snomedConceptCode") != null ? h.get("snomedConceptCode") : null;
                                    String hcType = h.get("healthCanadaType");
                                    String ispaStr = h.get("ispa");
                                    boolean ispa = ispaStr != null && "true".equals(ispaStr);
                                    String ispa1 = "";
                                    if (ispa) {
                                        ispa1 = "*";
                                    }

                                    if (hcType != null) {
                                        if (!preventionManager.hideItem(prevName) && !OTHERS.contains(prevName)) {
                                            List<CVCMapping> mappings = cvcMappingDao.findMultipleByOscarName(prevName);
                                            if (mappings != null && mappings.size() > 1) {
                            %>
                            <li class="py-0"><a
                                    href="javascript: function myFunction() {return false; }"
                                    onclick="javascript:popup(600,900,'AddPreventionDataDisambiguate.jsp?<%=snomedId != null ? "snomedId=" + snomedId + "&" : ""%>prevention=<%= java.net.URLEncoder.encode(prevName, StandardCharsets.UTF_8) %>&amp;demographic_no=<%= Encode.forJavaScript(demographic_no) %>&amp;prevResultDesc=<%= java.net.URLEncoder.encode(h.get("resultDesc"), StandardCharsets.UTF_8) %>','addPreventionData<%=Math.abs(prevName.hashCode()) %>')"
                                    title="<%=Encode.forHtmlAttribute(h.get("desc"))%>">
                                <%=Encode.forHtml(displayName)%><%=ispa1 %>
                            </a></li>
                            <% } else {
                            %>
                            <li class="py-0"><a
                                    href="javascript: function myFunction() {return false; }"
                                    onclick="javascript:popup(600,900,'AddPreventionData.jsp?4=4&<%=snomedId != null ? "snomedId=" + snomedId + "&" : ""%>prevention=<%= java.net.URLEncoder.encode(prevName, StandardCharsets.UTF_8) %>&amp;demographic_no=<%= Encode.forJavaScript(demographic_no) %>&amp;prevResultDesc=<%= java.net.URLEncoder.encode(h.get("resultDesc"), StandardCharsets.UTF_8) %>','addPreventionData<%=Math.abs(prevName.hashCode()) %>')"
                                    title="<%=Encode.forHtmlAttribute(h.get("desc"))%>">
                                <%=Encode.forHtml(displayName)%><%=ispa1 %>
                            </a></li>
                            <%
                                            }
                                        }
                                    }
                                }
                            %>
                        </ul>
                        <span>Other</span>
                        <ul>
                            <%
                                for (int i = 0; i < prevList.size(); i++) {
                                    HashMap<String, String> h = prevList.get(i);
                                    String prevName = h.get("name");
                                    String displayName = StringUtils.isNotBlank(h.get("displayName")) ? h.get("displayName") : prevName;
                                    String snomedId = h.get("snomedConceptCode") != null ? h.get("snomedConceptCode") : null;
                                    String hcType = h.get("healthCanadaType");

                                    if (!preventionManager.hideItem(prevName)) {

                                        if (OTHERS.contains(prevName)) {

                                            List<CVCMapping> mappings = cvcMappingDao.findMultipleByOscarName(prevName);
                                            if (mappings != null && mappings.size() > 1) {%>
                            <li class="py-0"><a
                                    href="javascript: function myFunction() {return false; }"
                                    onclick="javascript:popup(600,900,'AddPreventionDataDisambiguate.jsp?<%=snomedId != null ? "snomedId=" + snomedId + "&" : ""%>prevention=<%= java.net.URLEncoder.encode(prevName, StandardCharsets.UTF_8) %>&amp;demographic_no=<%= Encode.forJavaScript(demographic_no) %>&amp;prevResultDesc=<%= java.net.URLEncoder.encode(h.get("resultDesc"), StandardCharsets.UTF_8) %>','addPreventionData<%=Math.abs(prevName.hashCode()) %>')"
                                    title="<%=Encode.forHtmlAttribute(h.get("desc"))%>">
                                <%=Encode.forHtml(displayName)%>
                            </a></li>
                            <% } else {
                            %>
                            <li class="py-0"><a
                                    href="javascript: function myFunction() {return false; }"
                                    onclick="javascript:popup(600,900,'AddPreventionData.jsp?4=4&<%=snomedId != null ? "snomedId=" + snomedId + "&" : ""%>prevention=<%= java.net.URLEncoder.encode(prevName, StandardCharsets.UTF_8) %>&amp;demographic_no=<%= Encode.forJavaScript(demographic_no) %>&amp;prevResultDesc=<%= java.net.URLEncoder.encode(h.get("resultDesc"), StandardCharsets.UTF_8) %>','addPreventionData<%=Math.abs(prevName.hashCode()) %>')"
                                    title="<%=Encode.forHtmlAttribute(h.get("desc"))%>">
                                <%=Encode.forHtml(displayName)%>
                            </a></li>
                            <%
                                            }
                                        }
                                    }
                                }
                            %>
                        </ul>
                    </div>
                </div>
                <oscar:oscarPropertiesCheck property="IMMUNIZATION_IN_PREVENTION"
                                            value="yes">
                    <a href="javascript: function myFunction() {return false; }"
                       onclick="javascript:popup(700,960,'<%=request.getContextPath()%>/encounter/immunization/initSchedule.do?demographic_no=<%= Encode.forJavaScript(demographic_no) %>','oldImms')">Old
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="global.immunizations"/></a>
                    <br>
                </oscar:oscarPropertiesCheck></td>

            <form name="printFrm" method="post" onsubmit="return onPrint();"
                  action="<rewrite:reWrite jspPage="printPrevention.do"/>">
                <input type="hidden" name="immunizationOnly" value="false"/>
                <td valign="top" class="MainTableRightColumn">

                    <%if (dhirEnabled && !isSSOLoggedIn && !hideSSOWarning) {%>
                    <div class="alert alert-warning d-flex align-items-center" id="ssoWarning" role="alert">
                        <span class="me-2">Warning: You are not logged into OneId and will not be able to submit data to DHIR</span>
                        <button type="button" class="btn-close ms-auto" aria-label="Dismiss" onclick="disableSSOWarning()"></button>
                    </div>
                    <% } %>

                    <%if (dhirEnabled && !hasIspaConsent && !hideISPAWarning) {%>
                    <div class="alert alert-warning d-flex align-items-center" id="ispaWarning" role="alert">
                        <span class="me-2">Warning: This patient has not consented to send ISPA vaccines to DHIR</span>
                        <button type="button" class="btn-close ms-auto" aria-label="Dismiss" onclick="disableISPAWarning()"></button>
                    </div>
                    <% } %>

                    <%if (dhirEnabled && !hasNonIspaConsent && !hideNonISPAWarning) {%>
                    <div class="alert alert-warning d-flex align-items-center" id="nonIspaWarning" role="alert">
                        <span class="me-2">Warning: This patient has not consented to send non-ISPA vaccines to DHIR</span>
                        <button type="button" class="btn-close ms-auto" aria-label="Dismiss" onclick="disableNonISPAWarning()"></button>
                    </div>
                    <% } %>


                    <a href="#" onclick="popup(600,800,'http://www.phac-aspc.gc.ca/im/is-cv/index-eng.php')">Immunization
                        Schedules - Public Health Agency of Canada</a>


                    <%
                        if (warnings.size() > 0 || recomendations.size() > 0 || dsProblems) { %>
                    <div class="recommendations">
                        <%
                            if (printError) {
                        %>
                        <div class="alert alert-danger" role="alert">An error occurred while trying to print</div>
                        <%
                            }
                        %>
                        <span style="font-size: larger;">Prevention Recommendations</span>
                        <div class="mt-1">
                            <%
                            /* NOTE: warn/reminder strings are generated by the Drools prevention decision
                             * support engine (DSPreventionDrools) from rule file messages — they are not
                             * derived from raw user/patient input and are therefore output unencoded here.
                             * If rule messages are ever allowed to incorporate patient-supplied text,
                             * this output must be wrapped with Encode.forHtml().
                             */
                            if (warnings.size() > 0 ) {
                            %><div class="alert alert-danger py-1 mb-1" role="alert">
                                <%for (int i = 0; i < warnings.size(); i++) {
                                    String warn = (String) warnings.get(i);%>
                                <%=warn%><br>
                                <%}%>
                            </div>
                            <%}%>
                            <% if (recomendations.size() > 0 ) {
                            %><div class="alert alert-info py-1 mb-1" role="alert">
                            <% for (int i = 0; i < recomendations.size(); i++) {
                                String warn = (String) recomendations.get(i);%>
                            <%=warn%><br>
                            <%}%>
                            </div>
                            <%}%>
                            <% if (dsProblems) { %>
                            <div class="alert alert-danger py-1 mb-1" role="alert">Decision Support Had Errors Running.</div>
                            <% } %>
                        </div>
                    </div>
                    <% } %>

                    <%if (!StringUtils.isEmpty(CarlosProperties.getInstance().getProperty("cvc.url"))) { %>
                                <input type="text" id="lotNumberToAdd2" name="lotNumberToAdd2" class="form-control form-control-sm"
                                       style="width: 300px;" placeholder="Add by Brand/Generic/Lot#" autocomplete="off">
                                <div id="lotNumberToAdd2_choices" class="autocomplete"></div>
                    <% } else {%>
                                <input type="text" id="immunization" class="form-control form-control-sm"
                                       style="width: 300px;" placeholder="Pick vaccine brand/generic" autocomplete="off">
                                <div id="immunization_choices" class="autocomplete"></div>
                    <% } %>
                    <%
                        String[] ColourCodesArray = new String[7];
                        ColourCodesArray[1] = "#F0F0E7"; //very light grey - completed
                        ColourCodesArray[2] = "#FFDDDD"; //light pink - Refused
                        ColourCodesArray[3] = "#FFCC24"; //orange - Ineligible
                        ColourCodesArray[4] = "#FF00FF"; //dark pink - pending
                        ColourCodesArray[5] = "#ee5f5b"; //dark salmon to match part of bootstraps danger - abnormal
                        ColourCodesArray[6] = "#BDFCC9"; //green - other

                        //labels for colour codes
                        String[] lblCodesArray = new String[7];
                        lblCodesArray[1] = "Completed";
                        lblCodesArray[2] = "Refused";
                        lblCodesArray[3] = "Ineligible";
                        lblCodesArray[4] = "Pending";
                        lblCodesArray[5] = "Abnormal";
                        lblCodesArray[6] = "Other";

                        //Title ie: Legend or Profile Legend
                        String legend_title = "Legend: ";

                        //creat empty builder string
                        String legend_builder = " ";


                        for (int iLegend = 1; iLegend < 7; iLegend++) {

                            legend_builder += "<td> <table class='colour_codes' style=\"white-space:nowrap;\" bgcolor='" + ColourCodesArray[iLegend] + "'><tr><td> </td></tr></table> </td> <td align='center' style=\"white-space:nowrap;\">" + lblCodesArray[iLegend] + "</td>";

                        }

                        legend_builder += "<td> <table class='colour_codes' style=\"white-space:nowrap;border:none\" bgcolor='white'><tr><td>*</td></tr></table> </td> <td align='center' style=\"white-space:nowrap;\">ISPA</td>";


                        String legend = "<table class='legend' cellspacing='0'><tr><td><b>" + legend_title + "</b></td>" + legend_builder + "</tr></table>";

                        out.print(legend);
                    %>


                    <div>
                        <input type="hidden" name="demographic_no" value="<%= Encode.forHtmlAttribute(demographic_no) %>"/>
                        <input type="hidden" name="hin" value="<%=hin%>"/>
                        <input type="hidden" name="mrp" value="<%=mrp%>"/>
                        <input type="hidden" name="module" value="prevention">
                                <%
                 if (!io.github.carlos_emr.CarlosProperties.getInstance().getBooleanProperty("PREVENTION_CLASSIC_VIEW","yes")){
                   ArrayList<Map<String,Object>> hiddenlist = new ArrayList<Map<String,Object>>();
                  for (int i = 0 ; i < prevList.size(); i++){
                  		HashMap<String,String> h = prevList.get(i);
                        String prevName = h.get("name");
                        ArrayList<Map<String,Object>> alist = PreventionData.getPreventionData(loggedInInfo, prevName, Integer.valueOf(demographic_no));

                        boolean show = pdc.display(loggedInInfo, h, demographic_no,alist.size());
                        if(!show){
                            Map<String,Object> h2 = new HashMap<String,Object>();
                            h2.put("prev",h);
                            h2.put("list",alist);
                            hiddenlist.add(h2);
                        }else{
               %>

                        <div class="preventionSection">
                            <%
                                String snomedId = h.get("snomedConceptCode") != null ? h.get("snomedConceptCode") : null;
                                boolean ispa = h.get("ispa") != null ? Boolean.valueOf(h.get("ispa")) : false;
                                String ispa1 = "";
                                if (ispa) {
                                    ispa1 = "*";
                                }
                                if (alist.size() > 0) {

                            %>
                            <div style="position: relative; float: left; padding-right: 10px;">
                                <input style="display: none;" type="checkbox" name="printHP"
                                       value="<%=i%>" checked/> <%
                            } else {

                            %>
                                <div style="position: relative; float: left; padding-right: 25px;">
                                    <span style="display: none;" name="printSp">&nbsp;</span> <%}%>
                                </div>
                                <div class="headPrevention">
                                    <p>
                                        <%
                                            List<CVCMapping> mappings = cvcMappingDao.findMultipleByOscarName(prevName);
                                            if (mappings != null && mappings.size() > 1) {%>
                                        <a href="javascript: function myFunction() {return false; }"
                                           onclick="javascript:popup(600,900,'AddPreventionDataDisambiguate.jsp?1=1&<%=snomedId != null ? "snomedId=" + snomedId + "&" : ""%>prevention=<%= java.net.URLEncoder.encode(h.get("name"), StandardCharsets.UTF_8) %>&amp;demographic_no=<%= Encode.forJavaScript(demographic_no) %>&amp;prevResultDesc=<%= java.net.URLEncoder.encode(h.get("resultDesc"), StandardCharsets.UTF_8) %>','addPreventionData<%=Math.abs( ( h.get("name")).hashCode() ) %>')">
                                            <span title="<%=Encode.forHtmlAttribute(h.get("desc"))%>"
                                                  style="font-weight: bold;"><%=h.get("name")%><%=ispa1%></span>
                                        </a>
                                        <% } else { %>
                                        <a href="javascript: function myFunction() {return false; }"
                                           onclick="javascript:popup(600,900,'AddPreventionData.jsp?1=1&<%=snomedId != null ? "snomedId=" + snomedId + "&" : ""%>prevention=<%= java.net.URLEncoder.encode(h.get("name"), StandardCharsets.UTF_8) %>&amp;demographic_no=<%= Encode.forJavaScript(demographic_no) %>&amp;prevResultDesc=<%= java.net.URLEncoder.encode(h.get("resultDesc"), StandardCharsets.UTF_8) %>','addPreventionData<%=Math.abs( ( h.get("name")).hashCode() ) %>')">
                                            <span title="<%=Encode.forHtmlAttribute(h.get("desc"))%>"
                                                  style="font-weight: bold;"><%=h.get("name")%><%=ispa1 %></span>
                                        </a>
                                        <% } %>
                                        <br/>
                                    </p>
                                </div>
                                <% String result;
                                    for (int k = 0; k < alist.size(); k++) {
                                        Map<String, Object> hdata = alist.get(k);
                                        Map<String, String> hExt = PreventionData.getPreventionKeyValues((String) hdata.get("id"));
                                        result = hExt.get("result");

                                        String onClickCode = "javascript:popup(600,900,'AddPreventionData.jsp?id=" + hdata.get("id") + "&amp;demographic_no=" + demographic_no + "','addPreventionData')";
                                %>

                                <div class="preventionProcedure" onclick="<%=onClickCode%>"
                                     title="fade=[on] header=[<%=StringEscapeUtils.escapeHtml4((String)hdata.get("age"))%> -- Date:<%=StringEscapeUtils.escapeHtml4((String)hdata.get("prevention_date_no_time"))%>] body=[<%=StringEscapeUtils.escapeHtml4((String)hExt.get("comments"))%>&lt;br/&gt;Administered By: <%=StringEscapeUtils.escapeHtml4((String)hdata.get("provider_name"))%>]">


                                    <p <%=r(hdata.get("refused"),result)%> >
                                        Age: <%=StringEscapeUtils.escapeHtml4((String)hdata.get("age"))%> <%if(result!=null && result.equals("abnormal")){out.print("result:"+StringEscapeUtils.escapeHtml4(result));}%>
                                        <br/>
                                        <!--<%=refused(hdata.get("refused"))%>-->
                                        Date: <%=StringEscapeUtils.escapeHtml4((String)hdata.get("prevention_date_no_time"))%>
                                                <%if (hExt.get("comments") != null && (hExt.get("comments")).length()>0) {
                    if (io.github.carlos_emr.CarlosProperties.getInstance().getBooleanProperty("prevention_show_comments","yes")){%>
                                    <div class="comments">
                                        <span><%=StringEscapeUtils.escapeHtml4((String) hExt.get("comments"))%></span>
                                    </div>
                                    <% } else { %>
                                    <span class="footnote">1</span>
                                    <% }
                                    }%>

                                    <%
                                        /* Some results may not have a local database ID */
                                        if (hdata.containsKey("id")) {
                                            List<DHIRSubmissionLog> dhirLogs = submissionManager.findByPreventionId(Integer.parseInt((String) hdata.get("id")));

                                            if (!dhirLogs.isEmpty()) {
                                    %> <span class="footnote"
                                             style="background-color:black;color:white"><%=dhirLogs.get(0).getStatus()%></span> <%
                                } else {
                                    if (dhirEnabled && !StringUtils.isEmpty(snomedId)) {
                                        if ((ispa && hasIspaConsent) || (!ispa && hasNonIspaConsent)) {
                                %><span class="footnote" style="background-color:orange;color:black;white-space:nowrap">Not Submitted</span> <%
                                                }
                                            }
                                        }
                                    }
                                %>

                                            </p>
                                </div>
                                <%}%>
                            </div>
                            <%
                                    }
                                } %> <a href="#"
                                        onclick="var el=document.getElementById('otherElements'); el.style.display=(el.style.display==='none'?'':'none'); return false;"
                                        style="font-size: xx-small;">show/hide all other Preventions</a>
                            <div style="display: none;" id="otherElements">
                                <%
                                    for (int i = 0; i < hiddenlist.size(); i++) {
                                        Map<String, Object> h2 = hiddenlist.get(i);
                                        HashMap<String, String> h = (HashMap<String, String>) h2.get("prev");
                                        String prevName = h.get("name");
                                        ArrayList<HashMap<String, String>> alist = (ArrayList<HashMap<String, String>>) h2.get("list");
                                %>
                                <div class="preventionSection">
                                    <%
                                        if (alist.size() > 0) {

                                    %>
                                    <div style="position: relative; float: left; padding-right: 10px;">
                                        <input style="display: none;" type="checkbox" name="printHP"
                                               value="<%=i%>" checked/> <%} else {%>
                                        <div style="position: relative; float: left; padding-right: 25px;">
                                            <span style="display: none;" name="printSp">&nbsp;</span> <%
                                            }
                                            String snomedId = h.get("snomedConceptCode") != null ? h.get("snomedConceptCode") : null;
                                            boolean ispa = h.get("ispa") != null ? Boolean.valueOf(h.get("ispa")) : false;
                                            String ispa1 = "";
                                            if (ispa) {
                                                ispa1 = "*";
                                            }
                                        %>
                                        </div>
                                        <div class="headPrevention">
                                            <p><a href="javascript: function myFunction() {return false; }"
                                                  onclick="javascript:popup(600,900,'AddPreventionData.jsp?2=2&<%=snomedId != null ? "snomedId=" + snomedId + "&" : ""%>prevention=<%= java.net.URLEncoder.encode(h.get("name"), StandardCharsets.UTF_8) %>&amp;demographic_no=<%= Encode.forJavaScript(demographic_no) %>&amp;prevResultDesc=<%= java.net.URLEncoder.encode(h.get("resultDesc"), StandardCharsets.UTF_8) %>','addPreventionData<%=Math.abs( ( h.get("name")).hashCode() ) %>')">
                                                <span title="<%=Encode.forHtmlAttribute(h.get("desc"))%>"
                                                      style="font-weight: bold;"><%=h.get("name")%><%=ispa1 %></span>
                                            </a>

                                                <br/>
                                            </p>
                                        </div>
                                        <%
                                            String result;
                                            for (int k = 0; k < alist.size(); k++) {
                                                Map<String, String> hdata = alist.get(k);
                                                Map<String, String> hExt = PreventionData.getPreventionKeyValues(hdata.get("id"));
                                                result = hExt.get("result");
                                        %>
                                        <div class="preventionProcedure"
                                             onclick="javascript:popup(600,900,'AddPreventionData.jsp?id=<%=hdata.get("id")%>&amp;demographic_no=<%= Encode.forJavaScript(demographic_no) %>','addPreventionData')"
                                             title="fade=[on] header=[<%=StringEscapeUtils.escapeHtml4((String)hdata.get("age"))%> -- Date:<%=StringEscapeUtils.escapeHtml4((String)hdata.get("prevention_date_no_time"))%>] body=[<%=StringEscapeUtils.escapeHtml4((String)hExt.get("comments"))%>&lt;br/&gt;Administered By: <%=StringEscapeUtils.escapeHtml4((String)hdata.get("provider_name"))%>]">
                                            <p <%=r(hdata.get("refused"), result)%>>Age: <%=hdata.get("age")%> <br/>
                                                <!--<%=refused(hdata.get("refused"))%>-->
                                                Date: <%=StringEscapeUtils.escapeHtml4((String)hdata.get("prevention_date_no_time"))%>
                                                        <%if (hExt.get("comments") != null && (hExt.get("comments")).length()>0) {
                     if (io.github.carlos_emr.CarlosProperties.getInstance().getBooleanProperty("prevention_show_comments","yes")){ %>
                                            <div class="comments">
                                                <span><%=StringEscapeUtils.escapeHtml4((String) hExt.get("comments"))%></span>
                                            </div>
                                            <% } else { %>
                                            <span class="footnote">1</span>
                                            <% }
                                            }%>
                                            <%
                                                /* Integrated results dont have an "ID" key */
                                                if (hdata.containsKey("id")) {
                                                    List<DHIRSubmissionLog> dhirLogs = submissionManager.findByPreventionId(Integer.parseInt((String) hdata.get("id")));

                                                    if (!dhirLogs.isEmpty()) {
                                            %> <span class="footnote"
                                                     style="background-color:black;color:white"><%=dhirLogs.get(0).getStatus()%></span> <%
                                        } else {
                                            if (dhirEnabled && !StringUtils.isEmpty(snomedId)) {
                                                if ((ispa && hasIspaConsent) || (!ispa && hasNonIspaConsent)) {
                                        %><span class="footnote" style="background-color:orange;color:black">Not Submitted</span> <%
                                                        }
                                                    }
                                                }
                                            }
                                        %>
                                            </p>
                                        </div>
                                        <%}%>
                                    </div>

                                    <%}%>
                                </div>
                                <%
                                } else {  //OLD
                                    if (configSets == null) {
                                        configSets = new ArrayList<Map<String, Object>>();
                                    }

                                    for (int setNum = 0; setNum < configSets.size(); setNum++) {
                                        Map<String, Object> setHash = configSets.get(setNum);
                                        String[] prevs = (String[]) setHash.get("prevList");
                                %>
                                <div class="immSet">
                                    <h2 style="display: block;"><%=setHash.get("title")%>
                                        <span><%=setHash.get("effective")%></span></h2>
                                    <!--a style="font-size:xx-small;" onclick="javascript:showHideItem('<%="prev"+setNum%>')" href="javascript: function myFunction() {return false; }" >show/hide</a-->
                                    <a href="#"
                                       onclick="var el=document.getElementById('<%="prev"+setNum%>'); el.style.display=(el.style.display==='none'?'':'none'); return false;"
                                       style="font-size: xx-small;">show/hide</a>
                                    <div class="preventionSet"
                                         <%=pdc.getDisplay(loggedInInfo, setHash,demographic_no)%>;
                                    " id="<%="prev" + setNum%>">
                                    <%
                                        for (int i = 0; i < prevs.length; i++) {
                                            HashMap<String, String> h = pdc.getPrevention(prevs[i]);
                                    %>
                                    if(h == null) { //this happens with private entries
                                    continue;
                                    }
                                    %>
                                    <div class="preventionSection">
                                        <div class="headPrevention">
                                            <p><a href="javascript: function myFunction() {return false; }"
                                                  onclick="javascript:popup(600,900,'AddPreventionData.jsp?3=3&prevention=<%= java.net.URLEncoder.encode(h.get("name"), StandardCharsets.UTF_8) %>&amp;demographic_no=<%= Encode.forJavaScript(demographic_no) %>&amp;prevResultDesc=<%= java.net.URLEncoder.encode(h.get("resultDesc"), StandardCharsets.UTF_8) %>','addPreventionData<%=Math.abs(h.get("name").hashCode())%>')">
                                                <span title="<%=Encode.forHtmlAttribute(h.get("desc"))%>"
                                                      style="font-weight: bold;"><%=h.get("name")%></span>
                                            </a> <br/>
                                            </p>
                                        </div>
                                        <%
                                            String prevType = h.get("name");
                                            ArrayList<Map<String, Object>> alist = PreventionData.getPreventionData(loggedInInfo, prevType, Integer.valueOf(demographic_no));

                                            String result;
                                            for (int k = 0; k < alist.size(); k++) {
                                                Map<String, Object> hdata = alist.get(k);
                                                Map<String, String> hExt = PreventionData.getPreventionKeyValues((String) hdata.get("id"));
                                                result = hExt.get("result");

                                                String onClickCode = "javascript:popup(600,900,'AddPreventionData.jsp?id=" + hdata.get("id") + "&amp;demographic_no=" + demographic_no + "','addPreventionData')";
                                        %>
                                        <div class="preventionProcedure" onclick="<%=onClickCode%>">
                                            <p <%=r(hdata.get("refused"), result)%>>Age: <%=hdata.get("age")%> <br/>
                                                <!--<%=refused(hdata.get("refused"))%>-->
                                                Date: <%=StringEscapeUtils.escapeHtml4((String) hdata.get("prevention_date_no_time"))%>
                                                                                            </p>
                                        </div>
                                        <%}%>
                                    </div>
                                    <%}%>
                                </div>
                            </div>
                            <!--immSet--> <%
                                }
                            }
                        %>
                        </div>
                                <%=legend %>
                </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumnX">
			<span id="print_buttons">
				<input type="button" class="noPrint btn btn-secondary" name="printButton" onclick="EnablePrint(this)"
                       value="Enable Print">
			</input>
            </td>

            <input type="hidden" id="demographicNo" name="demographicNo" value="<%= Encode.forHtmlAttribute(demographic_no) %>"/>

            <%
                for (int i = 0; i < prevList.size(); i++) {
                    HashMap<String, String> h = prevList.get(i);
                    String prevName = h.get("name");
                    ArrayList<Map<String, Object>> alist = PreventionData.getPreventionData(loggedInInfo, prevName, Integer.valueOf(demographic_no));

                    if (alist.size() > 0) { %>
            <input type="hidden" id="preventionHeader<%=i%>"
                   name="preventionHeader<%=i%>" value="<%=h.get("name")%>">

            <%
                for (int k = 0; k < alist.size(); k++) {
                    Map<String, Object> hdata = alist.get(k);
                    Map<String, String> hExt = PreventionData.getPreventionKeyValues((String) hdata.get("id"));
            %>

            <input type="hidden" id="preventProcedureProvider<%=i%>-<%=k%>"
                   name="preventProcedureProvider<%=i%>-<%=k%>" value="<%=hdata.get("provider_name")%>"/>

            <input type="hidden" id="preventProcedureStatus<%=i%>-<%=k%>"
                   name="preventProcedureStatus<%=i%>-<%=k%>"
                   value="<%=hdata.get("refused")%>">
            <input type="hidden" id="preventProcedureAge<%=i%>-<%=k%>"
                   name="preventProcedureAge<%=i%>-<%=k%>"
                   value="<%=hdata.get("age")%>">
            <input type="hidden" id="preventProcedureDate<%=i%>-<%=k%>"
                   name="preventProcedureDate<%=i%>-<%=k%>"
                   value="<%=StringEscapeUtils.escapeHtml4((String)hdata.get("prevention_date_no_time"))%>">
            <% String comments = hExt.get("comments");
                if (comments != null && !comments.isEmpty()) {%>
            <input type="hidden" id="preventProcedureComments<%=i%>-<%=k%>"
                   name="preventProcedureComments<%=i%>-<%=k%>"
                   value="<%=StringEscapeUtils.escapeHtml4(comments)%>">
            <% } %>

            <% String result = hExt.get("result");
                if (result != null && !result.isEmpty()) {%>
            <input type="hidden" id="preventProcedureResult<%=i%>-<%=k%>"
                   name="preventProcedureResult<%=i%>-<%=k%>"
                   value="<%=StringEscapeUtils.escapeHtml4(result)%>">
            <% } %>

            <% String reason = hExt.get("reason");
                if (reason != null && !reason.isEmpty()) {%>
            <input type="hidden" id="preventProcedureReason<%=i%>-<%=k%>"
                   name="preventProcedureReason<%=i%>-<%=k%>"
                   value="<%=StringEscapeUtils.escapeHtml4(reason)%>">
            <% } %>

            <% String nameOfVaccine = hExt.get("name");
                if (nameOfVaccine != null && !nameOfVaccine.isEmpty()) {%>
            <input type="hidden" id="preventProcedureNameOfVaccine<%=i%>-<%=k%>"
                   name="preventProcedureNameOfVaccine<%=i%>-<%=k%>"
                   value="<%=StringEscapeUtils.escapeHtml4(nameOfVaccine)%>">
            <% } %>

            <% String manufacture = hExt.get("manufacture");
                if (manufacture != null && !manufacture.isEmpty()) {%>
            <input type="hidden" id="preventProcedureManufacture<%=i%>-<%=k%>"
                   name="preventProcedureManufacture<%=i%>-<%=k%>"
                   value="<%=StringEscapeUtils.escapeHtml4(manufacture)%>">
            <% } %>

            <% String lotID = hExt.get("lot");
                if (lotID != null && !lotID.isEmpty()) {%>
            <input type="hidden" id="preventProcedureLotID<%=i%>-<%=k%>"
                   name="preventProcedureLotID<%=i%>-<%=k%>"
                   value="<%=StringEscapeUtils.escapeHtml4(lotID)%>">
            <% } %>

            <% String doseAdministered = hExt.get("dose");
                if (doseAdministered != null && !doseAdministered.isEmpty()) {%>
            <input type="hidden" id="preventProcedureDoseAdministered<%=i%>-<%=k%>"
                   name="preventProcedureDoseAdministered<%=i%>-<%=k%>"
                   value="<%=StringEscapeUtils.escapeHtml4(doseAdministered)%>">
            <% } %>

            <% String locationOfShot = hExt.get("location");
                if (locationOfShot != null && !locationOfShot.isEmpty()) {%>
            <input type="hidden" id="preventProcedureLocationOfShot<%=i%>-<%=k%>"
                   name="preventProcedureLocationOfShot<%=i%>-<%=k%>"
                   value="<%=StringEscapeUtils.escapeHtml4(locationOfShot)%>">
            <% }
            }
            }
            } //for there are preventions
            %>
            </form>
        </tr>
    </table>

    <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/boxover.js"></script>

    <script type="text/javascript">

        /* ---- Client-side HTML escaping ----
         * Sanitises untrusted strings before inserting via innerHTML.
         * Used to encode data loaded from the admin-supplied vaccine-brands.json
         * catalogue so that a malicious JSON file cannot inject HTML/JS.
         */

        /* ---- Client-side HTML escaping ----
         * Sanitises untrusted strings before inserting via innerHTML.
         * Used to encode data loaded from the admin-supplied vaccine-brands.json
         * catalogue so that a malicious JSON file cannot inject HTML/JS.
         */
        function escHtml(str) {
            var d = document.createElement('div');
            d.textContent = typeof str === 'string' ? str : '';
            return d.innerHTML;
        }

        /* ---- Plain-JS autocomplete helper ----
         * Creates an autocomplete dropdown on an input element using the
         * .autocomplete / .ac-item classes from css/autocomplete.css.
         *
         * @param input      - the <input> element
         * @param dropdown   - the <div class="autocomplete"> element placed after the input
         * @param getResults - function(query) returning an array of result objects
         * @param renderItem - function(item) returning the innerHTML for one suggestion row
         * @param onSelect   - function(item) called when a suggestion is chosen
         * @param minLen     - minimum query length before suggestions appear (default 2)
         */
        function initAutocomplete(input, dropdown, getResults, renderItem, onSelect, minLen) {
            minLen = minLen || 2;
            var dropdownId = dropdown.id || ('ac-dropdown-' + Math.random().toString(36).slice(2));
            dropdown.id = dropdownId;
            input.setAttribute('role', 'combobox');
            input.setAttribute('aria-expanded', 'false');
            input.setAttribute('aria-controls', dropdownId);
            input.setAttribute('aria-autocomplete', 'list');
            dropdown.setAttribute('role', 'listbox');
            dropdown.setAttribute('aria-hidden', 'true');
            var activeIdx = -1;

            function clearActive(items) {
                items.forEach(function(el) { el.classList.remove('active'); el.setAttribute('aria-selected', 'false'); });
            }

            function renderDropdown(results) {
                dropdown.innerHTML = '';
                activeIdx = -1;
                if (!results || results.length === 0) {
                    dropdown.style.display = 'none';
                    input.setAttribute('aria-expanded', 'false');
                    dropdown.setAttribute('aria-hidden', 'true');
                    input.removeAttribute('aria-activedescendant');
                    return;
                }
                results.forEach(function(item, idx) {
                    var div = document.createElement('div');
                    div.className = 'ac-item';
                    div.id = dropdownId + '-opt-' + idx;
                    div.setAttribute('role', 'option');
                    div.setAttribute('aria-selected', 'false');
                    div.innerHTML = renderItem(item);
                    div.addEventListener('mousedown', function(e) {
                        e.preventDefault();
                        onSelect(item);
                        dropdown.style.display = 'none';
                        input.setAttribute('aria-expanded', 'false');
                        dropdown.setAttribute('aria-hidden', 'true');
                        input.removeAttribute('aria-activedescendant');
                    });
                    dropdown.appendChild(div);
                });
                input.setAttribute('aria-expanded', 'true');
                dropdown.setAttribute('aria-hidden', 'false');
                dropdown.style.display = 'block';
            }

            input.addEventListener('input', function() {
                var q = this.value.trim();
                if (q.length < minLen) {
                    dropdown.style.display = 'none';
                    input.setAttribute('aria-expanded', 'false');
                    dropdown.setAttribute('aria-hidden', 'true');
                    input.removeAttribute('aria-activedescendant');
                    return;
                }
                var results = getResults(q);
                renderDropdown(results);
            });

            input.addEventListener('keydown', function(e) {
                var items = dropdown.querySelectorAll('.ac-item');
                if (!items.length) return;
                if (e.key === 'ArrowDown') {
                    e.preventDefault();
                    activeIdx = Math.min(activeIdx + 1, items.length - 1);
                    clearActive(items);
                    items[activeIdx].classList.add('active');
                    items.forEach(function(el) { el.setAttribute('aria-selected', 'false'); });
                    items[activeIdx].setAttribute('aria-selected', 'true');
                    input.setAttribute('aria-activedescendant', items[activeIdx].id);
                } else if (e.key === 'ArrowUp') {
                    e.preventDefault();
                    activeIdx = Math.max(activeIdx - 1, 0);
                    clearActive(items);
                    items[activeIdx].classList.add('active');
                    items.forEach(function(el) { el.setAttribute('aria-selected', 'false'); });
                    items[activeIdx].setAttribute('aria-selected', 'true');
                    input.setAttribute('aria-activedescendant', items[activeIdx].id);
                } else if (e.key === 'Enter' && activeIdx >= 0) {
                    e.preventDefault();
                    items[activeIdx].dispatchEvent(new MouseEvent('mousedown'));
                } else if (e.key === 'Escape') {
                    dropdown.style.display = 'none';
                    input.setAttribute('aria-expanded', 'false');
                    dropdown.setAttribute('aria-hidden', 'true');
                    input.removeAttribute('aria-activedescendant');
                }
            });

            document.addEventListener('click', function(e) {
                if (!input.contains(e.target) && !dropdown.contains(e.target)) {
                    dropdown.style.display = 'none';
                    input.setAttribute('aria-expanded', 'false');
                    dropdown.setAttribute('aria-hidden', 'true');
                    input.removeAttribute('aria-activedescendant');
                }
            });
        }

        /* ---- Vaccine brand autocomplete on #immunization ----
         * Filters the `tags` array (loaded from vaccine-brands.json or fallback defaults).
         * Called by _vaccineLoadPromise.finally() after data is ready.
         */
        function initVaccineAutocomplete() {
            var input = document.getElementById('immunization');
            var dropdown = document.getElementById('immunization_choices');
            if (!input || !dropdown) return;

            function filterTags(q) {
                var lower = q.toLowerCase();
                return tags.filter(function(t) {
                    return t.value.toLowerCase().indexOf(lower) !== -1 ||
                           t.name.toLowerCase().indexOf(lower) !== -1;
                }).slice(0, 25);
            }

            initAutocomplete(
                input,
                dropdown,
                filterTags,
                function(item) {
                    /* escHtml() sanitises admin-loaded JSON data before innerHTML insertion */
                    return '<strong>' + escHtml(item.name) + '</strong> &ndash; ' + escHtml(item.value);
                },
                function(item) {
                    input.value = '';
                    var vPath = '<%=request.getContextPath()%>';
                    var demographicNo = '<%= Encode.forJavaScript(demographic_no) %>';
                    var url = vPath + '/oscarPrevention/AddPreventionData.jsp?1=1'
                        + '&prevention=' + encodeURIComponent(item.name)
                        + '&demographic_no=' + demographicNo
                        + '&brandName=' + encodeURIComponent(item.value || '')
                        + '&din=' + encodeURIComponent(item.din || '')
                        + '&dose=' + encodeURIComponent(item.dose || '')
                        + '&route=' + encodeURIComponent(item.route || '')
                        + '&doseUnit=' + encodeURIComponent(item.units || '')
                        + '&manufacture=' + encodeURIComponent(item.manufacture || '');
                    popup(600, 900, url, 'AddPreventionWindow');
                },
                2
            );
        }
        /* Initialize once vaccine data is loaded (or fallback is set) */
        _vaccineLoadPromise.finally(initVaccineAutocomplete);

        /* ---- CVC lot-number autocomplete on #lotNumberToAdd2 ----
         * Fetches suggestions from the server (cvc.do?method=query).
         * Mirrors ARIA combobox/listbox pattern from initAutocomplete().
         */
        (function() {
            var input = document.getElementById('lotNumberToAdd2');
            var dropdown = document.getElementById('lotNumberToAdd2_choices');
            if (!input || !dropdown) return;

            /* ARIA: wire up combobox/listbox roles */
            var dropdownId = dropdown.id || 'lotNumberToAdd2_choices';
            dropdown.id = dropdownId;
            input.setAttribute('role', 'combobox');
            input.setAttribute('aria-expanded', 'false');
            input.setAttribute('aria-controls', dropdownId);
            input.setAttribute('aria-autocomplete', 'list');
            dropdown.setAttribute('role', 'listbox');
            dropdown.setAttribute('aria-hidden', 'true');

            var activeIdx = -1;
            var debounceTimer;
            var requestSeq = 0;
            var cachedResults = [];

            function clearActive(items) {
                items.forEach(function(el) { el.classList.remove('active'); el.setAttribute('aria-selected', 'false'); });
            }

            function fetchResults(q, callback) {
                clearTimeout(debounceTimer);
                var seq = ++requestSeq;
                debounceTimer = setTimeout(function() {
                    var csrfEl = document.querySelector('input[name="CSRF-TOKEN"]');
                    var csrfToken = csrfEl ? csrfEl.value : '';
                    fetch('<%=request.getContextPath()%>/cvc.do?method=query', {
                        method: 'POST',
                        credentials: 'same-origin',
                        headers: {'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': csrfToken, 'X-Requested-With': 'XMLHttpRequest'},
                        body: encodeURIComponent(q)
                    })
                    .then(function(r) {
                        if (!r.ok) return Promise.reject('CVC query returned HTTP ' + r.status);
                        return r.json();
                    })
                    .then(function(data) {
                        if (seq !== requestSeq) return;
                        cachedResults = data.results || [];
                        callback(cachedResults);
                    })
                    .catch(function(err) {
                        console.error('CVC lot number lookup failed:', err);
                        if (seq !== requestSeq) return;
                        callback([]);
                    });
                }, 250);
            }

            /* Override input listener to use async fetch instead of sync filter */
            input.addEventListener('input', function() {
                var q = this.value.trim();
                if (q.length < 3) {
                    dropdown.style.display = 'none';
                    input.setAttribute('aria-expanded', 'false');
                    dropdown.setAttribute('aria-hidden', 'true');
                    input.removeAttribute('aria-activedescendant');
                    activeIdx = -1;
                    return;
                }
                fetchResults(q, function(results) {
                    dropdown.innerHTML = '';
                    activeIdx = -1;
                    if (!results.length) {
                        dropdown.style.display = 'none';
                        input.setAttribute('aria-expanded', 'false');
                        dropdown.setAttribute('aria-hidden', 'true');
                        input.removeAttribute('aria-activedescendant');
                        return;
                    }
                    results.slice(0, 25).forEach(function(item, idx) {
                        var div = document.createElement('div');
                        div.className = 'ac-item';
                        div.id = dropdownId + '-opt-' + idx;
                        div.setAttribute('role', 'option');
                        div.setAttribute('aria-selected', 'false');
                        div.innerHTML = item.generic ? escHtml(item.name) : '<strong>' + escHtml(item.name) + '</strong>';
                        div.addEventListener('mousedown', function(e) {
                            e.preventDefault();
                            input.value = '';
                            dropdown.style.display = 'none';
                            input.setAttribute('aria-expanded', 'false');
                            dropdown.setAttribute('aria-hidden', 'true');
                            input.removeAttribute('aria-activedescendant');
                            activeIdx = -1;
                            var lotNum = item.lotNumber || '';
                            if (lotNum.length > 0) {
                                popup(465, 635, 'AddPreventionData.jsp?demographic_no=<%= Encode.forJavaScript(demographic_no) %>&lotNumber=' + encodeURIComponent(lotNum), 'addPreventionData' + Math.floor(Math.random() * 10000 + 1));
                            } else {
                                popup(465, 635, 'AddPreventionData.jsp?search=true&demographic_no=<%= Encode.forJavaScript(demographic_no) %>&snomedId=' + encodeURIComponent(item.genericSnomedId || '') + '&brandSnomedId=' + encodeURIComponent(item.snomedId || ''), 'addPreventionData' + Math.floor(Math.random() * 10000 + 1));
                            }
                        });
                        dropdown.appendChild(div);
                    });
                    input.setAttribute('aria-expanded', 'true');
                    dropdown.setAttribute('aria-hidden', 'false');
                    dropdown.style.display = 'block';
                });
            });

            /* Keyboard navigation */
            input.addEventListener('keydown', function(e) {
                var items = dropdown.querySelectorAll('.ac-item');
                if (!items.length) return;
                if (e.key === 'ArrowDown') {
                    e.preventDefault();
                    activeIdx = Math.min(activeIdx + 1, items.length - 1);
                    clearActive(items);
                    items[activeIdx].classList.add('active');
                    items.forEach(function(el) { el.setAttribute('aria-selected', 'false'); });
                    items[activeIdx].setAttribute('aria-selected', 'true');
                    input.setAttribute('aria-activedescendant', items[activeIdx].id);
                } else if (e.key === 'ArrowUp') {
                    e.preventDefault();
                    activeIdx = Math.max(activeIdx - 1, 0);
                    clearActive(items);
                    items[activeIdx].classList.add('active');
                    items.forEach(function(el) { el.setAttribute('aria-selected', 'false'); });
                    items[activeIdx].setAttribute('aria-selected', 'true');
                    input.setAttribute('aria-activedescendant', items[activeIdx].id);
                } else if (e.key === 'Enter' && activeIdx >= 0) {
                    e.preventDefault();
                    items[activeIdx].dispatchEvent(new MouseEvent('mousedown'));
                } else if (e.key === 'Escape') {
                    dropdown.style.display = 'none';
                    input.setAttribute('aria-expanded', 'false');
                    dropdown.setAttribute('aria-hidden', 'true');
                    input.removeAttribute('aria-activedescendant');
                    activeIdx = -1;
                }
            });

            document.addEventListener('click', function(e) {
                if (!input.contains(e.target) && !dropdown.contains(e.target)) {
                    dropdown.style.display = 'none';
                    input.setAttribute('aria-expanded', 'false');
                    dropdown.setAttribute('aria-hidden', 'true');
                    input.removeAttribute('aria-activedescendant');
                    activeIdx = -1;
                }
            });
        })();

    </script>
    </body>
</html>
<%!
    String refused(Object re) {
        String ret = "Given";
        if (re instanceof java.lang.String) {

            if (re != null && re.equals("1")) {
                ret = "Refused";
            }
        }
        return ret;
    }

    String r(Object re, String result) {
        String ret = "";
        if (re instanceof java.lang.String) {
            if (re != null && re.equals("1")) {
                ret = "style=\"background: #FFDDDD;\"";
            } else if (re != null && re.equals("2")) {
                ret = "style=\"background: #FFCC24;\"";
            } else if (result != null && result.equalsIgnoreCase("pending")) {
                ret = "style=\"background: #FF00FF;\"";
            } else if (result != null && result.equalsIgnoreCase("other")) {
                ret = "style=\"background: #BDFCC9;\"";
            } else if (result != null && result.equals("abnormal")) {
                ret = "style=\"background: #ee5f5b;\"";

            }
        }
        return ret;
    }
%>
