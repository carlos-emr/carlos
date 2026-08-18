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
  Purpose: This page provides an opportunity to review
  and potentially print bills specified in billingON.jsp
  Expected request model data includes: reviewModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<!DOCTYPE html>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<fmt:setBundle basename="oscarResources"/>

<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
<c:set var="reviewModel" value="${reviewModel}" scope="page"/>
<c:set var="demographicNo" value="${reviewModel.requestParamEchoes['demographic_no']}" scope="request"/>

<%-- i18n message variables for JavaScript alerts and submit button values --%>
<fmt:message var="msgEnterNumbers" key="oscar.billing.ca.on.billingON.review.alertEnterNumbers"/>
<fmt:message var="msgEnterValidFee" key="oscar.billing.ca.on.billingON.review.alertEnterValidFee"/>
<fmt:message var="msgSelectPaymentMethod" key="oscar.billing.ca.on.billingON.review.alertSelectPaymentMethod"/>
<fmt:message var="msgNothingSelected" key="oscar.billing.ca.on.billingON.review.alertNothingSelected"/>
<fmt:message var="msgConfirmAddDxRegistry" key="oscar.billing.ca.on.billingON.review.confirmAddDxRegistry"/>
<fmt:message var="msgBtnBackToEdit" key="oscar.billing.ca.on.billingON.review.btnBackToEdit"/>
<fmt:message var="msgBtnSave" key="global.btnSave"/>
<fmt:message var="msgBtnSaveAndAdd" key="oscar.billing.ca.on.billingON.review.btnSaveAndAdd"/>
<fmt:message var="msgBtnSavePrint" key="oscar.billing.ca.on.billingON.review.btnSavePrint"/>
<fmt:message var="msgBtnSettlePrint" key="oscar.billing.ca.on.billingON.review.btnSettlePrint"/>
<fmt:message var="msgBtnAddDxRegistry" key="oscar.billing.ca.on.billingON.review.btnAddDxRegistry"/>

