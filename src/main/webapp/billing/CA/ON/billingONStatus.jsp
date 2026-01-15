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

--%>
<!DOCTYPE html>
<%@page import="java.math.*" %>
<%@page import="java.text.DecimalFormat" %>
<%@page import="java.text.NumberFormat" %>
<%@page import="java.util.*" %>
<%@page import="ca.openosp.openo.commn.dao.SiteDao" %>
<%@page import="ca.openosp.openo.commn.model.Site" %>
<%@page import="ca.openosp.openo.commn.model.Provider" %>
<%@page import="ca.openosp.openo.utility.MiscUtils" %>
<%@page import="org.owasp.encoder.Encode" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="ca.openosp.OscarProperties" %>
<%@page import="ca.openosp.openo.billing.ca.on.data.*" %>
<%@page import="ca.openosp.openo.billing.ca.on.pageUtil.*" %>
<%@page import="ca.openosp.openo.util.*" %>
<%@ page import="ca.openosp.openo.billings.ca.on.data.*" %>
<%@ page import="ca.openosp.openo.billings.ca.on.pageUtil.BillingStatusPrep" %>
<%@ page import="ca.openosp.openo.util.LabelValueBean" %>
<%@ page import="ca.openosp.openo.util.DateUtils" %>
<%@ page import="ca.openosp.openo.commn.IsPropertiesOn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%--
    The taglib directive below imports the JSTL library. If you uncomment it,
    you must also add the JSTL library to the project. The Add Library... action
    on Libraries node in Projects view can be used to add the JSTL 1.1 library.
    --%>
<%--
    <%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
    --%>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean isTeamBillingOnly = false;
    boolean isSiteAccessPrivacy = false;
    OscarProperties props = OscarProperties.getInstance();
    boolean hideName = Boolean.valueOf(props.getProperty("invoice_reports.print.hide_name", "false"));
%>
<security:oscarSec objectName="_team_billing_only" roleName="<%=roleName$ %>" rights="r" reverse="false">
    <% isTeamBillingOnly = true; %>
</security:oscarSec>
<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false">
    <%isSiteAccessPrivacy = true; %>
</security:oscarSec>
<%! boolean bMultisites = IsPropertiesOn.isMultisitesEnable(); %>
<%
    //multi-site office , save all bgcolor to Hashmap
    HashMap<String, String> siteBgColor = new HashMap<String, String>();
    HashMap<String, String> siteShortName = new HashMap<String, String>();
    int patientCount = 0;
    if (bMultisites) {
        SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);

        List<Site> sites = siteDao.getAllSites();
        for (Site st : sites) {
            siteBgColor.put(st.getName(), st.getBgColor());
            siteShortName.put(st.getName(), st.getShortName());
        }
    }
%>
<%
    //
    response.setHeader("Pragma", "no-cache"); //HTTP 1.0
    response.setHeader("Cache-Control", "no-cache"); //HTTP 1.1
    response.setDateHeader("Expires", 0); //prevents caching at the proxy server
    response.setHeader("Cache-Control", "private"); // HTTP 1.1
    response.setHeader("Cache-Control", "no-store"); // HTTP 1.1
    response.setHeader("Cache-Control", "max-stale=0"); // HTTP 1.1


    boolean bSearch = true;
    String[] billType = request.getParameterValues("billType");
    String[] strBillType = new String[]{""};
    if (billType == null || billType.length == 0) { // no boxes checked
        bSearch = false;
        strBillType = new String[]{"HCP", "WCB", "RMB", "NOT", "PAT", "OCF", "ODS", "CPP", "STD", "IFH"};
    } else {
        // at least on box checked
        strBillType = billType;
    }

    String statusType = request.getParameter("statusType");
    String providerNo = request.getParameter("providerview");
    String startDate = request.getParameter("xml_vdate");
    String endDate = request.getParameter("xml_appointment_date");
    String demoNo = request.getParameter("demographicNo");
    String serviceCode = request.getParameter("serviceCode");
    String raCode = request.getParameter("raCode");
    String claimNo = request.getParameter("claimNo");
    String dx = request.getParameter("dx");
    String visitType = request.getParameter("visitType");
    String filename = request.getParameter("demographicNo");

    String selectedSite = request.getParameter("site");
    String billingForm = request.getParameter("billing_form");

    String visitLocation = request.getParameter("xml_location");

    String sortName = request.getParameter("sortName");
    String sortOrder = request.getParameter("sortOrder");

    String paymentStartDate = request.getParameter("paymentStartDate");
    String paymentEndDate = request.getParameter("paymentEndDate");

    if (statusType == null) {
        statusType = "O";
    }
    if ("_".equals(statusType)) {
        demoNo = "";
    }
    if (startDate == null) {
        startDate = "";
    }
    if (startDate == "") {
        startDate = DateUtils.sumDate("yyyy-MM-dd", "-180");
    }
    if (endDate == null) {
        endDate = "";
    }
    if (endDate == "") {
        endDate = DateUtils.sumDate("yyyy-MM-dd", "0");
    }
    if (demoNo == null) {
        demoNo = "";
        filename = "";
    }
    if (providerNo == null) {
        providerNo = "";
    }
    if (raCode == null) {
        raCode = "";
    }
    if (claimNo == null) {
        claimNo = "";
    }
    if (dx == null) {
        dx = "";
    }
    if (visitType == null) {
        visitType = "-";
    }
    if (serviceCode == null || serviceCode.equals("")) serviceCode = "%";
    if (billingForm == null) {
        billingForm = "-";
    }
    if (visitLocation == null) {
        visitLocation = "";
    }
    if (sortName == null) {
        sortName = "ServiceDate";
    }
    if (sortOrder == null) {
        sortOrder = "asc";
    }
    if (paymentStartDate == null) {
        paymentStartDate = "";
    }
    if (paymentEndDate == null) {
        paymentEndDate = "";
    }

    List<String> pList = isTeamBillingOnly
            ? (new JdbcBillingPageUtil()).getCurTeamProviderStr((String) session.getAttribute("user"))
            : (new JdbcBillingPageUtil()).getCurProviderStr();

    BillingStatusPrep sObj = new BillingStatusPrep();
    List<BillingClaimHeader1Data> bList = null;
    if ((serviceCode == null || billingForm == null) && dx.length() < 2 && visitType.length() < 2) {
        bList = bSearch ? sObj.getBills(strBillType, statusType, providerNo, startDate, endDate, demoNo, visitLocation, paymentStartDate, paymentEndDate) : new ArrayList<BillingClaimHeader1Data>();
        //serviceCode = "-";
        serviceCode = "%";
    } else {
        serviceCode = (serviceCode == null || serviceCode.length() < 2) ? "%" : serviceCode;
        bList = bSearch ? sObj.getBillsWithSorting(strBillType, statusType, providerNo, startDate, endDate, demoNo, serviceCode, dx, visitType, billingForm, visitLocation, sortName, sortOrder, paymentStartDate, paymentEndDate, claimNo) : new ArrayList<BillingClaimHeader1Data>();
        //bList = bSearch ? sObj.getBillsWithSorting(strBillType, statusType,  providerNo, startDate,  endDate,  demoNo, serviceCode, dx, visitType, billingForm, visitLocation,sortName,sortOrder,paymentStartDate, paymentEndDate) : new ArrayList<BillingClaimHeader1Data>();
    }

    RAData raData = new RAData();

    BigDecimal total = new BigDecimal(0).setScale(2, BigDecimal.ROUND_HALF_UP);
    BigDecimal paidTotal = new BigDecimal(0).setScale(2, BigDecimal.ROUND_HALF_UP);
    BigDecimal adjTotal = new BigDecimal(0).setScale(2, BigDecimal.ROUND_HALF_UP);


    NumberFormat formatter = new DecimalFormat("#0.00");


