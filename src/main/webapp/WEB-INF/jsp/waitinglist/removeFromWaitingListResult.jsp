<%--

    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos

--%>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<%--
    Purpose:
        Render the post-remove popup result for waiting-list removals.

    Features:
        Reloads the opener window so the waiting-list view reflects the removal.
        Closes the popup after the parent window refresh is triggered.

    Parameters:
        None.

    @since 2026-04 waiting-list JSP gating hardening
--%>
<!DOCTYPE html>
<html>
<head>
    <title></title>
    <link rel="stylesheet" type="text/css"
          href="${e:forHtmlAttribute(pageContext.request.contextPath)}/styles.css">
</head>
<body>
<table>
    <tr>
        <td>Update waiting list</td>
    </tr>
</table>
<script type="text/javascript">
    if (window.opener && !window.opener.closed) {
        window.opener.location.reload();
    }
    self.close();
</script>
</body>
</html>
