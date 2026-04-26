<!DOCTYPE html>
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
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONCorrectionDataAssembler" %>
<%-- errorPage routes JSP-render exceptions to errorpage.jsp, which calls
     ErrorPageLogger.logIfPresent so a render-time NPE doesn't disappear
     into a generic CARLOS Error 500 with no stack trace in catalina.out.
     Without this directive, a throw inside the scriptlet body or any
     <jsp:include> would fall through to the container default. --%>
<%@page import="io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONCorrectionRenderContextComposer" %>
<%@page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<%--
  Defensive model-resolver: ensures ${correctionModel} is set on the request
  even on the unlikely path where this JSP is reached without going through
  BillingCorrection2Action (e.g., a stray <jsp:forward> from an unguarded
  entry). The action's own _billing privilege check is duplicated here for
  parity: without it a future bypass would silently run the full
  PHI-touching assembler on an unauthenticated request. Mirrors billingON.jsp.
--%>
<%
    if (request.getAttribute("correctionModel") == null) {
        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                "billingONCorrection.jsp reached without correctionModel — re-running assembler defensively. "
                + "Caller should route through billing/CA/ON/BillingONCorrection.");
        LoggedInInfo __fallbackLii = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (__fallbackLii == null) {
            throw new SecurityException("billingONCorrection.jsp fallback: missing session");
        }
        io.github.carlos_emr.carlos.managers.SecurityInfoManager __secMgr;
        try {
            __secMgr = SpringUtils.getBean(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
        } catch (RuntimeException __springEx) {
            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error(
                    "billingONCorrection.jsp fallback: SecurityInfoManager bean lookup failed", __springEx);
            throw new SecurityException("billingONCorrection.jsp fallback: privilege check unavailable", __springEx);
        }
        // BillingCorrection2Action enforces _billing w (mutation gate); on the
        // fallback render path no mutation occurs from the JSP itself, so r is
        // sufficient.
        if (!__secMgr.hasPrivilege(__fallbackLii, "_billing", "r", null)) {
            throw new SecurityException("billingONCorrection.jsp fallback: missing required sec object (_billing)");
        }
        request.setAttribute("correctionModel",
                new BillingONCorrectionDataAssembler().assemble(__fallbackLii, request));
    }
%>

