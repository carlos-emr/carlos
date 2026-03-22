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

    Returns <option> elements for the Rich Text Letter template dropdown.
    Called via AJAX from editControl2.js Start() function to populate
    the template <select> with available .rtl files from the eForm images directory.

    @since 2026-03-22
--%>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>
<%@ page import="io.github.carlos_emr.carlos.eform.EFormUtil" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page contentType="text/html; charset=UTF-8" %>
<%
    ArrayList<String> templates = EFormUtil.listRichTextLetterTemplates();
%>
<option value="">&mdash; template &mdash;</option>
<option value="blank.rtl">blank</option>
<% for (String template : templates) {
    if (!"blank.rtl".equalsIgnoreCase(template)) {
        String displayName = template.substring(0, template.lastIndexOf("."));
%>
<option value="<%= Encode.forHtmlAttribute(template) %>"><%= Encode.forHtml(displayName) %></option>
<%
    }
} %>