<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title>CARLOS Billing</title>

    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">

    <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>

    <oscar:customInterface section="billingReview"/>
    <script language="JavaScript">
        ctx = "${carlos:forJavaScript(ctx)}";
        demographicNo = "${carlos:forJavaScript(demographicNo)}";

        var bClick = false;

        function onSave() {
            var value = document.getElementById("payee").value;
            document.getElementById("payeename").value = value;
            var ret = checkTotal();
            bClick = false;

            return ret;
        }

        function onClickSave() {
            bClick = true;
        }

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

        function settlePayment() {
            document.forms[0].payment.value = document.forms[0].total.value;
        }

        function scriptAttach(elementName) {
            var d = elementName;
            t0 = d;
            popupPage('600', '700', ctx + '/billing/CA/ON/ViewOnSearch3rdBillAddr?param=' + t0);
        }

        function showtotal() {
            var el = document.getElementById('payMethod_0');
            if (el != null) {
                document.getElementById('payMethod_0').checked = true;
            }
            var subtotal = document.getElementById("total").value;
            var element = document.getElementById("stotal");
            if (element != null)
                element.value = subtotal;
        }

        function validatePaymentNumberic(idx) {
            var oldVal = document.getElementById("percCodeSubtotal_" + idx).value;
            var val = document.getElementById("paid_" + idx).value;
            var regexNumberic = /^([1-9]\d{0,9}|0)(\.\d{1,2})?$/;
            if (!regexNumberic.test(val)) {
                document.getElementById("paid_" + idx).value = oldVal;
                alert("${carlos:forJavaScript(msgEnterNumbers)}");
                return;
            }
            oldVal = val;
        }

        function validateDiscountNumberic(idx) {
            var oldVal = "0.00";
            var val = document.getElementById("discount_" + idx).value;
            if (val.length == 0) {
                document.getElementById("discount_" + idx).value = "0.00";
                oldVal = "0.00";
                return;
            }
            var regexNumberic = /^([1-9]\d{0,9}|0)(\.\d{1,2})?$/;
            if (!regexNumberic.test(val)) {
                document.getElementById("discount_" + idx).value = oldVal;
                alert("${carlos:forJavaScript(msgEnterNumbers)}");
                return;
            }
            oldVal = val;
        }

        function validateFeeNumberic(idx) {
            var oldVal = "0.00";
            var val = document.getElementById("percCodeSubtotal_" + idx).value;
            if (val.length == 0) {
                document.getElementById("percCodeSubtotal_" + idx).value = "0.00";
                oldVal = "0.00";
                return;
            }
            var regexNumberic = /^([1-9]\d{0,9}|0)(\.\d{1,2})?$/;
            if (!regexNumberic.test(val)) {
                document.getElementById("percCodeSubtotal_" + idx).value = oldVal;
                alert("${carlos:forJavaScript(msgEnterNumbers)}");
                return;
            }
            oldVal = val;
        }

        function updateElement(eId, data) {
            document.getElementById(eId).value = data;
        }

        function checkTotal() {
            var totValue = document.getElementById("total").value;
            if (isNaN(totValue)) {
                alert("${carlos:forJavaScript(msgEnterValidFee)}");
                return false;
            }
            return true;
        }

        function updateTotal(e) {
            var editedValue = e.value;
            if (isNaN(editedValue)) {
                alert("${carlos:forJavaScript(msgEnterValidFee)}");
                e.focus();
            } else {
                var codeFees;
                var unit;
                var total = 0.0;
                var idx = 0;
                var displayTotal = "0.00";

                while ((codeFees = document.getElementById("percCodeSubtotal_" + idx))) {
                    total += parseInt(codeFees.value);
                    ++idx;
                }

                updateElement("total", formatTotal(total));
                total += new Number(document.getElementById("gst").value);
                updateElement("gstBilledTotal", formatTotal(total));

            }

        }

        function formatTotal(total) {
            var displayTotal = "0.00";
            var decimal = total % 1;

            if (decimal == 0) {
                displayTotal = total + ".00";
            } else if ((decimal * 10) % 1 < 0.1) {
                displayTotal = total + "0";
            } else {
                displayTotal = total;
            }

            return displayTotal;
        }

        function checkPaymentMethod(settle) {
            var payMethods = document.getElementsByName("payMethod");
            var checkedMethod = false;

            if (settle != "Settle" && document.forms[0].payment.value == 0) {
                return true;
            }

            for (var idx = 0; idx < payMethods.length; ++idx) {
                if (payMethods[idx].checked) {
                    checkedMethod = true;
                    break;
                }
            }

            if (!checkedMethod) {
                alert("${carlos:forJavaScript(msgSelectPaymentMethod)}");
            } else if (settle == "Settle") {
                document.forms['titlesearch'].btnPressed.value = 'Settle';
                document.forms['titlesearch'].submit();
                popupPage(700, 720, ctx + '/billing/CA/ON/ViewBillingON3rdInv');
            }

            return checkedMethod;

        }

        function toggle(id) {
            var el = document.getElementById(id);
            if (window.getComputedStyle(el).display === "block") {
                el.style.display = "none";
            } else {
                el.style.display = "block";
            }
        }

    </script>

    <style type="text/css">
        div.wrapper {
            background-color: white;
            margin-top: 0px;
            padding-top: 0px;
            margin-bottom: 0px;
            padding-bottom: 0px;
        }

        div.wrapper br {
            clear: left;
        }

        div.wrapper ul {
            width: 80%;
            list-style: none;
            list-style-type: none;
            list-style-position: outside;
            padding-left: 55px;
            margin-left: 1px;
        }

        div.dxBox h3 {
            background-color: silver;
            font-size: 10pt;
            font-variant: small-caps;
            font-weight: bold;
            margin-top: 0px;
            padding-top: 0px;
            margin-bottom: 0px;
            padding-bottom: 0px;
        }

        div.dxBox form {
            margin-top: 0px;
            padding-top: 0px;
            margin-bottom: 0px;
            padding-bottom: 0px;
        }

        div.dxBox input {
            margin-top: 0px;
            padding-top: 0px;
            margin-bottom: 0px;
            padding-bottom: 0px;
        }

        .border1, .border1 th, .border1 td {
            border: 1px solid lightgray

        }

        input[type="text"] {
            margin-bottom: 0px;
        }

    </style>

</head>

<body onload="showtotal(),calculatePayment()">

