<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Purpose: Supports ScheduleOfBenefitsUpload in the Ontario billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<!DOCTYPE html>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<fmt:setBundle basename="oscarResources" charEncoding="UTF-8"/>
<fmt:message var="invalidAssistantFeeMsg" key="oscar.billing.CA.ON.billingON.sobUpload.invalidAssistantFee"/>
<fmt:message var="invalidAnaesthetistFeeMsg" key="oscar.billing.CA.ON.billingON.sobUpload.invalidAnaesthetistFee"/>

<html>

    <head>
        <title><fmt:message key="admin.admin.scheduleOfBenefits"/></title>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">

        <script type="text/javascript" LANGUAGE="JavaScript">

            function checkForm() {
                var result = true;
                if (document.getElementById("updateAssistantInput").style.display == "inline") {
                    if (!checkFee(document.getElementById("updateAssistantFeesValue").value)) {
                        alert("${carlos:forJavaScript(invalidAssistantFeeMsg)}");
                        result = false;
                    }
                }
                if (document.getElementById("updateAnaesthetistInput").style.display == "inline") {
                    if (!checkFee(document.getElementById("updateAnaesthetistFeesValue").value)) {
                        alert("${carlos:forJavaScript(invalidAnaesthetistFeeMsg)}");
                        result = false;
                    }
                }
                return result && displayAndDisable();
            }

            function checkFee(fee) {
                if (fee == null || fee.trim() == "") {
                    return false;
                }
                var feeFormat = /^\d+?(\.\d{0,2})$/;
                if (fee.match(feeFormat)) {
                    return true;
                } else {
                    return false;
                }
            }

            function toggleAnaesthetistInput(el) {
                document.getElementById("updateAnaesthetistInput").style.display = el.checked ? "inline" : "none";
            }

            function toggleAssistantInput(el) {
                document.getElementById("updateAssistantInput").style.display = el.checked ? "inline" : "none";
            }

            function displayAndDisable() {
                document.forms[0].Submit.disabled = true;
                //showHideItem('waitingMessage');
                return true;
            }

            function checkAll(formId) {
                var f = document.getElementById(formId);
                var val = f.checkA.checked;
                for (i = 0; i < f.change.length; i++) {
                    f.change[i].checked = val;
                }
            }
        </script>
    </head>

    <body>
    <h3><fmt:message key="admin.admin.scheduleOfBenefits"/> </h3>
    <div class="container-fluid d-flex flex-wrap align-items-center gap-2">


        <div class="card card-body bg-body-tertiary">

            <div>
                1. <fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.step1Before"/> <a
                    href="https://www.ontario.ca/page/ohip-schedule-benefits-and-fees"
                    target="_blank"><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.ohipFeeSchedule"/></a> <fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.step1After"/>
            </div><!--#1-->

            <div>
                2. <fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.step2"/>
                <c:choose>
                    <c:when test="${empty warnings}">
                        <form action="${pageContext.request.contextPath}/billing/CA/ON/benefitScheduleUpload"
                              method="POST" enctype="multipart/form-data" onsubmit="return checkForm();">
                            <input type="file" name="importFile" value="/root/apr05sob.001">
                            <input class="btn btn-primary" type="submit" name="Submit" value="<fmt:message key='oscar.billing.CA.ON.billingON.sobUpload.importButton'/>">
                            <div>
                                <input type="checkbox" name="showChangedCodes" value="on" checked tabindex="1"/><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.showCodesChangedPrices"/><br>
                                <input type="checkbox" name="showNewCodes" value="on" tabindex="2"/><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.showNewCodes"/><br>
                                <input type="checkbox" name="forceUpdate" value="on" tabindex="3"/><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.forceUpdate"/><br>
                                <input type="checkbox" name="updateAssistantFees" onclick="toggleAssistantInput(this);"
                                       value="on" tabindex="5"/><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.updateAssistantFees"/><span
                                        id="updateAssistantInput" style="display:none;"><input type="text"
                                                                                               name="updateAssistantFeesValue"
                                                                                               id="updateAssistantFeesValue"
                                                                                               size="7" maxlength="8"
                                                                                               style="margin-left:30px;"
                                                                                               tabindex="7"/></span><br/>
                                <input type="checkbox" name="updateAnaesthetistFees" onclick="toggleAnaesthetistInput(this);"
                                       value="on" tabindex="6"/><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.updateAnaesthetistFees"/><span
                                        id="updateAnaesthetistInput" style="display:none;"><input type="text"
                                                                                                  name="updateAnaesthetistFeesValue"
                                                                                                  id="updateAnaesthetistFeesValue"
                                                                                                  size="7" maxlength="8"
                                                                                                  style="margin-left:8px;"
                                                                                                  tabindex="8"/></span>
                            </div>
                        </form>
                    </c:when>
                    <c:otherwise>
                        <a href="${pageContext.request.contextPath}/billing/CA/ON/ViewBenefitScheduleUpload"><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.tryAgain"/></a>
                    </c:otherwise>
                </c:choose>
            </div><!--#2-->

            <div>
                3. <fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.step3"/>
            </div><!--#3-->

            <br>
            <c:choose>
                <c:when test="${outcome == 'success'}">
                    <div class="alert alert-success"><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.successMessage"/></div>
                </c:when>
                <c:when test="${outcome == 'exception'}">
                    <div class="alert alert-danger"><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.errorMessage"/></div>
                </c:when>
                <c:when test="${outcome == 'uploadedPreviously'}">
                    <div class="alert "><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.alreadyProcessed"/></div>
                </c:when>
            </c:choose>


            <c:if test="${not empty warnings and outcome == 'success'}">
                <div>
                    4. <fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.step4"/><br>

                    <form action="${pageContext.request.contextPath}/billing/CA/ON/benefitScheduleChange" method="POST"
                            id="sbForm">
                        <table class="table table-striped  table-sm">
                            <tr>
                                <th nowrap><oscar:oscarPropertiesCheck property="SOB_CHECKALL"
                                                                       value="yes">
                                    <input type="checkbox" name="checkAll2"
                                           onclick="checkAll('sbForm')" id="checkA"/>
                                </oscar:oscarPropertiesCheck> <fmt:message key="global.update"/>
                                </th>
                                <th><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.columnFeeCode"/></th>
                                <th><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.columnCurrentPrice"/></th>
                                <th><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.columnNewPrice"/></th>
                                <th><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.columnDiff"/></th>
                                <th><fmt:message key="global.description"/></th>
                                <th><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.columnEffectiveDate"/></th>
                                <th><fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.columnTerminationDate"/></th>
                            </tr>
                            <c:forEach var="h" items="${warnings}">
                                <tr>
                                    <td><input type="checkbox" name="change"
                                               value="<carlos:encode value='${h.feeCode}|${h.newprice}|${h.effectiveDate}|${h.terminationDate}|${h.description}' context='htmlAttribute'/>"/>
                                    </td>
                                    <td><carlos:encode value='${h.feeCode}' context='html'/></td>
                                    <td><carlos:encode value='${h.oldprice}' context='html'/></td>
                                    <td><carlos:encode value='${h.newprice}' context='html'/></td>
                                    <td><carlos:encode value='${h.diff}' context='html'/></td>
                                    <td title="<carlos:encode value='${h.prices}' context='htmlAttribute'/>"><carlos:encode value='${h.description}' context='html'/></td>
                                    <td><carlos:encode value='${h.effectiveDate}' context='html'/></td>
                                    <td><carlos:encode value='${h.terminationDate}' context='html'/></td>
                                </tr>
                            </c:forEach>
                        </table>
                        <input class="btn btn-primary" type="submit" value="<fmt:message key='oscar.billing.CA.ON.billingON.sobUpload.updatePricesButton'/>">
                    </form>

                    5. <fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.step5"/>
                </div><!--#4-->
            </c:if>


            <c:if test="${not empty changes}">
                <ul>
                    <c:forEach var="h" items="${changes}">
                        <li><carlos:encode value='${h.code}' context='html'/> <fmt:message key="oscar.billing.CA.ON.billingON.sobUpload.valueUpdatedTo"/> <carlos:encode value='${h.value}' context='html'/></li>
                    </c:forEach>
                </ul>
            </c:if>


        </div><!--main well-->


    </div><!--container-->


    </body>
</html>
