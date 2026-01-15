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

--%>
<!--
*
* Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved. *
* This software is published under the GPL GNU General Public License.
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version. *
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details. * * You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA. *
*
* Yi Li
*/
-->
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ page import="java.math.*,java.util.*,java.sql.*,ca.openosp.*,java.net.*,java.text.*"
         errorPage="/errorpage.jsp" %>
<%@page import="ca.openosp.openo.commn.model.Site,ca.openosp.openo.commn.dao.SiteDao" %>
<%@page import="ca.openosp.openo.commn.model.Provider,ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@page import="ca.openosp.openo.commn.model.BillingONPayment,ca.openosp.openo.commn.dao.BillingONPaymentDao" %>
<%@page import="ca.openosp.openo.commn.model.BillingPaymentType" %>
<%@page import="java.text.SimpleDateFormat,java.text.NumberFormat" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="org.springframework.web.context.WebApplicationContext" %>

<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.dao.BillingONExtDao" %>
<%@page import="ca.openosp.openo.commn.model.BillingONExt" %>
<%@page import="ca.openosp.openo.commn.dao.BillingOnItemPaymentDao" %>
<%@page import="ca.openosp.openo.billing.CA.ON.model.*" %>
<%@page import="ca.openosp.openo.billings.ca.on.data.BillingItemData" %>
<%@page import="java.math.BigDecimal" %>
<%@page import="ca.openosp.openo.commn.dao.BillingPaymentTypeDao" %>
<%@page import="ca.openosp.openo.commn.model.BillingPaymentType" %>
<%@ page import="ca.openosp.openo.commn.IsPropertiesOn" %>

<%
    List<String> errors = new ArrayList<String>();
    String datetime = new SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime());

    boolean bMultisites = IsPropertiesOn.isMultisitesEnable();
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String providerNo = (String) session.getAttribute("user");

    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");

    boolean isTeamBillingOnly = false;
    boolean isSiteAccessPrivacy = false;
    boolean isTeamAccessPrivacy = false;

    boolean isMultiSiteProvider = true;
    List<String> mgrSites = new ArrayList<String>();
    List<BillingItemData> items = (List<BillingItemData>) request.getAttribute("itemDataList");
    List<BillingONPayment> paymentLists = (List<BillingONPayment>) request.getAttribute("paymentsList");
%>
<security:oscarSec objectName="_team_billing_only" roleName="<%= roleName$ %>" rights="r" reverse="false">
    <% isTeamBillingOnly = true; %>
</security:oscarSec>
<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false">
    <%isSiteAccessPrivacy = true; %>
</security:oscarSec>
<security:oscarSec objectName="_team_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false">
    <%isTeamAccessPrivacy = true; %>
</security:oscarSec>
<%
    /* suppose that access is controlled by containing billingONCorrection.jsp page
    List<Site> providerSites = null;
    if(isSiteAccessPrivacy) {
        SiteDao siteDao = (SiteDao)WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
        providerSites = siteDao.getActiveSitesByProviderNo(providerNo);
    }
    List<Provider> teamProviders = null;
    if(isTeamBillingOnly || isTeamAccessPrivacy) {
        ProviderDao providerDao = (ProviderDao)WebApplicationContextUtils.getWebApplicationContext(application).getBean(ProviderDao.class);
         teamProviders = providerDao.getActiveTeamProviders(providerNo);
    }
    */
    NumberFormat currency = NumberFormat.getCurrencyInstance();
    BigDecimal paymentParam = BigDecimal.valueOf(0);

    String billingNo = request.getParameter("billingNo");
    if (billingNo == null) errors.add("Wrong parameters");

%>

