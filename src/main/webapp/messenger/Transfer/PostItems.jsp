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
 * Document Transfer Processing Backend
 *
 * This JSP page processes the final step of document transfer after users have
 * selected items in SelectItems.jsp. It handles the conversion of selected
 * document items into attachments and redirects to message creation.
 *
 * Main Features:
 * - Processes form data from document selection interface
 * - Converts selected items to XML attachment format
 * - Updates message session bean with attachment data
 * - Redirects to message creation page after processing
 *
 * Security Requirements:
 * - Requires "_msg" object write permissions via security taglib
 * - User session validation and role-based access control
 *
 * Request Parameters:
 * - xmlDoc: Base64-encoded XML document containing available items
 * - item[N]: Checkbox parameters for selected items (where N is item ID)
 *
 * Processing Flow:
 * 1. Iterates through all form parameters to find selected items
 * 2. Builds comma-separated list of selected item IDs
 * 3. Decodes the XML document from base64 format
 * 4. Uses MsgSendDocument to parse selections and generate attachment XML
 * 5. Updates msgSessionBean with attachment data
 * 6. Redirects to CreateMessage.jsp for final message composition
 *
 * Session Dependencies:
 * - msgSessionBean: Updated with attachment data for message creation
 *
 * Technical Details:
 * - Returns XML content type but immediately redirects
 * - Uses MsgCommxml utilities for XML processing
 * - Integrates with MsgSendDocument for attachment generation
 *
 * @since 2003
 */
--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page contentType='text/xml; charset=UTF-8' pageEncoding='UTF-8'
        import="ca.openosp.openo.messenger.docxfer.send.*, ca.openosp.openo.messenger.docxfer.util.*" %>
<%@ page import="ca.openosp.openo.messenger.docxfer.util.MsgCommxml" %>
<%@ page import="ca.openosp.openo.messenger.pageUtil.MsgSessionBean" %>
<%@ page import="ca.openosp.openo.messenger.docxfer.send.MsgSendDocument" %>
<%
    // Process selected document items from form submission
    String checks = "";
    java.util.Enumeration names = request.getParameterNames();

    // Iterate through form parameters to find selected items
    while (names.hasMoreElements()) {
        String name = (String) names.nextElement();
        if (name.startsWith("item")) {
            if (request.getParameter(name).equalsIgnoreCase("on")) {
                // Build comma-separated list of selected item IDs
                checks += name.substring(4) + ",";
            }
        }
    }

    // Decode XML document and process selected items
    String xmlDoc = MsgCommxml.decode64(request.getParameter("xmlDoc"));
    java.util.ArrayList aList = new java.util.ArrayList();
    String sXML = MsgCommxml.toXML(new MsgSendDocument().parseChecks2(xmlDoc, checks, aList));

    // Update message session bean with attachment data
    MsgSessionBean bean = (MsgSessionBean) request.getSession().getAttribute("msgSessionBean");
    bean.setAttachment(sXML);

    // Redirect to message creation page
    response.sendRedirect(request.getContextPath() + "/messenger/CreateMessage.jsp");
%>
