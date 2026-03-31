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
    if (session.getAttribute("user") == null) {
        response.sendRedirect(request.getContextPath() + "/logout.htm");
        return;
    }
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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>


<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.title"/></title>
    </head>

    <body onLoad="setfocus()">
    <div class="container-fluid p-3">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" class="page-header-icon">
                    <path d="M7 14s-1 0-1-1 1-4 5-4 5 3 5 4-1 1-1 1zm4-6a3 3 0 1 0 0-6 3 3 0 0 0 0 6m-5.784 6A2.24 2.24 0 0 1 5 13c0-1.355.68-2.75 1.936-3.72A6.3 6.3 0 0 0 5 9c-4 0-5 3-5 4s1 1 1 1zM4.5 8a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5"/>
                </svg>
                &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.msgTitle"/>
            </h4>
        </div>

    <form name="UPDATEPRE" method="post" action="providercontrol.jsp">
        <input type="hidden" name="submit_form" value="">
        <input type="hidden" name="displaymode" value="newgroup">
        <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">

        <table class="table table-sm table-bordered mb-0">
            <thead class="table-light">
            <tr>
                <th style="width:10%" class="text-center" colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.msgGroupNo"/></th>
                <th class="text-center"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerdisplaymygroup.msgProvider"/></th>
            </tr>
            </thead>
            <tbody>
            <%
                boolean bNewNo = false;
                String oldNo = "";
                List<MyGroup> myGroups = dao.findAll();
                Collections.sort(myGroups, MyGroup.MyGroupNoComparator);
                for (MyGroup myGroup : myGroups) {

                    String groupNo = myGroup.getId().getMyGroupNo();
                    if (!(groupNo.equals(oldNo))) {
                        bNewNo = bNewNo ? false : true;
                        oldNo = groupNo;
                    }
            %>
            <tr class="<%=bNewNo?"":"table-light"%>">
                <td style="width:10%" class="text-center">
                    <input type="checkbox" class="form-check-input"
                           name="<%=Encode.forHtmlAttribute(groupNo+myGroup.getId().getProviderNo())%>"
                           value="<%=Encode.forHtmlAttribute(groupNo)%>">
                </td>
                <td class="text-center"><%=Encode.forHtml(groupNo)%></td>
                <td class="text-center"><%=Encode.forHtml(myGroup.getLastName() + ", " + myGroup.getFirstName())%></td>
            </tr>
            <%
                }
            %>
            </tbody>
        </table>

        <div class="d-flex align-items-center mt-3">
            <fmt:setBundle basename="oscarResources"/>
            <fmt:message key="provider.providerdisplaymygroup.confirmDelete" var="confirmDeleteMsg"/>
            <fmt:message key="provider.providerdisplaymygroup.btnDelete" var="btnDeleteLabel"/>
            <fmt:message key="provider.providerdisplaymygroup.btnNew" var="btnNewLabel"/>
            <fmt:message key="global.btnBack" var="btnBackLabel"/>
            <input type="submit" class="btn btn-danger btn-sm"
                   value="${e:forHtmlAttribute(btnDeleteLabel)}"
                   onclick="if(!confirm('${e:forJavaScript(confirmDeleteMsg)}')){return false;} document.forms['UPDATEPRE'].submit_form.value='Delete';">
            <input type="submit" class="btn btn-primary btn-sm ms-2"
                   value="${e:forHtmlAttribute(btnNewLabel)}"
                   onclick="document.forms['UPDATEPRE'].submit_form.value='New Group/Add a Member';">
            <input type="button" class="btn btn-secondary btn-sm ms-2"
                   value="${e:forHtmlAttribute(btnBackLabel)}"
                   onClick="if (window.opener) { window.close(); } else { window.history.back(); }">
        </div>

    </form>
    </div>

    </body>
</html>
