<%--
  Author: Charles Liu <charles.liu@nondfa.com>
  Company: WELL Health Technologies Corp.
  Date: December 6, 2018
 --%>

<%@ page import="java.util.*" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>



<%
    if (session.getValue("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
%>

<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html>
    <head>
        <meta charset="utf-8">
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.PrivateBillingStatement"/></title>
        <link rel="stylesheet" type="text/css" media="all" href="${ctx}/library/bootstrap/3.0.0/css/bootstrap.min.css">
        <style>
            .table > tbody > tr.highlight_pink {
                background-color: pink;
            }

            .table > tbody > tr.highlight_yellow {
                background-color: yellow;
            }

            .table > tbody > tr.highlight_orange {
                background-color: orange;
            }

            .table > tbody > tr.highlight_default {
                background-color: white;
            }
        </style>
    </head>

    <body>
    <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.PrivateBillingStatement"/></h3>

    <div class="container-fluid well">

        <h4>Total Private Patient Bills: ${bills.size()}</h4>

        <div class="btn-toolbar" role="toolbar" arial-label="Toolbar">
            <div class="btn-group mr-2" role="group">Filter By:
                <select name="providerList" id="providerList" class="selectpicker" style="height:38px;margin-top:-1px;"
                        onchange="handleFilterByProvider()">
                    <option value="%">All Providers</option>
                    <c:forEach var="provider" items="${providers}">
                        <option value="${provider.providerNo}" ${providerId==provider.providerNo ? 'selected' : ''}>${provider.getFormattedName()}</option>
                    </c:forEach>
                </select>
            </div>
            <div class="btn-group mr-2" role="group" arial-label="Button group 1">
                <button type="button" id="btnPrintSelected" class="btn btn-primary" onclick="printSelected();">
                    <span class="glyphicon glyphicon-print" aria-hidden="true"></span>
                    Print Selected
                </button>
            </div>
            <div class="btn-group mr-2" role="group" arial-label="Button group 2">
                <div class="checkbox">
                    <label>
                        <input style="margin-top:0px;" type="checkbox" id="cbBillToClinic" checked> Bill To Clinic
                    </label>
                </div>
            </div>
        </div>

        <hr>

        <table class="table table-condensed">
            <thead>
            <tr>
                <th>
                        <%-- master checkbox to check/uncheck row checkboxes --%>
                    <input type="checkbox" id="master" onclick="checkAllCaseCheckboxes();"/>
                </th>
                <th>Invoice Date</th>
                <th>Type</th>
                <th>Patient</th>
                <th>Provider</th>
                <th>Recipient</th>
                <th>Balance</th>
                <th>Items</th>
                <th>Print</th>
            </tr>
            </thead>
            <tbody>

            <c:set var="providerNumber" value="" scope="page"/>
            <c:set var="providerName" value="" scope="page"/>

            <c:forEach var="invoice" items="${bills}">

                <%-- Highlight row based on invoice status (billing type):
                    - B: pink
                    - O: yellow
                    - D: orange
                    - default: white
                --%>
                <c:choose>
                    <c:when test="${invoice.status=='B'}">
                        <c:set var="rowstyle" value="highlight_pink"/>
                    </c:when>
                    <c:when test="${invoice.status=='O'}">
                        <c:set var="rowstyle" value="highlight_yellow"/>
                    </c:when>
                    <c:when test="${invoice.status=='D'}">
                        <c:set var="rowstyle" value="highlight_orange"/>
                    </c:when>
                    <c:otherwise>
                        <c:set var="rowstyle" value="highlight_default"/>
                    </c:otherwise>
                </c:choose>
                <tr class="${rowstyle}">
                    <td><input type="checkbox" class="case" value="${invoice.demographicNumber}|${invoice.recipientId}"
                               onclick="checkMasterCheckbox();"/></td>

                        <%-- Invoice Date --%>
                    <td>${invoice.billingDate}</td>

                        <%-- Billing Type & Status --%>
                    <td>${invoice.billingType} - ${invoice.status}</td>

                        <%-- Patient --%>
                    <td>${invoice.demographicName}</td>

                        <%-- Provider
                          - show providers name from provider_no
                        --%>
                    <td>
                        <c:set var="providerNumber" value="${invoice.providerNumber}" scope="page"/>
                        <%
                            ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
                            String providerNumber = (String) pageContext.getAttribute("providerNumber");
                            Provider provider = providerDao.getProvider(providerNumber);
                            pageContext.setAttribute("providerName", provider.getFormattedName());
                        %>
                            ${providerName}
                    </td>

                        <%-- Recipient:
                          - by default, show the bill recipient's name
                          - if it's empty, just display 'Patient'
                        --%>
                    <td><c:out value="${invoice.recipientName}" default="Patient"/></td>

                        <%-- Balance:
                          - by default, show balance in Canadian dollars
                        --%>
                    <td>
                        <fmt:setLocale value="en_CA"/>
                        <fmt:formatNumber value="${invoice.balance}" type="currency"/>
                    </td>

                        <%-- Items:
                          - show the number unpaid bills for this patient
                          - on click, go to the 'Edit Invoices' page
                        --%>
                    <td>
                        <a href="javascript: popupPage( 700, 1000, '${ctx}/billing/CA/BC/billStatus.jsp?showPRIV=show&providerview=ALL&verCode=V03&Submit=Create+Report&xml_vdate=&xml_appointment_date=&demographicNo=${ invoice.demographicNumber }&filterPatient=true&submitted=yes' );">
                                ${invoice.billingCount}
                        </a>
                    </td>

                        <%-- pop up a printer-frieldy private billing statement page --%>
                    <td>
                        <button class="btn btn-primary btn-xs"
                                value="${invoice.demographicNumber}|${invoice.recipientId}"
                                onclick="printItem(this.value)">
                            <span class="glyphicon glyphicon-print" aria-hidden="true"></span>
                            print
                        </button>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
    </div>

    <script type="text/javascript" src="${ctx}/js/jquery-1.12.3.js"></script>
    <script type="text/javascript" src="${ctx}/library/bootstrap/3.0.0/js/bootstrap.min.js"></script>
    <script type="text/javascript" src="${ctx}/js/global.js"></script>
    <script type="text/javascript">
        function printItem(itemValue) {
            var billToClinic = $("input:checkbox#cbBillToClinic").is(":checked");
            var values = itemValue.split('|');
            var selectedBillIds = [{demographicNumber: values[0], recipientId: values[1]}];
            generatePrintFriendlyPage(selectedBillIds, billToClinic);
        }

        function printSelected() {
            var billToClinic = $("input:checkbox#cbBillToClinic").is(":checked");
            var selectedBillIds = $("input:checkbox.case:checked").map(function () {
                var values = this.value.split('|');
                return {demographicNumber: values[0], recipientId: values[1]};
            }).get();
            generatePrintFriendlyPage(selectedBillIds, billToClinic);
        }

        function generatePrintFriendlyPage(billIds, billToClinic) {
            var encodedParams = encodeURIComponent(JSON.stringify(billIds));
            // redirect to print-ready page via controller
            window.location.href = "${ctx}/PrivateBillingController?action=printPreviewBills&billToClinic=" + billToClinic + "&billIds=" + encodedParams;
        }

        function checkAllCaseCheckboxes() {
            if ($("input:checkbox#master").is(":checked")) {
                $("input:checkbox.case").prop("checked", true);
            } else {
                $("input:checkbox.case").prop("checked", false);
            }
            enableBtnPrintSelected();
        }

        function checkMasterCheckbox() {
            if ($("input:checkbox.case").length == $("input:checkbox.case:checked").length) {
                $("#master").prop("checked", true);
            } else {
                $("#master").prop("checked", false);
            }
            enableBtnPrintSelected();
        }

        function enableBtnPrintSelected() {
            if ($("input:checkbox.case:checked").length > 0) {
                $("#btnPrintSelected").removeClass("disabled");
            } else {
                $("#btnPrintSelected").addClass("disabled");
            }
        }

        function handleFilterByProvider() {
            var providerId = $("select#providerList").val();
            window.location.href = "${ctx}/PrivateBillingController?action=listPrivateBills&providerId=" + providerId;
        }

        $(function () {
            // after the page is loaded, see if the print button needs to be disabled
            enableBtnPrintSelected();
        });
    </script>
    </body>
</html>