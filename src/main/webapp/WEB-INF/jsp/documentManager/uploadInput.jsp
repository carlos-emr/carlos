<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    The "input" result of documentManager/addEditDocument: where the workflow
    interceptor lands when the multipart layer rejects the upload (e.g. an
    empty file) before the action runs. One action serves two client shapes,
    which need opposite answers:

      - the eDocs add/edit FORM (full page): forward to the documents page,
        like failAdd. A raw 500 here was the old behavior.
      - the legacy inbox uploader (method=html5MultiUpload, raw XHR via
        noswfupload.js): that client treats any 2xx as success, so a 200 HTML
        forward would silently report a REJECTED upload as uploaded. It reads
        the oscar_error header on >= 400, so answer 400 with the message there.
--%><%@ page contentType="text/html;charset=UTF-8" session="false"
%><%@ page import="java.util.ResourceBundle" %><%
    if ("html5MultiUpload".equals(request.getParameter("method"))) {
        String message = ResourceBundle.getBundle("oscarResources")
                .getString("dms.addDocument.errorZeroSize");
        response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST);
        response.setHeader("oscar_error", message);
        response.setContentType("text/plain;charset=UTF-8");
        out.print(message);
    } else {
%><jsp:forward page="documentReport.jsp"/><%
    }
%>
