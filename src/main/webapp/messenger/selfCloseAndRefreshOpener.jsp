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
  selfCloseAndRefreshOpener.jsp - Closes popup and refreshes parent window
  
  This utility JSP page performs two actions when loaded:
  1. Refreshes the parent window that opened this popup
  2. Closes the current popup window
  
  This is commonly used after operations that modify data displayed in the
  parent window, ensuring the parent shows updated information after the
  popup completes its task.
  
  Usage scenarios:
  - After sending a message (refreshes message list)
  - After modifying message groups (updates group display)
  - Following delegate selection (updates delegate info)
  
  Technical notes:
  - Works in both frame-in-popup and direct popup contexts
  - Safely resolves opener from window.top or window
  - Refresh happens before close to ensure it executes
  - Null-safe: handles cases where opener is absent or closed
  
  @since 2003
--%>

<html>


<body>

<script>
    // Refresh the opener and close, handling both frame-in-popup and direct popup contexts.
    (function() {
        var targetWindow = window.top || window;
        // Find the opener from the top-level window or current window
        var opener = targetWindow.opener || window.opener;

        // Refresh the parent window if available
        if (opener && !opener.closed) {
            try {
                opener.location.reload();
            } catch(e) {
                // Opener may be cross-origin or inaccessible
            }
        }

        // Close the window
        targetWindow.close();
        if (!targetWindow.closed && targetWindow !== window) {
            window.close();
        }
    })();
</script>

</body>
</html>
