<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Purpose: Supports billingONPayment in the Ontario billing workflow.
  Expected request model data includes: paymentModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<!DOCTYPE html>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="oscar.billing.paymentReceived.title"/></title>

    <script type="text/javascript" src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.js"></script>

    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.css" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/fontawesome-all.min.css">

    <script type="text/javascript">
        function popupPage(vheight, vwidth, varpage) {
            var page = "" + varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
            var popup = window.open(page, "billcorrection", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
                popup.focus();
            }
        }
    </script>

    <style>
        table td, th {
            font-size: 12px;
        }
    </style>
</head>

<body>
<h3><fmt:message key="admin.admin.paymentReceived"/></h3>

<div class="container-fluid">
    <span class="float-end"><carlos:encode value="${paymentModel.today}" context="html"/></span>

    <div class="row card card-body bg-body-tertiary">
        <carlos:encode value="${paymentModel.errorMsg}" context="html"/>

        <form name="billingPaymentForm" method="get" action="${pageContext.request.contextPath}/billing/CA/ON/BillingONPayment">

            <h4><fmt:message key="oscar.billing.on.paymentReceived.freezePeriod"/></h4>

            <div class="col-md-3">
                Provider:<br>
                <!--<fmt:message key="oscar.billing.on.paymentReceived.providerName"/>-->
                <select name="providerList">
                    <c:if test="${paymentModel.allProvidersOption}">
                    <option value=""><fmt:message key="oscar.billing.on.paymentReceived.allproviders"/></option>
                    </c:if>

                    <c:forEach var="pe" items="${paymentModel.providerOptions}">
                    <option <c:if test="${not empty paymentModel.selectedProviderNo and paymentModel.selectedProviderNo eq pe.providerNo}">selected</c:if> value="<carlos:encode value='${pe.providerNo}' context='htmlAttribute'/>"><carlos:encode value="${pe.displayName}" context="html"/></option>
                    </c:forEach>
                </select>
            </div>

            <div class="col-md-2">
                <fmt:message key="oscar.billing.on.paymentReceived.startDate"/><br>
                <div class="input-group">
                    <input type="text" class="form-control" style="width:90px" name="startDateText" id="startDateText"
                           value="<carlos:encode value="${paymentModel.startDateStr}" context="htmlAttribute"/>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                    <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                </div>
            </div>

            <div class="col-md-2">
                <fmt:message key="oscar.billing.on.paymentReceived.endDate"/><br>
                <div class="input-group">
                    <input type="text" class="form-control" style="width:90px" name="endDateText" id="endDateText"
                           value="<carlos:encode value="${paymentModel.endDateStr}" context="htmlAttribute"/>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                    <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                </div>
            </div>

            <div class="col-md-2">
                <br>
                <input class="btn btn-primary" type="submit"
                       value="<fmt:message key="oscar.billing.on.paymentReceived.generateReport"/>"/>
            </div>

    </div>

    <c:if test="${paymentModel.paymentsPartial}">
        <div class="alert alert-warning" role="alert" style="margin: 8px 0;">
            <strong>Payment totals may be incomplete.</strong>
            One or more payment rows had unreadable amounts and were skipped
            from totals. Review the source rows before relying on the totals.
        </div>
    </c:if>

    <div class="row">
        <h4><fmt:message key="oscar.billing.on.paymentReceived.raBillingReport"/></h4>
        <table class="table-striped table-sm table-hover">
            <thead>
            <tr>
                <th><fmt:message key="oscar.billing.on.paymentReceived.invoiceNumber"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.invoiceStatus"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.serviceDate"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.demographicName"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.dxCode"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.serviceCode"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.serviceCount"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.currentFee"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.claimed"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.paid"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.adjustments"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.payprogram"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.claimNo"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.errorCodes"/></th>
            </tr>
            </thead>

            <tbody>
            <c:forEach var="ra" items="${paymentModel.raRows}">
            <tr class="<carlos:encode value='${ra.rowColor}' context='htmlAttribute'/>">
                <c:choose>
                <c:when test="${ra.firstRowForBill}">
                <td style="text-align:center"><a href="#"
                                                 onclick="popupPage(700,700,'${pageContext.request.contextPath}/billing/CA/ON/BillingONCorrection?billing_no=<carlos:encode value='${ra.billingNo}' context='javaScript'/>');return false;"><carlos:encode value="${ra.billingNo}" context="html"/>
                </a></td>
                </c:when>
                <c:otherwise>
                <td></td>
                </c:otherwise>
                </c:choose>
                <td style="text-align:center"><carlos:encode value="${ra.billStatus}" context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value="${ra.serviceDate}" context="html"/>
                </td>
                <c:choose>
                <c:when test="${ra.firstRowForBill}">
                <td style="text-align:center"><a href="#"
                                                 onclick="popupPage(800,740,'${pageContext.request.contextPath}/demographic/DemographicEdit?demographic_no=<carlos:encode value='${ra.demographicNo}' context='javaScript'/>');return false;"><carlos:encode value="${ra.demographicName}" context="html"/>
                </a></td>
                </c:when>
                <c:otherwise>
                <td></td>
                </c:otherwise>
                </c:choose>
                <td style="text-align:center"><carlos:encode value="${ra.dxCode}" context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value="${ra.serviceCode}" context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value="${ra.serviceCount}" context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value="${ra.fee}" context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value="${ra.claim}" context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value="${ra.paid}" context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value="${ra.adjustment}" context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value="${ra.payProgram}" context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value="${ra.claimNo}" context="html"/>
                </td>
                <td style="text-align:center;font-weight:bold"><carlos:encode value="${ra.errorCode}" context="html"/>
                </td>
            </tr>
            </c:forEach>
            <tr>
                <td colspan="2" style="font-weight:bold;"><fmt:message key="oscar.billing.on.paymentReceived.itemCount"/>:
                </td>
                <td colspan="4"><carlos:encode value="${paymentModel.raItemCount}" context="html"/>
                </td>
                <td style="font-weight:bold"><fmt:message key="oscar.billing.on.paymentReceived.cumulativeTotal"/>:
                </td>
                <td style="text-align:right"><carlos:encode value="${paymentModel.raFeeTotal}" context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value="${paymentModel.raClaimTotal}" context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value="${paymentModel.raPaidTotal}" context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value="${paymentModel.raAdjTotal}" context="html"/>
                </td>
                <td colspan="5"></td>
            </tr>
            </tbody>
        </table>
        <hr>
        <!-- Premium Payments Table -->
        <h4><fmt:message key="oscar.billing.on.paymentReceived.premiumPaymentReport"/></h4>
        <table width="100%" cellspacing="0" class="table-striped table-sm table-hover">
            <thead>
            <tr>
                <th style="text-align:left"><fmt:message key="oscar.billing.on.paymentReceived.providerName"/></th>
                <th style="text-align:left"><fmt:message key="oscar.billing.on.paymentReceived.payDate"/></th>
                <th colspan="9" style="text-align:right"><fmt:message key="oscar.billing.on.paymentReceived.paid"/></th>
            </tr>
            </thead>
            <tbody>
            <c:forEach var="pr" items="${paymentModel.premiumRows}">
            <tr class="<carlos:encode value='${pr.rowColor}' context='htmlAttribute'/>">
                <td><carlos:encode value="${pr.providerName}" context="html"/>
                </td>
                <td><carlos:encode value="${pr.payDate}" context="html"/>
                </td>
                <td colspan="9" style="text-align:right"><carlos:encode value="${pr.amountPaid}" context="html"/>
                </td>
            </tr>
            </c:forEach>
            <tr>
                <td colspan="2" style="font-weight:bold;"><fmt:message key="oscar.billing.on.paymentReceived.itemCount"/>:
                </td>
                <td colspan="3"><carlos:encode value="${paymentModel.premiumItemCount}" context="html"/>
                </td>
                <td style="font-weight:bold"><fmt:message key="oscar.billing.on.paymentReceived.cumulativeTotal"/>:
                </td>
                <td style="text-align:right;font-weight:bold"><carlos:encode value="${paymentModel.premiumTotal}" context="html"/>
                </td>
                <td colspan="4"></td>
            </tr>
            </tbody>
        </table>
        <hr>
        <!-- 3rd Party Payments Table -->
        <h4><fmt:message key="oscar.billing.on.paymentReceived.3rdPartyBillingReport"/></h4>
        <table class="table-striped table-sm table-hover">
            <thead>
            <tr>
                <th><fmt:message key="oscar.billing.on.paymentReceived.invoiceNumber"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.serviceDate"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.demographicName"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.dxCode"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.serviceCode"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.serviceCount"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.billed"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.paid"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.refund"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.paymentDate"/></th>
                <th><fmt:message key="oscar.billing.on.paymentReceived.balanceOutstanding"/></th>
            </tr>
            </thead>
            <tbody>
            <c:forEach var="tp" items="${paymentModel.thirdPartyRows}">
            <tr class="<carlos:encode value='${tp.rowColor}' context='htmlAttribute'/>">
                <c:choose>
                <c:when test="${not paymentModel.thisProviderOnly}">
                <td style="text-align:center"><a href="#"
                                                 onclick="popupPage(700,700,'${pageContext.request.contextPath}/billing/CA/ON/BillingONCorrection?billing_no=<carlos:encode value='${tp.billingNo}' context='javaScript'/>');return false;"><carlos:encode value="${tp.billingNo}" context="html"/>
                </a></td>
                </c:when>
                <c:otherwise>
                <td style="text-align:center"><carlos:encode value="${tp.billingNo}" context="html"/>
                </td>
                </c:otherwise>
                </c:choose>
                <td style="text-align:center"><carlos:encode value="${tp.billingDate}" context="html"/>
                </td>
                <td style="text-align:center"><a href="#"
                                                 onclick="popupPage(800,740,'${pageContext.request.contextPath}/demographic/DemographicEdit?demographic_no=<carlos:encode value='${tp.demographicNo}' context='javaScript'/>');return false;"><carlos:encode value="${tp.demographicName}" context="html"/>
                </a></td>
                <c:forEach var="it" items="${tp.items}" varStatus="itSt">
                    <c:if test="${not itSt.first}">
            </tr>
            <tr class="<carlos:encode value='${tp.rowColor}' context='htmlAttribute'/>">
                <td colspan="3"></td>
                    </c:if>
                <td style="text-align:center"><carlos:encode value="${it.dxCode}" context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value="${it.serviceCode}" context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value="${it.serviceCount}" context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value="${it.amtBilled}" context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value="${it.amtPaid}" context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value="${it.amtRefund}" context="html"/>
                </td>
                <td colspan="2"></td>
                </c:forEach>
            </tr>
            <tr class="<carlos:encode value='${tp.rowColor}' context='htmlAttribute'/>">
                <td colspan="6"></td>
                <td style="font-weight:bold;text-align:right"><carlos:encode value="${tp.totalBilled}" context="html"/>
                </td>
                <c:forEach var="pay" items="${tp.payments}" varStatus="paySt">
                    <c:if test="${not paySt.first}">
            </tr>
            <tr class="<carlos:encode value='${tp.rowColor}' context='htmlAttribute'/>">
                    </c:if>
                <td colspan="${paySt.first ? '1' : '8'}" style="text-align:right"><carlos:encode value="${pay.paymentAmt}" context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value="${pay.refundAmt}" context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value="${pay.paymentDate}" context="html"/>
                </td>
                <td style="text-align:center"></td>
            </tr>
                </c:forEach>
                <c:if test="${tp.hasOutstanding}">
            <tr class="<carlos:encode value='${tp.rowColor}' context='htmlAttribute'/>">
                <td colspan="11" style="text-align:right;${tp.outstandingBold ? 'font-weight:bold;' : ''}"><carlos:encode value="${tp.outstandingAmt}" context="html"/>
                </td>
            </tr>
                </c:if>
            </c:forEach>
            <tr>
                <td colspan="2" style="font-weight:bold;"><fmt:message key="oscar.billing.on.paymentReceived.itemCount"/>:
                </td>
                <td colspan="3"><carlos:encode value="${paymentModel.thirdPartyItemCount}" context="html"/>
                </td>
                <td style="font-weight:bold"><fmt:message key="oscar.billing.on.paymentReceived.cumulativeTotal"/>:
                </td>
                <td style="text-align:right"><carlos:encode value="${paymentModel.thirdPartyBilledTotal}" context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value="${paymentModel.thirdPartyPaidTotal}" context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value="${paymentModel.thirdPartyRefundedTotal}" context="html"/>
                </td>
                <td colspan="2"></td>
            </tr>
            </tbody>
        </table>
        <br>
        <h3><fmt:message key="oscar.billing.on.paymentReceived.totalPaid"/>: <carlos:encode value="${paymentModel.finalTotal}" context="html"/>
        </h3>

        </form>

    </div><!--row-->
</div><!--container-->

<script type="text/javascript">
    flatpickr("#startDateText", {dateFormat: "Y-m-d", allowInput: true});
    flatpickr("#endDateText", {dateFormat: "Y-m-d", allowInput: true});
</script>

</body>
</html>
