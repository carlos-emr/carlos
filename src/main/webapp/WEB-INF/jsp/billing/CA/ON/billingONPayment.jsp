<!DOCTYPE html>
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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<%@page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingONPaymentViewModel" %>
<%
    // BillingONPayment2Action has already enforced _tasks r and assembled the
    // view model with the 9 DAO lookups the JSP body used to perform. This JSP
    // is now a pure presentation layer.
    BillingONPaymentViewModel paymentModel =
            (BillingONPaymentViewModel) request.getAttribute("paymentModel");
    if (paymentModel == null) {
        // Defensive fallback: any caller that forwards directly here (without
        // routing through the action) gets a logout redirect rather than a
        // half-rendered page. Mirrors the legacy <security:oscarSec reverse>
        // gate at the top of the original JSP.
        if (session.getAttribute("userrole") == null) {
            response.sendRedirect(request.getContextPath() + "/logoutPage");
            return;
        }
        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                "billingONPayment.jsp reached without paymentModel — caller should "
              + "route through billing/CA/ON/BillingONPayment.");
        paymentModel = BillingONPaymentViewModel.builder().build();
    }
%>

<html>
<head>
    <title><fmt:message key="oscar.billing.paymentReceived.title"/></title>

    <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="<%=request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
    <script src="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.js"></script>

    <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
    <link href="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.css" rel="stylesheet">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">

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

        <form name="billingPaymentForm" method="get" action="<%= request.getContextPath() %>/billing/CA/ON/BillingONPayment">

            <h4><fmt:message key="oscar.billing.on.paymentReceived.freezePeriod"/></h4>

            <div class="col-md-3">
                Provider:<br>
                <!--<fmt:message key="oscar.billing.on.paymentReceived.providerName"/>-->
                <select name="providerList">
                    <% if (paymentModel.isAllProvidersOption()) { %>
                    <option value=""><fmt:message key="oscar.billing.on.paymentReceived.allproviders"/></option>
                    <% } %>

                    <% for (BillingONPaymentViewModel.ProviderEntry __pe : paymentModel.getProviderOptions()) {
                        boolean __selected = paymentModel.getSelectedProviderNo() != null
                                && paymentModel.getSelectedProviderNo().equals(__pe.providerNo());
                    %>
                    <option <%= __selected ? "selected" : "" %> value="<carlos:encode value='<%= __pe.providerNo() %>' context="htmlAttribute"/>"><carlos:encode value='<%= __pe.displayName() %>' context="html"/></option>
                    <% } %>
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
            <% for (BillingONPaymentViewModel.RaReportRow __ra : paymentModel.getRaRows()) { %>
            <tr class="<carlos:encode value='<%= __ra.rowColor() %>' context="htmlAttribute"/>">
                <% if (__ra.firstRowForBill()) {%>
                <td style="text-align:center"><a href="#"
                                                 onclick="popupPage(700,700,'<%= request.getContextPath() %>/billing/CA/ON/BillingONCorrection?billing_no=<carlos:encode value='<%= __ra.billingNo() %>' context="javaScript"/>');return false;"><carlos:encode value='<%= __ra.billingNo() %>' context="html"/>
                </a></td>
                <%} else {%>
                <td></td>
                <%}%>
                <td style="text-align:center"><carlos:encode value='<%= __ra.billStatus() %>' context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value='<%= __ra.serviceDate() %>' context="html"/>
                </td>
                <% if (__ra.firstRowForBill()) {%>
                <td style="text-align:center"><a href="#"
                                                 onclick="popupPage(800,740,'<%= request.getContextPath() %>/demographic/DemographicEdit?demographic_no=<carlos:encode value='<%= __ra.demographicNo() %>' context="javaScript"/>');return false;"><carlos:encode value='<%= __ra.demographicName() %>' context="html"/>
                </a></td>
                <%} else {%>
                <td></td>
                <%}%>
                <td style="text-align:center"><carlos:encode value='<%= __ra.dxCode() %>' context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value='<%= __ra.serviceCode() %>' context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value='<%= __ra.serviceCount() %>' context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value='<%= __ra.fee() %>' context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value='<%= __ra.claim() %>' context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value='<%= __ra.paid() %>' context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value='<%= __ra.adjustment() %>' context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value='<%= __ra.payProgram() %>' context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value='<%= __ra.claimNo() %>' context="html"/>
                </td>
                <td style="text-align:center;font-weight:bold"><carlos:encode value='<%= __ra.errorCode() %>' context="html"/>
                </td>
            </tr>
            <% } %>
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
            <% for (BillingONPaymentViewModel.PremiumRow __pr : paymentModel.getPremiumRows()) { %>
            <tr class="<carlos:encode value='<%= __pr.rowColor() %>' context="htmlAttribute"/>">
                <td><carlos:encode value='<%= __pr.providerName() %>' context="html"/>
                </td>
                <td><carlos:encode value='<%= __pr.payDate() %>' context="html"/>
                </td>
                <td colspan="9" style="text-align:right"><carlos:encode value='<%= __pr.amountPaid() %>' context="html"/>
                </td>
            </tr>
            <% } %>
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
            <% for (BillingONPaymentViewModel.ThirdPartyBillRow __tp : paymentModel.getThirdPartyRows()) { %>
            <tr class="<carlos:encode value='<%= __tp.rowColor() %>' context="htmlAttribute"/>">
                <% if (!paymentModel.isThisProviderOnly()) { %>
                <td style="text-align:center"><a href="#"
                                                 onclick="popupPage(700,700,'<%= request.getContextPath() %>/billing/CA/ON/BillingONCorrection?billing_no=<carlos:encode value='<%= __tp.billingNo() %>' context="javaScript"/>');return false;"><carlos:encode value='<%= __tp.billingNo() %>' context="html"/>
                </a></td>
                <% } else { %>
                <td style="text-align:center"><carlos:encode value='<%= __tp.billingNo() %>' context="html"/>
                </td>
                <% } %>
                <td style="text-align:center"><carlos:encode value='<%= __tp.billingDate() %>' context="html"/>
                </td>
                <td style="text-align:center"><a href="#"
                                                 onclick="popupPage(800,740,'<%= request.getContextPath() %>/demographic/DemographicEdit?demographic_no=<carlos:encode value='<%= __tp.demographicNo() %>' context="javaScript"/>');return false;"><carlos:encode value='<%= __tp.demographicName() %>' context="html"/>
                </a></td>
                <%
                    int __numItem = 0;
                    for (BillingONPaymentViewModel.ThirdPartyItemRow __it : __tp.items()) {
                        __numItem++;
                        if (__numItem > 1) {
                %>
            </tr>
            <tr class="<carlos:encode value='<%= __tp.rowColor() %>' context="htmlAttribute"/>">
                <td colspan="3"></td>
                <% } %>
                <td style="text-align:center"><carlos:encode value='<%= __it.dxCode() %>' context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value='<%= __it.serviceCode() %>' context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value='<%= __it.serviceCount() %>' context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value='<%= __it.amtBilled() %>' context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value='<%= __it.amtPaid() %>' context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value='<%= __it.amtRefund() %>' context="html"/>
                </td>
                <td colspan="2"></td>
                <% } %>
            </tr>
            <tr class="<carlos:encode value='<%= __tp.rowColor() %>' context="htmlAttribute"/>">
                <td colspan="6"></td>
                <td style="font-weight:bold;text-align:right"><carlos:encode value='<%= __tp.totalBilled() %>' context="html"/>
                </td>
                <%
                    int __numPay = 0;
                    for (BillingONPaymentViewModel.ThirdPartyPaymentRow __pay : __tp.payments()) {
                        __numPay++;
                        String __colSpan = "1";
                        if (__numPay > 1) {
                            __colSpan = "8";
                %>
            </tr>
            <tr class="<carlos:encode value='<%= __tp.rowColor() %>' context="htmlAttribute"/>">
                <% } %>
                <td colspan="<carlos:encode value='<%= __colSpan %>' context="htmlAttribute"/>" style="text-align:right"><carlos:encode value='<%= __pay.paymentAmt() %>' context="html"/>
                </td>
                <td style="text-align:right"><carlos:encode value='<%= __pay.refundAmt() %>' context="html"/>
                </td>
                <td style="text-align:center"><carlos:encode value='<%= __pay.paymentDate() %>' context="html"/>
                </td>
                <td style="text-align:center"></td>
            </tr>
            <%
                    }
                    if (__tp.hasOutstanding()) {
                        String __fontWeight = __tp.outstandingBold() ? "font-weight:bold;" : "";
            %>
            <tr class="<carlos:encode value='<%= __tp.rowColor() %>' context="htmlAttribute"/>">
                <td colspan="11" style="text-align:right;<carlos:encode value='<%= __fontWeight %>' context="htmlAttribute"/>"><carlos:encode value='<%= __tp.outstandingAmt() %>' context="html"/>
                </td>
            </tr>
            <% }
            } %>
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
