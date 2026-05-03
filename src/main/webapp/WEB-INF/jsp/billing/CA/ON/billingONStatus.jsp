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
  Purpose: Supports billingONStatus in the Ontario billing workflow.
  Expected request model data includes: statusModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<!DOCTYPE html>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>
            <fmt:message key="admin.admin.invoiceRpts"/>
        </title>
        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
        <script src="${pageContext.request.contextPath}/js/table-export.js"></script>
        <script src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/js/jquery.dataTables.min.js"></script>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/css/dataTables.bootstrap5.min.css"
              rel="stylesheet">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/fontawesome-all.min.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.css">
        <script src="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.js"></script>
        <script>
            document.addEventListener('DOMContentLoaded', function () {
                flatpickr("#xml_vdate", {dateFormat: "Y-m-d", allowInput: true});
                flatpickr("#xml_appointment_date", {dateFormat: "Y-m-d", allowInput: true});

                flatpickr("#paymentStartDate", {dateFormat: "Y-m-d", allowInput: true});
                flatpickr("#paymentEndDate", {dateFormat: "Y-m-d", allowInput: true});
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

            // Provider->OHIP map driven from the view model so the runtime
            // lookup is data-only (no scriptlet emission).
            var _providerOhipMap = {};
            <c:forEach var="po" items="${statusModel.providers}">
                _providerOhipMap["<carlos:encode value='${po.providerNo}' context='javaScript'/>"] = "<carlos:encode value='${po.ohipNo}' context='javaScript'/>";
            </c:forEach>

            function changeProvider(shouldSubmit) {
                var index = document.serviceform.providerview.selectedIndex;
                if (index < 0) return;
                var provider_no = document.serviceform.providerview[index].value;
                var ohip = _providerOhipMap[provider_no];
                if (ohip != null && ohip !== undefined) {
                    document.serviceform.provider_ohipNo.value = ohip;
                    if (shouldSubmit) {
                        if (document.getElementById("xml_vdate").value.length > 0
                                && document.getElementById("xml_appointment_date").value.length > 0)
                            document.serviceform.submit();
                    }
                    return;
                }
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
                xmlHttp.open("POST", "${pageContext.request.contextPath}/billing/CA/ON/BillingONStatusERUpdateStatus", true);
                xmlHttp.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
                xmlHttp.send("id=" + encodeURIComponent(idNum) + "&val=" + encodeURIComponent(val));
            }

            function handleStateChange() {
                if (xmlHttp.readyState == 4) {
                    //alert(xmlHttp.status + "0 :go 2" + xmlHttp.responseText);
                    //document.getElementById(ajaxFieldId).innerHTML = xmlHttp.responseText;
                    if (xmlHttp.status == 200) {
                        //alert("go 3" + xmlHttp.responseText);
                        document.getElementById(ajaxFieldId).textContent = xmlHttp.responseText;
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
                var sortName = document.getElementById("sortName").value;
                var sortOrder = document.getElementById("sortOrder").value;

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

                document.getElementById("sortName").value = sortName;
                document.getElementById("sortOrder").value = sortOrder;

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
                .d-print-none {
                    display: none !important;
                }
            }
        </style>
    </head>
    <body>
    <jsp:include page="/images/spinner.jsp" flush="true"/>
    <c:if test="${statusModel.partialTotal}">
        <div style="background:#fff3cd;color:#7a5b00;border:1px solid #d4a700;padding:8px;margin:4px 0;">
            <strong>Total may be incomplete.</strong>
            ${statusModel.unreadableTotalRowCount} row(s) had unreadable amounts and were skipped — refresh, or contact admin if persistent.
        </div>
    </c:if>
    <h3>
        <fmt:message key="admin.admin.invoiceRpts"/>
    </h3>
    <div class="container-fluid">
        <form name="serviceform" class="d-flex flex-wrap align-items-center gap-2" method="get"
              action="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingONStatus"
              onsubmit="ShowSpin(true);">
            <input type="hidden" id="sortName" name="sortName" value="${carlos:forHtmlAttribute(statusModel.sortName)}">
            <input type="hidden" id="sortOrder" name="sortOrder" value="${carlos:forHtmlAttribute(statusModel.sortOrder)}">
            <div class="row card card-body bg-body-tertiary d-print-none">
                <div class="row">
                    <div class="col-md-12">
                        <div class="form-check form-check-inline"><input type="checkbox" class="form-check-input" name="billTypeAll" id="ALL" value="ALL"
                                                              checked onclick="changeStatus();"><label class="form-check-label" for="ALL">ALL</label></div>
                        <div class="form-check form-check-inline"><input type="checkbox" class="form-check-input" name="billType" id="billType_HCP"
                                                              value="HCP" ${fn:contains(statusModel.billTypes, 'HCP') ? 'checked' : ''}><label class="form-check-label" for="billType_HCP">Bill
                            OHIP</label></div>
                        <div class="form-check form-check-inline"><input type="checkbox" class="form-check-input" name="billType" id="billType_RMB"
                                                              value="RMB" ${fn:contains(statusModel.billTypes, 'RMB') ? 'checked' : ''}><label class="form-check-label" for="billType_RMB">RMB</label></div>
                        <div class="form-check form-check-inline"><input type="checkbox" class="form-check-input" name="billType" id="billType_WCB"
                                                              value="WCB" ${fn:contains(statusModel.billTypes, 'WCB') ? 'checked' : ''}><label class="form-check-label" for="billType_WCB">WCB</label></div>
                        <div class="form-check form-check-inline"><input type="checkbox" class="form-check-input" name="billType" id="billType_NOT"
                                                              value="NOT" ${fn:contains(statusModel.billTypes, 'NOT') ? 'checked' : ''}><label class="form-check-label" for="billType_NOT">Not
                            Bill</label></div>
                        <div class="form-check form-check-inline"><input type="checkbox" class="form-check-input" name="billType" id="billType_PAT"
                                                              value="PAT" ${fn:contains(statusModel.billTypes, 'PAT') ? 'checked' : ''}><label class="form-check-label" for="billType_PAT">Bill
                            Patient</label></div>
                        <div class="form-check form-check-inline"><input type="checkbox" class="form-check-input" name="billType" id="billType_OCF"
                                                              value="OCF" ${fn:contains(statusModel.billTypes, 'OCF') ? 'checked' : ''}><label class="form-check-label" for="billType_OCF">OCF</label></div>
                        <div class="form-check form-check-inline"><input type="checkbox" class="form-check-input" name="billType" id="billType_ODS"
                                                              value="ODS" ${fn:contains(statusModel.billTypes, 'ODS') ? 'checked' : ''}><label class="form-check-label" for="billType_ODS">ODSP</label></div>
                        <div class="form-check form-check-inline"><input type="checkbox" class="form-check-input" name="billType" id="billType_CPP"
                                                              value="CPP" ${fn:contains(statusModel.billTypes, 'CPP') ? 'checked' : ''}><label class="form-check-label" for="billType_CPP">CPP</label></div>
                        <div class="form-check form-check-inline"><input type="checkbox" class="form-check-input" name="billType" id="billType_STD"
                                                              value="STD" ${fn:contains(statusModel.billTypes, 'STD') ? 'checked' : ''}><label class="form-check-label" for="billType_STD">STD/LTD</label></div>
                        <div class="form-check form-check-inline"><input type="checkbox" class="form-check-input" name="billType" id="billType_IFH"
                                                              value="IFH" ${fn:contains(statusModel.billTypes, 'IFH') ? 'checked' : ''}><label class="form-check-label" for="billType_IFH">IFH</label></div>
                    </div>
                    <div class="col-md-10">
                        <c:choose>
                            <c:when test="${statusModel.multisites}">
                                <script>
                                    var _providers = {};
                                    <c:forEach var="msite" items="${statusModel.multisiteSites}">
                                        _providers["<carlos:encode value='${msite.name}' context='javaScript'/>"] = [
                                            <c:forEach var="provider" items="${msite.providers}" varStatus="providerStatus">
                                            {
                                                value: "<carlos:encode value='${provider.providerNo}' context='javaScript'/>",
                                                text: "<carlos:encode value='${provider.lastName}, ${provider.firstName}' context='javaScript'/>"
                                            }<c:if test="${not providerStatus.last}">,</c:if>
                                            </c:forEach>
                                        ];
                                    </c:forEach>

                                    function changeSite(sel) {
                                        var providerSelect = sel.form.providerview;
                                        providerSelect.innerHTML = "";
                                        if (sel.value != "none") {
                                            var placeholder = document.createElement("option");
                                            placeholder.value = "none";
                                            placeholder.textContent = "---select providers---";
                                            providerSelect.appendChild(placeholder);
                                            (_providers[sel.value] || []).forEach(function (provider) {
                                                var option = document.createElement("option");
                                                option.value = provider.value;
                                                option.textContent = provider.text;
                                                providerSelect.appendChild(option);
                                            });
                                        }
                                        sel.style.backgroundColor = sel.options[sel.selectedIndex].style.backgroundColor;
                                        if (sel.value == '<carlos:encode value="${empty statusModel.requestParamEchoes['site'] ? '' : statusModel.requestParamEchoes['site']}" context="javaScriptBlock"/>') {
                                            if (document.serviceform.provider_ohipNo.value != '')
                                                sel.form.providerview.value = '<carlos:encode value="${empty statusModel.requestParamEchoes['providerview'] ? '' : statusModel.requestParamEchoes['providerview']}" context="javaScriptBlock"/>';
                                        }
                                        changeProvider(false);
                                    }
                                </script>
                                <label>
                                    Site:
                                    <select id="site" name="site" class="form-select" onchange="changeSite(this)">
                                        <option value="none" style="background-color:white">---select clinic---</option>
                                        <c:forEach var="msite" items="${statusModel.multisiteSites}">
                                            <option value="<carlos:encode value='${msite.name}' context='htmlAttribute'/>"
                                                    style="background-color:<carlos:encode value='${msite.bgColor}' context='cssString'/>"
                                                    ${msite.name eq statusModel.requestParamEchoes['site'] ? 'selected' : ''}>
                                                <carlos:encode value='${msite.name}' context='html'/>
                                            </option>
                                        </c:forEach>
                                    </select>
                                </label>
                                <label>Provider:
                                    <select id="providerview" class="form-select" name="providerview"
                                            onchange="changeProvider(true);"></select></label>
                                <c:if test="${not empty statusModel.requestParamEchoes['providerview']}">
                                    <script>
                                        window.onload = function () {
                                            changeSite(document.getElementById("site"));
                                        }
                                    </script>
                                </c:if>
                            </c:when>
                            <c:otherwise>
                                <label>
                                    Provider:
                                    <select name="providerview" class="form-select" onchange="changeProvider(false);">
                                        <c:choose>
                                            <c:when test="${fn:length(statusModel.providers) eq 1}">
                                                <c:forEach var="po" items="${statusModel.providers}">
                                                    <option value="<carlos:encode value='${po.providerNo}' context='htmlAttribute'/>">
                                                        <carlos:encode value='${po.lastName}' context='html'/>, <carlos:encode value='${po.firstName}' context='html'/>
                                                    </option>
                                                </c:forEach>
                                            </c:when>
                                            <c:otherwise>
                                                <option value="all">All Providers</option>
                                                <c:forEach var="po" items="${statusModel.providers}">
                                                    <option value="<carlos:encode value='${po.providerNo}' context='htmlAttribute'/>"
                                                            ${statusModel.providerNo eq po.providerNo ? 'selected' : ''}>
                                                        <carlos:encode value='${po.lastName}' context='html'/>, <carlos:encode value='${po.firstName}' context='html'/>
                                                    </option>
                                                </c:forEach>
                                            </c:otherwise>
                                        </c:choose>
                                    </select>
                                </label>
                            </c:otherwise>
                        </c:choose>
                        <label>
                            OHIP No.:
                            <input type="text" class="form-control form-control-sm d-inline-block w-auto" name="provider_ohipNo" readonly value="${carlos:forHtmlAttribute(statusModel.providerOhipNo)}"></label>
                    </div>
                    <div class="col-md-6">
                        <label for="xml_vdate">Start:</label>
                        <div class="input-group">
                            <input type="text" class="form-control" name="xml_vdate" id="xml_vdate" style="width:90px" value="${carlos:forHtmlAttribute(statusModel.startDate)}"
                                   pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off" required>
                            <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                        </div>
                        <label for="xml_appointment_date">End:
                            <small>
                                <a href="javascript: function myFunction() {return false; }"
                                   onClick="fillEndDate('<carlos:encode value="${statusModel.endDateMinus30}" context="javaScriptBlock"/>')">30</a>
                                <a href="javascript: function myFunction() {return false; }"
                                   onClick="fillEndDate('<carlos:encode value="${statusModel.endDateMinus60}" context="javaScriptBlock"/>')">60</a>
                                <a href="javascript: function myFunction() {return false; }"
                                   onClick="fillEndDate('<carlos:encode value="${statusModel.endDateMinus90}" context="javaScriptBlock"/>')">90</a>
                                days back
                            </small></label>
                        <div class="input-group">
                            <input type="text" class="form-control" name="xml_appointment_date" style="width:90px" id="xml_appointment_date"
                                   value="${carlos:forHtmlAttribute(statusModel.endDate)}" pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$"
                                   autocomplete="off" required>
                            <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                        </div>
                    </div>
                </div>
                <!-- row -->
                <div class="row">
                    <div class="col-md-12">
                        <label>Dx:
                            <input type="text" name="dx" class="form-control form-control-sm d-inline-block w-auto" placeholder="123" value="${carlos:forHtmlAttribute(statusModel.dx)}"></label>
                        <label>Serv. Code:
                            <input type="text" name="serviceCode" class="form-control form-control-sm d-inline-block w-auto" placeholder="A123A"
                                   value="${carlos:forHtmlAttribute(statusModel.serviceCode)}"></label>
                        <label>Demographic:
                            <input type="text" name="demographicNo" class="form-control form-control-sm d-inline-block w-auto" placeholder="1234"
                                   value="${carlos:forHtmlAttribute(statusModel.demoNo)}"></label>
                        <label>RA Code:
                            <input type="text" name="raCode" class="form-control form-control-sm d-inline-block w-auto" placeholder=""
                                   value="${carlos:forHtmlAttribute(statusModel.raCode)}"></label>
                        <label>Claim No (% for any):
                            <input type="text" name="claimNo" class="form-control form-control-sm d-inline-block w-auto" value="${carlos:forHtmlAttribute(statusModel.claimNo)}"></label>
                        <label>
                            Visit Type:
                            <select name="visitType" style="background-color:white;">
                                <option value="-" label="-%" ${fn:startsWith(statusModel.visitType, '-') ? 'selected' : ''}></option>
                                <option value="00" ${fn:startsWith(statusModel.visitType, '00') ? 'selected' : ''}>Clinic Visit</option>
                                <option value="01" ${fn:startsWith(statusModel.visitType, '01') ? 'selected' : ''}>Outpatient Visit</option>
                                <option value="02" ${fn:startsWith(statusModel.visitType, '02') ? 'selected' : ''}>Hospital Visit</option>
                                <option value="03" ${fn:startsWith(statusModel.visitType, '03') ? 'selected' : ''}>ER</option>
                                <option value="04" ${fn:startsWith(statusModel.visitType, '04') ? 'selected' : ''}>Nursing Home</option>
                                <option value="05" ${fn:startsWith(statusModel.visitType, '05') ? 'selected' : ''}>Home Visit</option>
                            </select>
                        </label>
                        <label>
                            Billing Form:
                            <select name="billing_form">
                                <option value="---" selected="selected"> ---</option>
                                <c:forEach var="bf" items="${statusModel.billingForms}">
                                    <option value="<carlos:encode value='${bf.value}' context='htmlAttribute'/>"
                                            ${statusModel.billingForm eq bf.value ? 'selected' : ''}>
                                        <carlos:encode value='${bf.label}' context='html'/>
                                    </option>
                                </c:forEach>
                            </select>
                        </label>
                        <label for="xml_location">Visit Location:</label>
                        <select name="xml_location" id="xml_location" class="form-select">
                            <c:forEach var="loc" items="${statusModel.visitLocations}">
                                <option value="<carlos:encode value='${loc.code}' context='htmlAttribute'/>"
                                        ${statusModel.visitLocation eq loc.code ? 'selected="selected"' : ''}>
                                    <carlos:encode value='${loc.label}' context='html'/>
                                </option>
                            </c:forEach>
                        </select>
                        <label for="paymentStartDate">Payment Start:</label>
                        <div class="input-group">
                            <input type="text" class="form-control" name="paymentStartDate" id="paymentStartDate" style="width:90px"
                                   value="${carlos:forHtmlAttribute(statusModel.paymentStartDate)}" pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$"
                                   autocomplete="off">
                            <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                        </div>
                        <label for="paymentEndDate">Payment End:</label>
                        <div class="input-group">
                            <input type="text" class="form-control" name="paymentEndDate" id="paymentEndDate" style="width:90px"
                                   value="${carlos:forHtmlAttribute(statusModel.paymentEndDate)}" pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$"
                                   autocomplete="off">
                            <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                        </div>
                        <div class="col-md-12">
                            <div class="form-check form-check-inline">
                                <input class="form-check-input" type="radio" name="statusType" id="statusTypeAll"
                                       value="%" ${statusModel.statusType eq '%' ? 'checked' : ''}>
                                <label class="form-check-label" for="statusTypeAll">All</label>
                            </div>
                            <div class="form-check form-check-inline">
                                <input class="form-check-input" type="radio" name="statusType" id="statusTypeRejected"
                                       value="_" ${statusModel.statusType eq '_' ? 'checked' : ''}>
                                <label class="form-check-label" for="statusTypeRejected">Rejected</label>
                            </div>
                            <div class="form-check form-check-inline">
                                <input class="form-check-input" type="radio" name="statusType" id="statusTypeCapitated"
                                       value="H" ${statusModel.statusType eq 'H' ? 'checked' : ''}>
                                <label class="form-check-label" for="statusTypeCapitated">Capitated</label>
                            </div>
                            <div class="form-check form-check-inline">
                                <input class="form-check-input" type="radio" name="statusType" id="statusTypeInvoiced"
                                       value="O" ${statusModel.statusType eq 'O' ? 'checked' : ''}>
                                <label class="form-check-label" for="statusTypeInvoiced">Invoiced</label>
                            </div>
                            <div class="form-check form-check-inline">
                                <input class="form-check-input" type="radio" name="statusType" id="statusTypeBillPatient"
                                       value="P" ${statusModel.statusType eq 'P' ? 'checked' : ''}>
                                <label class="form-check-label" for="statusTypeBillPatient">Bill Patient</label>
                            </div>
                            <div class="form-check form-check-inline">
                                <input class="form-check-input" type="radio" name="statusType" id="statusTypeSubmittedOHIP"
                                       value="B" ${statusModel.statusType eq 'B' ? 'checked' : ''}>
                                <label class="form-check-label" for="statusTypeSubmittedOHIP">Submitted OHIP</label>
                            </div>
                            <div class="form-check form-check-inline">
                                <input class="form-check-input" type="radio" name="statusType" id="statusTypeSettled"
                                       value="S" ${statusModel.statusType eq 'S' ? 'checked' : ''}>
                                <label class="form-check-label" for="statusTypeSettled">Settled/Paid</label>
                            </div>
                            <div class="form-check form-check-inline">
                                <input class="form-check-input" type="radio" name="statusType" id="statusTypeBadDebt"
                                       value="X" ${statusModel.statusType eq 'X' ? 'checked' : ''}>
                                <label class="form-check-label" for="statusTypeBadDebt">Bad Debt</label>
                            </div>
                            <div class="form-check form-check-inline">
                                <input class="form-check-input" type="radio" name="statusType" id="statusTypeDeleted"
                                       value="D" ${statusModel.statusType eq 'D' ? 'checked' : ''}>
                                <label class="form-check-label" for="statusTypeDeleted">Deleted Bill</label>
                            </div>
                        </div>
                        <!--</div>-->
                        <!-- row -->
                        <!--<div class="row">-->
                        <div class="col-md-4" style="padding-top:10px;">
                            <input class="btn btn-primary" type="submit" name="Submit" value="Create Report">
                            <button class="btn btn-secondary" type='button' name='print' value='Print' onClick='window.print()'><i
                                    class="fa-solid fa-print"></i> Print
                            </button>
                        </div>
                    </div>
                    <!-- row -->
                </div>
            </div>
            <!-- end card card-body bg-body-tertiary -->
        </form>
        <form name="invoiceForm" action="${pageContext.request.contextPath}/BillingInvoice">
            <input type="hidden" name="method" value="">
            <c:choose>
                <c:when test="${statusModel.statusType eq '_'}">
                    <table class="table" id="rejectTbl">
                        <thead>
                        <tr class="table-warning">
                            <th>Insurance#</th>
                            <th>D.O.B</th>
                            <th>Invoice#</th>
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
                                <button class="btn-link d-print-none" type="button" title="Show/Hide Checked"
                                        onClick="filterChecked()">Status
                                </button>
                            </th>
                            <th>Filename</th>
                        </tr>
                        </thead>
                        <c:forEach var="row" items="${statusModel.rejectedBillRows}">
                            <tr class="${row.rowClass}" name="BillingErrorRow"
                                id="BillingErrorRow_<carlos:encode value='${row.id}' context='htmlAttribute'/>">
                                <td><small><carlos:encode value='${row.hin}' context='html'/> <carlos:encode value='${row.ver}' context='html'/></small></td>
                                <td><font size="-1"><carlos:encode value='${row.dob}' context='html'/></font></td>
                                <td style="text-align:right">
                                    <a href="#"
                                       onclick="popupPage(800,700,'${pageContext.request.contextPath}/billing/CA/ON/BillingONCorrection?billing_no=${carlos:forUriComponent(row.billingNo)}');return false;">
                                        <carlos:encode value='${row.billingNo}' context='html'/>
                                    </a>
                                </td>
                                <td><carlos:encode value='${row.refNo}' context='html'/></td>
                                <td><carlos:encode value='${row.facility}' context='html'/></td>
                                <td><carlos:encode value='${row.admittedDate}' context='html'/></td>
                                <td><carlos:encode value='${row.claimError}' context='html'/></td>
                                <td><carlos:encode value='${row.code}' context='html'/></td>
                                <td style="text-align:right">
                                    <carlos:encode value='${row.formattedFee}' context='html'/>
                                    <c:if test="${row.feeUnreadable}">
                                        <span class="alert">Fee unreadable</span>
                                    </c:if>
                                </td>
                                <td style="text-align:right"><carlos:encode value='${row.unit}' context='html'/></td>
                                <td><font size="-1"><carlos:encode value='${row.codeDate}' context='html'/></font></td>
                                <td><carlos:encode value='${row.dx}' context='html'/></td>
                                <td><carlos:encode value='${row.exp}' context='html'/></td>
                                <td><carlos:encode value='${row.codeError}' context='html'/></td>
                                <td style="text-align:center">
                                    <input type="checkbox"
                                           id="status<carlos:encode value='${row.id}' context='htmlAttribute'/>"
                                           name="status<carlos:encode value='${row.id}' context='htmlAttribute'/>"
                                           value="Y" ${row.checked ? 'checked' : ''}
                                           onclick="startRequest('<carlos:encode value="${row.id}" context="javaScriptAttribute"/>');"/>
                                </td>
                                <td id="<carlos:encode value='${row.id}' context='htmlAttribute'/>">
                                    <carlos:encode value='${row.reportName}' context='html'/>
                                </td>
                            </tr>
                        </c:forEach>
                    </table>
                    <script>
                        $('#rejectTbl').DataTable({
                            "bPaginate": false,
                            "order": [],
                            "language": {
                                "url": "${pageContext.request.contextPath}/library/DataTables/i18n/<fmt:message key="global.i18n.datatablescode"/>.json"
                            }
                        });
                    </script>
                </c:when>
                <c:otherwise>
                    <table class="table display nowrap" id="bListTable">
                        <thead>
                        <tr>
                            <th><a href="javascript:void(0);" onClick="updateSort('ServiceDate');return false;">SERVICE
                                DATE</a></th>
                            <th><a href="javascript:void(0);"
                                   onClick="updateSort('DemographicNo');return false;">PATIENT</a></th>
                            <th class="${statusModel.hideName ? 'd-print-none' : ''}">PATIENT NAME</th>
                            <th><a href="javascript:void(0);"
                                   onClick="updateSort('VisitLocation');return false;">LOCATION</a></th>
                            <th title="Status">STAT</th>
                            <th>SETTLED</th>
                            <th title="Code Billed">CODE</th>
                            <th title="Amount Billed">BILLED</th>
                            <th title="Amount Paid">PAID</th>
                            <th title="Adjustments">ADJ</th>
                            <th>DX</th>
                            <th>TYPE</th>
                            <th>INVOICE #</th>
                            <th>MESSAGES</th>
                            <th>CASH</th>
                            <th>DEBIT</th>
                            <th>Quantity</th>
                            <th>Provider</th>
                            <c:if test="${statusModel.multisites}">
                                <th>SITE</th>
                            </c:if>
                            <th class="d-print-none">
                                <a href="#" onClick="checkAll(document.invoiceForm.invoiceAction)">
                                    <fmt:message key="billing.billingStatus.action"/>
                                </a>
                            </th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="bill" items="${statusModel.billRows}" varStatus="rowStat">
                            <tr class="${bill.rowClass}">
                                <td style="text-align:center"><carlos:encode value='${bill.billingDate}' context='html'/></td>
                                <td style="text-align:center"><carlos:encode value='${bill.demographicNo}' context='html'/></td>
                                <td style="text-align:center" class="${statusModel.hideName ? 'd-print-none' : ''}">
                                    <a href="#"
                                       onclick="popupPage(800,740,'${pageContext.request.contextPath}/demographic/DemographicEdit?demographic_no=${carlos:forUriComponent(bill.demographicNo)}');return false;">
                                        <carlos:encode value='${bill.demographicName}' context='html'/>
                                    </a>
                                </td>
                                <td style="text-align:center"><carlos:encode value='${bill.facilityNum}' context='html'/></td>
                                <td style="text-align:center"><carlos:encode value='${bill.status}' context='html'/></td>
                                <td style="text-align:center"><carlos:encode value='${bill.settleDate}' context='html'/></td>
                                <td style="text-align:center"><carlos:encode value='${bill.code}' context='html'/></td>
                                <td style="text-align:right"><carlos:encode value='${bill.billed}' context='html'/></td>
                                <td style="text-align:right"><carlos:encode value='${bill.amountPaid}' context='html'/></td>
                                <td style="text-align:center"><carlos:encode value='${bill.adjustment}' context='html'/></td>
                                <td style="text-align:center"><carlos:encode value='${bill.recId}' context='html'/></td>
                                <td style="text-align:center"><carlos:encode value='${bill.payProgram}' context='html'/></td>
                                <td style="text-align:center">
                                    <a href="#"
                                       onclick="popupPage(800,700,'${pageContext.request.contextPath}/billing/CA/ON/BillingONCorrection?billing_no=${carlos:forUriComponent(bill.invoiceNo)}');nav_colour_swap(this.id, ${fn:length(statusModel.billRows)});return false;">
                                        <carlos:encode value='${bill.invoiceNo}' context='html'/>
                                    </a>
                                </td>
                                <td class="highlightBox">
                                    <a id="A${rowStat.index}" href="#"
                                       onclick="popupPage(800,700,'${pageContext.request.contextPath}/billing/CA/ON/BillingONCorrection?billing_no=${carlos:forUriComponent(bill.invoiceNo)}');nav_colour_swap(this.id, ${fn:length(statusModel.billRows)});return false;">Edit</a>
                                    <carlos:encode value='${bill.errorCode}' context='html'/>
                                </td>
                                <td style="text-align:center">$<carlos:encode value='${bill.cash}' context='html'/></td>
                                <td style="text-align:center">$<carlos:encode value='${bill.debit}' context='html'/></td>
                                <td style="text-align:center">${bill.qty}</td>
                                <td style="text-align:center"><carlos:encode value='${bill.providerName}' context='html'/></td>
                                <c:if test="${statusModel.multisites}">
                                    <c:choose>
                                        <c:when test="${not empty bill.clinicBgColor}">
                                            <td style="background-color:<carlos:encode value='${bill.clinicBgColor}' context='cssString'/>;">
                                                <carlos:encode value='${bill.clinicShortName}' context='html'/>
                                            </td>
                                        </c:when>
                                        <c:otherwise>
                                            <td>
                                                <carlos:encode value='${bill.clinicShortName}' context='html'/>
                                            </td>
                                        </c:otherwise>
                                    </c:choose>
                                </c:if>
                                <td style="text-align:center" class="d-print-none">
                                    <c:if test="${bill.newInvoice and bill.thirdParty}">
                                        <input type="checkbox" name="invoiceAction"
                                               id="invoiceAction<carlos:encode value='${bill.invoiceNo}' context='htmlAttribute'/>"
                                               value="<carlos:encode value='${bill.invoiceNo}' context='htmlAttribute'/>"/>
                                    </c:if>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                    <script>
                        $('#bListTable').DataTable({
                            "bPaginate": false,
                            "order": [],
                            "language": {
                                "url": "${pageContext.request.contextPath}/library/DataTables/i18n/<fmt:message key="global.i18n.datatablescode"/>.json"
                            }
                        });
                    </script>
                    <table>
                        <tr class="table-warning">
                            <td>Count:</td>
                            <td style="text-align:center">${statusModel.patientCount}</td>
                            <td style="text-align:center" class="${statusModel.hideName ? 'd-print-none' : ''}">&nbsp;</td>
                            <td>&nbsp;</td>
                            <td>&nbsp;</td>
                            <td>&nbsp;</td>
                            <td>Total:</td>
                            <td style="text-align:right">$<carlos:encode value='${statusModel.totalBilled}' context='html'/></td>
                            <td style="text-align:right"> Paid: $<carlos:encode value='${statusModel.totalPaid}' context='html'/></td>
                            <td style="text-align:right"> Adj: $<carlos:encode value='${statusModel.totalAdjustments}' context='html'/></td>
                            <td>&nbsp;</td>
                            <td>&nbsp;</td>
                            <td>&nbsp;</td>
                            <td>&nbsp;</td>
                            <td style="text-align:center">Cash: $<carlos:encode value='${statusModel.totalCash}' context='html'/></td>
                            <td style="text-align:center">Debit: $<carlos:encode value='${statusModel.totalDebit}' context='html'/></td>
                            <td style="text-align:center">&nbsp;</td>
                            <td>&nbsp;</td>
                            <c:if test="${statusModel.multisites}">
                                <td>&nbsp;</td>
                            </c:if>
                            <td style="text-align:center" class="d-print-none">
                                <a href="#" onClick="submitForm('print')">
                                    <fmt:message key="billing.billingStatus.print"/>
                                </a>
                                    <%-- <a href="#" onClick="submitForm('email')">
                                        <fmt:message key="billing.billingStatus.email"/>
                                    </a> --%>
                            </td>
                        </tr>
                    </table>
                    <c:if test="${not empty statusModel.billRows}">
                        <a download="carlos_invoices.xls" href="#"
                           onclick="return TableExport.excel(this, 'bListTable', 'CARLOS Invoices');">Export to Excel</a>
                        <a download="carlos_invoices.csv" href="#" onclick="return TableExport.csv(this, 'bListTable');">Export
                            to CSV</a>
                    </c:if>
                </c:otherwise>
            </c:choose>
        </form>
    </div>
    <!-- end container -->
    </body>
</html>
