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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<html>
<body>
<h4><font color="red"><fmt:message key='provider.changePassword.msgAccountExpiring'/></font><fmt:message key='provider.changePassword.msgPleaseContact'/></h4>
<ol>
    <li><fmt:message key='provider.changePassword.stepAdminTab'/></li>
    <li><fmt:message key='provider.changePassword.stepSearch'/></li>
    <li><fmt:message key='provider.changePassword.stepChoose'/></li>
    <li><fmt:message key='provider.changePassword.stepUserName'/></li>
    <li><fmt:message key='provider.changePassword.stepChangePassword'/></li>
    <li><fmt:message key='provider.changePassword.stepExpiryDate'/></li>
    <li><fmt:message key='provider.changePassword.stepUpdateRecord'/></li>
    <li><fmt:message key='provider.changePassword.stepDone'/></li>
</ol>

</body>
