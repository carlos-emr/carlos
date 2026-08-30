<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    Rendered by the "input" result of documentManager/documentUpload when the
    multipart layer rejects the upload (e.g. an empty file) before the action
    runs. documentUploader.jsp's XHR handler parses the response as the JSON
    array executeUpload() writes and shows item.error to the user, so the
    rejection must answer in that same contract -- without this, the request
    fell through to errorpage.jsp as a raw HTML 500 and the user saw only
    "(HTTP 500)".

    An empty part is NOT the only condition that lands here: the multipart
    layer also rejects on struts.multipart.maxStringLength (default 4096, not
    overridden in struts.xml), maxFiles, the 50MB maxSize, and generic parse
    failures. It puts the real wording in the action's errors, so report that
    and fall back to the zero-length key only when there is nothing to report --
    hardcoding "Empty files not accepted." told a user whose 60MB scan was
    rejected for its size exactly the wrong thing.

    The message is minimally JSON-escaped so a bundle edit -- a quote, a
    backslash, a line break -- cannot break the client's JSON.parse.
--%><%@ page contentType="application/json;charset=UTF-8" session="false"
%><%@ page import="java.util.Collection" %><%@ page import="java.util.ResourceBundle" %><%
    Collection<String> uploadErrors = (Collection<String>) request.getAttribute("actionErrors");
    String message = (uploadErrors == null || uploadErrors.isEmpty())
            ? ResourceBundle.getBundle("oscarResources").getString("dms.addDocument.errorZeroSize")
            : String.join(" ", uploadErrors);
    StringBuilder escaped = new StringBuilder(message.length() + 8);
    for (int i = 0; i < message.length(); i++) {
        char c = message.charAt(i);
        if (c == '"' || c == '\\') {
            escaped.append('\\').append(c);
        } else if (c < 0x20) {
            escaped.append(String.format("\\u%04x", (int) c));
        } else {
            escaped.append(c);
        }
    }
%>[{"error":"<%= escaped.toString() %>"}]
