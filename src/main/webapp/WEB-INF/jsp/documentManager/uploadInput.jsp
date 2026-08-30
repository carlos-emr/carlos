<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    The "input" result of documentManager/addEditDocument: where the workflow
    interceptor lands when the multipart layer rejects the upload before the
    action runs. One action serves two client shapes, which need opposite
    answers:

      - the eDocs add/edit FORM (full page): forward to the documents page the
        way failAdd does. That forward is only useful if it carries an error --
        "docerrors" is the ONLY error channel those pages have (addDocument.jsp
        keys both its alert list and its openDocPanel decision off it), and the
        action that normally sets it never ran on this path. Without it the
        clinician gets a clean documents list and no message at all, which is
        strictly worse than the raw 500 this result replaced: a rejected upload
        becomes indistinguishable from a page refresh.
      - the legacy inbox uploader (method=html5MultiUpload, raw XHR via
        noswfupload.js): that client treats any 2xx as success, so a 200 HTML
        forward would silently report a REJECTED upload as uploaded. It reads
        the oscar_error header on >= 400, so answer 400 with the message there.

    The multipart layer rejects for several distinct reasons -- an empty part,
    struts.multipart.maxStringLength (default 4096, not overridden here),
    maxFiles, the 50MB maxSize, and generic parse failures -- and it puts the
    real wording in the action's errors. Report that wording rather than
    asserting "empty file", which is a guess that is wrong for every other case.
    docerrors values must be resource-bundle KEYS (addDocument.jsp resolves them
    with fmt:message), so the forward path maps to the closest key; the XHR path
    carries no such indirection and gets the literal text.
--%><%@ page contentType="text/html;charset=UTF-8" session="false"
%><%@ page import="java.util.Collection" %><%@ page import="java.util.Hashtable"
%><%@ page import="java.util.ResourceBundle" %><%
    Collection<String> uploadErrors = (Collection<String>) request.getAttribute("actionErrors");
    String reported = (uploadErrors == null || uploadErrors.isEmpty())
            ? "" : String.join(" ", uploadErrors);

    // An empty part is by far the common case and has its own wording; anything
    // else (oversize, too many files, a field over maxStringLength, a parse
    // failure) is reported with the generic upload-failure key so the message is
    // never actively misleading.
    boolean emptyFile = reported.isEmpty()
            || reported.toLowerCase().contains("empty");
    String errorKey = emptyFile
            ? "dms.addDocument.errorZeroSize" : "dms.error.uploadError";

    if ("html5MultiUpload".equals(request.getParameter("method"))) {
        String message = reported.isEmpty()
                ? ResourceBundle.getBundle("oscarResources").getString(errorKey) : reported;
        response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST);
        // Servlet headers are ISO-8859-1, so a localized message with non-Latin-1
        // characters would reach the browser mangled. The body carries the text
        // intact; the header stays ASCII-safe for the noswfupload.js reader.
        response.setHeader("oscar_error",
                message.replaceAll("[^\\x20-\\x7E]", "?"));
        response.setContentType("text/plain;charset=UTF-8");
        out.print(message);
    } else {
        Hashtable<String, String> docerrors = new Hashtable<>();
        docerrors.put("uploadRejected", errorKey);
        request.setAttribute("docerrors", docerrors);
%><jsp:forward page="documentReport.jsp"/><%
    }
%>
