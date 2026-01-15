<%--

   Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
   This software is published under the GPL GNU General Public License.
   This program is free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation; either version 2
   of the License, or (at your option) any later version.
   <p>
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.
   <p>
   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
   <p>
   This software was written for
   Centre for Research on Inner City Health, St. Michael's Hospital,
   Toronto, Ontario, Canada

--%>

<%
    //Make sure user has logged in first and username is in the session
    if (session.getAttribute("userName") == null) {
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
    }
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><fmt:message key="loginApplication.title"/></title>

    <c:set var="ctx" value="${ pageContext.request.contextPath }" scope="page"/>
    <link rel="stylesheet" href="${ctx}/library/bootstrap/5.0.2/css/bootstrap.min.css" type="text/css"/>
    <script type="text/javascript" src="${ctx}/library/bootstrap/5.0.2/js/bootstrap.min.js"></script>

</head>
<body class="bg-light">

<div class="container-lg d-flex justify-content-center align-items-center mt-0 mt-lg-5 py-4 py-lg-5">
    <div class="row justify-content-center w-100">

        <!-- Main Container -->
        <div class="col-xl-8 col-lg-8 col-md-9 col-sm-12 mt-0 mt-lg-5">
            <div class="card">

                <!-- Card Header -->
                <div class="card-header bg-transparent text-center">
                    <strong><fmt:message key="mfa.handler.title"/></strong>
                </div>

                <!-- Card Body -->
                <div class="card-body">
                    <c:choose>
                        <c:when test="${not empty requestScope.errMsg}">
                            <div class="alert alert-danger" role="alert">
                                <strong>${requestScope.errMsg}</strong>
                            </div>
                        </c:when>
                        <c:when test="${requestScope.mfaRegistrationRequired eq true}">
                            <jsp:include page="/mfa/mfa_registration.jsp"/>
                        </c:when>
                        <c:otherwise>
                            <jsp:include page="/mfa/mfa_otp_handler.jsp"/>
                        </c:otherwise>
                    </c:choose>
                </div>

            </div>
        </div>
    </div>
</div>
</body>
</html>
