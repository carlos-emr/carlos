<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<!DOCTYPE html>
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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<%@ page import="java.util.*, io.github.carlos_emr.carlos.billings.ca.bc.data.BillingCodeData" %>

<html>
    <head>
        <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/share/javascript/carlosCsrfForm.js"></script>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <title><fmt:message key="admin.admin.ManagePrivFrm"/></title>
        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
        <script>
            function deletePrivateCode(code) {
                var form = document.createElement('form');
                form.method = 'post';
                form.action = '<%= request.getContextPath() %>/billing/CA/BC/DeletePrivateCode';
                var input = document.createElement('input');
                input.type = 'hidden';
                input.name = 'code';
                input.value = code;
                form.appendChild(input);
                document.body.appendChild(form);
                // carlosSubmitForm attaches the CSRF token, which CSRFGuard cannot inject
                // into a runtime-built form in time (#4130).
                carlosSubmitForm(form);
            }
        </script>
    <%@ include file="/WEB-INF/jsp/common/carlosTableAssets.jspf" %>
</head>

    <body>
    <h3><fmt:message key="admin.admin.ManagePrivFrm"/></h3>
    <div class="container-fluid card card-body bg-body-tertiary">

        <a href="<%= request.getContextPath() %>/billing/CA/BC/ViewBillingAddPrivateCode?addNew=true" class="btn btn-primary">Add Code</a>

        <form action="<%= request.getContextPath() %>/billing/CA/BC/ViewBillingPrivateCodeAdjust" method="get">
            <%if (request.getAttribute("returnMessage") != null) { %>
            <%=request.getAttribute("returnMessage")%>
            <%} %>
        </form>
        <%

            String sortOrder = request.getParameter("sortOrder") != null ? request.getParameter("sortOrder") : "";
            BillingCodeData bcds = new BillingCodeData();
            ArrayList list = (ArrayList) bcds.findBillingCodesByCode("A", sortOrder.equals("desc") ? 1 : 0);
            if (list != null) {
                String arrow = "";
                String newOrder = "";
                if (sortOrder.equals("desc")) {
                    newOrder = "";
                    arrow = "&uarr;";
                } else if (sortOrder.equals("")) {
                    newOrder = "desc";
                    arrow = "&darr;";
                }
        %> <% request.setAttribute("test", list); %>
        <c:if test="${not empty test}">
        <table class="table table-hover table-striped" data-carlos-table data-page-size="0" data-default-sort="1" data-default-order="descending">
<thead><tr>
            <th scope="col" class="sortable"><span data-table-sort>Service Code</span></th>
            <th scope="col" class="sortable"><span data-table-sort>Description</span></th>
            <th scope="col" class="sortable"><span data-table-sort>Price</span></th>
            <th scope="col">Options</th>
        </tr></thead>
<tbody><c:forEach var="tableRow" items="${test}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">
            <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(tableRow.serviceCode))}" data-sort-type="${carlos:tableSortType(tableRow.serviceCode)}">${carlos:forHtml(tableRow.serviceCode)}</td>
            <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(tableRow.description))}" data-sort-type="${carlos:tableSortType(tableRow.description)}">${carlos:forHtml(tableRow.description)}</td>
            <td data-sort-value="${carlos:forHtmlAttribute(tableRow.value)}" data-sort-type="number">${carlos:forHtml(tableRow.value)}</td>
            <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(tableRow.billingserviceNo))}" data-sort-type="${carlos:tableSortType(tableRow.billingserviceNo)}"><a href="ViewBillingEditCode?codeId=${carlos:forUriComponent(tableRow.billingserviceNo)}&amp;code=${carlos:forUriComponent(tableRow.serviceCode)}&amp;desc=${carlos:forUriComponent(tableRow.description)}&amp;value=${carlos:forUriComponent(tableRow.value)}&amp;whereTo=private">Edit</a><br><a href="#" onclick="deletePrivateCode('${carlos:forJavaScriptAttribute(tableRow.billingserviceNo)}'); return false;">Delete</a></td>
        </tr></c:forEach></tbody>
</table>
        </c:if>
<c:if test="${empty test}"><p class="table-empty">Nothing found to display.</p></c:if>
        <%}%>
    </div>
    </body>
</html>
