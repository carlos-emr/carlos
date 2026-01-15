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
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ page import="ca.openosp.openo.report.data.RptSearchData,java.util.*" %>

<%
    RptSearchData searchData = new RptSearchData();
    java.util.ArrayList queryArray = searchData.getQueryTypes();
%>


<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Manage Saved Demographic Queries</title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <style type="text/css" media="print">
            .MainTable {
                display: none;
            }

            .hiddenInPrint {
                display: none;
            }

            /
            /
            .header INPUT {
            / / display: none;
            / /
            }

            /
            /
            /
            /
            .header A {
            / / display: none;
            / /
            }
        </style>
        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">
    </head>

    <body class="BodyStyle" vlink="#0000FF">
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn">oscarReport</td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td>Manage Saved Demographic Queries</td>
                        <td>&nbsp;</td>
                        <td style="text-align: right"><a
                                href="javascript:popupStart(300,400,'About.jsp')">About</a> | <a
                                href="javascript:popupStart(300,400,'License.jsp')">License</a></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>

            <td class="MainTableLeftColumn">&nbsp;</td>
            <td class="MainTableRightColumn"><form action="${pageContext.request.contextPath}/report/DeleteDemographicReport.do" method="post">
                <ul style="list-style-type: none; padding-left: 3px;">
                    <%
                        for (int i = 0; i < queryArray.size(); i++) {
                            RptSearchData.SearchCriteria sc = (RptSearchData.SearchCriteria) queryArray.get(i);
                            String qId = sc.id;
                            String qName = sc.queryName;
                    %>
                    <li><input type="checkbox" name="queryFavourite"
                               value="<%=qId%>"><%=qName%>
                    </input> <%}%>

                </ul>
                <input type="submit" value="Delete"/>
                <a href="<%= request.getContextPath() %>/oscarReport/ReportDemographicReport.jsp">cancel</a>
            </form></td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn">&nbsp;</td>
            <td class="MainTableBottomRowRightColumn">&nbsp;</td>
        </tr>
    </table>

    </body>
</html>
