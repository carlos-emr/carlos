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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="java.util.*,java.net.*, java.sql.*, io.github.carlos_emr.*" %>
<%@ page import="io.github.carlos_emr.carlos.billing.ca.on.data.*" %>
<%-- Imports the downstream JSP body still uses (Layer1 ctlBillForm picker,
     Layer2 dx-code picker, provider/clinic dropdowns, payee provider
     lookup). The top-scriptlet DAO usage moved to
     BillingShortcutPg1DataAssembler; these stay because their data
     still flows through scriptlet loops further down the page. --%>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ClinicNbr" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DiagnosticCode" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.SxmlMisc" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingShortcutPg1ViewModel" %>
<%
    // View-model bridge: ViewBillingShortcutPg12Action runs the 14 DAO
    // lookups and demographic-driven validation in Java and exposes the
    // result as request attribute `shortcutPg1Model`. The JSP keeps the
    // legacy scriptlet-variable names below so the existing render-expression
    // sites keep compiling; a follow-up commit will replace those with EL on
    // the same model.
    BillingShortcutPg1ViewModel shortcutPg1Model =
            (BillingShortcutPg1ViewModel) request.getAttribute("shortcutPg1Model");
    if (shortcutPg1Model == null) {
        // Defensive fallback for any caller that forwards directly to this JSP
        // without going through ViewBillingShortcutPg12Action. Re-check the
        // privilege the action enforces so a future <jsp:forward> from an
        // unguarded JSP can't silently expose PHI assembly. Mirrors
        // billingON.jsp.
        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                "billingShortcutPg1.jsp reached without shortcutPg1Model — using empty fallback. "
                + "Caller should route through billing/CA/ON/billingShortcutPg1View.");
        io.github.carlos_emr.carlos.utility.LoggedInInfo __fallbackLii =
                io.github.carlos_emr.carlos.utility.LoggedInInfo.getLoggedInInfoFromSession(request);
        if (__fallbackLii == null) {
            throw new SecurityException("billingShortcutPg1.jsp fallback: missing session");
        }
        io.github.carlos_emr.carlos.managers.SecurityInfoManager __secMgr;
        try {
            __secMgr = io.github.carlos_emr.carlos.utility.SpringUtils
                    .getBean(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
        } catch (RuntimeException __springEx) {
            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error(
                    "billingShortcutPg1.jsp fallback: SecurityInfoManager bean lookup failed", __springEx);
            throw new SecurityException("billingShortcutPg1.jsp fallback: privilege check unavailable", __springEx);
        }
        if (!__secMgr.hasPrivilege(__fallbackLii, "_billing", "r", null)) {
            throw new SecurityException("billingShortcutPg1.jsp fallback: missing required sec object (_billing)");
        }
        shortcutPg1Model = BillingShortcutPg1ViewModel.builder().build();
        // Re-publish to request scope so EL bindings later in the page
        // (${shortcutPg1Model...}) resolve to the fallback instead of null.
        // The local scriptlet variable alone is invisible to EL.
        request.setAttribute("shortcutPg1Model", shortcutPg1Model);
    }

    String user_no = shortcutPg1Model.getUserProviderNo();
    String providerview = shortcutPg1Model.getProviderView();
    String asstProvider_no = "";
    String color = "";
    String premiumFlag = "";
    String service_form = "";

    String clinicview = shortcutPg1Model.getClinicView();
    String clinicNo = shortcutPg1Model.getClinicNo();
    String visitType = shortcutPg1Model.getVisitType();
    String appt_no = shortcutPg1Model.getApptNo();
    String demoname = shortcutPg1Model.getDemoName();
    String demo_no = shortcutPg1Model.getDemoNo();
    String apptProvider_no = shortcutPg1Model.getApptProviderNo();
    String ctlBillForm = shortcutPg1Model.getCtlBillForm();
    String assgProvider_no = shortcutPg1Model.getAssignedProviderNo();

    String demoSex = shortcutPg1Model.getDemoSex();
    GregorianCalendar nowCal = new GregorianCalendar();
    int curYear = nowCal.get(Calendar.YEAR);
    int curMonth = (nowCal.get(Calendar.MONTH) + 1);
    int curDay = nowCal.get(Calendar.DAY_OF_MONTH);
    int dob_year = 0, dob_month = 0, dob_date = 0, age = 0;

    ResourceBundle res = ResourceBundle.getBundle("oscarResources", request.getLocale());
    // The localized prefix lives in resources; the assembler accumulates the
    // demographic-driven error/warning portion.
    String msg = res.getString("billing.hospitalBilling.msgDates") + shortcutPg1Model.getMsg();
    String action = "edit";
    Properties propHist = null;
    Vector vecHist = shortcutPg1Model.getBillingHistoryVec();

    String proOHIPNO = "", proRMA = "";
    String errorFlag = shortcutPg1Model.getErrorFlag();
    String warningMsg = shortcutPg1Model.getWarningMessage();
    String errorMsg = shortcutPg1Model.getErrorMessage();
    String r_doctor = shortcutPg1Model.getReferralDoctorName();
    String r_doctor_ohip = shortcutPg1Model.getReferralDoctorOhip();
    String demoFirst = shortcutPg1Model.getDemoFirst();
    String demoLast = shortcutPg1Model.getDemoLast();
    String demoHIN = shortcutPg1Model.getDemoHin();
    String demoDOB = shortcutPg1Model.getDemoDob();
    String demoDOBYY = shortcutPg1Model.getDemoDobYy();
    String demoDOBMM = shortcutPg1Model.getDemoDobMm();
    String demoDOBDD = shortcutPg1Model.getDemoDobDd();
    String demoHCTYPE = shortcutPg1Model.getDemoHcType();

    Vector vecHistD = shortcutPg1Model.getBillingHistoryDetailsVec();
    Vector vecProvider = shortcutPg1Model.getProvidersVec();
    Vector vecLocation = shortcutPg1Model.getClinicLocationsVec();

    String dxCode = shortcutPg1Model.getDxCode();
    String visitdate = shortcutPg1Model.getVisitDate();

    Vector vecCodeCol1 = shortcutPg1Model.getServiceCodeCol1Vec();
    Vector vecCodeCol2 = shortcutPg1Model.getServiceCodeCol2Vec();
    Vector vecCodeCol3 = shortcutPg1Model.getServiceCodeCol3Vec();
    Properties propPremium = shortcutPg1Model.getPropPremiumProps();
    String serviceCode = "", serviceDesc = "", serviceValue = "", servicePercentage = "", serviceType = "", serviceDisp = "", serviceSLI = "";
    String headerTitle1 = shortcutPg1Model.getHeaderTitle1();
    String headerTitle2 = shortcutPg1Model.getHeaderTitle2();
    String headerTitle3 = shortcutPg1Model.getHeaderTitle3();

    // Reused as a scratch variable by downstream scriptlet loops.
    Properties propT = null;
    // Round-15: ProviderDao + ClinicNbrDao + CtlBillingServiceDao +
    // DiagnosticCodeDao no longer fetched in JSP body code. The assembler
    // pre-loads everything onto the view model:
    //   shortcutPg1Model.isRmaEnabled()                — replaces the property check
    //   shortcutPg1Model.getClinicNbrs()                — replaces ClinicNbrDao.findAll()
    //   shortcutPg1Model.getSelectedClinicNbrPrefix()   — replaces SxmlMisc.getXmlContent("xml_p_nbr") on provider comments
    //   shortcutPg1Model.getSelectedXmlPSli()           — replaces SxmlMisc.getXmlContent("xml_p_sli") on provider comments
    //   shortcutPg1Model.getServiceTypes()              — replaces CtlBillingServiceDao.findServiceTypesByStatus
    //   shortcutPg1Model.getDxCodes()                   — replaces DiagnosticCodeDao.findDiagnosictsAndCtlDiagCodesByServiceType
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html>
<head>
    <c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>HospitalBilling</title>
    <link rel="stylesheet" type="text/css" href="billingON.css"/>

    <!-- calendar stylesheet -->
    <link rel="stylesheet" type="text/css" media="all"
          href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>
    <!-- main calendar program -->
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
    <!-- language for the calendar -->
    <script type="text/javascript"
            src="${carlos:forHtmlAttribute(ctx)}/share/calendar/lang/<fmt:message key="global.javascript.calendar"/>"></script>
    <!-- the following script defines the Calendar.setup helper function, which makes
           adding a calendar a matter of 1 or 2 lines of code. -->
    <script type="text/javascript"
            src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
    <script type="text/javascript" language="JavaScript">

        <!--
        window.focus();

        function checkSli() {
            var needsSli = false;
            jQuery("input[name^=code_xml_]:checked").each(function () {
                needsSli = needsSli || eval(jQuery("input[name='sli_xml_" + this.name.substring(9) + "']").val());
            });
            jQuery("input[name^=serviceDate][value!='']").each(function () {
                needsSli = needsSli || eval(jQuery("input[name='sli_xml_" + this.value + "']").val());
            });
            return !needsSli || jQuery("select[name='xml_slicode']").get(0).selectedIndex != 0;
        }


        function gotoBillingOB() {
            if (self.location.href.lastIndexOf("?") > 0) {
                a = self.location.href.substring(self.location.href.lastIndexOf("?"));
            }
            self.location.href = "/billing" + a;
        }

        function findObj(n, d) { //v4.0
            var p, i, x;
            if (!d) d = document;
            if ((p = n.indexOf("?")) > 0 && parent.frames.length) {
                d = parent.frames[n.substring(p + 1)].document;
                n = n.substring(0, p);
            }
            if (!(x = d[n]) && d.all) x = d.all[n];
            for (i = 0; !x && i < d.forms.length; i++) x = d.forms[i][n];
            for (i = 0; !x && d.layers && i < d.layers.length; i++) x = findObj(n, d.layers[i].document);
            if (!x && document.getElementById) x = document.getElementById(n);
            return x;
        }

        function showHideLayers() { //v3.0
            var i, p, v, obj, args = showHideLayers.arguments;
            for (i = 0; i < (args.length - 2); i += 3) if ((obj = findObj(args[i])) != null) {
                v = args[i + 2];
                if (obj.style) {
                    obj = obj.style;
                    v = (v == 'show') ? 'visible' : (v = 'hide') ? 'hidden' : v;
                }
                obj.visibility = v;
            }
        }

        function onNext() {
            //document.forms[0].submit.value="save";
            var ret = checkAllDates();
            if (!checkSli()) {
                alert("You have selected billing codes that require an SLI code but have not provided an SLI code.");
                return false;
            }
            return ret;
        }

        function checkAllDates() {
            document.forms[0].serviceDate0.value = document.forms[0].serviceDate0.value.toUpperCase();
            document.forms[0].serviceDate1.value = document.forms[0].serviceDate1.value.toUpperCase();
            document.forms[0].serviceDate2.value = document.forms[0].serviceDate2.value.toUpperCase();
            if (document.forms[0].billDate.value.length < 1) {
                alert("No billing date!");
                return false;
            } else if (!isChecked("code_xml_") && document.forms[0].serviceDate0.value.length != 5 || !isServiceCode(document.forms[0].serviceDate0.value)) {
                alert("Need service code!");
                return false;
            } else if (document.forms[0].serviceDate1.value.length > 0 && document.forms[0].serviceDate1.value.length != 5 || !isServiceCode(document.forms[0].serviceDate1.value)) {
                alert("Wrong service code 2!");
                return false;
            } else if (document.forms[0].serviceDate2.value.length > 0 && document.forms[0].serviceDate2.value.length != 5 || !isServiceCode(document.forms[0].serviceDate2.value)) {
                alert("Wrong service code 3!");
                return false;
            } else if (document.forms[0].serviceDate3.value.length > 0 && document.forms[0].serviceDate3.value.length != 5 || !isServiceCode(document.forms[0].serviceDate3.value)) {
                alert("Wrong service code 4!");
                return false;
            } else if (document.forms[0].serviceDate4.value.length > 0 && document.forms[0].serviceDate4.value.length != 5 || !isServiceCode(document.forms[0].serviceDate4.value)) {
                alert("Wrong service code 5!");
                return false;
            } else if (document.forms[0].dxCode.value.length != 3) {
                alert("Wrong dx code!");
                return false;
                //} else if(document.forms[0].xml_provider.options[0].selected){
            } else if (document.forms[0].xml_provider.value == "000000") {
                alert("Please select a providers.");
                return false;
            }
                <% if (!CarlosProperties.getInstance().getBooleanProperty("rma_enabled", "true")) { %>
            else if (document.forms[0].xml_visittype.options[2].selected && (document.forms[0].xml_vdate.value == "" || document.forms[0].xml_vdate.value == "0000-00-00")) {
                alert("Need an admission date.");
                return false;
            }
            <% } %>

            if (document.forms[0].xml_vdate.value.length > 0) {
                return checkServiceDate(document.forms[0].xml_vdate.value);
            }
            if (document.forms[0].billDate.value.length > 0) {
                var billDateA = document.forms[0].billDate.value.split("\n");
                for (var i in billDateA) {
                    var v = billDateA[i];
                    if (v) {
                        //alert(" !" + v);
                        return checkServiceDate(v);
                    }
                }
            }

            if (!isInteger(document.forms[0].dxCode.value)) {
                alert("Wrong dx code!");
                return false;
            }
            if (document.forms[0].referralCode.value.length > 0) {
                if (document.forms[0].referralCode.value.length != 6 || !isInteger(document.forms[0].referralCode.value)) {
                    alert("Wrong referral code!");
                    return false;
                }
            }

            return true;
        }

        function checkServiceDate(s) {
            var calDate = new Date();
            varYear = calDate.getFullYear();
            varMonth = calDate.getMonth() + 1;
            varDate = calDate.getDate();
            var str_date = s; //document.forms[0].xml_appointment_date.value;
            var yyyy = str_date.substring(0, str_date.indexOf("-"));
            var mm = str_date.substring(eval(str_date.indexOf("-") + 1), str_date.lastIndexOf("-"));
            var dd = str_date.substring(eval(str_date.lastIndexOf("-") + 1));
            var bWrongDate = false;
            sMsg = "";
            if (yyyy > varYear) {
                sMsg = "year";
                bWrongDate = true;
            } else if (yyyy == varYear && mm > varMonth) {
                sMsg = "month";
                bWrongDate = true;
            } else if (yyyy == varYear && mm == varMonth && dd > varDate) {
                sMsg = "date";
                bWrongDate = true;
            }
            if (bWrongDate) {
                alert("Warning - Service/Admission Date is future dated!");
                return false;
            } else {
                return true;
            }
        }

        function isInteger(s) {
            var i;
            for (i = 0; i < s.length; i++) {
                // Check that current character is number.
                var c = s.charAt(i);
                if (((c < "0") || (c > "9"))) return false;
            }
            // All characters are numbers.
            return true;
        }

        function isServiceCode(s) {
            // temp for 0.
            if (s.length == 0) return true;
            if (s.length != 5) return false;
            if ((s.charAt(0) < "A") || (s.charAt(0) > "Z")) return false;
            if ((s.charAt(4) < "A") || (s.charAt(4) > "Z")) return false;

            var i;
            for (i = 1; i < s.length - 1; i++) {
                // Check that current character is number.
                var c = s.charAt(i);
                if (((c < "0") || (c > "9"))) return false;
            }
            return true;
        }

        function isChecked(s) {
            for (var i = 0; i < document.forms[0].elements.length; i++) {
                if (document.forms[0].elements[i].name.indexOf(s) == 0 && document.forms[0].elements[i].name.length == 14) {
                    if (document.forms[0].elements[i].checked) {
                        return true;
                    }
                }
            }
            return false;
        }

        var remote = null;

        function rs(n, u, w, h, x) {
            args = "width=" + w + ",height=" + h + ",resizable=yes,scrollbars=yes,status=0,top=60,left=30";
            remote = window.open(u, n, args);
            //if (remote != null) {
            //  if (remote.opener == null)
            //    remote.opener = self;
            //}
            //if (x == 1) { return remote; }
        }

        var awnd = null;

        function referralScriptAttach(elementName) {
            var d = elementName;
            t0 = escape("document.forms[0].elements[\'" + d + "\'].value");
            //t1 = escape("");
            //alert(('<%= request.getContextPath() %>/billing/CA/ON/ViewSearchRefDoc?param='+t0));
            awnd = rs('att', ('<%= request.getContextPath() %>/billing/CA/ON/ViewSearchRefDoc?param=' + t0), 600, 600, 1);
            awnd.focus();
        }

        function referralScriptAttach2(elementName, name2) {
            var d = elementName;
            t0 = escape("document.forms[0].elements[\'" + d + "\'].value");
            t1 = escape("document.forms[0].elements[\'" + name2 + "\'].value");
            awnd = rs('att', ('<%= request.getContextPath() %>/billing/CA/ON/ViewSearchRefDoc?param=' + t0 + '&param2=' + t1), 600, 600, 1);
            awnd.focus();
        }

        function dxScriptAttach(name2) {
            f0 = escape(document.forms[0].dxCode.value);
            f1 = escape("document.forms[0].elements[\'" + name2 + "\'].value");
            awnd = rs('att', '<%= request.getContextPath() %>/billing/CA/ON/ViewBillingDigSearch?name=' + f0 + '&search=&name2=' + f1, 600, 600, 1);
            awnd.focus();
        }

        function onDblClickServiceCode(item) {
            //alert(item.id);
            if (document.forms[0].serviceDate0.value == "") {
                document.forms[0].serviceDate0.value = item.id.substring(3);
            } else if (document.forms[0].serviceDate1.value == "") {
                document.forms[0].serviceDate1.value = item.id.substring(3);
            } else if (document.forms[0].serviceDate2.value == "") {
                document.forms[0].serviceDate2.value = item.id.substring(3);
            }
        }

        //-->

    </script>
