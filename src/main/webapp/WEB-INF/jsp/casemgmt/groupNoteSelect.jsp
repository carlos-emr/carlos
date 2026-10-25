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
<%@ page import="java.util.List" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.web.GroupNoteSelect2Action.Member" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%
    List<Member> members = (List<Member>) request.getAttribute("groupNoteMembers");
    List<Member> current = (List<Member>) request.getAttribute("groupNoteCurrentMembers");
    String demographicNo = (String) request.getAttribute("groupNotePatient");
    String programId = (String) request.getAttribute("groupNoteProgram");
    String noteId = (String) request.getAttribute("groupNoteId");
    String chartContext = (String) request.getAttribute("groupNoteContext");
%>
<!DOCTYPE html>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title>Group Note - Select Clients</title>
</head>
<body>
<% if (Boolean.TRUE.equals(request.getAttribute("groupNoteUnsaved"))) { %>
<h4>In order to use this page, you must first save this note and then click on edit for this note then click on group icon to select client members of this group. Please try again.</h4>
<input type="button" onclick="window.close();" value="Close Window"/>
<% } else { %>
<% if (!current.isEmpty()) { %>
<h5>Current Group Links to this Note</h5>
<table border="1" width="80%">
    <tr><th>Name</th><th>Anonymous</th></tr>
    <% for (Member member : current) { %>
    <tr><td><%= SafeEncode.forHtml(member.name()) %></td><td><%= member.anonymous() %></td></tr>
    <% } %>
</table>
<% } %>
<h5>Select clients for group note</h5>
<form action="<%= request.getContextPath() %>/casemgmt/SaveGroupNoteSelection" method="post">
    <% if (chartContext != null) { %>
    <input type="hidden" name="chartContext" value="<%= SafeEncode.forHtmlAttribute(chartContext) %>"/>
    <% } %>
    <input type="hidden" name="demographicNo" value="<%= SafeEncode.forHtmlAttribute(demographicNo) %>"/>
    <input type="hidden" name="programId" value="<%= SafeEncode.forHtmlAttribute(programId) %>"/>
    <input type="hidden" name="noteId" value="<%= SafeEncode.forHtmlAttribute(noteId) %>"/>
    <table>
        <% for (Member member : members) { %>
        <tr>
            <td><input type="checkbox" name="group_client_id" value="<%= SafeEncode.forHtmlAttribute(member.id()) %>"/></td>
            <td><%= SafeEncode.forHtml(member.name()) %></td>
        </tr>
        <% } %>
    </table>
    <p><input type="number" name="num_anonymous" value="0" min="0" max="999" size="3"/> Anonymous Clients</p>
    <script>
        function confirmGroupNote() {
            return <%= current.isEmpty() %> || confirm('This will cause all previously associated clients to be disassociated with group note, and all previously associated anonymous clients to be set inactive');
        }
    </script>
    <input type="button" value="cancel" onclick="window.close();"/>
    <input type="submit" value="Enter note into selected clients" onclick="return confirmGroupNote();"/>
</form>
<% } %>
</body>
</html>
