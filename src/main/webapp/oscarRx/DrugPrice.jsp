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
    Document   : DrugPrice
    Created on : Apr 10, 2022
    Author     : phc
--%>


<%@page import="io.github.carlos_emr.carlos.demographic.data.*" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Demographic"%>
<%@page import="java.util.*" %>
<%@page import="java.text.*" %>
<%@page import="oscar.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.util.DrugPriceLookup" %>

           <%
		    String din = request.getParameter("din");
		    String randomId = request.getParameter("randomId");
		    String quantity = request.getParameter("qty");
		    String cost = DrugPriceLookup.getPriceInfoForDin(din);
		    String moneyString = "";
		    NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("en", "US"));

            if (cost != null && !cost.isEmpty() && cost.matches("\\d+(\\.\\d+)?")){
				//lets cast to float
				float fa = Float.valueOf(cost);
				float money = fa;
				if (quantity != null && !quantity.isEmpty() && !quantity.equals("0") && quantity.matches("\\d+(\\.\\d+)?")){
					float fb = Float.valueOf(quantity);
					money = fa * fb;
					moneyString = formatter.format(money)+"/"+quantity;
				} else {
				    //lets format it
					moneyString = formatter.format(money)+"/1";
                }
         %>
            <span style="margin-left:2px; margin-right: 2px;">
			<%=moneyString%>
            </span>
            <%}%>