</head>

<body onload="setfocus();" topmargin="0">
<div id="Layer1"
     style="position: absolute; left: 360px; top: 165px; width: 410px; height: 200px; z-index: 1; background-color: #FFCC00; layer-background-color: #FFCC00; border: 1px none #000000; visibility: hidden">
    <table width="98%" border="0" cellspacing="0" cellpadding="0"
           align=center>
        <tr bgcolor="#393764">
            <td width="96%" height="7" bgcolor="#FFCC00"><font size="-2"
                                                               face="Geneva, Arial, Helvetica, san-serif"
                                                               color="#000000"><b><fmt:message key="billing.billingform"/>
            </b></font></td>
            <td width="3%" bgcolor="#FFCC00" height="7"><b><a href="#"
                                                              onClick="showHideLayers('Layer1','','hide');return false;">X</a></b>
            </td>
        </tr>

        <%-- Round-15: service-type panel now iterates the pre-loaded
             shortcutPg1Model.serviceTypes list. The assembler does the
             findServiceTypesByStatus call + sanitisation; the JSP just
             walks the result. --%>
        <%
            String ctlcode, ctlcodename, currentFormName = "";
            int ctlCount = 0;

            for (io.github.carlos_emr.carlos.billings.ca.on.data.BillingShortcutPg1ViewModel.ServiceTypeEntry __st : shortcutPg1Model.getServiceTypes()) {
                ctlcode = __st.code();
                ctlcodename = __st.name();
                ctlCount++;
                if (ctlcode.equals(ctlBillForm)) {
                    currentFormName = ctlcodename;
                }
        %>
        <tr bgcolor=<%=ctlCount % 2 == 0 ? "#FFFFFF" : "#EEEEFF"%>>
            <td colspan="2"><b><font size="-2" color="#7A388D"><a
                    href="${pageContext.request.contextPath}/billing/CA/ON/billingShortcutPg1View?billForm=<carlos:encode value='<%=ctlcode%>' context="uriComponent"/>&hotclick=<%=URLEncoder.encode("","UTF-8")%>&appointment_no=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("appointment_no")) %>' context="uriComponent"/>&demographic_name=<%=URLEncoder.encode(demoname,"UTF-8")%>&demographic_no=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("demographic_no")) %>' context="uriComponent"/>&user_no=<carlos:encode value='<%=user_no%>' context="uriComponent"/>&apptProvider_no=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("apptProvider_no")) %>' context="uriComponent"/>&providerview=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(providerview) %>' context="uriComponent"/>&appointment_date=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("appointment_date")) %>' context="uriComponent"/>&status=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("status")) %>' context="uriComponent"/>&start_time=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("start_time")) %>' context="uriComponent"/>&bNewForm=1"
                    onClick="showHideLayers('Layer1','','hide');"><carlos:encode value='<%=ctlcodename%>' context="html"/>
            </a></font></b></td>
        </tr>
        <%
            }
        %>
    </table>
