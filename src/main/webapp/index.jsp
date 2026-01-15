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

--%>


<%@ page import="ca.openosp.openo.login.UAgentInfo" %>
<%@ page import="ca.openosp.openo.managers.MfaManager" %>
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

<jsp:useBean id="LoginResourceBean" beanName="ca.openosp.openo.login.LoginResourceBean" type="ca.openosp.openo.login.LoginResourceBean"/>
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

            function setfocus() {
                document.loginForm.username.focus();
                document.loginForm.username.select();
            }

            function popupPage(vheight, vwidth, varpage) {
                var page = "" + varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
                window.open(page, "gpl", windowprops);
            }

            function addStartTime() {
                document.getElementById("oneIdLogin").href += (Math.round(new Date().getTime() / 1000).toString());
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
                width: auto \9;
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

            .oneIdLogin {
                background-color: #000;
                width: 60%;
                height: 34px;
                margin: 0 auto;
            }

            .oneIdLogo {
                background-color: transparent;
                background: url("${pageContext.request.contextPath}/images/oneId/oneIDLogo.png");
                border: none;
                display: inline-block;
                float: left;
                vertical-align: bottom;
                width: 70px;
                height: 16px;
            }

            .oneIDText {
                display: inline-block;
                float: left;
                padding-left: 10px
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
                        <%--				    	<!-- Oscar logo -->--%>
                        <%--			        	<img title="OSCAR EMR Login" src="${pageContext.request.contextPath}/images/Logo.png"  alt="OSCAR EMR Login"--%>
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
                        <form action="login.do" method="POST">

                            <div class="form-group ${ login_error }">
                                <input type="text" name="username" placeholder="Enter your username"
                                       value="" size="15" maxlength="15" autocomplete="off"
                                       class="form-control" required/>
                            </div>

                            <div class="form-group ${ login_error }">
                                <input type="password" name="password" placeholder="Enter your password"
                                       value="" size="15" maxlength="32" autocomplete="off"
                                       class="form-control" required/>
                            </div>

							<% if (MfaManager.isOscarLegacyPinEnabled()) { %>
                            <c:if test="${not LoginResourceBean.ssoEnabled}">
                                <div class="form-group ${ login_error }">
                                    <input type="password" name="pin" placeholder="Enter your PIN" value=""
                                           size="15" maxlength="15" autocomplete="off" class="form-control"/>
                                    <span class="extrasmall">
										<fmt:setBundle basename="oscarResources"/><fmt:message key="loginApplication.formCmt"/>
									</span>
                                </div>
                            </c:if>
							<% } %>
                            <input type="hidden" id="oneIdKey" name="nameId" value="${ nameId }"/>
                            <input type="hidden" id="loginType" name="loginType" value=""/>
                            <input type=hidden name='propname'
                                   value='<fmt:setBundle basename="oscarResources"/><fmt:message key="loginApplication.propertyFile"/>'/>

                            <div id="buttonContainer">
                                <c:choose>
                                    <c:when test="${ isMobileDevice }">
                                        <input class="btn btn-oscar btn-primary btn-block" name="submit" id="fullSubmit"
                                               type="submit" onclick="enhancedOrClassic('C');" value="Full"/>
                                        <input class="btn btn-oscar btn-primary btn-block" name="submit"
                                               id="mobileSubmit" type="submit" onclick="enhancedOrClassic('C');"
                                               value="Mobile"/>
                                    </c:when>
                                    <c:otherwise>
                                        <input class="btn btn-oscar btn-primary btn-block" name="submit" type="submit"
                                               onclick="enhancedOrClassic('C');" value="Login"/>
                                    </c:otherwise>
                                </c:choose>
                            </div>

                        <form>

                        <oscar:oscarPropertiesCheck property="oneid.enabled" value="true" defaultVal="false">
                            <a href="${ LoginResourceBean.econsultURL }"
                               id="oneIdLogin" onclick="addStartTime()" class="btn btn-primary btn-block oneIDLogin">
                                <span class="oneIDLogo"></span>
                                <span class="oneIdText">
    									<fmt:setBundle basename="oscarResources"/><fmt:message key="loginApplication.oneid"/>
    								</span>
                            </a>
                        </oscar:oscarPropertiesCheck>

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
                    <img width="150px" src="${ pageContext.request.contextPath }/loginResource/supportLogo.png"
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
