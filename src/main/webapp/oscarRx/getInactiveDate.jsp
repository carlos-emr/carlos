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

<%@page import="ca.openosp.openo.utility.MiscUtils" %>
<%@page import="java.util.*,java.io.*,org.apache.xmlrpc.*,ca.openosp.openo.rx.util.*,ca.openosp.openo.rx.data.*" %>
<%@page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@page import="com.fasterxml.jackson.databind.node.ObjectNode" %>
<%@page import="com.fasterxml.jackson.databind.node.ArrayNode" %>
<%@ page import="ca.openosp.openo.prescript.util.RxDrugRef" %>
<%
    String din = request.getParameter("din");
    String id = request.getParameter("id");
    Vector vec = new Vector();
    try {
        RxDrugRef drugData = new RxDrugRef();
        vec = drugData.getInactiveDate(din);

    } catch (Exception e) {
        MiscUtils.getLogger().error("Error", e);
    }


    Hashtable d = new Hashtable();

    d.put("id", id);
    d.put("vec", vec);


    response.setContentType("application/json");
    ObjectMapper mapper = new ObjectMapper();
    out.print(mapper.writeValueAsString(d));
%>