</div>
<div id="Layer2"
     style="position: absolute; left: 1px; top: 26px; width: 332px; height: 600px; z-index: 2; background-color: #FFCC00; layer-background-color: #FFCC00; border: 1px none #000000; visibility: hidden">
    <table width="98%" border="0" cellspacing="0" cellpadding="0"
           align=center>
        <tr>
            <td width="18%"><b><font size="-2"><fmt:message key="billing.hospitalBilling.formDxCode"/></font></b></td>
            <td width="76%"><b><font size="-2"><fmt:message key="billing.billingCorrection.formDescription"/></font></b></td>
            <td width="6%"><a href="#"
                              onClick="showHideLayers('Layer2','','hide');return false">X</a></td>
        </tr>

        <%-- Round-15: dx-code panel now iterates pre-loaded
             shortcutPg1Model.dxCodes — assembler does the
             DiagnosticCodeDao.findDiagnosictsAndCtlDiagCodesByServiceType
             call. --%>
        <%
            String ctldiagcode, ctldiagcodename;
            ctlCount = 0;
            for (io.github.carlos_emr.carlos.billings.ca.on.data.BillingShortcutPg1ViewModel.DxCodeEntry __dx : shortcutPg1Model.getDxCodes()) {
                ctldiagcode = __dx.code();
                ctldiagcodename = __dx.description();
        %>
        <tr bgcolor=<%=ctlCount % 2 == 0 ? "#FFFFFF" : "#EEEEFF"%>>
            <td width="18%"><b><font size="-2" color="#7A388D"><a
                    href="#"
                    onClick="document.forms[0].dxCode.value='<%=ctldiagcode%>';showHideLayers('Layer2','','hide');return false;"><%=ctldiagcode%>
            </a></font></b></td>
            <td colspan="2"><font size="-2" color="#7A388D"><a
                    href="#"
                    onClick="document.forms[0].dxCode.value='<%=ctldiagcode%>';showHideLayers('Layer2','','hide');return false;">
                <%=ctldiagcodename.length() < 56 ? ctldiagcodename : ctldiagcodename.substring(0, 55)%>
            </a></font></td>
        </tr>
        <%
            }
        %>
    </table>
