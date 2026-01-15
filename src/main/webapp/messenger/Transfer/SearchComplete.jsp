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
 * Transfer Completion Confirmation Page
 *
 * This JSP page displays a confirmation message after document transfer has been
 * completed. It shows whether the attachment was successfully added to the target
 * demographic or if it was a duplicate attachment.
 *
 * Main Features:
 * - Displays completion status message with success/duplicate indication
 * - Provides close window functionality with parent window refresh
 * - Integration with OSCAR tab refresh system for message notifications
 * - Clean completion workflow for document transfer process
 *
 * Security Requirements:
 * - Requires "_msg" object write permissions via security taglib
 * - User session validation and role-based access control
 *
 * Request Attributes:
 * - confMessage: Status code indicating transfer result
 *   - "1": Duplicate attachment (already exists for demographic)
 *   - Other: Successful attachment (new attachment created)
 *
 * JavaScript Functions:
 * - BackToOscar(): Handles window closure and parent refresh
 *   - Calls parent window's refresh function for message notifications
 *   - Closes current window after brief delay
 *   - Fallback to simple window close if parent function not available
 *
 * User Experience:
 * - Clear success/duplicate messaging
 * - Automatic return to parent application
 * - Maintains workflow context through parent window integration
 *
 * Integration:
 * - Works with OSCAR tab alert system (oscar_new_msg)
 * - Called after PostItems.jsp processing completes
 * - Part of complete document transfer workflow
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
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/encounterStyles.css">
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>Search Complete</title>

    <script language="JavaScript">
        /**
         * Returns to main OSCAR application and refreshes message alerts
         */
        function BackToOscar() {
            if (opener.callRefreshTabAlerts) {
                // Refresh message tab alerts in parent window
                opener.callRefreshTabAlerts("oscar_new_msg");
                setTimeout("window.close()", 100);
            } else {
                // Fallback to simple window close
                window.close();
            }
        }
    </script>
</head>

<body class="BodyStyle" vlink="#0000FF">
<!--  -->
<table class="MainTable" id="scrollNumber1" name="encounterTable">
    <tr class="MainTableTopRow">
        <td class="MainTableTopRowLeftColumn">oscarComm</td>
        <td class="MainTableTopRowRightColumn">
            <table class="TopStatusBar">
                <tr>
                    <td>Search Complete</td>
                    <td></td>
                    <td style="text-align: right"><a
                            href="javascript:popupStart(300,400,'About.jsp')">About</a> | <a
                            href="javascript:popupStart(300,400,'License.jsp')">License</a></td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td class="MainTableLeftColumn">&nbsp;</td>
        <td class="MainTableRightColumn">
            <%
                // Display appropriate completion message based on result
                String conf = (String) request.getAttribute("confMessage");
                if (conf.equals("1")) { 
            %> 
                This attachment has already been attached to this demographic 
            <% } else { %> 
                Attachment has been attached to this demographic 
            <% } %> 
            <br>
            <a href="javascript:BackToOscar();">Click here</a> to close this
            window.
        </td>
    </tr>
    <tr>
        <td class="MainTableBottomRowLeftColumn"></td>
        <td class="MainTableBottomRowRightColumn"></td>
    </tr>
</table>
</body>
</html>
