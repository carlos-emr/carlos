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

<%@ page import="java.text.ParseException" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.PartialDateDao" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ConsentDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CVCMappingDao" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.providers.data.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.prevention.*" %>
<%@ page import="io.github.carlos_emr.carlos.providers.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicExtDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.PreventionsLotNrsDao" %>
<%@ page import="io.github.carlos_emr.carlos.prevention.PreventionData" %>
<%@ page import="io.github.carlos_emr.carlos.prevention.PreventionDisplayConfig" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.*" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
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
    DemographicExtDao demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);
    CanadianVaccineCatalogueManager cvcManager = SpringUtils.getBean(CanadianVaccineCatalogueManager.class);
    CVCMappingDao cvcMappingDao = SpringUtils.getBean(CVCMappingDao.class);
    CVCImmunizationDao cvcImmunizationDao = SpringUtils.getBean(CVCImmunizationDao.class);
    PartialDateDao partialDateDao = SpringUtils.getBean(PartialDateDao.class);

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    if (session.getValue("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String demographic_no = request.getParameter("demographic_no");
    String snomedId = request.getParameter("snomedId");
    String id = request.getParameter("id");
    Map<String, Object> existingPrevention = null;

    String providerName = "";
    String lot = "";
    String provider = (String) session.getValue("user");
    String dateFmt = "yyyy-MM-dd HH:mm";
    String prevDate = UtilDateUtilities.getToday(dateFmt);
    String completed = "0";
    String nextDate = "";
    String summary = "";
    String creatorProviderNo = "";
    String creatorName = "";
    String expiryDate = "";

    boolean never = false;
    Map<String, String> extraData = new HashMap<String, String>();
    boolean hasImportExtra = false;
    String annotation_display = CaseManagementNoteLink.DISP_PREV;


    boolean dhirEnabled = false;

    if ("true".equals(OscarProperties.getInstance().getProperty("dhir.enabled", "false"))) {
        dhirEnabled = true;
    }

    if (id != null) {

        existingPrevention = PreventionData.getPreventionById(id);

        prevDate = (String) existingPrevention.get("preventionDate");
        prevDate = partialDateDao.getDatePartial(prevDate, PartialDate.PREVENTION, Integer.parseInt(id), PartialDate.PREVENTION_PREVENTIONDATE);

        providerName = (String) existingPrevention.get("providerName");
        provider = (String) existingPrevention.get("provider_no");
        creatorProviderNo = (String) existingPrevention.get("creator");

        if (existingPrevention.get("refused") != null) {
            completed = (String) existingPrevention.get("refused");
        }
        if (existingPrevention.get("never") != null && ((String) existingPrevention.get("never")).equals("1")) {
            never = true;
        }
        nextDate = (String) existingPrevention.get("next_date");
        if (nextDate == null || nextDate.equalsIgnoreCase("null") || nextDate.equals("0000-00-00")) {
            nextDate = "";
        }
        summary = (String) existingPrevention.get("summary");
        extraData = PreventionData.getPreventionKeyValues(id);
        lot = (String) extraData.get("lot");
        expiryDate = (String) extraData.get("expiryDate");
        CaseManagementManager cmm = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);
        List<CaseManagementNoteLink> cml = cmm.getLinkByTableId(CaseManagementNoteLink.PREVENTIONS, Long.valueOf(id));
        hasImportExtra = (cml.size() > 0);
        snomedId = (String) existingPrevention.get("snomedId");

    }

    String prevention = request.getParameter("prevention");
    if (prevention == null && existingPrevention != null) {
        prevention = (String) existingPrevention.get("preventionType");
    }


    PreventionsLotNrsDao PreventionsLotNrsDao = (PreventionsLotNrsDao) SpringUtils.getBean(PreventionsLotNrsDao.class);
    List<String> lotNrList = PreventionsLotNrsDao.findLotNrs(prevention, false);

    String prevResultDesc = request.getParameter("prevResultDesc");

    String errorsToShow = "";
    boolean foundByLotNumber = false;
    CVCImmunization brandName = null;
    CVCImmunization generic = null;

    String addByLotNbr = request.getParameter("lotNumber");
    if (StringUtils.isNotEmpty(addByLotNbr)) {
        CVCMedicationLotNumber medLot = cvcManager.findByLotNumber(loggedInInfo, addByLotNbr);
        if (medLot != null) {
            String snomedCodeForMedication = medLot.getMedication().getSnomedCode();
            brandName = cvcManager.getBrandNameImmunizationBySnomedCode(loggedInInfo, snomedCodeForMedication);
            generic = cvcManager.getBrandNameImmunizationBySnomedCode(loggedInInfo, brandName.getParentConceptId());
            //Is there an OSCAR mapping for the prevention type
            CVCMapping mapping1 = cvcMappingDao.findBySnomedId(generic.getSnomedConceptId());
            if (mapping1 != null) {
                prevention = mapping1.getOscarName();
            } else {
                prevention = generic.getPicklistName();
            }
            snomedId = generic.getSnomedConceptId();
            foundByLotNumber = true;
        } else {
            errorsToShow = "Could not find this lot number in the system.";
        }
    }

    String addByLotNbr2 = request.getParameter("search");
    if (StringUtils.isNotEmpty(addByLotNbr2)) {
        String brandSnomedId = request.getParameter("brandSnomedId");

        generic = cvcManager.getBrandNameImmunizationBySnomedCode(loggedInInfo, snomedId);
        if (generic != null) {
            brandName = cvcManager.getBrandNameImmunizationBySnomedCode(loggedInInfo, brandSnomedId);
            prevention = generic.getPicklistName();
            CVCMapping mapping1 = cvcMappingDao.findBySnomedId(generic.getSnomedConceptId());
            if (mapping1 != null) {
                prevention = mapping1.getOscarName();
            } else {
                prevention = generic.getPicklistName();
            }
            foundByLotNumber = true;
        } else {
            errorsToShow = "Could not find this prevention in the system.";
        }
    }

    boolean isCvc = false;
    isCvc = snomedId != null;

    PreventionDisplayConfig pdc = PreventionDisplayConfig.getInstance();
    HashMap<String, String> prevHash = pdc.getPrevention(prevention);

    String layoutType = "default";
    if (prevHash != null) {
        layoutType = prevHash.get("layout");
        if (layoutType == null) {
            layoutType = "default";
        }
    }

    List<Map<String, String>> providers = ProviderData.getProviderList();
    List<Map<String, String>> allProviders = ProviderData.getProviderListOfAllTypes(true);

    //because inactive providers can be the original providers for an entry, the next two blocks checks if that's the case
    //and if so, dynamically adds the inactive original providers into the providers List for later use when constructing the GUI while continuing to exclude other inactive providers
    Boolean providerFoundInActiveList = false;
    for (int i = 0; i < providers.size(); i++) {
        Map<String, String> h = providers.get(i);
        if (h.get("providerNo").equals(provider)) {
            providerFoundInActiveList = true;
        }
    }

    //the below block is skipped if providers == -1 because we do NOT need to add this providers because -1 is used to mean "other", that is, a person outside the scope of this OSCAR
    //the issue that happens if -1 is added is that there usually is a system providers with -1 as the provider_no, resulting in this system account appearing to be the providers for a particular prevention
    //TODO: writer recommends that eventually -1 is not set as the provider_no
    if (!providerFoundInActiveList && !"-1".equals(provider)) {
        for (int i = 0; i < allProviders.size() && !providerFoundInActiveList; i++) {
            Map<String, String> h = allProviders.get(i);
            if (h.get("providerNo").equals(provider)) {
                providers.add(h);
                providerFoundInActiveList = true;
            }
        }
    }

    //Ensuring creator information is found/set. Note, the original creator can be inactive, so should iterate over allProviders list
    //the creatorProvider was deliberately not mixed into the providers List the same was the providers was, because the creatorProvider is only relevant here
    if (creatorProviderNo == "") {
        creatorProviderNo = provider;
    }
    for (int i = 0; i < allProviders.size(); i++) {
        Map<String, String> h = allProviders.get(i);
        if (h.get("providerNo").equals(creatorProviderNo)) {
            creatorName = h.get("lastName") + " " + h.get("firstName");
        }
    }

    //calc age at time of prevention
    Date dob = PreventionData.getDemographicDateOfBirth(LoggedInInfo.getLoggedInInfoFromSession(request), Integer.valueOf(demographic_no));
    Date dateOfPrev = parseDate(prevDate);
    String age = "";
    if (dateOfPrev != null) {
        age = UtilDateUtilities.calcAgeAtDate(dob, dateOfPrev);
    }
    DemographicData demoData = new DemographicData();
    String[] demoInfo = demoData.getNameAgeSexArray(LoggedInInfo.getLoggedInInfoFromSession(request), Integer.valueOf(demographic_no));
    String nameage = demoInfo[0] + ", " + demoInfo[1] + " " + demoInfo[2] + " " + age;
    HashMap<String, String> genders = new HashMap<String, String>();
    genders.put("M", "Male");
    genders.put("F", "Female");
    genders.put("U", "Unknown");

    String pBrand = request.getParameter("brandName");
    if (pBrand == null) pBrand = "";
    String pDIN = request.getParameter("din");
    if (pDIN == null) pDIN = "";
    String pDose = request.getParameter("dose");
    if (pDose == null) pDose = "";
    String pRoute = request.getParameter("route");
    if (pRoute == null) pRoute = "";
    String pUnit = request.getParameter("doseUnit");
    if (pUnit == null) pUnit = "";
    String pMaker = request.getParameter("manufacture");
    if (pMaker == null) pMaker = "";

%>
<html>
    <head>
        <title>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarprevention.index.oscarpreventiontitre"/>
        </title><!--I18n-->

        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" crossorigin="anonymous">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>



        <SCRIPT LANGUAGE="JavaScript">

            var isCvc = <%=isCvc%>;

            function showHideItem(id) {
                if (document.getElementById(id).style.display == 'none')
                    document.getElementById(id).style.display = '';
                else
                    document.getElementById(id).style.display = 'none';
            }

            function showItem(id) {
                document.getElementById(id).style.display = '';
            }

            function hideItem(id) {
                document.getElementById(id).style.display = 'none';
            }

            function showHideNextDate(id, nextDate, neverWarn) {
                if (document.getElementById(id).style.display == 'none') {
                    showItem(id);
                } else {
                    hideItem(id);
                    document.getElementById(nextDate).value = "";
                    document.getElementById(neverWarn).checked = false;

                }
            }

            function disableifchecked(ele, nextDate) {
                if (ele.checked == true) {
                    document.getElementById(nextDate).disabled = true;
                } else {
                    document.getElementById(nextDate).disabled = false;
                }
            }

        </SCRIPT>


        <script type="text/javascript">
            function hideExtraName(ele) {
                //alert(ele);
                if (ele.options[ele.selectedIndex].value != -1) {
                    hideItem('providerName');
                    //alert('hidding');
                } else {
                    showItem('providerName');
                    document.getElementById('providerName').focus();
                    //alert('showing');
                }
            }
        </script>

        <script type="text/javascript">
            function updateLotNr(elem) {
                if (elem.options[elem.selectedIndex].value != -1) {
                    hideItem('lot');
                }
                //show "other" in drop-down
                else if (elem.options[elem.selectedIndex].value == -1) {
                    document.getElementById('lot').value = "";
                    showItem('lot');
                    document.getElementById('lot').focus();
                }
            }
        </script>
        <script type="text/javascript">
            function hideLotDrop(elem) {
                var bFound = 0;
                var LotNr = document.getElementById('lot').value;
                var summary = document.getElementById('summary');
                //existing prevention record
                if (typeof (summary) != 'undefined' && summary != null) {
                    if (LotNr.length == 0) {
                        if (elem.options[0].value != -1) //table exists
                        {
                            elem.options[elem.options.length - 1].selected = true;
                            return;
                        } else {
                            hideItem('lotDrop');
                            showItem('lot');
                            return;
                        }
                    }
                }
                if (LotNr.length > 0) {
                    for (var i = 0; i < elem.length; i++) {
                        if (elem.options[i].value == LotNr) {
                            bFound = 1;
                            break;
                        }
                    }
                }
                if (elem.options[0].value == -1)
                    //no preventionslotnrs table
                {
                    hideItem('lotDrop');
                    showItem('lot');
                }
                // not in drop-down
                else if (!bFound && LotNr.length > 0) {
                    elem.options[elem.options.length - 1].selected = true;
                }
                //exists in dd
                else if (elem.options[elem.selectedIndex].value != -1) {
                    hideItem('lot');
                }
            }


            var warnOnWindowClose = true;

            function copyLot() {
                var cvcNameEl = document.getElementById('cvcName');
                var selectedOption = cvcNameEl ? cvcNameEl.options[cvcNameEl.selectedIndex] : null;
                var cvcNameVal = selectedOption ? selectedOption.value : undefined;
                var cvcLot = document.getElementById('cvcLot');
                if (cvcNameVal !== undefined && cvcNameVal != -1 && cvcLot && cvcLot.style.display !== 'none') {
                    document.getElementById('lot').value = cvcLot.value;
                    document.getElementById('name').value = selectedOption.text;
                }
            }

            function cancelCloseWarning() {
                warnOnWindowClose = false;
            }

            window.onbeforeunload = displayCloseWarning;

            function displayCloseWarning() {
                if (warnOnWindowClose) {
                    return 'Are you sure you want to close this window?';
                }
            }

            //{"lotNumber":"M042476","expiryDate":{"date":22,"day":5,"hours":0,"minutes":0,"month":2,"nanos":0,"seconds":0,"time":1553227200000,"timezoneOffset":240,"year":119}}

            function escapeHtml(unsafe1) {
                var unsafe = String(unsafe1);
                return unsafe
                    .replace(/&/g, "&amp;")
                    .replace(/</g, "&lt;")
                    .replace(/>/g, "&gt;")
                    .replace(/"/g, "&quot;")
                    .replace(/'/g, "&#039;");
            }

            var lots;
            var startup = false, startup2 = false;

            function changeCVCName() {
                lots = null;

                var snomedId = document.getElementById('cvcName').value;
                var lot = document.getElementById('lot');
                var cvcLot = document.getElementById('cvcLot');
                var expiryDate = document.getElementById('expiryDate');
                var unknownName = document.getElementById('unknownName');
                var name = document.getElementById('name');

                if (snomedId == "-1") {
                    lot.style.display = '';
                    cvcLot.style.display = 'none';
                    if (expiryDate) expiryDate.value = '';
                    if (unknownName) unknownName.style.display = '';
                } else if (snomedId == "0") {
                    if (name) name.style.display = '';
                } else {
                    if (unknownName) unknownName.style.display = 'none';
                    var formData = new URLSearchParams();
                    formData.append('method', 'getLotNumberAndExpiryDates');
                    formData.append('snomedConceptId', snomedId);
                    fetch('<%=request.getContextPath()%>/cvc.do', {
                        method: 'POST',
                        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                        body: formData.toString()
                    })
                    .then(function(response) { return response.json(); })
                    .then(function(data) {
                        if (data != null && Array.isArray(data) && data.length > 0) {
                            lot.style.display = 'none';
                            cvcLot.style.display = '';
                            cvcLot.innerHTML = '';
                            cvcLot.appendChild(new Option('', ''));

                            for (var x = 0; x < data.length; x++) {
                                var item = data[x];
                                var d = new Date(data[x].expiryDate.time);
                                var month = ((d.getMonth() + 1) > 9) ? (d.getMonth() + 1) : ("0" + (d.getMonth() + 1));
                                var day = ((d.getDate()) > 9) ? (d.getDate()) : ("0" + (d.getDate()));
                                var output = d.getFullYear() + "-" + month + "-" + day;

                                var opt = document.createElement('option');
                                opt.value = escapeHtml(item.lotNumber);
                                opt.setAttribute('expiryDate', output);
                                opt.text = escapeHtml(item.lotNumber);

                                if (startup2 && escapeHtml(item.lotNumber) == '<%=addByLotNbr %>') {
                                    opt.selected = true;
                                    startup2 = false;
                                } else if (startup && escapeHtml(item.lotNumber) == '<%=(existingPrevention != null)?existingPrevention.get("lot"):"" %>') {
                                    opt.selected = true;
                                    startup = false;
                                }
                                cvcLot.appendChild(opt);
                                updateCvcLot();
                            }
                        } else {
                            cvcLot.style.display = 'none';
                            cvcLot.innerHTML = '';
                            lot.style.display = '';
                        }
                    });
                }
            }

            function updateCvcLot() {
                var cvcLot = document.getElementById('cvcLot');
                var selected = cvcLot ? cvcLot.options[cvcLot.selectedIndex] : null;
                document.getElementById('expiryDate').value = selected ? (selected.getAttribute('expiryDate') || '') : '';
            }


            function handleFormSubmission() {
                const isValidDate = validatePreventionDate();
                if (isValidDate) {
                    copyLot();
                    cancelCloseWarning();
                }

                return isValidDate;
            }

            function validatePreventionDate() {
                const prevDateInput = document.getElementById('prevDate').value;
                const errorMessage = document.getElementById('errorPrevDateMessage');
                const validationResult = checkDateFormat(prevDateInput);
                let isValidDate = false;
                if (validationResult === true) {
                    errorMessage.textContent = '';
                    isValidDate = true;
                } else if (validationResult === "Required") {
                    errorMessage.textContent = 'Date is required!';
                } else {
                    errorMessage.textContent = 'Invalid date format! Expected formats are YYYY, YYYY-MM, YYYY-MM-DD, or YYYY-MM-DD hh:mm.';
                }

                return isValidDate;
            }

            function checkDateFormat(input) {
                const dateFormats = [
                    /^\d{4}$/,
                    /^\d{4}-(0[1-9]|1[0-2])$/,
                    /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])$/,
                    /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1]) (0[0-9]|1[0-9]|2[0-3]):([0-5][0-9])$/
                ];

                if (!input.trim()) {
                    return "Required";
                }

                for (let i = 0; i < dateFormats.length; i++) {
                    if (dateFormats[i].test(input)) {
                        return true;
                    }
                }

                return false;
            }

            <%
                if(foundByLotNumber) {
            %>
            document.addEventListener('DOMContentLoaded', function () {
                startup2 = true;
                changeCVCName();
            });

            <%
                }
            %>


            <% if(existingPrevention != null && snomedId != null && existingPrevention.get("brandSnomedId") != null) { %>
            document.addEventListener('DOMContentLoaded', function () {
                startup = true;
                document.getElementById('cvcName').value = '<%=existingPrevention.get("brandSnomedId")%>';
                changeCVCName();
            });
            <% } %>


            function changeSite(el) {
                var val = el.options[el.selectedIndex].value;
                var locationDiv = document.getElementById('locationDiv');
                var location2 = document.getElementById('location2');
                if (val == 'Other') {
                    locationDiv.style.display = '';
                } else {
                    locationDiv.style.display = 'none';
                    location2.value = '';
                }
            }
        </script>
    </head>

    <body class="BodyStyle" vlink="#0000FF" onload="disableifchecked(document.getElementById('neverWarn'),'nextDate');">
    <!--  -->
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn" width="100">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarprevention.index.oscarpreventiontitre"/>
            </td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td>
                            <%=StringEscapeUtils.escapeHtml4(nameage)%>
                        </td>
                        <td>&nbsp;

                        </td>
                        <td style="text-align:right">
                            <a
                                href="javascript:popupStart(300,400,'About.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a>
                            | <a href="javascript:popupStart(300,400,'License.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.license"/></a>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" valign="top">


                &nbsp;
                <!--
               <%
                 DemographicExt ineligx = demographicExtDao.getDemographicExt(Integer.parseInt(demographic_no),prevention+"Inelig");
				 String inelig = "";
				 if(ineligx != null) {
					 inelig = ineligx.getValue();
				 }

                 if (inelig.equals("yes")){ %>
                    Patient Ineligible<br>
                    <a href="setPatientIneligible.jsp?prev=<%=prevention%>&demo=<%=demographic_no%>&elig=yes">Set Patient Eligible</a>
                 <%}else{%>
                    <a href="setPatientIneligible.jsp?prev=<%=prevention%>&demo=<%=demographic_no%>">Set Patient Ineligible</a>
                 <%}%>
