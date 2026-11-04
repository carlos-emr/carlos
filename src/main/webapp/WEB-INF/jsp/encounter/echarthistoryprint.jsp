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
    Prints one revision of a patient's legacy chart: the split chart, the eChart history report and
    the messenger's encounter attachment open it with echartid and demographic_no. A revision that
    is missing or belongs to another patient prints only the patient's header. Requires _eChart read
    for the patient and access to the patient's record.
    @since 2026-10-16 (request-owned print bean, issue #4673)
--%>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@page import="io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>



<%@page
        import="io.github.carlos_emr.carlos.encounter.data.*,io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean, java.net.*" %>
<jsp:useBean id="providerBean" class="java.util.Properties"
             scope="session"/>
<%
    // The patient and revision this request names, loaded into a request-owned bean. This page
    // used to load them into the login-wide EctSessionBean, replacing the entry of whichever chart
    // was entered last in any window, and failed when this login had entered no chart (#4673).
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String printedPatient;
    try {
        printedPatient = EncounterPatientContext.requireDemographicNo(request);
    } catch (IllegalArgumentException invalidPatient) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, invalidPatient.getMessage());
        return;
    }
    // The gate checked only the _eChart role; this is the patient's chart.
    SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eChart", "r", printedPatient)
            || !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, Integer.valueOf(printedPatient))) {
        response.sendRedirect(request.getContextPath() + "/securityError?type=_eChart");
        return;
    }
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
    <body topmargin="0" leftmargin="0" vlink="#0000FF">
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

    <table class="Header">
        <tr>
            <td align="left"><input type="button" value="<fmt:message key='global.btnPrint'/>"
                                    onclick="javascript:return onPrint();"/> <input type="button"
                                                                                    value="<fmt:message key='global.btnClose'/>"
                                                                                    onclick="javascript:return onClose();"/>
            </td>
        </tr>
    </table>

    <table cellpadding="0" cellspacing="0"
           style="border-collapse: collapse; width: 7in; padding-left: 3px;">
        <tr>
            <td style="text-align: left; height: 34px;"><span
                    style="font-weight: bold;"><carlos:encode value='<%= bean.patientLastName %>'/>, <carlos:encode value='<%= bean.patientFirstName %>'/>
		<carlos:encode value='<%= bean.patientSex %>'/> <carlos:encode value='<%= bean.patientAge %>'/></span></td>
            <td style="text-align: right; height: 34px;"><span
                    style="font-weight: bold;">Dr. <carlos:encode value='<%= providerBean.getProperty(bean.familyDoctorNo == null ? "" : bean.familyDoctorNo, "") %>'/></span>
            </td>
        </tr>
    </table>
    <table cellpadding="0" cellspacing="0"
           style="border-collapse: collapse; width: 7in; padding-left: 3px;">
        <tr>
            <td colspan="2"
                style="border-left: 2px solid #A9A9A9; border-right: 2px solid #A9A9A9; border-bottom: 2px solid #A9A9A9;"
                valign="top">
                <table width="100%">
                    <tr>
                        <td>
                            <table width="100%">
                                <tr>
                                    <td width="33%">
                                        <div class="RowTop"><fmt:message key="encounter.echarthistoryprint.socialHistory"/></div>
                                    </td>
                                    <td width="33%">
                                        <div class="RowTop"><fmt:message key="encounter.echarthistoryprint.familyHistory"/></div>
                                    </td>
                                    <td width="33%">
                                        <div class="RowTop"><fmt:message key="encounter.echarthistoryprint.medicalHistory"/></div>
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
                                        <div class="RowTop"><fmt:message key="encounter.echarthistoryprint.ongoingConcerns"/></div>
                                    </td>

                                    <td width="50%">
                                        <div class="RowTop"><fmt:message key="encounter.echarthistoryprint.reminders"/></div>
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
                                    <td width=100%>
                                        <div class="RowTop"><fmt:message key="encounter.echarthistoryprint.encounter"/></div>
                                    </td>
                                </tr>
                                <tr>
                                    <td class="TableWithBorder" valign="top" style="text-align: left"
                                        width=100%>
                                        <pre name='enTextarea' style="font-size: 8pt;"><carlos:encode value='<%= bean.encounter %>'/></pre>
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

    <table class="Header">
        <tr>
            <td align="left"><input type="button" value="<fmt:message key='global.btnPrint'/>"
                                    onclick="javascript:return onPrint();"/> <input type="button"
                                                                                    value="<fmt:message key='global.btnClose'/>"
                                                                                    onclick="javascript:return onClose();"/>
            </td>
        </tr>
    </table>

    </body>
</html>
