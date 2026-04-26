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
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html>
    <head>
        <title><fmt:message key="admin.admin.managePrivBillingCode"/></title>

        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/css/fontawesome-all.min.css" rel="stylesheet">

        <script language="JavaScript">

            <!--
            function setfocus() {
                this.focus();
                document.forms[1].service_code.focus();
                document.forms[1].service_code.select();
                <c:if test="${not empty privateCodeModel.formFields['gstFlag']}">
                if ("<carlos:encode value='${privateCodeModel.formFields[\"gstFlag\"]}' context='javaScriptBlock'/>" == "1") {
                    document.getElementById("gstCheck").checked = true;
                    document.getElementById("gstFlag").value = 1;
                } else {
                    document.getElementById("gstCheck").checked = false;
                    document.getElementById("gstFlag").value = 0;
                }
                </c:if>
            }

            function onSearch() {
                //document.forms[1].submit.value="Search";
                var ret = checkServiceCode();
                return ret;
            }

            function onSave() {
                //document.forms[1].submit.value="Save";
                var ret = checkServiceCode();
                if (ret == true) {
                    ret = checkAllFields();
                }
                if (ret == true) {
                    ret = confirm("Are you sure you want to save?");
                }
                return ret;
            }

            function onDelete() {
                var ret = checkServiceCode();
                if (ret == true) {
                    ret = confirm("Are you sure you want to Delete?");
                }
                return ret;
            }

            function checkServiceCode() {
                var b = true;
                if (document.forms[1].service_code.value.length == 0) {
                    b = false;
                    alert("You must type in a service code with letters/digits.");
                }
                return b;
            }

            function checkAllFields() {
                var b = true;
                b = checkServiceCode();
                if (document.forms[1].value.value.length > 0) {
                    if (!isNumber(document.forms[1].value.value)) {
                        b = false;
                        alert("You must type in a number in the field fee");
                    }
                } else if (document.forms[1].value.value.length == 0) {
                    b = false;
                    alert("You must type in a number in the field fee");
                }

                if (document.forms[1].billingservice_date.value.length < 10) {
                    b = false;
                    alert("You need to select a date from the calendar.");
                }
                return b;
            }

            function isNumber(s) {
                var i;
                for (i = 0; i < s.length; i++) {
                    // Check that current character is number.
                    var c = s.charAt(i);
                    if (c == ".") continue;
                    if (((c < "0") || (c > "9"))) return false;
                }
                // All characters are numbers.
                return true;
            }

            function upCaseCtrl(ctrl) {
                ctrl.value = ctrl.value.toUpperCase();
            }

            function setFlag() {
                if (document.getElementById("gstCheck").checked == true) {
                    document.getElementById("gstFlag").value = "1";
                } else {
                    document.getElementById("gstFlag").value = "0";
                }
            }

            //-->

        </script>
    </head>
    <body>

    <h3><fmt:message key="admin.admin.managePrivBillingCode"/></h3>

    <div class="container-fluid">

        <div class="card card-body bg-body-tertiary">
            <form method="post" name="baseur0" action="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingONEditPrivateCode" class="d-flex flex-wrap align-items-center gap-2">

                Select Code to edit:<br>
                <select name="service_code" id="service_code" required>
                    <option selected="selected" value="">- choose one -</option>
                    <c:forEach var="opt" items="${privateCodeModel.options}">
                        <option value="<carlos:encode value='${opt.code}' context='htmlAttribute'/>"><carlos:encode value="${opt.label}" context="html"/>
                        </option>
                    </c:forEach>
                </select>
                <input type="hidden" name="submit" value="Search">
                <input class="btn btn-secondary" type="submit" name="action" value="Edit">
            </form>
        </div><!--select code to edit well-->

        <div class="card card-body bg-body-tertiary">
            <form method="post" name="baseurl" action="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingONEditPrivateCode">

                <div class="alert alert-<carlos:encode value='${privateCodeModel.alertLevel}' context='htmlAttribute'/>">
                    <c:out value="${privateCodeModel.message}" escapeXml="false"/>
                </div>

                <c:set var="storedCode" value="${privateCodeModel.formFields['service_code']}"/>
                <c:set var="displayCode" value="${empty storedCode ? '' : (fn:length(storedCode) > 0 ? fn:substring(storedCode, 1, fn:length(storedCode)) : '')}"/>

                Private Code_ <small>(e.g. O001A)</small><br>
                <div class="input-group">
                    <input type="text" name="service_code"
                           value="<carlos:encode value='${displayCode}' context='htmlAttribute'/>" class="col-md-2" maxlength='10'
                           onblur="upCaseCtrl(this)" required/>
                    <button type="submit" name="submit" class="btn btn-primary" onclick="javascript:return onSearch();"
                            value="Search">Search
                    </button>
                </div>

                <br>

                Description<br>
                <input type="text" name="description" value="<carlos:encode value='${privateCodeModel.formFields[\"description\"]}' context='htmlAttribute'/>" size='50'><br>

                Fee <small>(format: xx.xx, e.g. 18.20)</small><br>
                <input type="text" name="value" value="<carlos:encode value='${privateCodeModel.formFields[\"value\"]}' context='htmlAttribute'/>" size='8' maxlength='8'> <br>

                <input type="checkbox" name="gstCheck" id="gstCheck" onclick="setFlag()"/> Add GST <br>

                <input type="hidden" value="" id="gstFlag" name="gstFlag"/>

                <br>

                Issued Date <small>(effective date)</small><br>

                <div class="input-group date" data-date="">
                    <input style="width:90px" name="billingservice_date" id="billingservice_date" size="16" type="text"
                           value="" pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" readonly>
                    <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                </div>

                <br>
                <input class="btn btn-secondary" type="submit" name="submit" value="Delete" onclick="javascript:return onDelete();">
                <input type="hidden" name="action" value='<carlos:encode value="${privateCodeModel.action}" context="htmlAttribute"/>'>
                <input class="btn btn-secondary" type="submit" name="submit"
                       value="<fmt:message key="admin.resourcebaseurl.btnSave"/>"
                       onclick="javascript:return onSave();">
            </form>
        </div><!--edit/add well-->

    </div>

    <script src="${pageContext.request.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.js"></script>

    </body>
    <script type="text/javascript">
        document.addEventListener('DOMContentLoaded', function () {
            flatpickr('#billingservice_date', {dateFormat: "Y-m-d", allowInput: true});
        });
    </script>
</html>
