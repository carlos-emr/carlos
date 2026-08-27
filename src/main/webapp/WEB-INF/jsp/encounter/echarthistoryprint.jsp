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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>



<%@page
        import="io.github.carlos_emr.carlos.encounter.data.*,io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean, java.net.*" %>
<jsp:useBean id="providerBean" class="java.util.Properties"
             scope="session"/>

<link rel="stylesheet" type="text/css" media="print" href="print.css"/>
<link rel="stylesheet" type="text/css" href="encounterPrintStyles.css"/>
<%
    //The encounter session manager, if the session bean is not in the context it looks for a session cookie with the appropriate name and value, if the required cookie is not available
    //it dumps you out to an erros page.

    EctSessionBean bean = new EctSessionBean();
    if ((bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean")) == null) {
        response.sendRedirect(request.getContextPath() + "/encounter/ViewError");
        return;
    }
    bean.setUpEncounterPage(LoggedInInfo.getLoggedInInfoFromSession(request), request.getParameter("echartid"), request.getParameter("demographic_no"));
%>

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
                    style="font-weight: bold;"><carlos:encode value='<%= bean.patientLastName %>' context="html"/>, <carlos:encode value='<%= bean.patientFirstName %>' context="html"/>
		<carlos:encode value='<%= bean.patientSex %>' context="html"/> <carlos:encode value='<%= bean.patientAge %>' context="html"/></span></td>
            <td style="text-align: right; height: 34px;"><span
                    style="font-weight: bold;">Dr. <carlos:encode value='<%= providerBean.getProperty(bean.familyDoctorNo) %>' context="html"/></span>
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
                                            style="font-size: 8pt;"><carlos:encode value='<%= bean.socialHistory %>' context="html"/>&nbsp;</pre>
                                    </td>
                                    <td valign="top" class="TableWithBorder"><pre
                                            name='fhTextarea'
                                            style="font-size: 8pt;"><carlos:encode value='<%= bean.familyHistory %>' context="html"/>&nbsp;</pre>
                                    </td>
                                    <td valign="top" class="TableWithBorder"><pre
                                            name='mhTextarea'
                                            style="font-size: 8pt;"><carlos:encode value='<%= bean.medicalHistory %>' context="html"/>&nbsp;</pre>
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
                                            style="font-size: 8pt;"><carlos:encode value='<%= bean.ongoingConcerns %>' context="html"/>&nbsp;</pre>
                                    </td>
                                    <td valign="top" class="TableWithBorder"><pre
                                            name='reTextarea' style="font-size: 8pt;"><carlos:encode value='<%= bean.reminders %>' context="html"/>&nbsp;</pre>
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
                                        <pre name='enTextarea' style="font-size: 8pt;"><carlos:encode value='<%= bean.encounter %>' context="html"/></pre>
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
