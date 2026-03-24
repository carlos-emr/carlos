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
    displayAttachedFiles.jsp - AJAX endpoint returning attached file list HTML

    Returns an HTML fragment listing files attached to a saved eForm instance,
    with color-coded CSS classes (.doc, .lab, .hrm, .eform) matching the RTL
    sidebar legend. Displayed in the #tdAttachedDocs panel on the right side
    of the Rich Text Letter editor.

    Architecture:
      - Called by: fetchAttached() in the DB-stored form_html (via $.ajax)
      - URL: ../eform/displayAttachedFiles.jsp?requestId=<fdid>
      - Response: HTML fragment (not a full page) — injected via jQuery .html()
      - The fdid comes from the ${fdid} token replaced by EForm.setFdid()

    Note on ${fdid} token:
      For NEW forms (via efmformadd_data.jsp), the ${fdid} token is NOT replaced
      because no eform_data record exists yet. The request arrives with the literal
      string "${fdid}" as the requestId, which fails the \d+ regex check and
      returns "No attachments" — this is expected behavior.

    Parameters:
      - requestId: fdid of the eForm data instance (must be digits)

    Security:
      - Requires _eform read privilege
      - requestId validated with \d+ regex before Integer.parseInt()
      - All IDs OWASP-encoded in output

    @since 2026-03-22
--%>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager" %>
<%@ page import="java.util.List" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page contentType="text/html; charset=UTF-8" %>
<%
    SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", "r", null)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
    }

    String requestId = request.getParameter("requestId");
    if (requestId == null || !requestId.matches("\\d+")) {
        out.print("<em>No attachments</em>");
        return;
    }

    Integer fdid = Integer.parseInt(requestId);
    DocumentAttachmentManager attachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);

    List<String> docIds;
    List<String> labIds;
    List<String> hrmIds;
    List<String> eformIds;
    try {
        docIds = attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.DOC, null);
        labIds = attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.LAB, null);
        hrmIds = attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.HRM, null);
        eformIds = attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.EFORM, null);
    } catch (Exception e) {
        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error("Failed to load attachments for fdid=" + fdid, e);
        out.print("<em>Error loading attachments</em>");
        return;
    }

    boolean hasAttachments = (docIds != null && !docIds.isEmpty())
            || (labIds != null && !labIds.isEmpty())
            || (hrmIds != null && !hrmIds.isEmpty())
            || (eformIds != null && !eformIds.isEmpty());

    if (!hasAttachments) {
        out.print("<em>No attachments</em>");
        return;
    }
%>
<% if (docIds != null) { for (String id : docIds) { %>
<span class="doc">Doc #<%= Encode.forHtml(id) %></span><br>
<% } } %>
<% if (labIds != null) { for (String id : labIds) { %>
<span class="lab">Lab #<%= Encode.forHtml(id) %></span><br>
<% } } %>
<% if (hrmIds != null) { for (String id : hrmIds) { %>
<span class="hrm">HRM #<%= Encode.forHtml(id) %></span><br>
<% } } %>
<% if (eformIds != null) { for (String id : eformIds) { %>
<span class="eform">EForm #<%= Encode.forHtml(id) %></span><br>
<% } } %>
