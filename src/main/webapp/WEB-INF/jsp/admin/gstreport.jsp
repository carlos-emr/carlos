<%--
  Page role: Renders `gstreport.jsp` for the administration area.
  Expected request model data includes: gstReportModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<!DOCTYPE html>
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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<html>
<head>
    <title><fmt:message key="admin.admin.gstReport"/></title>

    <script type="text/javascript" src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.js"></script>

    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.css" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/fontawesome-all.min.css">
</head>
<body>
<form name="gstform" action="${pageContext.request.contextPath}/admin/GstReport" class="d-flex flex-wrap align-items-center gap-2">

    <h3><fmt:message key="admin.admin.gstReport"/></h3>

    <div class="container-fluid card card-body bg-body-tertiary">
        <div class="row">
        <div class="col-md-2"><fmt:message key="admin.gstReport.date"/>: ${gstReportModel.today}
        </div>
        <div class="col-md-2 float-end">
            <button class="btn btn-secondary" type="button" value="<fmt:message key='global.btnPrint'/>" onclick="window.print()">
            <i class="fa-solid fa-print icon-white"></i> <fmt:message key="global.btnPrint"/></button></div>

        <div class="col-md-12">
            <div class="col-md-2">
                <fmt:message key="admin.gstReport.start"/>:
                <div class="input-group">
                    <input type="text" name="xml_vdate" id="xml_vdate"
                           value="<carlos:encode value='${gstReportModel.startDate}' context='htmlAttribute'/>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off" style="width:90px"/>
                    <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                </div>
            </div>
            <div class="col-md-2">
                <fmt:message key="admin.gstReport.end"/>:
                <div class="input-group">
                    <input type="text" name="xml_appointment_date" id="xml_appointment_date"
                           value="<carlos:encode value='${gstReportModel.endDate}' context='htmlAttribute'/>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off" style="width:90px"/>
                    <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                </div>
            </div>

            <div class="col-md-6">
                <fmt:message key="admin.gstReport.provider"/>
                <div>
                    <select name="providerview">
                        <c:choose>
                            <c:when test="${fn:length(gstReportModel.providerOptions) == 1}">
                                <c:forEach var="opt" items="${gstReportModel.providerOptions}">
                                    <option value="<carlos:encode value='${opt.value}' context='htmlAttribute'/>">
                                        <carlos:encode value="${opt.lastName}"/>, <carlos:encode value="${opt.firstName}"/>
                                    </option>
                                </c:forEach>
                            </c:when>
                            <c:otherwise>
                                <option value="all">-- <fmt:message key="admin.gstReport.selectProvider"/> --</option>
                                <c:forEach var="opt" items="${gstReportModel.providerOptions}">
                                    <option value="<carlos:encode value='${opt.value}' context='htmlAttribute'/>"
                                            <c:if test="${gstReportModel.providerView eq opt.value}">selected</c:if>>
                                        <carlos:encode value="${opt.lastName}"/>, <carlos:encode value="${opt.firstName}"/>
                                    </option>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                    </select>
                    <input class="btn btn-primary" type="submit" value="<fmt:message key='admin.gstReport.search'/>"/>
                </div>
            </div><!--span6-->

        </div><!--span10-->


        <div class="col-md-12">
            <br>

        </div><!--span12-->

        </div><!--row-->
    </div>

    <table class="table table-striped table-sm">
        <tr style="font-weight:bold;">
            <td align="center"><fmt:message key="admin.gstReport.table.serviceDate"/></td>
            <td align="center"><fmt:message key="admin.gstReport.table.patient"/></td>
            <td align="center"><fmt:message key="admin.gstReport.table.patientName"/></td>
            <td align="center"><fmt:message key="admin.gstReport.table.gstBilled"/></td>
            <td align="center"><fmt:message key="admin.gstReport.table.revenue"/></td>
            <td align="center"><fmt:message key="admin.gstReport.table.totalWithOnlyGst"/></td>
        </tr>
        <c:forEach var="row" items="${gstReportModel.rows}">
            <tr>
                <td width="20%" align="center"><carlos:encode value="${row.serviceDate}"/></td>
                <td width="10%" align="center"><carlos:encode value="${row.demographicNo}"/></td>
                <td width="15%" align="center"><carlos:encode value="${row.patientName}"/></td>
                <td width="15%" align="center">${row.gstBilled}</td>
                <td width="15%" align="center">${row.earned}</td>
                <td width="15%" align="center">${row.billed}</td>
            </tr>
        </c:forEach>
        <tr align="center" style="font-weight:bold;">
            <td width="20%">Totals:</td>
            <td></td>
            <td></td>
            <td width="15%" align="center">${gstReportModel.gstTotal}</td>
            <td width="15%">${gstReportModel.earnedTotal}</td>
            <td width="15%">${gstReportModel.billedTotal}</td>
        </tr>
    </table>
</form>
</body>
<script type="text/javascript">
    flatpickr("#xml_vdate", {dateFormat: "Y-m-d", allowInput: true});
    flatpickr("#xml_appointment_date", {dateFormat: "Y-m-d", allowInput: true});
</script>
</html>
