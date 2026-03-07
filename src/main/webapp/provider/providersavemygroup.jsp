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
    if (session.getValue("user") == null) response.sendRedirect(request.getContextPath() + "/logout.htm");
%>
<%@ page import="java.sql.*, java.util.*, io.github.carlos_emr.MyDateFormat" errorPage="/errorpage.jsp" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MyGroup" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MyGroupPrimaryKey" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.MyGroupDao" %>
<%
    MyGroupDao myGroupDao = SpringUtils.getBean(MyGroupDao.class);
%>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providersavemygroup.msgTitle"/></title>
    </head>
    <body>
    <div class="container-fluid p-3">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" class="page-header-icon">
                    <path d="M7 14s-1 0-1-1 1-4 5-4 5 3 5 4-1 1-1 1zm4-6a3 3 0 1 0 0-6 3 3 0 0 0 0 6m-5.784 6A2.24 2.24 0 0 1 5 13c0-1.355.68-2.75 1.936-3.72A6.3 6.3 0 0 0 5 9c-4 0-5 3-5 4s1 1 1 1zM4.5 8a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5"/>
                </svg>
                &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providersavemygroup.msgTitle"/>
            </h4>
        </div>

        <%
            int rowsAffected = 0, datano = 0;

            for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                StringBuffer strbuf = new StringBuffer(e.nextElement().toString());
                if (strbuf.toString().indexOf("data") == -1) continue;
                datano = Integer.parseInt(request.getParameter(strbuf.toString()));
                MyGroup myGroup = new MyGroup();
                myGroup.setId(new MyGroupPrimaryKey());
                myGroup.getId().setMyGroupNo(request.getParameter("mygroup_no"));
                myGroup.getId().setProviderNo(request.getParameter("provider_no" + datano));
                myGroup.setFirstName(request.getParameter("first_name" + datano));
                myGroup.setLastName(request.getParameter("last_name" + datano));
                if (myGroupDao.find(myGroup.getId()) == null) {
                    myGroupDao.persist(myGroup);
                }
                rowsAffected = 1;
            }

            if (rowsAffected == 1) {
                response.sendRedirect(request.getContextPath() + "/provider/providercontrol.jsp?displaymode=displaymygroup");
                return;
            }
        %>
        <div class="alert alert-danger" role="alert">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providersavemygroup.msgFailed"/>
        </div>

        <input type="button" class="btn btn-secondary btn-sm"
               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnBack"/>"
               onClick="window.history.go(-1);return false;">
    </div>
    </body>
</html>
