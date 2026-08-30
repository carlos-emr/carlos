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

    The message comes from the same localized bundle key the action itself
    uses for a zero-length file (the one condition the multipart layer rejects
    here), and it is minimally JSON-escaped so a future edit to the bundle
    text -- a quote, a backslash, a line break -- cannot break the client's
    JSON.parse.
--%><%@ page contentType="application/json;charset=UTF-8" session="false"
%><%@ page import="java.util.ResourceBundle" %><%
    String message = ResourceBundle.getBundle("oscarResources")
            .getString("dms.addDocument.errorZeroSize");
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