<html>
    <head>
        <title><fmt:message key="billing.billingCorrection.title"/></title>

        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>

        <script src="${pageContext.request.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.js"></script>

        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.css" rel="stylesheet">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/fontawesome-all.min.css">

        <oscar:customInterface section="editInvoice"/>

        <script>
            <!--

            function setfocus() {
                document.form1.billing_no.focus();
                document.form1.billing_no.select();
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


            var awnd = null;

            function ScriptAttach() {
                f0 = escape(document.forms[1].xml_diagnostic_detail.value);
                f1 = document.forms[1].xml_dig_search1.value;
                // f2 = escape(document.serviceform.elements["File2Data"].value);
                // fname = escape(document.Compose.elements["FName"].value);
                awnd = rs('att', '${pageContext.request.contextPath}/billing/CA/ON/ViewBillingDigSearch?name=' + f0 + '&search=' + f1, 600, 600, 1);
                awnd.focus();
            }

            function referralScriptAttach2(elementName, name2) {
                var d = elementName;
                t0 = escape("document.forms[1].elements[\'" + d + "\'].value");
                t1 = escape("document.forms[1].elements[\'" + name2 + "\'].value");
                awnd = rs('att', ('${pageContext.request.contextPath}/billing/CA/ON/ViewSearchRefDoc?param=' + t0 + '&param2=' + t1), 600, 600, 1);
                awnd.focus();
            }

            function scScriptAttach(nameF) {
                f0 = document.forms[1].elements[nameF].value;
                f1 = escape("document.forms[1].elements[\'" + nameF + "\'].value");
                awnd = rs('att', '${pageContext.request.contextPath}/billing/CA/ON/ViewBillingCodeSearch?name=' + f0 + '&search=&name1=&name2=&nameF=' + f1, 600, 600, 1);
                awnd.focus();
            }

            function search3rdParty(elementName) {
                var d = elementName;
                t0 = escape("document.forms[1].elements[\'" + d + "\'].value");
                popupPage('600', '700', '${pageContext.request.contextPath}/billing/CA/ON/ViewOnSearch3rdBillAddr?param=' + t0);
            }

            function validateNum(el) {
                var val = el.value;
                var tval = "" + val;

                if (isNaN(val)) {
                    alert("Item value must be numeric.");
                    el.select();
                    el.focus();
                    return false;
                }
                if (val > 999999.99) {
                    alert("Item value must be below $1000000");
                    el.select();
                    el.focus();
                    return false;
                }
                decLen = tval.indexOf(".");
                if (decLen != -1 && (tval.length - decLen) > 3) {
                    alert("Item value has a maximum of 2 decimal places");
                    el.select();
                    el.focus();
                    return false;
                }

                return true;
            }

            function validateAllItems() {

                var provider = document.getElementById("provider_no");
                if (provider.options[provider.selectedIndex].value == "") {
                    alert("Billing providers must be set");
                    return false;
                }

                var billamt;
                for (idx = 0; idx < 6; ++idx) {
                    billamt = document.getElementById("billingamount" + idx);
                    if (billamt != undefined && !validateNum(billamt)) {
                        return false;
                    }
                }

                var statusOpts = document.getElementById("status");
                var status = statusOpts.options[statusOpts.selectedIndex].value;
                var payPrgrmOpts = document.getElementById("payProgram");
                var payPrgrm = payPrgrmOpts.options[payPrgrmOpts.selectedIndex].value;
                var is3rdParty = true;
                if (payPrgrm == "HCP" || payPrgrm == "RMB" || payPrgrm == "WCB") {
                    is3rdParty = false;
                }
                if (status == "P" && !is3rdParty) {
                    alert("Pay Program does not match bill status.");
                    return false;
                }
                /*
                var outstandingAmt = document.getElementById("outstandingBalance").value;

                if ((outstandingAmt != "0.00") && (status == "S" && is3rdParty)) {
                    if(!confirm('Warning: Settling this invoice will also settle the outstanding balance as paid. Continue?')){
                       return false;
                    }
                }*/


                var billingDate = document.getElementById("xml_appointment_date").value;
                var billingDt = parseDate(billingDate);
                if (billingDt != null) {
                    if (billingDt > new Date()) {
                        alert('Billing date cannot be in the future');
                        return false;
                    }
                }


                return true;
            }

            function parseDate(str) {
                if (str.length != 10) {
                    return null;
                }

                var y = str.substr(0, 4);
                var m = str.substr(5, 2) - 1;
                var d = str.substr(8, 2);
                var D = new Date(y, m, d);
                return (D.getFullYear() == y && D.getMonth() == m && D.getDate() == d) ? D : null;
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

            function sanityCheck(id, billNoErr) {
                if (id != "" && !billNoErr) {
                    location.href = "${pageContext.request.contextPath}/billing/CA/ON/ViewBillingON3rdInv?billingNo=" + id;
                } else {
                    alert("Please search a valid invoice number");
                }
                return false;
            }

            function checkPayProgram(payProgram) {
                //enable 3rd party elements
                if (payProgram == 'PAT' || payProgram == 'OCF' || payProgram == 'ODS' || payProgram == 'CPP' || payProgram == 'STD' || payProgram == 'IFH') {
                    document.getElementById("thirdParty").style.display = "inline";
                    document.getElementById("thirdPartyPymnt").style.display = "inline";

                    document.getElementById("billTo").disabled = false;
                } else {
                    document.getElementById("thirdParty").style.display = "none";
                    document.getElementById("thirdPartyPymnt").style.display = "none";

                    document.getElementById("billTo").disabled = true;
                }
            }

            function checkSettle(status) {
                if (status == 'S') {
                    var payElem = document.getElementById("payment");
                    if (payElem != null) {
                        payElem.value = document.getElementById("billTotal").value;
                    }
                } else if (status == 'P') {
                    document.getElementById("thirdParty").style.display = "inline";
                    //document.getElementById("thirdPartyPymnt").style.display = "inline";

                    document.getElementById("payment").disabled = false;
                    if (document.getElementById("oldPayment") != null) {
                        document.getElementById("oldPayment").disabled = false;
                    }
                    document.getElementById("payDate").disabled = false;
                    document.getElementById("refund").disabled = false;
                    document.getElementById("billTo").disabled = false;
                } else {
                    document.getElementById("thirdParty").style.display = "none";
                    document.getElementById("thirdPartyPymnt").style.display = "none";

                    document.getElementById("payment").disabled = true;
                    if (document.getElementById("oldPayment") != null) {
                        document.getElementById("oldPayment").disabled = true;
                    }
                    document.getElementById("payDate").disabled = true;
                    document.getElementById("refund").disabled = true;
                    document.getElementById("billTo").disabled = true;
                }

            }

            function validateAmountNumberic(idx) {
                var oldVal = document.getElementById("billingamounttmp" + idx).value;
                var val = document.getElementById("billingamount" + idx).value;
                if (val.length == 0) {
                    if (document.getElementsByName("servicecode" + idx)[0].value.trim().length > 0) {
                        document.getElementById("billingamount" + idx).value = " ";
                    }
                    return;
                }
                //var regexNumberic = /^([1-9]\d*|0)(\.\d{1,2})?$/;
                var regexNumberic = /^([1-9]\d{0,9}|0)(\.\d{1,2})?$/;
                if (!regexNumberic.test(val)) {
                    document.getElementById("billingamount" + idx).value = oldVal;
                    alert("Please enter digital numbers !");
                    return;
                }
                oldVal = val;
            }

            //-->
        </script>

    </head>

    <body onload="setfocus();">

    <h3><fmt:message key="admin.admin.btnBillingCorrection"/></h3>

    <div class="container-fluid">

        <c:if test="${not empty correctionModel.requestParamEchoes['adminSubmit']}">
        <div class="alert alert-success" id="alert_message">
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            <strong>Success! </strong> Your entry was saved!
        </div>
        </c:if>

        <c:if test="${correctionModel.billNoErr}">
        <div class="alert alert-danger" id="alert_message">
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            <strong>Error! </strong> Invoice number does not exist!
        </div>
        </c:if>

        <%-- Banners for the demoLoadError / raLookupError flags set by
             BillingONCorrectionDataAssembler.loadBillRecord. Without these,
             the operator might mistake an empty patient context or empty
             claimNo for authoritative data. --%>
        <c:if test="${correctionModel.demoLoadError}">
        <div class="alert alert-warning" role="alert">
            <strong>Warning:</strong> The patient demographic could not be
            loaded for this bill. Patient name, DOB, sex, HIN, and roster
            status are unavailable below &mdash; do <em>not</em> rely on the empty
            fields as authoritative. The system administrator has been
            notified via the server log; please retry shortly.
        </div>
        </c:if>
        <c:if test="${correctionModel.raLookupError}">
        <div class="alert alert-warning" role="alert">
            <strong>Warning:</strong> The OHIP RA claim number lookup failed
            for this bill. The claim number field below may be empty &mdash;
            cross-reference with ministry remittance is unavailable until
            the lookup is restored. The system administrator has been
            notified via the server log.
        </div>
        </c:if>

        <div class="row card card-body bg-body-tertiary">
            <c:if test="${not empty correctionModel.createTimestamp}">
            <fmt:message key="billing.billingCorrection.msgLastUpdate"/>: <carlos:encode value="${correctionModel.createTimestamp}" context="html"/>
            </c:if>

            <c:set var="__formAction" value="billingONCorrection.jsp${not empty correctionModel.requestParamEchoes['admin'] ? '?admin' : ''}"/>
            <form name="form1" method="post"
                  action="${carlos:forHtmlAttribute(__formAction)}">
                <input type="hidden" id="billTotal" value="${carlos:forHtmlAttribute(correctionModel.billTotal)}"/>

                <div class="col-md-2">
                    <a href="#" onclick="return sanityCheck('${carlos:forJavaScriptAttribute(correctionModel.billingNo)}', ${correctionModel.billNoErr});"><fmt:message key="billing.billingCorrection.formInvoiceNo"/></a><br>
                    <input type="text" id="billing_no" name="billing_no" value="${carlos:forHtmlAttribute(correctionModel.billingNo)}" class="col-md-2"
                           required>
                </div>


                <div class="col-md-2">
                    OHIP Claim No <br>
                    <input type="text" name="claim_no" value="${carlos:forHtmlAttribute(correctionModel.claimNo)}" class="col-md-2">
                </div>

                <div class="col-md-2">
                    <br>
                    <input class="btn btn-primary" type="submit" name="submit" value="Search">
                </div>

            </form>
        </div><!-- /well -->


        <!-- RA error -->
        <c:set var="__bFlag" value="${correctionModel.billLoaded or correctionModel.billNoErr or not empty correctionModel.billingNo}"/>
        <c:if test="${__bFlag and not empty correctionModel.errorReportEntries}">
        <table>
            <c:forEach var="__err" items="${correctionModel.errorReportEntries}">
            <tr>
                <th style="width:10%"><b><carlos:encode value="${__err.code}" context="html"/></b></th>
                <td style="text-align:left"><carlos:encode value="${__err.description}" context="html"/></td>
            </tr>
            </c:forEach>
        </table>
        </c:if>


        <%-- Form posts to UpdateBillingONCorrection2Action (POST-only).
             The legacy URL was /BillingONCorrection with a hidden
             method=updateInvoice param; the new sibling action makes the
             update workflow its own URL endpoint, removing the
             string-switch dispatch on the action class. --%>
        <form action="${pageContext.request.contextPath}/billing/CA/ON/UpdateBillingONCorrection" method="post">
            <input type="hidden" name="xml_billing_no" value="${carlos:forHtmlAttribute(correctionModel.billingNo)}"/>
            <input type="hidden" name="update_date" value="${carlos:forHtmlAttribute(correctionModel.createTimestamp)}"/>
            <input type="hidden" name="payDate" value=""/>
            <input type="hidden" name="demoNo" value="${carlos:forHtmlAttribute(correctionModel.demoNo)}"/>
            <input type="hidden" name="oldStatus" value="${correctionModel.thirdParty ? 'thirdParty' : ''}"/>

            <div class="row card card-body bg-body-tertiary">
                <div class="col-md-10">
                    <table>
                        <tr>
                            <th style="text-align:left" colspan="2"><fmt:message key="billing.billingCorrection.msgPatientInformation"/></th>
                        </tr>
                        <tr>
                            <td style="width:54%"><fmt:message key="billing.billingCorrection.msgPatientName"/>: <a href=#
                                                                                         onclick="popupPage(720,860,'${carlos:forJavaScriptAttribute(pageContext.request.contextPath)}/demographic/DemographicEdit?demographic_no=${carlos:forJavaScriptAttribute(carlos:forUriComponent(correctionModel.demoNo))}');return false;">
                                <carlos:encode value="${correctionModel.demoName}" context="html"/>
                            </a> <input type="hidden" name="demo_name"
                                        value="${carlos:forHtmlAttribute(correctionModel.demoName)}"></td>
                            <td style="width:46%"><fmt:message key="billing.billingCorrection.formHealth"/>: <carlos:encode value="${correctionModel.hin}" context="html"/> <input
                                    type="hidden" name="xml_hin" value="${carlos:forHtmlAttribute(correctionModel.hin)}">
                                RS: <carlos:encode value="${correctionModel.demoRosterStatus}" context="html"/>
                            </td>
                        </tr>
                        <tr>
                            <td><fmt:message key="billing.billingCorrection.msgSex"/>:
                                <carlos:encode value="${correctionModel.demoSex}" context="html"/> <input type="hidden" name="demo_sex" value="${carlos:forHtmlAttribute(correctionModel.demoSex)}">
                                <input type="hidden" name="hc_sex" value="${carlos:forHtmlAttribute(correctionModel.hcSex)}"></td>
                            <td><fmt:message key="billing.billingCorrection.formDOB"/>:
                                <input type="hidden" name="xml_dob" value="${carlos:forHtmlAttribute(correctionModel.demoDob)}"> <carlos:encode value="${correctionModel.demoDob}" context="html"/>
                            </td>
                        </tr>
                        <tr>
                            <td><fmt:message key="billing.billingCorrection.msgDoctor"/>:<br>
                                <input type="text" name="rd" value="${carlos:forHtmlAttribute(correctionModel.referralDoctor)}" size=20 readonly>
                            </td>
                            <td>

                                <fmt:message key="billing.billingCorrection.msgDoctorNo"/>:
                                <div class="input-group">
                                    <input type="text" name="rdohip" value="${carlos:forHtmlAttribute(correctionModel.referralDoctorOhip)}" class="col-md-2" readonly/>
                                    <a href="javascript:referralScriptAttach2('rdohip','rd')" class="btn btn-secondary"><i
                                            class="fa-solid fa-magnifying-glass"></i></a>
                                </div>
                            </td>
                        </tr>
                    </table>
                </div><!--span-->
            </div>

            <div class="row card card-body bg-body-tertiary">
                <div class="col-md-10">

                    <strong><fmt:message key="billing.billingCorrection.msgAditInfo"/></strong><br>

                    <fmt:message key="billing.billingCorrection.formHCType"/>:
                    <select name="hc_type" style="font-size: 80%;">
                        <c:set var="__hc" value="${correctionModel.hcType}"/>
                        <option value="OT" ${__hc eq 'OT' ? 'selected' : ''}>OT-Other</option>
                        <option value="AB" ${__hc eq 'AB' ? 'selected' : ''}>AB-Alberta</option>
                        <option value="BC" ${__hc eq 'BC' ? 'selected' : ''}>BC-British Columbia</option>
                        <option value="MB" ${__hc eq 'MB' ? 'selected' : ''}>MB-Manitoba</option>
                        <option value="NB" ${__hc eq 'NB' ? 'selected' : ''}>NB-New Brunswick</option>
                        <option value="NF" ${__hc eq 'NF' ? 'selected' : ''}>NF-Newfoundland &amp; Labrador
                        </option>
                        <option value="NT" ${__hc eq 'NT' ? 'selected' : ''}>NT-Northwest Territory</option>
                        <option value="NS" ${__hc eq 'NS' ? 'selected' : ''}>NS-Nova Scotia</option>
                        <option value="NU" ${__hc eq 'NU' ? 'selected' : ''}>NU-Nunavut</option>
                        <option value="ON" ${__hc eq 'ON' ? 'selected' : ''}>ON-Ontario</option>
                        <option value="PE" ${__hc eq 'PE' ? 'selected' : ''}>PE-Prince Edward Island</option>
                        <option value="QC" ${__hc eq 'QC' ? 'selected' : ''}>QC-Quebec</option>
                        <option value="SK" ${__hc eq 'SK' ? 'selected' : ''}>SK-Saskatchewan</option>
                        <option value="YT" ${__hc eq 'YT' ? 'selected' : ''}>YT-Yukon</option>
                        <option value="US" ${__hc eq 'US' ? 'selected' : ''}>US resident</option>
                        <option value="US-AK" ${__hc eq 'US-AK' ? 'selected' : ''}>US-AK-Alaska</option>
                        <option value="US-AL" ${__hc eq 'US-AL' ? 'selected' : ''}>US-AL-Alabama</option>
                        <option value="US-AR" ${__hc eq 'US-AR' ? 'selected' : ''}>US-AR-Arkansas</option>
                        <option value="US-AZ" ${__hc eq 'US-AZ' ? 'selected' : ''}>US-AZ-Arizona</option>
                        <option value="US-CA" ${__hc eq 'US-CA' ? 'selected' : ''}>US-CA-California</option>
                        <option value="US-CO" ${__hc eq 'US-CO' ? 'selected' : ''}>US-CO-Colorado</option>
                        <option value="US-CT" ${__hc eq 'US-CT' ? 'selected' : ''}>US-CT-Connecticut</option>
                        <option value="US-CZ" ${__hc eq 'US-CZ' ? 'selected' : ''}>US-CZ-Canal Zone</option>
                        <option value="US-DC" ${__hc eq 'US-DC' ? 'selected' : ''}>US-DC-District Of
                            Columbia
                        </option>
                        <option value="US-DE" ${__hc eq 'US-DE' ? 'selected' : ''}>US-DE-Delaware</option>
                        <option value="US-FL" ${__hc eq 'US-FL' ? 'selected' : ''}>US-FL-Florida</option>
                        <option value="US-GA" ${__hc eq 'US-GA' ? 'selected' : ''}>US-GA-Georgia</option>
                        <option value="US-GU" ${__hc eq 'US-GU' ? 'selected' : ''}>US-GU-Guam</option>
                        <option value="US-HI" ${__hc eq 'US-HI' ? 'selected' : ''}>US-HI-Hawaii</option>
                        <option value="US-IA" ${__hc eq 'US-IA' ? 'selected' : ''}>US-IA-Iowa</option>
                        <option value="US-ID" ${__hc eq 'US-ID' ? 'selected' : ''}>US-ID-Idaho</option>
                        <option value="US-IL" ${__hc eq 'US-IL' ? 'selected' : ''}>US-IL-Illinois</option>
                        <option value="US-IN" ${__hc eq 'US-IN' ? 'selected' : ''}>US-IN-Indiana</option>
                        <option value="US-KS" ${__hc eq 'US-KS' ? 'selected' : ''}>US-KS-Kansas</option>
                        <option value="US-KY" ${__hc eq 'US-KY' ? 'selected' : ''}>US-KY-Kentucky</option>
                        <option value="US-LA" ${__hc eq 'US-LA' ? 'selected' : ''}>US-LA-Louisiana</option>
                        <option value="US-MA" ${__hc eq 'US-MA' ? 'selected' : ''}>US-MA-Massachusetts
                        </option>
                        <option value="US-MD" ${__hc eq 'US-MD' ? 'selected' : ''}>US-MD-Maryland</option>
                        <option value="US-ME" ${__hc eq 'US-ME' ? 'selected' : ''}>US-ME-Maine</option>
                        <option value="US-MI" ${__hc eq 'US-MI' ? 'selected' : ''}>US-MI-Michigan</option>
                        <option value="US-MN" ${__hc eq 'US-MN' ? 'selected' : ''}>US-MN-Minnesota</option>
                        <option value="US-MO" ${__hc eq 'US-MO' ? 'selected' : ''}>US-MO-Missouri</option>
                        <option value="US-MS" ${__hc eq 'US-MS' ? 'selected' : ''}>US-MS-Mississippi</option>
                        <option value="US-MT" ${__hc eq 'US-MT' ? 'selected' : ''}>US-MT-Montana</option>
                        <option value="US-NC" ${__hc eq 'US-NC' ? 'selected' : ''}>US-NC-North Carolina
                        </option>
                        <option value="US-ND" ${__hc eq 'US-ND' ? 'selected' : ''}>US-ND-North Dakota</option>
                        <option value="US-NE" ${__hc eq 'US-NE' ? 'selected' : ''}>US-NE-Nebraska</option>
                        <option value="US-NH" ${__hc eq 'US-NH' ? 'selected' : ''}>US-NH-New Hampshire
                        </option>
                        <option value="US-NJ" ${__hc eq 'US-NJ' ? 'selected' : ''}>US-NJ-New Jersey</option>
                        <option value="US-NM" ${__hc eq 'US-NM' ? 'selected' : ''}>US-NM-New Mexico</option>
                        <option value="US-NU" ${__hc eq 'US-NU' ? 'selected' : ''}>US-NU-Nunavut</option>
                        <option value="US-NV" ${__hc eq 'US-NV' ? 'selected' : ''}>US-NV-Nevada</option>
                        <option value="US-NY" ${__hc eq 'US-NY' ? 'selected' : ''}>US-NY-New York</option>
                        <option value="US-OH" ${__hc eq 'US-OH' ? 'selected' : ''}>US-OH-Ohio</option>
                        <option value="US-OK" ${__hc eq 'US-OK' ? 'selected' : ''}>US-OK-Oklahoma</option>
                        <option value="US-OR" ${__hc eq 'US-OR' ? 'selected' : ''}>US-OR-Oregon</option>
                        <option value="US-PA" ${__hc eq 'US-PA' ? 'selected' : ''}>US-PA-Pennsylvania</option>
                        <option value="US-PR" ${__hc eq 'US-PR' ? 'selected' : ''}>US-PR-Puerto Rico</option>
                        <option value="US-RI" ${__hc eq 'US-RI' ? 'selected' : ''}>US-RI-Rhode Island</option>
                        <option value="US-SC" ${__hc eq 'US-SC' ? 'selected' : ''}>US-SC-South Carolina
                        </option>
                        <option value="US-SD" ${__hc eq 'US-SD' ? 'selected' : ''}>US-SD-South Dakota</option>
                        <option value="US-TN" ${__hc eq 'US-TN' ? 'selected' : ''}>US-TN-Tennessee</option>
                        <option value="US-TX" ${__hc eq 'US-TX' ? 'selected' : ''}>US-TX-Texas</option>
                        <option value="US-UT" ${__hc eq 'US-UT' ? 'selected' : ''}>US-UT-Utah</option>
                        <option value="US-VA" ${__hc eq 'US-VA' ? 'selected' : ''}>US-VA-Virginia</option>
                        <option value="US-VI" ${__hc eq 'US-VI' ? 'selected' : ''}>US-VI-Virgin Islands
                        </option>
                        <option value="US-VT" ${__hc eq 'US-VT' ? 'selected' : ''}>US-VT-Vermont</option>
                        <option value="US-WA" ${__hc eq 'US-WA' ? 'selected' : ''}>US-WA-Washington</option>
                        <option value="US-WI" ${__hc eq 'US-WI' ? 'selected' : ''}>US-WI-Wisconsin</option>
                        <option value="US-WV" ${__hc eq 'US-WV' ? 'selected' : ''}>US-WV-West Virginia
                        </option>
                        <option value="US-WY" ${__hc eq 'US-WY' ? 'selected' : ''}>US-WY-Wyoming</option>
                    </select>


                    <fmt:message key="billing.billingCorrection.formManualReview"/>: <input type="checkbox"
                                                                                             name="m_review"
                                                                                             value="Y" ${correctionModel.manReview eq 'Y' ? 'checked' : ''} >
                </div><!--span-->
            </div>

            <div class="row card card-body bg-body-tertiary">

                <div class="col-md-10">
                    <b><fmt:message key="billing.billingCorrection.msgBillingInf"/></b><br>

                    <div class="col-md-4">

                        <div class="col-md-4" style="margin-left:0px;">
                            <label><fmt:message key="billing.billingCorrection.btnBillingDate"/>:</label>
                            <div class="input-group">
                                <input type="text" name="xml_appointment_date" id="xml_appointment_date"
                                       class="form-control" value="${carlos:forHtmlAttribute(correctionModel.billDate)}" style="width:90px"
                                       pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                                <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                            </div>
                        </div><!--cal col-md-2-->

                        <fmt:message key="billing.billingCorrection.formBillingType"/>:<br>
                        <input type="hidden" name="xml_status" value="${carlos:forHtmlAttribute(correctionModel.billStatus)}">
                        <c:set var="__bt" value="${correctionModel.billStatus}"/>
                        <select style="font-size: 80%;" id="status" name="status"
                                onchange="checkSettle(this.options[this.selectedIndex].value);">
                            <option value=""><fmt:message key="billing.billingCorrection.formSelectBillType"/></option>
                            <option value="H" ${__bt eq 'H' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeH"/></option>
                            <option value="O" ${__bt eq 'O' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeO"/></option>
                            <option value="P" ${__bt eq 'P' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeP"/></option>
                            <option value="N" ${__bt eq 'N' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeN"/></option>
                            <option value="W" ${__bt eq 'W' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeW"/></option>
                            <option value="B" ${__bt eq 'B' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeB"/></option>
                            <option value="S" ${__bt eq 'S' ? 'selected' : ''}>S
                                | Settled
                            </option>
                            <option value="X" ${__bt eq 'X' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeX"/></option>
                            <option value="D" ${__bt eq 'D' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeD"/></option>
                            <option value="I" ${__bt eq 'I' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeI"/></option>
                        </select><br>

                        Pay Program:<br>
                        <input type="hidden" name="xml_payProgram" value="${carlos:forHtmlAttribute(correctionModel.payProgram)}"/>
                        <select style="font-size: 80%;" id="payProgram" name="payProgram"
                                onchange="checkPayProgram(this.options[this.selectedIndex].value)">
                            <c:forEach var="__pt" items="${correctionModel.paymentTypes}">
                            <option value="${carlos:forHtmlAttribute(__pt.code)}"
                                    ${correctionModel.payProgram eq __pt.code ? 'selected' : ''}><carlos:encode value="${__pt.label}" context="html"/>
                            </option>
                            </c:forEach>
                        </select><br>
                        <fmt:message key="billing.billingCorrection.formBillingPhysician"/>: <br>

                        <%-- multisite start ========================================== --%>
                        <c:choose>
                        <c:when test="${correctionModel.multisites}">
                        <script>
                            var _providers = [];
                            <c:forEach var="__msite" items="${correctionModel.multisiteSites}">
                            _providers["${carlos:forJavaScript(__msite.name)}"] = "${carlos:forJavaScript(correctionModel.multisiteProviderHtml[__msite.name])}";
                            </c:forEach>

                            function changeSite(sel) {
                                sel.form.provider_no.innerHTML = sel.value == "none" ? "" : _providers[sel.value];
                                sel.style.backgroundColor = sel.options[sel.selectedIndex].style.backgroundColor;
                            }
                        </script>
                        <select id="site" name="site" style="font-size: 80%;" onchange="changeSite(this)">
                            <option value="none" style="background-color:white">---select clinic---</option>
                            <c:forEach var="__msite" items="${correctionModel.multisiteSites}">
                            <option value="${carlos:forHtmlAttribute(__msite.name)}"
                                    style="background-color:${carlos:forCssString(__msite.bgColor)}"
                                    ${__msite.name eq correctionModel.currentSite ? 'selected' : ''}><carlos:encode value="${__msite.name}" context="html"/>
                            </option>
                            </c:forEach>
                        </select>
                        <select id="provider_no" name="provider_no" style="font-size: 80%;width:140px"></select>
                        <script>
                            changeSite(document.getElementById("site"));
                            document.getElementById("provider_no").value = '${carlos:forJavaScript(correctionModel.billProvider)}';
                        </script>
                        </c:when>
                        <c:otherwise>
                        <select
                                id="provider_no" style="font-size: 80%;" name="provider_no">
                            <option value=""><fmt:message key="billing.billingCorrection.msgSelectProvider"/></option>
                            <c:forEach var="__po" items="${correctionModel.providerOptions}">
                            <option value="${carlos:forHtmlAttribute(__po.providerNo)}"
                                    ${correctionModel.billProvider eq __po.providerNo ? 'selected' : ''}><carlos:encode value="${__po.providerNo}" context="html"/> |
                                <carlos:encode value="${__po.lastName}" context="html"/>, <carlos:encode value="${__po.firstName}" context="html"/>
                            </option>
                            </c:forEach>
                        </select>
                        </c:otherwise>
                        </c:choose>
                        <%-- multisite end ========================================== --%>
                        <input type="hidden" name="xml_provider_no" value="${carlos:forHtmlAttribute(correctionModel.billProvider)}">
                    </div><!--span4-->

                    <div class="col-md-4">
                        <input type="hidden" name="xml_visitdate" value="${carlos:forHtmlAttribute(correctionModel.visitDate)}"/>
                        <div class="col-md-4" style="margin-left:0px;">
                            <label><fmt:message key="billing.billingCorrection.btnAdmissionDate"/>:</label>
                            <div class="input-group">
                                <input type="text" name="xml_vdate" id="xml_vdate" class="form-control"
                                       value="${carlos:forHtmlAttribute(correctionModel.visitDate)}"
                                       pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" style="width:90px"
                                       autocomplete="off"/>
                                <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                            </div>
                        </div><!--date span-->
                        <br>

                        <fmt:message key="billing.billingCorrection.formVisit"/>: <br>
                        <input type="hidden" name="xml_clinic_ref_code" value="${carlos:forHtmlAttribute(correctionModel.billLocationNo)}">
                        <select name="clinic_ref_code">
                            <option value=""><fmt:message key="billing.billingCorrection.msgSelectLocation"/></option>
                            <c:forEach var="__cl" items="${correctionModel.clinicLocations}">
                            <option value="${carlos:forHtmlAttribute(__cl.no)}"
                                    ${correctionModel.billLocationNo eq __cl.no ? 'selected' : ''}><carlos:encode value="${__cl.no}" context="html"/>
                                | <carlos:encode value="${__cl.name}" context="html"/>
                            </option>
                            </c:forEach>
                        </select><br>

                        <c:choose>
                        <c:when test="${correctionModel.rmaEnabled}"> Clinic
                        Nbr </c:when>
                        <c:otherwise> <fmt:message key="billing.billingCorrection.formVisitType"/> </c:otherwise>
                        </c:choose>: <br>

                        <input type="hidden" name="xml_visittype" value="${carlos:forHtmlAttribute(correctionModel.visitType)}">
                        <c:set var="__vt" value="${correctionModel.visitType}"/>
                        <select style="font-size: 80%;" name="visittype">
                            <option value=""><fmt:message key="billing.billingCorrection.msgSelectVisitType"/></option>
                            <c:choose>
                            <c:when test="${correctionModel.rmaEnabled}">
                            <c:forEach var="__cn" items="${correctionModel.clinicNbrs}">
                            <option value="${carlos:forHtmlAttribute(__cn.label)}" ${fn:startsWith(__vt, __cn.value) ? 'selected' : ''}><carlos:encode value="${__cn.label}" context="html"/>
                            </option>
                            </c:forEach>
                            </c:when>
                            <c:otherwise>
                            <option value="00" ${__vt eq '00' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formClinicVisit"/></option>
                            <option value="01" ${__vt eq '01' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formOutpatientVisit"/></option>
                            <option value="02" ${__vt eq '02' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formHospitalVisit"/></option>
                            <option value="03" ${__vt eq '03' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formER"/></option>
                            <option value="04" ${__vt eq '04' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formNursingHome"/></option>
                            <option value="05" ${__vt eq '05' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formHomeVisit"/></option>
                            </c:otherwise>
                            </c:choose>
                        </select><br>


                        <c:set var="__sli" value="${correctionModel.sliCode}"/>
                        <fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode"/>: <br>
                        <select name="xml_slicode">
                            <option value="${carlos:forHtmlAttribute(correctionModel.clinicNo)}"><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.NA"/></option>
                            <option value="HDS " ${fn:startsWith(__sli, 'HDS') ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HDS"/></option>
                            <option value="HED " ${fn:startsWith(__sli, 'HED') ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HED"/></option>
                            <option value="HIP " ${fn:startsWith(__sli, 'HIP') ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HIP"/></option>
                            <option value="HOP " ${fn:startsWith(__sli, 'HOP') ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HOP"/></option>
                            <option value="HRP " ${fn:startsWith(__sli, 'HRP') ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HRP"/></option>
                            <option value="IHF " ${fn:startsWith(__sli, 'IHF') ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.IHF"/></option>
                            <option value="OFF " ${fn:startsWith(__sli, 'OFF') ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.OFF"/></option>
                            <option value="OTN " ${fn:startsWith(__sli, 'OTN') ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.OTN"/></option>
                            <option value="PDF " ${fn:startsWith(__sli, 'PDF') ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.PDF"/></option>
                            <option value="RTF " ${fn:startsWith(__sli, 'RTF') ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.RTF"/></option>
                        </select>
                    </div><!--span4-->
                </div><!-- col-md-10 -->
            </div>
            <!--well-->


            <div class="row card card-body bg-body-tertiary">

                <table class="table table-striped table-hover">
                    <thead>
                    <tr>
                        <td colspan=2><b><fmt:message key="billing.billingCorrection.formServiceCode"/></b></td>
                        <th><b><fmt:message key="billing.billingCorrection.formDescription"/></b></th>
                        <th><b><fmt:message key="billing.billingCorrection.formUnit"/></b></th>
                        <th style="text-align:right"><b><fmt:message key="billing.billingCorrection.formFee"/></b></th>
                        <th>Settle</th>
                    </tr>
                    </thead>

                    <tbody>
                    <%-- Per-row line-item table; correctionModel.billItems is
                         pre-resolved by BillingONCorrectionRenderContextComposer.
                         Display at least MAXRECORDS (6) rows; pad with empty
                         rows if fewer items exist. --%>
                    <c:if test="${__bFlag and correctionModel.multiSiteProvider and not empty correctionModel.billItems}">
                    <c:set var="__maxRecs" value="${fn:length(correctionModel.billItems) > 6 ? fn:length(correctionModel.billItems) : 6}"/>
                    <c:forEach var="__i" begin="0" end="${__maxRecs - 1}" varStatus="__rowLoop">
                    <c:set var="__rowCount" value="${__rowLoop.index + 1}"/>
                    <c:set var="__rowZero" value="${__rowLoop.index}"/>
                    <c:choose>
                    <c:when test="${__rowLoop.index < fn:length(correctionModel.billItems)}">
                        <c:set var="__bi" value="${correctionModel.billItems[__rowLoop.index]}"/>
                        <c:set var="__sc" value="${__bi.serviceCode}"/>
                        <c:set var="__sd" value="${__bi.serviceDesc}"/>
                        <c:set var="__ba" value="${__bi.fee}"/>
                        <c:set var="__bu" value="${__bi.count}"/>
                        <c:set var="__is" value="${__bi.status}"/>
                    </c:when>
                    <c:otherwise>
                        <c:set var="__sc" value=""/>
                        <c:set var="__sd" value=""/>
                        <c:set var="__ba" value=""/>
                        <c:set var="__bu" value=""/>
                        <c:set var="__is" value=""/>
                    </c:otherwise>
                    </c:choose>
                    <tr>
                        <th style="width:25%"><input type="hidden"
                                                     name="xml_service_code${__rowCount}" value="${carlos:forHtmlAttribute(__sc)}">
                            <input type="text" style="width: 100%"
                                   name="servicecode${__rowZero}" value="${carlos:forHtmlAttribute(__sc)}"></th>
                        <td><a href=# onClick="scScriptAttach('servicecode${__rowZero}')">Search</a></td>
                        <th><carlos:encode value="${__sd}" context="html"/>
                        </th>
                        <th><input type="hidden" name="xml_billing_unit${__rowCount}"
                                   value="${carlos:forHtmlAttribute(__bu)}"> <input type="text"
                                                                    style="width: 100%"
                                                                    name="billingunit${__rowZero}"
                                                                    value="${carlos:forHtmlAttribute(__bu)}" size="5" maxlength="5">
                        </th>
                        <th style="text-align:right"><input type="hidden"
                                                            name="xml_billing_amount${__rowCount}"
                                                            value="${carlos:forHtmlAttribute(__ba)}">
                            <input type="text" style="width: 100%" size="5" maxlength="6"
                                   id="billingamount${__rowZero}" name="billingamount${__rowZero}"
                                   value="${carlos:forHtmlAttribute(__ba)}" onchange="javascript:validateNum(this)"></th>
                        <td style="text-align:center"><input type="checkbox"
                                                             name="itemStatus${__rowZero}"
                                                             id="itemStatus${__rowZero}"
                                                             value="S" ${__is}></td>
                    </tr>
                    </c:forEach>
                    </c:if>
                    </tbody>
                </table>
            </div>


            <div class="row card card-body bg-body-tertiary">
                <div class="col-md-10">

                    <b> <fmt:message key="billing.billingCorrection.formDiagnosticCode"/></b>
                    <br>

                    <%-- diagCode comes from the last bill item in the legacy
                         scriptlet (if any). The composer surfaces it as the
                         dx field on the last billItem. --%>
                    <c:set var="__diagCode" value=""/>
                    <c:forEach var="__bi" items="${correctionModel.billItems}">
                        <c:set var="__diagCode" value="${__bi.dx}"/>
                    </c:forEach>
                    <input type="hidden" name="xml_diagnostic_code" value="${carlos:forHtmlAttribute(__diagCode)}">
                    <input type="hidden" name="xml_dig_search1">

                    <div class="input-group">
                        <input type="text" name="xml_diagnostic_detail" value="${carlos:forHtmlAttribute(__diagCode)}" class="col-md-8">
                        <a href="javascript:ScriptAttach()" class="btn btn-secondary"><i class="fa-solid fa-magnifying-glass"></i></a>
                    </div>

                </div>
            </div>

            <div class="row card card-body bg-body-tertiary">
                <div class="col-md-10">

                    <c:if test="${correctionModel.canEditBilling}">
                    <c:choose>
                    <c:when test="${not empty correctionModel.requestParamEchoes['admin'] or not empty correctionModel.requestParamEchoes['adminSubmit']}">
                    <input type="hidden" name="adminSubmit" value="adminSubmit">
                    <input class="btn btn-primary" type="submit" name="submit" onclick="return validateAllItems();"
                           value="Save">
                    </c:when>
                    <c:otherwise>
                    <input class="btn btn-primary" type="submit" name="submit" onclick="return validateAllItems();"
                           value="Save">
                    <input class="btn btn-secondary" type="submit" name="submit" onclick="return validateAllItems();"
                           value="Save&Correct Another">
                    </c:otherwise>
                    </c:choose>
                    </c:if>

                    <c:if test="${not empty correctionModel.billingNo}">

                    <a id="reprintLink" onclick="return sanityCheck('${carlos:forJavaScriptAttribute(correctionModel.billingNo)}', ${correctionModel.billNoErr})" href="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingON3rdInv?billingNo=${carlos:forUriComponent(correctionModel.billingNo)}" class="btn btn-secondary"><i
                            class="fa-solid fa-print"></i> Reprint</a>
                    <a id="rebillLink"
                       onclick="document.querySelector(&quot;select[name='status']&quot;).value = 'O'; document.getElementsByName(&quot;submit&quot;)[1].click();"
                       class="btn btn-secondary">Rebill OHIP</a>
                    <a id="settleLink"
                       onclick="document.querySelector(&quot;select[name='status']&quot;).value = 'S';document.getElementsByName(&quot;submit&quot;)[1].click();"
                       class="btn btn-secondary">Settle All</a>
                    </c:if>


                    <br><br>

                    <div class="row">
                        <div class="col-md-5">
                            <fmt:message key="billing.billingCorrection.msgNotes"/>:<br>
                            <textarea name="comment" style="width:100%" rows=4><carlos:encode value="${correctionModel.comment}" context="html"/></textarea>
                            <c:if test="${correctionModel.dueDateAvailable}">
                            <br>
                            <!--
                    <fmt:message key="billing.billingCorrection.dueDate"/><img src="${pageContext.request.contextPath}/images/cal.gif" id="invoiceDueDate_cal" />
                    :<input type="text" maxlength="10" id="invoiceDueDate" name="invoiceDueDate" value="${carlos:forHtmlAttribute(correctionModel.dueDateString)}"/>
                    -->
                            <div class="col-md-2">
                                <label><fmt:message key="billing.billingCorrection.dueDate"/>:</label>
                                <div class="input-group">
                                    <input type="text" name="invoiceDueDate" id="invoiceDueDate" class="form-control"
                                           value="${carlos:forHtmlAttribute(correctionModel.dueDateString)}"
                                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"
                                           style="width:90px"/>
                                    <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                                </div>
                            </div>
                            </c:if>
                        </div>

                        <div class="col-md-5" id="thirdParty" style=" ${correctionModel.thirdParty ? '' : 'display:none'}">
                            <a href="#" onclick="search3rdParty('billTo');return false;"><fmt:message key="billing.billingCorrection.msgPayer"/></a><br>
                            <textarea id="billTo" name="billTo" cols="32" rows=4><carlos:encode value="${correctionModel.payer}" context="html"/></textarea>
                            <c:if test="${correctionModel.useDemoContactAvailable}">
                            <br><fmt:message key="billing.billingCorrection.useDemoContactYesNo"/>:<input
                                type="checkbox" name="overrideUseDemoContact"
                                id="overrideUseDemoContact" ${correctionModel.useDemoContactChecked ? 'checked' : ''} />
                            </c:if>
                        </div>
                    </div>

                </div>
            </div>


            <div id="thirdPartyPymnt" style="${correctionModel.thirdParty ? '' : 'display:none'}">
                ${correctionModel.htmlPaid}
            </div>

        </form>
    </div>
    <div>


        <c:if test="${correctionModel.dueDateAvailable}">
        <script>
            flatpickr("#invoiceDueDate", {dateFormat: "Y-m-d", allowInput: true});
        </script>
        </c:if>


    </div>
    </body>
    <script>
        flatpickr("#xml_appointment_date", {dateFormat: "Y-m-d", allowInput: true});
        flatpickr("#xml_vdate", {dateFormat: "Y-m-d", allowInput: true});

        window.setTimeout(function () {
            $("#alert_message").fadeTo(500, 0).slideUp(500, function () {
                $(this).remove();
            });
        }, 5000);

        function display3rdPartyPayments() {
        popupPage('800', '860', 'billingON3rdPayments?method=listPayments&billingNo=${carlos:forJavaScript(carlos:forUriComponent(correctionModel.billingNo))}');
        }

        document.addEventListener('DOMContentLoaded', function () {
            parent.parent.resizeIframe(document.documentElement.scrollHeight);

        });
    </script>

</html>
