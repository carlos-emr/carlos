<%--

    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

--%>
<%-- new billing report as its only slightly over 10 years old --%>
<!DOCTYPE html>
<%! boolean bMultisites = IsPropertiesOn.isMultisitesEnable(); %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String user_no = (String) session.getAttribute("user");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean isTeamBillingOnly = false;
%>
<security:oscarSec objectName="_team_billing_only" roleName="<%= roleName$ %>" rights="r" reverse="false">
    <% isTeamBillingOnly = true; %>
</security:oscarSec>

<%

    int nItems = 0;
    String strLimit1 = "0";
    String strLimit2 = "50";
    if (request.getParameter("limit1") != null) strLimit1 = request.getParameter("limit1");
    if (request.getParameter("limit2") != null) strLimit2 = request.getParameter("limit2");
    String providerview = request.getParameter("providerview") == null ? "all" : request.getParameter("providerview");
%>

<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.*, java.sql.*, ca.openosp.openo.login.*, ca.openosp.*, java.net.*" errorPage="/errorpage.jsp" %>
<%@ include file="/admin/dbconnection.jsp" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.ReportProvider" %>
<%@ page import="ca.openosp.openo.commn.dao.ReportProviderDao" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%@ page import="ca.openosp.openo.commn.dao.SiteDao" %>
<%@ page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ page import="ca.openosp.openo.commn.model.Site" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="ca.openosp.openo.login.DBHelp" %>
<%@ page import="ca.openosp.openo.commn.IsPropertiesOn" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    ReportProviderDao reportProviderDao = SpringUtils.getBean(ReportProviderDao.class);
%>
<%
    GregorianCalendar now = new GregorianCalendar();
    int curYear = now.get(Calendar.YEAR);
    int curMonth = (now.get(Calendar.MONTH) + 1);
    int curDay = now.get(Calendar.DAY_OF_MONTH);

    String xml_vdate = request.getParameter("xml_vdate") == null ? "" : request.getParameter("xml_vdate");
    String xml_appointment_date = request.getParameter("xml_appointment_date") == null ? "" : request.getParameter("xml_appointment_date");
%>

