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
<!DOCTYPE html>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp"%>

<%@page import="java.util.*,java.net.*,java.sql.*" %>
<%@page import="io.github.carlos_emr.*" %>
<%@page import="io.github.carlos_emr.carlos.util.*" %>
<%@page import="io.github.carlos_emr.carlos.appt.*" %>
<%@page import="io.github.carlos_emr.carlos.billings.ca.on.data.*" %>
<%@page import="io.github.carlos_emr.carlos.billings.ca.on.pageUtil.*" %>
<%@page import="io.github.carlos_emr.carlos.billings.ca.bc.decisionSupport.BillingGuidelines" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ProviderPreference" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.CssStyle" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.BillingServiceDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.BillingService" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ClinicNbr" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.CtlBillingTypeDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.CtlBillingType" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.CtlBillingService" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.CtlBillingServicePremium" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.CtlDiagCode" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.DiagnosticCode" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DxresearchDAO" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Dxresearch" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.MyGroupDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.MyGroup" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Appointment" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.decisionSupport.model.DSConsequence" %>
<%@page import="io.github.carlos_emr.carlos.web.admin.ProviderPreferencesUIBean" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>

<%@page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@page import="io.github.carlos_emr.carlos.billing.CA.filters.CodeFilterManager" %>

<%--
  Defensive model-resolver: ensures ${formModel} is set on the request even on
  the unlikely path where this JSP is reached without going through
  ViewBillingON2Action (e.g., a stray <jsp:forward> from an unguarded entry).
  The action's own _billing r privilege check is duplicated here for parity:
  without it a future bypass would silently run the full PHI-touching
  assembler on an unauthenticated request.
--%>
<%
    if (request.getAttribute("formModel") == null) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("billingON.jsp fallback: missing session");
        }
        io.github.carlos_emr.carlos.managers.SecurityInfoManager __secMgr;
        try {
            __secMgr = SpringUtils.getBean(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
        } catch (RuntimeException __springEx) {
            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error(
                    "billingON.jsp fallback: SecurityInfoManager bean lookup failed", __springEx);
            throw new SecurityException("billingON.jsp fallback: privilege check unavailable", __springEx);
        }
        if (!__secMgr.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("billingON.jsp fallback: missing required sec object (_billing)");
        }
        request.setAttribute("formModel",
                new BillingONFormDataAssembler().assemble(loggedInInfo, request));
    }
%>


