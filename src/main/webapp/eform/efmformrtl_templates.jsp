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
    efmformrtl_templates.jsp - RTL Template Dropdown Options

    AJAX endpoint that returns <option> HTML elements for the Rich Text Letter
    template dropdown selector. Called by editControl2.js Start() function on
    eForm load to populate the template <select> control.

    How it works:
      1. Enforces _eform read privilege (same check as all eForm endpoints)
      2. Calls EFormUtil.listRichTextLetterTemplates() which scans the eForm
         images directory for *.rtl files
      3. Emits one <option> per template with OWASP-encoded filename as value
         and the filename (minus extension) as display text
      4. "blank.rtl" is always listed first as a static option and excluded
         from the dynamic loop to avoid duplication

    Called from: editControl2.js Start() -> $.ajax({url: "efmformrtl_templates.jsp"})
    Response: HTML fragment (not a full page) — injected into #template select via .html()

    Parameters: none (uses session for auth, no request parameters needed)
    Security: _eform read privilege required

    @since 2026-03-22
--%>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<%@ page import="io.github.carlos_emr.carlos.eform.EFormUtil" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page contentType="text/html; charset=UTF-8" %>
<%
    // Security: require _eform read privilege before listing templates
    SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", "r", null)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
    }

    // Scan the eForm images directory for .rtl template files
    ArrayList<String> templates = EFormUtil.listRichTextLetterTemplates();
%>
<%-- Default placeholder and blank template are always available --%>
<option value="">&mdash; template &mdash;</option>
<option value="blank.rtl">blank</option>
<%-- Dynamically list any additional .rtl files uploaded by clinic admins --%>
<% for (String template : templates) {
    if (!"blank.rtl".equalsIgnoreCase(template)) {
        // Strip file extension for display (e.g., "referral.rtl" -> "referral")
        // Guard: dotIndex > 0 handles files with no dot or leading dot
        int dotIndex = template.lastIndexOf('.');
        String displayName = (dotIndex > 0) ? template.substring(0, dotIndex) : template;
%>
<option value="<%= Encode.forHtmlAttribute(template) %>"><%= Encode.forHtml(displayName) %></option>
<%
    }
} %>
