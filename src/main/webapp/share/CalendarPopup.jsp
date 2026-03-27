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
<%--
  /*
    input: urlfrom and param
	output: urlfrom + "?year-day" + param
	or
	output: opener.param.substring("&formdatebox=".length()) = year1 + "-" + month1 + "-" + day1
  */
--%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page
        import="java.util.*, java.sql.*, io.github.carlos_emr.*, java.text.*, java.lang.*,java.net.*"
        errorPage="/errorpage.jsp" %>
<%
    String urlfrom = request.getParameter("urlfrom") == null ? "" : request.getParameter("urlfrom");
    String param = request.getParameter("param") == null ? "" : request.getParameter("param");
//to prepare calendar display
    int year = Integer.parseInt(request.getParameter("year"));
    int month = Integer.parseInt(request.getParameter("month"));
    int delta = request.getParameter("delta") == null ? 0 : Integer.parseInt(request.getParameter("delta")); //add or minus month
    GregorianCalendar now = new GregorianCalendar(year, month - 1, 1);

    now.add(now.MONTH, delta);
    year = now.get(Calendar.YEAR);
    month = now.get(Calendar.MONTH) + 1;

//the date of today
    GregorianCalendar cal = new GregorianCalendar();
    int todayDate = cal.get(Calendar.DATE);
    boolean bTodayDate = false;
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
    <script src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>CALENDAR</title>
    <% if (session.getAttribute("mobileOptimized") != null) { %>
    <meta name="viewport"
          content="initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=no, width=device-width">
    <% } %>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css">
    <style>
        td, th {
            font-size: 14px;
        }
        .circle {
            background: lightblue;
            width: 25px;
            height: 25px;
            border-radius: 50%;
            display: inline-block;
            line-height: 25px;
            text-align: center;
            text-decoration: none;
        }
    </style>
    <script>
        function typeInDate(year1, month1, day1) {

            <%
                if (param.startsWith("&formdatebox=")) {
            %>
            opener.<%=Encode.forJavaScript(param.substring("&formdatebox=".length()))%> = year1 + "-" + month1 + "-" + day1;
            <%
                } else {
            %>
            opener.location.href = "<%=Encode.forJavaScript(urlfrom)%>" + "?year=" + year1 + "&month=" + month1 + "&day=" + day1 + "<%=Encode.forJavaScript(param)%>";
            <%  }  %>
            self.close();
        }
    </script>
</head>
<body onload="setfocus()">
<%
    ResourceBundle oscarRec = ResourceBundle.getBundle("oscarResources");
    String jan = oscarRec.getString("share.CalendarPopUp.msgJan");
    String feb = oscarRec.getString("share.CalendarPopUp.msgFeb");
    String mar = oscarRec.getString("share.CalendarPopUp.msgMar");
    String apr = oscarRec.getString("share.CalendarPopUp.msgApr");
    String may = oscarRec.getString("share.CalendarPopUp.msgMay");
    String jun = oscarRec.getString("share.CalendarPopUp.msgJun");
    String jul = oscarRec.getString("share.CalendarPopUp.msgJul");
    String aug = oscarRec.getString("share.CalendarPopUp.msgAug");
    String sep = oscarRec.getString("share.CalendarPopUp.msgSep");
    String oct = oscarRec.getString("share.CalendarPopUp.msgOct");
    String nov = oscarRec.getString("share.CalendarPopUp.msgNov");
    String dec = oscarRec.getString("share.CalendarPopUp.msgDec");


    String[] arrayMonth = new String[]{jan, feb, mar, apr, may, jun, jul, aug, sep, oct, nov, dec};


    now.add(now.DATE, -1);
    DateInMonthTable aDate = new DateInMonthTable(year, month - 1, 1);
    int[][] dateGrid = aDate.getMonthDateGrid();
%>

