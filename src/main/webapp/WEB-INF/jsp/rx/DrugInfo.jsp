<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%--
    Displays configured DrugRef product details and local name-search results without an external redirect.
    RxDrugInfo2Action requires prescription read access and supplies the view attributes.
    Request selectors: DIN identifies a prescribed product; BN is a chooser product key; GN is a name search.
    Exact identifiers take precedence. Missing information and service errors remain on this page.
    @since 2026-10-05
--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="carlos" uri="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<c:url var="drugInfoAction" value="/rx/drugInfo"/>
<c:url var="bootstrapCss" value="/library/bootstrap/5.3.8/css/bootstrap.min.css"/>
<!DOCTYPE html>
<html lang="<carlos:encode value='${pageContext.response.locale.language}' context='htmlAttribute'/>">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><fmt:message key="RxDrugInfo.title"/></title>
    <link rel="stylesheet" href="<carlos:encode value='${bootstrapCss}' context='htmlAttribute'/>">
</head>
<body>
<main id="drug-info" class="container py-4">
    <h1 class="h3 mb-3"><fmt:message key="RxDrugInfo.title"/></h1>
    <form method="get" action="<carlos:encode value='${drugInfoAction}' context='htmlAttribute'/>" class="mb-4">
        <label for="drug-info-name" class="form-label"><fmt:message key="RxDrugInfo.drugName"/></label>
        <div class="input-group">
            <input id="drug-info-name" name="GN" type="search" class="form-control"
                   value="<carlos:encode value='${drugInfoSearchTerm}' context='htmlAttribute'/>">
            <button type="submit" class="btn btn-primary"><fmt:message key="RxDrugInfo.search"/></button>
        </div>
    </form>
    <c:choose>
        <c:when test="${drugInfoUnavailable}">
            <p role="status" class="alert alert-warning"><fmt:message key="RxDrugInfo.unavailable"/></p>
        </c:when>
        <c:when test="${not empty drugInfoMonograph}">
            <section id="drug-reference-details">
                <h2 class="h4">
                    <c:choose>
                        <c:when test="${not empty drugInfoMonograph.product}"><carlos:encode value="${drugInfoMonograph.product}" context="html"/></c:when>
                        <c:otherwise><carlos:encode value="${drugInfoMonograph.name}" context="html"/></c:otherwise>
                    </c:choose>
                </h2>
                <dl class="row">
                    <c:if test="${not empty drugInfoMonograph.name}">
                        <dt class="col-sm-4"><fmt:message key="RxDrugInfo.drugName"/></dt>
                        <dd class="col-sm-8"><carlos:encode value="${drugInfoMonograph.name}" context="html"/></dd>
                    </c:if>
                    <c:if test="${not empty drugInfoMonograph.regionalIdentifier}">
                        <dt class="col-sm-4">DIN</dt>
                        <dd id="drug-info-din" class="col-sm-8"><carlos:encode value="${drugInfoMonograph.regionalIdentifier}" context="html"/></dd>
                    </c:if>
                    <c:if test="${not empty drugInfoMonograph.atc}">
                        <dt class="col-sm-4">ATC</dt>
                        <dd class="col-sm-8"><carlos:encode value="${drugInfoMonograph.atc}" context="html"/></dd>
                    </c:if>
                    <c:if test="${not empty drugInfoMonograph.drugForm}">
                        <dt class="col-sm-4"><fmt:message key="RxDrugInfo.form"/></dt>
                        <dd class="col-sm-8"><carlos:encode value="${drugInfoMonograph.drugForm}" context="html"/></dd>
                    </c:if>
                    <c:if test="${not empty drugInfoMonograph.route}">
                        <dt class="col-sm-4"><fmt:message key="RxDrugInfo.route"/></dt>
                        <dd class="col-sm-8"><ul class="list-unstyled mb-0"><c:forEach var="route" items="${drugInfoMonograph.route}">
                            <li><carlos:encode value="${route}" context="html"/></li>
                        </c:forEach></ul></dd>
                    </c:if>
                </dl>
                <c:if test="${not empty drugInfoMonograph.drugComponentList}">
                    <h3 class="h5"><fmt:message key="RxDrugInfo.ingredients"/></h3>
                    <table class="table">
                        <thead><tr>
                            <th scope="col"><fmt:message key="RxDrugInfo.drugName"/></th>
                            <th scope="col"><fmt:message key="RxDrugInfo.strength"/></th>
                            <th scope="col"><fmt:message key="RxDrugInfo.unit"/></th>
                        </tr></thead>
                        <tbody><c:forEach var="component" items="${drugInfoMonograph.drugComponentList}">
                            <tr>
                                <td><carlos:encode value="${component.name}" context="html"/></td>
                                <td><carlos:encode value="${component.strength}" context="html"/></td>
                                <td><carlos:encode value="${component.unit}" context="html"/></td>
                            </tr>
                        </c:forEach></tbody>
                    </table>
                </c:if>
            </section>
        </c:when>
        <c:when test="${not empty drugInfoMatches}">
            <h2 class="h4"><fmt:message key="RxDrugInfo.matches"/></h2>
            <ul id="drug-info-matches" class="list-group">
                <c:forEach var="drug" items="${drugInfoMatches}">
                    <c:url var="drugInfoLink" value="/rx/drugInfo"><c:param name="BN" value="${drug.pKey}"/></c:url>
                    <li class="list-group-item"><a href="<carlos:encode value='${drugInfoLink}' context='htmlAttribute'/>"><carlos:encode value="${drug.name}" context="html"/></a></li>
                </c:forEach>
            </ul>
        </c:when>
        <c:when test="${drugInfoRequested}">
            <p role="status"><fmt:message key="RxDrugInfo.noMatches"/></p>
        </c:when>
        <c:otherwise>
            <p><fmt:message key="RxDrugInfo.chooseDrug"/></p>
        </c:otherwise>
    </c:choose>
    <p class="text-secondary mt-4"><fmt:message key="RxDrugInfo.source"/></p>
</main>
</body>
</html>
