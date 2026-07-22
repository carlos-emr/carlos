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
  Page role: Renders `adjustBill.jsp` for the British Columbia billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_billing");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@page import="io.github.carlos_emr.carlos.billing.ca.bc.data.*,io.github.carlos_emr.*,io.github.carlos_emr.carlos.commn.model.*" %>
<%@page import="java.math.*, java.util.*, java.sql.*, io.github.carlos_emr.*, java.net.*,io.github.carlos_emr.carlos.billing.ca.bc.MSP.*" %>
<%@page import="org.springframework.web.context.WebApplicationContext,org.springframework.web.context.support.WebApplicationContextUtils, io.github.carlos_emr.carlos.entities.*" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.service.GstSettingsService" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.administration.GstReport" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.entities.Billingmaster" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPBillingNote" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcile" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.data.BillingFormData" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.data.BillingCodeData" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.data.BillingNote" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.BillingService" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Billing" %>
<%@ page import="io.github.carlos_emr.MyDateFormat" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.SxmlMisc" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>


<%
    BillingmasterDAO billingMasterDao = SpringUtils.getBean(BillingmasterDAO.class);
    DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
%>
<%

    String curUser_no = (String) session.getAttribute("user");
    String UpdateDate = "";
    String DemoNo = "";
    String DemoName = "";
    String DemoAddress = "";
    String DemoCity = "";
    String DemoProvince = "";
    String DemoPostal = "";
    String DemoDOB = "";
    String DemoSex = "";
    String hin = "";
    String location = "";
    String BillLocation = "";
    String BillLocationNo = "";
    String BillDate = "";
    String Provider = "";
    String BillType = "";
    String BillTotal = "";
    String visitdate = "";
    String visittype = "";
    String BillDTNo = "";
    String HCTYPE = "";
    String HCSex = "";
    String r_doctor_ohip = "";
    String r_doctor = "";
    String m_review = "";
    String specialty = "";
    String r_status = "";
    String roster_status = "";
    String billRegion = CarlosProperties.getInstance().getProperty("billRegion", "BC");
    int rowCount = 0;
    int rowReCount = 0;

    ////
    BillingFormData billform = new BillingFormData();
    List<BillingFormData.BillingVisit> billvisit = billform.getVisitType(billRegion);
    request.setAttribute("billvisit", billvisit);
    int bFlag = 0;
    String billingmasterNo = request.getParameter("billingmaster_no");
    if (billingmasterNo == null) {
        billingmasterNo = (String) request.getAttribute("billingmaster_no");
    }
    MSPReconcile msp = new MSPReconcile();
    Properties allFields = msp.getBillingMasterRecord(billingmasterNo);
    MSPBillingNote billingNote = new MSPBillingNote();
    String corrNote = billingNote.getNote(billingmasterNo);
    BillingNote bNote = new BillingNote();
    String messageNotes = bNote.getNote(billingmasterNo);
    //TODO get note for this record and put it on screen and then be able to save a new note

    GregorianCalendar now = new GregorianCalendar();
    int curYear = now.get(Calendar.YEAR);
    int curMonth = (now.get(Calendar.MONTH) + 1);
    int curDay = now.get(Calendar.DAY_OF_MONTH);

    String codes[] = {"W", "O", "P", "N", "X", "T", "D", "I"};
    request.setAttribute("codes", codes);
    String serviceLocation = allFields.getProperty("serviceLocation");


    BillingmasterDAO billingmasterDAO = (BillingmasterDAO) SpringUtils.getBean(BillingmasterDAO.class);
    Billingmaster billingmaster = billingmasterDAO.getBillingMasterByBillingMasterNo(billingmasterNo);
    Billing bill = billingmasterDAO.getBilling(billingmaster.getBillingNo());
    BillingCodeData bcd = new BillingCodeData();

    //fixes bug where invoice number is null when
    //bill changed to Private
    Billingmaster bm = billingMasterDao.getBillingmaster(Integer.parseInt(billingmasterNo));
    if (bm != null) {
        request.setAttribute("invoiceNo", String.valueOf(bm.getBillingNo()));
    }
    GstReport gstReport = new GstReport();
    java.math.BigDecimal __gstBd = io.github.carlos_emr.carlos.utility.SpringUtils.getBean(GstSettingsService.class).getCurrentPercent();
    String gstPercent = __gstBd == null ? "" : __gstBd.toPlainString();

