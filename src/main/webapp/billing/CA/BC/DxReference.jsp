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

--%>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_billing");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="java.text.*, java.util.*, ca.openosp.openo.billing.ca.bc.data.*,ca.openosp.openo.billing.ca.bc.pageUtil.*,ca.openosp.*,ca.openosp.openo.entities.*" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.data.DxReference" %>

<%
    String demo = request.getParameter("demographicNo");
    DxReference dxRef = new DxReference();
    List<DxReference.DxCode> pastDxList = dxRef.getLatestDxCodes(demo);
    pageContext.setAttribute("dxList", pastDxList);
%>

<div>
    <c:if test="${ not empty dxList }">
        <select class="form-control" size="10" style="width:100%;height:100%;">
            <c:forEach items="${ dxList }" var="dx">
                <option onClick="quickPickDiagnostic('${ dx.dx }');return false;" title="${ dx.desc }">
                    <c:out value="${ dx.dx }"/> - <c:out value="${ dx.numMonthSinceDate }m"/>
                </option>
            </c:forEach>
        </select>
    </c:if>
</div>
