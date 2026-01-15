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

--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@page import="ca.openosp.openo.utility.SpringUtils,ca.openosp.openo.utility.LocaleUtils,ca.openosp.openo.utility.MiscUtils, ca.openosp.openo.util.DateUtils" %>
<%@page import="ca.openosp.openo.commn.model.Demographic, ca.openosp.openo.commn.model.BillingONItem, ca.openosp.openo.commn.model.BillingOnItemPayment, ca.openosp.openo.commn.model.RaDetail" %>
<%@page import="java.util.Locale, java.math.BigDecimal, java.util.Calendar,java.util.List,java.util.ArrayList, java.util.HashMap, java.util.Map, java.util.Date" %>
<%@page import="java.text.ParseException" %>
<%@page import="ca.openosp.openo.commn.model.BillingONPayment,ca.openosp.openo.commn.dao.BillingONPaymentDao" %>
<%@page import="ca.openosp.openo.commn.model.BillingONPayment,ca.openosp.openo.commn.dao.BillingOnItemPaymentDao" %>
<%@page import="ca.openosp.openo.commn.model.BillingONCHeader1,ca.openosp.openo.commn.dao.BillingONCHeader1Dao" %>
<%@page import="ca.openosp.openo.commn.model.BillingONExt,ca.openosp.openo.commn.dao.BillingONExtDao" %>
<%@page import="ca.openosp.openo.commn.model.Demographic,ca.openosp.openo.commn.dao.DemographicDao" %>
<%@page import="ca.openosp.openo.commn.model.Provider,ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@page import="ca.openosp.openo.commn.model.RaHeader,ca.openosp.openo.commn.dao.RaHeaderDao" %>
<%@page import="ca.openosp.openo.commn.model.RaDetail,ca.openosp.openo.commn.dao.RaDetailDao" %>
<%@page import="ca.openosp.openo.commn.model.BillingONPremium,ca.openosp.openo.commn.dao.BillingONPremiumDao" %>
<%@page import="ca.openosp.openo.commn.model.BillingONItem, ca.openosp.openo.commn.service.BillingONService" %>

<%
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>

<security:oscarSec roleName="<%=roleName$%>" objectName="_tasks" rights="r" reverse="true">
    <%response.sendRedirect(request.getContextPath() + "/noRights.html");%>
</security:oscarSec>


<%
    boolean isTeamBillingOnly = false;
    boolean isThisProviderOnly = false;
%>

<security:oscarSec objectName="_team_billing_only" roleName="<%=roleName$ %>" rights="r" reverse="false">
    <% isTeamBillingOnly = true; %>
</security:oscarSec>

<security:oscarSec roleName="<%=roleName$%>" objectName="_admin.invoices" rights="r" reverse="false">
    <% isThisProviderOnly = true; %>
</security:oscarSec>

<security:oscarSec objectName="_admin,_admin.billing" roleName="<%=roleName$ %>" rights="r" reverse="false">
    <% isThisProviderOnly = false; %>
</security:oscarSec>

