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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ page import="io.github.carlos_emr.carlos.rx.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBeanResolver" %><%@ page import="io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.util.LimitedUseCode" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.util.RxUtil" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.data.RxDrugData" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.data.RxCodesData" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.util.LimitedUseLookup" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.pageUtil.RxWriteScriptForm" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink" %>
<%@ page import="java.util.*" %>
<%long start = System.currentTimeMillis();%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_rx" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_rx");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="WriteScript.title"/></title>

        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/rx/styles.css">
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/carlos-ajax.js"></script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

<%-- Rx state is per patient (#3875): expose this request's bean where the page's EL expects it. --%>
<%-- No bean for the request's patient (none named and none open, a patient whose Rx is not open,
     or a malformed/conflicting demographicNo): redirect and stop here, before any scriptlet below
     dereferences the bean (#3908). --%>
<% { RxSessionBean rxResolvedBean = RxRequestedPatientAccess.resolveAuthorised(request, "_rx", "r"); if (rxResolvedBean != null) { pageContext.setAttribute("RxSessionBean", rxResolvedBean); } else { response.sendRedirect("error.html"); return; } } %>
        <c:if test="${pageScope.RxSessionBean == null}">
            <c:redirect url="error.html"/>
        </c:if>

        <c:if test="${not empty pageScope.RxSessionBean}">
            <c:set var="bean" value="${pageScope.RxSessionBean}" scope="page"/>

            <c:if test="${bean.valid == false}">
                <c:redirect url="error.html"/>
            </c:if>

            <c:if test="${bean.stashIndex == -1}">
                <c:redirect url="/rx/searchDrug"><c:param name="demographicNo" value="${bean.demographicNo}"/></c:redirect>
            </c:if>
        </c:if>

        <%
            RxSessionBean bean = (RxSessionBean) pageContext.findAttribute("bean");
            int n = pageContext.getAttributesScope("bean");
            Enumeration emn = pageContext.getAttributeNamesInScope(n);
            int specialStringLen = 0;
            String quan = "";
            boolean isCustom = true;
            String atcCode = null;
            String regionalIdentifier = "";
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        %>
        <fmt:message key="WriteScript.msgQtyMitte" var="msgQtyMitte"/>
        <fmt:message key="WriteScript.msgRepeats" var="msgRepeats"/>
        <script language=javascript>

            var addTextView = 0;

            function showAddText(randId) {
                var addTextId = "addText_" + randId;
                var addTextWordId = "addTextWord_" + randId;
                oscarLog("randId=" + randId);
                if (addTextView == 0) {
                    document.getElementById(addTextId).style.display = '';
                    addTextView = 1;
                    document.getElementById(addTextWordId).textContent = "less";
                } else {
                    document.getElementById(addTextId).style.display = 'none';
                    addTextView = 0;
                    document.getElementById(addTextWordId).textContent = "more";
                }
            }

            // The write-script form is name="frm" (below); the legacy binding named the Struts 1
            // form bean, so every field handler on this page threw. This script runs in <head>,
            // before the form exists, so it is bound in pageLoad() (#3908).
            var frm = null;
            var freqMin;
            var freqMax;
            var orig = null;
            var first = true;
            var warningArray = new Array();

            var calcQtyflag = true;

            function takeChg() {
                if (frm.take.value != 'Other') {
                    frm.takeOther.style.display = 'none';
                    frm.takeOther.value = frm.take.value;
                } else {
                    frm.takeOther.style.display = '';
                }


                if (frm.take.value == '1/2') {
                    frm.takeOther.value = 0.5;
                }

                if (frm.take.value == '1/4') {
                    frm.takeOther.value = 0.25;
                }
                var sTake = frm.takeOther.value;
                var s = sTake.split('-');
                var sMin;
                var sMax;

                if (s.length == 1) {
                    sMin = sTake;
                    sMax = sTake;
                } else {
                    sMin = s[0];
                    sMax = s[1];
                }

                if (isNaN(parseFloat(sMin))) {
                    sMin = '';
                }
                if (isNaN(parseFloat(sMax))) {
                    sMax = sMin;
                }

                frm.takeMin.value = sMin;
                frm.takeMax.value = sMax;

                calcQty();
            }

            function brandChg() {
                submitForm('update');
            }

            function submitForm(action) {
                oscarLog(frm);
                oscarLog("submitForm called");
                if (frm.repeat.value.length < 1 || isNaN(parseInt(frm.repeat.value))) {
                    oscarLog("first");
                    frm.repeat.value = 0;
                }

                if (frm.quantity.value.length < 1 || frm.quantity.value.match(/\D/)) {
                    oscarLog('<fmt:message key="WriteScript.msgQuantity"/>');
                } else {
                    oscarLog("else");
                    frm.elements["action"].value = action;

                    frm.submit();
                }
            }

            function changeDuration() {
                var freqId = frm.frequencyCode.selectedIndex;

                var dailyMin = freqMin[freqId];

                if (dailyMin < 1) {
                    var minDays = 1 / dailyMin;
                    var bySeven = minDays / 7;
                    var byThirty = minDays / 30;
                    var currentDur = frm.cmbDuration.value;

                    if (Math.floor(bySeven) == bySeven) {  //no remainder
                        frm.durationUnit.value = 'W';
                        if (currentDur < bySeven) {
                            frm.cmbDuration.value = bySeven;
                        }
                        //alert (bySeven);
                    } else {
                        if (Math.floor(byThirty) == byThirty) {  //no remainder
                            frm.durationUnit.value = 'M';
                            if (currentDur < byThirty) {
                                frm.cmbDuration.value = byThirty;
                            }
                            //alert (byThirty);
                        }
                    }

                }
                return true;
            }


            function calculateDuration(durUnit, durValue) {
                var dur = 1;
                switch (durUnit) {
                    case 'D': {
                        dur = durValue;
                        break;
                    }
                    case 'W': {
                        dur = (durValue * 7);
                        break;
                    }
                    case 'M': {
                        dur = (durValue * 30);
                        break;
                    }
                }
                return dur;
            }

            function calcQuantity() {
                var takeMax = frm.takeMax.value;

                var dailyMax = freqMax[frm.frequencyCode.selectedIndex];
                var dur = 1;

                // calculate duration units
                dur = calculateDuration(frm.durationUnit.value, frm.duration.value);

                var maxQty = (takeMax * dailyMax * dur);

                if (isNaN(maxQty)) {

                } else {
                    maxQty = Math.ceil(maxQty);
                    return maxQty;
                }
            }

            function calcQty() {
                var takeMin = frm.takeMin.value;
                var takeMax = frm.takeMax.value;
                var freqId = frm.frequencyCode.selectedIndex;
                var dailyMin = freqMin[freqId];
                var dailyMax = freqMax[freqId];
                var dur = 1;

                if (frm.cmbDuration.value == 'Other') {
                    frm.txtDuration.style.display = '';
                } else {
                    frm.txtDuration.style.display = 'none';
                    frm.txtDuration.value = frm.cmbDuration.value;
                }

                frm.duration.value = frm.txtDuration.value;

                // calculate duration units
                dur = calculateDuration(frm.durationUnit.value, frm.duration.value);

                // calculate quantity
                var minQty = (takeMin * dailyMin * dur);
                var maxQty = (takeMax * dailyMax * dur);

                if (isNaN(minQty) || isNaN(maxQty)) {
                    alert('The value entered is invalid.');
                } else {

                    minQty = Math.ceil(minQty);
                    maxQty = Math.ceil(maxQty);
                    frm.sugQtyMin.value = minQty;
                    frm.sugQtyMax.value = maxQty;
                    if (frm.autoQty.checked == true) {
                        frm.quantity.value = frm.sugQtyMax.value;
                    }
                    setQuantity();
                }

                // calculate repeats
                if (frm.cmbRepeat.value == 'Other') {
                    frm.txtRepeat.style.display = '';
                } else {
                    frm.txtRepeat.style.display = 'none';
                    frm.txtRepeat.value = frm.cmbRepeat.value;
                }

                frm.repeat.value = frm.txtRepeat.value;
//alert ("q"+calcQtyflag);
                //alert(frm.autoQty.checked);
                if (frm.autoQty.checked == true) {
                    if (calcQtyflag) {
                        writeScriptDisplay();
                        calcQtyflag = true;
                    }
                }
            }

            function useQtyMax() {
                var maximum = calcQuantity();
                if (!Number.isFinite(maximum)) {
                    alert('The value entered is invalid.');
                    return;
                }
                frm.quantity.value = maximum;

                writeScriptDisplay();
            }

            function regexDebug(regRet) {
                alert(regRet.input + "\n" + regRet.index + " " + (regRet.index + regRet[0].length) + " " + regRet[0]);
            }

            function replaceScriptDisplay() {
                var orig = frm.special.value;
                var nameRegExp = /.*\n/i;
                var a = nameRegExp.exec(orig);

                clearWarning();
                if (a) {  //Find the title and shorten the string
                    //regexDebug(a);
                    origMinusName = orig.substring(a.index + a[0].length);
                    origMinusName = origMinusName.replace(/Q12H/, "OD");   //KLUDGE the 2 get picked up as the second digit
                    origMinusName = origMinusName.replace(/Q1-2H/, "OD");   //KLUDGE the 2 get picked up as the second digit
                    origMinusName = origMinusName.replace(/Q3-4H/, "OD");   //KLUDGE the 2 get picked up as the second digit
                    origMinusName = origMinusName.replace(/Q4-6H/, "OD");   //KLUDGE the 2 get picked up as the second digit
                    origMinusName = removePRNifNeeded(origMinusName);
                    origMinusName = removeNoSubsifNeeded(origMinusName);

                    if (frm.brandName) {
                        drugName = orig.substring(0, a.index + a[0].length);
                    } else {
                        drugName = frm.customName.value + "\n";
                    }

                    var beforeFirstDigit = "";

                    //find first digit
                    var firstDigitRegExp = /[0-9][0-9,\/,-]*/;
                    var b = firstDigitRegExp.exec(origMinusName);
                    if (b) {
                        //regexDebug(b);
                        if (b.index == 0) {  //frequency is at the start of the line
                            //TODO should I add method if it is not there??
                            addWarning(frm.method.options[frm.method.selectedIndex].text + " Not Added");
                        } else { // frequency is not at the start of the line words come before
                            var beforeFirstDigit = origMinusName.substring(0, b.index);
                            var findMethodRegExp = /(Take|Apply|Rub card card-body bg-body-tertiary in)/;
                            var c = findMethodRegExp.exec(beforeFirstDigit);
                            if (c) {
                                //regexDebug(c);
                                beforeFirstDigit = beforeFirstDigit.replace(/(Take|Apply|Rub card card-body bg-body-tertiary in)/, frm.method.options[frm.method.selectedIndex].text);
                            } else {
                                if (frm.method.options[frm.method.selectedIndex].text != "") {
                                    addWarning("Could not replace word for " + frm.method.options[frm.method.selectedIndex].text);
                                }
                            }
                        }
                        var firstDigitStringLen = b.index + b[0].length;

                        if (origMinusName.substring(b.index + b[0].length, b.index + b[0].length + 1) == '.') {   //Another Kludge, \. does not seem to work correctly t get values 0.5
                            firstDigitStringLen++;
                            var afterDot = /[0-9][0-9]*/;
                            var afterDotResult = afterDot.exec(origMinusName.substring(firstDigitStringLen));
                            if (afterDotResult) {

                                //regexDebug(afterDotResult);
                                if (afterDotResult.index == 0) {
                                    firstDigitStringLen = firstDigitStringLen + afterDotResult.index + afterDotResult[0].length;
                                }
                            }
                        }

                        var afterFirstDigit = origMinusName.substring(firstDigitStringLen);

                        //Find Next Digit
                        var secondDigitRegExp = /[^Q,-,0-9][0-9][0-9,\/,-]*/;   //This will ignore digits starting with Q and - need for frequencies like Q3-4H
                        var d = secondDigitRegExp.exec(afterFirstDigit);
                        if (d) {
                            //regexDebug(d);
                            var betweenFirstAndSecondDigit = afterFirstDigit.substring(0, d.index + 1);
                            var afterSecondDigit = afterFirstDigit.substring(d.index + d[0].length);

                            //Replace units and frequency Unit  //TODO Pull this from Database

                            var findUnitRegExp = /(Tabs|mL|Squirts|gm|mg|µg|Drops|Patch|Puffs|Units|Inhalations)/;
                            var findU = findUnitRegExp.exec(betweenFirstAndSecondDigit);
                            if (findU) {
                                //todo make it like !findU
                            } else {
                                if (frm.unit.options[frm.unit.selectedIndex].text != "") {
                                    addWarning("Could not find place to put " + frm.unit.options[frm.unit.selectedIndex].text);
                                }
                            }

                            var findUnitRegExp = /(PO|SL|IM|SC|PATCH|TOP.|INH|SUPP|O.D.|O.S.|O.U.)/;
                            var findU = findUnitRegExp.exec(betweenFirstAndSecondDigit);
                            if (findU) {
                                //todo make it like !findU
                            } else {
                                if (frm.route.options[frm.route.selectedIndex].text != "") {
                                    addWarning("Could not find place to put " + frm.route.options[frm.route.selectedIndex].text);
                                }
                            }

                            var findFreqRegExp = /(OD|BID|TID|QID|Q1H|Q2H|Q1-2H|Q3-4H|Q4H|Q4-6H|Q6H|Q8H|Q12H|QAM|QPM|QHS|Q1Week|Q2Week|Q1Month|Q3Month)/;
                            var findFreq = findFreqRegExp.exec(betweenFirstAndSecondDigit);
                            if (findFreq) {
                                //todo make it like !findFreq
                            } else {
                                addWarning("Could not find place to put " + frm.frequencyCode.value);
                            }

                            betweenFirstAndSecondDigit = betweenFirstAndSecondDigit.replace(/(Tabs|mL|Squirts|gm|mg|µg|Drops|Patch|Puffs|Units|Inhalations)/, frm.unit.options[frm.unit.selectedIndex].text);
                            betweenFirstAndSecondDigit = betweenFirstAndSecondDigit.replace(/(PO|SL|IM|SC|TOP.|INH|SUPP|O.D.|O.S.|O.U.)/, frm.route.options[frm.route.selectedIndex].text);
                            betweenFirstAndSecondDigit = betweenFirstAndSecondDigit.replace(/(OD|BID|TID|QID|Q1H|Q2H|Q1-2H|Q3-4H|Q4H|Q4-6H|Q6H|Q8H|Q12H|QAM|QPM|QHS|Q1Week|Q2Week|Q1Month|Q3Month)/, frm.frequencyCode.value);

                            //Replace Days Weeks or Months

                            afterSecondDigit = afterSecondDigit.replace(/(Days|Weeks|Months)/, getDurationUnit());

                            var findDurationRegExp = /(Days|Weeks|Months)/;
                            var e = findDurationRegExp.exec(afterSecondDigit);
                            if (e) {
                                //todo make it like !e
                            } else {
                                addWarning("Could not find place to put " + getDurationUnit());
                            }

                            //Replace Qty
                            var f = firstDigitRegExp.exec(afterSecondDigit);

                            if (f) {
                                //regexDebug(f)
                                var betweenSecondDigitandQuantity = afterSecondDigit.substring(0, f.index);
                                var afterQuantity = afterSecondDigit.substring(f.index + f[0].length);
                                var g = firstDigitRegExp.exec(afterQuantity);
                                if (g) {
                                    //regexDebug(g);
                                    var betweenQtyandRepeat = afterQuantity.substring(0, g.index);
                                    var afterRepeat = afterQuantity.substring(g.index + g[0].length);
                                    var b4final = drugName + beforeFirstDigit + getTakeValue() + betweenFirstAndSecondDigit + getDurationValue() + betweenSecondDigitandQuantity + frm.quantity.value + betweenQtyandRepeat + frm.txtRepeat.value + afterRepeat;
                                    var newStr = drugName + beforeFirstDigit + getTakeValue() + betweenFirstAndSecondDigit + getPRN(b4final) + getDurationValue() + betweenSecondDigitandQuantity + frm.quantity.value + " " + frm.unitName.value + "\n" + "Repeats:" + frm.txtRepeat.value + getNoSubs(b4final) + afterRepeat;
                                    //alert (betweenFirstAndSecondDigit);
                                    frm.special.value = newStr;
                                    //alert(newStr);
                                } else {
                                    addWarning("Could not find value to replace Repeat Value");
                                }
                            } else {
                                addWarning("Could not find value to replace Qty Value");
                            }
                        } else {
                            addWarning("Could not find value to replace Duration Value");
                        }
                    } else {
                        addWarning("Could not find value frequency value");
                    }
                } else {
                    addWarning("Name of Drug could not be found");
                }
                fillWarnings();
            }

            function removePRNifNeeded(str) {
                var retval = str;
                if (frm.prn.checked == false) {
                    retval = str.replace(/PRN /, "");
                }
                return retval;
            }

            function removeNoSubsifNeeded(str) {
                var retval = str;
                if (frm.nosubs.checked == false) {
                    retval = str.replace(/No Subs/, "");
                }
                return retval;
            }

            function getPRN(str) {
                var retval = "";
                if (frm.prn.checked) {//is PRN CHECKED?
                    var prnFindRegExp = /PRN/;
                    var p = prnFindRegExp.exec(str);
                    if (!p) {//PRN not found
                        retval = "PRN ";
                    }
                }
                return retval;
            }

            function getNoSubs(str) {
                var retval = "";
                if (frm.nosubs.checked) {//is NOSUB CHECKED?
                    var noSubFindRegExp = /No Subs/;
                    var p = noSubFindRegExp.exec(str);
                    if (!p) {//NOSUB not found
                        retval = " No Subs ";
                    }
                }
                return retval;
            }

            function getTakeValue() {
                var retval = "";
                if (frm.take.value != 'Other') {
                    retval = frm.take.value;
                } else {
                    retval = frm.takeOther.value;
                }
                return retval;
            }

            function getDurationValue() {
                var retval = "";
                if (frm.cmbDuration.value == 'Other') {
                    retval = frm.txtDuration.value;
                } else {
                    retval = frm.cmbDuration.value;
                }
                return retval;
            }

            function getDurationUnit() {
                var retval = "";
                switch (frm.durationUnit.value) {
                    case 'D': {
                        retval = "Days";
                        break;
                    }
                    case 'W': {
                        retval = "Weeks";
                        break;
                    }
                    case 'M': {
                        retval = "Months";
                        break;
                    }
                }
                return retval;
            }

            function checkPatientCompliance(pc) {
                if (pc == "Y") {
                    if (frm.patientComplianceY.checked) frm.patientComplianceN.checked = false;
                } else if (pc == "N") {
                    if (frm.patientComplianceN.checked) frm.patientComplianceY.checked = false;
                }
                frm.elements['patientCompliance'].value = frm.patientComplianceY.checked ? 'true'
                    : frm.patientComplianceN.checked ? 'false' : '';
                writeScriptDisplay();
            }

            function writeScriptDisplay() {
                //alert ("f"+first);

                var disabled = frm.customInstr.checked;

                if (!disabled) {
                    if (first == false) {

                        var frm2 = document.forms.frm;

                        var orig2 = frm.special.value;
                        var preStr = "";

                        if (frm2.brandName) {
                            preStr = frm2.brandName.value + "\n";
                        } else {
                            preStr = frm2.customName.value + "\n";
                        }

                        preStr = preStr + frm2.method.options[frm2.method.selectedIndex].text + " ";

                        if (frm2.take.value != 'Other') {
                            preStr = preStr + frm2.take.value;
                        } else {
                            preStr = preStr + frm2.takeOther.value;
                        }

                        preStr = preStr + " " + frm2.unit.options[frm2.unit.selectedIndex].text;

                        preStr = preStr + " " + frm2.route.options[frm2.route.selectedIndex].text;

                        preStr = preStr + " " + frm2.frequencyCode.value + " ";

                        if (frm2.prn.checked) {
                            preStr = preStr + "PRN ";
                        }

                        /////////////////////
                        preStr = preStr + "for ";
                        if (frm2.cmbDuration.value == 'Other') {
                            preStr = preStr + frm2.txtDuration.value;
                        } else {
                            preStr = preStr + frm2.cmbDuration.value;
                        }

                        // calculate duration units
                        /*switch(frm2.durationUnit.value){
                            case 'D':{
                                preStr = preStr + " Days";
                                break;
                            }
                            case 'W':{
                                preStr = preStr + " Weeks";
                                break;
                            }
                            case 'M':{
                                preStr = preStr + " Months";
                                break;
                            }
                        }
                       */
                        preStr = preStr + getDurationUnit();

                        if ("" == frm2.quantity.value) {
                            frm2.quantity.value = "0";
                        }
                        /////////////////////
                        preStr = preStr + "\n";
                        preStr = preStr + "${msgQtyMitte}:" + frm2.quantity.value + " " + frm2.unitName.value + "\n" + "${msgRepeats}:" + frm2.txtRepeat.value;
                        if (frm2.nosubs.checked) {
                            preStr = preStr + " No Subs";
                        }

                        preStr = preStr + "\n";
                        frm.special.value = preStr;
                        first = true;
                    } else {
                        replaceScriptDisplay();
                    }
                    //first = false;
                }
            }

            function addLuCode(codeToAdd) {
                var txt = frm.special.value;
                frm.special.value = txt + "LU Code: " + codeToAdd;
            }

            function clearWarning() {
                warningArray = new Array();
            }

            function addWarning(addit) {
                warningArray[warningArray.length] = addit;
            }

            function fillWarnings() {
                //warningDiv
                // warningList

                var warningDiv = document.getElementById("warningDiv");
                var warningList = document.getElementById("warningList");

                while (warningList.hasChildNodes()) {
                    warningList.removeChild(warningList.firstChild);
                }
                for (i = 0; i < warningArray.length; i++) {
                    var newText = document.createTextNode(warningArray[i]);
                    var newNode = document.createElement("li");
                    newNode.appendChild(newText);
                    warningList.appendChild(newNode);
                }

                if (warningArray.length == 0) {
                    warningDiv.style.display = 'None';
                } else {
                    warningDiv.style.display = '';
                }

            }

            function validNum(e) {
                var keynum;

                if (window.event)
                    keynum = e.keyCode;
                else if (e.which)
                    keynum = e.which;

                if (keynum == undefined)
                    return true;

                var keychar = String.fromCharCode(keynum);
                var numcheck = /(\d|\x08)/;

                return numcheck.test(keychar)
            }

            function chkQty(val) {
                if (val.match(/\D/))
                    return false;

                return true;
            }

            function restoreEditorSelect(select, value) {
                select.value = value;
                if (select.value !== value) {
                    select.add(new Option(value, value));
                    select.value = value;
                }
            }

            function refreshSuggestedQuantity() {
                var index = frm.frequencyCode.selectedIndex;
                var duration = calculateDuration(frm.durationUnit.value, frm.duration.value);
                var minimum = Math.ceil(Number(frm.takeMin.value) * freqMin[index] * duration);
                var maximum = Math.ceil(Number(frm.takeMax.value) * freqMax[index] * duration);
                if (Number.isFinite(minimum) && Number.isFinite(maximum)) {
                    frm.sugQtyMin.value = minimum;
                    frm.sugQtyMax.value = maximum;
                    setQuantity();
                }
            }

            function pageLoad() {
                frm = document.forms.frm;
                // Rendering an existing prescription must not recalculate its quantity or rewrite
                // its instructions. Dosing controls explicitly invoke calcQty when edited.
                refreshSuggestedQuantity();
                var txtQty = frm.quantity;
                if (txtQty.restrict) alert("YES");
                txtQty.restrict = "0-9";

                prepareOutsideProvider();
            }

            function prepareOutsideProvider() {
                var checked = frm.outsideProviderName.value.length > 0 || frm.outsideProviderOhip.value.length > 0;
                document.getElementById('ocheck').checked = checked;
                document.getElementById('otext').style.display = checked ? '' : 'none';
            }

            function showHideOutsideProvider() {
                if (document.getElementById('ocheck').checked) {
                    document.getElementById('otext').style.display = '';
                    frm.outsideProviderName.focus();
                } else {
                    frm.outsideProviderName.value = "";
                    frm.outsideProviderOhip.value = "";
                    document.getElementById('otext').style.display = 'none';
                }
            }
        </script>


        <script language="javascript">

            function showpic(picture) {
                if (document.getElementById) { // Netscape 6 and IE 5+

                    var targetElement = document.getElementById(picture);
                    var bal = document.getElementById("Calcs");

                    var offsetTrail = document.getElementById("Calcs");
                    var offsetLeft = 0;
                    var offsetTop = 0;
                    while (offsetTrail) {
                        offsetLeft += offsetTrail.offsetLeft;
                        offsetTop += offsetTrail.offsetTop;
                        offsetTrail = offsetTrail.offsetParent;
                    }
                    if (navigator.userAgent.indexOf("Mac") != -1 &&
                        typeof document.body.leftMargin != "undefined") {
                        offsetLeft += document.body.leftMargin;
                        offsetTop += document.body.topMargin;
                    }

                    targetElement.style.left = offsetLeft + bal.offsetWidth;
                    targetElement.style.top = offsetTop;
                    targetElement.style.visibility = 'visible';
                }
            }

            function hidepic(picture) {
                if (document.getElementById) { // Netscape 6 and IE 5+
                    var targetElement = document.getElementById(picture);
                    targetElement.style.visibility = 'hidden';
                }
            }

            function popperup(vheight, vwidth, varpage, pageName) { //open a new popup window
                hidepic('Layer1');
                var page = varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",status=yes,location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=100,left=100";
                var popup = window.open(varpage, pageName, windowprops);
                popup.opener = self;
                popup.focus();
            }

            var resHidden = 0;

            function showUntrustedRes() {
                var list = document.querySelectorAll('div.untrustedResource');

                if (resHidden == 0) {
                    document.getElementById('showUntrustedResWord').textContent = 'hide';
                    list.forEach(function(el) { el.style.display = ''; });
                    resHidden = 1;
                } else {
                    document.getElementById('showUntrustedResWord').textContent = 'show';
                    list.forEach(function(el) { el.style.display = 'none'; });
                    resHidden = 0;
                }
            }




            var resHidden2 = 0;

            function showHiddenRes() {
                var list = document.querySelectorAll('div.hiddenResource');

                if (resHidden2 == 0) {
                    list.forEach(function(el) { el.style.display = ''; });
                    resHidden2 = 1;
                    document.getElementById('showHiddenResWord').textContent = 'hide';
                } else {
                    document.getElementById('showHiddenResWord').textContent = 'show';
                    list.forEach(function(el) { el.style.display = 'none'; });
                    resHidden2 = 0;
                }
            }
        </script>

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>


    </head>
    <body topmargin="0" leftmargin="0" vlink="#0000FF"
          onload="javascript:pageLoad();">
    <form id="addFavoriteWriteScriptForm" method="post" action="<%= request.getContextPath() %>/rx/addFavoriteWriteScript" style="display:none">
        <input type="hidden" name="randomId" value=""/>
        <input type="hidden" name="favoriteName" value=""/>
        <%-- The staged card is looked up in this window's patient's stash only (#3875). --%>
        <input type="hidden" name="demographicNo" value="<%= bean.getDemographicNo() %>"/>
    </form>

    <form id="RxStashForm" name="RxStashForm" action="${pageContext.request.contextPath}/rx/stash" method="post" style="display:none">
        <input type="hidden" name="action" value=""/>
        <input type="hidden" name="demographicNo" value="<%= bean.getDemographicNo() %>"/>
        <input type="hidden" name="randomId"/>
        <input type="hidden" name="draftRevision"/>
    </form>

    <form action="${pageContext.request.contextPath}/rx/writeScript" method="post" id="frm" name="frm">

    <input type="hidden" name="action" id="action"/>

    <%-- Printing persists the entire displayed stash; bind every card version, not only the editor. --%>
    <% for (RxPrescriptionData.Prescription draftCard : bean.getStash()) { %>
    <input type="hidden" name="draftRevision_<%= draftCard.getRandomId() %>" value="<carlos:encode value='<%= draftCard.getDraftRevision() %>' context="htmlAttribute"/>"/>
    <% } %>

    <%


        // define current form
        RxWriteScriptForm thisForm = (RxWriteScriptForm) request.getAttribute("RxWriteScriptForm");

        if (thisForm == null) {
            thisForm = new RxWriteScriptForm();
            request.setAttribute("RxWriteScriptForm", thisForm);
        }

        if (thisForm != null) {
            if (bean.getStashIndex() > -1) { //new way
                RxPrescriptionData.Prescription rx = bean.getStashItem(bean.getStashIndex());
    %>
    <%-- Bind this editor to the displayed card, including when another window moves the cursor. --%>
    <input type="hidden" name="randomId" value="<%= rx.getRandomId() %>"/>
    <input type="hidden" name="draftRevision" value="<carlos:encode value='<%= rx.getDraftRevision() %>' context="htmlAttribute"/>"/>
    <%
                RxDrugData drugData = new RxDrugData();
                thisForm.setDemographicNo(bean.getDemographicNo());
                thisForm.setRxDate(RxUtil.DateToString(rx.getRxDate(), "yyyy-MM-dd"));
                thisForm.setEndDate(RxUtil.DateToString(rx.getEndDate(), "yyyy-MM-dd"));
                thisForm.setWrittenDate(RxUtil.DateToString(rx.getWrittenDate(), "yyyy-MM-dd"));
                if (thisForm.getWrittenDate().length() == 0)
                    thisForm.setWrittenDate(RxUtil.DateToString(RxUtil.Today(), "yyyy-MM-dd"));


                if (!rx.isCustom()) {
                    thisForm.setGenericName(rx.getGenericName());
                    thisForm.setBrandName(rx.getBrandName());
                    thisForm.setGCN_SEQNO(rx.getGCN_SEQNO());
                    thisForm.setCustomName("");
                } else {
                    thisForm.setGenericName("");
                    thisForm.setBrandName("");
                    thisForm.setGCN_SEQNO("0");
                    thisForm.setCustomName(rx.getCustomName());
                }

                quan = rx.getQuantity();
                thisForm.setTakeMin(rx.getTakeMinString());
                thisForm.setTakeMax(rx.getTakeMaxString());
                thisForm.setFrequencyCode(rx.getFrequencyCode());
                thisForm.setDuration(rx.getDuration());
                thisForm.setDurationUnit(rx.getDurationUnit());
                thisForm.setQuantity(rx.getQuantity());

                thisForm.setDosage(rx.getDosage());
                thisForm.setRepeat(rx.getRepeat());
                thisForm.setLastRefillDate(RxUtil.DateToString(rx.getLastRefillDate(), "yyyy-MM-dd"));
                if (isEmpty(thisForm.getLastRefillDate())) thisForm.setLastRefillDate("yyyy-mm-dd");
                thisForm.setNosubs(rx.getNosubs());
                thisForm.setPrn(rx.getPrn());

                if (rx.getSpecial() == null || rx.getSpecial().length() < 6)
                    MiscUtils.getLogger().warn("The drug instructions passed to the display were blank or truncated");

                thisForm.setSpecial(rx.getSpecial());
                thisForm.setLongTerm(rx.getLongTerm());
                thisForm.setPastMed(rx.getPastMed());
                thisForm.setShortTerm(rx.getShortTerm());
                thisForm.setDispenseInternal(rx.getDispenseInternal());
                thisForm.setPatientCompliance(rx.getPatientCompliance());
                thisForm.setAtcCode(rx.getAtcCode());
                thisForm.setRegionalIdentifier(rx.getRegionalIdentifier());
                thisForm.setUnit(rx.getUnit());
                thisForm.setUnitName(rx.getUnitName());
                thisForm.setMethod(rx.getMethod());
                thisForm.setRoute(rx.getRoute());
                thisForm.setCustomInstr(rx.getCustomInstr());
                thisForm.setOutsideProviderName(rx.getOutsideProviderName());
                thisForm.setOutsideProviderOhip(rx.getOutsideProviderOhip());

                atcCode = rx.getAtcCode();
            }

            isCustom = Objects.equals(thisForm.getGCN_SEQNO(), "0");
            String drugId = thisForm.getGCN_SEQNO();
        }
    %>
    <% regionalIdentifier = thisForm.getRegionalIdentifier(); %>
    <%

        // set patient info
        RxPatientData.Patient patient = RxPatientData.getPatient(loggedInInfo, thisForm.getDemographicNo());

        RxDrugData drug = new RxDrugData();

        java.util.ArrayList brands = null;
        java.util.ArrayList forms = null;
        java.util.ArrayList routes = null;
        java.util.Hashtable dosage = null;
        try {
            specialStringLen = thisForm.getSpecial().length();
        } catch (Exception strLenEx) {
            specialStringLen = 0;
        }
        String compString = null;

        Vector comps = (Vector) request.getAttribute("components");
        if (comps != null) {
            compString = new String();
            for (int c = 0; c < comps.size(); c++) {
                RxDrugData.DrugMonograph.DrugComponent dc = (RxDrugData.DrugMonograph.DrugComponent) comps.get(c);
                compString = compString + dc.name + " " + dc.strength + " " + dc.unit + "\n";
            }
        }

        RxCodesData codesData = new RxCodesData();

// create freq list
        RxCodesData.FrequencyCode[] freq = codesData.getFrequencyCodes();

// create special instructions list
        String[] spec = codesData.getSpecialInstructions();

        int i;
    %>

    <script language=javascript>
        freqMin = new Array(<%= freq.length%>);
        freqMax = new Array(<%= freq.length%>);

        <%for(i=0;i<freq.length;i++){%>
        freqMin[<%=i%>] = <%= freq[i].getDailyMin()%>;
        freqMax[<%=i%>] = <%= freq[i].getDailyMax()%>;
        <%}%>
    </script>

    <%-- Carries the window's patient: the per-patient Rx bean is resolved from it, and a save that
         does not name its patient is refused (#3875). --%>
    <input type="hidden" name="demographicNo" id="demographicNo" value="<%= bean.getDemographicNo() %>"/>
    <input type="hidden" name="dispenseInternal" value="<%= thisForm.getDispenseInternal() %>"/>
    <input type="hidden" name="shortTerm" value="<%= thisForm.getShortTerm() %>"/>
    <input type="hidden" name="patientCompliance" value="<%= thisForm.getPatientCompliance() == null ? "" : thisForm.getPatientCompliance().toString() %>"/>
    <input type="hidden" name="GCN_SEQNO" id="GCN_SEQNO" value="<carlos:encode value='<%= thisForm.getGCN_SEQNO() %>' context="htmlAttribute"/>"/>
    <input type="hidden" name="atcCode" id="atcCode" value="<carlos:encode value='<%= thisForm.getAtcCode() %>' context="htmlAttribute"/>"/>
    <input type="hidden" name="regionalIdentifier" id="regionalIdentifier" value="<carlos:encode value='<%= thisForm.getRegionalIdentifier() %>' context="htmlAttribute"/>"/>
    <input type="hidden" name="dosage" id="dosage" value="<carlos:encode value='<%= thisForm.getDosage() %>' context="htmlAttribute"/>"/>


    <table border="0" cellpadding="0" cellspacing="0" <% /*style="border-collapse: collapse"*/%> bordercolor="#111111"
           width="100%" height="100%">
        <%@ include file="TopLinks.jsp" %><!-- Row One included here-->
        <tr>
            <%@ include file="SideLinksNoEditFavorites.jsp" %><!-- <td></td>Side Bar File --->
            <td width="100%" style="border-left: 2px solid #A9A9A9; " height="100%" valign="top">
                <table cellpadding="0" cellspacing="2" style="border-collapse: collapse" bordercolor="#111111"
                       width="100%" height="100%">
                    <tr>
                        <td width="0%" valign="top">
                            <div class="DivCCBreadCrumbs">
                                <a href="<%= request.getContextPath() %>/rx/searchDrug?demographicNo=<%= bean.getDemographicNo() %>"> <fmt:message key="SearchDrug.title"/></a> >
                                <fmt:message key="ChooseDrug.title"/> >
                                <b><fmt:message key="WriteScript.title"/></b>
                            </div>
                        </td>
                    </tr>
                    <!----Start new rows here-->

                    <tr>
                        <td>
                            <div class="DivContentTitle"><fmt:message key="WriteScript.title"/></div>
                        </td>
                    </tr>

                    <tr>
                        <td>
                            <div class="DivContentSectionHead"><fmt:message key="WriteScript.section2Title"/>
                                for <%= patient.getFirstName() %> <%= patient.getSurname() %>
                            </div>
                        </td>
                    </tr>


                    <tr>
                        <td>
                            <table border=1 style="border: 1px solid #A9A9A9; ">

                                <% if (!isCustom) { %>
                                <tr>
                                    <td colspan=2>
                                        <fmt:message key="WriteScript.genericNameText"/>:
                                    </td>
                                    <td colspan=2>
                                        <input type="hidden" name="genericName" id="genericName" value="<carlos:encode value='<%= thisForm.getGenericName() %>' context="htmlAttribute"/>"/>
                                        <b><carlos:encode value="<%= thisForm.getGenericName() %>" context="html"/>
                                        </b>
                                        <%if (compString != null) {%>
                                        <a href="javascript: function myFunction() {return false; }"
                                           title="<%=compString%>"><fmt:message key="WriteScript.msgComponents"/></a>
                                        <%}%>
                                    </td>
                                    <td valign=top rowspan=9>
                                        <select size=20 name="selSpecial" ondblclick="javascript:cmdSpecial_click();">
                                            <%for (i = 0; i < spec.length; i++) {%>
                                            <option value="<%= spec[i] %>">
                                                <%= spec[i] %>
                                            </option>
                                            <%}%>
                                        </select>
                                    </td>
                                </tr>

                                <tr>
                                    <td colspan=2>
                                        <fmt:message key="WriteScript.brandNameText"/>:
                                    </td>
                                    <td colspan=2>
                                        <input type="hidden" name="brandName" id="brandName" value="<carlos:encode value='<%= thisForm.getBrandName() %>' context="htmlAttribute"/>"/>
                                        <b title="<carlos:encode value='<%= thisForm.getRegionalIdentifier() %>' context="htmlAttribute"/>"><carlos:encode value="<%= thisForm.getBrandName() %>" context="html"/>
                                        </b>
                                        <oscar:oscarPropertiesCheck property="SHOW_ODB_LINK" value="yes">
                                            <!--a href="javascript: function myFunction() {return false; }" onclick="javascript:popup(700,630,'http://216.176.50.202/formulary/SearchServlet?searchType=singleQuery&phrase=exact&keywords=<%=regionalIdentifier%>','ODBInfo')">ODB info</a-->
                                            <a href="javascript: function myFunction() {return false; }"
                                               onclick="javascript:popup(725,690,'http://216.176.50.202/formulary/SearchServlet?sort=genericName&section=1&pcg=%25&manufacturerID=%25&keywords=<%=regionalIdentifier%>&searchType=drugID&Search=Search&phrase=exact','ODBInfo')">ODB
                                                info</a>
                                        </oscar:oscarPropertiesCheck>
                                    </td>
                                    <!--<td >
                                        &nbsp;
                                    </td>-->
                                </tr>


                                <% } else { /* Custom Drug Entry */%>

                                <tr>
                                    <td colspan=2 valign="top">Custom Drug:</td>
                                    <td colspan=2><textarea name="customName" cols="50"
                                                                 rows="3"
                                                            onchange="javascript:writeScriptDisplay();"><carlos:encode value='<%= thisForm.getCustomName() %>' context="html"/></textarea></td>
                                    <td valign=top rowspan=8>
                                        <div style="z-index: 0;"><select size=20 name="selSpecial"
                                                                         ondblclick="javascript:cmdSpecial_click();">
                                            <%for (i = 0; i < spec.length; i++) {%>
                                            <option value="<%= spec[i] %>"><%= spec[i] %>
                                            </option>
                                            <%}%>
                                        </select></div>
                                    </td>
                                </tr>

                                <% } /* Custom */ %>

                                <tr>
                                    <td colspan=2><label for="rxDate"><fmt:message key="WriteScript.startDate"/></label>:</td>
                                    <td colspan=2><input type="text" name="rxDate" id="rxDate"  value="<carlos:encode value='<%= thisForm.getRxDate() %>' context="htmlAttribute"/>"/></td>
                                    <!--<td >
                                          &nbsp;
                                        </td>-->
                                </tr>


                                <tr>
                                    <td colspan=2><select name="method"
                                                               style="width:90px" onchange="calcQty();">
                                        <option value="Take">Take</option>
                                        <option value="Apply">Apply</option>
                                        <option value="Rub">Rub card card-body bg-body-tertiary in</option>
                                        <option value=""></option>
                                    </select></td>
                                    <td colspan=2><select name="take" style="width: 72px"
                                                          onChange="javascript:takeChg();">
                                        <option value="1/4">1/4</option>
                                        <option value="1/2">1/2</option>
                                        <option value="1">1</option>
                                        <option value="1-2">1-2</option>
                                        <option value="1-3">1-3</option>
                                        <option value="2">2</option>
                                        <option value="2-3">2-3</option>
                                        <option value="3">3</option>
                                        <option value="3-4">3-4</option>
                                        <option value="4">4</option>
                                        <option value="5">5</option>
                                        <option value="6">6</option>
                                        <option value="7">7</option>
                                        <option value="8">8</option>
                                        <option value="9">9</option>
                                        <option value="Other">Other</option>
                                    </select> <input type=text name="takeOther" style="display: none" size="5"
                                                     onChange="takeChg();"/> <select
                                            name="unit" style="width:80px" onchange="calcQty();">
                                        <option value="tab">Tabs</option>
                                        <option value="mL">mL</option>
                                        <option value="sqrt">Squirts</option>
                                        <option value="gm">gm</option>
                                        <option value="mg">mg</option>
                                        <option value="micg">µg</option>
                                        <option value="drop">Drops</option>
                                        <option value="patc">Patch</option>
                                        <option value="puff">Puffs</option>
                                        <option value="units">Units</option>
                                        <option value="units">Inhalations</option>
                                        <option value=""></option>
                                    </select> <select name="route" style="width:80px"
                                                                onchange="calcQty();">
                                        <option value="PO">PO</option>
                                        <option value="SL">SL</option>
                                        <option value="IM">IM</option>
                                        <option value="SC">SC</option>
                                        <option value="TOP">TOP.</option>
                                        <option value="INH">INH</option>
                                        <option value="SUPP">SUPP</option>
                                        <option value="O.D.">O.D.</option>
                                        <option value="O.S.">O.S.</option>
                                        <option value="O.U.">O.U.</option>

                                        <option value=""></option>
                                    </select> <select name="frequencyCode" style="width:80px"
                                                                onchange="javascript:changeDuration();calcQty();">
                                        <%for (i = 0; i < freq.length; i++) {%>
                                        <option value="<%= freq[i].getFreqCode() %>">
                                            <%= freq[i].getFreqCode() %>
                                        </option>
                                        <%}%>
                                    </select> <input type="hidden" name="takeMin" id="takeMin" value="<carlos:encode value='<%= thisForm.getTakeMin() %>' context="htmlAttribute"/>"/>
                                        <input type="hidden" name="takeMax" id="takeMax" value="<carlos:encode value='<%= thisForm.getTakeMax() %>' context="htmlAttribute"/>"/>
                                        <script language=javascript>
                                            var frm = document.forms.frm;


                                            if (frm.takeMin.value == frm.takeMax.value) {
                                                frm.takeOther.value = frm.takeMin.value;
                                            } else {
                                                frm.takeOther.value = (frm.takeMin.value + '-' + frm.takeMax.value);
                                            }

                                            if (frm.takeOther.value == '0.5') {
                                                frm.takeOther.value = '1/2';
                                            }

                                            if (frm.takeOther.value == '0.25') {
                                                frm.takeOther.value = '1/4';
                                            }
                                            frm.take.value = frm.takeOther.value;
                                            if (frm.take.value != frm.takeOther.value) {
                                                frm.take.value = 'Other';
                                                frm.takeOther.style.display = '';
                                            }
                                        </script>
                                        <label for="prn"><fmt:message key="WriteScript.prn"/></label>
                                        <input type="checkbox" id="prn" name="prn" value="true" <%= thisForm.getPrn() ? "checked" : "" %> onchange="javascript:writeScriptDisplay();"/>
                                    </td>
                                    <!--<td>
                                            &nbsp;
                                        </td>-->
                                </tr>


                                <tr>
                                    <td colspan=2><fmt:message key="WriteScript.msgFor"/>:</td>
                                    <td colspan=2><select name="cmbDuration" style="width: 72px"
                                                          onChange="javascript:calcQty();">
                                        <%for (i = 1; i < 15; i++) {%>
                                        <option value="<%= i%>"><%= i%>
                                        </option>
                                        <%}%>
                                        <option value="30">30</option>
                                        <option value="60">60</option>
                                        <option value="90">90</option>
                                        <option value="Other">Other</option>
                                    </select> <input type=text name="txtDuration" size="4"
                                                     onchange="javascript:calcQty();" style="display: none"/>
                                        <select
                                                name="durationUnit" style="width:80px"
                                                onchange="javascript:calcQty();">
                                            <option value="D"><fmt:message key="WriteScript.msgDays"/></option>
                                            <option value="W"><fmt:message key="WriteScript.msgWeeks"/></option>
                                            <option value="M"><fmt:message key="WriteScript.msgMonths"/></option>
                                        </select> <input type="hidden" name="duration" id="duration" value="<carlos:encode value='<%= thisForm.getDuration() %>' context="htmlAttribute"/>"/>
                                        <script language=javascript>
                                            frm.txtDuration.value = frm.duration.value;

                                            frm.cmbDuration.value = frm.txtDuration.value;
                                            if (frm.cmbDuration.value != frm.txtDuration.value) {
                                                frm.cmbDuration.value = 'Other';
                                                frm.txtDuration.style.display = '';
                                            }
                                        </script>
                                    </td>
                                    <!--<td>
                                        &nbsp;
                                        </td>-->
                                </tr>

                                <tr>
                                    <td colspan=2><fmt:message key="WriteScript.quantity"/>: auto<input type="checkbox"
                                                                                                         name="autoQty"/>
                                    </td>
                                    <td colspan=2 width=65%><input type="text" name="quantity"
                                                                       size="8"
                                                                       onchange="javascript:if( chkQty(this.value) ) {writeScriptDisplay(); customQty(this.value);}"
                                                                       onkeypress="return validNum(event);"
                                                                       onkeyup="customQty(this.value);" value="<carlos:encode value='<%= thisForm.getQuantity() %>' context="htmlAttribute"/>"/> <input
                                            type=button
                                            value="<<" onclick=" javascript:useQtyMax();"/>
                                        (<fmt:message key="WriteScript.msgCalculated"/>:&nbsp;<span id="lblSugQty"
                                                                                                     style="font-weight: bold"></span>&nbsp;
                                        )&nbsp;<input type="text" name="unitName" size="5"
                                                          onchange="javascript:writeScriptDisplay();" value="<carlos:encode value='<%= thisForm.getUnitName() %>' context="htmlAttribute"/>"/> <input
                                                type=hidden name="sugQtyMin"/> <input type=hidden
                                                                                      name="sugQtyMax"/>
                                        <script language="javascript">
                                            function setQuantity() {
                                                var path = "${carlos:forJavaScript(ctx)}";
                                                oscarLog("here1");
                                                oscarLog("path in setQuantity" + path);
                                                var sugQtyLbl = document.getElementById('lblSugQty');
                                                while (sugQtyLbl.hasChildNodes()) {
                                                    sugQtyLbl.removeChild(sugQtyLbl.firstChild);
                                                }
                                                var newSugQty = frm.sugQtyMin.value;
                                                if (frm.sugQtyMin.value != frm.sugQtyMax.value) {
                                                    newSugQty = frm.sugQtyMin.value + ' - ' + frm.sugQtyMax.value;
                                                }
                                                sugQtyLbl.appendChild(document.createTextNode(newSugQty));
                                            }

                                            setQuantity();
                                        </script>
                                    </td>
                                    <!--<td>
                                            &nbsp;
                                        </td>-->
                                </tr>

                                <tr>
                                    <td colspan=2><fmt:message key="WriteScript.msgRepeats"/>:</td>
                                    <td colspan=2><select name="cmbRepeat" style="width: 72px"
                                                          onChange="javascript:calcQty();">
                                        <%for (i = 0; i < 9; i++) {%>
                                        <option value="<%= i%>"><%= i%>
                                        </option>
                                        <%}%>
                                        <option value="Other"><fmt:message key="WriteScript.msgOther"/></option>
                                    </select> <input type=text name="txtRepeat" size="5"
                                                     onchange="calcQty();" style="display: none"/>
                                        <input type="hidden" name="repeat" id="repeat" value="<carlos:encode value='<%= String.valueOf(thisForm.getRepeat()) %>' context="htmlAttribute"/>"/>
                                        <script language=javascript>
                                            frm.txtRepeat.value = frm.repeat.value;

                                            frm.cmbRepeat.value = frm.txtRepeat.value;
                                            if (frm.cmbRepeat.value != frm.txtRepeat.value) {
                                                frm.cmbRepeat.value = 'Other';
                                                frm.txtRepeat.style.display = '';
                                            }
                                        </script>
                                        &nbsp;
                                        <label for="nosubs"><fmt:message key="WriteScript.noSubs"/></label>:
                                        <input type="checkbox" id="nosubs" name="nosubs" value="true" <%= thisForm.getNosubs() ? "checked" : "" %> onchange="javascript:writeScriptDisplay();"/>
                                        &nbsp;
                                        <label for="lastRefillDate"><fmt:message key="WriteScript.msgLastRefillDate"/></label>:
                                        <input type="text" id="lastRefillDate" name="lastRefillDate" onfocus="javascript:lastRefillDate.value='';" value="<carlos:encode value='<%= thisForm.getLastRefillDate() %>' context="htmlAttribute"/>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan=4>
                                        <label for="longTermFlag"><fmt:message key="WriteScript.msgLongTermMedication"/></label>:
                                        <input type="hidden" name="longTerm" value="<%= thisForm.getLongTerm() == null ? "" : thisForm.getLongTerm().toString() %>"/>
                                        <input type="checkbox" id="longTermFlag" name="longTermFlag" <%= Boolean.TRUE.equals(thisForm.getLongTerm()) ? "checked" : "" %> onchange="frm.elements['longTerm'].value = this.checked; writeScriptDisplay();"/>&nbsp;&nbsp;
                                        <label for="pastMedFlag"><fmt:message key="WriteScript.msgPastMedication"/></label>:
                                        <input type="hidden" name="pastMed" value="<%= thisForm.getPastMed() == null ? "" : thisForm.getPastMed().toString() %>"/>
                                        <input type="checkbox" id="pastMedFlag" name="pastMedFlag" <%= Boolean.TRUE.equals(thisForm.getPastMed()) ? "checked" : "" %> onchange="frm.elements['pastMed'].value = this.checked; writeScriptDisplay();"/>&nbsp;&nbsp;
                                        <span id="patientComplianceLabel"><fmt:message key="WriteScript.msgPatientCompliance"/>:</span>
                                        <label for="patientComplianceY"><fmt:message key="WriteScript.msgYes"/></label>
                                        <input type="checkbox" aria-describedby="patientComplianceLabel" id="patientComplianceY" name="patientComplianceY" <%= Boolean.TRUE.equals(thisForm.getPatientCompliance()) ? "checked" : "" %> onchange="javascript:checkPatientCompliance('Y');"/>
                                        <label for="patientComplianceN"><fmt:message key="WriteScript.msgNo"/></label>
                                        <input type="checkbox" aria-describedby="patientComplianceLabel" id="patientComplianceN" name="patientComplianceN" <%= Boolean.FALSE.equals(thisForm.getPatientCompliance()) ? "checked" : "" %> onchange="javascript:checkPatientCompliance('N');"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan=4>
                                        <fmt:message key="WriteScript.special"/>: &nbsp; &nbsp; &nbsp; &nbsp;
                                        <input type="checkbox" id="customInstr" name="customInstr" value="true" <%= thisForm.getCustomInstr() ? "checked" : "" %>/><label for="customInstr"><fmt:message key="WriteScript.msgCustomInstructions"/></label>
                                        <script language=javascript>
                                            function cmdSpecial_click() {
                                                var frm = document.forms.frm;
                                                if (frm.selSpecial.selectedIndex > -1) {
                                                    var s = frm.selSpecial.value;

                                                    frm.special.value += s + '\n';
                                                }
                                            }
                                        </script>

                                        <table width=100% border=1>
                                            <tr>
                                                <td valign=top><textarea name="special" cols="50"
                                                                         rows="5"><carlos:encode value='<%= thisForm.getSpecial() %>' context="html"/></textarea> <input type=button value="RD"
                                                                                                title="Redraw"
                                                                                                onclick="javascript:first = false; writeScriptDisplay(); clearWarning(); fillWarnings();"/>
                                                    <div id="warningDiv" style="display: none;">
                                                        <ul id="warningList">
                                                            <li><fmt:message key="WriteScript.msgWarning"/></li>
                                                        </ul>
                                                    </div>
                                                    <oscar:oscarPropertiesCheck property="billregion" value="ON">
                                                        <a target="_new"
                                                           href="https://www.healthinfo.moh.gov.on.ca/formulary/SearchServlet?searchType=drugID&keywords=<%=regionalIdentifier%>">ODB
                                                            lookup</a>
                                                        <%
                                                            ArrayList<LimitedUseCode> luList = LimitedUseLookup.getLUInfoForDin(regionalIdentifier);
                                                            if (luList != null) { %>

                                                        <table
                                                                style="border-width: 1px; border-spacing: 2px; border-style: outset; border-color: black;">
                                                            <tr>
                                                                <th colspan="2" align="left">Limited Use Codes</th>
                                                            </tr>


                                                            <%for (LimitedUseCode limitedUseCode : luList) {%>
                                                            <tr>
                                                                <td valign="top"><a
                                                                        onclick="addLuCode('<%=limitedUseCode.getUseId()%>')"
                                                                        href="javascript: return void(0);"><%=limitedUseCode.getUseId()%>
                                                                </a>&nbsp;
                                                                </td>
                                                                <td><%=limitedUseCode.getTxt()%>
                                                                </td>
                                                            </tr>
                                                            <%}%>
                                                        </table>
                                                        <%}%>
                                                    </oscar:oscarPropertiesCheck></td>
                                                <td valign=center><input type=button name="cmdSpecial"
                                                                         value="<<"
                                                                         onclick="cmdSpecial_click();"/>
                                                </td>

                                            </tr>
                                        </table>
                                    </td>

                                </tr>
                                <tr>
                                    <td colspan="5">
                                        <label for="ocheck"><fmt:message key="WriteScript.msgPrescribedByOutsideProvider"/></label>
                                        <input type="checkbox" id="ocheck"
                                               onclick="showHideOutsideProvider();"/> &nbsp;
                                        <span id="otext">
							    <b><label for="outsideProviderName"><fmt:message key="WriteScript.msgName"/></label>:</b>
                                            <input type="text" id="outsideProviderName" name="outsideProviderName" value="<carlos:encode value='<%= thisForm.getOutsideProviderName() %>' context="htmlAttribute"/>"/> &nbsp;
							    <b><label for="outsideProviderOhip"><fmt:message key="WriteScript.msgOHIPNO"/></label>:</b>
                                            <input type="text" id="outsideProviderOhip" name="outsideProviderOhip" value="<carlos:encode value='<%= thisForm.getOutsideProviderOhip() %>' context="htmlAttribute"/>"/>
							</span>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="5">
                                      <label for="writtenDate"> <fmt:message key="WriteScript.msgRxWrittenDate"/>: </label>
                                            <input type="text" name="writtenDate" id="writtenDate"  value="<carlos:encode value='<%= thisForm.getWrittenDate() %>' context="htmlAttribute"/>"/>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>

                    <tr>
                        <td><!--3a-->
                            <table width="100%">
                                <tr>
                                    <td>
                                        <input type=button class="ControlPushButton" style="width: 55px"
                                               onclick="submitForm('update');"
                                               value="<fmt:message key="WriteScript.msgUpdate"/>"/>
                                        <input type=button class="ControlPushButton" style="width: 200px"
                                               onclick="submitForm('updateAddAnother');"
                                               value="<fmt:message key="WriteScript.msgUpdateAndGetNewDrug"/>"/>
                                        <input type=button class="ControlPushButton" style="width: 200px"
                                               onclick="submitForm('updateAndPrint');"
                                               value="<fmt:message key="WriteScript.msgUpdatePrintAndSave"/>"/>
                                    </td>
                                </tr>
                            </table>
                            <!-- input type=button class="ControlPushButton" style="width:200px" onclick="javascript:replaceScriptDisplay();" value="REPLACE" />
                                        <input type=button class="ControlPushButton" style="width:200px" onclick="javascript:fillWarnings();" value="RunWarning" /
                                        <input type=button class="ControlPushButton" style="width:200px" onclick="javascript:addWarning();" value="FillWarning" /-->

                            <!-- peice Went Here --> <%
                                //RxPatientData.Patient.Allergy[] allerg = (RxPatientData.Patient.Allergy[]) request.getAttribute("ALLERGIES");
                                Allergy[] allerg = bean.getAllergyWarnings(loggedInInfo, atcCode);
                                if (allerg != null && allerg.length > 0) {
                                    for (int allergIndex = 0; allergIndex < allerg.length; allergIndex++) {
                            %>
                            <div style="background-color:<%=severityOfReactionColor(allerg[allergIndex].getSeverityOfReaction())%>;margin-right:100px;margin-left:20px;margin-top:10px;padding-left:10px;padding-top:10px;padding-bottom:5px;border-bottom: 2px solid gray;border-right: 2px solid #999;border-top: 1px solid #CCC;border-left: 1px solid #CCC;">
                                <b>Allergy:</b> <%= allerg[allergIndex].getDescription() %> <b>Reaction:</b>
                                <%= allerg[allergIndex].getReaction() %>
                                <b>Severity:</b> <%=severityOfReaction(allerg[allergIndex].getSeverityOfReaction())%>
                                <b>Onset of Reaction:</b> <%=onSetOfReaction(allerg[allergIndex].getOnsetOfReaction())%>
                            </div>
                            <% }
                            }%>

                            <div id="interactionsRx"></div>
                            <div id="renalDosing"></div>


                            <!--<div style="background-color:yellow;margin-right:100px;margin-left:20px;margin-top:10px;padding-left:10px;padding-top:10px;padding-bottom:5px;border-bottom: 2px solid gray;border-right: 2px solid #999;border-top: 1px solid #CCC;border-left: 1px solid #CCC;">
                                ACETAMINOPHEN	inhibits	BENZODIAZEPINE, long acting &nbsp;&nbsp;&nbsp;&nbsp;SIGNIFICANCE = MINOR &nbsp;&nbsp;&nbsp;EVIDENCE = POOR
                                </div>
                                <div style="background-color:red;margin-right:100px;margin-left:20px;margin-top:1px;padding-left:10px;padding-top:10px;padding-bottom:5px;border-bottom: 2px solid gray;border-right: 2px solid #999;border-top: 1px solid #CCC;border-left: 1px solid #CCC;">
                                ACETAMINOPHEN	inhibits	BENZODIAZEPINE, long acting &nbsp;&nbsp;&nbsp;&nbsp;SIGNIFICANCE = MINOR &nbsp;&nbsp;&nbsp;EVIDENCE = POOR
                                </div>-->
                            <script language=javascript>
                                function submitPending(randomId, draftRevision, action) { //calls stash action
                                    var path = "${carlos:forJavaScript(ctx)}";
                                    oscarLog("path in submitPending:" + path);
                                    var stashForm = document.forms["RxStashForm"];
                                    stashForm.elements["randomId"].value = randomId;
                                    stashForm.elements["draftRevision"].value = draftRevision;
                                    stashForm.elements["action"].value = action;
                                    stashForm.submit();
                                }
                            </script>
                      </td>
                    </tr>

                    <tr>
                        <td><!--5a-->
                            <div class="DivContentSectionHead"><fmt:message key="WriteScript.section5Title"/></div>
                        </td>
                    </tr>


                    <tr>
                        <td>
                            <script type="text/javascript">
                                function ShowDrugInfo(GN, din) {
                                    window.open("<%= request.getContextPath() %>/rx/drugInfo?GN=" + encodeURIComponent(GN)
                                        + (din && din !== "null" && din !== "0" ? "&DIN=" + encodeURIComponent(din) : ""), "_blank",
                                        "location=no, menubar=no, toolbar=no, scrollbars=yes, status=yes, resizable=yes");
                                }

                                function addFavorite(randomId, brandName) {
                                    var favoriteName = window.prompt('Please enter a name for the Favorite:',
                                        brandName);

                                    if (favoriteName !== null && favoriteName.length > 0) {
                                        var form = document.getElementById('addFavoriteWriteScriptForm');
                                        form.elements['randomId'].value = randomId;
                                        form.elements['favoriteName'].value = favoriteName;
                                        form.submit();
                                    }
                                }
                            </script>
                            <table width="100%">
                                <tr>
                                    <td width="60%" valign="top">
                                        <table cellspacing=0 cellpadding=5 width="100%">
                                            
                                            <c:forEach var="rx" items="${bean.stash}" varStatus="loopStatus">
                                                <c:set var="rx2" value="${rx}" />
                                                <c:choose>
                                                    <c:when test="${loopStatus.index == bean.stashIndex}">
                                                        <tr class="tblRowSelected">
                                                    </c:when>
                                                    <c:otherwise>
                                                        <tr>
                                                    </c:otherwise>
                                                </c:choose>
                                                <td>
                                                    <a href="javascript:submitPending('${rx.randomId}', '${carlos:forJavaScript(rx.draftRevision)}', 'edit');">
                                                        <fmt:message key="WriteScript.msgEdit"/>
                                                    </a>
                                                </td>
                                                <td>
                                                    <a href="javascript:submitPending('${rx.randomId}', '${carlos:forJavaScript(rx.draftRevision)}', 'delete');">
                                                        <fmt:message key="WriteScript.msgDelete"/>
                                                    </a>
                                                </td>
                                                <td>
                                                    <a href="javascript:submitPending('${rx.randomId}', '${carlos:forJavaScript(rx.draftRevision)}', 'edit');">
                                                        ${carlos:forHtml(rx.rxDisplay)}
                                                    </a>
                                                </td>
                                                <td>
                                                    <a href="javascript:ShowDrugInfo('<carlos:encode value='${rx2.genericName}' context="javaScriptAttribute"/>', '<carlos:encode value='${rx2.regionalIdentifier}' context="javaScriptAttribute"/>');">
                                                        <fmt:message key="WriteScript.msgInfo"/>
                                                    </a>
                                                </td>
                                                <td>
                                                    <c:set var="drugNameForFavorite" value="${rx2.custom ? rx2.customName : rx2.brandName}"/>
                                                    <a href="javascript:addFavorite('${rx2.randomId}', '<carlos:encode value='<%= (String)pageContext.getAttribute("drugNameForFavorite") %>' context="javaScript"/>');">
                                                        <fmt:message key="WriteScript.msgAddtoFavorites"/>
                                                    </a>
                                                </td>
                                                </tr>
                                            </c:forEach>

                                        </table>
                                    </td>
                                    <td width="40%"><%--
                                <div id="interactionsRx"></div>
                                <div id="renalDosing"></div>
                                --%>
                                        &nbsp;
                                    </td>
                                </tr>
                            </table>
                        </td>

        </tr>
    </table>
            </td>
        </tr>

    <tr>
        <td height="0%"
            style="border-bottom: 2px solid #A9A9A9; border-top: 2px solid #A9A9A9;"></td>
        <td height="0%"
            style="border-bottom: 2px solid #A9A9A9; border-top: 2px solid #A9A9A9;"></td>
    </tr>

    <tr>
        <td width="100%" height="0%" colspan="2">&nbsp;</td>
    </tr>

    <tr>
        <td width="100%" height="0%" style="padding: 5px;" bgcolor="#DCDCDC"
            colspan="2">
            <script language=javascript>
                <%if ( specialStringLen == 0 ){
                        out.write("first=false;");
                  }else{
                        //out.write("calcQtyflag=false;");
                  }

                %>

                function customQty(quan) {
                    if (calcQuantity() == quan || quan == null) {
                        document.forms.frm.autoQty.checked = true;
                    } else {
                        document.forms.frm.autoQty.checked = false;
                    }
                }

                // Preserve stored values, including historical options no longer in current lists.
                restoreEditorSelect(frm.elements['method'], '<carlos:encode value='<%= thisForm.getMethod() %>' context="javaScriptBlock"/>');
                restoreEditorSelect(frm.elements['unit'], '<carlos:encode value='<%= thisForm.getUnit() %>' context="javaScriptBlock"/>');
                restoreEditorSelect(frm.elements['route'], '<carlos:encode value='<%= thisForm.getRoute() %>' context="javaScriptBlock"/>');
                restoreEditorSelect(frm.elements['frequencyCode'], '<carlos:encode value='<%= thisForm.getFrequencyCode() %>' context="javaScriptBlock"/>');
                restoreEditorSelect(frm.elements['durationUnit'], '<carlos:encode value='<%= thisForm.getDurationUnit() %>' context="javaScriptBlock"/>');
                customQty('<carlos:encode value='<%= quan %>' context="javaScriptBlock"/>');
                // Keep the stored instructions verbatim until a dosing control is edited.
                <oscar:oscarPropertiesCheck property="RENAL_DOSING_DS" value="yes">

                function getRenalDosingInformation(origRequest) {
                    var dummie = "";
                    var url = "<%= request.getContextPath() %>/rx/ViewRenalDosing";
                    var ran_number = Math.round(Math.random() * 1000000);
                    var params = "demographicNo=<%=bean.getDemographicNo()%>&atcCode=<%=atcCode%>&rand=" + ran_number;  //hack to get around ie caching the page
                    //alert(params);
                    CarlosAjax.updater('renalDosing', url, {method: 'GET', parameters: params});
                    //alert(origRequest.responseText);
                }

                getRenalDosingInformation();
                </oscar:oscarPropertiesCheck>

                function callReplacementWebService(url, id) {
                    oscarLog("in callReplacementWebService writescript.jsp: " + url + "--" + id);
                    var ran_number = Math.round(Math.random() * 1000000);
                    var params = "demographicNo=<%=bean.getDemographicNo()%>&atcCode=<%=atcCode%>&rand=" + ran_number;  //hack to get around ie caching the page
                    CarlosAjax.updater(id, url, {method: 'GET', parameters: params});
                }

                // callReplacementWebService("/rx/ViewInteractionDisplay",'interactionsRx');

            </script>
        </td>
    </tr>

    </table>
    </form>
    </body>
</html>
<%long end = System.currentTimeMillis() - start; %>

<%!

    String severityOfReaction(String s) {
        Hashtable h = new Hashtable();
        h.put("1", "Mild");
        h.put("2", "Moderate");
        h.put("3", "Severe");

        String retval = (String) h.get(s);
        if (retval == null) {
            retval = "Unknown";
        }
        return retval;
    }

    String severityOfReactionColor(String s) {
        Hashtable h = new Hashtable();
        h.put("1", "yellow");
        h.put("2", "orange");
        h.put("3", "red");

        String retval = (String) h.get(s);
        if (retval == null) {
            retval = "red";
        }
        return retval;
    }

    String onSetOfReaction(String s) {
        Hashtable h = new Hashtable();
        h.put("1", "Immediate");
        h.put("2", "Gradual");
        h.put("3", "Slow");

        String retval = (String) h.get(s);
        if (retval == null) {
            retval = "Unknown";
        }
        return retval;
    }

    boolean isEmpty(String s) {
        return (s == null || s.length() == 0);
    }
%>
