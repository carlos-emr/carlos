<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Page role: Renders `errorpage.jsp` for the error handling workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%--
  Purpose: Displays a CARLOS-branded error page for unhandled HTTP and server errors.
  Features:
    - Internationalized messages via oscarResources bundle
    - Displays HTTP error status code with CARLOS branding
    - Navigation actions: Back (browser history) and Exit (main schedule)
    - Optional support contact information from LoginResourceBean
  Parameters:
    - pageContext.errorData.statusCode: HTTP error status code forwarded by the container
    - LoginResourceBean.supportLink: URL for support contact link (optional)
    - LoginResourceBean.supportName: Support contact display name (optional)
    - LoginResourceBean.supportText: Support contact descriptive text or HTML (optional)
  @since 2026-03
--%>
<%@ page isErrorPage="true" %>
<%-- Log the captured exception so JSP-render failures don't disappear into a
     generic "CARLOS Error" page with nothing in catalina.out. The logic
     lives in ErrorPageLogger so it can be unit-tested without a JSP
     container; this scriptlet is a 1-line dispatcher. --%>
<% io.github.carlos_emr.carlos.utility.ErrorPageLogger.logIfPresent(exception, request); %>
<!-- only true can access exception object -->
<%--
  DISPLAY_ERROR — developer mode block.
  When carlos.properties sets DISPLAY_ERROR=true, render the developer detail block
  in the browser to aid local debugging. To allow raw exception details through
  ResponseSanitizationFilter, also set response.sanitization.enabled=false.
  DISPLAY_ERROR alone does not disable sanitization.
  SECURITY: this block MUST remain inactive in all production and PHI environments.
--%>
<%
    boolean _displayError = io.github.carlos_emr.CarlosProperties.getInstance()
            .isPropertyActive("DISPLAY_ERROR");
    request.setAttribute("_displayError", _displayError);
    if (_displayError) {
        Throwable _t = exception;
        if (_t == null) {
            Object _errAttr = request.getAttribute("jakarta.servlet.error.exception");
            if (_errAttr instanceof Throwable) {
                _t = (Throwable) _errAttr;
            }
        }
        if (_t != null) {
            java.io.StringWriter _sw = new java.io.StringWriter();
            _t.printStackTrace(new java.io.PrintWriter(_sw));
            request.setAttribute("_exceptionType", _t.getClass().getName());
            request.setAttribute("_exceptionMessage", _t.getMessage());
            request.setAttribute("_exceptionTrace", _sw.toString());
        }
        Object _errMsg = request.getAttribute("jakarta.servlet.error.message");
        if (_errMsg != null) {
            request.setAttribute("_errorMessage", _errMsg.toString());
        }
    }
%>
<%@ taglib uri='jakarta.tags.core' prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:message key="messenger.config.MessengerAdmin.goBack" var="btnBackTitle"/>
<fmt:message key="provider.appointmentProviderAdminDay.schedView" var="btnExitTitle"/>
<jsp:useBean id="LoginResourceBean" beanName="io.github.carlos_emr.carlos.login.LoginResourceBean" type="io.github.carlos_emr.carlos.login.LoginResourceBean"/>
<!DOCTYPE html>
<html>

<head>
    <title>
        <fmt:message key="error.description"/>
    </title>
    <link rel="shortcut icon" href="${carlos:forUri(pageContext.request.contextPath)}/images/favicon.ico"/>
    <link rel="stylesheet" href="${carlos:forUri(pageContext.request.contextPath)}/library/bootstrap/5.3.8/css/bootstrap.min.css"/>

    <style media="all">

        body {
            background-color: #F5F5F5;
            margin: 0px;
            padding: 0px;
            top: 0;
            left: 0;
        }

        h2 {
            color: #00293c;
            font-size: larger;
            text-align: center;
        }

        p {
            text-align: center;
        }

        .support_details {
            width: 100%;
            text-align: center;
            margin-top: 50px;
        }

        #heading, #container #error-code, #support {
            margin-bottom: 50px;
        }

        #heading, #container #error-code {
            text-align: center;
            font-size: 20px;
            color: #707070;
        }

        #heading span {
            vertical-align: middle;
            font-size: 40px;
            color: #909090;
        }

        #support_text {
            color: grey;
        }

        #container {
            text-align: center;
            margin: auto 150px;
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

        #support {
            width: 450px;
        }

        #support {
            margin-right: auto;
            margin-left: auto;
        }

        #support .support_details {
            text-align: right;
            margin: 10px 20px 0 0;
            display: inline-table;
            clear: both;
        }

    </style>
