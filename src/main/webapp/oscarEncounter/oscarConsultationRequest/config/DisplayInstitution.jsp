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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.consult" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.consult");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@page import="java.util.List" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Institution" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.InstitutionDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Department" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DepartmentDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.InstitutionDepartment" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.InstitutionDepartmentPK" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.InstitutitionDepartmentDao" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConTitlebar" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    InstitutionDao institutionDao = SpringUtils.getBean(InstitutionDao.class);
    DepartmentDao departmentDao = SpringUtils.getBean(DepartmentDao.class);
    InstitutitionDepartmentDao institutionDepartmentDao = SpringUtils.getBean(InstitutitionDepartmentDao.class);

    String id = (String) request.getAttribute("id");
    String name = (String) request.getAttribute("name");
%>
<!DOCTYPE html>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title>Display Institution</title>
    </head>

    <body>
    <div class="container-fluid">
        <div class="page-header-bar">
            <h5 class="page-header-title">Adjust Institutions</h5>
        </div>

<%
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
        <div class="action-errors">
            <ul>
                <% for (String error : actionErrors) { %>
                    <li><%= Encode.forHtml(error) %></li>
                <% } %>
            </ul>
        </div>
<% } %>

        <div class="row">
            <div class="col-md-3 consult-sidebar">
                <%
                    EctConTitlebar titlebar = new EctConTitlebar(request);
                    out.print(titlebar.estBar(request));
                %>
            </div>

            <div class="col-md-9">
                <p>Please check off all the departments offered by <%= Encode.forHtml(name) %>.</p>

                <form action="${pageContext.request.contextPath}/oscarEncounter/UpdateInstitutionDepartment.do" method="post">
                    <input type="hidden" name="id" value="<%=id %>">
                    <input type="submit" class="btn btn-primary mb-3" value="Update Institution Department">

                    <table class="table table-sm table-hover table-bordered">
                        <thead>
                            <tr>
                                <th class="col-checkbox">&nbsp;</th>
                                <th>Department</th>
                            </tr>
                        </thead>
                        <tbody>
                            <%
                                for (Department i : departmentDao.findAll()) {
                                    InstitutionDepartment assoc = institutionDepartmentDao.find(new InstitutionDepartmentPK(Integer.parseInt(id), i.getId()));
                            %>
                            <tr>
                                <td><input type="checkbox" name="specialists" value="<%=i.getId()%>" <%=assoc != null ? "checked" : ""%>></td>
                                <td><%= Encode.forHtml(i.getName()) %></td>
                            </tr>
                            <% } %>
                        </tbody>
                    </table>
                </form>
            </div>
        </div>
    </div>
    </body>
</html>
