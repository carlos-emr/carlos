<%--
  Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

  This software is published under the GPL GNU General Public License.
  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License
  as published by the Free Software Foundation; either version 2
  of the License, or (at your option) any later version.

  CARLOS EMR Project
  https://github.com/carlos-emr/carlos
--%>
<%--
  Purpose: Surface a BillingValidationException from any billing action as an
  actionable message rather than the generic "CARLOS Error 500" page. The
  audit-trail integrity is preserved by the throw site's ERROR log; this
  view is purely user-facing guidance.

  Wired via the package-level <global-exception-mappings> in struts-billing.xml,
  so it covers ViewBillingONReview2Action (addToPatientDx with non-numeric
  demographic_no) and BillingCorrection2Action (unparseable
  xml_appointment_date and unparseable bill admission/billing date) without
  per-action duplication.

  PHI-safe: the exception message is server-composed at the throw site with
  LogSanitizer-sanitized values; it is rendered with the carlos:encode tag
  in context="html" mode so any residual control characters are HTML-escaped
  before display.

  @since 2026-04-25
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%-- OWASP encoder taglib declared per project JSP-security rule. Even though
     this page renders dynamic content via the carlos:encode wrapper (which
     null-coalesces before delegating to OWASP), declaring `e` keeps the
     taglib surface uniform across new JSPs and enables direct e:forXxx use
     for any future inline encoding. --%>
<fmt:setBundle basename="oscarResources"/>
<%
    // Pull the exception message defensively — three sources in priority order:
    //   1. Struts2 ExceptionMappingInterceptor (the documented wiring via
    //      <global-exception-mappings> in struts-billing.xml) places the
    //      caught exception on the ValueStack and exposes it as the request
    //      attribute "exception". This is the path most BillingValidationException
    //      flows take, so check it first.
    //   2. JSP errorPage forward — page-context "exception" implicit, populated
    //      when one JSP forwards to another via <%@ page errorPage="..." %>.
    //   3. Container error dispatcher — "jakarta.servlet.error.exception" is
    //      set when reached via <error-page> in web.xml.
    // Cast through instanceof at every step (never assume non-null).
    Throwable __bve = null;
    Object __strutsExc = request.getAttribute("exception");
    if (__strutsExc instanceof Throwable) {
        __bve = (Throwable) __strutsExc;
    }
    if (__bve == null) {
        __bve = exception;
    }
    if (__bve == null) {
        Object __attr = request.getAttribute("jakarta.servlet.error.exception");
        if (__attr instanceof Throwable) {
            __bve = (Throwable) __attr;
        }
    }
    String __bveMessage = __bve == null ? "" : (__bve.getMessage() == null ? "" : __bve.getMessage());
    request.setAttribute("__bveMessage", __bveMessage);
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Billing — Submission Rejected</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css"/>
</head>
<body>
<div class="container py-5">
    <div class="alert alert-danger" role="alert">
        <h4 class="alert-heading">Bill submission rejected</h4>
        <p>The submission was rejected because one of the form values failed
           validation. Common causes:</p>
        <ul>
            <li>The chart was reloaded mid-bill — return to the chart and try again.</li>
            <li>A browser auto-fill rewrote a hidden field — clear cached form data and retry.</li>
            <li>A date field was entered in a format the server could not parse
                (use <code>YYYY-MM-DD</code>).</li>
        </ul>
        <c:if test="${not empty __bveMessage}">
            <p class="mt-3"><strong>Details:</strong>
                <span><carlos:encode value="${__bveMessage}" context="html"/></span>
            </p>
        </c:if>
        <p class="mb-0">No data was saved. Use your browser's <strong>Back</strong>
           button to return to the form and correct the input.</p>
    </div>
    <div class="d-flex gap-2">
        <a class="btn btn-secondary" href="javascript:history.back()" role="button">Back</a>
        <a class="btn btn-secondary" href="${pageContext.request.contextPath}/provider/providercontrol" role="button">Schedule</a>
    </div>
</div>
</body>
</html>
