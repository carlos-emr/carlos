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

    Returns an HTML fragment listing files attached to an eForm instance,
    with color-coded classes (.doc, .lab, .hrm, .eform). Called by
    fetchAttached() in the Rich Text Letter eForm HTML.

    Parameters:
      - requestId: fdid of the eForm instance

    @since 2026-03-22
--%>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>
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

    // Get attached document IDs by type
    List<String> docIds = attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.DOC, null);
    List<String> labIds = attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.LAB, null);
    List<String> hrmIds = attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.HRM, null);
    List<String> eformIds = attachmentManager.getEFormAttachments(loggedInInfo, fdid, DocumentType.EFORM, null);

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