%>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key='billing.billingCorrection.title'/></title>
    <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/calendar/calendar.css"
          title="win2k-cold-1"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
    <script type="text/javascript"
            src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:message key='global.javascript.calendar'/>"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/carlos-ajax.js"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
    <script language="JavaScript">
        if ('<carlos:encode value='<%= StringUtils.noNull((String) request.getAttribute("close")) %>' context="javaScriptBlock"/>' == 'true') {
            window.close();
        }

        function setfocus() {
            this.focus();
            //document.form1.billing_no.focus();
            //document.form1.billing_no.select();
        }

        function checkTextLimit(textField, maximumlength) {
            if (textField.value.length > maximumlength + 1) {
                alert("<fmt:message key='billing.billingCorrection.alert.maxCharacters'/>".replace("{0}", maximumlength));
            }
            if (textField.value.length > maximumlength) {
                textField.value = textField.value.substring(0, maximumlength);
            }
        }

        function correspondenceNote() {
            if (document.forms['reprocessBilling'].correspondenceCode.value == "0") {
                HideElementById('CORRESPONDENCENOTE');
            } else if (document.forms['reprocessBilling'].correspondenceCode.value == "C") {
                HideElementById('CORRESPONDENCENOTE');
            } else if (document.forms['reprocessBilling'].correspondenceCode.value == "N") {
                ShowElementById('CORRESPONDENCENOTE');
            } else {
                (document.forms['reprocessBilling'].correspondenceCode.value == "B")
                ShowElementById('CORRESPONDENCENOTE');
            }
        }


        function rs(n, u, w, h, x) {
            args = "width=" + w + ",height=" + h + ",resizable=yes,scrollbars=yes,status=0,top=60,left=30";
            remote = window.open(u, n, args);
            if (remote != null) {
                if (remote.opener == null)
                    remote.opener = self;
            }
            if (x == 1) {
                return remote;
            }
        }

        function popupPage2(varpage) {
            var page = "" + varpage;
            windowprops = "height=700,width=800,location=no,"
                + "scrollbars=yes,menubars=no,toolbars=no,resizable=yes,top=0,left=0";
            window.open(page, "<fmt:message key='encounter.Index.popupPage2Window'/>", windowprops);
        }

        var awnd = null;

        function ScriptAttach(elementName) {
            var d = elementName;
            var t0 = encodeURIComponent(document.forms['reprocessBilling'].elements[d].value);
            var t1 = encodeURIComponent("");
            var t2 = encodeURIComponent("");
            awnd = rs('att', '<rewrite:reWrite jspPage="/billing/CA/BC/ViewBillingDigNewSearch" context="javaScriptBlock"/>?name=' + t0 + '&name1=' + t1 + '&name2=' + t2 + '&search=&formElement=' + encodeURIComponent(d) + '&formName=reprocessBilling', 820, 660, 1);
            awnd.focus();
        }

        function GetPriceOfCode(formName, codeElementName, priceElementName) {
            var code = codeElementName;
            var form = formName;
            var price = priceElementName;
            var t0 = encodeURIComponent(document.forms[form].elements[code].value);
            var t1 = encodeURIComponent("");
            var t2 = encodeURIComponent("");
            awnd = rs('att', '<rewrite:reWrite jspPage="/billing/CA/BC/ViewBillingGetPriceCode" context="javaScriptBlock"/>?name=' + t0 + '&name1=' + t1 + '&name2=' + t2 + '&search=&formElementCode=' + t0 + '&formName=' + encodeURIComponent(form) + '&formElementPrice=' + encodeURIComponent(price) + '&formNothing=blank', 820, 660, 1);
            awnd.focus();
        }

        function OtherScriptAttach() {
            var t0 = encodeURIComponent(document.forms['reprocessBilling'].service_code.value);
            var t1 = encodeURIComponent("");
            var t2 = encodeURIComponent("");
            awnd = rs('att', '<rewrite:reWrite jspPage="/billing/CA/BC/ViewBillingCodeNewSearch" context="javaScriptBlock"/>?name=' + t0 + '&name1=' + t1 + '&name2=' + t2 + '&search=&formName=reprocessBilling&formElement=service_code', 820, 660, 1);
            awnd.focus();
        }

        function ReferralScriptAttach(elementName) {
            var d = elementName;
            var t0 = encodeURIComponent(document.forms['reprocessBilling'].elements[d].value);
            var t1 = encodeURIComponent("");
            awnd = rs('att', '<rewrite:reWrite jspPage="/billing/CA/BC/ViewBillingReferCodeSearch" context="javaScriptBlock"/>?name=' + t0 + '&name1=' + t1 + '&name2=&search=&formElement=' + encodeURIComponent(d) + '&formName=reprocessBilling', 600, 600, 1);
            awnd.focus();
        }


        //
        function HideElementById(ele) {
            document.getElementById(ele).style.display = 'none';
        }

        function ShowElementById(ele) {
            document.getElementById(ele).style.display = '';
        }


        function checkDebitRequest() {
            if (document.forms['reprocessBilling'].submissionCode.value == "E") {
                ShowElementById('DEBITREQUEST');
                ShowElementById('submitButton');
            } else {
                HideElementById('DEBITREQUEST');
                HideElementById('submitButton');
            }
        }

        function showRecord() {
            if (document.getElementById('SENDRECORD').style.display == 'none') {
                ShowElementById('SENDRECORD');
            } else {
                HideElementById('SENDRECORD');
            }
        }


        function validateNum(el) {
            var val = el.value;
            var tval = "" + val;
            if (isNaN(val)) {
                alert("<fmt:message key='billing.billingCorrection.alert.numeric'/>");
                el.select();
                el.focus();
                return false;
            }
            if (val >= 99999.99) {
                alert("<fmt:message key='billing.billingCorrection.alert.belowMax'/>");
                el.select();
                el.focus();
                return false;
            }
            decLen = tval.indexOf(".");
            if (decLen != -1 && (tval.length - decLen) > 3) {
                alert("<fmt:message key='billing.billingCorrection.alert.decimals'/>");
                el.select();
                el.focus();
                return false;
            }
            return true;
        }

        function isNumeric(n) {
            return !isNaN(parseFloat(n)) && isFinite(n);
        }

        function checkSubmitType() {
            var billtype = document.getElementById('status').value;
            if (billtype == 'W') {
                if (document.forms[0].WCBid == null || document.forms[0].WCBid.value == "") {
                    alert("<fmt:message key='billing.billingCorrection.alert.selectWcbForm'/>");
                    return false;
                }
            }
            //Simple validation to prevent billing unit being invalid
            var billingUnit = document.getElementById('billingUnit').value;
            if (!isNumeric(billingUnit)) {
                alert("<fmt:message key='billing.billingCorrection.alert.validBillingUnit'/>");
                return false;
            }
        }

        function popup(height, width, url, windowName) {
            var page = url;
            windowprops = "height=" + height + ",width=" + width + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
            var popup = window.open(url, windowName, windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
            }
            popup.focus();
            return false;
        }

        function popFeeItemList(form, field) {
            var width = 575;
            var height = 400;
            var serviceDate = document.getElementById('serviceDate').value;
            var str = document.forms[form].elements[field].value;
            var providerNo = document.getElementById('providerNo').value;
            var url = '<rewrite:reWrite jspPage="/billing/CA/BC/support/BillingFeeItem" context="javaScriptBlock"/>'
                + '?form=' + encodeURIComponent(form)
                + '&field=' + encodeURIComponent(field)
                + '&feeField=billingAmount&corrections=1'
                + '&searchStr=' + encodeURIComponent(str)
                + '&serviceDate=' + encodeURIComponent(serviceDate)
                + '&providerNo=' + encodeURIComponent(providerNo);
            var windowName = field;
            popup(height, width, url, windowName);
        }

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

        //Rounding function seems to use the right rules for MSP
        let GST = <%=gstPercent.isEmpty() ? 0 : gstPercent%>;

        function calculateFee() {
            var billValue = document.getElementById("billValue").value;
            var billUnit = document.getElementById("billingUnit").value;
            var withGst = document.getElementById("isGst").value === "true";
            let subTotal = billValue * billUnit;
            if (withGst && GST > 0) {
                let gstVal = GST / 100;
                let gstTotal = gstVal * subTotal;

                document.getElementById("gstTotal").value = (Math.round(gstTotal * 100) / 100).toFixed(2);
                subTotal += gstTotal;
            } else {
                document.getElementById("gstTotal").value = '0.00';
            }
            var roundedValue = Math.round(subTotal * 100) / 100;
            document.getElementById("billingAmount").value = roundedValue.toFixed(2);
        }

        function calculateGst() {
            var billAmount = document.getElementById("billingAmount").value;
            var withGst = document.getElementById("isGst").value === "true";
            if (withGst && GST > 0) {
                var gstTotal = (GST / (100 + GST)) * billAmount;
                document.getElementById("gstTotal").value = (Math.round(gstTotal * 100) / 100).toFixed(2);
            } else {
                document.getElementById("gstTotal").value = '0.00';
            }
        }

    </script>
    <style type="text/css">
        td.bCellData {
            font-weight: bold;
            font-family: Arial, Helvetica, sans-serif;
        }

        th.bHeaderData {
            font-weight: bold;
            font-family: Arial, Helvetica, sans-serif;
        }
    </style>
</head>


<body bgcolor="#FFFFFF" text="#000000" topmargin="5" leftmargin="0" rightmargin="0"
      onLoad="setfocus();checkDebitRequest();correspondenceNote();">
<table width="100%" border="0" cellspacing="0" cellpadding="0">
    <tr bgcolor="#000000">
        <td height="40" width="10%"></td>
        <td width="90%" align="left">
            <p><font face="Verdana, Arial, Helvetica, sans-serif" color="#FFFFFF"><b><font
                    face="Arial, Helvetica, sans-serif" size="4">oscar<fmt:message key='billing.billingCorrection.msgBillingCorrection'/></font></b></font>
            </p>
        </td>
    </tr>
</table>
<%
    if (allFields == null) {
        /////////////////////////////////////////////////////////////////////////
%>
<table width="100%" border="0" cellspacing="0" cellpadding="0">
    <tr>
        <td height="40" width="10%"><fmt:message key="billing.billingCorrection.msgNoRecordFound"/></td>
    </tr>
</table>
</body>
</html>

<%
        ///////////////////////////////////////////////////////////////////////
        return;
    }
%>
<table width="100%" border="0" bgcolor="#FFFFFF">
    <tr>
        <td align="left" class="bCellData">
            <fmt:message key='billing.billingCorrection.msgOfficeClaimNo'/>
        </td>
        <td class="bCellData">
            <carlos:encode value='<%= billingmasterNo != null ? billingmasterNo : "" %>' context="html"/>
        </td>
        <td align="left" class="bCellData">
            <font color="#000000"><fmt:message key='billing.billingCorrection.msgLastUpdate'/>: <carlos:encode value='<%= MyDateFormat.getMyStandardDate(bill.getUpdateDate()) %>' context="html"/>
            </font>
        </td>
        <td align="right" class="bCellData">
            <fmt:message key='billing.billingCorrection.msgCreator'/>:  <carlos:encode value='<%= providerBean.getProperty(bill.getCreator(), bill.getCreator()) %>' context="html"/>
        </td>
    </tr>

</table>

<SCRIPT Language="Javascript">
    function printBill() {
        if (window.print) {
            window.print();
        } else {
            var WebBrowser = '<OBJECT ID="WebBrowser1" WIDTH=0 HEIGHT=0 CLASSID="CLSID:8856F961-340A-11D0-A96B-00C04FD705A2"></OBJECT>';
            document.body.insertAdjacentHTML('beforeEnd', WebBrowser);
            WebBrowser1.ExecWB(6, 2);//Use a 1 vs. a 2 for a prompting dialog box    WebBrowser1.outerHTML = "";
        }
    }
</script>
<table>
    <%
        ArrayList li = msp.getAllC12Records(billingmasterNo);
        li.addAll(msp.getAllS00Records(billingmasterNo));

        for (int i = 0; i < li.size(); i++) {
            String rejReason = (String) li.get(i);
    %>
    <tr>
        <td><carlos:encode value='<%= rejReason %>' context="html"/>
        </td>
    </tr>

    <% }%>
</table>

