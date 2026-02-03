<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>


<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page
        import="io.github.carlos_emr.carlos.PMmodule.caisi_integrator.CaisiIntegratorManager, io.github.carlos_emr.carlos.utility.LoggedInInfo, io.github.carlos_emr.carlos.commn.model.Facility" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    EctSessionBean bean = null;
    if ((bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean")) == null) {
        response.sendRedirect("error.jsp");
        return;
    }

    Facility facility = loggedInInfo.getCurrentFacility();
    String demoNo = bean.demographicNo;
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    Demographic demographic = demographicManager.getDemographicWithExt(loggedInInfo, Integer.parseInt(demoNo));

    // this is accessed in the newEncounterLayout after this header is included.
    String privateConsentEnabledProperty = OscarProperties.getInstance().getProperty("privateConsentEnabled");
    boolean privateConsentEnabled = privateConsentEnabledProperty != null && privateConsentEnabledProperty.equals("true");

%>

<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<div id="header-top-row">
    <div id="left-column">
        <div id="branding-logo">
            <img alt="OSCAR EMR" src="<%=request.getContextPath()%>/images/oscar_logo_small.png" width="19px">
        </div>
        <%= demographic.getStandardIdentificationHTML(request.getContextPath()) %>
    </div>
    <div id="right-column">
        <div id="userSettings" style="align-self: center; margin-right:15px;">
            <ul id="userSettingsMenu" style="display: flex; gap:5px;">
                <li>
                    <a title="Scratch Pad" href="javascript:void(0)"
                       onClick="window.open('${ctx}/scratch/index.jsp','scratch','height=700px, width=1024px')">
                        <div class="glyphicon">
                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor"
                                 class="bi bi-card-list" viewBox="0 0 16 16">
                                <path d="M14.5 3a.5.5 0 0 1 .5.5v9a.5.5 0 0 1-.5.5h-13a.5.5 0 0 1-.5-.5v-9a.5.5 0 0 1 .5-.5zm-13-1A1.5 1.5 0 0 0 0 3.5v9A1.5 1.5 0 0 0 1.5 14h13a1.5 1.5 0 0 0 1.5-1.5v-9A1.5 1.5 0 0 0 14.5 2z"></path>
                                <path d="M5 8a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7A.5.5 0 0 1 5 8m0-2.5a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5m0 5a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5m-1-5a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0M4 8a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0m0 2.5a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0"></path>
                            </svg>
                        </div>
                    </a>
                </li>
                <li>
                    <a href="javascript:void(0)" style="display: flex; align-items: flex-end;"
                       onClick="window.open('${ctx}/provider/providerpreference.jsp?provider_no=<%=loggedInInfo.getLoggedInProviderNo()%>', 'height=680px,width=715px')"
                       title='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.msgSettings"/>'>
                        <div class="glyphicon">
                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor"
                                 class="bi bi-person-fill" viewBox="0 0 16 16">
                                <path d="M3 14s-1 0-1-1 1-4 6-4 6 3 6 4-1 1-1 1zm5-6a3 3 0 1 0 0-6 3 3 0 0 0 0 6"></path>
                            </svg>
                        </div>
                        <div>
                            <c:out value='<%= loggedInInfo.getLoggedInProvider().getFirstName() + " " + loggedInInfo.getLoggedInProvider().getLastName() %>'/>
                        </div>
                    </a>
                </li>
            </ul>
        </div>
    </div>
</div>

<div id="header-bottom-row">
    <% if (OscarProperties.getInstance().hasProperty("ONTARIO_MD_INCOMINGREQUESTOR")) {%>
    <div>
        <a href="javascript:void(0);" onClick="popupPage(600,175,'Calculators','<c:out
                value="${ctx}"/>/commons/omdDiseaseList.jsp?sex=<%=bean.patientSex%>&age=<%=demographic.getAge()%>'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Header.OntMD"/></a>
    </div>
    <%}%>

    <div>
        <%=getEChartLinks() %>
    </div>

    <%
        if (facility.isIntegratorEnabled()) { %>
    <div>
        <% int secondsTillConsideredStale = -1;
            try {
                secondsTillConsideredStale = Integer.parseInt(OscarProperties.getInstance().getProperty("seconds_till_considered_stale"));
            } catch (Exception e) {
                MiscUtils.getLogger().error("OSCAR Property: seconds_till_considered_stale did not parse to an int", e);
                secondsTillConsideredStale = -1;
            }

            boolean allSynced = true;

            try {
                allSynced = CaisiIntegratorManager.haveAllRemoteFacilitiesSyncedIn(loggedInInfo, loggedInInfo.getCurrentFacility(), secondsTillConsideredStale, false);
                CaisiIntegratorManager.setIntegratorOffline(session, false);
            } catch (Exception remoteFacilityException) {
                MiscUtils.getLogger().error("Error checking Remote Facilities Sync status", remoteFacilityException);
                CaisiIntegratorManager.checkForConnectionError(session, remoteFacilityException);
            }
            if (secondsTillConsideredStale == -1) {
                allSynced = true;
            }
        %>
        <%if (CaisiIntegratorManager.isIntegratorOffline(session)) {%>
        <div style="background: none repeat scroll 0 0 red; color: white; font-weight: bold; padding-left: 10px; margin-bottom: 2px;">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.integrator.NA"/></div>
        <%} else if (!allSynced) {%>
        <div style="background: none repeat scroll 0% 0% orange; color: white; font-weight: bold; padding-left: 10px; margin-bottom: 2px;">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.integrator.outOfSync"/>

            <a href="javascript:void(0)" onClick="popupPage(233,600,'ViewICommun','<c:out
                    value="${ctx}"/>/admin/viewIntegratedCommunity.jsp'); return false;">Integrator</a>
        </div>
        <%} else {%>
        <a href="javascript:void(0)" onClick="popupPage(233,600,'ViewICommun','<c:out
                value="${ctx}"/>/admin/viewIntegratedCommunity.jsp'); return false;">I</a>
        <%}%>
    </div>
    <%}%>
</div>

<%!
    String getEChartLinks() {
        String str = OscarProperties.getInstance().getProperty("ECHART_LINK");
        if (str == null) {
            return "";
        }
        try {
            String[] httpLink = str.split("\\|");
            return "<a target=\"_blank\" href=\"" + httpLink[1] + "\">" + httpLink[0] + "</a>";
        } catch (Exception e) {
            MiscUtils.getLogger().error("ECHART_LINK is not in the correct format. title|url :" + str, e);
        }
        return "";
    }
%>
