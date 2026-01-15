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
  AdjustAttachments.jsp - Processes selected attachments for message composition
  
  This JSP page handles the selection and adjustment of attachments during the message
  composition workflow. It processes checkbox selections from the previous page to
  determine which attachments should be included with the message being composed.
  
  Main functionality:
  - Validates user has write permissions for messaging
  - Processes checkbox selections for attachments (items prefixed with "item")
  - Decodes the Base64-encoded XML document containing attachment metadata
  - Parses and filters the attachments based on user selections
  - Updates the session bean with the filtered attachment list
  - Redirects to demographic search for recipient selection
  
  Security:
  - Requires "_msg" object with write ("w") permissions
  - Redirects to security error page if unauthorized
  
  Session dependencies:
  - Requires msgSessionBean in session for message composition state
  - Validates bean existence and validity before processing
  
  Request parameters:
  - xmlDoc: Base64-encoded XML document containing attachment information
  - id: Encoded message ID
  - item*: Checkbox parameters indicating selected attachments (e.g., item0, item1)
  
  @since 2003
--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    // Build role name string for security check combining user role and user ID
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");%>
</security:oscarSec>
<%
    // Exit page processing if user is not authorized
    if (!authed) {
        return;
    }
%>


<%@page contentType='text/xml; charset=UTF-8' pageEncoding='UTF-8'
        import="ca.openosp.openo.messenger.docxfer.send.*, ca.openosp.openo.messenger.docxfer.util.*" %>
<%@ page import="ca.openosp.openo.messenger.docxfer.util.MsgCommxml" %>
<%@ page import="ca.openosp.openo.messenger.pageUtil.MsgSessionBean" %>
<%@ page import="ca.openosp.openo.messenger.docxfer.send.MsgSendDocument" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>


<c:if test="${empty sessionScope.msgSessionBean}">
    <c:redirect url="index.jsp"/>
</c:if>
<c:if test="${not empty sessionScope.msgSessionBean}">
    <c:set var="bean" value="${sessionScope.msgSessionBean}" scope="page"/>
    <c:if test="${bean.valid == false}">
        <c:redirect url="index.jsp"/>
    </c:if>
</c:if>

<%
    // Retrieve the message session bean from page context
    MsgSessionBean bean = (MsgSessionBean) pageContext.findAttribute("bean");
    String checks = "";
    java.util.Enumeration names = request.getParameterNames();

    // Process all request parameters to find selected attachment checkboxes
    // Checkboxes are named "item0", "item1", etc.
    while (names.hasMoreElements()) {
        String name = (String) names.nextElement();
        if (name.startsWith("item")) {
            if (request.getParameter(name).equalsIgnoreCase("on")) {
                // Extract the item number and add to comma-separated list
                checks += name.substring(4) + ",";
            }
        }
    }

    // Decode the Base64-encoded XML document containing attachment metadata
    String xmlDoc = MsgCommxml.decode64(request.getParameter("xmlDoc"));
    String idEnc = request.getParameter("id");

    // Parse the XML document and filter based on selected checkboxes
    String sXML = MsgCommxml.toXML(new MsgSendDocument().parseChecks(xmlDoc, checks));

    // Update session bean with filtered attachments and redirect
    if (bean != null) {
        bean.setAttachment(sXML);
        bean.setMessageId(idEnc);
        // Store encoded versions in request for potential use by next page
        request.setAttribute("XMLattachment", MsgCommxml.encode64(sXML));
        request.setAttribute("IDenc", idEnc);
    }
    
    // Redirect to demographic search page to select message recipients
    response.sendRedirect(request.getContextPath() + "/messenger/Transfer/DemographicSearch.jsp");
%>
<%
    // Debug output placeholder - originally used to display processed XML
%>