<table style="width:100%">
    <tr>
        <td style="text-align:left">
            <h2>&nbsp;<%=arrayMonth[month-1]%>&nbsp;<%=year%>&nbsp;</h2>
        </td>
        <td style="text-align:right"><h2>
            <a href="CalendarPopup.jsp?urlfrom=<%=Encode.forHtmlAttribute(urlfrom)%>&year=<%=year%>&month=<%=month%>&param=<%=URLEncoder.encode(param, StandardCharsets.UTF_8)%>&delta=-12">
                <i class="fa-solid fa-angles-left" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="share.CalendarPopUp.msgLastYear"/>"></i>
            </a>
            <a href="CalendarPopup.jsp?urlfrom=<%=Encode.forHtmlAttribute(urlfrom)%>&year=<%=year%>&month=<%=month%>&param=<%=URLEncoder.encode(param, StandardCharsets.UTF_8)%>&delta=-1">
                <i class="fa-solid fa-angle-left" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="share.CalendarPopUp.msgViewLastMonth"/>"></i>
            </a>
            <a href="CalendarPopup.jsp?urlfrom=<%=Encode.forHtmlAttribute(urlfrom)%>&year=<%=year%>&month=<%=month%>&param=<%=URLEncoder.encode(param, StandardCharsets.UTF_8)%>&delta=1">
                <i class="fa-solid fa-angle-right" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="share.CalendarPopUp.msgNextMonth"/>"></i>
            </a>
            <a href="CalendarPopup.jsp?urlfrom=<%=Encode.forHtmlAttribute(urlfrom)%>&year=<%=year%>&month=<%=month%>&param=<%=URLEncoder.encode(param, StandardCharsets.UTF_8)%>&delta=12">
                <i class="fa-solid fa-angles-right" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="share.CalendarPopUp.msgNextYear"/>"></i>
            </a>&nbsp;</h2>
        </td>
    </tr>
</table>

<table style="width:100%">
    <tr style="text-align:center">
        <th>
            <%
                for (int i = 0; i < 12; i++) {
            %> <a
                href="CalendarPopup.jsp?urlfrom=<%=Encode.forHtmlAttribute(urlfrom)%>&year=<%=year%>&month=<%=i+1%>&param=<%=URLEncoder.encode(param, StandardCharsets.UTF_8)%>"
                <%=(i+1)==month?"style=\"color:red\"":""%>><span style="font-size:smaller"><%=arrayMonth[i]%></span>
        </a>
            <% } %>
        </th>
    </tr>
</table>

<table style="width:100%" class="table">
    <tr style="text-align:center">
        <th style="width:14%"><span style="color:red"><fmt:setBundle basename="oscarResources"/><fmt:message key="share.CalendarPopUp.msgSun"/></span></th>
        <th style="width:14%"><fmt:setBundle basename="oscarResources"/><fmt:message key="share.CalendarPopUp.msgMon"/></th>
        <th style="width:14%"><fmt:setBundle basename="oscarResources"/><fmt:message key="share.CalendarPopUp.msgTue"/></th>
        <th style="width:14%"><fmt:setBundle basename="oscarResources"/><fmt:message key="share.CalendarPopUp.msgWed"/></th>
        <th style="width:14%"><fmt:setBundle basename="oscarResources"/><fmt:message key="share.CalendarPopUp.msgThu"/></th>
        <th style="width:14%"><fmt:setBundle basename="oscarResources"/><fmt:message key="share.CalendarPopUp.msgFri"/></th>
        <th style="width:14%"><span style="color:green"><fmt:setBundle basename="oscarResources"/><fmt:message key="share.CalendarPopUp.msgSat"/></span></th>
    </tr>

    <%
        for (int i = 0; i < dateGrid.length; i++) {
            out.println("<tr>");
            for (int j = 0; j < 7; j++) {
                if (dateGrid[i][j] == 0) out.println("<td></td>");
                else {
                    bTodayDate = false;
                    now.add(now.DATE, 1);
                    if ( (todayDate == now.get(Calendar.DATE)) && ( (month - 1) == (cal.get(Calendar.MONTH)) ) && ( year == cal.get(Calendar.YEAR) ) ) {
                        bTodayDate = true;
                    }
    %>
    <td style="text-align:center"><a class='<%=bTodayDate?"circle":""%>'
            href="#"
            onclick="typeInDate(<%=year%>,<%=month%>,<%= dateGrid[i][j] %>)">
        <%= dateGrid[i][j] %>
    </a></td>
    <%
                }
            }
            out.println("</tr>");
        }
    %>

</table>

<table style="width:100%">
    <tr>
        <td style="text-align:right"><input type="button" class="btn btn-link"
                                 name="Cancel" value="Cancel" onclick="window.close()"></td>
    </tr>
</table>

</body>
</html>
