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
<!DOCTYPE html>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<fmt:setBundle basename="oscarResources"/>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <c:set var="ctx" value="${pageContext.request.contextPath}" scope="page"/>
    <title><fmt:message key="email.myFooter.title"/></title>
    <meta name="viewport" content="width=device-width,initial-scale=1.0">
    <link rel="stylesheet" href="${ctx}/library/bootstrap/5.3.8/css/bootstrap.min.css"/>
</head>
<body>
<div class="container my-4" style="max-width: 50rem;">
    <h3><fmt:message key="email.myFooter.title"/></h3>
    <p><fmt:message key="email.myFooter.intro"/></p>
    <c:if test="${param.saved eq 'true'}">
        <div class="alert alert-success" role="status"><fmt:message key="email.myFooter.saved"/></div>
    </c:if>
    <c:if test="${myFooterTooLong}">
        <div class="alert alert-danger" role="alert"><fmt:message key="email.myFooter.tooLong"/></div>
    </c:if>
    <c:if test="${myFooterSaveConflict}">
        <div class="alert alert-danger" role="alert"><fmt:message key="email.myFooter.saveConflict"/></div>
    </c:if>
    <c:if test="${myFooterStale}">
        <div class="alert alert-warning" role="alert"><fmt:message key="email.myFooter.stale"/></div>
        <details class="mb-3">
            <summary><fmt:message key="email.myFooter.unsavedText"/></summary>
            <pre style="white-space: pre-wrap;"><c:out value="${myFooterAttempted}"/></pre>
        </details>
    </c:if>
    <form action="${ctx}/email/saveMyEmailFooter" method="post" id="myFooterForm">
        <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>"/>
        <input type="hidden" name="personalFooterRevision" value="${carlos:forHtmlAttribute(personalFooterRevision)}"/>
        <div class="d-flex justify-content-between align-items-center mb-2">
            <span id="myFooterLabel"><fmt:message key="email.myFooter.label"/></span>
            <%-- The window's Save footer sends this form as a save: no second Save button. --%>
            <button type="button" class="btn btn-outline-primary btn-sm" data-bs-toggle="modal"
                    data-bs-target="#footerEditorModal" data-footer-editor-target="myFooter"
                    data-footer-editor-preview="myFooterPreview" aria-describedby="myFooterHelp"
                    data-footer-editor-save="myFooterForm" data-footer-editor-save-name="footerAction"
                    data-footer-editor-save-value="save">
                <fmt:message key="email.footerEditor.open"/></button>
        </div>
        <input type="hidden" name="myFooter" id="myFooter" value="${carlos:forHtmlAttribute(myFooter)}"/>
        <div id="myFooterPreview" class="border bg-white p-2 footer-editor-mail" style="white-space: pre-wrap;"
             aria-labelledby="myFooterLabel" data-empty-text="<fmt:message key='email.myFooter.none'/>"><%= io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlContent(io.github.carlos_emr.carlos.email.core.EmailFooterHtml.toPlainText((String) request.getAttribute("myFooter"))) %></div>
        <p class="form-text" id="myFooterHelp"><fmt:message key="email.myFooter.emptyHelp"/></p>
        <p class="form-text"><fmt:message key="email.compose.footer.help"/></p>
        <button type="submit" name="footerAction" value="clear" class="btn btn-outline-secondary"><fmt:message key="email.myFooter.clear"/></button>
    </form>
    <h5 class="mt-4"><fmt:message key="email.compose.footer.clinicHeading"/></h5>
    <c:choose>
        <c:when test="${clinicFooterOff}">
            <p class="small text-muted" id="clinicFooterOffNote"><fmt:message key="email.compose.footer.clinicOff"/></p>
        </c:when>
        <c:otherwise>
    <p class="small"><fmt:message key="email.compose.footer.clinicReadonly"/></p>
    <c:if test="${not empty clinicFooterLogoPreview}">
        <img src="${carlos:forHtmlAttribute(clinicFooterLogoPreview)}" alt="" style="max-width: 100%; height: auto;"/>
    </c:if>
    <div class="border bg-white p-2 footer-editor-mail" style="white-space: pre-wrap;"
         data-footer-html="${carlos:forHtmlAttribute(clinicFooter)}"><%= io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlContent(io.github.carlos_emr.carlos.email.core.EmailFooterHtml.toPlainText((String) request.getAttribute("clinicFooter"))) %></div>
        </c:otherwise>
    </c:choose>
    <c:if test="${clinicFooterMissing}">
        <p class="alert alert-warning"><fmt:message key="email.compose.footer.clinicRequired"/></p>
    </c:if>
</div>
<script src="${ctx}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
<c:set var="footerEditorPersonalOnly" value="${true}"/>
<c:set var="footerEditorScopeKey" value="email.footerEditor.scopeMine"/>
<c:set var="footerEditorApplyKey" value="email.footerEditor.save"/>
<%@ include file="/WEB-INF/jsp/email/footerEditorModal.jspf" %>
</body>
</html>
