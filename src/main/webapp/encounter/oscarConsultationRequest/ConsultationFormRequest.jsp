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

<%@page import="io.github.carlos_emr.carlos.utility.WebUtils" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="/WEB-INF/special_tag.tld" prefix="special" %>
<!-- end -->
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>


<%@page import="java.util.ArrayList, java.util.List, java.util.*, io.github.carlos_emr.CarlosProperties, io.github.carlos_emr.carlos.lab.ca.on.*" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager,io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote,io.github.carlos_emr.carlos.casemgmt.model.Issue,io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO,org.springframework.web.context.support.*,org.springframework.web.context.*" %>

<%@page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.WebUtils" %>
<%@page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequest2Form" %>
<%@page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequestUtil" %>
<%@page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctViewRequest2Action" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils,io.github.carlos_emr.carlos.clinic.ClinicData" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.DigitalSignatureUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.ui.servlet.ImageRenderingServlet" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao, io.github.carlos_emr.carlos.PMmodule.model.Program" %>
<%@page import="io.github.carlos_emr.carlos.demographic.data.DemographicData, io.github.carlos_emr.carlos.prescript.data.RxProviderData, io.github.carlos_emr.carlos.prescript.data.RxProviderData.Provider, io.github.carlos_emr.carlos.clinic.ClinicData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.FaxConfigDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ContactSpecialtyDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DemographicContactDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.enumerator.ConsultationRequestExtKey" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao" %>
<%@ page import="io.github.carlos_emr.carlos.managers.ConsultationManager" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.EctFormData" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.eform.EFormUtil" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDocUtil" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDoc" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.EctInformation" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.RxInformation" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.LabResultData" %>
<%@ page import="io.github.carlos_emr.carlos.managers.LookupListManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>


