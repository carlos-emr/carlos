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
    Encounter Page (Index2.jsp)
    The e-chart encounter view for patient clinical notes.

    Data is prepared by EctIncomingEncounter2Action.setEncounterAttributes()
    and rendered via JSTL/EL with OWASP encoding. Layout uses Bootstrap 5.

    @since 2001 (original), restructured 2026-02-20
--%>

<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.log.LogAction" %>
<%@ page import="io.github.carlos_emr.carlos.log.LogConst" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.EctPatientData" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.EctProgram" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>


<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<%-- Security validation --%>
<%
    long startTime = System.currentTimeMillis();
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logoutPage");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    String demographic$;
    try {
        demographic$ = EncounterPatientContext.requireDemographicNo(request);
    } catch (IllegalArgumentException invalidPatient) {
        response.sendError(400, "Invalid encounter patient");
        return;
    }
    boolean bPrincipalControl = false;
    boolean bPrincipalDisplay = false;

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    String eChart$ = "_eChart$" + demographic$;
%>
<%
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_eChart" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_eChart");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<security:oscarSec roleName="<%=roleName$%>" objectName="<%=eChart$%>"
                   rights="o" reverse="<%=false%>">
    You have no rights to access the data!
    <% response.sendRedirect(request.getContextPath() + "/acctLocked.html"); %>
</security:oscarSec>

<%-- only principal has the save rights --%>
<security:oscarSec roleName="_principal" objectName="_eChart"
                   rights="ow" reverse="<%=false%>">
    <% bPrincipalControl = true;
        if (EctPatientData.getProviderNo(loggedInInfo, demographic$).equals((String) session.getAttribute("user"))) {
            bPrincipalDisplay = true;
        }
    %>
</security:oscarSec>

<%-- if this patients eChart is read only remove the save rights --%>
<security:oscarSec roleName="_all" objectName="<%=eChart$%>" rights="or"
                   reverse="<%=false%>">
    <%
        bPrincipalControl = true;
        bPrincipalDisplay = false;
    %>
</security:oscarSec>

<%-- Audit logging --%>
<%
    String ip = request.getRemoteAddr();
    LogAction.addLog((String) session.getAttribute("user"), LogConst.READ, LogConst.CON_ECHART, demographic$, ip, demographic$);
%>

<%-- IncomingEncounter and the legacy save (EctSaveEncounter2Action) each supply this chart's
     request-owned bean. The login-wide session bean follows whichever chart was entered last in
     any window, so it is never rendered here (issues #4556, #4673). --%>
<%
    EctSessionBean bean = (EctSessionBean) request.getAttribute("EctSessionBean");
    if (bean == null || !demographic$.equals(bean.demographicNo)) {
        response.sendRedirect(request.getContextPath() + "/encounter/ViewError");
        return;
    }
    // Make bean available for EL access as ${bean}
    request.setAttribute("bean", bean);
%>

<%-- New case management redirect check --%>
<%
    session.setAttribute("newCaseManagement", "true");
    String userNo = (String) request.getSession().getAttribute("user");
    if (userNo != null) {
        // Both chart variants choose their own program before redirecting. An ordinary
        // day-sheet entry must not capture a program selected in an unrelated PMM window.
        try {
            io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.initialize(
                    request, new EctProgram(session), userNo, bean.providerNo);
        } catch (SecurityException invalidProgram) {
            response.sendError(403, "Unavailable chart program selection");
            return;
        }
%>
<caisi:isModuleLoad moduleName="caisi" reverse="true">
    <%
        session.setAttribute("casemgmt_oscar_baseurl", request.getContextPath());
        String hrefurl = request.getContextPath() + "/casemgmt/ViewForward?action=view" +
        "&demographicNo=" + bean.demographicNo +
        "&chartContext=" + io.github.carlos_emr.carlos.utility.SafeEncode.forUriComponent(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.token(request)) +
        "&providerNo=" + bean.providerNo +
        "&providerName=" + URLEncoder.encode(bean.userName, StandardCharsets.UTF_8) +
        "&appointmentNo=" + (bean.appointmentNo != null ? bean.appointmentNo : "") +
        "&reason=" + URLEncoder.encode(bean.reason != null ? bean.reason : "", StandardCharsets.UTF_8) +
        (bean.oscarMsgID != null && !bean.oscarMsgID.isEmpty() ? "&msgId=" + SafeEncode.forUriComponent(bean.oscarMsgID) : "") +
        "&reasonCode=" + (bean.reasonCode != null ? bean.reasonCode : "") +
        "&appointmentDate=" + (bean.appointmentDate != null ? bean.appointmentDate : "") +
        "&start_time=" + (bean.startTime != null ? bean.startTime : "") +
        "&apptProvider=" + (bean.curProviderNo != null ? bean.curProviderNo : "") +
        "&providerview=" + (bean.curProviderNo != null ? bean.curProviderNo : "") +
        "&noteId=" + request.getParameter("noteId") +
        (request.getParameter("noteId") != null ? "&forceNote=true" : "");

        if (request.getParameter("noteBody") != null)
            hrefurl += "&noteBody=" + request.getParameter("noteBody");

        if (!response.isCommitted()) {
            response.sendRedirect(hrefurl);
            return;
        }
    %>
</caisi:isModuleLoad>

<% } %>

<%-- CAISI module redirect --%>
<caisi:isModuleLoad moduleName="caisi">
    <%
        session.setAttribute("casemgmt_oscar_baseurl", request.getContextPath());
        // The request-owned bean is not copied into the session: under this unkeyed name it was
        // the last-entered chart's, and every reader asks for "casemgmt_oscar_bean" + patient.
        String hrefurl = request.getContextPath() + "/casemgmt/ViewForward?action=view&demographicNo=" + bean.demographicNo + "&chartContext=" + io.github.carlos_emr.carlos.utility.SafeEncode.forUriComponent(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.token(request)) + "&providerNo=" + bean.providerNo + "&providerName=" + URLEncoder.encode(bean.userName, StandardCharsets.UTF_8) +
                (bean.oscarMsgID != null && !bean.oscarMsgID.isEmpty() ? "&msgId=" + SafeEncode.forUriComponent(bean.oscarMsgID) : "");
        // Retired bookmarks must enter the modern chart before accepting typing.
        if (!response.isCommitted()) {
            response.sendRedirect(hrefurl);
            return;
        }
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    %>
</caisi:isModuleLoad>

<%-- The legacy editor is retired; this view only forwards to the modern chart. --%>
