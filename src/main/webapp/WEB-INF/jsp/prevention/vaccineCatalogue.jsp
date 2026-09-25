<%--

    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos

--%>
<%--
    National Vaccine Catalogue (NVC) administration page.

    Purpose: shows which NVC catalogue is installed (version, install time, generic and
    tradename counts, source URL) and lets an administrator with _admin write rights
    download and install the current NVC v2 bundle.

    Request attributes (set by ViewVaccineCatalogue2Action, which enforces _admin read):
      catalogueSourceUrl, catalogueLastUpdated, catalogueVersion,
      catalogueGenericCount, catalogueTradenameCount, canUpdate, updateResult

    The Update button is a plain form POST to prevention/UpdateVaccineCatalogue (CSRFGuard
    injects the token into the form); that action re-checks _admin write, and redirects back
    here with result=updated|failed|unavailable. The button is advisory only.

    @since 2026-09-24
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.response.locale.language)}">
<head>
    <meta charset="UTF-8">
    <link rel="icon" href="${carlos:forHtmlAttribute(ctx)}/images/favicon.ico"/>
    <title><fmt:message key="admin.vaccineCatalogue.title"/></title>
    <link href="${carlos:forHtmlAttribute(ctx)}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
</head>
<body class="p-3">
<div class="container-fluid" style="max-width: 760px;">
    <h4 class="mb-3"><fmt:message key="admin.vaccineCatalogue.title"/></h4>
    <p class="text-body-secondary"><fmt:message key="admin.vaccineCatalogue.intro"/></p>

    <c:choose>
        <c:when test="${updateResult == 'updated'}">
            <div class="alert alert-success" role="status" id="catalogueResult" data-result="updated">
                <fmt:message key="admin.vaccineCatalogue.result.updated"/></div>
        </c:when>
        <c:when test="${updateResult == 'unavailable'}">
            <div class="alert alert-warning" role="alert" id="catalogueResult" data-result="unavailable">
                <fmt:message key="admin.vaccineCatalogue.result.unavailable"/></div>
        </c:when>
        <c:when test="${updateResult == 'failed'}">
            <div class="alert alert-danger" role="alert" id="catalogueResult" data-result="failed">
                <fmt:message key="admin.vaccineCatalogue.result.failed"/></div>
        </c:when>
    </c:choose>

    <table class="table table-sm table-bordered" id="catalogueStatus">
        <tbody>
        <tr>
            <th scope="row" class="w-50"><fmt:message key="admin.vaccineCatalogue.lastUpdated"/></th>
            <td id="catalogueLastUpdated">
                <c:choose>
                    <c:when test="${empty catalogueLastUpdated}"><fmt:message key="admin.vaccineCatalogue.never"/></c:when>
                    <c:otherwise><carlos:encode value="${catalogueLastUpdated}"/></c:otherwise>
                </c:choose>
            </td>
        </tr>
        <tr>
            <th scope="row"><fmt:message key="admin.vaccineCatalogue.version"/></th>
            <td id="catalogueVersion"><carlos:encode value="${catalogueVersion}"/></td>
        </tr>
        <tr>
            <th scope="row"><fmt:message key="admin.vaccineCatalogue.generics"/></th>
            <td id="catalogueGenericCount"><carlos:encode value="${catalogueGenericCount}"/></td>
        </tr>
        <tr>
            <th scope="row"><fmt:message key="admin.vaccineCatalogue.tradenames"/></th>
            <td id="catalogueTradenameCount"><carlos:encode value="${catalogueTradenameCount}"/></td>
        </tr>
        <tr>
            <th scope="row"><fmt:message key="admin.vaccineCatalogue.source"/></th>
            <td id="catalogueSource" class="text-break"><carlos:encode value="${catalogueSourceUrl}"/></td>
        </tr>
        </tbody>
    </table>

    <c:choose>
        <c:when test="${canUpdate}">
            <form action="${carlos:forHtmlAttribute(ctx)}/prevention/UpdateVaccineCatalogue" method="post" id="catalogueUpdateForm">
                <button type="submit" class="btn btn-primary" id="catalogueUpdateButton">
                    <fmt:message key="admin.vaccineCatalogue.btnUpdate"/></button>
                <span class="ms-2 text-body-secondary d-none" id="catalogueUpdating" role="status">
                    <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                    <fmt:message key="admin.vaccineCatalogue.updating"/></span>
            </form>
            <script>
                // The download and install take up to a minute or two; stop double submits and
                // tell the administrator the page is working rather than hung.
                document.getElementById('catalogueUpdateForm').addEventListener('submit', function () {
                    document.getElementById('catalogueUpdateButton').disabled = true;
                    document.getElementById('catalogueUpdating').classList.remove('d-none');
                });
            </script>
        </c:when>
        <c:otherwise>
            <p class="text-body-secondary" id="catalogueReadOnly"><fmt:message key="admin.vaccineCatalogue.readOnly"/></p>
        </c:otherwise>
    </c:choose>
</div>
</body>
</html>