<%
    // action
    Vector vecHeader = new Vector();
    Vector vecValue = new Vector();
    Vector vecTotal = new Vector();
    Properties prop = null;
    DBHelp dbObj = new DBHelp();
    ResultSet rs = null;
    String sql = null;

    String action = request.getParameter("reportAction") == null ? "" : request.getParameter("reportAction");
    if ("unbilled".equals(action)) {
        vecHeader.add("SERVICE DATE");
        vecHeader.add("TIME");
        vecHeader.add("PATIENT");
        vecHeader.add("DESCRIPTION");
        vecHeader.add("COMMENTS");

        sql = "select * from appointment where provider_no='" + providerview + "' and appointment_date >='" + xml_vdate
                + "' and appointment_date<='" + xml_appointment_date
                + "' and (BINARY status NOT LIKE 'B%' AND BINARY status NOT LIKE 'C%' AND BINARY status NOT LIKE 'N%')"
                + " and demographic_no != 0 order by appointment_date , start_time ";

        rs = dbObj.searchDBRecord(sql);
        while (rs.next()) {
            if (bMultisites) {
                // skip record if location does not match the selected site, blank location always gets displayed for backward-compatibility
                String location = rs.getString("location");
                if (StringUtils.isNotBlank(location) && !location.equals(request.getParameter("site")))
                    continue;
            }

            prop = new Properties();
            prop.setProperty("SERVICE DATE", rs.getString("appointment_date"));
            prop.setProperty("TIME", rs.getString("start_time").substring(0, 5));
            prop.setProperty("PATIENT", rs.getString("name"));
            prop.setProperty("DESCRIPTION", rs.getString("reason"));
            String tempStr = "<a href=# onClick='popupPage(700,1000, \"billingOB.jsp?billForm="
                    + URLEncoder.encode(oscarVariables.getProperty("default_view"), StandardCharsets.UTF_8) + "&hotclick=&appointment_no="
                    + rs.getString("appointment_no") + "&demographic_name=" + URLEncoder.encode(rs.getString("name"), StandardCharsets.UTF_8)
                    + "&demographic_no=" + rs.getString("demographic_no") + "&user_no=" + rs.getString("provider_no")
                    + "&apptProvider_no=" + providerview + "&appointment_date=" + rs.getString("appointment_date")
                    + "&start_time=" + rs.getString("start_time") + "&bNewForm=1\"); return false;'>Bill ";
            prop.setProperty("COMMENTS", tempStr);
            vecValue.add(prop);
        }

    }

    if ("billed".equals(action)) {
        vecHeader.add("SERVICE DATE");
        vecHeader.add("TIME");
        vecHeader.add("PATIENT");
        vecHeader.add("DESCRIPTION");
        vecHeader.add("ACCOUNT");
        sql = "select * from billing_on_cheader1 where provider_no='" + providerview + "' and billing_date >='" + xml_vdate
                + "' and billing_date<='" + xml_appointment_date + "' and (status<>'D' and status<>'S' and status<>'B')"
                + " order by billing_date , billing_time ";
        rs = dbObj.searchDBRecord(sql);
        while (rs.next()) {
            if (bMultisites) {
                // skip record if clinic is not match the selected site, blank clinic always gets displayed for backward compatible
                String clinic = rs.getString("clinic");
                if (StringUtils.isNotBlank(clinic) && !clinic.equals(request.getParameter("site")))
                    continue;
            }

            prop = new Properties();
            prop.setProperty("SERVICE DATE", rs.getString("billing_date"));
            prop.setProperty("TIME", rs.getString("billing_time").substring(0, 5));
            prop.setProperty("PATIENT", rs.getString("demographic_name"));

            String apptDoctorNo = rs.getString("apptProvider_no");
            String userno = rs.getString("provider_no");
            String reason = rs.getString("status");
            String note = "";
            if (apptDoctorNo.compareTo("none") == 0) {
                note = "No Appt / INR";
            } else {
                if (apptDoctorNo.compareTo(userno) == 0) {
                    note = "With Appt. Doctor";
                } else {
                    note = "Unmatched Appt. Doctor";
                }
            }
            if (reason.compareTo("N") == 0) reason = "Do Not Bill ";
            else if (reason.compareTo("O") == 0) reason = "Bill OHIP ";
            else if (reason.compareTo("W") == 0) reason = "Bill WSIB ";
            else if (reason.compareTo("H") == 0) reason = "Capitated Bill ";
            else if (reason.compareTo("P") == 0) reason = "Bill Patient";

            prop.setProperty("DESCRIPTION", reason + "(" + note + ")");
            String tempStr = "<a href=# onClick='popupPage(700,720, \"" + request.getContextPath() + "/billing/CA/ON/billingCorrection.jsp?billing_no="
                    + rs.getString("id") + "&dboperation=search_bill&hotclick=0\"); return false;' title='"
                    + reason + "'>" + rs.getString("id") + "</a>";
            prop.setProperty("ACCOUNT", tempStr);
            vecValue.add(prop);
        }


    }

    if ("paid".equals(action)) {
        vecHeader.add("No");
        vecHeader.add("Billing No");
        vecHeader.add("HIN");
        vecHeader.add("Claim");
        vecHeader.add("Paid");
        vecHeader.add("Billing Date");
        //vecHeader.add("Time");
        float fTotalClaim = 0.00f;
        float fTotalPaid = 0.00f;

        // get billing no in the date range
        Vector vecBillingNo = new Vector();
        Properties propTotal = new Properties();
        sql = "select billing_no,total from billing where provider_no='" + providerview
                + "' and billing_date>='" + xml_vdate + "' and billing_date<='" + xml_appointment_date
                + "' and status ='S' order by billing_date, billing_time";

        // change 'S' to 'O' for testing

        rs = dbObj.searchDBRecord(sql);
        while (rs.next()) {
            vecBillingNo.add("" + rs.getInt("billing_no"));
            propTotal.setProperty("" + rs.getInt("billing_no"), rs.getString("total"));
        }
        rs.close();

        // get detail ra for the billing no
        String tempStr = "";
        for (int i = 0; i < vecBillingNo.size(); i++) {
            tempStr += ("".equals(tempStr) ? "" : ",") + (String) vecBillingNo.get(i);
        }
        tempStr = "".equals(tempStr) ? "-1" : tempStr;

        // change tempStr to '75980, 75982, 75990' for testing
        //tempStr = "75980, 75982, 75990,79571,79066";

        sql = "select billing_no, amountclaim, amountpay, hin, service_date from radetail where billing_no in ("
                + tempStr + ") and raheader_no !=0 order by billing_no, radetail_no";
        rs = dbObj.searchDBRecord(sql);
        String sAmountclaim = "", sAmountpay = "", hin = "";
        int nNo = 0;
        while (rs.next()) {
            if (!tempStr.equals("" + rs.getInt("billing_no"))) { // new billing no
                prop = new Properties();
                // reset something
                tempStr = "" + rs.getInt("billing_no");
                nNo++;
                sAmountclaim = rs.getString("amountclaim");
                sAmountpay = rs.getString("amountpay");
                String strT = "<a href=# onClick='popupPage(700,720, \"" + request.getContextPath() + "/billing/CA/BC/billingView.do?billing_no="
                        + rs.getString("billing_no") + "&dboperation=search_bill&hotclick=0\"); return false;' >"
                        + rs.getString("billing_no") + "</a>";
                prop.setProperty("No", "" + nNo);
                prop.setProperty("Billing No", strT);
                prop.setProperty("HIN", rs.getString("hin"));
                prop.setProperty("Claim", sAmountclaim);
                prop.setProperty("Paid", sAmountpay);
                prop.setProperty("Billing Date", getFormatDateStr(rs.getString("service_date")));
                vecValue.add(prop);

                fTotalClaim += Float.parseFloat(rs.getString("amountclaim"));
                fTotalPaid += Float.parseFloat(rs.getString("amountpay"));
            } else { // old billing no
                prop = new Properties();
                //sAmountclaim = rs.getString("amountclaim");
                //sAmountpay = rs.getString("amountpay");
                float fAmountclaim = Float.parseFloat(sAmountclaim);
                fAmountclaim = fAmountclaim + Float.parseFloat(rs.getString("amountclaim"));
                sAmountclaim = "" + Math.round(fAmountclaim * 100) / 100.00;
                float fAmountpay = Float.parseFloat(sAmountpay);
                fAmountpay = fAmountpay + Float.parseFloat(rs.getString("amountpay"));
                sAmountpay = "" + Math.round(fAmountpay * 100) / 100.00;
                //hin = rs.getString("hin");
                String strT = "<a href=# onClick='popupPage(700,720, \"" + request.getContextPath() + "/billing/CA/BC/billingView.do?billing_no="
                        + rs.getString("billing_no") + "&dboperation=search_bill&hotclick=0\"); return false;' >"
                        + rs.getString("billing_no") + "</a>";
                prop.setProperty("No", "" + nNo);
                prop.setProperty("Billing No", strT);
                prop.setProperty("HIN", rs.getString("hin"));
                // repeated records
                //prop.setProperty("Claim", sAmountclaim);
                prop.setProperty("Claim", propTotal.getProperty(tempStr));
                prop.setProperty("Paid", sAmountpay);
                prop.setProperty("Billing Date", getFormatDateStr(rs.getString("service_date")));
                vecValue.remove(vecValue.size() - 1);
                vecValue.add(prop);

                fTotalClaim += Float.parseFloat(rs.getString("amountclaim"));
                fTotalPaid += Float.parseFloat(rs.getString("amountpay"));
            }
        }
        rs.close();
        vecTotal.add("Total");
        vecTotal.add("");
        vecTotal.add("");
        vecTotal.add("" + Math.round(fTotalClaim * 100) / 100.00);
        vecTotal.add("" + Math.round(fTotalPaid * 100) / 100.00);
        vecTotal.add("");
    }

    if ("unpaid".equals(action)) {
        vecHeader.add("No");
        vecHeader.add("Billing No");
        vecHeader.add("Patient");
        vecHeader.add("Claim");
        vecHeader.add("Description");
        vecHeader.add("Service Date");
        vecHeader.add("Time");
        float fTotalClaim = 0.00f;
        String sAmountclaim = "";

        sql = "select * from billing where provider_no='" + providerview + "' and billing_date >='" + xml_vdate
                + "' and billing_date<='" + xml_appointment_date + "' and (status<>'D' and status<>'S')"
                + " order by billing_date , billing_time ";
        int nNo = 0;
        rs = dbObj.searchDBRecord(sql);
        while (rs.next()) {
            prop = new Properties();
            nNo++;
            prop.setProperty("No", "" + nNo);
            prop.setProperty("Service Date", rs.getString("billing_date"));
            prop.setProperty("Time", rs.getString("billing_time").substring(0, 5));
            prop.setProperty("Patient", rs.getString("demographic_name"));

            String apptDoctorNo = rs.getString("apptProvider_no");
            String userno = rs.getString("provider_no");
            String reason = rs.getString("status");
            String note = "";
            if (apptDoctorNo.compareTo("none") == 0) {
                note = "No Appt / INR";
            } else {
                if (apptDoctorNo.compareTo(userno) == 0) {
                    note = "With Appt. Doctor";
                } else {
                    note = "Unmatched Appt. Doctor";
                }
            }
            if (reason.compareTo("N") == 0) reason = "Do Not Bill ";
            else if (reason.compareTo("O") == 0) reason = "Bill OHIP ";
            else if (reason.compareTo("W") == 0) reason = "Bill WSIB ";
            else if (reason.compareTo("H") == 0) reason = "Capitated Bill ";
            else if (reason.compareTo("P") == 0) reason = "Bill Patient";
            else if (reason.compareTo("B") == 0) reason = "Sent OHIP";

            prop.setProperty("Description", reason + "(" + note + ")");
            String tempStr = "<a href=# onClick='popupPage(700,720, \""+ request.getContextPath() + "/billing/CA/BC/billingView.do?billing_no="
                    + rs.getString("billing_no") + "&dboperation=search_bill&hotclick=0\"); return false;' title='"
                    + reason + "'>" + rs.getString("billing_no") + "</a>";
            prop.setProperty("Billing No", tempStr);
            sAmountclaim = rs.getString("total");
            prop.setProperty("Claim", sAmountclaim);
            fTotalClaim += Float.parseFloat(rs.getString("total"));

            vecValue.add(prop);
        }
        rs.close();
        vecTotal.add("Total");
        vecTotal.add("");
        vecTotal.add("");
        vecTotal.add("" + Math.round(fTotalClaim * 100) / 100.00);
        vecTotal.add("");
        vecTotal.add("");
        vecTotal.add("");
    }

