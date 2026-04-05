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
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<%@ page import="java.math.*, java.util.*, java.io.*, java.sql.*, io.github.carlos_emr.*, java.net.*,io.github.carlos_emr.MyDateFormat" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.CtlBillingType" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CtlBillingTypeDao" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%
    CtlBillingTypeDao ctlBillingTypeDao = SpringUtils.getBean(CtlBillingTypeDao.class);
%>


<%
    String type_id = "", type_name = "", billtype = "no";
    type_id = request.getParameter("type_id");
    type_name = request.getParameter("type_name");

    for (CtlBillingType cbt : ctlBillingTypeDao.findByServiceType(type_id)) {
        billtype = cbt.getBillType();
    }

%>

<table width=95%>
    <tr>
        <td class="black" width="15%"><%= Encode.forHtml(type_id) %>
        </td>
        <td class="black" height="30"><%= Encode.forHtml(type_name) %>
        </td>
    </tr>
    <tr>
        <td>&nbsp;</td>
        <td class="white">
            <p>&nbsp;<br>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.manageBillingform_add.formDefaultBillType"/>
                :<br>
                <input type="hidden" name="bill_servicetype" value="<%= Encode.forHtmlAttribute(type_id) %>">
                <input type="hidden" name="billtype_old" value="<%= Encode.forHtmlAttribute(billtype) %>">
                <select name="billtype_new">
                    <option value="no" <%=billtype.equals("no") ? "selected" : ""%>>--
                        no --
                    </option>
                    <option value="ODP" <%=billtype.equals("ODP") ? "selected" : ""%>>Bill
                        OHIP
                    </option>
                    <option value="WCB" <%=billtype.equals("WCB") ? "selected" : ""%>>WSIB</option>
                    <option value="NOT" <%=billtype.equals("NOT") ? "selected" : ""%>>Do
                        Not Bill
                    </option>
                    <option value="IFH" <%=billtype.equals("IFH") ? "selected" : ""%>>IFH</option>
                    <option value="PAT" <%=billtype.equals("PAT") ? "selected" : ""%>>3rd
                        Party
                    </option>
                    <option value="OCF" <%=billtype.equals("OCF") ? "selected" : ""%>>-OCF</option>
                    <option value="ODS" <%=billtype.equals("ODS") ? "selected" : ""%>>-ODSP</option>
                    <option value="CPP" <%=billtype.equals("CPP") ? "selected" : ""%>>-CPP</option>
                    <option value="STD" <%=billtype.equals("STD") ? "selected" : ""%>>-STD/LTD</option>
                </select> <input type="button" value="Change"
                                 onclick="manageBillType(bill_servicetype.value, billtype_old.value, billtype_new.value);"><br>
            </p>
            <p><input type="button" value="Delete Billing Form"
                      onclick="onUnbilled('<%= Encode.forJavaScript(type_id) %>');"></p>
            <p><input type="button" value="Cancel"
                      onclick="showManageType(false);"></p>
        </td>
    </tr>
</table>
