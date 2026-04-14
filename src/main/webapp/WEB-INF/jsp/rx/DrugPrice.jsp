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
    - qty      : Optional quantity multiplier; defaults to 1 if absent or zero
    - randomId : Row identifier passed by the caller (not used in response)

    Response:
    Renders a <span> containing the formatted Canadian-dollar cost as "{price}/{quantity}",
    or nothing if the DIN is not found or the price/quantity values are invalid.

    @since 2026-03-22
--%>

<%@page import="java.math.BigDecimal" %>
<%@page import="java.util.*" %>
<%@page import="java.text.*" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.util.DrugPriceLookup" %>
<%@ page import="org.owasp.encoder.Encode" %>

           <%
		    String din = request.getParameter("din");
		    String randomId = request.getParameter("randomId");
		    String quantity = request.getParameter("qty");
		    String cost = DrugPriceLookup.getPriceInfoForDin(din);
		    String moneyString = "";
		    NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.CANADA);

            if (cost != null && !cost.isEmpty() && cost.matches("\\d+(\\.\\d+)?")){
				BigDecimal unitPrice = new BigDecimal(cost);
				BigDecimal money = unitPrice;
				if (quantity != null && !quantity.isEmpty() && quantity.matches("\\d+(\\.\\d+)?")) {
					BigDecimal qty = new BigDecimal(quantity);
					if (qty.compareTo(BigDecimal.ZERO) > 0) {
						money = unitPrice.multiply(qty);
						moneyString = formatter.format(money)+"/"+quantity;
					} else {
						moneyString = formatter.format(unitPrice)+"/1";
					}
				} else {
				    //lets format it
					moneyString = formatter.format(money)+"/1";
                }
         %>
            <span style="margin-left:2px; margin-right: 2px;">
			<%=Encode.forHtml(moneyString)%>
            </span>
            <%}%>
