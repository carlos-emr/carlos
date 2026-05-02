<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Purpose: Supports billingShortcutPg1 in the Ontario billing workflow.
  Expected request model data includes: shortcutPg1Model.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html>
<head>
    <script type="text/javascript" src="${ctx}/js/global.js"></script>
    <title>HospitalBilling</title>
    <link rel="stylesheet" type="text/css" href="billingON.css"/>

    <!-- calendar stylesheet -->
    <link rel="stylesheet" type="text/css" media="all"
          href="${ctx}/share/calendar/calendar.css" title="win2k-cold-1"/>
    <!-- main calendar program -->
    <script type="text/javascript" src="${ctx}/share/calendar/calendar.js"></script>
    <!-- language for the calendar -->
    <script type="text/javascript"
            src="${carlos:forHtmlAttribute(ctx)}/share/calendar/lang/<fmt:message key="global.javascript.calendar"/>"></script>
    <!-- the following script defines the Calendar.setup helper function, which makes
           adding a calendar a matter of 1 or 2 lines of code. -->
    <script type="text/javascript"
            src="${ctx}/share/calendar/calendar-setup.js"></script>
    <script type="text/javascript" src="${ctx}/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="${ctx}/library/jquery/jquery-compat.js"></script>
    <script type="text/javascript" language="JavaScript">

        <!--
        window.focus();

        function checkSli() {
            var needsSli = false;
            jQuery("input[name^=code_xml_]:checked").each(function () {
                needsSli = needsSli || (jQuery("input[name='sli_xml_" + this.name.substring(9) + "']").val() === 'true');
            });
            jQuery("input[name^=serviceDate][value!='']").each(function () {
                needsSli = needsSli || (jQuery("input[name='sli_xml_" + this.value + "']").val() === 'true');
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
            <c:if test="${!shortcutPg1Model.rmaEnabled}">
            else if (document.forms[0].xml_visittype.options[2].selected && (document.forms[0].xml_vdate.value == "" || document.forms[0].xml_vdate.value == "0000-00-00")) {
                alert("Need an admission date.");
                return false;
            }
            </c:if>

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
            var mm = str_date.substring(str_date.indexOf("-") + 1, str_date.lastIndexOf("-"));
            var dd = str_date.substring(str_date.lastIndexOf("-") + 1);
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
            awnd = rs('att', ('${ctx}/billing/CA/ON/ViewSearchRefDoc?param=' + t0), 600, 600, 1);
            awnd.focus();
        }

        function referralScriptAttach2(elementName, name2) {
            var d = elementName;
            t0 = escape("document.forms[0].elements[\'" + d + "\'].value");
            t1 = escape("document.forms[0].elements[\'" + name2 + "\'].value");
            awnd = rs('att', ('${ctx}/billing/CA/ON/ViewSearchRefDoc?param=' + t0 + '&param2=' + t1), 600, 600, 1);
            awnd.focus();
        }

        function dxScriptAttach(name2) {
            f0 = escape(document.forms[0].dxCode.value);
            f1 = escape("document.forms[0].elements[\'" + name2 + "\'].value");
            awnd = rs('att', '${ctx}/billing/CA/ON/ViewBillingDigSearch?name=' + f0 + '&search=&name2=' + f1, 600, 600, 1);
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

        <%-- service-type panel iterates the pre-loaded
             shortcutPg1Model.serviceTypes list. The assembler does the
             findServiceTypesByStatus call + sanitisation; the JSP just
             walks the result. --%>
        <c:forEach var="__st" items="${shortcutPg1Model.serviceTypes}" varStatus="__stStatus">
            <tr bgcolor="${__stStatus.count % 2 == 0 ? '#FFFFFF' : '#EEEEFF'}">
                <td colspan="2"><b><font size="-2" color="#7A388D"><a
                        href="${ctx}/billing/CA/ON/billingShortcutPg1View?billForm=${carlos:forUriComponent(__st.code)}&hotclick=&appointment_no=${carlos:forUriComponent(shortcutPg1Model.requestParamEchoes['appointment_no'])}&demographic_name=${carlos:forUriComponent(shortcutPg1Model.demoName)}&demographic_no=${carlos:forUriComponent(shortcutPg1Model.requestParamEchoes['demographic_no'])}&user_no=${carlos:forUriComponent(shortcutPg1Model.userProviderNo)}&apptProvider_no=${carlos:forUriComponent(shortcutPg1Model.requestParamEchoes['apptProvider_no'])}&providerview=${carlos:forUriComponent(shortcutPg1Model.providerView)}&appointment_date=${carlos:forUriComponent(shortcutPg1Model.requestParamEchoes['appointment_date'])}&status=${carlos:forUriComponent(shortcutPg1Model.requestParamEchoes['status'])}&start_time=${carlos:forUriComponent(shortcutPg1Model.requestParamEchoes['start_time'])}&bNewForm=1"
                        onClick="showHideLayers('Layer1','','hide');"><carlos:encode value="${__st.name}" context="html"/>
                </a></font></b></td>
            </tr>
        </c:forEach>
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

        <%-- dx-code panel iterates pre-loaded shortcutPg1Model.dxCodes. --%>
        <c:forEach var="__dx" items="${shortcutPg1Model.dxCodes}" varStatus="__dxStatus">
            <tr bgcolor="${__dxStatus.count % 2 == 0 ? '#FFFFFF' : '#EEEEFF'}">
                <td width="18%"><b><font size="-2" color="#7A388D"><a
                        href="#"
                        onClick="document.forms[0].dxCode.value='<carlos:encode value='${__dx.code}' context='javaScript'/>';showHideLayers('Layer2','','hide');return false;"><carlos:encode value="${__dx.code}" context="html"/>
                </a></font></b></td>
                <td colspan="2"><font size="-2" color="#7A388D"><a
                        href="#"
                        onClick="document.forms[0].dxCode.value='<carlos:encode value='${__dx.code}' context='javaScript'/>';showHideLayers('Layer2','','hide');return false;">
                    <carlos:encode value="${fn:length(__dx.description) < 56 ? __dx.description : fn:substring(__dx.description, 0, 55)}" context="html"/>
                </a></font></td>
            </tr>
        </c:forEach>
    </table>
</div>

<form method="post" name="titlesearch" action="${ctx}/billing/CA/ON/BillingShortcutPg2Save"
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
                        <td bgcolor="#99CCCC" align="center"><font color="black"><fmt:message key="billing.hospitalBilling.msgDates"/>${shortcutPg1Model.msg}
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
                                                  readonly><carlos:encode value='${shortcutPg1Model.requestParamEchoes["billDate"]}' context="html"/></textarea>
                                    </td>
                                    <td nowrap align="center"><fmt:message key="billing.billingCorrection.formServiceCode"/> x <fmt:message key="billing.billingCorrection.formUnit"/><br>
                                        <input type="text" name="serviceDate0" size="5" maxlength="5"
                                               value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes["serviceDate0"]}' context="htmlAttribute"/>">x
                                        <input type="text" name="serviceUnit0" size="2" maxlength="2"
                                               style=""
                                               value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes["serviceUnit0"]}' context="htmlAttribute"/>"><br>
                                        <input type="text" name="serviceDate1" size="5" maxlength="5"
                                               value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes["serviceDate1"]}' context="htmlAttribute"/>">x
                                        <input type="text" name="serviceUnit1" size="2" maxlength="2"
                                               style=""
                                               value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes["serviceUnit1"]}' context="htmlAttribute"/>"><br>
                                        <input type="text" name="serviceDate2" size="5" maxlength="5"
                                               value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes["serviceDate2"]}' context="htmlAttribute"/>">x
                                        <input type="text" name="serviceUnit2" size="2" maxlength="2"
                                               style=""
                                               value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes["serviceUnit2"]}' context="htmlAttribute"/>"><br>
                                        <input type="text" name="serviceDate3" size="5" maxlength="5"
                                               value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes["serviceDate3"]}' context="htmlAttribute"/>">x
                                        <input type="text" name="serviceUnit3" size="2" maxlength="2"
                                               style=""
                                               value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes["serviceUnit3"]}' context="htmlAttribute"/>"><br>
                                        <input type="text" name="serviceDate4" size="5" maxlength="5"
                                               value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes["serviceDate4"]}' context="htmlAttribute"/>">x
                                        <input type="text" name="serviceUnit4" size="2" maxlength="2"
                                               style=""
                                               value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes["serviceUnit4"]}' context="htmlAttribute"/>">
                                    </td>
                                    <td valign="top">
                                        <table border="0" cellspacing="0" cellpadding="0" width="100%">
                                            <tr>
                                                <td><a href="#"
                                                       onClick="showHideLayers('Layer2','','show','Layer1','','hide'); return false;"><fmt:message key="billing.hospitalBilling.formDx"/></a><br>
                                                    <input type="text" name="dxCode" size="5" maxlength="5"
                                                           onDblClick="dxScriptAttach('dxCode')"
                                                           value="<carlos:encode value='${empty shortcutPg1Model.requestParamEchoes["dxCode"] ? shortcutPg1Model.dxCode : shortcutPg1Model.requestParamEchoes["dxCode"]}' context="htmlAttribute"/>">
                                                </td>
                                                <td>Cal.% mode<br>
                                                    <c:set var="__rulePerc" value="${shortcutPg1Model.requestParamEchoes['rulePerc']}"/>
                                                    <select name="rulePerc">
                                                        <option value="onlyAboveCode"
                                                                ${'onlyAboveCode' eq __rulePerc ? 'selected' : ''}>
                                                            <fmt:message key="billing.hospitalBilling.optAbove"/>
                                                        </option>
                                                        <option value="allAboveCode"
                                                                ${'allAboveCode' eq __rulePerc ? 'selected' : ''}>
                                                            <fmt:message key="billing.hospitalBilling.optAll"/></option>
                                                    </select></td>
                                            </tr>
                                        </table>

                                        <hr>
                                        <a
                                                href="javascript:referralScriptAttach2('referralCode','referralDocName')"><fmt:message key="billing.hospitalBilling.btnReferral"/>
                                        </a> <input type="text" name="referralCode" size="5"
                                                    maxlength="6"
                                                    value="<carlos:encode value='${empty shortcutPg1Model.requestParamEchoes["referralCode"] ? shortcutPg1Model.referralDoctorOhip : shortcutPg1Model.requestParamEchoes["referralCode"]}' context="htmlAttribute"/>"><br>
                                        <input type="text" name="referralDocName" size="22" maxlength="30"
                                               value="<carlos:encode value='${empty shortcutPg1Model.requestParamEchoes["referralDocName"] ? shortcutPg1Model.referralDoctorName : shortcutPg1Model.requestParamEchoes["referralDocName"]}' context="htmlAttribute"/>">
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
                                        <c:choose>
                                            <c:when test="${fn:length(shortcutPg1Model.providers) == 1}">
                                                <c:set var="__pr0" value="${shortcutPg1Model.providers[0]}"/>
                                                <option value="<carlos:encode value='${__pr0.proOHIP}' context='htmlAttribute'/>"
                                                        ${shortcutPg1Model.providerView eq __pr0.proOHIP ? 'selected' : ''}>
                                                    <b><carlos:encode value="${__pr0['last_name']}" context="html"/>,
                                                        <carlos:encode value="${__pr0['first_name']}" context="html"/>
                                                    </b></option>
                                            </c:when>
                                            <c:otherwise>
                                                <option value="000000"
                                                        ${shortcutPg1Model.providerView eq '000000' ? 'selected' : ''}><b><fmt:message key="billing.billingCorrection.msgSelectProvider"/>
                                                </b></option>
                                                <c:forEach var="__pr" items="${shortcutPg1Model.providers}">
                                                    <option value="<carlos:encode value='${__pr.proOHIP}' context='htmlAttribute'/>"
                                                            ${shortcutPg1Model.providerView eq __pr.proOHIP ? 'selected' : ''}>
                                                        <b><carlos:encode value="${__pr['last_name']}" context="html"/>,
                                                            <carlos:encode value="${__pr['first_name']}" context="html"/>
                                                        </b></option>
                                                </c:forEach>
                                            </c:otherwise>
                                        </c:choose>
                                    </select></td>
                                    <td nowrap width="30%" align="center"><b><fmt:message key="billing.hospitalBilling.frmAssgnPhysician"/></b></td>
                                    <td width="20%"><carlos:encode value="${shortcutPg1Model.assgProviderDisplay}" context="html"/>
                                    </td>
                                </tr>
                                <tr>

                                    <td width="30%">
                                        <b><c:choose><c:when test="${shortcutPg1Model.rmaEnabled}">Clinic Nbr</c:when><c:otherwise><fmt:message key="billing.billingCorrection.formVisitType"/></c:otherwise></c:choose></b></td>
                                    <td width="20%"><select name="xml_visittype">
                                        <%-- clinic-nbr dropdown driven by pre-loaded
                                             shortcutPg1Model.clinicNbrs. The auto-select uses
                                             shortcutPg1Model.selectedClinicNbrPrefix from the user
                                             provider's comments XML. --%>
                                        <c:choose>
                                            <c:when test="${shortcutPg1Model.rmaEnabled}">
                                                <c:forEach var="__c" items="${shortcutPg1Model.clinicNbrs}">
                                                    <option value="<carlos:encode value='${__c.displayLabel}' context='htmlAttribute'/>" ${fn:startsWith(shortcutPg1Model.selectedClinicNbrPrefix, __c.nbrValue) ? 'selected' : ''}><carlos:encode value="${__c.displayLabel}" context="html"/>
                                                    </option>
                                                </c:forEach>
                                            </c:when>
                                            <c:otherwise>
                                                <option value="00| Clinic Visit"
                                                        ${fn:startsWith(shortcutPg1Model.visitType, '00') ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formClinicVisit"/>
                                                </option>
                                                <option value="01| Outpatient Visit"
                                                        ${fn:startsWith(shortcutPg1Model.visitType, '01') ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formOutpatientVisit"/>
                                                </option>
                                                <option value="02| Hospital Visit"
                                                        ${fn:startsWith(shortcutPg1Model.visitType, '02') ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formHospitalVisit"/>
                                                </option>
                                                <option value="03| ER"
                                                        ${fn:startsWith(shortcutPg1Model.visitType, '03') ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formER"/></option>
                                                <option value="04| Nursing Home"
                                                        ${fn:startsWith(shortcutPg1Model.visitType, '04') ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formNursingHome"/>
                                                </option>
                                                <option value="05| Home Visit"
                                                        ${fn:startsWith(shortcutPg1Model.visitType, '05') ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formHomeVisit"/>
                                                </option>
                                            </c:otherwise>
                                        </c:choose>
                                    </select></td>

                                    <td width="30%"><b>Billing Type</b></td>
                                    <td width="20%">
                                        <c:set var="__srtBillType" value="${shortcutPg1Model.requestParamEchoes['xml_billtype']}"/>
                                        <select name="xml_billtype">
                                            <option value="ODP | Bill OHIP"
                                                    ${fn:startsWith(__srtBillType, 'ODP') ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeO"/>
                                            </option>
                                            <option value="PAT | Bill Patient"
                                                    ${fn:startsWith(__srtBillType, 'PAT') ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeP"/>
                                            </option>
                                            <option value="WCB | Worker's Compensation Board"
                                                    ${fn:startsWith(__srtBillType, 'WCB') ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeW"/></option>
                                        </select></td>
                                </tr>
                                <tr>
                                    <td><b><fmt:message key="billing.billingCorrection.msgVisitLocation"/></b></td>
                                    <td colspan="3"><select name="xml_location">
                                        <c:set var="__strLocation" value="${empty shortcutPg1Model.requestParamEchoes['xml_location'] ? shortcutPg1Model.clinicView : shortcutPg1Model.requestParamEchoes['xml_location']}"/>
                                        <c:forEach var="__loc" items="${shortcutPg1Model.clinicLocations}">
                                            <c:set var="__locValue" value="${__loc.clinic_location_no}|${__loc.clinic_location_name}"/>
                                            <option
                                                    value="<carlos:encode value='${__locValue}' context='htmlAttribute'/>"
                                                    ${fn:startsWith(__strLocation, __loc.clinic_location_no) ? 'selected' : ''}>
                                                <carlos:encode value="${__loc.clinic_location_name}" context="html"/>
                                            </option>
                                        </c:forEach>
                                    </select></td>
                                </tr>
                                <%--
                                    SLI-code dropdown auto-select reads from
                                    shortcutPg1Model.selectedXmlPSli (assembler pre-loaded
                                    the user-provider's xml_p_sli comments-XML field).
                                --%>
                                <c:set var="__prSli" value="${shortcutPg1Model.selectedXmlPSli}"/>
                                <tr>
                                    <td><b><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode"/></b></td>
                                    <td colspan="3">
                                        <select name="xml_slicode">

                                            <option value="<carlos:encode value='${shortcutPg1Model.clinicNo}' context='htmlAttribute'/>"><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.NA"/></option>

                                            <option value="HDS " ${__prSli eq 'HDS' ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HDS"/></option>

                                            <option value="HED " ${__prSli eq 'HED' ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HED"/></option>

                                            <option value="HIP " ${__prSli eq 'HIP' ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HIP"/></option>

                                            <option value="HOP " ${__prSli eq 'HOP' ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HOP"/></option>

                                            <option value="HRP " ${__prSli eq 'HRP' ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.HRP"/></option>

                                            <option value="IHF " ${__prSli eq 'IHF' ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.IHF"/></option>

                                            <option value="OFF " ${__prSli eq 'OFF' ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.OFF"/></option>

                                            <option value="OTN " ${__prSli eq 'OTN' ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.OTN"/></option>

                                            <option value="PDF " ${__prSli eq 'PDF' ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.PDF"/></option>

                                            <option value="RTF " ${__prSli eq 'RTF' ? 'selected' : ''}><fmt:message key="oscar.billing.CA.ON.billingON.OB.SLIcode.RTF"/></option>
                                        </select>
                                    </td>
                                </tr>
                                <tr>
                                    <td><b><fmt:message key="billing.admissiondate"/></b></td>
                                    <td>
                                        <input type="text" name="xml_vdate" id="xml_vdate"
                                               value="<carlos:encode value='${empty shortcutPg1Model.requestParamEchoes["xml_vdate"] ? shortcutPg1Model.admissionDate : shortcutPg1Model.requestParamEchoes["xml_vdate"]}' context="htmlAttribute"/>"
                                               size='10' maxlength='10'> <img
                                            src="${ctx}/images/cal.gif" id="xml_vdate_cal"></td>
                                    <td colspan="2"><a href="#"
                                                       onClick="showHideLayers('Layer1','','show');return false;"><fmt:message key="billing.billingform"/>
                                    </a>:</font></b> <carlos:encode value="${shortcutPg1Model.currentFormName}" context="html"/>
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
                                    <th width="10%" nowrap><font size="-1" color="#000000"><carlos:encode value="${shortcutPg1Model.headerTitle1}" context="html"/>
                                    </font></th>
                                    <th width="70%" bgcolor="#CCCCFF"><font size="-1"
                                                                            color="#000000">Description</font></th>
                                    <th><font size="-1" color="#000000"> Fee</font></th>
                                </tr>
                                <c:forEach var="__svc1" items="${shortcutPg1Model.serviceCodeCol1}" varStatus="__svc1Status">
                                    <c:set var="__sc" value="${__svc1.serviceCode}"/>
                                    <c:set var="__codeKey" value="code_xml_${__sc}"/>
                                    <c:set var="__unitKey" value="unit_xml_${__sc}"/>
                                    <c:set var="__premium" value="${not empty shortcutPg1Model.propPremium[__sc] ? 'A' : ''}"/>
                                    <tr bgcolor="${__svc1Status.index % 2 == 0 ? '#FFFFFF' : '#EEEEFF'}">
                                        <td nowrap><input type="checkbox"
                                                          name="code_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>" value="checked"
                                            ${'checked' eq shortcutPg1Model.requestParamEchoes[__codeKey] ? 'checked' : ''}>
                                            <b><font size="-1"
                                                     color="${__premium eq 'A' ? '#993333' : 'black'}"><span
                                                    id="sc${__svc1Status.index}<carlos:encode value='${__sc}' context='html'/>"
                                                    onDblClick="onDblClickServiceCode(this)"><carlos:encode value="${__sc}" context="html"/></span></font></b>
                                            <input type="text" name="unit_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>"
                                                   value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes[__unitKey]}' context="htmlAttribute"/>"
                                                   size="1" maxlength="2" style="width: 20px; height: 12px;"></td>
                                        <td <c:if test="${fn:length(__svc1.serviceDesc) > 30}">title="<carlos:encode value='${__svc1.serviceDesc}' context='htmlAttribute'/>"</c:if>><font
                                                size="-1"><carlos:encode value="${fn:length(__svc1.serviceDesc) > 30 ? fn:substring(__svc1.serviceDesc, 0, 30).concat('...') : __svc1.serviceDesc}" context="html"/>
                                            <input type="hidden" name="desc_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>"
                                                   value="<carlos:encode value='${__svc1.serviceDesc}' context='htmlAttribute'/>"/>
                                            <input type="hidden" name="sli_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>" value="<carlos:encode value='${__svc1.serviceSLI}' context='htmlAttribute'/>"/>
                                        </font></td>
                                        <td align="right"><font size="-1"><carlos:encode value="${__svc1.serviceDisp}" context="html"/>
                                        </font> <input
                                                type="hidden" name="price_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>"
                                                value="<carlos:encode value='${__svc1.serviceDisp}' context='htmlAttribute'/>"/> <input type="hidden"
                                                                                  name="perc_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>"
                                                                                  value="<carlos:encode value='${__svc1.servicePercentage}' context='htmlAttribute'/>"/>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </table>

                        </td>
                        <td width="33%" valign="top">

                            <table width="100%" border="1" cellspacing="0" cellpadding="0"
                                   height="0" bordercolorlight="#99A005" bordercolordark="#FFFFFF">
                                <tr bgcolor="#CCCCFF">
                                    <th width="10%" nowrap><font size="-1" color="#000000"><carlos:encode value="${shortcutPg1Model.headerTitle2}" context="html"/>
                                    </font></th>
                                    <th width="70%" bgcolor="#CCCCFF"><font size="-1"
                                                                            color="#000000">Description</font></th>
                                    <th><font size="-1" color="#000000"> Fee</font></th>
                                </tr>
                                <c:forEach var="__svc2" items="${shortcutPg1Model.serviceCodeCol2}" varStatus="__svc2Status">
                                    <c:set var="__sc" value="${__svc2.serviceCode}"/>
                                    <c:set var="__codeKey" value="code_xml_${__sc}"/>
                                    <c:set var="__unitKey" value="unit_xml_${__sc}"/>
                                    <c:set var="__premium" value="${not empty shortcutPg1Model.propPremium[__sc] ? 'A' : ''}"/>
                                    <tr bgcolor="${__svc2Status.index % 2 == 0 ? '#FFFFFF' : '#EEEEFF'}">
                                        <td nowrap><input type="checkbox"
                                                          name="code_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>" value="checked"
                                                ${'checked' eq shortcutPg1Model.requestParamEchoes[__codeKey] ? 'checked' : ''} />
                                            <b><font size="-1"
                                                     color="${__premium eq 'A' ? '#993333' : 'black'}"><span
                                                    id="sc${__svc2Status.index}<carlos:encode value='${__sc}' context='html'/>"
                                                    onDblClick="onDblClickServiceCode(this)"><carlos:encode value="${__sc}" context="html"/></span></font></b>
                                            <input type="text" name="unit_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>"
                                                   value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes[__unitKey]}' context="htmlAttribute"/>"
                                                   size="1" maxlength="2" style="width: 20px; height: 12px;"/></td>
                                        <td <c:if test="${fn:length(__svc2.serviceDesc) > 30}">title="<carlos:encode value='${__svc2.serviceDesc}' context='htmlAttribute'/>"</c:if>><font
                                                size="-1"><carlos:encode value="${fn:length(__svc2.serviceDesc) > 30 ? fn:substring(__svc2.serviceDesc, 0, 30).concat('...') : __svc2.serviceDesc}" context="html"/>
                                            <input type="hidden" name="desc_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>"
                                                   value="<carlos:encode value='${__svc2.serviceDesc}' context='htmlAttribute'/>"/> </font></td>
                                        <td align="right"><font size="-1"><carlos:encode value="${__svc2.serviceDisp}" context="html"/>
                                        </font> <input
                                                type="hidden" name="price_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>"
                                                value="<carlos:encode value='${__svc2.serviceDisp}' context='htmlAttribute'/>"/> <input type="hidden"
                                                                                  name="perc_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>"
                                                                                  value="<carlos:encode value='${__svc2.servicePercentage}' context='htmlAttribute'/>"/>
                                            <input type="hidden" name="sli_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>" value="<carlos:encode value='${__svc2.serviceSLI}' context='htmlAttribute'/>"/>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </table>

                        </td>
                        <td width="33%" valign="top">

                            <table width="100%" border="1" cellspacing="0" cellpadding="0"
                                   height="0" bordercolorlight="#99A005" bordercolordark="#FFFFFF">
                                <tr bgcolor="#CCCCFF">
                                    <th width="10%" nowrap><font size="-1" color="#000000"><carlos:encode value="${shortcutPg1Model.headerTitle3}" context="html"/>
                                    </font></th>
                                    <th width="70%" bgcolor="#CCCCFF"><font size="-1"
                                                                            color="#000000"><fmt:message key="billing.service.desc"/></font></th>
                                    <th><font size="-1" color="#000000"> <fmt:message key="billing.service.fee"/></font></th>
                                </tr>
                                <c:forEach var="__svc3" items="${shortcutPg1Model.serviceCodeCol3}" varStatus="__svc3Status">
                                    <c:set var="__sc" value="${__svc3.serviceCode}"/>
                                    <c:set var="__codeKey" value="code_xml_${__sc}"/>
                                    <c:set var="__unitKey" value="unit_xml_${__sc}"/>
                                    <c:set var="__premium" value="${not empty shortcutPg1Model.propPremium[__sc] ? 'A' : ''}"/>
                                    <tr bgcolor="${__svc3Status.index % 2 == 0 ? '#FFFFFF' : '#EEEEFF'}">
                                        <td nowrap><input type="checkbox"
                                                          name="code_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>" value="checked"
                                                ${'checked' eq shortcutPg1Model.requestParamEchoes[__codeKey] ? 'checked' : ''} />
                                            <b><font size="-1"
                                                     color="${__premium eq 'A' ? '#993333' : 'black'}"><span
                                                    id="sc${__svc3Status.index}<carlos:encode value='${__sc}' context='html'/>"
                                                    onDblClick="onDblClickServiceCode(this)"><carlos:encode value="${__sc}" context="html"/></span></font></b>
                                            <input type="text" name="unit_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>"
                                                   value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes[__unitKey]}' context="htmlAttribute"/>"
                                                   size="1" maxlength="2" style="width: 20px; height: 12px;"/></td>
                                        <td <c:if test="${fn:length(__svc3.serviceDesc) > 30}">title="<carlos:encode value='${__svc3.serviceDesc}' context='htmlAttribute'/>"</c:if>><font
                                                size="-1"><carlos:encode value="${fn:length(__svc3.serviceDesc) > 30 ? fn:substring(__svc3.serviceDesc, 0, 30).concat('...') : __svc3.serviceDesc}" context="html"/>
                                            <input type="hidden" name="desc_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>"
                                                   value="<carlos:encode value='${__svc3.serviceDesc}' context='htmlAttribute'/>"/> </font></td>
                                        <td align="right"><font size="-1"><carlos:encode value="${__svc3.serviceDisp}" context="html"/>
                                        </font> <input
                                                type="hidden" name="price_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>"
                                                value="<carlos:encode value='${__svc3.serviceDisp}' context='htmlAttribute'/>"/> <input type="hidden"
                                                                                  name="perc_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>"
                                                                                  value="<carlos:encode value='${__svc3.servicePercentage}' context='htmlAttribute'/>"/>
                                            <input type="hidden" name="sli_xml_<carlos:encode value='${__sc}' context='htmlAttribute'/>" value="<carlos:encode value='${__svc3.serviceSLI}' context='htmlAttribute'/>"/>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </table>

                        </td>
                    </tr>
                </table>

            </td>
        </tr>

        <input type="hidden" name="clinic_no" value="<carlos:encode value='${shortcutPg1Model.clinicNo}' context='htmlAttribute'/>"/>
        <input type="hidden" name="demographic_no" value="${carlos:forHtmlAttribute(shortcutPg1Model.demoNo)}"/>
        <input type="hidden" name="appointment_no" value="${carlos:forHtmlAttribute(shortcutPg1Model.apptNo)}"/>

        <input type="hidden" name="ohip_version" value="V03G"/>
        <input type="hidden" name="hin" value="<carlos:encode value='${shortcutPg1Model.demoHin}' context='htmlAttribute'/>"/>

        <input type="hidden" name="start_time"
               value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes["start_time"]}' context="htmlAttribute"/>"/>

        <input type="hidden" name="demographic_dob" value="<carlos:encode value='${shortcutPg1Model.demoDob}' context='htmlAttribute'/>"/>

        <input type="hidden" name="apptProvider_no"
               value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes["apptProvider_no"]}' context="htmlAttribute"/>"/>
        <input type="hidden" name="asstProvider_no"
               value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes["asstProvider_no"]}' context="htmlAttribute"/>"/>

        <input type="hidden" name="demographic_name" value="${carlos:forHtmlAttribute(shortcutPg1Model.demoName)}"/>
        <input type="hidden" name="providerview" value="${carlos:forHtmlAttribute(shortcutPg1Model.providerView)}"/>
        <input type="hidden" name="appointment_date"
               value="<carlos:encode value='${shortcutPg1Model.requestParamEchoes["appointment_date"]}' context="htmlAttribute"/>"/>
        <input type="hidden" name="assgProvider_no"
               value="${carlos:forHtmlAttribute(shortcutPg1Model.assignedProviderNo)}"/>
        <input type="hidden" name="billForm" value="${carlos:forHtmlAttribute(shortcutPg1Model.ctlBillForm)}"/>

    </table>
</form>

<br/>
<c:if test="${shortcutPg1Model.historyUnavailable}">
    <div class="alert alert-danger" role="alert" style="margin: 8px 0;">
        <strong>Billing history unavailable.</strong>
        The lookup failed; the table below is empty even if this patient
        has prior bills. Verify history through another channel before
        submitting to avoid duplicate billing.
    </div>
</c:if>
<c:if test="${shortcutPg1Model.historyPartial}">
    <div class="alert alert-warning" role="alert" style="margin: 8px 0;">
        <strong>Billing history may be incomplete.</strong>
        <carlos:encode value="${shortcutPg1Model.historyPartialRowCount}" context="html"/>
        history row(s) could not be displayed. Verify history through another
        channel before submitting to avoid duplicate billing.
    </div>
</c:if>
<%-- Both branches now iterate the unified billingHistory + billingHistoryDetails
     that the assembler builds for legacy and new-ON-billing modes alike. --%>
<c:choose>
    <c:when test="${!shortcutPg1Model.newOnBilling}">
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
                        <c:forEach var="__hist" items="${shortcutPg1Model.billingHistory}" varStatus="__histStatus">
                            <c:set var="__histD" value="${shortcutPg1Model.billingHistoryDetails[__histStatus.index]}"/>
                            <tr bgcolor="${__histStatus.index % 2 == 0 ? 'ivory' : '#EEEEFF'}" align="center">
                                <td><c:choose><c:when test="${empty __hist.billing_no}">&nbsp;</c:when><c:otherwise><carlos:encode value="${__hist.billing_no}" context="html"/></c:otherwise></c:choose>
                                </td>
                                <td><c:choose><c:when test="${empty __hist.billing_date}">&nbsp;</c:when><c:otherwise><carlos:encode value="${__hist.billing_date}" context="html"/></c:otherwise></c:choose>
                                </td>
                                <td><c:choose><c:when test="${empty __hist.visitdate}">&nbsp;</c:when><c:otherwise><carlos:encode value="${__hist.visitdate}" context="html"/></c:otherwise></c:choose>
                                </td>
                                <td><c:choose><c:when test="${empty __histD.service_code}">&nbsp;</c:when><c:otherwise><carlos:encode value="${__histD.service_code}" context="html"/></c:otherwise></c:choose>
                                </td>
                                <td><c:choose><c:when test="${empty __histD.diagnostic_code}">&nbsp;</c:when><c:otherwise><carlos:encode value="${__histD.diagnostic_code}" context="html"/></c:otherwise></c:choose>
                                </td>
                                <td><c:choose><c:when test="${empty __hist.update_date}">&nbsp;</c:when><c:otherwise><carlos:encode value="${__hist.update_date}" context="html"/></c:otherwise></c:choose>
                                </td>
                            </tr>
                        </c:forEach>
                    </table>
                </td>
            </tr>
        </table>
    </c:when>
    <c:otherwise>
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
                        <c:forEach var="__hist" items="${shortcutPg1Model.billingHistory}" varStatus="__histStatus">
                            <c:set var="__histD" value="${shortcutPg1Model.billingHistoryDetails[__histStatus.index]}"/>
                            <tr ${__histStatus.index % 2 == 0 ? 'class="myGreen"' : ''} align="center">
                                <td><c:choose><c:when test="${empty __hist.billing_no}">&nbsp;</c:when><c:otherwise><carlos:encode value="${__hist.billing_no}" context="html"/></c:otherwise></c:choose>
                                </td>
                                <td><c:choose><c:when test="${empty __hist.billing_date}">&nbsp;</c:when><c:otherwise><carlos:encode value="${__hist.billing_date}" context="html"/></c:otherwise></c:choose>
                                </td>
                                <td><c:choose><c:when test="${empty __hist.visitdate}">&nbsp;</c:when><c:otherwise><carlos:encode value="${__hist.visitdate}" context="html"/></c:otherwise></c:choose>
                                </td>
                                <td><c:choose><c:when test="${empty __histD.service_code}">&nbsp;</c:when><c:otherwise><carlos:encode value="${__histD.service_code}" context="html"/></c:otherwise></c:choose>
                                </td>
                                <td><c:choose><c:when test="${empty __histD.diagnostic_code}">&nbsp;</c:when><c:otherwise><carlos:encode value="${__histD.diagnostic_code}" context="html"/></c:otherwise></c:choose>
                                </td>
                                <td><c:choose><c:when test="${empty __hist.update_date}">&nbsp;</c:when><c:otherwise><carlos:encode value="${__hist.update_date}" context="html"/></c:otherwise></c:choose>
                                </td>
                            </tr>
                        </c:forEach>
                    </table>
                </td>
            </tr>
        </table>
    </c:otherwise>
</c:choose>
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
</body>
</html>
