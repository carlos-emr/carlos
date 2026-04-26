<!DOCTYPE html>
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
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ include file="/WEB-INF/jsp/casemgmt/taglibs.jsp" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<html>
<head>
    <script src="${ctx}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/flatpickr/flatpickr.min.js"></script>

    <link href="${ctx}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
    <link href="${ctx}/library/flatpickr/flatpickr.min.css" rel="stylesheet" type="text/css">
    <link rel="stylesheet" href="${ctx}/css/fontawesome-all.min.css">

    <title><fmt:message key="admin.admin.btnBatchBilling"/></title>
    <script type="text/javascript">
        <!--

        function askFirst(method) {
            if (confirm('<fmt:message key="billing.batchbilling.msgConfirmDelete"/>')) {
                setMethod(method);
            }

            return false;
        }

        function setMethod(val) {
            var element = document.getElementById("method");
            element.value = val;
            document.forms["serviceform"].submit();
        }

        var selected = false;

        function selectAll() {
            var checkboxes = document.getElementsByName("bill");

            for (var idx = 0; idx < checkboxes.length; ++idx) {
                if (selected) {
                    checkboxes[idx].checked = false;
                } else {
                    checkboxes[idx].checked = true;
                }
            }

            selected = !selected;
        }

        function jumpMenu(targ, provider) {
            var servicecode = document.getElementById("service_code");
            var service = servicecode.options[servicecode.selectedIndex].value;
            var providerNo = provider.options[provider.selectedIndex].value;

            if (providerNo != "#") {
                var url = '${ctx}/billing/CA/ON/BatchBill?provider_no=' + encodeURIComponent(providerNo) + '&service_code=' + encodeURIComponent(service);
                if (targ === 'window') {
                    window.location = url;
                } else {
                    // Legacy contract supports targ="self" / "parent"; resolve
                    // dynamically without using eval so we keep behavior parity
                    // without an arbitrary-code-execution sink.
                    var target = window[targ];
                    if (target && typeof target.location !== 'undefined') {
                        target.location = url;
                    } else {
                        window.location = url;
                    }
                }
            }

        }

        function init() {
            <c:if test="${batchModel.rowsAvailable}">
            Calendar.setup({
                inputField: "BillDate",
                ifFormat: "%Y-%m-%d",
                showsTime: false,
                button: "billDate_cal",
                singleClick: true,
                step: 1
            });
            </c:if>
        }

        //-->
    </script>

</head>

<body>
<h3><fmt:message key="admin.admin.btnBatchBilling"/></h3>

