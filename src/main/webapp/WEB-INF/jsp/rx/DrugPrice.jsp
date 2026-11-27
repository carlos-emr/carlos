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

<%--
    DrugPrice.jsp — Drug Price Lookup AJAX Endpoint

    Purpose:
    Returns an HTML snippet containing the estimated prescription cost for a given DIN
    and quantity. Intended to be called via AJAX from the prescription writing interface.

    Parameters (request):
    - din      : Drug Identification Number used to look up the unit price
    - qty      : Optional positive decimal dispense quantity

    Response:
    Renders a Canadian-dollar total for a known dispense quantity or a per-unit estimate.
    An unpriced DIN produces an empty fragment.

    @since 2026-03-22
--%>

<%@ page import="io.github.carlos_emr.carlos.prescript.util.DrugPriceEstimate" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.util.DrugPriceLookup" %>

<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%
    String unitPrice = DrugPriceLookup.getPriceInfoForDin(request.getParameter("din"));
    DrugPriceEstimate estimate = DrugPriceEstimate.from(unitPrice, request.getParameter("qty"));
    if (estimate != null) {
%>
    <span style="margin-left:2px; margin-right:2px;">
    <% if (estimate.quantity() == null) { %>
        <fmt:message key="WriteScript.msgDrugPricePerUnit" var="pricePerUnit">
            <fmt:param value="<%= estimate.amount() %>"/>
        </fmt:message>
        <carlos:encode value="${pricePerUnit}" context="html"/>
    <% } else { %>
        <carlos:encode value='<%= estimate.amount() + "/" + estimate.quantity() %>' context="html"/>
    <% } %>
    </span>
<% } %>
