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
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@page import="io.github.carlos_emr.carlos.billings.ca.on.data.GenRASummaryViewModel" %>

<%
    // ViewGenRASummary2Action enforces _billing w + POST-only and assembles
    // the view model with the 5 DAO lookups + RA-detail iteration + RA-header
    // content merge the legacy JSP body used to perform inline.
    GenRASummaryViewModel raSummaryModel =
            (GenRASummaryViewModel) request.getAttribute("raSummaryModel");
    if (raSummaryModel == null) {
        // Defensive fallback: any caller that forwards directly here gets a
        // safe stub render. The canonical entrypoint is
        // billing/CA/ON/ViewGenRASummary.
        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                "genRASummary.jsp reached without raSummaryModel — caller should "
              + "route through billing/CA/ON/ViewGenRASummary.");
        raSummaryModel = GenRASummaryViewModel.builder().build();
    }
%>

<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <link rel="stylesheet" href="billing.css">
    <title>Billing Reconcilliation</title>
</head>

<body bgcolor="#EBF4F5" text="#000000" leftmargin="0" topmargin="0"
      marginwidth="0" marginheight="0">

<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr bgcolor="#486ebd">
        <th align='LEFT'><input type='button' name='print' value='Print'
                                onClick='window.print(); return false;'></th>
        <th><font face="Arial, Helvetica, sans-serif" color="#FFFFFF">
            Billing Reconcilliation - Payment Summary</font></th>
        <th align='RIGHT'><input type='button' name='close' value='Close'
                                 onClick='window.close()'></th>
    </tr>
</table>

<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr bgcolor="#333333">
        <th align='CENTRE' nowrap>
            <form action="<%= request.getContextPath() %>/billing/CA/ON/ViewGenRASummary" method="post">
                <input type="hidden" name="rano" value="<carlos:encode value="${raSummaryModel.raNo}" context="htmlAttribute"/>">
                <select name="proNo">
                    <% for (GenRASummaryViewModel.ProviderOption __opt : raSummaryModel.getProviderOptions()) {
                            String __sel = __opt.selected() ? " selected=\"selected\"" : "";
                    %>
                    <option value="<carlos:encode value='<%= __opt.ohipNo() %>' context="htmlAttribute"/>"<%= __sel %>><carlos:encode value='<%= __opt.displayName() %>' context="html"/></option>
                    <% } %>
                </select>
                <input type="submit" name="submit" value="Generate">
                <a href="<%= request.getContextPath() %>/billing/CA/ON/ViewGenRASummaryDetail?rano=<carlos:encode value="${raSummaryModel.raNo}" context="uriComponent"/>&proNo=">Detail</a>
            </form>
        </th>
    </tr>
</table>

<table width="100%" border="1" cellspacing="0" cellpadding="0"
       bgcolor="#EFEFEF">
    <tr>
        <td width="7%" height="16">Billing No</td>
        <td width="14%" height="16">Provider</td>
        <td width="15%" height="16">Patient</td>
        <td width="7%" height="16">HIN</td>
        <td width="10%" height="16">Service Date</td>
        <td width="7%" height="16">Service Code</td>
        <td width="7%" height="16" align=right>Invoiced</td>
        <td width="7%" height="16" align=right>Paid</td>
        <td width="7%" height="16" align=right>Clinic Pay</td>
        <td width="7%" height="16" align=right>Hospital Pay</td>
        <td width="7%" height="16" align=right>OB</td>
        <td width="5%" height="16" align=right>Error</td>
    </tr>
    <% for (GenRASummaryViewModel.ReportRow __row : raSummaryModel.getRows()) {
            // Per-category render decides which "Pay" column shows the value
            // and which shows N/A. Mirrors the three legacy <tr> blocks.
            String __clinicCell;
            String __hospitalCell;
            switch (__row.category()) {
                case HOSPITAL -> { __clinicCell = "N/A"; __hospitalCell = __row.paidAmount(); }
                case LOCAL_CLINIC -> { __clinicCell = __row.paidAmount(); __hospitalCell = "N/A"; }
                default -> { __clinicCell = "N/A"; __hospitalCell = "N/A"; }
            }
    %>
    <tr>
        <td height="16"><carlos:encode value='<%= __row.billingNo() %>' context="html"/></td>
        <td height="16"><carlos:encode value='<%= __row.providerName() %>' context="html"/></td>
        <td height="16"><carlos:encode value='<%= __row.demoName() %>' context="html"/></td>
        <td height="16"><carlos:encode value='<%= __row.demoHin() %>' context="html"/></td>
        <td height="16"><carlos:encode value='<%= __row.serviceDate() %>' context="html"/></td>
        <td height="16"><carlos:encode value='<%= __row.serviceCode() %>' context="html"/></td>
        <td height="16" align=right><carlos:encode value='<%= __row.invoicedAmount() %>' context="html"/></td>
        <td height="16" align=right><carlos:encode value='<%= __row.paidAmount() %>' context="html"/></td>
        <td height="16" align=right><carlos:encode value='<%= __clinicCell %>' context="html"/></td>
        <td height="16" align=right><carlos:encode value='<%= __hospitalCell %>' context="html"/></td>
        <td height="16" align=right><carlos:encode value='<%= __row.obAmount() %>' context="html"/></td>
        <td height="16" align=right><carlos:encode value='<%= __row.errorCode() %>' context="html"/></td>
    </tr>
    <% } %>
    <tr bgcolor='#FFFF3E'>
        <td height="16"></td>
        <td height="16"></td>
        <td height="16"></td>
        <td height="16"></td>
        <td height="16"></td>
        <td height="16">Total</td>
        <td height="16" align=right><carlos:encode value="${raSummaryModel.invoicedTotal}" context="html"/></td>
        <td height="16" align=right><carlos:encode value="${raSummaryModel.paidTotal}" context="html"/></td>
        <td height="16" align=right><carlos:encode value="${raSummaryModel.clinicPayTotal}" context="html"/></td>
        <td height="16" align=right><carlos:encode value="${raSummaryModel.hospitalPayTotal}" context="html"/></td>
        <td height="16" align=right><carlos:encode value="${raSummaryModel.obTotal}" context="html"/></td>
        <td height="16"></td>
    </tr>
</table>

</body>
</html>
