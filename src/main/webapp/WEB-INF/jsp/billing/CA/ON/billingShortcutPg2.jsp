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
<%@page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@page import="org.owasp.encoder.Encode" %>
<%@page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingShortcutPg2ViewModel" %>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>

<%
    // BillingShortcutPg2Save2Action enforces _billing w and assembles the
    // view model with the 6 DAO lookups + calculation + persistence the
    // legacy JSP body used to perform inline.
    BillingShortcutPg2ViewModel shortcutPg2Model =
            (BillingShortcutPg2ViewModel) request.getAttribute("shortcutPg2Model");
    if (shortcutPg2Model == null) {
        // Defensive fallback for any caller forwarding directly here. The
        // empty model renders a stub page; the action layer is the canonical
        // entry point.
        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                "billingShortcutPg2.jsp reached without shortcutPg2Model — caller "
              + "should route through billing/CA/ON/BillingShortcutPg2Save.");
        shortcutPg2Model = BillingShortcutPg2ViewModel.builder().build();
    }
    String demoname = request.getParameter("demographic_name");
%>

<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>CARLOS Billing</title>
    <script language="JavaScript">
        <!--
        function onSave() {
            var submitTypeString = document.forms[0].submitType.value;
            var ret = true;
            if (ret == true) {
                ret = confirm("Are you sure you want to " + submitTypeString + "?");
            }
            return ret;
        }

        //-->
    </script>
</head>

<body topmargin="0">

