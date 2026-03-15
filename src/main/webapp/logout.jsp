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

    Logout Broadcast Page
    =====================
    Broadcasts a logout signal to all open browser windows/tabs via
    BroadcastChannel and localStorage before redirecting to logout.do.
    This ensures popup windows close and other tabs redirect to the
    login page when any window initiates logout.

    session="false" prevents creating a new session when accessed
    without an active session (e.g., after timeout).

    The meta refresh tag provides a no-JavaScript fallback to ensure
    logout completes even if JavaScript is disabled.

    @since 2026-02-24

--%>
<%@ page session="false" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html><head>
<meta http-equiv="refresh" content="1;url=logout.do">
<style>
body{margin:0;display:flex;align-items:center;justify-content:center;
height:100vh;font-family:sans-serif;font-size:1.5em;color:#333;background:#fff;}
</style>
</head><body>
<span><fmt:message key="logoutBroadcast.loggedOut"/></span>
<script>
(function(){
    try { var bc = new BroadcastChannel('carlos_logout'); bc.postMessage('logout'); bc.close(); } catch(e) {}
    try { localStorage.setItem('carlos_logout_signal', '' + Date.now()); } catch(e) {}
    try { localStorage.removeItem('carlos_logout_signal'); } catch(e) {}
    setTimeout(function(){ window.location.href = 'logout.do'; }, 500);
})();
</script>
</body></html>
