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
    AddPreventionData.jsp - Prevention / Immunization Data Entry Popup

    Popup form for adding or editing a patient's prevention or immunization record.
    Supports both creating a new prevention entry and editing an existing one (when
    an 'id' parameter is supplied). Also supports pre-population via lot number lookup
    through the Canadian Vaccine Catalogue (CVC).

    Features:
      - Add or edit immunization/prevention records linked to a patient demographic
      - Lot-number lookup via CVC to auto-populate vaccine brand, generic type, and SNOMED ID
      - Partial-date support for prevention dates (year or month/year precision)
      - DHIR (Digital Health Immunization Repository) integration for Ontario SSO sessions
      - Consent checking (ISPA and non-ISPA) before allowing DHIR submission
      - Case-management note linking via CaseManagementNoteLink
      - Unsaved-changes guard via beforeunload (cancelCloseWarning / setCloseWarning)
      - Logout signal handling: listens on BroadcastChannel 'carlos_logout' and the
        'carlos_logout_signal' localStorage key so the popup closes cleanly on logout
        without prompting the user about unsaved changes

    Request Parameters:
      - demographic_no  (String) patient demographic identifier
      - prevention      (String) prevention type name (e.g. "COVID19", "Influenza")
      - id              (String, optional) existing prevention record ID; triggers edit mode
      - snomedId        (String, optional) SNOMED concept ID for the vaccine
      - lotNumber       (String, optional) CVC lot number for auto-population
      - prevResultDesc  (String, optional) result description to pre-fill

    Security:
      - Requires '_prevention' read privilege; redirects to securityError.jsp if absent

    @since 2001 (OSCAR McMaster original), enhanced 2026-03-20 with logout signal handling
--%>

