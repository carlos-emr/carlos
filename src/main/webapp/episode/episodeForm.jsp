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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin" rights="r" reverse="<%=true%>">
    <%response.sendRedirect(request.getContextPath() + "/logout.jsp");%>
</security:oscarSec>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ page import="ca.openosp.openo.commn.model.Episode" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="java.util.List" %>

<%
    Episode episode = (Episode) request.getAttribute("episode");
%>
<html>
    <head>
        <script src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Episode Form</title>

        <link rel="stylesheet" href="<%=request.getContextPath() %>/css/bootstrap.css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.structure-1.12.1.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.theme-1.12.1.min.css">

        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.12.1.min.js"></script>
        <script src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>

        <script>
            var ctx = '<%=request.getContextPath()%>';

            $(document).ready(function () {

                $("#startDate").datepicker({dateFormat: "yy-mm-dd"});
                $("#endDate").datepicker({dateFormat: "yy-mm-dd"});
                $("#description").autocomplete({
                    source: ctx + '/CodeSearch.do?codingSystem=' + $("#search_coding_system").val(),
                    select: function (event, ui) {
                        $("#description").val(ui.item.label);
                        $("#code").val(ui.item.value);
                        $('input[name="episode.codingSystem"]').val($("#search_coding_system").val());
                        return false;
                    }
                });

                $("#search_coding_system").bind('change', function () {
                    $("#description").autocomplete("option", "source", ctx + '/CodeSearch.do?codingSystem=' + $("#search_coding_system").val());
                    $('input[name="episode.codingSystem"]').val($("#search_coding_system").val());
                });

                $('input[name="episode.codingSystem"]').val($("#search_coding_system").val());

                <%if(episode != null) { %>
                $("#code").val('<%=episode.getCode()%>');
                $('input[name="episode.status"]').val('<%=episode.getStatus()%>');
                $('input[name="episode.id"]').val('<%=episode.getId()%>');
                $("#description").val('<%=episode.getDescription()%>');
                $('select[name="episode.codingSystem"]').val('<%=episode.getCodingSystem()%>');
                $('#search_coding_system').val('<%=episode.getCodingSystem()%>');
                $('input[name="episode.demographicNo"]').val('<%=episode.getDemographicNo()%>');
                $("#startDate").val('<%=episode.getStartDateStr()%>');
                $("#endDate").val('<%=episode.getEndDateStr()%>');
                <% } %>

            });


        </script>
        <script>
            function validate() {
                if ($("#description").val().length == 0) {
                    alert("Description Required");
                    return false;
                }
                if ($("#startDate").val().length == 0) {
                    alert("Start Date Required");
                    return false;
                }

                if ($("#endDate").val().length == 0 && $('select[name="episode.status"]').val() == 'Completed') {
                    alert("End Date Required");
                    return false;
                }
                return true;
            }
        </script>


        <style>
            .ui-autocomplete {
                max-height: 100px;
                overflow-y: auto;
                /* prevent horizontal scrollbar */
                overflow-x: hidden;
                /* add padding to account for vertical scrollbar */
                padding-right: 20px;
            }

            /* IE 6 doesn't support max-height
             * we use height instead, but this forces the menu to always be this tall
             */
            * html .ui-autocomplete {
                height: 100px;
            }
        </style>

    </head>

    <body class="BodyStyle">

    <table class="MainTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><h4>Admin</h4></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar" style="width: 100%;">
                    <tr>
                        <td><h4>Episode Editor</h4></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" style="width:160px;">
                &nbsp;
            </td>
            <td class="MainTableRightColumn">
                <form action="${pageContext.request.contextPath}/Episode.do" method="post">
                    <input type="hidden" name="method" value="save"/>
                    <input type="hidden" id="episode.demographicNo" name="episode.demographicNo"
                           value="<%=request.getAttribute("demographicNo")%>"/>
                    <input type="hidden" id="episode.id" name="episode.id" value=""/>

                    <table>
                        <tr>
                            <td>Coding System:</td>
                            <td>
                                <select id="search_coding_system" name="search_coding_system">
                                    <%
                                        List<String> codingSystems = (List<String>) request.getAttribute("codingSystems");
                                        for (String cs : codingSystems) {
                                    %>
                                    <option value="<%=cs%>"><%=cs%>
                                    </option>
                                    <% } %>
                                </select>
                            </td>
                        </tr>
                        <tr>
                            <td>Description:</td>
                            <td><input id="description" name="episode.description" type="text"
                                       onkeypress="$('#code').val('')"/></td>
                        </tr>
                        <tr>
                            <td>Start Date:</td>
                            <td><input id="startDate" name="episode.startDateStr" type="text"/></td>
                        </tr>
                        <tr>
                            <td>End Date:</td>
                            <td><input id="endDate" name="episode.endDateStr" type="text"/></td>
                        </tr>
                        <tr>
                            <td>Code:</td>
                            <td>
                                <input type="hidden" id="episode.codingSystem" name="episode.codingSystem" value=""/>
                                <input id="code" name="episode.code" type="text" readonly="readonly"/>
                            </td>
                        </tr>
                        <tr>
                            <td>Status:</td>
                            <td>
                                <%

                                    String status = episode == null ? "Current" : episode.getStatus();
                                    if (status == null || status.length() == 0) {
                                        status = "Current";
                                    }
                                    String selected = " selected=\"selected\" ";
                                %>
                                <select id="episode.status" name="episode.status">
                                    <option value="Current" <%=("Current".equals(status)) ? selected : "" %>>Current
                                    </option>
                                    <option value="Complete" <%=("Complete".equals(status)) ? selected : "" %>>
                                        Completed
                                    </option>
                                    <option value="Deleted" <%=("Deleted".equals(status)) ? selected : "" %>>Deleted
                                    </option>
                                </select>
                            </td>
                        </tr>
                    </table>
                    <input type="submit" name="submit" value="Submit" class="btn btn-primary" onclick="return validate();"/>
                </form>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn">&nbsp;</td>
            <td class="MainTableBottomRowRightColumn">&nbsp;</td>
        </tr>
    </table>
</html>