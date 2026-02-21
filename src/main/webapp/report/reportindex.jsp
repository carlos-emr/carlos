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
    reportindex.jsp - Report Index Page

    Purpose: Main entry point for CARLOS EMR reporting features.
             Displays available reports and provides navigation to each report tool.

    Features:
      - Day Sheet report with provider/date/time filtering
      - Demographic Report Tool
      - Prevention Reporting
      - Chronic Disease Management
      - Waiting List
      - Clinical Reports

    Parameters:
      Session: userrole, user, logged-in provider preference

    @since 2026-02-13
--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed2 = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed2 = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed2) {
        return;
    }
%>

<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderPreference" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%
    String country = request.getLocale().getCountry();

    ProviderPreference providerPreference = (ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE);
    String curUser_no = (String) session.getAttribute("user");
    String mygroupno = "";
    if (providerPreference != null) {
        mygroupno = providerPreference.getMyGroupNo();
    }
    mygroupno = StringUtils.trimToEmpty(mygroupno);
    String billingRegion = (io.github.carlos_emr.CarlosProperties.getInstance()).getProperty("billregion");
%>
<%@ page
        import="java.util.*, io.github.carlos_emr.*, java.sql.*, java.text.*, java.net.*"
        errorPage="/errorpage.jsp" %>
<jsp:useBean id="reportMainBean" class="io.github.carlos_emr.AppointmentMainBean"
             scope="session"/>
<% if (!reportMainBean.getBDoConfigure()) { %>
<%@ include file="reportMainBeanConn.jspf" %>
<% } %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%
    boolean isSiteAccessPrivacy = false;
    boolean isTeamAccessPrivacy = false;
    String provider_dboperation = "search_provider";
    String mygroup_dboperation = "search_group";
%>
<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName2$%>" rights="r" reverse="false">
    <%
        isSiteAccessPrivacy = true;
        provider_dboperation = "site_search_provider";
        mygroup_dboperation = "site_search_group";
    %>
</security:oscarSec>
<security:oscarSec objectName="_team_access_privacy" roleName="<%=roleName2$%>" rights="r" reverse="false">
    <%
        isTeamAccessPrivacy = true;
        provider_dboperation = "team_search_provider";
    %>

</security:oscarSec>