%>


<html>
<head>

    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <link href="<%=request.getContextPath() %>/css/datepicker.css" rel="stylesheet">
    <link href="<%=request.getContextPath() %>/css/font-awesome.min.css" rel="stylesheet">


    <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/DT_bootstrap.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/css/bootstrap-responsive.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/library/DataTables-1.10.12/media/css/jquery.dataTables.min.css"
          rel="stylesheet">

    <script src="${pageContext.request.contextPath}/share/javascript/Oscar.js"></script>
    <script src="${pageContext.request.contextPath}/js/bootstrap.min.2.js"></script>
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/DataTables/datatables.min.js"></script><!-- 1.13.4 -->

    <script src="${pageContext.request.contextPath}/js/global.js"></script>

    <title>Bills - ONTARIO BILLING REPORT</title>


    <script>
        <!--

        function selectprovider(s) {
            if (self.location.href.lastIndexOf("&providerview=") > 0) a = self.location.href.substring(0, self.location.href.lastIndexOf("&providerview="));
            else a = self.location.href;
            self.location.href = a + "&providerview=" + s.options[s.selectedIndex].value;
        }

        function openBrWindow(theURL, winName, features) { //v2.0
            window.open(theURL, winName, features);
        }

        function refresh() {
            var u = self.location.href;
            if (u.lastIndexOf("view=1") > 0) {
                self.location.href = u.substring(0, u.lastIndexOf("view=1")) + "view=0" + u.substring(eval(u.lastIndexOf("view=1") + 6));
            } else {
                history.go(0);
            }
        }

        function calToday(field) {
            var calDate = new Date();
            varMonth = calDate.getMonth() + 1;
            varMonth = varMonth > 9 ? varMonth : ("0" + varMonth);
            varDate = calDate.getDate() > 9 ? calDate.getDate() : ("0" + calDate.getDate());
            field.value = calDate.getFullYear() + '/' + (varMonth) + '/' + varDate;
        }

        //-->
    </script>
