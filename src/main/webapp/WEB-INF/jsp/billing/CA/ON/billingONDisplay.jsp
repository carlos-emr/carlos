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
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<%
    if (session.getAttribute("user") == null) {
        response.sendRedirect(request.getContextPath() + "/logout.htm");
        return;
    }
    %>

<html>
    <head>
        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
        <title><fmt:message key="billing.billingCorrection.title"/></title>
        <link rel="stylesheet" type="text/css" href="billingON.css"/>
        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="${pageContext.request.contextPath}/share/calendar/calendar.css" title="win2k-cold-1"/>
        <!-- main calendar program -->
        <script type="text/javascript" src="${pageContext.request.contextPath}/share/calendar/calendar.js"></script>
        <!-- language for the calendar -->
        <script type="text/javascript"
                src="${pageContext.request.contextPath}/share/calendar/lang/calendar-en.js"></script>
        <!-- the following script defines the Calendar.setup helper function, which makes
               adding a calendar a matter of 1 or 2 lines of code. -->
        <script type="text/javascript"
                src="${pageContext.request.contextPath}/share/calendar/calendar-setup.js"></script>
        <script language="JavaScript">
            <!--
            function setfocus() {
                //document.form1.billing_no.focus();
                //document.form1.billing_no.select();
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
                f0 = escape(document.serviceform.xml_diagnostic_detail.value);
                f1 = document.serviceform.xml_dig_search1.value;
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

            function validateNum(el) {
                var val = el.value;
                var tval = "" + val;
                if (isNaN(val)) {
                    alert("Item value must be numeric.");
                    el.select();
                    el.focus();
                    return false;
                }
                if (val > 999.99) {
                    alert("Item value must be below $1000");
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
                if (!validateNum(document.getElementById("billingamount0"))) {
                    return false;
                }
                if (!validateNum(document.getElementById("billingamount1"))) {
                    return false;
                }
                if (!validateNum(document.getElementById("billingamount2"))) {
                    return false;
                }
                if (!validateNum(document.getElementById("billingamount3"))) {
                    return false;
                }
                if (!validateNum(document.getElementById("billingamount4"))) {
                    return false;
                }
                if (!validateNum(document.getElementById("billingamount5"))) {
                    return false;
                }
                return true;
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

            //-->
        </script>
    </head>

    <body bgcolor="ivory" text="#000000" topmargin="0" leftmargin="0"
          rightmargin="0" onLoad="setfocus()">

    <table width="100%" border="0" class="myYellow">
        <form name="form1" method="post" action="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingONDisplay">
            <tr>
                <th width="30%" align="left"><fmt:message key="billing.billingCorrection.formInvoiceNo"/></th>
                <th width="10%"><input type="text" name="billing_no"
                                       value="<carlos:encode value='${displayModel.billingNo}' context='htmlAttribute'/>" maxsize="10"></th>
                <th width="50%" align="left"><fmt:message key="billing.billingCorrection.msgLastUpdate"/>: <carlos:encode value="${displayModel.updateDate}"/>
                </th>
                <th><input type="button" name="submit" value="Exit"
                           onClick="window.close();"/></th>
            </tr>
        </form>
    </table>

    <!-- RA error -->
    <c:if test="${displayModel.billPresent}">
        <table width="100%" border="0" class="myIvory">
            <c:forEach var="err" items="${displayModel.errorCodes}">
                <tr>
                    <th width="10%"><b><carlos:encode value="${err.code}"/></b></th>
                    <td align="left"><carlos:encode value="${err.description}"/></td>
                </tr>
            </c:forEach>
        </table>
    </c:if>

    <form name="serviceform" method="post"
          action=""
          onsubmit="return validateAllItems()"><input type="hidden"
                                                      name="xml_billing_no" value="<carlos:encode value='${displayModel.billingNo}' context='htmlAttribute'/>"/> <input type="hidden"
                                                                                                         name="update_date"
                                                                                                         value="<carlos:encode value='${displayModel.updateDate}' context='htmlAttribute'/>"/>

        <table width="600" border="0">
            <tr class="myGreen">
                <th align="left" colspan="2"><b><fmt:message key="billing.billingCorrection.msgPatientInformation"/></b></th>
            </tr>
            <tr>
                <td width="54%"><b><fmt:message key="billing.billingCorrection.msgPatientName"/>: <a href="#"
                                                                             onclick="popupPage(720,860,'${pageContext.request.contextPath}/demographic/DemographicEdit?demographic_no=${carlos:forUriComponent(displayModel.demoNo)}');return false;">
                    <carlos:encode value="${displayModel.demoName}"/>
                </a> <input type="hidden" name="demo_name"
                            value="<carlos:encode value='${displayModel.demoName}' context='htmlAttribute'/>"> </b></td>
                <td width="46%"><b><fmt:message key="billing.billingCorrection.formHealth"/>: <carlos:encode value="${displayModel.hin}"/> <input
                        type="hidden" name="xml_hin" value="<carlos:encode value='${displayModel.hin}' context='htmlAttribute'/>"> </b></td>
            </tr>
            <tr>
                <td><b><fmt:message key="billing.billingCorrection.msgSex"/>:
                    <carlos:encode value="${displayModel.demoSex}"/> <input type="hidden" name="demo_sex" value="<carlos:encode value='${displayModel.demoSex}' context='htmlAttribute'/>">
                    <input type="hidden" name="hc_sex" value="<carlos:encode value='${displayModel.HCSex}' context='htmlAttribute'/>"> </b></td>
                <td><b><fmt:message key="billing.billingCorrection.formDOB"/>:
                    <input type="hidden" name="xml_dob" value="<carlos:encode value='${displayModel.demoDOB}' context='htmlAttribute'/>"> <carlos:encode value="${displayModel.demoDOB}"/>
                </b></td>
            </tr>
            <tr>
                <td><strong><fmt:message key="billing.billingCorrection.msgDoctor"/>: <input type="text"
                                                                            name="rd" value="<carlos:encode value='${displayModel.RDoctor}' context='htmlAttribute'/>" size="20"
                                                                            readonly></strong></td>
                <td><strong><fmt:message key="billing.billingCorrection.msgDoctorNo"/>: <input type="text"
                                                                              name="rdohip" value="<carlos:encode value='${displayModel.RDoctorOhip}' context='htmlAttribute'/>"
                                                                              size="8" readonly/></strong> <a
                        href="javascript:referralScriptAttach2('rdohip','rd')">Search</a></td>
            </tr>
        </table>

        <table width="600" border="0">
            <tr class="myGreen">
                <td colspan="2"><strong><fmt:message key="billing.billingCorrection.msgAditInfo"/></strong></td>
            </tr>
            <tr class="myIvory">
                <td width="320"><strong><fmt:message key="billing.billingCorrection.formHCType"/>:</strong> <select
                        name="hc_type" style="font-size: 80%;">
                    <option value="ON" ${displayModel.HCTYPE eq 'ON' ? 'selected' : ''}>ON-Ontario</option>
                    <option value="AB" ${displayModel.HCTYPE eq 'AB' ? 'selected' : ''}>AB-Alberta</option>
                    <option value="BC" ${displayModel.HCTYPE eq 'BC' ? 'selected' : ''}>BC-British
                        Columbia
                    </option>
                    <option value="MB" ${displayModel.HCTYPE eq 'MB' ? 'selected' : ''}>MB-Manitoba</option>
                    <option value="NL" ${displayModel.HCTYPE eq 'NL' ? 'selected' : ''}>NL-Newfoundland</option>
                    <option value="NB" ${displayModel.HCTYPE eq 'NB' ? 'selected' : ''}>NB-New
                        Brunswick
                    </option>
                    <option value="YT" ${displayModel.HCTYPE eq 'YT' ? 'selected' : ''}>YT-Yukon</option>
                    <option value="NS" ${displayModel.HCTYPE eq 'NS' ? 'selected' : ''}>NS-Nova
                        Scotia
                    </option>
                    <option value="PE" ${displayModel.HCTYPE eq 'PE' ? 'selected' : ''}>PE-Prince
                        Edward Island
                    </option>
                    <option value="SK" ${displayModel.HCTYPE eq 'SK' ? 'selected' : ''}>SK-Saskatchewan</option>
                </select></td>
                <td width="270"><strong><fmt:message key="billing.billingCorrection.formManualReview"/>: <input
                        type="checkbox" name="m_review" value="Y"
                        ${displayModel.MReview eq 'Y' ? 'checked' : ''}> </strong></td>
            </tr>
        </table>

        <table width="600" border="0">
            <tr class="myGreen">
                <td><b><fmt:message key="billing.billingCorrection.msgBillingInf"/></b></td>
                <td width="46%"><fmt:message key="billing.billingCorrection.btnBillingDate"/><img
                        src="${pageContext.request.contextPath}/images/cal.gif" id="xml_appointment_date_cal"/>: <input
                        type="text" id="xml_appointment_date" name="xml_appointment_date"
                        value="<carlos:encode value='${displayModel.billDate}' context='htmlAttribute'/>" size="10"/></td>
            </tr>
            <tr>
                <td width="54%"><b><fmt:message key="billing.billingCorrection.formBillingType"/>: </b> <input
                        type="hidden" name="xml_status" value="<carlos:encode value='${displayModel.billType}' context='htmlAttribute'/>"> <select
                        style="font-size: 80%;" name="status">
                    <option value=""><fmt:message key="billing.billingCorrection.formSelectBillType"/></option>
                    <option value="H" ${displayModel.billType eq 'H' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeH"/></option>
                    <option value="O" ${displayModel.billType eq 'O' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeO"/></option>
                    <option value="P" ${displayModel.billType eq 'P' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeP"/></option>
                    <option value="N" ${displayModel.billType eq 'N' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeN"/></option>
                    <option value="W" ${displayModel.billType eq 'W' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeW"/></option>
                    <option value="B" ${displayModel.billType eq 'B' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeB"/></option>
                    <option value="S" ${displayModel.billType eq 'S' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeS"/></option>
                    <option value="X" ${displayModel.billType eq 'X' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeX"/></option>
                    <option value="D" ${displayModel.billType eq 'D' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formBillTypeD"/></option>
                </select></td>
                <td width="46%"><b> Pay Program:</b> <input type="hidden"
                                                            name="xml_payProgram" value="<carlos:encode value='${displayModel.billDate}' context='htmlAttribute'/>"/><select
                        style="font-size: 80%;" name="payProgram">
                    <c:forEach var="pt" items="${displayModel.paymentTypes}">
                        <option value="<carlos:encode value='${pt.value}' context='htmlAttribute'/>"
                                ${displayModel.payProgram eq pt.value ? 'selected' : ''}><carlos:encode value="${pt.label}"/>
                        </option>
                    </c:forEach>
                </select></td>
            </tr>
            <tr class="myGreen">
                <td width="54%"><b><c:choose><c:when test="${displayModel.rmaEnabled}">Clinic Nbr</c:when><c:otherwise><fmt:message key="billing.billingCorrection.formVisitType"/></c:otherwise></c:choose>:</b>
                    <input type="hidden"
                           name="xml_clinic_ref_code" value="<carlos:encode value='${displayModel.location}' context='htmlAttribute'/>"> <select
                            name="clinic_ref_code">
                        <option value=""><fmt:message key="billing.billingCorrection.msgSelectLocation"/></option>
                        <c:forEach var="loc" items="${displayModel.locations}">
                            <option value="<carlos:encode value='${loc.number}' context='htmlAttribute'/>"
                                    ${displayModel.location eq loc.number ? 'selected' : ''}><carlos:encode value="${loc.number}"/>
                                | <carlos:encode value="${loc.label}"/>
                            </option>
                        </c:forEach>
                    </select></td>
                <td width="46%"><b><fmt:message key="billing.billingCorrection.formBillingPhysician"/>: </b> <select
                        style="font-size: 80%;" name="provider_no">
                    <option value=""><fmt:message key="billing.billingCorrection.msgSelectProvider"/></option>
                    <c:forEach var="p" items="${displayModel.providers}">
                        <option value="<carlos:encode value='${p.providerNo}' context='htmlAttribute'/>"
                                ${displayModel.provider eq p.providerNo ? 'selected' : ''}><carlos:encode value="${p.providerNo}"/> |
                            <carlos:encode value="${p.lastName}"/>, <carlos:encode value="${p.firstName}"/>
                        </option>
                    </c:forEach>
                </select> <input type="hidden" name="xml_provider_no" value="<carlos:encode value='${displayModel.provider}' context='htmlAttribute'/>"></td>
            </tr>
            <tr>
                <td width="54%"><b> <fmt:message key="billing.billingCorrection.formVisitType"/>: </b> <input
                        type="hidden" name="xml_visittype" value="<carlos:encode value='${displayModel.visitType}' context='htmlAttribute'/>"> <select
                        style="font-size: 80%;" name="visittype">
                    <option value=""><fmt:message key="billing.billingCorrection.msgSelectVisitType"/></option>
                    <c:choose>
                        <c:when test="${displayModel.rmaEnabled}">
                            <c:forEach var="cn" items="${displayModel.clinicNbrs}">
                                <option value="<carlos:encode value='${cn.valueString}' context='htmlAttribute'/>" ${fn:startsWith(displayModel.visitType, cn.value) ? 'selected' : ''}><carlos:encode value="${cn.valueString}"/>
                                </option>
                            </c:forEach>
                        </c:when>
                        <c:otherwise>
                            <option value="00" ${displayModel.visitType eq '00' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formClinicVisit"/></option>
                            <option value="01" ${displayModel.visitType eq '01' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formOutpatientVisit"/></option>
                            <option value="02" ${displayModel.visitType eq '02' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formHospitalVisit"/></option>
                            <option value="03" ${displayModel.visitType eq '03' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formER"/></option>
                            <option value="04" ${displayModel.visitType eq '04' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formNursingHome"/></option>
                            <option value="05" ${displayModel.visitType eq '05' ? 'selected' : ''}><fmt:message key="billing.billingCorrection.formHomeVisit"/></option>
                        </c:otherwise>
                    </c:choose>
                </select></td>
                <td width="46%"><b> <input type="hidden" name="xml_visitdate"
                                           value="<carlos:encode value='${displayModel.visitDate}' context='htmlAttribute'/>"/> <fmt:message key="billing.billingCorrection.btnAdmissionDate"/><img
                        src="${pageContext.request.contextPath}/images/cal.gif" id="xml_vdate_cal"/>: <input
                        type="text" id="xml_vdate" name="xml_vdate" value="<carlos:encode value='${displayModel.visitDate}' context='htmlAttribute'/>"
                        size="10"/></b></td>
            </tr>
        </table>

        <table width="600" border="0" cellspacing="1" cellpadding="0">
            <tr class="myYellow">
                <td width="30%" colspan="2"><b><fmt:message key="billing.billingCorrection.formServiceCode"/></b></td>
                <th width="50%"><b><fmt:message key="billing.billingCorrection.formDescription"/></b></th>
                <th width="3%"><b><fmt:message key="billing.billingCorrection.formUnit"/></b></th>
                <th width="13%" align="right"><b><fmt:message key="billing.billingCorrection.formFee"/></b></th>
                <th><font size="-1">Settle</font></th>
            </tr>
            <c:forEach var="row" items="${displayModel.serviceRows}">
                <tr>
                    <th width="25%"><input type="hidden"
                                           name="xml_service_code${row.rowIndex}" value="<carlos:encode value='${row.serviceCode}' context='htmlAttribute'/>">
                        <input type="text" style="width: 100%"
                               name="servicecode${row.rowIndex - 1}" value="<carlos:encode value='${row.serviceCode}' context='htmlAttribute'/>"></th>
                    <td><a href="#" onClick="scScriptAttach('servicecode${row.rowIndex - 1}')">Search</a></td>
                    <th><font size="-1"><carlos:encode value="${row.serviceDesc}"/></font>
                    </th>
                    <th><input type="hidden" name="xml_billing_unit${row.rowIndex}"
                               value="<carlos:encode value='${row.billingUnit}' context='htmlAttribute'/>"> <input type="text"
                                                                style="width: 100%" name="billingunit${row.rowIndex - 1}"
                                                                value="<carlos:encode value='${row.billingUnit}' context='htmlAttribute'/>" size="5" maxlength="5"></th>
                    <th align="right"><input type="hidden"
                                             name="xml_billing_amount${row.rowIndex}" value="<carlos:encode value='${row.billAmount}' context='htmlAttribute'/>">
                        <input type="text" style="width: 100%" size="5" maxlength="6"
                               id="billingamount${row.rowIndex - 1}" name="billingamount${row.rowIndex - 1}"
                               value="<carlos:encode value='${row.billAmount}' context='htmlAttribute'/>" onchange="javascript:validateNum(this)"></th>
                    <td align="center"><input type="checkbox"
                                              name="itemStatus${row.rowIndex - 1}" id="itemStatus${row.rowIndex - 1}"
                                              value="S" ${row.settled ? 'checked' : ''}></td>
                </tr>
            </c:forEach>
            <c:if test="${displayModel.billPresent}">
                <tr>
                    <td><input type="text" style="width: 100%"
                               name="servicecode${displayModel.trailingRowIndex - 1}" value=""></td>
                    <td><a href="#"
                           onClick="scScriptAttach('servicecode${displayModel.trailingRowIndex - 1}')">Search</a></td>
                    <td>&nbsp;</td>
                    <td><input type="text" style="width: 100%"
                               name="billingunit${displayModel.trailingRowIndex - 1}" value="" size="5" maxlength="5"></td>
                    <td align="right"><input type="text" style="width: 100%"
                                             name="billingamount${displayModel.trailingRowIndex - 1}" id="billingamount${displayModel.trailingRowIndex - 1}"
                                             value="" size="5" maxlength="5"></td>
                </tr>
            </c:if>

            <tr class="myGreen">
                <td colspan="5"><b> <fmt:message key="billing.billingCorrection.formDiagnosticCode"/></b></td>
            </tr>
            <tr>
                <td colspan="4"><input type="hidden" name="xml_diagnostic_code"
                                       value="<carlos:encode value='${displayModel.diagCode}' context='htmlAttribute'/>"> <input type="text"
                                                                     style="font-size: 80%;"
                                                                     name="xml_diagnostic_detail"
                                                                     value="<carlos:encode value='${displayModel.diagCode}' context='htmlAttribute'/>" size="50"> <input
                        type="hidden"
                        name="xml_dig_search1"> <a href="javascript:ScriptAttach()"><fmt:message key="billing.billingCorrection.btnDXSearch"/></a></td>
            </tr>
            <tr>
                <td colspan="4"><input type="button" name="submit" value="Exit"
                                       onClick="window.close();"/></td>
            </tr>
            <tr>
                <td colspan="4">Billing Notes:<br>
                    <textarea name="comment" cols="60" rows="4"><carlos:encode value="${displayModel.comment}"/></textarea>
                </td>
            </tr>
        </table>
    </form>
    </body>
    <script type="text/javascript">
        Calendar.setup({
            inputField: "xml_appointment_date",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "xml_appointment_date_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "xml_vdate",
            ifFormat: "%Y-%m-%d",
            showsTime: false,
            button: "xml_vdate_cal",
            singleClick: true,
            step: 1
        });
    </script>

</html>