<div class="container-fluid">

    <div class="row card card-body bg-body-tertiary d-print-none">

        <form name="serviceform" method="post" action="BatchBill" class="d-flex flex-wrap align-items-center gap-2">
            <input type="hidden" id="method" name="method" value="">

            <div class="col-md-2">
                <fmt:message key="billing.batchbilling.msgProvider"/><br>
                <select name="providers" class="form-select" onChange="jumpMenu('window',this)">
                    <option value="#"><b><fmt:message key="billing.batchbilling.msgProvider"/></b></option>
                    <option value="all" ${batchModel.providerView eq 'all' ? 'selected' : ''}><fmt:message key="billing.batchbilling.msgAllProvider"/></option>
                    <c:forEach var="__p" items="${batchModel.providers}">
                    <option value="${carlos:forHtmlAttribute(__p.providerNo)}"
                            ${batchModel.providerView eq __p.providerNo ? 'selected' : ''}><carlos:encode value="${__p.lastName}" context="html"/>,
                        <carlos:encode value="${__p.firstName}" context="html"/>
                    </option>
                    </c:forEach>
                </select>
            </div>

            <div class="col-md-3">
                <fmt:message key="billing.batchbilling.serviceCode"/>:
                <select id="service_code" class="form-select" name="service_code">
                    <option value="all" ${batchModel.serviceCode eq 'all' ? 'selected' : ''}><fmt:message key="billing.batchbilling.msgAllServiceCode"/></option>
                    <c:forEach var="__sc" items="${batchModel.serviceCodes}">
                    <option value="${carlos:forHtmlAttribute(__sc)}" ${batchModel.serviceCode eq __sc ? 'selected' : ''}><carlos:encode value="${__sc}" context="html"/>
                    </option>
                    </c:forEach>
                </select>
            </div>

            <div class="col-md-4">
                <fmt:message key="billing.batchbilling.msgClinicLocation"/>:
                <select name="clinic_view" class="form-select">
                    <c:forEach var="__cl" items="${batchModel.clinicLocations}">
                    <option value="${carlos:forHtmlAttribute(__cl.code)}"
                            ${batchModel.clinicView eq __cl.code ? 'selected' : ''}><carlos:encode value="${__cl.name}" context="html"/>
                    </option>
                    </c:forEach>
                </select>
            </div>
    </div><!--row well-->

    <div class="row">

        <input type="hidden" name="verCode"
               value="V03"> <input type="hidden" name="curUser"
                                   value="${carlos:forHtmlAttribute(batchModel.userNo)}"> <input type="hidden" name="curDate"
                                                                value="${carlos:forHtmlAttribute(batchModel.nowDate)}"> <input type="hidden"
                                                                                             name="curTime"
                                                                                             value="${carlos:forHtmlAttribute(batchModel.nowTime)}">

        <c:choose>
        <c:when test="${batchModel.rowsAvailable}">

        <button class="btn float-end" type='button' name='print' value='Print' onClick='window.print()'><i
                class="fa-solid fa-print"></i> Print
        </button>
        <br/><input type="checkbox" onclick="selectAll();"><br/><br/>

        <table class="table table-striped table-hover table-sm">
            <thead>
            <tr>
                <th><fmt:message key="billing.batchbilling.msgSelection"/></th>
                <th><fmt:message key="billing.batchbilling.msgDemographic"/></th>
                <th><fmt:message key="billing.batchbilling.msgProviderTitle"/></th>
                <th><fmt:message key="billing.batchbilling.msgService"/></th>
                <th><fmt:message key="billing.batchbilling.msgAmount"/></th>
                <th><fmt:message key="billing.batchbilling.msgDiagnostic"/></th>
                <th><fmt:message key="billing.batchbilling.msgLastBillDate"/></th>

            </tr>
            </thead>
            <tbody>

            <c:forEach var="__row" items="${batchModel.rows}">
            <tr>
                <td><input type="checkbox"
                           name="bill"
                           value="${carlos:forHtmlAttribute(__row.checkboxValue)}">
                </td>
                <td><carlos:encode value="${__row.demoName}" context="html"/></td>
                <td><carlos:encode value="${__row.providerName}" context="html"/>
                </td>
                <td><carlos:encode value="${__row.serviceCode}" context="html"/>
                </td>
                <td><carlos:encode value="${__row.billingAmount}" context="html"/>
                </td>
                <td><carlos:encode value="${__row.diagnosticCode}" context="html"/>
                </td>
                <td><carlos:encode value="${__row.lastBilledDate}" context="html"/>
                </td>
            </tr>
            </c:forEach>

            <tr>
                <td colspan="7">
                    <div class="col-md-3">
                        <fmt:message key="billing.batchbilling.serviceDate"/>
                        <div class="input-group">
                            <input type="text" class="form-control" name="BillDate" id="BillDate"
                                   value="${carlos:forHtmlAttribute(batchModel.defaultBillDate)}"
                                   style="width:90px" autocomplete="off" readonly/>
                            <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                        </div>
                    </div>

                    <div class="col-md-4">
                        <input type="button" class="btn btn-primary" onclick="return setMethod('doBatchBill');"
                               value="<fmt:message key="billing.batchbilling.btnSubmit"/>">
                        <input type="button" class="btn btn-secondary" onclick="return askFirst('remove');"
                               value="<fmt:message key="billing.batchbilling.btnRemove"/>">
                    </div>
                </td>
            </tr>

            </tbody>
        </table>
        </c:when>
        <c:when test="${batchModel.filterApplied}">
        <table class="table table-striped table-hover table-sm">
            <tbody>
            <tr>
                <td>* Make selection above to generate batch billing</td>
            </tr>
            </tbody>
        </table>
        </c:when>
        <c:otherwise>
        <table class="table table-striped table-hover table-sm">
            <tbody>
            <tr>
                <td>Nothing to report</td>
            </tr>
            </tbody>
        </table>
        </c:otherwise>
        </c:choose>

        </form>
    </div>

</div>

<script>
    document.addEventListener('DOMContentLoaded', function () {
        flatpickr('#BillDate', {dateFormat: "Y-m-d", allowInput: true});
        // Guard for standalone loads where the EMR-chrome resizeIframe
        // wrapper isn't on parent.parent — see billingONCorrection.jsp
        // for the same pattern.
        try {
            if (window.parent && window.parent.parent
                    && typeof window.parent.parent.resizeIframe === 'function') {
                window.parent.parent.resizeIframe(document.documentElement.scrollHeight + 300);
            }
        } catch (e) {
            // Cross-origin or detached frame — nothing to size.
        }
    });

</script>
</body>
</html>
