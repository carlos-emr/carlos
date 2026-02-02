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


<%@ page import="java.math.*, java.util.*, java.io.*, java.sql.*, io.github.carlos_emr.*, java.net.*,io.github.carlos_emr.MyDateFormat" %>


<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.CtlBillingServicePremium" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao" %>
<%
    CtlBillingServicePremiumDao dao = SpringUtils.getBean(CtlBillingServicePremiumDao.class);
%>
<%
    String temp;
    int recordAffected = -100;
    for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
        temp = e.nextElement().toString();
        if (temp.indexOf("service") == -1) continue;

        for (CtlBillingServicePremium b : dao.findByServiceCode(request.getParameter(temp))) {
            dao.remove(b.getId());
        }
    }

    response.sendRedirect("manageBillingform.jsp");
%>
