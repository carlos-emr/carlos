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
  Purpose: Surface a BillingFileWriteException (OHIP claim file generation
  failure) as an actionable operator-facing message rather than the generic
  CARLOS Error 500 page. Triggered by the package-level
  <global-exception-mappings> in struts-billing.xml.

  Failure scenarios that reach this page:
    - Disk full / permission denied on HOME_DIR
    - Path-traversal attempt rejected by PathValidationUtils
    - Mid-loop DAO failure during createBillingFileStr / dbQuery (the
      previously-swallowed catch sites). Some claim headers may already
      have been flipped to billed; an admin should reconcile against the
      OHIP claim record before retrying.

  PHI-safe: the exception message is composed at the throw site with the
  ohipFilename only (no patient identifiers). Rendered with the carlos:encode
  tag in context="html" mode for defense-in-depth.

  @since 2026-04-29
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%
    // Pull the exception message defensively — Struts2 ExceptionMappingInterceptor
    // places the caught exception on the ValueStack and exposes it as the
    // request attribute "exception". Fall back to the JSP errorPage and the
    // container error dispatcher attributes for completeness.
    Throwable __bfwe = null;
    Object __strutsExc = request.getAttribute("exception");
    if (__strutsExc instanceof Throwable) {
        __bfwe = (Throwable) __strutsExc;
    }
    if (__bfwe == null) {
        __bfwe = exception;
    }
    if (__bfwe == null) {
        Object __attr = request.getAttribute("jakarta.servlet.error.exception");
        if (__attr instanceof Throwable) {
            __bfwe = (Throwable) __attr;
        }
    }
    String __bfweMessage = __bfwe == null ? ""
            : (__bfwe.getMessage() == null ? "" : __bfwe.getMessage());
    request.setAttribute("__bfweMessage", __bfweMessage);

    // BillingDataLoadException carries structured Phase + context for
    // operator-facing rendering. Pull them out via reflection-free
    // type check so the same JSP serves BillingFileWriteException too.
    String __phase = "";
    java.util.Map<String, String> __ctx = java.util.Collections.emptyMap();
    if (__bfwe instanceof io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException) {
        io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException __dle =
                (io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException) __bfwe;
        __phase = __dle.phase().name();
        __ctx = __dle.context();
    }
    request.setAttribute("__phase", __phase);
    request.setAttribute("__ctx", __ctx);
%>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <meta charset="UTF-8">
    <title>Billing — OHIP Claim File Failed</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css"/>
</head>
<body>
<div class="container py-5">
    <div class="alert alert-danger" role="alert">
        <h4 class="alert-heading">OHIP claim file generation failed</h4>
        <p>The OHIP claim file could not be written or the extraction failed
           partway through. Common causes:</p>
        <ul>
            <li>Disk full or permission denied on the configured
                <code>HOME_DIR</code> billing directory.</li>
            <li>A previous file with the same name is locked by another process.</li>
            <li>An unexpected database error during claim header iteration —
                some headers may already have been flagged <code>billed</code>;
                reconcile against the OHIP claim record before retrying.</li>
        </ul>
        <c:if test="${not empty __bfweMessage}">
            <p class="mt-3"><strong>Details:</strong>
                <span><carlos:encode value="${__bfweMessage}" context="html"/></span>
            </p>
        </c:if>
        <c:if test="${not empty __phase}">
            <p class="mb-1"><strong>Phase:</strong>
                <span><carlos:encode value="${__phase}" context="html"/></span>
            </p>
        </c:if>
        <c:if test="${not empty __ctx}">
            <dl class="row mb-0">
                <c:forEach var="entry" items="${__ctx}">
                    <dt class="col-sm-3 text-end"><carlos:encode value="${entry.key}" context="html"/>:</dt>
                    <dd class="col-sm-9"><carlos:encode value="${entry.value}" context="html"/></dd>
                </c:forEach>
            </dl>
        </c:if>
        <p class="mb-0">Check the application log for the full stack trace, then
           contact your billing administrator. The claim batch was <strong>not</strong>
           submitted.</p>
    </div>
    <div class="d-flex gap-2">
        <a class="btn btn-secondary" href="javascript:history.back()" role="button">Back</a>
        <a class="btn btn-secondary" href="${pageContext.request.contextPath}/provider/providercontrol" role="button">Schedule</a>
    </div>
</div>
</body>
</html>
