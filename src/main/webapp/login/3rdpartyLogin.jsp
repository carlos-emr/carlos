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

    Migrated from Apache CXF to ScribeJava OAuth1 implementation.

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ page import="com.github.scribejava.core.model.OAuth1RequestToken" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.login.OAuthSessionMerger" %>
<%@ page import="java.util.*" %>
<%@ page import="jakarta.servlet.http.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.login.OAuthData" %>
<%@ page import="io.github.carlos_emr.carlos.login.OOBAuthorizationResponse" %>

<%
    // --- Make variables visible to the whole JSP ---
    OAuthData oauthData = (OAuthData) request.getAttribute("oauthData");
    if (oauthData == null) {
        // also check alternate key used elsewhere during the migration
        oauthData = (OAuthData) request.getAttribute("oauthauthorizationdata");
    }
    if (oauthData == null) {
        oauthData = (OAuthData) session.getAttribute("oauthData");
        if (oauthData == null) {
            oauthData = (OAuthData) session.getAttribute("oauthauthorizationdata");
        }
    }

    OOBAuthorizationResponse oauthOobResponse =
        (OOBAuthorizationResponse) request.getAttribute("oobauthorizationresponse");
    if (oauthOobResponse == null) {
        oauthOobResponse = (OOBAuthorizationResponse) session.getAttribute("oobauthorizationresponse");
    }

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    boolean loggedIn = (session.getAttribute("user") != null) && (loggedInInfo != null);

    // Try to merge only if we think we're logged in
    if (loggedIn) {
        boolean didMerge = OAuthSessionMerger.mergeSession(request);
        if (!didMerge) {
            loggedIn = false;
        }
    }
    request.setAttribute("loggedIn", loggedIn);

    String providerName = "";
    if (loggedInInfo != null && loggedInInfo.getLoggedInProvider() != null) {
        providerName = loggedInInfo.getLoggedInProvider().getFormattedName();
    }
    request.setAttribute("providerName", providerName);

    if (oauthData != null) {
        request.setAttribute("oauthData", oauthData);
    }
    if (oauthOobResponse != null) {
        request.setAttribute("oauthOobResponse", oauthOobResponse);
    }
%>


<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Login and Authorize 3rd Party Application</title>
    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet">
    <style type="text/css">
        body {
            padding-top: 40px;
            padding-bottom: 40px;
            background-color: #f5f5f5;
        }
        .form-signin {
            max-width: 300px;
            padding: 19px 29px 29px;
            margin: 0 auto 20px;
            background-color: #fff;
            border: 1px solid #e5e5e5;
            border-radius: 5px;
            box-shadow: 0 1px 2px rgba(0, 0, 0, .05);
        }
        .form-signin .form-signin-heading,
        .form-signin .form-check {
            margin-bottom: 10px;
        }
        .form-signin input[type="text"],
        .form-signin input[type="password"] {
            font-size: 16px;
            height: auto;
            margin-bottom: 15px;
            padding: 7px 9px;
        }
    </style>
    <script>
        // tiny DOM helpers
        const el = (id) => document.getElementById(id);
        const show = (id) => { const n = el(id); if (n) n.style.display = ""; };
        const hide = (id) => { const n = el(id); if (n) n.style.display = "none"; };
        const setText = (id, txt) => { const n = el(id); if (n) n.textContent = txt; };

        // expose to inline onclick handlers
        window.deny = () => {
            const decision = el("oauthDecision");
            const form = el("scopeForm");
            if (decision) decision.value = "deny";
            if (form) form.submit();
        };

        window.submitCredentials = async () => {
            const username = el("username")?.value || "";
            const password = el("password")?.value || "";
            const pin      = el("pin")?.value || "";
            const oauthToken = el("oauth_token")?.value || "";

            const loginUrl = "${pageContext.request.contextPath}/login.do;jsessionid=${pageContext.session.id}";

            const formData = new URLSearchParams();
            formData.set("username", username);
            formData.set("password", password);
            formData.set("pin", pin);
            formData.set("ajaxResponse", "true");
            formData.set("invalidate_session", "false");
            formData.set("oauth_token", oauthToken);

            try {
                const res = await fetch(loginUrl, {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
                    body: formData.toString(),
                    credentials: "same-origin"
                });

                const data = await res.json();

                if (data && data.success) {
                    hide("login_div");
                    show("scope_div");
                    show("loggedin_div");
                    setText("providerName", data.providerName || "");
                } else {
                    const msg = (data && data.error) ? data.error : "Login failed.";
                    const err = el("login_error");
                    if (err) {
                        err.style.display = "";
                        const span = err.querySelector("span span");
                        if (span) span.textContent = msg;
                    }
                }
                } catch (e) {
                    const err = el("login_error");
                    if (err) {
                        err.style.display = "";
                        const span = err.querySelector("span span");
                        if (span) span.textContent = "Network error. Please try again.";
                    }
            }
        };

        document.addEventListener("DOMContentLoaded", () => {
            const loggedIn = ${loggedIn};
            const hasOauthOobResponse = ${not empty oauthOobResponse};
            const safeProviderName = '${e:forJavaScript(providerName)}';

            if (loggedIn) {
                hide("login_div");
                show("scope_div");
                show("loggedin_div");
                setText("providerName", safeProviderName);
            } else {
                show("login_div");
                hide("scope_div");
                hide("loggedin_div");
            }

            if (hasOauthOobResponse) {
                hide("login_div");
            }
        });
    </script>