<html>
<head>
    <link rel="stylesheet" type="text/css" media="all"
          href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>

    <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
    <script type="text/javascript"
            src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath()%>/js/jquery-1.7.1.min.js"></script>
    <script type="text/javascript">

        function popupPage(vheight, vwidth, varpage) {
            var page = "" + varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
            var popup = window.open(page, "viewPayment", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
                popup.focus();
            }
        }

        function onViewPayment(id) {
            popupPage(500, 500, "<%=request.getContextPath()%>/billing/CA/ON/billingON3rdPayments.do?method=viewPayment_ext&billPaymentId=" + id);
        }

        function clickSaveAndSettle() {
            var validInput = true;

            elem = document.getElementById('paymentDate');
            if (elem.value == null || elem.value == '') {
                alert('Payment Date is required');
                validInput = false;
            }

            if (validInput) {
                jQuery.ajax({
                    url: "<%=request.getContextPath()%>/billing/CA/ON/billingON3rdPayments.do?status=S",
                    type: "GET",
                    async: "fasle",
                    timeout: 30000,
                    data: jQuery("#editPayment").serialize(),
                    dataType: "json",
                    success: function (data) {
                        if (data == null) {
                            alert("Error happened after getting response!");
                            return;
                        }
                        if (data.ret == 0) {
                            alert("Save payments successfully!");
                        } else {
                            alert(data.reason);
                        }
                        location.reload(true);
                    },
                    error: function () {
                        alert("Error happened while saving payments!");
                    }
                });
            }
        }

        function checkInput() {
            var validInput = true;

            elem = document.getElementById('paymentDate');
            if (elem.value == null || elem.value == '') {
                alert('Payment Date is required');
                validInput = false;
            }
            if (validInput) {
                // document.forms['editPayment'].submit();
                jQuery.ajax({
                    url: "<%=request.getContextPath()%>/billing/CA/ON/billingON3rdPayments.do",
                    type: "GET",
                    async: "fasle",
                    timeout: 30000,
                    data: jQuery("#editPayment").serialize(),
                    dataType: "json",
                    success: function (data) {
                        if (data == null) {
                            alert("Error happened after getting response!");
                            return;
                        }
                        if (data.ret == 0) {
                            alert("Save payments successfully!");
                        } else {
                            alert(data.reason);
                        }
                        location.reload(true);
                    },
                    error: function () {
                        alert("Error happened while saving payments!");
                    }
                });
            }
        }

        function setStatus(selIndex, idx) {
            if (selIndex == 0) {
                document.getElementById("discount" + idx).disabled = false;
            } else {
                document.getElementById("discount" + idx).disabled = true;
            }
        }


        function validatePaymentNumberic(idx) {
            var oldVal = "0.00";
            var val = document.getElementById("payment" + idx).value;
            if (val.length == 0) {
                document.getElementById("payment" + idx).value = "0.00";
                oldVal = "0.00";
                return;
            }
            //var regexNumberic = /^([1-9]\d*|0)(\.\d{1,2})?$/;
            var regexNumberic = /^([1-9]\d{0,9}|0)(\.\d{1,2})?$/;
            if (!regexNumberic.test(val)) {
                document.getElementById("payment" + idx).value = oldVal;
                alert("Please enter digital numbers !");
                return;
            }
            oldVal = val;
        }


        function validateDiscountNumberic(idx) {
            var oldVal = "0.00";
            var val = document.getElementById("discount" + idx).value;
            if (val.length == 0) {
                document.getElementById("discount" + idx).value = "0.00";
                oldVal = "0.00";
                return;
            }
            //var regexNumberic = /^([1-9]\d*|0)(\.\d{1,2})?$/;
            var regexNumberic = /^([1-9]\d{0,9}|0)(\.\d{1,2})?$/;
            if (!regexNumberic.test(val)) {
                document.getElementById("discount" + idx).value = oldVal;
                alert("Please enter digital numbers !");
                return;
            }
            oldVal = val;
        }

    </script>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.editBillPaymentList"/></title>
</head>
<security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="w">
<body bgcolor="ivory" text="#000000" topmargin="0" leftmargin="0" rightmargin="0">

