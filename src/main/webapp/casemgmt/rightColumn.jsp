<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>


<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ include file="/casemgmt/taglibs.jsp" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.*" %>

<%
    String demo = request.getParameter("demographicNo");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>

<html>
<head>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/oscarClientManagement/profilePicture.js"></script>
</head>
<body>
    <!-- Sets the profile picture of a loaded case -->
    <!-- "roleName$" is the correct name for this role, see other jsp pages for confirmation -->
    <security:oscarSec roleName="<%=roleName$%>" objectName="_newCasemgmt.photo" rights="r">
        <c:choose>
            <c:when test="${not empty requestScope.image_exists}">
                <c:set var="clientId" value="${demographicNo}"></c:set>
                <img style="cursor: pointer;" id="ci"
                    src="${pageContext.request.contextPath}/imageRenderingServlet?source=local_client&amp;clientId=${demographicNo}" alt="id_photo" height="100"
                    title="Click to upload a new photo."
                onClick="popupUploadPage('${pageContext.request.contextPath}/casemgmt/uploadimage.jsp', ${demographicNo}); return false;"/>
            </c:when>
            <c:otherwise>
                <svg xmlns="http://www.w3.org/2000/svg" width="80" height="100" viewBox="0 0 80 100"
                     style="cursor: pointer; background: #e9ecef; border-radius: 4px;"
                     title="Click to upload a new photo."
                     onclick="popupUploadPage('${pageContext.request.contextPath}/casemgmt/uploadimage.jsp', ${demographicNo}); return false;">
                    <circle cx="40" cy="32" r="16" fill="#adb5bd"/>
                    <ellipse cx="40" cy="82" rx="28" ry="22" fill="#adb5bd"/>
                </svg>
            </c:otherwise>
        </c:choose>
    </security:oscarSec>

    <div id="rightColLoader" style="width: 100%;">
        <h3 style="width: 100%; background-color: #CCCCFF;">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.LeftNavBar.msgLoading"/></h3>
    </div>
</body>
</html>
