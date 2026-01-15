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
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<fmt:setBundle basename="oscarResources"/>

<html>
<body>
<div class="card-body d-flex align-items-center justify-content-center">

    <form action="<%= request.getContextPath() %>/mfa/loginMfa.do" method="post">
        <input type="hidden" name="mfaRegistrationFlow" value="${requestScope.mfaRegistrationRequired}">
        <div class="">
            <div class="row mx-3 mx-md-3 mx-lg-3 px-3 px-md-3 px-lg-3 mb-3">
                <input class="form-control form-control-sm ${not empty requestScope.mfaValidateCodeErr ? 'is-invalid' : ''}"
                       type="text" name="code" id="otpInput" autofocus
                       placeholder="Enter code" aria-label=".form-control-sm"
                       required maxlength="6">
                <div id="otpInputFeedback" class="invalid-feedback">
                    <c:out value="${requestScope.mfaValidateCodeErr}"/>
                </div>
            </div>

            <div class="row mx-3 mx-md-3 mx-lg-3 px-3 px-md-3 px-lg-3 mb-3">
                <input name="submit" type="submit" class="btn btn-success btn-sm w-100"
                       id="verifyButton" value="Verify Code"/>
            </div>
        </div>

        <div class="px-3 mt-3">
            <span class="text-muted"><small><fmt:message key="mfa.otp.handler.instruction"/></small></span>
        </div>
    </form>
</div>
</body>

</html>
