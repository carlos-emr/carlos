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

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="ca.openosp.Misc" %>
<%@page import="ca.openosp.openo.util.UtilMisc" %>
<%@include file="/casemgmt/taglibs.jsp" %>
<%@taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@page import="java.util.Enumeration" %>
<%@page import="ca.openosp.openo.encounter.pageUtil.NavBarDisplayDAO" %>
<%@page import="java.util.Arrays,java.util.Properties,java.util.List,java.util.Set,java.util.ArrayList,java.util.Enumeration,java.util.HashSet,java.util.Iterator,java.text.SimpleDateFormat,java.util.Calendar,java.util.Date,java.text.ParseException" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="ca.openosp.openo.casemgmt.model.*,ca.openosp.openo.casemgmt.service.* " %>
<%@page import="ca.openosp.openo.casemgmt.web.formbeans.*" %>
<%@page import="ca.openosp.openo.PMmodule.model.*" %>
<%@page import="ca.openosp.openo.commn.model.*" %>
<%@page import="ca.openosp.openo.commn.dao.EFormDao" %>
<%@page import="ca.openosp.openo.util.DateUtils" %>
<%@page import="ca.openosp.openo.documentManager.EDocUtil" %>
<%@page import="org.springframework.web.context.WebApplicationContext" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="ca.openosp.openo.casemgmt.common.Colour" %>
<%@page import="ca.openosp.openo.documentManager.EDoc" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="com.quatro.dao.security.*,ca.openosp.openo.model.security.Secrole" %>
<%@page import="ca.openosp.openo.utility.EncounterUtil" %>
<%@page import="org.apache.cxf.common.i18n.UncheckedException" %>
<%@page import="ca.openosp.openo.casemgmt.web.NoteDisplay" %>
<%@page import="ca.openosp.openo.casemgmt.web.CaseManagementViewAction" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.prescript.data.RxPrescriptionData" %>
<%@page import="ca.openosp.openo.casemgmt.dao.CaseManagementNoteLinkDAO" %>
<%@page import="ca.openosp.OscarProperties" %>
<%@page import="ca.openosp.openo.utility.MiscUtils" %>
<%@page import="ca.openosp.openo.PMmodule.model.Program" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProgramDao" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.util.UtilDateUtilities" %>
<%@page import="ca.openosp.openo.casemgmt.web.NoteDisplayNonNote" %>
<%@page import="ca.openosp.openo.commn.dao.EncounterTemplateDao" %>
<%@page import="ca.openosp.openo.casemgmt.web.CheckBoxBean" %>
<%@page import="ca.openosp.openo.managers.ProgramManager2" %>
<%@ page import="ca.openosp.openo.managers.DemographicManager" %>
<%@ page import="ca.openosp.openo.encounter.pageUtil.EctSessionBean" %>
<%@ page import="ca.openosp.openo.casemgmt.web.formbeans.CaseManagementEntryFormBean" %>
<%@ page import="ca.openosp.openo.commn.model.*" %>
<%@ page import="ca.openosp.openo.PMmodule.model.ProgramProvider" %>

