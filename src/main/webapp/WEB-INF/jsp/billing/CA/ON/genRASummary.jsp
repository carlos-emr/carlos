<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Purpose: Supports genRASummary in the Ontario billing workflow.
  Expected request model data includes: raSummaryModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<html>
<head>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <link rel="stylesheet" href="billing.css">
    <title>Billing Reconcilliation</title>
</head>

<body bgcolor="#EBF4F5" text="#000000" leftmargin="0" topmargin="0"
      marginwidth="0" marginheight="0">

<c:if test="${raSummaryModel.raFileIncomplete}">
    <div class="alert alert-warning" role="alert">
        <carlos:encode value="${raSummaryModel.raFileWarning}" context="html"/>
    </div>
</c:if>

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
            <form action="${pageContext.request.contextPath}/billing/CA/ON/ViewGenRASummary" method="post">
                <input type="hidden" name="rano" value="<carlos:encode value="${raSummaryModel.raNo}" context="htmlAttribute"/>">
                <select name="proNo">
                    <c:forEach var="__opt" items="${raSummaryModel.providerOptions}">
                    <option value="<carlos:encode value='${__opt.ohipNo}' context='htmlAttribute'/>" <c:if test="${__opt.selected}">selected="selected"</c:if>><carlos:encode value="${__opt.displayName}" context="html"/></option>
                    </c:forEach>
                </select>
                <input type="submit" name="submit" value="Generate">
                <button type="submit" name="submit" value="Detail"
                        formaction="${pageContext.request.contextPath}/billing/CA/ON/ViewGenRASummaryDetail"
                        formmethod="post"
                        style="background:none;border:none;color:#cccccc;text-decoration:underline;cursor:pointer;padding:0 0 0 8px;font:inherit;">
                    Detail
                </button>
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
    <%-- Per-category clinic / hospital cell rendering is encapsulated in
         ReportRow.getClinicCell() / .getHospitalCell() — the legacy switch
         on category() now lives on the record itself. --%>
    <c:forEach var="__row" items="${raSummaryModel.rows}">
    <tr>
        <td height="16"><carlos:encode value="${__row.billingNo}" context="html"/></td>
        <td height="16"><carlos:encode value="${__row.providerName}" context="html"/></td>
        <td height="16"><carlos:encode value="${__row.demoName}" context="html"/></td>
        <td height="16"><carlos:encode value="${__row.demoHin}" context="html"/></td>
        <td height="16"><carlos:encode value="${__row.serviceDate}" context="html"/></td>
        <td height="16"><carlos:encode value="${__row.serviceCode}" context="html"/></td>
        <td height="16" align=right><carlos:encode value="${__row.invoicedAmount}" context="html"/></td>
        <td height="16" align=right><carlos:encode value="${__row.paidAmount}" context="html"/></td>
        <td height="16" align=right><carlos:encode value="${__row.clinicCell}" context="html"/></td>
        <td height="16" align=right><carlos:encode value="${__row.hospitalCell}" context="html"/></td>
        <td height="16" align=right><carlos:encode value="${__row.obAmount}" context="html"/></td>
        <td height="16" align=right><carlos:encode value="${__row.errorCode}" context="html"/></td>
    </tr>
    </c:forEach>
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
