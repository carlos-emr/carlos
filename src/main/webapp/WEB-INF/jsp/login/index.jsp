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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri='jakarta.tags.core' prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="carlos" prefix="carlos" %>
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
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">

    <head>
        <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
        <title>
            <fmt:message key="loginApplication.title"/>
        </title>

        <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <link href="${pageContext.request.contextPath}/css/Roboto.css" rel='stylesheet' type='text/css'/>
        <%@ include file="/WEB-INF/jsp/login/includes/login-scripts.jspf" %>
        <%@ include file="/WEB-INF/jsp/login/includes/login-styles.jspf" %>
    </head>

    <body onLoad="setfocus()">

    <div class="content">
        <%@ include file="/WEB-INF/jsp/login/includes/login-branding.jspf" %>
        <%@ include file="/WEB-INF/jsp/login/includes/login-form.jspf" %>
        <%@ include file="/WEB-INF/jsp/login/includes/login-aua.jspf" %>
        <%@ include file="/WEB-INF/jsp/login/includes/login-support.jspf" %>
    </div>
    <%@ include file="/WEB-INF/jsp/login/includes/login-footer.jspf" %>

    </body>
</html>
