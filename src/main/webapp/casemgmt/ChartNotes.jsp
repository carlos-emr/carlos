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
    ChartNotes.jsp — Renders the clinical notes panel inside the encounter page.

    Loaded via jsp:include from newCaseManagementView.jsp. Displays filtered and
    sorted clinical notes for a patient, with inline editing, issue assignment,
    and template insertion.

    The 1 MB buffer (page directive + response.setBufferSize) prevents Tomcat 11
    from truncating large AJAX forward responses. Without it, the
    CsrfGuardScriptInjectionFilter's CaptureResponseWrapper can overflow the
    default 8 KB JSP buffer during forward dispatch, causing silent truncation
    of the notes HTML.

    @since 2006-01-01
--%>

<%@page buffer="1024kb" %>
<% response.setBufferSize(1024 * 1024); %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.Misc" %>
<%@page import="io.github.carlos_emr.carlos.util.UtilMisc" %>
<%@include file="/casemgmt/taglibs.jsp" %>
<%@taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@page import="java.util.Enumeration" %>
<%@page import="io.github.carlos_emr.carlos.encounter.pageUtil.NavBarDisplayDAO" %>
<%@page import="java.util.Arrays,java.util.Properties,java.util.List,java.util.Set,java.util.ArrayList,java.util.Enumeration,java.util.HashSet,java.util.Iterator,java.text.SimpleDateFormat,java.util.Calendar,java.util.Date,java.text.ParseException" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.model.*,io.github.carlos_emr.carlos.casemgmt.service.* " %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.web.formbeans.*" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.model.*" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.*" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.EFormDao" %>
<%@page import="io.github.carlos_emr.carlos.util.DateUtils" %>
<%@page import="io.github.carlos_emr.carlos.documentManager.EDocUtil" %>
<%@page import="org.springframework.web.context.WebApplicationContext" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.common.Colour" %>
<%@page import="io.github.carlos_emr.carlos.documentManager.EDoc" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="com.quatro.dao.security.*,io.github.carlos_emr.carlos.model.security.Secrole" %>
<%@page import="io.github.carlos_emr.carlos.utility.EncounterUtil" %>
<%@page import="org.apache.cxf.common.i18n.UncheckedException" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.web.NoteDisplay" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.web.CaseManagementViewAction" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO" %>
<%@page import="io.github.carlos_emr.CarlosProperties" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.model.Program" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.web.NoteDisplayNonNote" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.EncounterTemplateDao" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.web.CheckBoxBean" %>
<%@page import="io.github.carlos_emr.carlos.managers.ProgramManager2" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider" %>

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
    String privateConsentEnabledProperty = CarlosProperties.getInstance().getProperty("privateConsentEnabled");
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
    String[] privateConsentPrograms = CarlosProperties.getInstance().getProperty("privateConsentPrograms", "").split(",");
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

