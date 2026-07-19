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
  selfClose.jsp - Utility page for closing popup windows
  
  This simple JSP page automatically closes the current browser window or tab
  when loaded. It's typically used after completing an action in a popup window
  to return control to the parent window.
  
  Usage scenarios:
  - After completing message composition in a popup
  - Following successful attachment upload
  - After delegate selection or cancellation
  
  Technical note:
  - Uses top.window.close() to ensure the topmost window is closed
  - Works in popup windows opened via JavaScript
  - May be blocked by browser security in regular tabs
  
  @since 2003
--%>

<html>


<body>

<script>
    // Close the current popup window
    top.window.close();
</script>

</body>
</html>