<jsp:useBean id="displayServiceUtil" scope="request"
             class="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConDisplayServiceUtil"/>
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

            if (!appNo.isEmpty()) {
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
        alert('<%= Encode.forJavaScript(errorMessage) %>');
    </SCRIPT>
    <%
        }

        java.util.Calendar calender = java.util.Calendar.getInstance();
        String day = Integer.toString(calender.get(java.util.Calendar.DAY_OF_MONTH));
        String mon = Integer.toString(calender.get(java.util.Calendar.MONTH) + 1);
        String year = Integer.toString(calender.get(java.util.Calendar.YEAR));
        String formattedDate = year + "/" + mon + "/" + day;

        CarlosProperties props = CarlosProperties.getInstance();
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
                    for (var i = 0; i < consultationServices.length; i++) {
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
        <%@ include file="/includes/global-head.jspf" %>
        <title>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.title"/>
        </title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
        <script>
            var ctx = '<%=request.getContextPath()%>';
            var requestId = '<%=Encode.forJavaScript(requestId)%>';
            var demographicNo = '<%=Encode.forJavaScript(demo)%>';
            var demoNo = '<%=Encode.forJavaScript(demo)%>';
            var appointmentNo = '<%=Encode.forJavaScript(appNo)%>';
        </script>

        <script type="text/javascript"
                src="<%=request.getContextPath()%>/library/jquery/jquery-ui-1.14.2.min.js"></script>
        <link href="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.14.2.min.css" rel="stylesheet"
              media="screen"/>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/jquery_oscar_defaults.js"></script>

        <!-- Instead of importing conreq.js using the CME tag (as done in Oscar19/OscarPro), we are opting to directly import conreq.js without utilizing the CME tag. -->
        <% if ("ocean".equals(props.get("cme_js"))) {
            int randomNo = new Random().nextInt();%>
        <script id="mainScript"
                src="${ pageContext.request.contextPath }/js/custom/ocean/conreq.js?no-cache=<%=randomNo%>&autoRefresh=true"
                ocean-host=<%=Encode.forUriComponent(props.getProperty("ocean_host"))%>></script>
        <% } %>
        <link rel="stylesheet" type="text/css" href="${ pageContext.request.contextPath }/css/healthCareTeam.css"/>

        <style type="text/css">
            /* Ocean refer style */
            span.oceanRefer {
                display: flex;
                align-items: center;
                padding-top: 10px;
            }
            span.oceanRefer a {
                margin-right: 5px;
            }

            /* Attachment tables */
            .consult-attachments {
                border: 1px solid var(--carlos-border);
                border-radius: 4px;
                background-color: var(--carlos-bg-light);
                padding: 0.5rem;
            }
            .consult-attachments h3 {
                margin: 0 !important;
                padding: 0.25rem 0 !important;
                border-bottom: 1px solid var(--carlos-border);
                font-size: 0.85rem;
                font-weight: 600;
            }
            #attachedDocumentsTable, #attachedLabsTable, #attachedFormsTable,
            #attachedEFormsTable, #attachedHRMDocumentsTable {
                border-collapse: collapse;
                width: 100%;
            }
            #attachedDocumentTable {
                border: 1px solid var(--carlos-border);
                border-collapse: collapse;
                width: 100%;
                background-color: var(--carlos-bg-light);
            }
            #attachedDocumentTable tr td {
                padding: 5px;
            }

            /* jQuery UI overrides */
            .ui-dialog { font-size: small !important; }
            .ui-autocomplete {
                font-size: 0.82rem !important;
                max-height: 320px;
                overflow-y: auto;
                overflow-x: hidden;
                z-index: 9999 !important;
            }
            .ui-autocomplete .ui-menu-item { border-bottom: 1px solid #eee; }
            .ui-autocomplete .ui-menu-item:nth-child(even) { background-color: rgba(0,0,0,0.04); }
            .ui-autocomplete .ui-menu-item-wrapper { padding: 0.3rem 0.5rem; }
            .specialist-ac-name { font-weight: 600; }
            .specialist-ac-details { font-size: 0.72rem; color: #6c757d; margin-top: 0.1rem; }
            .save-and-close-button {
                width: auto !important;
                height: auto !important;
                background-color: var(--carlos-primary) !important;
                color: #fff !important;
                border: none !important;
                border-radius: 4px !important;
                padding: 0.35rem 0.75rem !important;
                font-size: 0.8rem !important;
                font-weight: 500 !important;
                line-height: 1.2 !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                cursor: pointer !important;
            }
            .save-and-close-button:hover {
                opacity: 0.85 !important;
            }

            /* Consultation form layout */
            .consult-form-label {
                background-color: var(--carlos-bg-light);
                color: var(--bs-body-color);
                font-size: 0.85rem;
                font-weight: 500;
                padding: 0.4rem 0.75rem;
                white-space: nowrap;
                vertical-align: top;
            }
            .consult-form-value {
                padding: 0.4rem 0.75rem;
                font-size: 0.85rem;
                vertical-align: top;
            }
            .consult-form-value input[type="text"],
            .consult-form-value input[type="date"],
            .consult-form-value select,
            .consult-form-value textarea {
                width: 100% !important;
                box-sizing: border-box;
            }
            .consult-section-heading {
                font-weight: 600;
                font-size: 0.9rem;
                padding: 0.5rem 0.75rem;
                background-color: var(--carlos-bg-light);
                border-top: 1px solid var(--carlos-border);
            }

            /* Control panel (action buttons) */
            .consult-control-panel {
                padding: 0.5rem 0.75rem;
                border: 1px solid var(--carlos-primary);
                border-radius: 4px;
                background-color: var(--carlos-bg-light);
                display: flex;
                flex-wrap: wrap;
                gap: 0.35rem;
            }
            .consult-control-panel-sticky {
                position: sticky;
                top: 0;
                z-index: 10;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }

            /* Patient info card */
            .consult-patient-card td {
                vertical-align: top;
            }

            /* Status sidebar */
            .consult-sidebar .form-check {
                margin-bottom: 0.15rem;
            }

            /* Textarea sizing */
            textarea {
                width: 100%;
                box-sizing: border-box;
            }

            /* Clinical data import buttons - dropdown style */
            .consult-import-btn-bar {
                display: inline-flex;
                flex-wrap: wrap;
                gap: 0.25rem;
                padding: 0.25rem 0;
            }
            .consult-import-btn-bar .btn {
                font-size: 0.75rem;
                padding: 0.15rem 0.5rem;
            }
            .consult-import-dropdown .dropdown-item {
                font-size: 0.8rem;
                padding: 0.25rem 0.75rem;
                cursor: pointer;
            }

            /* Collapsible sections */
            .consult-collapse-heading {
                cursor: pointer;
                user-select: none;
                display: flex;
                align-items: center;
                justify-content: space-between;
            }
            .consult-collapse-heading .collapse-icon {
                transition: transform 0.2s;
                font-size: 0.75rem;
            }
            .consult-collapse-heading[aria-expanded="false"] .collapse-icon {
                transform: rotate(-90deg);
            }

            /* Annotation textarea */
            #annotation {
                color: var(--carlos-primary);
            }

            /* Radio/checkbox CARLOS blue */
            .form-check-input:checked {
                background-color: var(--carlos-primary);
                border-color: var(--carlos-primary);
            }

            /* Specialist disclaimer indicator — shown when saved consultant is no longer in the directory */
            .consult-disclaimer-indicator {
                display: none;
                font-size: 24px;
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
            var xhr = new XMLHttpRequest();
            xhr.open('GET', '<%= request.getContextPath() %>/encounter/ConsultationLookup2Action.do?method=getServices', true);
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
            allServicesData = serviceList; // Store for autocomplete
            for (var i = 0; i < serviceList.length; i++) {
                var service = serviceList[i];
                K(service.serviceId, service.serviceDesc);
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
            xhr.open('GET', '<%= request.getContextPath() %>/encounter/ConsultationLookup2Action.do?method=getSpecialists&serviceId=' + serviceId, true);
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

        ////////////////////////////////////////////////////////////////////
        // All-specialists data for autocomplete (loaded once on page ready)
        var allSpecialistsData = [];
        var allServicesData = [];

        function loadAllSpecialistsData(callback) {
            jQuery.ajax({
                url: ctx + '/encounter/ConsultationLookup2Action.do?method=getAllSpecialists',
                method: 'GET',
                dataType: 'json',
                success: function(data) {
                    allSpecialistsData = data;
                    if (callback) callback();
                },
                error: function(xhr, status, err) {
                    console.error('Failed to load all specialists:', err);
                    if (callback) callback();
                }
            });
        }

        function initServiceAutocomplete() {
            jQuery('#serviceInput').autocomplete({
                minLength: 0,
                source: function(request, response) {
                    var term = request.term.toLowerCase();
                    var results = allServicesData.filter(function(s) {
                        return !term || s.serviceDesc.toLowerCase().indexOf(term) >= 0;
                    });
                    response(jQuery.map(results, function(s) {
                        return { label: s.serviceDesc, value: s.serviceDesc, id: s.serviceId };
                    }));
                },
                select: function(event, ui) {
                    jQuery('#serviceInput').val(ui.item.label);
                    jQuery('#service').val(ui.item.id);
                    onServiceSelected(ui.item.id);
                    return false;
                },
                focus: function() { return false; }
            });
            jQuery('#serviceInput').on('click', function() {
                jQuery(this).autocomplete('search', '');
            });
        }

        function initSpecialistAutocomplete() {
            var acWidget = jQuery('#specialistInput').autocomplete({
                minLength: 0,
                source: function(request, response) {
                    var term = request.term.toLowerCase();
                    var selectedServiceId = jQuery('#service').val();
                    var filtered = allSpecialistsData.filter(function(s) {
                        if (selectedServiceId && selectedServiceId !== '' && selectedServiceId !== '-1') {
                            if (s.serviceIds.indexOf(parseInt(selectedServiceId)) === -1) return false;
                        }
                        if (!term) return true;
                        return (s.name && s.name.toLowerCase().indexOf(term) >= 0) ||
                               (s.address && s.address.toLowerCase().indexOf(term) >= 0) ||
                               (s.phone && s.phone.indexOf(term) >= 0) ||
                               (s.fax && s.fax.indexOf(term) >= 0) ||
                               (s.serviceNames && s.serviceNames.join(' ').toLowerCase().indexOf(term) >= 0);
                    });
                    response(filtered.slice(0, 25));
                },
                select: function(event, ui) {
                    jQuery('#specialistInput').val(ui.item.name);
                    jQuery('#specialist').val(ui.item.specId);
                    onSpecialistSelected(ui.item);
                    return false;
                },
                focus: function() { return false; }
            }).data('ui-autocomplete');

            if (acWidget) {
                // HTML encoding helper using DOM APIs — functionally equivalent to the jQuery .text().html() idiom but uses a pattern that static analysis tools (CodeQL) can verify as safe
                function escapeHtml(text) {
                    var div = document.createElement('div');
                    div.appendChild(document.createTextNode(text));
                    return div.innerHTML;
                }
                acWidget._renderItem = function(ul, item) {
                    var serviceBadges = (item.serviceNames || []).map(function(sn) {
                        return '<span class="badge rounded-pill border border-primary text-primary ms-1" style="font-size:0.65rem;">' + escapeHtml(sn) + '</span>';
                    }).join('');
                    var namePart = '<strong class="specialist-ac-name">' + escapeHtml(item.name) + '</strong>';
                    var details = [];
                    if (item.address) details.push(escapeHtml(item.address));
                    if (item.phone) details.push('Tel: ' + escapeHtml(item.phone));
                    if (item.fax) details.push('Fax: ' + escapeHtml(item.fax));
                    var detailLine = details.length ? '<div class="specialist-ac-details">' + details.join(' &bull; ') + '</div>' : '';
                    return jQuery('<li>')
                        .append('<div class="d-flex justify-content-between align-items-center">' + namePart + '<span>' + serviceBadges + '</span></div>' + detailLine)
                        .appendTo(ul);
                };
            }

            jQuery('#specialistInput').on('click', function() {
                jQuery(this).autocomplete('search', '');
            });
        }

        function onServiceSelected(serviceId) {
            // Clear specialist selection when service changes
            jQuery('#specialistInput').val('');
            jQuery('#specialist').val('');
            var form = document.EctConsultationFormRequest2Form;
            form.phone.value = '';
            form.fax.value = '';
            form.address.value = '';
            document.getElementById('annotation').value = '';
            document.getElementById('eFormButton').style.display = 'none';
            <%if (props.isConsultationFaxEnabled()) {%>
            specialistFaxNumber = '';
            updateFaxButton();
            <%}%>
        }

        function onSpecialistSelected(specData) {
            var form = document.EctConsultationFormRequest2Form;
            form.phone.value = specData.phone || '';
            form.fax.value = specData.fax || '';
            form.address.value = specData.address || '';

            // Set service to the first service of this specialist if no service currently selected
            var currentService = jQuery('#service').val();
            if ((!currentService || currentService === '' || currentService === '-1') && specData.serviceIds && specData.serviceIds.length > 0) {
                jQuery('#service').val(specData.serviceIds[0]);
                jQuery('#serviceInput').val(specData.serviceNames ? specData.serviceNames[0] : '');
            }

            document.getElementById('consult-disclaimer').style.display = 'none';

            <%if (props.isConsultationFaxEnabled()) {%>
            specialistFaxNumber = specData.fax ? specData.fax.trim() : '';
            updateFaxButton();
            <%}%>

            jQuery.post(ctx + '/getProfessionalSpecialist.do', { id: specData.specId }, function(xml) {
                var hasUrl = xml.eDataUrl != null && xml.eDataUrl !== '';
                enableDisableRemoteReferralButton(form, !hasUrl);
                document.getElementById('annotation').value = xml.annotation || '';
                updateEFormLink(xml.eformId);
            });
        }
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
                    var current = jQuery(target).val();
                    if (current && current.trim().length > 0) {
                        jQuery(target).val(current + "\n" + data.note);
                    } else {
                        jQuery(target).val(data.note);
                    }
                }
            });
        }

        /**
         * Auto-pulls Medical, Social, and Family History into the clinical
         * information textarea for new consultations.
         */
        function autoImportClinicalHistory(demographicNo) {
            var target = "#clinicalInformation";
            var issueTypes = ["MedHistory", "SocHistory", "FamHistory"];
            var idx = 0;

            function fetchNext() {
                if (idx >= issueTypes.length) return;
                var issueType = issueTypes[idx];
                idx++;
                jQuery.ajax({
                    method: "POST",
                    url: "${ pageContext.request.contextPath }/oscarConsultationRequest/consultationClinicalData.do",
                    data: { method: "fetchIssueNote", issueType: issueType, demographicNo: demographicNo },
                    dataType: 'JSON',
                    success: function (data) {
                        if (data.note && data.note.trim().length > 0) {
                            var current = jQuery(target).val();
                            if (current && current.trim().length > 0) {
                                jQuery(target).val(current + "\n" + data.note);
                            } else {
                                jQuery(target).val(data.note);
                            }
                        }
                        fetchNext();
                    },
                    error: function () { console.warn('Failed to auto-import ' + issueType + ' for consultation'); fetchNext(); }
                });
            }

            fetchNext();
        }

        /**
         * Populates the shared import dropdown menus dynamically to avoid duplicating
         * the same menu markup for each clinical textarea section.
         */
        function buildImportMenus() {
            var fullImportItems = [
                {cls: 'clinicalData', prefix: 'SocHistory', label: '<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnImportSocHistory"/>'},
                {cls: 'clinicalData', prefix: 'FamHistory', label: '<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnImportFamHistory"/>'},
                {cls: 'clinicalData', prefix: 'MedHistory', label: '<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnImportMedHistory"/>'},
                {cls: 'clinicalData', prefix: 'Concerns', label: '<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnImportConcerns"/>'},
                {cls: 'clinicalData', prefix: 'OMeds', label: '<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnImportOtherMeds"/>'},
                {cls: 'clinicalData', prefix: 'Reminders', label: '<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnImportReminders"/>'},
                {cls: 'clinicalData', prefix: 'RiskFactors', label: '<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnImportRiskFactors"/>'},
                {divider: true},
                {cls: 'medicationData', prefix: 'fetchMedications', label: '<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnImportActiveMedications"/>'},
                {cls: 'medicationData', prefix: 'fetchLongTermMedications', label: '<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnImportLongTermMedications"/>'}
            ];
            var medsOnlyItems = [
                {cls: 'clinicalData', prefix: 'OMeds', label: '<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnImportOtherMeds"/>'},
                {cls: 'medicationData', prefix: 'fetchMedications', label: '<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnImportActiveMedications"/>'},
                {cls: 'medicationData', prefix: 'fetchLongTermMedications', label: '<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnImportLongTermMedications"/>'}
            ];

            jQuery('.consult-import-menu').each(function() {
                var target = String(jQuery(this).data('target')).replace(/[^a-zA-Z0-9_-]/g, '');
                var html = '';
                for (var i = 0; i < fullImportItems.length; i++) {
                    var item = fullImportItems[i];
                    if (item.divider) { html += '<li><hr class="dropdown-divider"></li>'; continue; }
                    html += '<li><a class="dropdown-item ' + item.cls + '" id="' + item.prefix + '_' + target + '" href="javascript:void(0);">' + item.label + '</a></li>';
                }
                jQuery(this).html(html);
            });
            jQuery('.consult-import-menu-meds').each(function() {
                var target = String(jQuery(this).data('target')).replace(/[^a-zA-Z0-9_-]/g, '');
                var html = '';
                for (var i = 0; i < medsOnlyItems.length; i++) {
                    var item = medsOnlyItems[i];
                    html += '<li><a class="dropdown-item ' + item.cls + '" id="' + item.prefix + '_' + target + '" href="javascript:void(0);">' + item.label + '</a></li>';
                }
                jQuery(this).html(html);
            });
        }

        jQuery(document).ready(function () {
            buildImportMenus();
            initAppointmentTimeDisplay();

            // Attach event listeners for selects that previously used inline onchange
            var providerNoSelect = document.getElementById('providerNoSelect');
            if (providerNoSelect) {
                providerNoSelect.addEventListener('change', function() { switchProvider(this.value); });
            }
            var specialistHctSelect = document.getElementById('specialist');
            if (specialistHctSelect && specialistHctSelect.tagName === 'SELECT') {
                specialistHctSelect.addEventListener('change', function() { getSpecialist(this); });
            }
            var siteNameSelect = document.getElementById('siteName');
            if (siteNameSelect) {
                siteNameSelect.addEventListener('change', function() {
                    this.style.backgroundColor = this.options[this.selectedIndex].style.backgroundColor;
                });
                // Apply initial background color on load
                if (siteNameSelect.options.length > 0) {
                    siteNameSelect.style.backgroundColor = siteNameSelect.options[siteNameSelect.selectedIndex].style.backgroundColor;
                }
            }


            jQuery(document).on('click', '.medicationData', function () {
                var data = new Object();
                var target = "#" + this.id.split("_")[1];
                data.method = this.id.split("_")[0];
                data.demographicNo = <%= Encode.forJavaScript(demo) %>;
                getClinicalData(data, target)
            });

            jQuery(document).on('click', '.clinicalData', function () {
                var data = new Object();
                var target = "#" + this.id.split("_")[1];
                data.method = "fetchIssueNote";
                data.issueType = this.id.split("_")[0];
                data.demographicNo = <%= Encode.forJavaScript(demo) %>;
                getClinicalData(data, target)
            });

            // Auto-import Medical and Social History for new consultations
            <% if (requestId == null && demo != null) { %>
            var clinical = jQuery("#clinicalInformation").val();
            if (!clinical || clinical.trim().length === 0) {
                autoImportClinicalHistory(<%= Encode.forJavaScript(demo) %>);
            }
            <% } %>
        })

        function setDisabledDateFields(form, disabled) {
            var timeDisplay = document.getElementById('appointmentTimeDisplay');
            if (timeDisplay) timeDisplay.disabled = disabled;
            var clearTimeBtn = document.getElementById('clearTimeBtn');
            if (clearTimeBtn) clearTimeBtn.disabled = disabled;
            var appointmentDate = document.getElementById('appointmentDate');
            if (appointmentDate) appointmentDate.disabled = disabled;
            var shouldClear = disabled && form.patientWillBook && form.patientWillBook.checked;
            if (shouldClear) {
                // Clear date and hidden time fields so they are not submitted when patient will book
                if (appointmentDate) appointmentDate.value = '';
                document.getElementById('appointmentHour').value = '';
                document.getElementById('appointmentMinute').value = '';
                document.getElementById('appointmentPm').value = 'AM';
                if (timeDisplay) timeDisplay.value = '';
            }
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
                disableIfExists(document.getElementById('specialistInput'), disableFields);
                disableIfExists(document.getElementById('serviceInput'), disableFields);
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

                // Calendar elements removed - using native HTML5 date inputs
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
            // Set service autocomplete input and hidden value
            if (savedService && savedService !== 'null' && savedService !== '-1') {
                jQuery('#service').val(savedService);
                jQuery('#serviceInput').val(savedServiceName || '');
                // Maintain legacy data structure for backward compatibility
                if (!services[savedService]) {
                    K(savedService, savedServiceName);
                }
            }

            // Set specialist autocomplete input and hidden value
            if (savedSpecialist && savedSpecialist !== 'null') {
                jQuery('#specialist').val(savedSpecialist);
                jQuery('#specialistInput').val(savedSpecName || '');

                // Fill phone/fax/address from saved values
                var form = document.EctConsultationFormRequest2Form;
                if (savedPhone) form.phone.value = savedPhone;
                if (savedFax) form.fax.value = savedFax;
                if (savedAddress) form.address.value = savedAddress;

                <%if (props.isConsultationFaxEnabled()) {%>
                if (savedFax) { specialistFaxNumber = savedFax.trim(); updateFaxButton(); }
                <%}%>

                // Check if specialist is still in the directory; show disclaimer if not
                var foundInData = allSpecialistsData.some(function(s) { return String(s.specId) === String(savedSpecialist); });
                if (!foundInData) {
                    document.getElementById('consult-disclaimer').style.display = 'inline';
                }

                // Load additional specialist data (annotation, eform)
                jQuery.post(ctx + '/getProfessionalSpecialist.do', {id: savedSpecialist}, function(xml) {
                    enableDisableRemoteReferralButton(form, !(xml.eDataUrl != null && xml.eDataUrl !== ''));
                    document.getElementById('annotation').value = xml.annotation || '';
                    updateEFormLink(xml.eformId);
                });
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
            var popup = window.open(varpage, "<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgCal"/>", windowprops);

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
            var hasApptTime = document.EctConsultationFormRequest2Form.appointmentHour.value !== '' &&
                document.EctConsultationFormRequest2Form.appointmentMinute.value !== '';

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

            document.getElementById("saved").value = "true";
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

        providerData['<%=Encode.forJavaScript(clinic.getClinicName())%>'] = {};

        let addr, ph, fx;

        <% if (consultUtil.letterheadAddress != null) { %>
        addr = '<%= Encode.forJavaScript(consultUtil.letterheadAddress.replaceAll("\\n", " ")) %>';
        <%} else {%>
        addr = '<%=Encode.forJavaScript(clinic.getClinicAddress()) + " " + Encode.forJavaScript(clinic.getClinicCity()) + " " + Encode.forJavaScript(clinic.getClinicProvince()) + " " + Encode.forJavaScript(clinic.getClinicPostal()) %>';
        <%}%>

        <% if(consultUtil.letterheadPhone != null) { %>
        ph = '<%=Encode.forJavaScript(consultUtil.letterheadPhone.replaceAll("\\n", " "))%>';
        <%} else { %>
        ph = '<%=Encode.forJavaScript(clinic.getClinicPhone())%>';
        <% }%>

        <%if(consultUtil.letterheadFax != null) { %>
        fx = '<%=Encode.forJavaScript(consultUtil.letterheadFax)%>';
        <% } else {%>
        fx = '<%=Encode.forJavaScript(clinic.getClinicFax())%>';
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

        providerData['<%=prov_no%>'].address = "<%=Encode.forJavaScript(providerItem.getFullAddress())%>";
        providerData['<%=prov_no%>'].phone = "<%=Encode.forJavaScript(providerItem.getClinicPhone())%>";
        providerData['<%=prov_no%>'].fax = "<%=Encode.forJavaScript(providerItem.getClinicFax())%>";

        <%	}
}

ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);
List<Program> programList = programDao.getAllActivePrograms();

if (CarlosProperties.getInstance().getBooleanProperty("consultation_program_letterhead_enabled", "true")) {
	if (programList != null) {
		for (Program program : programList) {
			String progNo = "prog_" + program.getId();
%>
        providerData['<%=progNo %>'] = {};
        providerData['<%=progNo %>'].address = '<%=(program.getAddress() != null && !program.getAddress().trim().isEmpty()) ? Encode.forJavaScript(program.getAddress()) : (Encode.forJavaScript(clinic.getClinicAddress()) + " " + Encode.forJavaScript(clinic.getClinicCity()) + " " + Encode.forJavaScript(clinic.getClinicProvince()) + " " + Encode.forJavaScript(clinic.getClinicPostal())) %>';
        providerData['<%=progNo %>'].phone = '<%=(program.getPhone() != null && !program.getPhone().trim().isEmpty()) ? Encode.forJavaScript(program.getPhone()) : Encode.forJavaScript(clinic.getClinicPhone()) %>';
        providerData['<%=progNo %>'].fax = '<%=(program.getFax() != null && !program.getFax().trim().isEmpty()) ? Encode.forJavaScript(program.getFax()) : Encode.forJavaScript(clinic.getClinicFax()) %>';
        <%
		}
	}
} %>

        function switchProvider(value) {
            if (value === -1) {
                document.getElementById("letterheadName").value = value;
                document.getElementById("letterheadAddress").value = '<%=Encode.forJavaScript(clinic.getClinicAddress()) + " " + Encode.forJavaScript(clinic.getClinicCity()) + " " + Encode.forJavaScript(clinic.getClinicProvince()) + " " + Encode.forJavaScript(clinic.getClinicPostal()) %>';
                document.getElementById("letterheadAddressSpan").textContent = '<%=Encode.forJavaScript(clinic.getClinicAddress()) + " " + Encode.forJavaScript(clinic.getClinicCity()) + " " + Encode.forJavaScript(clinic.getClinicProvince()) + " " + Encode.forJavaScript(clinic.getClinicPostal()) %>';
                document.getElementById("letterheadPhone").value = "<%=Encode.forJavaScript(clinic.getClinicPhone()) %>";
                document.getElementById("letterheadPhoneSpan").textContent = "<%=Encode.forJavaScript(clinic.getClinicPhone()) %>";
                document.getElementById("letterheadFax").value = "<%=Encode.forJavaScript(clinic.getClinicFax()) %>";

                document.getElementById("letterheadFaxSpan").textContent = "<%=Encode.forJavaScript(clinic.getClinicFax()) %>";

                let faxAccountOptions = document.getElementById("faxAccount");
                if (faxAccountOptions) {
                    faxAccountOptions.value = "<%=Encode.forJavaScript(clinic.getClinicFax()) %>".replace(/[^0-9.]/g, '');
                    for(let i = 0; i < faxAccountOptions.options.length; i++) {
                        let option = faxAccountOptions.options[i];
                        if(option.value === "<%=Encode.forJavaScript(clinic.getClinicFax()) %>".replace(/[^0-9.]/g, '')) {
                            faxAccountOptions.value = "<%=Encode.forJavaScript(clinic.getClinicFax())%>".replace(/[^0-9.]/g, '');
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

                document.getElementById('signatureImgTag').src = "<%=storedImgUrl %>" + encodeURIComponent(document.getElementById('signatureImg').value);
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
            var btn = document.getElementById("fax_button");
            if (btn) btn.disabled = disabled;
        }

        // If the user clicks the 'Print Preview' button, ensure that their unsaved changes are preserved, allowing them to stay on the same page. Achieve this by making an AJAX call.
        function getConsultFormPrintPreview(form) {
            form.submission.value = "And Print Preview";
            jQuery.ajax({
                type: "POST",
                url: "${ pageContext.request.contextPath }/encounter/RequestConsultation.do",
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

        function clearAppointmentTime() {
            document.getElementById('appointmentTimeDisplay').value = '';
            document.getElementById('appointmentHour').value = '';
            document.getElementById('appointmentMinute').value = '';
            document.getElementById('appointmentPm').value = 'AM';
        }

        function syncTimeToHiddenFields(timeVal) {
            if (!timeVal) {
                document.getElementById('appointmentHour').value = '';
                document.getElementById('appointmentMinute').value = '';
                document.getElementById('appointmentPm').value = 'AM';
                return;
            }
            var parts = timeVal.split(':');
            var hour24 = parseInt(parts[0], 10);
            var minute = parseInt(parts[1], 10);
            var pm = (hour24 >= 12) ? 'PM' : 'AM';
            var hour12 = hour24 % 12;
            if (hour12 === 0) hour12 = 12;
            document.getElementById('appointmentHour').value = String(hour12);
            document.getElementById('appointmentMinute').value = String(minute);
            document.getElementById('appointmentPm').value = pm;
        }

        function initAppointmentTimeDisplay() {
            var hour = document.getElementById('appointmentHour').value;
            var minute = document.getElementById('appointmentMinute').value;
            var pm = document.getElementById('appointmentPm').value;
            if (hour && hour !== '-1' && hour !== '' && minute !== '' && minute !== '-1') {
                var h = parseInt(hour, 10);
                var m = parseInt(minute, 10);
                if (pm === 'PM' && h !== 12) h += 12;
                else if (pm === 'AM' && h === 12) h = 0;
                var timeStr = String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0');
                document.getElementById('appointmentTimeDisplay').value = timeStr;
            }
        }
    </script>

    <%=WebUtils.popErrorMessagesAsAlert(session)%>

    <body onload="window.focus();disableDateFields();disableEditing();showSignatureImage();">
    <jsp:include page="/images/spinner.jsp" flush="true"/>
    <%
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="alert alert-danger m-2">
        <ul class="mb-0">
            <% for (String error : actionErrors) { %>
                <li><%= Encode.forHtml(error) %></li>
            <% } %>
        </ul>
    </div>
<% } %>
    <form id="EctConsultationFormRequest2Form" name="EctConsultationFormRequest2Form" action="${pageContext.request.contextPath}/encounter/RequestConsultation.do"
                method="post">
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
        <script>
            disableFields = true;
        </script>
        <%
            }
        %>

        <% if (!props.isConsultationFaxEnabled() || !CarlosProperties.getInstance().isPropertyActive("consultation_dynamic_labelling_enabled")) { %>
        <input type="hidden" name="providerNo" value="<%=providerNo%>">
        <% } %>
        <input type="hidden" name="demographicNo" id="demographicNo" value="<%=Encode.forHtmlAttribute(demo)%>">
        <input type="hidden" name="requestId" id="requestId" value="<%=requestId%>">
        <input type="hidden" name="ext_appNo" value="<%=request.getParameter("appNo") %>">
        <input type="hidden" name="source"
               value="<%=(requestId!=null)?thisForm.getSource():request.getParameter("source") %>">
        <input type="hidden" name="submission" value="">
        <input type="hidden" id="saved" value="false">
        <input type="hidden" id="contextPath" value="${pageContext.request.contextPath}">

        <%-- Page Header --%>
        <div class="page-header-bar d-flex align-items-center justify-content-between">
            <h4 class="page-header-title mb-0">
                <i class="fa-solid fa-file-medical me-2"></i>Consultation Request
            </h4>
            <div class="d-flex align-items-center gap-2">
                <span class="badge bg-light text-dark border" style="font-size:0.85rem;">
                    <%=Encode.forHtml(thisForm.getPatientName())%> &mdash; <%=Encode.forHtml(thisForm.getPatientSex())%> <%=Encode.forHtml(thisForm.getPatientAge())%>
                </span>
                <% if ("ocean".equals(props.get("cme_js"))) { %>
                    <span id="ocean" style="display:none"></span>
                    <% if (requestId == null) { %>
                    <span id="oceanReferButton" class="oceanRefer"></span>
                    <% } %>
                <% } %>
            </div>
        </div>

        <%-- Main Content: Sidebar + Form --%>
        <div class="container-fluid py-2">
        <div class="row g-3">

        <%-- Left Sidebar: Status & Attachments --%>
        <div class="col-md-3 col-lg-2 consult-sidebar">
                    <div class="card mb-2">
                        <div class="card-body p-2">
                            <small class="text-muted d-block"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgCreated"/></small>
                            <small class="fw-semibold"><%=Encode.forHtml(thisForm.getProviderName())%></small>
                        </div>
                    </div>

                    <div class="card mb-2">
                        <div class="card-header p-2 fw-semibold" style="font-size:0.85rem;">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgStatus"/>
                        </div>
                        <div class="card-body p-2">
                            <div class="form-check">
                                <input class="form-check-input" type="radio" name="status" value="1" id="status1" <%="1".equals(thisForm.getStatus()) ? "checked" : ""%>/>
                                <label class="form-check-label" for="status1" style="font-size:0.85rem;"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgNoth"/></label>
                            </div>
                            <div class="form-check">
                                <input class="form-check-input" type="radio" name="status" value="2" id="status2" <%="2".equals(thisForm.getStatus()) ? "checked" : ""%>/>
                                <label class="form-check-label" for="status2" style="font-size:0.85rem;"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgSpecCall"/></label>
                            </div>
                            <div class="form-check">
                                <input class="form-check-input" type="radio" name="status" value="3" id="status3" <%="3".equals(thisForm.getStatus()) ? "checked" : ""%>/>
                                <label class="form-check-label" for="status3" style="font-size:0.85rem;"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgPatCall"/></label>
                            </div>
                            <% if (thisForm.iseReferral()) { %>
                            <div class="form-check">
                                <input class="form-check-input" type="radio" name="status" value="5" id="status5" <%="5".equals(thisForm.getStatus()) ? "checked" : ""%>/>
                                <label class="form-check-label" for="status5" style="font-size:0.85rem;"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgBookCon"/></label>
                            </div>
                            <% } %>
                            <div class="form-check">
                                <input class="form-check-input" type="radio" name="status" value="4" id="status4" <%="4".equals(thisForm.getStatus()) ? "checked" : ""%>/>
                                <label class="form-check-label" for="status4" style="font-size:0.85rem;"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgCompleted"/></label>
                            </div>
                        </div>
                    </div>

                    <div class="card mb-2 consult-attachments">
                        <div class="card-body p-2">
                                <table id="attachedDocumentTable" style="border:none;background:transparent;">
                                    <tr>
                                        <td>

                                            <%
                                                if (thisForm.iseReferral()) {
                                            %>
                                                <%-- <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.attachDoc"/> --%>
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
                        </div>
                    </div>
        </div><%-- end sidebar --%>

        <%-- Right Main Column --%>
        <div class="col-md-9 col-lg-10">
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
                        <%-- Action Buttons --%>
                        <% if (thisForm.geteReferralId() == null) { %>
                                <div class="consult-control-panel consult-control-panel-sticky mb-2">
                                <% if (request.getAttribute("id") != null) { %>
                                <input name="update" type="button" class="btn btn-primary btn-sm"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnUpdate"/>"
                                       onclick="return checkForm('Update Consultation Request','EctConsultationFormRequest2Form');"/>
                                <input name="updateAndPrint" type="button" class="btn btn-primary btn-sm"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnUpdateAndPrint"/>"
                                       onclick="return checkForm('Update Consultation Request And Print Preview','EctConsultationFormRequest2Form');"/>
                                <input name="printPreview" type="button" class="btn btn-primary btn-sm" value="Print Preview"
                                       onclick="return checkForm('And Print Preview','EctConsultationFormRequest2Form');"/>

                                <c:if test="${EctConsultationFormRequest2Form.eReferral == true}">
                                    <input name="updateAndSendElectronicallyTop" type="button" class="btn btn-outline-info btn-sm"
                                           value="<fmt:message key='encounter.oscarConsultationRequest.ConsultationFormRequest.btnUpdateAndSendElectronicReferral'/>"
                                           onclick="return checkForm('Update_esend', 'EctConsultationFormRequest2Form');"/>
                                </c:if>

                                <oscar:oscarPropertiesCheck value="yes" property="consultation_fax_enabled">
                                    <input id="fax_button" name="updateAndFax" type="button" class="btn btn-primary btn-sm"
                                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnUpdateAndFax"/>"
                                           onclick="return checkForm('Update And Fax','EctConsultationFormRequest2Form');"/>
                                </oscar:oscarPropertiesCheck>

                                <% } else { %>
                                <input name="submitSaveOnly" type="button" class="btn btn-primary btn-sm"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnSubmit"/>"
                                       onclick="return checkForm('Submit Consultation Request','EctConsultationFormRequest2Form'); "/>
                                <input name="submitAndPrint" type="button" class="btn btn-primary btn-sm"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnSubmitAndPrint"/>"
                                       onclick="return checkForm('Submit Consultation Request And Print Preview','EctConsultationFormRequest2Form'); "/>

                                <c:if test="${EctConsultationFormRequest2Form.eReferral == true}">
                                    <input name="submitAndSendElectronicallyTop" type="button" class="btn btn-outline-info btn-sm"
                                           value="<fmt:message key='encounter.oscarConsultationRequest.ConsultationFormRequest.btnSubmitAndSendElectronicReferral'/>"
                                           onclick="return checkForm('Submit_esend', 'EctConsultationFormRequest2Form');"/>
                                </c:if>

                                <oscar:oscarPropertiesCheck value="yes" property="consultation_fax_enabled">
                                    <input id="fax_button" name="submitAndFax" type="button" class="btn btn-primary btn-sm"
                                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnSubmitAndFax"/>"
                                           onclick="return checkForm('Submit And Fax','EctConsultationFormRequest2Form');"/>
                                </oscar:oscarPropertiesCheck>
                                <c:if test="${EctConsultationFormRequest2Form.eReferral == true}">
                                    <input type="button" class="btn btn-outline-success btn-sm" value="Send eResponse"
                                           onclick="document.getElementById('saved').value='true'; document.location='${thisForm.oruR01UrlString(request)}'"/>
                                </c:if>

                                <% } %>
                                </div>
                        <% } %>
                        <%-- Patient Demographics Card --%>
                                <div class="card mb-2">
                                    <div class="card-header p-2 fw-semibold" style="font-size:0.85rem;">
                                        <i class="fa-solid fa-user me-1"></i>
                                        <a href="javascript:void(0);"
                                           onClick="popupAttach(600,900,'<%=request.getContextPath()%>/demographic/demographiccontrol.jsp?demographic_no=<%=demo%>&displaymode=edit&dboperation=search_detail')"><%=Encode.forHtml(thisForm.getPatientName())%></a>
                                    </div>
                                    <div class="card-body p-2">
                                        <div class="row g-2" style="font-size:0.85rem;">
                                            <div class="col-md-4">
                                                <small class="text-muted"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgAddress"/></small><br>
                                                <%=thisForm.getPatientAddress().replace("null", "")%>
                                            </div>
                                            <div class="col-md-4">
                                                <small class="text-muted"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgPhone"/></small>: <%=Encode.forHtml(thisForm.getPatientPhone())%><br>
                                                <small class="text-muted"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgWPhone"/></small>: <%=Encode.forHtml(thisForm.getPatientWPhone())%><br>
                                                <small class="text-muted"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgCellPhone"/></small>: <%=Encode.forHtml(thisForm.getPatientCellPhone())%><br>
                                                <small class="text-muted"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgEmail"/></small>: <%=Encode.forHtml(thisForm.getPatientEmail())%>
                                            </div>
                                            <div class="col-md-4">
                                                <small class="text-muted"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgBirthDate"/></small>: <%=Encode.forHtml(thisForm.getPatientDOB())%><br>
                                                <small class="text-muted"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgSex"/></small>: <%=Encode.forHtml(thisForm.getPatientSex())%><br>
                                                <small class="text-muted"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgHealthCard"/></small>: <%=Encode.forHtml(thisForm.getPatientHealthNum())%><%=Encode.forHtml(thisForm.getPatientHealthCardVersionCode())%><%=Encode.forHtml(thisForm.getPatientHealthCardType())%>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                        <div class="consultDemographicData">
                            <div class="row g-3">
                            <div class="col-md-7">
                                <% // Determine if curUser has selected a default practitioner in preferences
                                    UserProperty refPracProp = userPropertyDAO.getProp(providerNo,  UserProperty.DEFAULT_REF_PRACTITIONER);
                                    String refPrac = "";
                                    if (refPracProp != null && refPracProp.getValue() != null) {
                                        refPrac = refPracProp.getValue();
                                    }
                                %>

                                <table>
                                    <% if (props.isConsultationFaxEnabled() && CarlosProperties.getInstance().isPropertyActive("consultation_dynamic_labelling_enabled")) { %>
                                    <tr>
                                        <td class="consult-form-label" style="width:30%"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgAssociated2"/></td>
                                        <td class="consult-form-value" style="width:70%">

                                            <select name="providerNo" id="providerNoSelect" class="form-select form-select-sm">
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
                                        <td class="consult-form-label">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formRefDate"/>
                                        </td>

                                        <oscar:oscarPropertiesCheck value="false"
                                                                    property="CONSULTATION_LOCK_REFERRAL_DATE">
                                            <td class="consult-form-value">
                                                <%
                                                    if (request.getAttribute("id") != null) {
                                                %>
                                                <input type="date" class="form-control form-control-sm" id="referalDate" name="referalDate"
                                                           value="<%=Encode.forHtmlAttribute(thisForm.getReferalDate() != null ? thisForm.getReferalDate().replace("/", "-") : "")%>"/>
                                                <%
                                                } else {
                                                    // Format as YYYY-MM-DD for HTML5 date input
                                                    String refDateFormatted = year + "-" + (mon.length() == 1 ? "0" + mon : mon) + "-" + (day.length() == 1 ? "0" + day : day);
                                                %>
                                                <input type="date" class="form-control form-control-sm" id="referalDate" name="referalDate"
                                                           value="<%=refDateFormatted%>"/>
                                                <%
                                                    }
                                                %>
                                            </td>
                                        </oscar:oscarPropertiesCheck>

                                        <oscar:oscarPropertiesCheck value="true"
                                                                    property="CONSULTATION_LOCK_REFERRAL_DATE">

                                            <td class="consult-form-value">
                                                <%
                                                    String refDateFormattedLocked = year + "-" + (mon.length() == 1 ? "0" + mon : mon) + "-" + (day.length() == 1 ? "0" + day : day);
                                                %>
                                                <input type="date" class="form-control form-control-sm" id="referalDate" name="referalDate" readonly
                                                           value="<%=refDateFormattedLocked%>"/>
                                            </td>

                                        </oscar:oscarPropertiesCheck>

                                    </tr>
                                    <oscar:oscarPropertiesCheck value="false"
                                                                property="ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS"
                                                                defaultVal="false">
                                        <tr>
                                            <td class="consult-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formService"/>
                                            </td>
                                            <td class="consult-form-value">
                                                <% if (thisForm.iseReferral() && !thisForm.geteReferralService().isEmpty()) { %>
                                                <%=Encode.forHtml(thisForm.geteReferralService())%>
                                                <% } else { %>
                                                <input type="hidden" id="service" name="service" value=""/>
                                                <input type="text" id="serviceInput" class="form-control form-control-sm"
                                                       autocomplete="off"
                                                       placeholder="<fmt:setBundle basename='oscarResources'/><fmt:message key='consultationList.header.service'/>"/>
                                                <% } %>
                                            </td>
                                        </tr>
                                    </oscar:oscarPropertiesCheck>
                                    <tr>
                                        <td class="consult-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formCons"/>
                                        </td>
                                        <td class="consult-form-value">
                                            <% if (thisForm.iseReferral()) { %>

                                            <%=Encode.forHtml(thisForm.getProfessionalSpecialistName())%>

                                            <% } else if (CarlosProperties.getInstance().getBooleanProperty("ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS", "true")) { %>

                                            <select name="specialist" id="specialist" class="form-select form-select-sm">
                                                <c:forEach items="${ healthCareTeam }" var="contact" varStatus="loop">
                                                    <option value="${ contact.id }" ${ specialist eq contact.id ? 'selected' : ''} >
                                                            ${ contact.details.formattedName } ( ${ contact.role } )
                                                    </option>
                                                </c:forEach>
                                            </select>

                                            <% } else { %>

                                            <span id="consult-disclaimer"
                                                  class="consult-disclaimer-indicator"
                                                  title="When consult was saved this was the saved consultant but is no longer on this specialist list.">*</span>
                                            <input type="hidden" id="specialist" name="specialist" value=""/>
                                            <input type="text" id="specialistInput" class="form-control form-control-sm"
                                                   autocomplete="off"
                                                   placeholder="<fmt:setBundle basename='oscarResources'/><fmt:message key='consultationList.header.consultant'/>"/>

                                            <%} // end specialist list condition block %>
                                        </td>
                                    </tr>
                                    <oscar:oscarPropertiesCheck value="true"
                                                                property="ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS"
                                                                defaultVal="false">
                                        <tr>
                                            <td class="consult-form-label">
                                                <input type="hidden" id="hctService" name="service" value="0"/>
                                            </td>
                                            <td class="consult-form-label" style="font-size:11px;">
                                                <a href="javascript:void(0);"
                                                   onclick="popupPage(500,700,'${ctx}/demographic/Contact.do?method=manageContactList&contactList=HCT&view=detached&demographic_no=<%=demo%>' ); return false;">
                                                    edit Health Care Team
                                                </a>
                                            </td>
                                        </tr>
                                    </oscar:oscarPropertiesCheck>

                                    <tr>
                                        <td class="consult-form-label">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formInstructions"/> </br>
                                            <br>
                                            <button type="button" id="eFormButton" style="display: none"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.eFormReferralInstructions"/></button>
                                        </td>
                                        <td class="consult-form-value">
                                            <textarea id="annotation" class="form-control form-control-sm" style="color: var(--carlos-primary);" rows="4" readonly></textarea>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="consult-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formUrgency"/></td>
                                        <td class="consult-form-value">
                                            <select name="urgency" id="urgency" class="form-select form-select-sm">
                                                <option value="2" <%="2".equals(thisForm.getUrgency()) ? "selected" : ""%>>
                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgNUrgent"/>
                                                </option>
                                                <option value="1" <%="1".equals(thisForm.getUrgency()) ? "selected" : ""%>>
                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgUrgent"/>
                                                </option>
                                                <option value="3" <%="3".equals(thisForm.getUrgency()) ? "selected" : ""%>>
                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgReturn"/>
                                                </option>
                                            </select>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="consult-form-label">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formPhone"/>
                                        </td>
                                        <td class="consult-form-value"><input readonly type="text" name="phone" class="form-control form-control-sm"
                                                                 value="<%=Encode.forHtmlAttribute(thisForm.getProfessionalSpecialistPhone())%>"/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="consult-form-label">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formFax"/>
                                            <c:if test="${ not empty consultUtil.specialistFaxLog.status }">
                                                <span style="font-size:80%;color:red;">Status: <c:out
                                                        value="${ consultUtil.specialistFaxLog.status }"/></span>
                                            </c:if>
                                        </td>
                                        <td class="consult-form-value">
                                            <input readonly type="text" name="fax" class="form-control form-control-sm"/>
                                        </td>
                                    </tr>

                                    <tr>
                                        <td class="consult-form-label">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formAddr"/>
                                        </td>
                                        <td class="consult-form-value">
                                            <textarea readonly name="address" class="form-control form-control-sm"
                                                      rows="5"><%=Encode.forHtml(thisForm.getProfessionalSpecialistAddress())%></textarea>
                                        </td>
                                    </tr>

                                    <oscar:oscarPropertiesCheck defaultVal="false" value="true"
                                                                property="CONSULTATION_APPOINTMENT_INSTRUCTIONS_LOOKUP">
                                        <tr>
                                            <td class="consult-form-label">
                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.appointmentInstr"/>
                                            </td>
                                            <td class="consult-form-value">
                                                <select name="appointmentInstructions" class="form-select form-select-sm"
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
                                            <td class="consult-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formPatientBook"/></td>
                                            <td class="consult-form-value"><input type="checkbox" name="patientWillBook" value="1" onclick="disableDateFields()" /></td>
                                        </tr>
                                    </oscar:oscarPropertiesCheck>


                                    <tr>
                                        <td class="consult-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.btnAppointmentDate"/>
                                        </td>
                                        <td class="consult-form-value">
                                            <div class="input-group input-group-sm" style="max-width:220px;">
                                                <input type="date" class="form-control form-control-sm" id="appointmentDate" name="appointmentDate"
                                                       value="<%=Encode.forHtmlAttribute(thisForm.getAppointmentDate() != null ? thisForm.getAppointmentDate().replace("/", "-") : "")%>"/>
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="consult-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formAppointmentTime"/>
                                        </td>
                                        <td class="consult-form-value">
                                            <%-- Hidden fields keep existing server-side processing unchanged --%>
                                            <input type="hidden" id="appointmentHour" name="appointmentHour"
                                                   value="<%=Encode.forHtmlAttribute(thisForm.getAppointmentHour() != null ? thisForm.getAppointmentHour() : "")%>"/>
                                            <input type="hidden" id="appointmentMinute" name="appointmentMinute"
                                                   value="<%=Encode.forHtmlAttribute(thisForm.getAppointmentMinute() != null ? thisForm.getAppointmentMinute() : "")%>"/>
                                            <input type="hidden" id="appointmentPm" name="appointmentPm"
                                                   value="<%=Encode.forHtmlAttribute(thisForm.getAppointmentPm() != null ? thisForm.getAppointmentPm() : "AM")%>"/>
                                            <div class="input-group input-group-sm" style="max-width:180px;">
                                                <input type="time" class="form-control form-control-sm" id="appointmentTimeDisplay"
                                                       oninput="syncTimeToHiddenFields(this.value)"/>
                                                <button type="button" class="btn btn-outline-secondary" id="clearTimeBtn"
                                                        title="<fmt:setBundle basename='oscarResources'/><fmt:message key='encounter.oscarConsultationRequest.ConsultationFormRequest.btnClearTime'/>"
                                                        onclick="clearAppointmentTime();">&times;</button>
                                            </div>
                                        </td>
                                    </tr>
                                    <%if (bMultisites) { %>
                                    <tr>
                                        <td class="consult-form-label">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.siteName"/>
                                        </td>
                                        <td>
                                            <select name="siteName" id="siteName" class="form-select form-select-sm">
                                                <% for (int i = 0; i < vecAddressName.size(); i++) {
                                                    String te = vecAddressName.get(i);
                                                    String bg = bgColor.get(i);
                                                    if (te.equals(defaultSiteName))
                                                        defaultSiteId = siteIds.get(i);
                                                %>
                                                <option value="<%=Encode.forHtmlAttribute(te)%>"
                                                             style="background-color:<%=Encode.forCssString(bg)%>"><%=Encode.forHtmlContent(te)%>
                                                </option>
                                                <% }%>
                                            </select>
                                        </td>
                                    </tr>
                                    <%} %>
                                </table>
                            </div><%-- end col-md-7 --%>
                            <div class="col-md-5">
                                <table class="table table-sm table-bordered mb-0" style="font-size:0.85rem;">
                                    <tr id="conReqSendTo">
                                        <td class="consult-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgSendTo"/>
                                        </td>
                                        <td class="consult-form-value"><select name="sendTo" id="sendTo" class="form-select form-select-sm">
                                            <option value="-1" <%="-1".equals(thisForm.getSendTo()) ? "selected" : ""%>>---- <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.msgTeams"/> ----</option>
                                            <%
                                                for (int i = 0; i < consultUtil.teamVec.size(); i++) {
                                                    String te = (String) consultUtil.teamVec.elementAt(i);
                                                    String selectedTeam = (te.equals(thisForm.getSendTo())) ? "selected" : "";
                                            %>
                                            <option value="<%=Encode.forHtmlAttribute(te)%>" <%=selectedTeam%>><%=Encode.forHtmlContent(te)%>
                                            </option>
                                            <%
                                                }
                                            %>
                                        </select></td>
                                    </tr>

                                    <tr>
                                        <td colspan="2" class="consult-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formAppointmentNotes"/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="2" class="consult-form-value"><textarea class="form-control form-control-sm"
                                                name="appointmentNotes"><%=Encode.forHtmlContent(thisForm.getAppointmentNotes())%></textarea></td>
                                    </tr>


                                    <tr>
                                        <td class="consult-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formLastFollowup"/>
                                        </td>
                                        <td class="consult-form-value">
                                            <input type="date" class="form-control form-control-sm" id="followUpDate" name="followUpDate"
                                                       value="<%=thisForm.getFollowUpDate() != null ? Encode.forHtmlAttribute(thisForm.getFollowUpDate().replace("/", "-")) : ""%>"/>
                                        </td>

                                    </tr>

                                    <%
                                        if (thisForm.getFdid() != null) {
                                    %>
                                    <tr>
                                        <td class="consult-form-label">EForm
                                        </td>
                                        <td class="consult-form-value">
                                            <a href="<%=request.getContextPath()%>/eform/efmshowform_data.jsp?fdid=<%=thisForm.getFdid() %>">Click
                                                to view</a>
                                        </td>
                                    </tr>
                                    <%
                                        }
                                    %>
                                </table>
                            </div><%-- end col-md-5 --%>
                            </div><%-- end row --%>
                        </div><%-- end consultDemographicData --%>

                        <%-- Letterhead (collapsible, default collapsed) --%>
                        <div class="consult-section-heading">
                            <a class="consult-collapse-heading text-decoration-none text-dark w-100" data-bs-toggle="collapse" href="#collapseLetterhead" role="button" aria-expanded="false" aria-controls="collapseLetterhead">
                                Letterhead
                                <i class="fa-solid fa-chevron-down collapse-icon"></i>
                            </a>
                        </div>
                        <div class="collapse" id="collapseLetterhead">
                        <table class="w-100">
                                    <tr>

                                        <td class="consult-form-label">
                                            <label for="letterheadName">
                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.letterheadName"/>
                                            </label>
                                        </td>
                                        <td class="consult-form-value">
                                            <select name="letterheadName" id="letterheadName" class="form-select form-select-sm">
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

                                                    if (CarlosProperties.getInstance().getBooleanProperty("consultation_program_letterhead_enabled", "true")) {
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
                                        <td class="consult-form-label">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.letterheadAddress"/>
                                        </td>
                                        <td class="consult-form-value">
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
                                        <td class="consult-form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.letterheadPhone"/>
                                        </td>
                                        <td class="consult-form-value">
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
                                        <td class="consult-form-label">
                                            <label for="letterheadFax"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.letterheadFax"/></label>
                                        </td>
                                        <td  class="consult-form-value" style="width:70%;">
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
				<% if (props.isConsultationFaxEnabled()) { %>
                        <div class="consult-section-heading">Fax Account</div>
                                <table class="w-100">
								<tr>
									<td class="consult-form-label" style="width:30%;">
										<label for="faxAccount">Select Account</label>
									</td>
									<td class="consult-form-value" style="width:70%;">
                                            <%
                                                FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
                                                List<FaxConfig> faxConfigs = faxConfigDao.findAll(null, null);
                                            %>
										<select name="faxAccount" id="faxAccount" class="form-select form-select-sm">
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
                        <div class="consult-section-heading">Additional Fax Recipients</div>
                                <table style="border-collapse:collapse;" id="addFaxRecipient" class="w-100">
                                    <tr>
                                        <td class="consult-form-label">
                                            Name <input type="text" id="searchHealthCareTeamInput" value=""
                                                        placeholder="last, first"/>
                                        </td>
                                        <td class="consult-form-label">
                                            Fax <input type="text" id="copytoSpecialistFax" placeholder="xxx-xxx-xxxx"
                                                       value=""/>
                                        </td>
                                        <td class="consult-form-label">
                                            <button onclick="AddOtherFaxProvider(); return false;"> Add Recipient
                                            </button>
                                        </td>
                                    </tr>
                                    <c:if test="${ not empty consultUtil.copyToFaxLog }">
                                        <c:forEach items="${ consultUtil.copyToFaxLog }" var="faxLog">
                                            <tr>
                                                <td class="consult-form-label"><c:out value="${ faxLog.name }"/></td>
                                                <td class="consult-form-label"><c:out value="${ faxLog.fax }"/></td>
                                                <td class="consult-form-label">
                                                    <c:out value="${ faxLog.status }"/>
                                                    <c:out value="${ faxLog.sent }"/>
                                                </td>
                                            </tr>
                                        </c:forEach>
                                    </c:if>
                                </table>
                        <% } %>
                        </div><%-- end collapseLetterhead --%>

                        <%-- Clinical Sections --%>
                        <div class="consult-section-heading">
                                <a class="consult-collapse-heading text-decoration-none text-dark w-100" data-bs-toggle="collapse" href="#collapseReason" role="button" aria-expanded="true" aria-controls="collapseReason">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formReason"/>
                                    <i class="fa-solid fa-chevron-down collapse-icon"></i>
                                </a>
                        </div>
                                <div class="collapse show" id="collapseReason">
                                <textarea rows="6" name="reasonForConsultation" class="form-control"><%=Encode.forHtmlContent(thisForm.getReasonForConsultation())%></textarea>
                                </div>
                        <div class="consult-section-heading">
                                <div class="d-flex align-items-center justify-content-between w-100">
                                    <a class="consult-collapse-heading text-decoration-none text-dark" data-bs-toggle="collapse" href="#collapseClinical" role="button" aria-expanded="true" aria-controls="collapseClinical">
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formClinInf"/>
                                        <i class="fa-solid fa-chevron-down collapse-icon"></i>
                                    </a>
                                    <% if (thisForm.geteReferralId() == null) { %>
                                    <%-- Import dropdown: data-target (not data-bs-target) is required because
                                         buildImportMenus() reads it via jQuery .data('target') to generate
                                         menu item IDs that reference the correct textarea. --%>
                                    <div class="dropdown consult-import-dropdown">
                                        <button class="btn btn-outline-secondary btn-sm dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false" style="font-size:0.75rem;">
                                            <i class="fa-solid fa-file-import me-1"></i>Import
                                        </button>
                                        <ul class="dropdown-menu dropdown-menu-end consult-import-menu" data-target="clinicalInformation"></ul>
                                    </div>
                                    <% } %>
                                </div>
                        </div>
                                <div class="collapse show" id="collapseClinical">
                                <textarea rows="6" id="clinicalInformation" class="form-control"
                                               name="clinicalInformation"><%=Encode.forHtmlContent(thisForm.getClinicalInformation())%></textarea>
                                </div>
                        <div class="consult-section-heading">
                                <div class="d-flex align-items-center justify-content-between w-100">
                                    <a class="consult-collapse-heading text-decoration-none text-dark" data-bs-toggle="collapse" href="#collapseConcurrent" role="button" aria-expanded="true" aria-controls="collapseConcurrent">
                                            <%
                                                if (props.getProperty("significantConcurrentProblemsTitle", "").length() > 1) {
                                                    out.print(props.getProperty("significantConcurrentProblemsTitle", ""));
                                                } else {
                                            %><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formSignificantProblems"/><%
                                                }
                                            %>
                                        <i class="fa-solid fa-chevron-down collapse-icon"></i>
                                    </a>
                                    <% if (thisForm.geteReferralId() == null) { %>
                                    <div class="dropdown consult-import-dropdown">
                                        <button class="btn btn-outline-secondary btn-sm dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false" style="font-size:0.75rem;">
                                            <i class="fa-solid fa-file-import me-1"></i>Import
                                        </button>
                                        <ul class="dropdown-menu dropdown-menu-end consult-import-menu" data-target="concurrentProblems"></ul>
                                    </div>
                                    <% } %>
                                </div>
                        </div>
                        <div id="trConcurrentProblems">
                                <div class="collapse show" id="collapseConcurrent">
                                <textarea rows="6" id="concurrentProblems" class="form-control"
                                               name="concurrentProblems"><%=Encode.forHtmlContent(thisForm.getConcurrentProblems())%></textarea>
                                </div>
                        </div>
                        <div class="consult-section-heading">
                                <div class="d-flex align-items-center justify-content-between w-100">
                                    <a class="consult-collapse-heading text-decoration-none text-dark" data-bs-toggle="collapse" href="#collapseMedications" role="button" aria-expanded="true" aria-controls="collapseMedications">
                                            <% if (props.getProperty("currentMedicationsTitle", "").length() > 1) {
                                                out.print(props.getProperty("currentMedicationsTitle", ""));
                                            } else { %><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formCurrMedications"/><% } %>
                                        <i class="fa-solid fa-chevron-down collapse-icon"></i>
                                    </a>
                                    <% if (thisForm.geteReferralId() == null) { %>
                                    <div class="dropdown consult-import-dropdown">
                                        <button class="btn btn-outline-secondary btn-sm dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false" style="font-size:0.75rem;">
                                            <i class="fa-solid fa-file-import me-1"></i>Import
                                        </button>
                                        <ul class="dropdown-menu dropdown-menu-end consult-import-menu-meds" data-target="currentMedications"></ul>
                                    </div>
                                    <% } %>
                                </div>
                        </div>
                                <div class="collapse show" id="collapseMedications">
                                <textarea rows="6" id="currentMedications" class="form-control"
                                               name="currentMedications"><%=Encode.forHtmlContent(thisForm.getCurrentMedications())%></textarea>
                                </div>
                        <div class="consult-section-heading">
                                <div class="d-flex align-items-center justify-content-between w-100">
                                    <a class="consult-collapse-heading text-decoration-none text-dark" data-bs-toggle="collapse" href="#collapseAllergies" role="button" aria-expanded="true" aria-controls="collapseAllergies">
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formAllergies"/>
                                        <i class="fa-solid fa-chevron-down collapse-icon"></i>
                                    </a>
                                    <% if (thisForm.geteReferralId() == null) { %>
                                    <a class="btn btn-outline-secondary btn-sm medicationData" id="fetchAllergies_allergies" href="javascript:void(0);" style="font-size:0.75rem;">
                                        <i class="fa-solid fa-file-import me-1"></i>Import Allergies
                                    </a>
                                    <% } %>
                                </div>
                        </div>
                                <div class="collapse show" id="collapseAllergies">
                                <textarea rows="6" id="allergies" name="allergies" class="form-control"><%=Encode.forHtmlContent(thisForm.getAllergies())%></textarea>
                                </div>

                        <%
                            if (props.isConsultationSignatureEnabled()) {
                                // Check for provider signature stamp
                                UserProperty consultSigProp = userPropertyDAO.getProp(providerNo, UserProperty.PROVIDER_CONSULT_SIGNATURE);
                                boolean hasStampSignature = (consultSigProp != null && consultSigProp.getValue() != null && !consultSigProp.getValue().trim().isEmpty());
                        %>
                        <div class="consult-section-heading"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.formSignature"/></div>
                        <div>
                                <input type="hidden" name="newSignature" id="newSignature" value="<%= hasStampSignature ? "false" : "true" %>"/>
                                <input type="hidden" name="signatureImg" id="signatureImg"
                                       value="<%=(consultUtil.signatureImg != null ? Encode.forHtmlAttribute(consultUtil.signatureImg) : "") %>"/>
                                <input type="hidden" name="newSignatureImg" id="newSignatureImg"
                                       value="<%=signatureRequestId %>"/>

                                <% if (hasStampSignature) { %>
                                <fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.altProviderSig" var="providerSigAlt"/>
                                <div id="signatureShow" style="display: block;">
                                    <img id="signatureImgTag" src="<%=request.getContextPath()%>/provider/providerSignatureImage.do"
                                         alt="${e:forHtmlAttribute(providerSigAlt)}" style="max-height:120px;"/>
                                </div>
                                <div id="signatureFrame" style="display: none;">
                                    <iframe style="width:500px; height:132px;"
                                        src="<%= request.getContextPath() %>/signature_pad/tabletSignature.jsp?inWindow=true&<%=DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY%>=<%=signatureRequestId%>&<%=ModuleType.class.getSimpleName()%>=<%=ModuleType.CONSULTATION%>" ></iframe>
                                </div>
                                <div style="margin-top:5px;">
                                    <a href="javascript:void(0)" onclick="document.getElementById('signatureShow').style.display='none';document.getElementById('signatureFrame').style.display='block';document.getElementById('newSignature').value='true';">
                                        <fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.linkResignManually"/>
                                    </a>
                                </div>
                                <% } else { %>
                                <div id="signatureShow" style="display: none;">
                                    <img id="signatureImgTag" src=""/>
                                </div>

                                <iframe style="width:500px; height:132px;" id="signatureFrame"
							src="<%= request.getContextPath() %>/signature_pad/tabletSignature.jsp?inWindow=true&<%=DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY%>=<%=signatureRequestId%>&<%=ModuleType.class.getSimpleName()%>=<%=ModuleType.CONSULTATION%>" ></iframe>
                                <% } %>
                        </div>
                        <% }%>

                        <oscar:oscarPropertiesCheck value="false"
                                                    property="ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS"
                                                    defaultVal="false">
                            <script type="text/javascript">
                                //<!--
                                // Initialize appointment time display from saved values
                                initAppointmentTimeDisplay();

                                // Load services and all-specialists data, then wire autocompletes
                                loadServicesFromServer(function() {
                                    loadAllSpecialistsData(function() {
                                        initServiceAutocomplete();
                                        initSpecialistAutocomplete();
                                        initializeConsultation(
                                            '<%=Encode.forJavaScript(String.valueOf(consultUtil.service))%>',
                                            '<%=((consultUtil.service==null)?"":Encode.forJavaScript(consultUtil.getServiceName(consultUtil.service.toString())))%>',
                                            '<%=Encode.forJavaScript(String.valueOf(consultUtil.specialist))%>',
                                            '<%=((consultUtil.specialist==null)?"":Encode.forJavaScript(consultUtil.getSpecailistsName(consultUtil.specialist.toString())))%>',
                                            '<%=Encode.forJavaScript(consultUtil.specPhone)%>',
                                            '<%=Encode.forJavaScript(consultUtil.specFax)%>',
                                            '<%=Encode.forJavaScript(consultUtil.specAddr)%>'
                                        );
                                    });
                                });
                                //-->
                            </script>
                        </oscar:oscarPropertiesCheck>

                        <oscar:oscarPropertiesCheck value="true"
                                                    property="ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS"
                                                    defaultVal="false">
                            <script type="text/javascript">
                                const specialist = "${e:forJavaScript(consultUtil.specialist)}";
                                const servicevalue = "${e:forJavaScript(consultUtil.service)}";

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

        </div><%-- end main column --%>
        </div><%-- end row --%>
        </div><%-- end container-fluid --%>
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

            // Event listener for letterhead select (replaces inline onchange)
            var letterheadSelect = document.getElementById('letterheadName');
            if (letterheadSelect) {
                letterheadSelect.addEventListener('change', function() { switchProvider(this.value); });
            }

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
                    switchProvider("<%=Encode.forJavaScript(clinic.getClinicName())%>");
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
                                // addFormIfNotFound only handles form (formNo) attachments;
                                // skip pre-check for labs, docs, eForms, HRM not found in dialog
                                if (delegate.startsWith("#formNo")) {
                                    element = addFormIfNotFound(data, '<%=demo%>', delegate);
                                } else {
                                    return;
                                }
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
                            var rowId = "entry_" + element.attr("name") + element.val();

                            // skip if this entry was already added (e.g. dialog opened/closed multiple times)
                            if (jQuery('#EctConsultationFormRequest2Form').find("#" + rowId).length > 0) {
                                return;
                            }

                            var input = jQuery("<input />", {
                                type: 'hidden',
                                name: element.attr('name'),
                                value: element.val(),
                                id: "delegate_" + element.attr('id'),
                                class: 'delegateAttachment'
                            });
                            var row = jQuery("<tr>", {id: rowId});
                            var column = jQuery("<td>");
                            var target = "#attachedDocumentsTable";

                            if (element.hasClass("lab_check")) {
                                target = "#attachedLabsTable";
                            } else if (element.hasClass("eForm_check")) {
                                target = "#attachedEFormsTable";
                            } else if (element.hasClass("form_check")) {
                                target = "#attachedFormsTable";
                            } else if (element.hasClass("hrm_check")) {
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


