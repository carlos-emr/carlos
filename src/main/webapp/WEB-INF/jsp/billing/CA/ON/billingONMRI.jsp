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
    <title><fmt:message key="admin.admin.btnGenerateOHIPDiskette"/></title>

    <script src="${pageContext.request.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.js"></script>

    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.css" rel="stylesheet" type="text/css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/fontawesome-all.min.css">

    <script>

        var checkSubmitFlg = false;

        function checkSubmit() {
            if (checkSubmitFlg == true) {
                return false;
            }
            checkSubmitFlg = true;
            document.forms[0].Submit.disabled = true;
            return true;
        }

        function recreate(si) {
            var ret = confirm("Are you sure you want to regenerate the file? \n\nWARNING: This should only be performed in very specific circumstances. If you are unsure, consult your OSCAR administrator before using this feature.");
            if (!ret) return;
            // ViewOnregenreport is a mutation gate (POST-only). Build a form on
            // the fly so CSRFGuard auto-injects the token alongside our params.
            var ss = document.forms[0].billcenter[document.forms[0].billcenter.selectedIndex].value;
            var su = document.forms[0].useProviderMOH.checked;
            var f = document.createElement("form");
            f.method = "post";
            f.action = "${pageContext.request.contextPath}/billing/CA/ON/ViewOnregenreport";
            ["diskId", "billcenter", "useProviderMOH"].forEach(function (n) {
                var input = document.createElement("input");
                input.type = "hidden";
                input.name = n;
                input.value = (n === "diskId" ? si : (n === "billcenter" ? ss : su));
                f.appendChild(input);
            });
            document.body.appendChild(f);
            f.submit();
        }

        var providerBillCenterMap = {};
        <c:forEach var="entry" items="${mriModel.providerBillCenterMap}">
        providerBillCenterMap['<carlos:encode value="${entry.key}" context="javaScriptBlock"/>'] = '<carlos:encode value="${entry.value}" context="javaScriptBlock"/>';
        </c:forEach>

        function setBillingCenter(providerNo) {
            var bcDropdown = document.getElementById("billcenter");

            var textToFind = providerBillCenterMap[providerNo];

            if (bcDropdown) {
                for (var i = 0; i < bcDropdown.options.length; i++) {
                    if (bcDropdown.options[i].value === textToFind) {
                        bcDropdown.selectedIndex = i;
                        break;
                    }
                }
            }
        }
    </script>

    <style type="text/css">
        input[name=useProviderMOH] {
            margin: 4px 4px 4px;
        }

        @media print {
            /*this is so the link locatons don't display*/
            a:link:after, a:visited:after {
                content: "";
            }
        }
    </style>
</head>

<body>

<h3><fmt:message key="admin.admin.btnGenerateOHIPDiskette"/></h3>

