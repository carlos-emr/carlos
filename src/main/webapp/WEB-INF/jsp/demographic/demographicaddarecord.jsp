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

    View template for DemographicAddRecord2Action results.
    All business logic is in DemographicAddRecord2Action; this JSP only renders output
    using request attributes set by that action.

    Request attributes consumed:
      demographicNo      - newly created demographic number (String)
      hinDuplicateDemo   - Demographic object when a HIN duplicate was found (duplicate result only)
      startTime          - appointment start_time param (may be null)
      providerNo         - appointment provider_no param
      bFirstDisp         - appointment bFirstDisp param
      year2, month2, day2, endTime, duration - appointment date/time params
      appointmentDate    - appointment_date param
      bufName            - "last,first" for appointment URL
      bufChart           - chart_no placeholder for appointment URL
      bufDoctorNo        - provider_no placeholder for appointment URL
      creator, messageId, notes, reason, location, resources, type, style,
      billing, status, createdatetime, remarks - remaining appointment URL params

    @since 2026-04-04
--%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css"/>
    <script language="JavaScript">
        function start() {
            this.focus();
            this.resizeTo(1000, 700);
        }
        function closeit() {
            close();
        }
    </script>
</head>
<body onload="start()" bgproperties="fixed" topmargin="0" leftmargin="0" rightmargin="0">
<div class="container">
    <table border="0" cellspacing="0" cellpadding="0" width="100%">
        <tr bgcolor="#486ebd">
            <th align="CENTER"><font face="Helvetica" color="#FFFFFF">
                <fmt:setBundle basename="oscarResources"/>
                <fmt:message key="demographic.demographicaddarecord.title"/></font></th>
        </tr>
    </table>

    <%-- Read request attributes set by DemographicAddRecord2Action --%>
    <%
        String dem = (String) request.getAttribute("demographicNo");
        String fromAppt = (String) request.getAttribute("fromAppt");
        String start_time2 = (String) request.getAttribute("startTime");
        String provider_no2 = (String) request.getAttribute("providerNo");
        String bFirstDisp2 = (String) request.getAttribute("bFirstDisp");
        String year2 = (String) request.getAttribute("year2");
        String month2 = (String) request.getAttribute("month2");
        String day2 = (String) request.getAttribute("day2");
        String end_time2 = (String) request.getAttribute("endTime");
        String duration2 = (String) request.getAttribute("duration");
        String bufName = (String) request.getAttribute("bufName");
        String bufChart = (String) request.getAttribute("bufChart");
        String bufDoctorNo = (String) request.getAttribute("bufDoctorNo");
        String creator = (String) request.getAttribute("creator");
        String appointmentDate = (String) request.getAttribute("appointmentDate");
        String messageId = (String) request.getAttribute("messageId");
        String notes = (String) request.getAttribute("notes");
        String reason = (String) request.getAttribute("reason");
        String location = (String) request.getAttribute("location");
        String resources = (String) request.getAttribute("resources");
        String type = (String) request.getAttribute("type");
        String style = (String) request.getAttribute("style");
        String billing = (String) request.getAttribute("billing");
        String status = (String) request.getAttribute("status");
        String createdatetime = (String) request.getAttribute("createdatetime");
        String remarks = (String) request.getAttribute("remarks");

        io.github.carlos_emr.carlos.commn.model.Demographic hinDuplicateDemo =
            (io.github.carlos_emr.carlos.commn.model.Demographic) request.getAttribute("hinDuplicateDemo");
    %>

    <%-- HIN duplicate result --%>
    <% if (hinDuplicateDemo != null) { %>
    <span style="color:red;"><fmt:setBundle basename="oscarResources"/>
        <fmt:message key="demographic.demographicaddarecord.msgDuplicatedHINError"/></span><br>
    <fmt:message key="demographic.msgDuplicatedHINDetail"/>
    <a href="DemographicEdit.do?demographic_no=<%= Encode.forUriComponent(hinDuplicateDemo.getDemographicNo().toString()) %>">
        <%= Encode.forHtml(hinDuplicateDemo.getLastName() + ", " + hinDuplicateDemo.getFirstName()) %></a><br><br>
    <a href="#" onClick="history.go(-1);return false;"><b>&lt;-<fmt:setBundle basename="oscarResources"/>
        <fmt:message key="global.btnBack"/></b></a>
    <% } else { %>

    <%-- Appointment return form (JS-driven redirect back to appointment booking) --%>
    <% if (start_time2 != null && !start_time2.equals("null")) { %>
    <form method="post" name="addappt">
        <script language="JavaScript">
            <%-- URL parameters are encoded with forUriComponent(), whose output alphabet
                 (A-Za-z0-9-._~ and %XX hex sequences) contains no JS-unsafe characters,
                 making this encoding sufficient for both the URL parameter context and
                 the containing JS string. See Encode.forUriComponent() JavaDoc. --%>
            document.addappt.action = "<%= request.getContextPath() %>/appointment/addappointment.jsp?user_id=<%=Encode.forUriComponent(creator != null ? creator : "")%>&provider_no=<%=Encode.forUriComponent(provider_no2 != null ? provider_no2 : "")%>&bFirstDisp=<%=Encode.forUriComponent(bFirstDisp2 != null ? bFirstDisp2 : "")%>&appointment_date=<%=Encode.forUriComponent(appointmentDate != null ? appointmentDate : "")%>&year=<%=Encode.forUriComponent(year2 != null ? year2 : "")%>&month=<%=Encode.forUriComponent(month2 != null ? month2 : "")%>&day=<%=Encode.forUriComponent(day2 != null ? day2 : "")%>&start_time=<%=Encode.forUriComponent(start_time2 != null ? start_time2 : "")%>&end_time=<%=Encode.forUriComponent(end_time2 != null ? end_time2 : "")%>&duration=<%=Encode.forUriComponent(duration2 != null ? duration2 : "")%>&name=<%=URLEncoder.encode(bufName != null ? bufName : "", StandardCharsets.UTF_8)%>&chart_no=<%=URLEncoder.encode(bufChart != null ? bufChart : "", StandardCharsets.UTF_8)%>&bFirstDisp=false&demographic_no=<%=Encode.forUriComponent(dem != null ? dem : "")%>&messageID=<%=Encode.forUriComponent(messageId != null ? messageId : "")%>&doctor_no=<%=Encode.forUriComponent(bufDoctorNo != null ? bufDoctorNo : "")%>&notes=<%=Encode.forUriComponent(notes != null ? notes : "")%>&reason=<%=Encode.forUriComponent(reason != null ? reason : "")%>&location=<%=Encode.forUriComponent(location != null ? location : "")%>&resources=<%=Encode.forUriComponent(resources != null ? resources : "")%>&type=<%=Encode.forUriComponent(type != null ? type : "")%>&style=<%=Encode.forUriComponent(style != null ? style : "")%>&billing=<%=Encode.forUriComponent(billing != null ? billing : "")%>&status=<%=Encode.forUriComponent(status != null ? status : "")%>&createdatetime=<%=Encode.forUriComponent(createdatetime != null ? createdatetime : "")%>&creator=<%=Encode.forUriComponent(creator != null ? creator : "")%>&remarks=<%=Encode.forUriComponent(remarks != null ? remarks : "")%>";
            document.addappt.submit();
        </script>
    </form>
    <% } %>

    <%-- Success message and links --%>
    <p>
        <h2><fmt:setBundle basename="oscarResources"/>
            <fmt:message key="demographic.demographicaddarecord.msgSuccessful"/></h2>

        <a href="DemographicEdit.do?demographic_no=<%=Encode.forUriComponent(dem != null ? dem : "")%>">
            <fmt:setBundle basename="oscarResources"/>
            <fmt:message key="demographic.demographicaddarecord.goToRecord"/></a>

        <caisi:isModuleLoad moduleName="caisi">
            <br/>
            <a href="<%= request.getContextPath() %>/PMmodule/ClientManager.do?id=<%=Encode.forUriComponent(dem != null ? dem : "")%>">
                <fmt:setBundle basename="oscarResources"/>
                <fmt:message key="demographic.demographicaddarecord.goToCaisiRecord"/>
                (<a href="#" onclick="popup(700,1027,'DemographicEdit.do?demographic_no=<%=Encode.forUriComponent(dem != null ? dem : "")%>')">New Window</a>)</a>
        </caisi:isModuleLoad>

    </p>
    <% } %>

    <%@ include file="/demographic/footer.jsp" %>
</div>
</body>
</html>
