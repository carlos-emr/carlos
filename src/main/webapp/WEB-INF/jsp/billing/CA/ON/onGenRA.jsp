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
  Purpose: Supports onGenRA in the Ontario billing workflow.
  Expected request model data includes: onGenRAModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<!DOCTYPE html>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<jsp:useBean id="documentBean" class="io.github.carlos_emr.DocumentBean" scope="request"/>
<%
    // Defensive top-of-page model resolver. The canonical entrypoint is
    // billing/CA/ON/ViewOnGenRA which assembles the model up front; any
    // forward that lands here directly gets the privilege check + assembler
    // re-run inline so the body can stay 100% EL.
    %>
<fmt:setBundle basename="oscarResources"/>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="admin.admin.btnBillingReconciliation"/></title>
    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/fontawesome-all.min.css">

    <script language="JavaScript">
        <!--
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

        function popPage(url) {
            awnd = rs('', url, 400, 200, 1);
            awnd.focus();
        }

        function postTo(action, rano, target) {
            var form = document.createElement('form');
            form.method = 'post';
            form.action = action;
            if (target) {
                form.target = target;
            }
            var input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'rano';
            input.value = rano;
            form.appendChild(input);
            document.body.appendChild(form);
            form.submit();
        }

        function checkReconcile(action, rano) {
            if (confirm("You are about to reconcile the file, are you sure?")) {
                postTo(action, rano);
            } else {
                alert("You have cancel the action!");
            }
        }

        //-->
    </SCRIPT>
</head>

<body>
<h3><fmt:message key="admin.admin.btnBillingReconciliation"/></h3>

<%-- Surface RA-import failures so the operator doesn't see a clean page
     when the import service silently rolled back. The action sets
     `raImportFailed=true` on the request when OnRaImportService
     returned false (file unreadable / parse failure). --%>
<c:if test="${raImportFailed}">
    <div class="alert alert-danger" role="alert">
        RA import failed — see server log for details. The displayed RA list
        does not reflect the file you just attempted to import.
    </div>
</c:if>

<div class="container-fluid card card-body bg-body-tertiary">
    <button class="btn btn-primary float-end" type='button' name='print' value='Print'
            onClick='window.print(); return false;'><i class="fa-solid fa-print"></i> Print
    </button>
    <br/><br/>

    <table class="table table-striped table-hover table-sm">
        <thead>
        <tr>
            <th>Read Date</th>
            <th>Payment Date</th>
            <th>Payable</th>
            <th>Records/Claims</th>
            <th>Total</th>
            <th>Action</th>
            <th>Status</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="row" items="${onGenRAModel.rows}" varStatus="rs">
            <tr class="${rs.index % 2 == 0 ? 'myGreen' : 'myIvory'}">
                <td><carlos:encode value="${row.readDate}" context="html"/></td>
                <td align="center"><carlos:encode value="${row.paymentDate}" context="html"/></td>
                <td><carlos:encode value="${row.payable}" context="html"/></td>
                <td align="center"><carlos:encode value="${row.claimsCount}" context="html"/>/<carlos:encode value="${row.recordsCount}" context="html"/></td>
                <td align="right"><carlos:encode value="${row.total}" context="html"/></td>
                <td align="center">
                    <a href="${pageContext.request.contextPath}/billing/CA/ON/ViewOnGenRAError?rano=<carlos:encode value='${row.raNo}' context='uriComponent'/>&proNo="
                       target="_blank">Error</a>
                    | <a href="#" onclick="postTo('${pageContext.request.contextPath}/billing/CA/ON/ViewOnGenRASummary','<carlos:encode value="${row.raNo}" context="javaScript"/>','_blank');return false;">Summary</a>
                    | <a href="#" onclick="postTo('${pageContext.request.contextPath}/billing/CA/ON/ViewGenRADesc','<carlos:encode value="${row.raNo}" context="javaScript"/>','_blank');return false;">Report</a>
                </td>
                <td>
                    <c:choose>
                        <c:when test="${row.status == 'N'}">
                            <a href="#" onclick="checkReconcile('${pageContext.request.contextPath}/billing/CA/ON/ViewOnGenRAsettle','<carlos:encode value="${row.raNo}" context="javaScript"/>')">Settle</a>
                            <a href="#" onclick="checkReconcile('${pageContext.request.contextPath}/billing/CA/ON/ViewOnGenRAsettle35','<carlos:encode value="${row.raNo}" context="javaScript"/>')">S35</a>
                        </c:when>
                        <c:when test="${row.status == 'S'}">
                            <a href="#" onclick="checkReconcile('${pageContext.request.contextPath}/billing/CA/ON/ViewOnGenRAsettle35','<carlos:encode value="${row.raNo}" context="javaScript"/>')">S35</a>
                        </c:when>
                        <c:otherwise>
                            Processed
                        </c:otherwise>
                    </c:choose>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>
</body>
</html>
