<%--
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LocaleUtils" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="carlos" uri="carlos" %>
<fmt:setLocale value="<%= LocaleUtils.resolveBundleLocale(request) %>"/>
<fmt:setBundle basename="oscarResources"/>
<c:url var="managerUrl" value="/web/dashboard/admin/DashboardManager"/>
<!DOCTYPE html>
<html lang="${pageContext.response.locale.language}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><fmt:message key="dashboard.dashboardmanager.title"/></title>
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/library/bootstrap/5.3.8/css/bootstrap.min.css">
</head>
<body class="p-3">
<main>
    <h1><fmt:message key="dashboard.dashboardmanager.title"/></h1>
    <c:if test="${not empty dashboardManagerError}">
        <p role="alert" class="alert alert-danger"><fmt:message key="${dashboardManagerError}"/></p>
    </c:if>
    <table class="table" id="dashboards">
        <thead><tr>
            <th scope="col"><fmt:message key="dashboard.dashboardmanager.dashboard.name"/></th>
            <th scope="col"><fmt:message key="dashboard.dashboardmanager.dashboard.description"/></th>
            <th scope="col"><fmt:message key="dashboard.dashboardmanager.dashboard.active"/></th>
            <th scope="col"><fmt:message key="dashboard.dashboardmanager.dashboard.edit"/></th>
        </tr></thead>
        <tbody>
        <c:forEach var="dashboard" items="${dashboards}">
            <tr data-dashboard-id="${dashboard.id}">
                <th scope="row">${carlos:forHtmlContent(dashboard.name)}</th>
                <td>${carlos:forHtmlContent(dashboard.description)}</td>
                <td>
                    <c:choose>
                        <c:when test="${dashboard.active}"><fmt:message key="dashboard.dashboardmanager.dashboard.active"/></c:when>
                        <c:otherwise><fmt:message key="dashboard.dashboardmanager.inactive"/></c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <c:if test="${dashboardCanWrite and not dashboard.locked}">
                        <c:url var="editUrl" value="/web/dashboard/admin/DashboardManager"><c:param name="dashboardId" value="${dashboard.id}"/></c:url>
                        <a href="${carlos:forHtmlAttribute(editUrl)}"><fmt:message key="dashboard.dashboardmanager.dashboard.edit"/></a>
                        <form method="post" action="${carlos:forHtmlAttribute(managerUrl)}" class="d-inline">
                            <input type="hidden" name="operation" value="status">
                            <input type="hidden" name="dashboardId" value="${dashboard.id}">
                            <input type="hidden" name="active" value="${not dashboard.active}">
                            <button type="submit" class="btn btn-sm btn-secondary">
                                <c:choose>
                                    <c:when test="${dashboard.active}"><fmt:message key="dashboard.dashboardmanager.deactivate"/></c:when>
                                    <c:otherwise><fmt:message key="dashboard.dashboardmanager.activate"/></c:otherwise>
                                </c:choose>
                            </button>
                        </form>
                    </c:if>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
    <c:if test="${dashboardCanWrite and (empty editedDashboard or not editedDashboard.locked)}">
        <h2>
            <c:choose>
                <c:when test="${empty editedDashboard}"><fmt:message key="dashboard.dashboardmanager.dashboard.create"/></c:when>
                <c:otherwise><fmt:message key="dashboard.dashboardmanager.dashboard.edit"/></c:otherwise>
            </c:choose>
        </h2>
        <form method="post" action="${carlos:forHtmlAttribute(managerUrl)}" id="dashboard-editor">
            <input type="hidden" name="operation" value="save">
            <c:if test="${not empty editedDashboard}"><input type="hidden" name="dashboardId" value="${editedDashboard.id}"></c:if>
            <div class="mb-3">
                <label for="dashboard-name" class="form-label"><fmt:message key="dashboard.dashboardmanager.dashboard.name"/></label>
                <input id="dashboard-name" name="name" class="form-control" required maxlength="255"
                    value="${carlos:forHtmlAttribute(dashboardName != null ? dashboardName : editedDashboard.name)}">
            </div>
            <div class="mb-3">
                <label for="dashboard-description" class="form-label"><fmt:message key="dashboard.dashboardmanager.dashboard.description"/></label>
                <textarea id="dashboard-description" name="description" class="form-control" maxlength="255">${carlos:forHtmlContent(dashboardDescription != null ? dashboardDescription : editedDashboard.description)}</textarea>
            </div>
            <button type="submit" class="btn btn-primary"><fmt:message key="dashboard.dashboardmanager.dashboard.save"/></button>
            <a href="${carlos:forHtmlAttribute(managerUrl)}" class="btn btn-secondary"><fmt:message key="dashboard.dashboardmanager.dashboard.close"/></a>
        </form>
    </c:if>
</main>
</body>
</html>
