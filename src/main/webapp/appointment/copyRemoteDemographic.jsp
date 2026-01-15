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
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
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

<%@page import="java.util.GregorianCalendar" %>
<%@page import="java.sql.ResultSet" %>
<%@page import="ca.openosp.openo.caisi_integrator.ws.DemographicWs" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="java.net.URLEncoder" %>
<%@page import="java.nio.charset.StandardCharsets" %>
<%@page import="java.util.Enumeration" %>
<%@page import="ca.openosp.openo.commn.model.Demographic" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.dao.DemographicDao" %>
<%@page import="ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager" %>
<%@page import="ca.openosp.openo.utility.WebUtils" %>
<%@page import="ca.openosp.openo.utility.MiscUtils" %>
<%@ page import="ca.openosp.openo.commn.model.Admission" %>
<%@ page import="ca.openosp.openo.commn.dao.AdmissionDao" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProgramDao" %>
<%@page import="ca.openosp.openo.PMmodule.model.Program" %>
<%@page import="ca.openosp.openo.managers.PatientConsentManager" %>
<%@page import="ca.openosp.openo.commn.model.ConsentType" %>
<%@page import="ca.openosp.openo.commn.model.Facility" %>
<%@page import="ca.openosp.openo.caisi_integrator.ws.GetConsentTransfer" %>
<%@page import="ca.openosp.openo.commn.model.UserProperty" %>
<%@ page import="ca.openosp.openo.encounter.data.EctProgram" %>
<%@ page import="ca.openosp.MyDateFormat" %>
<%
    AdmissionDao admissionDao = (AdmissionDao) SpringUtils.getBean(AdmissionDao.class);
    ProgramDao programDao = SpringUtils.getBean(ProgramDao.class);

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    int remoteFacilityId = Integer.parseInt(request.getParameter("remoteFacilityId"));
    int remoteDemographicId = Integer.parseInt(request.getParameter("demographic_no"));

    //--- make new local demographic record ---
    Demographic demographic = CaisiIntegratorManager.makeUnpersistedDemographicObjectFromRemoteEntry(loggedInInfo, loggedInInfo.getCurrentFacility(), remoteFacilityId, remoteDemographicId);
    DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    demographicDao.saveClient(demographic);
    Integer demoNoRightNow = demographic.getDemographicNo(); // temp use for debugging

    //--- set the local patient consent based on the Integrator consent state for this demographic. ---
    GetConsentTransfer consentTransfer = CaisiIntegratorManager.getConsentState(loggedInInfo, loggedInInfo.getCurrentFacility(), remoteFacilityId, remoteDemographicId);
    Boolean consented = null;

    if (consentTransfer != null) {
        consented = ("ALL".equals(consentTransfer.getConsentState().value()));
    }

    if (consented != null) {
        PatientConsentManager patientConsentManager = SpringUtils.getBean(PatientConsentManager.class);
        ConsentType consentType = patientConsentManager.getConsentType(UserProperty.INTEGRATOR_PATIENT_CONSENT);
        patientConsentManager.setConsent(loggedInInfo, demographic.getDemographicNo(), consentType.getId(), consented);
    }

    //--- link the demographic on the integrator so associated data shows up ---
    DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());

    String providerNo = loggedInInfo.getLoggedInProviderNo();
    demographicWs.linkDemographics(providerNo, demographic.getDemographicNo(), remoteFacilityId, remoteDemographicId);


    MiscUtils.getLogger().info("LINK DEMOGRAPHIC #### ProviderNo :" + providerNo + " ,demo No :" + demographic.getDemographicNo() + " , remoteFacilityId :" + remoteFacilityId + " ,remoteDemographicId " + remoteDemographicId + " orig demo " + demoNoRightNow);


    //--- add to program so the caisi program access filtering doesn't cause a sec problem ---
    EctProgram program = new EctProgram(request.getSession());
    String progId = program.getProgram(providerNo);
    if (progId.equals("0")) {
        Program p = programDao.getProgramByName("OSCAR");
        if (p != null) {
            progId = p.getId().toString();
        }
    }
    GregorianCalendar cal = new GregorianCalendar();
    String admissionDate = "" + cal.get(GregorianCalendar.YEAR) + '-' + (cal.get(GregorianCalendar.MONTH) + 1) + '-' + cal.get(GregorianCalendar.DAY_OF_MONTH);

    Admission admission = new Admission();
    admission.setClientId(demographic.getDemographicNo());
    admission.setProgramId(Integer.parseInt(progId));
    admission.setProviderNo(providerNo);
    admission.setAdmissionDate(MyDateFormat.getSysDate(admissionDate));
    admission.setAdmissionStatus("current");
    admission.setTeamId(null);
    admission.setTemporaryAdmission(false);
    admission.setAdmissionFromTransfer(false);
    admission.setDischargeFromTransfer(false);
    admission.setRadioDischargeReason("0");
    admission.setClientStatusId(null);
    admissionDao.saveAdmission(admission);

    StringBuilder redirect = new StringBuilder();

    if (request.getParameter("originalPage") != null) {

        redirect.append(request.getParameter("originalPage"));
        redirect.append("?");

        redirect.append("demographic_no=");
        redirect.append(demographic.getDemographicNo());

        redirect.append("&doctor_no=");
        redirect.append(request.getParameter("provider_no"));
        @SuppressWarnings("unchecked")
        Enumeration<String> e = request.getParameterNames();
        while (e.hasMoreElements()) {
            String key = e.nextElement();

            if (key.equals("demographic_no") || key.equals("doctor_no") || key.equals("originalPage")) {
                // ignore these parameters
            } else {
                redirect.append("&");
                redirect.append(key);
                redirect.append("=");
                redirect.append(URLEncoder.encode(request.getParameter(key), StandardCharsets.UTF_8));
            }
        }
    } else {
        redirect.append(request.getContextPath() + "/close.html");
    }
    response.sendRedirect(redirect.toString());
%>
