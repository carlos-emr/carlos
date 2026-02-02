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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_con");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.utility.WebUtils" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="/WEB-INF/special_tag.tld" prefix="special" %>
<!-- end -->
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>


<%@page import="java.util.ArrayList, java.util.List, java.util.*, ca.openosp.OscarProperties, ca.openosp.openo.lab.ca.on.*" %>
<%@page import="ca.openosp.openo.casemgmt.service.CaseManagementManager,ca.openosp.openo.casemgmt.model.CaseManagementNote,ca.openosp.openo.casemgmt.model.Issue,ca.openosp.openo.commn.dao.UserPropertyDAO,org.springframework.web.context.support.*,org.springframework.web.context.*" %>

<%@page import="ca.openosp.openo.commn.dao.SiteDao" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="ca.openosp.openo.utility.WebUtils" %>
<%@page import="ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequest2Form" %>
<%@page import="ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequestUtil" %>
<%@page import="ca.openosp.openo.demographic.data.DemographicData" %>
<%@page import="ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctViewRequest2Action" %>
<%@page import="ca.openosp.openo.utility.MiscUtils,ca.openosp.openo.clinic.ClinicData" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.utility.DigitalSignatureUtils" %>
<%@ page import="ca.openosp.openo.ui.servlet.ImageRenderingServlet" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.utility.MiscUtils" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProgramDao, ca.openosp.openo.PMmodule.model.Program" %>
<%@page import="ca.openosp.openo.demographic.data.DemographicData, ca.openosp.openo.prescript.data.RxProviderData, ca.openosp.openo.prescript.data.RxProviderData.Provider, ca.openosp.openo.clinic.ClinicData" %>
<%@ page import="ca.openosp.openo.commn.dao.FaxConfigDao" %>
<%@page import="ca.openosp.openo.commn.dao.ConsultationServiceDao" %>
<%@ page import="ca.openosp.openo.managers.DemographicManager" %>
<%@page import="ca.openosp.openo.commn.dao.ContactSpecialtyDao" %>
<%@page import="ca.openosp.openo.commn.dao.DemographicContactDao" %>
<%@ page import="ca.openosp.openo.commn.model.enumerator.ConsultationRequestExtKey" %>
<%@ page import="ca.openosp.openo.commn.dao.ConsultationRequestExtDao" %>
<%@ page import="ca.openosp.openo.managers.ConsultationManager" %>
<%@ page import="ca.openosp.openo.encounter.data.EctFormData" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.eform.EFormUtil" %>
<%@ page import="ca.openosp.openo.lab.ca.all.Hl7textResultsData" %>
<%@ page import="ca.openosp.openo.documentManager.EDocUtil" %>
<%@ page import="ca.openosp.openo.documentManager.EDoc" %>
<%@ page import="ca.openosp.openo.util.StringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.enumerator.ModuleType" %>
<%@ page import="ca.openosp.openo.demographic.data.EctInformation" %>
<%@ page import="ca.openosp.openo.demographic.data.RxInformation" %>
<%@ page import="ca.openosp.openo.lab.ca.on.CommonLabResultData" %>
<%@ page import="ca.openosp.openo.lab.ca.on.LabResultData" %>
<%@ page import="ca.openosp.openo.managers.LookupListManager" %>
<%@ page import="ca.openosp.openo.commn.model.*" %>
<%@ page import="ca.openosp.openo.commn.IsPropertiesOn" %>


<jsp:useBean id="displayServiceUtil" scope="request"
             class="ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConDisplayServiceUtil"/>