<%@page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@page import="org.apache.commons.lang3.StringUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.appt.JdbcApptImpl" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingSiteIdPrep" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.SxmlMisc" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<html>
<head>
    <title><fmt:message key="oscar.billing.ca.on.billingON.title"/></title>

    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link href="${ pageContext.request.contextPath }/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
    <link href="${ pageContext.request.contextPath }/css/fontawesome-all.min.css" rel="stylesheet" type="text/css">


    <style type="text/css">
        <!--
        .demo1 {
            color: #000033;
            background-color: lightgray;
            position: absolute;
            top: 50px;
            left: 170px;
            width: 190px;
            height: 80px;
            z-index: 99;
            visibility: hidden;
        }

        .demo1 th {
            background-color: silver;

        }

        .demo1 td {
            text-align: center;

        }

        -->
        select, input[type="text"] {
            margin-bottom: 0px;
        }

        .border1, .border1 th, .border1 td {
            border: 1px solid lightgray

        }
    </style>

    <!-- calendar stylesheet -->
    <link rel="stylesheet" type="text/css" media="all"
          href="${ pageContext.request.contextPath }/share/calendar/calendar.css" title="win2k-cold-1"/>
    <!-- main calendar program -->
    <script type="text/javascript" src="${ pageContext.request.contextPath }/share/calendar/calendar.js"></script>
    <!-- language for the calendar -->
    <script src="${ pageContext.request.contextPath }/share/calendar/lang/<fmt:message key="global.javascript.calendar"/>"></script>
    <!-- the following script defines the Calendar.setup helper function, which makes
           adding a calendar a matter of 1 or 2 lines of code. -->
    <script type="text/javascript"
            src="${ pageContext.request.contextPath }/share/calendar/calendar-setup.js"></script>

    <script src="${ pageContext.request.contextPath }/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="${ pageContext.request.contextPath }/library/jquery/jquery-compat.js"></script>
    <link rel="stylesheet" href="${ pageContext.request.contextPath }/library/jquery/jquery-ui-1.14.2.min.css"/>
    <script src="${ pageContext.request.contextPath }/library/jquery/jquery-ui-1.14.2.min.js"></script>

    <!-- to load for example /oscar/js/custom/ocean/global.js and /oscar/js/custom/ocean/billing.js although those are not present in stock -->
    <oscar:customInterface section="billing"/>
    <%-- Defined in its own <script> so a parse error in the larger inline
         script below cannot prevent onBlur handlers from resolving it. --%>
    <script>
        function upCaseCtrl(ctrl) {
            var n = document.forms[0].xml_billtype.selectedIndex;
            var val = document.forms[0].xml_billtype[n].value;
            if (val.substring(0, 3) == "ODP" || val.substring(0, 3) == "WCB" || val.substring(0, 3) == "BON") ctrl.value = ctrl.value.toUpperCase();
        }
    </script>
    <script>
        var billingContextPath = "<carlos:encode value='${pageContext.request.contextPath}' context='javaScriptBlock'/>";

        function gotoBillingOB() {
            var a = "";
            if (self.location.href.lastIndexOf("?") > 0) {
                a = self.location.href.substring(self.location.href.lastIndexOf("?"));
            }
            self.location.href = billingContextPath + "/billing" + a;
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
            for (i = 0; i < (args.length - 2); i += 3) {
                if ((obj = findObj(args[i])) != null) {
                    v = args[i + 2];
                    if (obj.style) {
                        obj = obj.style;
                        v = (v == 'show') ? 'visible' : (v = 'hide') ? 'hidden' : v;
                    }
                    obj.visibility = v;
                }
            }
        }

        function onNext() {
            var codeToAddStr = "<carlos:encode value='${formModel.patientDxAddCode}' context="javaScriptBlock"/>";
            var codeToMatchStr = "<carlos:encode value='${formModel.patientDxMatchCode}' context="javaScriptBlock"/>";

            var codeToAdd = codeToAddStr.split(",");
            var codeToMatch = {};
            if (codeToMatchStr != "") {
                var codeToMatchArr = codeToMatchStr.split(",");
                for (var i = 0; i < codeToMatchArr.length; i++) {
                    var codeMatch = codeToMatchArr[i].split(":");
                    codeToMatch[codeMatch[0]] = codeMatch[1];
                }
            }

            var dxCode = document.titlesearch.dxCode.value;
            var codeMatch = codeToMatch[dxCode];
            if (codeToAdd.indexOf(dxCode) >= 0 || codeMatch != null) {
                var dxCodeMatch = codeMatch == null ? dxCode : codeMatch;
                <c:forEach var="__pc" items="${formModel.patientDx}">
                if (dxCodeMatch === "<carlos:encode value='${__pc}' context='javaScript'/>") dxCode = -1;
                </c:forEach>
                if (dxCode != -1 && codeMatch != null) {
                    document.titlesearch.codeMatchToPatientDx.value = codeMatch;
                }
            } else {
                dxCode = -1;
            }

            var ret = true;
            if (!checkAllDates()) {
                ret = false;
            } else if (!checkServicePercent()) {
                ret = false;
                alert("Please enter a decimal number in the service code percent textbox!");
            } else if (!existServiceCode() && document.forms[0].services_checked.value <= 0) {
                ret = false;
                alert("You haven't selected any billing item yet!");
            } else if (!checkSli()) {
                ret = false;
                alert("You have selected billing codes that require an SLI code but have not provided an SLI code.");
            } else if (document.forms[0].dxCode.value == "") {
                ret = confirm("You didn't enter a diagnostic code in the Dx box. Continue?");
                if (!ret) document.forms[0].dxCode.focus();
            } else if (dxCode != -1) {
                var codeDesc = document.getElementById("code_desc").innerHTML.trim();
                var yes = confirm("Add \"" + codeDesc + "\"(" + dxCode + ") to patient's disease registry?\n(OK=Yes, Cancel=No)");
                if (yes) document.titlesearch.addToPatientDx.value = "yes";
            }
            return ret;
        }

        function checkServicePercent() {
            var ret = true;
            var regInt = /^-?\d+\.\d+$/;
            jQuery("input[id^='serviceAt'][value!='']").each(function () {
                var val = this.value.trim();
                if (val.length > 0 && !regInt.test(val)) {
                    ret = false;
                    return false;
                }
            });
            return ret;
        }

        function checkSli() {
            var needsSli = false;
            jQuery("input[name^=xml_]:checked").each(function () {
                needsSli = needsSli || eval(jQuery("input[name='sli_xml_" + this.name.substring(4) + "']").val());
            });
            jQuery("input[name^=serviceCode][value!='']").each(function () {
                needsSli = needsSli || eval(jQuery("input[name='sli_xml_" + this.value + "']").val());
            });
            return !needsSli || jQuery("select[name='xml_slicode']").get(0).selectedIndex != 0;
        }

        function checkAllDates() {
            var b = true;

            if (document.forms[0].xml_provider.value == "000000") {
                alert("Please select a providers.");
                b = false;
            }
                <c:if test="${not formModel.rmaEnabled}">
            else if (document.forms[0].xml_visittype.options[2].selected && (document.forms[0].xml_vdate.value == "" || document.forms[0].xml_vdate.value == "0000-00-00")) {
                alert("Need an admission date.");
                b = false;
            }
                </c:if>
            else if (document.forms[0].xml_vdate.value.length > 0) {
                b = checkServiceDate(document.forms[0].xml_vdate.value);
            } else if (document.forms[0].service_date.value.length > 0) {
                b = checkServiceDate(document.forms[0].service_date.value);
            } else if (document.forms[0].referralCode.value.length > 0) {
                if (document.forms[0].referralCode.value.length != 6 || !isInteger(document.forms[0].referralCode.value)) {
                    alert("Wrong referral code!");
                    b = false;
                }
            }
            return b;
        }

        function updateDate() {
            if (!document.forms[0].xml_visittype.options[2].selected || !document.forms[0].xml_visittype.options[4].selected) {
                document.getElementById("xml_vdate").value = "";  //only nursing homes and hospitals have admission dates
            }
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
		alert("You may have a wrong Service/admission Date!" + " Wrong " + sMsg);
		return false;
            } else {
                return true;
            }
        }

        function existServiceCode() {
            b = false;

            if (document.forms[0].serviceCode0.value != "") b = true;
                <c:forEach var="i" begin="1" end="11">
            else if (document.forms[0]['serviceCode' + ${i}].value != "") b = true;
            </c:forEach>

            return b;
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
            awnd = rs('att', (billingContextPath + '/billing/CA/ON/ViewSearchRefDoc?param=' + t0), 1000, 800, 1);
        }

        function referralScriptAttach2(elementName, name2) {
            var d = elementName;
            t0 = escape("document.forms[0].elements[\'" + d + "\'].value");
            t1 = escape("document.forms[0].elements[\'" + name2 + "\'].value");
            awnd = rs('att', (billingContextPath + '/billing/CA/ON/ViewSearchRefDoc?param=' + t0 + '&param2=' + t1 + '&submit=Search&keyword=' + document.forms[0].elements[name2].value), 1000, 800, 1);
            //awnd.focus();
        }

        function dxScriptAttach(name2) {
            ff = eval("document.forms[0].elements['" + name2 + "']");
            f0 = ff.value;
            f1 = escape("document.forms[0].elements[\'" + name2 + "\'].value");
            awnd = rs('att', billingContextPath + '/billing/CA/ON/ViewBillingDigSearch?name=' + f0 + '&search=&name2=' + f1, 800, 800, 1);
            //awnd.focus();
        }

        function scScriptAttach(nameF) {
            f0 = escape(nameF.value);
            f1 = escape("document.forms[0].elements[\'" + nameF.name + "\'].value");
            awnd = rs('att', billingContextPath + '/billing/CA/ON/ViewBillingCodeSearch?name=' + f0 + '&search=&name1=&name2=&nameF=' + f1, 600, 600, 1);
            //awnd.focus();
        }

        function onDblClickServiceCode(item) {
            if (document.forms[0].serviceCode0.value == "") {
                document.forms[0].serviceCode0.value = item.id.substring(3);
            }
                <c:forEach var="i" begin="1" end="11">
            else if (document.forms[0]['serviceCode' + ${i}].value == "") {
                document.forms[0]['serviceCode' + ${i}].value = item.id.substring(3);
            }
            </c:forEach>
        }

        function onClickServiceCode(item) {
            if (document.forms[0].serviceCode0.value == "") {
                document.forms[0].serviceCode0.value = item.id.substring(4);
            }
                <c:forEach var="i" begin="1" end="11">
            else if (document.forms[0]['serviceCode' + ${i}].value == "") {
                document.forms[0]['serviceCode' + ${i}].value = item.id.substring(4);
            }
            </c:forEach>
        }

        function changeCut(dropdown) {
            var str = dropdown.options[dropdown.selectedIndex].value;
            var temp = new Array();
            temp = str.split('\|');
            var tlen = temp.length;
            document.forms[0].dxCode.value = "";
            document.forms[0].dxCode1.value = "";
            document.forms[0].dxCode2.value = "";
            var n = 0;
            for (var i = 0; i < 12; ++i) {
                ocode = eval("document.forms[0].serviceCode" + i);
                ounit = eval("document.forms[0].serviceUnit" + i);
                operc = eval("document.forms[0].serviceAt" + i);
                ocode.value = "";
                ounit.value = "";
                operc.value = "";
                if (i < tlen && temp[n].length == 5) {
                    ocode.value = temp[n];
                    ounit.value = temp[n + 1];
                    operc.value = temp[n + 2];
                    n = n + 3;
                } else if (i < tlen && temp[n].length == 3) {
                    if (document.forms[0].dxCode.value == "") {
                        document.forms[0].dxCode.value = temp[n];
                    } else if (document.forms[0].dxCode1.value == "") {
                        document.forms[0].dxCode1.value = temp[n];
                    } else if (document.forms[0].dxCode2.value == "") {
                        document.forms[0].dxCode2.value = temp[n];
                    }
                    n = n + 1;
                }
            }
            if (document.forms[0].dxCode.value == "" && document.forms[0].dxCode1.value == "" && document.forms[0].dxCode2.value == "") {
                document.forms[0].dxCode.value = '<carlos:encode value='${formModel.dxCodeDefault}' context='javaScript'/>';
                document.forms[0].dxCode1.value = '<carlos:encode value='${formModel.requestParamEchoes["dxCode1"]}' context='javaScript'/>';
                document.forms[0].dxCode2.value = '<carlos:encode value='${formModel.requestParamEchoes["dxCode2"]}' context='javaScript'/>';
            }
        }

        function popupPage(vheight, vwidth, varpage) { //open a new popup window
            var page = "" + varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
            var popup = window.open(page, "billingfavourite", windowprops);
            if (popup != null) {
                if (popup.opener == null) popup.opener = self;
                popup.focus();
            }
        }

        function onClickRefDoc() {
            if (!document.forms[0].rfcheck.checked) {
                document.forms[0].referralCode.value = "";
                document.forms[0].referralDocName.value = "";
            } else {
                document.forms[0].referralCode.value = "<carlos:encode value='${formModel.referralDoctorOhip}' context="javaScriptBlock"/>";
                document.forms[0].referralDocName.value = "<carlos:encode value='${formModel.referralDoctor}' context="javaScriptBlock"/>";
            }
        }

        function onChangePrivate() {
            var n = document.forms[0].xml_billtype.selectedIndex;
            var val = document.forms[0].xml_billtype[n].value;
            <%-- Pre-encoded URL components precomputed in the assembler.
                 demoNameUrlEncoded uses URLEncoder.encode(...UTF-8); the others
                 round-trip via <carlos:encode context="uriComponent">. --%>
            <c:set var="__apptNoUri"><carlos:encode value='${formModel.requestParamEchoes["appointment_no"]}' context='uriComponent'/></c:set>
            <c:set var="__demoNoUri"><carlos:encode value='${formModel.requestParamEchoes["demographic_no"]}' context='uriComponent'/></c:set>
            <c:set var="__apptProvUri"><carlos:encode value='${formModel.requestParamEchoes["apptProvider_no"]}' context='uriComponent'/></c:set>
            <c:set var="__apptDateUri"><carlos:encode value='${formModel.requestParamEchoes["appointment_date"]}' context='uriComponent'/></c:set>
            <c:set var="__statusUri"><carlos:encode value='${formModel.requestParamEchoes["status"]}' context='uriComponent'/></c:set>
            <c:set var="__startTimeUri"><carlos:encode value='${formModel.requestParamEchoes["start_time"]}' context='uriComponent'/></c:set>
            <c:set var="__demoNameJs">&demographic_name=<carlos:encode value='${formModel.demoNameUrlEncoded}' context='javaScript'/></c:set>
            <c:set var="__commonQs">&appointment_no=<carlos:encode value='${__apptNoUri}' context='javaScript'/>${__demoNameJs}&demographic_no=<carlos:encode value='${__demoNoUri}' context='javaScript'/></c:set>
            <c:set var="__commonTail">&apptProvider_no=<carlos:encode value='${__apptProvUri}' context='javaScript'/>&providerview=<carlos:encode value='${__apptProvUri}' context='javaScript'/>&appointment_date=<carlos:encode value='${__apptDateUri}' context='javaScript'/>&status=<carlos:encode value='${__statusUri}' context='javaScript'/>&start_time=<carlos:encode value='${__startTimeUri}' context='javaScript'/>&bNewForm=1</c:set>
            if (val.substring(0, 3) == "PAT" || val.substring(0, 3) == "OCF" || val.substring(0, 3) == "ODS" || val.substring(0, 3) == "CPP" || val.substring(0, 3) == "STD") {
                self.location.href = billingContextPath + "/billing?curBillForm=PRI&hotclick=${__commonQs}&xml_billtype=" + val.substring(0, 3) + "${__commonTail}";
            } else if (val.substring(0, 3) == "BON") {
                self.location.href = billingContextPath + "/billing?curBillForm=<carlos:encode value='${formModel.primaryCareIncentive}' context='javaScript'/>&hotclick=${__commonQs}&xml_billtype=" + val.substring(0, 3) + "${__commonTail}";
            } else {
                <c:if test="${formModel.ctlBillForm eq 'PRI'}">
                self.location.href = billingContextPath + "/billing?curBillForm=<carlos:encode value='${formModel.defaultView}' context='javaScript'/>&hotclick=${__commonQs}&xml_billtype=" + val.substring(0, 3) + "${__commonTail}";
                </c:if>
            }
        }

        function showHideBox(layerName, iState) { // 1 visible, 0 hidden
            if (document.layers) {   //NN4+
                document.layers[layerName].visibility = iState ? "show" : "hide";
            } else if (document.getElementById) {  //gecko(NN6) + IE 5+
                var obj = document.getElementById(layerName);
                obj.style.visibility = iState ? "visible" : "hidden";
            } else if (document.all) {	// IE 4
                document.all[layerName].style.visibility = iState ? "visible" : "hidden";
            }
        }

        function onHistory() {
            var dd = document.forms[0].day.value;
            popupPage("800", "1000", billingContextPath + "/billing/CA/ON/ViewBillingONHistorySpec?demographic_no=<carlos:encode value='${formModel.demographicNo}' context='javaScript'/>&demo_name=<carlos:encode value='${formModel.demoNameUrlEncoded}' context='javaScript'/>&orderby=appointment_date&day=" + dd);
        }

        function prepareBack() {
            document.forms[0].services_checked.value = "<carlos:encode value='${formModel.requestParamEchoes["services_checked"]}' context='javaScript'/>";
            if (document.forms[0].services_checked.value == "") document.forms[0].services_checked.value = 0;
            document.forms[0].url_back.value = location.href;

            showBillFormDiv("group1_", "<carlos:encode value='${formModel.ctlBillForm}' context="javaScriptBlock"/>");
            showBillFormDiv("group2_", "<carlos:encode value='${formModel.ctlBillForm}' context="javaScriptBlock"/>");
            showBillFormDiv("group3_", "<carlos:encode value='${formModel.ctlBillForm}' context="javaScriptBlock"/>");
            showBillFormDiv("dxCodeSearchDiv_", "<carlos:encode value='${formModel.ctlBillForm}' context="javaScriptBlock"/>");

        }

        function showBillFormDiv(group, selectedForm) {
            var selectedFormDivGroupId = group + selectedForm;
            var thisDiv = document.getElementById(selectedFormDivGroupId);
            if (thisDiv) {
                if (thisDiv.style.display == "none") {
                    thisDiv.style.display = "block";
                }
            }
        }

        function hideBillFormDiv(group, selectedForm) {
            var selectedFormDivGroupId = group + selectedForm;
            var thisDiv = document.getElementById(selectedFormDivGroupId);
            if (thisDiv) {
                if (thisDiv.style.display == "block") {
                    thisDiv.style.display = "none";
                }
            }
        }

        function refreshServicesChecked(chkd) {
            var name_id = chkd.name;
            if (chkd.checked) {
                document.forms[0].services_checked.value++;

                //check other checkbox with same name
                for (i = 0; i < document.forms[0][name_id].length; i++) {
                    document.forms[0][name_id][i].checked = true;
                }
            } else {
                document.forms[0].services_checked.value--;

                //uncheck other checkbox with same name
                for (i = 0; i < document.forms[0][name_id].length; i++) {
                    document.forms[0][name_id][i].checked = false;
                }
            }
        }


        function callChangeCodeDesc() {
            setTimeout("changeCodeDesc();", 10);
        }

        function changeCodeDesc() {
            var url = billingContextPath + "/billing/CA/ON/ViewBillingONDxDesc";
            var pars = "diagnostic_code=" + document.forms[0].dxCode.value;

            //prototype
            //var descAjax = new Ajax.Updater("code_desc",url, {method: "get", parameters: pars});

            jQuery.ajax({
                url: url,
                type: "get",
                dataType: "html",
                data: pars,
                success: function (returnData) {
                    jQuery("#code_desc").html(returnData);
                },
                error: function (e) {
                    alert(e);
                }
            });
        }

        //this function will show the content within the <div> tag of billing codes
