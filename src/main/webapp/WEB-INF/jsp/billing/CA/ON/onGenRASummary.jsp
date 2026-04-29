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
  Page role: Renders `onGenRASummary.jsp` for the Ontario billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@page import="io.github.carlos_emr.carlos.billings.ca.on.assembler.OnRaSummaryViewModelAssembler" %>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%-- Data assembly + RaHeader audit merge run in ViewOnGenRaSummary2Action via
     OnRaSummaryViewModelAssembler. The view model is stashed on the request as
     ${model}; this JSP only renders. The "no rano" early return in the legacy
     JSP becomes a no-op render. --%>
<c:if test="${not empty model.raNo}">
<html>
<head>
    <script type="text/javascript" src="<c:out value='${pageContext.request.contextPath}'/>/js/global.js"></script>
    <script type="text/javascript" src="<c:out value='${pageContext.request.contextPath}'/>/library/jquery/jquery-3.7.1.min.js"></script>
    <script type="text/javascript" src="<c:out value='${pageContext.request.contextPath}'/>/library/DataTables/DataTables-1.13.11/js/jquery.dataTables.min.js"></script>
    <link rel="stylesheet" type="text/css" href="<c:out value='${pageContext.request.contextPath}'/>/library/DataTables/DataTables-1.13.11/css/dataTables.bootstrap5.min.css"/>
    <link rel="stylesheet" type="text/css" href="billingON.css"/>
    <title>Billing Reconcilliation</title>
    <style>
        <c:choose>
            <c:when test="${model.multisitesEnabled}">
            .positionFilter { position: absolute; top: 2px; right: 350px; display: block; }
            </c:when>
            <c:otherwise>
            .positionFilter { display: none; }
            </c:otherwise>
        </c:choose>
    </style>
</head>

<body leftmargin="0" topmargin="0" marginwidth="0" marginheight="0">

<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <form action="<c:out value='${pageContext.request.contextPath}'/>/billing/CA/ON/ViewOnGenRASummary" method="post">
        <tr class="myDarkGreen">
            <th align='LEFT'><font color="#FFFFFF"> Billing Reconcilliation - Summary Report</font></th>
            <th align='RIGHT'>
                <select id="loadingMsg" class="positionFilter">
                    <option>Loading filters...</option>
                </select>
                <select name="proNo">
                    <option value="all" ${model.selectedProviderOhip == 'all' ? 'selected' : ''}>All Providers</option>
                    <c:forEach var="opt" items="${model.providerOptions}">
                        <option value="<carlos:encode value='${opt.ohipNo}' context='htmlAttribute'/>"
                                ${model.selectedProviderOhip == opt.ohipNo ? 'selected' : ''}>
                            <carlos:encode value='${opt.lastName}' context='html'/>,<carlos:encode value='${opt.firstName}' context='html'/>
                        </option>
                    </c:forEach>
                </select>
                <input type='submit' name='submit' value='Generate'>
                <input type="hidden" name="rano" value="<carlos:encode value='${model.raNo}' context='htmlAttribute'/>">
                <input type='button' name='print' value='Print' onClick='window.print()'>
                <input type='button' name='close' value='Close' onClick='window.close()'>
            </th>
        </tr>
    </form>
</table>

