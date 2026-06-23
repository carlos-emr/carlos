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
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed2) {
        return;
    }
    boolean showScheduleNav = "1".equals(request.getParameter("scheduleNav"));
%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page import="java.util.Calendar" %>
<%@ page import="java.util.GregorianCalendar" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderPreference" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String country = request.getLocale().getCountry();
    CarlosProperties carlosVariables = CarlosProperties.getInstance();
    String prov = (carlosVariables.getProperty("billregion", "")).trim().toUpperCase();

    LoggedInInfo loggedInInfo1 = LoggedInInfo.getLoggedInInfoFromSession(request);
    ProviderPreference providerPreference = (ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE);
    String curUser_no = (String) session.getAttribute("user");
    String mygroupno = "";
    if (providerPreference != null) {
        mygroupno = providerPreference.getMyGroupNo();
    }
    mygroupno = StringUtils.trimToEmpty(mygroupno);
%>

<jsp:useBean id="reportMainBean" class="io.github.carlos_emr.AppointmentMainBean"
             scope="session"/>
<% if (!reportMainBean.getBDoConfigure()) { %>
<%@ include file="reportMainBeanConn.jspf" %>
<% } %>



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

<c:set var="flatpickrLanguage" value="${pageContext.request.locale.language}"/>

<!DOCTYPE html>
<html lang="${flatpickrLanguage}">
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
        <% if (showScheduleNav) { %>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/css/topnav.css">
        <% } %>
        <title><fmt:message key="report.reportindex.title"/></title>

        <!-- Flatpickr -->
        <script type="text/javascript" src="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.js"></script>
        <c:if test="${flatpickrLanguage != 'en'}">
        <script type="text/javascript" src="${pageContext.request.contextPath}/library/flatpickr/l10n/${carlos:forHtmlAttribute(flatpickrLanguage)}.js"></script>
        </c:if>
        <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.css">

        <style>
          .date-inline-group {
            display: flex;
            align-items: center;
            gap: 0.75rem;
          }
          .date-inline-group .form-control {
            width: 140px; /* compact width close to date length */
          }
          /* Keep the date input and its calendar trigger button on the same line.
             Bootstrap 5 .input-group defaults to flex-wrap: wrap, which causes the
             trigger button to drop below the input when the input-group is sized
             to its content (width: auto) next to a fixed-width .form-control. */
          .date-inline-group .input-group {
            flex-wrap: nowrap;
          }
        </style>

        <script>
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
                var x = '<%= request.getContextPath() %>/report/ViewReportdaysheet?dsmode=' + encodeURIComponent(r) + '&provider_no=' + encodeURIComponent(s) + '&sdate=' + encodeURIComponent(u) + '&edate=' + encodeURIComponent(v) + '&sTime=' + encodeURIComponent(y) + '&eTime=' + encodeURIComponent(z);
                var x2 = x + '&rosteredStatus=true';

                if (ro == true) {
                    popupPageNew(600, 750, x2);
                } else {
                    popupPageNew(600, 750, x);
                }
            }

            //-->
        </script>

        <script>
          document.addEventListener("DOMContentLoaded", function () {
            const localeCode = "${carlos:forJavaScript(flatpickrLanguage)}";
            const fromPickerOptions = {
              dateFormat: "Y-m-d",
              allowInput: true,
              onChange: syncFrom,
              onClose: syncFrom
            };
            const toPickerOptions = {
              dateFormat: "Y-m-d",
              allowInput: true,
              onChange: syncTo,
              onClose: syncTo
            };

            if (localeCode !== "en") {
              fromPickerOptions.locale = localeCode;
              toPickerOptions.locale = localeCode;
            }

            const fromPicker = flatpickr("#asdate", fromPickerOptions);
            const toPicker = flatpickr("#aedate", toPickerOptions);
        
            function syncFrom(selectedDates, dateStr, instance) {
              const fromDate = instance.selectedDates[0];
              const toDate = toPicker.selectedDates[0];
        
              if (fromDate) {
                toPicker.set("minDate", fromDate);
        
                // auto-correct if To < From
                if (toDate && toDate < fromDate) {
                  toPicker.setDate(fromDate, true);
                }
              } else {
                toPicker.set("minDate", null);
              }
            }
        
            function syncTo(selectedDates, dateStr, instance) {
              const toDate = instance.selectedDates[0];
              const fromDate = fromPicker.selectedDates[0];
        
              if (toDate) {
                fromPicker.set("maxDate", toDate);
        
                // auto-correct if From > To
                if (fromDate && fromDate > toDate) {
                  fromPicker.setDate(toDate, true);
                }
              } else {
                fromPicker.set("maxDate", null);
              }
            }
        
            // Button triggers
            document.getElementById("btn-asdate").addEventListener("click", function () {
              fromPicker.open();
            });
        
            document.getElementById("btn-aedate").addEventListener("click", function () {
              toPicker.open();
            });
          });
        </script>

    </head>
    <body onload="setfocus()">
    <% if (showScheduleNav) { %>
        <jsp:include page="/WEB-INF/jsp/provider/mainMenu.jsp"/>
    <% } %>
    <%
        GregorianCalendar now = new GregorianCalendar();
        GregorianCalendar cal = (GregorianCalendar) now.clone();
        String today = now.get(Calendar.YEAR) + "-" + (now.get(Calendar.MONTH) + 1) + "-" + now.get(Calendar.DATE);
    %>
    <div class="container-fluid carlos-content-shell">
    <div class="searchBox">
    <div class="page-header-bar">
        <h4 class="page-header-title">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" class="page-header-icon">
                <path d="M1 2.5A1.5 1.5 0 0 1 2.5 1h3A1.5 1.5 0 0 1 7 2.5v3A1.5 1.5 0 0 1 5.5 7h-3A1.5 1.5 0 0 1 1 5.5zM2.5 2a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zm6.5.5A1.5 1.5 0 0 1 10.5 1h3A1.5 1.5 0 0 1 15 2.5v3A1.5 1.5 0 0 1 13.5 7h-3A1.5 1.5 0 0 1 9 5.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zM1 10.5A1.5 1.5 0 0 1 2.5 9h3A1.5 1.5 0 0 1 7 10.5v3A1.5 1.5 0 0 1 5.5 15h-3A1.5 1.5 0 0 1 1 13.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zm6.5.5A1.5 1.5 0 0 1 10.5 9h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-3A1.5 1.5 0 0 1 9 13.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5z"/>
            </svg>
            &nbsp;<fmt:message key="report.reportindex.msgTitle"/>
        </h4>
    </div>
    <form name='report'>
        <table class="table table-sm table-striped" id="reportsTbl" style="width:100%">
            <%int j = 1; %>
            <tr>
                <td style="width: 30px;"><%=j%>
                    <%j++;%>
                </td>
                <td style="width: 10px;"></td>
                <td  style="width: 300px;"><fmt:message key="report.reportindex.formDaySheet"/></td>
                <td><select name="provider_no" class="form-select form-select-sm" style="width:auto;display:inline-block">
                    <%
                        ResultSet rsgroup = reportMainBean.queryResults(mygroup_dboperation);

                        while (rsgroup.next()) {
                            if (isTeamAccessPrivacy)
                                continue;    //skip mygroup display if user have TeamAccessPrivacy
                    %>
                    <option value="<carlos:encode value='<%= "_grp_"+rsgroup.getString("mygroup_no") %>' context="htmlAttribute"/>"
                            <%=mygroupno.equals(rsgroup.getString("mygroup_no")) ? "selected" : ""%>><fmt:message key="provider.appointmentprovideradminmonth.formGRP"/>:&nbsp;<carlos:encode value='<%= rsgroup.getString("mygroup_no") %>' context="html"/>
                    </option>
                    <%
                        }
                    %>
                    <%
                        rsgroup = reportMainBean.queryResults(provider_dboperation);
                        while (rsgroup.next()) {
                    %>
                    <option value="<carlos:encode value='<%= rsgroup.getString("provider_no") %>' context="htmlAttribute"/>"
                            <%=curUser_no.equals(rsgroup.getString("provider_no")) ? "selected" : ""%>><carlos:encode value='<%= rsgroup.getString("last_name") + ", " + rsgroup.getString("first_name") %>' context="html"/>
                    </option>
                    <%
                        }
                    %>
                    <option value="*"><fmt:message key="report.reportindex.formAllProviders"/></option>
                </select></td>
                <td></td>
                <td></td>
                <td></td>
            </tr>
            <tr>
                <td style="width: 30px;"></td>
                <td style="width: 0px;">&nbsp;</td>
                <td style="width: 300px;">
                    <sup>*</sup><a HREF="#" ONCLICK="go('all')"><fmt:message key="report.reportindex.btnAllAppt"/></a><br>&nbsp;&nbsp; <fmt:message key="report.reportindex.chkRostered"/> <input type="checkbox" id="rosteredOnly" value="true">
                </td>
                <td colspan="2">
                  <div class="date-inline-group">
                    <!-- From -->
                    <label for="asdate" class="mb-0"><fmt:message key="report.reportindex.formFrom"/></label>
                    <div class="input-group input-group-sm" style="width: auto;">
                      <input type="text" id="asdate" name="asdate" class="form-control" value="<%=today%>">
                      <button class="btn btn-outline-secondary" type="button" id="btn-asdate">
                        <i class="fa-solid fa-calendar-day"></i>
                      </button>
                    </div>
                  
                    <!-- To -->
                    <label for="aedate" class="mb-0"><fmt:message key="report.reportindex.formTo"/></label>
                    <div class="input-group input-group-sm" style="width: auto;">
                      <input type="text" id="aedate" name="aedate" class="form-control" value="<%=today%>">
                      <button class="btn btn-outline-secondary" type="button" id="btn-aedate">
                        <i class="fa-solid fa-calendar-day"></i>
                      </button>
                    </div>
                  </div>
                </td>
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
                <td style="width: 30px;"><%=j%>
                    <%j++;%>
                </td>
                <td style="width: 10px;"></td>
                <td style="width: 300px;"><a
                        href="<%= request.getContextPath() %>/oscarReport/ViewReportDemographicReport" target="_blank"><fmt:message key="report.reportindex.btnDemographicReportTool"/></a></td>
                <td></td>
                <td></td>
                <td></td>
                <td></td>
            </tr>

            <tr>
                <td style="width: 30px;"><%=j%>
                    <%j++;%>
                </td>
                <td style="width: 10px;"></td>
                <td style="width: 300px;"><a
                        href="<%= request.getContextPath() %>/prevention/PreventionReport" target="_blank"><fmt:message key="report.reportindex.btnReport18n"/></a></td>
                <td></td>
                <td></td>
                <td></td>
                <td></td>
            </tr>
            <security:oscarSec roleName="<%=roleName2$%>" objectName="_admin,_billing" rights="r" reverse="<%=false%>">
            <% if (StringUtils.isNotBlank(prov)) { %>
            <tr>
                <td style="width: 30px;"><%=j%>
                    <%j++;%>
                </td>
                <td style="width: 10px;"></td>
                <td style="width: 300px;">
                  <a href="<%= request.getContextPath() %>/billing/CA/<%=prov%>/ViewBillingReportCenter?displaymode=billreport&amp;providerview=<%=URLEncoder.encode(loggedInInfo1.getLoggedInProviderNo(), StandardCharsets.UTF_8)%>" target="_blank"><fmt:message key="global.genBillReport"/></a></td>
                <td></td>
                <td></td>
                <td></td>
                <td></td>
            </tr>
            <% } %>
            </security:oscarSec>
        </table>
    </form>
    </div>
    </div>
    </body>
</html>