<%


    DemoNo = "" + bill.getDemographicNo();
    UpdateDate = MyDateFormat.getMyStandardDate(bill.getUpdateDate());//  rslocation.getString("update_date");
    BillDate = MyDateFormat.getMyStandardDate(bill.getBillingDate());//  rslocation.getString("billing_date");
    BillType = bill.getStatus();
    Provider = bill.getProviderNo();
    visitdate = MyDateFormat.getMyStandardDate(bill.getVisitDate());  //rslocation.getString("visitdate");
    visittype = bill.getVisitType();

    BillType = allFields.getProperty("billingstatus");
    if (BillType.equalsIgnoreCase("O") && bill.getBillingtype().equalsIgnoreCase("ICBC")) {
        BillType = "I";
    }
    Demographic d = demographicDao.getDemographic(DemoNo);
    if (d != null) {
        DemoName = d.getFormattedName();
        DemoSex = d.getSex();
        DemoAddress = d.getAddress();
        DemoCity = d.getCity();
        DemoProvince = d.getProvince();
        DemoPostal = d.getPostal();
        DemoDOB = MyDateFormat.getStandardDate(Integer.parseInt(d.getYearOfBirth()), Integer.parseInt(d.getMonthOfBirth()), Integer.parseInt(d.getDateOfBirth()));
        hin = d.getHin() + d.getVer();
        if (d.getFamilyDoctor() == null) {
            r_doctor = "N/A";
            r_doctor_ohip = "000000";
        } else {
            r_doctor = SxmlMisc.getXmlContent(d.getFamilyDoctor(), "rd") == null ? "" : SxmlMisc.getXmlContent(d.getFamilyDoctor(), "rd");
            r_doctor_ohip = SxmlMisc.getXmlContent(d.getFamilyDoctor(), "rdohip") == null ? "" : SxmlMisc.getXmlContent(d.getFamilyDoctor(), "rdohip");
        }

        HCTYPE = d.getHcType() == null ? "" : d.getHcType();
        if (DemoSex.equals("M")) HCSex = "1";
        if (DemoSex.equals("F")) HCSex = "2";
        roster_status = d.getRosterStatus();
    }