<!DOCTYPE html>
<html>

    <%! boolean bMultisites = IsPropertiesOn.isMultisitesEnable(); %>

    <%
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
        displayServiceUtil.estSpecialist();

        //multi-site support
        String appNo = request.getParameter("appNo");
        appNo = (appNo == null ? "" : appNo);

        String defaultSiteName = "";
        Integer defaultSiteId = 0;
        Vector<String> vecAddressName = new Vector<String>();
        Vector<String> bgColor = new Vector<String>();
        Vector<Integer> siteIds = new Vector<Integer>();
        if (bMultisites) {
            SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);

            List<Site> sites = siteDao.getActiveSitesByProviderNo((String) session.getAttribute("user"));
            if (sites != null) {
                for (Site s : sites) {
                    siteIds.add(s.getSiteId());
                    vecAddressName.add(s.getName());
                    bgColor.add(s.getBgColor());
                }
            }

            if (appNo != "") {
                defaultSiteName = siteDao.getSiteNameByAppointmentNo(appNo);
            }
        }
        String demo_mrp = null;
        String demo = StringUtils.isNullOrEmpty(request.getParameter("de")) ? ((String) request.getAttribute("demographicId")) : request.getParameter("de");
        String requestId = StringUtils.isNullOrEmpty(request.getParameter("requestId")) ? ((String) request.getAttribute("reqId")) : request.getParameter("requestId");
        // segmentId is != null when viewing a remote consultation request from an hl7 source
        String segmentId = request.getParameter("segmentId");
        String team = request.getParameter("teamVar");
        String providerNo = (String) session.getAttribute("user");
        String providerNoFromChart = null;
        DemographicData demoData = null;
        Demographic demographic = null;

        RxProviderData rx = new RxProviderData();
        List<Provider> prList = rx.getAllProviders();
        Provider thisProvider = rx.getProvider(providerNo);
        ClinicData clinic = new ClinicData();

        EctConsultationFormRequestUtil consultUtil = new EctConsultationFormRequestUtil();

        if (requestId != null) {
            consultUtil.estRequestFromId(loggedInInfo, requestId);
        }

        if (demo == null) {
            demo = consultUtil.demoNo;
        }

        // Check if the selected providers is currently active. If it is not active, add it to the prList, as the list only contains active providers.
        Boolean isProviderActive = false;
        for (Provider activeProvider : prList) {
            if (consultUtil.providerNo != null && consultUtil.providerNo.equalsIgnoreCase(activeProvider.getProviderNo())) {
                isProviderActive = true;
                break;
            }
        }

        if (!isProviderActive && consultUtil.providerNo != null) {
            Provider inactiveProvider = rx.getProvider(consultUtil.providerNo);
            if (inactiveProvider != null) {
                prList.add(inactiveProvider);
            }
        }

        UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
        if (demo != null) {
            demoData = new DemographicData();
            demographic = demoData.getDemographic(loggedInInfo, demo);
            providerNoFromChart = demographic.getProviderNo();
            demo_mrp = demographic.getProviderNo();

            if (demo_mrp == null || demo_mrp.isEmpty()) {
                DemographicContact demographicContact = demographicManager.getMostResponsibleProviderFromHealthCareTeam(loggedInInfo, Integer.parseInt(demo));

                if (demographicContact != null) {
                    demo_mrp = demographicContact.getContactId();
                }
            }

            consultUtil.estPatient(loggedInInfo, demo);
            consultUtil.estActiveTeams();
        } else if (requestId == null && segmentId == null) {
            MiscUtils.getLogger().debug("Missing both requestId and segmentId.");
        }

        if (request.getParameter("error") != null) {
            String errorMessage = (String) request.getAttribute("errorMessage");
            if (StringUtils.isNullOrEmpty(errorMessage)) {
                errorMessage = "The form could not be printed due to an error. Please refer to the server logs for more details.";
            }
    %>
    <SCRIPT LANGUAGE="JavaScript">
        alert('<%= errorMessage %>');
    </SCRIPT>
    <%
        }

        java.util.Calendar calender = java.util.Calendar.getInstance();
        String day = Integer.toString(calender.get(java.util.Calendar.DAY_OF_MONTH));
        String mon = Integer.toString(calender.get(java.util.Calendar.MONTH) + 1);
        String year = Integer.toString(calender.get(java.util.Calendar.YEAR));
        String formattedDate = year + "/" + mon + "/" + day;

        OscarProperties props = OscarProperties.getInstance();
        ConsultationServiceDao consultationServiceDao = SpringUtils.getBean(ConsultationServiceDao.class);
    %>
    <%--
			// Get attached documents and labs
		 --%>
    <%
        if (requestId != null && Integer.parseInt(requestId) > 0) {
            List<EDoc> attachedDocuments = EDocUtil.listDocs(loggedInInfo, demo, requestId, EDocUtil.ATTACHED);
            CommonLabResultData commonLabResultData = new CommonLabResultData();
            List<LabResultData> attachedLabs = commonLabResultData.populateLabResultsData(loggedInInfo, demo, requestId, CommonLabResultData.ATTACHED);
            ConsultationManager consultationManager = SpringUtils.getBean(ConsultationManager.class);
            List<EctFormData.PatientForm> attachedForms = consultationManager.getAttachedForms(loggedInInfo, Integer.parseInt(requestId), Integer.parseInt(demo));
            List<EFormData> attachedEForms = consultationManager.getAttachedEForms(requestId);
            ArrayList<HashMap<String, ? extends Object>> attachedHRMDocuments = consultationManager.getAttachedHRMDocuments(loggedInInfo, demo, requestId);

            Collections.sort(attachedLabs);
            List<LabResultData> attachedLabsSortedByVersions = new ArrayList<>();
            for (LabResultData attachedLab1 : attachedLabs) {
                if (attachedLabsSortedByVersions.contains(attachedLab1)) {
                    continue;
                }
                String[] matchingLabIds = Hl7textResultsData.getMatchingLabs(attachedLab1.getSegmentID()).split(",");
                if (matchingLabIds.length == 1) {
                    attachedLabsSortedByVersions.add(attachedLab1);
                    continue;
                }
                for (int i = matchingLabIds.length - 1; i >= 0; i--) {
                    for (LabResultData attachedLab2 : attachedLabs) {
                        if (!attachedLab2.getSegmentID().equals(matchingLabIds[i])) {
                            continue;
                        }
                        if (i != matchingLabIds.length - 1) {
                            attachedLab2.setDescription("v" + (i + 1));
                        }
                        attachedLabsSortedByVersions.add(attachedLab2);
                        break;
                    }
                }
            }

            pageContext.setAttribute("attachedDocuments", attachedDocuments);
            pageContext.setAttribute("attachedLabs", attachedLabsSortedByVersions);
            pageContext.setAttribute("attachedForms", attachedForms);
            pageContext.setAttribute("attachedEForms", attachedEForms);
            pageContext.setAttribute("attachedHRMDocuments", attachedHRMDocuments);

        }
    %>
    <%--
			// Look up list for appointment instructions.
		 --%>
    <%

        LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);
        pageContext.setAttribute("appointmentInstructionList", lookupListManager.findLookupListByName(loggedInInfo, "consultApptInst"));

    %>
    <%--
	// enable option to populate the patients Health Care Team into the Specialist/Service fields.
	// The Health Care Team module will be available to add additional contacts to the patient demographic
 --%>
    <%

        // A null demo varialbe means that this iteration is a postback. This script need not be run on postback.
        if (demo != null && "true".equals(props.getProperty("ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS"))) {

            ContactSpecialtyDao contactSpecialtyDao = SpringUtils.getBean(ContactSpecialtyDao.class);
            List<DemographicContact> demographicContacts = demographicManager.getHealthCareTeam(loggedInInfo, Integer.parseInt(demo));
            HashSet<ConsultationServices> consultationServices = new HashSet<ConsultationServices>();
            List<DemographicContact> healthCareTeam = new ArrayList<DemographicContact>();
            DemographicContactDao demographicContactDao = SpringUtils.getBean(DemographicContactDao.class);

            // incoming professionalSpecialists can be added to the patient health care team.
            // The id for these stray professionalSpecialists are identified as less than 0
            // - only if the Health Care Team module is enabled.
            String currentSpecialistId = consultUtil.getSpecialist();
            Integer currentSpecialistIdInt = 0;
            String currentDemographicContact = null;

            if (currentSpecialistId != null) {
                currentSpecialistIdInt = Integer.parseInt(currentSpecialistId);
            }

            if (currentSpecialistIdInt < 0) {

                // Set the DemographicContact list into an array for further processing.
                // This list contains a guranteed id from the ProfessionalSpecialist model
                for (DemographicContact demographicContact : demographicContacts) {
                    // separate method to check the current list for existing professionalSpecialist
                    // This will determine if a new DemographicContact should be created.
                    if (currentDemographicContact == null && ((currentSpecialistIdInt * -1) + "").equals(demographicContact.getContactId())) {
                        currentDemographicContact = demographicContact.getId() + "";
                    }
                }
            }

            if (currentDemographicContact != null) {

                // this ProfessionalSpecialist is already in the DemographicContacts.
                consultUtil.setSpecialist(currentDemographicContact);

            } else if (currentSpecialistIdInt < 0) {

                // this ProfessionalSpecialist needs to have a DemographicContact created.
                String service = consultUtil.getService();

                ContactSpecialty contactSpecialty = contactSpecialtyDao.findBySpecialty(service);

                if (contactSpecialty == null) {
                    contactSpecialty = contactSpecialtyDao.findBySpecialty("other");
                }

                service = contactSpecialty.getId() + "";

                if (service == null) {
                    service = "";
                }

                DemographicContact demographicContact = addDemographicContact(loggedInInfo, demo, (currentSpecialistIdInt * -1), service);

                demographicContactDao.persist(demographicContact);
                demographicContacts = demographicManager.getHealthCareTeam(loggedInInfo, Integer.parseInt(demo));
                consultUtil.setSpecialist(demographicContact.getId() + "");
            }

            setHealthCareTeam(demographicContacts, healthCareTeam, consultationServices, consultationServiceDao);

            pageContext.setAttribute("consultationServices", consultationServices);
            pageContext.setAttribute("healthCareTeam", healthCareTeam);
        }

        pageContext.setAttribute("consultUtil", consultUtil);
    %>
    <%!
        private static DemographicContact addDemographicContact(LoggedInInfo loggedInInfo,
                                                                String demographicNo, int contactId, String role) {

            if (role == null) {
                role = "0";
            }

            DemographicContact demographicContact = new DemographicContact();
            demographicContact.setFacilityId(loggedInInfo.getCurrentFacility().getId());
            demographicContact.setCreator(loggedInInfo.getLoggedInProviderNo());
            demographicContact.setCreated(new Date(System.currentTimeMillis()));
            demographicContact.setUpdateDate(new Date(System.currentTimeMillis()));
            demographicContact.setDeleted(Boolean.FALSE);
            demographicContact.setDemographicNo(Integer.parseInt(demographicNo));
            demographicContact.setContactId(contactId + "");
            demographicContact.setRole(role);
            demographicContact.setType(3);
            demographicContact.setCategory("professional");

            return demographicContact;
        }
    %>

    <%!
        private static void setHealthCareTeam(List<DemographicContact> demographicContacts,
                                              List<DemographicContact> healthCareTeam, HashSet<ConsultationServices> consultationServices,
                                              ConsultationServiceDao consultationServiceDao) {

            for (DemographicContact demographicContact : demographicContacts) {
                // ensure consent has been given to contact this specialist.
                // ensure that this specialist has a cpso (specialist, msp, or college id)
                if (demographicContact.isConsentToContact() &&
                        (((ProfessionalContact) demographicContact.getDetails()).getCpso() != null) &&
                        (!((ProfessionalContact) demographicContact.getDetails()).getCpso().isEmpty())
                ) {
                    healthCareTeam.add(demographicContact);

                    // Get the specialty list for this group of specialist.
                    // This is a hack. Do not expand on it. There are several specialty look up tables in Oscar
                    // The health care team uses the ContactSpecialty table and this Consultation feature uses the consultatationServices
                    ConsultationServices consultService = consultationServiceDao.findByDescription(demographicContact.getRole());
                    if (consultService != null) {
                        consultationServices.add(consultService);
                    }
                }
            }

        }

    %>
    <%--
	// Read the Health Care Team from the pageScope into a Javascript globalScope;
 --%>
    <c:if test="${ not empty consultationServices }">
        <script type="text/javascript">
            var consultationServices = [];
        </script>
        <c:forEach items="${ consultationServices }" var="consultationService" varStatus="loop">
            <script type="text/javascript">
                //<!--
                var service = {};
                service.id = `${ consultationService.serviceId }`;
                service.description = `${ consultationService.serviceDesc }`;
                if (service) {
                    consultationServices.push(service);
                }
                //-->
            </script>
        </c:forEach>
    </c:if>
    <%--
	// Read the associated services and specialties from the pageScope into a Javascript globalScope;
 --%>
    <c:if test="${ not empty healthCareTeam }">
        <script type="text/javascript">
            var healthCareTeam = [];
        </script>
        <c:forEach items="${ healthCareTeam }" var="demographicContact" varStatus="loop">
            <script type="text/javascript">
                //<!--
                var contact = {};
                contact.contactId = `${ demographicContact.details.id }`;
                contact.specNbr = `${ demographicContact.details.cpso }`;
                contact.phoneNum = `${ demographicContact.details.workPhone }`;
                contact.specName = `${ demographicContact.details.formattedName }`;
                contact.service = `${ demographicContact.role }`;
                contact.specFax = `${ demographicContact.details.fax }`;
                contact.specAddress = `${ demographicContact.details.address }`;
                contact.specAddress2 = `${ demographicContact.details.address2 }`;
                contact.city = `${ demographicContact.details.city }`;
                contact.province = `${ demographicContact.details.province }`;
                contact.postal = `${ demographicContact.details.postal }`;
                contact.note = `${ demographicContact.details.note }`;
                healthCareTeam[`${ demographicContact.id }`] = contact;
                //-->
            </script>
        </c:forEach>
    </c:if>

    <%-- Add function for specialist selection events. --%>
    <script type="text/javascript">
        //<!--
        function getSpecialist(selected) {
            var specialistIndex = selected.value;
            var form = document.EctConsultationFormRequest2Form;

            if (specialistIndex < 0) {
                form.phone.value = ("");
                form.fax.value = ("");
                form.address.value = ("");

                specialistFaxNumber = ""; // global variable
            }

            if (specialistIndex > -1) {
                form.annotation.value = healthCareTeam[specialistIndex].note;
                form.phone.value = healthCareTeam[specialistIndex].phoneNum;
                form.fax.value = healthCareTeam[specialistIndex].specFax;
                form.address.value = healthCareTeam[specialistIndex].specAddress;

                specialistFaxNumber = healthCareTeam[specialistIndex].specFax; // global variable
                updateFaxButton();

                var service = healthCareTeam[specialistIndex].service;

                if (!service) {

                    form.service.value = '57';

                } else {
                    form.service.value = "";
                    for (var i = 0; consultationServices.length; i++) {
                        var specialistService = consultationServices[i];
                        if (specialistService.description === service) {
                            form.service.value = specialistService.id;
                        }
                    }
                }
            }
        }

        //-->
    </script>

    <head>
        <title>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.title"/>
        </title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
        <script>
            var ctx = '<%=request.getContextPath()%>';
            var requestId = '<%=requestId%>';
            var demographicNo = '<%=demo%>';
            var demoNo = '<%=demo%>';
            var appointmentNo = '<%=appNo%>';
        </script>

        <script type="text/javascript" src="<%=request.getContextPath()%>/js/global.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/library/jquery/jquery-3.6.4.min.js"></script>
        <script type="text/javascript"
                src="<%=request.getContextPath()%>/library/jquery/jquery-ui-1.12.1.min.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/jquery_oscar_defaults.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/share/javascript/prototype.js"></script>
        <link href="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.12.1.min.css" rel="stylesheet"
              media="screen"/>
        <link rel="stylesheet" type="text/css" media="all"
              href="<%=request.getContextPath()%>/share/calendar/calendar.css" title="win2k-cold-1"/>
        <!-- main calendar program -->
        <script type="text/javascript" src="<%=request.getContextPath()%>/share/calendar/calendar.js"></script>
        <!-- language for the calendar -->
        <script type="text/javascript" src="<%=request.getContextPath()%>/share/calendar/lang/calendar-en.js"></script>
        <!-- the following script defines the Calendar.setup helper function, which makes adding a calendar a matter of 1 or 2 lines of code. -->
        <script type="text/javascript" src="<%=request.getContextPath()%>/share/calendar/calendar-setup.js"></script>

        <script>
            jQuery.noConflict();
        </script>

        <!-- Instead of importing conreq.js using the CME tag (as done in Oscar19/OscarPro), we are opting to directly import conreq.js without utilizing the CME tag. -->
        <% if ("ocean".equals(props.get("cme_js"))) {
            int randomNo = new Random().nextInt();%>
        <script id="mainScript"
                src="${ pageContext.request.contextPath }/js/custom/ocean/conreq.js?no-cache=<%=randomNo%>&autoRefresh=true"
                ocean-host=<%=Encode.forUriComponent(props.getProperty("ocean_host"))%>></script>
        <% } %>
        <link rel="stylesheet" type="text/css" href="${ pageContext.request.contextPath }/css/healthCareTeam.css"/>
        <link rel="stylesheet" type="text/css"
              href="${ pageContext.request.contextPath }/oscarEncounter/encounterStyles.css">

        <style type="text/css">

            /* Ocean refer style */
            span.oceanRefer {
                display: flex;
                align-items: center;
                padding-top: 20px
            }

            span.oceanRefer a {
                margin-right: 5px;
            }

            /* End of Ocean refer style */

            #attachedDocumentTable {
                border: blue thin solid;
                border-collapse: collapse;
                width: 100%;
                background-color: #ddddff;
            }

            #attachedDocumentTable tr td {
                padding: 5px;
            }

            #attachedDocumentsTable {
                border-collapse: collapse;
                width: 100%;
            }

            #attachedDocumentsTable h3, #attachedLabsTable h3, #attachedFormsTable h3, #attachedEFormsTable h3, #attachedHRMDocumentsTable h3 {
                margin: 0px !important;
                padding: 0px !important;
                border-bottom: grey thin solid;
            }

            #attachedLabsTable, #attachedFormsTable, #attachedDocumentsTable, #attachedEFormsTable, #attachedHRMDocumentsTable {
                border-collapse: collapse;
                width: 100%;
            }

            .ui-dialog {
                font-size: small !important;
            }

            .ui-autocomplete {
                font-size: small !important;
            }

            .save-and-close-button {
                width: auto !important;
            }

            th, td.tite1 {
                background-color: #BFBFFF;
                color: black;
                font-size: small;
                padding: 0px;
            }

            td.tite3 {
                background-color: #BFBFFF;
                color: black;
                padding: 0px;
            }

            td.tite1 {
                padding: 5px 10px;
            }

            td.tite4 {
                padding: 0px 10px;
                background-color: #ddddff;
                color: black;
                font-size: small;
            }

            td.stat {
                font-size: 10pt;
            }

            .consultDemographicData input, .consultDemographicData select, .consultDemographicData textarea {
                width: 100% !important;
                box-sizing: border-box;
            }

            input#referalDate, input#appointmentDate, input#followUpDate {
                background-image: url(<%= request.getContextPath() %>/images/cal.gif);
                background-position-x: right;
                background-position-y: center;
                background-repeat: no-repeat;
            }

            #referalDate_cal, #appointmentDate_cal, #followUpDate_cal {
                display: none !important;
            }

            * table tr td {
                vertical-align: top !important;
            }


            textarea {
                width: 100%;
                box-sizing: border-box;
            }

            .controlPanel {
                padding: 5px 10px !important;
                border: blue thin solid;
            }

            .heading {
                font-weight: bold;
                padding: 5px !important;
            }


        </style>
    </head>


    <script type="text/javascript">

        var servicesName = new Object();   		// used as a cross reference table for name and number
        var services = new Array();				// the following are used as a 2D table for makes and models
        var specialists = new Array();
        var specialistFaxNumber = "";
        var servicesLoaded = false;  // Flag to track if services have been loaded

        /////////////////////////////////////////////////////////////////////
        // Load services via AJAX - accepts callback for proper sequencing
        function loadServicesFromServer(callback) {
            var serviceDropdown = document.getElementById('service');
            if (!serviceDropdown) {
                console.error('Service dropdown not found on page');
                return;
            }

            // Initialize default service and specialist
            K(-1, "----All Services-------");
            var defaultSpec = new Specialist(-1, -1, "", "--------All Specialists-----", "", "", "");
            services[-1] = new Service();
            services[-1].specialists.push(defaultSpec);

            // Add "All Services" as first option in dropdown
            serviceDropdown.options.length = 0;  // Clear any existing options
            serviceDropdown.options[0] = new Option("----All Services-------", -1);

            var xhr = new XMLHttpRequest();
            xhr.open('GET', '<%= request.getContextPath() %>/oscarEncounter/ConsultationLookup2Action.do?method=getServices', true);
            xhr.onreadystatechange = function() {
                if (xhr.readyState === 4) {
                    if (xhr.status === 200) {
                        try {
                            var serviceList = JSON.parse(xhr.responseText);
                            processServices(serviceList);
                            servicesLoaded = true;
                            if (callback) callback();
                        } catch(e) {
                            console.error('Error parsing services JSON:', e);
                            if (callback) {
                                try {
                                    callback(e);
                                } catch (callbackError) {
                                    console.error('Error in loadServicesFromServer callback:', callbackError);
                                }
                            }
                        }
                    } else {
                        console.error('Failed to load services. HTTP status: ' + xhr.status);
                        if (callback) {
                            try {
                                callback(new Error('Failed to load services (HTTP ' + xhr.status + ')'));
                            } catch (callbackError) {
                                console.error('Error in loadServicesFromServer callback:', callbackError);
                            }
                        }
                    }
                }
            };
            xhr.send();
        }

        function processServices(serviceList) {
            var serviceDropdown = document.getElementById('service');
            for (var i = 0; i < serviceList.length; i++) {
                var service = serviceList[i];
                K(service.serviceId, service.serviceDesc);

                // Add to dropdown if not exists
                var exists = false;
                for (var idx = 0; idx < serviceDropdown.options.length; idx++) {
                    if (serviceDropdown.options[idx].value == service.serviceId) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    serviceDropdown.options[serviceDropdown.options.length] = new Option(service.serviceDesc, service.serviceId);
                }
            }
        }

        /////////////////////////////////////////////////////////////////////
        // Load specialists for a service via AJAX
        function loadSpecialistsForService(serviceId, callback) {
            // Check if already loaded
            if (services[serviceId] && services[serviceId].specialists.length > 0) {
                if (callback) callback();
                return;
            }

            var xhr = new XMLHttpRequest();
            xhr.open('GET', '<%= request.getContextPath() %>/oscarEncounter/ConsultationLookup2Action.do?method=getSpecialists&serviceId=' + serviceId, true);
            xhr.onreadystatechange = function() {
                if (xhr.readyState === 4) {
                    if (xhr.status === 200) {
                        try {
                            var specialistList = JSON.parse(xhr.responseText);
                            // Ensure service exists in array before adding specialists
                            if (!services[serviceId]) {
                                services[serviceId] = new Service();
                            }
                            // Clear existing specialists to prevent duplicates on reload
                            services[serviceId].specialists = [];
                            for (var i = 0; i < specialistList.length; i++) {
                                var spec = specialistList[i];
                                addSpecialist(serviceId, spec.specId, spec.phone, spec.name, spec.fax, spec.address, spec.annotation);
                            }
                            if (callback) callback();
                        } catch(e) {
                            console.error('Error parsing specialists JSON:', e);
                            if (callback) {
                                try {
                                    callback(e);
                                } catch (callbackError) {
                                    console.error('Error in loadSpecialistsForService callback:', callbackError);
                                }
                            }
                        }
                    } else {
                        console.error('Failed to load specialists for service ' + serviceId + '. HTTP status: ' + xhr.status);
                        if (callback) {
                            try {
                                callback(new Error('Failed to load specialists for service ' + serviceId + ' (HTTP ' + xhr.status + ')'));
                            } catch (callbackError) {
                                console.error('Error in loadSpecialistsForService callback:', callbackError);
                            }
                        }
                    }
                }
            };
            xhr.send();
        }

        /////////////////////////////////////////////////////////////////////
        // Add specialist to array (with duplicate check)
        function addSpecialist(serviceId, specId, phone, name, fax, address, annotation) {
            var specs = services[serviceId].specialists;
            for (var i = 0; i < specs.length; i++) {
                if (specs[i].specNbr == specId) return; // Already exists
            }
            specs.push(new Specialist(serviceId, specId, phone, name, fax, address, annotation));
        }

        /////////////////////////////////////////////////////////////////////
        // Single function to populate specialist dropdown - handles all cases
        function populateSpecialistDropdown(serviceId, savedSpecialistId) {
            var dropdown = document.EctConsultationFormRequest2Form.specialist;

            // Defensive: services[-1] and its specialists array may not be initialized
            var defaultSpec = null;
            if (services && services[-1] && services[-1].specialists && services[-1].specialists.length > 0) {
                defaultSpec = services[-1].specialists[0];
            }

            var isExistingConsultation = (requestId && requestId != "null" && requestId != "");

            // Clear dropdown
            dropdown.options.length = 0;

            // For existing consultations, use blank first option; for new, use "All Specialists"
            if (isExistingConsultation) {
                dropdown.options[0] = new Option("", "");
            } else {
                dropdown.options[0] = new Option(
                    defaultSpec ? defaultSpec.specName : "Select a specialist",
                    defaultSpec ? defaultSpec.specNbr : ""
                );
            }

            if (!serviceId || serviceId == "-1" || serviceId == "null") {
                return; // No service selected
            }

            var specs = services[serviceId] ? services[serviceId].specialists : [];
            var foundSaved = false;

            for (var i = 0; i < specs.length; i++) {
                var spec = specs[i];
                var isSelected = (savedSpecialistId && spec.specNbr == savedSpecialistId);
                if (isSelected) foundSaved = true;
                dropdown.options[dropdown.options.length] = new Option(spec.specName, spec.specNbr, false, isSelected);
            }

            return foundSaved;
        }

        //-------------------------------------------------------------------

        /////////////////////////////////////////////////////////////////////
        // create car make objects and fill arrays
        //==========
        function K(serviceNumber, service) {

            //servicesName[service] = new ServicesName(serviceNumber);
            servicesName[service] = serviceNumber;
            services[serviceNumber] = new Service();
        }

        //-------------------------------------------------------------------

        //-----------------disableDateFields() disables date fields if "Patient Will Book" selected
        var disableFields = false;
    </script>

    <oscar:oscarPropertiesCheck defaultVal="false" value="true" property="CONSULTATION_PATIENT_WILL_BOOK">
        <script type="text/javascript">


            function disableDateFields() {
                if (document.forms[0].patientWillBook.checked) {
                    setDisabledDateFields(document.forms[0], true);
                } else {
                    setDisabledDateFields(document.forms[0], false);
                }
            }
        </script>
    </oscar:oscarPropertiesCheck>

    <oscar:oscarPropertiesCheck defaultVal="false" value="false" property="CONSULTATION_PATIENT_WILL_BOOK">
        <script type="text/javascript">

            function disableDateFields() {

                setDisabledDateFields(document.forms[0], false);

            }
        </script>
    </oscar:oscarPropertiesCheck>


    <script type="text/javascript">

        function getClinicalData(data, target) {
            jQuery.ajax({
                method: "POST",
                url: "${ pageContext.request.contextPath }/oscarConsultationRequest/consultationClinicalData.do",
                data: data,
                dataType: 'JSON',
                success: function (data) {
                    jQuery(target).val(jQuery(target).val() + "\n" + data.note);
                }
            });
        }

        jQuery(document).ready(function () {
            jQuery(".medicationData").click(function () {
                var data = new Object();
                var target = "#" + this.id.split("_")[1];
                data.method = this.id.split("_")[0];
                data.demographicNo = <%= demo %>;
                getClinicalData(data, target)
            });

            jQuery(".clinicalData").click(function () {
                var data = new Object();
                var target = "#" + this.id.split("_")[1];
                data.method = "fetchIssueNote";
                data.issueType = this.id.split("_")[0];
                data.demographicNo = <%= demo %>;
                getClinicalData(data, target)
            });
        })

        function setDisabledDateFields(form, disabled) {
            //form.appointmentYear.disabled = disabled;
            //form.appointmentMonth.disabled = disabled;
            //form.appointmentDay.disabled = disabled;
            form.appointmentHour.disabled = disabled;
            form.appointmentMinute.disabled = disabled;
            form.appointmentPm.disabled = disabled;
        }

        function disableEditing() {
            if (disableFields) {
                form = document.forms[0];

                setDisabledDateFields(form, disableFields);

                form.status[0].disabled = disableFields;
                form.status[1].disabled = disableFields;
                form.status[2].disabled = disableFields;
                form.status[3].disabled = disableFields;

                form.referalDate.disabled = disableFields;
                form.providerNo.selectedIndex = -1;
                disableIfExists(form.providerNo, disableFields);
                disableIfExists(form.specialist, disableFields);
                disableIfExists(form.service, disableFields);
                form.urgency.disabled = disableFields;
                form.phone.disabled = disableFields;
                form.fax.disabled = disableFields;
                form.address.disabled = disableFields;
                disableIfExists(form.patientWillBook, disableFields);
                form.sendTo.disabled = disableFields;

                form.appointmentNotes.disabled = disableFields;
                form.reasonForConsultation.disabled = disableFields;
                form.clinicalInformation.disabled = disableFields;
                form.concurrentProblems.disabled = disableFields;
                form.currentMedications.disabled = disableFields;
                form.allergies.disabled = disableFields;
                form.annotation.disabled = disableFields;
                form.appointmentDate.disabled = disableFields;
                form.followUpDate.disabled = disableFields;
                disableIfExists(form.letterheadFax, disableFields);

                disableIfExists(form.update, disableFields);
                disableIfExists(form.updateAndPrint, disableFields);
                disableIfExists(form.updateAndSendElectronically, disableFields);
                disableIfExists(form.updateAndFax, disableFields);

                disableIfExists(form.submitSaveOnly, disableFields);
                disableIfExists(form.submitAndPrint, disableFields);
                disableIfExists(form.submitAndSendElectronically, disableFields);
                disableIfExists(form.submitAndFax, disableFields);

                hideElement('referalDate_cal');
                hideElement('appointmentDate_cal');
                hideElement("followUpDate_cal");
            }
        }

        function disableIfExists(item, disabled) {
            if (item != null) item.disabled = disabled;
        }

        function hideElement(elementId) {
            let element = document.getElementById(elementId)
            if (element != null) {
                element.style.display = 'none';
            }
        }

        //------------------------------------------------------------------------------------------
        /////////////////////////////////////////////////////////////////////
        // Specialist constructor
        function Specialist(makeNumber, specNum, phoneNum, SpecName, SpecFax, SpecAddress, SpecAnnotation) {
            this.specId = makeNumber;
            this.specNbr = specNum;
            this.phoneNum = phoneNum;
            this.specName = SpecName;
            this.specFax = SpecFax;
            this.specAddress = SpecAddress;
            this.specAnnotation = SpecAnnotation || undefined;
        }

        // Service constructor
        function Service() {
            this.specialists = new Array();
        }

        // Service name constructor (legacy)
        function ServicesName(makeNumber) {
            this.serviceNumber = makeNumber;
        }

        // Legacy D() function - now uses addSpecialist
        function D(servNumber, specNum, phoneNum, SpecName, SpecFax, SpecAddress, SpecAnnotation) {
            if (!services[servNumber]) {
                services[servNumber] = new Service();
            }
            addSpecialist(servNumber, specNum, phoneNum, SpecName, SpecFax, SpecAddress, SpecAnnotation);
        }

        //-------------------------------------------------------------------

        /////////////////////////////////////////////////////////////////////
        // Called when user changes service dropdown
        function fillSpecialistSelect(aSelectedService) {
            document.getElementById("eFormButton").style.display = "none";

            var selectedIdx = aSelectedService.selectedIndex;
            var serviceId = (aSelectedService.options[selectedIdx]).value;

            // Clear form fields
            document.EctConsultationFormRequest2Form.phone.value = "";
            document.EctConsultationFormRequest2Form.fax.value = "";
            document.EctConsultationFormRequest2Form.address.value = "";
            document.getElementById("annotation").value = "";

            if (selectedIdx == 0 || serviceId == "-1") {
                populateSpecialistDropdown(null, null);
                return;
            }

            // Load specialists and populate dropdown
            loadSpecialistsForService(serviceId, function() {
                populateSpecialistDropdown(serviceId, null);
            });
        }

        //-------------------------------------------------------------------

        /////////////////////////////////////////////////////////////////////
        // Initialize consultation - called AFTER services are loaded
        function initializeConsultation(savedService, savedServiceName, savedSpecialist, savedSpecName, savedPhone, savedFax, savedAddress) {
            var serviceDropdown = document.getElementById('service');

            // Check if saved service exists in loaded services
            var serviceExists = false;
            for (var idx = 0; idx < serviceDropdown.options.length; idx++) {
                if (serviceDropdown.options[idx].value == savedService) {
                    serviceDropdown.options[idx].selected = true;
                    serviceExists = true;
                    break;
                }
            }

            // If service was deleted but we have saved data, add it
            if (!serviceExists && savedService && savedService != "null") {
                K(savedService, savedServiceName);
                serviceDropdown.options[serviceDropdown.options.length] = new Option(savedServiceName, savedService, false, true);

                // Also add the specialist for this deleted service
                if (savedSpecialist && savedSpecialist != "null") {
                    if (!services[savedService]) {
                        services[savedService] = new Service();
                    }
                    addSpecialist(savedService, savedSpecialist, savedPhone, savedSpecName, savedFax, savedAddress, "");
                }
            }

            // Now load specialists for the saved service
            if (savedService && savedService != "null") {
                loadSpecialistsForService(savedService, function() {
                    var foundSaved = populateSpecialistDropdown(savedService, savedSpecialist);

                    <%if(requestId!=null){ %>
                    // Handle case where saved specialist no longer exists
                    if (!foundSaved && savedSpecialist && savedSpecialist != "null") {
                        var dropdown = document.EctConsultationFormRequest2Form.specialist;
                        // Replace "All Specialists" with the saved specialist name
                        dropdown.options[0] = new Option("<%=consultUtil.getSpecailistsName(consultUtil.specialist)%>", savedSpecialist, false, true);
                        document.getElementById("consult-disclaimer").style.display = 'inline';
                    } else if (!savedSpecialist || savedSpecialist == "null") {
                        var dropdown = document.EctConsultationFormRequest2Form.specialist;
                        dropdown.options[0] = new Option("No Consultant Saved", "-1", false, true);
                    }
                    <%}%>

                    // Fill phone/fax/address fields
                    FillThreeBoxes(savedSpecialist);
                    onSelectSpecialist(document.EctConsultationFormRequest2Form.specialist);
                });
            } else {
                // New consultation - just show "All Specialists"
                populateSpecialistDropdown(null, null);
            }
        }

        //-------------------------------------------------------------------
        /////////////////////////////////////////////////////////////////////
        function onSelectSpecialist(SelectedSpec) {
            var selectedIdx = SelectedSpec.selectedIndex;
            var form = document.EctConsultationFormRequest2Form;

            if (selectedIdx == null || selectedIdx === -1 || (SelectedSpec.options[selectedIdx]).value === "-1") {   		//if its the first item set everything to blank
                form.phone.value = ("");
                form.fax.value = ("");
                form.address.value = ("");
                document.getElementById("annotation").value = "";

                enableDisableRemoteReferralButton(form, true);

                <%
		if (props.isConsultationFaxEnabled()) {//
		%>
                specialistFaxNumber = "";
                updateFaxButton();
                <% } %>

                return;
            }
            var selectedService = document.EctConsultationFormRequest2Form.service.value;  				// get the service that is selected now
            var specs = (services[selectedService].specialists); 			// get all the specs the offer this service

            // load the text fields with phone fax and address for past consult review even if spec has been removed from service list
            <%if(requestId!=null && ! "null".equals( consultUtil.specialist ) ){ %>
            form.phone.value = '<%=Encode.forJavaScript(consultUtil.specPhone)%>';
            form.fax.value = '<%=Encode.forJavaScript(consultUtil.specFax)%>';
            form.address.value = '<%=Encode.forJavaScript(consultUtil.specAddr)%>';

            //make sure this dislaimer is displayed
            document.getElementById("consult-disclaimer").style.display = 'inline';
            <%}%>


            for (var idx = 0; idx < specs.length; ++idx) {
                aSpeci = specs[idx];									// get the specialist Object for the currently selected spec
                if (aSpeci.specNbr == SelectedSpec.value) {
                    form.phone.value = (aSpeci.phoneNum.replace(null, ""));
                    form.fax.value = (aSpeci.specFax.replace(null, ""));					// load the text fields with phone fax and address
                    form.address.value = (aSpeci.specAddress.replace(null, ""));

                    //since there is a match make sure the dislaimer is hidden
                    document.getElementById("consult-disclaimer").style.display = 'none';

                    <%
        		if (props.isConsultationFaxEnabled()) {//
				%>
                    specialistFaxNumber = aSpeci.specFax.trim();
                    updateFaxButton();
                    <% } %>

                    jQuery.post(ctx + "/getProfessionalSpecialist.do", {id: aSpeci.specNbr},
                        function (xml) {
                            console.log(xml);
                            let hasUrl = xml.eDataUrl != null && xml.eDataUrl !== "";
                            enableDisableRemoteReferralButton(form, !hasUrl);
                            let annotation = document.getElementById("annotation");
                            annotation.value = xml.annotation;
                            updateEFormLink(xml.eformId)
                        }
                    );

                    break;
                }
            }//spec loop

        }

        function updateEFormLink(eformID) {
            if (eformID > 0) {
                let eFormURL = '<%=request.getContextPath()%>/eform/efmformadd_data.jsp?fid=' + eformID + '&demographic_no=<%=demo%>&appointment=null';
                document.getElementById("eFormButton").style.display = "inline";
                document.getElementById("eFormButton").onclick = function () {
                    popup(eFormURL);
                };  //opening as a popup deliberately because the consult is already a popup so best to just have another popup
            } else {
                document.getElementById("eFormButton").style.display = "none";
            }
        }

        //-----------------------------------------------------------------

        /////////////////////////////////////////////////////////////////////
        function FillThreeBoxes(serNbr) {

            var selectedService = document.EctConsultationFormRequest2Form.service.value;  				// get the service that is selected now
            var specs = (services[selectedService].specialists);					// get all the specs the offer this service

            for (var idx = 0; idx < specs.length; ++idx) {
                aSpeci = specs[idx];									// get the specialist Object for the currently selected spec
                if (aSpeci.specNbr == serNbr) {
                    document.EctConsultationFormRequest2Form.phone.value = (aSpeci.phoneNum);
                    document.EctConsultationFormRequest2Form.fax.value = (aSpeci.specFax);					// load the text fields with phone fax and address
                    document.EctConsultationFormRequest2Form.address.value = (aSpeci.specAddress);
                    <%
        		if (props.isConsultationFaxEnabled()) {//
				%>
                    specialistFaxNumber = aSpeci.specFax.trim();
                    updateFaxButton();
                    <% } %>
                    break;
                }
            }
        }

        //-----------------------------------------------------------------

        function enableDisableRemoteReferralButton(form, disabled) {
            var button = form.updateAndSendElectronically;
            if (button != null) button.disabled = disabled;
            button = form.submitAndSendElectronically;
            if (button != null) button.disabled = disabled;

            var button = form.updateAndSendElectronicallyTop;
            if (button != null) button.disabled = disabled;
            button = form.submitAndSendElectronicallyTop;
            if (button != null) button.disabled = disabled;
        }

        //-->

        function BackToOscar() {
            window.close();
        }

        function rs(n, u, w, h, x) {
            args = "width=" + w + ",height=" + h + ",resizalbe=yes,scrollbars=yes,status=0,top=60,left=30";
            remote = window.open(u, n, args);
            if (remote != null) {
                if (remote.opener == null)
                    remote.opener = self;
            }
            if (x == 1) {
                return remote;
            }
        }

        var DocPopup = null;

        function popup(location) {
            DocPopup = window.open(location, "_blank", "height=380,width=580");

            if (DocPopup != null) {
                if (DocPopup.opener == null) {
                    DocPopup.opener = self;
                }
            }
        }

        function popupAttach(height, width, url, windowName) {
            var page = url;
            windowprops = "height=" + height + ",width=" + width + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
            var popup = window.open(url, windowName, windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
            }
            popup.focus();
            return false;
        }

        function popupOscarCal(vheight, vwidth, varpage) { //open a new popup window
            var page = varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=no,menubars=no,toolbars=no,resizable=no,screenX=0,screenY=0,top=20,left=20";
            var popup = window.open(varpage, "<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgCal"/>", windowprops);

            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
            }
        }

    </script>

    <oscar:oscarPropertiesCheck value="true" property="ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS"
                                defaultVal="false">
        <script type="text/javascript">
            //<!--
            function checkFormHCT() {

                var msg = "Please select a Consultant. Or add a Consultant using edit Health Care Team.";
                var specialistElement = document.EctConsultationFormRequest2Form.specialist.options;
                if (!specialistElement || specialistElement.selectedIndex < 0) {
                    document.EctConsultationFormRequest2Form.specialist.focus();
                    alert(msg);
                    return false;
                }

                msg = "The selected consultant contains an invalid specialty type. Please add or correct the current specialty using edit Health Care Team";
                var serviceElement = document.EctConsultationFormRequest2Form.service;
                if (!serviceElement || serviceElement.value == "") {
                    document.EctConsultationFormRequest2Form.service.focus();
                    alert(msg);
                    return false;
                }

                return true;
            }

            //-->
        </script>
    </oscar:oscarPropertiesCheck>

    <script type="text/javascript">

        function checkForm(submissionVal, formName) {
            ShowSpin(true);
            var success = true;

            if (typeof checkFormHCT === "function") {
                if (!checkFormHCT()) {
                    HideSpin();
                    return false;
                }
            }

            var msg = "<fmt:setBundle basename="oscarResources"/><fmt:message key="Errors.service.noServiceSelected"/>";
            msg = msg.replace('<li>', '');
            msg = msg.replace('</li>', '');
            var serviceOptionsElement = document.EctConsultationFormRequest2Form.service.options;
            if (serviceOptionsElement && serviceOptionsElement.selectedIndex == 0) {
                alert(msg);
                document.EctConsultationFormRequest2Form.service.focus();
                HideSpin();
                return false;
            }
            var faxNumber = document.EctConsultationFormRequest2Form.fax.value;
            faxNumber = faxNumber.trim();
            var apptDate = document.EctConsultationFormRequest2Form.appointmentDate.value;
            var hasApptTime = document.EctConsultationFormRequest2Form.appointmentHour.options.selectedIndex != 0 &&
                document.EctConsultationFormRequest2Form.appointmentMinute.options.selectedIndex != 0;

            if (apptDate.length > 0 && !hasApptTime) {
                alert('Please enter appointment time. You cannot choose appointment date only.');
                HideSpin();
                return false;
            }

            if ('Submit And Fax' === submissionVal && !faxNumber) {
                alert('Please enter a valid 10 digit consultant fax number');
                HideSpin();
                return false;
            }

            // If the user clicks the 'Print Preview' button, ensure that their unsaved changes are preserved, allowing them to stay on the same page. Achieve this by making an AJAX call.
            if ('And Print Preview' === submissionVal) {
                getConsultFormPrintPreview(document.forms[formName]);
                return false;
            }

            $("saved").value = "true";
            document.forms[formName].submission.value = submissionVal;
            document.forms[formName].submit();
            return true;
        }
    </script>


    <%
        /*
        * set and select the default provider to be used in the Letterhead.
        * It is possible for this value to be different than the letterhead provider.
        * 1). logged in provider
        * 2). MRP on patient file
        * 3). Clinic Address.
        * See calls to javascript switchProvider for methods and order of change.
        */
        String lhndType = "providers"; //set default as providers
        String providerDefault = providerNo;

        if (consultUtil.letterheadName == null || consultUtil.letterheadName.isEmpty()) {
            //nothing saved so find default
            UserProperty lhndProperty = userPropertyDAO.getProp(providerNo, UserProperty.CONSULTATION_LETTERHEADNAME_DEFAULT);
            String lhnd = null;

            if (lhndProperty != null) {
                lhnd = lhndProperty.getValue();
            }

            //1 or null = providers, 2 = MRP and 3 = clinic

            if (lhnd != null) {
                if ("2".equals(lhnd)) {
                    //mrp
                    providerDefault = providerNoFromChart;
                } else if ("3".equals(lhnd)) {
                    //clinic
                    lhndType = "clinic";
                }
            }

        }

        //TODO set up user settings for selecting default referring provider
        /*
        * set and select the default referring provider.
        * It is possible for this value to be different than the letterhead provider.
        * 1). or NULL:   use logged in provider
        * 2). use MRP on patient file.
        */
        // providerNo is the logged in provider
        String referringProviderDefault = providerNo;
        if(consultUtil.providerNo == null || consultUtil.providerNo.isEmpty()) {
            UserProperty defaultReferringPractitioner = userPropertyDAO.getProp(providerNo, UserProperty.DEFAULT_REF_PRACTITIONER);
            String defaultValue = null;
            if(defaultReferringPractitioner != null) {
                defaultValue = defaultReferringPractitioner.getValue();
            }
            if("2".equals(defaultValue)) {
                referringProviderDefault = providerNoFromChart;
            }

        }
        pageContext.setAttribute("referringProviderDefault", referringProviderDefault);
        pageContext.setAttribute("lhndType", lhndType);
        pageContext.setAttribute("providerDefault", providerDefault);
    %>

    <script>

        const providerData = {};

        providerData['<%=Encode.forHtmlContent(clinic.getClinicName())%>'] = {};

        let addr, ph, fx;

        <% if (consultUtil.letterheadAddress != null) { %>
        addr = '<%= Encode.forHtmlContent(consultUtil.letterheadAddress).replaceAll("\\n", " ") %>';
        <%} else {%>
        addr = '<%=Encode.forHtmlContent(clinic.getClinicAddress()) + " " + Encode.forHtmlContent(clinic.getClinicCity()) + " " + Encode.forHtmlContent(clinic.getClinicProvince()) + " " + Encode.forHtmlContent(clinic.getClinicPostal()) %>';
        <%}%>

        <% if(consultUtil.letterheadPhone != null) { %>
        ph = '<%=Encode.forHtmlContent(consultUtil.letterheadPhone).replaceAll("\\n", " ")%>';
        <%} else { %>
        ph = '<%=Encode.forHtmlContent(clinic.getClinicPhone())%>';
        <% }%>

        <%if(consultUtil.letterheadFax != null) { %>
        fx = '<%=Encode.forHtmlContent(consultUtil.letterheadFax)%>';
        <% } else {%>
        fx = '<%=Encode.forHtmlContent(clinic.getClinicFax())%>';
        <% } %>

        providerData['<%=Encode.forJavaScript(clinic.getClinicName())%>'].address = addr;
        providerData['<%=Encode.forJavaScript(clinic.getClinicName())%>'].phone = ph;
        providerData['<%=Encode.forJavaScript(clinic.getClinicName())%>'].fax = fx;


        <%
