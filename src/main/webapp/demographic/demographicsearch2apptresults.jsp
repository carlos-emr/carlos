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
<security:oscarSec roleName="<%=roleName$%>" objectName="_search" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_search");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<!DOCTYPE HTML>
<%@ taglib prefix="csrf" uri="http://www.owasp.org/index.php/Category:OWASP_CSRFGuard_Project/Owasp.CsrfGuard.tld" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<%@page import="java.nio.charset.StandardCharsets" %>
<%@page import="ca.openosp.openo.utility.MiscUtils" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="ca.openosp.openo.caisi_integrator.ws.CachedProvider" %>
<%@page import="ca.openosp.openo.caisi_integrator.ws.FacilityIdStringCompositePk" %>
<%@page import="ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager" %>
<%@page import="org.apache.commons.lang3.time.DateFormatUtils" %>
<%@page import="org.apache.commons.lang3.StringUtils" %>
<%@page import="ca.openosp.openo.util.DateUtils" %>
<%@page import="ca.openosp.openo.caisi_integrator.ws.DemographicTransfer" %>
<%@page import="ca.openosp.openo.caisi_integrator.ws.MatchingDemographicTransferScore" %>
<%@page import="ca.openosp.openo.casemgmt.service.CaseManagementManager" %>

<%@ page import="java.util.*, java.sql.*,java.net.*, ca.openosp.*" errorPage="/errorpage.jsp" %>

<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.model.Demographic" %>
<%@page import="ca.openosp.openo.commn.dao.DemographicDao" %>
<%@ page import="ca.openosp.openo.demographic.data.DemographicMerged" %>
<%@page import="ca.openosp.openo.commn.dao.OscarLogDao" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.Misc" %>
<%@ page import="ca.openosp.OscarProperties" %>
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

    int offset = Integer.parseInt(strLimit1);
    int limit = Integer.parseInt(strLimit2);
    boolean caisi = Boolean.valueOf(request.getParameter("caisi")).booleanValue();

    OscarProperties props = OscarProperties.getInstance();

    List<Demographic> demoList = null;
    DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    OscarLogDao oscarLogDao = (OscarLogDao) SpringUtils.getBean(OscarLogDao.class);
    String providerNo = loggedInInfo.getLoggedInProviderNo();
    boolean outOfDomain = true;
    if (OscarProperties.getInstance().getProperty("ModuleNames", "").indexOf("Caisi") != -1) {
        if (!"true".equals(OscarProperties.getInstance().getProperty("pmm.client.search.outside.of.domain.enabled", "true"))) {
            outOfDomain = false;
        }
        if (request.getParameter("outofdomain") != null && request.getParameter("outofdomain").equals("true")) {
            outOfDomain = true;
        }
    }

%>