</div>


<form method="post" name="titlesearch" action="<%= request.getContextPath() %>/billing/CA/ON/BillingShortcutPg2Save"
      onsubmit="return onNext();">
    <table border="0" cellpadding="0" cellspacing="2" width="100%"
           bgcolor="#CCCCFF">
        <tr>
            <td>
                <table border="0" cellspacing="0" cellpadding="0" width="100%">
                    <tr>
                        <td><b><fmt:message key="billing.hospitalBilling.formOscarBilling"/> </b></td>
                        <td align="right"><input type="submit" name="submit"
                                                 value="<fmt:message key="billing.hospitalBilling.btnNext"/>"
                                                 style="width: 120px;"/> <input type="button"
                                                                                name="button"
                                                                                value="<fmt:message key="global.btnExit"/>"
                                                                                style="width: 120px;"
                                                                                onClick="self.close();"/></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td>
                <table border="0" cellspacing="0" cellpadding="0" width="100%">
                    <tr bgcolor="#33CCCC">
                        <td nowrap bgcolor="#FFCC99" width="10%" align="center">${carlos:forHtmlContent(shortcutPg1Model.demoName)}
                        </td>
                        <td bgcolor="#99CCCC" align="center"><font color="black"><%= msg %>
                        </font>
                        </td>
                    </tr>
                </table>

                <table border="1" cellspacing="0" cellpadding="0" width="100%"
                       bordercolorlight="#99A005" bordercolordark="#FFFFFF"
                       bgcolor="#FFFFFF">
                    <tr>
                        <td width="50%">

                            <table border="1" cellspacing="2" cellpadding="0" width="100%"
                                   bordercolorlight="#99A005" bordercolordark="#FFFFFF"
                                   bgcolor="ivory">
                                <tr>
                                    <td nowrap width="30%" align="center"><a id="trigger"
                                                                             href="#">[<fmt:message key="billing.servicedate"/>]</a><br>
                                        <textarea name="billDate" cols="11" rows="5"
                                                  readonly><carlos:encode value='<%= request.getParameter("billDate") != null ? request.getParameter("billDate") : "" %>' context="html"/></textarea>
                                    </td>
                                    <td nowrap align="center"><fmt:message key="billing.billingCorrection.formServiceCode"/> x <fmt:message key="billing.billingCorrection.formUnit"/><br>
                                        <input type="text" name="serviceDate0" size="5" maxlength="5"
                                               value="<carlos:encode value='<%= request.getParameter("serviceDate0")!=null?request.getParameter("serviceDate0"):"" %>' context="htmlAttribute"/>">x
                                        <input type="text" name="serviceUnit0" size="2" maxlength="2"
                                               style=""
                                               value="<carlos:encode value='<%= request.getParameter("serviceUnit0")!=null?request.getParameter("serviceUnit0"):"" %>' context="htmlAttribute"/>"><br>
                                        <input type="text" name="serviceDate1" size="5" maxlength="5"
                                               value="<carlos:encode value='<%= request.getParameter("serviceDate1")!=null?request.getParameter("serviceDate1"):"" %>' context="htmlAttribute"/>">x
                                        <input type="text" name="serviceUnit1" size="2" maxlength="2"
                                               style=""
                                               value="<carlos:encode value='<%= request.getParameter("serviceUnit1")!=null?request.getParameter("serviceUnit1"):"" %>' context="htmlAttribute"/>"><br>
                                        <input type="text" name="serviceDate2" size="5" maxlength="5"
                                               value="<carlos:encode value='<%= request.getParameter("serviceDate2")!=null?request.getParameter("serviceDate2"):"" %>' context="htmlAttribute"/>">x
                                        <input type="text" name="serviceUnit2" size="2" maxlength="2"
                                               style=""
                                               value="<carlos:encode value='<%= request.getParameter("serviceUnit2")!=null?request.getParameter("serviceUnit2"):"" %>' context="htmlAttribute"/>"><br>
                                        <input type="text" name="serviceDate3" size="5" maxlength="5"
                                               value="<carlos:encode value='<%= request.getParameter("serviceDate3")!=null?request.getParameter("serviceDate3"):"" %>' context="htmlAttribute"/>">x
                                        <input type="text" name="serviceUnit3" size="2" maxlength="2"
                                               style=""
                                               value="<carlos:encode value='<%= request.getParameter("serviceUnit3")!=null?request.getParameter("serviceUnit3"):"" %>' context="htmlAttribute"/>"><br>
                                        <input type="text" name="serviceDate4" size="5" maxlength="5"
                                               value="<carlos:encode value='<%= request.getParameter("serviceDate4")!=null?request.getParameter("serviceDate4"):"" %>' context="htmlAttribute"/>">x
                                        <input type="text" name="serviceUnit4" size="2" maxlength="2"
                                               style=""
                                               value="<carlos:encode value='<%= request.getParameter("serviceUnit4")!=null?request.getParameter("serviceUnit4"):"" %>' context="htmlAttribute"/>">
                                    </td>
                                    <td valign="top">
                                        <table border="0" cellspacing="0" cellpadding="0" width="100%">
                                            <tr>
                                                <td><a href="#"
                                                       onClick="showHideLayers('Layer2','','show','Layer1','','hide'); return false;"><fmt:message key="billing.hospitalBilling.formDx"/></a><br>
                                                    <input type="text" name="dxCode" size="5" maxlength="5"
                                                           onDblClick="dxScriptAttach('dxCode')"
                                                           value="<carlos:encode value='<%= request.getParameter("dxCode")!=null?request.getParameter("dxCode"):dxCode %>' context="htmlAttribute"/>">
                                                </td>
                                                <td>Cal.% mode<br>
                                                    <select name="rulePerc">
                                                        <% String rulePerc = request.getParameter("rulePerc") != null ? request.getParameter("rulePerc") : ""; %>
                                                        <option value="onlyAboveCode"
                                                                <%="onlyAboveCode".equals(rulePerc) ? "selected" : ""%>>
                                                            <fmt:message key="billing.hospitalBilling.optAbove"/>
                                                        </option>
                                                        <option value="allAboveCode"
                                                                <%="allAboveCode".equals(rulePerc) ? "selected" : ""%>>
                                                            <fmt:message key="billing.hospitalBilling.optAll"/></option>
                                                    </select></td>
                                            </tr>
                                        </table>

                                        <hr>
                                        <a
                                                href="javascript:referralScriptAttach2('referralCode','referralDocName')"><fmt:message key="billing.hospitalBilling.btnReferral"/>
                                        </a> <input type="text" name="referralCode" size="5"
                                                    maxlength="6"
                                                    value="<carlos:encode value='<%= request.getParameter("referralCode")!=null?request.getParameter("referralCode"):r_doctor_ohip %>' context="htmlAttribute"/>"><br>
                                        <input type="text" name="referralDocName" size="22" maxlength="30"
                                               value="<carlos:encode value='<%= request.getParameter("referralDocName")!=null?request.getParameter("referralDocName"):r_doctor %>' context="htmlAttribute"/>">
                                    </td>
                                </tr>
                            </table>

                        </td>
                        <td valign="top">

                            <table border="1" cellspacing="2" cellpadding="0" width="100%"
                                   bordercolorlight="#99A005" bordercolordark="#FFFFFF"
                                   bgcolor="#EEEEFF">
                                <tr>
                                    <td nowrap width="30%" align="center"><b><fmt:message key="billing.hospitalBilling.frmBillPhysician"/>
                                    </b></td>
                                    <td width="20%"><select name="xml_provider">
                                        <%
                                            if (vecProvider.size() == 1) {
                                                propT = (Properties) vecProvider.get(0);
                                        %>
                                        <option value="<%=propT.getProperty("proOHIP")%>"
                                                <%=providerview.equals(propT.getProperty("proOHIP")) ? "selected" : ""%>>
                                            <b><%=propT.getProperty("last_name")%>,
                                                <%=propT.getProperty("first_name")%>
                                            </b></option>
                                        <% } else { %>
                                        <option value="000000"
                                                <%=providerview.equals("000000") ? "selected" : ""%>><b><fmt:message key="billing.billingCorrection.msgSelectProvider"/>
                                        </b></option>
                                        <%
                                            for (int i = 0; i < vecProvider.size(); i++) {
                                                propT = (Properties) vecProvider.get(i);
                                        %>
                                        <option value="<%=propT.getProperty("proOHIP")%>"
                                                <%=providerview.equals(propT.getProperty("proOHIP")) ? "selected" : ""%>>
                                            <b><%=propT.getProperty("last_name")%>,
                                                <%=propT.getProperty("first_name")%>
                                            </b></option>
                                        <% }
                                        }
                                        %>
                                    </select></td>
                                    <td nowrap width="30%" align="center"><b><fmt:message key="billing.hospitalBilling.frmAssgnPhysician"/></b></td>
                                    <td width="20%"><carlos:encode value='<%= providerBean.getProperty(assgProvider_no, "") %>' context="html"/>
                                    </td>
                                </tr>
                                <tr>

                                    <td width="30%">
                                        <b><%if (CarlosProperties.getInstance().getBooleanProperty("rma_enabled", "true")) { %>
                                            Clinic Nbr <% } else { %> <fmt:message key="billing.billingCorrection.formVisitType"/> <% } %></b></td>
                                    <td width="20%"><select name="xml_visittype">
                                        <%-- Round-15: clinic-nbr dropdown driven by pre-loaded
                                             shortcutPg1Model.clinicNbrs (replaces ClinicNbrDao.findAll +
                                             ProviderDao.getProvider lookup that ran inline). The
                                             auto-select uses shortcutPg1Model.selectedClinicNbrPrefix
                                             from the user provider's comments XML. --%>
                                        <% if (shortcutPg1Model.isRmaEnabled()) { %>
                                        <%
                                            String __providerNbr = shortcutPg1Model.getSelectedClinicNbrPrefix();
                                            for (io.github.carlos_emr.carlos.billings.ca.on.data.BillingShortcutPg1ViewModel.ClinicNbrEntry __c : shortcutPg1Model.getClinicNbrs()) {
                                                String valueString = __c.displayLabel();
                                        %>
                                        <option value="<%=valueString%>" <%=__providerNbr.startsWith(__c.nbrValue()) ? "selected" : ""%>><%=valueString%>
                                        </option>
                                        <%}%>
                                        <% } else { %>
                                        <option value="00| Clinic Visit"
                                                <%=visitType.startsWith("00") ? "selected" : ""%>><fmt:message key="billing.billingCorrection.formClinicVisit"/>
                                        </option>
                                        <option value="01| Outpatient Visit"
                                                <%=visitType.startsWith("01") ? "selected" : ""%>><fmt:message key="billing.billingCorrection.formOutpatientVisit"/>
                                        </option>
                                        <option value="02| Hospital Visit"
                                                <%=visitType.startsWith("02") ? "selected" : ""%>><fmt:message key="billing.billingCorrection.formHospitalVisit"/>
                                        </option>
                                        <option value="03| ER"
                                                <%=visitType.startsWith("03") ? "selected" : ""%>><fmt:message key="billing.billingCorrection.formER"/></option>
                                        <option value="04| Nursing Home"
                                                <%=visitType.startsWith("04") ? "selected" : ""%>><fmt:message key="billing.billingCorrection.formNursingHome"/>
                                        </option>
                                        <option value="05| Home Visit"
                                                <%=visitType.startsWith("05") ? "selected" : ""%>><fmt:message key="billing.billingCorrection.formHomeVisit"/>
                                        </option>
                                        <% } %>
                                    </select></td>

                                    <td width="30%"><b>Billing Type</b></td>
                                    <td width="20%">
                                        <% String srtBillType = request.getParameter("xml_billtype") != null ? request.getParameter("xml_billtype") : ""; %>
                                        <select name="xml_billtype">
                                            <option value="ODP | Bill OHIP"
                                                    <%=srtBillType.startsWith("ODP") ? "selected" : ""%>><fmt:message key="billing.billingCorrection.formBillTypeO"/>
                                            </option>
                                            <option value="PAT | Bill Patient"
                                                    <%=srtBillType.startsWith("PAT") ? "selected" : ""%>><fmt:message key="billing.billingCorrection.formBillTypeP"/>
                                            </option>
                                            <option value="WCB | Worker's Compensation Board"
                                                    <%=srtBillType.startsWith("WCB") ? "selected" : ""%>><fmt:message key="billing.billingCorrection.formBillTypeW"/></option>
                                        </select></td>
                                </tr>
                                <tr>
                                    <td><b><fmt:message key="billing.billingCorrection.msgVisitLocation"/></b></td>
                                    <td colspan="3"><select name="xml_location">
                                        <%
                                            for (int i = 0; i < vecLocation.size(); i++) {
                                                propT = (Properties) vecLocation.get(i);
                                                String strLocation = request.getParameter("xml_location") != null ? request.getParameter("xml_location") : clinicview;
                                        %>
                                        <option
                                                value="<%=propT.getProperty("clinic_location_no") + "|" + propT.getProperty("clinic_location_name")%>"
                                                <%=strLocation.startsWith(propT.getProperty("clinic_location_no")) ? "selected" : ""%>>
                                            <%=propT.getProperty("clinic_location_name")%>
                                        </option>
                                        <%
                                            }
                                        %>
                                    </select></td>
                                </tr>
                                <%--
                                    Round-15: SLI-code dropdown auto-select reads from
                                    shortcutPg1Model.selectedXmlPSli (assembler pre-loaded
                                    the user-provider's xml_p_sli comments-XML field). The
                                    legacy `pr != null` guard is now subsumed by the
                                    assembler's null-safety on the lookup — empty string
                                    means no auto-select, every option renders unselected.
                                --%>
                                <%
                                    String prSli = shortcutPg1Model.getSelectedXmlPSli();
                                %>
                                <tr>
                                    <td><b><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode"/></b></td>
                                    <td colspan="3">
                                        <select name="xml_slicode">

                                            <option value="<%=clinicNo%>"><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.NA"/></option>

                                            <%if (prSli.equals("HDS")) {%>
                                            <option selected value="HDS "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HDS"/></option>
                                            <%} else { %>
                                            <option value="HDS "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HDS"/></option>
                                            <%}%>

                                            <%if (prSli.equals("HED")) {%>
                                            <option selected value="HED "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HED"/></option>
                                            <%} else { %>
                                            <option value="HED "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HED"/></option>
                                            <%}%>

                                            <%if (prSli.equals("HIP")) {%>
                                            <option selected value="HIP "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HIP"/></option>
                                            <%} else { %>
                                            <option value="HIP "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HIP"/></option>
                                            <%}%>

                                            <%if (prSli.equals("HOP")) {%>
                                            <option selected value="HOP "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HOP"/></option>
                                            <%} else { %>
                                            <option value="HOP "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HOP"/></option>
                                            <%}%>

                                            <%if (prSli.equals("HRP")) {%>
                                            <option selected value="HRP "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HRP"/></option>
                                            <%} else { %>
                                            <option value="HRP "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HRP"/></option>
                                            <%}%>

                                            <%if (prSli.equals("IHF")) {%>
                                            <option selected value="IHF "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.IHF"/></option>
                                            <%} else { %>
                                            <option value="IHF "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.IHF"/></option>
                                            <%}%>

                                            <%if (prSli.equals("OFF")) {%>
                                            <option selected value="OFF "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.OFF"/></option>
                                            <%} else { %>
                                            <option value="OFF "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.OFF"/></option>
                                            <%}%>

                                            <%if (prSli.equals("OTN")) {%>
                                            <option selected value="OTN "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.OTN"/></option>
                                            <%} else { %>
                                            <option value="OTN "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.OTN"/></option>
                                            <%}%>

                                            <%if (prSli.equals("PDF")) {%>
                                            <option selected value="PDF "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.PDF"/></option>
                                            <%} else { %>
                                            <option value="PDF "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.PDF"/></option>
                                            <%}%>

                                            <%if (prSli.equals("RTF")) {%>
                                            <option selected value="RTF "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.RTF"/></option>
                                            <%} else { %>
                                            <option value="RTF "><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.RTF"/></option>
                                            <%}%>
                                        </select>
                                    </td>
                                </tr>
                                <%-- Round-15: removed redundant `else` branch. The auto-select
                                     loop above produces no `selected` attributes when prSli is
                                     empty (the empty-string default when no user-provider is
                                     resolved), so it renders identically to the legacy "else"
                                     fallback that omitted them entirely. --%>
                                <tr>
                                    <td><b><fmt:message key="billing.admissiondate"/></b></td>
                                    <td>
                                        <%
                                            String admDate = "";
                                            if (visitType.startsWith("02") || visitType.startsWith("04")) {
                                                admDate = visitdate;
                                            } %>
                                        <!--input type="text" name="xml_vdate" id="xml_vdate" value="<%--=request.getParameter("xml_vdate")!=null? request.getParameter("xml_vdate"):visitdate--%>" size='10' maxlength='10' -->
                                        <input type="text" name="xml_vdate" id="xml_vdate"
                                               value="<carlos:encode value='<%= request.getParameter("xml_vdate")!=null? request.getParameter("xml_vdate"):admDate %>' context="htmlAttribute"/>"
                                               size='10' maxlength='10'> <img
                                            src="<%= request.getContextPath() %>/images/cal.gif" id="xml_vdate_cal"></td>
                                    <td colspan="2"><a href="#"
                                                       onClick="showHideLayers('Layer1','','show');return false;"><fmt:message key="billing.billingform"/>
                                    </a>:</font></b> <%=currentFormName.length() < 30 ? currentFormName : currentFormName.substring(0, 30)%>
                                    </td>

                                </tr>
                            </table>


                        </td>
                    </tr>
                </table>

            </td>
        </tr>
        <tr>
            <td>


                <table width="100%" border="0" cellspacing="0" cellpadding="0"
                       height="137">
                    <tr>
                        <td valign="top" width="33%">

                            <table width="100%" border="1" cellspacing="0" cellpadding="0"
                                   height="0" bordercolorlight="#99A005" bordercolordark="#FFFFFF">
                                <tr bgcolor="#CCCCFF">
                                    <th width="10%" nowrap><font size="-1" color="#000000"><%=headerTitle1%>
                                    </font></th>
                                    <th width="70%" bgcolor="#CCCCFF"><font size="-1"
                                                                            color="#000000">Description</font></th>
                                    <th><font size="-1" color="#000000"> Fee</font></th>
                                </tr>
                                <%
                                    for (int i = 0; i < vecCodeCol1.size(); i++) {
                                        propT = (Properties) vecCodeCol1.get(i);
                                        serviceCode = propT.getProperty("serviceCode");
                                        serviceDesc = propT.getProperty("serviceDesc");
                                        serviceDisp = propT.getProperty("serviceDisp");
                                        servicePercentage = propT.getProperty("servicePercentage");
                                        serviceSLI = propT.getProperty("serviceSLI");
                                        if (propPremium.getProperty(serviceCode) != null) premiumFlag = "A";
                                        else premiumFlag = "";
                                %>
                                <tr bgcolor=<%=i % 2 == 0 ? "#FFFFFF" : "#EEEEFF"%>>
                                    <td nowrap><input type="checkbox"
                                                      name="code_xml_<%=serviceCode%>" value="checked"
                                        <%="checked".equals(request.getParameter("code_xml_"+serviceCode))? "checked":""%>>
                                        <b><font size="-1"
                                                 color="<%=premiumFlag.equals("A")? "#993333" : "black"%>"><span
                                                id="sc<%=(""+i).substring(0,1)+serviceCode%>"
                                                onDblClick="onDblClickServiceCode(this)"><%=serviceCode%></span></font></b>
                                        <input type="text" name="unit_xml_<%=serviceCode%>"
                                               value="<carlos:encode value='<%= request.getParameter("unit_xml_"+serviceCode)!=null? request.getParameter("unit_xml_"+serviceCode):"" %>' context="htmlAttribute"/>"
                                               size="1" maxlength="2" style="width: 20px; height: 12px;"></td>
                                    <td <%=serviceDesc.length() > 30 ? "title=\"" + serviceDesc + "\"" : ""%>><font
                                            size="-1"><%=serviceDesc.length() > 30 ? serviceDesc.substring(0, 30) + "..." : serviceDesc%>
                                        <input type="hidden" name="desc_xml_<%=serviceCode%>"
                                               value="<%=serviceDesc%>"/>
                                        <input type="hidden" name="sli_xml_<%=serviceCode%>" value="<%=serviceSLI%>"/>
                                    </font></td>
                                    <td align="right"><font size="-1"><%=serviceDisp%>
                                    </font> <input
                                            type="hidden" name="price_xml_<%=serviceCode%>"
                                            value="<%=serviceDisp%>"/> <input type="hidden"
                                                                              name="perc_xml_<%=serviceCode%>"
                                                                              value="<%=servicePercentage%>"/>
                                    </td>
                                </tr>
                                <% } %>
                            </table>

                        </td>
                        <td width="33%" valign="top">

                            <table width="100%" border="1" cellspacing="0" cellpadding="0"
                                   height="0" bordercolorlight="#99A005" bordercolordark="#FFFFFF">
                                <tr bgcolor="#CCCCFF">
                                    <th width="10%" nowrap><font size="-1" color="#000000"><%=headerTitle2%>
                                    </font></th>
                                    <th width="70%" bgcolor="#CCCCFF"><font size="-1"
                                                                            color="#000000">Description</font></th>
                                    <th><font size="-1" color="#000000"> Fee</font></th>
                                </tr>
                                <%
                                    for (int i = 0; i < vecCodeCol2.size(); i++) {
                                        propT = (Properties) vecCodeCol2.get(i);
                                        serviceCode = propT.getProperty("serviceCode");
                                        serviceDesc = propT.getProperty("serviceDesc");
                                        serviceDisp = propT.getProperty("serviceDisp");
                                        servicePercentage = propT.getProperty("servicePercentage");
                                        serviceSLI = propT.getProperty("serviceSLI");
                                        if (propPremium.getProperty(serviceCode) != null) premiumFlag = "A";
                                        else premiumFlag = "";
                                %>
                                <tr bgcolor=<%=i % 2 == 0 ? "#FFFFFF" : "#EEEEFF"%>>
                                    <td nowrap><input type="checkbox"
                                                      name="code_xml_<%=serviceCode%>" value="checked"
                                            <%="checked".equals(request.getParameter("code_xml_" + serviceCode)) ? "checked" : ""%> />
                                        <b><font size="-1"
                                                 color="<%=premiumFlag.equals("A")? "#993333" : "black"%>"><span
                                                id="sc<%=(""+i).substring(0,1)+serviceCode%>"
                                                onDblClick="onDblClickServiceCode(this)"><%=serviceCode%></span></font></b>
                                        <input type="text" name="unit_xml_<%=serviceCode%>"
                                               value="<carlos:encode value='<%= request.getParameter("unit_xml_"+serviceCode)!=null? request.getParameter("unit_xml_"+serviceCode):"" %>' context="htmlAttribute"/>"
                                               size="1" maxlength="2" style="width: 20px; height: 12px;"/></td>
                                    <td <%=serviceDesc.length() > 30 ? "title=\"" + serviceDesc + "\"" : ""%>><font
                                            size="-1"><%=serviceDesc.length() > 30 ? serviceDesc.substring(0, 30) + "..." : serviceDesc%>
                                        <input type="hidden" name="desc_xml_<%=serviceCode%>"
                                               value="<%=serviceDesc%>"/> </font></td>
                                    <td align="right"><font size="-1"><%=serviceDisp%>
                                    </font> <input
                                            type="hidden" name="price_xml_<%=serviceCode%>"
                                            value="<%=serviceDisp%>"/> <input type="hidden"
                                                                              name="perc_xml_<%=serviceCode%>"
                                                                              value="<%=servicePercentage%>"/>
                                        <input type="hidden" name="sli_xml_<%=serviceCode%>" value="<%=serviceSLI%>"/>
                                    </td>
                                </tr>
                                <% } %>
                            </table>


                        </td>
                        <td width="33%" valign="top">

                            <table width="100%" border="1" cellspacing="0" cellpadding="0"
                                   height="0" bordercolorlight="#99A005" bordercolordark="#FFFFFF">
                                <tr bgcolor="#CCCCFF">
                                    <th width="10%" nowrap><font size="-1" color="#000000"><%=headerTitle3%>
                                    </font></th>
                                    <th width="70%" bgcolor="#CCCCFF"><font size="-1"
                                                                            color="#000000"><fmt:message key="billing.service.desc"/></font></th>
                                    <th><font size="-1" color="#000000"> <fmt:message key="billing.service.fee"/></font></th>
                                </tr>
                                <%
                                    for (int i = 0; i < vecCodeCol3.size(); i++) {
                                        propT = (Properties) vecCodeCol3.get(i);
                                        serviceCode = propT.getProperty("serviceCode");
                                        serviceDesc = propT.getProperty("serviceDesc");
                                        serviceDisp = propT.getProperty("serviceDisp");
                                        servicePercentage = propT.getProperty("servicePercentage");
                                        serviceSLI = propT.getProperty("serviceSLI");
                                        if (propPremium.getProperty(serviceCode) != null) premiumFlag = "A";
                                        else premiumFlag = "";
                                %>
                                <tr bgcolor=<%=i % 2 == 0 ? "#FFFFFF" : "#EEEEFF"%>>
                                    <td nowrap><input type="checkbox"
                                                      name="code_xml_<%=serviceCode%>" value="checked"
                                            <%="checked".equals(request.getParameter("code_xml_" + serviceCode)) ? "checked" : ""%> />
                                        <b><font size="-1"
                                                 color="<%=premiumFlag.equals("A")? "#993333" : "black"%>"><span
                                                id="sc<%=(""+i).substring(0,1)+serviceCode%>"
                                                onDblClick="onDblClickServiceCode(this)"><%=serviceCode%></span></font></b>
                                        <input type="text" name="unit_xml_<%=serviceCode%>"
                                               value="<carlos:encode value='<%= request.getParameter("unit_xml_"+serviceCode)!=null? request.getParameter("unit_xml_"+serviceCode):"" %>' context="htmlAttribute"/>"
                                               size="1" maxlength="2" style="width: 20px; height: 12px;"/></td>
                                    <td <%=serviceDesc.length() > 30 ? "title=\"" + serviceDesc + "\"" : ""%>><font
                                            size="-1"><%=serviceDesc.length() > 30 ? serviceDesc.substring(0, 30) + "..." : serviceDesc%>
                                        <input type="hidden" name="desc_xml_<%=serviceCode%>"
                                               value="<%=serviceDesc%>"/> </font></td>
                                    <td align="right"><font size="-1"><%=serviceDisp%>
                                    </font> <input
                                            type="hidden" name="price_xml_<%=serviceCode%>"
                                            value="<%=serviceDisp%>"/> <input type="hidden"
                                                                              name="perc_xml_<%=serviceCode%>"
                                                                              value="<%=servicePercentage%>"/>
                                        <input type="hidden" name="sli_xml_<%=serviceCode%>" value="<%=serviceSLI%>"/>
                                    </td>
                                </tr>
                                <% } %>
                            </table>


                        </td>
                    </tr>
                </table>


            </td>
        </tr>

        <input type="hidden" name="clinic_no" value="<%=clinicNo%>"/>
        <input type="hidden" name="demographic_no" value="${carlos:forHtmlAttribute(shortcutPg1Model.demoNo)}"/>
        <input type="hidden" name="appointment_no" value="${carlos:forHtmlAttribute(shortcutPg1Model.apptNo)}"/>

        <input type="hidden" name="ohip_version" value="V03G"/>
        <input type="hidden" name="hin" value="<%=demoHIN%>"/>

        <input type="hidden" name="start_time"
               value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("start_time")) %>' context="htmlAttribute"/>"/>

        <input type="hidden" name="demographic_dob" value="<%=demoDOB%>"/>

        <input type="hidden" name="apptProvider_no"
               value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("apptProvider_no")) %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="asstProvider_no"
               value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("asstProvider_no")) %>' context="htmlAttribute"/>"/>

        <input type="hidden" name="demographic_name" value="${carlos:forHtmlAttribute(shortcutPg1Model.demoName)}"/>
        <input type="hidden" name="providerview" value="${carlos:forHtmlAttribute(shortcutPg1Model.providerView)}"/>
        <input type="hidden" name="appointment_date"
               value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("appointment_date")) %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="assgProvider_no"
               value="${carlos:forHtmlAttribute(shortcutPg1Model.assignedProviderNo)}"/>
        <input type="hidden" name="billForm" value="${carlos:forHtmlAttribute(shortcutPg1Model.ctlBillForm)}"/>

    </table>
