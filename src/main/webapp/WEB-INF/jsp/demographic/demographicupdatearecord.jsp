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

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

    View template for DemographicUpdate2Action results.
    All business logic is in DemographicUpdate2Action; this JSP only renders output
    using request attributes set by that action.

    Request attributes consumed:
      demographicNo   - updated demographic number (String)
      hinDuplicateDemo - Demographic object when a HIN duplicate was found (duplicate result only)
      addToWl         - Boolean, true when waiting-list add is needed
      needsWlConfirm  - Boolean, true when a JS confirm dialog should be shown first
      wlDemoNo        - demographic_no for WL form fields
      wlListId        - list_id for WL form fields
      wlNote          - waiting_list_note for WL form fields
      wlReferralDate  - waiting_list_referral_date for WL form fields

    @since 2026-04-04
--%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ page import="org.owasp.encoder.Encode" %>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
</head>
<body>
<center>
    <table border="0" cellspacing="0" cellpadding="0" width="100%">
        <tr bgcolor="#486ebd">
            <th align="CENTER"><font face="Helvetica" color="#FFFFFF">
                UPDATE demographic RECORD</font></th>
        </tr>
    </table>

    <%-- Read request attributes set by DemographicUpdate2Action --%>
    <%
        String demographicNo = (String) request.getAttribute("demographicNo");
        io.github.carlos_emr.carlos.commn.model.Demographic hinDuplicateDemo =
            (io.github.carlos_emr.carlos.commn.model.Demographic) request.getAttribute("hinDuplicateDemo");
        Boolean addToWl = (Boolean) request.getAttribute("addToWl");
        Boolean needsWlConfirm = (Boolean) request.getAttribute("needsWlConfirm");
        String wlDemoNo = (String) request.getAttribute("wlDemoNo");
        String wlListId = (String) request.getAttribute("wlListId");
        String wlNote = (String) request.getAttribute("wlNote");
        String wlReferralDate = (String) request.getAttribute("wlReferralDate");
        if (wlDemoNo == null) wlDemoNo = "";
        if (wlListId == null) wlListId = "";
        if (wlNote == null) wlNote = "";
        if (wlReferralDate == null) wlReferralDate = "";
    %>

    <%-- HIN duplicate result --%>
    <% if (hinDuplicateDemo != null) { %>
    <span style="color:red;"><fmt:setBundle basename="oscarResources"/>
        <fmt:message key="demographic.demographicupdatearecord.msgDuplicatedHINError"/></span><br>
    <fmt:message key="demographic.msgDuplicatedHINDetail"/>
    <a href="DemographicEdit.do?demographic_no=<%= Encode.forUriComponent(hinDuplicateDemo.getDemographicNo().toString()) %>">
        <%= Encode.forHtml(hinDuplicateDemo.getLastName() + ", " + hinDuplicateDemo.getFirstName()) %></a><br><br>
    <a href="#" onClick="history.go(-1);return false;"><b>&lt;-<fmt:setBundle basename="oscarResources"/>
        <fmt:message key="global.btnBack"/></b></a>
    <% } else if (Boolean.TRUE.equals(addToWl)) { %>

    <%-- Waiting list form: rendered when the action determined an add-to-WL is needed --%>
    <form name="add2WLFrm" action="<%= request.getContextPath() %>/waitinglist/Add2WaitingList.jsp" method="post">
        <input type="hidden" name="listId" value="<%= Encode.forHtmlAttribute(wlListId) %>"/>
        <input type="hidden" name="demographicNo" value="<%= Encode.forHtmlAttribute(wlDemoNo) %>"/>
        <input type="hidden" name="demographic_no" value="<%= Encode.forHtmlAttribute(wlDemoNo) %>"/>
        <input type="hidden" name="waitingListNote" value="<%= Encode.forHtmlAttribute(wlNote) %>"/>
        <input type="hidden" name="onListSince" value="<%= Encode.forHtmlAttribute(wlReferralDate) %>"/>
        <input type="hidden" name="displaymode" value="edit"/>
        <input type="hidden" name="dboperation" value="search_detail"/>

        <% if (Boolean.TRUE.equals(needsWlConfirm)) { %>
        <script language="JavaScript">
            var add2List = confirm("The patient already has an appointment, do you still want to add him/her to the waiting list?");
            if (add2List) {
                document.add2WLFrm.action = "<%= request.getContextPath() %>/waitinglist/Add2WaitingList.jsp?demographicNo=<%= Encode.forJavaScript(Encode.forUriComponent(wlDemoNo)) %>&listId=<%= Encode.forJavaScript(Encode.forUriComponent(wlListId)) %>&waitingListNote=<%= Encode.forJavaScript(Encode.forUriComponent(wlNote)) %>&onListSince=<%= Encode.forJavaScript(Encode.forUriComponent(wlReferralDate)) %>";
            } else {
                document.add2WLFrm.action = "DemographicEdit.do?demographic_no=<%= Encode.forJavaScript(Encode.forUriComponent(wlDemoNo)) %>";
            }
            document.add2WLFrm.submit();
        </script>
        <% } else { %>
        <script language="JavaScript">
            document.add2WLFrm.action = "<%= request.getContextPath() %>/waitinglist/Add2WaitingList.jsp?demographicNo=<%= Encode.forJavaScript(Encode.forUriComponent(wlDemoNo)) %>&listId=<%= Encode.forJavaScript(Encode.forUriComponent(wlListId)) %>&waitingListNote=<%= Encode.forJavaScript(Encode.forUriComponent(wlNote)) %>&onListSince=<%= Encode.forJavaScript(Encode.forUriComponent(wlReferralDate)) %>";
            document.add2WLFrm.submit();
        </script>
        <% } %>
    </form>

    <% } else { %>
    <%-- Normal success display (non-WL path shouldn't reach here due to redirect, but kept for safety) --%>
    <h2>Update a Patient Record Successfully!
        <p>
            <a href="DemographicEdit.do?demographic_no=<%= Encode.forUriComponent(demographicNo != null ? demographicNo : "") %>">
                <%= Encode.forHtml(demographicNo != null ? demographicNo : "") %></a>
        </p>
    </h2>
    <% } %>

</center>
</body>
</html>