%>
<%
    String ohipNo = "";
    if (request.getParameter("provider_ohipNo") != null)
        ohipNo = request.getParameter("provider_ohipNo");
%>

<html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.invoiceRpts"/>
        </title>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="<%=request.getContextPath() %>/js/bootstrap.min.js"></script>
        <script src="<%=request.getContextPath() %>/js/bootstrap-datepicker.js"></script>
        <script src="<%=request.getContextPath() %>/js/excellentexport.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/DataTables/datatables.min.js"></script><!-- 1.13.4 -->
        <link href="<%=request.getContextPath() %>/css/bootstrap.min.css" rel="stylesheet">
        <link href="<%=request.getContextPath() %>/css/bootstrap-responsive.css" rel="stylesheet">
        <link href="<%=request.getContextPath() %>/css/datepicker.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/DataTables-1.10.12/media/css/jquery.dataTables.min.css"
              rel="stylesheet">
        <script>
            $(document).ready(function () {
                var startDate = $("#xml_vdate").datepicker({format: "yyyy-mm-dd"});
                var endDate = $("#xml_appointment_date").datepicker({format: "yyyy-mm-dd"});

                var paymentStartDate = $("#paymentStartDate").datepicker({format: "yyyy-mm-dd"});
                var paymentEndDate = $("#paymentEndDate").datepicker({format: "yyyy-mm-dd"});
            });
        </script>
        <script>
            function nav_colour_swap(navid, num) {
                for (var i = 0; i < num; i++) {
                    var nav = document.getElementById("A" + i);
                    if (navid == nav.id) { //selected td
                        nav.style.color = "red";
                    } else { //other td
                        nav.style.color = "#645FCD";
                    }
                }
            }

            function submitForm(methodName) {
                // The sendListEmail() method in BillingInvoice2Action.java is not supported. For more details, please refer to the sendListEmail() method.
                // if (methodName=="email"){
                //     document.invoiceForm.method.value="sendListEmail";
                // } else

                if (methodName == "print") {
                    document.invoiceForm.method.value = "getListPrintPDF";
                }
                document.invoiceForm.submit();
            }

            function fillEndDate(d) {
                document.serviceform.xml_appointment_date.value = d;
            }

            function setDemographic(demoNo) {
                //alert(demoNo);
                document.serviceform.demographicNo.value = demoNo;
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

            function check(stat) {
                for (var x = 0; x < 10; x++) {
                    document.serviceform.billType[x].checked = stat;
                }
            }

            function changeStatus() {
                //alert(document.serviceform.billTypeAll.checked);
                if (document.serviceform.billTypeAll.checked) {
                    check(true);
                } else {
                    check(false);
                }
            }

            function changeProvider(shouldSubmit) {

                var index = document.serviceform.providerview.selectedIndex;
                var provider_no = document.serviceform.providerview[index].value;

                <% for (int i = 0 ; i < pList.size(); i++) {
                String temp[] = ( pList.get(i)).split("\\|");
                %>

                var temp_provider_no = <%=temp[0]%>;
                if (provider_no == temp_provider_no) {
                    var provider_ohipNo = "<%=temp[3]%>";
                    document.serviceform.provider_ohipNo.value = provider_ohipNo;
                    if (shouldSubmit) {
                        if (document.getElementById("xml_vdate").value.length > 0 && document.getElementById("xml_appointment_date").value.length > 0)
                            document.serviceform.submit();
                    } else return;
                }
                <%} %>
                document.serviceform.provider_ohipNo.value = "";
                if (shouldSubmit) document.serviceform.submit();
            }
        </script>
        <script>
            var xmlHttp;

            function createXMLHttpRequest() {
                if (window.ActiveXObject) {
                    xmlHttp = new ActiveXObject("Microsoft.XMLHTTP");
                } else if (window.XMLHttpRequest) {
                    xmlHttp = new XMLHttpRequest();
                }
            }

            var ajaxFieldId;

            function startRequest(idNum) {
                ajaxFieldId = idNum;
                createXMLHttpRequest();
                xmlHttp.onreadystatechange = handleStateChange;
                var val = 'N';
                if (document.getElementById('status' + idNum).checked) {
                    //alert(('status'+idNum) + document.getElementById('status'+idNum).checked);
                    val = 'Y';
                }
                xmlHttp.open("GET", "billingONStatusERUpdateStatus.jsp?id=" + idNum + "&val=" + val, true);
                xmlHttp.send(null);
            }

            function handleStateChange() {
                if (xmlHttp.readyState == 4) {
                    //alert(xmlHttp.status + "0 :go 2" + xmlHttp.responseText);
                    //document.getElementById(ajaxFieldId).innerHTML = xmlHttp.responseText;
                    if (xmlHttp.status == 200) {
                        //alert("go 3" + xmlHttp.responseText);
                        document.getElementById(ajaxFieldId).innerHTML = xmlHttp.responseText;
                    }
                }
            }

            var isChecked = false;

            function checkAll(group) {
                for (i = 0; i < group.length; i++)
                    group[i].checked = !isChecked;
                isChecked = !isChecked;
            }


            updateSort = function (name) {
                var sortName = $("#sortName").val();
                var sortOrder = $("#sortOrder").val();

                if (sortName != name) {
                    sortName = name;
                    sortOrder = 'asc';
                } else {
                    if (sortOrder == 'asc') {
                        sortOrder = 'desc';
                    } else if (sortOrder == 'desc') {
                        sortOrder = 'asc';
                    } else {
                        //this shouldn't happen..but just in case
                        sortOrder = 'asc';
                    }
                }

                $("#sortName").val(sortName);
                $("#sortOrder").val(sortOrder);

                document.serviceform.submit();
            }

            let isFiltered = false;

            function filterChecked() {
                isFiltered = !isFiltered;
                let billingErrorRows = document.getElementsByName("BillingErrorRow");
                for (let i = 0; i < billingErrorRows.length; i++) {
                    let rowId = billingErrorRows[i].id.split("_")[1];
                    let billingErrorRowStatus = document.getElementById("status" + rowId);
                    if (billingErrorRowStatus.checked) {
                        if (isFiltered) {
                            billingErrorRows[i].style.display = "none";
                        } else {
                            billingErrorRows[i].style.display = "";
                        }
                    }
                }
            }

        </script>
        <style>
            table td, th {
                font-size: 12px;
            }

            @media print {
                .hidden-print {
                    display: none !important;
                }
            }
        </style>
    </head>
    <body>
    <jsp:include page="/images/spinner.jsp" flush="true"/>
    <h3>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.invoiceRpts"/>
    </h3>
    <div class="container-fluid">
        <!--Hiding for now since this does not seem to manage the providers in the select
            <a href="javascript: function myFunction() {return false; }" onClick="popupPage(700,720,'<%= request.getContextPath() %>/oscarReport/manageProvider.jsp?action=billingreport')">Manage Provider List</a>-->
        <form name="serviceform" class="form-inline" method="get" action="billingONStatus.jsp"
              onsubmit="ShowSpin(true);">
            <input type="hidden" id="sortName" name="sortName" value="<%=sortName%>">
            <input type="hidden" id="sortOrder" name="sortOrder" value="<%=sortOrder%>">
            <div class="row well hidden-print">
                <%
                    String tmpStrBillType = Arrays.toString(strBillType);
                %>
                <div class="row">
                    <div class="span12">
                        <label class="checkbox inline"><input type="checkbox" name="billTypeAll" id="ALL" value="ALL"
                                                              checked onclick="changeStatus();">ALL</label>
                        <label class="checkbox inline"><input type="checkbox" name="billType"
                                                              value="HCP" <%=tmpStrBillType.indexOf("HCP")>=0?"checked":""%>>Bill
                            OHIP</label>
                        <label class="checkbox inline"><input type="checkbox" name="billType"
                                                              value="RMB" <%=tmpStrBillType.indexOf("RMB")>=0?"checked":""%>>RMB</label>
                        <label class="checkbox inline"><input type="checkbox" name="billType"
                                                              value="WCB" <%=tmpStrBillType.indexOf("WCB")>=0?"checked":""%>>WCB</label>
                        <label class="checkbox inline"><input type="checkbox" name="billType"
                                                              value="NOT" <%=tmpStrBillType.indexOf("NOT")>=0?"checked":""%>>Not
                            Bill</label>
                        <label class="checkbox inline"><input type="checkbox" name="billType"
                                                              value="PAT" <%=tmpStrBillType.indexOf("PAT")>=0?"checked":""%>>Bill
                            Patient</label>
                        <label class="checkbox inline"><input type="checkbox" name="billType"
                                                              value="OCF" <%=tmpStrBillType.indexOf("OCF")>=0?"checked":""%>>OCF</label>
                        <label class="checkbox inline"><input type="checkbox" name="billType"
                                                              value="ODS" <%=tmpStrBillType.indexOf("ODS")>=0?"checked":""%>>ODSP</label>
                        <label class="checkbox inline"><input type="checkbox" name="billType"
                                                              value="CPP" <%=tmpStrBillType.indexOf("CPP")>=0?"checked":""%>>CPP</label>
                        <label class="checkbox inline"><input type="checkbox" name="billType"
                                                              value="STD" <%=tmpStrBillType.indexOf("STD")>=0?"checked":""%>>STD/LTD</label>
                        <label class="checkbox inline"><input type="checkbox" name="billType"
                                                              value="IFH" <%=tmpStrBillType.indexOf("IFH")>=0?"checked":""%>>IFH</label>
                    </div>
                    <!--</div>-->
                    <!--<div class="row">-->
                    <div class="span10">
                        <% // multisite start ==========================================
                            String curSite = request.getParameter("site");
                            if (bMultisites) {
                                SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
                                List<Site> sites = siteDao.getActiveSitesByProviderNo((String) session.getAttribute("user"));
                                // now get all providers eligible
                                HashSet<String> pros = new HashSet<String>();
                                for (Object s : pList) {
                                    pros.add(((String) s).substring(0, ((String) s).indexOf("|")));
                                }
                        %>
                        <script>
                            var _providers = [];
                            <%	for (int i=0; i<sites.size(); i++) {
                                Set<Provider> siteProviders = sites.get(i).getProviders();
                                List<Provider>  siteProvidersList = new ArrayList<Provider> (siteProviders);
                                Collections.sort(siteProvidersList,(new Provider()).ComparatorName());%>
                            _providers["<%= sites.get(i).getName() %>"] = "<% Iterator<Provider> iter = siteProvidersList.iterator();
                                    while (iter.hasNext()) {
                                    	Provider p=iter.next();
                                    	if (pros.contains(p.getProviderNo())) {
                                    %><option value='<%= p.getProviderNo() %>'><%= Encode.forHtml(p.getLastName()) %>, <%= Encode.forHtml(p.getFirstName()) %></option><% }} %>";
                            <% } %>

                            function changeSite(sel) {
                                sel.form.providerview.innerHTML = sel.value == "none" ? "" : "<option value='none'>---select providers---</option>" + _providers[sel.value];
                                sel.style.backgroundColor = sel.options[sel.selectedIndex].style.backgroundColor;
                                if (sel.value == '<%=request.getParameter("site")%>') {
                                    if (document.serviceform.provider_ohipNo.value != '')
                                        sel.form.providerview.value = '<%=request.getParameter("providerview")%>';
                                }
                                changeProvider(false);
                            }
                        </script>
                        <label>
                            Site:
                            <select id="site" name="site" class="input-large" onchange="changeSite(this)">
                                <option value="none" style="background-color:white">---select clinic---</option>
                                <%
                                    for (int i = 0; i < sites.size(); i++) {
                                %>
                                <option value="<%= Encode.forHtml(sites.get(i).getName()) %>"
                                        style="background-color:<%= sites.get(i).getBgColor() %>"
                                        <%=sites.get(i).getName().toString().equals(curSite) ? "selected" : "" %>><%= Encode.forHtml(sites.get(i).getName()) %>
                                </option>
                                <% } %>
                            </select>
                        </label>
                        <label>Provider:
                            <select id="providerview" class="input-large" name="providerview"
                                    onchange="changeProvider(true);"></select></label>
                        <% if (request.getParameter("providerview") != null) { %>
                        <script>
                            window.onload = function () {
                                changeSite(document.getElementById("site"));
                            }

                        </script>
                        <% } // multisite end ==========================================
                        } else {
                        %>
                        <label>
                            Provider:
                            <select name="providerview" class="input-large" onchange="changeProvider(false);">
                                <%
                                    if (pList.size() == 1) {
                                        String temp[] = (pList.get(0)).split("\\|");
                                %>
                                <option value="<%=temp[0]%>"><%=temp[1]%>, <%=temp[2]%>
                                </option>
                                <%
                                } else {
                                %>
                                <option value="all">All Providers</option>
                                <% for (int i = 0; i < pList.size(); i++) {
                                    String temp[] = (pList.get(i)).split("\\|");
                                %>
                                <option value="<%=temp[0]%>" <%=providerNo.equals(temp[0]) ? "selected" : ""%>><%=temp[1]%>
                                    , <%=temp[2]%>
                                </option>
                                <% }
                                } %>
                            </select>
                        </label>
                        <% } %>
                        <label>
                            OHIP No.:
                            <input type="text" class="input-small" name="provider_ohipNo" readonly value="<%=ohipNo%>"></label>
                    </div>
                    <div class="span6">
                        <label for="xml_vdate">Start:</label>
                        <div class="input-append">
                            <input type="text" name="xml_vdate" id="xml_vdate" style="width:90px" value="<%=startDate%>"
                                   pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off" required>
                            <span class="add-on"><i class="icon-calendar"></i></span>
                        </div>
                        <label for="xml_appointment_date">End:
                            <small>
                                <a href="javascript: function myFunction() {return false; }"
                                   onClick="fillEndDate('<%=DateUtils.sumDate("yyyy-MM-dd","-30")%>')">30</a>
                                <a href="javascript: function myFunction() {return false; }"
                                   onClick="fillEndDate('<%=DateUtils.sumDate("yyyy-MM-dd","-60")%>')">60</a>
                                <a href="javascript: function myFunction() {return false; }"
                                   onClick="fillEndDate('<%=DateUtils.sumDate("yyyy-MM-dd","-90")%>')">90</a>
                                days back
                            </small></label>
                        <div class="input-append">
                            <input type="text" name="xml_appointment_date" style="width:90px" id="xml_appointment_date"
                                   value="<%=endDate%>" pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$"
                                   autocomplete="off" required>
                            <span class="add-on"><i class="icon-calendar"></i></span>
                        </div>
                    </div>
                </div>
                <!-- row -->
                <div class="row">
                    <div class="span12">
                        <label>Dx:
                            <input type="text" name="dx" class="input-mini" placeholder="123" value="<%=dx%>"></label>
                        <label>Serv. Code:
                            <input type="text" name="serviceCode" class="input-mini" placeholder="A123A"
                                   value="<%=serviceCode%>"></label>
                        <label>Demographic:
                            <input type="text" name="demographicNo" class="input-mini" placeholder="1234"
                                   value="<%=demoNo%>"></label>
                        <label>RA Code:
                            <input type="text" name="raCode" class="input-mini" placeholder=""
                                   value="<%=raCode%>"></label>
                        <label>Claim No (% for any):
                            <input type="text" name="claimNo" class="input-small" value="<%=claimNo%>"></label>
                        <label>
                            Visit Type:
                            <select name="visitType" style="background-color:white;">
                                <option value="-" label="-%" <%=visitType.startsWith("-") ? "selected" : ""%>></option>
                                <option value="00" <%=visitType.startsWith("00") ? "selected" : ""%>>Clinic Visit
                                </option>
                                <option value="01" <%=visitType.startsWith("01") ? "selected" : ""%>>Outpatient Visit
                                </option>
                                <option value="02" <%=visitType.startsWith("02") ? "selected" : ""%>>Hospital Visit
                                </option>
                                <option value="03" <%=visitType.startsWith("03") ? "selected" : ""%>>ER</option>
                                <option value="04" <%=visitType.startsWith("04") ? "selected" : ""%>>Nursing Home
                                </option>
                                <option value="05" <%=visitType.startsWith("05") ? "selected" : ""%>>Home Visit</option>
                            </select>
                        </label>
                        <label>
                            Billing Form:
                            <select name="billing_form">
                                <option value="---" selected="selected"> ---</option>
                                <%
                                    List<LabelValueBean> forms = sObj.listBillingForms();
                                    String selected = "";
                                    for (LabelValueBean form : forms) {
                                        if (billingForm != null) {
                                            if (billingForm.equals(form.getValue())) {
                                                selected = "selected";
                                            } else {
                                                selected = "";
                                            }
                                        }
                                %>
                                <option value="<%= form.getValue()%>" <%= selected%> ><%= form.getLabel()%>
                                </option>
                                <%
                                    }
                                %>
                            </select>
                        </label>
                        <label for="xml_location">Visit Location:</label>
                        <select name="xml_location" id="xml_location" class="input-large">
                            <% //
                                JdbcBillingPageUtil tdbObj = new JdbcBillingPageUtil();

                                String billLocationNo = "", billLocation = "";
                                List lLocation = tdbObj.getFacilty_num();
                                for (int i = 0; i < lLocation.size(); i = i + 2) {
                                    billLocationNo = (String) lLocation.get(i);
                                    billLocation = (String) lLocation.get(i + 1);
                                    String locationSelected = visitLocation.equals(billLocationNo) ? " selected=\"selected\" " : "";
                            %>
                            <option value="<%=billLocationNo%>" <%=locationSelected %>>
                                <%=Encode.forHtml(billLocation)%>
                            </option>
                            <% } %>
                        </select>
                        <label for="paymentStartDate">Payment Start:</label>
                        <div class="input-append">
                            <input type="text" name="paymentStartDate" id="paymentStartDate" style="width:90px"
                                   value="<%=paymentStartDate%>" pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$"
                                   autocomplete="off">
                            <span class="add-on"><i class="icon-calendar"></i></span>
                        </div>
                        <label for="paymentEndDate">Payment End:</label>
                        <div class="input-append">
                            <input type="text" name="paymentEndDate" id="paymentEndDate" style="width:90px"
                                   value="<%=paymentEndDate%>" pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$"
                                   autocomplete="off">
                            <span class="add-on"><i class="icon-calendar"></i></span>
                        </div>
                        <!--</div>-->
                        <!--<div class="row" >-->
                        <div class="span12">
                            <label class="radio inline"><input type="radio" name="statusType"
                                                               value="%" <%=statusType.equals("%")?"checked":""%>>All</label>
                            <label class="radio inline"><input type="radio" name="statusType"
                                                               value="_" <%=statusType.equals("_")?"checked":""%>>Rejected</label>
                            <label class="radio inline"><input type="radio" name="statusType"
                                                               value="H" <%=statusType.equals("H")?"checked":""%>>Capitated</label>
                            <label class="radio inline"><input type="radio" name="statusType"
                                                               value="O" <%=statusType.equals("O")?"checked":""%>>Invoiced</label>
                            <label class="radio inline"><input type="radio" name="statusType"
                                                               value="P" <%=statusType.equals("P")?"checked":""%>>Bill
                                Patient</label>
                            <!--li><label class="radio inline"><input type="radio" name="statusType" value="N" <%=statusType.equals("N")?"checked":""%>>Do Not Bill</label>
                                    <label class="radio inline"><input type="radio" name="statusType" value="W" <%=statusType.equals("W")?"checked":""%>>WCB</label>-->
                            <label class="radio inline"><input type="radio" name="statusType"
                                                               value="B" <%=statusType.equals("B")?"checked":""%>>Submmitted
                                OHIP</label>
                            <label class="radio inline"><input type="radio" name="statusType"
                                                               value="S" <%=statusType.equals("S")?"checked":""%>>Settled/Paid</label>
                            <label class="radio inline"><input type="radio" name="statusType"
                                                               value="X" <%=statusType.equals("X")?"checked":""%>>Bad
                                Debt</label>
                            <label class="radio inline"><input type="radio" name="statusType"
                                                               value="D" <%=statusType.equals("D")?"checked":""%>>Deleted
                                Bill</label>
                        </div>
                        <!--</div>-->
                        <!-- row -->
                        <!--<div class="row">-->
                        <div class="span4" style="padding-top:10px;">
                            <input class="btn btn-primary" type="submit" name="Submit" value="Create Report">
                            <button class="btn" type='button' name='print' value='Print' onClick='window.print()'><i
                                    class="icon icon-print"></i> Print
                            </button>
                        </div>
                    </div>
                    <!-- row -->
                </div>
            </div>
            <!-- end well -->
        </form>
        <form name="invoiceForm" action="<%=request.getContextPath()%>/BillingInvoice.do">
            <input type="hidden" name="method" value="">
            <% //
                if (statusType.equals("_")) { %>
            <!--  div class="rejected list"-->
            <table class="table" id="rejectTbl">
                <thead>
                <tr class="warning">
                    <th>Insurance#</th>
                    <th>D.O.B</th>
                    <th>Invoice#</th>
                    <!--th>Type</th-->
                    <th>Ref#</th>
                    <th>Hosp#</th>
                    <th title="admission date">Admitted</th>
                    <th>Claim Error</th>
                    <th>Code</th>
                    <th>Fee</th>
                    <th>Unit</th>
                    <th>Date</th>
                    <th>Dx</th>
                    <th>Exp.</th>
                    <th>Code Error</th>
                    <th>
                        <button class="btn-link hidden-print" type="button" title="Show/Hide Checked"
                                onClick="filterChecked()">Status
                        </button>
                    </th>
                    <th>Filename</th>
                </tr>
                </thead>
                <% //
                    ArrayList<String> aLProviders;
                    if (providerNo == null || providerNo.equals("")) {
                        aLProviders = new ArrayList<String>(pList);
                    } else {
                        aLProviders = new ArrayList<String>();
                        aLProviders.add(providerNo);
                    }
                    String[] provInfo;
                    for (int idx = 0; idx < aLProviders.size(); ++idx) {
                        provInfo = aLProviders.get(idx).split("\\|");
                        providerNo = provInfo[0].trim();

                        List lPat = null;
                        if (providerNo.equals("all")) {
                            List<BillingProviderData> providerObj = (new JdbcBillingPageUtil()).getProviderObjList(providerNo);
                            lPat = (new JdbcBillingErrorRepImpl()).getErrorRecords(providerObj, startDate, endDate, filename);
                        } else {
                            BillingProviderData providerObj = (new JdbcBillingPageUtil()).getProviderObj(providerNo);
                            lPat = (new JdbcBillingErrorRepImpl()).getErrorRecords(providerObj, startDate, endDate, filename);
                        }
                        boolean nC = false;
                        String invoiceNo = "";


                        for (int i = 0; i < lPat.size(); i++) {
                            BillingErrorRepData bObj = (BillingErrorRepData) lPat.get(i);
                            String color = "";
                            if (!invoiceNo.equals(bObj.getBilling_no())) {
                                invoiceNo = bObj.getBilling_no();
                                nC = nC ? false : true;
                            }
                            color = nC ? "class='success'" : "";
                %>
                <tr <%=color %> name="BillingErrorRow" id="BillingErrorRow_<%=bObj.getId() %>">
                    <td><small><%=bObj.getHin() %> <%=bObj.getVer() %>
                    </small></td>
                    <td><font size="-1"><%=bObj.getDob() %>
                    </font></td>
                    <td style="text-align:right"><a href=#
                                                    onclick="popupPage(800,700,'billingONCorrection.jsp?billing_no=<%=bObj.getBilling_no()%>','BillCorrection<%=bObj.getBilling_no()%>');return false;"><%=bObj.getBilling_no() %>
                    </a></td>
                    <td><%=bObj.getRef_no() %>
                    </td>
                    <td><%=bObj.getFacility() %>
                    </td>
                    <td><%=bObj.getAdmitted_date() %>
                    </td>
                    <td><%=bObj.getClaim_error() %>
                    </td>
                    <td><%=bObj.getCode() %>
                    </td>
                    <%
                        String formattedFee = null;
                        try {
                            formattedFee = String.valueOf(Integer.parseInt(bObj.getFee()));

                        } catch (NumberFormatException e) {
                            formattedFee = "N/A";
                        }
                    %>
                    <td style="text-align:right"><%=ch2StdCurrFromNoDot(formattedFee)%>
                    </td>
                    <td style="text-align:right"><%=bObj.getUnit() %>
                    </td>
                    <td><font size="-1"><%=bObj.getCode_date() %>
                    </font></td>
                    <td><%=bObj.getDx() %>
                    </td>
                    <td><%=bObj.getExp() %>
                    </td>
                    <td><%=bObj.getCode_error() %>
                    </td>
                    <td style="text-align:center">
                        <input type="checkbox" id="status<%=bObj.getId() %>" name="status<%=bObj.getId() %>"
                               value="Y" <%="N".equals(bObj.getStatus()) ? "" : "checked" %>
                               onclick="startRequest('<%=bObj.getId() %>');"/>
                    </td>
                    <td id="<%=bObj.getId() %>"><%=bObj.getReport_name() %>
                    </td>
                </tr>
                <% }
                }
                } else { %>
                <!--  div class="tableListing"-->
                <table class="table" id="bListTable" class="display nowrap">
                    <thead>
                    <tr>
                        <th><a href="javascript:void(0);" onClick="updateSort('ServiceDate');return false;">SERVICE
                            DATE</a></th>
                        <th><a href="javascript:void(0);"
                               onClick="updateSort('DemographicNo');return false;">PATIENT</a></th>
                        <th class="<%=hideName?"hidden-print":""%>">PATIENT NAME</th>
                        <th><a href="javascript:void(0);"
                               onClick="updateSort('VisitLocation');return false;">LOCATION</a></th>
                        <th title="Status">STAT</th>
                        <th>SETTLED</th>
                        <th title="Code Billed">CODE</th>
                        <th title="Amount Billed">BILLED</th>
                        <th title="Amount Paid">PAID</th>
                        <th title="Adjustments">ADJ</th>
                        <th>DX</th>
                        <!--th>DX1</th-->
                        <th>TYPE</th>
                        <th>INVOICE #</th>
                        <th>MESSAGES</th>
                        <th>CASH</th>
                        <th>DEBIT</th>
                        <th>Quantity</th>
                        <th>Provider</th>
                        <% if (bMultisites) {%>
                        <th>SITE</th>
                        <% }%>
                        <th class="hidden-print">
                            <a href="#" onClick="checkAll(document.invoiceForm.invoiceAction)">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingStatus.action"/>
                            </a>
                        </th>
                    </tr>
                    </thead>
                    <tbody>
                    <% //
                        String invoiceNo = "";
                        boolean nC = false;
                        boolean newInvoice = true;

                        double totalCash = 0;
                        double totalDebit = 0;

                        for (int i = 0; i < bList.size(); i++) {
                            BillingClaimHeader1Data ch1Obj = bList.get(i);

                            if (bMultisites && ch1Obj.getClinic() != null && curSite != null
                                    && !ch1Obj.getClinic().equals(curSite) && isSiteAccessPrivacy) // only applies on user have siteAccessPrivacy (SiteManager)
                                continue; // multisite: skip if the line doesn't belong to the selected clinic

                            if (bMultisites && selectedSite != null && (!selectedSite.equals(ch1Obj.getClinic())))
                                continue;

                            patientCount++;

                            // ra code error codes eg 33 V07
                            if (raCode.trim().length() == 2 || raCode.trim().length() == 3) {
                                if (!raData.isErrorCode(ch1Obj.getId(), raCode)) {
                                    continue;
                                }
                            }

                            String ohip_no = ch1Obj.getProvider_ohip_no();
                            ArrayList raList = raData.getRADataIntern(ch1Obj.getId(), ch1Obj.getBilling_date().replaceAll("\\D", ""), ohip_no);
                            boolean incorrectVal = false;

                            BigDecimal valueToAdd = new BigDecimal("0.00");
                            try {
                                valueToAdd = new BigDecimal(ch1Obj.getTotal()).setScale(2, BigDecimal.ROUND_HALF_UP);
                            } catch (Exception badValueException) {
                                incorrectVal = true;
                            }
                            total = total.add(valueToAdd);
                            String amountPaid = "0.00";
                            String errorCode = "";
                            if (serviceCode.equals("-") && raList.size() > 0) {
                                amountPaid = raData.getAmountPaid(raList);
                                errorCode = raData.getErrorCodes(raList);
                            } else if (raList.size() > 0) {
                                amountPaid = raData.getAmountPaid(raList, ch1Obj.getId(), ch1Obj.getTransc_id());
                                errorCode = raData.getErrorCodes(raList);
                            }
                            // 3rd party billing
                            if (ch1Obj.getPay_program().matches("PAT|OCF|ODS|CPP|STD|IFH")) {
                                amountPaid = ch1Obj.getPaid();
                            }

                            int qty = ch1Obj.getNumItems();

                            amountPaid = (amountPaid == null || amountPaid.equals("") || amountPaid.equals("null")) ? "0.00" : amountPaid;

                            BigDecimal bTemp;
                            BigDecimal adj;
                            try {
                                bTemp = (new BigDecimal(amountPaid.trim())).setScale(2, BigDecimal.ROUND_HALF_UP);
                                adj = (new BigDecimal(ch1Obj.getTotal())).setScale(2, BigDecimal.ROUND_HALF_UP);
                            } catch (NumberFormatException e) {
                                MiscUtils.getLogger().error("Could not parse amount paid for invoice " + ch1Obj.getId(), e);
                                throw e;
                            }

                            paidTotal = paidTotal.add(bTemp);
                            adj = adj.subtract(bTemp);
                            adjTotal = adjTotal.add(adj);

                            String color = "";

                            if (invoiceNo.equals(ch1Obj.getId())) {
                                newInvoice = false;
                            } else {
                                newInvoice = true;
                            }
                            if (!invoiceNo.equals(ch1Obj.getId())) {
                                invoiceNo = ch1Obj.getId();
                                nC = nC ? false : true;
                            }
                            color = nC ? "class='success'" : "";
                            String settleDate = ch1Obj.getSettle_date();
                            if (settleDate == null || !ch1Obj.getStatus().equals("S")) {
                                settleDate = "N/A";
                            } else {
                                settleDate = settleDate.substring(0, settleDate.indexOf(" "));
                            }

                            String payProgram = ch1Obj.getPay_program();
                            boolean b3rdParty = false;
                            if (payProgram.equals("PAT") || payProgram.equals("OCF") || payProgram.equals("ODS") || payProgram.equals("CPP") || payProgram.equals("STD")) {
                                b3rdParty = true;
                            }

                            String cash = formatter.format(ch1Obj.getCashTotal());
                            String debit = formatter.format(ch1Obj.getDebitTotal());

                            totalCash += ch1Obj.getCashTotal();
                            totalDebit += ch1Obj.getDebitTotal();


                    %>
                    <tr <%=color %>>
                        <td style="text-align:center"><%= ch1Obj.getBilling_date()%>  <%--=ch1Obj.getBilling_time()--%></td>
                        <!--SERVICE DATE-->
                        <td style="text-align:center"><%=ch1Obj.getDemographic_no()%>
                        </td>
                        <!--PATIENT-->
                        <td style="text-align:center" class="<%=hideName?"hidden-print":""%>"><a href=#
                                                                                                 onclick="popupPage(800,740,'<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=<%=ch1Obj.getDemographic_no()%>&displaymode=edit&dboperation=search_detail');return false;"><%= Encode.forHtml(ch1Obj.getDemographic_name())%>
                        </a></td>
                        <td style="text-align:center"><%=ch1Obj.getFacilty_num() != null ? ch1Obj.getFacilty_num() : "" %>
                        </td>
                        <td style="text-align:center"><%=ch1Obj.getStatus()%>
                        </td>
                        <!--STAT-->
                        <td style="text-align:center"><%=settleDate%>
                        </td>
                        <!--SETTLE DATE-->
                        <td style="text-align:center"><%=getHtmlSpace(ch1Obj.getTransc_id())%>
                        </td>
                        <!--CODE-->
                        <td style="text-align:right"><%=getStdCurr(ch1Obj.getTotal())%>
                        </td>
                        <!--BILLED-->
                        <td style="text-align:right"><%=amountPaid%>
                        </td>
                        <!--PAID-->
                        <td style="text-align:center"><%=adj.toString()%>
                        </td>
                        <!--SETTLE DATE-->
                        <td style="text-align:center"><%=getHtmlSpace(ch1Obj.getRec_id())%>
                        </td>
                        <!--DX1-->
                        <!--td>&nbsp;</td--><!--DX2-->
                        <td style="text-align:center"><%=payProgram%>
                        </td>
                        <td style="text-align:center"><a href=#
                                                         onclick="popupPage(800,700,'billingONCorrection.jsp?billing_no=<%=ch1Obj.getId()%>','BillCorrection<%=ch1Obj.getId()%>');nav_colour_swap(this.id, <%=bList.size()%>);return false;"><%=ch1Obj.getId()%>
                        </a></td>
                        <!--ACCOUNT-->
                        <td class="highlightBox"><a id="A<%=i%>" href=#
                                                    onclick="popupPage(800,700,'billingONCorrection.jsp?billing_no=<%=ch1Obj.getId()%>','BillCorrection<%=ch1Obj.getId()%>');nav_colour_swap(this.id, <%=bList.size()%>);return false;">Edit</a> <%=errorCode%>
                        </td>
                        <!--MESSAGES-->
                        <td style="text-align:center">$<%=cash%>
                        </td>
                        <td style="text-align:center">$<%=debit%>
                        </td>
                        <td style="text-align:center"><%=qty %>
                        </td>
                        <td style="text-align:center"><%=ch1Obj.getProviderName() %>
                        </td>
                        <% if (bMultisites) {%>
                        <td <%=(ch1Obj.getClinic() == null || ch1Obj.getClinic().equalsIgnoreCase("null") ? "" : "style='background-color:" + siteBgColor.get(ch1Obj.getClinic()) + ";'")%>>
                            <%=(ch1Obj.getClinic() == null || ch1Obj.getClinic().equalsIgnoreCase("null") ? "" : Encode.forHtml(siteShortName.get(ch1Obj.getClinic())))%>
                        </td>
                        <!--SITE-->
                        <% }%>
                        <td style="text-align:center" class="hidden-print">
                            <% if (newInvoice && b3rdParty) { %>
                            <input type="checkbox" name="invoiceAction" id="invoiceAction<%=invoiceNo%>"
                                   value="<%=invoiceNo%>"/>
                            <% }%>
                        </td>
                        <!--ACTION-->
                    </tr>
                    <% } %>
                    </tbody>
                </table>
                <script>
                    $('#bListTable').DataTable({
                        "bPaginate": false,
                        "order": [],
                        "language": {
                            "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                        }
                    });

                </script>
                <table>
                    <tr class="warning">
                        <td>Count:</td>
                        <td style="text-align:center"><%=patientCount%>
                        </td>
                        <td style="text-align:center" class="<%=hideName?"hidden-print":""%>">&nbsp;</td>
                        <td>&nbsp;</td>
                        <!--LOCATION-->
                        <td>&nbsp;</td>
                        <!--STAT-->
                        <td>&nbsp;</td>
                        <td>Total:</td>
                        <!--CODE-->
                        <td style="text-align:right">$<%=total.toString()%>
                        </td>
                        <!--BILLED-->
                        <td style="text-align:right"> Paid: $<%=paidTotal.toString()%>
                        </td>
                        <!--PAID-->
                        <td style="text-align:right"> Adj: $<%=adjTotal.toString()%>
                        </td>
                        <!--ADJUSTMENTS-->
                        <td>&nbsp;</td>
                        <!--DX-->
                        <td>&nbsp;</td>
                        <!--TYPE-->
                        <td>&nbsp;</td>
                        <!--ACCOUNT-->
                        <td>&nbsp;</td>
                        <!--MESSAGES-->
                        <td style="text-align:center">Cash: $<%=formatter.format(totalCash)%>
                        </td>
                        <td style="text-align:center">Debit: $<%=formatter.format(totalDebit) %>
                        </td>
                        <td style="text-align:center">&nbsp;</td>
                        <td>&nbsp;</td>
                        <!--PROVIDER-->
                        <% if (bMultisites) {%>
                        <td>&nbsp;</td>
                        <!--SITE-->
                        <% }%>
                        <td style="text-align:center" class="hidden-print">
                            <a href="#" onClick="submitForm('print')">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingStatus.print"/>
                            </a>
                                <%-- <a href="#" onClick="submitForm('email')">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingStatus.email"/>
                                </a> --%>
                        </td>
                    </tr>
                </table><!-- inner -->
                <%if (bList != null && !bList.isEmpty()) {%>
                <a download="oscar_invoices.xls" href="#"
                   onclick="return ExcellentExport.excel(this, 'bListTable', 'OSCAR Invoices');">Export to Excel</a>
                <a download="oscar_invoices.csv" href="#" onclick="return ExcellentExport.csv(this, 'bListTable');">Export
                    to CSV</a>
                <%} %>
                <% } %>
            </table> <!-- outer -->
            <script>
                $('#rejectTbl').DataTable({
                    "bPaginate": false,
                    "order": [],
                    "language": {
                        "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                    }
                });

            </script>
        </form>
    </div>
    <!-- end container -->
    </body>
    <%! String getStdCurr(String s) {
        if (s != null) {
            if (s.indexOf(".") >= 0) {
                s += "00".substring(0, 3 - s.length() + s.indexOf("."));
            } else {
                s = s + ".00";
            }
        }
        return s;
    }

        String getHtmlSpace(String s) {
            String ret = s == null ? "&nbsp;" : s;
            return ret;
        }

        String ch2StdCurrFromNoDot(String s) {
            if (s != null) {
                if (s.indexOf(".") <= 0) {
                    if (s.length() > 2) {
                        s = s.substring(0, (s.length() - 2)) + "." + s.substring((s.length() - 2));
                    } else if (s.length() == 1) {
                        s = "0.0" + s;
                    } else {
                        s = "0." + s;
                    }
                }
            }
            return s;
        }
    %>
</html>