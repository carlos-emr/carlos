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
<%
    String user_no = (String) session.getAttribute("user");
%>
<%@ page import="java.util.*, java.sql.*, io.github.carlos_emr.*, java.net.*" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>

<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.BillingServiceDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.BillingService" %>


<%
    BillingServiceDao billingServiceDao = SpringUtils.getBean(BillingServiceDao.class);
%>
<% String search = "search_service_price";
    String formName = request.getParameter("formName");
    String formElementCode = request.getParameter("formElementCode");
    String formElementPrice = request.getParameter("formElementPrice");
    if (formName != null && !formName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) { formName = ""; }
    if (formElementCode != null && !formElementCode.matches("[a-zA-Z_][a-zA-Z0-9_]*")) { formElementCode = ""; }
    if (formElementPrice != null && !formElementPrice.matches("[a-zA-Z_][a-zA-Z0-9_]*")) { formElementPrice = ""; }
%>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>Service Code Price Search</title>
    <script LANGUAGE="JavaScript">
        function CodeAttach(cost) {
            self.close();
            self.opener.document["<%= Encode.forJavaScript(StringUtils.noNull(formName)) %>"]["<%= Encode.forJavaScript(StringUtils.noNull(formElementPrice)) %>"].value = cost;
        }
    </script>
</head>
<body bgcolor="#FFFFFF" text="#000000" topmargin="0" leftmargin="0"
      rightmargin="0">
<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr bgcolor="#486ebd">
        <th align=CENTER NOWRAP bgcolor="#CCCCFF"><font face="Helvetica"
                                                        color="#000000">Service Code Search </font><font
                face="Arial, Helvetica, sans-serif" color="#FF0000">(Maximum 3
            selections)</font></th>
    </tr>
</table>
<table width="800" border="1">
    <%
        List<BillingService> bss = billingServiceDao.findByServiceCode(formElementCode);

        if (bss.size() > 0) {
            String cost = bss.get(0).getValue(); %>
    <script LANGUAGE="JavaScript">
        <!--
        CodeAttach('<%=cost%>');
        -->
    </script>
    <%} else {%>
    <tr bgcolor="#ffffff">
        <td colspan="2"><font face="Arial, Helvetica, sans-serif"
                              size="2">No match found. </font></td>
    </tr>
    <%}%>
</table>
</body>
</html>