%>
<form name="reprocessBilling" action="${pageContext.request.contextPath}/billing/CA/BC/reprocessBill" method="post" onsubmit="return checkSubmitType()">
    <input type="hidden" name="update_date" value="<carlos:encode value='<%= UpdateDate %>' context="htmlAttribute"/>"/>
    <input type="hidden" name="demoNo" value="<carlos:encode value='<%= DemoNo %>' context="htmlAttribute"/>"/>
    <input type="hidden" name="billNumber" value="<carlos:encode value='<%= allFields.getProperty("billingNo", "") %>' context="htmlAttribute"/>"/>
    <table width="100%" border="0">
        <tr bgcolor="#CCCCFF">
            <td height="21" colspan="2" class="bCellData"><fmt:message key='billing.billingCorrection.msgPatientInformation'/><input type="hidden" name="billingmasterNo"
                                                                                    value="<carlos:encode value='<%= billingmasterNo %>' context="htmlAttribute"/>"/>
                   <c:set var="__enc_1"><carlos:encode value='<%= StringUtils.noNull((String) request.getAttribute("invoiceNo")) %>' context="uriComponent"/></c:set>

                <%if (BillType.equals("A") || BillType.equals("P")) {%>
                <a href="#"
                   onClick="popupPage(800,800, '<%=request.getContextPath()%>/billing/CA/BC/billingView?billing_no=<carlos:encode value='${__enc_1}' context="javaScriptAttribute"/>&receipt=yes')">View
                    Invoice</a>
                <%}%>
            </td>

        </tr>
        <tr>
            <td width="54%" class="bCellData">
                Patient Name:
                   <c:set var="__enc_2"><carlos:encode value='<%= DemoNo %>' context="uriComponent"/></c:set>
                <a href=#
                   onClick="popupPage2('<%= request.getContextPath() %>/demographic/DemographicEdit?demographic_no=<carlos:encode value='${__enc_2}' context="javaScriptAttribute"/>');return false;"
                   title="<fmt:message key='provider.appointmentProviderAdminDay.msgMasterFile'/>">
                    <carlos:encode value='<%= DemoName %>' context="html"/>
                </a>
                <input type="hidden" name="demo_name" value="<carlos:encode value='<%= DemoName %>' context="htmlAttribute"/>">
            </td>
            <td width="46%" class="bCellData">Health# :
                <% if (HCTYPE != null && HCTYPE.equals("BC")) { %>
                <carlos:encode value='<%= allFields.getProperty("phn", "") %>' context="html"/>
                <%} else {%>
                <carlos:encode value='<%= allFields.getProperty("oinRegistrationNo", "") %>' context="html"/>
                <%}%>
                Type
                <carlos:encode value='<%= HCTYPE %>' context="html"/>
            </td>
        </tr>
        <tr bgcolor="#EEEEFF">
            <td class="bCellData">
                Sex: <carlos:encode value='<%= DemoSex %>' context="html"/>
                <input type="hidden" name="demo_sex" value="<carlos:encode value='<%= DemoSex %>' context="htmlAttribute"/>">
                <input type="hidden" name="hc_sex" value="<carlos:encode value='<%= HCSex %>' context="htmlAttribute"/>">
            </td>
            <td class="bCellData">
                D.O.B. : <carlos:encode value='<%= DemoDOB %>' context="html"/>
                <input type="hidden" name="xml_dob" value="<carlos:encode value='<%= DemoDOB %>' context="htmlAttribute"/>">
            </td>
        </tr>
        <tr>
            <td class="bCellData">
                Address: <carlos:encode value='<%= DemoAddress %>' context="html"/>
                <input type="hidden" name="demo_address" value="<carlos:encode value='<%= DemoAddress %>' context="htmlAttribute"/>">
            </td>
            <td class="bCellData">
                City: <carlos:encode value='<%= DemoCity %>' context="html"/>
                <input type="hidden" name="demo_city" value="<carlos:encode value='<%= DemoCity %>' context="htmlAttribute"/>">
            </td>
        </tr>
        <tr bgcolor="#EEEEFF">
            <td class="bCellData">
                Province: <carlos:encode value='<%= DemoProvince %>' context="html"/>
                <input type="hidden" name="demo_province" value="<carlos:encode value='<%= DemoProvince %>' context="htmlAttribute"/>">
            </td>
            <td class="bCellData">
                Postal Code: <carlos:encode value='<%= DemoPostal %>' context="html"/>
                <input type="hidden" name="demo_postal" value="<carlos:encode value='<%= DemoPostal %>' context="htmlAttribute"/>">
            </td>
        </tr>
    </table>


    <table width="100%" border="0">
        <tr bgcolor="#CCCCFF">
            <td colspan="2" class="bCellData">
                <fmt:message key='billing.billingCorrection.msgBillingInf'/> Data Center <carlos:encode value='<%= allFields.getProperty("datacenter", "") %>' context="html"/> Payee
                Number: <carlos:encode value='<%= allFields.getProperty("payeeNo", "") %>' context="html"/> Practitioner
                Number: <carlos:encode value='<%= allFields.getProperty("practitionerNo", "") %>' context="html"/>
                Bill Type: <carlos:encode value='<%= StringUtils.noNull(bill.getBillingtype()) %>' context="html"/>
            </td>
        </tr>

        <tr>
            <td class="bCellData">
                <!-- includes the Billing Type Drop Down List -->
                <jsp:include flush="false" page="billType_frag.jsp">
                    <jsp:param name="BillType" value="<%=BillType%>"/>
                </jsp:include>
            </td>
            <td class="bCellData">
                <table>
                    <tr>
                        <td>
                            <%--<a href="#" onClick='rs("billingcalendar","<rewrite:reWrite jspPage="/billing/CA/BC/ViewBillingCalendarPopup"/>?year=<%=curYear%>&month=<%=curMonth%>&type=&returnForm=ReProcessBilling&returnItem=serviceDate","380","300","0")'>--%>
                            <a href="javascript: function myFunction() {return false; }" id="hlSDate">
                                Billing Date:
                            </a>
                        </td>
                        <td>
                            To:
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <input type="text" style="font-size:80%;" id="serviceDate" name="serviceDate"
                                   value="<carlos:encode value='<%= allFields.getProperty("serviceDate", "") %>' context="htmlAttribute"/>">
                            <%--<%=allFields.getProperty("serviceDate")%>"/><%=BillDate%>--%>
                        </td>
                        <td>
                            <input type="text" name="serviceToDay" value="<carlos:encode value='<%= allFields.getProperty("serviceToDay", "") %>' context="htmlAttribute"/>"
                                   size="2" maxlength="2"/>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr bgcolor="#EEEEFF">
            <td width="54%" class="bCellData">
                Clarification Code:
                <input type="text" name="locationVisit" value="<carlos:encode value='<%= allFields.getProperty("clarificationCode", "") %>' context="htmlAttribute"/>"
                       maxlength="2" size="2"/>
            </td>
            <td width="46%" class="bCellData">
                Billing Physician#:
                <select style="font-size:80%;" name="providerNo" id="providerNo">
                    <option value="">--- Select Provider ---</option>
                            <%
               // Retrieving Provider
               String proFirst="", proLast="", proOHIP="", proNo="";
               int Count = 0;
               for(Provider p:providerDao.getActiveProviders()) {
            	 if(p.getOhipNo() != null && !p.getOhipNo().isEmpty()) {
                    proFirst = p.getFirstName();
                    proLast = p.getLastName();
                    proOHIP = p.getProviderNo();

            %>
                    <option value="<carlos:encode value='<%= proOHIP %>' context="htmlAttribute"/>" <%=Provider.equals(proOHIP) ? "selected" : ""%>><carlos:encode value='<%= proOHIP %>' context="html"/>
                        | <carlos:encode value='<%= proLast + ", " + proFirst %>' context="html"/>
                    </option>
                            <% } }%>
                    <input type="hidden" name="xml_provider_no" value="<carlos:encode value='<%= Provider %>' context="htmlAttribute"/>">
            </td>
        </tr>
        <tr>
            <%visittype = allFields.getProperty("serviceLocation");%>
            <td width="54%" class="bCellData">
                Visit Type:
                <input type="hidden" name="xml_visittype" value="<carlos:encode value='<%= visittype %>' context="htmlAttribute"/>">
                <select name="serviceLocation" style="font-size:80%;max-width:300px;">
                    <%
                        for (BillingFormData.BillingVisit billingVisit : billvisit) {
                            String selected = serviceLocation.equals(billingVisit.getVisitType()) ? "selected" : "";
                    %>
                    <option value="<carlos:encode value='<%= billingVisit.getVisitType() %>' context="htmlAttribute"/>" <%=selected%>><carlos:encode value='<%= billingVisit.getDisplayName() %>' context="html"/>
                    </option>
                    <%
                        }
                    %>

                </select>

            </td>
            <td width="46%" class="bCellData">
                <input type="hidden" name="xml_visitdate" value="<carlos:encode value='<%= visitdate %>' context="htmlAttribute"/>">
                <a href="#"
                   onClick='rs("billingcalendar","<%= request.getContextPath() %>/billing/CA/BC/ViewBillingCalendarPopup?year=<carlos:encode value='<%= String.valueOf(curYear) %>' context="javaScriptAttribute"/>&month=<carlos:encode value='<%= String.valueOf(curMonth) %>' context="javaScriptAttribute"/>&type=&returnForm=serviceform&returnItem=xml_vdate","380","300","0")'>
                    Admission Date:
                </a>
                <input type="text" style="font-size:80%;" name="xml_vdate" value="<carlos:encode value='<%= visitdate %>' context="htmlAttribute"/>">
            </td>
        </tr>
        <tr>
            <td class="bCellData">Dependent Number:
                <select name="dependentNo">
                    <option value="00" <%=allFields.getProperty("dependentNum").equals("00") ? "selected" : ""%>>00
                    </option>
                    <option value="66" <%=allFields.getProperty("dependentNum").equals("66") ? "selected" : ""%>>66
                    </option>
                </select>
            </td>
            <td class="bCellData">New Program Ind:
                <input type="text" name="newProgram" value="<carlos:encode value='<%= allFields.getProperty("newProgram", "") %>' context="htmlAttribute"/>" size="2"
                       maxlength="2"/>
            </td>
        </tr>
        <tr>
            <td class="bCellData">After Hours:
                <select name="afterHours">
                    <option value="0" <%=allFields.getProperty("afterHour").equals("0") ? "selected" : ""%>>NO</option>
                    <option value="E" <%=allFields.getProperty("afterHour").equals("E") ? "selected" : ""%>>Evening
                        Call
                    </option>
                    <option value="N" <%=allFields.getProperty("afterHour").equals("N") ? "selected" : ""%>>Night Call
                    </option>
                    <option value="W" <%=allFields.getProperty("afterHour").equals("W") ? "selected" : ""%>>Weekend
                        Call
                    </option>
                </select>
            </td>
            <td class="bCellData">Time Call Received<!--TIME-CALL-RECVD-SRV-->
                <input type="text" name="timeCallRec" value="<carlos:encode value='<%= allFields.getProperty("timeCall", "") %>' context="htmlAttribute"/>" size="4"
                       maxlength="4"/>
                <input type="hidden" name="anatomicalArea" value="<carlos:encode value='<%= allFields.getProperty("anatomicalArea", "") %>' context="htmlAttribute"/>"/>
            </td>
        </tr>

        <tr>

            <td class="bCellData">Service Time Start<%! /*SERVICE-TIME-START*/ %>
                <input type="text" name="startTime" value="<carlos:encode value='<%= allFields.getProperty("serviceStartTime", "") %>' context="htmlAttribute"/>" size="4"
                       maxlength="4"/></td>

            <td class="bCellData">Service Time Finish <%! /*SERVICE-TIME-FINISH*/ %>
                <input type="text" name="finishTime" value="<carlos:encode value='<%= allFields.getProperty("serviceEndTime", "") %>' context="htmlAttribute"/>" size="4"
                       maxlength="4"/></td>

        </tr>
        <tr>
            <td class="bCellData">MVA
                <select name="mvaClaim">
                    <option value="N" <%=allFields.getProperty("mvaClaimCode").equals("N") ? "selected" : ""%>>No
                    </option>
                    <option value="Y" <%=allFields.getProperty("mvaClaimCode").equals("Y") ? "selected" : ""%>>Yes
                    </option>
                </select>
            </td>
            <td class="bCellData">ICBC Claim Num:
                <input type="text" name="icbcClaim" value="<carlos:encode value='<%= allFields.getProperty("icbcClaimNo", "") %>' context="htmlAttribute"/>" size="8"
                       maxlength="8"/></td>
        </tr>
        <tr>


            <td class="bCellData">Facility Number
                <input type="text" name="facilityNum" value="<carlos:encode value='<%= allFields.getProperty("facilityNo", "") %>' context="htmlAttribute"/>" size="5"
                       maxlength="5"/></td>

            <td class="bCellData">Facility Sub Number
                <input type="text" name="facilitySubNum" value="<carlos:encode value='<%= allFields.getProperty("facilitySubNo", "") %>' context="htmlAttribute"/>" size="5"
                       maxlength="5"/></td>
        </tr>
    </table>
    <%
        BillingService billService = bcd.getBillingCodeByCode(allFields.getProperty("billingCode"), billingmaster.getServiceDateAsDate());
        String billValue = "0.00";
        boolean gstFlag = false;
        if (billService != null) {
            billValue = billService.getValue() != null ? billService.getValue() : "0.00";
            gstFlag = billService.getGstFlag();
        }
    %>
    <table width="100%" border=1>
        <tr bgcolor="#CCCCFF">
            <td class="bCellData"><fmt:message key="billing.billingCorrection.formServiceCode"/></td>
            <td width="50%" class="bCellData"><fmt:message key="billing.billingCorrection.formDescription"/></td>
            <td class="bCellData"><fmt:message key="billing.billingCorrection.formUnit"/></td>
            <td class="bCellData">
                <div align="right"><fmt:message key="billing.billingCorrection.formFee"/></div>
            </td>
            <td class="bCellData"><fmt:message key="billing.billingCorrection.formInternalAdj"/></td>
        </tr>

        <tr>
            <td class="bCellData">

                <input type="text" style="font-size:80%;" name="service_code"
                       value="<carlos:encode value='<%= allFields.getProperty("billingCode", "") %>' context="htmlAttribute"/>" size="10">
                <input type="button"
                       onClick="javascript:popFeeItemList('reprocessBilling','service_code');return false;"
                       value="<fmt:message key='billing.billingCorrection.btnSearchUpdate'/>"/>
            </td>
            <td width="50%" class="bCellData">
                <span id="description"><carlos:encode value='<%= billform.getServiceDesc(allFields.getProperty("billingCode"), billRegion) %>' context="html"/></span>
                ($<span id="valueDisplay"><carlos:encode value='<%= billValue %>' context="html"/></span>)
                <input type="hidden" value="<carlos:encode value='<%= billValue %>' context="htmlAttribute"/>" id="billValue"/>
                <input type="hidden" value="<%=gstFlag%>" id="isGst"/>
                <input type="button" value="<fmt:message key='billing.billingCorrection.btnRecalculate'/>" onclick="calculateFee()"/>
                <small style="float: right; display: <%=gstFlag? "" : "none"%>"
                       id="currentGST"><%=("+ " + SafeEncode.forHtml(gstPercent) + "% GST")%>
                </small>
                <input type="hidden" name="gstTotal" id="gstTotal" value="<carlos:encode value='<%= allFields.getProperty("gst", "0.00") %>' context="htmlAttribute"/>"/>
            </td>
            <td class="bCellData">
                <input type="hidden" name="billing_unit" value="<carlos:encode value='<%= allFields.getProperty("billingUnit", "") %>' context="htmlAttribute"/>">
                <input type="text" style="font-size:80%;" name="billingUnit"
                       value="<carlos:encode value='<%= allFields.getProperty("billingUnit", "") %>' context="htmlAttribute"/>" size="6" maxlength="6" id="billingUnit">
            </td>
            <td class="bCellData" nowrap>
                <div align="right">
                    <input type="hidden" name="billing_amount" value="<carlos:encode value='<%= allFields.getProperty("bilAmount", "") %>' context="htmlAttribute"/>">
                    <input type="text" style="font-size:80%;" size="8" maxlength="8" name="billingAmount"
                           value="<carlos:encode value='<%= allFields.getProperty("billAmount", "") %>' context="htmlAttribute"/>"
                           onChange="javascript:validateNum(this); calculateGst()" id="billingAmount">
                </div>
            </td>
            <td>
                <label>
                    <fmt:message key="billing.billingCorrection.labelAmount"/>
                    <input name="adjAmount" type="text" size="7" maxlength="7">
                </label>
                <label>
                    <input type="checkbox" name="adjType" value="1"/>
                    <fmt:message key="billing.billingCorrection.labelDebit"/>
                </label>
            </td>
        </tr>
    </table>
    <table width="100%" border=1>

        <tr>
            <td colspan=2 width="75%">
                <table width="100%">
                    <tr bgcolor="#CCCCFF">
                        <td colspan=2 class="bCellData">
                            <fmt:message key="billing.billingCorrection.formDiagnosticCode"/>
                        </td>


                    </tr>
                    <tr>
                        <td class="bCellData">
                            <a href="javascript:ScriptAttach('dx1')"><fmt:message key="billing.billingCorrection.dx1"/></a><input type="text" name="dx1"
                                                                                    onClick="checkSubmitType()"
                                                                                    value="<carlos:encode value='<%= allFields.getProperty("dxCode1", "") %>' context="htmlAttribute"/>"
                                                                                    size="10">
                        </td>
                        <td><carlos:encode value='<%= billform.getDiagDesc(allFields.getProperty("dxCode1"), billRegion) %>' context="html"/>
                        </td>
                    </tr>
                    <tr>
                        <td class="bCellData">
                            <a href="javascript:ScriptAttach('dx2')"><fmt:message key="billing.billingCorrection.dx2"/></a><input type="text" name="dx2"
                                                                                    onClick="checkSubmitType()"
                                                                                    value="<carlos:encode value='<%= allFields.getProperty("dxCode2", "") %>' context="htmlAttribute"/>"
                                                                                    size="10">
                        </td>
                        <td><carlos:encode value='<%= billform.getDiagDesc(allFields.getProperty("dxCode2"), billRegion) %>' context="html"/>
                        </td>
                    </tr>
                    <tr>
                        <td class="bCellData">
                            <a href="javascript:ScriptAttach('dx3')"><fmt:message key="billing.billingCorrection.dx3"/></a><input type="text" name="dx3"
                                                                                    onClick="checkSubmitType()"
                                                                                    value="<carlos:encode value='<%= allFields.getProperty("dxCode3", "") %>' context="htmlAttribute"/>"
                                                                                    size="10">
                        </td>
                        <td><carlos:encode value='<%= billform.getDiagDesc(allFields.getProperty("dxCode3"), billRegion) %>' context="html"/>
                        </td>
                    </tr>
                </table>
            </td>
            <td colspan=2 valign="top">
                <table width="100%">
                    <tr bgcolor="#CCCCFF">
                        <td colspan=2 class="bCellData">
                            Referrals
                            <% String refCD1 = allFields.getProperty("referralFlag1");
                                String refCD2 = allFields.getProperty("referralFlag2");
                                if (refCD1 == null || refCD1.equals("null")) {
                                    refCD1 = "0";
                                }
                                if (refCD2 == null || refCD2.equals("null")) {
                                    refCD2 = "0";
                                }
                            %>
                        </td>

                    </tr>
                    <tr>
                        <td class="bCellData">1.
                            <select name="referalPracCD1">

                                <option value="0" <%=refCD1.equals("0") ? "selected" : ""%>>None</option>
                                <option value="T" <%=refCD1.equals("T") ? "selected" : ""%>>TO</option>
                                <option value="B" <%=refCD1.equals("B") ? "selected" : ""%>>BY</option>
                            </select>
                        </td>
                        <td class="bCellData">
                            <input type="button" onClick="javascript:ReferralScriptAttach('referalPrac1')"
                                   value="Search"/>
                            <input type="text" name="referalPrac1" value="<carlos:encode value='<%= allFields.getProperty("referralNo1", "") %>' context="htmlAttribute"/>"
                                   size="5" maxlength="5"/>
                        </td>
                    </tr>
                    <tr>
                        <td class="bCellData">2.
                            <select name="referalPracCD2">
                                <option value="0" <%=refCD2.equals("0") ? "selected" : ""%>>None</option>
                                <option value="T" <%=refCD2.equals("T") ? "selected" : ""%>>TO</option>
                                <option value="B" <%=refCD2.equals("B") ? "selected" : ""%>>BY</option>
                            </select>
                        </td>
                        <td class="bCellData">
                            <input type="button" onClick="javascript:ReferralScriptAttach('referalPrac2')"
                                   value="Search"/>
                            <input type="text" name="referalPrac2" value="<carlos:encode value='<%= allFields.getProperty("referralNo2", "") %>' context="htmlAttribute"/>"
                                   size="5" maxlength="5"/>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="bCellData">Payment Mode</td><!--PAYMENT MODE-->
            <td class="bCellData">
                <select name="paymentMode">
                    <option value="0" <%=allFields.getProperty("paymentMode").equals("0") ? "selected" : ""%>>Fee For
                        Service
                    </option>
                    <option value="E" <%=allFields.getProperty("paymentMode").equals("E") ? "selected" : ""%>>Alternate
                        Funding
                    </option>
                </select>
            </td>
            <td class="bCellData">Submission Code</td><!--SUBMISSION-CODE-->
            <td class="bCellData">
                <select name="submissionCode" onChange="checkDebitRequest();">
                    <option value="0" <%=allFields.getProperty("submissionCode").equals("0") ? "selected" : ""%>>
                        0|Normal Submission
                    </option>
                    <option value="D" <%=allFields.getProperty("submissionCode").equals("D") ? "selected" : ""%>>
                        D|Duplicate
                    </option>
                    <option value="E" <%=allFields.getProperty("submissionCode").equals("E") ? "selected" : ""%>>E|Debit
                        Request
                    </option>
                    <option value="I" <%=allFields.getProperty("submissionCode").equals("I") ? "selected" : ""%>>I|ICBC
                        Claim
                    </option>
                    <option value="W" <%=allFields.getProperty("submissionCode").equals("W") ? "selected" : ""%>>W|Claim
                        not accepted by WCB
                    </option>
                    <option value="C" <%=allFields.getProperty("submissionCode").equals("C") ? "selected" : ""%>>
                        C|Subscriber Coverage Problem
                    </option>
                    <option value="R" <%=allFields.getProperty("submissionCode").equals("R") ? "selected" : ""%>>
                        R|Resubmit Claim
                    </option>
                    <option value="A" <%=allFields.getProperty("submissionCode").equals("A") ? "selected" : ""%>>
                        A|Pre-approved claim
                    </option>
                    <option value="X" <%=allFields.getProperty("submissionCode").equals("X") ? "selected" : ""%>>
                        X|Resubmitting refused or part paid
                    </option>
                </select>
            </td>
        </tr>
        <!--<tr>
            <td>Service Date</td><%/*SERVICE-DATE*/%>
            <td><input type="text" name="serviceDate" value="<%=allFields.getProperty("serviceDate")%>"/></td>
            <td>Service to Day</td><%/*SERVICE-TO-DAY*/%>
            <td<input type="text" name="serviceToDay" value="<%=allFields.getProperty("serviceToDay")%>"/></td>
       </tr>-->
        <!--
       <tr>
            <td>Time Call Received</td><%!/*TIME-CALL-RECVD-SRV*/%>
            <td><input type="text" name="timeCallRec" value="<%=allFields.getProperty("timeCall")%>" size="4"/></td>
            <td>&nbsp;</td>
            <td>&nbsp;</td>
       </tr>
       <tr>

            <td>Service Time Start</td><%! /*SERVICE-TIME-START*/ %>
            <td><input type="text" name="startTime" value="<%=allFields.getProperty("serviceStartTime")%>" size="4"/></td>

            <td>Service Time Finish</td><%! /*SERVICE-TIME-FINISH*/ %>
            <td><input type="text" name="finishTime" value="<%=allFields.getProperty("serviceEndTime")%>" size="4"</td>

       </tr>
       -->

        <tr>
            <td class="bCellData" colspan="4">
                <div id="DEBITREQUEST">
                    Select sequence number you would like to debit <input name="debitRequestSeqNum" type="text"
                                                                          maxlength="7" size="7"
                                                                          value="<carlos:encode value='<%= getDebitRequestSeqNum(allFields.getProperty("originalClaim")) %>' context="htmlAttribute"/>"/>
                    </br><fmt:message key="billing.billingCorrection.msgSelectMSPReceivedDate"/> <input
                        id="debitRequestDate" name="debitRequestDate" type="text" maxlength="8" size="8"
                        value="<carlos:encode value='<%= getDebitRequestDate(allFields.getProperty("originalClaim")) %>' context="htmlAttribute"/>"/>
                    <a id="hlADate"><img title="Calendar" src="<%= request.getContextPath() %>/images/cal.gif" alt="Calendar" border="0"/></a>
                    <!--Date Received MSP <input type="text" />-->
                </div>
            </td>
        </tr>


        <tr>

            <td class="bCellData">Correspondence Code</td><!--CORRESPONDENCE-CODE-->
            <td class="bCellData">
                <select name="correspondenceCode" onChange="correspondenceNote();">
                    <option value="0" <%=allFields.getProperty("correspondenceCode").equals("0") ? "selected" : ""%>>
                        None
                    </option>
                    <option value="C" <%=allFields.getProperty("correspondenceCode").equals("C") ? "selected" : ""%>>
                        Paper Note
                    </option>
                    <option value="N" <%=allFields.getProperty("correspondenceCode").equals("N") ? "selected" : ""%>>
                        Elec Note
                    </option>
                    <option value="B" <%=allFields.getProperty("correspondenceCode").equals("B") ? "selected" : ""%>>
                        Both
                    </option>
                </select>
            </td>
            <td class="bCellData">Insurer Code</td><!--OIN-INSURER-C0DE-->
            <td class="bCellData">
                <select name="insurerCode">
                    <option value="" <%=allFields.getProperty("oinInsurerCode").equals("0") ? "selected" : ""%>>None
                    </option>
                    <option value="IN" <%=allFields.getProperty("oinInsurerCode").equals("IN") ? "selected" : ""%>>
                        Institutional Claim
                    </option>
                    <option value="PP" <%=allFields.getProperty("oinInsurerCode").equals("PP") ? "selected" : ""%>>Pay
                        Patient
                    </option>
                    <option value="WC" <%=allFields.getProperty("oinInsurerCode").equals("WC") ? "selected" : ""%>>WCB
                    </option>
                </select>
            </td>
        </tr>
        <tr>
            <td class="bCellData">Claim Short Comment</td><!--CLAIM-SHORT-COMMENT-->
            <td><input type="text" name="shortComment" value="<carlos:encode value='<%= allFields.getProperty("claimComment", "") %>' context="htmlAttribute"/>" size="20"
                       maxlength="20"/></td>
            <td class="bCellData">Note</td>
            <td>
                <div id="CORRESPONDENCENOTE">
                    <textarea cols="60" rows="5" name="notes"
                              onKeyUp="checkTextLimit(this.form.notes,400);"><carlos:encode value='<%= StringUtils.noNull(corrNote) %>' context="html"/></textarea>
                </div>
            </td>

        </tr>
        <tr valign="top">
            <td>
                <table width="100%">
                    <tr bgcolor="#CCCCFF">
                        <td class="bCellData">Billing Notes</td>
                    </tr>
                    <tr>

                        <!--CLAIM-SHORT-COMMENT-->
                        <td colspan="3">
                            <c:set var="__enc_3"><carlos:encode value='<%= String.valueOf(bill.getDemographicNo()) %>' context="uriComponent"/></c:set>
                            <c:set var="__enc_4"><carlos:encode value='<%= allFields.getProperty("billingCode", "") %>' context="uriComponent"/></c:set>
                            <textarea cols="60" rows="5" name="messageNotes"><carlos:encode value="            