<%@ page import="java.text.ParseException" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.PartialDateDao" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
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

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

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

    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String demographic_no = request.getParameter("demographic_no");
    String snomedId = request.getParameter("snomedId");
    String id = request.getParameter("id");
    Map<String, Object> existingPrevention = null;

    String providerName = "";
    String lot = "";
    String provider = (String) session.getAttribute("user");
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

    if ("true".equals(CarlosProperties.getInstance().getProperty("dhir.enabled", "false"))) {
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
<fmt:setBundle basename="oscarResources"/>
<html>
    <head>
        <title>
            <fmt:message key="oscarprevention.index.oscarpreventiontitre"/>
        </title><!--I18n-->

        <%@ include file="/includes/global-head.jspf" %>

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
                                opt.value = item.lotNumber;
                                opt.setAttribute('expiryDate', output);
                                opt.text = item.lotNumber;

                                if (startup2 && item.lotNumber == '<%=Encode.forJavaScript(addByLotNbr != null ? addByLotNbr : "")%>') {
                                    opt.selected = true;
                                    startup2 = false;
                                } else if (startup && item.lotNumber == '<%=Encode.forJavaScript(existingPrevention != null && existingPrevention.get("lot") != null ? (String)existingPrevention.get("lot") : "")%>') {
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
                const prevDateInput = document.getElementById('prevDate');
                if (!prevDateInput.value) {
                    prevDateInput.setCustomValidity('Date is required');
                    return false;
                }
                prevDateInput.setCustomValidity('');
                return true;
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
                document.getElementById('cvcName').value = '<%=Encode.forJavaScript((String)existingPrevention.get("brandSnomedId"))%>';
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

    <body onload="disableifchecked(document.getElementById('neverWarn'),'nextDate');">

    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn" style="background-color:silver;">
                <h2><fmt:message key="oscarprevention.index.oscarpreventiontitre"/></h2>
            </td>
            <td class="MainTableTopRowRightColumn" style="width: 100%; background-color:silver;">
                <table class="TopStatusBar" style="width: 100%">
                    <tr>
                        <td style="background-color:silver;">
                            <%=StringEscapeUtils.escapeHtml4(nameage)%>
                        </td>
                        <td style="background-color:silver;">&nbsp;

                        </td>
                        <td style="text-align:right; background-color:silver;">
                            <a
                                href="javascript:popupStart(300,400,'About.jsp')"><fmt:message key="global.about"/></a>
                            | <a href="javascript:popupStart(300,400,'License.jsp')"><fmt:message key="global.license"/></a>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <div class="container-fluid px-2 py-1">
                <%

                    if (dhirEnabled && session.getAttribute("oneIdEmail") == null) {
                %>
                <div class="alert alert-danger">
                    <fmt:message key="oscarprevention.addpreventiondata.dhirWarning"/>
                </div>
                <% } %>

                <%
                    if (request.getAttribute("errors") != null) {
                        List<String> errorList = (List<String>) request.getAttribute("errors");
                %>
                <ul class="alert alert-danger"><%
                    for (String error : errorList) {
                %>
                    <li><%=Encode.forHtml(error) %>
                    </li>
                    <%
                        }
                    %></ul>
                <%
                    }

                %>

                <% if (prevHash == null) { %>
                <h3 class="alert alert-danger"><fmt:message key="oscarprevention.addpreventiondata.preventionNotFound"/></h3>
                <%} else { %>
                <form action="${pageContext.request.contextPath}/oscarPrevention/AddPrevention.do" method="post" onsubmit="return handleFormSubmission()">
                    <input type="hidden" name="prevention" value="<%=Encode.forHtmlAttribute(prevention != null ? prevention : "")%>"/>
                    <input type="hidden" name="demographic_no" value="<%=Encode.forHtmlAttribute(demographic_no != null ? demographic_no : "")%>"/>
                    <input type="hidden" name="providerNo" value="<%=Encode.forHtmlAttribute(provider != null ? provider : "")%>"/>
                    <%if (snomedId != null) {%>
                    <input type="hidden" name="snomedId" value="<%=Encode.forHtmlAttribute(snomedId != null ? snomedId : "")%>"/>
                    <%} %>
                    <% if (id != null) { %>
                    <input type="hidden" name="id" value="<%=Encode.forHtmlAttribute(id != null ? id : "")%>"/>
                    <input type="hidden" name="layoutType" value="<%=Encode.forHtmlAttribute(layoutType != null ? layoutType : "")%>"/>

                    <div class="prevention">
                        <fieldset>
                            <legend><fmt:message key="oscarprevention.addpreventiondata.summary"/></legend>
                            <textarea class="form-control form-control-sm" name="summary" readonly><%=Encode.forHtml(summary != null ? summary : "")%></textarea>
                            <%if (hasImportExtra) { %>
                            <a href="javascript:void(0);" title="Extra data from Import"
                               onclick="window.open('<%= request.getContextPath() %>/annotation/importExtra.jsp?display=<%=Encode.forJavaScriptAttribute(Encode.forUriComponent(annotation_display)) %>&amp;table_id=<%=Encode.forJavaScriptAttribute(Encode.forUriComponent(id != null ? id : "")) %>&amp;demo=<%=Encode.forJavaScriptAttribute(Encode.forUriComponent(demographic_no != null ? demographic_no : "")) %>','anwin','width=400,height=250');">
                                <img src="<%= request.getContextPath() %>/images/notes.gif" align="right" alt="Extra data from Import" height="16"
                                     width="13" border="0"> </a>
                            <%} %>
                        </fieldset>
                    </div>
                    <% } %>
                    <%if (layoutType.equals("injection")) {%>
                    <div class="prevention">
                    <div class="row g-2">
                    <div class="col-md-5">
                        <fieldset>
                            <legend><fmt:message key="oscarprevention.addpreventiondata.prevention"/> <%=Encode.forHtml(prevention != null ? prevention : "")%>
                            </legend>
                            <div>
                                <input name="given" type="radio" class="form-check-input" value="given"      <%=checked(completed,"0")%>
                                       onclick="document.getElementById('providerDrop').value='<%=LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo() %>';hideExtraName(document.getElementById('providerDrop'))"><fmt:message key="oscarprevention.addpreventiondata.completed"/></input>
                                <br/>
                                <input name="given" type="radio" class="form-check-input" value="given_ext"  <%=checked(completed,"3")%>
                                       onclick="document.getElementById('providerDrop').value='-1';hideExtraName(document.getElementById('providerDrop'))"><fmt:message key="oscarprevention.addpreventiondata.completedexternally"/></input><br/>
                                <input name="given" type="radio" class="form-check-input"
                                       value="refused"    <%=checked(completed,"1")%>><fmt:message key="oscarprevention.addpreventiondata.refused"/></input><br/>
                                <input name="given" type="radio" class="form-check-input" value="ineligible" <%=checked(completed,"2")%>><fmt:message key="oscarprevention.addpreventiondata.ineligible"/></input>
                            </div>
                            <div>&nbsp;</div>
                            <div>
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="prevDate" class="col-form-label col-form-label-sm fields"><fmt:message key="oscarprevention.addpreventiondata.date"/></label></div>
                                    <div class="col-sm-8"><input type="date" class="form-control form-control-sm" name="prevDate"
                                                                                          id="prevDate"
                                                                                          value="<%=Encode.forHtmlAttribute(prevDate != null && prevDate.length() >= 10 ? prevDate.substring(0, 10) : (prevDate != null ? prevDate : ""))%>"
                                                                                          required></div>
                                </div>
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="provider" class="col-form-label col-form-label-sm fields"><fmt:message key="oscarprevention.addpreventiondata.provider"/></label></div>
                                    <div class="col-sm-8"><input type="text" class="form-control form-control-sm"
                                                                                              name="providerName"
                                                                                              id="providerName"
                                                                                              value="<%=Encode.forHtmlAttribute(providerName != null ? providerName : "")%>"/>
                                <select onchange="javascript:hideExtraName(this);" class="form-select form-select-sm" id="providerDrop" name="provider">
                                    <%
                                        for (int i = 0; i < providers.size(); i++) {
                                            Map<String, String> h = providers.get(i);
                                    %>
                                    <option value="<%=Encode.forHtmlAttribute(h.get("providerNo"))%>" <%= (h.get("providerNo").equals(provider) ? " selected" : "") %>><%=Encode.forHtml(h.get("lastName") + " " + h.get("firstName"))%>
                                    </option>
                                    <%}%>
                                    <option value="-1" <%= ("-1".equals(provider) ? " selected" : "") %> ><fmt:message key="oscarprevention.addpreventiondata.other"/></option>
                                </select></div>
                                </div>
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="creator" class="col-form-label col-form-label-sm fields"><fmt:message key="oscarprevention.addpreventiondata.creator"/></label></div>
                                    <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="creator"
                                                                                            value="<%=Encode.forHtmlAttribute(creatorName != null ? creatorName : "")%>"
                                                                                            readonly/></div>
                                </div>
                            </div>
                        </fieldset>
                    </div><!-- end col-md-5 -->
                    <div class="col-md-7">
                        <fieldset>
                            <legend><fmt:message key="oscarprevention.addpreventiondata.resultat"/></legend>

                            <%
                                if (snomedId != null) {
                                    List<CVCImmunization> tnList = cvcManager.getImmunizationsByParent(snomedId);
                                    if (tnList != null && tnList.size() > 0) {
                            %>

                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="cvcName" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.tradename"/></label></div>
                                <div class="col-sm-8">
                            <select id="cvcName" name="cvcName" class="form-select form-select-sm" onChange="changeCVCName()">
                                <option value="-1"><fmt:message key="oscarprevention.addpreventiondata.selectbelow"/></option>
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
                                <option value="<%=Encode.forHtmlAttribute(tn.getSnomedConceptId())%>" <%=selected%>><%=Encode.forHtml(tn.getDisplayName())%>
                                </option>
                                <%
                                    }
                                %>

                            </select>
                                </div>
                            </div>

                            <span id="unknownName" style="display:block">
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="name" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.name"/></label></div>
                                <div class="col-sm-8"><input
                                    type="text" class="form-control form-control-sm" id="name" name="name"
                                    value="<%=Encode.forHtmlAttribute(!pBrand.isEmpty() ? pBrand : str((extraData.get("name")),""))%>"/></div>
                            </div></span>
                            <%

                            } else {
                            %>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="name" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.name"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" id="name" name="name"
                                                                      value="<%=Encode.forHtmlAttribute(!pBrand.isEmpty() ? pBrand : str((extraData.get("name")),""))%>"/></div>
                            </div>
                            <%
                            }

                        } else {
                        %>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="name" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.name"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" id="name" name="name"
                                                                  value="<%=Encode.forHtmlAttribute(!pBrand.isEmpty() ? pBrand : str((extraData.get("name")),""))%>"/></div>
                            </div>

                            <% } %>

                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="location" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.location"/></label></div>
                                <div class="col-sm-8">
                            <select name="location" id="location" class="form-select form-select-sm" onChange="changeSite(this)">
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
                                </div>
                            </div>

                            <%
                                String locationDisplay = "none";
                                if ("Other".equals(str(extraData.get("location"), ""))) {
                                    locationDisplay = "block";
                                }
                            %>
                            <div id="locationDiv" style="display:<%=locationDisplay%>">
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="location2" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.specifyLocation"/></label></div>
                                    <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="location2" id="location2"
                                       value="<%=str((extraData.get("location2")),"")%>"/></div>
                                </div>
                            </div>

                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="route" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.route"/></label></div>
                                <div class="col-sm-8">
                            <select name="route" id="route" class="form-select form-select-sm">
                                <option value=""></option>
                                <%
                                    String routeSelected = " selected=\"selected\" ";
                                %>
                                <option value="ID" <%="ID".equals(str((extraData.get("route")), pRoute)) ? routeSelected : "" %>>
                                    Intradermal: ID
                                </option>
                                <option value="IM" <%="IM".equals(str((extraData.get("route")), pRoute)) ? routeSelected : "" %>>
                                    Intramuscular: IM
                                </option>
                                <option value="IN" <%="IN".equals(str((extraData.get("route")), pRoute)) ? routeSelected : "" %>>
                                    Intranasal: IN
                                </option>
                                <option value="PO" <%="PO".equals(str((extraData.get("route")), pRoute)) ? routeSelected : "" %>>
                                    Oral: PO
                                </option>
                                <option value="SC" <%="SC".equals(str((extraData.get("route")), pRoute)) ? routeSelected : "" %>>
                                    Subcutaneous: SC
                                </option>
                            </select>
                                </div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="din" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.din"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="din" id="din" value="<%=Encode.forHtmlAttribute(str((extraData.get("din")), pDIN))%>"/></div>
                            </div>
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

                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="dose" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.dose"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="dose" id="dose" value="<%=Encode.forHtmlAttribute(d1)%>"/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="doseUnit" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.doseunit"/></label></div>
                                <div class="col-sm-8">
                            <select name="doseUnit" class="form-select form-select-sm">
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
                                </div>
                            </div>
                            <%if (!isCvc) { %>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="lot" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.lot"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="lot" id="lot"
                                                                 value="<%=str(lot,"")%>"/>
                            <select onchange="javascript:updateLotNr(this);" class="form-select form-select-sm" id="lotDrop" name="lotItem">
                                <%
                                    for (String lotnr : lotNrList) {
                                %>
                                <option value="<%=lotnr%>" <%= (lotnr.equals(lot) ? " selected" : "") %>><%=lotnr%>
                                </option>
                                <%}%>
                                <option value="-1"><fmt:message key="oscarprevention.addpreventiondata.other"/></option>
                            </select></div>
                            </div>
                            <%} else { %>
                            <div id="cvcLotDiv">
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="cvcLot" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.lot"/></label></div>
                                    <div class="col-sm-8">
                                <input type="text" class="form-control form-control-sm" name="lot" id="lot" value="<%=str(lot,"")%>" style="display:block"/>

                                <select onchange="javascript:updateCvcLot();" class="form-select form-select-sm" id="cvcLot" name="cvcLot"
                                        style="display:none;">

                                </select>
                                    </div>
                                </div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="expiryDate" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.expirydate"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="expiryDate"
                                                                                id="expiryDate"
                                                                                value="<%=str((extraData.get("expiryDate")),"")%>"/></div>
                            </div>
                            <% } %>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="manufacture" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.manufacture"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="manufacture"
                                                                                 id="manufacture"
                                                                                 value="<%=Encode.forHtmlAttribute(str((extraData.get("manufacture")), pMaker))%>"/></div>
                            </div>
                        </fieldset>
                    </div><!-- end col-md-7 -->
                    </div><!-- end row g-2 -->
                    <div class="row g-2 mt-1">
                    <div class="col-12">
                        <fieldset>
                            <legend><fmt:message key="oscarprevention.addpreventiondata.comments"/></legend>
                            <textarea class="form-control form-control-sm" name="comments"><%=str((extraData.get("comments")), "")%></textarea>
                        </fieldset>
                    </div><!-- end col-12 -->
                    </div><!-- end row g-2 mt-1 -->
                    </div><!-- end prevention div -->
                    <script type="text/javascript">
                        hideExtraName(document.getElementById('providerDrop'));
                    </script>
                    <script type="text/javascript">
                        hideLotDrop(document.getElementById('lotDrop'));
                    </script>
                    <%} else if (layoutType.equals("h1n1")) {%>
                    <div class="prevention">
                    <div class="row g-2">
                    <div class="col-md-5">
                        <fieldset>
                            <legend><fmt:message key="oscarprevention.addpreventiondata.prevention"/> <%=Encode.forHtml(prevention != null ? prevention : "")%>
                            </legend>
                            <div>
                                <input name="given" type="radio" class="form-check-input" value="given"      <%=checked(completed,"0")%>
                                       onclick="document.getElementById('providerDrop').value='<%=LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo() %>';hideExtraName(document.getElementById('providerDrop'))"><fmt:message key="oscarprevention.addpreventiondata.completed"/></input>
                                <br/>
                                <input name="given" type="radio" class="form-check-input" value="given_ext"  <%=checked(completed,"3")%>
                                       onclick="document.getElementById('providerDrop').value='-1';hideExtraName(document.getElementById('providerDrop'))"><fmt:message key="oscarprevention.addpreventiondata.completedexternally"/></input><br/>
                                <input name="given" type="radio" class="form-check-input"
                                       value="refused"    <%=checked(completed,"1")%>><fmt:message key="oscarprevention.addpreventiondata.refused"/></input><br/>
                                <input name="given" type="radio" class="form-check-input" value="ineligible" <%=checked(completed,"2")%>><fmt:message key="oscarprevention.addpreventiondata.ineligible"/></input>
                            </div>
                            <div>&nbsp;</div>
                            <div>
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="prevDate" class="col-form-label col-form-label-sm fields"><fmt:message key="oscarprevention.addpreventiondata.date"/></label></div>
                                    <div class="col-sm-8"><input type="date" class="form-control form-control-sm" name="prevDate"
                                                                                          id="prevDate"
                                                                                          value="<%=Encode.forHtmlAttribute(prevDate != null && prevDate.length() >= 10 ? prevDate.substring(0, 10) : (prevDate != null ? prevDate : ""))%>"
                                                                                          required></div>
                                </div>
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="provider" class="col-form-label col-form-label-sm fields"><fmt:message key="oscarprevention.addpreventiondata.provider"/></label></div>
                                    <div class="col-sm-8"><input type="text" class="form-control form-control-sm"
                                                                                              name="providerName"
                                                                                              id="providerName"
                                                                                              value="<%=Encode.forHtmlAttribute(providerName != null ? providerName : "")%>"/>
                                <select onchange="javascript:hideExtraName(this);" class="form-select form-select-sm" id="providerDrop" name="provider">
                                    <%
                                        for (int i = 0; i < providers.size(); i++) {
                                            Map<String, String> h = providers.get(i);
                                    %>
                                    <option value="<%=Encode.forHtmlAttribute(h.get("providerNo"))%>" <%= (h.get("providerNo").equals(provider) ? " selected" : "") %>><%=Encode.forHtml(h.get("lastName") + " " + h.get("firstName"))%>
                                    </option>
                                    <%}%>
                                    <option value="-1" <%= ("-1".equals(provider) ? " selected" : "") %> ><fmt:message key="oscarprevention.addpreventiondata.other"/></option>
                                </select></div>
                                </div>
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="creator" class="col-form-label col-form-label-sm fields"><fmt:message key="oscarprevention.addpreventiondata.creator"/></label></div>
                                    <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="creator"
                                                                                            value="<%=Encode.forHtmlAttribute(creatorName != null ? creatorName : "")%>"
                                                                                            readonly/></div>
                                </div>
                            </div>
                        </fieldset>
                    </div><!-- end col-md-5 -->
                    <div class="col-md-7">
                        <fieldset>
                            <legend><fmt:message key="oscarprevention.addpreventiondata.resultat"/></legend>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="location" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.location"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="location"
                                                                           value="<%=str((extraData.get("location")),"")%>"/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="location2" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.otherlocation"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="location2"
                                                                                 value="<%=str((extraData.get("location2")),"")%>"/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="route" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.route"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="route"
                                                                     value="<%=str((extraData.get("route")),"")%>"/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="dose" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.dose"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="dose"
                                                                   value="<%=str((extraData.get("dose")),"")%>"/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="dose1" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.dose1"/></label></div>
                                <div class="col-sm-8"><input type="checkbox" class="form-check-input" name="dose1"
                                                                      value="true" <%=checked(str((extraData.get("dose1")), ""), "true")%>/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="dose2" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.dose2"/></label></div>
                                <div class="col-sm-8"><input type="checkbox" class="form-check-input" name="dose2"
                                                                      value="true" <%=checked(str((extraData.get("dose2")), ""), "true")%>/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="lot" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.lot"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="lot"
                                                                 value="<%=str((extraData.get("lot")),"")%>"/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="manufacture" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.manufacture"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="manufacture"
                                                                                 value="<%=str((extraData.get("manufacture")),"")%>"/></div>
                            </div>
                        </fieldset>
                        <fieldset>
                            <legend><fmt:message key="oscarprevention.addpreventiondata.info"/></legend>
                            <% String gender = genders.get(demoInfo[2]);
                                if (gender == null) {
                                    gender = genders.get("U");
                                }

                            %>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="gender" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.gender"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="gender" readonly
                                                                       value="<%=gender%>"/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="age" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.age"/></label></div>
                                <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="age" readonly value="<%=age%>"/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="chronic" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.chronic"/></label></div>
                                <div class="col-sm-8">
                            <select name="chronic" class="form-select form-select-sm">
                                <option value="false">No</option>
                                <option value="true" <%= str((extraData.get("chronic")), "").equalsIgnoreCase("true") ? "selected" : "" %> >
                                    <fmt:message key="oscarprevention.addpreventiondata.yes"/>
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
                            </select>
                                </div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="pregnant" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.pregnant"/></label></div>
                                <div class="col-sm-8"><input type="checkbox" class="form-check-input" name="pregnant"
                                                                           value="true" <%=checked(str((extraData.get("pregnant")), ""), "true")%>/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="remote" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.remotesetting"/></label></div>
                                <div class="col-sm-8"><input type="checkbox" class="form-check-input" name="remote"
                                                                               value="true" <%=checked(str((extraData.get("remote")), ""), "true")%>/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="healthcareworker" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.healthcareworker"/></label></div>
                                <div class="col-sm-8">
                            <select name="healthcareworker" class="form-select form-select-sm">
                                <option value="false">No</option>
                                <option value="true" <%= str((extraData.get("healthcareworker")), "").equalsIgnoreCase("true") ? "selected" : "" %> >
                                    <fmt:message key="oscarprevention.addpreventiondata.yes"/>
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
                            </select>
                                </div>
                            </div>

                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="householdcontact" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.householdcontact"/></label></div>
                                <div class="col-sm-8"><input
                                type="checkbox" class="form-check-input" name="householdcontact"
                                value="true" <%=checked(str((extraData.get("householdcontact")), ""), "true")%>/></div>
                            </div>
                            <%
                                boolean bothfirstresponders = false;
                                if (str((extraData.get("firstresponder")), "").equalsIgnoreCase("true")) {
                                    bothfirstresponders = true;
                                }

                            %>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="firstresponderpolice" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.firstresponderpolice"/></label></div>
                                <div class="col-sm-8"><input type="checkbox" class="form-check-input"
                                                                                                     name="firstresponderpolice"
                                                                                                     value="true" <%=bothfirstresponders == true ? "checked" : checked(str((extraData.get("firstresponderpolice")), ""), "true")%>/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="firstresponderfire" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.firstresponderfire"/></label></div>
                                <div class="col-sm-8"><input type="checkbox" class="form-check-input"
                                                                                                 name="firstresponderfire"
                                                                                                 value="true" <%=bothfirstresponders == true ? "checked" : checked(str((extraData.get("firstresponderfire")), ""), "true")%>/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="swineworker" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.swineworker"/></label></div>
                                <div class="col-sm-8"><input type="checkbox" class="form-check-input" name="swineworker"
                                                                                  value="true" <%=checked(str((extraData.get("swineworker")), ""), "true")%>/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="poultryworker" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.poultryworker"/></label></div>
                                <div class="col-sm-8"><input type="checkbox" class="form-check-input"
                                                                                      name="poultryworker"
                                                                                      value="true" <%=checked(str((extraData.get("poultryworker")), ""), "true")%>/></div>
                            </div>
                            <div class="row g-2 align-items-center mb-1">
                                <div class="col-sm-4"><label for="firstnations" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.firstnations"/></label></div>
                                <div class="col-sm-8"><input type="checkbox" class="form-check-input" name="firstnations"
                                                                                    value="true" <%=checked(str((extraData.get("firstnations")), ""), "true")%>/></div>
                            </div>
                        </fieldset>
                    </div><!-- end col-md-7 -->
                    </div><!-- end row g-2 -->
                    <div class="row g-2 mt-1">
                    <div class="col-12">
                        <fieldset>
                            <legend><fmt:message key="oscarprevention.addpreventiondata.comments"/></legend>
                            <textarea class="form-control form-control-sm" name="comments"><%=str((extraData.get("comments")), "")%></textarea>
                        </fieldset>
                    </div><!-- end col-12 -->
                    </div><!-- end row g-2 mt-1 -->
                    </div><!-- end prevention div -->
                    <script type="text/javascript">
                        hideExtraName(document.getElementById('providerDrop'));
                    </script>
                    <%} else if (layoutType.equals("PAPMAM")) {/*next layout type*/%>
                    <div class="prevention">
                    <div class="row g-2">
                    <div class="col-md-5">
                        <fieldset>
                            <legend><fmt:message key="oscarprevention.addpreventiondata.prevention"/> <%=Encode.forHtml(prevention != null ? prevention : "")%>
                            </legend>
                            <div>
                                <input name="given" type="radio" class="form-check-input" value="given"      <%=checked(completed,"0")%>
                                       onclick="document.getElementById('providerDrop').value='<%=LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo() %>';hideExtraName(document.getElementById('providerDrop'))"><fmt:message key="oscarprevention.addpreventiondata.completed"/></input>
                                <br/>
                                <input name="given" type="radio" class="form-check-input" value="given_ext"  <%=checked(completed,"3")%>
                                       onclick="document.getElementById('providerDrop').value='-1';hideExtraName(document.getElementById('providerDrop'))"><fmt:message key="oscarprevention.addpreventiondata.completedexternally"/></input><br/>
                                <input name="given" type="radio" class="form-check-input"
                                       value="refused"    <%=checked(completed,"1")%>><fmt:message key="oscarprevention.addpreventiondata.refused"/></input><br/>
                                <input name="given" type="radio" class="form-check-input" value="ineligible" <%=checked(completed,"2")%>><fmt:message key="oscarprevention.addpreventiondata.ineligible"/></input>
                            </div>
                            <div>&nbsp;</div>
                            <div>
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="prevDate" class="col-form-label col-form-label-sm fields"><fmt:message key="oscarprevention.addpreventiondata.date"/></label></div>
                                    <div class="col-sm-8"><input type="date" class="form-control form-control-sm" name="prevDate"
                                                                                                          id="prevDate"
                                                                                                          value="<%=Encode.forHtmlAttribute(prevDate != null && prevDate.length() >= 10 ? prevDate.substring(0, 10) : (prevDate != null ? prevDate : ""))%>"
                                                                                                          required></div>
                                </div>
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="provider" class="col-form-label col-form-label-sm fields"><fmt:message key="oscarprevention.addpreventiondata.provider"/></label></div>
                                    <div class="col-sm-8"><input type="text" class="form-control form-control-sm"
                                                                                                              name="providerName"
                                                                                                              id="providerName"
                                                                                                              value="<%=Encode.forHtmlAttribute(providerName != null ? providerName : "")%>"/>
                                <select onchange="javascript:hideExtraName(this);" class="form-select form-select-sm" id="providerDrop" name="provider">
                                    <%
                                        for (int i = 0; i < providers.size(); i++) {
                                            Map<String, String> h = providers.get(i);
                                    %>
                                    <option value="<%=Encode.forHtmlAttribute(h.get("providerNo"))%>" <%= (h.get("providerNo").equals(provider) ? " selected" : "") %>><%=Encode.forHtml(h.get("lastName") + " " + h.get("firstName"))%>
                                    </option>
                                    <%}%>
                                    <option value="-1" <%= ("-1".equals(provider) ? " selected" : "") %> ><fmt:message key="oscarprevention.addpreventiondata.other"/></option>
                                </select></div>
                                </div>
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="creator" class="col-form-label col-form-label-sm fields"><fmt:message key="oscarprevention.addpreventiondata.creator"/></label></div>
                                    <div class="col-sm-8"><input type="text" class="form-control form-control-sm" name="creator"
                                                                                            value="<%=Encode.forHtmlAttribute(creatorName != null ? creatorName : "")%>"
                                                                                            readonly/></div>
                                </div>
                            </div>
                        </fieldset>
                    </div><!-- end col-md-5 -->
                    <div class="col-md-7">
                        <fieldset>
                            <legend><fmt:message key="oscarprevention.addpreventiondata.resultat"/></legend>
                            <% if (extraData.get("result") == null) {
                                extraData.put("result", "pending");
                            } %>
                            <%=str(prevResultDesc, "")%><br/>
                            <input type="radio" class="form-check-input" name="result"
                                   value="pending" <%=checked( (extraData.get("result")) ,"pending")%> ><fmt:message key="oscarprevention.addpreventiondata.pending"/></input>
                            <br/>
                            <input type="radio" class="form-check-input" name="result"
                                   value="normal"  <%=checked((extraData.get("result")),"normal")%> ><fmt:message key="oscarprevention.addpreventiondata.normal"/></input><br/>
                            <input type="radio" class="form-check-input" name="result"
                                   value="abnormal" <%=checked((extraData.get("result")),"abnormal")%> ><fmt:message key="oscarprevention.addpreventiondata.abnormal"/></input>
                            <br/>
                            <input type="radio" class="form-check-input" name="result"
                                   value="other" <%=checked((extraData.get("result")),"other")%> ><fmt:message key="oscarprevention.addpreventiondata.other"/></input> &nbsp;
                            &nbsp; <fmt:message key="oscarprevention.addpreventiondata.reason"/> <input type="text" class="form-control form-control-sm" name="reason"
                                                  value="<%=str((extraData.get("reason")),"")%>"/>
                        </fieldset>
                    </div><!-- end col-md-7 -->
                    </div><!-- end row g-2 -->
                    <div class="row g-2 mt-1">
                    <div class="col-12">
                        <fieldset>
                            <legend><fmt:message key="oscarprevention.addpreventiondata.comments"/></legend>
                            <textarea class="form-control form-control-sm" name="comments"><%=str((extraData.get("comments")), "")%></textarea>
                        </fieldset>
                    </div><!-- end col-12 -->
                    </div><!-- end row g-2 mt-1 -->
                    </div><!-- end prevention div -->
                    <script type="text/javascript">
                        hideExtraName(document.getElementById('providerDrop'));
                    </script>
                    <%} else if (layoutType.equals("history")) {%>
                    <div class="prevention">
                    <div class="row g-2">
                    <div class="col-md-5">
                        <fieldset>
                            <legend><fmt:message key="oscarprevention.addpreventiondata.prevention"/> <%=Encode.forHtml(prevention != null ? prevention : "")%>
                            </legend>
                            <div>
                                <input name="given" type="radio" class="form-check-input"
                                       value="yes"      <%=checked(completed,"0")%>><fmt:message key="oscarprevention.addpreventiondata.yes"/></input><br/>
                                <input name="given" type="radio" class="form-check-input"
                                       value="never"    <%=checked(completed,"1")%>><fmt:message key="oscarprevention.addpreventiondata.history.never"/></input><br/>
                                <input name="given" type="radio" class="form-check-input"
                                       value="previous" <%=checked(completed,"2")%>><fmt:message key="oscarprevention.addpreventiondata.previous"/></input>
                            </div>
                            <div>
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="prevDate" class="col-form-label col-form-label-sm fields"><fmt:message key="oscarprevention.addpreventiondata.date"/></label></div>
                                    <div class="col-sm-8"><input type="date" class="form-control form-control-sm" name="prevDate"
                                                                                          id="prevDate"
                                                                                          value="<%=Encode.forHtmlAttribute(prevDate != null && prevDate.length() >= 10 ? prevDate.substring(0, 10) : (prevDate != null ? prevDate : ""))%>"
                                                                                          required></div>
                                </div>
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="provider" class="col-form-label col-form-label-sm fields"><fmt:message key="oscarprevention.addpreventiondata.provider"/></label></div>
                                    <div class="col-sm-8"><input type="hidden"
                                                                                              name="providerName"
                                                                                              id="providerName"
                                                                                              value="<%=Encode.forHtmlAttribute(providerName != null ? providerName : "")%>"/>
                                <select onchange="javascript:hideExtraName(this);" class="form-select form-select-sm" id="providerDrop" name="provider">
                                    <%
                                        for (int i = 0; i < providers.size(); i++) {
                                            Map<String, String> h = providers.get(i);
                                    %>
                                    <option value="<%=Encode.forHtmlAttribute(h.get("providerNo"))%>" <%= (h.get("providerNo").equals(provider) ? " selected" : "") %>><%=Encode.forHtml(h.get("lastName") + " " + h.get("firstName"))%>
                                    </option>
                                    <%}%>
                                    <option value="-1" <%= ("-1".equals(provider) ? " selected" : "") %> ><fmt:message key="oscarprevention.addpreventiondata.other"/></option>
                                </select></div>
                                </div>
                            </div>
                        </fieldset>
                    </div><!-- end col-md-5 -->
                    <div class="col-md-7">
                        <fieldset>
                            <legend><fmt:message key="oscarprevention.addpreventiondata.comments"/></legend>
                            <textarea class="form-control form-control-sm" name="comments"><%=str((extraData.get("comments")), "")%></textarea>
                        </fieldset>
                    </div><!-- end col-md-7 -->
                    </div><!-- end row g-2 -->
                    </div><!-- end prevention div -->
                    <%} %>


                    <div class="row g-2 mt-1">
                    <div class="col-12">
                    <div class="prevention">
                        <fieldset>
                            <legend><a class="btn-link" onclick="showHideNextDate('nextDateDiv','nextDate','neverWarn')"
                                       href="javascript: function myFunction() {return false; }"><fmt:message key="oscarprevention.addpreventiondata.setnextdate"/></a>
                            </legend>
                            <div id="nextDateDiv" style="display:none;">
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="nextDate" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.nextdate"/></label></div>
                                    <div class="col-sm-8"><input type="date" class="form-control form-control-sm" name="nextDate"
                                                                                   value="<%=nextDate%>" id="nextDate"></div>
                                </div>
                                <div class="row g-2 align-items-center mb-1">
                                    <div class="col-sm-4"><label for="neverWarn" class="col-form-label col-form-label-sm"><fmt:message key="oscarprevention.addpreventiondata.neverremind"/></label></div>
                                    <div class="col-sm-8"><input type="checkbox" class="form-check-input"
                                                                                                        name="neverWarn"
                                                                                                        id="neverWarn"
                                                                                                        value="neverRemind"
                                                                                                        onchange="disableifchecked(this,'nextDate');"  <%=completed(never)%>/>
                                    <fmt:message key="oscarprevention.addpreventiondata.reason"/> <input type="text" class="form-control form-control-sm" name="neverReason"
                                                   value="<%=str((extraData.get("neverReason")),"")%>"/></div>
                                </div>
                            </div>
                        </fieldset>
                    </div>
                    </div><!-- end col-12 -->
                    </div><!-- end row g-2 mt-1 -->
                    <div class="row g-2 mt-1">
                    <div class="col-12">
                    <fmt:message key="oscarprevention.addpreventiondata.btnsave" var="msgBtnSave"/>
                    <fmt:message key="oscarprevention.addpreventiondata.saveandsubmit" var="msgBtnSaveSubmit"/>
                    <fmt:message key="oscarprevention.addpreventiondata.delete" var="msgBtnDelete"/>
                    <input type="submit" class="btn btn-primary btn-sm" value="${msgBtnSave}" name="action">
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
                    <input type="submit" class="btn btn-primary btn-sm" value="${msgBtnSaveSubmit}" name="action">
                    <% }
                    } %>
                    <% if (id != null) { %>
                    <input type="submit" class="btn btn-danger btn-sm" name="delete" value="${msgBtnDelete}"/>
                    <% } %>
                    </div><!-- end col-12 -->
                    </div><!-- end row g-2 mt-1 -->
                </form>
                <% } %>
    </div>
<script>
/* Suppress the leave-page confirmation dialog when a logout broadcast signal is received.
 * logout.jsp broadcasts 'logout' on the 'carlos_logout' BroadcastChannel and sets
 * the 'carlos_logout_signal' localStorage key. Either signal releases the dirty flag
 * so the beforeunload handler does not prompt the user, then closes this popup window. */
(function() {
    function handleLogoutSignal() {
        cancelCloseWarning();
        try { window.close(); } catch(e) {}
    }
    try {
        var logoutChannel = new BroadcastChannel('carlos_logout');
        logoutChannel.onmessage = function(e) {
            if (e.data === 'logout') { handleLogoutSignal(); }
        };
    } catch(e) {}
    window.addEventListener('storage', function(e) {
        if (e.key === 'carlos_logout_signal' && e.newValue !== null) { handleLogoutSignal(); }
    });
}());
</script>
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
