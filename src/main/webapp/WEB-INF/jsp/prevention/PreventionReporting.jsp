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
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LogSafe" %>
<%@page import="org.apache.commons.lang3.StringUtils" %>
<%@page import="io.github.carlos_emr.carlos.demographic.data.*,java.util.*, java.text.SimpleDateFormat,io.github.carlos_emr.carlos.prevention.*,io.github.carlos_emr.carlos.providers.data.*,io.github.carlos_emr.carlos.util.*,io.github.carlos_emr.carlos.report.data.*,io.github.carlos_emr.carlos.prevention.pageUtil.*,java.net.*,io.github.carlos_emr.carlos.eform.*" %>
<%@page import="io.github.carlos_emr.CarlosProperties"%>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils"%>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.prevention.pageUtil.PreventionReportDisplay" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptSearchData" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DemographicExt" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_prevention" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_prevention");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    String demographic_no = request.getParameter("demographic_no");

    RptSearchData searchData = new RptSearchData();
    ArrayList queryArray = searchData.getQueryTypes();

    String preventionText = "";

    String eformSearch = (String) request.getAttribute("eformSearch");
    String asofDate = request.getParameter("asofDate");
    if (asofDate == null) asofDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

    String patientSet = request.getParameter("patientSet");
    if (patientSet == null) {
        patientSet = (String) request.getAttribute("patientSet");
    }

    String prevention = request.getParameter("prevention");
    if (prevention == null) {
        prevention = (String) request.getAttribute("prevention");
    }
%>