<c:if test="${not empty paymentTypeList}">
    <form name="editPayment" id="editPayment" method="GET" action="">
        <input type="hidden" name="method" value="savePayment"/>
        <input type="hidden" name="billingNo" value="<%= billingNo %>"/>
        <input type="hidden" name="id" id="paymentId" value=""/>
        <table border=0 cellspacing=0 cellpadding=0 width="100%">
            <tr bgcolor="#CCCCFF">
                <th><font face="Helvetica">ADD / EDIT PAYMENT</font></th>
            </tr>
        </table>

        <table BORDER="3" CELLPADDING="0" CELLSPACING="0" WIDTH="100%" BGCOLOR="#C0C0C0">
            <%
                BigDecimal totalTotal = BigDecimal.ZERO;

                for (int i = 0; i < items.size(); i++) {
                    BillingItemData billItemData = items.get(i);
                    BigDecimal itemTotal = new BigDecimal(billItemData.getFee()).setScale(2, BigDecimal.ROUND_HALF_UP);
                    BigDecimal itemPaid = new BigDecimal(billItemData.getPaid()).setScale(2, BigDecimal.ROUND_HALF_UP);
                    BigDecimal itemDiscount = new BigDecimal(billItemData.getDiscount()).setScale(2, BigDecimal.ROUND_HALF_UP);
                    BigDecimal itemCredit = new BigDecimal(billItemData.getCredit()).setScale(2, BigDecimal.ROUND_HALF_UP);
                    BigDecimal itemBalance = itemTotal.subtract(itemPaid).subtract(itemDiscount).add(itemCredit);
                    BigDecimal realPaid = itemPaid.subtract(itemCredit);
                    totalTotal = new BigDecimal(totalTotal.intValue() + itemTotal.intValue());

                    String realPaidSign = "";
                    if (realPaid.compareTo(BigDecimal.ZERO) == -1) {
                        realPaidSign = "-";
                    }
                    String sign = "";
                    if (itemBalance.compareTo(BigDecimal.ZERO) == -1) {
                        sign = "-";
                    }
            %>
            <tr id="itemPayment<%=billItemData.getId() %>" BGCOLOR="#EEEEFF">
                <td width="30%">
                    <div align="right">
                        <select id="sel<%=i%>" name="sel<%=i%>" onchange="setStatus(this.selectedIndex,<%=i %>);">
                            <option value="payment">Payment</option>
                            <option value="credit">Refund Credit / Overpayment</option>
                            <option value="refund">Refund / Write off</option>
                        </select>
                    </div>
                </td>
                <td width="70%" align="left">
                    <input type="text" name="payment<%=i %>" id="payment<%=i %>" value="0.00" WIDTH="8" HEIGHT="20"
                           border="0" hspace="2" maxlength="50" onchange="validatePaymentNumberic(<%=i %>)"/>
                    Discount <input type="text" id="discount<%=i%>" name="discount<%=i %>" value="0.00"
                                    onchange="validateDiscountNumberic(<%=i %>)">
                </td>
            </tr>
            <tr BGCOLOR="#EEEEFF">
                <td>
                    <div></div>
                </td>
                <td align="left">
                    Service Code:&nbsp;<b><%=billItemData.getService_code()%>&nbsp;$<%=billItemData.getFee() %>&nbsp;
                    Paid:&nbsp;<%=realPaidSign %><%=currency.format(realPaid) %>&nbsp;
                    Balance:&nbsp;<%=sign %><%=currency.format(itemBalance) %>
                </b>
                    <input type="hidden" name="itemId<%=i %>" value="<%=billItemData.getId()%>"/>
                </td>
            </tr>
            <%if (i == (items.size() - 1)) {%>
            <tr BGCOLOR="#EEEEFF">
                <td>
                    <div align="right"><font face="arial">Payment Type:</font></div>
                </td>
                <td align="left">
                    <table width="100%">
                        <c:forEach var="billingPaymentType" items="${paymentTypeList}" varStatus="ttr">
                            <c:if test="${ttr.index % 2 == 0}">
                                <tr>
                            </c:if>
                            <td width="50%">
                                <input type="radio" name="paymentType"
                                    id="paymentType${billingPaymentType.id}"
                                    value="${billingPaymentType.id}" ${ttr.index == 0 ? "checked" : ""}/>
                                <c:out value="${billingPaymentType.paymentType}"/>
                            </td>
                            <c:if test="${ttr.index % 2 != 0}">
                                </tr>
                            </c:if>
                        </c:forEach>
                    </table>
                </td>
            </tr>
            <%} %>
            <%} %>
        </table>

        <table border="0" cellpadding="0" cellspacing="0" width="100%">
            <tr bgcolor="#CCCCFF">
                <td nowrap align="center">
                    <input type="text" name="paymentDate" id="paymentDate" onDblClick="calToday(this)" size="10"
                           value="<%=datetime%>">
                    <a id="btn_date"><img title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
                    <input type="button" id="saveBtn" name="submitBtn" value="    Save  "
                           onClick="checkInput(); return false;"/>
                    <input type="button" id="saveAndSettleBtn" value="Save & settle"
                           onClick="clickSaveAndSettle(); return false;"/>
                </td>
            </tr>
        </table>
        <input type="hidden" name="size" value="<%=items.size() %>">
    </form>
</c:if>
</security:oscarSec>

