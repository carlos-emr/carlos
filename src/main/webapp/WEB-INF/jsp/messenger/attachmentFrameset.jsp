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
  attachmentFrameset.jsp - Hosts the Messenger PDF attachment chooser in a frame
  
  This JSP page hosts the Messenger attachment chooser for a specific patient
  demographic in a single full-page iframe (the chooser and its PDF preview
  replace each other in that frame). It was a one-frame frameset; HTML5 drops
  framesets, and an iframe keeps the same "main" frame name and top/opener
  behaviour the chooser and its close/refresh page rely on.

  Frame structure:
  - main: the chooser (messenger/PreviewPDF); Preview streams the PDF into it

  There is no hidden source frame any more: items used to be loaded into one and
  their HTML posted back for conversion; the server now renders them (#4133).
  
  Request parameters:
  - demographic_no: Patient demographic ID for attachment retrieval
  
  Error handling:
  - Displays message if no demographic is selected
  
  @since 2003
--%>

<%@ page import="io.github.carlos_emr.carlos.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <%
// Retrieve the patient demographic number from request
String demographic_no = request.getParameter("demographic_no");
%>

    <title>CARLOS <fmt:message key="messenger.ViewMessage.msgAttachments"/></title>
    <style>
        html, body { height: 100%; margin: 0; }
        iframe[name="main"] { display: block; width: 100%; height: 100%; border: 0; }
    </style>
</head>
<body>
        <% if ( demographic_no != null ) { %>
    <%-- Main frame: the chooser via the gated Struts action; Preview streams the PDF into it. --%>
    <fmt:message key="messenger.ViewMessage.msgAttachments" var="attachmentsTitle"/>
    <iframe name="main" title="${carlos:forHtmlAttribute(attachmentsTitle)}"
            src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/messenger/PreviewPDF?demographic_no=<%= SafeEncode.forUriComponent(demographic_no) %>"></iframe>
        <% } else { %>
    <%-- Error message when no demographic selected --%>
    Please select a demographic.
        <% } %>
</body>
</html>
