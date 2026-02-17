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
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logout.htm");
%>
<%@ page import="java.util.*,java.sql.*"
         errorPage="/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MyGroup" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.MyGroupDao" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%
    MyGroupDao dao = SpringUtils.getBean(MyGroupDao.class);
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<!DOCTYPE html>
<html lang="en">
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.title"/></title>
    </head>

    <body>
    <div class="container">

        <div class="page-header-bar d-flex align-items-center justify-content-between">
            <h4 class="page-header-title">
                <i class="fas fa-users page-header-icon"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.msgTitle"/>
            </h4>
        </div>

        <form name="UPDATEPRE" method="post" action="providercontrol.jsp">
            <input type="hidden" name="submit_form" value="">

            <div class="d-flex gap-2 mb-3">
                <input type="submit" class="btn btn-secondary btn-sm"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.btnDelete"/>"
                       onclick="document.forms['UPDATEPRE'].submit_form.value='Delete';">
                <input type="submit" class="btn btn-primary btn-sm"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.btnNew"/>"
                       onclick="document.forms['UPDATEPRE'].submit_form.value='New Group/Add a Member';">
            </div>

            <table class="table table-hover table-sm table-striped">
                <thead>
                    <tr>
                        <th style="width:50px"></th>
                        <th><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.msgGroupNo"/></th>
                        <th><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.msgProvider"/></th>
                    </tr>
                </thead>
                <tbody>
                    <%
                        String oldNo = "";
                        List<MyGroup> myGroups = dao.findAll();
                        Collections.sort(myGroups, MyGroup.MyGroupNoComparator);
                        for (MyGroup myGroup : myGroups) {
                            String groupNo = myGroup.getId().getMyGroupNo();
                            String providerNo = myGroup.getId().getProviderNo();
                    %>
                    <tr>
                        <td class="text-center">
                            <input type="checkbox"
                                   name="<%=Encode.forHtmlAttribute(groupNo + providerNo)%>"
                                   value="<%=Encode.forHtmlAttribute(groupNo)%>">
                        </td>
                        <td><%=Encode.forHtmlContent(groupNo)%></td>
                        <td><%=Encode.forHtmlContent(myGroup.getLastName() + ", " + myGroup.getFirstName())%></td>
                    </tr>
                    <%
                        }
                    %>
                </tbody>
            </table>

            <input type="hidden" name="displaymode" value="newgroup">
        </form>

    </div>
    </body>
</html>
