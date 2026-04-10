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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

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
<%@ page import="java.util.*, java.sql.*, io.github.carlos_emr.carlos.login.*, io.github.carlos_emr.*, java.net.*" errorPage="/errorpage.jsp" %>
<%@ include file="/admin/dbconnection.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ReportProvider" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ReportProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@ page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.login.DBHelp" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

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

        sql = "select * from appointment where provider_no=? and appointment_date >=?"
                + " and appointment_date<=?"
                + " and (BINARY status NOT LIKE 'B%' AND BINARY status NOT LIKE 'C%' AND BINARY status NOT LIKE 'N%')"
                + " and demographic_no != 0 order by appointment_date , start_time ";

        rs = dbObj.searchDBRecord(sql, providerview, xml_vdate, xml_appointment_date);
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
            prop.setProperty("PATIENT", Encode.forHtml(rs.getString("name")));
            prop.setProperty("DESCRIPTION", Encode.forHtml(rs.getString("reason")));
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
        sql = "select * from billing_on_cheader1 where provider_no=? and billing_date >=?"
                + " and billing_date<=? and (status<>'D' and status<>'S' and status<>'B')"
                + " order by billing_date , billing_time ";
        rs = dbObj.searchDBRecord(sql, providerview, xml_vdate, xml_appointment_date);
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
            prop.setProperty("PATIENT", Encode.forHtml(rs.getString("demographic_name")));

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

            prop.setProperty("DESCRIPTION", Encode.forHtml(reason + "(" + note + ")"));
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
        sql = "select billing_no,total from billing where provider_no=?"
                + " and billing_date>=? and billing_date<=?"
                + " and status ='S' order by billing_date, billing_time";

        // change 'S' to 'O' for testing

        rs = dbObj.searchDBRecord(sql, providerview, xml_vdate, xml_appointment_date);
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
                prop.setProperty("HIN", Encode.forHtml(rs.getString("hin")));
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
                prop.setProperty("HIN", Encode.forHtml(rs.getString("hin")));
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

        sql = "select * from billing where provider_no=? and billing_date >=?"
                + " and billing_date<=? and (status<>'D' and status<>'S')"
                + " order by billing_date , billing_time ";
        int nNo = 0;
        rs = dbObj.searchDBRecord(sql, providerview, xml_vdate, xml_appointment_date);
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
    <%@ include file="/includes/global-head.jspf" %>
    <title>Ontario Billing Report</title>

    <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css" rel="stylesheet">

    <script src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap5.min.js"></script>

    <script>
        function selectprovider(s) {
            var a;
            if (self.location.href.lastIndexOf("&providerview=") > 0) a = self.location.href.substring(0, self.location.href.lastIndexOf("&providerview="));
            else a = self.location.href;
            self.location.href = a + "&providerview=" + s.options[s.selectedIndex].value;
        }

        function openBrWindow(theURL, winName, features) {
            window.open(theURL, winName, features);
        }

        function refresh() {
            var u = self.location.href;
            if (u.lastIndexOf("view=1") > 0) {
                var idx = u.lastIndexOf("view=1");
                self.location.href = u.substring(0, idx) + "view=0" + u.substring(idx + 6);
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
    </script>

    <style type="text/css" media="print">
        .searchBox { display: none; }
    </style>
</head>

<body>
<div class="container">
<div class="searchBox">

    <div class="page-header-bar">
        <h4 class="page-header-title">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" class="page-header-icon">
                <path d="M1.92.506a.5.5 0 0 1 .434.14L3 1.293l.646-.647a.5.5 0 0 1 .708 0L5 1.293l.646-.647a.5.5 0 0 1 .708 0L7 1.293l.646-.647a.5.5 0 0 1 .708 0L9 1.293l.646-.647a.5.5 0 0 1 .708 0l.646.647.646-.647a.5.5 0 0 1 .708 0l.646.647.646-.647a.5.5 0 0 1 .801.13l.5 1A.5.5 0 0 1 15 2v12a.5.5 0 0 1-.053.224l-.5 1a.5.5 0 0 1-.8.13L13 14.707l-.646.647a.5.5 0 0 1-.708 0L11 14.707l-.646.647a.5.5 0 0 1-.708 0L9 14.707l-.646.647a.5.5 0 0 1-.708 0L7 14.707l-.646.647a.5.5 0 0 1-.708 0L5 14.707l-.646.647a.5.5 0 0 1-.708 0L3 14.707l-.646.647a.5.5 0 0 1-.801-.13l-.5-1A.5.5 0 0 1 1 14V2a.5.5 0 0 1 .053-.224l.5-1a.5.5 0 0 1 .367-.27m.217 1.338L2 2.118v11.764l.137.274.51-.51a.5.5 0 0 1 .707 0l.646.647.646-.646a.5.5 0 0 1 .708 0l.646.646.646-.646a.5.5 0 0 1 .708 0l.646.646.646-.646a.5.5 0 0 1 .708 0l.646.646.646-.646a.5.5 0 0 1 .708 0l.646.646.646-.646a.5.5 0 0 1 .708 0l.509.509.137-.274V2.118l-.137-.274-.51.51a.5.5 0 0 1-.707 0L12 1.707l-.646.647a.5.5 0 0 1-.708 0L10 1.707l-.646.647a.5.5 0 0 1-.708 0L8 1.707l-.646.647a.5.5 0 0 1-.708 0L6 1.707l-.646.647a.5.5 0 0 1-.708 0L4 1.707l-.646.647a.5.5 0 0 1-.708 0l-.509-.51zM3 4.5a.5.5 0 0 1 .5-.5h6a.5.5 0 1 1 0 1h-6a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h6a.5.5 0 1 1 0 1h-6a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h6a.5.5 0 1 1 0 1h-6a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h6a.5.5 0 0 1 0 1h-6a.5.5 0 0 1-.5-.5m8-6a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 0 1h-1a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 0 1h-1a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 0 1h-1a.5.5 0 0 1-.5-.5m0 2a.5.5 0 0 1 .5-.5h1a.5.5 0 0 1 0 1h-1a.5.5 0 0 1-.5-.5"/>
            </svg>
            &nbsp;Ontario Billing Report
        </h4>
    </div>

    <form name="serviceform" method="post" action="billingONReport.jsp">
        <div class="d-flex flex-wrap align-items-center gap-2" style="margin-bottom:10px;">
            <div class="form-check form-check-inline">
                <input class="form-check-input" type="radio" name="reportAction" value="unbilled" <%="unbilled".equals(action)? "checked" : "" %>>
                <label class="form-check-label">Unbilled</label>
            </div>
            <div class="form-check form-check-inline">
                <input class="form-check-input" type="radio" name="reportAction" value="billed" <%="billed".equals(action)? "checked" : "" %>>
                <label class="form-check-label">Billed</label>
            </div>

            &nbsp;&nbsp;Provider
            <% if (bMultisites) { // multisite start ==========================================
                SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
                List<Site> sites = siteDao.getActiveSitesByProviderNo(user_no);

                HashSet<String> reporters = new HashSet<String>();
                for (Object[] res : reportProviderDao.search_reportprovider("billingreport")) {
                    ReportProvider rp = (ReportProvider) res[0];
                    Provider p = (Provider) res[1];
                    reporters.add(p.getProviderNo());
                }

            %>
            <script>
                var _providers = {};
                <%  for (int i=0; i<sites.size(); i++) {
                    Set<Provider> siteProviders = sites.get(i).getProviders();
                    List<Provider> siteProvidersList = new ArrayList<Provider>(siteProviders);
                    Collections.sort(siteProvidersList,(new Provider()).ComparatorName()); %>
                _providers["<%= Encode.forJavaScript(sites.get(i).getName()) %>"] = [
                    <% Iterator<Provider> iter = siteProvidersList.iterator();
                    while (iter.hasNext()) {
                        Provider p = iter.next();
                        if (reporters.contains(p.getProviderNo())) { %>
                    {value: '<%= Encode.forJavaScript(p.getProviderNo()) %>', text: '<%= Encode.forJavaScript(p.getLastName() + ", " + p.getFirstName()) %>'},
                    <% }} %>
                ];
                <% } %>

                function changeSite(sel) {
                    var provSelect = sel.form.providerview;
                    provSelect.length = 0;
                    if (sel.value !== "none") {
                        var providers = _providers[sel.value];
                        for (var i = 0; i < providers.length; i++) {
                            var opt = document.createElement('option');
                            opt.value = providers[i].value;
                            opt.textContent = providers[i].text;
                            provSelect.add(opt);
                        }
                    }
                    sel.style.backgroundColor = sel.options[sel.selectedIndex].style.backgroundColor;
                }
            </script>
            <select id="site" name="site" class="form-select form-select-sm" style="width:auto; display:inline-block;" onchange="changeSite(this)">
                <option value="none" style="background-color:white">---select clinic---</option>
                <%
                    for (int i = 0; i < sites.size(); i++) {
                %>
                <option value="<%= Encode.forHtmlAttribute(sites.get(i).getName()) %>"
                        style="background-color:<%= Encode.forCssString(sites.get(i).getBgColor()) %>"
                        <%=sites.get(i).getName().toString().equals(request.getParameter("site")) ? "selected" : "" %>><%= Encode.forHtml(sites.get(i).getName()) %>
                </option>
                <% } %>
            </select>
            <select id="providerview" name="providerview" class="form-select form-select-sm" style="width:auto; display:inline-block;"></select>
            <% if (request.getParameter("providerview") != null) { %>
            <script>
                changeSite(document.getElementById("site"));
                document.getElementById("providerview").value = '<%=Encode.forJavaScript(io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("providerview")))%>';
            </script>
            <% } // multisite end ==========================================
            } else {
            %>
            <select name="providerview" class="form-select form-select-sm" style="width:auto; display:inline-block;">
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
                <option value="<%=Encode.forHtmlAttribute(proOHIP)%>" <%=providerview.equals(proOHIP) ? "selected" : ""%>><%=Encode.forHtml(proLast + ", " + proFirst)%></option>
                <%
                    }
                %>
            </select>
            <% } %>

            <label style="margin-left:10px;">From:
                <input type="date" name="xml_vdate" id="xml_vdate" class="form-select form-select-sm" style="width:auto; display:inline-block;" value="<%=Encode.forHtmlAttribute(xml_vdate)%>">
            </label>
            <label>To:
                <input type="date" name="xml_appointment_date" id="xml_appointment_date" class="form-select form-select-sm" style="width:auto; display:inline-block;" value="<%=Encode.forHtmlAttribute(xml_appointment_date)%>">
            </label>

            <input type="submit" name="Submit" class="btn btn-sm btn-primary" value="Create Report">
        </div>
        <a href="#" onClick="popupPage(700,720,'<%= request.getContextPath() %>/oscarReport/manageProvider.jsp?action=billingreport'); return false;" class="btn btn-sm btn-secondary">Manage Provider List</a>
    </form>

    <table id="reportTbl" class="table table-sm table-striped table-hover" style="margin-top:10px;">
        <thead>
        <tr>
            <% for (int i=0; i<vecHeader.size(); i++) {%>
            <th><%=vecHeader.get(i) %></th>
            <% } %>
        </tr>
        </thead>
        <tbody>
        <% for (int i = 0; i < vecValue.size(); i++) {%>
        <tr>
            <% for (int j = 0; j < vecHeader.size(); j++) {
                prop = (Properties) vecValue.get(i);
            %>
            <td><%=prop.getProperty((String) vecHeader.get(j), "&nbsp;") %></td>
            <% } %>
        </tr>
        <% } %>

        <% if (vecTotal.size() > 0) { %>
        <tr>
            <% for (int i = 0; i < vecTotal.size(); i++) {%>
            <th><%=vecTotal.get(i) %></th>
            <% } %>
        </tr>
        <% } %>
        </tbody>
    </table>


</div>
</div>

<script>
    $('#reportTbl').DataTable({
        "order": [],
        "language": {
            "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18n.datatablescode"/>.json"
        }
    });
</script>

</body>
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
