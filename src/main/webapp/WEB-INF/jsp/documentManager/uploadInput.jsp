<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    The "input" result of documentManager/addEditDocument: where the workflow
    interceptor lands when the multipart layer rejects the upload before the
    action runs. One action serves three client shapes, which need different
    answers:

      - the legacy inbox uploader (method=html5MultiUpload, raw XHR via
        noswfupload.js): that client treats any 2xx as success, so a 200 HTML
        forward would silently report a REJECTED upload as uploaded. It reads
        the oscar_error header on >= 400, so answer 400 with the message there.
      - the eDocs ADD form (full page, mode=add/addLink): forward to the
        documents page the way failAdd does. That forward is only useful if it
        carries an error -- "docerrors" is the ONLY error channel those pages
        have (addDocument.jsp keys both its alert list and its openDocPanel
        decision off it), and the action that normally sets it never ran on this
        path. Without it the clinician gets a clean documents list and no
        message at all, which is strictly worse than the raw 500 this result
        replaced: a rejected upload becomes indistinguishable from a refresh.
      - the eDocs EDIT form (mode = the document number): must NOT take that
        forward. editDocument.jsp is opened with popup1(350, 500, ...), so the
        whole documents page lands in a 350x500 window -- and worse,
        addDocument.jsp expands the ADD panel, so a clinician who set out to
        REPLACE a scan is handed a pre-opened Add form. Upload there and the
        chart ends up with two copies, which is the same duplicate-document
        outcome the DocumentUpload2Action catch split exists to prevent. Answer
        with the small shared rejection page instead: visible, correctly sized,
        and it cannot be mistaken for the add form.

    The multipart layer rejects for several distinct reasons -- an empty part,
    struts.multipart.maxStringLength (default 4096, not overridden here),
    maxFiles, the 50MB maxSize, and generic parse failures -- and it puts the
    real wording in the action's errors. Report that wording rather than
    asserting "empty file", which is a guess that is wrong for every other case.
--%><%@ page contentType="text/html;charset=UTF-8" session="false"
%><%@ page import="java.util.Collection" %><%@ page import="java.util.Hashtable"
%><%@ page import="java.util.ResourceBundle" %><%
    Collection<String> uploadErrors = (Collection<String>) request.getAttribute("actionErrors");
    String reported = (uploadErrors == null || uploadErrors.isEmpty())
            ? "" : String.join(" ", uploadErrors);

    // Match on "(0 bytes)", the stable tail of Struts' zero-length message, NOT on the word
    // "empty": that message interpolates the SUBMITTED FILENAME, so a file called
    // "empty-form.pdf" rejected for its size would be reported as zero-length -- user-controlled
    // text deciding which error the user is shown. Anything unrecognised, including a localized
    // message and the fieldErrors-only conversionError path (which leaves actionErrors empty),
    // falls back to the generic key, which is never actively wrong.
    boolean emptyFile = reported.contains("(0 bytes)");
    String errorKey = emptyFile
            ? "dms.addDocument.errorZeroSize" : "dms.error.uploadError";

    String mode = request.getParameter("mode");
    boolean editing = mode != null && !mode.isBlank()
            && !"add".equals(mode) && !"addLink".equals(mode);

    if ("html5MultiUpload".equals(request.getParameter("method"))) {
        String message = reported.isEmpty()
                ? ResourceBundle.getBundle("oscarResources").getString(errorKey) : reported;
        response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST);
        // Servlet headers are ISO-8859-1, so a localized message with non-Latin-1
        // characters would reach the browser mangled. Stripping to printable ASCII
        // also removes CR/LF, so a filename embedded in the message cannot inject a
        // header. The body carries the text intact.
        response.setHeader("oscar_error",
                message.replaceAll("[^\\x20-\\x7E]", "?"));
        response.setContentType("text/plain;charset=UTF-8");
        out.print(message);
    } else if (editing) {
%><jsp:forward page="/WEB-INF/jsp/common/uploadRejected.jsp"/><%
    } else {
        Hashtable<String, String> docerrors = new Hashtable<>();
        // "uploaderror" is the key addDocument.jsp:382 gates its is-invalid highlight on, and
        // the same key the action itself uses for this condition. Any other key still renders
        // the alert but leaves the file input unmarked.
        docerrors.put("uploaderror", errorKey);
        request.setAttribute("docerrors", docerrors);
%><jsp:forward page="documentReport.jsp"/><%
    }
%>
