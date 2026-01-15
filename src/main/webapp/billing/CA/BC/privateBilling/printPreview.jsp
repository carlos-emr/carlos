<%--
  Author: Charles Liu <charles.liu@nondfa.com>
  Company: WELL Health Technologies Corp.
  Date: December 6, 2018
 --%>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.commn.dao.PropertyDao" %>
<%@ page import="ca.openosp.openo.commn.dao.SystemPreferencesDao" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.data.BillingPreferencesDAO" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.data.BillingPreference" %>
<%@ page import="ca.openosp.openo.commn.model.Property" %>
<%@ page import="java.util.List" %>
<%@ page import="ca.openosp.openo.clinic.ClinicData" %>
<%@ page import="java.util.StringTokenizer" %>
<%@ page import="java.util.Vector" %>
<%@ page import="ca.openosp.openo.util.StringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.SystemPreferences" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%
    if (session.getValue("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
%>

<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
<html>
<head>
    <title>Private Billing Statement - Print Preview</title>
    <link rel="stylesheet" type="text/css" media="all" href="${ctx}/library/bootstrap/3.0.0/css/bootstrap.min.css">
    <style type="text/css">
        @media print, screen {
            body {
                color: black;
                background-color: #FFF;
                margin: 0px;
                border: none;
                padding: 0px;
                margin-right: 5px;
                margin-left: 5px;
            }

            h1, h2 {
                border: none;
                padding: 0px;
                margin: 0px;
            }

            h2 {
                font-weight: 100;
            }

            table {
                border: none;
                border-collapse: collapse;
                width: 100%;
                margin: 0px;
                padding: 0px;
            }

            th {
                border-bottom: black 1px solid;
            }

            th#payableTitle {
                border: none;
                border-top: black 1px solid;
                font-size: 16pt;
            }

            td, th {
                text-align: left;
                verticle-align: top;
                padding: 5px;
            }

            #payableTo {
                border-left: #ccc 1px solid;
                border-right: #ccc 1px solid;
                margin-top: 10px;
                padding-left: 10px;
            }

            #itemsHead {
                border-bottom: black 1px solid;
            }

            #itemsHead td {
                background-color: #ccc !important;
            }

            tr.spacer td {
                height: 20px;
            }

            tr#recipient input {
                width: 75%;
                background-color: transparent;
                overflow: visible;
            }

            #statementComment {
                width: 75%;
                height: 125px;
                overflow: visible;
            }

            .Print {
                display: none;
            }
        }

        @media screen {
            div.billPage {
                margin: 0;
                padding: 0 0 250px 0;
                border-bottom: 1px dotted #c5c5c5;
            }
        }

        @media print {
            @page {
                size: letter portrait;
                margin: 0in;
            }

            div.billPage {
                page-break-after: always;
                margin: 0;
                padding: 0.5in 0.5in 0 0.5in;
                border: none;
            }

            table td, table th {
                font-size: 12px;
            }

            .noPrint {
                display: none;
            }

            .Print {
                display: inline;
            }

            .noBorder {
                border: none;
                margin: 0px;
                padding: 0px;
                font-size: 12px;
                color: black;
                font: inherit;
                width: 100%;
            }

            a {
                text-decoration: none;
                border: none;
                color: black;
            }

            strong#payableName {
                font-size: 14pt;
            }

            .yesprint {
                display: block;
            }

        }

        .payeeInfo {
            white-space: pre-line;
            line-height: 13pt;
        }
    </style>
</head>

<body>
<div class="noPrint btn-toolbar" role="toolbar" arial-label="Toolbar">
    <div class="btn-group mr-2" role="group" arial-label="Button group 1">
        <button class="btn btn-primary" onclick="window.print();">
            <span class="glyphicon glyphicon-print" aria-hidden="true"></span>
            Print
        </button>
        <button class="btn btn-secondary" onclick="goBack();">Go Back</button>
    </div>

    <div class="btn-group mr-2" role="group" arial-label="Button group 2">
        <div class="checkbox">
            <label>
                <input type="checkbox" id="cbBillToClinic" style="margin-top:0px;" onclick="handleClickBillToClinic()"
                       <c:if test="${billToClinic}">checked</c:if>
                > Bill To Clinic
            </label>
        </div>
    </div>
