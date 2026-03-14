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

<%
    String user_no = (String) session.getAttribute("user");
    int nItems = 0;
    String strLimit1 = "0";
    String strLimit2 = "5";
    if (request.getParameter("limit1") != null) strLimit1 = request.getParameter("limit1");
    if (request.getParameter("limit2") != null) strLimit2 = request.getParameter("limit2");
    String providerview = request.getParameter("providerview") == null ? "all" : request.getParameter("providerview");
%>
<%@ page
        import="java.math.*, java.util.*, java.sql.*, io.github.carlos_emr.*, java.net.*" %>

<%@ include file="/taglibs.jsp" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"
       scope="request"/>

<%@ include file="/admin/dbconnection.jsp" %>

<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ClinicLocation" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ReportProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ReportProvider" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.BillingONCHeader1" %>
<%@page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%
    ClinicLocationDao clinicLocationDao = (ClinicLocationDao) SpringUtils.getBean(ClinicLocationDao.class);
    ReportProviderDao reportProviderDao = SpringUtils.getBean(ReportProviderDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    BillingONCHeader1Dao billingOnCHeaderDao = SpringUtils.getBean(BillingONCHeader1Dao.class);
%>
<%
    GregorianCalendar now = new GregorianCalendar();
    int curYear = now.get(Calendar.YEAR);
    int curMonth = (now.get(Calendar.MONTH) + 1);
    int curDay = now.get(Calendar.DAY_OF_MONTH);
    String clinic = "";
    String clinicview = oscarVariables.getProperty("clinic_view");

    String visitLocation = clinicLocationDao.searchVisitLocation(clinicview);
    if (visitLocation != null) {
        clinic = visitLocation;
    }

    int flag = 0, rowCount = 0;
    String reportAction = request.getParameter("reportAction") == null ? "" : request.getParameter("reportAction");
    String xml_vdate = request.getParameter("xml_vdate") == null ? "" : request.getParameter("xml_vdate");
    String xml_appointment_date = request.getParameter("xml_appointment_date") == null ? "" : request.getParameter("xml_appointment_date");
%>
<html>
<head>
    <title>Reports</title>
    <script type="text/javascript">

        function popupPage(height, width, url) {
            window.open(url, "manageProviders", "height=" + height + ",width=" + width + ",scrollbars=yes");

        }
    </script>
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
    <link href="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.css" rel="stylesheet">
    <script src="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.js"></script>
</head>
<body>
<div class="d-print-none" style="float:right;">
    <a style="font-size:10px" href="#"
       onclick="popupPage(700,720,'<%= request.getContextPath() %>/oscarReport/manageProvider.jsp?action=visitreport')">Manage Visit Report
        Providers</a>
</div>

<div class="pb-2 mt-4 mb-3 border-bottom">
    <h3>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportVisitControl.title"/>
        <div class="float-end">
            <button name="print" onclick="window.print()" class="btn d-print-none">
                <i class="fa-solid fa-print"></i>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnPrint"/>
            </button>
        </div>
    </h3>
</div>

<form action="${ctx}/oscarReport/oscarReportVisitControl.jsp"
      class="card card-body bg-body-tertiary d-print-none" id="visitForm">
    <fieldset>
        <h4>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportVisitControl.title"/>
            <br> <small>Please select the report type, provider and
            service begin and end dates.</small>
        </h4>
        <div class="mb-3">
            <label class="form-label">Select Report</label>
            <div>
                <label class="radio inline"> <input type="radio"
                                                    name="reportAction" onClick="toggleDivs();" value="lk"
                    <%=reportAction.equals("lk")?"checked":""%>> <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportVisitControl.msgLarryKainReport"/>
                </label> <label class="radio inline"> <input type="radio"
                                                             name="reportAction" onClick="toggleDivs();" value="vr"
                <%=reportAction.equals("vr") || reportAction.equals("")?"checked":""%>>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportVisitControl.msgVisitReport"/>
            </label>
            </div>
        </div>
        <div class="mb-3" id="providerDiv">
            <label class="form-label">Provider</label>

            <div>
                <select id="providerview" name="providerview"
                        <%=reportAction.equals("lk") ? "disabled" : ""%>>
                    <option value="%">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportVisitControl.msgSelectProviderAll"/>
                    </option>
                    <%
                        for (ReportProvider rps : reportProviderDao.findByAction("visitreport")) {
                            Provider p = providerDao.getProvider(rps.getProviderNo());
                            if (p.getStatus().equals("1")) {
                    %>
                    <option value="<%=p.getProviderNo()%>"><%=p.getFormattedName()%>
                    </option>
                    <%
                            }
                        }
                    %>
                </select>
            </div>
        </div>

        </div>

        <div class="mb-3">
            <label class="form-label">Service Date Begin</label>
            <div>
                <input type="text" id="xml_vdate" name="xml_vdate"
                       value="<%=xml_vdate%>">
            </div>
        </div>
        <div class="mb-3">
            <label class="form-label">Service Date End</label>
            <div>

                <input type="text" id="xml_appointment_date"
                       name="xml_appointment_date" value="<%=xml_appointment_date%>">
            </div>
        </div>
        <div class="mb-3">
            <div>
                <button type="submit" class="btn btn-primary">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportVisitControl.btnCreateReport"/>
                </button>
            </div>
        </div>
    </fieldset>
</form>
<%
    if (reportAction.compareTo("") == 0 || reportAction == null) {
%>
<p>&nbsp;</p>
<%
} else {
    if (reportAction.compareTo("lk") == 0) {
%>
<%@ include file="oscarReportVisit_lk.jspf" %>
<%
    }
    if (reportAction.compareTo("vr") == 0) {
%>
<%@ include file="oscarReportVisit_vr.jspf" %>
<%
        }

    }
%>

<script>
    flatpickr("#xml_vdate", {dateFormat: "Y-m-d", allowInput: true});
    flatpickr("#xml_appointment_date", {dateFormat: "Y-m-d", allowInput: true});

    $(document).ready(function () {
        $('#visitform').validate({
            rules: {
                xml_vdate: {
                    required: false,
                    oscarDate: true
                },
                xml_appointment_date: {
                    required: false,
                    oscarDate: true
                }
            }
        });
    });

    registerFormSubmit('visitForm', 'dynamic-content');

    function toggleDivs() {
        if (document.querySelector('input[name=reportAction]:checked').value == 'vr')
            document.getElementById('providerview').disabled = false;
        else
            document.getElementById('providerview').disabled = true;
    }
</script>
</body>
</html>