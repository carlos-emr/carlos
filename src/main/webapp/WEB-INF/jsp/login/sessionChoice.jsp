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
<%--
    Concurrent-session chooser (issue #3980)
    ========================================
    Shown after every credential check has passed, when the site's concurrent-session policy
    (login.concurrent_sessions.policy / .max in carlos.properties) needs the user to decide what
    happens to their other signed-in sessions. The page is rendered by Login2Action (result
    "sessionChoice") and never has a public URL.

    Security contract:
      - The user is authenticated but has NO authenticated session yet. The pre-login session holds
        only an opaque token (PendingSessionChoices.TOKEN_ATTR); the login itself waits server-side
        in PendingSessionChoiceCache. Without that token this page redirects to /logoutPage.
      - The form is a real POST to /login/sessionChoice so CSRFGuard injects its token; the route
        is CSRF-protected and POST-only.
      - Every value printed is encoded with <carlos:encode>. The session list carries no session
        ids, only times and the sign-in address, and only the signing-in user ever sees it.

    Request attribute:
      concurrentSessionChoice  ConcurrentSessionChoiceViewModel (other sessions, whether "keep"
                               is allowed, the configured limit)
      sessionChoiceKeepRefused Boolean, set when "keep" was chosen after the limit was reached

    The "keep or sign out other sessions" chooser follows open-osp/Open-O PR #136 (Chitrank Davé,
    2025); this page is a CARLOS reimplementation, not a copy.

    @since 2026-09-26
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%
    // Reachable only as a forward from Login2Action while a pending login is staged. Return right
    // after the redirect so nothing is written to a committed response.
    if (session.getAttribute(io.github.carlos_emr.carlos.login.PendingSessionChoices.TOKEN_ATTR) == null
            || request.getAttribute(io.github.carlos_emr.carlos.login.ConcurrentSessionChoiceViewModel.REQUEST_ATTR) == null) {
        response.sendRedirect(request.getContextPath() + "/logoutPage");
        return;
    }
%>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%-- Same locale the view model formats times with, so text and dates never mix languages. --%>
<c:set var="bundleLocale" value="<%= io.github.carlos_emr.carlos.utility.LocaleUtils.resolveBundleLocale(request) %>" scope="page"/>
<fmt:setLocale value="${bundleLocale}"/>
<fmt:setBundle basename="oscarResources"/>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="page"/>
<c:set var="choice" value="${requestScope.concurrentSessionChoice}" scope="page"/>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(bundleLocale.language)}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="icon" href="${ctx}/images/favicon.ico"/>
    <title><fmt:message key="login.concurrentSessions.title"/></title>
    <link rel="stylesheet" href="${ctx}/library/bootstrap/5.3.8/css/bootstrap.min.css" type="text/css"/>
</head>
<body class="bg-light">
<main class="container-lg d-flex justify-content-center mt-0 mt-lg-5 py-4 py-lg-5">
    <div class="col-xl-8 col-lg-8 col-md-10 col-sm-12">
        <div class="card shadow-sm" id="sessionChoiceCard">
            <div class="card-header bg-transparent text-center">
                <h1 class="h5 mb-0"><fmt:message key="login.concurrentSessions.title"/></h1>
            </div>
            <div class="card-body">
                <p id="sessionChoiceIntro">
                    <fmt:message key="login.concurrentSessions.intro">
                        <fmt:param><strong><carlos:encode value="${choice.otherSessionCount}"/></strong></fmt:param>
                    </fmt:message>
                </p>

                <c:if test="${not empty choice.otherSessions}">
                    <div class="table-responsive">
                        <table class="table table-sm align-middle" id="otherSessionsTable">
                            <thead>
                            <tr>
                                <th scope="col"><fmt:message key="login.concurrentSessions.columnSignedIn"/></th>
                                <th scope="col"><fmt:message key="login.concurrentSessions.columnLastActive"/></th>
                                <th scope="col"><fmt:message key="login.concurrentSessions.columnAddress"/></th>
                            </tr>
                            </thead>
                            <tbody>
                            <c:forEach var="other" items="${choice.otherSessions}">
                                <tr>
                                    <td><carlos:encode value="${other.signedInAt}"/></td>
                                    <td><carlos:encode value="${other.lastActiveAt}"/></td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${not empty other.remoteAddr}"><carlos:encode value="${other.remoteAddr}"/></c:when>
                                            <c:otherwise><fmt:message key="login.concurrentSessions.unknownAddress"/></c:otherwise>
                                        </c:choose>
                                    </td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:if>

                <c:if test="${requestScope.sessionChoiceKeepRefused eq true}">
                    <div class="alert alert-warning" role="alert" id="sessionChoiceKeepRefused">
                        <fmt:message key="login.concurrentSessions.keepRefused"/>
                    </div>
                </c:if>
                <c:if test="${choice.signOutRequired}">
                    <div class="alert alert-info" aria-live="polite" id="sessionChoiceLimitReached">
                        <fmt:message key="login.concurrentSessions.limitReached">
                            <fmt:param><carlos:encode value="${choice.maxSessions}"/></fmt:param>
                        </fmt:message>
                    </div>
                </c:if>

                <p class="text-body-secondary small mb-1" id="sessionChoiceWarning">
                    <fmt:message key="login.concurrentSessions.warning"/>
                </p>
                <p class="text-body-secondary small">
                    <fmt:message key="login.concurrentSessions.help"/>
                </p>

                <form action="${ctx}/login/sessionChoice" method="post" id="sessionChoiceForm">
                    <div class="row g-2 mt-2">
                        <c:if test="${not choice.signOutRequired}">
                            <div class="col-12 col-md-6">
                                <button type="submit" class="btn btn-outline-secondary w-100" id="keepOtherSessions"
                                        name="sessionChoice" value="keep">
                                    <fmt:message key="login.concurrentSessions.keep"/>
                                </button>
                            </div>
                        </c:if>
                        <div class="col-12 ${choice.signOutRequired ? '' : 'col-md-6'}">
                            <button type="submit" class="btn btn-primary w-100" id="signOutOtherSessions"
                                    name="sessionChoice" value="signOutOthers">
                                <fmt:message key="login.concurrentSessions.signOut"/>
                            </button>
                        </div>
                    </div>
                </form>

                <%-- Cancel ends the pending login the same way a logout would: POST, because /logout
                     only accepts POST, and it clears the pending token with the pre-login session. --%>
                <form action="${ctx}/logout" method="post" class="text-center mt-3" id="sessionChoiceCancelForm">
                    <button type="submit" class="btn btn-link btn-sm" id="cancelSessionChoice">
                        <fmt:message key="login.concurrentSessions.cancel"/>
                    </button>
                </form>
            </div>
        </div>
    </div>
</main>
</body>
</html>
