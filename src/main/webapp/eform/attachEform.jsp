<%--

    Copyright (c) 2024-2026. CARLOS EMR Project. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    Maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%--
    attachEform.jsp - eForm Attachment Popup for Rich Text Letter

    Provides a popup interface for attaching documents, labs, HRM records,
    and other eForms to a Rich Text Letter. Called by the [attach] toolbar
    button in editControl2.js via popupEformUpload().

    Parameters:
      - demo: demographic number of the patient
      - requestId: fdid of the current eForm instance (optional)

    @since 2026-03-22
--%>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Document" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DocumentManager" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page contentType="text/html; charset=UTF-8" %>
<%
    SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", "r", null)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
        return;
    }

    String demoNo = request.getParameter("demo");
    String requestId = request.getParameter("requestId");

    if (demoNo == null || !demoNo.matches("\\d+")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid demographic number");
        return;
    }

    Integer demographicNo = Integer.parseInt(demoNo);
    Integer fdid = (requestId != null && requestId.matches("\\d+")) ? Integer.parseInt(requestId) : null;

    DocumentManager documentManager = SpringUtils.getBean(DocumentManager.class);

    List<Document> documents;
    try {
        documents = documentManager.getDocumentsByDemographicNo(loggedInInfo, demographicNo);
    } catch (Exception e) {
        documents = new ArrayList<>();
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Attach Files to Letter</title>
    <style>
        body { font-family: Arial, sans-serif; font-size: 12px; margin: 10px; }
        h3 { margin: 5px 0; }
        .doc-list { max-height: 200px; overflow-y: auto; border: 1px solid #ccc; padding: 5px; }
        .doc-item { padding: 2px 0; }
        .doc-item label { cursor: pointer; }
        .btn { padding: 4px 12px; margin: 4px; cursor: pointer; }
        .section { margin-bottom: 10px; }
        .doc { color: blue; }
        .lab { color: #CC0099; }
        .hrm { color: red; }
        .eform { color: green; }
    </style>
</head>
<body>
    <h3>Attach Files to Letter</h3>
    <p>Patient Demographic: <%= Encode.forHtml(demoNo) %></p>

    <form method="post" action="../eform/attachDoc.do">
        <input type="hidden" name="demoNo" value="<%= Encode.forHtmlAttribute(demoNo) %>">
        <% if (fdid != null) { %>
        <input type="hidden" name="requestId" value="<%= fdid %>">
        <% } %>

        <div class="section">
            <h4 class="doc">Documents</h4>
            <div class="doc-list">
                <% if (documents != null && !documents.isEmpty()) {
                    for (Document doc : documents) { %>
                <div class="doc-item">
                    <label>
                        <input type="checkbox" name="attachedDocs" value="<%= doc.getDocumentNo() %>">
                        <%= Encode.forHtml(doc.getDocdesc() != null ? doc.getDocdesc() : "Document #" + doc.getDocumentNo()) %>
                    </label>
                </div>
                <%  }
                } else { %>
                <em>No documents available</em>
                <% } %>
            </div>
        </div>

        <div style="text-align: center;">
            <input type="submit" class="btn" value="Attach Selected">
            <input type="button" class="btn" value="Close" onclick="window.close();">
        </div>
    </form>
</body>
</html>
