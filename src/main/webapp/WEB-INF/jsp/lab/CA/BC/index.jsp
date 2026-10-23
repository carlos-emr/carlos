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
<security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_lab");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@page import="io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Obr" %>
<%@page import="io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Pid" %>
<%@page import="java.util.List" %>
<%@page import="java.sql.ResultSet" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7LinkDao" %>
<%@page import="io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Link" %>
<%@ page import="io.github.carlos_emr.Misc" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%

    Hl7LinkDao linkDao = SpringUtils.getBean(Hl7LinkDao.class);

%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>CARLOS PathNET - Patient Linking</title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/share/css/oscar.css">
        <script language="JavaScript">
            var demo;
            var lab;
            window.name = 'LabIndex';

            function PopupDemo(pid) {
                var ctx = '<%= request.getContextPath() %>';
                demo = window.open(ctx + '/lab/CA/BC/ViewDemoSelect?pid=' + encodeURIComponent(pid), 'Patients', 'height=500,width=750,scrollbars=1,toolbar=0,status=1,menubar=0,location=0,directories=0,resizable=1');
                demo.focus();
                return false;
            }

            function selectPathnetPatient(pid, demographic) {
                var form = document.getElementById('patientLinkForm');
                form.elements.pid.value = pid;
                form.elements.demo_id.value = demographic;
                form.requestSubmit();
            }

            function PopupLab(pid) {
                lab = window.open('<%= request.getContextPath() %>/lab/CA/BC/ViewReport?pid=' + pid, 'Lab', 'height=500,width=900,scrollbars=1,toolbar=0,status=1,menubar=0,location=0,directories=0,resizable=1');
                lab.focus();
                return false;
            }
        </script>
    </head>
    <body>
    <form id="patientLinkForm" action="<%= request.getContextPath() %>/lab/CA/BC/ViewIndex" method="post">
        <input type="hidden" name="pid" value=""/>
        <input type="hidden" name="demo_id" value=""/>
        <table width="100%" border="0" cellspacing="0" cellpadding="0"
               bgcolor="#D3D3D3">
            <tr>
                <td height="40" width="25"></td>
                <td width="50%" align="left"><font color="#4D4D4D"><b><font
                        size="4">oscar<font size="3">PathNET - Patient Linking</font></font></b></font>
                </td>
                <td class="Text" align="right"><a href="<%= request.getContextPath() %>/lab/CA/BC/ViewSearchReports">Search
                    Lab Reports</a>&nbsp;
                </td>
            </tr>
        </table>
        <table width="100%" cellspacing="2" cellpadding="2">
            <tr bgcolor="E6E6E6">
                <td class="Text" align="center" colspan="6"
                    style="border-right: #464646 1px solid;">Lab Information
                </td>
                <td class="Text" align="center" colspan="3"
                    style="border-left: #464646 1px solid;">Demographic Information
                </td>
            </tr>
            <tr>
                <td class="Header">Valid</td>
                <td class="Header">Patient</td>
                <td class="Header">BirthDate</td>
                <td class="Header">Gender</td>
                <td class="Header">Ordered By</td>
                <td class="Header" style="border-right: #464646 1px solid;">Copies
                    To
                </td>
                <td class="Header" style="border-left: #464646 1px solid;">Demo.
                    No.
                </td>
                <td class="Header">Name</td>
                <td class="Header">BirthDate</td>
            </tr>
            <%
                boolean other = true;
                for (Object[] o : linkDao.findLabs()) {
                    Hl7Pid pid = (Hl7Pid) o[0];
                    Hl7Link link = (Hl7Link) o[1];
                    Hl7Obr obr = (Hl7Obr) o[2];
                    Demographic demo = (Demographic) o[3];
            %>
            <tr class="<%=(other? "WhiteBG" : "LightBG")%>">
                <td class="Text"><input type="checkbox" name="chk"
                                        value="<%=pid.getId()%>" <%=demo == null ? "disabled" : ""%>/></td>
                <td class="Text"><a href="#"
                                    onclick="return PopupLab('<%=pid.getId()%>');"><%=Encode.forHtml(Misc.check(pid.getPatientName(), ""))%>
                </a></td>
                <td class="Text">
                    <%=ConversionUtils.toDateString(pid.getDateOfBirth())%>
                </td>
                <td class="Text" align="center">
                    <%=Encode.forHtml(Misc.check(pid.getSex(), ""))%>
                </td>
                <td class="Text">
                    <%=Encode.forHtml(obr == null ? "" : Misc.check(obr.getOrderingProvider(), ""))%>
                </td>
                <td class="Text" style="border-right: #464646 1px solid;">
                    <%=Encode.forHtml(obr == null ? "" : Misc.check(obr.getResultCopiesTo(), ""))%>
                </td>
                <td class="Text" style="border-left: #464646 1px solid;">
                    <a href="#" onclick="return PopupDemo('<%=pid.getId()%>');">
                        <%=demo == null ? "Select patient" : demo.getDemographicNo()%>
                    </a>
                </td>
                <td class="Text">
                    <%= demo == null ? "" : Encode.forHtml(demo.getFullName())%>
                </td>
                <td class="Text">
                    <%=    demo == null ? "" : Encode.forHtml(demo.getBirthDayAsString()) %>
                </td>
            </tr>
            <%
                    other = !other;
                }
            %>
            <tr>
                <td colspan="9" align="left"><input type="submit" name="submit"
                                                    value="Link"/></td>
            </tr>
        </table>
    </form>
    </body>
</html>