<script type="text/javascript" src="<c:out value="${ctx}/library/jquery/jquery-3.7.1.min.js"/>"></script>
<script type="text/javascript" src="<c:out value="${ctx}/library/jquery/jquery-ui-1.14.2.min.js" />"></script>
<script type="text/javascript">jQuery.noConflict();</script>
<link rel="stylesheet" type="text/css" href="<c:out value="${ctx}"/>/library/jquery/jquery-ui-1.14.2.min.css">
<!-- Prototype.js/Scriptaculous removed — using prototype-compat.js shim + carlos-ajax.js (Phase 4d migration) -->
<!-- jQuery.noConflict() frees $ for the Prototype shim; use jQuery() or jQuery.ajax() for jQuery calls -->
<script src="<c:out value="${ctx}"/>/share/javascript/prototype-compat.js" type="text/javascript"></script>
<script src="<c:out value="${ctx}"/>/share/javascript/carlos-ajax.js" type="text/javascript"></script>
<!-- vanilla JS autocomplete select box (replaces Scriptaculous Autocompleter.SelectBox) -->
<script src="<c:out value="${ctx}"/>/share/javascript/select.js" type="text/javascript"></script>
<script type="text/javascript" src="<c:out value="${ctx}/js/newCaseManagementView.js.jsp"/>?v=<%= System.currentTimeMillis() %>"></script>
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
    CarlosProperties props = CarlosProperties.getInstance();
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

    notesIncrement = parseInt("<%=CarlosProperties.getInstance().getProperty("num_loaded_notes", "20") %>");

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
                 value="<%=(CarlosProperties.getInstance().getProperty("billregion","")).trim().toUpperCase()%>"/>
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
                    <%
                        try {
                        if (facility != null && facility.isEnableGroupNotes()) {
                    %>
                    <input tabindex="16" type='image'
                           src="<c:out value="${ctx}/oscarEncounter/graphics/group-gnote.png"/>" id="groupNoteImg"
                           onclick="event.preventDefault();event.stopPropagation();return selectGroup(document.forms['caseManagementEntryForm'].elements['caseNote.program_no'].value,document.forms['caseManagementEntryForm'].elements['demographicNo'].value);"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnGroupNote"/>'>
                    <% }
                        if (facility != null && facility.isEnablePhoneEncounter()) {
                    %>
                    <input tabindex="25" type='image' src="<c:out value="${ctx}/oscarEncounter/graphics/attach.png"/>"
                           id="attachNoteImg"
                           onclick="event.preventDefault();event.stopPropagation();return assign(document.forms['caseManagementEntryForm'].elements['caseNote.program_no'].value,document.forms['caseManagementEntryForm'].elements['demographicNo'].value);"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnAttachNote"/>'>
                    <% }
                        } catch (Exception facilityEx) {
                            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error("Facility check error in ChartNotes.jsp", facilityEx);
                        }
                    %>
                    <input tabindex="17" type='image'
                           src="<c:out value="${ctx}/oscarEncounter/graphics/media-floppy.png"/>" id="saveImg"
                           onclick="event.preventDefault();event.stopPropagation();return saveNoteAjax('save', 'list');"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnSave"/>'>
                    <input tabindex="18" type='image'
                           src="<c:out value="${ctx}/oscarEncounter/graphics/document-new.png"/>" id="newNoteImg"
                           onclick="newNote(event); return false;"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnNew"/>'>
                    <input tabindex="19" type='image'
                           src="<c:out value="${ctx}/oscarEncounter/graphics/note-save.png"/>" id="signSaveImg"
                           onclick="document.forms['caseManagementEntryForm'].sign.value='on';event.preventDefault();event.stopPropagation();return savePage('saveAndExit', '');"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnSignSave"/>'>
                    <input tabindex="20" type='image'
                           src="<c:out value="${ctx}/oscarEncounter/graphics/verify-sign.png"/>" id="signVerifyImg"
                           onclick="document.forms['caseManagementEntryForm'].sign.value='on';document.forms['caseManagementEntryForm'].verify.value='on';event.preventDefault();event.stopPropagation();return savePage('saveAndExit', '');"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnSign"/>'>
                    <%
                        if (bean.source == null) {
                    %>
                    <input tabindex="21" type='image'
                           src="<c:out value="${ctx}/oscarEncounter/graphics/dollar-sign-icon.png"/>"
                           onclick="document.forms['caseManagementEntryForm'].sign.value='on';document.forms['caseManagementEntryForm'].toBill.value='true';event.preventDefault();event.stopPropagation();return savePage('saveAndExit', '');"
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
                </div>
        </div>
        <div class="row">
            <div id="note-control-panel">
                <button type="button"
                        onclick="popupPage(500,200,'noteBrowser<%=bean.demographicNo%>','casemgmt/noteBrowser.jsp?demographic_no=<%=bean.demographicNo%>&FirstTime=1');">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.BrowseNotes"/></button>
                <button type="button" onclick="notesLoadAll();"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnLoadAllNotes"/></button>
                <button type="button" onclick="toggleFullViewForAll();"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btneExpandLoadedNotes"/></button>
                <button type="button" onclick="toggleCollapseViewForAll();"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnCollapseLoadedNotes"/></button>
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
    try { out.flush(); } catch (java.io.IOException flushEx) {
        MiscUtils.getLogger().debug("Failed to flush ChartNotes.jsp output", flushEx);
    }
%>

