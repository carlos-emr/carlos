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
<%@page import="org.apache.commons.lang3.StringUtils" %>
<%@page import="io.github.carlos_emr.carlos.demographic.data.*,java.util.*, java.text.SimpleDateFormat,io.github.carlos_emr.carlos.prevention.*,io.github.carlos_emr.carlos.providers.data.*,io.github.carlos_emr.carlos.util.*,io.github.carlos_emr.carlos.report.data.*,io.github.carlos_emr.carlos.prevention.pageUtil.*,java.net.*,io.github.carlos_emr.carlos.eform.*" %>
<%@page import="io.github.carlos_emr.OscarProperties"%>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils"%>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.prevention.pageUtil.PreventionReportDisplay" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptSearchData" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DemographicExt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_prevention" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_prevention");%>
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
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarprevention.index.oscarpreventiontitre"/></title>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1">
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/prototype.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/sortable.js"></script>

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
    </head>

    <body>
    <div class="container">
    <div class="searchBox">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" class="page-header-icon">
                    <path d="M1 2.5A1.5 1.5 0 0 1 2.5 1h3A1.5 1.5 0 0 1 7 2.5v3A1.5 1.5 0 0 1 5.5 7h-3A1.5 1.5 0 0 1 1 5.5zM2.5 2a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zm6.5.5A1.5 1.5 0 0 1 10.5 1h3A1.5 1.5 0 0 1 15 2.5v3A1.5 1.5 0 0 1 13.5 7h-3A1.5 1.5 0 0 1 9 5.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zM1 10.5A1.5 1.5 0 0 1 2.5 9h3A1.5 1.5 0 0 1 7 10.5v3A1.5 1.5 0 0 1 5.5 15h-3A1.5 1.5 0 0 1 1 13.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zm6.5.5A1.5 1.5 0 0 1 10.5 9h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-3A1.5 1.5 0 0 1 9 13.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5z"/>
                </svg>
                &nbsp;Prevention Reporting
            </h4>
        </div>

        <form action="${pageContext.request.contextPath}/oscarPrevention/PreventionReport.do" method="get">
            <table class="table table-sm" style="font-size:13px; margin-bottom:10px;">
                <tr>
                    <td style="width:180px;">Patient Demographic Query</td>
                    <td>
                        <select name="patientSet" id="patientSet" class="form-control form-control-sm" style="width:auto;display:inline-block">
                            <option value="-1" <%=("-1".equals(patientSet) || patientSet == null) ? "selected" : ""%>>--Select Query--</option>
                            <%
                                for (int i = 0; i < queryArray.size(); i++) {
                                    RptSearchData.SearchCriteria sc = (RptSearchData.SearchCriteria) queryArray.get(i);
                                    String qId = sc.id;
                                    String qName = sc.queryName;
                                    String selected = (patientSet != null && patientSet.equals(qId)) ? "selected" : "";
                            %>
                            <option value="<%=Encode.forHtmlAttribute(qId)%>" <%=selected%>><%=Encode.forHtmlContent(qName)%></option>
                            <%}%>
                        </select>
                    </td>
                </tr>
                <tr>
                    <td>Prevention</td>
                    <td>
                        <select name="prevention" id="prevention" class="form-control form-control-sm" style="width:auto;display:inline-block">
                            <option value="-1" <%=("-1".equals(prevention) || prevention == null) ? "selected" : ""%>>--Select Prevention--</option>
                            <option value="Mammogram" <%="Mammogram".equals(prevention) ? "selected" : ""%>>Breast</option>
                            <option value="PAP" <%="PAP".equals(prevention) ? "selected" : ""%>>Cervical</option>
                            <option value="ChildImmunizations" <%="ChildImmunizations".equals(prevention) ? "selected" : ""%>>Child Immunizations</option>
                            <option value="FOBT" <%="FOBT".equals(prevention) ? "selected" : ""%>>Colorectal</option>
                            <option value="Flu" <%="Flu".equals(prevention) ? "selected" : ""%>>Flu</option>
                        </select>
                    </td>
                </tr>
                <tr>
                    <td>As of</td>
                    <td>
                        <input type="text" name="asofDate" size="9" id="asofDate" class="form-control form-control-sm" style="width:auto;display:inline-block" value="<%=Encode.forHtmlAttribute(asofDate)%>"/>
                        <a id="date"><img title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
                    </td>
                </tr>
            </table>
            <div style="padding:0 0 10px 0;">
                <input type="submit" value="Run Report" class="btn btn-sm btn-primary"/>
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
            <input type="hidden" name="clinic_view" value="<%=OscarProperties.getInstance().getProperty("clinic_view","")%>">

            <div style="background:#f5f5f5; padding:8px 15px; border:1px solid #ddd; border-radius:3px; margin-bottom:10px;">
                <span style="margin-right:15px;">Total patients: <strong><%=list.size()%></strong></span>
                <span style="margin-right:15px;">Ineligible: <strong><%=ineligible%></strong></span>
                <span style="margin-right:15px;">Up to Date: <strong><%=done%> = <%=percentage%>%</strong></span>
                <span style="margin-right:15px;"><%=Encode.forHtml(String.valueOf(request.getAttribute("patientSet")))%></span>
            </div>

            <table id="preventionTable" class="sortable table table-sm table-bordered" style="font-size:12px;">
                <thead>
                <tr>
                    <th class="unsortable">#</th>
                    <th>DemoNo</th>
                    <th>DOB</th>
                    <th>Age as of<br/><%=UtilDateUtilities.DateToString(asDate)%></th>
                    <th>Sex</th>
                    <th>Lastname</th>
                    <th>Firstname</th>
                    <%if (type != null) { %>
                    <th>Guardian</th>
                    <%}%>
                    <th>Phone</th>
                    <th>Email</th>
                    <th>Address</th>
                    <th>Next Appt.</th>
                    <th>Status</th>
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

                        if (dis.state != null && dis.state.equals("Overdue")) {
                            overDueList.add(dis.demographicNo);
                        }
                %>
                <tr>
                    <td><%=i+1%></td>
                    <td>
                        <a href="javascript: return false;" onClick="popup(724,964,'<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=<%=Encode.forHtmlAttribute(dis.demographicNo.toString())%>&amp;displaymode=edit&amp;dboperation=search_detail','MasterDemographic')"><%=Encode.forHtml(dis.demographicNo.toString())%></a>
                    </td>
                    <td><%=DemographicData.getDob(demo,"-")%></td>

                    <%if (type == null) { %>
                    <td><%=demo.getAgeAsOf(asDate)%></td>
                    <td><%=Encode.forHtmlContent(h.get("sex"))%></td>
                    <td><%=Encode.forHtmlContent(h.get("lastName"))%></td>
                    <td><%=Encode.forHtmlContent(h.get("firstName"))%></td>
                    <td><%
                        String hExt = demo.getExtraValue(DemographicExt.DemographicProperty.hPhoneExt);
                        String wExt = demo.getExtraValue(DemographicExt.DemographicProperty.wPhoneExt);
                        if (!demo.getPhone().isEmpty()) { %>H: <%=Encode.forHtmlContent(demo.getPhone())%><%= !hExt.isEmpty() ? " x" + Encode.forHtmlContent(hExt) : "" %><%
                        }
                        if (!demo.getPhone2().isEmpty()) { %><br/>W: <%=Encode.forHtmlContent(demo.getPhone2())%><%= !wExt.isEmpty() ? " x" + Encode.forHtmlContent(wExt) : "" %><%
                        }
                        if (!demo.getCellPhone().isEmpty()) { %><br/>C: <%=Encode.forHtmlContent(demo.getCellPhone())%><%
                        }
                    %></td>
                    <td><%=Encode.forHtmlContent(demo.getEmail())%></td>
                    <td><%=Encode.forHtmlContent(demo.getAddress())+" "+Encode.forHtmlContent(demo.getCity())+" "+Encode.forHtmlContent(demo.getProvince())+" "+Encode.forHtmlContent(demo.getPostal())%></td>
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
%><span class="badge <%=labelClass%>"><%=Encode.forHtml(dis.state)%></span></td>
                    <td><%=Encode.forHtml(String.valueOf(dis.numMonths))%></td>
                    <td><%=Encode.forHtml(dis.lastDate)%></td>

                    <% } else {
                        Demographic demoSDM = demoData.getSubstituteDecisionMaker(LoggedInInfo.getLoggedInInfoFromSession(request), dis.demographicNo.toString());%>
                    <td><%=demo.getAgeAsOf(asDate)%></td>
                    <td><%=Encode.forHtmlContent(h.get("sex"))%></td>
                    <td><%=Encode.forHtmlContent(h.get("lastName"))%></td>
                    <td><%=Encode.forHtmlContent(h.get("firstName"))%></td>
                    <td><%=demoSDM == null ? "" : Encode.forHtmlContent(demoSDM.getLastName())%><%=demoSDM == null ? "" : ","%> <%= demoSDM == null ? "" : Encode.forHtmlContent(demoSDM.getFirstName()) %>&nbsp;</td>
                    <td><%=demoSDM == null ? "" : Encode.forHtmlContent(demoSDM.getPhone())%>&nbsp;</td>
                    <td><%=demoSDM == null ? "" : Encode.forHtmlContent(demoSDM.getEmail())%>&nbsp;</td>
                    <td><%=demoSDM == null ? "" :Encode.forHtmlContent(demoSDM.getAddress())%> <%=demoSDM == null ? "" : Encode.forHtmlContent(demoSDM.getCity())%> <%=demoSDM == null ? "" : Encode.forHtmlContent(demoSDM.getProvince())%> <%=demoSDM == null ? "" : Encode.forHtmlContent(demoSDM.getPostal())%>&nbsp;</td>
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
%><span class="badge <%=labelClass%>"><%=Encode.forHtml(dis.state)%></span></td>
                    <td><%=Encode.forHtml(String.valueOf(dis.numShots))%></td>
                    <td><%=Encode.forHtml(String.valueOf(dis.numMonths))%></td>
                    <td><%=Encode.forHtml(dis.lastDate)%></td>

                    <%}%>
                    <%
                        String providerName=providerBean.getProperty(demo.getProviderNo());
                        providerName=StringUtils.trimToEmpty(providerName);
                    %>
                    <td><%=Encode.forHtml(providerName)%></td>
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

