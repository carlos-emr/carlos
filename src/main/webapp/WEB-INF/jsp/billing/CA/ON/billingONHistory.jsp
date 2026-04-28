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
  Page role: Renders `billingONHistory.jsp` for the Ontario billing workflow.
  Expected request model data includes: historyModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%--
    Ontario Billing History popup.

    Purpose: Displays all billing history records for a demographic in a DataTables-powered
    table with Bootstrap styling.

    Parameters:
        demographic_no  - Patient demographic number (required; name is resolved server-side)
        orderby         - Sort column (appointment_date) — unused, kept for URL compatibility
        displaymode     - Display mode (appt_history) — unused, kept for URL compatibility
        dboperation     - Database operation (appt_history) — unused, kept for URL compatibility

    @since 2006
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="carlos" prefix="carlos" %>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<!DOCTYPE html>
<fmt:message var="msgOnUnbilledText" key="provider.appointmentProviderAdminDay.onUnbilled"/>
<fmt:message var="dtLanguageCode" key="global.i18n.datatablescode"/>
<html>
<head>
    <title><fmt:message key="billing.billingONHistory.title"/></title>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/css/dataTables.bootstrap5.min.css" rel="stylesheet" type="text/css">
    <script src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/js/jquery.dataTables.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/js/dataTables.bootstrap5.min.js"></script>

    <script language="JavaScript">
        function onUnbilled(billingNo, billCode) {
            if (confirm("${carlos:forJavaScript(msgOnUnbilledText)}")) {
                var form = document.createElement('form');
                form.method = 'post';
                form.action = '${pageContext.request.contextPath}/billing/CA/ON/BillingDeleteNoAppt';
                form.target = 'unbill_popup';
                var fields = {billing_no: billingNo, billCode: billCode, dboperation: 'delete_bill', hotclick: '0'};
                for (var key in fields) {
                    var input = document.createElement('input');
                    input.type = 'hidden';
                    input.name = key;
                    input.value = fields[key];
                    form.appendChild(input);
                }
                document.body.appendChild(form);
                window.open('', 'unbill_popup', 'height=700,width=720,location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes');
                form.submit();
                document.body.removeChild(form);
            }
        }

        function popUpClosed() {
            window.location.reload();
        }

        jQuery(document).ready(function () {
            jQuery('#billingHistoryTable').DataTable({
                language: {
                    url: '${pageContext.request.contextPath}/library/DataTables/i18n/${carlos:forUriComponent(dtLanguageCode)}.json'
                }
            });
        });
    </script>

    <oscar:customInterface section="billingONHistory"/>
</head>
<body>

<nav class="navbar navbar-dark bg-dark">
    <div class="container-fluid">
        <span class="navbar-brand"><fmt:message key="billing.billingONHistory.title"/></span>
        <span class="navbar-text text-white-50">
            <em><carlos:encode value="${historyModel.patientDisplayName}" context="html"/></em>
            &nbsp;(<carlos:encode value="${historyModel.demographicNo}" context="html"/>)
        </span>
    </div>
</nav>

<fmt:message var="msgBillingDisplay" key="billing.billingONHistory.titleBillingDisplay"/>
<fmt:message var="msgBillingCorrection" key="billing.billingONHistory.titleBillingCorrection"/>
<fmt:message var="msgEdit" key="billing.billingONHistory.btnEdit"/>
<fmt:message var="msgPrint" key="billing.billingONHistory.btnPrint"/>
<fmt:message var="msgUnbill" key="billing.billingONHistory.btnUnbill"/>

<div class="container-fluid mt-3">
    <table id="billingHistoryTable" class="table table-striped table-hover table-bordered">
        <thead>
            <tr>
                <th><fmt:message key="billing.billingONHistory.colInvoiceNo"/></th>
                <th><fmt:message key="billing.billingONHistory.colBillingDoctor"/></th>
                <th><fmt:message key="billing.billingONHistory.colApptDate"/></th>
                <th><fmt:message key="billing.billingONHistory.colBillType"/></th>
                <th><fmt:message key="billing.billingONHistory.colServiceCode"/></th>
                <th><fmt:message key="billing.billingONHistory.colDx"/></th>
                <th><fmt:message key="billing.billingONHistory.colBalance"/></th>
                <th><fmt:message key="billing.billingONHistory.colFee"/></th>
                <th><fmt:message key="billing.billingONHistory.colComments"/></th>
            </tr>
        </thead>
        <tbody>
        <c:forEach var="row" items="${historyModel.rows}">
            <tr>
                <td class="text-center">
                    <a href="javascript:void(0)"
                       onclick="popupPage(600,800, '${pageContext.request.contextPath}/billing/CA/ON/ViewBillingONDisplay?billing_no=${carlos:forUriComponent(row.invoiceId)}')"
                       title="${msgBillingDisplay}"><carlos:encode value="${row.invoiceId}" context="html"/>
                    </a>

                    <c:if test="${row.canEdit}">
                        <a href="javascript:void(0)"
                           onclick="popupPage(600,800, '${pageContext.request.contextPath}/billing/CA/ON/BillingONCorrection?billing_no=${carlos:forUriComponent(row.invoiceId)}')"
                           title="${msgBillingCorrection}">${msgEdit}</a>
                    </c:if>

                    <a href="javascript:void(0)"
                       onclick="popupPage(600,800, '${pageContext.request.contextPath}/billing/CA/ON/ViewBillingON3rdInv?billingNo=${carlos:forUriComponent(row.invoiceId)}')">${msgPrint}</a>
                </td>
                <td class="text-center"><carlos:encode value="${row.providerLastFirst}" context="html"/></td>
                <td class="text-center"><carlos:encode value="${row.billingDate}" context="html"/></td>
                <td class="text-center"><carlos:encode value="${row.billType}" context="html"/></td>
                <td class="text-center"><carlos:encode value="${row.serviceCode}" context="html"/></td>
                <td class="text-center"><carlos:encode value="${row.dx}" context="html"/></td>
                <td class="text-center">
                    <c:choose>
                        <c:when test="${row.balanceShown}"><carlos:encode value="${row.balance}" context="html"/></c:when>
                        <c:otherwise>&nbsp;</c:otherwise>
                    </c:choose>
                </td>
                <td class="text-center"><carlos:encode value="${row.total}" context="html"/></td>
                <td class="text-center">
                    <c:if test="${row.unbillLinkShown}">
                        <a href="#"
                           onclick="onUnbilled('<carlos:encode value="${row.invoiceId}" context="javaScriptAttribute"/>','<carlos:encode value="${row.status}" context="javaScriptAttribute"/>');return false;">${msgUnbill}</a>
                    </c:if>
                    <c:if test="${not row.unbillLinkShown}">&nbsp;</c:if>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>

    <div class="d-flex justify-content-between mt-3 mb-3">
        <button class="btn btn-secondary" onclick="javascript:history.go(-1);return false;"><fmt:message key="billing.billingONHistory.btnBack"/></button>
        <button class="btn btn-primary" onclick="self.close();"><fmt:message key="billing.billingONHistory.btnClose"/></button>
    </div>
</div>

</body>
</html>
