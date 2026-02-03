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
<%@ include file="/taglibs.jsp" %>
<br/>
<%-- Error Messages --%>
<c:if test="${not empty pageContext.request.getAttribute('org.apache.struts.action.ERROR')}">
    <table width="100%" border="0" cellpadding="0" cellspacing="1"
           bgcolor="#C0C0C0">
        <c:if test="${not empty savedMessage}">
            <tr>
                <td class="error">${savedMessage}</td>
            </tr>
        </c:if>
    </table>
</c:if>
<%-- Success Messages --%>
<c:if test="${not empty pageContext.request.getAttribute('org.apache.struts.action.MESSAGE')}">
    <table width="100%" border="0" cellpadding="0" cellspacing="1"
           bgcolor="#C0C0C0">
        <c:if test="${not empty savedMessage}">
            <tr>
                <td class="message"><c:out value="${savedMessage}"/></td>
            </tr>
        </c:if>
    </table>
</c:if>

