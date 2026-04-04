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
<%@ page import="io.github.carlos_emr.carlos.encounter.data.EctPatientData" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.EctProgram" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.ArrayList" %>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>

<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
<fmt:setBundle basename="oscarResources"/>

<%-- Security validation --%>
<%
    long startTime = System.currentTimeMillis();
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    String demographic$ = request.getParameter("demographicNo");
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
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_eChart");%>
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

<%-- Session bean retrieval --%>
<%
    EctSessionBean bean = null;
    if ((bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean")) == null) {
        response.sendRedirect("error.jsp");
        return;
    }
    // Make bean available for EL access as ${bean}
    request.setAttribute("bean", bean);
%>

<%-- New case management redirect check --%>
<%
    String userNo = (String) request.getSession().getAttribute("user");
    if (userNo != null) {
        session.setAttribute("newCaseManagement", "true");
%>
<caisi:isModuleLoad moduleName="caisi" reverse="true">
    <%
        EctProgram prgrmMgr = new EctProgram(session);
        session.setAttribute("case_program_id", prgrmMgr.getProgram(bean.providerNo));
        session.setAttribute("casemgmt_oscar_baseurl", request.getContextPath());
        session.setAttribute("casemgmt_bean_flag", "true");
        String hrefurl = request.getContextPath() + "/casemgmt/forward.jsp?action=view" +
        "&demographicNo=" + bean.demographicNo +
        "&providerNo=" + bean.providerNo +
        "&providerName=" + URLEncoder.encode(bean.userName, StandardCharsets.UTF_8) +
        "&appointmentNo=" + (bean.appointmentNo != null ? bean.appointmentNo : "") +
        "&reason=" + URLEncoder.encode(bean.reason != null ? bean.reason : "", StandardCharsets.UTF_8) +
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
        session.setAttribute("casemgmt_oscar_bean", bean);
        session.setAttribute("casemgmt_bean_flag", "true");
        String hrefurl = request.getContextPath() + "/casemgmt/forward.jsp?action=view&demographicNo=" + bean.demographicNo + "&providerNo=" + bean.providerNo + "&providerName=" + bean.userName;
        if (request.getParameter("casetoEncounter") == null) {
            if (!response.isCommitted())
                response.sendRedirect(hrefurl);
            return;
        }
    %>
</caisi:isModuleLoad>

<html>
<%@ include file="includes/encounter-head.jspf" %>

    <body topmargin="0" leftmargin="0" bottommargin="0" rightmargin="0" vlink="#0000FF">

    <%-- Action errors display --%>
    <c:if test="${not empty actionErrors}">
        <div class="action-errors">
            <ul>
                <c:forEach var="error" items="${actionErrors}">
                    <li><c:out value="${error}"/></li>
                </c:forEach>
            </ul>
        </div>
    </c:if>

    <div id="templatejs" style="display: none"></div>

    <%-- Header bar with patient info and search --%>
    <%@ include file="includes/encounter-header-bar.jspf" %>

    <%-- Main content: left nav + encounter form --%>
    <div class="container-fluid p-0">
        <div class="row g-0" style="min-height: calc(100vh - 42px);">
            <%-- Left navigation --%>
            <div class="col-auto" id="encounter-left-nav" style="width: 22%;">
                <div id="leftNavbar" style="height: 100%; width: 100%;">
                    <caisi:isModuleLoad moduleName="caisi">
                        <%
                            String hrefurl2 = request.getContextPath() + "/casemgmt/forward.jsp?action=view&demographicNo=" + bean.demographicNo + "&providerNo=" + bean.providerNo + "&providerName=" + bean.userName;
                        %>
                        <a href="<%=hrefurl2%>">Case Management Encounter</a>
                    </caisi:isModuleLoad>
                </div>
            </div>

            <%-- Right content: encounter form --%>
            <div class="col" style="background-color: #CCCCFF;">
                <form name="encForm" action="SaveEncounter2.do" method="POST">
                    <input type="hidden" id="reloadDiv" name="reloadDiv" value="none"
                           onchange="updateDiv();">
                    <caisi:isModuleLoad moduleName="caisi">
                        <input type="hidden" name="casetoEncounter" value="true">
                    </caisi:isModuleLoad>

                    <%@ include file="includes/encounter-row-one.jspf" %>
                    <%@ include file="includes/encounter-row-two.jspf" %>
                    <%@ include file="includes/encounter-rx-allergies.jspf" %>
                    <%@ include file="includes/encounter-row-three.jspf" %>
                </form>
            </div>
        </div>
    </div>

    <%-- Split chart overlay --%>
    <%@ include file="includes/encounter-split-chart.jspf" %>

    </body>
</html>
