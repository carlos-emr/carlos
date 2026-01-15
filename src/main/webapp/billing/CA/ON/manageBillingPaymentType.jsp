<%--

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

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada

--%>
<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="ca.openosp.openo.commn.model.BillingPaymentType" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<html>
<head>
    <title>Manage Billing Payment Type</title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <link href="${ pageContext.request.contextPath }/css/bootstrap.css" rel="stylesheet" type="text/css">
    <!-- Bootstrap 2.3.1 -->
    <link href="${ pageContext.request.contextPath }/css/DT_bootstrap.css" rel="stylesheet" type="text/css">
    <link href="${ pageContext.request.contextPath }/library/DataTables-1.10.12/media/css/jquery.dataTables.min.css"
          rel="stylesheet">
    <script src="${ pageContext.request.contextPath }/library/jquery/jquery-3.6.4.min.js"></script>
    <script src="${ pageContext.request.contextPath }/js/global.js"></script>
    <script src="${ pageContext.request.contextPath }/library/DataTables/datatables.min.js"></script>
    <!-- DataTables 1.13.4 -->

    <script>
        jQuery(document).ready(function () {
            jQuery('#tblBillType').DataTable({
                "order": [],
                "language": {
                    "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                }
            });
        });
    </script>

    <%
        List<BillingPaymentType> paymentTypeList = (List<BillingPaymentType>) request
                .getAttribute("paymentTypeList");
    %>
</head>
<body>
&nbsp;<h4>Manage Billing Payment Type</h4>

<div class="well">
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
        <%
            int count = 0;
            for (BillingPaymentType paymentType : paymentTypeList) {
                count++;
        %>
        <tr>
            <td><%=paymentType.getId()%>
            </td>
            <td><%=paymentType.getPaymentType()%>
            </td>
            <td>
                <a href="<%=request.getContextPath()%>/billing/CA/ON/editBillingPaymentType.jsp?id=<%=paymentType.getId()%>&type=<%=paymentType.getPaymentType()%>">Edit</a>
            </td>
            <td>
                <a href="#" data-paymentTypeId="<%=paymentType.getId()%>">Delete</a>
            </td>
        </tr>
        <%
            }
        %>
        </tbody>
    </table>
    <p>
    <hr/>
    <a class="btn"
       href="<%=request.getContextPath()%>/billing/CA/ON/editBillingPaymentType.jsp">Create
        a new payment type</a>

</div>


<script type="text/javascript">

    jQuery(document).ready(function () {
        jQuery("tr td:nth-child(4)").on("click", "a", function (event) {
            jQuery.ajax({
                url: "<%=request.getContextPath()%>/billing/CA/ON/managePaymentType.do",
                type: "get",
                async: "false",
                timeout: 30000,
                dataType: "json",
                data: {method: "removeType", paymentTypeId: event.target.getAttribute("data-paymentTypeId")},
                success: function (data) {
                    if (data == null) {
                        alert("Error happened after getting response!");
                    }
                    if (parseInt(data.ret) == 0) {
                        alert("Successed deleting the payment type!");
                        location.href = "<%=request.getContextPath()%>/billing/CA/ON/managePaymentType.do?method=listAllType";
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