<%
    Locale locale = request.getLocale();
    Calendar cal = Calendar.getInstance();
    String today = DateUtils.formatDate(cal, locale);

    String startDateStr = request.getParameter("startDateText");
    if (startDateStr == null || startDateStr.isEmpty()) {
        cal.add(Calendar.MONTH, -1);
        startDateStr = DateUtils.formatDate(cal, locale);
    }

    String errorMsg = "";
    String endDateStr = request.getParameter("endDateText");
    if (endDateStr == null || endDateStr.isEmpty()) {
        endDateStr = today;
    }
    Date startDate = null;
    Date endDate = null;
    try {
        startDate = DateUtils.parseDate(startDateStr, locale);
        endDate = DateUtils.parseDate(endDateStr, locale);
        if (DateUtils.calculateDayDifference(startDate, endDate) < 0) {
            errorMsg = LocaleUtils.getMessage(locale, "oscar.billing.paymentReceived.errorEndDateGreater");
        }
    } catch (java.text.ParseException e) {
        errorMsg = LocaleUtils.getMessage(locale, "oscar.billing.paymentReceived.errorOnDate");
    }


    //Get list of providers           
    String curProviderNo = (String) session.getAttribute("user");
    ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
    Provider provider = providerDao.getProvider(curProviderNo);

    List<Provider> pList = null;

    if (isThisProviderOnly) {
        if (provider.getOhipNo().isEmpty())
            response.sendRedirect(request.getContextPath() + "/noRights.html");

        pList = new ArrayList<Provider>();
        pList.add(provider);
    } else if (isTeamBillingOnly) {
        pList = providerDao.getBillableProvidersOnTeam(provider);
    } else {
        pList = providerDao.getBillableProviders();
    }

    BillingONPremiumDao bPremiumDao = (BillingONPremiumDao) SpringUtils.getBean(BillingONPremiumDao.class);
    RaDetailDao raDetailDao = (RaDetailDao) SpringUtils.getBean(RaDetailDao.class);
    BillingONCHeader1Dao bCh1Dao = (BillingONCHeader1Dao) SpringUtils.getBean(BillingONCHeader1Dao.class);
    BillingONPaymentDao bPaymentDao = (BillingONPaymentDao) SpringUtils.getBean(BillingONPaymentDao.class);
    BillingOnItemPaymentDao bItemPaymentDao = (BillingOnItemPaymentDao) SpringUtils.getBean(BillingOnItemPaymentDao.class);
    BillingONExtDao bExtDao = (BillingONExtDao) SpringUtils.getBean(BillingONExtDao.class);
    DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    BillingONService billingONService = (BillingONService) SpringUtils.getBean(BillingONService.class);

    List<RaDetail> raList = null;
    List<BillingONPremium> premiumList = null;
    List<BillingONCHeader1> ptList = null;

    String providerNo = request.getParameter("providerList");

    if (errorMsg.isEmpty() && providerNo != null) {

        Calendar raCalEndDate = Calendar.getInstance();
        Calendar raCalStartDate = Calendar.getInstance();

        //Only get OHIP numbers from the last month
        raCalStartDate.setTime(endDate);
        raCalEndDate.setTime(endDate);

        int firstDate = raCalStartDate.getActualMinimum(Calendar.DATE);
        int lastDate = raCalEndDate.getActualMaximum(Calendar.DATE);

        raCalStartDate.set(Calendar.DATE, firstDate);
        raCalEndDate.set(Calendar.DATE, lastDate);

        Date raStartDate = raCalStartDate.getTime();
        Date raEndDate = raCalEndDate.getTime();

        if (providerNo.isEmpty()) {
            raList = raDetailDao.getRaDetailByDate(raStartDate, raEndDate, locale);
            ptList = bCh1Dao.get3rdPartyInvoiceByDate(startDate, endDate, locale);
            premiumList = bPremiumDao.getActiveRAPremiumsByPayDate(startDate, endDate, locale);
        } else {
            Provider p = providerDao.getProvider(providerNo);
            raList = raDetailDao.getRaDetailByDate(p, raStartDate, raEndDate, locale);
            ptList = bCh1Dao.get3rdPartyInvoiceByProvider(p, startDate, endDate, locale);
            premiumList = bPremiumDao.getActiveRAPremiumsByProvider(p, startDate, endDate, locale);
        }
    }

%>
<html>
<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.paymentReceived.title"/></title>

    <script type="text/javascript" src="<%=request.getContextPath() %>/js/jquery-1.9.1.min.js"></script>
    <script src="<%=request.getContextPath() %>/js/bootstrap.min.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath() %>/js/bootstrap-datepicker.js"></script>

    <link href="<%=request.getContextPath() %>/css/bootstrap.min.css" rel="stylesheet">
    <link href="<%=request.getContextPath() %>/css/datepicker.css" rel="stylesheet" type="text/css">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/font-awesome.min.css">

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
<h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.paymentReceived"/></h3>