</head>

<body>


<table style="width:100%">
    <tr>
        <td style="width:1%"></td>
        <td style="width:80%; text-align:left;">
            <H4><a style="color:black;"
                   href="billingReportCenter.jsp">OSCARbilling</a></H4>
        </td>
        <td style="text-align:right;">
            <i class=" icon-question-sign"></i>
            <a href="javascript:void(0)"
               onClick="popupPage(600,750,'<%=(OscarProperties.getInstance()).getProperty("HELP_SEARCH_URL")%>'+'OscarBilling+Billing')"><fmt:setBundle basename="oscarResources"/><fmt:message key="app.top1"/></a>
            <i class=" icon-info-sign" style="margin-left:10px;"></i>
            <a href="javascript:void(0)"
               onClick="window.open('<%=request.getContextPath()%>/oscarEncounter/About.jsp','About OSCAR','scrollbars=1,resizable=1,width=800,height=600,left=0,top=0')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a>
        </td>
        <td style="width:1%"></td>
    </tr>
</table>
<div class="well">
    <form name="serviceform" method="post" action="billingONReport.jsp">
        <table style="width:100%;">

            <tr class="form-inline">
                <td>
                    <label class="radio inline">
                        <input
                                type="radio" name="reportAction" value="unbilled"
                            <%="unbilled".equals(action)? "checked" : "" %>>Unbilled
                    </label>
                    <label class="radio inline">
                        <input
                                type="radio" name="reportAction" value="billed"
                            <%="billed".equals(action)? "checked" : "" %>>Billed
                    </label>
                    <!-- broken
          <label class="radio inline">
                <input
                type="radio" name="reportAction" value="paid" <%="paid".equals(action)? "checked" : "" %>>Paid
            </label>
            <label class="radio inline">
                <input type="radio" name="reportAction" value="unpaid" <%="unpaid".equals(action)? "checked" : "" %>>Unpaid
            </label>
