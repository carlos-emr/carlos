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

<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ page import="java.util.ResourceBundle" %>
<%
    if (session.getValue("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.htm");

    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());
%>

<!DOCTYPE html>
<html lang="en">
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setNoteStaleDate.title"/></title>
    </head>

    <body>
    <div class="container">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <i class="fas fa-clock page-header-icon"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setNoteStaleDate.msgProviderStaleDate"/>
            </h4>
        </div>

        <% if (request.getAttribute("status") == null) { %>
        <form action="${pageContext.request.contextPath}/setProviderStaleDate.do" method="post" class="mt-3">
            <input type="hidden" id="method" name="method" value="save">
            <input type="hidden" name="dateProperty.name" value="staleNoteDate"/>
            <input type="hidden" name="dateProperty.providerNo" value="${providerNo}"/>

            <p class="text-muted"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setNoteStaleDate.msgEdit"/></p>
            <div class="mb-3">
                <select name="dateProperty.value" id="dateProperty.value" class="form-select form-select-sm" style="max-width:200px;">
                    <c:forEach var="opt" items="${staleDateOptions}">
                        <option value="${opt.value}" <c:if test="${opt.value == dateProperty.value}">selected</c:if>>${opt.label}</option>
                    </c:forEach>
                </select>
            </div>

            <input type="hidden" name="singleViewProperty.name" value="staleFormat"/>
            <input type="hidden" name="singleViewProperty.providerNo" value="${providerNo}"/>

            <div class="mb-3">
                <label class="form-label fw-bold">Use Single Line View:</label>
                <select name="singleViewProperty.value" id="singleViewProperty.value" class="form-select form-select-sm" style="max-width:200px;">
                    <c:forEach var="opt" items="${viewOptions}">
                        <option value="${opt.value}" <c:if test="${opt.value == singleViewProperty.value}">selected</c:if>>${opt.label}</option>
                    </c:forEach>
                </select>
            </div>

            <div class="d-flex gap-2">
                <input type="submit" class="btn btn-primary btn-sm"
                       value="<%=bundle.getString("provider.setNoteStaleDate.btnSubmit")%>"/>
                <input type="submit" class="btn btn-outline-secondary btn-sm"
                       onclick="$('#method').val('remove');"
                       value="<%=bundle.getString("provider.setNoteStaleDate.btnReset")%>"/>
            </div>
        </form>

        <% } else { %>
        <div class="alert alert-success mt-3"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setNoteStaleDate.msgSuccess"/></div>
        <% } %>

    </div>
    </body>
</html>
