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
    Displays patient search results for appointment selection and contact pickers.
    Features: search/pagination, recent patients, appointment return, and the
    caisi=true callback that fills the opener's patient name and identifier.
    Parameters: keyword/search_mode control search; appointment context includes
    provider_no and originalPage. Contact callbacks use formName, elementName,
    and elementId; URI-encoded result names are decoded once for display fields.
    Access: requires _search read access. Recent-patient loading excludes missing
    or merged records in the DAO and skips records removed during rendering.
    @since 2026.08 (contact workflow corrections and contract documentation)
--%>

<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_search" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_search");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<!DOCTYPE HTML>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setLocale value="<%= io.github.carlos_emr.carlos.utility.LocaleUtils.resolveBundleLocale(request) %>"/>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<%@page import="java.nio.charset.StandardCharsets" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager" %>

<%@ page import="java.util.*, java.sql.*,java.net.*, io.github.carlos_emr.*" errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>

<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicMerged" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.OscarLogDao" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.Misc" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>

<%
    Boolean isMobileOptimized = session.getAttribute("mobileOptimized") != null;

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    String curProvider_no = request.getParameter("provider_no");

    String keyword = request.getParameter("keyword");
    String searchMode = request.getParameter("search_mode");

    String strLimit1 = "0";
    String strLimit2 = "10";
    if (request.getParameter("limit1") != null) strLimit1 = request.getParameter("limit1");
    if (request.getParameter("limit2") != null) strLimit2 = request.getParameter("limit2");

    int offset;
    try {
        offset = Integer.parseInt(strLimit1);
    } catch (NumberFormatException e) {
        offset = 0;
    }
    int limit;
    try {
        limit = Integer.parseInt(strLimit2);
    } catch (NumberFormatException e) {
        limit = 10;
    }
    // Sanitize: replace raw request strings with parsed integer values to prevent XSS
    strLimit1 = String.valueOf(offset);
    strLimit2 = String.valueOf(limit);
    boolean caisi = Boolean.valueOf(request.getParameter("caisi")).booleanValue();

    // Validate originalpage to prevent open redirect: must be a relative URL.
    // Note: getParameter() auto-decodes URL-encoded values, so %2F%2F decodes to // and is
    // caught by startsWith("//"). Backslash bypass (/\) is also rejected explicitly.
    String originalpage = request.getParameter("originalpage");
    if (originalpage == null || originalpage.isEmpty() || !originalpage.startsWith("/") || originalpage.startsWith("//") || originalpage.startsWith("/\\")) {
        originalpage = request.getContextPath() + "/appointment/addappointment";
    }

    CarlosProperties props = CarlosProperties.getInstance();

    List<Demographic> demoList = null;
    DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    OscarLogDao oscarLogDao = (OscarLogDao) SpringUtils.getBean(OscarLogDao.class);
    String providerNo = loggedInInfo.getLoggedInProviderNo();
    boolean outOfDomain = true;
    if (CarlosProperties.getInstance().getProperty("ModuleNames", "").indexOf("Caisi") != -1) {
        if (!"true".equals(CarlosProperties.getInstance().getProperty("pmm.client.search.outside.of.domain.enabled", "true"))) {
            outOfDomain = false;
        }
        if (request.getParameter("outofdomain") != null && request.getParameter("outofdomain").equals("true")) {
            outOfDomain = true;
        }
    }

