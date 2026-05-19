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
  Purpose: Supports editBillingPaymentType in the Ontario billing workflow.
  Expected request model data includes: paymentTypeModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>

<style type="text/css">
    body {
        font-size: 18px;
        font-family: Verdana;
    }
</style>

<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title><carlos:encode value='${paymentTypeModel.title}' context='html'/>
    </title>
    <script type="text/javascript"
            src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
            <script src="${pageContext.request.contextPath}/library/jquery/jquery-compat.js"></script>
    <script type="text/javascript">

        function check() {
            if (document.getElementById("paymentType").value.length < 1) {
                alert("Payment type can not be empty!");
                return false;
            }
            return true;
        }

        function csrfTokenValue() {
            var tokenInput = document.querySelector("input[name='CSRF-TOKEN']");
            return tokenInput ? tokenInput.value : "";
        }

        function createType() {
            if (!check()) {
                return;
            }
            $.ajax({
                type: "POST",
                async: true,
                headers: {"CSRF-TOKEN": csrfTokenValue()},
                data: {paymentType: document.getElementById("paymentType").value},
                url: "${pageContext.request.contextPath}/billing/CA/ON/createPaymentType",
                dataType: "json",
                success: function (ret) {
                    if (!ret) {
                        alert("Failed to create new payment type!");
                    } else if (ret.ret == "1") {
                        alert(ret.reason);
                    } else {
                        alert("Success");
                        history.back();
                    }
                },
                error: function (XMLHttpRequest, textStatus, errorThrown) {
                    if (textStatus) {
                        alert(JSON.toString(textStatus));
                    } else if (errorThrown) {
                        alert(JSON.toString(errorThrown));
                    } else {
                        alert("Unknown error happened!");
                    }
                }
            });
        }

        function saveType() {
            if (!check()) {
                return;
            }
            $.ajax({
                type: "POST",
                async: true,
                headers: {"CSRF-TOKEN": csrfTokenValue()},
                data: {
                    id: "<carlos:encode value='${paymentTypeModel.id}' context='javaScriptBlock'/>",
                    oldPaymentType: "<carlos:encode value='${paymentTypeModel.type}' context='javaScriptBlock'/>",
                    paymentType: document.getElementById("paymentType").value
                },
                url: "${pageContext.request.contextPath}/billing/CA/ON/updatePaymentType",
                dataType: "json",
                success: function (ret) {
                    if (!ret) {
                        alert("Failed to create new payment type!");
                    } else if (ret.ret == "1") {
                        alert(ret.reason);
                    } else {
                        alert("Success");
                        history.back();
                    }
                },
                error: function (XMLHttpRequest, textStatus, errorThrown) {
                    if (textStatus) {
                        alert(JSON.toString(textStatus));
                    } else if (errorThrown) {
                        alert(JSON.toString(errorThrown));
                    } else {
                        alert("Unknown error happened!");
                    }
                }
            });
        }
    </script>

</head>

<body>
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>
<table width="100%">
    <tbody>
    <tr bgcolor="#CCCCFF">
        <th><carlos:encode value='${paymentTypeModel.title}' context='html'/>
        </th>
    </tr>
    </tbody>
</table>
<p/>
<p/>

<center>
    <input id="paymentType" name="paymentType" type="text"
           value="<carlos:encode value='${paymentTypeModel.type}' context="htmlAttribute"/>" placeholder="Please input a new payment type"
           size="38"/>
    <c:choose>
        <c:when test="${paymentTypeModel.modify}">
            <input name="save" type="button" onclick="saveType()" value="save"/>
        </c:when>
        <c:otherwise>
            <input name="create" type="button" onclick="createType()"
                   value="create"/>
        </c:otherwise>
    </c:choose>
    <input name="back" type="button" onclick="history.back();" value="back"/>
</center>
</body>
</html>