</head>
<body>
<div class="container">
    <div class="row">
        <div class="col-md-5">
            <div style="margin-top:25px;">
                <img src="${pageContext.request.contextPath}/images/OSCAR-LOGO.gif"
                     width="450" height="274" alt="OSCAR Logo">
                <p>
                    <font size="-1" face="Verdana, Arial, Helvetica, sans-serif">
                        OSCAR McMaster<br>
                        For more info, visit
                        <a href="http://www.oscarcanada.org">www.oscarcanada.org</a><br>
                    </font>
                </p>
            </div>
        </div>
        <div class="col-md-7">

            <!-- Username / Password Form -->
            <div id="login_div" class="form-container">
                <form class="form-signin">
                    <h2 class="form-signin-heading">Please sign in</h2>
                    <input class="form-control mb-2" placeholder="Username"
                           type="text" id="username">
                    <input class="form-control mb-2" placeholder="Password"
                           type="password" id="password">
                    <input class="form-control mb-2" placeholder="Pin"
                           type="password" id="pin">

                    <div id="login_error" class="form-text" style="display:none;">
                        <span class="text-danger">
                            <strong>Login Failed: </strong><span></span>
                        </span>
                    </div>

                    <button class="btn btn-lg btn-primary"
                            type="button"
                            onclick="submitCredentials()">
                        Sign in
                    </button>
                </form>
            </div>

            <!-- Out‐of‐band Verifier Display -->
            <div id="oob_div">
            <c:if test="${not empty oauthOobResponse}">
                <h5>
                    Request token <e:forHtmlContent value='${oauthOobResponse.requestToken}' />
                    has verifier <e:forHtmlContent value='${oauthOobResponse.verifier}' />
                </h5>
            </c:if>
            </div>

            <!-- OAuth Scope Approval -->
            <div id="loggedin_div">
                <c:if test="${not empty oauthData}">
                    <form class="form-signin">
                        <h2 class="form-signin-heading">Welcome</h2>
                        <h4><span id="providerName"></span></h4>
                    </form>
                    <h5>
                        The 3rd party application
                        &quot;<e:forHtmlContent value='${oauthData.applicationName}' />&quot;
                        is requesting access to your OSCAR account.<br>
                        URL: <e:forHtmlContent value='${oauthData.applicationURI}' />.
                    </h5>
                    <h5>Permissions requested:</h5>
                    <form id="scopeForm" method="post"
                        action="${e:forHtmlAttribute(oauthData.replyTo)};jsessionid=${pageContext.session.id}">
                        <c:forEach var="perm" items="${oauthData.permissions}">
                            <div class="mb-3">
                            <div>
                                <div class="form-check">
                                <input type="checkbox" class="form-check-input" checked="checked" disabled="disabled">
                                <label class="form-check-label"><e:forHtmlContent value='${perm}' />
                                <c:if test="${empty fn:trim(perm)}">
                                    <em>(no description)</em>
                                </c:if>
                                </label>
                                </div>
                            </div>
                            </div>
                        </c:forEach>

                        <input type="hidden" name="session_authenticity_token"
                                value="<e:forHtmlAttribute value='${oauthData.authenticityToken}' />"/>

                        <input type="hidden" name="oauth_token" id="oauth_token"
                                value="<e:forHtmlAttribute value='${oauthData.oauthToken}' />"/>

                        <input type="hidden" name="oauthDecision" id="oauthDecision" value="allow"/>

                        <button type="submit" class="btn btn-primary">
                            Authorize <e:forHtmlContent value='${oauthData.applicationName}' />
                        </button>

                        <button type="button" class="btn btn-danger" onclick="deny();">
                            Cancel
                        </button>
                    </form>
                </c:if>
            </div>

        </div>
    </div>
</div>
</body>
</html>