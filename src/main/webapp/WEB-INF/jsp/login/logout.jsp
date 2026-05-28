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
    BroadcastChannel and localStorage, then submits a POST to /logout
    to invalidate the server-side session and clear cookies.

    POST (not GET) is used because /logout is a mutating action:
    session.invalidate() and cookie deletion are side effects that must
    not fire on a plain link click or browser pre-fetch (GET). A form
    POST keeps the HTTP method semantics correct and matches the
    POST-only guard in Logout2Action.execute().

    There is no fallback for no-JS environments: without JavaScript neither
    the BroadcastChannel broadcast nor the form POST occurs. The session
    will expire naturally rather than being explicitly invalidated. This is
    an acceptable trade-off given that non-JS browsers are effectively
    unsupported in CARLOS EMR.

    session="false" prevents creating a new session when accessed
    without an active session (e.g., after timeout).

    @since 2026-02-24

--%>
<%@ page session="false" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html><head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
<style>
body{margin:0;display:flex;align-items:center;justify-content:center;
height:100vh;font-family:sans-serif;font-size:1.5em;color:#333;background:#fff;}
</style>
</head><body>
<span><fmt:message key="logoutBroadcast.loggedOut"/></span>
<%-- Hidden form used by JavaScript to POST to /logout.
     A GET redirect (window.location.href or meta refresh) cannot be used because
     Logout2Action.execute() rejects non-POST requests with 405. --%>
<form id="logoutForm" action="${pageContext.request.contextPath}/logout" method="post" style="display:none"></form>
<script>
(function(){
    try { var bc = new BroadcastChannel('carlos_logout'); bc.postMessage('logout'); bc.close(); } catch(e) {}
    try { localStorage.setItem('carlos_logout_signal', '' + Date.now()); } catch(e) {}
    try { localStorage.removeItem('carlos_logout_signal'); } catch(e) {}
    // POST to /logout rather than a GET redirect: Logout2Action is a mutating action
    // (session invalidation, cookie deletion) and only accepts POST.
    setTimeout(function(){ document.getElementById('logoutForm').submit(); }, 500);
})();
</script>
</body></html>
