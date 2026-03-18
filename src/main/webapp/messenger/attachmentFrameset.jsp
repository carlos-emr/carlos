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
  attachmentFrameset.jsp - Creates frameset for PDF attachment preview
  
  This JSP page creates a frameset structure for displaying PDF attachments
  associated with a specific patient demographic. It divides the browser window
  into two frames for preview and source management.
  
  Frame structure:
  - Top frame (300px): Displays PDF preview via generatePreviewPDF.jsp
  - Bottom frame (0px): Hidden frame for source/processing operations
  
  Request parameters:
  - demographic_no: Patient demographic ID for attachment retrieval
  
  Error handling:
  - Displays message if no demographic is selected
  
  @since 2003
--%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>


<%@ page import="io.github.carlos_emr.carlos.util.*" %>

<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <%
// Retrieve the patient demographic number from request
String demographic_no = request.getParameter("demographic_no");
%>

    <title>OSCAR attachment</title>

        <% if ( demographic_no != null ) { %>
    <%-- Create frameset when demographic is provided --%>
    <frameset rows="300,0">
        <%-- Main frame: Shows the PDF preview for the selected demographic --%>
        <frame name="main"
               src="generatePreviewPDF.jsp?demographic_no=<%=demographic_no%>"
               noresize scrolling=auto marginheight=5 marginwidth=5>
        <%-- Hidden source frame: Used for background processing --%>
        <frame name="srcFrame" src="">
    </frameset>
        <% } else { %>
    <%-- Error message when no demographic selected --%>
    Please select a demographic.
        <% } %>
</html>