<table border="0" cellpadding="0" cellspacing="2" width="100%"
       bgcolor="#CCCCFF">
    <form method="post" name="titlesearch" action="<%= request.getContextPath() %>/billing/CA/ON/BillingShortcutPg2Save" onsubmit="return onSave();">
        <input type="hidden" value="" name="submitType"/>
        <tr>
            <td>
                <table border="0" cellspacing="0" cellpadding="0" width="100%">
                    <tr>
                        <td><b>Confirmation </b></td>
                        <td align="right"><input type="hidden" name="addition"
                                                 value="Confirm"/></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td>
                <table border="0" cellspacing="0" cellpadding="0" width="100%">
                    <tr bgcolor="#33CCCC">
                        <td nowrap bgcolor="#FFCC99" width="10%" align="center"><carlos:encode value='<%= demoname %>' context="html"/>
                            <carlos:encode value="${shortcutPg2Model.displaySex}" context="html"/>
                            DOB: <carlos:encode value="${shortcutPg2Model.demoDobYy}" context="html"/>/<carlos:encode value="${shortcutPg2Model.demoDobMm}" context="html"/>/<carlos:encode value="${shortcutPg2Model.demoDobDd}" context="html"/>
                            HIN: <carlos:encode value="${shortcutPg2Model.demoHin}" context="html"/>
                        </td>
                        <td bgcolor="#99CCCC" align="center"><%= shortcutPg2Model.getCombinedMsgs() %>
                        </td>
                    </tr>
                </table>

                <table border="1" cellspacing="0" cellpadding="0" width="100%"
                       bordercolorlight="#99A005" bordercolordark="#FFFFFF"
                       bgcolor="#FFFFFF">
                    <tr>
                        <td width="50%">

                            <table border="1" cellspacing="2" cellpadding="0" width="100%"
                                   bordercolorlight="#99A005" bordercolordark="#FFFFFF"
                                   bgcolor="ivory">
                                <tr>
                                    <td nowrap width="30%" align="center" valign="top"><b>Service
                                        Date</b><br>
                                        <%= request.getParameter("billDate") != null ? String.join("<br>", java.util.Arrays.stream(request.getParameter("billDate").split("\\n")).map(Encode::forHtml).toArray(String[]::new)) : "" %>
                                    </td>
                                    <td align="center" width="33%"><b>Diagnostic Code</b><br>
                                        <carlos:encode value='<%= StringUtils.noNull(request.getParameter("dxCode")) %>' context="html"/>
                                        <hr>
                                        <b>Cal.% mode</b><br>
                                        <carlos:encode value='<%= StringUtils.noNull(request.getParameter("rulePerc")) %>' context="html"/>
                                    </td>
                                    <td valign="top"><b>Refer.
                                        Doctor</b><br><carlos:encode value='<%= StringUtils.noNull(request.getParameter("referralDocName")) %>' context="html"/><br>
                                        <b>Refer. Doctor #</b><br><carlos:encode value='<%= StringUtils.noNull(request.getParameter("referralCode")) %>' context="html"/>
                                    </td>
                                </tr>
                            </table>

                        </td>
                        <td valign="top">

                            <table border="1" cellspacing="2" cellpadding="0" width="100%"
                                   bordercolorlight="#99A005" bordercolordark="#FFFFFF"
                                   bgcolor="#EEEEFF">
                                <tr>
                                    <td nowrap width="30%"><b>Billing Physician</b></td>
                                    <td width="20%"><carlos:encode value='<%= providerBean.getProperty(request.getParameter("xml_provider"), "") %>' context="html"/>
                                    </td>
                                    <td nowrap width="30%"><b>Assig. Physician</b></td>
                                    <td width="20%"><carlos:encode value='<%= providerBean.getProperty(request.getParameter("assgProvider_no") == null ? "" : request.getParameter("assgProvider_no"), "") %>' context="html"/>
                                    </td>
                                </tr>
                                <tr>

                                    <td width="30%"><b>Visit Type</b></td>
                                    <td width="20%"><carlos:encode value='<%= request.getParameter("xml_visittype") != null && request.getParameter("xml_visittype").contains("|") ? request.getParameter("xml_visittype").substring(request.getParameter("xml_visittype").indexOf("|") + 1) : "" %>' context="html"/>
                                    </td>

                                    <td width="30%"><b>Billing Type</b></td>
                                    <td width="20%"><carlos:encode value='<%= request.getParameter("xml_billtype") != null && request.getParameter("xml_billtype").contains("|") ? request.getParameter("xml_billtype").substring(request.getParameter("xml_billtype").indexOf("|") + 1) : "" %>' context="html"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td><b>Visit Location</b></td>
                                    <td colspan="3"><carlos:encode value='<%= request.getParameter("xml_location") != null && request.getParameter("xml_location").contains("|") ? request.getParameter("xml_location").substring(request.getParameter("xml_location").indexOf("|") + 1) : "" %>' context="html"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td><b>SLI Code</b></td>
                                    <td><%
                                        String xmlSlicodeRaw = request.getParameter("xml_slicode");
                                        String testSliCode = (xmlSlicodeRaw != null && xmlSlicodeRaw.contains("|")) ? xmlSlicodeRaw.substring(xmlSlicodeRaw.indexOf("|") + 1) : "";
                                        String clinicNoTrim = io.github.carlos_emr.CarlosProperties.getInstance().getProperty("clinic_no", "").trim();
                                        if (testSliCode.startsWith(clinicNoTrim)) {
                                    %>
                                        Not Applicable &nbsp;
                                        <%} else {%>
                                        <carlos:encode value='<%= testSliCode %>' context="html"/> &nbsp;
                                        <%}%>
                                    </td>
                                </tr>
                                <tr>
                                    <td><b>Admission Date</b></td>
                                    <td><carlos:encode value='<%= StringUtils.noNull(request.getParameter("xml_vdate")) %>' context="html"/>
                                    </td>
                                    <td colspan="2"></td>

                                </tr>
                            </table>


                        </td>
                    </tr>
                </table>

            </td>

        </tr>
        <tr>
            <td align="center">
                <table border="1" width="50%" bordercolorlight="#99A005"
                       bordercolordark="#FFFFFF">

                    <%-- Calculation HTML is pre-rendered by the assembler. The
                         JSP previously built the same string inline as it walked
                         the per-line and percent-code vectors. --%>
                    <%= shortcutPg2Model.getCalculationHtml() %>

                    <tr>

                        <td colspan='2' align='center' bgcolor="silver">
                            <input type="submit" name="button" value="Back to Edit"
                                   onclick="document.forms[0].submitType.value='Back to Edit'" style="width: 120px;"/>
                            <input type="submit" name="button" value="Save"
                                   onclick="document.forms[0].submitType.value='Save'" style="width: 120px;"/>
                            <input type="submit" name="button" value="Save and Back"
                                   onclick="document.forms[0].submitType.value='Save and Back'" style="width: 120px;"/>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>


        <%
            for (java.util.Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                String temp = e.nextElement().toString();
        %>
        <input type="hidden" name="<carlos:encode value='<%= temp %>' context="htmlAttribute"/>"
               value="<carlos:encode value='<%= StringUtils.noNull(request.getParameter(temp)) %>' context="htmlAttribute"/>">
        <%
            }
        %>
        <input type="hidden" name="hc_type" value="<carlos:encode value="${shortcutPg2Model.demoHcType}" context="htmlAttribute"/>">
        <input type="hidden" name="referralCode" value="<carlos:encode value="${shortcutPg2Model.referralDoctorOhip}" context="htmlAttribute"/>">
        <input type="hidden" name="sex" value="<carlos:encode value="${shortcutPg2Model.demoSex}" context="htmlAttribute"/>">
        <input type="hidden" name="proOHIPNO" value="<carlos:encode value="${shortcutPg2Model.providerOhipNo}" context="htmlAttribute"/>">
    </form>

</table>

<%-- Post-save navigation: action layer decides between "close popup"
     (Save button), redirect to pg1 (Save and Back / Next), or no-op
     (initial render or Back-to-Edit which is routed elsewhere). --%>
<% if (shortcutPg2Model.getPostSaveAction() == BillingShortcutPg2ViewModel.PostSaveAction.CLOSE_WINDOW) { %>
<script language="JavaScript"> self.close();</script>
<% } else if (shortcutPg2Model.getPostSaveAction() == BillingShortcutPg2ViewModel.PostSaveAction.REDIRECT_TO_PG1) { %>
<script language="JavaScript">window.location = '<%= SafeEncode.forJavaScript(shortcutPg2Model.getRedirectUrl()) %>';</script>
<% } %>

</body>
</html>
