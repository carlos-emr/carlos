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
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%-- Reached only through ViewTabAlertsRefresh2Action, which accepts just the five tab ids below
     and refuses a tab the user's role cannot see; the equals calls stay null-safe regardless. --%>
<%
    String curUser_no = (String) session.getAttribute("user");
    String tab = request.getParameter("id");

    if ("oscar_new_lab".equals(tab)) { %>
<%@page import="io.github.carlos_emr.CarlosProperties" %>
<oscar:newLab providerNo="<%=curUser_no%>"><fmt:message key="global.lab"/></oscar:newLab>
<% }
    if ("oscar_new_msg".equals(tab)) { %>
<oscar:newMessage providerNo="<%=curUser_no%>"><fmt:message key="global.msg"/></oscar:newMessage>
<% }
    if ("oscar_new_tickler".equals(tab)) { %>
<oscar:newTickler providerNo="<%=curUser_no%>"><fmt:message key="global.btntickler"/></oscar:newTickler>
<% }
    if ("oscar_aged_consults".equals(tab)) { %>
<oscar:agedConsult providerNo="<%=curUser_no%>"><fmt:message key="global.con"/></oscar:agedConsult>
<%
    }
    if (!CarlosProperties.getInstance().isTorontoRFQ() && "oscar_scratch".equals(tab)) {
%>
<img src='<oscar:filledScratch providerNo="<%=curUser_no%>"></oscar:filledScratch>' border='0' align="absbottom"
     title="<fmt:message key='ScratchPad.title'/>">
<% } %>
