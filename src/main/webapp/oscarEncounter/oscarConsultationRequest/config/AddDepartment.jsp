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

<%@ page import="java.util.ResourceBundle" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConTitlebar" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="org.owasp.encoder.Encode" %>
<% java.util.Properties oscarVariables = CarlosProperties.getInstance(); %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.consult" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.consult");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%
    ResourceBundle oscarR = ResourceBundle.getBundle("oscarResources", request.getLocale());

    String transactionType = new String(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.AddDepartment.addOperation"));
    String id = null;
    int whichType = 1;
    if (request.getAttribute("upd") != null) {
        transactionType = new String(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.AddDepartment.updateOperation"));
        whichType = 2;
        id = (String) request.getAttribute("id");
    }
%>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><%=transactionType%></title>
    </head>

    <body>
    <div class="container-fluid">
        <div class="page-header-bar">
            <h5 class="page-header-title"><%=transactionType%></h5>
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
                <%
                    String added = (String) request.getAttribute("Added");
                    if (added != null) {
                %>
                <div class="alert alert-success">
                    <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddDepartment.msgDepartmentAdded">
                        <fmt:param value="<%=added%>" />
                    </fmt:message>
                </div>
                <% } %>

                <form action="${pageContext.request.contextPath}/oscarEncounter/AddDepartment.do" method="post">
                    <input type="hidden" name="id" id="id" value="<%= id != null ? id : "" %>"/>
                    <div class="row mb-3">
                        <div class="col-md-5">
                            <label for="name" class="form-label">Name</label>
                            <input type="text" name="name" id="name" class="form-control" value="<e:forHtmlAttribute value='${name}'/>"/>
                        </div>
                    </div>
                    <div class="row mb-3">
                        <div class="col-md-6">
                            <label for="annotation" class="form-label">Annotation</label>
                            <textarea name="annotation" id="annotation" class="form-control" rows="3"><e:forHtmlContent value='${annotation}'/></textarea>
                        </div>
                    </div>
                    <input type="hidden" name="whichType" value="<%=whichType%>"/>
                    <input type="submit" class="btn btn-primary" name="transType" value="<%=transactionType%>"/>
                </form>
            </div>
        </div>
    </div>
    </body>
</html>
