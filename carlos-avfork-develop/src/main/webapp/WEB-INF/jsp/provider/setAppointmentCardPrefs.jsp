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

<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/casemgmt/taglibs.jsp" %>
<%@page import="java.util.*" %>
<%@ page import="java.util.ResourceBundle"%>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%
    if (session.getAttribute("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.htm");

    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());

    String providertitle = (String) request.getAttribute("providertitle");
    String providermsgPrefs = (String) request.getAttribute("providermsgPrefs");
    String providermsgProvider = (String) request.getAttribute("providermsgProvider");
    String providermsgEdit = (String) request.getAttribute("providermsgEdit");
    String providermsgSuccess = (String) request.getAttribute("providermsgSuccess");
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title><%=bundle.getString(providertitle)%></title>

        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/encounter/encounterStyles.css">
    </head>

    <body class="BodyStyle" vlink="#0000FF">

    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn">
                <%=bundle.getString(providermsgPrefs)%>
            </td>
            <td style="color: white" class="MainTableTopRowRightColumn">
                <%=bundle.getString(providermsgProvider)%>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn">&nbsp;</td>
            <td class="MainTableRightColumn">
                <%if (request.getAttribute("status") == null) {%>
                <%=bundle.getString(providermsgEdit)%>

                <form action="${pageContext.request.contextPath}/setProviderStaleDate" method="post">
                    <input type="hidden" name="method" value="${carlos:forHtmlAttribute(method)}">
                    <br/>
                    <fmt:message key="provider.appointmentCardPrefs.labelName"/> <input type="text" name="appointmentCardName.value" value="${carlos:forHtmlAttribute(name.value)}" size="50" />
                    <br/>
                    <fmt:message key="provider.appointmentCardPrefs.labelPhone"/> <input type="text" name="appointmentCardPhone.value" value="${carlos:forHtmlAttribute(phone.value)}" size="50" />
                    <br/>
                    <fmt:message key="provider.appointmentCardPrefs.labelFax"/> <input type="text" name="appointmentCardFax.value" value="${carlos:forHtmlAttribute(fax.value)}" size="50" />
                    <br/>
                    <input type="submit" name="btnApply" value="<fmt:message key='provider.setShowPatientDOB.btnApply'/>" />
                </form>

                <%} else {%>
                <%=bundle.getString(providermsgSuccess)%> <br>
                <%}%>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>
    </body>
</html>
