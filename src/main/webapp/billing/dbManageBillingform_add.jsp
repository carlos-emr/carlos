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

<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.CtlBillingService" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.CtlDiagCode" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CtlDiagCodeDao" %>
<%
    CtlBillingServiceDao ctlBillingServiceDao = SpringUtils.getBean(CtlBillingServiceDao.class);
    CtlDiagCodeDao ctlDiagCodeDao = SpringUtils.getBean(CtlDiagCodeDao.class);
%>
<%


    String group1 = "", group2 = "", group3 = "";
    String typeid = "", type = "";

    typeid = request.getParameter("typeid");
    type = request.getParameter("type");
    group1 = request.getParameter("group1");
    group2 = request.getParameter("group2");
    group3 = request.getParameter("group3");

%>

<%
    if (type.compareTo("") == 0 || group1.compareTo("") == 0 || group2.compareTo("") == 0 || group3.compareTo("") == 0) {
        String errormsg = "Error: Type Description, Groups Descrption  must be entered.";
        out.println(errormsg);

    } else {
        CtlBillingService cbs = new CtlBillingService();
        cbs.setServiceTypeName(type);
        cbs.setServiceType(typeid);
        cbs.setServiceCode("A007A");
        cbs.setServiceGroupName(group1);
        cbs.setServiceGroup("Group1");
        cbs.setStatus("A");
        cbs.setServiceOrder(1);
        ctlBillingServiceDao.persist(cbs);


        cbs = new CtlBillingService();
        cbs.setServiceTypeName(type);
        cbs.setServiceType(typeid);
        cbs.setServiceCode("A007A");
        cbs.setServiceGroupName(group2);
        cbs.setServiceGroup("Group2");
        cbs.setStatus("A");
        cbs.setServiceOrder(1);
        ctlBillingServiceDao.persist(cbs);

        cbs = new CtlBillingService();
        cbs.setServiceTypeName(type);
        cbs.setServiceType(typeid);
        cbs.setServiceCode("A007A");
        cbs.setServiceGroupName(group3);
        cbs.setServiceGroup("Group3");
        cbs.setStatus("A");
        cbs.setServiceOrder(1);
        ctlBillingServiceDao.persist(cbs);

        String[] param3 = new String[3];


        CtlDiagCode cdc = new CtlDiagCode();
        cdc.setServiceType(typeid);
        cdc.setDiagnosticCode("000");
        cdc.setStatus("A");
        ctlDiagCodeDao.persist(cdc);

%>
<% response.sendRedirect("manageBillingform.jsp"); %>
<%
    }


%>
