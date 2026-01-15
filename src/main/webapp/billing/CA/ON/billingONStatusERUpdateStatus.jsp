<%--

    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

--%>
<%
    if (session.getAttribute("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
    //String user_no = (String) session.getAttribute("user");
%>
<%@ page
        import="ca.openosp.openo.billing.ca.on.data.*, java.sql.*, ca.openosp.*, java.net.*"
        errorPage="/errorpage.jsp" %>
<%@ page import="ca.openosp.openo.billings.ca.on.data.JdbcBillingErrorRepImpl" %>
<%
    String id = request.getParameter("id");
    String val = request.getParameter("val");
    ;
    JdbcBillingErrorRepImpl dbObj = new JdbcBillingErrorRepImpl();
    boolean bChecked = dbObj.updateErrorReportStatus(id, val);
    String ret = "Y".equals(val) ? "checked" : "uncheck";
    out.println(ret);

%>
