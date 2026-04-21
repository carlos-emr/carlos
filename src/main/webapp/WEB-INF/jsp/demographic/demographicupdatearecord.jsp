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
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
</head>
<body>
<center>
    <table border="0" cellspacing="0" cellpadding="0" width="100%">
        <tr bgcolor="#486ebd">
            <th align="CENTER"><font face="Helvetica" color="#FFFFFF">
                <fmt:message key="demographic.demographicupdatearecord.title"/></font></th>
        </tr>
    </table>

    <%-- Read request attributes set by DemographicUpdate2Action --%>
    <%
        java.util.List<String> fieldLengthValidationErrors =
            (java.util.List<String>) request.getAttribute("fieldLengthValidationErrors");
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
    <% if (fieldLengthValidationErrors != null && !fieldLengthValidationErrors.isEmpty()) { %>
    <span style="color:red;">One or more demographic fields exceed the maximum allowed length.</span><br><br>
    <ul>
        <% for (String validationError : fieldLengthValidationErrors) { %>
        <li><carlos:encode value='<%= validationError %>' context="html"/></li>
        <% } %>
    </ul>
    <a href="#" onClick="history.go(-1);return false;"><b>&lt;-
        <fmt:message key="global.btnBack"/></b></a>
    <% } else if (hinDuplicateDemo != null) { %>
    <span style="color:red;">
        <fmt:message key="demographic.demographicupdatearecord.msgDuplicatedHINError"/></span><br>
    <fmt:message key="demographic.msgDuplicatedHINDetail"/>
    <a href="DemographicEdit?demographic_no=<carlos:encode value='<%= hinDuplicateDemo.getDemographicNo().toString() %>' context="uriComponent"/>">
        <carlos:encode value='<%= hinDuplicateDemo.getLastName() + ", " + hinDuplicateDemo.getFirstName() %>' context="html"/></a><br><br>
    <a href="#" onClick="history.go(-1);return false;"><b>&lt;-
        <fmt:message key="global.btnBack"/></b></a>
    <% } else if (Boolean.TRUE.equals(addToWl)) { %>

    <%-- Waiting list form: rendered when the action determined an add-to-WL is needed --%>
    <form name="add2WLFrm" action="<%= request.getContextPath() %>/waitinglist/Add2WaitingList" method="post">
        <input type="hidden" name="listId" value="<carlos:encode value='<%= wlListId %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="demographicNo" value="<carlos:encode value='<%= wlDemoNo %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="demographic_no" value="<carlos:encode value='<%= wlDemoNo %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="waitingListNote" value="<carlos:encode value='<%= wlNote %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="onListSince" value="<carlos:encode value='<%= wlReferralDate %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="displaymode" value="edit"/>
        <input type="hidden" name="dboperation" value="search_detail"/>

        <% if (Boolean.TRUE.equals(needsWlConfirm)) { %>
        <script language="JavaScript">
            var add2List = confirm("The patient already has an appointment, do you still want to add him/her to the waiting list?");
            if (add2List) {
                <c:set var="__enc_1"><carlos:encode value='<%= wlDemoNo %>' context="uriComponent"/></c:set>
                document.add2WLFrm.action = "<%= request.getContextPath() %>/wa                
itinglist/Add2WaitingList";
            } else {
                document.add2WLFrm.action = "DemographicEdit?demographic_no=<carlos:encode value='${__enc_1}' context="javaScript"/>";
            }
            document.add2WLFrm.submit();
        </script>
        <% } else { %>
        <script language="JavaScript">
            document.add2WLFrm.action = "<%= request.getContextPath() %>/waitinglist/Add2WaitingList";
            document.add2WLFrm.submit();
        </script>
        <% } %>
    </form>

    <% } else { %>
    <%-- Normal success display (non-WL path shouldn't reach here due to redirect, but kept for safety) --%>
    <h2><fmt:message key="demographic.demographicupdatearecord.msgSuccessful"/></h2>
    <p>
        <a href="DemographicEdit?demographic_no=<carlos:encode value='<%= demographicNo != null ? demographicNo : "" %>' context="uriComponent"/>">
            <carlos:encode value='<%= demographicNo != null ? demographicNo : "" %>' context="html"/></a>
    </p>
    <% } %>

</center>
</body>
</html>