<% BillingPaymentTypeDao paymentTypeDao = (BillingPaymentTypeDao) SpringUtils.getBean(BillingPaymentTypeDao.class);
    ;
    BigDecimal sum = BigDecimal.valueOf(0);
    BigDecimal balance = BigDecimal.valueOf(0);
    int index = 0;
    List<BigDecimal> balances = new ArrayList<BigDecimal>();
    List<String> types = new ArrayList<String>();
    if (paymentLists != null && paymentLists.size() > 0) {
        BigDecimal total = paymentLists.get(0).getBillingONCheader1().getTotal();
        BigDecimal sumOfPay = BigDecimal.ZERO;
        BigDecimal sumOfDiscount = BigDecimal.ZERO;
        BigDecimal sumOfRefund = BigDecimal.ZERO;
        BigDecimal sumOfCredit = BigDecimal.ZERO;
        for (int i = 0; i < paymentLists.size(); i++) {
            balance = BigDecimal.ZERO;
            sumOfPay = sumOfPay.add(paymentLists.get(i).getTotal_payment());
            sumOfDiscount = sumOfDiscount.add(paymentLists.get(i).getTotal_discount());
            sumOfRefund = sumOfRefund.add(paymentLists.get(i).getTotal_refund());
            sumOfCredit = sumOfCredit.add(paymentLists.get(i).getTotal_credit());
            balance = total.subtract(sumOfPay).subtract(sumOfDiscount).add(sumOfCredit);
            balances.add(balance);

            String paymentType = "";
            int paymentTypeId = paymentLists.get(i).getPaymentTypeId();
            if (paymentTypeId > 0) {

                BillingPaymentType ptype = paymentTypeDao.find(paymentTypeId);
                paymentType = ptype.getPaymentType();
            }
            if (paymentType == null)
                paymentType = "";
            types.add(paymentType);
        }
    }
    sum = (BigDecimal) request.getAttribute("totalInvoiced");
    balance = (BigDecimal) request.getAttribute("balance");
%>

<table border=0 cellspacing=0 cellpadding=0 width="100%">
    <tr bgcolor="#CCCCFF">
        <th><font face="Helvetica">PAYMENTS LIST</font></th>
    </tr>
    <br/>
    <table width="100%" border="0">
        <thead>
        <tr>
            <th align="left">#</th>
            <th align="left">Payment</th>
            <th align="left">Payment Type</th>
            <th align="left">Date</th>
            <th align="left">Discount</th>
            <th align="left">Refund Credit / Overpayment</th>
            <th align="left">Refund / Write off</th>
            <th align="left">Balance</th>
            <th></th>
        </tr>
        </thead>
        <tbody>
        <c:if test="${not empty paymentsList}">
            <c:forEach var="displayPayment" items="${paymentsList}" varStatus="ctr">
                <tr>
                    <td>${ctr.index + 1}</td>
                    <td><c:out value="${displayPayment.total_payment}"/></td>
                    <td>${types[ctr.index]}</td>
                    <td><c:out value="${displayPayment.paymentDateFormatted}"/></td>
                    <td><c:out value="${displayPayment.total_discount}"/></td>
                    <td><c:out value="${displayPayment.total_credit}"/></td>
                    <td><c:out value="${displayPayment.total_refund}"/></td>
                    <td>
                        <c:choose>
                            <c:when test="${balances[ctr.index] < 0}">
                                -${currency.format(balances[ctr.index])}
                            </c:when>
                            <c:otherwise>
                                ${currency.format(balances[ctr.index])}
                            </c:otherwise>
                        </c:choose>
                    </td>
                    <td>
                        <a href="javascript:onViewPayment('<c:out value="${displayPayment.id}"/>')" >view</a>
                    </td>
                </tr>
            </c:forEach>
        </c:if>
        <tr>
            <td/>
            <td/>
            <td><b>Total:</b></td>
            <td><b><%= currency.format(sum) %>
            </b>
                    <%if (balance.compareTo(BigDecimal.ZERO) == -1) { %>
        <tr>
            <td/>
            <td/>
            <td><b>Balance:</b></td>
            <td><b><%= "-" + currency.format(balance) %>
            </b>
                    <%} else { %>
        <tr>
            <td/>
            <td/>
            <td><b>Balance:</b></td>
            <td><b><%= currency.format(balance) %>
            </b>
                    <%} %>
        </tbody>
    </table>
</table>

<% for (String error : errors) { %>
Error: <%= error %><br>
<% } %>

</body>
</html>
<security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="w">
    <script type="text/javascript">
        Calendar.setup({
            inputField: "paymentDate",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "btn_date",
            singleClick: true,
            step: 1
        });
    </script>
</security:oscarSec>
