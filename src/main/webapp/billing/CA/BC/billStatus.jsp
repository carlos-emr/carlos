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
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting,_admin" rights="r"
                   reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report&type=_admin.reporting&type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="java.math.*,java.util.*,ca.openosp.openo.billing.ca.bc.MSP.*,ca.openosp.openo.util.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.dao.ReportProviderDao" %>
<%@page import="ca.openosp.openo.commn.model.ReportProvider" %>
<%@page import="ca.openosp.openo.commn.model.Provider" %>


<%
    ReportProviderDao reportProviderDao = SpringUtils.getBean(ReportProviderDao.class);
%>

<%
    java.text.NumberFormat nf = java.text.NumberFormat.getCurrencyInstance();
    String user_no;
    user_no = (String) session.getAttribute("user");
    int nItems = 0;
    String strLimit1 = "0";
    String strLimit2 = "5";
    if (request.getParameter("limit1") != null) strLimit1 = request.getParameter("limit1");
    if (request.getParameter("limit2") != null) strLimit2 = request.getParameter("limit2");
    String providerview = request.getParameter("providerview") == null ? "ALL" : request.getParameter("providerview");
    BigDecimal total = new BigDecimal(0).setScale(2, BigDecimal.ROUND_HALF_UP);
    BigDecimal paidTotal = new BigDecimal(0).setScale(2, BigDecimal.ROUND_HALF_UP);
    BigDecimal owedTotal = new BigDecimal(0).setScale(2, BigDecimal.ROUND_HALF_UP);
    MSPReconcile msp = new MSPReconcile();

    GregorianCalendar now = new GregorianCalendar();
    int curYear = now.get(Calendar.YEAR);
    int curMonth = (now.get(Calendar.MONTH) + 1);
    int curDay = now.get(Calendar.DAY_OF_MONTH);

    int flag = 0, rowCount = 0;
    //String reportAction=request.getParameter("reportAction")==null?"":request.getParameter("reportAction");
    String xml_vdate = request.getParameter("xml_vdate") == null ? "" : request.getParameter("xml_vdate");
    String xml_appointment_date = request.getParameter("xml_appointment_date") == null ? "" : request.getParameter("xml_appointment_date");
    String xml_demoNo = request.getParameter("demographicNo") == null ? "" : request.getParameter("demographicNo");

    boolean defaultShow = request.getParameter("submitted") == null ? true : false;

    boolean showMSP = request.getParameter("showMSP") == null ? defaultShow : !defaultShow;  //request.getParameter("showMSP");
    boolean showWCB = request.getParameter("showWCB") == null ? defaultShow : !defaultShow;  //request.getParameter("showWCB");
    boolean showPRIV = request.getParameter("showPRIV") == null ? defaultShow : !defaultShow;  //request.getParameter("showPRIV");
    boolean showICBC = request.getParameter("showICBC") == null ? defaultShow : !defaultShow;  //request.getParameter("showPRIV");

    String readonly = request.getParameter("filterPatient");
    String firstName = request.getParameter("firstName");
    String lastName = request.getParameter("lastName");
    String demographicNo = request.getParameter("demographicNo") != null ? request.getParameter("demographicNo") : "";

    boolean adminAccess = false;
%>


<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.billing" rights="r" reverse="<%=false%>">
    <% adminAccess = true; %>
</security:oscarSec>


