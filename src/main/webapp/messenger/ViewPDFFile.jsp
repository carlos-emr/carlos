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
 * PDF File Viewer/Download Handler
 *
 * This JSP page handles the viewing and downloading of specific PDF files from
 * message attachments. It validates session state and processes PDF file requests
 * for individual files within attachment collections.
 *
 * Main Features:
 * - Session validation for message context
 * - PDF file identification and processing
 * - Integration with attachment viewing workflow
 *
 * Security Requirements:
 * - Requires "_msg" object read permissions via security taglib
 * - User session validation and role-based access control
 * - Validates msgSessionBean presence and validity
 *
 * Request Parameters:
 * - id: PDF file identifier for processing
 *
 * Request Attributes:
 * - PDFAttachment: PDF attachment data from previous processing step
 *
 * Session Dependencies:
 * - msgSessionBean: Required for attachment management context
 * - Must be valid session bean or redirects to index.jsp
 *
 * Processing Flow:
 * 1. Validates user permissions and session state
 * 2. Extracts PDF attachment data from request attributes
 * 3. Processes specified PDF file for viewing/download
 * 4. Note: Full implementation appears to be truncated in this file
 *
 * @since 2003
 */
--%>

<%@ page
        import="ca.openosp.openo.messenger.docxfer.send.*, ca.openosp.openo.messenger.docxfer.util.*, ca.openosp.openo.util.*" %>
<%@ page import="java.util.*, org.w3c.dom.*" %>
<%@ page import="ca.openosp.openo.messenger.pageUtil.MsgSessionBean" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
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

<c:if test="${empty sessionScope.msgSessionBean}">
    <% response.sendRedirect(request.getContextPath() + "/messenger/index.jsp"); %>
</c:if>
<c:if test="${not empty sessionScope.msgSessionBean}">
    <% 
        // Directly accessing the bean from the session
        MsgSessionBean bean = (MsgSessionBean) session.getAttribute("msgSessionBean");
        if (!bean.isValid()) {
            response.sendRedirect(request.getContextPath() + "/messenger/index.jsp");
        }
    %>
</c:if>
<%
    // Extract PDF processing parameters
    String pdfAttch = (String) request.getAttribute("PDFAttachment");
    String id = request.getParameter("id");
%>