<table id="ra_table" width="100%" border="0" cellspacing="1" cellpadding="0" class="myIvory">
    <thead>
    <tr class="myYellow">
        <th width="6%">Billing No</th>
        <th width="7%">Claim No</th>
        <th width="14%">Patient</th>
        <th>Fam Doc</th>
        <th width="10%">HIN</th>
        <th width="9%">Service Date</th>
        <th width="8%">Service Code</th>
        <th width="7%" align=right>Invoiced</th>
        <th width="7%" align=right>Paid</th>
        <th width="7%" align=right>Clinic Pay</th>
        <th width="7%" align=right>Hospital Pay</th>
        <th width="7%" align=right>OB</th>
        <th align=right>Error</th>
        <th width="0" align=right style="display:none">Site</th>
    </tr>
    </thead>
    <tbody>
    <c:forEach var="row" items="${model.summaryRows}" varStatus="rs" end="${fn:length(model.summaryRows) - 2}">
        <tr class="${rs.index % 2 == 0 ? 'myGreen' : ''}">
            <td align="center"><carlos:encode value='${row["account"]}' context='html'/></td>
            <td align="center"><carlos:encode value='${row["claimNo"]}' context='html'/></td>
            <td><carlos:encode value='${row["demo_name"]}' context='html'/></td>
            <td align="center"><carlos:encode value='${row["demo_doc"]}' context='html'/></td>
            <td align="center"><carlos:encode value='${row["demo_hin"]}' context='html'/></td>
            <td align="center"><carlos:encode value='${row["servicedate"]}' context='html'/></td>
            <td align="center"><carlos:encode value='${row["servicecode"]}' context='html'/></td>
            <td align=right><carlos:encode value='${row["amountsubmit"]}' context='html'/></td>
            <td align=right><carlos:encode value='${row["amountpay"]}' context='html'/></td>
            <td align=right><carlos:encode value='${row["clinicPay"]}' context='html'/></td>
            <td align=right><carlos:encode value='${row["hospitalPay"]}' context='html'/></td>
            <td align=right><carlos:encode value='${row["obPay"]}' context='html'/></td>
            <td align=right><carlos:encode value='${row["explain"]}' context='html'/></td>
            <td width="0" style="display:none"><carlos:encode value='${row["site"]}' context='html'/></td>
        </tr>
    </c:forEach>
    </tbody>
    <tfoot>
    <tr class="myYellow">
        <td align="center"></td>
        <td></td>
        <td align="center"></td>
        <td align="center"></td>
        <td align="center"></td>
        <td align="center"></td>
        <td align="center">Total:</td>
        <td id="amountSubmit" align=right></td>
        <td id="amountPay" align=right></td>
        <td id="clinicPay" align=right></td>
        <td id="hospitalPay" align=right></td>
        <td id="OBPay" align=right></td>
        <td align=right>&nbsp;</td>
        <td align=right width="0" style="display:none">&nbsp;</td>
    </tr>
    </tfoot>
</table>

<script type="text/javascript">
    document.getElementById('loadingMsg').style.display = 'none';

    $(document).ready(function () {
        var sumCols = [7, 8, 9, 10, 11];
        var sumIds = ['amountSubmit', 'amountPay', 'clinicPay', 'hospitalPay', 'OBPay'];

        function updateSums(api) {
            sumCols.forEach(function (colIdx, i) {
                var total = api.column(colIdx, {search: 'applied'}).data().reduce(function (a, b) {
                    return a + (parseFloat(String(b).replace(/[^\d.-]/g, '')) || 0);
                }, 0);
                document.getElementById(sumIds[i]).textContent = total.toFixed(2);
            });
        }

        var multisites = ${model.multisitesEnabled};

        var table = $('#ra_table').DataTable({
            paging: false,
            info: false,
            searching: multisites,
            ordering: true,
            autoWidth: false,
            columnDefs: [{targets: 13, visible: false}],
            initComplete: function () {
                if (!multisites) return;
                var api = this.api();
                var siteCol = api.column(13);
                var $select = $('<select class="positionFilter"></select>');
                $select.append($('<option>').val('').text(' [ Show all clinics ] '));
                siteCol.data().unique().sort().each(function (d) {
                    if (d && d.trim()) $select.append($('<option>').val(d.trim()).text(d.trim()));
                });
                $select.on('change', function () {
                    siteCol.search($(this).val() ? '^' + $.fn.dataTable.util.escapeRegex($(this).val()) + '$' : '', true, false).draw();
                });
                $(api.table().container()).before($select);
            },
            drawCallback: function () {
                updateSums(this.api());
            }
        });

        updateSums(table);
    });
</script>

</body>
</html>
</c:if>