<%@page import="ca.openosp.openo.utility.MiscUtils" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.MSP.MSPReconcile" %>
<%@ page import="ca.openosp.openo.util.DateUtils" %>
<!DOCTYPE HTML>
<html>
<head>
    <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.editInvoices"/></title>
    <script src="<%=request.getContextPath()%>/csrfguard" type="text/javascript"></script>
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js" type="text/javascript"></script>
    <script src="${pageContext.request.contextPath}/library/bootstrap/3.0.0/js/bootstrap.min.js"
            type="text/javascript"></script>

    <script type="text/javascript" src="<%=request.getContextPath() %>/js/bootstrap-datepicker.js"></script>
    <link href="${pageContext.request.contextPath}/library/bootstrap/3.0.0/css/bootstrap.css" rel="stylesheet"
          type="text/css"/>
    <link href="<%=request.getContextPath() %>/css/datepicker.css" rel="stylesheet" type="text/css">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/font-awesome.min.css">

    <script>
        function checkChecked() {
            if ($('input[name^="billCheck"]:checked').length > 0) {
                $('#resubmitButton').attr("disabled", false);
                $('#settleButton').attr("disabled", false);
            } else {
                $('#resubmitButton').attr("disabled", true);
                $('#settleButton').attr("disabled", true);
            }
        }

        $(document).ready(function () {
            $(document).on("click", "input[name^='billCheck']", function () {
                checkChecked()
            });
            $(document).on("change", "#checkAll", function () {
                checkChecked()
            });
            checkChecked();
        })

        function popupPage(vheight, vwidth, varpage) { //open a new popup window
            var page = "" + varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
            var popup = window.open(page, "attachment", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
                popup.focus();
            }
        }

        function popupPage2(vheight, vwidth, varpage, pagename) { //open a new popup window
            var page = "" + varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
            var popup = window.open(page, pagename, windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
                popup.focus();
            }
        }

        function selectprovider(s) {
            if (self.location.href.lastIndexOf("&providerview=") > 0) a = self.location.href.substring(0, self.location.href.lastIndexOf("&providerview="));
            else a = self.location.href;
            self.location.href = a + "&providerview=" + s.options[s.selectedIndex].value;
        }

        function openBrWindow(theURL, winName, features) { //v2.0
            window.open(theURL, winName, features);
        }

        function setfocus() {
            this.focus();
        }

        function refresh() {
            history.go(0);

        }

        function fillEndDate(d) {
            document.serviceform.xml_appointment_date.value = d;

        }

        function setDemographic(demoNo) {
            document.serviceform.demographicNo.value = demoNo;
        }

        function billTypeOnly(showEle) {
            document.serviceform.showMSP.checked = false;
            document.serviceform.showWCB.checked = false;
            document.serviceform.showPRIV.checked = false;
            document.serviceform.showICBC.checked = false;
            document.serviceform.elements[showEle].checked = true;
        }

        $(document).on('click', '#checkAll', function () {
            $("input[name^='billCheck']").prop('checked', $(this).is(':checked'));
        })

        function setOperation(value) {
            let submitOperation = document.getElementById("submitOperation");
            submitOperation.value = value;
        }
    </script>

    <style>

        @media print {

            .hidden-print {
                display: none !important;
            }


            /*this is so the link locatons don't display*/
            a:link:after, a:visited:after {
                content: "";
            }
        }
    </style>

</head>