<div class="container-fluid">

    <div id="Layer1" style="position: absolute; left: 90px; top: 35px; width: 0px; height: 12px; z-index: 1"></div>

    <div class="row card card-body bg-body-tertiary d-print-none">

        <button type='button' name='print' value='Print' class="btn d-print-none" onClick='window.print()'
                style="position:absolute;top:20px;right:20px;"><i class="fa-solid fa-print"></i> Print
        </button>

        <div class="dropdown">
            <a href="#" class="dropdown-archive dropdown-toggle" data-bs-toggle="dropdown" aria-expanded="false">Show Archive</a>
            <ul class="dropdown-menu" role="menu" aria-labelledby="dLabel">
                <c:forEach var="year" items="${mriModel.archiveYears}">
                <li><a class="dropdown-item" href="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingONMRI?year=${carlos:forUriComponent(year)}">YEAR <carlos:encode value="${year}" context="html"/></a></li>
                </c:forEach>
            </ul>
        </div>

        <form name="form1" method="post" action="${pageContext.request.contextPath}/billing/CA/ON/ViewOngenreport" onsubmit="return checkSubmit();">

            <div class="col-md-4">
                Select Provider<br>
                <select name="providers" onchange="setBillingCenter(this.value);">
                    <c:if test="${fn:length(mriModel.providerOptions) != 1}">
                    <option value="all">All Providers</option>
                    </c:if>
                    <c:forEach var="pe" items="${mriModel.providerOptions}">
                    <option value="<carlos:encode value='${pe.providerNo}' context='htmlAttribute'/>"><carlos:encode value="${pe.lastName}" context="html"/>, <carlos:encode value="${pe.firstName}" context="html"/></option>
                    </c:forEach>
                </select>
            </div>

            <div class="col-md-4">
                Billing Center<br>
                <select name="billcenter" id="billcenter">
                    <c:forEach var="bc" items="${mriModel.billCenterOptions}">
                    <option value="<carlos:encode value='${bc.code}' context='htmlAttribute'/>" <c:if test="${bc.selected}">selected</c:if>><carlos:encode value="${bc.label}" context="html"/></option>
                    </c:forEach>
                </select>
            </div>

            <input type="hidden" name="monthCode" value="<carlos:encode value="${mriModel.monthCode}" context="htmlAttribute"/>">
            <input type="hidden" name="verCode" value="V03">
            <input type="hidden" name="curUser" value="<carlos:encode value="${mriModel.userProviderNo}" context="htmlAttribute"/>">
            <input type="hidden" name="curDate" value="<carlos:encode value="${mriModel.currentTimestamp}" context="htmlAttribute"/>">

            <div class="col-md-4">
                <label>Service Date Start:</label>
                <div class="input-group">
                    <input type="text" name="xml_vdate" id="xml_vdate" value="<carlos:encode value="${mriModel.serviceDateStart}" context="htmlAttribute"/>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                    <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                </div>
            </div>

            <div class="col-md-4">
                <label>Service Date End:</label>
                <div class="input-group">
                    <input type="text" name="xml_appointment_date" id="xml_appointment_date"
                           value="<carlos:encode value="${mriModel.serviceDateEnd}" context="htmlAttribute"/>" pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$"
                           autocomplete="off"/>
                    <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                </div>
            </div>

            <div class="col-md-10">
                <input type="checkbox" name="useProviderMOH"
                       id="useProviderMOH" <c:if test="${mriModel.useProviderMOHChecked}">checked</c:if>>Use
                individual provider's bill center setting (will use above bill center if provider does not have one
                set.)
                <br><br>
                <input class="btn btn-primary" type="submit" name="Submit" value="Create Report">
            </div>
        </form>

    </div><!--row well-->

    <table class="table ">
        <thead>
        <tr>
            <th>Provider</th>
            <th>Creation Date</th>
            <th>Clm/Rec</th>
            <th>Total</th>
            <th colspan=2>to OHIP</th>
            <th>HTML</th>
        </tr>
        </thead>

        <tbody>
        <c:forEach var="row" items="${mriModel.mriRows}">
        <tr onMouseOver="this.style.backgroundColor='pink';" onMouseout="this.style.backgroundColor='<carlos:encode value='${row.rowBgColor}' context='javaScriptAttribute'/>';"
            bgcolor="<carlos:encode value='${row.rowBgColor}' context='htmlAttribute'/>">
            <td><font size="2"><carlos:encode value="${row.providerName}" context="html"/></font></td>
            <td align="center"><font size="2"><carlos:encode value="${row.updateDate}" context="html"/></font></td>
            <td align="center"><font size="2"><carlos:encode value="${row.claimRecord}" context="html"/></font></td>
            <td align="right"><font size="2"><carlos:encode value="${row.total}" context="html"/></font></td>

            <td width="15%"><font size="2"> <a
                    href="${pageContext.request.contextPath}/servlet/OscarDownload?homepath=ohipdownload&filename=${carlos:forUriComponent(row.ohipFile)}"
                    target="_blank"><carlos:encode value="${row.ohipFile}" context="html"/></a></font></td>
            <td width="3%"><input type="button" value="R" class="btn d-print-none"
                                  onclick="recreate(<c:out value='${row.diskId}'/>)"/></td>
            <td><font size="2"> <a
                    href="${pageContext.request.contextPath}/servlet/OscarDownload?homepath=ohipdownload&filename=${carlos:forUriComponent(row.htmlFile)}"
                    target="_blank"><carlos:encode value="${row.htmlFile}" context="html"/></a></font></td>
        </tr>
        </c:forEach>

        <c:forEach var="row" items="${mriModel.billActivityRows}">
        <tr bgcolor="<carlos:encode value='${row.rowBgColor}' context='htmlAttribute'/>">
            <td><c:if test="${not empty row.providerName}"><font size="2"><carlos:encode value="${row.providerName}" context="html"/></font></c:if></td>
            <td align="center"><font size="2"><carlos:encode value="${row.updateDate}" context="html"/></font></td>
            <td align="center"><font size="2"><carlos:encode value="${row.claimRecord}" context="html"/></td>
            <td align="right"><font size="2"><carlos:encode value="${row.formattedTotal}" context="html"/></font></td>

            <td colspan=2><font size="2"> <a
                    href="${pageContext.request.contextPath}/servlet/OscarDownload?homepath=ohipdownload&filename=${carlos:forUriComponent(row.ohipFile)}"
                    target="_blank"><carlos:encode value="${row.ohipFile}" context="html"/></a></font></td>
            <td><font size="2"> <a
                    href="${pageContext.request.contextPath}/servlet/OscarDownload?homepath=ohipdownload&filename=${carlos:forUriComponent(row.htmlFile)}"
                    target="_blank"><carlos:encode value="${row.htmlFile}" context="html"/></a></font></td>
        </tr>
        </c:forEach>
        </tbody>
    </table>
</div><!--container-->

<script>
    flatpickr("#xml_vdate", {dateFormat: "Y-m-d", allowInput: true});
    flatpickr("#xml_appointment_date", {dateFormat: "Y-m-d", allowInput: true});

    document.addEventListener('DOMContentLoaded', function () {
        parent.resizeIframe(document.documentElement.scrollHeight);
    });

</script>
</body>
</html>
