<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    The "input" result of documentManager/addEditDocument: where the workflow
    interceptor lands when the multipart layer rejects the upload before the
    action runs. Two client shapes need different answers:

      - the legacy inbox uploader (method=html5MultiUpload, raw XHR via
        noswfupload.js): that client treats any 2xx as success, so a 200 would
        silently report a REJECTED upload as uploaded. It reads the oscar_error
        header on >= 400, so answer with sendError. This branch works because
        "method" travels in the QUERY STRING, which survives a failed multipart
        parse.
      - every browser form (eDocs add, Add Link, edit): render the rejection
        inline, below.

    TWO THINGS THIS FILE MUST NOT DO, both established by bisecting on the
    packaged install:

    1. It must not <jsp:forward>. pageContext.forward() resets the response
       buffer, and ResponseSanitizationFilter -- which wraps the response to
       strip stack traces -- does not carry the forwarded body through: the
       client receives 200 with an EMPTY body. Writing the same markup inline
       from this page works. (WEB-INF/jsp/common/uploadRejected.jsp is the same
       content and is fine as a Struts dispatcher result, which forwards before
       any page has started writing; it is only the JSP-to-JSP forward that is
       lost.)
    2. It must not setStatus(4xx) and write a body. The same filter cannot
       replay that combination and the request comes back as a raw 500 -- the
       exact failure this result exists to prevent. sendError is unaffected.

    It must also not forward to documentReport.jsp the way failAdd does: the
    multipart parse FAILED, so every POST body parameter that page is built from
    (function, functionid, doctype) is gone, and it renders blank.
--%><%@ page contentType="text/html;charset=UTF-8" session="false"
%><%@ page import="java.util.Collection" %><%@ page import="java.util.ResourceBundle"
%><%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %><%
    Collection<String> uploadErrors = (Collection<String>) request.getAttribute("actionErrors");
    String reported = (uploadErrors == null || uploadErrors.isEmpty())
            ? "" : String.join(" ", uploadErrors);
    // Match "(0 bytes)", the stable tail of Struts' zero-length message, NOT the word "empty":
    // that message interpolates the SUBMITTED FILENAME, so a file called "empty-form.pdf"
    // refused for its size would be reported as zero-length. Anything unrecognised falls back
    // to the generic key, which is never actively wrong.
    String errorKey = reported.contains("(0 bytes)")
            ? "dms.addDocument.errorZeroSize" : "dms.error.uploadError";
    String message = reported.isEmpty()
            ? ResourceBundle.getBundle("oscarResources").getString(errorKey) : reported;

    if ("html5MultiUpload".equals(request.getParameter("method"))) {
        // Servlet headers are ISO-8859-1, so a localized message with non-Latin-1 characters
        // would reach the browser mangled. Stripping to printable ASCII also removes CR/LF, so
        // a filename embedded in the message cannot inject a header.
        response.setHeader("oscar_error", message.replaceAll("[^\\x20-\\x7E]", "?"));
        response.sendError(jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST, message);
        return;
    }
%><fmt:setBundle basename="oscarResources"/><!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <title><fmt:message key="global.error"/></title>
</head>
<body class="p-4">
<div class="alert alert-danger" role="alert">
    <h5 class="alert-heading"><fmt:message key="dms.error.uploadError"/></h5>
    <%-- The multipart layer's own wording; it can embed the submitted filename, so encode it. --%>
    <p class="mb-0"><%= io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlContent(message) %></p>
</div>
<button type="button" class="btn btn-secondary" onclick="history.back();">
    <fmt:message key="global.btnBack"/>
</button>
</body>
</html>