</form>


<br/>
<%-- Both branches now iterate the unified vecHist + vecHistD that the
     assembler builds for legacy and new-ON-billing modes alike. --%>
<% if (!CarlosProperties.getInstance().getProperty("isNewONbilling", "").equals("true")) {
%>
<table border="0" cellpadding="0" cellspacing="2" width="100%"
       bgcolor="#CCCCFF">
    <tr>
        <td colspan="6" class="RowTop">${carlos:forHtmlContent(shortcutPg1Model.demoName)} - <b><fmt:message key="billing.hospitalBilling.frmBillHistory"/>
        </b> <fmt:message key="billing.hospitalBilling.frmLastFive"/></td>
    </tr>
    <tr>
        <td>
            <table border="1" cellspacing="0" cellpadding="0"
                   bordercolorlight="#99A005" bordercolordark="#FFFFFF" width="100%"
                   bgcolor="#FFFFFF">
                <tr bgcolor="#99CCCC" align="center">
                    <td nowrap><fmt:message key="billing.hospitalBilling.frmSerial"/></td>
                    <td nowrap><fmt:message key="billing.billingCorrection.msgBillingDate"/></td>
                    <td nowrap><fmt:message key="billing.hospitalBilling.frmApptAdmDate"/></td>
                    <td nowrap><fmt:message key="billing.billingCorrection.formServiceCode"/></td>
                    <td nowrap><fmt:message key="billing.hospitalBilling.formDx"/></td>
                    <td><fmt:message key="billing.hospitalBilling.frmCreateDate"/></td>
                </tr>
                <%
                    for (int i = 0; i < vecHist.size(); i++) {
                        Properties prop = (Properties) vecHist.get(i);
                        Properties propD = (Properties) vecHistD.get(i);
                %>
                <tr bgcolor="<%=i%2==0?"ivory":"#EEEEFF"%>" align="center">
                    <td><%= prop.getProperty("billing_no", "&nbsp;") %>
                    </td>
                    <td><%= prop.getProperty("billing_date", "&nbsp;") %>
                    </td>
                    <td><%= prop.getProperty("visitdate", "&nbsp;") %>
                    </td>
                    <td><%= propD.getProperty("service_code", "&nbsp;") %>
                    </td>
                    <td><%= propD.getProperty("diagnostic_code", "&nbsp;") %>
                    </td>
                    <td><%= prop.getProperty("update_date", "&nbsp;") %>
                    </td>
                </tr>
                <%
                    }
                %>
            </table>
        </td>
    </tr>
</table>
<% } else { %>
<table border="0" cellpadding="1" cellspacing="2" width="100%"
       class="myIvory">
    <tr class="myYellow">
        <td colspan="6">${carlos:forHtmlContent(shortcutPg1Model.demoName)} - <b><fmt:message key="billing.hospitalBilling.frmBillHistory"/></b>
            <fmt:message key="billing.hospitalBilling.frmLastFive"/>
        </td>
    </tr>
    <tr>
        <td>
            <table border="1" cellspacing="0" cellpadding="1"
                   bordercolorlight="#99A005" bordercolordark="#FFFFFF" width="100%">
                <tr class="myYellow" align="center">
                    <th><fmt:message key="billing.hospitalBilling.frmSerial"/></th>
                    <th><fmt:message key="billing.billingCorrection.msgBillingDate"/></th>
                    <th><fmt:message key="billing.hospitalBilling.frmApptAdmDate"/></th>
                    <th><fmt:message key="billing.billingCorrection.formServiceCode"/></th>
                    <th><fmt:message key="billing.hospitalBilling.formDx"/></th>
                    <th><fmt:message key="billing.hospitalBilling.frmCreateDate"/></th>
                </tr>
                <%
                    for (int i = 0; i < vecHist.size(); i++) {
                        Properties prop = (Properties) vecHist.get(i);
                        Properties propD = (Properties) vecHistD.get(i);
                %>
                <tr <%=i % 2 == 0 ? "class=\"myGreen\"" : ""%> align="center">
                    <td><%= prop.getProperty("billing_no", "&nbsp;") %>
                    </td>
                    <td><%= prop.getProperty("billing_date", "&nbsp;") %>
                    </td>
                    <td><%= prop.getProperty("visitdate", "&nbsp;") %>
                    </td>
                    <td><%= propD.getProperty("service_code", "&nbsp;") %>
                    </td>
                    <td><%= propD.getProperty("diagnostic_code", "&nbsp;") %>
                    </td>
                    <td><%= prop.getProperty("update_date", "&nbsp;") %>
                    </td>
                </tr>
                <%
                    }
                %>
            </table>
        </td>
    </tr>
</table>
<% } %>
<script type="text/javascript">//<![CDATA[
// the default multiple dates selected, first time the calendar is instantiated
var MA = [];
setupServiceDates();

