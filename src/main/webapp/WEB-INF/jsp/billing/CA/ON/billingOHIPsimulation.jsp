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
  Purpose: Supports billingOHIPsimulation in the Ontario billing workflow.
  Expected request model data includes: simulationModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="carlos" prefix="carlos" %>
<html>
<jsp:useBean id="SxmlMisc" class="io.github.carlos_emr.SxmlMisc" scope="session"/>

<head>
    <title><fmt:message key="admin.admin.btnSimulationOHIPDiskette"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/fontawesome-all.min.css">
    <link href="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.css" rel="stylesheet">
    <script src="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.js"></script>

    <script type="text/javascript" language="JavaScript">
        <!--
        function openBrWindow(theURL, winName, features) {
            window.open(theURL, winName, features);
        }

        function checkData() {
            var b = true;
            if (document.forms[0].provider.value == "000000") {
                alert("Please select a providers!");
                b = false;
            } else if (document.forms[0].xml_vdate.value == "") {
                alert("Please give a date!");
                b = false;
            }

            return b;
        }

        //-->
    </script>
    <style type="text/css">
        input[name=summaryView] {
            margin: 4px 4px 4px;
            margin-left: 20px;
        }

        select[name=provider] {
            margin-right: 20px;
        }

        input[name=submit] {
            margin-bottom: -60px;
        }

        .myLightBlue {
            background-color: #d9edf7;
        }
    </style>
</head>

<body>

<div class="container-fluid">
    <h3><fmt:message key="admin.admin.btnSimulationOHIPDiskette"/></h3>

    <form name="serviceform" id="serviceform"
          action="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingOHIPsimulation">
        <div class="row card card-body bg-body-tertiary d-print-none">

            <input type="hidden" name="submit" value="Create Report">

            Bill Center:
            <input type="hidden" name="billcenter" value="<carlos:encode value='${simulationModel.billCenter}' context='htmlAttribute'/>">
            <carlos:encode value="${simulationModel.healthOffice}" context="html"/>

            <button type='button' name='print' value='Print' class="btn float-end" onClick='window.print()'><i
                    class="fa-solid fa-print"></i> Print
            </button>
            <br/>

            <input type="hidden" name="monthCode" value="<carlos:encode value='${simulationModel.monthCode}' context='htmlAttribute'/>">
            <input type="hidden" name="verCode" value="V03">
            <input type="hidden" name="curUser" value="<carlos:encode value='${simulationModel.userNo}' context='htmlAttribute'/>">
            <input type="hidden" name="curDate" value="<carlos:encode value='${simulationModel.nowDate}' context='htmlAttribute'/>">

            <div class="col-md-12" style="margin:4px;">

                <div class="col-md-3">
                    Select Provider<br>
                    <select name="providers">
                        <c:choose>
                            <c:when test="${simulationModel.multisites}"><option value="all">Select Providers</option></c:when>
                            <c:otherwise><option value="all">All Providers</option></c:otherwise>
                        </c:choose>
                        <c:forEach var="opt" items="${simulationModel.providers}">
                            <c:set var="isSelected" value="${simulationModel.providerView eq opt.providerNo or fn:length(simulationModel.providers) == 1}"/>
                            <option value="<carlos:encode value='${opt.providerNo}' context='htmlAttribute'/>"
                                    ${isSelected ? 'selected' : ''}><carlos:encode value="${opt.lastName}" context="html"/>
                                ,
                                <carlos:encode value="${opt.firstName}" context="html"/>
                            </option>
                        </c:forEach>
                    </select>
                </div><!--span3-->

                <div class="col-md-2">
                    From:<br>
                    <div class="input-group">
                        <input type="text" name="xml_vdate" id="xml_vdate" class="form-control"
                               value="<carlos:encode value='${simulationModel.startDate}' context='htmlAttribute'/>" style="width:90px" autocomplete="off"/>
                        <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                    </div>
                </div>

                <div class="col-md-2">
                    To:<br>
                    <div class="input-group">
                        <input type="text" name="xml_appointment_date" id="xml_appointment_date"
                               class="form-control" value="<carlos:encode value='${simulationModel.endDate}' context='htmlAttribute'/>" style="width:90px"
                               autocomplete="off"/>
                        <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                    </div>
                </div>

                <c:if test="${not simulationModel.multisites}">
                <div class="col-md-2" style="min-width:140px"><br><input type="checkbox" name="summaryView"
                                                                      id="summaryView" ${simulationModel.summaryView ? 'checked' : ''} />Summary
                    View
                </div>
                </c:if>

                <div class="col-md-2">
                    <br>
                    <button class="btn btn-primary " type="submit" name="submit" value="Create Report">Create Report
                    </button>
                </div>

            </div> <!--span12-->

            <div class="col-md-11">

                <br>

            </div><!--span12-->

        </div><!--form well-->
    </form>

</div><!--container-->

<%--
  ${simulationModel.previewHtml} contains assembler-built trusted HTML — the
  OHIP file preview rendered as a <pre>-style code block. The producer
  (BillingOhipSimulationViewModelAssembler) builds it from constants and
  server-side BillingONCHeader1 / BillingItem state only; no raw request
  parameter reaches this rendering point. Do not change this contract
  without updating the assembler's safety invariant comment.
--%>
${simulationModel.previewHtml}

<script type="text/javascript">

    // registerFormSubmit is defined on the administration/index.jsp parent
    // page where this view is normally loaded into a dynamic-content area.
    // Guard for standalone loads (direct URL) so a missing wrapper doesn't
    // throw a ReferenceError on every render.
    if (typeof registerFormSubmit === 'function') {
        registerFormSubmit('serviceform', 'dynamic-content');
    }

    flatpickr("#xml_vdate", {dateFormat: "Y-m-d", allowInput: true});
    flatpickr("#xml_appointment_date", {dateFormat: "Y-m-d", allowInput: true});

    //open a new popup window
    function popupPage(vheight, vwidth, varpage) {
        var page = "" + varpage;
        windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
        var popup = window.open(page, "attachment", windowprops);
        if (popup != null) {
            if (popup.opener == null) {
                popup.opener = self;
            }
        }
    }

    document.querySelectorAll(".xlink").forEach(function (el) {
        el.addEventListener('click', function (e) {
            var source = this.getAttribute('rel');
            // Existing pattern: creates iframe from server-set rel attribute
            document.getElementById("dynamic-content").innerHTML = '<iframe id="myFrame" name="myFrame" frameborder="0" width="950" height="1000" src="' + source + '">';
        });
    });

</script>
</body>
</html>