for (Provider providerItem: prList) {
	if (!providerItem.getProviderNo().equalsIgnoreCase("-1")) {
		String prov_no = "prov_"+providerItem.getProviderNo();

		%>
        providerData['<%=prov_no%>'] = {};

        providerData['<%=prov_no%>'].address = "<%=Encode.forHtmlContent(providerItem.getFullAddress())%>";
        providerData['<%=prov_no%>'].phone = "<%=Encode.forHtmlContent(providerItem.getClinicPhone())%>";
        providerData['<%=prov_no%>'].fax = "<%=Encode.forHtmlContent(providerItem.getClinicFax())%>";

        <%	}
}

ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);
List<Program> programList = programDao.getAllActivePrograms();

if (OscarProperties.getInstance().getBooleanProperty("consultation_program_letterhead_enabled", "true")) {
	if (programList != null) {
		for (Program program : programList) {
			String progNo = "prog_" + program.getId();
%>
        providerData['<%=progNo %>'] = {};
        providerData['<%=progNo %>'].address = '<%=(program.getAddress() != null && !program.getAddress().trim().isEmpty()) ? Encode.forHtmlContent(program.getAddress()) : (Encode.forHtmlContent(clinic.getClinicAddress()) + " " + Encode.forHtmlContent(clinic.getClinicCity()) + " " + Encode.forHtmlContent(clinic.getClinicProvince()) + " " + Encode.forHtmlContent(clinic.getClinicPostal())) %>';
        providerData['<%=progNo %>'].phone = '<%=(program.getPhone() != null && !program.getPhone().trim().isEmpty()) ? Encode.forHtmlContent(program.getPhone()) : Encode.forHtmlContent(clinic.getClinicPhone()) %>';
        providerData['<%=progNo %>'].fax = '<%=(program.getFax() != null && !program.getFax().trim().isEmpty()) ? Encode.forHtmlContent(program.getFax()) : Encode.forHtmlContent(clinic.getClinicFax()) %>';
        <%
		}
	}
} %>
        console.log(providerData);

        function switchProvider(value) {
            if (value === -1) {
                document.getElementById("letterheadName").value = value;
                document.getElementById("letterheadAddress").value = '<%=Encode.forHtmlAttribute(clinic.getClinicAddress()) + " " + Encode.forHtmlAttribute(clinic.getClinicCity()) + " " + Encode.forHtmlAttribute(clinic.getClinicProvince()) + " " + Encode.forHtmlAttribute(clinic.getClinicPostal()) %>';
                document.getElementById("letterheadAddressSpan").textContent = '<%=Encode.forHtmlContent(clinic.getClinicAddress()) + " " + Encode.forHtmlContent(clinic.getClinicCity()) + " " + Encode.forHtmlContent(clinic.getClinicProvince()) + " " + Encode.forHtmlContent(clinic.getClinicPostal()) %>';
                document.getElementById("letterheadPhone").value = "<%=Encode.forHtmlAttribute(clinic.getClinicPhone()) %>";
                document.getElementById("letterheadPhoneSpan").textContent = "<%=Encode.forHtmlContent(clinic.getClinicPhone()) %>";
                document.getElementById("letterheadFax").value = "<%=Encode.forHtmlAttribute(clinic.getClinicFax()) %>";

                document.getElementById("letterheadFaxSpan").textContent = "<%=Encode.forHtmlAttribute(clinic.getClinicFax()) %>";

                let faxAccountOptions = document.getElementById("faxAccount");
                if (faxAccountOptions) {
                    faxAccountOptions.value = "<%=Encode.forHtmlAttribute(clinic.getClinicFax()) %>".replace(/[^0-9.]/g, '');
                    for(let i = 0; i < faxAccountOptions.options.length; i++) {
                        let option = faxAccountOptions.options[i];
                        if(option.value === "<%=clinic.getClinicFax() %>".replace(/[^0-9.]/g, '')) {
                            faxAccountOptions.value = "<%=clinic.getClinicFax()%>".replace(/[^0-9.]/g, '');
                            break;
                        }
                    }
                }
            } else {
                let origValue = value;
                value = value.replace(/[^A-Za-z0-9]+/g, '');
                if (typeof providerData["prov_" + value.toString()] != "undefined") {
                    value = "prov_" + value;
                }
                document.getElementById("letterheadName").value = origValue;
                document.getElementById("letterheadAddress").value = providerData[value]['address'];
                document.getElementById("letterheadAddressSpan").textContent = providerData[value]['address'];
                document.getElementById("letterheadPhone").value = providerData[value]['phone'];
                document.getElementById("letterheadPhoneSpan").textContent = providerData[value]['phone'];
                document.getElementById("letterheadFax").value = providerData[value]['fax'];
                document.getElementById("letterheadFaxSpan").textContent = providerData[value]['fax'];

                let faxAccountOptions = document.getElementById("faxAccount");
                if (faxAccountOptions) {
                    for(let option in faxAccountOptions.options) {
                        if(faxAccountOptions.options[option].value === providerData[value]['fax'].replace(/[^0-9.]/g, '')) {
                            faxAccountOptions.value = providerData[value]['fax'].replace(/[^0-9.]/g, '');
                            break;
                        }
                    }
                }
            }
        }

        <%