<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
        <title><fmt:message key="oscarprevention.index.oscarpreventiontitre"/></title>
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1">
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:message key="global.javascript.calendar"/>"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>

        <script type="text/javascript">
            function showHideItem(id) {
                if (document.getElementById(id).style.display == 'none')
                    document.getElementById(id).style.display = '';
                else
                    document.getElementById(id).style.display = 'none';
            }
            function showItem(id) { document.getElementById(id).style.display = ''; }
            function hideItem(id) { document.getElementById(id).style.display = 'none'; }

            function showHideNextDate(id, nextDate, neverWarn) {
                if (document.getElementById(id).style.display == 'none') {
                    showItem(id);
                } else {
                    hideItem(id);
                    document.getElementById(nextDate).value = "";
                    document.getElementById(neverWarn).checked = false;
                }
            }

            function disableifchecked(ele, nextDate) {
                document.getElementById(nextDate).disabled = (ele.checked == true);
            }

        </script>

        <style type="text/css">
            .section-header {
                font-weight: bold;
                font-size: 14px;
                padding: 6px 10px;
                background: #eee;
                border-bottom: 1px solid #ddd;
                margin-bottom: 8px;
            }
            table.sortable thead {
                background-color: #eee;
                color: #666666;
                font-size: x-small;
                cursor: default;
            }
            table.sortable thead th {
                padding: 4px 6px;
                border: 1px solid #ddd;
            }
            .sortarrow img[src=""] {
                display: none;
            }
        </style>
        <style type="text/css" media="print">
            .searchBox { display: none; }
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
                &nbsp;<fmt:message key="prevention.reporting.title"/>
            </h4>
        </div>

        <form action="${pageContext.request.contextPath}/prevention/PreventionReport" method="get">
            <table class="table table-sm" style="font-size:13px; margin-bottom:10px;">
                <tr>
                    <td style="width:180px;"><fmt:message key="prevention.reporting.patientDemographicQuery"/></td>
                    <td>
                        <select name="patientSet" id="patientSet" class="form-select form-select-sm" style="width:auto;display:inline-block">
                            <option value="-1" <%=("-1".equals(patientSet) || patientSet == null) ? "selected" : ""%>><fmt:message key="prevention.reporting.selectQuery"/></option>
                            <%
                                for (int i = 0; i < queryArray.size(); i++) {
                                    RptSearchData.SearchCriteria sc = (RptSearchData.SearchCriteria) queryArray.get(i);
                                    String qId = sc.id;
                                    String qName = sc.queryName;
                                    String selected = (patientSet != null && patientSet.equals(qId)) ? "selected" : "";
                            %>
                            <option value="<carlos:encode value='<%= qId %>' context="htmlAttribute"/>" <%=selected%>><carlos:encode value='<%= qName %>' context="html"/></option>
                            <%}%>
                        </select>
                    </td>
                </tr>
                <tr>
                    <td><fmt:message key="prevention.reporting.prevention"/></td>
                    <td>
                        <select name="prevention" id="prevention" class="form-select form-select-sm" style="width:auto;display:inline-block">
                            <option value="-1" <%=("-1".equals(prevention) || prevention == null) ? "selected" : ""%>><fmt:message key="prevention.reporting.selectPrevention"/></option>
                            <option value="Mammogram" <%="Mammogram".equals(prevention) ? "selected" : ""%>><fmt:message key="prevention.reporting.breast"/></option>
                            <option value="PAP" <%="PAP".equals(prevention) ? "selected" : ""%>><fmt:message key="prevention.reporting.cervical"/></option>
                            <option value="ChildImmunizations" <%="ChildImmunizations".equals(prevention) ? "selected" : ""%>><fmt:message key="prevention.reporting.childImmunizations"/></option>
                            <option value="FOBT" <%="FOBT".equals(prevention) ? "selected" : ""%>><fmt:message key="prevention.reporting.colorectal"/></option>
                            <option value="Flu" <%="Flu".equals(prevention) ? "selected" : ""%>><fmt:message key="prevention.reporting.flu"/></option>
                        </select>
                    </td>
                </tr>
                <tr>
                    <td><fmt:message key="prevention.reporting.asOf"/></td>
                    <td>
                        <input type="text" name="asofDate" size="9" id="asofDate" class="form-control form-control-sm" style="width:auto;display:inline-block" value="<carlos:encode value='<%= asofDate %>' context="htmlAttribute"/>"/>
                        <a id="date"><img title="<fmt:message key='prevention.reporting.calendar'/>" src="<%= request.getContextPath() %>/images/cal.gif" alt="<fmt:message key='prevention.reporting.calendar'/>" border="0"/></a>
                    </td>
                </tr>
            </table>
            <div style="padding:0 0 10px 0;">
                <input type="submit" value="<fmt:message key='prevention.reporting.runReport'/>" class="btn btn-sm btn-primary"/>
            </div>
        </form>

    </div>

    <%
        ArrayList overDueList = new ArrayList();
        String type = (String) request.getAttribute("ReportType");
        String ineligible = (String) request.getAttribute("inEligible");
        String done = (String) request.getAttribute("up2date");
        String percentage = (String) request.getAttribute("percent");
        String percentageWithGrace = (String) request.getAttribute("percentWithGrace");
        ArrayList list = (ArrayList) request.getAttribute("returnReport");
        Date asDate = (Date) request.getAttribute("asDate");
        if (asDate == null) {
            asDate = Calendar.getInstance().getTime();
        }

        if (list != null) {
    %>
    <div style="margin-top:10px;">
        <form name="frmBatchBill" action="" method="post">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
            <input type="hidden" name="clinic_view" value="<%=CarlosProperties.getInstance().getProperty("clinic_view","")%>">

            <div style="background:#f5f5f5; padding:8px 15px; border:1px solid #ddd; border-radius:3px; margin-bottom:10px;">
                <span style="margin-right:15px;"><fmt:message key="prevention.reporting.totalPatients"/>: <strong><%=list.size()%></strong></span>
                <span style="margin-right:15px;"><fmt:message key="prevention.reporting.ineligible"/>: <strong><carlos:encode value='<%= StringUtils.defaultString(ineligible, "0") %>' context="html"/></strong></span>
                <span style="margin-right:15px;"><fmt:message key="prevention.reporting.upToDate"/>: <strong><carlos:encode value='<%= StringUtils.defaultString(done, "0") %>' context="html"/> = <carlos:encode value='<%= StringUtils.defaultString(percentage, "0") %>' context="html"/>%</strong></span>
                <span style="margin-right:15px;"><carlos:encode value='<%= String.valueOf(request.getAttribute("patientSet")) %>' context="html"/></span>
            </div>

            <table id="preventionTable" class="sortable table table-sm table-bordered" style="font-size:12px;">
                <thead>
                <tr>
                    <th class="unsortable">#</th>
                    <th><fmt:message key="prevention.reporting.demoNo"/></th>
                    <th><fmt:message key="prevention.reporting.dob"/></th>
                    <th><fmt:message key="prevention.reporting.ageAsOf"/><br/><%=UtilDateUtilities.DateToString(asDate)%></th>
                    <th><fmt:message key="prevention.reporting.sex"/></th>
                    <th><fmt:message key="prevention.reporting.lastname"/></th>
                    <th><fmt:message key="prevention.reporting.firstname"/></th>
                    <%if (type != null) { %>
                    <th><fmt:message key="prevention.reporting.guardian"/></th>
                    <%}%>
                    <th><fmt:message key="prevention.reporting.phone"/></th>
                    <th><fmt:message key="prevention.reporting.email"/></th>
                    <th><fmt:message key="prevention.reporting.address"/></th>
                    <th><fmt:message key="prevention.reporting.nextAppt"/></th>
                    <th><fmt:message key="prevention.reporting.status"/></th>
                    <%if (type != null) { %>
                    <th>Shot #</th>
                    <%}%>
                    <th>Months Since</th>
                    <th>Last Procedure</th>
                    <th>Roster Physician</th>
                </tr>
                </thead>
                <tbody>
                <%
                    DemographicNameAgeString deName = DemographicNameAgeString.getInstance();
                    DemographicData demoData = new DemographicData();

                    for (int i = 0; i < list.size(); i++) {
                        PreventionReportDisplay dis = (PreventionReportDisplay) list.get(i);
                        Hashtable<String, String> h = new Hashtable<>(deName.getNameAgeSexHashtable(LoggedInInfo.getLoggedInInfoFromSession(request), dis.demographicNo.toString()));
                        Demographic demo = demoData.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), dis.demographicNo.toString());

                        if (demo == null) {
                            MiscUtils.getLogger().warn(
                            "PreventionReporting: demographic not found for demographicNo={}",
                            LogSafe.sanitizeObject(dis.demographicNo)
                        );
                            continue;
                        }

                        if (dis.state != null && dis.state.equals("Overdue")) {
                            overDueList.add(dis.demographicNo);
                        }
                %>
                <tr>
                    <td><%=i+1%></td>
                    <td>
                        <a href="javascript: return false;" onClick="popup(724,964,'<%= request.getContextPath() %>/demographic/DemographicEdit?demographic_no=<carlos:encode value='<%= dis.demographicNo.toString() %>' context="htmlAttribute"/>','MasterDemographic')"><carlos:encode value='<%= dis.demographicNo.toString() %>' context="html"/></a>
                    </td>
                    <td><%=DemographicData.getDob(demo,"-")%></td>

                    <%if (type == null) { %>
                    <td><%=demo.getAgeAsOf(asDate)%></td>
                    <td><carlos:encode value='<%= h.getOrDefault("sex", "") %>' context="html"/></td>
                    <td><carlos:encode value='<%= h.getOrDefault("lastName", "") %>' context="html"/></td>
                    <td><carlos:encode value='<%= h.getOrDefault("firstName", "") %>' context="html"/></td>
                    <td><%
                        String hExt = demo.getExtraValue(DemographicExt.DemographicProperty.hPhoneExt);
                        String wExt = demo.getExtraValue(DemographicExt.DemographicProperty.wPhoneExt);
                        if (!demo.getPhone().isEmpty()) { %>H: <carlos:encode value='<%= demo.getPhone() %>' context="html"/><%= !hExt.isEmpty() ? " x" + SafeEncode.forHtmlContent(hExt) : "" %><%
                        }
                        if (!demo.getPhone2().isEmpty()) { %><br/>W: <carlos:encode value='<%= demo.getPhone2() %>' context="html"/><%= !wExt.isEmpty() ? " x" + SafeEncode.forHtmlContent(wExt) : "" %><%
                        }
                        if (!demo.getCellPhone().isEmpty()) { %><br/>C: <carlos:encode value='<%= demo.getCellPhone() %>' context="html"/><%
                        }
                    %></td>
                    <td><carlos:encode value='<%= StringUtils.defaultString(demo.getEmail()) %>' context="html"/></td>
                    <td><%=SafeEncode.forHtmlContent(StringUtils.defaultString(demo.getAddress()))+" "+SafeEncode.forHtmlContent(StringUtils.defaultString(demo.getCity()))+" "+SafeEncode.forHtmlContent(StringUtils.defaultString(demo.getProvince()))+" "+SafeEncode.forHtmlContent(StringUtils.defaultString(demo.getPostal()))%></td>
                    <td><oscar:nextAppt demographicNo="<%=demo.getDemographicNo().toString()%>"/></td>
                    <td><%
String labelClass;
if ("green".equals(dis.color)) labelClass = "bg-success";
else if ("red".equals(dis.color)) labelClass = "bg-danger";
else if ("yellow".equals(dis.color)) labelClass = "bg-warning text-dark";
else if ("orange".equals(dis.color)) labelClass = "bg-warning text-dark";
else if ("pink".equals(dis.color)) labelClass = "bg-info";
else if ("Magenta".equals(dis.color)) labelClass = "bg-info";
else labelClass = "bg-secondary";
%><span class="badge <%=labelClass%>"><carlos:encode value='<%= StringUtils.defaultString(dis.state) %>' context="html"/></span></td>
                    <td><carlos:encode value='<%= String.valueOf(dis.numMonths) %>' context="html"/></td>
                    <td><carlos:encode value='<%= StringUtils.defaultString(dis.lastDate) %>' context="html"/></td>

                    <% } else {
                        Demographic demoSDM = demoData.getSubstituteDecisionMaker(LoggedInInfo.getLoggedInInfoFromSession(request), dis.demographicNo.toString());%>
                    <td><%=demo.getAgeAsOf(asDate)%></td>
                    <td><carlos:encode value='<%= h.getOrDefault("sex", "") %>' context="html"/></td>
                    <td><carlos:encode value='<%= h.getOrDefault("lastName", "") %>' context="html"/></td>
                    <td><carlos:encode value='<%= h.getOrDefault("firstName", "") %>' context="html"/></td>
                    <td><%=demoSDM == null ? "" : SafeEncode.forHtmlContent(demoSDM.getLastName())%><%=demoSDM == null ? "" : ","%> <%= demoSDM == null ? "" : SafeEncode.forHtmlContent(demoSDM.getFirstName()) %>&nbsp;</td>
                    <td><%=demoSDM == null ? "" : SafeEncode.forHtmlContent(demoSDM.getPhone())%>&nbsp;</td>
                    <td><%=demoSDM == null ? "" : SafeEncode.forHtmlContent(demoSDM.getEmail())%>&nbsp;</td>
                    <td><%=demoSDM == null ? "" :SafeEncode.forHtmlContent(demoSDM.getAddress())%> <%=demoSDM == null ? "" : SafeEncode.forHtmlContent(demoSDM.getCity())%> <%=demoSDM == null ? "" : SafeEncode.forHtmlContent(demoSDM.getProvince())%> <%=demoSDM == null ? "" : SafeEncode.forHtmlContent(demoSDM.getPostal())%>&nbsp;</td>
                    <td><oscar:nextAppt demographicNo="<%=demo.getDemographicNo().toString()%>"/></td>
                    <td><%
String labelClass;
if ("green".equals(dis.color)) labelClass = "bg-success";
else if ("red".equals(dis.color)) labelClass = "bg-danger";
else if ("yellow".equals(dis.color)) labelClass = "bg-warning text-dark";
else if ("orange".equals(dis.color)) labelClass = "bg-warning text-dark";
else if ("pink".equals(dis.color)) labelClass = "bg-info";
else if ("Magenta".equals(dis.color)) labelClass = "bg-info";
else labelClass = "bg-secondary";
%><span class="badge <%=labelClass%>"><carlos:encode value='<%= StringUtils.defaultString(dis.state) %>' context="html"/></span></td>
                    <td><carlos:encode value='<%= String.valueOf(dis.numShots) %>' context="html"/></td>
                    <td><carlos:encode value='<%= String.valueOf(dis.numMonths) %>' context="html"/></td>
                    <td><carlos:encode value='<%= StringUtils.defaultString(dis.lastDate) %>' context="html"/></td>

                    <%}%>
                    <%
                        String providerName=providerBean.getProperty(StringUtils.defaultString(demo.getProviderNo()), "");
                        providerName=StringUtils.trimToEmpty(providerName);
                    %>
                    <td><carlos:encode value='<%= providerName %>' context="html"/></td>
                </tr>
                <%}%>
                </tbody>
            </table>
            <div style="margin-bottom:10px;"></div>
        </form>

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
