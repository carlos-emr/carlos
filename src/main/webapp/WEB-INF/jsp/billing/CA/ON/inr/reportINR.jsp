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
  Purpose: Supports reportINR in the Ontario billing workflow.
  Expected request model data includes: reportInrModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<!DOCTYPE html>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="admin.admin.btnINRBatchBilling"/></title>
    <script language="JavaScript">
        <!--
        function openBrWindow(theURL, winName, features) {
            window.open(theURL, winName, features);
        }

        function jumpMenu(targ, selObj, restore) {
            eval(targ + ".location='" + selObj.options[selObj.selectedIndex].value + "'");
            if (restore) selObj.selectedIndex = 0;
        }

        var remote = null;

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


        //-->
    </script>
    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/fontawesome-all.min.css">
</head>

<body>
<h3><fmt:message key="admin.admin.btnINRBatchBilling"/></h3>

<div class="container-fluid card card-body bg-body-tertiary">
    <button class="btn btn-secondary" type='button' name='print' value='Print' onClick='window.print()'><i
            class="fa-solid fa-print"></i> Print
    </button>

    <form name="serviceform" method="post"
          action="${carlos:forHtmlAttribute(reportInrModel.inrBillingActionUrl)}">
        Select provider
        <select name="provider" onChange="jumpMenu('parent',this,0)" class="form-select">
            <option value="#">Select Provider</option>
            <option value="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/billing/CA/ON/ViewInrReportINR?provider_no=all"
                    ${reportInrModel.allProvidersSelected ? 'selected' : ''}><b>All Provider</b></option>
            <c:forEach var="prov" items="${reportInrModel.providers}">
                <option value="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/billing/CA/ON/ViewInrReportINR?provider_no=${carlos:forUriComponent(prov.providerNo)}"
                        ${reportInrModel.providerView eq prov.providerNo ? 'selected' : ''}><carlos:encode value="${prov.lastName}"/>,
                    <carlos:encode value="${prov.firstName}"/>
                </option>
            </c:forEach>
        </select>
        Clinic Location:
        <input type="hidden" name="billcenter" value="G">
        <select name="xml_location" datafld='xml_location' class="form-select">
            <c:forEach var="loc" items="${reportInrModel.clinicLocations}">
                <option value="${carlos:forHtmlAttribute(loc.code)}"
                        ${reportInrModel.clinicView eq loc.code ? 'selected' : ''}><carlos:encode value="${loc.name}"/>
                </option>
            </c:forEach>
        </select>
        <input type="hidden" name="verCode" value="V03">
        <input type="hidden" name="curUser" value="${carlos:forHtmlAttribute(reportInrModel.userNo)}">
        <input type="hidden" name="curDate" value="${carlos:forHtmlAttribute(reportInrModel.nowDate)}">
        <input type="hidden" name="curTime" value="${carlos:forHtmlAttribute(reportInrModel.nowTime)}">

        <table class="table table-striped  table-sm">

            <tr>
                <td>Selection</td>
                <td>Demographic</td>
                <td>Provider</td>
                <td>Service</td>
                <td>Amount</td>
                <td>Diagnostic</td>
                <td>Last Bill Date</td>

            </tr>
            <c:forEach var="row" items="${reportInrModel.billRows}">
                <tr>
                    <td width="12%" height="16"><input type="checkbox"
                                                       name="inrbilling${carlos:forHtmlAttribute(row.billingInrNo)}"></td>
                    <td width="22%" height="16"><a href="#"
                                                   onClick='rs("billinginrupdate","${carlos:forJavaScript(pageContext.request.contextPath)}/billing/CA/ON/InrUpdateINRbilling?demono=${carlos:forJavaScript(row.demoNo)}&billinginr_no=${carlos:forJavaScript(row.billingInrNo)}&servicecode=${carlos:forJavaScript(row.serviceCode)}&billingamount=${carlos:forJavaScript(row.billingAmount)}&dxcode=${carlos:forJavaScript(row.diagnosticCode)}&demo_name=${carlos:forJavaScript(row.demoNameUrlEncoded)}&provider_name=${carlos:forJavaScript(row.providerNameUrlEncoded)}","380","300","0")'><carlos:encode value="${row.demoName}"/>
                    </a></td>
                    <td width="22%" height="16"><carlos:encode value="${row.providerName}"/>
                    </td>
                    <td width="12%" height="16"><carlos:encode value="${row.serviceCode}"/>
                    </td>
                    <td width="12%" height="16"><carlos:encode value="${row.billingAmount}"/>
                    </td>
                    <td width="10%" height="16"><carlos:encode value="${row.diagnosticCode}"/>
                    </td>
                    <td width="10%" height="16"><carlos:encode value="${row.lastBillDateLabel}"/>
                    </td>
                </tr>
            </c:forEach>
            <c:choose>
                <c:when test="${reportInrModel.rowCount == 0}">
                    <tr>
                        <td colspan="7">No Match Found</td>
                    </tr>
                </c:when>
                <c:otherwise>
                    <tr>
                        <td>
                            <a href="#"
                               onClick='rs("billingcalendar","${carlos:forJavaScript(pageContext.request.contextPath)}/billing/CA/ON/ViewBillingCalendarPopup?year=${carlos:forJavaScript(reportInrModel.curYear)}&month=${carlos:forJavaScript(reportInrModel.curMonth)}&type=service","380","300","0")'>Service
                                Date:</a>
                            <input type="text" name="xml_appointment_date"
                                   value="${carlos:forHtmlAttribute(reportInrModel.defaultServiceDate)}"
                                   size="12" datafld='xml_appointment_date'>
                        </td>
                        <td colspan="7">
                            <input type="submit" name="submit" value="Generate INR Batch Billing">
                            <input type="hidden" name="rowCount" value="${carlos:forHtmlAttribute(reportInrModel.rowCount)}">
                            <input type="hidden" name="clinic_no" value="${carlos:forHtmlAttribute(reportInrModel.clinicNo)}">
                            <input type="hidden" name="visittype" value="00">
                        </td>
                    </tr>
                </c:otherwise>
            </c:choose>
        </table>
    </form>
</div>
</body>
</html>
