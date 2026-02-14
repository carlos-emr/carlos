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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page import="io.github.carlos_emr.carlos.report.data.RptSearchData,java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptDemographicColumnNames" %>
<%@ page import="io.github.carlos_emr.carlos.report.pageUtil.RptDemographicReport2Form" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%!
    // Helper method to check if a value exists in an array
    private boolean containsValue(String[] array, String value) {
        if (array == null || value == null) return false;
        for (String s : array) {
            if (value.equals(s)) return true;
        }
        return false;
    }

    // Helper to safely get string value with HTML encoding
    private String safeValue(String value) {
        return value == null ? "" : Encode.forHtmlAttribute(value);
    }
%>

<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<%
    RptSearchData searchData = new RptSearchData();
    java.util.ArrayList rosterArray;
    java.util.ArrayList patientArray;
    java.util.ArrayList providerArray;
    java.util.ArrayList queryArray;
    rosterArray = searchData.getRosterTypes();
    patientArray = searchData.getPatientTypes();
    providerArray = searchData.getProvidersWithDemographics();
    queryArray = searchData.getQueryTypes();

    String studyId = request.getParameter("studyId");
    if (studyId == null) {
        studyId = (String) request.getAttribute("studyId");
    }

    // Get form bean from action for repopulating values
    RptDemographicReport2Form formBean = (RptDemographicReport2Form) request.getAttribute("formBean");
    if (formBean == null) {
        formBean = new RptDemographicReport2Form();
    }
%>

