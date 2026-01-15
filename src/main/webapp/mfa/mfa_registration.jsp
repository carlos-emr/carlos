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

<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<fmt:setBundle basename="oscarResources"/>

<html>
<body>
<p><fmt:message key="mfa.registration.title"/></p>
<div class="row p-2">

    <!-- Left Section: Instructions -->
    <div class="col-12 col-md-7">
        <div class="mx-2">
            <p class="h4 text-secondary mt-3"><fmt:message key="mfa.registration.instruct.heading"/></p>
            <p class="mb-4"><small><fmt:message key="mfa.registration.instruct.1"/></small></p>
            <p><small><fmt:message key="mfa.registration.instruct.2"/></small></p>
        </div>
    </div>

    <!-- Right Section: QR Code -->
    <div class="col-12 col-md-5 text-center">
        <div class="row mx-0 mt-2 p-2">
            <img src="${requestScope.qrData}" alt="QR Code for Multi-Factor Authentication Setup" class="img-fluid">
        </div>
        <div class="row">
            <jsp:include page="/mfa/mfa_otp_handler.jsp"/>
        </div>
    </div>
</div>
</body>