<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_casemgmt.notes" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_casemgmt.notes");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    String demoNo = request.getParameter("demographicNo");
    String privateConsentEnabledProperty = OscarProperties.getInstance().getProperty("privateConsentEnabled");
    boolean privateConsentEnabled = privateConsentEnabledProperty != null && privateConsentEnabledProperty.equals("true");
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    Demographic demographic = demographicManager.getDemographic(loggedInInfo, Integer.parseInt(demoNo));
    DemographicExt infoExt = demographicManager.getDemographicExt(loggedInInfo, Integer.parseInt(demoNo), "informedConsent");
    pageContext.setAttribute("demographic", demographic);
    boolean showPopup = false;
    if (infoExt == null || !"yes".equalsIgnoreCase(infoExt.getValue())) {
        showPopup = true;
    }

    ProgramManager2 programManager2 = SpringUtils.getBean(ProgramManager2.class);

    boolean showConsentsThisTime = false;
    String[] privateConsentPrograms = OscarProperties.getInstance().getProperty("privateConsentPrograms", "").split(",");
    ProgramProvider pp = programManager2.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
    if (pp != null) {
        for (int x = 0; x < privateConsentPrograms.length; x++) {
            if (privateConsentPrograms[x].equals(pp.getProgramId().toString())) {
                showConsentsThisTime = true;
            }
        }
    }

    try {
        Facility facility = loggedInInfo.getCurrentFacility();

        String pId = (String) session.getAttribute("case_program_id");
        if (pId == null) {
            pId = "";
        }

        String demographicNo = request.getParameter("demographicNo");
        EctSessionBean bean = null;
        if ((bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean")) == null) {
            response.sendRedirect("error.jsp");
            return;
        }

        String provNo = bean.providerNo;
        String dateFormat = "dd-MMM-yyyy H:mm";
        SimpleDateFormat jsfmt = new SimpleDateFormat("MMM dd, yyyy");
        Date dToday = new Date();
        String strToday = jsfmt.format(dToday);
        String frmName = "caseManagementEntryForm" + demographicNo;
        CaseManagementEntryFormBean cform = (CaseManagementEntryFormBean) session.getAttribute(frmName);
        if (request.getParameter("caseManagementEntryForm") == null) {
            request.setAttribute("caseManagementEntryForm", cform);
        }
%>

<script type="text/javascript" src="<c:out value="${ctx}/js/jquery-1.7.1.min.js"/>"></script>
<script type="text/javascript" src="<c:out value="${ctx}/library/jquery/jquery-ui-1.12.1.min.js" />"></script>
<script type="text/javascript">
    jQuery.noConflict();
</script>
<link rel="stylesheet" type="text/css" href="<c:out value="${ctx}"/>/library/jquery/jquery-ui-1.12.1.min.css">
<script src="<c:out value="${ctx}"/>/share/javascript/prototype.js" type="text/javascript"></script>
<script src="<c:out value="${ctx}"/>/share/javascript/scriptaculous.js" type="text/javascript"></script>
<script type="text/javascript" src="<c:out value="${ctx}/js/newCaseManagementView.js.jsp"/>"></script>
<script type="text/javascript">
    ctx = "<c:out value="${ctx}"/>";
    imgPrintgreen.src = ctx + "/oscarEncounter/graphics/printerGreen.png"; //preload green print image so firefox will update properly
    providerNo = "<%=provNo%>";
    demographicNo = "<%=demographicNo%>";
    case_program_id = "<%=pId%>";

    <caisi:isModuleLoad moduleName="caisi">
    caisiEnabled = true;
    </caisi:isModuleLoad>

    <%
    OscarProperties props = OscarProperties.getInstance();
    String requireIssue = props.getProperty("caisi.require_issue","true");
    if(requireIssue != null && requireIssue.equals("false")) {
    //require issue is false%>
    requireIssue = false;
    <% } %>

    <%
        String requireObsDate = props.getProperty("caisi.require_observation_date","true");
        if(requireObsDate != null && requireObsDate.equals("false")) {
        //do not need observation date%>
    requireObsDate = false;
    <% } %>


    strToday = "<%=strToday%>";

    notesIncrement = parseInt("<%=OscarProperties.getInstance().getProperty("num_loaded_notes", "20") %>");

    jQuery(document).ready(function () {
        notesLoader(0, notesIncrement, demographicNo);
        notesScrollCheckInterval = setInterval('notesIncrementAndLoadMore()', 1000);
    });

    <% if( request.getAttribute("NoteLockError") != null ) { %>
    alert("<%=request.getAttribute("NoteLockError")%>");
    <%}%>

</script>
<div id="topContent">
    <form name="caseManagementViewForm" action="${pageContext.request.contextPath}/CaseManagementView.do" method="post">
        <input type="hidden" name="demographicNo" value="<%=demographicNo%>"/>
        <input type="hidden" name="providerNo" value="<%=provNo%>"/>
        <input type="hidden" name="tab" value="Current Issues"/>
        <input type="hidden" name="hideActiveIssue" id="hideActiveIssue"/>
        <input type="hidden" name="ectWin.rowOneSize" id="rowOneSize"/>
        <input type="hidden" name="ectWin.rowTwoSize" id="rowTwoSize"/>
        <input type="hidden" name="chain" value="list">
        <input type="hidden" name="method" value="view">
        <input type="hidden" id="check_issue" name="check_issue">
        <input type="hidden" id="serverDate" value="<%=strToday%>">
        <input type="hidden" id="resetFilter" name="resetFilter" value="false">

        <div id="filteredresults">
            <c:if test="${not empty caseManagementViewForm.filter_providers}">
                <fieldset class="filterresult">
                    <legend><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.providers.title"/></legend>
                    <c:forEach var="filter_provider" items="${caseManagementViewForm.filter_providers}" varStatus="status">
                        <c:choose>
                            <c:when test="${filter_provider == 'a'}">All</c:when>
                            <c:otherwise>
                                <c:forEach var="provider" items="${providers}">
                                    <c:if test="${filter_provider == provider.providerNo}">
                                        ${provider.formattedName}<br>
                                    </c:if>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                    </c:forEach>
                </fieldset>
            </c:if>
        
            <c:if test="${not empty caseManagementViewForm.filter_roles}">
                <fieldset class="filterresult">
                    <legend><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.roles.title"/></legend>
                    <c:forEach var="filter_role" items="${caseManagementViewForm.filter_roles}" varStatus="status">
                        <c:choose>
                            <c:when test="${filter_role == 'a'}">All</c:when>
                            <c:otherwise>
                                <c:forEach var="role" items="${roles}">
                                    <c:if test="${filter_role == role.id}">
                                        ${role.name}<br>
                                    </c:if>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                    </c:forEach>
                </fieldset>
            </c:if>
        
            <c:if test="${not empty caseManagementViewForm.note_sort}">
                <fieldset class="filterresult">
                    <legend><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.sort.title"/></legend>
                    ${caseManagementViewForm.note_sort}<br>
                </fieldset>
            </c:if>
        
            <c:if test="${not empty caseManagementViewForm.issues}">
                <fieldset class="filterresult">
                    <legend><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.issues.title"/></legend>
                    <c:forEach var="filter_issue" items="${caseManagementViewForm.issues}" varStatus="status">
                        <c:choose>
                            <c:when test="${filter_issue == 'a'}">All</c:when>
                            <c:when test="${filter_issue == 'n'}">None</c:when>
                            <c:otherwise>
                                <c:forEach var="issue" items="${cme_issues}">
                                    <c:if test="${filter_issue == issue.issue.id}">
                                        ${issue.issueDisplay.description}<br>
                                    </c:if>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                    </c:forEach>
                </fieldset>
            </c:if>
        </div>        
        <div id="filter" style="display:none;margin-top: 5px; margin-left: 5px;margin-right: 5px;">
            <input type="button" value="Hide" onclick="return filter(false);"/>
            <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.resetFilter.title"/>"
                   onclick="return filter(true);"/>

            <table style="border-collapse:collapse;width:100%;">
                <tr>
                    <th>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.providers.title"/>
                    </th>
                    <th>
                        Role
                    </th>
                    <th>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.sort.title"/>
                    </th>
                    <th>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.issues.title"/>
                    </th>
                </tr>
                <tr>
                    <td style="border-left:solid #ddddff 3px">
                        <div style="height:150px;overflow:auto">
                            <ul style="padding:0;margin:0;list-style:none inside none">
                                <li>
                                    <input type="checkbox" name="filter_providers" value="a" onclick="filterCheckBox(this)" />
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.sortAll.title"/>
                                </li>
                                <%
                                    @SuppressWarnings("unchecked")
                                    Set<Provider> providers = (Set<Provider>) request.getAttribute("providers");

                                    String providerNo;
                                    Provider prov;
                                    Iterator<Provider> iter = providers.iterator();
                                    while (iter.hasNext()) {
                                        prov = iter.next();
                                        providerNo = prov.getProviderNo();
                                %>
                                <li>
                                    <input type="checkbox" name="filter_providers" value="<%= providerNo %>" onclick="filterCheckBox(this)" /><%=prov.getFormattedName()%>
                                </li>
                                <%
                                    }
                                %>
                            </ul>
                        </div>
                    </td>
                    <td style="border-left:solid #ddddff 3px">
                        <div style="height:150px;overflow:auto">
                            <ul style="padding:0;margin:0;list-style:none inside none">
                                <li>
                                    <input type="checkbox" name="filter_roles" value="a" onclick="filterCheckBox(this)" />
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.sortAll.title"/>
                                </li>
                                <%
                                    @SuppressWarnings("unchecked")
                                    List roles = (List) request.getAttribute("roles");
                                    for (int num = 0; num < roles.size(); ++num) {
                                        Secrole role = (Secrole) roles.get(num);
                                %>
                                <li>
                                    <input type="checkbox" name="filter_roles" value="<%=String.valueOf(role.getId())%>" onclick="filterCheckBox(this)" />
                                    <%=role.getName()%>
                                </li>
                                <%
                                    }
                                %>
                            </ul>
                        </div>
                    </td>
                    <td style="border-left:solid #ddddff 3px">
                        <div style="height:150px;overflow:auto">
                            <ul style="padding:0;margin:0;list-style:none inside none">
                                <li><input type="radio" name="note_sort" value="observation_date_asc"/>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.sortDateAsc.title"/>
                                </li>
                                <li><input type="radio" name="note_sort" value="observation_date_desc"/>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.sortDateDesc.title"/>
                                </li>
                                <li><input type="radio" name="note_sort" value="providerName"/>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.provider.title"/>
                                </li>
                                <li><input type="radio" name="note_sort" value="programName"/>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.program.title"/>
                                </li>
                                <li><input type="radio" name="note_sort" value="roleName"/>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.role.title"/>
                                </li>
                            </ul>
                        </div>
                    </td>
                    <td style="border-left:solid #ddddff 3px;">
                        <div style="height:150px;overflow:auto">
                            <ul style="padding:0;margin:0;list-style:none inside none">
                                <li>
                                    <input type="checkbox" name="issues" value="a" onclick="filterCheckBox(this)" />
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.sortAll.title"/>
                                </li>
                                <li>
                                    <input type="checkbox" name="issues" value="n" onclick="filterCheckBox(this)" />None
                                </li>

                                <%
                                    @SuppressWarnings("unchecked")
                                    List issues = (List) request.getAttribute("cme_issues");
                                    for (int num = 0; num < issues.size(); ++num) {
                                        CheckBoxBean issue_checkBoxBean = (CheckBoxBean) issues.get(num);
                                %>
                                <li>
                                    <input type="checkbox" name="issues" value="<%=String.valueOf(issue_checkBoxBean.getIssue().getId())%>"
                                                   onclick="filterCheckBox(this)" />
                                    <%=issue_checkBoxBean.getIssueDisplay().getResolved().equals("resolved") ? "* " : ""%> <%=issue_checkBoxBean.getIssueDisplay().getDescription()%>
                                </li>
                                <%
                                    }
                                %>
                            </ul>
                        </div>
                    </td>
                </tr>
            </table>
        </div>

        <div id="encounterTools">
            <!--  This leaves the OCEAN toolbar accessible -->
            <div id="ocean_placeholder" style="display:none; width: 100%">
                <span style="display:none">Ocean Toolbar</span>
            </div>

            <%
                if (privateConsentEnabled && showPopup && showConsentsThisTime) {
            %>
            <div id="informedConsentDiv" style="background-color: orange; padding: 5px; font-weight: bold;">
                <oscar:oscarPropertiesCheck value="true" property="STUDENT_PARTICIPATION_CONSENT">
                    <input type="checkbox" value="" name="studentParticipationConsentCheck"
                           id="studentParticipationConsentCheck"
                           onClick="return doStudentParticipationCheck('<%=demoNo%>');"/>
                    <label for="studentParticipationConsentCheck"><fmt:setBundle basename="oscarResources"/><fmt:message key="casemgmt.chartnotes.studentParticipationConsent"/></label>
                </oscar:oscarPropertiesCheck>
                <oscar:oscarPropertiesCheck value="false" property="STUDENT_PARTICIPATION_CONSENT">
                    <input type="checkbox" value="" name="informedConsentCheck" id="informedConsentCheck"
                           onClick="return doInformedConsent('<%=demoNo%>');"/>
                    <label for="informedConsentCheck"><fmt:setBundle basename="oscarResources"/><fmt:message key="casemgmt.chartnotes.informedConsent"/></label>
                </oscar:oscarPropertiesCheck>
            </div>
            <%
                }
            %>
            <fieldset>
                <legend>Template Search</legend>

                <img alt="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.msgFind"/>"
                     src="<c:out value="${ctx}/oscarEncounter/graphics/edit-find.png"/>">
                <input id="enTemplate" placeholder="template name" tabindex="6" size="16" type="text" value=""
                       onkeypress="return grabEnterGetTemplate(event)">

                <div class="enTemplate_name_auto_complete" id="enTemplate_list" style="z-index: 1; display: none">
                    &nbsp;
                </div>
            </fieldset>
            <fieldset>
                <legend>Research</legend>

                <input type="text" id="keyword" name="keyword" value=""
                       onkeypress="return grabEnter('searchButton',event)">

                <div style="display:inline-block; text-align: left;">
                    <!-- channel -->
                    <select id="channel">
                        <option value="http://resource.oscarmcmaster.org/oscarResource/OSCAR_search?query=">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.oscarSearch"/></option>
                        <option value="http://www.google.com/search?q="><fmt:setBundle basename="oscarResources"/><fmt:message key="global.google"/></option>
                        <option value="http://www.ncbi.nlm.nih.gov/entrez/query.fcgi?SUBMIT=y&amp;CDM=Search&amp;DB=PubMed&amp;term=">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="global.pubmed"/></option>
                        <option value="http://search.nlm.nih.gov/medlineplus/query?DISAMBIGUATION=true&amp;FUNCTION=search&amp;SERVER2=server2&amp;SERVER1=server1&amp;PARAMETER=">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="global.medlineplus"/></option>
                        <option value="casemgmt/tripsearch.jsp?searchterm=">Trip Database</option>
                        <option value="https://empendium.com/mcmtextbook/search?type=textbook&q=">McMaster Text Book
                        </option>
                    </select>
                </div>
                <input type="button" id="searchButton" name="button"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnSearch"/>"
                       onClick="popupPage(600,800,'<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.popupSearchPageWindow"/>',$('channel').options[$('channel').selectedIndex].value+urlencode($F('keyword')) ); return false;"/>
            </fieldset>

            <fieldset>
                <legend>Tools</legend>

                <oscar:oscarPropertiesCheck value="BC" property="billregion">
                <security:oscarSec roleName="<%=roleName$%>" objectName="_careconnect" rights="r">
                <c:if test="${ not empty careconnecturl }">
                <input type="button" title="Connect to BC Care Connect" value="CareConnect"
                       onclick="callCareConnect('${ careconnecturl }', '${ demographic.hin }', '${ demographic.firstName }',
                               '${ demographic.lastName }', '${ demographic.formattedDob }', '${ demographic.sex }',
                               '${ OscarProperties.getInstance()['BC_CARECONNECT_REGION'] }' )"/>
                </c:if>
                </security:oscarSec>
                </oscar:oscarPropertiesCheck>

                <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Filter.title"/>" onclick="showFilter();"/>
                        <%
					String roleName = session.getAttribute("userrole") + "," + session.getAttribute("user");
					String pAge = Integer.toString(UtilDateUtilities.calcAge(bean.yearOfBirth,bean.monthOfBirth,bean.dateOfBirth));
				%>
                <security:oscarSec roleName="<%=roleName%>" objectName="_newCasemgmt.calculators" rights="r"
                                   reverse="false">
                    <%@include file="calculatorsSelectList.jspf" %>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName%>" objectName="_newCasemgmt.templates" rights="r">
                <select onchange="javascript:popupPage(700,700,'Templates',this.value);">
                    <option value="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Header.Templates"/></option>
                    <option value="-1">------------------</option>
                    <security:oscarSec roleName="<%=roleName%>" objectName="_newCasemgmt.templates" rights="w">
                        <option value="<%=request.getContextPath()%>/admin/providertemplate.jsp">New / Edit Template
                        </option>
                        <option value="-1">------------------</option>
                    </security:oscarSec>
                    <%
                        EncounterTemplateDao encounterTemplateDao = (EncounterTemplateDao) SpringUtils.getBean(EncounterTemplateDao.class);
                        List<EncounterTemplate> allTemplates = encounterTemplateDao.findAll();

                        for (EncounterTemplate encounterTemplate : allTemplates) {
                            String templateName = StringEscapeUtils.escapeHtml4(encounterTemplate.getEncounterTemplateName());
                    %>
                    <option value="<%=request.getContextPath()+"/admin/providertemplate.jsp?dboperation=Edit&name="+templateName%>"><%=templateName%>
                    </option>
                    <%
                        }
                    %>
                </select>
                </security:oscarSec>

        </div>
    </form>
</div>


<%-- Insert smart note templates here --%>
<div style="display:none;" id="templateContainer">
    <div id="templatePlaceholder">
        <%-- place holder --%>
    </div>
</div>
<%-- Insert smart note templates here --%>
<%
    String oscarMsgType = (String) request.getParameter("msgType");
    String OscarMsgTypeLink = (String) request.getParameter("OscarMsgTypeLink");
%>
<form name="caseManagementEntryForm" id="caseManagementEntryForm" action="<%=request.getContextPath()%>/CaseManagementEntry.do" method="post">
    <input type="hidden" name="demographicNo" value="<%=demographicNo%>"/>
    <input type="hidden" name="includeIssue" value="off"/>
    <input type="hidden" name="OscarMsgType" value="<%=oscarMsgType%>"/>
    <input type="hidden" name="OscarMsgTypeLink" value="<%=OscarMsgTypeLink%>"/>
    <%
        String apptNo = request.getParameter("appointmentNo");
        if (apptNo == null || apptNo.equals("") || apptNo.equals("null")) {
            apptNo = "0";
        }

        String apptDate = request.getParameter("appointmentDate");
        if (apptDate == null || apptDate.equals("") || apptDate.equals("null")) {
            apptDate = UtilDateUtilities.getToday("yyyy-MM-dd");
        }

        String startTime = request.getParameter("start_time");
        if (startTime == null || startTime.equals("") || startTime.equals("null")) {
            startTime = "00:00:00";
        }

        String apptProv = request.getParameter("apptProvider");
        if (apptProv == null || apptProv.equals("") || apptProv.equals("null")) {
            apptProv = "none";
        }

        String provView = request.getParameter("providerview");
        if (provView == null || provView.equals("") || provView.equals("null")) {
            provView = provNo;
        }
    %>

    <input type="hidden" name="appointmentNo" value="<%=apptNo%>"/>
    <input type="hidden" name="appointmentDate" value="<%=apptDate%>"/>
    <input type="hidden" name="start_time" value="<%=startTime%>"/>
    <input type="hidden" name="billRegion"
                 value="<%=(OscarProperties.getInstance().getProperty("billregion","")).trim().toUpperCase()%>"/>
    <input type="hidden" name="apptProvider" value="<%=apptProv%>"/>
    <input type="hidden" name="providerview" value="<%=provView%>"/>
    <input type="hidden" name="toBill" id="toBill" value="false">
    <input type="hidden" name="deleteId" value="0">
    <input type="hidden" name="lineId" value="0">
    <input type="hidden" name="from" value="casemgmt">
    <input type="hidden" name="method" value="save">
    <input type="hidden" name="change_diagnosis" value="<c:out value="${change_diagnosis}"/>">
    <input type="hidden" name="change_diagnosis_id" value="<c:out value="${change_diagnosis_id}"/>">
    <input type="hidden" name="newIssueId" id="newIssueId">
    <input type="hidden" name="newIssueName" id="newIssueName">
    <input type="hidden" name="ajax" value="false">
    <input type="hidden" name="chain" value="">
    <input type="hidden" name="caseNote.program_no" value="<%=pId%>">
    <input type="hidden" name="noteId" value="0">
    <input type="hidden" name="note_edit" value="">
    <input type="hidden" name="sign" value="off">
    <input type="hidden" name="verify" value="off">
    <input type="hidden" name="forceNote" value="false">
    <input type="hidden" name="newNoteIdx" value="">
    <input type="hidden" name="notes2print" id="notes2print" value="">
    <input type="hidden" name="printCPP" id="printCPP" value="false">
    <input type="hidden" name="printRx" id="printRx" value="false">
    <input type="hidden" name="printLabs" id="printLabs" value="false">
    <input type="hidden" name="printPreventions" id="printPreventions" value="false">
    <input type="hidden" name="printAllergies" id="printAllergies" value="false">
    <input type="hidden" name="encType" id="encType" value="">
    <input type="hidden" name="pType" id="pType" value="">
    <input type="hidden" name="pStartDate" id="pStartDate" value="">
    <input type="hidden" name="pEndDate" id="pEndDate" value="">
    <input type="hidden" id="annotation_attribname" name="annotation_attribname" value="">
    <%
        if (OscarProperties.getInstance().getBooleanProperty("note_program_ui_enabled", "true")) {
    %>
    <input type="hidden" name="_note_program_no" value=""/>
    <input type="hidden" name="_note_role_id" value=""/>
    <% } %>

    <span id="notesLoading">
		<img src="<c:out value="${ctx}/images/DMSLoader.gif" />">Loading Notes...
	</span>


    <div id="issueList"
         style="background-color: #FFFFFF; height: 440px; width: 350px; position: absolute; z-index: 1; display: none; overflow: auto;">
        <table id="issueTable" class="enTemplate_name_auto_complete"
               style="position: relative; left: 0; display: none;">
            <tr>
                <td style="height: 430px; vertical-align: bottom;">
                    <div class="enTemplate_name_auto_complete" id="issueAutocompleteList"
                         style="position: relative; left: 0; display: none;"></div>
                </td>
            </tr>
        </table>
    </div>
    <div id="encMainDivWrapper">
        <div id="encMainDiv">

        </div>
    </div>
    <div id='control-panel'>
        <div class="row">

            <div id="form-control-panel">
                <div id="save-sign-bill-buttons">
                    <%

                        if (facility.isEnableGroupNotes()) {
                    %>
                    <input tabindex="16" type='image'
                           src="<c:out value="${ctx}/oscarEncounter/graphics/group-gnote.png"/>" id="groupNoteImg"
                           onclick="Event.stop(event);return selectGroup(document.forms['caseManagementEntryForm'].elements['caseNote.program_no'].value,document.forms['caseManagementEntryForm'].elements['demographicNo'].value);"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnGroupNote"/>'>
                    <% }
                        if (facility.isEnablePhoneEncounter()) {
                    %>
                    <input tabindex="25" type='image' src="<c:out value="${ctx}/oscarEncounter/graphics/attach.png"/>"
                           id="attachNoteImg"
                           onclick="Event.stop(event);return assign(document.forms['caseManagementEntryForm'].elements['caseNote.program_no'].value,document.forms['caseManagementEntryForm'].elements['demographicNo'].value);"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnAttachNote"/>'>
                    <% } %>
                    <input tabindex="17" type='image'
                           src="<c:out value="${ctx}/oscarEncounter/graphics/media-floppy.png"/>" id="saveImg"
                           onclick="Event.stop(event);return saveNoteAjax('save', 'list');"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnSave"/>'>
                    <input tabindex="18" type='image'
                           src="<c:out value="${ctx}/oscarEncounter/graphics/document-new.png"/>" id="newNoteImg"
                           onclick="newNote(event); return false;"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnNew"/>'>
                    <input tabindex="19" type='image'
                           src="<c:out value="${ctx}/oscarEncounter/graphics/note-save.png"/>" id="signSaveImg"
                           onclick="document.forms['caseManagementEntryForm'].sign.value='on';Event.stop(event);return savePage('saveAndExit', '');"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnSignSave"/>'>
                    <input tabindex="20" type='image'
                           src="<c:out value="${ctx}/oscarEncounter/graphics/verify-sign.png"/>" id="signVerifyImg"
                           onclick="document.forms['caseManagementEntryForm'].sign.value='on';document.forms['caseManagementEntryForm'].verify.value='on';Event.stop(event);return savePage('saveAndExit', '');"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnSign"/>'>
                    <%
                        if (bean.source == null) {
                    %>
                    <input tabindex="21" type='image'
                           src="<c:out value="${ctx}/oscarEncounter/graphics/dollar-sign-icon.png"/>"
                           onclick="document.forms['caseManagementEntryForm'].sign.value='on';document.forms['caseManagementEntryForm'].toBill.value='true';Event.stop(event);return savePage('saveAndExit', '');"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnBill"/>'>
                    <%
                        }
                    %>


                    <input tabindex="23" type='image'
                           src="<c:out value="${ctx}/oscarEncounter/graphics/system-log-out.png"/>"
                           onclick='closeEnc(event);return false;' title='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnExit"/>'>
                    <input tabindex="24" type='image'
                           src="<c:out value="${ctx}/oscarEncounter/graphics/document-print.png"/>"
                           onclick="return printSetup(event);"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnPrint"/>' id="imgPrintEncounter">
                </div>
                <div id="timer-control">
                    <input type="text" placeholder="Time Label" id="timer-note" title="Time Label"/>
                    <button type="button" onclick="pasteTimer()" id="aTimer"
                            title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.pasteTimer"/>">00:00
                    </button>
                    <button type="button" id="toggleTimer" onclick="toggleATimer(this)"
                            title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.toggleTimer"/>'>
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor"
                             class="bi bi-pause-fill" viewBox="0 0 16 16">
                            <path d="M5.5 3.5A1.5 1.5 0 0 1 7 5v6a1.5 1.5 0 0 1-3 0V5a1.5 1.5 0 0 1 1.5-1.5m5 0A1.5 1.5 0 0 1 12 5v6a1.5 1.5 0 0 1-3 0V5a1.5 1.5 0 0 1 1.5-1.5"></path>
                        </svg>
                    </button>
                </div>
            </div>
            <div id="assignIssueSection">
                <input tabindex="8" type="text" id="issueAutocomplete" class="issueAutocomplete"
                       placeholder="Search Issue" title="Search Issues" name="issueSearch"
                       onkeydown="return submitIssue(event);">
                <input tabindex="9" type="button" id="asgnIssues" title="Assign Selected Issue"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.assign.title"/>">
                <span id="busy" style="display: none">
		            <img src="<c:out value="${ctx}/oscarEncounter/graphics/busy.gif"/>"
                         alt="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnWorking"/>">
		        </span>
            </div>
        </div>
        <div class="row">
            <div id="note-control-panel">
                <button type="button" onclick="return showHideIssues(event, 'noteIssues-resolved');"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnDisplayResolvedIssues"/></button>
                <button type="button" onclick="return showHideIssues(event, 'noteIssues-unresolved');"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnDisplayUnresolvedIssues"/></button>
                <button type="button" onclick="notesLoadAll();"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnLoadAllNotes"/></button>
                <button type="button" onclick="toggleFullViewForAll();"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btneExpandLoadedNotes"/></button>
                <button type="button" onclick="toggleCollapseViewForAll();"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnCollapseLoadedNotes"/></button>
                <button type="button"
                        onclick="popupPage(500,200,'noteBrowser<%=bean.demographicNo%>','casemgmt/noteBrowser.jsp?demographic_no=<%=bean.demographicNo%>&FirstTime=1');">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.BrowseNotes"/></button>
            </div>
        </div>
    </div>

</form>

<script type="text/javascript">
    /**
     * enable autocomplete for Issue search menus.
     */
    jQuery(document).ready(function($) {
        var autocompleteUrl = ctx + "/CaseManagementEntry.do?method=issueList&demographicNo=" + demographicNo + "&providerNo=" + providerNo;
        
        $(".issueAutocomplete").autocomplete({
            source: function(request, response) {
                $.get(autocompleteUrl + "&term=" + request.term)
                    .done(function(data) {
                        // Transform the data to the format expected by jQuery UI autocomplete
                        var transformedData = $.map(data, function(item) {
                            return {
                                label: item.description.trim() + ' (' + item.code + ')',
                                value: item.description.trim(),
                                id: item.id
                            };
                        });
                        response(transformedData);
                    })
                    .fail(function(xhr, status, error) {
                        console.error("Autocomplete request failed:", status, error);
                        response([]);
                    });
            },
            delay: 100,
            minLength: 3,
            select: function (event, ui) {
                document.getElementById("newIssueId").value = ui.item.id;
                document.getElementById("newIssueName").value = ui.item.value;
            }
        });
    });
</script>

<%
    } catch (Exception e) {
        MiscUtils.getLogger().error("Unexpected error.", e);
    }
%>

