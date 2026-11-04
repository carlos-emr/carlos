<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<fmt:setBundle basename="oscarResources"/>
<fmt:message key="provider.setHl7LabResultPrefs.error" var="preferenceSaveError"/>
<fmt:message key="provider.setHl7LabResultPrefs.wait" var="preferenceWait"/>

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
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>
<jsp:include page="/WEB-INF/jsp/includes/spinner.jspf" flush="true"/>

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
                <div class="form-text text-muted"><fmt:message key="provider.setHl7LabResultPrefs.defaultNo"/></div>
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
    async function updatePreference(methodName, key, value) {
        const token = document.querySelector('input[name="CSRF-TOKEN"]');
        if (!token || !token.value) {
            jQuery('#' + key).prop('checked', !value);
            alert('${carlos:forJavaScript(preferenceWait)}');
            return;
        }
        jQuery('#' + key).prop('disabled', true);
        ShowSpin(true);
        try {
            // fetch is not hijacked by CSRFGuard's XHR hook: send the token exactly once.
            const response = await fetch('${pageContext.request.contextPath}/setProviderStaleDate?method=' + methodName, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': token.value},
                body: new URLSearchParams({value: String(value)}).toString()
            });
            if (!response.ok) throw new Error('Preference save was refused');
            const result = await response.json();
            if (typeof result.status !== 'boolean') throw new Error('Invalid preference response');
            jQuery('#' + key).prop('checked', result.status);
            const msg = jQuery('#successMessage');
            msg.removeClass('d-none');
            setTimeout(() => msg.addClass('d-none'), 3000);
        } catch (error) {
            alert('${carlos:forJavaScript(preferenceSaveError)}');
            jQuery('#' + key).prop('checked', !value);
        } finally {
            HideSpin();
            jQuery('#' + key).prop('disabled', false);
        }
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
