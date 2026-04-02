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
<%@ page import="java.util.*" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    // Check if demographicNo is present and valid
    String demographicNo = request.getParameter("demographicNo");
    if (demographicNo == null || demographicNo.trim().isEmpty() || "null".equals(demographicNo)) {
        // No patient matched - redirect to patient search page
        String labNo = request.getParameter("labNo");
        String labType = request.getParameter("labType");
        String keyword = request.getParameter("keyword");

        String redirectURL = request.getContextPath() + "/oscarMDS/PatientSearch.jsp?search_mode=search_name&limit1=0&limit2=10";
        if (labNo != null) {
            redirectURL += "&labNo=" + URLEncoder.encode(labNo, "UTF-8");
        }
        if (labType != null) {
            redirectURL += "&labType=" + URLEncoder.encode(labType, "UTF-8");
        }
        if (keyword != null) {
            redirectURL += "&keyword=" + URLEncoder.encode(keyword, "UTF-8");
        }

        response.sendRedirect(redirectURL);
        return;
    }
%>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>E-Chart</title>

    <script language="javascript">
        <%

        GregorianCalendar cal = new GregorianCalendar();
        int curYear = cal.get(Calendar.YEAR);
        int curMonth = (cal.get(Calendar.MONTH)+1);
        int curDay = cal.get(Calendar.DAY_OF_MONTH);

        %>

        location.href = '${pageContext.request.contextPath}/encounter/IncomingEncounter.do?demographicNo=<%=demographicNo%>&reason=Lab+Results-Notes&curDate=<%=curYear%>-<%=curMonth%>-<%=curDay%>&encType=<%=URLEncoder.encode("Lab Results","UTF-8")%>&status=';
        window.resizeTo(980, 700);

    </script>

</head>
<body>

<a
        href="javascript:popupPage(700, 980, '${pageContext.request.contextPath}/encounter/IncomingEncounter.do?demographicNo=<%= Encode.forHtmlAttribute(demographicNo) %>&reason=Lab+Results-Notes&curDate=<%=curYear%>-<%=curMonth%>-<%=curDay%>&encType=<%=URLEncoder.encode("Lab Results","UTF-8")%>&status=');window.close();">Please
    click here to go to the patient's E-Chart.</a>

</body>
</html>
