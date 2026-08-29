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
--%><%@ page contentType="application/json;charset=UTF-8" session="false"
%>[{"error":"The file could not be uploaded. It may be empty or unreadable - please check the file and try again."}]
