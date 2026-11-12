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
    Prints the legacy encounter editor's chart: the patient's header, family doctor, history and
    the encounter of the revision the editor window shows. Parameters: demographicNo (required)
    and echartid (the window's revision; a missing revision or another patient's prints only the
    header). Requires the _eChart role, _eChart read for the patient and access to the record.
    @since 2026-10-16 (request-owned print bean, issue #4673)
--%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_eChart" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_eChart");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="io.github.carlos_emr.carlos.encounter.data.*,java.net.*" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>

<jsp:useBean id="providerBean" class="java.util.Properties"
             scope="session"/>
<% java.util.Properties oscarVariables = CarlosProperties.getInstance(); %>

<%
    // The legacy editor's Print button names its own patient and the revision its window shows.
    // This page printed the login-wide EctSessionBean, which follows whichever chart was entered
    // last in any window, so A's window printed B's name, doctor, history and encounter (#4673).
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String printedPatient;
    try {
        printedPatient = EncounterPatientContext.requireDemographicNo(request);
    } catch (IllegalArgumentException invalidPatient) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, invalidPatient.getMessage());
        return;
    }
    // The check above is for the _eChart role; this is the patient's chart.
    SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eChart", "r", printedPatient)
            || !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, Integer.valueOf(printedPatient))) {
        response.sendRedirect(request.getContextPath() + "/securityError?type=_eChart");
        return;
    }
    // A missing revision, or another patient's, prints only this patient's header.
    EctSessionBean bean = new EctSessionBean();
    bean.setUpEncounterPage(loggedInInfo, request.getParameter("echartid"), printedPatient);
%>

<link rel="stylesheet" type="text/css" media="print" href="print.css"/>
<link rel="stylesheet" type="text/css" href="encounterPrintStyles.css"/>

<script type="text/javascript" language="Javascript">
    function onPrint() {
        window.print();
        return true;
    }

    function onClose() {
        window.close();
        return true;
    }
</script>
<html>

    <body topmargin="0" leftmargin="0" vlink="#0000FF"
          onload="window.focus()">

    <% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %>

    <table class="Header" style="width: 7in">
        <tr>
            <td align="left"><input type="button"
                                    value="<fmt:message key="global.btnPrint"/>"
                                    onclick="javascript:return onPrint();"/> <input type="button"
                                                                                    value="<fmt:message key="global.btnClose"/>"
                                                                                    onclick="javascript:return onClose();"/>
            </td>
        </tr>
    </table>

    <table cellpadding="0" cellspacing="0"
           style="border-collapse: collapse; width: 7in; padding-left: 3px;">
        <tr>
            <td
                    style="text-align: left; height: 34px; border-left: 2px solid #A9A9A9; border-bottom: 2px solid #A9A9A9; border-top: 2px solid #A9A9A9;">
		<span style="font-weight: bold;"><carlos:encode value='<%= bean.patientLastName %>'/>, <carlos:encode value='<%= bean.patientFirstName %>'/>
		<carlos:encode value='<%= bean.patientSex %>'/> <carlos:encode value='<%= bean.patientAge %>'/></span></td>
            <td
                    style="text-align: right; height: 34px; border-bottom: 2px solid #A9A9A9; border-right: 2px solid #A9A9A9; border-top: 2px solid #A9A9A9;">
		<span style="font-weight: bold;"><fmt:message key="encounter.encounterPrint.msgDr"/>. <carlos:encode value='<%= providerBean.getProperty(bean.familyDoctorNo == null ? "" : bean.familyDoctorNo, "") %>'/></span>
            </td>
        </tr>
        <tr>
            <td colspan=2
                style="border-left: 2px solid #A9A9A9; border-right: 2px solid #A9A9A9; border-bottom: 2px solid #A9A9A9;"
                valign="top">
                <table width="100%">
                    <tr>
                        <td>
                            <table width="100%">
                                <tr>
                                    <td width="33%">
                                        <div class="RowTop"><fmt:message key="encounter.Index.socialFamHist"/>:
                                        </div>
                                    </td>
                                    <td width="33%">
                                        <div class="RowTop">
                                            <% if (oscarVariables.getProperty("otherMedications", "").length() > 1) {
                                                out.print(oscarVariables.getProperty("otherMedications", ""));
                                            %> <% } else { %> <fmt:message key="encounter.Index.otherMed"/>: <% } %>
                                        </div>
                                    </td>
                                    <td width="33%">
                                        <div class="RowTop">
                                            <% if (oscarVariables.getProperty("medicalHistory", "").length() > 1) {
                                                out.print(oscarVariables.getProperty("medicalHistory", ""));
                                            } else { %> <fmt:message key="encounter.Index.medHist"/>: <% } %>
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td valign="top" align="left" class="TableWithBorder"><pre
                                            name='shTextarea'
                                            style="font-size: 8pt;"><carlos:encode value='<%= bean.socialHistory %>'/>&nbsp;</pre>
                                    </td>
                                    <td valign="top" class="TableWithBorder"><pre
                                            name='fhTextarea'
                                            style="font-size: 8pt;"><carlos:encode value='<%= bean.familyHistory %>'/>&nbsp;</pre>
                                    </td>
                                    <td valign="top" class="TableWithBorder"><pre
                                            name='mhTextarea'
                                            style="font-size: 8pt;"><carlos:encode value='<%= bean.medicalHistory %>'/>&nbsp;</pre>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <table width="100%">
                                <tr>
                                    <td width="50%">
                                        <div class="RowTop">
                                            <% if (oscarVariables.getProperty("ongoingConcerns", "").length() > 1) {
                                                out.print(oscarVariables.getProperty("ongoingConcerns", ""));
                                            } else { %> <fmt:message key="encounter.encounterPrint.msgOngCon"/>:
                                        </div>
                                        <% } %>
                                    </td>

                                    <td width="50%">
                                        <div class="RowTop"><fmt:message key="encounter.encounterPrint.msgReminders"/>:
                                        </div>
                                    </td>
                                </tr>
                                <tr width="100%">
                                    <td valign="top" class="TableWithBorder"><pre
                                            name='ocTextarea'
                                            style="font-size: 8pt;"><carlos:encode value='<%= bean.ongoingConcerns %>'/>&nbsp;</pre>
                                    </td>
                                    <td valign="top" class="TableWithBorder"><pre
                                            name='reTextarea' style="font-size: 8pt;"><carlos:encode value='<%= bean.reminders %>'/>&nbsp;</pre>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <!--encounter row-->
                    <tr>
                        <td>
                            <table width="100%">
                                <tr>
                                    <td>
                                        <div class="RowTop"><fmt:message key="encounter.encounterPrint.msgEncounter"/>:
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td class="TableWithBorder" valign="top" style="text-align: left">
                                        <pre name='enTextarea'
                                             style="font-size: 8pt;"><carlos:encode value='<%= bean.encounter %>' context="html"/></pre>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <!----End new rows here-->
                </table>
            </td>
        </tr>
    </table>

    <table class="Header" style="width: 7in">
        <tr>
            <td align="left"><input type="button"
                                    value="<fmt:message key="global.btnPrint"/>"
                                    onclick="javascript:return onPrint();"/> <input type="button"
                                                                                    value="<fmt:message key="global.btnClose"/>"
                                                                                    onclick="javascript:return onClose();"/>
            </td>
        </tr>
    </table>

    </body>
</html>