</head>

<body>
<div id="heading">
    <span><fmt:message key="global.msgSomethingWrong"/></span>
</div>

<div id="container">
    <div id="error-code">
        <h2><fmt:message key="error.msgException"/>:</h2>
        <p>CARLOS Error: ${carlos:forHtml(pageContext.errorData.statusCode)}</p>

        <div id="navigation">
            <a class="btn btn-secondary float-start" title="${carlos:forHtmlAttribute(btnBackTitle)}"
               href="#" onclick="window.history.back();" role="button"><fmt:message key="global.btnBack"/></a>
            <a class="btn btn-secondary float-end" title="${carlos:forHtmlAttribute(btnExitTitle)}"
               href="${carlos:forUri(pageContext.request.contextPath)}/provider/providercontrol" role="button"><fmt:message key="global.btnExit"/></a>
        </div>
    </div>

    <%-- Developer error detail — visible only when DISPLAY_ERROR=true in carlos.properties.
         SECURITY RISK: never enable this in production or any PHI environment. --%>
    <c:if test="${_displayError}">
        <div id="dev-error-detail" style="margin: 30px 0; text-align: left;
             background: #fff8f0; border: 2px solid #cc4400; border-radius: 4px; padding: 16px;">
            <h3 style="color: #cc4400; margin-top: 0; font-family: monospace;">
                Developer Detail (DISPLAY_ERROR=true — not for production)
            </h3>
            <c:choose>
                <c:when test="${not empty _exceptionType}">
                    <p style="font-family: monospace;">
                        <strong>Exception:</strong> <carlos:encode value="${_exceptionType}"/>
                    </p>
                    <c:if test="${not empty _exceptionMessage}">
                        <p style="font-family: monospace;">
                            <strong>Message:</strong> <carlos:encode value="${_exceptionMessage}"/>
                        </p>
                    </c:if>
                    <pre style="background: #f8f8f8; padding: 10px; overflow: auto;
                         max-height: 500px; white-space: pre-wrap; word-break: break-all;
                         font-size: 0.85em;"><carlos:encode value="${_exceptionTrace}"/></pre>
                </c:when>
                <c:otherwise>
                    <p style="font-family: monospace;">
                        No exception details available — the error was reported via
                        <code>sendError()</code> without propagating the exception object.
                        <c:if test="${not empty _errorMessage}">
                            <br/><strong>Error message:</strong>
                            <carlos:encode value="${_errorMessage}"/>
                        </c:if>
                    </p>
                    <p style="font-family: monospace; color: #666;">
                        To locate the root cause, check the application log for WARN entries
                        from ErrorPageLogger matching this URI and status code.
                    </p>
                </c:otherwise>
            </c:choose>
        </div>
    </c:if>

    <c:if test="${ not empty LoginResourceBean.supportLink
							or not empty LoginResourceBean.supportName
							or not empty LoginResourceBean.supportText }">
        <div id="support">
            <div class="support_details">
                <a target="_blank" href="${carlos:forHtmlAttribute(LoginResourceBean.supportLink)}" id="supportImageLink">
                    <img width="150px" src="${carlos:forUri(pageContext.request.contextPath)}/loginResource/supportLogo.png"
                         alt="${carlos:forHtmlAttribute(LoginResourceBean.supportName)}"
                         onerror="this.style.display='none'; document.getElementById('supportImageLink').style.display='none';">
                </a>
                <c:if test="${ not empty LoginResourceBean.supportName }">
                    <div id="support_name">
                        <a target="_blank" href="${carlos:forHtmlAttribute(LoginResourceBean.supportLink)}">
                            ${carlos:forHtml(LoginResourceBean.supportName)}
                        </a>
                    </div>
                </c:if>
                <div id="support_text">
                    <%-- supportText is deployment-configured text and must be HTML-encoded before rendering in the page body. --%>
                    ${carlos:forHtml(LoginResourceBean.supportText)}
                </div>
            </div>
        </div>
    </c:if>

</div>
</body>
</html>