<html>
    <head>
        <title>Demographic Report Tool</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script src="<%= request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
        <link href="<%= request.getContextPath() %>/library/bootstrap/3.0.0/css/bootstrap.css" rel="stylesheet" type="text/css">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/searchBox.css">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>

        <script type="text/javascript">
            $(document).ready(function () {
                $("#select_demographic_no").on('change', function () {
                    updateSetBox();
                });
                updateSetBox();
            });

            function updateSetBox() {
                if ($("#select_demographic_no").is(":checked")) {
                    $("#submitPatientSet").show();
                } else {
                    $("#submitPatientSet").hide();
                }
            }

            function checkQuery() {
                var ret = false;
                var chks = document.forms[0].select;
                for (var i = 0; i < chks.length; i++) {
                    if (chks[i].checked) {
                        ret = true;
                        break;
                    }
                }
                if (!ret) alert("Please select at least one field");
                return ret;
            }
        </script>

        <style type="text/css" media="print">
            .searchBox { display: none; }
        </style>
        <style type="text/css">
            #provider {
                margin: 0; padding: 0;
                list-style: none;
            }
            #provider li {
                display: inline-block;
                padding: 4px 6px;
                border: 1px solid #ddd;
                margin: 0 2px 2px 0;
                border-radius: 3px;
                background: #f9f9f9;
                font-size: 12px;
            }
            .section-header {
                font-weight: bold;
                font-size: 14px;
                padding: 6px 10px;
                background: #eee;
                border-bottom: 1px solid #ddd;
                margin-bottom: 8px;
            }
        </style>
    </head>

    <body>
    <div class="container">
    <div class="searchBox">

        <div style="background:#f5f5f5; padding:8px 15px; border-bottom:1px solid #ddd; margin-bottom:10px;">
            <h4 style="margin:0; font-size:18px;">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" style="vertical-align:text-bottom">
                    <path d="M1 2.5A1.5 1.5 0 0 1 2.5 1h3A1.5 1.5 0 0 1 7 2.5v3A1.5 1.5 0 0 1 5.5 7h-3A1.5 1.5 0 0 1 1 5.5zM2.5 2a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zm6.5.5A1.5 1.5 0 0 1 10.5 1h3A1.5 1.5 0 0 1 15 2.5v3A1.5 1.5 0 0 1 13.5 7h-3A1.5 1.5 0 0 1 9 5.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zM1 10.5A1.5 1.5 0 0 1 2.5 9h3A1.5 1.5 0 0 1 7 10.5v3A1.5 1.5 0 0 1 5.5 15h-3A1.5 1.5 0 0 1 1 13.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zm6.5.5A1.5 1.5 0 0 1 10.5 9h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-3A1.5 1.5 0 0 1 9 13.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5z"/>
                </svg>
                &nbsp;Demographic Report Tool
            </h4>
        </div>

        <form action="${pageContext.request.contextPath}/report/DemographicReport.do" method="post" onsubmit="return checkQuery();">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
            <input type="hidden" name="studyId" id="studyId" value='<%=studyId == null ? "" : Encode.forHtmlAttribute(studyId)%>'/>

            <div style="margin-bottom:10px; padding:5px 10px; background:#fafafa; border:1px solid #eee; border-radius:3px;">
                <select name="savedQuery" id="savedQuery" class="form-control input-sm" style="width:auto;display:inline-block">
                    <%
                        for (int i = 0; i < queryArray.size(); i++) {
                            RptSearchData.SearchCriteria sc = (RptSearchData.SearchCriteria) queryArray.get(i);
                            String qId = sc.id;
                            String qName = sc.queryName;%>
                    <option value="<%=Encode.forHtmlAttribute(qId)%>"><%=Encode.forHtml(qName)%></option>
                    <%}%>
                </select>
                <input type="submit" value="Load Query" name="query" class="btn btn-sm btn-default"/>
                <a href="<%= request.getContextPath() %>/oscarReport/ManageDemographicQueryFavourites.jsp">manage</a>
            </div>

            <div class="row">
                <div class="col-md-4">
                    <div class="section-header">Search For</div>
                    <table class="table table-condensed table-striped" style="font-size:13px;">
                        <tr>
                            <td><input type="checkbox" name="select" id="select_demographic_no" value="demographic_no" <%= containsValue(formBean.getSelect(), "demographic_no") ? "checked" : "" %>/></td>
                            <td>Demographic #</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_last_name" value="last_name" <%= containsValue(formBean.getSelect(), "last_name") ? "checked" : "" %>/></td>
                            <td>Last Name</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_first_name" value="first_name" <%= containsValue(formBean.getSelect(), "first_name") ? "checked" : "" %>/></td>
                            <td>First Name</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_address" value="address" <%= containsValue(formBean.getSelect(), "address") ? "checked" : "" %>/></td>
                            <td>Address</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_city" value="city" <%= containsValue(formBean.getSelect(), "city") ? "checked" : "" %>/></td>
                            <td>City</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_province" value="province" <%= containsValue(formBean.getSelect(), "province") ? "checked" : "" %>/></td>
                            <td>Province</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_postal_code" value="postal" <%= containsValue(formBean.getSelect(), "postal") ? "checked" : "" %>/></td>
                            <td>Postal Code</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_phone" value="phone" <%= containsValue(formBean.getSelect(), "phone") ? "checked" : "" %>/></td>
                            <td>Phone</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_phone_2" value="phone2" <%= containsValue(formBean.getSelect(), "phone2") ? "checked" : "" %>/></td>
                            <td>Phone 2</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_email" value="email" <%= containsValue(formBean.getSelect(), "email") ? "checked" : "" %>/></td>
                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportscpbDemo.msgEmail"/></td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_year_of_birth" value="year_of_birth" <%= containsValue(formBean.getSelect(), "year_of_birth") ? "checked" : "" %>/></td>
                            <td>Year of Birth</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_month_of_birth" value="month_of_birth" <%= containsValue(formBean.getSelect(), "month_of_birth") ? "checked" : "" %>/></td>
                            <td>Month of Birth</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_date_of_birth" value="date_of_birth" <%= containsValue(formBean.getSelect(), "date_of_birth") ? "checked" : "" %>/></td>
                            <td>Date of Birth</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_hin" value="hin" <%= containsValue(formBean.getSelect(), "hin") ? "checked" : "" %>/></td>
                            <td>HIN</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_ver" value="ver" <%= containsValue(formBean.getSelect(), "ver") ? "checked" : "" %>/></td>
                            <td>Version Code</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_roster_status" value="roster_status" <%= containsValue(formBean.getSelect(), "roster_status") ? "checked" : "" %>/></td>
                            <td>Roster Status</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_patient_status" value="patient_status" <%= containsValue(formBean.getSelect(), "patient_status") ? "checked" : "" %>/></td>
                            <td>Patient Status</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_date_joined" value="date_joined" <%= containsValue(formBean.getSelect(), "date_joined") ? "checked" : "" %>/></td>
                            <td>Date Joined</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_chart_no" value="chart_no" <%= containsValue(formBean.getSelect(), "chart_no") ? "checked" : "" %>/></td>
                            <td>Chart #</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_provider_no" value="provider_no" <%= containsValue(formBean.getSelect(), "provider_no") ? "checked" : "" %>/></td>
                            <td>Provider #</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_provider_name" value="provider_name" <%= containsValue(formBean.getSelect(), "provider_name") ? "checked" : "" %>/></td>
                            <td>Provider Name</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_sex" value="sex" <%= containsValue(formBean.getSelect(), "sex") ? "checked" : "" %>/></td>
                            <td>Sex</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_end_date" value="end_date" <%= containsValue(formBean.getSelect(), "end_date") ? "checked" : "" %>/></td>
                            <td>End Date</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_eff_date" value="eff_date" <%= containsValue(formBean.getSelect(), "eff_date") ? "checked" : "" %>/></td>
                            <td>Eff. Date</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_pcn_indicator" value="pcn_indicator" <%= containsValue(formBean.getSelect(), "pcn_indicator") ? "checked" : "" %>/></td>
                            <td>Pcn Indicator</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_hc_type" value="hc_type" <%= containsValue(formBean.getSelect(), "hc_type") ? "checked" : "" %>/></td>
                            <td>Health Card Type</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_hc_renew_date" value="hc_renew_date" <%= containsValue(formBean.getSelect(), "hc_renew_date") ? "checked" : "" %>/></td>
                            <td>HC Renew Date</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_family_doctor" value="family_doctor" <%= containsValue(formBean.getSelect(), "family_doctor") ? "checked" : "" %>/></td>
                            <td>Family Doctor</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="select" id="select_newsletter" value="newsletter" <%= containsValue(formBean.getSelect(), "newsletter") ? "checked" : "" %>/></td>
                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoReport.frmNewsletter"/></td>
                        </tr>
                    </table>

                    <div style="padding:5px 0;">
                        <input type="text" name="queryName" id="queryName" class="form-control input-sm" style="margin-bottom:5px;" placeholder="Query Name" value="<%= safeValue(formBean.getQueryName()) %>"/>
                        <input type="submit" value="Save Query" name="query" class="btn btn-sm btn-default"/>
                        <input type="submit" value="Run Query" name="query" class="btn btn-sm btn-primary"/>
                    </div>
                    <span id="submitPatientSet" style="display:block; margin-top:5px;">
                        <input type="submit" value="Run Query And Save to Patient Set" name="query" class="btn btn-sm btn-default"/>
                        <input type="text" name="setName" class="form-control input-sm" style="width:auto;display:inline-block;margin-top:3px;" placeholder="Set Name" value="<%= safeValue(formBean.getSetName()) %>"/>
                    </span>
                    <%if (studyId != null && !studyId.equals("") && !studyId.equalsIgnoreCase("null")) {%>
                    <div style="margin-top:5px;">
                        <input type="submit" value="Add to Study" name="query" class="btn btn-sm btn-default"/>
                    </div>
                    <%} %>
                </div>

                <div class="col-md-8">
                    <div class="section-header">Where</div>
                    <table class="table table-condensed table-striped" style="font-size:13px;">
                        <tr>
                            <td style="width:120px;">AGE</td>
                            <td>
                                <select name="age" id="age" class="form-control input-sm" style="width:auto;display:inline-block">
                                    <option value="0" <%= "0".equals(formBean.getAge()) || formBean.getAge() == null ? "selected" : "" %>>---NO AGE SPECIFIED---</option>
                                    <option value="1" <%= "1".equals(formBean.getAge()) ? "selected" : "" %>>younger than</option>
                                    <option value="2" <%= "2".equals(formBean.getAge()) ? "selected" : "" %>>older than</option>
                                    <option value="3" <%= "3".equals(formBean.getAge()) ? "selected" : "" %>>equal too</option>
                                    <option value="4" <%= "4".equals(formBean.getAge()) ? "selected" : "" %>>ages between</option>
                                </select>
                                <input type="text" name="startYear" size="4" class="form-control input-sm" style="width:60px;display:inline-block" value="<%= safeValue(formBean.getStartYear()) %>"/>
                                <input type="text" name="endYear" size="4" class="form-control input-sm" style="width:60px;display:inline-block" value="<%= safeValue(formBean.getEndYear()) %>"/>
                            </td>
                        </tr>
                        <tr>
                            <td>Age Style</td>
                            <td>
                                Exact: <input type="radio" name="ageStyle" value="1" <%= "1".equals(formBean.getAgeStyle()) || formBean.getAgeStyle() == null ? "checked" : "" %>/>
                                &nbsp; In the year: <input type="radio" name="ageStyle" value="2" <%= "2".equals(formBean.getAgeStyle()) ? "checked" : "" %>/>
                                &nbsp; As of: <input type="text" name="asofDate" size="9" id="asofDate" class="form-control input-sm" style="width:auto;display:inline-block" value="<%= safeValue(formBean.getAsofDate()) %>"/>
                                <a id="date"><img title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
                            </td>
                        </tr>
                        <tr>
                            <td>First Name</td>
                            <td>
                                <input type="text" name="firstName" id="firstName" class="form-control input-sm" style="width:auto;display:inline-block" value="<%= safeValue(formBean.getFirstName()) %>"/>
                                &nbsp; Last Name:
                                <input type="text" name="lastName" id="lastName" class="form-control input-sm" style="width:auto;display:inline-block" value="<%= safeValue(formBean.getLastName()) %>"/>
                            </td>
                        </tr>
                        <tr>
                            <td>Roster Status</td>
                            <td>
                                <%
                                    for (int i = 0; i < rosterArray.size(); i++) {
                                        String ros = (String) rosterArray.get(i);%>
                                <label style="display:inline-block; margin-right:8px; font-weight:normal; font-size:12px;">
                                    <input type="checkbox" name="rosterStatus" value="<%=Encode.forHtmlAttribute(ros)%>" <%= containsValue(formBean.getRosterStatus(), ros) ? "checked" : "" %>/> <%=Encode.forHtml(ros)%>
                                </label>
                                <%}%>
                            </td>
                        </tr>
                        <tr>
                            <td>Sex</td>
                            <td>
                                <select name="sex" id="sex" class="form-control input-sm" style="width:auto;display:inline-block">
                                    <option value="0" <%= "0".equals(formBean.getSex()) || formBean.getSex() == null ? "selected" : "" %>>---NO SEX SPECIFIED---</option>
                                    <option value="1" <%= "1".equals(formBean.getSex()) ? "selected" : "" %>>Female</option>
                                    <option value="2" <%= "2".equals(formBean.getSex()) ? "selected" : "" %>>Male</option>
                                </select>
                            </td>
                        </tr>
                        <tr>
                            <td>Provider No</td>
                            <td>
                                <ul id="provider">
                                    <%
                                        for (int i = 0; i < providerArray.size(); i++) {
                                            String pro = (String) providerArray.get(i);
                                            if (pro != null && !"".equals(pro)) {
                                    %>
                                    <li><%=Encode.forHtml(providerBean.getProperty(pro, pro))%>
                                        <input type="checkbox" name="providerNo" value="<%=Encode.forHtmlAttribute(pro)%>" <%= containsValue(formBean.getProviderNo(), pro) ? "checked" : "" %>/>
                                    </li>
                                    <%
                                            }
                                        }
                                    %>
                                </ul>
                            </td>
                        </tr>
                        <tr>
                            <td>Patient Status</td>
                            <td>
                                <%
                                    for (int i = 0; i < patientArray.size(); i++) {
                                        String pat = (String) patientArray.get(i);%>
                                <label style="display:inline-block; margin-right:8px; font-weight:normal; font-size:12px;">
                                    <input type="checkbox" name="patientStatus" value="<%=Encode.forHtmlAttribute(pat)%>" <%= containsValue(formBean.getPatientStatus(), pat) ? "checked" : "" %>/> <%=Encode.forHtml(pat)%>
                                </label>
                                <%}%>
                            </td>
                        </tr>
                        <tr>
                            <td>Demographic ID(s)</td>
                            <td>
                                <textarea name="demoIds" class="form-control input-sm" rows="3" style="width:100%;"><%= formBean.getDemoIds() != null ? Encode.forHtml(formBean.getDemoIds()) : "" %></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td>Order By</td>
                            <td>
                                <select name="orderBy" id="orderBy" class="form-control input-sm" style="width:auto;display:inline-block">
                                    <option value="0" <%= "0".equals(formBean.getOrderBy()) || formBean.getOrderBy() == null || formBean.getOrderBy().isEmpty() ? "selected" : "" %>>---NO ORDER---</option>
                                    <option value="Demographic #" <%= "Demographic #".equals(formBean.getOrderBy()) ? "selected" : "" %>>Demographic #</option>
                                    <option value="Last Name" <%= "Last Name".equals(formBean.getOrderBy()) ? "selected" : "" %>>Last Name</option>
                                    <option value="First Name" <%= "First Name".equals(formBean.getOrderBy()) ? "selected" : "" %>>First Name</option>
                                    <option value="Address" <%= "Address".equals(formBean.getOrderBy()) ? "selected" : "" %>>Address</option>
                                    <option value="City" <%= "City".equals(formBean.getOrderBy()) ? "selected" : "" %>>City</option>
                                    <option value="Province" <%= "Province".equals(formBean.getOrderBy()) ? "selected" : "" %>>Province</option>
                                    <option value="Postal Code" <%= "Postal Code".equals(formBean.getOrderBy()) ? "selected" : "" %>>Postal Code</option>
                                    <option value="Phone" <%= "Phone".equals(formBean.getOrderBy()) ? "selected" : "" %>>Phone</option>
                                    <option value="Phone 2" <%= "Phone 2".equals(formBean.getOrderBy()) ? "selected" : "" %>>Phone 2</option>
                                    <option value="Year of Birth" <%= "Year of Birth".equals(formBean.getOrderBy()) ? "selected" : "" %>>Year of Birth</option>
                                    <option value="Month of Birth" <%= "Month of Birth".equals(formBean.getOrderBy()) ? "selected" : "" %>>Month of Birth</option>
                                    <option value="Date of Birth" <%= "Date of Birth".equals(formBean.getOrderBy()) ? "selected" : "" %>>Date of Birth</option>
                                    <option value="HIN" <%= "HIN".equals(formBean.getOrderBy()) ? "selected" : "" %>>HIN</option>
                                    <option value="Version Code" <%= "Version Code".equals(formBean.getOrderBy()) ? "selected" : "" %>>Version Code</option>
                                    <option value="Roster Status" <%= "Roster Status".equals(formBean.getOrderBy()) ? "selected" : "" %>>Roster Status</option>
                                    <option value="Patient Status" <%= "Patient Status".equals(formBean.getOrderBy()) ? "selected" : "" %>>Patient Status</option>
                                    <option value="Date Joined" <%= "Date Joined".equals(formBean.getOrderBy()) ? "selected" : "" %>>Date Joined</option>
                                    <option value="Chart #" <%= "Chart #".equals(formBean.getOrderBy()) ? "selected" : "" %>>Chart #</option>
                                    <option value="Provider #" <%= "Provider #".equals(formBean.getOrderBy()) ? "selected" : "" %>>Provider #</option>
                                    <option value="Sex" <%= "Sex".equals(formBean.getOrderBy()) ? "selected" : "" %>>Sex</option>
                                    <option value="End Date" <%= "End Date".equals(formBean.getOrderBy()) ? "selected" : "" %>>End Date</option>
                                    <option value="Eff. Date" <%= "Eff. Date".equals(formBean.getOrderBy()) ? "selected" : "" %>>Eff. Date</option>
                                    <option value="Pcn indicator" <%= "Pcn indicator".equals(formBean.getOrderBy()) ? "selected" : "" %>>Pcn indicator</option>
                                    <option value="HC Type" <%= "HC Type".equals(formBean.getOrderBy()) ? "selected" : "" %>>HC Type</option>
                                    <option value="HC Renew Date" <%= "HC Renew Date".equals(formBean.getOrderBy()) ? "selected" : "" %>>HC Renew Date</option>
                                    <option value="Family Doctor" <%= "Family Doctor".equals(formBean.getOrderBy()) ? "selected" : "" %>>Family Doctor</option>
                                    <option value="Random" <%= "Random".equals(formBean.getOrderBy()) ? "selected" : "" %>>Random</option>
                                </select>
                            </td>
                        </tr>
                        <tr>
                            <td>Limit Results to</td>
                            <td>
                                <select name="resultNum" id="resultNum" class="form-control input-sm" style="width:auto;display:inline-block">
                                    <option value="0" <%= "0".equals(formBean.getResultNum()) || formBean.getResultNum() == null || formBean.getResultNum().isEmpty() ? "selected" : "" %>>---NO LIMIT---</option>
                                    <option value="10" <%= "10".equals(formBean.getResultNum()) ? "selected" : "" %>>10</option>
                                    <option value="50" <%= "50".equals(formBean.getResultNum()) ? "selected" : "" %>>50</option>
                                    <option value="100" <%= "100".equals(formBean.getResultNum()) ? "selected" : "" %>>100</option>
                                    <option value="150" <%= "150".equals(formBean.getResultNum()) ? "selected" : "" %>>150</option>
                                    <option value="200" <%= "200".equals(formBean.getResultNum()) ? "selected" : "" %>>200</option>
                                    <option value="250" <%= "250".equals(formBean.getResultNum()) ? "selected" : "" %>>250</option>
                                    <option value="300" <%= "300".equals(formBean.getResultNum()) ? "selected" : "" %>>300</option>
                                </select>
                            </td>
                        </tr>
                    </table>
                </div>
            </div>
        </form>

    <%
        String[] selectArray = (String[]) request.getAttribute("selectArray");
        java.util.ArrayList searchList = (java.util.ArrayList) request.getAttribute("searchedArray");
        if (searchList != null && selectArray != null) {
            RptDemographicColumnNames dcn = new RptDemographicColumnNames();
    %>
        <div style="margin-top:15px;">
            <div class="section-header">Search Returned: <%=searchList.size()%> Results</div>
            <table class="table table-condensed table-striped table-bordered" style="font-size:13px;">
                <tr>
                    <%for (int i = 0; i < selectArray.length; i++) {%>
                    <th><%=Encode.forHtml(dcn.getColumnTitle(selectArray[i]))%></th>
                    <%}%>
                </tr>
                <%
                    for (int i = 0; i < searchList.size(); i++) {
                        java.util.ArrayList al = (java.util.ArrayList) searchList.get(i);
                %>
                <tr>
                    <%
                        for (int j = 0; j < al.size(); j++) {
                            String str = (String) al.get(j);
                            boolean isEmpty = (str == null || str.equals(""));
                    %>
                    <td><%= isEmpty ? "&nbsp;" : Encode.forHtml(str) %></td>
                    <%}%>
                </tr>
                <%}%>
            </table>
        </div>
    <%}%>

    </div>
    </div>

    <script type="text/javascript">
        Calendar.setup({
            inputField: "asofDate",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "date",
            singleClick: true,
            step: 1
        });
    </script>
    </body>
</html>
