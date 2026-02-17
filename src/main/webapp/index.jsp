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
        <%@ include file="/includes/global-head.jspf" %>
        <title>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="loginApplication.title"/>
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
                var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
                window.open(page, "gpl", windowprops);
            }

            function addStartTime() {
                document.getElementById("oneIdLogin").href += (Math.round(new Date().getTime() / 1000).toString());
            }

            function enhancedOrClassic(choice) {
                document.getElementById("loginType").value = choice;
            }
        </script>

        <style>
            body, html { height: 100%; }
            body {
                margin: 0;
                font-family: 'Roboto', Helvetica, Arial, sans-serif;
                font-size: 16px;
                display: flex;
                flex-direction: column;
            }
            .content { flex: 1 0 auto; }
            .heading, .loginContainer { text-align: center; }

            #clinic_logo { max-width: 400px; margin: 0 auto; }
            #clinic_name { margin-bottom: 0; }
            #clinic_text, #support_text, .topbar #buildInfo { color: grey; }

            .loginContainer { padding: 30px 15px; margin: 0 auto; }
            .loginContainer .card-header { margin: 0 auto; padding-top: 25px; padding-bottom: 10px; background: transparent; border: none; }
            .loginContainer .card-body { padding: 10px 40px 40px; }

            .btn-login {
                --bs-btn-bg: #53b848;
                --bs-btn-border-color: #3f9336;
                --bs-btn-hover-bg: #3f9336;
                --bs-btn-hover-border-color: #3f9336;
                --bs-btn-active-bg: #3f9336;
                --bs-btn-active-border-color: #3f9336;
                --bs-btn-color: #fff;
                --bs-btn-hover-color: #fff;
                --bs-btn-active-color: #fff;
            }

            .auaContainer { margin: 0 auto; text-align: left; margin-bottom: 30px; padding: 15px; }
            .auaContainer .card-header { font-size: small; text-align: center; }
            .auaContainer .card-body { font-size: x-small; }

            .powered { margin: 0 auto; }
            .powered .details { text-align: right; margin: 10px 20px 0 0; display: inline-table; width: 35%; }
            .support_details { text-align: left; width: 35%; display: inline-table; }
            .support_details div { font-size: smaller; text-align: center; }

            span#buildInfo { float: right; color: grey; font-size: x-small; position: absolute; right: 0; padding: 5px 10px 0 0; }
            span.extrasmall { font-size: x-small; }
            .extrasmall a { color: blue; }

            .topbar { height: 25px; }

            .oneIdLogin { background-color: #000; width: 60%; height: 34px; margin: 0 auto; }
            .oneIdLogo { background: url("${pageContext.request.contextPath}/images/oneId/oneIDLogo.png") transparent; border: none; display: inline-block; float: left; width: 70px; height: 16px; }
            .oneIDText { display: inline-block; float: left; padding-left: 10px; }

            footer { padding: 5px 10px; margin-top: 50px; color: grey; flex-shrink: 0; }
            footer a { color: blue; }

            @media (min-width: 450px) {
                .loginContainer, .powered, .auaContainer { width: 350px; }
                .loginContainer .card-header { width: 200px; }
                #clinic_logo { width: 400px; }
            }
            @media (min-width: 768px) {
                .loginContainer, .powered, .auaContainer { width: 450px; }
                .loginContainer .card-header { width: 300px; }
                #clinic_logo { width: 500px; }
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
            <div class="card">

                <div class="card-header">
                    <h2 id="default_logo" style="display:none;">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="loginApplication.formLabel"/>
                    </h2>
                </div>

                <c:if test='${ param.login eq "failed" }'>
                    <c:set var="login_error" value="is-invalid" scope="page"/>
                    <div class="alert alert-danger text-center mx-3 mt-3 mb-0">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="loginApplication.formFailedLabel"/>
                    </div>
                </c:if>

                <div class="card-body">
                    <div class="leftinput">
                        <%--
                            Autocomplete attribute strategy (WHATWG HTML spec):
                            - username: "off" — shared clinical workstations; prevent autofill of another provider's identity
                            - password: "current-password" — enable browser password manager save/fill per
                              NIST SP 800-63B (https://pages.nist.gov/800-63-3/sp800-63b.html) and
                              OWASP Authentication Cheat Sheet (https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
                              which recommend allowing password managers for stronger credential hygiene
                            - pin: "one-time-code" — signal browsers this is a session code, not a saveable credential
                        --%>
                        <form action="login.do" method="POST">

                            <div class="mb-3 ${ login_error }">
                                <input type="text" name="username" placeholder="Enter your username"
                                       value="" size="15" maxlength="15" autocomplete="off"
                                       class="form-control" required/>
                            </div>

                            <div class="mb-3 ${ login_error }">
                                <input type="password" name="password" placeholder="Enter your password"
                                       value="" size="15" maxlength="32" autocomplete="current-password"
                                       class="form-control" required/>
                            </div>

							<% if (MfaManager.isOscarLegacyPinEnabled()) { %>
                            <c:if test="${not LoginResourceBean.ssoEnabled}">
                                <div class="mb-3 ${ login_error }">
                                    <input type="text" name="pin" placeholder="Enter your PIN" value="" style="-webkit-text-security: disc;"
                                           size="15" maxlength="15" autocomplete="one-time-code"
                                           inputmode="numeric" class="form-control"/>
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
                                        <input class="btn btn-login d-block w-100 mb-2" name="submit" id="fullSubmit"
                                               type="submit" onclick="enhancedOrClassic('C');" value="Full"/>
                                        <input class="btn btn-login d-block w-100 mb-2" name="submit"
                                               id="mobileSubmit" type="submit" onclick="enhancedOrClassic('C');"
                                               value="Mobile"/>
                                    </c:when>
                                    <c:otherwise>
                                        <input class="btn btn-login d-block w-100 mb-2" name="submit" type="submit"
                                               onclick="enhancedOrClassic('C');" value="Login"/>
                                    </c:otherwise>
                                </c:choose>
                            </div>

                        <form>

                        <oscar:oscarPropertiesCheck property="oneid.enabled" value="true" defaultVal="false">
                            <a href="${ LoginResourceBean.econsultURL }"
                               id="oneIdLogin" onclick="addStartTime()" class="btn btn-login d-block w-100 oneIDLogin">
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
            <div class="card">
                <div class="card-header">
                    Acceptable Use Agreement
                </div>
                <div class="card-body">
                    <c:out value="${ LoginResourceBean.acceptableUseAgreementManager.text }" escapeXml="false"/>
                </div>
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
