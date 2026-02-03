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

<%@ page import="java.math.*, java.util.*, java.io.*, java.sql.*, io.github.carlos_emr.*, java.net.*,io.github.carlos_emr.MyDateFormat" %>

<%@page import="io.github.carlos_emr.carlos.commn.model.CtlBillingService" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.CtlDiagCode" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.CtlDiagCodeDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>


<%
    CtlBillingServiceDao billingServiceDao = SpringUtils.getBean(CtlBillingServiceDao.class);
    CtlDiagCodeDao diagCodeDao = SpringUtils.getBean(CtlDiagCodeDao.class);

    String typeid = request.getParameter("servicetype");

    for (CtlBillingService b : billingServiceDao.findByServiceType(typeid)) {
        billingServiceDao.remove(b.getId());
    }

    for (CtlDiagCode d : diagCodeDao.findByServiceType(typeid)) {
        diagCodeDao.remove(d.getId());
    }
%>
<script LANGUAGE="JavaScript">
    self.close();
    self.opener.refresh();
</script>