<%= StringUtils.noNull(messageNotes) %>" context="html"/></textarea>
                        </td>
                        <td></td>

                </table>

            <td colspan="3">
                <jsp:include flush="false" page="billTransactions.jsp">
                    <jsp:param name="billMasterNo" value='<%= billingmasterNo != null ? billingmasterNo : "" %>'/>
                </jsp:include>
            </td>

    </table>

    <script type="text/javascript">
        function callReplacementWebService(url, id) {
            var ran_number = Math.round(Math.random() * 1000000);
            // forUriComponent encodes URL parameter values, forJavaScript wraps for JS string context; rand busts cache
            var params = "demographicNo=<carlos:encode value='${__enc_3}' context="javaScript"/>&wcb=&billingcode=<carlos:encode value='${__enc_4}' context="javaScript"/>&rand=" + ran_number;
            CarlosAjax.updater(id, url, {method: 'get', parameters: params});
        }

        function replaceWCB(id) {
            oscarLog("In replaceWCB");
            var ur = "<%= request.getContextPath() %>/billing/CA/BC/ViewWcbForms?wcbid=" + id;
            callReplacementWebService(ur, 'wcbForms');
            oscarLog("replaceWCB out == " + ur);
        }

        function toggleWCB() {
            var statusType = document.getElementById('status');
            //alert(statusType.value);

            if (statusType.value == "W") {
                oscarLog("Replacing WCB element");
                replaceWCB('0');
            } else {
                document.getElementById('wcbForms').innerHTML = "";
            }

        }


        <%if(bill.getBillingtype().equals("WCB")){ %>
        oscarLog("DOES THIS LOG");
        replaceWCB('<carlos:encode value='<%= String.valueOf(billingmaster.getWcbId()) %>' context="javaScriptBlock"/>');

        <%}%>
    </script>
    <div id="wcbForms"></div>


    <!--<tr>
    <td>Facility Num</td><%! /*FACILITY-NUM*/ %>
    <td><input type="text" name="facilityNum" value="<%=allFields.getProperty("facilityNo")%>" size="5"/></td>
    <td>Facility Sub Num</td><%! /*FACILITY-SUB-NUM*/%>
    <td><input type="text" name="facilitySubNum" value="<%=allFields.getProperty("facilitySubNo")%>" size="5"/></td>
    </tr>-->

    <!--<tr>


    <td>Registration Num</td><%!/*OIN-REGISTRATION-NUM*/%>
    <td><input type="text" name="registrationNum" value="<%=allFields.getProperty("oinRegistrationNo")%>" size="12"/></td>
    </tr>-->
    <!--
    <tr>
    <td>First Name</td><%/*OIN-FIRST-NAME*/%>
    <td><input type="text" name="firstName" value="<%=allFields.getProperty("oinFirstName")%>" size="12"/></td>

    <td>Surname</td><%/*OIN-SURNAME*/%>
    <td><input type="text" name="surname" value="<%=allFields.getProperty("oinSurname")%>" size="18"/></td>
    </tr>
    <tr>
    <td>SEX</td>
    <td><input type="text" name="sex" value="<%=allFields.getProperty("oinSexCode")%>" size="1"/></td>
    <td>Birth date</td><%/*OIN-BIRTHDATE*/%>
    <td><input type="text" name="birthdate" value="<%=allFields.getProperty("oinBirthdate")%>" size="8"/></td>
    </tr>
    <tr>

    <td>Address 1 WCB Date Of Injury</td><%/*OIN-ADDRESS-1		WCB DATE OF INJURY*/%>
    <td colspan="3"><input type="text" name="address1" value="<%=allFields.getProperty("oinAddress")%>" size="25"/></td>
    </tr>
    <tr>
    <td>Address 2 WCB AREA OF INJURY</td><%/*OIN-ADDRESS-2 WCB AREA OF INJURY ANATOMICAL-POSITION*/%>
    <td colspan="3"><input type="text" name="address2" value="<%=allFields.getProperty("oinAddress2")%>" size="25"/></td>
    </tr>
    <tr>
    <Td>Address 3 WCB NATURE OF INJURY</td><%/*OIN-ADDRESS-3 WCB NATURE OF INJURY*/%>
    <td colspan="3"><input type="text" name="address3" value="<%=allFields.getProperty("oinAddress3")%>" size="25" /></td>
    </tr>
    <tr>
    <td>Address 4 WCB Claim Number</td><%/*OIN-ADDRESS-4 WCB CLAIM NUMBER*/%>
    <td colspan="3"><input type="text" name="address4" value="<%=allFields.getProperty("oinAddress4")%>" size="25" /></td>
    </tr>
    <tr>
    <td>Postal Code</td><%/*OIN-POSTAL-CODE*/%>
    <td colspan="3"><input type="text" name="postalCode" value="<%=allFields.getProperty("oinPostalcode")%>" size="6"/></td>
    </tr>
    -->


    <input type="hidden" value="0" name="saveandclose"/>

    <%
        if (!BillType.equals("S")) {
    %>
    <tr>
        <td colspan="4" class="bCellData">
            <input type="submit" name="submit" value="<fmt:message key='billing.billingCorrection.btnReprocessBill'/>">
            <input type="submit" name="submit" value="<fmt:message key='billing.billingCorrection.btnResubmitBill'/>">
            <input type="submit" name="submit" id="reprocessAndReSubmitBill" value="<fmt:message key='billing.billingCorrection.btnReprocessAndResubmitBill'/>">
            <input type="submit" name="submit" value="<fmt:message key='billing.billingCorrection.btnSettleBill'/>">

        </td>
    </tr>
    <%} else {%>
    <tr>
        <td colspan="4" class="bCellData">
            <input type="submit" name="submit" id="submitButton" style="display:none;"
                   value="<fmt:message key='billing.billingCorrection.btnReprocessAndResubmitBill'/>">
            <% if (!bill.getBillingtype().equals("Pri")) { %>
            <input type="submit" name="submit" value="<fmt:message key='billing.billingCorrection.btnRevertToPWE'/>">
            <% } %>
        </td>
    </tr>

    <%}%>


    </table>
