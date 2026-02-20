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


<%@ page import="io.github.carlos_emr.carlos.login.UAgentInfo" %>
<%@ page import="io.github.carlos_emr.carlos.managers.MfaManager" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri='http://java.sun.com/jsp/jstl/core' prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" session="false" %>

<%
    // detect if mobile device.
    String userAgent = request.getHeader("User-Agent");
    String accept = request.getHeader("Accept");
    UAgentInfo userAgentInfo = new UAgentInfo(userAgent, accept);
    boolean isMobileDevice = userAgentInfo.detectMobileQuick();
    pageContext.setAttribute("isMobileDevice", isMobileDevice);
%>

<jsp:useBean id="LoginResourceBean" beanName="io.github.carlos_emr.carlos.login.LoginResourceBean" type="io.github.carlos_emr.carlos.login.LoginResourceBean"/>
<c:set var="login_error" value="" scope="page"/>
<!DOCTYPE html>
<html>

    <head>
        <title>
                <%--	    <c:choose>--%>
                <%--	    	<c:when test="${ not empty LoginResourceBean.tabName }">--%>
                <%--	    		<c:out value="${ LoginResourceBean.tabName }" />--%>
                <%--	    	</c:when>--%>
                <%--	    	<c:otherwise>--%>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="loginApplication.title"/>
                <%--	    	</c:otherwise>--%>
                <%--	    </c:choose>--%>
        </title>

        <link rel="icon" href="${pageContext.request.contextPath}/images/Oscar.ico"/>
        <link href="${pageContext.request.contextPath}/css/Roboto.css" rel='stylesheet' type='text/css'/>
        <script type="text/javascript">

            function showHideItem(id) {
                if (document.getElementById(id).style.display === 'none') {
                    document.getElementById(id).style.display = 'block';
                } else {
                    document.getElementById(id).style.display = 'none';
                }
            }

            function togglepwd(){
                const passwordInput = document.getElementById('password');
                var isNowVisible = passwordInput.type === 'text';
                const toggleBtn = document.getElementById('toggleBtn');
                toggleBtn.setAttribute('aria-pressed', isNowVisible ? 'true' : 'false');
                toggleBtn.setAttribute('aria-label', isNowVisible ? 'Hide password' : 'Show password');
                const isPassword = passwordInput.type === 'password';
                passwordInput.type = isPassword ? 'text' : 'password';

            }

            function togglepin(button){
                const pinInput = document.getElementById('pin');
                // Toggle the custom security class
                pinInput.classList.toggle('secure-text');
                const toggleBtn = document.getElementById('togglePin');
                const isNowVisible = !(pinInput.classList.contains("secure-text"));
                toggleBtn.setAttribute('aria-pressed', isNowVisible ? 'true' : 'false');
                toggleBtn.setAttribute('aria-label', isNowVisible ? 'Hide password' : 'Show password');
            }

            function setfocus() {
                document.loginForm.username.focus();
                document.loginForm.username.select();
            }

            function popupPage(vheight, vwidth, varpage) {
                var page = "" + varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
                window.open(page, "gpl", windowprops);
            }

            function enhancedOrClassic(choice) {
                document.getElementById("loginType").value = choice;
            }
        </script>

        <style media="all">
            body, html {
                height: 100%;
            }

            body {
                margin: 0;
                font-family: 'Roboto', Helvetica, Arial, sans-serif;
                font-size: 16px;
                color: #333333;
                background-color: #ffffff;
                display: flex;
                flex-direction: column;
            }

            * {
                -webkit-box-sizing: border-box;
                -moz-box-sizing: border-box;
                box-sizing: border-box;
            }

            .content {
                flex: 1 0 auto;
            }

            .extrasmall a {
                color: blue;
            }

            a {
                text-decoration: none;
                border: none;
                padding: 0;
                margin: 0;
                color: black;
            }

            img {
                max-width: 100%;
                height: auto;
                width: auto;
            }

            #clinic_logo {
                max-width: 400px;
                margin: 0 auto;
            }

            .loginContainer .panel-heading #oscar_logo {
                max-width: 300px;
                margin: 0 auto;
            }

            .loginContainer .panel-heading {
                margin: 0 auto;
                padding-top: 25px;
                padding-bottom: 10px;
            }

            h1 {
                font-size: 38px;
                font-weight: 300;
            }

            button, input, optgroup, select, textarea {
                margin: 0;
                font: inherit;
                color: inherit;
            }

            input {
                line-height: normal;
            }

            button, input, select, textarea {
                font-family: inherit;
                font-size: inherit;
                line-height: inherit;
            }

            .heading, .loginContainer {
                text-align: center;
            }

            .powered {
                margin-right: auto;
                margin-left: auto;
            }

            .powered .details {
                text-align: right;
                margin: 10px 20px 0 0;
                display: inline-table;
                width: 35%;
            }

            .loginContainer {
                padding: 30px 15px;
                margin-right: auto;
                margin-left: auto;
            }

            .auaContainer {
                margin: 0 auto;
			text-align: left;
                z-index: 3;
                margin-bottom: 30px;
                padding: 15px;
            }

            .auaContainer .panel {
                padding: 10px;
            }

            .auaContainer .panel-heading {
                font-size: small;
			text-align: center;
            }

            .auaContainer .panel-body {
                font-size: x-small;
            }

            .panel {
                background-color: #fff;
                border: 1px solid transparent;
                border-radius: 4px;
                -webkit-box-shadow: 0 1px 1px rgba(0, 0, 0, .05);
                box-shadow: 0 1px 1px rgba(0, 0, 0, .05);
            }

            .panel-body {
                padding: 10px 40px 40px;
            }

            .panel-danger > .panel-heading {
                color: #a94442;
                background-color: #f2dede;
                border-color: #ebccd1;
            }

            .panel-danger > .panel-heading + .panel-collapse > .panel-body {
                border-top-color: #ebccd1;
            }

            .panel-danger > .panel-heading .badge {
                color: #f2dede;
                background-color: #a94442;
            }

            .panel-danger > .panel-footer + .panel-collapse > .panel-body {
                border-bottom-color: #ebccd1;
            }

            .panel-default {
                border-color: #ddd;
            }

            .form-group {
                margin-bottom: 15px;
            }

            .form-control {
                display: block;
                width: 100%;
                height: 34px;
                padding: 6px 12px;
                font-size: 14px;
                line-height: 1.42857143;
                color: #555;
                background-color: #fff;
                background-image: none;
                border: 1px solid #ccc;
                border-radius: 4px;
                -webkit-box-shadow: inset 0 1px 1px rgba(0, 0, 0, .075);
                box-shadow: inset 0 1px 1px rgba(0, 0, 0, .075);
                -webkit-transition: border-color ease-in-out .15s, -webkit-box-shadow ease-in-out .15s;
                -o-transition: border-color ease-in-out .15s, box-shadow ease-in-out .15s;
                transition: border-color ease-in-out .15s, box-shadow ease-in-out .15s;
            }

            .has-error .form-control {
                border-color: #a94442;
                -webkit-box-shadow: inset 0 1px 1px rgba(0, 0, 0, .075);
                box-shadow: inset 0 1px 1px rgba(0, 0, 0, .075);
            }

            .alert {
                color: #a94442;
            }

            .btn {
                display: inline-block;
                padding: 6px 12px;
                margin-bottom: 0;
                font-size: 14px;
                font-weight: 400;
                line-height: 1.42857143;
                text-align: center;
                white-space: nowrap;
                vertical-align: middle;
                -ms-touch-action: manipulation;
                touch-action: manipulation;
                cursor: pointer;
                -webkit-user-select: none;
                -moz-user-select: none;
                -ms-user-select: none;
                user-select: none;
                background-image: none;
                border: 1px solid transparent;
                border-radius: 4px;
            }

            .btn-primary {
                color: #fff;
                background-color: #53b848;
                border-color: #3f9336;
            }

            .btn-block {
                display: block;
                width: 100%;
            }

            button, html input[type=button], input[type=reset], input[type=submit] {
                -webkit-appearance: button;
                cursor: pointer;
            }

            .btn.active.focus, .btn.active:focus, .btn.focus, .btn:active.focus,
            .btn:active:focus, .btn:focus {
                outline: 5px auto -webkit-focus-ring-color;
                outline-offset: -2px;
            }

            .btn.focus, .btn:focus, .btn:hover {
                color: #333;
                text-decoration: none;
            }

            .btn.active, .btn:active {
                background-image: none;
                outline: 0;
                -webkit-box-shadow: inset 0 3px 5px rgba(0, 0, 0, .125);
                box-shadow: inset 0 3px 5px rgba(0, 0, 0, .125);
            }

            .btn[disabled="disabled"]:hover {
                cursor: not-allowed;
            }

            .btn-primary.focus, .btn-primary:focus {
                color: #fff;
                background-color: #3f9336;
                border-color: #3f9336;
            }

            .btn-primary:hover {
                color: #fff;
                background-color: #3f9336;
                border-color: #3f9336;
            }

            .btn-primary.active, .btn-primary:active, .open > .dropdown-toggle.btn-primary {
                color: #fff;
                background-color: #3f9336;
                border-color: #3f9336;
            }

            .btn-primary.active, .btn-primary:active, .open > .dropdown-toggle.btn-primary {
                background-image: none;
            }

            input[type=button].btn-block, input[type=reset].btn-block, input[type=submit].btn-block {
                width: 100%;
                margin-bottom: 10px;
            }

            .btn.active.focus, .btn.active:focus, .btn.focus, .btn:active.focus,
            .btn:active:focus, .btn:focus {
                outline: 5px auto -webkit-focus-ring-color;
                outline-offset: -2px;
            }

            .btn-primary.active.focus, .btn-primary.active:focus, .btn-primary.active:hover,
            .btn-primary:active.focus, .btn-primary:active:focus, .btn-primary:active:hover,
            .open > .dropdown-toggle.btn-primary.focus, .open > .dropdown-toggle.btn-primary:focus,
            .open > .dropdown-toggle.btn-primary:hover {
                color: #fff;
                background-color: #3f9336;
                border-color: #3f9336;
            }

            span#buildInfo {
                float: right;
                color: grey;
                font-size: x-small;
                text-align: right;
                position: absolute;
                right: 0;
                padding-top: 5px;
                padding-right: 10px;
            }

            span.extrasmall {
                font-size: x-small;
            }

            .clinic-text {
                display: inline;
                font-weight: 400;
            }

            @media ( min-width: 450px) {
                .loginContainer, .powered, .auaContainer {
                    width: 350px;
                }

                .loginContainer .panel-heading {
                    width: 200px;
                }

                #clinic_logo {
                    width: 400px;
                    margin: 0 auto;
                }

            }

            @media ( min-width: 768px) {
                .loginContainer, .powered, .auaContainer {
                    width: 450px;
                }

                .loginContainer .panel-heading {
                    width: 300px;
                }

                #clinic_logo {
                    width: 500px;
                    margin: 0 auto;
                }
            }

            @media ( min-width: 992px) {
            }

            @media ( min-width: 1200px) {
            }

            footer {
                padding: 5px 10px;
                margin-top: 50px;
                color: grey;
                position: sticky;
                flex-shrink: 0;
            }

            footer a {
                color: blue;
            }

            .topbar {
                height: 25px;
            }

            #clinic_name {
                margin-bottom: 0;
            }

            #clinic_text, #support_text, .topbar #buildInfo {
                color: grey;
            }

            .support_details {
                text-align: left;
                width: 35%;
                display: inline-table;
            }

            .support_details div {
                font-size: smaller;
                text-align: center;
            }

            /* Hide the default browser eye in Edge */
            #password::-ms-reveal,
            #password::-ms-clear {
              display: none;
            }
            .secure-text { -webkit-text-security: disc; }

            .input-wrapper { position: relative; display: inline-block; margin-bottom: 15px; width: 100%; }
            .toggle-input { width: 100%; padding-right: 35px; box-sizing: border-box; }

            .toggle-btn {
              position: absolute; right: 10px; top: 17px; transform: translateY(-50%);
              width: 26px; height: 26px; border: none;
              cursor: pointer;
            }

            /* Svg for "Hidden" state (Class-based OR Type-based) */
            .toggle-input.secure-text + .toggle-btn,
            .toggle-input[type="password"] + .toggle-btn {
              display: inline-block;
              width: 24px; /* 24px+ for click area WCAG SC 2.5.8 AA */
              height: 24px;
              background-color: #959595; /* 3:1 contrast ratio as per WCAG, alternately use currentColor; to match text color */
              -webkit-mask-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="black" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>');
              mask-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="black" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>');
              mask-size: contain;
              -webkit-mask-repeat: no-repeat;
              mask-repeat: no-repeat;
              mask-position: center;
            }

            /* Svg for "Visible" state */
            .toggle-input:not(.secure-text):not([type="password"]) + .toggle-btn {
            display: inline-block;
              width: 24px; /* NO SMALLER for best practice */
              height: 24px;
              background-color: #959595; /* lightest grey that gives 3:1 contrast ratio */
              -webkit-mask-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="black" stroke-width="2"><line x1="0" y1="24" x2="24" y2="0" /><line x1="0" y1="24" x2="24" y2="0" /></svg>');
              mask-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="black" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/><line x1="0" y1="24" x2="24" y2="0" /></svg>');
              mask-size: contain;
              -webkit-mask-repeat: no-repeat;
              mask-repeat: no-repeat;
              mask-position: center;
            }
        </style>

    </head>

    <body onLoad="setfocus()">
    <div class="content">
        <div class="topbar">
            <span id="buildInfo">
            	<c:out value="${ LoginResourceBean.buildTag }"/>
            </span>
        </div>

        <div class="heading">

            <!-- Clinic logo  -->
            <div id="clinic_logo">
                <a target="_blank" href="${ LoginResourceBean.clinicLink }">
                    <img src="${ pageContext.request.contextPath }/loginResource/clinicLogo.png"
                         alt="${ LoginResourceBean.clinicLink }"
                         onerror="this.style.display='none'"/>
                </a>
            </div>

            <!-- Clinic info -->
            <div id="clinic_text">
                <h2 id="clinic_name">
                    <a target="_blank" href="${ LoginResourceBean.clinicLink }">
                        <c:out value="${ LoginResourceBean.clinicName }"/>
                    </a>
                </h2>
                <div id="clinic_address">
                    <c:out value="${ LoginResourceBean.clinicText }" escapeXml="false"/>
                </div>
            </div>

        </div>

        <div class="loginContainer">
            <div class="panel panel-default">

                <div class="panel-heading">

                        <%--			    	<div id="oscar_logo">--%>
                        <%--				    	<!-- EMR logo -->--%>
                        <%--			        	<img title="EMR Login" src="${pageContext.request.contextPath}/images/Logo.png"  alt="EMR Login"--%>
                        <%--			        		onerror="document.getElementById('default_logo').style.display='block'; this.style.display='none'; " />--%>
                        <%--		        	</div>--%>

                    <!-- default text if logo is missing -->
                    <h2 id="default_logo" style="display:none;">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="loginApplication.formLabel"/>
                    </h2>
                </div>

                <c:if test='${ param.login eq "failed" }'>
                    <c:set var="login_error" value="has-error" scope="page"/>
                    <div class="alert">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="loginApplication.formFailedLabel"/>
                    </div>
                </c:if>

                <div class="panel-body">
                    <div class="leftinput">
                        <%--
                            Autocomplete attribute strategy (WHATWG HTML spec):
                            - username: "off" \u2014 shared clinical workstations; prevent autofill of another provider's identity
                            - password: "current-password" \u2014 enable browser password manager save/fill per
                              NIST SP 800-63B (https://pages.nist.gov/800-63-3/sp800-63b.html) and
                              OWASP Authentication Cheat Sheet (https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
                              which recommend allowing password managers for stronger credential hygiene
                            - pin: "one-time-code" \u2014 signal browsers this is a session code, not a saveable credential
                        --%>
                        <form action="login.do" method="POST" name="loginForm">
<input type="hidden" name="${empty _csrf.parameterName ? 'none' : _csrf.parameterName}" value="${_csrf.token}">

                            <div class="form-group ${ login_error }">
                                <input type="text" name="username" id="username" placeholder="<fmt:setBundle basename="oscarResources"/><fmt:message key="Logon.userName"/>"
                                       value="" size="15" maxlength="15" autocomplete="off"
                                       class="form-control" required>
                            </div>

                            <div class="input-wrapper form-group ${ login_error }">
                              <input type="password" name="password" id="password" placeholder="<fmt:message key="Logon.passWord"/>" autocomplete="current-password" class="form-control toggle-input" required>
                              <button type="button"
                                      class="toggle-btn" 
                                      aria-label="Show Password"
                                      aria-pressed="false
                                      onclick="togglepwd();" >
                              </button>
                            </div>

							<% if (MfaManager.isOscarLegacyPinEnabled()) { %>
                            <div class="pin-wrapper">
                                <div class="input-wrapper form-group ${ login_error }">
                                  <!-- The input starts with the secure-text class -->
                                  <input type="text" id="pin" class="form-control secure-text toggle-input" name="pin" autocomplete="one-time-code"
                                               inputmode="numeric"  placeholder="<fmt:message key="admin.securityrecord.formPIN"/>">
                                    <button type="button" 
                                      class="toggle-btn" 
                                      aria-label="Show PIN"
                                      aria-pressed="false"
                                      onclick="togglepin();">
                                    </button>
                                    <span class="extrasmall">
										    <fmt:message key="loginApplication.formCmt"/>
                                    </span>
                                </div>
                            </div>
							<% } %>
                            <input type="hidden" id="loginType" name="loginType" value="">
                            <input type=hidden name='propname'
                                   value='<fmt:message key="loginApplication.propertyFile"/>'>

                            <div id="buttonContainer">
                                <c:choose>
                                    <c:when test="${ isMobileDevice }">
                                        <input class="btn btn-oscar btn-primary btn-block" name="submit" id="fullSubmit"
                                               type="submit" onclick="enhancedOrClassic('C');" value="Full">
                                        <input class="btn btn-oscar btn-primary btn-block" name="submit"
                                               id="mobileSubmit" type="submit" onclick="enhancedOrClassic('C');"
                                               value="Mobile">
                                    </c:when>
                                    <c:otherwise>
                                        <input class="btn btn-oscar btn-primary btn-block" name="submit" type="submit"
                                               onclick="enhancedOrClassic('C');" value="<fmt:message key="index.btnSignIn"/>">
                                    </c:otherwise>
                                </c:choose>
                            </div>

                        </form>

                        <c:if test="${ LoginResourceBean.acceptableUseAgreementManager.auaAvailable }">
    			            <span class="extrasmall">
	                        	<fmt:setBundle basename="oscarResources"/><fmt:message key="global.aua"/> &nbsp;
	                        	<a href="javascript:void(0);" onclick="showHideItem('auaText');">
	                        		<fmt:setBundle basename="oscarResources"/><fmt:message key="global.showhide"/>
	                        	</a>
	                        </span>
                        </c:if>
                    </div>
                </div>
            </div>
        </div>

        <div id="auaText" class="auaContainer" style="display:none;">
            <div class="panel panel-default">
                <div class="panel-heading">
                    Acceptable Use Agreement
                </div>
                <div class="panel-body">
                    <c:out value="${ LoginResourceBean.acceptableUseAgreementManager.text }" escapeXml="false"/>
                </div>
                <div class="panel-footer"></div>
            </div>
        </div>


        <c:if test="${ LoginResourceBean.acceptableUseAgreementManager.auaAvailable and LoginResourceBean.acceptableUseAgreementManager.alwaysShow }">
            <script type="text/javascript">document.getElementById('auaText').style.display = 'block';</script>
        </c:if>

        <div class="powered">
            <c:if test="${ not empty LoginResourceBean.supportLink
								or not empty LoginResourceBean.supportName
								or not empty LoginResourceBean.supportText }">
                <div class="details">
                    <div>Powered</div>
                    <div>by</div>
                </div>
            </c:if>
            <div class="support_details">
                <a target="_blank" href="${ LoginResourceBean.supportLink }" id="supportImageLink">
                    <img width="150" src="${ pageContext.request.contextPath }/loginResource/supportLogo.png"
                         alt="Support Image"
                         onerror="this.style.display='none'; document.getElementById('supportImageLink').style.display='none';">
                </a>
                <c:if test="${ not empty LoginResourceBean.supportName }">
                    <div id="support_name">
                        <a target="_blank" href="${ LoginResourceBean.supportLink }">
                            <c:out value="${ LoginResourceBean.supportName }"/>
                        </a>
                    </div>
                </c:if>
                <div id="support_text">
                    <c:out value="${ LoginResourceBean.supportText }" escapeXml="false"/>
                </div>
            </div>
        </div>

    </div>
    <footer>
     	<span id="license" class="extrasmall">
     		<fmt:setBundle basename="oscarResources"/><fmt:message key="loginApplication.leftRmk2"/>
     	</span>
    </footer>

    </body>
    <script type="text/javascript" src="${pageContext.request.contextPath}/csrfguard"></script>
</html>
