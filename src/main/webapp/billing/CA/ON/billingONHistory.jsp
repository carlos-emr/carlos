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
    Ontario Billing History popup.

    Purpose: Displays all billing history records for a demographic in a DataTables-powered
    table with Bootstrap styling.

    Parameters:
        demographic_no  - Patient demographic number
        last_name       - Patient last name (for display)
        first_name      - Patient first name (for display)
        orderby         - Sort column (appointment_date)
        displaymode     - Display mode (appt_history)
        dboperation     - Database operation (appt_history)
        limit1          - Unused (DataTables handles client-side pagination)
        limit2          - Unused (DataTables handles client-side pagination)

    @since 2006
--%>
<%@page import="java.nio.charset.StandardCharsets" %>
<%@page import="java.math.BigDecimal" %>
<%
    if (session.getAttribute("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.htm");
    String curProvider_no;
    curProvider_no = (String) session.getAttribute("user");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>
<%@ page
        import="java.util.*, java.sql.*, java.net.*, io.github.carlos_emr.*, io.github.carlos_emr.carlos.db.*"
        errorPage="/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.billing.ca.on.data.*" %>
<%@page import="io.github.carlos_emr.carlos.billing.CA.ON.dao.*" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.BillingONExtDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>

<%@page import="io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.BillingONPayment" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.BillingONCHeader1" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingItemData" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.JdbcBillingReviewImpl" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingDataHlp" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%
    BillingONPaymentDao billingOnPaymentDao = SpringUtils.getBean(BillingONPaymentDao.class);
    BillingONCHeader1Dao bCh1Dao = SpringUtils.getBean(BillingONCHeader1Dao.class);
%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<jsp:useBean id="providerBean" class="java.util.Properties"
             scope="session"/>
<!DOCTYPE html>
<html>
<head>
    <title>Billing History</title>
    <%@ include file="/includes/global-head.jspf" %>
    <link href="<%=request.getContextPath()%>/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css" rel="stylesheet" type="text/css">
    <script src="<%=request.getContextPath()%>/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.min.js"></script>
    <script src="<%=request.getContextPath()%>/library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap5.min.js"></script>

    <script language="JavaScript">
        function onUnbilled(billingNo, billCode) {
            if (confirm("<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.onUnbilled"/>")) {
                var form = document.createElement('form');
                form.method = 'post';
                form.action = 'billingDeleteNoAppt.jsp';
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
                    url: '<%=request.getContextPath()%>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json'
                }
            });
        });
    </script>

    <oscar:customInterface section="billingONHistory"/>
</head>
<body>

<nav class="navbar navbar-dark bg-dark">
    <div class="container-fluid">
        <span class="navbar-brand">Billing History</span>
        <span class="navbar-text text-white-50">
            <em><%=request.getParameter("last_name")%>, <%=request.getParameter("first_name")%></em>
            &nbsp;(<%=request.getParameter("demographic_no")%>)
        </span>
    </div>
</nav>

