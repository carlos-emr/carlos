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
<!DOCTYPE html>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="java.text.SimpleDateFormat" %>
<%@ include file="/taglibs.jsp" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>

<%@page import="java.util.*" %>
<%@page import="ca.openosp.openo.commn.model.Episode" %>
<%@page import="ca.openosp.openo.commn.dao.EpisodeDao" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>

<%

%>
<html>
    <head>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/DataTables/datatables.min.js"></script>

        <title>Episode List</title>
        <link href="<%=request.getContextPath() %>/css/bootstrap.css" rel="stylesheet" type="text/css">
        <link href="<%=request.getContextPath() %>/css/DT_bootstrap.css" rel="stylesheet" type="text/css">

        <style>
            body {
                text-align: center;
            }

            div#demo {
                margin-left: auto;
                margin-right: auto;
                width: 90%;
                text-align: left;
            }
        </style>
        <script>
            $(document).ready(function () {
                $('#ocanTable').DataTable({
                    "language": {
                        "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                    }
                    //  "aaSorting": [[ 1, "desc" ]]
                });
            });
        </script>

    </head>

    <body>

    <Br/>
    <h2 style="text-align:center">Episode Listing</h2>
    <br/>

    <div id="demo">
        <table id="ocanTable" class="table table-striped table-condensed">
            <thead>
            <tr>
                <th>Description</th>
                <th>Start Date</th>
                <th>End Date</th>
                <th>Code</th>
                <th>Coding System</th>
                <th>Status</th>
            </tr>
            </thead>
            <tbody>
            <%
                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
                List<Episode> episodes = (List<Episode>) request.getAttribute("episodes");

                for (int x = 0; x < episodes.size(); x++) {
                    Episode episode = episodes.get(x);

                    String startDateStr = "";
                    if (episode.getStartDate() != null) {
                        startDateStr = dateFormatter.format(episode.getStartDate());
                    }
                    String endDateStr = "";
                    if (episode.getEndDate() != null) {
                        endDateStr = dateFormatter.format(episode.getEndDate());
                    }

            %>
            <tr class="gradeB">
                <td>
                    <a href="<%=request.getContextPath()%>/Episode.do?method=edit&episode.id=<%=episode.getId()%>"><%=episode.getDescription() %>
                    </a>
                </td>
                <td style="text-align:center"><%=startDateStr %>
                </td>
                <td style="text-align:center"><%=endDateStr %>
                </td>
                <td style="text-align:center"><%=episode.getCode() %>
                </td>
                <td style="text-align:center"><%=episode.getCodingSystem() %>
                </td>
                <td style="text-align:center"><%=episode.getStatus() %>
                </td>
            </tr>
            <%
                }
            %>
            </tbody>
        </table>
    </div>

    <br/><br/>

</html>