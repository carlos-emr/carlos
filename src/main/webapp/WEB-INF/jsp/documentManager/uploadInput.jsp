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

    This page renders inline rather than forwarding. When it was written, two
    packaged-install failures forced that shape: a JSP-to-JSP <jsp:forward>
    returned 200 with an EMPTY body, and setStatus(4xx) plus a body came back
    as a raw 500. Both are since fixed at the root -- Tomcat 11's
    suspendWrappedResponseAfterForward default suspended the response when a
    forward returned, stranding the forwarded body in javamelody's writer
    buffer and breaking ResponseSanitizationFilter's replay; the context
    descriptors now pin that attribute false and the filter appends instead of
    500ing (see ResponseSanitizationFilter's class javadoc). The inline render
    is kept deliberately: it is simpler and depends on neither fix.

    One constraint that remains real: do not forward to documentReport.jsp the
    way failAdd does. The multipart parse FAILED, so every POST body parameter
    that page is built from (function, functionid, doctype) is gone, and it
    renders blank.
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
