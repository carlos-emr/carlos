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
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ taglib uri="/WEB-INF/oscarProperties-tag.tld" prefix="oscarProp" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@page import="ca.openosp.openo.commn.dao.DemographicDao" %>
<%@page import="ca.openosp.openo.commn.model.Provider" %>
<%@page import="ca.openosp.openo.commn.model.Demographic" %>
<%@page import="ca.openosp.openo.commn.dao.DemographicArchiveDao" %>
<%@page import="ca.openosp.openo.commn.model.DemographicArchive" %>
<%@page import="java.util.List" %>
<%@page import="java.util.Date" %>
<%@page import="ca.openosp.openo.util.DateUtils" %>
<%@page import="ca.openosp.openo.util.StringUtils" %>
<%@page import="ca.openosp.openo.demographic.pageUtil.Util" %>
<html>
    <head>
        <title>Enrollment History</title>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/jquery.js"></script>
        <script>
            jQuery.noConflict();
        </script>
        <c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
        <script>
            var ctx = '<%=request.getContextPath()%>';
        </script>

        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicappthistory.title"/></title>
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/share/css/OscarStandardLayout.css">

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>


    </head>

    <%
        String demographicNo = request.getParameter("demographicNo");
        ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);

        //load demographic
        DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
        Demographic demographic = demographicDao.getClientByDemographicNo(Integer.valueOf(demographicNo));

        //load current roster status
        String rosterStatus = demographic.getRosterStatus();
        Date rosterDate = demographic.getRosterDate();
        Date rosterTermDate = demographic.getRosterTerminationDate();
    %>

    <body class="BodyStyle" vlink="#0000FF">

    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn">Enrolment History</td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td><%=demographic.getFormattedName() %> (<%=demographic.getFormattedDob()%>)</td>
                        <td>&nbsp;</td>
                        <td style="text-align: right"><a target="_blank"
                                                         href="http://www.oscarmanual.org/search?SearchableText=appointment history">Help</a>
                            | <a href="javascript:popupStart(300,400,'About.jsp')">
                                About</a> | <a href="javascript:popupStart(300,400,'License.jsp')">
                                License</a>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" valign="top">
                &nbsp;
            </td>
            <td class="MainTableRightColumn">
                <% if (!org.apache.commons.lang3.StringUtils.isEmpty(demographic.getRosterStatus())) { %>
                <h2>Current Enrollment Status</h2>
                <br/>
                <table style="width:5%">
                    <tr>
                        <td nowrap="nowrap"><b>Status</b>:</td>
                        <td nowrap="nowrap"><%=demographic.getRosterStatusDisplay()%>
                        </td>
                    </tr>
                    <% if ("RO".equals(demographic.getRosterStatus()) || "TE".equals(demographic.getRosterStatus())) { %>
                    <tr>
                        <td nowrap="nowrap"><b>Date Rostered</b>:</td>
                        <td nowrap="nowrap"><%=DateUtils.formatDate(demographic.getRosterDate(), request.getLocale())%>
                        </td>
                    </tr>
                    <tr>
                        <td nowrap="nowrap"><b>Enrolled To:</b>:</td>
                        <td nowrap="nowrap"><%=providerDao.getProviderName(demographic.getRosterEnrolledTo())%>
                        </td>
                    </tr>
                    <% } %>

                    <% if ("TE".equals(demographic.getRosterStatus())) { %>
                    <tr>
                        <td nowrap="nowrap"><b>Date Terminated</b>:</td>
                        <td nowrap="nowrap"><%=DateUtils.formatDate(demographic.getRosterTerminationDate(), request.getLocale())%>
                        </td>
                    </tr>
                    <tr>
                        <td nowrap="nowrap"><b>Termination Reason:</b>:</td>
                        <td nowrap="nowrap"><%=Util.rosterTermReasonProperties.getReasonByCode(demographic.getRosterTerminationReason()) %>
                        </td>
                    </tr>
                    <% } %>
                </table>

                <br/>

                <h2>Patient Enrollment History</h2>
                <br/>

                <table width="80%">
                    <%
                        DemographicArchiveDao demoArchiveDao = (DemographicArchiveDao) SpringUtils.getBean(DemographicArchiveDao.class);
                        List<DemographicArchive> archiveList = demoArchiveDao.findByDemographicNoChronologically(demographic.getDemographicNo());

                        String currentRosterStatus = null;

                        boolean printedHeaders = false;

                        String lastRosterStatusDisplay = null;
                        String lastRosterDateDisplay = null;
                        String lastRosterEnrolledToDisplay = null;
                        String lastRosterTerminationDateDisplay = null;
                        String lastRosterTerminationReasonDisplay = null;

                        for (DemographicArchive da : archiveList) {
                            String itemRosterStatus = da.getRosterStatus();

                            //has any field changed.
                            if (itemRosterStatus != null && !itemRosterStatus.equals(currentRosterStatus)) {
                                String iRosterStatus = da.getRosterStatus();

                                if ("".equals(iRosterStatus)) {
                                    continue;
                                }


                                String iRosterEnrolledTo = da.getRosterEnrolledTo();
                                Provider enrolledToProvider = providerDao.getProvider(iRosterEnrolledTo);

                                Date iRosterDate = da.getRosterDate();
                                Date iRosterTermination = da.getRosterTerminationDate();
                                String iRosterTerminationReason = da.getRosterTerminationReason();

                                String dRosterStatus = getRosterStatusDisplay(iRosterStatus);
                                String dRosterEnrolledTo = enrolledToProvider != null ? enrolledToProvider.getFormattedName() : "N/A";
                                String dRosterDate = DateUtils.formatDate(iRosterDate, request.getLocale());
                                String dRosterTerminationDate = iRosterTermination != null ? DateUtils.formatDate(iRosterTermination, request.getLocale()) : "";
                                String dRosterTerminationReason = iRosterTerminationReason != null && Util.rosterTermReasonProperties.getReasonByCode(iRosterTerminationReason) != null ? Util.rosterTermReasonProperties.getReasonByCode(iRosterTerminationReason) : "";

                                //all same?
                                if (dRosterStatus.equals(lastRosterStatusDisplay) && dRosterEnrolledTo.equals(lastRosterEnrolledToDisplay) && dRosterDate.equals(lastRosterDateDisplay) && dRosterTerminationDate.equals(lastRosterTerminationDateDisplay) && dRosterTerminationReason.equals(lastRosterTerminationReasonDisplay)) {
                                    continue;
                                }
                    %>
                    <%
                        if (!printedHeaders) {
                            printedHeaders = true;
                    %>
                    <tr>
                        <th>Date of Record</th>
                        <th>Status</th>
                        <th>Enrolled To</th>
                        <th>Date Rostered</th>
                        <th>Date Terminated</th>
                        <th>Termination Reason</th>
                    </tr>
                    <%
                        }
                    %>

                    <tr>
                        <td><%=DateUtils.formatDate(da.getLastUpdateDate(), request.getLocale())%>
                        </td>
                        <td><%=dRosterStatus %>
                        </td>
                        <td><%=dRosterEnrolledTo %>
                        </td>
                        <td><%=dRosterDate %>
                        </td>
                        <td><%=dRosterTerminationDate %>
                        </td>
                        <td><%=dRosterTerminationReason %>
                        </td>
                    </tr>
                    <%

                                lastRosterStatusDisplay = dRosterStatus;
                                lastRosterEnrolledToDisplay = dRosterEnrolledTo;
                                lastRosterDateDisplay = dRosterDate;
                                lastRosterTerminationDateDisplay = dRosterTerminationDate;
                                lastRosterTerminationReasonDisplay = dRosterTerminationReason;
                            }

                        }
                    %>


                    <% } else { %>
                    <table>
                        <tr>
                            <td>No Enrollment Data Found!</td>
                        </tr>
                    </table>
                    <% } %>

                </table>

                <br/>

                <input type="button" value="close" onClick="window.close()"/>
            </td>
        </tr>

    </table>
    </body>
</html>


<%!
    String getRosterStatusDisplay(String rosterStatus) {
        String rs = org.apache.commons.lang3.StringUtils.trimToNull(rosterStatus);
        if (rs != null) {
            if ("RO".equals(rs)) {
                return "ROSTERED";
            }
            if ("TE".equals(rs)) {
                return "TERMINATED";
            }
            if ("FS".equals(rs)) {
                return "FEE FOR SERVICE";
            }
            return rs;
        } else {
            return "";
        }
    }


%>



 