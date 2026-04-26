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
<%--
    Ontario Billing History (service-code-filtered) popup. Posts to
    ViewBillingONHistorySpec which builds a BillingONHistorySpecViewModel
    via BillingONHistorySpecDataAssembler and exposes it as
    ${historySpecModel}.

    @since 2006
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<!DOCTYPE html>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<html>
<head>
    <title>BILLING HISTORY</title>
    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet"> <!-- Bootstrap -->
    <script src="${pageContext.request.contextPath}/js/global.js"></script>
    <script language="JavaScript">
        function upCaseCtrl(ctrl) {
            ctrl.value = ctrl.value.toUpperCase();
        }
    </script>
</head>
<body>

<table style="width:100%">
    <tr class="myDarkGreen">
        <th>BILLING HISTORY</th>
    </tr>
</table>

<form method="post" name="titlesearch" action="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingONHistorySpec">
    <table style="width:95%; margin:auto;">
        <tr>
            <td style="text-align:left"><carlos:encode value="${historySpecModel.demoName}" context="html"/>
                (<carlos:encode value="${historySpecModel.demographicNo}" context="html"/>)
                <carlos:encode value="${historySpecModel.todayStr} - ${historySpecModel.startDayStr}" context="html"/>
            </td>
            <td style="text-align:right">Service Code <input type="text"
                                                             name="serviceCode"
                                                             value="<carlos:encode value='${historySpecModel.serviceCodeFilter}' context='htmlAttribute'/>" maxlength="5"
                                                             onBlur="upCaseCtrl(this)"/> <input type="hidden" name="day"
                                                                                                value="<carlos:encode value='${historySpecModel.day}' context='htmlAttribute'/>"/>
                <input type="hidden" name="demo_name"
                       value="<carlos:encode value='${historySpecModel.demoName}' context='htmlAttribute'/>"/> <input
                        type="hidden" name="demographic_no"
                        value="<carlos:encode value='${historySpecModel.demographicNo}' context='htmlAttribute'/>"/> <input
                        type="submit" name="submit" value="Search"/></td>
        </tr>
    </table>
</form>

<table style="width:95%; margin:auto;" class="table table-striped table-sm">
    <thead>
    <tr class="myYellow">
        <th style="text-align:center; white-space:nowrap;"><b>Invoice No.</b></th>
        <th style="text-align:center"><b>Appt. Date</b></th>
        <th style="text-align:center"><b>Bill Type</b></th>
        <th style="text-align:center"><b>Service Code</b></th>
        <th style="text-align:center"><b>Dx</b></th>
        <th style="text-align:center"><b>Fee</b></th>
    </tr>
    </thead>
    <tbody>
    <c:forEach var="row" items="${historySpecModel.rows}">
        <tr>
            <td style="text-align:center"><carlos:encode value="${row.invoiceNo}" context="html"/>
            </td>
            <td style="text-align:center"><carlos:encode value="${row.billingDate}" context="html"/></td>
            <td style="text-align:center"><carlos:encode value="${row.billType}" context="html"/>
            </td>
            <td style="text-align:center"><carlos:encode value="${row.serviceCode}" context="html"/>
            </td>
            <td style="text-align:center"><carlos:encode value="${row.dx}" context="html"/>
            </td>
            <td style="text-align:center"><carlos:encode value="${row.total}" context="html"/>
            </td>
        </tr>
    </c:forEach>
    </tbody>

</table>
<br> &nbsp;<carlos:encode value="${historySpecModel.itemCount}" context="html"/> Items
<p>

<table style="width:100%">
    <tr>
        <td style="text-align:right"><a href="" onClick="self.close();">Close
            the Window</a></td>
    </tr>
</table>

</body>
</html>