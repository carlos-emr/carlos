<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<fmt:setBundle basename="oscarResources"/>

<c:set var="roleName" value="${sessionScope.userrole},${sessionScope.user}" />

<security:oscarSec roleName="${roleName}" objectName="_lab" rights="w" reverse="true">
    <c:redirect url="../securityError">
        <c:param name="type" value="_lab" />
    </c:redirect>
</security:oscarSec>

<!DOCTYPE html>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="provider.setHl7LabResultPrefs.title"/></title>

    <link href="${pageContext.servletContext.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" media="screen">
    <script src="${pageContext.servletContext.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="${pageContext.servletContext.contextPath}/library/jquery/jquery-compat.js"></script>
    <script src="${pageContext.servletContext.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
</head>
<body>
<jsp:include page="../images/spinner.jsp" flush="true"/>

<div class="container py-5">
    <div class="card shadow-sm">
        <div class="card-header bg-primary text-white">
            <h5 class="mb-0"><fmt:message key="provider.setHl7LabResultPrefs.header"/></h5>
        </div>
        <div class="card-body">

            <div class="form-check form-switch mb-3">
                <input class="form-check-input" type="checkbox" id="offerFileForOthers"
                       <c:if test="${offerFileForOthers}">checked</c:if>>
                <label class="form-check-label" for="offerFileForOthers">
                    <fmt:message key="provider.setHl7LabResultPrefs.offerFileForOthers"/>
                </label>
                <div class="form-text text-muted"><fmt:message key="provider.setHl7LabResultPrefs.defaultYes"/></div>
            </div>

            <div class="form-check form-switch mb-3">
                <input class="form-check-input" type="checkbox" id="allowOthersFileForYou"
                       <c:if test="${allowOthersFileForYou}">checked</c:if>>
                <label class="form-check-label" for="allowOthersFileForYou">
                    <fmt:message key="provider.setHl7LabResultPrefs.allowOthersFileForYou"/>
                </label>
                <div class="form-text text-muted"><fmt:message key="provider.setHl7LabResultPrefs.defaultNo"/></div>
            </div>

            <div id="successMessage" class="alert alert-success d-none" role="alert">
                <fmt:message key="provider.setHl7LabResultPrefs.success"/>
            </div>

        </div>
    </div>
</div>

<script>
    function updatePreference(methodName, key, value) {
        ShowSpin(true);
        jQuery.ajax({
            url: '${pageContext.request.contextPath}/setProviderStaleDate?method=' + methodName,
            method: 'POST',
            data: {
                key: key,
                value: value
            },
            success: function (response) {
                const status = response.status;
                jQuery('#' + key).prop('checked', status);
                const msg = jQuery('#successMessage');
                msg.removeClass('d-none');
                setTimeout(() => msg.addClass('d-none'), 3000);
            },
            error: function (xhr, status, error) {
                alert("Error updating preference: " + error);
                jQuery('#' + key).prop('checked', !value);
            },
            complete: function () {
                HideSpin();
            }
        });
    }

    jQuery(function () {
        jQuery('#offerFileForOthers').on('change', function () {
            updatePreference('setOfferFileForOthersPref', 'offerFileForOthers', this.checked);
        });

        jQuery('#allowOthersFileForYou').on('change', function () {
            updatePreference('setAllowOthersFileForYouPref', 'allowOthersFileForYou', this.checked);
        });
    });
</script>

</body>
</html>