</div>

<%-- Note: setting locale would should negative balance in parentheses --%>
<fmt:setLocale value="en_US"/>

<c:forEach var="patientBill" items="${patientBills}">
    <div class="billPage">
        <table>
                <%-- Statement heading  Table width = 7 columns --%>
            <tr>
                <th colspan="4">
                    <h1>STATEMENT</h1>
                </th>
                <th colspan="4" style="text-align:right;">${date}</th>
            </tr>

            <tr>
                <td colspan="8">
                    <h2>${patientBill.clinicName}</h2>
                        ${patientBill.clinicAddress},
                        ${patientBill.clinicCity}, ${patientBill.clinicProvince}
                        ${patientBill.clinicPostal}<br/>
                    Telephone: ${patientBill.clinicPhone}<br/>
                    Fax: ${patientBill.clinicFax}<br/>
                </td>
            </tr>

                <%-- Billing addresses  --%>
            <tr>
                <th colspan="4">Billing To:</th>
                <th colspan="4" style="padding-left:30px">Patient</th>
            </tr>

            <tr id="recipient">
                    <%-- billing address - defaults to patient if not specified. --%>
                <td colspan="4">
                        ${patientBill.recipientName}<br/>
                        ${patientBill.recipientAddress}<br/>
                        ${patientBill.recipientCity}, ${patientBill.recipientProvince}<br/>
                        ${patientBill.recipientPostal}
                </td>

                    <%-- patient data --%>
                <td colspan="4" style="padding-left:30px">
                        ${patientBill.patientFirstName} ${patientBill.patientLastName}<br/>
                        ${patientBill.patientAddress}<br/>
                        ${patientBill.patientCity}, ${patientBill.patientProvince}<br/>
                        ${patientBill.patientPostal}<br/>
                    Birth Date:
                        ${patientBill.patientMonthOfBirth}&#47;${patientBill.patientDateOfBirth}&#47;${patientBill.patientYearOfBirth}
                </td>
            </tr>

            <tr class="spacer">
                <td colspan="8"></td>
            </tr>

                <%-- Invoice items  --%>
            <tr>
                <th colspan="8">Items</th>
            </tr>

            <tr id="itemsHead">
                <td>Invoice</td>
                <td>Date</td>
                <td>Practitioner</td>
                <td>GST #</td>
                <td>Service Code</td>
                <td>Description</td>
                <td>GST Amount</td>
                <td>Amount</td>
                <td>Received</td>
            </tr>

                <%-- iterate the invoice results --%>
            <c:set var="balance" value="${ 0.00 }" scope="page"/>
            <c:set var="gstTotal" value="${ 0.00 }" scope="page"/>
            <c:set var="payments" value="${ 0.00 }" scope="page"/>
            <c:set var="providerNumber" value="" scope="page"/>
            <c:set var="gstNo" value="" scope="page"/>
            <c:set var="providerName" value="" scope="page"/>
            <c:set var="providerFormattedName" value="" scope="page"/>

            <c:forEach items="${patientBill.invoiceItems}" var="invoice">

                <%-- add the totals --%>
                <c:set var="balance" value="${ balance + (invoice.bill_amount - invoice.gst)}" scope="page"/>
                <c:set var="gstTotal" value="${gstTotal + invoice.gst}" scope="page"/>
                <c:set var="payments" value="${ payments + invoice.amount_received }" scope="page"/>

                <tr>
                    <td>${invoice.billing_no}</td>
                    <td>${invoice.billing_date}</td>
                    <td>
                        <c:set var="providerNumber" value="${ invoice.provider_no }" scope="page"/>
                        <%
                            String providerNumber = (String) pageContext.getAttribute("providerNumber");
                            ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
                            Provider provider = providerDao.getProvider(providerNumber);
                            pageContext.setAttribute("providerName", Encode.forHtml(provider.getFullName()));
                            pageContext.setAttribute("providerFormattedName", provider.getFormattedName());
                        %>
                            ${providerFormattedName}
                    </td>
                    <td>${invoice.gstNo}</td>
                    <td>${invoice.billing_code}</td>
                    <td>${invoice.description}</td>
                    <td>
                        <fmt:formatNumber type="currency" groupingUsed="true" minFractionDigits="2"
                                          value="${invoice.gst}"/>
                    </td>
                    <td>
                        <fmt:formatNumber type="currency" groupingUsed="true" minFractionDigits="2"
                                          value="${ invoice.bill_amount }"/>
                    </td>
                    <td>
                        <fmt:formatNumber type="currency" groupingUsed="true" minFractionDigits="2"
                                          value="${ invoice.amount_received }"/>
                    </td>
                </tr>

            </c:forEach>
            <tr class="spacer">
                <td colspan="8"></td>
            </tr>

                <%-- Payment Details  --%>
            <tr>
                <th id="payableTitle" colspan="8">Please Make Cheque Payable To:</th>
            </tr>

            <tr style="border-top:#ccc 1px solid;">
                <td id="payableTo" colspan="5" rowspan="6">
                    <%
                        PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);
                        SystemPreferencesDao systemPreferencesDao = SpringUtils.getBean(SystemPreferencesDao.class);
                        BillingPreferencesDAO billingPreferencesDAO = SpringUtils.getBean(BillingPreferencesDAO.class);
                        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
                        String providerNumber = (String) pageContext.getAttribute("providerNumber");
                        Provider p = providerDao.getProvider(providerNumber);

                        BillingPreference billingPreference = billingPreferencesDAO.getUserBillingPreference(p.getProviderNo());

                        Provider payeeProvider = providerDao.getProvider(billingPreference != null ? "" + billingPreference.getDefaultPayeeNo() : null);

                        String payeeInfo;
                        if (billingPreference == null || "NONE".equals(billingPreference.getDefaultPayeeNo())) {
                            payeeInfo = "";
                        } else {
                            if ("CUSTOM".equals(billingPreference.getDefaultPayeeNo())) {
                                List<Property> propList = propertyDao.findByNameAndProvider("invoice_payee_info", p.getProviderNo());
                                payeeInfo = !propList.isEmpty() ? propList.get(0).getValue() : "";
                            } else {
                                payeeInfo = (payeeProvider != null ? ("Dr. " + payeeProvider.getFirstName() + " " + payeeProvider.getLastName()) : "");
                            }
                        }

                        ClinicData clinic = new ClinicData();

                        String strPhones = clinic.getClinicDelimPhone();
                        if (strPhones == null) {
                            strPhones = "";
                        }
                        String strFaxes = clinic.getClinicDelimFax();
                        if (strFaxes == null) {
                            strFaxes = "";
                        }
                        Vector vecPhones = new Vector();
                        Vector vecFaxes = new Vector();
                        StringTokenizer st = new StringTokenizer(strPhones, "|");
                        while (st.hasMoreTokens()) {
                            vecPhones.add(st.nextToken());
                        }
                        st = new StringTokenizer(strFaxes, "|");
                        while (st.hasMoreTokens()) {
                            vecFaxes.add(st.nextToken());
                        }

                    %>
                    <% if (!StringUtils.isNullOrEmpty(payeeInfo)) { %>
                    <div class="payeeInfo"><%=Encode.forHtml(payeeInfo)%>
                    </div>
                    <% }
                        //Default to true when not found
                        if (propertyDao.findByNameAndProvider("invoice_payee_display_clinic", p.getProviderNo()).isEmpty() || propertyDao.isActiveBooleanProperty("invoice_payee_display_clinic", p.getProviderNo())) {

                    %>

                    <% SystemPreferences invoiceClinicInfo = systemPreferencesDao.findPreferenceByName(SystemPreferences.GENERAL_SETTINGS_KEYS.invoice_custom_clinic_info);
                        if (invoiceClinicInfo == null || StringUtils.isNullOrEmpty(invoiceClinicInfo.getValue())) { %>
                    <div>
                        <%=Encode.forHtml(clinic.getClinicName())%>
                    </div>
                    <div><%=Encode.forHtml(clinic.getClinicAddress() + ", " + clinic.getClinicCity() + ", " + clinic.getClinicProvince() + " " + clinic.getClinicPostal())%>
                    </div>
                    <div id="clinicPhone">
                        Telephone: <%=vecPhones.size() >= 1 ? vecPhones.elementAt(0) : clinic.getClinicPhone()%>
                    </div>
                    <div id="clinicFax"> Fax: <%=vecFaxes.size() >= 1 ? vecFaxes.elementAt(0) : clinic.getClinicFax()%>
                    </div>
                    <% } else { %>

                    <div class="payeeInfo"><%= Encode.forHtml(invoiceClinicInfo.getValue())%>
                    </div>

                    <% } %>
                    <% } %>
                </td>

                <td style="text-align:right;">
                    Subtotal:
                </td>

                <td style="text-align:right;">
                    <fmt:formatNumber type="currency" groupingUsed="true" minFractionDigits="2" value="${ balance }"/>
                </td>
            </tr>

            <tr>
                <td style="text-align:right;">Total GST:</td>
                <td style="text-align:right;">
                    <fmt:formatNumber type="currency" groupingUsed="true" minFractionDigits="2" value="${ gstTotal }"/>
                </td>
            </tr>

            <tr>
                <td style="text-align:right;">Total:</td>
                <td style="text-align:right;">
                    <fmt:formatNumber type="currency" groupingUsed="true" minFractionDigits="2"
                                      value="${  balance + gstTotal }"/>
                </td>
            </tr>

            <tr>
                <td style="text-align:right;">Payments:</td>
                <td style="text-align:right;">
                    <fmt:formatNumber type="currency" groupingUsed="true" minFractionDigits="2" value="${ payments }"/>
                </td>
            </tr>

            <tr>
                <td style="text-align:right;">Refunds:</td>
                <td style="text-align:right;">
                    <fmt:formatNumber type="currency" groupingUsed="true" minFractionDigits="2" value="0"/>
                </td>
            </tr>

            <c:set var="finalBalance" value="${ (balance + gstTotal) - payments }" scope="page"/>

            <tr id="finalBalance" style="border-bottom:#ccc 1px solid;">
                <td style="text-align:right;">
                    <strong>Balance:</strong>
                </td>
                <td style="text-align:right;">
                    <strong>
                        <fmt:formatNumber type="currency" groupingUsed="true" minFractionDigits="2"
                                          value="${ finalBalance }"/>
                    </strong>
                </td>
            </tr>

            <tr class="spacer">
                <td colspan="8"></td>
            </tr>

                <%-- Comments input  --%>
            <tr>
                <th id="commentTitle" colspan="8">Comments:</th>
            </tr>
            <tr>
                <td colspan="8">
                    <textarea id="statementComment" name="statementComment" class="noBorder"></textarea>
                </td>
            </tr>

            <tr>
                <td style="border-bottom:black 2px solid;" class="spacer" colspan="8"></td>
            </tr>

            <tr class="spacer">
                <td colspan="8">
                    <p class="yesprint"><b>Personal Health Information: CONFIDENTIAL</b><br/>
                        <b>END OF PRINTED DOCUMENT</b>
                    </p>
                </td>
            </tr>
        </table>

    </div>
</c:forEach>

<div class="noPrint btn-toolbar" role="toolbar" arial-label="Toolbar">
    <div class="btn-group mr-2" role="group" arial-label="Button group 1">
        <button class="btn btn-primary" onclick="window.print();">
            <span class="glyphicon glyphicon-print" aria-hidden="true"></span>
            Print
        </button>
        <button class="btn btn-secondary" onclick="goBack();">Go Back</button>
    </div>
</div>

<script type="text/javascript" src="${ctx}/js/jquery-1.12.3.js"></script>
<script type="text/javascript" src="${ctx}/library/bootstrap/3.0.0/js/bootstrap.min.js"></script>
<script type="text/javascript">
    function handleClickBillToClinic() {
        var billToClinic = $("input:checkbox#cbBillToClinic").is(":checked");
        var encodedParams = encodeURIComponent('${billIds}');
        // redirect to print-ready page via controller
        window.location.href = "${ctx}/PrivateBillingController?action=printPreviewBills&billToClinic=" + billToClinic + "&billIds=" + encodedParams;
    }

    function goBack() {
        window.location.href = "${ctx}/PrivateBillingController?action=listPrivateBills";
    }
</script>
</body>
</html>