String signatureRequestId=DigitalSignatureUtils.generateSignatureRequestId(loggedInInfo.getLoggedInProviderNo());
String imageUrl=request.getContextPath()+"/imageRenderingServlet?source="+ImageRenderingServlet.Source.signature_preview.name()+"&"+DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY+"="+signatureRequestId;
String storedImgUrl=request.getContextPath()+"/imageRenderingServlet?source="+ImageRenderingServlet.Source.signature_stored.name()+"&digitalSignatureId=";
%>

        var POLL_TIME = 1500;
        var counter = 0;

        function refreshImage() {
            counter = counter + 1;
            document.getElementById('signatureImgTag').src = '<%=imageUrl%>&rand=' + counter;
            document.getElementById('signatureImg').value = '<%=signatureRequestId%>';
        }

        function showSignatureImage() {
            if (document.getElementById('signatureImg') != null && document.getElementById('signatureImg').value.length > 0) {

                document.getElementById('signatureImgTag').src = "<%=storedImgUrl %>" + document.getElementById('signatureImg').value;
                document.getElementById('newSignature').value = "false";
                document.getElementById("signatureFrame").style.display = "none";
                document.getElementById('signatureShow').style.display = "block";
            }

            return true;
        }

        <%
String userAgent = request.getHeader("User-Agent");
String browserType = "";
if (userAgent != null) {
	if (userAgent.toLowerCase().indexOf("ipad") > -1) {
		browserType = "IPAD";
	} else {
		browserType = "ALL";
	}
}
%>

        var isSignatureDirty = false;
        var isSignatureSaved = <%= consultUtil.signatureImg != null && !"".equals(consultUtil.signatureImg) ? "true" : "false" %>;

        function signatureHandler(e) {
            isSignatureDirty = e.isDirty;
            isSignatureSaved = e.isSave;
            <%
	if (props.isConsultationFaxEnabled()) { //
	%>
            updateFaxButton();
            <% } %>
            if (e.isSave) {
                refreshImage();
                document.getElementById('newSignature').value = "true";
            } else {
                document.getElementById('newSignature').value = "false";
            }
        }

        var requestIdKey = "<%=signatureRequestId %>";

        function AddOtherFaxProvider() {
            var name = jQuery("#searchHealthCareTeamInput").val();
            var fax = jQuery("#copytoSpecialistFax").val();
            if (checkPhone(fax)) {
                _AddOtherFax(name, fax);
                jQuery("#searchHealthCareTeamInput").val("");
                jQuery("#copytoSpecialistFax").val("");
            } else {
                alert("The fax number you entered is invalid.");
            }
        }

        function AddOtherFax() {
            var number = jQuery("#otherFaxInput").val();
            if (checkPhone(number)) {
                _AddOtherFax(number, number);
            } else {
                alert("The fax number you entered is invalid.");
            }
        }

        function _AddOtherFax(name, number) {
            var remove = "<a href='javascript:void(0);' onclick='removeRecipient(this)'>remove</a>";
            var rvalue = {};
            rvalue.name = name;
            rvalue.fax = number;
            var html = "<tr><td class='tite1'>" + name + "</td><td class='tite1'>" + number + "</td><td class='tite1'>" + remove
                + "<input type='hidden' id='faxRecipients' name='faxRecipients' value='" + JSON.stringify(rvalue) + "' /> </td></tr>";
            jQuery("#addFaxRecipient").append(jQuery(html));
            updateFaxButton();
        }

        function checkPhone(str) {
            str = str.trim().replace(/\D/g, '');
            var phone = /^((\+\d{1,3}(-| )?\(?\d\)?(-| )?\d{1,5})|(\(?\d{2,6}\)?))(-| )?(\d{3,4})(-| )?(\d{4})(( x| ext)\d{1,5}){0,1}$/
            if (str.match(phone)) {
                return true;
            } else {
                return false;
            }
        }

        function removeRecipient(el) {
            var el = jQuery(el);
            if (el) {
                el.parent().parent().remove();
            } else {
                alert("Unable to remove recipient.");
            }
        }

        function hasFaxNumber() {
            return specialistFaxNumber.length > 0 || (jQuery("#faxRecipients").val() != null && jQuery("#faxRecipients").val() != "undefined");
        }

        function updateFaxButton() {
            var disabled = !hasFaxNumber();
            document.getElementById("fax_button").disabled = disabled;
            document.getElementById("fax_button2").disabled = disabled;
        }

        // If the user clicks the 'Print Preview' button, ensure that their unsaved changes are preserved, allowing them to stay on the same page. Achieve this by making an AJAX call.
        function getConsultFormPrintPreview(form) {
            form.submission.value = "And Print Preview";
            jQuery.ajax({
                type: "POST",
                url: "${ pageContext.request.contextPath }/oscarEncounter/RequestConsultation.do",
                data: form.serialize(),
                dataType: "json",
                success: function (data) {
                    HideSpin();
                    if (data.errorMessage) {
                        alert(data.errorMessage.replace(/\\n/g, '\n'));
                        return;
                    }
                    showPreview(data.consultPDF, data.consultPDFName);
                },
                error: function (xhr, status, error) {
                    HideSpin();
                    alert("Preview request failed: " + status + ", " + error);
                }
            });
        }

        function showPreview(base64PDF, pdfName) {
            const pdfData = new Uint8Array(atob(base64PDF).split('').map(char => char.charCodeAt(0)));
            const pdfBlob = new Blob([pdfData], {type: 'application/pdf'});
            const downloadLink = document.createElement('a');
            downloadLink.href = URL.createObjectURL(pdfBlob);
            downloadLink.download = pdfName;
            downloadLink.click();
            URL.revokeObjectURL(downloadLink.href);
        }

        function clearAppointmentDateAndTime() {
            document.EctConsultationFormRequest2Form.appointmentDate.value = "";
            document.EctConsultationFormRequest2Form.appointmentHour.options.selectedIndex = 0;
            document.EctConsultationFormRequest2Form.appointmentMinute.options.selectedIndex = 0;
            document.EctConsultationFormRequest2Form.appointmentPm.options.selectedIndex = 0;
        }
    </script>

    <%=WebUtils.popErrorMessagesAsAlert(session)%>

    <body topmargin="0" leftmargin="0" vlink="#0000FF"
          onload="window.focus();disableDateFields();disableEditing();showSignatureImage();">
    <jsp:include page="/images/spinner.jsp" flush="true"/>
    <%
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %>
    <form id="EctConsultationFormRequest2Form" name="EctConsultationFormRequest2Form" style="consultationRequestForm" action="${pageContext.request.contextPath}/oscarEncounter/RequestConsultation.do"
                method="post" onsubmit="alert('HTHT'); return false;">
        <%
            EctConsultationFormRequest2Form thisForm = (EctConsultationFormRequest2Form) request.getAttribute("EctConsultationFormRequest2Form");
            if (thisForm == null) {
                thisForm = new EctConsultationFormRequest2Form();
                request.setAttribute("EctConsultationFormRequest2Form", thisForm);
            }

            if (thisForm != null) {
                if (requestId != null && !"null".equals(requestId) && !requestId.isEmpty()) {
                    EctViewRequest2Action.fillFormValues(LoggedInInfo.getLoggedInInfoFromSession(request), thisForm, new Integer(requestId));
                    thisForm.setSiteName(consultUtil.siteName);
                    defaultSiteName = consultUtil.siteName;

                } else if (segmentId != null) {
                    EctViewRequest2Action.fillFormValues(thisForm, segmentId);
                    thisForm.setSiteName(consultUtil.siteName);
                    defaultSiteName = consultUtil.siteName;
                } else if (request.getAttribute("validateError") == null) {
                    //  new request
                    if (demo != null) {
                        RxInformation RxInfo = new RxInformation();
                        EctViewRequest2Action.fillFormValues(thisForm, consultUtil);

                        if ("true".equalsIgnoreCase(props.getProperty("CONSULTATION_AUTO_INCLUDE_ALLERGIES", "true"))) {
                            String allergies = RxInfo.getAllergies(loggedInInfo, demo);
                            thisForm.setAllergies(allergies);
                        }

                        if ("true".equalsIgnoreCase(props.getProperty("CONSULTATION_AUTO_INCLUDE_MEDICATIONS", "true"))) {
                            if (props.getProperty("currentMedications", "").equalsIgnoreCase("otherMedications")) {
                                EctInformation EctInfo = new EctInformation(loggedInInfo, demo);
                                thisForm.setCurrentMedications(EctInfo.getFamilyHistory());
                            } else {
                                thisForm.setCurrentMedications(RxInfo.getCurrentMedication(demo));
                            }
                        }

                        team = consultUtil.getProviderTeam(consultUtil.mrp);
                    }

                    thisForm.setStatus("1");

                    thisForm.setSendTo(team);

                    if (bMultisites) {
                        thisForm.setSiteName(defaultSiteName);
                    }
                }
            }

            if (thisForm.iseReferral()) {
        %>
        <SCRIPT LANGUAGE="JavaScript">
            disableFields = true;
        </SCRIPT>
        <%
            }


        %>

        <% if (!props.isConsultationFaxEnabled() || !OscarProperties.getInstance().isPropertyActive("consultation_dynamic_labelling_enabled")) { %>
        <input type="hidden" name="providerNo" value="<%=providerNo%>">
        <% } %>
        <input type="hidden" name="demographicNo" id="demographicNo" value="<%=demo%>">
        <input type="hidden" name="requestId" id="requestId" value="<%=requestId%>">
        <input type="hidden" name="ext_appNo" value="<%=request.getParameter("appNo") %>">
        <input type="hidden" name="source"
               value="<%=(requestId!=null)?thisForm.getSource():request.getParameter("source") %>">
        <input type="hidden" id="saved" value="false">
        <input type="hidden" id="contextPath" value="${pageContext.request.contextPath}">

        <table class="MainTable" id="scrollNumber1" name="encounterTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn">Consultation</td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar">
                        <tr>
                            <td class="Header"
                                style="padding-left: 2px; padding-right: 2px; border-right: 2px solid #003399; text-align: left; font-size: 80%; font-weight: bold; width: 100%;"
                            >
                                <h2>
                                    <%=thisForm.getPatientName()%> <%=thisForm.getPatientSex()%> <%=thisForm.getPatientAge()%>
                                </h2>
                            </td>
                            <% if ("ocean".equals(props.get("cme_js"))) { %>
                            <td>
                                <span id="ocean" style="display:none"></span>
                                <% if (requestId == null) { %>
                                <span id="oceanReferButton" class="oceanRefer"></span>
                            </td>
                            <% }
                            }%>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr style="vertical-align: top">
                <td class="MainTableLeftColumn">
                    <table>
                        <tr>
                            <td colspan="2">
                                <table>
                                    <tr>
                                        <td class="stat" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgCreated"/></td>
                                    </tr>
                                    <tr>
                                        <td class="stat" colspan="2"><%=thisForm.getProviderName()%>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td class="tite4" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgStatus"/>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="2">
                                <table>
                                    <tr>
                                        <td class="stat"><input type="radio" name="status" value="1" <%="1".equals(thisForm.getStatus()) ? "checked" : ""%>/>
                                        </td>
                                        <td class="stat"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgNoth"/>:
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="2">
                                <table>
                                    <tr>
                                        <td class="stat"><input type="radio" name="status" value="2" <%="2".equals(thisForm.getStatus()) ? "checked" : ""%>/>
                                        </td>
                                        <td class="stat"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgSpecCall"/>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="2">
                                <table>
                                    <tr>
                                        <td class="stat"><input type="radio" name="status" value="3" <%="3".equals(thisForm.getStatus()) ? "checked" : ""%>/>
                                        </td>
                                        <td class="stat"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgPatCall"/>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <%
                            if (thisForm.iseReferral()) {
                        %>
                        <tr>
                            <td colspan="2">
                                <table>
                                    <tr>
                                        <td class="stat"><input type="radio" name="status" value="5" <%="5".equals(thisForm.getStatus()) ? "checked" : ""%>/>
                                        </td>
                                        <td class="stat"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgBookCon"/>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <%
                            }
                        %>
                        <tr>
                            <td colspan="2">
                                <table>
                                    <tr>
                                        <td class="stat"><input type="radio" name="status" value="4" <%="4".equals(thisForm.getStatus()) ? "checked" : ""%>/>
                                        </td>
                                        <td class="stat"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgCompleted"/></td>
                                    </tr>
                                </table>
                            </td>
                        </tr>

                        <tr>
                            <td colspan="2">
                                <table id="attachedDocumentTable">
                                    <tr>
                                        <td>

                                            <%
                                                if (thisForm.iseReferral()) {
                                            %>
                                                <%-- <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.attachDoc"/> --%>
                                            <a href="javascript:void(0);" id="attachDocumentPanelBtn"
                                               title="Add Attachment"
                                               data-poload="${ ctx }/previewDocs.do?method=fetchConsultDocuments&amp;demographicNo=<%=demo%>&amp;requestId=<%=requestId%>">
                                                Manage Attachments
                                            </a>
                                            <input type="hidden" id="isOceanEReferral"
                                                   value="<%=thisForm.iseReferral()%>"/>
                                            <%
                                            } else { %>
                                            <a href="javascript:void(0);" id="attachDocumentPanelBtn"
                                               title="Add Attachment"
                                               data-poload="${ ctx }/previewDocs.do?method=fetchConsultDocuments&amp;demographicNo=<%=demo%>&amp;requestId=<%=requestId%>">
                                                Manage Attachments
                                            </a>

                                            <% } %>

                                        </td>
                                    </tr>

                                    <tr>
                                        <td>
                                            <table id="attachedEFormsTable">
                                                <tr>
                                                    <td><h3>eForms</h3></td>
                                                </tr>
                                                <c:forEach items="${ attachedEForms }" var="attachedEForm">
                                                    <tr id="entry_eFormNo${ attachedEForm.id }">
                                                        <td>
                                                            <c:out value="${ attachedEForm.formName }"/>
                                                            <input name="eFormNo" value="${ attachedEForm.id }"
                                                                   id="delegate_eFormNo${ attachedEForm.id }"
                                                                   class="delegateAttachment" type="hidden">
                                                        </td>
                                                    </tr>
                                                </c:forEach>
                                            </table>
                                        </td>
                                    </tr>

                                    <tr>
                                        <td>
                                            <table id="attachedDocumentsTable">
                                                <tr>
                                                    <td><h3>Documents</h3></td>
                                                </tr>
                                                <c:forEach items="${ attachedDocuments }" var="attachedDocument">
                                                    <tr id="entry_docNo${ attachedDocument.docId }">
                                                        <td>
                                                            <c:out value="${ attachedDocument.description }"/>
                                                            <input name="docNo" value="${ attachedDocument.docId }"
                                                                   id="delegate_docNo${ attachedDocument.docId }"
                                                                   class="delegateAttachment" type="hidden">
                                                        </td>
                                                    </tr>
                                                </c:forEach>
                                            </table>
                                        </td>
                                    </tr>

                                    <tr>
                                        <td>
                                            <table id="attachedLabsTable">
                                                <tr>
                                                    <td><h3>Labs</h3></td>
                                                </tr>
                                                <c:forEach items="${ attachedLabs }" var="attachedLab">
                                                    <tr id="entry_labNo${ attachedLab.segmentID }">
                                                        <td>
                                                            <c:set var="labName"
                                                                   value="${ fn:trim(attachedLab.label) != '' ? attachedLab.label : attachedLab.discipline}"/>
                                                            <c:if test="${empty labName}"><c:set var="labName"
                                                                                                 value="UNLABELLED"/></c:if>
                                                            <c:out value="${attachedLab.description} ${ labName }"/>
                                                            <input name="labNo" value="${ attachedLab.segmentID }"
                                                                   id="delegate_labNo${ attachedLab.segmentID }"
                                                                   class="delegateAttachment" type="hidden">
                                                        </td>
                                                    </tr>
                                                </c:forEach>
                                            </table>
                                        </td>
                                    </tr>

                                    <tr>
                                        <td>
                                            <table id="attachedHRMDocumentsTable">
                                                <tr>
                                                    <td><h3>HRM</h3></td>
                                                </tr>
                                                <c:forEach items="${ attachedHRMDocuments }" var="attachedHrm">
                                                    <tr id="entry_hrmNo${ attachedHrm['id'] }">
                                                        <td>
                                                            <c:out value="${ attachedHrm['name'] }"/>
                                                            <input name="hrmNo" value="${ attachedHrm['id'] }"
                                                                   id="delegate_hrmNo${ attachedHrm['id'] }"
                                                                   class="delegateAttachment" type="hidden">
                                                        </td>
                                                    </tr>
                                                </c:forEach>
                                            </table>
                                        </td>
                                    </tr>

                                    <tr>
                                        <td>
                                            <table id="attachedFormsTable">
                                                <tr>
                                                    <td><h3>Forms</h3></td>
                                                </tr>
                                                <c:forEach items="${ attachedForms }" var="attachedForm">
                                                    <tr id="entry_formNo${ attachedForm.formId }"
                                                        data-formName="${ attachedForm.formName }"
                                                        data-formDate="${ attachedForm.getEdited() }">
                                                        <td>
                                                            <c:out value="${ attachedForm.formName }"/>
                                                            <input name="formNo" value="${ attachedForm.formId }"
                                                                   id="delegate_formNo${ attachedForm.formId }"
                                                                   class="delegateAttachment" type="hidden">
                                                        </td>
                                                    </tr>
                                                </c:forEach>
                                            </table>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </td>
                <td class="MainTableRightColumn">
                    <table cellpadding="0" cellspacing="2"
                           style="border-collapse: collapse" bordercolor="#111111" width="100%"
                           height="100%" border=1>
                        <% if (requestId != null && "ocean".equals(props.get("cme_js"))) {
                            ConsultationRequestExtDao consultationRequestExtDao = SpringUtils.getBean(ConsultationRequestExtDao.class);
                            Integer consultId = Integer.parseInt(requestId);
                            String eReferralRef = consultationRequestExtDao.getConsultationRequestExtsByKey(consultId, ConsultationRequestExtKey.EREFERRAL_REF.getKey());
                            if (eReferralRef != null) {
                        %>
                        <input id="ereferral_ref" type="hidden" value="<%= Encode.forHtmlAttribute(eReferralRef) %>"/>
                        <span id="editOnOcean" class="oceanRefer"></span>
                        <% }
                        } %>
                        <!----Start new rows here-->
                        <% if (thisForm.geteReferralId() == null) { %>
                        <tr>
                            <td class="tite4 controlPanel" colspan=2>

                                <% if (request.getAttribute("id") != null) { %>
                                <input name="update" type="button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnUpdate"/>"
                                       onclick="return checkForm('Update Consultation Request','EctConsultationFormRequest2Form');"/>
                                <input name="updateAndPrint" type="button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnUpdateAndPrint"/>"
                                       onclick="return checkForm('Update Consultation Request And Print Preview','EctConsultationFormRequest2Form');"/>
                                <input name="printPreview" type="button" value="Print Preview"
                                       onclick="return checkForm('And Print Preview','EctConsultationFormRequest2Form');"/>

                                <c:if test="${EctConsultationFormRequest2Form.eReferral == true}">
                                    <input name="updateAndSendElectronicallyTop" type="button"
                                           value="<fmt:message key='oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnUpdateAndSendElectronicReferral'/>"
                                           onclick="return checkForm('Update_esend', 'EctConsultationFormRequest2Form');"/>
                                </c:if>

                                <oscar:oscarPropertiesCheck value="yes" property="consultation_fax_enabled">
                                    <input id="fax_button" name="updateAndFax" type="button"
                                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnUpdateAndFax"/>"
                                           onclick="return checkForm('Update And Fax','EctConsultationFormRequest2Form');"/>
                                </oscar:oscarPropertiesCheck>

                                <% } else { %>
                                <input name="submitSaveOnly" type="button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnSubmit"/>"
                                       onclick="return checkForm('Submit Consultation Request','EctConsultationFormRequest2Form'); "/>
                                <input name="submitAndPrint" type="button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnSubmitAndPrint"/>"
                                       onclick="return checkForm('Submit Consultation Request And Print Preview','EctConsultationFormRequest2Form'); "/>

                                <c:if test="${EctConsultationFormRequest2Form.eReferral == true}">
                                    <input name="submitAndSendElectronicallyTop" type="button"
                                           value="<fmt:message key='oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnSubmitAndSendElectronicReferral'/>"
                                           onclick="return checkForm('Submit_esend', 'EctConsultationFormRequest2Form');"/>
                                </c:if>


                                <oscar:oscarPropertiesCheck value="yes" property="consultation_fax_enabled">
                                    <input id="fax_button" name="submitAndFax" type="button"
                                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnSubmitAndFax"/>"
                                           onclick="return checkForm('Submit And Fax','EctConsultationFormRequest2Form');"/>
                                </oscar:oscarPropertiesCheck>
                                <c:if test="${EctConsultationFormRequest2Form.eReferral == true}">
                                    <input type="button" value="Send eResponse"
                                           onclick="document.getElementById('saved').value='true'; document.location='${thisForm.oruR01UrlString(request)}'"/>
                                </c:if>

                                <% } %>
                            </td>
                        </tr>
                        <% } %>
                        <tr class="consultDemographicData">
                            <td>
                                <% // Determine if curUser has selected a default practitioner in preferences
                                    UserProperty refPracProp = userPropertyDAO.getProp(providerNo,  UserProperty.DEFAULT_REF_PRACTITIONER);
                                    String refPrac = "";
                                    if (refPracProp != null && refPracProp.getValue() != null) {
                                        refPrac = refPracProp.getValue();
                                    }
                                %>

                                <table>
                                    <% if (props.isConsultationFaxEnabled() && OscarProperties.getInstance().isPropertyActive("consultation_dynamic_labelling_enabled")) { %>
                                    <tr>
                                        <td class="tite4" style="width:30%"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgAssociated2"/></td>
                                        <td class="tite1" style="width:70%">

                                            <select name="providerNo" onchange="switchProvider(this.value)">
                                                <%
                                                    for (Provider p : prList) {
                                                        if (p.getProviderNo().compareTo("-1") != 0) {
                                                %>
                                                <option value="<%=p.getProviderNo() %>" <%=((consultUtil.providerNo != null && consultUtil.providerNo.equalsIgnoreCase(p.getProviderNo())) || (consultUtil.providerNo == null && referringProviderDefault.equalsIgnoreCase(p.getProviderNo())) ? "selected" : "") %>>
                                                    <%=Encode.forHtmlContent(p.getFirstName().replace("Dr.", "")) %>&nbsp;<%=Encode.forHtmlContent(p.getSurname()) %>
                                                </option>
                                                <% }

                                                }
                                                %>
                                            </select>
                                        </td>
                                    </tr>
                                    <% } %>
                                    <tr>
                                        <td class="tite4">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formRefDate"/>
                                        </td>

                                        <oscar:oscarPropertiesCheck value="false"
                                                                    property="CONSULTATION_LOCK_REFERRAL_DATE">
                                            <td class="tite3">
                                                <img alt="calendar" id="referalDate_cal" src="<%= request.getContextPath() %>/images/cal.gif"/>
                                                <%
                                                    if (request.getAttribute("id") != null) {
                                                %>
                                                <input type="text" id="referalDate" name="referalDate"
                                                           ondblclick="this.value='';" value="<%=Encode.forHtmlAttribute(thisForm.getReferalDate())%>"/>
                                                <%
                                                } else {
                                                %>
                                                <input type="text" id="referalDate" name="referalDate"
                                                           ondblclick="this.value='';" value="<%=formattedDate%>"/>
                                                <%
                                                    }
                                                %>
                                            </td>
                                        </oscar:oscarPropertiesCheck>

                                        <oscar:oscarPropertiesCheck value="true"
                                                                    property="CONSULTATION_LOCK_REFERRAL_DATE">

                                            <td class="tite3">
                                                <input type="text" id="referalDate" name="referalDate" readonly="true"
                                                           value="<%=formattedDate%>"/>
                                            </td>

                                        </oscar:oscarPropertiesCheck>

                                    </tr>
                                    <oscar:oscarPropertiesCheck value="false"
                                                                property="ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS"
                                                                defaultVal="false">
                                        <tr>
                                            <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formService"/>
                                            </td>
                                            <td class="tite3">
                                                <% if (thisForm.iseReferral() && !thisForm.geteReferralService().isEmpty()) { %>
                                                <%= thisForm.geteReferralService() %>
                                                <% } else { %>
                                                <select id="service" name="service"
                                                             onchange="fillSpecialistSelect(this);"></select>
                                                <% } %>
                                            </td>
                                        </tr>
                                    </oscar:oscarPropertiesCheck>
                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formCons"/>
                                        </td>
                                        <td class="tite3">
                                            <% if (thisForm.iseReferral()) { %>

                                            <%=thisForm.getProfessionalSpecialistName()%>

                                            <% } else if (OscarProperties.getInstance().getBooleanProperty("ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS", "true")) { %>

                                            <select name="specialist" id="specialist" onchange="getSpecialist(this)">
                                                <c:forEach items="${ healthCareTeam }" var="contact" varStatus="loop">
                                                    <option value="${ contact.id }" ${ specialist eq contact.id ? 'selected' : ''} >
                                                            ${ contact.details.formattedName } ( ${ contact.role } )
                                                    </option>
                                                </c:forEach>
                                            </select>

                                            <% } else { %>

                                            <span id="consult-disclaimer"
                                                  title="When consult was saved this was the saved consultant but is no longer on this specialist list."
                                                  style="display:none;font-size:24px;">*</span>
                                            <select id="specialist" name="specialist" size="1"
                                                         onchange="onSelectSpecialist(this)"></select>

                                            <%} // end specialist list condition block %>
                                        </td>
                                    </tr>
                                    <oscar:oscarPropertiesCheck value="true"
                                                                property="ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS"
                                                                defaultVal="false">
                                        <tr>
                                            <td class="tite4">
                                                <input type="hidden" id="hctService" name="service" value="0"/>
                                            </td>
                                            <td class="tite4" style="font-size:11px;">
                                                <a href="javascript:void(0);"
                                                   onclick="popupPage(500,700,'${ctx}/demographic/Contact.do?method=manageContactList&contactList=HCT&view=detached&demographic_no=<%=demo%>' ); return false;">
                                                    edit Health Care Team
                                                </a>
                                            </td>
                                        </tr>
                                    </oscar:oscarPropertiesCheck>

                                    <tr>
                                        <td class="tite4">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formInstructions"/> </br>
                                            <br>
                                            <button type="button" id="eFormButton" style="display: none"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.eFormReferralInstructions"/></button>
                                        </td>
                                        <td class="tite3">
                                            <textarea id="annotation" style="color: blue;" rows="4" readonly></textarea>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formUrgency"/></td>
                                        <td class="tite3">
                                            <select name="urgency" id="urgency">
                                                <option value="2" <%="2".equals(thisForm.getUrgency()) ? "selected" : ""%>>
                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgNUrgent"/>
                                                </option>
                                                <option value="1" <%="1".equals(thisForm.getUrgency()) ? "selected" : ""%>>
                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgUrgent"/>
                                                </option>
                                                <option value="3" <%="3".equals(thisForm.getUrgency()) ? "selected" : ""%>>
                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgReturn"/>
                                                </option>
                                            </select>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="tite4">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formPhone"/>
                                        </td>
                                        <td class="tite3"><input readonly type="text" name="phone" class="righty"
                                                                 value="<%=thisForm.getProfessionalSpecialistPhone()%>"/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="tite4">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formFax"/>
                                            <c:if test="${ not empty consultUtil.specialistFaxLog.status }">
                                                <span style="font-size:80%;color:red;">Status: <c:out
                                                        value="${ consultUtil.specialistFaxLog.status }"/></span>
                                            </c:if>
                                        </td>
                                        <td class="tite3">
                                            <input readonly type="text" name="fax" class="righty"/>
                                        </td>
                                    </tr>

                                    <tr>
                                        <td class="tite4">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formAddr"/>
                                        </td>
                                        <td class="tite3">
                                            <textarea readonly name="address"
                                                      rows="5"><%=thisForm.getProfessionalSpecialistAddress()%></textarea>
                                        </td>
                                    </tr>

                                    <oscar:oscarPropertiesCheck defaultVal="false" value="true"
                                                                property="CONSULTATION_APPOINTMENT_INSTRUCTIONS_LOOKUP">
                                        <tr>
                                            <td class="tite4">
                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.appointmentInstr"/>
                                            </td>
                                            <td class="tite3">
                                                <select name="appointmentInstructions"
                                                             id="appointmentInstructions">
                                                    <option value=""></option>
                                                    <c:forEach items="${ appointmentInstructionList.items }"
                                                               var="appointmentInstruction">
                                                        <%-- Ensure that only active items are shown --%>
                                                        <c:if test="${ appointmentInstruction.active }">
                                                            <option value="${ appointmentInstruction.value }" ${ EctConsultationFormRequest2Form.appointmentInstructions eq appointmentInstruction.value ? 'selected' : '' }>
                                                                <c:out value="${ appointmentInstruction.label }"/>
                                                            </option>
                                                        </c:if>
                                                    </c:forEach>
                                                </select>
                                            </td>
                                        </tr>
                                    </oscar:oscarPropertiesCheck>
                                    <oscar:oscarPropertiesCheck defaultVal="false" value="true"
                                                                property="CONSULTATION_PATIENT_WILL_BOOK">
                                        <tr>
                                            <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formPatientBook"/></td>
                                            <td class="tite3"><input type="checkbox" name="patientWillBook" value="1" onclick="disableDateFields()" /></td>
                                        </tr>
                                    </oscar:oscarPropertiesCheck>


                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnAppointmentDate"/>
                                        </td>
                                        <td class="tite3"><img alt="calendar" id="appointmentDate_cal"
                                                               src="<%= request.getContextPath() %>/images/cal.gif">
                                            <input type="text" id="appointmentDate" name="appointmentDate"
                                                       readonly="true" ondblclick="this.value='';" value="<%=Encode.forHtmlAttribute(thisForm.getAppointmentDate())%>"/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formAppointmentTime"/>
                                        </td>
                                        <td class="tite3">
                                            <table>
                                                <tr>
                                                    <td><select name="appointmentHour" id="appointmentHour">
                                                        <option value="-1"></option>
                                                        <%
                                                            for (int i = 1; i < 13; i = i + 1) {
                                                                String hourOfday = Integer.toString(i);
                                                                String selectedHour = (hourOfday.equals(thisForm.getAppointmentHour())) ? "selected" : "";
                                                        %>
                                                        <option value="<%=hourOfday%>" <%=selectedHour%>><%=hourOfday%>
                                                        </option>
                                                        <%
                                                            }
                                                        %>
                                                    </select></td>
                                                    <td><select name="appointmentMinute" id="appointmentMinute">
                                                        <option value="-1"></option>
                                                        <%
                                                            for (int i = 0; i < 60; i = i + 1) {
                                                                String minuteOfhour = Integer.toString(i);
                                                                if (i < 10) {
                                                                    minuteOfhour = "0" + minuteOfhour;
                                                                }
                                                                String selectedMinute = (String.valueOf(i).equals(thisForm.getAppointmentMinute())) ? "selected" : "";
                                                        %>
                                                        <option value="<%=String.valueOf(i)%>" <%=selectedMinute%>><%=minuteOfhour%>
                                                        </option>
                                                        <%
                                                            }
                                                        %>
                                                    </select></td>
                                                    <td><select name="appointmentPm" id="appointmentPm">
                                                        <option value="AM" <%="AM".equals(thisForm.getAppointmentPm()) ? "selected" : ""%>>AM</option>
                                                        <option value="PM" <%="PM".equals(thisForm.getAppointmentPm()) ? "selected" : ""%>>PM</option>
                                                    </select></td>
                                                    <td><input type="button" value="Clear Date & Time"
                                                               onclick="clearAppointmentDateAndTime()"/></td>
                                                </tr>
                                            </table>

                                        </td>
                                    </tr>
                                    <%if (bMultisites) { %>
                                    <tr>
                                        <td class="tite4">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.siteName"/>
                                        </td>
                                        <td>
                                            <select name="siteName" id="siteName"
                                                         onchange='this.style.backgroundColor=this.options[this.selectedIndex].style.backgroundColor'>
                                                <% for (int i = 0; i < vecAddressName.size(); i++) {
                                                    String te = vecAddressName.get(i);
                                                    String bg = bgColor.get(i);
                                                    if (te.equals(defaultSiteName))
                                                        defaultSiteId = siteIds.get(i);
                                                %>
                                                <option value="<%=te%>"
                                                             style='<%="background-color: "+bg%>'><%=te%>
                                                </option>
                                                <% }%>
                                            </select>
                                        </td>
                                    </tr>
                                    <%} %>
                                </table>
                            </td>
                            <td valign="top">
                                <table height="100%" width="100%" bgcolor="white">
                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgPatient"/>
                                        </td>
                                        <td class="tite1"><a href="javascript:void(0);"
                                                             onClick="popupAttach(600,900,'<%=request.getContextPath()%>/demographic/demographiccontrol.jsp?demographic_no=<%=demo%>&displaymode=edit&dboperation=search_detail')"><%=thisForm.getPatientName()%>
                                        </a></td>
                                    </tr>
                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgAddress"/>
                                        </td>
                                        <td class="tite1"><%=thisForm.getPatientAddress().replace("null", "")%>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgPhone"/>
                                        </td>
                                        <td class="tite1"><%=thisForm.getPatientPhone()%>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgWPhone"/>
                                        </td>
                                        <td class="tite1"><%=thisForm.getPatientWPhone()%>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgCellPhone"/>
                                        </td>
                                        <td class="tite1"><%=thisForm.getPatientCellPhone()%>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgEmail"/>
                                        </td>
                                        <td class="tite1"><%=thisForm.getPatientEmail()%>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgBirthDate"/>
                                        </td>
                                        <td class="tite1"><%=thisForm.getPatientDOB()%>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgSex"/>
                                        </td>
                                        <td class="tite1"><%=thisForm.getPatientSex()%>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgHealthCard"/>
                                        </td>
                                        <td class="tite1"><%=thisForm.getPatientHealthNum()%><%=thisForm.getPatientHealthCardVersionCode()%><%=thisForm.getPatientHealthCardType()%>
                                        </td>
                                    </tr>
                                    <tr id="conReqSendTo">
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgSendTo"/>
                                        </td>
                                        <td class="tite3"><select name="sendTo" id="sendTo">
                                            <option value="-1" <%="-1".equals(thisForm.getSendTo()) ? "selected" : ""%>>---- <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgTeams"/> ----</option>
                                            <%
                                                for (int i = 0; i < consultUtil.teamVec.size(); i++) {
                                                    String te = (String) consultUtil.teamVec.elementAt(i);
                                                    String selectedTeam = (te.equals(thisForm.getSendTo())) ? "selected" : "";
                                            %>
                                            <option value="<%=te%>" <%=selectedTeam%>><%=te%>
                                            </option>
                                            <%
                                                }
                                            %>
                                        </select></td>
                                    </tr>

                                    <tr>
                                        <td colspan="2" class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formAppointmentNotes"/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="2" class="tite3"><textarea
                                                name="appointmentNotes"><%=Encode.forHtmlContent(thisForm.getAppointmentNotes())%></textarea></td>
                                    </tr>


                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formLastFollowup"/>
                                        </td>
                                        <td class="tite3">
                                            <img alt="calendar" id="followUpDate_cal" src="<%= request.getContextPath() %>/images/cal.gif"/>
                                            <input type="text" id="followUpDate" name="followUpDate"
                                                       ondblclick="this.value='';" value="<%=thisForm.getFollowUpDate() != null ? Encode.forHtmlAttribute(thisForm.getFollowUpDate()) : ""%>"/>
                                        </td>

                                    </tr>

                                    <%
                                        if (thisForm.getFdid() != null) {
                                    %>
                                    <tr>
                                        <td class="tite4">EForm
                                        </td>
                                        <td class="tite1">
                                            <a href="<%=request.getContextPath()%>/eform/efmshowform_data.jsp?fdid=<%=thisForm.getFdid() %>">Click
                                                to view</a>
                                        </td>
                                    </tr>
                                    <%
                                        }
                                    %>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="2" class="tite4 heading">Letterhead</td>
                        <tr>
                            <td colspan="2">
                                <table>
                                    <tr>

                                        <td class="tite4">
                                            <label for="letterheadName">
                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.letterheadName"/>
                                            </label>
                                        </td>
                                        <td class="tite1">
                                            <select name="letterheadName" id="letterheadName"
                                                    onchange="switchProvider(this.value)">
                                                <option value="<%=Encode.forHtmlAttribute(clinic.getClinicName())%>" <%=(consultUtil.letterheadName != null && consultUtil.letterheadName.equalsIgnoreCase(clinic.getClinicName())) ? "selected" : (lhndType.equals("clinic") ? "selected" : "") %>>
                                                <%=Encode.forHtmlContent(clinic.getClinicName()) %>
                                                </option>
                                                <%
                                                    for (Provider p : prList) {
                                                        if (p.getProviderNo().compareTo("-1") != 0 && (p.getFirstName() != null || p.getSurname() != null)) {
                                                %>
                                                <option value="<%=p.getProviderNo() %>"
                                                        <%=(thisForm.getLetterheadName() != null && !thisForm.getLetterheadName().isEmpty() && thisForm.getLetterheadName().equalsIgnoreCase(p.getProviderNo())) ? "selected" : ((thisForm.getLetterheadName() == null || thisForm.getLetterheadName().isEmpty()) && p.getProviderNo().equalsIgnoreCase(providerDefault) && lhndType.equals("providers") ? "selected" : "") %>>
                                                    <%=Encode.forHtmlContent(p.getSurname())%>
                                                    ,&nbsp;<%=Encode.forHtmlContent(p.getFirstName().replace("Dr.", ""))%>
                                                        </option>
                                                <% }
                                                }

                                                    if (OscarProperties.getInstance().getBooleanProperty("consultation_program_letterhead_enabled", "true")) {
                                                        for (Program p : programList) {
                                                %>
                                                <option value="prog_<%=p.getId() %>" <%=(thisForm.getLetterheadName() != null && thisForm.getLetterheadName().equalsIgnoreCase("prog_" + p.getId()) ? "selected" : "") %>>
                                                    <%=Encode.forHtmlContent(p.getName()) %>
                                                </option>
                                                <% }
                                                }%>
                                            </select>
                                            <%if (props.isConsultationFaxEnabled()) {%>
                                            <div>
                                                <input type="checkbox" id="ext_letterheadTitle"
                                                       name="ext_letterheadTitle"
                                                       value="Dr" <%=(consultUtil.letterheadTitle != null && consultUtil.letterheadTitle.equals("Dr") ? "checked"  : "") %>>
                                                <label for="ext_letterheadTitle">Include Dr. with name</label>
                                            </div>
                                            <%}%>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="tite4">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.letterheadAddress"/>
                                        </td>
                                        <td class="tite1">
                                            <% if (consultUtil.letterheadAddress != null) { %>
                                            <input type="hidden" name="letterheadAddress" id="letterheadAddress"
                                                   value="<%=Encode.forHtmlAttribute(consultUtil.letterheadAddress) %>"/>
                                            <span id="letterheadAddressSpan">
										<%=Encode.forHtmlContent(consultUtil.letterheadAddress) %>
									</span>
                                            <% } else { %>
                                            <input type="hidden" name="letterheadAddress" id="letterheadAddress"
                                                   value='<%=Encode.forHtmlAttribute(clinic.getClinicAddress()) + " " + Encode.forHtmlAttribute(clinic.getClinicCity()) + " " + Encode.forHtmlAttribute(clinic.getClinicProvince()) + " " + Encode.forHtmlAttribute(clinic.getClinicPostal()) %>'/>
                                            <span id="letterheadAddressSpan">
										<%=Encode.forHtmlContent(clinic.getClinicAddress()) %>&nbsp;<%=Encode.forHtmlContent(clinic.getClinicCity()) %>&nbsp;<%=Encode.forHtmlContent(clinic.getClinicProvince()) %>&nbsp;<%=Encode.forHtmlContent(clinic.getClinicPostal()) %>
									</span>
                                            <% } %>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="tite4"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.letterheadPhone"/>
                                        </td>
                                        <td class="tite1">
                                            <% if (consultUtil.letterheadPhone != null) {
                                            %>
                                            <input type="hidden" name="letterheadPhone" id="letterheadPhone"
                                                   value="<%=Encode.forHtmlAttribute(consultUtil.letterheadPhone) %>"/>
                                            <span id="letterheadPhoneSpan">
										<%=Encode.forHtmlContent(consultUtil.letterheadPhone)%>
									</span>
                                            <% } else { %>
                                            <input type="hidden" name="letterheadPhone" id="letterheadPhone"
                                                   value="<%=Encode.forHtmlAttribute(clinic.getClinicPhone()) %>"/>
                                            <span id="letterheadPhoneSpan">
										<%=Encode.forHtmlContent(clinic.getClinicPhone())%>
									</span>
                                            <% } %>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="tite4">
                                            <label for="letterheadFax"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.letterheadFax"/></label>
                                        </td>
                                        <td  class="tite1" style="width:70%;">
								<c:choose>
								    <c:when test="${not empty consultUtil.letterheadFax}">
									    <input type="hidden" name="letterheadFax" id="letterheadFax" value="${e:forHtmlAttribute(consultUtil.letterheadFax)}" />
									    <span id="letterheadFaxSpan">
										    <e:forHtmlContent value="${consultUtil.letterheadFax}" />
									    </span>
								    </c:when>
									<c:otherwise>
										<input type="hidden" name="letterheadFax" id="letterheadFax" value="<%=Encode.forHtmlAttribute(clinic.getClinicFax())%>" />
										<span id="letterheadFaxSpan">
										    <%=Encode.forHtmlContent(clinic.getClinicFax())%>
									    </span>
									</c:otherwise>
								</c:choose>
							</td>
						</tr>
					</table>
					</td>
				</tr>
				<% if (props.isConsultationFaxEnabled()) { %>
					<tr>
						<td colspan=2 class="tite4 heading">
							Fax Account
						</td>
					</tr>
					<tr>
						<td colspan="2">
							<table>
								<tr>
									<td class="tite4" style="width:30%;">
										<label for="faxAccount">Select Account</label>
									</td>
									<td class="tite1" style="width:70%;">
                                            <%
                                                FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
                                                List<FaxConfig> faxConfigs = faxConfigDao.findAll(null, null);
                                            %>
										<select name="faxAccount" id="faxAccount">
								<%
                                    for (FaxConfig faxConfig : faxConfigs) {
                                %>
										<option value="<%=Encode.forHtmlAttribute(faxConfig.getFaxNumber())%>" <%=faxConfig.getFaxNumber().equalsIgnoreCase(consultUtil.letterheadFax) ? "selected" : ""%>><%=Encode.forHtmlContent(faxConfig.getAccountName())%></option>
								<%
                                    }
                                %>
									</select>

                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td colspan=2 class="tite4 heading">
                                Additional Fax Recipients
                            </td>
                        </tr>
                        <tr>
                            <td colspan=2>
                                <table style="border-collapse:collapse;" id="addFaxRecipient" width="100%">

                                    <tr>
                                        <td class="tite4">
                                            Name <input type="text" id="searchHealthCareTeamInput" value=""
                                                        placeholder="last, first"/>
                                        </td>

                                        <td class="tite4">
                                            Fax <input type="text" id="copytoSpecialistFax" placeholder="xxx-xxx-xxxx"
                                                       value=""/>
                                        </td>
                                        <td class="tite4">
                                            <button onclick="AddOtherFaxProvider(); return false;"> Add Recipient
                                            </button>
                                        </td>
                                    </tr>
                                    <c:if test="${ not empty consultUtil.copyToFaxLog }">
                                        <c:forEach items="${ consultUtil.copyToFaxLog }" var="faxLog">
                                            <tr>
                                                <td class="tite4"><c:out value="${ faxLog.name }"/></td>
                                                <td class="tite4"><c:out value="${ faxLog.fax }"/></td>
                                                <td class="tite4">
                                                    <c:out value="${ faxLog.status }"/>
                                                    <c:out value="${ faxLog.sent }"/>
                                                </td>
                                            </tr>
                                        </c:forEach>
                                    </c:if>
                                </table>
                            </td>
                        </tr>
                        <% } %>


                        <tr>
                            <td colspan="2" class="tite4 heading"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formReason"/>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="2">
                                <textarea rows="10" name="reasonForConsultation"><%=Encode.forHtmlContent(thisForm.getReasonForConsultation())%></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="2">
                                <table style="border-collapse: collapse;" width="100%">
                                    <tr>
                                        <td width="30%" class="tite4 heading">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formClinInf"/>
                                        </td>
                                        <td id="clinicalInfoButtonBar" class="tite4 buttonBar">
                                            <% if (thisForm.geteReferralId() == null) { %>
                                            <input id="SocHistory_clinicalInformation" type="button"
                                                   class="btn clinicalData"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportSocHistory"/>"/>
                                            <input id="FamHistory_clinicalInformation" type="button"
                                                   class="btn clinicalData"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportFamHistory"/>"/>
                                            <input id="MedHistory_clinicalInformation" type="button"
                                                   class="btn clinicalData"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportMedHistory"/>"/>
                                            <input id="Concerns_clinicalInformation" type="button"
                                                   class="btn clinicalData"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportConcerns"/>"/>
                                            <input id="OMeds_clinicalInformation" type="button" class="btn clinicalData"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportOtherMeds"/>"/>
                                            <input id="Reminders_clinicalInformation" type="button"
                                                   class="btn clinicalData"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportReminders"/>"/>
                                            <input id="RiskFactors_clinicalInformation" type="button"
                                                   class="btn clinicalData" value="Risk Factors"/>
                                            <input id="fetchMedications_clinicalInformation" type="button"
                                                   class="btn medicationData" value="Active Medications"/>
                                            <input id="fetchLongTermMedications_clinicalInformation" type="button"
                                                   class="btn medicationData" value="Long Term Medications"/>
                                            <% } %>
                                        </td>
                                    </tr>
                                </table>
                        </tr>
                        <tr>
                            <td colspan="2">
                                <textarea rows="10" id="clinicalInformation"
                                               name="clinicalInformation"><%=Encode.forHtmlContent(thisForm.getClinicalInformation())%></textarea></td>
                        </tr>
                        <tr>
                            <td colspan="2">
                                <table style="border-collapse: collapse;" width="100%">
                                    <tr>
                                        <td width="30%" class="tite4 heading">
                                            <%
                                                if (props.getProperty("significantConcurrentProblemsTitle", "").length() > 1) {
                                                    out.print(props.getProperty("significantConcurrentProblemsTitle", ""));
                                                } else {
                                            %> <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formSignificantProblems"/>
                                            <%
                                                }
                                            %>
                                        </td>
                                        <td id="concurrentProblemsButtonBar" class="tite4 buttonBar">
                                            <% if (thisForm.geteReferralId() == null) { %>
                                            <input id="SocHistory_concurrentProblems" type="button"
                                                   class="btn clinicalData"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportSocHistory"/>"/>
                                            <input id="FamHistory_concurrentProblems" type="button"
                                                   class="btn clinicalData"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportFamHistory"/>"/>
                                            <input id="MedHistory_concurrentProblems" type="button"
                                                   class="btn clinicalData"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportMedHistory"/>"/>
                                            <input id="Concerns_concurrentProblems" type="button"
                                                   class="btn clinicalData"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportConcerns"/>"/>
                                            <input id="OMeds_concurrentProblems" type="button" class="btn clinicalData"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportOtherMeds"/>"/>
                                            <input id="Reminders_concurrentProblems" type="button"
                                                   class="btn clinicalData"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportReminders"/>"/>
                                            <input id="RiskFactors_concurrentProblems" type="button"
                                                   class="btn clinicalData" value="Risk Factors"/>
                                            <input id="fetchMedications_concurrentProblems" type="button"
                                                   class="btn medicationData" value="Active Medications"/>
                                            <input id="fetchLongTermMedications_concurrentProblems" type="button"
                                                   class="btn medicationData" value="Long Term Medications"/>
                                            <% } %>
                                        </td>
                                    </tr>
                                </table>

                            </td>
                        </tr>
                        <tr id="trConcurrentProblems">
                            <td colspan=2>

                                <textarea rows="10" id="concurrentProblems"
                                               name="concurrentProblems"><%=Encode.forHtmlContent(thisForm.getConcurrentProblems())%></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="2">
                                <table style="border-collapse: collapse;" width="100%">
                                    <tr>
                                        <td width="30%" class="tite4 heading">
                                            <% if (props.getProperty("currentMedicationsTitle", "").length() > 1) {
                                                out.print(props.getProperty("currentMedicationsTitle", ""));
                                            } else { %>
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formCurrMedications"/>
                                            <% } %>
                                        </td>
                                        <td id="medsButtonBar" class="tite4 buttonBar">
                                            <% if (thisForm.geteReferralId() == null) { %>
                                            <input id="OMeds_currentMedications" type="button" class="btn clinicalData"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportOtherMeds"/>"/>
                                            <input id="fetchMedications_currentMedications" type="button"
                                                   class="btn medicationData" value="Active Medications"/>
                                            <input id="fetchLongTermMedications_currentMedications" type="button"
                                                   class="btn medicationData" value="Long Term Medications"/>
                                            <% } %>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td colspan=2>
                                <textarea rows="10" id="currentMedications"
                                               name="currentMedications"><%=Encode.forHtmlContent(thisForm.getCurrentMedications())%></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td colspan=2>
                                <table style="border-collapse: collapse;" width="100%">
                                    <tr>
                                        <td width="30%" class="tite4 heading">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formAllergies"/>
                                        </td>
                                        <td class="tite4 buttonBar">
                                            <% if (thisForm.geteReferralId() == null) { %>
                                            <input id="fetchAllergies_allergies" type="button"
                                                   class="btn medicationData" value="Allergies"/>
                                            <% } %>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td colspan=2>
                                <textarea rows="10" id="allergies" name="allergies"><%=Encode.forHtmlContent(thisForm.getAllergies())%></textarea></td>
                        </tr>

                        <%
                            if (props.isConsultationSignatureEnabled()) {
                        %>
                        <tr>
                            <td colspan=2 class="tite4 heading"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formSignature"/>
                            </td>
                        </tr>
                        <tr>
                            <td colspan=2>

                                <input type="hidden" name="newSignature" id="newSignature" value="true"/>
                                <input type="hidden" name="signatureImg" id="signatureImg"
                                       value="<%=(consultUtil.signatureImg != null ? consultUtil.signatureImg : "") %>"/>
                                <input type="hidden" name="newSignatureImg" id="newSignatureImg"
                                       value="<%=signatureRequestId %>"/>

                                <div id="signatureShow" style="display: none;">
                                    <img id="signatureImgTag" src=""/>
                                </div>

                                <iframe style="width:500px; height:132px;" id="signatureFrame"
							src="<%= request.getContextPath() %>/signature_pad/tabletSignature.jsp?inWindow=true&<%=DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY%>=<%=signatureRequestId%>&<%=ModuleType.class.getSimpleName()%>=<%=ModuleType.CONSULTATION%>" ></iframe>

                            </td>
                        </tr>
                        <tr>
                            <td colspan=2 class="spacer"></td>
                        </tr>
                        <% }%>

                        <% if (thisForm.geteReferralId() == null) { %>
                        <tr>

                            <td colspan=2 class="tite4 controlPanel">
                                <input type="hidden" name="submission" value=""/>

                                <%if (request.getAttribute("id") != null) {%>

                                <input name="update" type="button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnUpdate"/>"
                                       onclick="return checkForm('Update Consultation Request','EctConsultationFormRequest2Form');"/>
                                <input name="updateAndPrint" type="button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnUpdateAndPrint"/>"
                                       onclick="return checkForm('Update Consultation Request And Print Preview','EctConsultationFormRequest2Form');"/>

                                <c:if test="${EctConsultationFormRequest2Form.eReferral == true}">
                                    <input name="updateAndSendElectronically" type="button"
                                           value="<fmt:message key='oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnUpdateAndSendElectronicReferral'/>"
                                           onclick="return checkForm('Update_esend','EctConsultationFormRequest2Form');"/>
                                </c:if>

                                <oscar:oscarPropertiesCheck value="yes" property="consultation_fax_enabled">
                                    <input id="fax_button2" name="updateAndFax" type="button"
                                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnUpdateAndFax"/>"
                                           onclick="return checkForm('Update And Fax','EctConsultationFormRequest2Form');"/>
                                </oscar:oscarPropertiesCheck>

                                <%} else {%>

                                <input name="submitSaveOnly" type="button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnSubmit"/>"
                                       onclick="return checkForm('Submit Consultation Request','EctConsultationFormRequest2Form'); "/>
                                <input name="submitAndPrint" type="button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnSubmitAndPrint"/>"
                                       onclick="return checkForm('Submit Consultation Request And Print Preview','EctConsultationFormRequest2Form'); "/>

                                <c:if test="${EctConsultationFormRequest2Form.eReferral == true}">
                                    <input name="submitAndSendElectronically" type="button"
                                           value="<fmt:message key='oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnSubmitAndSendElectronicReferral'/>"
                                           onclick="return checkForm('Submit_esend','EctConsultationFormRequest2Form');"/>
                                </c:if>

                                <oscar:oscarPropertiesCheck value="yes" property="consultation_fax_enabled">
                                    <input id="fax_button2" name="submitAndFax" type="button"
                                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnSubmitAndFax"/>"
                                           onclick="return checkForm('Submit And Fax','EctConsultationFormRequest2Form');"/>
                                </oscar:oscarPropertiesCheck>
                                <c:if test="${EctConsultationFormRequest2Form.eReferral == true}">
                                    <input type="button" value="Send eResponse"
                                           onclick="document.getElementById('saved').value='true'; document.location='${thisForm.oruR01UrlString(request)}'"/>
                                </c:if>
                                <% }%>
                            </td>
                        </tr>
                        <% } %>

                        <oscar:oscarPropertiesCheck value="false"
                                                    property="ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS"
                                                    defaultVal="false">
                            <script type="text/javascript">
                                //<!--
                                // Load services first, then initialize consultation with saved data
                                loadServicesFromServer(function() {
                                    initializeConsultation(
                                        '<%=consultUtil.service%>',
                                        '<%=((consultUtil.service==null)?"":Encode.forJavaScript(consultUtil.getServiceName(consultUtil.service.toString())))%>',
                                        '<%=consultUtil.specialist%>',
                                        '<%=((consultUtil.specialist==null)?"":Encode.forJavaScript(consultUtil.getSpecailistsName(consultUtil.specialist.toString())))%>',
                                        '<%=Encode.forJavaScript(consultUtil.specPhone)%>',
                                        '<%=Encode.forJavaScript(consultUtil.specFax)%>',
                                        '<%=Encode.forJavaScript(consultUtil.specAddr)%>'
                                    );
                                });
                                //-->
                            </script>
                        </oscar:oscarPropertiesCheck>

                        <oscar:oscarPropertiesCheck value="true"
                                                    property="ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS"
                                                    defaultVal="false">
                            <script type="text/javascript">
                                const specialist = "${ consultUtil.specialist }";
                                const servicevalue = "${ consultUtil.service }";

                                document.EctConsultationFormRequest2Form.specialist.value = specialist;
                                document.EctConsultationFormRequest2Form.service.value = servicevalue;

                                if (typeof healthCareTeam !== 'undefined' && healthCareTeam !== null) {
                                    document.EctConsultationFormRequest2Form.annotation.value = healthCareTeam[specialist].note;
                                    document.EctConsultationFormRequest2Form.phone.value = healthCareTeam[specialist].phoneNum;
                                    document.EctConsultationFormRequest2Form.fax.value = healthCareTeam[specialist].specFax;
                                    document.EctConsultationFormRequest2Form.address.value = healthCareTeam[specialist].specAddress;
                                }

                            </script>
                        </oscar:oscarPropertiesCheck>

                        <!----End new rows here-->

                        <tr height="100%">
                            <td></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="MainTableBottomRowLeftColumn"></td>
                <td class="MainTableBottomRowRightColumn"></td>
            </tr>
        </table>
        <div id="attachDocumentDisplay" style="display:none;"></div>
    </form>
    </body>

    <script type="text/javascript">
        jQuery(document).ready(function () {
            var ctx = "${pageContext.request.contextPath}";
            //--> Autocomplete searches
            jQuery("#searchHealthCareTeamInput").autocomplete({
                source: function (request, response) {
                    var url = ctx + "/demographic/Contact.do?method=searchAllContacts&searchMode=search_name&orderBy=c.lastName,c.firstName";
                    jQuery.ajax({
                        url: url,
                        type: "GET",
                        dataType: "json",
                        data: {
                            term: request.term
                        },
                        contentType: "application/json",
                        success: function (data) {
                            response(jQuery.map(data, function (item) {
                                return {
                                    label: item.lastName + ", "
                                        + item.firstName + " :: "
                                        + item.residencePhone
                                        + " :: " + item.address
                                        + " " + item.city,
                                    value: item.id,
                                    contact: item
                                }
                            }));
                        }
                    });
                },
                minLength: 2,
                focus: function (event, ui) {
                    event.preventDefault();
                    return false;
                },
                select: function (event, ui) {
                    event.preventDefault();
                    jQuery("#copytoSpecialistFax").val(ui.item.contact.fax);
                    jQuery("#searchHealthCareTeamInput").val(ui.item.contact.lastName + ", " + ui.item.contact.firstName);
                }
            });

            /*
            * Selecting which letterhead to load for new consult requests.
            * Default is logged in provider on page load
            * Options are:
            *  2 : MRP on patient file
            *  3 : Clinic address.
            * Clinic address is set if no selection is detected.
            */
            if("${empty pageScope.consultUtil.letterheadName}" === "true") {
                // New consultation - set default letterhead
                if("${pageScope.lhndType eq 'providers'}" === "true"){
                    switchProvider("${pageScope.providerDefault}");
                } else if("${pageScope.lhndType eq 'clinic'}" === "true"){
                    switchProvider("<%=clinic.getClinicName()%>");
                } else {
                    switchProvider("-1");
                }
            } else {
                // Existing consultation - load saved letterhead
                switchProvider("${pageScope.consultUtil.letterheadName}");
            }
        })
    </script>


    <script type="text/javascript">

        Calendar.setup({
            inputField: "followUpDate",
            ifFormat: "%Y/%m/%d",
            showsTime: false,
            trigger: "followUpDate",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "appointmentDate",
            ifFormat: "%Y/%m/%d",
            showsTime: false,
            trigger: "appointmentDate",
            singleClick: true,
            step: 1
        });
        <%if("false".equals(OscarProperties.getInstance().getProperty("CONSULTATION_LOCK_REFERRAL_DATE", "true"))) {%>
        Calendar.setup({
            inputField: "referalDate",
            ifFormat: "%Y/%m/%d",
            showsTime: false,
            trigger: "referalDate",
            singleClick: true,
            step: 1
        });
        <%}%>
        jQuery(document).ready(function () {

            /**
             * This function adds the old form to the attachment window only if that form is displayed in the consultForm/eForm attachments.
             * The attachment window only displays the latest (updated) forms.
             */
            function addFormIfNotFound(form, demographicNo, delegate) {
                const checkboxName = form.getAttribute('name');
                const formValue = form.getAttribute('value');
                const formId = "formNo" + formValue;
                const formName = document.getElementById("entry_" + formId).getAttribute('data-formName');
                const formDate = document.getElementById("entry_" + formId).getAttribute('data-formDate');

                const checkbox = jQuery('<input>', {
                    class: 'form_check',
                    type: 'checkbox',
                    name: checkboxName,
                    id: formId,
                    value: formValue,
                    title: formName
                });

                const label = jQuery('<label>', {
                    for: formId,
                    text: "(Not Latest Version) " + formName + " " + formDate
                });

                const previewButton = jQuery('<button>', {
                    class: 'preview-button',
                    type: 'button',
                    text: 'Preview',
                    title: 'Preview'
                }).click(function () {
                    getPdf('FORM', formValue, 'method=renderFormPDF&formId=' + formValue + '&formName=' + formName + '&demographicNo=' + demographicNo);
                });

                const newLiFormElement = jQuery('<li>', {
                    class: 'form',
                }).append(checkbox).append(label).append(previewButton);
                jQuery('#formList').find('.selectAllHeading').after(newLiFormElement);

                return jQuery('#attachDocumentsForm').find(delegate);
            }

            /**
             DOCUMENT ATTACHMENT MANAGER JAVASCRIPT
             **/
            jQuery(document).on('click', '*[data-poload]', function () {
                const $mainForm = jQuery('#EctConsultationFormRequest2Form');

                var trigger = jQuery(this);
                trigger.off('click');
                var triggerId = "#" + trigger.attr('id');
                var title = trigger.attr("title");

                jQuery("#attachDocumentDisplay").load(trigger.data('poload'), function (response, status, xhr) {
                    if (status === "success") {
                        $mainForm.find(".delegateAttachment").each(function (index, data) {
                            let delegate = "#" + this.id.split("_")[1];
                            let element = jQuery('#attachDocumentsForm').find(delegate);
                            if (element.length === 0) {
                                element = addFormIfNotFound(data, '<%=demo%>', delegate);
                            }
                            let elementClassType = element.attr("class").split("_")[0];
                            element.attr("checked", true).attr("class", elementClassType + "_pre_check");

                            // Expand list if selected lab is older version
                            if (element.attr('data-version')) {
                                expandLabVersionList(element.parent().parent().parent().find('.collapse-arrow'));
                            }
                        });

                        // Disable all EncounterForm (form) checkboxes in the attachment window if a consultation request is created using OceanMD.
                        if (typeof disableFields !== 'undefined' && disableFields === true) {
                            jQuery("#formList input[type='checkbox']").prop("disabled", true);
                        }
                    }
                }).dialog({
                    title: title,
                    modal: true,
                    closeText: "Save and Close",
                    height: 'auto',
                    width: 'auto',
                    resizable: true,
                    open: function (event, ui) {
                        jQuery(this).parent().css({
                            top: 0,
                            left: 0
                        });

                        let closeBtn = jQuery(this).parent().find(".ui-dialog-titlebar-close");
                        closeBtn.removeClass("ui-button-icon-only");
                        closeBtn.addClass("save-and-close-button");
                        closeBtn.html("Save and Close");
                    },

                    beforeClose: function (event, ui) {
                        // before the dialog is closed:

                        // pass the checked elements to the consultation request form
                        jQuery('#attachDocumentsForm').find(".document_check:checked:not(input[disabled='disabled']), .lab_check:checked:not(input[disabled='disabled']), .form_check:checked:not(input[disabled='disabled']), .eForm_check:checked:not(input[disabled='disabled']), .hrm_check:checked:not(input[disabled='disabled'])"
                        ).each(function (index, data) {
                            var element = jQuery(this);
                            var input = jQuery("<input />", {
                                type: 'hidden',
                                name: element.attr('name'),
                                value: element.val(),
                                id: "delegate_" + element.attr('id'),
                                class: 'delegateAttachment'
                            });
                            var row = jQuery("<tr>", {id: "entry_" + element.attr("name") + element.val()});
                            var column = jQuery("<td>");
                            var target = "#attachedDocumentsTable";

                            if ("lab_check".indexOf(element.attr("class")) !== -1) {
                                target = "#attachedLabsTable";
                            }

                            if ("form_check".indexOf(element.attr("class")) !== -1) {
                                target = "#attachedFormsTable";
                            }

                            if ("eForm_check".indexOf(element.attr("class")) != -1) {
                                target = "#attachedEFormsTable";
                            }

                            if ("hrm_check".indexOf(element.attr("class")) != -1) {
                                target = "#attachedHRMDocumentsTable";
                            }
                            column.text(element.attr("title"));
                            column.append(input);
                            row.append(column);

                            jQuery('#EctConsultationFormRequest2Form').find(target).append(row);
                        });

                        // remove unchecked elements from the request form.
                        jQuery('#attachDocumentsForm').find(".document_pre_check:not(input[disabled='disabled']), .lab_pre_check:not(input[disabled='disabled']), .form_pre_check:not(input[disabled='disabled']), .eForm_pre_check:not(input[disabled='disabled']), .hrm_pre_check:not(input[disabled='disabled'])").each(function (index, data) {
                            var checkedElement = jQuery(this);

                            if (!checkedElement.is(':checked')) {
                                var checkedElementClass = checkedElement.attr("class");
                                $mainForm.find("#entry_" + checkedElement.attr("id")).remove();
                                checkedElement.attr("class", checkedElementClass.split("_")[0] + "_check");
                            }
                        });

                        const isOceanEReferral = document.getElementById('isOceanEReferral');
                        if (isOceanEReferral !== null && isOceanEReferral.value.toLowerCase() === "true") {
                            attachOceanAttachments();
                        }
                    }
                });
            })
        })

    </script>

</html>

<%!
    protected String listNotes(CaseManagementManager cmgmtMgr, String code, String providerNo, String demoNo) {
        // filter the notes by the checked issues
        List<Issue> issues = cmgmtMgr.getIssueInfoByCode(providerNo, code);

        String[] issueIds = new String[issues.size()];
        int idx = 0;
        for (Issue issue : issues) {
            issueIds[idx] = String.valueOf(issue.getId());
        }

        // need to apply issue filter
        List<CaseManagementNote> notes = cmgmtMgr.getNotes(demoNo, issueIds);
        StringBuffer noteStr = new StringBuffer();
        for (CaseManagementNote n : notes) {
            if (!n.isLocked() && !n.isArchived()) noteStr.append(n.getNote() + "\n");
        }

        return noteStr.toString();
    }
%>