<form method="post" name="titlesearch" action="${pageContext.request.contextPath}/billing/CA/ON/BillingONSave" onsubmit="return onSave();">
    <input type="hidden" name="url_back" value="<carlos:encode value='${reviewModel.requestParamEchoes[\"url_back\"]}' context='htmlAttribute'/>">
    <input type="hidden" name="billNo_old" id="billNo_old" value="<carlos:encode value='${reviewModel.requestParamEchoes[\"billNo_old\"]}' context='htmlAttribute'/>"/>
    <input type="hidden" name="billStatus_old" id="billStatus_old" value="<carlos:encode value='${reviewModel.requestParamEchoes[\"billStatus_old\"]}' context='htmlAttribute'/>"/>
    <input type="hidden" name="billForm" id="billForm" value="<carlos:encode value='${reviewModel.requestParamEchoes[\"billForm\"]}' context='htmlAttribute'/>"/>
    <input type="hidden" name="payeename" id="payeename" value=""/>
    <input type="hidden" name="billingAction" id="billingAction" value="SAVE"/>
    <table style="width:100%" class="myIvory">
        <tr>
            <td>
                <table style="width:100%" class="myDarkGreen">
                    <tr style="background-color:silver;">
                        <td><H4>&nbsp;<fmt:message key="oscar.billing.ca.on.billingON.review.heading"/></H4></td>
                        <td style="text-align:right"><input type="hidden" name="addition" value="Confirm"/></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td>
                <table style="width:100%" class="myYellow">
                    <tr>
                        <td style="white-space:nowrap; width:10%; text-align:center"><carlos:encode value="${reviewModel.demoName}" context="html"/>
                            &nbsp;&nbsp; <c:choose>
                                <c:when test="${reviewModel.demoSexLabel eq 'Female'}"><fmt:message key="global.gender.female"/></c:when>
                                <c:when test="${reviewModel.demoSexLabel eq 'Male'}"><fmt:message key="global.gender.male"/></c:when>
                                <c:when test="${reviewModel.demoSexLabel eq 'Intersex'}"><fmt:message key="global.gender.intersex"/></c:when>
                                <c:when test="${reviewModel.demoSexLabel eq 'Other'}"><fmt:message key="global.gender.other"/></c:when>
                                <c:when test="${reviewModel.demoSexLabel eq 'Undisclosed'}"><fmt:message key="global.gender.undisclosed"/></c:when>
                                <c:otherwise><carlos:encode value="${reviewModel.demoSexLabel}" context="html"/></c:otherwise>
                            </c:choose> &nbsp;&nbsp;
                            <carlos:encode value="${reviewModel.demoHeaderLine}" context="html"/>
                        </td>
                        <td style="text-align:center">
                            <c:forEach var="alert" items="${reviewModel.reviewAlerts}">
                                <div class="alert alert-danger" role="alert">
                                    <carlos:encode value="${alert.message}" context="html"/>
                                </div>
                            </c:forEach>
                        </td>
                    </tr>
                </table>

                <table style="width:100%;">
                    <tr>
                        <td style="width:50%">

                            <table style="width:100%">
                                <tr>
                                    <td style="white-space:nowrap; width:30%; text-align:center"><b><fmt:message key="global.serviceDate"/></b><br>
                                        <c:forEach var="line" items="${reviewModel.serviceDateLines}" varStatus="lst">
                                            <c:if test="${not lst.first}"><br></c:if>
                                            <carlos:encode value="${line}" context="html"/>
                                        </c:forEach>
                                    </td>
                                    <td style="text-align:center; width:33%"><b><fmt:message key="global.diagnosticCode"/></b><br>
                                        ${carlos:forHtmlContent(reviewModel.dxCode)}<br>
                                        ${carlos:forHtmlContent(reviewModel.dxDesc)}
                                    </td>
                                    <td style="vertical-align:top"><b><fmt:message key="oscar.billing.ca.on.billingON.review.referralDoctor"/></b><br>
                                        <carlos:encode value="${reviewModel.requestParamEchoes['referralDocName']}" context="html"/><br>
                                        <b><fmt:message key="oscar.billing.ca.on.billingON.review.referralDoctor"/>&nbsp;#</b><br>
                                        <carlos:encode value="${reviewModel.requestParamEchoes['referralCode']}" context="html"/>
                                    </td>
                                </tr>
                            </table>

                        </td>
                        <td style="vertical-align:top">

                            <table style="width:100%"
                                   class="myGreen">
                                <tr>
                                    <td style="white-space:nowrap;width:30%"><b><fmt:message key="oscar.billing.ca.on.billingON.billingPhysician"/></b></td>
                                    <td style="width:20%"><carlos:encode value="${reviewModel.billingPhysicianLabel}" context="html"/>
                                    </td>
                                    <td style="white-space:nowrap; width:30%"><b><fmt:message key="encounter.Index.msgMRP"/></b></td>
                                    <td style="width:20%"><carlos:encode value="${reviewModel.mrpLabel}" context="html"/>
                                    </td>
                                </tr>
                                <tr>

                                    <td style="width:30%"><b><fmt:message key="billing.billingCorrection.formVisitType"/></b></td>
                                    <td style="width:20%"><carlos:encode value="${reviewModel.visitTypeLabel}" context="html"/>
                                    </td>

                                    <td style="width:30%"><b><fmt:message key="oscar.billing.ca.on.billingON.billingType"/></b></td>
                                    <td style="width:20%"><carlos:encode value="${reviewModel.billTypeLabel}" context="html"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td><b><fmt:message key="oscar.billing.ca.on.billingON.visitLocation"/></b></td>
                                    <td><carlos:encode value="${reviewModel.locationLabel}" context="html"/> &nbsp;
                                        <c:if test="${reviewModel.MReview}">
                                            <b><fmt:message key="oscar.billing.ca.on.billingON.review.manualY"/></b>
                                        </c:if>
                                    </td>

                                    <c:if test="${reviewModel.multisitesEnabled}">
                                        <td style="width:30%"><b><fmt:message key="oscar.billing.ca.on.billingON.review.billingClinic"/></b></td>
                                        <td style="width:20%; white-space:nowrap;"><carlos:encode value="${reviewModel.siteName}" context="html"/>
                                        </td>
                                    </c:if>
                                </tr>
                                <tr>
                                    <td><b><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode"/></b></td>
                                    <td><carlos:encode value="${reviewModel.sliCodeLabel}" context="html"/>
                                        &nbsp;
                                    </td>
                                    <c:if test="${reviewModel.multisitesEnabled}">
                                        <td></td>
                                        <td></td>
                                    </c:if>
                                </tr>
                                <tr>
                                    <td><b><fmt:message key="oscar.billing.ca.on.billingON.admissionDate"/></b></td>
                                    <td><carlos:encode value="${reviewModel.admissionDate}" context="html"/>
                                    </td>
                                    <td colspan="2"></td>
                                    <c:if test="${reviewModel.multisitesEnabled}">
                                        <td></td>
                                        <td></td>
                                    </c:if>
                                </tr>
                            </table>

                        </td>
                    </tr>
                </table>

            </td>

        </tr>
        <tr>
            <td style="text-align:center">
                <table class="border1" style="width:100%">
                    <%-- Pre-render validation rows. The legacy 80-line scriptlet
                         block (3 inline DAO calls, A003A guard + service-code
                         validity + dx-code validity) has been moved to
                         BillingOnReviewValidator (run pre-render by
                         BillingOnReviewViewModelAssembler). The JSP just iterates
                         the resulting messages. --%>
                    <c:forEach var="vm" items="${reviewModel.validationMessages}">
                        <c:choose>
                            <c:when test="${vm.severity == 'WARNING'}">
                                <tr style="color:white">
                                    <td align="center">
                                        <div class="myError">${carlos:forHtmlContent(vm.text)}</div>
                                    </td>
                                </tr>
                            </c:when>
                            <c:otherwise>
                                <tr class="alert alert-danger">
                                    <td align="center">
                                        &nbsp;<br>
                                        ${carlos:forHtmlContent(vm.text)}
                                    </td>
                                </tr>
                            </c:otherwise>
                        </c:choose>
                    </c:forEach>
                    <c:if test="${reviewModel.codeValid}">
                    <tr class="myYellow">
                        <td colspan='3'><fmt:message key="oscar.billing.ca.on.billingON.review.calculation"/></td>
                        <c:choose>
                            <c:when test="${reviewModel.billType ne 'PAT'}">
                                <td><fmt:message key="global.description"/></td>
                            </c:when>
                            <c:otherwise>
                                <td style="width:14%"><fmt:message key="global.description"/></td>
                                <td style="width:3%"><fmt:message key="oscar.billing.ca.on.billingON.review.colPayment"/></td>
                                <td style="width:3%"><fmt:message key="oscar.billing.ca.on.billingON.review.colDiscount"/></td>
                            </c:otherwise>
                        </c:choose>
                    </tr>
                    </c:if>

                    <%-- Service-code rows --%>
                    <c:forEach var="row" items="${reviewModel.serviceCodeRows}">
                        <c:if test="${row.codeValid}">
                            <tr class="myGreen">
                                <td style="text-align:center; width:3%">${row.n}</td>
                                <td style="text-align:right; width:12%"><carlos:encode value="${row.codeName}" context="html"/> (<carlos:encode value="${row.codeUnit}" context="html"/>)</td>
                                <td>
                                    <c:if test="${not empty row.warning}">
                                        <span style="float:left;" class="alert alert-warning">${carlos:forHtmlContent(row.warning)}</span>
                                    </c:if>
                                    <span style="float:right;"> <carlos:encode value="${row.codeFee}" context="html"/> x <carlos:encode value="${row.codeUnit}" context="html"/><c:if test="${row.gstApplied}"> + <carlos:encode value="${reviewModel.gstPercent}" context="html"/>% GST</c:if> =
                                        <input type="text" name="percCodeSubtotal_${row.rowIndex}" value="<carlos:encode value='${row.codeTotal}' context='htmlAttribute'/>" id="percCodeSubtotal_${row.rowIndex}"
                                               onBlur="calculateTotal();" onchange="validateFeeNumberic(${row.rowIndex})">
                                        <input type="hidden" name="xserviceCode_${row.rowIndex}" value="<carlos:encode value='${row.codeName}' context='htmlAttribute'/>">
                                        <input type="hidden" id="xserviceUnit_${row.rowIndex}" name="xserviceUnit_${row.rowIndex}" value="<carlos:encode value='${row.codeUnit}' context='htmlAttribute'/>">
                                    </span>
                                </td>
                                <c:choose>
                                    <c:when test="${reviewModel.billType ne 'PAT'}">
                                        <td style="width:25%"><carlos:encode value="${reviewModel.codeDescriptions[row.codeName]}" context="html"/></td>
                                    </c:when>
                                    <c:otherwise>
                                        <c:set var="paidValue" value="0.00"/>
                                        <oscar:oscarPropertiesCheck property="BILLING_REVIEW_AUTO_PAYMENT" value="yes">
                                            <c:set var="paidValue" value="${row.codeTotal}"/>
                                        </oscar:oscarPropertiesCheck>
                                        <td style="white-space:nowrap; width:14%">
                                            <pre><carlos:encode value="${row.codeDescription}" context="html"/></pre>
                                        </td>
                                        <td style="white-space:nowrap; width:3%">
                                            <input type="text" id="paid_${row.rowIndex}" name="paid_${row.rowIndex}"
                                                   value="<carlos:encode value='${paidValue}' context='htmlAttribute'/>"
                                                   onBlur="calculatePayment();"
                                                   onchange="validatePaymentNumberic(${row.rowIndex})"/>
                                        </td>
                                        <td style="white-space:nowrap; width:3%">
                                            <input type="text" id="discount_${row.rowIndex}"
                                                   name="discount_${row.rowIndex}" value="0.00"
                                                   onBlur="calculateDiscount();"
                                                   onchange="validateDiscountNumberic(${row.rowIndex})"/>
                                        </td>
                                    </c:otherwise>
                                </c:choose>
                            </tr>
                        </c:if>
                    </c:forEach>

                    <%-- Percent-code rows --%>
                    <c:forEach var="prow" items="${reviewModel.percCodeRows}">
                        <c:if test="${reviewModel.codeValid}">
                            <tr class="myPink">
                                <td style="text-align:center;">&nbsp;</td>
                                <td style="text-align:right;"><carlos:encode value="${prow.codeName}" context="html"/> (1)</td>
                                <td style="text-align:right;">
                                    <c:forEach var="seg" items="${prow.segments}">
                                        <input type="checkbox" id="percentCode" name="percCode_${prow.rowIndex}" value="<carlos:encode value='${seg.percTotal}' context='htmlAttribute'/>"
                                               onclick="onCheckMaster();"/> <carlos:encode value="${seg.percTotal}" context="html"/>(<carlos:encode value="${seg.factor}" context="html"/>x<carlos:encode value="${prow.percFee}" context="html"/>x<carlos:encode value="${prow.codeUnit}" context="html"/>) |
                                    </c:forEach>
                                    = <input type="text" name="percCodeSubtotal_${prow.rowIndex}" value="0.00"/>
                                    <input type="hidden" name="xserviceCode_${prow.rowIndex}" value="<carlos:encode value='${prow.codeName}' context='htmlAttribute'/>"/>
                                    <input type="hidden" name="xserviceUnit_${prow.rowIndex}" value="<carlos:encode value='${prow.codeUnit}' context='htmlAttribute'/>"/>
                                </td>
                                <td style="width:25%"><carlos:encode value="${reviewModel.codeDescriptions[prow.codeName]}" context="html"/></td>
                            </tr>
                        </c:if>
                    </c:forEach>

                    <c:if test="${reviewModel.codeValid}">
                        <tr>
                            <td style="text-align:left;" colspan="2">
                            </td>
                            <td style="text-align:right;" colspan='1' class="myGreen">

                                <fmt:message key="global.total"/>: <input type="text" id="total" name="total" value="0.00"
                                              onchange="onTotalChanged();"/>
                                <input type="hidden" name="totalItem" value="${reviewModel.totalItem}"/></td>

                            <script Language="JavaScript">

                                function onCheckMaster() {
                                    <c:forEach var="ph" items="${reviewModel.percJsHandlers}">
                                    var nSubtotal = 0.00;
                                    var nMin = ${carlos:forJavaScript(ph.min)};
                                    var nMax = ${carlos:forJavaScript(ph.max)};
                                    if (document.forms[0].percCode_${ph.iCheckNo}.length == undefined) {
                                        if (document.forms[0].percCode_${ph.iCheckNo}.checked) {
                                            nSubtotal = nSubtotal + (Number(document.forms[0].percCode_${ph.iCheckNo}.value) * 100);
                                        }
                                    }
                                    for (n = 0; n < document.forms[0].percCode_${ph.iCheckNo}.length; n++) {
                                        if (document.forms[0].percCode_${ph.iCheckNo}[n].checked) {
                                            nSubtotal = nSubtotal + (Number(document.forms[0].percCode_${ph.iCheckNo}[n].value) * 100);
                                        }
                                    }
                                    nSubtotal = Math.round(nSubtotal);
                                    ssubtotal = nSubtotal / 100 + "";
                                    if (ssubtotal.indexOf(".") < 0) {
                                        ssubtotal = ssubtotal + ".00";
                                    } else if ((ssubtotal.length - ssubtotal.indexOf('.')) <= 2) {
                                        ssubtotal = ssubtotal + "00".substring(0, (ssubtotal.length - ssubtotal.indexOf('.') - 1));
                                    }
                                    document.forms[0].percCodeSubtotal_${ph.iCheckNo}.value = ssubtotal;
                                    if (nMin > document.forms[0].percCodeSubtotal_${ph.iCheckNo}.value) {
                                        document.forms[0].percCodeSubtotal_${ph.iCheckNo}.value = nMin;
                                    } else if (nMax < document.forms[0].percCodeSubtotal_${ph.iCheckNo}.value) {
                                        document.forms[0].percCodeSubtotal_${ph.iCheckNo}.value = nMax;
                                    }
                                    </c:forEach>
                                    nSubtotal = 0.00;
                                    for (var i = 0; i < document.forms[0].elements.length; i++) {
                                        if (document.forms[0].elements[i].name.indexOf("percCodeSubtotal") >= 0) {
                                            nSubtotal = nSubtotal + document.forms[0].elements[i].value * 10 * 10;
                                        }
                                    }
                                    stotal = nSubtotal / 100 + "";
                                    if (stotal.indexOf(".") < 0) {
                                        stotal = stotal + ".00";
                                    } else if ((stotal.length - stotal.indexOf('.')) <= 2) {
                                        stotal = stotal + "00".substring(0, (stotal.length - stotal.indexOf('.') - 1));
                                    }
                                    var num = new Number(stotal);
                                    document.forms[0].total.value = num.toFixed(2);
                                }

                                var ntotal = 0.00;
                                for (var i = 0; i < document.forms[0].elements.length; i++) {
                                    if (document.forms[0].elements[i].name.indexOf("percCodeSubtotal") >= 0) {
                                        ntotal = ntotal + (document.forms[0].elements[i].value * 10 * 10);
                                    }
                                }
                                stotal = ntotal / 100 + "";
                                if (stotal.indexOf(".") < 0) {
                                    stotal = stotal + ".00";
                                } else if ((stotal.length - stotal.indexOf('.')) <= 2) {
                                    stotal = stotal + "00".substring(0, (stotal.length - stotal.indexOf('.') - 1));
                                }
                                var num = new Number(stotal);
                                document.forms[0].total.value = num.toFixed(2);

                            </script>

                        </tr>
                    </c:if>
                    <c:if test="${reviewModel.totalsParseFailed}">
                        <tr>
                            <td colspan="4" style="text-align:center;">
                                <div class='alert alert-danger' role='alert' style='margin: 8px 0;'>
                                    <strong><fmt:message key="oscar.billing.ca.on.billingON.review.totalsParseFailed"/></strong>
                                    <fmt:message key="oscar.billing.ca.on.billingON.review.totalsParseFailedDetail"/>
                                </div>
                            </td>
                        </tr>
                    </c:if>
                    <tr>

                        <td colspan="4" style="text-align:center; background-color:silver">
                            <input type="submit" value="<carlos:encode value='${msgBtnBackToEdit}' context='htmlAttribute'/>" class="btn btn-secondary"
                                   onclick="document.getElementById('billingAction').value='BACK_TO_EDIT';"/>
                            <c:choose>
                                <c:when test="${reviewModel.codeValid and not reviewModel.dupServiceCode and not reviewModel.totalsParseFailed}">
                                    <input type="submit" value="<carlos:encode value='${msgBtnSave}' context='htmlAttribute'/>" class="btn btn-primary"
                                           onclick="document.getElementById('billingAction').value='SAVE'; onClickSave();"/>
                                    <input type="submit" value="<carlos:encode value='${msgBtnSaveAndAdd}' context='htmlAttribute'/>" class="btn btn-secondary"
                                           onclick="document.getElementById('billingAction').value='SAVE_ADD_ANOTHER'; onClickSave();"/>
                                </c:when>
                                <c:when test="${reviewModel.dupServiceCode}">
                        <td>
                            <div class='alert alert-danger'><fmt:message key="oscar.billing.ca.on.billingON.review.dupServiceCodes"/></div>
                        </td>
                                </c:when>
                            </c:choose>
            </td>
        </tr>
    </table>

    </td>
    </tr>

    <c:if test="${reviewModel.percRendered}">
        <tr><td style='text-align:center'><br><span class='alert alert-info'><fmt:message key="oscar.billing.ca.on.billingON.review.percCodeHint"/></span></td></tr>
    </c:if>

    <c:if test="${reviewModel.codeValid and reviewModel.publicPayer}">
    <tr>
        <td>
            <br>
            <fmt:message key="billing.billingCorrection.msgNotes"/>:<br>
            <textarea name="comment" style="width:600px;"><carlos:encode value="${reviewModel.billingNotes}" context="html"/></textarea>
        </td>
    </tr>
    <tr>
        <td>
    </c:if>

    <c:if test="${reviewModel.codeValid and reviewModel.privatePayer}">
        </td>
    </tr>
    <tr>
        <td>
            <table class="border1" style="width:100%">
                <tr class="myYellow">
                    <td colspan='2'><fmt:message key="oscar.billing.ca.on.billingON.review.privateBilling"/></td>
                </tr>
                <tr>
                    <td style="width:80%">

                        <table id="privateBillInfo" style="width:100%">
                            <tr>
                                <td><fmt:message key="billing.billingCorrection.msgPayer"/> [<a href="#" onclick="scriptAttach('billTo'); return false;"><fmt:message key="global.btnSearch"/></a>]<br>
                                    <textarea name="billto" id="billTo" cols="30" rows="6">${carlos:forHtmlContent(reviewModel.patientAddress)}</textarea>
                                </td>
                                <td><fmt:message key="oscar.billing.ca.on.billingON.review.remitTo"/> [<a href="#" onclick="scriptAttach('remitTo'); return false;"><fmt:message key="global.btnSearch"/></a>]<br>
                                    <c:if test="${reviewModel.clinicAddressUnavailable}">
                                        <div class="alert alert-danger" role="alert"><fmt:message key="oscar.billing.ca.on.billingON.review.clinicAddressUnavailable"/></div>
                                    </c:if>
                                    <textarea name="remitto" id="remitTo" value="" cols="30"
                                              rows="6">${carlos:forHtmlContent(reviewModel.clinicAddress)}</textarea></td>
                                <td><fmt:message key="oscar.billing.ca.on.billingON.review.payee"/><br>
                                    <c:choose>
                                        <c:when test="${reviewModel.payeeFromConfigSet}">
                                            <textarea id="payee" name="payee" value="" cols="20" rows="6"><carlos:encode value="${reviewModel.payeeFromConfig}" context="html"/></textarea></td>
                                        </c:when>
                                        <c:otherwise>
                                            <textarea id="payee" name="payee" value="" cols="20" rows="6"><carlos:encode value="${reviewModel.payeeName}" context="html"/></textarea>
                    </td>
                    <input type="hidden" name="payeename1" id="payeename1" value="<carlos:encode value='${reviewModel.payeeName}' context='htmlAttribute'/>"/>
                                        </c:otherwise>
                                    </c:choose>
                </tr>
            </table>
            <table style="width:100%">
                <tr>
                    <td>
                        <fmt:message key="billing.billingCorrection.msgNotes"/>:<br>
                        <textarea name="comment" cols="100" rows="6"><carlos:encode value="${reviewModel.billingNotes}" context="html"/></textarea>
                    </td>
                    <td style="text-align:right">
                        <input type="hidden" name="provider_no"
                               value="<carlos:encode value='${reviewModel.payeeProviderNo}' context='htmlAttribute'/>"/>
                        <fmt:message key="admin.gstReport.table.gstBilled"/>:<input type="text" id="gst" name="gst" value="<carlos:encode value='${reviewModel.gstTotal}' context='htmlAttribute'/>"><br>
                        <input type="hidden" id="gstBilledTotal" name="gstBilledTotal" value="<carlos:encode value='${reviewModel.gstBilledTotal}' context='htmlAttribute'/>">
                        <fmt:message key="global.total"/>:<input type="text" id="stotal" disabled name="stotal" value="0.00"><br>
                        <fmt:message key="oscar.billing.ca.on.billingON.review.payments"/>:<input type="text" disabled name="payment1" id="payment" value="0.00"
                                        onDblClick="settlePayment();"/><br/>
                        <fmt:message key="oscar.billing.ca.on.billingON.review.discount"/>:<input type="text" disabled name="discount2" id="discount" value="0.00">
                    </td>
                </tr>
            </table>

        <td class="myGreen">
            <fmt:message key="oscar.billing.ca.on.billingON.review.paymentMethod"/>:<br/>
            <c:if test="${reviewModel.paymentTypeLookupFailed}">
                <div class="alert alert-danger" role="alert">
                    <fmt:message key="oscar.billing.ca.on.billingON.review.paymentTypesUnavailable"/>
                </div>
            </c:if>
            <c:forEach var="pt" items="${reviewModel.paymentTypes}" varStatus="pst">
                <input type="radio" name="payMethod" value="<carlos:encode value='${pt.id}' context='htmlAttribute'/>" id="payMethod_${pst.index * 2}"/><carlos:encode value="${pt.label}" context="html"/>
                <br/>
            </c:forEach>
        </td>
    </tr>
    <tr>
        <td colspan='2' align='center' bgcolor="silver">
            <input type="submit" value="<carlos:encode value='${msgBtnSavePrint}' context='htmlAttribute'/>" class="btn btn-secondary"
                   style="width: 150px;"
                   onclick="document.getElementById('billingAction').value='SAVE_PRINT';"/>
            <input type="button" id="settlePrintBtn" class="btn btn-primary"
                   value="<carlos:encode value='${msgBtnSettlePrint}' context='htmlAttribute'/>"
                   style="width: 160px;"
                   onclick="document.getElementById('billingAction').value='SETTLE_PRINT'; document.forms['titlesearch'].submit(); popupPage(700,720,'${pageContext.request.contextPath}/billing/CA/ON/ViewBillingON3rdInv');"/>
            <input type="hidden" name="total_payment" id="total_payment" value="0.00"/>
            <input type="hidden" name="total_discount" id="total_discount" value="0.00"/>
            <input type="hidden" name="refund" id="refund" value="0.00"/>
        </td>
    </tr>
    </table>

    </td></tr>
    <tr>
        <td>
    </c:if>

            <c:forEach var="pp" items="${reviewModel.allRequestParams}">
                <input type="hidden" name="<carlos:encode value='${pp.name}' context='htmlAttribute'/>"
                       value="<carlos:encode value='${pp.value}' context='htmlAttribute'/>"/>
            </c:forEach>
        </td>
    </tr>
    </table>

