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
  Purpose: Supports manageBillingPaymentType in the Ontario billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%--
    manageBillingPaymentType.jsp (view) - Ontario billing payment type list.
    Rendered by PaymentType2Action#listAllType which exposes
    ${paymentTypeList}. _billing w privilege is enforced upstream.
    Pure presentation here.
    @since 2001
--%>
<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title>Manage Billing Payment Type</title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
    <!-- Bootstrap 2.3.1 -->
    <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/css/dataTables.bootstrap5.min.css" rel="stylesheet" type="text/css">
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="${pageContext.request.contextPath}/js/global.js"></script>
    <script src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/js/jquery.dataTables.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.11/js/dataTables.bootstrap5.min.js"></script>

    <script>
        function csrfTokenValue() {
            var tokenInput = document.querySelector("input[name='CSRF-TOKEN']");
            return tokenInput ? tokenInput.value : "";
        }

        jQuery(document).ready(function () {
            jQuery('#tblBillType').DataTable({
                "order": [],
                "language": {
                    "url": "${pageContext.request.contextPath}/library/DataTables/i18n/<fmt:message key="global.i18n.datatablescode"/>.json"
                }
            });
        });
    </script>
</head>
<body>
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>
&nbsp;<h4>Manage Billing Payment Type</h4>

<div class="card card-body bg-body-tertiary">
    <table style="width:80%" id="tblBillType" class="table table-striped">
        <thead>
        <tr>
            <th>Id</th>
            <th>Type</th>
            <th>Operation</th>
            <th></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="paymentType" items="${paymentTypeList}">
            <tr>
                <td><carlos:encode value="${paymentType.id}" context="html"/>
                </td>
                <td><carlos:encode value="${paymentType.paymentType}" context="html"/>
                </td>
                <td>
                    <a href="${pageContext.request.contextPath}/billing/CA/ON/EditBillingPaymentType?id=${carlos:forUriComponent(paymentType.id)}&type=${carlos:forUriComponent(paymentType.paymentType)}">Edit</a>
                </td>
                <td>
                    <a href="#" data-paymentTypeId="<carlos:encode value='${paymentType.id}' context='htmlAttribute'/>">Delete</a>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
    <p>
    <hr/>
    <a class="btn btn-secondary"
       href="${pageContext.request.contextPath}/billing/CA/ON/EditBillingPaymentType">Create
        a new payment type</a>

</div>


<script type="text/javascript">

    jQuery(document).ready(function () {
        jQuery("tr td:nth-child(4)").on("click", "a", function (event) {
            jQuery.ajax({
                url: "${pageContext.request.contextPath}/billing/CA/ON/removePaymentType",
                type: "post",
                async: false,
                headers: {"CSRF-TOKEN": csrfTokenValue()},
                timeout: 30000,
                dataType: "json",
                data: {paymentTypeId: event.target.getAttribute("data-paymentTypeId")},
                success: function (data) {
                    if (data == null) {
                        alert("Error happened after getting response!");
                    }
                    if (parseInt(data.ret) == 0) {
                        alert("Successed deleting the payment type!");
                        location.href = "${pageContext.request.contextPath}/billing/CA/ON/managePaymentType";
                    } else {
                        alert("Failed to delete the payment type, reason:" + data.reason);
                    }
                },
                error: function () {
                    alert("Error happened!!");
                }
            });
            return false;
        });
    })

</script>
</body>
</html>
