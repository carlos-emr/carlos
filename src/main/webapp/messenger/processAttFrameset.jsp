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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%--
/**
 * Attachment Processing Frameset
 *
 * This JSP page creates a frameset for processing PDF attachments in the OpenO EMR
 * messenger system. It displays the main attachment processing interface in the top
 * frame and provides a hidden bottom frame for background processing operations.
 *
 * Main Features:
 * - Two-frame layout with main interface and hidden processing frame
 * - Passes demographic context and attachment parameters to processing page
 * - Integrated with PDF generation and attachment workflow
 *
 * Request Parameters:
 * - demographic_no: Required patient demographic number for context
 * - uri: Document URI to be processed as attachment
 * - pdfTitle: Display title for the PDF attachment
 *
 * Frame Structure:
 * - attMain: Main interface (400px height) - processPDF.jsp
 * - attFrame: Hidden processing frame (0px height) - initially empty
 *
 * Integration:
 * - Used in conjunction with generatePreviewPDF.jsp workflow
 * - Supports PDF generation from various medical document sources
 * - Maintains patient context throughout attachment process
 *
 * @since 2003
 */
--%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ page import="io.github.carlos_emr.carlos.util.*" %>

<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <%
            // Extract attachment processing parameters
            String demographic_no = request.getParameter("demographic_no");
            String uri = request.getParameter("uri");
            String pdfTitle = request.getParameter("pdfTitle");
        %>

    <title>OSCAR attachment <%=uri%>
    </title>
    <frameset rows="400,0">
        <frame name="attMain"
               src="processPDF.jsp?demographic_no=<%=demographic_no%>&pdfTitle=<%=pdfTitle%>&uri=<%=uri%>"
               noresize scrolling=auto marginheight=5 marginwidth=5>
        <frame name="attFrame" src="">
    </frameset>

</html>