<div class="container-fluid">
    <span class="pull-right"><%=today%></span>

    <div class="row well">
        <%=errorMsg%>

        <form name="billingPaymentForm" method="get" action="billingONPayment.jsp">

            <h4><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.freezePeriod"/></h4>

            <div class="span3">
                Provider:<br>
                <!--<fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.providerName"/>-->
                <select name="providerList">
                    <% if (pList.size() > 1) { %>
                    <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.allproviders"/></option>
                    <% } %>

                    <% for (Provider p : pList) {
                        String selected = "";
                        if (providerNo != null && providerNo.equals(p.getProviderNo())) {
                            selected = "selected";
                        }
                    %>
                    <option <%=selected%> value="<%=p.getProviderNo()%>"><%=p.getLastName()%>, <%=p.getFirstName()%>
                    </option>
                    <% } %>
                </select>
            </div>


            <div class="span2">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.startDate"/><br>
                <div class="input-append">
                    <input type="text" style="width:90px" name="startDateText" id="startDateText"
                           value="<%=DateUtils.formatDate(startDate,locale)%>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                    <span class="add-on"><i class="icon-calendar"></i></span>
                </div>
            </div>

            <div class="span2">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.endDate"/><br>
                <div class="input-append">
                    <input type="text" style="width:90px" name="endDateText" id="endDateText"
                           value="<%=DateUtils.formatDate(endDate,locale)%>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                    <span class="add-on"><i class="icon-calendar"></i></span>
                </div>
            </div>

            <div class="span2">
                <br>
                <input class="btn btn-primary" type="submit"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.generateReport"/>"/>
            </div>

    </div>


    <div class="row">
        <h4><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.raBillingReport"/></h4>
        <table class="table-striped table-condensed table-hover">
            <thead>
            <tr>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.invoiceNumber"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.invoiceStatus"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.serviceDate"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.demographicName"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.dxCode"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.serviceCode"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.serviceCount"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.currentFee"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.claimed"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.paid"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.adjustments"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.payprogram"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.claimNo"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.errorCodes"/></th>
            </tr>
            </thead>

            </tbody>
            <%
                BigDecimal feeTotal = new BigDecimal("0.00");
                BigDecimal claimTotal = new BigDecimal("0.00");
                BigDecimal paidTotal = new BigDecimal("0.00");
                BigDecimal adjTotal = new BigDecimal("0.00");
                int numItems = 0;

                if (raList != null) {

                    String rowColor = "myWhite";
                    int curBillingNo = 0;
                    BillingONItem b = null;
                    for (RaDetail rad : raList) {

                        BillingONCHeader1 bCh1 = bCh1Dao.find(rad.getBillingNo());
                        if (bCh1 == null) {
                            // Check to make sure there is actually a bill in this OSCAR instance
                            // that is associated with this RA detail
                            continue;
                        }

                        if (providerNo != null && !providerNo.isEmpty() && !bCh1.getProviderNo().equals(providerNo)) {
                            // Check to make sure that the providers account associated with the bill matches the
                            // providers record we are searching on since it is not necessarily true:
                            // radetail.providerOhipNo == provider.ohip_no && billing_on_cheader1.provider_no == provider.provider_no
                            continue;
                        }

                        numItems++;
                        int lastBillingNo = curBillingNo;
                        curBillingNo = rad.getBillingNo();

                        String serviceCode = rad.getServiceCode();
                        BillingONItem bLast = b;
                        b = bCh1Dao.findBillingONItemByServiceCode(bCh1, serviceCode);

                        boolean isSameBill = true;

                        String dxCode = "";
                        String claimAmtStr = rad.getAmountClaim();
                        String bItemFee = "D";
                        if (b != null) {
                            dxCode = b.getDx();
                            if (!b.getStatus().equals(BillingONItem.DELETED)) {
                                bItemFee = b.getFee();
                            }

                            if (b.equals(bLast)) {
                                serviceCode = "";
                                dxCode = "";
                                bItemFee = "";
                            }
                        }

                        Integer demoNo = bCh1.getDemographicNo();
                        Demographic d = demographicDao.getDemographicById(demoNo);
                        String demographicName = "";
                        String billStatus = "";
                        String serviceDate = "";
                        if (lastBillingNo != curBillingNo) {

                            isSameBill = false;
                            serviceDate = rad.getServiceDate();
                            demographicName = d.getFormattedName();
                            billStatus = bCh1.getStatus();

                            if (rowColor.equals("myWhite"))
                                rowColor = "myPurple";
                            else
                                rowColor = "myWhite";
                        }

                        BigDecimal feeAmt = new BigDecimal("0.00");
                        if (!bItemFee.isEmpty() && !bItemFee.equals("D")) {
                            feeAmt = new BigDecimal(bItemFee);
                        }

                        BigDecimal claimAmt = new BigDecimal(claimAmtStr);
                        BigDecimal paidAmt = new BigDecimal(rad.getAmountPay().trim());
                        BigDecimal adjAmt = claimAmt.subtract(paidAmt);

                        feeTotal = feeTotal.add(feeAmt);
                        claimTotal = claimTotal.add(claimAmt);
                        paidTotal = paidTotal.add(paidAmt);
                        adjTotal = adjTotal.add(adjAmt);

                        if ((feeAmt.compareTo(paidAmt) != 0) || (billStatus.equals(BillingONCHeader1.DELETED) && (paidAmt.compareTo(new BigDecimal("0.00")) != 0))) {
                            rowColor = "myPink";
                        }
                        String curBillingNoStr = String.valueOf(curBillingNo);
            %>
            <tr class="<%=rowColor%>">
                <% if (!isSameBill) {%>
                <td style="text-align:center"><a href="#"
                                                 onclick="popupPage(700,700,'billingONCorrection.jsp?billing_no=<%=curBillingNoStr%>');return false;"><%=curBillingNoStr%>
                </a></td>
                <%} else {%>
                <td></td>
                <%}%>
                <td style="text-align:center"><%=billStatus%>
                </td>
                <td style="text-align:center"><%=serviceDate%>
                </td>
                <% if (!isSameBill) {%>
                <td style="text-align:center"><a href="#"
                                                 onclick="popupPage(800,740,'<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=<%=demoNo%>&displaymode=edit&dboperation=search_detail');return false;"><%=demographicName%>
                </a></td>
                <%} else {%>
                <td></td>
                <%}%>
                <td style="text-align:center"><%=dxCode%>
                </td>
                <td style="text-align:center"><%=serviceCode%>
                </td>
                <td style="text-align:center"><%=rad.getServiceCount()%>
                </td>
                <td style="text-align:right"><%=bItemFee%>
                </td>
                <td style="text-align:right"><%=claimAmtStr%>
                </td>
                <td style="text-align:right"><%=paidAmt.toPlainString()%>
                </td>
                <td style="text-align:right"><%=adjAmt.toPlainString()%>
                </td>
                <td style="text-align:center"><%=rad.getBillType()%>
                </td>
                <td style="text-align:center"><%=rad.getClaimNo()%>
                </td>
                <td style="text-align:center;font-weight:bold"><%=rad.getErrorCode()%>
                </td>
            </tr>
            <% }
            }
            %>
            <tr>
                <td colspan="2" style="font-weight:bold;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.itemCount"/>:
                </td>
                <td colspan="4"><%=numItems%>
                </td>
                <td style="font-weight:bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.cumulativeTotal"/>:
                </td>
                <td style="text-align:right"><%=feeTotal%>
                </td>
                <td style="text-align:right"><%=claimTotal%>
                </td>
                <td style="text-align:right"><%=paidTotal%>
                </td>
                <td style="text-align:right"><%=adjTotal%>
                </td>
                <td colspan="5"></td>
            </tr>
            </tbody>
        </table>
        <hr>
        <!-- 3rd Party Payments Table -->
        <h4><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.premiumPaymentReport"/></h4>
        <table width="100%" cellspacing="0" class="table-striped table-condensed table-hover">
            <thead>
            <tr>
                <th style="text-align:left"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.providerName"/></th>
                <th style="text-align:left"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.payDate"/></th>
                <th colspan="9" style="text-align:right"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.paid"/></th>
            </tr>
            </thead>
            <tbody>
            <%
                int numPremiumItems = 0;
                String rowColor = "myWhite";
                BigDecimal totalPremiums = new BigDecimal("0.00");

                if (premiumList != null) {
                    for (BillingONPremium bPremium : premiumList) {

                        numPremiumItems++;

                        if (rowColor.equals("myWhite"))
                            rowColor = "myPurple";
                        else
                            rowColor = "myWhite";

                        String amountPaid = "0.00";
                        try {
                            amountPaid = bPremium.getAmountPay();
                        } catch (NumberFormatException e) {
                            MiscUtils.getLogger().warn("Premium Amount Paid not a number", e);
                        }

                        String providerName = "";
                        String premProviderNo = bPremium.getProviderNo();
                        if (premProviderNo != null) {
                            Provider p = providerDao.getProvider(premProviderNo);
                            if (p != null) {
                                providerName = p.getFormattedName();
                            }
                        }

                        Date payDate = bPremium.getPayDate();
                        String payDateStr = DateUtils.formatDate(payDate, request.getLocale());
            %>
            <tr class="<%=rowColor%>">
                <td><%=providerName%>
                </td>
                <td><%=payDateStr%>
                </td>
                <td colspan="9" style="text-align:right"><%=amountPaid%>
                </td>
            </tr>
            <% totalPremiums = totalPremiums.add(new BigDecimal(amountPaid));
            }
            }%>
            <tr>
                <td colspan="2" style="font-weight:bold;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.itemCount"/>:
                </td>
                <td colspan="3"><%=numPremiumItems%>
                </td>
                <td style="font-weight:bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.cumulativeTotal"/>:
                </td>
                <td style="text-align:right;font-weight:bold"><%=totalPremiums.toPlainString()%>
                </td>
                <td colspan="4"></td>
            </tr>
            </tbody>
        </table>
        <hr>
        <!-- 3rd Party Payments Table -->
        <h4><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.3rdPartyBillingReport"/></h4>
        <table class="table-striped table-condensed table-hover">
            <thead>
            <tr>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.invoiceNumber"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.serviceDate"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.demographicName"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.dxCode"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.serviceCode"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.serviceCount"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.billed"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.paid"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.refund"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.paymentDate"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.balanceOutstanding"/></th>
            </tr>
            </thead>
            <tbody>
            <%
                BigDecimal total3rdPaid = new BigDecimal("0.00");
                BigDecimal total3rdRefunded = new BigDecimal("0.00");
                BigDecimal total3rdBilled = new BigDecimal("0.00");
                final BigDecimal zeroAmt = new BigDecimal("0.00");

                int num3rdItems = 0;
                if (ptList != null) {

                    rowColor = "myWhite";

                    for (BillingONCHeader1 bCh1 : ptList) {

                        List<BillingONPayment> bPayList = bPaymentDao.find3rdPartyPayRecordsByBill(bCh1, startDate, endDate);
                        BigDecimal totalPaid = BillingONPaymentDao.calculatePaymentTotal(bPayList);
                        BigDecimal totalRefunded = BillingONPaymentDao.calculateRefundTotal(bPayList);

                        //make sure there were actually payments made in the date range specified on the bill in question
                        if ((totalPaid.compareTo(zeroAmt) != 0) || (totalRefunded.compareTo(zeroAmt) != 0)) {
                            num3rdItems++;

                            if (rowColor.equals("myWhite"))
                                rowColor = "myPurple";
                            else
                                rowColor = "myWhite";
            %>
            <tr class="<%=rowColor%>">
                <%
                    String billingDateStr = "";
                    String demographicName = "";

                    billingDateStr = DateUtils.formatDate(bCh1.getBillingDate(), locale);

                    Integer demoNo = bCh1.getDemographicNo();
                    Demographic d = demographicDao.getDemographicById(demoNo);
                    demographicName = d.getFormattedName();
                    String billingNo = String.valueOf(bCh1.getId());
                    if (!isThisProviderOnly) { %>
                <td style="text-align:center"><a href="#"
                                                 onclick="popupPage(700,700,'billingONCorrection.jsp?billing_no=<%=billingNo%>');return false;"><%=bCh1.getId()%>
                </a></td>
                <% } else { %>
                <td style="text-align:center"><%=billingNo%>
                </td>
                <% } %>
                <td style="text-align:center"><%=billingDateStr%>
                </td>
                <td style="text-align:center"><a href="#"
                                                 onclick="popupPage(800,740,'<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=<%=demoNo%>&displaymode=edit&dboperation=search_detail');return false;"><%=demographicName%>
                </a></td>
                <%
                    String dxCode = "";
                    String serviceCode = "";
                    String serviceCount = "";
                    String amtBilled = "";

                    List<BillingONItem> bItems = billingONService.getNonDeletedInvoices(bCh1.getId());

                    BigDecimal totalBilled = new BigDecimal("0.00");

                    int numBillItems = 0;
                    for (BillingONItem bItem : bItems) {
                        dxCode = bItem.getDx();
                        serviceCode = bItem.getServiceCode();
                        serviceCount = bItem.getServiceCount();
                        amtBilled = bItem.getFee();
                        try {
                            totalBilled = totalBilled.add(new BigDecimal(amtBilled));
                        } catch (NumberFormatException e) {
                            MiscUtils.getLogger().warn("BillItem fee is not a valid amount:" + amtBilled);
                        }
                        numBillItems++;

                        List<BillingOnItemPayment> bItemPayList = bItemPaymentDao.getItemPaymentByInvoiceNoItemId(bCh1.getId(), bItem.getId());
                        BigDecimal amtPaid = BillingOnItemPaymentDao.calculateItemPaymentTotal(bItemPayList);
                        BigDecimal amtRefund = BillingOnItemPaymentDao.calculateItemRefundTotal(bItemPayList);
                        if (numBillItems > 1) {
                %>
            </tr>
            <tr class="<%=rowColor%>">
                <td colspan="3"></td>
                <% } %>
                <td style="text-align:center"><%=dxCode%>
                </td>
                <td style="text-align:center"><%=serviceCode%>
                </td>
                <td style="text-align:center"><%=serviceCount%>
                </td>
                <td style="text-align:right"><%=amtBilled%>
                </td>
                <td style="text-align:right"><%=amtPaid.toPlainString()%>
                </td>
                <td style="text-align:right"><%=amtRefund.toPlainString()%>
                </td>
                <td colspan="2"></td>


                <% } %>
            </tr>
            <tr class="<%=rowColor%>">
                <td colspan="6"></td>
                <td style="font-weight:bold;text-align:right"><%=totalBilled.toPlainString()%>
                </td>


                <%
                    total3rdBilled = total3rdBilled.add(totalBilled);

                    int numPayments = 0;
                    for (BillingONPayment bPay : bPayList) {
                        BigDecimal payAmt = bPay.getTotal_payment();
                        BigDecimal refundAmt = bPay.getTotal_refund();
                        if ((payAmt.compareTo(zeroAmt) != 0) || (refundAmt.compareTo(zeroAmt) != 0)) {
                            numPayments++;
                            String payDate = DateUtils.formatDate(bPay.getPaymentDate(), locale);

                            String colSpan = "1";
                            if (numPayments > 1) {
                                colSpan = "8";
                %>
            </tr>
            <tr class="<%=rowColor%>">
                <%

                    }

                %>
                <td colspan="<%=colSpan%>" style="text-align:right"><%=payAmt.toPlainString()%>
                </td>
                <td style="text-align:right"><%=refundAmt.toPlainString()%>
                </td>
                <td style="text-align:center"><%=payDate%>
                </td>
                <td style="text-align:center"></td>
            </tr>

            <%
                        total3rdPaid = total3rdPaid.add(payAmt);
                        total3rdRefunded = total3rdRefunded.add(refundAmt);
                    }
                }
                String outstandingAmt = "";
                String fontWeight = "";
                if (!bCh1.getPayProgram().equals("HCP")
                        && !bCh1.getPayProgram().equals("WCB")
                        && !bCh1.getPayProgram().equals("RMB")
                        && !bCh1.getStatus().equals(BillingONCHeader1.DELETED)) {

                    BigDecimal outstandingBalance = totalBilled.subtract(totalPaid).add(totalRefunded);
                    outstandingAmt = outstandingBalance.toPlainString();

                    if (outstandingBalance.compareTo(zeroAmt) != 0) {
                        fontWeight = "font-weight:bold;";
                    }

            %>


            <tr class="<%=rowColor%>">
                <td colspan="11" style="text-align:right;<%=fontWeight%>"><%=outstandingAmt%>
                </td>
            </tr>
            <% } %>

            <%
                        }
                    }
                }
            %>
            <tr>
                <td colspan="2" style="font-weight:bold;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.itemCount"/>:
                </td>
                <td colspan="3"><%=num3rdItems%>
                </td>
                <td style="font-weight:bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.cumulativeTotal"/>:
                </td>
                <td style="text-align:right"><%=total3rdBilled%>
                </td>
                <td style="text-align:right"><%=total3rdPaid%>
                </td>
                <td style="text-align:right"><%=total3rdRefunded%>
                </td>
                <td colspan="2"></td>
            </tr>
            </tbody>
        </table>
        <br>
        <%
            BigDecimal finalAmt = paidTotal.add(total3rdPaid).subtract(total3rdRefunded).add(totalPremiums);
        %>
        <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.billing.on.paymentReceived.totalPaid"/>: <%=finalAmt%>
        </h3>

        </form>

    </div><!--row-->
</div><!--container-->

<script type="text/javascript">
    var startDate = $("#startDateText").datepicker({format: "yyyy-mm-dd"});
    var endDate = $("#endDateText").datepicker({format: "yyyy-mm-dd"});
</script>

</body>
</html>