end broken -->

                    <br>&nbsp;Provider
                    <% if (bMultisites) { // multisite start ==========================================
                        SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
                        List<Site> sites = siteDao.getActiveSitesByProviderNo(user_no);
                        // now get all report providers

                        HashSet<String> reporters = new HashSet<String>();
                        for (Object[] res : reportProviderDao.search_reportprovider("billingreport")) {
                            ReportProvider rp = (ReportProvider) res[0];
                            Provider p = (Provider) res[1];
                            reporters.add(p.getProviderNo());
                        }

                    %>
                    <script>
                        var _providers = [];
                        <%	for (int i=0; i<sites.size(); i++) {
                            Set<Provider> siteProviders = sites.get(i).getProviders();
                            List<Provider>  siteProvidersList = new ArrayList<Provider> (siteProviders);
                            Collections.sort(siteProvidersList,(new Provider()).ComparatorName());%>
                        _providers["<%= sites.get(i).getName() %>"] = "<% Iterator<Provider> iter = siteProvidersList.iterator();
	while (iter.hasNext()) {
		Provider p=iter.next();
		if (reporters.contains(p.getProviderNo())) {
	%><option value='<%= p.getProviderNo() %>'><%= p.getLastName() %>, <%= p.getFirstName() %></option><% }} %>";
                        <% } %>

                        function changeSite(sel) {
                            sel.form.providerview.innerHTML = sel.value == "none" ? "" : _providers[sel.value];
                            sel.style.backgroundColor = sel.options[sel.selectedIndex].style.backgroundColor;
                        }
                    </script>
                    <select id="site" name="site" onchange="changeSite(this)">
                        <option value="none" style="background-color:white">---select clinic---</option>
                        <%
                            for (int i = 0; i < sites.size(); i++) {
                        %>
                        <option value="<%= sites.get(i).getName() %>"
                                style="background-color:<%= sites.get(i).getBgColor() %>"
                                <%=sites.get(i).getName().toString().equals(request.getParameter("site")) ? "selected" : "" %>><%= sites.get(i).getName() %>
                        </option>
                        <% } %>
                    </select>
                    <select id="providerview" name="providerview" style="width:140px"></select>
                    <% if (request.getParameter("providerview") != null) { %>
                    <script>
                        changeSite(document.getElementById("site"));
                        document.getElementById("providerview").value = '<%=request.getParameter("providerview")%>';
                    </script>
                    <% } // multisite end ==========================================
                    } else {
                    %>
                    <select
                            name="providerview">
                        <%
                            String proFirst = "";
                            String proLast = "";
                            String proOHIP = "";
                            String specialty_code;
                            String billinggroup_no;
                            int Count = 0;


                            for (Object[] res : reportProviderDao.search_reportprovider("billingreport")) {
                                ReportProvider rp = (ReportProvider) res[0];
                                Provider p = (Provider) res[1];
                                proFirst = p.getFirstName();
                                proLast = p.getLastName();
                                proOHIP = p.getProviderNo();


                        %>
                        <option value="<%=proOHIP%>"
                                <%=providerview.equals(proOHIP) ? "selected" : ""%>><%=proLast%>,
                            <%=proFirst%>
                        </option>
                        <%
                            }
                        %>
                    </select>
                    <% } %>


                    <label class="date">
                        From:
                        <input class="input-medium" style="height:30px;"
                               type="date" name="xml_vdate" id="xml_vdate"
                               value="<%=xml_vdate%>">
                    </label>
                    <label class="date">
                        To:
                        <input class="input-medium" style="height:30px;"
                               type="date" name="xml_appointment_date" id="xml_appointment_date"
                               value="<%=xml_appointment_date%>">
                    </label>
                </td>
                <td style="text-align:right"><input type="submit" name="Submit" class="btn btn-primary"
                                                    value="Create Report"></td>
            </tr>
            <tr>
                <td colspan="2"><a href=#
                                   onClick="popupPage(700,720,'<%= request.getContextPath() %>/oscarReport/manageProvider.jsp?action=billingreport')"
                                   class="btn btn-link">
                    Manage Provider List</a></td>
            </tr>
        </table>
    </form>
    <table id="reportTbl" style="width:100%" class="table table-condensed table-striped table-hover">
        <thead>
        <tr>
                <% for (int i=0; i<vecHeader.size(); i++) {%>
            <th><%=vecHeader.get(i) %>
            </th>
                <% } %>
        </thead>
        <tbody>
        <% for (int i = 0; i < vecValue.size(); i++) {%>

        <tr>
            <% for (int j = 0; j < vecHeader.size(); j++) {
                prop = (Properties) vecValue.get(i);
            %>
            <td style="text-align:center;"><%=prop.getProperty((String) vecHeader.get(j), "&nbsp;") %>&nbsp;</td>
            <% } %>
        </tr>
        <% } %>

        <% if (vecTotal.size() > 0) { %>
        <tr>
            <% for (int i = 0; i < vecTotal.size(); i++) {%>
            <th><%=vecTotal.get(i) %>&nbsp;</th>
            <% } %>
        </tr>
        <% } %>
        </tbody>
    </table>

</div> <!-- well end -->

<table style="width:100%">
    <tr>
        <td><a href=# onClick="javascript:history.go(-1);return false;" class="btn btn-link">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnCancel"/></a></td>
        <td style="text-align:right;"><a href="" onClick="self.close();" class="btn btn-link">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnExit"/></a></td>
    </tr>
</table>
<script>
    $('#reportTbl').DataTable({
        "order": [],
        "language": {
            "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
        }
    });
</script>

</html>
<%!
    String getFormatDateStr(String str) {
        String ret = str;
        if (str.length() == 8) {
            ret = str.substring(0, 4) + "/" + str.substring(4, 6) + "/" + str.substring(6);
        }
        return ret;
    }
%>