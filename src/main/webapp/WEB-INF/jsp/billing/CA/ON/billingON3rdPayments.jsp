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
  Page role: Renders `billingON3rdPayments.jsp` for the Ontario billing workflow.
  Expected request model data includes: paymentsViewModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<fmt:setBundle basename="oscarResources"/>
<%
        if (request.getAttribute("__roleName3rd") == null) {
        Object userRole = session.getAttribute("userrole");
        Object userId = session.getAttribute("user");
        request.setAttribute("__roleName3rd", String.valueOf(userRole) + "," + String.valueOf(userId));
    }
%>

<html>
<head>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.request.contextPath}/share/calendar/calendar.css" title="win2k-cold-1"/>

    <script type="text/javascript" src="${pageContext.request.contextPath}/share/calendar/calendar.js"></script>
    <script type="text/javascript"
            src="${pageContext.request.contextPath}/share/calendar/lang/<fmt:message key="global.javascript.calendar"/>"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/share/calendar/calendar-setup.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-compat.js"></script>
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
            popupPage(500, 500, "${pageContext.request.contextPath}/billing/CA/ON/billingON3rdPayments?method=viewPayment_ext&billPaymentId=" + id);
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
                    url: "${pageContext.request.contextPath}/billing/CA/ON/billingON3rdPayments",
                    type: "POST",
                    async: false,
                    timeout: 30000,
                    data: jQuery("#editPayment").serialize() + "&status=S",
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
                    url: "${pageContext.request.contextPath}/billing/CA/ON/billingON3rdPayments",
                    type: "POST",
                    async: false,
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
    <title><fmt:message key="admin.admin.editBillPaymentList"/></title>
</head>
<security:oscarSec roleName="${__roleName3rd}" objectName="_billing" rights="w">
<body bgcolor="ivory" text="#000000" topmargin="0" leftmargin="0" rightmargin="0">

<c:if test="${not empty paymentTypeList}">
    <form name="editPayment" id="editPayment" method="POST" action="">
        <input type="hidden" name="method" value="savePayment"/>
        <input type="hidden" name="billingNo" value="<carlos:encode value='${paymentsViewModel.billingNo}' context='htmlAttribute'/>"/>
        <input type="hidden" name="id" id="paymentId" value=""/>
        <table border=0 cellspacing=0 cellpadding=0 width="100%">
            <tr bgcolor="#CCCCFF">
                <th><font face="Helvetica">ADD / EDIT PAYMENT</font></th>
            </tr>
        </table>

        <table BORDER="3" CELLPADDING="0" CELLSPACING="0" WIDTH="100%" BGCOLOR="#C0C0C0">
            <c:forEach var="rowItem" items="${paymentsViewModel.items}" varStatus="itr">
                <tr id="itemPayment<carlos:encode value='${rowItem.id}' context='htmlAttribute'/>" BGCOLOR="#EEEEFF">
                    <td width="30%">
                        <div align="right">
                            <select id="sel${itr.index}" name="sel${itr.index}" onchange="setStatus(this.selectedIndex,${itr.index});">
                                <option value="payment">Payment</option>
                                <option value="credit">Refund Credit / Overpayment</option>
                                <option value="refund">Refund / Write off</option>
                            </select>
                        </div>
                    </td>
                    <td width="70%" align="left">
                        <input type="text" name="payment${itr.index}" id="payment${itr.index}" value="0.00" WIDTH="8" HEIGHT="20"
                               border="0" hspace="2" maxlength="50" onchange="validatePaymentNumberic(${itr.index})"/>
                        Discount <input type="text" id="discount${itr.index}" name="discount${itr.index}" value="0.00"
                                        onchange="validateDiscountNumberic(${itr.index})">
                    </td>
                </tr>
                <tr BGCOLOR="#EEEEFF">
                    <td>
                        <div></div>
                    </td>
                    <td align="left">
                        Service Code:&nbsp;<b><carlos:encode value='${rowItem.serviceCode}' context='html'/>&nbsp;$<carlos:encode value='${rowItem.fee}' context='html'/>&nbsp;
                        Paid:&nbsp;<carlos:encode value='${rowItem.realPaidDisplay}' context='html'/>&nbsp;
                        Balance:&nbsp;<carlos:encode value='${rowItem.balanceDisplay}' context='html'/>
                        </b>
                        <input type="hidden" name="itemId${itr.index}" value="<carlos:encode value='${rowItem.id}' context='htmlAttribute'/>"/>
                    </td>
                </tr>
                <c:if test="${itr.last}">
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
                                        ${carlos:forHtml(billingPaymentType.paymentType)}
                                    </td>
                                    <c:if test="${ttr.index % 2 != 0}">
                                        </tr>
                                    </c:if>
                                </c:forEach>
                            </table>
                        </td>
                    </tr>
                </c:if>
            </c:forEach>
        </table>

        <table border="0" cellpadding="0" cellspacing="0" width="100%">
            <tr bgcolor="#CCCCFF">
                <td nowrap align="center">
                    <input type="text" name="paymentDate" id="paymentDate" onDblClick="calToday(this)" size="10"
                           value="<carlos:encode value='${paymentsViewModel.today}' context='htmlAttribute'/>">
                    <a id="btn_date"><img title="Calendar" src="${pageContext.request.contextPath}/images/cal.gif" alt="Calendar" border="0"/></a>
                    <input type="button" id="saveBtn" name="submitBtn" value="    Save  "
                           onClick="checkInput(); return false;"/>
                    <input type="button" id="saveAndSettleBtn" value="Save & settle"
                           onClick="clickSaveAndSettle(); return false;"/>
                </td>
            </tr>
        </table>
        <input type="hidden" name="size" value="${paymentsViewModel.itemCount}">
    </form>
</c:if>
</security:oscarSec>

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
        <c:forEach var="payRow" items="${paymentsViewModel.payments}" varStatus="ctr">
            <tr>
                <td>${ctr.index + 1}</td>
                <td><carlos:encode value='${payRow.totalPayment}' context='html'/></td>
                <td><carlos:encode value='${payRow.paymentTypeName}' context='html'/></td>
                <td><carlos:encode value='${payRow.paymentDateFormatted}' context='html'/></td>
                <td><carlos:encode value='${payRow.totalDiscount}' context='html'/></td>
                <td><carlos:encode value='${payRow.totalCredit}' context='html'/></td>
                <td><carlos:encode value='${payRow.totalRefund}' context='html'/></td>
                <td><carlos:encode value='${payRow.balanceDisplay}' context='html'/></td>
                <td>
                    <a href="javascript:onViewPayment('<carlos:encode value="${payRow.id}" context="javaScript"/>')" >view</a>
                </td>
            </tr>
        </c:forEach>
        <tr>
            <td/>
            <td/>
            <td><b>Total:</b></td>
            <td><b><carlos:encode value='${paymentsViewModel.totalDisplay}' context='html'/></b></td>
        </tr>
        <tr>
            <td/>
            <td/>
            <td><b>Balance:</b></td>
            <td><b><carlos:encode value='${paymentsViewModel.balanceDisplay}' context='html'/></b></td>
        </tr>
        </tbody>
    </table>
</table>

<c:forEach var="errMsg" items="${paymentsViewModel.errors}">
    Error: <carlos:encode value='${errMsg}' context='html'/><br>
</c:forEach>

</body>
</html>
<security:oscarSec roleName="${__roleName3rd}" objectName="_billing" rights="w">
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
