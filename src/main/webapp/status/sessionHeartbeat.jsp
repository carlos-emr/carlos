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

    Session Heartbeat Endpoint
    =========================
    Lightweight JSON endpoint for client-side session validity checking.
    Returns {"valid": true} if the user has an active authenticated session,
    or {"valid": false} otherwise.

    Critical: session="false" prevents the JSP engine from auto-creating
    a new session on each request (default JSP behavior is session="true").
    Without this, every heartbeat from a logged-out window would create
    an orphan session on the server.

    Called with ?autoRefresh=true so UserActivityFilter.isUserRequest()
    returns false (existing bypass), preventing heartbeats from resetting
    the inactivity timer.

    @since 2026-02-24

--%>
<%@ page contentType="application/json;charset=UTF-8" session="false" %>
<%
    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
    response.setHeader("Pragma", "no-cache");
    response.setDateHeader("Expires", 0);
    boolean valid = false;
    HttpSession sess = request.getSession(false);
    if (sess != null && sess.getAttribute("user") != null) {
        valid = true;
    }
%>
{"valid": <%= valid %>}