</form>

<script language="JavaScript">
    function calculatePayment() {
        var payment = 0.00;
        document.querySelectorAll("input[id^='paid_']").forEach(function (el) {
            if (el != null && el.value.length > 0) {
                payment = parseFloat(payment) + parseFloat(el.value);
                payment = payment.toFixed(2);
            }
        });
        el = document.getElementById("payment");
        if (el != null) {
            document.getElementById("payment").value = payment;
            document.getElementById("total_payment").value = payment;
        }
    }

    function calculateDiscount() {
        var discount = 0.00;
        document.querySelectorAll("input[id^='discount_']").forEach(function (el) {
            if (el != null && el.value.length > 0) {
                discount = parseFloat(discount) + parseFloat(el.value);
                discount = discount.toFixed(2);
            }
        });

        document.getElementById("discount").value = discount;
        document.getElementById("total_discount").value = discount;
    }

    function calculateTotal() {
        var total = 0.00;
        document.querySelectorAll("input[id^='percCodeSubtotal_']").forEach(function (el) {
            if (el != null && el.value.length > 0) {
                total = parseFloat(total) + parseFloat(el.value);
                total = total.toFixed(2);
            }
        });
        document.getElementById("total").value = total;
        document.getElementById("gstBilledTotal").value = total;
        document.getElementById("stotal").value = total;
    }

    function onTotalChanged() {
        var val = document.getElementById("total").value;
        var regexNumberic = /^([1-9]\d{0,9}|0)(\.\d{1,2})?$/;
        if (!regexNumberic.test(val)) {
            calculateTotal();
            alert("${carlos:forJavaScript(msgEnterNumbers)}");
            return;
        }

        var total = document.getElementById("total").value;
        document.getElementById("gstBilledTotal").value = total;
        document.getElementById("stotal").value = total;
    }

    function addToDiseaseRegistry() {
        if (validateItems()) {
            var url = ctx + "/oscarResearch/oscarDxResearch/dxResearch";
            data = jQuery('#dxForm').serialize();
            jQuery.post(url, data, function (data) {
                jQuery("#dxListing").html(data);
                getNewCurrentDxCodeList();
            });

        }
    }

    function validateItems() {
        var ret = false;

        dxChecks = document.getElementsByName("xml_research");
        for (idx = 0; idx < dxChecks.length; ++idx) {
            if (dxChecks[idx].checked) {
                ret = true;
                break;
            }
        }
        if (!ret) alert("${carlos:forJavaScript(msgNothingSelected)}");
        else ret = confirm("${carlos:forJavaScript(msgConfirmAddDxRegistry)}");
        return ret;
    }

    function getNewCurrentDxCodeList(origRequest) {
        var url = ctx + "/oscarResearch/oscarDxResearch/ViewCurrentCodeList";
        var ran_number = Math.round(Math.random() * 1000000);
        var params = "demographicNo=" + encodeURIComponent("${carlos:forJavaScript(reviewModel.requestParamEchoes['demographic_no'])}") + "&rand=" + ran_number;

        jQuery.ajax({
            url: url,
            type: "get",
            dataType: "html",
            data: params,
            success: function (returnData) {
                jQuery("#dxFullListing").html(returnData);
            },
            error: function (e) {
                alert(e);
            }
        });
    }
