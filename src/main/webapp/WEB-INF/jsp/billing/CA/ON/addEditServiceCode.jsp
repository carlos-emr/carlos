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
<%@page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<html>
    <head>
        <title><fmt:message key="admin.admin.manageBillingServiceCode"/></title>
        <script type="text/javascript" src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.js"></script>

        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.css" rel="stylesheet">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/fontawesome-all.min.css">

        <script type="text/javascript">

            <!--
            function displayStyleText(value) {
                var tmp = value.split(",");
                document.getElementById('displayStyle').value = tmp[1];
            }

            function setfocus() {
                var optionElements = document.getElementById("servicecode_style");
                displayStyleText(optionElements.options[optionElements.selectedIndex].value);
                this.focus();
                document.forms[0].service_code.focus();
                document.forms[0].service_code.select();
            }

            function onSearch() {
                var ret = checkServiceCode();
                return ret;
            }

            function onSave() {
                var ret = checkServiceCode();
                if (ret == true) {
                    ret = checkAllFields();
                }
                if (ret == true) {
                    ret = confirm("Are you sure you want to save?");
                }
                return ret;
            }

            function checkServiceCode() {
                var b = true;
                if (document.forms[0].service_code.value.length != 5 || !isServiceCode(document.forms[0].service_code.value)) {
                    b = false;
                    alert("You must type in a service code with 5 (upper case) letters/digits. The service code ends with \'A\' or \'B\'...");
                }
                return b;
            }

            function isServiceCode(s) {
                if (s.length == 0) return true;
                if (s.length != 5) return false;
                if ((s.charAt(0) < "A") || (s.charAt(0) > "Z")) return false;
                if ((s.charAt(4) < "A") || (s.charAt(4) > "Z")) return false;

                var i;
                for (i = 1; i < s.length - 1; i++) {
                    var c = s.charAt(i);
                    if (((c < "0") || (c > "9"))) return false;
                }
                return true;
            }

            function checkAllFields() {
                var b = true;
                if (document.forms[0].value.value.length > 0 && document.forms[0].percentage.value.length > 0) {
                    b = false;
                    alert("You can NOT type in a fee and a percentage at the same time. (leave one blank)");
                } else if (document.forms[0].value.value.length > 0) {
                    if (!isNumber(document.forms[0].value.value)) {
                        b = false;
                        alert("You must type in a number in the field fee");
                    }
                } else if (document.forms[0].percentage.value.length > 0) {
                    if (!isNumber(document.forms[0].percentage.value)) {
                        b = false;
                        alert("You must type in a number in the field percentage");
                    } else if (document.forms[0].percentage.value > 1) {
                        b = false;
                        alert("The percentage should be less than 1.");
                    }
                    if (document.forms[0].min.value.length > 0 && document.forms[0].max.value.length > 0) {
                        if (!isNumber(document.forms[0].min.value) || !isNumber(document.forms[0].max.value)) {
                            b = false;
                            alert("You must type in a number in the min/max fields.");
                        }
                    }
                } else if (document.forms[0].value.value.length == 0 && document.forms[0].percentage.value.length == 0) {
                    b = false;
                    alert("You must type in a number in the field fee");
                }

                if (document.forms[0].billingservice_date.value.length < 10) {
                    b = false;
                    alert("You need to select a date from the calendar.");
                }
                return b;
            }

            function isNumber(s) {
                var i;
                for (i = 0; i < s.length; i++) {
                    var c = s.charAt(i);
                    if (c == ".") continue;
                    if (((c < "0") || (c > "9"))) return false;
                }
                return true;
            }

            function upCaseCtrl(ctrl) {
                ctrl.value = ctrl.value.toUpperCase();
            }

            function fetchBillService(billno) {
                document.getElementById('action').value = "single" + billno;
                var frm = document.getElementById("baseurl");
                frm.submit();
            }

            //-->

        </script>

        <style type="text/css">
            input[name=value], input[name=percentage], input[name=min], input[name=max] {
                width: 70px;
            }

            input[name=termination_date], input[name=billingservice_date] {
                width: 90px;
            }

            input[name=description] {
                width: 350px;
            }

            input[name=submitFrm] {
                margin-bottom: 10px;
            }
        </style>

    </head>
    <body onLoad="setfocus()">
    <h3><fmt:message key="admin.admin.manageBillingServiceCode"/></h3>

    <div class="container-fluid card card-body bg-body-tertiary">

        <div class="alert alert-<carlos:encode value="${addEditModel.alert}" context="htmlAttribute"/>">
            <%-- The assembler builds the message with HTML escape on the user-controlled
                 service-code value (via SafeEncode.forHtml) — render raw so the trailing
                 "<br>" + prompt text remains visible. Same as the legacy JSP. --%>
            ${addEditModel.message}
        </div>

        <form method="post" id="baseurl" name="baseurl" action="${pageContext.request.contextPath}/billing/CA/ON/AddEditServiceCode">

            <div class="col-md-10">
                Service Code <small>5 Characters, e.g. A001A</small><br>
                <div class="input-group">
                    <input type="text" name="service_code" value="<carlos:encode value="${addEditModel.prop['service_code']}" context="htmlAttribute"/>"
                           class="col-md-2" maxlength='5' onblur="upCaseCtrl(this)"/>
                    <button class="btn btn-primary" type="submit" name="submitFrm" value="Search"
                            onclick="javascript:return onSearch();">Search
                    </button>
                </div>
                <br/>
                <c:if test="${fn:length(addEditModel.codes) > 1}">
                Edit Entry<br>
                <select name="billingservice_no" onchange="fetchBillService(this.options[this.selectedIndex].value);">
                    <c:forEach var="__c" items="${addEditModel.codes}">
                    <option value="<carlos:encode value='${__c.value}' context='htmlAttribute'/>" <c:if test="${addEditModel.prop['billingservice_date'] == __c.key}">selected</c:if>><carlos:encode value="${__c.key}" context="html"/></option>
                    </c:forEach>
                </select>
                </c:if>
            </div>

            <div class="col-md-10">
                Description <small>50 Characters</small><br>
                <textarea name="description" class="form-control"><carlos:encode value="${addEditModel.prop['description']}" context="html"/></textarea>
            </div>

            <div class="col-md-10">
                Style<br>
                <select id="servicecode_style" name="servicecode_style" class="form-select"
                        onchange="displayStyleText(this.options[this.selectedIndex].value);" title="CSS Style Viewer">
                    <option value="-1,None">None</option>
                    <c:forEach var="__cs" items="${addEditModel.cssStyles}">
                    <option value="<carlos:encode value='${__cs.id}' context='htmlAttribute'/>,<carlos:encode value='${__cs.style}' context='htmlAttribute'/>" <c:if test="${addEditModel.prop['displaystyle'] == __cs.id}">selected</c:if>><carlos:encode value="${__cs.name}" context="html"/></option>
                    </c:forEach>
                </select>
                <br>
                <textarea id="displayStyle" readonly="readonly" class="form-control"></textarea>
            </div>

            <div class="col-md-2">
                Fee <small> e.g. 18.20</small><br>
                <input type="text" name="value" value="<carlos:encode value="${addEditModel.prop['value']}" context="htmlAttribute"/>" size='8' maxlength='8'
                       pattern="\d+(\.\d{2})?"><br/>
            </div>

            <div class="col-md-6">
                Percentage <small> e.g. 0.20</small><br>
                <input type="text" name="percentage" value="<carlos:encode value="${addEditModel.prop['percentage']}" context="htmlAttribute"/>" size='8'
                       maxlength='8'>
                min.<input type="text" name="min" value="<carlos:encode value="${addEditModel.prop['min']}" context="htmlAttribute"/>" size='7' maxlength='8'>
                max.<input type="text" name="max" value="<carlos:encode value="${addEditModel.prop['max']}" context="htmlAttribute"/>" size='7' maxlength='8'>
            </div>

            <div class="col-md-2">
                <label>Issued Date</label>
                <div class="input-group">
                    <input type="text" name="billingservice_date" id="billingservice_date"
                           class="form-control" value="<carlos:encode value="${addEditModel.prop['billingservice_date']}" context="htmlAttribute"/>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                    <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                </div>
            </div>

            <div class="col-md-2">
                <label>Termination Date</label>
                <div class="input-group">
                    <input type="text" name="termination_date" id="termination_date"
                           class="form-control" value="<carlos:encode value="${addEditModel.terminationDateOrDefault}" context="htmlAttribute"/>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                    <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                </div>
            </div>

            <div class="col-md-10">
                <input type="checkbox" name="sliFlag" id="sliFlag" value="true" <c:if test="${addEditModel.sliFlagChecked}">checked</c:if>> Requires SLI Code
            </div>

            <div class="col-md-10">
                <br>
                <input type="hidden" id="action" name="action" value=''>
                <input class="btn btn-secondary" type="submit"
                       name="submitFrm"
                       value="<fmt:message key="admin.resourcebaseurl.btnSave"/>"
                       onclick="document.getElementById('action').value='<carlos:encode value="${addEditModel.action}" context="javaScript"/>';return onSave();">

                <c:if test="${not empty addEditModel.action2}">
                <input class="btn btn-secondary" type="submit" name="submitFrm"
                       value="<fmt:message key="admin.resourcebaseurl.btnAdd"/>"
                       onclick="document.getElementById('action').value='<carlos:encode value="${addEditModel.action2}" context="javaScript"/>';return onSave();">
                </c:if>
            </div>

        </form>
    </div>
    <script type="text/javascript">
        flatpickr("#billingservice_date", {dateFormat: "Y-m-d", allowInput: true});
        flatpickr("#termination_date", {dateFormat: "Y-m-d", allowInput: true});
    </script>

    </body>
</html>
