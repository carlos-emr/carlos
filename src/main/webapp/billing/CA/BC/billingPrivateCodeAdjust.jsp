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

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="http://displaytag.sf.net" prefix="display" %>
<%@ page import="java.util.*,io.github.carlos_emr.carlos.billings.ca.bc.data.BillingCodeData,io.github.carlos_emr.carlos.billing.ca.bc.pageUtil.*" %>

<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.ManagePrivFrm"/></title>
        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet">
    </head>

    <body>
    <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.ManagePrivFrm"/></h3>
    <div class="container-fluid well">

        <a href="billingAddPrivateCode.jsp?addNew=true" class="btn btn-primary">Add Code</a>

        <form action="billingPrivateCodeAdjust.jsp" method="get">
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
        <display:table name="test" defaultsort="1" defaultorder="descending"
                       decorator="io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillCodesTableWrapper"
                       class="table table-hover table-striped">
            <display:column property="serviceCode" title="Service Code" sortable="true" headerClass="sortable"/>
            <display:column property="description" title="Description" sortable="true" headerClass="sortable"/>
            <display:column property="value" title="Price" sortable="true" headerClass="sortable"/>
            <display:column property="billingserviceNo" title="Options"/>
        </display:table>
        <%}%>
    </div>
    </body>
</html>