<body>
<div class="container">
    <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.editInvoices"/></h3>

    <div class="row well hidden-print">

        <div style="text-align: right;"><a href="javascript: function myFunction() {return false; }"
                                           onClick="popupPage(700,720,'<%= request.getContextPath() %>/oscarReport/manageProvider.jsp?action=billingreport')">
            Manage Provider List</a></div>

        <div style="text-align: right;"><%=DateUtils.sumDate("yyyy-M-d", "0")%>
        </div>


        <%
            if ("true".equals(readonly)) {
        %>
        <div class="row">
            <div class="col-lg-12">
                <i>Results for Demographic</i>
                :
                <%=request.getParameter("lastName")%>      ,
                <%=request.getParameter("firstName")%>      (
                <%=request.getParameter("demographicNo")%>      )
            </div>
        </div>
        <%}%>


        <form name="serviceform" method="get" action="billStatus.jsp" class="form-inline">
            <input type="hidden" name="filterPatient" value="<%=readonly%>"/>
            <input type="hidden" name="lastName" value="<%=request.getParameter("lastName")%>"/>
            <input type="hidden" name="firstName" value="<%=request.getParameter("firstName")%>"/>
            <div class="row">
                <div class="col-lg-12">
                    <div class="form-group">
                        <label class="checkbox-inline">
                            <input type="checkbox" id="showMSP" name="showMSP"
                                   value="show"  <%=showMSP ? "checked" : ""%>/>
                            <a onclick="billTypeOnly('showMSP')">MSP</a></label>
                        <label class="checkbox-inline">
                            <input type="checkbox" name="showWCB" value="show"  <%=showWCB ? "checked" : ""%>/><a
                                onclick="billTypeOnly('showWCB')">WCB</a>
                        </label>
                        <label class="checkbox-inline">
                            <input type="checkbox" name="showPRIV" value="show" <%=showPRIV ? "checked" : ""%>/><a
                                onClick="billTypeOnly('showPRIV')">Private</a>
                        </label>
                        <label class="checkbox-inline">
                            <input type="checkbox" name="showICBC" value="show" <%=showICBC ? "checked" : ""%>/><a
                                onClick="billTypeOnly('showICBC')">ICBC</a>
                        </label>
                    </div>
                </div>
            </div>
            <div class="row">
                <div class="col-sm-3">
                    <div class="form-group">
                        <label for="providerview">Select provider</label>
                        <select name="providerview" id="providerview" class="form-control">
                            <option value="ALL">All Providers</option>
                            <% String proFirst = "";
                                String proLast = "";
                                String proOHIP = "";
                                String specialty_code;
                                String billinggroup_no;
                                int Count = 0;
                                for (Object[] result : reportProviderDao.search_reportprovider("billingreport")) {
                                    ReportProvider rp = (ReportProvider) result[0];
                                    Provider p = (Provider) result[1];
                                    proFirst = p.getFirstName();
                                    proLast = p.getLastName();
                                    proOHIP = p.getProviderNo();
                            %>
                            <option value="<%=proOHIP%>" <%=providerview.equals(proOHIP) ? "selected" : ""%>><%=proLast%>
                                , <%=proFirst%>
                            </option>
                            <% } %>
                        </select>
                    </div>
                </div>
                <input type="hidden" name="verCode" value="V03"/>
                <div class="col-sm-3">
                    <div class="form-group">
                        <label for="xml_vdate">Service Start Date:</label>
                        <div class="input-group">
                            <input type="text" name="xml_vdate" class="form-control" id="xml_vdate"
                                   value="<%=xml_vdate%>" placeholder="yyyy-mm-dd"
                                   pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                            <span class="input-group-addon"><i class="icon-calendar"></i></span>
                        </div>
                    </div><!--span2-->
                </div>
                <div class="col-sm-3">
                    <div class="form-group">
    <label style="white-space: nowrap;" for="xml_appointment_date">Service End Date:
        <a href="javascript: function myFunction() {return false; }" onClick="fillEndDate('<%=DateUtils.sumDate("yyyy-MM-dd","-30")%>')" >30</a>
        <a href="javascript: function myFunction() {return false; }" onClick="fillEndDate('<%=DateUtils.sumDate("yyyy-MM-dd","-60")%>')" >60</a>
        <a href="javascript: function myFunction() {return false; }" onClick="fillEndDate('<%=DateUtils.sumDate("yyyy-MM-dd","-90")%>')" >90</a>
    </label>
                        <div class="input-group">
                            <input type="text" class="form-control" name="xml_appointment_date" placeholder="yyyy-mm-dd"
                                   id="xml_appointment_date" value="<%=xml_appointment_date%>"
                                   pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                            <span class="input-group-addon"><i class="icon-calendar"></i></span>
                        </div>
                    </div><!--span3-->
                </div>
                <div class="col-sm-3">
                    <div class="form-group">
                        <label for="demographicNo">Demographic:</label>
                        <%
                            String readonlyStr = "true".equals(readonly) ? "readonly" : "";
                        %>
                        <input type="text" class="form-control" id="demographicNo" name="demographicNo" size="6"
                               value="<%=xml_demoNo%>" <%=readonlyStr%> />
                    </div>
                </div>

            </div>
            <div class="row">
                <div class="col-lg-12">
                    <div class="form-group">

                        <% String billTypes = request.getParameter("billTypes");
                            if (billTypes == null) {
                                billTypes = MSPReconcile.REJECTED;
                            }
                            if ("true".equals(readonly)) {
                                billTypes = "%";
                            }

                        %>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes"
                                   value="<%=MSPReconcile.REJECTED%>"     <%=billTypes.equals(MSPReconcile.REJECTED) ? "checked" : ""%>/>
                            Rejected
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes"
                                   value="<%=MSPReconcile.NOTSUBMITTED%>" <%=billTypes.equals(MSPReconcile.NOTSUBMITTED) ? "checked" : ""%>/>
                            Not Submitted
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes"
                                   value="<%=MSPReconcile.SUBMITTED%>"    <%=billTypes.equals(MSPReconcile.SUBMITTED) ? "checked" : ""%>/>
                            Submitted
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes"
                                   value="<%=MSPReconcile.SETTLED%>"      <%=billTypes.equals(MSPReconcile.SETTLED) ? "checked" : ""%>/>
                            Settled
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes"
                                   value="<%=MSPReconcile.DELETED%>"      <%=billTypes.equals(MSPReconcile.DELETED) ? "checked" : ""%>/>
                            Deleted
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes"
                                   value="<%=MSPReconcile.HELD%>"         <%=billTypes.equals(MSPReconcile.HELD) ? "checked" : ""%>/>
                            Held
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes" value="<%=MSPReconcile.DATACENTERCHANGED%>"
                                   title="Data Center Changed" <%=billTypes.equals(MSPReconcile.DATACENTERCHANGED) ? "checked" : ""%>/>
                            DCC
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes" value="<%=MSPReconcile.PAIDWITHEXP%>"
                                   title="Paid with Explanation"     <%=billTypes.equals(MSPReconcile.PAIDWITHEXP) ? "checked" : ""%>/>
                            PwE
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes"
                                   value="<%=MSPReconcile.BADDEBT%>"      <%=billTypes.equals(MSPReconcile.BADDEBT) ? "checked" : ""%>/>
                            Bad Debt
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes"
                                   value="<%=MSPReconcile.REFUSED%>"      <%=billTypes.equals(MSPReconcile.REFUSED) ? "checked" : ""%>/>
                            Refused
                        </label>
                        <label class="radio-inline">
                            <!--<input type="radio" name="billTypes" value="<%=MSPReconcile.WCB%>"          <%=billTypes.equals(MSPReconcile.WCB)?"checked":""%>/> WCB-->
                            <input type="radio" name="billTypes" value="<%=MSPReconcile.CAPITATED%>"
                                   title="Capitated"   <%=billTypes.equals(MSPReconcile.CAPITATED) ? "checked" : ""%>/>
                            Cap
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes" value="<%=MSPReconcile.DONOTBILL%>"
                                   title="Do Not Bill"    <%=billTypes.equals(MSPReconcile.DONOTBILL) ? "checked" : ""%>/>
                            DNBill
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes"
                                   value="<%=MSPReconcile.BILLPATIENT%>"  <%=billTypes.equals(MSPReconcile.BILLPATIENT) ? "checked" : ""%>/>
                            Bill Patient
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes" value="<%=MSPReconcile.PAIDPRIVATE%>"
                                   title="Paid Private"  <%=billTypes.equals(MSPReconcile.PAIDPRIVATE) ? "checked" : ""%>/>
                            Private
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes" value="<%=MSPReconcile.COLLECTION%>"
                                   title="Transfered to Collection"<%=billTypes.equals(MSPReconcile.COLLECTION) ? "checked" : ""%>/>
                            Collection
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes"
                                   value="%"                              <%=billTypes.equals("%") ? "checked" : ""%>/>
                            All
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes"
                                   value="?"                              <%=billTypes.equals("?") ? "checked" : ""%>/>
                            Fixable Receivables
                        </label>
                        <label class="radio-inline">
                            <input type="radio" name="billTypes"
                                   value="$"                              <%=billTypes.equals("$") ? "checked" : ""%>/>
                            Paid Bills
                        </label>
                    </div>
                </div>
            </div>
            <div class="form-group pull-right">
                <input type="hidden" name="submitted" value="yes"/>
                <input class="btn btn-primary" type="submit" name="Submit" value="Create Report">
            </div>

        </form>

    </div><!-- row well-->


    <form name="ReProcessBillingForm" method="get" action="reprocessBill.do">

        <input type="hidden" id="hiddenFilterType" name="hiddenFilterType"
               value="<%=request.getParameter("billTypes")%>">


        <table class="table table-striped table-condensed sortable" id="resultsTable">
            <thead>

            <th class="no-sort"><label for="checkAll" class="checkbox-inline">
                <input type="checkbox" id="checkAll" name="checkAll">Select All</label></th>
            <th title="INVOICE #">INVOICE #</th>
            <th title="LINE #">SEQ #</th>
            <th title="APP. DATE">APP. DATE</th>
            <th title="TYPE">TYPE</th>
            <%
                if (!"true".equals(readonly)) {
            %>
            <th title="PATIENT">PATIENT</th>
            <%}%>
            <th title="PRACT">PRACT.</th>
            <th title="Status">STAT</th>


            <th title="Fee Code">FEE CODE</th>
            <th title="QTY">QTY</th>
            <th title="Amount Billed">AMT</th>
            <th title="Amount Paid">PAID</th>
            <th>OWED</th>
            <th>DX CODE</th>
            <th>MSGS</th>

            </thead>
            <tbody>
            <%

                String dateBegin = request.getParameter("xml_vdate");
                String dateEnd = request.getParameter("xml_appointment_date");
                String demoNo = request.getParameter("demographicNo");

                MSPReconcile.BillSearch bSearch = msp.getBills(billTypes, providerview, dateBegin, dateEnd, demoNo, !showWCB, !showMSP, !showPRIV, !showICBC);

                Properties p2 = bSearch.getCurrentErrorMessages();
                Properties p = msp.currentC12Records();
                boolean bodd = true;
                boolean incorrectVal = false;
                boolean paidinCorrectval = false;
                String currentBillingNo = "";
                for (int i = 0; i < bSearch.list.size(); i++) {

                    incorrectVal = false;
                    paidinCorrectval = false;
                    MSPReconcile.Bill b = (MSPReconcile.Bill) bSearch.list.get(i);

                    bodd = currentBillingNo.equals(b.billing_no) ? !bodd : bodd; //for the color of rows
                    nItems++; //to calculate if it is the end of records
                    String rejected = isRejected(b.billMasterNo, p, b.isWCB());
                    String rejected2 = isRejected(b.billMasterNo, p2, b.isWCB());
                    BigDecimal valueToAdd = new BigDecimal("0.00");
                    try {
                        valueToAdd = new BigDecimal(b.amount).setScale(2, BigDecimal.ROUND_HALF_UP);
                    } catch (Exception badValueException) {
                        MiscUtils.getLogger().error(" Error calculating value for " + b.billMasterNo);
                        incorrectVal = true;
                    }
                    total = total.add(valueToAdd);
                    double pAmount = msp.getAmountPaid(b.billMasterNo, b.billingtype);
                    BigDecimal valueToPaidAdd = new BigDecimal("0.00");
                    try {
                        valueToPaidAdd = new BigDecimal(pAmount).setScale(2, BigDecimal.ROUND_HALF_UP);
                    } catch (Exception badValueException) {
                        MiscUtils.getLogger().error(" Error calculating paid value for " + b.billMasterNo);
                        paidinCorrectval = true;
                    }
                    paidTotal = paidTotal.add(valueToPaidAdd);

            %>

            <tr>
                <td>
                    <label>
                        <input type="checkbox" id="billCheck_<%=b.getBilling_no()%>" name="billCheck"
                               value="<%=b.getBilling_no() + "_" + b.getBillMasterNo()%>">
                    </label>
                </td>
                <td>
                    <%if ("Pri".equals(b.billingtype)) {%>
                    <a href="javascript:popupPage(800,800, '<%=request.getContextPath()%>/billing/CA/BC/billingView.do?billing_no=<%=b.billing_no%>&receipt=yes')"><%=b.billing_no%>
                    </a>
                    <%
                    } else {
                    %>
                    <%=b.billing_no%>
                    <%}%></td>


                <td><a href="javascript: function myFunction() {return false; }"
                       onClick="popupPage2(500,1020,'genTAS00ByOfficeNo.jsp?officeNo=<%=b.billMasterNo%>','RecValues');"><%=b.seqNum%>
                </a></td>
                <td><%=b.apptDate%>
                </td>
                <td><%=b.billingtype%>
                </td>
                <%
                    if (!"true".equals(readonly)) {
                %>
                <td><a href="javascript: setDemographic('<%=b.demoNo%>');"><%=b.demoName%>
                </a></td>
                <%}%>
                <td><%=b.providerLastName%>,<%=b.providerFirstName%>
                </td>
                <td title="<%=msp.getStatusDesc(b.reason)%>"><%=msp.getStatusDesc(b.reason) == null ? "&nbsp" : msp.getStatusDesc(b.reason)%>
                </td>


                <td><%=b.code%>
                </td>
                <td <%=isBadVal(incorrectVal)%> ><%=b.quantity%>
                </td>
                <td <%=isBadVal(incorrectVal)%> ><%=nf.format(Double.parseDouble(b.amount))%>
                </td>
                <td><%=nf.format(pAmount)%>
                </td>
                <%
                    double dblAmtOwed = msp.getAmountOwing(b.billMasterNo, b.amount, b.billingtype);
                    BigDecimal amtOwed = new BigDecimal(dblAmtOwed).setScale(2, BigDecimal.ROUND_HALF_UP);
                    owedTotal = owedTotal.add(amtOwed);

                %>
                <td><%=nf.format(amtOwed)%>
                </td>
                <td><%=s(b.dx1)%>
                </td>

                <td>
                    <% if (adminAccess) { %>
                    <a href="javascript: popupPage(700,1000,'adjustBill.jsp?billingmaster_no=<%=b.billMasterNo%>&invoiceNo=<%=b.billing_no%>')">Edit </a>
                    <% } %>


                    <%=rejected%><%=rejected2%>
                </td>
            </tr>

            <% //}
                rowCount = rowCount + 1;
                currentBillingNo = b.billing_no;
                bodd = !bodd;
            }
                if (rowCount == 0) {
            %>
            <tr>
                <td style="background-color:white;"> No bills</td>
            </tr>
            <% }%>
            </tbody>
            <tfoot>
            <tr class="sortbottom">
                <td></td>
                <td></td>
                <td></td>
                <td></td>
                <td></td>
                <c:if test="${param.filterPatient ne 'true'}">
                    <td></td>
                </c:if>
                <td>Count:</td>
                <td><%=bSearch.list.size()%>
                </td>
                <td></td>
                <td>Total:</td>
                <td><%=nf.format(total.doubleValue())%>
                </td>
                <td><%=nf.format(paidTotal.doubleValue())%>
                </td>
                <td><%=nf.format(owedTotal.doubleValue())%>
                </td>
                <td></td>
                <td></td>

            </tr>
            </tfoot>
        </table>


        <input type="hidden" id="submitOperation" name="submitOperation" value="">
        <div class="row">
            <button id="resubmitButton" type="submit" class="btn btn-primary"
                    onclick="setOperation('Reprocess and Resubmit Bill')">Reprocess And Resubmit
            </button>
            <button id="settleButton" type="submit" class="btn btn-primary" onclick="setOperation('Settle Bill')">
                Settle
            </button>
            <input class="btn hidden-print" type='button' name='print' value='Print' onClick='window.print()'>
        </div>
    </form>

    <script type="text/javascript">

        var startDate = $("#xml_vdate").datepicker({
            format: "yyyy-mm-dd"
        });

        var endDate = $("#xml_appointment_date").datepicker({
            format: "yyyy-mm-dd"
        });
    </script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/commons/scripts/sort_table/css.js"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/commons/scripts/sort_table/common.js"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/commons/scripts/sort_table/standardista-table-sorting.js"></script>
