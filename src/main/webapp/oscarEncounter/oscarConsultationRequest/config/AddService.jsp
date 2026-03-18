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

<%@ page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConTitlebar" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
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
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddService.title"/></title>
        <script>
            function checkServiceName() {
                var service = document.forms[0].service;
                if (service.value.trim() == "") {
                    alert("<fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddService.serviceNameEmpty"/>");
                    service.focus();
                    return false;
                } else return true;
            }
        </script>
    </head>

    <body>
    <div class="container-fluid">
        <div class="page-header-bar">
            <h5 class="page-header-title">
                <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddService.title"/>
            </h5>
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
                    String added = (String) request.getAttribute("SERVADD");
                    if (added != null) {
                %>
                <div class="alert alert-success">
                    <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddService.msgServiceAdded">
                        <fmt:param value="<%=added%>" />
                    </fmt:message>
                </div>
                <% } %>

                <form action="${pageContext.request.contextPath}/oscarEncounter/AddService.do" method="post" onsubmit="return checkServiceName();">
                    <div class="row mb-3">
                        <div class="col-md-5">
                            <label for="service" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddService.service"/></label>
                            <input type="text" name="service" id="service" class="form-control"/>
                        </div>
                    </div>
                    <input type="submit" class="btn btn-primary"
                           value="<fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddService.btnAddService"/>"/>
                </form>
            </div>
        </div>
    </div>
    </body>
</html>
