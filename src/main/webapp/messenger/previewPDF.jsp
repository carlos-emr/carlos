<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada

--%>

<%--
/**
 * PDF Preview Renderer
 *
 * This minimal JSP page renders HTML content as PDF for message attachment previews.
 * It takes HTML source text from a form parameter and uses the Doc2PDF utility to
 * generate and stream a PDF response directly to the client browser.
 *
 * Main Features:
 * - Direct HTML to PDF conversion using Doc2PDF utility
 * - Minimal processing with security validation
 * - Streams PDF content directly to response output
 *
 * Security Requirements:
 * - Requires "_msg" object read permissions via security taglib
 * - User session validation and role-based access control
 *
 * Request Parameters:
 * - srcText: Required HTML source content to convert to PDF
 *
 * Processing:
 * 1. Validates user permissions
 * 2. Extracts HTML content from srcText parameter
 * 3. Wraps content in basic HTML structure
 * 4. Uses Doc2PDF.parseString2PDF() to generate and stream PDF
 *
 * @since 2003
 */
--%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="ca.openosp.openo.util.*" %>
<%@ page import="ca.openosp.openo.util.Doc2PDF" %>
<%
    // Extract HTML content and generate PDF response
    String srcText = request.getParameter("srcText");
    
    // Convert HTML content to PDF and stream directly to response
    Doc2PDF.parseString2PDF(request, response, "<HTML>" + srcText + "</HTML>");
%>