</form>
<a href="javascript: function myFunction() {return false; }" onClick="javascript: showRecord();"><fmt:message key="billing.billingCorrection.btnViewFullRecord"/></a>
<div style="display: none;" id="SENDRECORD">
    <table border=1>
        <tr>
            <td>Name</td>
            <td>Value</td>
            <td>Size</td>
            <td>Shown</td>
        </tr>
        <tr>
            <!--REC-CODE-IN-->
            <td>Data-Center</td><!--DATA-CENTRE-NUM-->
            <td><carlos:encode value='<%= allFields.getProperty("datacenter", "") %>' context="html"/>
            </td>
            <td>5</td>
            <td>Y</td>
        </tr>
        <tr>

            <!--DATA-CENTER-SEQNUM-->
            <td>PAYEE-NUM</td><!--PAYEE-NUM-->
            <td><carlos:encode value='<%= allFields.getProperty("payeeNo", "") %>' context="html"/>
            </td>
            <td>5</td>
            <td>Y</td>
        </tr>
        <tr>
            <td>Practitioner Number</td><!--PRACTITIONER-NUM-->
            <td><carlos:encode value='<%= allFields.getProperty("practitionerNo", "") %>' context="html"/>
            </td><!--MSP PHN-->
            <td>5</td>
            <td></td>
        </tr>
        <tr>
            <td>PHN</td><!--PRACTITIONER-NUM-->
            <td><carlos:encode value='<%= allFields.getProperty("phn", "") %>' context="html"/>
            </td><!--MSP PHN-->
            <td>10</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Name Verify</td><!--NAME-VERIFY-->
            <td><carlos:encode value='<%= allFields.getProperty("nameVerify", "") %>' context="html"/>
            </td>
            <td>4</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Dependent Number</td><!--DEPENDENT-NUM-->
            <td><carlos:encode value='<%= allFields.getProperty("dependentNum", "") %>' context="html"/>
            </td>
            <td>2</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Billed Units</td><!--BILLED-SRV-UNITS-->
            <td><carlos:encode value='<%= allFields.getProperty("billingUnit", "") %>' context="html"/>
            </td>
            <td>3</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Service Clarification code</td><!--SERVICE CLARIFICATION CODE-->
            <Td><carlos:encode value='<%= allFields.getProperty("clarificationCode", "") %>' context="html"/>
            </td>
            <td>2</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Anatomical Area</td><!--MSP SERVICE ANATOMICAL AREA-->
            <td><carlos:encode value='<%= allFields.getProperty("anatomicalArea", "") %>' context="html"/>
            </td>
            <td>2</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>After Hours</td><!--AFTER HOURS SERVICE IND-->
            <td><carlos:encode value='<%= allFields.getProperty("afterHour", "") %>' context="html"/>
            </td>
            <td>1</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>New Program Ind</td><!--NEW PROGRAM IND-->
            <td><carlos:encode value='<%= allFields.getProperty("newProgram", "") %>' context="html"/>
            </td>
            <td>2</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Billed Fee Item</td><!--BILLED-FEE-ITEM-->
            <td><carlos:encode value='<%= allFields.getProperty("billingCode", "") %>' context="html"/>
            </td>
            <td>5</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Billed Amount</td><!--BILLED-AMOUNT-->
            <td><carlos:encode value='<%= allFields.getProperty("billAmount", "") %>' context="html"/>
            </td>
            <td>5</td>
            <td>Y</td>
        </tr>
        <tr>
            <td>Payment Mode</td><!--PAYMENT MODE-->
            <td><carlos:encode value='<%= allFields.getProperty("paymentMode", "") %>' context="html"/>
            </td>
            <td>1</td>
            <td>Y</td>
        </tr>
        <tr>
            <td>Service Date</td><!--SERVICE-DATE-->
            <td><carlos:encode value='<%= allFields.getProperty("serviceDate", "") %>' context="html"/>
            </td>
            <td>8</td>
            <td>Y</td>
        </tr>
        <tr>
            <td>Service to Day</td><!--SERVICE-TO-DAY-->
            <td><carlos:encode value='<%= allFields.getProperty("serviceToDay", "") %>' context="html"/>
            </td>
            <td>2</td>
            <td>Y</td>
        </tr>
        <tr>
            <td>Submission Code</td><!--SUBMISSION-CODE-->
            <td><carlos:encode value='<%= allFields.getProperty("submissionCode", "") %>' context="html"/>
            </td>
            <td>1</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Ex Submission Code</td><!--EXTENDED SUBMISSION CODE-->
            <td><carlos:encode value='<%= allFields.getProperty("extendedSubmissionCode", "") %>' context="html"/>
            </td>
            <td>1</td>
            <td></td>
        </tr>
        <tr>

            <td>Diag Code 1</td><!--DIAGNOSTIC-CODE-1-->
            <td><carlos:encode value='<%= allFields.getProperty("dxCode1", "") %>' context="html"/>
            </td>
            <td>5</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Diag Code 2</td><!--DIAGNOSTIC-CODE-2-->
            <td><carlos:encode value='<%= allFields.getProperty("dxCode2", "") %>' context="html"/>
            </td>
            <td>5</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Diag Code 3</td><!--DIAGNOSTIC-CODE-3-->
            <td><carlos:encode value='<%= allFields.getProperty("dxCode3", "") %>' context="html"/>
            </td>
            <td>5</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Diag Expansion</td><!--DIAGNOSTIC EXPANSION-->
            <td><carlos:encode value='<%= allFields.getProperty("dxExpansion", "") %>' context="html"/>
            </td>
            <td>15</td>
            <td></td>
        </tr>
        <tr>

            <td>Service Location</td><!--SERVICE-LOCATION-CD-->
            <td><carlos:encode value='<%= allFields.getProperty("serviceLocation", "") %>' context="html"/>
            </td>
            <td>1</td>
            <td></td>
        </tr>
        <tr>

            <td>Referal Practitioner CD</td><!--REF-PRACT-1-CD-->
            <Td><carlos:encode value='<%= allFields.getProperty("referralFlag1", "") %>' context="html"/>
            </td>
            <td>1</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Referal Practitioner</td><!--REF-PRACT-1-->
            <td><carlos:encode value='<%= allFields.getProperty("referralNo1", "") %>' context="html"/>
            </td>
            <td>5</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Referal Practitioner CD</td><!--REF-PRACT-2-CD-->
            <Td><carlos:encode value='<%= allFields.getProperty("referralFlag2", "") %>' context="html"/>
            </td>
            <td>1</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Referal Practitioner</td><!--REF-PRACT-2-->
            <td><carlos:encode value='<%= allFields.getProperty("referralNo2", "") %>' context="html"/>
            </td>
            <td>5</td>
            <td>Y</td>
        </tr>
        <tr>
            <td>Time Call Received</td><!--TIME-CALL-RECVD-SRV-->
            <td><carlos:encode value='<%= allFields.getProperty("timeCall", "") %>' context="html"/>
            </td>
            <td>4</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Service Time Start</td><!--SERVICE-TIME-START-->
            <td><carlos:encode value='<%= allFields.getProperty("serviceStartTime", "") %>' context="html"/>
            </td>
            <td>4</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Service Time Finish</td><!--SERVICE-TIME-FINISH-->
            <td><carlos:encode value='<%= allFields.getProperty("serviceEndTime", "") %>' context="html"/>
            </td>
            <td>4</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Birth Date</td><!--BIRTH-DATE-->
            <td><carlos:encode value='<%= allFields.getProperty("birthDate", "") %>' context="html"/>
            </td>
            <td>8</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Office Number</td><!--OFFICE-FOLIO-NUMBER-->
            <td><carlos:encode value='<%= allFields.getProperty("officeNumber", "") %>' context="html"/>
            </td>
            <td>7</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Correspondence Code</td><!--CORRESPONDENCE-CODE-->
            <td><carlos:encode value='<%= allFields.getProperty("correspondenceCode", "") %>' context="html"/>
            </td>
            <td>1</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Claim Short Comment</td><!--CLAIM-SHORT-COMMENT-->
            <td><carlos:encode value='<%= allFields.getProperty("claimComment", "") %>' context="html"/>
            </td>
            <td>20</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>MVA Claim Code</td><!--MVA-CLAIM-CODE-->
            <td><carlos:encode value='<%= allFields.getProperty("mvaClaimCode", "") %>' context="html"/>
            </td>
            <td>1</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>ICBC Claim Num</td><!--ICBC-CLAIM-NUM-->
            <td><carlos:encode value='<%= allFields.getProperty("icbcClaimNo", "") %>' context="html"/>
            </td>
            <td>8</td>
            <td>Y</td>
        </tr>
        <tr>
            <td>MSP File Num</td><!--ORG-MSP-FILE-NUM-->
            <td><carlos:encode value='<%= allFields.getProperty("originalClaim", "") %>' context="html"/>
            </td>
            <td>20</td>
            <td></td>
        </tr>
        <tr>
            <td>Facility Num</td><!--FACILITY-NUM-->facility_no
            <td><carlos:encode value='<%= allFields.getProperty("facilityNo", "") %>' context="html"/>
            </td>
            <td>5</td>
            <td>Y</td>
        </tr>
        <tr>
            <td>Facility Sub Num</td><!--FACILITY-SUB-NUM-->
            <td><carlos:encode value='<%= allFields.getProperty("facilitySubNo", "") %>' context="html"/>
            </td>
            <td>5</td>
            <td>Y</td>
        </tr>
        <tr>
            <td>Insurer Code</td><!--OIN-INSURER-C0DE-->
            <td><carlos:encode value='<%= allFields.getProperty("oinInsurerCode", "") %>' context="html"/>
            </td>
            <td>2</td>
            <td>Y</td>
        </tr>
        <tr>
            <td>Registration Num</td><!--OIN-REGISTRATION-NUM-->
            <td><carlos:encode value='<%= allFields.getProperty("oinRegistrationNo", "") %>' context="html"/>
            </td>
            <td>12</td>
            <td>Y</td>
        </tr>
        <tr>
            <td>Birth date</td><!--OIN-BIRTHDATE-->
            <td><carlos:encode value='<%= allFields.getProperty("oinBirthdate", "") %>' context="html"/>
            </td>
            <td>8</td>
            <td>Y</td>
        </tr>
        <tr>
            <td>First Name</td><!--OIN-FIRST-NAME-->
            <td><carlos:encode value='<%= allFields.getProperty("oinFirstName", "") %>' context="html"/>
            </td>
            <td>12</td>
            <td>Y</td>
        </tr>
        <tr>
            <td>Second Name</td><!--OIN-SECOND-NAME-INITIAL-->
            <td><carlos:encode value='<%= allFields.getProperty("oinSecondName", "") %>' context="html"/>
            </td>
            <td>1</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Surname</td><!--OIN-SURNAME-->
            <td><carlos:encode value='<%= allFields.getProperty("oinSurname", "") %>' context="html"/>
            </td>
            <td>18</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>SEX</td>
            <td><carlos:encode value='<%= allFields.getProperty("oinSexCode", "") %>' context="html"/>
            </td>
            <td>1</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Address 1 WCB Date Of Injury</td><!--OIN-ADDRESS-1		WCB DATE OF INJURY-->
            <td><carlos:encode value='<%= allFields.getProperty("oinAddress", "") %>' context="html"/>
            </td>
            <td>25</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Address 2 WCB AREA OF INJURY</td><!--OIN-ADDRESS-2 WCB AREA OF INJURY ANATOMICAL-POSITION-->
            <td><carlos:encode value='<%= allFields.getProperty("oinAddress2", "") %>' context="html"/>
            </td>
            <td>25</td>
            <td>Y</td>
        </tr>
        <tr>

            <Td>Address 3 WCB NATURE OF INJURY</td><!--OIN-ADDRESS-3 WCB NATURE OF INJURY-->
            <td><carlos:encode value='<%= allFields.getProperty("oinAddress3", "") %>' context="html"/>
            </td>
            <td>25</td>
            <td>Y</td>
        </tr>
        <tr>

            <td>Address 4 WCB Claim Number</td><!--OIN-ADDRESS-4 WCB CLAIM NUMBER-->
            <td><carlos:encode value='<%= allFields.getProperty("oinAddress4", "") %>' context="html"/>
            </td>
            <td>25</td>
            a
            <td>Y</td>
        </tr>
        <tr>

            <td>Postal Code</td><!--OIN-POSTAL-CODE-->
            <td><carlos:encode value='<%= allFields.getProperty("oinPostalcode", "") %>' context="html"/>
            </td>
            <td>6</td>
            <td>Y</td>
        </tr>

    </table>
</div>
<script language='javascript'>
    Calendar.setup({
        inputField: "serviceDate",
        ifFormat: "%Y%m%d",
        showsTime: false,
        button: "hlSDate",
        singleClick: true,
        step: 1
    });
    Calendar.setup({
        inputField: "debitRequestDate",
        ifFormat: "%Y%m%d",
        showsTime: false,
        button: "hlADate",
        singleClick: true,
        step: 1
    });
</script>
</body>
</html>
<%!
    public String getDebitRequestSeqNum(String str) {
        if (str == null || str.length() < 12) {
            return "";
        }
        String retval = str.substring(5, 12);
        return retval;
    }

    public String getDebitRequestDate(String str) {
        if (str == null || str.length() < 12) {
            return "";
        }
        String retval = str.substring(13);
        return retval;
    }


%>