<html>
<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.title"/></title>
    <script src="${pageContext.request.contextPath}/csrfguard"></script>
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
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js" type="text/javascript"></script>
    <script src="${pageContext.request.contextPath}/library/bootstrap/3.0.0/js/bootstrap.min.js"
            type="text/javascript"></script>
    <link href="${pageContext.request.contextPath}/library/bootstrap/3.0.0/css/bootstrap.css" rel="stylesheet"
          type="text/css"/>
    <script language="javascript" type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
    <script language="JavaScript">
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
            var dob = document.titlesearch.keyword;

            if (dob.value.indexOf('%b610054') == 0 && dob.value.length > 18) {
                document.titlesearch.keyword.value = dob.value.substring(8, 18);
                document.titlesearch.search_mode[4].checked = true;
            }

            if (document.titlesearch.search_mode[2].checked) {
                if (dob.value.length == 8) {
                    dob.value = dob.value.substring(0, 4) + "-" + dob.value.substring(4, 6) + "-" + dob.value.substring(6, 8);
                }
                if (dob.value.length != 10) {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.msgWrongDOB"/>");
                    return false;
                } else {
                    return true;
                }
            } else {
                return true;
            }
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
    <form method="post" name="titlesearch" action="<%= request.getContextPath() %>/demographic/demographiccontrol.jsp"
          onSubmit="return checkTypeIn()">
        <div id="demographicSearch" class="searchBox input-group select-group" style="margin-bottom:10px;">
            <%--    <ul style="display: flex;">--%>
            <%--        <li>--%>
            <select class="wideInput form-control input-group-addon" name="search_mode">
                <option value="search_name" <%=request.getParameter("search_mode").equals("search_name") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.optName"/>
                </option>
                <option value="search_phone" <%=request.getParameter("search_mode").equals("search_phone") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.optPhone"/>
                </option>
                <option value="search_dob" <%=request.getParameter("search_mode").equals("search_dob") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.optDOB"/>
                </option>
                <option value="search_address" <%=request.getParameter("search_mode").equals("search_address") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.optAddress"/>
                </option>
                <option value="search_hin" <%=request.getParameter("search_mode").equals("search_hin") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.optHIN"/>
                </option>
                <option value="search_chart_no" <%=request.getParameter("search_mode").equals("search_chart_no") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.optChart"/>
                </option>
                <option value="search_demographic_no" <%=request.getParameter("search_mode").equals("search_demographic_no") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.demographicId"/>
                </option>
            </select>
            <%--        </li>--%>
            <%--        <li>--%>

            <input type="text" class="wideInput form-control" NAME="keyword"
                   VALUE="<%=Encode.forHtmlAttribute(request.getParameter("keyword"))%>" SIZE="17" MAXLENGTH="100"/>
            <%--        </li>--%>
            <%--        <li>--%>
            <INPUT TYPE="hidden" NAME="orderby" VALUE="last_name, first_name">
            <INPUT TYPE="hidden" NAME="dboperation" VALUE="search_titlename">
            <INPUT TYPE="hidden" NAME="limit1" VALUE="0">
            <INPUT TYPE="hidden" NAME="limit2" VALUE="5">
            <input type="hidden" name="displaymode" value="Search ">
            <INPUT TYPE="hidden" NAME="ptstatus" VALUE="active">

            <input type="hidden" name="fromAppt" value="<%=Encode.forHtmlAttribute(request.getParameter("fromAppt"))%>">
            <input type="hidden" name="originalPage"
                   value="<%=Encode.forHtmlAttribute(request.getParameter("originalPage"))%>">
            <input type="hidden" name="bFirstDisp"
                   value="<%=Encode.forHtmlAttribute(request.getParameter("bFirstDisp"))%>">
            <input type="hidden" name="provider_no"
                   value="<%=Encode.forHtmlAttribute(request.getParameter("provider_no"))%>">
            <input type="hidden" name="start_time"
                   value="<%=Encode.forHtmlAttribute(request.getParameter("start_time"))%>">
            <input type="hidden" name="end_time" value="<%=Encode.forHtmlAttribute(request.getParameter("end_time"))%>">
            <input type="hidden" name="year" value="<%=Encode.forHtmlAttribute(request.getParameter("year"))%>">
            <input type="hidden" name="month" value="<%=Encode.forHtmlAttribute(request.getParameter("month"))%>">
            <input type="hidden" name="day" value="<%=Encode.forHtmlAttribute(request.getParameter("day"))%>">
            <input type="hidden" name="appointment_date"
                   value="<%=Encode.forHtmlAttribute(request.getParameter("appointment_date"))%>">
            <input type="hidden" name="notes" value="<%=Encode.forHtmlAttribute(request.getParameter("notes"))%>">
            <input type="hidden" name="reasonCode"
                   value="<%=Encode.forHtmlAttribute(request.getParameter("reasonCode"))%>">
            <input type="hidden" name="reason" value="<%=Encode.forHtmlAttribute(request.getParameter("reason"))%>">
            <input type="hidden" name="location" value="<%=Encode.forHtmlAttribute(request.getParameter("location"))%>">
            <input type="hidden" name="resources"
                   value="<%=Encode.forHtmlAttribute(request.getParameter("resources"))%>">
            <input type="hidden" name="type" value="<%=Encode.forHtmlAttribute(request.getParameter("type"))%>">
            <input type="hidden" name="style" value="<%=Encode.forHtmlAttribute(request.getParameter("style"))%>">
            <input type="hidden" name="billing" value="<%=Encode.forHtmlAttribute(request.getParameter("billing"))%>">
            <input type="hidden" name="status" value="<%=Encode.forHtmlAttribute(request.getParameter("status"))%>">
            <input type="hidden" name="createdatetime"
                   value="<%=Encode.forHtmlAttribute(request.getParameter("createdatetime"))%>">
            <input type="hidden" name="creator" value="<%=Encode.forHtmlAttribute(request.getParameter("creator"))%>">
            <input type="hidden" name="remarks" value="<%=Encode.forHtmlAttribute(request.getParameter("remarks"))%>">

            <%
                String temp = null;
                for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                    temp = e.nextElement().toString();
                    if (temp.equals("keyword") || temp.equals("dboperation") || temp.equals("displaymode") || temp.equals("search_mode") || temp.equals("chart_no") || temp.equals("ptstatus") || temp.equals("submit") || temp.equals("includeIntegratedResults"))
                        continue;
            %>
            <input type="hidden" name="<%=Encode.forHtmlAttribute(temp)%>"
                   value="<%=Encode.forHtmlAttribute(request.getParameter(temp))%>">
            <% }
            %>
            <div class="input-group-btn">
                <a href="#" onclick="showHideItem('demographicSearch');" id="cancelButton"
                   class="leftButton top btn btn-link">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnCancel"/>
                </a>
                <input type="SUBMIT" class="btn btn-primary" name="displaymode"
                       value='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.search"/>'
                       title='<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchActive"/>'>
                <INPUT TYPE="button" id="inactiveButton" class="btn btn-secondary"
                       onclick="searchInactive();"
                       TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchInactive"/>"
                       VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.Inactive"/>">
                <INPUT TYPE="button" id="allButton" class="btn btn-secondary"
                       onclick="searchAll();"
                       TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchAll"/>"
                       VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.All"/>">
            </div>
            <%
                if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
            %>
            <input type="checkbox" class="checkbox-inline" name="includeIntegratedResults"
                   value="true"   <%="true".equals(request.getParameter("includeIntegratedResults")) ? "checked" : ""%>/>
            <span style="font-size:small"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.msgInclIntegratedResults"/></span>
            <% } %>
            <%--   </li>--%>
            <% if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {%>
            <%--        <li>--%>
            <jsp:include page="/admin/IntegratorStatus.jspf"></jsp:include>
            <%--        </li>--%>
            <% } %>
            <%--    </ul>--%>
        </div>
    </form>


    <div id="searchResults" style="margin-bottom:10px;">

        <div>
            <%if (request.getParameter("keyword") != null && request.getParameter("keyword").length() == 0) { %>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.msgMostRecentPatients"/>
            <% } else { %>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.msgKeywords"/> <%=Encode.forHtml(request.getParameter("keyword"))%> <%}%>
        </div>
        <script language="JavaScript">

            var fullname = "";
            <%-- RJ 07/10/2006 Need to pass doctor of patient back to referrer --%>

            function addName(demographic_no, lastname, firstname, chartno, messageID, doctorNo, remoteFacilityId) {
                fullname = lastname + "," + firstname;

                if (remoteFacilityId == '') {
                    document.addform.action = "<%=request.getParameter("originalpage")%>?";
                } else {
                    document.addform.action = "<%=request.getContextPath()%>/appointment/copyRemoteDemographic.jsp?originalPage=<%=URLEncoder.encode(request.getParameter("originalpage"), StandardCharsets.UTF_8)%>&";
                }

                document.addform.action = document.addform.action + "demographic_no=" + demographic_no + "&name=" + fullname + "&chart_no=" + chartno + "&bFirstDisp=false" + "&messageID=" + messageID + "&doctor_no=" + doctorNo + "&remoteFacilityId=" + remoteFacilityId;

                document.addform.submit();
                return true;
            }

            <%if(caisi) {%>

            function addNameCaisi(demographic_no, lastname, firstname, chartno, messageID) {
                fullname = lastname + "," + firstname;
                if (opener.document.<%=request.getParameter("formName")%> != null) {
                    if (opener.document.<%=request.getParameter("formName")%>.
                    elements['<%=request.getParameter("elementName")%>'] != null
                )
                    opener.document
                .<%=request.getParameter("formName")%>.
                    elements['<%=request.getParameter("elementName")%>'].value = fullname;
                    if (opener.document.<%=request.getParameter("formName")%>.
                    elements['<%=request.getParameter("elementId")%>'] != null
                )
                    opener.document
                .<%=request.getParameter("formName")%>.
                    elements['<%=request.getParameter("elementId")%>'].value = demographic_no;
                }
                self.close();
            }

            <%}%>
        </script>


        <form method="post" name="addform" action="<%= request.getContextPath() %>/appointment/addappointment.jsp">
            <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>"/>

            <table class="table table-condensed table-striped table-responsive">
                <tr class="tableHeadings deep">


                    <th class="demoIdSearch">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.demographicId"/>
                    </th>

                    <th class="lastname">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.lastName"/>
                    </th>
                    <th class="firstname">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.firstName"/>
                    </th>
                    <th class="age">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.age"/>
                    </th>
                    <th class="rosterStatus">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.rosterStatus"/>
                    </th>
                    <th class="sex">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.sex"/>
                    </th>
                    <th class="dob">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.DOB"/>
                    </th>
                    <th class="doctor">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.doctor"/>
                    </th>
                </tr>


                <%
                    String ptstatus = request.getParameter("ptstatus") == null ? "active" : request.getParameter("ptstatus");
                    ca.openosp.openo.utility.MiscUtils.getLogger().debug("PSTATUS " + ptstatus);

                    int rowCounter = 0;
                    String bgColor = rowCounter % 2 == 0 ? "#EEEEFF" : "white";

                    String pstatus = props.getProperty("inactive_statuses", "IN, DE, IC, ID, MO, FI");
                    pstatus = pstatus.replaceAll("'", "").replaceAll("\\s", "");
                    List<String> stati = Arrays.asList(pstatus.split(","));

                    if (request.getParameter("keyword") != null && request.getParameter("keyword").length() == 0) {
                        int mostRecentPatientListSize = Integer.parseInt(OscarProperties.getInstance().getProperty("MOST_RECENT_PATIENT_LIST_SIZE", "3"));
                        List<Integer> results = oscarLogDao.getRecentDemographicsAccessedByProvider(providerNo, 0, mostRecentPatientListSize);
                        demoList = new ArrayList<Demographic>();
                        for (Integer r : results) {
                            demoList.add(demographicDao.getDemographicById(r));
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
                <tr style="background-color: <%=bgColor%>"
                    onMouseOver="this.style.cursor='hand';this.style.backgroundColor='pink';"
                    onMouseout="this.style.backgroundColor='<%=bgColor%>';"
                    onClick="document.forms[0].demographic_no.value=<%=demo.getDemographicNo()%>;<% if(caisi) { out.print("addNameCaisi");} else { out.print("addName");} %>('<%=demo.getDemographicNo()%>','<%=URLEncoder.encode(demo.getLastName(), StandardCharsets.UTF_8)%>','<%=URLEncoder.encode(demo.getFirstName(), StandardCharsets.UTF_8)%>','<%=URLEncoder.encode(demo.getChartNo() == null ? "" : demo.getChartNo(), StandardCharsets.UTF_8)%>','<%=request.getParameter("messageId")%>','<%=demo.getProviderNo()%>','')">

                    <td class="demoId">
                        <input type="submit" class="mbttn btn btn-default btn-sm" name="demographic_no"
                               value="<%=demo.getDemographicNo()%>"
                               onClick="<% if(caisi) {out.print("addNameCaisi");} else {out.print("addName");} %>('<%=demo.getDemographicNo()%>','<%=URLEncoder.encode(demo.getLastName(), StandardCharsets.UTF_8)%>','<%=URLEncoder.encode(demo.getFirstName(), StandardCharsets.UTF_8)%>','<%=URLEncoder.encode(demo.getChartNo() == null ? "" : demo.getChartNo(), StandardCharsets.UTF_8)%>','<%=request.getParameter("messageId")%>','<%=demo.getProviderNo()%>','')">
                    </td>
                    <td class="lastName"><%=Encode.forHtml(Misc.toUpperLowerCase(demo.getLastName()))%>
                    </td>
                    <td class="firstName"><%=Encode.forHtml(Misc.toUpperLowerCase(demo.getFirstName())) + " " + Encode.forHtml(Misc.toUpperLowerCase(demo.getMiddleNames()))%>
                    </td>
                    <td class="age"><%=demo.getAge()%>
                    </td>
                    <td class="rosterStatus"><%=demo.getRosterStatus() == null || demo.getRosterStatus().equals("") ? "&nbsp;" : demo.getRosterStatus()%>
                    </td>
                    <td class="sex"><%=demo.getSex()%>
                    </td>
                    <td class="dob"><%=demo.getYearOfBirth() + "-" + demo.getMonthOfBirth() + "-" + demo.getDateOfBirth()%>
                    </td>
                    <td class="doctor"><%=providerBean.getProperty(demo.getProviderNo() == null ? "" : demo.getProviderNo()) == null ? "" : providerBean.getProperty(demo.getProviderNo())%>
                    </td>
                </tr>

                <%
                        }
                    }

                    @SuppressWarnings("unchecked")
                    List<MatchingDemographicTransferScore> integratorSearchResults = (List<MatchingDemographicTransferScore>) request.getAttribute("integratorSearchResults");
                    if (integratorSearchResults != null) {
                        for (MatchingDemographicTransferScore matchingDemographicTransferScore : integratorSearchResults) {
                            if (isLocal(matchingDemographicTransferScore, demoList)) {
                                continue;
                            }
                            rowCounter++;
                            bgColor = rowCounter % 2 == 0 ? "#EEEEFF" : "white";
                            DemographicTransfer demographicTransfer = matchingDemographicTransferScore.getDemographicTransfer();
                %>
                <tr style="background-color: <%=bgColor%>"
                    onMouseOver="this.style.cursor='hand';this.style.backgroundColor='pink';"
                    onMouseout="this.style.backgroundColor='<%=bgColor%>';"
                    onClick="document.forms[0].demographic_no.value=<%=demographicTransfer.getCaisiDemographicId()%>;addName('<%=demographicTransfer.getCaisiDemographicId()%>','<%=URLEncoder.encode(demographicTransfer.getLastName(), StandardCharsets.UTF_8)%>','<%=URLEncoder.encode(demographicTransfer.getFirstName(), StandardCharsets.UTF_8)%>','','<%=request.getParameter("messageId")%>','<%=demographicTransfer.getCaisiProviderId()%>','<%=demographicTransfer.getIntegratorFacilityId()%>')">
                    <td class="demoId" colspan="8">
                        <input type="submit" class="mbttn btn btn-default btn-sm" name="demographic_no"
                               value="Integrator <%=CaisiIntegratorManager.getRemoteFacility(loggedInInfo, loggedInInfo.getCurrentFacility(), demographicTransfer.getIntegratorFacilityId()).getName()%>:<%=demographicTransfer.getCaisiDemographicId()%>"/>
                    </td>
                    <td class="lastName"><%=Misc.toUpperLowerCase(demographicTransfer.getLastName())%>
                    </td>
                    <td class="firstName"><%=Misc.toUpperLowerCase(demographicTransfer.getFirstName())%>
                    </td>
                    <%
                        String ageString = "";
                        String bdayString = "";

                        if (demographicTransfer.getBirthDate() != null) {
                            Integer ageX = DateUtils.getAge(demographicTransfer.getBirthDate(), new GregorianCalendar());
                            ageString = ageX.toString();

                            bdayString = DateFormatUtils.ISO_DATE_FORMAT.format(demographicTransfer.getBirthDate());
                        }
                    %>
                    <td class="age"><%=ageString%>
                    </td>
                    <td class="rosterStatus"></td>
                    <td class="sex"><%=demographicTransfer.getGender()%>
                    </td>
                    <td class="dob"><%=bdayString%>
                    </td>
                    <td class="doctor">
                        <%
                            FacilityIdStringCompositePk providerPk = new FacilityIdStringCompositePk();
                            providerPk.setIntegratorFacilityId(demographicTransfer.getIntegratorFacilityId());
                            providerPk.setCaisiItemId(demographicTransfer.getCaisiProviderId());
                            CachedProvider cachedProvider = CaisiIntegratorManager.getProvider(loggedInInfo, loggedInInfo.getCurrentFacility(), providerPk);
                            MiscUtils.getLogger().debug("Cached providers, pk=" + providerPk.getIntegratorFacilityId() + "," + providerPk.getCaisiItemId() + ", cachedProvider=" + cachedProvider);

                            String providerName = "";

                            if (cachedProvider != null) {
                                providerName = cachedProvider.getLastName() + ", " + cachedProvider.getFirstName();
                            }
                        %>
                        <%=Encode.forHtml(providerName)%>
                    </td>
                </tr>
                <%
                        }
                    }
                    for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                        temp = e.nextElement().toString();
                        if (temp.equals("keyword") || temp.equals("dboperation") || temp.equals("displaymode") || temp.equals("submit") || temp.equals("chart_no"))
                            continue; %>
                <input type="hidden" name="<%=Encode.forHtmlAttribute(temp)%>"
                       value="<%=Encode.forHtmlAttribute(request.getParameter(temp))%>">
                <% }

                %>

            </table>
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

                if (OscarProperties.getInstance().getProperty("ModuleNames", "").indexOf("Caisi") != -1 &&
                        OscarProperties.getInstance().getProperty("caisi.search.workflow", "false").equals("true")) {

        %>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.noResultsWereFound"/>
        <div class="createNew">
            <a href="<%= request.getContextPath() %>/demographic/demographicaddarecordhtm.jsp?fromAppt=1&originalPage=<%=request.getParameter("originalPage")%>&search_mode=<%=Encode.forUriComponent(request.getParameter("search_mode"))%>&keyword=<%=Encode.forUriComponent(request.getParameter("keyword"))%>&notes=<%=Encode.forUriComponent(request.getParameter("notes"))%>&appointment_date=<%=request.getParameter("appointment_date")%>&year=<%=request.getParameter("year")%>&month=<%=request.getParameter("month")%>&day=<%=request.getParameter("day")%>&start_time=<%=request.getParameter("start_time")%>&end_time=<%=request.getParameter("end_time")%>&duration=<%=request.getParameter("duration")%>&bFirstDisp=false&provider_no=<%=request.getParameter("provider_no")%>&notes=<%=Encode.forUriComponent(request.getParameter("notes"))%>&reasonCode=<%=Encode.forUriComponent(request.getParameter("reasonCode"))%>&reason=<%=Encode.forUriComponent(request.getParameter("reason"))%>&location=<%=Encode.forUriComponent(request.getParameter("location"))%>&resources=<%=request.getParameter("resources")%>&type=<%=request.getParameter("type")%>&style=<%=request.getParameter("style")%>&billing=<%=request.getParameter("billing")%>&status=<%=Encode.forUriComponent(request.getParameter("status"))%>&createdatetime=<%=request.getParameter("createdatetime")%>&creator=<%=Encode.forUriComponent(request.getParameter("creator"))%>&remarks=<%=request.getParameter("remarks")%>">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.btnCreateNew"/></a>
        </div>
        <%
        } else {
        %>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.noResultsWereFound"/>
        <div class="createNew">
            <a href="<%= request.getContextPath() %>/demographic/demographicaddarecordhtm.jsp?fromAppt=1&originalPage=<%=request.getParameter("originalPage")%>&search_mode=<%=Encode.forUriComponent(request.getParameter("search_mode"))%>&keyword=<%=Encode.forUriComponent(request.getParameter("keyword"))%>&notes=<%=Encode.forUriComponent(request.getParameter("notes"))%>&appointment_date=<%=request.getParameter("appointment_date")%>&year=<%=request.getParameter("year")%>&month=<%=request.getParameter("month")%>&day=<%=request.getParameter("day")%>&start_time=<%=request.getParameter("start_time")%>&end_time=<%=request.getParameter("end_time")%>&duration=<%=request.getParameter("duration")%>&bFirstDisp=false&provider_no=<%=request.getParameter("provider_no")%>&notes=<%=Encode.forUriComponent(request.getParameter("notes"))%>&reasonCode=<%=Encode.forUriComponent(request.getParameter("reasonCode"))%>&reason=<%=Encode.forUriComponent(request.getParameter("reason"))%>&location=<%=Encode.forUriComponent(request.getParameter("location"))%>&resources=<%=request.getParameter("resources")%>&type=<%=request.getParameter("type")%>&style=<%=request.getParameter("style")%>&billing=<%=request.getParameter("billing")%>&status=<%=Encode.forUriComponent(request.getParameter("status"))%>&createdatetime=<%=request.getParameter("createdatetime")%>&creator=<%=Encode.forUriComponent(request.getParameter("creator"))%>&remarks=<%=request.getParameter("remarks")%>">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.btnCreateNew"/></a>
        </div>
        <%
            }
        %>


        <%
            }
        %>
        <script language="JavaScript">

            function last() {
                document.nextform.action = "<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?keyword=<%=request.getParameter("keyword")%>&search_mode=<%=request.getParameter("search_mode")%>&displaymode=<%=request.getParameter("displaymode")%>&dboperation=<%=request.getParameter("dboperation")%>&orderby=<%=request.getParameter("orderby")%>&limit1=<%=nLastPage%>&limit2=<%=strLimit2%>";
                //document.nextform.submit();
            }

            function next() {
                document.nextform.action = "<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?keyword=<%=request.getParameter("keyword")%>&search_mode=<%=request.getParameter("search_mode")%>&displaymode=<%=request.getParameter("displaymode")%>&dboperation=<%=request.getParameter("dboperation")%>&orderby=<%=request.getParameter("orderby")%>&limit1=<%=nNextPage%>&limit2=<%=strLimit2%>";
                //document.nextform.submit();
            }

            //-->
        </script>
        <a href="#" onclick="showHideItem('demographicSearch');" id="searchPopUpButton"
           class="rightButton top">Search</a>
        <div class="bottomBar" style="margin-bottom:10px; margin-top:10px;">
            <form method="post" name="nextform" action="<%= request.getContextPath() %>/demographic/demographiccontrol.jsp">
                <%
                    if (nLastPage >= 0) {
                %>
                <input type="submit" id="prevPageButton" name="submit" class="btn btn-default"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.btnPrevPage"/>"
                       onClick="last()">
                <%
                    }

                    if (rowCounter == limit) {
                %>
                <input type="submit" id="nextPageButton" class="btn btn-default" name="submit"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.btnNextPage"/>"
                       onClick="next()">
                <%
                    }
                    for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                        temp = e.nextElement().toString();
                        if (temp.equals("dboperation") || temp.equals("displaymode") || temp.equals("submit") || temp.equals("chart_no"))
                            continue; %>
                <input type='hidden' name="<%=Encode.forHtmlAttribute(temp)%>"
                       value="<%=Encode.forHtmlAttribute(request.getParameter(temp))%>">
                <% }
                %>

            </form>
        </div>
    </div>
</div>
</body>
</html>
<%!

    Boolean isLocal(MatchingDemographicTransferScore matchingDemographicTransferScore, List<Demographic> demoList) {
        String hin = matchingDemographicTransferScore.getDemographicTransfer().getHin();
        for (Demographic demo : demoList) {

            if (hin != null && hin.equals(demo.getHin())) {
                return true;
            }
        }

        return false;

    }

%>