<!DOCTYPE html>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.title"/></title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <link href="<%= request.getContextPath() %>/library/bootstrap/5.0.2/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/searchBox.css">

        <link rel="stylesheet" type="text/css" media="all"
              href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/calendar-en.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>


        <script type="text/javascript">
            <!--
            function setfocus() {
                this.focus();
            }

            function popupPageNew(vheight, vwidth, varpage) {
                var page = "" + varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
                var popup = window.open(page, "demographicprofile", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                }
            }

            function go(r) {
                var s = document.getElementsByName("provider_no")[0].value;
                var u = document.getElementsByName("asdate")[0].value;
                var v = document.getElementsByName("aedate")[0].value;
                var y = document.getElementsByName("sTime")[0].value;
                var z = document.getElementsByName("eTime")[0].value;
                var ro = document.getElementById("rosteredOnly").checked;
                var x = 'reportdaysheet.jsp?dsmode=' + encodeURIComponent(r) + '&provider_no=' + encodeURIComponent(s) + '&sdate=' + encodeURIComponent(u) + '&edate=' + encodeURIComponent(v) + '&sTime=' + encodeURIComponent(y) + '&eTime=' + encodeURIComponent(z);
                var x2 = x + '&rosteredStatus=true';

                if (ro == true) {
                    popupPageNew(600, 750, x2);
                } else {
                    popupPageNew(600, 750, x);
                }
            }

            //-->
        </script>
    </head>
    <body onload="setfocus()">
    <%
        GregorianCalendar now = new GregorianCalendar();
        GregorianCalendar cal = (GregorianCalendar) now.clone();
        String today = now.get(Calendar.YEAR) + "-" + (now.get(Calendar.MONTH) + 1) + "-" + now.get(Calendar.DATE);
    %>
    <div class="container">
    <div class="searchBox">
    <div style="background:#f5f5f5; padding:8px 15px; border-bottom:1px solid #ddd; margin-bottom:10px;">
        <h4 style="margin:0; font-size:18px;">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" style="vertical-align:text-bottom">
                <path d="M1 2.5A1.5 1.5 0 0 1 2.5 1h3A1.5 1.5 0 0 1 7 2.5v3A1.5 1.5 0 0 1 5.5 7h-3A1.5 1.5 0 0 1 1 5.5zM2.5 2a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zm6.5.5A1.5 1.5 0 0 1 10.5 1h3A1.5 1.5 0 0 1 15 2.5v3A1.5 1.5 0 0 1 13.5 7h-3A1.5 1.5 0 0 1 9 5.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zM1 10.5A1.5 1.5 0 0 1 2.5 9h3A1.5 1.5 0 0 1 7 10.5v3A1.5 1.5 0 0 1 5.5 15h-3A1.5 1.5 0 0 1 1 13.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zm6.5.5A1.5 1.5 0 0 1 10.5 9h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-3A1.5 1.5 0 0 1 9 13.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5z"/>
            </svg>
            &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.msgTitle"/>
        </h4>
    </div>
    <form name='report'>
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <table class="table table-sm table-striped" id="reportsTbl" style="width:100%">
            <%int j = 1; %>
            <tr>
                <td width="2"><%=j%>
                    <%j++;%>
                </td>
                <td width="1"></td>
                <td width="300"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.formDaySheet"/></td>
                <td><select name="provider_no" class="form-select form-select-sm" style="width:auto;display:inline-block">
                    <%
                        ResultSet rsgroup = reportMainBean.queryResults(mygroup_dboperation);

                        while (rsgroup.next()) {
                            if (isTeamAccessPrivacy)
                                continue;    //skip mygroup display if user have TeamAccessPrivacy
                    %>
                    <option value="<%=Encode.forHtmlAttribute("_grp_"+rsgroup.getString("mygroup_no"))%>"
                            <%=mygroupno.equals(rsgroup.getString("mygroup_no")) ? "selected" : ""%>><%=Encode.forHtml("GRP: " + rsgroup.getString("mygroup_no"))%>
                    </option>
                    <%
                        }
                    %>
                    <%
                        rsgroup = reportMainBean.queryResults(provider_dboperation);
                        while (rsgroup.next()) {
                    %>
                    <option value="<%=Encode.forHtmlAttribute(rsgroup.getString("provider_no"))%>"
                            <%=curUser_no.equals(rsgroup.getString("provider_no")) ? "selected" : ""%>><%=Encode.forHtml(rsgroup.getString("last_name") + ", " + rsgroup.getString("first_name"))%>
                    </option>
                    <%
                        }
                    %>
                    <option value="*"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.formAllProviders"/></option>
                </select></td>
                <td></td>
                <td></td>
                <td></td>
            </tr>
            <tr>
                <td width="2"></td>
                <td width="1">&nbsp;</td>
                <td width="300">
                    <sup>*</sup><a HREF="#" ONCLICK="go('all')"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.btnAllAppt"/></a><br>&nbsp;&nbsp; <fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.chkRostered"/> <input type="checkbox" id="rosteredOnly" value="true">
                </td>
                <td><a HREF="#"
                       onClick="popupPage(310,430,'<%= request.getContextPath() %>/share/CalendarPopup.jsp?urlfrom=<%= request.getContextPath() %>/report/reportindex.jsp&year=<%=now.get(Calendar.YEAR)%>&month=<%=now.get(Calendar.MONTH)+1%>&param=<%=URLEncoder.encode("&formdatebox=document.getElementsByName('asdate')[0].value", StandardCharsets.UTF_8)%>')"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.formFrom"/></a> <input type='text' name="asdate"
                                                                       VALUE="<%=today%>" class="form-control form-control-sm" style="width:auto;display:inline-block"></td>
                <td><a HREF="#"
                       onClick="popupPage(310,430,'<%= request.getContextPath() %>/share/CalendarPopup.jsp?urlfrom=<%= request.getContextPath() %>/report/reportindex.jsp&year=<%=now.get(Calendar.YEAR)%>&month=<%=now.get(Calendar.MONTH)+1%>&param=<%=URLEncoder.encode("&formdatebox=document.getElementsByName('aedate')[0].value", StandardCharsets.UTF_8)%>')"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.formTo"/> </a> <input type='text' name="aedate"
                                                                      VALUE="<%=today%>" class="form-control form-control-sm" style="width:auto;display:inline-block"></td>
                <td><select name="sTime" class="form-select form-select-sm" style="width:auto;display:inline-block">
                    <%
                        for (int i = 0; i < 24; i++) {
                            String timeString = i < 12 && i >= 0 ? (i + " am") : ((i == 12 ? i : i - 12) + " pm");
                    %>
                    <option value="<%=""+i%>" <%=i == 8 ? "selected" : ""%>><%=timeString%>
                    </option>
                    <% } %>
                </select> - <select name="eTime" class="form-select form-select-sm" style="width:auto;display:inline-block">
                    <%
                        for (int i = 0; i < 24; i++) {
                            String timeString = i < 12 && i >= 0 ? (i + " am") : ((i == 12 ? i : i - 12) + " pm");
                    %>
                    <option value="<%=""+i%>" <%=i == 20 ? "selected" : ""%>><%=timeString%>
                    </option>
                    <% } %>
                </select></td>
                <td></td>
            </tr>

            <tr>
                <td width="2"><%=j%>
                    <%j++;%>
                </td>
                <td width="1"></td>
                <td width="300"><a
                        href="<%= request.getContextPath() %>/oscarReport/ReportDemographicReport.jsp" target="_blank"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.btnDemographicReportTool"/></a></td>
                <td></td>
                <td></td>
                <td></td>
                <td></td>
            </tr>

            <tr>
                <td width="2"><%=j%>
                    <%j++;%>
                </td>
                <td width="1"></td>
                <td width="300"><a
                        href="<%= request.getContextPath() %>/oscarPrevention/PreventionReporting.jsp" target="_blank"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.btnReport18n"/></a></td>
                <td></td>
                <td></td>
                <td></td>
                <td></td>
            </tr>

            <tr>
                <td width="2"><%=j%>
                    <%j++;%>
                </td>
                <td width="1"></td>
                <td width="300"><a
                        href="<%= request.getContextPath() %>/oscarReport/oscarMeasurements/SetupSelectCDMReport.do"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.chronicDiseaseManagement"/></a></td>
                <td></td>
                <td></td>
                <td></td>
            </tr>
            <tr>
                <td width="2"><%=j%>
                    <%j++;%>
                </td>
                <td width="1"></td>
                <td width="300"><a
                        href="<%= request.getContextPath() %>/oscarWaitingList/SetupDisplayWaitingList.do?waitingListId="><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.btnWaiting"/></a></td>
                <td></td>
                <td></td>
                <td></td>
            </tr>

            <tr>
                <td width="2"><%=j%><%j++;%></td>
                <td width="1"></td>
                <td width="300"><a href="ClinicalReports.jsp"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.btnClinicalReport"/> </a></td>
                <td></td>
                <td></td>
                <td></td>
            </tr>

        </table>
    </form>
    </div>
    </div>
    </body>
</html>
