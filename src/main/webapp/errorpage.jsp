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
<!-- only true can access exception object -->
<%@ taglib uri='jakarta.tags.core' prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<fmt:setBundle basename="oscarResources"/>
<fmt:message key="messenger.config.MessengerAdmin.goBack" var="btnBackTitle"/>
<fmt:message key="provider.appointmentProviderAdminDay.schedView" var="btnExitTitle"/>
<jsp:useBean id="LoginResourceBean" beanName="io.github.carlos_emr.carlos.login.LoginResourceBean" type="io.github.carlos_emr.carlos.login.LoginResourceBean"/>
<!DOCTYPE html>
<html>

<head>
    <title>
        <fmt:message key="error.description"/>
    </title>
    <link rel="shortcut icon" href="${e:forUri(pageContext.request.contextPath)}/images/favicon.ico"/>
    <link rel="stylesheet" href="${e:forUri(pageContext.request.contextPath)}/library/bootstrap/5.3.3/css/bootstrap.min.css"/>

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
        <p>CARLOS Error: ${e:forHtml(pageContext.errorData.statusCode)}</p>

        <div id="navigation">
            <a class="btn btn-secondary float-start" title="${e:forHtmlAttribute(btnBackTitle)}"
               href="#" onclick="window.history.back();" role="button"><fmt:message key="global.btnBack"/></a>
            <a class="btn btn-secondary float-end" title="${e:forHtmlAttribute(btnExitTitle)}"
               href="${e:forUri(pageContext.request.contextPath)}/provider/providercontrol.jsp" role="button"><fmt:message key="global.btnExit"/></a>
        </div>
    </div>

    <c:if test="${ not empty LoginResourceBean.supportLink
							or not empty LoginResourceBean.supportName
							or not empty LoginResourceBean.supportText }">
        <div id="support">
            <div class="support_details">
                <a target="_blank" href="${e:forHtmlAttribute(LoginResourceBean.supportLink)}" id="supportImageLink">
                    <img width="150px" alt="${e:forHtmlAttribute(LoginResourceBean.supportName)}"
                         style="display:none;">
                </a>
                <c:if test="${ not empty LoginResourceBean.supportName }">
                    <div id="support_name">
                        <a target="_blank" href="${e:forHtmlAttribute(LoginResourceBean.supportLink)}">
                            ${e:forHtml(LoginResourceBean.supportName)}
                        </a>
                    </div>
                </c:if>
                <div id="support_text">
                    <c:out value="${ LoginResourceBean.supportText }" escapeXml="false"/>
                </div>
            </div>
        </div>
    </c:if>

</div>
</body>
</html>