%>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="demographic.demographicsearch2apptresults.title"/></title>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>


    <%
        if (isMobileOptimized) {
    %>
    <meta name="viewport"
          content="initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=no, width=device-width"/>
    <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/mobile/searchdemographicstyle.css">
    <%
    } else {
    %>
    <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/share/css/searchBox.css"/>
    <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/demographic/searchdemographicstyle.css"/>
    <%
        }
    %>
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js" type="text/javascript"></script>
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-compat.js"></script>
    <script src="${pageContext.request.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"
            type="text/javascript"></script>
    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet"
          type="text/css"/>
    <%-- global.css: CARLOS color overrides for Bootstrap (this page doesn't use global-head.jspf) --%>
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/share/css/global.css"/>
    <script language="javascript" type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
    <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/share/javascript/dobSearchKeyword.js"></script>
    <fmt:message key="demographic.zdemographicfulltitlesearch.msgDobFormat" var="dobFormatMessage"/>
    <script language="JavaScript">
        var DOB_FORMAT_MESSAGE = '${carlos:forJavaScript(dobFormatMessage)}';
        function setfocus() {
            this.focus();
            document.titlesearch.keyword.focus();
            document.titlesearch.keyword.select();
        }

        function showHideItem(id) {
            if (document.getElementById(id).style.display == 'inline')
                document.getElementById(id).style.display = 'none';
            else
                document.getElementById(id).style.display = 'inline';
        }

        function checkTypeIn() {
            var form = document.titlesearch;
            var keyword = form.keyword;
            // Preserve Ontario card-swiping support when selecting a patient.
            if (/^%b610054[0-9]{10}/.test(keyword.value)) {
                keyword.value = keyword.value.substring(8, 18);
                form.search_mode.value = 'search_hin';
                return true;
            }
            if (form.search_mode.value === 'search_dob') {
                var value = keyword.value.trim();
                // Server-populated/restored values need not fire an input event.
                if (/^[0-9]{8}$/.test(value)) {
                    keyword.value = CarlosDobSearch.format(value);
                    value = keyword.value;
                }
                if (value.length > 0 && !CarlosDobSearch.isValid(value)) {
                    alert(DOB_FORMAT_MESSAGE);
                    return false;
                }
            }
            return true;
        }

        function searchInactive() {
            document.titlesearch.ptstatus.value = "inactive"
            if (checkTypeIn()) document.forms[0].submit()
        }

        function searchAll() {
            document.titlesearch.ptstatus.value = ""
            if (checkTypeIn()) document.forms[0].submit()
        }


    </script>
</head>
<body onLoad="setfocus()">
<div class="container">
    <h2 style="margin:auto 15px;">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="currentColor" class="bi bi-search"
             viewBox="0 0 16 16">
            <path d="M11.742 10.344a6.5 6.5 0 1 0-1.397 1.398h-.001q.044.06.098.115l3.85 3.85a1 1 0 0 0 1.415-1.414l-3.85-3.85a1 1 0 0 0-.115-.1zM12 6.5a5.5 5.5 0 1 1-11 0 5.5 5.5 0 0 1 11 0"/>
        </svg>
        Search Patient
    </h2>
    <form method="post" name="titlesearch" action="<%= request.getContextPath() %>/demographic/DemographicSearch"
          onSubmit="return checkTypeIn()">
        <div id="demographicSearch" class="searchBox input-group select-group" style="margin-bottom:10px;">
            <%--    <ul style="display: flex;">--%>
            <%--        <li>--%>
            <label class="visually-hidden" for="appointment-search-mode"><fmt:message key="demographic.zdemographicfulltitlesearch.msgBy"/></label>
            <select id="appointment-search-mode" class="wideInput form-select" name="search_mode"
                    onchange="if(this.value === 'search_dob') document.titlesearch.keyword.value = '';">
                <option value="search_name" <%="search_name".equals(request.getParameter("search_mode")) ? "selected" : ""%>><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                    <fmt:message key="demographic.demographicsearch2apptresults.optName"/>
                </option>
                <option value="search_phone" <%="search_phone".equals(request.getParameter("search_mode")) ? "selected" : ""%>><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                    <fmt:message key="demographic.demographicsearch2apptresults.optPhone"/>
                </option>
                <option value="search_dob" <%="search_dob".equals(request.getParameter("search_mode")) ? "selected" : ""%>><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                    <fmt:message key="demographic.demographicsearch2apptresults.optDOB"/>
                </option>
                <option value="search_address" <%="search_address".equals(request.getParameter("search_mode")) ? "selected" : ""%>><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                    <fmt:message key="demographic.demographicsearch2apptresults.optAddress"/>
                </option>
                <option value="search_hin" <%="search_hin".equals(request.getParameter("search_mode")) ? "selected" : ""%>><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                    <fmt:message key="demographic.demographicsearch2apptresults.optHIN"/>
                </option>
                <option value="search_chart_no" <%="search_chart_no".equals(request.getParameter("search_mode")) ? "selected" : ""%>><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                    <fmt:message key="demographic.demographicsearch2apptresults.optChart"/>
                </option>
                <option value="search_demographic_no" <%="search_demographic_no".equals(request.getParameter("search_mode")) ? "selected" : ""%>><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                    <fmt:message key="demographic.demographicsearch2apptresults.demographicId"/>
                </option>
            </select>
            <%--        </li>--%>
            <%--        <li>--%>

            <input type="text" class="wideInput form-control" NAME="keyword"
                   oninput="if(document.titlesearch.search_mode.value === 'search_dob') CarlosDobSearch.formatInput(this);"
                   VALUE="<carlos:encode value='<%= request.getParameter("keyword") != null ? request.getParameter("keyword") : "" %>' context="htmlAttribute"/>" SIZE="17" MAXLENGTH="100"/><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <%--        </li>--%>
            <%--        <li>--%>
            <INPUT TYPE="hidden" NAME="orderby" VALUE="last_name, first_name">
            <INPUT TYPE="hidden" NAME="dboperation" VALUE="search_titlename">
            <INPUT TYPE="hidden" NAME="limit1" VALUE="0">
            <INPUT TYPE="hidden" NAME="limit2" VALUE="5">
            <input type="hidden" name="displaymode" value="Search ">
            <INPUT TYPE="hidden" NAME="ptstatus" VALUE="active">

            <input type="hidden" name="fromAppt" value="<carlos:encode value='<%= request.getParameter("fromAppt") != null ? request.getParameter("fromAppt") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="originalPage"
                   value="<carlos:encode value='<%= request.getParameter("originalPage") != null ? request.getParameter("originalPage") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="bFirstDisp"
                   value="<carlos:encode value='<%= request.getParameter("bFirstDisp") != null ? request.getParameter("bFirstDisp") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="provider_no"
                   value="<carlos:encode value='<%= request.getParameter("provider_no") != null ? request.getParameter("provider_no") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="start_time"
                   value="<carlos:encode value='<%= request.getParameter("start_time") != null ? request.getParameter("start_time") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="end_time" value="<carlos:encode value='<%= request.getParameter("end_time") != null ? request.getParameter("end_time") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="year" value="<carlos:encode value='<%= request.getParameter("year") != null ? request.getParameter("year") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="month" value="<carlos:encode value='<%= request.getParameter("month") != null ? request.getParameter("month") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="day" value="<carlos:encode value='<%= request.getParameter("day") != null ? request.getParameter("day") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="appointment_date"
                   value="<carlos:encode value='<%= request.getParameter("appointment_date") != null ? request.getParameter("appointment_date") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="notes" value="<carlos:encode value='<%= request.getParameter("notes") != null ? request.getParameter("notes") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="reasonCode"
                   value="<carlos:encode value='<%= request.getParameter("reasonCode") != null ? request.getParameter("reasonCode") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="reason" value="<carlos:encode value='<%= request.getParameter("reason") != null ? request.getParameter("reason") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="location" value="<carlos:encode value='<%= request.getParameter("location") != null ? request.getParameter("location") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="resources"
                   value="<carlos:encode value='<%= request.getParameter("resources") != null ? request.getParameter("resources") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="type" value="<carlos:encode value='<%= request.getParameter("type") != null ? request.getParameter("type") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="style" value="<carlos:encode value='<%= request.getParameter("style") != null ? request.getParameter("style") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="billing" value="<carlos:encode value='<%= request.getParameter("billing") != null ? request.getParameter("billing") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="status" value="<carlos:encode value='<%= request.getParameter("status") != null ? request.getParameter("status") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="createdatetime"
                   value="<carlos:encode value='<%= request.getParameter("createdatetime") != null ? request.getParameter("createdatetime") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="creator" value="<carlos:encode value='<%= request.getParameter("creator") != null ? request.getParameter("creator") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <input type="hidden" name="remarks" value="<carlos:encode value='<%= request.getParameter("remarks") != null ? request.getParameter("remarks") : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>

            <%
                String temp = null;
                for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                    temp = e.nextElement().toString();
                    if (temp.equals("keyword") || temp.equals("dboperation") || temp.equals("displaymode") || temp.equals("search_mode") || temp.equals("chart_no") || temp.equals("ptstatus") || temp.equals("submit"))
                        continue;
            %>
            <input type="hidden" name="<carlos:encode value='<%= temp %>' context="htmlAttribute"/>"
                   value="<carlos:encode value='<%= request.getParameter(temp) != null ? request.getParameter(temp) : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            <% }
            %>
            <div class="input-group">
                <a href="#" onclick="showHideItem('demographicSearch');" id="cancelButton"
                   class="leftButton top btn btn-link">
                    <fmt:message key="global.btnCancel"/>
                </a>
                <input type="SUBMIT" class="btn btn-primary"
                       value='<fmt:message key="global.search"/>'
                       title='<fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchActive"/>'>
                <INPUT TYPE="button" id="inactiveButton" class="btn btn-secondary"
                       onclick="searchInactive();"
                       TITLE="<fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchInactive"/>"
                       VALUE="<fmt:message key="demographic.search.Inactive"/>">
                <INPUT TYPE="button" id="allButton" class="btn btn-secondary"
                       onclick="searchAll();"
                       TITLE="<fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchAll"/>"
                       VALUE="<fmt:message key="demographic.search.All"/>">
            </div>
            <%--    </ul>--%>
        </div>
    </form>


    <div id="searchResults" style="margin-bottom:10px;">

        <div>
            <%if (Boolean.TRUE.equals(request.getAttribute("showRecentPatients"))) { %>
            <fmt:message key="demographic.demographicsearch2apptresults.msgMostRecentPatients"/>
            <% } else { %>
            <fmt:message key="demographic.demographicsearch2apptresults.msgKeywords"/> <carlos:encode value='<%= request.getParameter("keyword") != null ? request.getParameter("keyword") : "" %>' context="html"/> <%}%><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
        </div>
        <script language="JavaScript">

            var fullname = "";
            <%-- RJ 07/10/2006 Need to pass doctor of patient back to referrer --%>

            function addName(demographic_no, lastname, firstname, chartno, messageID, doctorNo) {
                fullname = lastname + "," + firstname;

                const form = document.forms.namedItem("addform");
                form.action = "<carlos:encode value='<%= originalpage %>' context="javaScript"/>";
                form.elements.namedItem("demographic_no").value = demographic_no;
                form.elements.namedItem("chart_no").value = decodeURIComponent(chartno);
                form.elements.namedItem("name").value = decodeURIComponent(lastname) + "," + decodeURIComponent(firstname);
                form.elements.namedItem("bFirstDisp").value = "false";
                form.elements.namedItem("messageID").value = messageID;
                form.elements.namedItem("doctor_no").value = doctorNo;
                form.submit();
                return true;
            }

            <%if(caisi) {%>

            function addNameCaisi(demographic_no, lastname, firstname, chartno, messageID) {
                // Result arguments are URI-encoded for the appointment callback.
                // A contact field needs display text, decoded exactly once.
                fullname = decodeURIComponent(lastname) + "," + decodeURIComponent(firstname);
                if (opener.document['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("formName")) %>' context="javaScriptBlock"/>'] != null) {
                    if (opener.document['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("formName")) %>' context="javaScriptBlock"/>'].
                    elements['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("elementName")) %>' context="javaScriptBlock"/>'] != null
                )
                    opener.document
                ['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("formName")) %>' context="javaScriptBlock"/>'].
                    elements['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("elementName")) %>' context="javaScriptBlock"/>'].value = fullname;
                    if (opener.document['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("formName")) %>' context="javaScriptBlock"/>'].
                    elements['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("elementId")) %>' context="javaScriptBlock"/>'] != null
                )
                    opener.document
                ['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("formName")) %>' context="javaScriptBlock"/>'].
                    elements['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("elementId")) %>' context="javaScriptBlock"/>'].value = demographic_no;
                }
                self.close();
            }

            <%}%>
        </script>


        <form method="post" name="addform" action="<%= request.getContextPath() %>/appointment/addappointment">
<input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>"/>
<input type="hidden" name="demographic_no" value=""/>
<input type="hidden" name="chart_no" value=""/>
<input type="hidden" name="name" value=""/>
<input type="hidden" name="bFirstDisp" value=""/>
<input type="hidden" name="messageID" value=""/>
<input type="hidden" name="doctor_no" value=""/>

            <div class="table-responsive">
            <table class="table table-sm table-striped">
                <tr class="tableHeadings deep">


                    <th class="demoIdSearch">
                        <fmt:message key="demographic.demographicsearch2apptresults.demographicId"/>
                    </th>

                    <th class="lastname">
                        <fmt:message key="demographic.demographicsearch2apptresults.lastName"/>
                    </th>
                    <th class="firstname">
                        <fmt:message key="demographic.demographicsearch2apptresults.firstName"/>
                    </th>
                    <th class="age">
                        <fmt:message key="demographic.demographicsearch2apptresults.age"/>
                    </th>
                    <th class="rosterStatus">
                        <fmt:message key="demographic.demographicsearch2apptresults.rosterStatus"/>
                    </th>
                    <th class="sex">
                        <fmt:message key="demographic.demographicsearch2apptresults.sex"/>
                    </th>
                    <th class="dob">
                        <fmt:message key="demographic.demographicsearch2apptresults.DOB"/>
                    </th>
                    <th class="doctor">
                        <fmt:message key="demographic.demographicsearch2apptresults.doctor"/>
                    </th>
                </tr>


                <%
                    String ptstatus = request.getParameter("ptstatus") == null ? "active" : request.getParameter("ptstatus");
                    io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().debug("PSTATUS " + ptstatus);

                    int rowCounter = 0;
                    String bgColor = rowCounter % 2 == 0 ? "#EEEEFF" : "white";

                    String pstatus = props.getProperty("inactive_statuses", "IN, DE, IC, ID, MO, FI");
                    pstatus = pstatus.replaceAll("'", "").replaceAll("\\s", "");
                    List<String> stati = Arrays.asList(pstatus.split(","));

                    if (Boolean.TRUE.equals(request.getAttribute("showRecentPatients"))) {
                        int mostRecentPatientListSize = Integer.parseInt(CarlosProperties.getInstance().getProperty("MOST_RECENT_PATIENT_LIST_SIZE", "3"));
                        List<Integer> results = oscarLogDao.getRecentDemographicsAccessedByProvider(providerNo, 0, mostRecentPatientListSize);
                        demoList = new ArrayList<Demographic>();
                        for (Integer r : results) {
                            // A patient can disappear after the recent-ID query.
                            Demographic recentPatient = demographicDao.getDemographicById(r);
                            if (recentPatient != null) demoList.add(recentPatient);
                        }
                    } else {

                        if ("".equals(ptstatus)) {
                            if (searchMode.equals("search_name")) {
                                demoList = demographicDao.searchDemographicByName(keyword, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_phone")) {
                                demoList = demographicDao.searchDemographicByPhone(keyword, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_dob")) {
                                demoList = demographicDao.searchDemographicByDOB(keyword, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_address")) {
                                demoList = demographicDao.searchDemographicByAddress(keyword, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_hin")) {
                                demoList = demographicDao.searchDemographicByHIN(keyword, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_chart_no")) {
                                demoList = demographicDao.findDemographicByChartNo(keyword, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_demographic_no")) {
                                demoList = demographicDao.findDemographicByDemographicNo(keyword, limit, offset, providerNo, outOfDomain);
                            }

                        } else if ("active".equals(ptstatus)) {
                            if (searchMode.equals("search_name")) {
                                demoList = demographicDao.searchDemographicByNameAndNotStatus(keyword, stati, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_phone")) {
                                demoList = demographicDao.searchDemographicByPhoneAndNotStatus(keyword, stati, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_dob")) {
                                demoList = demographicDao.searchDemographicByDOBAndNotStatus(keyword, stati, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_address")) {
                                demoList = demographicDao.searchDemographicByAddressAndNotStatus(keyword, stati, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_hin")) {
                                demoList = demographicDao.searchDemographicByHINAndNotStatus(keyword, stati, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_chart_no")) {
                                demoList = demographicDao.findDemographicByChartNoAndNotStatus(keyword, stati, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_demographic_no")) {
                                demoList = demographicDao.findDemographicByDemographicNoAndNotStatus(keyword, stati, limit, offset, providerNo, outOfDomain);
                            }
                        } else if ("inactive".equals(ptstatus)) {
                            if (searchMode.equals("search_name")) {
                                demoList = demographicDao.searchDemographicByNameAndStatus(keyword, stati, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_phone")) {
                                demoList = demographicDao.searchDemographicByPhoneAndStatus(keyword, stati, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_dob")) {
                                demoList = demographicDao.searchDemographicByDOBAndStatus(keyword, stati, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_address")) {
                                demoList = demographicDao.searchDemographicByAddressAndStatus(keyword, stati, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_hin")) {
                                demoList = demographicDao.searchDemographicByHINAndStatus(keyword, stati, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_chart_no")) {
                                demoList = demographicDao.findDemographicByChartNoAndStatus(keyword, stati, limit, offset, providerNo, outOfDomain);
                            } else if (searchMode.equals("search_demographic_no")) {
                                demoList = demographicDao.findDemographicByDemographicNoAndStatus(keyword, stati, limit, offset, providerNo, outOfDomain);
                            }
                        }
                    }

                    if (demoList == null) {
                        //out.println("failed!!!");
                    } else {
                        Collections.sort(demoList, Demographic.LastNameComparator);

                        DemographicMerged dmDAO = new DemographicMerged();

                        for (Demographic demo : demoList) {

                            String dem_no = demo.getDemographicNo().toString();
                            String head = dmDAO.getHead(dem_no);

                            if (head != null && !head.equals(dem_no)) {
                                //skip non head records
                                continue;
                            }

                            rowCounter++;
                            bgColor = rowCounter % 2 == 0 ? "#EEEEFF" : "white";

                %>
                <c:set var="__enc_1"><carlos:encode value='<%= StringUtils.noNull(demo.getLastName()) %>' context="uriComponent"/></c:set>
                <c:set var="__enc_2"><carlos:encode value='<%= StringUtils.noNull(demo.getFirstName()) %>' context="uriComponent"/></c:set>
                <c:set var="__enc_3"><carlos:encode value='<%= demo.getChartNo() == null ? "" : demo.getChartNo() %>' context="uriComponent"/></c:set>
                <tr style="background-color: <%=bgColor%>"
                    onMouseOver="this.style.cursor='hand';this.style.backgroundColor='pink';"
                    onMouseout="this.style.backgroundColor='<%=bgColor%>';"
                    onClick="<% if(caisi) { out.print("addNameCaisi");} else { out.print("addName");} %>('<%=demo.getDemographicNo()%>','<carlos:encode value='${__enc_1}' context="javaScriptAttribute"/>','<carlos:encode value='${__enc_2}' context="javaScriptAttribute"/>','<carlos:encode value='${__enc_3}' context="javaScriptAttribute"/>','<carlos:encode value='<%= StringUtils.noNull(request.getParameter("messageId")) %>' context="javaScriptAttribute"/>','<carlos:encode value='<%= StringUtils.noNull(demo.getProviderNo()) %>' context="javaScriptAttribute"/>')">

                    <c:set var="__enc_4"><carlos:encode value='<%= StringUtils.noNull(demo.getLastName()) %>' context="uriComponent"/></c:set>
                    <c:set var="__enc_5"><carlos:encode value='<%= StringUtils.noNull(demo.getFirstName()) %>' context="uriComponent"/></c:set>
                    <c:set var="__enc_6"><carlos:encode value='<%= demo.getChartNo() == null ? "" : demo.getChartNo() %>' context="uriComponent"/></c:set>
                    <td class="demoId">
                        <input type="button" class="mbttn btn btn-secondary btn-sm" name="pick_demographic"
                               value="<%=demo.getDemographicNo()%>"
                               onClick="event.stopPropagation(); <% if(caisi) {out.print("addNameCaisi");} else {out.print("addName");} %>('<%=demo.getDemographicNo()%>','<carlos:encode value='${__enc_4}' context="javaScriptAttribute"/>','<carlos:encode value='${__enc_5}' context="javaScriptAttribute"/>','<carlos:encode value='${__enc_6}' context="javaScriptAttribute"/>','<carlos:encode value='<%= StringUtils.noNull(request.getParameter("messageId")) %>' context="javaScriptAttribute"/>','<carlos:encode value='<%= StringUtils.noNull(demo.getProviderNo()) %>' context="javaScriptAttribute"/>')">
                    </td>
                    <td class="lastName"><carlos:encode value='<%= Misc.toUpperLowerCase(demo.getLastName()) %>' context="html"/>
                    </td>
                    <td class="firstName"><%=SafeEncode.forHtml(Misc.toUpperLowerCase(demo.getFirstName())) + " " + SafeEncode.forHtml(Misc.toUpperLowerCase(demo.getMiddleNames()))%>
                    </td>
                    <td class="age"><carlos:encode value='<%= demo.getAge() == null ? "" : String.valueOf(demo.getAge()) %>' context="html"/>
                    </td>
                    <td class="rosterStatus"><% if (demo.getRosterStatus() == null || demo.getRosterStatus().equals("")) { %>&nbsp;<% } else { %><carlos:encode value='<%= demo.getRosterStatus() %>' context="html"/><% } %>
                    </td>
                    <td class="sex"><carlos:encode value='<%= demo.getSex() == null ? "" : demo.getSex() %>' context="html"/>
                    </td>
                    <td class="dob"><carlos:encode value='<%= demo.getYearOfBirth() + "-" + demo.getMonthOfBirth() + "-" + demo.getDateOfBirth() %>' context="html"/>
                    </td>
                    <td class="doctor"><carlos:encode value='<%= providerBean.getProperty(demo.getProviderNo() == null ? "" : demo.getProviderNo()) == null ? "" : providerBean.getProperty(demo.getProviderNo()) %>' context="html"/>
                    </td>
                </tr>

                <%
                        }
                    }

                    for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                        temp = e.nextElement().toString();
                        if (temp.equals("keyword") || temp.equals("dboperation") || temp.equals("displaymode") || temp.equals("submit")
                            || temp.equals(org.owasp.csrfguard.CsrfGuard.getInstance().getTokenName())
                            || java.util.Set.of("demographic_no", "chart_no", "name", "bFirstDisp", "messageID", "doctor_no").contains(temp))
                            continue; %>
                <input type="hidden" name="<carlos:encode value='<%= temp %>' context="htmlAttribute"/>"
                       value="<carlos:encode value='<%= request.getParameter(temp) != null ? request.getParameter(temp) : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                <% }

                %>

            </table>
            </div>
        </form>
        <%
            int nLastPage = 0, nNextPage = 0;
            nNextPage = Integer.parseInt(strLimit2) + Integer.parseInt(strLimit1);
            nLastPage = Integer.parseInt(strLimit1) - Integer.parseInt(strLimit2);
        %>
        <%
            if (rowCounter == 0 && nLastPage <= 0) {

                java.util.HashMap<String, String> params = new java.util.HashMap<String, String>();
                params.put("originalPage", request.getParameter("originalPage"));
                params.put("provider_no", request.getParameter("provider_no"));
                params.put("bFirstDisp", request.getParameter("bFirstDisp"));
                params.put("year", request.getParameter("year"));
                params.put("month", request.getParameter("month"));
                params.put("day", request.getParameter("day"));
                params.put("start_time", request.getParameter("start_time"));
                params.put("end_time", request.getParameter("end_time"));
                params.put("duration", request.getParameter("duration"));
                params.put("appointment_date", request.getParameter("appointment_date"));
                params.put("notes", request.getParameter("notes"));
                params.put("reason", request.getParameter("reason"));
                params.put("reasonCode", request.getParameter("reasonCode"));
                params.put("location", request.getParameter("location"));
                params.put("resources", request.getParameter("resources"));
                params.put("apptType", request.getParameter("type"));
                params.put("style", request.getParameter("style"));
                params.put("billing", request.getParameter("billing"));
                params.put("status", request.getParameter("status"));
                params.put("createdatetime", request.getParameter("createdatetime"));
                params.put("creator", request.getParameter("creator"));
                params.put("remarks", request.getParameter("remarks"));

                pageContext.setAttribute("apptParamsName", params);

                if (CarlosProperties.getInstance().getProperty("ModuleNames", "").indexOf("Caisi") != -1 &&
                        CarlosProperties.getInstance().getProperty("caisi.search.workflow", "false").equals("true")) {

        %>
        <fmt:message key="demographic.search.noResultsWereFound"/>
        <div class="createNew">
            <form method="post" action="${pageContext.request.contextPath}/demographic/ViewDemographicAddARecordHtm">
<input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>"/>
<c:forTokens var="searchField" items="originalPage,search_mode,keyword,notes,appointment_date,year,month,day,start_time,end_time,duration,provider_no,reasonCode,reason,location,resources,type,style,billing,status,createdatetime,creator,remarks" delims=",">
        <input type="hidden" name="${carlos:forHtmlAttribute(searchField)}" value="${carlos:forHtmlAttribute(param[searchField])}"/>
    </c:forTokens><input type="hidden" name="fromAppt" value="1"/><input type="hidden" name="bFirstDisp" value="false"/>
<button type="submit" class="btn btn-link p-0"><fmt:message key="demographic.search.btnCreateNew"/></button>
</form>
        </div>
        <%
        } else {
        %>
        <fmt:message key="demographic.search.noResultsWereFound"/>
        <div class="createNew">
            <form method="post" action="${pageContext.request.contextPath}/demographic/ViewDemographicAddARecordHtm">
<input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>"/>
<c:forTokens var="searchField" items="originalPage,search_mode,keyword,notes,appointment_date,year,month,day,start_time,end_time,duration,provider_no,reasonCode,reason,location,resources,type,style,billing,status,createdatetime,creator,remarks" delims=",">
        <input type="hidden" name="${carlos:forHtmlAttribute(searchField)}" value="${carlos:forHtmlAttribute(param[searchField])}"/>
    </c:forTokens><input type="hidden" name="fromAppt" value="1"/><input type="hidden" name="bFirstDisp" value="false"/>
<button type="submit" class="btn btn-link p-0"><fmt:message key="demographic.search.btnCreateNew"/></button>
</form>
        </div>
        <%
            }
        %>


        <%
            }
        %>

        <a href="#" onclick="showHideItem('demographicSearch');" id="searchPopUpButton"
           class="rightButton top">Search</a>
        <div class="bottomBar" style="margin-bottom:10px; margin-top:10px;">
            <form method="post" name="nextform" action="<%= request.getContextPath() %>/demographic/DemographicSearch">
<input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>"/>
<input type="hidden" name="limit2" value="<%= strLimit2 %>"/>
                <%
                    if (nLastPage >= 0) {
                %>
                <button type="submit" class="btn btn-secondary" id="prevPageButton" name="limit1" value="<%=nLastPage%>"><fmt:message key="demographic.demographicsearch2apptresults.btnPrevPage"/></button>
                <%
                    }

                    if (rowCounter == limit) {
                %>
                <button type="submit" class="btn btn-secondary" id="nextPageButton" name="limit1" value="<%=nNextPage%>"><fmt:message key="demographic.demographicsearch2apptresults.btnNextPage"/></button>
                <%
                    }
                    for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                        temp = e.nextElement().toString();
                        if (temp.equals("limit1") || temp.equals("limit2") || temp.equals(org.owasp.csrfguard.CsrfGuard.getInstance().getTokenName()) || temp.equals("submit") || temp.equals("chart_no"))
                            continue; %>
                <input type='hidden' name="<carlos:encode value='<%= temp %>' context="htmlAttribute"/>"
                       value="<carlos:encode value='<%= request.getParameter(temp) != null ? request.getParameter(temp) : "" %>' context="htmlAttribute"/>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                <% }
                %>

            </form>
        </div>
    </div>
</div>
</body>
</html>