-->
            </td>
            <td valign="top" class="MainTableRightColumn">
                <%

                    if (dhirEnabled && session.getAttribute("oneIdEmail") == null) {
                %>
                <div class="alert alert-danger">
                    Warning: You are not logged into OneId and will not be able to submit data to DHIR
                </div>
                <% } %>

                <%
                    if (request.getAttribute("errors") != null) {
                        List<String> errorList = (List<String>) request.getAttribute("errors");
                %>
                <ul class="alert alert-danger"><%
                    for (String error : errorList) {
                %>
                    <li><%=error %>
                    </li>
                    <%
                        }
                    %></ul>
                <%
                    }

                %>

                <% if (prevHash == null) { %>
                <h3 class="alert alert-danger">Prevention not found!</h3>
                <%} else { %>
                <form action="${pageContext.request.contextPath}/oscarPrevention/AddPrevention.do" method="post" onsubmit="return handleFormSubmission()">
                    <input type="hidden" name="prevention" value="<%=prevention%>"/>
                    <input type="hidden" name="demographic_no" value="<%=demographic_no%>"/>
                    <input type="hidden" name="providerNo" value="<%=provider%>"/>
                    <%if (snomedId != null) {%>
                    <input type="hidden" name="snomedId" value="<%=snomedId %>"/>
                    <%} %>
                    <% if (id != null) { %>
                    <input type="hidden" name="id" value="<%=id%>"/>
                    <input type="hidden" name="layoutType" value="<%=layoutType%>"/>

                    <div class="prevention">
                        <fieldset>
                            <legend>Summary</legend>
                            <textarea class="form-control" name="summary" readonly><%=summary%></textarea>
                            <%if (hasImportExtra) { %>
                            <a href="javascript:void(0);" title="Extra data from Import"
                               onclick="window.open('<%= request.getContextPath() %>/annotation/importExtra.jsp?display=<%=annotation_display %>&amp;table_id=<%=id %>&amp;demo=<%=demographic_no %>','anwin','width=400,height=250');">
                                <img src="<%= request.getContextPath() %>/images/notes.gif" align="right" alt="Extra data from Import" height="16"
                                     width="13" border="0"> </a>
                            <%} %>
                        </fieldset>
                    </div>
                    <% } %>
                    <%if (layoutType.equals("injection")) {%>
                    <div class="prevention">
                        <fieldset>
                            <legend>Prevention : <%=prevention%>
                            </legend>
                            <div>
                                <input name="given" type="radio" class="form-check-input" value="given"      <%=checked(completed,"0")%>
                                       onclick="document.getElementById('providerDrop').value='<%=LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo() %>';hideExtraName(document.getElementById('providerDrop'))">Completed</input>
                                <br/>
                                <input name="given" type="radio" class="form-check-input" value="given_ext"  <%=checked(completed,"3")%>
                                       onclick="document.getElementById('providerDrop').value='-1';hideExtraName(document.getElementById('providerDrop'))">Completed
                                externally</input><br/>
                                <input name="given" type="radio" class="form-check-input"
                                       value="refused"    <%=checked(completed,"1")%>>Refused</input><br/>
                                <input name="given" type="radio" class="form-check-input" value="ineligible" <%=checked(completed,"2")%>>Ineligible</input>
                            </div>
                            <div>&nbsp;</div>
                            <div style="margin-left:30px;">
                                <label for="prevDate" class="form-label fields">Date:</label> <input type="text" class="form-control" name="prevDate"
                                                                                          id="prevDate"
                                                                                          value="<%=prevDate%>"
                                                                                          size="15" required> <a
                                    id="date"><img title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
                                <br>
                                <div id="errorPrevDateMessage"
                                     class="alert alert-danger"></div>
                                <label for="provider" class="form-label fields">Provider:</label> <input type="text" class="form-control"
                                                                                              name="providerName"
                                                                                              id="providerName"
                                                                                              value="<%=providerName%>"/>
                                <select onchange="javascript:hideExtraName(this);" class="form-select" id="providerDrop" name="provider">
                                    <%
                                        for (int i = 0; i < providers.size(); i++) {
                                            Map<String, String> h = providers.get(i);
                                    %>
                                    <option value="<%= h.get("providerNo")%>" <%= (h.get("providerNo").equals(provider) ? " selected" : "") %>><%= h.get("lastName") %> <%= h.get("firstName") %>
                                    </option>
                                    <%}%>
                                    <option value="-1" <%= ("-1".equals(provider) ? " selected" : "") %> >Other</option>
                                </select>
                                <br/>
                                <label for="creator" class="form-label fields">Creator:</label> <input type="text" class="form-control" name="creator"
                                                                                            value="<%=creatorName%>"
                                                                                            readonly/> <br/>
                            </div>
                        </fieldset>
                        <fieldset>
                            <legend>Result</legend>

                            <%
                                if (snomedId != null) {
                                    List<CVCImmunization> tnList = cvcManager.getImmunizationsByParent(snomedId);
                                    if (tnList != null && tnList.size() > 0) {
                            %>

                            <label for="cvcName" class="form-label">Trade Name:</label>
                            <select id="cvcName" name="cvcName" class="form-select" onChange="changeCVCName()">
                                <option value="-1">Select Below</option>
                                <%
                                    //get the tradenames associated with this generic
                                    for (CVCImmunization tn : tnList) {
                                        String selected = "";
                                        if (existingPrevention != null) {
                                            String brandSnomedId = (String) existingPrevention.get("brandSnomedId");
                                            if (brandSnomedId != null && brandSnomedId.equals(tn.getSnomedConceptId())) {
                                                selected = "selected=\"selected\"";
                                            }
                                        }
                                        if (foundByLotNumber) {
                                            String brandSnomedId = brandName.getSnomedConceptId();
                                            if (brandSnomedId != null && brandSnomedId.equals(tn.getSnomedConceptId())) {
                                                selected = "selected=\"selected\"";
                                            }
                                        }
                                %>
                                <option value="<%=tn.getSnomedConceptId()%>" <%=selected%>><%=tn.getDisplayName() %>
                                </option>
                                <%
                                    }
                                %>

                            </select>

                            <br/>
                            <span id="unknownName" style="display:block"><label for="name" class="form-label">Name</label> <input
                                    type="text" class="form-control" id="name" name="name"
                                    value="<%=str((extraData.get("name")),"")+Encode.forHtmlAttribute(pBrand)%>"/> <br/><br/></span>
                            <%

                            } else {
                            %> <label for="name" class="form-label">Name:</label> <input type="text" class="form-control" id="name" name="name"
                                                                      value="<%=str((extraData.get("name")),"")+Encode.forHtmlAttribute(pBrand)%>"/>
                            <br/> <%
                            }

                        } else {
                        %> <label for="name" class="form-label">Name:</label> <input type="text" class="form-control" id="name" name="name"
                                                                  value="<%=str((extraData.get("name")),"")+Encode.forHtmlAttribute(pBrand)%>"/>
                            <br/>

                            <% } %>


                            <label for="location" class="form-label">Location:</label>

                            <select name="location" id="location" class="form-select" onChange="changeSite(this)">
                                <option value=""></option>
                                <%
                                    String locationSelected = " selected=\"selected\" ";
                                %>
                                <option value="Superior Deltoid Lt" <%="Superior Deltoid Lt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Superior Deltoid Lt
                                </option>
                                <option value="Inferior Deltoid Lt" <%="Inferior Deltoid Lt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Inferior Deltoid Lt
                                </option>
                                <option value="Anterolateral Thigh Lt" <%="Anterolateral Thigh Lt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Anterolateral Thigh Lt
                                </option>
                                <option value="Gluteal Lt" <%="Gluteal Lt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Gluteal Lt
                                </option>
                                <option value="Superior Deltoid Rt" <%="Superior Deltoid Rt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Superior Deltoid Rt
                                </option>
                                <option value="Inferior Deltoid Rt" <%="Inferior Deltoid Rt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Inferior Deltoid Rt
                                </option>
                                <option value="Anterolateral Thigh Rt" <%="Anterolateral Thigh Rt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Anterolateral Thigh Rt
                                </option>
                                <option value="Gluteal Rt" <%="Gluteal Rt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Gluteal Rt
                                </option>
                                <option value="Arm Lt" <%="Arm Lt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Arm Lt
                                </option>
                                <option value="Arm Rt" <%="Arm Rt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Arm Rt
                                </option>
                                <option value="Unknown" <%="Unknown".equals(str((extraData.get("location")), "")) || extraData.get("location") == null ? locationSelected : "" %>>
                                    Unknown
                                </option>
                                <option value="Mouth" <%="Mouth".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Mouth
                                </option>
                                <option value="Deltoid Lt" <%="Deltoid Lt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Deltoid Lt
                                </option>
                                <option value="Deltoid Rt" <%="Deltoid Rt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Deltoid Rt
                                </option>
                                <option value="Naris Lt" <%="Naris Lt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Naris Lt
                                </option>
                                <option value="Naris Rt" <%="Naris Rt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Naris Rt
                                </option>
                                <option value="Forearm Lt" <%="Forearm Lt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Forearm Lt
                                </option>
                                <option value="Forearm Rt" <%="Forearm Rt".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Forearm Rt
                                </option>
                                <option value="Other" <%="Other".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Other
                                </option>
                                <option value="Nares (Lt and Rt)" <%="Nares (Lt and Rt)".equals(str((extraData.get("location")), "")) ? locationSelected : "" %>>
                                    Nares (Lt and Rt)
                                </option>

                            </select>

                            <%
                                String locationDisplay = "none";
                                if ("Other".equals(str(extraData.get("location"), ""))) {
                                    locationDisplay = "block";
                                }
                            %>
                            <br>
                            <div id="locationDiv" style="display:<%=locationDisplay%>">
                                <label for="location2">Specify Location:</label>

                                <input type="text" class="form-control" name="location2" id="location2"
                                       value="<%=str((extraData.get("location2")),"")%>"/>
                            </div>

                            <br/>
                            <label for="route" class="form-label">Route:</label>
                            <select name="route" id="route" class="form-select">
                                <option value=""></option>
                                <%
                                    String routeSelected = " selected=\"selected\" ";
                                %>
                                <option value="ID" <%="ID".equals(str((extraData.get("route")), Encode.forHtmlAttribute(pRoute))) ? routeSelected : "" %>>
                                    Intradermal: ID
                                </option>
                                <option value="IM" <%="IM".equals(str((extraData.get("route")), Encode.forHtmlAttribute(pRoute))) ? routeSelected : "" %>>
                                    Intramuscular: IM
                                </option>
                                <option value="IN" <%="IN".equals(str((extraData.get("route")), Encode.forHtmlAttribute(pRoute))) ? routeSelected : "" %>>
                                    Intranasal: IN
                                </option>
                                <option value="PO" <%="PO".equals(str((extraData.get("route")), Encode.forHtmlAttribute(pRoute))) ? routeSelected : "" %>>
                                    Oral: PO
                                </option>
                                <option value="SC" <%="SC".equals(str((extraData.get("route")), Encode.forHtmlAttribute(pRoute))) ? routeSelected : "" %>>
                                    Subcutaneous: SC
                                </option>
                            </select>
                            <br/>
                            <label for="din" class="form-label">DIN:</label>
                            <input type="text" class="form-control" name="din" id="din" value="<%=str((extraData.get("din")),Encode.forHtmlAttribute(pDIN))%>"/>
                            <br/>
                            <%
                                String dose = str((extraData.get("dose")), "");
                                String d1 = pDose;
                                String d2 = pUnit;
                                if (dose.split(" ").length == 2) {
                                    String d3 = dose.split(" ")[1];
                                    if (!d3.equals("mL") && !d3.equals("mg") && !d3.equals("g") && !d3.equals("capsule") && !d3.equals("vial")) {
                                        d1 = dose;
                                    } else {
                                        d1 = dose.split(" ")[0];
                                        d2 = dose.split(" ")[1];
                                    }
                                } else {
                                    d1 = pDose;
                                }

                                if ("".equals(dose)) {
                                    d2 = "mL";
                                }
                            %>

                            <label for="dose" class="form-label">Dose:</label> <input type="text" class="form-control" name="dose" id="dose" value="<%=Encode.forHtmlAttribute(d1)%>"/>
                            <br>
                            <label for="doseUnit" class="form-label">Dose Unit:</label>
                            <select name="doseUnit" class="form-select">
                                <option value="" <%="".equals(d2) ? "selected=\"selected\" " : "" %>></option>
                                <option value="mL" <%="mL".equals(d2) ? "selected=\"selected\" " : "" %>>mL</option>
                                <option value="mg" <%="mg".equals(d2) ? "selected=\"selected\" " : "" %>>mg</option>
                                <option value="g" <%="g".equals(d2) ? "selected=\"selected\" " : "" %>>g</option>
                                <option value="capsule" <%="capsule".equals(d2) ? "selected=\"selected\" " : "" %>>
                                    capsule
                                </option>
                                <option value="vial" <%="vial".equals(d2) ? "selected=\"selected\" " : "" %>>vial
                                </option>

                            </select>

                            <br/>
                            <%if (!isCvc) { %>
                            <label for="lot" class="form-label">Lot:</label> <input type="text" class="form-control" name="lot" id="lot"
                                                                 value="<%=str(lot,"")%>"/>
                            <select onchange="javascript:updateLotNr(this);" class="form-select" id="lotDrop" name="lotItem">
                                <%
                                    for (String lotnr : lotNrList) {
                                %>
                                <option value="<%=lotnr%>" <%= (lotnr.equals(lot) ? " selected" : "") %>><%=lotnr%>
                                </option>
                                <%}%>
                                <option value="-1">Other</option>
                            </select><br/>
                            <%} else { %>
                            <div id="cvcLotDiv">
                                <label for="cvcLot">Lot:</label>
                                <input type="text" class="form-control" name="lot" id="lot" value="<%=str(lot,"")%>" style="display:block"/>

                                <select onchange="javascript:updateCvcLot();" class="form-select" id="cvcLot" name="cvcLot"
                                        style="display:none;">

                                </select></div>
                            <label for="expiryDate" class="form-label">Expiry Date:</label> <input type="text" class="form-control" name="expiryDate"
                                                                                id="expiryDate"
                                                                                value="<%=str((extraData.get("expiryDate")),"")%>"/><br/>
                            <% } %>
                            <label for="manufacture" class="form-label">Manufacture:</label> <input type="text" class="form-control" name="manufacture"
                                                                                 id="manufacture"
                                                                                 value="<%=str((extraData.get("manufacture")),Encode.forHtmlAttribute(pMaker))%>"/><br/>
                        </fieldset>
                        <fieldset>
                            <legend>Comments</legend>
                            <textarea class="form-control" name="comments"><%=str((extraData.get("comments")), "")%></textarea>
                        </fieldset>
                    </div>
                    <script type="text/javascript">
                        hideExtraName(document.getElementById('providerDrop'));
                    </script>
                    <script type="text/javascript">
                        hideLotDrop(document.getElementById('lotDrop'));
                    </script>
                    <%} else if (layoutType.equals("h1n1")) {%>
                    <div class="prevention">
                        <fieldset>
                            <legend>Prevention : <%=prevention%>
                            </legend>
                            <div>
                                <input name="given" type="radio" class="form-check-input" value="given"      <%=checked(completed,"0")%>
                                       onclick="document.getElementById('providerDrop').value='<%=LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo() %>';hideExtraName(document.getElementById('providerDrop'))">Completed</input>
                                <br/>
                                <input name="given" type="radio" class="form-check-input" value="given_ext"  <%=checked(completed,"3")%>
                                       onclick="document.getElementById('providerDrop').value='-1';hideExtraName(document.getElementById('providerDrop'))">Completed
                                externally</input><br/>
                                <input name="given" type="radio" class="form-check-input"
                                       value="refused"    <%=checked(completed,"1")%>>Refused</input><br/>
                                <input name="given" type="radio" class="form-check-input" value="ineligible" <%=checked(completed,"2")%>>Ineligible</input>
                            </div>
                            <div>&nbsp;</div>
                            <div style="margin-left:30px;">
                                <label for="prevDate" class="form-label fields">Date:</label> <input type="text" class="form-control" name="prevDate"
                                                                                          id="prevDate"
                                                                                          value="<%=prevDate%>"
                                                                                          size="15" required> <a
                                    id="date"><img title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
                                <br>
                                <div id="errorPrevDateMessage"
                                     class="alert alert-danger"></div>
                                <label for="provider" class="form-label fields">Provider:</label> <input type="text" class="form-control"
                                                                                              name="providerName"
                                                                                              id="providerName"
                                                                                              value="<%=providerName%>"/>
                                <select onchange="javascript:hideExtraName(this);" class="form-select" id="providerDrop" name="provider">
                                    <%
                                        for (int i = 0; i < providers.size(); i++) {
                                            Map<String, String> h = providers.get(i);
                                    %>
                                    <option value="<%= h.get("providerNo")%>" <%= (h.get("providerNo").equals(provider) ? " selected" : "") %>><%= h.get("lastName") %> <%= h.get("firstName") %>
                                    </option>
                                    <%}%>
                                    <option value="-1" <%= ("-1".equals(provider) ? " selected" : "") %> >Other</option>
                                </select>
                                <br/>
                                <label for="creator" class="form-label fields">Creator:</label> <input type="text" class="form-control" name="creator"
                                                                                            value="<%=creatorName%>"
                                                                                            readonly/> <br/>
                            </div>
                        </fieldset>
                        <fieldset>
                            <legend>Result</legend>
                            <label for="location" class="form-label">Location:</label> <input type="text" class="form-control" name="location"
                                                                           value="<%=str((extraData.get("location")),"")%>"/>
                            <br/>
                            <label for="location2" class="form-label">Other Location:</label> <input type="text" class="form-control" name="location2"
                                                                                 value="<%=str((extraData.get("location2")),"")%>"/>
                            <br/>
                            <label for="route" class="form-label">Route:</label> <input type="text" class="form-control" name="route"
                                                                     value="<%=str((extraData.get("route")),"")%>"/><br/>
                            <label for="dose" class="form-label">Dose:</label> <input type="text" class="form-control" name="dose"
                                                                   value="<%=str((extraData.get("dose")),"")%>"/><br/>
                            <label for="dose1" class="form-label">Dose 1:</label> <input type="checkbox" class="form-check-input" name="dose1"
                                                                      value="true" <%=checked(str((extraData.get("dose1")), ""), "true")%>/><br/>
                            <label for="dose2" class="form-label">Dose 2:</label> <input type="checkbox" class="form-check-input" name="dose2"
                                                                      value="true" <%=checked(str((extraData.get("dose2")), ""), "true")%>/><br/>
                            <label for="lot" class="form-label">Lot:</label> <input type="text" class="form-control" name="lot"
                                                                 value="<%=str((extraData.get("lot")),"")%>"/><br/>
                            <label for="manufacture" class="form-label">Manufacture:</label> <input type="text" class="form-control" name="manufacture"
                                                                                 value="<%=str((extraData.get("manufacture")),"")%>"/><br/>
                        </fieldset>
                        <fieldset>
                            <legend>Info</legend>
                            <% String gender = genders.get(demoInfo[2]);
                                if (gender == null) {
                                    gender = genders.get("U");
                                }

                            %>
                            <label for="gender" class="form-label">Gender:</label> <input type="text" class="form-control" name="gender" readonly
                                                                       value="<%=gender%>"/> <br/>
                            <label for="age" class="form-label">Age:</label> <input type="text" class="form-control" name="age" readonly value="<%=age%>"/><br/>
                            <label for="chronic" class="form-label">Chronic Condition:</label>
                            <select name="chronic" class="form-select">
                                <option value="false">No</option>
                                <option value="true" <%= str((extraData.get("chronic")), "").equalsIgnoreCase("true") ? "selected" : "" %> >
                                    Yes
                                </option>
                                <option value="cardiac disorder" <%= str((extraData.get("chronic")), "").equalsIgnoreCase("cardiac disorder") ? "selected" : "" %> >
                                    Cardiac Disorder
                                </option>
                                <option value="diabetes" <%= str((extraData.get("chronic")), "").equalsIgnoreCase("diabetes") ? "selected" : "" %> >
                                    Diabetes
                                </option>
                                <option value="cancer" <%= str((extraData.get("chronic")), "").equalsIgnoreCase("cancer") ? "selected" : "" %> >
                                    Cancer
                                </option>
                                <option value="immunodeficiency" <%= str((extraData.get("chronic")), "").equalsIgnoreCase("immunodeficiency") ? "selected" : "" %> >
                                    Immunodeficiency
                                </option>
                                <option value="immunosuppression" <%= str((extraData.get("chronic")), "").equalsIgnoreCase("immunosuppression") ? "selected" : "" %> >
                                    Immunosuppression
                                </option>
                                <option value="renal disease" <%= str((extraData.get("chronic")), "").equalsIgnoreCase("renal disease") ? "selected" : "" %> >
                                    Renal Disease
                                </option>
                                <option value="anemia or hemoglobinopathy" <%= str((extraData.get("chronic")), "").equalsIgnoreCase("anemia or hemoglobinopathy") ? "selected" : "" %> >
                                    Anemia or Hemoglobinopathy
                                </option>
                                <option value="compromised management of respiratory secretions" <%= str((extraData.get("chronic")), "").equalsIgnoreCase("compromised management of respiratory secretions") ? "selected" : "" %> >
                                    Compromised Management of Respiratory Secretions
                                </option>
                                <option value="Children/Adolescent with Longterm Acetylsalicylic Acid" <%= str((extraData.get("chronic")), "").equalsIgnoreCase("Children/Adolescent with Longterm Acetylsalicylic Acid") ? "selected" : "" %> >
                                    Children/Adolescent with Longterm Acetylsalicylic Acid
                                </option>
                            </select><br/>
                            <label for="pregnant" class="form-label">Pregnant:</label> <input type="checkbox" class="form-check-input" name="pregnant"
                                                                           value="true" <%=checked(str((extraData.get("pregnant")), ""), "true")%>/><br/>
                            <label for="remote" class="form-label">Remote Setting:</label> <input type="checkbox" class="form-check-input" name="remote"
                                                                               value="true" <%=checked(str((extraData.get("remote")), ""), "true")%>/><br/>
                            <label for="healthcareworker" class="form-label">Health Care Worker:</label>
                            <select name="healthcareworker" class="form-select">
                                <option value="false">No</option>
                                <option value="true" <%= str((extraData.get("healthcareworker")), "").equalsIgnoreCase("true") ? "selected" : "" %> >
                                    Yes
                                </option>
                                <option value="acute care" <%= str((extraData.get("healthcareworker")), "").equalsIgnoreCase("acute care") ? "selected" : "" %> >
                                    Acute Care
                                </option>
                                <option value="chronic care" <%= str((extraData.get("healthcareworker")), "").equalsIgnoreCase("chronic care") ? "selected" : "" %> >
                                    Chronic Care
                                </option>
                                <option value="community care" <%= str((extraData.get("healthcareworker")), "").equalsIgnoreCase("community care") ? "selected" : "" %> >
                                    Ambulatory/Community Care
                                </option>
                                <option value="emergency medical services" <%= str((extraData.get("healthcareworker")), "").equalsIgnoreCase("emergency medical services") ? "selected" : "" %> >
                                    Emergency Medical Services
                                </option>
                                <option value="laboratory" <%= str((extraData.get("healthcareworker")), "").equalsIgnoreCase("laboratory") ? "selected" : "" %> >
                                    Laboratory
                                </option>
                                <option value="public health" <%= str((extraData.get("healthcareworker")), "").equalsIgnoreCase("public health") ? "selected" : "" %> >
                                    Public Health
                                </option>
                                <option value="pharmacy" <%= str((extraData.get("healthcareworker")), "").equalsIgnoreCase("pharmacy") ? "selected" : "" %> >
                                    Pharmacy
                                </option>
                                <option value="vaccine manufacturer" <%= str((extraData.get("healthcareworker")), "").equalsIgnoreCase("vaccine manufacturer") ? "selected" : "" %> >
                                    Vaccine Mfr
                                </option>
                            </select><br/>

                            <label for="householdcontact" class="form-label">Household Contact or Care Provider:</label> <input
                                type="checkbox" class="form-check-input" name="householdcontact"
                                value="true" <%=checked(str((extraData.get("householdcontact")), ""), "true")%>/><br/>
                            <%
                                boolean bothfirstresponders = false;
                                if (str((extraData.get("firstresponder")), "").equalsIgnoreCase("true")) {
                                    bothfirstresponders = true;
                                }

                            %>
                            <label for="firstresponderpolice" class="form-label">First Responder Police:</label> <input type="checkbox" class="form-check-input"
                                                                                                     name="firstresponderpolice"
                                                                                                     value="true" <%=bothfirstresponders == true ? "checked" : checked(str((extraData.get("firstresponderpolice")), ""), "true")%>/><br/>
                            <label for="firstresponderfire" class="form-label">First Responder Fire:</label> <input type="checkbox" class="form-check-input"
                                                                                                 name="firstresponderfire"
                                                                                                 value="true" <%=bothfirstresponders == true ? "checked" : checked(str((extraData.get("firstresponderfire")), ""), "true")%>/><br/>
                            <label for="swineworker" class="form-label">Swine Worker:</label> <input type="checkbox" class="form-check-input" name="swineworker"
                                                                                  value="true" <%=checked(str((extraData.get("swineworker")), ""), "true")%>/><br/>
                            <label for="poultryworker" class="form-label">Poultry Worker:</label> <input type="checkbox" class="form-check-input"
                                                                                      name="poultryworker"
                                                                                      value="true" <%=checked(str((extraData.get("poultryworker")), ""), "true")%>/><br/>
                            <label for="firstnations" class="form-label">First Nations:</label> <input type="checkbox" class="form-check-input" name="firstnations"
                                                                                    value="true" <%=checked(str((extraData.get("firstnations")), ""), "true")%>/><br/>
                        </fieldset>
                        <fieldset>
                            <legend>Comments</legend>
                            <textarea class="form-control" name="comments"><%=str((extraData.get("comments")), "")%></textarea>
                        </fieldset>
                    </div>
                    <script type="text/javascript">
                        hideExtraName(document.getElementById('providerDrop'));
                    </script>
                    <%} else if (layoutType.equals("PAPMAM")) {/*next layout type*/%>
                    <div class="prevention">
                        <fieldset>
                            <legend>Prevention : <%=prevention%>
                            </legend>
                            <div>
                                <input name="given" type="radio" class="form-check-input" value="given"      <%=checked(completed,"0")%>
                                       onclick="document.getElementById('providerDrop').value='<%=LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo() %>';hideExtraName(document.getElementById('providerDrop'))">Completed</input>
                                <br/>
                                <input name="given" type="radio" class="form-check-input" value="given_ext"  <%=checked(completed,"3")%>
                                       onclick="document.getElementById('providerDrop').value='-1';hideExtraName(document.getElementById('providerDrop'))">Completed
                                externally</input><br/>
                                <input name="given" type="radio" class="form-check-input"
                                       value="refused"    <%=checked(completed,"1")%>>Refused</input><br/>
                                <input name="given" type="radio" class="form-check-input" value="ineligible" <%=checked(completed,"2")%>>Ineligible</input>
                            </div>
                            <div>&nbsp;</div>
                            <div style="margin-left:30px;">
                                <label for="prevDate" class="form-label fields">Date:</label> <input type="text" class="form-control" name="prevDate"
                                                                                                          id="prevDate"
                                                                                                          value="<%=prevDate%>"
                                                                                                          size="15" required> <a
                                    id="date"><img title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
                                <br>
                                <div id="errorPrevDateMessage"
                                     class="alert alert-danger"></div>
                                <label for="provider" class="form-label fields">Provider:</label> <input type="text" class="form-control"
                                                                                                              name="providerName"
                                                                                                              id="providerName"
                                                                                                              value="<%=providerName%>"/>
                                <select onchange="javascript:hideExtraName(this);" class="form-select" id="providerDrop" name="provider">
                                    <%
                                        for (int i = 0; i < providers.size(); i++) {
                                            Map<String, String> h = providers.get(i);
                                    %>
                                    <option value="<%= h.get("providerNo")%>" <%= (h.get("providerNo").equals(provider) ? " selected" : "") %>><%= h.get("lastName") %> <%= h.get("firstName") %>
                                    </option>
                                    <%}%>
                                    <option value="-1" <%= ("-1".equals(provider) ? " selected" : "") %> >Other</option>
                                </select>
                                <br/>
                                <label for="creator" class="form-label fields">Creator:</label> <input type="text" class="form-control" name="creator"
                                                                                            value="<%=creatorName%>"
                                                                                            readonly/> <br/>
                            </div>
                        </fieldset>
                        <fieldset>
                            <legend>Result</legend>
                            <% if (extraData.get("result") == null) {
                                extraData.put("result", "pending");
                            } %>
                            <%=str(prevResultDesc, "")%><br/>
                            <input type="radio" class="form-check-input" name="result"
                                   value="pending" <%=checked( (extraData.get("result")) ,"pending")%> >Pending</input>
                            <br/>
                            <input type="radio" class="form-check-input" name="result"
                                   value="normal"  <%=checked((extraData.get("result")),"normal")%> >Normal</input><br/>
                            <input type="radio" class="form-check-input" name="result"
                                   value="abnormal" <%=checked((extraData.get("result")),"abnormal")%> >Abnormal</input>
                            <br/>
                            <input type="radio" class="form-check-input" name="result"
                                   value="other" <%=checked((extraData.get("result")),"other")%> >Other</input> &nbsp;
                            &nbsp; Reason: <input type="text" class="form-control" name="reason"
                                                  value="<%=str((extraData.get("reason")),"")%>"/>
                        </fieldset>
                        <fieldset>
                            <legend>Comments</legend>
                            <textarea class="form-control" name="comments"><%=str((extraData.get("comments")), "")%></textarea>
                        </fieldset>
                    </div>
                    <script type="text/javascript">
                        hideExtraName(document.getElementById('providerDrop'));
                    </script>
                    <%} else if (layoutType.equals("history")) {%>
                    <div class="prevention">
                        <fieldset>
                            <legend>Prevention : <%=prevention%>
                            </legend>
                            <div style="float:left;">
                                <input name="given" type="radio" class="form-check-input"
                                       value="yes"      <%=checked(completed,"0")%>>Yes</input><br/>
                                <input name="given" type="radio" class="form-check-input"
                                       value="never"    <%=checked(completed,"1")%>>Never</input><br/>
                                <input name="given" type="radio" class="form-check-input"
                                       value="previous" <%=checked(completed,"2")%>>Previous</input>
                            </div>
                            <div style="float:left;margin-left:30px;">
                                <label for="prevDate" class="form-label fields">Date:</label> <input type="text" class="form-control" name="prevDate"
                                                                                          id="prevDate"
                                                                                          value="<%=prevDate%>"
                                                                                          size="15" required> <a
                                    id="date"><img title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
                                <br>
                                <div id="errorPrevDateMessage"
                                     class="alert alert-danger"></div>
                                <label for="provider" class="form-label fields">Provider:</label> <input type="hidden"
                                                                                              name="providerName"
                                                                                              id="providerName"
                                                                                              value="<%=providerName%>"/>
                                <select onchange="javascript:hideExtraName(this);" class="form-select" id="providerDrop" name="provider">
                                    <%
                                        for (int i = 0; i < providers.size(); i++) {
                                            Map<String, String> h = providers.get(i);
                                    %>
                                    <option value="<%= h.get("providerNo")%>" <%= (h.get("providerNo").equals(provider) ? " selected" : "") %>><%= h.get("lastName") %> <%= h.get("firstName") %>
                                    </option>
                                    <%}%>
                                    <option value="-1" <%= ("-1".equals(provider) ? " selected" : "") %> >Other</option>
                                </select>
                            </div>
                        </fieldset>
                        <fieldset>
                            <legend>Comments</legend>
                            <textarea class="form-control" name="comments"><%=str((extraData.get("comments")), "")%></textarea>
                        </fieldset>
                    </div>
                    <%} %>


                    <div class="prevention">
                        <fieldset>
                            <legend><a onclick="showHideNextDate('nextDateDiv','nextDate','neverWarn')"
                                       href="javascript: function myFunction() {return false; }">Set Next Date</a>
                            </legend>
                            <div id="nextDateDiv" style="display:none;">
                                <div>
                                    <label for="nextDate" class="form-label">Next Date:</label><input type="text" class="form-control" name="nextDate"
                                                                                   value="<%=nextDate%>" id="nextDate"
                                                                                   size="9"><a id="nextDateCal"><img
                                        title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
                                </div>
                                <div>
                                    <label for="neverWarn" class="form-label checkbox">Never Remind:</label><input type="checkbox" class="form-check-input"
                                                                                                        name="neverWarn"
                                                                                                        id="neverWarn"
                                                                                                        value="neverRemind"
                                                                                                        onchange="disableifchecked(this,'nextDate');"  <%=completed(never)%>/>
                                    Reason: <input type="text" class="form-control" name="neverReason"
                                                   value="<%=str((extraData.get("neverReason")),"")%>"/>
                                </div>
                            </div>
                        </fieldset>
                    </div>
                    <br/>
                    <input type="submit" class="btn btn-primary" value="Save" name="action">
                    <%
                        ConsentDao consentDao = SpringUtils.getBean(ConsentDao.class);
                        Consent ispaConsent = consentDao.findByDemographicAndConsentType(Integer.parseInt(demographic_no), "dhir_ispa_consent");
                        Consent nonIspaConsent = consentDao.findByDemographicAndConsentType(Integer.parseInt(demographic_no), "dhir_non_ispa_consent");

                        boolean ispa = Boolean.valueOf((String) prevHash.get("ispa"));

                        boolean isSSOLoggedIn = session.getAttribute("oneIdEmail") != null;
                        boolean hasIspaConsent = ispaConsent != null && !ispaConsent.isOptout();
                        boolean hasNonIspaConsent = nonIspaConsent != null && !nonIspaConsent.isOptout();

                        if (dhirEnabled && isSSOLoggedIn) {
                            if ((ispa && hasIspaConsent) || (!ispa && hasNonIspaConsent)) {
                    %>
                    <input type="submit" class="btn btn-primary" value="Save & Submit" name="action">
                    <% }
                    } %>
                    <% if (id != null) { %>
                    <input type="submit" class="btn btn-danger" name="delete" value="Delete"/>
                    <% } %>
                </form>
                <% } %>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn">
                &nbsp;
            </td>
            <td class="MainTableBottomRowRightColumn" valign="top">
                &nbsp;
            </td>
        </tr>
    </table>
    <%if (prevHash != null) { %>
    <script type="text/javascript">
        Calendar.setup({
            inputField: "prevDate",
            ifFormat: "%Y-%m-%d %H:%M",
            showsTime: true,
            button: "date",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "nextDate",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "nextDateCal",
            singleClick: true,
            step: 1
        });
    </script>
    <% } %>
    </body>
</html>
<%!

    String completed(boolean b) {
        String ret = "";
        if (b) {
            ret = "checked";
        }
        return ret;
    }

    String refused(boolean b) {
        String ret = "";
        if (!b) {
            ret = "checked";
        }
        return ret;
    }

    String str(String first, String second) {
        String ret = "";
        if (first != null) {
            ret = first;
        } else if (second != null) {
            ret = second;
        }
        return StringEscapeUtils.escapeHtml4(ret);
    }

    String checked(String first, String second) {
        String ret = "";
        if (first != null && second != null) {
            if (first.equals(second)) {
                ret = "checked";
            }
        }
        return ret;
    }

    Date parseDate(String dt) {
        SimpleDateFormat fmt = null;

        if (dt.length() == 4) {
            fmt = new SimpleDateFormat("yyyy");
        } else if (dt.length() == 7) {
            fmt = new SimpleDateFormat("yyyy-MM");
        } else if (dt.length() == 10) {
            fmt = new SimpleDateFormat("yyyy-MM-dd");
        } else if (dt.length() == 16) {
            fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        }

        if (fmt != null) {
            try {
                return fmt.parse(dt);
            } catch (ParseException e) {
                return null;
            }
        }
        return null;
    }
%>