<div class="container-fluid mt-3">
    <table id="billingHistoryTable" class="table table-striped table-hover table-bordered">
        <thead>
            <tr>
                <th>Invoice No.</th>
                <th>Billing Doctor</th>
                <th>Appt. Date</th>
                <th>Bill Type</th>
                <th>Service Code</th>
                <th>Dx</th>
                <th>Balance</th>
                <th>Fee</th>
                <th>Comments</th>
            </tr>
        </thead>
        <tbody>
        <% // Load all records for DataTables client-side pagination
            JdbcBillingReviewImpl dbObj = new JdbcBillingReviewImpl();
            BillingONExtDao billingOnExtDao = (BillingONExtDao) SpringUtils.getBean(BillingONExtDao.class);
            List aL = dbObj.getBillingHist(request.getParameter("demographic_no"), 10000, 0, null);
            for (int i = 0; i < aL.size(); i = i + 2) {
                BillingClaimHeader1Data obj = (BillingClaimHeader1Data) aL.get(i);
                BillingItemData itObj = (BillingItemData) aL.get(i + 1);
                String strBillType = obj.getPay_program();
                if (strBillType != null) {
                    if (strBillType.matches(BillingDataHlp.BILLINGMATCHSTRING_3RDPARTY)) {
                        if (BillingDataHlp.propBillingType.getProperty(obj.getStatus(), "").equals("Settled")) {
                            strBillType += " Settled";
                        }
                    } else {
                        strBillType = BillingDataHlp.propBillingType.getProperty(obj.getStatus(), "");
                    }
                } else {
                    strBillType = "";
                }

                BigDecimal balance = new BigDecimal("0.00");
                if ("PAT".equals(strBillType) || "PAT Settled".equals(strBillType)) {
                    int billingNo = Integer.parseInt(obj.getId());
                    BillingONCHeader1 bCh1 = bCh1Dao.find(billingNo);

                    BigDecimal total = bCh1.getTotal();
                    BigDecimal sumOfPay = BigDecimal.ZERO;
                    BigDecimal sumOfDiscount = BigDecimal.ZERO;
                    BigDecimal sumOfRefund = BigDecimal.ZERO;
                    BigDecimal sumOfCredit = BigDecimal.ZERO;

                    for (BillingONPayment payment : billingOnPaymentDao.find3rdPartyPaymentsByBillingNo(billingNo)) {
                        sumOfPay = sumOfPay.add(payment.getTotal_payment());
                        sumOfDiscount = sumOfDiscount.add(payment.getTotal_discount());
                        sumOfRefund = sumOfRefund.add(payment.getTotal_refund());
                        sumOfCredit = sumOfCredit.add(payment.getTotal_credit());
                    }

                    balance = total.subtract(sumOfPay).subtract(sumOfDiscount).add(sumOfCredit);
                }
        %>
            <tr>
                <td class="text-center">
                    <a href="javascript:void(0)"
                       onclick="popupPage(600,800, 'billingONDisplay.jsp?billing_no=<%=obj.getId()%>')"
                       title="Billing Display"><%=obj.getId()%>
                    </a>

                    <security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="w">
                        <a href="javascript:void(0)"
                           onclick="popupPage(600,800, 'billingONCorrection.jsp?billing_no=<%=obj.getId()%>')"
                           title="Billing Correction">Edit</a>
                    </security:oscarSec>

                    <a href="javascript:void(0)"
                       onclick="popupPage(600,800, 'billingON3rdInv.jsp?billingNo=<%=obj.getId()%>')">Print</a>
                </td>
                <td class="text-center"><%=obj.getLast_name() + ", " + obj.getFirst_name()%></td>
                <td class="text-center"><%=obj.getBilling_date()%></td>
                <td class="text-center"><%=strBillType%></td>
                <td class="text-center"><%=itObj.getService_code()%></td>
                <td class="text-center"><%=itObj.getDx()%></td>
                <td class="text-center">
                    <%if ("PAT".equals(strBillType) || "PAT Settled".equals(strBillType)) { %>
                        <%=balance%>
                    <%} else { %>
                        &nbsp;
                    <%} %>
                </td>
                <td class="text-center"><%=obj.getTotal()%></td>
                <td class="text-center">
                    <% if (obj.getStatus().compareTo("B") == 0 || obj.getStatus().compareTo("S") == 0) { %>
                        &nbsp;
                    <% } else if (CarlosProperties.getInstance().getBooleanProperty("warnOnDeleteBill", "true")) { %>
                        <a href="#"
                           onclick="onUnbilled('<%=obj.getId()%>','<%=obj.getStatus()%>');return false;">Unbill</a>
                    <% } else { %>
                        <a href="#" onclick="onUnbilled('<%=obj.getId()%>','<%=obj.getStatus()%>');return false;">Unbill</a>
                    <% } %>
                </td>
            </tr>
        <%
            }
        %>
        </tbody>
    </table>

    <div class="d-flex justify-content-between mt-3 mb-3">
        <button class="btn btn-secondary" onclick="javascript:history.go(-1);return false;">Back</button>
        <button class="btn btn-primary" onclick="self.close();">Close</button>
    </div>
</div>

</body>
</html>
