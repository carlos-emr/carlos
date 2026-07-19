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
 * Session Expiration Error Page
 *
 * This JSP page displays an error message when a user's messenger session has
 * expired or failed. It provides a simple error notification and instructions
 * for the user to retry their request by returning to the main application.
 *
 * Main Features:
 * - Clear session expiration error message
 * - User-friendly explanation of the error condition
 * - Instructions for resolving the issue
 * - Close window functionality for popup contexts
 *
 * Error Conditions:
 * - Expired oscarEncounter session
 * - Invalid session state
 * - General session-related failures
 *
 * User Actions:
 * - Close window and return to main OSCAR application
 * - Retry the original request after re-establishing session
 *
 * Usage Context:
 * - Displayed when document transfer or attachment operations fail due to session issues
 * - Can appear in popup windows or main application context
 * - Part of error handling workflow for messenger operations
 *
 * Technical Support:
 * - Advises users to contact technical support for persistent issues
 * - Indicates potential system errors beyond normal session expiration
 *
 * @since 2003
 */
--%>

<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>Oscar Prescription Module - Error</title>
</head>
<body style="width: 600px">
<h1>Session Expired</h1>
<p>Your oscarEncounter session has failed.</p>
<p>Please go back to oscar and try your request again.</p>
<p>If this message appears to have been shown for no reason an error
    may have occurred. If this problem persists, please contact Oscar
    Technical Support.</p>
<a href="javascript:window.close();">Close this window</a>
</body>
</html>