</div><!--container-->
</body>
</html>
<%!
    String getReasonEx(String reason) {
        if (reason.equals("N")) reason = "Do Not Bill ";
        if (reason.equals("O")) reason = "Bill MSP ";
        if (reason.equals("W")) reason = "Bill WCB ";
        if (reason.equals("H")) reason = "Capitated Bill ";
        if (reason.equals("P")) reason = "Bill Patient";
        return reason;
    }

    String isRejected(String billingNo, Properties p, boolean wcb) {
        String s = "";
        if (p.containsKey(billingNo)) {
            s = "<a href=\"javascript: popupPage(700,1000,'adjustBill.jsp?billingmaster_no=" + billingNo + "')\" > " + p.getProperty(billingNo) + "</a>";
        }
        return s;
    }

    String moneyFormat(String str) {
        String moneyStr = "0.00";
        try {
            moneyStr = new java.math.BigDecimal(str).movePointLeft(2).toString();
        } catch (Exception moneyException) {
        }
        return moneyStr;
    }

    String s(String str) {
        if (str == null || str.length() == 0) {
            str = "";
        }
        return str;
    }

    String isBadVal(boolean valBad) {
        String retval = "";
        if (valBad) {
            retval = "style=\"background-color: red\" title=\"Unprocessable Value: value will not be included in total\"";
        }
        return retval;
    }
%>