</script>

<oscar:oscarPropertiesCheck property="DX_QUICK_LIST_BILLING_REVIEW" value="yes">

    <div class="dxBox">
        <h3>&nbsp;<fmt:message key="oscar.billing.ca.on.billingON.review.currentPatientDxList"/> &nbsp;<a href="#" onclick="toggle('dxFullListing'); return false;"
                                                   style="font-size:small;"><fmt:message key="oscar.billing.ca.on.billingON.review.showHide"/></a></h3>
        <div class="wrapper" id="dxFullListing">
            <jsp:include page="/oscarResearch/oscarDxResearch/ViewCurrentCodeList">
                <jsp:param name="demographicNo" value="${reviewModel.requestParamEchoes['demographic_no']}"/>
            </jsp:include>
        </div>
    </div>

    <div class="dxBox">

        <h3>&nbsp;<fmt:message key="oscar.billing.ca.on.billingON.review.dxQuickPick"/> &nbsp;<a href="#" onclick="toggle('dxForm'); return false;"
                                                   style="font-size:small;"><fmt:message key="oscar.billing.ca.on.billingON.review.showHide"/></a></h3>
        <form id="dxForm">
            <input type="hidden" name="demographicNo" value="<carlos:encode value='${reviewModel.requestParamEchoes[\"demographic_no\"]}' context='htmlAttribute'/>"/>
            <input type="hidden" name="providerNo" value="<carlos:encode value='${reviewModel.loggedInUserNo}' context='htmlAttribute'/>"/>
            <input type="hidden" name="forward" value=""/>
            <input type="hidden" name="forwardTo" value="codeList"/>
            <div class="wrapper" id="dxListing">
                <jsp:include page="/WEB-INF/jsp/oscarResearch/oscarDxResearch/quickCodeList.jsp">
                    <jsp:param name="demographicNo" value="${reviewModel.requestParamEchoes['demographic_no']}"/>
                </jsp:include>
            </div>
            <input type="button" value="<carlos:encode value='${msgBtnAddDxRegistry}' context='htmlAttribute'/>" class="btn btn-secondary" onclick="addToDiseaseRegistry()"/>
        </form>
    </div>

</oscar:oscarPropertiesCheck>

</body>
</html>