function toggleDiv(selectedBillForm, selectedBillFormName,billType)
{
            document.getElementById("billForm").value = selectedBillForm;
            document.getElementById("billFormName").value = selectedBillFormName;

            if (billType != '') {
                for (var i = 0; i < document.forms[0].xml_billtype.options.length; i++) {
                    if (document.forms[0].xml_billtype.options[i].value.substring(0, 3) == billType) {
                        document.forms[0].xml_billtype.options[i].selected = true;
                    }
                }
            }

            //dx search
            showBillFormDiv("dxCodeSearchDiv_", selectedBillForm);

            //var selectedForm = selectedBillForm.options[selectedBillForm.selectedIndex].value;
            showBillFormDiv("group1_", selectedBillForm);
            showBillFormDiv("group2_", selectedBillForm);
            showBillFormDiv("group3_", selectedBillForm);

            //hide other billing codes whose forms are not selected
            <c:forEach var="__st" items="${formModel.listServiceType}">
            if (selectedBillForm != "<carlos:encode value='${__st}' context='javaScript'/>") {
                hideBillFormDiv("group1_", "<carlos:encode value='${__st}' context='javaScript'/>");
                hideBillFormDiv("group2_", "<carlos:encode value='${__st}' context='javaScript'/>");
                hideBillFormDiv("group3_", "<carlos:encode value='${__st}' context='javaScript'/>");
                hideBillFormDiv("dxCodeSearchDiv_", "<carlos:encode value='${__st}' context='javaScript'/>");
            }
            </c:forEach>
        }


        //-->
    </script>

    <%-- Autocomplete styles: code items are one-line with ellipsis; referral items are two-row --%>
    <style>
        .billing-ac-item { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; padding: 3px 6px; cursor: pointer; }
        .billing-ac-item strong { font-weight: 600; }
        .billing-ac-ref-row1 { padding: 2px 6px; }
        .billing-ac-ref-row2 { padding: 1px 6px; font-size: 0.82em; color: #666; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .ui-autocomplete { max-height: 280px; overflow-y: auto; overflow-x: hidden; z-index: 9999 !important; }
        .ui-menu-item > div:hover, .ui-menu-item:hover { background-color: #e8f0fe; }
    </style>
    <fmt:message var="searchLabelMsg" key="billing.billingDigSearch.btnSearch"/>

    <script>
    jQuery(document).ready(function () {
        var ctx = "${pageContext.request.contextPath}";
        var searchLabel = '<carlos:encode value='${searchLabelMsg}' context='javaScript'/>';

        // Safe HTML escaping for autocomplete rendering
        function escHtml(s) {
            return jQuery("<div>").text(s || "").html();
        }

        // One-line rendering: <b>CODE</b> – description (truncated)
        function renderCodeItem(ul, item) {
            var li = jQuery("<li>").addClass("ui-menu-item");
            var inner = jQuery("<div>").addClass("billing-ac-item");
            inner.html("<strong>" + escHtml(item.code) + "</strong> \u2013 " + escHtml(item.description));
            li.append(inner).appendTo(ul);
            return li;
        }

        // Two-row rendering for referral doctors
        function renderRefDocItem(ul, item) {
            var li = jQuery("<li>").addClass("ui-menu-item");
            var row1 = jQuery("<div>").addClass("billing-ac-ref-row1");
            var nameHtml = "<strong>" + escHtml(item.lastName) + (item.firstName ? ", " + escHtml(item.firstName) : "") + "</strong>";
            if (item.specialtyType) {
                nameHtml += " <span class=\"badge rounded-pill border border-secondary text-secondary py-0\" style=\"font-size:0.78em\">" + escHtml(item.specialtyType) + "</span>";
            }
            row1.html(nameHtml);
            var row2 = jQuery("<div>").addClass("billing-ac-ref-row2");
            var info = [];
            if (item.streetAddress) info.push(escHtml(item.streetAddress));
            if (item.phoneNumber) info.push(escHtml(item.phoneNumber));
            row2.html(info.join(" &nbsp;|&nbsp; ") || "&nbsp;");
            li.append(row1).append(row2).appendTo(ul);
            return li;
        }

        // Attach code autocomplete with custom rendering to a jQuery set
        function initCodeAutocomplete($inputs, ajaxUrl) {
            $inputs.each(function () {
                var $input = jQuery(this);
                $input.attr("placeholder", searchLabel);
                var inst = $input.autocomplete({
                    source: function (request, response) {
                        jQuery.getJSON(ctx + ajaxUrl, {term: request.term}, response);
                    },
                    minLength: 2,
                    delay: 250,
                    select: function (event, ui) {
                        this.value = ui.item.code;
                        if (typeof changeCodeDesc === "function") setTimeout(changeCodeDesc, 10);
                        return false;
                    }
                }).data("ui-autocomplete");
                if (inst) inst._renderItem = renderCodeItem;
            });
        }

        // Dx diagnosis code fields
        initCodeAutocomplete(jQuery("input[name^='dxCode']"), "/billing/CA/ON/ViewBillingDigSearchAjax");

        // Billing service code fields
        initCodeAutocomplete(jQuery("input[name^='serviceCode']"), "/billing/CA/ON/ViewBillingCodeSearchAjax");

        // Referral doctor fields: referralCode and referralDocName both trigger search
        function initRefDocAutocomplete(inputEl) {
            var inst = jQuery(inputEl).autocomplete({
                source: function (request, response) {
                    jQuery.getJSON(ctx + "/billing/CA/ON/ViewSearchRefDocAjax", {term: request.term}, response);
                },
                minLength: 2,
                delay: 300,
                select: function (event, ui) {
                    var form = document.forms[0];
                    if (form.referralCode)    form.referralCode.value    = ui.item.referralNo || "";
                    if (form.referralDocName) form.referralDocName.value = (ui.item.lastName || "") + (ui.item.firstName ? ", " + ui.item.firstName : "");
                    return false;
                }
            }).data("ui-autocomplete");
            if (inst) inst._renderItem = renderRefDocItem;
        }

        var form = document.forms[0];
        if (form) {
            if (form.referralCode)    initRefDocAutocomplete(form.referralCode);
            if (form.referralDocName) initRefDocAutocomplete(form.referralDocName);
        }

        // billFormName autocomplete using the billing forms array embedded in the page
        var $bf = jQuery("#billFormName");
        $bf.prop("readonly", false);
        if ($bf.length && typeof _billingForms !== "undefined") {
            $bf.autocomplete({
                source: function (request, response) {
                    var term = request.term.toLowerCase();
                    response(jQuery.grep(_billingForms, function (item) {
                        return item.name.toLowerCase().indexOf(term) >= 0 ||
                               item.code.toLowerCase().indexOf(term) >= 0;
                    }));
                },
                minLength: 1,
                select: function (event, ui) {
                    this.value = ui.item.name;
                    toggleDiv(ui.item.code, ui.item.name, ui.item.billType);
                    showHideLayers("Layer1", "", "hide");
                    return false;
                }
            });
            var bfInst = $bf.data("ui-autocomplete");
            if (bfInst) {
                bfInst._renderItem = function (ul, item) {
                    return jQuery("<li>").addClass("ui-menu-item")
                        .append(jQuery("<div>").addClass("billing-ac-item")
                            .html("<strong>" + escHtml(item.code) + "</strong> \u2013 " + escHtml(item.name)))
                        .appendTo(ul);
                };
            }
        }
    });
    </script>
</head>

<body onload="if (typeof prepareBack === 'function') prepareBack(); if (typeof changeCodeDesc === 'function') changeCodeDesc(); if (typeof getDays === 'function') getDays(); if (typeof toggleDiv === 'function' && document.forms[0] && document.forms[0].xml_billtype && document.forms[0].xml_billtype.value) toggleDiv('<carlos:encode value='${formModel.ctlBillForm}' context="javaScriptAttribute"/>', '<carlos:encode value='${formModel.defaultBillFormName}' context="javaScriptAttribute"/>', document.forms[0].xml_billtype.value.substring(0, 3));">
<div id="Instrdiv" class="demo1">

    <table style="width: 99%;">
        <tr>
            <th style="text-align: right"><a href="javascript:void(0)"
                                             onclick="showHideBox('Instrdiv',0); return false;">X</a></th>
        </tr>
        <tr>
            <td><fmt:message key="oscar.billing.ca.on.billingON.defaultUnitAt"/></td>
        </tr>

    </table>
</div>
<div id="Layer1"
     style="position: absolute; left: 360px; top: 165px; width: 410px; height: 210px; z-index: 1; visibility: hidden">
    <table style="width: 98%; margin:auto; ">
        <tr>
            <td style="width: 96%; background-color:silver; height:12px;">
                <b><fmt:message key="oscar.billing.ca.on.billingON.billingFormLayer"/></b></td>
            <td style="width: 3%; background-color:silver; height:12px;"><b><a href="javascript:void(0);"
                                                                               onclick="showHideLayers('Layer1','','hide');return false;">X</a></b>
            </td>
        </tr>
        <c:forEach var="bf" items="${formModel.billingForms}">
        <tr>
            <td colspan="2" style="background-color:lightgray;"><b>
                <a href="javascript:void(0);"
                   onclick="toggleDiv('<carlos:encode value='${bf.code}' context="javaScriptAttribute"/>', '<carlos:encode value='${bf.name}' context="javaScriptAttribute"/>','<carlos:encode value='${bf.billType}' context="javaScriptAttribute"/>');showHideLayers('Layer1','','hide');"><carlos:encode value='${bf.name}' context="html"/>
                </a>
            </b></td>
        </tr>
        </c:forEach>
    </table>
</div>
<%-- Build billing form data for billFormName autocomplete --%>
<script>
var _billingForms = [<c:forEach var="bf" items="${formModel.billingForms}" varStatus="st"><c:if test="${not st.first}">,</c:if>{"code":"<carlos:encode value='${bf.code}' context="javaScriptBlock"/>","name":"<carlos:encode value='${bf.name}' context="javaScriptBlock"/>","billType":"<carlos:encode value='${bf.billType}' context="javaScriptBlock"/>","label":"<carlos:encode value='${bf.name}' context="javaScriptBlock"/>","value":"<carlos:encode value='${bf.name}' context="javaScriptBlock"/>"}</c:forEach>];
</script>

<div id="Layer2"
     style="position: absolute; left: 1px; top: 26px; width: 435px; height: 680px; z-index: 2; background-color: #FFCC00; border: 1px none #000000; visibility: hidden">
    <table style="width: 98%; margin:auto;">
        <tr>
            <td style="width: 10%"><b><fmt:message key="oscar.billing.ca.on.billingON.dxCode"/></b></td>
            <td style="width: 85%"><b><fmt:message key="oscar.billing.ca.on.billingON.description"/></b></td>
            <td><a href="javascript:void(0);" onclick="showHideLayers('Layer2','','hide');return false">
            </a>
            </td>
        </tr>
    </table>
    <c:forEach var="dxEntry" items="${formModel.dxCodesByServiceType}">
    <%-- The assembler already runs sanitizeIdToken on service-type codes, so
         dxEntry.key is alphanumeric+underscore. The fn:replace here is
         defense-in-depth — if a future code path ever bypasses the sanitizer,
         the rendered HTML id remains valid (no spaces, which would otherwise
         split the id attribute and break the JS lookup-by-id). --%>
    <div id="dxCodeSearchDiv_<carlos:encode value='${fn:replace(dxEntry.key, " ", "_")}' context='htmlAttribute'/>" style="display: none;">
        <c:forEach var="dx" items="${dxEntry.value}">
        <table style="width: 98%; margin:auto;" class="table-striped table-hover">
            <tr>
                <td style="width: 10%">
                    <a href="javascript:void(0);"
                       onclick="document.forms[0].dxCode.value='<carlos:encode value='${dx.diagnosticCode}' context='javaScriptAttribute'/>';showHideLayers('Layer2','','hide');changeCodeDesc();return false;">
                        <carlos:encode value='${dx.diagnosticCode}' context='html'/>
                    </a>
                </td>
                <td>
                    <a href="javascript:void(0);"
                       onclick="document.forms[0].dxCode.value='<carlos:encode value='${dx.diagnosticCode}' context='javaScriptAttribute'/>';showHideLayers('Layer2','','hide');changeCodeDesc();return false;">
                        <carlos:encode value='${fn:length(dx.description) lt 56 ? dx.description : fn:substring(dx.description, 0, 55)}' context='html'/>
                    </a>
                </td>
            </tr>
        </table>
        </c:forEach>
    </div>
    </c:forEach>

</div>

<form method="post" id="titlesearch" name="titlesearch"
      action="<carlos:encode value='${pageContext.request.contextPath}/billing/CA/ON/ViewBillingONReview' context='htmlAttribute'/>" onsubmit="return onNext();">
    <input type="hidden" name="checkFlag" id="checkFlag"
           value="<carlos:encode value='${formModel.requestParamEchoes[\"checkFlag\"]}' context='htmlAttribute'/>"/>
    <input type="hidden" name="addToPatientDx"/>
    <input type="hidden" name="codeMatchToPatientDx"/>

    <table class="xmyDarkGreen"
           style="width: 100%; background-color: silver;">
        <tr>
            <td><H4><i class="fa-solid fa-money-bill" style="margin-left:10px;"></i>&nbsp;<fmt:message key="oscar.billing.ca.on.billingON.headerTitle"/></H4></td>
            <td style="text-align: right"><i class="fa-solid fa-circle-question"></i>&nbsp;
                <i class="fa-solid fa-pen-to-square"></i><a href="javascript:void(0);"
                                              onclick="popupPage(800,700,billingContextPath + '/billing/CA/ON/ViewBillingONFavourite'); return false;">
                    <fmt:message key="oscar.billing.ca.on.billingON.edit"/>
                </a> <select name="cutlist" id="cutlist" onchange="changeCut(this)">
                    <option selected="selected" value=""><fmt:message key="oscar.billing.ca.on.billingON.superCodes"/></option>
                    <c:forEach var="fav" items="${formModel.billingFavouriteOptions}">
                        <option value="<carlos:encode value='${fav.value}' context='htmlAttribute'/>"><carlos:encode value='${fav.text}' context='html'/></option>
                    </c:forEach>
                </select></td>
            <td style="text-align: right; width: 10%; white-space:nowrap">
                <input type="submit"
                       name="submit" value="<fmt:message key="oscar.billing.ca.on.billingON.next"/>" style="width: 120px;" class="btn btn-primary"/>
                <input
                        type="button" class="btn btn-secondary" name="button" value="<fmt:message key="oscar.billing.ca.on.billingON.exit"/>" style="width: 120px;"
                        onclick="self.close();"/> &nbsp;
            </td>
        </tr>
    </table>

    <table style="width: 100%; border-spacing:2px;">
        <tr>
            <td>
					<table  style="width: 100%;">
                    <tr>
                        <td style="white-space:nowrap; width: 10%; text-align: center"><b>&nbsp;<oscar:nameage
                                demographicNo="${formModel.demographicNo}"/> <carlos:encode value='${formModel.rosterStatus}' context='html'/>
                        </b>
                            <c:choose>
                                <c:when test="${formModel.appointmentNo eq '0'}">
                                    <span class="input-group">
								        <input type="text" class="form-control" id="service_date" name="service_date" readonly
                                           value="<carlos:encode value='${formModel.serviceDateDefault}' context='htmlAttribute'/>"
                                           style="width: 80px; height:14px;  vertical-align: bottom;">
                                        <span class="input-group-text" id="service_date_cal" style="cursor:pointer;">
                                            <img src="${pageContext.request.contextPath}/images/cal.gif"
                                                 style="height:14px;" alt="cal"></span></span>
                                </c:when>
                                <c:otherwise>
                                    <input type="text" id="service_date" name="service_date" readonly
								        value="<carlos:encode value='${formModel.requestParamEchoes[\"appointment_date\"]}' context='htmlAttribute'/>"
                                       maxlength="10" style="width: 80px;">
                                </c:otherwise>
                            </c:choose></td>
                        <td style="text-align: center;"
                            class="${not empty formModel.billingRecommendations ? 'alert' : ''}">${formModel.billingRecommendations}
                        </td>
                        <td style="text-align: center;">${formModel.displayMessage}
                        </td>
                    </tr>
                </table>

                <table class="border1" style="width: 100%;">
                    <tr>
                        <td style="width: 46%">
                            <table class="border1" style="width: 100%; border-spacing:2px;"
                            >
                                <tr>
                                    <td colspan="2"><fmt:message key="oscar.billing.ca.on.billingON.specialistBilling"/>
                                        &nbsp;&nbsp;&nbsp;&nbsp; <a href="javascript:void(0);"
                                                                    title="<fmt:message key="oscar.billing.ca.on.billingON.instructionTitle"/>"
                                                                    onClick="showHideBox('Instrdiv',1);return false;"><fmt:message key="oscar.billing.ca.on.billingON.instruction"/>
                                        </a>
                                    </td>
                                    <td style="vertical-align:top" rowspan="2">
                                        <table
                                                style="width: 100%;">
                                            <tr>
                                                <td style="width: 15%">&nbsp;</td>
                                                <td style="white-space:nowrap">
                                                    <div id="code_desc"></div>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td><a href="javascript:void(0);"
                                                       onclick="showHideLayers('Layer2','','show','Layer1','','hide'); return false;"><fmt:message key="oscar.billing.ca.on.billingON.dx"/></a>
                                                </td>
                                                <td><input type="text" name="dxCode" class="form-control form-control-sm d-inline-block w-auto"
                                                           maxlength="5"
                                                           onchange="changeCodeDesc();"
                                                           value="<carlos:encode value='${formModel.dxCodeDefault}' context='htmlAttribute'/>"/>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td><fmt:message key="oscar.billing.ca.on.billingON.dx1"/></td>
                                                <td><input type="text" name="dxCode1" class="form-control form-control-sm d-inline-block w-auto"
                                                           maxlength="5"
                                                           value="<carlos:encode value='${formModel.requestParamEchoes[\"dxCode1\"]}' context='htmlAttribute'/>"/>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td><fmt:message key="oscar.billing.ca.on.billingON.dx2"/></td>
                                                <td><input type="text" name="dxCode2" class="form-control form-control-sm d-inline-block w-auto"
                                                           maxlength="5"
                                                           value="<carlos:encode value='${formModel.requestParamEchoes[\"dxCode2\"]}' context='htmlAttribute'/>"/>
                                                </td>
                                            </tr>
                                        </table>
                                        <a
                                                href="javascript:referralScriptAttach2('referralCode','referralDocName')"><fmt:message key="oscar.billing.ca.on.billingON.referralDoctor"/></a>
                                        <input type="checkbox" name="rfcheck" value="checked"
                                            ${formModel.referralCheckedDefault eq 'checked' ? 'checked' : ''} onclick="onClickRefDoc()"/><br/>
                                        <input
                                                type="text" name="referralCode" class="form-control form-control-sm d-inline-block w-auto" maxlength="6"
                                                placeholder="<fmt:message key="encounter.oscarConsultationRequest.config.AddSpecialist.referralNo"/>"
                                                value="<carlos:encode value='${formModel.referralNoDefault}' context='html'/>"><br/>
                                        <input placeholder="<fmt:message key="demographic.demographiceditdemographic.formRefDoc"/>"
                                               type="text" name="referralDocName" class="form-control" maxlength="60"
                                               value="<carlos:encode value='${formModel.referralNameDefault}' context='html'/>">
                                    </td>
                                </tr>
                                <tr>
                                    <td style="white-space:nowrap; width: 33%; text-align: center" class="xmyPink"><b><fmt:message key="oscar.billing.ca.on.billingON.codeTimePercent"/></b><br/>
                                        <c:forEach var="i" begin="0" end="5">
                                            <input type="text" name="serviceCode${i}" class="form-control form-control-sm d-inline-block w-auto"
                                                   value="<carlos:encode value='${formModel.requestParamEchoes[\"serviceCode\".concat(i)]}' context='htmlAttribute'/>"
                                                   onBlur="upCaseCtrl(this)"/>x
                                            <input type="text" name="serviceUnit${i}" size="2" maxlength="4"
                                                   style="width: 20px;"
                                                   value="<carlos:encode value='${formModel.requestParamEchoes[\"serviceUnit\".concat(i)]}' context='htmlAttribute'/>"/>@
                                            <input type="text" name="serviceAt${i}" size="3" maxlength="4"
                                                   style="width: 30px"
                                                   value="<carlos:encode value='${formModel.requestParamEchoes[\"serviceAt\".concat(i)]}' context='htmlAttribute'/>"/><br/>
                                        </c:forEach></td>
                                    <td style="white-space:nowrap; width: 33%; text-align: center" class="xmyPink"><b><fmt:message key="oscar.billing.ca.on.billingON.codeTimePercent"/></b><br/>
                                        <c:forEach var="i" begin="6" end="11">
                                            <input type="text" name="serviceCode${i}" class="form-control form-control-sm d-inline-block w-auto"
                                                   value="<carlos:encode value='${formModel.requestParamEchoes[\"serviceCode\".concat(i)]}' context='htmlAttribute'/>"
                                                   onBlur="upCaseCtrl(this)"/>x
                                            <input type="text" name="serviceUnit${i}" size="2" maxlength="2"
                                                   style="width: 20px;"
                                                   value="<carlos:encode value='${formModel.requestParamEchoes[\"serviceUnit\".concat(i)]}' context='htmlAttribute'/>"/>@
                                            <input type="text" name="serviceAt${i}" size="3" maxlength="4"
                                                   style="width: 30px"
                                                   value="<carlos:encode value='${formModel.requestParamEchoes[\"serviceAt\".concat(i)]}' context='htmlAttribute'/>"/><br/>
                                        </c:forEach></td>
                                </tr>
                            </table>
                        </td>
                        <td style="vertical-align:top">
                            <table class="border1 table-hover" style="width: 100%; border-spacing:2px;">
                                <tr>
                                    <td style="white-space:nowrap; width: 30%"><b><fmt:message key="oscar.billing.ca.on.billingON.billingPhysician"/></b></td>
                                    <td style="width: 20%">
                                        <%-- Multisite + non-multisite provider pickers fully driven
                                             by formModel; SiteDao + Provider iteration done in the
                                             assembler. The pre-rendered <option> HTML for the
                                             multisite per-site picker lives in
                                             ${formModel.multisiteProviderHtml} keyed by site name. --%>
                                        <c:choose>
                                            <c:when test="${formModel.multisiteEnabled}">
                                                <script>
                                                    var _providers = [];
                                                    <c:forEach var="msite" items="${formModel.multisiteSites}">
                                                        _providers["<carlos:encode value='${msite.name}' context='javaScript'/>"] = "<carlos:encode value='${formModel.multisiteProviderHtml[msite.name]}' context='javaScript'/>";
                                                    </c:forEach>
                                                    function changeSite(sel) {
                                                        sel.form.xml_provider.innerHTML = sel.value == "none" ? "" : _providers[sel.value];
                                                        sel.style.backgroundColor = sel.options[sel.selectedIndex].style.backgroundColor;
                                                    }
                                                </script>
                                                <select id="site" name="site" onchange="changeSite(this)">
                                                    <option value="none" style="background-color: white"><fmt:message key="oscar.billing.ca.on.billingON.selectClinic"/></option>
                                                    <c:forEach var="msite" items="${formModel.multisiteSites}">
                                                        <option value="<carlos:encode value='${msite.name}' context='htmlAttribute'/>"
                                                                style="background-color:<carlos:encode value='${msite.bgColor}' context='cssString'/>"
                                                                ${msite.name eq formModel.defaultSelectedSite ? 'selected' : ''}>
                                                            <carlos:encode value='${msite.name}' context='html'/>
                                                        </option>
                                                    </c:forEach>
                                                </select>
                                                <select id="xml_provider" name="xml_provider"></select>
                                                <script>
                                                    changeSite(document.getElementById("site"));
                                                    document.getElementById("xml_provider").value = '<carlos:encode value="${formModel.selectedXmlProvider}" context="javaScript"/>';
                                                </script>
                                            </c:when>
                                            <c:otherwise>
                                                <select name="xml_provider">
                                                    <c:choose>
                                                        <c:when test="${fn:length(formModel.providers) eq 1}">
                                                            <c:forEach var="po" items="${formModel.providers}">
                                                                <c:set var="__poPrefix" value="${fn:substringBefore(po.proOhip, '|')}"/>
                                                                <option value="<carlos:encode value='${po.proOhip}' context='htmlAttribute'/>"
                                                                        ${formModel.providerView eq fn:trim(__poPrefix) ? 'selected' : ''}>
                                                                    <b><carlos:encode value='${po.lastName}, ${po.firstName}' context='html'/></b>
                                                                </option>
                                                            </c:forEach>
                                                        </c:when>
                                                        <c:otherwise>
                                                            <option value="000000"
                                                                    ${formModel.providerView eq '000000' ? 'selected' : ''}>
                                                                <b><fmt:message key="oscar.billing.ca.on.billingON.selectProvider"/></b>
                                                            </option>
                                                            <c:forEach var="po" items="${formModel.providers}">
                                                                <c:set var="__poPrefix" value="${fn:substringBefore(po.proOhip, '|')}"/>
                                                                <option value="<carlos:encode value='${po.proOhip}' context='htmlAttribute'/>"
                                                                        ${fn:toLowerCase(formModel.providerView) eq fn:toLowerCase(__poPrefix) ? 'selected' : ''}>
                                                                    <b><carlos:encode value='${po.lastName}, ${po.firstName}' context='html'/></b>
                                                                </option>
                                                            </c:forEach>
                                                        </c:otherwise>
                                                    </c:choose>
                                                </select>
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                    <td style="white-space:nowrap; width: 30%"><b><fmt:message key="oscar.billing.ca.on.billingON.assignedPhysician"/></b></td>
                                    <td style="width: 20%"><carlos:encode value='${formModel.assgProviderDisplay}' context='html'/>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="width: 30%"><b>
                                        <c:choose>
                                            <c:when test="${formModel.rmaEnabled}">
                                                <fmt:message key="oscar.billing.ca.on.billingON.clinicNbr"/>
                                            </c:when>
                                            <c:otherwise>
                                                <fmt:message key="billing.billingCorrection.formVisitType"/>
                                            </c:otherwise>
                                        </c:choose>
                                    </b></td>
                                    <td style="width: 20%"><select name="xml_visittype" onchange="updateDate()">
                                        <c:choose>
                                            <c:when test="${formModel.rmaEnabled}">
                                                <%-- clinic-nbr dropdown driven by pre-loaded
                                                     formModel.clinicNbrs (replaces ClinicNbrDao.findAll +
                                                     ProviderDao.getProvider/comments XML inline calls). --%>
                                                <c:forEach var="__c" items="${formModel.clinicNbrs}">
                                                    <option value="<carlos:encode value='${__c.displayLabel}' context='htmlAttribute'/>"
                                                            ${fn:startsWith(formModel.selectedClinicNbrPrefix, __c.nbrValue) ? 'selected' : ''}>
                                                        <carlos:encode value='${__c.displayLabel}' context='html'/>
                                                    </option>
                                                </c:forEach>
                                            </c:when>
                                            <c:otherwise>
                                        <option value="00| Clinic Visit"
                                                ${fn:startsWith(formModel.visitType, '00') ? 'selected' : ''}><fmt:message key="oscar.billing.ca.on.billingON.visitType.00"/>
                                        </option>
                                        <option value="01| Outpatient Visit"
                                                ${fn:startsWith(formModel.visitType, '01') ? 'selected' : ''}><fmt:message key="oscar.billing.ca.on.billingON.visitType.01"/>
                                        </option>
                                        <option value="02| Hospital Visit"
                                                ${fn:startsWith(formModel.visitType, '02') ? 'selected' : ''}><fmt:message key="oscar.billing.ca.on.billingON.visitType.02"/>
                                        </option>
                                        <option value="03| ER"
                                                ${fn:startsWith(formModel.visitType, '03') ? 'selected' : ''}><fmt:message key="oscar.billing.ca.on.billingON.visitType.03"/>
                                        </option>
                                        <option value="04| Nursing Home"
                                                ${fn:startsWith(formModel.visitType, '04') ? 'selected' : ''}><fmt:message key="oscar.billing.ca.on.billingON.visitType.04"/>
                                        </option>
                                        <option value="05| Home Visit"
                                                ${fn:startsWith(formModel.visitType, '05') ? 'selected' : ''}><fmt:message key="oscar.billing.ca.on.billingON.visitType.05"/>
                                        </option>
                                            </c:otherwise>
                                        </c:choose>
                                    </select></td>
                                    <td style="width: 30%"><b><fmt:message key="oscar.billing.ca.on.billingON.billingType"/></b></td>
                                    <td style="width: 20%">
                                        <%-- selectedBillType pre-resolved by the assembler:
                                             roster QU/FS forces PAT (when defaultServiceType != RN),
                                             then request param overrides. --%>
                                        <select name="xml_billtype" onchange="onChangePrivate();">
                                        <option value="ODP | Bill OHIP"
                                                ${fn:startsWith(formModel.selectedBillType, "ODP") ? "selected" : ""}><fmt:message key="oscar.billing.ca.on.billingON.billType.ODP"/>
                                        </option>
                                        <option value="WCB | Worker's Compensation Board"
                                                ${fn:startsWith(formModel.selectedBillType, "WCB") ? "selected" : ""}><fmt:message key="oscar.billing.ca.on.billingON.billType.WCB"/>
                                        </option>
                                        <option value="NOT | Do Not Bill"
                                                ${fn:startsWith(formModel.selectedBillType, "NOT") ? "selected" : ""}><fmt:message key="oscar.billing.ca.on.billingON.billType.NOT"/>
                                        </option>
                                        <option value="IFH | Interm Federal Health"
                                                ${fn:startsWith(formModel.selectedBillType, "IFH") ? "selected" : ""}><fmt:message key="oscar.billing.ca.on.billingON.billType.IFH"/>
                                        </option>
                                        <option value="PAT | Bill Patient"
                                                ${fn:startsWith(formModel.selectedBillType, "PAT") ? "selected" : ""}><fmt:message key="oscar.billing.ca.on.billingON.billType.PAT"/>
                                        </option>
                                        <option value="OCF | "
                                                ${fn:startsWith(formModel.selectedBillType, "OCF") ? "selected" : ""}><fmt:message key="oscar.billing.ca.on.billingON.billType.OCF"/>
                                        </option>
                                        <option value="ODS | "
                                                ${fn:startsWith(formModel.selectedBillType, "ODS") ? "selected" : ""}><fmt:message key="oscar.billing.ca.on.billingON.billType.ODS"/>
                                        </option>
                                        <option value="CPP | Canada Pension Plan"
                                                ${fn:startsWith(formModel.selectedBillType, "CPP") ? "selected" : ""}><fmt:message key="oscar.billing.ca.on.billingON.billType.CPP"/>
                                        </option>
                                        <option
                                                value="STD | Short Term Disability / Long Term Disability"
                                                ${fn:startsWith(formModel.selectedBillType, "STD") ? "selected" : ""}><fmt:message key="oscar.billing.ca.on.billingON.billType.STD"/>
                                        </option>
                                        <option value="BON | Bonus Codes"
                                                ${fn:startsWith(formModel.selectedBillType, "BON") ? "selected" : ""}><fmt:message key="oscar.billing.ca.on.billingON.billType.BON"/>
                                        </option>
                                    </select>
                                    </td>
                                </tr>
                                <tr>
                                    <td><b><fmt:message key="oscar.billing.ca.on.billingON.visitLocation"/></b></td>
                                    <td colspan="3"><select name="xml_location">
                                        <c:forEach var="loc" items="${formModel.facilityNumOptions}">
                                            <option value="<carlos:encode value='${loc.code}|${loc.label}' context='htmlAttribute'/>"
                                                    ${fn:startsWith(formModel.defaultLocation, loc.code) ? 'selected' : ''}>
                                                <carlos:encode value='${loc.label}' context='html'/>
                                            </option>
                                        </c:forEach>
                                    </select><fmt:message key="oscar.billing.ca.on.billingON.manualReviewFlag"/> <input type="checkbox" name="m_review" value="Y"
                                        ${formModel.MReview eq 'Y' ? 'checked' : ''}></td>
                                </tr>
                                <tr>
                                    <td><b><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode"/></b></td>
                                    <td colspan="3"><select name="xml_slicode">
                                        <option value="<carlos:encode value='${formModel.clinicNo}' context='htmlAttribute'/>">
                                            <fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.NA"/>
                                        </option>
                                        <option value="HDS">
                                            <fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HDS"/>
                                        </option>
                                        <option value="HED">
                                            <fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HED"/>
                                        </option>
                                        <option value="HIP">
                                            <fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HIP"/>
                                        </option>
                                        <option value="HOP">
                                            <fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HOP"/>
                                        </option>
                                        <option value="HRP">
                                            <fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HRP"/>
                                        </option>
                                        <option value="IHF">
                                            <fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.IHF"/>
                                        </option>
                                        <option value="OFF">
                                            <fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.OFF"/>
                                        </option>
                                        <option value="OTN">
                                            <fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.OTN"/>
                                        </option>
                                        <option value="PDF">
                                            <fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.PDF"/>
                                        </option>
                                        <option value="RTF">
                                            <fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.RTF"/>
                                        </option>
                                    </select></td>
                                </tr>
                                <tr>
                                    <td><b><fmt:message key="oscar.billing.ca.on.billingON.admissionDate"/></b></td>
										<td>
                                        <%-- xml_vdate default resolved by the assembler:
                                             request param > admissionDate (from
                                             oscarVariables.inPatient + DemographicData
                                             with the visit-type 02/04 history fallback). --%>
											<div class="input-group input-group-sm">
											    <input type="text" name="xml_vdate" id="xml_vdate" onchange="getDays();"
                                               value="<carlos:encode value='${formModel.defaultXmlVdate}' context='htmlAttribute'/>"
											class="form-control" readonly>
											<button type="button" class="btn btn-outline-secondary" id="xml_vdate_cal" title="<fmt:message key="oscar.billing.ca.on.billingON.chooseDate"/>">
											    <img alt="cal" style="height:14px;"
											         src="${ pageContext.request.contextPath }/images/cal.gif"></button>
											</div>
                                            <span id="duration_display"></span>
                                    </td>
                                    <td colspan="2"><a href="javascript:void(0);"
                                                       onclick="showHideLayers('Layer1','','show');return false;">
                                        <fmt:message key="oscar.billing.ca.on.billingON.billingFormLink"/></a>: <input type="text" name="billFormName" class="form-control"
											id="billFormName" readonly
                                                                 value="${carlos:forHtmlAttribute(fn:length(formModel.defaultBillFormName) lt 40 ? formModel.defaultBillFormName : fn:substring(formModel.defaultBillFormName, 0, 40))}"/>
                                        <input type="hidden" name="billForm" id="billForm"
                                               value="<carlos:encode value='${formModel.ctlBillForm}' context="htmlAttribute"/>"/></td>
                                </tr>
                                <%-- Legacy non-multisite "site" dropdown (BillingSiteIdPrep +
                                     JdbcApptImpl.getLocationFromSchedule) now driven by
                                     formModel.legacySiteContextEnabled / legacySiteOptions. --%>
                                <c:if test="${formModel.legacySiteContextEnabled}">
                                <tr>
                                    <td style="text-align: right"><fmt:message key="oscar.billing.ca.on.billingON.site"/></td>
                                    <td colspan="3"><select name="siteId">
                                        <c:forEach var="site" items="${formModel.legacySiteOptions}">
                                            <option value="<carlos:encode value='${site.name}' context='htmlAttribute'/>"
                                                    ${site.suggested ? 'selected' : ''}>
                                                <b><carlos:encode value='${site.name}' context='html'/></b>
                                            </option>
                                        </c:forEach>
                                    </select></td>
                                </tr>
                                </c:if>

                            </table>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td>
                <table style="width: 100%; height:137px">
                    <tr>
                        <td style="vertical-align:top; width: 33%">
                            <c:forEach var="st" items="${formModel.listServiceType}">
                            <c:set var="g1Key" value="group1_${st}"/>
                            <div id="${g1Key}" style="display: none;">
                                <table style="width: 100%;" class="border1 table-striped table-hover">
                                    <tr>
                                        <th style="width: 10%; white-space:nowrap; background-color:silver">
                                            <div class="smallFont"><carlos:encode value='${formModel.titleMap[g1Key]}' context='html'/>
                                            </div>
                                        </th>
                                        <th style="width: 70%; background-color:silver">
                                            <div class="smallFont"><fmt:message key="oscar.billing.ca.on.billingON.description"/></div>
                                        </th>
                                        <th style="background-color:silver">
                                            <div class="smallFont"><fmt:message key="oscar.billing.ca.on.billingON.fee"/></div>
                                        </th>
                                    </tr>
                                    <c:forEach var="entry" items="${formModel.billingServiceCodesMap[g1Key]}" varStatus="rowLoop">
                                    <c:set var="xmlParamKey" value="xml_${entry.serviceCode}"/>
                                    <c:set var="bgcolor" value="${not empty param[xmlParamKey] ? 'background-color: #66FF66;' : ''}"/>
                                    <tr>
                                        <td style="${entry.displayStyle} text-align: left; white-space:nowrap; ${bgcolor}">
                                            <input
                                                    type="checkbox" id="xml_${entry.serviceCode}"
                                                    name="xml_${entry.serviceCode}" value="checked"
                                                    onclick="refreshServicesChecked(this);"
                                                    <c:if test="${param[xmlParamKey] eq 'checked'}">checked</c:if>
                                                    <c:if test="${formModel.singleClickEnabled}">onClick='onClickServiceCode(this)'</c:if> />
                                            <span id="sc${rowLoop.index}${entry.serviceCode}"
                                                  onclick="getElementById('xml_${entry.serviceCode}').click();"
                                                  ondblclick="onDblClickServiceCode(this)"><carlos:encode value='${entry.serviceCode}' context='html'/></span>
                                        </td>
                                        <td style="${entry.displayStyle} ${bgcolor}"
                                                title="${carlos:forHtmlAttribute(fn:length(entry.serviceDesc) gt 30 ? entry.serviceDesc : '')}"
                                                class="${entry.displayStyle eq '' ? 'smallFont' : ''}">
                                            <div onclick="getElementById('xml_${entry.serviceCode}').click();"><c:choose><c:when test="${fn:length(entry.serviceDesc) gt 30}"><carlos:encode value="${fn:substring(entry.serviceDesc, 0, 30)}"/>...</c:when><c:otherwise><carlos:encode value="${entry.serviceDesc}"/></c:otherwise></c:choose>
                                            </div>
                                        </td>
                                        <td style="text-align: right; ${entry.displayStyle} ${bgcolor}">
                                            <div class="smallFont"><carlos:encode value='${entry.serviceDisp}' context='html'/>
                                            </div>
                                            <input
                                                    type="hidden" name="sli_xml_${entry.serviceCode}"
                                                    value="${entry.sliFlag}"/>
                                        </td>
                                    </tr>
                                    </c:forEach>
                                </table>
                            </div>
                            </c:forEach>

                        </td>
                        <td style="width: 33%; vertical-align: top;">
                            <c:forEach var="st" items="${formModel.listServiceType}">
                            <c:set var="g2Key" value="group2_${st}"/>
                            <div id="${g2Key}" style="display: none;">
                                <table style="width: 100%;" class="border1 table-striped table-hover">
                                    <tr>
                                        <th style="width: 10%; white-space:nowrap; background-color:silver">
                                            <div class="smallFont"><carlos:encode value='${formModel.titleMap[g2Key]}' context='html'/>
                                            </div>
                                        </th>
                                        <th style="width: 70%; background-color:silver">
                                            <div class="smallFont"><fmt:message key="oscar.billing.ca.on.billingON.description"/></div>
                                        </th>
                                        <th style="background-color:silver">
                                            <div class="smallFont"><fmt:message key="oscar.billing.ca.on.billingON.fee"/></div>
                                        </th>
                                    </tr>
                                    <c:forEach var="entry" items="${formModel.billingServiceCodesMap[g2Key]}" varStatus="rowLoop">
                                    <c:set var="xmlParamKey" value="xml_${entry.serviceCode}"/>
                                    <c:set var="bgcolor" value="${not empty param[xmlParamKey] ? 'background-color: #66FF66;' : ''}"/>
                                    <tr>
                                        <td style="text-align: left; ${entry.displayStyle} white-space:nowrap; ${bgcolor}">
                                            <input
                                                    type="checkbox" id="xml_${entry.serviceCode}"
                                                    name="xml_${entry.serviceCode}" value="checked"
                                                    onclick="refreshServicesChecked(this);"
                                                    <c:if test="${param[xmlParamKey] eq 'checked'}">checked</c:if>
                                                    <c:if test="${formModel.singleClickEnabled}">onClick='onClickServiceCode(this)'</c:if> />
                                            <span id="sc${rowLoop.index}${entry.serviceCode}"
                                                  onclick="getElementById('xml_${entry.serviceCode}').click();"
                                                  onDblClick="onDblClickServiceCode(this)"><carlos:encode value='${entry.serviceCode}' context='html'/></span>
                                        </td>
                                        <td style="${entry.displayStyle} ${bgcolor}"
                                                title="${carlos:forHtmlAttribute(fn:length(entry.serviceDesc) gt 30 ? entry.serviceDesc : '')}"
                                                class="${entry.displayStyle eq '' ? 'smallFont' : ''}">
                                            <div onclick="getElementById('xml_${entry.serviceCode}').click();">
                                                <c:choose><c:when test="${fn:length(entry.serviceDesc) gt 30}"><carlos:encode value="${fn:substring(entry.serviceDesc, 0, 30)}"/>...</c:when><c:otherwise><carlos:encode value="${entry.serviceDesc}"/></c:otherwise></c:choose>
                                            </div>
                                        </td>
                                        <td style="text-align: right;${entry.displayStyle}  ${bgcolor}">
                                            <div class="smallFont"><carlos:encode value='${entry.serviceDisp}' context='html'/>
                                            </div>
                                            <input
                                                    type="hidden" name="sli_xml_${entry.serviceCode}"
                                                    value="${entry.sliFlag}"/>
                                        </td>
                                    </tr>
                                    </c:forEach>
                                </table>
                            </div>
                            </c:forEach>

                        </td>
                        <td style="width: 33%; vertical-align: top;">
                            <c:forEach var="st" items="${formModel.listServiceType}">
                            <c:set var="g3Key" value="group3_${st}"/>
                            <div id="${g3Key}" style="display: none;">
                                <table style="width: 100%;" class="border1 table-striped table-hover">
                                    <tr>
                                        <th style="width: 10%; white-space:nowrap; background-color:silver">
                                            <div class="smallFont"><carlos:encode value='${formModel.titleMap[g3Key]}' context='html'/>
                                            </div>
                                        </th>
                                        <th style="width: 70%; background-color:silver">
                                            <div class="smallFont"><fmt:message key="oscar.billing.ca.on.billingON.description"/></div>
                                        </th>
                                        <th style="background-color:silver">
                                            <div class="smallFont"><fmt:message key="oscar.billing.ca.on.billingON.fee"/></div>
                                        </th>
                                    </tr>
                                    <c:forEach var="entry" items="${formModel.billingServiceCodesMap[g3Key]}" varStatus="rowLoop">
                                    <c:set var="xmlParamKey" value="xml_${entry.serviceCode}"/>
                                    <c:set var="bgcolor" value="${not empty param[xmlParamKey] ? 'background-color: #66FF66;' : ''}"/>
                                    <tr>
                                        <td style="text-align: left; ${entry.displayStyle} white-space:nowrap; ${bgcolor}">
                                            <input
                                                    type="checkbox" id="xml_${entry.serviceCode}"
                                                    name="xml_${entry.serviceCode}" value="checked"
                                                    onclick="refreshServicesChecked(this);"
                                                    <c:if test="${param[xmlParamKey] eq 'checked'}">checked</c:if>
                                                    <c:if test="${formModel.singleClickEnabled}">onClick='onClickServiceCode(this)'</c:if> />
                                            <span id="sc${rowLoop.index}${entry.serviceCode}"
                                                  onclick="getElementById('xml_${entry.serviceCode}').click();"
                                                  onDblClick="onDblClickServiceCode(this)"><carlos:encode value='${entry.serviceCode}' context='html'/></span>
                                        </td>
                                        <td style="${entry.displayStyle} ${bgcolor} "
                                                title="${carlos:forHtmlAttribute(fn:length(entry.serviceDesc) gt 30 ? entry.serviceDesc : '')}"
                                                class="${entry.displayStyle eq '' ? 'smallFont' : ''}">
                                            <div onclick="getElementById('xml_${entry.serviceCode}').click();">
                                                <c:choose><c:when test="${fn:length(entry.serviceDesc) gt 30}"><carlos:encode value="${fn:substring(entry.serviceDesc, 0, 30)}"/>...</c:when><c:otherwise><carlos:encode value="${entry.serviceDesc}"/></c:otherwise></c:choose>
                                            </div>
                                        </td>
                                        <td style="text-align: right; ${entry.displayStyle}  ${bgcolor}">
                                            <div class="smallFont"><carlos:encode value='${entry.serviceDisp}' context='html'/>
                                            </div>
                                            <input
                                                    type="hidden" name="sli_xml_${entry.serviceCode}"
                                                    value="${entry.sliFlag}"/>
                                        </td>
                                    </tr>
                                    </c:forEach>
                                </table>
                            </div>
                            </c:forEach>

                        </td>
                    </tr>
                </table>
            </td>
        </tr>

        <input type="hidden" name="clinic_no" value="<carlos:encode value='${formModel.clinicNo}' context='htmlAttribute'/>"/>
        <input type="hidden" name="demographic_no" value="<carlos:encode value='${formModel.demographicNo}' context='htmlAttribute'/>"/>
        <input type="hidden" name="appointment_no" value="<carlos:encode value='${formModel.appointmentNo}' context='htmlAttribute'/>"/>

        <input type="hidden" name="ohip_version" value="V03G"/>
        <input type="hidden" name="hin" value="<carlos:encode value='${formModel.demoHin}' context='htmlAttribute'/>"/>
        <input type="hidden" name="ver" value="<carlos:encode value='${formModel.demoVer}' context='htmlAttribute'/>"/>
        <input type="hidden" name="hc_type" value="<carlos:encode value='${formModel.demoHcType}' context='htmlAttribute'/>"/>
        <input type="hidden" name="sex" value="<carlos:encode value='${formModel.demoSex}' context='htmlAttribute'/>"/>

        <input type="hidden" name="start_time"
               value="<carlos:encode value='${formModel.requestParamEchoes[\"start_time\"]}' context='htmlAttribute'/>"/>

        <input type="hidden" name="demographic_dob" value="<carlos:encode value='${formModel.demoDob}' context='htmlAttribute'/>"/>

        <input type="hidden" name="apptProvider_no"
               value="<carlos:encode value='${formModel.requestParamEchoes[\"apptProvider_no\"]}' context='htmlAttribute'/>"/>
        <input type="hidden" name="asstProvider_no"
               value="<carlos:encode value='${formModel.requestParamEchoes[\"asstProvider_no\"]}' context='htmlAttribute'/>"/>

        <input type="hidden" name="demographic_name" value="<carlos:encode value='${formModel.demoName}' context='htmlAttribute'/>"/>
        <input type="hidden" name="providerview" value="<carlos:encode value='${formModel.providerView}' context='htmlAttribute'/>"/>
        <input type="hidden" name="appointment_date"
               value="<carlos:encode value='${formModel.requestParamEchoes[\"appointment_date\"]}' context='htmlAttribute'/>"/>
        <input type="hidden" name="assgProvider_no"
               value="<carlos:encode value='${formModel.assgProviderNo}' context='htmlAttribute'/>"/>
        <%-- billForm is already declared at the form-name input above (line ~1573,
             id="billForm") — that field is the one toggleDiv() updates when the
             user switches forms. Keeping a second name="billForm" here would
             post two values for the same param, making the receiver dependent
             on parameter ordering. curBillForm tracks the original form. --%>
        <input type="hidden" name="curBillForm" value="<carlos:encode value='${formModel.ctlBillForm}' context='htmlAttribute'/>"/>
        <input type="hidden" name="services_checked">
        <input type="hidden" name="url_back">
        <input type="hidden" name="billNo_old" id="billNo_old"
               value="<carlos:encode value='${formModel.requestParamEchoes[\"billNo_old\"]}' context='htmlAttribute'/>"/>
        <input type="hidden" name="billStatus_old" id="billStatus_old"
               value="<carlos:encode value='${formModel.requestParamEchoes[\"billStatus_old\"]}' context='htmlAttribute'/>"/>

    </table>

    <table style="width: 100%; border-spacing:2px;"
    >
        <tr style="background-color: silver;">
            <td><carlos:encode value='${formModel.demoName}' context="html"/> - <b><fmt:message key="oscar.billing.ca.on.billingON.billingHistory"/></b> (last 5 records)</td>
            <td style="width: 20%; text-align: right"><fmt:message key="oscar.billing.ca.on.billingON.last"/> <input type="text"
                                                                  name="day" value="365" class="form-control form-control-sm d-inline-block w-auto"/> <fmt:message key="oscar.billing.ca.on.billingON.days"/>
                <input type="button"
                       name="buttonDay" value="<fmt:message key="oscar.billing.ca.on.billingON.go"/>" onClick="onHistory(); return false;"/>
            </td>
        </tr>
    </table>
</form>

<table style="width: 100%; border-spacing:2px;"
       class="myIvory">
    <tr>
        <td>
            <table class="border1"
                   class="table-striped" style="width: 100%;">
                <tr class="myYellow" style="text-align: center">
                    <th style="white-space:nowrap"><fmt:message key="oscar.billing.ca.on.billingON.history.serialNo"/></th>
                    <th style="white-space:nowrap"><fmt:message key="oscar.billing.ca.on.billingON.history.billingDate"/></th>
                    <th style="white-space:nowrap"><fmt:message key="oscar.billing.ca.on.billingON.history.apptAdmDate"/></th>
                    <th style="white-space:nowrap"><fmt:message key="oscar.billing.ca.on.billingON.history.serviceCode"/></th>
                    <th style="white-space:nowrap"><fmt:message key="oscar.billing.ca.on.billingON.history.dx"/></th>
                    <th><fmt:message key="oscar.billing.ca.on.billingON.history.createDate"/></th>
                </tr>
                <c:forEach var="row" items="${formModel.billingHistoryRows}">
                <tr style="text-align: center">
                    <td class="smallFont"><carlos:encode value='${row.id}' context='html'/></td>
                    <td class="smallFont"><carlos:encode value='${row.billingDate}' context='html'/></td>
                    <td class="smallFont"><carlos:encode value='${row.serviceDate}' context='html'/></td>
                    <td class="smallFont"><carlos:encode value='${row.serviceCode}' context='html'/></td>
                    <td class="smallFont"><carlos:encode value='${row.dx}' context='html'/></td>
                    <td class="smallFont"><carlos:encode value='${row.updateDate}' context='html'/></td>
                </tr>
                </c:forEach>
            </table>
        </td>
    </tr>
</table>

	<script>

    Calendar.setup({
        inputField: "xml_vdate",
        ifFormat: "%Y-%m-%d",
        showsTime: false,
        button: "xml_vdate_cal",
        singleClick: true,
        step: 1
    });
    <c:if test="${formModel.appointmentNo eq '0'}">
    Calendar.setup({
        inputField: "service_date",
        ifFormat: "%Y-%m-%d",
        showsTime: false,
        button: "service_date_cal",
        singleClick: true,
        step: 1
    });
    </c:if>

function getDays() {
    if (!document.getElementById("xml_vdate") || !document.getElementById("service_date")) { return; }
    if (document.getElementById("xml_vdate").value == "" || document.getElementById("service_date").value == "" ) { return; }

    let date_xml_vdate = new Date(document.getElementById("xml_vdate").value);
    let date_service_date = new Date(document.getElementById("service_date").value);

    // Convert dates to UTC timestamps
    let utc1 =
        Date.UTC(date_xml_vdate.getFullYear(), date_xml_vdate.getMonth(), date_xml_vdate.getDate());
    let utc2 =
        Date.UTC(date_service_date.getFullYear(), date_service_date.getMonth(), date_service_date.getDate());

    // Calculate the time difference in milliseconds
    let timeDiff = Math.abs(utc2 - utc1);

    // Convert milliseconds to days
    let daysDiff = Math.ceil(timeDiff / (1000 * 60 * 60 * 24));

	let display = " "+daysDiff+"d";

    if (daysDiff > 34){
        let weeksDiff = Math.floor(daysDiff/7);
        let remainder = daysDiff - (weeksDiff*7);
        display = " "+weeksDiff+"w "+remainder+"d";
    }

    // Display the result
    document.getElementById("duration_display").textContent = display;
}

</script>

</body>
</html>