function setupServiceDates() {
    var el = document.titlesearch.billDate.value;

    var dates = el.split('\n');

    MA.length = 0;
    for (var i in dates) {
        if (dates[i])
            MA[MA.length] = new Date(dates[i]);
    }
}

function closed(cal) {
    var el = document.titlesearch.billDate;
    // reset initial content.
    el.value = "";
    MA.length = 0;
    for (var i in cal.multiple) {
        var d = cal.multiple[i];

        if (d) {
            el.value += d.print("%Y-%m-%d") + "\n";
            MA[MA.length] = d;
        }
    }
    cal.hide();

    return true;
};

Calendar.setup({
    align: "BR",
    showOthers: true,
    multiple: MA, // pass the initial or computed array of multiple dates to be initially selected
    onClose: closed,
    button: "trigger",
    inputField: "billDate"
});
//]]>
Calendar.setup({
    inputField: "xml_vdate",
    ifFormat: "%Y-%m-%d",
    showsTime: false,
    button: "xml_vdate_cal",
    singleClick: true,
    step: 1
});

</script>
<%!
    String getDefaultValue(String paraName, Vector vec, String propName) {
        String ret = "";
        if (paraName != null && !"".equals(paraName)) {
            ret = paraName;
        } else if (vec != null && vec.size() > 0 && vec.get(0) != null) {
            ret = ((Properties) vec.get(0)).getProperty(propName, "");
        }
        return ret;
    }
%>
</body>
</html>
