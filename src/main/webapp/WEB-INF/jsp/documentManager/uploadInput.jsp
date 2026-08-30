<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    The "input" result of documentManager/addEditDocument: where the workflow
    interceptor lands when the multipart layer rejects the upload before the
    action runs. Two client shapes need different answers:

      - the legacy inbox uploader (method=html5MultiUpload, raw XHR via
        noswfupload.js): that client treats any 2xx as success, so a 200 HTML
        response would silently report a REJECTED upload as uploaded. It reads
        the oscar_error header on >= 400, so answer 400 with the message there.
        This branch works because "method" travels in the QUERY STRING, which
        survives a failed multipart parse.
      - every browser form (eDocs add, Add Link, edit): render the shared
        rejection page.

    WHY NOT forward to documentReport.jsp, which is what failAdd does. Because
    the multipart parse FAILED, so none of the POST body parameters exist any
    more -- MultiPartRequestWrapper.getParameter falls back to the wrapped
    request, which only carries the query string. documentReport.jsp is built
    from function/functionid/doctype, all of which were body fields, so the
    forward renders a BLANK PAGE. An earlier version of this file did exactly
    that: it set docerrors and forwarded, which looked right and produced
    nothing at all -- the same silent-drop failure this result exists to fix,
    reached by a different route.

    The shared page depends on no request state, always renders, carries 400 so
    scripted clients see a failure, and reports what the multipart layer
    actually said rather than guessing "empty file".
--%><%@ page contentType="text/html;charset=UTF-8" session="false"
%><%@ page import="java.util.Collection" %><%@ page import="java.util.ResourceBundle" %><%
    if ("html5MultiUpload".equals(request.getParameter("method"))) {
        Collection<String> uploadErrors = (Collection<String>) request.getAttribute("actionErrors");
        String reported = (uploadErrors == null || uploadErrors.isEmpty())
                ? "" : String.join(" ", uploadErrors);
        // Match "(0 bytes)", the stable tail of Struts' zero-length message, NOT the word
        // "empty": that message interpolates the SUBMITTED FILENAME, so a file called
        // "empty-form.pdf" refused for its size would be reported as zero-length. Anything
        // unrecognised falls back to the generic key, which is never actively wrong.
        String errorKey = reported.contains("(0 bytes)")
                ? "dms.addDocument.errorZeroSize" : "dms.error.uploadError";
        String message = reported.isEmpty()
                ? ResourceBundle.getBundle("oscarResources").getString(errorKey) : reported;
        // Servlet headers are ISO-8859-1, so a localized message with non-Latin-1 characters
        // would reach the browser mangled. Stripping to printable ASCII also removes CR/LF, so
        // a filename embedded in the message cannot inject a header.
        response.setHeader("oscar_error", message.replaceAll("[^\\x20-\\x7E]", "?"));
        // sendError, NOT setStatus plus a body: ResponseSanitizationFilter captures the body to
        // strip stack traces and cannot replay a JSP-written 4xx, turning it into a 500 -- the
        // exact failure this result exists to prevent. sendError is unaffected, it is the same
        // mechanism sendHtml5UploadError already uses for this client, and this client reads the
        // oscar_error header rather than the body, so discarding the body costs nothing.
        response.sendError(jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST, message);
    } else {
%><jsp:forward page="/WEB-INF/jsp/common/uploadRejected.jsp"/><%
    }
%>
