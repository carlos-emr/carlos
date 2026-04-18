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
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="org.owasp.encoder.Encode" %>
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
    <span style="color:red;">
        <fmt:message key="demographic.demographicaddarecord.msgDuplicatedHINError"/></span><br>
    <fmt:message key="demographic.msgDuplicatedHINDetail"/>
    <a href="DemographicEdit?demographic_no=<carlos:encode value='<%= hinDuplicateDemo.getDemographicNo().toString() %>' context="uriComponent"/>">
        <carlos:encode value='<%= hinDuplicateDemo.getLastName() + ", " + hinDuplicateDemo.getFirstName() %>' context="html"/></a><br><br>
    <a href="#" onClick="history.go(-1);return false;"><b>&lt;-
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
            document.addappt.action = "<%= request.getContextPath() %>/appointment/addappointment?user_id=<carlos:encode value='<%= creator != null ? creator : "" %>' context="uriComponent"/>&provider_no=<carlos:encode value='<%= provider_no2 != null ? provider_no2 : "" %>' context="uriComponent"/>&bFirstDisp=<carlos:encode value='<%= bFirstDisp2 != null ? bFirstDisp2 : "" %>' context="uriComponent"/>&appointment_date=<carlos:encode value='<%= appointmentDate != null ? appointmentDate : "" %>' context="uriComponent"/>&year=<carlos:encode value='<%= year2 != null ? year2 : "" %>' context="uriComponent"/>&month=<carlos:encode value='<%= month2 != null ? month2 : "" %>' context="uriComponent"/>&day=<carlos:encode value='<%= day2 != null ? day2 : "" %>' context="uriComponent"/>&start_time=<carlos:encode value='<%= start_time2 != null ? start_time2 : "" %>' context="uriComponent"/>&end_time=<carlos:encode value='<%= end_time2 != null ? end_time2 : "" %>' context="uriComponent"/>&duration=<carlos:encode value='<%= duration2 != null ? duration2 : "" %>' context="uriComponent"/>&name=<carlos:encode value='<%= bufName != null ? bufName : "" %>' context="uriComponent"/>&chart_no=<carlos:encode value='<%= bufChart != null ? bufChart : "" %>' context="uriComponent"/>&demographic_no=<carlos:encode value='<%= dem != null ? dem : "" %>' context="uriComponent"/>&messageID=<carlos:encode value='<%= messageId != null ? messageId : "" %>' context="uriComponent"/>&doctor_no=<carlos:encode value='<%= bufDoctorNo != null ? bufDoctorNo : "" %>' context="uriComponent"/>&notes=<carlos:encode value='<%= notes != null ? notes : "" %>' context="uriComponent"/>&reason=<carlos:encode value='<%= reason != null ? reason : "" %>' context="uriComponent"/>&location=<carlos:encode value='<%= location != null ? location : "" %>' context="uriComponent"/>&resources=<carlos:encode value='<%= resources != null ? resources : "" %>' context="uriComponent"/>&type=<carlos:encode value='<%= type != null ? type : "" %>' context="uriComponent"/>&style=<carlos:encode value='<%= style != null ? style : "" %>' context="uriComponent"/>&billing=<carlos:encode value='<%= billing != null ? billing : "" %>' context="uriComponent"/>&status=<carlos:encode value='<%= status != null ? status : "" %>' context="uriComponent"/>&createdatetime=<carlos:encode value='<%= createdatetime != null ? createdatetime : "" %>' context="uriComponent"/>&creator=<carlos:encode value='<%= creator != null ? creator : "" %>' context="uriComponent"/>&remarks=<carlos:encode value='<%= remarks != null ? remarks : "" %>' context="uriComponent"/>";
            document.addappt.submit();
        </script>
    </form>
    <% } %>

    <%-- Success message and links --%>
    <p>
        <h2>
            <fmt:message key="demographic.demographicaddarecord.msgSuccessful"/></h2>

        <a href="DemographicEdit?demographic_no=<carlos:encode value='<%= dem != null ? dem : "" %>' context="uriComponent"/>">
            <fmt:message key="demographic.demographicaddarecord.goToRecord"/></a>

        <caisi:isModuleLoad moduleName="caisi">
            <br/>
            <a href="<%= request.getContextPath() %>/PMmodule/ClientManager?id=<carlos:encode value='<%= dem != null ? dem : "" %>' context="uriComponent"/>">
                <fmt:message key="demographic.demographicaddarecord.goToCaisiRecord"/></a>
            (<a href="#" onclick="popup(700,1027,'DemographicEdit?demographic_no=<carlos:encode value='<%= dem != null ? dem : "" %>' context="uriComponent"/>')">New Window</a>)
        </caisi:isModuleLoad>

    </p>
    <% } %>

    <%@ include file="/WEB-INF/jsp/demographic/footer.jsp" %>
</div>
</body>
</html>
